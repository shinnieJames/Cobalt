package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BusinessBroadcastCampaignBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles business broadcast campaign sync actions.
 *
 * <p>This handler processes mutations for business broadcast campaigns,
 * supporting both SET (upsert) and REMOVE operations. On SET, the handler
 * validates that the action value contains non-null {@code broadcastJid},
 * {@code deviceId}, and {@code status} fields before persisting.
 *
 * <p>Index format: {@code ["business_broadcast_campaign", campaignId]}
 */
@WhatsAppWebModule(moduleName = "WAWebBroadcastCampaignSync")
public final class BusinessBroadcastCampaignHandler implements WebAppStateActionHandler {
    /**
     * Logger for broadcast campaign sync operations.
     */
    private static final Logger LOGGER = Logger.getLogger(BusinessBroadcastCampaignHandler.class.getName());

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessBroadcastCampaignHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return BusinessBroadcastCampaignAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return BusinessBroadcastCampaignAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return BusinessBroadcastCampaignAction.ACTION_VERSION;
    }

    /**
     * Applies a business broadcast campaign mutation.
     *
     * <p>Per WhatsApp Web ({@code WAWebBroadcastCampaignSync.applyMutations}),
     * on SET the handler validates that the action value is present and that
     * {@code broadcastJid}, {@code deviceId}, and {@code status} are all
     * non-null. If any are missing, the mutation is classified as malformed
     * via {@code WAWebSyncdIndexUtils.malformedActionValue}. On REMOVE, the
     * campaign is removed from storage. The {@code campaignId} from
     * {@code indexParts[1]} must be present or the mutation is classified as
     * malformed via {@code malformedActionIndex}.
     *
     * <p>Per WhatsApp Web, each individual mutation is wrapped in a try/catch;
     * any unhandled error yields {@code SyncActionState.Failed}.
    /**
     * Applies a business broadcast campaign mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web ({@code WAWebBroadcastCampaignSync.applyMutations}),
     * the per-mutation logic:
     * <ol>
     *   <li>Extracts {@code campaignId} from {@code indexParts[1]}; returns
     *       {@code malformedActionIndex()} if missing</li>
     *   <li>For SET operations: validates the {@code businessBroadcastCampaignAction}
     *       value has non-null {@code broadcastJid}, {@code deviceId}, and {@code status};
     *       returns {@code malformedActionValue(collectionName)} if invalid; otherwise
     *       calls {@code upsertCampaignStorage(campaignId, action, timestamp)}</li>
     *   <li>For REMOVE operations: calls
     *       {@code removeCampaignStorage(campaignId)}</li>
     *   <li>Wraps the entire logic in try/catch, returning {@code Failed} on error</li>
     * </ol>
     * @param client   the WhatsApp client
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index()); // ADAPTED: WAWebBroadcastCampaignSync uses e.indexParts (pre-parsed); Cobalt parses from JSON string
            // WAWebBroadcastCampaignSync.applyMutations: var t=e.indexParts, n=t[1]; if(!n) return r.malformedActionIndex().
            // The slot-missing case must yield MALFORMED, not FAILED via the outer catch.
            if (indexArray.size() <= 1) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var campaignId = indexArray.getString(1);
            if (campaignId == null || campaignId.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (mutation.operation() == SyncdOperation.SET) {
                if (!(mutation.value().action().orElse(null) instanceof BusinessBroadcastCampaignAction action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                if (action.broadcastJid().isEmpty() || action.deviceId().isEmpty() || action.status().isEmpty()) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                client.store().putBusinessBroadcastCampaign(new BusinessBroadcastCampaignBuilder()
                        .id(campaignId)
                        .deviceId(action.deviceId().isPresent() ? action.deviceId().getAsInt() : null)
                        .adId(action.adId().orElse(null))
                        .name(action.name().orElse(null))
                        .marketingMessageId(action.msgId().orElse(null))
                        .broadcastJid(action.broadcastJid().map(Jid::of).orElse(null))
                        .reservedQuota(action.reservedQuota().isPresent() ? action.reservedQuota().getAsInt() : null)
                        .scheduledAt(action.scheduledTimestamp().isPresent() ? Instant.ofEpochMilli(action.scheduledTimestamp().getAsLong()) : null)
                        .createdAt(action.createTimestamp().isPresent() ? Instant.ofEpochMilli(action.createTimestamp().getAsLong()) : null)
                        .status(action.status().orElse(null))
                        .build()); // ADAPTED: WAWebBroadcastCampaignSync.applyMutations: yield o("WAWebBizBroadcastCampaignStorageUtils").upsertCampaignStorage(n, c, u)
                return MutationApplicationResult.success();
            }

            if (mutation.operation() == SyncdOperation.REMOVE) {
                client.store().removeBusinessBroadcastCampaign(campaignId); // ADAPTED: WAWebBroadcastCampaignSync.applyMutations: yield o("WAWebBizBroadcastCampaignStorageUtils").removeCampaignStorage(n)
                return MutationApplicationResult.success();
            }

            return MutationApplicationResult.failed();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Applies a batch of business broadcast campaign mutations.
     *
     * <p>Per WhatsApp Web ({@code WAWebBroadcastCampaignSync.applyMutations}), the batch
     * handler first checks the {@code isBizBroadcastSendWebEnabledNoExposure()} gating flag.
     * If the feature is not enabled, all mutations are returned as {@code Unsupported}.
     * Otherwise, each mutation is processed individually and a malformed count is logged
     * after the batch. Additionally, any affected {@code broadcastJid}s are collected and
     * a {@code refreshBroadcastCampaignState} event is fired.
     *
     * <p>In Cobalt, the AB prop gating check and the frontend fire-and-forget call are
     * intentionally omitted (AB props and frontend API are not replicated). The malformed
     * count warning is preserved via logging.
     * @param client    the WhatsAppClient instance linked to the mutations
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        // ADAPTED: WAWebBroadcastCampaignSync.applyMutations checks isBizBroadcastSendWebEnabledNoExposure()
        // and returns all Unsupported if false — Cobalt omits AB prop gating
        var malformedCount = 0;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) { // ADAPTED: WAWebBroadcastCampaignSync.applyMutations uses Promise.all(t.map(...))
            var result = applyMutation(client, mutation);
            if (result.actionState() == SyncActionState.MALFORMED) {
                malformedCount++;
            }
            results.add(result);
        }
        if (malformedCount > 0) {
            LOGGER.warning("broadcast campaign sync: " + malformedCount + " malformed mutations");
        }
        // ADAPTED: WAWebBroadcastCampaignSync.applyMutations: i.size > 0 && o("WAWebBackendApi").frontendFireAndForget("refreshBroadcastCampaignState", ...)
        // — frontend UI refresh omitted, no Cobalt equivalent
        return results;
    }

}
