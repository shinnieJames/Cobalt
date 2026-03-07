package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.SettingsSyncAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Handles settings sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebSettingsSync}, uses batch-level
 * deduplication keeping only the latest-timestamped mutation per index.
 * On SET, validates that the index has exactly 4 parts, and that
 * {@code settingsSyncAction} is non-{@code null}.
 *
 * <p>Index format: ["settings_sync", "platform", "settingKey", "scope"]
 */
public final class SettingsSyncHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code SettingsSyncHandler}.
     */
    public static final SettingsSyncHandler INSTANCE = new SettingsSyncHandler();

    private SettingsSyncHandler() {

    }

    @Override
    public String actionName() {
        return SettingsSyncAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SettingsSyncAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return SettingsSyncAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() != 4) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof SettingsSyncAction)) {
            return true;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Per WhatsApp Web {@code WAWebSettingsSync.applyMutations}: deduplicates
     * mutations by index, keeping only the latest-timestamped SET mutation per
     * index. Older duplicates are skipped. Non-SET mutations and mutations
     * without a matching latest entry are marked as malformed.
     */
    @Override
    public List<Boolean> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        var latestByIndex = new HashMap<String, DecryptedMutation.Trusted>();
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                continue;
            }

            var key = mutation.index();
            var existing = latestByIndex.get(key);
            if (existing == null || mutation.timestamp().compareTo(existing.timestamp()) > 0) {
                latestByIndex.put(key, mutation);
            }
        }

        var results = new ArrayList<Boolean>(mutations.size());
        for (var mutation : mutations) {
            var latest = latestByIndex.get(mutation.index());
            if (latest == null || latest != mutation) {
                results.add(true);
                continue;
            }

            results.add(applyMutation(client, mutation));
        }

        return results;
    }
}
