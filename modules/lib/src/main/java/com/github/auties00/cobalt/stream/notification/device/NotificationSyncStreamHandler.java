package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.stream.control.OfflineNotificationsReporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles server sync notifications by parsing the changed collection names,
 * triggering a pull sync for recognized collections, and acknowledging the
 * notification back to the server.
 *
 * <p>The WA Web source module {@code WAWebHandleServerSyncNotification} uses a
 * {@code WADeprecatedWapParser} to extract {@code changedCollections} (a
 * {@code Map<string, int>} of collection name to version), a {@code stanzaId},
 * and an {@code offline} boolean from the incoming notification stanza. The
 * parsed result is then processed by the helper function {@code _} which
 * casts collection names to known {@code CollectionName} enum values, filters
 * to critical collections during bootstrap, calls
 * {@code WAWebSyncd.markCollectionsForSync}, and returns an ack promise.
 *
 */
@WhatsAppWebModule(moduleName = "WAWebHandleServerSyncNotification")
public final class NotificationSyncStreamHandler implements SocketStream.Handler {
    /**
     * Logger for diagnostic output from this handler.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationSyncStreamHandler.class.getName());

    /**
     * The WhatsApp client used to access the store and send nodes.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The shared reporter used to accumulate per-collection offline {@code server_sync} notification counts for the {@code MdAppStateOfflineNotifications} WAM event.
     */
    private final OfflineNotificationsReporter offlineNotificationsReporter;

    /**
     * Constructs a new notification sync stream handler.
     *
     * @param whatsapp                     the WhatsApp client instance
     * @param offlineNotificationsReporter the shared reporter used to record per-collection offline server-sync observations for later WAM flushing
     */
    public NotificationSyncStreamHandler(WhatsAppClient whatsapp, OfflineNotificationsReporter offlineNotificationsReporter) {
        this.whatsapp = whatsapp;
        this.offlineNotificationsReporter = offlineNotificationsReporter;
    }

    /**
     * Handles an incoming server sync notification by extracting the changed
     * collection names, casting them to {@link SyncPatchType} values, and
     * triggering a pull sync for recognized collections. Unknown collection
     * names are logged and skipped. An acknowledgement stanza is always sent
     * back to the server regardless of success or failure.
     *
     * <p>Per WhatsApp Web, the parser ({@code WADeprecatedWapParser}) first asserts
     * the tag is {@code "notification"} and throws a parse error if no
     * {@code <collection>} children exist. If parsing succeeds, the helper
     * function {@code _} iterates over the parsed {@code changedCollections}
     * map, casts each name to a {@code CollectionName} enum value, and
     * collects up to 3 unknown names for a warning log. During bootstrap
     * (critical data sync in process), only critical collections
     * ({@code critical_block}, {@code critical_unblock_low}) are processed.
     *
     * @param node the incoming notification node
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
     * Processes the parsed notification data: validates the {@code from}
     * attribute, iterates collection children to build a map of recognized
     * {@link SyncPatchType} to version, logs unknown names, optionally filters
     * to critical collections during bootstrap, and triggers a pull sync.
     *
     * <p>Per WhatsApp Web, the {@code from} attribute is validated against
     * {@code S_WHATSAPP_NET} and an error is logged if they do not match.
     * When the notification carries the {@code offline} attribute, each
     * affected collection also bumps the shared
     * {@link OfflineNotificationsReporter}, which is flushed into a
     * {@code MdAppStateOfflineNotifications} WAM event by the info
     * bulletin handler once the offline backlog window ends.
     *
     * @param node the notification node to process
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

        // Records per-collection observation counts when offline so the info-bulletin offline handler can emit MdAppStateOfflineNotifications with the accumulated redundantCount once the offline backlog window ends.
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
     * Checks whether a critical data sync (bootstrap) is currently in process.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess}:
     * returns {@code true} when the state machine variable {@code $3} equals
     * {@code ce.InProcess}, meaning the critical sync phase is active. In Cobalt,
     * this is approximated by checking whether the {@code critical_block}
     * collection has not yet been bootstrapped, which yields the same behavioral
     * result.
     *
     * @return {@code true} if critical data sync is still in process
     */
    private boolean isCriticalDataSyncInProcess() {
        return !whatsapp.store()
                .findWebAppState(SyncPatchType.CRITICAL_BLOCK)
                .bootstrapped();
    }

    /**
     * Sends an acknowledgement for the server sync notification.
     *
     * <p>Per WhatsApp Web {@code WAWebHandleServerSyncNotification._}: the ack stanza
     * is constructed as {@code wap("ack", {id: CUSTOM_STRING(e.stanzaId),
     * class: "notification", type: "server_sync", to: S_WHATSAPP_NET})} and
     * returned as a resolved promise. In Cobalt, this is sent as a fire-and-forget
     * node via {@link WhatsAppClient#sendNodeWithNoResponse(Node)}.
     *
     * @param node the notification node to acknowledge
     */
    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        if (stanzaId == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification")
                .attribute("type", "server_sync")
                .attribute("to", JidServer.user())
                .build());
    }
}
