package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.chat.MarkChatAsReadActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link MarkChatAsReadMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.MarkChatAsReadHandler}
 * whose incoming-side coverage lives in
 * {@code MarkChatAsReadHandlerTest}; the parity target is
 * {@code WAWebMarkChatAsReadSync.getMarkChatAsReadMutation}.
 *
 * @implNote
 * This implementation rebuilds the {@code markChatAsReadAction} value
 * from the oracle's {@code read} flag and {@code timestampSeconds} field
 * with a null message range so the parity check focuses on the read
 * boolean encoding; range-bearing parity is covered by the handler tests.
 */
@DisplayName("MarkChatAsReadMutationFactory")
class MarkChatAsReadMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/mark-chat-as-read/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/mark-chat-as-read/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");
        var read = oracle.getBoolean("read");

        var action = new MarkChatAsReadActionBuilder().read(read).build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .markChatAsReadAction(action)
                .build();
        assertNotNull(expected);
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
