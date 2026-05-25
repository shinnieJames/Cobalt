package com.github.auties00.cobalt.message.send.crypto;

import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;

/**
 * Holds the result of encrypting a single outbound message for one recipient device or group.
 * <p>
 * Produced by {@link MessageEncryption#encryptForDevice(Jid, byte[])} and
 * {@link MessageEncryption#encryptForGroup(Jid, Jid, byte[])}, then consumed by the fanout-stanza builders that write one
 * {@code <enc>} child per recipient on the outgoing {@code <message>}. The {@link #recipientJid} is {@code null} for SKMSG
 * payloads because a sender-key ciphertext is broadcast once to the whole group rather than addressed per device.
 *
 * @param type         the Signal envelope type, one of the constants of {@link MessageEncryptionType}
 * @param ciphertext   the encrypted envelope bytes
 * @param recipientJid the recipient device {@link Jid}, or {@code null} for SKMSG group payloads
 */
@WhatsAppWebModule(moduleName = "WAWebEncryptMsgProtobuf")
public record MessageEncryptedPayload(
        MessageEncryptionType type,
        byte[] ciphertext,
        Jid recipientJid
) {
    /**
     * Returns whether this payload establishes a new Signal session.
     * <p>
     * A {@code true} return means the recipient does not yet hold the sender's identity, so the fanout-stanza writer must
     * accompany the {@code <enc>} element with an {@code <identity>} sibling.
     *
     * @return {@code true} when {@link #type} is {@link MessageEncryptionType#PKMSG}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isPreKeyMessage() {
        return type.isPreKeyMessage();
    }

    /**
     * Returns whether this payload is a sender-key group message.
     * <p>
     * Distinguishes SKMSG payloads, delivered via a shared sender key, from the per-device PKMSG and MSG payloads, so the
     * stanza writer knows whether to emit a single broadcast {@code <enc>} or one element per recipient device.
     *
     * @return {@code true} when {@link #type} is {@link MessageEncryptionType#SKMSG}
     */
    public boolean isSenderKeyMessage() {
        return type.isSenderKeyMessage();
    }
}
