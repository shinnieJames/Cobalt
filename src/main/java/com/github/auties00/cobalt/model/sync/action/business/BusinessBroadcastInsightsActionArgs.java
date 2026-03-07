package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link BusinessBroadcastInsightsAction}.
 *
 * <p>The sync index produced is {@code ["business_broadcast_insights_sync", campaignId]}.
 *
 * @param campaignId the unique identifier of the business broadcast campaign
 */
public record BusinessBroadcastInsightsActionArgs(String campaignId) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{campaignId};
    }
}
