package com.github.auties00.cobalt.node.smax.voip;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <call><waiting_room_toggle/></call>} request that flips
 * the waiting-room gate on an existing call link.
 *
 * @apiNote
 * Drives the call-link admin's "Require approval to join" toggle on the
 * link details surface; only the link's creator can issue this RPC, and the
 * relay rejects non-creator callers with a Nack carrying the offending
 * token.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutVoipWaitingRoomToggleCallLinkRequest")
public final class SmaxWaitingRoomToggleCallLinkRequest implements SmaxOperation.Request {
    /**
     * The desired waiting-room state on the wire.
     *
     * @apiNote
     * {@code "0"} disables the gate, {@code "1"} enables it.
     * {@code WAWebVoipWaitingRoomToggleJob.toggleWaitingRoomForCallLink}
     * derives the value from a boolean; the field is modelled as a raw
     * string for forward-compat with relay-side enum extensions.
     */
    private final String waitingRoomToggleEnabled;

    /**
     * The call-link token whose waiting-room state should be toggled.
     */
    private final String waitingRoomToggleLinkToken;

    /**
     * The media type the link is configured for.
     *
     * @apiNote
     * Either {@code "audio"} or {@code "video"} on the wire; supplied so
     * the relay can confirm the toggle targets the correct link.
     */
    private final String waitingRoomToggleMedia;

    /**
     * Constructs a request.
     *
     * @param waitingRoomToggleEnabled   the desired enabled state on the wire; never {@code null}
     * @param waitingRoomToggleLinkToken the call-link token; never {@code null}
     * @param waitingRoomToggleMedia     the media type; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxWaitingRoomToggleCallLinkRequest(String waitingRoomToggleEnabled,
                   String waitingRoomToggleLinkToken,
                   String waitingRoomToggleMedia) {
        this.waitingRoomToggleEnabled = Objects.requireNonNull(waitingRoomToggleEnabled, "waitingRoomToggleEnabled cannot be null");
        this.waitingRoomToggleLinkToken = Objects.requireNonNull(waitingRoomToggleLinkToken, "waitingRoomToggleLinkToken cannot be null");
        this.waitingRoomToggleMedia = Objects.requireNonNull(waitingRoomToggleMedia, "waitingRoomToggleMedia cannot be null");
    }

    /**
     * Returns the desired waiting-room state on the wire.
     *
     * @return the enabled string; never {@code null}
     */
    public String waitingRoomToggleEnabled() {
        return waitingRoomToggleEnabled;
    }

    /**
     * Returns the call-link token.
     *
     * @return the token; never {@code null}
     */
    public String waitingRoomToggleLinkToken() {
        return waitingRoomToggleLinkToken;
    }

    /**
     * Returns the media type.
     *
     * @return the media type; never {@code null}
     */
    public String waitingRoomToggleMedia() {
        return waitingRoomToggleMedia;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a {@code <call to="call">} envelope around a
     * {@code <waiting_room_toggle/>} child, mirroring
     * {@code makeWaitingRoomToggleCallLinkRequest}.
     *
     * @return a {@link NodeBuilder} carrying the
     *         {@code <call><waiting_room_toggle/></call>} stanza
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutVoipWaitingRoomToggleCallLinkRequest",
            exports = "makeWaitingRoomToggleCallLinkRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var toggleNode = new NodeBuilder()
                .description("waiting_room_toggle")
                .attribute("enabled", waitingRoomToggleEnabled)
                .attribute("link-token", waitingRoomToggleLinkToken)
                .attribute("media", waitingRoomToggleMedia)
                .build();
        return new NodeBuilder()
                .description("call")
                .attribute("to", JidServer.call())
                .content(toggleNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxWaitingRoomToggleCallLinkRequest) obj;
        return Objects.equals(this.waitingRoomToggleEnabled, that.waitingRoomToggleEnabled)
                && Objects.equals(this.waitingRoomToggleLinkToken, that.waitingRoomToggleLinkToken)
                && Objects.equals(this.waitingRoomToggleMedia, that.waitingRoomToggleMedia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(waitingRoomToggleEnabled, waitingRoomToggleLinkToken, waitingRoomToggleMedia);
    }

    @Override
    public String toString() {
        return "SmaxWaitingRoomToggleCallLinkRequest[waitingRoomToggleEnabled=" + waitingRoomToggleEnabled
                + ", waitingRoomToggleLinkToken=" + waitingRoomToggleLinkToken
                + ", waitingRoomToggleMedia=" + waitingRoomToggleMedia + ']';
    }
}
