package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageBroadcastAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles marketing message broadcast actions.
 *
 * <p>This handler processes mutations that associate sent message IDs with
 * premium/marketing messages.
 *
 * <p>Index format: ["marketingMessageBroadcast", premiumMessageId, messageId]
 */
public final class MarketingMessageBroadcastHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code MarketingMessageBroadcastHandler}.
     */
    public static final MarketingMessageBroadcastHandler INSTANCE = new MarketingMessageBroadcastHandler();

    private MarketingMessageBroadcastHandler() {

    }

    @Override
    public String actionName() {
        return MarketingMessageBroadcastAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return MarketingMessageBroadcastAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return MarketingMessageBroadcastAction.ACTION_VERSION;
    }

    /**
     * Applies a marketing message broadcast mutation.
     *
     * <p>Per WhatsApp Web (WAWebPremiumMessageBroadcastSync), on SET the web client
     * looks up the premium message by premiumMessageId (index[1]) in the
     * PremiumMessageCollection. If found, it associates the sent messageId (index[2])
     * with it. If the premium message is not found, the mutation is orphaned. Non-SET
     * operations are unsupported (acknowledged). Both premiumMessageId and messageId
     * must be present in the index or the mutation is malformed.
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was acknowledged, {@code false} if orphaned
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var premiumMessageId = indexArray.getString(1);
        var messageId = indexArray.getString(2);
        if (premiumMessageId == null || premiumMessageId.isEmpty()
                || messageId == null || messageId.isEmpty()) {
            return true;
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        return false;
    }
}
