package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.RequestWelcomeMessageMetadata")
public final class RequestWelcomeMessageMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    LocalChatState localChatState;


    RequestWelcomeMessageMetadata(LocalChatState localChatState) {
        this.localChatState = localChatState;
    }

    public Optional<LocalChatState> localChatState() {
        return Optional.ofNullable(localChatState);
    }

    public void setLocalChatState(LocalChatState localChatState) {
        this.localChatState = localChatState;
    }

    @ProtobufEnum(name = "Message.RequestWelcomeMessageMetadata.LocalChatState")
    public static enum LocalChatState {
        EMPTY(0),
        NON_EMPTY(1);

        LocalChatState(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
