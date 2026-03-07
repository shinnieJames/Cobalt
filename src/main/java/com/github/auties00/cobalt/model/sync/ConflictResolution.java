package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Represents the result of a conflict resolution between a local pending
 * mutation and an incoming remote mutation with the same index.
 *
 * <p>Per WhatsApp Web, some handlers (e.g., archive, clear chat) may
 * produce a merged mutation when neither range fully encloses the other.
 * In that case, the merged mutation replaces the original local pending
 * mutation and is applied to local state instead of the remote.
 *
 * @param state           the resolution state indicating which mutation to keep
 * @param mergedMutation  an optional merged mutation to apply and add to pending,
 *                        only present when the handler merges two non-enclosing
 *                        ranges and returns {@code SKIP_REMOTE_DROP_LOCAL}
 */
public record ConflictResolution(
        ConflictResolutionState state,
        DecryptedMutation.Trusted mergedMutation
) {
    /**
     * Creates a resolution with no merged mutation.
     *
     * @param state the resolution state
     * @return a new conflict resolution
     */
    public static ConflictResolution of(ConflictResolutionState state) {
        return new ConflictResolution(state, null);
    }

    /**
     * Creates a resolution that merges the local and remote mutations.
     *
     * <p>Per WhatsApp Web, the merged mutation replaces the old local
     * pending mutation and is applied to local state. Both the original
     * local and remote mutations are dropped.
     *
     * @param merged the merged mutation to apply and add to pending
     * @return a new conflict resolution with {@code SKIP_REMOTE_DROP_LOCAL} state
     */
    public static ConflictResolution merged(DecryptedMutation.Trusted merged) {
        return new ConflictResolution(ConflictResolutionState.SKIP_REMOTE_DROP_LOCAL, merged);
    }
}
