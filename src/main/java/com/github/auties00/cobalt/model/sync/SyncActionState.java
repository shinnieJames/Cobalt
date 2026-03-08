package com.github.auties00.cobalt.model.sync;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * Represents the processing state of a sync action mutation.
 *
 * <p>Per WhatsApp Web, every sync action mutation is tracked with a state
 * indicating how it was processed. This enables proper auditing, orphan
 * management, and re-processing of mutations that could not be applied
 * during their initial sync round.
 */
@ProtobufEnum
public enum SyncActionState {
    /**
     * The mutation was successfully applied to local state.
     */
    SUCCESS(0),

    /**
     * The mutation references an entity that does not yet exist locally.
     *
     * <p>Orphaned mutations are persisted and retried when the referenced
     * entity arrives (e.g., via history sync or new message receipt).
     */
    ORPHAN(1),

    /**
     * The mutation's action type has no registered handler.
     *
     * <p>Unsupported mutations are persisted for future compatibility
     * when a handler may be added for the action type.
     */
    UNSUPPORTED(2),

    /**
     * The mutation's protobuf data could not be decoded or validated.
     */
    MALFORMED(3),

    /**
     * The mutation was intentionally skipped (e.g., version-gated).
     */
    SKIPPED(4),

    /**
     * The mutation handler encountered an error during application.
     */
    FAILED(5);

    final int index;

    SyncActionState(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
