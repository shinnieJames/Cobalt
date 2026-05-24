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
 * End-to-end parity test for
 * {@link WaRelayAllocateRequestBuilder#build}.
 *
 * <p>Reconstructs each captured 344-byte Allocate Request from its
 * documented inputs:
 *
 * <ul>
 *   <li>The captured 12-byte transaction id (from the packet header).</li>
 *   <li>The {@code WA-RELAY-TOKEN} blob (from the captured attribute,
 *       equal to {@code relay_tokens[token_id]} base64-decoded for the
 *       targeted relay).</li>
 *   <li>The decoded {@link WaRelayCallInfo}
 *       object (so the encoder is exercised, not just a byte
 *       passthrough).</li>
 *   <li>The targeted relay endpoint, decoded via
 *       {@link WaRelayXorAddress#decode}.</li>
 *   <li>The {@code relay_key} from the {@code RelayListUpdate} event
 *       whose {@code relay_tokens[]} contains the packet's
 *       WA-RELAY-TOKEN — used as <em>raw ASCII bytes of its base64
 *       string form</em>, the WhatsApp-specific MI keying confirmed
 *       in {@link WaRelayMessageIntegrityParityTest}.</li>
 * </ul>
 *
 * <p>Asserts the rebuilt packet is byte-identical to the captured
 * one, pinning every stage of the pipeline (packet header, attribute
 * order, XOR encoding, protobuf encoding, padding, and HMAC-SHA1
 * stamping with WhatsApp's base64-string key).
 */
public class WaRelayAllocateRequestBuilderParityTest {

    /**
     * Classpath path of the captured-bytes fixture.
     */
    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

    /**
     * Classpath path of the relay-list-updates fixture.
     */
    private static final String RLU_FIXTURE = "fixtures/relay/relay-list-updates.json";

    /**
     * Rebuilds the first captured 344-byte Allocate Request and
     * asserts byte-exact equality with the capture.
     *
     * @throws IOException if a fixture file cannot be read
     */
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

    /**
     * Locates the {@code RelayListUpdate} whose {@code relay_tokens[]}
     * contains the packet's WA-RELAY-TOKEN and returns its
     * {@code relay_key} as raw ASCII bytes of the base64 string.
     *
     * @param tokenInPacket the WA-RELAY-TOKEN attribute value
     * @param rlus          the array of captured RLU event payloads
     * @return the HMAC key bytes (ASCII of base64 string), or
     *         {@code null} if no match
     */
    private static byte[] resolveRelayKey(byte[] tokenInPacket, JSONArray rlus) {
        for (var i = 0; i < rlus.size(); i++) {
            var rlu = rlus.getJSONObject(i);
            var tokens = rlu.getJSONArray("relay_tokens");
            for (var j = 0; j < tokens.size(); j++) {
                var t = Base64.getDecoder().decode(tokens.getString(j));
                if (Arrays.equals(t, tokenInPacket)) {
                    return rlu.getString("relay_key").getBytes(StandardCharsets.US_ASCII);
                }
            }
        }
        return null;
    }
}
