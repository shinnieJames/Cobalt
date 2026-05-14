package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationAction;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds outgoing sentinel sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.SentinelHandler}.
 */
public final class SentinelMutationFactory {
    /**
     * Logger for sentinel mutation creation diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(SentinelMutationFactory.class.getName());

    /**
     * Constructs a sentinel mutation factory.
     */
    public SentinelMutationFactory() {

    }

    /**
     * Creates sentinel pending mutations for all sync collection types.
     *
     * <p>Per WhatsApp Web {@code WAWebSentinelMutationSync.getSentinelMutations}:
     * retrieves the newest sync key pair, extracts its key epoch, and creates
     * one pending mutation per collection name. Each mutation is a SET operation
     * with the sentinel action, the handler's version, the current timestamp,
     * and a value containing {@code keyExpiration.expiredKeyEpoch} set to the
     * active key's epoch.
     *
     * <p>The index for each mutation is {@code ["sentinel", collectionName]},
     * matching the WA Web pattern of {@code buildPendingMutation} with
     * {@code action = getAction()} and {@code indexArgs = [collectionName]}.
     *
     * <p>This is called by the sentinel scheduling logic (equivalent to
     * {@code WAWebSentinel.default}) before marking all collections for sync.
     *
     * @param client the WhatsApp client instance for accessing the store
     * @return a list of pending mutations, one per collection type, or an empty
     *         list if no sync key pairs exist
     */
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "getSentinelMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public List<SyncPendingMutation> getSentinelMutations(WhatsAppClient client) {
        LOGGER.fine("preparing mutations...");

        var timestamp = Instant.now();
        var collections = SyncPatchType.values();
        var newestKey = SyncKeyUtils.findNewestKey(client.store().appStateKeys());
        if (newestKey == null) {
            LOGGER.warning("sentinel mutation sync: no key pairs");
            return Collections.emptyList();
        }

        var keyEpoch = SyncKeyUtils.getKeyEpoch(newestKey);
        var keyExpirationAction = new KeyExpirationActionBuilder()
                .expiredKeyEpoch(keyEpoch)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .keyExpirationAction(keyExpirationAction)
                .build();

        var mutations = new ArrayList<SyncPendingMutation>(collections.length);
        for (var collection : collections) {
            var index = JSON.toJSONString(List.of(KeyExpirationAction.ACTION_NAME, collection.toString()));
            var mutation = new DecryptedMutation.Trusted(
                    index,
                    value,
                    SyncdOperation.SET,
                    timestamp,
                    KeyExpirationAction.ACTION_VERSION
            );
            mutations.add(new SyncPendingMutation(mutation, 0)); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation returns raw; WAWebSentinel bulk-creates via bulkCreateSyncPendingMutationsInTransaction
        }
        return mutations;
    }
}
