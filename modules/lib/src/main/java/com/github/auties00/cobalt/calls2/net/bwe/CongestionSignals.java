package com.github.auties00.cobalt.calls2.net.bwe;

/**
 * Holds the two boolean flags the {@link CongestionSignalDetector} produces from a feedback round.
 *
 * <p>The {@code congested} flag drives the sender estimator's decrease and hold logic; the
 * {@code aggressive} flag marks the higher-sensitivity tier, used to back off more sharply or to gate
 * the audio-quality controller. The two are independent: a round can be congested without being
 * aggressive, and the detector sets each from its own threshold tier.
 *
 * @param congested  whether the link is judged congested this round
 * @param aggressive whether the higher-sensitivity tier also tripped, calling for a sharper response
 * @implNote This record models the two output flags ({@code congested} at parameter 6,
 * {@code aggressive} at parameter 7) of {@code get_congestion_signals} (fn4226) in the wa-voip engine
 * ({@code rate_control/wa_rate_control.cc}) (re/calls2-spec/SPEC.md sec 15.4).
 */
public record CongestionSignals(boolean congested, boolean aggressive) {
    /**
     * A signals value with neither flag set, used as the not-congested result.
     */
    public static final CongestionSignals NONE = new CongestionSignals(false, false);

    /**
     * A signals value with the congested flag set but not the aggressive flag, the default-tier trip.
     */
    public static final CongestionSignals CONGESTED = new CongestionSignals(true, false);

    /**
     * A signals value with both flags set, the high-sensitivity trip; the aggressive flag always implies
     * the congested flag.
     */
    public static final CongestionSignals AGGRESSIVE = new CongestionSignals(true, true);
}
