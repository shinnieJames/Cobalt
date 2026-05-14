package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.model.call.CallLogBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.call.CallLogAction;
import com.github.auties00.cobalt.model.sync.action.call.CallLogActionBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.CallLogMutationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CallLogHandler} â€” Cobalt's adapter for
 * {@code WAWebCallLogSync}.
 */
@DisplayName("CallLogHandler")
class CallLogHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid PEER = Jid.of("1234567890@s.whatsapp.net");
    private static final String CALL_ID = "CALL_ID_42";

    private WhatsAppClient client;

    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    private static CallLog log(String callId) {
        return new CallLogBuilder().callId(callId).startTime(Instant.ofEpochSecond(1_700_000_000L)).build();
    }

    private static String callLogIndex(Jid peer, String callId, boolean fromMe) {
        return "[\"call_log\",\"" + peer.toString() + "\",\"" + callId + "\",\"" + (fromMe ? "1" : "0") + "\"]";
    }

    private static DecryptedMutation.Trusted setMutation(Jid peer, String callId, boolean fromMe, CallLog payload, Instant ts) {
        var actionBuilder = new CallLogActionBuilder();
        if (payload != null) actionBuilder.log(payload);
        var value = new SyncActionValueBuilder().timestamp(ts).callLogAction(actionBuilder.build()).build();
        return new DecryptedMutation.Trusted(callLogIndex(peer, callId, fromMe), value, SyncdOperation.SET, ts, CallLogAction.ACTION_VERSION);
    }

    private static DecryptedMutation.Trusted removeMutation(Jid peer, String callId, boolean fromMe, Instant ts) {
        var value = new SyncActionValueBuilder().timestamp(ts).callLogAction(new CallLogActionBuilder().build()).build();
        return new DecryptedMutation.Trusted(callLogIndex(peer, callId, fromMe), value, SyncdOperation.REMOVE, ts, CallLogAction.ACTION_VERSION);
    }

    @Nested
    @DisplayName("metadata â€” wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the CallLogAction wire constant")
        void actionName() {
            assertEquals(CallLogAction.ACTION_NAME, new CallLogHandler().actionName());
            assertEquals("call_log", new CallLogHandler().actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(CallLogAction.COLLECTION_NAME, new CallLogHandler().collectionName());
            assertEquals(SyncPatchType.REGULAR, new CallLogHandler().collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version (1)")
        void version() {
            assertEquals(CallLogAction.ACTION_VERSION, new CallLogHandler().version());
            assertEquals(1, new CallLogHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("SET with a well-formed log adds it to the store and returns SUCCESS")
        void addsCallLog() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = new CallLogHandler().applyMutation(client, setMutation(PEER, CALL_ID, false, log(CALL_ID), ts));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(client.store().findCallLog(CALL_ID).isPresent(),
                    "WAWebCallLogSync.applyMutations writes the record via generateCallLogFromCallSyncRecord; Cobalt stores it directly");
        }
    }

    @Nested
    @DisplayName("applyMutation â€” orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("call log entries are keyed by callId; there is no parent entity to be missing")
        void noOrphan() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = new CallLogHandler().applyMutation(client, setMutation(PEER, CALL_ID, false, log(CALL_ID), ts));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed action value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SyncActionValue carrying a different action returns MALFORMED")
        void wrongActionIsMalformed() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .archiveChatAction(new ArchiveChatActionBuilder().archived(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(callLogIndex(PEER, CALL_ID, false), value, SyncdOperation.SET, ts, 1);
            var result = new CallLogHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a CallLogAction missing the inner log payload returns MALFORMED")
        void nullLogIsMalformed() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = new CallLogHandler().applyMutation(client, setMutation(PEER, CALL_ID, false, null, ts));
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a log without a callId returns MALFORMED (Cobalt's addCallLog precondition)")
        void emptyCallIdIsMalformed() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var logNoId = new CallLogBuilder().startTime(Instant.ofEpochSecond(1)).build();
            var result = new CallLogHandler().applyMutation(client, setMutation(PEER, CALL_ID, false, logNoId, ts));
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed action index")
    class MalformedActionIndex {
        @Test
        @DisplayName("an index with the wrong arity returns MALFORMED")
        void wrongArityIsMalformed() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder().timestamp(ts).callLogAction(new CallLogActionBuilder().log(log(CALL_ID)).build()).build();
            // only 3 elements instead of 4
            var mutation = new DecryptedMutation.Trusted("[\"call_log\",\"" + PEER + "\",\"" + CALL_ID + "\"]", value, SyncdOperation.SET, ts, 1);
            var result = new CallLogHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” REMOVE")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE drops the call log entry and returns SUCCESS")
        void removeDropsEntry() {
            client.store().addCallLog(log(CALL_ID));
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = new CallLogHandler().applyMutation(client, removeMutation(PEER, CALL_ID, false, ts));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(client.store().findCallLog(CALL_ID).isEmpty(),
                    "Cobalt's REMOVE branch additionally clears the stored entry");
        }

        @Test
        @DisplayName("REMOVE on an unknown callId still returns SUCCESS")
        void removeUnknownIdSuccess() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = new CallLogHandler().applyMutation(client, removeMutation(PEER, "no-such-id", false, ts));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts â€” inherits default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote â†’ APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = setMutation(PEER, CALL_ID, false, log(CALL_ID), Instant.ofEpochSecond(1_000));
            var remote = setMutation(PEER, CALL_ID, false, log(CALL_ID), Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    new CallLogHandler().resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote â†’ SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = setMutation(PEER, CALL_ID, false, log(CALL_ID), Instant.ofEpochSecond(2_000));
            var remote = setMutation(PEER, CALL_ID, false, log(CALL_ID), Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    new CallLogHandler().resolveConflicts(local, remote).state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch â€” inherits default sequential apply")
    class ApplyBatch {
        @Test
        @DisplayName("default batch path applies each mutation in order")
        void sequentialApply() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var results = new CallLogHandler().applyMutationBatch(client, List.of(
                    setMutation(PEER, "id-A", false, log("id-A"), ts),
                    setMutation(PEER, "id-B", false, log("id-B"), ts.plusSeconds(1))
            ));
            assertEquals(2, results.size());
            for (var r : results) {
                assertEquals(SyncActionState.SUCCESS, r.actionState());
            }
            assertTrue(client.store().findCallLog("id-A").isPresent());
            assertTrue(client.store().findCallLog("id-B").isPresent());
        }
    }

    @Nested
    @DisplayName("static builder â€” getCallLogMutation")
    class StaticBuilder {
        @Test
        @DisplayName("produces a SET mutation with the [action,callerJid,callId,fromMe] index")
        void buildsPendingMutation() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var payload = log(CALL_ID);
            var pending = new CallLogMutationFactory().getCallLogMutation(ts, PEER, CALL_ID, true, payload);
            var inner = pending.mutation();

            assertEquals(SyncdOperation.SET, inner.operation());
            assertEquals(CallLogAction.ACTION_VERSION, inner.actionVersion());
            assertEquals("[\"call_log\",\"" + PEER + "\",\"" + CALL_ID + "\",\"1\"]", inner.index());
            assertEquals(CALL_ID, inner.value().action().filter(a -> a instanceof CallLogAction).map(a -> (CallLogAction) a).orElseThrow().log().orElseThrow().callId().orElseThrow());
        }

        @Test
        @DisplayName("fromMe=false renders the index trailing slot as \"0\"")
        void fromMeFalseRendersZero() {
            var pending = new CallLogMutationFactory().getCallLogMutation(Instant.now(), PEER, CALL_ID, false, log(CALL_ID));
            assertTrue(pending.mutation().index().endsWith(",\"0\"]"));
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle (gated)")
    class OracleParity {
        @Test
        @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
        void byteEqualityWithOracle() {
            if (!SyncFixtures.isOracleAvailable("handler/call-log/encode")) return;
            var oracle = SyncFixtures.loadOracle("handler/call-log/encode");
            var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

            var pending = new CallLogMutationFactory().getCallLogMutation(
                    Instant.ofEpochSecond(1_700_000_000L), PEER, CALL_ID, true, log(CALL_ID));
            var actual = SyncActionValueSpec.encode(pending.mutation().value());

            assertNotNull(actual);
            assertArrayEquals(expected, actual);
        }
    }
}
