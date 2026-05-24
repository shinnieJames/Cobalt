package com.github.auties00.cobalt.call.internal.rtp.congestion;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Java port of WebRTC's delay-based congestion estimator —
 * combines the {@code TrendlineEstimator} (slope of a smoothed
 * arrival-time-delay window) with the {@code OveruseDetector}
 * (adaptive threshold + duration filter) into one
 * {@link BandwidthState} classifier.
 *
 * <p>Algorithm (RFC draft-ietf-rmcat-gcc, §5):
 *
 * <ol>
 *   <li>Each packet group reports {@code sendDeltaMs}
 *       (group-to-group send-time spacing) and
 *       {@code arrivalDeltaMs} (group-to-group arrival-time
 *       spacing). Their difference is one-way-delay growth — the
 *       queue's instantaneous fill direction.</li>
 *   <li>The accumulated delay is exponentially smoothed
 *       ({@code α = 0.9}) and pushed into a sliding window of
 *       {@link #WINDOW_SIZE} (smoothed-time, smoothed-delay)
 *       samples.</li>
 *   <li>A least-squares line is fit across the window; its slope is
 *       the trend. Multiplied by the window size and a gain factor
 *       ({@link #THRESHOLD_GAIN}), it gives the
 *       {@code modifiedTrend} the detector compares against.</li>
 *   <li>If {@code |modifiedTrend|} exceeds the adaptive threshold
 *       {@code γ} for at least {@link #OVERUSING_TIME_MS}, the link
 *       is in {@link BandwidthState#OVERUSE}. Below {@code -γ} →
 *       {@link BandwidthState#UNDERUSE}. Otherwise →
 *       {@link BandwidthState#NORMAL}.</li>
 *   <li>{@code γ} adapts each call: rises (gain
 *       {@link #K_UP}) while overusing to suppress repeat triggers,
 *       falls (gain {@link #K_DOWN}) otherwise to recover
 *       sensitivity. Bounded to {@code [6, 600]}.</li>
 * </ol>
 *
 * <p>Constants come straight from
 * {@code modules/congestion_controller/goog_cc/trendline_estimator.cc}
 * and {@code modules/remote_bitrate_estimator/overuse_detector.cc}
 * in upstream WebRTC. Tuning them is rarely the right answer —
 * they're calibrated against real cellular and Wi-Fi traces.
 *
 * <p>Threading: not safe for concurrent updates. Cobalt drives one
 * estimator per remote endpoint from the call's transport thread.
 */
public final class TrendlineEstimator {
    /**
     * Number of (smoothed-time, smoothed-delay) samples the
     * least-squares regression operates over.
     */
    static final int WINDOW_SIZE = 20;

    /**
     * Exponential-smoothing coefficient applied to the running
     * accumulated-delay sum.
     */
    static final double SMOOTHING_COEFFICIENT = 0.9;

    /**
     * Multiplier on {@code slope * windowSize} that produces the
     * {@code modifiedTrend} compared against the threshold.
     */
    static final double THRESHOLD_GAIN = 4.0;

    /**
     * Initial value of the adaptive threshold {@code γ}.
     */
    static final double INITIAL_THRESHOLD = 12.5;

    /**
     * Lower bound on the adaptive threshold.
     */
    static final double MIN_THRESHOLD = 6.0;

    /**
     * Upper bound on the adaptive threshold.
     */
    static final double MAX_THRESHOLD = 600.0;

    /**
     * Threshold up-adapt gain — applied while
     * {@code |modifiedTrend| > γ}.
     */
    static final double K_UP = 0.0087;

    /**
     * Threshold down-adapt gain — applied while
     * {@code |modifiedTrend| ≤ γ}.
     */
    static final double K_DOWN = 0.039;

    /**
     * The minimum continuous duration {@code modifiedTrend > γ}
     * before the detector fires {@link BandwidthState#OVERUSE}. Acts
     * as a low-pass filter against single-spike noise.
     */
    static final double OVERUSING_TIME_MS = 10.0;

    /**
     * Cap on the inter-update {@code dt} fed into the threshold
     * adapt step — guards against pathologically-large gaps making
     * the threshold jump.
     */
    static final double MAX_TIME_DELTA_MS = 100.0;

    /**
     * Sliding window of {@link Sample} instances used by the
     * least-squares regression. Bounded to {@link #WINDOW_SIZE}.
     */
    private final Deque<Sample> window = new ArrayDeque<>(WINDOW_SIZE);

    /**
     * Running exponentially-smoothed sum of per-group delay deltas.
     */
    private double smoothedDelayMs = 0.0;

    /**
     * Running sum of per-group delay deltas (without smoothing) —
     * the input to the EMA.
     */
    private double accumulatedDelayMs = 0.0;

    /**
     * Reference timestamp of the first group seen. Sample timestamps
     * are stored relative to this so the regression works with
     * small-magnitude doubles.
     */
    private long firstArrivalMs = -1;

    /**
     * Most recent computed slope.
     */
    private double trend = 0.0;

    /**
     * Adaptive threshold {@code γ}.
     */
    private double threshold = INITIAL_THRESHOLD;

    /**
     * Cumulative time the detector has been in
     * {@link BandwidthState#OVERUSE}-candidate territory; reset
     * whenever the sign of the modified trend flips. Compared
     * against {@link #OVERUSING_TIME_MS}.
     */
    private double overusingTimeMs = 0.0;

    /**
     * Number of times in a row the modified trend has been above
     * the threshold — used to require monotonic delay growth, not
     * just a single excursion.
     */
    private int overuseCounter = 0;

    /**
     * Last classification, exposed via {@link #state()}.
     */
    private BandwidthState state = BandwidthState.NORMAL;

    /**
     * Wall-clock of the last {@link #update} — used for the
     * threshold adapt's {@code dt}.
     */
    private long lastUpdateMs = -1;

    /**
     * One sample point fed to the least-squares regression.
     *
     * @param tMs   smoothed time in milliseconds, relative to the
     *              first packet group
     * @param delay smoothed accumulated delay in milliseconds
     */
    private record Sample(double tMs, double delay) {
    }

    /**
     * Constructs a fresh estimator with the canonical WebRTC tuning.
     */
    public TrendlineEstimator() {
    }

    /**
     * Feeds one packet-group inter-arrival measurement, updates the
     * trendline, adapts the threshold, and returns the new
     * classification.
     *
     * <p>"Packet group" here is in the WebRTC sense: packets sent
     * within a 5 ms send-time burst are coalesced; this method
     * receives one (sendDelta, arrivalDelta, payload) per group, not
     * per packet. The grouping is the caller's responsibility — it
     * lives in the RTP receiver code, not this estimator.
     *
     * @param sendDeltaMs    send-time difference between this group
     *                       and the previous one
     * @param arrivalDeltaMs arrival-time difference between this
     *                       group and the previous one
     * @param payloadBytes   total payload bytes in this group
     *                       (informational; carried for parity with
     *                       the WebRTC API but unused by the
     *                       slope-only estimator)
     * @param nowMs          monotonic timestamp at which this update
     *                       is processed
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
     * Runs the over-/under-/normal classification, including the
     * {@link #OVERUSING_TIME_MS} duration filter that prevents
     * single-spike noise from triggering an OVERUSE.
     *
     * @param modifiedTrend {@code slope * windowSize * THRESHOLD_GAIN}
     * @param dtMs          time since the previous update
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
     * Adapts {@link #threshold} toward the magnitude of
     * {@code modifiedTrend}. Up-gain ({@link #K_UP}) when over the
     * threshold suppresses repeat triggers; down-gain
     * ({@link #K_DOWN}) when under recovers sensitivity. Bounded to
     * {@code [MIN_THRESHOLD, MAX_THRESHOLD]}.
     *
     * @param modifiedTrend the current modified trend
     * @param dtMs          time since the previous update; capped at
     *                      {@link #MAX_TIME_DELTA_MS}
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
     * Returns the most recent classification. Equivalent to the
     * value returned by the last {@link #update}.
     *
     * @return the current state
     */
    public BandwidthState state() {
        return state;
    }

    /**
     * Returns the latest computed slope (delay-ms per smoothed-time-ms).
     * Zero before the window has filled up.
     *
     * @return the trend slope
     */
    public double trendSlope() {
        return trend;
    }

    /**
     * Returns the current adaptive threshold ({@code γ}). Useful for
     * diagnostics.
     *
     * @return the current threshold value
     */
    public double threshold() {
        return threshold;
    }

    /**
     * Computes the slope of the least-squares-fit line through a set
     * of {@link Sample}s. Returns {@code 0.0} if the variance in
     * time is zero (all samples at the same instant) — which never
     * happens in practice.
     *
     * @param samples the sample window
     * @return the slope
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
