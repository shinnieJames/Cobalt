package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.business.NoteStateBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditAction;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditAction.NoteType;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.NoteEditMutationFactory;
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
 * Tests for {@link NoteEditHandler} — Cobalt's adapter for
 * {@code WAWebNoteSync}.
 *
 * <p>The handler installs / edits / deletes chat-scoped notes via SET
 * mutations keyed by {@code indexParts[1]}. Deletions are encoded as SET
 * with {@code deleted=true}; create / edit requires a non-null type, a
 * non-null chat JID, and the referenced chat must exist locally. These
 * tests pin the wire metadata, the SET / deletion / orphan branches, the
 * malformed-input fallbacks, the default timestamp-based conflict
 * resolution, the static outbound-mutation builder, and the
 * {@code resolveNoteId} fallback semantics.
 */
@DisplayName("NoteEditHandler")
class NoteEditHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid CHAT_JID = Jid.of("12345@s.whatsapp.net");

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private NoteEditHandler handler;
    private NoteEditMutationFactory factory;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new NoteEditHandler();
        factory = new NoteEditMutationFactory();
    }

    /**
     * Builds a trusted mutation carrying the given note action.
     *
     * @param indexNoteId the note id placed in {@code indexParts[1]}, may be {@code null}
     * @param action      the note action payload, may be {@code null}
     * @param operation   the sync operation
     * @param ts          the mutation timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted buildMutation(String indexNoteId, NoteEditAction action,
                                                    SyncdOperation operation, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.noteEditAction(action);
        }
        var indexParts = indexNoteId == null ? List.of(handler.actionName()) : List.of(handler.actionName(), indexNoteId);
        var index = JSON.toJSONString(indexParts);
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), operation, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata — wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the wire constant")
        void actionName() {
            assertEquals(NoteEditAction.ACTION_NAME, handler.actionName());
            assertEquals("note_edit", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR_LOW")
        void collectionName() {
            assertEquals(NoteEditAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR_LOW, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version")
        void version() {
            assertEquals(NoteEditAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation — SET upsert (create / edit)")
    class ApplySetUpsert {
        @Test
        @DisplayName("a SET with type + chatJid + content installs the note state")
        void installsNote() {
            store.addNewChat(CHAT_JID);
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .createdAt(ts)
                    .deleted(false)
                    .unstructuredContent("remember to ask")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("note-1", action, SyncdOperation.SET, ts));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stored = store.findNoteState("note-1").orElseThrow();
            assertEquals("note-1", stored.id());
            assertEquals("remember to ask", stored.unstructuredContent().orElseThrow());
            assertEquals(NoteType.UNSTRUCTURED, stored.type().orElseThrow());
        }

        @Test
        @DisplayName("deleted=true on a SET drops the note from the store")
        void deletedTrueRemovesNote() {
            store.addNewChat(CHAT_JID);
            store.putNoteState(new NoteStateBuilder()
                    .id("note-rm")
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .createdAt(Instant.now())
                    .deleted(false)
                    .unstructuredContent("body")
                    .build());

            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .createdAt(Instant.now())
                    .deleted(true)
                    .unstructuredContent("")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("note-rm", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findNoteState("note-rm").isEmpty(),
                    "deleted=true must drop the stored note");
        }
    }

    @Nested
    @DisplayName("applyMutation — orphan when chat is unknown")
    class OrphanChat {
        @Test
        @DisplayName("a SET targeting an unknown chat returns ORPHAN with the chat JID")
        void orphanChat() {
            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .createdAt(Instant.now())
                    .deleted(false)
                    .unstructuredContent("body")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("note-orphan", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.ORPHAN, result.actionState());
            assertEquals(CHAT_JID.toString(), result.modelId());
            assertEquals("Chat", result.modelType());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a SET whose value carries the wrong action returns MALFORMED")
        void wrongActionType() {
            store.addNewChat(CHAT_JID);
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var index = JSON.toJSONString(List.of(handler.actionName(), "note-x"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a non-deletion action missing the type field returns MALFORMED")
        void missingType() {
            store.addNewChat(CHAT_JID);
            var action = new NoteEditActionBuilder()
                    .chatJid(CHAT_JID)
                    .createdAt(Instant.now())
                    .deleted(false)
                    .unstructuredContent("body")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("note-no-type", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a non-deletion action missing the chatJid returns MALFORMED")
        void missingChatJid() {
            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .createdAt(Instant.now())
                    .deleted(false)
                    .unstructuredContent("body")
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("note-no-jid", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("a missing note id slot returns MALFORMED")
        void missingNoteIdSlot() {
            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .deleted(false)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation(null, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("an empty note id returns MALFORMED")
        void emptyNoteId() {
            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .deleted(false)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — REMOVE")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE operation returns UNSUPPORTED")
        void removeUnsupported() {
            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .deleted(false)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("note-rm", action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts — default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote — APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = mutationAt(Instant.ofEpochSecond(1_000));
            var remote = mutationAt(Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote — SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = mutationAt(Instant.ofEpochSecond(2_000));
            var remote = mutationAt(Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }

        private DecryptedMutation.Trusted mutationAt(Instant ts) {
            var action = new NoteEditActionBuilder()
                    .type(NoteType.UNSTRUCTURED)
                    .chatJid(CHAT_JID)
                    .deleted(false)
                    .build();
            return buildMutation("note-tie", action, SyncdOperation.SET, ts);
        }
    }

    @Nested
    @DisplayName("static builder — getNoteEditMutation")
    class StaticBuilder {
        @Test
        @DisplayName("create / edit produces a SET pending mutation with the note body")
        void createCarriesContent() {
            var pending = factory.getNoteEditMutation("note-9", CHAT_JID, "body text", false);
            var inner = pending.mutation();

            assertEquals(SyncdOperation.SET, inner.operation());
            assertEquals(handler.version(), inner.actionVersion());
            assertEquals(JSON.toJSONString(List.of(handler.actionName(), "note-9")), inner.index());

            var roundtrip = inner.value().action().filter(a -> a instanceof NoteEditAction).map(a -> (NoteEditAction) a).orElseThrow();
            assertEquals("body text", roundtrip.unstructuredContent().orElseThrow());
            assertEquals(NoteType.UNSTRUCTURED, roundtrip.type().orElseThrow());
            assertEquals(CHAT_JID, roundtrip.chatJid().orElseThrow());
            assertTrue(!roundtrip.deleted(), "create / edit mutations must carry deleted=false");
        }

        @Test
        @DisplayName("deletion produces a SET pending mutation flagged as deleted")
        void deletionFlagsAsDeleted() {
            var pending = factory.getNoteEditMutation("note-del", CHAT_JID, null, true);
            var roundtrip = pending.mutation().value().action().filter(a -> a instanceof NoteEditAction).map(a -> (NoteEditAction) a).orElseThrow();
            assertTrue(roundtrip.deleted(), "deletion mutation must carry deleted=true");
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle (gated)")
    class OracleParity {
        @Test
        @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
        void byteEqualityWithOracle() {
            if (!SyncFixtures.isOracleAvailable("handler/note-edit/encode")) return;
            var oracle = SyncFixtures.loadOracle("handler/note-edit/encode");
            var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

            // Use the deterministic-Instant overload (package-private) so the
            // captured oracle's pinned timestamp matches our re-encoded bytes.
            var pending = factory.getNoteEditMutation(
                    "note-oracle", CHAT_JID, "body", false,
                    Instant.ofEpochSecond(oracle.getLong("timestampSeconds")));
            var actual = SyncActionValueSpec.encode(pending.mutation().value());

            assertNotNull(actual);
            assertArrayEquals(expected, actual);
        }
    }
}
