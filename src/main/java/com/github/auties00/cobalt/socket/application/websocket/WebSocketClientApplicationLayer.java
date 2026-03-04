package com.github.auties00.cobalt.socket.application.websocket;

import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.application.SocketClientApplicationLayer;
import com.github.auties00.cobalt.socket.application.websocket.frame.encoder.WebSocketFrameEncoder;
import com.github.auties00.cobalt.socket.http.HttpResponseReader;
import com.github.auties00.cobalt.socket.threading.SocketClientSelector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An application layer that provides WebSocket framing over an existing
 * connection.
 *
 * <p>Outbound data is wrapped in WebSocket binary frames before being
 * passed to the inner layer.  Inbound WebSocket frame decoding is handled
 * by the {@link WebSocketLayerContext} on the selector thread.
 *
 * <p>During {@link #connect(InetSocketAddress, SocketClientLayerListener)},
 * this layer performs the HTTP/1.1 WebSocket upgrade handshake (RFC 6455
 * opening handshake) over the inner layer.  The handshake generates a
 * random {@code Sec-WebSocket-Key}, sends the {@code GET} upgrade request,
 * and validates the server's {@code 101 Switching Protocols} response
 * including the {@code Sec-WebSocket-Accept} header.
 *
 * <p>The upgrade response is parsed byte-by-byte with amortized buffered
 * reads (512-byte shared buffer), matching the same pattern used by the
 * HTTP CONNECT tunnel layer.  Header names are matched inline using
 * first-byte dispatch and incremental byte comparison without allocating
 * any strings, char arrays, or maps.  Header values are validated
 * byte-by-byte against known expected values.
 *
 * <p>After the upgrade completes, the connection is marked ready via the
 * central {@link SocketClientSelector} and any leftover bytes or buffered
 * TLS application data are fed into the WebSocket frame decoder pipeline.
 *
 * <p>The {@link #init(SocketChannel)} method must be called after the
 * inner layer has connected and before
 * {@link #connect(InetSocketAddress, SocketClientLayerListener)} is
 * invoked, to provide the NIO channel reference needed for selector
 * interaction.
 */
public final class WebSocketClientApplicationLayer implements SocketClientApplicationLayer {
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
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer innerLayer;

    /**
     * The NIO socket channel, set by {@link #init(SocketChannel)}.
     */
    private SocketChannel channel;

    private final HttpResponseReader responseReader;

    /**
     * Creates a WebSocket application layer wrapping the given inner layer.
     *
     * @param innerLayer the layer below
     */
    public WebSocketClientApplicationLayer(SocketClientLayer innerLayer) {
        this.innerLayer = innerLayer;
        this.responseReader = new HttpResponseReader(
                innerLayer,
                "WebSocket HTTP upgrade timed out",
                "WebSocket response headers exceed maximum size",
                "Unexpected end of stream during WebSocket upgrade",
                MAX_RESPONSE_HEADER_SIZE,
                MAX_STATUS_LINE_SPACES
        );
    }

    /**
     * Initializes this layer with the NIO channel.
     *
     * <p>Must be called after the transport layer has connected and before
     * {@link #connect(InetSocketAddress, SocketClientLayerListener)}.
     *
     * @param channel the NIO socket channel
     */
    public void init(SocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Connects to the target endpoint and performs the WebSocket upgrade
     * handshake.
     *
     * <p>First delegates to the inner layer to establish the underlying
     * connection (which may include proxy tunnelling and TLS).  Then
     * performs the HTTP/1.1 WebSocket upgrade handshake using synchronous
     * reads through the inner layer.
     *
     * <p>After a successful upgrade, marks the connection as ready via the
     * central selector, feeds any leftover bytes from the HTTP response
     * buffer into the WebSocket frame decoder, and drains any buffered TLS
     * application data.
     *
     * @param address  the target endpoint
     * @param listener the callback for events
     * @throws IOException if the connection or upgrade handshake fails
     */
    public void connect(URI address, SocketClientLayerListener listener) throws IOException {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        innerLayer.connect(InetSocketAddress.createUnresolved(address.getHost(), address.getPort()), listener);
        performUpgrade(address.getHost(), address.getPort(), address.getRawPath());
    }

    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        innerLayer.connect(address, listener);
        performUpgrade(address.getHostString(), address.getPort(), "/");
    }

    @Override
    public void disconnect() {
        innerLayer.disconnect();
    }

    @Override
    public boolean isConnected() {
        return innerLayer.isConnected();
    }

    /**
     * Wraps all provided buffers into one WebSocket binary message and sends
     * it through the inner layer.
     *
     * @param buffers the message payload buffers
     */
    @Override
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        var encoded = WebSocketFrameEncoder.encodeBinaryMessage(buffers);
        if (encoded.length != 0) {
            innerLayer.sendBinary(encoded);
        }
    }

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return innerLayer.readBinary(buffer, fully);
    }

    /**
     * Performs the HTTP/1.1 WebSocket upgrade handshake.
     *
     * <p>Generates a random {@code Sec-WebSocket-Key}, sends the upgrade
     * request, reads and validates the server response, marks the
     * connection ready, and feeds any leftover data into the frame
     * decoder pipeline.
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

        if (!SocketClientSelector.INSTANCE.completeUpgrade(channel, leftover)) {
            throw new IOException("Failed to finalize transport state after WebSocket upgrade");
        }
        responseReader.reset();
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
     * @param websocketKey the client-generated key
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
        innerLayer.sendBinary(ByteBuffer.wrap(builder.toString().getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Parses the HTTP status line from the upgrade response.
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
     * Consumes the HTTP response headers and validates the mandatory
     * WebSocket upgrade headers inline.
     *
     * <p>After the 3-digit status code consumed by
     * {@link #readStatusLine(long)}, the status line remainder (reason
     * phrase) is still in the stream and is skipped first.
     *
     * <p>Each header line is parsed byte-by-byte with first-byte dispatch
     * to identify candidate header names, then incremental byte comparison
     * against the expected name.  On a colon, leading whitespace in the
     * value is skipped, and the value is validated byte-by-byte against
     * the expected value for that header.  No strings, char arrays, or
     * maps are allocated during header parsing.
     *
     * @param deadline            the handshake deadline
     * @param expectedAcceptBytes the expected {@code Sec-WebSocket-Accept}
     *                            value as ASCII bytes
     * @throws IOException if the headers exceed
     *                     {@value #MAX_RESPONSE_HEADER_SIZE} bytes, a
     *                     mandatory header is missing or invalid, the
     *                     connection closes, or the deadline is exceeded
     */
    private void consumeAndValidateHeaders(long deadline, byte[] expectedAcceptBytes) throws IOException {
        responseReader.startHeaderSection();
        var hasUpgrade = false;
        var hasConnectionUpgrade = false;
        var acceptValid = false;

        skipToEndOfLine(deadline);

        while (innerLayer.isConnected()) {
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

            b = nextHeaderByte(deadline);
            while (b == SPACE) {
                b = nextHeaderByte(deadline);
            }

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
     * expected bytes, then verifies the line ends with optional trailing
     * whitespace and a line feed.  Consumes all bytes through the end of
     * the line.
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
     * expected bytes, then verifies the line ends with optional trailing
     * whitespace and a line feed.  Consumes all bytes through the end of
     * the line.
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
     * {@code "upgrade"} token in a comma-separated list.  Each token is
     * trimmed of leading whitespace and matched case-insensitively.
     * Consumes all bytes through the end of the line.
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
     * Reads the next byte from the response stream during header parsing,
     * enforcing the {@link #MAX_RESPONSE_HEADER_SIZE} safety limit.
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
