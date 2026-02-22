package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.Objects;

/**
 * Represents a pending mutation that hasn't been synced to the server yet.
 *
 * <p>Pending mutations are queued locally and sent to the server during the next sync cycle.
 */
public final class SyncPendingMutation {
    private final DecryptedMutation.Trusted mutation;
    private final int attemptCount;

    /**
     * Creates a new pending mutation
     *
     * @param mutation     the mutation to be synced
     * @param attemptCount the number of sync attempts made for this mutation
     */
    public SyncPendingMutation(DecryptedMutation.Trusted mutation, int attemptCount) {
        this.mutation = mutation;
        this.attemptCount = attemptCount;
    }

    /**
     * Creates a copy of this mutation with incremented attempt count.
     *
     * @return a new pending mutation with attempt count + 1
     */
    public SyncPendingMutation incrementAttempt() {
        return new SyncPendingMutation(mutation, attemptCount + 1);
    }

    public DecryptedMutation.Trusted mutation() {
        return mutation;
    }

    public int attemptCount() {
        return attemptCount;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof SyncPendingMutation that
                            && attemptCount == that.attemptCount
                            && Objects.equals(mutation, that.mutation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mutation, attemptCount);
    }

    @Override
    public String toString() {
        return "SyncPendingMutation[" +
               "mutation=" + mutation + ", " +
               "attemptCount=" + attemptCount + ']';
    }
}