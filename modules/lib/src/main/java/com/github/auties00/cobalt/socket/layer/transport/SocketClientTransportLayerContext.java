package com.github.auties00.cobalt.socket.layer.transport;

import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientPendingWrites;

/**
 * Transport-level per-connection state and the bottommost layer in the
 * processing chain.
 *
 * <p>This context holds the connection lifecycle state, the outbound
 * write queue, and extends {@link SocketClientLayerContext} so the
 * selector reads raw channel bytes into its inbound buffer and
 * propagates data up through the layer chain.
 */
public non-sealed interface SocketClientTransportLayerContext extends SocketClientLayerContext {

    /**
     * Returns whether the connection is currently active.
     *
     * @return {@code true} if connected
     */
    boolean isConnected();

    /**
     * Sets the connection state.
     *
     * @param value {@code true} to mark as connected, {@code false} to
     *              mark as disconnected
     */
    void setConnected(boolean value);

    /**
     * Atomically sets the connection state if it currently has the expected
     * value.
     *
     * @param expected the expected current value
     * @param newValue the new value
     * @return {@code true} if the compare-and-set succeeded
     */
    boolean compareAndSetConnected(boolean expected, boolean newValue);

    /**
     * Returns the monitor used to block the connecting thread until the
     * selector completes the non-blocking connect operation.
     *
     * @return the connection lock, never {@code null}
     */
    Object connectionLock();

    /**
     * Returns the lock-free MPSC queue of outbound buffers waiting to be
     * written to the channel.
     *
     * @return the pending writes queue, never {@code null}
     */
    SocketClientPendingWrites pendingWrites();
}
