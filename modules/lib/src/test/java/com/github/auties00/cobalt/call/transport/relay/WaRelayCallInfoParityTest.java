package com.github.auties00.cobalt.call.transport.relay;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.call.transport.relay.WaRelayAttributeType;
import com.github.auties00.cobalt.call.transport.relay.WaRelayCallInfoSpec;
import com.github.auties00.cobalt.call.transport.relay.WaRelayPacket;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity test for {@link com.github.auties00.cobalt.call.transport.relay.WaRelayCallInfo}
 * against the captured WA-CALL-INFO attribute payload.
 *
 * <p>Asserts the observed shape: a 95-byte protobuf payload that
 * decodes to nine entries enumerating every (IP version, relay) pair
 * from the matching {@code RelayListUpdate} (3 relays × 3 IP versions
 * including "any").
 *
 * <p>Pins both the protobuf wire format (round-trip byte-equality) and
 * the engine-emitted axis grid.
 */
public class WaRelayCallInfoParityTest {

    /**
     * Classpath path of the captured-bytes fixture.
     */
    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

    /**
     * Decodes the WA-CALL-INFO attribute of the first 344-byte
     * Allocate Request and asserts that the entry grid matches the
     * documented 3-relay × 3-IP-version axis pattern.
     *
     * @throws IOException if the fixture file cannot be read
     */
    @Test
    public void firstAllocateRequestCallInfoMatchesAxisGrid() throws IOException {
        var attrValue = firstCallInfoBytes();
        var info = WaRelayCallInfoSpec.decode(attrValue);
        assertEquals(9, info.entries().size(), "expected 9 axis entries (3 relays × 3 IP versions)");

        var pairs = new HashSet<String>();
        for (var entry : info.entries()) {
            pairs.add(entry.ipVersion() + ":" + entry.relayId());
            assertTrue(entry.priority() > 0,
                    "priority must be positive, got " + entry.priority() + " for "
                            + entry.ipVersion() + ":" + entry.relayId());
        }

        for (var ip = 0; ip <= 2; ip++) {
            for (var relay = 0; relay <= 2; relay++) {
                var key = ip + ":" + relay;
                assertTrue(pairs.contains(key), "missing axis pair " + key);
            }
        }
    }

    /**
     * Round-trips the WA-CALL-INFO payload and asserts byte-equality
     * with the captured bytes.
     *
     * @throws IOException if the fixture file cannot be read
     */
    @Test
    public void callInfoRoundTripsByteExact() throws IOException {
        var attrValue = firstCallInfoBytes();
        var info = WaRelayCallInfoSpec.decode(attrValue);
        var roundTripped = WaRelayCallInfoSpec.encode(info);
        assertArrayEquals(attrValue, roundTripped,
                "WA-CALL-INFO round-trip must be byte-exact");
    }

    /**
     * Locates the WA-CALL-INFO attribute value in the first captured
     * 344-byte Allocate Request.
     *
     * @return the raw 95-byte attribute value
     * @throws IOException if the fixture file cannot be read
     */
    private static byte[] firstCallInfoBytes() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var bytes = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (bytes.length != 344) continue;
            var packet = WaRelayPacket.decode(bytes);
            return packet.attributes().stream()
                    .filter(a -> a.resolvedType() == WaRelayAttributeType.WA_CALL_INFO)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no WA-CALL-INFO in packet"))
                    .value();
        }
        throw new AssertionError("no 344-byte Allocate Request in fixture");
    }
}
