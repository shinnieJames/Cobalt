package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the attribute-type codes that may appear in a {@link WaRelayAttribute} of a
 * {@link WaRelayPacket}.
 *
 * <p>The relay protocol mixes standard STUN and TURN attributes with WhatsApp-specific
 * comprehension-optional attributes (type codes {@code >= 0x4000} per RFC 5389). Each constant pins
 * one 16-bit code as returned by {@link #wireValue()} and resolved by {@link #ofWire(int)}; the codes
 * below were observed in captured Allocate Requests on a production live session.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public enum WaRelayAttributeType {
    /**
     * Standard {@code MESSAGE-INTEGRITY} (RFC 5389 section 15.4), a 20-byte HMAC-SHA1 over every byte
     * of the packet preceding this attribute.
     *
     * <p>The HMAC is keyed on the relay key the server supplies in {@code RelayListUpdate.relay_key};
     * see {@link WaRelayMessageIntegrity} for the keying details and computation.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE_INTEGRITY(0x0008),

    /**
     * Standard {@code XOR-RELAYED-ADDRESS} (RFC 5766 section 14.5), the relay endpoint the request
     * targets, XOR'd with the magic cookie.
     *
     * <p>The value is 8 bytes for an IPv4 endpoint and 20 bytes for an IPv6 endpoint; see
     * {@link WaRelayXorAddress} for the codec.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.DIRECT)
    XOR_RELAYED_ADDRESS(0x0016),

    /**
     * WhatsApp-specific {@code WA-RELAY-TOKEN}, a variable-length opaque per-relay authorisation blob.
     *
     * <p>The value is the Base64-decoded {@code RelayListUpdate.relay_tokens[i]} delivered by the
     * relay-list update.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    WA_RELAY_TOKEN(0x4000),

    /**
     * WhatsApp-specific {@code WA-CALL-INFO}, a variable-length protobuf blob describing per-call
     * routing information such as timestamps, IP-version preferences, and candidate priorities.
     *
     * <p>The payload is modelled by {@link WaRelayCallInfo}.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    WA_CALL_INFO(0x4024);

    /**
     * Holds the on-wire 16-bit attribute-type code.
     */
    private final int wireValue;

    /**
     * Constructs a constant bound to its on-wire type code.
     *
     * @param wireValue the on-wire 16-bit attribute-type code
     */
    WaRelayAttributeType(int wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the on-wire 16-bit attribute-type code of this constant.
     *
     * @return the wire-level attribute type
     */
    public int wireValue() {
        return wireValue;
    }

    /**
     * Resolves a wire-level attribute-type code to its constant.
     *
     * <p>Returns {@code null} when the code is not one of the codes enumerated here, leaving the
     * caller to treat the attribute as comprehension-optional and skip it.
     *
     * @param wireValue the 16-bit code
     * @return the matching constant, or {@code null} when the code is unknown
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
