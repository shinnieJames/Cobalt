package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.business.BusinessFeatureFlagBuilder;
import com.github.auties00.cobalt.model.business.BusinessSubscriptionBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.action.device.PaidFeature;
import com.github.auties00.cobalt.model.sync.action.device.PaidFeatureBuilder;
import com.github.auties00.cobalt.model.sync.action.device.SubscriptionInfo;
import com.github.auties00.cobalt.model.sync.action.device.SubscriptionInfoBuilder;
import com.github.auties00.cobalt.model.sync.action.device.SubscriptionsSyncV2ActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SubscriptionHandler}'s parity with
 * {@code WAWebSubscriptionsSyncV2Sync.applyMutations}.
 *
 * @apiNote
 * Covers the wire-constant trio, the rewrite semantics of a happy
 * {@code SET} (stale subscriptions and feature flags must be cleared
 * before the new snapshot lands), the malformed-value branch when the
 * action is missing, the {@link SyncdOperation#REMOVE} no-op-success
 * branch, the batch-level REMOVE counter logging, and the default
 * conflict-resolution tiebreaker.
 *
 * @implNote
 * The fixture pre-populates a stale subscription and feature flag so
 * the rewrite-semantics test can observe both being wiped on the next
 * {@code SET}; tests that exercise empty payloads omit the seed.
 */
@DisplayName("SubscriptionHandler")
class SubscriptionHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppClient client;

    /**
     * Builds the per-test harness.
     *
     * @apiNote
     * Each test runs against a fresh
     * {@link com.github.auties00.cobalt.store.WhatsAppStore} so the
     * subscription and feature-flag maps start empty.
     */
    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    /**
     * Wraps the given subscription and feature-flag lists into a
     * trusted mutation under the canonical
     * {@code ["subscriptions_sync_v2"]} index.
     *
     * @apiNote
     * Callers passing {@code null} for either list get an empty list
     * substituted so the per-mutation rewrite still runs.
     *
     * @param op       the mutation operation
     * @param ts       the mutation timestamp
     * @param subs     the new subscriptions list, or {@code null} for empty
     * @param features the new paid-features list, or {@code null} for empty
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted setMutation(SyncdOperation op, Instant ts,
                                                         List<SubscriptionInfo> subs,
                                                         List<PaidFeature> features) {
        var action = new SubscriptionsSyncV2ActionBuilder()
                .subscriptions(subs == null ? List.of() : subs)
                .paidFeatures(features == null ? List.of() : features)
                .build();
        var value = new SyncActionValueBuilder().timestamp(ts).subscriptionsSyncV2Action(action).build();
        return new DecryptedMutation.Trusted("[\"subscriptions_sync_v2\"]", value, op, ts, 1);
    }

    @Nested
    @DisplayName("metadata - wire identity")
    class Metadata {
        @Test
        @DisplayName("actionName() returns \"subscriptions_sync_v2\"")
        void actionName() {
            assertEquals("subscriptions_sync_v2", new SubscriptionHandler().actionName());
        }

        @Test
        @DisplayName("collectionName() returns REGULAR")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR, new SubscriptionHandler().collectionName());
        }

        @Test
        @DisplayName("version() returns 1")
        void version() {
            assertEquals(1, new SubscriptionHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation - happy SET")
    class ApplySetHappy {
        @Test
        @DisplayName("SET rewrites the subscription table and the feature-flag table")
        void rewritesTables() {
            // Pre-populate one stale subscription and one stale feature flag
            client.store().putBusinessSubscription(new BusinessSubscriptionBuilder().id("STALE").build());
            client.store().putBusinessFeatureFlag(new BusinessFeatureFlagBuilder().name("stale_feature").enabled(true).build());

            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var subs = List.of(new SubscriptionInfoBuilder().id("SUB-1").status("active")
                    .endTime(1_800_000_000L).creationTime(1_600_000_000L).build());
            var features = List.of(new PaidFeatureBuilder().name("marketing_messages").enabled(true).build());

            var result = new SubscriptionHandler().applyMutation(client, setMutation(SyncdOperation.SET, ts, subs, features));
            assertEquals(SyncActionState.SUCCESS, result.actionState());

            assertTrue(client.store().findBusinessSubscription("STALE").isEmpty(),
                    "WAWebSubscriptions.applySubscriptionsAndFeatureFlags 'rewrite' clears the stale subscription");
            var newSub = client.store().findBusinessSubscription("SUB-1").orElseThrow();
            assertEquals("active", newSub.status().orElseThrow());

            assertTrue(client.store().findBusinessFeatureFlag("stale_feature").isEmpty(),
                    "the stale feature flag is dropped on rewrite");
            var newFeature = client.store().findBusinessFeatureFlag("marketing_messages").orElseThrow();
            assertTrue(newFeature.enabled());
        }

        @Test
        @DisplayName("a subscription without an id is silently dropped (Cobalt addCallLog-style precondition)")
        void subscriptionWithoutIdIsDropped() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var subs = List.of(new SubscriptionInfoBuilder().status("active").build());
            var result = new SubscriptionHandler().applyMutation(client, setMutation(SyncdOperation.SET, ts, subs, List.of()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(client.store().businessSubscriptions().isEmpty());
        }

        @Test
        @DisplayName("a paid feature without a name is silently dropped")
        void featureWithoutNameIsDropped() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var features = List.of(new PaidFeatureBuilder().enabled(true).build());
            var result = new SubscriptionHandler().applyMutation(client, setMutation(SyncdOperation.SET, ts, List.of(), features));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(client.store().businessFeatureFlags().isEmpty());
        }
    }

    @Nested
    @DisplayName("applyMutation - orphan dimension is n/a")
    class OrphanDimension {
        @Test
        @DisplayName("subscriptions sync is singleton and account-wide; no per-entity orphan path")
        void noOrphan() {
            var result = new SubscriptionHandler().applyMutation(client,
                    setMutation(SyncdOperation.SET, Instant.now(), List.of(), List.of()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed action value")
    class MalformedActionValue {
        @Test
        @DisplayName("a SyncActionValue carrying a different action returns MALFORMED")
        void wrongActionIsMalformed() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .archiveChatAction(new ArchiveChatActionBuilder().archived(true).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("[\"subscriptions_sync_v2\"]", value, SyncdOperation.SET, ts, 1);
            var result = new SubscriptionHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation - malformed action index")
    class MalformedActionIndex {
        @Test
        @DisplayName("the handler ignores the index shape (singleton action)")
        void indexShapeIgnored() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .subscriptionsSyncV2Action(new SubscriptionsSyncV2ActionBuilder().build())
                    .build();
            var mutation = new DecryptedMutation.Trusted("", value, SyncdOperation.SET, ts, 1);
            var result = new SubscriptionHandler().applyMutation(client, mutation);
            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "the handler does not parse the index; the action is keyed by name only");
        }
    }

    @Nested
    @DisplayName("applyMutation - REMOVE")
    class RemoveOperation {
        @Test
        @DisplayName("REMOVE returns SUCCESS without touching state (per WA Web)")
        void removeReturnsSuccess() {
            client.store().putBusinessSubscription(new BusinessSubscriptionBuilder().id("KEEP").build());
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var result = new SubscriptionHandler().applyMutation(client, setMutation(SyncdOperation.REMOVE, ts, List.of(), List.of()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(client.store().findBusinessSubscription("KEEP").isPresent(),
                    "WAWebSubscriptionsSyncV2Sync's REMOVE branch only increments a counter and returns Success");
        }
    }

    @Nested
    @DisplayName("resolveConflicts - inherits default timestamp comparison")
    class ResolveConflicts {
        @Test
        @DisplayName("newer remote -> APPLY_REMOTE_DROP_LOCAL")
        void newerRemoteApplies() {
            var local = setMutation(SyncdOperation.SET, Instant.ofEpochSecond(1_000), List.of(), List.of());
            var remote = setMutation(SyncdOperation.SET, Instant.ofEpochSecond(2_000), List.of(), List.of());
            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    new SubscriptionHandler().resolveConflicts(local, remote).state());
        }

        @Test
        @DisplayName("older remote -> SKIP_REMOTE")
        void olderRemoteSkipped() {
            var local = setMutation(SyncdOperation.SET, Instant.ofEpochSecond(2_000), List.of(), List.of());
            var remote = setMutation(SyncdOperation.SET, Instant.ofEpochSecond(1_000), List.of(), List.of());
            assertEquals(ConflictResolutionState.SKIP_REMOTE,
                    new SubscriptionHandler().resolveConflicts(local, remote).state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch - sequential apply with REMOVE counter")
    class ApplyBatchOverride {
        @Test
        @DisplayName("each mutation is applied; REMOVEs are counted but do not block subsequent SETs")
        void sequentialApply() {
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var feature = List.of(new PaidFeatureBuilder().name("f").enabled(true).build());
            var results = new SubscriptionHandler().applyMutationBatch(client, List.of(
                    setMutation(SyncdOperation.REMOVE, ts, List.of(), List.of()),
                    setMutation(SyncdOperation.SET, ts.plusSeconds(1), List.of(), feature)
            ));
            assertEquals(2, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
            assertEquals(SyncActionState.SUCCESS, results.get(1).actionState());
            assertTrue(client.store().findBusinessFeatureFlag("f").isPresent());
        }

        @Test
        @DisplayName("an empty batch yields an empty result list")
        void emptyBatchEmptyResult() {
            assertTrue(new SubscriptionHandler().applyMutationBatch(client, List.of()).isEmpty());
        }
    }

}
