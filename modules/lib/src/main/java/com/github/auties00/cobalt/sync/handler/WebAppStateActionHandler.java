package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.sync.ConflictResolution;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>Per WhatsApp Web, each handler declares:
 * <ul>
 *   <li>{@link #actionName()} — the action identifier used for routing</li>
 *   <li>{@link #collectionName()} — the sync collection this action belongs to</li>
 *   <li>{@link #version()} — the mutation format version for version gating</li>
 * </ul>
 *
 * <p>This interface flattens WA Web's five-class category hierarchy
 * ({@code AccountSyncdActionBase}, {@code ChatSyncdActionBase},
 * {@code ChatOrContactSyncdActionBase}, {@code MessageSyncdActionBase},
 * {@code ChatMessageRangeSyncdActionBase}) plus their shared prototype root
 * into a single interface, mirroring the Cobalt store flattening design.
 * The {@code is*SyncdAction()} / {@code as*SyncdActionHandler()} discrimination
 * pattern from WA Web is intentionally dropped because Cobalt handlers are
 * dispatched directly by {@link #actionName()} without category casts.
 *
 * @implNote WAWebSyncdAction — the shared prototype root plus its five base
 *           subclasses ({@code AccountSyncdActionBase}, {@code ChatSyncdActionBase},
 *           {@code ChatOrContactSyncdActionBase}, {@code MessageSyncdActionBase},
 *           {@code ChatMessageRangeSyncdActionBase}) are flattened into this
 *           single interface. Concrete {@code WAWeb*Sync} handlers extend the
 *           appropriate base with {@code getAction()}, {@code getVersion()},
 *           {@code collectionName}, and {@code applyMutations()}
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdAction")
public interface WebAppStateActionHandler {
    /**
     * Gets the action type name this handler processes.
     *
     * @implNote WAWeb*Sync.getAction — returns the {@code WASyncdConst.Actions} constant
     *           for this handler (e.g., {@code "archive"}, {@code "pin_v1"})
     * @return the action type name
     */
    String actionName();

    /**
     * Returns the sync collection this handler's action belongs to.
     *
     * <p>Per WhatsApp Web, each handler declares which collection its mutations
     * are stored in (e.g., {@code REGULAR}, {@code CRITICAL_BLOCK}).
     *
     * @implNote WAWeb*Sync.collectionName — set in constructor from
     *           {@code WASyncdConst.CollectionName} (e.g., {@code RegularLow}, {@code CriticalBlock})
     * @return the sync patch type / collection name
     */
    SyncPatchType collectionName();

    /**
     * Returns the mutation format version for this handler.
     *
     * <p>Per WhatsApp Web, each handler declares a version number used for
     * version gating. Mutations with a version higher than this value are
     * skipped to avoid processing with incompatible logic.
     *
     * @implNote WAWeb*Sync.getVersion — returns the version constant for this handler
     * @return the handler's supported mutation version
     */
    int version();

    /**
     * Returns a malformed result for an invalid action index.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdAction.malformedActionIndex}: the base
     * class method delegates to {@code WAWebSyncdIndexUtils.malformedActionIndex}
     * passing the handler's {@code collectionName} and {@code getAction()} values.
     * The utility uploads a WAM critical event metric and returns
     * {@code {actionState: Malformed}}.
     *
     * <p>In Cobalt, WAM telemetry is intentionally omitted, but the return value
     * semantics are preserved.
     *
     * @implNote WAWebSyncdAction.malformedActionIndex, WAWebSyncdIndexUtils.malformedActionIndex
     * @return a {@link MutationApplicationResult} with {@code MALFORMED} state
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "AccountSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatOrContactSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "MessageSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatMessageRangeSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    default MutationApplicationResult malformedActionIndex() {
        return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName()); // WAWebSyncdAction.malformedActionIndex -> WAWebSyncdIndexUtils.malformedActionIndex(e.collectionName, e.getAction())
    }

    /**
     * Returns a malformed result for an invalid action value.
     *
     * <p>Per WhatsApp Web, individual handlers call
     * {@code WAWebSyncdIndexUtils.malformedActionValue(collectionName)} directly
     * when the sync action value cannot be decoded or validated.
     *
     * <p>In Cobalt, this method provides a convenient default that passes the
     * handler's {@code collectionName} to the underlying utility.
     *
     * @implNote WAWebSyncdIndexUtils.malformedActionValue
     * @return a {@link MutationApplicationResult} with {@code MALFORMED} state
     */
    default MutationApplicationResult malformedActionValue() {
        return SyncdIndexUtils.malformedActionValue(collectionName().name()); // WAWebSyncdIndexUtils.malformedActionValue(a.collectionName)
    }

    /**
     * Applies mutation to local state.
     *
     * @implNote WAWeb*Sync.applyMutations — per-mutation application logic within
     *           the batch handler. Returns {@code true} on success, {@code false} on orphan.
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was applied successfully, {@code false} otherwise
     */
    boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation);

    /**
     * Applies a batch of mutations to local state.
     *
     * <p>Per WhatsApp Web, each handler receives the full batch of mutations
     * for its action type via {@code applyMutations(mutations, options)}.
     * Some handlers (e.g., favorites, primary feature, archive setting) apply
     * only the mutation with the latest timestamp rather than processing each
     * mutation sequentially.
     *
     * <p>The default implementation processes mutations one by one via
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}.
     * Handlers that need batch-level deduplication should override this method.
     *
     * @implNote WAWeb*Sync.applyMutations — the main entry point for batch mutation
     *           application in each handler
     * @param client    the WhatsAppClient instance linked to the mutations
     * @param mutations the batch of mutations to apply (already version-gated)
     * @return a list of results parallel to the input, where {@code true}
     *         means applied/acknowledged and {@code false} means orphan
     */
    default List<Boolean> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        var results = new ArrayList<Boolean>(mutations.size());
        for (var mutation : mutations) {
            results.add(applyMutation(client, mutation));
        }
        return results;
    }

    /**
     * Applies a single mutation and returns a richer WA-style outcome.
     *
     * <p>The default implementation preserves legacy behavior where {@code true}
     * is treated as success and {@code false} is treated as orphan.
     *
     * @implNote ADAPTED: WAWeb*Sync.applyMutations — WA Web returns
     *           {@code WASyncdConst.SyncActionState} values directly; Cobalt wraps them
     *           in {@link MutationApplicationResult} for type safety
     * @param client the WhatsApp client
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    default MutationApplicationResult applyMutationResult(
            WhatsAppClient client,
            DecryptedMutation.Trusted mutation
    ) {
        return applyMutation(client, mutation)
                ? MutationApplicationResult.success()
                : MutationApplicationResult.orphan();
    }

    /**
     * Applies a batch of mutations and returns richer WA-style outcomes.
     *
     * <p>The default implementation preserves legacy behavior where {@code true}
     * is treated as success and {@code false} is treated as orphan.
     *
     * @implNote ADAPTED: WAWeb*Sync.applyMutations — WA Web returns per-mutation
     *           {@code WASyncdConst.SyncActionState} values; Cobalt wraps them in
     *           {@link MutationApplicationResult} for type safety
     * @param client the WhatsApp client
     * @param mutations the mutations to apply
     * @return the detailed application results
     */
    default List<MutationApplicationResult> applyMutationBatchResults(
            WhatsAppClient client,
            List<DecryptedMutation.Trusted> mutations
    ) {
        var legacy = applyMutationBatch(client, mutations);
        var results = new ArrayList<MutationApplicationResult>(legacy.size());
        for (var applied : legacy) {
            results.add(applied
                    ? MutationApplicationResult.success()
                    : MutationApplicationResult.orphan());
        }
        return results;
    }

    /**
     * Resolves a conflict between a local pending mutation and an incoming
     * remote mutation with the same index.
     *
     * <p>The default implementation uses timestamp comparison: the mutation
     * with the later (or equal) timestamp wins. Subclasses can override this
     * to implement specialized logic (e.g., message-range merging).
     *
     * @implNote WAWebSyncdAction.resolveConflicts — default timestamp-based
     *           resolution; some handlers override with message-range merging
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep and
     *         optionally a merged mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "AccountSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatOrContactSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "MessageSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatMessageRangeSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    default ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        if (remoteMutation.timestamp().compareTo(localMutation.timestamp()) >= 0) {
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);
        } else {
            return ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE);
        }
    }

    /**
     * Allows a handler to drop a remote mutation when a different pending local
     * mutation makes it obsolete, mirroring WA's cross-index conflict hook.
     *
     * @implNote WAWebSyncdAction.dropMutationDueToCrossIndexConflict — cross-index conflict
     *           check; most handlers return {@code false} (no cross-index conflicts)
     * @param remoteMutation the candidate remote mutation
     * @param pendingByIndex all pending mutations indexed by mutation index
     * @return whether the remote mutation should be dropped
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "AccountSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatOrContactSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "MessageSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatMessageRangeSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    default boolean dropMutationDueToCrossIndexConflict(
            DecryptedMutation.Trusted remoteMutation,
            java.util.Map<String, DecryptedMutation.Trusted> pendingByIndex
    ) {
        return false;
    }

    /**
     * Returns whether the mutation's index targets a LID-namespaced JID.
     *
     * <p>Per WhatsApp Web, the root {@code WAWebSyncdAction} prototype returns
     * {@code false}; {@code ChatSyncdActionBase} overrides this to inspect the
     * mutation index entry at {@code chatJidIndex} and check
     * {@code WAWebWidFactory.createWid(jid).isLid()}. Because Cobalt flattens
     * the WA Web class hierarchy into a single interface, individual chat-scoped
     * handlers may override this default to perform the LID inspection inline.
     *
     * @implNote WAWebSyncdAction.isLidMutation — root returns {@code false};
     *           {@code ChatSyncdActionBase.isLidMutation} reads the JID from
     *           the index slot identified by {@code chatJidIndex} and checks
     *           {@code WAWebWidFactory.createWid(jid).isLid()}
     * @param mutation the mutation to inspect
     * @return {@code true} if the mutation targets a LID JID, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "AccountSyncdActionBase", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatOrContactSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "MessageSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdAction", exports = "ChatMessageRangeSyncdActionBase", adaptation = WhatsAppAdaptation.ADAPTED)
    default boolean isLidMutation(DecryptedMutation.Trusted mutation) {
        return false;
    }
}
