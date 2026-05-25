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
 * A single mutation that has come off the wire and been authenticated and decrypted.
 *
 * <p>The two variants capture the two stages of the incoming-patch pipeline:
 * <ul>
 *   <li>{@link Untrusted} is the freshly decrypted form produced by
 *       {@link Untrusted#of}; it still carries the raw {@code indexMac},
 *       {@code valueMac}, and {@code keyId} that the LT-Hash bookkeeping and
 *       the snapshot-MAC chaining consume downstream.</li>
 *   <li>{@link Trusted} is the post-validation form attached to the local
 *       store: the MAC metadata has been consumed and only the
 *       application-level fields remain.</li>
 * </ul>
 *
 * <p>The receive pipeline produces {@link Untrusted} from
 * {@link SyncActionValue}-bearing wire records, verifies the patch and snapshot
 * MACs against the resulting value MACs, and only then promotes the survivors
 * to {@link Trusted}.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdDecryptMutations")
@WhatsAppWebModule(moduleName = "WAWebSyncdDecryptMutationsWrapper")
public sealed interface DecryptedMutation {
    /**
     * Returns the UTF-8 decoded mutation index string.
     *
     * <p>The index is the JSON-array key under which the sync handlers locate
     * the mutation target, for example {@code ["archive","1234@s.whatsapp.net"]}.
     *
     * @return the index string
     */
    String index();

    /**
     * Returns whether this mutation sets or removes its index.
     *
     * @return the sync operation
     */
    SyncdOperation operation();

    /**
     * Returns the timestamp the originating device stamped on the action.
     *
     * <p>Handlers that order conflicting actions by recency (chat archive,
     * mute, pin) read this value; it is not a server-supplied wall clock.
     *
     * @return the action timestamp
     */
    Instant timestamp();

    /**
     * A decrypted mutation that still carries the MAC and key-id metadata
     * needed to chain LT-Hash and snapshot-MAC verification.
     *
     * <p>Produced exclusively by {@link Untrusted#of}. Downstream code consumes
     * {@link #indexMac()} and {@link #valueMac()} when feeding the
     * {@link MutationIntegrityVerifier} patch path, and {@link #keyId()} when
     * recording the {@link com.github.auties00.cobalt.model.sync.SyncActionEntry}
     * that LT-Hash recomputation reads back during consistency checks.
     *
     * @param index         the decoded index string
     * @param indexMac      the wire index MAC, also recomputed from the index
     * @param valueMac      the trailing 32 bytes of the wire encrypted value
     * @param value         the decoded {@link SyncActionValue} payload
     * @param operation     the sync operation
     * @param timestamp     the action timestamp
     * @param keyId         the sync key id used for decryption
     * @param actionVersion the action protobuf version from the payload
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
         * Decrypts and authenticates a single wire mutation.
         *
         * <p>The expected wire format is
         * {@snippet :
         *     // encryptedValue = IV (16 bytes)
         *     //                || AES-CBC(SyncActionData protobuf)
         *     //                || HMAC-SHA512(...)[0..32]   // trailing valueMac
         *     // indexMac        = HMAC-SHA256(indexKey, SyncActionData.index)
         * }
         * Verification proceeds value MAC first (so a tampered ciphertext fails
         * before the AES-CBC step), then the protobuf decode, then the index MAC;
         * the thrown exception subtype names the failed step.
         *
         * @implNote
         * This implementation extracts the {@link SyncActionValue} and
         * {@code timestamp} eagerly for both SET and REMOVE. WA Web treats
         * the value as nullable and validates only SET in
         * {@code WAWebSyncdValidateMutations.validateAndTypeSetMutations};
         * Cobalt's downstream handlers require a non-null value on every
         * mutation, so the eager check moves a later
         * {@link NullPointerException} into an explicit
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError} here.
         * The matching divergence on timestamps is identical and motivated
         * by the same downstream constraint on the {@link Trusted} record.
         *
         * @param encryptedValue the wire {@code IV || ciphertext || valueMac} blob
         * @param indexMac       the wire index MAC to verify against
         * @param keys           the derived sync keys for the relevant key id
         * @param operation      the operation the wire frame declares
         * @param keyId          the sync key id, mixed into the AAD
         * @return a freshly decoded {@link Untrusted} instance
         * @throws IllegalArgumentException                          if {@code encryptedValue} is shorter than {@code IV_LENGTH + MAC_LENGTH}
         * @throws GeneralSecurityException                          if the JCE primitives fail
         * @throws WhatsAppWebAppStateSyncException.ValueMacMismatch if the value MAC does not validate
         * @throws WhatsAppWebAppStateSyncException.IndexMacMismatch if the index MAC does not validate
         * @throws WhatsAppWebAppStateSyncException.DecryptionFailed if the AES-CBC output is not a valid {@link SyncActionData} protobuf
         * @throws WhatsAppWebAppStateSyncException.UnexpectedError  if the decoded protobuf is missing the index, version, value, or timestamp fields
         * @throws WhatsAppWebAppStateSyncException.MissingActionTimestamp if the action value carries no timestamp
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

            var valueMac = MutationKeys.valueMacFromIndexAndValueCipherText(encryptedValue);

            var associatedData = MutationKeys.generateAssociatedData(operation, keyId);

            var ivAndCipherText = Arrays.copyOfRange(
                    encryptedValue,
                    0,
                    encryptedValue.length - MutationKeys.MAC_LENGTH
            );

            var expectedMac = keys.generateMac(associatedData, ivAndCipherText);
            if (!MessageDigest.isEqual(valueMac, expectedMac)) {
                throw new WhatsAppWebAppStateSyncException.ValueMacMismatch();
            }

            var iv = Arrays.copyOfRange(encryptedValue, 0, MutationKeys.IV_LENGTH);
            var cipherText = Arrays.copyOfRange(
                    encryptedValue,
                    MutationKeys.IV_LENGTH,
                    encryptedValue.length - MutationKeys.MAC_LENGTH
            );
            var plaintext = keys.decryptCipherText(iv, cipherText);

            SyncActionData actionData;
            try {
                actionData = SyncActionDataSpec.decode(ProtobufInputStream.fromBytes(plaintext));
            } catch (Exception e) {
                throw new WhatsAppWebAppStateSyncException.DecryptionFailed(
                        "syncd: data protobuf deserialization failed: " + e.getMessage(), e);
            }

            var actionIndex = actionData.index()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("missing action index", null));
            var actionVersion = actionData.version()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "missing action version", null));
            var actionValue = actionData.value()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing value from action data", null));
            var actionTimestamp = actionValue.timestamp()
                    .orElseThrow(WhatsAppWebAppStateSyncException.MissingActionTimestamp::new);

            var expectedIndexMac = keys.generateIndexMac(actionIndex);
            if (!MessageDigest.isEqual(indexMac, expectedIndexMac)) {
                throw new WhatsAppWebAppStateSyncException.IndexMacMismatch();
            }
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

    /**
     * A validated mutation that the sync handlers can apply to the local
     * store without further crypto checks.
     *
     * <p>Produced either by promoting an {@link Untrusted} after MAC chaining
     * succeeds, or by the outgoing path that originates mutations locally
     * (no decryption involved). Carries no MAC metadata; the sync handlers
     * see only the application-level fields.
     *
     * @param index         the index string identifying the mutation target
     * @param value         the action payload
     * @param operation     the sync operation
     * @param timestamp     the action timestamp
     * @param actionVersion the action protobuf version
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
