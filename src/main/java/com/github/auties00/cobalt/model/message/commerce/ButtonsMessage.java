package com.github.auties00.cobalt.model.message.commerce;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveHeader;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ProtobufMessage(name = "Message.ButtonsMessage")
public final class ButtonsMessage implements ContextualMessage {
    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String contentText;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String footerText;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    List<TemplateButtonVariant> buttons;

    @ProtobufProperty(index = 10, type = ProtobufType.ENUM)
    HeaderType headerType;

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    DocumentMessage documentMessage;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ImageMessage imageMessage;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    VideoMessage videoMessage;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    LocationMessage locationMessage;


    ButtonsMessage(String contentText, String footerText, ContextInfo contextInfo, List<TemplateButtonVariant> buttons, HeaderType headerType, String text, DocumentMessage documentMessage, ImageMessage imageMessage, VideoMessage videoMessage, LocationMessage locationMessage) {
        this.contentText = contentText;
        this.footerText = footerText;
        this.contextInfo = contextInfo;
        this.buttons = buttons;
        this.headerType = headerType;
        this.text = text;
        this.documentMessage = documentMessage;
        this.imageMessage = imageMessage;
        this.videoMessage = videoMessage;
        this.locationMessage = locationMessage;
    }

    public Optional<String> contentText() {
        return Optional.ofNullable(contentText);
    }

    public Optional<String> footerText() {
        return Optional.ofNullable(footerText);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public List<TemplateButtonVariant> buttons() {
        return buttons == null ? List.of() : Collections.unmodifiableList(buttons);
    }

    public Optional<HeaderType> headerType() {
        return Optional.ofNullable(headerType);
    }

    public Optional<? extends InteractiveHeader> header() {
        if (text != null) return Optional.of(InteractiveHeader.Text.of(text));
        if (documentMessage != null) return Optional.of(documentMessage);
        if (imageMessage != null) return Optional.of(imageMessage);
        if (videoMessage != null) return Optional.of(videoMessage);
        if (locationMessage != null) return Optional.of(locationMessage);
        return Optional.empty();
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public void setFooterText(String footerText) {
        this.footerText = footerText;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setButtons(List<TemplateButtonVariant> buttons) {
        this.buttons = buttons;
    }

    public void setHeaderType(HeaderType headerType) {
        this.headerType = headerType;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setDocumentMessage(DocumentMessage documentMessage) {
        this.documentMessage = documentMessage;
    }

    public void setImageMessage(ImageMessage imageMessage) {
        this.imageMessage = imageMessage;
    }

    public void setVideoMessage(VideoMessage videoMessage) {
        this.videoMessage = videoMessage;
    }

    public void setLocationMessage(LocationMessage locationMessage) {
        this.locationMessage = locationMessage;
    }

    @ProtobufEnum(name = "Message.ButtonsMessage.HeaderType")
    public static enum HeaderType {
        UNKNOWN(0),
        EMPTY(1),
        TEXT(2),
        DOCUMENT(3),
        IMAGE(4),
        VIDEO(5),
        LOCATION(6);

        HeaderType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "Message.ButtonsMessage.Button")
    public static final class TemplateButtonVariant {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String buttonId;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        TemplateButtonVariant.ButtonText buttonText;

        @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
        TemplateButtonVariant.Type type;

        @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
        TemplateButtonVariant.NativeFlowInfo nativeFlowInfo;


        TemplateButtonVariant(String buttonId, ButtonText buttonText, Type type, NativeFlowInfo nativeFlowInfo) {
            this.buttonId = buttonId;
            this.buttonText = buttonText;
            this.type = type;
            this.nativeFlowInfo = nativeFlowInfo;
        }

        public Optional<String> buttonId() {
            return Optional.ofNullable(buttonId);
        }

        public Optional<ButtonText> buttonText() {
            return Optional.ofNullable(buttonText);
        }

        public Optional<Type> type() {
            return Optional.ofNullable(type);
        }

        public Optional<NativeFlowInfo> nativeFlowInfo() {
            return Optional.ofNullable(nativeFlowInfo);
        }

        public void setButtonId(String buttonId) {
            this.buttonId = buttonId;
    }

        public void setButtonText(ButtonText buttonText) {
            this.buttonText = buttonText;
    }

        public void setType(Type type) {
            this.type = type;
    }

        public void setNativeFlowInfo(NativeFlowInfo nativeFlowInfo) {
            this.nativeFlowInfo = nativeFlowInfo;
    }

        @ProtobufEnum(name = "Message.ButtonsMessage.Button.Type")
        public static enum Type {
            UNKNOWN(0),
            RESPONSE(1),
            NATIVE_FLOW(2);

            Type(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufMessage(name = "Message.ButtonsMessage.Button.ButtonText")
        public static final class ButtonText {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String displayText;


            ButtonText(String displayText) {
                this.displayText = displayText;
            }

            public Optional<String> displayText() {
                return Optional.ofNullable(displayText);
            }

            public void setDisplayText(String displayText) {
                this.displayText = displayText;
    }
        }

        @ProtobufMessage(name = "Message.ButtonsMessage.Button.NativeFlowInfo")
        public static final class NativeFlowInfo {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String name;

            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String paramsJson;


            NativeFlowInfo(String name, String paramsJson) {
                this.name = name;
                this.paramsJson = paramsJson;
            }

            public Optional<String> name() {
                return Optional.ofNullable(name);
            }

            public Optional<String> paramsJson() {
                return Optional.ofNullable(paramsJson);
            }

            public void setName(String name) {
                this.name = name;
    }

            public void setParamsJson(String paramsJson) {
                this.paramsJson = paramsJson;
    }
        }
    }
}
