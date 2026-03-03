package com.github.auties00.cobalt.socket.transport.tcp;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.client.tcp.TcpSocketClientListener;
import com.github.auties00.cobalt.socket.context.SocketPendingRead;
import com.github.auties00.cobalt.socket.transport.SocketClientTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public sealed class TcpSocketClientTransport implements SocketClientTransport<TcpSocketClientListener>
        permits HttpTunnelledTcpSocketClientTransport, SocksTunnelledTcpSocketClientTransport {
    private static final int DEFAULT_READ_TIMEOUT = 10_000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 30_000;

    public static TcpSocketClientTransport newTcpSocketClientTransport(WhatsAppClientProxy proxy) {
        return switch (proxy) {
            case null -> new TcpSocketClientTransport();
            case WhatsAppClientProxy.Http http -> new HttpTunnelledTcpSocketClientTransport(http);
            case WhatsAppClientProxy.Socks socks -> new SocksTunnelledTcpSocketClientTransport(socks);
        };
    }

    private SocketChannel channel;

    TcpSocketClientTransport() {

    }

    @Override
    public TcpSocketClientContext connect(InetSocketAddress endpoint, TcpSocketClientListener listener) throws IOException, InterruptedException {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        if(isConnected()) {
            throw new IllegalStateException("Socket is already connected");
        }

        this.channel = SocketChannel.open();
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        channel.configureBlocking(false);
        var ctx = new TcpSocketClientContext(listener);
        if (channel.connect(endpoint)) {
            TcpSocketClientSelector.INSTANCE.register(channel, SelectionKey.OP_READ, ctx);
        } else {
            TcpSocketClientSelector.INSTANCE.register(channel, SelectionKey.OP_CONNECT, ctx);
            synchronized (ctx.connectionLock) {
                var deadline = System.currentTimeMillis() + DEFAULT_CONNECT_TIMEOUT;
                while (!channel.isConnected() && channel.isOpen()) {
                    var remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    ctx.connectionLock.wait(remaining);
                }
            }
            if (!channel.isConnected()) {
                TcpSocketClientSelector.INSTANCE.unregister(channel);
                throw new IOException("Connection timed out");
            }
        }

        ctx.connected.set(true);

        return ctx;
    }

    @Override
    public void disconnect() throws IOException {
        if(!isConnected()) {
            throw new IOException("Socket is not connected");
        }

        TcpSocketClientSelector.INSTANCE.unregister(channel);
    }

    @Override
    public void sendBinary(ByteBuffer... buffers) {
        if (!isConnected()) {
            throw new IllegalStateException("Socket is not connected");
        }

        if (!TcpSocketClientSelector.INSTANCE.addWrite(channel, buffers)) {
            throw new IllegalStateException("Failed to send binary");
        }
    }

    @Override
    public boolean isConnected() {
        return TcpSocketClientSelector.INSTANCE.isConnected(channel);
    }

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("Socket is not connected");
        }

        var read = new SocketPendingRead(buffer, fully);
        if(!TcpSocketClientSelector.INSTANCE.addRead(channel, read)) {
            throw new IllegalStateException("Failed to read binary");
        }

        synchronized (read.lock) {
            try {
                var deadline = System.currentTimeMillis() + DEFAULT_READ_TIMEOUT;
                while (read.length == -1 && isConnected()) {
                    var remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    read.lock.wait(remaining);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for read", exception);
            }
        }

        if(read.length == -1) {
            if (isConnected()) {
                TcpSocketClientSelector.INSTANCE.unregister(channel);
                throw new IOException("Read timed out");
            } else {
                throw new IOException("Unexpected end of stream");
            }
        }

        return read.length;
    }

    public boolean finishConnect() {
        if(!isConnected()) {
            return false;
        }

        return TcpSocketClientSelector.INSTANCE.markReady(channel);
    }

    public void startTlsHandshake(long tlsHandshakeTimeout) throws IOException {
        TcpSocketClientSelector.INSTANCE.startTlsHandshake(channel, tlsHandshakeTimeout);
    }

    public boolean preSeedDatagram(ByteBuffer responseBuf) {
        return TcpSocketClientSelector.INSTANCE.preSeedDatagram(channel, responseBuf);
    }

    public boolean drainSslAppBuffer() {
        return TcpSocketClientSelector.INSTANCE.drainSslAppBuffer(channel);
    }
}
