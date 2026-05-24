package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.QuickReplyAction;
import com.github.auties00.cobalt.model.sync.action.chat.QuickReplyActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing quick-reply sync mutations.
 *
 * @apiNote
 * Drives the Business quick-replies management surface on Settings;
 * supports the create, edit, and delete paths used by
 * {@code WAWebSendQuickReplyAddOrEditMutation} on WA Web. Mutations
 * produced here are consumed on the inbound side by
 * {@link com.github.auties00.cobalt.sync.handler.QuickReplyHandler}.
 *
 * @implNote
 * This implementation mirrors
 * {@code WAWebQuickRepliesSync.getQuickReplyDeleteMutation} and
 * {@code WAWebQuickRepliesSync.getQuickReplyAddOrEditMutation}, including
 * the always-empty {@code associatedLabelIds} field that WA Web emits on
 * both paths. Defensive null checks are added on every reference type
 * because the WA Web bundle assumes the caller never passes
 * {@code null}.
 */
public final class QuickReplyMutationFactory {
    /**
     * Constructs a quick-reply mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the public quick-reply setter on
     * {@link com.github.auties00.cobalt.client.WhatsAppClient}. The
     * factory keeps no state, so a single instance is sufficient per
     * client.
     */
    public QuickReplyMutationFactory() {

    }

    /**
     * Builds a pending mutation that deletes a quick reply by its id.
     *
     * @apiNote
     * Invoked from the public quick-reply deletion path; the receiver
     * detects the deletion via {@code s.deleted === true} on
     * {@link QuickReplyAction#deleted()} and removes the row from
     * {@code WAWebSchemaQuickReply.getQuickReplyTable}, then fires a
     * {@code removeQuickReplyFromCollection} backend event. The other
     * payload fields are set to their canonical empty values to match
     * WA Web's emitter shape.
     *
     * @implNote
     * This implementation adds defensive null checks on the
     * {@code quickReplyId} and {@code timestamp} parameters not present
     * in WA Web. The index follows the standard
     * {@code [actionName, quickReplyId]} shape and writes into the
     * {@code Regular} collection alongside the other quick-reply
     * mutations.
     *
     * @param quickReplyId the quick reply identifier (the
     *                     {@code indexArgs[0]} entry)
     * @param timestamp    the mutation timestamp
     * @return the pending mutation that removes the quick reply on the
     *         server side
     * @throws NullPointerException if {@code quickReplyId} or
     *                              {@code timestamp} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "getQuickReplyDeleteMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getQuickReplyDeleteMutation(String quickReplyId, Instant timestamp) {
        Objects.requireNonNull(quickReplyId, "quickReplyId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        var action = new QuickReplyActionBuilder()
                .deleted(true)
                .keywords(List.of())
                .shortcut("")
                .message("")
                .count(0)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .quickReplyAction(action)
                .build();
        var index = JSON.toJSONString(List.of(QuickReplyAction.ACTION_NAME, quickReplyId));
        var pendingMutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                QuickReplyAction.ACTION_VERSION
        );
        return new SyncPendingMutation(pendingMutation, 0);
    }

    /**
     * Builds a pending mutation that creates or edits a quick reply.
     *
     * @apiNote
     * Invoked from the public quick-reply create-or-edit path; the
     * receiver-side branch rejects mutations with an empty
     * {@code shortcut} or empty {@code message} as malformed, so callers
     * must supply non-empty strings on both. Receiving devices store the
     * full record via {@code createOrReplace} on the quick-reply table
     * and fire a {@code updateQuickReplyCollection} backend event with
     * the new keyword list and usage counter.
     *
     * @implNote
     * This implementation adds defensive null checks not present in
     * WA Web ({@code WAWebQuickRepliesSync.getQuickReplyAddOrEditMutation}
     * trusts every caller-supplied reference). The index follows the
     * standard {@code [actionName, quickReplyId]} shape and writes into
     * the {@code Regular} collection.
     *
     * @param quickReplyId the quick reply identifier (the
     *                     {@code indexArgs[0]} entry)
     * @param shortcut     the shortcut text; non-empty
     * @param message      the quick reply message body; non-empty
     * @param count        the usage counter, incremented on every send
     *                     of the quick reply
     * @param keywords     the keyword list (may be empty but not
     *                     {@code null})
     * @param timestamp    the mutation timestamp
     * @return the pending mutation that creates or updates the quick
     *         reply on the server side
     * @throws NullPointerException if any reference parameter is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "getQuickReplyAddOrEditMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getQuickReplyAddOrEditMutation(
            String quickReplyId,
            String shortcut,
            String message,
            int count,
            List<String> keywords,
            Instant timestamp
    ) {
        Objects.requireNonNull(quickReplyId, "quickReplyId cannot be null");
        Objects.requireNonNull(shortcut, "shortcut cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(keywords, "keywords cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        var action = new QuickReplyActionBuilder()
                .deleted(false)
                .keywords(keywords)
                .shortcut(shortcut)
                .message(message)
                .count(count)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .quickReplyAction(action)
                .build();
        var index = JSON.toJSONString(List.of(QuickReplyAction.ACTION_NAME, quickReplyId));
        var pendingMutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                QuickReplyAction.ACTION_VERSION
        );
        return new SyncPendingMutation(pendingMutation, 0);
    }
}
