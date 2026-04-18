package com.github.auties00.cobalt.socket.layer.application.websocket;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientPendingRead;
import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayerContext;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.WebSocketFrameConstants;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.decoder.WebSocketDecodedFrame;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.decoder.WebSocketFrameDecoder;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.encoder.WebSocketFrameEncoder;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A layer context that handles WebSocket frame decoding and encoding for
 * a single connection.
 *
 * <p>Adapts the behaviour of WA Web's {@code WebSocketTransport} class
 * (module {@code WASocketTransport}).  The WA Web class simply wires
 * browser {@code onmessage}/{@code onclose}/{@code onerror} callbacks to
 * {@code onData}/{@code onClose}/{@code onError} consumers; here the
 * same dispatch happens inside the selector's inbound pipeline, where
 * decoded data frames flow up to the next layer and control frames
 * (PING, PONG, CLOSE) are echoed or ignored according to
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC 6455</a>.
 *
 * <p>Unlike WA Web, which delegates RFC 6455 framing entirely to the
 * browser, Cobalt implements the wire format itself via
 * {@link WebSocketFrameDecoder} and {@link WebSocketFrameEncoder}.  The
 * CLOSE echo, PING → PONG echo and upgrade-phase pending-read redirection
 * have no WA Web counterpart for this reason.
 *
 * @implNote WA Web's {@code WebSocketTransport.$3} ({@code onmessage}),
 *     {@code $4} ({@code onclose}), {@code $5} ({@code onerror}) and
 *     {@code $6} (shared close helper) map onto
 *     {@link #feedWebSocket(ByteBuffer)}, {@link #onDisconnect()} and
 *     {@link #handleControl(byte, byte[], int)} respectively.
 */
@WhatsAppWebModule(moduleName = "WASocketTransport")
final class WebSocketClientLayerContext implements SocketClientApplicationLayerContext {
    /**
     * Capacity of the inbound frame-decode buffer in bytes.  Sized to
     * hold a typical WebSocket frame batch without excessive refills.
     */
    private static final int READ_BUFFER_SIZE = 16384;

    /**
     * The next layer context in the chain (the layer above WebSocket).
     * Set via {@link #setNextLayer(SocketClientLayerContext)} by the
     * selector's chain rebuilding.
     */
    private volatile SocketClientLayerContext nextLayer;

    /**
     * The previous layer context in the chain (the layer below WebSocket).
     * Set via {@link #setPrevLayer(SocketClientLayerContext)} by the
     * selector's chain wiring.  Outbound bytes flow to this layer.
     */
    private volatile SocketClientLayerContext prevLayer;

    /**
     * Buffer for raw or TLS-decrypted bytes before WebSocket frame
     * decoding.  Used as the inbound target when the selector or TLS
     * layer needs to place bytes.
     */
    private final ByteBuffer readBuffer;

    /**
     * Stateful WebSocket frame decoder.
     */
    private final WebSocketFrameDecoder frameDecoder;

    /**
     * Set to {@code true} after a WebSocket CLOSE frame has been received
     * and the close echo has been enqueued.  On the next
     * {@link #processInbound(int)} call, the connection is closed.
     */
    private boolean closePending;

    /**
     * The pending blocking-read request for the HTTP upgrade phase, or
     * {@code null} after the upgrade completes.
     *
     * <p>During the WebSocket HTTP upgrade, the caller thread reads the
     * upgrade response through a blocking {@link SocketClientPendingRead}.
     * While this field is non-null, {@link #inboundTarget()} returns the
     * read's buffer and {@link #processInbound(int)} drains into it
     * instead of invoking the WebSocket frame decoder.
     */
    private volatile SocketClientPendingRead pendingUpgradeRead;

    /**
     * Creates a WebSocket layer context with a freshly allocated
     * {@value #READ_BUFFER_SIZE}-byte direct read buffer and a new
     * stateful {@link WebSocketFrameDecoder}.
     */
    WebSocketClientLayerContext() {
        this.readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
        this.frameDecoder = new WebSocketFrameDecoder();
    }

    /**
     * Sets the next layer context in the inbound chain.
     *
     * <p>Decoded WebSocket data frames are forwarded to this layer via
     * {@link SocketClientLayerContext#feedFromSource(ByteBuffer)}.
     *
     * @param next the next layer context
     */
    @Override
    public void setNextLayer(SocketClientLayerContext next) {
        this.nextLayer = next;
    }

    /**
     * Sets the previous layer context in the outbound chain.
     *
     * @param prev the previous layer context
     */
    @Override
    public void setPrevLayer(SocketClientLayerContext prev) {
        this.prevLayer = prev;
    }

    /**
     * Returns the previous layer context in the outbound chain.
     *
     * @return the previous layer context, or {@code null} if unset
     */
    @Override
    public SocketClientLayerContext prevLayer() {
        return prevLayer;
    }

    /**
     * Returns the inbound target buffer.
     *
     * <p>During the WebSocket HTTP upgrade phase (while
     * {@link #pendingUpgradeRead} is set), returns the pending read's
     * destination buffer so the upgrade response lands directly where the
     * blocking reader expects it.  After the upgrade completes, returns
     * the regular frame-decode read buffer.
     *
     * @return the buffer to read into
     */
    @Override
    public ByteBuffer inboundTarget() {
        var read = pendingUpgradeRead;
        if (read != null) {
            return read.buffer;
        }
        return readBuffer;
    }

    /**
     * Processes inbound bytes.
     *
     * <p>If a pending upgrade read is active, delivers the bytes into it
     * and notifies the waiting thread when the read is satisfied.
     * Otherwise decodes WebSocket frames from the read buffer.
     *
     * <p>Control frames are handled internally:
     * <ul>
     * <li>PING: returns {@link SocketClientInboundResult.NeedsWrite} with a PONG
     * <li>CLOSE: returns {@link SocketClientInboundResult.NeedsWrite} with a CLOSE
     *     echo, followed by {@link SocketClientInboundResult.Close}
     * <li>PONG: ignored
     * </ul>
     *
     * @param bytesRead the number of bytes placed into the read buffer,
     *                  or -1 for end-of-stream
     * @return the processing result
     * @throws IOException if layer processing fails
     */
    @Override
    public SocketClientInboundResult processInbound(int bytesRead) throws IOException {
        if (pendingUpgradeRead != null) {
            return processUpgradeRead(bytesRead);
        }

        if (closePending) {
            return new SocketClientInboundResult.Close();
        }

        if (bytesRead == -1) {
            return new SocketClientInboundResult.Close();
        }

        if (bytesRead == 0) {
            return new SocketClientInboundResult.Buffering();
        }

        readBuffer.flip();
        var result = feedWebSocket(readBuffer);
        readBuffer.compact();
        return result;
    }

    /**
     * Delivers inbound bytes to the pending upgrade read and notifies the
     * waiting thread when the read is satisfied.
     *
     * @param bytesRead the number of bytes placed into the pending read
     *                  buffer, or -1 for end-of-stream
     * @return the processing result
     */
    private SocketClientInboundResult processUpgradeRead(int bytesRead) {
        var read = pendingUpgradeRead;
        if (read == null) {
            return new SocketClientInboundResult.Buffering();
        }

        if (bytesRead == -1) {
            read.length = -1;
            synchronized (read.lock) {
                read.lock.notifyAll();
            }
            return new SocketClientInboundResult.Close();
        }

        if (bytesRead == 0) {
            return new SocketClientInboundResult.Buffering();
        }

        if (read.length == -1) {
            read.length = 0;
        }
        read.length += bytesRead;

        if (!read.fullRead || !read.buffer.hasRemaining()) {
            pendingUpgradeRead = null;
            synchronized (read.lock) {
                read.lock.notifyAll();
            }
        }

        return new SocketClientInboundResult.Continue();
    }

    /**
     * Accepts a pending upgrade read request.
     *
     * @param read the pending read request
     * @return {@code true} if the read was accepted
     */
    @Override
    public boolean setPendingRead(SocketClientPendingRead read) {
        if (pendingUpgradeRead != null) {
            return false;
        }
        pendingUpgradeRead = read;
        return true;
    }

    /**
     * Feeds source bytes into the WebSocket frame decoder and processes
     * decoded frames.
     *
     * <p>This method is also used by the TLS layer to feed decrypted
     * data that was unwrapped into the app-in buffer (slow path).
     *
     * @param source the buffer containing bytes to decode, in read mode
     * @return the processing result
     * @throws IOException if layer processing fails
     */
    @Override
    public SocketClientInboundResult feedFromSource(ByteBuffer source) throws IOException {
        return feedWebSocket(source);
    }

    /**
     * Drains {@code source} through the stateful WebSocket frame decoder,
     * dispatching decoded frames to the rest of the pipeline.
     *
     * <p>Data frames are delivered to the next layer via
     * {@link SocketClientLayerContext#feedFromSource(ByteBuffer)}.
     * Control frames are handled internally by
     * {@link #handleControl(byte, byte[], int)}; a {@code null} return
     * from that helper (PONG) is ignored and the loop continues with the
     * next frame.
     *
     * @implNote Adapts WA Web's {@code WebSocketTransport.$3}
     *     ({@code onmessage}) behaviour: WA Web calls
     *     {@code onData(new Uint8Array(event.data))} once per frame;
     *     here the decoder emits a chunked stream of
     *     {@link WebSocketDecodedFrame.Data} values, each delivered to
     *     the next layer in turn.
     * @param source the buffer containing bytes to decode, in read mode
     * @return the processing result to return to the selector
     * @throws IOException if layer processing fails
     */
    private SocketClientInboundResult feedWebSocket(ByteBuffer source) throws IOException {
        while (source.hasRemaining()) {
            var decodedFrame = frameDecoder.decode(source);
            switch (decodedFrame) {
                case WebSocketDecodedFrame.None _ -> {
                    return new SocketClientInboundResult.Continue();
                }
                case WebSocketDecodedFrame.Invalid _ -> {
                    return new SocketClientInboundResult.Close();
                }
                case WebSocketDecodedFrame.Data data -> {
                    var layerResult = nextLayer.feedFromSource(data.payload());
                    if (layerResult instanceof SocketClientInboundResult.Close) {
                        return layerResult;
                    }
                }
                case WebSocketDecodedFrame.Control control -> {
                    var controlResult = handleControl(control.opcode(), control.payload(), control.length());
                    if (controlResult != null) {
                        return controlResult;
                    }
                }
            }
        }

        return new SocketClientInboundResult.Continue();
    }

    /**
     * Handles an incoming control frame according to
     * <a href="https://datatracker.ietf.org/doc/html/rfc6455#section-5.5">RFC 6455 §5.5</a>.
     *
     * <ul>
     * <li>PING → a matching PONG is scheduled to be written to the channel.
     * <li>CLOSE → the closed flag is latched and a CLOSE echo is scheduled;
     *     the subsequent {@link #processInbound(int)} will close the
     *     connection.
     * <li>PONG → returns {@code null} so the caller continues with the
     *     next frame.
     * <li>Any other opcode → the connection is closed.
     * </ul>
     *
     * @implNote WA Web has no counterpart because PING/PONG/CLOSE are
     *     handled internally by the browser's native WebSocket before any
     *     observable event reaches JS.
     * @param opcode  the control opcode
     * @param payload the control payload bytes
     * @param length  the number of valid bytes in {@code payload}
     * @return a {@link SocketClientInboundResult} to return to the
     *         selector, or {@code null} to continue decoding
     */
    private SocketClientInboundResult handleControl(byte opcode, byte[] payload, int length) {
        return switch (opcode) {
            case WebSocketFrameConstants.OPCODE_PING -> {
                var pong = WebSocketFrameEncoder.encodeControlFrame(WebSocketFrameConstants.OPCODE_PONG, payload, length);
                yield new SocketClientInboundResult.NeedsWrite(pong.header(), pong.payload());
            }
            case WebSocketFrameConstants.OPCODE_CLOSE -> {
                closePending = true;
                var close = WebSocketFrameEncoder.encodeControlFrame(WebSocketFrameConstants.OPCODE_CLOSE, payload, length);
                yield new SocketClientInboundResult.NeedsWrite(close.header(), close.payload());
            }
            case WebSocketFrameConstants.OPCODE_PONG -> null; // ignored
            default -> new SocketClientInboundResult.Close();
        };
    }

    /**
     * Tears down per-connection state when the transport is disconnected.
     *
     * <p>If a pending upgrade read is still active, it is resolved with
     * an EOF sentinel so the blocking handshake thread can observe the
     * disconnection and throw.  The disconnect signal is then propagated
     * upwards along the inbound chain.
     *
     * @implNote Adapts WA Web's {@code WebSocketTransport.$4}
     *     ({@code onclose}) and {@code $6} helper: WA Web sets the
     *     closed flag and invokes the {@code onClose} consumer; Cobalt
     *     translates that into a fan-out across the layered-selector
     *     chain.
     */
    @Override
    public void onDisconnect() {
        var read = pendingUpgradeRead;
        if (read != null) {
            read.length = -1;
            pendingUpgradeRead = null;
            synchronized (read.lock) {
                read.lock.notifyAll();
            }
        }
        if(nextLayer != null) {
            nextLayer.onDisconnect();
        }
    }
}
