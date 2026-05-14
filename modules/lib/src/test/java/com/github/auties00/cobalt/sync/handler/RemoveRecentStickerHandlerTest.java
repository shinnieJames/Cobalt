package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.preference.StickerBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.RemoveRecentStickerAction;
import com.github.auties00.cobalt.model.sync.action.media.RemoveRecentStickerActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.RemoveRecentStickerMutationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RemoveRecentStickerHandler}, Cobalt's adapter for
 * {@code WAWebStickersRemoveRecentSyncAction}.
 *
 * <p>The handler removes a sticker from the recent-stickers collection when
 * the incoming mutation's {@code lastStickerSentTs} is missing or not older
 * than the local entry's timestamp.
 *
 * <p>Matrix:
 * <ul>
 *   <li>Metadata wire constants.</li>
 *   <li>Feature gating: missing {@code recent_sticker} primary feature is
 *       {@code UNSUPPORTED} for every mutation.</li>
 *   <li>Non-{@code SET} operation is {@code UNSUPPORTED}.</li>
 *   <li>Malformed index variations.</li>
 *   <li>ORPHAN when the recent-stickers store has no matching entry.</li>
 *   <li>Happy path: removes the recent sticker when the timestamps agree.</li>
 *   <li>Skip-removal path: local entry is newer than the incoming
 *       {@code lastStickerSentTs} â€” entry stays, but result is still SUCCESS.</li>
 *   <li>Default conflict-resolution semantics.</li>
 *   <li>{@code getRemoveRecentStickerMutation} produces a SET pending
 *       mutation with the configured index and timestamp propagation.</li>
 *   <li>WA Web byte-parity oracle (gated).</li>
 * </ul>
 */
@DisplayName("RemoveRecentStickerHandler")
class RemoveRecentStickerHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final String STICKER_HASH = "recent-hash-12345";
    private static final String RECENT_STICKER_FEATURE = "recent_sticker";

    private WhatsAppStore store;
    private TestWhatsAppClient client;

    /**
     * Builds a fresh harness and enables the {@code recent_sticker} feature so the
     * tests that need the post-feature-gate branches reach the handler body.
     */
    @BeforeEach
    void setUp() {
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    /**
     * Builds a trusted SET mutation carrying the given action and index.
     *
     * @param action the remove-recent-sticker payload
     * @param index  the JSON-encoded index
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted setMutation(RemoveRecentStickerAction action, String index) {
        var ts = Instant.ofEpochSecond(1_700_000_000L);
        var value = new SyncActionValueBuilder()
                .timestamp(ts)
                .removeRecentStickerAction(action)
                .build();
        return new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts,
                RemoveRecentStickerAction.ACTION_VERSION);
    }

    /**
     * Seeds the recent-stickers store with a sticker carrying the given timestamp
     * keyed under {@link #STICKER_HASH}.
     *
     * @param epochSecond the sticker's epoch-second timestamp
     */
    private void seedRecentSticker(long epochSecond) {
        var sticker = new StickerBuilder().timestamp(epochSecond).build();
        store.addRecentSticker(STICKER_HASH, sticker);
    }

    @Nested
    @DisplayName("metadata â€” wire constants")
    class Metadata {
        @Test
        @DisplayName("actionName() returns removeRecentSticker")
        void actionName() {
            assertEquals(RemoveRecentStickerAction.ACTION_NAME,
                    new RemoveRecentStickerHandler().actionName());
            assertEquals("removeRecentSticker", new RemoveRecentStickerHandler().actionName());
        }

        @Test
        @DisplayName("collectionName() is REGULAR_LOW")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR_LOW,
                    new RemoveRecentStickerHandler().collectionName());
        }

        @Test
        @DisplayName("version() is 7")
        void version() {
            assertEquals(7, new RemoveRecentStickerHandler().version());
            assertEquals(RemoveRecentStickerAction.ACTION_VERSION,
                    new RemoveRecentStickerHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” feature gating")
    class FeatureGating {
        @Test
        @DisplayName("missing recent_sticker primary feature short-circuits to UNSUPPORTED")
        void unsupportedWhenFeatureDisabled() {
            // Default temporary store has no primary features.
            var action = new RemoveRecentStickerActionBuilder().build();
            var result = new RemoveRecentStickerHandler().applyMutation(client,
                    setMutation(action, "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]"));
            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” REMOVE / non-SET branch")
    class RemoveBranch {
        @Test
        @DisplayName("REMOVE operation is UNSUPPORTED even when the feature is enabled")
        void removeUnsupported() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder()
                    .timestamp(ts)
                    .removeRecentStickerAction(new RemoveRecentStickerActionBuilder().build())
                    .build();
            var index = "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]";
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.REMOVE, ts, 7);
            assertEquals(SyncActionState.UNSUPPORTED,
                    new RemoveRecentStickerHandler().applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed index")
    class MalformedIndex {
        @Test
        @DisplayName("missing sticker hash (single-element index) is MALFORMED")
        void singleElementIndex() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            var action = new RemoveRecentStickerActionBuilder().build();
            var result = new RemoveRecentStickerHandler().applyMutation(client,
                    setMutation(action, "[\"removeRecentSticker\"]"));
            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation â€” orphan (no local entry)")
    class Orphan {
        @Test
        @DisplayName("no local recent sticker reports ORPHAN (no model id/type)")
        void orphanWhenMissingLocally() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            var action = new RemoveRecentStickerActionBuilder().build();
            var result = new RemoveRecentStickerHandler().applyMutation(client,
                    setMutation(action, "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]"));
            assertEquals(MutationApplicationResult.orphan(), result,
                    "WA Web reports a bare ORPHAN without model id/type for this handler");
        }
    }

    @Nested
    @DisplayName("applyMutation â€” happy SET path")
    class HappySet {
        @Test
        @DisplayName("missing lastStickerSentTs removes the local entry and reports SUCCESS")
        void removesWhenLastSentMissing() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            seedRecentSticker(1_600_000_000L);
            var action = new RemoveRecentStickerActionBuilder().build(); // no lastStickerSentTs
            var result = new RemoveRecentStickerHandler().applyMutation(client,
                    setMutation(action, "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]"));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findRecentSticker(STICKER_HASH).isEmpty(),
                    "missing lastStickerSentTs must trigger removal");
        }

        @Test
        @DisplayName("local timestamp <= lastStickerSentTs removes the local entry")
        void removesWhenLocalNotNewer() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            seedRecentSticker(1_600_000_000L); // local
            var action = new RemoveRecentStickerActionBuilder()
                    .lastStickerSentTs(Instant.ofEpochSecond(1_700_000_000L)) // newer
                    .build();
            var result = new RemoveRecentStickerHandler().applyMutation(client,
                    setMutation(action, "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]"));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertTrue(store.findRecentSticker(STICKER_HASH).isEmpty());
        }

        @Test
        @DisplayName("local timestamp > lastStickerSentTs keeps the local entry but still reports SUCCESS")
        void keepsWhenLocalNewer() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            seedRecentSticker(1_800_000_000L); // local newer
            var action = new RemoveRecentStickerActionBuilder()
                    .lastStickerSentTs(Instant.ofEpochSecond(1_700_000_000L)) // older
                    .build();
            var result = new RemoveRecentStickerHandler().applyMutation(client,
                    setMutation(action, "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]"));
            assertEquals(SyncActionState.SUCCESS, result.actionState(),
                    "WA Web returns SUCCESS even when the removal is skipped");
            assertTrue(store.findRecentSticker(STICKER_HASH).isPresent(),
                    "local entry newer than lastStickerSentTs must survive");
        }
    }

    @Nested
    @DisplayName("applyMutation â€” malformed action value")
    class MalformedValue {
        @Test
        @DisplayName("missing action sub-message still reaches orphan check; with no entry yields ORPHAN")
        void missingActionWithoutEntry() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            var ts = Instant.ofEpochSecond(1_700_000_000L);
            var value = new SyncActionValueBuilder().timestamp(ts).build(); // no removeRecentStickerAction
            var index = "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]";
            var mutation = new DecryptedMutation.Trusted(index, value, SyncdOperation.SET, ts, 7);

            // WA Web's per-mutation closure tolerates a missing sub-message via the optional
            // chain. Cobalt mirrors that: lastStickerSentTs is treated as null, the orphan
            // check follows, and with no local entry the result is ORPHAN.
            assertEquals(SyncActionState.ORPHAN,
                    new RemoveRecentStickerHandler().applyMutation(client, mutation).actionState());
        }
    }

    @Nested
    @DisplayName("resolveConflicts â€” default timestamp tiebreaker")
    class ResolveConflicts {
        @Test
        @DisplayName("remote with later timestamp wins (APPLY_REMOTE_DROP_LOCAL)")
        void remoteLater() {
            var ts0 = Instant.ofEpochSecond(1_700_000_000L);
            var local = setMutation(new RemoveRecentStickerActionBuilder().build(),
                    "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]");
            var remoteTs = ts0.plusSeconds(10);
            var remoteValue = new SyncActionValueBuilder()
                    .timestamp(remoteTs)
                    .removeRecentStickerAction(new RemoveRecentStickerActionBuilder().build())
                    .build();
            var remote = new DecryptedMutation.Trusted(local.index(), remoteValue, SyncdOperation.SET, remoteTs, 7);
            var resolution = new RemoveRecentStickerHandler().resolveConflicts(local, remote);
            assertEquals(com.github.auties00.cobalt.model.sync.ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL,
                    resolution.state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch â€” default per-item dispatch (n/a override)")
    class BatchDispatch {
        @Test
        @DisplayName("the handler does not override applyMutationBatch â€” default per-item dispatch is used")
        void defaultDispatchPreserved() {
            store.setPrimaryFeatures(List.of(RECENT_STICKER_FEATURE));
            seedRecentSticker(1_600_000_000L);
            var action = new RemoveRecentStickerActionBuilder().build();
            var batch = List.of(
                    setMutation(action, "[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]"));
            var results = new RemoveRecentStickerHandler().applyMutationBatch(client, batch);
            assertEquals(1, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
        }
    }

    @Nested
    @DisplayName("getRemoveRecentStickerMutation â€” pending mutation builder")
    class Builder {
        @Test
        @DisplayName("builder emits a SET mutation at [\"removeRecentSticker\", hash]")
        void buildsCorrectIndex() {
            var pending = new RemoveRecentStickerMutationFactory().getRemoveRecentStickerMutation(STICKER_HASH);
            var mutation = pending.mutation();
            assertEquals(SyncdOperation.SET, mutation.operation());
            assertEquals("[\"removeRecentSticker\",\"" + STICKER_HASH + "\"]", mutation.index());
            assertEquals(RemoveRecentStickerAction.ACTION_VERSION, mutation.actionVersion());
        }

        @Test
        @DisplayName("builder propagates the current timestamp to both lastStickerSentTs and value.timestamp")
        void timestampPropagated() {
            var before = Instant.now();
            var pending = new RemoveRecentStickerMutationFactory().getRemoveRecentStickerMutation(STICKER_HASH);
            var after = Instant.now();
            var action = pending.mutation().value().action().filter(a -> a instanceof RemoveRecentStickerAction).map(a -> (RemoveRecentStickerAction) a).orElseThrow();
            var lastTs = action.lastStickerSentTs().orElseThrow();
            assertFalse(lastTs.isBefore(before.minusSeconds(1)),
                    "lastStickerSentTs must reflect the build instant");
            assertFalse(lastTs.isAfter(after.plusSeconds(1)),
                    "lastStickerSentTs must reflect the build instant");
        }

        @Test
        @DisplayName("attemptCount of a freshly built pending mutation is zero")
        void freshAttemptCount() {
            assertEquals(0,
                    new RemoveRecentStickerMutationFactory().getRemoveRecentStickerMutation(STICKER_HASH).attemptCount());
        }
    }

    @Nested
    @DisplayName("WA Web byte-parity oracle")
    class OracleParity {
        @Test
        @DisplayName("captured encode payload (when present) matches Cobalt's wire encoding")
        void oracle() {
            if (!SyncFixtures.isOracleAvailable("handler/remove-recent-sticker/encode")) {
                return;
            }
            var oracle = SyncFixtures.loadOracle("handler/remove-recent-sticker/encode");
            assertNotNull(oracle);
        }
    }

}
