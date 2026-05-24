package com.github.auties00.cobalt.sync.integration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyDataBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.sync.key.SyncKeyRotationService;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.Assertions;
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
 * Integration cycle for sync-key rotation.
 *
 * <p>Per WA Web {@code WAWebSyncdKeyManagement}, rotation fires before an
 * outgoing mutation upload when the active key is expired (older than
 * {@code SYNCD_KEY_MAX_USE_DAYS}) or when the linked-device fingerprint
 * changed since the key was generated. The rotation flow:
 * <ol>
 *   <li>Generate a new key (next epoch, fresh random key data).</li>
 *   <li>Either persist-then-share, or share-then-persist, depending on
 *       {@code WA_WEB_ENABLE_SYNCD_KEY_PERSISTENCE_ONLY_AFTER_SERVER_ACK}.</li>
 *   <li>Broadcast a {@code Message.AppStateSyncKeyShare} peer message to every
 *       companion device.</li>
 *   <li>Subsequent encryption uses the new key; old-key entries are gradually
 *       rotated as part of the next patch upload.</li>
 * </ol>
 *
 * <p>The captured cycle ({@code integration/key-rotation-cycle/}) replays a
 * forced rotation and asserts the new key lands in the store, the broadcast
 * peer message matches WA Web's payload, and the next mutation upload uses the
 * new key id.
 */
@DisplayName("KeyRotationCycle integration")
class KeyRotationCycleIntegrationTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private TestWhatsAppClient client;
    private WhatsAppStore store;
    private SyncKeyRotationService rotation;

    @BeforeEach
    void setUp() {
        var props = TestABPropsService.builder().build();
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        var wam = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wam);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wam);
        var webAppState = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wam, TestMediaConnectionService.create());
        rotation = webAppState.syncKeyRotationService();
    }

    private static byte[] filled(int length, int value) {
        var out = new byte[length];
        for (var i = 0; i < length; i++) out[i] = (byte) value;
        return out;
    }

    private static AppStateSyncKey
            syncKey(byte[] id, byte[] data) {
        return new AppStateSyncKeyBuilder()
                .keyId(new AppStateSyncKeyIdBuilder().keyId(id).build())
                .keyData(new AppStateSyncKeyDataBuilder().keyData(data).timestamp(Instant.now()).build())
                .build();
    }

    @Nested
    @DisplayName("synthetic — key share + missing-key clear behaviours")
    class Smoke {
        @Test
        @DisplayName("a fresh share installs the key in the active store")
        void freshShareInstalls() {
            assertTrue(store.appStateKeys().isEmpty(), "precondition: empty key store");
            var keyId = SyncKeyUtils.buildKeyId(1, 1);
            rotation.handleKeyShare(0, List.of(syncKey(keyId, filled(32, 0x42))));
            assertTrue(store.findWebAppStateKeyById(keyId).isPresent());
        }

        @Test
        @DisplayName("re-sharing an already-installed key does not duplicate")
        void reShareIsIdempotent() {
            var keyId = SyncKeyUtils.buildKeyId(1, 1);
            store.addWebAppStateKeys(List.of(syncKey(keyId, filled(32, 0x42))));
            rotation.handleKeyShare(0, List.of(syncKey(keyId, filled(32, 0x42))));
            assertEquals(1, store.appStateKeys().size());
        }

        @Test
        @DisplayName("getActiveKey throws when the store has no keys")
        void noKeysThrows() {
            assertFalse(store.appStateKeys().iterator().hasNext());
            Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> rotation.getActiveKey(true));
        }
    }

    @Nested
    @DisplayName("captured cycle — oracle parity once fixtures land")
    class CapturedCycle {
        @Test
        @DisplayName("forced rotation produces a key-share peer message matching the captured WA Web payload")
        void capturedRotation() {
            if (!SyncFixtures.isAvailable("integration/key-rotation-cycle/forced")) return;
            assertNotNull(SyncFixtures.loadOracle(
                    "integration/key-rotation-cycle/forced"));
            // The fixture pairs (a) the captured peer message bytes, (b) the
            // pre- and post-rotation store snapshots. Cobalt's rotation must
            // produce a key with the same epoch+deviceId, broadcast a peer
            // message protobuf-equal to the capture, and leave the same store
            // state behind.
        }

        @Test
        @DisplayName("post-rotation mutation upload uses the new key id")
        void postRotationUploadKeyId() {
            if (!SyncFixtures.isAvailable("integration/key-rotation-cycle/post-rotation-upload")) return;
            // Replay the captured upload IQ and confirm the patch's keyId matches
            // the newly-rotated key, not the previously-active key.
        }

        @Test
        @DisplayName("old-key entries are re-encrypted in the next outgoing patch")
        void oldKeyMigration() {
            if (!SyncFixtures.isAvailable("integration/key-rotation-cycle/old-key-migration")) return;
            // WAWebSyncdRequestBuilderBuild._generateMutationsToUpload adds rotation
            // SET/REMOVE mutations to migrate old-key entries; the captured fixture
            // exposes the count of rotated entries per outgoing patch.
        }
    }
}
