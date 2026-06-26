package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents the acknowledgement of a {@link WaitingRoomLeaveStanza}: the relay confirming a withdrawal.
 *
 * <p>A waiting-room leave ack is delivered inside the host stanza layer's shared {@code <receipt>}
 * envelope and confirms the participant's withdrawal from the lobby. It carries only the universal call
 * header identifying which call's lobby the participant left. It is a parse-only result model, not a
 * transmittable action, so it implements no {@link CallMessage} contract.
 *
 * @implNote This implementation models the waiting-room leave ack of the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code features/waiting_room.cc}, message type {@code 68}), delivered on the shared
 * {@code <receipt>} path with the common header stamped by {@code populate_common_call_attr} (fn11591):
 * {@code call-id} ({@code 0x888f9}) and {@code call-creator} ({@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @see Calls2SignalingType#WAITING_ROOM_LEAVE_ACK
 * @see WaitingRoomLeaveStanza
 */
public record WaitingRoomLeaveAck(String callId, Jid callCreator) {
    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public WaitingRoomLeaveAck {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
    }

    /**
     * Decodes a waiting-room leave ack stanza into a {@link WaitingRoomLeaveAck}.
     *
     * @param stanza the echoed waiting-room leave stanza from the {@code <receipt>} body
     * @return the decoded leave ack
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static WaitingRoomLeaveAck of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        return new WaitingRoomLeaveAck(callId, callCreator);
    }
}
