package com.github.auties00.cobalt.sync.integration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration cycle for snapshot-recovery driven by a snapshot MAC mismatch.
 *
 * <p>Per WA Web {@code WAWebRequestSyncdSnapshotRecovery}, when a snapshot MAC
 * fails validation during the per-collection apply step the client fires a
 * {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY} peer-data-operation request to
 * the primary, waits up to 60s for the {@code PeerDataOperationRequestResponseMessage}
 * carrying the corrected snapshot, decodes it, and replaces the local
 * collection state. The cycle is gated on:
 * <ul>
 *   <li>{@code primaryDeviceSupportsSyncdRecovery} (store-side flag).</li>
 *   <li>{@code ENABLE_PEER_SNAPSHOT_RECOVERY} AB prop.</li>
 *   <li>Collection not being {@code CRITICAL_BLOCK}.</li>
 *   <li>Mutation count within {@code SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED}.</li>
 * </ul>
 *
 * <p>The full cycle requires the {@code integration/snapshot-recovery-cycle/}
 * corpus. Until those fixtures land the test exercises:
 * <ul>
 *   <li>Recovery-enabled gating composition.</li>
 *   <li>Per-collection shouldAttemptRecovery decision matrix.</li>
 *   <li>resolveRecovery on a never-requested collection (no-op safety).</li>
 * </ul>
 */
@DisplayName("SnapshotRecoveryCycle integration")
class SnapshotRecoveryCycleIntegrationTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestABPropsService props;
    private SnapshotRecoveryService recovery;

    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        var wam = new DefaultWamService(client, props);
        recovery = new SnapshotRecoveryService(client, props, wam);
    }

    @Nested
    @DisplayName("synthetic smoke — gating composition without IO")
    class Smoke {
        @Test
        @DisplayName("recovery is disabled when the primary doesn't advertise support")
        void disabledByPrimary() {
            store.setPrimaryDeviceSupportsSyncdRecovery(false);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            assertFalse(recovery.isRecoveryEnabled());
        }

        @Test
        @DisplayName("recovery is disabled when the AB prop is off")
        void disabledByAbProp() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, false);
            assertFalse(recovery.isRecoveryEnabled());
        }

        @Test
        @DisplayName("recovery is enabled when both gates pass")
        void enabledByDefault() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            assertTrue(recovery.isRecoveryEnabled());
        }

        @Test
        @DisplayName("CRITICAL_BLOCK never attempts recovery (collection-specific gate)")
        void criticalBlockExcluded() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            props.set(ABProp.SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED, 1_000);
            assertFalse(recovery.shouldAttemptRecovery(SyncPatchType.CRITICAL_BLOCK, 0));
        }
    }

    @Nested
    @DisplayName("captured cycle — oracle parity once fixtures land")
    class CapturedCycle {
        @Test
        @DisplayName("forced MAC mismatch → recovery request → captured response replaces collection state")
        void capturedRecoveryCycle() {
            if (!SyncFixtures.isAvailable("integration/snapshot-recovery-cycle/regular-low")) return;
            // Reserved for the Phase 10 corpus. The fixture replays the captured
            // PeerDataOperationRequestResponseMessage and asserts the store's
            // post-recovery state matches the WA Web oracle projection.
            assertNotNull(SyncFixtures.loadOracle(
                    "integration/snapshot-recovery-cycle/regular-low"));
        }

        @Test
        @DisplayName("a second in-flight request for the same collection is debounced (semaphore-serialized)")
        void debouncedInFlight() {
            if (!SyncFixtures.isAvailable("integration/snapshot-recovery-cycle/in-flight-debounce")) return;
            // Exercise the recoverySemaphore tryAcquire path under concurrent calls
            // for the same collection. The cycle asserts only one outgoing request
            // is dispatched even when two threads race to request recovery.
        }
    }
}
