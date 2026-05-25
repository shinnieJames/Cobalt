package com.github.auties00.cobalt.call.internal.transport.relay;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayAllocateRequestBuilder;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayAttributeType;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayCallInfoSpec;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayPacket;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayXorAddress;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end parity suite for {@link WaRelayAllocateRequestBuilder#build} against captured
 * WA Web relay traffic.
 *
 * <p>Each captured 344-byte Allocate Request is reconstructed from its inputs (transaction id,
 * {@code WA-RELAY-TOKEN} blob, decoded {@link WaRelayCallInfo}, the relay endpoint decoded via
 * {@link WaRelayXorAddress#decode}, and the matching {@code relay_key}) and asserted byte-identical
 * to the capture, pinning every pipeline stage: packet header, attribute order, XOR encoding,
 * protobuf encoding, padding, and HMAC-SHA1 stamping. The {@code relay_key} is keyed as raw ASCII
 * bytes of its base64 string form, the WhatsApp-specific MESSAGE-INTEGRITY keying confirmed in
 * {@link WaRelayMessageIntegrityParityTest}.
 *
 * <p>Fixtures are captured wasm-engine output under {@code src/test/resources/fixtures/relay/}:
 * {@code stun-bytes-raw.json} holds the raw packet bytes and {@code relay-list-updates.json} the
 * {@code RelayListUpdate} event stream that supplies each refresh's {@code relay_key}.
 */
public class WaRelayAllocateRequestBuilderParityTest {

    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

    private static final String RLU_FIXTURE = "fixtures/relay/relay-list-updates.json";

    @Test
    public void rebuildsFirstAllocateRequestByteExact() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        var rluRaw = Fixtures.readJson(RLU_FIXTURE);
        var rlus = rluRaw.getJSONArray("relayListUpdates");

        byte[] capturedBytes = null;
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var b = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (b.length == 344) {
                capturedBytes = b;
                break;
            }
        }
        assertNotNull(capturedBytes, "no 344-byte Allocate Request found in fixture");

        var packet = WaRelayPacket.decode(capturedBytes);
        var attrs = packet.attributes();
        var token = attrs.stream()
                .filter(a -> a.resolvedType() == WaRelayAttributeType.WA_RELAY_TOKEN)
                .findFirst().orElseThrow().value();
        var callInfoBytes = attrs.stream()
                .filter(a -> a.resolvedType() == WaRelayAttributeType.WA_CALL_INFO)
                .findFirst().orElseThrow().value();
        var xorAddrBytes = attrs.stream()
                .filter(a -> a.resolvedType() == WaRelayAttributeType.XOR_RELAYED_ADDRESS)
                .findFirst().orElseThrow().value();

        var callInfo = WaRelayCallInfoSpec.decode(callInfoBytes);
        var xorAddress = WaRelayXorAddress.decode(xorAddrBytes, packet.transactionId());
        var keyBytes = resolveRelayKey(token, rlus);
        assertNotNull(keyBytes, "no matching RelayListUpdate for packet's WA-RELAY-TOKEN");

        var rebuilt = WaRelayAllocateRequestBuilder.build(
                packet.transactionId(),
                token,
                callInfo,
                xorAddress.address(),
                xorAddress.port(),
                keyBytes);

        assertArrayEquals(capturedBytes, rebuilt,
                "rebuilt Allocate Request must be byte-exact with capture");
    }

    private static byte[] resolveRelayKey(byte[] tokenInPacket, JSONArray rlus) {
        for (var i = 0; i < rlus.size(); i++) {
            var rlu = rlus.getJSONObject(i);
            var tokens = rlu.getJSONArray("relay_tokens");
            for (var j = 0; j < tokens.size(); j++) {
                var t = Base64.getDecoder().decode(tokens.getString(j));
                if (Arrays.equals(t, tokenInPacket)) {
                    // HMAC key is the relay_key's base64 string as raw ASCII bytes, not its decode
                    return rlu.getString("relay_key").getBytes(StandardCharsets.US_ASCII);
                }
            }
        }
        return null;
    }
}
