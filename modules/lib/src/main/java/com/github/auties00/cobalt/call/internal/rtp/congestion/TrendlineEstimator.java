package com.github.auties00.cobalt.call.internal.rtp.congestion;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Estimates delay-based congestion by fitting a trendline over smoothed arrival-delay samples and
 * applying an adaptive overuse detector.
 *
 * <p>This class combines the slope of a smoothed arrival-time-delay window with an adaptive-threshold
 * detector and a duration filter to produce one {@link BandwidthState} classification per packet
 * group. Each group reports its send-time spacing and arrival-time spacing relative to the previous
 * group; their difference is the one-way-delay growth, which gives the queue's instantaneous fill
 * direction. The accumulated delay is exponentially smoothed and pushed into a sliding window of
 * {@link #WINDOW_SIZE} samples. A least-squares line is fit across the window, and its slope is
 * scaled by the window size and {@link #THRESHOLD_GAIN} into the modified trend that the detector
 * compares against the adaptive threshold. When the modified trend exceeds the threshold for at
 * least {@link #OVERUSING_TIME_MS}, the link is classified {@link BandwidthState#OVERUSE}; when it
 * drops below the negated threshold, it is classified {@link BandwidthState#UNDERUSE}; otherwise
 * {@link BandwidthState#NORMAL}. The threshold itself adapts on each call, rising with gain
 * {@link #K_UP} while overusing to suppress repeat triggers and falling with gain {@link #K_DOWN}
 * otherwise to recover sensitivity, bounded to {@code [MIN_THRESHOLD, MAX_THRESHOLD]}.
 *
 * <p>The class is not safe for concurrent updates; Cobalt drives one estimator per remote endpoint
 * from the call's transport thread.
 *
 * @implNote This implementation follows the algorithm described in RFC draft-ietf-rmcat-gcc section
 * 5. The numeric constants are taken from upstream WebRTC's
 * {@code modules/congestion_controller/goog_cc/trendline_estimator.cc} and
 * {@code modules/remote_bitrate_estimator/overuse_detector.cc}; they are calibrated against real
 * cellular and Wi-Fi traces and are not intended to be retuned.
 */
public final class TrendlineEstimator {
    /**
     * Holds the number of samples the least-squares regression operates over.
     *
     * <p>The sliding window retains this many (smoothed-time, smoothed-delay) sample points, evicting
     * the oldest once full.
     */
    static final int WINDOW_SIZE = 20;

    /**
     * Holds the exponential-smoothing coefficient applied to the running accumulated-delay sum.
     *
     * <p>Each update blends this fraction of the previous smoothed delay with the complementary
     * fraction of the freshly accumulated delay.
     */
    static final double SMOOTHING_COEFFICIENT = 0.9;

    /**
     * Holds the multiplier on {@code slope * windowSize} that produces the modified trend.
     *
     * <p>The modified trend is the quantity the detector compares against the adaptive threshold.
     */
    static final double THRESHOLD_GAIN = 4.0;

    /**
     * Holds the initial value of the adaptive threshold.
     */
    static final double INITIAL_THRESHOLD = 12.5;

    /**
     * Holds the lower bound on the adaptive threshold.
     */
    static final double MIN_THRESHOLD = 6.0;

    /**
     * Holds the upper bound on the adaptive threshold.
     */
    static final double MAX_THRESHOLD = 600.0;

    /**
     * Holds the up-adapt gain applied to the threshold while the modified trend magnitude exceeds it.
     *
     * <p>Raising the threshold while overusing suppresses repeated OVERUSE triggers from the same
     * sustained excursion.
     */
    static final double K_UP = 0.0087;

    /**
     * Holds the down-adapt gain applied to the threshold while the modified trend magnitude is at or
     * below it.
     *
     * <p>Lowering the threshold when the link is not overusing recovers detector sensitivity.
     */
    static final double K_DOWN = 0.039;

    /**
     * Holds the minimum continuous duration the modified trend must exceed the threshold before
     * {@link BandwidthState#OVERUSE} fires.
     *
     * <p>This acts as a low-pass filter so that a single-spike excursion does not trigger an OVERUSE
     * classification.
     */
    static final double OVERUSING_TIME_MS = 10.0;

    /**
     * Holds the cap on the inter-update time delta fed into the threshold adapt step.
     *
     * <p>Capping the time delta guards against a pathologically large gap between updates making the
     * threshold jump.
     */
    static final double MAX_TIME_DELTA_MS = 100.0;

    /**
     * Holds the sliding window of samples used by the least-squares regression.
     *
     * <p>The deque is bounded to {@link #WINDOW_SIZE} entries; once full, the oldest sample is
     * evicted from the front before a new one is appended to the back.
     */
    private final Deque<Sample> window = new ArrayDeque<>(WINDOW_SIZE);

    /**
     * Holds the running exponentially-smoothed sum of per-group delay deltas.
     */
    private double smoothedDelayMs = 0.0;

    /**
     * Holds the running unsmoothed sum of per-group delay deltas.
     *
     * <p>This is the input to the exponential moving average that produces {@link #smoothedDelayMs}.
     */
    private double accumulatedDelayMs = 0.0;

    /**
     * Holds the reference timestamp of the first packet group seen.
     *
     * <p>Sample timestamps are stored relative to this value so that the regression operates on
     * small-magnitude doubles. A value of {@code -1} marks that no group has been seen yet.
     */
    private long firstArrivalMs = -1;

    /**
     * Holds the most recently computed slope.
     */
    private double trend = 0.0;

    /**
     * Holds the current adaptive threshold.
     */
    private double threshold = INITIAL_THRESHOLD;

    /**
     * Holds the cumulative time the detector has spent in OVERUSE-candidate territory.
     *
     * <p>The accumulator is reset whenever the sign of the modified trend flips and is compared
     * against {@link #OVERUSING_TIME_MS} to gate the OVERUSE classification.
     */
    private double overusingTimeMs = 0.0;

    /**
     * Holds the count of consecutive updates in which the modified trend exceeded the threshold.
     *
     * <p>Requiring more than one consecutive excursion enforces monotonic delay growth rather than a
     * single isolated overshoot.
     */
    private int overuseCounter = 0;

    /**
     * Holds the most recent classification returned by {@link #update} and exposed via
     * {@link #state()}.
     */
    private BandwidthState state = BandwidthState.NORMAL;

    /**
     * Holds the timestamp of the last {@link #update} call.
     *
     * <p>The difference between successive timestamps supplies the time delta used by the threshold
     * adapt step. A value of {@code -1} marks that no update has occurred yet.
     */
    private long lastUpdateMs = -1;

    /**
     * Holds one sample point fed to the least-squares regression.
     *
     * @param tMs   the smoothed time in milliseconds, relative to the first packet group
     * @param delay the smoothed accumulated delay in milliseconds
     */
    private record Sample(double tMs, double delay) {
    }

    /**
     * Constructs a fresh estimator with the canonical WebRTC tuning.
     */
    public TrendlineEstimator() {
    }

    /**
     * Feeds one packet-group inter-arrival measurement, updates the trendline, adapts the threshold,
     * and returns the new classification.
     *
     * <p>The measurement is taken over a packet group in the WebRTC sense: packets sent within a 5 ms
     * send-time burst are coalesced, so this method receives one tuple of send delta, arrival delta,
     * and payload per group rather than per packet. The grouping is performed by the caller in the
     * RTP receiver code, not by this estimator. The method accumulates the one-way-delay growth,
     * updates the exponentially-smoothed delay, appends a sample to the window, refits the slope once
     * the window is full, then classifies and adapts the threshold before returning the resulting
     * {@link BandwidthState}.
     *
     * @param sendDeltaMs    the send-time difference between this group and the previous one
     * @param arrivalDeltaMs the arrival-time difference between this group and the previous one
     * @param payloadBytes   the total payload bytes in this group; carried for parity with the
     *                       WebRTC API but unused by the slope-only estimator
     * @param nowMs          the monotonic timestamp at which this update is processed
     * @return the classification for this packet group
     */
    @SuppressWarnings("unused")
    public BandwidthState update(double sendDeltaMs, double arrivalDeltaMs, int payloadBytes, long nowMs) {
        var oneWayDelayDelta = arrivalDeltaMs - sendDeltaMs;
        accumulatedDelayMs += oneWayDelayDelta;
        smoothedDelayMs = SMOOTHING_COEFFICIENT * smoothedDelayMs
                + (1.0 - SMOOTHING_COEFFICIENT) * accumulatedDelayMs;

        if (firstArrivalMs < 0) {
            firstArrivalMs = nowMs;
        }
        var relativeTimeMs = (double) (nowMs - firstArrivalMs);
        if (window.size() == WINDOW_SIZE) {
            window.removeFirst();
        }
        window.addLast(new Sample(relativeTimeMs, smoothedDelayMs));

        if (window.size() == WINDOW_SIZE) {
            trend = leastSquaresSlope(window);
        }

        var modifiedTrend = trend * Math.min(window.size(), WINDOW_SIZE) * THRESHOLD_GAIN;
        var dtMs = lastUpdateMs < 0 ? 0.0 : (double) (nowMs - lastUpdateMs);
        lastUpdateMs = nowMs;
        classify(modifiedTrend, dtMs);
        adaptThreshold(modifiedTrend, dtMs);
        return state;
    }

    /**
     * Classifies the link as overuse, underuse, or normal and updates {@link #state}.
     *
     * <p>A modified trend above {@link #threshold} accrues {@link #overusingTimeMs} and increments
     * {@link #overuseCounter}; an OVERUSE is declared only once the accrued time reaches
     * {@link #OVERUSING_TIME_MS} across more than one consecutive excursion, which is the duration
     * filter that prevents single-spike noise from triggering it. A modified trend below the negated
     * threshold yields UNDERUSE, and anything in between yields NORMAL; both reset the overuse
     * accumulators.
     *
     * @param modifiedTrend the slope scaled by the window size and {@link #THRESHOLD_GAIN}
     * @param dtMs          the time since the previous update
     */
    private void classify(double modifiedTrend, double dtMs) {
        if (modifiedTrend > threshold) {
            overusingTimeMs += Math.min(dtMs, MAX_TIME_DELTA_MS);
            overuseCounter++;
            if (overusingTimeMs >= OVERUSING_TIME_MS && overuseCounter > 1) {
                if (modifiedTrend >= trend * WINDOW_SIZE * THRESHOLD_GAIN) {
                    overusingTimeMs = 0.0;
                    overuseCounter = 0;
                    state = BandwidthState.OVERUSE;
                }
            }
        } else if (modifiedTrend < -threshold) {
            overusingTimeMs = 0.0;
            overuseCounter = 0;
            state = BandwidthState.UNDERUSE;
        } else {
            overusingTimeMs = 0.0;
            overuseCounter = 0;
            state = BandwidthState.NORMAL;
        }
    }

    /**
     * Adapts {@link #threshold} toward the magnitude of the modified trend.
     *
     * <p>The threshold moves with gain {@link #K_UP} when the trend magnitude is at or above it,
     * suppressing repeat triggers, and with gain {@link #K_DOWN} when it is below, recovering
     * sensitivity. The step is scaled by the time delta capped at {@link #MAX_TIME_DELTA_MS} and the
     * result is clamped to {@code [MIN_THRESHOLD, MAX_THRESHOLD]}. When the trend magnitude overshoots
     * the threshold by more than 15, the adapt step is skipped entirely so that a large transient does
     * not drag the threshold upward.
     *
     * @param modifiedTrend the current modified trend
     * @param dtMs          the time since the previous update, capped at {@link #MAX_TIME_DELTA_MS}
     * @implNote This implementation uses the upstream WebRTC guard constant of 15: when the modified
     * trend magnitude exceeds the threshold plus 15, the threshold is left unchanged.
     */
    private void adaptThreshold(double modifiedTrend, double dtMs) {
        var absTrend = Math.abs(modifiedTrend);
        if (absTrend > threshold + 15.0) {
            return;
        }
        var k = absTrend < threshold ? K_DOWN : K_UP;
        var clampedDt = Math.min(dtMs, MAX_TIME_DELTA_MS);
        threshold += k * (absTrend - threshold) * clampedDt;
        if (threshold < MIN_THRESHOLD) {
            threshold = MIN_THRESHOLD;
        } else if (threshold > MAX_THRESHOLD) {
            threshold = MAX_THRESHOLD;
        }
    }

    /**
     * Returns the most recent classification.
     *
     * <p>The returned value equals the value returned by the last {@link #update} call, or
     * {@link BandwidthState#NORMAL} before any update.
     *
     * @return the current state
     */
    public BandwidthState state() {
        return state;
    }

    /**
     * Returns the latest computed slope in delay milliseconds per smoothed-time millisecond.
     *
     * <p>The slope is {@code 0.0} until the window has filled to {@link #WINDOW_SIZE} samples.
     *
     * @return the trend slope
     */
    public double trendSlope() {
        return trend;
    }

    /**
     * Returns the current adaptive threshold.
     *
     * <p>The value is primarily useful for diagnostics, since the threshold is maintained internally
     * by the adapt step.
     *
     * @return the current threshold value
     */
    public double threshold() {
        return threshold;
    }

    /**
     * Computes the slope of the least-squares-fit line through a set of samples.
     *
     * <p>The method returns {@code 0.0} when the sample set is empty or when the variance in time is
     * zero, that is when every sample shares the same instant; the latter does not occur in practice
     * because successive groups carry distinct monotonic timestamps.
     *
     * @param samples the sample window
     * @return the slope, or {@code 0.0} when it is undefined
     */
    private static double leastSquaresSlope(Iterable<Sample> samples) {
        var sumT = 0.0;
        var sumD = 0.0;
        var n = 0;
        for (var s : samples) {
            sumT += s.tMs;
            sumD += s.delay;
            n++;
        }
        if (n == 0) {
            return 0.0;
        }
        var avgT = sumT / n;
        var avgD = sumD / n;
        var num = 0.0;
        var den = 0.0;
        for (var s : samples) {
            var dt = s.tMs - avgT;
            num += dt * (s.delay - avgD);
            den += dt * dt;
        }
        return den == 0.0 ? 0.0 : num / den;
    }
}
