package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code <waiting_room_update>} signal: a refresh of the full waiting-room state.
 *
 * <p>A waiting-room update announces the current lobby state: the {@link #enabled() gate flag}, whether
 * the receiving device {@link #admin() is the admin}, the optional {@link #linkToken() call-link token}
 * the lobby is keyed by, and the {@link #users() roster} of participants currently waiting. It is the
 * message a host receives to learn who is queued, driving the waiting-room state change surfaced to the
 * application; it is also the snapshot pushed when the lobby composition changes.
 *
 * <p>On the wire the element is {@code <waiting_room_update call-id="..." call-creator="..." enabled="1"
 * is_admin="1" link-token="..."><user .../>*</waiting_room_update>}.
 *
 * @implNote This implementation models the waiting-room update element of the wa-voip WASM module
 * {@code ff-tScznZ8P} ({@code handlers/waiting_room.cc}, message type {@code 75}; parsed inbound by
 * {@code deserialize_waiting_room_update}), sharing the {@code <waiting_room>} grammar (element data
 * offset {@code 0x5a594}) centralized in {@link WaitingRoomStanzas}: {@code enabled} ({@code 0x8fe48}),
 * {@code is_admin} ({@code 0x58d7b}), and {@code link-token} ({@code 0x598f0}). The element tag is taken
 * from {@link Calls2SignalingType#WAITING_ROOM_UPDATE} and each waiting participant is a {@code <user>}
 * entry decoded by {@link WaitingRoomUser}.
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param enabled     the gate state, present only when the update carried it
 * @param admin       whether the receiving device is the admin, present only when the update carried it
 * @param linkToken   the call-link token the lobby is keyed by, if present
 * @param users       the waiting participants; never {@code null}, empty when none are queued
 * @see Calls2SignalingType#WAITING_ROOM_UPDATE
 * @see WaitingRoomUser
 */
public record WaitingRoomUpdateStanza(String callId,
                                      Jid callCreator,
                                      Optional<Boolean> enabled,
                                      Optional<Boolean> admin,
                                      Optional<String> linkToken,
                                      List<WaitingRoomUser> users) implements CallMessage {
    /**
     * Validates the record components and defensively copies the participant list.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public WaitingRoomUpdateStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(enabled, "enabled cannot be null");
        Objects.requireNonNull(admin, "admin cannot be null");
        Objects.requireNonNull(linkToken, "linkToken cannot be null");
        Objects.requireNonNull(users, "users cannot be null");
        users = List.copyOf(users);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#WAITING_ROOM_UPDATE}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.WAITING_ROOM_UPDATE;
    }

    /**
     * Builds the {@code <waiting_room_update call-id call-creator enabled is_admin link-token><user/>*
     * </waiting_room_update>} action stanza.
     *
     * <p>Each optional flag and the link token are omitted when their backing component is absent; the
     * element carries one {@code <user>} child per waiting participant.
     *
     * @return the waiting-room update action stanza
     */
    @Override
    public Stanza toStanza() {
        return WaitingRoomStanzas.build(type().wireTag().orElseThrow(), callId, callCreator,
                enabled, linkToken, admin, users);
    }

    /**
     * Decodes a {@code <waiting_room_update>} action stanza into a {@link WaitingRoomUpdateStanza}.
     *
     * <p>Absent {@code enabled} and {@code is_admin} attributes yield empty optionals so a re-emitted
     * update preserves which flags arrived; every nested {@code <user>} child forms the waiting roster.
     *
     * @param stanza the {@code <waiting_room_update>} stanza
     * @return the decoded waiting-room update signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static WaitingRoomUpdateStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var enabled = WaitingRoomStanzas.enabled(stanza);
        var admin = WaitingRoomStanzas.admin(stanza);
        var linkToken = WaitingRoomStanzas.linkToken(stanza);
        var users = WaitingRoomStanzas.users(stanza);
        return new WaitingRoomUpdateStanza(callId, callCreator, enabled, admin, linkToken, users);
    }
}
