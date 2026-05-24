package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyDataBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the synchronous, store-observable invariants of {@link SyncKeyRotationService}.
 *
 * @apiNote
 * Covers the deterministic store-only paths of the rotation service:
 * {@code handleKeyShare} (insertion, dedup, missing-key clear, key-data divergence
 * logging), {@code getNewestKeyPair} (delegation to
 * {@link SyncKeyUtils#findNewestKey}), and {@code getActiveKey}
 * (empty-store throw, rotation-suppressed return). The full timer-driven rotation flow
 * and the CDN-mediated key-share peer messages are exercised by the Phase 9 integration
 * cycles, which run a fully wired stack.
 *
 * @implNote
 * This implementation drives every test through {@link #build()} which constructs the
 * full {@link WebAppStateService} dependency chain and pulls the rotation service out;
 * doing so avoids reproducing the wiring (and the
 * {@link MissingSyncKeyRequestService}/{@link MissingSyncKeyTimeoutScheduler} cyclic
 * dependency in particular) per test.
 */
@DisplayName("SyncKeyRotationService")
class SyncKeyRotationServiceTest {
    /**
     * The fixed self phone-number JID used by every test in this class.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The fixed self LID JID used by every test in this class.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The fixed self device JID used by every test in this class (device 1).
     */
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    /**
     * Bundles the {@link TestWhatsAppClient}, the {@link WhatsAppStore}, and the
     * {@link SyncKeyRotationService} into a single value so each test can name them
     * locally.
     *
     * @param client the synthetic client wired to {@code store}
     * @param store the store the rotation service reads and mutates
     * @param rotation the system under test
     */
    private record Harness(TestWhatsAppClient client, WhatsAppStore store, SyncKeyRotationService rotation) {
    }

    /**
     * Builds a full Cobalt {@link WebAppStateService} stack and returns the rotation
     * service together with the underlying store.
     *
     * @apiNote
     * Used by every test as a one-line setup; centralises the dependency wiring so
     * individual tests stay focused on the rotation behaviour.
     *
     * @return the freshly built {@link Harness}
     */
    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        var client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wam);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wam);
        var webAppState = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wam, TestMediaConnectionService.create());
        return new Harness(client, store, webAppState.syncKeyRotationService());
    }

    /**
     * Returns a new byte array of the given length, every entry filled with {@code value}.
     *
     * @apiNote
     * Helper used to build deterministic 32-byte key data without the noise of a
     * {@code byte[]} literal.
     *
     * @param length the array length
     * @param value the byte value to fill with
     * @return the freshly allocated array
     */
    private static byte[] filled(int length, int value) {
        var out = new byte[length];
        for (var i = 0; i < length; i++) out[i] = (byte) value;
        return out;
    }

    /**
     * Builds an {@link AppStateSyncKey} from a key id and 32-byte key data.
     *
     * @apiNote
     * Helper used by every test to keep the {@link AppStateSyncKey} construction off the
     * test bodies.
     *
     * @param id the key id bytes
     * @param data the 32-byte key data
     * @return the built {@link AppStateSyncKey}
     */
    private static AppStateSyncKey syncKey(byte[] id, byte[] data) {
        return new AppStateSyncKeyBuilder()
                .keyId(new AppStateSyncKeyIdBuilder().keyId(id).build())
                .keyData(new AppStateSyncKeyDataBuilder().keyData(data).timestamp(Instant.now()).build())
                .build();
    }

    /**
     * Tests for the store-side effects of
     * {@link SyncKeyRotationService#handleKeyShare(int, List)}.
     */
    @Nested
    @DisplayName("handleKeyShare - store / dedup / missing-key removal")
    class HandleKeyShare {
        /**
         * Asserts that an inbound key not yet in the store is added on receipt.
         */
        @Test
        @DisplayName("a key not present in the store is added on receipt")
        void storesNewKey() {
            var h = build();
            assertTrue(h.store.appStateKeys().isEmpty(), "precondition: empty key store");

            var keyId = SyncKeyUtils.buildKeyId(1, 1);
            var keyData = filled(32, 0x77);
            h.rotation.handleKeyShare(0, List.of(syncKey(keyId, keyData)));

            var stored = h.store.findWebAppStateKeyById(keyId).orElseThrow(
                    () -> new AssertionError("expected key to have been stored"));
            assertArrayEquals(keyId,
                    stored.keyId().orElseThrow().keyId().orElseThrow());
            assertArrayEquals(keyData,
                    stored.keyData().orElseThrow().keyData().orElseThrow());
        }

        /**
         * Asserts that an inbound key with identical key data does not duplicate the
         * existing entry.
         */
        @Test
        @DisplayName("an already-stored key with identical key data is not re-added")
        void deduplicatesIdenticalKey() {
            var h = build();
            var keyId = SyncKeyUtils.buildKeyId(1, 1);
            var keyData = filled(32, 0x77);
            h.store.addWebAppStateKeys(List.of(syncKey(keyId, keyData)));
            assertEquals(1, h.store.appStateKeys().size());

            h.rotation.handleKeyShare(0, List.of(syncKey(keyId, keyData)));
            assertEquals(1, h.store.appStateKeys().size(),
                    "dedup keeps the existing entry");
        }

        /**
         * Asserts that an inbound key without key data is not stored as a positive
         * response.
         */
        @Test
        @DisplayName("a shared key without key data does not modify the store")
        void keyWithoutDataIsIgnored() {
            var h = build();
            var keyId = SyncKeyUtils.buildKeyId(1, 1);
            var keyWithoutData = new AppStateSyncKeyBuilder()
                    .keyId(new AppStateSyncKeyIdBuilder().keyId(keyId).build())
                    .build();
            h.rotation.handleKeyShare(0, List.of(keyWithoutData));
            assertTrue(h.store.appStateKeys().isEmpty(),
                    "negative response must not be stored as if it were a positive one");
        }

        /**
         * Asserts that receiving a key that was previously tracked as missing clears the
         * tracker entry and adds the key to the active store.
         */
        @Test
        @DisplayName("receiving a previously-missing key removes the tracker entry")
        void resolvesMissingKey() {
            var h = build();
            var keyId = SyncKeyUtils.buildKeyId(2, 5);
            var keyData = filled(32, 0x55);

            h.store.addMissingSyncKey(new MissingDeviceSyncKeyBuilder()
                    .keyId(keyId)
                    .timestamp(Instant.now())
                    .askedDevices(Set.of(0))
                    .build());
            assertTrue(h.store.findMissingSyncKey(keyId).isPresent(),
                    "precondition: key tracked as missing");

            h.rotation.handleKeyShare(0, List.of(syncKey(keyId, keyData)));

            assertFalse(h.store.findMissingSyncKey(keyId).isPresent(),
                    "missing-key tracker must be cleared once the key arrives");
            assertTrue(h.store.findWebAppStateKeyById(keyId).isPresent(),
                    "key must be in the active store after the share");
        }

        /**
         * Asserts that a batched share with multiple positive keys stores all of them.
         */
        @Test
        @DisplayName("a batched share with multiple keys stores all of them")
        void batchedShareStoresAll() {
            var h = build();
            var keyA = syncKey(SyncKeyUtils.buildKeyId(1, 1), filled(32, 0x11));
            var keyB = syncKey(SyncKeyUtils.buildKeyId(2, 1), filled(32, 0x22));
            var keyC = syncKey(SyncKeyUtils.buildKeyId(3, 1), filled(32, 0x33));
            h.rotation.handleKeyShare(0, List.of(keyA, keyB, keyC));
            assertEquals(3, h.store.appStateKeys().size());
        }

        /**
         * Asserts that an inbound key with no key id is silently skipped rather than
         * throwing.
         */
        @Test
        @DisplayName("a share carrying keys without key id is ignored without throwing")
        void absentKeyIdSkipped() {
            var h = build();
            var malformed = new AppStateSyncKeyBuilder()
                    .keyData(new AppStateSyncKeyDataBuilder().keyData(filled(32, 0x00)).build())
                    .build();
            h.rotation.handleKeyShare(0, List.of(malformed));
            assertTrue(h.store.appStateKeys().isEmpty(),
                    "share with absent key id must be a no-op, not throw");
        }
    }

    /**
     * Tests for {@link SyncKeyRotationService#getNewestKeyPair()}.
     */
    @Nested
    @DisplayName("getNewestKeyPair - delegates to SyncKeyUtils.findNewestKey")
    class GetNewestKeyPair {
        /**
         * Asserts that {@code null} is returned when the key store is empty.
         */
        @Test
        @DisplayName("empty key store returns null")
        void emptyReturnsNull() {
            var h = build();
            assertNull(h.rotation.getNewestKeyPair());
        }

        /**
         * Asserts that a single-key store returns that key regardless of epoch value.
         */
        @Test
        @DisplayName("single key is returned regardless of epoch")
        void singleKey() {
            var h = build();
            h.store.addWebAppStateKeys(List.of(
                    syncKey(SyncKeyUtils.buildKeyId(1, 7), filled(32, 0x42))));
            var newest = h.rotation.getNewestKeyPair();
            assertNotNull(newest);
            assertEquals(7, SyncKeyUtils.getKeyEpoch(newest));
        }

        /**
         * Asserts that the highest-epoch key wins across multiple stored keys.
         */
        @Test
        @DisplayName("highest-epoch key wins across multiple")
        void highestEpochWins() {
            var h = build();
            h.store.addWebAppStateKeys(List.of(
                    syncKey(SyncKeyUtils.buildKeyId(1, 1), filled(32, 0x11)),
                    syncKey(SyncKeyUtils.buildKeyId(1, 5), filled(32, 0x55)),
                    syncKey(SyncKeyUtils.buildKeyId(1, 3), filled(32, 0x33))));
            assertEquals(5, SyncKeyUtils.getKeyEpoch(h.rotation.getNewestKeyPair()));
        }
    }

    /**
     * Tests for {@link SyncKeyRotationService#getActiveKey(boolean)}.
     */
    @Nested
    @DisplayName("getActiveKey")
    class GetActiveKey {
        /**
         * Asserts that an empty key store throws regardless of the
         * {@code triggerRotation} flag.
         */
        @Test
        @DisplayName("throws IllegalStateException when no keys are present")
        void throwsOnEmpty() {
            var h = build();
            assertThrows(IllegalStateException.class, () -> h.rotation.getActiveKey(true));
            assertThrows(IllegalStateException.class, () -> h.rotation.getActiveKey(false));
        }

        /**
         * Asserts that a populated store returns the existing newest key when rotation is
         * suppressed.
         */
        @Test
        @DisplayName("returns the existing newest key when rotation is suppressed")
        void rotationSuppressedReturnsExisting() {
            var h = build();
            h.store.addWebAppStateKeys(List.of(
                    syncKey(SyncKeyUtils.buildKeyId(1, 7), filled(32, 0x42))));
            var active = h.rotation.getActiveKey(false);
            assertNotNull(active);
            assertEquals(7, SyncKeyUtils.getKeyEpoch(active));
        }
    }

    /**
     * Tests for {@link SyncKeyRotationService#ensureActiveKey(boolean)}.
     */
    @Nested
    @DisplayName("ensureActiveKey - read-through to getActiveKey")
    class EnsureActiveKey {
        /**
         * Asserts that the empty-store throw propagates through the read-through wrapper.
         */
        @Test
        @DisplayName("ensureActiveKey(false) on an empty store throws (delegation contract)")
        void throwsOnEmpty() {
            var h = build();
            assertThrows(IllegalStateException.class, () -> h.rotation.ensureActiveKey(false));
        }

        /**
         * Asserts that a populated store with rotation suppressed leaves the store
         * unchanged.
         */
        @Test
        @DisplayName("ensureActiveKey(false) is a no-op when a usable key already exists")
        void noopWhenKeyExists() {
            var h = build();
            h.store.addWebAppStateKeys(List.of(
                    syncKey(SyncKeyUtils.buildKeyId(1, 1), filled(32, 0x33))));
            h.rotation.ensureActiveKey(false);
            assertEquals(1, h.store.appStateKeys().size(),
                    "no rotation requested -> no new key generated");
        }
    }

    /**
     * Tests for {@link SyncKeyRotationService#logMissingKeysReceived()}.
     */
    @Nested
    @DisplayName("logMissingKeysReceived - gated on critical bootstrap state")
    class LogMissingKeysReceived {
        /**
         * Asserts that invoking the bootstrap-stage emitter against a fresh store does
         * not throw.
         *
         * @implNote
         * The emitted WAM event contents are covered by the WAM package tests; this test
         * only smoke-asserts that the bootstrap-state gate routes through cleanly.
         */
        @Test
        @DisplayName("invoking with an unbootstrapped critical_block collection does not throw")
        void smokeRun() {
            var h = build();
            h.rotation.logMissingKeysReceived();
        }
    }
}
