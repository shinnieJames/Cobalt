package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

/**
 * High-level helper that composes a fully-formed
 * {@link WaRelayMessageType#ALLOCATE_REQUEST} packet.
 *
 * <p>An Allocate Request carries four attributes in this order:
 *
 * <ol>
 *   <li>{@link WaRelayAttributeType#WA_RELAY_TOKEN} — the Base64-decoded
 *       per-relay authorisation blob from
 *       {@code RelayListUpdate.relay_tokens[i]}.</li>
 *   <li>{@link WaRelayAttributeType#WA_CALL_INFO} — the protobuf
 *       payload describing per-(IP version, relay) candidate
 *       priorities; see {@link WaRelayCallInfo}.</li>
 *   <li>{@link WaRelayAttributeType#XOR_RELAYED_ADDRESS} — the
 *       targeted relay endpoint, XOR'd with the magic cookie; see
 *       {@link WaRelayXorAddress}.</li>
 *   <li>{@link WaRelayAttributeType#MESSAGE_INTEGRITY} — HMAC-SHA1 of
 *       all preceding bytes keyed on
 *       {@code RelayListUpdate.relay_key}; see
 *       {@link WaRelayMessageIntegrity}.</li>
 * </ol>
 *
 * <p>The MAC is computed and stamped into the packet after the
 * surrounding bytes are finalised, mirroring the way the wasm engine
 * emits the packet.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayAllocateRequestBuilder {
    /**
     * Prevents instantiation.
     */
    private WaRelayAllocateRequestBuilder() {
        throw new AssertionError("no instances");
    }

    /**
     * Builds and stamps a fully-formed Allocate Request packet.
     *
     * @param transactionId the 12-byte STUN transaction id
     * @param relayToken    the raw {@code WA-RELAY-TOKEN} blob (the
     *                      {@code RelayListUpdate.relay_tokens[i]}
     *                      Base64-decoded value)
     * @param callInfo      the {@code WA-CALL-INFO} protobuf payload
     * @param relayAddress  the relay endpoint (IPv4 or IPv6)
     * @param relayPort     the relay port (1..65535)
     * @param relayKey      the {@code RelayListUpdate.relay_key} bytes,
     *                      used as the HMAC-SHA1 key
     * @return a freshly-allocated byte array containing the encoded,
     *         MAC-stamped packet
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    public static byte[] build(byte[] transactionId,
                               byte[] relayToken,
                               WaRelayCallInfo callInfo,
                               InetAddress relayAddress,
                               int relayPort,
                               byte[] relayKey) {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(relayToken, "relayToken cannot be null");
        Objects.requireNonNull(callInfo, "callInfo cannot be null");
        Objects.requireNonNull(relayAddress, "relayAddress cannot be null");
        Objects.requireNonNull(relayKey, "relayKey cannot be null");

        var callInfoBytes = WaRelayCallInfoSpec.encode(callInfo);
        var xorAddress = new WaRelayXorAddress(relayAddress, relayPort);
        var xorAddressBytes = xorAddress.encode(transactionId);
        var macPlaceholder = new byte[WaRelayMessageIntegrity.MAC_LENGTH];

        var attributes = List.of(
                new WaRelayAttribute(WaRelayAttributeType.WA_RELAY_TOKEN.wireValue(), relayToken),
                new WaRelayAttribute(WaRelayAttributeType.WA_CALL_INFO.wireValue(), callInfoBytes),
                new WaRelayAttribute(WaRelayAttributeType.XOR_RELAYED_ADDRESS.wireValue(), xorAddressBytes),
                new WaRelayAttribute(WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue(), macPlaceholder));

        var packet = new WaRelayPacket(
                WaRelayMessageType.ALLOCATE_REQUEST.wireValue(),
                transactionId,
                attributes);
        var bytes = packet.encode();
        WaRelayMessageIntegrity.stamp(bytes, relayKey);
        return bytes;
    }
}
