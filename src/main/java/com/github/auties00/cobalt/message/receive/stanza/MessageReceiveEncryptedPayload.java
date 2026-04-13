package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.message.MessageEncryptionType;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single encrypted payload within an incoming message stanza.
 *
 * <p>A message stanza may contain multiple {@code <enc>} child nodes, each
 * with a different encryption type (e.g. {@code skmsg} for group sender-key
 * encryption and {@code pkmsg}/{@code msg} for per-device Signal encryption).
 * This class captures the parsed attributes and raw ciphertext of one such
 * node.
 *
 * @implNote WAWebHandleMsgParser.incomingMsgParser: maps each {@code <enc>}
 * child to an object with e2eType, encMediaType, ciphertext, retryCount,
 * and hideFail fields.
 */
public final class MessageReceiveEncryptedPayload {
    private final MessageEncryptionType e2eType;
    private final String encMediaType;
    private final byte[] ciphertext;
    private final int retryCount;
    private final boolean hideFail;

    public MessageReceiveEncryptedPayload(
            MessageEncryptionType e2eType,
            String encMediaType,
            byte[] ciphertext,
            int retryCount,
            boolean hideFail
    ) {
        this.e2eType = Objects.requireNonNull(e2eType, "e2eType cannot be null");
        this.encMediaType = encMediaType;
        this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        this.retryCount = retryCount;
        this.hideFail = hideFail;
    }

    /**
     * Returns the Signal encryption type (PKMSG, MSG, or SKMSG).
     */
    public MessageEncryptionType e2eType() {
        return e2eType;
    }

    /**
     * Returns the optional {@code mediatype} attribute from the {@code <enc>}
     * node (e.g. "image", "video", "ptt").
     */
    public Optional<String> encMediaType() {
        return Optional.ofNullable(encMediaType);
    }

    /**
     * Returns the raw encrypted bytes (content of the {@code <enc>} node).
     */
    public byte[] ciphertext() {
        return ciphertext;
    }

    /**
     * Returns the number of retries indicated by the {@code count}
     * attribute, defaulting to 0.
     */
    public int retryCount() {
        return retryCount;
    }

    /**
     * Returns {@code true} when {@code decrypt-fail="hide"}, indicating
     * the message should be silently dropped on decryption failure rather
     * than showing a placeholder.
     */
    public boolean hideFail() {
        return hideFail;
    }
}
