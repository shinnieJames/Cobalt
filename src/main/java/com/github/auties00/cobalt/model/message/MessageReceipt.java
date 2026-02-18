package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ProtobufMessage(name = "UserReceipt")
public final class MessageReceipt {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid userJid;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant receiptTimestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant readTimestamp;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant playedTimestamp;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    List<Jid> pendingDeviceJid;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    List<Jid> deliveredDeviceJid;


    MessageReceipt(Jid userJid, Instant receiptTimestamp, Instant readTimestamp, Instant playedTimestamp, List<Jid> pendingDeviceJid, List<Jid> deliveredDeviceJid) {
        this.userJid = Objects.requireNonNull(userJid);
        this.receiptTimestamp = receiptTimestamp;
        this.readTimestamp = readTimestamp;
        this.playedTimestamp = playedTimestamp;
        this.pendingDeviceJid = pendingDeviceJid;
        this.deliveredDeviceJid = deliveredDeviceJid;
    }

    public Jid userJid() {
        return userJid;
    }

    public Optional<Instant> receiptTimestamp() {
        return Optional.ofNullable(receiptTimestamp);
    }

    public Optional<Instant> readTimestamp() {
        return Optional.ofNullable(readTimestamp);
    }

    public Optional<Instant> playedTimestamp() {
        return Optional.ofNullable(playedTimestamp);
    }

    public List<Jid> pendingDeviceJid() {
        return pendingDeviceJid == null ? List.of() : Collections.unmodifiableList(pendingDeviceJid);
    }

    public List<Jid> deliveredDeviceJid() {
        return deliveredDeviceJid == null ? List.of() : Collections.unmodifiableList(deliveredDeviceJid);
    }

    public MessageReceipt setUserJid(Jid userJid) {
        this.userJid = userJid;
        return this;
    }

    public MessageReceipt setReceiptTimestamp(Instant receiptTimestamp) {
        this.receiptTimestamp = receiptTimestamp;
        return this;
    }

    public MessageReceipt setReadTimestamp(Instant readTimestamp) {
        this.readTimestamp = readTimestamp;
        return this;
    }

    public MessageReceipt setPlayedTimestamp(Instant playedTimestamp) {
        this.playedTimestamp = playedTimestamp;
        return this;
    }

    public MessageReceipt setPendingDeviceJid(List<Jid> pendingDeviceJid) {
        this.pendingDeviceJid = pendingDeviceJid;
        return this;
    }

    public MessageReceipt setDeliveredDeviceJid(List<Jid> deliveredDeviceJid) {
        this.deliveredDeviceJid = deliveredDeviceJid;
        return this;
    }
}
