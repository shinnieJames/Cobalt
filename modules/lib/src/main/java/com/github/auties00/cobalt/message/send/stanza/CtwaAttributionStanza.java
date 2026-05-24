package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the optional {@code <ctwa_attribution>} child of an outgoing
 * {@code <message>} stanza for Click-to-WhatsApp ad-attributed sends, and
 * owns the in-memory {@link ExternalEntryPoint} cache that drives it.
 *
 * @apiNote
 * When a user opens a chat through a CTWA ad link, the deep-link
 * parameters are saved to a per-chat {@link ExternalEntryPoint}. The first
 * messages sent in that chat carry a {@code <ctwa_attribution>} child
 * encoding the JSON {@code {"lt": "WEB_<type>", "s": 0?, "p": "<partner>"?}}
 * so the server attributes the conversation back to the advertising
 * campaign. The save/get/delete methods mirror WA Web's
 * {@code WAWebExternalEntryPointPrefs} module; the AB-prop gating helpers
 * mirror {@code WAWebExternalCtxConfig}.
 *
 * @implNote
 * This implementation backs the entry-point store with a
 * {@link ConcurrentHashMap} keyed by chat JID string, matching how WA Web
 * stores the same map under {@code WAWebUserPrefsKeys.KEYS.EXTERNAL_ENTRY_POINT}.
 * Persistence across restarts is not implemented and the WA-Web JSON
 * serialisation order is reproduced exactly via {@link LinkedHashMap} so
 * the {@code <ctwa_attribution>} byte content matches the live wire.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgCtwaAttributionNode")
@WhatsAppWebModule(moduleName = "WAWebExternalEntryPointPrefs")
@WhatsAppWebModule(moduleName = "WAWebExternalCtxConfig")
public final class CtwaAttributionStanza {
    /**
     * The in-memory map of recorded entry points keyed by chat JID string.
     */
    private final ConcurrentHashMap<String, ExternalEntryPoint> entryPoints;

    /**
     * The {@link WhatsAppStore} used to look up the chat and count its
     * messages for the first-message logging policy.
     */
    private final WhatsAppStore store;

    /**
     * The {@link ABPropsService} consulted for the CTWA-related props.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a stanza builder bound to the given services.
     *
     * @apiNote
     * Constructed once per client; the entry-point cache lives on this
     * instance.
     *
     * @param store          the {@link WhatsAppStore}
     * @param abPropsService the {@link ABPropsService}
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CtwaAttributionStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.entryPoints = new ConcurrentHashMap<>();
    }

    /**
     * Records an external entry point for the given chat, stamping the
     * current instant as the added time.
     *
     * @apiNote
     * Convenience overload that builds the {@link ExternalEntryPoint}
     * internally. Callers that already hold a record (e.g. when replaying
     * historical attribution) should use the two-argument form.
     *
     * @param chatJid      the chat {@link Jid}
     * @param deepLinkType the deep-link type token
     * @param authSuccess  whether the ad-flow authentication succeeded
     * @param partnerName  the partner or advertiser name, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "saveExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void saveEntryPoint(Jid chatJid, String deepLinkType, boolean authSuccess, String partnerName) {
        var addedTime = Instant.now();
        var entryPoint = new ExternalEntryPoint(deepLinkType, authSuccess, partnerName, addedTime);
        saveEntryPoint(chatJid, entryPoint);
    }

    /**
     * Records an already-built external entry point for the given chat,
     * pruning expired siblings.
     *
     * @apiNote
     * Replaces any previous entry for the same chat. Pruning of expired
     * entries from the in-memory map happens on every save, mirroring WA
     * Web's {@code c(t)} persist helper.
     *
     * @param chatJid    the chat {@link Jid}
     * @param entryPoint the {@link ExternalEntryPoint} to save
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "saveExternalEntryPoint",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void saveEntryPoint(Jid chatJid, ExternalEntryPoint entryPoint) {
        entryPoints.put(chatJid.toString(), entryPoint);
        pruneExpired();
    }

    /**
     * Removes the external entry point recorded for the given chat.
     *
     * @apiNote
     * Idempotent: removing an absent entry is a no-op. WA Web also persists
     * the resulting map back to {@code WAWebUserPrefsStore}; the
     * in-memory-only Cobalt port skips that write because the map is the
     * source of truth.
     *
     * @param chatJid the chat {@link Jid}
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "deleteExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void deleteEntryPoint(Jid chatJid) {
        entryPoints.remove(chatJid.toString());
    }

    /**
     * Returns the external entry point recorded for the given chat, if any
     * non-expired entry exists.
     *
     * @apiNote
     * Expired entries are not pruned here; this method merely declines to
     * return them. Pruning is performed by
     * {@link #saveEntryPoint(Jid, ExternalEntryPoint)} on the next save.
     *
     * @param chatJid the chat {@link Jid}
     * @return the {@link ExternalEntryPoint}, or empty
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "getExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<ExternalEntryPoint> getEntryPoint(Jid chatJid) {
        var entry = entryPoints.get(chatJid.toString());
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /**
     * Builds the {@code <ctwa_attribution>} child for the given chat, or
     * {@code null} when CTWA attribution does not apply.
     *
     * @apiNote
     * Four gates suppress the node: {@code chatJid} is {@code null}, CTWA
     * logging is disabled via the {@code external_ctx_authorise_wa_chat}
     * AB prop, no entry point is recorded for the chat, or the
     * {@link #getFirstMessageLoggingOption() first-message logging policy}
     * excludes this chat (typically because it already has multiple
     * non-system messages). When emitted, the child carries the UTF-8 JSON
     * payload as its byte content with no attributes.
     *
     * @implNote
     * This implementation uses {@link LinkedHashMap} so the
     * {@code {lt, s?, p?}} key order matches WA Web's
     * {@code JSON.stringify} output byte-for-byte, which the server treats
     * as a content tag.
     *
     * @param chatJid the recipient chat {@link Jid}
     * @return the {@code <ctwa_attribution>} {@link Node}, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node build(Jid chatJid) {
        if (chatJid == null) {
            return null;
        }

        if (!isCtxLoggingEnabled()) {
            return null;
        }

        var entryPoint = getEntryPoint(chatJid).orElse(null);
        if (entryPoint == null) {
            return null;
        }

        var chat = store.findChatByJid(chatJid).orElse(null);
        if (!shouldLogFirstMessage(chat, entryPoint.partnerName())) {
            return null;
        }

        var json = new LinkedHashMap<String, Object>();
        json.put("lt", "WEB_" + entryPoint.deepLinkType());
        if (!entryPoint.authSuccess()) {
            json.put("s", 0);
        }
        if (entryPoint.partnerName() != null) {
            json.put("p", entryPoint.partnerName());
        }

        var jsonBytes = serializeJson(json).getBytes(StandardCharsets.UTF_8);

        return new NodeBuilder()
                .description("ctwa_attribution")
                .content(jsonBytes)
                .build();
    }

    /**
     * Returns whether CTWA context logging is currently enabled for this
     * client.
     *
     * @apiNote
     * Gates {@link #build(Jid)} emission and the deletion of pending entry
     * points on send. Backed by the {@link ABProp#EXTERNAL_CTX_AUTHORISE_WA_CHAT}
     * AB prop.
     *
     * @return {@code true} when CTWA context logging is enabled
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "isCtxLoggingEnabled",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isCtxLoggingEnabled() {
        return abPropsService.getBool(ABProp.EXTERNAL_CTX_AUTHORISE_WA_CHAT);
    }

    /**
     * Returns the active first-message logging policy for CTWA attribution.
     *
     * @apiNote
     * The {@link ABProp#EXTERNAL_CTX_AUTHORISE_EXISTING_CHATS} prop maps an
     * integer to one of three policy labels. WA Web throws on an unknown
     * value; Cobalt narrows to the safest default ({@link FirstMessageLoggingOption#NEW_CHATS_ONLY})
     * so a fresh AB-prop value will never produce a hard failure.
     *
     * @implNote
     * This implementation models the three labels as a {@link Enum} so the
     * downstream switch in {@link #shouldLogFirstMessage(Chat, String)} is
     * exhaustive at compile time; WA Web's string-labels approach is
     * checked at runtime.
     *
     * @return the resolved {@link FirstMessageLoggingOption}, never
     *         {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "getFirstMessageLoggingOption",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public FirstMessageLoggingOption getFirstMessageLoggingOption() {
        var loggingOption = abPropsService.getInt(ABProp.EXTERNAL_CTX_AUTHORISE_EXISTING_CHATS);
        return switch (loggingOption) {
            case 1 -> FirstMessageLoggingOption.NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS;
            case 2 -> FirstMessageLoggingOption.ALL_CHATS;
            default -> FirstMessageLoggingOption.NEW_CHATS_ONLY;
        };
    }

    /**
     * Returns the list of URL query-parameter names that may carry a CTWA
     * deep-link token.
     *
     * @apiNote
     * The base list comes from the comma-separated
     * {@link ABProp#EXTERNAL_CTX_URL_PARAM_NAMES} prop and defaults to
     * {@code ["partnertoken"]}. When
     * {@link ABProp#EXTERNAL_CTX_FOA_LOGGING} equals {@code 1}, a trailing
     * {@code "token"} entry is appended for the First-Open-Attribution
     * surface.
     * {@snippet :
     *     // example AB-prop value
     *     // "partnertoken,token,utm_token"
     *     var names = stanza.getExternalCtxUrlParamNames();
     * }
     *
     * @return an unmodifiable {@link List} of query-parameter names
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "getExternalCtxUrlParamNames",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<String> getExternalCtxUrlParamNames() {
        var rawValue = abPropsService.getString(ABProp.EXTERNAL_CTX_URL_PARAM_NAMES);
        if (rawValue == null) {
            rawValue = "";
        }

        var names = new ArrayList<String>();
        for (var token : rawValue.split(",")) {
            var trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }

        if (names.isEmpty()) {
            names.add(PARTNER_TOKEN_PARAM);
        }

        if (isFoaLoggingEnabled()) {
            names.add(TOKEN_PARAM);
        }

        return List.copyOf(names);
    }

    /**
     * The default URL query parameter name carrying the CTWA partner
     * deep-link token.
     *
     * @apiNote
     * Used as the sole fallback when
     * {@link ABProp#EXTERNAL_CTX_URL_PARAM_NAMES} is missing or contains
     * only blanks. Mirrors WA Web's local {@code e} in
     * {@code WAWebExternalCtxConfig}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "e",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String PARTNER_TOKEN_PARAM = "partnertoken";

    /**
     * The additional URL query parameter name appended to the deep-link
     * list when the First-Open-Attribution feature is enabled.
     *
     * @apiNote
     * Mirrors WA Web's local {@code s} in {@code WAWebExternalCtxConfig};
     * appended only when {@link #isFoaLoggingEnabled()} returns
     * {@code true}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "s",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String TOKEN_PARAM = "token";

    /**
     * Returns whether the First-Open-Attribution logging feature is
     * enabled.
     *
     * @apiNote
     * Backed by {@link ABProp#EXTERNAL_CTX_FOA_LOGGING}, which is compared
     * to the literal {@code 1} (any other value, including {@code 0} and
     * unknown values, counts as disabled).
     *
     * @return {@code true} when FOA logging is enabled
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "c",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isFoaLoggingEnabled() {
        return abPropsService.getInt(ABProp.EXTERNAL_CTX_FOA_LOGGING) == 1;
    }

    /**
     * Returns whether the active first-message logging policy permits
     * emitting CTWA attribution for the given chat.
     *
     * @apiNote
     * The policy is a tri-state:
     * {@link FirstMessageLoggingOption#NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS}
     * permits when the entry point has a partner name OR the chat is new,
     * {@link FirstMessageLoggingOption#ALL_CHATS} permits unconditionally,
     * and {@link FirstMessageLoggingOption#NEW_CHATS_ONLY} permits only
     * for new chats. A chat is "new" when it contains at most one
     * non-system, non-send-failure message.
     *
     * @param chat        the {@link Chat}, or {@code null} when not found
     * @param partnerName the partner name from the
     *                    {@link ExternalEntryPoint}, or {@code null}
     * @return {@code true} when attribution should be emitted
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldLogFirstMessage(Chat chat, String partnerName) {
        var loggingOption = getFirstMessageLoggingOption();
        return switch (loggingOption) {
            case NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS ->
                    partnerName != null || !hasMultipleNonSystemMessages(chat);
            case ALL_CHATS -> true;
            case NEW_CHATS_ONLY -> !hasMultipleNonSystemMessages(chat);
        };
    }

    /**
     * The three policies the
     * {@link ABProp#EXTERNAL_CTX_AUTHORISE_EXISTING_CHATS} prop selects
     * between when deciding whether the next outgoing message in a chat
     * receives a CTWA attribution tag.
     *
     * @apiNote
     * Mirrors the three string labels returned by
     * {@code WAWebExternalCtxConfig.getFirstMessageLoggingOption}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "getFirstMessageLoggingOption",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public enum FirstMessageLoggingOption {
        /**
         * Attributes every chat with at most one non-system message, plus
         * existing chats that already carry a partner link.
         */
        NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS,

        /**
         * Attributes every chat regardless of message count.
         */
        ALL_CHATS,

        /**
         * Attributes only chats with at most one non-system message (the
         * default policy).
         */
        NEW_CHATS_ONLY
    }

    /**
     * Returns whether the chat contains more than one non-system message.
     *
     * @apiNote
     * A chat with zero or one non-system, non-send-failure message is
     * considered "new" for CTWA attribution; only the first such message
     * carries the attribution tag.
     *
     * @implNote
     * This implementation streams the chat's messages, filters out system
     * types via {@link #isSystemMessage(ChatMessageInfo)}, skips one entry,
     * and tests whether a second qualifying message exists. The stream is
     * closed via try-with-resources because the underlying {@link Chat}
     * messages stream may hold a snapshot iterator.
     *
     * @param chat the {@link Chat}, or {@code null}
     * @return {@code true} when more than one qualifying message exists
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean hasMultipleNonSystemMessages(Chat chat) {
        if (chat == null) {
            return false;
        }

        try (var stream = chat.messages()) {
            return stream.filter(msg -> !isSystemMessage(msg))
                    .skip(1)
                    .findAny()
                    .isPresent();
        }
    }

    /**
     * Returns whether the given message belongs to WA Web's
     * {@code SYSTEM_MESSAGE_TYPES} classification.
     *
     * @apiNote
     * System messages (broadcast notifications, call logs, debug messages,
     * E2E notifications, group notifications, newsletter notifications,
     * generic notifications, notification templates, protocol messages,
     * pinned messages, decrypted poll-add-option entries) are excluded
     * from the "new chat" count so notification-only conversations still
     * receive CTWA attribution.
     *
     * @implNote
     * This implementation reduces the WA-Web type list to a simpler
     * predicate: a message with a {@code messageStubType}, a {@code null}
     * container, or a {@link ProtocolMessage} payload counts as system.
     * That set covers every notification type emitted by WA Web's send
     * pipeline.
     *
     * @param msg the {@link ChatMessageInfo} to classify
     * @return {@code true} when the message is a system type
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgType", exports = "SYSTEM_MESSAGE_TYPES",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isSystemMessage(ChatMessageInfo msg) {
        if (msg.messageStubType().isPresent()) {
            return true;
        }

        var content = msg.message();
        if (content == null) {
            return true;
        }

        return content.content() instanceof ProtocolMessage;
    }

    /**
     * Removes expired entry points from the in-memory map.
     *
     * @apiNote
     * Invoked on every save; matches the pruning step inside WA Web's
     * {@code c(t)} persist helper. WA Web also writes the pruned map back
     * to {@code WAWebUserPrefsStore}; Cobalt skips that step because the
     * map is the source of truth.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "saveExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void pruneExpired() {
        entryPoints.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Serialises a string-keyed map to a minimal JSON object string.
     *
     * @apiNote
     * Internal helper for {@link #build(Jid)} so the encoded
     * {@code <ctwa_attribution>} content matches WA Web's
     * {@code JSON.stringify} output byte-for-byte. Handles
     * {@link String}, {@link Number}, and {@link Boolean} values only;
     * other types are coerced via {@link Object#toString()}.
     *
     * @implNote
     * This implementation escapes string values exactly like
     * {@code JSON.stringify}: backslash, double quote, the five
     * two-character control shortcuts ({@code \b}, {@code \f}, {@code \n},
     * {@code \r}, {@code \t}), and any other character below
     * {@code U+0020} as {@code \\u00XX}. Higher Unicode code points are
     * emitted verbatim, matching the {@code TextEncoder().encode(JSON.stringify(...))}
     * pipeline used on the WA Web side.
     *
     * @param map the entries to serialise
     * @return the JSON object string
     */
    private String serializeJson(LinkedHashMap<String, Object> map) {
        var sb = new StringBuilder("{");
        var first = true;
        for (var entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendJsonString(sb, entry.getKey());
            sb.append(':');
            var value = entry.getValue();
            if (value instanceof String s) {
                appendJsonString(sb, s);
            } else {
                sb.append(value);
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Appends a JSON-quoted, properly escaped string to the buffer.
     *
     * @apiNote
     * Internal helper consumed exclusively by {@link #serializeJson}; the
     * escaping table is JSON-spec strict so the encoded bytes match
     * {@code JSON.stringify}.
     *
     * @param sb    the {@link StringBuilder} to append to
     * @param value the source string to encode
     */
    private static void appendJsonString(StringBuilder sb, String value) {
        sb.append('"');
        for (var i = 0; i < value.length(); i++) {
            var ch = value.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }
}
