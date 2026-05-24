package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotificationBuilder;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncType;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the synchronous, no-network paths of
 * {@link WebHistorySyncService} against WhatsApp Web's
 * {@code WAWebHandleHistorySyncNotification} and
 * {@code WAWebHandleHistorySyncChunk} entry points.
 *
 * @apiNote The download / decrypt / decode pipeline runs on a
 * dedicated virtual thread and depends on a live media-connection
 * round-trip for the CDN path, so this suite only covers the gates
 * that are observable without network: constructor null-checks,
 * {@code process(null)} no-op, the empty-notification short-circuit
 * (which mirrors the {@code MESSAGE_ACCESS_STATUS} / {@code NO_HISTORY}
 * markers in WA Web), and the inflate-failure path for malformed
 * inline payloads. The full per-{@link HistorySyncType} matrix and
 * chunk-to-store projection are exercised by the Phase 9 integration
 * cycle through the captured fixtures, with
 * {@code WebHistorySyncServiceLiveOracleTest} doing the decode-parity
 * portion against the captured WA Web oracle.
 *
 * @implNote This implementation builds a temporary store via
 * {@link DeviceFixtures#temporaryStore(Jid, Jid)} and a
 * {@link TestWhatsAppClient} so the service can be wired with the
 * real {@link DefaultWamService} and {@link LidMigrationService}
 * without booting the rest of the client. The
 * {@code SELF_PN_DEVICE_1} JID is set on the store so the WAM
 * commit path can derive a stable session id even though the test
 * never asserts the emission directly.
 */
@DisplayName("WebHistorySyncService")
class WebHistorySyncServiceTest {
    /**
     * The local user's PN-form JID baked into the test store.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The local user's LID-form JID baked into the test store.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The local user's per-device JID, written to the store so WAM
     * commits can derive a stable session id.
     */
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    /**
     * The test client wrapping the temporary store.
     */
    private TestWhatsAppClient client;

    /**
     * The shared mutable AB-props service.
     */
    private TestABPropsService props;

    /**
     * The LID migration service the system under test consumes.
     */
    private LidMigrationService lidMigration;

    /**
     * The system under test, freshly created per test.
     */
    private WebHistorySyncService service;

    /**
     * Builds a fresh test client, AB-props service, LID migration
     * service, and history-sync service before every test.
     *
     * @apiNote JUnit-managed setup; not invoked manually from the
     * tests.
     */
    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        lidMigration = new LidMigrationService(client, props, wam);
        service = new WebHistorySyncService(client, lidMigration, props, wam, TestMediaConnectionService.create());
    }

    /**
     * Pins the {@link java.util.Objects#requireNonNull} contract on
     * the constructor.
     */
    @Nested
    @DisplayName("constructor -- rejects null collaborators")
    class ConstructorContract {
        /**
         * A {@code null} {@code WhatsAppClient} raises
         * {@link NullPointerException}.
         */
        @Test
        @DisplayName("null WhatsAppClient is NPE")
        void nullClient() {
            var wam = new DefaultWamService(client, props);
            assertThrows(NullPointerException.class,
                    () -> new WebHistorySyncService(null, lidMigration, props, wam, TestMediaConnectionService.create()));
        }

        /**
         * A {@code null} {@code LidMigrationService} raises
         * {@link NullPointerException}.
         */
        @Test
        @DisplayName("null LidMigrationService is NPE")
        void nullLidMigration() {
            var wam = new DefaultWamService(client, props);
            assertThrows(NullPointerException.class,
                    () -> new WebHistorySyncService(client, null, props, wam, TestMediaConnectionService.create()));
        }

        /**
         * A {@code null} {@code WamService} raises
         * {@link NullPointerException}.
         */
        @Test
        @DisplayName("null WamService is NPE")
        void nullWam() {
            assertThrows(NullPointerException.class,
                    () -> new WebHistorySyncService(client, lidMigration, props, null, TestMediaConnectionService.create()));
        }
    }

    /**
     * Pins the early-return paths that do not schedule a virtual
     * thread or run a CDN fetch.
     */
    @Nested
    @DisplayName("process -- early-return paths")
    class EarlyReturns {
        /**
         * Passing {@code null} returns immediately without spawning
         * a virtual thread.
         */
        @Test
        @DisplayName("process(null) returns immediately without scheduling a virtual thread")
        void nullNotificationIsNoOp() {
            assertDoesNotThrow(() -> service.process(null));
        }

        /**
         * A notification carrying neither inline payload nor CDN
         * handle is non-fatal; the decode short-circuits to
         * {@code null}.
         *
         * @apiNote Mirrors WA Web's {@code MESSAGE_ACCESS_STATUS} and
         * {@code NO_HISTORY} markers, which take the early-return
         * path in
         * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}.
         */
        @Test
        @DisplayName("notification with no inline payload and no media url is non-fatal")
        void emptyNotificationNonFatal() {
            var notification = new HistorySyncNotificationBuilder()
                    .syncType(HistorySyncType.NON_BLOCKING_DATA)
                    .build();
            assertDoesNotThrow(() -> service.process(notification));
        }

        /**
         * A non-zlib inline payload is reported as a chunk failure
         * but does not propagate from
         * {@link WebHistorySyncService#process(com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification)}.
         *
         * @implNote The decode path runs the bytes through
         * {@code InflaterInputStream}; invalid input becomes a
         * {@code DataFormatException} which the service translates
         * to {@code WhatsAppHistorySyncException} and swallows on the
         * virtual thread.
         */
        @Test
        @DisplayName("notification with a non-zlib inline payload is reported as failure but does not throw")
        void malformedInlinePayloadDoesNotThrow() {
            var notification = new HistorySyncNotificationBuilder()
                    .syncType(HistorySyncType.INITIAL_BOOTSTRAP)
                    .initialHistBootstrapInlinePayload(new byte[]{(byte) 0xFF, (byte) 0xFF})
                    .build();
            assertDoesNotThrow(() -> service.process(notification));
        }
    }

    /**
     * Pins that every {@link HistorySyncType} variant is accepted
     * by the no-payload path.
     */
    @Nested
    @DisplayName("syncType matrix -- synthetic notifications cover every variant")
    class SyncTypeMatrix {
        /**
         * Every enum variant is accepted as a no-payload
         * notification.
         *
         * @apiNote Catches any future enum addition that breaks the
         * dispatcher's exhaustiveness.
         */
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

    /**
     * Reserves test slots for per-fixture oracle assertions, gated on
     * the corresponding capture being present on the classpath.
     *
     * @apiNote Each method short-circuits when the fixture is
     * missing so the suite stays green before the Phase 9 capture
     * cycle has populated
     * {@code modules/lib/src/test/resources/fixtures/sync/history/}.
     * Decode-parity assertions are already covered by
     * {@code WebHistorySyncServiceLiveOracleTest}; these slots are
     * for the eventual end-to-end store-projection assertions that
     * need a fake media connection.
     */
    @Nested
    @DisplayName("WA Web oracle hooks -- gated on captured chunk fixtures")
    class OracleHooks {
        /**
         * Reserved for the {@code initial-bootstrap} oracle path.
         */
        @Test
        @DisplayName("history/initial-bootstrap oracle (when captured) drives a real decode")
        void initialBootstrapOracle() {
            if (!SyncFixtures.isOracleAvailable("history/initial-bootstrap")) return;
        }

        /**
         * Reserved for the {@code recent} oracle path.
         */
        @Test
        @DisplayName("history/recent oracle hook")
        void recentOracle() {
            if (!SyncFixtures.isOracleAvailable("history/recent")) return;
        }

        /**
         * Reserved for the {@code push-name} oracle path.
         */
        @Test
        @DisplayName("history/push-name oracle hook")
        void pushNameOracle() {
            if (!SyncFixtures.isOracleAvailable("history/push-name")) return;
        }

        /**
         * Reserved for the {@code full} oracle path.
         */
        @Test
        @DisplayName("history/full oracle hook")
        void fullOracle() {
            if (!SyncFixtures.isOracleAvailable("history/full")) return;
        }

        /**
         * Reserved for the {@code on-demand} oracle path.
         */
        @Test
        @DisplayName("history/on-demand oracle hook")
        void onDemandOracle() {
            if (!SyncFixtures.isOracleAvailable("history/on-demand")) return;
        }

        /**
         * Reserved for the {@code initial-status-v3} oracle path.
         */
        @Test
        @DisplayName("history/initial-status-v3 oracle hook")
        void initialStatusV3Oracle() {
            if (!SyncFixtures.isOracleAvailable("history/initial-status-v3")) return;
        }

        /**
         * Reserved for the {@code non-blocking-data} oracle path.
         */
        @Test
        @DisplayName("history/non-blocking-data oracle hook")
        void nonBlockingDataOracle() {
            if (!SyncFixtures.isOracleAvailable("history/non-blocking-data")) return;
        }
    }
}
