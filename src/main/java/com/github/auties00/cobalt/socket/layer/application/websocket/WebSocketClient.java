package com.github.auties00.cobalt.socket.layer.application.websocket;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.encoder.WebSocketFrameEncoder;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayer;
import com.github.auties00.cobalt.socket.layer.tunnel.TunnelLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.http.HttpResponseReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A standalone WebSocket client that wraps a {@link SocketClientLayer}
 * transport stack.
 *
 * <p>This client performs the HTTP/1.1 WebSocket upgrade handshake over
 * the transport layer, then provides WebSocket binary frame encoding for
 * outbound messages.  Inbound WebSocket frame decoding is handled by the
 * {@link WebSocketLayerContext} registered with the selector pipeline.
 *
 * <p>After the upgrade completes, the connection transitions to
 * asynchronous data flow: the selector delivers decoded WebSocket data
 * frame payloads through the layer context chain to the listener.
 */
public final class WebSocketClient {
    private static final byte[] WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII);
    private static final int HTTP_VERSION_MAJOR = 1;
    private static final int HTTP_VERSION_MINOR_MAX = 1;
    private static final byte CARRIAGE_RETURN = '\r';
    private static final byte LINE_FEED = '\n';
    private static final byte SPACE = ' ';
    private static final long UPGRADE_TIMEOUT = 30_000;
    private static final int MAX_RESPONSE_HEADER_SIZE = 65_536;
    private static final int MAX_STATUS_LINE_SPACES = 64;
    private static final int EXPECTED_STATUS_CODE = 101;
    private static final byte[] HEADER_UPGRADE = "upgrade".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEADER_CONNECTION = "connection".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEADER_ACCEPT = "sec-websocket-accept".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VALUE_WEBSOCKET = "websocket".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VALUE_UPGRADE = "upgrade".getBytes(StandardCharsets.US_ASCII);

    /**
     * The transport layer stack (TCP + optional TLS + optional proxy).
     */
    private final SocketClientLayer transportLayer;

    /**
     * The HTTP response reader for the upgrade handshake.
     */
    private final HttpResponseReader responseReader;

    /**
     * Creates a WebSocket client wrapping the given transport layer.
     *
     * @param transportLayer the transport layer stack
     */
    public WebSocketClient(SocketClientLayer transportLayer) {
        this.transportLayer = transportLayer;
        this.responseReader = new HttpResponseReader(
                transportLayer,
                "WebSocket HTTP upgrade timed out",
                "WebSocket response headers exceed maximum size",
                "Unexpected end of stream during WebSocket upgrade",
                MAX_RESPONSE_HEADER_SIZE,
                MAX_STATUS_LINE_SPACES
        );
    }

    /**
     * Connects to the specified WebSocket URI and performs the upgrade
     * handshake.
     *
     * <p>First connects the transport layer to the target host, then
     * performs the HTTP/1.1 WebSocket upgrade.  After a successful
     * upgrade, registers the WebSocket layer context with the selector
     * pipeline (chained above the caller-provided {@code nextLayer}),
     * transitions to asynchronous mode, and feeds any leftover bytes
     * from the HTTP response into the pipeline.
     *
     * @param address   the WebSocket URI (e.g. {@code wss://host/path})
     * @param nextLayer the layer context above WebSocket in the inbound
     *                  pipeline (receives decoded data frame payloads)
     * @param listener  the callback for events (used only for connect)
     * @throws IOException if the connection or upgrade fails
     */
    public void connect(URI address, SocketClientLayerContext nextLayer, SocketClientLayerListener listener) throws IOException {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(nextLayer, "nextLayer cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        var port = address.getPort() != -1 ? address.getPort() : 443;
        transportLayer.connect(
                InetSocketAddress.createUnresolved(address.getHost(), port),
                listener
        );

        var wsContext = new WebSocketLayerContext(nextLayer);
        var gatingContext = new TunnelLayerContext(wsContext, false);
        transportLayer.registerLayerContext(SocketClientTunnelLayer.class, gatingContext);

        performUpgrade(address.getHost(), port, address.getRawPath());
    }

    /**
     * Disconnects the WebSocket connection.
     */
    public void disconnect() {
        transportLayer.disconnect();
    }

    /**
     * Returns whether the WebSocket connection is active.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return transportLayer.isConnected();
    }

    /**
     * Wraps all provided buffers into one WebSocket binary message and
     * sends it through the transport layer.
     *
     * @param buffers the message payload buffers
     * @throws IOException if the write fails
     */
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        var encoded = WebSocketFrameEncoder.encodeBinaryMessage(buffers);
        if (encoded.length != 0) {
            transportLayer.sendBinary(encoded);
        }
    }

    /**
     * Reads binary bytes from the transport layer.
     *
     * @param buffer the destination buffer
     * @param fully  {@code true} to fill the buffer completely
     * @return bytes read, or {@code -1} on end-of-stream
     * @throws IOException if reading fails
     */
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return transportLayer.readBinary(buffer, fully);
    }

    /**
     * Performs the HTTP/1.1 WebSocket upgrade handshake.
     *
     * @param host the target host name
     * @param port the target port
     * @param path the target path
     * @throws IOException if the handshake fails
     */
    private void performUpgrade(String host, int port, String path) throws IOException {
        var websocketKey = createWebSocketKey();
        var expectedAccept = computeExpectedAccept(websocketKey);
        sendUpgradeRequest(host, port, path, websocketKey);

        var deadline = System.currentTimeMillis() + UPGRADE_TIMEOUT;
        responseReader.reset();

        var statusCode = readStatusLine(deadline);
        if (statusCode != EXPECTED_STATUS_CODE) {
            throw new IOException("WebSocket upgrade failed with status code " + statusCode);
        }

        consumeAndValidateHeaders(deadline, expectedAccept.getBytes(StandardCharsets.US_ASCII));
        var leftover = responseReader.remainingBytes();
        responseReader.reset();
        transportLayer.finishConnect(leftover);
    }

    /**
     * Generates a random 16-byte WebSocket key, Base64-encoded.
     *
     * @return the Base64-encoded key
     */
    private static String createWebSocketKey() {
        var raw = new byte[16];
        ThreadLocalRandom.current().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    /**
     * Computes the expected {@code Sec-WebSocket-Accept} value by hashing
     * the key concatenated with the WebSocket GUID using SHA-1.
     *
     * @param websocketKey the client-generated key
     * @return the expected Base64-encoded accept value
     * @throws IOException if SHA-1 is not available
     */
    private static String computeExpectedAccept(String websocketKey) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            digest.update(websocketKey.getBytes(StandardCharsets.US_ASCII));
            digest.update(WEBSOCKET_GUID);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("Cannot compute WebSocket accept key", exception);
        }
    }

    /**
     * Sends the HTTP/1.1 WebSocket upgrade request.
     *
     * @param host         the target host
     * @param port         the target port
     * @param path         the target path
     * @param websocketKey the client-generated key
     * @throws IOException if the write fails
     */
    private void sendUpgradeRequest(String host, int port, String path, String websocketKey) throws IOException {
        var builder = new StringBuilder();
        builder.append("GET ")
                .append(path)
                .append(" HTTP/")
                .append(HTTP_VERSION_MAJOR)
                .append('.')
                .append(HTTP_VERSION_MINOR_MAX)
                .append("\r\n");
        builder.append("Host: ");
        if (port == 443) {
            builder.append(host);
        } else {
            builder.append(host).append(':').append(port);
        }
        builder.append("\r\n");
        builder.append("Upgrade: websocket\r\n");
        builder.append("Connection: Upgrade\r\n");
        builder.append("Sec-WebSocket-Version: 13\r\n");
        builder.append("Sec-WebSocket-Key: ")
                .append(websocketKey)
                .append("\r\n");
        builder.append("Origin: https://")
                .append(host)
                .append("\r\n");
        builder.append("\r\n");
        transportLayer.sendBinary(ByteBuffer.wrap(builder.toString().getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Parses the HTTP status line from the upgrade response.
     *
     * @param deadline the handshake deadline
     * @return the 3-digit HTTP status code
     * @throws IOException if the response is malformed or the deadline is exceeded
     */
    private int readStatusLine(long deadline) throws IOException {
        return responseReader.readStatusLine(deadline);
    }

    /**
     * Consumes the HTTP response headers and validates the mandatory
     * WebSocket upgrade headers inline.
     *
     * @param deadline            the handshake deadline
     * @param expectedAcceptBytes the expected {@code Sec-WebSocket-Accept}
     *                            value as ASCII bytes
     * @throws IOException if headers are missing, invalid, or the deadline
     *                     is exceeded
     */
    private void consumeAndValidateHeaders(long deadline, byte[] expectedAcceptBytes) throws IOException {
        responseReader.startHeaderSection();
        var hasUpgrade = false;
        var hasConnectionUpgrade = false;
        var acceptValid = false;

        skipToEndOfLine(deadline);

        while (transportLayer.isConnected()) {
            var b = nextHeaderByte(deadline);
            if (b == CARRIAGE_RETURN) {
                b = nextHeaderByte(deadline);
            }
            if (b == LINE_FEED) {
                break;
            }

            var lower = (byte) (b | 0x20);
            byte[] candidate;
            if (lower == 'u') {
                candidate = HEADER_UPGRADE;
            } else if (lower == 'c') {
                candidate = HEADER_CONNECTION;
            } else if (lower == 's') {
                candidate = HEADER_ACCEPT;
            } else {
                skipToEndOfLine(deadline);
                continue;
            }

            var matched = true;
            for (var i = 1; i < candidate.length; i++) {
                b = nextHeaderByte(deadline);
                if ((b | 0x20) != candidate[i]) {
                    matched = false;
                    break;
                }
            }

            if (!matched) {
                skipToEndOfLine(deadline);
                continue;
            }

            b = nextHeaderByte(deadline);
            if (b != ':') {
                skipToEndOfLine(deadline);
                continue;
            }

            do {
                b = nextHeaderByte(deadline);
            } while (b == SPACE);

            if (candidate == HEADER_UPGRADE) {
                hasUpgrade = matchValueExactIgnoreCase(b, VALUE_WEBSOCKET, deadline);
            } else if (candidate == HEADER_CONNECTION) {
                hasConnectionUpgrade = matchConnectionValue(b, deadline);
            } else {
                acceptValid = matchValueExact(b, expectedAcceptBytes, deadline);
            }
        }

        if (!hasUpgrade || !hasConnectionUpgrade) {
            throw new IOException("WebSocket upgrade response is missing mandatory headers");
        }

        if (!acceptValid) {
            throw new IOException("WebSocket upgrade failed: invalid Sec-WebSocket-Accept");
        }
    }

    /**
     * Matches a header value exactly (case-insensitive) against the given
     * expected bytes, then verifies the line ends properly.
     *
     * @param firstByte the first non-space byte of the value
     * @param expected  the expected value in lowercase ASCII
     * @param deadline  the handshake deadline
     * @return {@code true} if the value matches exactly
     * @throws IOException if a read fails or the deadline is exceeded
     */
    private boolean matchValueExactIgnoreCase(byte firstByte, byte[] expected, long deadline) throws IOException {
        if ((firstByte | 0x20) != expected[0]) {
            skipToEndOfLine(deadline);
            return false;
        }
        for (var i = 1; i < expected.length; i++) {
            var b = nextHeaderByte(deadline);
            if ((b | 0x20) != expected[i]) {
                skipToEndOfLine(deadline);
                return false;
            }
        }
        var b = nextHeaderByte(deadline);
        while (b == SPACE) {
            b = nextHeaderByte(deadline);
        }
        if (b == CARRIAGE_RETURN) {
            b = nextHeaderByte(deadline);
        }
        return b == LINE_FEED;
    }

    /**
     * Matches a header value exactly (case-sensitive) against the given
     * expected bytes, then verifies the line ends properly.
     *
     * @param firstByte the first non-space byte of the value
     * @param expected  the expected value as ASCII bytes
     * @param deadline  the handshake deadline
     * @return {@code true} if the value matches exactly
     * @throws IOException if a read fails or the deadline is exceeded
     */
    private boolean matchValueExact(byte firstByte, byte[] expected, long deadline) throws IOException {
        if (firstByte != expected[0]) {
            skipToEndOfLine(deadline);
            return false;
        }
        for (var i = 1; i < expected.length; i++) {
            var b = nextHeaderByte(deadline);
            if (b != expected[i]) {
                skipToEndOfLine(deadline);
                return false;
            }
        }
        var b = nextHeaderByte(deadline);
        while (b == SPACE) {
            b = nextHeaderByte(deadline);
        }
        if (b == CARRIAGE_RETURN) {
            b = nextHeaderByte(deadline);
        }
        return b == LINE_FEED;
    }

    /**
     * Checks whether the {@code Connection} header value contains the
     * {@code "upgrade"} token in a comma-separated list.
     *
     * @param firstByte the first non-space byte of the value
     * @param deadline  the handshake deadline
     * @return {@code true} if the {@code "upgrade"} token is present
     * @throws IOException if a read fails or the deadline is exceeded
     */
    private boolean matchConnectionValue(byte firstByte, long deadline) throws IOException {
        var b = firstByte;
        while (true) {
            while (b == SPACE) {
                b = nextHeaderByte(deadline);
            }

            var matchIndex = 0;
            if ((b | 0x20) == VALUE_UPGRADE[0]) {
                matchIndex = 1;
                while (matchIndex < VALUE_UPGRADE.length) {
                    b = nextHeaderByte(deadline);
                    if ((b | 0x20) != VALUE_UPGRADE[matchIndex]) {
                        break;
                    }
                    matchIndex++;
                }
            }

            if (matchIndex == VALUE_UPGRADE.length) {
                b = nextHeaderByte(deadline);
                while (b == SPACE) {
                    b = nextHeaderByte(deadline);
                }
                if (b == ',' || b == CARRIAGE_RETURN || b == LINE_FEED) {
                    if (b != LINE_FEED) {
                        skipToEndOfLine(deadline);
                    }
                    return true;
                }
            }

            while (b != ',' && b != LINE_FEED) {
                if (b == CARRIAGE_RETURN) {
                    b = nextHeaderByte(deadline);
                    continue;
                }
                b = nextHeaderByte(deadline);
            }

            if (b == LINE_FEED) {
                return false;
            }

            b = nextHeaderByte(deadline);
        }
    }

    /**
     * Reads the next byte from the response stream during header parsing.
     *
     * @param deadline the handshake deadline
     * @return the next byte
     * @throws IOException if the limit is exceeded or the read fails
     */
    private byte nextHeaderByte(long deadline) throws IOException {
        return responseReader.nextHeaderByte(deadline);
    }

    /**
     * Skips all bytes until and including the next line feed.
     *
     * @param deadline the handshake deadline
     * @throws IOException if a read fails or the deadline is exceeded
     */
    private void skipToEndOfLine(long deadline) throws IOException {
        responseReader.skipToEndOfLine(deadline);
    }
}
