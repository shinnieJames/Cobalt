package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Attribute-type discriminator for {@link WaRelayPacket} attributes.
 *
 * <p>WhatsApp's relay protocol mixes standard STUN/TURN attributes with
 * a set of comprehension-optional WhatsApp-specific attributes (type
 * codes &gt;= 0x4000 per RFC 5389). The values below were observed in
 * captured Allocate Requests on the production live session.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public enum WaRelayAttributeType {
    /**
     * Standard {@code MESSAGE-INTEGRITY} (RFC 5389 §15.4) — 20-byte
     * HMAC-SHA1 over all preceding bytes of the packet, keyed on the
     * relay key the server provides in {@code RelayListUpdate.relay_key}.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE_INTEGRITY(0x0008),

    /**
     * Standard {@code XOR-RELAYED-ADDRESS} (RFC 5766 §14.5) — the
     * relay endpoint the request is targeting, XOR'd with the magic
     * cookie. 8 bytes for IPv4, 20 bytes for IPv6.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    XOR_RELAYED_ADDRESS(0x0016),

    /**
     * WhatsApp-specific {@code WA-RELAY-TOKEN} — variable-length
     * opaque token the relay-list update delivered in
     * {@code relay_tokens[i]} (Base64-decoded). Carries the per-relay
     * authorisation blob.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    WA_RELAY_TOKEN(0x4000),

    /**
     * WhatsApp-specific {@code WA-CALL-INFO} — variable-length
     * protobuf-encoded blob describing per-call routing information
     * (timestamps, IP-version preferences, candidate priorities).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    WA_CALL_INFO(0x4024);

    /**
     * The wire-level 16-bit attribute type code.
     */
    private final int wireValue;

    /**
     * Constructs a new {@code WaRelayAttributeType}.
     *
     * @param wireValue the on-wire 16-bit attribute type code
     */
    WaRelayAttributeType(int wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the on-wire 16-bit attribute type code.
     *
     * @return the wire-level attribute type
     */
    public int wireValue() {
        return wireValue;
    }

    /**
     * Resolves a wire-level attribute type code to a constant, or
     * {@code null} when the code is unknown.
     *
     * @param wireValue the 16-bit code
     * @return the matching enum value, or {@code null}
     */
    public static WaRelayAttributeType ofWire(int wireValue) {
        for (var v : values()) {
            if (v.wireValue == wireValue) {
                return v;
            }
        }
        return null;
    }
}
