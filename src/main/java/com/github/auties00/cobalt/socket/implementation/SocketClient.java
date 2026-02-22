package com.github.auties00.cobalt.socket.implementation;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.implementation.context.SocketContext;
import com.github.auties00.cobalt.socket.implementation.context.SocketPendingRead;
import com.github.auties00.cobalt.socket.implementation.threading.CentralSelector;
import com.github.auties00.cobalt.socket.implementation.tunnel.DirectSocketClient;
import com.github.auties00.cobalt.socket.implementation.tunnel.HttpProxySocketClient;
import com.github.auties00.cobalt.socket.implementation.tunnel.SocksProxySocketClient;
import com.github.auties00.cobalt.socket.implementation.websocket.WebSocketSocketClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public abstract sealed class SocketClient permits DirectSocketClient, HttpProxySocketClient, SocksProxySocketClient, WebSocketSocketClient {
    private static final int DEFAULT_READ_TIMEOUT = 10_000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 30_000;

    protected SocketChannel channel;

    public static SocketClient newPlainSocketClient(WhatsAppClientProxy proxy) {
        if(proxy == null) {
            return new DirectSocketClient();
        } else {
            return switch (proxy) {
                case WhatsAppClientProxy.Http httpProxy -> new HttpProxySocketClient(httpProxy);
                case WhatsAppClientProxy.Socks socksProxy -> new SocksProxySocketClient(socksProxy);
            };
        }
    }

    public abstract void connect(String host, int port, SocketListener listener) throws IOException, InterruptedException;

    public void disconnect() throws IOException {
        if(!isConnected()) {
            throw new IOException("Socket is not connected");
        }

        CentralSelector.INSTANCE.unregister(channel);
    }

    public void sendBinary(ByteBuffer buffer) {
        if (!isConnected()) {
            throw new IllegalStateException("Socket is not connected");
        }

        if(!CentralSelector.INSTANCE.addWrite(channel, buffer)) {
            throw new IllegalStateException("Failed to send binary");
        }
    }

    public boolean isConnected() {
        return CentralSelector.INSTANCE.isConnected(channel);
    }

    protected SocketContext openConnection(InetSocketAddress endpoint, SocketListener listener) throws IOException, InterruptedException {
        if(isConnected()) {
            throw new IOException("Socket is already connected");
        }

        this.channel = SocketChannel.open();
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        channel.configureBlocking(false);
        var ctx = new SocketContext(listener);
        if (channel.connect(endpoint)) {
            CentralSelector.INSTANCE.register(channel, SelectionKey.OP_READ, ctx);
        } else {
            CentralSelector.INSTANCE.register(channel, SelectionKey.OP_CONNECT, ctx);
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
                CentralSelector.INSTANCE.unregister(channel);
                throw new IOException("Connection timed out");
            }
        }
        ctx.connected.set(true);
        return ctx;
    }

    protected int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("Socket is not connected");
        }

        var read = new SocketPendingRead(buffer, fully);
        if(!CentralSelector.INSTANCE.addRead(channel, read)) {
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
                CentralSelector.INSTANCE.unregister(channel);
                throw new IOException("Read timed out");
            } else {
                throw new IOException("Unexpected end of stream");
            }
        }

        return read.length;
    }
}
