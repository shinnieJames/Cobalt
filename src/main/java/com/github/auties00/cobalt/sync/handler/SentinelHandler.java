package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * Handles sentinel actions for sync key expiration.
 *
 * <p>This handler processes mutations that signal sync key expiration by
 * reading the expired key epoch from the mutation value. Only SET operations
 * are supported; other operations are acknowledged as unsupported.
 *
 * <p>Per WhatsApp Web {@code WAWebSentinelMutationSync.applyMutations}:
 * on receiving a SET operation with a {@code keyExpiration} value, calls
 * {@code expireSyncKeyInTransaction(expiredKeyEpoch)} to mark that key
 * epoch as expired in the sync key store.
 *
 * <p>Index format: ["sentinel", collectionName]
 */
public final class SentinelHandler implements WebAppStateActionHandler {
    private static final Logger LOGGER = Logger.getLogger(SentinelHandler.class.getName());

    /**
     * The singleton instance of {@code SentinelHandler}.
     */
    public static final SentinelHandler INSTANCE = new SentinelHandler();

    private SentinelHandler() {

    }

    @Override
    public String actionName() {
        return KeyExpirationAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return KeyExpirationAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return KeyExpirationAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof KeyExpirationAction action)) {
            return true;
        }

        var expiredEpoch = action.expiredKeyEpoch();
        if (expiredEpoch.isEmpty()) {
            return true;
        }

        // Find the latest timestamp among keys with the expired epoch
        // and expire all keys at or before that timestamp
        var epoch = expiredEpoch.getAsInt();
        Instant latestTimestamp = null;
        for (var key : client.store().appStateKeys()) {
            if (SyncKeyUtils.getKeyEpoch(key) != epoch) {
                continue;
            }

            var timestamp = key.keyData()
                    .flatMap(AppStateSyncKeyData::timestamp)
                    .orElse(null);
            if (timestamp != null && (latestTimestamp == null || timestamp.isAfter(latestTimestamp))) {
                latestTimestamp = timestamp;
            }
        }

        if (latestTimestamp != null) {
            LOGGER.info("Expiring sync keys at or before " + latestTimestamp + " (epoch " + epoch + ")");
            client.store().expireAppStateKeys(latestTimestamp);
        }

        return true;
    }
}
