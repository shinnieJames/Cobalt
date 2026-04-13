package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the {@code <ctwa_attribution>} stanza child node for
 * Click-to-WhatsApp attribution tracking.
 *
 * <p>When a user opens a chat via a CTWA ad link, WhatsApp records the
 * entry point (link type, partner name, auth status).  The first message
 * sent in that chat includes this attribution data so the server can
 * attribute the conversation to the ad.
 *
 * <p>External entry points are stored in an in-memory map keyed by chat
 * JID string, mirroring how WA Web stores them in
 * {@code WAWebUserPrefsStore} under the {@code EXTERNAL_ENTRY_POINT} key.
 * Entry points expire after one week.
 *
 * @implNote WAWebSendMsgCtwaAttributionNode.getCtwaAttributionNode:
 * checks {@code isCtxLoggingEnabled}, retrieves the external entry point
 * from prefs, evaluates the first-message logging policy, builds a JSON
 * payload ({@code lt}, {@code s}, {@code p}), encodes to UTF-8, and wraps
 * in a {@code <ctwa_attribution>} node.
 * WAWebExternalCtxConfig: controls whether logging is enabled and
 * which chats qualify (first message only, or all messages).
 * WAWebExternalEntryPointPrefs: stores/retrieves per-chat entry points.
 */
public final class CtwaAttributionStanza {

    /**
     * In-memory store of external entry points keyed by chat JID string.
     *
     * @implNote WAWebExternalEntryPointPrefs: persisted via
     * {@code WAWebUserPrefsStore.setUser(KEYS.EXTERNAL_ENTRY_POINT, ...)}.
     * Cobalt uses a concurrent map as a direct equivalent.
     */
    private final ConcurrentHashMap<String, ExternalEntryPoint> entryPoints;

    /**
     * The WhatsApp store, used for chat lookup and message counting.
     */
    private final WhatsAppStore store;

    /**
     * The AB props service, used to check CTWA logging configuration.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new CTWA attribution stanza builder.
     *
     * @param store          the WhatsApp store
     * @param abPropsService the AB props service
     * @implNote WAWebSendMsgCtwaAttributionNode: depends on
     * {@code WAWebExternalCtxConfig} (AB props) and
     * {@code WAWebExternalEntryPointPrefs} (user prefs storage).
     */
    public CtwaAttributionStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.entryPoints = new ConcurrentHashMap<>();
    }

    /**
     * Saves an external entry point for the given chat JID.
     *
     * <p>Any previously stored entry point for the same chat is replaced.
     * Expired entries for other chats are pruned during save.
     *
     * @param chatJid      the chat JID
     * @param entryPoint   the entry point to save
     * @implNote WAWebExternalEntryPointPrefs.saveExternalEntryPoint:
     * adds the entry point to the map, prunes expired entries, and
     * persists via {@code WAWebUserPrefsStore}.
     */
    public void saveEntryPoint(Jid chatJid, ExternalEntryPoint entryPoint) {
        // WAWebExternalEntryPointPrefs.saveExternalEntryPoint
        entryPoints.put(chatJid.toString(), entryPoint);
        pruneExpired();
    }

    /**
     * Deletes the external entry point for the given chat JID.
     *
     * @param chatJid the chat JID
     * @implNote WAWebExternalEntryPointPrefs.deleteExternalEntryPoint:
     * removes the entry from the map and persists.
     */
    public void deleteEntryPoint(Jid chatJid) {
        // WAWebExternalEntryPointPrefs.deleteExternalEntryPoint
        entryPoints.remove(chatJid.toString());
    }

    /**
     * Retrieves the external entry point for the given chat JID,
     * returning empty if none exists or the entry has expired.
     *
     * @param chatJid the chat JID
     * @return the entry point, or empty
     * @implNote WAWebExternalEntryPointPrefs.getExternalEntryPoint:
     * returns {@code null} if not found or expired.
     */
    public Optional<ExternalEntryPoint> getEntryPoint(Jid chatJid) {
        // WAWebExternalEntryPointPrefs.getExternalEntryPoint
        var entry = entryPoints.get(chatJid.toString());
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /**
     * Builds the {@code <ctwa_attribution>} node for the given chat,
     * if CTWA attribution is applicable.
     *
     * <p>Returns {@code null} when:
     * <ul>
     *   <li>The chat is {@code null}</li>
     *   <li>CTWA context logging is disabled via AB prop</li>
     *   <li>No external entry point exists for the chat</li>
     *   <li>The first-message logging policy excludes this chat</li>
     * </ul>
     *
     * @param chatJid the chat JID being sent to
     * @return the ctwa attribution node, or {@code null} if not applicable
     * @implNote WAWebSendMsgCtwaAttributionNode.getCtwaAttributionNode:
     * checks {@code isCtxLoggingEnabled()}, retrieves entry point via
     * {@code getExternalEntryPoint(e.id)}, evaluates
     * {@code shouldLogFirstMessage(chat, partnerName)}, then builds JSON
     * ({@code lt}, {@code s}, {@code p}), encodes to UTF-8 bytes, and wraps
     * via {@code makeCtwaAttributionCtwaAttribution}.
     */
    public Node build(Jid chatJid) {
        // WAWebSendMsgCtwaAttributionNode.getCtwaAttributionNode: if(e == null ...)
        if (chatJid == null) {
            return null;
        }

        // WAWebExternalCtxConfig.isCtxLoggingEnabled
        var isCtxLoggingEnabled = abPropsService.getBool(ABProp.EXTERNAL_CTX_AUTHORISE_WA_CHAT);
        if (!isCtxLoggingEnabled) {
            return null;
        }

        // WAWebExternalEntryPointPrefs.getExternalEntryPoint
        var entryPoint = getEntryPoint(chatJid).orElse(null);
        if (entryPoint == null) {
            return null;
        }

        // WAWebSendMsgCtwaAttributionNode: shouldLogFirstMessage check
        var chat = store.findChatByJid(chatJid).orElse(null);
        if (!shouldLogFirstMessage(chat, entryPoint.partnerName())) {
            return null;
        }

        // WAWebSendMsgCtwaAttributionNode: build JSON payload
        // Preserve insertion order for consistent serialisation
        var json = new LinkedHashMap<String, Object>();
        // WAWebSendMsgCtwaAttributionNode: n.lt = "WEB_" + t.deepLinkType
        json.put("lt", "WEB_" + entryPoint.deepLinkType());
        // WAWebSendMsgCtwaAttributionNode: if (!t.authSuccess) n.s = 0
        if (!entryPoint.authSuccess()) {
            json.put("s", 0);
        }
        // WAWebSendMsgCtwaAttributionNode: if (t.partnerName != null) n.p = t.partnerName
        if (entryPoint.partnerName() != null) {
            json.put("p", entryPoint.partnerName());
        }

        // WAWebSendMsgCtwaAttributionNode: new TextEncoder().encode(JSON.stringify(n))
        var jsonBytes = serializeJson(json).getBytes(StandardCharsets.UTF_8);

        // WASmaxOutMessagePublishCtwaAttributionMixin.makeCtwaAttributionCtwaAttribution:
        // smax("ctwa_attribution", null, ctwaAttributionElementValue)
        return new NodeBuilder()
                .description("ctwa_attribution")
                .content(jsonBytes)
                .build();
    }

    /**
     * Determines whether the first-message logging policy allows
     * attribution for this chat.
     *
     * <p>The policy is controlled by the
     * {@code external_ctx_authorise_existing_chats} AB prop:
     * <ul>
     *   <li>{@code 1} ("NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS"):
     *       log if a partner name exists OR the chat is new
     *       (has at most one non-system message)</li>
     *   <li>{@code 2} ("ALL_CHATS"): always log</li>
     *   <li>{@code 0} or default ("NEW_CHATS_ONLY"): log only if the
     *       chat is new</li>
     * </ul>
     *
     * @param chat        the chat, or {@code null} if not found
     * @param partnerName the partner name from the entry point, or
     *                    {@code null}
     * @return {@code true} if attribution should be included
     * @implNote WAWebSendMsgCtwaAttributionNode: function {@code s(t, n)}
     * delegates to {@code WAWebExternalCtxConfig.getFirstMessageLoggingOption()}
     * and {@code hasMultipleNonSystemMessages(chat)}.
     */
    private boolean shouldLogFirstMessage(Chat chat, String partnerName) {
        // WAWebExternalCtxConfig.getFirstMessageLoggingOption
        var loggingOption = abPropsService.getInt(ABProp.EXTERNAL_CTX_AUTHORISE_EXISTING_CHATS);
        return switch (loggingOption) {
            // WAWebExternalCtxConfig: e === 1 → "NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS"
            case 1 -> partnerName != null || !hasMultipleNonSystemMessages(chat);
            // WAWebExternalCtxConfig: e === 2 → "ALL_CHATS"
            case 2 -> true;
            // WAWebExternalCtxConfig: default → "NEW_CHATS_ONLY"
            default -> !hasMultipleNonSystemMessages(chat);
        };
    }

    /**
     * Returns whether the chat contains more than one non-system,
     * non-send-failure message.
     *
     * <p>A chat with zero or one real (user-sent/received) message is
     * considered "new" for attribution purposes.
     *
     * @param chat the chat, or {@code null}
     * @return {@code true} if the chat has more than one qualifying message
     * @implNote WAWebSendMsgCtwaAttributionNode: function {@code e(e)}
     * iterates all messages across all CMCs, filtering out
     * {@code SYSTEM_MESSAGE_TYPES} and {@code isSendFailure === true},
     * and returns {@code true} if the count exceeds 1.
     */
    private boolean hasMultipleNonSystemMessages(Chat chat) {
        // WAWebSendMsgCtwaAttributionNode: function e(e)
        if (chat == null) {
            return false;
        }

        var count = 0;
        for (var msg : chat.messages()) {
            // WAWebMsgType.SYSTEM_MESSAGE_TYPES check:
            // system messages are those with stubType (notification-type messages)
            // WAWebSendMsgCtwaAttributionNode: also skips isSendFailure === true
            if (isSystemMessage(msg)) {
                continue;
            }

            count++;
            if (count > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a message is a system message type, corresponding
     * to WA Web's {@code SYSTEM_MESSAGE_TYPES} classification.
     *
     * <p>System message types include: broadcast_notification, call_log,
     * debug, e2e_notification, gp2, newsletter_notification, notification,
     * notification_template, protocol, pinned_message,
     * poll_add_option_decrypted.
     *
     * @param msg the message to check
     * @return {@code true} if the message is a system type
     * @implNote WAWebMsgType.SYSTEM_MESSAGE_TYPES: defines the set of
     * message types considered "system". Cobalt approximates this by
     * checking for {@code stubType} presence (which marks notification
     * and system messages) and protocol messages.
     */
    private boolean isSystemMessage(ChatMessageInfo msg) {
        // WAWebMsgType.SYSTEM_MESSAGE_TYPES includes notification types
        // (which in Cobalt have a non-null stubType) and protocol messages
        if (msg.messageStubType().isPresent()) {
            return true;
        }

        // WAWebMsgType.SYSTEM_MESSAGE_TYPES includes "protocol"
        var content = msg.message();
        if (content == null) {
            return true;
        }

        return content.content() instanceof ProtocolMessage;
    }

    /**
     * Removes expired entry points from the in-memory map.
     *
     * @implNote WAWebExternalEntryPointPrefs: function {@code c(t)}
     * iterates all entry points and removes those older than one week.
     */
    private void pruneExpired() {
        // WAWebExternalEntryPointPrefs: Object.keys(t).forEach(...)
        entryPoints.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Serialises a map to a minimal JSON string.
     *
     * <p>Handles {@link String}, {@link Number}, and {@link Boolean} values.
     * This avoids pulling in a JSON library for a single use case.
     *
     * @param map the key-value pairs to serialise
     * @return the JSON string
     * @implNote WAWebSendMsgCtwaAttributionNode: uses
     * {@code JSON.stringify(n)} on the payload object.
     */
    private String serializeJson(LinkedHashMap<String, Object> map) {
        // WAWebSendMsgCtwaAttributionNode: JSON.stringify(n)
        var sb = new StringBuilder("{");
        var first = true;
        for (var entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append('"').append(':');
            var value = entry.getValue();
            if (value instanceof String s) {
                sb.append('"').append(s).append('"');
            } else {
                sb.append(value);
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
