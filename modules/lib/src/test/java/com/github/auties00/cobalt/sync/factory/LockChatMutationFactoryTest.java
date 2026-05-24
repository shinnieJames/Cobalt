package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.chat.LockChatActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link LockChatMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.LockChatHandler} whose
 * incoming-side coverage lives in {@code LockChatHandlerTest}; the
 * parity target is {@code WAWebLockChatSync.getChatLockMutation}, whose
 * value shape carries only the {@code locked} flag.
 *
 * @implNote
 * This implementation rebuilds the {@code lockChatAction} value from the
 * oracle's {@code locked} flag and {@code timestampSeconds} field rather
 * than calling the factory directly, so the parity check exercises the
 * protobuf encoding rather than the index-string formatting; the
 * companion archive and pin mutations emitted by
 * {@link LockChatMutationFactory#getMutationsForLock(Instant, boolean, com.github.auties00.cobalt.model.jid.Jid, com.github.auties00.cobalt.model.sync.SyncActionMessageRange)}
 * are covered by their own factory tests.
 */
@DisplayName("LockChatMutationFactory")
class LockChatMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/lock-chat/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/lock-chat/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");
        var locked = oracle.getBoolean("locked");

        var action = new LockChatActionBuilder().locked(locked).build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .lockChatAction(action)
                .build();
        assertNotNull(expected);
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
