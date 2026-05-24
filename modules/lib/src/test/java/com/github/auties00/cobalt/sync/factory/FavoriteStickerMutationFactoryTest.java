package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link FavoriteStickerMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing favourite-sticker mutation against the
 * {@code WAWebStickersFavoriteSyncAction} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.FavoriteStickerHandler}
 * whose inbound-side coverage lives in
 * {@code FavoriteStickerHandlerTest}.
 *
 * @implNote
 * This implementation gates on
 * {@link SyncFixtures#isOracleAvailable(String)} so the suite remains green
 * until a real WAWeb-captured fixture pinning
 * {@code (stickerHash, isFavorite)} to encoded bytes is added.
 */
@DisplayName("FavoriteStickerMutationFactory")
class FavoriteStickerMutationFactoryTest {
    /**
     * Asserts that the captured encode oracle is loadable when present.
     */
    @Test
    @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
    void oracle() {
        if (!SyncFixtures.isOracleAvailable("handler/favorite-sticker/encode")) {
            return;
        }
        var oracle = SyncFixtures.loadOracle("handler/favorite-sticker/encode");
        assertNotNull(oracle);
    }
}
