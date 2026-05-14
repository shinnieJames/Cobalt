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
 * Handles primary version actions.
 *
 * <p>This handler processes mutations that track the WhatsApp primary client
 * version string for either the {@code "current"} or {@code "session_start"}
 * checkpoint. WhatsApp Web's implementation simply validates each mutation in
 * the batch, increments local counters for unsupported and malformed entries,
 * and emits {@code WALogger.WARN} traces. There is no application side effect
 * beyond the per-mutation result classification, so Cobalt mirrors the same
 * validation pipeline without persisting any value.
 *
 * <p>Index format: {@code ["primaryVersion", "current"|"session_start"]}.
 */
@WhatsAppWebModule(moduleName = "WAWebPrimaryVersionSync")
public final class PrimaryVersionHandler implements WebAppStateActionHandler {
    /**
     * Sub-index value identifying the {@code "current"} primary version
     * checkpoint inside the index parts array.
     */
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static final String INDEX_CURRENT = "current";

    /**
     * Sub-index value identifying the {@code "session_start"} primary version
     * checkpoint inside the index parts array.
     */
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static final String INDEX_SESSION_START = "session_start";

    /**
     * Constructs the singleton instance.
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
     * <p>Per WhatsApp Web {@code WAWebPrimaryVersionSync.applyMutations}: maps
     * each mutation in the batch to its per-mutation classification. The
     * mapping logic mirrors WA Web exactly:
     * <ul>
     *   <li>Non-{@code SET} operations are acknowledged as {@code UNSUPPORTED}.</li>
     *   <li>If {@code indexParts[1]} is missing/empty or is not one of
     *       {@code "current"} or {@code "session_start"}, the mutation is
     *       classified as {@code MALFORMED} via
     *       {@link #malformedActionIndex()}.</li>
     *   <li>If {@code value.primaryVersionAction.version} is missing, the
     *       mutation is classified as {@code MALFORMED} via
     *       {@link #malformedActionValue()}.</li>
     *   <li>Otherwise, the mutation is classified as {@code SUCCESS}.</li>
     * </ul>
     *
     * <p>WhatsApp Web also tracks {@code WALogger.WARN} counters for the
     * unsupported and malformed-value paths, which Cobalt intentionally
     * omits as logging/telemetry is not replicated.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        // r and a are local counters for the WALogger WARN telemetry, intentionally omitted in Cobalt
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            results.add(applyMutation(client, mutation));
        }
        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Single-mutation adapter that mirrors the per-mutation classification
     * inside WhatsApp Web's batch entry point exactly. The validation order
     * follows WA Web:
     * <ol>
     *   <li>If {@code operation !== "set"}, return {@code UNSUPPORTED}.</li>
     *   <li>Parse the index, take {@code indexParts[1]}; if it is missing,
     *       empty, or not one of {@code "current"} or {@code "session_start"},
     *       return {@link #malformedActionIndex()}.</li>
     *   <li>If {@code value.primaryVersionAction.version} is missing, return
     *       {@link #malformedActionValue()}.</li>
     *   <li>Otherwise, return {@code SUCCESS}.</li>
     * </ol>
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPrimaryVersionSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        // WAWebPrimaryVersionSync.applyMutations: var i=e.indexParts, s=i[1]; if(!s||...) return n.malformedActionIndex().
        // indexParts[1] is undefined when missing; mirror with explicit size check.
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
