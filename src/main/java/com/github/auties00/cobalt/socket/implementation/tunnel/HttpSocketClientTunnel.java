package com.github.auties00.cobalt.socket.implementation.tunnel;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketClientContext;
import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketSelector;
import com.github.auties00.cobalt.socket.implementation.SocketClientListener;
import com.github.auties00.cobalt.socket.implementation.transport.SocketClientTransport;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP CONNECT tunnel implementation for proxied socket connections.
 * <p>
 * Establishes a TCP tunnel through an HTTP or HTTPS proxy server by issuing an
 * {@code HTTP CONNECT} request. Once the tunnel is established, the underlying
 * connection is transparent to higher-level protocols.
 */
final class HttpSocketClientTunnel extends SocketClientTunnel {
    /** Scheme identifier for plain HTTP, used for TLS endpoint identification. */
    private static final String HTTP_SCHEME = "http";

    /** Expected HTTP major version in proxy responses. */
    private static final int HTTP_VERSION_MAJOR = 1;

    /** Minimum accepted HTTP minor version (1.0). */
    private static final int HTTP_VERSION_MINOR_MIN = 0;

    /** Maximum accepted HTTP minor version (1.1). */
    private static final int HTTP_VERSION_MINOR_MAX = 1;

    /** Carriage return byte constant. */
    private static final byte CARRIAGE_RETURN = '\r';

    /** Line feed byte constant. */
    private static final byte LINE_FEED = '\n';

    /** Space byte constant. */
    private static final byte SPACE = ' ';

    /** HTTP 407 Proxy Authentication Required status code. */
    private static final int PROXY_AUTH_REQUIRED_STATUS_CODE = 407;

    /** Timeout for the TLS handshake with HTTPS proxies, in milliseconds. */
    private static final long TLS_HANDSHAKE_TIMEOUT = 15_000;

    /** Maximum time for the entire CONNECT handshake (including auth retries), in milliseconds. */
    private static final long OVERALL_HANDSHAKE_TIMEOUT = 30_000;

    /** Maximum total bytes consumed while parsing response headers before aborting. */
    private static final int MAX_RESPONSE_HEADER_SIZE = 65536;

    /** Maximum number of 1xx informational responses before aborting. */
    private static final int MAX_1XX_RESPONSES = 10;

    /** Maximum number of space characters allowed between HTTP version and status code. */
    private static final int MAX_STATUS_LINE_SPACES = 64;

    /** Maximum number of 407 authentication round-trips (supports multi-step protocols like NTLM). */
    private static final int MAX_AUTH_ATTEMPTS = 3;

    /** Maximum number of 3xx redirects to follow before aborting. */
    private static final int MAX_REDIRECTS = 5;

    /** The original proxy configuration. */
    private final WhatsAppClientProxy.Http proxy;

    /** The target host for the CONNECT request. */
    private String host;

    /** The target port for the CONNECT request. */
    private int port;

    /**
     * Shared response buffer for amortized byte-by-byte reading.
     * <p>
     * Both {@link #readStatusLine} and header parsing methods read through
     * {@link #nextByte}, which refills this buffer in 512-byte chunks from
     * the underlying channel. Set to {@code null} after authentication completes
     * to allow garbage collection.
     */
    private ByteBuffer responseBuf;

    HttpSocketClientTunnel(SocketClientTransport transport, WhatsAppClientProxy.Http proxy) {
        super(transport);
        this.proxy = proxy;
    }

    /**
     * Connects through the HTTP proxy to the specified target host and port.
     * <p>
     * Follows 3xx redirects to alternate proxy servers (up to {@value #MAX_REDIRECTS}).
     * On each attempt, opens a TCP connection to the proxy, optionally wraps it in TLS
     * (for HTTPS proxies), and performs the CONNECT handshake.
     *
     * @param endpoint the target for the CONNECT tunnel
     * @param listener the callback for data received through the tunnel
     * @throws IOException          if the connection or handshake fails
     * @throws InterruptedException if the thread is interrupted during connection
     */
    @Override
    public void connect(InetSocketAddress endpoint, SocketClientListener listener) throws IOException, InterruptedException {
        this.host = endpoint.getHostString();
        this.port = endpoint.getPort();

        var currentProxy = proxy;
        for (var redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            var deadline = System.currentTimeMillis() + OVERALL_HANDSHAKE_TIMEOUT;
            var ctx = transport.connect(new InetSocketAddress(currentProxy.host(), currentProxy.port()), listener);

            if (currentProxy instanceof WhatsAppClientProxy.Http.Secure) {
                initProxyTls(ctx, currentProxy.host(), currentProxy.port());
            }

            var redirect = authenticate(currentProxy, deadline);
            if (redirect == null) {
                return;
            }

            AbstractSocketSelector.INSTANCE.unregister(transport);
            currentProxy = redirect;
        }
        throw new IOException("HTTP proxy CONNECT exceeded maximum redirects (" + MAX_REDIRECTS + ")");
    }

    /**
     * Initiates a TLS handshake with the proxy server for HTTPS proxy connections.
     * <p>
     * Configures the {@link javax.net.ssl.SSLEngine} with the proxy hostname for SNI
     * and endpoint identification (certificate verification).
     *
     * @param ctx       the socket context to attach the SSL engine to
     * @param proxyHost the proxy hostname for SNI and certificate verification
     * @param proxyPort the proxy port for the SSL engine
     * @throws IOException if TLS initialization or handshake fails
     */
    private void initProxyTls(AbstractSocketClientContext ctx, String proxyHost, int proxyPort) throws IOException {
        try {
            var sslContext = SSLContext.getDefault();
            var engine = sslContext.createSSLEngine(proxyHost, proxyPort);
            engine.setUseClientMode(true);
            var params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm(HTTP_SCHEME.toUpperCase());
            engine.setSSLParameters(params);
            ctx.initSsl(engine);
            AbstractSocketSelector.INSTANCE.startTlsHandshake(transport, TLS_HANDSHAKE_TIMEOUT);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to create SSLContext", e);
        }
    }

    /**
     * Performs the HTTP CONNECT handshake with authentication and redirect handling.
     * <p>
     * Sends the initial CONNECT request without authentication. If the proxy responds
     * with 407, retries with the configured authenticator (up to {@value #MAX_AUTH_ATTEMPTS}
     * times to support multi-step protocols). On 3xx, returns the redirect target.
     * On 2xx, finalizes the tunnel by pre-seeding any leftover bytes and marking the
     * channel ready for application data.
     *
     * @param currentProxy the proxy configuration for this connection attempt
     * @param deadline     the absolute timestamp (millis) after which the handshake times out
     * @return {@code null} on success, or the redirect target proxy on 3xx
     * @throws IOException if the handshake fails, times out, or authentication is rejected
     */
    private WhatsAppClientProxy.Http authenticate(WhatsAppClientProxy.Http currentProxy, long deadline) throws IOException {
        var isHttps = currentProxy instanceof WhatsAppClientProxy.Http.Secure;
        var authenticator = currentProxy.authenticator();

        checkDeadline(deadline);
        var statusCode = sendConnectAndReadStatus(null, deadline);

        for (var attempt = 0; statusCode == PROXY_AUTH_REQUIRED_STATUS_CODE && attempt < MAX_AUTH_ATTEMPTS; attempt++) {
            var headers = consumeHeaders(deadline);
            responseBuf = null;

            if (authenticator.isEmpty()) {
                throw new IOException("Proxy requires authentication but no authenticator configured");
            }

            var challenges = headers.getOrDefault("proxy-authenticate", List.of());
            var authValue = authenticator.get().authenticate("CONNECT", host + ":" + port, challenges);

            checkDeadline(deadline);
            statusCode = sendConnectAndReadStatus(authValue, deadline);
        }

        if (statusCode == PROXY_AUTH_REQUIRED_STATUS_CODE) {
            throw new IOException("HTTP proxy authentication failed after " + MAX_AUTH_ATTEMPTS + " attempts");
        }

        if (statusCode >= 300 && statusCode < 400) {
            return handleRedirect(currentProxy, statusCode, deadline);
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP proxy CONNECT failed with status code " + statusCode);
        }

        skipHeaders(deadline);

        if (responseBuf != null && responseBuf.hasRemaining()) {
            if (!AbstractSocketSelector.INSTANCE.preSeedDatagram(transport, responseBuf)) {
                throw new IOException("Failed to pre-seed leftover bytes from proxy response");
            }
        }
        responseBuf = null;

        if (!AbstractSocketSelector.INSTANCE.markReady(transport)) {
            throw new IOException("Failed to authenticate with proxy: rejected");
        }

        if (isHttps && !AbstractSocketSelector.INSTANCE.drainSslAppBuffer(transport)) {
            throw new IOException("Failed to drain leftover SSL data after proxy authentication");
        }

        return null;
    }

    /**
     * Handles a 3xx redirect response by parsing the {@code Location} header
     * and constructing a new proxy configuration for the redirect target.
     * <p>
     * The authenticator from the current proxy is carried over to the redirect target.
     *
     * @param currentProxy the current proxy configuration (provides the authenticator)
     * @param statusCode   the 3xx status code
     * @param deadline     the handshake deadline
     * @return the redirect target proxy configuration
     * @throws IOException if the redirect response is malformed or has no {@code Location} header
     */
    private WhatsAppClientProxy.Http handleRedirect(WhatsAppClientProxy.Http currentProxy, int statusCode, long deadline) throws IOException {
        var headers = consumeHeaders(deadline);
        responseBuf = null;
        var locations = headers.getOrDefault("location", List.of());
        if (locations.isEmpty()) {
            throw new IOException("HTTP proxy CONNECT redirect (status " + statusCode + ") without Location header");
        }

        var redirectUri = URI.create(locations.getFirst());
        var redirectHost = redirectUri.getHost() != null ? redirectUri.getHost() : currentProxy.host();
        var redirectPort = redirectUri.getPort();
        var redirectHttps = "https".equalsIgnoreCase(redirectUri.getScheme());
        if (redirectPort == -1) {
            redirectPort = redirectHttps ? 443 : 80;
        }

        var authenticator = currentProxy.authenticator().orElse(null);
        return redirectHttps
                ? WhatsAppClientProxy.ofHttps(redirectHost, redirectPort, authenticator)
                : WhatsAppClientProxy.ofHttp(redirectHost, redirectPort, authenticator);
    }

    /**
     * Throws {@link IOException} if the current time exceeds the given deadline.
     *
     * @param deadline the absolute deadline timestamp in milliseconds
     * @throws IOException if the deadline has passed
     */
    private void checkDeadline(long deadline) throws IOException {
        if (System.currentTimeMillis() > deadline) {
            throw new IOException("HTTP proxy CONNECT handshake timed out");
        }
    }

    /**
     * Reads the next byte from the response stream, refilling the buffer as needed.
     * <p>
     * Uses a 512-byte buffer to amortize the cost of channel reads across many
     * byte-by-byte parsing calls. Checks the handshake deadline before each refill.
     *
     * @param deadline the handshake deadline for timeout checks
     * @return the next byte from the response stream
     * @throws IOException if the read fails, the stream ends, or the deadline is exceeded
     */
    private byte nextByte(long deadline) throws IOException {
        if (responseBuf != null && responseBuf.hasRemaining()) {
            return responseBuf.get();
        }
        checkDeadline(deadline);
        if (responseBuf == null) {
            responseBuf = ByteBuffer.allocate(512);
        } else {
            responseBuf.clear();
        }
        var length = super.readBinary(responseBuf, false);
        responseBuf.position(0);
        responseBuf.limit(length);
        return responseBuf.get();
    }

    /**
     * Sends a CONNECT request and reads the response status code.
     * <p>
     * Resets the response buffer before sending to discard any stale data from
     * a previous request-response cycle. Handles 1xx informational responses in
     * a bounded loop (up to {@value #MAX_1XX_RESPONSES}).
     *
     * @param authorizationHeader the {@code Proxy-Authorization} value, or {@code null} for no auth
     * @param deadline            the handshake deadline
     * @return the final (non-1xx) HTTP status code
     * @throws IOException if sending, reading, or parsing fails
     */
    private int sendConnectAndReadStatus(String authorizationHeader, long deadline) throws IOException {
        responseBuf = null;
        sendConnectRequest(authorizationHeader);
        for (var i = 0; i < MAX_1XX_RESPONSES; i++) {
            var statusCode = readStatusLine(deadline);
            if (statusCode < 100 || statusCode > 199) {
                return statusCode;
            }
            skipHeaders(deadline);
        }
        throw new IOException("HTTP proxy sent too many 1xx informational responses");
    }

    /**
     * Sends an HTTP CONNECT request to the proxy server.
     * <p>
     * The request targets {@code host:port} (the tunnel destination) and includes
     * standard headers. Uses the {@code Connection} header (not the non-standard
     * {@code Proxy-Connection}).
     *
     * @param authorizationHeader the {@code Proxy-Authorization} value, or {@code null} to omit
     */
    private void sendConnectRequest(String authorizationHeader) {
        var builder = new StringBuilder();
        builder.append("CONNECT ")
                .append(host)
                .append(":")
                .append(port)
                .append(" HTTP/")
                .append(HTTP_VERSION_MAJOR)
                .append('.')
                .append(HTTP_VERSION_MINOR_MAX)
                .append("\r\n");
        builder.append("Host: ")
                .append(host)
                .append(":")
                .append(port)
                .append("\r\n");
        builder.append("User-Agent: WhatsApp\r\n");
        builder.append("Connection: keep-alive\r\n");
        if (authorizationHeader != null) {
            builder.append("Proxy-Authorization: ")
                    .append(authorizationHeader)
                    .append("\r\n");
        }
        builder.append("\r\n");
        super.sendBinary(ByteBuffer.wrap(builder.toString().getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Parses the HTTP status line from the proxy response.
     * <p>
     * Tolerates leading whitespace/CRLF junk before the status line and allows
     * any amount of whitespace between the HTTP version and the status code
     * (capped at {@value #MAX_STATUS_LINE_SPACES} to prevent abuse).
     * Accepts both HTTP/1.0 and HTTP/1.1 responses.
     *
     * @param deadline the handshake deadline
     * @return the 3-digit HTTP status code
     * @throws IOException if the response is malformed or the deadline is exceeded
     */
    private int readStatusLine(long deadline) throws IOException {
        // Skip leading junk (whitespace/CRLF before the status line)
        byte b;
        do {
            b = nextByte(deadline);
        } while (b == SPACE || b == CARRIAGE_RETURN || b == LINE_FEED);

        // Parse "HTTP/"
        if (b != 'H'
            || nextByte(deadline) != 'T'
            || nextByte(deadline) != 'T'
            || nextByte(deadline) != 'P'
            || nextByte(deadline) != '/') {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }

        var major = nextByte(deadline) - '0';
        if (major != HTTP_VERSION_MAJOR) {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }
        if (nextByte(deadline) != '.') {
            throw new IOException("Invalid HTTP response: expected HTTP/1.0 or HTTP/1.1");
        }

        var minor = nextByte(deadline) - '0';
        if (minor != HTTP_VERSION_MINOR_MIN && minor != HTTP_VERSION_MINOR_MAX) {
            throw new IOException("Invalid HTTP response: unsupported version HTTP/1." + minor);
        }

        // Skip spaces between version and status code (at least one required)
        b = nextByte(deadline);
        if (b != SPACE) {
            throw new IOException("Invalid HTTP response: expected space after HTTP version");
        }
        for (var spacesSkipped = 1; ; spacesSkipped++) {
            b = nextByte(deadline);
            if (b != SPACE) {
                break;
            }
            if (spacesSkipped >= MAX_STATUS_LINE_SPACES) {
                throw new IOException("Invalid HTTP response: too many spaces in status line");
            }
        }

        // b is the first digit of the status code
        var d1 = b;
        var d2 = nextByte(deadline);
        var d3 = nextByte(deadline);
        if (d1 < '0' || d1 > '9' || d2 < '0' || d2 > '9' || d3 < '0' || d3 > '9') {
            throw new IOException("Invalid HTTP response: status code contains non-digit characters");
        }

        return (d1 - '0') * 100 + (d2 - '0') * 10 + (d3 - '0');
    }

    /**
     * Parses the response headers into a multi-valued map.
     * <p>
     * Reads from the current stream position (after the status code) through the
     * header terminator (empty line). The first line (status line remainder / reason phrase)
     * is skipped. Header names are lowercased for case-insensitive lookup. Multiple
     * headers with the same name are stored as separate list entries (not merged).
     * <p>
     * Tolerates both {@code \r\n} and bare {@code \n} line endings: {@code \r} bytes are
     * simply skipped, and {@code \n} is treated as the line terminator.
     * <p>
     * This method allocates a {@link HashMap}. For the success path where headers are not
     * needed, use {@link #skipHeaders} instead.
     *
     * @param deadline the handshake deadline
     * @return the parsed headers, never {@code null}
     * @throws IOException if the headers exceed {@value #MAX_RESPONSE_HEADER_SIZE} bytes,
     *                     the connection closes, or the deadline is exceeded
     */
    private Map<String, List<String>> consumeHeaders(long deadline) throws IOException {
        var headers = new HashMap<String, List<String>>();
        var line = new StringBuilder();
        var totalRead = 0;
        var firstLine = true;

        while (isConnected()) {
            if (totalRead > MAX_RESPONSE_HEADER_SIZE) {
                throw new IOException("HTTP proxy response headers exceed maximum size");
            }

            var b = nextByte(deadline);
            totalRead++;

            if (b == CARRIAGE_RETURN) {
                continue;
            }

            if (b == LINE_FEED) {
                if (firstLine) {
                    firstLine = false;
                    line.setLength(0);
                    continue;
                }
                if (line.isEmpty()) {
                    return headers;
                }
                var colon = line.indexOf(":");
                if (colon > 0) {
                    var key = line.substring(0, colon).trim().toLowerCase();
                    var value = line.substring(colon + 1).trim();
                    headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                }
                line.setLength(0);
                continue;
            }

            if (!firstLine) {
                line.append((char) b);
            }
        }
        throw new IOException("Unexpected end of stream");
    }

    /**
     * Skips the response headers without parsing them.
     * <p>
     * Scans from the current stream position to the header terminator (empty line)
     * without allocating a headers map. Used on the success path (2xx) and for 1xx
     * informational responses where header contents are not needed.
     * <p>
     * Tolerates both {@code \r\n} and bare {@code \n} line endings.
     *
     * @param deadline the handshake deadline
     * @throws IOException if the headers exceed {@value #MAX_RESPONSE_HEADER_SIZE} bytes,
     *                     the connection closes, or the deadline is exceeded
     */
    private void skipHeaders(long deadline) throws IOException {
        var totalRead = 0;
        var firstLine = true;
        var lineIsEmpty = false;

        while (isConnected()) {
            if (totalRead > MAX_RESPONSE_HEADER_SIZE) {
                throw new IOException("HTTP proxy response headers exceed maximum size");
            }

            var b = nextByte(deadline);
            totalRead++;

            if (b == CARRIAGE_RETURN) {
                continue;
            }

            if (b == LINE_FEED) {
                if (firstLine) {
                    firstLine = false;
                    lineIsEmpty = true;
                    continue;
                }
                if (lineIsEmpty) {
                    return;
                }
                lineIsEmpty = true;
                continue;
            }

            firstLine = false;
            lineIsEmpty = false;
        }
        throw new IOException("Unexpected end of stream");
    }
}
