package com.github.auties00.cobalt.socket.layer.application.websocket;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.WebSocketFrameConstants;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.decoder.WebSocketDecodedFrame;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.decoder.WebSocketFrameDecoder;
import com.github.auties00.cobalt.socket.layer.application.websocket.frame.encoder.WebSocketFrameEncoder;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A layer context that handles WebSocket frame decoding and encoding.
 */
public final class WebSocketLayerContext implements SocketClientLayerContext {
    private static final int READ_BUFFER_SIZE = 16384;

    /**
     * The next layer context in the chain (the layer above WebSocket).
     */
    private final SocketClientLayerContext nextLayer;

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
     * Creates a WebSocket layer context.
     *
     * @param nextLayer the layer above in the read pipeline
     */
    public WebSocketLayerContext(SocketClientLayerContext nextLayer) {
        this.nextLayer = nextLayer;
        this.readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
        this.frameDecoder = new WebSocketFrameDecoder();
    }

    /**
     * Returns the read buffer for incoming bytes.
     *
     * <p>The selector or TLS layer reads/unwraps data into this buffer,
     * which is then decoded as WebSocket frames.
     *
     * @return the read buffer in write mode
     */
    @Override
    public ByteBuffer inboundTarget() {
        return readBuffer;
    }

    /**
     * Decodes WebSocket frames from the read buffer and feeds data
     * payloads into the next layer.
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
    public SocketClientInboundResult feedFromSource(ByteBuffer source) throws IOException {
        return feedWebSocket(source);
    }

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

    @Override
    public void onDisconnect() {
        nextLayer.onDisconnect();
    }
}
