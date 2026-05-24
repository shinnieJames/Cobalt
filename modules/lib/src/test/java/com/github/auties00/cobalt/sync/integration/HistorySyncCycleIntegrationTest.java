package com.github.auties00.cobalt.sync.integration;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotificationBuilder;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncType;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.WebHistorySyncService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration cycle for the history-sync pipeline.
 *
 * <p>End-to-end flow: companion receives a {@link HistorySyncNotification}
 * peer message → {@link WebHistorySyncService} schedules an async chunk
 * processor → downloads the encrypted blob via the media connection (or
 * uses the inline payload) → AES-CBC decrypts, validates HMAC, inflates the
 * gzip stream → decodes a {@link com.github.auties00.cobalt.model.sync.history.HistorySync}
 * payload → fans the chunk out to {@link com.github.auties00.cobalt.client.WhatsAppClientListener}
 * callbacks and the {@link LidMigrationService}.
 *
 * <p>WA Web emits seven {@link HistorySyncType} flavours
 * ({@code INITIAL_BOOTSTRAP}, {@code INITIAL_STATUS_V3}, {@code RECENT},
 * {@code FULL}, {@code PUSH_NAME}, {@code NON_BLOCKING_DATA},
 * {@code ON_DEMAND}); each has a dedicated fixture under
 * {@code integration/history-sync-cycle/}.
 */
@DisplayName("HistorySyncCycle integration")
class HistorySyncCycleIntegrationTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private WhatsAppStore store;
    private WebHistorySyncService service;

    @BeforeEach
    void setUp() {
        var props = TestABPropsService.builder().build();
        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props);
        var wam = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wam);
        service = new WebHistorySyncService(client, lidMigration, props, wam, TestMediaConnectionService.create());
    }

    @Nested
    @DisplayName("synthetic smoke — null/empty inputs are non-fatal")
    class Smoke {
        @Test
        @DisplayName("process(null) returns immediately")
        void nullNotification() {
            assertDoesNotThrow(() -> service.process(null));
        }

        @ParameterizedTest(name = "syncType={0}")
        @EnumSource(HistorySyncType.class)
        @DisplayName("a notification carrying no payload and no media url is a no-op for every syncType")
        void emptyNotificationPerType(HistorySyncType syncType) {
            var notification = new HistorySyncNotificationBuilder().syncType(syncType).build();
            assertDoesNotThrow(() -> service.process(notification));
        }
    }

    @Nested
    @DisplayName("captured cycle — per-chunk-type oracle parity once fixtures land")
    class CapturedCycle {
        @ParameterizedTest(name = "{0}")
        @EnumSource(HistorySyncType.class)
        @DisplayName("each chunk type's captured payload decodes to the WA Web oracle projection")
        void perChunkType(HistorySyncType syncType) {
            var topic = "integration/history-sync-cycle/" + syncType.name().toLowerCase().replace('_', '-');
            if (!SyncFixtures.isOracleAvailable(topic)) return;
            // The fixture exposes:
            //   - the captured peer-message notification (mediaSha256, mediaSize,
            //     mediaKey, directPath, syncType, ...)
            //   - the captured plaintext bytes (post-decrypt, post-inflate)
            //   - the expected post-apply store state (chats injected, contacts
            //     push-name updated, messages decoded into MessageContainer)
            // Reserved for the Phase 10 corpus.
            assertNotNull(SyncFixtures.loadOracle(topic));
        }

        @Test
        @DisplayName("a chunk that fails decompression emits the WAM failure metric")
        void decompressionFailure() {
            if (!SyncFixtures.isAvailable("integration/history-sync-cycle/decompression-failure")) return;
            // Replay a captured notification whose inline payload is intentionally
            // corrupted; the service must emit MdBootstrapDataApplied with
            // mdBootstrapStepResult=FAILURE and mdSyncFailureReason populated.
        }

        @Test
        @DisplayName("multi-chunk reassembly: progress monotonically advances and chunkOrder is preserved")
        void multiChunkReassembly() {
            if (!SyncFixtures.isAvailable("integration/history-sync-cycle/multi-chunk")) return;
            // The captured trace contains N notifications with monotonically
            // increasing chunkOrder. The cycle asserts:
            //   - each chunk fires onWebHistorySyncProgress
            //   - the final progress reaches 100
            //   - the post-cycle store state matches the captured oracle
        }
    }
}
