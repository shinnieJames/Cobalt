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
 * Builds outgoing business-broadcast-campaign sync mutations.
 *
 * <p>Mirrors the {@code getCampaignMutation} and
 * {@code getDeleteCampaignMutation} exports of WhatsApp Web's
 * {@code WAWebBroadcastCampaignSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.BusinessBroadcastCampaignHandler}.
 */
public final class BusinessBroadcastCampaignMutationFactory {
    /**
     * Constructs a business-broadcast-campaign mutation factory.
     */
    public BusinessBroadcastCampaignMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for creating or updating a business broadcast campaign.
     *
     * <p>Per WhatsApp Web ({@code WAWebBroadcastCampaignSync.getCampaignMutation}),
     * this method wraps the supplied {@link BusinessBroadcastCampaignAction} into a
     * {@code SyncActionValue} whose payload field is
     * {@code businessBroadcastCampaignAction}, then delegates to
     * {@code WAWebSyncdActionUtils.buildPendingMutation} with {@code action =
     * getAction()}, {@code indexArgs = [campaignId]}, {@code collection =
     * Regular}, {@code version = 1}, and {@code operation = SET}.
     *
     * @param campaignId the business broadcast campaign identifier (index arg)
     * @param action     the campaign action payload
     * @param timestamp  the mutation timestamp
     * @return a pending mutation ready for outbound sync
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
     * Builds a pending REMOVE mutation for deleting a business broadcast campaign.
     *
     * <p>Per WhatsApp Web ({@code WAWebBroadcastCampaignSync.getDeleteCampaignMutation}),
     * this method delegates to {@code WAWebSyncdActionUtils.buildPendingMutation}
     * with an empty value, {@code indexArgs = [campaignId]}, and
     * {@code operation = REMOVE}.
     *
     * @param campaignId the business broadcast campaign identifier to remove
     * @param timestamp  the mutation timestamp
     * @return a pending mutation ready for outbound sync
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
