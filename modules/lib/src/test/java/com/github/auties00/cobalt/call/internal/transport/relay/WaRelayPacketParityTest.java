package com.github.auties00.cobalt.call.internal.transport.relay;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayAttributeType;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayMessageType;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayPacket;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Byte-exact parity suite for {@link WaRelayPacket} against every captured WA Web relay packet.
 *
 * <p>Every packet is decoded then re-encoded and must reproduce the captured bytes, pinning both
 * the decoder and the encoder against the on-wire format the WhatsApp wasm engine emits. The
 * captured bytes are read from {@code src/test/resources/fixtures/relay/stun-bytes-raw.json}.
 */
public class WaRelayPacketParityTest {

    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

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

    private static String hex(byte[] b) {
        var sb = new StringBuilder(b.length * 2);
        for (var x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }
}
