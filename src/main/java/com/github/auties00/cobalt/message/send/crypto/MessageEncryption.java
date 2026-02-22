package com.github.auties00.cobalt.message.send.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.receive.crypto.SenderKeyNameFactory;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import com.github.auties00.libsignal.protocol.SignalSenderKeyDistributionMessage;

import java.util.Objects;

/**
 * A service for encrypting messages using the Signal Protocol.
 * <p>
 * This service handles both 1:1 messages using Signal sessions and group messages
 * using sender keys. It is the sending counterpart to
 * {@link MessageDecryption}.
 *
 * @apiNote WAWebEncryptMsgProtobuf: provides encryptMsgProtobuf for 1:1 device encryption
 * and encryptMsgSenderKey for group sender key encryption.
 */
public final class MessageEncryption {
    private static final System.Logger LOGGER = System.getLogger(MessageEncryption.class.getName());

    /**
     * Current ciphertext version used for message encryption.
     *
     * @apiNote WAWebBackendJobsCommon.CIPHERTEXT_VERSION
     */
    public static final int CIPHERTEXT_VERSION = 2;

    private final WhatsAppStore store;
    private final SignalSessionCipher sessionCipher;
    private final SignalGroupCipher groupCipher;

    private static final int MIN_PADDING = 1;
    private static final int MAX_PADDING = 16;


    /**
     * Creates a new message encryption service.
     *
     * @param store         the store for Signal protocol state
     * @param sessionCipher the cipher for 1:1 encryption
     * @param groupCipher   the cipher for sender key encryption
     */
    public MessageEncryption(
            WhatsAppStore store,
            SignalSessionCipher sessionCipher,
            SignalGroupCipher groupCipher
    ) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.sessionCipher = Objects.requireNonNull(sessionCipher, "sessionCipher cannot be null");
        this.groupCipher = Objects.requireNonNull(groupCipher, "groupCipher cannot be null");
    }

    /**
     * Encrypts a message for a specific device using Signal Protocol.
     * <p>
     * The plaintext is padded with 1-16 random bytes before encryption.
     * Returns either a PreKeySignalMessage (pkmsg) for new sessions or
     * a SignalMessage (msg) for established sessions.
     *
     * @param recipientJid the recipient's device JID
     * @param plaintext    the plaintext message bytes
     * @return the encrypted payload with its type
     * @throws WhatsAppMessageException.Send if encryption fails
     *
     * @apiNote WAWebEncryptMsgProtobuf.encryptMsgProtobuf
     */
    public MessageEncryptedPayload encryptForDevice(Jid recipientJid, byte[] plaintext) {
        Objects.requireNonNull(recipientJid, "recipientJid cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        // WAWebSendMsgCommonApi.encodeAndPad: writeRandomPadMax16 adds 1-16 bytes padding
        var paddedPlaintext = addPadding(plaintext);

        // WAWebSignalCommonUtils.createSignalAddress: convert JID to Signal address
        var address = recipientJid.toSignalAddress();

        try {
            // WAWebSignal.Cipher.encryptSignalProto -> WAWebCryptoLibrary.encryptSignalProto
            var ciphertextMessage = sessionCipher.encrypt(address, paddedPlaintext);

            // WAWebEncryptMsgProtobuf: type is Pkmsg for PreKeySignalMessage, Msg for SignalMessage
            var encryptionType = MessageEncryptionType.fromSignalCiphertext(ciphertextMessage);

            LOGGER.log(System.Logger.Level.DEBUG,
                    "Encrypted message for {0}, type={1}",
                    recipientJid, encryptionType);

            return new MessageEncryptedPayload(
                    encryptionType,
                    ciphertextMessage.toSerialized(),
                    recipientJid
            );
        } catch (Exception e) {
            // WAWebEncryptMsgProtobuf: logs "encryption fail for {device}" and rejects
            LOGGER.log(System.Logger.Level.WARNING,
                    "encryptMsgProtobuf: encryption fail for {0}: {1}",
                    recipientJid, e.getMessage());

            // Per WhatsApp Web: delete stale/corrupted session on encryption failure
            // This forces a new session establishment on the next attempt
            try {
                store.removeSession(address);
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Removed stale session for {0} after encryption failure",
                        recipientJid);
            } catch (Exception cleanupError) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Failed to cleanup session for {0}: {1}",
                        recipientJid, cleanupError.getMessage());
            }

            throw new WhatsAppMessageException.Send.Unknown(
                    "Failed to encrypt message for device: " + recipientJid, e
            );
        }
    }

    /**
     * Encrypts a message for a group using Sender Key encryption.
     * <p>
     * Group messages are encrypted once with the sender's key. All group members
     * who have received the SenderKeyDistributionMessage can decrypt.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @param plaintext the plaintext message bytes
     * @return the encrypted payload (always SKMSG type)
     * @throws WhatsAppMessageException.Send if encryption fails
     *
     * @apiNote WAWebEncryptMsgProtobuf.encryptMsgSenderKey
     */
    public MessageEncryptedPayload encryptForGroup(Jid groupJid, Jid senderJid, byte[] plaintext) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        // WAWebSendMsgCommonApi.encodeAndPad: same padding as 1:1 messages
        var paddedPlaintext = addPadding(plaintext);

        // WAWebSendGroupSkmsgJob: sender JID is based on addressing mode (PN or LID)
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);

        try {
            // WAWebSignal.Cipher.encryptSenderKeyMsgSignalProto
            var ciphertextMessage = groupCipher.encrypt(senderKeyName, paddedPlaintext);

            LOGGER.log(System.Logger.Level.DEBUG,
                    "Encrypted group message for {0}, sender={1}",
                    groupJid, senderJid);

            return new MessageEncryptedPayload(
                    MessageEncryptionType.SKMSG,
                    ciphertextMessage.toSerialized(),
                    null
            );
        } catch (Exception e) {
            // WAWebEncryptMsgProtobuf: logs and rejects on failure
            LOGGER.log(System.Logger.Level.WARNING,
                    "encryptMsgSenderKey: encryption fail for {0}: {1}",
                    groupJid, e.getMessage());
            throw new WhatsAppMessageException.Send.Unknown(
                    "Failed to encrypt group message for group: " + groupJid, e
            );
        }
    }

    /**
     * Adds PKCS#7 style padding to a plaintext message.
     * The padding length is random between 1-16 bytes.
     * All padding bytes contain the padding length value.
     *
     * @param plaintext the original plaintext bytes
     * @return the padded plaintext
     *
     * @apiNote WAWebSendMsgCommonApi.writeRandomPadMax16: PKCS#7 padding where all padding
     * bytes are set to the padding length value.
     */
    private static byte[] addPadding(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        // Generate random padding length between 1 and 16
        var paddingLength = MIN_PADDING + (SecureBytes.random(1)[0] & 0x0F);

        var padded = new byte[plaintext.length + paddingLength];
        System.arraycopy(plaintext, 0, padded, 0, plaintext.length);

        // PKCS#7 padding: fill ALL padding bytes with the padding length value
        // Per WAWebSendMsgCommonApi.writeRandomPadMax16
        for (int i = plaintext.length; i < padded.length; i++) {
            padded[i] = (byte) paddingLength;
        }

        return padded;
    }

    /**
     * Creates a sender key distribution message for a group.
     * <p>
     * This message must be sent to group members who don't have the sender's key.
     * It contains the initial chain key and signing key for decryption.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @return the sender key distribution message
     *
     * @apiNote WAWebSignal.Session.getGroupSenderKeyInfo
     * WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg
     */
    public SignalSenderKeyDistributionMessage createSenderKeyDistributionMessage(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return groupCipher.create(senderKeyName);
    }

    /**
     * Gets the raw sender key bytes for distribution.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @return the serialized sender key distribution message
     *
     * @apiNote WAWebGetGroupKeyDistributionMsg: extracts axolotlSenderKeyDistributionMessage
     */
    public byte[] getSenderKeyBytes(Jid groupJid, Jid senderJid) {
        var distributionMessage = createSenderKeyDistributionMessage(groupJid, senderJid);
        return distributionMessage.toSerialized();
    }

    /**
     * Deletes the sender key for a group, forcing regeneration.
     * <p>
     * Key rotation is required when participants are removed from the group.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     *
     * @apiNote WAWebSignal.Session.deleteGroupSenderKeyInfo
     */
    public void rotateSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        // WAWebSignal.Session.deleteGroupSenderKeyInfo(groupJid, senderJid)
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        store.removeSenderKeysForDevice(senderKeyName);

        LOGGER.log(System.Logger.Level.DEBUG,
                "Rotated sender key for group {0}, sender {1}",
                groupJid, senderJid);
    }

    /**
     * Returns whether a Signal session exists with the specified device.
     *
     * @param deviceJid the device JID to check
     * @return {@code true} if a session exists
     *
     * @apiNote WAWebSignal.Session.hasSignalSessions
     */
    public boolean hasSessionWith(Jid deviceJid) {
        Objects.requireNonNull(deviceJid, "deviceJid cannot be null");
        var address = deviceJid.toSignalAddress();
        return store.findSessionByAddress(address).isPresent();
    }

    /**
     * Returns whether a sender key exists for the specified group and sender.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @return {@code true} if a sender key exists
     *
     * @apiNote WAWebApiParticipantStore: tracks hasSenderKey status per participant
     */
    public boolean hasSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return store.findSenderKeyByName(senderKeyName).isPresent();
    }

}
