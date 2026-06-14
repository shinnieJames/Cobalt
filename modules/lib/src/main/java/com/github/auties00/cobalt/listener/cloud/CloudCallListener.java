package com.github.auties00.cobalt.listener.cloud;

import com.github.auties00.cobalt.client.cloud.CloudWhatsAppClientListener;

import com.github.auties00.cobalt.client.cloud.CloudWhatsAppClient;
import com.github.auties00.cobalt.model.cloud.CloudCallEvent;

/**
 * A functional interface for the {@link CloudWhatsAppClientListener#onCall onCall} event.
 *
 * <p>{@link CloudWhatsAppClientListener} extends this interface and supplies an empty default
 * implementation, so the event can also be observed in isolation as a lambda. The event is raised for
 * each Calling API event delivered through the {@code calls} webhook change field (an inbound SDP
 * offer, a connect, or a terminate).
 *
 * @see CloudWhatsAppClientListener
 */
@FunctionalInterface
public non-sealed interface CloudCallListener extends CloudListener {
    /**
     * Notifies the listener of a Calling API event.
     *
     * @param whatsapp the client emitting the event
     * @param event    the call event
     */
    void onCall(CloudWhatsAppClient whatsapp, CloudCallEvent event);
}
