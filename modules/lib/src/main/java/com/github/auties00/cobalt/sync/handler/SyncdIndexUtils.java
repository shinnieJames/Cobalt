package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Utility methods for working with sync action indices and message key
 * conversions during app state synchronization.
 *
 * <p>Per WhatsApp Web {@code WAWebSyncdIndexUtils}: this module provides
 * shared helpers used by multiple sync action handlers for converting
 * between sync action index parts and {@link MessageKey} objects, as
 * well as sentinel-returning functions for malformed mutation data.
 *
 * <p>Additionally mirrors {@code WAWebSyncdActionUtils}, which exposes
 * shared helpers for building and parsing the JSON-encoded index arrays
 * used by every sync action and for assembling the message-key tuple
 * embedded in message-oriented mutation indices.
 *
 * <p>Consolidates {@code WAWebSyncdUtils} as well, whose three exports
 * ({@code constructMsgKeySegments},
 * {@code constructMsgKeySegmentsFromMsgKey}, {@code extractParticipantForSync})
 * produce the {@code [remote, id, fromMe, participant]} tuple with
 * {@code {legacy:true}} JID serialization and the
 * {@code !remote.isUser() && !fromMe} participant gate.
 *
 * <p>The helpers {@link #syncKeyToMsgKey} and
 * {@link #msgKeyToDbIdWithoutFromMeParticipant} are also consumed by
 * {@code WAWebSyncdResolveMessages.resolveMessagesForMutations}, whose
 * batch orchestration Cobalt inlines into individual mutation handlers
 * (see e.g. {@code InteractiveMessageHandler}) rather than running as a
 * pre-pass; the AB-prop driven async-chunked vs. sync branch and the
 * {@code WAWebSchemaMessage.getMessageTable().startsWithAnyOf(["id"], ...)}
 * DB query are not replicated because Cobalt resolves chats directly via
 * {@code WhatsAppStore.findChatByJid} without a message-existence probe.
 *
 * @implNote WAWebSyncdIndexUtils, WAWebSyncdActionUtils, WAWebSyncdResolveMessages, WAWebSyncdUtils
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdIndexUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdActionUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdResolveMessages")
@WhatsAppWebModule(moduleName = "WAWebSyncdUtils")
public final class SyncdIndexUtils {
    /**
     * Position of the action name within a parsed sync action index array.
     *
     * <p>Per WhatsApp Web {@code WASyncdConst.MUTATION_NAME_INDEX = 0}: the
     * zeroth element of a parsed index is always the action name.
     *
     * @implNote WASyncdConst.MUTATION_NAME_INDEX
     */
    public static final int MUTATION_NAME_INDEX = 0; // WASyncdConst.MUTATION_NAME_INDEX = f = 0
    /**
     * Logger for sync index utilities.
     *
     * @implNote ADAPTED: WAWebSyncdIndexUtils uses WALogger; Cobalt uses java.util.logging
     */
    private static final Logger LOGGER = Logger.getLogger(SyncdIndexUtils.class.getName()); // ADAPTED: WAWebSyncdIndexUtils — WALogger

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS — Java utility class pattern
     */
    private SyncdIndexUtils() {
    }

    /**
     * Serializes an action name together with its index arguments into the
     * JSON-encoded index string used by every sync mutation.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdActionUtils.buildIndex}:
     * {@code JSON.stringify([e].concat(t))}. The action name is prepended to
     * the variadic argument list and the resulting array is serialized to
     * JSON. The resulting bytes become the mutation index both in the
     * outgoing upload and in the encrypted index MAC.
     *
     * <p>This centralizes the inline {@code JSON.toJSONString(List.of(
     * actionName(), ...))} pattern used by every {@code *Handler}. Call
     * sites that still inline this pattern are flagged as
     * {@code ADAPTED: WAWebSyncdActionUtils.buildPendingMutation} since
     * they compose the index as part of building a pending mutation.
     *
     * @implNote WAWebSyncdActionUtils.buildIndex (function u)
     * @param actionName the sync action name (e.g. {@code "archive"},
     *                   {@code "pin_v1"})
     * @param indexArgs  the action-specific index arguments (zero or more
     *                   strings); may be empty but must not be {@code null}
     * @return the JSON-encoded index string
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "buildIndex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String buildIndex(String actionName, String... indexArgs) {
        // WAWebSyncdActionUtils.buildIndex (function u): return JSON.stringify([e].concat(t))
        var parts = new Object[indexArgs.length + 1];
        parts[0] = actionName; // WAWebSyncdActionUtils.u: [e]
        System.arraycopy(indexArgs, 0, parts, 1, indexArgs.length); // WAWebSyncdActionUtils.u: .concat(t)
        return JSON.toJSONString(Arrays.asList(parts)); // WAWebSyncdActionUtils.u: JSON.stringify(...)
    }

    /**
     * Parses a JSON-encoded sync action index back into its component
     * array.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdActionUtils.parseIndex}: calls
     * {@code JSON.parse(n)}; returns {@code null} if parsing fails or if
     * the resulting array has fewer than one element, in both cases after
     * logging a warning via {@code WALogger}. The collection name is
     * passed in purely for the log message.
     *
     * @implNote WAWebSyncdActionUtils.parseIndex (function c)
     * @param collectionName the collection the mutation belongs to, used
     *                       for diagnostic logging only
     * @param index          the JSON-encoded index string
     * @return the parsed array, or {@code null} if the index is missing,
     *         unparseable, or empty
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "parseIndex", adaptation = WhatsAppAdaptation.DIRECT)
    public static JSONArray parseIndex(String collectionName, String index) {
        // WAWebSyncdActionUtils.parseIndex (function c)
        try {
            var parsed = JSON.parseArray(index); // WAWebSyncdActionUtils.c: var r = JSON.parse(n)
            if (parsed == null || parsed.size() < 1) { // WAWebSyncdActionUtils.c: if (r.length < 1)
                LOGGER.warning(() -> "[syncd] invalid empty index for collection " + collectionName); // WAWebSyncdActionUtils.c: WALogger.WARN
                return null; // WAWebSyncdActionUtils.c: return null
            }
            return parsed;
        } catch (Throwable throwable) { // WAWebSyncdActionUtils.c: catch (e)
            LOGGER.warning(() -> "[syncd] invalid index for collection " + collectionName); // WAWebSyncdActionUtils.c: WALogger.WARN
            return null; // WAWebSyncdActionUtils.c: return null
        }
    }

    /**
     * Extracts the mutation (action) name from a JSON-encoded sync action
     * index.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdActionUtils.getMutationNameFromIndex}:
     * {@code var n = parseIndex(e, t); return n == null ? void 0 :
     * n[WASyncdConst.MUTATION_NAME_INDEX];}. If the underlying parse
     * returns {@code null} (missing/unparseable/empty), returns
     * {@code null}; otherwise returns element 0 of the parsed array.
     *
     * @implNote WAWebSyncdActionUtils.getMutationNameFromIndex (function d)
     * @param collectionName the collection the mutation belongs to, used
     *                       for diagnostic logging in the nested parse
     * @param index          the JSON-encoded index string
     * @return the action name, or {@code null} if the index is invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "getMutationNameFromIndex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String getMutationNameFromIndex(String collectionName, String index) {
        // WAWebSyncdActionUtils.getMutationNameFromIndex (function d): var n = c(e, t); return n == null ? void 0 : n[MUTATION_NAME_INDEX]
        var parsed = parseIndex(collectionName, index); // WAWebSyncdActionUtils.d: var n = c(e, t)
        if (parsed == null) { // WAWebSyncdActionUtils.d: n == null ? void 0
            return null;
        }
        return parsed.getString(MUTATION_NAME_INDEX); // WAWebSyncdActionUtils.d: n[o("WASyncdConst").MUTATION_NAME_INDEX]
    }

    /**
     * Builds the {@code [remoteJid, id, fromMe, participant]} index tuple
     * used by message-oriented sync mutations (star, delete-for-me,
     * mark-as-read on a single message, etc.).
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdActionUtils.buildMessageKey}:
     * {@code return [o, n, t?"1":"0", r!=null && !t ? r : "0"]} where
     * {@code t = fromMe}, {@code n = id}, {@code r = participant},
     * {@code o = remoteJid}. The participant segment is coerced to the
     * literal {@code "0"} when absent or when the message is outgoing
     * (since the self JID is implicit for {@code fromMe == true}).
     *
     * @implNote WAWebSyncdActionUtils.buildMessageKey (function p)
     * @param remoteJid   the chat JID (must not be {@code null})
     * @param id          the message ID
     * @param fromMe      whether the message was sent by the current user
     * @param participant the participant JID for incoming group messages,
     *                    or {@code null} for 1:1 chats / outgoing messages
     * @return the four-element index tuple
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "buildMessageKey", adaptation = WhatsAppAdaptation.DIRECT)
    public static List<String> buildMessageKey(Jid remoteJid, String id, boolean fromMe, Jid participant) {
        // WAWebSyncdActionUtils.buildMessageKey (function p):
        // return [o, n, t?"1":"0", r!=null && !t ? r : "0"]
        var fromMeStr = fromMe ? "1" : "0"; // WAWebSyncdActionUtils.p: t?"1":"0"
        var participantStr = participant != null && !fromMe // WAWebSyncdActionUtils.p: r!=null && !t
                ? participant.toString() // WAWebSyncdActionUtils.p: r
                : "0"; // WAWebSyncdActionUtils.p: "0"
        return List.of(
                remoteJid.toString(), // WAWebSyncdActionUtils.p: o (remoteJid)
                id, // WAWebSyncdActionUtils.p: n (id)
                fromMeStr, // WAWebSyncdActionUtils.p: t?"1":"0"
                participantStr // WAWebSyncdActionUtils.p: r!=null && !t ? r : "0"
        );
    }

    /**
     * Builds the four-segment message-key tuple used as the identity portion
     * of every message-oriented sync-action index, keyed directly off a
     * {@link ChatMessageInfo}.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdUtils.constructMsgKeySegments}:
     * {@code function e(e){return l(e.id)}} — unwraps the embedded
     * {@code MessageKey} and delegates to
     * {@link #constructMsgKeySegmentsFromMsgKey(MessageKey)}. The parameter
     * is the full message wrapper (equivalent to WA Web's message value
     * object whose {@code id} field holds the {@code MsgKey}).
     *
     * @implNote WAWebSyncdUtils.constructMsgKeySegments (function e)
     * @param info the chat message whose key is being encoded
     * @return the four-element segment list
     *         {@code [remote, id, fromMe, participant]}
     * @throws NullPointerException if {@code info} is {@code null}
     * @see #constructMsgKeySegmentsFromMsgKey(MessageKey)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdUtils", exports = "constructMsgKeySegments", adaptation = WhatsAppAdaptation.DIRECT)
    public static List<String> constructMsgKeySegments(ChatMessageInfo info) {
        // WAWebSyncdUtils.constructMsgKeySegments (function e): return l(e.id)
        Objects.requireNonNull(info, "info cannot be null");
        return constructMsgKeySegmentsFromMsgKey(info.key()); // WAWebSyncdUtils.e: l(e.id)
    }

    /**
     * Builds the four-segment message-key tuple used as the identity portion
     * of every message-oriented sync-action index.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey}:
     * {@code function l(e){var t=s(e);return[e.remote.toString({legacy:!0}),
     * e.id,e.fromMe?"1":"0",t]}}. The remote JID is serialized using
     * {@code {legacy:true}} semantics (mapping the legacy {@code c.us}
     * server domain onto the canonical {@code s.whatsapp.net} wire
     * representation); the participant segment is delegated to
     * {@link #extractParticipantForSync(MessageKey)} which returns the
     * literal {@code "0"} for outgoing or 1:1 messages.
     *
     * <p>Unlike {@link #buildMessageKey(Jid, String, boolean, Jid)}, which
     * maps to {@code WAWebSyncdActionUtils.buildMessageKey} and accepts
     * pre-decomposed fields with the caller responsible for gating the
     * participant, this method accepts a raw {@link MessageKey} and applies
     * the full {@code extractParticipantForSync} predicate.
     *
     * @implNote WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey (function l)
     * @param key the message key to encode; must carry a non-{@code null}
     *            {@code parentJid} and a non-{@code null} {@code id}
     * @return the four-element segment list
     *         {@code [remote, id, fromMe, participant]}
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has no {@code parentJid}
     *                                  or no {@code id}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdUtils", exports = "constructMsgKeySegmentsFromMsgKey", adaptation = WhatsAppAdaptation.DIRECT)
    public static List<String> constructMsgKeySegmentsFromMsgKey(MessageKey key) {
        // WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey (function l):
        // var t = s(e); return [e.remote.toString({legacy:!0}), e.id, e.fromMe?"1":"0", t]
        Objects.requireNonNull(key, "key cannot be null");
        var remoteJid = key.parentJid() // WAWebSyncdUtils.l: e.remote
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var id = key.id() // WAWebSyncdUtils.l: e.id
                .orElseThrow(() -> new IllegalArgumentException("key must carry an id"));
        var participantSegment = extractParticipantForSync(key); // WAWebSyncdUtils.l: var t = s(e)
        return List.of(
                toLegacyJidString(remoteJid), // WAWebSyncdUtils.l: e.remote.toString({legacy:!0})
                id, // WAWebSyncdUtils.l: e.id
                key.fromMe() ? "1" : "0", // WAWebSyncdUtils.l: e.fromMe?"1":"0"
                participantSegment // WAWebSyncdUtils.l: t
        );
    }

    /**
     * Returns the participant segment of a message-key sync tuple.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdUtils.extractParticipantForSync}:
     * {@code function s(e){var t="0"; return e.participant && !e.remote.isUser()
     * && !e.fromMe && (t=e.participant.toString({legacy:!0})), t}}. The
     * participant string is only emitted when all three conditions hold:
     * <ul>
     *   <li>The key records a dedicated participant (sender JID distinct
     *       from the chat JID).</li>
     *   <li>The remote JID is not a single-user chat JID, i.e. it is a
     *       group, broadcast, newsletter, or other multi-participant JID
     *       per {@link Jid}-based equivalents of {@code Wid.isUser}.</li>
     *   <li>The message was not sent by the current user.</li>
     * </ul>
     * When any condition fails, the literal {@code "0"} is returned so that
     * the index tuple retains a fixed arity.
     *
     * <p>Cobalt uses the raw {@link MessageKey#senderJid} field rather than
     * {@link MessageKey#senderJid()} because the latter carries 1:1 fallback
     * semantics (returns {@code parentJid} when no dedicated sender is
     * stored) that would wrongly emit the chat JID as participant on
     * single-user chats.
     *
     * @implNote WAWebSyncdUtils.extractParticipantForSync (function s)
     * @param key the message key whose participant segment is required;
     *            must carry a non-{@code null} {@code parentJid}
     * @return the serialized participant JID, or the literal {@code "0"}
     *         when the predicate is not satisfied
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has no {@code parentJid}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdUtils", exports = "extractParticipantForSync", adaptation = WhatsAppAdaptation.ADAPTED)
    public static String extractParticipantForSync(MessageKey key) {
        // WAWebSyncdUtils.extractParticipantForSync (function s):
        // var t = "0"; return e.participant && !e.remote.isUser() && !e.fromMe
        //               && (t = e.participant.toString({legacy:!0})), t
        Objects.requireNonNull(key, "key cannot be null");
        var remoteJid = key.parentJid() // WAWebSyncdUtils.s: e.remote
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        // ADAPTED: Cobalt's MessageKey.senderJid() falls back to parentJid when the
        // raw sender field is null, so we read the raw field via a short-circuit
        // predicate that mirrors WA Web's `e.participant` truthiness check.
        var rawParticipant = rawSenderJid(key); // WAWebSyncdUtils.s: e.participant
        if (rawParticipant != null // WAWebSyncdUtils.s: e.participant &&
                && !isUserJid(remoteJid) // WAWebSyncdUtils.s: !e.remote.isUser()
                && !key.fromMe()) { // WAWebSyncdUtils.s: !e.fromMe
            return toLegacyJidString(rawParticipant); // WAWebSyncdUtils.s: e.participant.toString({legacy:!0})
        }
        return "0"; // WAWebSyncdUtils.s: t = "0"
    }

    /**
     * Returns the dedicated sender JID recorded on a {@link MessageKey},
     * without the 1:1-fallback semantics of {@link MessageKey#senderJid()}.
     *
     * <p>Cobalt's {@link MessageKey#senderJid()} accessor falls back to
     * {@link MessageKey#parentJid()} when no explicit sender was stored,
     * which is convenient for business logic but incorrect for the
     * {@code extractParticipantForSync} predicate because it would erase
     * the "no dedicated participant" sentinel that WA Web relies on.
     * This helper discriminates between a deliberately-set sender and
     * the parent-fallback by JID equality: when the sender equals the
     * parent JID the fallback is assumed and {@code null} is returned.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific accessor to work around the
     *           {@link MessageKey#senderJid()} fallback that does not exist
     *           in WA Web's {@code MsgKey} model
     * @param key the message key whose raw sender is required
     * @return the raw sender JID, or {@code null} when none was set
     */
    private static Jid rawSenderJid(MessageKey key) {
        // ADAPTED helper: MessageKey.senderJid() returns parentJid when the raw
        // senderJid field is null (1:1 convenience fallback). WA Web's
        // `e.participant` check treats that situation as falsy; recover that
        // semantics by discriminating on JID equality with parentJid.
        var parent = key.parentJid().orElse(null);
        var sender = key.senderJid().orElse(null);
        if (sender == null) {
            return null;
        }
        if (parent != null && parent.equals(sender)) {
            return null; // fallback case: no dedicated sender was set
        }
        return sender;
    }

    /**
     * Serializes a {@link Jid} using WhatsApp Web's {@code {legacy:true}}
     * wire convention.
     *
     * <p>Per WhatsApp Web {@code WAWebWid.toString}: when the legacy flag is
     * set, a JID whose server is the legacy user domain {@code c.us} is
     * emitted as if it were on the current standard user domain
     * {@code s.whatsapp.net}. All other servers pass through unchanged and
     * produce the same output as the default {@link Jid#toString()}.
     *
     * <p>This matches the serialization used by every sync-index consumer
     * that explicitly requests the legacy form, and is applied by
     * {@link #constructMsgKeySegmentsFromMsgKey(MessageKey)} and
     * {@link #extractParticipantForSync(MessageKey)} so that stored
     * mutation indices remain stable across the historical {@code c.us}
     * transition.
     *
     * @implNote WAWebWid.toString with {@code {legacy:true}}: maps
     *           {@code c.us} to {@code s.whatsapp.net}, otherwise returns
     *           the serialized form
     * @param jid the JID to serialize
     * @return the JID in legacy-wire form
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "toString", adaptation = WhatsAppAdaptation.ADAPTED)
    private static String toLegacyJidString(Jid jid) {
        // WAWebWid.toString({legacy:!0}): r = t.legacy && this.server === "c.us" ? "s.whatsapp.net" : this.server
        // Only the c.us server remap branch is reachable without formatFull/forLog;
        // for any other server the {legacy:true} argument is a no-op and the
        // default `_serialized` value is returned.
        Objects.requireNonNull(jid, "jid cannot be null");
        if (!jid.server().equals(JidServer.legacyUser())) { // WAWebWid.toString: this.server === "c.us" gate
            return jid.toString(); // WAWebWid.toString: return this._serialized
        }
        // Legacy user server remap: swap the @c.us suffix for @s.whatsapp.net.
        // Jid.toString already produces a well-formed user[_agent][:device]@server
        // string, and the only server-dependent segment is the trailing domain.
        var serialized = jid.toString();
        var atIndex = serialized.lastIndexOf('@');
        if (atIndex < 0) {
            return JidServer.user().toString(); // server-only c.us -> s.whatsapp.net (defensive)
        }
        return serialized.substring(0, atIndex + 1) + JidServer.user();
    }

    /**
     * Converts a {@link MessageKey} to its DB ID string representation, but
     * WITHOUT the {@code fromMe} and {@code participant} segments.
     *
     * <p>Per WhatsApp Web {@code msgKeyToDbIdWithoutFromMeParticipant}: calls
     * {@code toString()} on the message key. If the message was sent by the
     * current user ({@code fromMe} is {@code true}) AND the remote JID is NOT
     * a user JID (i.e., it is a group, broadcast, or newsletter), then the
     * last underscore-separated segment is stripped from the serialized key.
     * For all other cases, the full {@code toString()} value is returned.
     *
     * <p>The WA Web MsgKey serialization format is:
     * {@code fromMe_remote_id[_self][_participant]}, joined by underscores.
     * Stripping the last segment removes the participant component, enabling
     * lookups where the participant is not part of the key.
     *
     * <p>Since Cobalt's {@link MessageKey#senderJid()} has fallback semantics
     * (returns {@code parentJid} when the raw field is null), this method
     * uses {@link #serializeMessageKey(MessageKey)} for the full serialization
     * and strips the last segment when applicable.
     *
     * @implNote WAWebSyncdIndexUtils.msgKeyToDbIdWithoutFromMeParticipant
     * @param key the message key to convert
     * @return the DB ID string with the participant segment removed when applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "msgKeyToDbIdWithoutFromMeParticipant", adaptation = WhatsAppAdaptation.ADAPTED)
    static String msgKeyToDbIdWithoutFromMeParticipant(MessageKey key) {
        // WAWebSyncdIndexUtils.msgKeyToDbIdWithoutFromMeParticipant (function d)
        var serialized = serializeMessageKey(key); // WAWebSyncdIndexUtils.d: var t = e.toString()
        var remoteJid = key.parentJid().orElse(null); // WAWebSyncdIndexUtils.d: e.remote
        if (!key.fromMe() || remoteJid == null || isUserJid(remoteJid)) { // WAWebSyncdIndexUtils.d: if (!e.fromMe || e.remote.isUser()) return t
            return serialized;
        }
        var lastUnderscore = serialized.lastIndexOf('_'); // WAWebSyncdIndexUtils.d: var n = t.lastIndexOf("_")
        if (lastUnderscore < 0) {
            return serialized; // ADAPTED: defensive null check — WA Web assumes underscore always exists
        }
        return serialized.substring(0, lastUnderscore); // WAWebSyncdIndexUtils.d: return t.substring(0, n)
    }

    /**
     * Converts sync action index parts back into a {@link MessageKey}.
     *
     * <p>Per WhatsApp Web {@code syncKeyToMsgKey}: the four index parts are
     * typically {@code [chatJid, messageId, fromMe, senderJid]}. The function:
     * <ul>
     *   <li>Validates that {@code remote} is a valid WID</li>
     *   <li>Creates a Jid from {@code remote}</li>
     *   <li>If the remote is NOT a user JID and NOT a newsletter:
     *     <ul>
     *       <li>If {@code fromMe} is {@code "0"}: validates {@code participant}
     *           is a valid WID and uses it as the participant</li>
     *       <li>If {@code fromMe} is {@code "1"}: uses the current user's JID
     *           as the participant</li>
     *     </ul>
     *   </li>
     *   <li>Returns a {@link MessageKey} with the resolved fields</li>
     * </ul>
     *
     * @implNote WAWebSyncdIndexUtils.syncKeyToMsgKey
     * @param store       the store to obtain the current user's JID for group fromMe resolution
     * @param remote      the remote JID string (chat JID)
     * @param id          the message ID string
     * @param fromMe      the fromMe string ({@code "0"} or {@code "1"})
     * @param participant the participant JID string (sender in group chats)
     * @return the resolved {@link MessageKey}, or empty if the input is invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "syncKeyToMsgKey", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<MessageKey> syncKeyToMsgKey(WhatsAppStore store, String remote, String id, String fromMe, String participant) {
        // WAWebSyncdIndexUtils.syncKeyToMsgKey (function m)
        if (remote == null || remote.isEmpty()) { // WAWebSyncdIndexUtils.m: if (!r("WAWebWid").isWid(t)) return ... null
            LOGGER.warning("syncKeyToMsgKey: invalid remote value"); // WAWebSyncdIndexUtils.m: WALogger.WARN
            return Optional.empty();
        }

        Jid remoteJid;
        try {
            remoteJid = Jid.of(remote); // WAWebSyncdIndexUtils.m: var u = o("WAWebWidFactory").createWid(t)
        } catch (Exception e) {
            LOGGER.warning("syncKeyToMsgKey: invalid remote value: " + remote); // WAWebSyncdIndexUtils.m: WALogger.WARN
            return Optional.empty();
        }

        Jid participantJid = null; // WAWebSyncdIndexUtils.m: var l = void 0
        var isUser = isUserJid(remoteJid); // WAWebSyncdIndexUtils.m: u.isUser()
        var isNewsletter = remoteJid.hasNewsletterServer(); // WAWebSyncdIndexUtils.m: u.isNewsletter()
        if (!isUser && !isNewsletter) { // WAWebSyncdIndexUtils.m: if (!u.isUser() && !u.isNewsletter())
            if ("1".equals(fromMe)) { // WAWebSyncdIndexUtils.m: a === "1" ? l = o("WAWebUserPrefsMeUser").getMePnUserOrThrow_DO_NOT_USE()
                participantJid = store.jid().orElse(null); // WAWebSyncdIndexUtils.m: getMePnUserOrThrow_DO_NOT_USE()
            } else { // WAWebSyncdIndexUtils.m: a === "0" && !r("WAWebWid").isWid(i) check
                if (participant == null || participant.isEmpty()) { // WAWebSyncdIndexUtils.m: if (a === "0" && !r("WAWebWid").isWid(i))
                    LOGGER.warning("syncKeyToMsgKey: invalid participant value"); // WAWebSyncdIndexUtils.m: WALogger.WARN
                    return Optional.empty();
                }
                try {
                    participantJid = Jid.of(participant); // WAWebSyncdIndexUtils.m: l = o("WAWebWidFactory").createWid(i)
                } catch (Exception e) {
                    LOGGER.warning("syncKeyToMsgKey: invalid participant value: " + participant); // WAWebSyncdIndexUtils.m: WALogger.WARN
                    return Optional.empty();
                }
            }
        }

        // WAWebSyncdIndexUtils.m: return new(r("WAWebMsgKey"))({fromMe: a === "1", remote: u, id: n, participant: l})
        var key = new MessageKeyBuilder()
                .fromMe("1".equals(fromMe))
                .parentJid(remoteJid)
                .id(id)
                .senderJid(participantJid)
                .build();
        return Optional.of(key);
    }

    /**
     * Extracts a {@link MessageKey} from a star action's index format.
     *
     * <p>Per WhatsApp Web {@code getMsgKeyFromStarActionIndex}: parses the
     * index string as a JSON array and expects at least 5 elements. The
     * message key is constructed from elements at indices 1 through 4
     * using {@link #syncKeyToMsgKey}.
     *
     * @implNote WAWebSyncdIndexUtils.getMsgKeyFromStarActionIndex
     * @param store the store for current user JID resolution
     * @param index the JSON-encoded index string from the star action
     * @return the resolved {@link MessageKey}, or empty if the index is malformed
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "getMsgKeyFromStarActionIndex", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<MessageKey> getMsgKeyFromStarActionIndex(WhatsAppStore store, String index) {
        // WAWebSyncdIndexUtils.getMsgKeyFromStarActionIndex (function p)
        var parsed = JSON.parseArray(index); // WAWebSyncdIndexUtils.p: var t = JSON.parse(e)
        if (parsed == null || parsed.size() < 5) { // WAWebSyncdIndexUtils.p: if (t.length < 5) throw ...  (ADAPTED: defensive null check vs. WA Web's JS-runtime throw)
            LOGGER.warning("[sync-action] star action index malformed, cannot create MsgKey"); // WAWebSyncdIndexUtils.p: r("err")("[sync-action] star action index malformed...")
            return Optional.empty(); // ADAPTED: WAWebSyncdIndexUtils.p throws; Cobalt returns empty
        }
        var result = syncKeyToMsgKey( // WAWebSyncdIndexUtils.p: var n = m(t[1], t[2], t[3], t[4])
                store,
                parsed.getString(1),
                parsed.getString(2),
                parsed.getString(3),
                parsed.getString(4)
        );
        if (result.isEmpty()) { // WAWebSyncdIndexUtils.p: if (!n) throw ...
            LOGGER.warning("[sync-action] star index malformed, MsgKey failed"); // WAWebSyncdIndexUtils.p: WALogger.WARN
        }
        return result;
    }

    /**
     * Returns a malformed result for an invalid action index.
     *
     * <p>Per WhatsApp Web {@code malformedActionIndex}: uploads a critical event
     * metric with code {@code ACTION_INVALID_INDEX_DATA} and returns
     * {@code {actionState: Malformed}}.
     *
     * <p>In Cobalt, the WAM metric upload is intentionally omitted (telemetry
     * is not replicated), but the return value semantics are preserved.
     *
     * @implNote WAWebSyncdIndexUtils.malformedActionIndex
     * @param collectionName the collection name for diagnostic context (WAM metric parameter)
     * @param actionName     the action name for diagnostic context (WAM metric parameter)
     * @return a {@link MutationApplicationResult} with {@code MALFORMED} state
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "malformedActionIndex", adaptation = WhatsAppAdaptation.ADAPTED)
    static MutationApplicationResult malformedActionIndex(String collectionName, String actionName) {
        // WAWebSyncdIndexUtils.malformedActionIndex (function _)
        // WAWebSyncdIndexUtils._: o("WAWebSyncdMetrics").uploadMdCriticalEventMetric(...) — WAM telemetry skipped
        LOGGER.fine(() -> "malformedActionIndex: collection=" + collectionName + ", action=" + actionName); // ADAPTED: WAWebSyncdIndexUtils._ — WAM metric replaced with fine log
        return MutationApplicationResult.malformed(); // WAWebSyncdIndexUtils._: {actionState: o("WASyncdConst").SyncActionState.Malformed}
    }

    /**
     * Returns a malformed result for an invalid action value.
     *
     * <p>Per WhatsApp Web {@code malformedActionValue}: returns
     * {@code {actionState: Malformed}}. Unlike {@code malformedActionIndex},
     * this function does NOT upload a WAM metric.
     *
     * @implNote WAWebSyncdIndexUtils.malformedActionValue
     * @param collectionName the collection name for diagnostic context
     * @return a {@link MutationApplicationResult} with {@code MALFORMED} state
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "malformedActionValue", adaptation = WhatsAppAdaptation.DIRECT)
    static MutationApplicationResult malformedActionValue(String collectionName) {
        // WAWebSyncdIndexUtils.malformedActionValue (function f)
        // WAWebSyncdIndexUtils.f: return {actionState: o("WASyncdConst").SyncActionState.Malformed}
        return MutationApplicationResult.malformed(); // WAWebSyncdIndexUtils.f
    }

    /**
     * Serializes a {@link MessageKey} to the WA Web MsgKey string format.
     *
     * <p>Per WhatsApp Web, a MsgKey serializes as its constituent fields
     * joined by underscores: {@code fromMe_remote_id[_self][_participant]}.
     * Cobalt's {@link MessageKey} is a protobuf model and does not have
     * the same {@code toString()} semantics, so this method replicates
     * the WA Web serialization logic.
     *
     * <p>Because Cobalt's {@link MessageKey#senderJid()} has fallback semantics
     * (returns {@code parentJid} when the raw sender field is null), this method
     * only appends the participant segment when the remote JID is NOT a user
     * and NOT a newsletter. Per WhatsApp Web, participant is only present on
     * group/broadcast message keys, never on user or newsletter keys.
     *
     * @implNote ADAPTED: WAWebMsgKey.prototype.toString — Cobalt MessageKey has different
     *           toString semantics and senderJid fallback, so this method replicates
     *           the WA Web serialization with explicit participant-presence logic
     * @param key the message key to serialize
     * @return the WA Web-compatible serialized string
     */
    static String serializeMessageKey(MessageKey key) {
        // WAWebMsgKey constructor: this._serialized = E.join("_")
        // where E = [this.fromMe, this.remote, this.id, ...optional self, ...optional participant]
        var sb = new StringBuilder();
        sb.append(key.fromMe()); // WAWebMsgKey: this.fromMe
        sb.append('_');
        var remoteJid = key.parentJid().orElse(null);
        sb.append(remoteJid != null ? remoteJid.toString() : ""); // WAWebMsgKey: this.remote
        sb.append('_');
        sb.append(key.id().orElse("")); // WAWebMsgKey: this.id
        // WAWebMsgKey: participant is only set for group/broadcast keys (not user/newsletter)
        // Cobalt's senderJid() has fallback to parentJid, so we must only append it
        // when the remote is NOT a user and NOT a newsletter (matching WA Web behavior)
        if (remoteJid != null && !isUserJid(remoteJid) && !remoteJid.hasNewsletterServer()) { // ADAPTED: detect participant presence via remote type
            key.senderJid().ifPresent(sender -> { // WAWebMsgKey: this.participant
                sb.append('_');
                sb.append(sender);
            });
        }
        return sb.toString();
    }

    /**
     * Checks whether the given JID is a "user" JID per WhatsApp Web's definition.
     *
     * <p>Per WhatsApp Web {@code WAWebWid.prototype.isUser}: a WID is considered
     * a user if its server is {@code c.us}, {@code lid}, {@code bot},
     * {@code hosted}, or {@code hosted.lid}. Note that {@code s.whatsapp.net}
     * is the Cobalt-canonical user server equivalent to {@code c.us}.
     *
     * @implNote ADAPTED: WAWebWid.prototype.isUser — mapped to Cobalt's Jid server checks
     * @param jid the JID to check
     * @return {@code true} if the JID belongs to a user-category server
     */
    private static boolean isUserJid(Jid jid) {
        // WAWebWid.prototype.isUser: this.server === "c.us" || this.server === "lid" || this.server === "bot" || this.server === "hosted" || this.server === "hosted.lid"
        return jid.hasUserServer()        // c.us / s.whatsapp.net
                || jid.hasLidServer()     // lid
                || jid.hasBotServer()     // bot
                || jid.hasHostedServer()  // hosted
                || jid.hasHostedLidServer(); // hosted.lid
    }
}
