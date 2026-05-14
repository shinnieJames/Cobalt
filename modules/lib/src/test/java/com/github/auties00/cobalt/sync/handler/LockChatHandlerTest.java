package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.LockChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.LockChatActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinAction;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.ArchiveChatMutationFactory;
import com.github.auties00.cobalt.sync.factory.LockChatMutationFactory;
import com.github.auties00.cobalt.sync.factory.PinChatMutationFactory;
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
 * Tests for {@link LockChatHandler}.
 */
@DisplayName("LockChatHandler")
class LockChatHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid PEER = Jid.of("1234567890@s.whatsapp.net");

    private TestWhatsAppClient client;

    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    private static DecryptedMutation.Trusted lockMutation(boolean locked, Jid jid, Instant ts) {
        var value = new SyncActionValueBuilder()
                .timestamp(ts)
                .lockChatAction(new LockChatActionBuilder().locked(locked).build())
                .build();
        var index = JSON.toJSONString(List.of("lock", jid.toString()));
        return new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, 7);
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {
        @Test
        @DisplayName("actionName returns \"lock\"")
        void actionName() {
            assertEquals("lock", new LockChatHandler().actionName());
            assertEquals(LockChatAction.ACTION_NAME, new LockChatHandler().actionName());
        }

        @Test
        @DisplayName("collectionName resolves to REGULAR_LOW")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR_LOW, new LockChatHandler().collectionName());
        }

        @Test
        @DisplayName("version returns the declared action version 7")
        void version() {
            assertEquals(7, new LockChatHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation SET â€” happy path")
    class HappySet {
        @Test
        @DisplayName("locked=true also unarchives and unpins the chat (mutual exclusion)")
        void lockingUnarchivesAndUnpins() {
            var chat = client.store().addNewChat(PEER);
            chat.setArchived(true);
            chat.setPinnedTimestamp(Instant.ofEpochSecond(123L));

            var result = new LockChatHandler().applyMutation(client,
                    lockMutation(true, PEER, Instant.ofEpochSecond(1L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(chat.locked());
            assertFalse(chat.archived(), "locking must also unarchive the chat");
            assertTrue(chat.pinnedTimestamp().isEmpty(), "locking must also unpin the chat");
        }

        @Test
        @DisplayName("locked=false only flips the lock flag (does not touch archive/pin)")
        void unlockingLeavesOthersAlone() {
            var chat = client.store().addNewChat(PEER);
            chat.setLocked(true);
            chat.setArchived(true);
            var pinTs = Instant.ofEpochSecond(123L);
            chat.setPinnedTimestamp(pinTs);

            var result = new LockChatHandler().applyMutation(client,
                    lockMutation(false, PEER, Instant.ofEpochSecond(2L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(chat.locked());
            assertTrue(chat.archived(), "unlock must not touch the archive flag");
            assertEquals(pinTs, chat.pinnedTimestamp().orElseThrow(),
                    "unlock must not touch the pinned timestamp");
        }
    }

    @Nested
    @DisplayName("applyMutation â€” orphan")
    class Orphan {
        @Test
        @DisplayName("SET against an unknown chat JID returns ORPHAN with modelType=Chat")
        void orphanReturnsChatModel() {
            var result = new LockChatHandler().applyMutation(client,
                    lockMutation(true, PEER, Instant.ofEpochSecond(1L)));

            assertEquals(SyncActionState.ORPHAN, result.actionState());
            assertEquals(PEER.toString(), result.modelId());
            assertEquals("Chat", result.modelType());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a SyncActionValue carrying a pinAction instead of lockChatAction is MALFORMED")
        void wrongActionTypeIsMalformed() {
            client.store().addNewChat(PEER);
            var wrong = new SyncActionValueBuilder()
                    .timestamp(Instant.ofEpochSecond(1L))
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    JSON.toJSONString(List.of("lock", PEER.toString())),
                    wrong, SyncdOperation.SET, Instant.ofEpochSecond(1L), 7);

            var result = new LockChatHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("an empty chat JID at slot 1 is MALFORMED")
        void emptyChatJidIsMalformed() {
            client.store().addNewChat(PEER);
            var value = new SyncActionValueBuilder()
                    .timestamp(Instant.ofEpochSecond(1L))
                    .lockChatAction(new LockChatActionBuilder().locked(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"lock\",\"\"]",
                    value, SyncdOperation.SET, Instant.ofEpochSecond(1L), 7);

            var result = new LockChatHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” REMOVE")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE returns UNSUPPORTED")
        void removeReturnsUnsupported() {
            client.store().addNewChat(PEER);
            var mutation = new DecryptedMutation.Trusted(
                    JSON.toJSONString(List.of("lock", PEER.toString())),
                    new SyncActionValueBuilder()
                            .timestamp(Instant.ofEpochSecond(1L))
                            .lockChatAction(new LockChatActionBuilder().locked(true).build())
                            .build(),
                    SyncdOperation.REMOVE, Instant.ofEpochSecond(1L), 7);

            var result = new LockChatHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts â€” default timestamp-based behaviour")
    class ResolveConflicts {
        // LockChatHandler does NOT override resolveConflicts; the interface default applies.
        @Test
        @DisplayName("older local vs. newer remote â†’ APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteWins() {
            var local = lockMutation(true, PEER, Instant.ofEpochSecond(100L));
            var remote = lockMutation(false, PEER, Instant.ofEpochSecond(200L));

            var resolution = new LockChatHandler().resolveConflicts(local, remote);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL, resolution.state());
        }

        @Test
        @DisplayName("newer local vs. older remote â†’ SKIP_REMOTE")
        void newerLocalWins() {
            var local = lockMutation(true, PEER, Instant.ofEpochSecond(300L));
            var remote = lockMutation(false, PEER, Instant.ofEpochSecond(200L));

            var resolution = new LockChatHandler().resolveConflicts(local, remote);
            assertEquals(ConflictResolutionState.SKIP_REMOTE, resolution.state());
        }
    }

    @Nested
    @DisplayName("getChatLockMutation / getMutationsForLock â€” builder helpers")
    class BuilderHelpers {
        @Test
        @DisplayName("getChatLockMutation carries the locked flag and the [\"lock\", jid] index")
        void chatLockMutationStructure() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var pinChatMutationFactory = new PinChatMutationFactory();
            var pending = new LockChatMutationFactory(new ArchiveChatMutationFactory(pinChatMutationFactory), pinChatMutationFactory).getChatLockMutation(ts, true, PEER);

            var trusted = pending.mutation();
            assertEquals(SyncdOperation.SET, trusted.operation());
            assertEquals(7, trusted.actionVersion());
            assertEquals(JSON.toJSONString(List.of("lock", PEER.toString())), trusted.index());
            assertTrue(trusted.value().action().filter(a -> a instanceof LockChatAction).map(a -> (LockChatAction) a).orElseThrow().locked());
        }

        @Test
        @DisplayName("getMutationsForLock when locking emits (unarchive, unpin, lock) in that order")
        void lockingEmitsThreeMutations() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var pinChatMutationFactory = new PinChatMutationFactory();
            var mutations = new LockChatMutationFactory(new ArchiveChatMutationFactory(pinChatMutationFactory), pinChatMutationFactory).getMutationsForLock(ts, true, PEER, null);

            assertEquals(3, mutations.size(),
                    "locking emits the archive(false) + pin(false) + lock(true) triple");
            assertFalse(mutations.get(0).mutation().value().action().filter(a -> a instanceof ArchiveChatAction).map(a -> (ArchiveChatAction) a).orElseThrow().archived());
            assertFalse(mutations.get(1).mutation().value().action().filter(a -> a instanceof PinAction).map(a -> (PinAction) a).orElseThrow().pinned());
            assertTrue(mutations.get(2).mutation().value().action().filter(a -> a instanceof LockChatAction).map(a -> (LockChatAction) a).orElseThrow().locked());
        }

        @Test
        @DisplayName("getMutationsForLock when unlocking emits only the lock mutation")
        void unlockingEmitsOnlyLock() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var pinChatMutationFactory = new PinChatMutationFactory();
            var mutations = new LockChatMutationFactory(new ArchiveChatMutationFactory(pinChatMutationFactory), pinChatMutationFactory).getMutationsForLock(ts, false, PEER, null);

            assertEquals(1, mutations.size(), "unlocking only emits the lock(false) mutation");
            assertFalse(mutations.get(0).mutation().value().action().filter(a -> a instanceof LockChatAction).map(a -> (LockChatAction) a).orElseThrow().locked());
        }
    }

    @Nested
    @DisplayName("WA Web oracle parity (gated)")
    class OracleParity {
        @Test
        @DisplayName("captured SyncActionValue bytes match Cobalt's encode output when the oracle is present")
        void byteParityWithOracle() {
            if (!SyncFixtures.isOracleAvailable("handler/lock-chat/encode")) return;
            var oracle = SyncFixtures.loadOracle("handler/lock-chat/encode");
            var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");
            var locked = oracle.getBoolean("locked");

            var action = new LockChatActionBuilder().locked(locked).build();
            var value = new SyncActionValueBuilder()
                    .timestamp(Instant.ofEpochSecond(oracle.getLong("timestampSeconds")))
                    .lockChatAction(action)
                    .build();
            assertNotNull(expected);
            assertArrayEquals(expected, SyncActionValueSpec.encode(value));
        }
    }
}
