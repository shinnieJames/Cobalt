package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.UsernameChatStartModeAction")
public final class UsernameChatStartModeAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    ChatStartMode chatStartMode;


    UsernameChatStartModeAction(ChatStartMode chatStartMode) {
        this.chatStartMode = chatStartMode;
    }

    public Optional<ChatStartMode> chatStartMode() {
        return Optional.ofNullable(chatStartMode);
    }

    public UsernameChatStartModeAction setChatStartMode(ChatStartMode chatStartMode) {
        this.chatStartMode = chatStartMode;
        return this;
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
