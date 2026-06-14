package com.github.auties00.cobalt.listener;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.message.MessageInfo;

/**
 * A functional interface for the {@code onNewMessage} event.
 *
 * <p>The event is emitted by every {@link WhatsAppClient} flavour: the Linked client raises it for
 * each message decrypted from the socket, and the Cloud client raises it for each inbound message
 * decoded from a webhook delivery whose change field is {@code messages}. The flavour aggregators
 * extend this interface and supply an empty default implementation, so the event can also be
 * observed in isolation as a lambda.
 */
@FunctionalInterface
public non-sealed interface NewMessageListener extends WhatsAppListener {
    /**
     * Notifies the listener that a new message has been received.
     *
     * @param whatsapp the client emitting the event
     * @param info     the message that was received
     */
    void onNewMessage(WhatsAppClient whatsapp, MessageInfo info);
}
