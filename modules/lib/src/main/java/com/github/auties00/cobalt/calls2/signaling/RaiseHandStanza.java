package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <raise_hand>} in-call action: a participant raises or lowers a hand in a group
 * call.
 *
 * <p>A raise-hand action reports whether the sender currently has a hand raised. It carries the
 * universal call header, a {@code raise-hand-state} flag ({@code "1"} raised, {@code "0"} lowered),
 * and an optional {@code broadcast} flag set when the action is fanned out to every participant. The
 * engine feeds the resulting state into the grid-ranking comparator (hand-raised participants sort
 * first) and surfaces a raise-hand state-change event to the host.
 *
 * <p>Unlike most in-call actions {@code raise_hand} has no entry in the numeric
 * {@code voip_signaling_message_type} table; it is carried inside the {@link Calls2SignalingType#NOTIFY
 * notify} message container ({@code 0x68}) and dispatched on its wire tag, so {@link #type()} returns
 * {@code null}.
 *
 * <p>On the wire the element is {@code <raise_hand call-id="..." call-creator="..."
 * raise-hand-state="1|0" broadcast="1"/>}.
 *
 * @implNote This implementation models the {@code <raise_hand>} element built by
 * {@code serialize_raise_hand} and parsed by {@code deserialize_raise_hand} in the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code stanzas/in_call_actions.cc}), carried in message-container type
 * {@code 0x68}. The parser accepts either {@code raise-hand-state} or the legacy
 * {@code hand-raise-state} spelling; this decoder reads {@code raise-hand-state} first and falls back
 * to {@code hand-raise-state}, and always emits the canonical {@code raise-hand-state}. Attributes are
 * stamped over the common header written by {@code populate_common_call_attr} (fn11591):
 * {@code call-id} (data offset {@code 0x888f9}) and {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param raised      {@code true} when the hand is raised, {@code false} when lowered
 * @param broadcast   {@code true} when the action is fanned out to all participants
 * @see Calls2SignalingType#NOTIFY
 */
public record RaiseHandStanza(String callId, Jid callCreator, boolean raised, boolean broadcast)
        implements InCallActionStanza {
    /**
     * The wire element tag for a raise-hand action.
     */
    public static final String ELEMENT = "raise_hand";

    /**
     * The canonical wire attribute carrying the raise-hand state.
     */
    private static final String RAISE_HAND_STATE_ATTRIBUTE = "raise-hand-state";

    /**
     * The legacy wire attribute carrying the raise-hand state, accepted on inbound nodes.
     */
    private static final String HAND_RAISE_STATE_ATTRIBUTE = "hand-raise-state";

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
    public RaiseHandStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>A raise-hand action has no entry in the numeric {@code voip_signaling_message_type} table, so
     * this projection has no {@link Calls2SignalingType} and the method returns {@code null}; the
     * element is dispatched on its {@link #ELEMENT wire tag} inside the {@link Calls2SignalingType#NOTIFY
     * notify} container instead.
     *
     * @return {@code null}, since raise-hand carries no taxonomy ordinal
     */
    @Override
    public Calls2SignalingType type() {
        return null;
    }

    /**
     * Builds the {@code <raise_hand call-id call-creator raise-hand-state broadcast/>} action stanza.
     *
     * <p>The {@code raise-hand-state} attribute serializes to {@code 1} when {@link #raised()} is
     * {@code true} and {@code 0} otherwise; the {@code broadcast} attribute is omitted unless
     * {@link #broadcast()} is {@code true}.
     *
     * @return the raise-hand action stanza
     */
    @Override
    public Stanza toStanza() {
        return CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(RAISE_HAND_STATE_ATTRIBUTE, raised ? FLAG_TRUE : FLAG_FALSE)
                .attribute(BROADCAST_ATTRIBUTE, FLAG_TRUE, broadcast)
                .build();
    }

    /**
     * Decodes a {@code <raise_hand>} action stanza into a {@link RaiseHandStanza}.
     *
     * <p>The raise-hand state is read from {@code raise-hand-state}, falling back to the legacy
     * {@code hand-raise-state} spelling; the required state attribute is the literal {@code 1} or
     * {@code 0}. An absent {@code broadcast} decodes to {@code false}.
     *
     * @param stanza the {@code <raise_hand>} stanza
     * @return the decoded raise-hand action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent, or if neither {@code raise-hand-state} nor
     *                                {@code hand-raise-state} is present
     */
    public static RaiseHandStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var state = stanza.getAttributeAsString(RAISE_HAND_STATE_ATTRIBUTE)
                .or(() -> stanza.getAttributeAsString(HAND_RAISE_STATE_ATTRIBUTE))
                .orElseThrow(() -> new NoSuchElementException(
                        "raise_hand requires raise-hand-state or hand-raise-state"));
        var broadcast = FLAG_TRUE.equals(stanza.getAttributeAsString(BROADCAST_ATTRIBUTE, FLAG_FALSE));
        return new RaiseHandStanza(callId, callCreator, FLAG_TRUE.equals(state), broadcast);
    }
}
