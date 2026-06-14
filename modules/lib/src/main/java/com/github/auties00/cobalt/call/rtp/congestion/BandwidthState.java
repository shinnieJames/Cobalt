package com.github.auties00.cobalt.call.rtp.congestion;

/**
 * Classifies the sender's link into one of the three delay-based congestion states.
 *
 * <p>A delay-based bandwidth estimator (Google Congestion Control's trendline plus overuse detector)
 * assigns one of these states to the current send rate by inspecting the direction in which the
 * receiver's queue is filling or draining. The AIMD rate controller reads the classification to
 * decide whether to ramp up, hold, or back off the target send bitrate.
 */
public enum BandwidthState {
    /**
     * Indicates that inter-arrival delay is shrinking and the link is being underused.
     *
     * <p>The receiver's queue drains faster than packets fill it, meaning either the link's
     * available capacity grew or the sender has been throttled below it. The rate controller
     * responds by ramping the target bitrate up.
     */
    UNDERUSE,

    /**
     * Indicates that inter-arrival delays are flat and the send rate matches the link capacity.
     *
     * <p>The current send rate tracks the link capacity, so the rate controller holds the target
     * bitrate steady.
     */
    NORMAL,

    /**
     * Indicates that inter-arrival delay is growing and the link is being overused.
     *
     * <p>The receiver's queue is filling because the sender exceeds the link's capacity. The rate
     * controller responds by multiplicatively decreasing the target bitrate.
     */
    OVERUSE
}
