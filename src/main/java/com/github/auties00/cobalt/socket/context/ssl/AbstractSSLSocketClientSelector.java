package com.github.auties00.cobalt.socket.context.ssl;

import com.github.auties00.cobalt.socket.context.AbstractSocketClientContext;
import com.github.auties00.cobalt.socket.context.AbstractSocketClientSelector;
import com.github.auties00.cobalt.socket.context.SocketPendingWrites;
import com.github.auties00.cobalt.socket.transport.websocket.WebSocketClientContext;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import static java.lang.System.Logger.Level.ERROR;


public abstract class AbstractSSLSocketClientSelector extends AbstractSocketClientSelector {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    protected AbstractSSLSocketClientSelector() throws IOException {
        super();
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

        var sslCtx = (AbstractSSLSocketClientContext) key.attachment();

        // Always notify waiting threads so they don't block until timeout
        var pendingRead = sslCtx.pendingBinaryRead;
        if (pendingRead != null) {
            pendingRead.length = -1;
            sslCtx.pendingBinaryRead = null;
            synchronized (pendingRead.lock) {
                pendingRead.lock.notifyAll();
            }
        }

        synchronized (sslCtx.connectionLock) {
            sslCtx.connectionLock.notifyAll();
        }

        synchronized (sslCtx.sslHandshakeLock) {
            sslCtx.sslHandshakeLock.notifyAll();
        }

        // Use connected CAS to guard listener notification and executor shutdown
        if (!sslCtx.connected.compareAndSet(true, false)) {
            wakeup();
            return;
        }

        if (sslCtx.sslEngine != null) {
            sslCtx.sslEngine.closeOutbound();
        }

        sslCtx.stopListenerExecutor();

        try {
            sslCtx.onClose();
        } catch (Throwable error) {
            logger.log(ERROR, error);
        }

        wakeup();
    }

    public void startTlsHandshake(SocketChannel channel, long timeout) throws IOException {
        var key = channel.keyFor(selector);
        if (key == null || !key.isValid()) {
            throw new IOException("Channel not registered");
        }

        var sslCtx = (AbstractSSLSocketClientContext) key.attachment();
        if (sslCtx.sslEngine == null) {
            throw new IOException("SSL not initialized on context");
        }

        sslCtx.sslEngine.beginHandshake();

        try {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } catch (CancelledKeyException e) {
            throw new IOException("Key cancelled during TLS init");
        }
        wakeup();

        synchronized (sslCtx.sslHandshakeLock) {
            var deadline = System.currentTimeMillis() + timeout;
            while (!sslCtx.sslHandshakeComplete && sslCtx.connected.get()) {
                var remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("TLS handshake timed out");
                }
                try {
                    sslCtx.sslHandshakeLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("TLS handshake interrupted", e);
                }
            }
        }

        if (!sslCtx.sslHandshakeComplete) {
            throw new IOException("TLS handshake failed: connection lost");
        }
    }

    @Override
    protected void handleKey(SelectionKey key) {
        var sslCtx = (AbstractSSLSocketClientContext)key.attachment();

        var channel = (SocketChannel) key.channel();
        try {
            if (key.isConnectable()) {
                if (channel.finishConnect()) {
                    key.interestOps(SelectionKey.OP_READ);
                    synchronized (sslCtx.connectionLock) {
                        sslCtx.connectionLock.notifyAll();
                    }
                }
            }

            if (sslCtx.sslEngine != null && sslCtx.sslHandshaking) {
                if (sslCtx.sslTasksPending) {
                    return;
                }
                if (!driveHandshake(channel, sslCtx, key)) {
                    unregister(channel);
                }
                return;
            }

            if (key.isReadable()) {
                if(!processPreTunnelRead(channel, sslCtx, key)) {
                    unregister(channel);
                    return;
                }else if (!processRead(channel, sslCtx, key)) {
                    unregister(channel);
                    return;
                }
            }
            if (key.isWritable()) {
                if (processWrite(channel, sslCtx)) {
                    if (!sslCtx.pendingWrites.isEmpty()) {
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

    @Override
    protected int executePreTunnelRead(SocketChannel channel, AbstractSocketClientContext ctx, ByteBuffer target) throws IOException {
        var sslCtx = (AbstractSSLSocketClientContext) ctx;
        if (sslCtx.sslEngine == null) {
            return channel.read(target);
        }

        // Slow path: drain leftover decrypted data from previous unwrap
        sslCtx.appInBuffer.flip();
        if (sslCtx.appInBuffer.hasRemaining()) {
            var transferred = transferBytes(sslCtx.appInBuffer, target);
            sslCtx.appInBuffer.compact();
            if (transferred > 0) {
                return transferred;
            }
        } else {
            sslCtx.appInBuffer.compact();
        }

        // Read encrypted data from channel into direct buffer
        var bytesRead = channel.read(sslCtx.netInBuffer);
        if (bytesRead == -1) {
            return -1;
        }
        if (bytesRead == 0 && sslCtx.netInBuffer.position() == 0) {
            return 0;
        }

        // Fast path: unwrap directly into target (avoids appInBuffer copy)
        sslCtx.netInBuffer.flip();
        SSLEngineResult result;
        try {
            result = sslCtx.sslEngine.unwrap(sslCtx.netInBuffer, target);
        } catch (SSLException e) {
            sslCtx.netInBuffer.compact();
            throw new IOException("SSL unwrap failed", e);
        }

        switch (result.getStatus()) {
            case OK -> {
                sslCtx.netInBuffer.compact();
                return result.bytesProduced();
            }
            case BUFFER_UNDERFLOW -> {
                sslCtx.netInBuffer.compact();
                return 0;
            }
            case BUFFER_OVERFLOW -> {
                // Target too small for a full TLS record; unwrap into appInBuffer
                try {
                    result = sslCtx.sslEngine.unwrap(sslCtx.netInBuffer, sslCtx.appInBuffer);
                } catch (SSLException e) {
                    sslCtx.netInBuffer.compact();
                    throw new IOException("SSL unwrap failed", e);
                }
                sslCtx.netInBuffer.compact();

                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    return 0;
                }

                sslCtx.appInBuffer.flip();
                var transferred = transferBytes(sslCtx.appInBuffer, target);
                sslCtx.appInBuffer.compact();
                return transferred;
            }
            case CLOSED -> {
                sslCtx.netInBuffer.compact();
                return -1;
            }
        }

        sslCtx.netInBuffer.compact();
        return 0;
    }

    public boolean drainSslAppBuffer(SocketChannel channel) {
        var key = channel.keyFor(selector);
        if (key == null) {
            return false;
        }

        var sslCtx = (AbstractSSLSocketClientContext) key.attachment();
        if (sslCtx.sslEngine == null || sslCtx.appInBuffer == null) {
            return true;
        }

        sslCtx.appInBuffer.flip();
        if (!sslCtx.appInBuffer.hasRemaining()) {
            sslCtx.appInBuffer.compact();
            return true;
        }

        var result = feedDatagram(sslCtx, sslCtx.appInBuffer);
        sslCtx.appInBuffer.compact();
        return result;
    }

    protected int transferBytes(ByteBuffer src, ByteBuffer dst) {
        var count = Math.min(src.remaining(), dst.remaining());
        if (count <= 0) {
            return 0;
        }
        var savedLimit = src.limit();
        src.limit(src.position() + count);
        dst.put(src);
        src.limit(savedLimit);
        return count;
    }

    protected int countConsumed(SocketPendingWrites.Claim claim) {
        var consumed = 0;
        for (var i = claim.offset(); i < claim.offset() + claim.count(); i++) {
            if (claim.array()[i].hasRemaining()) {
                break;
            }
            consumed++;
        }
        return consumed;
    }

    @Override
    protected boolean processWrite(SocketChannel channel, AbstractSocketClientContext ctx) throws IOException {
        var webSocketCtx = (WebSocketClientContext) ctx;
        if (webSocketCtx.sslEngine != null) {
            return processWriteSsl(channel, webSocketCtx);
        } else {
            return processWriteDirect(channel, webSocketCtx);
        }
    }

    private boolean processWriteDirect(SocketChannel channel, WebSocketClientContext ctx) throws IOException {
        while (ctx.connected.get()) {
            var claim = ctx.pendingWrites.claim();
            if (claim.isEmpty()) {
                return true;
            }

            channel.write(claim.array(), claim.offset(), claim.count());

            var consumed = countConsumed(claim);
            ctx.pendingWrites.release(consumed);

            if (consumed < claim.count()) {
                return false;
            }
        }
        return false;
    }

    private boolean processWriteSsl(SocketChannel channel, WebSocketClientContext ctx) throws IOException {
        // Flush residual encrypted data from a previous partial write
        if (ctx.netOutBuffer.position() > 0) {
            ctx.netOutBuffer.flip();
            channel.write(ctx.netOutBuffer);
            if (ctx.netOutBuffer.hasRemaining()) {
                ctx.netOutBuffer.compact();
                return false;
            }
            ctx.netOutBuffer.compact();
        }

        while (ctx.connected.get()) {
            var claim = ctx.pendingWrites.claim();
            if (claim.isEmpty()) {
                return true;
            }

            // Coalesced wrap: pack multiple app buffers into one TLS record
            ctx.netOutBuffer.clear();
            SSLEngineResult result;
            try {
                result = ctx.sslEngine.wrap(claim.array(), claim.offset(), claim.count(), ctx.netOutBuffer);
            } catch (SSLException e) {
                throw new IOException("SSL wrap failed", e);
            }

            if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                throw new IOException("SSL netOutBuffer overflow: buffer sized incorrectly after handshake");
            }

            ctx.netOutBuffer.flip();
            while (ctx.netOutBuffer.hasRemaining()) {
                if (channel.write(ctx.netOutBuffer) == 0) {
                    ctx.netOutBuffer.compact();
                    ctx.pendingWrites.release(countConsumed(claim));
                    return false;
                }
            }
            ctx.netOutBuffer.compact();

            var consumed = countConsumed(claim);
            ctx.pendingWrites.release(consumed);

            if (consumed < claim.count()) {
                continue;
            }
        }
        return false;
    }
}
