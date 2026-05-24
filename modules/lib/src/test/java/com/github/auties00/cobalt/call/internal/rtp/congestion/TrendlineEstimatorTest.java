package com.github.auties00.cobalt.call.internal.rtp.congestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-stream tests for {@link TrendlineEstimator}. Drives the
 * estimator with three traffic shapes — constant capacity, dropping
 * capacity, recovering capacity — and asserts the classification
 * matches the expectation. The values are tuned to be far from the
 * decision boundary so the test isn't sensitive to constant tweaks.
 */
public class TrendlineEstimatorTest {

    /**
     * Number of packet groups fed in each scenario — enough to fill
     * the regression window several times over.
     */
    private static final int GROUP_COUNT = 200;

    /**
     * Send-side spacing between consecutive packet groups (typical
     * 5 ms burst grouping).
     */
    private static final double SEND_DELTA_MS = 5.0;

    /**
     * Group payload — informational, the estimator only uses
     * timing.
     */
    private static final int GROUP_PAYLOAD_BYTES = 1200;

    /**
     * A constant-capacity link should not trigger overuse: arrival
     * deltas equal send deltas, so accumulated delay stays at zero
     * and the trend is flat.
     */
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

    /**
     * Ramping queue delay (each group arrives slightly later than
     * the previous) must produce at least one OVERUSE classification
     * within the test horizon.
     */
    @Test
    public void risingDelayTriggersOveruse() {
        var est = new TrendlineEstimator();
        long now = 0;
        var sawOveruse = false;
        for (var i = 0; i < GROUP_COUNT; i++) {
            now += (long) SEND_DELTA_MS;
            // Arrival delta steadily above send delta — queue is
            // accumulating delay.
            var arrival = SEND_DELTA_MS + 2.0;
            var s = est.update(SEND_DELTA_MS, arrival, GROUP_PAYLOAD_BYTES, now);
            if (s == BandwidthState.OVERUSE) {
                sawOveruse = true;
                break;
            }
        }
        assertTrue(sawOveruse, "expected OVERUSE for a steadily-rising delay; final slope " + est.trendSlope());
    }

    /**
     * Falling queue delay (each group arrives slightly earlier than
     * the previous) must produce at least one UNDERUSE
     * classification.
     */
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

    /**
     * The adaptive threshold should track the magnitude of
     * sustained-noise modified-trends — start at the default
     * (12.5), then drift down toward the lower bound when the
     * trend stays small.
     */
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
