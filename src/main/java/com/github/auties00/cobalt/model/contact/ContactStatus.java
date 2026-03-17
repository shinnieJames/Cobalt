package com.github.auties00.cobalt.model.contact;


import com.github.auties00.cobalt.model.chat.Chat;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

import java.util.Arrays;
import java.util.Optional;

/**
 * A presence state for a {@link Contact}, describing whether the contact is currently
 * online, offline, typing a message, or recording an audio message.
 *
 * <p>In the WhatsApp protocol, presence information is exchanged through XMPP-style
 * {@code <chatstate>} stanzas. The server sends a {@code <composing>} element when a
 * contact begins typing, a {@code <composing media="audio">} element when a contact
 * begins recording an audio message, and a {@code <paused>} element when the contact
 * stops composing. Presence availability is communicated via the {@code type} attribute
 * on the {@code <presence>} stanza, using the values {@code "available"} and
 * {@code "unavailable"}.
 *
 * <p>By default, the WhatsApp server does not push presence updates for every contact.
 * Updates are sent automatically only when a contact sends a message or appears in the
 * recent contacts list. To receive real-time presence updates for a specific contact,
 * subscribe using
 * {@link com.github.auties00.cobalt.client.WhatsAppClient#subscribeToPresence(com.github.auties00.cobalt.model.jid.JidProvider)}.
 *
 * <p>This enum represents presence at the individual contact level. For group-level
 * presence (e.g. a participant typing in a group), use
 * {@link Chat#getPresence(com.github.auties00.cobalt.model.jid.JidProvider)}
 * instead.
 *
 * @see Contact#lastKnownPresence()
 */
@ProtobufEnum
public enum ContactStatus {
    /**
     * The contact is currently online and connected to WhatsApp. This state corresponds
     * to the {@code "available"} value in the XMPP presence stanza. When a contact comes
     * online, the server sends a presence update with this type, and the contact's last
     * seen timestamp is updated to the current time.
     */
    AVAILABLE(0),

    /**
     * The contact is currently offline or has disconnected from WhatsApp. This state
     * corresponds to the {@code "unavailable"} value in the XMPP presence stanza. This
     * is the default state assigned to a contact when no presence information has been
     * received yet. When the contact is unavailable, the last seen timestamp (if not
     * hidden by privacy settings) indicates when the contact was last online.
     */
    UNAVAILABLE(1),

    /**
     * The contact is currently typing a text message in the conversation. This state
     * corresponds to the {@code <composing>} element in the XMPP chatstate stanza
     * (without the {@code media} attribute). In the WhatsApp Web model this is
     * represented as the {@code "typing"} chatstate type. The composing state
     * automatically expires after 25 seconds if no further composing updates are
     * received from the server.
     */
    COMPOSING(2),

    /**
     * The contact is currently recording an audio message in the conversation. This
     * state corresponds to the {@code <composing media="audio">} element in the XMPP
     * chatstate stanza. In the WhatsApp Web model this is represented as the
     * {@code "recording_audio"} chatstate type. Like the composing state, this state
     * automatically expires after 25 seconds if no further updates are received.
     */
    RECORDING(3);

    /**
     * The protobuf index associated with this presence state.
     */
    final int index;

    /**
     * Constructs a new presence state with the given protobuf index.
     *
     * @param index the protobuf wire-format index for this state
     */
    ContactStatus(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    /**
     * Returns the protobuf index associated with this presence state.
     *
     * @return the protobuf wire-format index
     */
    public int index() {
        return index;
    }

    /**
     * Returns the {@code ContactStatus} whose {@link #name()} matches the given string,
     * ignoring case.
     *
     * <p>This method is used internally when parsing presence stanza attributes from
     * the server. The attribute value (e.g. {@code "available"}, {@code "composing"})
     * is matched case-insensitively against the enum constant names.
     *
     * @param name the presence state name to look up
     * @return an {@code Optional} containing the matching state, or an empty
     *         {@code Optional} if no constant matches
     */
    public static Optional<ContactStatus> of(String name) {
        return Arrays.stream(values())
                .filter(entry -> entry.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Returns the lowercase name of this presence state.
     *
     * @return the state name in lowercase (e.g. {@code "available"}, {@code "composing"})
     */
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
