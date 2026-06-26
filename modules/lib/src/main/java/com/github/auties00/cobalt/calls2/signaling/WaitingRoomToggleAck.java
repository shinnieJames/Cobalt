package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the acknowledgement of a {@link WaitingRoomToggleStanza}: the relay confirming a gate flip.
 *
 * <p>A waiting-room toggle ack is delivered inside the host stanza layer's shared {@code <receipt>}
 * envelope and confirms the applied gate state. It carries the universal call header and, when the relay
 * echoes it, the resulting {@link #enabled() gate state}. It is a parse-only result model, not a
 * transmittable action, so it implements no {@link CallMessage} contract.
 *
 * @implNote This implementation models the waiting-room toggle ack of the wa-voip WASM module
 * {@code ff-tScznZ8P} (parsed by {@code deserialize_waiting_room_toggle_ack} in
 * {@code features/waiting_room.cc}, message type {@code 70}), delivered on the shared {@code <receipt>}
 * path. The {@code enabled} attribute ({@code 0x8fe48}) is classified through the {@code '1'}/{@code '0'}
 * boolean literal and is optional, so an ack that omits it yields an empty {@link #enabled()}.
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param enabled     the applied gate state, present only when the ack echoed it
 * @see Calls2SignalingType#WAITING_ROOM_TOGGLE_ACK
 * @see WaitingRoomToggleStanza
 */
public record WaitingRoomToggleAck(String callId, Jid callCreator, Optional<Boolean> enabled) {
    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code enabled} is
     *                              {@code null}
     */
    public WaitingRoomToggleAck {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(enabled, "enabled cannot be null");
    }

    /**
     * Decodes a waiting-room toggle ack stanza into a {@link WaitingRoomToggleAck}.
     *
     * @param stanza the echoed waiting-room toggle stanza from the {@code <receipt>} body
     * @return the decoded toggle ack
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static WaitingRoomToggleAck of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var enabled = WaitingRoomStanzas.enabled(stanza);
        return new WaitingRoomToggleAck(callId, callCreator, enabled);
    }
}
