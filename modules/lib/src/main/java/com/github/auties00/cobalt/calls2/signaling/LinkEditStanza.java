package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code <link_edit>} signal: a request to change an existing call link's settings.
 *
 * <p>A link-edit request asks the relay to mutate the configuration of the call link identified by
 * {@link #token() token}. The change is described by an {@link #action() action} verb plus the settings
 * the verb operates on; the one setting recovered in the wire grammar is the
 * {@link #waitingRoomEnabled() waiting-room toggle}, modeled as an optional so an edit can leave the
 * toggle untouched rather than always asserting a value. The relay answers on a {@link LinkEditAck}.
 *
 * <p>Like the other call-link control requests, {@code link_edit} carries no universal
 * {@code call-id}/{@code call-creator} header: it is addressed to the {@code call} service rather than
 * to a peer.
 *
 * <p>On the wire the element is {@code <link_edit token="..." action="..." waiting_room_enabled="1"/>}.
 *
 * @implNote This implementation models the {@code <link_edit>} element (data offset {@code 0x21f9d})
 * built by {@code serialize_link_edit} in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code protocol/xmpp/stanzas/call_link.cc}, message type {@code 55}); the matching {@code LinkEditAck}
 * (type {@code 56}) is parsed by {@code LinkEditAck} in {@code call_link.cc}. The {@code action}
 * attribute reuses the shared {@code action} data offset ({@code 0x56cc3}) and the
 * {@code waiting_room_enabled} attribute reuses data offset {@code 0x8f44e}, serialized as the
 * {@code '1'}/{@code '0'} literals the module uses for boolean attributes; here the toggle is optional
 * so an unset value omits the attribute entirely.
 *
 * @param token              the call-link token to edit; never {@code null}
 * @param action             the edit action verb, if present
 * @param waitingRoomEnabled the desired waiting-room gate state, present only when the edit asserts one
 * @see Calls2SignalingType#LINK_EDIT
 * @see LinkEditAck
 */
public record LinkEditStanza(String token, Optional<String> action, Optional<Boolean> waitingRoomEnabled)
        implements CallMessage {
    /**
     * The wire element tag for a link-edit signal.
     */
    public static final String ELEMENT = "link_edit";

    /**
     * The wire attribute naming the call-link token.
     */
    private static final String TOKEN_ATTRIBUTE = "token";

    /**
     * The wire attribute naming the edit action verb.
     */
    private static final String ACTION_ATTRIBUTE = "action";

    /**
     * The wire attribute naming the waiting-room gate state.
     */
    private static final String WAITING_ROOM_ENABLED_ATTRIBUTE = "waiting_room_enabled";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public LinkEditStanza {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(waitingRoomEnabled, "waitingRoomEnabled cannot be null");
    }

    /**
     * Returns a link-edit signal that toggles only the waiting-room gate.
     *
     * <p>The action verb is left absent and the toggle is asserted to the supplied value, producing the
     * shape an admin's "Require approval to join" switch emits.
     *
     * @param token              the call-link token to edit
     * @param waitingRoomEnabled the desired waiting-room gate state
     * @return the link-edit signal
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public static LinkEditStanza ofWaitingRoom(String token, boolean waitingRoomEnabled) {
        return new LinkEditStanza(token, Optional.empty(), Optional.of(waitingRoomEnabled));
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#LINK_EDIT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.LINK_EDIT;
    }

    /**
     * Builds the {@code <link_edit token action waiting_room_enabled/>} action stanza.
     *
     * <p>The {@code action} attribute is omitted when absent; the {@code waiting_room_enabled} attribute
     * is written as {@code '1'} or {@code '0'} only when the toggle is asserted, and omitted entirely
     * when {@link #waitingRoomEnabled()} is empty.
     *
     * @return the link-edit action stanza
     */
    @Override
    public Stanza toStanza() {
        return new StanzaBuilder()
                .description(ELEMENT)
                .attribute(TOKEN_ATTRIBUTE, token)
                .attribute(ACTION_ATTRIBUTE, action.orElse(null), action.isPresent())
                .attribute(WAITING_ROOM_ENABLED_ATTRIBUTE,
                        waitingRoomEnabled.map(value -> value ? "1" : "0").orElse(null),
                        waitingRoomEnabled.isPresent())
                .build();
    }

    /**
     * Decodes a {@code <link_edit>} action stanza into a {@link LinkEditStanza}.
     *
     * <p>An absent {@code waiting_room_enabled} attribute yields an empty {@link #waitingRoomEnabled()}
     * so a re-emitted edit omits the toggle exactly as it arrived; a present attribute classifies
     * through the {@code '1'}/{@code '0'} literal.
     *
     * @param stanza the {@code <link_edit>} stanza
     * @return the decoded link-edit signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code token} attribute is absent
     */
    public static LinkEditStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var token = stanza.getRequiredAttributeAsString(TOKEN_ATTRIBUTE);
        var action = stanza.getAttributeAsString(ACTION_ATTRIBUTE);
        var waitingRoomEnabled = stanza.getAttributeAsString(WAITING_ROOM_ENABLED_ATTRIBUTE)
                .map("1"::equals);
        return new LinkEditStanza(token, action, waitingRoomEnabled);
    }
}
