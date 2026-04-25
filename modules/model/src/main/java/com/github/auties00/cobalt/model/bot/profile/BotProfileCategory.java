package com.github.auties00.cobalt.model.bot.profile;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

/**
 * Represents the character category of a WhatsApp AI bot's persona,
 * indicating the nature of the character the bot portrays.
 *
 * <p>WhatsApp classifies bot personas into categories so that users
 * understand what kind of character they are interacting with. Four
 * categories are currently defined:
 * <ul>
 * <li>{@link Synthetic} - a fully artificial persona with no real-world
 *     counterpart (e.g. Meta AI)
 * <li>{@link Living} - a persona modeled after a currently living person
 * <li>{@link Fictional} - a persona modeled after a fictional character
 * <li>{@link Historical} - a persona modeled after a historical figure
 * </ul>
 *
 * <p>An {@link Unknown} variant is provided for forward compatibility with
 * values that may be added by the server in the future. Instances are
 * obtained via the {@link #of(String)} factory method, which maps
 * wire-format strings to the appropriate variant.
 *
 * @implNote WAWebBotProfileCategory.BotProfileCategory:
 * {@code n("$InternalEnum")({SYNTHETIC:"synthetic",LIVING:"living",
 * FICTIONAL:"fictional",HISTORICAL:"historical"})}. WA Web exposes a
 * plain string-valued enum with four members. Cobalt adapts it to a
 * sealed interface of record singletons so that an {@link Unknown}
 * variant can carry forward-compatible unrecognised wire values without
 * breaking deserialization; the four canonical singletons preserve the
 * exact lowercase wire strings.
 * @see BotProfile#category()
 */
@WhatsAppWebModule(moduleName = "WAWebBotProfileCategory")
public sealed interface BotProfileCategory {
    /**
     * Singleton for a fully synthetic or artificial bot persona with no
     * real-world counterpart, such as Meta AI.
     *
     * @implNote WAWebBotProfileCategory.BotProfileCategory.SYNTHETIC with on-wire
     * value {@code "synthetic"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotProfileCategory",
            exports = "BotProfileCategory.SYNTHETIC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    BotProfileCategory SYNTHETIC = new Synthetic();

    /**
     * Singleton for a bot persona modeled after a currently living person.
     *
     * @implNote WAWebBotProfileCategory.BotProfileCategory.LIVING with on-wire
     * value {@code "living"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotProfileCategory",
            exports = "BotProfileCategory.LIVING",
            adaptation = WhatsAppAdaptation.ADAPTED)
    BotProfileCategory LIVING = new Living();

    /**
     * Singleton for a bot persona modeled after a fictional character.
     *
     * @implNote WAWebBotProfileCategory.BotProfileCategory.FICTIONAL with on-wire
     * value {@code "fictional"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotProfileCategory",
            exports = "BotProfileCategory.FICTIONAL",
            adaptation = WhatsAppAdaptation.ADAPTED)
    BotProfileCategory FICTIONAL = new Fictional();

    /**
     * Singleton for a bot persona modeled after a historical figure.
     *
     * @implNote WAWebBotProfileCategory.BotProfileCategory.HISTORICAL with on-wire
     * value {@code "historical"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotProfileCategory",
            exports = "BotProfileCategory.HISTORICAL",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Variant representing a fully synthetic or artificial bot persona with
     * no real-world counterpart.
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
     * Variant representing a bot persona modeled after a currently living
     * person.
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
     * Variant representing a bot persona modeled after a fictional character.
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
     * Variant representing a bot persona modeled after a historical figure.
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
     * Variant representing an unrecognized category value, provided for
     * forward compatibility with values that may be added by the server
     * in the future.
     *
     * @param value the raw wire-format string returned by the server
     */
    record Unknown(String value) implements BotProfileCategory {
    }
}
