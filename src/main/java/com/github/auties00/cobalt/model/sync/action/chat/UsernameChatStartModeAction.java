package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.UsernameChatStartModeAction")
public final class UsernameChatStartModeAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "usernameChatStartMode";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

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


    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    ChatStartMode chatStartMode;


    UsernameChatStartModeAction(ChatStartMode chatStartMode) {
        this.chatStartMode = chatStartMode;
    }

    public Optional<ChatStartMode> chatStartMode() {
        return Optional.ofNullable(chatStartMode);
    }

    public void setChatStartMode(ChatStartMode chatStartMode) {
        this.chatStartMode = chatStartMode;
    }

    @ProtobufEnum(name = "SyncActionValue.UsernameChatStartModeAction.ChatStartMode")
    public static enum ChatStartMode {
        LID(1),
        PN(2);

        ChatStartMode(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
