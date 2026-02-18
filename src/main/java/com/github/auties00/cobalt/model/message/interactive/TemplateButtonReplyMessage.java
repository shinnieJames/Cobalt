package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.TemplateButtonReplyMessage")
public final class TemplateButtonReplyMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String selectedId;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String selectedDisplayText;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer selectedIndex;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer selectedCarouselCardIndex;


    TemplateButtonReplyMessage(String selectedId, String selectedDisplayText, ContextInfo contextInfo, Integer selectedIndex, Integer selectedCarouselCardIndex) {
        this.selectedId = selectedId;
        this.selectedDisplayText = selectedDisplayText;
        this.contextInfo = contextInfo;
        this.selectedIndex = selectedIndex;
        this.selectedCarouselCardIndex = selectedCarouselCardIndex;
    }

    public Optional<String> selectedId() {
        return Optional.ofNullable(selectedId);
    }

    public Optional<String> selectedDisplayText() {
        return Optional.ofNullable(selectedDisplayText);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public OptionalInt selectedIndex() {
        return selectedIndex == null ? OptionalInt.empty() : OptionalInt.of(selectedIndex);
    }

    public OptionalInt selectedCarouselCardIndex() {
        return selectedCarouselCardIndex == null ? OptionalInt.empty() : OptionalInt.of(selectedCarouselCardIndex);
    }

    public TemplateButtonReplyMessage setSelectedId(String selectedId) {
        this.selectedId = selectedId;
        return this;
    }

    public TemplateButtonReplyMessage setSelectedDisplayText(String selectedDisplayText) {
        this.selectedDisplayText = selectedDisplayText;
        return this;
    }

    public TemplateButtonReplyMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public TemplateButtonReplyMessage setSelectedIndex(Integer selectedIndex) {
        this.selectedIndex = selectedIndex;
        return this;
    }

    public TemplateButtonReplyMessage setSelectedCarouselCardIndex(Integer selectedCarouselCardIndex) {
        this.selectedCarouselCardIndex = selectedCarouselCardIndex;
        return this;
    }
}
