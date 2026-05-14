package com.github.auties00.cobalt.call.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Message-type discriminator on the first two bytes of every packet
 * WhatsApp sends through its SCTP relay data channels.
 *
 * <p>The wire format is STUN-shaped (RFC 5389): {@code [type:u16,
 * length:u16, magic:0x2112A442, txId:12 bytes, attributes...]}. Most
 * methods reuse standard STUN/TURN message types; a few are
 * WhatsApp-specific extensions identified by inspecting captured
 * traffic.
 *
 * <p>Captured live (snapshot 1038740778):
 *
 * <ul>
 *   <li>{@link #ALLOCATE_REQUEST} (0x0003) — TURN-style allocation
 *       carrying the relay_token + per-call protobuf metadata.</li>
 *   <li>{@link #WA_KEEPALIVE} (0x0801) — empty 20-byte packet,
 *       periodic between Allocate retries. Message-type bits do not
 *       match the STUN class encoding, so this is a WhatsApp-private
 *       method.</li>
 * </ul>
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public enum WaRelayMessageType {
    /**
     * TURN Allocate Request, RFC 5766. The 344-byte
     * {@code dc.send(...)} packet observed during call setup.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    ALLOCATE_REQUEST(0x0003),

    /**
     * TURN Allocate Success Response, RFC 5766. Carries the assigned
     * XOR-RELAYED-ADDRESS back to the client.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    ALLOCATE_SUCCESS(0x0103),

    /**
     * TURN Allocate Error Response.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    ALLOCATE_ERROR(0x0113),

    /**
     * STUN Binding Request, RFC 5389.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    BINDING_REQUEST(0x0001),

    /**
     * STUN Binding Success Response, RFC 5389.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    BINDING_SUCCESS(0x0101),

    /**
     * WhatsApp keepalive / refresh probe. 20-byte body (header only),
     * no attributes. Message-type bits do not match RFC 5389's class
     * encoding, so this is a private method.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    WA_KEEPALIVE(0x0801);

    /**
     * The wire-level 16-bit type code carried in the packet header.
     */
    private final int wireValue;

    /**
     * Constructs a new {@code WaRelayMessageType}.
     *
     * @param wireValue the on-wire 16-bit message type code
     */
    WaRelayMessageType(int wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the on-wire 16-bit message type code.
     *
     * @return the wire-level message type
     */
    public int wireValue() {
        return wireValue;
    }

    /**
     * Resolves a wire-level message type code to a {@code WaRelayMessageType},
     * or {@code null} when the code is not in the captured set.
     *
     * @param wireValue the 16-bit code from the packet header
     * @return the matching enum value, or {@code null} if unknown
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
