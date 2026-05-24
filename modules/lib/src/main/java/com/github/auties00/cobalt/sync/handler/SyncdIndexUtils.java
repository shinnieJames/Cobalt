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
 * Helpers shared across the sync-action handlers for building and parsing
 * mutation indices and for moving between JSON index tuples and
 * {@link MessageKey} objects.
 *
 * @apiNote
 * Embedders never call this utility directly. It is the Cobalt-side
 * consolidation of three WA Web index/utility modules:
 * {@code WAWebSyncdIndexUtils} (orphan-friendly sentinels, message-key
 * round-trip), {@code WAWebSyncdActionUtils} (index serialization,
 * {@code [remote, id, fromMe, participant]} message-key index segments,
 * mutation builder), and {@code WAWebSyncdUtils} (the
 * {@code constructMsgKeySegmentsFromMsgKey} /
 * {@code extractParticipantForSync} pair). The
 * {@code WAWebSyncdResolveMessages.resolveMessagesForMutations} batch
 * pre-pass that consumes these helpers in WA Web has no Cobalt analogue;
 * Cobalt's handlers resolve their messages inline via
 * {@link WhatsAppStore#findChatByJid(Jid)} so the AB-prop driven
 * chunked-vs-sync branch and the IDB existence probe are not replicated.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdIndexUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdActionUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdResolveMessages")
@WhatsAppWebModule(moduleName = "WAWebSyncdUtils")
public final class SyncdIndexUtils {
    /**
     * The slot in a parsed index array that carries the action name.
     *
     * @apiNote
     * Matches {@code WASyncdConst.MUTATION_NAME_INDEX}; the index array
     * is conventionally laid out as
     * {@code [actionName, ...actionSpecificArgs]} so the zeroth slot is
     * always the action.
     */
    public static final int MUTATION_NAME_INDEX = 0;

    /**
     * The logger used for the malformed-index diagnostic paths.
     */
    private static final Logger LOGGER = Logger.getLogger(SyncdIndexUtils.class.getName());

    /**
     * Hides the constructor of this utility class.
     *
     * @apiNote
     * The class only exposes {@code static} helpers; instantiation is
     * pointless.
     */
    private SyncdIndexUtils() {
    }

    /**
     * Serializes an action name and its trailing arguments into the
     * JSON-encoded mutation index used by every sync handler.
     *
     * @apiNote
     * Centralises the inline {@code JSON.toJSONString(List.of(actionName(), ...))}
     * pattern used by every {@code *Handler}; callers should prefer this
     * over hand-rolling the JSON string so that future changes to the
     * index format remain a single edit.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdActionUtils.buildIndex}:
     * {@code JSON.stringify([e].concat(t))}. The variadic argument list
     * is prepended with the action name then serialized.
     *
     * @param actionName the sync action name (e.g. {@code "archive"}, {@code "pin_v1"})
     * @param indexArgs  the action-specific index arguments, may be empty but not {@code null}
     * @return the JSON-encoded index string
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "buildIndex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String buildIndex(String actionName, String... indexArgs) {
        var parts = new Object[indexArgs.length + 1];
        parts[0] = actionName;
        System.arraycopy(indexArgs, 0, parts, 1, indexArgs.length);
        return JSON.toJSONString(Arrays.asList(parts));
    }

    /**
     * Parses a JSON-encoded mutation index back into its component array.
     *
     * @apiNote
     * Returns {@code null} for missing, unparseable, or empty indices so
     * callers can take the malformed branch without having to wrap the
     * call in a {@code try/catch}.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdActionUtils.parseIndex} which logs a WARN through
     * {@code WALogger} and returns {@code null} on either an empty array
     * or a JSON parse failure. The collection name is propagated only as
     * a diagnostic tag for the log message.
     *
     * @param collectionName the collection the mutation belongs to (diagnostic only)
     * @param index          the JSON-encoded index string
     * @return the parsed array, or {@code null} if missing/unparseable/empty
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "parseIndex", adaptation = WhatsAppAdaptation.DIRECT)
    public static JSONArray parseIndex(String collectionName, String index) {
        try {
            var parsed = JSON.parseArray(index);
            if (parsed == null || parsed.size() < 1) {
                LOGGER.warning(() -> "[syncd] invalid empty index for collection " + collectionName);
                return null;
            }
            return parsed;
        } catch (Throwable throwable) {
            LOGGER.warning(() -> "[syncd] invalid index for collection " + collectionName);
            return null;
        }
    }

    /**
     * Extracts the action name slot from a JSON-encoded mutation index.
     *
     * @apiNote
     * Useful for dispatchers that need to identify the handler before
     * decoding the rest of the mutation; returns {@code null} when the
     * underlying parse fails.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdActionUtils.getMutationNameFromIndex} which is a
     * thin wrapper around {@link #parseIndex(String, String)} returning
     * the {@link #MUTATION_NAME_INDEX} slot.
     *
     * @param collectionName the collection the mutation belongs to (diagnostic only)
     * @param index          the JSON-encoded index string
     * @return the action name, or {@code null} if the index is invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "getMutationNameFromIndex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String getMutationNameFromIndex(String collectionName, String index) {
        var parsed = parseIndex(collectionName, index);
        if (parsed == null) {
            return null;
        }
        return parsed.getString(MUTATION_NAME_INDEX);
    }

    /**
     * Builds the {@code [remoteJid, id, fromMe, participant]} index tuple
     * for message-oriented sync mutations.
     *
     * @apiNote
     * Used by handlers whose action targets a single message
     * (star, delete-for-me, mark-as-read on one message). The fourth
     * slot is forced to the literal {@code "0"} when no dedicated
     * participant exists, preserving the four-arity invariant.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdActionUtils.buildMessageKey}:
     * {@code [o, n, t?"1":"0", r!=null && !t ? r : "0"]}.
     *
     * @param remoteJid   the chat JID (must not be {@code null})
     * @param id          the message id
     * @param fromMe      whether the message was sent by the current user
     * @param participant the participant JID for incoming group messages, or {@code null} otherwise
     * @return the four-element index tuple
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "buildMessageKey", adaptation = WhatsAppAdaptation.DIRECT)
    public static List<String> buildMessageKey(Jid remoteJid, String id, boolean fromMe, Jid participant) {
        var fromMeStr = fromMe ? "1" : "0";
        var participantStr = participant != null && !fromMe
                ? participant.toString()
                : "0";
        return List.of(
                remoteJid.toString(),
                id,
                fromMeStr,
                participantStr
        );
    }

    /**
     * Builds the four-segment message-key tuple from a
     * {@link ChatMessageInfo}.
     *
     * @apiNote
     * Used by senders that already have a full message wrapper at hand
     * and want to compute its sync-action index segments without
     * unwrapping the {@link MessageKey} themselves.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdUtils.constructMsgKeySegments} which calls
     * {@link #constructMsgKeySegmentsFromMsgKey(MessageKey)} on the
     * embedded key.
     *
     * @param info the chat message whose key is being encoded
     * @return the four-element segment list {@code [remote, id, fromMe, participant]}
     * @throws NullPointerException if {@code info} is {@code null}
     * @see #constructMsgKeySegmentsFromMsgKey(MessageKey)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdUtils", exports = "constructMsgKeySegments", adaptation = WhatsAppAdaptation.DIRECT)
    public static List<String> constructMsgKeySegments(ChatMessageInfo info) {
        Objects.requireNonNull(info, "info cannot be null");
        return constructMsgKeySegmentsFromMsgKey(info.key());
    }

    /**
     * Builds the four-segment message-key tuple from a raw
     * {@link MessageKey}.
     *
     * @apiNote
     * Differs from {@link #buildMessageKey(Jid, String, boolean, Jid)} in
     * that callers do not need to pre-decompose the key; the participant
     * predicate is applied via {@link #extractParticipantForSync(MessageKey)}.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdUtils.constructMsgKeySegmentsFromMsgKey}:
     * {@code [e.remote.toString({legacy:!0}), e.id, e.fromMe?"1":"0", s(e)]}.
     * Remote JID serialization uses the {@code legacy:true} form via
     * {@link #toLegacyJidString(Jid)} so historical {@code c.us} keys
     * remap onto the canonical {@code s.whatsapp.net} wire
     * representation.
     *
     * @param key the message key to encode
     * @return the four-element segment list {@code [remote, id, fromMe, participant]}
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has no {@code parentJid} or no {@code id}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdUtils", exports = "constructMsgKeySegmentsFromMsgKey", adaptation = WhatsAppAdaptation.DIRECT)
    public static List<String> constructMsgKeySegmentsFromMsgKey(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        var remoteJid = key.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var id = key.id()
                .orElseThrow(() -> new IllegalArgumentException("key must carry an id"));
        var participantSegment = extractParticipantForSync(key);
        return List.of(
                toLegacyJidString(remoteJid),
                id,
                key.fromMe() ? "1" : "0",
                participantSegment
        );
    }

    /**
     * Computes the participant segment of a message-key sync tuple.
     *
     * @apiNote
     * The participant slot is only emitted when the message has a
     * dedicated sender JID, the remote JID is multi-participant (group,
     * broadcast, newsletter), and the message was not sent by the
     * current user; any other combination collapses to the literal
     * {@code "0"} so the index tuple keeps a fixed arity.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdUtils.extractParticipantForSync}:
     * {@code e.participant && !e.remote.isUser() && !e.fromMe} guards the
     * emission. Cobalt reads the raw sender JID through
     * {@link #rawSenderJid(MessageKey)} rather than through
     * {@link MessageKey#senderJid()} because the latter falls back to the
     * parent JID when no explicit sender was stored, which would
     * wrongly emit the chat JID as the participant.
     *
     * @param key the message key whose participant segment is required
     * @return the serialized participant JID, or {@code "0"} when the predicate is not met
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has no {@code parentJid}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdUtils", exports = "extractParticipantForSync", adaptation = WhatsAppAdaptation.ADAPTED)
    public static String extractParticipantForSync(MessageKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        var remoteJid = key.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("key must carry a parentJid"));
        var rawParticipant = rawSenderJid(key);
        if (rawParticipant != null
                && !isUserJid(remoteJid)
                && !key.fromMe()) {
            return toLegacyJidString(rawParticipant);
        }
        return "0";
    }

    /**
     * Returns the explicit sender JID on a {@link MessageKey}, ignoring
     * the parent-JID fallback.
     *
     * @apiNote
     * Used internally by {@link #extractParticipantForSync(MessageKey)}
     * to recover WA Web's "no dedicated participant" sentinel.
     *
     * @implNote
     * This implementation discriminates the fallback case by JID
     * equality: when {@link MessageKey#senderJid()} equals
     * {@link MessageKey#parentJid()} the helper assumes the fallback was
     * applied and returns {@code null}.
     *
     * @param key the message key whose raw sender is required
     * @return the raw sender JID, or {@code null} when none was stored
     */
    private static Jid rawSenderJid(MessageKey key) {
        var parent = key.parentJid().orElse(null);
        var sender = key.senderJid().orElse(null);
        if (sender == null) {
            return null;
        }
        if (parent != null && parent.equals(sender)) {
            return null;
        }
        return sender;
    }

    /**
     * Serializes a {@link Jid} in the WA Web {@code legacy:true} form.
     *
     * @apiNote
     * Sync-action indices are written in the legacy form so that
     * historical {@code c.us} keys remain stable across the
     * {@code c.us -> s.whatsapp.net} server domain transition; this
     * helper is consumed by every sync index path that needs the legacy
     * serialization.
     *
     * @implNote
     * This implementation rewrites only the trailing {@code @c.us} server
     * to {@code @s.whatsapp.net}; for any other server the JID's default
     * {@link Jid#toString()} is identical to its legacy form so it is
     * passed through unchanged.
     *
     * @param jid the JID to serialize
     * @return the JID in legacy-wire form
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "toString", adaptation = WhatsAppAdaptation.ADAPTED)
    private static String toLegacyJidString(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        if (!jid.server().equals(JidServer.legacyUser())) {
            return jid.toString();
        }
        var serialized = jid.toString();
        var atIndex = serialized.lastIndexOf('@');
        if (atIndex < 0) {
            return JidServer.user().toString();
        }
        return serialized.substring(0, atIndex + 1) + JidServer.user();
    }

    /**
     * Returns the serialized message-key DB id with the participant
     * segment stripped when the message is an outgoing group/broadcast
     * message.
     *
     * @apiNote
     * Used by {@link StarMessageHandler} (and any future handler that
     * needs a prefix-match against the WA Web MsgKey serialization) to
     * compare local keys against pre-fetched DB id lists.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdIndexUtils.msgKeyToDbIdWithoutFromMeParticipant}:
     * the trailing underscore-separated segment is stripped only when
     * {@code fromMe} is {@code true} AND the remote JID is not a user
     * JID. Cobalt's
     * {@link MessageKey#senderJid()} fallback forces this helper to use
     * {@link #serializeMessageKey(MessageKey)} for the full serialization.
     *
     * @param key the message key to convert
     * @return the DB id string with the participant segment removed when applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "msgKeyToDbIdWithoutFromMeParticipant", adaptation = WhatsAppAdaptation.ADAPTED)
    static String msgKeyToDbIdWithoutFromMeParticipant(MessageKey key) {
        var serialized = serializeMessageKey(key);
        var remoteJid = key.parentJid().orElse(null);
        if (!key.fromMe() || remoteJid == null || isUserJid(remoteJid)) {
            return serialized;
        }
        var lastUnderscore = serialized.lastIndexOf('_');
        if (lastUnderscore < 0) {
            return serialized;
        }
        return serialized.substring(0, lastUnderscore);
    }

    /**
     * Rebuilds a {@link MessageKey} from the four index parts of a
     * message-oriented sync action.
     *
     * @apiNote
     * Used by handlers that need to surface a malformed-or-orphan
     * mutation back to the dispatcher in {@link MessageKey} form (e.g.
     * {@link StarMessageHandler} returning a populated
     * {@link MutationApplicationResult#orphan(String, String)}).
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdIndexUtils.syncKeyToMsgKey}: it validates the
     * remote JID is a wid, parses it, and for non-user / non-newsletter
     * chats resolves the participant from either the explicit slot
     * (when {@code fromMe == "0"}) or from the current user's JID (when
     * {@code fromMe == "1"}). Invalid inputs return {@link Optional#empty()}.
     *
     * @param store       the {@link WhatsAppStore} consulted for the current user's JID
     * @param remote      the chat JID string
     * @param id          the message id string
     * @param fromMe      the {@code fromMe} flag as {@code "0"} or {@code "1"}
     * @param participant the participant JID string, may be {@code "0"} or empty
     * @return the resolved {@link MessageKey}, or empty when the input is invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "syncKeyToMsgKey", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<MessageKey> syncKeyToMsgKey(WhatsAppStore store, String remote, String id, String fromMe, String participant) {
        if (remote == null || remote.isEmpty()) {
            LOGGER.warning("syncKeyToMsgKey: invalid remote value");
            return Optional.empty();
        }

        Jid remoteJid;
        try {
            remoteJid = Jid.of(remote);
        } catch (Exception e) {
            LOGGER.warning("syncKeyToMsgKey: invalid remote value: " + remote);
            return Optional.empty();
        }

        Jid participantJid = null;
        var isUser = isUserJid(remoteJid);
        var isNewsletter = remoteJid.hasNewsletterServer();
        if (!isUser && !isNewsletter) {
            if ("1".equals(fromMe)) {
                participantJid = store.jid().orElse(null);
            } else {
                if (participant == null || participant.isEmpty()) {
                    LOGGER.warning("syncKeyToMsgKey: invalid participant value");
                    return Optional.empty();
                }
                try {
                    participantJid = Jid.of(participant);
                } catch (Exception e) {
                    LOGGER.warning("syncKeyToMsgKey: invalid participant value: " + participant);
                    return Optional.empty();
                }
            }
        }

        var key = new MessageKeyBuilder()
                .fromMe("1".equals(fromMe))
                .parentJid(remoteJid)
                .id(id)
                .senderJid(participantJid)
                .build();
        return Optional.of(key);
    }

    /**
     * Extracts a {@link MessageKey} from a star-action index.
     *
     * @apiNote
     * Convenience wrapper for callers that already have the raw
     * JSON-encoded star-action index (e.g. when forwarding a star
     * mutation as a re-issue) and want the rebuilt message key without
     * having to parse the JSON themselves.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdIndexUtils.getMsgKeyFromStarActionIndex}: it
     * requires the parsed array to have at least 5 elements and delegates
     * to {@link #syncKeyToMsgKey} on slots {@code [1..4]}.
     *
     * @param store the {@link WhatsAppStore} consulted for the current user's JID
     * @param index the JSON-encoded star-action index string
     * @return the resolved {@link MessageKey}, or empty when the input is malformed
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "getMsgKeyFromStarActionIndex", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<MessageKey> getMsgKeyFromStarActionIndex(WhatsAppStore store, String index) {
        var parsed = JSON.parseArray(index);
        if (parsed == null || parsed.size() < 5) {
            LOGGER.warning("[sync-action] star action index malformed, cannot create MsgKey");
            return Optional.empty();
        }
        var result = syncKeyToMsgKey(
                store,
                parsed.getString(1),
                parsed.getString(2),
                parsed.getString(3),
                parsed.getString(4)
        );
        if (result.isEmpty()) {
            LOGGER.warning("[sync-action] star index malformed, MsgKey failed");
        }
        return result;
    }

    /**
     * Returns the {@code MALFORMED} sentinel for an invalid action index.
     *
     * @apiNote
     * Centralizes the "invalid index" report so the WAM critical-event
     * metric upload can be added in one place if Cobalt ever mirrors
     * WA Web's telemetry; until then it is just a convenience over the
     * raw {@link MutationApplicationResult#malformed()} sentinel.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdIndexUtils.malformedActionIndex} return value but
     * skips the {@code WAWebSyncdMetrics.uploadMdCriticalEventMetric(ACTION_INVALID_INDEX_DATA, ...)}
     * call because Cobalt does not replicate WAM telemetry; the
     * collection/action diagnostic is preserved in a fine log line for
     * local debugging.
     *
     * @param collectionName the collection name for diagnostic context
     * @param actionName     the action name for diagnostic context
     * @return the {@code MALFORMED} {@link MutationApplicationResult}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "malformedActionIndex", adaptation = WhatsAppAdaptation.ADAPTED)
    static MutationApplicationResult malformedActionIndex(String collectionName, String actionName) {
        LOGGER.fine(() -> "malformedActionIndex: collection=" + collectionName + ", action=" + actionName);
        return MutationApplicationResult.malformed();
    }

    /**
     * Returns the {@code MALFORMED} sentinel for an invalid action value.
     *
     * @apiNote
     * Used when the mutation index is well-formed but the decoded
     * action body is missing required fields; distinguished from
     * {@link #malformedActionIndex(String, String)} only by the absence
     * of the WAM metric upload in WA Web.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebSyncdIndexUtils.malformedActionValue} which returns
     * {@code {actionState: Malformed}} without uploading any metric.
     *
     * @param collectionName the collection name for diagnostic context
     * @return the {@code MALFORMED} {@link MutationApplicationResult}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdIndexUtils", exports = "malformedActionValue", adaptation = WhatsAppAdaptation.DIRECT)
    static MutationApplicationResult malformedActionValue(String collectionName) {
        return MutationApplicationResult.malformed();
    }

    /**
     * Serializes a {@link MessageKey} into the WA Web MsgKey string form.
     *
     * @apiNote
     * Used by {@link #msgKeyToDbIdWithoutFromMeParticipant(MessageKey)}
     * and by the {@link StarMessageHandler} orphan branch to produce a
     * stable identifier that round-trips through WA Web's MsgKey
     * format.
     *
     * @implNote
     * This implementation reproduces WA Web's
     * {@code fromMe_remote_id[_participant]} layout: the participant
     * segment is only appended when the remote JID is non-user and
     * non-newsletter, matching WA Web's behaviour and dodging Cobalt's
     * {@link MessageKey#senderJid()} parent-JID fallback.
     *
     * @param key the message key to serialize
     * @return the WA Web-compatible serialized string
     */
    static String serializeMessageKey(MessageKey key) {
        var sb = new StringBuilder();
        sb.append(key.fromMe());
        sb.append('_');
        var remoteJid = key.parentJid().orElse(null);
        sb.append(remoteJid != null ? remoteJid.toString() : "");
        sb.append('_');
        sb.append(key.id().orElse(""));
        if (remoteJid != null && !isUserJid(remoteJid) && !remoteJid.hasNewsletterServer()) {
            key.senderJid().ifPresent(sender -> {
                sb.append('_');
                sb.append(sender);
            });
        }
        return sb.toString();
    }

    /**
     * Reports whether the given JID matches WA Web's {@code Wid.isUser}
     * predicate.
     *
     * @apiNote
     * Used by the index utilities to decide whether a remote JID has a
     * dedicated participant slot; covered servers are
     * {@code c.us} / {@code s.whatsapp.net}, {@code lid}, {@code bot},
     * {@code hosted}, and {@code hosted.lid}.
     *
     * @implNote
     * This implementation expands the predicate beyond
     * {@link Jid#hasUserServer()} because the latter only covers the
     * standard and legacy user domains, missing the LID / bot / hosted
     * variants WA Web treats as user JIDs.
     *
     * @param jid the JID to test
     * @return {@code true} if the JID belongs to a user-category server
     */
    private static boolean isUserJid(Jid jid) {
        return jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer();
    }
}
