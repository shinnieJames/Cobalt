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
 * Maintains the business-broadcast-campaign roster from {@code business_broadcast_campaign} sync mutations.
 *
 * @apiNote
 * Drives the Business Manager broadcast-campaign surface (a campaign
 * groups a marketing message, a target broadcast list, an ad
 * association, and a scheduled or pending status). When the user
 * creates, edits, or deletes a campaign on another device, the server
 * replays the change here as a SET (upsert) or REMOVE; Cobalt
 * embedders observe the result through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findBusinessBroadcastCampaign(String)}.
 *
 * @implNote
 * This implementation drops two WA Web side effects: the
 * {@code isBizBroadcastSendWebEnabledNoExposure()} AB-prop gate that
 * short-circuits the entire batch (Cobalt does not gate on AB-prop
 * exposure here) and the post-batch
 * {@code refreshBroadcastCampaignState} fire-and-forget event (Cobalt
 * has no browser frontend bridge). The malformed-mutation count is
 * still logged from {@link #applyMutationBatch(WhatsAppClient, List)}
 * to match WA Web's per-batch warning.
 */
@WhatsAppWebModule(moduleName = "WAWebBroadcastCampaignSync")
public final class BusinessBroadcastCampaignHandler implements WebAppStateActionHandler {
    /**
     * The handler-scoped {@link Logger} used to emit the per-batch malformed-mutation summary.
     *
     * @apiNote
     * Records the line equivalent to WA Web's
     * {@code broadcast campaign sync: <n> malformed mutations} after
     * each batch.
     */
    private static final Logger LOGGER = Logger.getLogger(BusinessBroadcastCampaignHandler.class.getName());

    /**
     * Constructs the singleton broadcast-campaign handler.
     *
     * @apiNote
     * Instantiated once by the sync handler registry. Embedders do not
     * normally construct this directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessBroadcastCampaignHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return BusinessBroadcastCampaignAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return BusinessBroadcastCampaignAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return BusinessBroadcastCampaignAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * For SET mutations, validates that the
     * {@link BusinessBroadcastCampaignAction} value carries a
     * {@code broadcastJid}, {@code deviceId}, and {@code status}, then
     * upserts the campaign keyed by {@code campaignId} from
     * {@code indexParts[1]}. For REMOVE mutations, drops the campaign
     * by id. Returns
     * {@link SyncdIndexUtils#malformedActionIndex(String, String)} when
     * the index slot is empty,
     * {@link SyncdIndexUtils#malformedActionValue(String)} when the
     * required fields are missing, and
     * {@link MutationApplicationResult#failed()} for unknown operations
     * or any thrown exception.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index());
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
                        .build());
                return MutationApplicationResult.success();
            }

            if (mutation.operation() == SyncdOperation.REMOVE) {
                client.store().removeBusinessBroadcastCampaign(campaignId);
                return MutationApplicationResult.success();
            }

            return MutationApplicationResult.failed();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Iterates the batch, applying each mutation via
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * and aggregating a malformed-mutation count for the warning log.
     *
     * @implNote
     * This implementation omits WA Web's
     * {@code isBizBroadcastSendWebEnabledNoExposure()} short-circuit
     * (Cobalt does not gate on AB-prop exposure here) and the
     * post-batch {@code refreshBroadcastCampaignState} fire-and-forget
     * event because Cobalt has no browser frontend bridge.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        var malformedCount = 0;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            var result = applyMutation(client, mutation);
            if (result.actionState() == SyncActionState.MALFORMED) {
                malformedCount++;
            }
            results.add(result);
        }
        if (malformedCount > 0) {
            LOGGER.warning("broadcast campaign sync: " + malformedCount + " malformed mutations");
        }
        return results;
    }

}
