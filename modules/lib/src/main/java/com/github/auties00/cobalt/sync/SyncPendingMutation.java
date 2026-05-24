package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.Objects;
import java.util.UUID;

/**
 * One row of the local pending-mutation queue: a decrypted sync action that
 * has been produced by Cobalt and is waiting to be uploaded on the next
 * syncd round-trip.
 *
 * @apiNote Mirrors the shape carried by WhatsApp Web's
 * {@code WAWebPendingMutationStore} IndexedDB rows
 * ({@code WAWebSchemaPendingMutations.convertToPendingMutationFromRow})
 * with one Cobalt-specific addition: an {@code attemptCount} field that
 * lets the syncd retry loop count attempts per row without consulting the
 * collection-level {@code WAWebSyncdCollectionsStateMachine}. End users
 * never construct these directly; they are produced by the per-handler
 * {@code <Name>MutationFactory} classes and persisted by
 * {@link WebAppStateService}.
 *
 * @implNote This implementation has no IndexedDB-backed primary-key column;
 * instead the {@code mutationId} field is a {@link UUID#randomUUID()}
 * string created at construction time, which serves the same role for
 * correlating an upload IQ stanza with the server-side acknowledgement
 * that follows.
 */
@WhatsAppWebModule(moduleName = "WAWebPendingMutationStore")
public final class SyncPendingMutation {
    /**
     * The opaque identifier used to correlate this row with its server
     * acknowledgement.
     */
    private final String mutationId;

    /**
     * The decrypted, trusted sync action waiting to be uploaded.
     */
    private final DecryptedMutation.Trusted mutation;

    /**
     * The number of upload attempts already made for this row.
     */
    private final int attemptCount;

    /**
     * Builds a fresh pending-mutation row with a generated identifier and
     * the given attempt count.
     *
     * @apiNote Called from the {@code *MutationFactory} classes when a new
     * outgoing mutation is enqueued for the next sync round-trip; the
     * generated id is what {@link WebAppStateService} later uses to
     * correlate the server acknowledgement.
     *
     * @param mutation     the trusted decrypted mutation to enqueue
     * @param attemptCount the initial attempt count, typically {@code 0}
     *                     for a freshly enqueued row
     */
    public SyncPendingMutation(DecryptedMutation.Trusted mutation, int attemptCount) {
        this(UUID.randomUUID().toString(), mutation, attemptCount);
    }

    /**
     * Builds a pending-mutation row with an explicit identifier.
     *
     * @apiNote Used by {@link #incrementAttempt()} to preserve the original
     * identifier across retry attempts and by tests that need a
     * deterministic id.
     *
     * @param mutationId   the identifier to bind to the new row
     * @param mutation     the trusted decrypted mutation to enqueue
     * @param attemptCount the initial attempt count for the new row
     */
    public SyncPendingMutation(String mutationId, DecryptedMutation.Trusted mutation, int attemptCount) {
        this.mutationId = mutationId;
        this.mutation = mutation;
        this.attemptCount = attemptCount;
    }

    /**
     * Returns a new row that carries the same identifier and mutation as
     * this one but with {@code attemptCount + 1}.
     *
     * @apiNote Called by the syncd retry path after a failed upload, before
     * re-enqueuing the row for the next attempt. The original instance is
     * left untouched, matching the immutable-record style of the rest of
     * the sync package.
     *
     * @return a new {@link SyncPendingMutation} instance with the bumped
     *         {@link #attemptCount()}
     */
    public SyncPendingMutation incrementAttempt() {
        return new SyncPendingMutation(mutationId, mutation, attemptCount + 1);
    }

    /**
     * Returns the opaque identifier bound to this row at construction
     * time.
     *
     * @apiNote The id is the only stable handle through which the upload
     * IQ can be correlated with the server acknowledgement that closes
     * the round-trip; downstream consumers like
     * {@code WAWebSyncdRequestBuilderBuild._generateMutationsToUpload}
     * key their per-mutation bookkeeping on this value.
     *
     * @return the identifier supplied at construction
     */
    public String mutationId() {
        return mutationId;
    }

    /**
     * Returns the trusted decrypted mutation wrapped by this row.
     *
     * @apiNote Used by the upload path to read the underlying
     * {@link DecryptedMutation.Trusted} payload that will be re-encrypted
     * and serialised into the syncd patch IQ.
     *
     * @return the wrapped {@link DecryptedMutation.Trusted}
     */
    public DecryptedMutation.Trusted mutation() {
        return mutation;
    }

    /**
     * Returns the number of upload attempts that have already been made
     * for this row.
     *
     * @apiNote Read by the syncd retry loop to gate per-row backoff
     * decisions independently of the collection-level state machine.
     *
     * @return the current attempt count, monotonically increased by
     *         {@link #incrementAttempt()}
     */
    public int attemptCount() {
        return attemptCount;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation compares every field
     * ({@link #mutationId()}, {@link #mutation()},
     * {@link #attemptCount()}) so that two rows with the same payload but
     * different identifiers, or the same identifier but different attempt
     * counts, are considered distinct.
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof SyncPendingMutation that
                            && attemptCount == that.attemptCount
                            && Objects.equals(mutationId, that.mutationId)
                            && Objects.equals(mutation, that.mutation);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation hashes the same triple of fields as
     * {@link #equals(Object)} to keep the contract consistent.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mutationId, mutation, attemptCount);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation emits a single-line, comma-separated
     * record-style rendering with every field labeled, intended for log
     * lines rather than user-facing display.
     */
    @Override
    public String toString() {
        return "SyncPendingMutation[" +
               "mutationId=" + mutationId + ", " +
               "mutation=" + mutation + ", " +
               "attemptCount=" + attemptCount + ']';
    }
}
