package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <mute_v2>} in-call action: a self mute-state change or a request to mute
 * another participant.
 *
 * <p>The {@code mute_v2} element carries EXACTLY ONE of two mutually exclusive intents, distinguished
 * by which attribute is present:
 * <ul>
 *   <li>a self mute-state report, carried as {@code mute-state="1"} (self muted) or
 *       {@code mute-state="0"} (self unmuted);
 *   <li>a request that the recipient mute itself, carried as {@code request-state="1"} (the
 *       group-admin peer-mute request).
 * </ul>
 * It additionally carries an optional {@code broadcast} flag, set when the action is fanned out to
 * every participant rather than addressed to a single peer. This supersedes the legacy {@code <mute>}
 * element ({@link Calls2SignalingType#MUTE}); calls2 emits and parses only {@code mute_v2}.
 *
 * <p>On the wire the element is {@code <mute_v2 call-id="..." call-creator="..." (request-state="1" |
 * mute-state="1|0") broadcast="1"/>}.
 *
 * @implNote This implementation models the {@code <mute_v2>} element built and parsed by
 * {@code deserialize_mute_v2} (and its serialize side) in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code stanzas/in_call_actions.cc}), carried in message-container type {@code 0x68} and projecting
 * to taxonomy ordinal {@code 26} ({@link Calls2SignalingType#MUTE_V2}). The parser enforces that
 * exactly one of {@code request-state} or {@code mute-state} is present ("either request-state or
 * mute-state should be present"); {@code mute-state} is the single-byte dictionary token {@code 79}
 * holding the literal {@code 0} or {@code 1}. Attributes are stamped over the common header written by
 * {@code populate_common_call_attr} (fn11591): {@code call-id} (data offset {@code 0x888f9}) and
 * {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param peerRequest {@code true} when this is a request that the recipient mute itself
 *                    ({@code request-state="1"}); {@code false} when this reports the sender's own
 *                    mute state
 * @param muted       the self mute state ({@code true} muted, {@code false} unmuted) when
 *                    {@code peerRequest} is {@code false}; ignored when {@code peerRequest} is
 *                    {@code true}
 * @param broadcast   {@code true} when the action is fanned out to all participants
 * @see Calls2SignalingType#MUTE_V2
 */
public record MuteV2Stanza(String callId, Jid callCreator, boolean peerRequest, boolean muted, boolean broadcast)
        implements InCallActionStanza {
    /**
     * The wire element tag for a mute_v2 action.
     */
    public static final String ELEMENT = "mute_v2";

    /**
     * The wire attribute carrying a request that the recipient mute itself.
     */
    private static final String REQUEST_STATE_ATTRIBUTE = "request-state";

    /**
     * The wire attribute carrying the sender's own mute state.
     */
    private static final String MUTE_STATE_ATTRIBUTE = "mute-state";

    /**
     * The wire attribute flagging a fanned-out action.
     */
    private static final String BROADCAST_ATTRIBUTE = "broadcast";

    /**
     * The wire literal for a set ({@code true}) voip boolean flag.
     */
    private static final String FLAG_TRUE = "1";

    /**
     * The wire literal for a clear ({@code false}) voip boolean flag.
     */
    private static final String FLAG_FALSE = "0";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public MuteV2Stanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * Returns a {@code mute_v2} reporting the sender's own mute state.
     *
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @param muted       {@code true} when the sender is muted, {@code false} when unmuted
     * @param broadcast   {@code true} when the action is fanned out to all participants
     * @return the self mute-state action
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public static MuteV2Stanza ofSelfState(String callId, Jid callCreator, boolean muted, boolean broadcast) {
        return new MuteV2Stanza(callId, callCreator, false, muted, broadcast);
    }

    /**
     * Returns a {@code mute_v2} requesting that the recipient mute itself.
     *
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @param broadcast   {@code true} when the request is fanned out to all participants
     * @return the peer-mute request action
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public static MuteV2Stanza ofPeerRequest(String callId, Jid callCreator, boolean broadcast) {
        return new MuteV2Stanza(callId, callCreator, true, false, broadcast);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#MUTE_V2}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.MUTE_V2;
    }

    /**
     * Builds the {@code <mute_v2 call-id call-creator (request-state|mute-state) broadcast/>} action
     * stanza.
     *
     * <p>Exactly one of {@code request-state} or {@code mute-state} is written: {@code request-state}
     * (always {@code 1}) when {@link #peerRequest()} is {@code true}, otherwise {@code mute-state}
     * carrying {@code 1} or {@code 0} for {@link #muted()}. The {@code broadcast} attribute is omitted
     * unless {@link #broadcast()} is {@code true}.
     *
     * @return the mute_v2 action stanza
     */
    @Override
    public Stanza toStanza() {
        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator);
        if (peerRequest) {
            builder.attribute(REQUEST_STATE_ATTRIBUTE, FLAG_TRUE);
        } else {
            builder.attribute(MUTE_STATE_ATTRIBUTE, muted ? FLAG_TRUE : FLAG_FALSE);
        }
        return builder
                .attribute(BROADCAST_ATTRIBUTE, FLAG_TRUE, broadcast)
                .build();
    }

    /**
     * Decodes a {@code <mute_v2>} action stanza into a {@link MuteV2Stanza}.
     *
     * <p>The stanza is classified as a peer-mute request when it carries {@code request-state};
     * otherwise it is read as a self mute-state report whose {@link #muted()} reflects the
     * {@code mute-state} attribute. An absent {@code broadcast} decodes to {@code false}.
     *
     * @param stanza the {@code <mute_v2>} stanza
     * @return the decoded mute_v2 action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent, or if neither {@code request-state} nor
     *                                {@code mute-state} is present
     */
    public static MuteV2Stanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var broadcast = FLAG_TRUE.equals(stanza.getAttributeAsString(BROADCAST_ATTRIBUTE, FLAG_FALSE));
        if (stanza.getAttributeAsString(REQUEST_STATE_ATTRIBUTE).isPresent()) {
            return new MuteV2Stanza(callId, callCreator, true, false, broadcast);
        }
        var muteState = stanza.getAttributeAsString(MUTE_STATE_ATTRIBUTE)
                .orElseThrow(() -> new NoSuchElementException(
                        "mute_v2 requires either request-state or mute-state"));
        return new MuteV2Stanza(callId, callCreator, false, FLAG_TRUE.equals(muteState), broadcast);
    }
}
