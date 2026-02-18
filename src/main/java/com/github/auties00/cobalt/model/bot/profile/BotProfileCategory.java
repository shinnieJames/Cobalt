package com.github.auties00.cobalt.model.bot.profile;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

/**
 * The character category of a bot profile, indicating the nature of the
 * bot's persona.
 *
 * <p>This classification is fetched from the server via USync and determines
 * how the bot is categorized in the WhatsApp client. Four categories are
 * currently defined:
 * <ul>
 * <li>{@link Synthetic} — a fully artificial persona with no real-world
 *     counterpart (e.g. Meta AI)
 * <li>{@link Living} — a persona modeled after a currently living person
 * <li>{@link Fictional} — a persona modeled after a fictional character
 * <li>{@link Historical} — a persona modeled after a historical figure
 * </ul>
 *
 * <p>An {@link Unknown} variant is provided for forward compatibility with
 * values that may be added by the server in the future.
 *
 * <p>The wire format is a plain {@code String} (e.g. {@code "synthetic"},
 * {@code "living"}).
 *
 * @see BotProfile#category()
 */
public sealed interface BotProfileCategory {
    /**
     * A fully synthetic / artificial bot persona with no real-world
     * counterpart (e.g. Meta AI).
     *
     * <p>This is the default category assigned when the server does not
     * return a recognized value.
     */
    BotProfileCategory SYNTHETIC = new Synthetic();

    /**
     * A bot persona modeled after a currently living person.
     */
    BotProfileCategory LIVING = new Living();

    /**
     * A bot persona modeled after a fictional character.
     */
    BotProfileCategory FICTIONAL = new Fictional();

    /**
     * A bot persona modeled after a historical figure.
     */
    BotProfileCategory HISTORICAL = new Historical();

    /**
     * Returns the {@code BotProfileCategory} corresponding to the given wire
     * value.
     *
     * <p>Recognized values are {@code "synthetic"}, {@code "living"},
     * {@code "fictional"}, and {@code "historical"} (case-sensitive). Any
     * other non-{@code null} value yields an {@link Unknown} instance.
     *
     * @param value the wire-format string, or {@code null}
     * @return the corresponding category, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    static BotProfileCategory of(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "synthetic" -> SYNTHETIC;
            case "living" -> LIVING;
            case "fictional" -> FICTIONAL;
            case "historical" -> HISTORICAL;
            default -> new Unknown(value);
        };
    }

    /**
     * Returns the wire-format string representation of this category.
     *
     * @return the category value as sent over the wire (e.g. {@code "synthetic"})
     */
    @ProtobufSerializer
    String value();

    /**
     * A fully synthetic / artificial bot persona.
     */
    record Synthetic() implements BotProfileCategory {
        /**
         * {@inheritDoc}
         *
         * @return {@code "synthetic"}
         */
        @Override
        public String value() {
            return "synthetic";
        }
    }

    /**
     * A bot persona modeled after a currently living person.
     */
    record Living() implements BotProfileCategory {
        /**
         * {@inheritDoc}
         *
         * @return {@code "living"}
         */
        @Override
        public String value() {
            return "living";
        }
    }

    /**
     * A bot persona modeled after a fictional character.
     */
    record Fictional() implements BotProfileCategory {
        /**
         * {@inheritDoc}
         *
         * @return {@code "fictional"}
         */
        @Override
        public String value() {
            return "fictional";
        }
    }

    /**
     * A bot persona modeled after a historical figure.
     */
    record Historical() implements BotProfileCategory {
        /**
         * {@inheritDoc}
         *
         * @return {@code "historical"}
         */
        @Override
        public String value() {
            return "historical";
        }
    }

    /**
     * An unrecognized category value, provided for forward compatibility.
     *
     * @param value the raw wire-format string
     */
    record Unknown(String value) implements BotProfileCategory {
    }
}
