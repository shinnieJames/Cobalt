package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.preference.LabelBuilder;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditAction;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.LabelEditMutationFactory;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LabelEditHandler} — Cobalt's adapter for
 * {@code WAWebLabelSync}.
 *
 * <p>The handler creates, edits, and deletes chat labels driven by SET mutations
 * keyed on {@code labelId}. These tests pin the wire metadata, the
 * insert/merge/delete branches, the {@code SERVER_ASSIGNED} short-circuit,
 * malformed input fallbacks, the default timestamp-based conflict resolution,
 * and the static builder helper.
 */
@DisplayName("LabelEditHandler")
class LabelEditHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private LabelEditHandler handler;
    private LabelEditMutationFactory factory;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new LabelEditHandler();
        factory = new LabelEditMutationFactory();
    }

    /**
     * Builds a SET mutation whose value carries the given label-edit action under the
     * canonical {@code ["label_edit", labelId]} index.
     *
     * @param labelId the label identifier
     * @param action  the action payload, may be {@code null} to omit it
     * @param ts      the mutation timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted buildSet(String labelId, LabelEditAction action, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) valueBuilder.labelEditAction(action);
        var index = JSON.toJSONString(List.of("label_edit", labelId));
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), SyncdOperation.SET, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata — wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the LabelEditAction wire constant")
        void actionName() {
            assertEquals(LabelEditAction.ACTION_NAME, handler.actionName());
            assertEquals("label_edit", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns the LabelEditAction collection")
        void collectionName() {
            assertEquals(LabelEditAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared LabelEditAction version")
        void version() {
            assertEquals(LabelEditAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation — happy SET (insert + merge)")
    class ApplySetHappy {
        @Test
        @DisplayName("inserts a new label when none exists with the given id")
        void insertsNewLabel() {
            var action = new LabelEditActionBuilder()
                    .name("Customers")
                    .color(5)
                    .predefinedId(0)
                    .isActive(true)
                    .build();

            var result = handler.applyMutation(client, buildSet("42", action, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var label = store.findLabel("42").orElseThrow();
            assertEquals("Customers", label.name());
            assertEquals(5, label.color());
            assertEquals(0, label.predefinedId().orElseThrow());
            assertEquals(Boolean.TRUE, label.isActive().orElseThrow(),
                    "isActive=true must be persisted on the inserted label");
        }

        @Test
        @DisplayName("merges into an existing label, preserving fields not present in the action")
        void mergesIntoExisting() {
            // Pre-existing label carries assignments; the handler must NOT clear them on a name update.
            var existing = new LabelBuilder()
                    .id("42")
                    .name("Old")
                    .color(1)
                    .build();
            existing.addAssignment(Jid.of("11110000@s.whatsapp.net"));
            store.addLabel(existing);

            var action = new LabelEditActionBuilder()
                    .name("Renamed")
                    .color(7)
                    .build();
            var result = handler.applyMutation(client, buildSet("42", action, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var merged = store.findLabel("42").orElseThrow();
            assertEquals("Renamed", merged.name());
            assertEquals(7, merged.color());
            assertEquals(1, merged.assignments().size(),
                    "assignments must be preserved across a merge — they are not part of the action payload");
        }

        @Test
        @DisplayName("deleted=true removes the label from the store")
        void deletedRemoves() {
            store.addLabel(new LabelBuilder().id("42").name("X").color(0).build());
            assertTrue(store.findLabel("42").isPresent());

            var action = new LabelEditActionBuilder()
                    .deleted(true)
                    .build();
            var result = handler.applyMutation(client, buildSet("42", action, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findLabel("42").isEmpty(), "the label must be removed on deleted=true");
        }

        @Test
        @DisplayName("deleted=true on an unknown label still returns SUCCESS")
        void deletedOnUnknownStillSucceeds() {
            var action = new LabelEditActionBuilder().deleted(true).build();

            var result = handler.applyMutation(client, buildSet("not-there", action, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }

        @Test
        @DisplayName("SERVER_ASSIGNED labels are not added to the main collection")
        void serverAssignedNotAdded() {
            var action = new LabelEditActionBuilder()
                    .name("Detected: New Order")
                    .color(0)
                    .type(LabelEditAction.ListType.SERVER_ASSIGNED)
                    .predefinedId(1)
                    .build();

            var result = handler.applyMutation(client, buildSet("99", action, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findLabel("99").isEmpty(),
                    "SERVER_ASSIGNED labels are filtered out of the main label collection by WA Web");
        }
    }

    @Nested
    @DisplayName("applyMutation — orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("a label can be created without a pre-existing record — no orphan path")
        void noOrphanPath() {
            // The handler upserts: there is no parent entity that can be missing. We confirm
            // the absence of an ORPHAN outcome by exercising the insert path against an empty store.
            var action = new LabelEditActionBuilder().name("Fresh").color(0).build();
            var result = handler.applyMutation(client, buildSet("new", action, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(result.isOrphan());
            assertNull(result.modelId());
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
            var index = JSON.toJSONString(List.of("label_edit", "42"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a value with no labelEditAction returns MALFORMED")
        void missingActionPayload() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var index = JSON.toJSONString(List.of("label_edit", "42"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("an index whose labelId slot is empty returns MALFORMED")
        void emptyLabelId() {
            var action = new LabelEditActionBuilder().name("X").color(0).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelEditAction(action).build();
            var index = JSON.toJSONString(List.of("label_edit", ""));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("an index missing the labelId slot returns MALFORMED")
        void shortIndex() {
            var action = new LabelEditActionBuilder().name("X").color(0).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelEditAction(action).build();
            var index = JSON.toJSONString(List.of("label_edit"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — REMOVE")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE operation returns UNSUPPORTED")
        void removeUnsupported() {
            var action = new LabelEditActionBuilder().name("X").color(0).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelEditAction(action).build();
            var index = JSON.toJSONString(List.of("label_edit", "42"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.REMOVE, ts, handler.version());

            assertEquals(SyncActionState.UNSUPPORTED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts — default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote → APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = buildSet("42", action("A"), Instant.ofEpochSecond(1_000));
            var remote = buildSet("42", action("B"), Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps → APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTiesGoToRemote() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(buildSet("42", action("A"), ts), buildSet("42", action("B"), ts)).state());
        }

        @Test
        @DisplayName("older remote → SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = buildSet("42", action("A"), Instant.ofEpochSecond(2_000));
            var remote = buildSet("42", action("B"), Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }

        private LabelEditAction action(String name) {
            return new LabelEditActionBuilder().name(name).color(0).build();
        }
    }

    @Nested
    @DisplayName("static builder — getLabelMutation")
    class StaticBuilder {
        @Test
        @DisplayName("produces a SET pending mutation with the canonical [\"label_edit\", labelId] index")
        void indexShape() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var pending = factory.getLabelMutation(
                    "42", "Customers", 5, false, 0, true, LabelEditAction.ListType.CUSTOM, ts);
            var inner = pending.mutation();

            assertEquals(SyncdOperation.SET, inner.operation());
            assertEquals(handler.version(), inner.actionVersion());
            assertEquals(ts, inner.timestamp());
            assertEquals(JSON.toJSONString(List.of("label_edit", "42")), inner.index());

            var action = inner.value().action().filter(a -> a instanceof LabelEditAction).map(a -> (LabelEditAction) a).orElseThrow();
            assertEquals("Customers", action.name().orElseThrow());
            assertEquals(5, action.color().orElseThrow());
            assertEquals(0, action.predefinedId().orElseThrow());
            assertFalse(action.deleted());
            assertTrue(action.isActive());
            assertEquals(LabelEditAction.ListType.CUSTOM, action.type().orElseThrow());
        }

        @Test
        @DisplayName("deleted=true is preserved on the produced action")
        void deletedFlagRoundtrips() {
            var ts = Instant.now();
            var pending = factory.getLabelMutation(
                    "42", null, null, true, null, null, null, ts);
            var action = pending.mutation().value().action().filter(a -> a instanceof LabelEditAction).map(a -> (LabelEditAction) a).orElseThrow();
            assertTrue(action.deleted());
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle (gated)")
    class OracleParity {
        @Test
        @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
        void byteEqualityWithOracle() {
            if (!SyncFixtures.isOracleAvailable("handler/label-edit/encode")) return;
            var oracle = SyncFixtures.loadOracle("handler/label-edit/encode");
            var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

            var pending = factory.getLabelMutation(
                    "42", "Customers", 5, false, 0, true,
                    LabelEditAction.ListType.CUSTOM, Instant.ofEpochSecond(1_700_000_000L));
            var actual = SyncActionValueSpec.encode(pending.mutation().value());

            assertNotNull(actual);
            assertArrayEquals(expected, actual);
        }
    }
}
