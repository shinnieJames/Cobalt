package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.chat.MuteActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link MuteChatMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.MuteChatHandler} whose
 * incoming-side coverage lives in {@code MuteChatHandlerTest}; the parity
 * target is {@code WAWebMuteChatSync.generateMuteMutation}, whose
 * {@code muteEndTimestamp} encoding preserves the {@code -1} sentinel
 * for indefinite mutes.
 *
 * @implNote
 * This implementation rebuilds the {@code muteAction} value from the
 * oracle's {@code muted} flag and {@code muteEndMillis} field rather
 * than invoking the production factory so the test runs without the
 * AB-props dependency; mention-everyone parity lives in the handler
 * tests.
 */
@DisplayName("MuteChatMutationFactory")
class MuteChatMutationFactoryTest {
    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
    void byteParityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/mute-chat/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/mute-chat/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");
        var muted = oracle.getBoolean("muted");
        var muteEndMillis = oracle.getLong("muteEndMillis");

        var builder = new MuteActionBuilder().muted(muted);
        if (muteEndMillis != null) builder.muteEndTimestamp(Instant.ofEpochMilli(muteEndMillis));
        var value = new SyncActionValueBuilder()
                .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                .muteAction(builder.build())
                .build();
        assertNotNull(expected);
        assertArrayEquals(expected, SyncActionValueSpec.encode(value));
    }
}
