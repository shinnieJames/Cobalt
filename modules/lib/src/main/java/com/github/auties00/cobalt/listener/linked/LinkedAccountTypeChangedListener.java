package com.github.auties00.cobalt.listener.linked;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;

/**
 * A functional interface for the {@link LinkedWhatsAppClientListener#onAccountTypeChanged onAccountTypeChanged} event.
 *
 * <p>{@link LinkedWhatsAppClientListener} extends this interface and supplies an empty
 * default implementation, so the event can also be observed in isolation as a
 * lambda.
 *
 * @see LinkedWhatsAppClientListener
 */
@FunctionalInterface
public non-sealed interface LinkedAccountTypeChangedListener extends LinkedListener {
    /**
     * Notifies the listener that a contact's account type has changed
     * between {@code E2EE} and {@code HOSTED}.
     *
     * <p>This indicates a transition in the contact's encryption
     * configuration.
     *
     * @param whatsapp the client emitting the event
     * @param userJid  the user whose account type changed
     * @param oldType  the previous account type
     * @param newType  the new account type
     */
    void onAccountTypeChanged(LinkedWhatsAppClient whatsapp, Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType);
}
