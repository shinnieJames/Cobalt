package com.github.auties00.cobalt.call.transport.relay;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity suite for {@link WaRelayMessageIntegrity} against captured WA Web Allocate Request bytes.
 *
 * <p>Confirms two non-obvious invariants recovered by reversing the wasm engine's pjsip-derived
 * STUN code: the HMAC algorithm is HMAC-SHA1, and the HMAC key is the {@code RelayListUpdate.relay_key}
 * value used as raw ASCII bytes of its base64 string form (including the {@code "=="} padding), NOT
 * the bytes obtained by base64-decoding it. WhatsApp passes the base64 string straight through to
 * pjsip's {@code pj_stun_authenticate_request(password, password_len)}; the captured MAC verifies
 * against {@code HMAC-SHA1(base64_string_bytes, prefix)} and not against the decoded form.
 *
 * <p>WhatsApp emits multiple {@code RelayListUpdate} events per call (one per relay-list refresh).
 * Each Allocate Request is keyed on the RLU whose {@code relay_tokens[]} contains the packet's
 * {@code WA-RELAY-TOKEN} attribute value, resolved per packet by scanning the RLU stream and matching
 * tokens. Fixtures are captured wasm-engine output under {@code src/test/resources/fixtures/relay/}:
 * {@code stun-bytes-raw.json} (raw packet bytes) and {@code relay-list-updates.json} (the per-refresh
 * {@code relay_key} stream).
 */
public class WaRelayMessageIntegrityParityTest {

    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

    private static final String RLU_FIXTURE = "fixtures/relay/relay-list-updates.json";

    @Test
    public void everyAllocateRequestVerifiesAgainstRelayKey() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        var rluRaw = Fixtures.readJson(RLU_FIXTURE);
        var rlus = rluRaw.getJSONArray("relayListUpdates");

        var checked = 0;
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var bytes = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (bytes.length != 344) continue;

            var keyBytes = resolveRelayKeyBytes(bytes, rlus);
            assertNotNull(keyBytes, "no matching RLU for packet " + i);
            assertTrue(WaRelayMessageIntegrity.verify(bytes, keyBytes),
                    "MESSAGE-INTEGRITY did not verify on packet " + i);
            checked++;
        }
        assertTrue(checked >= 1, "expected at least 1 Allocate Request, got " + checked);
    }

    @Test
    public void stampReproducesCapturedMac() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        var rluRaw = Fixtures.readJson(RLU_FIXTURE);
        var rlus = rluRaw.getJSONArray("relayListUpdates");

        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var bytes = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (bytes.length != 344) continue;

            var keyBytes = resolveRelayKeyBytes(bytes, rlus);
            assertNotNull(keyBytes);

            var offset = WaRelayMessageIntegrity.locate(bytes);
            assertTrue(offset > 0, "MESSAGE-INTEGRITY not found in packet " + i);
            var capturedMac = new byte[WaRelayMessageIntegrity.MAC_LENGTH];
            System.arraycopy(bytes, offset + 4, capturedMac, 0, WaRelayMessageIntegrity.MAC_LENGTH);

            var zeroed = bytes.clone();
            for (var j = 0; j < WaRelayMessageIntegrity.MAC_LENGTH; j++) {
                zeroed[offset + 4 + j] = 0;
            }
            WaRelayMessageIntegrity.stamp(zeroed, keyBytes);

            for (var j = 0; j < WaRelayMessageIntegrity.MAC_LENGTH; j++) {
                assertEquals(capturedMac[j], zeroed[offset + 4 + j],
                        "stamp MAC byte " + j + " mismatch on packet " + i);
            }
            return;
        }
        throw new AssertionError("no 344-byte Allocate Request found in fixture");
    }

    @Test
    public void locateReturnsExpectedOffset() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var bytes = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (bytes.length != 344) continue;

            var packet = WaRelayPacket.decode(bytes);
            assertNotNull(packet);
            // MESSAGE-INTEGRITY is the trailing attribute: 344 - (4-byte header + 20-byte MAC) = 320
            assertEquals(320, WaRelayMessageIntegrity.locate(bytes));
            return;
        }
        throw new AssertionError("no 344-byte Allocate Request found in fixture");
    }

    private static byte[] resolveRelayKeyBytes(byte[] packetBytes, JSONArray rlus) {
        var packet = WaRelayPacket.decode(packetBytes);
        byte[] tokenInPacket = null;
        for (var attr : packet.attributes()) {
            // 0x4000 is the WA-RELAY-TOKEN attribute type
            if (attr.type() == 0x4000) { tokenInPacket = attr.value(); break; }
        }
        if (tokenInPacket == null) return null;
        for (var i = 0; i < rlus.size(); i++) {
            var rlu = rlus.getJSONObject(i);
            var tokens = rlu.getJSONArray("relay_tokens");
            for (var j = 0; j < tokens.size(); j++) {
                var t = Base64.getDecoder().decode(tokens.getString(j));
                if (Arrays.equals(t, tokenInPacket)) {
                    var keyB64 = rlu.getString("relay_key");
                    return keyB64.getBytes(StandardCharsets.US_ASCII);
                }
            }
        }
        return null;
    }
}
