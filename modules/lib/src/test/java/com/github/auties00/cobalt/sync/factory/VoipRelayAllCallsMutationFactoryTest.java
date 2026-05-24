package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link VoipRelayAllCallsMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.VoipRelayAllCallsHandler}
 * whose incoming-side coverage lives in
 * {@code VoipRelayAllCallsHandlerTest}; the parity target is
 * {@code WAWebVoipRelayAllCallsSettingSync.getMutation}, the
 * privacy-calls relay toggle.
 *
 * @implNote
 * This implementation calls the factory directly with the
 * {@code isEnabled == true} branch at a pinned timestamp; the disabled
 * branch differs only by the inner boolean and is covered by the
 * handler tests.
 */
@DisplayName("VoipRelayAllCallsMutationFactory")
class VoipRelayAllCallsMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/voip-relay-all-calls/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/voip-relay-all-calls/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = new VoipRelayAllCallsMutationFactory().getVoipRelayAllCallsMutation(Instant.ofEpochSecond(1_700_000_000L), true);
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
