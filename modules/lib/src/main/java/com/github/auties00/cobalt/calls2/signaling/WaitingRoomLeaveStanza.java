package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.NoSuchElementException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code <waiting_room_leave>} signal: a joiner withdrawing from the waiting room.
 *
 * <p>A waiting-room leave is sent by a participant queued in a call-link lobby to cancel its pending
 * join before the host admits or denies it. It carries the universal call header and, when the lobby is
 * keyed by a call link, the {@link #linkToken() link token} the participant is leaving. The relay answers
 * on a waiting-room leave receipt.
 *
 * <p>On the wire the element is {@code <waiting_room_leave call-id="..." call-creator="..."
 * link-token="..."/>}.
 *
 * @implNote This implementation models the waiting-room leave element of the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code features/waiting_room.cc}, message type {@code 67}), sharing the
 * {@code <waiting_room>} grammar (element data offset {@code 0x5a594}) centralized in
 * {@link WaitingRoomStanzas}. The element tag is taken from {@link Calls2SignalingType#WAITING_ROOM_LEAVE}.
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param linkToken   the call-link token being left, if present
 * @see Calls2SignalingType#WAITING_ROOM_LEAVE
 */
public record WaitingRoomLeaveStanza(String callId, Jid callCreator, Optional<String> linkToken)
        implements CallMessage {
    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code linkToken} is
     *                              {@code null}
     */
    public WaitingRoomLeaveStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(linkToken, "linkToken cannot be null");
    }

    /**
     * Returns a leave signal that withdraws from the lobby without naming a call-link token.
     *
     * @param callId      the call identifier
     * @param callCreator the call creator's device JID
     * @return the leave signal
     * @throws NullPointerException if {@code callId} or {@code callCreator} is {@code null}
     */
    public static WaitingRoomLeaveStanza of(String callId, Jid callCreator) {
        return new WaitingRoomLeaveStanza(callId, callCreator, Optional.empty());
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#WAITING_ROOM_LEAVE}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.WAITING_ROOM_LEAVE;
    }

    /**
     * Builds the {@code <waiting_room_leave call-id call-creator link-token/>} action stanza.
     *
     * <p>An absent link token is omitted; the element carries no {@code <user>} children.
     *
     * @return the waiting-room leave action stanza
     */
    @Override
    public Stanza toStanza() {
        return WaitingRoomStanzas.build(type().wireTag().orElseThrow(), callId, callCreator,
                Optional.empty(), linkToken, Optional.empty(), List.of());
    }

    /**
     * Decodes a {@code <waiting_room_leave>} action stanza into a {@link WaitingRoomLeaveStanza}.
     *
     * @param stanza the {@code <waiting_room_leave>} stanza
     * @return the decoded waiting-room leave signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static WaitingRoomLeaveStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var linkToken = WaitingRoomStanzas.linkToken(stanza);
        return new WaitingRoomLeaveStanza(callId, callCreator, linkToken);
    }
}
