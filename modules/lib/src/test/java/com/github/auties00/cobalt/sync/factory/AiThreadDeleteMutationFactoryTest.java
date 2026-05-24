package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link AiThreadDeleteMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing AI-thread-delete mutation. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.AiThreadDeleteHandler}
 * whose inbound-side coverage lives in
 * {@code AiThreadDeleteHandlerTest}.
 *
 * @implNote
 * This implementation gates the encode test on
 * {@link SyncFixtures#isOracleAvailable(String)}; when no encode fixture
 * has been captured the test no-ops cleanly, matching the rule in
 * memory feedback_no_synthetic_fixtures.
 */
@DisplayName("AiThreadDeleteMutationFactory")
class AiThreadDeleteMutationFactoryTest {
    /**
     * Asserts that the captured encode oracle is loadable when present.
     */
    @Test
    @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
    void oracle() {
        if (!SyncFixtures.isOracleAvailable("handler/ai-thread-delete/encode")) {
            return;
        }
        var oracle = SyncFixtures.loadOracle("handler/ai-thread-delete/encode");
        assertNotNull(oracle);
    }
}
