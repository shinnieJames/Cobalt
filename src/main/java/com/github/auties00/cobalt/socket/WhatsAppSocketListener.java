package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.node.Node;

/**
 * A listener for events produced by the {@link WhatsAppSocketClient}.
 *
 * <p>Implementations receive notifications when complete deserialized
 * nodes arrive, when errors occur, and when the connection is closed.
 */
public interface WhatsAppSocketListener {
    /**
     * Called when a complete WhatsApp node has been received and decrypted.
     *
     * @param node the deserialized node
     */
    void onNode(Node node);

    /**
     * Called when an error occurs during processing.
     *
     * @param exception the error
     */
    void onError(WhatsAppException exception);

    /**
     * Called when the connection has been closed.
     */
    void onClose();
}
