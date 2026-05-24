package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.preference.QuickReplyBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.QuickReplyAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code quick_reply} app-state action that creates,
 * updates, or deletes WhatsApp Business quick reply templates.
 *
 * @apiNote
 * Drives the WhatsApp Business "Quick replies" surface: each mutation
 * upserts or deletes a {@code (shortcut, message, keywords, count)}
 * record keyed by quick reply id. The mutation index keys each entry
 * by the quick reply id, formatted as {@snippet :
 *     ["quick_reply", quickReplyId]
 * }
 *
 * @implNote
 * This implementation persists each entry on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} via
 * {@code addQuickReply}/{@code removeQuickReply} keyed by id; WA Web
 * stores the same shape in the {@code quick-reply} IndexedDB table
 * via {@code WAWebSchemaQuickReply.getQuickReplyTable().createOrReplace}/{@code .remove}.
 * The {@code WAWebBackendApi.frontendFireAndForget("updateQuickReplyCollection" /
 * "removeQuickReplyFromCollection")} dispatches are dropped because
 * Cobalt has no UI consumer; the per-batch
 * {@code WALogger.WARN} counters are also dropped.
 */
@WhatsAppWebModule(moduleName = "WAWebQuickRepliesSync")
public final class QuickReplyHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton quick reply sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; no AB-prop, store, or WAM
     * dependency is held.
     */
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public QuickReplyHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return QuickReplyAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return QuickReplyAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return QuickReplyAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the per-mutation arms of WA Web's
     * {@code WAWebQuickRepliesSync.applyMutations}: only
     * {@link SyncdOperation#SET} is accepted; a missing quick reply id
     * surfaces as {@link SyncdIndexUtils#malformedActionIndex(String, String)};
     * a missing {@link QuickReplyAction} payload as
     * {@link SyncdIndexUtils#malformedActionValue(String)};
     * {@link QuickReplyAction#deleted()} {@code == true} removes the
     * entry by id; otherwise the {@code shortcut} and {@code message}
     * fields must both be non-empty (mirroring WA Web's
     * {@code (c == null || c === "" || u == null || u === "") -> malformedActionValue}
     * guard); {@code keywords} defaults to an empty list and
     * {@code count} to {@code 0}. Per-mutation exceptions surface as
     * {@link MutationApplicationResult#failed()}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            var indexArray = JSON.parseArray(mutation.index());
            var quickReplyId = indexArray.size() > 1 ? indexArray.getString(1) : null;
            if (quickReplyId == null || quickReplyId.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof QuickReplyAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            if (action.deleted()) {
                client.store().removeQuickReply(quickReplyId);
                return MutationApplicationResult.success();
            }

            var message = action.message().orElse(null);
            var shortcut = action.shortcut().orElse(null);
            if (shortcut == null || shortcut.isEmpty() || message == null || message.isEmpty()) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var keywords = action.keywords();
            var count = action.count().orElse(0);
            var quickReply = new QuickReplyBuilder()
                    .id(quickReplyId)
                    .shortcut(shortcut)
                    .message(message)
                    .keywords(keywords)
                    .count(count)
                    .build();
            client.store().addQuickReply(quickReply);
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
