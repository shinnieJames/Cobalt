package com.github.auties00.cobalt.node.smax.voip;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <call><link_create/></call>} request that mints a fresh
 * shareable call-link token on the VoIP relay.
 *
 * @apiNote
 * Backs the "Create call link" UI surface and the scheduled-event call-link
 * generator. {@link SmaxLinkCreateResponse.Success#linkCreateToken()}
 * is suffixed onto {@code https://call.whatsapp.com/voice/} or
 * {@code https://call.whatsapp.com/video/} to produce the link the user shares.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutVoipLinkCreateRequest")
public final class SmaxLinkCreateRequest implements SmaxOperation.Request {
    /**
     * The optional media type carried by the {@code media} attribute.
     *
     * @apiNote
     * Either {@code "audio"} or {@code "video"} on the wire; kept as a raw
     * {@link String} so future relay-side enum extensions do not require a
     * Cobalt rebuild.
     */
    private final String linkCreateMedia;

    /**
     * The optional creator-device JID carried by the {@code call-creator}
     * attribute.
     *
     * @apiNote
     * Supplied when the link should be associated with a specific device of
     * the caller rather than the user's primary device.
     */
    private final Jid linkCreateCallCreator;

    /**
     * The optional call identifier carried by the {@code call-id} attribute.
     *
     * @apiNote
     * Populated when the call link is generated from an already in-flight
     * call rather than minted standalone.
     */
    private final String linkCreateCallId;

    /**
     * The optional creator-username displayed by the join-prompt surface.
     *
     * @apiNote
     * Resolved by {@code WAWebVoipCreateCallLink.createCallLink} when
     * username-based calling is gated on for the local account.
     */
    private final String linkCreateLinkCreatorUsername;

    /**
     * Whether the {@code waiting_room_enabled="1"} marker is emitted.
     *
     * @apiNote
     * Maps to the "Require approval to join" toggle on the call-link create
     * sheet. When {@code false} the attribute is omitted, which the relay
     * treats as disabled.
     */
    private final boolean linkCreateWaitingRoomEnabled;

    /**
     * The optional scheduled-event start instant.
     *
     * @apiNote
     * Supplied by the scheduled-call surface; rendered as an
     * {@code <event start_time="..."/>} child whose value is seconds since
     * the epoch.
     */
    private final Instant eventStartTime;

    /**
     * Constructs a request with every wire-level attribute spelled out.
     *
     * @apiNote
     * Most embedders should leave the optional fields {@code null} and let
     * the relay supply defaults; the only field most call-link UIs set
     * is {@code linkCreateMedia} ({@code "audio"} or {@code "video"}) and,
     * for scheduled calls, {@code eventStartTime}.
     *
     * @param linkCreateMedia               the optional media type; may be {@code null}
     * @param linkCreateCallCreator         the optional creator-device JID; may be {@code null}
     * @param linkCreateCallId              the optional pre-allocated call id; may be {@code null}
     * @param linkCreateLinkCreatorUsername the optional creator username; may be {@code null}
     * @param linkCreateWaitingRoomEnabled  whether the waiting-room gate is enabled at creation time
     * @param eventStartTime                the optional event start instant; may be {@code null}
     */
    public SmaxLinkCreateRequest(String linkCreateMedia,
                   Jid linkCreateCallCreator,
                   String linkCreateCallId,
                   String linkCreateLinkCreatorUsername,
                   boolean linkCreateWaitingRoomEnabled,
                   Instant eventStartTime) {
        this.linkCreateMedia = linkCreateMedia;
        this.linkCreateCallCreator = linkCreateCallCreator;
        this.linkCreateCallId = linkCreateCallId;
        this.linkCreateLinkCreatorUsername = linkCreateLinkCreatorUsername;
        this.linkCreateWaitingRoomEnabled = linkCreateWaitingRoomEnabled;
        this.eventStartTime = eventStartTime;
    }

    /**
     * Returns the optional media type to be carried by the {@code media} attribute.
     *
     * @return an {@link Optional} carrying the media type, or empty when omitted
     */
    public Optional<String> linkCreateMedia() {
        return Optional.ofNullable(linkCreateMedia);
    }

    /**
     * Returns the optional creator-device JID to be carried by the {@code call-creator} attribute.
     *
     * @return an {@link Optional} carrying the device JID, or empty when omitted
     */
    public Optional<Jid> linkCreateCallCreator() {
        return Optional.ofNullable(linkCreateCallCreator);
    }

    /**
     * Returns the optional pre-allocated call identifier to be carried by the {@code call-id} attribute.
     *
     * @return an {@link Optional} carrying the call id, or empty when omitted
     */
    public Optional<String> linkCreateCallId() {
        return Optional.ofNullable(linkCreateCallId);
    }

    /**
     * Returns the optional creator-username to be carried by the {@code link_creator_username} attribute.
     *
     * @return an {@link Optional} carrying the username, or empty when omitted
     */
    public Optional<String> linkCreateLinkCreatorUsername() {
        return Optional.ofNullable(linkCreateLinkCreatorUsername);
    }

    /**
     * Returns whether the {@code waiting_room_enabled="1"} marker will be emitted.
     *
     * @return {@code true} when the gate is requested, {@code false} otherwise
     */
    public boolean linkCreateWaitingRoomEnabled() {
        return linkCreateWaitingRoomEnabled;
    }

    /**
     * Returns the optional scheduled-event start instant.
     *
     * @return an {@link Optional} carrying the event start, or empty when omitted
     */
    public Optional<Instant> eventStartTime() {
        return Optional.ofNullable(eventStartTime);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a {@code <call to="call">} envelope around a
     * {@code <link_create/>} child, mirroring
     * {@code WASmaxOutVoipLinkCreateRequest.makeLinkCreateRequest}. The
     * scheduled-event payload, when present, is rendered as the inner
     * {@code <event start_time="..."/>} child with seconds-since-epoch.
     *
     * @return a {@link NodeBuilder} carrying the {@code <call><link_create/></call>}
     *         stanza
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutVoipLinkCreateRequest",
            exports = "makeLinkCreateRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var linkCreateBuilder = new NodeBuilder()
                .description("link_create");
        if (linkCreateMedia != null) {
            linkCreateBuilder.attribute("media", linkCreateMedia);
        }
        if (linkCreateCallCreator != null) {
            linkCreateBuilder.attribute("call-creator", linkCreateCallCreator);
        }
        if (linkCreateCallId != null) {
            linkCreateBuilder.attribute("call-id", linkCreateCallId);
        }
        if (linkCreateLinkCreatorUsername != null) {
            linkCreateBuilder.attribute("link_creator_username", linkCreateLinkCreatorUsername);
        }
        if (linkCreateWaitingRoomEnabled) {
            linkCreateBuilder.attribute("waiting_room_enabled", "1");
        }
        if (eventStartTime != null) {
            var eventNode = new NodeBuilder()
                    .description("event")
                    .attribute("start_time", eventStartTime.getEpochSecond())
                    .build();
            linkCreateBuilder.content(eventNode);
        }
        return new NodeBuilder()
                .description("call")
                .attribute("to", JidServer.call())
                .content(linkCreateBuilder.build());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxLinkCreateRequest) obj;
        return this.linkCreateWaitingRoomEnabled == that.linkCreateWaitingRoomEnabled
                && Objects.equals(this.linkCreateMedia, that.linkCreateMedia)
                && Objects.equals(this.linkCreateCallCreator, that.linkCreateCallCreator)
                && Objects.equals(this.linkCreateCallId, that.linkCreateCallId)
                && Objects.equals(this.linkCreateLinkCreatorUsername, that.linkCreateLinkCreatorUsername)
                && Objects.equals(this.eventStartTime, that.eventStartTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linkCreateMedia, linkCreateCallCreator, linkCreateCallId,
                linkCreateLinkCreatorUsername, linkCreateWaitingRoomEnabled, eventStartTime);
    }

    @Override
    public String toString() {
        return "SmaxLinkCreateRequest[linkCreateMedia=" + linkCreateMedia
                + ", linkCreateCallCreator=" + linkCreateCallCreator
                + ", linkCreateCallId=" + linkCreateCallId
                + ", linkCreateLinkCreatorUsername=" + linkCreateLinkCreatorUsername
                + ", linkCreateWaitingRoomEnabled=" + linkCreateWaitingRoomEnabled
                + ", eventStartTime=" + eventStartTime + ']';
    }
}
