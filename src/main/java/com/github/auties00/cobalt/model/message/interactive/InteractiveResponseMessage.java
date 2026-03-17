package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.InteractiveResponseMessage")
public final class InteractiveResponseMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    Body body;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    NativeFlowResponseMessage nativeFlowResponseMessage;


    InteractiveResponseMessage(Body body, ContextInfo contextInfo, NativeFlowResponseMessage nativeFlowResponseMessage) {
        this.body = body;
        this.contextInfo = contextInfo;
        this.nativeFlowResponseMessage = nativeFlowResponseMessage;
    }

    public Optional<Body> body() {
        return Optional.ofNullable(body);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<? extends InteractiveResponseMessageContent> content() {
        if (nativeFlowResponseMessage != null) return Optional.of(nativeFlowResponseMessage);
        return Optional.empty();
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setNativeFlowResponseMessage(NativeFlowResponseMessage nativeFlowResponseMessage) {
        this.nativeFlowResponseMessage = nativeFlowResponseMessage;
    }

    @ProtobufMessage(name = "Message.InteractiveResponseMessage.Body")
    public static final class Body {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String text;

        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        Body.TemplateFormat format;


        Body(String text, TemplateFormat format) {
            this.text = text;
            this.format = format;
        }

        public Optional<String> text() {
            return Optional.ofNullable(text);
        }

        public Optional<TemplateFormat> format() {
            return Optional.ofNullable(format);
        }

        public void setText(String text) {
            this.text = text;
    }

        public void setFormat(TemplateFormat format) {
            this.format = format;
    }

        @ProtobufEnum(name = "Message.InteractiveResponseMessage.Body.Format")
        public static enum TemplateFormat {
            DEFAULT(0),
            EXTENSIONS_1(1);

            TemplateFormat(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "Message.InteractiveResponseMessage.NativeFlowResponseMessage")
    public static final class NativeFlowResponseMessage implements InteractiveResponseMessageContent {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String name;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String paramsJson;

        @ProtobufProperty(index = 3, type = ProtobufType.INT32)
        Integer version;


        NativeFlowResponseMessage(String name, String paramsJson, Integer version) {
            this.name = name;
            this.paramsJson = paramsJson;
            this.version = version;
        }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public Optional<String> paramsJson() {
            return Optional.ofNullable(paramsJson);
        }

        public OptionalInt version() {
            return version == null ? OptionalInt.empty() : OptionalInt.of(version);
        }

        public void setName(String name) {
            this.name = name;
    }

        public void setParamsJson(String paramsJson) {
            this.paramsJson = paramsJson;
    }

        public void setVersion(Integer version) {
            this.version = version;
    }
    }
}
