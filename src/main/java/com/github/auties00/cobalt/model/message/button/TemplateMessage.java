package com.github.auties00.cobalt.model.message.button;

import com.github.auties00.cobalt.model.button.base.TemplateFormatter;
import com.github.auties00.cobalt.model.button.template.highlyStructured.HighlyStructuredFourRowTemplate;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedFourRowTemplate;
import com.github.auties00.cobalt.model.info.ContextInfo;
import com.github.auties00.cobalt.model.message.model.ButtonMessage;
import com.github.auties00.cobalt.model.message.model.ContextualMessage;
import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A model class that represents a message sent in a WhatsappBusiness chat that provides a list of
 * buttons to choose from.
 */
@ProtobufMessage(name = "Message.TemplateMessage")
public final class TemplateMessage implements ContextualMessage, ButtonMessage {
    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    final String templateId;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    final HydratedFourRowTemplate content;

    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    final HighlyStructuredFourRowTemplate highlyStructuredFourRowTemplateFormat;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    final HydratedFourRowTemplate hydratedFourRowTemplateFormat;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    final InteractiveMessage interactiveMessageFormat;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    public TemplateMessage(String templateId, HydratedFourRowTemplate content, HighlyStructuredFourRowTemplate highlyStructuredFourRowTemplateFormat, HydratedFourRowTemplate hydratedFourRowTemplateFormat, InteractiveMessage interactiveMessageFormat, ContextInfo contextInfo) {
        this.templateId = templateId;
        this.content = content;
        this.highlyStructuredFourRowTemplateFormat = highlyStructuredFourRowTemplateFormat;
        this.hydratedFourRowTemplateFormat = hydratedFourRowTemplateFormat;
        this.interactiveMessageFormat = interactiveMessageFormat;
        this.contextInfo = contextInfo;
    }

    @ProtobufBuilder(className = "TemplateMessageSimpleBuilder")
    static TemplateMessage customBuilder(String templateId, HydratedFourRowTemplate content, TemplateFormatter format, ContextInfo contextInfo) {
        var builder = new TemplateMessageBuilder()
                .templateId(resolveTemplateId(templateId, content, format))
                .content(content)
                .contextInfo(contextInfo);
        switch (format) {
            case HighlyStructuredFourRowTemplate highlyStructuredFourRowTemplate ->
                    builder.highlyStructuredFourRowTemplateFormat(highlyStructuredFourRowTemplate);
            case HydratedFourRowTemplate hydratedFourRowTemplate ->
                    builder.hydratedFourRowTemplateFormat(hydratedFourRowTemplate);
            case InteractiveMessage interactiveMessage -> builder.interactiveMessageFormat(interactiveMessage);
            case null -> {}
        }
        return builder.build();
    }

    private static String resolveTemplateId(String templateId, HydratedFourRowTemplate content, TemplateFormatter format) {
        if (templateId != null && !templateId.isBlank()) {
            return templateId;
        }

        if (content != null && content.templateId() != null && !content.templateId().isBlank()) {
            return content.templateId();
        }

        if (format instanceof HydratedFourRowTemplate hydratedFourRowTemplate
                && hydratedFourRowTemplate.templateId() != null
                && !hydratedFourRowTemplate.templateId().isBlank()) {
            return hydratedFourRowTemplate.templateId();
        }

        return null;
    }

    /**
     * Returns the type of format of this message
     *
     * @return a non-null {@link TemplateFormatter.Type}
     */
    public TemplateFormatter.Type formatType() {
        return format().map(TemplateFormatter::templateType)
                .orElse(TemplateFormatter.Type.NONE);
    }

    /**
     * Returns the formatter of this message
     *
     * @return an optional
     */
    public Optional<? extends TemplateFormatter> format() {
        if (highlyStructuredFourRowTemplateFormat != null) {
            return Optional.of(highlyStructuredFourRowTemplateFormat);
        }else if (hydratedFourRowTemplateFormat != null) {
            return Optional.of(hydratedFourRowTemplateFormat);
        }else if(interactiveMessageFormat != null){
            return Optional.of(interactiveMessageFormat);
        }else {
            return Optional.empty();
        }
    }

    @Override
    public Type type() {
        return Type.TEMPLATE;
    }

    public String id() {
        return templateId;
    }

    public String templateId() {
        return templateId;
    }

    public HydratedFourRowTemplate content() {
        return content;
    }

    public Optional<HighlyStructuredFourRowTemplate> highlyStructuredFourRowTemplateFormat() {
        return Optional.ofNullable(highlyStructuredFourRowTemplateFormat);
    }

    public Optional<HydratedFourRowTemplate> hydratedFourRowTemplateFormat() {
        return Optional.ofNullable(hydratedFourRowTemplateFormat);
    }

    public Optional<InteractiveMessage> interactiveMessageFormat() {
        return Optional.ofNullable(interactiveMessageFormat);
    }

    @Override
    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    @Override
    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    @Override
    public String toString() {
        return "TemplateMessage[" +
                "templateId=" + templateId + ", " +
                "content=" + content + ", " +
                "highlyStructuredFourRowTemplateFormat=" + highlyStructuredFourRowTemplateFormat + ", " +
                "hydratedFourRowTemplateFormat=" + hydratedFourRowTemplateFormat + ", " +
                "interactiveMessageFormat=" + interactiveMessageFormat + ", " +
                "contextInfo=" + contextInfo + ']';
    }
}
