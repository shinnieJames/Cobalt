package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles chat lock settings actions.
 *
 * <p>This handler processes mutations related to the global chat lock settings
 * (e.g., whether chat locking is enabled, the secret code hash).
 *
 * <p>Index format: ["setting_chatLock"]
 */
public final class ChatLockSettingsHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code ChatLockSettingsHandler}.
     */
    public static final ChatLockSettingsHandler INSTANCE = new ChatLockSettingsHandler();

    private ChatLockSettingsHandler() {

    }

    @Override
    public String actionName() {
        return ChatLockSettings.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return ChatLockSettings.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return ChatLockSettings.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().chatLockSettings().orElse(null) instanceof ChatLockSettings settings)) {
            return false;
        }

        client.store().setChatLockSettings(settings);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Per WhatsApp Web {@code WAWebChatLockSettingsSync.applyMutations}: iterates
     * all mutations, accumulating the last valid {@code chatLockSettings} value from
     * SET operations. After iteration, persists the accumulated value once.
     */
    @Override
    public List<Boolean> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        if (mutations.isEmpty()) {
            return List.of();
        }

        ChatLockSettings lastValid = null;
        var results = new ArrayList<Boolean>(mutations.size());
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                results.add(true);
                continue;
            }

            if (mutation.value().chatLockSettings().orElse(null) instanceof ChatLockSettings settings) {
                lastValid = settings;
                results.add(true);
            } else {
                results.add(true);
            }
        }

        if (lastValid != null) {
            client.store().setChatLockSettings(lastValid);
        }

        return results;
    }
}
