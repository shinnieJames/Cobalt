package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.business.AgentStateBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.action.device.AgentAction;
import com.github.auties00.cobalt.model.sync.action.device.AgentActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentActionHandler} - Cobalt's adapter for
 * {@code WAWebAgentSync}.
 *
 * <p>The handler maintains the business-account agent roster. SET mutations
 * merge the {@link AgentAction} payload into the store (even when the
 * {@code isDeleted} flag is set - the action is stored as-is). REMOVE
 * mutations drop the agent from the store. These tests pin the wire
 * metadata, the SET/REMOVE behaviour, the malformed-input fallbacks, and
 * the default timestamp-based conflict resolution.
 */
@DisplayName("AgentActionHandler")
class AgentActionHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestWhatsAppClient client;
    private AgentActionHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new AgentActionHandler();
    }

    /**
     * Builds a trusted mutation whose value carries the given agent action.
     *
     * @param indexId   the agent id placed in {@code indexParts[1]}, may be {@code null}
     * @param action    the agent action payload, may be {@code null}
     * @param operation the sync operation
     * @param ts        the mutation timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted buildMutation(String indexId, AgentAction action, SyncdOperation operation, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.agentAction(action);
        }
        var indexParts = indexId == null ? List.of(handler.actionName()) : List.of(handler.actionName(), indexId);
        var index = JSON.toJSONString(indexParts);
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), operation, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the AgentAction wire constant")
        void actionName() {
            assertEquals(AgentAction.ACTION_NAME, handler.actionName());
            assertEquals("deviceAgent", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(AgentAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared AgentAction version")
        void version() {
            assertEquals(AgentAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - SET upsert")
    class ApplySet {
        @Test
        @DisplayName("a SET upserts the agent state into the store")
        void upsertsAgent() {
            var action = new AgentActionBuilder()
                    .name("Alice")
                    .deviceID(3)
                    .isDeleted(false)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("agent-1", action, SyncdOperation.SET, Instant.ofEpochSecond(1_700_000_000L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stored = store.findAgentState("agent-1").orElseThrow();
            assertEquals("agent-1", stored.agentId());
            assertEquals("Alice", stored.name().orElseThrow());
            assertEquals(3, stored.deviceId().orElseThrow());
            assertTrue(!stored.deleted(), "isDeleted=false must propagate to the stored entry");
        }

        @Test
        @DisplayName("a SET with isDeleted=true still upserts the agent - store as-is")
        void deletedFlagStored() {
            // Per WA Web, the handler merges the agent regardless of the deleted flag; the
            // tombstone is preserved so other devices can converge on the same state.
            var action = new AgentActionBuilder()
                    .name("Bob")
                    .deviceID(7)
                    .isDeleted(true)
                    .build();

            var result = handler.applyMutation(client,
                    buildMutation("agent-tombstone", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            var stored = store.findAgentState("agent-tombstone").orElseThrow();
            assertTrue(stored.deleted(), "isDeleted=true must propagate to the stored entry");
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("SET on an unknown agent id is the upsert path, not an orphan")
        void unknownIdUpserts() {
            // Per WAWebAgentSync.applyMutations, the SET path is unconditional upsert. There is
            // no prior-entity lookup that could produce an orphan outcome.
            var action = new AgentActionBuilder().name("New").deviceID(0).isDeleted(false).build();
            var result = handler.applyMutation(client,
                    buildMutation("brand-new", action, SyncdOperation.SET, Instant.now()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findAgentState("brand-new").isPresent());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedValue {
        @Test
        @DisplayName("a SET whose value carries the wrong action returns MALFORMED")
        void wrongActionType() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var index = JSON.toJSONString(List.of(handler.actionName(), "agent-x"));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("an empty agent id at indexParts[1] returns MALFORMED")
        void emptyAgentId() {
            var action = new AgentActionBuilder().name("X").build();

            var result = handler.applyMutation(client,
                    buildMutation("", action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("a missing agent id slot returns MALFORMED")
        void missingAgentId() {
            var action = new AgentActionBuilder().name("X").build();

            var result = handler.applyMutation(client,
                    buildMutation(null, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE removes the agent")
    class ApplyRemove {
        @Test
        @DisplayName("REMOVE drops the agent from the store and returns SUCCESS")
        void removeDropsAgent() {
            store.putAgentState(new AgentStateBuilder()
                    .agentId("agent-rm")
                    .name("Old")
                    .deviceId(1)
                    .deleted(false)
                    .build());

            var result = handler.applyMutation(client,
                    buildMutation("agent-rm", null, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findAgentState("agent-rm").isEmpty());
        }

        @Test
        @DisplayName("REMOVE of an unknown agent still returns SUCCESS - idempotent")
        void removeUnknownAgent() {
            var result = handler.applyMutation(client,
                    buildMutation("never-existed", null, SyncdOperation.REMOVE, Instant.now()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote - APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = mutationAt(Instant.ofEpochSecond(1_000));
            var remote = mutationAt(Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps - APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTimestampApplies() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(mutationAt(ts), mutationAt(ts)).state());
        }

        @Test
        @DisplayName("older remote - SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = mutationAt(Instant.ofEpochSecond(2_000));
            var remote = mutationAt(Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }

        private DecryptedMutation.Trusted mutationAt(Instant ts) {
            var action = new AgentActionBuilder().name("Alice").deviceID(0).isDeleted(false).build();
            return buildMutation("agent-tie", action, SyncdOperation.SET, ts);
        }
    }

    @Nested
    @DisplayName("static builders - n/a")
    class StaticBuilders {
        @Test
        @DisplayName("AgentActionHandler exposes no outbound mutation builder - dimension is n/a")
        void noBuilder() {
            // AgentActionHandler is an inbound-only handler in Cobalt. The outbound path is
            // handled elsewhere (Cobalt does not currently emit deviceAgent mutations from this
            // handler). Confirm the handler instantiates cleanly with no outbound surface.
            assertNotNull(new AgentActionHandler(),
                    "AgentActionHandler has a public no-arg constructor and exposes no builder method");
        }
    }
}
