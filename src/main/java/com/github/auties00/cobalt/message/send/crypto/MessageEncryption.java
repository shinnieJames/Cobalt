package com.github.auties00.cobalt.message.send.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.receive.crypto.SenderKeyNameFactory;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.FastRandomUtils;
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
 * @implNote WAWebEncryptMsgProtobuf: provides {@code encryptMsgProtobuf} for 1:1 device
 * encryption and {@code encryptMsgSenderKey} for group sender key encryption.
 * In Cobalt, constructor-based DI replaces module-level imports for
 * WAWebSignal.Cipher and WAWebSignalProtocolStore.
 */
public final class MessageEncryption {
    /**
     * Logger for encryption diagnostics.
     *
     * @implNote ADAPTED: WAWebEncryptMsgProtobuf uses {@code WALogger.WARN} for error
     * logging; Cobalt uses {@link System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageEncryption.class.getName());

    /**
     * Current ciphertext version used for message encryption.
     *
     * @implNote WAWebBackendJobsCommon.CIPHERTEXT_VERSION: constant {@code m = 2},
     * exported as {@code CIPHERTEXT_VERSION}.
     */
    public static final int CIPHERTEXT_VERSION = 2;

    /**
     * The WhatsApp store for session and sender key lookups.
     *
     * @implNote ADAPTED: WAWebEncryptMsgProtobuf accesses
     * WAWebSignalProtocolStore and WAWebSignalSessionApi implicitly via
     * module imports; Cobalt uses constructor-based DI.
     */
    private final WhatsAppStore store;

    /**
     * The Signal session cipher for per-device encryption.
     *
     * @implNote ADAPTED: WAWebEncryptMsgProtobuf delegates to
     * WAWebSignal.Cipher which wraps WAWebCryptoLibrary.
     */
    private final SignalSessionCipher sessionCipher;

    /**
     * The Signal group cipher for sender-key encryption.
     *
     * @implNote ADAPTED: WAWebEncryptMsgProtobuf delegates to
     * WAWebSignal.Cipher which wraps WAWebCryptoLibrary.
     */
    private final SignalGroupCipher groupCipher;

    /**
     * Minimum padding length in bytes.
     *
     * @implNote WACryptoPkcs7.writeRandomPadMax16: padding byte is
     * {@code (randomByte & 0x0F) + 1}, giving minimum 1.
     */
    private static final int MIN_PADDING = 1;

    /**
     * Maximum padding length in bytes.
     *
     * @implNote WACryptoPkcs7.writeRandomPadMax16: padding byte is
     * {@code (randomByte & 0x0F) + 1}, giving maximum 16.
     */
    private static final int MAX_PADDING = 16;

    /**
     * Creates a new message encryption service.
     *
     * @param store         the store for Signal protocol state
     * @param sessionCipher the cipher for 1:1 encryption
     * @param groupCipher   the cipher for sender key encryption
     *
     * @implNote ADAPTED: WAWebEncryptMsgProtobuf uses module-level imports
     * for WAWebSignal.Cipher and WAWebSignalProtocolStore;
     * Cobalt uses constructor-based DI instead.
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
     * @param plaintext    the plaintext message bytes (already protobuf-encoded)
     * @return the encrypted payload with its type
     * @throws WhatsAppMessageException.Send if encryption fails
     *
     * @implNote WAWebEncryptMsgProtobuf.encryptMsgProtobuf: calls
     * {@code WAWebSignal.Cipher.encryptSignalProto(address, encodeAndPad(msg), scope)}
     * which returns {@code {type, ciphertext}}. In Cobalt, the caller handles protobuf
     * encoding; this method handles only padding and encryption.
     * WAM metrics ({@code postSuccessDirectE2eMessageSendMetric} /
     * {@code postFailureDirectE2eMessageSendMetric}) are intentionally skipped.
     */
    public MessageEncryptedPayload encryptForDevice(Jid recipientJid, byte[] plaintext) {
        Objects.requireNonNull(recipientJid, "recipientJid cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        // ADAPTED: WAWebSendMsgCommonApi.encodeAndPad calls encodeProtobuf then
        // writeRandomPadMax16; in Cobalt the caller encodes, we only pad here
        var paddedPlaintext = addPadding(plaintext);

        // WAWebSignalCommonUtils.createSignalAddress: convert JID to Signal address
        var address = recipientJid.toSignalAddress();

        try {
            // WAWebSignal.Cipher.encryptSignalProto -> WAWebCryptoLibrary.encryptSignalProto
            var ciphertextMessage = sessionCipher.encrypt(address, paddedPlaintext);

            // WAWebEncryptMsgProtobuf: type is cast via CiphertextType.cast(type)
            // Returns Pkmsg for PreKeySignalMessage, Msg for SignalMessage
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

            // ADAPTED: WAWebSignalSessionApi.maybeDeleteUnconvertedSession cleans up
            // unconverted sessions only; Cobalt removes the full session since
            // the unified store does not distinguish converted vs unconverted sessions
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
     * @param senderJid the sender's device JID (PN or LID based on addressing mode)
     * @param plaintext the plaintext message bytes (already protobuf-encoded)
     * @return the encrypted payload (always SKMSG type)
     * @throws WhatsAppMessageException.Send if encryption fails
     *
     * @implNote WAWebEncryptMsgProtobuf.encryptMsgSenderKey: receives
     * {@code (msg, groupJid, paddedContent, groupInfo)}, determines sender
     * as LID or PN based on {@code isCagAddon || isLidAddressingMode}, then
     * calls {@code WAWebSignal.Cipher.encryptSenderKeyMsgSignalProto(groupJid, senderJid, content)}.
     * In Cobalt, the caller resolves the sender JID and handles protobuf encoding;
     * this method handles only padding and encryption.
     * In WA Web the return includes {@code senderKeyBytes}; in Cobalt, sender key
     * bytes are obtained separately via {@link #getSenderKeyBytes(Jid, Jid)}.
     * WAM metrics ({@code E2eMessageSendWamEvent}) are intentionally skipped.
     */
    public MessageEncryptedPayload encryptForGroup(Jid groupJid, Jid senderJid, byte[] plaintext) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        // ADAPTED: WAWebSendGroupSkmsgJob calls encodeAndPad before encryptMsgSenderKey;
        // in Cobalt the caller encodes, we pad here
        var paddedPlaintext = addPadding(plaintext);

        // WAWebEncryptMsgProtobuf.encryptMsgSenderKey: sender JID is based on
        // addressing mode (isCagAddon || isLidAddressingMode -> LID, else PN)
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);

        try {
            // WAWebSignal.Cipher.encryptSenderKeyMsgSignalProto -> WAWebCryptoLibrary.encryptSenderKeyMsgSignalProto
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
            // WAWebEncryptMsgProtobuf.encryptMsgSenderKey: logs and rejects on failure
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
     * The padding length is random between 1 and 16 bytes.
     * All padding bytes contain the padding length value.
     *
     * @param plaintext the original plaintext bytes
     * @return the padded plaintext
     *
     * @implNote WACryptoPkcs7.writeRandomPadMax16: generates a random byte,
     * masks with {@code 0x0F} (range 0-15), adds 1 (range 1-16), then writes
     * that many copies of the padding length. Called from
     * WAWebSignalCommonUtils.writeRandomPadMax16 and
     * WAWebSendMsgCommonApi.encodeAndPad.
     */
    private static byte[] addPadding(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        // Generate random padding length between 1 and 16
        var paddingLength = MIN_PADDING + (FastRandomUtils.randomByteArray(1)[0] & 0x0F);

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
     * @implNote WAWebSignalSessionApi.getGroupSenderKeyInfo: creates or retrieves
     * the sender key session for the group/sender pair.
     * WAWebGetGroupKeyDistributionMsg.getKeyDistributionMsg: builds
     * the distribution message to send to other participants.
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
     * @implNote WAWebCryptoLibrary.encryptSenderKeyMsgSignalProto: returns
     * {@code senderKeyBytes} alongside ciphertext.
     * WAWebGetGroupKeyDistributionMsg: extracts
     * {@code axolotlSenderKeyDistributionMessage} for ICDC construction.
     * In Cobalt, sender key bytes are obtained separately from encryption.
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
     * @implNote WAWebSignalSessionApi.deleteGroupSenderKeyInfo: deletes
     * the sender key session for the group/sender pair from the
     * signal protocol store.
     */
    public void rotateSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        // WAWebSignal.Session.deleteGroupSenderKeyInfo(groupJid, senderJid)
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        store.removeSenderKeys(senderKeyName);

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
     * @implNote WAWebSignalSessionApi.hasSignalSessions: checks
     * the signal protocol store for an existing session with the device.
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
     * @implNote WAWebApiParticipantStore: tracks {@code hasSenderKey} status
     * per participant. In Cobalt, the sender key store is queried directly.
     */
    public boolean hasSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return store.findSenderKeyByName(senderKeyName).isPresent();
    }

}
