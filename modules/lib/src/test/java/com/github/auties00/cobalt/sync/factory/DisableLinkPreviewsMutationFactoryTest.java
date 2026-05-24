package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link DisableLinkPreviewsMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing disable-link-previews mutation against the
 * {@code WAWebDisableLinkPreviewsSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.DisableLinkPreviewsHandler}
 * whose inbound-side coverage lives in
 * {@code DisableLinkPreviewsHandlerTest}.
 *
 * @implNote
 * This implementation pins the timestamp to the seed used when capturing
 * the WA Web oracle so byte parity holds; the flag is set to
 * {@code isPreviewsDisabled = true} to match the capture.
 */
@DisplayName("DisableLinkPreviewsMutationFactory")
class DisableLinkPreviewsMutationFactoryTest {
    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/disable-link-previews/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/disable-link-previews/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = new DisableLinkPreviewsMutationFactory().getDisableLinkPreviewsMutation(Instant.ofEpochSecond(1_700_000_000L), true);
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
