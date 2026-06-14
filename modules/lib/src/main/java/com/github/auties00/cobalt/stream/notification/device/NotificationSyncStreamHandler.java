package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.control.OfflineNotificationsReporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles {@code type="server_sync"} notifications announcing app-state collection updates the
 * client needs to pull.
 *
 * <p>Dispatched by {@link NotificationDeviceDispatcher}. Each notification carries one or more
 * {@code <collection name="..." version="..."/>} children naming the collections whose latest
 * version the server is advertising. While the bootstrap-critical sync phase is still in progress,
 * the handler filters the pull down to the critical collections (see {@link SyncPatchType#isCritical()})
 * so the initial sync does not stall on non-critical collections. Offline-window observations are
 * pushed to {@link OfflineNotificationsReporter} for the {@code MdAppStateOfflineNotifications} WAM
 * event, and the protocol-level ACK is always sent.
 *
 * @implNote This implementation pulls directly via {@link LinkedWhatsAppClient#pullWebAppState(SyncPatchType...)},
 * whereas WA Web routes through a sync marker that batches with other markers; Cobalt's pull is
 * direct because the Cobalt store has no equivalent marker indirection.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleServerSyncNotification")
public final class NotificationSyncStreamHandler extends SocketStreamHandler.Concurrent {
    /**
     * Logs warnings about unknown collection names and errors during sync.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationSyncStreamHandler.class.getName());

    /**
     * Provides store reads and app-state pulls.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Accumulates per-collection observation counts during the offline window for the
     * {@code MdAppStateOfflineNotifications} WAM event.
     */
    private final OfflineNotificationsReporter offlineNotificationsReporter;

    /**
     * Ships the post-processing
     * {@code <ack class="notification" type="server_sync" to="s.whatsapp.net"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * <p>Called once by {@link NotificationDeviceDispatcher}.
     *
     * @param whatsapp                     the {@link LinkedWhatsAppClient}
     * @param offlineNotificationsReporter the {@link OfflineNotificationsReporter}
     * @param ackSender                    the {@link AckSender}
     */
    public NotificationSyncStreamHandler(LinkedWhatsAppClient whatsapp, OfflineNotificationsReporter offlineNotificationsReporter, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.offlineNotificationsReporter = offlineNotificationsReporter;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape, processes the collection list, and always sends the protocol-level
     * ACK.
     *
     * <p>Stanzas that are not a {@code <notification type="server_sync">} return without
     * side-effects. A stanza with no {@code <collection>} child is error-logged and ACKed without
     * further work, matching WA Web's parser which throws before its dispatch helper runs. Otherwise
     * the collection list is processed inside a {@code try} block, any failure is caught and
     * warning-logged, and the ACK is sent in the {@code finally} block.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "server_sync")) {
            return;
        }

        if (!node.hasChild("collection")) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Server sync notification does not contain any collections");
            sendNotificationAck(node);
            return;
        }

        try {
            processNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle server_sync notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    /**
     * Builds the collection-version map, applies the bootstrap filter, triggers the pull, and
     * updates the offline-observation reporter.
     *
     * <p>The {@code from} attribute is asserted against the server-domain JID; a mismatch is
     * error-logged but does not abort processing because the server is the only sender of this
     * stanza. Each {@code <collection>} child is mapped to its {@link SyncPatchType} and version;
     * unknown collection names are collected and warning-logged. When the stanza carries the
     * {@code offline} attribute, every changed collection is reported to
     * {@link OfflineNotificationsReporter}. While {@link #isCriticalDataSyncInProcess()} holds, the
     * pull list is narrowed to critical collections. A non-empty pull list is pulled via
     * {@link LinkedWhatsAppClient#pullWebAppState(SyncPatchType...)}.
     *
     * @implNote This implementation caps the unknown-name warning at three names per stanza,
     * mirroring WA Web's guard which limits log spam during a server-side collection rollout.
     *
     * @param node the {@code <notification>} stanza
     */
    private void processNotification(Node node) {
        var from = node.getAttributeAsString("from", null);
        if (from != null && !from.equals(JidServer.user().toString())) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "handleServerSyncNotification: \"from\" is not domain jid \"s.whatsapp.net\"");
        }

        var changedCollections = new LinkedHashMap<SyncPatchType, Integer>();
        var unknownNames = new ArrayList<String>();
        for (var collectionNode : node.getChildren("collection")) {
            var collectionName = collectionNode.getAttributeAsString("name", null);
            var collectionVersion = collectionNode.getAttributeAsInt("version", 0);
            var collectionType = SyncPatchType.of(collectionName).orElse(null);
            if (collectionType != null) {
                changedCollections.put(collectionType, collectionVersion);
            } else if (unknownNames.size() < 3) {
                unknownNames.add(collectionName);
            }
        }

        if (!unknownNames.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "syncd: {0} unknown collection names in notification => {1}",
                    unknownNames.size(), unknownNames);
        }

        var collectionsToSync = new ArrayList<>(changedCollections.keySet());

        var offline = node.hasAttribute("offline");
        if (offline) {
            for (var collection : collectionsToSync) {
                offlineNotificationsReporter.increment(collection);
            }
        }
        LOGGER.log(System.Logger.Level.INFO,
                "syncd: incoming sync notification for collections\n    {0}",
                changedCollections.entrySet().stream()
                        .map(e -> e.getKey() + " v" + e.getValue())
                        .collect(Collectors.joining("\n    ")));

        if (isCriticalDataSyncInProcess()) {
            collectionsToSync = collectionsToSync.stream()
                    .filter(SyncPatchType::isCritical)
                    .collect(Collectors.toCollection(ArrayList::new));
            LOGGER.log(System.Logger.Level.INFO,
                    "syncd: filtered non critical collections during bootstrap. new collections: {0}",
                    collectionsToSync);
        }

        if (!collectionsToSync.isEmpty()) {
            whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new));
        }
    }

    /**
     * Returns whether the bootstrap-critical sync phase is still in progress.
     *
     * @implNote This implementation approximates WA Web's bootstrap state machine by checking
     * whether the {@link SyncPatchType#CRITICAL_BLOCK} collection has been bootstrapped. WA Web's
     * bootstrap pipeline has additional staging steps not modelled in Cobalt; this approximation
     * produces the same observable behaviour (non-critical collections deferred until critical_block
     * lands).
     *
     * @return {@code true} when the bootstrap is still in progress
     */
    private boolean isCriticalDataSyncInProcess() {
        return !whatsapp.store().syncStore().findWebAppState(SyncPatchType.CRITICAL_BLOCK)
                .bootstrapped();
    }

    /**
     * Sends the {@code <ack class="notification" type="server_sync" to="s.whatsapp.net"/>} stanza.
     *
     * <p>The destination is hard-coded to the server-domain JID.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).to(JidServer.user().toJid()).type("server_sync").send();
    }
}
