package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.business.AgentStateBuilder;
import com.github.auties00.cobalt.model.sync.action.device.AgentAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles agent sync actions for managing business account agents (device agents).
 *
 * <p>This handler processes mutations that manage agent/bot assignments.
 * Per WhatsApp Web, the handler belongs to the {@code Regular} collection,
 * uses version {@code 7}, and routes on action name {@code "deviceAgent"}.
 *
 * <p>Index format: {@code ["deviceAgent", agentId]}
 *
 * <p>On {@code SET}, the handler validates that {@code indexParts[1]} (the agentId)
 * is present and that the protobuf {@code agentAction} field is non-null, then
 * merges the agent into the store (even when {@code isDeleted} is {@code true}).
 * On {@code REMOVE}, only the agentId is validated, and the agent is removed
 * from the store.
 */
@WhatsAppWebModule(moduleName = "WAWebAgentSync")
public final class AgentActionHandler implements WebAppStateActionHandler {

    /**
     * Creates the singleton agent action handler.
     *
     * <p>Per WhatsApp Web, the constructor of class {@code u} extends
     * {@code AccountSyncdActionBase} and sets
     * {@code this.collectionName = WASyncdConst.CollectionName.Regular}. The
     * {@code collectionName} assignment is surfaced in Cobalt via
     * {@link #collectionName()} rather than as an instance field.
     */
    @WhatsAppWebExport(moduleName = "WAWebAgentSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AgentActionHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAgentSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return AgentAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAgentSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return AgentAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAgentSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return AgentAction.ACTION_VERSION;
    }

    /**
     * Applies an agent mutation and returns the detailed result.
     *
     * <p>Per WhatsApp Web, the handler validates the mutation content via
     * {@code getValidatedContentSet} / {@code getValidatedContentRemove}, then:
     * <ul>
     *   <li>For {@code REMOVE}: removes the agent from the store by agentId</li>
     *   <li>For {@code SET}: merges the agent into the store with all its fields
     *       (including {@code isDeleted}), regardless of the deleted flag</li>
     * </ul>
     *
     * <p>After all mutations in a batch, WA Web also reconciles unattributed
     * messages with the agent collection via a post-processing step that
     * iterates {@code UnattributedMessageCollection.getModelsArray()}, looks up
     * each message, and if its {@code deviceId} maps to an agent, sets
     * {@code msg.agentId} and removes the message from the unattributed
     * collection. That reconciliation is intentionally omitted in Cobalt
     * because unattributed message tracking is not implemented.
     *
     * <p>Additionally, WA Web formats the display name via
     * {@code WAWebAgentModelUtils.getFormattedAgentName} (which returns a
     * localized {@code "{business-name} (Admin)"} string when {@code deviceId}
     * is the primary device id {@code 0}). Cobalt stores the raw
     * {@link AgentAction} protobuf with its original {@code name()} and
     * {@code deviceID()} accessors, deferring display formatting to UI layers.
     * @param client   the WhatsApp client
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAgentSync", exports = {"applyMutations", "getValidatedContentSet", "getValidatedContentRemove"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        // WAWebAgentSync.getValidatedContentSet/Remove: var t=e.indexParts, n=t[1]; if(!n) return {result:"malformed_index"}
        // Out-of-bounds in JS yields undefined which is falsy; mirror via explicit size check.
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var agentId = indexArray.getString(1);
        if (agentId == null || agentId.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        if (mutation.operation() == SyncdOperation.REMOVE) {
            client.store().removeAgentState(agentId); // ADAPTED: WAWebAgentCollection/WAWebSchemaAgent — Cobalt uses typed store quintet
            return MutationApplicationResult.success();
        }

        if (mutation.operation() != SyncdOperation.SET) { // NO_WA_BASIS — defensive guard for unknown operation types
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof AgentAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().putAgentState(new AgentStateBuilder()
                .agentId(agentId)
                .name(action.name().orElse(null))
                .deviceId(action.deviceID().isPresent() ? action.deviceID().getAsInt() : null)
                .deleted(action.isDeleted())
                .build());
        return MutationApplicationResult.success();
    }
}
