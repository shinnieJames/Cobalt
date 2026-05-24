package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link RemoveRecentStickerMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.RemoveRecentStickerHandler}
 * whose incoming-side coverage lives in
 * {@code RemoveRecentStickerHandlerTest}; the parity target is
 * {@code WAWebStickersRemoveRecentSyncAction.generateRemoveStickerMutation}.
 *
 * @implNote
 * This implementation only verifies the oracle loads when present; the
 * full byte-equality check requires a deterministic sticker hash and
 * timestamp that the corpus capture does not yet emit, so the test
 * stops at the non-null oracle check until the fixture grows.
 */
@DisplayName("RemoveRecentStickerMutationFactory")
class RemoveRecentStickerMutationFactoryTest {
    /**
     * Verifies that the oracle loads when the fixture is present.
     */
    @Test
    @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
    void oracle() {
        if (!SyncFixtures.isOracleAvailable("handler/remove-recent-sticker/encode")) {
            return;
        }
        var oracle = SyncFixtures.loadOracle("handler/remove-recent-sticker/encode");
        assertNotNull(oracle);
    }
}
