package com.github.auties00.cobalt.model.message.call;

import com.github.auties00.cobalt.model.chat.ChatMessageInfoContext;
import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

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
    ChatMessageInfoContext messageContextInfo;


    CallOfferMessage(byte[] callKey, String conversionSource, byte[] conversionData, Integer conversionDelaySeconds, String ctwaSignals, byte[] ctwaPayload, ContextInfo contextInfo, String nativeFlowCallButtonPayload, String deeplinkPayload, ChatMessageInfoContext messageContextInfo) {
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

    public Optional<ChatMessageInfoContext> messageContextInfo() {
        return Optional.ofNullable(messageContextInfo);
    }

    public CallOfferMessage setCallKey(byte[] callKey) {
        this.callKey = callKey;
        return this;
    }

    public CallOfferMessage setConversionSource(String conversionSource) {
        this.conversionSource = conversionSource;
        return this;
    }

    public CallOfferMessage setConversionData(byte[] conversionData) {
        this.conversionData = conversionData;
        return this;
    }

    public CallOfferMessage setConversionDelaySeconds(Integer conversionDelaySeconds) {
        this.conversionDelaySeconds = conversionDelaySeconds;
        return this;
    }

    public CallOfferMessage setCtwaSignals(String ctwaSignals) {
        this.ctwaSignals = ctwaSignals;
        return this;
    }

    public CallOfferMessage setCtwaPayload(byte[] ctwaPayload) {
        this.ctwaPayload = ctwaPayload;
        return this;
    }

    public CallOfferMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public CallOfferMessage setNativeFlowCallButtonPayload(String nativeFlowCallButtonPayload) {
        this.nativeFlowCallButtonPayload = nativeFlowCallButtonPayload;
        return this;
    }

    public CallOfferMessage setDeeplinkPayload(String deeplinkPayload) {
        this.deeplinkPayload = deeplinkPayload;
        return this;
    }

    public CallOfferMessage setMessageContextInfo(ChatMessageInfoContext messageContextInfo) {
        this.messageContextInfo = messageContextInfo;
        return this;
    }
}
