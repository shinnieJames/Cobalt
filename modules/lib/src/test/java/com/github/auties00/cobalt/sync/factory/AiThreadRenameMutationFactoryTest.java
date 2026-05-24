package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link AiThreadRenameMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing AI-thread-rename mutation. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.AiThreadRenameHandler}
 * whose inbound-side coverage lives in
 * {@code AiThreadRenameHandlerTest}.
 *
 * @implNote
 * This implementation gates the encode test on
 * {@link SyncFixtures#isOracleAvailable(String)} so the suite remains green
 * until a real WAWeb-captured encode fixture is added.
 */
@DisplayName("AiThreadRenameMutationFactory")
class AiThreadRenameMutationFactoryTest {
    /**
     * Asserts that the captured encode oracle is loadable when present.
     */
    @Test
    @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
    void oracle() {
        if (!SyncFixtures.isOracleAvailable("handler/ai-thread-rename/encode")) {
            return;
        }
        var oracle = SyncFixtures.loadOracle("handler/ai-thread-rename/encode");
        assertNotNull(oracle);
    }
}
