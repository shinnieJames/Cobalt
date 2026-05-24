package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.MarketingMessageBroadcastBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageBroadcastAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code marketingMessageBroadcast} app-state sync action that
 * tags an outgoing message as belonging to a premium message template.
 *
 * @apiNote
 * Drives the SMB premium-message tracking surface: when a marketing
 * template is broadcast, each recipient send is tagged with the
 * template's {@code premiumMessageId} and the resulting association
 * fans out across the {@link SyncPatchType#REGULAR} collection so
 * companion devices can attribute the send to the template. The
 * mutation index encodes both ids, formatted as
 * {@snippet :
 *     ["marketingMessageBroadcast", premiumMessageId, messageId]
 * }
 *
 * @implNote
 * This implementation persists each association eagerly through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#putMarketingMessageBroadcast}
 * keyed by the sent message id, with the premium template id stored
 * as the record's status field. WA Web batches the pairs into an
 * {@code n} array and calls
 * {@code WAWebPremiumMessageAddSendAction(n)} once at the end of the
 * batch to mutate the {@code sentMessageIds} {@link java.util.Set} on
 * each premium template; Cobalt's
 * {@link MarketingMessageBroadcastAction} protobuf does not carry
 * {@code sentMessageIds}, so a side map is used instead. Per
 * Cobalt's pluggable error model, exceptions propagate to the
 * orchestrator instead of being mapped to
 * {@link MutationApplicationResult#failed()} inline.
 */
@WhatsAppWebModule(moduleName = "WAWebPremiumMessageBroadcastSync")
public final class MarketingMessageBroadcastHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link MarketingMessageBroadcastHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageBroadcastSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MarketingMessageBroadcastHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageBroadcastSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return MarketingMessageBroadcastAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageBroadcastSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return MarketingMessageBroadcastAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageBroadcastSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return MarketingMessageBroadcastAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation classifies a missing index slot as
     * {@link MutationApplicationResult#malformed()} explicitly so the
     * orchestrator does not silently surface
     * {@link MutationApplicationResult#failed()} from an
     * out-of-bounds {@code JSON.parseArray}. When the referenced
     * premium template is unknown locally the mutation is reported as
     * {@link MutationApplicationResult#orphan()}, mirroring WA Web's
     * {@code PremiumMessageCollection.find(i) == null} branch. The
     * association is stored eagerly because Cobalt's underlying
     * storage is a flat key/value map and there is no per-entity
     * {@link java.util.Set} to mutate.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageBroadcastSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 2) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var premiumMessageId = indexArray.getString(1);
        var messageId = indexArray.getString(2);
        if (premiumMessageId == null || premiumMessageId.isEmpty()
                || messageId == null || messageId.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (client.store().findMarketingMessage(premiumMessageId).isEmpty()) {
            return MutationApplicationResult.orphan();
        }

        client.store().putMarketingMessageBroadcast(new MarketingMessageBroadcastBuilder()
                .templateId(messageId)
                .status(premiumMessageId)
                .build());
        return MutationApplicationResult.success();
    }
}
