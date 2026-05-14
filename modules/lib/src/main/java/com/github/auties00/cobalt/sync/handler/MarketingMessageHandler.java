package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.MarketingMessageBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.time.Instant;

/**
 * Handles marketing (a.k.a. premium) message template actions.
 *
 * <p>This handler processes mutations related to premium/marketing message
 * templates. Each mutation persists or updates a {@link MarketingMessageAction}
 * keyed by a stable {@code messageId} parsed from the mutation index.
 *
 * <p>Index format: {@code ["marketingMessage", messageId]}
 *
 * <p>Per WhatsApp Web {@code WAWebPremiumMessageSync.applyMutations}, on a
 * {@code "set"} operation the web client validates that
 * {@code value.marketingMessageAction} is present and that {@code type} is
 * non-{@code null}, then accumulates a row to be persisted via
 * {@code WAWebPremiumMessageSchema.getPremiumMessageTable().bulkCreateOrMerge}
 * and added to the in-memory {@code PremiumMessageCollection}. Any other
 * operation maps to {@code SyncActionState.Unsupported} and a missing index
 * maps to {@code malformedActionIndex()}.
 *
 * <p>Cobalt collapses both the IDB table and the in-memory collection into
 * {@code AbstractWhatsAppStore.marketingMessages}, a flat
 * {@code Map<messageId, MarketingMessageAction>}. The map is updated eagerly
 * per mutation, mirroring how the other Cobalt sync handlers update their
 * store maps without holding a per-entity lock.
 */
@WhatsAppWebModule(moduleName = "WAWebPremiumMessageSync")
public final class MarketingMessageHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MarketingMessageHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return MarketingMessageAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return MarketingMessageAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return MarketingMessageAction.ACTION_VERSION;
    }

    /**
     * Applies a marketing message mutation and returns the detailed outcome.
     *
     * <p>Per WhatsApp Web {@code WAWebPremiumMessageSync.applyMutations} the
     * per-mutation logic is:
     * <ol>
     *   <li>Read {@code indexParts[1]} as {@code messageId}. If absent, return
     *       {@code malformedActionIndex()}.</li>
     *   <li>If the operation is {@code "set"}:
     *     <ol type="a">
     *       <li>If {@code value.marketingMessageAction} is missing, increment
     *           the {@code r} counter and return
     *           {@code malformedActionValue(collectionName)}.</li>
     *       <li>If {@code marketingMessageAction.type} is {@code null},
     *           increment the {@code a} counter and return
     *           {@code malformedActionValue(collectionName)}.</li>
     *       <li>Otherwise, queue
     *           {@code {id, name, type, isDeleted, message, mediaId, sentMessageIds: new Set}}
     *           for the batched persist and return
     *           {@code {actionState: Success}}. The full action (including
     *           {@code isDeleted}) is stored as-is; readers later filter on
     *           {@code isDeleted} when listing live templates.</li>
     *     </ol>
     *   </li>
     *   <li>For any other operation, increment the {@code i} counter and
     *       return {@code {actionState: Unsupported}}.</li>
     * </ol>
     *
     * <p>After the loop WhatsApp Web persists via
     * {@code WAWebPremiumMessageSchema.getPremiumMessageTable().bulkCreateOrMerge(n)}
     * and adds the rows to the in-memory
     * {@code WAWebPremiumMessageCollection.PremiumMessageCollection}. Cobalt
     * collapses both into a single
     * {@code Map<messageId, MarketingMessageAction>} on the store and updates
     * it eagerly per mutation.
     *
     * <p>Cobalt diverges from WhatsApp Web in two intentional ways:
     * <ul>
     *   <li>The persist is per-mutation rather than batched. WhatsApp Web
     *       collects all queued rows in an {@code n} array and calls
     *       {@code bulkCreateOrMerge(n)} once at the end of the batch.
     *       Cobalt updates the {@code marketingMessages} map eagerly because
     *       the underlying storage is a flat key/value map and there is no
     *       per-entity batch buffer.</li>
     *   <li>WhatsApp Web's per-mutation {@code try/catch} that produces a
     *       {@code Failed} state is not replicated. Per Cobalt's error model,
     *       unexpected exceptions propagate to the orchestration layer instead
     *       of being mapped to a {@code Failed} state.</li>
     * </ul>
     *
     * <p>The {@code r > 0}, {@code a > 0} and {@code i > 0} expressions in
     * WhatsApp Web are dead-code reads of the per-batch counters and are
     * intentionally not replicated.
     * @param client   the {@link WhatsAppClient} instance linked to the mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        // WAWebPremiumMessageSync.applyMutations: var l=e.indexParts, s=l[1]; if(!s) return t.malformedActionIndex().
        // l[1] is undefined when missing; mirror with explicit size check.
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var messageId = indexArray.getString(1);
        if (messageId == null || messageId.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        if (mutation.operation() != SyncdOperation.SET) {
            // The `i++` counter is dead-code in WhatsApp Web (only read by `i > 0` which is itself a no-op expression).
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof MarketingMessageAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        if (action.type().isEmpty()) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        //   n.push({id: s, name: p, type: _, isDeleted: c, message: m, mediaId: d, sentMessageIds: new Set})
        // followed (after the loop) by:
        //   yield WAWebPremiumMessageSchema.getPremiumMessageTable().bulkCreateOrMerge(n)
        //   PremiumMessageCollection.add(n.map(e => babelHelpers.extends({}, e)))
        // ADAPTED: Cobalt's marketingMessages quintet plays the role of both the IDB table and the
        // PremiumMessageCollection. The action is stored as-is regardless of the isDeleted flag,
        // matching WhatsApp Web (which never branches on isDeleted in this handler — readers
        // filter on it when listing live templates).
        client.store().putMarketingMessage(new MarketingMessageBuilder()
                .templateId(messageId)
                .name(action.name().orElse(null))
                .message(action.message().orElse(null))
                .type(action.type().orElse(null))
                .createdAt(action.createdAt().isPresent() ? Instant.ofEpochMilli(action.createdAt().getAsLong()) : null)
                .lastSentAt(action.lastSentAt().isPresent() ? Instant.ofEpochMilli(action.lastSentAt().getAsLong()) : null)
                .deleted(action.isDeleted())
                .mediaId(action.mediaId().orElse(null))
                .build()); // ADAPTED: WAWebPremiumMessageSync.applyMutations: n.push({id: s, ...}) + bulkCreateOrMerge + PremiumMessageCollection.add
        return MutationApplicationResult.success();
    }
}
