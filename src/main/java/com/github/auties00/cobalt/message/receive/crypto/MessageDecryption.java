package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.send.bot.BotMessageSecret;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.SecretMessageContainerSpec;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.exception.*;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import com.github.auties00.libsignal.protocol.SignalMessage;
import com.github.auties00.libsignal.protocol.SignalPreKeyMessage;
import com.github.auties00.libsignal.protocol.SignalSenderKeyDistributionMessage;
import it.auties.protobuf.exception.ProtobufDeserializationException;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for decrypting incoming messages using the Signal Protocol
 * and the MSMSG bot message scheme.
 *
 * <p>Handles three decryption paths:
 * <ul>
 *   <li><b>Per-device</b> (PKMSG/MSG) -- Signal session cipher for
 *       1:1 messages via {@link #decryptFromDevice}</li>
 *   <li><b>Group</b> (SKMSG) -- Signal sender-key cipher for group
 *       messages via {@link #decryptFromGroup}</li>
 *   <li><b>Bot</b> (MSMSG) -- AES-GCM with HKDF-derived key from
 *       the target message's {@code messageSecret} via
 *       {@link #decryptBotMessage}</li>
 * </ul>
 *
 * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc: dispatches to
 * WAWebSignal.Cipher.decryptSignalProto (PKMSG/MSG),
 * WAWebSignal.Cipher.decryptGroupSignalProto (SKMSG),
 * or WAWebBotMessageSecret.decryptMsmsgBotMessage (MSMSG).
 * In Cobalt, the dispatch is done by the caller rather than a single
 * dispatch method.
 */
public final class MessageDecryption {
    /**
     * Minimum valid padding length.
     *
     * @implNote WACryptoPkcs7.unpad: padding byte must be in range [1, 16].
     */
    private static final int MIN_PADDING = 1;

    /**
     * Maximum valid padding length.
     *
     * @implNote WACryptoPkcs7.unpad: padding byte must be in range [1, 16].
     */
    private static final int MAX_PADDING = 16;

    /**
     * Size of the HKDF-derived key in bytes for MSMSG decryption.
     *
     * @implNote WAWebBotMessageSecret: module-level constant
     * {@code c = 32}, used as the HKDF output length.
     */
    private static final int HKDF_KEY_SIZE = 32;

    /**
     * AES-GCM tag size in bits for MSMSG decryption.
     *
     * @implNote WAWebBotMessageSecret: AES-GCM uses a 128-bit tag
     * via {@code WACryptoAesGcm.gcmDecrypt}.
     */
    private static final int AES_GCM_TAG_BITS = 128;

    /**
     * The HKDF algorithm identifier for MSMSG key derivation.
     *
     * @implNote WAWebBotMessageSecret: uses {@code WACryptoHkdf}
     * which is HKDF-SHA-256.
     */
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * The AES-GCM algorithm identifier for MSMSG decryption.
     *
     * @implNote WAWebBotMessageSecret: uses {@code WACryptoAesGcm.gcmDecrypt}
     * which is AES/GCM/NoPadding.
     */
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * The WhatsApp store for session and sender key lookups.
     *
     * @implNote ADAPTED: WAWebMsgProcessingDecryptEnc accesses
     * WAWebSignalProtocolStore and other stores implicitly via module imports.
     */
    private final WhatsAppStore store;

    /**
     * The Signal session cipher for per-device decryption.
     *
     * @implNote ADAPTED: WAWebMsgProcessingDecryptEnc delegates to
     * WAWebSignal.Cipher which wraps WAWebCryptoLibrary.
     */
    private final SignalSessionCipher sessionCipher;

    /**
     * The Signal group cipher for sender-key decryption.
     *
     * @implNote ADAPTED: WAWebMsgProcessingDecryptEnc delegates to
     * WAWebSignal.Cipher which wraps WAWebCryptoLibrary.
     */
    private final SignalGroupCipher groupCipher;

    /**
     * Constructs a new {@code MessageDecryption} service with the
     * required cryptographic dependencies.
     *
     * @param store         the WhatsApp store for session and key lookups
     * @param sessionCipher the Signal session cipher for 1:1 decryption
     * @param groupCipher   the Signal group cipher for group decryption
     *
     * @implNote ADAPTED: WAWebMsgProcessingDecryptEnc.decryptEnc uses
     * module-level imports for WAWebSignal.Cipher and WAWebBotMessageSecret;
     * Cobalt uses constructor-based DI instead.
     */
    public MessageDecryption(
            WhatsAppStore store,
            SignalSessionCipher sessionCipher,
            SignalGroupCipher groupCipher
    ) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.sessionCipher = Objects.requireNonNull(sessionCipher, "sessionCipher cannot be null");
        this.groupCipher = Objects.requireNonNull(groupCipher, "groupCipher cannot be null");
    }

    /**
     * Decrypts a message from a specific device using Signal Protocol.
     * Handles both PreKeySignalMessage (pkmsg) for new sessions and
     * SignalMessage (msg) for existing sessions.
     *
     * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc PKMSG/MSG branch:
     * determines sender as {@code a.isUser() ? a : participant}, then calls
     * {@code WAWebSignal.Cipher.decryptSignalProto(sender, e2eType, ciphertext, omitPersist, scope)}.
     * In Cobalt, the caller resolves the sender JID before calling this method.
     *
     * @param ciphertext     the encrypted message bytes
     * @param senderJid      the sender's device JID
     * @param encryptionType the type of encryption (PKMSG or MSG)
     * @return the decrypted plaintext (padding removed)
     * @throws WhatsAppMessageException.Receive if decryption fails
     */
    public byte[] decryptFromDevice(byte[] ciphertext, Jid senderJid, MessageEncryptionType encryptionType) {
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(encryptionType, "encryptionType cannot be null");

        var address = senderJid.toSignalAddress();
        return switch (encryptionType) {
            case PKMSG -> {
                try {
                    var message = SignalPreKeyMessage.ofSerialized(ciphertext);
                    var paddedPlaintext = sessionCipher.decrypt(address, message);
                    yield removePadding(paddedPlaintext);
                } catch (ProtobufDeserializationException e) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Invalid PreKeySignalMessage format from: " + senderJid, e);
                } catch (SignalMissingSessionException e) {
                    // This shouldn't happen for pkmsg since it establishes a session,
                    // but handle it anyway for robustness
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "No session for PreKeyMessage from: " + senderJid, false, e);
                } catch (SignalUninitializedSessionException e) {
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "Session not initialized for: " + senderJid, false, e);
                } catch (SignalUntrustedIdentityException e) {
                    throw new WhatsAppMessageException.Receive.InvalidSignature(
                            "Identity key changed for: " + senderJid, e);
                } catch (SignalDecryptException e) {
                    // Check for duplicate message (old counter) in error message
                    if (isDuplicateCounterError(e)) {
                        throw new WhatsAppMessageException.Receive.DuplicateMessage(
                                "Decryption failed for PreKeyMessage from: " + senderJid, e);
                    }
                    throw new WhatsAppMessageException.Receive.Unknown(
                            "Decryption failed for PreKeyMessage from: " + senderJid, e);
                } catch (SecurityException e) {
                    // Bad MAC or signature verification failure
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Security verification failed for message from: " + senderJid, e);
                }
            }
            case MSG -> {
                try {
                    var message = SignalMessage.ofSerialized(ciphertext);
                    var paddedPlaintext = sessionCipher.decrypt(address, message);
                    yield removePadding(paddedPlaintext);
                } catch (ProtobufDeserializationException e) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Invalid SignalMessage format from: " + senderJid, e);
                } catch (SignalMissingSessionException e) {
                    // MSG type requires existing session - send retry so sender re-sends as pkmsg
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "No session exists for MSG from: " + senderJid, false, e);
                } catch (SignalUninitializedSessionException e) {
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "Session not initialized for: " + senderJid, false, e);
                } catch (SignalUntrustedIdentityException e) {
                    throw new WhatsAppMessageException.Receive.InvalidSignature(
                            "Identity key changed for: " + senderJid, e);
                } catch (SignalDecryptException e) {
                    if (isDuplicateCounterError(e)) {
                        throw new WhatsAppMessageException.Receive.DuplicateMessage(
                                "Decryption failed for MSG from: " + senderJid, e);
                    }
                    throw new WhatsAppMessageException.Receive.Unknown(
                            "Decryption failed for MSG from: " + senderJid, e);
                } catch (SecurityException e) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Security verification failed for message from: " + senderJid, e);
                }
            }
            case SKMSG -> throw new IllegalArgumentException("Use decryptFromGroup for SKMSG encryption type");
            case MSMSG -> throw new IllegalArgumentException("Use decryptBotMessage for MSMSG encryption type");
        };
    }

    /**
     * Checks if a {@link SignalDecryptException} is due to a duplicate
     * message counter (old counter error).
     * This happens when we receive a message with a counter we have
     * already seen.
     *
     * @param e the decrypt exception to inspect
     * @return {@code true} if the error indicates a duplicate counter
     *
     * @implNote WAWebCryptoLibrary.decryptSignalProto and
     * WAWebCryptoLibrary.decryptGroupSignalProto: distinguish
     * {@code errDuplicateMsg} from generic {@code SignalDecryptionError}.
     * Cobalt maps this to {@code SignalDecryptException} message content.
     */
    private boolean isDuplicateCounterError(SignalDecryptException e) {
        var message = e.getMessage();
        return message != null && (
                message.contains("old counter") ||
                message.contains("Received message with old counter")
        );
    }

    /**
     * Decrypts a group message using Sender Key encryption.
     *
     * <p>Signal Exception Mapping:
     * <ul>
     *   <li>{@code SignalMissingSenderKeyException} -- NoSenderKey (no sender key record)</li>
     *   <li>{@code SignalMissingSenderKeyStateException} -- InvalidSenderKey (key exists but no state for ID)</li>
     *   <li>{@code SignalDecryptException} -- Unknown or DuplicateMessage</li>
     *   <li>{@code SecurityException} -- InvalidSenderKey (signature verification failed)</li>
     * </ul>
     *
     * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc SKMSG branch:
     * validates {@code a.isGroup() || a.isBroadcast()} and requires a
     * participant, then calls
     * {@code WAWebSignal.Cipher.decryptGroupSignalProto(chatJid, participant, ciphertext)}.
     *
     * @param ciphertext the encrypted message bytes
     * @param groupJid   the group JID
     * @param senderJid  the sender's device JID
     * @return the decrypted plaintext (padding removed)
     * @throws WhatsAppMessageException.Receive if decryption fails
     */
    public byte[] decryptFromGroup(byte[] ciphertext, Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);

        try {
            // groupCipher.decrypt() takes raw bytes and deserializes internally
            var paddedPlaintext = groupCipher.decrypt(senderKeyName, ciphertext);
            return removePadding(paddedPlaintext);
        } catch (SignalMissingSenderKeyException e) {
            // No sender key record exists at all - sender should re-distribute
            throw new WhatsAppMessageException.Receive.NoSenderKey(
                    "No sender key exists for group: " + groupJid + " sender: " + senderJid, e);
        } catch (SignalMissingSenderKeyStateException e) {
            // Sender key record exists but no state for the message's key ID
            // This can happen if the sender rotated their key and we have an old one
            throw new WhatsAppMessageException.Receive.InvalidSenderKey(
                    "Sender key state not found for ID " + e.id().orElse(-1) +
                            " in group: " + groupJid + " sender: " + senderJid, e);
        } catch (SignalDecryptException e) {
            if (isDuplicateCounterError(e)) {
                throw new WhatsAppMessageException.Receive.DuplicateMessage(
                        "Group decryption failed for message from: " + senderJid + " in group: " + groupJid, e);
            }
            throw new WhatsAppMessageException.Receive.Unknown(
                    "Group decryption failed for message from: " + senderJid + " in group: " + groupJid, e);
        } catch (SecurityException e) {
            // Signature verification failed on the sender key message
            throw new WhatsAppMessageException.Receive.InvalidSenderKey(
                    "Sender key signature verification failed from: " + senderJid + " in group: " + groupJid, e);
        } catch (ProtobufDeserializationException e) {
            throw new WhatsAppMessageException.Receive.InvalidMessage(
                    "Invalid SenderKeyMessage format from: " + senderJid + " in group: " + groupJid, e);
        }
    }

    /**
     * Decrypts an MSMSG bot message payload.
     *
     * <p>The ciphertext is a serialized {@code MessageSecretMessage}
     * protobuf containing {@code encIv} and {@code encPayload} fields.
     * The decryption key is derived from the provided
     * {@code messageSecret} using HKDF-SHA256 with the message ID and
     * sender JIDs as context.
     *
     * <p>The caller is responsible for resolving the {@code messageId}
     * (from bot edit target ID or stanza ID), looking up the
     * {@code messageSecret} from the target message, and resolving
     * the sender JIDs from the stanza addressing.
     *
     * @param ciphertext      the MSMSG ciphertext (MessageSecretMessage protobuf)
     * @param messageSecret   the 32-byte secret from the target message
     * @param messageId       the message ID for key derivation and AAD
     *                        (bot edit target ID or stanza ID)
     * @param targetSenderJid the target message sender's user JID
     * @param botSenderJid    the bot's user JID
     * @return the decrypted inner plaintext
     * @throws WhatsAppMessageException.Receive if decryption fails
     *
     * @implNote WAWebBotMessageSecret.decryptMsmsgBotMessage: decodes
     * the ciphertext as {@code MessageSecretMessageSpec}, derives the
     * bot secret via {@code genBotMsgSecretFromMsgSecret}, then derives
     * the per-message AES-GCM key via function S() with
     * {@code extractAndExpand(botSecret, info, 32)}, and decrypts with
     * {@code WACryptoAesGcm.gcmDecrypt(key, encIv, encPayload, aad)}
     * where AAD = {@code messageId + "\0" + botSenderJid}.
     * In Cobalt, the caller resolves the messageSecret and sender JIDs
     * before calling this method.
     */
    public byte[] decryptBotMessage(
            byte[] ciphertext,
            byte[] messageSecret,
            String messageId,
            Jid targetSenderJid,
            Jid botSenderJid
    ) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        Objects.requireNonNull(messageSecret, "messageSecret");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(targetSenderJid, "targetSenderJid");
        Objects.requireNonNull(botSenderJid, "botSenderJid");

        try {
            var secretMessage = SecretMessageContainerSpec.decode(ciphertext); // WAWebBotMessageSecret.decryptMsmsgBotMessage
            var encIv = secretMessage.encIv().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidMessage(
                            "MSMSG missing encIv", null));
            var encPayload = secretMessage.encPayload().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidMessage(
                            "MSMSG missing encPayload", null));

            var botSecret = BotMessageSecret.derive(messageSecret);
            var aesKey = deriveBotPerMessageKey(
                    messageId, targetSenderJid, botSenderJid, botSecret);
            var aad = buildBotAad(messageId, botSenderJid);

            var cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            var keySpec = new SecretKeySpec(aesKey, "AES");
            var gcmSpec = new GCMParameterSpec(AES_GCM_TAG_BITS, encIv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);
            return cipher.doFinal(encPayload);
        } catch (WhatsAppMessageException.Receive e) {
            throw e;
        } catch (Exception e) {
            throw new WhatsAppMessageException.Receive.Unknown(
                    "MSMSG decryption failed", e);
        }
    }

    /**
     * Removes padding from a decrypted message.
     * Reads the last byte to determine padding length and removes it.
     *
     * @implNote WAWebHandleMsgProcess.processDecryptedMessageProto:
     * uses {@code WACryptoPkcs7.unpad} to strip padding from the
     * decrypted plaintext before protobuf decoding.
     *
     * @param paddedPlaintext the padded plaintext bytes
     * @return the original plaintext without padding
     * @throws IllegalArgumentException if the padding is invalid
     */
    private static byte[] removePadding(byte[] paddedPlaintext) {
        Objects.requireNonNull(paddedPlaintext, "paddedPlaintext cannot be null");

        if (paddedPlaintext.length == 0) {
            throw new IllegalArgumentException("Padded plaintext cannot be empty");
        }

        // Last byte indicates padding length
        var paddingLength = paddedPlaintext[paddedPlaintext.length - 1] & 0xFF;

        if (paddingLength < MIN_PADDING || paddingLength > MAX_PADDING) {
            throw new IllegalArgumentException(
                    "Invalid padding length: " + paddingLength + " (expected " + MIN_PADDING + "-" + MAX_PADDING + ")"
            );
        }

        if (paddingLength > paddedPlaintext.length) {
            throw new IllegalArgumentException(
                    "Padding length " + paddingLength + " exceeds message length " + paddedPlaintext.length
            );
        }

        var originalLength = paddedPlaintext.length - paddingLength;
        return Arrays.copyOf(paddedPlaintext, originalLength);
    }


    /**
     * Processes a received sender key distribution message,
     * storing the sender key for future group message decryption.
     *
     * @implNote WAWebCryptoLibrary.processSenderKeyDistributionMsg:
     * calls {@code saveSenderKeySession(loadSenderKeySession, saveSenderKeySession,
     * groupJid, deviceJid, distributionData)}.
     *
     * @param groupJid        the group JID
     * @param senderJid       the sender's device JID
     * @param distributionMsg the sender key distribution message
     */
    public void processSenderKeyDistribution(Jid groupJid, Jid senderJid, SignalSenderKeyDistributionMessage distributionMsg) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(distributionMsg, "distributionMsg cannot be null");
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        groupCipher.process(senderKeyName, distributionMsg);
    }

    /**
     * Processes a received sender key distribution message from raw bytes.
     *
     * @implNote WAWebCryptoLibrary.processSenderKeyDistributionMsg:
     * takes raw distribution bytes and processes them via
     * {@code saveSenderKeySession}.
     *
     * @param groupJid         the group JID
     * @param senderJid        the sender's device JID
     * @param distributionData the raw distribution message bytes
     */
    public void processSenderKeyDistribution(Jid groupJid, Jid senderJid, byte[] distributionData) {
        Objects.requireNonNull(distributionData, "distributionData cannot be null");
        var distributionMsg = SignalSenderKeyDistributionMessage.ofSerialized(distributionData);
        processSenderKeyDistribution(groupJid, senderJid, distributionMsg);
    }

    /**
     * Checks if a Signal session exists with the specified device.
     *
     * @implNote ADAPTED: WAWebCryptoLibrary.getRemoteRegId loads a
     * session to check existence; Cobalt queries the store directly.
     *
     * @param deviceJid the device JID to check
     * @return {@code true} if a session exists, {@code false} otherwise
     */
    public boolean hasSessionWith(Jid deviceJid) {
        Objects.requireNonNull(deviceJid, "deviceJid cannot be null");
        var address = deviceJid.toSignalAddress();
        return store.findSessionByAddress(address).isPresent();
    }

    /**
     * Checks if a sender key exists for the specified group and sender.
     *
     * @implNote ADAPTED: WAWebCryptoLibrary stores sender keys via
     * {@code loadSenderKeySession}; Cobalt queries the store directly.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @return {@code true} if a sender key exists, {@code false} otherwise
     */
    public boolean hasSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return store.findSenderKeyByName(senderKeyName).isPresent();
    }

    /**
     * Extracts the sender's identity key from a PreKeySignalMessage ciphertext.
     * This is used for ADV (Account Device Verification) validation before decryption.
     *
     * <p>The PreKeySignalMessage structure contains the sender's identity key
     * which can be extracted without decrypting the message. This is used to
     * verify ADV signatures for companion devices before proceeding with decryption.
     *
     * @param ciphertext the PKMSG ciphertext bytes
     * @return the identity key bytes (32 bytes), or empty if extraction fails
     *
     * @implNote WAWebSignalUtilsApi.extractIdentityKey: extracts identity key from
     * PreKeySignalMessage for ADV validation.
     */
    public Optional<byte[]> extractIdentityKeyFromPkmsg(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length == 0) {
            return Optional.empty();
        }

        try {
            var message = SignalPreKeyMessage.ofSerialized(ciphertext);
            var identityKey = message.identityKey();
            if (identityKey == null) {
                return Optional.empty();
            }
            return Optional.of(identityKey.toEncodedPoint());
        } catch (ProtobufDeserializationException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Derives the per-message AES-GCM key using HKDF-SHA256.
     *
     * <p>Performs HKDF-Extract with a null (all-zero) salt over the
     * {@code botSecret}, then HKDF-Expand with info constructed as:
     * {@code messageId || targetSenderJid || botSenderJid}.
     *
     * @param messageId       the message ID (or bot edit target ID)
     * @param targetSenderJid the target message sender's user JID
     * @param botSenderJid    the bot's user JID
     * @param botSecret       the base bot secret derived from messageSecret
     * @return the 32-byte AES-GCM key
     *
     * @implNote WAWebBotMessageSecret function S(): calls
     * {@code WACryptoHkdf.extractAndExpand(new Uint8Array(botSecret), info, 32)}
     * where info = {@code Binary.build(externalId, targetSenderJid, botSenderJid)}.
     * {@code extractAndExpand} performs HKDF-Extract with null salt followed
     * by HKDF-Expand.
     */
    private byte[] deriveBotPerMessageKey(
            String messageId,
            Jid targetSenderJid,
            Jid botSenderJid,
            byte[] botSecret
    ) throws GeneralSecurityException {
        var idBytes = messageId.getBytes(StandardCharsets.UTF_8);
        var targetBytes = targetSenderJid.toString().getBytes(StandardCharsets.UTF_8);
        var botBytes = botSenderJid.toString().getBytes(StandardCharsets.UTF_8);

        // WAWebBotMessageSecret: Binary.build(externalId, targetSenderJid, botSenderJid)
        var info = new byte[idBytes.length + targetBytes.length + botBytes.length];
        System.arraycopy(idBytes, 0, info, 0, idBytes.length);
        System.arraycopy(targetBytes, 0, info, idBytes.length, targetBytes.length);
        System.arraycopy(botBytes, 0, info, idBytes.length + targetBytes.length, botBytes.length);

        // WAWebBotMessageSecret: WACryptoHkdf.extractAndExpand(botSecret, info, 32)
        // extractAndExpand = extractSha256(null, ikm) then expand(prk, info, length)
        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        var params = HKDFParameterSpec.ofExtract()
                .addIKM(botSecret)
                .thenExpand(info, HKDF_KEY_SIZE);
        return kdf.deriveData(params);
    }

    /**
     * Builds the AAD (Additional Authenticated Data) for bot message
     * AES-GCM decryption.
     *
     * <p>Format: {@code messageId + "\0" + botSenderJid}
     *
     * @param messageId    the message ID
     * @param botSenderJid the bot's user JID
     * @return the AAD bytes
     *
     * @implNote WAWebBotMessageSecret.decryptMsmsgBotMessage: AAD =
     * {@code externalId + "\0" + botSenderJid} passed to
     * {@code WACryptoAesGcm.gcmDecrypt}.
     */
    private byte[] buildBotAad(String messageId, Jid botSenderJid) {
        var idBytes = messageId.getBytes(StandardCharsets.UTF_8);
        var botBytes = botSenderJid.toString().getBytes(StandardCharsets.UTF_8);
        var aad = new byte[idBytes.length + 1 + botBytes.length];
        System.arraycopy(idBytes, 0, aad, 0, idBytes.length);
        aad[idBytes.length] = 0x00;
        System.arraycopy(botBytes, 0, aad, idBytes.length + 1, botBytes.length);
        return aad;
    }
}
