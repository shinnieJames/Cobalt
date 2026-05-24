package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * The decision returned by a sync action handler when a remote mutation
 * arrives at an index that the local device already has a pending mutation
 * for.
 *
 * @param state          the {@link ConflictResolutionState} verdict that
 *                       drives the {@code remoteMutationsToApply} and
 *                       {@code pendingSetMutationsToDrop} buckets in
 *                       {@code WAWebSyncdResolveConflict.resolveConflict}
 * @param mergedMutation a non-{@code null} mutation that supersedes both
 *                       sides when the handler combined two non-enclosing
 *                       message ranges, or {@code null} for the
 *                       enum-only verdicts
 */
@WhatsAppWebModule(moduleName = "WAWebSyncActionStore")
public record ConflictResolution(
        ConflictResolutionState state,
        DecryptedMutation.Trusted mergedMutation
) {
    /**
     * Wraps an enum-only verdict that needs no merged mutation.
     *
     * @apiNote Returned by handlers that map straight onto WA Web's three
     * verdict cases in {@code WAWebSyncdResolveConflict.resolveConflict}:
     * {@link ConflictResolutionState#APPLY_REMOTE_DROP_LOCAL},
     * {@link ConflictResolutionState#SKIP_REMOTE}, and the no-merge form
     * of {@link ConflictResolutionState#SKIP_REMOTE_DROP_LOCAL}. Use
     * {@link #merged(DecryptedMutation.Trusted)} when the handler wants
     * to substitute a third mutation in place of the two losing ones.
     *
     * @param state the verdict to record
     * @return a resolution carrying {@code state} and a {@code null}
     *         {@link #mergedMutation()}
     * @see #merged(DecryptedMutation.Trusted)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncActionStore", exports = "doConflictResolution", adaptation = WhatsAppAdaptation.ADAPTED)
    public static ConflictResolution of(ConflictResolutionState state) {
        return new ConflictResolution(state, null);
    }

    /**
     * Wraps a verdict that replaces both sides with a third mutation
     * computed by the handler.
     *
     * @apiNote Used by message-range handlers (archive, mark-as-read,
     * delete-chat, ...) when neither the local pending mutation nor the
     * incoming remote mutation fully encloses the other. The handler
     * unions the two ranges into a single mutation, returns it here, and
     * the caller drops both originals before applying {@code merged} to
     * local state. The verdict is fixed at
     * {@link ConflictResolutionState#SKIP_REMOTE_DROP_LOCAL} because the
     * remote mutation is dropped without being applied as-is.
     *
     * @param merged the merged mutation to apply and add to the pending
     *               queue
     * @return a resolution carrying
     *         {@link ConflictResolutionState#SKIP_REMOTE_DROP_LOCAL} and
     *         the supplied {@code merged} payload
     * @see #of(ConflictResolutionState)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncActionStore", exports = "doConflictResolution", adaptation = WhatsAppAdaptation.ADAPTED)
    public static ConflictResolution merged(DecryptedMutation.Trusted merged) {
        return new ConflictResolution(ConflictResolutionState.SKIP_REMOTE_DROP_LOCAL, merged);
    }
}
