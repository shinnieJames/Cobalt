package com.github.auties00.cobalt.socket.threading;

import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayerContext;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * A singleton NIO event loop that multiplexes all socket connections on
 * a single virtual thread.
 *
 * <p>This selector is a thin orchestrator that reads bytes from the
 * channel into the bottommost layer context's
 * {@link SocketClientLayerContext#inboundTarget() inboundTarget()}, calls
 * {@link SocketClientLayerContext#processInbound(int) processInbound()},
 * and handles the resulting {@link SocketClientInboundResult}.  All protocol-specific
 * logic (TLS, WebSocket, datagram framing, proxy handshake) lives in
 * the layer contexts, not here.
 *
 * <p>The outbound write path drains the transport context's
 * {@link SocketClientPendingWrites} queue.  If a
 * TLS layer context is present, buffers are wrapped before writing;
 * otherwise they are written directly via
 * {@link java.nio.channels.GatheringByteChannel}.
 */
public final class SocketClientSelector implements Runnable {
    /**
     * The singleton instance.
     */
    public static final SocketClientSelector INSTANCE;

    static {
        try {
            INSTANCE = new SocketClientSelector();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static final long SAFETY_TIMEOUT_MS = 5000;

    private final System.Logger logger;
    private final Selector selector;
    private final AtomicBoolean wakeupPending;
    private volatile Thread selectorThread;

    private SocketClientSelector() throws IOException {
        this.logger = System.getLogger(SocketClientSelector.class.getName());
        this.selector = Selector.open();
        this.wakeupPending = new AtomicBoolean();
    }

    private void wakeup() {
        if (wakeupPending.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }

    /**
     * Registers a channel with this selector, attaching the given head
     * layer context (typically the transport context) as the first entry
     * in the attachment's chain.
     *
     * <p>Starts the selector virtual thread if it is not already running.
     *
     * @param channel the non-blocking socket channel
     * @param head    the head layer context for this connection's chain
     * @throws IOException if registration fails
     */
    public synchronized void register(SocketChannel channel, SocketClientLayerContext head) throws IOException {
        selector.wakeup();
        channel.register(selector, SelectionKey.OP_CONNECT, AttachmentData.newConnectionContext(head));
        if (selectorThread == null || !selectorThread.isAlive()) {
            selectorThread = Thread.startVirtualThread(this);
        }
    }

    /**
     * Blocks the current thread until the given channel is connected
     * (that is, the selector thread has processed the {@code OP_CONNECT}
     * event) or until the timeout elapses.
     *
     * <p>On success, marks the attachment as connected and returns.  On
     * failure or timeout, throws an {@link IOException}.
     *
     * @param channel   the channel
     * @param timeoutMs the maximum time to wait, in milliseconds
     * @throws IOException if the channel never connects or the wait is
     *                     interrupted
     */
    public void awaitConnect(SocketChannel channel, long timeoutMs) throws IOException {
        var key = channel.keyFor(selector);
        if (key == null) {
            throw new IOException("Channel not registered");
        }
        var ctx = (AttachmentData) key.attachment();
        var lock = ctx.connectionLock();
        synchronized (lock) {
            var deadline = System.currentTimeMillis() + timeoutMs;
            while (!channel.isConnected() && channel.isOpen()) {
                var remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Connect interrupted", e);
                }
            }
        }
        if (!channel.isConnected()) {
            throw new IOException("Connection failed");
        }
        ctx.setConnected(true);
    }

    /**
     * Registers a layer context for the given channel.
     *
     * @param channel      the channel
     * @param layerContext the context to register
     * @return {@code true} if registered, {@code false} if the channel
     *         is not registered with this selector
     */
    public boolean registerLayerContext(SocketChannel channel, SocketClientLayerContext layerContext) {
        var selKey = channel.keyFor(selector);
        if (selKey == null) {
            return false;
        }
        ((AttachmentData) selKey.attachment()).addLayerContext(layerContext);
        return true;
    }

    /**
     * Unregisters a channel, cancels its key, closes the channel, and
     * notifies all layer contexts.
     *
     * @param channel the channel to unregister
     */
    public void unregister(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null) {
            return;
        }

        key.cancel();

        var ctx = (AttachmentData) key.attachment();

        // Notify connection lock
        synchronized (ctx.connectionLock()) {
            ctx.connectionLock().notifyAll();
        }

        // Use connected CAS to guard single notification
        if (!ctx.compareAndSetConnected(true, false)) {
            try {
                channel.close();
            } catch (IOException _) {
            }
            wakeup();
            return;
        }

        // Notify all layer contexts before closing the channel so that
        // the TLS layer can send its close_notify alert (RFC 8446 §6.1)
        ctx.head().onDisconnect();

        try {
            channel.close();
        } catch (IOException _) {
        }

        wakeup();
    }

    /**
     * Returns whether the given channel is connected.
     *
     * @param channel the channel to check
     * @return {@code true} if connected
     */
    public boolean isConnected(SocketChannel channel) {
        if (channel == null) {
            return false;
        }

        var key = channel.keyFor(selector);
        if (key == null) {
            return false;
        }

        return ((AttachmentData) key.attachment()).isConnected();
    }

    /**
     * Posts a blocking read request during a synchronous handshake phase.
     *
     * <p>Walks the layer chain from tail to head and delivers the read to
     * the first context that accepts it.  This lets each handshake phase
     * route the read to whichever layer is currently topmost — the tunnel
     * during a proxy handshake, the WebSocket layer during the HTTP
     * upgrade, the WhatsApp layer during the Noise handshake.
     *
     * @param channel the channel
     * @param read    the pending read request
     * @return {@code true} if some layer accepted the read
     */
    public boolean addRead(SocketChannel channel, SocketClientPendingRead read) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        var ctx = (AttachmentData) key.attachment();
        if (!ctx.offerPendingRead(read)) {
            return false;
        }

        try {
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        } catch (CancelledKeyException _) {
            return false;
        }
        wakeup();
        return true;
    }

    /**
     * Enqueues outbound buffers for writing.
     *
     * @param channel the channel
     * @param buffers the buffers to write
     * @return {@code true} if the write was enqueued
     */
    public boolean addWrite(SocketChannel channel, ByteBuffer... buffers) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        if (buffers == null || buffers.length == 0) {
            return true;
        }

        var ctx = (AttachmentData) key.attachment();
        var pendingWrites = ctx.pendingWrites();
        var hasWrites = false;
        for (var buffer : buffers) {
            if (buffer != null && buffer.hasRemaining()) {
                if (!pendingWrites.offer(buffer)) {
                    return false;
                }
                hasWrites = true;
            }
        }
        if (!hasWrites) {
            return true;
        }

        try {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        } catch (CancelledKeyException _) {
            return false;
        }
        wakeup();
        return true;
    }

    /**
     * Marks the connection as ready and transitions it to asynchronous
     * data flow.
     *
     * <p>Marks the tunnel context as tunnelled and enables read interest.
     *
     * @param channel the channel
     * @return {@code true} if successfully marked
     */
    public boolean finishConnect(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        var ctx = (AttachmentData) key.attachment();

        // Mark tunnel as established
        ctx.tunnelContext().ifPresent(SocketClientTunnelLayerContext::markTunnelled);

        try {
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        } catch (CancelledKeyException _) {
            return false;
        }
        wakeup();
        return true;
    }

    /**
     * Marks the connection as ready, feeds leftover bytes into the
     * pipeline, and drains any buffered TLS application data.
     *
     * @param channel  the channel
     * @param leftover leftover bytes from a synchronous parser, or {@code null}
     * @return {@code true} if finalization succeeded
     */
    public boolean finishConnect(SocketChannel channel, ByteBuffer leftover) {
        if (!finishConnect(channel)) {
            return false;
        }
        if (leftover != null && leftover.hasRemaining() && !preSeedDatagram(channel, leftover)) {
            return false;
        }
        return drainAppBuffer(channel);
    }

    /**
     * Initiates a security handshake and blocks the calling virtual thread
     * until it completes.
     *
     * @param channel         the channel
     * @param securityContext  the security layer context to handshake
     * @param timeout         the handshake timeout in milliseconds
     * @throws IOException if the handshake fails or times out
     */
    public void startHandshake(SocketChannel channel, SocketClientLayerContext securityContext, long timeout) throws IOException {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            throw new IOException("Channel not registered");
        }

        var ctx = (AttachmentData) key.attachment();

        securityContext.beginHandshake();

        try {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } catch (CancelledKeyException e) {
            throw new IOException("Key cancelled during handshake init");
        }
        wakeup();

        var lock = securityContext.handshakeLock();
        if (lock == null) {
            throw new IOException("Security context does not support handshaking");
        }

        synchronized (lock) {
            var deadline = System.currentTimeMillis() + timeout;
            while (!securityContext.isHandshakeComplete() && ctx.isConnected()) {
                var remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("Handshake timed out");
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Handshake interrupted", e);
                }
            }
        }

        if (!securityContext.isHandshakeComplete()) {
            throw new IOException("Handshake failed: connection lost");
        }
    }

    /**
     * Drains any leftover buffered data from all layer contexts into
     * the layer pipeline.
     *
     * @param channel the channel
     * @return {@code true} if draining succeeded or no layers needed
     *         draining, {@code false} if draining failed
     */
    public boolean drainAppBuffer(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null) {
            return false;
        }

        var ctx = (AttachmentData) key.attachment();
        try {
            return ctx.drainAllLayers();
        } catch (IOException _) {
            return false;
        }
    }

    /**
     * Feeds leftover bytes from a synchronous protocol upgrade into the
     * datagram pipeline.
     *
     * <p>Leftover bytes are produced by a synchronous parser that read
     * slightly past its own message boundary (for example, the WebSocket
     * HTTP upgrade parser consuming bytes past the empty-header terminator).
     * Those bytes are already post-crypto plaintext and belong at the
     * first application-layer context's buffer — feeding them any lower in
     * the chain would re-decrypt or re-read them.
     *
     * @param channel  the channel
     * @param leftover the leftover bytes in read mode
     * @return {@code true} if processing succeeded
     */
    public boolean preSeedDatagram(SocketChannel channel, ByteBuffer leftover) {
        if (!leftover.hasRemaining()) {
            return true;
        }

        var key = channel.keyFor(selector);
        if (key == null) {
            return false;
        }

        var ctx = (AttachmentData) key.attachment();
        var target = ctx.firstApplicationContext();
        if (target == null) {
            return false;
        }

        try {
            return !(target.feedFromSource(leftover) instanceof SocketClientInboundResult.Close);
        } catch (IOException _) {
            return false;
        }
    }

    @Override
    public void run() {
        try {
            while (selector.isOpen()) {
                var readyChannels = selector.select(SAFETY_TIMEOUT_MS);
                wakeupPending.set(false);
                if (readyChannels > 0) {
                    var iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        var key = iterator.next();
                        iterator.remove();
                        handleKey(key);
                    }
                }
                if (selector.keys().isEmpty()) {
                    synchronized (this) {
                        if (selector.keys().isEmpty()) {
                            selectorThread = null;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            logger.log(Level.ERROR, throwable);

            for (var key : selector.keys()) {
                if (key.channel() instanceof SocketChannel socketChannel) {
                    unregister(socketChannel);
                }
            }

            synchronized (this) {
                selectorThread = null;
            }
        }
    }

    @SuppressWarnings("MagicConstant")
    private void handleKey(SelectionKey key) {
        var attachment = key.attachment();
        if (!(attachment instanceof AttachmentData ctx)) {
            return;
        }

        var channel = (SocketChannel) key.channel();

        try {
            if (key.isConnectable()) {
                if (channel.finishConnect()) {
                    key.interestOps(SelectionKey.OP_READ);
                    synchronized (ctx.connectionLock()) {
                        ctx.connectionLock().notifyAll();
                    }
                }
            }

            // Check for any layer currently handshaking
            var handshakingCtx = ctx.findHandshakingContext();
            if (handshakingCtx.isPresent()) {
                var hCtx = handshakingCtx.get();
                if (hCtx.isTasksPending()) {
                    return;
                }
                // Flow any readable bytes through the chain first so the
                // handshaking layer's netInBuffer is populated through the
                // outer layers' unwraps (matters for nested TLS).
                if (key.isReadable() && !processRead(channel, ctx)) {
                    unregister(channel);
                    return;
                }
                if (!processHandshake(channel, ctx, key, hCtx)) {
                    unregister(channel);
                }
                return;
            }

            if (key.isReadable()) {
                if (!processRead(channel, ctx)) {
                    unregister(channel);
                    return;
                }
            }
            if (key.isWritable()) {
                processWrite(channel, ctx);
                var hasPendingWrites = !ctx.pendingWrites().isEmpty() || ctx.hasPendingOutput();
                key.interestOps(updateWriteInterestOps(key.interestOps(), hasPendingWrites));
            }
        } catch (Exception _) {
            unregister(channel);
        }
    }

    private boolean processHandshake(SocketChannel channel, AttachmentData ctx, SelectionKey key, SocketClientLayerContext layerCtx) throws IOException {
        var result = layerCtx.driveHandshake(channel);
        return handleInboundResult(ctx, key, result);
    }

    private boolean processRead(SocketChannel channel, AttachmentData ctx) throws IOException {
        var bottom = ctx.head();
        var target = bottom.inboundTarget();
        var bytesRead = channel.read(target);
        var result = bottom.processInbound(bytesRead);

        var key = channel.keyFor(selector);
        return key != null && handleInboundResult(ctx, key, result);
    }

    private boolean handleInboundResult(AttachmentData ctx, SelectionKey key, SocketClientInboundResult result) {
        return switch (result) {
            case SocketClientInboundResult.Continue _, SocketClientInboundResult.Buffering _ -> true;
            case SocketClientInboundResult.Close _ -> false;
            case SocketClientInboundResult.NeedsWrite needsWrite -> {
                for (var buf : needsWrite.data()) {
                    if (buf != null && buf.hasRemaining()) {
                        ctx.pendingWrites().offer(buf);
                    }
                }
                try {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                } catch (CancelledKeyException _) {
                    yield false;
                }
                wakeup();
                yield true;
            }
            case SocketClientInboundResult.Suspended _ -> {
                try {
                    key.interestOps(0);
                } catch (CancelledKeyException _) {
                    yield true;
                }
                ctx.findTasksPendingContext().ifPresent(layer -> layer.runDelegatedTasks(() -> {
                    try {
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    } catch (CancelledKeyException _) {
                        return;
                    }
                    wakeup();
                }));
                yield true;
            }
        };
    }

    private boolean processWrite(SocketChannel channel, AttachmentData ctx) throws IOException {
        var tail = ctx.tail();
        while (ctx.isConnected()) {
            var claim = ctx.pendingWrites().claim();
            if (claim.isEmpty()) {
                return true;
            }

            var success = tail.processOutbound(channel, claim.array(), claim.offset(), claim.count());

            var consumed = countConsumed(claim);
            ctx.pendingWrites().release(consumed);
            if (!success) {
                return false;
            }
        }
        return false;
    }

    private static int countConsumed(SocketClientPendingWrites.Claim claim) {
        var consumed = 0;
        for (var i = claim.offset(); i < claim.offset() + claim.count(); i++) {
            if (claim.array()[i].hasRemaining()) {
                break;
            }
            consumed++;
        }
        return consumed;
    }

    static int updateWriteInterestOps(int currentOps, boolean hasPendingWrites) {
        return hasPendingWrites
                ? (currentOps | SelectionKey.OP_WRITE)
                : (currentOps & ~SelectionKey.OP_WRITE);
    }

    /**
     * Per-connection context attached to a {@link SelectionKey}.
     *
     * <p>Layer contexts are stored as a singly-linked list that is built
     * in the order the factories register them (bottom-to-top).  Each new
     * registration appends to the tail and wires the previous tail's
     * {@code nextLayer} forward — no rebuilding, no type-based ordering,
     * no field-per-position switch.
     *
     * <p>The chain order is therefore exactly the composition order used
     * by the factory in {@code WhatsAppSocketClient.newCipheredSocketClient},
     * so the selector can never disagree with the factory about layer
     * positions.
     *
     * <p>The attachment also owns the per-connection state that is not
     * really "the transport's" — the outbound write queue, the connection
     * lock, and the {@code connected} flag.  The transport layer context
     * only owns the read buffer and its chain link.
     */
    private static final class AttachmentData {
        private static final int WRITES_CHUNK_CAPACITY = 64;
        private static final VarHandle CONNECTED;

        static {
            try {
                CONNECTED = MethodHandles.lookup().findVarHandle(AttachmentData.class, "connected", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        /**
         * The layers in registration order, first entry is the head
         * (the transport context).  Short — typical depth is 3–5 entries.
         */
        private final List<SocketClientLayerContext> layers;

        /**
         * Monitor used to block the connecting thread until the selector
         * completes the non-blocking connect operation.
         */
        private final Object connectionLock;

        /**
         * Lock-free MPSC queue of outbound buffers waiting to be written
         * to the channel.  Owned by the connection, drained by the
         * selector thread, produced into by caller-thread
         * {@code sendBinary} calls.
         */
        private final SocketClientPendingWrites pendingWrites;

        /**
         * Whether the underlying channel is connected and registered with
         * the selector.  Accessed via the {@link #CONNECTED} VarHandle for
         * atomic transitions.
         */
        @SuppressWarnings("unused")
        private volatile boolean connected;

        private AttachmentData(SocketClientLayerContext head) {
            Objects.requireNonNull(head);
            this.layers = new ArrayList<>(5);
            this.layers.add(head);
            this.connectionLock = new Object();
            this.pendingWrites = new SocketClientPendingWrites(WRITES_CHUNK_CAPACITY);
        }

        /**
         * Creates a new connection attachment whose chain begins at the
         * given head context.
         *
         * @param head the head of the layer chain (typically the transport context)
         * @return a new {@code AttachmentData}
         */
        static AttachmentData newConnectionContext(SocketClientLayerContext head) {
            return new AttachmentData(head);
        }

        /**
         * Returns the head of the layer chain — always the transport
         * context, the one the selector reads bytes into.
         *
         * @return the head context, never {@code null}
         */
        SocketClientLayerContext head() {
            return layers.getFirst();
        }

        /**
         * Returns the tail of the layer chain — the topmost layer, where
         * outbound processing begins.
         *
         * @return the tail context, never {@code null}
         */
        SocketClientLayerContext tail() {
            return layers.getLast();
        }

        /**
         * Returns the outbound write queue for this connection.
         *
         * @return the pending-writes queue, never {@code null}
         */
        SocketClientPendingWrites pendingWrites() {
            return pendingWrites;
        }

        /**
         * Returns the monitor used to notify threads waiting for the
         * non-blocking connect to complete.
         *
         * @return the connection lock, never {@code null}
         */
        Object connectionLock() {
            return connectionLock;
        }

        /**
         * Returns whether the underlying channel is currently connected.
         *
         * @return {@code true} if connected
         */
        boolean isConnected() {
            return (boolean) CONNECTED.getVolatile(this);
        }

        /**
         * Sets the connected flag.
         *
         * @param value {@code true} to mark as connected, {@code false}
         *              to mark as disconnected
         */
        void setConnected(boolean value) {
            CONNECTED.setVolatile(this, value);
        }

        /**
         * Atomically transitions the connected flag from {@code expected}
         * to {@code newValue}, returning {@code true} on success.
         *
         * @param expected the expected current value
         * @param newValue the new value
         * @return {@code true} if the compare-and-set succeeded
         */
        boolean compareAndSetConnected(boolean expected, boolean newValue) {
            return CONNECTED.compareAndSet(this, expected, newValue);
        }

        /**
         * Returns the first application layer context in the chain.
         *
         * <p>Used as the feed point for leftover bytes from a synchronous
         * protocol upgrade (for example the WebSocket HTTP upgrade parser).
         * By the time the upgrade completes, all crypto layers have been
         * unwrapping bytes, so leftover bytes are plaintext that belongs at
         * the first application-layer buffer.
         *
         * @return the first application-layer context, or {@code null}
         *         if no application layer is registered
         */
        SocketClientApplicationLayerContext firstApplicationContext() {
            for (var layer : layers) {
                if (layer instanceof SocketClientApplicationLayerContext app) {
                    return app;
                }
            }
            return null;
        }

        /**
         * Appends a layer context to the tail of the chain and wires both
         * directions of the link: the previous tail's {@code nextLayer}
         * points forward to the new context, and the new context's
         * {@code prevLayer} points back to the previous tail.
         *
         * <p>The forward link drives the inbound walk (head to tail); the
         * backward link drives the outbound walk (tail to head).
         *
         * @param layerContext the context to register
         */
        void addLayerContext(SocketClientLayerContext layerContext) {
            var previous = layers.getLast();
            layers.add(layerContext);
            previous.setNextLayer(layerContext);
            layerContext.setPrevLayer(previous);
        }

        /**
         * Returns the tunnel layer context, if any appears in the chain.
         *
         * @return an optional containing the tunnel context, or empty if
         *         no tunnel layer is registered
         */
        Optional<SocketClientTunnelLayerContext> tunnelContext() {
            for (var layer : layers) {
                if (layer instanceof SocketClientTunnelLayerContext t) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }

        /**
         * Walks the chain from tail to head and offers the pending read
         * to each layer until one accepts it.
         *
         * @param read the pending read request
         * @return {@code true} if some layer accepted the read
         */
        boolean offerPendingRead(SocketClientPendingRead read) {
            for (var i = layers.size() - 1; i >= 0; i--) {
                if (layers.get(i).setPendingRead(read)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Finds the first layer context that is currently handshaking.
         *
         * @return an optional containing the handshaking context, or empty
         *         if no layer is handshaking
         */
        Optional<SocketClientLayerContext> findHandshakingContext() {
            return findFirst(SocketClientLayerContext::isHandshaking);
        }

        /**
         * Returns whether any layer context has pending output data.
         *
         * @return {@code true} if any layer has pending output
         */
        boolean hasPendingOutput() {
            return findFirst(SocketClientLayerContext::hasPendingOutput).isPresent();
        }

        /**
         * Finds the first layer context that has pending delegated tasks.
         *
         * @return an optional containing the context with pending tasks,
         *         or empty if none
         */
        Optional<SocketClientLayerContext> findTasksPendingContext() {
            return findFirst(SocketClientLayerContext::isTasksPending);
        }

        /**
         * Drains buffered data from all layer contexts into their next layers.
         *
         * @return {@code true} if all layers drained successfully,
         *         {@code false} if any layer signalled close
         * @throws IOException if layer processing fails
         */
        boolean drainAllLayers() throws IOException {
            for (var layer : layers) {
                if (!layer.drainToNextLayer()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns the first layer context matching the predicate, walking
         * the chain from head to tail.
         */
        private Optional<SocketClientLayerContext> findFirst(Predicate<SocketClientLayerContext> predicate) {
            for (var layer : layers) {
                if (predicate.test(layer)) {
                    return Optional.of(layer);
                }
            }
            return Optional.empty();
        }
    }
}
