package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BusinessBroadcastInsightBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastInsightsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles business broadcast insights sync actions.
 *
 * <p>Per WhatsApp Web, this handler processes mutations for business
 * broadcast campaign delivery statistics (recipient, delivered, read,
 * replied, and quick reply counts). It is synced via the {@code REGULAR}
 * collection. The handler supports both SET (upsert) and REMOVE operations,
 * and tracks per-batch counters for SET, REMOVE, and malformed mutations.
 *
 * <p>Index format: {@code ["business_broadcast_insights_sync", campaignId]}
 */
@WhatsAppWebModule(moduleName = "WAWebBusinessBroadcastInsightsSync")
public final class BusinessBroadcastInsightsHandler implements WebAppStateActionHandler {
    /**
     * Logger for broadcast insights sync operations.
     */
    private static final Logger LOGGER = Logger.getLogger(BusinessBroadcastInsightsHandler.class.getName());

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebBusinessBroadcastInsightsSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessBroadcastInsightsHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessBroadcastInsightsSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return BusinessBroadcastInsightsAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessBroadcastInsightsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return BusinessBroadcastInsightsAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessBroadcastInsightsSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return BusinessBroadcastInsightsAction.ACTION_VERSION;
    }

    /**
     * Applies a business broadcast insights mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web ({@code WAWebBusinessBroadcastInsightsSync.applyMutations}),
     * the per-mutation logic:
     * <ol>
     *   <li>Extracts {@code campaignId} from {@code indexParts[1]}; returns
     *       {@code malformedActionIndex()} if missing</li>
     *   <li>For SET operations: extracts the {@code businessBroadcastInsightsAction}
     *       value; returns {@code malformedActionValue(collectionName)} if absent;
     *       otherwise calls {@code upsertInsightsStorage(campaignId, action, timestamp)}</li>
     *   <li>For REMOVE operations: calls
     *       {@code removeInsightsStorage(campaignId)}</li>
     *   <li>For unrecognised operations: throws an error (caught by outer try/catch
     *       returning {@code Failed})</li>
     *   <li>Wraps the entire logic in try/catch, returning {@code Failed} on error</li>
     * </ol>
     * @param client   the WhatsApp client
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessBroadcastInsightsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index()); // ADAPTED: WAWebBusinessBroadcastInsightsSync uses e.indexParts (pre-parsed); Cobalt parses from JSON string
            // WAWebBusinessBroadcastInsightsSync.applyMutations: var t=e.indexParts, n=t[1]; if(!n) return r.malformedActionIndex().
            // The slot-missing case must yield MALFORMED, not FAILED via the outer catch.
            if (indexArray.size() <= 1) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var campaignId = indexArray.getString(1);
            if (campaignId == null || campaignId.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (mutation.operation() == SyncdOperation.SET) {
                if (!(mutation.value().action().orElse(null) instanceof BusinessBroadcastInsightsAction action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                client.store().putBusinessBroadcastInsight(new BusinessBroadcastInsightBuilder()
                        .id(campaignId)
                        .recipientCount(action.recipientCount().isPresent() ? action.recipientCount().getAsInt() : null)
                        .deliveredCount(action.deliveredCount().isPresent() ? action.deliveredCount().getAsInt() : null)
                        .readCount(action.readCount().isPresent() ? action.readCount().getAsInt() : null)
                        .repliedCount(action.repliedCount().isPresent() ? action.repliedCount().getAsInt() : null)
                        .quickReplyCount(action.quickReplyCount().isPresent() ? action.quickReplyCount().getAsInt() : null)
                        .build()); // ADAPTED: WAWebBusinessBroadcastInsightsSync.applyMutations: yield o("WAWebBizBroadcastInsightsStorageUtils").upsertInsightsStorage(n, {...}, c)
                return MutationApplicationResult.success();
            }

            if (mutation.operation() == SyncdOperation.REMOVE) {
                client.store().removeBusinessBroadcastInsight(campaignId); // ADAPTED: WAWebBusinessBroadcastInsightsSync.applyMutations: yield o("WAWebBizBroadcastInsightsStorageUtils").removeInsightsStorage(n)
                return MutationApplicationResult.success();
            }

            return MutationApplicationResult.failed();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Applies a batch of business broadcast insights mutations.
     *
     * <p>Per WhatsApp Web ({@code WAWebBusinessBroadcastInsightsSync.applyMutations}), the batch
     * handler first checks the {@code isBizBroadcastSendWebEnabledNoExposure()} gating flag.
     * If the feature is not enabled, all mutations are returned as {@code Unsupported}.
     * Otherwise, each mutation is processed individually and counters are maintained for
     * SET operations, REMOVE operations, and malformed mutations. After the batch, warnings
     * are logged for each non-zero counter, and a {@code refreshBroadcastCampaignState}
     * event is fired if any SET or REMOVE operations succeeded.
     *
     * <p>In Cobalt, the AB prop gating check and the frontend fire-and-forget call are
     * intentionally omitted (AB props and frontend API are not replicated). The malformed,
     * SET, and REMOVE count warnings are preserved via logging.
     * @param client    the WhatsAppClient instance linked to the mutations
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessBroadcastInsightsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        // ADAPTED: WAWebBusinessBroadcastInsightsSync.applyMutations checks isBizBroadcastSendWebEnabledNoExposure()
        // and returns all Unsupported if false — Cobalt omits AB prop gating
        var malformedCount = 0;
        var setCount = 0;
        var removeCount = 0;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) { // ADAPTED: WAWebBusinessBroadcastInsightsSync.applyMutations uses Promise.all(t.map(...))
            var result = applyMutation(client, mutation);
            // when `p` (businessBroadcastInsightsAction) is missing — i.e. malformedActionValue path.
            // The malformedActionIndex path (missing indexParts[1]) returns without bumping `a`.
            if (result.actionState() == SyncActionState.MALFORMED && mutation.operation() == SyncdOperation.SET) {
                malformedCount++;
            }
            if (result.actionState() == SyncActionState.SUCCESS && mutation.operation() == SyncdOperation.SET) {
                setCount++;
            }
            if (result.actionState() == SyncActionState.SUCCESS && mutation.operation() == SyncdOperation.REMOVE) {
                removeCount++;
            }
            results.add(result);
        }
        if (setCount > 0) {
            LOGGER.warning("BBI SyncD received " + setCount + " SET operations");
        }
        if (removeCount > 0) {
            LOGGER.warning("BBI SyncD received " + removeCount + " REMOVE operations");
        }
        if (malformedCount > 0) {
            LOGGER.warning("BBI sync: " + malformedCount + " malformed mutations");
        }
        // ADAPTED: WAWebBusinessBroadcastInsightsSync.applyMutations: (i > 0 || d > 0) && o("WAWebBackendApi").frontendFireAndForget("refreshBroadcastCampaignState", {broadcastJids: []})
        // — frontend UI refresh omitted, no Cobalt equivalent
        return results;
    }
}
