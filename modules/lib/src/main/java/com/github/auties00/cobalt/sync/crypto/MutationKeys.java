package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.util.DataUtils;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.Mac;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Holds the five derived encryption keys used for app state sync mutations.
 *
 * <p>Keys are derived from a sync key via HKDF-SHA256 with info string
 * {@code "WhatsApp Mutation Keys"}, producing 160 bytes which are split into
 * five 32-byte keys: indexKey, valueEncryptionKey, valueMacKey, snapshotMacKey,
 * and patchMacKey.
 *
 * <p>Implements {@link AutoCloseable} to allow secure destruction of key material.
 *
 * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized,
 *           WAWebSyncdCryptoConst (HKDF_INFO, DERIVED_KEY_LENGTH, key offset constants),
 *           WAWebSyncdCrypto.generateEncryptionKeys (memoized wrapper),
 *           WAWebSyncdMutationsCryptoUtils (generateAssociatedData, generatePadding,
 *           generateCipherText, generateMac, decryptCipherText).
 *
 *           WAWebSyncdKeyCache is also folded into this class's ecosystem: the WA Web
 *           module maintains a process-wide {@code Map<base64(keyId), AppStateSyncKey>}
 *           in front of an IndexedDB {@code SyncKeyStore} and exposes two exports,
 *           {@code getKeyData(keyId)} and {@code clearSyncKeysCache()}. Cobalt collapses
 *           this cache into {@code AbstractWhatsAppStore.appStateKeys} (a hex-keyed
 *           {@code LinkedHashMap<String, AppStateSyncKey>}) so lookups already hit an
 *           in-memory structure and no separate cache layer is needed:
 *           <ul>
 *             <li>{@code WAWebSyncdKeyCache.getKeyData(keyId)} maps to
 *                 {@code WhatsAppStore.findWebAppStateKeyById(byte[])} followed by
 *                 {@link com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey#keyData()};</li>
 *             <li>{@code WAWebSyncdKeyCache.clearSyncKeysCache()} has no Cobalt
 *                 counterpart: there is no secondary cache to invalidate — the store
 *                 map IS the cache, and it is cleared as part of normal store
 *                 teardown, not via a dedicated API.</li>
 *           </ul>
 *           Per-batch memoization inside a single mutation round is achieved by
 *           deriving a {@link MutationKeys} instance once from the looked-up sync
 *           key data and reusing it for all mutations in the batch.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdCryptoConst")
@WhatsAppWebModule(moduleName = "WAWebSyncdMutationsCryptoUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdCryptoHelper")
@WhatsAppWebModule(moduleName = "WAWebSyncdCrypto")
@WhatsAppWebModule(moduleName = "WAWebSyncdEncryptionManager")
@WhatsAppWebModule(moduleName = "WAWebSyncdKeyCache")
public final class MutationKeys implements AutoCloseable {
    /**
     * HKDF info string used for mutation key derivation.
     *
     * @implNote WAWebSyncdCryptoConst.HKDF_INFO — raw string literal
     *           {@code "WhatsApp Mutation Keys"} passed as the HKDF-SHA256 info
     *           parameter when expanding a sync key into the five mutation keys.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "HKDF_INFO", adaptation = WhatsAppAdaptation.DIRECT)
    private static final String HKDF_INFO = "WhatsApp Mutation Keys"; // WAWebSyncdCryptoConst.HKDF_INFO

    /**
     * Total length of the HKDF-expanded key material, in bytes.
     *
     * <p>Equals the sum of the five 32-byte mutation keys (indexKey,
     * valueEncryptionKey, valueMacKey, snapshotMacKey, patchMacKey).
     *
     * @implNote WAWebSyncdCryptoConst.DERIVED_KEY_LENGTH — {@code l = 160}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "DERIVED_KEY_LENGTH", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int DERIVED_KEY_LENGTH = 160; // WAWebSyncdCryptoConst.DERIVED_KEY_LENGTH

    /**
     * End offset (exclusive) of the {@code indexKey} slice within the derived key bytes.
     *
     * <p>The index key occupies bytes {@code [0, INDEX_KEY_END)} of the 160-byte HKDF output.
     *
     * @implNote WAWebSyncdCryptoConst.INDEX_KEY_END — {@code p = s = 32} (first 32-byte segment).
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "INDEX_KEY_END", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int INDEX_KEY_END = 32; // WAWebSyncdCryptoConst.INDEX_KEY_END

    /**
     * End offset (exclusive) of the {@code valueEncryptionKey} slice within the derived key bytes.
     *
     * <p>The value encryption key occupies bytes {@code [INDEX_KEY_END, VALUE_ENCRYPTION_KEY_END)}
     * of the 160-byte HKDF output.
     *
     * @implNote WAWebSyncdCryptoConst.VALUE_ENCRYPTION_KEY_END — {@code _ = p + u = 64}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "VALUE_ENCRYPTION_KEY_END", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int VALUE_ENCRYPTION_KEY_END = 64; // WAWebSyncdCryptoConst.VALUE_ENCRYPTION_KEY_END

    /**
     * End offset (exclusive) of the {@code valueMacKey} slice within the derived key bytes.
     *
     * <p>The value MAC key occupies bytes {@code [VALUE_ENCRYPTION_KEY_END, VALUE_MAC_KEY_END)}
     * of the 160-byte HKDF output.
     *
     * @implNote WAWebSyncdCryptoConst.VALUE_MAC_KEY_END — {@code f = _ + c = 96}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "VALUE_MAC_KEY_END", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int VALUE_MAC_KEY_END = 96; // WAWebSyncdCryptoConst.VALUE_MAC_KEY_END

    /**
     * End offset (exclusive) of the {@code snapshotMacKey} slice within the derived key bytes.
     *
     * <p>The snapshot MAC key occupies bytes {@code [VALUE_MAC_KEY_END, SNAPSHOT_MAC_KEY_END)}
     * of the 160-byte HKDF output.
     *
     * @implNote WAWebSyncdCryptoConst.SNAPSHOT_MAC_KEY_END — {@code g = f + d = 128}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "SNAPSHOT_MAC_KEY_END", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int SNAPSHOT_MAC_KEY_END = 128; // WAWebSyncdCryptoConst.SNAPSHOT_MAC_KEY_END

    /**
     * End offset (exclusive) of the {@code patchMacKey} slice within the derived key bytes.
     *
     * <p>The patch MAC key occupies bytes {@code [SNAPSHOT_MAC_KEY_END, PATCH_MAC_KEY_END)}
     * of the 160-byte HKDF output. This is also the total derived key length.
     *
     * @implNote WAWebSyncdCryptoConst.PATCH_MAC_KEY_END — {@code h = g + m = 160}, equal to
     *           {@link #DERIVED_KEY_LENGTH}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "PATCH_MAC_KEY_END", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int PATCH_MAC_KEY_END = 160; // WAWebSyncdCryptoConst.PATCH_MAC_KEY_END

    /**
     * Hex-encoded operation byte for {@link SyncdOperation#SET} mutations.
     *
     * <p>Used in {@code WAWebSyncdMutationsCryptoUtils.generateAssociatedData} where it is
     * parsed via {@code parseInt(OPERATION_SET_HEX, 16)} to produce the byte {@code 0x01}
     * prepended to the associated data for authenticated encryption.
     *
     * <p>Retained for provenance only: the actual byte used by
     * {@link #generateAssociatedData(SyncdOperation, byte[])} is obtained directly from
     * {@link SyncdOperation#content()} ({@code 0x01} for {@code SET}), which bypasses the
     * hex-string parsing step from WA Web.
     *
     * @implNote WAWebSyncdCryptoConst.OPERATION_SET_HEX — {@code y = "0x01"}. Cobalt reads
     *           the operation byte from {@link SyncdOperation#content()} instead of parsing
     *           this hex string.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "OPERATION_SET_HEX", adaptation = WhatsAppAdaptation.ADAPTED)
    @SuppressWarnings("unused") // kept for source provenance; actual byte comes from SyncdOperation.content()
    private static final String OPERATION_SET_HEX = "0x01"; // WAWebSyncdCryptoConst.OPERATION_SET_HEX

    /**
     * Hex-encoded operation byte for {@link SyncdOperation#REMOVE} mutations.
     *
     * <p>Used in {@code WAWebSyncdMutationsCryptoUtils.generateAssociatedData} where it is
     * parsed via {@code parseInt(OPERATION_REMOVE_HEX, 16)} to produce the byte {@code 0x02}
     * prepended to the associated data for authenticated encryption.
     *
     * <p>Retained for provenance only: the actual byte used by
     * {@link #generateAssociatedData(SyncdOperation, byte[])} is obtained directly from
     * {@link SyncdOperation#content()} ({@code 0x02} for {@code REMOVE}), which bypasses the
     * hex-string parsing step from WA Web.
     *
     * @implNote WAWebSyncdCryptoConst.OPERATION_REMOVE_HEX — {@code C = "0x02"}. Cobalt reads
     *           the operation byte from {@link SyncdOperation#content()} instead of parsing
     *           this hex string.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "OPERATION_REMOVE_HEX", adaptation = WhatsAppAdaptation.ADAPTED)
    @SuppressWarnings("unused") // kept for source provenance; actual byte comes from SyncdOperation.content()
    private static final String OPERATION_REMOVE_HEX = "0x02"; // WAWebSyncdCryptoConst.OPERATION_REMOVE_HEX

    /**
     * The length of the truncated HMAC value MAC, in bytes.
     *
     * @implNote WAWebSyncdCryptoConst.MAC_LENGTH — {@code v = 32}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "MAC_LENGTH", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int MAC_LENGTH = 32; // WAWebSyncdCryptoConst.MAC_LENGTH

    /**
     * The length of the length-suffix buffer used when computing the value MAC, in bytes.
     *
     * @implNote WAWebSyncdCryptoConst.OCTET_LENGTH — {@code S = 8}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "OCTET_LENGTH", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int OCTET_LENGTH = 8; // WAWebSyncdCryptoConst.OCTET_LENGTH

    /**
     * The length of the initialization vector for AES-CBC encryption, in bytes.
     *
     * @implNote WAWebSyncdCryptoConst.IV_LENGTH — {@code R = 16}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "IV_LENGTH", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int IV_LENGTH = 16; // WAWebSyncdCryptoConst.IV_LENGTH

    /**
     * The lower bound for the combined index and value data length before padding kicks in.
     *
     * <p>Currently {@code 0}, which means {@link #generatePadding(int, int)} always returns an
     * empty array. Retained as a named constant for provenance and forward compatibility with
     * future non-zero values in {@code WAWebSyncdCryptoConst}.
     *
     * @implNote WAWebSyncdCryptoConst.MAX_OF_MIN_DATA_LENGTH — {@code b = 0}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoConst", exports = "MAX_OF_MIN_DATA_LENGTH", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int MAX_OF_MIN_DATA_LENGTH = 0; // WAWebSyncdCryptoConst.MAX_OF_MIN_DATA_LENGTH

    private final SecretKeySpec indexKey;
    private final SecretKeySpec valueEncryptionKey;
    private final SecretKeySpec valueMacKey;
    private final SecretKeySpec snapshotMacKey;
    private final SecretKeySpec patchMacKey;


    /**
     * Constructs a new set of mutation keys from the five derived key specs.
     *
     * @param indexKey            the key for computing index MACs (HMAC-SHA256)
     * @param valueEncryptionKey  the key for AES-CBC encryption/decryption
     * @param valueMacKey         the key for computing value MACs (HMAC-SHA512)
     * @param snapshotMacKey      the key for computing snapshot MACs (HMAC-SHA256)
     * @param patchMacKey         the key for computing patch MACs (HMAC-SHA256)
     * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized
     */
    private MutationKeys(SecretKeySpec indexKey, SecretKeySpec valueEncryptionKey, SecretKeySpec valueMacKey, SecretKeySpec snapshotMacKey, SecretKeySpec patchMacKey) {
        this.indexKey = Objects.requireNonNull(indexKey, "Index key cannot be null");
        this.valueEncryptionKey = Objects.requireNonNull(valueEncryptionKey, "Value encryption key cannot be null");
        this.valueMacKey = Objects.requireNonNull(valueMacKey, "Value MAC key cannot be null");
        this.snapshotMacKey = Objects.requireNonNull(snapshotMacKey, "Snapshot MAC key cannot be null") ;
        this.patchMacKey = Objects.requireNonNull(patchMacKey, "Patch MAC key cannot be null");
    }

    /**
     * Derives the five mutation keys from the given sync key data using HKDF-SHA256.
     *
     * <p>The derivation performs extract-then-expand with no salt, info string
     * {@code "WhatsApp Mutation Keys"}, and output length 160 bytes. The resulting
     * bytes are split at offsets 0, 32, 64, 96, 128 matching the WA Web constants
     * {@code INDEX_KEY_END}, {@code VALUE_ENCRYPTION_KEY_END}, {@code VALUE_MAC_KEY_END},
     * {@code SNAPSHOT_MAC_KEY_END}, and {@code PATCH_MAC_KEY_END}.
     *
     * @param syncKey the raw sync key data (must be 32 bytes)
     * @return a new {@code MutationKeys} instance with the five derived keys
     * @throws NullPointerException     if {@code syncKey} is {@code null}
     * @throws IllegalArgumentException if {@code syncKey} is not 32 bytes
     * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized,
     *           WAWebSyncdCrypto.generateEncryptionKeys (memoized wrapper over the helper),
     *           WACryptoHkdf.extractAndExpand,
     *           WAWebSyncdCryptoConst (INDEX_KEY_END=32, VALUE_ENCRYPTION_KEY_END=64,
     *           VALUE_MAC_KEY_END=96, SNAPSHOT_MAC_KEY_END=128, PATCH_MAC_KEY_END=160).
     *           The memoization performed by {@code WAMemoizeCache.memoizeWithArgs} in
     *           {@code WAWebSyncdCrypto.generateEncryptionKeys} is a JS-specific performance
     *           optimization and is not replicated in Cobalt.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCrypto", exports = "generateEncryptionKeys", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoHelper", exports = "generateEncryptionKeysUnmemoized", adaptation = WhatsAppAdaptation.DIRECT)
    public static MutationKeys ofSyncKey(byte[] syncKey) {
        if (syncKey == null) {
            throw new NullPointerException("Sync key cannot be null");
        }

        if (syncKey.length != 32) {
            throw new IllegalArgumentException("Sync key must be 32 bytes, got " + syncKey.length);
        }

        try {
            var kdf = KDF.getInstance("HKDF-SHA256"); // ADAPTED: WACryptoHkdf.extractAndExpand
            var params = HKDFParameterSpec.ofExtract() // WACryptoHkdf.extractAndExpand — extract with null salt
                    .addIKM(syncKey) // WASyncdKeyTypes.fromSyncKeyData (identity)
                    .thenExpand(HKDF_INFO.getBytes(StandardCharsets.UTF_8), DERIVED_KEY_LENGTH); // WAWebSyncdCryptoConst.HKDF_INFO, DERIVED_KEY_LENGTH
            var derivedBytes = kdf.deriveData(params);

            return new MutationKeys(
                    new SecretKeySpec(derivedBytes, 0, INDEX_KEY_END, "HmacSHA256"), // WAWebSyncdCryptoConst: [0, INDEX_KEY_END)
                    new SecretKeySpec(derivedBytes, INDEX_KEY_END, VALUE_ENCRYPTION_KEY_END - INDEX_KEY_END, "AES"), // WAWebSyncdCryptoConst: [INDEX_KEY_END, VALUE_ENCRYPTION_KEY_END)
                    new SecretKeySpec(derivedBytes, VALUE_ENCRYPTION_KEY_END, VALUE_MAC_KEY_END - VALUE_ENCRYPTION_KEY_END, "HmacSHA512"), // WAWebSyncdCryptoConst: [VALUE_ENCRYPTION_KEY_END, VALUE_MAC_KEY_END)
                    new SecretKeySpec(derivedBytes, VALUE_MAC_KEY_END, SNAPSHOT_MAC_KEY_END - VALUE_MAC_KEY_END, "HmacSHA256"), // WAWebSyncdCryptoConst: [VALUE_MAC_KEY_END, SNAPSHOT_MAC_KEY_END)
                    new SecretKeySpec(derivedBytes, SNAPSHOT_MAC_KEY_END, PATCH_MAC_KEY_END - SNAPSHOT_MAC_KEY_END, "HmacSHA256") // WAWebSyncdCryptoConst: [SNAPSHOT_MAC_KEY_END, PATCH_MAC_KEY_END)
            );
        } catch (GeneralSecurityException e) {
            throw new InternalError("Failed to derive keys", e);
        }
    }

    /**
     * Computes an index MAC from the given index bytes using this instance's index key.
     *
     * <p>Performs HMAC-SHA256 over the index bytes, matching WA Web's
     * {@code generateIndexMac(indexKey, indexBytes)} which delegates to
     * {@code WACryptoHmac.hmacSha256}.
     *
     * @param indexBytes the raw index bytes to MAC
     * @return the 32-byte HMAC-SHA256 result
     * @throws GeneralSecurityException if the HMAC operation fails
     * @implNote WAWebSyncdCrypto.generateIndexMac,
     *           WACryptoHmac.hmacSha256
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCrypto", exports = "generateIndexMac", adaptation = WhatsAppAdaptation.DIRECT)
    public byte[] generateIndexMac(byte[] indexBytes) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256"); // WACryptoHmac.hmacSha256
        mac.init(indexKey);
        return mac.doFinal(indexBytes); // WAWebSyncdCrypto.generateIndexMac: hmacSha256(new Uint8Array(e), new Uint8Array(t))
    }

    /**
     * Extracts the value MAC from an encrypted value buffer.
     *
     * <p>The value MAC occupies the last {@value #MAC_LENGTH} bytes of the encrypted value,
     * matching WA Web's {@code valueMacFromIndexAndValueCipherText} which slices
     * {@code e.slice(byteLength - MAC_LENGTH)}.
     *
     * @param encryptedValue the encrypted value bytes (IV || ciphertext || MAC)
     * @return the {@value #MAC_LENGTH}-byte value MAC
     * @throws IllegalArgumentException if {@code encryptedValue} is shorter than {@value #MAC_LENGTH} bytes
     * @implNote WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText,
     *           WAWebSyncdCryptoConst.MAC_LENGTH
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCrypto", exports = "valueMacFromIndexAndValueCipherText", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] valueMacFromIndexAndValueCipherText(byte[] encryptedValue) {
        if (encryptedValue.length < MAC_LENGTH) { // ADAPTED: Java defensive check
            throw new IllegalArgumentException("Encrypted value too short to contain a MAC");
        }
        return Arrays.copyOfRange( // WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText: new Uint8Array(e).slice(t - MAC_LENGTH)
                encryptedValue,
                encryptedValue.length - MAC_LENGTH,
                encryptedValue.length
        );
    }

    /**
     * Generates the associated data (AAD) for authenticated encryption of a sync mutation.
     *
     * <p>Builds a byte array consisting of the operation byte (0x01 for SET, 0x02 for REMOVE)
     * followed by the raw sync key ID bytes. The operation byte is derived by parsing the
     * hex constant from {@code WAWebSyncdCryptoConst} (e.g., {@code "0x01"}) through
     * {@code parseInt(hex, 16)}, and the key ID bytes are obtained via
     * {@code WASyncdKeyTypes.fromSyncKeyId} (identity function).
     *
     * @param operation the sync operation type (SET or REMOVE)
     * @param keyId     the raw sync key ID bytes
     * @return the associated data byte array: {@code [opByte || keyId]}
     * @throws NullPointerException if {@code operation} or {@code keyId} is {@code null}
     * @implNote WAWebSyncdMutationsCryptoUtils.generateAssociatedData,
     *           WAWebSyncdCryptoConst.OPERATION_SET_HEX,
     *           WAWebSyncdCryptoConst.OPERATION_REMOVE_HEX,
     *           WASyncdKeyTypes.fromSyncKeyId
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMutationsCryptoUtils", exports = "generateAssociatedData", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] generateAssociatedData(SyncdOperation operation, byte[] keyId) {
        Objects.requireNonNull(operation, "Operation cannot be null"); // ADAPTED: Java null-safety guard
        Objects.requireNonNull(keyId, "Key ID cannot be null"); // ADAPTED: Java null-safety guard
        var opByte = operation.content(); // WAWebSyncdMutationsCryptoUtils.generateAssociatedData: parseInt(r, 16) where r is OPERATION_SET_HEX or OPERATION_REMOVE_HEX
        var result = new byte[1 + keyId.length]; // WAWebSyncdMutationsCryptoUtils.generateAssociatedData: new Uint8Array(a.byteLength + n.byteLength)
        result[0] = opByte; // WAWebSyncdMutationsCryptoUtils.generateAssociatedData: i.set(new Uint8Array(a))
        System.arraycopy(keyId, 0, result, 1, keyId.length); // WAWebSyncdMutationsCryptoUtils.generateAssociatedData: i.set(new Uint8Array(n), a.byteLength)
        return result; // WAWebSyncdMutationsCryptoUtils.generateAssociatedData: return i.buffer
    }

    /**
     * Generates PKCS7-style padding for plaintext before encryption.
     *
     * <p>Computes padding length as {@code max(0, MAX_OF_MIN_DATA_LENGTH - indexLength - valueLength)}.
     * Since {@code WAWebSyncdCryptoConst.MAX_OF_MIN_DATA_LENGTH} is currently {@code 0},
     * this always produces an empty byte array. The padding bytes would otherwise be filled
     * with random values from {@code WACryptoDependencies.getCrypto().getRandomValues}.
     *
     * @param indexLength the byte length of the index buffer
     * @param valueLength the byte length of the binary sync action value
     * @return the padding byte array (currently always empty)
     * @implNote WAWebSyncdMutationsCryptoUtils.generatePadding,
     *           WAWebSyncdCryptoConst.MAX_OF_MIN_DATA_LENGTH,
     *           WACryptoDependencies.getCrypto().getRandomValues
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMutationsCryptoUtils", exports = "generatePadding", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] generatePadding(int indexLength, int valueLength) {
        var paddingLength = Math.max(0, MAX_OF_MIN_DATA_LENGTH - indexLength - valueLength); // WAWebSyncdMutationsCryptoUtils.generatePadding: Math.max(0, MAX_OF_MIN_DATA_LENGTH - e - t)
        var padding = new byte[paddingLength]; // WAWebSyncdMutationsCryptoUtils.generatePadding: new Uint8Array(n)
        if (paddingLength > 0) {
            DataUtils.randomByteArray(padding, 0, paddingLength); // WAWebSyncdMutationsCryptoUtils.generatePadding: getCrypto().getRandomValues(r)
        }
        return padding; // WAWebSyncdMutationsCryptoUtils.generatePadding: return r.buffer
    }

    /**
     * Encrypts plaintext using AES-CBC with this instance's value encryption key.
     *
     * <p>Generates a random 16-byte IV, encrypts the plaintext with AES-CBC (PKCS5 padding),
     * and returns the result as {@code [IV || ciphertext]}. This matches WA Web's
     * {@code WACryptoAesCbc.aesCbcEncrypt} which prepends the IV to the encrypted output.
     *
     * @param plaintext the plaintext bytes to encrypt
     * @return the encrypted bytes: {@code [IV (16 bytes) || ciphertext]}
     * @throws GeneralSecurityException if the AES-CBC encryption fails
     * @implNote WAWebSyncdMutationsCryptoUtils.generateCipherText,
     *           WACryptoAesCbc.aesCbcEncrypt,
     *           WAWebSyncdCryptoConst.IV_LENGTH
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMutationsCryptoUtils", exports = "generateCipherText", adaptation = WhatsAppAdaptation.ADAPTED)
    public byte[] generateCipherText(byte[] plaintext) throws GeneralSecurityException {
        var iv = new byte[IV_LENGTH]; // WAWebSyncdMutationsCryptoUtils.generateCipherText → WACryptoAesCbc.aesCbcEncrypt: getIv(optionalIv)
        DataUtils.randomByteArray(iv, 0, IV_LENGTH); // WACryptoDependencies.getCrypto().getRandomValues
        return generateCipherText(iv, plaintext);
    }

    /**
     * Encrypts plaintext using AES-CBC with this instance's value encryption key
     * and the specified IV.
     *
     * <p>Encrypts the plaintext with AES-CBC (PKCS5 padding) using the given IV,
     * and returns the result as {@code [IV || ciphertext]}. This matches WA Web's
     * {@code WACryptoAesCbc.aesCbcEncrypt} which prepends the IV to the encrypted output.
     *
     * @param iv        the 16-byte initialization vector
     * @param plaintext the plaintext bytes to encrypt
     * @return the encrypted bytes: {@code [IV (16 bytes) || ciphertext]}
     * @throws GeneralSecurityException if the AES-CBC encryption fails
     * @implNote WAWebSyncdMutationsCryptoUtils.generateCipherText,
     *           WACryptoAesCbc.aesCbcEncrypt
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMutationsCryptoUtils", exports = "generateCipherText", adaptation = WhatsAppAdaptation.DIRECT)
    public byte[] generateCipherText(byte[] iv, byte[] plaintext) throws GeneralSecurityException {
        var ivSpec = new IvParameterSpec(iv); // WAWebSyncdMutationsCryptoUtils.generateCipherText → WACryptoAesCbc.aesCbcEncrypt: _(iv)
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // ADAPTED: WACryptoAesCbc.aesCbcEncrypt uses WebCrypto AES-CBC
        cipher.init(Cipher.ENCRYPT_MODE, valueEncryptionKey, ivSpec); // WACryptoAesCbc.aesCbcEncrypt: importKey(key, "encrypt")
        var ciphertext = cipher.doFinal(plaintext); // WACryptoAesCbc.aesCbcEncrypt: crypto.subtle.encrypt(algo, key, plaintext)
        var result = new byte[IV_LENGTH + ciphertext.length]; // WACryptoAesCbc.aesCbcEncrypt: concatTypedArrays([iv, encrypted])
        System.arraycopy(iv, 0, result, 0, IV_LENGTH); // WACryptoAesCbc.aesCbcEncrypt: prepend IV
        System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length); // WACryptoAesCbc.aesCbcEncrypt: append encrypted
        return result; // WACryptoAesCbc.aesCbcEncrypt: return concatenated.buffer
    }

    /**
     * Generates a truncated HMAC-SHA512 MAC over the associated data and ciphertext
     * using this instance's value MAC key.
     *
     * <p>Builds the MAC input as {@code combine([associatedData, ciphertext, lengthSuffix])}
     * where {@code lengthSuffix} is an 8-byte (OCTET_LENGTH) buffer with the associated data
     * length stored in the last byte. The MAC is computed via HMAC-SHA512 and truncated to
     * {@value #MAC_LENGTH} bytes.
     *
     * @param associatedData the associated data bytes (typically from {@link #generateAssociatedData})
     * @param ciphertext     the ciphertext bytes (typically IV || encrypted from {@link #generateCipherText})
     * @return the {@value #MAC_LENGTH}-byte truncated HMAC-SHA512 value MAC
     * @throws GeneralSecurityException if the HMAC operation fails
     * @implNote WAWebSyncdMutationsCryptoUtils.generateMac,
     *           WAWebSyncdCryptoConst.OCTET_LENGTH,
     *           WAWebSyncdCryptoConst.MAC_LENGTH,
     *           WAWebSyncdCryptoUtils.combine,
     *           WACryptoHmac.hmacSha512
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMutationsCryptoUtils", exports = "generateMac", adaptation = WhatsAppAdaptation.DIRECT)
    public byte[] generateMac(byte[] associatedData, byte[] ciphertext) throws GeneralSecurityException {
        var lengthSuffix = new byte[OCTET_LENGTH]; // WAWebSyncdMutationsCryptoUtils.generateMac: new Uint8Array(OCTET_LENGTH)
        lengthSuffix[OCTET_LENGTH - 1] = (byte) associatedData.length; // WAWebSyncdMutationsCryptoUtils.generateMac: r.set([e.byteLength], r.byteLength - 1)
        var mac = Mac.getInstance("HmacSHA512"); // WAWebSyncdMutationsCryptoUtils.generateMac → WACryptoHmac.hmacSha512
        mac.init(valueMacKey); // WACryptoHmac.hmacSha512: importKey(key)
        mac.update(associatedData); // WAWebSyncdMutationsCryptoUtils.generateMac: combine([e, t, r.buffer]) — part 1
        mac.update(ciphertext); // WAWebSyncdMutationsCryptoUtils.generateMac: combine([e, t, r.buffer]) — part 2
        mac.update(lengthSuffix); // WAWebSyncdMutationsCryptoUtils.generateMac: combine([e, t, r.buffer]) — part 3
        var fullMac = mac.doFinal(); // WACryptoHmac.sign: crypto.subtle.sign(algo, key, data)
        return Arrays.copyOf(fullMac, MAC_LENGTH); // WACryptoHmac.sign: e.slice(0, MAC_LENGTH)
    }

    /**
     * Decrypts ciphertext using AES-CBC with this instance's value encryption key.
     *
     * <p>Decrypts the ciphertext using the provided IV and the value encryption key,
     * then removes PKCS5 padding. This matches WA Web's
     * {@code WACryptoAesCbc.aesCbcDecrypt(key, iv, ciphertext)}.
     *
     * @param iv         the 16-byte initialization vector
     * @param ciphertext the ciphertext bytes to decrypt (without IV prefix)
     * @return the decrypted plaintext bytes
     * @throws GeneralSecurityException if the AES-CBC decryption fails
     * @implNote WAWebSyncdMutationsCryptoUtils.decryptCipherText,
     *           WACryptoAesCbc.aesCbcDecrypt
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMutationsCryptoUtils", exports = "decryptCipherText", adaptation = WhatsAppAdaptation.DIRECT)
    public byte[] decryptCipherText(byte[] iv, byte[] ciphertext) throws GeneralSecurityException {
        var ivSpec = new IvParameterSpec(iv); // WAWebSyncdMutationsCryptoUtils.decryptCipherText → WACryptoAesCbc.aesCbcDecrypt: _(iv)
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // ADAPTED: WACryptoAesCbc.aesCbcDecrypt uses WebCrypto AES-CBC
        cipher.init(Cipher.DECRYPT_MODE, valueEncryptionKey, ivSpec); // WACryptoAesCbc.aesCbcDecrypt: importKey(key, "decrypt")
        return cipher.doFinal(ciphertext); // WACryptoAesCbc.aesCbcDecrypt: crypto.subtle.decrypt(algo, key, ciphertext)
    }

    /**
     * Destroys all key material held by this instance.
     *
     * <p>Silently ignores {@link DestroyFailedException} since not all JCE providers
     * support key destruction.
     *
     * @implNote ADAPTED: Java-specific resource cleanup, no WA Web equivalent
     */
    @Override
    public void close() {
        try {
            indexKey.destroy();
        }catch (DestroyFailedException _) {

        }
        try {
            valueEncryptionKey.destroy();
        }catch (DestroyFailedException _) {

        }
        try {
            valueMacKey.destroy();
        }catch (DestroyFailedException _) {

        }
        try {
            snapshotMacKey.destroy();
        }catch (DestroyFailedException _) {

        }
        try {
            patchMacKey.destroy();
        }catch (DestroyFailedException _) {

        }
    }

    /**
     * Returns a string representation that does not leak key material.
     *
     * @implNote ADAPTED: Java-specific defensive toString, no WA Web equivalent
     * @return the fixed string {@code "AppStateSyncKeys"}
     */
    @Override
    public String toString() {
        return "AppStateSyncKeys";
    }

    /**
     * Returns the index key used for HMAC-SHA256 index MAC computation.
     *
     * @return the index key spec
     * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized — indexKey slice
     */
    public SecretKeySpec indexKey() {
        return indexKey;
    }

    /**
     * Returns the value encryption key used for AES-CBC encryption/decryption.
     *
     * @return the value encryption key spec
     * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized — valueEncryptionKey slice
     */
    public SecretKeySpec valueEncryptionKey() {
        return valueEncryptionKey;
    }

    /**
     * Returns the value MAC key used for HMAC-SHA512 value MAC computation.
     *
     * @return the value MAC key spec
     * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized — valueMacKey slice
     */
    public SecretKeySpec valueMacKey() {
        return valueMacKey;
    }

    /**
     * Returns the snapshot MAC key used for HMAC-SHA256 snapshot MAC computation.
     *
     * @return the snapshot MAC key spec
     * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized — snapshotMacKey slice
     */
    public SecretKeySpec snapshotMacKey() {
        return snapshotMacKey;
    }

    /**
     * Returns the patch MAC key used for HMAC-SHA256 patch MAC computation.
     *
     * @return the patch MAC key spec
     * @implNote WAWebSyncdCryptoHelper.generateEncryptionKeysUnmemoized — patchMacKey slice
     */
    public SecretKeySpec patchMacKey() {
        return patchMacKey;
    }
}
