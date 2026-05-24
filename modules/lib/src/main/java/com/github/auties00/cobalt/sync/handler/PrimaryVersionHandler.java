package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.PrimaryVersionAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the {@code primaryVersion} app-state action that distributes
 * the primary device's WhatsApp version string across linked devices.
 *
 * @apiNote
 * Drives the multi-device version-tracking surface: the primary
 * device announces its current build and its session-start build to
 * the paired companions so each companion can render version-skew
 * warnings when needed. The handler validates the per-mutation
 * shape but writes nothing to the store: WA Web emits only
 * {@code WALogger.WARN} telemetry for invalid entries, and Cobalt
 * has no consumer for the version data either. The mutation index
 * keys each entry by the checkpoint name, formatted as
 * {@snippet :
 *     ["primaryVersion", "current"]
 *     ["primaryVersion", "session_start"]
 * }
 *
 * @implNote
 * This implementation overrides
 * {@link #applyMutationBatch(WhatsAppClient, List)} to
 * mirror WA Web's batch loop shape, but the per-mutation arm
 * delegates to
 * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
 * because there is no batch-level state to track. WA Web's
 * {@code WALogger.WARN} batch counters and the missing-version /
 * unknown-sub-index trace messages are dropped; per-mutation
 * outcomes are surfaced through {@link MutationApplicationResult}.
 */
@WhatsAppWebModule(moduleName = "WAWebPrimaryVersionSync")
public final class PrimaryVersionHandler implements WebAppStateActionHandler {
    /**
     * The {@code "current"} sub-index value identifying the primary
     * device's current-build version checkpoint.
     *
     * @apiNote
     * Internal constant consumed by
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * to validate {@code indexParts[1]}.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code WAWebPrimaryVersionSync.CURRENT = "current"} literal.
     */
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static final String INDEX_CURRENT = "current";

    /**
     * The {@code "session_start"} sub-index value identifying the
     * primary device's session-start version checkpoint.
     *
     * @apiNote
     * Internal constant consumed by
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * to validate {@code indexParts[1]}.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code WAWebPrimaryVersionSync.SESSION_START = "session_start"}
     * literal.
     */
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static final String INDEX_SESSION_START = "session_start";

    /**
     * Constructs the singleton primary-version sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; no AB-prop or store dependency
     * is held.
     */
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public PrimaryVersionHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PrimaryVersionAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PrimaryVersionAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PrimaryVersionAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * for every entry; there is no batch-level deduplication or
     * latest-wins tracking. WA Web's per-batch
     * {@code WALogger.WARN} counters for the unsupported and
     * malformed-value paths are dropped.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            results.add(applyMutation(client, mutation));
        }
        return results;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the per-mutation arms of WA Web's
     * {@code WAWebPrimaryVersionSync.applyMutations} in this order:
     * <ol>
     *   <li>only {@link SyncdOperation#SET} is accepted;</li>
     *   <li>{@code indexParts[1]} must equal exactly one of
     *       {@link #INDEX_CURRENT} or {@link #INDEX_SESSION_START};
     *       any missing, empty, or unknown value surfaces as
     *       {@link SyncdIndexUtils#malformedActionIndex(String, String)};</li>
     *   <li>the mutation value must decode to a
     *       {@link PrimaryVersionAction} with a non-empty
     *       {@link PrimaryVersionAction#version()};</li>
     *   <li>otherwise, returns {@link MutationApplicationResult#success()}.</li>
     * </ol>
     * No store side effect is performed.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var subIndex = indexArray.getString(1);
        if (subIndex == null || subIndex.isEmpty() || (!subIndex.equals(INDEX_CURRENT) && !subIndex.equals(INDEX_SESSION_START))) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        if (!(mutation.value().action().orElse(null) instanceof PrimaryVersionAction action) || action.version().isEmpty()) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        return MutationApplicationResult.success();
    }
}
