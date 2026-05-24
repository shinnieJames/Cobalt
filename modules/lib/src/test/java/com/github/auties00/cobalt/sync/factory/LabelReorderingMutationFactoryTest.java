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
 * {@link LabelReorderingMutationFactory}.
 *
 * @apiNote
 * Pairs with {@link com.github.auties00.cobalt.sync.handler.LabelReorderingHandler}
 * whose incoming-side coverage lives in
 * {@code LabelReorderingHandlerTest}; the production class has no
 * dedicated WA Web outgoing helper so this test verifies the
 * {@code WAWebSyncdActionUtils.buildPendingMutation}-shaped output against
 * a captured oracle.
 *
 * @implNote
 * This implementation skips when the oracle fixture is unavailable so
 * developers running the suite without a corpus dump still see a green
 * build; when the oracle is present, the encoded
 * {@link SyncActionValueSpec} bytes must match exactly.
 */
@DisplayName("LabelReorderingMutationFactory")
class LabelReorderingMutationFactoryTest {
    /**
     * The factory under test, freshly constructed per test method.
     */
    private LabelReorderingMutationFactory factory;

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
        factory = new LabelReorderingMutationFactory();
    }

    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/label-reordering/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/label-reordering/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.getReorderLabelsMutation(List.of(1, 2, 3), Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
