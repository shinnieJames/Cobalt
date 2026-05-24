package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.action.business.BroadcastListParticipantAction;
import com.github.auties00.cobalt.model.sync.action.business.BroadcastListParticipantActionBuilder;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link BusinessBroadcastListMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing business-broadcast-list mutation against the
 * {@code WAWebBroadcastListSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.BusinessBroadcastListHandler}
 * whose inbound-side coverage lives in
 * {@code BusinessBroadcastListHandlerTest}.
 *
 * @implNote
 * This implementation exercises the four-argument overload (no audience
 * expression); the WA Web oracle was captured by sending the broadcast
 * list with a {@code null} audience and a single LID participant.
 */
@DisplayName("BusinessBroadcastListMutationFactory")
class BusinessBroadcastListMutationFactoryTest {
    /**
     * Participant LID used by the sample broadcast list.
     */
    private static final Jid PARTICIPANT_LID = Jid.of("83116928594001@lid");

    /**
     * The factory under test; rebuilt before each scenario.
     */
    private BusinessBroadcastListMutationFactory factory;

    /**
     * Builds a fresh {@link BusinessBroadcastListMutationFactory} before each test.
     */
    @BeforeEach
    void setUp() {
        factory = new BusinessBroadcastListMutationFactory();
    }

    /**
     * Returns a fixed {@link BroadcastListParticipantAction} matching the WA Web capture.
     *
     * @apiNote
     * Test fixture; the participant LID must stay in lockstep with the
     * captured oracle for byte-equality to hold.
     *
     * @return the canonical sample participant
     */
    private BroadcastListParticipantAction sampleParticipant() {
        return new BroadcastListParticipantActionBuilder().lidJid(PARTICIPANT_LID).build();
    }

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/business-broadcast-list/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/business-broadcast-list/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.getBroadcastListMutation(
                "list-oracle", List.of(sampleParticipant()), "Oracle",
                Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
