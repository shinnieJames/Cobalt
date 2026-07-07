package com.github.auties00.cobalt.calls2.core;

import java.util.Objects;
import java.util.Optional;

/**
 * The single transition guard for a call's internal state machine, and the implementation of the
 * {@link Calls2CallStateTransition} seam.
 *
 * <p>This is the one chokepoint through which every {@link Calls2CallState} change for a
 * {@link Calls2CallContext} passes. It reproduces the engine's {@code change_call_state_no_event} guard
 * exactly: it accepts a self-transition as a no-op, enforces the two closed in-call transition sets
 * ({@link Calls2CallState#CALL_ACTIVE} only to {@link Calls2CallState#NONE} or
 * {@link Calls2CallState#CONNECTED_LONELY}, and {@link Calls2CallState#CONNECTED_LONELY} only to
 * {@link Calls2CallState#NONE} or {@link Calls2CallState#CALL_ACTIVE}), treats transitions into
 * {@link Calls2CallState#LINK} and {@link Calls2CallState#ENDING} (and a teardown to
 * {@link Calls2CallState#NONE} from either) as silent, and otherwise applies the transition, performing
 * the duration accounting and the connected-lonely timer (de)scheduling the engine attaches to entering
 * and leaving the two in-call states. An illegal transition is rejected without mutating the context,
 * mirroring the native guard that logs and returns failure rather than forcing the state.
 *
 * <p>The class exposes the guard at two levels. The {@link Calls2CallStateTransition} seam method
 * {@link #transition(String, Calls2CallState)} is the cross-unit entry the lifecycle controller calls: it
 * resolves the call by id through the injected {@link Calls2CallManager}, takes the context's lock, runs
 * the guard, and reports the prior state on acceptance or an empty result on rejection (or when no call
 * exists). The context-level {@link #transition(Calls2CallContext, Calls2CallState)} and
 * {@link #transition(Calls2CallContext, Calls2CallState, long)} methods run the guard against an
 * already-resolved context and return the richer {@link Transition} record; a caller using them holds the
 * context's {@linkplain Calls2CallContext#lock() lock} itself.
 *
 * <p>The richer {@link Transition} record reports whether the change was accepted, the previous and new
 * states, and whether the caller should fire the listener-facing
 * {@linkplain CallEventType#CALL_STATE_CHANGED call-state-changed} event. The native engine splits this:
 * {@code change_call_state_no_event} performs the guarded mutation and the wrapper {@code change_call_state}
 * fires event {@code 0x10}; this guard returns the wrapper's emit decision so the lifecycle controller can
 * fire the event (through {@link Calls2CallEventSink}) after the transition, but it never fires the event
 * itself, because the event bus is owned by a sibling subsystem. The silent {@link Calls2CallState#LINK}
 * and {@link Calls2CallState#ENDING} transitions report no event, matching the engine's silent paths.
 *
 * @implNote This implementation is a faithful port of {@code change_call_state_no_event} (fn10920,
 * {@code call_state.cc}) of the wa-voip WASM module {@code ff-tScznZ8P}, with the wrapper
 * {@code change_call_state} (fn10921) emit decision folded into the returned {@link Transition}. The two
 * closed in-call transition sets, the silent {@link Calls2CallState#LINK}/{@link Calls2CallState#ENDING}
 * paths, and the {@code newState == 0} silent-teardown shortcut from {@code Link}/{@code Ending} are the
 * guard's documented branches; the duration accounting (add the elapsed segment to the lonely accumulator
 * on leaving {@link Calls2CallState#CONNECTED_LONELY} and to the active accumulator on leaving
 * {@link Calls2CallState#CALL_ACTIVE}), the connected-lonely timer scheduling on entering
 * {@link Calls2CallState#CONNECTED_LONELY} (interval picked by direction from the parsed lonely-state
 * timeouts), and the connected-lonely timer cancellation plus transport-timestamp capture on entering
 * {@link Calls2CallState#CALL_ACTIVE} are the {@code ctx+0x4b}/{@code +0x4c} duration region and the
 * {@code ctx+0x7e} timer-handle effects of the guard. The native server-state-code assignment per
 * peer/self on entering {@link Calls2CallState#CALL_ACTIVE} (codes 5/7/3/1/2) is a participant-plane
 * effect owned by the membership subsystem and is not performed here. The call result is deliberately not
 * touched: it is the separate {@code set_call_result} field at {@code ctx+0x478}
 * ({@link Calls2CallContext#result(Calls2CallResult)}), which the guard never conflates with the state.
 */
public final class Calls2CallStateMachine implements Calls2CallStateTransition {
    /**
     * Logs accepted transitions and rejected illegal transitions, mirroring the engine guard's logging.
     */
    private static final System.Logger LOGGER = System.getLogger(Calls2CallStateMachine.class.getName());

    /**
     * The manager the {@link #transition(String, Calls2CallState)} seam resolves a call context from.
     */
    private final Calls2CallManager manager;

    /**
     * Constructs a state machine that resolves call contexts through the given manager.
     *
     * <p>The guard logic itself holds no per-call state; the manager is held only so the
     * {@link Calls2CallStateTransition} seam can resolve a context by call id. The context-level
     * {@link #transition(Calls2CallContext, Calls2CallState)} overloads do not use the manager and run
     * against the context the caller passes. One instance per engine is sufficient and may be shared
     * across calls.
     *
     * @param manager the manager the call-id seam resolves contexts from
     * @throws NullPointerException if {@code manager} is {@code null}
     */
    public Calls2CallStateMachine(Calls2CallManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the call by id through the {@link Calls2CallManager}, takes the resolved context's
     * {@linkplain Calls2CallContext#lock() lock}, and runs the guard against it. When no call exists for
     * the id the result is empty; otherwise the guard runs and the result holds the prior state on
     * acceptance (including a no-op self-transition, which returns the unchanged current state) or is
     * empty when the guard rejected the transition.
     *
     * @param callId   {@inheritDoc}
     * @param newState {@inheritDoc}
     * @return an {@link Optional} holding the prior state when the transition was accepted, or empty when
     *         the guard rejected it or no call exists for the identifier
     * @throws NullPointerException if {@code callId} or {@code newState} is {@code null}
     * @implNote This implementation is the engine's {@code change_call_state_no_event} (fn10920) reached
     * by call id: the native callers hold the {@code call-info-mutex} for the resolved context, which this
     * method models by taking {@link Calls2CallContext#lock()} for the duration of the guard.
     */
    @Override
    public Optional<Calls2CallState> transition(String callId, Calls2CallState newState) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(newState, "newState cannot be null");
        var context = manager.getByCallId(callId).orElse(null);
        if (context == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "State transition to {0} ignored: no call for id {1}", newState, callId);
            return Optional.empty();
        }
        context.lock().lock();
        try {
            var result = transition(context, newState);
            return result.accepted() ? Optional.of(result.oldState()) : Optional.empty();
        } finally {
            context.lock().unlock();
        }
    }

    /**
     * Attempts to transition a call context to a new state, timestamping any segment effect with the
     * current wall-clock time.
     *
     * <p>This is the common entry point; it delegates to {@link #transition(Calls2CallContext,
     * Calls2CallState, long)} with {@link System#currentTimeMillis()} as the segment timestamp. The caller
     * holds the context's {@linkplain Calls2CallContext#lock() lock}.
     *
     * @param context  the call context to transition
     * @param newState the state to transition to
     * @return the {@link Transition} describing the outcome
     * @throws NullPointerException if {@code context} or {@code newState} is {@code null}
     */
    public Transition transition(Calls2CallContext context, Calls2CallState newState) {
        return transition(context, newState, System.currentTimeMillis());
    }

    /**
     * Attempts to transition a call context to a new state, using the given timestamp for any segment
     * effect.
     *
     * <p>The guard applies these rules in order, exactly as the engine does:
     * <ul>
     *   <li>A transition to the current state is a no-op success: the context is unchanged and the result
     *       reports the transition was accepted but carries no event.</li>
     *   <li>From {@link Calls2CallState#CONNECTED_LONELY} only {@link Calls2CallState#NONE} and
     *       {@link Calls2CallState#CALL_ACTIVE} are legal; any other target is rejected without
     *       mutation.</li>
     *   <li>From {@link Calls2CallState#CALL_ACTIVE} only {@link Calls2CallState#NONE} and
     *       {@link Calls2CallState#CONNECTED_LONELY} are legal; any other target is rejected without
     *       mutation.</li>
     *   <li>A transition into {@link Calls2CallState#LINK}, or to {@link Calls2CallState#NONE} from
     *       {@link Calls2CallState#LINK}, applies the state silently and reports no event.</li>
     *   <li>A transition into {@link Calls2CallState#ENDING}, or to {@link Calls2CallState#NONE} from
     *       {@link Calls2CallState#ENDING}, applies the state silently and reports no event.</li>
     *   <li>Otherwise the transition is applied with full accounting: leaving
     *       {@link Calls2CallState#CONNECTED_LONELY} closes the lonely segment and cancels the
     *       connected-lonely timer; leaving {@link Calls2CallState#CALL_ACTIVE} closes the active segment;
     *       entering {@link Calls2CallState#CONNECTED_LONELY} opens a lonely segment and schedules the
     *       connected-lonely timer; entering {@link Calls2CallState#CALL_ACTIVE} cancels the
     *       connected-lonely timer and opens an active segment; and the result reports an event should be
     *       fired.</li>
     * </ul>
     *
     * <p>The {@code nowMillis} timestamp is used only to close and open the duration segments, so a caller
     * can supply a deterministic clock in tests. The caller holds the context's
     * {@linkplain Calls2CallContext#lock() lock}.
     *
     * @param context   the call context to transition
     * @param newState  the state to transition to
     * @param nowMillis the wall-clock millisecond timestamp used for segment accounting
     * @return the {@link Transition} describing the outcome
     * @throws NullPointerException if {@code context} or {@code newState} is {@code null}
     */
    public Transition transition(Calls2CallContext context, Calls2CallState newState, long nowMillis) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(newState, "newState cannot be null");
        var current = context.state();
        if (newState == current) {
            return Transition.noChange(current);
        }
        if (current == Calls2CallState.CONNECTED_LONELY && !isLegalFromConnectedLonely(newState)) {
            return reject(context, current, newState);
        }
        if (current == Calls2CallState.CALL_ACTIVE && !isLegalFromCallActive(newState)) {
            return reject(context, current, newState);
        }
        if (isSilentLink(current, newState)) {
            context.state(newState);
            return Transition.silent(current, newState);
        }
        if (isSilentEnding(current, newState)) {
            context.state(newState);
            return Transition.silent(current, newState);
        }
        applyAccounting(context, current, nowMillis);
        context.state(newState);
        applyEntryEffects(context, newState, nowMillis);
        LOGGER.log(System.Logger.Level.DEBUG, "change_call_state call id {0}: [{1} -> {2}]",
                context.callId(), current, newState);
        return Transition.accepted(current, newState);
    }

    /**
     * Returns whether a target state is a legal successor of {@link Calls2CallState#CONNECTED_LONELY}.
     *
     * <p>Only {@link Calls2CallState#NONE} (teardown) and {@link Calls2CallState#CALL_ACTIVE} (a peer
     * (re)connected) are legal, matching the engine's closed transition set out of the lonely state.
     *
     * @param newState the candidate target state
     * @return {@code true} if the transition is legal
     */
    private static boolean isLegalFromConnectedLonely(Calls2CallState newState) {
        return newState == Calls2CallState.NONE || newState == Calls2CallState.CALL_ACTIVE;
    }

    /**
     * Returns whether a target state is a legal successor of {@link Calls2CallState#CALL_ACTIVE}.
     *
     * <p>Only {@link Calls2CallState#NONE} (teardown) and {@link Calls2CallState#CONNECTED_LONELY} (the
     * last peer left) are legal, matching the engine's closed transition set out of the active state.
     *
     * @param newState the candidate target state
     * @return {@code true} if the transition is legal
     */
    private static boolean isLegalFromCallActive(Calls2CallState newState) {
        return newState == Calls2CallState.NONE || newState == Calls2CallState.CONNECTED_LONELY;
    }

    /**
     * Returns whether a transition is the silent {@link Calls2CallState#LINK} path.
     *
     * <p>The engine applies a transition into {@link Calls2CallState#LINK}, and a teardown to
     * {@link Calls2CallState#NONE} from {@link Calls2CallState#LINK}, without taking the event path.
     *
     * @param current  the current state
     * @param newState the target state
     * @return {@code true} if the transition is a silent link transition
     */
    private static boolean isSilentLink(Calls2CallState current, Calls2CallState newState) {
        return newState == Calls2CallState.LINK
                || (newState == Calls2CallState.NONE && current == Calls2CallState.LINK);
    }

    /**
     * Returns whether a transition is the silent {@link Calls2CallState#ENDING} path.
     *
     * <p>The engine applies a transition into {@link Calls2CallState#ENDING}, and a teardown to
     * {@link Calls2CallState#NONE} from {@link Calls2CallState#ENDING}, without taking the event path.
     *
     * @param current  the current state
     * @param newState the target state
     * @return {@code true} if the transition is a silent ending transition
     */
    private static boolean isSilentEnding(Calls2CallState current, Calls2CallState newState) {
        return newState == Calls2CallState.ENDING
                || (newState == Calls2CallState.NONE && current == Calls2CallState.ENDING);
    }

    /**
     * Applies the leave-state duration accounting and lonely-timer cancellation for a transition.
     *
     * <p>Leaving {@link Calls2CallState#CONNECTED_LONELY} closes the open lonely segment and cancels the
     * connected-lonely timer; leaving {@link Calls2CallState#CALL_ACTIVE} closes the open active segment.
     * This runs before the state field is written so the segment that is closing is still the current
     * state's segment.
     *
     * @param context   the call context being transitioned
     * @param current   the state being left
     * @param nowMillis the timestamp used to close the leaving segment
     */
    private static void applyAccounting(Calls2CallContext context, Calls2CallState current, long nowMillis) {
        if (current == Calls2CallState.CONNECTED_LONELY) {
            context.closeLonelySegment(nowMillis);
            context.fireCancelConnectedLonelyTimer();
        } else if (current == Calls2CallState.CALL_ACTIVE) {
            context.closeActiveSegment(nowMillis);
        }
    }

    /**
     * Applies the enter-state segment opening and timer effects for a transition.
     *
     * <p>Entering {@link Calls2CallState#CONNECTED_LONELY} opens a lonely segment and schedules the
     * connected-lonely timer; entering {@link Calls2CallState#CALL_ACTIVE} cancels the connected-lonely
     * timer (in case it was scheduled) and opens an active segment. This runs after the state field is
     * written so a scheduler that reads {@link Calls2CallContext#state()} sees the new state.
     *
     * @param context   the call context being transitioned
     * @param newState  the state being entered
     * @param nowMillis the timestamp used to open the entered segment
     */
    private static void applyEntryEffects(Calls2CallContext context, Calls2CallState newState, long nowMillis) {
        switch (newState) {
            case CONNECTED_LONELY -> {
                context.openLonelySegment(nowMillis);
                context.fireScheduleConnectedLonelyTimer();
            }
            case CALL_ACTIVE -> {
                context.fireCancelConnectedLonelyTimer();
                context.openActiveSegment(nowMillis);
            }
            default -> {
                // No enter-state effect for the other states; their setup is driven by the lifecycle
                // controller, not by the state guard.
            }
        }
    }

    /**
     * Rejects an illegal transition, logging it and leaving the context unchanged.
     *
     * @param context  the call context whose transition is rejected
     * @param current  the current state
     * @param newState the rejected target state
     * @return a rejected {@link Transition} reporting the unchanged current state
     */
    private static Transition reject(Calls2CallContext context, Calls2CallState current, Calls2CallState newState) {
        LOGGER.log(System.Logger.Level.WARNING,
                "Rejecting illegal call state transition for call {0}: [{1} -> {2}]",
                context.callId(), current, newState);
        return Transition.rejected(current, newState);
    }

    /**
     * Describes the outcome of a state-transition attempt.
     *
     * <p>A transition is either accepted (the state changed, or a no-op self-transition) or rejected (the
     * change was illegal and the context was not mutated). An accepted transition that took the event path
     * reports {@link #shouldEmitEvent()} {@code true} and carries the
     * {@linkplain CallEventType#CALL_STATE_CHANGED call-state-changed} event the lifecycle controller fires
     * after the transition; a no-op self-transition and the silent {@link Calls2CallState#LINK} and
     * {@link Calls2CallState#ENDING} transitions report {@code false}. The {@link #oldState()} and
     * {@link #newState()} record the transition's endpoints; for a rejected transition {@link #newState()}
     * is the target that was refused and the context remains in {@link #oldState()}.
     *
     * @param accepted        whether the transition was applied (including a no-op self-transition)
     * @param oldState        the state the context was in before the attempt
     * @param newState        the state transitioned to, or the refused target for a rejected transition
     * @param shouldEmitEvent whether the caller should fire the call-state-changed event
     */
    public record Transition(boolean accepted, Calls2CallState oldState, Calls2CallState newState,
                             boolean shouldEmitEvent) {
        /**
         * Canonicalizes the record components.
         *
         * @throws NullPointerException if {@code oldState} or {@code newState} is {@code null}
         */
        public Transition {
            Objects.requireNonNull(oldState, "oldState cannot be null");
            Objects.requireNonNull(newState, "newState cannot be null");
        }

        /**
         * Returns a no-op self-transition outcome for the given state.
         *
         * <p>The endpoints are both {@code state}, the transition is accepted, and no event is emitted,
         * matching the engine guard's success-without-effect on a transition to the current state.
         *
         * @param state the unchanged current state
         * @return a no-op accepted transition carrying no event
         */
        static Transition noChange(Calls2CallState state) {
            return new Transition(true, state, state, false);
        }

        /**
         * Returns an accepted, event-bearing transition outcome.
         *
         * @param oldState the state left
         * @param newState the state entered
         * @return an accepted transition that should fire the call-state-changed event
         */
        static Transition accepted(Calls2CallState oldState, Calls2CallState newState) {
            return new Transition(true, oldState, newState, true);
        }

        /**
         * Returns an accepted but silent transition outcome.
         *
         * <p>The silent {@link Calls2CallState#LINK} and {@link Calls2CallState#ENDING} transitions apply
         * the state but report no event, matching the engine's silent paths.
         *
         * @param oldState the state left
         * @param newState the state entered
         * @return an accepted transition that should not fire an event
         */
        static Transition silent(Calls2CallState oldState, Calls2CallState newState) {
            return new Transition(true, oldState, newState, false);
        }

        /**
         * Returns a rejected transition outcome.
         *
         * <p>The context was not mutated and remains in {@code oldState}; {@code newState} records the
         * refused target.
         *
         * @param oldState the unchanged current state
         * @param newState the refused target state
         * @return a rejected transition carrying no event
         */
        static Transition rejected(Calls2CallState oldState, Calls2CallState newState) {
            return new Transition(false, oldState, newState, false);
        }

        /**
         * Returns whether this transition actually changed the state.
         *
         * <p>A transition changed the state when it was accepted and its endpoints differ; a no-op
         * self-transition and a rejected transition both report {@code false}.
         *
         * @return {@code true} when the state changed
         */
        public boolean changedState() {
            return accepted && oldState != newState;
        }

        /**
         * Returns the call-state-changed event to fire, if this transition took the event path.
         *
         * <p>The result is present only when {@link #shouldEmitEvent()} is {@code true}, and it is always
         * {@link CallEventType#CALL_STATE_CHANGED}; the lifecycle controller fires it on the event bus
         * after the transition. A no-op, silent, or rejected transition yields an empty result.
         *
         * @return an {@link Optional} holding the call-state-changed event, or empty when no event fires
         */
        public Optional<CallEventType> event() {
            return shouldEmitEvent ? Optional.of(CallEventType.CALL_STATE_CHANGED) : Optional.empty();
        }
    }
}
