package com.github.auties00.cobalt.model.error;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

/**
 * A numeric exit code that classifies the terminal condition signaled by the WhatsApp
 * server in a synchronization patch.
 *
 * <p>Two exit codes are currently defined by the WhatsApp protocol. {@link MissingData}
 * with wire value {@code 100} indicates that the patch is missing required mutation
 * data. {@link DeserializationError} with wire value {@code 101} indicates that the
 * server was unable to deserialize the patch contents.
 *
 * <p>An {@link Unknown} variant preserves exit code values that may be introduced by
 * the server in the future. On the client any unrecognized code is treated identically
 * to a known terminal code and triggers a fatal synchronization error.
 *
 * <p>The wire format is an unsigned 64-bit integer ({@code UINT64}).
 *
 * @see DisconnectReason#code()
 */
public sealed interface DisconnectCode {
    /**
     * The singleton instance for a missing-data exit code, corresponding to the wire
     * value {@code 100}.
     */
    DisconnectCode MISSING_DATA = new MissingData();

    /**
     * The singleton instance for a deserialization-error exit code, corresponding to
     * the wire value {@code 101}.
     */
    DisconnectCode DESERIALIZATION_ERROR = new DeserializationError();

    /**
     * Returns the {@code DisconnectCode} corresponding to the given wire value.
     *
     * <p>Recognized values are {@code 100} and {@code 101}. Any other
     * non-{@code null} value yields an {@link Unknown} instance that preserves the
     * original number for forward compatibility.
     *
     * @param value the unsigned 64-bit wire-format value, or {@code null}
     * @return the corresponding disconnect code, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    static DisconnectCode of(Long value) {
        if (value == null) {
            return null;
        }
        return switch (value.intValue()) {
            case 100 -> MISSING_DATA;
            case 101 -> DESERIALIZATION_ERROR;
            default -> new Unknown(value);
        };
    }

    /**
     * Returns the unsigned 64-bit wire-format value of this exit code.
     *
     * @return the numeric code as sent over the wire
     */
    @ProtobufSerializer
    Long value();

    /**
     * A terminal exit code indicating that the synchronization patch is missing
     * required mutation data.
     *
     * <p>Corresponds to the wire value {@code 100} and maps to the
     * {@code TERMINAL_PATCH_MISSING_DATA} fatal error type on the client.
     */
    record MissingData() implements DisconnectCode {
        /**
         * {@inheritDoc}
         *
         * @return {@code 100L}
         */
        @Override
        public Long value() {
            return 100L;
        }
    }

    /**
     * A terminal exit code indicating that the server was unable to deserialize the
     * contents of the synchronization patch.
     *
     * <p>Corresponds to the wire value {@code 101} and maps to the
     * {@code TERMINAL_PATCH_DESERIALIZATION_ERROR} fatal error type on the client.
     */
    record DeserializationError() implements DisconnectCode {
        /**
         * {@inheritDoc}
         *
         * @return {@code 101L}
         */
        @Override
        public Long value() {
            return 101L;
        }
    }

    /**
     * An exit code that is not recognized by the current protocol version.
     *
     * <p>This variant preserves the original wire value for forward compatibility
     * with exit codes that may be introduced by the server in the future. On the
     * client this maps to the {@code TERMINAL_PATCH_UNKNOWN} fatal error type.
     *
     * @param value the raw unsigned 64-bit wire-format value
     */
    record Unknown(Long value) implements DisconnectCode {
    }
}
