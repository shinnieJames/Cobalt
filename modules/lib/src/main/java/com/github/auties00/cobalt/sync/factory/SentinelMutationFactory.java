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
 * <p>This factory drives the key-rotation flow which seeds one sentinel mutation per app-state
 * collection so that subsequent mutations encrypted under the freshly rotated sync key have a
 * predecessor MAC chain anchored on the new key epoch. Mutations produced here are consumed on
 * receiving devices by {@link com.github.auties00.cobalt.sync.handler.SentinelHandler}, which
 * expires the matching key epoch in a transaction.
 *
 * @implNote
 * WA Web iterates over its collection-name enum and emits one mutation per collection name; Cobalt
 * iterates over {@link SyncPatchType#values()}, which lists the same set of names. Each mutation's
 * index follows the standard {@code [actionName, collectionName]} shape and carries the newest key
 * pair's epoch as the {@code keyExpiration.expiredKeyEpoch} field.
 */
public final class SentinelMutationFactory {
    /**
     * Logs the sentinel mutation preparation progress.
     *
     * @implNote
     * This logger emits the same tag strings WA Web uses ({@code preparing mutations...} and the
     * no-key-pair warning) for parity with the source's syncd sentinel tag.
     */
    private static final Logger LOGGER = Logger.getLogger(SentinelMutationFactory.class.getName());

    /**
     * Constructs a sentinel mutation factory.
     *
     * <p>The factory keeps no state, so a single instance is sufficient per client.
     */
    public SentinelMutationFactory() {

    }

    /**
     * Creates sentinel pending mutations for every sync collection type.
     *
     * <p>The returned list contains one mutation per {@link SyncPatchType}; receiving devices
     * expire the matching key epoch and mark the collection as ready for the next sync cycle. The
     * list is {@link Collections#emptyList()} when no sync key pairs are available, matching WA
     * Web's no-key-pair early return.
     *
     * @implNote
     * This implementation reads the newest sync-key pair via
     * {@link SyncKeyUtils#findNewestKey(java.util.Collection)} and its epoch via
     * {@link SyncKeyUtils#getKeyEpoch(byte[])}, then emits one {@code SET} mutation per
     * {@link SyncPatchType}. The {@code SyncActionValue} (and therefore the inner
     * {@code keyExpiration.expiredKeyEpoch}) is shared across every mutation because the epoch is
     * per-account, not per-collection.
     *
     * @param client the WhatsApp client whose store is consulted for the app-state-keys map; the
     *               newest key pair becomes the epoch source
     * @return a list of pending mutations, one per {@link SyncPatchType}, or
     *         {@link Collections#emptyList()} if no sync key pairs exist
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
            mutations.add(new SyncPendingMutation(mutation, 0));
        }
        return mutations;
    }
}
