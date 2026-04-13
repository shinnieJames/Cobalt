package com.github.auties00.cobalt.message.send.crypto;

import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;

/**
 * The result of encrypting a message.
 *
 * @param type         the Signal encryption type (pkmsg, msg, or skmsg)
 * @param ciphertext   the encrypted message bytes
 * @param recipientJid the recipient device JID, or {@code null} for group messages
 *
 * @implNote WAWebEncryptMsgProtobuf.encryptMsgProtobuf returns
 * {@code {type, ciphertext}} for 1:1 encryption;
 * WAWebEncryptMsgProtobuf.encryptMsgSenderKey returns
 * {@code {ciphertext, senderKeyBytes}} for group encryption.
 * In Cobalt, both are unified into this record; the {@code recipientJid}
 * field is a Cobalt-specific convenience (NO_WA_BASIS).
 */
public record MessageEncryptedPayload(
        MessageEncryptionType type,
        byte[] ciphertext,
        Jid recipientJid
) {
    /**
     * Returns whether this message establishes a new session.
     *
     * @return {@code true} if this is a PreKeySignalMessage
     *
     * @implNote WAWebSendMsgCreateFanoutStanza: sets {@code shouldHaveIdentity}
     * when any encryption result has type {@code Pkmsg}.
     */
    public boolean isPreKeyMessage() {
        return type.isPreKeyMessage();
    }

    /**
     * Returns whether this is a group sender key message.
     *
     * @return {@code true} if this is a SenderKeyMessage
     *
     * @implNote NO_WA_BASIS: convenience predicate for checking
     * SenderKeyMessage type.
     */
    public boolean isSenderKeyMessage() {
        return type.isSenderKeyMessage();
    }
}
