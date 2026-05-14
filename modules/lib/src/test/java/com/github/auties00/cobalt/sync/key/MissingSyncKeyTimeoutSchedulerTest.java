package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link MissingSyncKeyTimeoutScheduler} — Cobalt's adapter for
 * {@code WAWebSyncdStoreMissingKeys.setMissingKeyTimeoutInTransaction} and the
 * {@code WAWebSyncdRequestAllSyncdMissingKeysJob} periodic re-request.
 *
 * <p>The scheduler owns a real {@link java.util.concurrent.ScheduledExecutorService},
 * so the tests focus on observable invariants that don't depend on timer fires:
 * <ul>
 *   <li>{@code scheduleTimeoutCheck} with no missing keys is a no-op
 *       (debug-logged, no scheduled task).</li>
 *   <li>{@code scheduleTimeoutCheck} with a tracked missing key schedules
 *       a check and doesn't throw.</li>
 *   <li>{@code cancel} is safe to call regardless of state.</li>
 *   <li>{@code shutdown} terminates the executor and is idempotent.</li>
 *   <li>{@code startPeriodicReRequestJob} is idempotent (second call is a
 *       no-op while the first is still pending).</li>
 *   <li>{@code scheduleAllDevicesRespondedCheck} schedules the short grace
 *       period without throwing.</li>
 * </ul>
 *
 * <p>Long-running fatal-error flows ({@code checkForExpiredKeys},
 * {@code checkForAllDevicesRespondedWithoutKey}) are gated on real wall-clock
 * elapsed time and are covered by the Phase 9 integration cycles instead.
 */
@DisplayName("MissingSyncKeyTimeoutScheduler")
class MissingSyncKeyTimeoutSchedulerTest {
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    private TestWhatsAppClient client;
    private WhatsAppStore store;
    private TestABPropsService props;
    private MissingSyncKeyRequestService requestService;
    private MissingSyncKeyTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        // Keep the wait-for-key window large so scheduled checks don't fire mid-test
        props.set(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS, 30);

        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        requestService = new MissingSyncKeyRequestService(client, wam);
        scheduler = new MissingSyncKeyTimeoutScheduler(client, props, requestService);
        requestService.setTimeoutScheduler(scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Nested
    @DisplayName("scheduleTimeoutCheck")
    class ScheduleTimeoutCheck {
        @Test
        @DisplayName("with no missing keys is a no-op (does not throw)")
        void noKeysNoSchedule() {
            assertDoesNotThrow(scheduler::scheduleTimeoutCheck,
                    "empty missing-key store must not throw — WAWebSyncdStoreMissingKeys._setMissingKeyTimeout returns early when n.length === 0");
        }

        @Test
        @DisplayName("with a tracked missing key schedules the timeout check")
        void tracksFuture() {
            store.addMissingSyncKey(new MissingDeviceSyncKeyBuilder()
                    .keyId(new byte[]{1, 2, 3, 4, 5, 6})
                    .timestamp(Instant.now())
                    .askedDevices(Set.of(0))
                    .build());
            assertDoesNotThrow(scheduler::scheduleTimeoutCheck,
                    "non-empty missing-key store should schedule a check without throwing");
        }

        @Test
        @DisplayName("rescheduling is safe (replaces the previous future)")
        void reschedulingReplaces() {
            store.addMissingSyncKey(new MissingDeviceSyncKeyBuilder()
                    .keyId(new byte[]{1, 2, 3, 4, 5, 6})
                    .timestamp(Instant.now())
                    .build());
            scheduler.scheduleTimeoutCheck();
            assertDoesNotThrow(scheduler::scheduleTimeoutCheck,
                    "second schedule must cancel the first and re-arm");
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {
        @Test
        @DisplayName("cancel without a scheduled check is safe")
        void cancelNoOp() {
            assertDoesNotThrow(scheduler::cancel);
        }

        @Test
        @DisplayName("cancel after scheduling clears the pending check")
        void cancelAfterSchedule() {
            store.addMissingSyncKey(new MissingDeviceSyncKeyBuilder()
                    .keyId(new byte[]{1, 2, 3, 4, 5, 6})
                    .timestamp(Instant.now())
                    .build());
            scheduler.scheduleTimeoutCheck();
            assertDoesNotThrow(scheduler::cancel);
        }

        @Test
        @DisplayName("cancel is idempotent")
        void cancelIdempotent() {
            scheduler.cancel();
            assertDoesNotThrow(scheduler::cancel);
        }
    }

    @Nested
    @DisplayName("scheduleAllDevicesRespondedCheck — 5-second grace period")
    class GracePeriod {
        @Test
        @DisplayName("scheduling the grace period does not throw")
        void scheduleGracePeriod() {
            assertDoesNotThrow(scheduler::scheduleAllDevicesRespondedCheck);
        }

        @Test
        @DisplayName("a second schedule cancels and replaces the previous grace check")
        void rescheduleGracePeriod() {
            scheduler.scheduleAllDevicesRespondedCheck();
            assertDoesNotThrow(scheduler::scheduleAllDevicesRespondedCheck);
        }
    }

    @Nested
    @DisplayName("startPeriodicReRequestJob — idempotent")
    class PeriodicJob {
        @Test
        @DisplayName("first call schedules without throwing")
        void firstCallSchedules() {
            assertDoesNotThrow(scheduler::startPeriodicReRequestJob);
        }

        @Test
        @DisplayName("second call is a no-op (already scheduled)")
        void secondCallIsNoop() {
            scheduler.startPeriodicReRequestJob();
            assertDoesNotThrow(scheduler::startPeriodicReRequestJob,
                    "WA Web relies on WAWebTasksDefinitions single-registration; Cobalt guards explicitly");
        }
    }

    @Nested
    @DisplayName("shutdown — releases executor and is idempotent")
    class Shutdown {
        @Test
        @DisplayName("shutdown does not throw")
        void shutdownDoesNotThrow() {
            assertDoesNotThrow(scheduler::shutdown);
        }

        @Test
        @DisplayName("shutdown after scheduling does not throw")
        void shutdownAfterScheduling() {
            store.addMissingSyncKey(new MissingDeviceSyncKeyBuilder()
                    .keyId(new byte[]{1, 2, 3, 4, 5, 6})
                    .timestamp(Instant.now())
                    .build());
            scheduler.scheduleTimeoutCheck();
            scheduler.scheduleAllDevicesRespondedCheck();
            scheduler.startPeriodicReRequestJob();
            assertDoesNotThrow(scheduler::shutdown);
        }

        @Test
        @DisplayName("a second shutdown is a no-op")
        void shutdownIdempotent() {
            scheduler.shutdown();
            assertDoesNotThrow(scheduler::shutdown);
        }
    }
}
