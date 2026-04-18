package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionData;
import com.github.auties00.cobalt.model.sync.SyncActionDataSpec;
import com.github.auties00.cobalt.model.sync.SyncActionValue;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import it.auties.protobuf.stream.ProtobufInputStream;

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
 * <p>{@link Untrusted#of(byte[], byte[], MutationKeys, SyncdOperation, byte[])} adapts
 * {@code WAWebSyncdDecryptMutations.syncdDecryptMutation} and is invoked from the per-mutation
 * loop that adapts {@code WAWebSyncdDecryptMutationsWrapper.tryDecryptSnapshot} and
 * {@code WAWebSyncdDecryptMutationsWrapper.tryDecryptPatch}. The batch wrapper exports
 * live in {@code WebAppStateService.decryptMutations}.
 *
 * @implNote WAWebSyncdDecryptMutations.syncdDecryptMutation,
 *           WAWebSyncdDecryptMutationsWrapper.tryDecryptSnapshot,
 *           WAWebSyncdDecryptMutationsWrapper.tryDecryptPatch
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdDecryptMutations")
@WhatsAppWebModule(moduleName = "WAWebSyncdDecryptMutationsWrapper")
public sealed interface DecryptedMutation {
    /**
     * Returns the index string identifying this mutation's target.
     *
     * @implNote WAWebSyncdDecryptMutations.syncdDecryptMutation — return field {@code index},
     *           decoded from raw bytes via {@code WAWebSyncdDecryptMutationsWrapper} TextDecoder
     * @return the index string
     */
    String index();

    /**
     * Returns the sync operation type (SET or REMOVE).
     *
     * @implNote ADAPTED: WAWebSyncdDecryptMutationsWrapper passes operation from the SyncdMutation
     *           record; Cobalt stores it directly in the mutation result
     * @return the operation type
     */
    SyncdOperation operation();

    /**
     * Returns the timestamp of the action.
     *
     * @implNote ADAPTED: WAWebSyncdValidateMutations.validateAndTypeSetMutations extracts
     *           timestamp for SET mutations only; Cobalt extracts eagerly during decryption
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
        /**
         * Decrypts and verifies an encrypted mutation value.
         *
         * <p>Performs the following steps matching WA Web's {@code syncdDecryptMutation},
         * delegating each cryptographic primitive to {@link MutationKeys}:
         * <ol>
         *   <li>Extracts value MAC from last 32 bytes via
         *       {@link MutationKeys#valueMacFromIndexAndValueCipherText(byte[])}</li>
         *   <li>Splits encrypted value into IV, ciphertext, and MAC</li>
         *   <li>Verifies value MAC via {@link MutationKeys#generateMac(byte[], byte[])}
         *       (HMAC-SHA512 truncated to 32 bytes)</li>
         *   <li>Decrypts ciphertext via {@link MutationKeys#decryptCipherText(byte[], byte[])}
         *       (AES-256-CBC)</li>
         *   <li>Decodes and validates the {@code SyncActionData} protobuf</li>
         *   <li>Verifies index MAC via {@link MutationKeys#generateIndexMac(byte[])}
         *       (HMAC-SHA256)</li>
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
         *           WAWebSyncdDecryptMutationsWrapper (per-mutation body of {@code y}
         *           after the missing-key branch, which in Cobalt is pre-scanned in the
         *           calling batch loop),
         *           WAWebSyncdMutationsCryptoUtils.generateMac,
         *           WAWebSyncdMutationsCryptoUtils.generateAssociatedData,
         *           WAWebSyncdMutationsCryptoUtils.decryptCipherText,
         *           WAWebSyncdCrypto.generateIndexMac,
         *           WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText,
         *           WAWebSyncdDecode.decodeSyncActionData,
         *           WAWebSyncdValidateSyncActionProtobuf.validateSyncActionDataProtobuf
         */
        @WhatsAppWebExport(moduleName = "WAWebSyncdDecryptMutations", exports = "syncdDecryptMutation", adaptation = WhatsAppAdaptation.DIRECT)
        @WhatsAppWebExport(moduleName = "WAWebSyncdDecryptMutationsWrapper", exports = {"tryDecryptSnapshot", "tryDecryptPatch"}, adaptation = WhatsAppAdaptation.ADAPTED)
        public static Untrusted of(
                byte[] encryptedValue,
                byte[] indexMac,
                MutationKeys keys,
                SyncdOperation operation,
                byte[] keyId
        ) throws GeneralSecurityException {
            if (encryptedValue.length < MutationKeys.IV_LENGTH + MutationKeys.MAC_LENGTH) {
                throw new IllegalArgumentException("Encrypted value too short");
            }

            // Extract value MAC (last 32 bytes) — WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText
            var valueMac = MutationKeys.valueMacFromIndexAndValueCipherText(encryptedValue);

            // Build associated data: [opByte] || keyIdBytes — WAWebSyncdMutationsCryptoUtils.generateAssociatedData
            var associatedData = MutationKeys.generateAssociatedData(operation, keyId);

            // Split encrypted value into IV || ciphertext (excluding trailing MAC) — WAWebSyncdCryptoUtils.split
            var ivAndCipherText = Arrays.copyOfRange(
                    encryptedValue,
                    0,
                    encryptedValue.length - MutationKeys.MAC_LENGTH
            );

            // Verify value MAC using HMAC-SHA-512 truncated to 32 bytes — WAWebSyncdMutationsCryptoUtils.generateMac
            var expectedMac = keys.generateMac(associatedData, ivAndCipherText);
            if (!MessageDigest.isEqual(valueMac, expectedMac)) { // WACryptoUtils.arrayBuffersEqual
                throw new WhatsAppWebAppStateSyncException.ValueMacMismatch(); // WAWebSyncdError.SyncdFatalError("decryption failure: valueMAC mismatch")
            }

            // Decrypt payload with AES-256-CBC — WAWebSyncdMutationsCryptoUtils.decryptCipherText
            var iv = Arrays.copyOfRange(encryptedValue, 0, MutationKeys.IV_LENGTH); // WAWebSyncdCryptoUtils.split — iv part
            var cipherText = Arrays.copyOfRange( // WAWebSyncdCryptoUtils.split — ciphertext part
                    encryptedValue,
                    MutationKeys.IV_LENGTH,
                    encryptedValue.length - MutationKeys.MAC_LENGTH
            );
            var plaintext = keys.decryptCipherText(iv, cipherText);

            // Decode protobuf — WAWebSyncdDecode.decodeSyncActionData
            SyncActionData actionData;
            try {
                actionData = SyncActionDataSpec.decode(ProtobufInputStream.fromBytes(plaintext)); // WAWebSyncdDecode.decodeSyncActionData
            } catch (Exception e) {
                throw new WhatsAppWebAppStateSyncException.DecryptionFailed( // WAWebSyncdDecode.decodeSyncActionData — SyncdFatalError("syncd: data protobuf deserialization failed: ...")
                        "syncd: data protobuf deserialization failed: " + e.getMessage(), e);
            }

            // Validate SyncActionData fields — WAWebSyncdValidateSyncActionProtobuf.validateSyncActionDataProtobuf
            var actionIndex = actionData.index() // WAWebSyncdValidateSyncActionProtobuf.validateSyncActionDataProtobuf — index check (!e)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("missing action index", null));
            var actionVersion = actionData.version() // WAWebSyncdValidateSyncActionProtobuf.validateSyncActionDataProtobuf — version check (i == null)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "missing action version", null));
            var actionValue = actionData.value() // ADAPTED: WAWebSyncdDecryptMutations returns value as nullable (null for REMOVE); WAWebSyncdValidateMutations.validateAndTypeSetMutations checks only for SET; Cobalt checks eagerly for all mutations because downstream handlers require non-null value
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing value from action data", null));
            var actionTimestamp = actionValue.timestamp() // ADAPTED: WAWebSyncdDecryptMutations does not extract timestamp; WAWebSyncdValidateMutations.validateAndTypeSetMutations extracts for SET only; Cobalt extracts eagerly because Untrusted/Trusted records require non-null timestamp
                    .orElseThrow(WhatsAppWebAppStateSyncException.MissingActionTimestamp::new);

            // Verify index MAC — WAWebSyncdCrypto.generateIndexMac
            var expectedIndexMac = keys.generateIndexMac(actionIndex);
            if (!MessageDigest.isEqual(indexMac, expectedIndexMac)) { // WACryptoUtils.arrayBuffersEqual
                throw new WhatsAppWebAppStateSyncException.IndexMacMismatch(); // WAWebSyncdError.SyncdFatalError("decryption failure: indexMAC mismatch")
            }
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
