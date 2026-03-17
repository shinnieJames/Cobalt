package com.github.auties00.cobalt.model.message.commerce;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveResponse;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage(name = "Message.ButtonsResponseMessage")
public final class ButtonsResponseMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String selectedButtonId;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    Type type;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String selectedDisplayText;


    ButtonsResponseMessage(String selectedButtonId, ContextInfo contextInfo, Type type, String selectedDisplayText) {
        this.selectedButtonId = selectedButtonId;
        this.contextInfo = contextInfo;
        this.type = type;
        this.selectedDisplayText = selectedDisplayText;
    }

    public Optional<String> selectedButtonId() {
        return Optional.ofNullable(selectedButtonId);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<Type> type() {
        return Optional.ofNullable(type);
    }

    public Optional<? extends InteractiveResponse> response() {
        if (selectedDisplayText != null) return Optional.of(InteractiveResponse.SelectedDisplayText.of(selectedDisplayText));
        return Optional.empty();
    }

    public void setSelectedButtonId(String selectedButtonId) {
        this.selectedButtonId = selectedButtonId;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setSelectedDisplayText(String selectedDisplayText) {
        this.selectedDisplayText = selectedDisplayText;
    }

    @ProtobufEnum(name = "Message.ButtonsResponseMessage.Type")
    public static enum Type {
        UNKNOWN(0),
        DISPLAY_TEXT(1);

        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
