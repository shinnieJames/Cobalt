package com.github.auties00.cobalt.call.internal.transport.ice;

/**
 * The five candidate-pair states defined by RFC 8445 §6.1.2.6 — the
 * connectivity-check state-machine that {@link IceAgent} drives for
 * each {@link IceCandidatePair}.
 *
 * <p>The transitions are:
 *
 * <pre>
 *   FROZEN ──→ WAITING ──→ IN_PROGRESS ──→ SUCCEEDED
 *                              │
 *                              └────────────────→ FAILED
 * </pre>
 *
 * <p>FROZEN pairs are unfrozen by the agent's foundation-grouping
 * logic (RFC 8445 §6.1.2.6), at which point they enter
 * {@link #WAITING}. The pair runner picks {@link #WAITING} pairs in
 * priority order, fires a STUN binding request, and moves them to
 * {@link #IN_PROGRESS}. Successful responses transition to
 * {@link #SUCCEEDED}; timeouts or error responses go to
 * {@link #FAILED}.
 */
public enum IceCheckState {
    /**
     * The pair has been formed but its foundation is shared with
     * another in-progress pair, so it's queued but not yet eligible
     * for a connectivity check.
     */
    FROZEN,
    /**
     * The pair is eligible and will be picked up by the next
     * scheduling tick.
     */
    WAITING,
    /**
     * A STUN binding request has been sent and we're waiting for the
     * response.
     */
    IN_PROGRESS,
    /**
     * The peer responded successfully — the pair is a candidate for
     * nomination.
     */
    SUCCEEDED,
    /**
     * Either the request timed out, the peer answered with an error
     * code, or MESSAGE-INTEGRITY failed.
     */
    FAILED
}
