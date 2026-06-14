package com.github.auties00.cobalt.call.signaling;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates every payload that can ride inside a call stanza on the wire.
 *
 * <p>Each constant binds two coordinates: the integer ordinal the native VoIP backend assigns to the
 * payload ({@link #index()}) and the lower-case stanza tag that identifies the payload inside the call
 * envelope ({@link #wireTag()}). Because Cobalt does not implement the WebRTC media plane, most of
 * these payloads are observed only on inbound traffic; the stanza builders in {@link CallStanza} cover
 * the subset that is meaningful at the signalling layer alone, and {@link CallReceiver} parses the
 * remainder for acknowledgement.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSignalingEnums")
public enum CallSignalingType {
    /**
     * Represents the sentinel the native parser uses when the payload tag matches no other constant.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    NONE(0, "none"),

    /**
     * Represents the initial offer that announces a one-to-one or group call to the peers.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER(1, "offer"),

    /**
     * Represents the server-side acknowledgement that an offer reached the peer's relay.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_RECEIPT(2, "offer_receipt"),

    /**
     * Represents the callee accepting the call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ACCEPT(3, "accept"),

    /**
     * Represents the callee declining the call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    REJECT(4, "reject"),

    /**
     * Represents either side hanging up.
     *
     * <p>The payload carries a {@code reason} attribute that {@link CallReceiver} parses.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    TERMINATE(5, "terminate"),

    /**
     * Represents a transport-level relay metadata exchange.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    TRANSPORT(6, "transport"),

    /**
     * Represents the server acknowledgement for an offer this device sent.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_ACK(7, "offer_ack"),

    /**
     * Represents the negative acknowledgement for an offer, typically because the peer was busy or
     * unreachable.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_NACK(8, "offer_nack"),

    /**
     * Represents a periodic relay-latency probe used to elect the lowest-latency relay.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    RELAY_LATENCY(9, "relaylatency"),

    /**
     * Represents the conclusion of relay election by selecting one of the probed relays.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    RELAY_ELECTION(10, "relayelection"),

    /**
     * Represents a connection interruption such as a network change or a peer suspend.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    INTERRUPTION(11, "interruption"),

    /**
     * Represents a mute or unmute toggle.
     *
     * @implNote This implementation binds the wire tag {@code mute_v2}, the tag emitted by the current
     * WhatsApp Web snapshot, rather than the legacy {@code mute} tag; the captured
     * {@code fixtures/call/1to1/mute-toggle.*.jsonl} corpus carries the payload as
     * {@code <mute_v2 call-id call-creator mute-state="0|1"/>}.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    MUTE(12, "mute_v2"),

    /**
     * Represents a pre-acceptance signal: the callee's device is alerting but the user has not picked
     * up yet.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    PREACCEPT(13, "preaccept"),

    /**
     * Represents the server acknowledgement for the {@link #ACCEPT} signal.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ACCEPT_RECEIPT(14, "accept_receipt"),

    /**
     * Represents a video on or off announcement during a call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    VIDEO_STATE(15, "video_state"),

    /**
     * Represents a generic event announcement such as a reaction or a screen-share notice.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    NOTIFY(16, "notify"),

    /**
     * Represents a group-info exchange between participants.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_INFO(17, "group_info"),

    /**
     * Represents a group-call key re-exchange.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ENC_REKEY(18, "enc_rekey"),

    /**
     * Represents a per-participant peer state update.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    PEER_STATE(19, "peer_state"),

    /**
     * Represents the server acknowledgement for the {@link #VIDEO_STATE} signal.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    VIDEO_STATE_ACK(20, "video_state_ack"),

    /**
     * Represents a flow-control signal used by the relay.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    FLOW_CONTROL(21, "flow_control"),

    /**
     * Represents a web-client-specific marker.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    WEB_CLIENT(22, "web_client"),

    /**
     * Represents the server acknowledgement for the secondary {@code accept} on group calls.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ACCEPT_ACK(23, "accept_ack"),

    /**
     * Represents a group-membership update during an in-progress group call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_UPDATE(24, "group_update"),

    /**
     * Represents a server-initiated notice that an offer arrived while the device was offline.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_NOTICE(25, "offer_notice");

    /**
     * Holds the integer ordinal the native VoIP backend assigns to this payload.
     */
    private final int index;

    /**
     * Holds the lower-case stanza tag that identifies this payload on the wire.
     */
    private final String wireTag;

    /**
     * Constructs a constant bound to its native ordinal and wire tag.
     *
     * @param index   the native VoIP backend ordinal
     * @param wireTag the lower-case stanza tag used on the wire
     */
    CallSignalingType(int index, String wireTag) {
        this.index = index;
        this.wireTag = wireTag;
    }

    /**
     * Returns the integer ordinal the native VoIP backend assigns to this payload.
     *
     * @return the native ordinal
     */
    public int index() {
        return index;
    }

    /**
     * Returns the lower-case stanza tag that identifies this payload on the wire.
     *
     * @return the wire tag
     */
    public String wireTag() {
        return wireTag;
    }
}
