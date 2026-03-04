package com.github.auties00.cobalt.model.sync;

/**
 * Represents the outcome of a conflict resolution between a local pending
 * mutation and an incoming remote mutation with the same index.
 *
 * <p>Per WhatsApp Web {@code WASyncdConst.ConflictResolutionState}, each
 * handler can return one of three outcomes when resolving a conflict.
 */
public enum ConflictResolutionState {
    /**
     * Apply the remote mutation and drop the local pending mutation.
     */
    APPLY_REMOTE_DROP_LOCAL,

    /**
     * Skip the remote mutation and keep the local pending mutation.
     */
    SKIP_REMOTE,

    /**
     * Skip the remote mutation and drop the local pending mutation.
     *
     * <p>Used when the handler has already applied a merged result directly
     * during conflict resolution (e.g. for message-range-based actions).
     */
    SKIP_REMOTE_DROP_LOCAL
}
