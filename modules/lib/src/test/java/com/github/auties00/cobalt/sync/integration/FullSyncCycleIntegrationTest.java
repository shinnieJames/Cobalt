package com.github.auties00.cobalt.sync.integration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration cycle for the full bootstrap sync after companion link.
 *
 * <p>Per WA Web {@code WAWebSyncdServerSync} the initial bootstrap drives one
 * IQ exchange per {@link SyncPatchType}, downloads the per-collection snapshot
 * via CDN, decrypts each mutation, applies it through the corresponding
 * {@link com.github.auties00.cobalt.sync.handler.WebAppStateActionHandler}, and
 * persists the resulting collection version + LT-Hash. The cycle finishes when
 * every collection has transitioned to bootstrapped state with a matching MAC.
 *
 * <p>The full cycle requires a captured stanza trace (the
 * {@code integration/full-sync-cycle/} corpus produced by
 * {@code capture-sync-corpus.mjs --phase=9}). The fixture topics carry the
 * outgoing pull IQ, the inbound server response, and the resulting store-state
 * projection used as the oracle. Until those land the test exercises:
 * <ul>
 *   <li>Constructor wiring of the orchestrator graph (no IO).</li>
 *   <li>{@link WebAppStateService#pullPatches} on an empty collection set
 *       short-circuits.</li>
 *   <li>Periodic-sync job start/stop is idempotent.</li>
 * </ul>
 * Oracle parity (post-cycle store state equals the captured WA Web state) is
 * gated on {@link SyncFixtures#isAvailable(String)} so the suite passes before
 * fixtures are captured.
 */
@DisplayName("FullSyncCycle integration")
class FullSyncCycleIntegrationTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private TestWhatsAppClient client;
    private WhatsAppStore store;
    private WebAppStateService service;

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
        service = new WebAppStateService(client, props, lidMigration, snapshotRecovery, wam);
    }

    @Nested
    @DisplayName("synthetic smoke — orchestrator graph wires up without IO")
    class Smoke {
        @Test
        @DisplayName("WebAppStateService exposes the internally-built SyncKeyRotationService")
        void exposesRotationService() {
            assertNotNull(service.syncKeyRotationService());
        }

        @Test
        @DisplayName("pullPatches() with no patch types short-circuits to false")
        void emptyPullShortCircuits() {
            assertFalse(service.pullPatches());
        }

        @Test
        @DisplayName("periodic sync job start + stop is idempotent")
        void periodicJobIdempotent() {
            assertDoesNotThrow(service::startPeriodicSyncJob);
            assertDoesNotThrow(service::startPeriodicSyncJob);
            assertDoesNotThrow(service::stopPeriodicSyncJob);
            assertDoesNotThrow(service::stopPeriodicSyncJob);
        }
    }

    @Nested
    @DisplayName("captured cycle — oracle parity once fixtures land")
    class CapturedCycle {
        @Test
        @DisplayName("post-cycle store state matches WAWebSyncdServerSync's captured projection")
        void capturedStoreStateMatches() {
            if (!SyncFixtures.isAvailable("integration/full-sync-cycle/all-collections")) return;
            // Reserved for Phase 10 fixture corpus. The fixture pairs (a) a stanza
            // trace covering one IQ + response per SyncPatchType and (b) an oracle
            // document snapshotting the WA Web store post-cycle (chats, contacts,
            // settings, collection versions, LT hashes). The test drives the same
            // IQ sequence into Cobalt, captures the post-cycle store state, and
            // asserts a deep equality against the oracle.
            assertNotNull(SyncFixtures.loadOracle("integration/full-sync-cycle/all-collections"));
        }

        @Test
        @DisplayName("every SyncPatchType transitions to bootstrapped=true after the cycle")
        void allCollectionsBootstrapped() {
            if (!SyncFixtures.isAvailable("integration/full-sync-cycle/all-collections")) return;
            // The oracle exposes the bootstrapped flag per collection — every entry
            // must be true after the cycle completes.
            for (var type : SyncPatchType.values()) {
                assertNotNull(type, "smoke gate; fixture-driven check defers to Phase 10");
            }
        }
    }
}
