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
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction.MaibaAIFeatureStatus;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlActionBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@link MaibaAIFeaturesControlHandler} forward-looking
 * adapter for {@code maiba_ai_features_control}.
 *
 * @apiNote
 * Verifies the Cobalt-inferred behaviour for the SMB Maiba AI feature
 * status mutation across metadata, the SET happy path that persists
 * the status via
 * {@link WhatsAppStore#setAiBusinessAgentStatus(MaibaAIFeatureStatus)},
 * the malformed branch when {@link MaibaAIFeatureStatus} is empty,
 * the REMOVE rejection and the inherited timestamp-based conflict
 * resolution. WA Web ships the protobuf field but does not register a
 * corresponding sync handler, so every behavioural step is
 * Cobalt-inferred.
 *
 * @implNote
 * This implementation exercises the handler against an in-memory
 * {@link DeviceFixtures#temporaryStore} via {@link TestWhatsAppClient}
 * so the
 * {@link WhatsAppStore#aiBusinessAgentStatus()} read-back can be
 * asserted directly.
 */
@DisplayName("MaibaAIFeaturesControlHandler")
class MaibaAIFeaturesControlHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private WhatsAppClient client;
    private MaibaAIFeaturesControlHandler handler;

    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
        handler = new MaibaAIFeaturesControlHandler();
    }

    /**
     * Builds a {@link DecryptedMutation.Trusted} carrying the given
     * action payload under the canonical
     * {@code ["maiba_ai_features_control"]} index.
     *
     * @apiNote
     * Used by every test to centralise mutation construction. The
     * {@code action} parameter is nullable so the malformed-value
     * path can be exercised without re-implementing the envelope.
     *
     * @param action the action payload, may be {@code null}
     * @param op     the {@link SyncdOperation} to wrap
     * @param ts     the mutation timestamp
     * @return a {@link DecryptedMutation.Trusted} with the requested
     *         shape
     */
    private DecryptedMutation.Trusted build(MaibaAIFeaturesControlAction action, SyncdOperation op, Instant ts) {
        var valueBuilder = new SyncActionValueBuilder().timestamp(ts);
        if (action != null) {
            valueBuilder.maibaAiFeaturesControlAction(action);
        }
        var index = JSON.toJSONString(List.of(handler.actionName()));
        return new DecryptedMutation.Trusted(index, valueBuilder.build(), op, ts, handler.version());
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {
        @Test
        @DisplayName("actionName() returns 'maiba_ai_features_control'")
        void actionName() {
            assertEquals(MaibaAIFeaturesControlAction.ACTION_NAME, handler.actionName());
            assertEquals("maiba_ai_features_control", handler.actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR_HIGH")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR_HIGH, handler.collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version")
        void version() {
            assertEquals(MaibaAIFeaturesControlAction.ACTION_VERSION, handler.version());
        }
    }

    @Nested
    @DisplayName("applyMutation - SET writes the AI feature status")
    class ApplySetHappy {
        @Test
        @DisplayName("SET with ENABLED persists ENABLED on the store")
        void setsEnabled() {
            assertTrue(store.aiBusinessAgentStatus().isEmpty(), "precondition: status is unset");
            var action = new MaibaAIFeaturesControlActionBuilder()
                    .aiFeatureStatus(MaibaAIFeatureStatus.ENABLED).build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(MaibaAIFeatureStatus.ENABLED, store.aiBusinessAgentStatus().orElseThrow());
        }

        @Test
        @DisplayName("SET with DISABLED overwrites a prior ENABLED value")
        void setsDisabledOverwrites() {
            store.setAiBusinessAgentStatus(MaibaAIFeatureStatus.ENABLED);
            var action = new MaibaAIFeaturesControlActionBuilder()
                    .aiFeatureStatus(MaibaAIFeatureStatus.DISABLED).build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(MaibaAIFeatureStatus.DISABLED, store.aiBusinessAgentStatus().orElseThrow());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SET with a different action returns MALFORMED")
        void wrongActionType() {
            var ts = Instant.now();
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .pinAction(new PinActionBuilder().pinned(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"maiba_ai_features_control\"]", value, SyncdOperation.SET, ts, handler.version());

            assertEquals(SyncActionState.MALFORMED, handler.applyMutation(client, mutation).actionState());
            assertTrue(store.aiBusinessAgentStatus().isEmpty(),
                    "a malformed payload must not touch the store");
        }

        @Test
        @DisplayName("a SET whose aiFeatureStatus is unset returns MALFORMED")
        void emptyStatus() {
            var action = new MaibaAIFeaturesControlActionBuilder().build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE is UNSUPPORTED")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE returns UNSUPPORTED without touching the store")
        void removeIsUnsupported() {
            var action = new MaibaAIFeaturesControlActionBuilder()
                    .aiFeatureStatus(MaibaAIFeatureStatus.ENABLED).build();

            var result = handler.applyMutation(client, build(action, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
            assertTrue(store.aiBusinessAgentStatus().isEmpty());
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp-based")
    class ConflictResolution {
        @Test
        @DisplayName("newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var action = new MaibaAIFeaturesControlActionBuilder()
                    .aiFeatureStatus(MaibaAIFeatureStatus.ENABLED).build();
            var local = build(action, SyncdOperation.SET, Instant.ofEpochSecond(1_000));
            var remote = build(action, SyncdOperation.SET, Instant.ofEpochSecond(2_000));

            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    handler.resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var action = new MaibaAIFeaturesControlActionBuilder()
                    .aiFeatureStatus(MaibaAIFeatureStatus.ENABLED).build();
            var local = build(action, SyncdOperation.SET, Instant.ofEpochSecond(2_000));
            var remote = build(action, SyncdOperation.SET, Instant.ofEpochSecond(1_000));

            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    handler.resolveConflicts(local, remote).state());
        }
    }

}
