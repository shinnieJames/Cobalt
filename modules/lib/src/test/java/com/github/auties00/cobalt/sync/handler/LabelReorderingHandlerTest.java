package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.preference.LabelBuilder;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LabelReorderingAction;
import com.github.auties00.cobalt.model.sync.action.contact.LabelReorderingActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.LabelReorderingMutationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@link LabelReorderingHandler} adapter for
 * {@code WAWebLabelReorderingSync}.
 *
 * @apiNote
 * Verifies parity with WA Web for the {@code label_reordering}
 * app-state sync action across metadata, the SET sort-order write,
 * the REMOVE rejection, the empty-list rejection, the
 * malformed-input fallbacks, the inherited timestamp-based conflict
 * resolution and the
 * {@link LabelReorderingMutationFactory}
 * builder.
 *
 * @implNote
 * This implementation exercises the handler against an in-memory
 * {@link DeviceFixtures#temporaryStore} via {@link TestWhatsAppClient}
 * so each
 * {@link com.github.auties00.cobalt.model.preference.Label#orderIndex()}
 * read-back can be asserted directly. Labels referenced by the
 * action but absent from the store are silently skipped, matching
 * WA Web's
 * {@code WAWebDBLabelsReorder.updateLabelsSortOrder} bulk-get
 * behaviour.
 */
@DisplayName("LabelReorderingHandler")
class LabelReorderingHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final String INDEX = "[\"label_reordering\"]";

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private LabelReorderingHandler handler;
    private LabelReorderingMutationFactory factory;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new LabelReorderingHandler();
        factory = new LabelReorderingMutationFactory();
    }

    /**
     * Builds a {@link DecryptedMutation.Trusted} carrying the given
     * reordering action under the canonical
     * {@code ["label_reordering"]} index.
     *
     * @apiNote
     * Used by every test to centralise mutation construction. The
     * {@code action} parameter is nullable so the malformed-value
     * path can be exercised without re-implementing the envelope.
     *
     * @param action    the reordering action payload, may be
     *                  {@code null}
     * @param operation the {@link SyncdOperation} to wrap
     * @param ts        the mutation timestamp
     * @return a {@link DecryptedMutation.Trusted} with the requested
     *         shape
     */
    private DecryptedMutation.Trusted buildMutation(LabelReorderingAction action, SyncdOperation operation, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.labelReorderingAction(action);
        }
        return new DecryptedMutation.Trusted(INDEX, valueBuilder.build(), operation, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the LabelReorderingAction wire constant")
        void actionName() {
            assertEquals(LabelReorderingAction.ACTION_NAME, handler.actionName());
            assertEquals("label_reordering", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns the LabelReorderingAction collection")
        void collectionName() {
            assertEquals(LabelReorderingAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared LabelReorderingAction version")
        void version() {
            assertEquals(LabelReorderingAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - SET reorders the referenced labels")
    class ApplySet {
        @Test
        @DisplayName("each label's orderIndex matches its position in sortedLabelIds")
        void writesPositionsInOrder() {
            store.addLabel(new LabelBuilder().id("10").name("A").color(0).build());
            store.addLabel(new LabelBuilder().id("20").name("B").color(0).build());
            store.addLabel(new LabelBuilder().id("30").name("C").color(0).build());

            var action = new LabelReorderingActionBuilder()
                    .sortedLabelIds(List.of(30, 10, 20))
                    .build();
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = handler.applyMutation(client, buildMutation(action, SyncdOperation.SET, ts));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            // position 0 -> label 30; position 1 -> label 10; position 2 -> label 20
            assertEquals(0, store.findLabel("30").orElseThrow().orderIndex().orElseThrow());
            assertEquals(1, store.findLabel("10").orElseThrow().orderIndex().orElseThrow());
            assertEquals(2, store.findLabel("20").orElseThrow().orderIndex().orElseThrow());
        }

        @Test
        @DisplayName("labels in sortedLabelIds but missing from the store are silently skipped")
        void missingLabelsSkipped() {
            // Only label 10 is present; the action also references 20 and 30.
            store.addLabel(new LabelBuilder().id("10").name("A").color(0).build());
            var action = new LabelReorderingActionBuilder()
                    .sortedLabelIds(List.of(30, 10, 20))
                    .build();

            var result = handler.applyMutation(client, buildMutation(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(1, store.findLabel("10").orElseThrow().orderIndex().orElseThrow(),
                    "the lone present label is positioned at its zero-based index in the sortedLabelIds list");
            assertTrue(store.findLabel("20").isEmpty(), "missing labels are not implicitly created");
            assertTrue(store.findLabel("30").isEmpty(), "missing labels are not implicitly created");
        }

        @Test
        @DisplayName("labels present locally but absent from sortedLabelIds keep their existing orderIndex")
        void unreferencedLabelsKeepTheirOrder() {
            store.addLabel(new LabelBuilder().id("10").name("A").color(0).orderIndex(7).build());
            store.addLabel(new LabelBuilder().id("20").name("B").color(0).build());

            var action = new LabelReorderingActionBuilder()
                    .sortedLabelIds(List.of(20))
                    .build();
            var result = handler.applyMutation(client, buildMutation(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(0, store.findLabel("20").orElseThrow().orderIndex().orElseThrow());
            assertEquals(7, store.findLabel("10").orElseThrow().orderIndex().orElseThrow(),
                    "unreferenced labels retain their existing orderIndex");
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("missing labels do not produce an ORPHAN result - they are silently skipped to SUCCESS")
        void orphanDimensionNotApplicable() {
            // Reordering has no per-mutation target entity that can be marked orphan; missing
            // labels are silently skipped per WA Web's bulkGet semantics. We confirm here that
            // the absence of every label still produces SUCCESS, ruling out an orphan path.
            var action = new LabelReorderingActionBuilder()
                    .sortedLabelIds(List.of(99, 100))
                    .build();

            var result = handler.applyMutation(client, buildMutation(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(result.isOrphan(), "no orphan outcome is produced for missing labels");
            assertNull(result.modelId());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a value carrying the wrong action returns MALFORMED")
        void wrongActionType() {
            // A pinAction in place of the expected labelReorderingAction.
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(INDEX, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a labelReorderingAction with no sortedLabelIds returns MALFORMED")
        void emptySortedLabelIds() {
            // Per WAWebLabelReorderingSync.default.applyMutations, sortedLabelIds null/empty
            // is reported as malformed-action-value.
            var action = new LabelReorderingActionBuilder().build();

            var result = handler.applyMutation(client, buildMutation(action, SyncdOperation.SET, Instant.now()));
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("malformed index dimension is n/a - the index payload is unused")
        void indexUnused() {
            // LabelReorderingHandler never reads the index array (only the wire-name comes through
            // the dispatch in WebAppStateHandlerRegistry). Confirm that an empty or malformed
            // string at the index slot does not prevent a valid SET from succeeding.
            store.addLabel(new LabelBuilder().id("10").name("A").color(0).build());
            var action = new LabelReorderingActionBuilder().sortedLabelIds(List.of(10)).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelReorderingAction(action).build();

            for (var malformedIndex : new String[]{"", "not-json", "[", "[]"}) {
                var mutation = new DecryptedMutation.Trusted(malformedIndex, value, SyncdOperation.SET, ts, handler.version());
                assertEquals(SyncActionState.SUCCESS, handler.applyMutation(client, mutation).actionState(),
                        "handler must not read the index - malformed index '" + malformedIndex + "' is irrelevant");
            }
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE operation returns UNSUPPORTED")
        void removeUnsupported() {
            var action = new LabelReorderingActionBuilder().sortedLabelIds(List.of(10)).build();

            var result = handler.applyMutation(client, buildMutation(action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = mutationAt(Instant.ofEpochSecond(1_000));
            var remote = mutationAt(Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps -> APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTimestampApplies() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(mutationAt(ts), mutationAt(ts)).state());
        }

        @Test
        @DisplayName("older remote -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = mutationAt(Instant.ofEpochSecond(2_000));
            var remote = mutationAt(Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }

        private DecryptedMutation.Trusted mutationAt(Instant ts) {
            var action = new LabelReorderingActionBuilder().sortedLabelIds(List.of(10)).build();
            return buildMutation(action, SyncdOperation.SET, ts);
        }
    }

}
