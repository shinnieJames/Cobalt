package com.github.auties00.cobalt.socket.security.whatsapp;

import com.github.auties00.cobalt.socket.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A layer context that handles WhatsApp datagram framing.
 *
 * <p>Inbound data follows a length-prefixed protocol: each message is
 * preceded by a 3-byte big-endian integer ({@code int24}) that encodes
 * the payload length.  This context reassembles complete datagrams from
 * the byte stream and delivers them to the {@link SocketClientLayerListener}
 * through a single-threaded virtual executor to preserve ordering.
 *
 * <p>This is the topmost layer context in the read pipeline.  It does not
 * delegate to any further layer above it.
 *
 * <p>Buffer management is designed for zero-copy efficiency:
 * <ul>
 * <li>The 3-byte {@link #datagramLengthBuffer} is heap-allocated once and
 *     reused across all datagrams for the lifetime of the connection.
 * <li>The {@link #datagramBuffer} is allocated per-message to the exact
 *     decoded length, then handed off to the listener and never reused.
 * <li>Lower layers (or the selector) read or unwrap directly into
 *     whichever buffer {@link #inboundTarget()} returns, avoiding
 *     intermediate copies.
 * </ul>
 */
public final class WhatsAppLayerContext implements SocketClientLayerContext {
    private static final int INT24_BYTE_SIZE = 3;
    private static final int MAX_MESSAGE_LENGTH = 1048576;

    /**
     * The listener that receives complete datagrams and close events.
     */
    private final SocketClientLayerListener listener;

    /**
     * Buffer for reading the 3-byte length prefix of the current inbound
     * datagram.  Heap-allocated, reused for every datagram.
     */
    private final ByteBuffer datagramLengthBuffer;

    /**
     * Lock for creating and destroying the listener executor.
     */
    private final Object executorLock;

    /**
     * Buffer for accumulating the payload of the current inbound datagram,
     * or {@code null} if the length prefix has not yet been fully read.
     *
     * <p>Allocated once the 3-byte length prefix is complete, sized to the
     * decoded length.  Set back to {@code null} after the completed datagram
     * is handed off to the listener.
     */
    private ByteBuffer datagramBuffer;

    /**
     * Single-threaded virtual executor for delivering datagrams to the
     * listener in order.
     */
    private volatile ExecutorService listenerExecutor;

    /**
     * Creates an application layer context for the given listener.
     *
     * @param listener the non-null listener to receive datagrams
     */
    public WhatsAppLayerContext(SocketClientLayerListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
        this.datagramLengthBuffer = ByteBuffer.allocate(INT24_BYTE_SIZE);
        this.executorLock = new Object();
    }

    /**
     * Returns the current inbound target buffer.
     *
     * <p>When the length prefix is still being read, returns the 3-byte
     * {@link #datagramLengthBuffer}.  When the length has been decoded
     * and a payload is being accumulated, returns the per-message
     * {@link #datagramBuffer}.
     *
     * @return the buffer to read or unwrap into, in write mode
     */
    @Override
    public ByteBuffer inboundTarget() {
        return datagramBuffer != null ? datagramBuffer : datagramLengthBuffer;
    }

    /**
     * Processes inbound bytes that were placed into the current
     * {@link #inboundTarget()}.
     *
     * <p>This method drives the datagram reassembly state machine:
     * <ol>
     * <li>If end-of-stream is reached, returns {@link SocketClientInboundResult.Close}.
     * <li>If the length buffer is not yet full, returns
     *     {@link SocketClientInboundResult.Buffering}.
     * <li>When the length buffer fills, decodes the 3-byte int24, validates
     *     it, and allocates the payload buffer.
     * <li>If the payload buffer is not yet full, returns
     *     {@link SocketClientInboundResult.Buffering}.
     * <li>When the payload is complete, delivers the datagram to the
     *     listener and resets state for the next message.
     * </ol>
     *
     * @param bytesRead the number of bytes placed into the target, or -1
     *                  for end-of-stream
     * @return the processing result
     */
    @Override
    public SocketClientInboundResult processInbound(int bytesRead) {
        if (bytesRead == -1) {
            return new SocketClientInboundResult.Close();
        }

        if (bytesRead == 0) {
            return new SocketClientInboundResult.Buffering();
        }

        if (datagramBuffer == null) {
            // Still reading the 3-byte length prefix
            if (datagramLengthBuffer.hasRemaining()) {
                return new SocketClientInboundResult.Buffering();
            }

            // Length prefix complete — decode and allocate payload buffer
            datagramLengthBuffer.flip();
            var length = ((datagramLengthBuffer.get() & 0xFF) << 16)
                    | ((datagramLengthBuffer.get() & 0xFF) << 8)
                    | (datagramLengthBuffer.get() & 0xFF);
            datagramLengthBuffer.clear();

            if (length <= 0 || length > MAX_MESSAGE_LENGTH) {
                return new SocketClientInboundResult.Close();
            }

            datagramBuffer = ByteBuffer.allocate(length);
            return new SocketClientInboundResult.Buffering();
        }

        // Still reading the payload
        if (datagramBuffer.hasRemaining()) {
            return new SocketClientInboundResult.Buffering();
        }

        // Payload complete — deliver
        datagramBuffer.flip();
        var completed = datagramBuffer;
        datagramBuffer = null;
        listenerExecutor.execute(() -> listener.onDatagram(completed));
        return new SocketClientInboundResult.Continue();
    }

    /**
     * Feeds source bytes into the datagram reassembly state machine.
     *
     * <p>This method is used by intermediate layers (such as TLS or
     * WebSocket) that decode data into their own buffers and then need
     * to push the decoded bytes into the application layer.  It performs
     * a bounded copy from the source into the current
     * {@link #inboundTarget()} and advances the state machine.
     *
     * @param source the buffer containing decoded bytes, in read mode
     * @return the result of processing
     */
    public SocketClientInboundResult feedFromSource(ByteBuffer source) {
        while (source.hasRemaining()) {
            var noDatagram = datagramBuffer == null;
            var target = noDatagram ? datagramLengthBuffer : datagramBuffer;
            var count = Math.min(source.remaining(), target.remaining());
            var savedLimit = source.limit();
            source.limit(source.position() + count);
            target.put(source);
            source.limit(savedLimit);

            if (!target.hasRemaining()) {
                if (noDatagram) {
                    // Length prefix complete
                    datagramLengthBuffer.flip();
                    var length = ((datagramLengthBuffer.get() & 0xFF) << 16)
                            | ((datagramLengthBuffer.get() & 0xFF) << 8)
                            | (datagramLengthBuffer.get() & 0xFF);
                    datagramLengthBuffer.clear();

                    if (length <= 0 || length > MAX_MESSAGE_LENGTH) {
                        return new SocketClientInboundResult.Close();
                    }

                    datagramBuffer = ByteBuffer.allocate(length);
                } else {
                    // Payload complete
                    datagramBuffer.flip();
                    var completed = datagramBuffer;
                    datagramBuffer = null;
                    listenerExecutor.execute(() -> listener.onDatagram(completed));
                }
            }
        }
        return new SocketClientInboundResult.Continue();
    }

    /**
     * Starts the virtual listener executor if one does not already exist.
     *
     * <p>Called when the connection transitions to the post-tunnel phase
     * and datagram delivery begins.
     */
    public void startListenerExecutor() {
        if (listenerExecutor == null || listenerExecutor.isShutdown()) {
            synchronized (executorLock) {
                if (listenerExecutor == null || listenerExecutor.isShutdown()) {
                    listenerExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
                }
            }
        }
    }

    @Override
    public void onDisconnect() {
        if (listenerExecutor != null && !listenerExecutor.isShutdown()) {
            synchronized (executorLock) {
                if (listenerExecutor != null && !listenerExecutor.isShutdown()) {
                    listenerExecutor.shutdownNow();
                    listenerExecutor = null;
                }
            }
        }

        try {
            listener.onClose();
        } catch (Throwable _) {
            // Swallow listener exceptions during disconnect
        }
    }
}
