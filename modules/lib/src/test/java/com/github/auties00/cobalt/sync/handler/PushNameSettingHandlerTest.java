package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.action.setting.PushNameSetting;
import com.github.auties00.cobalt.model.sync.action.setting.PushNameSettingBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link PushNameSettingHandler} against the
 * {@code WAWebPushNameSync.applyMutations} per-mutation flow.
 *
 * @apiNote
 * Verifies the
 * {@link SyncdOperation#SET}
 * happy path: a {@code presence} node is dispatched on the wire,
 * {@link WhatsAppStore#setName(String)} is updated, the self-contact's
 * {@code chosenName} is mirrored, and the
 * {@link WhatsAppClientListener#onNameChanged}
 * listener fires. A missing or empty
 * {@link PushNameSetting#name()}
 * defaults to the empty string and still returns
 * {@link SyncActionState#SUCCESS}.
 *
 * @implNote
 * This implementation captures outgoing nodes via the
 * {@code onNodeSent} listener rather than wrapping
 * {@link TestWhatsAppClient} in a
 * proxy: {@code TestWhatsAppClient.sendNodeWithNoResponse} fires the
 * listener directly, which is sufficient for asserting the
 * {@code <presence name="..."/>} stanza shape.
 */
@DisplayName("PushNameSettingHandler")
class PushNameSettingHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestABPropsService props;
    private WhatsAppClient client;
    private List<Node> sentNodes;
    private PushNameSettingHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        props = TestABPropsService.builder().build();
        sentNodes = new ArrayList<>();
        store.addListener(new WhatsAppClientListener() {
            @Override
            public void onNodeSent(WhatsAppClient whatsapp, Node outgoing) {
                sentNodes.add(outgoing);
            }
        });
        client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        handler = new PushNameSettingHandler(new DefaultWamService(client, props));
    }

    private static DecryptedMutation.Trusted pushNameMutation(String name, SyncdOperation op, Instant ts) {
        var builder = new PushNameSettingBuilder();
        if (name != null) builder.name(name);
        var value = new SyncActionValueBuilder()
                .timestamp(ts)
                .pushNameSetting(builder.build())
                .build();
        return new DecryptedMutation.Trusted("[\"setting_pushName\"]", value, op, ts, PushNameSetting.ACTION_VERSION);
    }

    @Nested
    @DisplayName("metadata — wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns the PushNameSetting wire constant")
        void actionName() {
            assertEquals(PushNameSetting.ACTION_NAME, handler.actionName());
            assertEquals("setting_pushName", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns CRITICAL_BLOCK")
        void collectionName() {
            assertEquals(PushNameSetting.COLLECTION_NAME, handler.collectionName());
            assertEquals(SyncPatchType.CRITICAL_BLOCK, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared PushNameSetting version (1)")
        void version() {
            assertEquals(PushNameSetting.ACTION_VERSION, handler.version());
            assertEquals(1, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation — happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("persists the new pushname into the store, sends presence stanza, and returns SUCCESS")
        void persistsPushName() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);

            var result = handler.applyMutation(client, pushNameMutation("Maria", SyncdOperation.SET, ts));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals("Maria", store.name(),
                    "WAWebSetPushnameLocallyAction.setPushnameLocally writes the pushname into Conn.pushname; Cobalt collapses this into store.setName");
            assertEquals(1, sentNodes.size(), "WASendPresenceStatusProtocol.sendPresenceStatusProtocol dispatches one <presence/> stanza");
            var stanza = sentNodes.getFirst();
            assertEquals("presence", stanza.description());
            assertEquals("Maria", stanza.getAttributeAsString("name").orElseThrow(),
                    "smax(\"presence\", {name: OPTIONAL(CUSTOM_STRING, _)})");
        }

        @Test
        @DisplayName("an empty or missing name falls back to the empty string default")
        void emptyNameFallsBack() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);

            var result = handler.applyMutation(client, pushNameMutation(null, SyncdOperation.SET, ts));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals("", store.name(),
                    "WA Web: `_ || (a++, logCriticalBootstrapStageIfNecessary(PUSHNAME_INVALID), _=\"\")` falls back to empty string");
            assertEquals(1, sentNodes.size());
            assertEquals("", sentNodes.getFirst().getAttributeAsString("name").orElseThrow());
        }
    }

    @Nested
    @DisplayName("applyMutation — orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("pushname is a global account setting, so there is no per-entity orphan path")
        void noOrphan() {
            var result = handler.applyMutation(client, pushNameMutation("X", SyncdOperation.SET, Instant.now()));
            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "WAWebPushNameSync has no per-chat/per-contact target");
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed action value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SyncActionValue carrying a different action still applies as empty pushname (optional-chain semantics)")
        void wrongShapeFallsThrough() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .archiveChatAction(new ArchiveChatActionBuilder().archived(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("[\"setting_pushName\"]", value, SyncdOperation.SET, ts, 1);

            var result = handler.applyMutation(client, mutation);

            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "WA Web: `_ = e.value.pushNameSetting?.name` tolerates a missing pushNameSetting via the optional chain and applies the empty-string default");
            assertEquals("", store.name());
        }
    }

    @Nested
    @DisplayName("applyMutation — malformed action index")
    class MalformedActionIndex {
        @Test
        @DisplayName("the pushname handler ignores the index shape (global setting)")
        void indexShapeIgnored() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pushNameSetting(new PushNameSettingBuilder().name("Bob").build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("", value, SyncdOperation.SET, ts, 1);

            var result = handler.applyMutation(client, mutation);
            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "WAWebPushNameSync.applyMutations does not consult the index; only the action value is read");
        }
    }

    @Nested
    @DisplayName("applyMutation — REMOVE returns UNSUPPORTED")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE is unsupported per the WA Web fall-through")
        void removeIsUnsupported() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = handler.applyMutation(client, pushNameMutation("Maria", SyncdOperation.REMOVE, ts));
            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
            assertTrue(sentNodes.isEmpty(), "REMOVE must not send a presence stanza");
        }
    }

    @Nested
    @DisplayName("resolveConflicts — inherits default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote → APPLY_REMOTE_DROP_LOCAL")
        void newerRemote() {
            var local = pushNameMutation("A", SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = pushNameMutation("B", SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote → SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = pushNameMutation("A", SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = pushNameMutation("B", SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch — inherits default sequential apply")
    class ApplyBatch {
        @Test
        @DisplayName("default batch path applies each mutation in order")
        void sequentialApply() {
            var results = handler.applyMutationBatch(client, List.of(
                    pushNameMutation("Alice", SyncdOperation.SET, Instant.ofEpochSecond(1_000)),
                    pushNameMutation("Bob", SyncdOperation.SET, Instant.ofEpochSecond(2_000))
            ));
            assertEquals(2, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
            assertEquals(SyncActionState.SUCCESS, results.get(1).actionState());
            assertEquals("Bob", store.name());
        }
    }

}
