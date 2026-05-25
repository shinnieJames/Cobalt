package com.github.auties00.cobalt.call.internal.transport.ice;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents one ICE candidate pair, the {@code (local, remote)} tuple modeling a possible network
 * path between the two endpoints.
 *
 * <p>{@link IceAgent} runs a STUN binding-request connectivity check on each pair and tracks the
 * outcome through this pair's {@link IceCheckState}; the highest-priority pair that succeeds is
 * nominated as the call's transport path. The pair caches its RFC 8445 section 6.1.2.3 priority at
 * construction and exposes mutable check-state, in-flight transaction, and nomination bookkeeping
 * the agent updates as checks run.
 *
 * <p>The pair priority is the RFC 8445 section 6.1.2.3 formula:
 *
 * {@snippet :
 *   pair_priority = (2^32 * MIN(G, D))
 *                 + (2    * MAX(G, D))
 *                 + (G > D ? 1 : 0);
 * }
 *
 * <p>where {@code G} is the controlling agent's candidate priority and {@code D} is the controlled
 * agent's. The {@code controlling} flag supplied at construction decides which of the local and
 * remote candidate priorities plays each role.
 */
public final class IceCandidatePair {
    /**
     * The local-side candidate of the pair.
     */
    private final IceCandidate local;

    /**
     * The remote-side candidate of the pair.
     */
    private final IceCandidate remote;

    /**
     * The RFC 8445 section 6.1.2.3 pair priority, cached at construction because the candidate
     * priorities and the controlling role are immutable.
     */
    private final long priority;

    /**
     * Whether the local agent holds the controlling role and therefore chooses the nominated pair,
     * per RFC 8445 section 6.1.1.
     */
    private final boolean controlling;

    /**
     * The connectivity-check state, mutated atomically as the agent fires and resolves checks.
     */
    private final AtomicReference<IceCheckState> state;

    /**
     * The transaction id of the in-flight STUN binding request while the state is
     * {@link IceCheckState#IN_PROGRESS}, or {@code null} when no check is in flight.
     */
    private volatile byte[] inFlightTxId;

    /**
     * The wall-clock timestamp the in-flight check was sent at, which the agent compares against
     * the per-check timeout, or {@code null} when no check is in flight.
     */
    private volatile Instant lastCheckSent;

    /**
     * Whether the controlling agent has nominated this pair, per RFC 8445 section 8.1.
     */
    private volatile boolean nominated;

    /**
     * Constructs a pair from a local and remote candidate and caches its priority.
     *
     * <p>The pair starts in {@link IceCheckState#FROZEN}. Both candidates must address the same
     * {@link IceComponent}, since ICE pairs candidates component by component.
     *
     * @param local       the local-side candidate
     * @param remote      the remote-side candidate
     * @param controlling whether the local agent holds the controlling role
     * @throws NullPointerException     if either candidate is {@code null}
     * @throws IllegalArgumentException if the two candidates address different components
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
     * Returns the pair priority computed per RFC 8445 section 6.1.2.3.
     *
     * @return the pair priority
     */
    public long priority() {
        return priority;
    }

    /**
     * Returns whether the local agent holds the controlling role for this pair.
     *
     * @return {@code true} if the local agent is controlling
     */
    public boolean controlling() {
        return controlling;
    }

    /**
     * Returns the current connectivity-check state.
     *
     * @return the current state
     */
    public IceCheckState state() {
        return state.get();
    }

    /**
     * Atomically transitions the pair from {@code expected} to {@code next}.
     *
     * <p>The agent uses this to schedule and complete checks without losing a concurrent update;
     * the transition is a no-op when the current state has already moved past {@code expected}.
     *
     * @param expected the state the pair must currently be in for the transition to apply
     * @param next     the state to move to
     * @return {@code true} if the pair was in {@code expected} and moved to {@code next}
     */
    public boolean transition(IceCheckState expected, IceCheckState next) {
        return state.compareAndSet(expected, next);
    }

    /**
     * Sets the connectivity-check state unconditionally, regardless of the current state.
     *
     * <p>The agent uses this for a hard transition such as moving a pair to
     * {@link IceCheckState#FAILED} on timeout or on a STUN error response.
     *
     * @param next the state to set
     */
    public void forceState(IceCheckState next) {
        state.set(next);
    }

    /**
     * Returns the in-flight STUN transaction id.
     *
     * @return the transaction id of the in-flight check, or {@code null} if no check is in flight
     */
    public byte[] inFlightTxId() {
        return inFlightTxId;
    }

    /**
     * Returns the wall-clock timestamp the in-flight check was sent at.
     *
     * @return the send timestamp, or {@code null} if no check is in flight
     */
    public Instant lastCheckSent() {
        return lastCheckSent;
    }

    /**
     * Records the start of a new in-flight check, storing a defensive copy of the transaction id
     * and the send timestamp.
     *
     * <p>The agent calls this immediately after sending the binding request so that the inbound
     * response can be matched and the timeout can be measured.
     *
     * @param txId the transaction id of the binding request
     * @param at   the wall-clock timestamp the request was sent at
     */
    public void markInFlight(byte[] txId, Instant at) {
        this.inFlightTxId = txId.clone();
        this.lastCheckSent = at;
    }

    /**
     * Clears the in-flight transaction id and send timestamp after a check completes, whether it
     * succeeded or failed.
     */
    public void clearInFlight() {
        this.inFlightTxId = null;
        this.lastCheckSent = null;
    }

    /**
     * Returns whether the controlling agent has nominated this pair.
     *
     * @return {@code true} if the pair has been nominated
     */
    public boolean nominated() {
        return nominated;
    }

    /**
     * Marks the pair as nominated by the controlling agent.
     */
    public void nominate() {
        this.nominated = true;
    }

    /**
     * Computes the RFC 8445 section 6.1.2.3 pair priority from the two candidate priorities and the
     * controlling role.
     *
     * @param controlling    whether the local agent is controlling, which selects the {@code G}
     *                       and {@code D} terms of the formula
     * @param localPriority  the local candidate's priority
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

    /**
     * Indicates whether another object is a pair with the same local candidate, remote candidate,
     * and controlling role.
     *
     * @param o the object to compare against
     * @return {@code true} if {@code o} is an equal {@link IceCandidatePair}
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof IceCandidatePair p
                && local.equals(p.local) && remote.equals(p.remote)
                && controlling == p.controlling;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, derived from the local
     * candidate, remote candidate, and controlling role.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(local, remote, controlling);
    }

    /**
     * Returns a diagnostic string showing the pair's transport addresses, priority, current state,
     * and nomination flag.
     *
     * @return a human-readable description of the pair
     */
    @Override
    public String toString() {
        return "IceCandidatePair[" + local.transportAddress() + " ↔ " + remote.transportAddress()
                + ", priority=" + priority + ", state=" + state.get()
                + (nominated ? ", nominated" : "") + "]";
    }
}
