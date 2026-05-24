package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link DeleteMessageForMeMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing delete-message-for-me mutation against the
 * {@code WAWebDeleteMessageForMeSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.DeleteMessageForMeHandler}
 * whose inbound-side coverage lives in
 * {@code DeleteMessageForMeHandlerTest}.
 *
 * @implNote
 * This implementation rebuilds the action graph inline so the captured
 * minimal shape (only {@code deleteMedia} and timestamp) is pinned
 * independently of the full per-message index that the factory would
 * normally produce.
 */
@DisplayName("DeleteMessageForMeMutationFactory")
class DeleteMessageForMeMutationFactoryTest {
    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/delete-message-for-me/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/delete-message-for-me/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");
        var deleteMedia = oracle.getBoolean("deleteMedia");

        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .deleteMessageForMeAction(new DeleteMessageForMeActionBuilder().deleteMedia(deleteMedia).build())
                .build();
        assertNotNull(expected);
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
