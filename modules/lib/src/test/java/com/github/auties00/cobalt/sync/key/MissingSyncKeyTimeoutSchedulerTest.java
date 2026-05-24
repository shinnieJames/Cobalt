package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
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
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Pins the observable invariants of {@link MissingSyncKeyTimeoutScheduler} that do not
 * depend on a real timer fire.
 *
 * @apiNote
 * Covers the synchronous-observable behaviour of every scheduler entry point:
 * {@code scheduleTimeoutCheck}, {@code cancel}, {@code shutdown},
 * {@code startPeriodicReRequestJob}, and {@code scheduleAllDevicesRespondedCheck}. The
 * fatal-error flows that depend on multi-day wait-for-key elapse are exercised by the
 * Phase 9 integration cycles instead.
 *
 * @implNote
 * This implementation keeps the {@code SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS} prop at 30 days so
 * the {@link ScheduledExecutorService}-backed timers cannot fire during a test and pollute
 * the assertions; {@link #tearDown()} unconditionally calls
 * {@link MissingSyncKeyTimeoutScheduler#shutdown()} so the executor thread does not leak
 * across the test suite.
 */
@DisplayName("MissingSyncKeyTimeoutScheduler")
class MissingSyncKeyTimeoutSchedulerTest {
    /**
     * The fixed self phone-number JID used by every test in this class.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The fixed self LID JID used by every test in this class.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The fixed self device JID used by every test in this class (device 1).
     */
    private static final Jid SELF_PN_DEVICE_1 = Jid.of("19250000001:1@s.whatsapp.net");

    /**
     * The synthetic {@link TestWhatsAppClient} wired to {@link #store}.
     */
    private TestWhatsAppClient client;

    /**
     * The {@link WhatsAppStore} the scheduler reads.
     */
    private WhatsAppStore store;

    /**
     * The {@link TestABPropsService} preloaded with the wait-for-key timeout.
     */
    private TestABPropsService props;

    /**
     * The companion request service wired to the scheduler.
     */
    private MissingSyncKeyRequestService requestService;

    /**
     * The system under test.
     */
    private MissingSyncKeyTimeoutScheduler scheduler;

    /**
     * Builds a fresh harness per test: temporary store seeded with the device JIDs, AB
     * props with a 30-day wait-for-key timeout, scheduler wired to the request service.
     *
     * @apiNote
     * The 30-day timeout is large enough that no scheduled check fires within the test
     * lifecycle.
     */
    @BeforeEach
    void setUp() {
        props = TestABPropsService.builder().build();
        props.set(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS, 30);

        store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(SELF_PN_DEVICE_1);
        client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        requestService = new MissingSyncKeyRequestService(client, wam);
        scheduler = new MissingSyncKeyTimeoutScheduler(client, props, requestService);
        requestService.setTimeoutScheduler(scheduler);
    }

    /**
     * Shuts down the scheduler so the executor thread does not leak between tests.
     */
    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    /**
     * Tests for {@link MissingSyncKeyTimeoutScheduler#scheduleTimeoutCheck()}.
     */
    @Nested
    @DisplayName("scheduleTimeoutCheck")
    class ScheduleTimeoutCheck {
        /**
         * Asserts that an empty missing-key store does not throw and does not arm a timer.
         */
        @Test
        @DisplayName("with no missing keys is a no-op (does not throw)")
        void noKeysNoSchedule() {
            assertDoesNotThrow(scheduler::scheduleTimeoutCheck,
                    "empty missing-key store must not throw - WAWebSyncdStoreMissingKeys._setMissingKeyTimeout returns early when n.length === 0");
        }

        /**
         * Asserts that scheduling a check with a tracked missing key in the store does not
         * throw.
         */
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

        /**
         * Asserts that a second schedule call cancels the first and re-arms cleanly.
         */
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

    /**
     * Tests for {@link MissingSyncKeyTimeoutScheduler#cancel()}.
     */
    @Nested
    @DisplayName("cancel")
    class Cancel {
        /**
         * Asserts that calling cancel without an armed check is safe.
         */
        @Test
        @DisplayName("cancel without a scheduled check is safe")
        void cancelNoOp() {
            assertDoesNotThrow(scheduler::cancel);
        }

        /**
         * Asserts that calling cancel after a schedule clears the pending check.
         */
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

        /**
         * Asserts that successive cancel calls are safe.
         */
        @Test
        @DisplayName("cancel is idempotent")
        void cancelIdempotent() {
            scheduler.cancel();
            assertDoesNotThrow(scheduler::cancel);
        }
    }

    /**
     * Tests for {@link MissingSyncKeyTimeoutScheduler#scheduleAllDevicesRespondedCheck()}.
     */
    @Nested
    @DisplayName("scheduleAllDevicesRespondedCheck - 5-second grace period")
    class GracePeriod {
        /**
         * Asserts that scheduling the grace period without an armed check is safe.
         */
        @Test
        @DisplayName("scheduling the grace period does not throw")
        void scheduleGracePeriod() {
            assertDoesNotThrow(scheduler::scheduleAllDevicesRespondedCheck);
        }

        /**
         * Asserts that a second grace-period call cancels and replaces the first.
         */
        @Test
        @DisplayName("a second schedule cancels and replaces the previous grace check")
        void rescheduleGracePeriod() {
            scheduler.scheduleAllDevicesRespondedCheck();
            assertDoesNotThrow(scheduler::scheduleAllDevicesRespondedCheck);
        }
    }

    /**
     * Tests for {@link MissingSyncKeyTimeoutScheduler#startPeriodicReRequestJob()}.
     */
    @Nested
    @DisplayName("startPeriodicReRequestJob - idempotent")
    class PeriodicJob {
        /**
         * Asserts that the first call schedules the periodic job without throwing.
         */
        @Test
        @DisplayName("first call schedules without throwing")
        void firstCallSchedules() {
            assertDoesNotThrow(scheduler::startPeriodicReRequestJob);
        }

        /**
         * Asserts that a second call while the job is still pending is a no-op.
         */
        @Test
        @DisplayName("second call is a no-op (already scheduled)")
        void secondCallIsNoop() {
            scheduler.startPeriodicReRequestJob();
            assertDoesNotThrow(scheduler::startPeriodicReRequestJob,
                    "WA Web relies on WAWebTasksDefinitions single-registration; Cobalt guards explicitly");
        }
    }

    /**
     * Tests for {@link MissingSyncKeyTimeoutScheduler#shutdown()}.
     */
    @Nested
    @DisplayName("shutdown - releases executor and is idempotent")
    class Shutdown {
        /**
         * Asserts that shutdown on a freshly built scheduler is safe.
         */
        @Test
        @DisplayName("shutdown does not throw")
        void shutdownDoesNotThrow() {
            assertDoesNotThrow(scheduler::shutdown);
        }

        /**
         * Asserts that shutdown after every schedule path is safe.
         */
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

        /**
         * Asserts that successive shutdown calls are safe.
         */
        @Test
        @DisplayName("a second shutdown is a no-op")
        void shutdownIdempotent() {
            scheduler.shutdown();
            assertDoesNotThrow(scheduler::shutdown);
        }
    }
}
