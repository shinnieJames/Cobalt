package com.github.auties00.cobalt.model.bot.profile;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A suggested prompt from a bot's profile, displayed to help users start
 * a conversation with the bot.
 *
 * <p>Each prompt is parsed from a {@code <prompt>} element inside the
 * {@code <prompts>} block of the USync bot profile response:
 * <pre>{@code
 * <prompts>
 *   <prompt>
 *     <emoji>✨</emoji>
 *     <text>Tell me a joke</text>
 *   </prompt>
 * </prompts>
 * }</pre>
 *
 * @see BotProfile#prompts()
 */
@ProtobufMessage
public final class BotProfilePrompt {
    /**
     * The emoji displayed alongside the prompt text
     * (e.g. {@code "✨"}, {@code "🎨"}).
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String emoji;

    /**
     * The suggested prompt text that the user can tap to send
     * (e.g. {@code "Tell me a joke"}, {@code "Write a poem about nature"}).
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;

    /**
     * Constructs a new {@code BotProfilePrompt}.
     *
     * @param emoji the emoji associated with this prompt, may be {@code null}
     * @param text  the prompt text, may be {@code null}
     */
    BotProfilePrompt(String emoji, String text) {
        this.emoji = emoji;
        this.text = text;
    }

    /**
     * Returns the emoji associated with this prompt.
     *
     * @return an {@code Optional} containing the emoji if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> emoji() {
        return Optional.ofNullable(emoji).filter(s -> !s.isEmpty());
    }

    /**
     * Returns the suggested prompt text.
     *
     * @return an {@code Optional} containing the text if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> text() {
        return Optional.ofNullable(text).filter(s -> !s.isEmpty());
    }

    /**
     * Sets the emoji for this prompt.
     *
     * @param emoji the emoji string, may be {@code null}
     */
    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    /**
     * Sets the prompt text.
     *
     * @param text the prompt text, may be {@code null}
     */
    public void setText(String text) {
        this.text = text;
    }
}
