package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link PinChatMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.PinChatHandler} whose
 * incoming-side coverage lives in {@code PinChatHandlerTest}; the parity
 * target is {@code WAWebPinChatSync.getPinMutation}, which the WA Web
 * pin gesture and the companion unpin emitted by
 * {@link LockChatMutationFactory#getMutationsForLock(Instant, boolean, com.github.auties00.cobalt.model.jid.Jid, com.github.auties00.cobalt.model.sync.SyncActionMessageRange)}
 * both go through.
 *
 * @implNote
 * This implementation rebuilds the {@code pinAction} value from the
 * oracle's {@code pinned} flag and {@code timestampSeconds} field rather
 * than invoking the production factory so the parity check focuses on
 * the boolean encoding; index-string formatting is covered by the
 * handler tests.
 */
@DisplayName("PinChatMutationFactory")
class PinChatMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/pin-chat/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/pin-chat/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");
        var pinned = oracle.getBoolean("pinned");

        var action = new PinActionBuilder().pinned(pinned).build();
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .pinAction(action)
                .build();
        assertNotNull(expected);
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
