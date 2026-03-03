package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
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
import java.util.SequencedCollection;

/**
 * Verifies the integrity of sync mutations using HMAC-SHA256 based MACs.
 *
 * <p>Provides per-patch verification of snapshot MACs and patch MACs,
 * matching the WhatsApp Web {@code WAWebSyncdEncryptionManager} behavior.
 * Each patch is verified individually with its own value MACs and the
 * wire-provided snapshot MAC, rather than batch verification across all patches.
 */
public final class MutationIntegrityVerifier {
    private final WhatsAppStore store;

    /**
     * Constructs a new integrity verifier.
     *
     * @param store the WhatsApp store for key lookups
     */
    public MutationIntegrityVerifier(WhatsAppStore store) {
        this.store = store;
    }

    /**
     * Verifies the snapshot MAC and returns the computed snapshot MAC for use
     * in subsequent patch MAC verification.
     *
     * <p>Snapshot MAC is computed as:
     * {@code HMAC-SHA256(snapshotMacKey, ltHash || to64BitNetworkOrder(version) || UTF8(collectionName))}
     *
     * @param collectionName the collection type
     * @param version the collection version
     * @param snapshot the decoded snapshot
     * @param expectedHash the computed LT-Hash
     * @return the computed snapshot MAC, or {@code null} if snapshot has no MAC
     */
    public byte[] verifySnapshotMac(SyncPatchType collectionName, long version, SyncdSnapshot snapshot, byte[] expectedHash) {
        if (!store.checkPatchMacs()) {
            return null;
        }

        var mac = snapshot.mac();
        if(mac.isEmpty()) {
            return null;
        }

        var keyId = snapshot.keyId()
                .flatMap(KeyId::id);
        if(keyId.isEmpty()) {
            throw new IllegalArgumentException("Snapshot missing key id");
        }

        var keyData = store.findWebAppStateKeyById(keyId.get())
                .orElseThrow(() -> new InternalError("Unknown sync key for snapshot"))
                .keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new IllegalArgumentException("Sync key had no key data"));

        try (var keys = MutationKeys.ofSyncKey(keyData)) {
            var expectedMac = computeSnapshotMac(keys.snapshotMacKey(), expectedHash, version, collectionName);
            if (!MessageDigest.isEqual(mac.get(), expectedMac)) {
                throw new WhatsAppWebAppStateSyncException.SnapshotMacMismatch(collectionName, version);
            }
            return expectedMac;
        }
    }

    /**
     * Verifies the integrity of a single patch using the computed LT-Hash
     * and the patch's own value MACs.
     *
     * <p>Per WhatsApp Web behavior, each patch is verified individually:
     * <ol>
     *   <li>Extract the wire {@code snapshotMac} from the patch protobuf</li>
     *   <li>If present, compute the expected snapshot MAC from the incremental
     *       LT-Hash and compare against the wire value</li>
     *   <li>Extract the wire {@code patchMac} from the patch protobuf</li>
     *   <li>If present, compute the expected patch MAC using the <b>wire</b>
     *       snapshot MAC bytes (not the locally computed one) as HMAC input,
     *       along with only this patch's value MACs</li>
     * </ol>
     *
     * @param collectionName the collection type
     * @param patch the patch to verify
     * @param computedLtHash the incrementally computed LT-Hash after applying this patch
     * @param patchValueMacs the value MACs from only this patch's mutations, in order
     */
    public void verifyPatchIntegrity(SyncPatchType collectionName, SyncdPatch patch, byte[] computedLtHash, SequencedCollection<byte[]> patchValueMacs) {
        if (!store.checkPatchMacs()) {
            return;
        }

        var keyId = patch.keyId()
                .flatMap(KeyId::id);
        if (keyId.isEmpty()) {
            throw new IllegalArgumentException("Patch missing key id");
        }

        var keyData = store.findWebAppStateKeyById(keyId.get())
                .orElseThrow(() -> new InternalError("Unknown sync key for patch"))
                .keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new IllegalArgumentException("Sync key had no key data"));

        long patchVersion = patch.version()
                .map(version -> version.version().orElse(0L))
                .orElse(0L);

        try (var keys = MutationKeys.ofSyncKey(keyData)) {
            // Step 1: Verify wire snapshotMac against locally computed LT-Hash
            var wireSnapshotMac = patch.snapshotMac().orElse(null);
            if (wireSnapshotMac != null) {
                var expectedSnapshotMac = computeSnapshotMac(keys.snapshotMacKey(), computedLtHash, patchVersion, collectionName);
                if (!MessageDigest.isEqual(wireSnapshotMac, expectedSnapshotMac)) {
                    throw new WhatsAppWebAppStateSyncException.SnapshotMacMismatch(collectionName, patchVersion);
                }
            }

            // Step 2: Verify wire patchMac using wire snapshotMac as input
            var wirePatchMac = patch.patchMac().orElse(null);
            if (wirePatchMac != null) {
                var expectedPatchMac = computePatchMac(keys.patchMacKey(), wireSnapshotMac, patchValueMacs, patchVersion, collectionName);
                if (!MessageDigest.isEqual(wirePatchMac, expectedPatchMac)) {
                    throw new WhatsAppWebAppStateSyncException.PatchMacMismatch(collectionName, patchVersion);
                }
            }
        }
    }

    /**
     * Computes the snapshot MAC.
     *
     * <p>Formula: {@code HMAC-SHA256(snapshotMacKey, ltHash || version8 || collectionUtf8)}
     *
     * @param snapshotMacKey the HMAC key for snapshot MAC
     * @param ltHash the computed LT-Hash
     * @param version the snapshot version
     * @param type the collection type
     * @return the computed snapshot MAC
     */
    private byte[] computeSnapshotMac(SecretKeySpec snapshotMacKey, byte[] ltHash, long version, SyncPatchType type) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(snapshotMacKey);

            // ltHash first
            mac.update(ltHash);

            // 64-bit big-endian version
            mac.update((byte) (version >> 56));
            mac.update((byte) (version >> 48));
            mac.update((byte) (version >> 40));
            mac.update((byte) (version >> 32));
            mac.update((byte) (version >> 24));
            mac.update((byte) (version >> 16));
            mac.update((byte) (version >> 8));
            mac.update((byte) version);

            // UTF-8 collection name
            mac.update(type.toBytes());

            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppWebAppStateSyncException.MacComputationFailed(exception);
        }
    }

    /**
     * Computes the patch MAC.
     *
     * <p>Formula: {@code HMAC-SHA256(patchMacKey, snapshotMac || valueMac1 || ... || valueMacN || version8 || collectionUtf8)}
     *
     * @param patchMacKey the HMAC key for patch MAC
     * @param snapshotMac the snapshot MAC (may be {@code null})
     * @param valueMacs the individual value MACs from mutations
     * @param version the patch version
     * @param type the collection type
     * @return the computed patch MAC
     */
    private byte[] computePatchMac(SecretKeySpec patchMacKey, byte[] snapshotMac, SequencedCollection<byte[]> valueMacs, long version, SyncPatchType type) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(patchMacKey);

            // snapshotMac (if present)
            if (snapshotMac != null) {
                mac.update(snapshotMac);
            }

            // Individual valueMacs
            for (var valueMac : valueMacs) {
                mac.update(valueMac);
            }

            // 64-bit big-endian version
            mac.update((byte) (version >> 56));
            mac.update((byte) (version >> 48));
            mac.update((byte) (version >> 40));
            mac.update((byte) (version >> 32));
            mac.update((byte) (version >> 24));
            mac.update((byte) (version >> 16));
            mac.update((byte) (version >> 8));
            mac.update((byte) version);

            // UTF-8 collection name
            mac.update(type.toBytes());

            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppWebAppStateSyncException.MacComputationFailed(exception);
        }
    }
}
