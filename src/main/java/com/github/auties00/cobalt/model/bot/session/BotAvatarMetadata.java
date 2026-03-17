package com.github.auties00.cobalt.model.bot.session;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Metadata controlling the animated avatar displayed alongside a bot response
 * on WhatsApp.
 *
 * <p>When Meta AI sends a response, the client may render an animated avatar
 * whose appearance and behaviour are driven by the fields in this message.
 * The server populates these fields based on the sentiment and content of the
 * generated response so that the avatar's expression and motion match the
 * tone of the text.
 *
 * <p>This metadata is attached to a bot message via
 * {@link com.github.auties00.cobalt.model.bot.BotMetadata#avatarMetadata()}.
 */
@ProtobufMessage(name = "BotAvatarMetadata")
public final class BotAvatarMetadata {
    /**
     * The emotional sentiment of the bot response, represented as a numeric
     * code that maps to an avatar facial expression.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer sentiment;

    /**
     * The name of the behaviour graph that drives the avatar animation
     * state machine, for example {@code "greeting"} or {@code "thinking"}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String behaviorGraph;

    /**
     * The action the avatar should perform, represented as a numeric code
     * (e.g. waving, nodding, pointing).
     */
    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer action;

    /**
     * The intensity of the avatar animation, where higher values produce
     * more pronounced expressions or movements.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer intensity;

    /**
     * The word count of the bot response text, used by the client to
     * synchronise the avatar animation duration with the reading time.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer wordCount;


    /**
     * Constructs a new {@code BotAvatarMetadata} with the specified values.
     *
     * @param sentiment     the sentiment code, or {@code null}
     * @param behaviorGraph the behaviour graph name, or {@code null}
     * @param action        the action code, or {@code null}
     * @param intensity     the animation intensity, or {@code null}
     * @param wordCount     the response word count, or {@code null}
     */
    BotAvatarMetadata(Integer sentiment, String behaviorGraph, Integer action, Integer intensity, Integer wordCount) {
        this.sentiment = sentiment;
        this.behaviorGraph = behaviorGraph;
        this.action = action;
        this.intensity = intensity;
        this.wordCount = wordCount;
    }

    /**
     * Returns the emotional sentiment code for the avatar expression.
     *
     * @return an {@code OptionalInt} describing the sentiment code, or an
     *         empty {@code OptionalInt} if not set
     */
    public OptionalInt sentiment() {
        return sentiment == null ? OptionalInt.empty() : OptionalInt.of(sentiment);
    }

    /**
     * Returns the name of the behaviour graph driving the avatar animation.
     *
     * @return an {@code Optional} describing the behaviour graph name, or an
     *         empty {@code Optional} if not set
     */
    public Optional<String> behaviorGraph() {
        return Optional.ofNullable(behaviorGraph);
    }

    /**
     * Returns the action code for the avatar animation.
     *
     * @return an {@code OptionalInt} describing the action code, or an empty
     *         {@code OptionalInt} if not set
     */
    public OptionalInt action() {
        return action == null ? OptionalInt.empty() : OptionalInt.of(action);
    }

    /**
     * Returns the intensity of the avatar animation.
     *
     * @return an {@code OptionalInt} describing the intensity, or an empty
     *         {@code OptionalInt} if not set
     */
    public OptionalInt intensity() {
        return intensity == null ? OptionalInt.empty() : OptionalInt.of(intensity);
    }

    /**
     * Returns the word count of the bot response text.
     *
     * @return an {@code OptionalInt} describing the word count, or an empty
     *         {@code OptionalInt} if not set
     */
    public OptionalInt wordCount() {
        return wordCount == null ? OptionalInt.empty() : OptionalInt.of(wordCount);
    }

    /**
     * Sets the emotional sentiment code for the avatar expression.
     *
     * @param sentiment the new sentiment code, or {@code null}
     */
    public void setSentiment(Integer sentiment) {
        this.sentiment = sentiment;
    }

    /**
     * Sets the name of the behaviour graph driving the avatar animation.
     *
     * @param behaviorGraph the new behaviour graph name, or {@code null}
     */
    public void setBehaviorGraph(String behaviorGraph) {
        this.behaviorGraph = behaviorGraph;
    }

    /**
     * Sets the action code for the avatar animation.
     *
     * @param action the new action code, or {@code null}
     */
    public void setAction(Integer action) {
        this.action = action;
    }

    /**
     * Sets the intensity of the avatar animation.
     *
     * @param intensity the new intensity, or {@code null}
     */
    public void setIntensity(Integer intensity) {
        this.intensity = intensity;
    }

    /**
     * Sets the word count of the bot response text.
     *
     * @param wordCount the new word count, or {@code null}
     */
    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }
}
