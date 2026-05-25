package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestMessageBuilder;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestMessageSyncDCollectionFatalRecoveryRequestBuilder;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestResponseMessage;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestType;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecovery;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecoverySpec;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.NonMessagePeerDataRequestEventBuilder;
import com.github.auties00.cobalt.wam.type.PeerDataRequestType;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Drives the companion-to-primary syncd snapshot recovery peer-data
 * operation that lets a companion ask the primary device for a corrected
 * snapshot when its local snapshot MAC fails to validate.
 *
 * <p>The service is invoked by {@link WebAppStateService} as part of the
 * syncd patch pipeline, after a {@link WhatsAppWebAppStateSyncException}
 * marked as fatal is raised; embedders never call it directly. Recovery
 * covers a snapshot mismatch on a non-{@link SyncPatchType#CRITICAL_BLOCK}
 * collection when the AB-prop gate is on, the primary device supports
 * recovery, and the mutation count is within the configured budget.
 */
@WhatsAppWebModule(moduleName = "WAWebRequestSyncdSnapshotRecovery")
@WhatsAppWebModule(moduleName = "WAWebSyncdSnapshotRecoveryGatingUtils")
@WhatsAppWebModule(moduleName = "WAWebSendNonMessageDataRequest")
public final class SnapshotRecoveryService {
    /**
     * The logger used for non-fatal recovery diagnostics; recovery failures
     * are logged here and the call returns {@code null} so the caller can
     * fall back to a full re-link.
     */
    private static final Logger LOGGER = Logger.getLogger(SnapshotRecoveryService.class.getName());

    /**
     * The hard upper bound, in milliseconds, on a single recovery call.
     */
    private static final long RECOVERY_TIMEOUT_MS = 60_000;

    /**
     * The {@link WhatsAppClient} used to send the peer-data operation request
     * to the primary device.
     */
    private final WhatsAppClient client;

    /**
     * The {@link ABPropsService} used to read the recovery-enabled and
     * maximum-mutation-count gates.
     */
    private final ABPropsService abPropsService;

    /**
     * The {@link WamService} used to commit the per-request telemetry event.
     */
    private final WamService wamService;

    /**
     * The map of in-flight per-collection recovery futures.
     */
    private final Map<SyncPatchType, CompletableFuture<SyncdSnapshotRecovery>> pendingRecoveries;

    /**
     * The semaphore that serialises concurrent recovery attempts across
     * collections.
     */
    private final Semaphore recoverySemaphore;

    /**
     * Builds a new recovery service.
     *
     * <p>Constructed once by {@link WebAppStateService}; the service caches
     * its dependencies and lives for the lifetime of the parent client.
     *
     * @param client         the {@link WhatsAppClient} used to send the
     *                       recovery peer-message
     * @param abPropsService the {@link ABPropsService} that gates the
     *                       recovery flow
     * @param wamService     the {@link WamService} used to commit the
     *                       per-request telemetry event
     */
    @WhatsAppWebExport(moduleName = "WAWebRequestSyncdSnapshotRecovery", exports = "SyncdSnapshotRecoveryModule", adaptation = WhatsAppAdaptation.ADAPTED)
    public SnapshotRecoveryService(WhatsAppClient client, ABPropsService abPropsService, WamService wamService) {
        this.client = client;
        this.abPropsService = abPropsService;
        this.wamService = wamService;
        this.pendingRecoveries = new ConcurrentHashMap<>();
        this.recoverySemaphore = new Semaphore(1);
    }

    /**
     * Returns whether snapshot recovery is enabled for this device.
     *
     * <p>Both the persisted primary-device support flag and the
     * recovery-enabled AB-prop must be set for recovery to be attempted.
     *
     * @return {@code true} when both the primary-device support flag and the
     *         AB-prop are enabled
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdSnapshotRecoveryGatingUtils", exports = "syncdSnapshotRecoveryEnabled", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isRecoveryEnabled() {
        if (!client.store().primaryDeviceSupportsSyncdRecovery()) {
            return false;
        }

        return abPropsService.getBool(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY);
    }

    /**
     * Persists the flag indicating whether the primary device supports syncd
     * snapshot recovery.
     *
     * <p>Called from the device-capabilities ingest path when the primary
     * advertises (or stops advertising) recovery support; the flag is read
     * back by {@link #isRecoveryEnabled()}.
     *
     * @param supported {@code true} when the primary supports recovery
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdSnapshotRecoveryGatingUtils", exports = "updatePrimaryDeviceSupportsSyncdRecovery", adaptation = WhatsAppAdaptation.ADAPTED)
    public void updatePrimaryDeviceSupportsSyncdRecovery(boolean supported) {
        client.store().setPrimaryDeviceSupportsSyncdRecovery(supported);
    }

    /**
     * Returns whether a recovery attempt should be made for the given
     * collection.
     *
     * <p>Composes {@link #isRecoveryEnabled()} with the collection-name and
     * mutation-count gates: recovery is rejected when the global gate is off,
     * when the collection is {@link SyncPatchType#CRITICAL_BLOCK}, or when the
     * mutation count is strictly greater than the configured maximum.
     *
     * @implNote This implementation expects the caller (typically
     * {@link WebAppStateService}) to have already classified the triggering
     * exception as fatal; Cobalt's sealed exception hierarchy enforces that
     * statically at call sites rather than with an {@code instanceof} check
     * here.
     *
     * @param collectionName the collection that failed snapshot validation
     * @param mutationCount  the number of decrypted mutations in the failing
     *                       snapshot
     * @return {@code true} when recovery should be attempted
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdSnapshotRecoveryGatingUtils", exports = "shouldPreformSnapshotRecovery", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean shouldAttemptRecovery(SyncPatchType collectionName, int mutationCount) {
        if (!isRecoveryEnabled()) {
            return false;
        }

        if (collectionName == SyncPatchType.CRITICAL_BLOCK) {
            return false;
        }

        var maxMutations = abPropsService.getInt(ABProp.SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED);
        return mutationCount <= maxMutations;
    }

    /**
     * Sends a recovery request for {@code collectionName} and blocks until
     * the primary device responds or the timeout elapses.
     *
     * <p>Called by {@link WebAppStateService} when an incoming snapshot fails
     * validation and {@link #shouldAttemptRecovery(SyncPatchType, int)} has
     * already returned {@code true}. Returns {@code null} on any failure
     * (timeout, semaphore-acquisition timeout, primary error) so the caller
     * can fall back to a full re-link.
     *
     * @implNote This implementation funnels every concurrent recovery through
     * {@link #recoverySemaphore} so two collections cannot race the primary's
     * promise registry. The same {@value #RECOVERY_TIMEOUT_MS} ms budget
     * covers both the semaphore acquisition and the response wait, so a slow
     * concurrent recovery does not extend the effective per-call timeout. The
     * {@code primaryDevice == null} bail-out is Cobalt-specific because the
     * caller's JID may not be set yet during early startup.
     *
     * @param collectionName the collection to recover
     * @return the decoded snapshot returned by the primary, or {@code null}
     *         on any failure
     */
    @WhatsAppWebExport(moduleName = "WAWebRequestSyncdSnapshotRecovery", exports = "SyncdSnapshotRecoveryModule", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncdSnapshotRecovery requestRecovery(SyncPatchType collectionName) {
        var startTime = System.currentTimeMillis();
        try {
            if (!recoverySemaphore.tryAcquire(RECOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOGGER.warning("Snapshot recovery timed out waiting for concurrent recovery to complete");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        try {
            var responseFuture = pendingRecoveries.computeIfAbsent(collectionName,
                    _ -> new CompletableFuture<>());

            sendRecoveryRequest(collectionName);

            var elapsed = System.currentTimeMillis() - startTime;
            var remainingTimeout = RECOVERY_TIMEOUT_MS - elapsed;
            if (remainingTimeout <= 0) {
                LOGGER.warning("Snapshot recovery timed out after semaphore acquisition for collection " + collectionName);
                return null;
            }
            return responseFuture.get(remainingTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warning("Snapshot recovery failed for collection " + collectionName + ": " + e.getMessage());
            return null;
        } finally {
            pendingRecoveries.remove(collectionName);
            recoverySemaphore.release();
        }
    }

    /**
     * Completes the in-flight recovery future for {@code collectionName} with
     * the decoded snapshot.
     *
     * <p>Called by the protocol-message receiver when a peer-data operation
     * response of the snapshot fatal-recovery type arrives; the receiver
     * decodes the snapshot via
     * {@link #decodeRecoverySnapshot(PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse)}
     * first and forwards the decoded value here. A missing future is logged
     * at FINE and silently dropped.
     *
     * @param collectionName    the collection name carried by the decoded
     *                          snapshot
     * @param recoveredSnapshot the decoded {@link SyncdSnapshotRecovery}
     */
    @WhatsAppWebExport(moduleName = "WAWebRequestSyncdSnapshotRecovery", exports = "SyncdSnapshotRecoveryModule", adaptation = WhatsAppAdaptation.ADAPTED)
    public void resolveRecovery(
            SyncPatchType collectionName,
            SyncdSnapshotRecovery recoveredSnapshot
    ) {
        var future = pendingRecoveries.get(collectionName);
        if (future != null) {
            future.complete(recoveredSnapshot);
        } else {
            LOGGER.fine("Received snapshot recovery response for " + collectionName + " but no pending request found");
        }
    }

    /**
     * Decodes a snapshot fatal-recovery response payload into a
     * {@link SyncdSnapshotRecovery}.
     *
     * <p>Called by the protocol-message receiver before invoking
     * {@link #resolveRecovery(SyncPatchType, SyncdSnapshotRecovery)} so that
     * the same decoded value is shared between the resolver and any
     * downstream consumer. Gzip-compressed responses are streamed through a
     * {@link GZIPInputStream} transparently.
     *
     * @param response the raw snapshot fatal-recovery response carried by the
     *                 peer-data operation response
     * @return the decoded {@link SyncdSnapshotRecovery}
     * @throws NoSuchElementException                                if the
     *         response carries no {@code collectionSnapshot} bytes
     * @throws WhatsAppWebAppStateSyncException.ExternalDecodeFailed if the
     *         bytes cannot be decoded
     */
    @WhatsAppWebExport(moduleName = "WAWebNonMessageDataRequestHandler", exports = "m", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncdSnapshotRecovery decodeRecoverySnapshot(
            PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse response
    ) {
        var snapshotBytes = response.collectionSnapshot()
                .orElseThrow(() -> new NoSuchElementException("Missing snapshot"));
        try {
            if (response.isCompressed()) {
                try (var protobufStream = ProtobufInputStream.fromStream(new GZIPInputStream(new ByteArrayInputStream(snapshotBytes)))) {
                    return SyncdSnapshotRecoverySpec.decode(protobufStream);
                }
            } else {
                return SyncdSnapshotRecoverySpec.decode(snapshotBytes);
            }
        } catch (Exception e) {
            throw new WhatsAppWebAppStateSyncException.ExternalDecodeFailed(e);
        }
    }

    /**
     * Builds and sends the snapshot fatal-recovery peer-data operation
     * request to the primary device.
     *
     * <p>The request is wrapped in a {@link ProtocolMessage} of type
     * {@link ProtocolMessage.Type#PEER_DATA_OPERATION_REQUEST_MESSAGE} and a
     * {@link com.github.auties00.cobalt.model.message.MessageContainer} before
     * being sent via {@link WhatsAppClient#sendPeerMessage}. Per-request
     * telemetry is committed through {@link WamService#commit} as a
     * {@link com.github.auties00.cobalt.wam.event.NonMessagePeerDataRequestEvent}
     * keyed on the outbound peer-message id.
     *
     * @implNote This implementation performs the message wrapping inline
     * because Cobalt has no shared key-message send helper.
     *
     * @param collectionName the collection to recover
     * @throws IllegalStateException if no primary device JID can be resolved
     *                               or the local user's JID is not set
     */
    @WhatsAppWebExport(moduleName = "WAWebSendNonMessageDataRequest", exports = "sendPeerDataOperationRequest", adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendRecoveryRequest(SyncPatchType collectionName) {
        var primaryDevice = getPrimaryDevice();
        if (primaryDevice == null) {
            throw new IllegalStateException("No primary device available for snapshot recovery");
        }

        var recoveryRequest = new PeerDataOperationRequestMessageSyncDCollectionFatalRecoveryRequestBuilder()
                .collectionName(collectionName.toString())
                .timestamp(Instant.now())
                .build();

        var requestMessage = new PeerDataOperationRequestMessageBuilder()
                .peerDataOperationRequestType(PeerDataOperationRequestType.COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY)
                .syncdCollectionFatalRecoveryRequest(recoveryRequest)
                .build();

        var protocolMessage = new ProtocolMessageBuilder()
                .type(ProtocolMessage.Type.PEER_DATA_OPERATION_REQUEST_MESSAGE)
                .peerDataOperationRequestMessage(requestMessage)
                .build();

        var messageContainer = new MessageContainerBuilder()
                .protocolMessage(protocolMessage)
                .build();

        LOGGER.info("Sending snapshot recovery request for collection " + collectionName + " to primary device " + primaryDevice);
        var self = client.store().jid().orElse(null);
        if (self == null) {
            throw new IllegalStateException("Own JID not available for snapshot recovery request");
        }

        var peerMessageId = MessageIdGenerator.generate(MessageIdVersion.V2, self);
        var messageKey = new MessageKeyBuilder()
                .id(peerMessageId)
                .parentJid(self)
                .fromMe(true)
                .senderJid(self)
                .build();
        var messageInfo = new ChatMessageInfoBuilder()
                .key(messageKey)
                .message(messageContainer)
                .build();
        wamService.commit(new NonMessagePeerDataRequestEventBuilder()
                .peerDataRequestCount(1)
                .peerDataRequestType(PeerDataRequestType.SYNCD_SNAPSHOT_RECOVERY)
                .peerDataRequestSessionId(peerMessageId)
                .build());
        client.sendPeerMessage(primaryDevice, messageInfo);
    }

    /**
     * Builds the device-zero JID of the primary device.
     *
     * <p>Resolves the destination of the recovery peer-data operation request
     * for {@link #sendRecoveryRequest(SyncPatchType)}.
     *
     * @return the primary device JID, or {@code null} when the local user's
     *         JID has not been set yet
     */
    @WhatsAppWebExport(moduleName = "WAWebSendNonMessageDataRequest", exports = "D", adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid getPrimaryDevice() {
        var myJid = client.store().jid().orElse(null);
        if (myJid == null) {
            return null;
        }

        return Jid.of(myJid.user(), myJid.server(), 0, 0);
    }
}
