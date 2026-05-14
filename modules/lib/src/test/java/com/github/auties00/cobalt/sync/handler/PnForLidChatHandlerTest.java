package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.PnForLidChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.PnForLidChatActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link PnForLidChatHandler} — Cobalt's adapter for
 * {@code WAWebPnForLidChatSync}.
 *
 * <p>The handler records bidirectional {@code phoneJid <-> lidJid} mappings on
 * the store driven by SET mutations indexed by the LID side. Gating is the
 * {@code pnh_pn_for_lid_chat_sync} AB-prop; the action payload carries a
 * {@code pnJid}. These tests pin the wire metadata, AB-prop gating, SET happy
 * path, the non-LID and non-WID rejections, malformed fallbacks, the REMOVE
 * unsupported branch, and the default timestamp-based conflict resolution.
 */
@DisplayName("PnForLidChatHandler")
class PnForLidChatHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid CONTACT_LID = Jid.of("70000000000000@lid");
    private static final Jid CONTACT_PN = Jid.of("33330000@s.whatsapp.net");

    private WhatsAppStore store;
    private TestABPropsService props;
    private WhatsAppClient client;
    private PnForLidChatHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        // Default-on so happy-path tests don't have to repeat the .set(...) call.
        props = TestABPropsService.builder()
                .with(ABProp.PNH_PN_FOR_LID_CHAT_SYNC, true)
                .build();
        client = TestWhatsAppClient.create().withStore(store).withAbPropsService(props);
        handler = new PnForLidChatHandler(props);
    }

    /**
     * Builds a mutation whose value carries the given action under the
     * canonical {@code ["pnForLidChat", lidJid]} index.
     *
     * @param lidJid the LID JID
     * @param action the action payload, may be {@code null}
     * @param op     the sync operation
     * @param ts     the timestamp
     * @return the trusted mutation
     */
    private DecryptedMutation.Trusted build(Jid lidJid, PnForLidChatAction action, SyncdOperation op, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) valueBuilder.pnForLidChatAction(action);
        var index = JSON.toJSONString(List.of("pnForLidChat", lidJid.toString()));
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), op, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata — wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the PnForLidChatAction wire constant")
        void actionName() {
            assertEquals(PnForLidChatAction.ACTION_NAME, handler.actionName());
            assertEquals("pnForLidChat", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns the PnForLidChatAction collection")
        void collectionName() {
            assertEquals(PnForLidChatAction.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.REGULAR, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared PnForLidChatAction version")
        void version() {
            assertEquals(PnForLidChatAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("AB-prop gating — pnh_pn_for_lid_chat_sync")
    class AbPropGating {
        @Test
        @DisplayName("when the prop is off, the mutation returns UNSUPPORTED")
        void propOffReturnsUnsupported() {
            props.set(ABProp.PNH_PN_FOR_LID_CHAT_SYNC, false);
            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();

            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("registers the bidirectional phoneJid <-> lidJid mapping on the store")
        void registersMapping() {
            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();

            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(CONTACT_PN, store.getPhoneNumberByLid(CONTACT_LID).orElseThrow(),
                    "WAWebPnForLidChatSync.applyMutations must persist the LID -> PN mapping");
        }
    }

    @Nested
    @DisplayName("applyMutation — orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("the handler writes to the LID-mapping table directly — no orphan path")
        void noOrphanPath() {
            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();
            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

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
            var index = JSON.toJSONString(List.of("pnForLidChat", CONTACT_LID.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a pnForLidChatAction with no pnJid returns MALFORMED")
        void missingPnJid() {
            // Empty action — pnJid is unset.
            var action = new PnForLidChatActionBuilder().build();

            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("an empty lidJid slot returns MALFORMED")
        void emptyLidJid() {
            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).pnForLidChatAction(action).build();
            var index = JSON.toJSONString(List.of("pnForLidChat", ""));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a non-LID JID in the index returns MALFORMED")
        void nonLidJid() {
            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).pnForLidChatAction(action).build();
            // The index slot expects a @lid JID; a phone-number JID is rejected.
            var index = JSON.toJSONString(List.of("pnForLidChat", CONTACT_PN.toString()));
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
        }

        @Test
        @DisplayName("a short index missing the lidJid slot returns MALFORMED")
        void shortIndex() {
            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();
            var ts = Instant.now();
            var value = new SyncActionValueBuilder().timestamp(ts).pnForLidChatAction(action).build();
            var index = JSON.toJSONString(List.of("pnForLidChat"));
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
            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();

            var result = handler.applyMutation(client, build(CONTACT_LID, action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts — default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote → APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = build(CONTACT_LID, action(CONTACT_PN), SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = build(CONTACT_LID, action(CONTACT_PN), SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("equal timestamps → APPLY_REMOTE_DROP_LOCAL (remote wins on tie)")
        void equalTiesGoToRemote() {
            var ts = Instant.ofEpochSecond(1_500);
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(
                            build(CONTACT_LID, action(CONTACT_PN), SyncdOperation.SET, ts),
                            build(CONTACT_LID, action(CONTACT_PN), SyncdOperation.SET, ts)).state());
        }

        @Test
        @DisplayName("older remote → SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = build(CONTACT_LID, action(CONTACT_PN), SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = build(CONTACT_LID, action(CONTACT_PN), SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }

        private PnForLidChatAction action(Jid pnJid) {
            return new PnForLidChatActionBuilder().pnJid(pnJid).build();
        }
    }

    @Nested
    @DisplayName("static builder — none exposed")
    class StaticBuilder {
        @Test
        @DisplayName("PnForLidChatHandler exposes no static builder helpers — Cobalt does not emit pnForLidChat mutations")
        void noBuilder() {
            // The handler is read-only in Cobalt: WAWebPnForLidChatSync does not expose a
            // public outgoing-mutation builder either. This test pins the absence so a future
            // addition is intentional and reviewed.
            var methods = PnForLidChatHandler.class.getDeclaredMethods();
            var hasBuilder = false;
            for (var m : methods) {
                if (m.getName().toLowerCase().contains("mutation") && !m.getName().startsWith("apply")) {
                    hasBuilder = true;
                    break;
                }
            }
            assertFalse(hasBuilder, "no mutation-building helper is exposed on PnForLidChatHandler");
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle (gated)")
    class OracleParity {
        @Test
        @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
        void byteEqualityWithOracle() {
            if (!com.github.auties00.cobalt.sync.SyncFixtures.isOracleAvailable("handler/pn-for-lid-chat/encode")) return;
            var oracle = com.github.auties00.cobalt.sync.SyncFixtures.loadOracle("handler/pn-for-lid-chat/encode");
            var expected = com.github.auties00.cobalt.sync.SyncFixtures.decodeOracleBytes(oracle, "encoded");

            var action = new PnForLidChatActionBuilder().pnJid(CONTACT_PN).build();
            var value = new SyncActionValueBuilder()
                    .timestamp(Instant.ofEpochSecond(1_700_000_000L))
                    .pnForLidChatAction(action)
                    .build();
            var actual = com.github.auties00.cobalt.model.sync.SyncActionValueSpec.encode(value);

            org.junit.jupiter.api.Assertions.assertNotNull(actual);
            org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
        }
    }

}
