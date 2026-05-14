package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSetting;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSettingBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.factory.UnarchiveChatsSettingMutationFactory;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link UnarchiveChatsSettingHandler} â€” Cobalt's adapter for
 * {@code WAWebArchiveSettingSync}.
 */
@DisplayName("UnarchiveChatsSettingHandler")
class UnarchiveChatsSettingHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppClient client;

    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    private static DecryptedMutation.Trusted unarchiveMutation(Boolean value, SyncdOperation op, Instant ts) {
        var builder = new UnarchiveChatsSettingBuilder();
        if (value != null) builder.unarchiveChats(value);
        var actionValue = new SyncActionValueBuilder()
                .timestamp(ts)
                .unarchiveChatsSetting(builder.build())
                .build();
        return new DecryptedMutation.Trusted("[\"setting_unarchiveChats\"]", actionValue, op, ts, UnarchiveChatsSetting.ACTION_VERSION);
    }

    @Nested
    @DisplayName("metadata â€” wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the UnarchiveChatsSetting wire constant")
        void actionName() {
            assertEquals(UnarchiveChatsSetting.ACTION_NAME, new UnarchiveChatsSettingHandler().actionName());
            assertEquals("setting_unarchiveChats", new UnarchiveChatsSettingHandler().actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR_LOW")
        void collectionName() {
            assertEquals(UnarchiveChatsSetting.COLLECTION_NAME, new UnarchiveChatsSettingHandler().collectionName());
            assertEquals(SyncPatchType.REGULAR_LOW, new UnarchiveChatsSettingHandler().collectionName());
        }

        @Test
        @DisplayName("version() returns the declared UnarchiveChatsSetting version (4)")
        void version() {
            assertEquals(UnarchiveChatsSetting.ACTION_VERSION, new UnarchiveChatsSettingHandler().version());
            assertEquals(4, new UnarchiveChatsSettingHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("SET true updates the store flag and returns SUCCESS")
        void setsTrue() {
            assertFalse(client.store().unarchiveChats(), "precondition: setting starts false");
            var ts = Instant.ofEpochSecond(1_700_000_000L);

            var result = new UnarchiveChatsSettingHandler().applyMutation(client, unarchiveMutation(true, SyncdOperation.SET, ts));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(client.store().unarchiveChats());
        }

        @Test
        @DisplayName("SET false updates the store flag to false")
        void setsFalse() {
            client.store().setUnarchiveChats(true);
            var ts = Instant.ofEpochSecond(1_700_000_000L);

            var result = new UnarchiveChatsSettingHandler().applyMutation(client, unarchiveMutation(false, SyncdOperation.SET, ts));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(client.store().unarchiveChats());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("unarchive is a global setting; no per-entity orphan path")
        void noOrphan() {
            var result = new UnarchiveChatsSettingHandler().applyMutation(client, unarchiveMutation(true, SyncdOperation.SET, Instant.now()));
            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "WAWebArchiveSettingSync has no per-entity target");
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed action value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SyncActionValue carrying a different action returns MALFORMED")
        void wrongActionShapeIsMalformed() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .archiveChatAction(new ArchiveChatActionBuilder().archived(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("[\"setting_unarchiveChats\"]", value, SyncdOperation.SET, ts, 4);

            var result = new UnarchiveChatsSettingHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed action index")
    class MalformedActionIndex {
        @Test
        @DisplayName("the unarchive handler ignores the index shape (global setting)")
        void indexShapeIgnored() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .unarchiveChatsSetting(new UnarchiveChatsSettingBuilder().unarchiveChats(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("", value, SyncdOperation.SET, ts, 4);

            var result = new UnarchiveChatsSettingHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "the handler does not parse the index; the setting is keyed off the action only");
        }
    }

    @Nested
    @DisplayName("applyMutation â€” REMOVE returns UNSUPPORTED")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE is unsupported per the WA Web fall-through")
        void removeIsUnsupported() {
            var result = new UnarchiveChatsSettingHandler().applyMutation(client, unarchiveMutation(true, SyncdOperation.REMOVE, Instant.now()));
            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts â€” inherits default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote â†’ APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = unarchiveMutation(false, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = unarchiveMutation(true, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    new UnarchiveChatsSettingHandler().resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote â†’ SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = unarchiveMutation(false, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = unarchiveMutation(true, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    new UnarchiveChatsSettingHandler().resolveConflicts(local, remote).state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch â€” only the last mutation is applied")
    class ApplyBatchOverride {
        @Test
        @DisplayName("an empty batch yields an empty result list")
        void emptyBatchEmptyResult() {
            assertTrue(new UnarchiveChatsSettingHandler().applyMutationBatch(client, List.of()).isEmpty());
        }

        @Test
        @DisplayName("only the last mutation is applied; earlier ones are reported as SKIPPED")
        void onlyLastIsApplied() {
            // First mutation pushes true; second mutation pushes false; only the second should be applied.
            var results = new UnarchiveChatsSettingHandler().applyMutationBatch(client, List.of(
                    unarchiveMutation(true, SyncdOperation.SET, Instant.ofEpochSecond(1_000)),
                    unarchiveMutation(false, SyncdOperation.SET, Instant.ofEpochSecond(2_000))
            ));
            assertEquals(2, results.size());
            assertEquals(SyncActionState.SKIPPED, results.get(0).actionState(),
                    "WAWebArchiveSettingSync only applies the last mutation in the batch");
            assertEquals(SyncActionState.SUCCESS, results.get(1).actionState());
            assertFalse(client.store().unarchiveChats(),
                    "only the last mutation's value (false) is written to the store");
        }

        @Test
        @DisplayName("a single-mutation batch applies the only mutation")
        void singleMutationBatchApplies() {
            var results = new UnarchiveChatsSettingHandler().applyMutationBatch(client, List.of(
                    unarchiveMutation(true, SyncdOperation.SET, Instant.ofEpochSecond(1_000))
            ));
            assertEquals(1, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
            assertTrue(client.store().unarchiveChats());
        }
    }

    @Nested
    @DisplayName("static builder â€” getUnarchiveChatsMutation")
    class StaticBuilder {
        @Test
        @DisplayName("produces a SET mutation with the singleton index")
        void buildsPendingMutation() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var pending = new UnarchiveChatsSettingMutationFactory().getUnarchiveChatsMutation(ts, true);
            var inner = pending.mutation();

            assertEquals(SyncdOperation.SET, inner.operation());
            assertEquals(UnarchiveChatsSetting.ACTION_VERSION, inner.actionVersion());
            assertEquals("[\"setting_unarchiveChats\"]", inner.index());
            assertTrue(inner.value().action().filter(a -> a instanceof UnarchiveChatsSetting).map(a -> (UnarchiveChatsSetting) a).orElseThrow().unarchiveChats());
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle (gated)")
    class OracleParity {
        @Test
        @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when present")
        void byteEqualityWithOracle() {
            if (!SyncFixtures.isOracleAvailable("handler/unarchive-chats-setting/encode")) return;
            var oracle = SyncFixtures.loadOracle("handler/unarchive-chats-setting/encode");
            var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

            var pending = new UnarchiveChatsSettingMutationFactory().getUnarchiveChatsMutation(Instant.ofEpochSecond(1_700_000_000L), true);
            var actual = SyncActionValueSpec.encode(pending.mutation().value());

            assertNotNull(actual);
            assertArrayEquals(expected, actual);
        }
    }
}
