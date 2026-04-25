package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.send.bot.BotMessageSecret;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
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
 * Decrypts incoming WhatsApp message payloads using the Signal
 * Protocol and the MSMSG bot message scheme.
 *
 * <p>This service exposes three decryption paths, each corresponding
 * to one of the encryption types used by the WhatsApp protocol:
 * <ul>
 *   <li><b>Per-device</b> (PKMSG/MSG): Signal session cipher for 1:1
 *       messages, via {@link #decryptFromDevice}.</li>
 *   <li><b>Group</b> (SKMSG): Signal sender-key cipher for group
 *       messages, via {@link #decryptFromGroup}.</li>
 *   <li><b>Bot</b> (MSMSG): AES-GCM with an HKDF-derived key from the
 *       target message's {@code messageSecret}, via
 *       {@link #decryptBotMessage}.</li>
 * </ul>
 * It also exposes helpers for processing incoming sender-key
 * distribution messages and for inspecting existing session state.
 *
 * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc: dispatches to
 * WAWebSignal.Cipher.decryptSignalProto (PKMSG/MSG),
 * WAWebSignal.Cipher.decryptGroupSignalProto (SKMSG), or
 * WAWebBotMessageSecret.decryptMsmsgBotMessage (MSMSG). In Cobalt
 * the dispatch is performed by the caller rather than by a single
 * dispatch method.
 */
@WhatsAppWebModule(moduleName = "WAWebMsgProcessingDecryptEnc")
@WhatsAppWebModule(moduleName = "WAWebSignalCipherApi")
@WhatsAppWebModule(moduleName = "WAWebBotMessageSecret")
@WhatsAppWebModule(moduleName = "WAWebCryptoLibrary")
public final class MessageDecryption {
    /**
     * The minimum valid PKCS#7 padding length.
     *
     * @implNote WACryptoPkcs7.unpad: padding byte must be in the
     * range {@code [1, 16]}.
     */
    private static final int MIN_PADDING = 1;

    /**
     * The maximum valid PKCS#7 padding length.
     *
     * @implNote WACryptoPkcs7.unpad: padding byte must be in the
     * range {@code [1, 16]}.
     */
    private static final int MAX_PADDING = 16;

    /**
     * Size of the HKDF-derived AES-GCM key in bytes for MSMSG
     * decryption.
     *
     * @implNote WAWebBotMessageSecret: module-level constant
     * {@code c = 32}, used as the HKDF output length.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int HKDF_KEY_SIZE = 32;

    /**
     * AES-GCM authentication tag size in bits for MSMSG decryption.
     *
     * @implNote WAWebBotMessageSecret: AES-GCM uses a 128-bit tag
     * via {@code WACryptoAesGcm.gcmDecrypt}.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int AES_GCM_TAG_BITS = 128;

    /**
     * The HKDF algorithm identifier for MSMSG key derivation.
     *
     * @implNote WAWebBotMessageSecret: uses {@code WACryptoHkdf}
     * which is HKDF-SHA-256.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * The AES-GCM transformation identifier for MSMSG decryption.
     *
     * @implNote WAWebBotMessageSecret: uses {@code WACryptoAesGcm.gcmDecrypt}
     * which is AES/GCM/NoPadding.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * The WhatsApp store used for session and sender-key lookups.
     *
     * @implNote WAWebMsgProcessingDecryptEnc accesses
     * WAWebSignalProtocolStore and related stores implicitly via
     * module imports; Cobalt injects the store through this field.
     */
    private final WhatsAppStore store;

    /**
     * The Signal session cipher used for per-device decryption.
     *
     * @implNote WAWebMsgProcessingDecryptEnc delegates to
     * WAWebSignal.Cipher which wraps WAWebCryptoLibrary; Cobalt uses
     * the {@code SignalSessionCipher} abstraction from libsignal.
     */
    private final SignalSessionCipher sessionCipher;

    /**
     * The Signal group cipher used for sender-key decryption.
     *
     * @implNote WAWebMsgProcessingDecryptEnc delegates to
     * WAWebSignal.Cipher which wraps WAWebCryptoLibrary; Cobalt uses
     * the {@code SignalGroupCipher} abstraction from libsignal.
     */
    private final SignalGroupCipher groupCipher;

    /**
     * Constructs a new decryption service.
     *
     * @param store         the WhatsApp store for session and key lookups
     * @param sessionCipher the Signal session cipher for 1:1 decryption
     * @param groupCipher   the Signal group cipher for group decryption
     *
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc uses
     * module-level imports for WAWebSignal.Cipher and
     * WAWebBotMessageSecret; Cobalt uses constructor-based DI.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptEnc", exports = "decryptEnc",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Decrypts a per-device message using the Signal Protocol.
     *
     * <p>Handles both {@code PreKeySignalMessage} (PKMSG, for new
     * sessions) and {@code SignalMessage} (MSG, for existing
     * sessions). Signal-specific exceptions are mapped onto the
     * Cobalt {@link WhatsAppMessageException.Receive} hierarchy so
     * that upstream code can decide the receipt type uniformly.
     *
     * @param ciphertext     the encrypted message bytes
     * @param senderJid      the sender's device JID
     * @param encryptionType the type of encryption (PKMSG or MSG)
     * @return the decrypted plaintext with padding already removed
     *
     * @throws WhatsAppMessageException.Receive if decryption fails
     * @throws NullPointerException             if any argument is {@code null}
     *
     * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc PKMSG/MSG
     * branch: determines the sender as
     * {@code a.isUser() ? a : participant}, then calls
     * {@code WAWebSignal.Cipher.decryptSignalProto(sender, e2eType, ciphertext, omitPersist, scope)}.
     * In Cobalt, the caller resolves the sender JID before calling
     * this method.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptEnc", exports = "decryptEnc",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSignalCipherApi", exports = "decryptSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "decryptSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public byte[] decryptFromDevice(byte[] ciphertext, Jid senderJid, MessageEncryptionType encryptionType) {
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(encryptionType, "encryptionType cannot be null");

        // WAWebSignalCipherApi.decryptSignalProto
        // Converts the sender JID into a Signal protocol address for session lookup
        var address = senderJid.toSignalAddress();
        return switch (encryptionType) {
            case PKMSG -> {
                // WAWebSignalCipherApi.decryptSignalProto PKMSG branch
                // Deserializes the PreKey envelope, decrypts via the session cipher, and strips padding
                try {
                    var message = SignalPreKeyMessage.ofSerialized(ciphertext);
                    var paddedPlaintext = sessionCipher.decrypt(address, message);
                    yield removePadding(paddedPlaintext);
                } catch (ProtobufDeserializationException e) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Invalid PreKeySignalMessage format from: " + senderJid, e);
                } catch (SignalMissingSessionException e) {
                    // PKMSG establishes a session, so missing-session is unusual; still map it for robustness
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "No session for PreKeyMessage from: " + senderJid, false, e);
                } catch (SignalUninitializedSessionException e) {
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "Session not initialized for: " + senderJid, false, e);
                } catch (SignalUntrustedIdentityException e) {
                    throw new WhatsAppMessageException.Receive.InvalidSignature(
                            "Identity key changed for: " + senderJid, e);
                } catch (SignalDecryptException e) {
                    // Distinguish duplicate/old-counter errors from generic decryption errors
                    if (isDuplicateCounterError(e)) {
                        throw new WhatsAppMessageException.Receive.DuplicateMessage(
                                "Decryption failed for PreKeyMessage from: " + senderJid, e);
                    }
                    throw new WhatsAppMessageException.Receive.Unknown(
                            "Decryption failed for PreKeyMessage from: " + senderJid, e);
                } catch (SecurityException e) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Security verification failed for message from: " + senderJid, e);
                }
            }
            case MSG -> {
                // WAWebSignalCipherApi.decryptSignalProto MSG branch
                // Deserializes the SignalMessage, decrypts via the session cipher, and strips padding
                try {
                    var message = SignalMessage.ofSerialized(ciphertext);
                    var paddedPlaintext = sessionCipher.decrypt(address, message);
                    yield removePadding(paddedPlaintext);
                } catch (ProtobufDeserializationException e) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Invalid SignalMessage format from: " + senderJid, e);
                } catch (SignalMissingSessionException e) {
                    // MSG requires an existing session; raise NoSession so sender can re-send as PKMSG
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
     * Returns whether a {@link SignalDecryptException} indicates a
     * duplicate-message/old-counter condition.
     *
     * <p>Triggered when a message with an already-seen counter is
     * received, typically due to resend or delivery races.
     *
     * @param e the decrypt exception to inspect
     * @return {@code true} if the error message matches a duplicate
     *         counter condition
     *
     * @implNote WAWebCryptoLibrary.decryptSignalProto and
     * WAWebCryptoLibrary.decryptGroupSignalProto distinguish
     * {@code errDuplicateMsg} from generic
     * {@code SignalDecryptionError}. Cobalt inspects the exception
     * message text because libsignal does not currently expose a
     * dedicated error subtype for this condition.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = {"decryptSignalProto", "decryptGroupSignalProto"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isDuplicateCounterError(SignalDecryptException e) {
        // WAWebCryptoLibrary.decryptSignalProto
        // Pattern-matches the error message to detect old-counter duplicates
        var message = e.getMessage();
        return message != null && (
                message.contains("old counter") ||
                message.contains("Received message with old counter")
        );
    }

    /**
     * Decrypts a group message using Signal's sender-key scheme.
     *
     * <p>Signal-specific exceptions are mapped as follows:
     * <ul>
     *   <li>{@code SignalMissingSenderKeyException}: NoSenderKey (no record)</li>
     *   <li>{@code SignalMissingSenderKeyStateException}: InvalidSenderKey (record exists but no state for ID)</li>
     *   <li>{@code SignalDecryptException}: Unknown or DuplicateMessage</li>
     *   <li>{@code SecurityException}: InvalidSenderKey (signature verification failure)</li>
     * </ul>
     *
     * @param ciphertext the encrypted message bytes
     * @param groupJid   the group JID
     * @param senderJid  the sender's device JID
     * @return the decrypted plaintext with padding already removed
     *
     * @throws WhatsAppMessageException.Receive if decryption fails
     * @throws NullPointerException             if any argument is {@code null}
     *
     * @implNote WAWebMsgProcessingDecryptEnc.decryptEnc SKMSG branch:
     * validates {@code a.isGroup() || a.isBroadcast()} and requires a
     * participant, then calls
     * {@code WAWebSignal.Cipher.decryptGroupSignalProto(chatJid, participant, ciphertext)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptEnc", exports = "decryptEnc",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSignalCipherApi", exports = "decryptGroupSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "decryptGroupSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public byte[] decryptFromGroup(byte[] ciphertext, Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        // WAWebSignalCommonUtils.createSignalLikeSenderKeyName
        // Produces the sender-key name used to look up the per-sender group session
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);

        // WAWebSignalCipherApi.decryptGroupSignalProto
        // Decrypts the ciphertext via the group cipher and strips padding
        try {
            var paddedPlaintext = groupCipher.decrypt(senderKeyName, ciphertext);
            return removePadding(paddedPlaintext);
        } catch (SignalMissingSenderKeyException e) {
            // No sender key record at all; sender should re-distribute their key
            throw new WhatsAppMessageException.Receive.NoSenderKey(
                    "No sender key exists for group: " + groupJid + " sender: " + senderJid, e);
        } catch (SignalMissingSenderKeyStateException e) {
            // Record exists but no state for the message key id (sender rotated their key)
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
            // Sender key signature verification failed
            throw new WhatsAppMessageException.Receive.InvalidSenderKey(
                    "Sender key signature verification failed from: " + senderJid + " in group: " + groupJid, e);
        } catch (ProtobufDeserializationException e) {
            throw new WhatsAppMessageException.Receive.InvalidMessage(
                    "Invalid SenderKeyMessage format from: " + senderJid + " in group: " + groupJid, e);
        }
    }

    /**
     * Decrypts a bot message (MSMSG) payload.
     *
     * <p>The ciphertext is a serialised
     * {@code MessageSecretMessage} protobuf containing {@code encIv}
     * and {@code encPayload} fields. The decryption key is derived
     * from the provided {@code messageSecret} using HKDF-SHA256 with
     * the message id and sender JIDs as info.
     *
     * <p>The caller is responsible for resolving the
     * {@code messageId} (from bot edit target or stanza id), looking
     * up the {@code messageSecret} from the target message, and
     * resolving the sender JIDs from stanza addressing.
     *
     * @param ciphertext      the MSMSG ciphertext (MessageSecretMessage protobuf)
     * @param messageSecret   the 32-byte secret from the target message
     * @param messageId       the message id used for key derivation and AAD
     * @param targetSenderJid the target message sender's user JID
     * @param botSenderJid    the bot's user JID
     * @return the decrypted inner plaintext
     *
     * @throws WhatsAppMessageException.Receive if decryption fails
     * @throws NullPointerException             if any argument is {@code null}
     *
     * @implNote WAWebBotMessageSecret.decryptMsmsgBotMessage: decodes
     * the ciphertext as {@code MessageSecretMessageSpec}, derives the
     * bot secret via {@code genBotMsgSecretFromMsgSecret}, then
     * derives the per-message AES-GCM key via function S() with
     * {@code extractAndExpand(botSecret, info, 32)}, and decrypts
     * with
     * {@code WACryptoAesGcm.gcmDecrypt(key, encIv, encPayload, aad)}
     * where AAD equals {@code messageId + "\0" + botSenderJid}.
     * In Cobalt, the caller resolves the messageSecret and sender
     * JIDs before calling this method.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
            // WAWebBotMessageSecret.decryptMsmsgBotMessage
            // Decodes the outer MessageSecretMessage protobuf carrying IV and encrypted payload
            var secretMessage = SecretMessageContainerSpec.decode(ciphertext);
            var encIv = secretMessage.encIv().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidMessage(
                            "MSMSG missing encIv", null));
            var encPayload = secretMessage.encPayload().orElseThrow(() ->
                    new WhatsAppMessageException.Receive.InvalidMessage(
                            "MSMSG missing encPayload", null));

            // WAWebBotMessageSecret.genBotMsgSecretFromMsgSecret
            // Derives the bot-specific secret from the stored messageSecret
            var botSecret = BotMessageSecret.derive(messageSecret);

            // WAWebBotMessageSecret function S()
            // Derives the per-message AES-GCM key via HKDF extract-and-expand
            var aesKey = deriveBotPerMessageKey(
                    messageId, targetSenderJid, botSenderJid, botSecret);

            // WAWebBotMessageSecret.decryptMsmsgBotMessage
            // Builds the AAD as messageId + 0x00 + botSenderJid for GCM authentication
            var aad = buildBotAad(messageId, botSenderJid);

            // WACryptoAesGcm.gcmDecrypt
            // Decrypts the payload with AES-GCM using the derived key, IV, and AAD
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
     * Removes PKCS#7 padding from a decrypted plaintext.
     *
     * <p>Reads the last byte to determine padding length and returns
     * the original plaintext with the padding stripped.
     *
     * @param paddedPlaintext the padded plaintext bytes
     * @return the plaintext with padding removed
     *
     * @throws IllegalArgumentException if the padding is invalid
     *
     * @implNote WAWebHandleMsgProcess.processDecryptedMessageProto:
     * uses {@code WACryptoPkcs7.unpad} to strip padding from the
     * decrypted plaintext before protobuf decoding.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgProcess", exports = "processDecryptedMessageProto",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static byte[] removePadding(byte[] paddedPlaintext) {
        Objects.requireNonNull(paddedPlaintext, "paddedPlaintext cannot be null");

        if (paddedPlaintext.length == 0) {
            throw new IllegalArgumentException("Padded plaintext cannot be empty");
        }

        // WACryptoPkcs7.unpad
        // Reads the last byte as the padding length
        var paddingLength = paddedPlaintext[paddedPlaintext.length - 1] & 0xFF;

        // WACryptoPkcs7.unpad
        // Validates the padding length is within the allowed 1..16 range
        if (paddingLength < MIN_PADDING || paddingLength > MAX_PADDING) {
            throw new IllegalArgumentException(
                    "Invalid padding length: " + paddingLength + " (expected " + MIN_PADDING + "-" + MAX_PADDING + ")"
            );
        }

        // WACryptoPkcs7.unpad
        // Validates the padding length does not exceed the plaintext length
        if (paddingLength > paddedPlaintext.length) {
            throw new IllegalArgumentException(
                    "Padding length " + paddingLength + " exceeds message length " + paddedPlaintext.length
            );
        }

        // WACryptoPkcs7.unpad
        // Returns the original plaintext by trimming the trailing padding bytes
        var originalLength = paddedPlaintext.length - paddingLength;
        return Arrays.copyOf(paddedPlaintext, originalLength);
    }


    /**
     * Processes a received sender key distribution message, storing
     * the sender key for future group message decryption.
     *
     * @param groupJid        the group JID
     * @param senderJid       the sender's device JID
     * @param distributionMsg the sender key distribution message
     *
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebCryptoLibrary.processSenderKeyDistributionMsg:
     * calls
     * {@code saveSenderKeySession(loadSenderKeySession, saveSenderKeySession, groupJid, deviceJid, distributionData)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "processSenderKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void processSenderKeyDistribution(Jid groupJid, Jid senderJid, SignalSenderKeyDistributionMessage distributionMsg) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(distributionMsg, "distributionMsg cannot be null");

        // WAWebCryptoLibrary.processSenderKeyDistributionMsg
        // Builds the sender-key name and delegates to the group cipher to install the key
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        groupCipher.process(senderKeyName, distributionMsg);
    }

    /**
     * Processes a received sender key distribution message from raw
     * bytes, deserialising the distribution message first.
     *
     * @param groupJid         the group JID
     * @param senderJid        the sender's device JID
     * @param distributionData the raw distribution message bytes
     *
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebCryptoLibrary.processSenderKeyDistributionMsg:
     * takes the raw distribution bytes and processes them via
     * {@code saveSenderKeySession}.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "processSenderKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void processSenderKeyDistribution(Jid groupJid, Jid senderJid, byte[] distributionData) {
        Objects.requireNonNull(distributionData, "distributionData cannot be null");

        // WAWebCryptoLibrary.processSenderKeyDistributionMsg
        // Deserialises the raw distribution bytes and delegates to the SignalSenderKeyDistributionMessage overload
        var distributionMsg = SignalSenderKeyDistributionMessage.ofSerialized(distributionData);
        processSenderKeyDistribution(groupJid, senderJid, distributionMsg);
    }

    /**
     * Returns whether a Signal session exists for the given device.
     *
     * @param deviceJid the device JID to check
     * @return {@code true} if a session exists, {@code false} otherwise
     *
     * @throws NullPointerException if {@code deviceJid} is {@code null}
     *
     * @implNote WAWebCryptoLibrary.getRemoteRegId loads a session to
     * check existence; Cobalt queries the store directly for
     * efficiency.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "getRemoteRegId",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasSessionWith(Jid deviceJid) {
        Objects.requireNonNull(deviceJid, "deviceJid cannot be null");

        // WAWebCryptoLibrary.getRemoteRegId
        // Returns true when the store has a session record for the Signal address
        var address = deviceJid.toSignalAddress();
        return store.findSessionByAddress(address).isPresent();
    }

    /**
     * Returns whether a sender key exists for the given group and
     * sender.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @return {@code true} if a sender key exists, {@code false} otherwise
     *
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebCryptoLibrary stores sender keys via
     * {@code loadSenderKeySession}; Cobalt queries the store directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "getGroupSenderKeyInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        // WAWebCryptoLibrary
        // Returns true when the store has a sender-key record keyed by the sender-key name
        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return store.findSenderKeyByName(senderKeyName).isPresent();
    }

    /**
     * Extracts the sender's identity key from a PreKeySignalMessage
     * ciphertext for use in ADV validation before decryption.
     *
     * <p>The PreKeySignalMessage structure carries the sender's
     * identity key which can be extracted without decrypting the
     * inner message. This allows verifying ADV signatures for
     * companion devices before attempting decryption.
     *
     * @param ciphertext the PKMSG ciphertext bytes
     * @return an {@link Optional} wrapping the 32-byte identity key
     *
     * @implNote WAWebSignalUtilsApi.extractIdentityKey: extracts the
     * identity key from PreKeySignalMessage for ADV validation.
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalUtilsApi", exports = "extractIdentityKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<byte[]> extractIdentityKeyFromPkmsg(byte[] ciphertext) {
        // WAWebSignalUtilsApi.extractIdentityKey
        // Returns empty on null/empty input
        if (ciphertext == null || ciphertext.length == 0) {
            return Optional.empty();
        }

        // WAWebSignalUtilsApi.extractIdentityKey
        // Deserialises the PreKeySignalMessage and returns its identity key point bytes
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
     * Derives the per-message AES-GCM key for MSMSG decryption using
     * HKDF-SHA256.
     *
     * <p>Performs HKDF-Extract with a null (all-zero) salt over the
     * bot secret, then HKDF-Expand with info built as
     * {@code messageId || targetSenderJid || botSenderJid}.
     *
     * @param messageId       the message id (or bot edit target id)
     * @param targetSenderJid the target message sender's user JID
     * @param botSenderJid    the bot's user JID
     * @param botSecret       the base bot secret derived from messageSecret
     * @return the 32-byte AES-GCM key
     *
     * @throws GeneralSecurityException if the HKDF provider is unavailable
     *
     * @implNote WAWebBotMessageSecret function S(): calls
     * {@code WACryptoHkdf.extractAndExpand(new Uint8Array(botSecret), info, 32)}
     * where info equals
     * {@code Binary.build(externalId, targetSenderJid, botSenderJid)}.
     * {@code extractAndExpand} performs HKDF-Extract with null salt
     * followed by HKDF-Expand.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private byte[] deriveBotPerMessageKey(
            String messageId,
            Jid targetSenderJid,
            Jid botSenderJid,
            byte[] botSecret
    ) throws GeneralSecurityException {
        // WAWebBotMessageSecret function S()
        // Encodes the messageId and sender JIDs as UTF-8 for the HKDF info
        var idBytes = messageId.getBytes(StandardCharsets.UTF_8);
        var targetBytes = targetSenderJid.toString().getBytes(StandardCharsets.UTF_8);
        var botBytes = botSenderJid.toString().getBytes(StandardCharsets.UTF_8);

        // WAWebBotMessageSecret function S()
        // Concatenates the three components to form the HKDF info
        var info = new byte[idBytes.length + targetBytes.length + botBytes.length];
        System.arraycopy(idBytes, 0, info, 0, idBytes.length);
        System.arraycopy(targetBytes, 0, info, idBytes.length, targetBytes.length);
        System.arraycopy(botBytes, 0, info, idBytes.length + targetBytes.length, botBytes.length);

        // WACryptoHkdf.extractAndExpand
        // Performs HKDF-Extract (null salt) then HKDF-Expand to the target key size
        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        var params = HKDFParameterSpec.ofExtract()
                .addIKM(botSecret)
                .thenExpand(info, HKDF_KEY_SIZE);
        return kdf.deriveData(params);
    }

    /**
     * Builds the AAD (Additional Authenticated Data) for MSMSG
     * AES-GCM decryption.
     *
     * <p>Format: {@code messageId + "\0" + botSenderJid}.
     *
     * @param messageId    the message id
     * @param botSenderJid the bot's user JID
     * @return the AAD bytes
     *
     * @implNote WAWebBotMessageSecret.decryptMsmsgBotMessage: AAD is
     * {@code externalId + "\0" + botSenderJid} passed to
     * {@code WACryptoAesGcm.gcmDecrypt}.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private byte[] buildBotAad(String messageId, Jid botSenderJid) {
        // WAWebBotMessageSecret.decryptMsmsgBotMessage
        // Constructs the AAD as the UTF-8 messageId followed by a null byte and the bot JID
        var idBytes = messageId.getBytes(StandardCharsets.UTF_8);
        var botBytes = botSenderJid.toString().getBytes(StandardCharsets.UTF_8);
        var aad = new byte[idBytes.length + 1 + botBytes.length];
        System.arraycopy(idBytes, 0, aad, 0, idBytes.length);
        aad[idBytes.length] = 0x00;
        System.arraycopy(botBytes, 0, aad, idBytes.length + 1, botBytes.length);
        return aad;
    }
}
