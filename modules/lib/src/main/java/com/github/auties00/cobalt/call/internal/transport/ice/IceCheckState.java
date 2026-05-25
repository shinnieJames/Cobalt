package com.github.auties00.cobalt.call.internal.transport.ice;

/**
 * Enumerates the five candidate-pair states of the RFC 8445 section 6.1.2.6 connectivity-check
 * state machine that {@link IceAgent} drives for each {@link IceCandidatePair}.
 *
 * <p>A pair begins {@link #FROZEN}, is unfrozen to {@link #WAITING} by the agent's
 * foundation-grouping logic, advances to {@link #IN_PROGRESS} when a STUN binding request is sent,
 * and ends in either {@link #SUCCEEDED} or {@link #FAILED}. The transitions are:
 *
 * {@snippet :
 *   FROZEN --> WAITING --> IN_PROGRESS --> SUCCEEDED
 *                              |
 *                              +---------> FAILED
 * }
 *
 * <p>{@link IceCandidatePair#transition(IceCheckState, IceCheckState)} performs each forward step
 * atomically, while {@link IceCandidatePair#forceState(IceCheckState)} performs the unconditional
 * move to {@link #FAILED}.
 */
public enum IceCheckState {
    /**
     * The pair has been formed but its foundation is shared with another pair that has already
     * been unfrozen, so it is queued and not yet eligible for a connectivity check.
     *
     * <p>This is the initial state of every pair produced by the check-list build.
     */
    FROZEN,
    /**
     * The pair is eligible for a connectivity check and will be picked up by the next scheduling
     * tick in priority order.
     */
    WAITING,
    /**
     * A STUN binding request has been sent for the pair and the agent is awaiting the response.
     *
     * <p>While in this state the pair holds an in-flight transaction id and a send timestamp that
     * the agent uses to detect a timeout.
     */
    IN_PROGRESS,
    /**
     * The peer answered the binding request successfully, making the pair eligible for nomination.
     */
    SUCCEEDED,
    /**
     * The check did not succeed: the request timed out, the peer answered with an error code, or
     * MESSAGE-INTEGRITY verification failed.
     */
    FAILED
}
