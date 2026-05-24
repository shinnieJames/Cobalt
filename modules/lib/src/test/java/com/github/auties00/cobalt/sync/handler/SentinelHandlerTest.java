package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyDataBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationAction;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationActionBuilder;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.factory.SentinelMutationFactory;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SentinelHandler}'s parity with
 * {@code WAWebSentinelMutationSync.applyMutations} plus the outgoing
 * sentinel-mutation builder in {@code SentinelMutationFactory}.
 *
 * @apiNote
 * Covers the wire-constant trio, the happy {@code SET} branch that flips
 * the matched app-state-sync key's data timestamp to
 * {@link Instant#EPOCH}, the no-op branch when no stored key
 * matches the announced epoch, the malformed branch for missing or
 * wrong-typed values, the unsupported branch for non-{@code SET}
 * operations, the {@code applyMutationBatch} default per-item dispatch,
 * the default conflict-resolution tiebreaker, and the one-mutation-per-
 * collection emission shape of {@code SentinelMutationFactory}.
 *
 * @implNote
 * Tests seed sync keys via {@link SyncKeyUtils#buildKeyId(int, int)} so
 * the in-store key id layout matches what the production handler reads;
 * the matching predicate is exact so a single off-by-one epoch leaves
 * every stored key untouched.
 */
@DisplayName("SentinelHandler")
class SentinelHandlerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppClient client;

    /**
     * Builds the per-test harness.
     *
     * @apiNote
     * Each test runs against a fresh
     * {@link com.github.auties00.cobalt.store.WhatsAppStore} so any
     * seeded sync keys do not leak between cases.
     */
    @BeforeEach
    void setUp() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        client = TestWhatsAppClient.create().withStore(store);
    }

    /**
     * Builds a trusted sentinel mutation carrying the given epoch and
     * operation.
     *
     * @apiNote
     * The boxed {@link Integer} epoch lets tests pass {@code null} to
     * exercise the malformed-value branch where
     * {@link KeyExpirationAction#expiredKeyEpoch()} is empty.
     *
     * @param epoch the {@code expiredKeyEpoch} carried by the action, or {@code null} to omit the field
     * @param op    the mutation operation
     * @param ts    the mutation timestamp (also used as the {@code SyncActionValue} timestamp)
     * @return the trusted mutation
     */
    private static DecryptedMutation.Trusted sentinelMutation(Integer epoch, SyncdOperation op, Instant ts) {
        var action = new KeyExpirationActionBuilder().expiredKeyEpoch(epoch).build();
        var value = new SyncActionValueBuilder().timestamp(ts).keyExpirationAction(action).build();
        var index = "[\"sentinel\",\"regular\"]";
        return new DecryptedMutation.Trusted(index, value, op, ts, 3);
    }

    /**
     * Returns the WA-Web-compatible sync-key id byte string for the
     * given device and epoch.
     *
     * @apiNote
     * Centralizes the id layout so {@link #seedSyncKey(int, int)} and
     * any future assertion agree on the bit-for-bit encoding.
     *
     * @param deviceId the registered device id
     * @param epoch    the key epoch
     * @return the sync-key id byte string
     */
    private static byte[] syncKeyId(int deviceId, int epoch) {
        return SyncKeyUtils.buildKeyId(deviceId, epoch);
    }

    /**
     * Seeds a single app-state-sync key with the given device and
     * epoch.
     *
     * @apiNote
     * Used to set up the local key store before exercising the
     * happy/no-match branches of
     * {@link SentinelHandler#applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}.
     *
     * @implNote
     * The seeded key carries a 32-byte zero buffer for {@code keyData}
     * and the current instant as its data timestamp; the handler only
     * mutates the timestamp on expiry, so any non-{@link Instant#EPOCH}
     * value works as the "before" baseline.
     *
     * @param deviceId the registered device id
     * @param epoch    the key epoch
     */
    private void seedSyncKey(int deviceId, int epoch) {
        var keyId = new AppStateSyncKeyIdBuilder().keyId(syncKeyId(deviceId, epoch)).build();
        var keyData = new AppStateSyncKeyDataBuilder()
                .keyData(new byte[32])
                .timestamp(Instant.now())
                .build();
        client.store().addWebAppStateKeys(List.of(
                new AppStateSyncKeyBuilder().keyId(keyId).keyData(keyData).build()));
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {
        @Test
        @DisplayName("actionName() returns 'sentinel'")
        void actionName() {
            assertEquals(KeyExpirationAction.ACTION_NAME, new SentinelHandler().actionName());
            assertEquals("sentinel", new SentinelHandler().actionName());
        }

        @Test
        @DisplayName("collectionName() is SyncPatchType.REGULAR_LOW")
        void collectionName() {
            assertEquals(SyncPatchType.REGULAR_LOW, new SentinelHandler().collectionName());
        }

        @Test
        @DisplayName("version() returns the declared action version (3)")
        void version() {
            assertEquals(KeyExpirationAction.ACTION_VERSION, new SentinelHandler().version());
            assertEquals(3, new SentinelHandler().version());
        }
    }

    @Nested
    @DisplayName("applyMutation: SET stamps Instant.EPOCH onto the matching sync key")
    class SetHappy {
        @Test
        @DisplayName("expired epoch matching a stored key flips that key's data timestamp to EPOCH")
        void expiresStoredKey() {
            seedSyncKey(0, 5);
            assertEquals(1, client.store().appStateKeys().size());

            var result = new SentinelHandler().applyMutation(
                    client, sentinelMutation(5, SyncdOperation.SET, Instant.ofEpochSecond(1700000000L)));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(1, client.store().appStateKeys().size(),
                    "the key is NOT removed; only its data.timestamp is set to Instant.EPOCH");
            var key = client.store().appStateKeys().iterator().next();
            var timestamp = key.keyData().orElseThrow().timestamp().orElseThrow();
            assertEquals(Instant.EPOCH, timestamp,
                    "the matching key's data timestamp must be set to Instant.EPOCH");
        }

        @Test
        @DisplayName("an epoch not matching any stored key leaves the timestamp untouched")
        void noMatchingKey() {
            seedSyncKey(0, 5);
            var beforeTs = client.store().appStateKeys().iterator().next()
                    .keyData().orElseThrow().timestamp().orElseThrow();

            var result = new SentinelHandler().applyMutation(
                    client, sentinelMutation(99, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.SUCCESS, result.actionState());
            assertEquals(1, client.store().appStateKeys().size(),
                    "no key matches the expired epoch - nothing to expire");
            var afterTs = client.store().appStateKeys().iterator().next()
                    .keyData().orElseThrow().timestamp().orElseThrow();
            assertEquals(beforeTs, afterTs,
                    "the unmatched key's timestamp must remain as seeded");
        }
    }

    @Nested
    @DisplayName("applyMutation: malformed value")
    class Malformed {
        @Test
        @DisplayName("non-keyExpiration action is MALFORMED")
        void wrongActionType() {
            var wrongValue = new SyncActionValueBuilder()
                    .timestamp(Instant.now())
                    .favoritesAction(new FavoritesActionBuilder().favorites(List.of()).build())
                    .build();
            var mutation = new DecryptedMutation.Trusted(
                    "[\"sentinel\",\"regular\"]", wrongValue, SyncdOperation.SET, Instant.now(), 3);

            var result = new SentinelHandler().applyMutation(client, mutation);

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }

        @Test
        @DisplayName("missing expiredKeyEpoch field is MALFORMED")
        void missingEpoch() {
            var result = new SentinelHandler().applyMutation(
                    client, sentinelMutation(null, SyncdOperation.SET, Instant.now()));

            assertEquals(SyncActionState.MALFORMED, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation: malformed index - n/a")
    class MalformedIndexNa {
        @Test
        @DisplayName("the handler does not parse indexParts[1]; the index is not part of malformed surface")
        void indexNotValidated() {
            var result = new SentinelHandler().applyMutation(
                    client, sentinelMutation(5, SyncdOperation.SET, Instant.now()));
            assertEquals(SyncActionState.SUCCESS, result.actionState());
        }
    }

    @Nested
    @DisplayName("applyMutation: REMOVE is UNSUPPORTED")
    class RemoveBranch {
        @Test
        @DisplayName("REMOVE operation does not touch the keystore")
        void removeIsUnsupported() {
            seedSyncKey(0, 5);

            var result = new SentinelHandler().applyMutation(
                    client, sentinelMutation(5, SyncdOperation.REMOVE, Instant.now()));

            assertEquals(SyncActionState.UNSUPPORTED, result.actionState());
            assertEquals(1, client.store().appStateKeys().size(),
                    "REMOVE must not expire any key");
        }
    }

    @Nested
    @DisplayName("getSentinelMutations - one pending mutation per collection")
    class SentinelBuilder {
        @Test
        @DisplayName("returns one SET pending mutation per SyncPatchType when a key exists")
        void emitsOnePerCollection() {
            seedSyncKey(7, 12);

            var pending = new SentinelMutationFactory().getSentinelMutations(client);

            assertEquals(SyncPatchType.values().length, pending.size(),
                    "one mutation per collection");
            for (var m : pending) {
                assertEquals(SyncdOperation.SET, m.mutation().operation());
                assertEquals(KeyExpirationAction.ACTION_VERSION, m.mutation().actionVersion());
                var action = (KeyExpirationAction) m.mutation().value().action().orElseThrow();
                assertEquals(12, action.expiredKeyEpoch().orElseThrow(),
                        "every sentinel mutation must carry the newest key's epoch");
            }
        }

        @Test
        @DisplayName("returns an empty list when no sync keys exist")
        void emptyWhenNoKeys() {
            var pending = new SentinelMutationFactory().getSentinelMutations(client);
            assertTrue(pending.isEmpty(),
                    "no key pairs in the store means no sentinel work");
        }
    }

    @Nested
    @DisplayName("resolveConflicts - default timestamp-based")
    class ResolveConflicts {
        @Test
        @DisplayName("remote with the later timestamp wins")
        void remoteWins() {
            var local  = sentinelMutation(5, SyncdOperation.SET, Instant.ofEpochSecond(1700000000L));
            var remote = sentinelMutation(6, SyncdOperation.SET, Instant.ofEpochSecond(1700000010L));

            var resolution = new SentinelHandler().resolveConflicts(local, remote);

            assertEquals(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL, resolution.state());
        }

        @Test
        @DisplayName("local with the strictly later timestamp wins")
        void localWins() {
            var local  = sentinelMutation(6, SyncdOperation.SET, Instant.ofEpochSecond(1700000010L));
            var remote = sentinelMutation(5, SyncdOperation.SET, Instant.ofEpochSecond(1700000000L));

            var resolution = new SentinelHandler().resolveConflicts(local, remote);

            assertEquals(ConflictResolutionState.SKIP_REMOTE, resolution.state());
        }
    }

    @Nested
    @DisplayName("applyMutationBatch - n/a, default implementation")
    class BatchNa {
        @Test
        @DisplayName("the handler delegates to applyMutation per mutation")
        void perItem() {
            seedSyncKey(0, 5);
            seedSyncKey(0, 6);

            var batch = List.of(
                    sentinelMutation(5, SyncdOperation.SET, Instant.now()),
                    sentinelMutation(99, SyncdOperation.SET, Instant.now()),
                    sentinelMutation(6, SyncdOperation.REMOVE, Instant.now()));

            var results = new SentinelHandler().applyMutationBatch(client, batch);

            assertEquals(3, results.size());
            assertEquals(SyncActionState.SUCCESS, results.get(0).actionState());
            assertEquals(SyncActionState.SUCCESS, results.get(1).actionState());
            assertEquals(SyncActionState.UNSUPPORTED, results.get(2).actionState());
            assertEquals(2, client.store().appStateKeys().size(),
                    "neither SET nor REMOVE removes keys; expireAppStateKeysByEpoch only flips data timestamps");
        }
    }

}
