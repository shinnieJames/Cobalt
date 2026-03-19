package com.github.auties00.cobalt.socket.layer.tunnel.http;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * An HTTP CONNECT tunnel layer for proxied socket connections.
 *
 * <p>Establishes a TCP tunnel through an HTTP or HTTPS proxy server by
 * issuing an {@code HTTP CONNECT} request.  Once the tunnel is
 * established, the underlying connection is transparent to higher-level
 * protocols.
 *
 * <p>This layer only performs the HTTP CONNECT handshake.  It does
 * <b>not</b> handle TLS for HTTPS proxies or call
 * {@code markReady()} — those are the responsibility of the tunnel
 * security layer above.
 *
 * <p>Authentication is supported via the proxy's configured
 * {@link com.github.auties00.cobalt.client.WhatsAppClientProxyAuthenticator.Http
 * authenticator}.  Redirects (3xx) to alternate proxy servers within the
 * same scheme are followed up to {@value #MAX_REDIRECTS} times.
 *
 * <p><strong>Performance notes:</strong>
 * <ul>
 *   <li>Non-matching headers are skipped via the vectorised
 *       {@link HttpResponseReader#skipToEndOfLine(long)} — no per-byte
 *       method calls for the vast majority of header data.</li>
 *   <li>{@code Proxy-Authenticate} values are extracted with a single
 *       bulk copy when the value fits in the read buffer (the common
 *       case with an 8 KiB buffer).  The slow path — which essentially
 *       never runs for typical auth challenges — is split into a
 *       separate method so the JIT always inlines the fast path, and
 *       uses a {@link StringBuilder} for zero-guess-sizing
 *       accumulation.</li>
 *   <li>{@code Location} URI parsing never materialises the full value.
 *       When the value fits in the buffer, direct array indexing is
 *       used.  When it spans buffers, a streaming state machine reads
 *       host bytes into a fixed {@code byte[253]} and port digits into
 *       an {@code int} accumulator, refilling as needed — no
 *       intermediate buffer at all.</li>
 * </ul>
 *
 * @apiNote This layer directly depends on
 * {@link WhatsAppClientProxy.Http} for proxy configuration.  This
 * coupling is intentional for this project.  If the socket stack is
 * extracted as a standalone library, a generic proxy configuration
 * interface should be introduced to replace the direct dependency.
 */
public final class HttpSocketClientTunnelLayer implements SocketClientTunnelLayer {
    private static final int HTTP_VERSION_MAJOR = 1;
    private static final int HTTP_VERSION_MINOR_MAX = 1;
    private static final int PROXY_AUTH_REQUIRED_STATUS_CODE = 407;
    private static final long OVERALL_HANDSHAKE_TIMEOUT = 30_000;
    private static final int MAX_RESPONSE_HEADER_SIZE = 65536;
    private static final int MAX_1XX_RESPONSES = 10;
    private static final int MAX_STATUS_LINE_SPACES = 64;
    private static final int MAX_AUTH_ATTEMPTS = 3;
    private static final int MAX_REDIRECTS = 5;
    private static final byte CARRIAGE_RETURN = '\r';
    private static final byte LINE_FEED = '\n';
    private static final byte SPACE = ' ';

    /**
     * The original proxy configuration.
     */
    private final WhatsAppClientProxy.Http proxy;

    /**
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer innerLayer;

    /**
     * The target host for the CONNECT request.
     */
    private String host;

    /**
     * The target port for the CONNECT request.
     */
    private int port;

    /**
     * The buffered response reader for HTTP parsing.
     */
    private final HttpResponseReader responseReader;

    /**
     * Creates an HTTP tunnel layer wrapping the given inner layer.
     *
     * @param proxy      the HTTP proxy configuration
     * @param innerLayer the layer below (transport or TLS-over-transport)
     */
    public HttpSocketClientTunnelLayer(WhatsAppClientProxy.Http proxy, SocketClientLayer innerLayer) {
        this.proxy = proxy;
        this.innerLayer = innerLayer;
        this.responseReader = new HttpResponseReader(
                innerLayer,
                "HTTP proxy CONNECT handshake timed out",
                "HTTP proxy response headers exceed maximum size",
                "Unexpected end of stream",
                MAX_RESPONSE_HEADER_SIZE,
                MAX_STATUS_LINE_SPACES
        );
    }

    /**
     * Connects through the HTTP proxy to the specified target endpoint.
     *
     * <p>Opens a connection to the proxy via the inner layer, then
     * performs the HTTP CONNECT handshake.  Follows 3xx redirects to
     * alternate proxy servers (up to {@value #MAX_REDIRECTS}).
     *
     * <p>After this method returns successfully, the tunnel is established
     * but the connection is not yet marked ready for application data.
     * The caller (typically a tunnel security layer) is responsible for
     * calling {@code markReady()} and optionally setting up TLS.
     *
     * @param address  the target endpoint for the CONNECT tunnel
     * @param listener the callback for events (not used during handshake)
     * @throws IOException if the connection or handshake fails
     */
    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        this.host = address.getHostString();
        this.port = address.getPort();

        var currentProxy = proxy;
        for (var redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            innerLayer.connect(
                    new InetSocketAddress(currentProxy.host(), currentProxy.port()),
                    listener
            );

            var deadline = System.currentTimeMillis() + OVERALL_HANDSHAKE_TIMEOUT;
            var redirect = authenticate(currentProxy, deadline);
            if (redirect == null) {
                return;
            }

            innerLayer.disconnect();
            currentProxy = redirect;
        }
        throw new IOException("HTTP proxy CONNECT exceeded maximum redirects (" + MAX_REDIRECTS + ")");
    }

    /**
     * Performs the HTTP CONNECT handshake with authentication and redirect
     * handling.
     *
     * <p>Sends the initial CONNECT request without authentication.  If
     * the proxy responds with 407, retries with the configured
     * authenticator (up to {@value #MAX_AUTH_ATTEMPTS} times to support
     * multi-step protocols).  On 3xx, returns the redirect target.  On
     * 2xx, the tunnel is established.
     *
     * @param currentProxy the proxy configuration for this attempt
     * @param deadline     the absolute timestamp after which the handshake
     *                     times out
     * @return {@code null} on success, or the redirect target proxy on 3xx
     * @throws IOException if the handshake fails, times out, or
     *                     authentication is rejected
     */
    private WhatsAppClientProxy.Http authenticate(WhatsAppClientProxy.Http currentProxy, long deadline) throws IOException {
        var authenticator = currentProxy.authenticator();

        responseReader.checkDeadline(deadline);
        var statusCode = sendConnectAndReadStatus(null, deadline);

        for (var attempt = 0; statusCode == PROXY_AUTH_REQUIRED_STATUS_CODE && attempt < MAX_AUTH_ATTEMPTS; attempt++) {
            var challenges = consumeProxyAuthenticateValues(deadline);
            responseReader.reset();

            if (authenticator.isEmpty()) {
                throw new IOException("Proxy requires authentication but no authenticator configured");
            }

            var authValue = authenticator.get().authenticate("CONNECT", host + ":" + port, challenges);

            responseReader.checkDeadline(deadline);
            statusCode = sendConnectAndReadStatus(authValue, deadline);
        }

        if (statusCode == PROXY_AUTH_REQUIRED_STATUS_CODE) {
            throw new IOException("HTTP proxy authentication failed after " + MAX_AUTH_ATTEMPTS + " attempts");
        }

        if (statusCode >= 300 && statusCode < 400) {
            var redirect = consumeLocationRedirect(currentProxy, statusCode, deadline);
            responseReader.reset();
            return redirect;
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP proxy CONNECT failed with status code " + statusCode);
        }

        responseReader.skipHeaders(deadline);
        responseReader.reset();
        return null;
    }

    /**
     * Scans response headers and collects all {@code Proxy-Authenticate}
     * values as strings.
     *
     * <p>Only the {@code proxy-authenticate} header is extracted; all
     * other headers are skipped at the byte level without allocation.
     * All headers are consumed so the stream is correctly positioned for
     * the next request on the same connection.
     *
     * <p>Per-byte method calls are limited to the short header-name
     * prefix match (~20 bytes) that occurs only for candidate headers.
     * Value extraction has two tiers:
     * <ol>
     *   <li><b>Fast path</b> (value in buffer — the common case): a
     *       single bulk {@link HttpResponseReader#getBuffered} produces
     *       an exact-sized {@code byte[]} that is decoded to a
     *       {@link String} with zero transcoding on compact-string
     *       JVMs.</li>
     *   <li><b>Slow path</b> (value spans buffers — essentially never
     *       for typical auth challenges): split into
     *       {@link #readAuthValueSlow} so the JIT always inlines this
     *       method.  Uses a {@link StringBuilder} that grows on demand
     *       with no up-front sizing.</li>
     * </ol>
     *
     * @param deadline the handshake deadline
     * @return all {@code Proxy-Authenticate} challenge values
     * @throws IOException if the headers exceed the size limit, the
     *                     connection closes, or the deadline is exceeded
     */
    private List<String> consumeProxyAuthenticateValues(long deadline) throws IOException {
        responseReader.startHeaderSection();
        responseReader.skipToEndOfLine(deadline);

        var challenges = new ArrayList<String>();
        outer:
        while (innerLayer.isConnected()) {
            var b = responseReader.nextHeaderByte(deadline);
            if (b == CARRIAGE_RETURN) {
                b = responseReader.nextHeaderByte(deadline);
            }
            if (b == LINE_FEED) {
                break;
            }

            // Quick reject: first char must be 'p'/'P'
            if ((b | 0x20) != 'p') {
                responseReader.skipToEndOfLine(deadline);
                continue;
            }

            // Match "roxy-authenticate" (already consumed 'p')
            var suffix = "roxy-authenticate";
            for (var i = 0; i < suffix.length(); i++) {
                b = responseReader.nextHeaderByte(deadline);
                if ((b | 0x20) != suffix.charAt(i)) {
                    if (b != LINE_FEED) {
                        responseReader.skipToEndOfLine(deadline);
                    }
                    continue outer;
                }
            }

            b = responseReader.nextHeaderByte(deadline);
            if (b != ':') {
                if (b != LINE_FEED) {
                    responseReader.skipToEndOfLine(deadline);
                }
                continue;
            }

            // Skip OWS after colon
            do {
                b = responseReader.nextHeaderByte(deadline);
            } while (b == SPACE);

            var dist = responseReader.distanceToLineFeed();
            if (dist >= 0) {
                var end = dist;
                if (end > 0 && responseReader.peekBuffered(end - 1) == CARRIAGE_RETURN) {
                    end--;
                }
                var value = new byte[1 + end];
                value[0] = b;
                if (end > 0) {
                    responseReader.getBuffered(value, 1, end);
                }
                responseReader.skipBuffered(dist + 1 - end); // skip optional CR + LF
                responseReader.accountHeaderBytes(dist + 1);

                var len = value.length;
                while (len > 0 && value[len - 1] == SPACE) {
                    len--;
                }
                challenges.add(new String(value, 0, len, StandardCharsets.ISO_8859_1));
            } else {
                challenges.add(readAuthValueSlow(b, deadline));
            }
        }
        return challenges;
    }

    /**
     * Slow-path reader for a {@code Proxy-Authenticate} value that spans
     * buffer boundaries.
     *
     * <p>Separated from the fast path so that the JIT sees a small
     * inlineable method body for
     * {@link #consumeProxyAuthenticateValues}.  Uses a
     * {@link StringBuilder} whose internal growth is JVM-optimised,
     * avoiding any up-front size guesses or large fixed-size buffers.
     *
     * <p>Each buffer chunk is appended in bulk via
     * {@link HttpResponseReader#writeTo(StringBuilder, int)},
     * so per-byte overhead is limited to the {@code StringBuilder}
     * internal char copy.
     *
     * @param firstByte the first non-OWS byte (already consumed)
     * @param deadline  the handshake deadline
     * @return the trimmed header value
     * @throws IOException on timeout, EOF, or header size overflow
     */
    private String readAuthValueSlow(byte firstByte, long deadline) throws IOException {
        var sb = new StringBuilder();
        sb.append((char) (firstByte & 0xFF));

        // Drain whatever is left in the current buffer
        var remaining = responseReader.bufferedRemaining();
        if (remaining > 0) {
            responseReader.writeTo(sb, remaining);
            responseReader.accountHeaderBytes(remaining);
        }

        // Refill and scan until we find the LF
        while (true) {
            responseReader.refillBuffered(deadline);
            var dist = responseReader.distanceToLineFeed();
            if (dist >= 0) {
                if (dist > 0) {
                    responseReader.writeTo(sb, dist);
                }
                responseReader.skipBuffered(1); // skip the LF
                responseReader.accountHeaderBytes(dist + 1);
                break;
            }
            remaining = responseReader.bufferedRemaining();
            responseReader.writeTo(sb, remaining);
            responseReader.accountHeaderBytes(remaining);
        }

        // Trim trailing CR and spaces
        var len = sb.length();
        while (len > 0) {
            var c = sb.charAt(len - 1);
            if (c != '\r' && c != ' ') {
                break;
            }
            len--;
        }
        sb.setLength(len);
        return sb.toString();
    }

    /**
     * Scans response headers for the first {@code Location} value and
     * parses it directly into a {@link WhatsAppClientProxy.Http} redirect
     * target.
     *
     * <p>Two parsing tiers avoid ever materialising the full URI:
     * <ol>
     *   <li><b>Fast path</b> (value in buffer — the common case): the
     *       value is extracted into an exact-sized {@code byte[]} and
     *       parsed with direct array indexing via
     *       {@link #parseRedirectUri(byte[], int, WhatsAppClientProxy.Http)}.</li>
     *   <li><b>Slow path</b> (value spans buffers): a streaming state
     *       machine in {@link #parseRedirectUriStreaming} reads host
     *       bytes into a fixed {@code byte[253]} and port digits into
     *       an {@code int} accumulator, refilling as needed — zero
     *       intermediate allocation.</li>
     * </ol>
     *
     * <p>All headers are consumed so the stream is correctly positioned
     * regardless of which header contained the {@code Location}.
     *
     * @param currentProxy the current proxy (used as host fallback and
     *                     authenticator source)
     * @param statusCode   the 3xx status code (for error messages)
     * @param deadline     the handshake deadline
     * @return the redirect target proxy configuration
     * @throws IOException if the {@code Location} header is missing or
     *                     the URI is malformed, or if headers exceed the
     *                     size limit, the connection closes, or the
     *                     deadline is exceeded
     */
    private WhatsAppClientProxy.Http consumeLocationRedirect(WhatsAppClientProxy.Http currentProxy, int statusCode, long deadline) throws IOException {
        responseReader.startHeaderSection();
        responseReader.skipToEndOfLine(deadline);

        WhatsAppClientProxy.Http result = null;
        outer:
        while (innerLayer.isConnected()) {
            var b = responseReader.nextHeaderByte(deadline);
            if (b == CARRIAGE_RETURN) {
                b = responseReader.nextHeaderByte(deadline);
            }
            if (b == LINE_FEED) {
                break;
            }

            // Skip if already found or first char is not 'l'/'L'
            if (result != null || (b | 0x20) != 'l') {
                responseReader.skipToEndOfLine(deadline);
                continue;
            }

            // Match "ocation" (already consumed 'l')
            var suffix = "ocation";
            for (var i = 0; i < suffix.length(); i++) {
                b = responseReader.nextHeaderByte(deadline);
                if ((b | 0x20) != suffix.charAt(i)) {
                    if (b != LINE_FEED) {
                        responseReader.skipToEndOfLine(deadline);
                    }
                    continue outer;
                }
            }

            b = responseReader.nextHeaderByte(deadline);
            if (b != ':') {
                if (b != LINE_FEED) {
                    responseReader.skipToEndOfLine(deadline);
                }
                continue;
            }

            // Skip OWS after colon
            do {
                b = responseReader.nextHeaderByte(deadline);
            } while (b == SPACE);

            var dist = responseReader.distanceToLineFeed();
            if (dist >= 0) {
                var end = dist;
                if (end > 0 && responseReader.peekBuffered(end - 1) == CARRIAGE_RETURN) {
                    end--;
                }
                var value = new byte[1 + end];
                value[0] = b;
                if (end > 0) {
                    responseReader.getBuffered(value, 1, end);
                }
                responseReader.skipBuffered(dist + 1 - end);
                responseReader.accountHeaderBytes(dist + 1);

                var valueLen = value.length;
                while (valueLen > 0 && value[valueLen - 1] == SPACE) {
                    valueLen--;
                }
                result = parseRedirectUri(value, valueLen, currentProxy);
            } else {
                result = parseRedirectUriStreaming(b, currentProxy, deadline);
            }
        }

        if (result == null) {
            throw new IOException("HTTP proxy CONNECT redirect (status " + statusCode + ") without Location header");
        }
        return result;
    }

    /**
     * Parses an HTTP or HTTPS redirect URI from a byte array using
     * direct random access.
     *
     * <p>Extracts scheme, host, and port with no intermediate
     * {@link java.net.URI} or {@link String} allocation beyond the
     * final host name (required by the proxy config API).
     *
     * @param value        the raw URI bytes
     * @param valueLen     the number of valid bytes in {@code value}
     * @param currentProxy fallback host and authenticator source
     * @return the parsed redirect proxy configuration
     * @throws IOException if the URI is malformed
     */
    private WhatsAppClientProxy.Http parseRedirectUri(byte[] value, int valueLen, WhatsAppClientProxy.Http currentProxy) throws IOException {
        if (valueLen < 7
            || (value[0] | 0x20) != 'h'
            || (value[1] | 0x20) != 't'
            || (value[2] | 0x20) != 't'
            || (value[3] | 0x20) != 'p') {
            throw new IOException("Invalid redirect URI: missing scheme");
        }

        int authorityStart;
        boolean isHttps;
        if ((value[4] | 0x20) == 's') {
            isHttps = true;
            if (valueLen < 9 || value[5] != ':' || value[6] != '/' || value[7] != '/') {
                throw new IOException("Invalid redirect URI: missing ://");
            }
            authorityStart = 8;
        } else if (value[4] == ':') {
            isHttps = false;
            if (value[5] != '/' || value[6] != '/') {
                throw new IOException("Invalid redirect URI: missing ://");
            }
            authorityStart = 7;
        } else {
            throw new IOException("Invalid redirect URI: missing scheme");
        }

        var hostEnd = valueLen;
        var portStart = -1;
        for (var i = authorityStart; i < valueLen; i++) {
            if (value[i] == ':') {
                hostEnd = i;
                portStart = i + 1;
                break;
            }
            if (value[i] == '/') {
                hostEnd = i;
                break;
            }
        }

        var redirectHost = hostEnd > authorityStart
                ? new String(value, authorityStart, hostEnd - authorityStart, StandardCharsets.ISO_8859_1)
                : currentProxy.host();

        var redirectPort = -1;
        if (portStart >= 0) {
            var portEnd = valueLen;
            for (var i = portStart; i < valueLen; i++) {
                if (value[i] == '/') {
                    portEnd = i;
                    break;
                }
            }
            if (portEnd > portStart) {
                redirectPort = 0;
                for (var i = portStart; i < portEnd; i++) {
                    var d = value[i] - '0';
                    if (d < 0 || d > 9) {
                        throw new IOException("Invalid redirect URI: non-digit in port");
                    }
                    redirectPort = redirectPort * 10 + d;
                }
            }
        }
        if (redirectPort == -1) {
            redirectPort = isHttps ? 443 : 80;
        }

        var authenticator = currentProxy.authenticator().orElse(null);
        return isHttps
                ? WhatsAppClientProxy.ofHttps(redirectHost, redirectPort, authenticator)
                : WhatsAppClientProxy.ofHttp(redirectHost, redirectPort, authenticator);
    }

    /**
     * Parses a redirect URI incrementally when the value spans buffer
     * boundaries.
     *
     * <p>Separated from the fast path so that the JIT always inlines
     * {@link #consumeLocationRedirect}.  No intermediate buffer is
     * allocated for the full URI.  Instead:
     * <ul>
     *   <li>Scheme ({@code http://} or {@code https://}) is verified
     *       byte-by-byte (~10 bytes).</li>
     *   <li>Host bytes are accumulated into a fixed
     *       {@code byte[253]} (DNS maximum).</li>
     *   <li>Port digits are accumulated into an {@code int}.</li>
     *   <li>Remaining path/query bytes are skipped to end of line
     *       via {@link HttpResponseReader#skipToEndOfLine}.</li>
     * </ul>
     *
     * @param firstByte    the first non-OWS byte of the value
     * @param currentProxy fallback host and authenticator source
     * @param deadline     the handshake deadline
     * @return the parsed redirect proxy configuration
     * @throws IOException if the URI is malformed
     */
    private WhatsAppClientProxy.Http parseRedirectUriStreaming(byte firstByte, WhatsAppClientProxy.Http currentProxy, long deadline) throws IOException {
        // Parse scheme
        if ((firstByte | 0x20) != 'h') {
            throw new IOException("Invalid redirect URI: missing scheme");
        }
        var b = responseReader.nextHeaderByte(deadline);
        if ((b | 0x20) != 't') throw new IOException("Invalid redirect URI: missing scheme");
        b = responseReader.nextHeaderByte(deadline);
        if ((b | 0x20) != 't') throw new IOException("Invalid redirect URI: missing scheme");
        b = responseReader.nextHeaderByte(deadline);
        if ((b | 0x20) != 'p') throw new IOException("Invalid redirect URI: missing scheme");

        b = responseReader.nextHeaderByte(deadline);
        boolean isHttps;
        if ((b | 0x20) == 's') {
            isHttps = true;
            b = responseReader.nextHeaderByte(deadline);
        } else {
            isHttps = false;
        }
        if (b != ':') throw new IOException("Invalid redirect URI: missing ://");
        b = responseReader.nextHeaderByte(deadline);
        if (b != '/') throw new IOException("Invalid redirect URI: missing ://");
        b = responseReader.nextHeaderByte(deadline);
        if (b != '/') throw new IOException("Invalid redirect URI: missing ://");

        // Parse host into fixed buffer (DNS max label length is 253)
        var hostBuf = new byte[253];
        var hostLen = 0;
        b = responseReader.nextHeaderByte(deadline);
        while (b != ':' && b != '/' && b != LINE_FEED && b != CARRIAGE_RETURN) {
            if (hostLen >= hostBuf.length) {
                throw new IOException("Invalid redirect URI: host too long");
            }
            hostBuf[hostLen++] = b;
            b = responseReader.nextHeaderByte(deadline);
        }

        // Parse port into int accumulator
        var redirectPort = -1;
        if (b == ':') {
            redirectPort = 0;
            b = responseReader.nextHeaderByte(deadline);
            while (b != '/' && b != LINE_FEED && b != CARRIAGE_RETURN) {
                var d = b - '0';
                if (d < 0 || d > 9) {
                    throw new IOException("Invalid redirect URI: non-digit in port");
                }
                redirectPort = redirectPort * 10 + d;
                b = responseReader.nextHeaderByte(deadline);
            }
        }

        // Skip remaining path/query to end of line
        if (b != LINE_FEED) {
            responseReader.skipToEndOfLine(deadline);
        }

        if (redirectPort == -1) {
            redirectPort = isHttps ? 443 : 80;
        }

        var redirectHost = hostLen > 0
                ? new String(hostBuf, 0, hostLen, StandardCharsets.ISO_8859_1)
                : currentProxy.host();
        var authenticator = currentProxy.authenticator().orElse(null);
        return isHttps
                ? WhatsAppClientProxy.ofHttps(redirectHost, redirectPort, authenticator)
                : WhatsAppClientProxy.ofHttp(redirectHost, redirectPort, authenticator);
    }

    /**
     * Sends a CONNECT request and reads the response status code.
     *
     * @param authorizationHeader the {@code Proxy-Authorization} value,
     *                            or {@code null} for no auth
     * @param deadline            the handshake deadline
     * @return the final (non-1xx) HTTP status code
     * @throws IOException if sending, reading, or parsing fails
     */
    private int sendConnectAndReadStatus(String authorizationHeader, long deadline) throws IOException {
        responseReader.reset();
        sendConnectRequest(authorizationHeader);
        for (var i = 0; i < MAX_1XX_RESPONSES; i++) {
            var statusCode = responseReader.readStatusLine(deadline);
            if (statusCode < 100 || statusCode > 199) {
                return statusCode;
            }
            responseReader.skipHeaders(deadline);
        }
        throw new IOException("HTTP proxy sent too many 1xx informational responses");
    }

    /**
     * Sends an HTTP CONNECT request to the proxy server.
     *
     * @param authorizationHeader the {@code Proxy-Authorization} value,
     *                            or {@code null} to omit
     * @throws IOException if the write fails
     */
    private void sendConnectRequest(String authorizationHeader) throws IOException {
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
        innerLayer.sendBinary(ByteBuffer.wrap(builder.toString().getBytes(StandardCharsets.US_ASCII)));
    }

    @Override
    public void disconnect() {
        innerLayer.disconnect();
    }

    @Override
    public boolean isConnected() {
        return innerLayer.isConnected();
    }

    @Override
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        innerLayer.sendBinary(buffers);
    }

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return innerLayer.readBinary(buffer, fully);
    }

    @Override
    public void finishConnect() throws IOException {
        innerLayer.finishConnect();
    }

    @Override
    public void finishConnect(ByteBuffer leftover) throws IOException {
        innerLayer.finishConnect(leftover);
    }

    @Override
    public void startHandshake(SocketClientLayerContext tlsContext, long timeout) throws IOException {
        innerLayer.startHandshake(tlsContext, timeout);
    }

    @Override
    public void registerLayerContext(Class<? extends SocketClientLayer> key, SocketClientLayerContext context) throws IOException {
        innerLayer.registerLayerContext(key, context);
    }
}