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
 * {@link PushNameSettingMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.PushNameSettingHandler}
 * whose incoming-side coverage lives in
 * {@code PushNameSettingHandlerTest}; the parity target is
 * {@code WAWebPushNameSync.getPushnameMutation}, the single critical-block
 * setting that participates in the bootstrap data sync stage.
 *
 * @implNote
 * This implementation calls the factory directly with the canonical
 * {@code "Maria"} pushname at a pinned timestamp so the captured oracle
 * is reproducible across builds.
 */
@DisplayName("PushNameSettingMutationFactory")
class PushNameSettingMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/push-name-setting/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/push-name-setting/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = new PushNameSettingMutationFactory().getPushnameMutation(Instant.ofEpochSecond(1_700_000_000L), "Maria");
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
