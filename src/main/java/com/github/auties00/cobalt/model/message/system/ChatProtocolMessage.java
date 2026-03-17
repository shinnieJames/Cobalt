package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.Chat")
public final class ChatProtocolMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String displayName;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String id;


    ChatProtocolMessage(String displayName, String id) {
        this.displayName = displayName;
        this.id = id;
    }

    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setId(String id) {
        this.id = id;
    }
}
