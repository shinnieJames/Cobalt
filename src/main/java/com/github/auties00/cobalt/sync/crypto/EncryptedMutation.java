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

public record EncryptedMutation(
        byte[] indexMac,
        byte[] encryptedValue,
        byte[] keyId,
        SyncdOperation operation
) {
    private static final int IV_LENGTH = 16;
    private static final int MAC_LENGTH = 32;

    public static EncryptedMutation of(
            SyncPendingMutation patch,
            MutationKeys keys,
            byte[] keyId
    ) throws GeneralSecurityException {
        // Create ActionDataSync with no padding (WA Web uses MAX_OF_MIN_DATA_LENGTH = 0)
        var mutation = patch.mutation();
        var actionVersion = mutation.value()
                .action()
                .orElseThrow(() -> new IllegalArgumentException("Sync action must be present"))
                .actionVersion();
        var actionData = new SyncActionDataBuilder()
                .index(patch.mutation().index().getBytes(StandardCharsets.UTF_8))
                .value(mutation.value())
                .padding(new byte[0])
                .version(actionVersion)
                .build();

        // Encode to protobuf
        var plaintext = SyncActionDataSpec.encode(actionData);

        // Encrypt with AES-256-CBC
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        var ciphertextLength = cipher.getOutputSize(plaintext.length);
        var encryptedValue = new byte[IV_LENGTH + ciphertextLength + MAC_LENGTH];
        FastRandomUtils.randomByteArray(encryptedValue, 0, IV_LENGTH);
        var ivSpec = new IvParameterSpec(encryptedValue, 0, IV_LENGTH);
        cipher.init(Cipher.ENCRYPT_MODE, keys.valueEncryptionKey(), ivSpec);
        if(cipher.doFinal(plaintext, 0, plaintext.length, encryptedValue, IV_LENGTH) != ciphertextLength) {
            throw new InternalError("Ciphertext length mismatch");
        }

        // Build associated data: [opByte] || keyIdBytes
        var opByte = mutation.operation().content();
        var associatedData = new byte[1 + keyId.length];
        associatedData[0] = opByte;
        System.arraycopy(keyId, 0, associatedData, 1, keyId.length);

        // Build 8-byte length suffix: last byte = associatedData.length
        var lengthSuffix = new byte[8];
        lengthSuffix[7] = (byte) associatedData.length;

        // Compute value MAC using HMAC-SHA-512 truncated to 32 bytes
        var mac = Mac.getInstance("HmacSHA512");
        mac.init(keys.valueMacKey());
        mac.update(associatedData);
        mac.update(encryptedValue, 0, IV_LENGTH + ciphertextLength); // IV || ciphertext
        mac.update(lengthSuffix);
        var fullMac = mac.doFinal();
        System.arraycopy(fullMac, 0, encryptedValue, IV_LENGTH + ciphertextLength, MAC_LENGTH);

        // Compute index MAC
        var indexBytes = mutation.index().getBytes(StandardCharsets.UTF_8);
        var indexMac = Mac.getInstance("HmacSHA256");
        indexMac.init(keys.indexKey());
        var indexMacResult = indexMac.doFinal(indexBytes);

        // Create EncryptedMutation
        return new EncryptedMutation(indexMacResult, encryptedValue, keyId, mutation.operation());
    }

    /**
     * Extracts the value MAC from the encrypted value (last 32 bytes).
     *
     * @return the 32-byte value MAC
     */
    public byte[] valueMac() {
        return Arrays.copyOfRange(encryptedValue, encryptedValue.length - MAC_LENGTH, encryptedValue.length);
    }
}
