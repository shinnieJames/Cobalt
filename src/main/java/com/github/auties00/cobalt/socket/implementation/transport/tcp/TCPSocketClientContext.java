package com.github.auties00.cobalt.socket.implementation.transport.tcp;

import com.github.auties00.cobalt.socket.implementation.SocketClientListener;
import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketClientContext;

import java.nio.ByteBuffer;

public final class TCPSocketClientContext extends AbstractSocketClientContext {
    private static final int INT24_BYTE_SIZE = 3;

    /**
     * Buffer for reading the 3-byte length prefix of the current inbound
     * WhatsApp datagram.
     *
     * <p> Used only during the post-tunnel phase.  Accessed exclusively
     * by the selector thread.  Cleared and reused after each complete
     * datagram delivery.
     */
    public final ByteBuffer datagramLengthBuffer;

    /**
     * Buffer for accumulating the payload of the current inbound WhatsApp
     * datagram, or {@code null} if the length prefix has not yet been
     * fully read.
     *
     * <p> Allocated by the selector thread once the 3-byte length prefix
     * is complete, sized to the decoded length.  Set back to {@code null}
     * after the completed datagram is handed off to {@link #listener}.
     * Accessed exclusively by the selector thread.
     */
    public ByteBuffer datagramBuffer;

    /**
     * Callback invoked when a complete inbound datagram has been
     * reassembled.
     *
     * <p> The selector thread dispatches each datagram to the listener
     * on a virtual thread to avoid blocking the selection loop.
     */
    public final SocketClientListener listener;

    /**
     * Creates a context for a new connection.
     *
     * @param listener  the callback to receive completed inbound datagrams
     */
    public TCPSocketClientContext(SocketClientListener listener) {
        this.listener = listener;
        this.datagramLengthBuffer = ByteBuffer.allocate(INT24_BYTE_SIZE);
    }
}
