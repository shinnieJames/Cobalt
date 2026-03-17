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

    public void setUserJid(Jid userJid) {
        this.userJid = userJid;
    }

    public void setReceiptTimestamp(Instant receiptTimestamp) {
        this.receiptTimestamp = receiptTimestamp;
    }

    public void setReadTimestamp(Instant readTimestamp) {
        this.readTimestamp = readTimestamp;
    }

    public void setPlayedTimestamp(Instant playedTimestamp) {
        this.playedTimestamp = playedTimestamp;
    }

    public void setPendingDeviceJid(List<Jid> pendingDeviceJid) {
        this.pendingDeviceJid = pendingDeviceJid;
    }

    public void setDeliveredDeviceJid(List<Jid> deliveredDeviceJid) {
        this.deliveredDeviceJid = deliveredDeviceJid;
    }
}
