package com.github.auties00.cobalt.model.bot.ai;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

/**
 * Metadata associated with a request to regenerate an AI bot response.
 *
 * <p>When a user taps the "regenerate" button on an AI-generated message,
 * the client sends this metadata to identify which response should be
 * regenerated. It carries the {@linkplain #messageKey() key} of the original
 * bot response and the {@linkplain #responseTimestamp() timestamp} at which
 * that response was produced.
 */
@ProtobufMessage(name = "AIRegenerateMetadata")
public final class AIRegenerateMetadata {
    /**
     * The key that uniquely identifies the original bot response message to
     * be regenerated.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey messageKey;

    /**
     * The timestamp at which the original bot response was produced.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant responseTimestamp;


    /**
     * Constructs a new {@code AIRegenerateMetadata} with the specified values.
     *
     * @param messageKey        the key of the original response, or {@code null}
     * @param responseTimestamp  the original response timestamp, or {@code null}
     */
    AIRegenerateMetadata(MessageKey messageKey, Instant responseTimestamp) {
        this.messageKey = messageKey;
        this.responseTimestamp = responseTimestamp;
    }

    /**
     * Returns the key that uniquely identifies the original bot response message.
     *
     * @return an {@code Optional} describing the message key, or an empty
     *         {@code Optional} if not set
     */
    public Optional<MessageKey> messageKey() {
        return Optional.ofNullable(messageKey);
    }

    /**
     * Returns the timestamp at which the original bot response was produced.
     *
     * @return an {@code Optional} describing the response timestamp, or an empty
     *         {@code Optional} if not set
     */
    public Optional<Instant> responseTimestamp() {
        return Optional.ofNullable(responseTimestamp);
    }

    /**
     * Sets the key that uniquely identifies the original bot response message.
     *
     * @param messageKey the new message key, or {@code null}
     */
    public void setMessageKey(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * Sets the timestamp at which the original bot response was produced.
     *
     * @param responseTimestamp the new response timestamp, or {@code null}
     */
    public void setResponseTimestamp(Instant responseTimestamp) {
        this.responseTimestamp = responseTimestamp;
    }
}
