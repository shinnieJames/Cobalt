package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.ChatAssignmentOpenedStatusAction")
public final class ChatAssignmentOpenedStatusAction implements SyncAction<ChatAssignmentOpenedStatusActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "agentChatAssignmentOpenedStatus";

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


    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean chatOpened;


    ChatAssignmentOpenedStatusAction(Boolean chatOpened) {
        this.chatOpened = chatOpened;
    }

    public boolean chatOpened() {
        return chatOpened != null && chatOpened;
    }

    public ChatAssignmentOpenedStatusAction setChatOpened(Boolean chatOpened) {
        this.chatOpened = chatOpened;
        return this;
    }


}
