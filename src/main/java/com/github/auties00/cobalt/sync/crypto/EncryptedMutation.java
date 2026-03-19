package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.model.sync.SyncActionDataBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionDataSpec;
import com.github.auties00.cobalt.model.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.util.FastRandomUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Represents the result of encrypting a sync mutation for upload to the server.
 *
 * <p>Contains the computed index MAC, the encrypted value (IV || ciphertext || value MAC),
 * the key ID, and the operation type.
 *
 * @param indexMac       the HMAC-SHA256 of the index bytes using the index key
 * @param encryptedValue the encrypted payload: IV (16 bytes) || AES-CBC ciphertext || value MAC (32 bytes)
 * @param keyId          the sync key ID used for encryption
 * @param operation      the sync operation type (SET or REMOVE)
 * @implNote WAWebSyncdEncryptMutations.syncdEncryptMutation,
 *           WAWebSyncdEncryptionManager.WASyncdEncryptionManager.encryptMutation
 */
public record EncryptedMutation(
        byte[] indexMac,
        byte[] encryptedValue,
        byte[] keyId,
        SyncdOperation operation
) {
    private static final int IV_LENGTH = 16; // WAWebSyncdCryptoConst.IV_LENGTH
    private static final int MAC_LENGTH = 32; // WAWebSyncdCryptoConst.MAC_LENGTH

    /**
     * Encrypts a pending mutation into the wire format expected by the server.
     *
     * <p>Performs the following steps matching WA Web's {@code syncdEncryptMutation}:
     * <ol>
     *   <li>Builds the {@code SyncActionData} protobuf with index, value, padding, and version</li>
     *   <li>Encodes it to protobuf bytes</li>
     *   <li>Encrypts with AES-256-CBC using a random 16-byte IV</li>
     *   <li>Computes value MAC via HMAC-SHA512 truncated to 32 bytes</li>
     *   <li>Concatenates IV || ciphertext || value MAC</li>
     *   <li>Computes index MAC via HMAC-SHA256</li>
     * </ol>
     *
     * @param patch the pending mutation to encrypt
     * @param keys  the derived mutation keys
     * @param keyId the sync key ID bytes
     * @return a new {@code EncryptedMutation} with the encrypted data
     * @throws GeneralSecurityException if any cryptographic operation fails
     * @implNote WAWebSyncdEncryptMutations.syncdEncryptMutation,
     *           WAWebSyncdMutationsCryptoUtils.generateCipherText,
     *           WAWebSyncdMutationsCryptoUtils.generateAssociatedData,
     *           WAWebSyncdMutationsCryptoUtils.generateMac,
     *           WAWebSyncdMutationsCryptoUtils.generatePadding,
     *           WAWebSyncdCrypto.generateIndexMac,
     *           WAWebSyncdRequestEncode.encodeSyncActionData
     */
    public static EncryptedMutation of(
            SyncPendingMutation patch,
            MutationKeys keys,
            byte[] keyId
    ) throws GeneralSecurityException {
        // Create ActionDataSync with no padding (WA Web uses MAX_OF_MIN_DATA_LENGTH = 0)
        var mutation = patch.mutation();
        var actionVersion = mutation.value() // ADAPTED: Cobalt gets version from action; WA Web stores it as top-level field
                .action()
                .orElseThrow(() -> new IllegalArgumentException("Sync action must be present"))
                .actionVersion();
        var actionData = new SyncActionDataBuilder() // WAWebSyncdEncryptMutations: encodeSyncActionData({index, value, padding, version})
                .index(patch.mutation().index().getBytes(StandardCharsets.UTF_8)) // WAArrayBufferUtils.stringToArrayBuffer(l)
                .value(mutation.value()) // decodeProtobuf(SyncActionValueSpec, binarySyncAction)
                .padding(new byte[0]) // WAWebSyncdMutationsCryptoUtils.generatePadding — always 0 since MAX_OF_MIN_DATA_LENGTH = 0
                .version(actionVersion)
                .build();

        // Encode to protobuf — WAWebSyncdRequestEncode.encodeSyncActionData
        var plaintext = SyncActionDataSpec.encode(actionData);

        // Encrypt with AES-256-CBC — WAWebSyncdMutationsCryptoUtils.generateCipherText → WACryptoAesCbc.aesCbcEncrypt
        var iv = new byte[IV_LENGTH]; // WAWebSyncdCryptoConst.IV_LENGTH = 16
        FastRandomUtils.randomByteArray(iv, 0, IV_LENGTH); // WACryptoDependencies.getCrypto().getRandomValues
        var ivSpec = new IvParameterSpec(iv);
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // ADAPTED: WACryptoAesCbc.aesCbcEncrypt
        cipher.init(Cipher.ENCRYPT_MODE, keys.valueEncryptionKey(), ivSpec);
        var ciphertextLength = cipher.getOutputSize(plaintext.length);
        var encryptedValue = new byte[IV_LENGTH + ciphertextLength + MAC_LENGTH]; // WACryptoAesCbc.aesCbcEncrypt prepends IV; WAWebSyncdCryptoUtils.combine([b, S])
        System.arraycopy(iv, 0, encryptedValue, 0, IV_LENGTH);
        if(cipher.doFinal(plaintext, 0, plaintext.length, encryptedValue, IV_LENGTH) != ciphertextLength) {
            throw new InternalError("Ciphertext length mismatch");
        }

        // Build associated data: [opByte] || keyIdBytes — WAWebSyncdMutationsCryptoUtils.generateAssociatedData
        var opByte = mutation.operation().content(); // WAWebSyncdCryptoConst.OPERATION_SET_HEX / OPERATION_REMOVE_HEX
        var associatedData = new byte[1 + keyId.length];
        associatedData[0] = opByte;
        System.arraycopy(keyId, 0, associatedData, 1, keyId.length);

        // Build 8-byte length suffix: last byte = associatedData.length — WAWebSyncdMutationsCryptoUtils.generateMac
        var lengthSuffix = new byte[8]; // WAWebSyncdCryptoConst.OCTET_LENGTH = 8
        lengthSuffix[7] = (byte) associatedData.length;

        // Compute value MAC using HMAC-SHA-512 truncated to 32 bytes — WAWebSyncdMutationsCryptoUtils.generateMac
        var mac = Mac.getInstance("HmacSHA512"); // WACryptoHmac.hmacSha512
        mac.init(keys.valueMacKey());
        mac.update(associatedData); // WAWebSyncdMutationsCryptoUtils.generateMac: combine([associatedData, IV||ciphertext, lengthSuffix])
        mac.update(encryptedValue, 0, IV_LENGTH + ciphertextLength); // IV || ciphertext
        mac.update(lengthSuffix);
        var fullMac = mac.doFinal();
        System.arraycopy(fullMac, 0, encryptedValue, IV_LENGTH + ciphertextLength, MAC_LENGTH); // WAWebSyncdCryptoConst.MAC_LENGTH = 32

        // Compute index MAC — WAWebSyncdCrypto.generateIndexMac
        var indexBytes = mutation.index().getBytes(StandardCharsets.UTF_8); // WAArrayBufferUtils.stringToArrayBuffer
        var indexMac = Mac.getInstance("HmacSHA256"); // WACryptoHmac.hmacSha256
        indexMac.init(keys.indexKey());
        var indexMacResult = indexMac.doFinal(indexBytes);

        // Create EncryptedMutation — WAWebSyncdEncryptMutations: return {indexMac, indexAndValueCipherText}
        return new EncryptedMutation(indexMacResult, encryptedValue, keyId, mutation.operation());
    }

    /**
     * Extracts the value MAC from the encrypted value (last 32 bytes).
     *
     * @return the 32-byte value MAC
     * @implNote WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText
     */
    public byte[] valueMac() {
        return Arrays.copyOfRange(encryptedValue, encryptedValue.length - MAC_LENGTH, encryptedValue.length);
    }
}
