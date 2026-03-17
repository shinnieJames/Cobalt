package com.github.auties00.cobalt.model.bot.profile;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

/**
 * A slash-command registered on a bot's profile.
 *
 * <p>Each command is parsed from a {@code <command>} element inside the
 * {@code <commands>} block of the USync bot profile response:
 * <pre>{@code
 * <commands>
 *   <command>
 *     <name>imagine</name>
 *     <description>Generate an image from a text prompt</description>
 *   </command>
 * </commands>
 * }</pre>
 *
 * <p>When a user sends a message starting with {@code /commandName}, the
 * message's body type should be set to {@code COMMAND} instead of
 * {@code PROMPT}.
 *
 * @see BotProfile#commands()
 * @see BotProfile#isCommand(String)
 */
@ProtobufMessage
public final class BotProfileCommand {
    /**
     * The command name without the leading slash
     * (e.g. {@code "imagine"}, {@code "translate"}).
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String name;

    /**
     * A human-readable description of what this command does
     * (e.g. {@code "Generate an image from a text prompt"}).
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String description;

    /**
     * Constructs a new {@code BotProfileCommand}.
     *
     * @param name        the command name without the leading slash,
     *                    must be non-{@code null}
     * @param description a human-readable description of the command,
     *                    may be {@code null}
     */
    BotProfileCommand(String name, String description) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
    }

    /**
     * Returns the command name without the leading slash.
     *
     * @return a non-{@code null} command name (e.g. {@code "imagine"})
     */
    public String name() {
        return name;
    }

    /**
     * Returns the human-readable description of this command.
     *
     * @return an {@code Optional} containing the description if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> description() {
        return Optional.ofNullable(description).filter(d -> !d.isEmpty());
    }

    /**
     * Sets the command name.
     *
     * @param name the command name, must be non-{@code null}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the command description.
     *
     * @param description the description, may be {@code null}
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
