package com.github.auties00.cobalt.wam;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.binary.WamEventDecoder;
import com.github.auties00.cobalt.wam.binary.WamEventEncoder;
import com.github.auties00.cobalt.wam.binary.WamEventSizes;
import com.github.auties00.cobalt.wam.event.PsIdUpdateEventBuilder;
import com.github.auties00.cobalt.wam.event.WamClientErrorsEventBuilder;
import com.github.auties00.cobalt.wam.event.WamEventRegistry;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.type.PsIdAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Exercises the behavioural state machine of {@link WamService}
 * through a {@link TestableWamService} subclass that stubs the four
 * abstract timing/scheduling hooks.
 *
 * @apiNote
 * Validates the user-observable contract of {@link WamService}:
 * commit dispatch and channel routing, the sampling-override surface,
 * the {@code WAWebWamInitQueue} pre-init fallback, close idempotency,
 * per-channel sequence-number wrap, retry and backoff, connectivity
 * waiting, and buffer rotation and drop. Channel pinning is verified
 * dynamically against the captured live event registry under
 * {@code wam-event-definitions.json}.
 *
 * @implNote
 * This implementation never schedules a real
 * {@link java.util.concurrent.ScheduledExecutorService} task or calls
 * {@link Thread#sleep(long)}; the {@link TestableWamService} records
 * every scheduler and sleep request so the test bodies can drive ticks
 * deterministically. The retry/backoff and connectivity tests use a
 * second-tier {@link RealisticHarness} that wires a
 * {@link TestWhatsAppClient} with an injectable connectivity flag and a
 * queue of canned IQ responses.
 */
@DisplayName("WamService behavioural state machine")
class WamServiceTest {
    /**
     * Constructor smoke tests for both the public
     * {@link DefaultWamService} ctor and the protected three-arg
     * {@link WamService} ctor.
     */
    @Nested
    @DisplayName("constructor")
    class ConstructorTests {
        /**
         * The {@link DefaultWamService} two-arg constructor builds
         * without throwing.
         */
        @Test
        @DisplayName("DefaultWamService(client, abPropsService) builds without throwing")
        void defaultBuilds() {
            var props = TestABPropsService.builder().build();
            var client = TestWhatsAppClient.create();
            var service = assertDoesNotThrow(() -> new DefaultWamService(client, props));
            assertNotNull(service);
        }

        /**
         * The protected three-arg ctor accepts a substituted
         * {@link WamBeaconing} implementation.
         */
        @Test
        @DisplayName("protected ctor accepts a substituted WamBeaconing")
        void protectedCtorAcceptsCustomBeaconing() {
            var props = TestABPropsService.builder().build();
            var client = TestWhatsAppClient.create();
            var beaconing = (WamBeaconing) _ -> OptionalLong.empty();
            var service = assertDoesNotThrow(() -> new TestableWamService(client, props, beaconing));
            assertNotNull(service);
        }
    }

    /**
     * Public-API smoke and deterministic-keep tests for the runtime
     * sampling-override surface
     * ({@link WamService#setSamplingOverride(int, int)},
     * {@link WamService#removeSamplingOverride(int)},
     * {@link WamService#replaceSamplingOverrides(Map)}).
     */
    @Nested
    @DisplayName("setSamplingOverride / removeSamplingOverride / replaceSamplingOverrides")
    class SamplingOverrideSurface {
        /**
         * The three public sampling-override methods do not throw on
         * representative input shapes.
         */
        @Test
        @DisplayName("set / remove / replaceAll are no-throw")
        void apiSurface() {
            var service = newService();
            assertDoesNotThrow(() -> service.setSamplingOverride(2862, 100));
            assertDoesNotThrow(() -> service.removeSamplingOverride(2862));
            assertDoesNotThrow(() -> service.replaceSamplingOverrides(Map.of(1144, 50)));
        }

        /**
         * A sampling override with {@code weight=1} lets the next
         * commit reach the pending list.
         *
         * @apiNote
         * Only the deterministic case is asserted; the
         * {@code weight>1} branch is probabilistic and would flake
         * on a small sample.
         */
        @Test
        @DisplayName("weight=1 override never samples out")
        void weightOneOverrideAlwaysKeeps() {
            var service = newService();
            service.markInitializedForTesting();
            service.setSamplingOverride(2862, 1);
            service.commit(samplePsIdEvent());
            assertEquals(1, service.pendingCount(WamChannel.REGULAR),
                    "weight=1 sampling override must let every commit through");
        }
    }

    /**
     * Commit-dispatch tests covering
     * {@link WamChannel#REGULAR} routing after
     * {@link WamService#markInitializedForTesting()} has flipped the
     * service into the post-init phase. The pre-init deferral path is
     * covered separately by {@link InitQueueReplay}.
     */
    @Nested
    @DisplayName("commit dispatch (post-init)")
    class CommitDispatch {
        /**
         * A freshly-constructed service reports zero pending events
         * for every channel.
         */
        @Test
        @DisplayName("fresh service has zero pending events per channel")
        void freshHasEmptyPending() {
            var service = newService();
            for (var channel : WamChannel.values()) {
                assertEquals(0, service.pendingCount(channel),
                        () -> "fresh service should have zero pending events for " + channel);
            }
        }

        /**
         * A {@link WamChannel#REGULAR} commit lands in the REGULAR
         * pending list and not in any other channel's list.
         *
         * @implNote
         * Installs a {@code weight=1} override on the event id to
         * remove the probabilistic sampling branch.
         */
        @Test
        @DisplayName("REGULAR commit lands in the REGULAR pending list")
        void regularCommitLands() {
            var service = newService();
            service.markInitializedForTesting();
            service.setSamplingOverride(2862, 1);
            service.commit(samplePsIdEvent());
            assertEquals(1, service.pendingCount(WamChannel.REGULAR));
            assertEquals(0, service.pendingCount(WamChannel.REALTIME));
            assertEquals(0, service.pendingCount(WamChannel.PRIVATE));
        }

        /**
         * A redundant commit of the same event instance leaves the
         * pending list unchanged.
         *
         * @apiNote
         * Mirrors WA Web's
         * {@code WAWebWamCodegenWamEvent.WamEvent.commit} redundant-commit
         * guard: a second {@code markCommitted()} returns {@code false}
         * and the event is dropped with a logged warning.
         */
        @Test
        @DisplayName("redundant commit of the same event leaves pending unchanged")
        void redundantCommitIsNoop() {
            var service = newService();
            service.markInitializedForTesting();
            service.setSamplingOverride(2862, 1);
            var event = samplePsIdEvent();
            service.commit(event);
            assertEquals(1, service.pendingCount(WamChannel.REGULAR));
            service.commit(event);
            assertEquals(1, service.pendingCount(WamChannel.REGULAR),
                    "second commit of the same instance should be dropped by markCommitted()");
        }

        /**
         * Two distinct event instances of the same spec both land in
         * the pending list; there is no de-duplication at the spec
         * level.
         */
        @Test
        @DisplayName("distinct event instances both land")
        void twoInstancesLand() {
            var service = newService();
            service.markInitializedForTesting();
            service.setSamplingOverride(2862, 1);
            service.commit(samplePsIdEvent());
            service.commit(samplePsIdEvent());
            assertEquals(2, service.pendingCount(WamChannel.REGULAR));
        }

        /**
         * {@link WamService#commitAndWaitForFlush(WamEventSpec)}
         * returns a non-null future and queues the event into pending
         * just like {@link WamService#commit(WamEventSpec)}.
         */
        @Test
        @DisplayName("commitAndWaitForFlush returns a future and queues the event")
        void commitAndWaitForFlushFuture() {
            var service = newService();
            service.markInitializedForTesting();
            service.setSamplingOverride(2862, 1);
            var future = service.commitAndWaitForFlush(samplePsIdEvent());
            assertNotNull(future, "commitAndWaitForFlush must always return a non-null future");
            assertEquals(1, service.pendingCount(WamChannel.REGULAR));
        }

        /**
         * A {@link WamClientErrorsEventBuilder}-built event lands in
         * the REGULAR pending list, covering a second representative
         * event type beyond {@code PsIdUpdate}.
         */
        @Test
        @DisplayName("WamClientErrorsEvent commit lands in REGULAR")
        void wamClientErrorsCommit() {
            var service = newService();
            service.markInitializedForTesting();
            service.setSamplingOverride(1144, 1);
            var event = new WamClientErrorsEventBuilder()
                    .wamClientBufferDropErrorCount(1)
                    .build();
            service.commit(event);
            assertEquals(1, service.pendingCount(WamChannel.REGULAR));
        }
    }

    /**
     * Init-queue tests covering the
     * {@code WAWebWamInitQueue} fallback path: a commit that arrives
     * before {@link WamService#initialize()} (or
     * {@link WamService#markInitializedForTesting()}) has run is
     * deferred to the init queue and replayed when
     * {@link WamService#drainInitQueue()} runs.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebWamCodegenWamEvent.WamEvent.commit} branch
     * {@code getWamRuntime() ?? queueEvent}: validation, sampling,
     * and dispatch all happen at drain time against the live runtime,
     * not at the original commit point.
     */
    @Nested
    @DisplayName("init queue (WAWebWamInitQueue fallback)")
    class InitQueueReplay {
        /**
         * A pre-init commit pushes into the init queue rather than
         * the pending list.
         */
        @Test
        @DisplayName("pre-init commit defers into the init queue, not pending")
        void preInitCommitDefersToInitQueue() {
            var service = newService();
            service.commit(samplePsIdEvent());
            assertEquals(0, service.pendingCount(WamChannel.REGULAR),
                    "pre-init commit must not land in pending");
            assertEquals(1, service.initQueueSize(),
                    "pre-init commit must land in the init queue");
        }

        /**
         * Multiple pre-init commits accumulate in the init queue in
         * FIFO order and none escape into the pending list.
         */
        @Test
        @DisplayName("multiple pre-init commits all queue")
        void multiplePreInitCommitsAllQueue() {
            var service = newService();
            service.commit(samplePsIdEvent());
            service.commit(samplePsIdEvent());
            service.commit(samplePsIdEvent());
            assertEquals(3, service.initQueueSize());
            assertEquals(0, service.pendingCount(WamChannel.REGULAR));
        }

        /**
         * Draining the init queue after marking the service
         * initialised re-runs each deferred commit and lands every
         * event in the pending list.
         */
        @Test
        @DisplayName("drain replays queued commits into pending and empties the queue")
        void drainReplaysIntoPending() {
            var service = newService();
            service.commit(samplePsIdEvent());
            service.commit(samplePsIdEvent());
            assertEquals(2, service.initQueueSize());

            service.markInitializedForTesting();
            service.setSamplingOverride(2862, 1);
            service.drainInitQueue();

            assertEquals(0, service.initQueueSize(),
                    "drain must empty the init queue");
            assertEquals(2, service.pendingCount(WamChannel.REGULAR),
                    "drained events must land in the REGULAR pending list");
        }

        /**
         * Sampling overrides installed between a pre-init commit and
         * the drain are observed by the drained commit.
         *
         * @apiNote
         * Models the production sequence inside
         * {@link WamService#initialize()}: load AB-props sampling
         * configs, set {@code initialized=true}, then drain. The
         * drained commit re-enters the post-init dispatch path and
         * consults {@link WamSamplingOverride} freshly, so AB-prop
         * weights loaded mid-sequence apply to queued events.
         */
        @Test
        @DisplayName("sampling override installed before drain applies to queued commits")
        void samplingOverrideAppliesToDrainedCommits() {
            var service = newService();
            var event = heavilySampledRegularEvent(8888, 1000);
            service.commit(event);
            assertEquals(1, service.initQueueSize(),
                    "pre-init commit deferred to queue");
            assertEquals(0, service.pendingCount(WamChannel.REGULAR),
                    "deferred commit not yet in pending");

            service.markInitializedForTesting();
            service.setSamplingOverride(8888, 1);
            service.drainInitQueue();

            assertEquals(1, service.pendingCount(WamChannel.REGULAR),
                    "weight=1 override installed before drain must keep the event");
        }

        /**
         * A pre-init
         * {@link WamService#commitAndWaitForFlush(WamEventSpec)}
         * queues the event and returns a non-null future.
         *
         * @implNote
         * The future is not asserted to complete here (completion
         * requires a flush); the test only exercises the
         * {@code whenComplete} bridge that the pre-init path wires
         * between the deferred and live futures.
         */
        @Test
        @DisplayName("pre-init commitAndWaitForFlush queues and returns a non-null future")
        void preInitCommitAndWaitForFlushFuture() {
            var service = newService();
            var future = service.commitAndWaitForFlush(samplePsIdEvent());
            assertNotNull(future, "deferred commitAndWaitForFlush must still return a non-null future");
            assertEquals(1, service.initQueueSize());
            assertEquals(0, service.pendingCount(WamChannel.REGULAR));

            service.markInitializedForTesting();
            service.setSamplingOverride(2862, 1);
            service.drainInitQueue();

            assertEquals(0, service.initQueueSize());
            assertEquals(1, service.pendingCount(WamChannel.REGULAR),
                    "the deferred event must land in pending after the drain");
        }
    }

    /**
     * Lifecycle tests for {@link WamService#close()}.
     */
    @Nested
    @DisplayName("close")
    class CloseLifecycle {
        /**
         * {@link WamService#close()} on a never-initialised service
         * does not throw.
         */
        @Test
        @DisplayName("close on never-initialized service does not throw")
        void closeWithoutInitialize() {
            var service = newService();
            assertDoesNotThrow(service::close);
        }

        /**
         * A second {@link WamService#close()} after the first
         * completes without throwing (idempotency).
         */
        @Test
        @DisplayName("repeated close is idempotent")
        void repeatedClose() {
            var service = newService();
            service.close();
            assertDoesNotThrow(service::close);
        }

        /**
         * {@link WamService#close()} invokes
         * {@link WamService#cancelAllScheduled()} so the captured
         * recurring-task queue on the testable subclass drains to
         * empty.
         */
        @Test
        @DisplayName("close invokes cancelAllScheduled")
        void closeCancelsScheduled() {
            var service = newService();
            service.scheduledRunnables.add(new ScheduledCall(() -> {}, 5, 5));
            service.scheduledRunnables.add(new ScheduledCall(() -> {}, 120, 120));
            service.close();
            assertTrue(service.scheduledRunnables.isEmpty(),
                    "close() must drive cancelAllScheduled to empty the recurring task queue");
        }
    }

    /**
     * Tests covering {@link WamService#checkMidCycleUpload()}, the
     * five-second serialize tick's threshold-driven early-flush
     * behaviour.
     */
    @Nested
    @DisplayName("checkMidCycleUpload")
    class MidCycleEntry {
        /**
         * The local self-PN seed used by the in-memory store
         * harness.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * {@link WamService#checkMidCycleUpload()} pre-init returns
         * silently and leaves the init queue and pending lists
         * untouched.
         */
        @Test
        @DisplayName("no-ops when not initialised, leaves init queue intact")
        void noopWhenNotInitialized() {
            var service = newService();
            service.commit(samplePsIdEvent());
            assertEquals(1, service.initQueueSize());
            assertDoesNotThrow(service::checkMidCycleUpload);
            assertEquals(1, service.initQueueSize(),
                    "checkMidCycleUpload pre-init must not drain the init queue");
            assertEquals(0, service.pendingCount(WamChannel.REGULAR),
                    "checkMidCycleUpload pre-init must not promote queued events");
        }

        /**
         * Below-threshold pending aggregates stay in place and do
         * not trigger an early flush.
         */
        @Test
        @DisplayName("below-threshold pending stays in place")
        void belowThresholdDoesNotFlush() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);

            harness.service.commit(samplePsIdEvent());
            harness.service.commit(samplePsIdEvent());
            harness.service.commit(samplePsIdEvent());
            assertEquals(3, harness.service.pendingCount(WamChannel.REGULAR));

            harness.service.checkMidCycleUpload();

            assertEquals(0, harness.sendNodeCalls.get(),
                    "below-threshold pending should not trigger an early flush");
            assertEquals(3, harness.service.pendingCount(WamChannel.REGULAR),
                    "below-threshold pending must stay queued for the next cycle");
        }

        /**
         * Above-threshold {@link WamChannel#REGULAR} pending
         * triggers an early flush.
         */
        @Test
        @DisplayName("above-threshold REGULAR pending triggers early flush")
        void aboveThresholdTriggersFlush() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();

            harness.service.commit(hugeRegularEvent(55_000));
            assertEquals(1, harness.service.pendingCount(WamChannel.REGULAR));

            harness.service.checkMidCycleUpload();

            assertTrue(harness.sendNodeCalls.get() >= 1,
                    "above-threshold REGULAR pending must trigger an early flush");
            assertEquals(0, harness.service.pendingCount(WamChannel.REGULAR),
                    "early flush must drain the REGULAR pending list");
        }

        /**
         * The mid-cycle sweep skips {@link WamChannel#REALTIME} and
         * continues across without throwing.
         *
         * @implNote
         * Asserting the skip via a queued REALTIME event would race
         * against the production immediate-flush virtual thread, so
         * the test instead drives a small REGULAR commit and
         * verifies the sweep completes without sending any node.
         */
        @Test
        @DisplayName("REALTIME channel is skipped by the mid-cycle sweep")
        void realtimeIsSkipped() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            harness.service.commit(samplePsIdEvent());
            assertDoesNotThrow(harness.service::checkMidCycleUpload);
            assertEquals(0, harness.sendNodeCalls.get(),
                    "small commits should not trigger mid-cycle flush");
        }
    }

    /**
     * Asserts per-event channel parity against the captured
     * live WAM registry.
     *
     * @apiNote
     * For every event in {@code wam-event-definitions.json} that
     * resolves to a Cobalt {@link WamEventSpec}, the
     * {@link WamEventSpec#channel()} reported by Cobalt must equal
     * the channel string declared by WA Web's
     * {@code WAWebWamCodegenUtils.events} (case-insensitive match
     * against {@link WamChannel}). The factory catches regressions
     * where a Cobalt {@code @WamEvent} annotation defaults a channel
     * (typically REGULAR) for an event WA Web actually classifies as
     * REALTIME or PRIVATE.
     *
     * @implNote
     * The factory degrades to a single skip-test when the corpus
     * fixture is unavailable; this lets contributors run the suite
     * without the captured live registry installed.
     *
     * @return one {@link DynamicTest} per event id in the fixture,
     *         or a single skip-test when the corpus is missing
     */
    @TestFactory
    @DisplayName("channel pinning vs live WAM registry")
    List<DynamicTest> channelPinningAgainstLiveRegistry() {
        if (!WamFixtures.isAvailable("wam-event-definitions.json")) {
            return List.of(dynamicTest(
                    "wam-event-definitions.json not available",
                    () -> { /* skip when corpus missing */ }));
        }
        var fixture = WamFixtures.loadOracle("wam-event-definitions");
        var events = fixture.getJSONArray("events");
        var tests = new ArrayList<DynamicTest>();
        for (var entry : events) {
            var event = (JSONObject) entry;
            var id = event.get("id");
            var liveChannel = event.getString("channel");
            if (id == null || liveChannel == null) {
                continue;
            }
            var label = event.getString("name") + "(id=" + id + ")";
            tests.add(dynamicTest(label, () -> assertChannelMatches(
                    event.getString("name"),
                    ((Number) id).intValue(),
                    liveChannel)));
        }
        return tests;
    }

    /**
     * Decodes a minimal event marker for the given id and asserts
     * the resulting spec's channel matches the live channel string.
     *
     * @apiNote
     * Helper for {@link #channelPinningAgainstLiveRegistry()}; the
     * minimal marker carries no fields, so the registry must report
     * the spec on the event id alone.
     *
     * @param eventName   the live event name, used only for
     *                    assertion-failure messages
     * @param eventId     the wire event id
     * @param liveChannel the live channel string
     *                    ({@code "regular"}, {@code "realtime"}, or
     *                    {@code "private"})
     */
    private static void assertChannelMatches(String eventName, int eventId, String liveChannel) {
        var buffer = new byte[8];
        var encoder = WamEventEncoder.of(buffer);
        encoder.writeEventMarker(eventId, 0, false);
        var decoder = WamEventDecoder.of(buffer, 0, encoder.written());
        var spec = WamEventRegistry.decode(decoder);
        var expected = WamChannel.valueOf(liveChannel.toUpperCase(Locale.ROOT));
        assertSame(expected, spec.channel(),
                () -> "channel drift for " + eventName + " (id=" + eventId
                        + "): Cobalt=" + spec.channel() + " live=" + expected);
    }

    /**
     * Builds a fresh {@code PsIdUpdate} event with the
     * {@link PsIdAction#CREATED} action for use in commit tests.
     *
     * @apiNote
     * Each call returns a new instance so the spec's
     * {@code markCommitted()} guard does not reject repeat commits in
     * tests that need to push more than one event.
     *
     * @return a fresh {@code PsIdUpdate} event spec
     */
    private static WamEventSpec samplePsIdEvent() {
        return new PsIdUpdateEventBuilder()
                .psIdAction(PsIdAction.CREATED)
                .psIdKey(42)
                .psIdRotationFrequence(7)
                .build();
    }

    /**
     * Constructs a fresh {@link TestableWamService} bound to a
     * trivially wired {@link TestWhatsAppClient} and
     * {@link TestABPropsService}.
     *
     * @apiNote
     * Used by the unit-level state-machine tests that do not need a
     * real store or canned-response IQ pipeline; the heavier
     * {@link #newRealisticHarness(Jid)} factory is used by the
     * flush/retry tests.
     *
     * @return a new testable WAM service
     */
    private static TestableWamService newService() {
        var props = TestABPropsService.builder().build();
        var client = TestWhatsAppClient.create().withAbPropsService(props);
        return new TestableWamService(client, props, new DefaultWamBeaconing());
    }

    /**
     * Test double for {@link WamService} that stubs the four
     * abstract timing/scheduling hooks with deterministic capture
     * lists.
     *
     * @apiNote
     * Allows tests to inspect {@link #sleepRequests} and
     * {@link #scheduledRunnables} after exercising the service;
     * neither {@link #sleep(long)} nor
     * {@link #scheduleRecurring(Runnable, long, long)} actually
     * blocks or schedules.
     */
    private static final class TestableWamService extends WamService {
        /**
         * The current virtual instant returned by {@link #now()};
         * advanced by {@link #sleep(long)} so the connectivity-wait
         * deadline check terminates deterministically.
         */
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochSecond(1_747_000_000L));

        /**
         * The captured sleep requests in milliseconds, in submission
         * order.
         */
        final List<Long> sleepRequests = new ArrayList<>();

        /**
         * The captured recurring-task schedule requests in
         * submission order.
         */
        final Queue<ScheduledCall> scheduledRunnables = new ConcurrentLinkedQueue<>();

        /**
         * Constructs the testable service.
         *
         * @param client    the bound test client
         * @param props     the bound test AB-props service
         * @param beaconing the beaconing implementation
         */
        TestableWamService(WhatsAppClient client, ABPropsService props, WamBeaconing beaconing) {
            super(client, props, beaconing);
        }

        @Override
        protected Instant now() {
            return now.get();
        }

        @Override
        protected void sleep(long millis) {
            sleepRequests.add(millis);
            now.updateAndGet(current -> current.plusMillis(millis));
        }

        @Override
        protected void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds) {
            scheduledRunnables.add(new ScheduledCall(task, initialDelaySeconds, periodSeconds));
        }

        @Override
        protected void cancelAllScheduled() {
            scheduledRunnables.clear();
        }
    }

    /**
     * One captured
     * {@link WamService#scheduleRecurring(Runnable, long, long)}
     * invocation.
     *
     * @apiNote
     * Tests pop entries off
     * {@link TestableWamService#scheduledRunnables} and assert the
     * captured task and timing arguments against the production
     * call site.
     *
     * @param task                the task that would have been
     *                            scheduled
     * @param initialDelaySeconds the requested initial delay in
     *                            seconds
     * @param periodSeconds       the requested recurring period in
     *                            seconds
     */
    private record ScheduledCall(Runnable task, long initialDelaySeconds, long periodSeconds) {
    }

    /**
     * Per-channel sequence-counter tests covering monotonic
     * advancement, wrap from {@code MAX_SEQUENCE_NUMBER}
     * ({@code 0xFFFF}) back to {@code 1}, and independence across
     * the three channels.
     */
    @Nested
    @DisplayName("sequence numbers")
    class SequenceNumbers {
        /**
         * The local self-PN seed used by the in-memory store
         * harness.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * Each successful {@link WamChannel#REGULAR} flush advances
         * the channel's sequence counter by exactly one.
         */
        @Test
        @DisplayName("REGULAR flush advances the counter by 1 per buffer")
        void counterAdvancesPerFlush() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);

            var before = harness.service.sequenceNumberFor(WamChannel.REGULAR);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);
            var afterOne = harness.service.sequenceNumberFor(WamChannel.REGULAR);
            assertEquals(before + 1, afterOne, "first flush bumps the counter by 1");

            harness.service.setSamplingOverride(2862, 1);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);
            assertEquals(afterOne + 1, harness.service.sequenceNumberFor(WamChannel.REGULAR),
                    "second flush bumps the counter by 1 again");
        }

        /**
         * The per-channel sequence counters advance independently;
         * a flush on one channel does not move the other channels'
         * counters.
         */
        @Test
        @DisplayName("REGULAR / REALTIME / PRIVATE counters are independent")
        void countersAreIndependent() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);

            var realtimeBefore = harness.service.sequenceNumberFor(WamChannel.REALTIME);
            var privateBefore = harness.service.sequenceNumberFor(WamChannel.PRIVATE);

            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);

            assertEquals(realtimeBefore, harness.service.sequenceNumberFor(WamChannel.REALTIME),
                    "REGULAR flush must not touch the REALTIME counter");
            assertEquals(privateBefore, harness.service.sequenceNumberFor(WamChannel.PRIVATE),
                    "REGULAR flush must not touch the PRIVATE counter");
        }

        /**
         * A counter at {@code MAX_SEQUENCE_NUMBER} wraps to
         * {@code 1} (not {@code 0}) on the next flush.
         */
        @Test
        @DisplayName("counter at MAX_SEQUENCE_NUMBER wraps to 1 on next flush")
        void counterWrapsToOne() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);

            harness.service.setSequenceNumberForTesting(WamChannel.REGULAR, 0xFFFF);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);

            assertEquals(1, harness.service.sequenceNumberFor(WamChannel.REGULAR),
                    "counter must wrap from 0xFFFF back to 1, not to 0 or 0x10000");
        }
    }

    /**
     * Channel-routing tests covering the divergent commit paths for
     * {@link WamChannel#REALTIME} (immediate virtual-thread flush)
     * and {@link WamChannel#REGULAR} (accumulate-until-flush).
     *
     * @apiNote
     * {@link WamChannel#PRIVATE} bucketing is not exercised here
     * because private events upload through the HTTP-only
     * {@link com.github.auties00.cobalt.wam.privatestats.WamPrivateStatsUploader},
     * which does not surface through
     * {@code TestWhatsAppClient.sendNode}.
     */
    @Nested
    @DisplayName("channel routing")
    class ChannelRouting {
        /**
         * The local self-PN seed used by the in-memory store
         * harness.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * A {@link WamChannel#REGULAR} commit accumulates into the
         * pending list without triggering an immediate
         * {@code sendNode}.
         */
        @Test
        @DisplayName("REGULAR commit accumulates, no immediate flush")
        void regularAccumulatesUntilFlush() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);

            harness.service.commit(samplePsIdEvent());

            assertEquals(0, harness.sendNodeCalls.get(),
                    "REGULAR commit must not trigger sendNode synchronously");
            assertEquals(1, harness.service.pendingCount(WamChannel.REGULAR));
        }

        /**
         * A {@link WamChannel#REALTIME} commit spawns a virtual
         * thread that ultimately reaches {@code sendNode}.
         *
         * @implNote
         * The assertion waits up to five seconds on a latch the
         * {@code sendNodeHandler} counts down on every captured
         * invocation.
         *
         * @throws InterruptedException if the test is interrupted
         *                              waiting on the latch
         */
        @Test
        @DisplayName("REALTIME commit triggers immediate flush via virtual thread")
        void realtimeTriggersImmediateFlush() throws InterruptedException {
            var harness = newRealisticHarness(selfPn);
            var latch = new CountDownLatch(1);
            harness.client.withSendNodeHandler(builder -> {
                harness.sendNodeCalls.incrementAndGet();
                latch.countDown();
                return successResponse();
            });
            harness.service.markInitializedForTesting();

            harness.service.commit(realtimeEvent(9999));

            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "REALTIME commit must trigger sendNode within 5s via the virtual-thread flush spawn");
        }

        /**
         * Three {@link WamChannel#REALTIME} commits all drain
         * through the upload pipeline without losing any event.
         *
         * @apiNote
         * Mirrors WA Web's {@code WAWam} commit handler which pushes
         * REALTIME events onto a shared pending list and schedules a
         * single {@code setTimeout(forceRunNow("realtime"), 1)};
         * multiple commits within the same tick all batch into one
         * upload, so the assertion only requires
         * {@code sendNodeCalls >= 1} and {@code pendingCount == 0}.
         *
         * @implNote
         * Cobalt's parallel of the upstream batching is
         * {@code Thread.ofVirtual().start(() -> flushChannel(REALTIME))}
         * plus the atomic drain in
         * {@link WamService}'s
         * {@code swapPending}: the first virtual thread to reach
         * {@code swapPending} takes every queued event, later
         * threads observe an empty list and return.
         *
         * @throws InterruptedException if the test is interrupted
         *                              waiting on the upload latch
         */
        @Test
        @DisplayName("three REALTIME commits all drain through the pipeline")
        void multipleRealtimeCommitsAllFlush() throws InterruptedException {
            var harness = newRealisticHarness(selfPn);
            var firstUpload = new CountDownLatch(1);
            harness.client.withSendNodeHandler(builder -> {
                harness.sendNodeCalls.incrementAndGet();
                firstUpload.countDown();
                return successResponse();
            });
            harness.service.markInitializedForTesting();

            harness.service.commit(realtimeEvent(9991));
            harness.service.commit(realtimeEvent(9992));
            harness.service.commit(realtimeEvent(9993));

            assertTrue(firstUpload.await(5, TimeUnit.SECONDS),
                    "at least one REALTIME upload must reach sendNode within 5s "
                            + "(WA Web's setTimeout(1ms)+atomic-drain batches 3 commits "
                            + "into a single forceRunNow tick)");
            Thread.sleep(200);
            assertEquals(0, harness.service.pendingCount(WamChannel.REALTIME),
                    "every committed REALTIME event must drain (no events left in pending)");
            assertTrue(harness.sendNodeCalls.get() >= 1,
                    "at least one sendNode call must have been made; got "
                            + harness.sendNodeCalls.get());
        }
    }

    /**
     * Builds a synthetic {@link WamChannel#REALTIME}-channel
     * {@link WamEventSpec} with the given wire event id and no
     * fields.
     *
     * @apiNote
     * Used by {@link ChannelRouting} to drive the immediate-flush
     * path on a known-realtime spec without depending on a generated
     * realtime event class.
     *
     * @implNote
     * The {@code markCommitted()} guard only permits one positive
     * call; the deferred-into-init-queue replay re-enters
     * {@link WamService#commit(WamEventSpec)} which calls
     * {@code markCommitted()} again, but the second call returns
     * {@code false} and the deferred-replay branch handles the
     * redundant-commit log without re-enqueuing.
     *
     * @param eventId the wire event id
     * @return a fresh REALTIME event spec
     */
    private static WamEventSpec realtimeEvent(int eventId) {
        return new WamEventSpec() {
            private volatile boolean committed;

            @Override
            public int id() {
                return eventId;
            }

            @Override
            public WamChannel channel() {
                return WamChannel.REALTIME;
            }

            @Override
            public int alphaWeight() {
                return 1;
            }

            @Override
            public int betaWeight() {
                return 1;
            }

            @Override
            public int releaseWeight() {
                return 1;
            }

            @Override
            public int privateStatsId() {
                return -1;
            }

            @Override
            public boolean markCommitted() {
                if (committed) {
                    return false;
                }
                committed = true;
                return true;
            }

            @Override
            public int sizeOf(int weight) {
                return WamEventSizes.eventMarkerSize(eventId, weight);
            }

            @Override
            public void encode(WamEventEncoder encoder, int weight) {
                encoder.writeEventMarker(eventId, weight, false);
            }
        };
    }

    /**
     * Retry and backoff tests driving the full
     * {@link WamService}'s
     * {@code flushChannel} pipeline against an in-memory store and a
     * controllable {@code sendNode} response sequence.
     *
     * @apiNote
     * Captured {@link TestableWamService#sleepRequests} are asserted
     * against the expected exponential-backoff delays computed by
     * {@link WamService#computeBackoffDelay(int)}.
     */
    @Nested
    @DisplayName("retry / backoff")
    class RetryBackoff {
        /**
         * The local self-PN seed used by the in-memory store
         * harness.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * {@link WamService#computeBackoffDelay(int)} clamps low and
         * high and stays within the ten-percent jitter band.
         *
         * @apiNote
         * Probes the three documented regions: low clamp
         * ({@code 2^attempt < base}), the unclamped middle band, and
         * high clamp ({@code 2^attempt > max}).
         */
        @Test
        @DisplayName("computeBackoffDelay clamps low/high and lies within the 10% jitter band")
        void backoffFormulaBounds() {
            for (var attempt = 0; attempt < 4; attempt++) {
                var actual = WamService.computeBackoffDelay(attempt);
                assertTrue(actual >= 1_000 && actual <= 1_100,
                        "attempt=" + attempt + " should clamp to base [1000, 1100], was " + actual);
            }
            var mid = WamService.computeBackoffDelay(10);
            assertTrue(mid >= 1_024 && mid <= (long) (1_024 * 1.1),
                    "attempt=10 should be in [1024, ~1126], was " + mid);
            var clamped = WamService.computeBackoffDelay(20);
            assertTrue(clamped >= 120_000 && clamped <= (long) (120_000 * 1.1),
                    "attempt=20 should clamp to max [120_000, 132_000], was " + clamped);
        }

        /**
         * Two {@code 5xx} responses are retried with backoff and a
         * third successful response terminates the retry loop.
         *
         * @apiNote
         * Asserts the cumulative invocation contract: three
         * {@code sendNode} calls (two failures plus one success) and
         * two captured backoff sleeps in the documented range.
         */
        @Test
        @DisplayName("two 5xx responses are retried with backoff, third success terminates")
        void retryUntilSuccess() {
            var harness = newRealisticHarness(selfPn);
            harness.responsesQueue.add(errorResponse(500));
            harness.responsesQueue.add(errorResponse(503));
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);

            assertEquals(3, harness.sendNodeCalls.get(),
                    "expected three sendNode invocations: two failures + one success");
            assertEquals(2, harness.service.sleepRequests.size(),
                    "expected two backoff sleeps before the successful send");
            for (var delay : harness.service.sleepRequests) {
                assertTrue(delay >= 1_000 && delay <= 132_000,
                        () -> "captured backoff delay " + delay + " outside [1000, 132000]");
            }
        }

        /**
         * A {@code 4xx} response does not retry and the buffer is
         * dropped with a {@code WamClientErrors} drop-counter event
         * re-committed.
         */
        @Test
        @DisplayName("4xx response does not retry; buffer is dropped")
        void permanentErrorDropsBuffer() {
            var harness = newRealisticHarness(selfPn);
            harness.responsesQueue.add(errorResponse(400));
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);

            assertEquals(0, harness.service.sleepRequests.size(),
                    "4xx is permanent, no backoff sleep should be requested");
            assertTrue(harness.sendNodeCalls.get() >= 1,
                    "at least the original buffer upload must have been attempted");
        }
    }

    /**
     * Connectivity-wait tests covering
     * {@link WamService}'s
     * {@code waitIfDisconnected}, which polls the client in
     * one-second steps up to the 30-second timeout.
     */
    @Nested
    @DisplayName("connectivity wait")
    class Connectivity {
        /**
         * The local self-PN seed used by the in-memory store
         * harness.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * No sleep is captured when the client is connected on
         * entry.
         */
        @Test
        @DisplayName("connected on entry: no sleeps recorded")
        void connectedOnEntry() {
            var harness = newRealisticHarness(selfPn);
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);

            assertEquals(0, harness.service.sleepRequests.size(),
                    "no waitIfDisconnected sleeps should fire when client is connected");
        }

        /**
         * A permanently-disconnected client produces a sequence of
         * one-second sleeps until the 30-second deadline elapses
         * and the upload then proceeds.
         *
         * @apiNote
         * Each captured sleep is exactly {@code 1_000} milliseconds
         * (the production {@code Thread.sleep(1_000)} call site)
         * and at least {@code 30} sleeps are captured because the
         * testable {@link TestableWamService#sleep(long)} advances
         * the virtual clock by the requested duration.
         */
        @Test
        @DisplayName("disconnected on entry: 1s sleeps until deadline, then upload proceeds")
        void disconnectedUntilDeadline() {
            var harness = newRealisticHarness(selfPn);
            harness.client.withIsConnected(false);
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);

            var sleeps = harness.service.sleepRequests;
            assertTrue(sleeps.size() >= 30,
                    () -> "expected >= 30 connectivity sleeps (one per second of the 30s timeout), got " + sleeps.size());
            for (var delay : sleeps) {
                assertEquals(1_000L, delay,
                        () -> "every connectivity-wait sleep must be exactly 1000ms, was " + delay);
            }
            assertEquals(1, harness.sendNodeCalls.get(),
                    "upload should be attempted once after the connectivity-wait deadline elapses");
        }
    }

    /**
     * Buffer rotation and drop tests driving the encoder pipeline
     * with enough payload to cross the rotation threshold
     * ({@code MAX_BUFFER_SIZE = 50_000}) and the upload-drop
     * threshold ({@code MAX_UPLOAD_SIZE = 64_000}).
     */
    @Nested
    @DisplayName("buffer rotation / drop")
    class BufferRotation {
        /**
         * The local self-PN seed used by the in-memory store
         * harness.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * A flush whose aggregate payload exceeds
         * {@code MAX_BUFFER_SIZE} splits into multiple
         * {@code sendNode} calls.
         */
        @Test
        @DisplayName("flush across MAX_BUFFER_SIZE splits into multiple sendNode calls")
        void multipleBuffersAcrossMaxBufferSize() {
            var harness = newRealisticHarness(selfPn);
            harness.responsesQueue.add(successResponse());
            harness.alwaysSucceed = true;

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(1144, 1);

            for (var i = 0; i < 8000; i++) {
                var event = new WamClientErrorsEventBuilder()
                        .wamClientBufferDropErrorCount(1)
                        .build();
                harness.service.commit(event);
            }
            harness.service.flushChannel(WamChannel.REGULAR);

            assertTrue(harness.sendNodeCalls.get() >= 2,
                    "aggregate payload above MAX_BUFFER_SIZE must split into >= 2 sendNode calls, was " + harness.sendNodeCalls.get());
            assertEquals(0, harness.service.pendingCount(WamChannel.REGULAR),
                    "all committed events should be drained after flush");
        }

        /**
         * A successful flush drains the pending list to zero,
         * regardless of the number of physical buffers sent.
         */
        @Test
        @DisplayName("flush drains pending to zero on success")
        void flushDrainsPending() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);
            harness.service.commit(samplePsIdEvent());
            harness.service.commit(samplePsIdEvent());
            harness.service.commit(samplePsIdEvent());

            assertEquals(3, harness.service.pendingCount(WamChannel.REGULAR));
            harness.service.flushChannel(WamChannel.REGULAR);
            assertEquals(0, harness.service.pendingCount(WamChannel.REGULAR),
                    "successful flush must drain pending to zero");
        }

        /**
         * An event whose encoded size exceeds {@code MAX_UPLOAD_SIZE}
         * is dropped without uploading and a
         * {@link WamClientErrorsEventBuilder} drop-counter event is
         * re-committed into REGULAR pending for the next cycle.
         *
         * @implNote
         * Flow: the aggregate exceeds {@code MAX_BUFFER_SIZE} so
         * {@code flushEventList} calls {@code buildAndSend} once;
         * inside {@code buildAndSend} the precomputed size exceeds
         * {@code MAX_UPLOAD_SIZE} so the buffer is dropped before any
         * {@code sendNode} call and the drop-counter event is
         * re-committed for the next cycle.
         */
        @Test
        @DisplayName("buffer >64KB is dropped, drop-counter event re-committed")
        void oversizedBufferDropsAndRecommitsCounter() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(1144, 1);

            var huge = hugeRegularEvent(64_500);
            harness.service.commit(huge);
            assertEquals(1, harness.service.pendingCount(WamChannel.REGULAR));

            harness.service.flushChannel(WamChannel.REGULAR);

            assertEquals(0, harness.sendNodeCalls.get(),
                    "oversized buffer must be dropped before any sendNode call");
            assertEquals(1, harness.service.pendingCount(WamChannel.REGULAR),
                    "the drop-counter WamClientErrorsEvent should land in REGULAR pending after the drop");
        }
    }

    /**
     * Builds a synthetic {@link WamChannel#REGULAR} event spec with
     * a configurable {@code releaseWeight} so sampling-override
     * tests can observe the keep/drop probability change.
     *
     * @apiNote
     * A {@code releaseWeight} of {@code 1} always keeps; higher
     * values produce a probabilistic drop that the test counteracts
     * by installing a {@code weight=1} override on the same id.
     *
     * @param eventId       the wire event id
     * @param releaseWeight the sampling weight reported by
     *                      {@link WamEventSpec#releaseWeight()}
     * @return a fresh REGULAR event spec
     */
    private static WamEventSpec heavilySampledRegularEvent(int eventId, int releaseWeight) {
        return new WamEventSpec() {
            private volatile boolean committed;

            @Override
            public int id() {
                return eventId;
            }

            @Override
            public WamChannel channel() {
                return WamChannel.REGULAR;
            }

            @Override
            public int alphaWeight() {
                return releaseWeight;
            }

            @Override
            public int betaWeight() {
                return releaseWeight;
            }

            @Override
            public int releaseWeight() {
                return releaseWeight;
            }

            @Override
            public int privateStatsId() {
                return -1;
            }

            @Override
            public boolean markCommitted() {
                if (committed) {
                    return false;
                }
                committed = true;
                return true;
            }

            @Override
            public int sizeOf(int weight) {
                return WamEventSizes.eventMarkerSize(eventId, weight);
            }

            @Override
            public void encode(WamEventEncoder encoder, int weight) {
                encoder.writeEventMarker(eventId, weight, false);
            }
        };
    }

    /**
     * Builds a synthetic {@link WamChannel#REGULAR} event spec
     * carrying a single string field of the requested approximate
     * size.
     *
     * @apiNote
     * Used by the buffer-rotation and oversized-buffer tests; the
     * actual encoded length is the string length plus the event
     * marker and the {@code str32} length prefix (~10 bytes
     * overhead).
     *
     * @param targetSize the desired payload size in bytes
     * @return a fresh REGULAR event spec carrying a string of the
     *         requested length
     */
    private static WamEventSpec hugeRegularEvent(int targetSize) {
        var payload = "a".repeat(targetSize);
        return new WamEventSpec() {
            private volatile boolean committed;

            @Override
            public int id() {
                return 9000;
            }

            @Override
            public WamChannel channel() {
                return WamChannel.REGULAR;
            }

            @Override
            public int alphaWeight() {
                return 1;
            }

            @Override
            public int betaWeight() {
                return 1;
            }

            @Override
            public int releaseWeight() {
                return 1;
            }

            @Override
            public int privateStatsId() {
                return -1;
            }

            @Override
            public boolean markCommitted() {
                if (committed) {
                    return false;
                }
                committed = true;
                return true;
            }

            @Override
            public int sizeOf(int weight) {
                return WamEventSizes.eventMarkerSize(9000, weight)
                        + WamEventSizes.stringFieldSize(1, payload);
            }

            @Override
            public void encode(WamEventEncoder encoder, int weight) {
                encoder.writeEventMarker(9000, weight, true);
                encoder.writeStringField(1, payload, false);
            }
        };
    }

    /**
     * Realistic-store harness used by the retry, rotation, and
     * connectivity tests; wires a real in-memory store and a
     * controllable canned-response IQ pipeline.
     */
    private static final class RealisticHarness {
        /**
         * The bound test client.
         */
        final TestWhatsAppClient client;

        /**
         * The testable WAM service under test.
         */
        final TestableWamService service;

        /**
         * The FIFO queue of canned IQ responses drained on each
         * {@code sendNode} invocation.
         */
        final Deque<Node> responsesQueue;

        /**
         * The cumulative count of {@code sendNode} invocations.
         */
        final AtomicInteger sendNodeCalls;

        /**
         * When {@code true}, {@code sendNode} returns a fresh
         * success response once {@link #responsesQueue} is empty;
         * when {@code false} (the default) the handler throws.
         */
        boolean alwaysSucceed;

        /**
         * Constructs the harness from its fully-wired collaborators.
         *
         * @param client         the bound test client
         * @param service        the testable WAM service
         * @param responsesQueue the canned IQ-response queue
         * @param sendNodeCalls  the cumulative invocation counter
         */
        RealisticHarness(
                TestWhatsAppClient client,
                TestableWamService service,
                Deque<Node> responsesQueue,
                AtomicInteger sendNodeCalls) {
            this.client = client;
            this.service = service;
            this.responsesQueue = responsesQueue;
            this.sendNodeCalls = sendNodeCalls;
        }
    }

    /**
     * Builds a {@link RealisticHarness} bound to a real in-memory
     * store and a canned-response IQ pipeline.
     *
     * @apiNote
     * The store lets the flush path's {@code putWamSequenceNumber}
     * and persisted-buffer writes land in real backing storage,
     * which the retry and rotation tests rely on. The client is
     * pre-wired with {@code isConnected=true} so connectivity-wait
     * tests that opt into the disconnected branch must explicitly
     * call {@link TestWhatsAppClient#withIsConnected(boolean)}.
     *
     * @param selfPn the local user's PN-form JID used to seed the
     *               temporary store
     * @return a fresh realistic harness
     */
    private static RealisticHarness newRealisticHarness(Jid selfPn) {
        var props = TestABPropsService.builder().build();
        var responses = new ArrayDeque<Node>();
        var calls = new AtomicInteger();
        var harnessRef = new RealisticHarness[1];
        var store = DeviceFixtures.temporaryStore(selfPn, null);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(props)
                .withIsConnected(true)
                .withSendNodeHandler(builder -> {
                    calls.incrementAndGet();
                    var next = responses.poll();
                    if (next != null) {
                        return next;
                    }
                    if (harnessRef[0] != null && harnessRef[0].alwaysSucceed) {
                        return successResponse();
                    }
                    throw new IllegalStateException("realistic harness ran out of canned responses for sendNode");
                });
        var service = new TestableWamService(client, props, new DefaultWamBeaconing());
        harnessRef[0] = new RealisticHarness(client, service, responses, calls);
        return harnessRef[0];
    }

    /**
     * Builds a successful IQ response recognised by
     * {@link WamService}'s
     * {@code sendWithRetry} ({@code type="result"} on the root
     * {@code <iq>}).
     *
     * @return the success node
     */
    private static Node successResponse() {
        return new NodeBuilder()
                .description("iq")
                .attribute("type", "result")
                .build();
    }

    /**
     * Builds an error IQ response with the given HTTP-style code.
     *
     * @apiNote
     * The error code is embedded in an
     * {@code <error code="..."/>} child node;
     * {@link WamService}'s
     * {@code sendWithRetry} dispatches to retry for {@code >= 500}
     * and to permanent-drop for everything else.
     *
     * @param code the HTTP-style error code
     * @return the error node
     */
    private static Node errorResponse(int code) {
        var error = new NodeBuilder()
                .description("error")
                .attribute("code", String.valueOf(code))
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("type", "error")
                .content(error)
                .build();
    }
}
