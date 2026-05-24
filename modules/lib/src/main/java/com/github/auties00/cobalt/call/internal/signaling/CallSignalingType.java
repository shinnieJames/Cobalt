package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Mirrors {@code WAWebVoipSignalingEnums.TYPE} — the discriminator for every
 * payload that can ride inside a {@code <call>} stanza on the wire.
 *
 * <p>The enum constants intentionally carry both the integer ordinal used by
 * the native VoIP backend ({@link #index()}) and the lower-case wire tag used
 * inside the stanza envelope ({@link #wireTag()}). Cobalt does not implement
 * the WebRTC media plane, so most of these payloads are observed-only on
 * inbound traffic; the senders implemented in {@link CallSender} cover the
 * subset that is meaningful at the signalling layer alone.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSignalingEnums")
public enum CallSignalingType {
    /**
     * Sentinel used by the native parser when the payload tag does not match
     * any other variant.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    NONE(0, "none"),

    /**
     * Initial offer that announces a 1:1 or group call to the peer(s).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER(1, "offer"),

    /**
     * Server-side acknowledgement that an offer reached the peer's relay.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_RECEIPT(2, "offer_receipt"),

    /**
     * Callee accepted the call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ACCEPT(3, "accept"),

    /**
     * Callee declined the call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    REJECT(4, "reject"),

    /**
     * Either side hung up. Carries a {@code reason} attribute parsed by
     * {@link CallReceiver}.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    TERMINATE(5, "terminate"),

    /**
     * Transport-level relay metadata exchange.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    TRANSPORT(6, "transport"),

    /**
     * Server ack for an offer this device sent.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_ACK(7, "offer_ack"),

    /**
     * Negative ack for an offer (typically because the peer was busy or
     * unreachable).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_NACK(8, "offer_nack"),

    /**
     * Periodic relay-latency probe used to elect the lowest-latency relay.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    RELAY_LATENCY(9, "relaylatency"),

    /**
     * Concludes relay election by selecting one of the probed relays.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    RELAY_ELECTION(10, "relayelection"),

    /**
     * Connection interruption (network change, peer suspend).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    INTERRUPTION(11, "interruption"),

    /**
     * Mute / unmute toggle. The wire tag is {@code mute_v2} on the current
     * WhatsApp Web snapshot — verified against the
     * {@code fixtures/call/1to1/mute-toggle.*.jsonl} corpus where every
     * captured stanza is {@code <call><mute_v2 call-id call-creator
     * mute-state="0|1"/></call>}.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    MUTE(12, "mute_v2"),

    /**
     * Pre-acceptance signal — the callee's device is alerting but the user
     * has not picked up yet.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    PREACCEPT(13, "preaccept"),

    /**
     * Server ack for the {@link #ACCEPT} signal.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ACCEPT_RECEIPT(14, "accept_receipt"),

    /**
     * Video on/off announcement during a call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    VIDEO_STATE(15, "video_state"),

    /**
     * Generic event announcement (reaction, screen-share, etc).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    NOTIFY(16, "notify"),

    /**
     * Group-info exchange between participants.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_INFO(17, "group_info"),

    /**
     * Group-call key re-exchange.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ENC_REKEY(18, "enc_rekey"),

    /**
     * Per-participant peer state update.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    PEER_STATE(19, "peer_state"),

    /**
     * Server ack for the {@link #VIDEO_STATE} signal.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    VIDEO_STATE_ACK(20, "video_state_ack"),

    /**
     * Flow-control signal used by the relay.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    FLOW_CONTROL(21, "flow_control"),

    /**
     * Web-client-specific marker.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    WEB_CLIENT(22, "web_client"),

    /**
     * Server ack for the secondary {@code accept} on group calls.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    ACCEPT_ACK(23, "accept_ack"),

    /**
     * Group-membership update during an in-progress group call.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_UPDATE(24, "group_update"),

    /**
     * Server-initiated notice that an offer arrived while the device was
     * offline.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSignalingEnums", exports = "TYPE", adaptation = WhatsAppAdaptation.DIRECT)
    OFFER_NOTICE(25, "offer_notice");

    /**
     * Holds the integer ordinal used by the native VoIP backend.
     */
    private final int index;

    /**
     * Holds the lower-case stanza tag used on the wire.
     */
    private final String wireTag;

    /**
     * Constructs a new {@code CallSignalingType} with the given native
     * ordinal and wire tag.
     *
     * @param index   the native VoIP backend ordinal
     * @param wireTag the lower-case stanza tag used on the wire
     */
    CallSignalingType(int index, String wireTag) {
        this.index = index;
        this.wireTag = wireTag;
    }

    /**
     * Returns the integer ordinal used by the native VoIP backend.
     *
     * @return the native ordinal
     */
    public int index() {
        return index;
    }

    /**
     * Returns the lower-case stanza tag used on the wire.
     *
     * @return the wire tag
     */
    public String wireTag() {
        return wireTag;
    }
}
