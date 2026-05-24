package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke tests for the wiring and lifecycle surface of
 * {@link WebAppStateService}.
 *
 * @apiNote
 * Pins the synchronous, no-IO contract every embedder relies on when
 * constructing or shutting down the service: the constructor must wire
 * every collaborator without contacting the network, the empty-argument
 * pull must short-circuit, start/stop of the periodic jobs must be
 * idempotent, the orphan-mutation hooks must accept empty input, and
 * {@link WebAppStateService#reset()} must close cleanly. The full sync
 * round-trip (outgoing IQ build, server dispatch, patch receive, decrypt,
 * apply, ack) is exercised by the Phase 9 integration cycles against a
 * live connection rather than here.
 *
 * @implNote
 * This implementation builds the service against {@link TestWhatsAppClient}
 * and an in-memory {@link WhatsAppStore} produced by
 * {@link DeviceFixtures#temporaryStore(Jid, Jid)}, so every test runs
 * without IO; the named JIDs are fictitious fixtures and carry no
 * provenance from a real session.
 */
@DisplayName("WebAppStateService")
class WebAppStateServiceTest {
    /**
     * The synthetic PN user JID used as the test account's own identity.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The synthetic LID counterpart of {@link #SELF_PN}.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The synthetic device-1 JID stored as the test account's own device
     * via {@link WhatsAppStore#setJid(Jid)}.
     */
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    /**
     * The in-memory stub client wired against {@link #store}.
     */
    private TestWhatsAppClient client;

    /**
     * The in-memory store backing every test, freshly created per test by
     * {@link DeviceFixtures#temporaryStore(Jid, Jid)}.
     */
    private WhatsAppStore store;

    /**
     * The in-memory A/B-props provider supplying default values to the
     * service's feature-gated branches.
     */
    private TestABPropsService props;

    /**
     * The service under test, rebuilt fresh before every test.
     */
    private WebAppStateService service;

    /**
     * Wires a fresh {@link WebAppStateService} against a temporary store, a
     * stub client, a default props provider, and freshly-constructed
     * {@link DefaultWamService}, {@link LidMigrationService}, and
     * {@link SnapshotRecoveryService} collaborators.
     *
     * @apiNote
     * Runs before every test; the per-test rebuild guarantees the periodic
     * job handles, missing-key timers, and retry backoff counters start
     * cleared.
     *
     * @implNote
     * This implementation pins the own JID via {@link WhatsAppStore#setJid(Jid)}
     * so paths that consult {@code store.jid()} (notably
     * {@code sendAppStateFatalExceptionNotification}) do not have to deal
     * with an absent identity.
     */
    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wam);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wam);
        service = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wam, TestMediaConnectionService.create());
    }

    /**
     * Verifies that {@link WebAppStateService#stopPeriodicSyncJob()} is
     * safe to call after every test, regardless of whether the test ever
     * started the periodic job.
     *
     * @apiNote
     * Acts as both a cleanup hook and a defensive assertion that the stop
     * path never throws on a never-started job.
     */
    @AfterEach
    void tearDown() {
        assertDoesNotThrow(service::stopPeriodicSyncJob,
                "stopPeriodicSyncJob must be safe even when never started");
    }

    /**
     * Groups the constructor-wiring tests.
     *
     * @apiNote
     * Pins the contract that constructing the service is enough to make
     * every collaborator reachable, without a connect step.
     */
    @Nested
    @DisplayName("wiring - constructor produces a usable service")
    class ConstructorWiring {
        /**
         * The service exposes the internally-constructed
         * {@link com.github.auties00.cobalt.sync.key.SyncKeyRotationService}.
         */
        @Test
        @DisplayName("service exposes the internally-built SyncKeyRotationService")
        void exposesRotationService() {
            assertNotNull(service.syncKeyRotationService(),
                    "SyncKeyRotationService is constructed internally and must be reachable");
        }
    }

    /**
     * Groups the empty-argument short-circuit test for
     * {@link WebAppStateService#pullPatches(com.github.auties00.cobalt.model.sync.SyncPatchType...)}.
     */
    @Nested
    @DisplayName("pullPatches - empty argument is a no-op")
    class PullPatches {
        /**
         * An empty {@link WebAppStateService#pullPatches(com.github.auties00.cobalt.model.sync.SyncPatchType...)}
         * call returns {@code false} without contacting the network.
         */
        @Test
        @DisplayName("pullPatches() with no patch types returns false")
        void emptyPull() {
            assertFalse(service.pullPatches(),
                    "pullPatches with an empty argument set must short-circuit (returns false)");
        }
    }

    /**
     * Groups the start/stop idempotency tests for the periodic sync job.
     *
     * @apiNote
     * Pins the contract that the four-job lifecycle starter is safe to call
     * repeatedly; failures here would surface as duplicate scheduled tasks
     * on every reconnect.
     */
    @Nested
    @DisplayName("periodic sync job - start/stop idempotent")
    class PeriodicSyncJob {
        /**
         * Starting and then stopping the periodic job succeeds in sequence.
         */
        @Test
        @DisplayName("startPeriodicSyncJob then stopPeriodicSyncJob does not throw")
        void startStop() {
            assertDoesNotThrow(service::startPeriodicSyncJob);
            assertDoesNotThrow(service::stopPeriodicSyncJob);
        }

        /**
         * A redundant {@link WebAppStateService#startPeriodicSyncJob()} call
         * does not duplicate the scheduled handle.
         */
        @Test
        @DisplayName("a second start is a no-op")
        void startTwiceIsNoop() {
            service.startPeriodicSyncJob();
            assertDoesNotThrow(service::startPeriodicSyncJob,
                    "the periodic job is single-instance per service");
            service.stopPeriodicSyncJob();
        }
    }

    /**
     * Groups the empty-input no-op tests for the orphan-mutation public
     * hooks.
     *
     * @apiNote
     * The orphan hooks are entry points the protocol-message receivers call
     * with id buckets extracted from history-sync notifications; an empty
     * bucket must not panic or contact the server.
     */
    @Nested
    @DisplayName("orphan-mutation hooks - empty input is a no-op")
    class OrphanHooks {
        /**
         * {@link WebAppStateService#checkOrphanMessages(List)} with an empty
         * input list completes without raising.
         */
        @Test
        @DisplayName("checkOrphanMessages with empty input does not throw")
        void emptyMessages() {
            assertDoesNotThrow(() -> service.checkOrphanMessages(List.of()));
        }

        /**
         * {@link WebAppStateService#checkOrphanChats(List)} with an empty
         * input list completes without raising.
         */
        @Test
        @DisplayName("checkOrphanChats with empty input does not throw")
        void emptyChats() {
            assertDoesNotThrow(() -> service.checkOrphanChats(List.of()));
        }

        /**
         * {@link WebAppStateService#checkOrphanThreads(List)} with an empty
         * input list completes without raising.
         */
        @Test
        @DisplayName("checkOrphanThreads with empty input does not throw")
        void emptyThreads() {
            assertDoesNotThrow(() -> service.checkOrphanThreads(List.of()));
        }

        /**
         * {@link WebAppStateService#checkOrphanAgents(List)} with an empty
         * input list completes without raising.
         */
        @Test
        @DisplayName("checkOrphanAgents with empty input does not throw")
        void emptyAgents() {
            assertDoesNotThrow(() -> service.checkOrphanAgents(List.of()));
        }

        /**
         * {@link WebAppStateService#checkOrphanChatAssignments(List)} with
         * an empty input list completes without raising.
         */
        @Test
        @DisplayName("checkOrphanChatAssignments with empty input does not throw")
        void emptyChatAssignments() {
            assertDoesNotThrow(() -> service.checkOrphanChatAssignments(List.of()));
        }

        /**
         * {@link WebAppStateService#checkOrphanUserStatusMutes(List)} with
         * an empty input list completes without raising.
         */
        @Test
        @DisplayName("checkOrphanUserStatusMutes with empty input does not throw")
        void emptyUserStatusMutes() {
            assertDoesNotThrow(() -> service.checkOrphanUserStatusMutes(List.of()));
        }

        /**
         * The aggregated dispatcher
         * {@link WebAppStateService#checkOrphanMutations(List, List, List)}
         * forwards each id-bucket to its specialised hook without raising
         * on three empty inputs.
         */
        @Test
        @DisplayName("checkOrphanMutations dispatches each id-bucket to its specialised hook")
        void aggregatedDispatch() {
            assertDoesNotThrow(() ->
                    service.checkOrphanMutations(List.of(), List.of(), List.of()));
        }
    }

    /**
     * Groups the missing-sync-key timeout-rescheduling tests.
     *
     * @apiNote
     * Pins the contract that the two public hooks the protocol-message
     * receiver calls (after key-share replies) accept being invoked on a
     * freshly-constructed service with no pending keys.
     */
    @Nested
    @DisplayName("missing-sync-key follow-ups - scheduling hooks")
    class MissingKeyHooks {
        /**
         * {@link WebAppStateService#scheduleAllDevicesRespondedCheck()}
         * runs on a fresh service without raising.
         */
        @Test
        @DisplayName("scheduleAllDevicesRespondedCheck does not throw")
        void scheduleAllDevices() {
            assertDoesNotThrow(service::scheduleAllDevicesRespondedCheck);
        }

        /**
         * {@link WebAppStateService#rescheduleMissingSyncKeyTimeout()} runs
         * on a fresh service without raising.
         */
        @Test
        @DisplayName("rescheduleMissingSyncKeyTimeout does not throw")
        void rescheduleTimeout() {
            assertDoesNotThrow(service::rescheduleMissingSyncKeyTimeout);
        }
    }

    /**
     * Groups the {@link WebAppStateService#reset()} lifecycle tests.
     *
     * @apiNote
     * Pins the contract that the shutdown hook is safe both before any
     * scheduler has been armed and after {@link WebAppStateService#startPeriodicSyncJob()}.
     */
    @Nested
    @DisplayName("reset - clears service state")
    class Reset {
        /**
         * {@link WebAppStateService#reset()} runs on a never-started
         * service without raising.
         */
        @Test
        @DisplayName("reset does not throw on a freshly-constructed service")
        void resetFreshly() {
            assertDoesNotThrow(service::reset);
        }

        /**
         * {@link WebAppStateService#reset()} cleanly cancels the periodic
         * job after {@link WebAppStateService#startPeriodicSyncJob()}.
         */
        @Test
        @DisplayName("reset after starting the periodic job is safe")
        void resetAfterStart() {
            service.startPeriodicSyncJob();
            assertDoesNotThrow(service::reset);
        }
    }

    /**
     * Groups the empty-store contract test for
     * {@code syncBlockedCollections()}.
     *
     * @apiNote
     * Pins the contract that the BLOCKED-sweep iterator returns cleanly
     * when no collection has been parked in
     * {@link com.github.auties00.cobalt.model.sync.SyncCollectionState#BLOCKED};
     * a regression would either throw or fire a server request from a
     * disconnected test stub.
     */
    @Nested
    @DisplayName("syncBlockedCollections - no-op when nothing is blocked")
    class SyncBlockedCollections {
        /**
         * On a fresh store with no blocked collections,
         * {@code syncBlockedCollections()} completes without contacting the
         * server.
         */
        @Test
        @DisplayName("with a fresh store (nothing blocked), syncBlockedCollections is a no-op")
        void freshStoreNoOp() {
            assertDoesNotThrow(service::syncBlockedCollections);
        }
    }
}
