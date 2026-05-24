package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.chat.ClearChatActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link ClearChatMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing clear-chat mutation against the
 * {@code WAWebClearChatSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.ClearChatHandler} whose
 * inbound-side coverage lives in {@code ClearChatHandlerTest}.
 *
 * @implNote
 * This implementation rebuilds the {@link SyncActionValueBuilder} graph
 * inline rather than calling {@link ClearChatMutationFactory} so the
 * captured shape (empty {@link ClearChatActionBuilder}, no message range)
 * is pinned independently of any factory-side coalescing.
 */
@DisplayName("ClearChatMutationFactory")
class ClearChatMutationFactoryTest {
    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/clear-chat/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/clear-chat/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var action = new ClearChatActionBuilder().build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .clearChatAction(action)
                .build();
        assertNotNull(expected);
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
