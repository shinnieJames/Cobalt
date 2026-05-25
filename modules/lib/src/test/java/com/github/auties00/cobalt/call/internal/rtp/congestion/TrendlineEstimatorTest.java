package com.github.auties00.cobalt.call.internal.rtp.congestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-stream tests for {@link TrendlineEstimator} covering the three
 * {@link BandwidthState} classifications ({@link BandwidthState#NORMAL},
 * {@link BandwidthState#OVERUSE}, {@link BandwidthState#UNDERUSE}) plus adaptive-threshold
 * drift. Each scenario feeds a hand-built stream of send/arrival delta pairs whose values sit
 * well clear of the decision boundary, so the assertions stay stable against tuning of the
 * estimator's internal constants.
 */
public class TrendlineEstimatorTest {

    // Group count fills the regression window several times over.
    private static final int GROUP_COUNT = 200;

    // Typical 5 ms burst grouping for send-side spacing between groups.
    private static final double SEND_DELTA_MS = 5.0;

    // The estimator only uses timing, so the payload size is arbitrary.
    private static final int GROUP_PAYLOAD_BYTES = 1200;

    @Test
    public void constantCapacityStaysNormal() {
        var est = new TrendlineEstimator();
        long now = 0;
        for (var i = 0; i < GROUP_COUNT; i++) {
            now += (long) SEND_DELTA_MS;
            est.update(SEND_DELTA_MS, SEND_DELTA_MS, GROUP_PAYLOAD_BYTES, now);
        }
        assertEquals(BandwidthState.NORMAL, est.state(),
                "trend should stay flat with matched send/arrival deltas, was " + est.trendSlope());
    }

    @Test
    public void risingDelayTriggersOveruse() {
        var est = new TrendlineEstimator();
        long now = 0;
        var sawOveruse = false;
        for (var i = 0; i < GROUP_COUNT; i++) {
            now += (long) SEND_DELTA_MS;
            // Arrival delta steadily above send delta: the queue accumulates delay.
            var arrival = SEND_DELTA_MS + 2.0;
            var s = est.update(SEND_DELTA_MS, arrival, GROUP_PAYLOAD_BYTES, now);
            if (s == BandwidthState.OVERUSE) {
                sawOveruse = true;
                break;
            }
        }
        assertTrue(sawOveruse, "expected OVERUSE for a steadily-rising delay; final slope " + est.trendSlope());
    }

    @Test
    public void fallingDelayTriggersUnderuse() {
        var est = new TrendlineEstimator();
        long now = 0;
        // Prime with a flat phase so the smoothed delay starts non-zero.
        for (var i = 0; i < 30; i++) {
            now += (long) SEND_DELTA_MS;
            est.update(SEND_DELTA_MS, SEND_DELTA_MS + 5.0, GROUP_PAYLOAD_BYTES, now);
        }
        var sawUnderuse = false;
        for (var i = 0; i < GROUP_COUNT; i++) {
            now += (long) SEND_DELTA_MS;
            var arrival = SEND_DELTA_MS - 2.0;
            var s = est.update(SEND_DELTA_MS, arrival, GROUP_PAYLOAD_BYTES, now);
            if (s == BandwidthState.UNDERUSE) {
                sawUnderuse = true;
                break;
            }
        }
        assertTrue(sawUnderuse, "expected UNDERUSE for a steadily-shrinking delay; final slope " + est.trendSlope());
    }

    @Test
    public void thresholdAdaptsDownInQuietConditions() {
        var est = new TrendlineEstimator();
        long now = 0;
        var initial = est.threshold();
        for (var i = 0; i < GROUP_COUNT * 5; i++) {
            now += (long) SEND_DELTA_MS;
            est.update(SEND_DELTA_MS, SEND_DELTA_MS, GROUP_PAYLOAD_BYTES, now);
        }
        assertTrue(est.threshold() < initial,
                "threshold should adapt down when trend stays at zero; was "
                        + initial + " now " + est.threshold());
        assertTrue(est.threshold() >= TrendlineEstimator.MIN_THRESHOLD,
                "threshold must respect lower bound");
    }
}
