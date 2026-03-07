package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
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
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;

public sealed interface DecryptedMutation {
    String index();
    SyncdOperation operation();
    Instant timestamp();

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
        private static final int IV_LENGTH = 16;
        private static final int MAC_LENGTH = 32;

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

            // Extract value MAC (last 32 bytes)
            var valueMac = Arrays.copyOfRange(encryptedValue, encryptedValue.length - MAC_LENGTH, encryptedValue.length);

            // Build associated data: [opByte] || keyIdBytes
            var associatedData = new byte[1 + keyId.length];
            associatedData[0] = operation.content();
            System.arraycopy(keyId, 0, associatedData, 1, keyId.length);

            // Build 8-byte length suffix: last byte = associatedData.length
            var lengthSuffix = new byte[8];
            lengthSuffix[7] = (byte) associatedData.length;

            // Verify value MAC using HMAC-SHA-512 truncated to 32 bytes
            var mac = Mac.getInstance("HmacSHA512");
            mac.init(keys.valueMacKey());
            mac.update(associatedData);
            mac.update(encryptedValue, 0, encryptedValue.length - MAC_LENGTH); // IV || ciphertext
            mac.update(lengthSuffix);
            var fullMac = mac.doFinal();
            var expectedMac = Arrays.copyOf(fullMac, MAC_LENGTH);
            if (!MessageDigest.isEqual(valueMac, expectedMac)) {
                throw new WhatsAppWebAppStateSyncException.ValueMacMismatch();
            }

            // Decrypt payload with AES-256-CBC and decode protobuf
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            var ivSpec = new IvParameterSpec(encryptedValue, 0, IV_LENGTH);
            cipher.init(Cipher.DECRYPT_MODE, keys.valueEncryptionKey(), ivSpec);
            var ciphertextStream = new ByteArrayInputStream(encryptedValue, IV_LENGTH, encryptedValue.length - IV_LENGTH - MAC_LENGTH);
            var plaintextStream = new CipherInputStream(ciphertextStream, cipher);
            var actionData = SyncActionDataSpec.decode(ProtobufInputStream.fromStream(plaintextStream));

            // Verify index MAC
            var actionIndex = actionData.index()
                    .orElseThrow(() -> new IllegalStateException("Missing index from action data"));
            var actionValue = actionData.value()
                    .orElseThrow(() -> new IllegalStateException("Missing value from action data"));
            var actionTimestamp = actionValue.timestamp()
                    .orElseThrow(WhatsAppWebAppStateSyncException.MissingActionTimestamp::new);
            var indexMac2 = Mac.getInstance("HmacSHA256");
            indexMac2.init(keys.indexKey());
            var expectedIndexMac = indexMac2.doFinal(actionIndex);
            if (!MessageDigest.isEqual(indexMac, expectedIndexMac)) {
                throw new WhatsAppWebAppStateSyncException.IndexMacMismatch();
            }

            // Per WA Web: missing version is a fatal error, not a default-to-0 case
            var actionVersion = actionData.version()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.TerminalPatch(
                            null, 100));
            return new Untrusted(
                    new String(actionIndex, StandardCharsets.UTF_8),
                    indexMac,
                    valueMac,
                    actionValue,
                    operation,
                    actionTimestamp,
                    keyId,
                    actionVersion
            );
        }
    }

    record Trusted(
            String index,
            SyncActionValue value,
            SyncdOperation operation,
            Instant timestamp,
            int actionVersion
    ) implements DecryptedMutation {

    }
}
