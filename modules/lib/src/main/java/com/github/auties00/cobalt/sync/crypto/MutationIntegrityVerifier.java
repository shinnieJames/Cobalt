package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.signal.KeyId;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatch;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshot;
import com.github.auties00.cobalt.store.WhatsAppStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.SequencedCollection;

/**
 * Validates the snapshot MAC and patch MAC on every app-state sync envelope
 * the relay sends, and computes the same MACs for every outgoing patch.
 *
 * <p>The MAC formulas live in {@code WAWebSyncdEncryptionManager}; the
 * wire-level verification routines that call into them live in
 * {@code WAWebSyncdAntiTampering}. This class collapses the two:
 * {@link #verifySnapshotMac} and {@link #verifyPatchIntegrity} are the
 * verification entry points, the static {@link #computeSnapshotMac} and
 * {@link #computePatchMac} are the underlying formulas, and
 * {@link #computeOutgoingSnapshotAndPatchMacs} pairs them for the outgoing path.
 *
 * @apiNote
 * Driven by the collection-handler pipeline that processes a single
 * {@code SyncdPatch} or {@code SyncdSnapshot} envelope at a time. Cobalt
 * does not batch verification across patches; every patch carries its own
 * value MAC list and its own wire snapshot MAC, both of which feed back into
 * the patch MAC chain.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdAntiTampering")
@WhatsAppWebModule(moduleName = "WAWebSyncdEncryptionManager")
public final class MutationIntegrityVerifier {
    /**
     * The store backing key-id resolution and collection-state lookups.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a verifier bound to a store.
     *
     * @param store the store from which sync keys, collection versions, and
     *              the mac-mismatch latch are read
     */
    public MutationIntegrityVerifier(WhatsAppStore store) {
        this.store = store;
    }

    /**
     * Verifies the snapshot MAC on a freshly received {@link SyncdSnapshot}
     * and returns the computed MAC for downstream use.
     *
     * @apiNote
     * Called once per incoming snapshot, after the caller has decrypted every
     * mutation in the snapshot and accumulated the new LT-Hash. The
     * {@code expectedHash} input is the locally computed LT-Hash; matching
     * it under the snapshot-MAC key against the wire MAC closes the loop
     * between the decrypted records and the relay's authenticated digest.
     *
     * @implNote
     * This implementation diverges from
     * {@code WAWebSyncdAntiTampering.computeLtHashAndValidateSnapshot} in
     * two ways. WA Web computes the LT-Hash inline (as part of the same
     * routine), whereas Cobalt separates LT-Hash recomputation into the
     * caller and only validates the MAC here. WA Web on a snapshot MAC
     * mismatch falls through to a recovery flow with WAM telemetry; Cobalt
     * unconditionally raises
     * {@link WhatsAppWebAppStateSyncException.SnapshotMacMismatch} because
     * recovery is deferred to the configurable
     * {@code WhatsAppClientErrorHandler}.
     *
     * @param collectionName the collection type (drives the MAC-input suffix)
     * @param version        the snapshot version (drives the 8-byte big-endian suffix)
     * @param snapshot       the wire snapshot, source of both the wire MAC and the key id
     * @param expectedHash   the locally computed LT-Hash over the decrypted records
     * @return the computed snapshot MAC, or {@code null} when MAC checks are disabled
     * @throws WhatsAppWebAppStateSyncException.SnapshotMacMismatch if the wire MAC is absent or does not match
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError     if the snapshot lacks a key id or the resolved key has no data
     * @throws WhatsAppWebAppStateSyncException.MissingKey          if the snapshot's key id is not in the local store
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAntiTampering", exports = "computeLtHashAndValidateSnapshot", adaptation = WhatsAppAdaptation.ADAPTED)
    public byte[] verifySnapshotMac(SyncPatchType collectionName, long version, SyncdSnapshot snapshot, byte[] expectedHash) {
        if (!store.checkPatchMacs()) {
            return null;
        }

        var mac = snapshot.mac();
        if(mac.isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.SnapshotMacMismatch(collectionName, version);
        }

        var keyId = snapshot.keyId()
                .flatMap(KeyId::id);
        if(keyId.isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Snapshot missing key id for " + collectionName + " at version " + version,
                    null
            );
        }

        var keyData = store.findWebAppStateKeyById(keyId.get())
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.MissingKey(keyId.get()))
                .keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Snapshot sync key had no key data for " + collectionName + " at version " + version,
                        null
                ));

        try (var keys = MutationKeys.ofSyncKey(keyData)) {
            var expectedMac = computeSnapshotMac(keys.snapshotMacKey(), expectedHash, version, collectionName);
            if (!MessageDigest.isEqual(mac.get(), expectedMac)) {
                throw new WhatsAppWebAppStateSyncException.SnapshotMacMismatch(collectionName, version);
            }
            return expectedMac;
        }
    }

    /**
     * Verifies the patch MAC and the snapshot MAC on a freshly received
     * {@link SyncdPatch}.
     *
     * @apiNote
     * Called once per incoming patch envelope. The patch MAC chains over the
     * wire snapshot MAC plus the per-mutation value MACs and is the primary
     * integrity gate; the snapshot MAC re-checks the locally accumulated
     * LT-Hash against the relay's expected digest. WA Web treats the two
     * mismatches differently and Cobalt mirrors that asymmetry: patch MAC
     * mismatch is fatal, snapshot MAC mismatch reports {@code false} so the
     * caller can flip the collection into the mac-mismatch state without
     * tearing down the session.
     *
     * @implNote
     * This implementation skips the snapshot MAC validation when the
     * collection is already in the mac-mismatch state, matching the
     * {@code if (E && k) return} short-circuit in the WA Web
     * {@code validateSnapshotMac} helper. The {@code checkPatchMacs} short
     * circuit returning {@code true} unconditionally has no WA Web analogue;
     * it is a Cobalt-only embedder toggle for fixture replay.
     *
     * @param collectionName the collection type
     * @param patch          the wire patch
     * @param computedLtHash the locally accumulated LT-Hash after applying this patch
     * @param patchValueMacs the value MACs from this patch's mutations, in wire order
     * @return {@code true} when the snapshot MAC matched (or was skipped),
     *         {@code false} when the snapshot MAC mismatched and the collection should be flagged
     * @throws WhatsAppWebAppStateSyncException.PatchMacMismatch if the wire patch MAC does not match
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError  if the patch lacks a key id or the key has no data
     * @throws WhatsAppWebAppStateSyncException.MissingKey       if the patch's key id is not in the local store
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAntiTampering", exports = "computeLtHashAndValidatePatch", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean verifyPatchIntegrity(SyncPatchType collectionName, SyncdPatch patch, byte[] computedLtHash, SequencedCollection<byte[]> patchValueMacs) {
        if (!store.checkPatchMacs()) {
            return true;
        }

        var keyId = patch.keyId()
                .flatMap(KeyId::id);
        if (keyId.isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Patch missing key id for " + collectionName,
                    null
            );
        }

        var keyData = store.findWebAppStateKeyById(keyId.get())
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.MissingKey(keyId.get()))
                .keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Patch sync key had no key data for " + collectionName,
                        null
                ));

        long patchVersion = patch.version()
                .map(version -> version.version().orElse(0L))
                .orElse(0L);

        try (var keys = MutationKeys.ofSyncKey(keyData)) {
            var wireSnapshotMac = patch.snapshotMac().orElse(null);

            var wirePatchMac = patch.patchMac().orElse(null);
            if (wirePatchMac != null) {
                var expectedPatchMac = computePatchMac(keys.patchMacKey(), wireSnapshotMac, patchValueMacs, patchVersion, collectionName);
                if (!MessageDigest.isEqual(wirePatchMac, expectedPatchMac)) {
                    throw new WhatsAppWebAppStateSyncException.PatchMacMismatch(collectionName, patchVersion);
                }
            }

            var alreadyInMacMismatch = store.findWebAppState(collectionName).macMismatch();
            if (alreadyInMacMismatch) {
                return true;
            }

            if (wireSnapshotMac != null) {
                var expectedSnapshotMac = computeSnapshotMac(keys.snapshotMacKey(), computedLtHash, patchVersion, collectionName);
                if (!MessageDigest.isEqual(wireSnapshotMac, expectedSnapshotMac)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The paired output of {@link #computeOutgoingSnapshotAndPatchMacs}.
     *
     * @param snapshotMac the snapshot MAC over the new LT-Hash and version
     * @param patchMac    the patch MAC chained over the snapshot MAC and the value MACs
     */
    public record OutgoingMacs(
            byte[] snapshotMac,
            byte[] patchMac
    ) {
    }

    /**
     * Computes the snapshot MAC and patch MAC for an outgoing patch envelope.
     *
     * @apiNote
     * Called by the outgoing patch builder after the LT-Hash has been
     * recomputed for the new version. The patch MAC chains over the snapshot
     * MAC, so callers must use the {@link OutgoingMacs#snapshotMac()} output
     * (not any pre-existing wire MAC) for the relay to accept the patch.
     *
     * @implNote
     * This implementation accepts the already-derived MAC keys directly; the
     * WA Web routine re-derives them via {@code generateEncryptionKeys}
     * inside the same closure. Holding the keys in the caller avoids a
     * second HKDF expansion per outgoing patch.
     *
     * @param snapshotMacKey the snapshot MAC key
     * @param patchMacKey    the patch MAC key
     * @param newLtHash      the LT-Hash for the new version
     * @param valueMacs      the value MACs from the outgoing mutations, in wire order
     * @param newVersion     the new collection version
     * @param collectionName the collection type
     * @return the paired snapshot and patch MACs
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAntiTampering", exports = "computeOutgoingSnapshotAndPatchMacs", adaptation = WhatsAppAdaptation.ADAPTED)
    public static OutgoingMacs computeOutgoingSnapshotAndPatchMacs(
            SecretKeySpec snapshotMacKey,
            SecretKeySpec patchMacKey,
            byte[] newLtHash,
            SequencedCollection<byte[]> valueMacs,
            long newVersion,
            SyncPatchType collectionName
    ) {
        var snapshotMac = computeSnapshotMac(snapshotMacKey, newLtHash, newVersion, collectionName);
        var patchMac = computePatchMac(patchMacKey, snapshotMac, valueMacs, newVersion, collectionName);
        return new OutgoingMacs(snapshotMac, patchMac);
    }

    /**
     * Computes the snapshot MAC under the snapshot-MAC key.
     *
     * @apiNote
     * Formula: {@code HMAC-SHA256(snapshotMacKey, ltHash || version8 || collectionUtf8)}
     * where {@code version8} is an 8-byte big-endian encoding of the version
     * (the upper 4 bytes are zero in practice because versions are unsigned
     * 32-bit counters).
     *
     * @implNote
     * This implementation emits the 8 version bytes inline rather than
     * allocating a temporary buffer. The shift sequence reproduces
     * {@code WAWebSyncdCryptoUtils.to64BitNetworkOrder} byte for byte.
     *
     * @param snapshotMacKey the snapshot MAC key
     * @param ltHash         the LT-Hash to authenticate
     * @param version        the collection version
     * @param type           the collection type
     * @return the 32-byte MAC
     * @throws WhatsAppWebAppStateSyncException.MacComputationFailed if the JCE HMAC primitive fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdEncryptionManager", exports = "generateSnapshotMac", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] computeSnapshotMac(SecretKeySpec snapshotMacKey, byte[] ltHash, long version, SyncPatchType type) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(snapshotMacKey);

            mac.update(ltHash);

            mac.update((byte) (version >> 56));
            mac.update((byte) (version >> 48));
            mac.update((byte) (version >> 40));
            mac.update((byte) (version >> 32));
            mac.update((byte) (version >> 24));
            mac.update((byte) (version >> 16));
            mac.update((byte) (version >> 8));
            mac.update((byte) version);

            mac.update(type.toBytes());

            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppWebAppStateSyncException.MacComputationFailed(exception);
        }
    }

    /**
     * Computes the patch MAC under the patch-MAC key.
     *
     * @apiNote
     * Formula: {@code HMAC-SHA256(patchMacKey, snapshotMac || valueMac1 || ... || valueMacN || version8 || collectionUtf8)}.
     * The patch MAC therefore depends on the snapshot MAC, which is what
     * binds the per-mutation value MACs and the collection-level LT-Hash
     * digest together into a single per-version authenticator.
     *
     * @implNote
     * This implementation tolerates a {@code null} {@code snapshotMac} by
     * skipping its contribution to the HMAC. WA Web always supplies the
     * wire snapshot MAC; the null branch exists for the very first patch
     * on a fresh collection and for unit tests that exercise the formula
     * without a paired snapshot.
     *
     * @param patchMacKey the patch MAC key
     * @param snapshotMac the snapshot MAC, or {@code null} to omit
     * @param valueMacs   the per-mutation value MACs, in wire order
     * @param version     the collection version
     * @param type        the collection type
     * @return the 32-byte MAC
     * @throws WhatsAppWebAppStateSyncException.MacComputationFailed if the JCE HMAC primitive fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdEncryptionManager", exports = "generatePatchMac", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] computePatchMac(SecretKeySpec patchMacKey, byte[] snapshotMac, SequencedCollection<byte[]> valueMacs, long version, SyncPatchType type) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(patchMacKey);

            if (snapshotMac != null) {
                mac.update(snapshotMac);
            }

            for (var valueMac : valueMacs) {
                mac.update(valueMac);
            }

            mac.update((byte) (version >> 56));
            mac.update((byte) (version >> 48));
            mac.update((byte) (version >> 40));
            mac.update((byte) (version >> 32));
            mac.update((byte) (version >> 24));
            mac.update((byte) (version >> 16));
            mac.update((byte) (version >> 8));
            mac.update((byte) version);

            mac.update(type.toBytes());

            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppWebAppStateSyncException.MacComputationFailed(exception);
        }
    }

    /**
     * Formats an {@code (indexMac, valueMac)} pair as a colon-delimited hex string.
     *
     * @apiNote
     * Diagnostic helper consumed by the collection-handler log statements
     * that print per-mutation MAC pairs when a patch or snapshot fails to
     * validate. Not part of the wire-protocol surface. The truncated form
     * (last 16 hex characters of each side) is WA Web's default and the form
     * that flows into production log lines.
     * {@snippet :
     *     // verbose          = "0102030405060708...:090a0b0c0d0e0f10..."
     *     // truncated (true) = "0a1b2c3d4e5f6071:1234567890abcdef"
     * }
     *
     * @param indexMac the index MAC bytes
     * @param valueMac the value MAC bytes
     * @param truncate {@code true} to keep only the last 16 hex characters per side
     * @return the colon-delimited string
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAntiTampering", exports = "indexAndValueMacToString", adaptation = WhatsAppAdaptation.ADAPTED)
    public static String indexAndValueMacToString(byte[] indexMac, byte[] valueMac, boolean truncate) {
        var indexHex = HexFormat.of().formatHex(indexMac);
        var valueHex = HexFormat.of().formatHex(valueMac);
        var indexSlice = truncate && indexHex.length() > 16
                ? indexHex.substring(indexHex.length() - 16)
                : indexHex;
        var valueSlice = truncate && valueHex.length() > 16
                ? valueHex.substring(valueHex.length() - 16)
                : valueHex;
        return indexSlice + ":" + valueSlice;
    }

    /**
     * Formats an {@code (indexMac, valueMac)} pair with the WA Web default truncation.
     *
     * @apiNote
     * Convenience overload that pins the {@code truncate} parameter to
     * {@code true}, matching the WA Web call sites that rely on the
     * defaulted argument.
     *
     * @param indexMac the index MAC bytes
     * @param valueMac the value MAC bytes
     * @return the colon-delimited string, truncated to the last 16 hex characters per side
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdAntiTampering", exports = "indexAndValueMacToString", adaptation = WhatsAppAdaptation.DIRECT)
    public static String indexAndValueMacToString(byte[] indexMac, byte[] valueMac) {
        return indexAndValueMacToString(indexMac, valueMac, true);
    }

    /**
     * The direction of a syncd patch relative to this device.
     *
     * @apiNote
     * Surfaced for parity with WA Web's {@code WAWebSyncdAntiTampering.flow}
     * enum, which threads through the LT-Hash computation helper purely for
     * the verbose-logging branch that the {@code enable_syncd_debug_data_in_patch}
     * AB prop gates. The enum has no effect on any wire value (LT-Hash,
     * snapshot MAC, or patch MAC); it is kept so callers that want to log
     * the direction have a typed constant.
     *
     * @implNote
     * This implementation does not yet route the direction through to any
     * diagnostic path; {@link #verifyPatchIntegrity} is implicitly
     * {@link #INCOMING} and the outgoing MAC helper does not consult this
     * enum. Future contributors should plumb it through if and when the
     * Cobalt-side debug-data-in-patch logging is implemented.
     */
    @WhatsAppWebModule(moduleName = "WAWebSyncdAntiTampering.flow")
    public enum SyncdPatchDirection {
        /**
         * A patch the relay sent to this device.
         */
        INCOMING,

        /**
         * A patch this device built for upload to the relay.
         */
        OUTGOING
    }
}
