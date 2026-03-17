package com.github.auties00.cobalt.model.message.call;

import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.Call")
public final class CallOfferMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] callKey;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String conversionSource;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] conversionData;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer conversionDelaySeconds;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String ctwaSignals;

    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] ctwaPayload;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String nativeFlowCallButtonPayload;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String deeplinkPayload;

    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    ChatMessageContextInfo messageContextInfo;


    CallOfferMessage(byte[] callKey, String conversionSource, byte[] conversionData, Integer conversionDelaySeconds, String ctwaSignals, byte[] ctwaPayload, ContextInfo contextInfo, String nativeFlowCallButtonPayload, String deeplinkPayload, ChatMessageContextInfo messageContextInfo) {
        this.callKey = callKey;
        this.conversionSource = conversionSource;
        this.conversionData = conversionData;
        this.conversionDelaySeconds = conversionDelaySeconds;
        this.ctwaSignals = ctwaSignals;
        this.ctwaPayload = ctwaPayload;
        this.contextInfo = contextInfo;
        this.nativeFlowCallButtonPayload = nativeFlowCallButtonPayload;
        this.deeplinkPayload = deeplinkPayload;
        this.messageContextInfo = messageContextInfo;
    }

    public Optional<byte[]> callKey() {
        return Optional.ofNullable(callKey);
    }

    public Optional<String> conversionSource() {
        return Optional.ofNullable(conversionSource);
    }

    public Optional<byte[]> conversionData() {
        return Optional.ofNullable(conversionData);
    }

    public OptionalInt conversionDelaySeconds() {
        return conversionDelaySeconds == null ? OptionalInt.empty() : OptionalInt.of(conversionDelaySeconds);
    }

    public Optional<String> ctwaSignals() {
        return Optional.ofNullable(ctwaSignals);
    }

    public Optional<byte[]> ctwaPayload() {
        return Optional.ofNullable(ctwaPayload);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<String> nativeFlowCallButtonPayload() {
        return Optional.ofNullable(nativeFlowCallButtonPayload);
    }

    public Optional<String> deeplinkPayload() {
        return Optional.ofNullable(deeplinkPayload);
    }

    public Optional<ChatMessageContextInfo> messageContextInfo() {
        return Optional.ofNullable(messageContextInfo);
    }

    public void setCallKey(byte[] callKey) {
        this.callKey = callKey;
    }

    public void setConversionSource(String conversionSource) {
        this.conversionSource = conversionSource;
    }

    public void setConversionData(byte[] conversionData) {
        this.conversionData = conversionData;
    }

    public void setConversionDelaySeconds(Integer conversionDelaySeconds) {
        this.conversionDelaySeconds = conversionDelaySeconds;
    }

    public void setCtwaSignals(String ctwaSignals) {
        this.ctwaSignals = ctwaSignals;
    }

    public void setCtwaPayload(byte[] ctwaPayload) {
        this.ctwaPayload = ctwaPayload;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setNativeFlowCallButtonPayload(String nativeFlowCallButtonPayload) {
        this.nativeFlowCallButtonPayload = nativeFlowCallButtonPayload;
    }

    public void setDeeplinkPayload(String deeplinkPayload) {
        this.deeplinkPayload = deeplinkPayload;
    }

    public void setMessageContextInfo(ChatMessageContextInfo messageContextInfo) {
        this.messageContextInfo = messageContextInfo;
    }
}
