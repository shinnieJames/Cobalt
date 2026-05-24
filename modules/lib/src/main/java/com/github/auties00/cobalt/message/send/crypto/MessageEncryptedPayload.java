package com.github.auties00.cobalt.message.send.crypto;

import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;

/**
 * Result of encrypting a single outbound message for one recipient device or
 * group.
 *
 * @apiNote
 * Produced by {@link MessageEncryption#encryptForDevice} and
 * {@link MessageEncryption#encryptForGroup}; consumed by the fanout-stanza
 * builders (matching WA Web's {@code WAWebSendMsgCreateFanoutStanza}) to write
 * one {@code <enc>} child per recipient on the outgoing
 * {@code <message>}. The {@link #recipientJid} is {@code null} for SKMSG
 * payloads because a sender-key ciphertext is broadcast once to the whole
 * group rather than addressed per device.
 *
 * @param type         the Signal envelope type (PKMSG, MSG, or SKMSG)
 * @param ciphertext   the encrypted envelope bytes
 * @param recipientJid the recipient device {@link Jid}, or {@code null} for
 *                     SKMSG group payloads
 */
@WhatsAppWebModule(moduleName = "WAWebEncryptMsgProtobuf")
public record MessageEncryptedPayload(
        MessageEncryptionType type,
        byte[] ciphertext,
        Jid recipientJid
) {
    /**
     * Returns whether this payload establishes a new Signal session.
     *
     * @apiNote
     * Drives the identity-on-PKMSG branch in the fanout-stanza writer (matching
     * the {@code createFanoutMsgStanza} call-site): a {@code true} return
     * means the recipient does not yet hold the sender's identity and an
     * {@code <identity>} sibling element must accompany the {@code <enc>}.
     *
     * @return {@code true} when {@link #type} is a {@code PreKeySignalMessage}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateFanoutStanza", exports = "createFanoutMsgStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isPreKeyMessage() {
        return type.isPreKeyMessage();
    }

    /**
     * Returns whether this payload is a sender-key group message.
     *
     * @apiNote
     * Distinguishes SKMSG payloads (group fanout via shared sender key) from
     * per-device PKMSG/MSG payloads, so the stanza writer knows whether to
     * emit a single broadcast {@code <enc>} or one per recipient device.
     *
     * @return {@code true} when {@link #type} is a {@code SenderKeyMessage}
     */
    public boolean isSenderKeyMessage() {
        return type.isSenderKeyMessage();
    }
}
