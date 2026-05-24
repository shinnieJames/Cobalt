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
 * Exercises {@link ChatAssignmentMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing chat-assignment mutation against the
 * {@code WAWebChatAssignmentSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.ChatAssignmentHandler}
 * whose inbound-side coverage lives in
 * {@code ChatAssignmentHandlerTest}.
 *
 * @implNote
 * This implementation pins the chat JID, agent id, and timestamp to the
 * values captured by the WA Web oracle so byte parity holds.
 */
@DisplayName("ChatAssignmentMutationFactory")
class ChatAssignmentMutationFactoryTest {
    /**
     * Chat JID used as the assignment index segment.
     */
    private static final Jid CHAT_JID = Jid.of("12345@s.whatsapp.net");

    /**
     * The factory under test; rebuilt before each scenario.
     */
    private ChatAssignmentMutationFactory factory;

    /**
     * Builds a fresh {@link ChatAssignmentMutationFactory} before each test.
     */
    @BeforeEach
    void setUp() {
        factory = new ChatAssignmentMutationFactory();
    }

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/chat-assignment/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/chat-assignment/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.createChatAssignmentMutation(
                CHAT_JID, "agent-9", Instant.ofEpochSecond(1_700_000_000L));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
