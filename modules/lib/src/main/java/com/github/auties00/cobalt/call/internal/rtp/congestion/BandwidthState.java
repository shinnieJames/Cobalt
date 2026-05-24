package com.github.auties00.cobalt.call.internal.rtp.congestion;

/**
 * The three states a delay-based bandwidth estimator (Google
 * Congestion Control's trendline + overuse detector) classifies the
 * sender's link into. Used by the AIMD rate controller to decide
 * whether to ramp up, hold, or back off the target send bitrate.
 */
public enum BandwidthState {
    /**
     * Inter-arrival jitter is shrinking — the receiver's queue is
     * draining faster than packets fill it. Either the link's
     * available capacity grew, or the sender has been throttled
     * below it. The rate controller should ramp the target bitrate
     * up.
     */
    UNDERUSE,

    /**
     * Inter-arrival delays are flat. The current send rate is
     * tracking the link capacity; hold steady.
     */
    NORMAL,

    /**
     * Inter-arrival delay is growing — the receiver's queue is
     * filling. The sender is exceeding the link's capacity; the
     * rate controller should multiplicatively decrease the target
     * bitrate.
     */
    OVERUSE
}
