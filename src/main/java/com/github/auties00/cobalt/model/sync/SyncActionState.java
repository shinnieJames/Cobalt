package com.github.auties00.cobalt.model.sync;

/**
 * Represents the processing state of a sync action mutation.
 *
 * <p>Per WhatsApp Web, every sync action mutation is tracked with a state
 * indicating how it was processed. This enables proper auditing, orphan
 * management, and re-processing of mutations that could not be applied
 * during their initial sync round.
 */
public enum SyncActionState {
    /**
     * The mutation was successfully applied to local state.
     */
    SUCCESS,

    /**
     * The mutation references an entity that does not yet exist locally.
     *
     * <p>Orphaned mutations are persisted and retried when the referenced
     * entity arrives (e.g., via history sync or new message receipt).
     */
    ORPHAN,

    /**
     * The mutation's action type has no registered handler.
     *
     * <p>Unsupported mutations are persisted for future compatibility
     * when a handler may be added for the action type.
     */
    UNSUPPORTED,

    /**
     * The mutation's protobuf data could not be decoded or validated.
     */
    MALFORMED,

    /**
     * The mutation was intentionally skipped (e.g., version-gated).
     */
    SKIPPED,

    /**
     * The mutation handler encountered an error during application.
     */
    FAILED
}
