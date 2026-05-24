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
 * Applies the {@code marketingMessage} app-state sync action that creates,
 * edits or soft-deletes a premium message template.
 *
 * @apiNote
 * Drives the SMB premium-message template surface: when the primary
 * device authors, edits or deletes a marketing template the resulting
 * row fans out across the {@link SyncPatchType#REGULAR} collection so
 * companion devices can render the same template list. The mutation
 * index keys each entry by the stable template id, formatted as
 * {@snippet :
 *     ["marketingMessage", messageId]
 * }
 *
 * @implNote
 * This implementation persists each template eagerly through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#putMarketingMessage}
 * keyed by the template id, collapsing WA Web's two-stage
 * {@code WAWebPremiumMessageSchema.getPremiumMessageTable().bulkCreateOrMerge(n)}
 * + {@code PremiumMessageCollection.add(...)} flow into a single map
 * write because Cobalt's storage is a flat key/value map. The
 * {@code isDeleted} flag is preserved on the stored row so the rest
 * of the pipeline can filter the live-template view; per Cobalt's
 * pluggable error model, exceptions propagate to the orchestrator
 * instead of being mapped to
 * {@link MutationApplicationResult#failed()} inline.
 */
@WhatsAppWebModule(moduleName = "WAWebPremiumMessageSync")
public final class MarketingMessageHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link MarketingMessageHandler}.
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
     * {@inheritDoc}
     *
     * @implNote
     * This implementation classifies a missing index slot or a
     * missing {@link MarketingMessageAction#type()} as
     * {@link MutationApplicationResult#malformed()} so the
     * orchestrator does not silently overwrite a template with a
     * default-typed row. {@link MarketingMessageAction#createdAt()}
     * and {@link MarketingMessageAction#lastSentAt()} are converted
     * from the wire's epoch milliseconds to
     * {@link Instant#ofEpochMilli(long)}; the
     * {@link MarketingMessageAction#isDeleted()} flag is persisted
     * verbatim, matching WA Web which never branches on it (later
     * readers filter when listing live templates).
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPremiumMessageSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var messageId = indexArray.getString(1);
        if (messageId == null || messageId.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof MarketingMessageAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        if (action.type().isEmpty()) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().putMarketingMessage(new MarketingMessageBuilder()
                .templateId(messageId)
                .name(action.name().orElse(null))
                .message(action.message().orElse(null))
                .type(action.type().orElse(null))
                .createdAt(action.createdAt().isPresent() ? Instant.ofEpochMilli(action.createdAt().getAsLong()) : null)
                .lastSentAt(action.lastSentAt().isPresent() ? Instant.ofEpochMilli(action.lastSentAt().getAsLong()) : null)
                .deleted(action.isDeleted())
                .mediaId(action.mediaId().orElse(null))
                .build());
        return MutationApplicationResult.success();
    }
}
