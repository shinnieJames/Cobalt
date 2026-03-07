package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles marketing message actions.
 *
 * <p>This handler processes mutations related to premium/marketing message templates.
 *
 * <p>Index format: ["marketingMessage", messageId]
 */
public final class MarketingMessageHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code MarketingMessageHandler}.
     */
    public static final MarketingMessageHandler INSTANCE = new MarketingMessageHandler();

    private MarketingMessageHandler() {

    }

    @Override
    public String actionName() {
        return MarketingMessageAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return MarketingMessageAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return MarketingMessageAction.ACTION_VERSION;
    }

    /**
     * Applies a marketing message mutation.
     *
     * <p>Per WhatsApp Web (WAWebPremiumMessageSync), on SET the web client validates
     * that the action value (marketingMessageAction) is present and that {@code type}
     * is non-null, then stores the marketing message into the PremiumMessageCollection.
     * Non-SET operations are unsupported (acknowledged). The messageId from index[1]
     * must be present or the mutation is malformed.
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was acknowledged, {@code false} otherwise
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var messageId = indexArray.getString(1);
        if (messageId == null || messageId.isEmpty()) {
            return true;
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof MarketingMessageAction action)) {
            return true;
        }

        if (action.type().isEmpty()) {
            return true;
        }

        return true;
    }
}
