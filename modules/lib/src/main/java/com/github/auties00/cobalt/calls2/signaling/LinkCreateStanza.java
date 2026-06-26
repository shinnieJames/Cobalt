package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.call.CallLinkCreate;
import com.github.auties00.cobalt.model.call.CallLinkMedia;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code <link_create>} signal: a request to mint a shareable call-link token.
 *
 * <p>A link-create request asks the relay for a fresh call-link token bound to a fixed
 * {@link #media() media kind}. It optionally pins the originating {@link #callCreator() creator device},
 * carries an in-flight {@link #callId() call id} when the link is generated from a call already in
 * progress, advertises a {@link #creatorUsername() creator username} when username-based calling is
 * enabled, requests the {@link #waitingRoomEnabled() waiting-room gate}, and, for scheduled calls,
 * nests an {@code <event start_time/>} child carrying the {@link #eventStartTime() event start}. The
 * relay answers on a {@link LinkCreateAck}, which surfaces the minted token.
 *
 * <p>Unlike the in-call action elements, {@code link_create} does not carry the universal
 * {@code call-id}/{@code call-creator} header pair: it is a control-plane request addressed to the
 * {@code call} service rather than to a peer, so only the attributes it explicitly sets are emitted.
 *
 * <p>On the wire the element is {@code <link_create media="<type>" call-creator="..." call-id="..."
 * link_creator_username="..." waiting_room_enabled="1"><event start_time="..."/></link_create>}.
 *
 * @implNote This implementation models the {@code <link_create>} element (data offset {@code 0x747e7})
 * built by {@code serialize_link_create} (fn11673) in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code protocol/xmpp/stanzas/call_link.cc}, message type {@code 0xb8}). The recovered attribute data
 * offsets are {@code media} ({@code 0xbfe7a}), {@code link_creator_username} ({@code 0x79d36}), and
 * {@code waiting_room_enabled} ({@code 0x8f44e}); the nested {@code <event>} element ({@code 0x1e905})
 * carries {@code start_time} ({@code 0x787a9}) as seconds since the epoch for scheduled calls. The
 * boolean {@code waiting_room_enabled} flag is emitted only when set, using the {@code '1'} literal
 * ({@code 0xca53c}) the module uses for every boolean attribute.
 *
 * @param media              the call-link media kind; never {@code null}
 * @param callCreator        the originating creator device JID, if pinned
 * @param callId             the in-flight call id when minting from an active call, if present
 * @param creatorUsername    the creator display username, if present
 * @param waitingRoomEnabled whether the waiting-room gate is requested at creation time
 * @param eventStartTime     the scheduled-event start instant, if the link is bound to an event
 * @see Calls2SignalingType#LINK_CREATE
 * @see LinkCreateAck
 * @see CallLinkCreate
 */
public record LinkCreateStanza(CallLinkMedia media,
                               Optional<Jid> callCreator,
                               Optional<String> callId,
                               Optional<String> creatorUsername,
                               boolean waitingRoomEnabled,
                               Optional<Instant> eventStartTime) implements CallMessage {
    /**
     * The wire element tag for a link-create signal.
     */
    public static final String ELEMENT = "link_create";

    /**
     * The wire attribute naming the media kind.
     */
    private static final String MEDIA_ATTRIBUTE = "media";

    /**
     * The wire attribute naming the originating creator device JID.
     */
    private static final String CALL_CREATOR_ATTRIBUTE = "call-creator";

    /**
     * The wire attribute naming the in-flight call id.
     */
    private static final String CALL_ID_ATTRIBUTE = "call-id";

    /**
     * The wire attribute naming the creator display username.
     */
    private static final String CREATOR_USERNAME_ATTRIBUTE = "link_creator_username";

    /**
     * The wire attribute naming the waiting-room gate marker.
     */
    private static final String WAITING_ROOM_ENABLED_ATTRIBUTE = "waiting_room_enabled";

    /**
     * The wire tag of the nested scheduled-event child.
     */
    private static final String EVENT_ELEMENT = "event";

    /**
     * The wire attribute naming the scheduled-event start time.
     */
    private static final String START_TIME_ATTRIBUTE = "start_time";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public LinkCreateStanza {
        Objects.requireNonNull(media, "media cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(creatorUsername, "creatorUsername cannot be null");
        Objects.requireNonNull(eventStartTime, "eventStartTime cannot be null");
    }

    /**
     * Returns a link-create signal carrying only the media kind and the waiting-room toggle.
     *
     * <p>The creator device, call id, creator username, and scheduled-event start are left absent,
     * producing the minimal create request a fresh call link uses.
     *
     * @param media              the call-link media kind
     * @param waitingRoomEnabled whether the waiting-room gate is requested
     * @return the link-create signal
     * @throws NullPointerException if {@code media} is {@code null}
     */
    public static LinkCreateStanza of(CallLinkMedia media, boolean waitingRoomEnabled) {
        return new LinkCreateStanza(media, Optional.empty(), Optional.empty(), Optional.empty(),
                waitingRoomEnabled, Optional.empty());
    }

    /**
     * Returns a link-create signal mirroring a {@link CallLinkCreate} input model.
     *
     * <p>Every attribute is carried across verbatim: the media kind and creator device JID become the
     * required and pinned components, and the optional call id, creator username, waiting-room toggle, and
     * scheduled-event start are taken from the input model's optional fields.
     *
     * @param input the call-link create input model
     * @return the link-create signal
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public static LinkCreateStanza from(CallLinkCreate input) {
        Objects.requireNonNull(input, "input cannot be null");
        return new LinkCreateStanza(
                input.media(),
                Optional.of(input.callCreator()),
                input.callId(),
                input.creatorUsername(),
                input.waitingRoomEnabled(),
                input.eventStartTime());
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#LINK_CREATE}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.LINK_CREATE;
    }

    /**
     * Builds the {@code <link_create media call-creator call-id link_creator_username
     * waiting_room_enabled><event start_time/></link_create>} action stanza.
     *
     * <p>Each optional attribute is omitted when its backing component is absent; the
     * {@code waiting_room_enabled} marker is written only when {@link #waitingRoomEnabled()} is
     * {@code true}, and the {@code <event>} child is nested only when {@link #eventStartTime()} is
     * present, carrying the start time as seconds since the epoch.
     *
     * @return the link-create action stanza
     */
    @Override
    public Stanza toStanza() {
        var builder = new StanzaBuilder()
                .description(ELEMENT)
                .attribute(MEDIA_ATTRIBUTE, media.wireValue())
                .attribute(CALL_CREATOR_ATTRIBUTE, callCreator.orElse(null), callCreator.isPresent())
                .attribute(CALL_ID_ATTRIBUTE, callId.orElse(null), callId.isPresent())
                .attribute(CREATOR_USERNAME_ATTRIBUTE, creatorUsername.orElse(null), creatorUsername.isPresent())
                .attribute(WAITING_ROOM_ENABLED_ATTRIBUTE, "1", waitingRoomEnabled);
        eventStartTime.ifPresent(start -> builder.content(new StanzaBuilder()
                .description(EVENT_ELEMENT)
                .attribute(START_TIME_ATTRIBUTE, start.getEpochSecond())
                .build()));
        return builder.build();
    }

    /**
     * Decodes a {@code <link_create>} action stanza into a {@link LinkCreateStanza}.
     *
     * <p>An unrecognized or absent {@code media} attribute is rejected, since the media kind is a
     * required component; an absent {@code waiting_room_enabled} attribute classifies to {@code false},
     * and a missing {@code <event>} child yields an empty {@link #eventStartTime()}.
     *
     * @param stanza the {@code <link_create>} stanza
     * @return the decoded link-create signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code media} attribute is absent or unrecognized
     */
    public static LinkCreateStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var media = CallLinkMedia.ofWire(stanza.getAttributeAsString(MEDIA_ATTRIBUTE).orElse(null))
                .orElseThrow(() -> new NoSuchElementException("link_create is missing a recognized media attribute"));
        var callCreator = stanza.getAttributeAsJid(CALL_CREATOR_ATTRIBUTE);
        var callId = stanza.getAttributeAsString(CALL_ID_ATTRIBUTE);
        var creatorUsername = stanza.getAttributeAsString(CREATOR_USERNAME_ATTRIBUTE);
        var waitingRoomEnabled = "1".equals(stanza.getAttributeAsString(WAITING_ROOM_ENABLED_ATTRIBUTE).orElse("0"));
        var eventStartTime = stanza.getChild(EVENT_ELEMENT)
                .flatMap(event -> event.getAttributeAsLong(START_TIME_ATTRIBUTE)
                        .stream()
                        .mapToObj(Instant::ofEpochSecond)
                        .findFirst());
        return new LinkCreateStanza(media, callCreator, callId, creatorUsername, waitingRoomEnabled, eventStartTime);
    }
}
