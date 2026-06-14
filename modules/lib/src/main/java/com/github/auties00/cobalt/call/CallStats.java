package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.model.call.CallState;
import com.github.auties00.cobalt.wam.type.CallSide;

import java.time.Instant;

/**
 * Accumulates the per-call telemetry dimensions drained into a WAM Call event when a call ends.
 *
 * <p>One instance is created with each {@link CallRuntime} and owned by it. It captures the dimensions
 * known when the call begins ({@link #callId()}, {@link #side()}, {@link #videoEnabled()},
 * {@link #startedAt()}) and is stamped at the two lifecycle transitions the runtime already drives:
 * {@link #markConnected()} the first time the call reaches {@link CallState#ACTIVE} and
 * {@link #markEnded()} when it reaches {@link CallState#ENDED}.
 *
 * @implNote This implementation is event-driven: the connected and ended instants are stamped directly
 * from {@link CallRuntime#notifyActive()} and {@link CallRuntime#end(com.github.auties00.cobalt.model.call.CallEndReason, String)}
 * rather than by a per-call thread polling the call state, so no background ticker exists.
 */
final class CallStats {
    /**
     * The call identifier these dimensions belong to.
     */
    private final String callId;

    /**
     * Which side initiated the call.
     */
    private final CallSide side;

    /**
     * Whether video was enabled at call setup.
     */
    private final boolean videoEnabled;

    /**
     * When the call was placed or accepted.
     */
    private final Instant startedAt;

    /**
     * When the call first reached {@link CallState#ACTIVE}, or {@code null} if it never connected.
     */
    private volatile Instant connectedAt;

    /**
     * When the call reached {@link CallState#ENDED}, or {@code null} until it ends.
     */
    private volatile Instant endedAt;

    /**
     * Constructs a telemetry accumulator for one call.
     *
     * @param callId       the call identifier
     * @param side         which side initiated the call
     * @param videoEnabled whether video was enabled at call setup
     * @param startedAt    when the call was placed or accepted
     */
    CallStats(String callId, CallSide side, boolean videoEnabled, Instant startedAt) {
        this.callId = callId;
        this.side = side;
        this.videoEnabled = videoEnabled;
        this.startedAt = startedAt;
    }

    /**
     * Returns the call identifier.
     *
     * @return the call identifier
     */
    String callId() {
        return callId;
    }

    /**
     * Returns which side initiated the call.
     *
     * @return the call side
     */
    CallSide side() {
        return side;
    }

    /**
     * Returns whether video was enabled at call setup.
     *
     * @return {@code true} if video was enabled
     */
    boolean videoEnabled() {
        return videoEnabled;
    }

    /**
     * Returns when the call was placed or accepted.
     *
     * @return the start instant
     */
    Instant startedAt() {
        return startedAt;
    }

    /**
     * Stamps the connected instant the first time the call reaches {@link CallState#ACTIVE}.
     *
     * <p>Idempotent: a later invocation after the first leaves the recorded instant unchanged.
     */
    void markConnected() {
        if (connectedAt == null) {
            connectedAt = Instant.now();
        }
    }

    /**
     * Stamps the ended instant the first time the call reaches {@link CallState#ENDED}.
     *
     * <p>Idempotent: a later invocation after the first leaves the recorded instant unchanged.
     */
    void markEnded() {
        if (endedAt == null) {
            endedAt = Instant.now();
        }
    }

    /**
     * Returns the connected duration in seconds, or zero when the call never reached
     * {@link CallState#ACTIVE}.
     *
     * <p>When the call connected but {@link #markEnded()} has not yet run, the duration is measured to
     * the current instant.
     *
     * @return the connected duration in seconds
     */
    long connectedDurationSeconds() {
        var connected = connectedAt;
        if (connected == null) {
            return 0;
        }
        var end = endedAt != null ? endedAt : Instant.now();
        return Math.max(0, end.getEpochSecond() - connected.getEpochSecond());
    }
}
