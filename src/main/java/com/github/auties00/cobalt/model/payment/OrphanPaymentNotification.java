package com.github.auties00.cobalt.model.payment;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

@ProtobufMessage
public final class OrphanPaymentNotification {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String messageId;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    Jid receiverJid;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String currency;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64)
    Long amount1000;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String transactionType;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String status;

    @ProtobufProperty(index = 7, type = ProtobufType.INT64)
    Long transactionTimestamp;

    OrphanPaymentNotification(String messageId, Jid receiverJid, String currency, Long amount1000, String transactionType, String status, Long transactionTimestamp) {
        this.messageId = Objects.requireNonNull(messageId, "messageId cannot be null");
        this.receiverJid = receiverJid;
        this.currency = currency;
        this.amount1000 = amount1000;
        this.transactionType = transactionType;
        this.status = status;
        this.transactionTimestamp = transactionTimestamp;
    }

    public String messageId() {
        return messageId;
    }

    public Optional<Jid> receiverJid() {
        return Optional.ofNullable(receiverJid);
    }

    public Optional<String> currency() {
        return Optional.ofNullable(currency);
    }

    public Optional<Long> amount1000() {
        return Optional.ofNullable(amount1000);
    }

    public Optional<String> transactionType() {
        return Optional.ofNullable(transactionType);
    }

    public Optional<String> status() {
        return Optional.ofNullable(status);
    }

    public Optional<Long> transactionTimestamp() {
        return Optional.ofNullable(transactionTimestamp);
    }

    public void setReceiverJid(Jid receiverJid) {
        this.receiverJid = receiverJid;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setAmount1000(Long amount1000) {
        this.amount1000 = amount1000;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTransactionTimestamp(Long transactionTimestamp) {
        this.transactionTimestamp = transactionTimestamp;
    }
}
