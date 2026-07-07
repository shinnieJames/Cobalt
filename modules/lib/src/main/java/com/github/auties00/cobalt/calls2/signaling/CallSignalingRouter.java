package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.Objects;
import java.util.Optional;

/**
 * Classifies an inbound {@code <call>} child element into its signaling type and a routing verdict.
 *
 * <p>This is the pure-classification half of the wa-voip inbound dispatch: given the single child
 * element of a {@code <call>} stanza and the envelope context it arrived in, it reproduces the header
 * validation and the per-message routing decision the engine makes before any handler runs. It holds
 * no per-call or engine state; it reads the stanza and returns a {@link Verdict} the receiver acts on,
 * so the same instance can classify every inbound call regardless of which calls are active.
 *
 * <p>Classification proceeds in three stages. First the universal header is validated the way
 * {@code check_msg_header} validates the flattened message: the payload must carry a non-empty
 * {@code call-id} and a {@code call-creator}, or the message is rejected. Second the child element tag
 * is resolved to a {@link Calls2SignalingType} through {@link Calls2SignalingType#ofWireTag(String)};
 * a tag the taxonomy does not name is still routable when {@link Calls2CallStanza#isKnownTag(String)}
 * reports a decoder for it, which covers the few inbound actions that name a {@code <call>} child yet
 * carry no taxonomy ordinal ({@link RingingStanza}, {@link RaiseHandStanza}). Only a tag that is neither
 * a taxonomy type nor a known decoder tag is dropped, the way {@code handle_incoming_xmpp_msg} logs and
 * drops a tag it does not support. Third the receiving context decides the verdict: a stanza that is not
 * LID-addressed is dropped before it reaches a call, matching the engine's pre-dispatch LID-only guard,
 * and an offer or an in-call action that arrives before its call object exists is buffered for replay
 * rather than processed immediately.
 *
 * <p>The routing verdict is deliberately coarse: it tells the receiver whether to process the message
 * now, drop it as malformed or unroutable, or buffer it for later replay. The finer engine decisions
 * (which state transition to drive, whether to ring, whether a terminate was already seen) belong to
 * the lifecycle layer the receiver forwards a processed message to; this router only gates and
 * classifies.
 *
 * @implNote This implementation ports the inbound classification spread across {@code message_router.cc}
 * ({@code check_msg_header} fn11495), {@code core/call_state.cc} ({@code preprocess_incoming_message} /
 * {@code record_incoming_msg} fn11497), and {@code protocol/xmpp/call_signaling_xml.cc}
 * ({@code handle_incoming_xmpp_msg} fn11539) in the wa-voip WASM module {@code ff-tScznZ8P}. The
 * header validation reproduces {@code check_msg_header}'s rejection of a missing, empty, or all-zero
 * call-id: the native validator rejects an absent call-id, a 64-byte call-id buffer that is all zeros,
 * and the matching XML-to-struct bridge {@code convert_xmpp_msg_to_msg} (fn10725) logs
 * {@code "convert_xmpp_msg_to_msg, empty call_id"} ({@code 0x0ec064}) for a present-but-all-zero
 * call-id. Cobalt carries the call-id as a {@link String} rather than a 64-byte buffer, so the
 * all-zero buffer maps to a call-id whose characters are all {@code '0'} (the wire form of an all-zero
 * hex id), which is rejected here alongside the absent and empty cases. The native validator also
 * rejects {@code len < 100} and a per-type fixed-header length mismatch against the table at
 * {@code DAT 0xb887e}; those are buffer-layout checks with no analogue in the typed
 * {@link Stanza} model, so they are not ported and the per-type length is carried for reference by
 * {@link Calls2SignalingType#fixedHeaderLength()}. The unsupported-tag drop is the native
 * {@code "handle_incoming_xmpp_msg: msg tag %s not supported"} log (strings.json address
 * {@code 0x0ee90}) and the malformed-header drop is {@code "handle_incoming_xmpp_msg: invalid message
 * header, ignoring message"} ({@code 0x07ce06}). The LID-only drop is
 * {@code "preprocess_incoming_message: dropping non-LID stanza, type=%s"} ({@code 0x064f4}). The
 * buffer-before-call decision is {@code record_incoming_msg: no active call} ({@code 0x0a15c}) feeding
 * {@code message_buffer_add} (fn11489).
 */
public final class CallSignalingRouter {
    /**
     * The wire attribute naming the call identifier on a {@code <call>} child element.
     */
    private static final String CALL_ID_ATTRIBUTE = "call-id";

    /**
     * The wire attribute naming the call creator's device JID on a {@code <call>} child element.
     */
    private static final String CALL_CREATOR_ATTRIBUTE = "call-creator";

    /**
     * Classifies how the receiver must route an inbound {@code <call>} child element.
     *
     * <p>The verdict is the coarse decision the wa-voip engine makes before any per-message handler
     * runs: process the message against its call now, drop it as malformed or unroutable, or buffer it
     * for replay once the call object exists. It carries no transition detail; the lifecycle layer the
     * receiver forwards a {@link #PROCESS} message to decides the state change.
     */
    public enum Disposition {
        /**
         * Marks a well-formed, routable message the receiver forwards to the engine for handling now.
         */
        PROCESS,

        /**
         * Marks a message the receiver drops without forwarding or buffering.
         *
         * <p>Applies to a payload that fails header validation (missing, empty, or all-zero call-id,
         * missing call-creator), a payload whose child tag names neither an action-bearing
         * {@link Calls2SignalingType} nor a known {@link Calls2CallStanza} decoder, and a payload that
         * arrives in a context the engine refuses (a non-LID-addressed stanza).
         */
        DROP,

        /**
         * Marks a message the receiver buffers for replay because its call object does not yet exist.
         *
         * <p>Applies to an offer and to an in-call action that races ahead of the call it belongs to;
         * the receiver stores it through {@link CallMessageBuffer#buffer(String, Stanza)} and replays it
         * once the call is created.
         */
        BUFFER
    }

    /**
     * Holds the result of classifying an inbound {@code <call>} child element.
     *
     * <p>A {@link Disposition#PROCESS} or {@link Disposition#BUFFER} verdict carries the {@code call-id}
     * read from the payload and the resolved {@link Calls2SignalingType}, which is empty for a tag that
     * is decodable by {@link Calls2CallStanza} yet carries no taxonomy ordinal ({@link RingingStanza},
     * {@link RaiseHandStanza}); a {@link Disposition#DROP} verdict may carry an empty type when the drop
     * reason is a missing header or an unknown tag, and an empty call-id when the payload carried none.
     * Callers branch on {@link #disposition()} first and read {@link #type()} and {@link #callId()} only
     * for the non-drop verdicts.
     *
     * <p>The {@link #type()} and {@link #callId()} components are wrapped in {@link Optional} so a verdict
     * can express the absence of a resolved type (a dropped or ordinal-less message) or a parsed call-id
     * without a sentinel; the canonical accessors return the wrapped values directly.
     *
     * @param disposition the routing decision; never {@code null}
     * @param type        the resolved signaling type, empty when the payload failed header validation,
     *                    named no known type, or names a decodable tag with no taxonomy ordinal; never
     *                    {@code null}
     * @param callId      the call identifier read from the payload, empty when absent; never
     *                    {@code null}
     * @param callCreator the {@code call-creator} device JID read from the payload, empty when absent
     *                    (which occurs only on a header-validation {@link Disposition#DROP}); never
     *                    {@code null}
     */
    public record Verdict(Disposition disposition, Optional<Calls2SignalingType> type, Optional<String> callId,
                          Optional<Jid> callCreator) {
        /**
         * Canonicalizes the record components.
         *
         * @throws NullPointerException if {@code disposition}, {@code type}, {@code callId}, or
         *                              {@code callCreator} is {@code null}
         */
        public Verdict {
            Objects.requireNonNull(disposition, "disposition cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(callId, "callId cannot be null");
            Objects.requireNonNull(callCreator, "callCreator cannot be null");
        }
    }

    /**
     * Classifies an inbound {@code <call>} child element into its signaling type and routing verdict.
     *
     * <p>Validates the universal header, resolves the child element tag to a
     * {@link Calls2SignalingType}, applies the LID-only context gate, and decides whether the message
     * is processed now or buffered for its not-yet-created call. A tag the taxonomy does not name is
     * still routable when {@link Calls2CallStanza#isKnownTag(String)} reports a decoder for it; such a
     * tag carries an empty {@link Verdict#type()} but is otherwise routed like any other action. The
     * decision flow is:
     * <ul>
     *   <li>a payload with no {@code call-creator}, or whose {@code call-id} is absent, empty, or
     *       all-zero (every character {@code '0'}), yields {@link Disposition#DROP} with an empty
     *       type;</li>
     *   <li>a child tag that resolves to no {@link Calls2SignalingType} and names no known
     *       {@link Calls2CallStanza} decoder yields {@link Disposition#DROP} with an empty type;</li>
     *   <li>a stanza that is not LID-addressed yields {@link Disposition#DROP} with the resolved type,
     *       which is empty for an ordinal-less but decodable tag;</li>
     *   <li>a message whose call object does not yet exist yields {@link Disposition#BUFFER} with the
     *       resolved type, which is empty for an ordinal-less but decodable tag;</li>
     *   <li>otherwise the message yields {@link Disposition#PROCESS} with the resolved type, which is
     *       empty for an ordinal-less but decodable tag.</li>
     * </ul>
     *
     * @param payload      the single child element of the {@code <call>} stanza
     * @param senderLid    the {@code sender_lid} attribute from the {@code <call>} envelope, or
     *                     {@code null} when the stanza is not LID-addressed
     * @param callExists   whether a call object already exists for the payload's call identifier
     * @return the classification verdict; never {@code null}
     * @throws NullPointerException if {@code payload} is {@code null}
     */
    public Verdict classify(Stanza payload, Jid senderLid, boolean callExists) {
        Objects.requireNonNull(payload, "payload cannot be null");

        var callId = payload.getAttributeAsString(CALL_ID_ATTRIBUTE, null);
        var callCreator = payload.getAttributeAsJid(CALL_CREATOR_ATTRIBUTE, null);
        var callCreatorOpt = Optional.ofNullable(callCreator);
        if (callId == null || callId.isEmpty() || isAllZeroCallId(callId) || callCreator == null) {
            return new Verdict(Disposition.DROP, Optional.empty(), Optional.ofNullable(callId), callCreatorOpt);
        }

        var type = Calls2SignalingType.ofWireTag(payload.description());
        if (type.isEmpty() && !Calls2CallStanza.isKnownTag(payload.description())) {
            return new Verdict(Disposition.DROP, Optional.empty(), Optional.of(callId), callCreatorOpt);
        }

        if (!isLidAddressed(senderLid, callCreator)) {
            return new Verdict(Disposition.DROP, type, Optional.of(callId), callCreatorOpt);
        }

        if (!callExists) {
            return new Verdict(Disposition.BUFFER, type, Optional.of(callId), callCreatorOpt);
        }

        return new Verdict(Disposition.PROCESS, type, Optional.of(callId), callCreatorOpt);
    }

    /**
     * Returns whether a non-empty call-id is the all-zero call-id the engine treats as blank.
     *
     * <p>The native validator rejects a 64-byte call-id buffer that is all zeros; the wire form of such
     * a buffer is a hex id whose every character is {@code '0'}. A call-id that is all {@code '0'}
     * characters is therefore the typed-{@link String} analogue of the all-zero buffer and is rejected
     * the same way as an absent or empty call-id. The argument is assumed already non-empty; an empty
     * string is handled by the caller's emptiness check.
     *
     * @param callId the non-empty call-id read from the payload
     * @return {@code true} when every character of {@code callId} is {@code '0'}
     */
    private boolean isAllZeroCallId(String callId) {
        for (var index = 0; index < callId.length(); index++) {
            if (callId.charAt(index) != '0') {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether an inbound call stanza is LID-addressed.
     *
     * <p>The engine drops a call stanza before dispatch unless it is LID-addressed: the modern call
     * plane addresses every device by LID, so a stanza carrying neither a {@code sender_lid} on the
     * envelope nor a LID-server {@code call-creator} predates the LID migration and is not routed. A
     * stanza with a {@code sender_lid} is LID-addressed by definition; otherwise the call creator's
     * server is inspected.
     *
     * @param senderLid   the {@code sender_lid} attribute from the {@code <call>} envelope, or
     *                    {@code null} when absent
     * @param callCreator the {@code call-creator} device JID from the payload
     * @return {@code true} when the stanza is LID-addressed, {@code false} otherwise
     */
    private boolean isLidAddressed(Jid senderLid, Jid callCreator) {
        return senderLid != null || callCreator.hasLidServer();
    }
}
