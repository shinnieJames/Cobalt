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
import com.github.auties00.cobalt.model.sync.action.contact.LabelAssociationAction;
import com.github.auties00.cobalt.model.sync.action.contact.LabelAssociationActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.LabelAssociationMutationFactory;
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
 * Tests for {@link LabelAssociationHandler} — Cobalt's adapter for
 * {@code WAWebLabelJidSync}.
 *
 * <p>The handler manages the membership of labels: a SET with
 * {@code labeled=true} adds a chat/contact JID to the label's assignment set,
 * while {@code labeled=false} removes it. Labels that do not yet exist are
 * created as stubs to hold the association. These tests pin the wire metadata,
 * the add/remove paths, label-stub creation, malformed input fallbacks, the
 * default timestamp-based conflict resolution, and the static builder helper.
 */
@DisplayName("LabelAssociationHandler")
class LabelAssociationHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid CHAT_JID = Jid.of("11110000@s.whatsapp.net");

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private LabelAssociationHandler handler;
    private LabelAssociationMutationFactory factory;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new LabelAssociationHandler();
        factory = new LabelAssociationMutationFactory();
    }

    /**
     * Builds a SET mutation with the canonical {@code ["label_jid", labelId, chatJid]} index.
     *
     * @param labelId   the label identifier
     * @param chatJid   the chat or contact JID
     * @param labeled   the labeled flag
     * @param ts        the timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted buildSet(String labelId, Jid chatJid, boolean labeled, Instant ts) {
        var action = new LabelAssociationActionBuilder().labeled(labeled).build();
        var value = new SyncActionValueBuilder().timestamp(ts).labelAssociationAction(action).build();
        var index = JSON.toJSONString(List.of("label_jid", labelId, chatJid.toString()));
        return new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata — wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the LabelAssociationAction wire constant")
        void actionName() {
            assertEquals(LabelAssociationAction.ACTION_NAME, handler.actionName());
            assertEquals("label_jid", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns the LabelAssociationAction collection")
        void collectionName() {
            assertEquals(LabelAssociationAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared LabelAssociationAction version")
        void version() {
            assertEquals(LabelAssociationAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation — SET adds and removes associations")
    class ApplySet {
        @Test
        @DisplayName("labeled=true on an existing label adds the chat JID to its assignment set")
        void addAssignment() {
            store.addLabel(new LabelBuilder().id("42").name("Customers").color(0).build());

            var result = handler.applyMutation(client, buildSet("42", CHAT_JID, true, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findLabel("42").orElseThrow().assignments().contains(CHAT_JID),
                    "labeled=true must add the chat JID to the label's assignment set");
        }

        @Test
        @DisplayName("labeled=false removes an existing assignment")
        void removeAssignment() {
            var label = new LabelBuilder().id("42").name("Customers").color(0).build();
            label.addAssignment(CHAT_JID);
            store.addLabel(label);

            var result = handler.applyMutation(client, buildSet("42", CHAT_JID, false, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(store.findLabel("42").orElseThrow().assignments().contains(CHAT_JID));
        }

        @Test
        @DisplayName("labeled=true on an unknown label creates a stub label to hold the association")
        void createsStubLabel() {
            assertTrue(store.findLabel("42").isEmpty(), "precondition: no label 42");

            var result = handler.applyMutation(client, buildSet("42", CHAT_JID, true, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stub = store.findLabel("42").orElseThrow();
            assertTrue(stub.assignments().contains(CHAT_JID),
                    "the stub created on-the-fly must record the association");
        }
    }

    @Nested
    @DisplayName("applyMutation — orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("an unknown label upserts to a stub rather than returning ORPHAN")
        void unknownLabelStubsRatherThanOrphans() {
            var result = handler.applyMutation(client, buildSet("does-not-exist", CHAT_JID, true, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertFalse(result.isOrphan());
            assertNull(result.modelId(),
                    "the handler never reports an orphan target — labels are auto-created as stubs");
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
            var index = JSON.toJSONString(List.of("label_jid", "42", CHAT_JID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a value with no labelAssociationAction returns MALFORMED")
        void missingActionPayload() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).build();
            var index = JSON.toJSONString(List.of("label_jid", "42", CHAT_JID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("an index with empty labelId returns MALFORMED")
        void emptyLabelId() {
            var action = new LabelAssociationActionBuilder().labeled(true).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelAssociationAction(action).build();
            var index = JSON.toJSONString(List.of("label_jid", "", CHAT_JID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("an index with empty chatJid returns MALFORMED")
        void emptyChatJid() {
            var action = new LabelAssociationActionBuilder().labeled(true).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelAssociationAction(action).build();
            var index = JSON.toJSONString(List.of("label_jid", "42", ""));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("an index missing the chatJid slot returns MALFORMED")
        void shortIndex() {
            var action = new LabelAssociationActionBuilder().labeled(true).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelAssociationAction(action).build();
            var index = JSON.toJSONString(List.of("label_jid", "42"));
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
            var action = new LabelAssociationActionBuilder().labeled(true).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).labelAssociationAction(action).build();
            var index = JSON.toJSONString(List.of("label_jid", "42", CHAT_JID.toString()));
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
            var local = buildSet("42", CHAT_JID, true, Instant.ofEpochSecond(1_000));
            var remote = buildSet("42", CHAT_JID, false, Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps → APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTiesGoToRemote() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(buildSet("42", CHAT_JID, true, ts), buildSet("42", CHAT_JID, false, ts)).state());
        }

        @Test
        @DisplayName("older remote → SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = buildSet("42", CHAT_JID, true, Instant.ofEpochSecond(2_000));
            var remote = buildSet("42", CHAT_JID, false, Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }
    }

    @Nested
    @DisplayName("static builder — createLabelAssociationMutation")
    class StaticBuilder {
        @Test
        @DisplayName("produces a SET pending mutation carrying the canonical three-element index")
        void indexShape() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var pending = factory.createLabelAssociationMutation("42", CHAT_JID, true, ts);
            var inner = pending.mutation();

            assertEquals(SyncdOperation.SET, inner.operation());
            assertEquals(handler.version(), inner.actionVersion());
            assertEquals(ts, inner.timestamp());
            assertEquals(JSON.toJSONString(List.of("label_jid", "42", CHAT_JID.toString())), inner.index());

            var action = inner.value().action().filter(a -> a instanceof LabelAssociationAction).map(a -> (LabelAssociationAction) a).orElseThrow();
            assertTrue(action.labeled(),
                    "labeled flag is preserved verbatim — true means add the association");
        }

        @Test
        @DisplayName("labeled=false is preserved on the produced action")
        void labeledFalseRoundtrips() {
            var pending = factory.createLabelAssociationMutation("42", CHAT_JID, false, Instant.now());
            assertFalse(pending.mutation().value().action().filter(a -> a instanceof LabelAssociationAction).map(a -> (LabelAssociationAction) a).orElseThrow().labeled());
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle (gated)")
    class OracleParity {
        @Test
        @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
        void byteEqualityWithOracle() {
            if (!SyncFixtures.isOracleAvailable("handler/label-association/encode")) return;
            var oracle = SyncFixtures.loadOracle("handler/label-association/encode");
            var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

            var pending = factory.createLabelAssociationMutation(
                    "42", CHAT_JID, true, Instant.ofEpochSecond(1_700_000_000L));
            var actual = SyncActionValueSpec.encode(pending.mutation().value());

            assertNotNull(actual);
            assertArrayEquals(expected, actual);
        }
    }
}
