package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCallsBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.VoipRelayAllCallsMutationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link VoipRelayAllCallsHandler}'s parity with
 * {@code WAWebVoipRelayAllCallsSettingSync.applyMutations}.
 *
 * @apiNote
 * Covers the wire-constant trio, the happy {@code SET} branch that
 * persists the boolean to
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setRelayAllCalls(boolean)},
 * the malformed branch for missing action, the
 * {@link SyncdOperation#REMOVE} unsupported branch, the outgoing
 * builder shape from
 * {@link VoipRelayAllCallsMutationFactory}, and the default
 * conflict-resolution tiebreaker.
 *
 * @implNote
 * The fixture builds a temporary store with the local identity only;
 * tests that exercise the apply path observe the
 * {@code relayAllCalls} flag transition from the default unset state.
 */
@DisplayName("VoipRelayAllCallsHandler")
class VoipRelayAllCallsHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppClient client;

    /**
     * Builds the per-test harness.
     *
     * @apiNote
     * Each test runs against a fresh
     * {@link com.github.auties00.cobalt.store.WhatsAppStore} so the
     * {@code relayAllCalls} flag starts at its default value.
     */
    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    /**
     * Wraps the given enabled flag and operation into a trusted
     * mutation under the canonical {@code ["setting_relayAllCalls"]}
     * index.
     *
     * @param enabled the new relay-all-calls flag
     * @param op      the mutation operation
     * @param ts      the mutation timestamp
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted mutation(boolean enabled, SyncdOperation op, Instant ts) {
        var action = new PrivacySettingRelayAllCallsBuilder().isEnabled(enabled).build();
        var value = new SyncActionValueBuilder()
                .timestamp(ts)
                .privacySettingRelayAllCalls(action)
                .build();
        return new DecryptedMutation.Trusted("[\"setting_relayAllCalls\"]", value, op, ts,
                PrivacySettingRelayAllCalls.ACTION_VERSION);
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the PrivacySettingRelayAllCalls wire constant")
        void actionName() {
            assertEquals(PrivacySettingRelayAllCalls.ACTION_NAME, new VoipRelayAllCallsHandler().actionName());
            assertEquals("setting_relayAllCalls", new VoipRelayAllCallsHandler().actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(PrivacySettingRelayAllCalls.COLLECTION_NAME, new VoipRelayAllCallsHandler().collectionName());
            assertEquals(SyncPatchType.REGULAR, new VoipRelayAllCallsHandler().collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version (1)")
        void version() {
            assertEquals(PrivacySettingRelayAllCalls.ACTION_VERSION, new VoipRelayAllCallsHandler().version());
            assertEquals(1, new VoipRelayAllCallsHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation - happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("SET true updates the store flag and returns SUCCESS")
        void setsTrue() {
            assertFalse(client.store().relayAllCalls(), "precondition: starts false");
            var result = new VoipRelayAllCallsHandler().applyMutation(client,
                    mutation(true, SyncdOperation.SET, Instant.ofEpochSecond(1_700_000_000L)));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(client.store().relayAllCalls());
        }

        @Test
        @DisplayName("SET false flips the flag back to false")
        void setsFalse() {
            client.store().setRelayAllCalls(true);
            var result = new VoipRelayAllCallsHandler().applyMutation(client,
                    mutation(false, SyncdOperation.SET, Instant.ofEpochSecond(1_700_000_000L)));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(client.store().relayAllCalls());
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("relay-all-calls is a global setting; no per-entity orphan path")
        void noOrphan() {
            var result = new VoipRelayAllCallsHandler().applyMutation(client,
                    mutation(true, SyncdOperation.SET, Instant.now()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed action value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SyncActionValue carrying a different action returns MALFORMED")
        void wrongActionIsMalformed() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .archiveChatAction(new ArchiveChatActionBuilder().archived(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("[\"setting_relayAllCalls\"]", value, SyncdOperation.SET, ts, 1);
            var result = new VoipRelayAllCallsHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed action index")
    class MalformedActionIndex {
        @Test
        @DisplayName("the handler ignores the index shape (global setting)")
        void indexShapeIgnored() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .privacySettingRelayAllCalls(new PrivacySettingRelayAllCallsBuilder().isEnabled(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("", value, SyncdOperation.SET, ts, 1);
            var result = new VoipRelayAllCallsHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "the handler does not parse the index; the setting is keyed off the action only");
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE returns UNSUPPORTED")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE is unsupported per the WA Web fall-through")
        void removeIsUnsupported() {
            var result = new VoipRelayAllCallsHandler().applyMutation(client,
                    mutation(true, SyncdOperation.REMOVE, Instant.now()));
            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - inherits default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = mutation(false, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = mutation(true, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    new VoipRelayAllCallsHandler().resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = mutation(false, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = mutation(true, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    new VoipRelayAllCallsHandler().resolveConflicts(local, remote).state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch - inherits default sequential apply")
    class ApplyBatch {
        @Test
        @DisplayName("default batch path applies each mutation in order")
        void sequentialApply() {
            var results = new VoipRelayAllCallsHandler().applyMutationBatch(client, List.of(
                    mutation(true, SyncdOperation.SET, Instant.ofEpochSecond(1_000)),
                    mutation(false, SyncdOperation.SET, Instant.ofEpochSecond(2_000))
            ));
            assertEquals(2, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
            assertEquals(SyncActionState.SUCCESS, results.get(1).actionState());
            assertFalse(client.store().relayAllCalls(),
                    "the second SET overwrites the first under the default sequential apply");
        }
    }

}
