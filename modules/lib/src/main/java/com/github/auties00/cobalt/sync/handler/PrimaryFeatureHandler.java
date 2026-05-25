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
 * Applies the {@code primary_feature} app-state action that distributes the
 * primary device's advertised feature flag set across linked devices.
 *
 * <p>Every paired device learns the union of feature strings the primary
 * advertises so companion devices can light up or hide UI affordances
 * accordingly. Within a batch only the latest mutation by timestamp is
 * persisted; per-mutation results report the per-entry outcome. The mutation
 * index is the singleton {@snippet :
 *     ["primary_feature"]
 * }
 *
 * <p>An empty {@link PrimaryFeatureAction#flags()} list is treated as success
 * (the only malformed-value branch is a missing action payload).
 *
 * @implNote
 * This implementation overrides
 * {@link #applyMutationBatch(WhatsAppClient, List)} to implement the
 * latest-wins semantics inside a single store write; the single-mutation
 * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)} adapter
 * persists the same mutation immediately for callers that dispatch outside the
 * batch path. WA Web's {@code WARN} batch counters are dropped.
 */
@WhatsAppWebModule(moduleName = "WAWebPrimaryFeatureSync")
public final class PrimaryFeatureHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton primary-feature sync handler.
     *
     * @implNote
     * This implementation is stateless; no AB-prop or store dependency is
     * held.
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
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebPrimaryFeatureSync.applyMutations}: it walks the batch and
     * for each mutation appends an
     * {@link MutationApplicationResult#unsupported()} (non-{@code SET}), a
     * {@link SyncdIndexUtils#malformedActionValue(String)} (wrong action
     * type), or a {@link MutationApplicationResult#success()} entry; in
     * parallel it tracks the latest valid mutation by timestamp. After the
     * walk, the latest mutation's flags are persisted via
     * {@code WhatsAppStore.setPrimaryFeatures}; an empty flags list is accepted
     * as success (only a missing action payload triggers malformed).
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
     * @implNote
     * This implementation collapses WA Web's batch loop to the single-mutation
     * case: a non-{@code SET} mutation surfaces as
     * {@link MutationApplicationResult#unsupported()}, a wrong action type as
     * {@link SyncdIndexUtils#malformedActionValue(String)}, and a valid
     * mutation persists its {@link PrimaryFeatureAction#flags()} via
     * {@code WhatsAppStore.setPrimaryFeatures} and returns
     * {@link MutationApplicationResult#success()}. An empty flags list is
     * accepted as success.
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
