package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.PrimaryFeatureAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles primary device feature flag actions.
 *
 * <p>This handler processes mutations that communicate the set of primary
 * device feature flags. Only {@code SET} operations are supported; other
 * operations are acknowledged with an {@code UNSUPPORTED} state. When
 * processing a batch, only the {@link PrimaryFeatureAction} mutation with the
 * highest timestamp is actually applied to the store, mirroring WhatsApp Web's
 * "latest wins" semantics for this collection.
 *
 * <p>Index format: {@code ["primary_feature"]}
 */
@WhatsAppWebModule(moduleName = "WAWebPrimaryFeatureSync")
public final class PrimaryFeatureHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton instance.
     */
    @WhatsAppWebExport(moduleName = "WAWebPrimaryFeatureSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public PrimaryFeatureHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryFeatureSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PrimaryFeatureAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryFeatureSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PrimaryFeatureAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryFeatureSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PrimaryFeatureAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Per WhatsApp Web {@code WAWebPrimaryFeatureSync.applyMutations}: maps
     * each mutation to a per-mutation {@link MutationApplicationResult}, while
     * tracking the mutation with the highest timestamp among the valid {@code SET}
     * mutations. After the mapping, the flags from the latest valid mutation are
     * persisted via {@link com.github.auties00.cobalt.store.WhatsAppStore#setPrimaryFeatures(List)}.
     *
     * <p>Non-{@code SET} mutations are acknowledged as {@code UNSUPPORTED} and
     * mutations whose decoded value is not a {@link PrimaryFeatureAction} are
     * acknowledged as {@code MALFORMED}; neither participates in the timestamp
     * comparison. WhatsApp Web also accepts an empty {@code flags} list as a
     * valid value (its only check is {@code flags == null}), so an empty list
     * is treated as {@code SUCCESS} here as well.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryFeatureSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        DecryptedMutation.Trusted latest = null;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                results.add(MutationApplicationResult.unsupported());
                continue;
            }

            var action = mutation.value().action().orElse(null);
            if (!(action instanceof PrimaryFeatureAction)) {
                results.add(SyncdIndexUtils.malformedActionValue(collectionName().name()));
                continue;
            }

            if (latest == null || mutation.timestamp().compareTo(latest.timestamp()) > 0) {
                latest = mutation;
            }
            results.add(MutationApplicationResult.success());
        }
        if (latest != null) {
            var pfa = (PrimaryFeatureAction) latest.value().action().orElseThrow();
            client.store().setPrimaryFeatures(pfa.flags());
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Single-mutation adapter that mirrors the WhatsApp Web batch logic for
     * a list of size one: a non-{@code SET} mutation yields {@code UNSUPPORTED};
     * a mutation whose decoded value is not a {@link PrimaryFeatureAction}
     * yields {@code MALFORMED}; otherwise the flags are persisted to the store
     * and {@code SUCCESS} is returned. As in WhatsApp Web, an empty {@code flags}
     * list is considered valid.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryFeatureSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PrimaryFeatureAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().setPrimaryFeatures(action.flags());
        return MutationApplicationResult.success();
    }
}
