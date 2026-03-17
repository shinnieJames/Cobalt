package com.github.auties00.cobalt.model.message.bot;

import com.github.auties00.cobalt.model.bot.response.AIRichResponseMessageType;
import com.github.auties00.cobalt.model.bot.response.AIRichResponseSubMessage;
import com.github.auties00.cobalt.model.bot.response.AIRichResponseUnifiedResponse;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A rich response message sent by the WhatsApp AI bot.
 *
 * <p>A rich response is a structured message composed of one or more
 * typed {@link AIRichResponseSubMessage} fragments that the client
 * renders in sequence. Fragments can contain text, code, tables,
 * images, maps, LaTeX expressions, dynamic media, or reel carousels.
 *
 * <p>In addition to the typed fragments, the server may include an
 * {@linkplain #unifiedResponse() unified response} that carries a
 * single UTF-8 JSON blob representing the full response in a
 * consolidated format.
 *
 * <p>This message is embedded at field 97 of the WhatsApp
 * {@code Message} protobuf.
 */
@ProtobufMessage(name = "AIRichResponseMessage")
public final class AIRichResponseMessage implements ContextualMessage {
    /**
     * The overall type of this rich response.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    AIRichResponseMessageType messageType;

    /**
     * The ordered list of content fragments that compose this rich
     * response.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<AIRichResponseSubMessage> submessages;

    /**
     * An optional unified response payload containing a single
     * UTF-8 encoded JSON document that represents the full response.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    AIRichResponseUnifiedResponse unifiedResponse;

    /**
     * Optional context information for this message, such as
     * forwarding metadata and quoted message references.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    AIRichResponseMessage(AIRichResponseMessageType messageType, List<AIRichResponseSubMessage> submessages, AIRichResponseUnifiedResponse unifiedResponse, ContextInfo contextInfo) {
        this.messageType = messageType;
        this.submessages = submessages;
        this.unifiedResponse = unifiedResponse;
        this.contextInfo = contextInfo;
    }

    /**
     * Returns the overall type of this rich response.
     *
     * @return an {@link Optional} containing the message type, or
     *         empty if not set
     */
    public Optional<AIRichResponseMessageType> messageType() {
        return Optional.ofNullable(messageType);
    }

    /**
     * Returns the ordered list of content fragments that compose
     * this rich response.
     *
     * @return an unmodifiable list of sub-messages, never {@code null}
     */
    public List<AIRichResponseSubMessage> submessages() {
        return submessages == null ? List.of() : Collections.unmodifiableList(submessages);
    }

    /**
     * Returns the unified response payload, if present.
     *
     * @return an {@link Optional} containing the unified response,
     *         or empty if not set
     */
    public Optional<AIRichResponseUnifiedResponse> unifiedResponse() {
        return Optional.ofNullable(unifiedResponse);
    }

    /**
     * Returns the context information for this message.
     *
     * @return an {@link Optional} containing the context info, or
     *         empty if not set
     */
    @Override
    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    /**
     * Sets the overall type of this rich response.
     *
     * @param messageType the message type to set
     */
    public void setMessageType(AIRichResponseMessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * Sets the ordered list of content fragments that compose this
     * rich response.
     *
     * @param submessages the sub-messages to set
     */
    public void setSubmessages(List<AIRichResponseSubMessage> submessages) {
        this.submessages = submessages;
    }

    /**
     * Sets the unified response payload.
     *
     * @param unifiedResponse the unified response to set
     */
    public void setUnifiedResponse(AIRichResponseUnifiedResponse unifiedResponse) {
        this.unifiedResponse = unifiedResponse;
    }

    /**
     * Sets the context information for this message.
     *
     * @param contextInfo the context info to set
     */
    @Override
    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }
}
