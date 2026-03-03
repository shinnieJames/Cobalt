package com.github.auties00.cobalt.socket.implementation.context.ssl;

import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketClientContext;
import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketSelector;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class AbstractSSLSocketSelector extends AbstractSocketSelector {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final System.Logger logger;
    private final Selector selector;
    private final AtomicBoolean wakeupPending;
    private volatile Thread selectorThread;

    private AbstractSSLSocketSelector() throws IOException {
        super();
        this.logger = System.getLogger(AbstractSSLSocketSelector.class.getName());
        this.selector = Selector.open();
        this.wakeupPending = new AtomicBoolean();
    }

    @Override
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

        var ctx = (AbstractSSLSocketClientContext) key.attachment();

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

        synchronized (ctx.sslHandshakeLock) {
            ctx.sslHandshakeLock.notifyAll();
        }

        // Use connected CAS to guard listener notification and executor shutdown
        if (!ctx.connected.compareAndSet(true, false)) {
            wakeup();
            return;
        }

        if (ctx.sslEngine != null) {
            ctx.sslEngine.closeOutbound();
        }

        ctx.stopListenerExecutor();

        wakeup();
    }

    public void startTlsHandshake(SocketChannel channel, long timeout) throws IOException {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            throw new IOException("Channel not registered");
        }

        var ctx = (AbstractSSLSocketClientContext) key.attachment();
        if (ctx.sslEngine == null) {
            throw new IOException("SSL not initialized on context");
        }

        ctx.sslEngine.beginHandshake();

        try {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } catch (CancelledKeyException e) {
            throw new IOException("Key cancelled during TLS init");
        }
        wakeup();

        synchronized (ctx.sslHandshakeLock) {
            var deadline = System.currentTimeMillis() + timeout;
            while (!ctx.sslHandshakeComplete && ctx.connected.get()) {
                var remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("TLS handshake timed out");
                }
                try {
                    ctx.sslHandshakeLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("TLS handshake interrupted", e);
                }
            }
        }

        if (!ctx.sslHandshakeComplete) {
            throw new IOException("TLS handshake failed: connection lost");
        }
    }

    @Override
    protected void handleKey(SelectionKey key) {
        var attachment = key.attachment();
        if (!(attachment instanceof AbstractSSLSocketClientContext ctx)) {
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

            if (ctx.sslEngine != null && ctx.sslHandshaking) {
                if (ctx.sslTasksPending) {
                    return;
                }
                if (!driveHandshake(channel, ctx, key)) {
                    unregister(channel);
                }
                return;
            }

            if (key.isReadable()) {
                if (!processRead(channel, ctx, key)) {
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

    private boolean driveHandshake(SocketChannel channel, AbstractSSLSocketClientContext ctx, SelectionKey key) throws IOException {
        var engine = ctx.sslEngine;

        // Flush residual encrypted data from a previous partial write
        if (ctx.netOutBuffer.position() > 0) {
            ctx.netOutBuffer.flip();
            while (ctx.netOutBuffer.hasRemaining()) {
                if (channel.write(ctx.netOutBuffer) == 0) {
                    ctx.netOutBuffer.compact();
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    return true;
                }
            }
            ctx.netOutBuffer.compact();
        }

        while (ctx.connected.get()) {
            switch (engine.getHandshakeStatus()) {
                case NEED_WRAP -> {
                    ctx.netOutBuffer.clear();
                    SSLEngineResult result;
                    try {
                        result = engine.wrap(EMPTY_BUFFER, ctx.netOutBuffer);
                    } catch (SSLException e) {
                        throw new IOException("TLS handshake wrap failed", e);
                    }
                    ctx.netOutBuffer.flip();

                    if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                        return false;
                    }

                    while (ctx.netOutBuffer.hasRemaining()) {
                        if (channel.write(ctx.netOutBuffer) == 0) {
                            ctx.netOutBuffer.compact();
                            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                            return true;
                        }
                    }
                    ctx.netOutBuffer.compact();
                }

                case NEED_UNWRAP -> {
                    if (channel.read(ctx.netInBuffer) == -1) {
                        return false;
                    }

                    ctx.netInBuffer.flip();
                    SSLEngineResult result;
                    try {
                        result = engine.unwrap(ctx.netInBuffer, ctx.appInBuffer);
                    } catch (SSLException e) {
                        ctx.netInBuffer.compact();
                        throw new IOException("TLS handshake unwrap failed", e);
                    }
                    ctx.netInBuffer.compact();

                    switch (result.getStatus()) {
                        case BUFFER_UNDERFLOW -> {
                            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                            return true;
                        }
                        case CLOSED -> {
                            return false;
                        }
                        default -> {
                            // BUFFER_OVERFLOW is unreachable during the handshake:
                            // OpenJDK's SSLEngineImpl.readRecord() skips the overflow
                            // check when !conContext.isNegotiated, and handshake records
                            // produce 0 application bytes.  By the time isNegotiated
                            // becomes true, getHandshakeStatus() returns FINISHED and
                            // driveHandshake() exits before any data-path unwrap.
                            // This holds across TLS 1.0-1.3 (including 0.5-RTT).
                        }
                    }
                }

                case NEED_TASK -> {
                    ctx.sslTasksPending = true;
                    key.interestOps(0);
                    Thread.startVirtualThread(() -> {
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                        }
                        ctx.sslTasksPending = false;
                        try {
                            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        } catch (CancelledKeyException _) {
                            return;
                        }
                        wakeup();
                    });
                    return true;
                }

                case FINISHED, NOT_HANDSHAKING -> {
                    ctx.sslHandshaking = false;
                    ctx.sslHandshakeComplete = true;
                    ctx.appInBuffer.clear();
                    ctx.resizeSslBuffers();
                    synchronized (ctx.sslHandshakeLock) {
                        ctx.sslHandshakeLock.notifyAll();
                    }
                    return true;
                }
            }
        }

        throw new IOException("Unexpected end of stream");
    }

    protected boolean processRead(SocketChannel channel, AbstractSocketClientContext ctx, SelectionKey key) throws IOException {

    }


    protected abstract boolean processWrite(SocketChannel channel, AbstractSocketClientContext ctx);
}
