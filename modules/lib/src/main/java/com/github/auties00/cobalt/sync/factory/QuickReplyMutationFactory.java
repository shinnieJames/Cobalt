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
 * <p>Mirrors the {@code getQuickReplyAddOrEditMutation} and
 * {@code getQuickReplyDeleteMutation} exports of WhatsApp Web's
 * {@code WAWebQuickRepliesSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.QuickReplyHandler}.
 */
public final class QuickReplyMutationFactory {
    /**
     * Constructs a quick-reply mutation factory.
     */
    public QuickReplyMutationFactory() {

    }

    /**
     * Builds a pending mutation that deletes a quick reply by its id.
     *
     * <p>Per WhatsApp Web {@code WAWebQuickRepliesSync.getQuickReplyDeleteMutation}:
     * <ol>
     *   <li>Wraps a {@code quickReplyAction} with {@code deleted: true},
     *       {@code keywords: []}, {@code shortcut: ""}, {@code message: ""},
     *       {@code count: 0}</li>
     *   <li>Calls {@code WAWebSyncdActionUtils.buildPendingMutation} with
     *       {@code collection = Regular}, {@code indexArgs = [quickReplyId]},
     *       value, version, operation {@code SET}, the supplied timestamp, and
     *       the {@code QuickReply} action</li>
     * </ol>
     *
     * @param quickReplyId the quick reply identifier (the {@code indexArgs[0]} entry)
     * @param timestamp    the mutation timestamp
     * @return the pending mutation that removes the quick reply on the server side
     */
    @WhatsAppWebExport(moduleName = "WAWebQuickRepliesSync", exports = "getQuickReplyDeleteMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getQuickReplyDeleteMutation(String quickReplyId, Instant timestamp) {
        Objects.requireNonNull(quickReplyId, "quickReplyId cannot be null"); // ADAPTED: defensive null check not present in WA Web
        Objects.requireNonNull(timestamp, "timestamp cannot be null"); // ADAPTED: defensive null check not present in WA Web
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
     * <p>Per WhatsApp Web {@code WAWebQuickRepliesSync.getQuickReplyAddOrEditMutation}:
     * <ol>
     *   <li>Wraps a {@code quickReplyAction} with {@code deleted: false},
     *       {@code keywords: i}, {@code shortcut: n}, {@code message: r},
     *       {@code count: a}</li>
     *   <li>Calls {@code WAWebSyncdActionUtils.buildPendingMutation} with
     *       {@code collection = Regular}, {@code indexArgs = [quickReplyId]},
     *       value, version, operation {@code SET}, the supplied timestamp, and
     *       the {@code QuickReply} action</li>
     * </ol>
     *
     * @param quickReplyId the quick reply identifier (the {@code indexArgs[0]} entry)
     * @param shortcut     the shortcut text
     * @param message      the quick reply message body
     * @param count        the usage counter
     * @param keywords     the keyword list (may be empty but not {@code null})
     * @param timestamp    the mutation timestamp
     * @return the pending mutation that creates or updates the quick reply on the server side
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
        Objects.requireNonNull(quickReplyId, "quickReplyId cannot be null"); // ADAPTED: defensive null check not present in WA Web
        Objects.requireNonNull(shortcut, "shortcut cannot be null"); // ADAPTED: defensive null check not present in WA Web
        Objects.requireNonNull(message, "message cannot be null"); // ADAPTED: defensive null check not present in WA Web
        Objects.requireNonNull(keywords, "keywords cannot be null"); // ADAPTED: defensive null check not present in WA Web
        Objects.requireNonNull(timestamp, "timestamp cannot be null"); // ADAPTED: defensive null check not present in WA Web
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
