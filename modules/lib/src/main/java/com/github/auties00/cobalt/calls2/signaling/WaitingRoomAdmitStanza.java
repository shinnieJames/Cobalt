package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code <waiting_room_admit>} signal: the host admitting waiting-room participants.
 *
 * <p>A waiting-room admit is sent by the call host to release one or more queued participants from the
 * lobby into the active call. It carries the universal call header and the {@link #users() participant
 * list} to admit; an empty list expresses the admit-all action the host issues to release every pending
 * participant at once. The relay answers on a waiting-room admit receipt.
 *
 * <p>The host learns which participants are waiting from the inbound waiting-room state events before
 * issuing this admit.
 *
 * <p>On the wire the element is {@code <waiting_room_admit call-id="..." call-creator="..."><user
 * jid="..."/>*</waiting_room_admit>}.
 *
 * @implNote This implementation models the waiting-room admit element of the wa-voip WASM module
 * {@code ff-tScznZ8P}: {@code call_waiting_room_admit_internal} (fn11138) builds the admit list and sends
 * signaling IQ type {@code 0x47} (message type {@code 71}); an empty list admits every waiting
 * participant. It shares the {@code <waiting_room>} grammar (element data offset {@code 0x5a594})
 * centralized in {@link WaitingRoomStanzas}; the element tag is taken from
 * {@link Calls2SignalingType#WAITING_ROOM_ADMIT} and each admitted participant is a {@code <user>} entry
 * decoded by {@link WaitingRoomUser}.
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param users       the participants to admit; never {@code null}, empty for the admit-all action
 * @see Calls2SignalingType#WAITING_ROOM_ADMIT
 * @see WaitingRoomUser
 */
public record WaitingRoomAdmitStanza(String callId, Jid callCreator, List<WaitingRoomUser> users)
        implements CallMessage {
    /**
     * Validates the record components and defensively copies the participant list.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code users} is
     *                              {@code null}
     */
    public WaitingRoomAdmitStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(users, "users cannot be null");
        users = List.copyOf(users);
    }

    /**
     * Returns an admit signal expressing the admit-all action with no explicit participants.
     *
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @return the admit-all signal
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public static WaitingRoomAdmitStanza all(String callId, Jid callCreator) {
        return new WaitingRoomAdmitStanza(callId, callCreator, List.of());
    }

    /**
     * Returns an admit signal targeting the single participant with the given JID.
     *
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @param userJid     the participant to admit
     * @return the admit signal
     * @throws NullPointerException if any argument is {@code null}
     */
    public static WaitingRoomAdmitStanza of(String callId, Jid callCreator, Jid userJid) {
        return new WaitingRoomAdmitStanza(callId, callCreator, List.of(WaitingRoomUser.of(userJid)));
    }

    /**
     * Returns whether this signal expresses the admit-all action.
     *
     * @return {@code true} when the participant list is empty; {@code false} otherwise
     */
    public boolean isAdmitAll() {
        return users.isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#WAITING_ROOM_ADMIT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.WAITING_ROOM_ADMIT;
    }

    /**
     * Builds the {@code <waiting_room_admit call-id call-creator><user/>*</waiting_room_admit>} action
     * stanza.
     *
     * <p>The element carries one {@code <user>} child per admitted participant, or none for the
     * admit-all action.
     *
     * @return the waiting-room admit action stanza
     */
    @Override
    public Stanza toStanza() {
        return WaitingRoomStanzas.build(type().wireTag().orElseThrow(), callId, callCreator,
                Optional.empty(), Optional.empty(), Optional.empty(), users);
    }

    /**
     * Decodes a {@code <waiting_room_admit>} action stanza into a {@link WaitingRoomAdmitStanza}.
     *
     * <p>An absent {@code <user>} list classifies to the admit-all action.
     *
     * @param stanza the {@code <waiting_room_admit>} stanza
     * @return the decoded waiting-room admit signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static WaitingRoomAdmitStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var users = WaitingRoomStanzas.users(stanza);
        return new WaitingRoomAdmitStanza(callId, callCreator, users);
    }
}
