package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link QuickReplyMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.QuickReplyHandler} whose
 * incoming-side coverage lives in {@code QuickReplyHandlerTest}; the
 * parity target is
 * {@code WAWebQuickRepliesSync.getQuickReplyAddOrEditMutation}.
 *
 * @implNote
 * This implementation only exercises the add-or-edit path because the
 * delete path covers a subset of the add-or-edit value shape; the
 * delete-mutation parity is implicit since the only divergence is the
 * {@code deleted} flag flip.
 */
@DisplayName("QuickReplyMutationFactory")
class QuickReplyMutationFactoryTest {
    /**
     * The factory under test, freshly constructed per test method.
     */
    private QuickReplyMutationFactory factory;

    /**
     * Constructs the per-test factory instance.
     *
     * @apiNote
     * Required by JUnit's per-method fixture lifecycle; refreshing the
     * factory ensures no state leaks between tests even though the
     * production class is currently stateless.
     */
    @BeforeEach
    void setUp() {
        factory = new QuickReplyMutationFactory();
    }

    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/quick-reply/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/quick-reply/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.getQuickReplyAddOrEditMutation(
                "qr-oracle", "/hello", "Hi", 1, List.of("k1"),
                Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
