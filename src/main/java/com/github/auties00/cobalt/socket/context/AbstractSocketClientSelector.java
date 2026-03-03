package com.github.auties00.cobalt.socket.context;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.System.Logger.Level.ERROR;


public abstract class AbstractSocketClientSelector {
    private static final long SAFETY_TIMEOUT_MS = 5000;
    private static final int MAX_MESSAGE_LENGTH = 1048576;

    protected final System.Logger logger;
    protected final Selector selector;
    protected final AtomicBoolean wakeupPending;
    protected volatile Thread selectorThread;

    protected AbstractSocketClientSelector() throws IOException {
        this.logger = System.getLogger(AbstractSocketClientSelector.class.getName());
        this.selector = Selector.open();
        this.wakeupPending = new AtomicBoolean();
    }

    @SuppressWarnings("MagicConstant")
    public synchronized void register(SocketChannel channel, int ops, AbstractSocketClientContext context) throws IOException {
        selector.wakeup();
        channel.register(selector, ops, context);
        if (selectorThread == null || !selectorThread.isAlive()) {
            selectorThread = Thread.startVirtualThread(this::select);
        }
    }

    public void unregister(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null) {
            return;
        }

        // Always cancel key and close channel to prevent event loop leaks
        key.cancel();
        try {
            channel.close();
        } catch (IOException _) {

        }

        var ctx = (AbstractSocketClientContext) key.attachment();

        // Always notify waiting threads so they don't block until timeout
        var pendingRead = ctx.pendingBinaryRead;
        if (pendingRead != null) {
            pendingRead.length = -1;
            ctx.pendingBinaryRead = null;
            synchronized (pendingRead.lock) {
                pendingRead.lock.notifyAll();
            }
        }

        synchronized (ctx.connectionLock) {
            ctx.connectionLock.notifyAll();
        }

        // Use connected CAS to guard listener notification and executor shutdown
        if (!ctx.connected.compareAndSet(true, false)) {
            wakeup();
            return;
        }

        ctx.stopListenerExecutor();

        try {
            ctx.onClose();
        } catch (Throwable error) {
            logger.log(ERROR, error);
        }

        wakeup();
    }

    public boolean isConnected(SocketChannel channel) {
        if (channel == null) {
            return false;
        }

        var key = channel.keyFor(selector);
        if (key == null) {
            return false;
        }

        return ((AbstractSocketClientContext) key.attachment()).connected.get();
    }

    public boolean addRead(SocketChannel channel, SocketPendingRead read) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }
        var ctx = (AbstractSocketClientContext) key.attachment();
        if (ctx.pendingBinaryRead != null) {
            return false;
        }
        ctx.pendingBinaryRead = read;
        try {
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        } catch (CancelledKeyException _) {
            ctx.pendingBinaryRead = null;
            return false;
        }
        wakeup();
        return true;
    }

    public boolean markReady(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        var ctx = (AbstractSocketClientContext) key.attachment();
        ctx.startListenerExecutor();
        ctx.tunnelled = true;
        try {
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        } catch (CancelledKeyException _) {
            return false;
        }
        wakeup();
        return true;
    }

    public boolean addWrite(SocketChannel channel, ByteBuffer... buffers) {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            return false;
        }

        if (buffers == null || buffers.length == 0) {
            return true;
        }

        var ctx = (AbstractSocketClientContext) key.attachment();
        var hasWrites = false;
        for (var buffer : buffers) {
            if (buffer != null && buffer.hasRemaining()) {
                ctx.pendingWrites.offer(buffer);
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

    protected void handleKey(SelectionKey key) {
        var attachment = key.attachment();
        if (!(attachment instanceof AbstractSocketClientContext ctx)) {
            return;
        }

        var channel = (SocketChannel) key.channel();
        try {
            if (key.isConnectable()) {
                if (channel.finishConnect()) {
                    key.interestOps(SelectionKey.OP_READ);
                    synchronized (ctx.connectionLock) {
                        ctx.connectionLock.notifyAll();
                    }
                }
            }

            if (key.isReadable()) {
                if(!processPreTunnelRead(channel, ctx, key)) {
                    unregister(channel);
                    return;
                }else if (!processRead(channel, ctx, key)) {
                    unregister(channel);
                    return;
                }
            }
            if (key.isWritable()) {
                if (processWrite(channel, ctx)) {
                    if (!ctx.pendingWrites.isEmpty()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    } else {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    }
                }
            }
        } catch (Exception _) {
            unregister(channel);
        }
    }

    protected boolean processPreTunnelRead(SocketChannel channel, AbstractSocketClientContext ctx, SelectionKey key) throws IOException {
        var pendingRead = ctx.pendingBinaryRead;
        if (pendingRead == null) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            return true;
        }

        var bytesRead = executePreTunnelRead(channel, ctx, pendingRead.buffer);
        if (bytesRead == -1) {
            pendingRead.length = -1;
            synchronized (pendingRead.lock) {
                pendingRead.lock.notifyAll();
            }
            return false;
        }

        if (bytesRead == 0) {
            return true;
        }

        if (pendingRead.length == -1) {
            pendingRead.length = 0;
        }
        pendingRead.length += bytesRead;
        if (!pendingRead.fullRead || !pendingRead.buffer.hasRemaining()) {
            if (pendingRead.fullRead) {
                pendingRead.buffer.flip();
            }
            ctx.pendingBinaryRead = null;
            synchronized (pendingRead.lock) {
                pendingRead.lock.notifyAll();
            }
        }

        return true;
    }

    protected void wakeup() {
        if (wakeupPending.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }

    protected boolean feedDatagram(AbstractSocketClientContext ctx, ByteBuffer source) {
        while (source.hasRemaining()) {
            var noDatagram = ctx.datagramBuffer == null;
            var target = noDatagram ? ctx.datagramLengthBuffer : ctx.datagramBuffer;
            var count = Math.min(source.remaining(), target.remaining());
            var savedLimit = source.limit();
            source.limit(source.position() + count);
            target.put(source);
            source.limit(savedLimit);
            if (!target.hasRemaining() && !advanceDatagram(ctx, noDatagram)) {
                return false;
            }
        }
        return true;
    }

    protected boolean advanceDatagram(AbstractSocketClientContext ctx, boolean noDatagram) {
        if (noDatagram) {
            ctx.datagramLengthBuffer.flip();
            var length = ((ctx.datagramLengthBuffer.get() & 0xFF) << 16)
                         | ((ctx.datagramLengthBuffer.get() & 0xFF) << 8)
                         | (ctx.datagramLengthBuffer.get() & 0xFF);
            if (length > MAX_MESSAGE_LENGTH) {
                return false;
            }
            ctx.datagramBuffer = ByteBuffer.allocate(length);
            return true;
        } else {
            ctx.datagramBuffer.flip();
            ctx.datagramLengthBuffer.clear();
            var buffer = ctx.datagramBuffer;
            ctx.datagramBuffer = null;
            // Ordering is important at this stage
            ctx.datagramListenerVirtualExecutor.execute(() -> ctx.onDatagram(buffer));
            return true;
        }
    }

    protected abstract boolean processRead(SocketChannel channel, AbstractSocketClientContext ctx, SelectionKey key) throws IOException;

    protected abstract boolean processWrite(SocketChannel channel, AbstractSocketClientContext ctx) throws IOException;

    protected abstract int executePreTunnelRead(SocketChannel channel, AbstractSocketClientContext ctx, ByteBuffer target) throws IOException;

    protected abstract boolean preSeedDatagram(SocketChannel channel, ByteBuffer leftover);

    private void select() {
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
}
