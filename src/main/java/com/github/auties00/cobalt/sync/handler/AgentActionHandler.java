package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.AgentAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles agent actions.
 *
 * <p>This handler processes mutations that manage business account agents
 * (customer service representatives).
 *
 * <p>Index format: ["agentAction", agentId]
 */
public final class AgentActionHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code AgentActionHandler}.
     */
    public static final AgentActionHandler INSTANCE = new AgentActionHandler();

    private AgentActionHandler() {

    }

    @Override
    public String actionName() {
        return AgentAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return AgentAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return AgentAction.ACTION_VERSION;
    }

    /**
     * Applies an agent mutation.
     *
     * <p>Per WhatsApp Web (WAWebAgentSync), on SET the web client validates that
     * the agentId from index[1] is present and that the action value (agentAction)
     * is non-null. On REMOVE, only the agentId from index[1] is validated.
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was acknowledged, {@code false} otherwise
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var agentId = indexArray.getString(1);
        if (agentId == null || agentId.isEmpty()) {
            return true;
        }

        if (mutation.operation() == SyncdOperation.SET) {
            if (!(mutation.value().action().orElse(null) instanceof AgentAction)) {
                return true;
            }
        }

        return true;
    }
}
