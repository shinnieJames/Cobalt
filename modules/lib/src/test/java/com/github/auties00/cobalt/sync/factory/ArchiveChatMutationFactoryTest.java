package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Exercises {@link ArchiveChatMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing archive-chat mutation against the
 * {@code WAWebArchiveChatSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.ArchiveChatHandler}
 * whose inbound-side coverage lives in
 * {@code ArchiveChatHandlerTest}.
 *
 * @implNote
 * This implementation rebuilds the {@link SyncActionValueBuilder} graph
 * inline rather than calling {@link ArchiveChatMutationFactory} so the
 * test pins the exact field shape that the WA Web oracle captures
 * (archived flag, no message range), independent of any helper code in the
 * factory.
 */
@DisplayName("ArchiveChatMutationFactory")
class ArchiveChatMutationFactoryTest {
    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/archive-chat/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/archive-chat/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");
        var archived = oracle.getBoolean("archived");

        var action = new ArchiveChatActionBuilder().archived(archived).build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .archiveChatAction(action)
                .build();
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
