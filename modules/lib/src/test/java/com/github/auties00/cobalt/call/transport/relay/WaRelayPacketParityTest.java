package com.github.auties00.cobalt.call.transport.relay;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.call.transport.relay.WaRelayAttributeType;
import com.github.auties00.cobalt.call.transport.relay.WaRelayMessageType;
import com.github.auties00.cobalt.call.transport.relay.WaRelayPacket;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Byte-exact parity test for {@link WaRelayPacket} against every
 * captured packet.
 *
 * <p>Every packet is decoded then re-encoded; the result must be
 * identical to the captured bytes. This pins both the decoder and the
 * encoder against the on-wire format the WhatsApp wasm engine actually
 * emits.
 */
public class WaRelayPacketParityTest {

    /**
     * Classpath path of the captured-bytes fixture under
     * {@code src/test/resources/}.
     */
    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

    /**
     * Loads the fixture and asserts that every captured packet
     * round-trips byte-for-byte through {@link WaRelayPacket#decode}
     * and {@link WaRelayPacket#encode}.
     *
     * @throws IOException if the fixture file cannot be read
     */
    @Test
    public void everyCapturedPacketRoundTripsByteExact() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var captured = raw.getJSONArray("captured");
        assertNotNull(captured, "fixture missing 'captured' array");
        assertTrue(captured.size() > 0, "fixture has no captured workers");
        var trace = captured.getJSONObject(0).getJSONArray("trace");
        assertNotNull(trace, "first captured worker has no trace");
        assertTrue(trace.size() > 0, "trace is empty");

        var checked = 0;
        for (var i = 0; i < trace.size(); i++) {
            var entry = trace.getJSONObject(i);
            var args = entry.getJSONArray("args");
            if (args == null || args.isEmpty()) continue;
            var arg0 = args.get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var b64 = obj.getString("$bytes");
            if (b64 == null) continue;
            var bytes = Base64.getDecoder().decode(b64);

            var packet = WaRelayPacket.decode(bytes);
            var roundTripped = packet.encode();
            assertArrayEquals(bytes, roundTripped,
                    () -> "round-trip mismatch at trace index " + entry.getString("t") + "\n  expected: " + hex(bytes) + "\n  actual:   " + hex(roundTripped));
            checked++;
        }
        assertTrue(checked >= 2, "expected at least 2 round-trip checks, got " + checked);
    }

    /**
     * Asserts that the first 344-byte packet matches the documented
     * Allocate Request shape: WA-RELAY-TOKEN, WA-CALL-INFO,
     * XOR-RELAYED-ADDRESS, MESSAGE-INTEGRITY.
     *
     * @throws IOException if the fixture file cannot be read
     */
    @Test
    public void firstAllocateRequestHasExpectedAttributes() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        byte[] bytes = null;
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var b = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (b.length == 344) {
                bytes = b;
                break;
            }
        }
        assertNotNull(bytes, "no 344-byte packet found");

        var packet = WaRelayPacket.decode(bytes);
        assertEquals(WaRelayMessageType.ALLOCATE_REQUEST.wireValue(), packet.messageType());
        var attrs = packet.attributes();
        assertEquals(4, attrs.size(), "expected 4 attributes in 344-byte Allocate Request");
        assertEquals(WaRelayAttributeType.WA_RELAY_TOKEN, attrs.get(0).resolvedType());
        assertEquals(182, attrs.get(0).value().length, "WA-RELAY-TOKEN should be 182 bytes");
        assertEquals(WaRelayAttributeType.WA_CALL_INFO, attrs.get(1).resolvedType());
        assertEquals(WaRelayAttributeType.XOR_RELAYED_ADDRESS, attrs.get(2).resolvedType());
        assertEquals(8, attrs.get(2).value().length, "XOR-RELAYED-ADDRESS should be 8 bytes (IPv4)");
        assertEquals(WaRelayAttributeType.MESSAGE_INTEGRITY, attrs.get(3).resolvedType());
        assertEquals(20, attrs.get(3).value().length, "MESSAGE-INTEGRITY should be 20 bytes (HMAC-SHA1)");
    }

    /**
     * Asserts that the first 20-byte packet is a header-only WA
     * keepalive (msgType 0x0801, no attributes).
     *
     * @throws IOException if the fixture file cannot be read
     */
    @Test
    public void firstKeepaliveIsHeaderOnly() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        byte[] bytes = null;
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var b = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (b.length == 20) {
                bytes = b;
                break;
            }
        }
        assertNotNull(bytes, "no 20-byte keepalive found");

        var packet = WaRelayPacket.decode(bytes);
        assertEquals(WaRelayMessageType.WA_KEEPALIVE.wireValue(), packet.messageType());
        assertTrue(packet.attributes().isEmpty(), "keepalive must have no attributes");
        assertEquals(12, packet.transactionId().length);
    }

    /**
     * Hex-encodes the given bytes for assertion error messages.
     *
     * @param b the bytes
     * @return the lower-case hex string
     */
    private static String hex(byte[] b) {
        var sb = new StringBuilder(b.length * 2);
        for (var x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }
}
