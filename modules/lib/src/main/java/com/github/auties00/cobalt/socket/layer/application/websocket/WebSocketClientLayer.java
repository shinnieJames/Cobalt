package com.github.auties00.cobalt.socket.layer.application.websocket;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayer;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.encoder.WebSocketFrameEncoder;
import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.util.HttpResponseReader;
import com.github.auties00.cobalt.util.DataUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * A standalone WebSocket client that wraps a {@link SocketClientLayer}
 * transport stack.
 *
 * <p>This client performs the HTTP/1.1 WebSocket upgrade handshake over
 * the transport layer, then provides WebSocket binary frame encoding for
 * outbound messages.  Inbound WebSocket frame decoding is handled by the
 * {@link WebSocketClientLayerContext} registered with the selector pipeline.
 *
 * <p>After the upgrade completes, the connection transitions to
 * asynchronous data flow: the selector delivers decoded WebSocket data
 * frame payloads through the layer context chain to the listener.
 *
 * <p><strong>Performance notes:</strong> The upgrade handshake header
 * validation in {@link #consumeAndValidateHeaders} uses a two-tier
 * approach.  When the entire header line fits in the 8 KiB read buffer
 * (the common case for a 101 response), header names are matched with
 * bulk {@link HttpResponseReader#regionMatchesIgnoreCase} and values
 * are validated by direct buffer peeking — no per-byte method calls at
 * all.  The slow path (line spans buffers) is split into a separate
 * method so the JIT always inlines the fast path.
 *
 * @implNote Adapts WA Web's {@code WASocketTransport.openWebSocket}
 *     factory (module export {@code m}).  WA Web delegates the HTTP/1.1
 *     upgrade handshake, RFC 6455 framing and event dispatch to the
 *     browser's native {@code WebSocket} object and only synchronises on
 *     the {@code onopen}/{@code onclose} promise.  Cobalt performs the
 *     handshake, framing and dispatch itself on a layered selector
 *     model, which is why the bulk of this class has no direct WA Web
 *     counterpart.
 */
@WhatsAppWebModule(moduleName = "WASocketTransport")
public final class WebSocketClientLayer implements SocketClientApplicationLayer<WebSocketClientLayerContext> {
    /**
     * The WebSocket protocol GUID used when computing the
     * {@code Sec-WebSocket-Accept} hash as defined by
     * <a href="https://datatracker.ietf.org/doc/html/rfc6455#section-1.3">RFC 6455 §1.3</a>.
     */
    private static final byte[] WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII);

    /**
     * ASCII carriage-return byte used as HTTP line delimiter.
     */
    private static final byte CARRIAGE_RETURN = '\r';

    /**
     * ASCII line-feed byte used as HTTP line terminator.
     */
    private static final byte LINE_FEED = '\n';

    /**
     * ASCII space byte used as HTTP optional whitespace (OWS).
     */
    private static final byte SPACE = ' ';

    /**
     * Maximum time in milliseconds to wait for the upgrade handshake to
     * complete.
     */
    private static final long UPGRADE_TIMEOUT = 30_000;

    /**
     * Maximum total byte size of the upgrade response headers; enforced to
     * guard against unbounded header streams.
     */
    private static final int MAX_RESPONSE_HEADER_SIZE = 65_536;

    /**
     * Maximum number of whitespace characters allowed in the HTTP status
     * line; enforced by the response reader.
     */
    private static final int MAX_STATUS_LINE_SPACES = 64;

    /**
     * The HTTP status code expected from a successful WebSocket upgrade.
     */
    private static final int EXPECTED_STATUS_CODE = 101;

    /**
     * Lowercase ASCII bytes of the {@code Upgrade} header name, used for
     * case-insensitive comparison.
     */
    private static final byte[] HEADER_UPGRADE = "upgrade".getBytes(StandardCharsets.US_ASCII);

    /**
     * Lowercase ASCII bytes of the {@code Connection} header name, used
     * for case-insensitive comparison.
     */
    private static final byte[] HEADER_CONNECTION = "connection".getBytes(StandardCharsets.US_ASCII);

    /**
     * Lowercase ASCII bytes of the {@code Sec-WebSocket-Accept} header
     * name, used for case-insensitive comparison.
     */
    private static final byte[] HEADER_ACCEPT = "sec-websocket-accept".getBytes(StandardCharsets.US_ASCII);

    /**
     * Lowercase ASCII bytes of the expected {@code Upgrade} header value
     * ({@code "websocket"}).
     */
    private static final byte[] VALUE_WEBSOCKET = "websocket".getBytes(StandardCharsets.US_ASCII);

    /**
     * Lowercase ASCII bytes of the expected token in the
     * {@code Connection} header value ({@code "upgrade"}).
     */
    private static final byte[] VALUE_UPGRADE = "upgrade".getBytes(StandardCharsets.US_ASCII);

    /**
     * Bit flag recording that the {@code Upgrade} header has been validated.
     */
    private static final int FLAG_UPGRADE = 1;

    /**
     * Bit flag recording that the {@code Connection} header has been validated.
     */
    private static final int FLAG_CONNECTION = 2;

    /**
     * Bit flag recording that the {@code Sec-WebSocket-Accept} header has
     * been validated.
     */
    private static final int FLAG_ACCEPT = 4;

    /**
     * Bit flag set by the slow path to indicate that the terminating empty
     * header line has been reached.
     */
    private static final int FLAG_END = 8;

    /**
     * Pre-encoded HTTP request line prefix: {@code "GET "}.
     */
    private static final byte[] REQ_LINE_PREFIX = "GET ".getBytes(StandardCharsets.US_ASCII);

    /**
     * Pre-encoded HTTP version and Host header prefix:
     * {@code " HTTP/1.1\r\nHost: "}.
     */
    private static final byte[] REQ_HOST_HEADER = " HTTP/1.1\r\nHost: ".getBytes(StandardCharsets.US_ASCII);

    /**
     * Pre-encoded static headers block from the CRLF after Host through
     * the {@code Sec-WebSocket-Key} value prefix.
     */
    private static final byte[] REQ_STATIC_BLOCK = (
            "\r\nUpgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "Sec-WebSocket-Key: "
    ).getBytes(StandardCharsets.US_ASCII);

    /**
     * Pre-encoded Origin header prefix including leading CRLF:
     * {@code "\r\nOrigin: https://"}.
     */
    private static final byte[] REQ_ORIGIN = "\r\nOrigin: https://".getBytes(StandardCharsets.US_ASCII);

    /**
     * Pre-encoded request terminator: {@code "\r\n\r\n"}.
     */
    private static final byte[] REQ_END = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    /**
     * The inner layer stack (TCP + optional TLS + optional proxy).
     */
    private final SocketClientLayer<?> innerLayer;

    /**
     * The WebSocket endpoint path for the HTTP upgrade request.
     */
    private final String path;

    /**
     * The User-Agent header value for the WebSocket upgrade request.
     */
    private final byte[] userAgent;

    /**
     * The HTTP response reader for the upgrade handshake.
     */
    private final HttpResponseReader responseReader;

    /**
     * Leftover bytes from the HTTP upgrade response, to be fed into the
     * pipeline when {@link #finishConnect()} is called.
     */
    private ByteBuffer upgradeLeftover;

    /**
     * Creates a WebSocket application layer wrapping the given inner layer.
     *
     * @param innerLayer the inner layer stack (TCP + optional TLS
     *                   + optional proxy tunnel)
     * @param path       the WebSocket endpoint path (e.g. {@code "/ws"})
     * @param userAgent  the User-Agent string sent in the HTTP upgrade
     *                   request
     */
    public WebSocketClientLayer(SocketClientLayer<?> innerLayer, String path, String userAgent) {
        this.innerLayer = innerLayer;
        this.path = path;
        this.userAgent = ("\r\nUser-Agent: " + userAgent).getBytes(StandardCharsets.US_ASCII);
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
     * Connects the underlying transport stack to {@code address} and
     * performs the HTTP/1.1 WebSocket upgrade handshake synchronously.
     *
     * <p>On return the layer is attached to the selector pipeline and the
     * connection is ready to exchange binary WebSocket frames.  If the
     * handshake fails the method throws an {@link IOException} and the
     * caller is expected to discard the layer.
     *
     * @apiNote Equivalent to WA Web's {@code openWebSocket(url, onClose,
     *     onCloseWithId, onOpen)} factory: in WA Web the browser resolves
     *     the promise on {@code onopen} and rejects it on {@code onclose};
     *     Cobalt collapses both outcomes into this single blocking call
     *     that either returns normally (equivalent to {@code resolve})
     *     or throws (equivalent to {@code reject}).
     * @implNote WA Web's {@code onOpen}, {@code onClose} and
     *     {@code onCloseWithId} continuations are all mapped onto the
     *     caller's control flow: the caller continues after {@code connect}
     *     on success, and catches the thrown {@link IOException} on
     *     failure.
     * @param address  the remote endpoint
     * @param listener the callback for events
     * @throws IOException if the transport connection or the WebSocket
     *         upgrade handshake fails
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASocketTransport", exports = "openWebSocket", adaptation = WhatsAppAdaptation.ADAPTED)
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        innerLayer.connect(address, listener);
        innerLayer.registerLayerContext(new WebSocketClientLayerContext());

        performUpgrade(address.getHostString(), address.getPort(), path);
    }

    /**
     * Disconnects the underlying transport, tearing down the WebSocket
     * connection.
     *
     * @apiNote Equivalent to {@code WebSocketTransport.close()}: WA Web
     *     calls {@code underlyingWS.close()} and flips the closed flag;
     *     Cobalt delegates to {@link SocketClientLayer#disconnect()} on
     *     the inner layer, which closes the channel and notifies the
     *     listener.
     * @implNote The method is idempotent: a second call after the
     *     inner layer is already closed is a no-op because the transport
     *     layer tracks the connected state.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASocketTransport", exports = "WebSocketTransport", adaptation = WhatsAppAdaptation.ADAPTED)
    public void disconnect() {
        innerLayer.disconnect();
    }

    /**
     * Returns whether the underlying transport is still connected.
     *
     * @return {@code true} if the transport is connected
     */
    @Override
    public boolean isConnected() {
        return innerLayer.isConnected();
    }

    /**
     * Encodes {@code buffers} as one logical masked binary WebSocket
     * message and forwards the resulting frames to the inner layer.
     *
     * <p>If every buffer is {@code null} or empty, no bytes are written:
     * this matches WA Web's {@code requestSend()} guard
     * {@code if (dataToSend.size())} which suppresses empty sends.
     *
     * @apiNote Equivalent to {@code WebSocketTransport.requestSend()}
     *     plus the internal {@code dataToSend} accumulator buffer: WA Web
     *     appends payloads to {@code dataToSend} and then calls
     *     {@code underlyingWS.send(dataToSend.readBuffer())}; Cobalt
     *     bypasses the accumulator and encodes the supplied buffers
     *     directly, because callers already accumulate their own payloads
     *     before invoking this method.
     * @implNote WA Web's "WebSocket #id not opened" error log for sends
     *     after {@code close} has no direct equivalent: if the connection
     *     is closed, {@link SocketClientLayer#sendBinary(ByteBuffer...)}
     *     on the inner layer surfaces the condition by throwing.
     * @param buffers payload buffers in send order
     * @throws IOException if the inner layer fails to accept the frame
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASocketTransport", exports = "WebSocketTransport", adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        var encoded = WebSocketFrameEncoder.encodeBinaryMessage(buffers);
        if (encoded.length != 0) {
            innerLayer.sendBinary(encoded);
        }
    }

    /**
     * Reads binary bytes directly from the inner transport, bypassing the
     * WebSocket framing layer.
     *
     * <p>Used only during the WebSocket upgrade handshake when the HTTP
     * response parser needs raw bytes from the transport.
     *
     * @param buffer the destination buffer, in write mode
     * @param fully  {@code true} to fill the buffer, {@code false} to
     *               return after the first successful read
     * @return bytes read, or {@code -1} on end-of-stream
     * @throws IOException if reading fails
     */
    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return innerLayer.readBinary(buffer, fully);
    }

    /**
     * Finishes the connection setup and transitions to asynchronous data
     * flow, feeding any bytes buffered beyond the upgrade response into
     * the selector pipeline.
     *
     * <p>The HTTP response parser may have read past the terminating
     * {@code \r\n\r\n} if the server sent the first WebSocket frame in
     * the same TCP segment as the upgrade response.  Those leftover
     * bytes are passed through to the inner layer as pre-delivered
     * inbound data.
     *
     * @throws IOException if the transition fails
     */
    @Override
    public void finishConnect() throws IOException {
        if (upgradeLeftover != null) {
            innerLayer.finishConnect(upgradeLeftover);
            upgradeLeftover = null;
        } else {
            innerLayer.finishConnect();
        }
    }

    /**
     * Finishes the connection setup and feeds explicitly supplied
     * leftover bytes into the pipeline.
     *
     * @param leftover the leftover bytes in read mode, or {@code null}
     * @throws IOException if the transition fails
     */
    @Override
    public void finishConnect(ByteBuffer leftover) throws IOException {
        innerLayer.finishConnect(leftover);
    }

    /**
     * Drives a TLS handshake on the inner transport.
     *
     * @param tlsContext the TLS layer context
     * @param timeout    the handshake timeout in milliseconds
     * @throws IOException if the handshake fails or times out
     */
    @Override
    public void startHandshake(SocketClientLayerContext tlsContext, long timeout) throws IOException {
        innerLayer.startHandshake(tlsContext, timeout);
    }

    /**
     * Registers an additional layer context with the selector pipeline,
     * delegating to the inner layer.
     *
     * @param context the layer context to register
     * @throws IOException if registration fails
     */
    @Override
    public void registerLayerContext(SocketClientLayerContext context) throws IOException {
        innerLayer.registerLayerContext(context);
    }

    /**
     * Performs the HTTP/1.1 WebSocket upgrade handshake.
     *
     * <p>The WebSocket key and expected accept value are kept as
     * {@code byte[]} throughout to avoid intermediate {@link String}
     * allocations and charset re-encoding.
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

        var statusCode = responseReader.readStatusLine(deadline);
        if (statusCode != EXPECTED_STATUS_CODE) {
            throw new IOException("WebSocket upgrade failed with status code " + statusCode);
        }

        consumeAndValidateHeaders(deadline, expectedAccept);
        this.upgradeLeftover = responseReader.buffered();
        responseReader.reset();
    }

    /**
     * Generates a random 16-byte WebSocket key, Base64-encoded as a
     * {@code byte[]} to avoid an intermediate {@link String}.
     *
     * @return the Base64-encoded key as ASCII bytes
     */
    private static byte[] createWebSocketKey() {
        var raw = DataUtils.randomByteArray(16);
        return Base64.getEncoder().encode(raw);
    }

    /**
     * Computes the expected {@code Sec-WebSocket-Accept} value by hashing
     * the key concatenated with the WebSocket GUID using SHA-1.
     *
     * <p>Both input and output are {@code byte[]} to avoid charset
     * conversions.
     *
     * @param websocketKey the client-generated key as ASCII bytes
     * @return the expected Base64-encoded accept value as ASCII bytes
     * @throws IOException if SHA-1 is not available
     */
    private static byte[] computeExpectedAccept(byte[] websocketKey) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            digest.update(websocketKey);
            digest.update(WEBSOCKET_GUID);
            return Base64.getEncoder().encode(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("Cannot compute WebSocket accept key", exception);
        }
    }

    /**
     * Sends the HTTP/1.1 WebSocket upgrade request.
     *
     * <p>The request is assembled into a single pre-sized {@code byte[]}
     * using pre-encoded static fragments and {@link System#arraycopy},
     * avoiding {@link StringBuilder}, intermediate {@link String}
     * allocations, and charset-encoder overhead entirely.
     *
     * @param host         the target host
     * @param port         the target port
     * @param path         the target path
     * @param websocketKey the client-generated key as ASCII bytes
     * @throws IOException if the write fails
     */
    private void sendUpgradeRequest(String host, int port, String path, byte[] websocketKey) throws IOException {
        var hostBytes = host.getBytes(StandardCharsets.US_ASCII);
        var pathBytes = path.getBytes(StandardCharsets.US_ASCII);
        var portBytes = port != 443 ? Integer.toString(port).getBytes(StandardCharsets.US_ASCII) : null;

        var size = REQ_LINE_PREFIX.length + pathBytes.length + REQ_HOST_HEADER.length
                   + hostBytes.length + (portBytes != null ? 1 + portBytes.length : 0)
                   + REQ_STATIC_BLOCK.length + websocketKey.length
                   + userAgent.length
                   + REQ_ORIGIN.length + hostBytes.length + REQ_END.length;

        var request = new byte[size];
        var pos = 0;

        System.arraycopy(REQ_LINE_PREFIX, 0, request, pos, REQ_LINE_PREFIX.length);
        pos += REQ_LINE_PREFIX.length;

        System.arraycopy(pathBytes, 0, request, pos, pathBytes.length);
        pos += pathBytes.length;

        System.arraycopy(REQ_HOST_HEADER, 0, request, pos, REQ_HOST_HEADER.length);
        pos += REQ_HOST_HEADER.length;

        System.arraycopy(hostBytes, 0, request, pos, hostBytes.length);
        pos += hostBytes.length;

        if (portBytes != null) {
            request[pos++] = ':';
            System.arraycopy(portBytes, 0, request, pos, portBytes.length);
            pos += portBytes.length;
        }

        System.arraycopy(REQ_STATIC_BLOCK, 0, request, pos, REQ_STATIC_BLOCK.length);
        pos += REQ_STATIC_BLOCK.length;

        System.arraycopy(websocketKey, 0, request, pos, websocketKey.length);
        pos += websocketKey.length;

        System.arraycopy(userAgent, 0, request, pos, userAgent.length);
        pos += userAgent.length;

        System.arraycopy(REQ_ORIGIN, 0, request, pos, REQ_ORIGIN.length);
        pos += REQ_ORIGIN.length;

        System.arraycopy(hostBytes, 0, request, pos, hostBytes.length);
        pos += hostBytes.length;

        System.arraycopy(REQ_END, 0, request, pos, REQ_END.length);

        innerLayer.sendBinary(ByteBuffer.wrap(request));
    }

    /**
     * Consumes the HTTP response headers and validates the mandatory
     * WebSocket upgrade headers.
     *
     * <p>Two execution tiers:
     * <ol>
     *   <li><b>Fast path</b> (entire line in buffer — the common case
     *       for a 101 response with an 8 KiB read buffer): header names
     *       are matched with
     *       {@link HttpResponseReader#regionMatchesIgnoreCase}, values
     *       are validated with direct buffer peeking via
     *       {@link HttpResponseReader#peekBuffered}, and non-matching
     *       lines are skipped with a single
     *       {@link HttpResponseReader#skipBuffered} — zero per-byte
     *       method calls.</li>
     *   <li><b>Slow path</b> (line spans buffers — essentially never
     *       for typical upgrade responses): split into
     *       {@link #consumeHeaderLineSlow} so the JIT always inlines
     *       the fast path.  Uses per-byte reads with vectorised
     *       {@link HttpResponseReader#skipToEndOfLine} for
     *       non-matching lines.</li>
     * </ol>
     *
     * @param deadline            the handshake deadline
     * @param expectedAcceptBytes the expected {@code Sec-WebSocket-Accept}
     *                            value as ASCII bytes
     * @throws IOException if headers are missing, invalid, or the deadline
     *                     is exceeded
     */
    private void consumeAndValidateHeaders(long deadline, byte[] expectedAcceptBytes) throws IOException {
        responseReader.startHeaderSection();
        responseReader.skipToEndOfLine(deadline);

        var flags = 0;

        while (innerLayer.isConnected()) {
            if (responseReader.bufferedRemaining() == 0) {
                responseReader.refillBuffered(deadline);
            }

            var dist = responseReader.distanceToLineFeed();

            if (dist >= 0) {

                // Empty line — end of headers
                if (dist == 0 || (dist == 1 && responseReader.peekBuffered(0) == CARRIAGE_RETURN)) {
                    responseReader.accountHeaderBytes(dist + 1);
                    responseReader.skipBuffered(dist + 1);
                    break;
                }

                var contentLen = responseReader.peekBuffered(dist - 1) == CARRIAGE_RETURN
                        ? dist - 1
                        : dist;

                if ((flags & FLAG_UPGRADE) == 0
                    && contentLen > HEADER_UPGRADE.length
                    && responseReader.regionMatchesIgnoreCase(0, HEADER_UPGRADE, HEADER_UPGRADE.length)
                    && responseReader.peekBuffered(HEADER_UPGRADE.length) == ':') {
                    if (matchBufferedValueIgnoreCase(HEADER_UPGRADE.length + 1, contentLen, VALUE_WEBSOCKET)) {
                        flags |= FLAG_UPGRADE;
                    }
                } else if ((flags & FLAG_CONNECTION) == 0
                           && contentLen > HEADER_CONNECTION.length
                           && responseReader.regionMatchesIgnoreCase(0, HEADER_CONNECTION, HEADER_CONNECTION.length)
                           && responseReader.peekBuffered(HEADER_CONNECTION.length) == ':') {
                    if (bufferedConnectionContainsUpgrade(HEADER_CONNECTION.length + 1, contentLen)) {
                        flags |= FLAG_CONNECTION;
                    }
                } else if ((flags & FLAG_ACCEPT) == 0
                           && contentLen > HEADER_ACCEPT.length
                           && responseReader.regionMatchesIgnoreCase(0, HEADER_ACCEPT, HEADER_ACCEPT.length)
                           && responseReader.peekBuffered(HEADER_ACCEPT.length) == ':') {
                    if (matchBufferedValueExact(HEADER_ACCEPT.length + 1, contentLen, expectedAcceptBytes)) {
                        flags |= FLAG_ACCEPT;
                    }
                }

                responseReader.accountHeaderBytes(dist + 1);
                responseReader.skipBuffered(dist + 1);
            } else {
                flags = consumeHeaderLineSlow(deadline, flags, expectedAcceptBytes);
                if ((flags & FLAG_END) != 0) {
                    break;
                }
            }
        }

        if ((flags & FLAG_UPGRADE) == 0 || (flags & FLAG_CONNECTION) == 0) {
            throw new IOException("WebSocket upgrade response is missing mandatory headers");
        }

        if ((flags & FLAG_ACCEPT) == 0) {
            throw new IOException("WebSocket upgrade failed: invalid Sec-WebSocket-Accept");
        }
    }

    /**
     * Checks whether the buffered header value at the given offset
     * matches {@code expected} exactly, case-insensitive, with only
     * optional whitespace (OWS) surrounding the value.
     *
     * @param startOffset offset of the first byte after the colon
     * @param contentLen  the content length of the line (excluding CRLF)
     * @param expected    the expected value in lowercase ASCII
     * @return {@code true} if the value matches
     */
    private boolean matchBufferedValueIgnoreCase(int startOffset, int contentLen, byte[] expected) {
        var off = startOffset;
        while (off < contentLen && responseReader.peekBuffered(off) == SPACE) {
            off++;
        }
        if (contentLen - off < expected.length) {
            return false;
        }
        if (!responseReader.regionMatchesIgnoreCase(off, expected, expected.length)) {
            return false;
        }
        off += expected.length;
        while (off < contentLen && responseReader.peekBuffered(off) == SPACE) {
            off++;
        }
        return off == contentLen;
    }

    /**
     * Checks whether the buffered header value at the given offset
     * matches {@code expected} exactly, case-sensitive, with only
     * optional whitespace (OWS) surrounding the value.
     *
     * @param startOffset offset of the first byte after the colon
     * @param contentLen  the content length of the line (excluding CRLF)
     * @param expected    the expected value as ASCII bytes
     * @return {@code true} if the value matches
     */
    private boolean matchBufferedValueExact(int startOffset, int contentLen, byte[] expected) {
        var off = startOffset;
        while (off < contentLen && responseReader.peekBuffered(off) == SPACE) {
            off++;
        }
        if (contentLen - off < expected.length) {
            return false;
        }
        if (!responseReader.regionMatches(off, expected, expected.length)) {
            return false;
        }
        off += expected.length;
        while (off < contentLen && responseReader.peekBuffered(off) == SPACE) {
            off++;
        }
        return off == contentLen;
    }

    /**
     * Scans the buffered {@code Connection} header value for the
     * {@code "upgrade"} token in a comma-separated list.
     *
     * @param startOffset offset of the first byte after the colon
     * @param contentLen  the content length of the line (excluding CRLF)
     * @return {@code true} if the {@code "upgrade"} token is present
     */
    private boolean bufferedConnectionContainsUpgrade(int startOffset, int contentLen) {
        var off = startOffset;
        while (off < contentLen) {
            // Skip OWS before token
            while (off < contentLen && responseReader.peekBuffered(off) == SPACE) {
                off++;
            }

            // Try to match "upgrade" case-insensitively
            if (off + VALUE_UPGRADE.length <= contentLen
                && responseReader.regionMatchesIgnoreCase(off, VALUE_UPGRADE, VALUE_UPGRADE.length)) {
                var end = off + VALUE_UPGRADE.length;
                // Skip trailing OWS
                while (end < contentLen && responseReader.peekBuffered(end) == SPACE) {
                    end++;
                }
                // Must be at end of value or at comma separator
                if (end == contentLen || responseReader.peekBuffered(end) == ',') {
                    return true;
                }
            }

            // Skip to next comma
            while (off < contentLen && responseReader.peekBuffered(off) != ',') {
                off++;
            }
            if (off < contentLen) {
                off++; // skip the comma
            }
        }
        return false;
    }

    /**
     * Processes one header line using per-byte reads when the line spans
     * buffer boundaries.
     *
     * <p>Separated from the fast path so that the JIT always inlines
     * {@link #consumeAndValidateHeaders}.  Non-matching headers are
     * skipped with the vectorised
     * {@link HttpResponseReader#skipToEndOfLine}.
     *
     * @param deadline            the handshake deadline
     * @param flags               the current validation state bitmask
     * @param expectedAcceptBytes the expected accept value
     * @return the updated validation state; includes {@link #FLAG_END}
     *         if the empty header terminator was reached
     * @throws IOException on timeout, EOF, or size overflow
     */
    private int consumeHeaderLineSlow(long deadline, int flags, byte[] expectedAcceptBytes) throws IOException {
        var b = responseReader.nextHeaderByte(deadline);
        if (b == CARRIAGE_RETURN) {
            b = responseReader.nextHeaderByte(deadline);
        }
        if (b == LINE_FEED) {
            return flags | FLAG_END;
        }

        var lower = (byte) (b | 0x20);
        byte[] candidate;
        int flag;
        if (lower == 'u' && (flags & FLAG_UPGRADE) == 0) {
            candidate = HEADER_UPGRADE;
            flag = FLAG_UPGRADE;
        } else if (lower == 'c' && (flags & FLAG_CONNECTION) == 0) {
            candidate = HEADER_CONNECTION;
            flag = FLAG_CONNECTION;
        } else if (lower == 's' && (flags & FLAG_ACCEPT) == 0) {
            candidate = HEADER_ACCEPT;
            flag = FLAG_ACCEPT;
        } else {
            responseReader.skipToEndOfLine(deadline);
            return flags;
        }

        // Match rest of header name
        for (var i = 1; i < candidate.length; i++) {
            b = responseReader.nextHeaderByte(deadline);
            if ((b | 0x20) != candidate[i]) {
                if (b != LINE_FEED) {
                    responseReader.skipToEndOfLine(deadline);
                }
                return flags;
            }
        }

        b = responseReader.nextHeaderByte(deadline);
        if (b != ':') {
            if (b != LINE_FEED) {
                responseReader.skipToEndOfLine(deadline);
            }
            return flags;
        }

        // Skip OWS
        do {
            b = responseReader.nextHeaderByte(deadline);
        } while (b == SPACE);

        // Validate value
        boolean matched;
        if (candidate == HEADER_UPGRADE) {
            matched = matchValueExactIgnoreCaseSlow(b, VALUE_WEBSOCKET, deadline);
        } else if (candidate == HEADER_CONNECTION) {
            matched = matchConnectionValueSlow(b, deadline);
        } else {
            matched = matchValueExactSlow(b, expectedAcceptBytes, deadline);
        }

        return matched ? flags | flag : flags;
    }

    /**
     * Performs a per-byte case-insensitive match of a header value against
     * {@code expected}, also validating the subsequent line terminator.
     *
     * @param firstByte the byte already read as the first character of the
     *                  value, after any leading optional whitespace
     * @param expected  the expected value in lowercase ASCII
     * @param deadline  the handshake deadline used by the underlying reader
     * @return {@code true} if the value matches and the line terminates
     *         correctly, {@code false} otherwise
     * @throws IOException if reading fails, the deadline is exceeded, or
     *                     the stream is closed
     */
    private boolean matchValueExactIgnoreCaseSlow(byte firstByte, byte[] expected, long deadline) throws IOException {
        if ((firstByte | 0x20) != expected[0]) {
            responseReader.skipToEndOfLine(deadline);
            return false;
        }
        for (var i = 1; i < expected.length; i++) {
            var b = responseReader.nextHeaderByte(deadline);
            if ((b | 0x20) != expected[i]) {
                responseReader.skipToEndOfLine(deadline);
                return false;
            }
        }
        var b = responseReader.nextHeaderByte(deadline);
        while (b == SPACE) {
            b = responseReader.nextHeaderByte(deadline);
        }
        if (b == CARRIAGE_RETURN) {
            b = responseReader.nextHeaderByte(deadline);
        }
        return b == LINE_FEED;
    }

    /**
     * Performs a per-byte case-sensitive match of a header value against
     * {@code expected}, also validating the subsequent line terminator.
     *
     * @param firstByte the byte already read as the first character of the
     *                  value, after any leading optional whitespace
     * @param expected  the expected value as ASCII bytes
     * @param deadline  the handshake deadline used by the underlying reader
     * @return {@code true} if the value matches and the line terminates
     *         correctly, {@code false} otherwise
     * @throws IOException if reading fails, the deadline is exceeded, or
     *                     the stream is closed
     */
    private boolean matchValueExactSlow(byte firstByte, byte[] expected, long deadline) throws IOException {
        if (firstByte != expected[0]) {
            responseReader.skipToEndOfLine(deadline);
            return false;
        }
        for (var i = 1; i < expected.length; i++) {
            var b = responseReader.nextHeaderByte(deadline);
            if (b != expected[i]) {
                responseReader.skipToEndOfLine(deadline);
                return false;
            }
        }
        var b = responseReader.nextHeaderByte(deadline);
        while (b == SPACE) {
            b = responseReader.nextHeaderByte(deadline);
        }
        if (b == CARRIAGE_RETURN) {
            b = responseReader.nextHeaderByte(deadline);
        }
        return b == LINE_FEED;
    }

    /**
     * Scans a comma-separated {@code Connection} header value byte by byte
     * for the case-insensitive token {@code "upgrade"}.
     *
     * <p>The scan accepts the token anywhere in the list (for example
     * {@code "keep-alive, upgrade"}), requiring that the token is
     * terminated by optional whitespace followed by a comma or the line
     * terminator.
     *
     * @param firstByte the byte already read as the first character of the
     *                  value, after any leading optional whitespace
     * @param deadline  the handshake deadline used by the underlying reader
     * @return {@code true} if the token is present, {@code false} otherwise
     * @throws IOException if reading fails, the deadline is exceeded, or
     *                     the stream is closed
     */
    private boolean matchConnectionValueSlow(byte firstByte, long deadline) throws IOException {
        var b = firstByte;
        while (true) {
            // Skip OWS before token
            while (b == SPACE) {
                b = responseReader.nextHeaderByte(deadline);
            }

            // Try to match "upgrade"
            var matchIndex = 0;
            if ((b | 0x20) == VALUE_UPGRADE[0]) {
                matchIndex = 1;
                while (matchIndex < VALUE_UPGRADE.length) {
                    b = responseReader.nextHeaderByte(deadline);
                    if ((b | 0x20) != VALUE_UPGRADE[matchIndex]) {
                        break;
                    }
                    matchIndex++;
                }
            }

            if (matchIndex == VALUE_UPGRADE.length) {
                // Skip trailing OWS
                do {
                    b = responseReader.nextHeaderByte(deadline);
                } while (b == SPACE);

                if (b == ',' || b == CARRIAGE_RETURN || b == LINE_FEED) {
                    if (b != LINE_FEED) {
                        responseReader.skipToEndOfLine(deadline);
                    }
                    return true;
                }
            }

            // Skip to next comma or end of line
            while (b != ',' && b != LINE_FEED) {
                if (b == CARRIAGE_RETURN) {
                    b = responseReader.nextHeaderByte(deadline);
                    continue;
                }
                b = responseReader.nextHeaderByte(deadline);
            }

            if (b == LINE_FEED) {
                return false;
            }

            b = responseReader.nextHeaderByte(deadline);
        }
    }
}