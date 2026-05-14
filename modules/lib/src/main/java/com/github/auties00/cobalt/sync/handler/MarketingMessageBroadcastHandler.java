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
 * Handles marketing message broadcast actions.
 *
 * <p>This handler processes mutations that associate sent message ids with
 * premium/marketing messages. Per WhatsApp Web {@code WAWebPremiumMessageBroadcastSync},
 * each successful mutation marks the {@code messageId} (the id of an actual
 * message that was sent) as belonging to the premium message template
 * identified by {@code premiumMessageId}.
 *
 * <p>Index format: {@code ["marketingMessageBroadcast", premiumMessageId, messageId]}
 *
 * <p>Cobalt stores the association in {@code AbstractWhatsAppStore.marketingMessageBroadcasts}
 * as a {@code Map<messageId, premiumMessageId>}. This is an architectural adaptation:
 * WhatsApp Web mutates the {@code sentMessageIds} {@code Set} stored on the premium
 * message model itself (and persists via {@code WAWebPremiumMessageAddSendAction}).
 * Cobalt's {@link MarketingMessageBroadcastAction} protobuf does not carry a
 * {@code sentMessageIds} field, so a side map is used instead.
 */
@WhatsAppWebModule(moduleName = "WAWebPremiumMessageBroadcastSync")
public final class MarketingMessageBroadcastHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton handler.
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
     * Applies a marketing message broadcast mutation and returns the detailed
     * outcome.
     *
     * <p>Per WhatsApp Web {@code WAWebPremiumMessageBroadcastSync.applyMutations}
     * the per-mutation logic is:
     * <ol>
     *   <li>Read {@code indexParts[1]} as {@code premiumMessageId} and
     *       {@code indexParts[2]} as {@code messageId}.</li>
     *   <li>If either is missing or empty, return
     *       {@code malformedActionIndex()}.</li>
     *   <li>If the operation is {@code "set"}, look up the premium message
     *       template via {@code PremiumMessageCollection.find(premiumMessageId)}.
     *       If it does not exist, return {@code {actionState: Orphan}}.
     *       Otherwise, queue the {@code (premiumMessageId, messageId)} pair
     *       for the batched persist (in WhatsApp Web this is
     *       {@code WAWebPremiumMessageAddSendAction}, which adds
     *       {@code messageId} to the premium message's
     *       {@code sentMessageIds} set and writes it back to the
     *       PremiumMessageCollection / IDB table) and return
     *       {@code {actionState: Success}}.</li>
     *   <li>For any other operation (e.g., {@code "remove"}) increment the
     *       unsupported counter (a no-op in WhatsApp Web because the value is
     *       discarded) and return {@code {actionState: Unsupported}}.</li>
     * </ol>
     *
     * <p>Cobalt diverges from WhatsApp Web in two ways, both intentional:
     * <ul>
     *   <li>The persist is per-mutation rather than batched. WhatsApp Web
     *       collects all queued pairs in a {@code n} array and calls
     *       {@code WAWebPremiumMessageAddSendAction(n)} once at the end of the
     *       batch. Cobalt updates the {@code marketingMessageBroadcasts} map
     *       eagerly because the underlying storage is a flat key/value map and
     *       there is no per-entity Set to mutate.</li>
     *   <li>WhatsApp Web's per-mutation {@code try/catch} that produces a
     *       {@code Failed} state is not replicated. Per Cobalt's error model,
     *       unexpected exceptions propagate to the orchestration layer instead
     *       of being mapped to a Failed state.</li>
     * </ul>
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageBroadcastSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        // WAWebPremiumMessageBroadcastSync.applyMutations: var i=r[1], l=r[2]; if(!i||!l) return t.malformedActionIndex().
        // Slots 1 and 2 are read unconditionally; mirror the undefined-checks with an explicit size guard.
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
            // The `a++` counter in WhatsApp Web is dead code (only used in `a > 0` which is itself a no-op expression).
            return MutationApplicationResult.unsupported();
        }

        // PremiumMessageCollection.find(i) == null ? {actionState: Orphan}
        // ADAPTED: Cobalt's marketingMessages collection plays the role of PremiumMessageCollection.
        if (client.store().findMarketingMessage(premiumMessageId).isEmpty()) {
            return MutationApplicationResult.orphan();
        }

        // followed by `yield WAWebPremiumMessageAddSendAction(n)`, which (per
        //     var r = PremiumMessageCollection.get(premiumMessageId);
        //     if (r) { var a = new Set(r.sentMessageIds); a.add(messageId); r.set("sentMessageIds", a); }
        //     ...bulkCreateOrMerge to persist...
        // ADAPTED: MarketingMessageAction has no `sentMessageIds` field, so the association is
        // tracked in a side store keyed by messageId, with the premium message id stored as the
        // record's status field.
        client.store().putMarketingMessageBroadcast(new MarketingMessageBroadcastBuilder()
                .templateId(messageId)
                .status(premiumMessageId)
                .build());
        return MutationApplicationResult.success();
    }
}
