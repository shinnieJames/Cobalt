package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles time format actions.
 *
 * <p>This handler processes mutations that change the time format preference (12/24 hour).
 */
public final class TimeFormatHandler implements WebAppStateActionHandler {
    public static final TimeFormatHandler INSTANCE = new TimeFormatHandler();

    private TimeFormatHandler() {

    }

    @Override
    public String actionName() {
        return "timeFormatAction";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var action = mutation.value()
                .timeFormatAction()
                .orElseThrow(() -> new IllegalArgumentException("Missing timeFormatAction"));

        client.store()
                .setTwentyFourHourFormat(action.isTwentyFourHourFormatEnabled());

        return true;
    }
}
