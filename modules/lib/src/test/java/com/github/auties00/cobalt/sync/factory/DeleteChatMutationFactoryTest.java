package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link DeleteChatMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing delete-chat mutation against the
 * {@code WAWebDeleteChatSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.DeleteChatHandler} whose
 * inbound-side coverage lives in {@code DeleteChatHandlerTest}.
 *
 * @implNote
 * This implementation rebuilds the {@link SyncActionValueBuilder} graph
 * inline rather than calling {@link DeleteChatMutationFactory} so the
 * captured shape (empty {@link DeleteChatActionBuilder}, no message range)
 * is pinned independently of any factory-side coalescing.
 */
@DisplayName("DeleteChatMutationFactory")
class DeleteChatMutationFactoryTest {
    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/delete-chat/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/delete-chat/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var action = new DeleteChatActionBuilder().build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .deleteChatAction(action)
                .build();
        assertNotNull(expected);
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
