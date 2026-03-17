package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.DeviceSentMessage")
public final class DeviceSentMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid destinationJid;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageContainer messageContainer;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String phash;


    DeviceSentMessage(Jid destinationJid, MessageContainer messageContainer, String phash) {
        this.destinationJid = destinationJid;
        this.messageContainer = messageContainer;
        this.phash = phash;
    }

    public Optional<Jid> destinationJid() {
        return Optional.ofNullable(destinationJid);
    }

    public Optional<MessageContainer> message() {
        return Optional.ofNullable(messageContainer);
    }

    public Optional<String> phash() {
        return Optional.ofNullable(phash);
    }

    public void setDestinationJid(Jid destinationJid) {
        this.destinationJid = destinationJid;
    }

    public void setMessage(MessageContainer messageContainer) {
        this.messageContainer = messageContainer;
    }

    public void setPhash(String phash) {
        this.phash = phash;
    }
}
