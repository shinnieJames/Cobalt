package com.github.auties00.cobalt.model.message.group;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.SenderKeyDistributionMessage")
public final class SenderKeyDistributionMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid groupJid;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] axolotlSenderKeyDistributionMessage;


    SenderKeyDistributionMessage(Jid groupJid, byte[] axolotlSenderKeyDistributionMessage) {
        this.groupJid = groupJid;
        this.axolotlSenderKeyDistributionMessage = axolotlSenderKeyDistributionMessage;
    }

    public Optional<Jid> groupJid() {
        return Optional.ofNullable(groupJid);
    }

    public Optional<byte[]> axolotlSenderKeyDistributionMessage() {
        return Optional.ofNullable(axolotlSenderKeyDistributionMessage);
    }

    public void setGroupJid(Jid groupJid) {
        this.groupJid = groupJid;
    }

    public void setAxolotlSenderKeyDistributionMessage(byte[] axolotlSenderKeyDistributionMessage) {
        this.axolotlSenderKeyDistributionMessage = axolotlSenderKeyDistributionMessage;
    }
}
