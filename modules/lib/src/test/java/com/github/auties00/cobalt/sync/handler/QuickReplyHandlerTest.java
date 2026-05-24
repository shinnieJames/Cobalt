package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.preference.QuickReplyBuilder;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.QuickReplyAction;
import com.github.auties00.cobalt.model.sync.action.chat.QuickReplyActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.QuickReplyMutationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link QuickReplyHandler} against the
 * {@code WAWebQuickRepliesSync.applyMutations} per-mutation flow.
 *
 * @apiNote
 * Verifies that the Cobalt handler matches WA Web's per-mutation
 * classification: a {@link SyncdOperation#SET}
 * with {@code deleted=true} drops the entry by id; a {@code SET}
 * with non-empty {@code shortcut} and {@code message} upserts a
 * {@link com.github.auties00.cobalt.model.preference.QuickReply}
 * keyed by {@code indexParts[1]}; a missing quick reply id surfaces
 * as {@link SyncActionState#MALFORMED};
 * a missing
 * {@link QuickReplyAction}
 * payload, empty {@code shortcut}, or empty {@code message} surface
 * as {@link SyncActionState#MALFORMED};
 * non-{@code SET} operations surface as
 * {@link SyncActionState#UNSUPPORTED};
 * the default {@code resolveConflicts} chooses the later timestamp.
 *
 * @implNote
 * This implementation drives both the handler and the
 * {@link QuickReplyMutationFactory}
 * directly so the static outbound-mutation builders can be checked
 * alongside the inbound apply path.
 */
@DisplayName("QuickReplyHandler")
class QuickReplyHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private QuickReplyHandler handler;
    private QuickReplyMutationFactory factory;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new QuickReplyHandler();
        factory = new QuickReplyMutationFactory();
    }

    /**
     * Builds a trusted mutation whose value carries the given
     * quick-reply action under the
     * {@code ["quick_reply", indexId]} index.
     *
     * @apiNote
     * Internal helper consumed by every test in this class; not used
     * outside it. Setting {@code indexId} to {@code null} produces
     * the singleton-index shape {@code ["quick_reply"]} so the
     * malformed-index branch can be exercised; setting
     * {@code action} to {@code null} omits the {@code quickReplyAction}
     * field on the value so the malformed-value branch can be
     * exercised.
     *
     * @implNote
     * This implementation builds the index via
     * {@link JSON#toJSONString(Object)} to
     * match the on-wire JSON encoding the production handler reads
     * back via {@link JSON#parseArray(String)}.
     *
     * @param indexId   the quick reply id placed in {@code indexParts[1]};
     *                  may be {@code null}
     * @param action    the quick reply action payload; may be {@code null}
     * @param operation the sync operation
     * @param ts        the mutation timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted buildMutation(String indexId, QuickReplyAction action, SyncdOperation operation, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.quickReplyAction(action);
        }
        var indexParts = indexId == null ? List.of(handler.actionName()) : List.of(handler.actionName(), indexId);
        var index = JSON.toJSONString(indexParts);
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), operation, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata — wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the QuickReplyAction wire constant")
        void actionName() {
            assertEquals(QuickReplyAction.ACTION_NAME, handler.actionName());
            assertEquals("quick_reply", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(QuickReplyAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared QuickReplyAction version")
        void version() {
            assertEquals(QuickReplyAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation — SET upsert (happy path)")
    class ApplySetUpsert {
        @Test
        @DisplayName("a non-deleted action with shortcut + message upserts the quick reply")
        void upsertsNewEntry() {
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/hi")
                    .message("Hi there!")
                    .keywords(List.of("greet"))
                    .count(5)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("qr-1", action, SyncdOperation.SET, Instant.ofEpochSecond(1_700_000_000L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stored = store.findQuickReply("qr-1").orElseThrow();
            assertEquals("qr-1", stored.id());
            assertEquals("/hi", stored.shortcut());
            assertEquals("Hi there!", stored.message());
            assertEquals(List.of("greet"), stored.keywords());
        }

        @Test
        @DisplayName("a SET on an existing id replaces the prior entry")
        void replacesExistingEntry() {
            store.addQuickReply(new QuickReplyBuilder()
                    .id("qr-2")
                    .shortcut("/old")
                    .message("old body")
                    .keywords(List.of())
                    .count(0)
                    .build());
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/new")
                    .message("new body")
                    .keywords(List.of("a", "b"))
                    .count(2)
                    .build();

            handler.applyMutation(client,
                    buildMutation("qr-2", action, SyncdOperation.SET, Instant.now()));

            var stored = store.findQuickReply("qr-2").orElseThrow();
            assertEquals("/new", stored.shortcut());
            assertEquals("new body", stored.message());
            assertEquals(List.of("a", "b"), stored.keywords());
        }

        @Test
        @DisplayName("deleted=true removes the entry from the store")
        void deletedRemovesEntry() {
            store.addQuickReply(new QuickReplyBuilder()
                    .id("qr-3")
                    .shortcut("/x")
                    .message("body")
                    .keywords(List.of())
                    .count(0)
                    .build());
            var action = new QuickReplyActionBuilder()
                    .deleted(true)
                    .shortcut("")
                    .message("")
                    .keywords(List.of())
                    .count(0)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("qr-3", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findQuickReply("qr-3").isEmpty(),
                    "deleted=true must remove the entry from the store");
        }
    }

    @Nested
    @DisplayName("applyMutation — orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("an upsert of an unknown id succeeds rather than producing an ORPHAN result")
        void unknownIdSucceeds() {
            // Per WAWebQuickRepliesSync.applyMutations, the handler does not validate that the
            // quick reply id exists prior to the upsert (the table call is createOrReplace).
            // SET for a brand-new id is the upsert path, not orphan.
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/u")
                    .message("body")
                    .keywords(List.of())
                    .count(0)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("unknown-id", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }

        @Test
        @DisplayName("a delete of an unknown id still succeeds — remove is idempotent")
        void deleteUnknownIdSucceeds() {
            var action = new QuickReplyActionBuilder()
                    .deleted(true)
                    .shortcut("")
                    .message("")
                    .keywords(List.of())
                    .count(0)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("never-existed", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a value carrying the wrong action returns MALFORMED")
        void wrongActionType() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var index = JSON.toJSONString(List.of(handler.actionName(), "qr-x"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a non-deleted action with a blank shortcut returns MALFORMED")
        void blankShortcutMalformed() {
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("")
                    .message("ok")
                    .keywords(List.of())
                    .count(0)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("qr-blank", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a non-deleted action with a blank message returns MALFORMED")
        void blankMessageMalformed() {
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/ok")
                    .message("")
                    .keywords(List.of())
                    .count(0)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("qr-blank-msg", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("a missing index slot returns MALFORMED")
        void missingIndexSlot() {
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/hi")
                    .message("hi")
                    .keywords(List.of())
                    .count(0)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation(null, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("an empty-string id at indexParts[1] returns MALFORMED")
        void emptyStringId() {
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/hi")
                    .message("hi")
                    .keywords(List.of())
                    .count(0)
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
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/x")
                    .message("body")
                    .keywords(List.of())
                    .count(0)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("qr-rm", action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts — default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote wins — APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = mutationAt(Instant.ofEpochSecond(1_000));
            var remote = mutationAt(Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps — APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTimestampApplies() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(mutationAt(ts), mutationAt(ts)).state());
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
            var action = new QuickReplyActionBuilder()
                    .deleted(false)
                    .shortcut("/hi")
                    .message("hi")
                    .keywords(List.of())
                    .count(0)
                    .build();
            return buildMutation("qr-tie", action, SyncdOperation.SET, ts);
        }
    }

    @Nested
    @DisplayName("static builder — getQuickReplyAddOrEditMutation")
    class AddOrEditBuilder {
        @Test
        @DisplayName("produces a SET pending mutation whose payload mirrors the inputs")
        void carriesInputs() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var pending = factory.getQuickReplyAddOrEditMutation(
                    "qr-9", "/hello", "Hi", 3, List.of("k1", "k2"), ts);
            var inner = pending.mutation();

            assertEquals(SyncdOperation.SET, inner.operation());
            assertEquals(handler.version(), inner.actionVersion());
            assertEquals(ts, inner.timestamp());
            assertEquals(JSON.toJSONString(List.of(handler.actionName(), "qr-9")), inner.index());

            var roundtrip = inner.value().action().filter(a -> a instanceof QuickReplyAction).map(a -> (QuickReplyAction) a).orElseThrow();
            assertEquals("/hello", roundtrip.shortcut().orElseThrow());
            assertEquals("Hi", roundtrip.message().orElseThrow());
            assertEquals(List.of("k1", "k2"), roundtrip.keywords());
            assertEquals(3, roundtrip.count().orElseThrow());
            assertTrue(!roundtrip.deleted(), "add/edit mutation must carry deleted=false");
        }
    }

    @Nested
    @DisplayName("static builder — getQuickReplyDeleteMutation")
    class DeleteBuilder {
        @Test
        @DisplayName("produces a SET pending mutation flagged as deleted")
        void carriesDeletedFlag() {
            var ts = Instant.ofEpochSecond(1_700_000_001L);
            var pending = factory.getQuickReplyDeleteMutation("qr-del", ts);
            var inner = pending.mutation();

            assertEquals(SyncdOperation.SET, inner.operation(),
                    "WA Web emits the deletion as a SET with deleted=true, not as REMOVE");
            assertEquals(ts, inner.timestamp());
            assertEquals(JSON.toJSONString(List.of(handler.actionName(), "qr-del")), inner.index());

            var roundtrip = inner.value().action().filter(a -> a instanceof QuickReplyAction).map(a -> (QuickReplyAction) a).orElseThrow();
            assertTrue(roundtrip.deleted(), "delete mutation must carry deleted=true");
        }
    }

}
