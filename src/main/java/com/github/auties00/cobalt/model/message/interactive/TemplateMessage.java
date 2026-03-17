package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.TemplateMessage")
public final class TemplateMessage implements ContextualMessage {
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    HydratedFourRowTemplate hydratedTemplate;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String templateId;

    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    FourRowTemplate fourRowTemplate;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    HydratedFourRowTemplate hydratedFourRowTemplate;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    InteractiveMessage interactiveMessageTemplate;


    TemplateMessage(ContextInfo contextInfo, HydratedFourRowTemplate hydratedTemplate, String templateId, FourRowTemplate fourRowTemplate, HydratedFourRowTemplate hydratedFourRowTemplate, InteractiveMessage interactiveMessageTemplate) {
        this.contextInfo = contextInfo;
        this.hydratedTemplate = hydratedTemplate;
        this.templateId = templateId;
        this.fourRowTemplate = fourRowTemplate;
        this.hydratedFourRowTemplate = hydratedFourRowTemplate;
        this.interactiveMessageTemplate = interactiveMessageTemplate;
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<HydratedFourRowTemplate> hydratedTemplate() {
        return Optional.ofNullable(hydratedTemplate);
    }

    public Optional<String> templateId() {
        return Optional.ofNullable(templateId);
    }

    public Optional<? extends TemplateFormat> format() {
        if (fourRowTemplate != null) return Optional.of(fourRowTemplate);
        if (hydratedFourRowTemplate != null) return Optional.of(hydratedFourRowTemplate);
        if (interactiveMessageTemplate != null) return Optional.of(interactiveMessageTemplate);
        return Optional.empty();
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setHydratedTemplate(HydratedFourRowTemplate hydratedTemplate) {
        this.hydratedTemplate = hydratedTemplate;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public void setFourRowTemplate(FourRowTemplate fourRowTemplate) {
        this.fourRowTemplate = fourRowTemplate;
    }

    public void setHydratedFourRowTemplate(HydratedFourRowTemplate hydratedFourRowTemplate) {
        this.hydratedFourRowTemplate = hydratedFourRowTemplate;
    }

    public void setInteractiveMessageTemplate(InteractiveMessage interactiveMessageTemplate) {
        this.interactiveMessageTemplate = interactiveMessageTemplate;
    }

    public sealed interface Title permits DocumentMessage, HighlyStructuredMessage, ImageMessage, VideoMessage, LocationMessage {
    }

    public sealed interface TitleSpec permits DocumentMessage, TitleSpec.HydratedTitleText, ImageMessage, VideoMessage, LocationMessage {

        final class HydratedTitleText implements TitleSpec {
            String hydratedTitleText;

            HydratedTitleText(String hydratedTitleText) {
                this.hydratedTitleText = hydratedTitleText;
            }

            @ProtobufSerializer
            public String hydratedTitleText() {
                return hydratedTitleText;
            }

            @ProtobufDeserializer
            public static HydratedTitleText of(String hydratedTitleText) {
                return new HydratedTitleText(hydratedTitleText);
            }
        }
    }

    @ProtobufMessage(name = "Message.TemplateMessage.FourRowTemplate")
    public static final class FourRowTemplate implements TemplateFormat {
        @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage content;

        @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage footer;

        @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
        List<TemplateButton> buttons;

        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        DocumentMessage documentMessage;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        HighlyStructuredMessage highlyStructuredMessage;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        ImageMessage imageMessage;

        @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
        VideoMessage videoMessage;

        @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
        LocationMessage locationMessage;


        FourRowTemplate(HighlyStructuredMessage content, HighlyStructuredMessage footer, List<TemplateButton> buttons, DocumentMessage documentMessage, HighlyStructuredMessage highlyStructuredMessage, ImageMessage imageMessage, VideoMessage videoMessage, LocationMessage locationMessage) {
            this.content = content;
            this.footer = footer;
            this.buttons = buttons;
            this.documentMessage = documentMessage;
            this.highlyStructuredMessage = highlyStructuredMessage;
            this.imageMessage = imageMessage;
            this.videoMessage = videoMessage;
            this.locationMessage = locationMessage;
        }

        public Optional<HighlyStructuredMessage> content() {
            return Optional.ofNullable(content);
        }

        public Optional<HighlyStructuredMessage> footer() {
            return Optional.ofNullable(footer);
        }

        public List<TemplateButton> buttons() {
            return buttons == null ? List.of() : Collections.unmodifiableList(buttons);
        }

        public Optional<? extends Title> title() {
            if (documentMessage != null) return Optional.of(documentMessage);
            if (highlyStructuredMessage != null) return Optional.of(highlyStructuredMessage);
            if (imageMessage != null) return Optional.of(imageMessage);
            if (videoMessage != null) return Optional.of(videoMessage);
            if (locationMessage != null) return Optional.of(locationMessage);
            return Optional.empty();
        }

        public void setContent(HighlyStructuredMessage content) {
            this.content = content;
    }

        public void setFooter(HighlyStructuredMessage footer) {
            this.footer = footer;
    }

        public void setButtons(List<TemplateButton> buttons) {
            this.buttons = buttons;
    }

        public void setDocumentMessage(DocumentMessage documentMessage) {
            this.documentMessage = documentMessage;
    }

        public void setHighlyStructuredMessage(HighlyStructuredMessage highlyStructuredMessage) {
            this.highlyStructuredMessage = highlyStructuredMessage;
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
    }

    @ProtobufMessage(name = "Message.TemplateMessage.HydratedFourRowTemplate")
    public static final class HydratedFourRowTemplate implements TemplateFormat {
        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String hydratedContentText;

        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String hydratedFooterText;

        @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
        List<HydratedTemplateButton> hydratedButtons;

        @ProtobufProperty(index = 9, type = ProtobufType.STRING)
        String templateId;

        @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
        Boolean maskLinkedDevices;

        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        DocumentMessage documentMessage;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String hydratedTitleText;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        ImageMessage imageMessage;

        @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
        VideoMessage videoMessage;

        @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
        LocationMessage locationMessage;


        HydratedFourRowTemplate(String hydratedContentText, String hydratedFooterText, List<HydratedTemplateButton> hydratedButtons, String templateId, Boolean maskLinkedDevices, DocumentMessage documentMessage, String hydratedTitleText, ImageMessage imageMessage, VideoMessage videoMessage, LocationMessage locationMessage) {
            this.hydratedContentText = hydratedContentText;
            this.hydratedFooterText = hydratedFooterText;
            this.hydratedButtons = hydratedButtons;
            this.templateId = templateId;
            this.maskLinkedDevices = maskLinkedDevices;
            this.documentMessage = documentMessage;
            this.hydratedTitleText = hydratedTitleText;
            this.imageMessage = imageMessage;
            this.videoMessage = videoMessage;
            this.locationMessage = locationMessage;
        }

        public Optional<String> hydratedContentText() {
            return Optional.ofNullable(hydratedContentText);
        }

        public Optional<String> hydratedFooterText() {
            return Optional.ofNullable(hydratedFooterText);
        }

        public List<HydratedTemplateButton> hydratedButtons() {
            return hydratedButtons == null ? List.of() : Collections.unmodifiableList(hydratedButtons);
        }

        public Optional<String> templateId() {
            return Optional.ofNullable(templateId);
        }

        public boolean maskLinkedDevices() {
            return maskLinkedDevices != null && maskLinkedDevices;
        }

        public Optional<? extends TitleSpec> title() {
            if (documentMessage != null) return Optional.of(documentMessage);
            if (hydratedTitleText != null) return Optional.of(TitleSpec.HydratedTitleText.of(hydratedTitleText));
            if (imageMessage != null) return Optional.of(imageMessage);
            if (videoMessage != null) return Optional.of(videoMessage);
            if (locationMessage != null) return Optional.of(locationMessage);
            return Optional.empty();
        }

        public void setHydratedContentText(String hydratedContentText) {
            this.hydratedContentText = hydratedContentText;
    }

        public void setHydratedFooterText(String hydratedFooterText) {
            this.hydratedFooterText = hydratedFooterText;
    }

        public void setHydratedButtons(List<HydratedTemplateButton> hydratedButtons) {
            this.hydratedButtons = hydratedButtons;
    }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
    }

        public void setMaskLinkedDevices(Boolean maskLinkedDevices) {
            this.maskLinkedDevices = maskLinkedDevices;
    }

        public void setDocumentMessage(DocumentMessage documentMessage) {
            this.documentMessage = documentMessage;
    }

        public void setHydratedTitleText(String hydratedTitleText) {
            this.hydratedTitleText = hydratedTitleText;
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
    }
}
