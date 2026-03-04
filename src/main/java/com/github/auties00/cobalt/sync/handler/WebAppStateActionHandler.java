package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Interface for handling specific action types in app state sync.
 *
 * <p>Each implementation handles a specific type of mutation (e.g., archive, pin, mute)
 * and is responsible for:
 * <ul>
 *   <li>Applying mutations to local state</li>
 *   <li>Resolving conflicts between local and remote mutations</li>
 *   <li>Handling orphan cases when referenced entities don't exist</li>
 * </ul>
 */
public interface WebAppStateActionHandler {
    /**
     * Gets the action type name this handler processes.
     *
     * @return the action type name
     */
    String actionName();

    /**
     * Applies mutation to local state.
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was applied successfully, {@code false} otherwise
     */
    boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation);

    /**
     * Resolves a conflict between a local pending mutation and an incoming
     * remote mutation with the same index.
     *
     * <p>The default implementation uses timestamp comparison: the mutation
     * with the later (or equal) timestamp wins. Subclasses can override this
     * to implement specialized logic (e.g., message-range merging).
     *
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the resolution state indicating which mutation to keep
     */
    default ConflictResolutionState resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        if (remoteMutation.timestamp().compareTo(localMutation.timestamp()) >= 0) {
            return ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL;
        } else {
            return ConflictResolutionState.SKIP_REMOTE;
        }
    }
}
