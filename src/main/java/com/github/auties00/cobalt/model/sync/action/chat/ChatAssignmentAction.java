package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.ChatAssignmentAction")
public final class ChatAssignmentAction implements SyncAction<ChatAssignmentActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "agentChatAssignment";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String deviceAgentID;


    ChatAssignmentAction(String deviceAgentID) {
        this.deviceAgentID = deviceAgentID;
    }

    public Optional<String> deviceAgentID() {
        return Optional.ofNullable(deviceAgentID);
    }

    public ChatAssignmentAction setDeviceAgentID(String deviceAgentID) {
        this.deviceAgentID = deviceAgentID;
        return this;
    }


}
