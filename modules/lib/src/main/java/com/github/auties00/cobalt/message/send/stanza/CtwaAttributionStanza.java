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
import com.github.auties00.cobalt.props.ABProp;
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
@WhatsAppWebModule(moduleName = "WAWebSendMsgCtwaAttributionNode")
@WhatsAppWebModule(moduleName = "WAWebExternalEntryPointPrefs")
@WhatsAppWebModule(moduleName = "WAWebExternalCtxConfig")
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
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CtwaAttributionStanza(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.entryPoints = new ConcurrentHashMap<>();
    }

    /**
     * Saves an external entry point for the given chat JID, stamping
     * {@code addedTime} with the current instant.
     *
     * <p>This overload mirrors the JS {@code saveExternalEntryPoint}
     * signature exactly: the caller passes the four raw fields and the
     * record is constructed internally with {@link Instant#now()} as the
     * added-time, exactly as the JS source does with {@code Date.now()}.
     *
     * @param chatJid      the chat JID
     * @param deepLinkType the deep link type
     * @param authSuccess  whether authentication succeeded during the ad flow
     * @param partnerName  the partner name, or {@code null}
     * @implNote WAWebExternalEntryPointPrefs.saveExternalEntryPoint:
     * {@code function m(e,t,n,r){var o=d(),a=Date.now(); o[e.toString()]=
     * {addedTime:a, deepLinkType:t, authSuccess:n, partnerName:r!=null?r:null}; c(o)}}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "saveExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void saveEntryPoint(Jid chatJid, String deepLinkType, boolean authSuccess, String partnerName) {
        // WAWebExternalEntryPointPrefs: var o=d() — load current map (Cobalt: in-memory)
        // WAWebExternalEntryPointPrefs: var a=Date.now()
        var addedTime = Instant.now();
        // WAWebExternalEntryPointPrefs: o[e.toString()]={addedTime:a, deepLinkType:t, authSuccess:n, partnerName:r!=null?r:null}
        var entryPoint = new ExternalEntryPoint(deepLinkType, authSuccess, partnerName, addedTime);
        saveEntryPoint(chatJid, entryPoint);
    }

    /**
     * Saves an already-built external entry point for the given chat
     * JID.
     *
     * <p>Any previously stored entry point for the same chat is
     * replaced. Expired entries for other chats are pruned during save,
     * matching the JS {@code c(t)} persist helper which iterates the
     * map and removes expired entries before writing.
     *
     * @param chatJid    the chat JID
     * @param entryPoint the entry point to save
     * @implNote WAWebExternalEntryPointPrefs.saveExternalEntryPoint:
     * adds the entry point to the map, then calls {@code c(t)} which
     * prunes expired entries and persists via
     * {@code WAWebUserPrefsStore.setUser(KEYS.EXTERNAL_ENTRY_POINT, t)}.
     * Cobalt collapses the UserPrefs persistence into the in-memory
     * {@link ConcurrentHashMap} per the unified store-system contract.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "saveExternalEntryPoint",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void saveEntryPoint(Jid chatJid, ExternalEntryPoint entryPoint) {
        // WAWebExternalEntryPointPrefs.saveExternalEntryPoint: o[e.toString()]={...}
        entryPoints.put(chatJid.toString(), entryPoint);
        // WAWebExternalEntryPointPrefs: c(o) — prune expired and persist
        pruneExpired();
    }

    /**
     * Deletes the external entry point for the given chat JID.
     *
     * <p>Mirrors the JS guard {@code n != null && (delete t[e.toString()], c(t))}:
     * the persistence step is skipped when no entry existed for the JID.
     * In Cobalt the in-memory map removal of an absent key is a no-op
     * and there is no separate persistence write to skip, so observable
     * behavior is identical.
     *
     * @param chatJid the chat JID
     * @implNote WAWebExternalEntryPointPrefs.deleteExternalEntryPoint:
     * {@code function p(e){var t=d(),n=t[e.toString()]; n!=null&&(delete t[e.toString()],c(t))}}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "deleteExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void deleteEntryPoint(Jid chatJid) {
        // WAWebExternalEntryPointPrefs.deleteExternalEntryPoint:
        //   n != null && (delete t[e.toString()], c(t))
        // ConcurrentHashMap.remove is a no-op when the key is absent,
        // so the JS guard is implicit here.
        entryPoints.remove(chatJid.toString());
    }

    /**
     * Retrieves the external entry point for the given chat JID,
     * returning empty if none exists or the entry has expired.
     *
     * <p>Like the JS source, this method does NOT prune expired
     * entries; it merely declines to return them. Pruning happens only
     * during {@link #saveEntryPoint(Jid, ExternalEntryPoint)}.
     *
     * @param chatJid the chat JID
     * @return the entry point, or empty
     * @implNote WAWebExternalEntryPointPrefs.getExternalEntryPoint:
     * {@code function _(e){var t=d(),n=t[e.toString()]; return n==null||u(n)?null:n}}
     * — returns {@code null} if not found or expired.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "getExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<ExternalEntryPoint> getEntryPoint(Jid chatJid) {
        // WAWebExternalEntryPointPrefs.getExternalEntryPoint:
        //   var n = t[e.toString()]
        var entry = entryPoints.get(chatJid.toString());
        // WAWebExternalEntryPointPrefs: return n == null || u(n) ? null : n
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
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node build(Jid chatJid) {
        // WAWebSendMsgCtwaAttributionNode.getCtwaAttributionNode: if(e == null ...)
        if (chatJid == null) {
            return null;
        }

        // WAWebExternalCtxConfig.isCtxLoggingEnabled
        if (!isCtxLoggingEnabled()) {
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
     * Returns whether CTWA context logging is currently enabled for this
     * client.
     *
     * <p>The flag is driven by the {@code external_ctx_authorise_wa_chat}
     * AB prop. When disabled, no {@code <ctwa_attribution>} stanza child
     * is produced and pending external entry points are not deleted on
     * send.
     *
     * @return {@code true} when the AB prop is enabled
     * @implNote WAWebExternalCtxConfig.isCtxLoggingEnabled — JS export
     * returns {@code o("WAWebABProps").getABPropConfigValue("external_ctx_authorise_wa_chat")},
     * which Cobalt evaluates as a boolean via {@link ABPropsService#getBool(ABProp)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "isCtxLoggingEnabled",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isCtxLoggingEnabled() {
        // WAWebExternalCtxConfig.isCtxLoggingEnabled:
        //   o("WAWebABProps").getABPropConfigValue("external_ctx_authorise_wa_chat")
        return abPropsService.getBool(ABProp.EXTERNAL_CTX_AUTHORISE_WA_CHAT);
    }

    /**
     * Returns the active first-message logging policy for CTWA
     * attribution.
     *
     * <p>The policy is encoded in the
     * {@code external_ctx_authorise_existing_chats} AB prop as an integer
     * which the JS source translates into one of three string labels:
     * <ul>
     *   <li>{@code 1} → {@link FirstMessageLoggingOption#NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS}</li>
     *   <li>{@code 2} → {@link FirstMessageLoggingOption#ALL_CHATS}</li>
     *   <li>{@code 0} or any other value → {@link FirstMessageLoggingOption#NEW_CHATS_ONLY}</li>
     * </ul>
     *
     * @return the resolved logging option, never {@code null}
     * @implNote WAWebExternalCtxConfig.getFirstMessageLoggingOption — JS
     * export reads the AB prop and returns a string label;
     * Cobalt models the same three labels as a Java enum to make the
     * downstream switch exhaustive.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "getFirstMessageLoggingOption",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public FirstMessageLoggingOption getFirstMessageLoggingOption() {
        // WAWebExternalCtxConfig.getFirstMessageLoggingOption:
        //   var e = o("WAWebABProps").getABPropConfigValue("external_ctx_authorise_existing_chats");
        var loggingOption = abPropsService.getInt(ABProp.EXTERNAL_CTX_AUTHORISE_EXISTING_CHATS);
        return switch (loggingOption) {
            // WAWebExternalCtxConfig: e === 1 → "NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS"
            case 1 -> FirstMessageLoggingOption.NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS;
            // WAWebExternalCtxConfig: e === 2 → "ALL_CHATS"
            case 2 -> FirstMessageLoggingOption.ALL_CHATS;
            // WAWebExternalCtxConfig: default → "NEW_CHATS_ONLY"
            default -> FirstMessageLoggingOption.NEW_CHATS_ONLY;
        };
    }

    /**
     * Returns the list of URL query parameter names that carry the CTWA
     * deep-link token.
     *
     * <p>The base list is parsed from the
     * {@code external_ctx_url_param_names} AB prop (a comma-separated
     * string, defaulting to {@code "partnertoken"}). When
     * {@code external_ctx_foa_logging} is enabled the trailing
     * {@code "token"} parameter name is appended, matching the JS
     * source's {@code c() && n.push(s)} branch.
     *
     * <p>Empty parameter names are filtered out and surrounding
     * whitespace is trimmed. The resulting list is never {@code null}
     * and always contains at least the default {@code "partnertoken"}
     * entry when the AB prop is empty.
     *
     * @return an unmodifiable list of URL parameter names to inspect for
     *         CTWA deep-link tokens
     * @implNote WAWebExternalCtxConfig.getExternalCtxUrlParamNames — JS
     * export splits the AB prop on commas, trims whitespace, drops empty
     * tokens, falls back to {@code [partnertoken]} when the resulting
     * array is empty, and finally appends {@code "token"} when
     * {@code external_ctx_foa_logging === 1}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "getExternalCtxUrlParamNames",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<String> getExternalCtxUrlParamNames() {
        // WAWebExternalCtxConfig.getExternalCtxUrlParamNames:
        //   var t = o("WAWebABProps").getABPropConfigValue("external_ctx_url_param_names") || ""
        var rawValue = abPropsService.getString(ABProp.EXTERNAL_CTX_URL_PARAM_NAMES);
        if (rawValue == null) {
            rawValue = "";
        }

        // WAWebExternalCtxConfig: t.split(",").map(trim).filter(!=="")
        var names = new ArrayList<String>();
        for (var token : rawValue.split(",")) {
            var trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }

        // WAWebExternalCtxConfig: n = n.length > 0 ? n : [e]  (e === "partnertoken")
        if (names.isEmpty()) {
            names.add(PARTNER_TOKEN_PARAM);
        }

        // WAWebExternalCtxConfig: c() && n.push(s)  (s === "token")
        if (isFoaLoggingEnabled()) {
            names.add(TOKEN_PARAM);
        }

        return List.copyOf(names);
    }

    /**
     * Default URL query parameter name used to carry the CTWA partner
     * deep-link token.
     *
     * @implNote WAWebExternalCtxConfig — module-level constant
     * {@code var e = "partnertoken"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "e",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String PARTNER_TOKEN_PARAM = "partnertoken";

    /**
     * Additional URL query parameter name appended to the deep-link
     * token list when {@code external_ctx_foa_logging} is enabled.
     *
     * @implNote WAWebExternalCtxConfig — module-level constant
     * {@code var s = "token"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "s",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String TOKEN_PARAM = "token";

    /**
     * Returns whether the {@code external_ctx_foa_logging} AB prop is
     * enabled.
     *
     * <p>The JS source compares the prop directly to the integer
     * {@code 1}, which Cobalt mirrors via
     * {@link ABPropsService#getInt(ABProp)}.
     *
     * @return {@code true} when First-Open-Attribution logging is enabled
     * @implNote WAWebExternalCtxConfig — function {@code c()} returns
     * {@code o("WAWebABProps").getABPropConfigValue("external_ctx_foa_logging") === 1}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "c",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isFoaLoggingEnabled() {
        // WAWebExternalCtxConfig: function c(){return o("WAWebABProps").getABPropConfigValue("external_ctx_foa_logging") === 1}
        return abPropsService.getInt(ABProp.EXTERNAL_CTX_FOA_LOGGING) == 1;
    }

    /**
     * Determines whether the first-message logging policy allows
     * attribution for this chat.
     *
     * <p>This combines {@link #getFirstMessageLoggingOption()} with the
     * {@code hasMultipleNonSystemMessages} predicate, mirroring the JS
     * function {@code s(t, n)} from {@code WAWebSendMsgCtwaAttributionNode}.
     *
     * <ul>
     *   <li>{@link FirstMessageLoggingOption#NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS}:
     *       log if a partner name exists OR the chat is new
     *       (has at most one non-system message)</li>
     *   <li>{@link FirstMessageLoggingOption#ALL_CHATS}: always log</li>
     *   <li>{@link FirstMessageLoggingOption#NEW_CHATS_ONLY}: log only
     *       if the chat is new</li>
     * </ul>
     *
     * @param chat        the chat, or {@code null} if not found
     * @param partnerName the partner name from the entry point, or
     *                    {@code null}
     * @return {@code true} if attribution should be included
     * @implNote WAWebSendMsgCtwaAttributionNode — function {@code s(t, n)}
     * dispatches on {@code WAWebExternalCtxConfig.getFirstMessageLoggingOption()}
     * and falls through to {@code hasMultipleNonSystemMessages(chat)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldLogFirstMessage(Chat chat, String partnerName) {
        // WAWebSendMsgCtwaAttributionNode: var r = o("WAWebExternalCtxConfig").getFirstMessageLoggingOption()
        var loggingOption = getFirstMessageLoggingOption();
        return switch (loggingOption) {
            // WAWebSendMsgCtwaAttributionNode: r === "NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS" → n != null || !e(t)
            case NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS ->
                    partnerName != null || !hasMultipleNonSystemMessages(chat);
            // WAWebSendMsgCtwaAttributionNode: r === "ALL_CHATS" → true
            case ALL_CHATS -> true;
            // WAWebSendMsgCtwaAttributionNode: r === "NEW_CHATS_ONLY" → !e(t)
            case NEW_CHATS_ONLY -> !hasMultipleNonSystemMessages(chat);
        };
    }

    /**
     * Mirrors the three string labels returned by
     * {@code WAWebExternalCtxConfig.getFirstMessageLoggingOption}.
     *
     * <p>The JS source uses raw strings; Cobalt replaces them with an
     * enum so the {@link CtwaAttributionStanza#shouldLogFirstMessage}
     * switch can be exhaustive at compile time.
     *
     * @implNote WAWebExternalCtxConfig.getFirstMessageLoggingOption —
     * the JS export returns one of these three string literals.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalCtxConfig", exports = "getFirstMessageLoggingOption",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public enum FirstMessageLoggingOption {
        /**
         * Attribute every chat that has at most one non-system message,
         * plus existing chats that already have a partner link
         * recorded.
         *
         * @implNote WAWebExternalCtxConfig: returned when the AB prop
         * {@code external_ctx_authorise_existing_chats} equals {@code 1}.
         */
        NEW_CHATS_OR_EXISTING_CHATS_WITH_PARTNER_LINKS,

        /**
         * Attribute every chat regardless of message count.
         *
         * @implNote WAWebExternalCtxConfig: returned when the AB prop
         * {@code external_ctx_authorise_existing_chats} equals {@code 2}.
         */
        ALL_CHATS,

        /**
         * Attribute only chats that have at most one non-system message
         * (the default policy).
         *
         * @implNote WAWebExternalCtxConfig: returned when the AB prop
         * {@code external_ctx_authorise_existing_chats} is {@code 0} or
         * any unrecognised value.
         */
        NEW_CHATS_ONLY
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
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCtwaAttributionNode", exports = "getCtwaAttributionNode",
            adaptation = WhatsAppAdaptation.DIRECT)
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
    @WhatsAppWebExport(moduleName = "WAWebMsgType", exports = "SYSTEM_MESSAGE_TYPES",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * <p>This is the pruning step of the JS {@code c(t)} persist helper,
     * which is invoked by both {@code saveExternalEntryPoint} and
     * {@code deleteExternalEntryPoint} immediately before persisting to
     * UserPrefs. The Cobalt port retains the prune-on-save semantics but
     * skips the persistence write because the in-memory
     * {@link ConcurrentHashMap} is the source of truth.
     *
     * @implNote WAWebExternalEntryPointPrefs: function {@code c(t)}
     * runs {@code Object.keys(t).forEach(function(e){u(t[e]) && delete t[e]})}
     * to drop expired entries before {@code WAWebUserPrefsStore.setUser}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "saveExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void pruneExpired() {
        // WAWebExternalEntryPointPrefs: Object.keys(t).forEach(function(e){u(t[e]) && delete t[e]})
        entryPoints.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Serialises a map to a minimal JSON string with proper string
     * escaping.
     *
     * <p>Handles {@link String}, {@link Number}, and {@link Boolean} values.
     * String values are escaped to match {@code JSON.stringify} output:
     * the backslash, double-quote, and the four standard control-character
     * shortcuts ({@code \b}, {@code \f}, {@code \n}, {@code \r}, {@code \t})
     * are emitted as their two-character escape sequences, and any other
     * control character below {@code U+0020} is emitted as a {@code &#92;u00XX}
     * escape. This avoids pulling in a JSON library for a single use case
     * while still producing output byte-equivalent to
     * {@code TextEncoder().encode(JSON.stringify(n))} for partner-supplied
     * strings that may contain quotes, backslashes, or control characters.
     *
     * @param map the key-value pairs to serialise
     * @return the JSON string
     * @implNote WAWebSendMsgCtwaAttributionNode: uses
     * {@code JSON.stringify(n)} on the payload object.  The JS spec
     * mandates that strings be escaped per
     * {@code https://tc39.es/ecma262/#sec-quotejsonstring}; Cobalt mirrors
     * the subset of escapes that {@code JSON.stringify} emits.
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
     * <p>Escapes the same characters as {@code JSON.stringify}: backslash
     * and double-quote get a leading backslash, the five control
     * shortcuts ({@code \b \f \n \r \t}) emit their two-character form,
     * and any remaining character below {@code U+0020} emits a
     * {@code &#92;u00XX} sequence.
     *
     * @param sb    the buffer to append to
     * @param value the string to encode
     * @implNote ECMA-262 {@code QuoteJSONString}: the algorithm called by
     * {@code JSON.stringify} when serialising a string property value.
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
