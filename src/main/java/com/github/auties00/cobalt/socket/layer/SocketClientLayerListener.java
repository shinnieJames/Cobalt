package com.github.auties00.cobalt.socket.layer;

import java.nio.ByteBuffer;

/**
 * A listener for events produced by the socket layer stack.
 *
 * <p>Implementations receive notifications when complete datagrams arrive
 * and when the connection is closed.  Datagram delivery is serialized
 * through a single-threaded virtual executor, so implementations do not
 * need to synchronize between consecutive {@link #onDatagram(ByteBuffer)}
 * calls.  The {@link #onClose()} callback may be invoked from any thread.
 */
public interface SocketClientLayerListener {
    /**
     * Called when a complete WhatsApp datagram has been assembled from the
     * inbound stream.
     *
     * <p>The buffer is in read mode (flipped) and contains exactly the
     * payload bytes described by the preceding 3-byte length prefix.
     * The buffer is not reused by the layer stack after this call, so
     * the listener may retain or consume it freely.
     *
     * @param datagram the complete datagram payload, in read mode
     */
    void onDatagram(ByteBuffer datagram);

    /**
     * Called when the connection has been closed.
     *
     * <p>After this method returns, no further events will be delivered
     * for this connection.
     */
    void onClose();
}
