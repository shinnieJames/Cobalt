package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link CallLogMutationFactory} against captured WhatsApp Web encode payloads.
 *
 * @apiNote
 * Parity gate for the outgoing call-log mutation against the
 * {@code WAWebCallLogSync} JS encoder. Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.CallLogHandler} whose
 * inbound-side coverage lives in {@code CallLogHandlerTest}.
 *
 * @implNote
 * This implementation passes a {@code null} {@link com.github.auties00.cobalt.model.call.CallLog}
 * record so the captured oracle pins the minimal action shape; the fixture
 * for a populated call record will land in a follow-up matrix.
 */
@DisplayName("CallLogMutationFactory")
class CallLogMutationFactoryTest {
    /**
     * Peer JID used as the resolved caller index segment.
     */
    private static final Jid PEER = Jid.of("1234567890@s.whatsapp.net");

    /**
     * Call identifier used in the index.
     */
    private static final String CALL_ID = "CALL_ID_42";

    /**
     * Asserts byte parity between the captured oracle and Cobalt's encoded action value.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/call-log/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/call-log/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = new CallLogMutationFactory().getCallLogMutation(
                Instant.ofEpochSecond(1_700_000_000L), PEER, CALL_ID, true, null);
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
