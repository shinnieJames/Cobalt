package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents the acknowledgement of a {@link WaitingRoomDenyStanza}: the relay confirming denials.
 *
 * <p>A waiting-room deny ack is delivered inside the host stanza layer's shared {@code <receipt>}
 * envelope and confirms which participants were rejected from the lobby. It carries the universal call
 * header and the {@link #users() denied-participant list} the relay echoes back. It is a parse-only
 * result model, not a transmittable action, so it implements no {@link CallMessage} contract.
 *
 * @implNote This implementation models the waiting-room deny ack of the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code features/waiting_room.cc}, message type {@code 74}), delivered on the shared
 * {@code <receipt>} path; the inbound validator expects a {@code 43628}-byte fixed header reflecting the
 * echoed participant roster. Each echoed participant is a {@code <user>} entry decoded by
 * {@link WaitingRoomUser}, over the common header stamped by {@code populate_common_call_attr} (fn11591).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param users       the denied participants the relay echoed; never {@code null}, empty when none
 * @see Calls2SignalingType#WAITING_ROOM_DENY_ACK
 * @see WaitingRoomDenyStanza
 * @see WaitingRoomUser
 */
public record WaitingRoomDenyAck(String callId, Jid callCreator, List<WaitingRoomUser> users) {
    /**
     * Validates the record components and defensively copies the participant list.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code users} is
     *                              {@code null}
     */
    public WaitingRoomDenyAck {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(users, "users cannot be null");
        users = List.copyOf(users);
    }

    /**
     * Decodes a waiting-room deny ack stanza into a {@link WaitingRoomDenyAck}.
     *
     * @param stanza the echoed waiting-room deny stanza from the {@code <receipt>} body
     * @return the decoded deny ack
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static WaitingRoomDenyAck of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var users = WaitingRoomStanzas.users(stanza);
        return new WaitingRoomDenyAck(callId, callCreator, users);
    }
}
