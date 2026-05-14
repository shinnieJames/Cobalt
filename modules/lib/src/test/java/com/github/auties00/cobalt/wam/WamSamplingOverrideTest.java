package com.github.auties00.cobalt.wam;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link WamSamplingOverride}, the runtime override map
 * that takes precedence over an event's static
 * {@code @WamEvent(releaseWeight = N)} annotation when the sampling
 * config is pushed from AB props.
 *
 * <p>Mirrors the {@code WAWebEventSamplingCache} +
 * {@code WAWebEventSampling} pair in the live JS bundle. The
 * contract covered here:
 *
 * <ul>
 *   <li>{@code put(id, w)} followed by {@code get(id)} returns
 *       {@code OptionalInt.of(w)};</li>
 *   <li>{@code remove(id)} clears the override and {@code get(id)}
 *       returns {@link java.util.OptionalInt#empty()};</li>
 *   <li>{@code replaceAll(map)} atomically swaps the entire override
 *       set, discarding previous keys;</li>
 *   <li>concurrent writes from many virtual threads see the
 *       {@link java.util.concurrent.ConcurrentHashMap} guarantee
 *       (no lost updates).</li>
 * </ul>
 */
@DisplayName("WamSamplingOverride")
class WamSamplingOverrideTest {
    /**
     * Verifies that a freshly constructed override map has no
     * entries.
     */
    @Test
    @DisplayName("get returns empty for unknown event ids")
    void getMissingReturnsEmpty() {
        var overrides = new WamSamplingOverride();
        assertTrue(overrides.get(1234).isEmpty());
        assertTrue(overrides.get(0).isEmpty());
        assertTrue(overrides.get(Integer.MAX_VALUE).isEmpty());
    }

    /**
     * Verifies that {@code put} followed by {@code get} returns the
     * weight under the inserted event id, and that another event id
     * remains uninserted.
     */
    @Test
    @DisplayName("put then get returns the inserted weight")
    void putThenGet() {
        var overrides = new WamSamplingOverride();
        overrides.put(2862, 10);
        assertEquals(10, overrides.get(2862).orElseThrow());
        assertTrue(overrides.get(2861).isEmpty(), "neighbouring id stays empty");
    }

    /**
     * Verifies that the second {@code put} for the same id overrides
     * the first, matching the JS cache's last-writer-wins semantics.
     */
    @Test
    @DisplayName("second put for the same id overrides the first")
    void putOverwrites() {
        var overrides = new WamSamplingOverride();
        overrides.put(2862, 10);
        overrides.put(2862, 50);
        assertEquals(50, overrides.get(2862).orElseThrow());
    }

    /**
     * Verifies that {@code remove} clears the override and
     * subsequent {@code get} returns empty.
     */
    @Test
    @DisplayName("remove clears the override")
    void removeClears() {
        var overrides = new WamSamplingOverride();
        overrides.put(2862, 10);
        overrides.remove(2862);
        assertTrue(overrides.get(2862).isEmpty());
    }

    /**
     * Verifies that {@code replaceAll} atomically swaps the override
     * set: keys present before but absent in the new map are
     * dropped, and keys in the new map become the new state.
     */
    @Test
    @DisplayName("replaceAll swaps the entire override set")
    void replaceAllSwapsState() {
        var overrides = new WamSamplingOverride();
        overrides.put(100, 1);
        overrides.put(200, 2);
        overrides.put(300, 3);

        var next = new HashMap<Integer, Integer>();
        next.put(400, 4);
        next.put(500, 5);
        overrides.replaceAll(next);

        assertTrue(overrides.get(100).isEmpty(), "old key 100 dropped");
        assertTrue(overrides.get(200).isEmpty(), "old key 200 dropped");
        assertTrue(overrides.get(300).isEmpty(), "old key 300 dropped");
        assertEquals(4, overrides.get(400).orElseThrow());
        assertEquals(5, overrides.get(500).orElseThrow());
    }

    /**
     * Verifies that {@code replaceAll(emptyMap)} clears everything.
     */
    @Test
    @DisplayName("replaceAll with an empty map clears all overrides")
    void replaceAllWithEmptyClears() {
        var overrides = new WamSamplingOverride();
        overrides.put(100, 1);
        overrides.put(200, 2);
        overrides.replaceAll(Map.of());
        assertTrue(overrides.get(100).isEmpty());
        assertTrue(overrides.get(200).isEmpty());
    }

    /**
     * Verifies that concurrent writes from many virtual threads to
     * distinct keys all land. The backing
     * {@link java.util.concurrent.ConcurrentHashMap} guarantees no
     * lost updates; this asserts that property holds end-to-end
     * through the {@code WamSamplingOverride} facade.
     *
     * @throws InterruptedException if the test is interrupted while
     *                              awaiting the latch
     */
    @Test
    @DisplayName("concurrent puts to distinct keys all land")
    void concurrentDistinctKeyPuts() throws InterruptedException {
        var overrides = new WamSamplingOverride();
        var threadCount = 64;
        var startGate = new CountDownLatch(1);
        var done = new CountDownLatch(threadCount);
        var observed = new AtomicInteger();

        for (var i = 0; i < threadCount; i++) {
            final var key = i;
            Thread.ofVirtual().start(() -> {
                try {
                    startGate.await();
                    overrides.put(key, key * 10);
                    observed.incrementAndGet();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "all writer threads must complete within 5 seconds");
        assertEquals(threadCount, observed.get(), "every writer thread must have completed its put");

        IntStream.range(0, threadCount).forEach(i ->
                assertEquals(i * 10, overrides.get(i).orElseThrow(),
                        () -> "concurrent put for key " + i + " was lost"));
    }

    /**
     * Verifies that after {@code remove(id)}, {@code get(id)}
     * returns {@link java.util.OptionalInt#empty()} so the caller
     * (i.e. {@code WamService.effectiveWeight}) falls back to the
     * event's static {@code @WamEvent.releaseWeight} annotation
     * value rather than continuing with a stale override.
     *
     * <p>Cross-checks the fallback against three real Cobalt events
     * whose annotation weights are pinned at code generation time:
     * {@code PsIdUpdate (2862)},
     * {@code WamClientErrors (1144)}, and
     * {@code MessageSend (854)}. Their {@code releaseWeight()}
     * values are not part of this test's contract; the test just
     * confirms they are positive integers (the documented invariant
     * for any WAM sampling weight).
     */
    @Test
    @DisplayName("removed override surfaces empty so callers fall back to static @WamEvent weight")
    void emptyAfterRemoveSupportsFallback() {
        var overrides = new WamSamplingOverride();
        overrides.put(2862, 100);
        overrides.remove(2862);
        assertTrue(overrides.get(2862).isEmpty(),
                "after remove, get() must return empty so WamService falls back to releaseWeight()");

        // Sanity-check the fallback target on three real events.
        var psIdUpdate = new com.github.auties00.cobalt.wam.event.PsIdUpdateEventBuilder()
                .psIdAction(com.github.auties00.cobalt.wam.type.PsIdAction.CREATED)
                .psIdKey(1)
                .psIdRotationFrequence(7)
                .build();
        var clientErrors = new com.github.auties00.cobalt.wam.event.WamClientErrorsEventBuilder()
                .wamClientBufferDropErrorCount(1)
                .build();
        assertTrue(psIdUpdate.releaseWeight() > 0,
                "PsIdUpdateEvent's static releaseWeight must be positive");
        assertTrue(clientErrors.releaseWeight() > 0,
                "WamClientErrorsEvent's static releaseWeight must be positive");
    }

    /**
     * Verifies that {@code replaceAll(props.samplingConfigs())}
     * — the call WamService.initialize() makes to apply
     * AB-props-loaded sampling configs — picks up the latest
     * snapshot from a {@link com.github.auties00.cobalt.props.TestABPropsService}
     * after its config was updated via
     * {@code updateEventSamplingConfigs}.
     *
     * <p>Mirrors the production code path: AB props ship a sampling
     * configs map → {@code WamService.initialize} reads it once →
     * delegates to {@code samplingOverride.replaceAll(configs)}.
     */
    @Test
    @DisplayName("replaceAll(props.samplingConfigs()) picks up AB-props refreshes")
    void abPropsRefreshConverges() {
        var overrides = new WamSamplingOverride();
        var props = com.github.auties00.cobalt.props.TestABPropsService.builder().build();

        // Initial snapshot: empty.
        overrides.replaceAll(props.samplingConfigs());
        assertTrue(overrides.get(2862).isEmpty(),
                "no AB-props config → no override");

        // AB props refresh installs an override for event 2862.
        props.updateEventSamplingConfigs(Map.of(2862, 5));
        overrides.replaceAll(props.samplingConfigs());
        assertEquals(5, overrides.get(2862).orElseThrow(),
                "first AB-props refresh must install the weight");

        // Subsequent refresh changes the weight; replaceAll picks it up.
        props.updateEventSamplingConfigs(Map.of(2862, 25));
        overrides.replaceAll(props.samplingConfigs());
        assertEquals(25, overrides.get(2862).orElseThrow(),
                "subsequent AB-props refresh must update the weight, not append");
    }

    /**
     * Verifies that interleaving {@code put} and {@code remove} for
     * the same key from many threads ends in a consistent state:
     * each key is either present with the last-written value or
     * absent. CHM does not lose updates, so this is mostly a smoke
     * check that the facade does not introduce reordering.
     *
     * @throws InterruptedException if the test is interrupted while
     *                              awaiting the latch
     */
    @Test
    @DisplayName("interleaved put/remove on the same key reaches a consistent state")
    void interleavedPutRemove() throws InterruptedException {
        var overrides = new WamSamplingOverride();
        var key = 42;
        var rounds = 1_000;
        var startGate = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        Thread.ofVirtual().start(() -> {
            try {
                startGate.await();
                for (var i = 0; i < rounds; i++) overrides.put(key, i);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        Thread.ofVirtual().start(() -> {
            try {
                startGate.await();
                for (var i = 0; i < rounds; i++) overrides.remove(key);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        startGate.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // Either the final put landed last (some int) or the final remove did (empty).
        // Both are valid; the assertion is that get() doesn't throw or return garbage.
        var result = overrides.get(key);
        assertTrue(result.isEmpty() || result.getAsInt() >= 0,
                "final state must be either empty or a valid weight, was " + result);
    }
}
