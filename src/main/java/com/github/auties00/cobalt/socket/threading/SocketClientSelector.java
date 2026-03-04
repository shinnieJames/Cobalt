package com.github.auties00.cobalt.socket.threading;

import com.github.auties00.cobalt.socket.security.whatsapp.WhatsAppLayerContext;
import com.github.auties00.cobalt.socket.security.tls.TlsLayerContext;
import com.github.auties00.cobalt.socket.security.tls.TlsSocketClientSecurityLayer;
import com.github.auties00.cobalt.socket.transport.SocketClientTransportLayerContext;
import com.github.auties00.cobalt.socket.tunnel.TunnelLayerContext;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * {@link SocketClientTransportLayerContext.PendingWrites} queue.  If a
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
     * @param channel the non-blocking socket channel
     * @param ops     the initial interest ops
     * @param context the per-connection context
     * @throws IOException if registration fails
     */
    @SuppressWarnings("MagicConstant")
    public synchronized void register(SocketChannel channel, int ops, SocketClientContext context) throws IOException {
        selector.wakeup();
        channel.register(selector, ops, context);
        if (selectorThread == null || !selectorThread.isAlive()) {
            selectorThread = Thread.startVirtualThread(this);
        }
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
        try {
            channel.close();
        } catch (IOException _) {
        }

        var ctx = (SocketClientContext) key.attachment();
        var transportCtx = ctx.transportContext();

        // Notify connection lock
        synchronized (transportCtx.connectionLock) {
            transportCtx.connectionLock.notifyAll();
        }

        // Use connected CAS to guard single notification
        if (!transportCtx.connected.compareAndSet(true, false)) {
            wakeup();
            return;
        }

        // Notify all layer contexts
        var bottom = ctx.bottomProcessingContext();
        if (bottom != null) {
            bottom.onDisconnect();
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

        return ((SocketClientContext) key.attachment()).transportContext().connected.get();
    }

    /**
     * Posts a blocking read request for the pre-tunnel phase.
     *
     * @param channel the channel
     * @param read    the pending read request
     * @return {@code true} if the read was posted
     */
    public boolean addRead(SocketChannel channel, SocketClientTransportLayerContext.PendingRead read) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        var ctx = (SocketClientContext) key.attachment();
        Optional<TunnelLayerContext> tunnelCtx = ctx.getLayerContext(
                com.github.auties00.cobalt.socket.tunnel.SocketClientTunnelLayer.class
        );
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

        var ctx = (SocketClientContext) key.attachment();
        var pendingWrites = ctx.transportContext().pendingWrites;
        var hasWrites = false;
        for (var buffer : buffers) {
            if (buffer != null && buffer.hasRemaining()) {
                pendingWrites.offer(buffer);
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
     * Marks the connection as tunnelled (proxy handshake complete).
     *
     * <p>Starts the listener executor, sets the tunnel context to
     * tunnelled mode, and enables read interest.
     *
     * @param channel the channel
     * @return {@code true} if successfully marked
     */
    public boolean markReady(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        var ctx = (SocketClientContext) key.attachment();

        // Start the application layer's listener executor
        Optional<WhatsAppLayerContext> appCtx = ctx.getLayerContext(
                com.github.auties00.cobalt.socket.application.SocketClientApplicationLayer.class
        );
        appCtx.ifPresent(WhatsAppLayerContext::startListenerExecutor);

        // Mark tunnel as established
        Optional<TunnelLayerContext> tunnelCtx = ctx.getLayerContext(
                com.github.auties00.cobalt.socket.tunnel.SocketClientTunnelLayer.class
        );
        tunnelCtx.ifPresent(TunnelLayerContext::markTunnelled);

        try {
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        } catch (CancelledKeyException _) {
            return false;
        }
        wakeup();
        return true;
    }

    /**
     * Initiates a TLS handshake and blocks until it completes.
     *
     * @param channel the channel
     * @param timeout the handshake timeout in milliseconds
     * @throws IOException if the handshake fails or times out
     */
    public void startTlsHandshake(SocketChannel channel, long timeout) throws IOException {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            throw new IOException("Channel not registered");
        }

        var ctx = (SocketClientContext) key.attachment();
        Optional<TlsLayerContext> tlsCtx = ctx.getLayerContext(TlsSocketClientSecurityLayer.class);
        if (tlsCtx.isEmpty()) {
            throw new IOException("TLS not initialized on context");
        }

        var tls = tlsCtx.get();
        tls.beginHandshake();

        try {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } catch (CancelledKeyException e) {
            throw new IOException("Key cancelled during TLS init");
        }
        wakeup();

        synchronized (tls.handshakeLock()) {
            var deadline = System.currentTimeMillis() + timeout;
            while (!tls.isHandshakeComplete() && ctx.transportContext().connected.get()) {
                var remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("TLS handshake timed out");
                }
                try {
                    tls.handshakeLock().wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("TLS handshake interrupted", e);
                }
            }
        }

        if (!tls.isHandshakeComplete()) {
            throw new IOException("TLS handshake failed: connection lost");
        }
    }

    /**
     * Drains any leftover decrypted data from the TLS application input
     * buffer into the layer pipeline.
     *
     * <p>After a sequence of synchronous reads over TLS (such as a
     * WebSocket upgrade handshake), the TLS layer may have buffered
     * decrypted bytes that were not consumed by the pending read
     * requests.  This method feeds those bytes into the next layer so
     * they are not lost when the connection transitions to asynchronous
     * data flow.
     *
     * @param channel the channel
     * @return {@code true} if draining succeeded or no TLS layer is
     *         present, {@code false} if draining failed
     */
    public boolean drainSslAppBuffer(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null) {
            return false;
        }

        var ctx = (SocketClientContext) key.attachment();
        Optional<TlsLayerContext> tlsCtx = ctx.getLayerContext(TlsSocketClientSecurityLayer.class);
        if (tlsCtx.isEmpty()) {
            return true;
        }

        try {
            return tlsCtx.get().drainToNextLayer();
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

        var ctx = (SocketClientContext) key.attachment();
        var bottom = ctx.bottomProcessingContext();
        if (bottom == null) {
            return false;
        }

        try {
            return !(bottom.feedFromSource(leftover) instanceof SocketClientInboundResult.Close);
        } catch (IOException _) {
            return false;
        }
    }

    /**
     * Finalizes state after a synchronous protocol handshake/upgrade.
     *
     * <p>This transitions the connection to asynchronous post-handshake
     * mode by:
     * <ol>
     * <li>marking the connection ready for read callbacks</li>
     * <li>feeding any already-buffered leftover bytes into the pipeline</li>
     * <li>draining pending decrypted TLS application data</li>
     * </ol>
     *
     * @param channel  the channel
     * @param leftover leftover bytes from the synchronous parser, or {@code null}
     * @return {@code true} if finalization succeeded
     */
    public boolean completeUpgrade(SocketChannel channel, ByteBuffer leftover) {
        if (!markReady(channel)) {
            return false;
        }
        if (leftover != null && leftover.hasRemaining() && !preSeedDatagram(channel, leftover)) {
            return false;
        }
        return drainSslAppBuffer(channel);
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

    private void handleKey(SelectionKey key) {
        var attachment = key.attachment();
        if (!(attachment instanceof SocketClientContext ctx)) {
            return;
        }

        var channel = (SocketChannel) key.channel();
        var transportCtx = ctx.transportContext();

        try {
            if (key.isConnectable()) {
                if (channel.finishConnect()) {
                    key.interestOps(SelectionKey.OP_READ);
                    synchronized (transportCtx.connectionLock) {
                        transportCtx.connectionLock.notifyAll();
                    }
                }
            }

            // Check for TLS handshaking
            Optional<TlsLayerContext> tlsCtx = ctx.getLayerContext(TlsSocketClientSecurityLayer.class);
            if (tlsCtx.isPresent() && tlsCtx.get().isHandshaking()) {
                if (tlsCtx.get().isTasksPending()) {
                    return;
                }
                if (!processHandshake(channel, ctx, key, tlsCtx.get())) {
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
                var hasPendingWrites = !transportCtx.pendingWrites.isEmpty()
                        || tlsCtx.map(TlsLayerContext::hasPendingEncryptedOutput).orElse(false);
                key.interestOps(updateWriteInterestOps(key.interestOps(), hasPendingWrites));
            }
        } catch (Exception _) {
            unregister(channel);
        }
    }

    private boolean processHandshake(SocketChannel channel, SocketClientContext ctx, SelectionKey key, TlsLayerContext tlsCtx) throws IOException {
        var result = tlsCtx.driveHandshake(channel);
        return handleInboundResult(channel, ctx, key, result);
    }

    private boolean processRead(SocketChannel channel, SocketClientContext ctx) throws IOException {
        var bottom = ctx.bottomProcessingContext();
        if (bottom == null) {
            return false;
        }

        var target = bottom.inboundTarget();
        var bytesRead = channel.read(target);
        var result = bottom.processInbound(bytesRead);

        var key = channel.keyFor(selector);
        return key != null && handleInboundResult(channel, ctx, key, result);
    }

    private boolean handleInboundResult(SocketChannel channel, SocketClientContext ctx, SelectionKey key, SocketClientInboundResult result) throws IOException {
        return switch (result) {
            case SocketClientInboundResult.Continue _ -> true;
            case SocketClientInboundResult.Buffering _ -> true;
            case SocketClientInboundResult.Close _ -> false;
            case SocketClientInboundResult.NeedsWrite needsWrite -> {
                // Enqueue the write data and enable write interest
                var transportCtx = ctx.transportContext();
                for (var buf : needsWrite.data()) {
                    if (buf != null && buf.hasRemaining()) {
                        transportCtx.pendingWrites.offer(buf);
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
                // TLS tasks pending — disable interest, run tasks
                try {
                    key.interestOps(0);
                } catch (CancelledKeyException _) {
                    yield true;
                }
                Optional<TlsLayerContext> tlsCtx = ctx.getLayerContext(TlsSocketClientSecurityLayer.class);
                tlsCtx.ifPresent(tls -> tls.runDelegatedTasks(() -> {
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

    private boolean processWrite(SocketChannel channel, SocketClientContext ctx) throws IOException {
        var transportCtx = ctx.transportContext();
        Optional<TlsLayerContext> tlsCtx = ctx.getLayerContext(TlsSocketClientSecurityLayer.class);

        if (tlsCtx.isPresent()) {
            return processWriteSsl(channel, transportCtx, tlsCtx.get());
        } else {
            return processWriteDirect(channel, transportCtx);
        }
    }

    private boolean processWriteDirect(SocketChannel channel, SocketClientTransportLayerContext transportCtx) throws IOException {
        while (transportCtx.connected.get()) {
            var claim = transportCtx.pendingWrites.claim();
            if (claim.isEmpty()) {
                return true;
            }

            channel.write(claim.array(), claim.offset(), claim.count());

            var consumed = countConsumed(claim);
            transportCtx.pendingWrites.release(consumed);

            if (consumed < claim.count()) {
                return false;
            }
        }
        return false;
    }

    private boolean processWriteSsl(SocketChannel channel, SocketClientTransportLayerContext transportCtx, TlsLayerContext tlsCtx) throws IOException {
        while (transportCtx.connected.get()) {
            var claim = transportCtx.pendingWrites.claim();
            if (claim.isEmpty()) {
                return true;
            }

            if (!tlsCtx.wrapAndWrite(channel, claim.array(), claim.offset(), claim.count())) {
                transportCtx.pendingWrites.release(countConsumed(claim));
                return false;
            }

            var consumed = countConsumed(claim);
            transportCtx.pendingWrites.release(consumed);

            if (consumed < claim.count()) {
                continue;
            }
        }
        return false;
    }

    private static int countConsumed(SocketClientTransportLayerContext.PendingWrites.Claim claim) {
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
}
