package com.github.auties00.cobalt.model.message.payment;

import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.payment.Money;
import com.github.auties00.cobalt.model.payment.PaymentBackground;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.RequestPaymentMessage")
public final class RequestPaymentMessage implements Message {
    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    MessageContainer noteMessageContainer;

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String currencyCodeIso4217;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    Long amount1000;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String requestFrom;

    @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant expiryTimestamp;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    Money amount;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    PaymentBackground background;


    RequestPaymentMessage(MessageContainer noteMessageContainer, String currencyCodeIso4217, Long amount1000, String requestFrom, Instant expiryTimestamp, Money amount, PaymentBackground background) {
        this.noteMessageContainer = noteMessageContainer;
        this.currencyCodeIso4217 = currencyCodeIso4217;
        this.amount1000 = amount1000;
        this.requestFrom = requestFrom;
        this.expiryTimestamp = expiryTimestamp;
        this.amount = amount;
        this.background = background;
    }

    public Optional<MessageContainer> noteMessage() {
        return Optional.ofNullable(noteMessageContainer);
    }

    public Optional<String> currencyCodeIso4217() {
        return Optional.ofNullable(currencyCodeIso4217);
    }

    public OptionalLong amount1000() {
        return amount1000 == null ? OptionalLong.empty() : OptionalLong.of(amount1000);
    }

    public Optional<String> requestFrom() {
        return Optional.ofNullable(requestFrom);
    }

    public Optional<Instant> expiryTimestamp() {
        return Optional.ofNullable(expiryTimestamp);
    }

    public Optional<Money> amount() {
        return Optional.ofNullable(amount);
    }

    public Optional<PaymentBackground> background() {
        return Optional.ofNullable(background);
    }

    public void setNoteMessage(MessageContainer noteMessageContainer) {
        this.noteMessageContainer = noteMessageContainer;
    }

    public void setCurrencyCodeIso4217(String currencyCodeIso4217) {
        this.currencyCodeIso4217 = currencyCodeIso4217;
    }

    public void setAmount1000(Long amount1000) {
        this.amount1000 = amount1000;
    }

    public void setRequestFrom(String requestFrom) {
        this.requestFrom = requestFrom;
    }

    public void setExpiryTimestamp(Instant expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    public void setAmount(Money amount) {
        this.amount = amount;
    }

    public void setBackground(PaymentBackground background) {
        this.background = background;
    }
}
