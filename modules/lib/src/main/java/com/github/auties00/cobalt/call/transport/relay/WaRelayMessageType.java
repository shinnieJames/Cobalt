package com.github.auties00.cobalt.call.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the message-type codes carried in the first two bytes of every {@link WaRelayPacket}.
 *
 * <p>The relay wire format is STUN-shaped (RFC 5389): a 16-bit message type, a 16-bit attribute
 * length, the 32-bit magic cookie {@link WaRelayPacket#MAGIC_COOKIE}, a 12-byte transaction id, and
 * then the attributes. Most codes reuse the standard STUN and TURN method-and-class encoding;
 * {@link #WA_KEEPALIVE} is a WhatsApp-private code whose bits do not fit the RFC 5389 class encoding.
 * Each constant pins one 16-bit code as returned by {@link #wireValue()} and resolved by
 * {@link #ofWire(int)}.
 *
 * <p>Two codes are confirmed against captured live traffic (snapshot 1038740778): a 344-byte
 * {@link #ALLOCATE_REQUEST} during call setup and a 20-byte header-only {@link #WA_KEEPALIVE} emitted
 * periodically between allocate retries.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public enum WaRelayMessageType {
    /**
     * TURN Allocate Request (RFC 5766), the request that asks the relay to allocate a relayed
     * transport address.
     *
     * <p>This is the 344-byte packet observed during call setup, carrying the relay token and the
     * per-call protobuf metadata as attributes.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    ALLOCATE_REQUEST(0x0003),

    /**
     * TURN Allocate Success Response (RFC 5766), the reply that carries the assigned
     * {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS} back to the client.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    ALLOCATE_SUCCESS(0x0103),

    /**
     * TURN Allocate Error Response (RFC 5766), the reply that signals a failed allocation.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    ALLOCATE_ERROR(0x0113),

    /**
     * STUN Binding Request (RFC 5389), the connectivity-check request.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    BINDING_REQUEST(0x0001),

    /**
     * STUN Binding Success Response (RFC 5389), the connectivity-check reply.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    BINDING_SUCCESS(0x0101),

    /**
     * WhatsApp keepalive and refresh probe, a 20-byte header-only packet with no attributes.
     *
     * <p>The relay emits this periodically while allocate retries are in flight.
     *
     * @implNote This implementation treats {@code 0x0801} as a WhatsApp-private method rather than a
     * standard STUN method: its bits do not match the RFC 5389 message-class encoding, so it is not
     * derivable from the STUN specification and was identified solely from captured traffic.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    WA_KEEPALIVE(0x0801);

    /**
     * Holds the on-wire 16-bit type code carried in the packet header.
     */
    private final int wireValue;

    /**
     * Constructs a constant bound to its on-wire type code.
     *
     * @param wireValue the on-wire 16-bit message-type code
     */
    WaRelayMessageType(int wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the on-wire 16-bit message-type code of this constant.
     *
     * @return the wire-level message type
     */
    public int wireValue() {
        return wireValue;
    }

    /**
     * Resolves a wire-level message-type code to its constant.
     *
     * <p>Returns {@code null} when the code is not one of the captured codes enumerated here, leaving
     * the caller to decide how to treat an unrecognised packet.
     *
     * @param wireValue the 16-bit code read from the packet header
     * @return the matching constant, or {@code null} when the code is unknown
     */
    public static WaRelayMessageType ofWire(int wireValue) {
        for (var v : values()) {
            if (v.wireValue == wireValue) {
                return v;
            }
        }
        return null;
    }
}
