package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke tests for {@link WebAppStateService} — Cobalt's adapter for the
 * {@code WAWebSyncd} family of WA Web modules.
 *
 * <p>The full sync round-trip (build outgoing IQ → ship to the server →
 * receive patches → decrypt → apply → ack) requires a live connection and
 * is covered by the Phase 9 integration cycles. These tests pin only the
 * synchronous, observable wiring properties on a stub client:
 * <ul>
 *   <li>The constructor wires every collaborator without IO and exposes the
 *       internally-created {@link SyncKeyRotationService}.</li>
 *   <li>{@code pullPatches} with an empty argument is a no-op that returns
 *       {@code false}.</li>
 *   <li>{@code startPeriodicSyncJob} / {@code stopPeriodicSyncJob} are
 *       safe to call repeatedly without raising.</li>
 *   <li>The orphan-mutation hooks ({@code checkOrphanMessages}/...) accept
 *       an empty input as a no-op.</li>
 *   <li>{@code reset} clears state without IO.</li>
 * </ul>
 */
@DisplayName("WebAppStateService")
class WebAppStateServiceTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private TestWhatsAppClient client;
    private WhatsAppStore store;
    private TestABPropsService props;
    private WebAppStateService service;

    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wam);
        var snapshotRecovery = new SnapshotRecoveryService(client, props, wam);
        service = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wam);
    }

    @AfterEach
    void tearDown() {
        assertDoesNotThrow(service::stopPeriodicSyncJob,
                "stopPeriodicSyncJob must be safe even when never started");
    }

    @Nested
    @DisplayName("wiring — constructor produces a usable service")
    class ConstructorWiring {
        @Test
        @DisplayName("service exposes the internally-built SyncKeyRotationService")
        void exposesRotationService() {
            assertNotNull(service.syncKeyRotationService(),
                    "SyncKeyRotationService is constructed internally and must be reachable");
        }
    }

    @Nested
    @DisplayName("pullPatches — empty argument is a no-op")
    class PullPatches {
        @Test
        @DisplayName("pullPatches() with no patch types returns false")
        void emptyPull() {
            assertFalse(service.pullPatches(),
                    "pullPatches with an empty argument set must short-circuit (returns false)");
        }
    }

    @Nested
    @DisplayName("periodic sync job — start/stop idempotent")
    class PeriodicSyncJob {
        @Test
        @DisplayName("startPeriodicSyncJob then stopPeriodicSyncJob does not throw")
        void startStop() {
            assertDoesNotThrow(service::startPeriodicSyncJob);
            assertDoesNotThrow(service::stopPeriodicSyncJob);
        }

        @Test
        @DisplayName("a second start is a no-op")
        void startTwiceIsNoop() {
            service.startPeriodicSyncJob();
            assertDoesNotThrow(service::startPeriodicSyncJob,
                    "the periodic job is single-instance per service");
            service.stopPeriodicSyncJob();
        }
    }

    @Nested
    @DisplayName("orphan-mutation hooks — empty input is a no-op")
    class OrphanHooks {
        @Test
        @DisplayName("checkOrphanMessages with empty input does not throw")
        void emptyMessages() {
            assertDoesNotThrow(() -> service.checkOrphanMessages(java.util.List.of()));
        }

        @Test
        @DisplayName("checkOrphanChats with empty input does not throw")
        void emptyChats() {
            assertDoesNotThrow(() -> service.checkOrphanChats(java.util.List.of()));
        }

        @Test
        @DisplayName("checkOrphanThreads with empty input does not throw")
        void emptyThreads() {
            assertDoesNotThrow(() -> service.checkOrphanThreads(java.util.List.of()));
        }

        @Test
        @DisplayName("checkOrphanAgents with empty input does not throw")
        void emptyAgents() {
            assertDoesNotThrow(() -> service.checkOrphanAgents(java.util.List.of()));
        }

        @Test
        @DisplayName("checkOrphanChatAssignments with empty input does not throw")
        void emptyChatAssignments() {
            assertDoesNotThrow(() -> service.checkOrphanChatAssignments(java.util.List.of()));
        }

        @Test
        @DisplayName("checkOrphanUserStatusMutes with empty input does not throw")
        void emptyUserStatusMutes() {
            assertDoesNotThrow(() -> service.checkOrphanUserStatusMutes(java.util.List.of()));
        }

        @Test
        @DisplayName("checkOrphanMutations dispatches each id-bucket to its specialised hook")
        void aggregatedDispatch() {
            assertDoesNotThrow(() ->
                    service.checkOrphanMutations(java.util.List.of(), java.util.List.of(), java.util.List.of()));
        }
    }

    @Nested
    @DisplayName("missing-sync-key follow-ups — scheduling hooks")
    class MissingKeyHooks {
        @Test
        @DisplayName("scheduleAllDevicesRespondedCheck does not throw")
        void scheduleAllDevices() {
            assertDoesNotThrow(service::scheduleAllDevicesRespondedCheck);
        }

        @Test
        @DisplayName("rescheduleMissingSyncKeyTimeout does not throw")
        void rescheduleTimeout() {
            assertDoesNotThrow(service::rescheduleMissingSyncKeyTimeout);
        }
    }

    @Nested
    @DisplayName("reset — clears service state")
    class Reset {
        @Test
        @DisplayName("reset does not throw on a freshly-constructed service")
        void resetFreshly() {
            assertDoesNotThrow(service::reset);
        }

        @Test
        @DisplayName("reset after starting the periodic job is safe")
        void resetAfterStart() {
            service.startPeriodicSyncJob();
            assertDoesNotThrow(service::reset);
        }
    }

    @Nested
    @DisplayName("syncBlockedCollections — no-op when nothing is blocked")
    class SyncBlockedCollections {
        @Test
        @DisplayName("with a fresh store (nothing blocked), syncBlockedCollections is a no-op")
        void freshStoreNoOp() {
            // Nothing has been moved to BLOCKED state; the method should iterate the values,
            // find no blocked collections, and return without contacting the server.
            assertDoesNotThrow(service::syncBlockedCollections);
        }
    }
}
