package com.github.auties00.cobalt.model.bot.profile;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

/**
 * Indicates whether a bot is posing as a professional (e.g. a doctor,
 * lawyer, or financial advisor).
 *
 * <p>This classification is returned by the server as the {@code type}
 * attribute of the {@code <posing_as_professional>} element in the USync
 * bot profile response. Three values are currently defined:
 * <ul>
 * <li>{@link Yes} — the bot claims a professional role
 * <li>{@link No} — the bot does not claim a professional role
 * </ul>
 *
 * <p>An {@link Unknown} variant is provided for forward compatibility
 * with values that may be added by the server in the future.
 *
 * <p>The wire format is a plain {@code String} (e.g. {@code "unknown"},
 * {@code "yes"}, {@code "no"}).
 *
 * @see BotProfile#professionalStatus()
 */
public sealed interface BotProfessionalStatus {
    /**
     * The professional status has not been determined.
     */
    BotProfessionalStatus UNKNOWN = new Unknown();

    /**
     * The bot claims a professional role.
     */
    BotProfessionalStatus YES = new Yes();

    /**
     * The bot does not claim a professional role.
     */
    BotProfessionalStatus NO = new No();

    /**
     * Returns the {@code BotProfessionalStatus} corresponding to the given
     * wire value.
     *
     * <p>Recognized values are {@code "yes"}, and
     * {@code "no"} (case-sensitive). Any other non-{@code null} value yields
     * an {@link Unknown} instance.
     *
     * @param value the wire-format string, or {@code null}
     * @return the corresponding status, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    static BotProfessionalStatus of(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "yes" -> YES;
            case "no" -> NO;
            default -> UNKNOWN;
        };
    }

    /**
     * Returns the wire-format string representation of this status.
     *
     * @return the status value as sent over the wire (e.g. {@code "yes"})
     */
    @ProtobufSerializer
    String value();

    /**
     * The professional status has not been determined.
     */
    record Unknown() implements BotProfessionalStatus {
        /**
         * {@inheritDoc}
         *
         * @return {@code "unknown"}
         */
        @Override
        public String value() {
            return "unknown";
        }
    }

    /**
     * The bot claims a professional role.
     */
    record Yes() implements BotProfessionalStatus {
        /**
         * {@inheritDoc}
         *
         * @return {@code "yes"}
         */
        @Override
        public String value() {
            return "yes";
        }
    }

    /**
     * The bot does not claim a professional role.
     */
    record No() implements BotProfessionalStatus {
        /**
         * {@inheritDoc}
         *
         * @return {@code "no"}
         */
        @Override
        public String value() {
            return "no";
        }
    }
}
