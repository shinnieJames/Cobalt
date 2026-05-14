package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecovery;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecoveryBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SnapshotRecoveryService} — Cobalt's adapter for
 * {@code WAWebRequestSyncdSnapshotRecovery} and
 * {@code WAWebSyncdSnapshotRecoveryGatingUtils}.
 *
 * <p>The actual recovery request/response cycle requires a network round-trip
 * with the primary device and is exercised by the Phase 9 integration cycle.
 * These tests pin the synchronous, store-observable gates:
 * <ul>
 *   <li>{@code isRecoveryEnabled} requires both
 *       {@code primaryDeviceSupportsSyncdRecovery} (store) AND
 *       {@code ENABLE_PEER_SNAPSHOT_RECOVERY} (AB prop).</li>
 *   <li>{@code shouldAttemptRecovery} additionally excludes
 *       {@code CRITICAL_BLOCK} and clamps on
 *       {@code SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED}.</li>
 *   <li>{@code updatePrimaryDeviceSupportsSyncdRecovery} flips the store
 *       flag observably.</li>
 *   <li>{@code resolveRecovery} on a collection with no pending request is a
 *       no-op (logs FINE, does not throw).</li>
 * </ul>
 */
@DisplayName("SnapshotRecoveryService")
class SnapshotRecoveryServiceTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    private WhatsAppStore store;
    private TestABPropsService props;
    private SnapshotRecoveryService recovery;

    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        recovery = new SnapshotRecoveryService(client, props, wam);
    }

    @Nested
    @DisplayName("isRecoveryEnabled — gated on both primary support + AB prop")
    class IsRecoveryEnabled {
        @Test
        @DisplayName("primary unsupported → disabled regardless of AB prop")
        void primaryUnsupportedDisabled() {
            store.setPrimaryDeviceSupportsSyncdRecovery(false);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            assertFalse(recovery.isRecoveryEnabled());
        }

        @Test
        @DisplayName("AB prop disabled → disabled regardless of primary support")
        void abPropDisabled() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, false);
            assertFalse(recovery.isRecoveryEnabled());
        }

        @Test
        @DisplayName("both conditions true → enabled")
        void bothTrueEnables() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            assertTrue(recovery.isRecoveryEnabled());
        }
    }

    @Nested
    @DisplayName("shouldAttemptRecovery — composes isRecoveryEnabled with collection + mutation gates")
    class ShouldAttemptRecovery {
        @BeforeEach
        void enableRecovery() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            props.set(ABProp.SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED, 100);
        }

        @Test
        @DisplayName("CRITICAL_BLOCK collection is never recovered (returns false)")
        void criticalBlockExcluded() {
            assertFalse(recovery.shouldAttemptRecovery(SyncPatchType.CRITICAL_BLOCK, 0));
        }

        @Test
        @DisplayName("non-critical collection within mutation budget returns true")
        void withinBudgetReturnsTrue() {
            assertTrue(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 50));
        }

        @Test
        @DisplayName("non-critical collection at the boundary mutation count returns true")
        void atBoundaryReturnsTrue() {
            assertTrue(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 100),
                    "WAWebSyncdSnapshotRecoveryGatingUtils.d: n > a is strict, equal allowed");
        }

        @Test
        @DisplayName("non-critical collection past the mutation budget returns false")
        void pastBudgetReturnsFalse() {
            assertFalse(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 101));
        }

        @Test
        @DisplayName("disabled recovery returns false regardless of mutation count")
        void disabledRecoveryShortCircuits() {
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, false);
            assertFalse(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 0));
        }
    }

    @Nested
    @DisplayName("updatePrimaryDeviceSupportsSyncdRecovery — flips the store flag")
    class UpdatePrimarySupport {
        @Test
        @DisplayName("setting to true is observable through isRecoveryEnabled (with AB prop on)")
        void flipsObservably() {
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            recovery.updatePrimaryDeviceSupportsSyncdRecovery(false);
            assertFalse(recovery.isRecoveryEnabled());
            recovery.updatePrimaryDeviceSupportsSyncdRecovery(true);
            assertTrue(recovery.isRecoveryEnabled());
        }
    }

    @Nested
    @DisplayName("resolveRecovery — no pending request is a no-op")
    class ResolveRecovery {
        @Test
        @DisplayName("resolving a collection with no pending request does not throw")
        void noPendingNoop() {
            var snapshot = new SyncdSnapshotRecoveryBuilder().build();
            assertDoesNotThrow(() -> recovery.resolveRecovery(SyncPatchType.REGULAR, snapshot),
                    "WAWebRequestSyncdSnapshotRecovery.resolveRecoveryPromise: missing key logs and returns");
        }

        @Test
        @DisplayName("resolving with null snapshot does not throw at the resolveRecovery layer")
        void nullSnapshotIsAcceptedByResolveLayer() {
            // The resolveRecovery layer is just a dispatch — null payload semantics are the
            // caller's concern (the protocol decoder rejects malformed payloads upstream).
            assertDoesNotThrow(() ->
                    recovery.resolveRecovery(SyncPatchType.REGULAR, (SyncdSnapshotRecovery) null));
        }
    }
}
