package com.github.auties00.cobalt.call.internal.transport.relay;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayAttributeType;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayPacket;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayXorAddress;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity test for {@link WaRelayXorAddress} against the captured
 * Allocate Request fixture.
 *
 * <p>Asserts that the XOR-RELAYED-ADDRESS attribute carried by every
 * captured 344-byte Allocate Request decodes to one of the IPv4
 * relay endpoints listed across the captured {@code RelayListUpdate}
 * events for that session, on port 3478.
 */
public class WaRelayXorAddressParityTest {

    /**
     * Classpath path of the captured-bytes fixture.
     */
    private static final String FIXTURE = "fixtures/relay/stun-bytes-raw.json";

    /**
     * Classpath path of the relay-list-updates fixture. Each
     * {@code RelayListUpdate} lists the relays the wasm engine
     * considered for that refresh; an Allocate Request must target
     * one of the IPs across all RLUs in the same session.
     */
    private static final String RLU_FIXTURE = "fixtures/relay/relay-list-updates.json";

    /**
     * Decodes the XOR-RELAYED-ADDRESS attribute of every captured
     * 344-byte Allocate Request and asserts that the embedded endpoint
     * matches one of the IPv4 addresses listed across the captured
     * {@code RelayListUpdate} events on port 3478.
     *
     * @throws IOException if a fixture file cannot be read
     */
    @Test
    public void xorRelayedAddressDecodesToOneOfTheRelays() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        var rluRaw = Fixtures.readJson(RLU_FIXTURE);
        var rlus = rluRaw.getJSONArray("relayListUpdates");
        var expectedIps = new HashSet<String>();
        for (var i = 0; i < rlus.size(); i++) {
            var relays = rlus.getJSONObject(i).getJSONArray("relays");
            for (var j = 0; j < relays.size(); j++) {
                var addrs = relays.getJSONObject(j).getJSONArray("addresses");
                for (var k = 0; k < addrs.size(); k++) {
                    expectedIps.add(addrs.getJSONObject(k).getString("ipv4"));
                }
            }
        }

        var checked = 0;
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var bytes = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (bytes.length != 344) continue;

            var packet = WaRelayPacket.decode(bytes);
            var xorRelayedAttr = packet.attributes().stream()
                    .filter(a -> a.resolvedType() == WaRelayAttributeType.XOR_RELAYED_ADDRESS)
                    .findFirst()
                    .orElse(null);
            assertNotNull(xorRelayedAttr, "no XOR-RELAYED-ADDRESS in packet " + i);
            var addr = WaRelayXorAddress.decode(xorRelayedAttr.value(), packet.transactionId());
            assertEquals(3478, addr.port(), "every relay endpoint must be on port 3478");

            var ip = addr.address().getHostAddress();
            assertTrue(expectedIps.contains(ip), "decoded relay IP " + ip + " not in any captured RelayListUpdate");
            checked++;
        }
        assertTrue(checked >= 1, "expected at least 1 Allocate Request, got " + checked);
    }

    /**
     * Round-trips a decoded address back to wire bytes and asserts
     * byte-equality with the captured attribute value.
     *
     * @throws IOException if the fixture file cannot be read
     */
    @Test
    public void xorRelayedAddressRoundTrips() throws IOException {
        var raw = Fixtures.readJson(FIXTURE);
        var trace = raw.getJSONArray("captured").getJSONObject(0).getJSONArray("trace");
        for (var i = 0; i < trace.size(); i++) {
            var arg0 = trace.getJSONObject(i).getJSONArray("args").get(0);
            if (!(arg0 instanceof JSONObject obj)) continue;
            var bytes = Base64.getDecoder().decode(obj.getString("$bytes"));
            if (bytes.length != 344) continue;

            var packet = WaRelayPacket.decode(bytes);
            var attr = packet.attributes().stream()
                    .filter(a -> a.resolvedType() == WaRelayAttributeType.XOR_RELAYED_ADDRESS)
                    .findFirst()
                    .orElseThrow();
            var addr = WaRelayXorAddress.decode(attr.value(), packet.transactionId());
            var roundTripped = addr.encode(packet.transactionId());
            assertArrayEquals(attr.value(), roundTripped,
                    () -> "XOR-RELAYED-ADDRESS round-trip mismatch at packet " + bytes.length);
            return;
        }
        throw new AssertionError("no 344-byte Allocate Request found in fixture");
    }
}
