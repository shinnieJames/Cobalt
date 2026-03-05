package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles push name setting changes.
 *
 * <p>This handler processes mutations that update the user's display name (push name).
 */
public final class PushNameSettingHandler implements WebAppStateActionHandler {
    public static final PushNameSettingHandler INSTANCE = new PushNameSettingHandler();

    private PushNameSettingHandler() {

    }

    @Override
    public String actionName() {
        return "pushName";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.CRITICAL_BLOCK;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var setting = mutation.value()
                .pushNameSetting()
                .orElseThrow(() -> new IllegalArgumentException("Missing pushNameSetting"));

        var name = setting.name().orElse(null);

        client.store()
                .setName(name);

        client.store()
                .jid()
                .flatMap(entry -> client.store().findContactByJid(entry.withoutData()))
                .ifPresent(contact -> contact.setChosenName(name));

        return true;
    }
}
