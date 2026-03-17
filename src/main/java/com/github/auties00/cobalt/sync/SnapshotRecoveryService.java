package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
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
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
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
 * Service for handling snapshot recovery when a snapshot MAC validation fails.
 *
 * <p>Per WhatsApp Web {@code WAWebRequestSyncdSnapshotRecovery}: when a snapshot
 * MAC mismatch occurs, the client can request a corrected snapshot from the
 * primary device. This allows recovery from data corruption without requiring
 * a full re-link.
 *
 * <p>The recovery flow is:
 * <ol>
 *   <li>Check if recovery should be attempted (AB prop gating)</li>
 *   <li>Send a {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY} peer data
 *       operation request to the primary device</li>
 *   <li>Wait for the response (with timeout)</li>
 *   <li>Process the recovered snapshot data</li>
 * </ol>
 *
 * <p>Recovery is gated by:
 * <ul>
 *   <li>AB prop {@code enable_peer_snapshot_recovery} must be enabled</li>
 *   <li>Collection must not be {@code CRITICAL_BLOCK}</li>
 * </ul>
 */
public final class SnapshotRecoveryService {
    private static final Logger LOGGER = Logger.getLogger(SnapshotRecoveryService.class.getName());
    private static final long RECOVERY_TIMEOUT_MS = 60_000;

    private final WhatsAppClient client;
    private final ABPropsService abPropsService;
    private final Map<SyncPatchType, CompletableFuture<PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse>> pendingRecoveries;
    private final Semaphore recoverySemaphore;

    /**
     * Creates a new snapshot recovery service.
     *
     * @param client         the WhatsApp client for sending messages
     * @param abPropsService the AB props service for gating checks
     */
    public SnapshotRecoveryService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = client;
        this.abPropsService = abPropsService;
        this.pendingRecoveries = new ConcurrentHashMap<>();
        this.recoverySemaphore = new Semaphore(1);
    }

    /**
     * Checks whether snapshot recovery should be attempted for the given collection.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdSnapshotRecoveryGatingUtils.shouldPreformSnapshotRecovery}:
     * recovery is only attempted when all gating conditions are met:
     * <ol>
     *   <li>Primary device supports syncd recovery</li>
     *   <li>AB prop {@code enable_peer_snapshot_recovery} is enabled</li>
     *   <li>Collection is not {@code CRITICAL_BLOCK}</li>
     *   <li>Mutation count does not exceed
     *       {@code snapshot_recovery_max_mutations_count_allowed}</li>
     * </ol>
     *
     * @param collectionName the collection that failed snapshot MAC validation
     * @param mutationCount  the number of mutations in the snapshot
     * @return {@code true} if recovery should be attempted
     */
    public boolean shouldAttemptRecovery(SyncPatchType collectionName, int mutationCount) {
        // Primary device must support syncd recovery
        if (!client.store().primaryDeviceSupportsSyncdRecovery()) {
            return false;
        }

        // Check AB prop gating
        if (!abPropsService.getBool(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY)) {
            return false;
        }

        // CriticalBlock is never recoverable via peer recovery
        if (collectionName == SyncPatchType.CRITICAL_BLOCK) {
            return false;
        }

        // Check mutation count against AB prop threshold
        var maxMutations = abPropsService.getInt(ABProp.SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED);
        return mutationCount <= maxMutations;
    }

    /**
     * Requests a snapshot recovery from the primary device.
     *
     * <p>Sends a {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY} peer data
     * operation request and waits for the response with a timeout.
     *
     * <p>Per WhatsApp Web behavior, recovery requests are serialized globally
     * to prevent multiple concurrent recoveries across different collections.
     *
     * @param collectionName the collection to recover
     * @return the recovery response, or {@code null} if recovery failed or timed out
     */
    public PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse requestRecovery(SyncPatchType collectionName) {
        try {
            // Serialize recovery requests across all collections
            if (!recoverySemaphore.tryAcquire(RECOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOGGER.warning("Snapshot recovery timed out waiting for concurrent recovery to complete");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        try {
            // Cancel any existing recovery for this collection
            var existingFuture = pendingRecoveries.remove(collectionName);
            if (existingFuture != null) {
                existingFuture.cancel(false);
            }

            // Create a future for the response
            var responseFuture = new CompletableFuture<PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse>();
            pendingRecoveries.put(collectionName, responseFuture);

            // Build and send the recovery request
            sendRecoveryRequest(collectionName);

            // Wait for response with timeout
            return responseFuture.get(RECOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warning("Snapshot recovery failed for collection " + collectionName + ": " + e.getMessage());
            return null;
        } finally {
            pendingRecoveries.remove(collectionName);
            recoverySemaphore.release();
        }
    }

    /**
     * Resolves a pending recovery request with the response from the primary device.
     *
     * <p>This method should be called by the protocol message handler when a
     * {@code PeerDataOperationRequestResponseMessage} with type
     * {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY} is received.
     *
     * @param collectionName the collection name from the response
     * @param response       the recovery response data
     */
    public void resolveRecovery(
            SyncPatchType collectionName,
            PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse response
    ) {
        var future = pendingRecoveries.get(collectionName);
        if (future != null) {
            future.complete(response);
        } else {
            LOGGER.fine("Received snapshot recovery response for " + collectionName + " but no pending request found");
        }
    }

    /**
     * Decodes the snapshot recovery from a recovery response, transparently
     * handling gzip-compressed responses by streaming through a
     * {@link GZIPInputStream}.
     *
     * <p>Per WhatsApp Web behavior, the {@code collectionSnapshot} bytes
     * encode a {@link SyncdSnapshotRecovery} protobuf containing plaintext
     * mutation records, the primary device's LT-Hash, and the version.
     *
     * @param response the recovery response
     * @return the decoded snapshot recovery
     */
    public SyncdSnapshotRecovery decodeRecoverySnapshot(
            PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse response
    ) {
        var snapshotBytes = response.collectionSnapshot()
                .orElseThrow(() -> new NoSuchElementException("Missing snapshot"));;
        if (response.isCompressed()) {
            try (var protobufStream = ProtobufInputStream.fromStream(new GZIPInputStream(new ByteArrayInputStream(snapshotBytes)))) {
                return SyncdSnapshotRecoverySpec.decode(protobufStream);
            } catch (Exception e) {
                throw new WhatsAppWebAppStateSyncException.ExternalDecodeFailed(e);
            }
        } else {
            return SyncdSnapshotRecoverySpec.decode(snapshotBytes);
        }
    }

    /**
     * Sends the recovery request to the primary device.
     */
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

        var messageKey = new MessageKeyBuilder()
                .id(MessageIdGenerator.generate(MessageIdVersion.V2, self))
                .parentJid(self)
                .fromMe(true)
                .senderJid(self)
                .build();
        var messageInfo = new ChatMessageInfoBuilder()
                .key(messageKey)
                .message(messageContainer)
                .timestamp(Instant.now())
                .senderJid(self)
                .build();
        client.sendPeerMessage(primaryDevice, messageInfo);
    }

    /**
     * Gets the primary device JID (device 0).
     */
    private Jid getPrimaryDevice() {
        var myJid = client.store().jid().orElse(null);
        if (myJid == null) {
            return null;
        }

        return Jid.of(myJid.user(), myJid.server(), 0, 0);
    }
}
