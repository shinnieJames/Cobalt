package com.github.auties00.cobalt.socket.tunnel.http;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.http.HttpResponseReader;
import com.github.auties00.cobalt.socket.tunnel.SocketClientTunnelLayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

        checkDeadline(deadline);
        var statusCode = sendConnectAndReadStatus(null, deadline);

        for (var attempt = 0; statusCode == PROXY_AUTH_REQUIRED_STATUS_CODE && attempt < MAX_AUTH_ATTEMPTS; attempt++) {
            var headers = consumeHeaders(deadline);
            responseReader.reset();

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
        responseReader.reset();
        return null;
    }

    /**
     * Handles a 3xx redirect response by parsing the {@code Location}
     * header and constructing a new proxy configuration.
     *
     * @param currentProxy the current proxy configuration
     * @param statusCode   the 3xx status code
     * @param deadline     the handshake deadline
     * @return the redirect target proxy configuration
     * @throws IOException if the response is malformed or has no
     *                     {@code Location} header
     */
    private WhatsAppClientProxy.Http handleRedirect(WhatsAppClientProxy.Http currentProxy, int statusCode, long deadline) throws IOException {
        var headers = consumeHeaders(deadline);
        responseReader.reset();
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
     * Throws {@link IOException} if the current time exceeds the given
     * deadline.
     *
     * @param deadline the absolute deadline timestamp in milliseconds
     * @throws IOException if the deadline has passed
     */
    private void checkDeadline(long deadline) throws IOException {
        responseReader.checkDeadline(deadline);
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
     *
     * @param authorizationHeader the {@code Proxy-Authorization} value,
     *                            or {@code null} to omit
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

    /**
     * Parses the HTTP status line from the proxy response.
     *
     * <p>Tolerates leading whitespace/CRLF junk and allows any amount of
     * whitespace between the HTTP version and the status code.  Accepts
     * both HTTP/1.0 and HTTP/1.1 responses.
     *
     * @param deadline the handshake deadline
     * @return the 3-digit HTTP status code
     * @throws IOException if the response is malformed or the deadline is
     *                     exceeded
     */
    private int readStatusLine(long deadline) throws IOException {
        return responseReader.readStatusLine(deadline);
    }

    /**
     * Parses the response headers into a multi-valued map.
     *
     * <p>Header names are lowercased for case-insensitive lookup.
     * Tolerates both {@code \r\n} and bare {@code \n} line endings.
     *
     * @param deadline the handshake deadline
     * @return the parsed headers
     * @throws IOException if the headers exceed
     *                     {@value #MAX_RESPONSE_HEADER_SIZE} bytes, the
     *                     connection closes, or the deadline is exceeded
     */
    private Map<String, List<String>> consumeHeaders(long deadline) throws IOException {
        return responseReader.consumeHeaders(deadline);
    }

    /**
     * Skips the response headers without parsing them.
     *
     * <p>Scans from the current stream position to the header terminator
     * (empty line) without allocating a headers map.
     *
     * @param deadline the handshake deadline
     * @throws IOException if the headers exceed
     *                     {@value #MAX_RESPONSE_HEADER_SIZE} bytes, the
     *                     connection closes, or the deadline is exceeded
     */
    private void skipHeaders(long deadline) throws IOException {
        responseReader.skipHeaders(deadline);
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
}
