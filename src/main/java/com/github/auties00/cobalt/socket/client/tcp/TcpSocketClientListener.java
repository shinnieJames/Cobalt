package com.github.auties00.cobalt.socket.client.tcp;

import com.github.auties00.cobalt.socket.client.SocketClientListener;

public interface TcpSocketClientListener extends SocketClientListener {
    void onClose();
}
