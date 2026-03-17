package com.github.auties00.cobalt.model.message.interactive;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "HydratedTemplateButton")
public final class HydratedTemplateButton {
    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer index;

    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    HydratedQuickReplyButton quickReplyButton;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    HydratedURLButton urlButton;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    HydratedCallButton callButton;


    HydratedTemplateButton(Integer index, HydratedQuickReplyButton quickReplyButton, HydratedURLButton urlButton, HydratedCallButton callButton) {
        this.index = index;
        this.quickReplyButton = quickReplyButton;
        this.urlButton = urlButton;
        this.callButton = callButton;
    }

    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public Optional<? extends HydratedButtonVariant> hydratedButton() {
        if (quickReplyButton != null) return Optional.of(quickReplyButton);
        if (urlButton != null) return Optional.of(urlButton);
        if (callButton != null) return Optional.of(callButton);
        return Optional.empty();
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setQuickReplyButton(HydratedQuickReplyButton quickReplyButton) {
        this.quickReplyButton = quickReplyButton;
    }

    public void setUrlButton(HydratedURLButton urlButton) {
        this.urlButton = urlButton;
    }

    public void setCallButton(HydratedCallButton callButton) {
        this.callButton = callButton;
    }

    @ProtobufMessage(name = "HydratedTemplateButton.HydratedCallButton")
    public static final class HydratedCallButton implements HydratedButtonVariant {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String displayText;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String phoneNumber;


        HydratedCallButton(String displayText, String phoneNumber) {
            this.displayText = displayText;
            this.phoneNumber = phoneNumber;
        }

        public Optional<String> displayText() {
            return Optional.ofNullable(displayText);
        }

        public Optional<String> phoneNumber() {
            return Optional.ofNullable(phoneNumber);
        }

        public void setDisplayText(String displayText) {
            this.displayText = displayText;
    }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
    }
    }

    @ProtobufMessage(name = "HydratedTemplateButton.HydratedQuickReplyButton")
    public static final class HydratedQuickReplyButton implements HydratedButtonVariant {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String displayText;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String id;


        HydratedQuickReplyButton(String displayText, String id) {
            this.displayText = displayText;
            this.id = id;
        }

        public Optional<String> displayText() {
            return Optional.ofNullable(displayText);
        }

        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        public void setDisplayText(String displayText) {
            this.displayText = displayText;
    }

        public void setId(String id) {
            this.id = id;
    }
    }

    @ProtobufMessage(name = "HydratedTemplateButton.HydratedURLButton")
    public static final class HydratedURLButton implements HydratedButtonVariant {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String displayText;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String url;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String consentedUsersUrl;

        @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
        HydratedURLButton.WebviewPresentationType webviewPresentation;


        HydratedURLButton(String displayText, String url, String consentedUsersUrl, WebviewPresentationType webviewPresentation) {
            this.displayText = displayText;
            this.url = url;
            this.consentedUsersUrl = consentedUsersUrl;
            this.webviewPresentation = webviewPresentation;
        }

        public Optional<String> displayText() {
            return Optional.ofNullable(displayText);
        }

        public Optional<String> url() {
            return Optional.ofNullable(url);
        }

        public Optional<String> consentedUsersUrl() {
            return Optional.ofNullable(consentedUsersUrl);
        }

        public Optional<WebviewPresentationType> webviewPresentation() {
            return Optional.ofNullable(webviewPresentation);
        }

        public void setDisplayText(String displayText) {
            this.displayText = displayText;
    }

        public void setUrl(String url) {
            this.url = url;
    }

        public void setConsentedUsersUrl(String consentedUsersUrl) {
            this.consentedUsersUrl = consentedUsersUrl;
    }

        public void setWebviewPresentation(WebviewPresentationType webviewPresentation) {
            this.webviewPresentation = webviewPresentation;
    }

        @ProtobufEnum(name = "HydratedTemplateButton.HydratedURLButton.WebviewPresentationType")
        public static enum WebviewPresentationType {
            FULL(1),
            TALL(2),
            COMPACT(3);

            WebviewPresentationType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }
}
