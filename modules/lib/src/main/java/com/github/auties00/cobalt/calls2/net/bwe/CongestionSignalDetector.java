package com.github.auties00.cobalt.calls2.net.bwe;

/**
 * Produces the {@link CongestionSignals} for a feedback round by checking round-trip time and
 * packet-loss ratio against round-trip-scaled thresholds at three sensitivity tiers, with each input
 * gated by an enable bitmask.
 *
 * <p>{@link #evaluate(long, double, double, long, long)} reads which inputs are active from the enable
 * mask: {@link #ENABLE_RTT} compares the current round-trip time against a high-tier and a default-tier
 * round-trip-scaled threshold; {@link #ENABLE_REMOTE_PLR} and {@link #ENABLE_LOCAL_PLR} compare the
 * remote and local loss ratios against fractional thresholds; {@link #ENABLE_STALENESS} flags
 * congestion when no feedback has arrived within the staleness window. The detector yields a
 * {@code congested} flag from the normal and high tiers and an {@code aggressive} flag from the
 * high-sensitivity tier, returned together as one {@link CongestionSignals}.
 *
 * <p>Instances are not thread-safe; the owning sender estimator drives one detector from the single
 * transport thread.
 *
 * @implNote This implementation ports {@code get_congestion_signals} (fn4226) from the wa-voip engine
 * ({@code rate_control/wa_rate_control.cc}). The enable bits are the recovered literals
 * ({@code 0x1} RTT, {@code 0x200} remote-PLR, {@code 0x400} local-PLR, {@code 0x800} remote-timing,
 * {@code 0x4000} local-timing, {@code 0x8000} staleness), and the staleness window is the recovered
 * {@value #STALENESS_WINDOW_MS} ms. The round-trip and loss threshold coefficients are read from
 * voip-params offsets {@code +0x450} / {@code +0x454} (round-trip) and {@code +0x45c} / {@code +0x460}
 * (loss); the captured {@code voip_settings} blob does not carry those literals, so they are supplied
 * by the caller through the constructor (re/calls2-spec/SPEC.md sec 15.4).
 */
public final class CongestionSignalDetector {
    /**
     * Enable bit for the round-trip-time threshold check.
     */
    public static final int ENABLE_RTT = 0x0001;

    /**
     * Enable bit for the remote packet-loss-ratio check.
     */
    public static final int ENABLE_REMOTE_PLR = 0x0200;

    /**
     * Enable bit for the local packet-loss-ratio check.
     */
    public static final int ENABLE_LOCAL_PLR = 0x0400;

    /**
     * Enable bit for the remote inter-arrival timing check.
     */
    public static final int ENABLE_REMOTE_TIMING = 0x0800;

    /**
     * Enable bit for the local inter-arrival timing check.
     */
    public static final int ENABLE_LOCAL_TIMING = 0x4000;

    /**
     * Enable bit for the staleness check.
     */
    public static final int ENABLE_STALENESS = 0x8000;

    /**
     * Staleness window, in milliseconds, within which feedback must arrive to avoid a staleness flag.
     *
     * <p>The recovered literal {@code 0x752f}.
     */
    static final long STALENESS_WINDOW_MS = 30_000;

    /**
     * High-tier round-trip-time coefficient applied to the current round-trip time.
     *
     * <p>The {@code cc_rtt_heavily_congestion_multiplier} at voip-params offset {@code +0x454}; supplied by
     * the caller.
     */
    private final double rttHighCoefficient;

    /**
     * Default-tier round-trip-time coefficient applied to the current round-trip time.
     *
     * <p>The {@code cc_rtt_approaching_congestion_multiplier} at voip-params offset {@code +0x450}; supplied
     * by the caller.
     */
    private final double rttDefaultCoefficient;

    /**
     * High-tier remote-loss fraction, in {@code [0, 1]}, above which the high tier trips.
     *
     * <p>Read from voip-params offset {@code +0x45c}; supplied by the caller.
     */
    private final double plrHighFraction;

    /**
     * Default-tier loss fraction, in {@code [0, 1]}, above which the normal tier trips.
     *
     * <p>Read from voip-params offset {@code +0x460}; supplied by the caller.
     */
    private final double plrDefaultFraction;

    /**
     * Baseline round-trip time, in milliseconds, the current value is compared against, or {@code 0}
     * when not yet seeded.
     *
     * <p>Seeded from the first observed round-trip time; the high and default thresholds are this
     * baseline scaled by the coefficients.
     */
    private long baselineRttMs = 0;

    /**
     * Constructs a detector with the round-trip and loss threshold coefficients.
     *
     * @param rttHighCoefficient    the high-tier round-trip coefficient
     * @param rttDefaultCoefficient the default-tier round-trip coefficient
     * @param plrHighFraction       the high-tier loss fraction, in {@code [0, 1]}
     * @param plrDefaultFraction    the default-tier loss fraction, in {@code [0, 1]}
     */
    public CongestionSignalDetector(double rttHighCoefficient, double rttDefaultCoefficient,
                                    double plrHighFraction, double plrDefaultFraction) {
        this.rttHighCoefficient = rttHighCoefficient;
        this.rttDefaultCoefficient = rttDefaultCoefficient;
        this.plrHighFraction = Math.clamp(plrHighFraction, 0.0, 1.0);
        this.plrDefaultFraction = Math.clamp(plrDefaultFraction, 0.0, 1.0);
    }

    /**
     * Evaluates the congestion signals for one feedback round.
     *
     * <p>Seeds the baseline round-trip time from the first observed value, then for each enabled input
     * tests the round-trip time against the high and default round-trip-scaled thresholds, the remote
     * and local loss ratios against the high and default fractions, and the feedback recency against
     * the staleness window. The high-tier round-trip or loss trip sets the aggressive flag; the
     * default-tier round-trip or loss trip, the staleness trip, or the aggressive trip sets the
     * congested flag. The remote-timing ({@link #ENABLE_REMOTE_TIMING}) and local-timing
     * ({@link #ENABLE_LOCAL_TIMING}) bits are accepted in {@code enableMask} but not yet evaluated (see
     * the TODO below), so setting them currently contributes nothing to the verdict.
     *
     * @param rttMs            the current round-trip time, in milliseconds; ignored when non-positive
     * @param remotePlr        the remote packet-loss ratio, in {@code [0, 1]}
     * @param localPlr         the local packet-loss ratio, in {@code [0, 1]}
     * @param feedbackAgeMs    the time, in milliseconds, since the last feedback was received
     * @param enableMask       the bitmask selecting which inputs are evaluated
     * @return the congestion signals for this round
     */
    // TODO: evaluate the remote-timing (0x800) and local-timing (0x4000) congestion bits as fn4226
    //  (get_congestion_signals, rate_control/wa_rate_control.cc) does: under 0x800 set congested when the
    //  remote-timing threshold (param5[1], fallback voip +0x46c) <= min(stream-stat +0xa0, +0x128); under
    //  0x4000, gated by the +0x15d enable byte, set congested when the local-timing threshold (param5[3],
    //  fallback voip +0x470) <= min(stream-stat +0x130, +0x128). Both inputs are inter-arrival timing
    //  windows from the transport stream-stats / inter-arrival accounting, which is not threaded into this
    //  detector (evaluate() receives no timing measurements and the caller LiveSenderBandwidthEstimator
    //  sources none); a faithful implementation needs those measurements plumbed from the GCC delay-based
    //  estimator and the two thresholds, so the bits stay inert rather than guessed. SPEC 15.4.
    public CongestionSignals evaluate(long rttMs, double remotePlr, double localPlr, long feedbackAgeMs,
                                      long enableMask) {
        if (rttMs > 0 && baselineRttMs == 0) {
            baselineRttMs = rttMs;
        }
        var congested = false;
        var aggressive = false;

        if ((enableMask & ENABLE_RTT) != 0 && rttMs > 0 && baselineRttMs > 0) {
            if (rttMs > rttHighCoefficient * baselineRttMs) {
                aggressive = true;
            }
            if (rttMs > rttDefaultCoefficient * baselineRttMs) {
                congested = true;
            }
        }
        if ((enableMask & ENABLE_REMOTE_PLR) != 0) {
            if (remotePlr >= plrHighFraction) {
                aggressive = true;
            }
            if (remotePlr >= plrDefaultFraction) {
                congested = true;
            }
        }
        if ((enableMask & ENABLE_LOCAL_PLR) != 0) {
            if (localPlr >= plrHighFraction) {
                aggressive = true;
            }
            if (localPlr >= plrDefaultFraction) {
                congested = true;
            }
        }
        if ((enableMask & ENABLE_STALENESS) != 0 && feedbackAgeMs >= STALENESS_WINDOW_MS) {
            congested = true;
        }
        if (aggressive) {
            return CongestionSignals.AGGRESSIVE;
        }
        return congested ? CongestionSignals.CONGESTED : CongestionSignals.NONE;
    }

    /**
     * Returns the baseline round-trip time the thresholds are scaled from.
     *
     * @return the baseline round-trip time, in milliseconds, or {@code 0} when not yet seeded
     */
    public long baselineRttMs() {
        return baselineRttMs;
    }

    /**
     * Resets the detector, clearing the round-trip baseline.
     */
    public void reset() {
        baselineRttMs = 0;
    }
}
