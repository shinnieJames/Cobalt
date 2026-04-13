package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

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
 * @implNote WAWebHandleServerSyncNotification.handleServerSyncNotification (f),
 *           WAWebHandleServerSyncNotification._ (helper)
 */
public final class NotificationSyncStreamHandler implements SocketStream.Handler {
    /**
     * Logger for this handler.
     *
     * @implNote WAWebHandleServerSyncNotification (module-level WALogger usage)
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationSyncStreamHandler.class.getName());

    /**
     * The WhatsApp client instance used to access the store and send nodes.
     *
     * @implNote WAWebHandleServerSyncNotification (module-level dependency on WAWap, WAWebSyncd, etc.)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new notification sync stream handler.
     *
     * @param whatsapp the WhatsApp client instance
     * @implNote WAWebHandleServerSyncNotification (module-level dependency injection)
     */
    public NotificationSyncStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
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
     * @implNote WAWebHandleServerSyncNotification.handleServerSyncNotification (f: parser + dispatch),
     *           WAWebHandleServerSyncNotification._ (collection filtering + markCollectionsForSync)
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "server_sync")) { // ADAPTED: WAWebHandleServerSyncNotification.p (assertTag("notification")); dispatcher already checks type
            return;
        }

        if (!node.hasChild("collection")) { // WAWebHandleServerSyncNotification.p (!t.hasChild("collection") -> throw parseError)
            LOGGER.log(System.Logger.Level.ERROR,
                    "Server sync notification does not contain any collections"); // WAWebHandleServerSyncNotification.p
            sendNotificationAck(node); // ADAPTED: always ack even on parse failure (WA Web rejects promise instead)
            return;
        }

        try {
            processNotification(node); // WAWebHandleServerSyncNotification._ (helper function)
        } catch (Throwable throwable) { // ADAPTED: defensive error handling (Java practice; WA Web rejects promise)
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle server_sync notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendNotificationAck(node); // WAWebHandleServerSyncNotification._ (return Promise.resolve(wap("ack", ...)))
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
     * The offline flag triggers WAM telemetry counters (skipped in Cobalt).
     *
     * @param node the notification node to process
     * @implNote WAWebHandleServerSyncNotification._ (main processing logic)
     */
    private void processNotification(Node node) {
        var from = node.getAttributeAsString("from", null); // WAWebHandleServerSyncNotification.p (i = t.attrString("from"))
        if (from != null && !from.equals(JidServer.user().toString())) { // WAWebHandleServerSyncNotification.p (i !== S_WHATSAPP_NET.toString())
            LOGGER.log(System.Logger.Level.ERROR,
                    "handleServerSyncNotification: \"from\" is not domain jid \"s.whatsapp.net\""); // WAWebHandleServerSyncNotification.p
        }

        var changedCollections = new LinkedHashMap<SyncPatchType, Integer>(); // WAWebHandleServerSyncNotification._ (t = new Map)
        var unknownNames = new ArrayList<String>(); // WAWebHandleServerSyncNotification._ (a = [])
        for (var collectionNode : node.getChildren("collection")) { // WAWebHandleServerSyncNotification._ (for(var i of e.changedCollections))
            var collectionName = collectionNode.getAttributeAsString("name", null); // WAWebHandleServerSyncNotification._ (l = i[0])
            var collectionVersion = collectionNode.getAttributeAsInt("version", 0); // WAWebHandleServerSyncNotification._ (d = i[1])
            var collectionType = SyncPatchType.of(collectionName).orElse(null); // WAWebHandleServerSyncNotification._ (p = CollectionName.cast(l))
            if (collectionType != null) { // WAWebHandleServerSyncNotification._ (p != null)
                changedCollections.put(collectionType, collectionVersion); // WAWebHandleServerSyncNotification._ (t.set(p, d))
            } else if (unknownNames.size() < 3) { // WAWebHandleServerSyncNotification._ (a.length < 3 && a.push(l))
                unknownNames.add(collectionName);
            }
        }

        if (!unknownNames.isEmpty()) { // WAWebHandleServerSyncNotification._ (a.length > 0)
            LOGGER.log(System.Logger.Level.WARNING,
                    "syncd: {0} unknown collection names in notification => {1}",
                    unknownNames.size(), unknownNames); // WAWebHandleServerSyncNotification._ (WARN)
        }

        var collectionsToSync = new ArrayList<>(changedCollections.keySet()); // WAWebHandleServerSyncNotification._ (_ = Array.from(t.keys()))

        // WAWebHandleServerSyncNotification._ (e.offline && _.forEach(offlineNotificationsCount)): skipped (WAM telemetry)

        LOGGER.log(System.Logger.Level.INFO, // WAWebHandleServerSyncNotification._ (LOG("syncd: incoming sync notification for collections\n    ..."))
                "syncd: incoming sync notification for collections\n    {0}",
                changedCollections.entrySet().stream()
                        .map(e -> e.getKey() + " v" + e.getValue())
                        .collect(Collectors.joining("\n    ")));

        if (isCriticalDataSyncInProcess()) { // WAWebHandleServerSyncNotification._ (isSyncDCriticalDataSyncInProcess())
            collectionsToSync = collectionsToSync.stream()
                    .filter(SyncPatchType::isCritical) // WAWebHandleServerSyncNotification._ (isCriticalCollection(e))
                    .collect(Collectors.toCollection(ArrayList::new));
            LOGGER.log(System.Logger.Level.INFO, // WAWebHandleServerSyncNotification._ (LOG("syncd: filtered non critical collections..."))
                    "syncd: filtered non critical collections during bootstrap. new collections: {0}",
                    collectionsToSync);
        }

        if (!collectionsToSync.isEmpty()) { // ADAPTED: no-op guard (WA Web calls markCollectionsForSync unconditionally but it's a no-op for empty)
            whatsapp.store().setSyncedWebAppState(false); // ADAPTED: Cobalt store flag to signal pending sync
            whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new)); // ADAPTED: WAWebHandleServerSyncNotification._ -> WAWebSyncd.markCollectionsForSync(_, t) (no version map filtering)
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
     * @implNote WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess
     */
    private boolean isCriticalDataSyncInProcess() {
        return !whatsapp.store() // ADAPTED: WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess (global state machine → per-collection bootstrapped check)
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
     * @implNote WAWebHandleServerSyncNotification._ (return Promise.resolve(wap("ack", {...})))
     */
    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleServerSyncNotification._ (e.stanzaId)
        if (stanzaId == null) { // ADAPTED: defensive null check (Java practice; WA Web parser always extracts id via attrString)
            return;
        }

        whatsapp.sendNodeWithNoResponse(new NodeBuilder() // WAWebHandleServerSyncNotification._ (wap("ack", {...}))
                .description("ack") // WAWebHandleServerSyncNotification._ (tag: "ack")
                .attribute("id", stanzaId) // WAWebHandleServerSyncNotification._ (id: CUSTOM_STRING(e.stanzaId))
                .attribute("class", "notification") // WAWebHandleServerSyncNotification._ (class: "notification")
                .attribute("type", "server_sync") // WAWebHandleServerSyncNotification._ (type: "server_sync")
                .attribute("to", JidServer.user()) // WAWebHandleServerSyncNotification._ (to: S_WHATSAPP_NET)
                .build());
    }
}
