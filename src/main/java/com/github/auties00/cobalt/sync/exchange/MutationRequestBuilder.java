package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.signal.KeyIdBuilder;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.data.*;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.crypto.EncryptedMutation;
import com.github.auties00.cobalt.sync.crypto.MutationIntegrityVerifier;
import com.github.auties00.cobalt.sync.crypto.MutationKeys;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Builds outgoing sync request nodes with encrypted mutations.
 *
 * <p>Per WhatsApp Web behavior, outgoing patches are serialized as
 * {@code SyncdPatch} protobuf blobs containing encrypted mutations,
 * computed snapshotMac and patchMac, key ID, and device metadata.
 *
 * @implNote WAWebSyncdRequestBuilder.default, WAWebSyncdRequestBuilderBuild.buildSyncIqNode,
 *           WAWebSyncdRequestBuilderBuild._generateMutationsToUpload
 */
public final class MutationRequestBuilder {
    private static final Logger LOGGER = Logger.getLogger(MutationRequestBuilder.class.getName());

    private final WhatsAppClient whatsapp;
    private final ABPropsService abPropsService;

    /**
     * Constructs a new mutation request builder.
     *
     * @param whatsapp       the WhatsApp client instance
     * @param abPropsService the AB props service for threshold configuration
     */
    public MutationRequestBuilder(WhatsAppClient whatsapp, ABPropsService abPropsService) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
    }

    /**
     * Builds a sync request node for the specified collection.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdServerSync}: when the request
     * includes outgoing mutations, the returned {@link SyncRequest} carries
     * upload metadata so the caller can run the {@code _uploadSuccessful}
     * path after a successful server response.
     *
     * @implNote WAWebSyncdRequestBuilderBuild.buildSyncIqNode,
     *           WAWebSyncdRequestBuilderBuild._buildCollectionNodes
     * @param patchType the collection type to sync
     * @param patches   the pending mutations to include
     * @return the sync request containing the IQ node and optional upload info
     */
    public SyncRequest buildSyncRequest(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
        // ADAPTED: WAWebSyncdRequestBuilderBuild._buildCollectionNodes — getCollectionVersionInTransaction
        var hashState = whatsapp.store()
                .findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));

        // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — wap("collection", ...)
        var collectionBuilder = new NodeBuilder()
                .description("collection")
                .attribute("name", patchType.toString()) // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — CUSTOM_STRING(t)
                .attribute("version", hashState.version()) // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — INT(a ?? DEFAULT_COLLECTION_VERSION)
                .attribute("return_snapshot", !whatsapp.store().findWebAppState(patchType).bootstrapped() ? "true" : "false"); // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — a === void 0

        var compacted = compactPatch(patches); // WAWebSyncdRequestBuilderUtils.compactPatch

        SyncRequest.UploadedPatchInfo uploadInfo = null;
        if (!compacted.isEmpty()) {
            var buildResult = buildPatchProtobuf(patchType, compacted, hashState); // WAWebSyncdRequestBuilderBuild.C
            var patchNode = new NodeBuilder()
                    .description("patch") // WAWebSyncdRequestBuilderBuild.C — wap("patch", null, C)
                    .content(buildResult.bytes())
                    .build();
            collectionBuilder.content(patchNode);
            uploadInfo = buildResult.uploadInfo();
        }

        var collectionNode = collectionBuilder.build();

        // WAWebSyncdRequestBuilderBuild.buildSyncIqNode — wap("sync", null, r)
        var syncNode = new NodeBuilder()
                .description("sync")
                .content(collectionNode)
                .build();

        // WAWebSyncdRequestBuilderBuild.g — wap("iq", {...}, e)
        var node = new NodeBuilder()
                .description("iq")
                .attribute("type", "set") // WAWebSyncdRequestBuilderBuild.g
                .attribute("xmlns", "w:sync:app:state") // WAWebSyncdRequestBuilderBuild.g
                .attribute("to", Jid.userServer()) // ADAPTED: WAWebSyncdRequestBuilderBuild.g — S_WHATSAPP_NET
                .content(syncNode);
        // ADAPTED: id attribute added automatically by WhatsAppClient.sendNode — WAWebSyncdRequestBuilderBuild.g — generateId()

        return new SyncRequest(node, uploadInfo);
    }

    private record PatchBuildResult(byte[] bytes, SyncRequest.UploadedPatchInfo uploadInfo) {
    }

    public record BatchedSyncRequest(
            NodeBuilder node,
            Map<SyncPatchType, SyncRequest.UploadedPatchInfo> uploadInfos,
            Set<SyncPatchType> skippedUploads
    ) {
    }

    /**
     * Builds a serialized {@code SyncdPatch} protobuf containing encrypted
     * mutations with computed snapshotMac and patchMac.
     *
     * <p>Also captures upload metadata for post-response processing via
     * {@link SyncRequest.UploadedPatchInfo}.
     *
     * @implNote WAWebSyncdRequestBuilderBuild._buildCollectionNodes (inner function C/b),
     *           WAWebSyncdAntiTampering.computeOutgoingSnapshotAndPatchMacs,
     *           WAWebSyncdMMSUpload.exceedInlineMutationCount,
     *           WAWebSyncdMMSUpload.exceedPatchProtobufSize,
     *           WAWebSyncdMMSUpload.uploadPatch,
     *           WAWebSyncdRequestBuilderBuild.L (inline patch builder)
     * @param patchType the collection type
     * @param patches   the compacted pending mutations
     * @param hashState the current hash state for this collection
     * @return the serialized patch bytes and upload metadata
     */
    private PatchBuildResult buildPatchProtobuf(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches, SyncHashValue hashState) {
        // ADAPTED: WAWebSyncdKeyManagement.getActiveKey — get latest key
        var keys = whatsapp.store().appStateKeys();
        if (keys.isEmpty()) {
            throw new IllegalStateException("No app state sync keys available");
        }

        var latestKey = SyncKeyUtils.findNewestKey(keys); // ADAPTED: WAWebSyncdKeyManagement.getNewestKeyPair
        if (latestKey == null) {
            throw new IllegalStateException("No usable app state sync key found");
        }
        var latestKeyId = latestKey.keyId()
                .flatMap(AppStateSyncKeyId::keyId)
                .orElseThrow(() -> new IllegalArgumentException("No app state sync key found"));
        var latestKeyData = latestKey.keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new IllegalStateException("Latest app state sync key data has no ID"));

        try (var derivedKeys = MutationKeys.ofSyncKey(latestKeyData)) {
            // WAWebSyncdRequestBuilderBuild.C — encrypt all mutations
            var encryptedMutations = encryptMutations(patchType, patches, derivedKeys, latestKeyId);
            var userMutationCount = encryptedMutations.size();

            // WAWebSyncdRequestBuilderBuild._generateMutationsToUpload — key rotation mutations
            var keyRotationSources = new ArrayList<DecryptedMutation.Trusted>();
            var keyRotationMutations = buildKeyRotationMutations(patchType, patches, derivedKeys, latestKeyId, keyRotationSources);
            encryptedMutations.addAll(keyRotationMutations);

            // WAWebSyncdAntiTampering.computeLtHash — compute new LT-Hash
            var currentLtHash = hashState.hash() != null ? hashState.hash() : MutationLTHash.EMPTY_HASH;
            var toAdd = new ArrayList<byte[]>(encryptedMutations.size());
            var toRemove = new ArrayList<byte[]>();
            for (var encrypted : encryptedMutations) {
                if (encrypted.operation() == SyncdOperation.SET) {
                    // WAWebSyncdAntiTampering.computeLtHash — macsToOverwrite for existing, macsToAdd for new
                    whatsapp.store()
                            .findSyncActionEntry(patchType, encrypted.indexMac())
                            .ifPresent(existing -> toRemove.add(existing.valueMac()));
                    toAdd.add(encrypted.valueMac());
                } else {
                    // WAWebSyncdAntiTampering.computeLtHash — macsToRemove for REMOVE ops
                    whatsapp.store()
                            .findSyncActionEntry(patchType, encrypted.indexMac())
                            .ifPresent(existing -> toRemove.add(existing.valueMac()));
                }
            }
            var newLtHash = MutationLTHash.subtractThenAdd(currentLtHash, toAdd, toRemove).ltHash(); // WAWebSyncdAntiTampering.computeLtHash — subtractThenAdd

            // Build upload metadata by pairing encrypted mutations with source data
            var patchesIterator = patches.iterator();
            var keyRotationIterator = keyRotationSources.iterator();
            var uploadedMutations = new ArrayList<SyncRequest.UploadedMutationInfo>(encryptedMutations.size());
            var uploadedPendingMutationIds = new ArrayList<String>(userMutationCount);
            var index = 0;
            for (var encrypted : encryptedMutations) {
                DecryptedMutation.Trusted source;
                if (index < userMutationCount) {
                    var pendingMutation = patchesIterator.next();
                    uploadedPendingMutationIds.add(pendingMutation.mutationId());
                    source = pendingMutation.mutation();
                } else {
                    source = keyRotationIterator.next();
                }
                uploadedMutations.add(new SyncRequest.UploadedMutationInfo(
                        encrypted.indexMac(),
                        encrypted.valueMac(),
                        encrypted.keyId(),
                        encrypted.operation(),
                        source.index(),
                        source.value(),
                        source.actionVersion()
                ));
                index++;
            }

            // WAWebSyncdAntiTampering.computeOutgoingSnapshotAndPatchMacs — compute MACs
            var newVersion = hashState.version() + 1; // WAWebSyncdAntiTampering.computeOutgoingSnapshotAndPatchMacs — (version ?? DEFAULT) + 1
            var snapshotMac = MutationIntegrityVerifier.computeSnapshotMac( // WAWebSyncdAntiTampering.computeOutgoingSnapshotAndPatchMacs — generateSnapshotMac
                    derivedKeys.snapshotMacKey(), newLtHash, newVersion, patchType);
            var valueMacs = encryptedMutations.stream()
                    .map(EncryptedMutation::valueMac)
                    .toList();
            var patchMac = MutationIntegrityVerifier.computePatchMac( // WAWebSyncdAntiTampering.computeOutgoingSnapshotAndPatchMacs — generatePatchMac
                    derivedKeys.patchMacKey(), snapshotMac, valueMacs, newVersion, patchType);

            // WAWebSyncdRequestBuilderBuild.k — build SyncdMutation records
            var syncdMutations = new ArrayList<SyncdMutation>(encryptedMutations.size());
            for (var encrypted : encryptedMutations) {
                var record = new SyncdRecordBuilder()
                        .index(new SyncdIndexBuilder().blob(encrypted.indexMac()).build()) // WAWebSyncdRequestBuilderBuild.k — index: {blob: indexMac}
                        .value(new SyncdValueBuilder().blob(encrypted.encryptedValue()).build()) // WAWebSyncdRequestBuilderBuild.k — value: {blob: indexAndValueCipherText}
                        .keyId(new KeyIdBuilder().id(encrypted.keyId()).build()) // WAWebSyncdRequestBuilderBuild.k — keyId: {id: fromSyncKeyId(keyId)}
                        .build();
                syncdMutations.add(new SyncdMutationBuilder()
                        .operation(encrypted.operation()) // WAWebSyncdRequestBuilderBuild.k — operation
                        .record(record)
                        .build());
            }

            // WAWebSyncdRequestBuilderBuild.L — deviceIndex and clientDebugData
            var deviceIndex = whatsapp.store().jid()
                    .map(jid -> jid.device()) // WAWebSyncdRequestBuilderBuild.L — extractDeviceId(getMyDeviceJid())
                    .orElse(0);
            var debugData = new PatchDebugDataBuilder()
                    .isSenderPrimary(false) // WAWebSyncdRequestBuilderBuild.L — isSenderPrimary: false
                    .senderPlatform(PatchDebugData.Platform.WEB) // WAWebSyncdRequestBuilderBuild.L — PatchDebugData$Platform.WEB
                    .build();
            var clientDebugData = PatchDebugDataSpec.encode(debugData); // WAWebSyncdRequestBuilderBuild.L — encodeProtobuf(PatchDebugDataSpec, ...)

            // Capture upload metadata for post-response processing
            var uploadInfo = new SyncRequest.UploadedPatchInfo(
                    patchType,
                    newLtHash,
                    newVersion,
                    List.copyOf(uploadedPendingMutationIds),
                    List.copyOf(uploadedMutations)
            );

            // WAWebSyncdMMSUpload.exceedInlineMutationCount — check mutation count threshold
            var maxInlineCount = Math.min(2000, Math.max(100, abPropsService.getInt(ABProp.SYNCD_INLINE_MUTATIONS_MAX_COUNT))); // WAWebSyncdMMSUpload.exceedInlineMutationCount — Math.min(s=2000, Math.max(getSyncdInlineMutationsMaxCount(), e=100))
            var externalRef = (ExternalBlobReference) null;
            if (syncdMutations.size() > maxInlineCount) {
                externalRef = uploadExternalMutations(syncdMutations); // WAWebSyncdMMSUpload.uploadPatch
            } else {
                // WAWebSyncdRequestBuilderBuild.L — build inline patch
                // Per WA Web WAWebSyncdRequestBuilderBuild.L: inline patches include
                // clientDebugData but no version field
                var inlinePatch = new SyncdPatchBuilder()
                        .mutations(syncdMutations) // WAWebSyncdRequestBuilderBuild.L — mutations: e
                        .snapshotMac(snapshotMac) // WAWebSyncdRequestBuilderBuild.L — snapshotMac: n
                        .patchMac(patchMac) // WAWebSyncdRequestBuilderBuild.L — patchMac: r
                        .keyId(new KeyIdBuilder().id(latestKeyId).build()) // WAWebSyncdRequestBuilderBuild.L — keyId: {id: fromSyncKeyId(t)}
                        .deviceIndex(deviceIndex) // WAWebSyncdRequestBuilderBuild.L — deviceIndex: a
                        .clientDebugData(clientDebugData) // WAWebSyncdRequestBuilderBuild.L — clientDebugData: i
                        .build();
                var inlineBytes = SyncdPatchSpec.encode(inlinePatch); // WAWebSyncdRequestEncode.encodeSyncdPatch
                // WAWebSyncdMMSUpload.exceedPatchProtobufSize — check encoded size threshold
                var maxSizeBytes = Math.min(100, Math.max(10, abPropsService.getInt(ABProp.SYNCD_PATCH_PROTOBUF_MAX_SIZE))) * 1000L; // WAWebSyncdMMSUpload.exceedPatchProtobufSize — Math.min(c=100, Math.max(getSyncdPatchProtobufMaxSize(), u=10)) * 1e3
                if (inlineBytes.length > maxSizeBytes) {
                    externalRef = uploadExternalMutations(syncdMutations); // WAWebSyncdMMSUpload.uploadPatch
                } else {
                    return new PatchBuildResult(inlineBytes, uploadInfo);
                }
            }

            // WAWebSyncdMMSUpload.f — build external patch (no clientDebugData, no version)
            var syncdPatch = new SyncdPatchBuilder()
                    .externalMutations(externalRef) // WAWebSyncdMMSUpload.f — externalMutations: e
                    .snapshotMac(snapshotMac) // WAWebSyncdMMSUpload.f — snapshotMac: n
                    .patchMac(patchMac) // WAWebSyncdMMSUpload.f — patchMac: r
                    .keyId(new KeyIdBuilder().id(latestKeyId).build()) // WAWebSyncdMMSUpload.f — keyId: {id: fromSyncKeyId(t)}
                    .deviceIndex(deviceIndex) // WAWebSyncdMMSUpload.f — deviceIndex: a
                    .build();

            return new PatchBuildResult(SyncdPatchSpec.encode(syncdPatch), uploadInfo); // WAWebSyncdRequestEncode.encodeSyncdPatch
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to build outgoing patch", exception);
        }
    }

    /**
     * Encrypts pending mutations into {@code EncryptedMutation} objects.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdEncryptMutationsWrapper.encryptMutation}:
     * for SET operations the latest key is used, for REMOVE operations the
     * original key from the stored entry is looked up and used instead.
     *
     * @implNote WAWebSyncdEncryptMutationsWrapper.encryptMutation
     * @param patchType   the collection type
     * @param patches     the pending mutations to encrypt
     * @param derivedKeys the derived keys from the latest sync key
     * @param latestKeyId the latest key ID
     * @return the encrypted mutations
     * @throws GeneralSecurityException if encryption fails
     */
    private SequencedCollection<EncryptedMutation> encryptMutations(
            SyncPatchType patchType,
            SequencedCollection<SyncPendingMutation> patches,
            MutationKeys derivedKeys,
            byte[] latestKeyId
    ) throws GeneralSecurityException {
        var result = new ArrayList<EncryptedMutation>(patches.size());
        for (var patch : patches) {
            var mutation = patch.mutation();
            EncryptedMutation encrypted;

            // WAWebSyncdEncryptMutationsWrapper.encryptMutation — switch on operation
            if (mutation.operation() == SyncdOperation.REMOVE) {
                // WAWebSyncdEncryptMutationsWrapper.encryptMutation — REMOVE branch: look up original key
                // WAWebSyncdEncryptMutationsWrapper.encryptMutation — getSyncActionInTransaction(a)
                // Per WA Web: lookup by plaintext index string (key-independent), NOT by indexMac
                var originalEntry = whatsapp.store()
                        .findSyncActionEntryByActionIndex(patchType, mutation.index())
                        .orElseThrow(() -> new IllegalStateException( // WAWebSyncdEncryptMutationsWrapper.encryptMutation — SyncdFatalError("no corresponding set mutation")
                                "Cannot find original key for REMOVE operation on index: " + mutation.index()
                        ));

                // WAWebSyncdEncryptMutationsWrapper.encryptMutation — getKeyData(c)
                var originalKeyData = whatsapp.store()
                        .findWebAppStateKeyById(originalEntry.keyId())
                        .flatMap(AppStateSyncKey::keyData)
                        .flatMap(AppStateSyncKeyData::keyData)
                        .orElseThrow(() -> new IllegalStateException( // WAWebSyncdEncryptMutationsWrapper.encryptMutation — SyncdFatalError("no key data for corresponding mutation")
                                "Original sync key not found for REMOVE operation"
                        ));

                try (var originalDerivedKeys = MutationKeys.ofSyncKey(originalKeyData)) {
                    encrypted = EncryptedMutation.of(patch, originalDerivedKeys, originalEntry.keyId()); // WAWebSyncdEncryptMutationsWrapper.encryptMutation — l=c, s=d
                }
            } else {
                encrypted = EncryptedMutation.of(patch, derivedKeys, latestKeyId); // WAWebSyncdEncryptMutationsWrapper.encryptMutation — SET: use active key
            }

            result.add(encrypted);
        }
        return result;
    }

    /**
     * Builds additional SET and REMOVE mutations for key rotation.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRequestBuilderBuild}: when building
     * an outgoing patch, entries encrypted with keys older than the current key
     * are included as additional SET mutations to gradually rotate all entries
     * to the latest key. The number of additional SET mutations per patch is
     * limited by the {@code syncd_additional_mutations_count} AB prop.
     *
     * <p>Per WhatsApp Web {@code _generateMutationsToUpload}: after collecting
     * SET mutations (both user and rotation), REMOVE mutations are generated for
     * every stored entry whose index appears as a SET in the batch and whose key
     * is not the latest. This tells the server to explicitly remove the old-key
     * entry before the new-key entry is applied.
     *
     * @implNote WAWebSyncdRequestBuilderBuild._generateMutationsToUpload (function I/T),
     *           WAWebSyncdRequestBuilderBuild.D (rotation SET generation),
     *           WAWebSyncdRequestBuilderBuild.x (rotation REMOVE generation)
     * @param patchType   the collection type
     * @param patches     the core mutations being sent (to exclude from rotation)
     * @param derivedKeys the derived keys from the latest sync key
     * @param latestKeyId the latest app state sync key ID
     * @param keyRotationSourcesOut output list populated with source data for upload metadata
     * @return the additional encrypted mutations for key rotation (SETs + REMOVEs)
     * @throws GeneralSecurityException if encryption fails
     */
    private List<EncryptedMutation> buildKeyRotationMutations(
            SyncPatchType patchType,
            SequencedCollection<SyncPendingMutation> patches,
            MutationKeys derivedKeys,
            byte[] latestKeyId,
            List<DecryptedMutation.Trusted> keyRotationSourcesOut
    ) throws GeneralSecurityException {
        // WAWebSyncdRequestBuilderBuild.D — collect indices from current batch
        var batchIndices = new HashSet<String>();
        for (var patch : patches) {
            batchIndices.add(patch.mutation().index());
        }

        // ADAPTED: WAWebSyncdRequestBuilderBuild.D — getSyncActionsByCollectionsInTransaction([e])
        // then filter by !a.has(e.index) && !syncKeyIdsEqual(e.keyId, n)
        // then sortBy(e => getKeyEpoch(e.keyId))
        var allEntries = whatsapp.store().getSyncActionEntries(patchType)
                .stream()
                .sorted(Comparator.comparingInt(e -> SyncKeyUtils.getKeyEpoch(e.keyId()))) // WAWebSyncdRequestBuilderBuild.D — sortBy(getKeyEpoch)
                .toList();
        var maxAdditional = Math.min(5, Math.max(0, abPropsService.getInt(ABProp.SYNCD_ADDITIONAL_MUTATIONS_COUNT))); // WAWebSyncdRequestBuilderBuild.D — Math.min(p=5, getABPropConfigValue("syncd_additional_mutations_count"))
        var result = new ArrayList<EncryptedMutation>();

        for (var entry : allEntries) {
            if (result.size() >= maxAdditional) { // WAWebSyncdRequestBuilderBuild.D — i.slice(0, l)
                break;
            }

            if (Arrays.equals(entry.keyId(), latestKeyId)) { // WAWebSyncdRequestBuilderBuild.D — !syncKeyIdsEqual(e.keyId, n)
                continue;
            }

            if (entry.actionIndex() == null || entry.actionValue() == null) {
                continue;
            }

            if (batchIndices.contains(entry.actionIndex())) { // WAWebSyncdRequestBuilderBuild.D — !a.has(e.index)
                continue;
            }

            // ADAPTED: WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — re-encode as SET
            var trusted = new DecryptedMutation.Trusted(
                    entry.actionIndex(),
                    entry.actionValue(),
                    SyncdOperation.SET, // WAWebSyncdRequestBuilderBuild.D — SyncdMutation$SyncdOperation.SET
                    Instant.now(),
                    entry.actionVersion()
            );
            keyRotationSourcesOut.add(trusted);
            var pending = new SyncPendingMutation(trusted, 0);
            result.add(EncryptedMutation.of(pending, derivedKeys, latestKeyId));
        }

        // WAWebSyncdRequestBuilderBuild.x — generate REMOVE mutations for old-key entries
        // Per WA Web: only SET operations' indices are collected (not REMOVE), matching
        // t.filter(e => e.operation === SET).map(e => e.index)
        var allSetIndices = new HashSet<String>();
        for (var patch : patches) {
            if (patch.mutation().operation() == SyncdOperation.SET) { // WAWebSyncdRequestBuilderBuild.x — filter to SET operations only
                allSetIndices.add(patch.mutation().index());
            }
        }
        for (var rotationSource : keyRotationSourcesOut) {
            allSetIndices.add(rotationSource.index()); // WAWebSyncdRequestBuilderBuild.x — rotation SETs always have SET operation
        }

        for (var entry : allEntries) {
            if (entry.actionIndex() == null || !allSetIndices.contains(entry.actionIndex())) { // WAWebSyncdRequestBuilderBuild.x — r.has(e.index)
                continue;
            }

            if (Arrays.equals(entry.keyId(), latestKeyId)) { // WAWebSyncdRequestBuilderBuild.x — !syncKeyIdsEqual(e.keyId, n)
                continue;
            }

            if (entry.actionValue() == null) {
                continue;
            }

            // ADAPTED: WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — re-encode as REMOVE
            var removeTrusted = new DecryptedMutation.Trusted(
                    entry.actionIndex(),
                    entry.actionValue(),
                    SyncdOperation.REMOVE, // WAWebSyncdRequestBuilderBuild.x — SyncdMutation$SyncdOperation.REMOVE
                    Instant.now(),
                    entry.actionVersion()
            );
            keyRotationSourcesOut.add(removeTrusted);
            var removePending = new SyncPendingMutation(removeTrusted, 0);

            // WAWebSyncdEncryptMutationsWrapper.encryptMutation — REMOVE uses original key
            var originalKeyData = whatsapp.store()
                    .findWebAppStateKeyById(entry.keyId())
                    .flatMap(AppStateSyncKey::keyData)
                    .flatMap(AppStateSyncKeyData::keyData)
                    .orElseThrow(() -> new IllegalStateException(
                            "Original sync key not found for key rotation REMOVE"
                    ));
            try (var originalDerivedKeys = MutationKeys.ofSyncKey(originalKeyData)) {
                result.add(EncryptedMutation.of(removePending, originalDerivedKeys, entry.keyId()));
            }
        }

        return result;
    }

    /**
     * Builds a sync request node that batches multiple collections into a
     * single IQ stanza.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRequestBuilder}: all dirty collections
     * are batched into a single IQ with one {@code <sync>} node containing
     * multiple {@code <collection>} children.
     *
     * @implNote WAWebSyncdRequestBuilder.default, WAWebSyncdRequestBuilderBuild.buildSyncIqNode,
     *           WAWebSyncdRequestBuilderBuild._buildCollectionNodes
     * @param collectionPatches map of collection types to their pending mutations
     * @return the IQ request node builder
     */
    public BatchedSyncRequest buildBatchedSyncRequest(Map<SyncPatchType, SequencedCollection<SyncPendingMutation>> collectionPatches) {
        var collectionNodes = new ArrayList<com.github.auties00.cobalt.node.Node>();
        var uploadInfos = new LinkedHashMap<SyncPatchType, SyncRequest.UploadedPatchInfo>();
        var skippedUploads = new LinkedHashSet<SyncPatchType>();
        for (var entry : collectionPatches.entrySet()) {
            var patchType = entry.getKey();
            var patches = entry.getValue();

            var hashState = whatsapp.store()
                    .findWebAppHashStateByName(patchType)
                    .orElseGet(() -> new SyncHashValue(patchType));

            var collectionBuilder = new NodeBuilder()
                    .description("collection")
                    .attribute("name", patchType.toString())
                    .attribute("version", hashState.version())
                    .attribute("return_snapshot", !whatsapp.store().findWebAppState(patchType).bootstrapped() ? "true" : "false");

            var compacted = compactPatch(patches);
            if (!compacted.isEmpty()) {
                var buildResult = buildPatchProtobuf(patchType, compacted, hashState);
                var patchNode = new NodeBuilder()
                        .description("patch")
                        .content(buildResult.bytes())
                        .build();
                collectionBuilder.content(patchNode);
                if (buildResult.uploadInfo() != null) {
                    uploadInfos.put(patchType, buildResult.uploadInfo());
                }
            } else if (!patches.isEmpty()) {
                skippedUploads.add(patchType);
            }

            collectionNodes.add(collectionBuilder.build());
        }

        var syncNode = new NodeBuilder()
                .description("sync")
                .content(collectionNodes)
                .build();

        return new BatchedSyncRequest(
                new NodeBuilder()
                        .description("iq")
                        .attribute("type", "set")
                        .attribute("xmlns", "w:sync:app:state")
                        .attribute("to", Jid.userServer())
                        .content(syncNode),
                Collections.unmodifiableMap(uploadInfos),
                Collections.unmodifiableSet(skippedUploads)
        );
    }

    /**
     * Uploads a list of mutations as an external blob to the media CDN.
     *
     * <p>Per WhatsApp Web behavior, when the number of mutations or the encoded
     * protobuf size exceeds the configured inline thresholds, mutations are
     * serialized as a {@code SyncdMutations} protobuf, uploaded to the CDN via
     * the media connection, and referenced by an {@link ExternalBlobReference}
     * in the outgoing {@code SyncdPatch}.
     *
     * @implNote WAWebSyncdMMSUpload.uploadPatch, WAWebSyncdMMSUpload.buildExternalBlobReference,
     *           WAWebSyncdRequestEncode.encodeSyncdMutations
     * @param mutations the list of mutations to upload externally
     * @return the populated {@code ExternalBlobReference} with CDN metadata
     */
    private ExternalBlobReference uploadExternalMutations(List<SyncdMutation> mutations) {
        var syncdMutations = new SyncdMutationsBuilder()
                .mutations(mutations)
                .build();
        var encoded = SyncdMutationsSpec.encode(syncdMutations); // WAWebSyncdRequestEncode.encodeSyncdMutations — E(h)
        // ADAPTED: WAWebSyncdMMSUpload.uploadPatch — uploadSyncExternalPatch(e)
        var externalRef = new ExternalBlobReferenceBuilder().build();
        try {
            var uploaded = whatsapp.store()
                    .awaitMediaConnection()
                    .upload(externalRef, new ByteArrayInputStream(encoded)); // WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch
            if (!uploaded) {
                throw new IllegalStateException("Failed to upload external mutations to CDN");
            }
            return externalRef; // ADAPTED: WAWebSyncdMMSUpload.buildExternalBlobReference — fields populated by upload service
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while uploading external mutations", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to upload external mutations", throwable);
        }
    }

    /**
     * Deduplicates pending mutations by index, keeping only the last mutation
     * for each index.
     *
     * <p>Per WhatsApp Web {@code compactPatch}: when multiple mutations target
     * the same index (e.g., rapidly toggling a setting), only the final state
     * is sent to the server.
     *
     * @implNote WAWebSyncdRequestBuilderUtils.compactPatch
     * @param patches the pending mutations to compact
     * @return the compacted mutations
     */
    private SequencedCollection<SyncPendingMutation> compactPatch(SequencedCollection<SyncPendingMutation> patches) {
        // WAWebSyncdRequestBuilderUtils.compactPatch — e.reverse(), deduplicate, .reverse()
        var reversed = new ArrayList<SyncPendingMutation>(patches);
        Collections.reverse(reversed); // WAWebSyncdRequestBuilderUtils.compactPatch — e.reverse()
        var seen = new HashSet<String>();
        var deduplicated = new ArrayList<SyncPendingMutation>(reversed.size());
        for (var patch : reversed) {
            if (seen.add(patch.mutation().index())) { // WAWebSyncdRequestBuilderUtils.l — unique by index
                deduplicated.add(patch);
            }
        }
        Collections.reverse(deduplicated); // WAWebSyncdRequestBuilderUtils.compactPatch — .reverse()
        return List.copyOf(deduplicated);
    }
}
