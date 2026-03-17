package com.github.auties00.cobalt.model.message.commerce;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.OrderMessage")
public final class OrderMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String orderId;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] thumbnail;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    Integer itemCount;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    OrderStatus status;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    OrderSurface surface;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String message;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String orderTitle;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    Jid sellerJid;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String token;

    @ProtobufProperty(index = 10, type = ProtobufType.INT64)
    Long totalAmount1000;

    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    String totalCurrencyCode;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 12, type = ProtobufType.INT32)
    Integer messageVersion;

    @ProtobufProperty(index = 13, type = ProtobufType.MESSAGE)
    MessageKey orderRequestMessageId;

    @ProtobufProperty(index = 15, type = ProtobufType.STRING)
    String catalogType;


    OrderMessage(String orderId, byte[] thumbnail, Integer itemCount, OrderStatus status, OrderSurface surface, String message, String orderTitle, Jid sellerJid, String token, Long totalAmount1000, String totalCurrencyCode, ContextInfo contextInfo, Integer messageVersion, MessageKey orderRequestMessageId, String catalogType) {
        this.orderId = orderId;
        this.thumbnail = thumbnail;
        this.itemCount = itemCount;
        this.status = status;
        this.surface = surface;
        this.message = message;
        this.orderTitle = orderTitle;
        this.sellerJid = sellerJid;
        this.token = token;
        this.totalAmount1000 = totalAmount1000;
        this.totalCurrencyCode = totalCurrencyCode;
        this.contextInfo = contextInfo;
        this.messageVersion = messageVersion;
        this.orderRequestMessageId = orderRequestMessageId;
        this.catalogType = catalogType;
    }

    public Optional<String> orderId() {
        return Optional.ofNullable(orderId);
    }

    public Optional<byte[]> thumbnail() {
        return Optional.ofNullable(thumbnail);
    }

    public OptionalInt itemCount() {
        return itemCount == null ? OptionalInt.empty() : OptionalInt.of(itemCount);
    }

    public Optional<OrderStatus> status() {
        return Optional.ofNullable(status);
    }

    public Optional<OrderSurface> surface() {
        return Optional.ofNullable(surface);
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public Optional<String> orderTitle() {
        return Optional.ofNullable(orderTitle);
    }

    public Optional<Jid> sellerJid() {
        return Optional.ofNullable(sellerJid);
    }

    public Optional<String> token() {
        return Optional.ofNullable(token);
    }

    public OptionalLong totalAmount1000() {
        return totalAmount1000 == null ? OptionalLong.empty() : OptionalLong.of(totalAmount1000);
    }

    public Optional<String> totalCurrencyCode() {
        return Optional.ofNullable(totalCurrencyCode);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public OptionalInt messageVersion() {
        return messageVersion == null ? OptionalInt.empty() : OptionalInt.of(messageVersion);
    }

    public Optional<MessageKey> orderRequestMessageId() {
        return Optional.ofNullable(orderRequestMessageId);
    }

    public Optional<String> catalogType() {
        return Optional.ofNullable(catalogType);
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setSurface(OrderSurface surface) {
        this.surface = surface;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setOrderTitle(String orderTitle) {
        this.orderTitle = orderTitle;
    }

    public void setSellerJid(Jid sellerJid) {
        this.sellerJid = sellerJid;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setTotalAmount1000(Long totalAmount1000) {
        this.totalAmount1000 = totalAmount1000;
    }

    public void setTotalCurrencyCode(String totalCurrencyCode) {
        this.totalCurrencyCode = totalCurrencyCode;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setMessageVersion(Integer messageVersion) {
        this.messageVersion = messageVersion;
    }

    public void setOrderRequestMessageId(MessageKey orderRequestMessageId) {
        this.orderRequestMessageId = orderRequestMessageId;
    }

    public void setCatalogType(String catalogType) {
        this.catalogType = catalogType;
    }

    @ProtobufEnum(name = "Message.OrderMessage.OrderStatus")
    public static enum OrderStatus {
        INQUIRY(1),
        ACCEPTED(2),
        DECLINED(3);

        OrderStatus(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "Message.OrderMessage.OrderSurface")
    public static enum OrderSurface {
        CATALOG(1);

        OrderSurface(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
