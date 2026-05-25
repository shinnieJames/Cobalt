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
 * Decrypts every flavour of incoming end-to-end encrypted WhatsApp payload behind a
 * single service.
 *
 * <p>The four entry points cover the four encryption types that can appear in a
 * stanza's {@code <enc>} children:
 * <ul>
 *   <li>{@link #decryptFromDevice(byte[], Jid, MessageEncryptionType)} for
 *       {@link MessageEncryptionType#PKMSG} and {@link MessageEncryptionType#MSG}
 *       through the Signal session cipher.</li>
 *   <li>{@link #decryptFromGroup(byte[], Jid, Jid)} for
 *       {@link MessageEncryptionType#SKMSG} through the Signal group cipher.</li>
 *   <li>{@link #decryptBotMessage(byte[], byte[], String, Jid, Jid)} for
 *       {@link MessageEncryptionType#MSMSG}, an AES-GCM scheme keyed via HKDF over the
 *       target message's secret.</li>
 *   <li>{@link #processSenderKeyDistribution(Jid, Jid, byte[])} for storing a received
 *       sender-key distribution so future {@link MessageEncryptionType#SKMSG} payloads
 *       can be decrypted.</li>
 * </ul>
 *
 * <p>The service is consumed by {@code ChatMessageReceiver}
 * as part of the inbound message pipeline.
 *
 * @implNote
 * This implementation maps libsignal's exception hierarchy onto the sealed
 * {@link WhatsAppMessageException.Receive} subtypes so the upstream pipeline can pick
 * the right receipt (delivery, retry, NACK) by branching on a strongly-typed
 * exception; WhatsApp Web folds the same Signal errors into a single
 * {@code SignalDecryptionError} and re-discriminates them via string-match inside
 * {@code WAWebSendRetryReceiptJob.getRetryReasonFromError}.
 */
@WhatsAppWebModule(moduleName = "WAWebMsgProcessingDecryptEnc")
@WhatsAppWebModule(moduleName = "WAWebSignalCipherApi")
@WhatsAppWebModule(moduleName = "WAWebBotMessageSecret")
@WhatsAppWebModule(moduleName = "WAWebCryptoLibrary")
@WhatsAppWebModule(moduleName = "WASignalGroupCipher")
public final class MessageDecryption {
    /**
     * Holds the smallest valid PKCS#7 padding length used by the Signal protocol
     * payload format.
     */
    private static final int MIN_PADDING = 1;

    /**
     * Holds the largest valid PKCS#7 padding length used by the Signal protocol
     * payload format.
     */
    private static final int MAX_PADDING = 16;

    /**
     * Holds the HKDF-derived AES-GCM key length, in bytes, used for the
     * {@link MessageEncryptionType#MSMSG} bot-message scheme.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int HKDF_KEY_SIZE = 32;

    /**
     * Holds the AES-GCM authentication tag length, in bits, used for the
     * {@link MessageEncryptionType#MSMSG} bot-message scheme.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int AES_GCM_TAG_BITS = 128;

    /**
     * Holds the KDF algorithm identifier passed to {@link KDF#getInstance(String)} for
     * {@link MessageEncryptionType#MSMSG} key derivation.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * Holds the cipher transformation identifier passed to
     * {@link Cipher#getInstance(String)} for {@link MessageEncryptionType#MSMSG}
     * decryption.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * Holds the central session store used for Signal-session and sender-key existence
     * checks and for resolving bot-message metadata.
     */
    private final WhatsAppStore store;

    /**
     * Holds the Signal session cipher used by
     * {@link #decryptFromDevice(byte[], Jid, MessageEncryptionType)}.
     */
    private final SignalSessionCipher sessionCipher;

    /**
     * Holds the Signal group cipher used by {@link #decryptFromGroup(byte[], Jid, Jid)}
     * and {@link #processSenderKeyDistribution(Jid, Jid, SignalSenderKeyDistributionMessage)}.
     */
    private final SignalGroupCipher groupCipher;

    /**
     * Constructs the decryption service from its three collaborators.
     *
     * @param store         the central session store used for sender-key and Signal
     *                      session lookups
     * @param sessionCipher the libsignal session cipher used for
     *                      {@link MessageEncryptionType#PKMSG} and
     *                      {@link MessageEncryptionType#MSG} decryption
     * @param groupCipher   the libsignal group cipher used for
     *                      {@link MessageEncryptionType#SKMSG} decryption and
     *                      sender-key import
     * @throws NullPointerException if any argument is {@code null}
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
     * Decrypts a per-device Signal payload ({@link MessageEncryptionType#PKMSG} or
     * {@link MessageEncryptionType#MSG}).
     *
     * <p>The caller dispatches between {@link MessageEncryptionType#PKMSG} (new
     * session) and {@link MessageEncryptionType#MSG} (existing session) based on the
     * {@code <enc>} node's {@code type} attribute. The returned plaintext has the
     * Signal-protocol PKCS#7 padding already stripped by {@link #removePadding(byte[])}.
     *
     * @implNote
     * This implementation maps the libsignal exception hierarchy onto the sealed
     * {@link WhatsAppMessageException.Receive} subtypes so receipt selection can branch
     * on a strongly-typed exception rather than a string-match against the underlying
     * libsignal message; {@link MessageEncryptionType#SKMSG} and
     * {@link MessageEncryptionType#MSMSG} throw {@link IllegalArgumentException} as a
     * misuse signal because the caller must route those to
     * {@link #decryptFromGroup(byte[], Jid, Jid)} and
     * {@link #decryptBotMessage(byte[], byte[], String, Jid, Jid)}.
     *
     * @param ciphertext     the encrypted message bytes
     * @param senderJid      the sender's device JID
     * @param encryptionType the encryption type ({@link MessageEncryptionType#PKMSG} or
     *                       {@link MessageEncryptionType#MSG})
     * @return the decrypted plaintext bytes with padding stripped
     * @throws WhatsAppMessageException.Receive if decryption fails
     * @throws IllegalArgumentException         if {@code encryptionType} is
     *                                          {@link MessageEncryptionType#SKMSG} or
     *                                          {@link MessageEncryptionType#MSMSG}
     * @throws NullPointerException             if any argument is {@code null}
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
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "No session for PreKeyMessage from: " + senderJid, false, e);
                } catch (SignalUninitializedSessionException e) {
                    throw new WhatsAppMessageException.Receive.NoSession(
                            "Session not initialized for: " + senderJid, false, e);
                } catch (SignalUntrustedIdentityException e) {
                    throw new WhatsAppMessageException.Receive.InvalidSignature(
                            "Identity key changed for: " + senderJid, e);
                } catch (SignalDecryptException e) {
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
                try {
                    var message = SignalMessage.ofSerialized(ciphertext);
                    var paddedPlaintext = sessionCipher.decrypt(address, message);
                    yield removePadding(paddedPlaintext);
                } catch (ProtobufDeserializationException e) {
                    throw new WhatsAppMessageException.Receive.InvalidMessage(
                            "Invalid SignalMessage format from: " + senderJid, e);
                } catch (SignalMissingSessionException e) {
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
     * Returns whether the given libsignal {@link SignalDecryptException} indicates a
     * duplicate-counter or old-counter condition.
     *
     * <p>Inspected by both the per-device and group branches so a duplicate is mapped
     * onto {@link WhatsAppMessageException.Receive.DuplicateMessage} for dedup routing
     * rather than a generic {@link WhatsAppMessageException.Receive.Unknown} error.
     *
     * @implNote
     * This implementation inspects the exception message text because libsignal does
     * not expose a dedicated duplicate-counter subtype; mirrors WhatsApp Web's
     * {@code WAWebMsgProcessingDecryptionHandler} string-match
     * ({@code e.message==="errDuplicateMsg"}) and the
     * {@code WAWebSendRetryReceiptJob} branches that examine the literal Signal error
     * messages.
     *
     * @param e the libsignal decrypt exception to inspect
     * @return {@code true} when the underlying message matches a duplicate-counter
     *         pattern
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = {"decryptSignalProto", "decryptGroupSignalProto"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isDuplicateCounterError(SignalDecryptException e) {
        var message = e.getMessage();
        return message != null && (
                message.contains("old counter") ||
                message.contains("Received message with old counter")
        );
    }

    /**
     * Decrypts a group, community, or broadcast sender-key payload
     * ({@link MessageEncryptionType#SKMSG}).
     *
     * <p>The returned plaintext has Signal-protocol PKCS#7 padding stripped by
     * {@link #removePadding(byte[])}.
     *
     * @implNote
     * This implementation maps the libsignal exception subtypes onto the sealed
     * {@link WhatsAppMessageException.Receive} hierarchy:
     * <ul>
     *   <li>a missing sender-key record becomes
     *       {@link WhatsAppMessageException.Receive.NoSenderKey}.</li>
     *   <li>a missing sender-key state for the message-key id becomes
     *       {@link WhatsAppMessageException.Receive.InvalidSenderKey}.</li>
     *   <li>a {@link SignalDecryptException} becomes either
     *       {@link WhatsAppMessageException.Receive.DuplicateMessage} (when
     *       {@link #isDuplicateCounterError(SignalDecryptException)} matches) or
     *       {@link WhatsAppMessageException.Receive.Unknown}.</li>
     *   <li>a {@link SecurityException} becomes
     *       {@link WhatsAppMessageException.Receive.InvalidSenderKey}.</li>
     * </ul>
     *
     * @param ciphertext the encrypted message bytes
     * @param groupJid   the group, community, or broadcast JID
     * @param senderJid  the sender's device JID
     * @return the decrypted plaintext bytes with padding stripped
     * @throws WhatsAppMessageException.Receive if decryption fails
     * @throws NullPointerException             if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptEnc", exports = "decryptEnc",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSignalCipherApi", exports = "decryptGroupSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "decryptGroupSignalProto",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASignalGroupCipher", exports = "decryptSenderKeyMsgFromSession",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASignalGroupCipher", exports = "deserializeSenderKeyMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public byte[] decryptFromGroup(byte[] ciphertext, Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);

        try {
            var paddedPlaintext = groupCipher.decrypt(senderKeyName, ciphertext);
            return removePadding(paddedPlaintext);
        } catch (SignalMissingSenderKeyException e) {
            throw new WhatsAppMessageException.Receive.NoSenderKey(
                    "No sender key exists for group: " + groupJid + " sender: " + senderJid, e);
        } catch (SignalMissingSenderKeyStateException e) {
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
            throw new WhatsAppMessageException.Receive.InvalidSenderKey(
                    "Sender key signature verification failed from: " + senderJid + " in group: " + groupJid, e);
        } catch (ProtobufDeserializationException e) {
            throw new WhatsAppMessageException.Receive.InvalidMessage(
                    "Invalid SenderKeyMessage format from: " + senderJid + " in group: " + groupJid, e);
        }
    }

    /**
     * Decrypts a bot-message payload ({@link MessageEncryptionType#MSMSG}).
     *
     * <p>The caller resolves the {@code messageId} (preferring {@code botEditTargetId}
     * from the {@code <bot>} stanza child when present), looks up the
     * {@code messageSecret} from the target message, and resolves the sender JIDs from
     * the stanza's addressing attributes. The {@code ciphertext} is a serialised
     * {@code MessageSecretMessage} protobuf carrying {@code encIv} and
     * {@code encPayload} fields.
     *
     * @implNote
     * This implementation derives the AES-GCM key as
     * {@snippet :
     *     // info = messageId || targetSenderJid || botSenderJid (UTF-8 bytes)
     *     // aesKey = HKDF-SHA256(IKM = BotMessageSecret.derive(messageSecret),
     *     //                      salt = empty,
     *     //                      info = info,
     *     //                      L = 32)
     *     // aad = messageId || 0x00 || botSenderJid
     * }
     * mirroring WhatsApp Web's {@code WAWebBotMessageSecret.decryptMsmsgBotMessage}.
     * The {@code Bot Message} label that WhatsApp Web feeds into the inner HKDF over
     * the raw secret is handled inside {@link BotMessageSecret#derive(byte[])}, so this
     * method only sees the derived base secret.
     *
     * @param ciphertext      the {@link MessageEncryptionType#MSMSG} ciphertext (a
     *                        {@code MessageSecretMessage} protobuf)
     * @param messageSecret   the 32-byte secret from the target message
     * @param messageId       the message id used for key derivation and AAD
     * @param targetSenderJid the target message sender's user JID
     * @param botSenderJid    the bot sender's user JID
     * @return the decrypted inner plaintext
     * @throws WhatsAppMessageException.Receive if decryption fails
     * @throws NullPointerException             if any argument is {@code null}
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
            var secretMessage = SecretMessageContainerSpec.decode(ciphertext);
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
     * Strips Signal-protocol PKCS#7 padding from a decrypted plaintext.
     *
     * <p>Applied by the per-device and group decryption branches before returning to
     * the caller, and not applied to {@link MessageEncryptionType#MSMSG} ciphertexts.
     *
     * @implNote
     * This implementation reads the last byte as the padding length, validates that it
     * falls in the {@code [MIN_PADDING, MAX_PADDING]} range, and returns a fresh array
     * containing only the unpadded prefix; mirrors WhatsApp Web's
     * {@code WACryptoPkcs7.unpadPkcs7}. WhatsApp Web's
     * {@code WAWebMsgProcessingDecryptApi.processDecryptedMessageProto} also skips
     * unpadding for the {@code Msmsg} ciphertext type, which is why
     * {@link #decryptBotMessage(byte[], byte[], String, Jid, Jid)} never calls this
     * method.
     *
     * @param paddedPlaintext the padded plaintext bytes
     * @return the plaintext with padding removed
     * @throws IllegalArgumentException if the padding length is missing or invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgProcess", exports = "processDecryptedMessageProto",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WACryptoPkcs7", exports = "unpadPkcs7",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static byte[] removePadding(byte[] paddedPlaintext) {
        Objects.requireNonNull(paddedPlaintext, "paddedPlaintext cannot be null");

        if (paddedPlaintext.length == 0) {
            throw new IllegalArgumentException("Padded plaintext cannot be empty");
        }

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
     * Stores a received sender-key distribution message so future
     * {@link MessageEncryptionType#SKMSG} payloads from the same sender in the same
     * group can be decrypted.
     *
     * <p>The caller verifies that the embedded group id matches the stanza chat JID
     * before invoking this method.
     *
     * @param groupJid        the group JID
     * @param senderJid       the sender's device JID
     * @param distributionMsg the parsed Signal sender-key distribution message
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "processSenderKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASignalGroupCipher", exports = "processSenderKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void processSenderKeyDistribution(Jid groupJid, Jid senderJid, SignalSenderKeyDistributionMessage distributionMsg) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        Objects.requireNonNull(distributionMsg, "distributionMsg cannot be null");

        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        groupCipher.process(senderKeyName, distributionMsg);
    }

    /**
     * Stores a received sender-key distribution from raw protobuf bytes, parsing before
     * delegating to
     * {@link #processSenderKeyDistribution(Jid, Jid, SignalSenderKeyDistributionMessage)}.
     *
     * <p>This overload accepts the raw distribution-message bytes extracted from the
     * decrypted {@code MessageContainer}.
     *
     * @param groupJid         the group JID
     * @param senderJid        the sender's device JID
     * @param distributionData the raw distribution-message bytes
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "processSenderKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASignalGroupCipher", exports = "processSenderKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASignalGroupCipher", exports = "deserializeSenderKeyMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void processSenderKeyDistribution(Jid groupJid, Jid senderJid, byte[] distributionData) {
        Objects.requireNonNull(distributionData, "distributionData cannot be null");

        var distributionMsg = SignalSenderKeyDistributionMessage.ofSerialized(distributionData);
        processSenderKeyDistribution(groupJid, senderJid, distributionMsg);
    }

    /**
     * Returns whether a Signal session exists for the given device.
     *
     * <p>Serves as a defensive lookup before invoking
     * {@link #decryptFromDevice(byte[], Jid, MessageEncryptionType)} when the caller
     * needs to know whether a {@link MessageEncryptionType#PKMSG}-style session
     * installation is still pending.
     *
     * @param deviceJid the device JID to check
     * @return {@code true} when a Signal session record is present
     * @throws NullPointerException if {@code deviceJid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "getRemoteRegId",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasSessionWith(Jid deviceJid) {
        Objects.requireNonNull(deviceJid, "deviceJid cannot be null");

        var address = deviceJid.toSignalAddress();
        return store.findSessionByAddress(address).isPresent();
    }

    /**
     * Returns whether a sender-key record exists for the given group and sender.
     *
     * <p>Lets a caller gate a send or receive decision on the existence of the
     * sender-key state before invoking the group cipher.
     *
     * @param groupJid  the group JID
     * @param senderJid the sender's device JID
     * @return {@code true} when a sender-key record is present
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoLibrary", exports = "getGroupSenderKeyInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasSenderKey(Jid groupJid, Jid senderJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");

        var senderKeyName = SenderKeyNameFactory.create(groupJid, senderJid);
        return store.findSenderKeyByName(senderKeyName).isPresent();
    }

    /**
     * Extracts the sender's identity public key from a
     * {@link MessageEncryptionType#PKMSG} ciphertext.
     *
     * <p>Used by Auxiliary Device Validation (ADV) so the receiver can verify the
     * companion device's identity signature before attempting decryption.
     *
     * @implNote
     * This implementation parses the {@link MessageEncryptionType#PKMSG} envelope just
     * enough to surface the identity-key field; the inner message is not decrypted
     * here. A {@link ProtobufDeserializationException} or {@link IllegalArgumentException}
     * surfaces as an empty {@link Optional} so the caller decides whether to treat the
     * failure as a fatal ADV mismatch or to retry.
     *
     * @param ciphertext the {@link MessageEncryptionType#PKMSG} ciphertext bytes
     * @return an {@link Optional} wrapping the 32-byte identity-key encoded point
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalUtilsApi", exports = "extractIdentityKey",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * Derives the per-message AES-GCM key for {@link MessageEncryptionType#MSMSG}
     * decryption.
     *
     * @implNote
     * This implementation performs HKDF-SHA256 with an empty (default) salt over the
     * bot secret and an {@code info} string built as
     * {@code messageId || targetSenderJid || botSenderJid} (UTF-8 bytes, no
     * delimiters). The output length is {@value #HKDF_KEY_SIZE} bytes. Mirrors WhatsApp
     * Web's {@code v} helper inside {@code WAWebBotMessageSecret}.
     *
     * @param messageId       the message id (or bot edit-target id)
     * @param targetSenderJid the target message sender's user JID
     * @param botSenderJid    the bot sender's user JID
     * @param botSecret       the base bot secret derived from {@code messageSecret}
     * @return the 32-byte AES-GCM key
     * @throws GeneralSecurityException if the HKDF provider is unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private byte[] deriveBotPerMessageKey(
            String messageId,
            Jid targetSenderJid,
            Jid botSenderJid,
            byte[] botSecret
    ) throws GeneralSecurityException {
        var idBytes = messageId.getBytes(StandardCharsets.UTF_8);
        var targetBytes = targetSenderJid.toString().getBytes(StandardCharsets.UTF_8);
        var botBytes = botSenderJid.toString().getBytes(StandardCharsets.UTF_8);

        var info = new byte[idBytes.length + targetBytes.length + botBytes.length];
        System.arraycopy(idBytes, 0, info, 0, idBytes.length);
        System.arraycopy(targetBytes, 0, info, idBytes.length, targetBytes.length);
        System.arraycopy(botBytes, 0, info, idBytes.length + targetBytes.length, botBytes.length);

        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        var params = HKDFParameterSpec.ofExtract()
                .addIKM(botSecret)
                .thenExpand(info, HKDF_KEY_SIZE);
        return kdf.deriveData(params);
    }

    /**
     * Builds the additional authenticated data for the {@link MessageEncryptionType#MSMSG}
     * AES-GCM decryption.
     *
     * @implNote
     * This implementation builds the AAD as {@code messageId || 0x00 || botSenderJid}
     * with bytes encoded in UTF-8; mirrors WhatsApp Web's
     * {@code WAWebBotMessageSecret.decryptMsmsgBotMessage} call site that passes
     * {@code y+"\0"+_} to {@code WACryptoAesGcm.gcmDecrypt}.
     *
     * @param messageId    the message id
     * @param botSenderJid the bot sender's user JID
     * @return the AAD bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "decryptMsmsgBotMessage",
            adaptation = WhatsAppAdaptation.DIRECT)
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
