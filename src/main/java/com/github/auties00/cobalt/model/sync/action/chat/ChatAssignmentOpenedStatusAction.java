package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
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
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

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

    public void setChatOpened(Boolean chatOpened) {
        this.chatOpened = chatOpened;
    }


}
