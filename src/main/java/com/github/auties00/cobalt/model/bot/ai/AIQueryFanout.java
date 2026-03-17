package com.github.auties00.cobalt.model.bot.ai;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

/**
 * A record of an AI query being fanned out (distributed) to one or more
 * recipients in a conversation.
 *
 * <p>When a user sends a prompt to Meta AI inside a group or multi-device
 * session, the server fans the query out so that all relevant participants
 * receive the AI-generated response. This message captures the original
 * {@linkplain #messageKey() message key}, the {@linkplain #message() message content},
 * and the {@linkplain #timestamp() timestamp} of the fanout event.
 */
@ProtobufMessage(name = "AIQueryFanout")
public final class AIQueryFanout {
    /**
     * The key that uniquely identifies the original AI query message.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey messageKey;

    /**
     * The content of the AI query message that was fanned out.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageContainer messageContainer;

    /**
     * The timestamp at which the fanout occurred.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;


    /**
     * Constructs a new {@code AIQueryFanout} with the specified values.
     *
     * @param messageKey the key of the original query message, or {@code null}
     * @param messageContainer    the message content, or {@code null}
     * @param timestamp  the fanout timestamp, or {@code null}
     */
    AIQueryFanout(MessageKey messageKey, MessageContainer messageContainer, Instant timestamp) {
        this.messageKey = messageKey;
        this.messageContainer = messageContainer;
        this.timestamp = timestamp;
    }

    /**
     * Returns the key that uniquely identifies the original AI query message.
     *
     * @return an {@code Optional} describing the message key, or an empty
     *         {@code Optional} if not set
     */
    public Optional<MessageKey> messageKey() {
        return Optional.ofNullable(messageKey);
    }

    /**
     * Returns the content of the AI query message that was fanned out.
     *
     * @return an {@code Optional} describing the message, or an empty
     *         {@code Optional} if not set
     */
    public Optional<MessageContainer> message() {
        return Optional.ofNullable(messageContainer);
    }

    /**
     * Returns the timestamp at which the fanout occurred.
     *
     * @return an {@code Optional} describing the timestamp, or an empty
     *         {@code Optional} if not set
     */
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Sets the key that uniquely identifies the original AI query message.
     *
     * @param messageKey the new message key, or {@code null}
     */
    public void setMessageKey(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * Sets the content of the AI query message that was fanned out.
     *
     * @param messageContainer the new message content, or {@code null}
     */
    public void setMessage(MessageContainer messageContainer) {
        this.messageContainer = messageContainer;
    }

    /**
     * Sets the timestamp at which the fanout occurred.
     *
     * @param timestamp the new fanout timestamp, or {@code null}
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
