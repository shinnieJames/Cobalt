package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "TemplateButton")
public final class TemplateButton {
    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer index;

    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    QuickReplyButton quickReplyButton;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    URLButton urlButton;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    CallButton callButton;


    TemplateButton(Integer index, QuickReplyButton quickReplyButton, URLButton urlButton, CallButton callButton) {
        this.index = index;
        this.quickReplyButton = quickReplyButton;
        this.urlButton = urlButton;
        this.callButton = callButton;
    }

    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public Optional<? extends TemplateButtonVariant> button() {
        if (quickReplyButton != null) return Optional.of(quickReplyButton);
        if (urlButton != null) return Optional.of(urlButton);
        if (callButton != null) return Optional.of(callButton);
        return Optional.empty();
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setQuickReplyButton(QuickReplyButton quickReplyButton) {
        this.quickReplyButton = quickReplyButton;
    }

    public void setUrlButton(URLButton urlButton) {
        this.urlButton = urlButton;
    }

    public void setCallButton(CallButton callButton) {
        this.callButton = callButton;
    }

    @ProtobufMessage(name = "TemplateButton.CallButton")
    public static final class CallButton implements TemplateButtonVariant {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage displayText;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage phoneNumber;


        CallButton(HighlyStructuredMessage displayText, HighlyStructuredMessage phoneNumber) {
            this.displayText = displayText;
            this.phoneNumber = phoneNumber;
        }

        public Optional<HighlyStructuredMessage> displayText() {
            return Optional.ofNullable(displayText);
        }

        public Optional<HighlyStructuredMessage> phoneNumber() {
            return Optional.ofNullable(phoneNumber);
        }

        public void setDisplayText(HighlyStructuredMessage displayText) {
            this.displayText = displayText;
    }

        public void setPhoneNumber(HighlyStructuredMessage phoneNumber) {
            this.phoneNumber = phoneNumber;
    }
    }

    @ProtobufMessage(name = "TemplateButton.QuickReplyButton")
    public static final class QuickReplyButton implements TemplateButtonVariant {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage displayText;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String id;


        QuickReplyButton(HighlyStructuredMessage displayText, String id) {
            this.displayText = displayText;
            this.id = id;
        }

        public Optional<HighlyStructuredMessage> displayText() {
            return Optional.ofNullable(displayText);
        }

        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        public void setDisplayText(HighlyStructuredMessage displayText) {
            this.displayText = displayText;
    }

        public void setId(String id) {
            this.id = id;
    }
    }

    @ProtobufMessage(name = "TemplateButton.URLButton")
    public static final class URLButton implements TemplateButtonVariant {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage displayText;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage url;


        URLButton(HighlyStructuredMessage displayText, HighlyStructuredMessage url) {
            this.displayText = displayText;
            this.url = url;
        }

        public Optional<HighlyStructuredMessage> displayText() {
            return Optional.ofNullable(displayText);
        }

        public Optional<HighlyStructuredMessage> url() {
            return Optional.ofNullable(url);
        }

        public void setDisplayText(HighlyStructuredMessage displayText) {
            this.displayText = displayText;
    }

        public void setUrl(HighlyStructuredMessage url) {
            this.url = url;
    }
    }
}
