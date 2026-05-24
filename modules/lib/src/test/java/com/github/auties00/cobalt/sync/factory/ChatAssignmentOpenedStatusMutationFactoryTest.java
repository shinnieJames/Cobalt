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
 * Exercises {@link ChatAssignmentOpenedStatusMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing chat-assignment-opened-status mutation
 * against the {@code WAWebChatAssignmentOpenedStatusSync} JS encoder. Pairs
 * with
 * {@link com.github.auties00.cobalt.sync.handler.ChatAssignmentOpenedStatusHandler}
 * whose inbound-side coverage lives in
 * {@code ChatAssignmentOpenedStatusHandlerTest}.
 *
 * @implNote
 * This implementation pins the chat JID, agent id, open flag, and
 * timestamp to the values captured by the WA Web oracle so byte parity
 * holds.
 */
@DisplayName("ChatAssignmentOpenedStatusMutationFactory")
class ChatAssignmentOpenedStatusMutationFactoryTest {
    /**
     * Chat JID used as the second index segment.
     */
    private static final Jid CHAT_JID = Jid.of("12345@s.whatsapp.net");

    /**
     * Agent identifier used as the third index segment.
     */
    private static final String AGENT_ID = "agent-1";

    /**
     * The factory under test; rebuilt before each scenario.
     */
    private ChatAssignmentOpenedStatusMutationFactory factory;

    /**
     * Builds a fresh {@link ChatAssignmentOpenedStatusMutationFactory} before each test.
     */
    @BeforeEach
    void setUp() {
        factory = new ChatAssignmentOpenedStatusMutationFactory();
    }

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/chat-assignment-opened-status/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/chat-assignment-opened-status/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.createChatOpenedMutation(
                CHAT_JID, AGENT_ID, true, Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
