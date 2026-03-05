package com.github.auties00.cobalt.socket.layer.security;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;

import java.io.IOException;

/**
 * A security layer that provides encryption over an existing connection.
 *
 * <p>Currently only TLS is supported as a security layer.  Noise
 * encryption is handled by the standalone {@code WhatsAppSocketClient}
 * rather than as a layer in the stack.
 */
public sealed interface SocketClientSecurityLayer extends SocketClientLayer
        permits SocketClientTransportSecurityLayer, SocketClientTunnelSecurityLayer {
    /**
     * Starts the security handshake and blocks until it completes.
     *
     * @throws IOException if the handshake fails
     */
    void startHandshake() throws IOException;
}
