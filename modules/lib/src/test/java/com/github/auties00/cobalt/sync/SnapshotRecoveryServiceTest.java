package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecovery;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecoveryBuilder;
import com.github.auties00.cobalt.model.props.ABProp;
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
 * Pins the synchronous, store-observable gating behaviour of
 * {@link SnapshotRecoveryService} against WhatsApp Web's
 * {@code WAWebRequestSyncdSnapshotRecovery} and
 * {@code WAWebSyncdSnapshotRecoveryGatingUtils}.
 *
 * @apiNote The actual recovery request and response cycle requires a
 * network round-trip with the primary device and is exercised by the
 * Phase 9 integration cycle. This suite only covers the four
 * synchronous, observable gates: AB-prop combination in
 * {@link SnapshotRecoveryService#isRecoveryEnabled()},
 * collection plus mutation-count gating in
 * {@link SnapshotRecoveryService#shouldAttemptRecovery},
 * the store-flag flip done by
 * {@link SnapshotRecoveryService#updatePrimaryDeviceSupportsSyncdRecovery},
 * and the no-pending-future no-op behaviour of
 * {@link SnapshotRecoveryService#resolveRecovery}.
 *
 * @implNote This implementation builds a temporary store via
 * {@link DeviceFixtures#temporaryStore(Jid, Jid)} and a
 * {@link TestABPropsService} so each gate can be flipped
 * independently without booting the rest of the syncd stack; the same
 * pattern is reused across every nested block.
 */
@DisplayName("SnapshotRecoveryService")
class SnapshotRecoveryServiceTest {
    /**
     * The local user's PN-form JID baked into the test store.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The local user's LID-form JID baked into the test store.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The temporary in-memory store, freshly created per test.
     */
    private WhatsAppStore store;

    /**
     * The mutable {@link TestABPropsService} feeding the gates.
     */
    private TestABPropsService props;

    /**
     * The system under test, freshly created per test.
     */
    private SnapshotRecoveryService recovery;

    /**
     * Builds a fresh store, AB-props service, and recovery service
     * before every test so the gating state is hermetic across runs.
     *
     * @apiNote JUnit-managed setup; not invoked manually from the
     * tests.
     */
    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        recovery = new SnapshotRecoveryService(client, props, wam);
    }

    /**
     * Pins the truth table for
     * {@link SnapshotRecoveryService#isRecoveryEnabled()}.
     */
    @Nested
    @DisplayName("isRecoveryEnabled -- gated on both primary support + AB prop")
    class IsRecoveryEnabled {
        /**
         * Primary unsupported short-circuits the AB-prop check.
         */
        @Test
        @DisplayName("primary unsupported is disabled regardless of AB prop")
        void primaryUnsupportedDisabled() {
            store.setPrimaryDeviceSupportsSyncdRecovery(false);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            assertFalse(recovery.isRecoveryEnabled());
        }

        /**
         * AB-prop disabled short-circuits even when the primary
         * supports recovery.
         */
        @Test
        @DisplayName("AB prop disabled is disabled regardless of primary support")
        void abPropDisabled() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, false);
            assertFalse(recovery.isRecoveryEnabled());
        }

        /**
         * Recovery is only enabled when both gates are on.
         */
        @Test
        @DisplayName("both conditions true enables recovery")
        void bothTrueEnables() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            assertTrue(recovery.isRecoveryEnabled());
        }
    }

    /**
     * Pins the truth table for
     * {@link SnapshotRecoveryService#shouldAttemptRecovery}.
     */
    @Nested
    @DisplayName("shouldAttemptRecovery -- composes isRecoveryEnabled with collection + mutation gates")
    class ShouldAttemptRecovery {
        /**
         * Enables the global gate and sets the mutation budget so
         * each test can vary just the dimension it cares about.
         *
         * @apiNote JUnit-managed nested setup; runs after the outer
         * {@link SnapshotRecoveryServiceTest#setUp()}.
         */
        @BeforeEach
        void enableRecovery() {
            store.setPrimaryDeviceSupportsSyncdRecovery(true);
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, true);
            props.set(ABProp.SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED, 100);
        }

        /**
         * The {@link SyncPatchType#CRITICAL_BLOCK} collection is
         * always rejected.
         */
        @Test
        @DisplayName("CRITICAL_BLOCK collection is never recovered (returns false)")
        void criticalBlockExcluded() {
            assertFalse(recovery.shouldAttemptRecovery(SyncPatchType.CRITICAL_BLOCK, 0));
        }

        /**
         * A non-critical collection well within the budget is
         * accepted.
         */
        @Test
        @DisplayName("non-critical collection within mutation budget returns true")
        void withinBudgetReturnsTrue() {
            assertTrue(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 50));
        }

        /**
         * The boundary mutation count is treated as inclusive,
         * matching the WA Web {@code n > a} strict-greater check.
         */
        @Test
        @DisplayName("non-critical collection at the boundary mutation count returns true")
        void atBoundaryReturnsTrue() {
            assertTrue(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 100),
                    "WAWebSyncdSnapshotRecoveryGatingUtils.d: n > a is strict, equal allowed");
        }

        /**
         * One mutation past the budget is rejected.
         */
        @Test
        @DisplayName("non-critical collection past the mutation budget returns false")
        void pastBudgetReturnsFalse() {
            assertFalse(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 101));
        }

        /**
         * Disabling the global gate short-circuits before the
         * mutation-count check.
         */
        @Test
        @DisplayName("disabled recovery returns false regardless of mutation count")
        void disabledRecoveryShortCircuits() {
            props.set(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY, false);
            assertFalse(recovery.shouldAttemptRecovery(SyncPatchType.REGULAR, 0));
        }
    }

    /**
     * Pins the store-flag write done by
     * {@link SnapshotRecoveryService#updatePrimaryDeviceSupportsSyncdRecovery(boolean)}.
     */
    @Nested
    @DisplayName("updatePrimaryDeviceSupportsSyncdRecovery -- flips the store flag")
    class UpdatePrimarySupport {
        /**
         * Flipping the flag changes the result of
         * {@link SnapshotRecoveryService#isRecoveryEnabled()} once the
         * AB-prop is on.
         */
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

    /**
     * Pins the no-op behaviour of
     * {@link SnapshotRecoveryService#resolveRecovery} when no future
     * is in flight for the supplied collection.
     */
    @Nested
    @DisplayName("resolveRecovery -- no pending request is a no-op")
    class ResolveRecovery {
        /**
         * Resolving a collection with no pending future logs at
         * {@code FINE} and returns silently.
         */
        @Test
        @DisplayName("resolving a collection with no pending request does not throw")
        void noPendingNoop() {
            var snapshot = new SyncdSnapshotRecoveryBuilder().build();
            assertDoesNotThrow(() -> recovery.resolveRecovery(SyncPatchType.REGULAR, snapshot),
                    "WAWebRequestSyncdSnapshotRecovery.resolveRecoveryPromise: missing key logs and returns");
        }

        /**
         * A {@code null} snapshot is accepted at the dispatch layer;
         * payload validation is the protocol decoder's
         * responsibility.
         *
         * @implNote {@link SnapshotRecoveryService#resolveRecovery} is
         * a thin dispatch helper, not a payload validator.
         */
        @Test
        @DisplayName("resolving with null snapshot does not throw at the resolveRecovery layer")
        void nullSnapshotIsAcceptedByResolveLayer() {
            assertDoesNotThrow(() ->
                    recovery.resolveRecovery(SyncPatchType.REGULAR, (SyncdSnapshotRecovery) null));
        }
    }
}
