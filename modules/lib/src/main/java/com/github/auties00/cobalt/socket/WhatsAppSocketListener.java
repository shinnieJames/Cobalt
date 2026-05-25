package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.node.Node;

/**
 * Receives the post-handshake events that a {@link WhatsAppSocketClient}
 * surfaces over its decrypted byte stream.
 *
 * <p>Implementations are the application-level glue between the transport
 * layer and the protocol layer above it (typically a node router and a
 * stanza dispatcher). The listener is bound for the lifetime of a single
 * {@link WhatsAppSocketClient#connect(WhatsAppSocketListener)} call; after
 * {@link #onClose()} fires no further callbacks are delivered and a fresh
 * listener (or the same one, reset) must be supplied on the next
 * {@code connect}.
 */
public interface WhatsAppSocketListener {
    /**
     * Receives one decrypted and decoded inbound {@link Node}.
     *
     * <p>This is invoked once per inbound {@code int24}-framed AES-GCM
     * datagram. The call runs on the socket reader virtual thread and must
     * not block on I/O of the same {@link WhatsAppSocketClient}: doing so
     * would deadlock the reader and stall every subsequent inbound node on
     * the same socket.
     *
     * @implSpec
     * Implementations must accept any well-formed {@link Node};
     * application-level routing or filtering happens above this layer.
     *
     * @param node the deserialized inbound node
     */
    void onNode(Node node);

    /**
     * Receives a {@link WhatsAppException} observed while processing
     * inbound traffic or while interacting with the underlying
     * transport.
     *
     * <p>Errors surface here rather than being thrown at the application
     * thread because the reader runs asynchronously on a virtual thread. The
     * exception type discriminates the recovery action: see
     * {@link com.github.auties00.cobalt.exception.WhatsAppSessionException.BadMac}
     * for fatal stream corruption,
     * {@link com.github.auties00.cobalt.exception.WhatsAppConnectionException}
     * for transport-level drops, and
     * {@link com.github.auties00.cobalt.exception.WhatsAppStreamException.MalformedNode}
     * for protocol-level parse failures.
     *
     * @implSpec
     * Implementations must not throw out of this callback; the reader
     * loop has no general-purpose recovery path for callback failures.
     *
     * @param exception the observed error
     */
    void onError(WhatsAppException exception);

    /**
     * Receives notification that the underlying connection has closed
     * and the reader loop has terminated.
     *
     * <p>This fires exactly once per
     * {@link WhatsAppSocketClient#connect(WhatsAppSocketListener)} regardless
     * of whether the close was orderly (caller invoked
     * {@link WhatsAppSocketClient#disconnect()}) or remote-initiated (server
     * dropped the connection or the reader hit a fatal error). No further
     * {@link #onNode(Node)} or {@link #onError(WhatsAppException)} callbacks
     * are delivered after this call returns.
     */
    void onClose();
}
