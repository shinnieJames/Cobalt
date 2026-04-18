package com.github.auties00.cobalt.socket.layer.transport;

import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;

/**
 * Marker interface for the transport-level layer context — the head of
 * the layer chain and the only one that directly reads from the NIO
 * socket channel.
 *
 * <p>Per-connection state (the outbound write queue, the connection lock,
 * the {@code connected} flag) lives on the selector's {@code AttachmentData},
 * not here.  This interface only identifies the transport position in the
 * chain.
 */
public interface SocketClientTransportLayerContext extends SocketClientLayerContext {
}
