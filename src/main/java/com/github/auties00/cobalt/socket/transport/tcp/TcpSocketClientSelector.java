package com.github.auties00.cobalt.socket.transport.tcp;

import com.github.auties00.cobalt.socket.context.AbstractSocketClientContext;
import com.github.auties00.cobalt.socket.context.ssl.AbstractSSLSocketClientSelector;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;


public final class TcpSocketClientSelector extends AbstractSSLSocketClientSelector {
    public static final TcpSocketClientSelector INSTANCE;

    static {
        try {
            INSTANCE = new TcpSocketClientSelector();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private TcpSocketClientSelector() throws IOException {
        super();
    }

    @Override
    protected boolean processRead(SocketChannel channel, AbstractSocketClientContext ctx, SelectionKey key) throws IOException {
        var tcpCtx = (TcpSocketClientContext) ctx;
        return tcpCtx.sslEngine != null
                ? processDatagramSsl(channel, tcpCtx)
                : processDatagramDirect(channel, tcpCtx);
    }

    private boolean processDatagramSsl(SocketChannel channel, TcpSocketClientContext ctx) throws IOException {
        while (ctx.connected.get()) {
            var bytesRead = channel.read(ctx.netInBuffer);
            if (bytesRead == -1) {
                return false;
            }

            // Unwrap all available TLS records
            ctx.netInBuffer.flip();
            while (ctx.netInBuffer.hasRemaining()) {
                SSLEngineResult result;
                try {
                    result = ctx.sslEngine.unwrap(ctx.netInBuffer, ctx.appInBuffer);
                } catch (SSLException e) {
                    ctx.netInBuffer.compact();
                    throw new IOException("SSL unwrap failed", e);
                }

                switch (result.getStatus()) {
                    case BUFFER_UNDERFLOW -> {} // incomplete TLS record
                    case CLOSED -> {
                        ctx.netInBuffer.compact();
                        return false;
                    }
                    case OK -> {
                        continue;
                    }
                    case BUFFER_OVERFLOW -> {
                        ctx.netInBuffer.compact();
                        throw new IOException("SSL appInBuffer overflow: buffer sized incorrectly after handshake");
                    }
                }
                break;
            }
            ctx.netInBuffer.compact();

            // Drain decrypted data into datagram framing
            ctx.appInBuffer.flip();
            while (ctx.appInBuffer.hasRemaining() && ctx.connected.get()) {
                var noDatagram = ctx.datagramBuffer == null;
                var target = noDatagram ? ctx.datagramLengthBuffer : ctx.datagramBuffer;
                var count = Math.min(ctx.appInBuffer.remaining(), target.remaining());
                var savedLimit = ctx.appInBuffer.limit();
                ctx.appInBuffer.limit(ctx.appInBuffer.position() + count);
                target.put(ctx.appInBuffer);
                ctx.appInBuffer.limit(savedLimit);

                if (!target.hasRemaining() && !advanceDatagram(ctx, noDatagram)) {
                    ctx.appInBuffer.compact();
                    return false;
                }
            }
            ctx.appInBuffer.compact();

            if (bytesRead == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean processDatagramDirect(SocketChannel channel, TcpSocketClientContext ctx) throws IOException {
        while (ctx.connected.get()) {
            var noDatagram = ctx.datagramBuffer == null;
            var target = noDatagram ? ctx.datagramLengthBuffer : ctx.datagramBuffer;
            var bytesRead = channel.read(target);
            if (bytesRead == -1) {
                return false;
            }
            if (target.hasRemaining()) {
                return true;
            }
            if (!advanceDatagram(ctx, noDatagram)) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean preSeedDatagram(SocketChannel channel, ByteBuffer leftover) {
        if (!leftover.hasRemaining()) {
            return true;
        }

        var key = channel.keyFor(selector);
        if (key == null) {
            return false;
        }

        var ctx = (TcpSocketClientContext) key.attachment();
        return feedDatagram(ctx, leftover);
    }
}
