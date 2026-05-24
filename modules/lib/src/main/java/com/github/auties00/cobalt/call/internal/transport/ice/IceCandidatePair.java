package com.github.auties00.cobalt.call.internal.transport.ice;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One candidate pair — a {@code (local, remote)} tuple representing a
 * possible path. The agent runs a STUN binding-request connectivity
 * check on each pair; the highest-priority pair that succeeds is
 * nominated as the call's transport path.
 *
 * <p>{@link #priority()} is the RFC 8445 §6.1.2.3 formula:
 *
 * <pre>{@code
 *   pair_priority = (2^32 * MIN(G, D))
 *                 + (2    * MAX(G, D))
 *                 + (G > D ? 1 : 0)
 * }</pre>
 *
 * <p>where G is the controlling agent's candidate priority and D is
 * the controlled agent's. The {@code controlling} flag at
 * construction picks which is which.
 */
public final class IceCandidatePair {
    /**
     * The local-side candidate.
     */
    private final IceCandidate local;

    /**
     * The remote-side candidate.
     */
    private final IceCandidate remote;

    /**
     * RFC 8445 §6.1.2.3 priority — cached at construction since the
     * inputs are immutable.
     */
    private final long priority;

    /**
     * Whether the local agent is in the controlling role (picks the
     * nominated pair) per RFC 8445 §6.1.1.
     */
    private final boolean controlling;

    /**
     * Connectivity-check state — mutates as the agent fires checks.
     */
    private final AtomicReference<IceCheckState> state;

    /**
     * The transaction id of the in-flight STUN binding request when
     * {@code state == IN_PROGRESS}; {@code null} otherwise.
     */
    private volatile byte[] inFlightTxId;

    /**
     * Wall-clock time the in-flight check was sent; used by the
     * agent to time out checks.
     */
    private volatile Instant lastCheckSent;

    /**
     * Whether the pair has been nominated by the controlling agent
     * per RFC 8445 §8.1.
     */
    private volatile boolean nominated;

    /**
     * Constructs a new pair.
     *
     * @param local       the local-side candidate
     * @param remote      the remote-side candidate
     * @param controlling whether the local agent is in the
     *                    controlling role
     * @throws NullPointerException     if either candidate is
     *                                  {@code null}
     * @throws IllegalArgumentException if the candidates address
     *                                  different components
     */
    public IceCandidatePair(IceCandidate local, IceCandidate remote, boolean controlling) {
        this.local = Objects.requireNonNull(local, "local cannot be null");
        this.remote = Objects.requireNonNull(remote, "remote cannot be null");
        if (local.component() != remote.component()) {
            throw new IllegalArgumentException(
                    "candidate components must match: local=" + local.component()
                            + " remote=" + remote.component());
        }
        this.controlling = controlling;
        this.priority = computePriority(controlling, local.priority(), remote.priority());
        this.state = new AtomicReference<>(IceCheckState.FROZEN);
    }

    /**
     * Returns the local-side candidate.
     *
     * @return the local candidate
     */
    public IceCandidate local() {
        return local;
    }

    /**
     * Returns the remote-side candidate.
     *
     * @return the remote candidate
     */
    public IceCandidate remote() {
        return remote;
    }

    /**
     * Returns the pair priority per RFC 8445 §6.1.2.3.
     *
     * @return the pair priority
     */
    public long priority() {
        return priority;
    }

    /**
     * Returns whether the local agent is in the controlling role.
     *
     * @return {@code true} for controlling
     */
    public boolean controlling() {
        return controlling;
    }

    /**
     * Returns the current connectivity-check state.
     *
     * @return the state
     */
    public IceCheckState state() {
        return state.get();
    }

    /**
     * Atomically transitions the pair from {@code expected} to
     * {@code next} — used by the agent when scheduling and
     * completing checks. Falls through silently when the actual
     * state has moved on.
     *
     * @param expected the expected current state
     * @param next     the target state
     * @return {@code true} if the transition occurred
     */
    public boolean transition(IceCheckState expected, IceCheckState next) {
        return state.compareAndSet(expected, next);
    }

    /**
     * Sets the state unconditionally. Used for hard transitions like
     * "STUN error class 4xx received".
     *
     * @param next the target state
     */
    public void forceState(IceCheckState next) {
        state.set(next);
    }

    /**
     * Returns the in-flight STUN transaction id, or {@code null} if
     * no check is in flight.
     *
     * @return the transaction id, or {@code null}
     */
    public byte[] inFlightTxId() {
        return inFlightTxId;
    }

    /**
     * Returns the timestamp at which the in-flight check was sent,
     * or {@code null} if no check is in flight.
     *
     * @return the timestamp, or {@code null}
     */
    public Instant lastCheckSent() {
        return lastCheckSent;
    }

    /**
     * Records the start of a new in-flight check. The agent calls
     * this immediately after sending the binding request.
     *
     * @param txId the transaction id of the request
     * @param at   the wall-clock timestamp the request was sent
     */
    public void markInFlight(byte[] txId, Instant at) {
        this.inFlightTxId = txId.clone();
        this.lastCheckSent = at;
    }

    /**
     * Clears the in-flight bookkeeping after a check completes
     * (succeeds or fails).
     */
    public void clearInFlight() {
        this.inFlightTxId = null;
        this.lastCheckSent = null;
    }

    /**
     * Returns whether this pair has been nominated by the
     * controlling agent.
     *
     * @return {@code true} if nominated
     */
    public boolean nominated() {
        return nominated;
    }

    /**
     * Marks the pair as nominated.
     */
    public void nominate() {
        this.nominated = true;
    }

    /**
     * Computes the RFC 8445 §6.1.2.3 pair priority.
     *
     * @param controlling   whether the local agent is controlling
     * @param localPriority the local candidate's priority
     * @param remotePriority the remote candidate's priority
     * @return the pair priority
     */
    private static long computePriority(boolean controlling, long localPriority, long remotePriority) {
        var g = controlling ? localPriority : remotePriority;
        var d = controlling ? remotePriority : localPriority;
        var min = Math.min(g, d);
        var max = Math.max(g, d);
        long extra = (g > d) ? 1 : 0;
        return (min << 32) + (2L * max) + extra;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IceCandidatePair p
                && local.equals(p.local) && remote.equals(p.remote)
                && controlling == p.controlling;
    }

    @Override
    public int hashCode() {
        return Objects.hash(local, remote, controlling);
    }

    @Override
    public String toString() {
        return "IceCandidatePair[" + local.transportAddress() + " ↔ " + remote.transportAddress()
                + ", priority=" + priority + ", state=" + state.get()
                + (nominated ? ", nominated" : "") + "]";
    }
}
