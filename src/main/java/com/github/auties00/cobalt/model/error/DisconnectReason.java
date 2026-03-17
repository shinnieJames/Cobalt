package com.github.auties00.cobalt.model.error;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * An exit code that the WhatsApp server embeds in a synchronization patch to signal a
 * terminal condition during app-state synchronization.
 *
 * <p>When the server detects an unrecoverable problem while assembling a syncd patch
 * it attaches an {@code ExitCode} message to the patch instead of, or in addition to,
 * the normal mutation payload. The client is expected to treat any patch that carries
 * an exit code as fatal, log the code and the optional explanatory text, and trigger a
 * full re-synchronization of the affected collection.
 *
 * <p>The exit code is embedded at field index {@code 7} of the {@code SyncdPatch}
 * protobuf message. The {@link #code()} field classifies the nature of the failure
 * while the {@link #text()} field provides an optional human-readable description
 * that is included in diagnostic logs and error reports.
 *
 * @see DisconnectCode
 */
@ProtobufMessage(name = "ExitCode")
public final class DisconnectReason {
    /**
     * The numeric exit code that classifies the terminal condition, or {@code null}
     * if no code was provided by the server.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.UINT64)
    DisconnectCode code;

    /**
     * An optional human-readable description of the terminal condition, included by
     * the server for diagnostic purposes.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;

    /**
     * Constructs a new {@code DisconnectReason} with the given exit code and optional
     * explanatory text.
     *
     * @param code the exit code that classifies the terminal condition, or
     *        {@code null} if unknown
     * @param text an optional human-readable description of the terminal condition,
     *        or {@code null} if not provided
     */
    DisconnectReason(DisconnectCode code, String text) {
        this.code = code;
        this.text = text;
    }

    /**
     * Returns the exit code that classifies the terminal condition.
     *
     * @return an {@link Optional} containing the {@link DisconnectCode}, or an empty
     *         {@code Optional} if no code was provided by the server
     */
    public Optional<DisconnectCode> code() {
        return Optional.ofNullable(code);
    }

    /**
     * Returns the human-readable description of the terminal condition.
     *
     * @return an {@link Optional} containing the descriptive text, or an empty
     *         {@code Optional} if no text was provided by the server
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * Sets the exit code that classifies the terminal condition.
     *
     * @param code the exit code, or {@code null} to clear
     */
    public void setCode(DisconnectCode code) {
        this.code = code;
    }

    /**
     * Sets the human-readable description of the terminal condition.
     *
     * @param text the descriptive text, or {@code null} to clear
     */
    public void setText(String text) {
        this.text = text;
    }
}
