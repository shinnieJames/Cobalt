package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link UnarchiveChatsSettingMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.UnarchiveChatsSettingHandler}
 * whose incoming-side coverage lives in
 * {@code UnarchiveChatsSettingHandlerTest}; the production class has no
 * dedicated WA Web outgoing helper, so the parity target is the generic
 * {@code WAWebSyncdActionUtils.buildPendingMutation} pathway used by
 * every other {@code AccountSyncdActionBase} setter.
 *
 * @implNote
 * This implementation calls the factory directly with the
 * {@code unarchiveChats == true} branch at a pinned timestamp; the
 * disabled branch differs only by the inner boolean and is covered by
 * the handler tests.
 */
@DisplayName("UnarchiveChatsSettingMutationFactory")
class UnarchiveChatsSettingMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/unarchive-chats-setting/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/unarchive-chats-setting/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = new UnarchiveChatsSettingMutationFactory().getUnarchiveChatsMutation(Instant.ofEpochSecond(1_700_000_000L), true);
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
