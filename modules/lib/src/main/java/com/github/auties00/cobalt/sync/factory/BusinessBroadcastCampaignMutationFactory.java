package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.action.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.SyncActionValue;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing app-state mutations that create, update, or delete a Business Broadcast campaign.
 *
 * <p>SMB users schedule a campaign through the Web UI, and the resulting
 * mutations are pushed via
 * {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches} so the
 * primary device and other linked devices populate the broadcast-campaign
 * collection consistently. This factory builds the outgoing mutations; the
 * inbound counterpart is
 * {@link com.github.auties00.cobalt.sync.handler.BusinessBroadcastCampaignHandler}.
 *
 * @implNote
 * This implementation does not gate on
 * {@code WAWebBizGatingUtils.isBizBroadcastSendWebEnabledNoExposure()}; WA Web
 * only consults that flag on the receive side, so the outgoing factory remains
 * callable from any embedder regardless of the SMB feature gate.
 */
public final class BusinessBroadcastCampaignMutationFactory {
    /**
     * Creates a stateless factory with no collaborators.
     *
     * <p>A single instance may be shared across the lifetime of the client.
     */
    public BusinessBroadcastCampaignMutationFactory() {

    }

    /**
     * Returns a SET mutation that creates or updates a Business Broadcast campaign.
     *
     * <p>Call this when the user schedules a new campaign or edits an existing
     * one; the mutation index follows
     * {@snippet :
     *     ["businessBroadcastCampaign", campaignId]
     * }
     * and the {@link BusinessBroadcastCampaignAction} sub-message carries the
     * campaign descriptor (broadcast JID, device id, status, timestamps,
     * reserved quota). The action payload is passed in already built, so callers
     * control all required fields ({@code broadcastJid}, {@code deviceId},
     * {@code status}) that the receive-side handler validates.
     *
     * @implNote
     * This implementation emits a {@link SyncdOperation#SET} pinned to
     * {@link BusinessBroadcastCampaignAction#ACTION_VERSION}.
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
     * <p>Call this when the user cancels a scheduled campaign or deletes an
     * already-completed one; the mutation index follows
     * {@snippet :
     *     ["businessBroadcastCampaign", campaignId]
     * }
     * with an empty value, which the receive-side handler uses to look up the
     * existing campaign row before removing it.
     *
     * @implNote
     * This implementation emits a {@link SyncdOperation#REMOVE} with an empty
     * {@link SyncActionValue}; only the
     * timestamp and index travel on the wire.
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
