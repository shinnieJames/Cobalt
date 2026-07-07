package com.github.auties00.cobalt.calls2;

import com.github.auties00.cobalt.calls2.core.Calls2CallResult;
import com.github.auties00.cobalt.model.call.CallState;
import com.github.auties00.cobalt.wam.type.CallSide;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Accumulates the per-call telemetry dimensions drained into a WAM Call event when a call ends.
 *
 * <p>One instance is created with each {@link Calls2Runtime} and owned by it. It captures the dimensions
 * known when the call begins ({@link #callId()}, {@link #side()}, {@link #videoEnabled()},
 * {@link #startedAt()}) and is stamped at the two lifecycle transitions the runtime drives:
 * {@link #markConnected()} the first time the call reaches {@link CallState#ACTIVE} and
 * {@link #markEnded()} when it reaches {@link CallState#ENDED}. The telemetry emitter reads the
 * accumulated dimensions through the accessors when the call is unregistered and folds them into a single
 * WAM Call event, so an in-progress call never publishes telemetry.
 *
 * @implNote This implementation is event-driven: the connected and ended instants are stamped directly
 * from the runtime's lifecycle transitions rather than by a per-call thread polling the call state, so no
 * background ticker exists. It is the calls2 analogue of the legacy {@code call.CallStats} accumulator and
 * carries the same four start-time dimensions plus the two stamped instants, so the same WAM Call event
 * fields can be populated from it without a behavioural change.
 */
public final class Calls2CallStats {
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
     * The engine call result the service resolved for this call, or {@code null} when none was resolved
     * and the end reason alone determines the telemetry result type.
     *
     * <p>Set by the service for the outcomes whose distinct result the lifecycle controller's terminal end
     * reason cannot recover, in particular the accept-ack NACK ({@link Calls2CallResult#CALL_IS_FULL} and
     * {@link Calls2CallResult#CALL_DOES_NOT_EXIST_FOR_REJOIN}), so the WAM Call event reports the engine's
     * own result code rather than the lossy end-reason projection.
     */
    private volatile Calls2CallResult result;

    /**
     * Constructs a telemetry accumulator for one call.
     *
     * @param callId       the call identifier
     * @param side         which side initiated the call
     * @param videoEnabled whether video was enabled at call setup
     * @param startedAt    when the call was placed or accepted
     * @throws NullPointerException if {@code callId}, {@code side}, or {@code startedAt} is {@code null}
     */
    public Calls2CallStats(String callId, CallSide side, boolean videoEnabled, Instant startedAt) {
        this.callId = Objects.requireNonNull(callId, "callId cannot be null");
        this.side = Objects.requireNonNull(side, "side cannot be null");
        this.videoEnabled = videoEnabled;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt cannot be null");
        // TODO: wire calls2 last-minute call telemetry - own per-metric LastMinuteBuffer instances, insert(nowMillis, amount) on each stats tick, and emit last_min_* WAM fields (avg_rtt, jb delay/lost, video render-freeze) via sum()/count(), gated like should_report_last_min_metrics.
        //  Left as a TODO because the faithful per-second sampler this needs does not exist yet and cannot be built without guessing. In the wa-voip engine (module fBusb96-x4w) wa_last_min_buf_insert (fn10121) is driven from the engine's per-second field-stats collection loop (fn4790/fn11758), gated by should_report_last_min_metrics (fn12904); each last_min_* field is a distinct metric's 60s aggregate. Cobalt is deliberately tickerless (this class stamps only lifecycle instants) and has no 1Hz per-call stats sampler, so nothing produces the per-second amounts to insert. The metric sources are also not reachable as per-second snapshots: NetEqStatistics is a drain-at-end snapshot (LiveNetEq.statistics()) not a per-second delta, avg RTT lives in scattered per-unit RttEstimator instances (VideoTimingController/UnifiedAudioQualityControl/FastRampController) with no aggregated surface, and there is no video render-freeze source at all (VideoJitterBuffer tracks no freeze durations). Feeding fabricated numbers would reach the WAM wire, so this stays unwired until a faithful per-second field-stats sampler and a video render-freeze detector are ported.
    }

    /**
     * Returns the call identifier these dimensions belong to.
     *
     * @return the call identifier
     */
    public String callId() {
        return callId;
    }

    /**
     * Returns which side initiated the call.
     *
     * @return the call side
     */
    public CallSide side() {
        return side;
    }

    /**
     * Returns whether video was enabled at call setup.
     *
     * @return {@code true} if video was enabled
     */
    public boolean videoEnabled() {
        return videoEnabled;
    }

    /**
     * Returns when the call was placed or accepted.
     *
     * @return the start instant
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * Stamps the connected instant the first time the call reaches {@link CallState#ACTIVE}.
     *
     * <p>Idempotent: a later invocation after the first leaves the recorded instant unchanged, so a call
     * that briefly reconnects does not reset its connected timestamp.
     */
    public void markConnected() {
        if (connectedAt == null) {
            connectedAt = Instant.now();
        }
    }

    /**
     * Stamps the ended instant the first time the call reaches {@link CallState#ENDED}.
     *
     * <p>Idempotent: a later invocation after the first leaves the recorded instant unchanged, so a
     * double teardown does not move the ended timestamp.
     */
    public void markEnded() {
        if (endedAt == null) {
            endedAt = Instant.now();
        }
    }

    /**
     * Returns the connected duration in seconds, or zero when the call never reached
     * {@link CallState#ACTIVE}.
     *
     * <p>When the call connected but {@link #markEnded()} has not yet run, the duration is measured to
     * the current instant, so a still-active call reports its running duration. The result is clamped to
     * be non-negative.
     *
     * @return the connected duration in seconds
     */
    public long connectedDurationSeconds() {
        var connected = connectedAt;
        if (connected == null) {
            return 0;
        }
        var end = endedAt != null ? endedAt : Instant.now();
        return Math.max(0, end.getEpochSecond() - connected.getEpochSecond());
    }

    /**
     * Records the engine call result the service resolved for this call.
     *
     * <p>Stamped before the lifecycle controller tears the call down, so the recorded result survives the
     * controller's release of the engine context and is available when the telemetry is drained at
     * unregister. A {@code null} clears it.
     *
     * @param result the resolved call result, or {@code null} to clear
     */
    public void result(Calls2CallResult result) {
        this.result = result;
    }

    /**
     * Returns the engine call result the service resolved for this call, when one was resolved.
     *
     * @return an {@link Optional} holding the resolved result, or empty when none was resolved
     */
    public Optional<Calls2CallResult> result() {
        return Optional.ofNullable(result);
    }
}
