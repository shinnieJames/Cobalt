package com.github.auties00.cobalt.calls2.core;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Maintains the periodically-updated immutable snapshot of one call's coarse information for listeners,
 * field statistics, and the host {@code getCallInfo} query.
 *
 * <p>The wa-voip engine keeps a small call-info structure alongside each call context that aggregates the
 * call's accumulated durations, its current result, and its call-link information, and refreshes it on
 * every event so the host can read a consistent view without touching the live context. This class is
 * that aggregate: it holds one {@link Snapshot} per call and rebuilds it from the engine's running state
 * whenever a transition or a periodic tick calls {@link #update(Calls2CallState, Calls2CallResult,
 * Duration, Duration, Duration, String)} or {@link #updateForEvent(CallEventType, Calls2CallState,
 * Calls2CallResult, Duration, Duration, Duration, String)}. The latter additionally reproduces the
 * engine's event-to-result derivation, mapping the dispatched {@link CallEventType} to the
 * {@link Calls2CallResult} the snapshot should carry when the caller has not already resolved one.
 *
 * <p>The snapshot is published through a {@code volatile} field, so {@link #snapshot()} is a lock-free
 * read returning the latest immutable {@link Snapshot} (or an empty result before the first update); this
 * is the only read path and it never blocks an update or another read. Updates are serialised behind a
 * single lock so a snapshot is rebuilt atomically from a consistent set of inputs; the publish itself is
 * a volatile write of the immutable record. Because every {@link Snapshot} is immutable, handing
 * one to a listener or the host leaks no mutable state.
 *
 * @implNote This implementation ports the {@code call_info_manager} (native singleton at
 * {@code DAT_ram_00151044}) and its {@code call_info_manager_update} (fn10665) and
 * {@code call_info_snapshot_reset} from the wa-voip WASM module {@code ff-tScznZ8P}. The native snapshot
 * struct at manager offset {@code 0x10} carries a pointer at {@code +0x00}, a valid flag at {@code +0x14},
 * link-info at {@code +0x18}, total duration at {@code +0x20}, lonely duration at {@code +0x28}, active
 * duration at {@code +0x30}, and setup information at {@code +0x40}; this port models those as the fields
 * of the immutable {@link Snapshot} record. The native manager refreshes the snapshot from
 * {@code change_call_state_no_event} (fn10920, which calls fn10665 after a state change) and from the
 * periodic watchdog; this port exposes that refresh as {@link #update}. The event-to-result derivation in
 * {@link #updateForEvent} reproduces the {@code set_call_result} branches of
 * {@code call_info_manager_update} (fn11076) that write the call result at {@code call_context+0x478} the
 * snapshot reads; the function's separate call-log-state writes at {@code +0x47c}/{@code +0x480} are a
 * distinct axis the {@link Snapshot} does not carry.
 */
public final class Calls2CallInfoManager {
    /**
     * Holds the latest published immutable snapshot, or {@code null} before the first update.
     *
     * <p>Published by a volatile write after each update and read lock-free by {@link #snapshot()},
     * so a reader always observes a fully-built immutable {@link Snapshot} and never a torn one.
     */
    private volatile Snapshot current;

    /**
     * Serialises updates so a snapshot is rebuilt atomically from a consistent set of inputs.
     *
     * <p>This is the {@code call-info-mutex} analogue: it guards the rebuild-and-publish sequence, while
     * the read path bypasses it entirely through the {@code volatile} field.
     */
    private final Object lock;

    /**
     * Constructs an info manager with no snapshot yet published.
     *
     * <p>Until the first update {@link #snapshot()} returns an empty result, reflecting a call whose info
     * has not yet been computed.
     */
    public Calls2CallInfoManager() {
        this.lock = new Object();
    }

    /**
     * Rebuilds and publishes the call-info snapshot from the engine's current running state.
     *
     * <p>Builds a fresh immutable {@link Snapshot} from the supplied state, result, and accumulated
     * durations, marks it valid, and publishes it for lock-free reads. The total duration is the sum of
     * the active and lonely durations the engine accumulates as the call leaves the {@link
     * Calls2CallState#CALL_ACTIVE} and {@link Calls2CallState#CONNECTED_LONELY} states. A {@code null}
     * link token denotes a call that is not a group-call-link join.
     *
     * @param state           the call's current internal state; must not be {@code null}
     * @param result          the call's current result; must not be {@code null}
     * @param activeDuration  the accumulated time the call has spent with peer media flowing; must not be
     *                        {@code null}
     * @param lonelyDuration  the accumulated time the call has spent connected without a peer; must not be
     *                        {@code null}
     * @param setupDuration   the time elapsed from call start to the first connected state; must not be
     *                        {@code null}
     * @param linkToken       the call-link token, or {@code null} when the call is not a link join
     * @throws NullPointerException if {@code state}, {@code result}, {@code activeDuration},
     *                              {@code lonelyDuration}, or {@code setupDuration} is {@code null}
     */
    public void update(Calls2CallState state, Calls2CallResult result, Duration activeDuration,
                       Duration lonelyDuration, Duration setupDuration, String linkToken) {
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(result, "result cannot be null");
        Objects.requireNonNull(activeDuration, "activeDuration cannot be null");
        Objects.requireNonNull(lonelyDuration, "lonelyDuration cannot be null");
        Objects.requireNonNull(setupDuration, "setupDuration cannot be null");
        synchronized (lock) {
            current = new Snapshot(true, state, result, activeDuration.plus(lonelyDuration),
                    activeDuration, lonelyDuration, setupDuration, linkToken);
        }
    }

    /**
     * Rebuilds and publishes the call-info snapshot for a dispatched event, deriving the result from the
     * event when one is implied.
     *
     * <p>Reproduces the engine's event-to-result derivation: a dispatched {@link CallEventType} the engine
     * resolves to a fresh call result (the relay-bind failure, the audio-init error, and the video-preview
     * failure, each resolving to {@link Calls2CallResult#SETUP_ERROR}) overrides the supplied {@code result}
     * with that event-derived result; every other event leaves the supplied result intact, including the
     * call-concluding events whose result is already recorded on the context before they are dispatched. The
     * remaining inputs are aggregated exactly as in {@link #update(Calls2CallState, Calls2CallResult,
     * Duration, Duration, Duration, String)}.
     *
     * @param event           the dispatched event whose implied result the snapshot reflects; must not be
     *                        {@code null}
     * @param state           the call's current internal state; must not be {@code null}
     * @param result          the call's current result; must not be {@code null}
     * @param activeDuration  the accumulated time the call has spent with peer media flowing; must not be
     *                        {@code null}
     * @param lonelyDuration  the accumulated time the call has spent connected without a peer; must not be
     *                        {@code null}
     * @param setupDuration   the time elapsed from call start to the first connected state; must not be
     *                        {@code null}
     * @param linkToken       the call-link token, or {@code null} when the call is not a link join
     * @throws NullPointerException if {@code event}, {@code state}, {@code result}, {@code activeDuration},
     *                              {@code lonelyDuration}, or {@code setupDuration} is {@code null}
     */
    public void updateForEvent(CallEventType event, Calls2CallState state, Calls2CallResult result,
                               Duration activeDuration, Duration lonelyDuration, Duration setupDuration,
                               String linkToken) {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(result, "result cannot be null");
        update(state, deriveResult(event, result), activeDuration, lonelyDuration, setupDuration, linkToken);
    }

    /**
     * Returns the latest published call-info snapshot, if one has been computed.
     *
     * <p>This is a lock-free read of the immutable {@link Snapshot} published by the most recent update;
     * it returns an empty result before the first update. The returned snapshot is safe to hand directly
     * to a listener or the host because it is immutable.
     *
     * @return the latest snapshot, or an empty result when none has been computed yet
     */
    public Optional<Snapshot> snapshot() {
        return Optional.ofNullable(current);
    }

    /**
     * Resets the manager so it holds no snapshot.
     *
     * <p>Clears the published snapshot so {@link #snapshot()} reports an empty result again, mirroring the
     * engine's {@code call_info_snapshot_reset} run when a call context is freed. A later update publishes
     * a fresh snapshot.
     */
    public void reset() {
        synchronized (lock) {
            current = null;
        }
    }

    /**
     * Derives the snapshot result from a dispatched event, falling back to the supplied result.
     *
     * <p>Reproduces the {@code set_call_result} branches of the engine's event-to-result mapper: a dispatched
     * event that the engine resolves to a fresh call result writes that result onto the call context before
     * the snapshot is rebuilt, so the snapshot carries the event-derived result rather than a stale
     * in-progress one. The events that resolve an unconditional result map onto a {@link Calls2CallResult}
     * here; the relay-bind failure, the audio-init error, and the video-preview failure each resolve to
     * {@link Calls2CallResult#SETUP_ERROR}. Every other event leaves the caller-supplied {@code result}
     * intact: the engine's two conditional branches ({@link CallEventType#CALL_STATE_CHANGED}, gated on the
     * dispatched state-change reason, and {@link CallEventType#CALL_OFFER_NACK_RECEIVED}, gated on the
     * call's offer-ack and connection state) read context fields this two-argument derivation does not
     * carry, so the result they imply is already recorded on the supplied {@code result} by the time the
     * event reaches this manager and the manager does not re-derive it; and the call-concluding events
     * ({@link CallEventType#CALL_FATAL}, {@link CallEventType#UPDATE_1ON1_CALL_LOG},
     * {@link CallEventType#UPDATE_JOINABLE_CALL_LOG}) have their result recorded on the context by the
     * terminate, reject, accept, and fatal-emitter paths before they are dispatched, so they too surface the
     * supplied result unchanged.
     *
     * @implNote This implementation ports the {@code set_call_result} (fn10923) branches of
     * {@code call_info_manager_update} (fn11076, {@code call_info_manager.cc}) in the wa-voip WASM module
     * {@code ff-tScznZ8P}: {@code 0x2f} ({@link CallEventType#RELAY_BINDS_FAILED}), {@code 0x13}
     * ({@link CallEventType#AUDIO_INIT_ERROR}), and {@code 0x3a} ({@link CallEventType#VIDEO_PREVIEW_FAILED})
     * each call {@code set_call_result(6)}, which is {@link Calls2CallResult#SETUP_ERROR} (the result the
     * native {@code result_to_str} table names {@code "SetupError"}); the snapshot then reads that result
     * from {@code call_context+0x478}. The native function also writes the separate call-log-state field at
     * {@code call_context+0x47c} for {@code 0x29}/{@code 0x2a}/{@code 0x2e}/{@code 0x42}/{@code 0x43} and the
     * setup-information field at {@code +0x480}; those are a distinct log-state axis the {@link Snapshot}
     * does not carry (it carries the {@link Calls2CallResult} at {@code +0x478}), so they are not mapped
     * here. The {@code 0x10} and {@code 4} branches call {@code set_call_result} only behind a guard on the
     * dispatched reason ({@code *param3}) and on context fields ({@code +0x484}, the prior result at
     * {@code +0x480}) that this derivation is not given, so it preserves the supplied result for them.
     *
     * @param event  the dispatched event
     * @param result the caller-supplied current result
     * @return the result the snapshot should carry, never {@code null}
     */
    private static Calls2CallResult deriveResult(CallEventType event, Calls2CallResult result) {
        return switch (event) {
            case RELAY_BINDS_FAILED, AUDIO_INIT_ERROR, VIDEO_PREVIEW_FAILED -> Calls2CallResult.SETUP_ERROR;
            default -> result;
        };
    }

    /**
     * Holds one immutable, point-in-time view of a call's coarse information for listeners and the host.
     *
     * <p>A snapshot aggregates the call's current state and result, the durations the engine accumulates
     * as the call moves through its connected states, the time the call took to set up, and the call-link
     * token when the call is a link join. It is the lock-free read result of {@link
     * Calls2CallInfoManager#snapshot()} and is immutable, so it may be retained and read by any thread
     * after publication.
     *
     * @param valid          whether the snapshot holds computed information, always {@code true} for a
     *                       published snapshot and modelling the native valid flag
     * @param state          the call's internal state at snapshot time; never {@code null}
     * @param result         the call's result at snapshot time; never {@code null}
     * @param totalDuration  the sum of {@code activeDuration} and {@code lonelyDuration}; never
     *                       {@code null}
     * @param activeDuration the accumulated time spent with peer media flowing; never {@code null}
     * @param lonelyDuration the accumulated time spent connected without a peer; never {@code null}
     * @param setupDuration  the time from call start to the first connected state; never {@code null}
     * @param linkToken      the call-link token, or {@code null} when the call is not a link join
     */
    public record Snapshot(boolean valid, Calls2CallState state, Calls2CallResult result,
                           Duration totalDuration, Duration activeDuration, Duration lonelyDuration,
                           Duration setupDuration, String linkToken) {
        /**
         * Canonicalizes the snapshot, rejecting a null state, result, or duration.
         *
         * @throws NullPointerException if {@code state}, {@code result}, {@code totalDuration},
         *                              {@code activeDuration}, {@code lonelyDuration}, or
         *                              {@code setupDuration} is {@code null}
         */
        public Snapshot {
            Objects.requireNonNull(state, "state cannot be null");
            Objects.requireNonNull(result, "result cannot be null");
            Objects.requireNonNull(totalDuration, "totalDuration cannot be null");
            Objects.requireNonNull(activeDuration, "activeDuration cannot be null");
            Objects.requireNonNull(lonelyDuration, "lonelyDuration cannot be null");
            Objects.requireNonNull(setupDuration, "setupDuration cannot be null");
        }

        /**
         * Returns the call-link token as an optional, empty when the call is not a link join.
         *
         * @return the call-link token, or an empty result when absent
         */
        public Optional<String> linkTokenOptional() {
            return Optional.ofNullable(linkToken);
        }
    }
}
