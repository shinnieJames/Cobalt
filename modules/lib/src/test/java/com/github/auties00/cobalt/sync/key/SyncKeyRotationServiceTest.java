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
 * Tests for {@link SyncKeyRotationService} — Cobalt's adapter for
 * {@code WAWebSyncdKeyManagement}, {@code WAWebSyncdHandleKeyShare}, and the
 * {@code WAWebSyncdRotateKey} family of WA Web modules.
 *
 * <p>The full rotation flow (timer-driven re-keying and CDN-mediated key-share
 * peer messages) needs a live network and is exercised by the Phase 9 integration
 * cycles. These tests pin down the synchronous, store-observable paths:
 * <ul>
 *   <li>{@code handleKeyShare} — store insertion / dedup / missing-key removal
 *       / divergent-key-data logging.</li>
 *   <li>{@code getNewestKeyPair} — delegates to {@link SyncKeyUtils#findNewestKey}.</li>
 *   <li>{@code getActiveKey} — throws on empty key store; returns existing key when
 *       rotation is suppressed.</li>
 * </ul>
 */
@DisplayName("SyncKeyRotationService")
class SyncKeyRotationServiceTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private record Harness(TestWhatsAppClient client, WhatsAppStore store, SyncKeyRotationService rotation) {
    }

    private static Harness build() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        var client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wam);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wam);
        var webAppState = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wam);
        return new Harness(client, store, webAppState.syncKeyRotationService());
    }

    private static byte[] filled(int length, int value) {
        var out = new byte[length];
        for (var i = 0; i < length; i++) out[i] = (byte) value;
        return out;
    }

    private static AppStateSyncKey syncKey(byte[] id, byte[] data) {
        return new AppStateSyncKeyBuilder()
                .keyId(new AppStateSyncKeyIdBuilder().keyId(id).build())
                .keyData(new AppStateSyncKeyDataBuilder().keyData(data).timestamp(Instant.now()).build())
                .build();
    }

    @Nested
    @DisplayName("handleKeyShare — store / dedup / missing-key removal")
    class HandleKeyShare {
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

        @Test
        @DisplayName("receiving a previously-missing key removes the tracker entry")
        void resolvesMissingKey() {
            var h = build();
            var keyId = SyncKeyUtils.buildKeyId(2, 5);
            var keyData = filled(32, 0x55);

            // Track the key as missing
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

    @Nested
    @DisplayName("getNewestKeyPair — delegates to SyncKeyUtils.findNewestKey")
    class GetNewestKeyPair {
        @Test
        @DisplayName("empty key store returns null")
        void emptyReturnsNull() {
            var h = build();
            assertNull(h.rotation.getNewestKeyPair());
        }

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

    @Nested
    @DisplayName("getActiveKey")
    class GetActiveKey {
        @Test
        @DisplayName("throws IllegalStateException when no keys are present")
        void throwsOnEmpty() {
            var h = build();
            assertThrows(IllegalStateException.class, () -> h.rotation.getActiveKey(true));
            assertThrows(IllegalStateException.class, () -> h.rotation.getActiveKey(false));
        }

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

    @Nested
    @DisplayName("ensureActiveKey — read-through to getActiveKey")
    class EnsureActiveKey {
        @Test
        @DisplayName("ensureActiveKey(false) on an empty store throws (delegation contract)")
        void throwsOnEmpty() {
            var h = build();
            assertThrows(IllegalStateException.class, () -> h.rotation.ensureActiveKey(false));
        }

        @Test
        @DisplayName("ensureActiveKey(false) is a no-op when a usable key already exists")
        void noopWhenKeyExists() {
            var h = build();
            h.store.addWebAppStateKeys(List.of(
                    syncKey(SyncKeyUtils.buildKeyId(1, 1), filled(32, 0x33))));
            h.rotation.ensureActiveKey(false);
            assertEquals(1, h.store.appStateKeys().size(),
                    "no rotation requested → no new key generated");
        }
    }

    @Nested
    @DisplayName("logMissingKeysReceived — gated on critical bootstrap state")
    class LogMissingKeysReceived {
        @Test
        @DisplayName("invoking with an unbootstrapped critical_block collection does not throw")
        void smokeRun() {
            var h = build();
            // Fresh store has critical_block bootstrapped=false; the method emits a WAM event
            // and returns. We only assert it doesn't throw — the WAM event contents are covered
            // by the WAM package tests.
            h.rotation.logMissingKeysReceived();
        }
    }
}
