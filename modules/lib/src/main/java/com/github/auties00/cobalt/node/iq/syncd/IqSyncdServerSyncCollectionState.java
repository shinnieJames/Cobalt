package com.github.auties00.cobalt.node.iq.syncd;

/**
 * Enumerates the per-collection sync outcomes derived from the inbound
 * {@code <collection/>} wire shape returned by the syncd relay.
 *
 * @apiNote
 * Switch on the value to decide what to do next for one collection in a syncd
 * server-sync IQ response: the {@code SUCCESS*} arms apply the returned patches /
 * snapshot, the {@code CONFLICT*} arms reconcile against the relay's snapshot, and
 * the {@code ERROR_*} arms either drop the collection or schedule a retry. The
 * {@code *_HAS_MORE} suffix indicates that the relay still has additional patches
 * queued past the cursor it served and the caller must issue a follow-up sync to
 * drain them.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WASyncdConst.CollectionState} mirrored
 * enum verbatim, except Cobalt collapses the {@code Blocked} state onto
 * {@link #ERROR_FATAL} (WA Web's {@code Blocked} encodes a quota-limit-rejection
 * which the relay produces only when the caller's session has hit a server-side
 * rate cap; Cobalt treats this as a non-retryable fatal error).
 */
public enum IqSyncdServerSyncCollectionState {
    /**
     * Indicates that the collection synced cleanly with no further patches queued.
     *
     * @apiNote
     * The caller applies any returned patches / snapshot and stops the per-collection
     * loop for this iteration.
     */
    SUCCESS,

    /**
     * Indicates that the collection synced cleanly but the relay has additional
     * patches queued past the served cursor.
     *
     * @apiNote
     * The caller must issue a follow-up sync for this collection to drain the
     * remaining patches; WA Web's {@code serverSync} caps the per-call iteration
     * count at five (or 500 when the inbound queue is non-empty).
     */
    SUCCESS_HAS_MORE,

    /**
     * Indicates that the relay rejected the local patches because the collection has
     * diverged.
     *
     * @apiNote
     * The caller reconciles against the relay's snapshot, regenerates the local
     * mutations, and re-uploads. WA Web's flow drops the collection from the active
     * list when there are no pending mutations left to retry and marks it
     * {@link #SUCCESS} instead.
     */
    CONFLICT,

    /**
     * Indicates the {@link #CONFLICT} outcome with additional patches queued past
     * the served cursor.
     *
     * @apiNote
     * The caller reconciles and then issues a follow-up sync to drain the queued
     * patches.
     */
    CONFLICT_HAS_MORE,

    /**
     * Indicates that the relay rejected the collection with a fatal error
     * ({@code 400}, {@code 404} or {@code 405}).
     *
     * @apiNote
     * The caller stops the per-collection loop without retrying; WA Web increments
     * a {@code SyncdFatalErrorType} WAM counter and surfaces the error to the
     * outer bootstrap pipeline.
     */
    ERROR_FATAL,

    /**
     * Indicates that the relay rejected the collection with a transient error.
     *
     * @apiNote
     * The caller may retry after the optional server backoff hint; WA Web routes
     * the collection back into the next sync iteration with the same active
     * cursor.
     */
    ERROR_RETRY
}
