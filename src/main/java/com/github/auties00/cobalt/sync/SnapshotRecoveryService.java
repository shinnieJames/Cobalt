package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.peer.*;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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
    }

    /**
     * Checks whether snapshot recovery should be attempted for the given collection.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdSnapshotRecoveryGatingUtils.shouldPreformSnapshotRecovery}:
     * recovery is only attempted when gating conditions are met.
     *
     * @param collectionName the collection that failed snapshot MAC validation
     * @return {@code true} if recovery should be attempted
     */
    public boolean shouldAttemptRecovery(SyncPatchType collectionName) {
        // CriticalBlock is never recoverable via peer recovery
        if (collectionName == SyncPatchType.CRITICAL_BLOCK) {
            return false;
        }

        // Check AB prop gating
        return abPropsService.getBool(ABProp.ENABLE_PEER_SNAPSHOT_RECOVERY);
    }

    /**
     * Requests a snapshot recovery from the primary device.
     *
     * <p>Sends a {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY} peer data
     * operation request and waits for the response with a timeout.
     *
     * @param collectionName the collection to recover
     * @return the recovery response, or {@code null} if recovery failed or timed out
     */
    public PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse requestRecovery(SyncPatchType collectionName) {
        // Cancel any existing recovery for this collection
        var existingFuture = pendingRecoveries.remove(collectionName);
        if (existingFuture != null) {
            existingFuture.cancel(false);
        }

        // Create a future for the response
        var responseFuture = new CompletableFuture<PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse>();
        pendingRecoveries.put(collectionName, responseFuture);

        try {
            // Build and send the recovery request
            sendRecoveryRequest(collectionName);

            // Wait for response with timeout
            return responseFuture.get(RECOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warning("Snapshot recovery failed for collection " + collectionName + ": " + e.getMessage());
            return null;
        } finally {
            pendingRecoveries.remove(collectionName);
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
     * Sends the recovery request to the primary device.
     */
    private void sendRecoveryRequest(SyncPatchType collectionName) {
        var primaryDevice = getPrimaryDevice();
        if (primaryDevice == null) {
            throw new IllegalStateException("No primary device available for snapshot recovery");
        }

        var recoveryRequest = new PeerDataOperationRequestMessageSyncDCollectionFatalRecoveryRequestBuilder()
                .collectionName(collectionName.name())
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
        client.sendMessage(primaryDevice, messageContainer);
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
