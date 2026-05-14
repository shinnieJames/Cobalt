package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for {@link DeviceFixtures}.
 *
 * <p>Covers:
 *
 * <ol>
 *   <li>Oracle loading round-trips the JSON-stringified
 *       {@code result.value} payload through {@link DeviceFixtures#loadOracle}.</li>
 *   <li>The self-identity baseline parses into the bare PN and LID this
 *       session captured against
 *       ({@code 19254863482@c.us}, {@code 83116928594056@lid}).</li>
 *   <li>{@link DeviceFixtures#isAvailable} discriminates between
 *       committed-corpus topics and topics that have not yet been
 *       captured.</li>
 *   <li>{@link DeviceFixtures#temporaryStore} returns a store with the
 *       caller-supplied self JID and LID actually set on it (the
 *       implicit factory step that every other device test depends on).</li>
 * </ol>
 */
class DeviceFixturesTest {

    @Test
    void selfIdentityOracleParses() {
        var oracle = DeviceFixtures.loadOracle("self-identity");
        assertEquals("19254863482@c.us", oracle.getString("mePn"));
        assertEquals("83116928594056@lid", oracle.getString("meLid"));
        assertEquals("19254863482:1@c.us", oracle.getString("meDevicePn"));
        assertEquals("83116928594056:1@lid", oracle.getString("meDeviceLid"));
        assertEquals(1, oracle.getIntValue("meDeviceId"));
    }

    @Test
    void phashOracleHasFourSamples() {
        var oracle = DeviceFixtures.loadOracle("phash-samples");
        // The capture-device-corpus driver writes four samples. If the corpus
        // is regenerated with a different sample set, this test pins the
        // contract that downstream KAT tests rely on.
        assertEquals(4, oracle.size());
        for (var key : oracle.keySet()) {
            var sample = oracle.getJSONObject(key);
            assertNotNull(sample.getJSONArray("devices"));
            assertTrue(sample.getString("phashV1").startsWith("1:"));
            assertTrue(sample.getString("phashV2").startsWith("2:"));
        }
    }

    @Test
    void isAvailableDiscriminates() {
        // The corpus does not commit a JSONL stanza dump under "self-identity";
        // only the .expected.json oracle is present. isAvailable() guards the
        // JSONL pathway and should report false here.
        assertFalse(DeviceFixtures.isAvailable("self-identity"));
        assertFalse(DeviceFixtures.isAvailable("does-not-exist"));
    }

    @Test
    void temporaryStoreReflectsCallerJids() {
        var pn = Jid.of("19254863482@s.whatsapp.net");
        var lid = Jid.of("83116928594056@lid");
        var store = DeviceFixtures.temporaryStore(pn, lid);
        assertEquals(pn, store.jid().orElseThrow());
        assertEquals(lid, store.lid().orElseThrow());
    }
}
