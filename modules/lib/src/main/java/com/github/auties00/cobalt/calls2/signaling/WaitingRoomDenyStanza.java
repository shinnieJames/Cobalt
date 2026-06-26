package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code <waiting_room_deny>} signal: the host denying waiting-room participants.
 *
 * <p>A waiting-room deny is sent by the call host to reject one or more queued participants, removing
 * them from the lobby without admitting them to the call. It carries the universal call header and the
 * {@link #users() participant list} to deny; an empty list expresses a deny-all action. The relay answers
 * on a waiting-room deny receipt.
 *
 * <p>The host learns which participants are waiting from the inbound waiting-room state events before
 * issuing this deny.
 *
 * <p>On the wire the element is {@code <waiting_room_deny call-id="..." call-creator="..."><user
 * jid="..."/>*</waiting_room_deny>}.
 *
 * @implNote This implementation models the waiting-room deny element of the wa-voip WASM module
 * {@code ff-tScznZ8P}: {@code wa_call_waiting_room_deny} (fn11142) sends signaling IQ type {@code 0x49}
 * (message type {@code 73}) under the same admin guards as admit. It shares the {@code <waiting_room>}
 * grammar (element data offset {@code 0x5a594}) centralized in {@link WaitingRoomStanzas}; the element tag
 * is taken from {@link Calls2SignalingType#WAITING_ROOM_DENY} and each denied participant is a
 * {@code <user>} entry decoded by {@link WaitingRoomUser}.
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param users       the participants to deny; never {@code null}, empty for the deny-all action
 * @see Calls2SignalingType#WAITING_ROOM_DENY
 * @see WaitingRoomUser
 */
public record WaitingRoomDenyStanza(String callId, Jid callCreator, List<WaitingRoomUser> users)
        implements CallMessage {
    /**
     * Validates the record components and defensively copies the participant list.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code users} is
     *                              {@code null}
     */
    public WaitingRoomDenyStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(users, "users cannot be null");
        users = List.copyOf(users);
    }

    /**
     * Returns a deny signal targeting the single participant with the given JID.
     *
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @param userJid     the participant to deny
     * @return the deny signal
     * @throws NullPointerException if any argument is {@code null}
     */
    public static WaitingRoomDenyStanza of(String callId, Jid callCreator, Jid userJid) {
        return new WaitingRoomDenyStanza(callId, callCreator, List.of(WaitingRoomUser.of(userJid)));
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#WAITING_ROOM_DENY}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.WAITING_ROOM_DENY;
    }

    /**
     * Builds the {@code <waiting_room_deny call-id call-creator><user/>*</waiting_room_deny>} action
     * stanza.
     *
     * <p>The element carries one {@code <user>} child per denied participant.
     *
     * @return the waiting-room deny action stanza
     */
    @Override
    public Stanza toStanza() {
        return WaitingRoomStanzas.build(type().wireTag().orElseThrow(), callId, callCreator,
                Optional.empty(), Optional.empty(), Optional.empty(), users);
    }

    /**
     * Decodes a {@code <waiting_room_deny>} action stanza into a {@link WaitingRoomDenyStanza}.
     *
     * @param stanza the {@code <waiting_room_deny>} stanza
     * @return the decoded waiting-room deny signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static WaitingRoomDenyStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var users = WaitingRoomStanzas.users(stanza);
        return new WaitingRoomDenyStanza(callId, callCreator, users);
    }
}
