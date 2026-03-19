package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.model.sync.SyncActionData;
import com.github.auties00.cobalt.model.sync.SyncActionDataSpec;
import com.github.auties00.cobalt.model.sync.SyncActionValue;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import it.auties.protobuf.stream.ProtobufInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;

/**
 * Represents the result of decrypting a sync mutation.
 *
 * <p>Two variants exist: {@link Untrusted} (freshly decrypted with full MAC verification
 * metadata) and {@link Trusted} (validated and ready for application).
 *
 * @implNote WAWebSyncdDecryptMutations.syncdDecryptMutation,
 *           WAWebSyncdDecryptMutationsWrapper.tryDecryptSnapshot,
 *           WAWebSyncdDecryptMutationsWrapper.tryDecryptPatch
 */
public sealed interface DecryptedMutation {
    /**
     * Returns the index string identifying this mutation's target.
     *
     * @return the index string
     */
    String index();

    /**
     * Returns the sync operation type (SET or REMOVE).
     *
     * @return the operation type
     */
    SyncdOperation operation();

    /**
     * Returns the timestamp of the action.
     *
     * @return the action timestamp
     */
    Instant timestamp();

    /**
     * Represents a freshly decrypted mutation that has passed MAC verification
     * but retains all cryptographic metadata.
     *
     * @param index         the decoded index string from the action data
     * @param indexMac      the verified index MAC bytes
     * @param valueMac      the verified value MAC bytes (last 32 bytes of encrypted value)
     * @param value         the decoded sync action value
     * @param operation     the sync operation type
     * @param timestamp     the action timestamp
     * @param keyId         the sync key ID used for decryption
     * @param actionVersion the action version from the decoded action data
     * @implNote WAWebSyncdDecryptMutations.syncdDecryptMutation
     */
    record Untrusted(
            String index,
            byte[] indexMac,
            byte[] valueMac,
            SyncActionValue value,
            SyncdOperation operation,
            Instant timestamp,
            byte[] keyId,
            int actionVersion
    ) implements DecryptedMutation {
        private static final int IV_LENGTH = 16; // WAWebSyncdCryptoConst.IV_LENGTH
        private static final int MAC_LENGTH = 32; // WAWebSyncdCryptoConst.MAC_LENGTH

        /**
         * Decrypts and verifies an encrypted mutation value.
         *
         * <p>Performs the following steps matching WA Web's {@code syncdDecryptMutation}:
         * <ol>
         *   <li>Extracts value MAC from last 32 bytes</li>
         *   <li>Splits encrypted value into IV, ciphertext, and MAC</li>
         *   <li>Verifies value MAC via HMAC-SHA512 truncated to 32 bytes</li>
         *   <li>Decrypts ciphertext with AES-256-CBC</li>
         *   <li>Decodes and validates the {@code SyncActionData} protobuf</li>
         *   <li>Verifies index MAC via HMAC-SHA256</li>
         * </ol>
         *
         * @param encryptedValue the wire encrypted value bytes (IV || ciphertext || MAC)
         * @param indexMac       the wire index MAC bytes to verify against
         * @param keys           the derived mutation keys
         * @param operation      the sync operation type
         * @param keyId          the sync key ID bytes
         * @return a new {@code Untrusted} instance with the decrypted data
         * @throws GeneralSecurityException if any cryptographic operation fails
         * @throws WhatsAppWebAppStateSyncException.ValueMacMismatch if value MAC verification fails
         * @throws WhatsAppWebAppStateSyncException.IndexMacMismatch if index MAC verification fails
         * @implNote WAWebSyncdDecryptMutations.syncdDecryptMutation,
         *           WAWebSyncdMutationsCryptoUtils.generateMac,
         *           WAWebSyncdMutationsCryptoUtils.generateAssociatedData,
         *           WAWebSyncdMutationsCryptoUtils.decryptCipherText,
         *           WAWebSyncdCrypto.generateIndexMac,
         *           WAWebSyncdDecode.decodeSyncActionData,
         *           WAWebSyncdValidateSyncActionProtobuf.validateSyncActionDataProtobuf
         */
        public static Untrusted of(
                byte[] encryptedValue,
                byte[] indexMac,
                MutationKeys keys,
                SyncdOperation operation,
                byte[] keyId
        ) throws GeneralSecurityException {
            if (encryptedValue.length < IV_LENGTH + MAC_LENGTH) {
                throw new IllegalArgumentException("Encrypted value too short");
            }

            // Extract value MAC (last 32 bytes) — WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText
            var valueMac = Arrays.copyOfRange(encryptedValue, encryptedValue.length - MAC_LENGTH, encryptedValue.length);

            // Build associated data: [opByte] || keyIdBytes — WAWebSyncdMutationsCryptoUtils.generateAssociatedData
            var associatedData = new byte[1 + keyId.length];
            associatedData[0] = operation.content(); // WAWebSyncdCryptoConst.OPERATION_SET_HEX / OPERATION_REMOVE_HEX
            System.arraycopy(keyId, 0, associatedData, 1, keyId.length);

            // Build 8-byte length suffix: last byte = associatedData.length — WAWebSyncdMutationsCryptoUtils.generateMac
            var lengthSuffix = new byte[8]; // WAWebSyncdCryptoConst.OCTET_LENGTH = 8
            lengthSuffix[7] = (byte) associatedData.length;

            // Verify value MAC using HMAC-SHA-512 truncated to 32 bytes — WAWebSyncdMutationsCryptoUtils.generateMac
            var mac = Mac.getInstance("HmacSHA512"); // WACryptoHmac.hmacSha512
            mac.init(keys.valueMacKey());
            mac.update(associatedData); // WAWebSyncdCryptoUtils.combine([associatedData, iv_ciphertext, lengthSuffix])
            mac.update(encryptedValue, 0, encryptedValue.length - MAC_LENGTH); // IV || ciphertext
            mac.update(lengthSuffix);
            var fullMac = mac.doFinal();
            var expectedMac = Arrays.copyOf(fullMac, MAC_LENGTH); // WAWebSyncdCryptoConst.MAC_LENGTH = 32
            if (!MessageDigest.isEqual(valueMac, expectedMac)) { // WACryptoUtils.arrayBuffersEqual
                throw new WhatsAppWebAppStateSyncException.ValueMacMismatch(); // WAWebSyncdError.SyncdFatalError("decryption failure: valueMAC mismatch")
            }

            // Decrypt payload with AES-256-CBC and decode protobuf — WAWebSyncdMutationsCryptoUtils.decryptCipherText
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // ADAPTED: WACryptoAesCbc.aesCbcDecrypt
            var ivSpec = new IvParameterSpec(encryptedValue, 0, IV_LENGTH); // WAWebSyncdCryptoUtils.split — iv part
            cipher.init(Cipher.DECRYPT_MODE, keys.valueEncryptionKey(), ivSpec);
            var ciphertextStream = new ByteArrayInputStream(encryptedValue, IV_LENGTH, encryptedValue.length - IV_LENGTH - MAC_LENGTH); // WAWebSyncdCryptoUtils.split — ciphertext part
            var plaintextStream = new CipherInputStream(ciphertextStream, cipher);
            SyncActionData actionData;
            try {
                actionData = SyncActionDataSpec.decode(ProtobufInputStream.fromStream(plaintextStream)); // WAWebSyncdDecode.decodeSyncActionData
            } catch (Exception e) {
                throw new WhatsAppWebAppStateSyncException.DecryptionFailed( // WAWebSyncdDecode.decodeSyncActionData — SyncdFatalError("syncd: data protobuf deserialization failed: ...")
                        "syncd: data protobuf deserialization failed: " + e.getMessage(), e);
            }

            // Verify index MAC — WAWebSyncdCrypto.generateIndexMac
            var actionIndex = actionData.index() // WAWebSyncdValidateSyncActionProtobuf.validateSyncActionDataProtobuf — index check
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing index from action data", null));
            var actionValue = actionData.value() // ADAPTED: WAWebSyncdValidateMutations.validateAndTypeSetMutations checks this for SET mutations only; Cobalt checks eagerly for all mutations
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing value from action data", null));
            var actionTimestamp = actionValue.timestamp() // ADAPTED: WAWebSyncdValidateMutations.validateAndTypeSetMutations extracts timestamp for SET mutations only; Cobalt extracts eagerly
                    .orElseThrow(WhatsAppWebAppStateSyncException.MissingActionTimestamp::new);
            var indexMac2 = Mac.getInstance("HmacSHA256"); // WAWebSyncdCrypto.generateIndexMac — WACryptoHmac.hmacSha256
            indexMac2.init(keys.indexKey());
            var expectedIndexMac = indexMac2.doFinal(actionIndex);
            if (!MessageDigest.isEqual(indexMac, expectedIndexMac)) { // WACryptoUtils.arrayBuffersEqual
                throw new WhatsAppWebAppStateSyncException.IndexMacMismatch(); // WAWebSyncdError.SyncdFatalError("decryption failure: indexMAC mismatch")
            }

            // WAWebSyncdValidateSyncActionProtobuf.validateSyncActionDataProtobuf — version check
            var actionVersion = actionData.version()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Missing version in action data", null));
            return new Untrusted(
                    new String(actionIndex, StandardCharsets.UTF_8), // WAArrayBufferUtils equivalent
                    indexMac,  // WAWebSyncdDecryptMutations: indexMac: c
                    valueMac,  // WAWebSyncdDecryptMutations: valueMac: m
                    actionValue, // ADAPTED: stored as object, WA Web re-encodes to binary
                    operation,
                    actionTimestamp,
                    keyId,
                    actionVersion
            );
        }
    }

    /**
     * Represents a validated mutation ready for application to the local store.
     *
     * @param index         the index string identifying the mutation target
     * @param value         the sync action value to apply
     * @param operation     the sync operation type (SET or REMOVE)
     * @param timestamp     the action timestamp
     * @param actionVersion the action version number
     * @implNote ADAPTED: Cobalt-specific post-verification type, no direct WA Web equivalent.
     *           WA Web passes raw fields after anti-tampering; Cobalt wraps them in a sealed variant.
     */
    record Trusted(
            String index,
            SyncActionValue value,
            SyncdOperation operation,
            Instant timestamp,
            int actionVersion
    ) implements DecryptedMutation {

    }
}
