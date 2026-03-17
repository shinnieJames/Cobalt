package com.github.auties00.cobalt.model.message.payment;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.CancelPaymentRequestMessage")
public final class CancelPaymentRequestMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;


    CancelPaymentRequestMessage(MessageKey key) {
        this.key = key;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }
}
