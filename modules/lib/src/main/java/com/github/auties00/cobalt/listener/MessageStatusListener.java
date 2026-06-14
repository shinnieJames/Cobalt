package com.github.auties00.cobalt.listener;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageStatus;

/**
 * A functional interface for the {@code onMessageStatus} event.
 *
 * <p>The event is emitted when an outbound message transitions through its delivery lifecycle: the
 * Linked client raises it for each receipt covering the message, and the Cloud client raises it for
 * each {@code statuses[]} entry of a webhook delivery. The new status is read from
 * {@link MessageInfo#status()}. The flavour aggregators extend this interface and supply an empty
 * default implementation, so the event can also be observed in isolation as a lambda.
 *
 * @see MessageStatus
 */
@FunctionalInterface
public non-sealed interface MessageStatusListener extends WhatsAppListener {
    /**
     * Notifies the listener that a message's status has changed (sent, delivered, read).
     *
     * @param whatsapp the client emitting the event
     * @param info     the message whose status changed
     */
    void onMessageStatus(WhatsAppClient whatsapp, MessageInfo info);
}
