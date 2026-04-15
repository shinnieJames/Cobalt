package com.github.auties00.cobalt.socket.threading;

import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayerContext;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTransportSecurityLayerContext;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTunnelSecurityLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayerContext;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayerContext;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
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
     * Registers a channel with this selector.
     *
     * <p>Starts the selector virtual thread if it is not already running.
     *
     * @param channel          the non-blocking socket channel
     * @param transportContext the transport-level state for this connection
     * @throws IOException if registration fails
     */
    public synchronized void register(SocketChannel channel, SocketClientTransportLayerContext transportContext) throws IOException {
        selector.wakeup();
        channel.register(selector, SelectionKey.OP_CONNECT, AttachmentData.newConnectionContext(transportContext));
        if (selectorThread == null || !selectorThread.isAlive()) {
            selectorThread = Thread.startVirtualThread(this);
        }
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
        var transportCtx = ctx.transportContext();

        // Notify connection lock
        synchronized (transportCtx.connectionLock()) {
            transportCtx.connectionLock().notifyAll();
        }

        // Use connected CAS to guard single notification
        if (!transportCtx.compareAndSetConnected(true, false)) {
            try {
                channel.close();
            } catch (IOException _) {
            }
            wakeup();
            return;
        }

        // Notify all layer contexts before closing the channel so that
        // the TLS layer can send its close_notify alert (RFC 8446 §6.1)
        ctx.bottomProcessingContext().onDisconnect();

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

        return ((AttachmentData) key.attachment()).transportContext().isConnected();
    }

    /**
     * Posts a blocking read request for the pre-tunnel phase.
     *
     * @param channel the channel
     * @param read    the pending read request
     * @return {@code true} if the read was posted
     */
    public boolean addRead(SocketChannel channel, SocketClientPendingRead read) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        var ctx = (AttachmentData) key.attachment();
        var tunnelCtx = ctx.tunnelContext();
        if (tunnelCtx.isEmpty()) {
            return false;
        }

        if (!tunnelCtx.get().setPendingRead(read)) {
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
        var pendingWrites = ctx.transportContext().pendingWrites();
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
            while (!securityContext.isHandshakeComplete() && ctx.transportContext().isConnected()) {
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
     * Feeds leftover bytes from a proxy handshake into the datagram
     * pipeline.
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

        // Leftover bytes from the HTTP upgrade parser are already
        // TLS-decrypted.  Feed them into the first layer above transport
        // (which skips both the raw transport and TLS layers) so they are
        // not erroneously re-read or re-decrypted.
        var tunnelCtx = ctx.tunnelContext();
        SocketClientLayerContext target;
        if (tunnelCtx.isPresent()) {
            target = tunnelCtx.get();
        } else {
            target = ctx.firstLayerAboveTransport();
            if (target == null) {
                return false;
            }
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
        var transportCtx = ctx.transportContext();

        try {
            if (key.isConnectable()) {
                if (channel.finishConnect()) {
                    key.interestOps(SelectionKey.OP_READ);
                    synchronized (transportCtx.connectionLock()) {
                        transportCtx.connectionLock().notifyAll();
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
                var hasPendingWrites = !transportCtx.pendingWrites().isEmpty() || ctx.hasPendingOutput();
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
        var bottom = ctx.bottomProcessingContext();
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
                var transportCtx = ctx.transportContext();
                for (var buf : needsWrite.data()) {
                    if (buf != null && buf.hasRemaining()) {
                        transportCtx.pendingWrites().offer(buf);
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
        var transportCtx = ctx.transportContext();
        while (transportCtx.isConnected()) {
            var claim = transportCtx.pendingWrites().claim();
            if (claim.isEmpty()) {
                return true;
            }

            var success = transportCtx.processOutbound(channel, claim.array(), claim.offset(), claim.count());

            var consumed = countConsumed(claim);
            transportCtx.pendingWrites().release(consumed);
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
     * Per-connection context attached to a {@link SelectionKey}
     * as its attachment.
     *
     * <p>Layer contexts are stored as explicit typed fields in a fixed
     * bottom-to-top order: transport security, tunnel security, tunnel,
     * then application layers. When a new context is registered, the
     * chain is rebuilt automatically so the inbound processing pipeline
     * is wired correctly.
     */
    private static final class AttachmentData {
        /**
         * Transport-level state: connection lifecycle, pending writes.
         */
        private final SocketClientTransportLayerContext transportContext;

        /**
         * Transport-level security context (TLS or plain passthrough).
         */
        private SocketClientTransportSecurityLayerContext transportSecurity;

        /**
         * Tunnel-level security context (TLS or plain passthrough).
         */
        private SocketClientTunnelSecurityLayerContext tunnelSecurity;

        /**
         * Tunnel layer context (proxy handshake and blocking reads).
         */
        private SocketClientTunnelLayerContext tunnel;

        /**
         * Application layer contexts in registration order (bottom-to-top).
         */
        private final SequencedCollection<SocketClientApplicationLayerContext> applicationLayers = new ArrayList<>();

        /**
         * Creates a context with the given transport context.
         *
         * @param transportContext the transport-level state
         */
        private AttachmentData(SocketClientTransportLayerContext transportContext) {
            this.transportContext = Objects.requireNonNull(transportContext);
        }

        /**
         * Creates a new connection context wrapping the given transport context.
         *
         * @param transportContext the transport-level state for the connection
         * @return a new {@code SocketClientContext}
         */
        static AttachmentData newConnectionContext(SocketClientTransportLayerContext transportContext) {
            return new AttachmentData(transportContext);
        }

        /**
         * Returns the transport-level context.
         *
         * @return the transport context, never {@code null}
         */
        SocketClientTransportLayerContext transportContext() {
            return transportContext;
        }

        /**
         * Returns the bottommost processing layer context.
         *
         * <p>This is always the transport context — the one that the
         * selector reads bytes into and calls {@code processInbound()} on.
         *
         * @return the transport layer context, never {@code null}
         */
        SocketClientLayerContext bottomProcessingContext() {
            return transportContext;
        }

        /**
         * Returns the first layer context above the transport layer.
         *
         * <p>This is the first non-null processing layer above the raw
         * transport, used when leftover bytes are already TLS-decrypted
         * and should not be re-fed through the transport layer.
         *
         * @return the first layer above transport, or {@code null} if no
         *         processing layers are registered
         */
        SocketClientLayerContext firstLayerAboveTransport() {
            if (transportSecurity != null) return transportSecurity;
            if (tunnelSecurity != null) return tunnelSecurity;
            if (tunnel != null) return tunnel;
            if (!applicationLayers.isEmpty()) return applicationLayers.getFirst();
            return null;
        }

        /**
         * Registers a layer context and rebuilds the processing chain.
         *
         * <p>The concrete type of the context determines which field is set.
         * Transport and tunnel security are distinguished by their
         * respective context interfaces.
         *
         * @param layerContext the context to register
         */
        void addLayerContext(SocketClientLayerContext layerContext) {
            switch (layerContext) {
                case SocketClientTransportLayerContext _ -> { /* set at construction, no-op */ }
                case SocketClientTransportSecurityLayerContext ctx -> this.transportSecurity = ctx;
                case SocketClientTunnelSecurityLayerContext ctx -> this.tunnelSecurity = ctx;
                case SocketClientTunnelLayerContext t -> this.tunnel = t;
                case SocketClientApplicationLayerContext ctx -> this.applicationLayers.add(ctx);
                default -> throw new IllegalArgumentException("Unknown layer context type: " + layerContext.getClass());
            }
            rebuildChain();
        }

        /**
         * Returns the tunnel layer context, if present.
         *
         * @return an optional containing the tunnel context, or empty if
         *         no tunnel layer is registered
         */
        Optional<SocketClientTunnelLayerContext> tunnelContext() {
            return Optional.ofNullable(tunnel);
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
            if (!transportContext.drainToNextLayer()) return false;
            if (transportSecurity != null && !transportSecurity.drainToNextLayer()) return false;
            if (tunnelSecurity != null && !tunnelSecurity.drainToNextLayer()) return false;
            if (tunnel != null && !tunnel.drainToNextLayer()) return false;
            for (var appLayer : applicationLayers) {
                if (!appLayer.drainToNextLayer()) return false;
            }
            return true;
        }

        /**
         * Rebuilds the {@code nextLayer} chain by walking the fields in
         * bottom-to-top order and linking each context to the next.
         */
        private void rebuildChain() {
            var layers = new ArrayList<SocketClientLayerContext>();
            layers.add(transportContext);
            if (transportSecurity != null) layers.add(transportSecurity);
            if (tunnelSecurity != null) layers.add(tunnelSecurity);
            if (tunnel != null) layers.add(tunnel);
            layers.addAll(applicationLayers);
            linkLayers(layers.toArray(SocketClientLayerContext[]::new));
        }

        /**
         * Links non-null contexts in the given order, setting each one's
         * {@code nextLayer} to the next non-null context.
         */
        private static void linkLayers(SocketClientLayerContext... layers) {
            SocketClientLayerContext previous = null;
            for (var ctx : layers) {
                if (ctx == null) continue;
                if (previous != null) {
                    previous.setNextLayer(ctx);
                }
                previous = ctx;
            }
        }

        /**
         * Returns the first layer context matching the predicate, walking
         * fields in bottom-to-top order.
         */
        private Optional<SocketClientLayerContext> findFirst(Predicate<SocketClientLayerContext> predicate) {
            if (predicate.test(transportContext)) return Optional.of(transportContext);
            if (transportSecurity != null && predicate.test(transportSecurity)) return Optional.of(transportSecurity);
            if (tunnelSecurity != null && predicate.test(tunnelSecurity)) return Optional.of(tunnelSecurity);
            if (tunnel != null && predicate.test(tunnel)) return Optional.of(tunnel);
            for (var appLayer : applicationLayers) {
                if (predicate.test(appLayer)) return Optional.of(appLayer);
            }
            return Optional.empty();
        }
    }
}
