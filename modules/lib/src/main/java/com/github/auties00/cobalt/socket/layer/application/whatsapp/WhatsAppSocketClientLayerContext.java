package com.github.auties00.cobalt.socket.layer.application.whatsapp;

import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientPendingRead;

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
 *
 * @implNote ADAPTED: WAFrameSocket.FrameSocket — WA Web uses a {@code Binary}
 *     accumulation buffer with a peek/read loop to extract frames, while
 *     Cobalt uses a two-phase state machine (length buffer then payload
 *     buffer) integrated into the NIO selector-driven layer context chain.
 *     The pending-close mechanism ({@code $4} in WA Web) is unnecessary in
 *     Cobalt because frames are processed one at a time from the selector
 *     thread rather than buffered in a binary accumulator.
 */
public final class WhatsAppSocketClientLayerContext implements SocketClientApplicationLayerContext {
    /**
     * The size in bytes of the int24 length prefix (3 bytes).
     *
     * @implNote WAFrameSocket.FrameSocket.sendFrame — the int24 prefix
     *     is written as {@code writeUint8(n >> 16)} followed by
     *     {@code writeUint16(n & 65535)}, totalling 3 bytes.
     */
    private static final int INT24_BYTE_SIZE = 3;

    /**
     * The maximum valid frame length, equal to the largest unsigned int24
     * value ({@code 0xFFFFFF = 16777215}).
     *
     * @implNote WAFrameSocket.FrameSocket.$8 — WA Web validates outbound
     *     frame size with {@code n >= 1 << 24}, meaning values 0 through
     *     16777215 are valid.  WA Web does not validate inbound frame
     *     lengths at all; this constant provides a defensive upper bound
     *     matching the protocol's int24 capacity.
     */
    private static final int MAX_MESSAGE_LENGTH = 0xFFFFFF;

    /**
     * The listener that receives complete datagrams and close events.
     *
     * @implNote ADAPTED: WAFrameSocket.FrameSocket.onFrame — WA Web uses
     *     separate {@code onFrame} and {@code onClose} callback fields;
     *     Cobalt combines them into a single {@link SocketClientLayerListener}.
     */
    private final SocketClientLayerListener listener;

    /**
     * Buffer for reading the 3-byte length prefix of the current inbound
     * datagram.  Heap-allocated, reused for every datagram.
     *
     * @implNote ADAPTED: WAFrameSocket.FrameSocket.$3 — WA Web uses a
     *     single {@code Binary} accumulation buffer; Cobalt splits the
     *     read into a dedicated 3-byte length prefix buffer.
     */
    private final ByteBuffer datagramLengthBuffer;

    /**
     * Lock for creating and destroying the listener executor.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific synchronization for the
     *     listener executor lifecycle.
     */
    private final Object executorLock;

    /**
     * Whether this context is in handshake mode.
     *
     * <p>In handshake mode, inbound data is delivered to a blocking
     * {@link SocketClientPendingRead} instead of
     * being reassembled as int24-framed datagrams.  This allows the
     * Noise handshake to perform synchronous reads while data flows
     * asynchronously through the layer context chain.
     *
     * <p>Transitions from {@code true} to {@code false} exactly once
     * after the Noise handshake completes.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific dual-mode flag for bridging
     *     async NIO with blocking handshake reads.
     */
    private volatile boolean handshakeMode;

    /**
     * The pending blocking read request during handshake mode.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific mechanism for blocking
     *     handshake reads.
     */
    private volatile SocketClientPendingRead pendingHandshakeRead;

    /**
     * Buffer for data arriving during handshake mode before a pending
     * read is set.  This prevents data loss when the selector delivers
     * the server response before the handshake thread calls
     * {@link #setPendingRead(SocketClientPendingRead)}.
     *
     * <p>Accessed by the selector thread (write) and the handshake
     * virtual thread (drain in {@code setPendingRead}), so all access
     * is guarded by {@link #handshakeLock}.
     */
    private ByteBuffer handshakeBuffer;

    /**
     * Lock guarding {@link #handshakeBuffer} and {@link #pendingHandshakeRead}
     * to prevent data races between the selector thread and the
     * handshake virtual thread.
     */
    private final Object handshakeLock = new Object();

    /**
     * Buffer for accumulating the payload of the current inbound datagram,
     * or {@code null} if the length prefix has not yet been fully read.
     *
     * <p>Allocated once the 3-byte length prefix is complete, sized to the
     * decoded length.  Set back to {@code null} after the completed datagram
     * is handed off to the listener.
     *
     * @implNote ADAPTED: WAFrameSocket.FrameSocket.$3 — WA Web's single
     *     {@code Binary} accumulation buffer is split into a separate
     *     per-message payload buffer in Cobalt.
     */
    private ByteBuffer datagramBuffer;

    /**
     * Single-threaded virtual executor for delivering datagrams to the
     * listener in order.
     *
     * @implNote NO_WA_BASIS — Cobalt uses a virtual-thread executor to
     *     serialize datagram delivery off the selector thread.
     */
    private volatile ExecutorService listenerExecutor;

    /**
     * Creates an application layer context for the given listener.
     *
     * @implNote ADAPTED: WAFrameSocket.FrameSocket constructor — WA Web accepts
     *     a raw socket and optional initial data; Cobalt receives a
     *     {@link SocketClientLayerListener} via constructor DI and integrates
     *     into the NIO layer context chain instead of wiring raw socket
     *     callbacks.
     * @param listener the non-null listener to receive datagrams
     */
    private WhatsAppSocketClientLayerContext(SocketClientLayerListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
        this.datagramLengthBuffer = ByteBuffer.allocate(INT24_BYTE_SIZE);
        this.executorLock = new Object();
        this.handshakeMode = true;
    }

    /**
     * Creates a new application layer context for the given listener.
     *
     * @implNote ADAPTED: WAFrameSocket.FrameSocket constructor — WA Web accepts
     *     a raw socket and optional initial data; Cobalt creates via this
     *     factory method and integrates into the NIO layer context chain.
     * @param listener the non-null listener to receive datagrams and close events
     * @return a new {@code WhatsAppSocketClientLayerContext}
     */
    public static WhatsAppSocketClientLayerContext newAppContext(SocketClientLayerListener listener) {
        return new WhatsAppSocketClientLayerContext(listener);
    }

    /**
     * Returns the current inbound target buffer.
     *
     * <p>When the length prefix is still being read, returns the 3-byte
     * {@link #datagramLengthBuffer}.  When the length has been decoded
     * and a payload is being accumulated, returns the per-message
     * {@link #datagramBuffer}.
     *
     * @implNote ADAPTED: WAFrameSocket.FrameSocket.$5 — WA Web accumulates
     *     all inbound bytes into a single {@code Binary} buffer; Cobalt
     *     instead exposes a two-phase target (length prefix or payload) so
     *     the NIO selector can read directly into the correct buffer.
     * @return the buffer to read or unwrap into, in write mode
     */
    @Override
    public ByteBuffer inboundTarget() {
        if (handshakeMode) {
            var read = pendingHandshakeRead;
            return read != null ? read.buffer : EMPTY_BUFFER;
        }
        return datagramBuffer != null ? datagramBuffer : datagramLengthBuffer;
    }

    /**
     * An empty buffer returned when no handshake read is pending.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific sentinel for the handshake
     *     mode inbound target.
     */
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

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
     * @implNote ADAPTED: WAFrameSocket.FrameSocket.convertBufferedToFrames — WA Web
     *     loops over the accumulation buffer extracting complete frames via
     *     {@code peek(p)} and {@code _(binary)}; Cobalt processes one frame
     *     at a time through the NIO selector's read cycle, returning
     *     {@link SocketClientInboundResult} to control the event loop.
     * @param bytesRead the number of bytes placed into the target, or -1
     *                  for end-of-stream
     * @return the processing result
     */
    @Override
    public SocketClientInboundResult processInbound(int bytesRead) {
        if (handshakeMode) {
            return processHandshakeRead(bytesRead);
        }

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

            if (length < 0 || length > MAX_MESSAGE_LENGTH) {
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
     * @implNote ADAPTED: WAFrameSocket.FrameSocket.$5 — WA Web's
     *     {@code onData} handler writes bytes into the {@code Binary}
     *     accumulation buffer and calls {@code convertBufferedToFrames()};
     *     Cobalt pushes decoded bytes from upstream layers through the
     *     state machine directly, avoiding an intermediate accumulation
     *     buffer.
     * @param source the buffer containing decoded bytes, in read mode
     * @return the result of processing
     */
    public SocketClientInboundResult feedFromSource(ByteBuffer source) {
        if (handshakeMode) {
            return feedHandshakeRead(source);
        }

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

                    if (length < 0 || length > MAX_MESSAGE_LENGTH) {
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
     * Processes an inbound read completion during handshake mode.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific mechanism for bridging the
     *     blocking Noise handshake reads with the async NIO selector.
     *     WA Web's handshake is fully async and does not need this.
     * @param bytesRead the number of bytes read, or -1 for end-of-stream
     * @return the processing result
     */
    private SocketClientInboundResult processHandshakeRead(int bytesRead) {
        var read = pendingHandshakeRead;
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
            pendingHandshakeRead = null;
            synchronized (read.lock) {
                read.lock.notifyAll();
            }
        }

        return new SocketClientInboundResult.Continue();
    }

    /**
     * Feeds decoded bytes from an upstream layer into a pending handshake
     * read.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific mechanism for bridging the
     *     blocking Noise handshake reads with decoded bytes from upstream
     *     layers (e.g. TLS unwrap).  WA Web's handshake is fully async.
     * @param source the buffer containing decoded bytes, in read mode
     * @return the processing result
     */
    private SocketClientInboundResult feedHandshakeRead(ByteBuffer source) {
        synchronized (handshakeLock) {
            var read = pendingHandshakeRead;
            if (read != null) {
                var count = Math.min(source.remaining(), read.buffer.remaining());
                var savedLimit = source.limit();
                source.limit(source.position() + count);
                read.buffer.put(source);
                source.limit(savedLimit);

                if (read.length == -1) {
                    read.length = 0;
                }
                read.length += count;

                if (!read.fullRead || !read.buffer.hasRemaining()) {
                    pendingHandshakeRead = null;
                    synchronized (read.lock) {
                        read.lock.notifyAll();
                    }
                }
            }

            // Buffer any unconsumed bytes for the next read
            if (source.hasRemaining()) {
                if (handshakeBuffer == null) {
                    handshakeBuffer = ByteBuffer.allocate(source.remaining());
                } else if (handshakeBuffer.remaining() < source.remaining()) {
                    var newBuf = ByteBuffer.allocate(handshakeBuffer.position() + source.remaining());
                    handshakeBuffer.flip();
                    newBuf.put(handshakeBuffer);
                    handshakeBuffer = newBuf;
                }
                handshakeBuffer.put(source);
            }

            return new SocketClientInboundResult.Continue();
        }
    }

    /**
     * Sets the pending handshake read request.
     *
     * <p>Called by the handshake thread to post a read request that the
     * selector will fulfill through the layer context chain.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific mechanism for blocking
     *     handshake reads through the async NIO layer context chain.
     * @param read the pending read request
     * @return {@code true} if the read was posted, {@code false} if
     *         another read is already pending
     */
    public boolean setPendingRead(SocketClientPendingRead read) {
        synchronized (handshakeLock) {
            if (pendingHandshakeRead != null) {
                return false;
            }
            pendingHandshakeRead = read;

            // Drain any data that arrived before this read was posted
            if (handshakeBuffer != null && handshakeBuffer.position() > 0) {
                handshakeBuffer.flip();
                drainHandshakeBuffer();
                if (handshakeBuffer != null && !handshakeBuffer.hasRemaining()) {
                    handshakeBuffer = null;
                }
            }
            return true;
        }
    }

    /**
     * Drains the handshake buffer into the current pending read.
     * Must be called under {@link #handshakeLock}.
     */
    private void drainHandshakeBuffer() {
        var read = pendingHandshakeRead;
        if (read == null || handshakeBuffer == null || !handshakeBuffer.hasRemaining()) {
            return;
        }

        var count = Math.min(handshakeBuffer.remaining(), read.buffer.remaining());
        var savedLimit = handshakeBuffer.limit();
        handshakeBuffer.limit(handshakeBuffer.position() + count);
        read.buffer.put(handshakeBuffer);
        handshakeBuffer.limit(savedLimit);

        if (read.length == -1) {
            read.length = 0;
        }
        read.length += count;

        if (!read.fullRead || !read.buffer.hasRemaining()) {
            pendingHandshakeRead = null;
            synchronized (read.lock) {
                read.lock.notifyAll();
            }
        }

        if (handshakeBuffer.hasRemaining()) {
            handshakeBuffer.compact();
        } else {
            handshakeBuffer = null;
        }
    }

    /**
     * Transitions this context from handshake mode to datagram
     * reassembly mode.
     *
     * <p>After this call, inbound data is processed as int24-framed
     * datagrams and delivered to the listener.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific handshake/datagram mode
     *     transition.  WA Web's FrameSocket does not have a handshake
     *     mode; the Noise handshake is handled at a different layer.
     */
    public void markHandshakeComplete() {
        this.handshakeMode = false;
    }

    /**
     * Starts the virtual listener executor if one does not already exist.
     *
     * <p>Called when the connection transitions to the post-tunnel phase
     * and datagram delivery begins.
     *
     * @implNote NO_WA_BASIS — Cobalt uses a single-threaded virtual executor
     *     to serialize datagram delivery; WA Web delivers frames
     *     synchronously from the socket's data callback.
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

    /**
     * Handles connection teardown by releasing resources and notifying
     * the listener.
     *
     * <p>Completes any pending handshake read with end-of-stream, shuts
     * down the listener executor, and invokes the listener's
     * {@link SocketClientLayerListener#onClose()} callback.
     *
     * @implNote ADAPTED: WAFrameSocket.FrameSocket.$9 — WA Web's close
     *     handler sets {@code closed = true} and invokes the
     *     {@code onClose} callback.  Cobalt additionally releases the
     *     handshake read and listener executor resources.
     */
    @Override
    public void onDisconnect() {
        var read = pendingHandshakeRead;
        if (read != null) {
            read.length = -1;
            pendingHandshakeRead = null;
            synchronized (read.lock) {
                read.lock.notifyAll();
            }
        }

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
