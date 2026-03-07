package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.PrimaryFeatureAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles primary feature actions.
 *
 * <p>This handler processes mutations that communicate primary device feature flags.
 * Only SET operations are supported; other operations are acknowledged as unsupported.
 * When processing a batch, only the mutation with the highest timestamp is applied.
 *
 * <p>Index format: ["primary_feature"]
 */
public final class PrimaryFeatureHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code PrimaryFeatureHandler}.
     */
    public static final PrimaryFeatureHandler INSTANCE = new PrimaryFeatureHandler();

    private PrimaryFeatureHandler() {

    }

    @Override
    public String actionName() {
        return PrimaryFeatureAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return PrimaryFeatureAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return PrimaryFeatureAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof PrimaryFeatureAction action)) {
            return true;
        }

        if (action.flags().isEmpty()) {
            return true;
        }

        client.store().setPrimaryFeatures(action.flags());
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Per WhatsApp Web {@code WAWebPrimaryFeatureSync.applyMutations}: iterates
     * all mutations to find the one with the highest timestamp, then applies
     * only that mutation. Non-SET and malformed mutations are acknowledged
     * but do not participate in the timestamp comparison.
     */
    @Override
    public List<Boolean> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        DecryptedMutation.Trusted latest = null;
        var results = new ArrayList<Boolean>(mutations.size());
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                // Web: Unsupported
                results.add(true);
                continue;
            }

            var action = mutation.value().action().orElse(null);
            if (!(action instanceof PrimaryFeatureAction pfa) || pfa.flags().isEmpty()) {
                // Web: malformedActionValue
                results.add(true);
                continue;
            }

            if (latest == null || mutation.timestamp().compareTo(latest.timestamp()) > 0) {
                latest = mutation;
            }
            results.add(true);
        }

        if (latest != null) {
            applyMutation(client, latest);
        }

        return results;
    }
}
