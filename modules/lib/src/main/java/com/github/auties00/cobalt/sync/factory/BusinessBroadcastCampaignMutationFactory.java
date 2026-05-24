package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing app-state mutations that create, update, or delete a Business Broadcast campaign.
 *
 * @apiNote
 * Drives the WhatsApp Business broadcast-campaign feature: SMB users
 * schedule a campaign through the Web UI, and the resulting mutations are
 * pushed via {@link com.github.auties00.cobalt.sync.WebAppStateService} so
 * the primary device and other linked devices populate
 * {@code WAWebSchemaBusinessBroadcastCampaign} consistently. The factory is
 * the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.BusinessBroadcastCampaignHandler}.
 *
 * @implNote
 * This implementation does not gate on
 * {@code WAWebBizGatingUtils.isBizBroadcastSendWebEnabledNoExposure()}; WA
 * Web only consults that flag on the receive side
 * ({@code applyMutations}), so the outgoing factory remains callable from
 * any embedder regardless of the SMB feature gate.
 */
public final class BusinessBroadcastCampaignMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across the
     * lifetime of the client.
     */
    public BusinessBroadcastCampaignMutationFactory() {

    }

    /**
     * Returns a SET mutation that creates or updates a Business Broadcast campaign.
     *
     * @apiNote
     * Call this when the user schedules a new campaign or edits an existing
     * one; the mutation index follows
     * {@snippet :
     *     ["businessBroadcastCampaign", campaignId]
     * }
     * and the {@link BusinessBroadcastCampaignAction} sub-message carries
     * the campaign descriptor (broadcast JID, device id, status, timestamps,
     * reserved quota).
     *
     * @implNote
     * This implementation mirrors
     * {@code WAWebBroadcastCampaignSync.getCampaignMutation}; the action
     * payload is passed in already-built, so callers control all required
     * fields ({@code broadcastJid}, {@code deviceId}, {@code status}) that
     * the receive-side handler validates before calling
     * {@code WAWebBizBroadcastCampaignStorageUtils.upsertCampaignStorage}.
     *
     * @param campaignId the campaign identifier used as the mutation index
     * @param action     the pre-built campaign descriptor payload
     * @param timestamp  the mutation timestamp
     * @return the pending mutation ready to be queued for outbound app-state sync
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "getCampaignMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getCampaignMutation(
            String campaignId,
            BusinessBroadcastCampaignAction action,
            Instant timestamp
    ) {
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .businessBroadcastCampaignAction(action)
                .build();
        var index = JSON.toJSONString(List.of(BusinessBroadcastCampaignAction.ACTION_NAME, campaignId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                BusinessBroadcastCampaignAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }

    /**
     * Returns a REMOVE mutation that deletes a Business Broadcast campaign.
     *
     * @apiNote
     * Call this when the user cancels a scheduled campaign or deletes an
     * already-completed one; the mutation index follows
     * {@snippet :
     *     ["businessBroadcastCampaign", campaignId]
     * }
     * with an empty value, which the receive-side handler uses to look up
     * the existing campaign row (so it can capture the
     * {@code broadcastJid} for the post-removal
     * {@code refreshBroadcastCampaignState} fan-out) before calling
     * {@code WAWebBizBroadcastCampaignStorageUtils.removeCampaignStorage}.
     *
     * @implNote
     * This implementation mirrors
     * {@code WAWebBroadcastCampaignSync.getDeleteCampaignMutation} and emits
     * a {@link SyncdOperation#REMOVE} with an empty
     * {@link com.github.auties00.cobalt.model.sync.SyncActionValue}; only
     * the timestamp and index travel on the wire.
     *
     * @param campaignId the campaign identifier to delete
     * @param timestamp  the mutation timestamp
     * @return the pending mutation ready to be queued for outbound app-state sync
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastCampaignSync", exports = "getDeleteCampaignMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getDeleteCampaignMutation(String campaignId, Instant timestamp) {
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .build();
        var index = JSON.toJSONString(List.of(BusinessBroadcastCampaignAction.ACTION_NAME, campaignId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.REMOVE,
                timestamp,
                BusinessBroadcastCampaignAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
