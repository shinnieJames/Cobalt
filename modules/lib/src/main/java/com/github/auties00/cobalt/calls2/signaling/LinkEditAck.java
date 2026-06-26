package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.stanza.Stanza;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the acknowledgement of a {@link LinkEditStanza}: the relay's confirmation of an edit.
 *
 * <p>A link-edit ack is delivered inside the host stanza layer's shared {@code <ack>} envelope, whose
 * body echoes a {@code <link_edit>} stanza confirming the {@link #token() edited token} and, when the edit
 * touched the waiting-room gate, the applied {@link #waitingRoomEnabled() gate state}. It is a parse-only
 * result model, not a transmittable action, so it implements no {@link CallMessage} contract.
 *
 * <p>On the wire the acknowledged element is {@code <link_edit token="..." waiting_room_enabled="1"/>}.
 *
 * @implNote This implementation models the {@code <link_edit>} ack body parsed by {@code LinkEditAck} in
 * the wa-voip WASM module {@code ff-tScznZ8P} ({@code protocol/xmpp/stanzas/call_link.cc}, message type
 * {@code 56}). The {@code waiting_room_enabled} attribute reuses data offset {@code 0x8f44e}, classified
 * through the {@code '1'}/{@code '0'} boolean literals; it is optional so an ack that did not touch the
 * gate yields an empty {@link #waitingRoomEnabled()}.
 *
 * @param token              the echoed call-link token; never {@code null}
 * @param waitingRoomEnabled the applied waiting-room gate state, present only when the ack echoed the
 *                           toggle
 * @see Calls2SignalingType#LINK_EDIT_ACK
 * @see LinkEditStanza
 */
public record LinkEditAck(String token, Optional<Boolean> waitingRoomEnabled) {
    /**
     * The wire attribute naming the echoed call-link token.
     */
    private static final String TOKEN_ATTRIBUTE = "token";

    /**
     * The wire attribute naming the waiting-room gate state.
     */
    private static final String WAITING_ROOM_ENABLED_ATTRIBUTE = "waiting_room_enabled";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code token} or {@code waitingRoomEnabled} is {@code null}
     */
    public LinkEditAck {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(waitingRoomEnabled, "waitingRoomEnabled cannot be null");
    }

    /**
     * Decodes a {@code <link_edit>} ack stanza into a {@link LinkEditAck}.
     *
     * @param stanza the echoed {@code <link_edit>} stanza from the {@code <ack>} body
     * @return the decoded link-edit ack
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code token} attribute is absent
     */
    public static LinkEditAck of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var token = stanza.getRequiredAttributeAsString(TOKEN_ATTRIBUTE);
        var waitingRoomEnabled = stanza.getAttributeAsString(WAITING_ROOM_ENABLED_ATTRIBUTE)
                .map("1"::equals);
        return new LinkEditAck(token, waitingRoomEnabled);
    }
}
