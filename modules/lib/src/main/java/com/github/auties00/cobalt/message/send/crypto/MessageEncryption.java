package com.github.auties00.cobalt.message.send.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.receive.crypto.SenderKeyNameFactory;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import com.github.auties00.libsignal.protocol.SignalSenderKeyDistributionMessage;

import java.util.Objects;

/**
 * Encrypts outbound message protobufs into Signal envelopes for per-device
 * fanout or group sender-key delivery.
 *
 * @apiNote
 * Sending counterpart of {@link MessageDecryption}: every outgoing
 * {@link com.github.auties00.cobalt.model.message.MessageContainer} that
 * leaves the client passes through {@link #encryptForDevice} (1:1 and
 * companion fanout, matching WA Web's {@code WAWebEncryptMsgProtobuf.encryptMsgProtobuf})
 * or {@link #encryptForGroup} (SKMSG, matching {@code encryptMsgSenderKey}).
 * Held by the stanza-build pipeline as an injected service; embedders that
 * speak Signal directly do not normally call it.
 */
@WhatsAppWebModule(moduleName = "WAWebEncryptMsgProtobuf")
@WhatsAppWebModule(moduleName = "WAWebBackendJobsCommon")
@WhatsAppWebModule(moduleName = "WAWebSignalCipherApi")
@WhatsAppWebModule(moduleName = "WAWebSignalSessionApi")
@WhatsAppWebModule(moduleName = "WAWebCryptoLibrary")
@WhatsAppWebModule(moduleName = "WASignalGroupCipher")
public final class MessageEncryption {
    /**
     * Logger for encryption diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageEncryption.class.getName());

    /**
     * The ciphertext format version stamped on every outgoing {@code <enc>}
     * stanza.
     *
     * @apiNote
     * Maps to the {@code v="2"} attribute the fanout stanza writer adds to
     * every encrypted child element. Bumped only in lockstep with the WA Web
     * bundle; embedders should treat this as a read-only wire constant.
     */
    @WhatsAppWebExport(moduleName = "WAWebBackendJobsCommon", exports = "CIPHERTEXT_VERSION",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int CIPHERTEXT_VERSION = 2;

    /**
     * The store consulted for Signal session and sender-key lookups.
     */
    private final WhatsAppStore store;

    /**
     * The per-device Signal session cipher used by {@link #encryptForDevice}.
     */
    private final SignalSessionCipher sessionCipher;

    /**
     * The sender-key group cipher used by {@link #encryptForGroup} and
     * {@link #createSenderKeyDistributionMessage}.
     */
    private final SignalGroupCipher groupCipher;

    /**
     * The minimum number of random padding bytes appended to a plaintext
     * before encryption.
     */
    private static final int MIN_PADDING = 1;

    /**
     * The maximum number of random padding bytes appended to a plaintext
     * before encryption.
     */
    private static final int MAX_PADDING = 16;

    /**
     * Constructs an encryption service bound to the given Signal dependencies.
     *
     * @apiNote
     * The same {@link WhatsAppStore} must be shared with the
     * {@link SignalSessionCipher} and {@link SignalGroupCipher} so session
     * lookups during encrypt-then-cleanup observe the same backing state.
     *
     * @param store         the store providing Signal protocol state
     * @param sessionCipher the cipher used for {@link #encryptForDevice}
     * @param groupCipher   the cipher used for {@link #encryptForGroup} and
     *                      {@link #createSenderKeyDistributionMessage}
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptMsgProtobuf", exports = {"encryptMsgProtobuf", "encryptMsgSenderKey"},
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Encrypts the given plaintext for a specific recipient device.
     *
     * @apiNote
     * Used by the per-device fanout writer to produce one
     * {@link MessageEncryptedPayload} per recipient address; the result is a
     * {@code PreKeySignalMessage} when the Signal session is freshly
     * established and a {@code SignalMessage} once the recipient has decrypted
     * at least one PKMSG. Throwing here aborts the per-device branch for that
     * recipient; the caller decides whether the recipient is critical (a
     * primary device, in which case the whole send fails) or skippable (a
     * companion). The plaintext input is the protobuf-encoded
     * {@link com.github.auties00.cobalt.model.message.MessageContainer}.
     * @implNote
     * This implementation cleans up the failing Signal session on encryption
     * error (mirroring WA Web's {@code maybeDeleteUnconvertedSession}) so a
     * later retry on the same address triggers a fresh PreKey exchange rather
     * than re-using the stale session record.
     *
     * @param recipientJid the recipient device {@link Jid}
     * @param plaintext    the protobuf-encoded plaintext bytes
     * @return the encrypted payload tagged with its envelope type
     * @throws NullPointerException                  if any argument is
     *                                               {@code null}
     * @throws WhatsAppMessageException.Send.Unknown if encryption fails
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptMsgProtobuf", exports = "encryptMsgProtobuf",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSignalCipherApi", exports = "encryptSignalProto",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "encryptSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageEncryptedPayload encryptForDevice(Jid recipientJid, byte[] plaintext) {
        Objects.requireNonNull(recipientJid, "recipientJid cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        var paddedPlaintext = addPadding(plaintext);
        var address = recipientJid.toSignalAddress();

        try {
            var ciphertextMessage = sessionCipher.encrypt(address, paddedPlaintext);
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
            LOGGER.log(System.Logger.Level.WARNING,
                    "encryptMsgProtobuf: encryption fail for {0}: {1}",
                    recipientJid, e.getMessage());

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
     * Encrypts the given plaintext for a group using sender-key encryption.
     *
     * @apiNote
     * Used by the group send pipeline (matching WA Web's
     * {@code WAWebSendGroupSkmsgJob}) to produce one SKMSG ciphertext that is
     * delivered once to the group; every member who already holds the
     * sender's {@link SignalSenderKeyDistributionMessage} can decrypt it
     * locally. Members without the distribution need to receive it first via
     * {@link com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution#encrypt}.
     * @implNote
     * This implementation lazily bootstraps the sender-key state via
     * {@link SignalGroupCipher#create} when the store does not already hold
     * one for this sender; WA Web does the same lookup through
     * {@code getGroupSenderKeyInfo}.
     *
     * @param groupJid  the group {@link Jid}
     * @param senderJid the sender device {@link Jid} (PN or LID depending on
     *                  the group's addressing mode)
     * @param plaintext the protobuf-encoded plaintext bytes
     * @return the SKMSG-typed encrypted payload (with
     *         {@link MessageEncryptedPayload#recipientJid()} {@code null})
     * @throws NullPointerException                  if any argument is
     *                                               {@code null}
     * @throws WhatsAppMessageException.Send.Unknown if encryption fails
     */
    @WhatsAppWebExport(moduleName = "WAWebEncryptMsgProtobuf", exports = "encryptMsgSenderKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSignalCipherApi", exports = "encryptSenderKeyMsgSignalProto",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "encryptSenderKeyMsgSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASignalGroupCipher", exports = "encryptSenderKeyMsgWithSession",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageEncryptedPayload encryptForGroup(Jid groupJid, Jid senderJid, byte[] plaintext) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        var paddedPlaintext = addPadding(plaintext);
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);

        if (store.findSenderKeyByName(senderKeyName).isEmpty()) {
            groupCipher.create(senderKeyName);
        }

        try {
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
            LOGGER.log(System.Logger.Level.WARNING,
                    "encryptMsgSenderKey: encryption fail for {0}: {1}",
                    groupJid, e.getMessage());
            throw new WhatsAppMessageException.Send.Unknown(
                    "Failed to encrypt group message for group: " + groupJid, e
            );
        }
    }

    /**
     * Appends {@value #MIN_PADDING} to {@value #MAX_PADDING} random padding
     * bytes to the supplied plaintext.
     *
     * @apiNote
     * Mirrors WA Web's {@code writeRandomPadMax16}: every padding byte carries
     * the padding length value so the recipient can recover the original
     * length by reading the last byte. Required before encryption to mask the
     * plaintext length distribution.
     * @implNote
     * This implementation samples the padding length from the low four bits of
     * a single random byte and adds {@value #MIN_PADDING}, yielding the
     * inclusive range {@code [1, 16]} matching WA Web's pad distribution.
     *
     * @param plaintext the unpadded plaintext bytes
     * @return the padded plaintext bytes
     * @throws NullPointerException if {@code plaintext} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi", exports = "encodeAndPad",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSignalCommonUtils", exports = "writeRandomPadMax16",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WACryptoPkcs7", exports = "writeRandomPadMax16",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static byte[] addPadding(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        var paddingLength = MIN_PADDING + (DataUtils.randomByteArray(1)[0] & 0x0F);

        var padded = new byte[plaintext.length + paddingLength];
        System.arraycopy(plaintext, 0, padded, 0, plaintext.length);

        for (var i = plaintext.length; i < padded.length; i++) {
            padded[i] = (byte) paddingLength;
        }

        return padded;
    }

    /**
     * Creates and returns the sender-key distribution message for the given
     * group/sender pair.
     *
     * @apiNote
     * The distribution message must be delivered, via per-device pkmsg/msg
     * encryption, to every group member that does not yet hold the sender's
     * key before they can decrypt any SKMSG produced by
     * {@link #encryptForGroup}. Counterpart of WA Web's
     * {@code Session.getGroupSenderKeyInfo}.
     *
     * @param groupJid  the group {@link Jid}
     * @param senderJid the sender device {@link Jid}
     * @return the sender-key distribution message
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalSessionApi", exports = "getGroupSenderKeyInfo",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASignalGroupCipher", exports = "createSenderKeyDistributionProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public SignalSenderKeyDistributionMessage createSenderKeyDistributionMessage(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return groupCipher.create(senderKeyName);
    }

    /**
     * Returns the serialised sender-key distribution bytes for the given
     * group/sender pair.
     *
     * @apiNote
     * Convenience wrapper over
     * {@link #createSenderKeyDistributionMessage(Jid, Jid)} that hands the
     * already-serialised proto straight to the per-device encryption path in
     * {@link com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution#encrypt}.
     *
     * @param groupJid  the group {@link Jid}
     * @param senderJid the sender device {@link Jid}
     * @return the serialised {@code SenderKeyDistributionMessage} bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebGetGroupKeyDistributionMsg", exports = "getKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public byte[] getSenderKeyBytes(Jid groupJid, Jid senderJid) {
        var distributionMessage = createSenderKeyDistributionMessage(groupJid, senderJid);
        return distributionMessage.toSerialized();
    }

    /**
     * Removes the sender key for the given group/sender pair, forcing it to be
     * regenerated on the next send.
     *
     * @apiNote
     * Called after a participant change (member removed, added, or rotated)
     * invalidates the existing sender key, so the next {@link #encryptForGroup}
     * call lazily creates a fresh distribution and re-fans it out via
     * {@link com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution#encrypt}.
     * Counterpart of WA Web's {@code Session.deleteGroupSenderKeyInfo}.
     *
     * @param groupJid  the group {@link Jid}
     * @param senderJid the sender device {@link Jid}
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalSessionApi", exports = "deleteGroupSenderKeyInfo",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void rotateSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        store.removeSenderKeys(senderKeyName);

        LOGGER.log(System.Logger.Level.DEBUG,
                "Rotated sender key for group {0}, sender {1}",
                groupJid, senderJid);
    }

    /**
     * Returns whether a Signal session already exists with the given device.
     *
     * @apiNote
     * Used by the fanout planner to decide whether a per-device send needs to
     * be preceded by a PreKey exchange (no session) or can proceed directly
     * (session present). Counterpart of WA Web's {@code hasSignalSessions}.
     *
     * @param deviceJid the device {@link Jid} to query
     * @return {@code true} when a Signal session exists with
     *         {@code deviceJid}
     * @throws NullPointerException if {@code deviceJid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalSessionApi", exports = "hasSignalSessions",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean hasSessionWith(Jid deviceJid) {
        Objects.requireNonNull(deviceJid, "deviceJid cannot be null");
        var address = deviceJid.toSignalAddress();
        return store.findSessionByAddress(address).isPresent();
    }

    /**
     * Returns whether a sender key already exists for the given group/sender
     * pair.
     *
     * @apiNote
     * Used by the group send planner to skip the distribution-fanout step when
     * the sender key is already established for every recipient; the
     * complementary {@link #rotateSenderKey} clears this state when the group
     * membership changes.
     *
     * @param groupJid  the group {@link Jid}
     * @param senderJid the sender device {@link Jid}
     * @return {@code true} when a sender key is present for this pair
     * @throws NullPointerException if any argument is {@code null}
     */
    public boolean hasSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return store.findSenderKeyByName(senderKeyName).isPresent();
    }

}
