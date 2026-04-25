package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.signal.KeyIdBuilder;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.SyncActionEntry;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
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
import com.github.auties00.cobalt.wam.event.MediaUpload2EventBuilder;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MediaUploadModeType;
import com.github.auties00.cobalt.wam.type.MediaUploadResultType;
import com.github.auties00.cobalt.wam.type.UploadOriginType;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.time.Duration;
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
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestBuilder")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestBuilderBuild")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestBuilderTypesConverter")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestBuilderUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestEncode")
@WhatsAppWebModule(moduleName = "WAWebSyncdMMSUpload")
public final class MutationRequestBuilder {
    private static final Logger LOGGER = Logger.getLogger(MutationRequestBuilder.class.getName());

    private final WhatsAppClient whatsapp;
    private final ABPropsService abPropsService;

    /**
     * Constructs a new mutation request builder.
     *
     * @implNote ADAPTED: WAWebSyncdRequestBuilder, WAWebSyncdRequestBuilderBuild — module-level imports replaced
     *           with constructor DI per Cobalt architecture
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
     * <p>Semantically equivalent to invoking
     * {@code WAWebSyncdRequestBuilder.buildAppStateSyncRequest} with a
     * single-collection set: compacts mutation ids (pre-compaction) per
     * WA Web's {@code compactMap(t, e => e.id)}, then delegates to
     * {@code buildSyncIqNode}.
     *
     * @implNote WAWebSyncdRequestBuilder.buildAppStateSyncRequest
     *           (single-collection variant),
     *           WAWebSyncdRequestBuilderBuild.buildSyncIqNode,
     *           WAWebSyncdRequestBuilderBuild._buildCollectionNodes.
     *           ADAPTED: WA Web always batches across the dirty set; Cobalt
     *           additionally exposes a single-collection variant for the
     *           individual retry path in
     *           {@code WAWebSyncdServerSync.serverSync}.
     * @param patchType the collection type to sync
     * @param patches   the pending mutations to include
     * @return the sync request containing the IQ node and optional upload info
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilder", exports = "buildAppStateSyncRequest", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilderBuild", exports = "buildSyncIqNode", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncRequest buildSyncRequest(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
        // ADAPTED: WAWebSyncdRequestBuilderBuild._buildCollectionNodes — getCollectionVersionInTransaction
        var hashState = whatsapp.store()
                .findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));

        // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — check if collection has been bootstrapped
        var bootstrapped = whatsapp.store().findWebAppState(patchType).bootstrapped(); // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — n = getCollectionVersionInTransaction(e).then(e => e?.version)

        // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — wap("collection", ...)
        var collectionBuilder = new NodeBuilder()
                .description("collection")
                .attribute("name", patchType.toString()) // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — CUSTOM_STRING(t)
                .attribute("version", hashState.version()) // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — INT(a ?? DEFAULT_COLLECTION_VERSION)
                .attribute("return_snapshot", !bootstrapped ? "true" : "false"); // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — a === void 0

        SyncRequest.UploadedPatchInfo uploadInfo = null;
        // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — if (n == null) a.push(e): skip mutations for unbootstrapped collections
        if (bootstrapped) {
            // WAWebSyncdRequestBuilder.buildAppStateSyncRequest — compactMap(t, e => e.id) collects ALL IDs pre-compaction
            var allMutationIds = new ArrayList<String>(patches.size());
            for (var patch : patches) {
                if (patch.mutationId() != null) {
                    allMutationIds.add(patch.mutationId()); // WAWebSyncdRequestBuilder.buildAppStateSyncRequest — compactMap(t, function(e) { return e.id })
                }
            }

            var compacted = compactPatch(patches); // WAWebSyncdRequestBuilderUtils.compactPatch
            if (!compacted.isEmpty()) {
                var buildResult = buildPatchProtobuf(patchType, compacted, hashState, List.copyOf(allMutationIds)); // WAWebSyncdRequestBuilderBuild.C
                var patchNode = new NodeBuilder()
                        .description("patch") // WAWebSyncdRequestBuilderBuild.C — wap("patch", null, C)
                        .content(buildResult.bytes())
                        .build();
                collectionBuilder.content(patchNode);
                uploadInfo = buildResult.uploadInfo();
            }
        } else if (!patches.isEmpty()) {
            // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — a.push(e): log skipped bootstrap collection
            LOGGER.info("syncd: skipping mutations for collection " + patchType
                    + " because initial full sync is incomplete"); // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — LOG("syncd: skipping N collections in sync iq patch because initial full sync is incomplete")
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

    /**
     * Internal result of building a serialized patch protobuf, pairing the
     * encoded bytes with the upload metadata needed for post-response processing.
     *
     * @param bytes      the serialized {@code SyncdPatch} protobuf bytes
     * @param uploadInfo the upload metadata for post-response processing
     * @implNote WAWebSyncdRequestBuilderBuild._buildCollectionNodes (inner function b/v return value)
     */
    private record PatchBuildResult(byte[] bytes, SyncRequest.UploadedPatchInfo uploadInfo) {
    }

    /**
     * Represents the result of building a batched sync request across multiple
     * collections, pairing the IQ node with per-collection upload metadata
     * and a set of collections whose patches were skipped.
     *
     * @param node           the IQ request node builder
     * @param uploadInfos    per-collection upload metadata for post-response processing
     * @param skippedUploads collections whose patches were skipped (e.g., compacted to empty)
     * @implNote WAWebSyncdRequestBuilder.buildAppStateSyncRequest (return value structure)
     */
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
     *           WAWebSyncdRequestBuilderBuild._generateMutationsToUpload (orphan REMOVE filter
     *           step — function D lines filtering REMOVE mutations whose index is not in
     *           {@code getSyncActionsByCollectionsInTransaction}; the rotation-mutation
     *           generation step is delegated to {@link #buildKeyRotationMutations}),
     *           WAWebSyncdAntiTampering.computeOutgoingSnapshotAndPatchMacs,
     *           WAWebSyncdMMSUpload.exceedInlineMutationCount (function g — inlined threshold check on
     *           {@code syncdMutations.size()} vs {@code min(2000, max(100, abProp))}),
     *           WAWebSyncdMMSUpload.exceedPatchProtobufSize (function h — inlined threshold check on
     *           encoded patch bytes vs {@code min(100, max(10, abProp)) * 1000}),
     *           WAWebSyncdMMSUpload.uploadPatch (function d/m — delegated to
     *           {@link #uploadExternalMutations(List)}),
     *           WAWebSyncdRequestBuilderBuild.L (inline patch builder)
     * @param patchType       the collection type
     * @param patches         the compacted pending mutations
     * @param hashState       the current hash state for this collection
     * @param allMutationIds  all original pending mutation IDs (pre-compaction) for cleanup tracking;
     *                        per WA Web {@code buildAppStateSyncRequest}: all pending mutation IDs are
     *                        tracked via {@code compactMap(t, e => e.id)} before compaction so that
     *                        {@code _uploadSuccessful} can remove ALL pending mutations, not just
     *                        the compacted subset
     * @return the serialized patch bytes and upload metadata
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMMSUpload", exports = "exceedInlineMutationCount", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdMMSUpload", exports = "exceedPatchProtobufSize", adaptation = WhatsAppAdaptation.ADAPTED)
    private PatchBuildResult buildPatchProtobuf(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches, SyncHashValue hashState, List<String> allMutationIds) {
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
            // WAWebSyncdRequestBuilderBuild._generateMutationsToUpload — filter orphaned REMOVE mutations
            var storedEntries = whatsapp.store().getSyncActionEntries(patchType); // WAWebSyncdRequestBuilderBuild.D — getSyncActionsByCollectionsInTransaction([e])
            var storedIndices = new HashSet<String>(storedEntries.size());
            for (var entry : storedEntries) {
                if (entry.actionIndex() != null) {
                    storedIndices.add(entry.actionIndex()); // WAWebSyncdRequestBuilderBuild.D — new Set(r.map(e => e.index))
                }
            }
            var filteredPatches = new ArrayList<SyncPendingMutation>(patches.size());
            for (var patch : patches) {
                if (patch.mutation().operation() == SyncdOperation.REMOVE
                        && !storedIndices.contains(patch.mutation().index())) { // WAWebSyncdRequestBuilderBuild.D — !a.has(t.index)
                    LOGGER.warning("syncd: dropping orphaned REMOVE mutation (no corresponding SET in SyncActionStore) for collection "
                            + patchType + ", action: " + patch.mutation().index()); // WAWebSyncdRequestBuilderBuild.D — WARN("syncd: dropping orphaned REMOVE mutation...")
                    continue;
                }
                filteredPatches.add(patch);
            }

            // WAWebSyncdRequestBuilderBuild.C — encrypt all mutations
            var encryptedMutations = encryptMutations(patchType, filteredPatches, derivedKeys, latestKeyId);
            var userMutationCount = encryptedMutations.size();

            // WAWebSyncdRequestBuilderBuild._generateMutationsToUpload — key rotation mutations
            var keyRotationSources = new ArrayList<DecryptedMutation.Trusted>();
            var keyRotationMutations = buildKeyRotationMutations(patchType, patches, storedEntries, derivedKeys, latestKeyId, keyRotationSources);
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
            var patchesIterator = filteredPatches.iterator();
            var keyRotationIterator = keyRotationSources.iterator();
            var uploadedMutations = new ArrayList<SyncRequest.UploadedMutationInfo>(encryptedMutations.size());
            var index = 0;
            for (var encrypted : encryptedMutations) {
                DecryptedMutation.Trusted source;
                if (index < userMutationCount) {
                    var pendingMutation = patchesIterator.next();
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
            // Per WA Web WAWebSyncdRequestBuilder.buildAppStateSyncRequest:
            // allMutationIds includes ALL original pending mutation IDs (pre-compaction),
            // ensuring _uploadSuccessful removes every pending mutation, not just the compacted subset
            var uploadInfo = new SyncRequest.UploadedPatchInfo(
                    patchType,
                    newLtHash,
                    newVersion,
                    allMutationIds,
                    List.copyOf(uploadedMutations)
            );

            // WAWebSyncdMMSUpload.exceedInlineMutationCount — check mutation count threshold
            var maxInlineCount = Math.min(2000, Math.max(100, SyncKeyUtils.getSyncdInlineMutationsMaxCount(abPropsService))); // WAWebSyncdMMSUpload.exceedInlineMutationCount — Math.min(s=2000, Math.max(getSyncdInlineMutationsMaxCount(), e=100)) — delegated to SyncKeyUtils.getSyncdInlineMutationsMaxCount (WAWebSyncdGatingUtils.getSyncdInlineMutationsMaxCount)
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
                var inlineBytes = encodeSyncdPatch(inlinePatch); // WAWebSyncdRequestEncode.encodeSyncdPatch
                // WAWebSyncdMMSUpload.exceedPatchProtobufSize — check encoded size threshold
                var maxSizeBytes = Math.min(100, Math.max(10, SyncKeyUtils.getSyncdPatchProtobufMaxSize(abPropsService))) * 1000L; // WAWebSyncdMMSUpload.exceedPatchProtobufSize — Math.min(c=100, Math.max(getSyncdPatchProtobufMaxSize(), u=10)) * 1e3 — delegated to SyncKeyUtils.getSyncdPatchProtobufMaxSize (WAWebSyncdGatingUtils.getSyncdPatchProtobufMaxSize)
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

            return new PatchBuildResult(encodeSyncdPatch(syncdPatch), uploadInfo); // WAWebSyncdRequestEncode.encodeSyncdPatch
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
     * @implNote WAWebSyncdRequestBuilderBuild._generateMutationsToUpload (function T/D),
     *           WAWebSyncdRequestBuilderBuild.x (rotation SET generation),
     *           WAWebSyncdRequestBuilderBuild.$ (rotation REMOVE generation),
     *           WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations (inlined
     *           at both rotation sites — Cobalt's {@link SyncActionEntry} already holds the
     *           decoded {@link com.github.auties00.cobalt.model.sync.SyncActionValue}, so the
     *           WA Web decode/re-encode pipeline collapses to wrapping the value in a
     *           {@link DecryptedMutation.Trusted} paired with the caller-supplied operation;
     *           the {@code binarySyncData → value → binarySyncAction} round-trip is absorbed
     *           by {@link EncryptedMutation#of(SyncPendingMutation, MutationKeys, byte[])}
     *           which re-serializes the value when it builds the outgoing {@code SyncActionData}.
     *           Per-field mapping for the WA Web pending-mutation object literal:
     *           {@code collection: e.collection} is flattened — the rotation generator runs
     *           inside a {@code patchType}-scoped loop so the collection name is implicit;
     *           {@code index: e.index} maps to {@code entry.actionIndex()};
     *           {@code binarySyncAction: a} (the re-encoded {@code SyncActionValue} bytes) is
     *           produced lazily inside {@link EncryptedMutation#of(SyncPendingMutation, MutationKeys, byte[])}
     *           rather than eagerly here, so {@link DecryptedMutation.Trusted} carries the
     *           decoded {@link com.github.auties00.cobalt.model.sync.SyncActionValue} and lets
     *           the encryption step serialize once;
     *           {@code operation: t} maps to the caller-supplied {@link SyncdOperation}
     *           ({@code SET} for the rotation-set path, {@code REMOVE} for the rotation-remove
     *           path);
     *           {@code version: e.version} maps to {@code entry.actionVersion()};
     *           {@code timestamp: e.timestamp} is not preserved on
     *           {@link DecryptedMutation.Trusted} because Cobalt's {@link SyncActionEntry}
     *           does not store a per-action timestamp (WA Web's {@code SyncActionEntry} carries
     *           one only because it is populated from a freshly received mutation that has the
     *           timestamp from the wire). Cobalt synthesizes {@link java.time.Instant#now()}
     *           solely to satisfy the {@code Trusted} record's non-null contract; the field is
     *           never read on the rotation path — neither
     *           {@link EncryptedMutation#of(SyncPendingMutation, MutationKeys, byte[])} (which
     *           uses {@link com.github.auties00.cobalt.model.sync.SyncActionValue#timestamp()}
     *           from the decoded value) nor the upload-metadata pairing in
     *           {@link #buildPatchProtobuf} (which only reads {@code index}, {@code value},
     *           {@code actionVersion}) consume it;
     *           {@code action: e.action} has no Cobalt counterpart — WA Web's stored
     *           {@code action} mirror is the higher-level {@code SyncActionMessage} envelope
     *           used for in-memory reactive collections, while Cobalt collapses that envelope
     *           into the {@link com.github.auties00.cobalt.model.sync.SyncActionValue} held by
     *           {@code entry.actionValue()}, which is the same payload re-derived on demand.
     *           WAWebSyncdWamAppState.addKeyRotationRemoveCount (skipped per Cobalt's
     *           telemetry policy — WA Web reports the rotation REMOVE count as a WAM metric).
     *           ADAPTED: the orphan-REMOVE filter step of {@code _generateMutationsToUpload}
     *           lives in the caller ({@link #buildPatchProtobuf}) because Cobalt encrypts the
     *           user mutations first and delegates rotation generation here; the concatenation
     *           of user mutations + rotation SETs + rotation REMOVEs happens at the call site.
     * @param patchType   the collection type
     * @param patches     the core mutations being sent (to exclude from rotation)
     * @param storedEntries the stored sync action entries for this collection, reused from the caller
     *                      to match WA Web's single {@code getSyncActionsByCollectionsInTransaction} call
     * @param derivedKeys the derived keys from the latest sync key
     * @param latestKeyId the latest app state sync key ID
     * @param keyRotationSourcesOut output list populated with source data for upload metadata
     * @return the additional encrypted mutations for key rotation (SETs + REMOVEs)
     * @throws GeneralSecurityException if encryption fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilderBuild", exports = "_generateMutationsToUpload", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilderTypesConverter", exports = "syncActionsToPendingMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private List<EncryptedMutation> buildKeyRotationMutations(
            SyncPatchType patchType,
            SequencedCollection<SyncPendingMutation> patches,
            Collection<SyncActionEntry> storedEntries,
            MutationKeys derivedKeys,
            byte[] latestKeyId,
            List<DecryptedMutation.Trusted> keyRotationSourcesOut
    ) throws GeneralSecurityException {
        // WAWebSyncdRequestBuilderBuild.D — collect indices from current batch
        var batchIndices = new HashSet<String>();
        for (var patch : patches) {
            batchIndices.add(patch.mutation().index());
        }

        // WAWebSyncdRequestBuilderBuild.x — rotation SET list is sorted by key epoch before slicing
        // so the oldest-key entries are rotated first. The REMOVE rotation path in WA Web (function $)
        // does NOT sort; it iterates the stored entries in their original insertion order.
        // The sort therefore must be scoped to the SET path only.
        var sortedEntriesForSet = storedEntries.stream()
                .sorted(Comparator.comparingInt(e -> SyncKeyUtils.getKeyEpoch(e.keyId()))) // WAWebSyncdRequestBuilderBuild.x — sortBy(getKeyEpoch)
                .toList();
        var maxAdditional = Math.min(5, Math.max(0, abPropsService.getInt(ABProp.SYNCD_ADDITIONAL_MUTATIONS_COUNT))); // WAWebSyncdRequestBuilderBuild.D — Math.min(p=5, getABPropConfigValue("syncd_additional_mutations_count"))
        var result = new ArrayList<EncryptedMutation>();

        for (var entry : sortedEntriesForSet) {
            if (result.size() >= maxAdditional) { // WAWebSyncdRequestBuilderBuild.x — i.slice(0, l)
                break;
            }

            if (SyncKeyUtils.syncKeyIdsEqual(entry.keyId(), latestKeyId)) { // WAWebSyncdRequestBuilderBuild.x — !syncKeyIdsEqual(e.keyId, n) — delegated to SyncKeyUtils.syncKeyIdsEqual (WAWebSyncdCryptoUtils.syncKeyIdsEqual)
                continue;
            }

            if (entry.actionIndex() == null || entry.actionValue() == null) { // ADAPTED: defensive null check
                continue;
            }

            if (batchIndices.contains(entry.actionIndex())) { // WAWebSyncdRequestBuilderBuild.x — !a.has(e.index)
                continue;
            }

            // ADAPTED: WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — inlined here.
            // WA Web returns {collection,index,binarySyncAction,operation,version,timestamp,action};
            // Cobalt collapses the 7-field literal into a Trusted record (see method-level @implNote
            // for the per-field mapping table).
            var trusted = new DecryptedMutation.Trusted(
                    entry.actionIndex(), // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — index: e.index
                    entry.actionValue(), // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — replaces {binarySyncAction: a, action: e.action}; a = encodeProtobuf(SyncActionValueSpec, decode(SyncActionDataSpec, e.binarySyncData).value) is performed lazily inside EncryptedMutation.of
                    SyncdOperation.SET, // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — operation: t (caller passes SyncdMutation$SyncdOperation.SET at WAWebSyncdRequestBuilderBuild.x)
                    Instant.now(), // ADAPTED: WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — timestamp: e.timestamp; SyncActionEntry has no timestamp field, so a placeholder is used; field is never read on this path
                    entry.actionVersion() // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — version: e.version
            );
            keyRotationSourcesOut.add(trusted);
            var pending = new SyncPendingMutation(trusted, 0);
            result.add(EncryptedMutation.of(pending, derivedKeys, latestKeyId));
        }

        // WAWebSyncdRequestBuilderBuild.$ — generate REMOVE mutations for old-key entries
        // Per WA Web: only SET operations' indices are collected (not REMOVE), matching
        // t.filter(e => e.operation === SET).map(e => e.index). The function $ iterates
        // the stored entries in their original order (no sortBy), unlike the SET path (x).
        var allSetIndices = new HashSet<String>();
        for (var patch : patches) {
            if (patch.mutation().operation() == SyncdOperation.SET) { // WAWebSyncdRequestBuilderBuild.$ — filter to SET operations only
                allSetIndices.add(patch.mutation().index());
            }
        }
        for (var rotationSource : keyRotationSourcesOut) {
            allSetIndices.add(rotationSource.index()); // WAWebSyncdRequestBuilderBuild.$ — rotation SETs always have SET operation
        }

        // WAWebSyncdRequestBuilderBuild.$ — iterate stored entries in original order (no sort)
        for (var entry : storedEntries) {
            if (entry.actionIndex() == null || !allSetIndices.contains(entry.actionIndex())) { // WAWebSyncdRequestBuilderBuild.$ — r.has(e.index)
                continue;
            }

            if (SyncKeyUtils.syncKeyIdsEqual(entry.keyId(), latestKeyId)) { // WAWebSyncdRequestBuilderBuild.$ — !syncKeyIdsEqual(e.keyId, n) — delegated to SyncKeyUtils.syncKeyIdsEqual (WAWebSyncdCryptoUtils.syncKeyIdsEqual)
                continue;
            }

            if (entry.actionValue() == null) {
                continue;
            }

            // ADAPTED: WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — inlined here.
            // WA Web returns {collection,index,binarySyncAction,operation,version,timestamp,action};
            // Cobalt collapses the 7-field literal into a Trusted record (see method-level @implNote
            // for the per-field mapping table).
            var removeTrusted = new DecryptedMutation.Trusted(
                    entry.actionIndex(), // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — index: e.index
                    entry.actionValue(), // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — replaces {binarySyncAction: a, action: e.action}; the SyncActionValue carried here is re-serialized inside EncryptedMutation.of
                    SyncdOperation.REMOVE, // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — operation: t (caller passes SyncdMutation$SyncdOperation.REMOVE at WAWebSyncdRequestBuilderBuild.$)
                    Instant.now(), // ADAPTED: WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — timestamp: e.timestamp; placeholder, never read on this path
                    entry.actionVersion() // WAWebSyncdRequestBuilderTypesConverter.syncActionsToPendingMutations — version: e.version
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
     * <p>Per WhatsApp Web {@code WAWebSyncdRequestBuilder.buildAppStateSyncRequest}:
     * for the set of dirty collections passed in, the pending mutations for
     * each collection are loaded, all pending mutation IDs are captured
     * (pre-compaction) via {@code compactMap(t, e => e.id)}, the mutations
     * are compacted, and the per-collection maps are passed to
     * {@code buildSyncIqNode} which returns a single IQ node containing one
     * {@code <sync>} with multiple {@code <collection>} children.
     *
     * <p>Per WA Web {@code buildAppStateSyncRequest}: when the AB prop
     * {@code kmp_syncd_engine_outgoing_processor_enabled} is {@code true},
     * the request is built via
     * {@code WAWebKmpSyncdRequestBuilder.buildOutgoingRequestWithKmp} instead.
     * Cobalt does not implement the experimental KMP engine path; the AB
     * prop defaults to {@code false} so this is a no-op under default
     * configuration.
     *
     * @implNote WAWebSyncdRequestBuilder.buildAppStateSyncRequest,
     *           WAWebSyncdRequestBuilderBuild.buildSyncIqNode,
     *           WAWebSyncdRequestBuilderBuild._buildCollectionNodes.
     *           ADAPTED: WA Web loads pending mutations internally via
     *           {@code getSyncPendingMutationsByCollectionInTransaction};
     *           Cobalt accepts pre-loaded mutations via the
     *           {@code collectionPatches} parameter (caller performs the
     *           load). The return shape is also restructured:
     *           {@code collectionWithPendingMutationsIds},
     *           {@code collectionWithEncryptedMutations}, and
     *           {@code localCollectionVersions} are carried per-collection
     *           inside each {@link SyncRequest.UploadedPatchInfo} in
     *           {@code uploadInfos} rather than as separate top-level maps;
     *           {@code pendingCollectionsInBootstrap} maps to
     *           {@code skippedUploads}. The KMP engine fallback
     *           ({@code kmp_syncd_engine_outgoing_processor_enabled}) is
     *           not implemented (default {@code false}).
     * @param collectionPatches map of collection types to their pending mutations
     * @return the batched sync request containing the IQ node, per-collection upload metadata, and skipped uploads
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilder", exports = "buildAppStateSyncRequest", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilderBuild", exports = "buildSyncIqNode", adaptation = WhatsAppAdaptation.ADAPTED)
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

            // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — check if collection has been bootstrapped
            var bootstrapped = whatsapp.store().findWebAppState(patchType).bootstrapped(); // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — n = getCollectionVersionInTransaction(e).then(e => e?.version)

            var collectionBuilder = new NodeBuilder()
                    .description("collection")
                    .attribute("name", patchType.toString())
                    .attribute("version", hashState.version())
                    .attribute("return_snapshot", !bootstrapped ? "true" : "false"); // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — a === void 0

            // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — if (n == null) a.push(e): skip mutations for unbootstrapped collections
            if (bootstrapped) {
                // WAWebSyncdRequestBuilder.buildAppStateSyncRequest — compactMap(t, e => e.id) collects ALL IDs pre-compaction
                var allMutationIds = new ArrayList<String>(patches.size());
                for (var patch : patches) {
                    if (patch.mutationId() != null) {
                        allMutationIds.add(patch.mutationId());
                    }
                }

                var compacted = compactPatch(patches);
                if (!compacted.isEmpty()) {
                    var buildResult = buildPatchProtobuf(patchType, compacted, hashState, List.copyOf(allMutationIds));
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
            } else if (!patches.isEmpty()) {
                // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — a.push(e): track skipped bootstrap collections
                skippedUploads.add(patchType); // ADAPTED: uses skippedUploads instead of separate pendingCollectionsInBootstrap
                LOGGER.info("syncd: skipping mutations for collection " + patchType
                        + " because initial full sync is incomplete"); // WAWebSyncdRequestBuilderBuild._buildCollectionNodes — LOG("syncd: skipping N collections...")
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
     * <p>Per WhatsApp Web {@code WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch}:
     * the blob is passed to {@code WAWebUploadManager.encryptAndUpload} with
     * {@code type: "md-app-state"}, {@code uploadOrigin: UNKNOWN},
     * {@code userUploadAttemptCount: 0}, {@code forwardedFromWeb: false},
     * {@code isViewOnce: false}. The result is validated for a non-null handle,
     * then the mediaKey and encFilehash are base64-decoded into the returned
     * reference. In Cobalt the {@code MediaConnection.upload} method handles
     * encryption, upload, and field population in a single call.
     *
     * @implNote WAWebSyncdMMSUpload.uploadPatch (function d/m), WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch,
     *           WAWebSyncdMMSUpload.buildExternalBlobReference (function p/_),
     *           WAWebSyncdRequestEncode.encodeSyncdMutations.
     *           ADAPTED: WA Web's three-step pipeline (uploadSyncExternalPatch → buildExternalBlobReference →
     *           f encodes SyncdPatch) is collapsed: uploadSyncExternalPatch + buildExternalBlobReference are
     *           fused into {@code MediaConnection.upload} which encrypts, uploads, and populates the
     *           {@link ExternalBlobReference} fields ({@code mediaKey}, {@code directPath}, {@code handle},
     *           {@code fileSizeBytes}, {@code fileSha256}, {@code fileEncSha256}) in a single call; the
     *           final {@code f} step (encoding SyncdPatch with externalMutations) is performed by the caller
     *           in {@link #buildPatchProtobuf} after this method returns
     * @param mutations the list of mutations to upload externally
     * @return the populated {@code ExternalBlobReference} with CDN metadata
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMMSUpload", exports = "uploadPatch", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdMMSUpload", exports = "buildExternalBlobReference", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdNetCallbacksApi", exports = "uploadSyncExternalPatch", adaptation = WhatsAppAdaptation.ADAPTED)
    private ExternalBlobReference uploadExternalMutations(List<SyncdMutation> mutations) {
        var syncdMutations = new SyncdMutationsBuilder()
                .mutations(mutations)
                .build();
        var encoded = encodeSyncdMutations(syncdMutations); // WAWebSyncdRequestEncode.encodeSyncdMutations
        // ADAPTED: WAWebSyncdMMSUpload.uploadPatch — uploadSyncExternalPatch(e)
        // WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch — encryptAndUpload({blob, type:"md-app-state", ...})
        var externalRef = new ExternalBlobReferenceBuilder().build();
        var uploadStart = Instant.now();
        try {
            var uploaded = whatsapp.store()
                    .awaitMediaConnection()
                    .upload(externalRef, new ByteArrayInputStream(encoded)); // WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch
            if (!uploaded) {
                // WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch — if (handle == null) throw err("Missing handle after uploading external patch to mms4")
                throw new IllegalStateException("Missing handle after uploading external patch to mms4");
            }
            commitMediaUpload2Success(uploadStart); // WAWebCreateMediaUploadMetrics.handleUploadSuccess
            return externalRef; // ADAPTED: WAWebSyncdMMSUpload.buildExternalBlobReference — fields populated by upload service
        } catch (InterruptedException exception) {
            commitMediaUpload2Failure(uploadStart, exception); // WAWebCreateMediaUploadMetrics.handleUploadError
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while uploading external mutations", exception);
        } catch (IllegalStateException exception) {
            commitMediaUpload2Failure(uploadStart, exception); // WAWebCreateMediaUploadMetrics.handleUploadError
            throw exception;
        } catch (Throwable throwable) {
            commitMediaUpload2Failure(uploadStart, throwable); // WAWebCreateMediaUploadMetrics.handleUploadError
            throw new IllegalStateException("Failed to upload external mutations", throwable);
        }
    }

    /**
     * Commits a successful {@code MediaUpload2Event} for an app-state external
     * patch upload.
     *
     * <p>Mirrors the terminal success path of
     * {@code WAWebCreateMediaUploadMetrics.handleUploadSuccess} (the {@code f}
     * handler returned by the metrics factory): sets
     * {@code overallUploadResult=OK}, {@code overallIsFinal=true}, the
     * {@code resumeHttpCode/uploadHttpCode/finalizeHttpCode} HTTP codes, and
     * the factory-seeded descriptors (mediaType, upload origin, upload mode,
     * mms version). The WA Web factory seeds additional transient timers via
     * {@code markOverallCumT}; Cobalt collapses the CDN upload into one
     * {@code MediaConnection.upload} call and reports the end-to-end duration
     * as {@code overallT}.
     *
     * @implNote WAWebCreateMediaUploadMetrics.handleUploadSuccess invoked from
     * WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch via
     * WAWebUploadManager.encryptAndUpload with {@code type="md-app-state"} and
     * {@code uploadOrigin=UNKNOWN} (the Web reference passes
     * {@code UPLOAD_ORIGIN.UNKNOWN} because app-state uploads are not bound to
     * a chat; Cobalt reports {@code MESSAGE_HISTORY_SYNC} to align with the
     * download-side {@code MediaDownload2Event} that the same flow emits).
     * @param uploadStart the instant at which the upload attempt began
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateMediaUploadMetrics",
            exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitMediaUpload2Success(Instant uploadStart) {
        var overallT = Instant.ofEpochMilli(Duration.between(uploadStart, Instant.now()).toMillis());
        whatsapp.wamService().commit(new MediaUpload2EventBuilder()
                .overallMediaType(MediaType.MD_APP_STATE)
                .overallMmsVersion(4)
                .overallUploadOrigin(UploadOriginType.MESSAGE_HISTORY_SYNC)
                .overallUploadMode(MediaUploadModeType.REGULAR)
                .overallUploadResult(MediaUploadResultType.OK)
                .overallIsFinal(Boolean.TRUE)
                .resumeHttpCode(404)
                .uploadHttpCode(200)
                .finalizeHttpCode(200)
                .overallT(overallT)
                .overallAttemptCount(1)
                .overallRetryCount(0)
                .build());
    }

    /**
     * Commits a failing {@code MediaUpload2Event} for an app-state external
     * patch upload.
     *
     * <p>Mirrors the terminal error path of
     * {@code WAWebCreateMediaUploadMetrics.handleUploadError} (the {@code g}
     * handler returned by the metrics factory): classifies the thrown error
     * into a {@link MediaUploadResultType} via the equivalent of
     * {@code WAWebWamMediaMetricUtils.getMetricUploadErrorResultType}, sets
     * {@code overallIsFinal=true}, and when the error carries an HTTP status
     * code records it on {@code uploadHttpCode} and {@code finalizeHttpCode}
     * (matching the {@code u.uploadHttpCode = n; u.finalizeHttpCode = n}
     * assignments in the WA Web reference).
     *
     * @implNote WAWebCreateMediaUploadMetrics.handleUploadError invoked from
     * WAWebSyncdNetCallbacksApi.uploadSyncExternalPatch when the underlying
     * {@code encryptAndUpload} call rejects.
     * @param uploadStart the instant at which the upload attempt began
     * @param throwable   the error that aborted the upload
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateMediaUploadMetrics",
            exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricUploadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitMediaUpload2Failure(Instant uploadStart, Throwable throwable) {
        var overallT = Instant.ofEpochMilli(Duration.between(uploadStart, Instant.now()).toMillis());
        var builder = new MediaUpload2EventBuilder()
                .overallMediaType(MediaType.MD_APP_STATE)
                .overallMmsVersion(4)
                .overallUploadOrigin(UploadOriginType.MESSAGE_HISTORY_SYNC)
                .overallUploadMode(MediaUploadModeType.REGULAR)
                .overallUploadResult(classifyMediaUploadError(throwable))
                .overallIsFinal(Boolean.TRUE)
                .overallT(overallT)
                .overallAttemptCount(1)
                .overallRetryCount(0);
        var statusCode = extractUploadHttpStatusCode(throwable);
        if (statusCode != null) {
            builder.uploadHttpCode(statusCode);
            builder.finalizeHttpCode(statusCode);
        }
        whatsapp.wamService().commit(builder.build());
    }

    /**
     * Classifies a thrown upload error into a {@link MediaUploadResultType}.
     *
     * <p>The WA Web reference inspects the error's prototype chain
     * ({@code MMSThrottleError}, {@code MMSUnauthorizedError},
     * {@code MediaTooLargeError}, {@code MediaInvalidError}, generic
     * {@code HttpStatusCodeError}); Cobalt collapses the MMS error classes
     * into {@link WhatsAppMediaException.Upload} with an optional HTTP status
     * code, so the classifier dispatches on the status code when present.
     *
     * @implNote WAWebWamMediaMetricUtils.getMetricUploadErrorResultType.
     * @param throwable the error that aborted the upload
     * @return the matching {@link MediaUploadResultType}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricUploadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private MediaUploadResultType classifyMediaUploadError(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            return MediaUploadResultType.ERROR_CANCEL;
        }
        if (throwable instanceof WhatsAppMediaException.Upload uploadException) {
            var statusCode = uploadException.httpStatusCode();
            if (statusCode.isPresent()) {
                return switch (statusCode.getAsInt()) {
                    case 401 -> MediaUploadResultType.ERROR_REQUEST;
                    case 413 -> MediaUploadResultType.ERROR_TOO_LARGE;
                    case 415 -> MediaUploadResultType.ERROR_BAD_MEDIA;
                    case 507 -> MediaUploadResultType.ERROR_THROTTLE;
                    default -> {
                        if (statusCode.getAsInt() >= 500) {
                            yield MediaUploadResultType.ERROR_SERVER;
                        }
                        yield MediaUploadResultType.ERROR_UPLOAD;
                    }
                };
            }
            return MediaUploadResultType.ERROR_UPLOAD;
        }
        if (throwable instanceof WhatsAppMediaException) {
            return MediaUploadResultType.ERROR_UPLOAD;
        }
        return MediaUploadResultType.ERROR_UNKNOWN;
    }

    /**
     * Extracts the HTTP status code carried by an upload error, if any.
     *
     * <p>Mirrors {@code WAWebWamMediaMetricUtils.getStatusCode}, which reads
     * the {@code status} attribute from {@code HttpStatusCodeError} instances.
     *
     * @implNote WAWebWamMediaMetricUtils.getStatusCode.
     * @param throwable the error to inspect
     * @return the HTTP status code, or {@code null} when the error does not
     *         carry one
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getStatusCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Integer extractUploadHttpStatusCode(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WhatsAppMediaException mediaException) {
                var statusCode = mediaException.httpStatusCode();
                if (statusCode.isPresent()) {
                    return statusCode.getAsInt();
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Deduplicates pending mutations by index, keeping only the last mutation
     * for each index.
     *
     * <p>Per WhatsApp Web {@code compactPatch}: when multiple mutations target
     * the same index (e.g., rapidly toggling a setting), only the final state
     * is sent to the server. The reference implementation reverses the input,
     * applies a {@code unique-by-key} filter (internal helper {@code c}) keyed
     * on {@code e.index}, then reverses the result so that the last occurrence
     * of each index is preserved in its original relative position.
     *
     * @implNote WAWebSyncdRequestBuilderUtils.compactPatch (and its internal
     *           unique-by-key helper {@code c}). ADAPTED: WA Web reverses the
     *           input array in place via {@code e.reverse()}; Cobalt makes a
     *           defensive copy before reversing so the caller's
     *           {@link SequencedCollection} is not mutated. The key function
     *           {@code e => e.index} maps to {@code patch.mutation().index()}
     *           because Cobalt's {@link SyncPendingMutation} wraps the mutation
     *           inside a {@link DecryptedMutation.Trusted} record rather than
     *           exposing {@code index} as a direct property. The sibling
     *           exports {@code compactKmpPatch} and {@code compactKmpPatchArray}
     *           in the same WA Web module are not ported: they exist only as
     *           JS-to-Kotlin FFI glue for the experimental KMP syncd engine
     *           ({@code WAWebKmpSyncdRequestBuilder.buildOutgoingRequestWithKmp}),
     *           which Cobalt intentionally does not implement (see the
     *           {@code kmp_syncd_engine_outgoing_processor_enabled} AB prop
     *           handling above). Those helpers dedupe by {@code encodedIndex}
     *           on the Kotlin-side mutation shape; Cobalt's JVM-only
     *           {@link SyncPendingMutation} has no {@code encodedIndex} field.
     * @param patches the pending mutations to compact
     * @return the compacted mutations
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilderUtils", exports = "compactPatch", adaptation = WhatsAppAdaptation.ADAPTED)
    private SequencedCollection<SyncPendingMutation> compactPatch(SequencedCollection<SyncPendingMutation> patches) {
        // WAWebSyncdRequestBuilderUtils.compactPatch — return c(e.reverse(), e => e.index).reverse()
        var reversed = new ArrayList<SyncPendingMutation>(patches);
        Collections.reverse(reversed); // WAWebSyncdRequestBuilderUtils.compactPatch — e.reverse()
        var seen = new HashSet<String>();
        var deduplicated = new ArrayList<SyncPendingMutation>(reversed.size());
        for (var patch : reversed) {
            // WAWebSyncdRequestBuilderUtils.c — var n=new Set; return e.filter(e => n.has(t(e)) ? false : (n.add(t(e)), true))
            if (seen.add(patch.mutation().index())) {
                deduplicated.add(patch);
            }
        }
        Collections.reverse(deduplicated); // WAWebSyncdRequestBuilderUtils.compactPatch — .reverse()
        return List.copyOf(deduplicated);
    }

    /**
     * Encodes a {@code SyncdPatch} protobuf message into its binary representation.
     *
     * <p>Wraps the protobuf serialization in error handling that converts any encoding
     * failure into a fatal {@link WhatsAppWebAppStateSyncException.UnexpectedError},
     * matching WA Web's {@code SyncdFatalError("patch protobuf serialization failed")}.
     *
     * @implNote WAWebSyncdRequestEncode.encodeSyncdPatch — WAM fatal-error metric
     *           {@code reportSyncdFatalError(PATCH_PROTOBUF_SERIALIZATION_FAILED)} is
     *           skipped per Cobalt's telemetry policy; the thrown
     *           {@link WhatsAppWebAppStateSyncException.UnexpectedError} matches WA Web's
     *           {@code SyncdFatalError("patch protobuf serialization failed")}
     * @param patch the syncd patch to encode
     * @return the protobuf-encoded bytes
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if protobuf serialization fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestEncode", exports = "encodeSyncdPatch", adaptation = WhatsAppAdaptation.DIRECT)
    private static byte[] encodeSyncdPatch(SyncdPatch patch) {
        try {
            return SyncdPatchSpec.encode(patch); // WAWebSyncdRequestEncode.encodeSyncdPatch — encodeProtobuf(SyncdPatchSpec, e).readBuffer()
        } catch (Exception exception) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError( // WAWebSyncdRequestEncode.encodeSyncdPatch — SyncdFatalError("patch protobuf serialization failed")
                    "patch protobuf serialization failed", exception
            );
        }
    }

    /**
     * Encodes a {@code SyncdMutations} protobuf message into its binary representation.
     *
     * <p>Wraps the protobuf serialization in error handling that converts any encoding
     * failure into a fatal {@link WhatsAppWebAppStateSyncException.UnexpectedError},
     * matching WA Web's {@code SyncdFatalError("mutations protobuf serialization failed")}.
     *
     * @implNote WAWebSyncdRequestEncode.encodeSyncdMutations — WAM fatal-error metric
     *           {@code reportSyncdFatalError(MUTATIONS_PROTOBUF_SERIALIZATION_FAILED)} is
     *           skipped per Cobalt's telemetry policy; the thrown
     *           {@link WhatsAppWebAppStateSyncException.UnexpectedError} matches WA Web's
     *           {@code SyncdFatalError("mutations protobuf serialization failed")}
     * @param mutations the syncd mutations to encode
     * @return the protobuf-encoded bytes
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if protobuf serialization fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestEncode", exports = "encodeSyncdMutations", adaptation = WhatsAppAdaptation.DIRECT)
    private static byte[] encodeSyncdMutations(SyncdMutations mutations) {
        try {
            return SyncdMutationsSpec.encode(mutations); // WAWebSyncdRequestEncode.encodeSyncdMutations — encodeProtobuf(SyncdMutationsSpec, e).readBuffer()
        } catch (Exception exception) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError( // WAWebSyncdRequestEncode.encodeSyncdMutations — SyncdFatalError("mutations protobuf serialization failed")
                    "mutations protobuf serialization failed", exception
            );
        }
    }
}
