package com.github.auties00.cobalt.model.message.group;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.SenderKeyDistributionMessage")
public final class SenderKeyDistributionMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String groupId;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] axolotlSenderKeyDistributionMessage;


    SenderKeyDistributionMessage(String groupId, byte[] axolotlSenderKeyDistributionMessage) {
        this.groupId = groupId;
        this.axolotlSenderKeyDistributionMessage = axolotlSenderKeyDistributionMessage;
    }

    public Optional<String> groupId() {
        return Optional.ofNullable(groupId);
    }

    public Optional<byte[]> axolotlSenderKeyDistributionMessage() {
        return Optional.ofNullable(axolotlSenderKeyDistributionMessage);
    }

    public SenderKeyDistributionMessage setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public SenderKeyDistributionMessage setAxolotlSenderKeyDistributionMessage(byte[] axolotlSenderKeyDistributionMessage) {
        this.axolotlSenderKeyDistributionMessage = axolotlSenderKeyDistributionMessage;
        return this;
    }
}
