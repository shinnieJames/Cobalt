package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionDataBuilder;
import com.github.auties00.cobalt.model.sync.SyncActionDataSpec;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Represents the result of encrypting a sync mutation for upload to the server.
 *
 * <p>Contains the computed index MAC, the encrypted value (IV || ciphertext || value MAC),
 * the key ID, and the operation type. These four fields mirror the subset of the
 * returned object in {@code WAWebSyncdEncryptMutationsWrapper.encryptMutation} that the
 * rest of the sync pipeline actually consumes: the spread
 * {@code {...t, keyId, keyData, indexMac, indexAndValueCipherText, valueMac}} is flattened
 * into {@code indexMac}, {@code encryptedValue} (which concatenates
 * {@code indexAndValueCipherText} and {@code valueMac} — the wire representation), and
 * {@code keyId}. The {@code keyData} entry on the WA Web result is an intermediate that is
 * not needed after encryption, and the {@code valueMac} is recomputable from
 * {@code encryptedValue} via {@link #valueMac()} so it is not stored separately.
 *
 * @param indexMac       the HMAC-SHA256 of the index bytes using the index key
 * @param encryptedValue the encrypted payload: IV (16 bytes) || AES-CBC ciphertext || value MAC (32 bytes)
 * @param keyId          the sync key ID used for encryption
 * @param operation      the sync operation type (SET or REMOVE)
 * @implNote WAWebSyncdEncryptMutations.syncdEncryptMutation,
 *           WAWebSyncdEncryptionManager.WASyncdEncryptionManager.encryptMutation,
 *           WAWebSyncdEncryptMutationsWrapper.encryptMutation
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdEncryptMutations")
@WhatsAppWebModule(moduleName = "WAWebSyncdEncryptMutationsWrapper")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestEncode")
public record EncryptedMutation(
        byte[] indexMac,
        byte[] encryptedValue,
        byte[] keyId,
        SyncdOperation operation
) {
    /**
     * Encrypts a pending mutation into the wire format expected by the server.
     *
     * <p>Performs the following steps matching WA Web's {@code syncdEncryptMutation},
     * delegating each cryptographic primitive to {@link MutationKeys}:
     * <ol>
     *   <li>Builds the {@code SyncActionData} protobuf with index, value, padding, and version</li>
     *   <li>Encodes it to protobuf bytes</li>
     *   <li>Encrypts with AES-256-CBC via {@link MutationKeys#generateCipherText(byte[])}</li>
     *   <li>Computes value MAC via {@link MutationKeys#generateMac(byte[], byte[])} (HMAC-SHA512 truncated to 32 bytes)</li>
     *   <li>Concatenates IV || ciphertext || value MAC</li>
     *   <li>Computes index MAC via {@link MutationKeys#generateIndexMac(byte[])} (HMAC-SHA256)</li>
     * </ol>
     *
     * <p>This factory also acts as the semantic merge-point for
     * {@code WAWebSyncdEncryptMutationsWrapper.encryptMutation}. The wrapper is a thin
     * orchestration layer on top of {@code syncdEncryptMutation} that adds three
     * responsibilities:
     * <ul>
     *   <li><b>Operation dispatch</b> — {@code SET} uses the caller-supplied active key,
     *       while {@code REMOVE} looks up the original key via
     *       {@code WAWebGetSyncAction.getSyncActionInTransaction(indexString)} and
     *       {@code WAWebSyncdKeyCache.getKeyData(keyId)} before calling
     *       {@code syncdEncryptMutation}.</li>
     *   <li><b>Per-{@code keyId} key-data caching</b> — {@code WAWebSyncdKeyCache.getKeyData}
     *       memoizes the {@code keyData} lookup so repeated REMOVE mutations against the
     *       same key avoid redundant DB reads.</li>
     *   <li><b>Result reshaping</b> — the wrapper returns
     *       {@code {...t, keyId, keyData, indexMac, indexAndValueCipherText, valueMac}}
     *       where {@code valueMac} is extracted via
     *       {@code WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText(indexAndValueCipherText)}.</li>
     * </ul>
     *
     * <p>In Cobalt those three concerns are split as follows:
     * <ul>
     *   <li>Operation dispatch and REMOVE key lookup live in
     *       {@code MutationRequestBuilder.encryptMutations}, which selects between the
     *       caller-supplied active {@code MutationKeys} (for SET) and freshly derived
     *       keys loaded via {@link MutationKeys#ofSyncKey(byte[])} (for REMOVE).</li>
     *   <li>The WA Web per-{@code keyId} cache is replaced by the {@link MutationKeys}
     *       instance itself: once keys are derived for a given sync key, the caller holds
     *       the derived {@code MutationKeys} and reuses it across every mutation sharing
     *       that key for the lifetime of the encryption pass. This is structurally
     *       equivalent to the memoization performed by {@code WAWebSyncdKeyCache} (which
     *       caches {@code keyData}) combined with {@code WAWebSyncdCrypto.generateEncryptionKeys}
     *       (which memoizes the HKDF expansion from {@code keyData}).</li>
     *   <li>Result reshaping is performed by this factory (assembling the
     *       {@code EncryptedMutation} record) and by {@link #valueMac()}, which extracts
     *       the MAC suffix from {@code encryptedValue} on demand, matching
     *       {@code valueMacFromIndexAndValueCipherText}.</li>
     * </ul>
     *
     * @param patch the pending mutation to encrypt
     * @param keys  the derived mutation keys
     * @param keyId the sync key ID bytes
     * @return a new {@code EncryptedMutation} with the encrypted data
     * @throws GeneralSecurityException if any cryptographic operation fails
     * @implNote WAWebSyncdEncryptMutations.syncdEncryptMutation,
     *           WAWebSyncdEncryptMutationsWrapper.encryptMutation (result assembly and
     *           {@code valueMac} extraction; dispatch/key-lookup live in
     *           {@code MutationRequestBuilder.encryptMutations}; per-{@code keyId} caching
     *           is absorbed into the {@link MutationKeys} instance reuse in the caller),
     *           WAWebSyncdMutationsCryptoUtils.generateCipherText,
     *           WAWebSyncdMutationsCryptoUtils.generateAssociatedData,
     *           WAWebSyncdMutationsCryptoUtils.generateMac,
     *           WAWebSyncdMutationsCryptoUtils.generatePadding,
     *           WAWebSyncdCrypto.generateIndexMac,
     *           WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText,
     *           WAWebSyncdRequestEncode.encodeSyncActionData.
     *           WA Web derives the mutation keys inline from the sync key data via
     *           {@code WAWebSyncdCrypto.generateEncryptionKeys(r)}; Cobalt receives the already
     *           derived {@link MutationKeys} as a constructor-injected parameter, so the key
     *           derivation step lives in the caller.
     *           WA Web's {@code generatePadding} receives the pre-serialized
     *           {@code binarySyncAction.byteLength}; Cobalt stores the decoded
     *           {@code SyncActionValue} on the mutation, so the value length is unknown until
     *           encoding. Since {@code MAX_OF_MIN_DATA_LENGTH = 0} forces the result to an
     *           empty array regardless of inputs, Cobalt passes {@code 0} as the value length.
     *           WA Web's error path logs, reports a {@code SyncdFatalError} metric, and throws
     *           {@code new SyncdFatalError("encryption failure")} (both in
     *           {@code syncdEncryptMutation} and the outer wrapper); per Cobalt's error model,
     *           the underlying {@link GeneralSecurityException} is propagated directly and
     *           recovery is left to {@code WhatsAppClientErrorHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdEncryptMutations", exports = "syncdEncryptMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdEncryptMutationsWrapper", exports = "encryptMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestEncode", exports = "encodeSyncActionData", adaptation = WhatsAppAdaptation.ADAPTED)
    public static EncryptedMutation of(
            SyncPendingMutation patch,
            MutationKeys keys,
            byte[] keyId
    ) throws GeneralSecurityException {
        // Create SyncActionData with padding — WAWebSyncdEncryptMutations.d (encodeSyncActionData)
        var mutation = patch.mutation();
        var indexBytes = mutation.index().getBytes(StandardCharsets.UTF_8); // WAArrayBufferUtils.stringToArrayBuffer(l)
        // Padding is always empty while MAX_OF_MIN_DATA_LENGTH = 0 in WA Web, so the value-length
        // argument is irrelevant (any non-negative value produces the same empty array). We pass
        // indexBytes.length and 0 here and let MutationKeys.generatePadding centralize the formula.
        var padding = MutationKeys.generatePadding(indexBytes.length, 0); // WAWebSyncdMutationsCryptoUtils.generatePadding
        var actionData = new SyncActionDataBuilder() // WAWebSyncdEncryptMutations.d: encodeSyncActionData({index, value, padding, version})
                .index(indexBytes) // WAWebSyncdEncryptMutations.d: index: e (stringToArrayBuffer result)
                .value(mutation.value()) // WAWebSyncdEncryptMutations.d: value: decodeProtobuf(SyncActionValueSpec, binarySyncAction)
                .padding(padding) // WAWebSyncdMutationsCryptoUtils.generatePadding — currently always empty (MAX_OF_MIN_DATA_LENGTH = 0)
                .version(mutation.actionVersion()) // WAWebSyncdEncryptMutations: t.version (top-level field on mutation)
                .build();

        // Encode to protobuf — WAWebSyncdRequestEncode.encodeSyncActionData
        // WA Web throws SyncdFatalError("action data protobuf serialization failed") on failure
        // and reports a WAM fatal-error metric (ACTION_DATA_PROTOBUF_SERIALIZATION_FAILED);
        // the metric is skipped per Cobalt's telemetry policy, but the throw is preserved.
        byte[] plaintext;
        try {
            plaintext = SyncActionDataSpec.encode(actionData);
        } catch (Exception exception) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "action data protobuf serialization failed", exception
            );
        }

        // Encrypt with AES-256-CBC — WAWebSyncdMutationsCryptoUtils.generateCipherText → WACryptoAesCbc.aesCbcEncrypt
        // Returns [IV (16 bytes) || ciphertext]
        var ivAndCipherText = keys.generateCipherText(plaintext);

        // Build associated data: [opByte] || keyIdBytes — WAWebSyncdMutationsCryptoUtils.generateAssociatedData
        var associatedData = MutationKeys.generateAssociatedData(mutation.operation(), keyId);

        // Compute value MAC using HMAC-SHA-512 truncated to 32 bytes — WAWebSyncdMutationsCryptoUtils.generateMac
        var valueMac = keys.generateMac(associatedData, ivAndCipherText);

        // Concatenate IV || ciphertext || value MAC — WAWebSyncdCryptoUtils.combine([b, S])
        var encryptedValue = new byte[ivAndCipherText.length + valueMac.length];
        System.arraycopy(ivAndCipherText, 0, encryptedValue, 0, ivAndCipherText.length);
        System.arraycopy(valueMac, 0, encryptedValue, ivAndCipherText.length, valueMac.length);

        // Compute index MAC — WAWebSyncdCrypto.generateIndexMac
        var indexMacResult = keys.generateIndexMac(indexBytes);

        // Create EncryptedMutation — WAWebSyncdEncryptMutations: return {indexMac, indexAndValueCipherText}
        return new EncryptedMutation(indexMacResult, encryptedValue, keyId, mutation.operation());
    }

    /**
     * Extracts the value MAC from the encrypted value (last 32 bytes).
     *
     * <p>Delegates to {@link MutationKeys#valueMacFromIndexAndValueCipherText(byte[])}
     * to avoid duplicating the slicing logic. In WA Web,
     * {@code WAWebSyncdEncryptMutationsWrapper.encryptMutation} performs the same
     * extraction inline via {@code valueMacFromIndexAndValueCipherText(indexAndValueCipherText)}
     * before returning the wrapped result; Cobalt instead exposes the MAC as a computed
     * accessor so callers that only need the ciphertext+MAC buffer do not pay for the slice.
     *
     * @return the 32-byte value MAC
     * @implNote WAWebSyncdCrypto.valueMacFromIndexAndValueCipherText,
     *           WAWebSyncdEncryptMutationsWrapper.encryptMutation (inline {@code valueMac}
     *           extraction on the returned object)
     */
    public byte[] valueMac() {
        return MutationKeys.valueMacFromIndexAndValueCipherText(encryptedValue);
    }
}
