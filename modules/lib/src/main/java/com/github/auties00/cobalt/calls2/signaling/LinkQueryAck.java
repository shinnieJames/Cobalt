package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.call.CallLink;
import com.github.auties00.cobalt.model.call.CallLinkBuilder;
import com.github.auties00.cobalt.model.call.CallLinkMedia;
import com.github.auties00.cobalt.model.call.CallLinkWaitingRoom;
import com.github.auties00.cobalt.model.call.CallLinkWaitingRoomBuilder;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents the acknowledgement of a {@link LinkQueryStanza}: the relay's resolved call-link metadata.
 *
 * <p>A link-query ack is delivered inside the host stanza layer's shared {@code <ack>} envelope, whose
 * body echoes a {@code <link_query>} stanza carrying the full link metadata: the {@link #link() token and
 * media}, the creator's device and phone-number JIDs, the creator display username, whether the link is
 * bound to a scheduled event, and the nested waiting-room state. It is a parse-only result model, not a
 * transmittable action, so it implements no {@link CallMessage} contract; the decoded metadata is exposed
 * as a composed {@link #link() CallLink} value.
 *
 * <p>On the wire the acknowledged element is {@code <link_query token="..." media="<type>"
 * link_creator="..." link_creator_pn="..." link_creator_username="...">[<event/>][<waiting_room
 * is_admin="1" enabled="1"/>]</link_query>}.
 *
 * @implNote This implementation models the {@code <link_query>} ack body parsed by {@code LinkQueryAck}
 * in the wa-voip WASM module {@code ff-tScznZ8P} ({@code protocol/xmpp/stanzas/call_link.cc}, message
 * type {@code 32}). The creator-identity attributes reuse the group-roster {@code <user>} data offsets
 * ({@code user_lid}/{@code user_pn} family) and the nested waiting-room descriptor reuses the
 * {@code <waiting_room>} element ({@code 0x5a594}) with {@code is_admin} ({@code 0x58d7b}) and
 * {@code enabled} ({@code 0x8fe48}); the resulting {@link CallLink} value is composed with
 * {@link CallLinkBuilder} and the waiting-room state with the {@link CallLinkWaitingRoom} model.
 *
 * @param link the resolved call-link metadata; never {@code null}
 * @see Calls2SignalingType#LINK_QUERY_ACK
 * @see LinkQueryStanza
 * @see CallLink
 */
public record LinkQueryAck(CallLink link) {
    /**
     * The wire tag of the nested scheduled-event child whose presence marks a scheduled link.
     */
    private static final String EVENT_ELEMENT = "event";

    /**
     * The wire tag of the nested waiting-room descriptor child.
     */
    private static final String WAITING_ROOM_ELEMENT = "waiting_room";

    /**
     * The wire attribute naming the waiting-room admin flag.
     */
    private static final String IS_ADMIN_ATTRIBUTE = "is_admin";

    /**
     * The wire attribute naming the waiting-room enabled flag.
     */
    private static final String ENABLED_ATTRIBUTE = "enabled";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code link} is {@code null}
     */
    public LinkQueryAck {
        Objects.requireNonNull(link, "link cannot be null");
    }

    /**
     * Decodes a {@code <link_query>} ack stanza into a {@link LinkQueryAck}.
     *
     * <p>The presence of a nested {@code <event>} child sets the resolved link's scheduled flag; a nested
     * {@code <waiting_room>} child is decoded into a {@link CallLinkWaitingRoom} and attached. Absent
     * optional attributes leave their corresponding {@link CallLink} fields unset.
     *
     * @param stanza the echoed {@code <link_query>} stanza from the {@code <ack>} body
     * @return the decoded link-query ack
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code token} attribute is absent or the
     *                                {@code media} attribute is absent or unrecognized
     */
    public static LinkQueryAck of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var token = stanza.getRequiredAttributeAsString("token");
        var media = CallLinkMedia.ofWire(stanza.getAttributeAsString("media").orElse(null))
                .orElseThrow(() -> new NoSuchElementException("link_query ack is missing a recognized media attribute"));
        var builder = new CallLinkBuilder()
                .token(token)
                .media(media)
                .creator(stanza.getAttributeAsJid("link_creator").orElse(null))
                .creatorPn(stanza.getAttributeAsJid("link_creator_pn").orElse(null))
                .creatorUsername(stanza.getAttributeAsString("link_creator_username").orElse(null))
                .scheduled(stanza.getChild(EVENT_ELEMENT).isPresent());
        stanza.getChild(WAITING_ROOM_ELEMENT)
                .map(LinkQueryAck::decodeWaitingRoom)
                .ifPresent(builder::waitingRoom);
        return new LinkQueryAck(builder.build());
    }

    /**
     * Decodes the nested {@code <waiting_room>} descriptor into a {@link CallLinkWaitingRoom}.
     *
     * @param stanza the {@code <waiting_room>} descriptor stanza
     * @return the decoded waiting-room state
     */
    private static CallLinkWaitingRoom decodeWaitingRoom(Stanza stanza) {
        var admin = "1".equals(stanza.getAttributeAsString(IS_ADMIN_ATTRIBUTE).orElse("0"));
        var enabled = "1".equals(stanza.getAttributeAsString(ENABLED_ATTRIBUTE).orElse("0"));
        return new CallLinkWaitingRoomBuilder()
                .admin(admin)
                .enabled(enabled)
                .build();
    }
}
