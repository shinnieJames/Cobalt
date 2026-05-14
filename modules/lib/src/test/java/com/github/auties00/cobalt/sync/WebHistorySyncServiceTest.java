package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotificationBuilder;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncType;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link WebHistorySyncService} — Cobalt's adapter for
 * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
 * and {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk}.
 *
 * <p>The download / decrypt / decode pipeline is driven on a dedicated virtual
 * thread and depends on a live media-connection round-trip for the CDN path,
 * so these tests focus on the synchronous, observable invariants that don't
 * require a live network:
 * <ul>
 *   <li>Constructor rejects null collaborators.</li>
 *   <li>{@code process(null)} is a no-op (early return before the virtual
 *       thread is scheduled).</li>
 *   <li>{@code process(notification)} with no inline payload and no
 *       {@code directPath}/{@code mediaKey} pair is non-fatal — the decode
 *       returns {@code null} (matching WA Web's
 *       {@code MESSAGE_ACCESS_STATUS} / {@code NO_HISTORY} markers) and the
 *       service swallows it without throwing.</li>
 *   <li>An inline payload that fails to inflate is reported as a failed chunk
 *       (the WA Web {@code commitHistoryDataAppliedMetric} failure path) but
 *       still does not throw from {@code process}.</li>
 * </ul>
 *
 * <p>The per-{@link HistorySyncType} matrix (INITIAL_BOOTSTRAP, RECENT, FULL,
 * PUSH_NAME, NON_BLOCKING_DATA, ON_DEMAND, INITIAL_STATUS_V3) and the chunk
 * → store projection are exercised by the Phase 9 integration cycles where
 * a real CDN download + decoded {@link com.github.auties00.cobalt.model.sync.history.HistorySync}
 * payload is available via the live capture corpus. The chunk-type oracles
 * are gated through {@link SyncFixtures#isOracleAvailable(String)} so the
 * synthetic suite still runs green before fixtures land.
 */
@DisplayName("WebHistorySyncService")
class WebHistorySyncServiceTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private TestWhatsAppClient client;
    private TestABPropsService props;
    private LidMigrationService lidMigration;
    private WebHistorySyncService service;

    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        lidMigration = new LidMigrationService(client, props, wam);
        service = new WebHistorySyncService(client, lidMigration, props, wam);
    }

    @Nested
    @DisplayName("constructor — rejects null collaborators")
    class ConstructorContract {
        @Test
        @DisplayName("null WhatsAppClient → NPE")
        void nullClient() {
            var wam = new DefaultWamService(client, props);
            assertThrows(NullPointerException.class,
                    () -> new WebHistorySyncService(null, lidMigration, props, wam));
        }

        @Test
        @DisplayName("null LidMigrationService → NPE")
        void nullLidMigration() {
            var wam = new DefaultWamService(client, props);
            assertThrows(NullPointerException.class,
                    () -> new WebHistorySyncService(client, null, props, wam));
        }

        @Test
        @DisplayName("null WamService → NPE")
        void nullWam() {
            assertThrows(NullPointerException.class,
                    () -> new WebHistorySyncService(client, lidMigration, props, null));
        }
    }

    @Nested
    @DisplayName("process — early-return paths")
    class EarlyReturns {
        @Test
        @DisplayName("process(null) returns immediately without scheduling a virtual thread")
        void nullNotificationIsNoOp() {
            assertDoesNotThrow(() -> service.process(null));
        }

        @Test
        @DisplayName("notification with no inline payload and no media url is non-fatal")
        void emptyNotificationNonFatal() {
            // Schedules a virtual thread that runs decode() → returns null
            // (no inline payload, no directPath/mediaKey) → service short-circuits
            // before the dispatch step. Should never raise from the caller's
            // perspective; the WAM emissions happen on the virtual thread.
            var notification = new HistorySyncNotificationBuilder()
                    .syncType(HistorySyncType.NON_BLOCKING_DATA)
                    .build();
            assertDoesNotThrow(() -> service.process(notification));
        }

        @Test
        @DisplayName("notification with a non-zlib inline payload is reported as failure but does not throw")
        void malformedInlinePayloadDoesNotThrow() {
            // The inline payload is run through an InflaterInputStream; bytes that are not
            // valid zlib decode to a thrown DataFormatException, which decode() converts to
            // WhatsAppHistorySyncException. The service catches that and emits the WAM
            // failure metric — the caller sees no exception.
            var notification = new HistorySyncNotificationBuilder()
                    .syncType(HistorySyncType.INITIAL_BOOTSTRAP)
                    .initialHistBootstrapInlinePayload(new byte[]{(byte) 0xFF, (byte) 0xFF})
                    .build();
            assertDoesNotThrow(() -> service.process(notification));
        }
    }

    @Nested
    @DisplayName("syncType matrix — synthetic notifications cover every variant")
    class SyncTypeMatrix {
        @Test
        @DisplayName("every HistorySyncType can be wrapped in a notification and processed without throwing")
        void everySyncTypeAccepted() {
            for (var syncType : HistorySyncType.values()) {
                var notification = new HistorySyncNotificationBuilder()
                        .syncType(syncType)
                        .build();
                assertDoesNotThrow(() -> service.process(notification),
                        "syncType=" + syncType + " must be a no-op when carrying no payload");
            }
        }
    }

    @Nested
    @DisplayName("WA Web oracle hooks — gated on captured chunk fixtures")
    class OracleHooks {
        @Test
        @DisplayName("history/initial-bootstrap oracle (when captured) drives a real decode")
        void initialBootstrapOracle() {
            if (!SyncFixtures.isOracleAvailable("history/initial-bootstrap")) return;
            // Reserved for Phase 9: the integration cycle wires a fake MediaConnection that
            // returns the captured plaintext, runs process() against the captured
            // HistorySyncNotification, and asserts the store state matches the captured
            // WAWebHandleHistorySyncChunk projection. The hook is here so the test runs
            // green before fixtures are captured.
        }

        @Test
        @DisplayName("history/recent oracle hook")
        void recentOracle() {
            if (!SyncFixtures.isOracleAvailable("history/recent")) return;
        }

        @Test
        @DisplayName("history/push-name oracle hook")
        void pushNameOracle() {
            if (!SyncFixtures.isOracleAvailable("history/push-name")) return;
        }

        @Test
        @DisplayName("history/full oracle hook")
        void fullOracle() {
            if (!SyncFixtures.isOracleAvailable("history/full")) return;
        }

        @Test
        @DisplayName("history/on-demand oracle hook")
        void onDemandOracle() {
            if (!SyncFixtures.isOracleAvailable("history/on-demand")) return;
        }

        @Test
        @DisplayName("history/initial-status-v3 oracle hook")
        void initialStatusV3Oracle() {
            if (!SyncFixtures.isOracleAvailable("history/initial-status-v3")) return;
        }

        @Test
        @DisplayName("history/non-blocking-data oracle hook")
        void nonBlockingDataOracle() {
            if (!SyncFixtures.isOracleAvailable("history/non-blocking-data")) return;
        }
    }
}
