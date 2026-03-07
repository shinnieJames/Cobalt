package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a pending mutation that hasn't been synced to the server yet.
 *
 * <p>Pending mutations are queued locally and sent to the server during the next sync cycle.
 * Each mutation has a unique {@code mutationId} that persists through the sync cycle for
 * correlating upload requests with server acknowledgements.
 */
public final class SyncPendingMutation {
    private final String mutationId;
    private final DecryptedMutation.Trusted mutation;
    private final int attemptCount;

    /**
     * Creates a new pending mutation with an auto-generated unique ID.
     *
     * @param mutation     the mutation to be synced
     * @param attemptCount the number of sync attempts made for this mutation
     */
    public SyncPendingMutation(DecryptedMutation.Trusted mutation, int attemptCount) {
        this(UUID.randomUUID().toString(), mutation, attemptCount);
    }

    /**
     * Creates a new pending mutation with the specified ID.
     *
     * @param mutationId   the unique identifier for tracking through the sync cycle
     * @param mutation     the mutation to be synced
     * @param attemptCount the number of sync attempts made for this mutation
     */
    public SyncPendingMutation(String mutationId, DecryptedMutation.Trusted mutation, int attemptCount) {
        this.mutationId = mutationId;
        this.mutation = mutation;
        this.attemptCount = attemptCount;
    }

    /**
     * Creates a copy of this mutation with incremented attempt count.
     *
     * @return a new pending mutation with attempt count + 1
     */
    public SyncPendingMutation incrementAttempt() {
        return new SyncPendingMutation(mutationId, mutation, attemptCount + 1);
    }

    /**
     * Returns the unique identifier for this pending mutation.
     *
     * <p>Per WhatsApp Web: mutation IDs are used to correlate uploaded mutations
     * with server acknowledgements during the sync cycle.
     *
     * @return the mutation ID
     */
    public String mutationId() {
        return mutationId;
    }

    /**
     * Returns the mutation to be synced.
     *
     * @return the trusted decrypted mutation
     */
    public DecryptedMutation.Trusted mutation() {
        return mutation;
    }

    /**
     * Returns the number of sync attempts made for this mutation.
     *
     * @return the attempt count
     */
    public int attemptCount() {
        return attemptCount;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof SyncPendingMutation that
                            && attemptCount == that.attemptCount
                            && Objects.equals(mutationId, that.mutationId)
                            && Objects.equals(mutation, that.mutation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mutationId, mutation, attemptCount);
    }

    @Override
    public String toString() {
        return "SyncPendingMutation[" +
               "mutationId=" + mutationId + ", " +
               "mutation=" + mutation + ", " +
               "attemptCount=" + attemptCount + ']';
    }
}