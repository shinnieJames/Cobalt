package com.github.auties00.cobalt.listener.linked;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;

/**
 * A functional interface for the {@link LinkedWhatsAppClientListener#onPrivacySettingChanged onPrivacySettingChanged} event.
 *
 * <p>{@link LinkedWhatsAppClientListener} extends this interface and supplies an empty
 * default implementation, so the event can also be observed in isolation as a
 * lambda.
 *
 * @see LinkedWhatsAppClientListener
 */
@FunctionalInterface
public non-sealed interface LinkedPrivacySettingChangedListener extends LinkedListener {
    /**
     * Notifies the listener that a privacy setting has been changed.
     *
     * @param whatsapp        the client emitting the event
     * @param newPrivacyEntry the new privacy setting
     */
    void onPrivacySettingChanged(LinkedWhatsAppClient whatsapp, PrivacySettingEntry newPrivacyEntry);
}
