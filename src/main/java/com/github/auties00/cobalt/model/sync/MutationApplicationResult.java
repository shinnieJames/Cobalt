package com.github.auties00.cobalt.model.sync;

/**
 * Represents the detailed outcome of applying a sync mutation.
 *
 * <p>This mirrors WhatsApp Web's mutation result categories closely enough to
 * preserve static parity in the local processing pipeline even when the rest
 * of the codebase still uses simplified handlers.
 *
 * @param actionState the processing outcome
 * @param modelId the targeted entity id for orphan outcomes, if any
 * @param modelType the targeted entity type for orphan outcomes, if any
 */
public record MutationApplicationResult(
        SyncActionState actionState,
        String modelId,
        String modelType
) {
    public static MutationApplicationResult success() {
        return new MutationApplicationResult(SyncActionState.SUCCESS, null, null);
    }

    public static MutationApplicationResult unsupported() {
        return new MutationApplicationResult(SyncActionState.UNSUPPORTED, null, null);
    }

    public static MutationApplicationResult malformed() {
        return new MutationApplicationResult(SyncActionState.MALFORMED, null, null);
    }

    public static MutationApplicationResult skipped() {
        return new MutationApplicationResult(SyncActionState.SKIPPED, null, null);
    }

    public static MutationApplicationResult failed() {
        return new MutationApplicationResult(SyncActionState.FAILED, null, null);
    }

    public static MutationApplicationResult orphan() {
        return new MutationApplicationResult(SyncActionState.ORPHAN, null, null);
    }

    public static MutationApplicationResult orphan(String modelId, String modelType) {
        return new MutationApplicationResult(SyncActionState.ORPHAN, modelId, modelType);
    }

    public boolean isOrphan() {
        return actionState == SyncActionState.ORPHAN;
    }
}
