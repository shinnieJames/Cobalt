package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.bot.metrics.BotMetricsEntryPoint;
import com.github.auties00.cobalt.model.bot.metrics.BotMetricsMetadata;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.event.EventMessage;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollResultSnapshotMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.SecretEncMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryNotice;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Builds the {@code <meta>} stanza child node that carries auxiliary
 * metadata about the message being sent.
 *
 * <p>The node may include any of the following attributes, depending on
 * message type and recipient:
 * <ul>
 * <li>{@code origin} — LID origin type or bot entry-point origin
 * <li>{@code destination_id} — bot metrics destination identifier
 * <li>{@code sender_intent} — {@code "hosted"} for hosted business accounts
 * <li>{@code polltype} — poll message type ({@code "creation"}, {@code "vote"}, {@code "result_snapshot"})
 * <li>{@code event_type} — event message type ({@code "creation"}, {@code "response"}, {@code "edit"})
 * <li>{@code view_once} — {@code "true"} for view-once media
 * <li>{@code appdata} — app data type ({@code "member_tag"}, {@code "default"}, {@code "group_history"})
 * <li>{@code tag_reason} — member label operation ({@code "user_delete"}, {@code "user_update"})
 * <li>{@code thread_msg_id}, {@code thread_msg_sender_jid} — comment thread identifiers
 * <li>{@code conversation_thread_id} — hashed AI conversation thread identifier
 * <li>{@code status_setting} — status privacy setting for status messages
 * </ul>
 *
 * @implNote WAWebSendMsgMetaNode.genMetaNode: resolves polltype,
 * event_type, origin, destination_id, sender_intent, view_once,
 * appdata, tag_reason, thread_msg_id, thread_msg_sender_jid, and
 * conversation_thread_id from the message protobuf and chat data.
 */
public final class MetaStanza {
    /**
     * The store used for resolving chat metadata such as LID origin type
     * and verified business names.
     *
     * @implNote WAWebSendMsgMetaNode: uses WAWebChatCollection and
     * WAWebContactCollection to resolve origin and sender_intent.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a new {@code MetaStanza} builder with the given store.
     *
     * @param store the WhatsApp store for resolving chat metadata
     * @implNote WAWebSendMsgMetaNode: receives store dependencies via
     * module-level imports.
     */
    public MetaStanza(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Builds the {@code <meta>} node for an E2E-encrypted message.
     *
     * @param chatJid          the recipient chat JID (used to resolve
     *                         {@code origin} and {@code sender_intent})
     * @param container        the message container
     * @param statusSetting    the status privacy setting
     *                         ({@code "contacts"}, {@code "allowlist"},
     *                         or {@code "denylist"}), or {@code null}
     *                         for non-status messages
     * @param hashedAiThreadId the HMAC-hashed AI thread identifier for
     *                         the {@code conversation_thread_id} attribute,
     *                         or {@code null} if this is not an AI thread
     * @return the meta node, or {@code null} if no metadata attributes
     *         are applicable
     *
     * @implNote WAWebSendMsgMetaNode.genMetaNode: full variant with all
     * attributes. Receives hashedAiThreadId from the caller (pre-computed
     * via WAWebChatThreadLogging.getThreadIDHMAC).
     * WAWebEncryptAndSendStatusMsg: includes
     * {@code status_setting} in meta node for status stanzas.
     */
    public Node buildChat(Jid chatJid, MessageContainer container, String statusSetting, String hashedAiThreadId) {
        var message = container.content();

        // WAWebSendMsgMetaNode.u: determine polltype
        var polltype = resolvePollType(message);

        // WAWebSendMsgMetaNode.c: determine event_type
        var eventType = resolveEventType(message);

        // WAWebSendMsgMetaNode.d: determine appdata
        var appdata = resolveAppdata(message);

        // WAWebSendMsgMetaNode.p: determine tag_reason
        var tagReason = resolveTagReason(message);

        // WAWebSendMsgMetaNode: determine view_once
        // ADAPTED: WA Web checks n.data.mediaData?.isViewOnce; Cobalt checks
        // the protobuf wrapper type which is semantically equivalent
        var viewOnce = container.futureProofContentType() == FutureProofMessageType.VIEW_ONCE ? "true" : null;

        // WAWebSendMsgMetaNode.genMetaNode: resolve bot metrics metadata
        var botMetrics = container.messageContextInfo()
                .flatMap(ChatMessageContextInfo::botMetadata)
                .map(bot -> bot.botMetricsMetadata().orElse(null))
                .orElse(null);

        // WAWebSendMsgMetaNode.genMetaNode: destination_id from botMetricsMetadata
        var destinationId = botMetrics != null
                ? botMetrics.destinationId().orElse(null)
                : null;

        // WAWebSendMsgMetaNode.genMetaNode: resolve origin
        // WA Web: v = getBotOriginFromBotMetricsEntryPoint(b.destinationEntryPoint)
        //         R = v != null && isMetaAiBot(t) ? v : getOriginAttribute(t, i)
        var botOrigin = botMetrics != null
                ? botMetrics.destinationEntryPoint()
                        .map(MetaStanza::resolveBotOrigin)
                        .orElse(null)
                : null;
        var origin = botOrigin != null && isMetaAiBot(chatJid)
                ? botOrigin
                : resolveOrigin(chatJid);

        // WAWebSendMsgMetaNode.genMetaNode: sender_intent = "hosted"
        // when appendHostedSenderIntent is true
        var senderIntent = isHostedRecipient(chatJid) ? "hosted" : null;

        // WAWebSendMsgMetaNode.genMetaNode: thread context from
        // extractCommentTargetIdAndSenderLid (for addon/comment messages)
        // ADAPTED: Cobalt extracts thread_msg_id and thread_msg_sender_jid
        // from messageContextInfo.threadId for non-AI threads
        String threadMsgId = null;
        Jid threadMsgSenderJid = null;
        var deviceInfo = container.messageContextInfo().orElse(null);
        if (deviceInfo != null) {
            var threads = deviceInfo.threadId();
            for (var thread : threads) {
                if (thread.threadType().orElse(null) == MessageThreadId.ThreadType.AI_THREAD) {
                    continue;
                }
                var threadKey = thread.threadKey();
                var keyId = threadKey.flatMap(MessageKey::id)
                        .filter(entry -> !entry.isEmpty());
                if (keyId.isPresent()) {
                    threadMsgId = keyId.get();
                    threadMsgSenderJid = threadKey.flatMap(MessageKey::senderJid)
                            .orElse(null);
                    break;
                }
            }
        }

        // WAWebSendMsgMetaNode.genMetaNode: only emit if any attribute is set
        if (polltype == null && eventType == null && viewOnce == null
                && origin == null && destinationId == null
                && senderIntent == null && appdata == null
                && threadMsgId == null && hashedAiThreadId == null
                && tagReason == null && statusSetting == null) {
            return null;
        }

        // WAWebSendMsgMetaNode.genMetaNode: wap("meta", {...})
        return new NodeBuilder()
                .description("meta")
                .attribute("origin", origin)
                .attribute("destination_id", destinationId)
                .attribute("sender_intent", senderIntent)
                .attribute("polltype", polltype)
                .attribute("event_type", eventType)
                .attribute("thread_msg_id", threadMsgId)
                .attribute("thread_msg_sender_jid", threadMsgSenderJid)
                .attribute("appdata", appdata)
                .attribute("view_once", viewOnce)
                .attribute("conversation_thread_id", hashedAiThreadId)
                .attribute("tag_reason", tagReason)
                .attribute("status_setting", statusSetting)
                .build();
    }

    /**
     * Overload for callers that do not have a pre-computed hashed AI
     * thread identifier.
     *
     * @param chatJid       the recipient chat JID
     * @param container     the message container
     * @param statusSetting the status privacy setting, or {@code null}
     * @return the meta node, or {@code null} if no metadata attributes
     *         are applicable
     *
     * @implNote WAWebSendMsgMetaNode.genMetaNode: delegates to the
     * four-parameter variant with {@code null} hashedAiThreadId.
     */
    public Node buildChat(Jid chatJid, MessageContainer container, String statusSetting) {
        return buildChat(chatJid, container, statusSetting, null);
    }

    /**
     * Resolves the {@code polltype} attribute from the message content.
     *
     * @param message the unwrapped message
     * @return the poll type string, or {@code null} for non-poll messages
     *
     * @implNote WAWebSendMsgMetaNode.u: pollCreationMessage/V2/V3 returns
     * "creation", pollUpdateMessage with non-null vote returns "vote",
     * pollResultSnapshotMessage/V3 returns "result_snapshot".
     * WAWebHandleMsgCommon.POLL_TYPES: the possible string values.
     */
    private static String resolvePollType(Message message) {
        return switch (message) {
            // WAWebSendMsgMetaNode.u: pollCreationMessage != null → "creation"
            case PollCreationMessage _ -> "creation";
            // WAWebSendMsgMetaNode.u: pollUpdateMessage?.vote != null → "vote"
            case PollUpdateMessage p when p.vote().isPresent() -> "vote";
            // WAWebSendMsgMetaNode.u: pollResultSnapshotMessage != null → "result_snapshot"
            case PollResultSnapshotMessage _ -> "result_snapshot";
            default -> null;
        };
    }

    /**
     * Resolves the {@code event_type} attribute from the message content.
     *
     * @param message the unwrapped message
     * @return the event type string, or {@code null} for non-event messages
     *
     * @implNote WAWebSendMsgMetaNode.c: eventMessage returns "creation",
     * encEventResponseMessage returns "response",
     * secretEncryptedMessage with EVENT_EDIT returns "edit".
     * WAWebHandleMsgCommon.EVENT_TYPES: the possible string values.
     */
    private static String resolveEventType(Message message) {
        return switch (message) {
            // WAWebSendMsgMetaNode.c: eventMessage != null → "creation"
            case EventMessage _ -> "creation";
            // WAWebSendMsgMetaNode.c: encEventResponseMessage != null → "response"
            case EncEventResponseMessage _ -> "response";
            // WAWebSendMsgMetaNode.c: secretEncryptedMessage.secretEncType === EVENT_EDIT → "edit"
            case SecretEncMessage s
                    when s.secretEncType().orElse(null) == SecretEncMessage.SecretEncType.EVENT_EDIT -> "edit";
            default -> null;
        };
    }

    /**
     * Resolves the {@code appdata} attribute from the message content.
     *
     * <p>Returns {@code "member_tag"} for group member label change
     * protocol messages, {@code "default"} for ephemeral sync response
     * protocol messages, {@code "group_history"} for message history
     * notices, or {@code null} otherwise. The {@code "default"} value
     * for peer messages is handled separately by {@code PeerMessageSender}
     * (which always sets {@code appdata="default"}).
     *
     * @param message the unwrapped message
     * @return the appdata string, or {@code null}
     *
     * @implNote WAWebSendMsgMetaNode.d: checks msg type/subtype to return
     * "member_tag", "default" (peer/ephemeral sync), or "group_history".
     * WAWebHandleMsgCommon.APPDATA: the possible string values.
     * The {@code isCategoryPeerMessage} branch is ADAPTED into
     * {@code PeerMessageSender} which builds its own meta node.
     */
    private static String resolveAppdata(Message message) {
        // WAWebSendMsgMetaNode.d: type === PROTOCOL && subtype === "member_label"
        if (message instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.GROUP_MEMBER_LABEL_CHANGE) {
            return "member_tag";
        }
        // WAWebSendMsgMetaNode.d: type === PROTOCOL && subtype === EphemeralSyncResponse → "default"
        if (message instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.EPHEMERAL_SYNC_RESPONSE) {
            return "default";
        }
        // WAWebSendMsgMetaNode.d: type === MESSAGE_HISTORY_NOTICE → "group_history"
        if (message instanceof MessageHistoryNotice) {
            return "group_history";
        }
        return null;
    }

    /**
     * Resolves the {@code tag_reason} attribute from the message content.
     *
     * <p>For group member label change protocol messages, returns
     * {@code "user_delete"} when the label is empty or absent, or
     * {@code "user_update"} when the label has a value.
     *
     * @param message the unwrapped message
     * @return the tag reason string, or {@code null} for non-label messages
     *
     * @implNote WAWebSendMsgMetaNode.p: checks msg type/subtype for
     * "member_label", then inspects memberLabelData.label to determine
     * "user_delete" vs "user_update".
     */
    private static String resolveTagReason(Message message) {
        // WAWebSendMsgMetaNode.p: type === PROTOCOL && subtype === "member_label"
        if (!(message instanceof ProtocolMessage pm)
                || pm.type().orElse(null) != ProtocolMessage.Type.GROUP_MEMBER_LABEL_CHANGE) {
            return null;
        }
        // WAWebSendMsgMetaNode.p: label === "" || label == null → "user_delete", else "user_update"
        var label = pm.memberLabel()
                .flatMap(ml -> ml.label())
                .orElse(null);
        return (label == null || label.isEmpty()) ? "user_delete" : "user_update";
    }

    /**
     * Resolves the {@code origin} meta attribute for LID chats.
     *
     * <p>Returns the LID origin type string (e.g. {@code "ctwa"}) when the
     * chat JID is a LID and the chat's origin type is
     * {@code PNH_CTWA}. Returns {@code null} otherwise.
     *
     * @param chatJid the recipient chat JID
     * @return the origin string, or {@code null}
     *
     * @implNote WAWebSendMsgMetaNode.getOriginAttribute: returns
     * the lidOriginType when to.isLid() and origin === LidOriginType.PNH_CTWA.
     */
    private String resolveOrigin(Jid chatJid) {
        // WAWebSendMsgMetaNode.getOriginAttribute: e.isLid() && n === PNH_CTWA
        if (!chatJid.hasLidServer()) {
            return null;
        }

        return store.findChatByJid(chatJid)
                .flatMap(Chat::lidOriginType)
                .filter("ctwa"::equals)
                .orElse(null);
    }

    /**
     * Returns whether the given JID identifies a Meta AI bot account.
     *
     * <p>WA Web's {@code isMetaAiBot} checks if the JID equals either the
     * PN bot WID ({@code 13135550002@c.us}) or the FBID bot WID
     * ({@code 867051314767696@bot}). Cobalt compares against both known
     * Meta AI JIDs.
     *
     * @param jid the JID to check
     * @return {@code true} if the JID is a Meta AI bot
     *
     * @implNote WAWebBotUtils.isMetaAiBot: checks equality against
     * META_AI_BOT_FBID_WID and META_AI_BOT_WID.
     */
    private static boolean isMetaAiBot(Jid jid) {
        // WAWebBotUtils.isMetaAiBot: e.equals(META_AI_BOT_WID) || e.equals(META_AI_BOT_FBID_WID)
        return jid.equals(Jid.metaAiBotAccount())
                || "13135550002".equals(jid.user());
    }

    /**
     * Maps a {@link BotMetricsEntryPoint} to the corresponding origin
     * string for the {@code <meta>} node.
     *
     * @param entryPoint the bot metrics entry point
     * @return the origin string, or {@code null} if the entry point has
     *         no corresponding origin value
     *
     * @implNote WAWebBotLoggingUtils.getBotOriginFromBotMetricsEntryPoint:
     * maps each BotMetricsEntryPoint enum value to a string literal.
     */
    private static String resolveBotOrigin(BotMetricsEntryPoint entryPoint) {
        // WAWebBotLoggingUtils.getBotOriginFromBotMetricsEntryPoint
        return switch (entryPoint) {
            case FAVICON -> "favicon";
            case CHATLIST -> "chat_list";
            case AISEARCH_NULL_STATE_SUGGESTION -> "nullstate_suggestion";
            case AISEARCH_TYPE_AHEAD_SUGGESTION -> "typeahead_suggestion";
            case DEEPLINK -> "deeplink";
            case NOTIFICATION -> "notification";
            case AI_TAB -> "ai_tab";
            case ASK_META_AI_CONTEXT_MENU -> "ask_meta_ai_context_menu";
            case ASK_META_AI_CONTEXT_MENU_1ON1 -> "ask_meta_ai_context_menu_1on1";
            case ASK_META_AI_CONTEXT_MENU_GROUP -> "ask_meta_ai_context_menu_group";
            case META_AI_FORWARD -> "meta_ai_forward";
            // WAWebBotLoggingUtils: WEB_INTRO_PANEL and WEB_NAVIGATION_BAR are
            // web-specific entry points not yet in Cobalt's BotMetricsEntryPoint enum
            default -> null;
        };
    }

    /**
     * Returns whether the recipient is a hosted business account.
     *
     * @param chatJid the recipient chat JID
     * @return {@code true} if the recipient has hosted business storage
     *
     * @implNote WAWebSendMsgMetaNode.genMetaNode: appendHostedSenderIntent
     * is true when the recipient has a verified business name with
     * host_storage set (indicating a hosted account).
     */
    private boolean isHostedRecipient(Jid chatJid) {
        // WAWebSendMsgMetaNode.genMetaNode: appendHostedSenderIntent option
        return store.findVerifiedBusinessName(chatJid)
                .map(vbn -> vbn.hostStorage().isPresent())
                .orElse(false);
    }

    /**
     * Builds a {@code <meta questiontype="question">} node for
     * newsletter question messages.
     *
     * @return the meta node
     *
     * @implNote WASmaxOutMessagePublishQuestionTypeQuestionMixin:
     * {@code <meta questiontype="question">}
     */
    public static Node buildNewsletterQuestion() {
        return new NodeBuilder()
                .description("meta")
                .attribute("questiontype", "question")
                .build();
    }

    /**
     * Builds a {@code <meta questiontype="reply">} node for
     * newsletter question reply messages.
     *
     * @return the meta node
     *
     * @implNote WASmaxOutMessagePublishQuestionTypeReplyMixin:
     * {@code <meta questiontype="reply">}
     */
    public static Node buildNewsletterQuestionReply() {
        return new NodeBuilder()
                .description("meta")
                .attribute("questiontype", "reply")
                .build();
    }

    /**
     * Builds a {@code <meta questiontype="response">} node for
     * newsletter question response messages.
     *
     * @return the meta node
     *
     * @implNote WASmaxOutMessagePublishQuestionTypeResponseMixin:
     * {@code <meta questiontype="response">}
     */
    public static Node buildNewsletterQuestionResponse() {
        return new NodeBuilder()
                .description("meta")
                .attribute("questiontype", "response")
                .build();
    }
}
