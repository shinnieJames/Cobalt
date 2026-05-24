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
 * {@link LocaleSettingMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.LocaleSettingHandler}
 * whose incoming-side coverage lives in
 * {@code LocaleSettingHandlerTest}; WA Web's
 * {@code WAWebLocaleSettingSync} has no outgoing helper, so the parity
 * target is the generic
 * {@code WAWebSyncdActionUtils.buildPendingMutation} pathway.
 *
 * @implNote
 * This implementation skips when the oracle fixture is unavailable; when
 * present, the encoded {@link SyncActionValueSpec} bytes must match the
 * captured payload exactly for the {@code "en_US"} locale at a pinned
 * timestamp.
 */
@DisplayName("LocaleSettingMutationFactory")
class LocaleSettingMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/locale-setting/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/locale-setting/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = new LocaleSettingMutationFactory().getLocaleMutation(Instant.ofEpochSecond(1_700_000_000L), "en_US");
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
