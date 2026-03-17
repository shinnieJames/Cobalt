package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.event.EventMessage;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollResultSnapshotMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.SecretEncMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

/**
 * Builds the {@code <meta>} stanza child node that carries auxiliary
 * metadata about the message being sent.
 *
 * <p>Thread context ({@code thread_msg_id}, {@code thread_msg_sender_jid},
 * {@code conversation_thread_id}) is extracted automatically from the
 * container's {@code deviceInfo.threadId} list.
 *
 * <p>The {@code origin} and {@code sender_intent} attributes are resolved
 * from the store based on the recipient's LID origin type and hosted
 * business account status.
 *
 * @apiNote WAWebSendMsgMetaNode.genMetaNode: resolves polltype,
 * event_type, origin, sender_intent, view_once, appdata, thread_msg_id,
 * thread_msg_sender_jid, and conversation_thread_id from the message
 * protobuf and group/chat data.
 */
public final class MetaStanza {
    private final WhatsAppStore store;

    public MetaStanza(WhatsAppStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Builds the {@code <meta>} node for an E2E-encrypted message.
     *
     * @param chatJid       the recipient chat JID (used to resolve
     *                      {@code origin} and {@code sender_intent})
     * @param container     the message container
     * @param statusSetting the status privacy setting
     *                      ({@code "contacts"}, {@code "allowlist"},
     *                      or {@code "denylist"}), or {@code null}
     *                      for non-status messages
     * @return the meta node, or {@code null} if no metadata attributes
     *         are applicable
     *
     * @apiNote WAWebSendMsgMetaNode.genMetaNode: full variant with all
     * attributes.
     * WAWebEncryptAndSendStatusMsg: includes
     * {@code status_setting} in meta node for status stanzas.
     */
    public Node buildChat(Jid chatJid, MessageContainer container, String statusSetting) {
        var message = container.content();

        // WAWebSendMsgMetaNode: determine polltype
        var polltype = switch (message) {
            case PollCreationMessage _ -> "creation";
            case PollUpdateMessage _ -> "vote";
            case PollResultSnapshotMessage _ -> "result_snapshot";
            default -> null;
        };

        // WAWebSendMsgMetaNode: determine event_type
        var eventType = resolveEventType(message);

        // WAWebSendMsgMetaNode: determine view_once
        var viewOnce = container.futureProofContentType() == FutureProofMessageType.VIEW_ONCE ? "true" : null;

        // WAWebSendMsgMetaNode.getOriginAttribute: origin = lidOriginType when PNH_CTWA
        var origin = resolveOrigin(chatJid);

        // WAWebSendMsgMetaNode: sender_intent = "hosted" when appendHostedSenderIntent
        var senderIntent = isHostedRecipient(chatJid) ? "hosted" : null;

        // WAWebSendMsgMetaNode: determine appdata
        // "default" for peer messages, "member_tag" for group member label changes
        String appdata = null;
        if (message instanceof ProtocolMessage pm
                && pm.type().orElse(null) == ProtocolMessage.Type.GROUP_MEMBER_LABEL_CHANGE) {
            appdata = "member_tag";
        }

        // WAWebSendMsgCreateFanoutStanza: resolve thread context from
        // deviceInfo.threadId - AI threads set conversation_thread_id,
        // non-AI threads set thread_msg_id and thread_msg_sender_jid
        String threadMsgId = null;
        Jid threadMsgSenderJid = null;
        String hashedAiThreadId = null;
        var deviceInfo = container.messageContextInfo().orElse(null);
        if (deviceInfo != null) {
            var threads = deviceInfo.threadId();
            if (threads != null) {
                for (var thread : threads) {
                    var threadKey = thread.threadKey();
                    var keyId = threadKey.flatMap(MessageKey::id)
                            .filter(entry -> !entry.isEmpty());
                    if (keyId.isEmpty()) {
                        continue;
                    }
                    if (thread.threadType().orElse(null) == MessageThreadId.ThreadType.AI_THREAD) {
                        hashedAiThreadId = keyId.get();
                    } else {
                        threadMsgId = keyId.get();
                        threadMsgSenderJid = threadKey.flatMap(MessageKey::senderJid)
                                .orElse(null);
                    }
                    break;
                }
            }
        }

        // WAWebSendMsgMetaNode: only emit if any attribute is set
        if (polltype == null && eventType == null && viewOnce == null
                && origin == null && appdata == null && senderIntent == null
                && threadMsgId == null && hashedAiThreadId == null
                && statusSetting == null) {
            return null;
        }

        return new NodeBuilder()
                .description("meta")
                .attribute("origin", origin)
                .attribute("sender_intent", senderIntent)
                .attribute("polltype", polltype)
                .attribute("event_type", eventType)
                .attribute("view_once", viewOnce)
                .attribute("appdata", appdata)
                .attribute("status_setting", statusSetting)
                .attribute("thread_msg_id", threadMsgId)
                .attribute("thread_msg_sender_jid", threadMsgSenderJid)
                .attribute("conversation_thread_id", hashedAiThreadId)
                .build();
    }

    /**
     * Resolves the {@code origin} meta attribute for LID chats.
     *
     * @apiNote WAWebSendMsgMetaNode.getOriginAttribute: returns the
     * lidOriginType when to.isLid() and lidOriginType is PNH_CTWA.
     */
    private String resolveOrigin(Jid chatJid) {
        if (!chatJid.hasLidServer()) {
            return null;
        }

        return store.findChatByJid(chatJid)
                .flatMap(Chat::lidOriginType)
                .filter("ctwa"::equals)
                .orElse(null);
    }

    /**
     * Returns whether the recipient is a hosted business account.
     *
     * @apiNote WAWebSendMsgMetaNode: appendHostedSenderIntent is true
     * when the recipient has a verified business name with host_storage
     * set (indicating a hosted account).
     */
    private boolean isHostedRecipient(Jid chatJid) {
        return store.findVerifiedBusinessName(chatJid)
                .map(vbn -> vbn.hostStorage().isPresent())
                .orElse(false);
    }

    /**
     * Resolves the {@code event_type} attribute from the message content.
     *
     * @apiNote WAWebSendMsgMetaNode: eventMessage → "creation",
     * encEventResponseMessage → "response",
     * secretEncryptedMessage with EVENT_EDIT → "edit".
     * WAWebHandleMsgCommon.EVENT_TYPES: the possible values.
     */
    private static String resolveEventType(Message message) {
        return switch (message) {
            case EventMessage _ -> "creation";
            case SecretEncMessage s
                    when s.secretEncType().orElse(null) == SecretEncMessage.SecretEncType.EVENT_EDIT -> "edit";
            case EncEventResponseMessage _ -> "response";
            default -> null;
        };
    }

    /**
     * Builds a {@code <meta questiontype="question">} node for
     * newsletter question messages.
     *
     * @return the meta node
     *
     * @apiNote WASmaxOutMessagePublishQuestionTypeQuestionMixin:
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
     * @apiNote WASmaxOutMessagePublishQuestionTypeReplyMixin:
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
     * @apiNote WASmaxOutMessagePublishQuestionTypeResponseMixin:
     * {@code <meta questiontype="response">}
     */
    public static Node buildNewsletterQuestionResponse() {
        return new NodeBuilder()
                .description("meta")
                .attribute("questiontype", "response")
                .build();
    }
}
