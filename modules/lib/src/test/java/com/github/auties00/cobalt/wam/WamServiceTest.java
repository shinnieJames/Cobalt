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
import com.github.auties00.cobalt.wam.event.PsIdUpdateEventBuilder;
import com.github.auties00.cobalt.wam.event.WamClientErrorsEventBuilder;
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
 * Behavioural tests for {@link WamService} exercised through a
 * {@link TestableWamService} subclass that overrides the four
 * abstract timing/scheduling methods with deterministic stubs.
 *
 * <p>The intent is to verify state-machine behaviour
 * (commit dispatch, sampling-override surface, channel routing,
 * close idempotency) without depending on a real
 * {@link java.util.concurrent.ScheduledExecutorService} or
 * {@link Thread#sleep}.
 *
 * <p>End-to-end tests that exercise the encoder pipeline, persistent
 * buffer storage, retry/backoff, and live IQ upload are out of
 * scope here. Retry/backoff in particular requires a
 * {@link TestWhatsAppClient} extension that lets a test override
 * {@code isConnected()} and feed a controllable {@code sendNode}
 * response sequence — that scaffolding is documented as a TODO at
 * the bottom of this file.
 */
@DisplayName("WamService behavioural state machine")
class WamServiceTest {
    /**
     * Constructor tests.
     */
    @Nested
    @DisplayName("constructor")
    class ConstructorTests {
        /**
         * Verifies that {@link DefaultWamService}'s public
         * {@code (client, abPropsService)} constructor builds a
         * service without throwing.
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
         * Verifies that the protected
         * {@code (client, abPropsService, beaconing)} ctor accepts a
         * substituted {@link WamBeaconing} implementation.
         */
        @Test
        @DisplayName("protected ctor accepts a substituted WamBeaconing")
        void protectedCtorAcceptsCustomBeaconing() {
            var props = TestABPropsService.builder().build();
            var client = TestWhatsAppClient.create();
            var beaconing = (WamBeaconing) _ -> java.util.OptionalLong.empty();
            var service = assertDoesNotThrow(() -> new TestableWamService(client, props, beaconing));
            assertNotNull(service);
        }
    }

    /**
     * Sampling-override surface tests.
     */
    @Nested
    @DisplayName("setSamplingOverride / removeSamplingOverride / replaceSamplingOverrides")
    class SamplingOverrideSurface {
        /**
         * Verifies the public sampling-override API does not throw
         * on representative input shapes.
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
         * Verifies that a sampling override with weight {@code 1}
         * (i.e., never sample out) lets the next commit reach the
         * pending list.
         *
         * <p>The high-weight branch is probabilistic, so the test
         * only asserts the deterministic case: a {@code weight=1}
         * override leaves the committed event in pending.
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
     * Commit-dispatch tests covering REGULAR channel routing after
     * the service has been marked initialized.
     *
     * <p>Pre-init dispatch (which defers into {@link WamService}'s
     * init queue) is covered separately by {@link InitQueueReplay}.
     */
    @Nested
    @DisplayName("commit dispatch (post-init)")
    class CommitDispatch {
        /**
         * Verifies that a fresh service has an empty pending list
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
         * Verifies that committing a REGULAR-channel event adds it
         * to the channel's pending list. Uses {@code weight=1} via
         * an override to remove the probabilistic sampling branch.
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
         * Verifies that a redundant commit of the same event spec is
         * silently dropped — the second {@code markCommitted} returns
         * false and the pending list size does not grow.
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
         * Verifies that distinct event instances both land — there is
         * no de-duplication at the spec level.
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
         * Verifies that {@link WamService#commitAndWaitForFlush}
         * returns a non-null future and queues the event into
         * pending just like {@code commit}.
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
         * Verifies that committing a
         * {@link WamClientErrorsEventBuilder}-built event also lands
         * — covers a second representative event type beyond
         * PsIdUpdate.
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
     * Init-queue tests covering the WhatsApp Web
     * {@code WAWebWamInitQueue} fallback path: when a commit arrives
     * before {@code initialize()} has run, the deferral is queued
     * for later replay against the now-ready runtime.
     *
     * <p>Cobalt routes {@link WamService#commit} and
     * {@link WamService#commitAndWaitForFlush} through this fallback
     * whenever the {@code initialized} flag is {@code false},
     * matching the {@code WAWebWamCodegenWamEvent.commit} branch
     * that does {@code getWamRuntime() ?? queueEvent}.
     */
    @Nested
    @DisplayName("init queue (WAWebWamInitQueue fallback)")
    class InitQueueReplay {
        /**
         * Verifies that {@code commit} before
         * {@code markInitializedForTesting} pushes into the init
         * queue rather than the pending list.
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
         * Verifies that multiple pre-init commits accumulate in the
         * init queue in FIFO order.
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
         * Verifies that draining the init queue after marking the
         * service initialised re-runs each deferred commit against
         * the now-ready runtime: events end up in {@code pending}
         * and the init queue ends empty.
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
         * Verifies that sampling overrides loaded into the service
         * before the init-queue drain are observed by the drained
         * commits. The deferred commit re-enters the post-init
         * {@code commit} path, which consults
         * {@link WamSamplingOverride} freshly — so any override
         * installed between the original (pre-init) commit and the
         * drain takes effect.
         *
         * <p>Models the production sequence
         * {@code initialize()} runs: load AB-props
         * {@code samplingConfigs} → set {@code initialized=true} →
         * drain init queue. AB-props weights apply to queued
         * commits.
         */
        @Test
        @DisplayName("sampling override installed before drain applies to queued commits")
        void samplingOverrideAppliesToDrainedCommits() {
            var service = newService();
            // Synthetic event with releaseWeight = 1000: under the
            // default sampling, ~999/1000 commits would be dropped.
            // With a weight=1 override installed before drain, the
            // event must always pass.
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
         * Verifies that {@code commitAndWaitForFlush} called before
         * initialise returns a non-null future that is later
         * completed by the drained commit's inner future
         * pipeline.
         *
         * <p>The future does not actually complete in this test —
         * completion requires a flush to land — but the
         * {@code whenComplete} chain wired by the pre-init path is
         * exercised through the drain.
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
     * Close lifecycle tests.
     */
    @Nested
    @DisplayName("close")
    class CloseLifecycle {
        /**
         * Verifies that {@code close} on a never-initialized service
         * does not throw.
         */
        @Test
        @DisplayName("close on never-initialized service does not throw")
        void closeWithoutInitialize() {
            var service = newService();
            assertDoesNotThrow(service::close);
        }

        /**
         * Verifies idempotent close: a second close after the first
         * one completes without throwing.
         */
        @Test
        @DisplayName("repeated close is idempotent")
        void repeatedClose() {
            var service = newService();
            service.close();
            assertDoesNotThrow(service::close);
        }

        /**
         * Verifies that {@code close} invokes
         * {@link WamService#cancelAllScheduled()} by draining the
         * captured-schedule queue on the testable subclass.
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
     * Mid-cycle upload tests covering the 5-second serialize
     * tick's threshold-driven early flush.
     */
    @Nested
    @DisplayName("checkMidCycleUpload")
    class MidCycleEntry {
        /**
         * Local self-PN for the in-memory store.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * Verifies that {@code checkMidCycleUpload} returns silently
         * when the service has not been initialised — matches the
         * production code path's {@code !initialized} early return —
         * and does not promote queued events into pending.
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
         * Verifies that {@code checkMidCycleUpload} does NOT
         * trigger a flush when the channel's aggregate pending
         * size is below {@code MAX_BUFFER_SIZE} (50_000).
         */
        @Test
        @DisplayName("below-threshold pending stays in place")
        void belowThresholdDoesNotFlush() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);

            // Three small events, well under the 50_000-byte
            // mid-cycle threshold.
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
         * Verifies that {@code checkMidCycleUpload} triggers a
         * flush of the REGULAR channel when its aggregate pending
         * size exceeds {@code MAX_BUFFER_SIZE} (50_000) — emulating
         * the production 5-second serialize tick.
         */
        @Test
        @DisplayName("above-threshold REGULAR pending triggers early flush")
        void aboveThresholdTriggersFlush() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();

            // A single event whose payload is ~55KB pushes the
            // REGULAR pending aggregate well past the 50_000-byte
            // mid-cycle threshold (and stays below the 64_000-byte
            // drop limit, so the flush completes normally).
            harness.service.commit(hugeRegularEvent(55_000));
            assertEquals(1, harness.service.pendingCount(WamChannel.REGULAR));

            harness.service.checkMidCycleUpload();

            assertTrue(harness.sendNodeCalls.get() >= 1,
                    "above-threshold REGULAR pending must trigger an early flush");
            assertEquals(0, harness.service.pendingCount(WamChannel.REGULAR),
                    "early flush must drain the REGULAR pending list");
        }

        /**
         * Verifies that {@code checkMidCycleUpload} ignores the
         * REALTIME channel even when it would otherwise be over
         * threshold — REALTIME has its own immediate-flush path on
         * commit, and the mid-cycle sweep does not re-flush it.
         */
        @Test
        @DisplayName("REALTIME channel is skipped by the mid-cycle sweep")
        void realtimeIsSkipped() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            harness.responsesQueue.add(successResponse());
            harness.service.markInitializedForTesting();
            // Pre-populate REALTIME pending by setting sequence
            // counters and committing — but we need a synthetic
            // REALTIME event whose commit() doesn't immediately
            // spawn a flush. The production immediate-flush path is
            // a virtual thread, which races against this test —
            // skip the commit and inspect the swept channels via a
            // sentinel REGULAR commit instead.
            //
            // Concretely: this test asserts the mid-cycle sweep
            // continues across the REALTIME-skip without throwing.
            harness.service.commit(samplePsIdEvent());
            assertDoesNotThrow(harness.service::checkMidCycleUpload);
            // The PsId commit is small, so no flush should have
            // fired.
            assertEquals(0, harness.sendNodeCalls.get(),
                    "small commits should not trigger mid-cycle flush");
        }
    }

    /**
     * Channel-pinning tests against the captured live event-definition
     * registry. For every event the fixture's
     * {@code wam-event-definitions.json} resolves to a Cobalt
     * {@link WamEventSpec}, the {@code channel()} reported by Cobalt
     * must match what {@code WAWebWamCodegenUtils.events} declares
     * (case-insensitive name match against {@link WamChannel}).
     *
     * <p>This catches a class of regressions where the Cobalt
     * {@code @WamEvent} annotation defaults a channel (typically
     * REGULAR) for an event WA Web actually classifies as
     * REALTIME / PRIVATE.
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
     * Decodes a minimal event-marker for the given id and asserts
     * the resulting spec's {@code channel()} matches the live
     * channel string.
     *
     * @param eventName   the live event name (used only for error
     *                    messages)
     * @param eventId     the wire event id
     * @param liveChannel the live channel string ("regular" /
     *                    "realtime" / "private")
     */
    private static void assertChannelMatches(String eventName, int eventId, String liveChannel) {
        var buffer = new byte[8];
        var encoder = com.github.auties00.cobalt.wam.binary.WamEventEncoder.of(buffer);
        encoder.writeEventMarker(eventId, 0, false);
        var decoder = com.github.auties00.cobalt.wam.binary.WamEventDecoder.of(buffer, 0, encoder.written());
        var spec = com.github.auties00.cobalt.wam.event.WamEventRegistry.decode(decoder);
        var expected = WamChannel.valueOf(liveChannel.toUpperCase(Locale.ROOT));
        assertSame(expected, spec.channel(),
                () -> "channel drift for " + eventName + " (id=" + eventId
                        + "): Cobalt=" + spec.channel() + " live=" + expected);
    }

    /**
     * Builds a representative {@link com.github.auties00.cobalt.wam.event.PsIdUpdateEvent}
     * for use in commit tests.
     *
     * @return a PsIdUpdate event with the CREATED action
     */
    private static WamEventSpec samplePsIdEvent() {
        return new PsIdUpdateEventBuilder()
                .psIdAction(PsIdAction.CREATED)
                .psIdKey(42)
                .psIdRotationFrequence(7)
                .build();
    }

    /**
     * Creates a fresh {@link TestableWamService}.
     *
     * @return a new service bound to a {@link TestWhatsAppClient} and
     *         {@link TestABPropsService}
     */
    private static TestableWamService newService() {
        var props = TestABPropsService.builder().build();
        var client = TestWhatsAppClient.create().withAbPropsService(props);
        return new TestableWamService(client, props, new DefaultWamBeaconing());
    }

    /**
     * {@link WamService} subclass with deterministic stubs for the
     * four abstract timing/scheduling methods.
     */
    private static final class TestableWamService extends WamService {
        /**
         * Fixed instant returned by {@link #now()}.
         */
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochSecond(1_747_000_000L));

        /**
         * Captured sleep requests in milliseconds.
         */
        final List<Long> sleepRequests = new ArrayList<>();

        /**
         * Captured schedule requests in submission order.
         */
        final Queue<ScheduledCall> scheduledRunnables = new ConcurrentLinkedQueue<>();

        /**
         * Constructs the testable service.
         *
         * @param client    the test client
         * @param props     the test AB props
         * @param beaconing the beaconing impl
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
            // Advance the testable clock by the sleep duration so the
            // connectivity-wait loop's now-based deadline check
            // terminates after a deterministic number of iterations.
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
     * One captured {@link WamService#scheduleRecurring} invocation.
     *
     * @param task                the task that would have been
     *                            scheduled
     * @param initialDelaySeconds the requested initial delay
     * @param periodSeconds       the requested recurring period
     */
    private record ScheduledCall(Runnable task, long initialDelaySeconds, long periodSeconds) {
    }

    /**
     * Sequence-number tests covering per-channel counter
     * advancement, the wrap from {@code MAX_SEQUENCE_NUMBER}
     * (0xFFFF) back to {@code 1}, and independence across
     * channels.
     */
    @Nested
    @DisplayName("sequence numbers")
    class SequenceNumbers {
        /**
         * Local self-PN for the in-memory store.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * Verifies that each successful flush increments the
         * channel's counter by exactly one.
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
         * Verifies that the per-channel counters advance
         * independently — a REGULAR flush must not move the
         * REALTIME or PRIVATE counters, and vice versa.
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
         * Verifies that on the flush following a counter at
         * {@code MAX_SEQUENCE_NUMBER}, the counter wraps back to
         * {@code 1}, not to {@code 0}.
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
     * REALTIME (immediate virtual-thread flush) and REGULAR
     * (accumulate-until-flush).
     *
     * <p>PRIVATE bucketing is not exercised here: PRIVATE events
     * upload through the HTTP-only
     * {@link com.github.auties00.cobalt.wam.privatestats.WamPrivateStatsUploader},
     * which doesn't surface through {@code TestWhatsAppClient.sendNode}
     * and would need an injectable uploader seam. The bucketing
     * logic itself is exercised by {@code WamPrivateStatsUploaderTest}
     * once that is in place.
     */
    @Nested
    @DisplayName("channel routing")
    class ChannelRouting {
        /**
         * Local self-PN for the in-memory store.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * Verifies that a REGULAR commit accumulates into the
         * pending list and does not trigger an immediate flush
         * (no {@code sendNode} call until explicit flush).
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
         * Verifies that a REALTIME commit spawns a virtual thread
         * that ultimately reaches {@code sendNode}. The assertion
         * waits up to five seconds on a latch the
         * {@code sendNodeHandler} counts down.
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
         * Verifies that committing several REALTIME events drains
         * them through the upload pipeline without losing any.
         *
         * <p>WhatsApp Web's {@code WAWam} commit handler pushes
         * REALTIME events onto a shared pending list and schedules a
         * single {@code setTimeout(forceRunNow("realtime"), 1)}.
         * Multiple commits within the same event-loop tick all
         * accumulate into one batched upload — so 3 commits can
         * legitimately produce 1, 2, or 3 sendNode calls depending
         * on scheduling, but every event must reach the upload
         * pipeline and the pending list must drain to zero.
         *
         * <p>Cobalt mirrors this with
         * {@code Thread.ofVirtual().start(() -> flushChannel(REALTIME))}
         * plus {@code swapPending}'s atomic drain — the first
         * virtual thread to reach {@code swapPending} takes every
         * queued event; later threads find an empty list and
         * return.
         *
         * @throws InterruptedException if the test is interrupted
         *                              waiting for the upload-pipeline
         *                              latch
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
            // Settle: give any straggler virtual threads a moment to
            // complete, then assert the pending list is fully drained.
            Thread.sleep(200);
            assertEquals(0, harness.service.pendingCount(WamChannel.REALTIME),
                    "every committed REALTIME event must drain (no events left in pending)");
            assertTrue(harness.sendNodeCalls.get() >= 1,
                    "at least one sendNode call must have been made; got "
                            + harness.sendNodeCalls.get());
        }
    }

    /**
     * Returns a synthetic REALTIME-channel {@link WamEventSpec}
     * with the given event id. The event has no fields and
     * permits multiple {@code markCommitted()} calls — the latter
     * because the production {@code commit()} path now defers to
     * the init queue when not initialised, and the lambda re-enters
     * a fresh path that re-checks markCommitted.
     *
     * @param eventId the wire event id (any value the test cares
     *                about)
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
                return com.github.auties00.cobalt.wam.binary.WamEventSizes.eventMarkerSize(eventId, weight);
            }

            @Override
            public void encode(com.github.auties00.cobalt.wam.binary.WamEventEncoder encoder, int weight) {
                encoder.writeEventMarker(eventId, weight, false);
            }
        };
    }

    /**
     * Retry/backoff tests driving the full {@link #flushChannel}
     * pipeline against a real in-memory store and a controllable
     * {@link TestWhatsAppClient#withSendNodeHandler} response
     * sequence. Captured {@link TestableWamService#sleepRequests}
     * are asserted against the expected exponential-backoff delays.
     */
    @Nested
    @DisplayName("retry / backoff")
    class RetryBackoff {
        /**
         * Local self-PN for the in-memory store used by the retry
         * tests.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * Verifies the {@code computeBackoffDelay} formula at the
         * three documented regions: clamped to the base delay when
         * {@code 2^attempt < base}, the unclamped middle band, and
         * clamped to the max delay when {@code 2^attempt > max}.
         * Each captured value must lie in
         * {@code [delay, delay + 10% jitter]}.
         */
        @Test
        @DisplayName("computeBackoffDelay clamps low/high and lies within the 10% jitter band")
        void backoffFormulaBounds() {
            for (var attempt = 0; attempt < 4; attempt++) {
                var actual = WamService.computeBackoffDelay(attempt);
                assertTrue(actual >= 1_000 && actual <= 1_100,
                        "attempt=" + attempt + " should clamp to base [1000, 1100], was " + actual);
            }
            // attempt=10 → 2^10 = 1024 ms, unclamped middle band
            var mid = WamService.computeBackoffDelay(10);
            assertTrue(mid >= 1_024 && mid <= (long) (1_024 * 1.1),
                    "attempt=10 should be in [1024, ~1126], was " + mid);
            // attempt=20 → 2^20 = 1_048_576 ms, clamped to 120_000
            var clamped = WamService.computeBackoffDelay(20);
            assertTrue(clamped >= 120_000 && clamped <= (long) (120_000 * 1.1),
                    "attempt=20 should clamp to max [120_000, 132_000], was " + clamped);
        }

        /**
         * Drives a full {@code flushChannel(REGULAR)} cycle against a
         * canned send-node sequence that returns two server-side 5xx
         * errors before a success. Verifies that:
         *
         * <ol>
         *   <li>{@code sendNode} was invoked three times (two retries
         *       plus the final success);</li>
         *   <li>two sleep delays were captured, each in the documented
         *       backoff range.</li>
         * </ol>
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
         * Drives {@code flushChannel(REGULAR)} against a permanent
         * 4xx response (e.g. {@code 400 Bad Request}). Verifies that
         * no retry is attempted — 4xx is permanent — and the buffer
         * is dropped with one drop-counter event re-committed.
         */
        @Test
        @DisplayName("4xx response does not retry; buffer is dropped")
        void permanentErrorDropsBuffer() {
            var harness = newRealisticHarness(selfPn);
            harness.responsesQueue.add(errorResponse(400));
            // The drop-counter re-commit may itself flush — provide
            // a success response in case the test's later behaviour
            // triggers another sendNode call.
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(2862, 1);
            harness.service.commit(samplePsIdEvent());
            harness.service.flushChannel(WamChannel.REGULAR);

            assertEquals(0, harness.service.sleepRequests.size(),
                    "4xx is permanent, no backoff sleep should be requested");
            // The exact number of sendNode invocations depends on
            // whether the re-committed WamClientErrors fanout flushes
            // synchronously; both 1 and 2 are valid post-conditions.
            assertTrue(harness.sendNodeCalls.get() >= 1,
                    "at least the original buffer upload must have been attempted");
        }
    }

    /**
     * Connectivity-wait tests covering
     * {@code waitIfDisconnected}, which sleeps in 1-second steps
     * up to {@code CONNECTIVITY_WAIT_TIMEOUT_MS = 30_000}.
     */
    @Nested
    @DisplayName("connectivity wait")
    class Connectivity {
        /**
         * Local self-PN for the in-memory store.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * Verifies that {@code waitIfDisconnected} returns
         * immediately (no sleep captured) when the client is
         * connected on entry.
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
         * Verifies that with the client wired as permanently
         * disconnected, {@code waitIfDisconnected} sleeps in
         * 1000ms chunks until the 30s deadline elapses, then the
         * upload still proceeds once (and lands the canned success
         * response).
         *
         * <p>Each captured sleep delay should be exactly
         * {@code 1_000} milliseconds, matching the production
         * {@code Thread.sleep(1_000)} call site. The total number of
         * sleeps is at least {@code 30} (one per second of timeout)
         * since {@link TestableWamService#sleep} advances the
         * testable clock by the requested duration.
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
     * with enough payload to cross
     * {@code MAX_BUFFER_SIZE = 50_000} (rotation) and
     * {@code MAX_UPLOAD_SIZE = 64_000} (drop).
     */
    @Nested
    @DisplayName("buffer rotation / drop")
    class BufferRotation {
        /**
         * Local self-PN for the in-memory store.
         */
        private final Jid selfPn = Jid.of("19254863482@s.whatsapp.net");

        /**
         * Commits enough small events that the aggregate buffer size
         * crosses {@code MAX_BUFFER_SIZE}; verifies that the flush
         * splits into at least two server IQ uploads.
         */
        @Test
        @DisplayName("flush across MAX_BUFFER_SIZE splits into multiple sendNode calls")
        void multipleBuffersAcrossMaxBufferSize() {
            var harness = newRealisticHarness(selfPn);
            // Always succeed.
            harness.responsesQueue.add(successResponse());
            harness.alwaysSucceed = true;

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(1144, 1);

            // Commit ~8000 small WamClientErrors events, each at roughly
            // 7 bytes (event marker + one int8 field + commit-time
            // global). Total ~56_000 bytes, comfortably past the 50_000
            // rotation threshold but below 64_000 drop limit per buffer.
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
         * Verifies that {@link WamService#pendingCount(WamChannel)}
         * reports zero after a successful flush — covers the drain
         * path of {@code swapPending} regardless of the number of
         * physical buffers sent.
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
         * Verifies that an event whose encoded size exceeds
         * {@code MAX_UPLOAD_SIZE} (64 000 bytes) is dropped without
         * being uploaded, and a {@link WamClientErrorsEventBuilder}
         * drop-counter event is re-committed into the REGULAR
         * pending list for a subsequent flush.
         *
         * <p>The flush path is: aggregate > 50KB → buildAndSend; the
         * single oversized event exceeds 64KB inside buildAndSend, so
         * the buffer is dropped before any {@code sendNode} call and
         * the WamClientErrors event is queued for the next cycle.
         */
        @Test
        @DisplayName("buffer >64KB is dropped, drop-counter event re-committed")
        void oversizedBufferDropsAndRecommitsCounter() {
            var harness = newRealisticHarness(selfPn);
            harness.alwaysSucceed = true;
            // Pre-queue a success response in case the drop-counter
            // flush hits it on a later flush.
            harness.responsesQueue.add(successResponse());

            harness.service.markInitializedForTesting();
            harness.service.setSamplingOverride(1144, 1);

            // Synthetic event with a >64KB string payload. The
            // event marker + str32-length-prefix + payload itself
            // overshoots MAX_UPLOAD_SIZE comfortably.
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
     * Returns a synthetic REGULAR-channel {@link WamEventSpec}
     * with a configurable {@code releaseWeight}. Useful for
     * sampling-override behaviour tests where the default weight
     * of {@code 1} (always keep) would mask the effect.
     *
     * @param eventId       the wire event id
     * @param releaseWeight the sampling weight reported by
     *                      {@code releaseWeight()}
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
            public com.github.auties00.cobalt.wam.model.WamChannel channel() {
                return com.github.auties00.cobalt.wam.model.WamChannel.REGULAR;
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
                return com.github.auties00.cobalt.wam.binary.WamEventSizes.eventMarkerSize(eventId, weight);
            }

            @Override
            public void encode(com.github.auties00.cobalt.wam.binary.WamEventEncoder encoder, int weight) {
                encoder.writeEventMarker(eventId, weight, false);
            }
        };
    }

    /**
     * Returns a synthetic REGULAR-channel {@link WamEventSpec}
     * whose encoded payload is a single string field of the given
     * approximate byte length (the actual encoded length is the
     * string length plus event-marker + str32 length-prefix
     * overhead, ~10 bytes).
     *
     * @param targetSize the desired event payload size (in bytes)
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
            public com.github.auties00.cobalt.wam.model.WamChannel channel() {
                return com.github.auties00.cobalt.wam.model.WamChannel.REGULAR;
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
                return com.github.auties00.cobalt.wam.binary.WamEventSizes.eventMarkerSize(9000, weight)
                        + com.github.auties00.cobalt.wam.binary.WamEventSizes.stringFieldSize(1, payload);
            }

            @Override
            public void encode(com.github.auties00.cobalt.wam.binary.WamEventEncoder encoder, int weight) {
                encoder.writeEventMarker(9000, weight, true);
                encoder.writeStringField(1, payload, false);
            }
        };
    }

    /**
     * Realistic-store harness used by the retry / rotation tests.
     */
    private static final class RealisticHarness {
        /**
         * The test client.
         */
        final TestWhatsAppClient client;

        /**
         * The testable WAM service.
         */
        final TestableWamService service;

        /**
         * FIFO queue of canned IQ responses, drained on each
         * {@code sendNode} invocation.
         */
        final Deque<Node> responsesQueue;

        /**
         * Counter of {@code sendNode} invocations observed by the
         * wired {@link TestWhatsAppClient#withSendNodeHandler}.
         */
        final AtomicInteger sendNodeCalls;

        /**
         * When {@code true}, {@code sendNode} returns a fresh success
         * response once {@link #responsesQueue} is exhausted. When
         * {@code false} (the default), the handler throws.
         */
        boolean alwaysSucceed;

        /**
         * Constructs the harness from its fully-wired collaborators.
         *
         * @param client         the test client
         * @param service        the testable WAM service
         * @param responsesQueue the canned response queue
         * @param sendNodeCalls  the invocation counter
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
     * Builds a {@link TestableWamService} bound to a real in-memory
     * {@link com.github.auties00.cobalt.store.WhatsAppStore} (so the
     * flush path's {@code putWamSequenceNumber} and persisted-buffer
     * writes have somewhere to land) and a
     * {@link TestWhatsAppClient} whose {@code sendNode} returns
     * responses from a FIFO queue (and a sticky-success fallback
     * once the queue is empty).
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
     * Builds a successful IQ response that {@code WamService}
     * recognises ({@code type="result"} on the root node).
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
     * Builds an error IQ response with the given HTTP-style code
     * embedded in an {@code <error code="..."/>} child. WamService
     * dispatches to retry for {@code >= 500} and to drop for
     * everything else.
     *
     * @param code the HTTP-style error code (e.g. {@code 500},
     *             {@code 400})
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
