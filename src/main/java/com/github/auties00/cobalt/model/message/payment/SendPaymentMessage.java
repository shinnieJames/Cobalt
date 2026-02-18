package com.github.auties00.cobalt.model.message.payment;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.payment.PaymentBackground;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.SendPaymentMessage")
public final class SendPaymentMessage implements Message {
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageContainer noteMessageContainer;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    MessageKey requestMessageKey;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    PaymentBackground background;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String transactionData;


    SendPaymentMessage(MessageContainer noteMessageContainer, MessageKey requestMessageKey, PaymentBackground background, String transactionData) {
        this.noteMessageContainer = noteMessageContainer;
        this.requestMessageKey = requestMessageKey;
        this.background = background;
        this.transactionData = transactionData;
    }

    public Optional<MessageContainer> noteMessage() {
        return Optional.ofNullable(noteMessageContainer);
    }

    public Optional<MessageKey> requestMessageKey() {
        return Optional.ofNullable(requestMessageKey);
    }

    public Optional<PaymentBackground> background() {
        return Optional.ofNullable(background);
    }

    public Optional<String> transactionData() {
        return Optional.ofNullable(transactionData);
    }

    public SendPaymentMessage setNoteMessage(MessageContainer noteMessageContainer) {
        this.noteMessageContainer = noteMessageContainer;
        return this;
    }

    public SendPaymentMessage setRequestMessageKey(MessageKey requestMessageKey) {
        this.requestMessageKey = requestMessageKey;
        return this;
    }

    public SendPaymentMessage setBackground(PaymentBackground background) {
        this.background = background;
        return this;
    }

    public SendPaymentMessage setTransactionData(String transactionData) {
        this.transactionData = transactionData;
        return this;
    }
}
