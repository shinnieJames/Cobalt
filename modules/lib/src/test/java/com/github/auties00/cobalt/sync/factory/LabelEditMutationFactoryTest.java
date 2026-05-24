package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditAction;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link LabelEditMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing label-edit mutation against the
 * {@code WAWebLabelSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.LabelEditHandler} whose
 * inbound-side coverage lives in {@code LabelEditHandlerTest}.
 *
 * @implNote
 * This implementation pins every label field (id, name, colour, deleted
 * flag, predefined id, active flag, list type, timestamp) to the values
 * captured by the WA Web oracle so byte parity holds.
 */
@DisplayName("LabelEditMutationFactory")
class LabelEditMutationFactoryTest {
    /**
     * The factory under test; rebuilt before each scenario.
     */
    private LabelEditMutationFactory factory;

    /**
     * Builds a fresh {@link LabelEditMutationFactory} before each test.
     */
    @BeforeEach
    void setUp() {
        factory = new LabelEditMutationFactory();
    }

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/label-edit/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/label-edit/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.getLabelMutation(
                "42", "Customers", 5, false, 0, true,
                LabelEditAction.ListType.NONE, Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
