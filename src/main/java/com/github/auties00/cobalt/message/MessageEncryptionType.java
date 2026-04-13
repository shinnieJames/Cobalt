package com.github.auties00.cobalt.message;

import com.github.auties00.libsignal.protocol.SignalCiphertextMessage;

/**
 * The encryption type used for message payloads.
 *
 * <p>This determines the Signal protocol message type:
 * <ul>
 *   <li>{@link #PKMSG} - PreKeySignalMessage for establishing new sessions</li>
 *   <li>{@link #MSG} - SignalMessage for messages within established sessions</li>
 *   <li>{@link #SKMSG} - SenderKeyMessage for group messages</li>
 *   <li>{@link #MSMSG} - MessageSecretMessage for bot messages with inner AES-GCM encryption</li>
 * </ul>
 *
 * <p>Both message sending and receiving need to understand these types.
 *
 * @implNote WAWebBackendJobs.flow.CiphertextType: defines Pkmsg, Msg,
 * Skmsg, and Msmsg as the four encryption types used in the protocol.
 */
public enum MessageEncryptionType {
    /**
     * PreKeySignalMessage - used for the first message to a device
     * when no session exists. Includes prekey data for session establishment.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType.Pkmsg
     */
    PKMSG("pkmsg"),

    /**
     * SignalMessage - used for subsequent messages within an
     * established Signal session.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType.Msg
     */
    MSG("msg"),

    /**
     * SenderKeyMessage - used for group messages encrypted with
     * the sender's group key. All group members can decrypt with
     * the distributed sender key.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType.Skmsg
     */
    SKMSG("skmsg"),

    /**
     * MessageSecretMessage - used for bot messages where the payload is
     * a {@code MessageSecretMessage} protobuf containing AES-GCM encrypted
     * content.  The decryption key is derived from the target message's
     * {@code messageSecret} via HKDF-SHA256.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType.Msmsg: the fourth
     * encryption type dispatched in WAWebMsgProcessingDecryptEnc.decryptEnc
     * to WAWebBotMessageSecret.decryptMsmsgBotMessage.
     */
    MSMSG("msmsg");

    /**
     * The protocol-level string value used in stanza attributes.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType: the string value
     * associated with each enum variant.
     */
    private final String protocolValue;

    /**
     * Constructs an encryption type with the given protocol value.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType: each variant maps
     * to a string literal ("pkmsg", "msg", "skmsg", "msmsg").
     * @param protocolValue the protocol-level string value
     */
    MessageEncryptionType(String protocolValue) {
        this.protocolValue = protocolValue;
    }

    /**
     * Returns the protocol value used in stanza attributes.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType: direct string
     * representation of the enum variant.
     * @return the encryption type string value (e.g., "pkmsg", "msg", "skmsg")
     */
    public String protocolValue() {
        return protocolValue;
    }

    /**
     * Determines the encryption type from a Signal ciphertext message.
     *
     * @implNote ADAPTED: WAWebBackendJobs.flow.CiphertextType: in WA Web
     * the mapping from Signal ciphertext to CiphertextType occurs inline;
     * Cobalt centralizes it here.
     * @param ciphertext the Signal ciphertext message
     * @return the corresponding encryption type
     * @throws IllegalArgumentException if the ciphertext type is unknown
     */
    public static MessageEncryptionType fromSignalCiphertext(SignalCiphertextMessage ciphertext) {
        return switch (ciphertext.type()) {
            case SignalCiphertextMessage.PRE_KEY_TYPE -> PKMSG;
            case SignalCiphertextMessage.WHISPER_TYPE -> MSG;
            case SignalCiphertextMessage.SENDER_KEY_TYPE -> SKMSG;
            default -> throw new IllegalArgumentException(
                    "Unknown Signal ciphertext type: " + ciphertext.type()
            );
        };
    }

    /**
     * Parses an encryption type from its protocol value.
     *
     * @implNote WAWebBackendJobs.flow.CiphertextType: reverse lookup by
     * string value to enum variant.
     * @param value the protocol value (e.g., "pkmsg", "msg", "skmsg")
     * @return the corresponding encryption type
     * @throws IllegalArgumentException if the value is unknown
     */
    public static MessageEncryptionType fromProtocolValue(String value) {
        return switch (value) {
            case "pkmsg" -> PKMSG;
            case "msg" -> MSG;
            case "skmsg" -> SKMSG;
            case "msmsg" -> MSMSG;
            default -> throw new IllegalArgumentException("Unknown encryption type: " + value);
        };
    }

    /**
     * Returns whether this encryption type indicates a new session establishment.
     *
     * @implNote NO_WA_BASIS: convenience predicate for checking
     * PreKeySignalMessage type.
     * @return {@code true} if this is a PreKeySignalMessage
     */
    public boolean isPreKeyMessage() {
        return this == PKMSG;
    }

    /**
     * Returns whether this encryption type is for group messages.
     *
     * @implNote NO_WA_BASIS: convenience predicate for checking
     * SenderKeyMessage type.
     * @return {@code true} if this is a SenderKeyMessage
     */
    public boolean isSenderKeyMessage() {
        return this == SKMSG;
    }
}
