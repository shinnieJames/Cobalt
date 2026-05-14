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
 * Handles quick reply sync mutations.
 *
 * <p>This handler processes incoming mutations that create, update, or delete
 * quick reply templates for business accounts. The action is identified by
 * the {@code "quick_reply"} action name in {@code SyncActionValue.quickReplyAction}.
 * The mutation index format is {@code ["quick_reply", quickReplyId]}.
 *
 * <p>Per WhatsApp Web {@code WAWebQuickRepliesSync}, the handler belongs to the
 * {@code Regular} collection, uses version {@code 2}, and routes on action name
 * {@code "quick_reply"}. WA Web persists each entry in the {@code quick-reply}
 * IndexedDB table keyed by the {@code id} primary key (the second index part).
 *
 * <p>On {@code SET}, the handler validates that {@code indexParts[1]} (the
 * quick reply id) is present, that the {@code quickReplyAction} payload is
 * present, and either removes the entry (when {@code deleted === true}) or
 * upserts it (after validating {@code shortcut} and {@code message} are
 * non-empty). All non-{@code SET} operations are classified as
 * {@code UNSUPPORTED}.
 *
 * <p>Index format: {@code ["quick_reply", quickReplyId]}
 */
@WhatsAppWebModule(moduleName = "WAWebQuickRepliesSync")
public final class QuickReplyHandler implements WebAppStateActionHandler {

    /**
     * Creates the singleton quick reply sync handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public QuickReplyHandler() {

    }

    /**
     * {@inheritDoc}
     * @return the action name {@code "quick_reply"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return QuickReplyAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     * @return {@link SyncPatchType#REGULAR}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return QuickReplyAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     * @return the version number {@code 2}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return QuickReplyAction.ACTION_VERSION;
    }

    /**
     * Applies a quick reply mutation and returns the detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebQuickRepliesSync.applyMutations}, the
     * handler iterates the batch and, for each mutation:
     * <ol>
     *   <li>If {@code operation !== "set"}, increments the unsupported counter
     *       and returns {@code Unsupported}</li>
     *   <li>Reads {@code indexParts[1]} as the quick reply id; if missing,
     *       returns {@code malformedActionIndex()} (no counter increment)</li>
     *   <li>Reads {@code value.quickReplyAction}; if missing, increments the
     *       malformed counter and returns
     *       {@code WAWebSyncdIndexUtils.malformedActionValue(collectionName)}</li>
     *   <li>If {@code quickReplyAction.deleted === true}, removes the entry
     *       from the local {@code quick-reply} table via
     *       {@code WAWebSchemaQuickReply.getQuickReplyTable().remove(id)} and
     *       calls {@code WAWebBackendApi.frontendFireAndForget("removeQuickReplyFromCollection")},
     *       returning {@code Success}</li>
     *   <li>Reads {@code shortcut} and {@code message}; if either is null or
     *       empty, increments the malformed counter and returns
     *       {@code WAWebSyncdIndexUtils.malformedActionValue(collectionName)}</li>
     *   <li>Defaults {@code keywords} to an empty list and {@code count} to
     *       {@code 0} (matching the WA Web {@code s.keywords||[]} and
     *       {@code s.count||0} expressions)</li>
     *   <li>Builds {@code {id, shortcut, count, message, keywords}} and
     *       upserts it via
     *       {@code WAWebSchemaQuickReply.getQuickReplyTable().createOrReplace(p)},
     *       then calls {@code WAWebBackendApi.frontendFireAndForget("updateQuickReplyCollection")},
     *       returning {@code Success}</li>
     * </ol>
     *
     * <p>After the loop, WA Web logs the malformed and unsupported counters
     * via {@code WALogger.WARN}. Cobalt's per-mutation interface omits the
     * batch-level logging because each mutation returns its own
     * {@link MutationApplicationResult}.
     *
     * <p>Exceptions thrown inside the per-mutation block are caught in WA Web
     * and converted to a {@code Failed} result, mirrored here by the outer
     * try/catch.
     *
     * <p>Cobalt's {@link com.github.auties00.cobalt.model.preference.QuickReply}
     * carries the {@code id} field as its primary key, mirroring WA Web's
     * {@code WAWebSchemaQuickReply} table. The {@code WhatsAppStore} maps
     * quick replies by their {@code id}, so mutations that change a quick
     * reply's shortcut while preserving its id correctly upsert the existing
     * entry rather than leaking duplicates.
     *
     * <p>The {@code WAWebBackendApi.frontendFireAndForget} calls are
     * intentionally omitted: Cobalt has no frontend bridge that needs to be
     * notified of collection updates.
     * @param client   the WhatsApp client
     * @param mutation the mutation to apply
     * @return the detailed application result
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
                // ADAPTED: Cobalt has no frontend bridge; the fire-and-forget call is omitted.
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
            // ADAPTED: Cobalt has no frontend bridge; the fire-and-forget call is omitted.
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
