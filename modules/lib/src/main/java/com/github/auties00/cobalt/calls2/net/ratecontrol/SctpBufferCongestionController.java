package com.github.auties00.cobalt.calls2.net.ratecontrol;

/**
 * Detects congestion of the SCTP data-channel send buffer and clamps the video send rate while the
 * buffer is filling.
 *
 * <p>The controller is fed the SCTP buffer occupancy each round and detects congestion two ways. The
 * standing-tail test fires when the occupancy stays above a high watermark, meaning the buffer is not
 * draining. The peak-plus-slope test fits a linear regression over a sliding window of recent occupancy
 * samples and fires when the buffer amount is high, the tail exceeds a threshold, and the slope is
 * positive, meaning the buffer is filling faster than it drains. While congested the controller reports
 * a clamped rate computed from the current target so the video encoder backs off; it clears only when
 * the tail drops below a low watermark and feedback has been received within the recency threshold, so a
 * stalled feedback path does not let the clamp release prematurely.
 *
 * <p>The controller holds no clock; the caller supplies a current-time reading and the most recent
 * feedback time each round so the recency gate is computed against real elapsed time. Instances are not
 * thread-safe; the single rate-control thread that owns one drives all updates.
 *
 * @implNote This implementation ports {@code wa_sctp_buffer_congestion_controller_update} (fn4285,
 * {@code rate_control/wa_sctp_buffer_congestion_controller.cc} of the wa-voip WASM module
 * {@code ff-tScznZ8P}, {@code rev-net-bwe}). The native controller detects congestion by a standing-tail
 * watermark and by a linear-regression slope over a buffer-occupancy window, clamps the rate on
 * activation, and on clear requires the tail below the low watermark and feedback received within a
 * threshold (logging {@code "SCTP buffer congestion activated: ba=%d, tail=%d, high=%d, clamped to %u"}
 * and {@code "SCTP buffer congestion cleared: tail=%d, low=%d"}). The high watermark, low watermark,
 * persistence count, rate factor, decrease factor, and feedback recency are server voip-params
 * ({@code p->sctp_buffer_*}, struct fields {@code param1[0x19a]} through {@code param1[0x1a0]}) whose
 * compiled-in defaults are all zero, so the native detection is disabled until a per-call
 * {@code voip_settings} blob supplies them; the live server pushes them and the operative values are wired
 * into {@link #CLAMP_FACTOR} and {@link #defaults()} (decoded from {@code <voip_settings uncompressed=1>}
 * in stanzas-primary.jsonl, union in voip-settings-merged.json). The occupancy-window length is a
 * compiled-in literal (four samples) and the least-squares slope over the window reproduces the native
 * peak-plus-slope linear regression.
 */
public final class SctpBufferCongestionController {
    /**
     * The number of occupancy samples the slope-regression window retains.
     *
     * <p>The least-squares slope is fit over up to this many recent samples; the window scrolls as new
     * samples arrive.
     *
     * @implNote This implementation uses four samples, the compiled-in slide-window depth the native
     * {@code wa_sctp_buffer_congestion_controller_init} (fn4298) stamps into both occupancy ring buffers
     * ({@code *(undefined1 *)(param1 + 9) = 4} and {@code *(undefined1 *)(param1 + 0x4c) = 4}, the
     * {@code wa_slide_window_stat} capacity byte) of {@code wa_sctp_buffer_congestion_controller.cc} in the
     * wa-voip WASM module {@code ff-tScznZ8P}. The native peak-plus-slope regression then walks the four
     * stored deltas (the {@code do {...} while (bVar3 < 3)} loops in fn4285), so the window is four, not a
     * configurable value.
     */
    private static final int OCCUPANCY_WINDOW = 4;

    /**
     * The least-squares abscissa sums {@code sum(i)} for {@code i} in {@code 0..n-1}, indexed by the live
     * sample count {@code n}.
     *
     * <p>Tabled for the sample counts {@link #occupancySlope()} evaluates ({@code n} in {@code 2..4}); the
     * abscissa is the fixed sample index, so this sum depends only on {@code n}. The unused {@code n < 2}
     * slots are {@code 0}.
     */
    private static final long[] SUM_X_BY_N = {0, 0, 1, 3, 6};

    /**
     * The fraction of the current target the rate is clamped to while congested.
     *
     * <p>On activation the reported clamp is this fraction of the target the encoder would otherwise
     * use, backing the video rate off until the buffer drains.
     *
     * @implNote The native clamp (fn4285) computes the clamped rate as
     * {@code (p->sctp_buffer_congestion_decrease_factor * buffer_amount) / 100}
     * ({@code wa_sctp_buffer_congestion_controller.cc} lines 248 and 403,
     * {@code (longlong)param1[0x19f] * (ulonglong)*(uint *)(param2 + 0x14c) / 100}), so the percent is the
     * server voip-param {@code sctp_buffer_congestion_decrease_factor} (struct field {@code param1[0x19f]}).
     * The compiled-in default is 0, but the live server pushes 70, so this implementation clamps to 70
     * percent of the target each congested round: voip-settings-merged.json
     * {@code vid_rc.sctp_buffer_congestion_decrease_factor=70} (decoded from {@code <voip_settings
     * uncompressed=1>} in stanzas-primary.jsonl). The companion floor
     * {@code vid_rc.min_decrease_factor_on_congestion=33} bounds how far the persistence-weighted decrease
     * may fall over repeated rounds; this controller applies the single-round percent directly.
     */
    private static final double CLAMP_FACTOR = 0.70;

    /**
     * The buffer occupancy, in bytes, above which the standing-tail test fires.
     */
    private final long highWatermarkBytes;

    /**
     * The buffer occupancy, in bytes, below which the congestion may clear.
     */
    private final long lowWatermarkBytes;

    /**
     * The maximum age, in milliseconds, of the most recent feedback for the clamp to be allowed to
     * clear.
     */
    private final long feedbackThresholdMs;

    /**
     * The minimum buffer amount for the peak-plus-slope test to consider the buffer high.
     */
    private final long bufferAmountThresholdBytes;

    /**
     * The number of consecutive rounds a detection or clear condition must hold before it takes effect.
     *
     * <p>Both the activation tests and the clear test are gated by this count: a transient single-round
     * spike neither activates nor clears the congestion.
     */
    private final int persistenceCount;

    /**
     * The sliding window of recent occupancy samples, in bytes, for the slope regression.
     */
    private final long[] occupancyWindow;

    /**
     * The number of live samples in {@link #occupancyWindow}, saturating at its length.
     */
    private int sampleCount;

    /**
     * The next write index into {@link #occupancyWindow}, modulo its length.
     */
    private int writeIndex;

    /**
     * The number of consecutive rounds an activation condition (standing-tail or peak-plus-slope) has
     * held, reset to zero on the first round neither condition holds.
     *
     * <p>Congestion activates once this reaches {@link #persistenceCount}.
     */
    private int activateStreak;

    /**
     * The number of consecutive rounds the clear condition (occupancy below the low watermark with fresh
     * feedback) has held, reset to zero on the first round it does not hold.
     *
     * <p>Congestion clears once this reaches {@link #persistenceCount}.
     */
    private int clearStreak;

    /**
     * Whether the controller currently reports the buffer as congested.
     */
    private boolean congested;

    /**
     * Constructs a controller with the given watermarks and feedback recency threshold.
     *
     * @param highWatermarkBytes         the standing-tail high watermark in bytes; must be positive
     * @param lowWatermarkBytes          the clear low watermark in bytes; must be in
     *                                   {@code [0, highWatermarkBytes]}
     * @param feedbackThresholdMs        the feedback recency threshold in milliseconds; must be positive
     * @param bufferAmountThresholdBytes the buffer-amount threshold for the peak-plus-slope test in
     *                                   bytes; must be non-negative
     * @param persistenceCount          the number of consecutive rounds an activation or clear condition
     *                                   must hold before taking effect; must be positive
     * @throws IllegalArgumentException if the watermarks are inconsistent or a bound is out of range
     */
    public SctpBufferCongestionController(long highWatermarkBytes,
                                          long lowWatermarkBytes,
                                          long feedbackThresholdMs,
                                          long bufferAmountThresholdBytes,
                                          int persistenceCount) {
        if (highWatermarkBytes <= 0) {
            throw new IllegalArgumentException("highWatermarkBytes must be positive, got " + highWatermarkBytes);
        }
        if (lowWatermarkBytes < 0 || lowWatermarkBytes > highWatermarkBytes) {
            throw new IllegalArgumentException("lowWatermarkBytes must be in [0, " + highWatermarkBytes
                    + "], got " + lowWatermarkBytes);
        }
        if (feedbackThresholdMs <= 0) {
            throw new IllegalArgumentException("feedbackThresholdMs must be positive, got " + feedbackThresholdMs);
        }
        if (bufferAmountThresholdBytes < 0) {
            throw new IllegalArgumentException(
                    "bufferAmountThresholdBytes must be non-negative, got " + bufferAmountThresholdBytes);
        }
        if (persistenceCount <= 0) {
            throw new IllegalArgumentException("persistenceCount must be positive, got " + persistenceCount);
        }
        this.highWatermarkBytes = highWatermarkBytes;
        this.lowWatermarkBytes = lowWatermarkBytes;
        this.feedbackThresholdMs = feedbackThresholdMs;
        this.bufferAmountThresholdBytes = bufferAmountThresholdBytes;
        this.persistenceCount = persistenceCount;
        this.occupancyWindow = new long[OCCUPANCY_WINDOW];
        this.sampleCount = 0;
        this.writeIndex = 0;
        this.activateStreak = 0;
        this.clearStreak = 0;
        this.congested = false;
    }

    /**
     * Returns a default controller seeded with the live server watermarks.
     *
     * <p>The watermark, factor, and feedback-recency fields are server voip-params whose compiled-in
     * default is zero (which would leave the native detection disabled); the live server pushes the
     * operative values, so this seeds a fifteen-thousand-byte high watermark, a seven-thousand-five-hundred-
     * byte low watermark, a two-thousand-millisecond feedback recency, and a three-round persistence count
     * from the live blob, and retains the thirty-two-kilobyte peak-plus-slope buffer-amount threshold for
     * which the server pushes no key.
     *
     * @return the default SCTP buffer congestion controller
     * @implNote The high watermark is the server voip-param {@code sctp_buffer_high_threshold_bytes}
     * (struct field {@code param1[0x19b]}), the low watermark is {@code sctp_buffer_low_threshold_bytes}
     * ({@code param1[0x19c]}), and the feedback recency is {@code sctp_buffer_clear_rtcp_fresh_threshold_ms};
     * detection is gated by {@code enable_sctp_buffer_congestion_detection} ({@code param1[0x19a]}) which the
     * server enables. Live values: voip-settings-merged.json
     * {@code vid_rc.sctp_buffer_high_threshold_bytes=15000},
     * {@code vid_rc.sctp_buffer_low_threshold_bytes=7500},
     * {@code vid_rc.sctp_buffer_clear_rtcp_fresh_threshold_ms=2000}, and
     * {@code vid_rc.enable_sctp_buffer_congestion_detection=1} (decoded from {@code <voip_settings
     * uncompressed=1>} in stanzas-primary.jsonl). The persistence count
     * ({@code sctp_buffer_congestion_persistence_count}, {@code param1[0x19d]}) is 3 in the same live blob
     * ({@code vid_rc.sctp_buffer_congestion_persistence_count=3}) and is threaded through to
     * {@link #persistenceCount}, gating both activation and clear as fn4285's three streak counters
     * ({@code param1[2]} standing-tail, {@code param1[4]} peak-plus-slope, {@code param1[3]} clear) do. The
     * rate factor ({@code sctp_buffer_threshold_factor_ms}, {@code param1[0x19e]}, live 500) sizes the
     * native watermarks dynamically from the running bandwidth estimate
     * ({@code (estimate * threshold_factor_ms) / 8000}, fn4285), and the companion floor
     * ({@code min_decrease_factor_on_congestion}, live 33) bounds repeated decreases in the video rate
     * controller, not in fn4285; this controller takes the watermarks as fixed constructor arguments and
     * applies the single-round decrease percent directly, so those two live values are recorded for the
     * spec but not threaded here. The peak-plus-slope buffer-amount threshold (32 kilobytes) has no
     * corresponding {@code sctp_buffer_*} key in the 759-key live voip_settings union and so keeps its
     * compiled-in stand-in.
     */
    // TODO: size the high/low watermarks dynamically from the running bandwidth estimate as fn4285 does
    //  ((estimate * sctp_buffer_threshold_factor_ms[500]) / 8000) once update() receives the bandwidth
    //  estimate; the controller currently takes fixed watermarks because VideoRateController.apply passes
    //  no estimate input (param1[0x19e], rate_control/wa_sctp_buffer_congestion_controller.cc fn4285).
    public static SctpBufferCongestionController defaults() {
        return new SctpBufferCongestionController(15000, 7500, 2000, 32 * 1024, 3);
    }

    /**
     * Folds the latest buffer occupancy in, updates the congestion verdict, and returns whether the
     * buffer is congested.
     *
     * <p>Records the occupancy into the slope window, then evaluates the standing-tail and
     * peak-plus-slope tests. Congestion activates only once an activation test (either the standing-tail
     * or the peak-plus-slope) has fired for {@link #persistenceCount} consecutive rounds, and clears only
     * once the occupancy has stayed below the low watermark with feedback within the recency threshold for
     * {@link #persistenceCount} consecutive rounds; a single transient round that breaks the streak resets
     * the corresponding counter so a momentary spike neither activates nor releases the clamp.
     *
     * @param occupancyBytes      the current SCTP send-buffer occupancy in bytes
     * @param lastFeedbackMs      the time of the most recent feedback in milliseconds, from the same
     *                            monotonic source as {@code nowMs}
     * @param nowMs               the current time in milliseconds, from a monotonic source
     * @return {@code true} when the buffer is congested after this round
     */
    public boolean update(long occupancyBytes, long lastFeedbackMs, long nowMs) {
        occupancyWindow[writeIndex] = occupancyBytes;
        writeIndex = (writeIndex + 1) % OCCUPANCY_WINDOW;
        if (sampleCount < OCCUPANCY_WINDOW) {
            sampleCount++;
        }

        if (congested) {
            var feedbackFresh = nowMs - lastFeedbackMs <= feedbackThresholdMs;
            if (occupancyBytes < lowWatermarkBytes && feedbackFresh) {
                clearStreak++;
                if (clearStreak >= persistenceCount) {
                    congested = false;
                    clearStreak = 0;
                    activateStreak = 0;
                }
            } else {
                clearStreak = 0;
            }
        } else {
            var standingTail = occupancyBytes > highWatermarkBytes;
            var peakAndSlope = occupancyBytes > bufferAmountThresholdBytes && occupancySlope() > 0.0;
            if (standingTail || peakAndSlope) {
                activateStreak++;
                if (activateStreak >= persistenceCount) {
                    congested = true;
                    activateStreak = 0;
                    clearStreak = 0;
                }
            } else {
                activateStreak = 0;
            }
        }
        return congested;
    }

    /**
     * Returns the send rate clamped for the current congestion state.
     *
     * <p>When congested the rate is {@link #CLAMP_FACTOR} of the supplied target; otherwise the target is
     * returned unchanged.
     *
     * @param targetBps the rate the encoder would otherwise use, in bits per second
     * @return the clamped rate while congested, else {@code targetBps}
     */
    public long clampRate(long targetBps) {
        if (!congested) {
            return targetBps;
        }
        return (long) (targetBps * CLAMP_FACTOR);
    }

    /**
     * Returns whether the buffer is currently congested.
     *
     * @return {@code true} when congested
     */
    public boolean isCongested() {
        return congested;
    }

    /**
     * Clears the occupancy window and the congestion state.
     *
     * <p>Used on a transport restart so a stale window does not carry forward.
     */
    public void reset() {
        sampleCount = 0;
        writeIndex = 0;
        activateStreak = 0;
        clearStreak = 0;
        congested = false;
    }

    /**
     * Returns a value with the same sign as the least-squares slope of the occupancy window.
     *
     * <p>Fits a line over the live samples in arrival order with the sample index as the abscissa; the
     * returned value is positive exactly when the buffer is filling. The least-squares denominator
     * {@code n*sum(x*x) - sum(x)*sum(x)} is a strictly positive function of the sample count for the
     * counts evaluated ({@code n} in {@code 2..4}), so the slope sign equals the numerator sign; the sole
     * caller tests only the sign, so the constant positive-denominator division is elided and the
     * numerator is returned directly from two long products ({@code n*sum(x*y)} against
     * {@code sum(x)*sum(y)}). Returns zero with fewer than two samples.
     *
     * @return a sign-equivalent of the occupancy slope: positive when filling, zero when too few samples
     *         are present
     */
    private double occupancySlope() {
        if (sampleCount < 2) {
            return 0.0;
        }
        var n = sampleCount;
        var sumX = SUM_X_BY_N[n];
        var sumY = 0L;
        var sumXy = 0L;
        for (var i = 0; i < n; i++) {
            var index = (writeIndex - sampleCount + i + OCCUPANCY_WINDOW) % OCCUPANCY_WINDOW;
            var y = occupancyWindow[index];
            sumY += y;
            sumXy += (long) i * y;
        }
        return (double) (n * sumXy - sumX * sumY);
    }
}
