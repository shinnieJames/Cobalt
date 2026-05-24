package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link LabelAssociationMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing label-association mutation against the
 * {@code WAWebLabelJidSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.LabelAssociationHandler}
 * whose inbound-side coverage lives in
 * {@code LabelAssociationHandlerTest}.
 *
 * @implNote
 * This implementation pins the label id, chat JID, labelled flag, and
 * timestamp to the values captured by the WA Web oracle so byte parity
 * holds.
 */
@DisplayName("LabelAssociationMutationFactory")
class LabelAssociationMutationFactoryTest {
    /**
     * Chat JID used as the third index segment.
     */
    private static final Jid CHAT_JID = Jid.of("11110000@s.whatsapp.net");

    /**
     * The factory under test; rebuilt before each scenario.
     */
    private LabelAssociationMutationFactory factory;

    /**
     * Builds a fresh {@link LabelAssociationMutationFactory} before each test.
     */
    @BeforeEach
    void setUp() {
        factory = new LabelAssociationMutationFactory();
    }

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/label-association/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/label-association/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.createLabelAssociationMutation(
                "42", CHAT_JID, true, Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
