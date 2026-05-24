package com.github.auties00.cobalt.call.internal.transport.relay;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayMessageIntegrity;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayPacket;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity test for {@link WaRelayMessageIntegrity} against a captured
 * Allocate Request fixture.
 *
 * <p>Confirms two non-obvious invariants pinned by reversing the wasm
 * engine's STUN code (pjsip-derived
 * {@code wa_stun_finalize_integrity_and_fingerprint}):
 *
 * <ol>
 *   <li>The HMAC algorithm is HMAC-SHA1.</li>
 *   <li>The HMAC key is the {@code RelayListUpdate.relay_key} value
 *       used as <em>raw ASCII bytes of its base64 string form</em>
 *       — including the {@code "=="} padding — NOT the binary bytes
 *       you'd get by decoding the base64. WhatsApp passes the base64
 *       string straight through to pjsip's
 *       {@code pj_stun_authenticate_request(password, password_len)}.
 *       This was confirmed by dumping the wasm linear memory of the
 *       active call worker, observing the relay_key only ever appears
 *       as a base64 string in heap, then verifying the captured MAC
 *       against {@code HMAC-SHA1(base64_string_bytes, prefix)}.</li>
 * </ol>
 *
 * <p>WhatsApp emits multiple {@code RelayListUpdate} events per call
 * (one per relay-list refresh). Each Allocate Request is keyed on the
 * RLU whose {@code relay_tokens[]} contains the packet's
 * {@code WA-RELAY-TOKEN} attribute value. This test resolves that
 * mapping per packet by scanning RLUs from
 * {@code relay-list-updates.json} and matching tokens.
 */
public class WaRelayMessageIntegrityParityTest {

    /**
     * Classpath path of the captured-bytes fixture.
     */
    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

    /**
     * Classpath path of the relay-list-updates fixture (the engine
     * event stream that delivers {@code relay_key} for each refresh
     * during the call).
     */
    private static final String RLU_FIXTURE = "fixtures/relay/relay-list-updates.json";

    /**
     * Verifies that every captured 344-byte Allocate Request carries a
     * MESSAGE-INTEGRITY whose value is
     * {@code HMAC-SHA1(relay_key_base64_string, prefix)} for the
     * matching {@code RelayListUpdate}.
     *
     * @throws IOException if a fixture file cannot be read
     */
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

    /**
     * Round-trips a stamped MESSAGE-INTEGRITY: zeros the MAC bytes,
     * re-stamps them with {@link WaRelayMessageIntegrity#stamp}, and
     * asserts byte-equality with the captured packet.
     *
     * @throws IOException if a fixture file cannot be read
     */
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

    /**
     * Asserts that {@link WaRelayMessageIntegrity#locate} returns the
     * correct offset for the first captured Allocate Request — the
     * MESSAGE-INTEGRITY attribute must be the last attribute, which on
     * a 344-byte packet starts at offset 320 (4-byte header + 20-byte
     * value = 24 bytes; 344 - 24 = 320).
     *
     * @throws IOException if the fixture file cannot be read
     */
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
            assertEquals(320, WaRelayMessageIntegrity.locate(bytes));
            return;
        }
        throw new AssertionError("no 344-byte Allocate Request found in fixture");
    }

    /**
     * Locates the {@code RelayListUpdate} whose {@code relay_tokens[]}
     * contains the {@code WA-RELAY-TOKEN} attribute carried by the
     * given packet, and returns its {@code relay_key} as raw ASCII
     * bytes of the base64 string.
     *
     * @param packetBytes the encoded packet
     * @param rlus        the array of captured {@code RelayListUpdate}
     *                    event payloads
     * @return the HMAC key bytes (ASCII of base64 string), or
     *         {@code null} if no match
     */
    private static byte[] resolveRelayKeyBytes(byte[] packetBytes, JSONArray rlus) {
        var packet = WaRelayPacket.decode(packetBytes);
        byte[] tokenInPacket = null;
        for (var attr : packet.attributes()) {
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
