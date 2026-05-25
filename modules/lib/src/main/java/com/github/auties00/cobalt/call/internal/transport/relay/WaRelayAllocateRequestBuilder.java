package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

/**
 * Composes a fully formed and MAC-stamped {@link WaRelayMessageType#ALLOCATE_REQUEST}
 * {@link WaRelayPacket}.
 *
 * <p>An Allocate Request carries four attributes in a fixed order: a
 * {@link WaRelayAttributeType#WA_RELAY_TOKEN} (the Base64-decoded per-relay authorisation blob from
 * {@code RelayListUpdate.relay_tokens[i]}), a {@link WaRelayAttributeType#WA_CALL_INFO} (the
 * {@link WaRelayCallInfo} protobuf describing per-(IP version, relay) candidate priorities), a
 * {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS} (the targeted relay endpoint, see
 * {@link WaRelayXorAddress}), and a {@link WaRelayAttributeType#MESSAGE_INTEGRITY} (the HMAC-SHA1 over
 * all preceding bytes, see {@link WaRelayMessageIntegrity}).
 *
 * @implNote This implementation encodes the {@code MESSAGE-INTEGRITY} attribute with a zero-filled
 * placeholder value and computes the MAC over the finalised surrounding bytes afterwards via
 * {@link WaRelayMessageIntegrity#stamp(byte[], byte[])}, because the HMAC must cover the header (whose
 * {@code msgLength} already accounts for the attribute) and all preceding attributes; this mirrors the
 * order in which the wasm engine emits the packet.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayAllocateRequestBuilder {
    /**
     * Prevents instantiation of this builder holder.
     */
    private WaRelayAllocateRequestBuilder() {
        throw new AssertionError("no instances");
    }

    /**
     * Builds and MAC-stamps a fully formed Allocate Request packet.
     *
     * <p>Encodes the call info, derives the XOR-relayed-address value for the relay endpoint, assembles
     * the four attributes in their fixed order with a placeholder integrity value, encodes the packet,
     * and stamps the HMAC-SHA1 over the finalised bytes keyed on {@code relayKey}.
     *
     * @param transactionId the 12-byte STUN transaction id
     * @param relayToken    the raw {@code WA-RELAY-TOKEN} blob, the Base64-decoded
     *                      {@code RelayListUpdate.relay_tokens[i]} value
     * @param callInfo      the {@code WA-CALL-INFO} protobuf payload
     * @param relayAddress  the relay endpoint address, IPv4 or IPv6
     * @param relayPort     the relay port in the range 1 to 65535
     * @param relayKey      the {@code RelayListUpdate.relay_key} bytes used as the HMAC-SHA1 key
     * @return a freshly allocated byte array containing the encoded, MAC-stamped packet
     * @throws NullPointerException if {@code transactionId}, {@code relayToken}, {@code callInfo},
     *                              {@code relayAddress}, or {@code relayKey} is {@code null}
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
