package com.github.auties00.cobalt.sync.crypto;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.signal.KeyId;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatch;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.exchange.MutationSyncResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

public final class MutationIntegrityVerifier {
    private final WhatsAppStore store;

    public MutationIntegrityVerifier(WhatsAppStore store) {
        this.store = store;
    }

    public void verifyIntegrity(MutationSyncResponse response, byte[] expectedHash) {
        if (!store.checkPatchMacs()) {
            return;
        }

        if (response.isSnapshot()) {
            verifySnapshotMac(response, expectedHash);
        }

        for (var patch : response.patches()) {
            verifyPatchMac(response.collectionName(), patch, expectedHash);
        }
    }

    private void verifySnapshotMac(MutationSyncResponse response, byte[] expectedHash) {
        var snapshot = response.snapshot();
        if(snapshot.isEmpty()) {
            return;
        }

        var mac = snapshot.get().mac();
        if(mac.isEmpty()) {
            return;
        }

        var keyId = snapshot.get()
                .keyId()
                .flatMap(KeyId::id);
        if(keyId.isEmpty()) {
            throw new IllegalArgumentException("Snapshot missing key id");
        }

        var keyData = store.findWebAppStateKeyById(keyId.get())
                .orElseThrow(() -> new InternalError("Unknown sync key for patch"))
                .keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new IllegalArgumentException("Sync key had no key data"));

        try (var keys = MutationKeys.ofSyncKey(keyData)) {
            var expectedMac = computeMac(response.collectionName(), response.version(), expectedHash, keys.snapshotMacKey());
            if (!MessageDigest.isEqual(mac.get(), expectedMac)) {
                throw new WhatsAppWebAppStateSyncException.SnapshotMacMismatch(response.collectionName(), response.version());
            }
        }
    }

    private void verifyPatchMac(SyncPatchType type, SyncdPatch patch, byte[] expectedHash) {
        var patchMac = patch.patchMac();
        if (patchMac.isEmpty()) {
            return;
        }

        var keyId = patch.keyId()
                .flatMap(KeyId::id);
        if (keyId.isEmpty()) {
            throw new IllegalArgumentException("Patch missing key id");
        }

        // Get sync key
        var keyData = store.findWebAppStateKeyById(keyId.get())
                .orElseThrow(() -> new InternalError("Unknown sync key for patch"))
                .keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new IllegalArgumentException("Sync key had no key data"));

        // Derive keys to get patch MAC key
        try (var keys = MutationKeys.ofSyncKey(keyData)) {
            long patchVersion = patch.version()
                    .map(version -> version.version().orElse(0L))
                    .orElse(0L);
            var expectedMac = computeMac(type, patchVersion, expectedHash, keys.patchMacKey());
            if (!MessageDigest.isEqual(patchMac.get(), expectedMac)) {
                throw new WhatsAppWebAppStateSyncException.PatchMacMismatch(type, patchVersion);
            }
        }
    }

    private byte[] computeMac(SyncPatchType type, long version, byte[] expectedHash, SecretKeySpec secretKey) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);

            mac.update((byte) (version >> 56));
            mac.update((byte) (version >> 48));
            mac.update((byte) (version >> 40));
            mac.update((byte) (version >> 32));
            mac.update((byte) (version >> 24));
            mac.update((byte) (version >> 16));
            mac.update((byte) (version >> 8));
            mac.update((byte) version);

            mac.update(type.toBytes());

            mac.update(expectedHash);

            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppWebAppStateSyncException.MacComputationFailed(exception);
        }
    }
}
