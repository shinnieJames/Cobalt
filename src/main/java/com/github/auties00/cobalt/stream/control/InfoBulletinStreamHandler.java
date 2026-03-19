package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.util.LinkedHashSet;

public final class InfoBulletinStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER =
            System.getLogger(InfoBulletinStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public InfoBulletinStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        try {
            for (var child : node.children()) {
                switch (child.description()) {
                    case "dirty" -> {
                        handleDirty(node);
                        return;
                    }
                    case "routing" -> {
                        handleRouting(child);
                        return;
                    }
                    case "offline" -> {
                        handleOffline(child);
                        return;
                    }
                    case "offline_preview" -> {
                        handleOfflinePreview(child);
                        return;
                    }
                    case "offline_priority_complete" -> {
                        handleOfflinePriorityComplete();
                        return;
                    }
                    case "tos" -> {
                        handleTos(child);
                        return;
                    }
                    case "thread_meta" -> {
                        handleThreadMeta(child);
                        return;
                    }
                    case "client_expiration" -> {
                        handleClientExpiration(child);
                        return;
                    }
                    default -> {
                    }
                }
            }

            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported info bulletin {0}",
                    node.getAttributeAsString("id", "[missing-id]"));
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle info bulletin {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        }
    }

    /**
     * Handles dirty bit notifications by inspecting each dirty node's type
     * and syncing the appropriate syncd collections.
     *
     * <p>Per WhatsApp Web, the {@code syncd_app_state} dirty type triggers a
     * sync for all collection types, while other dirty types only sync
     * collections explicitly listed as children of the dirty node.
     *
     * @param node the info bulletin node containing dirty children
     * @implNote WAWebHandleDirtyBits.handleDirtyBits, WAWebHandleDirtyBits.p (syncd_app_state handler)
     */
    private void handleDirty(Node node) {
        var collectionsToSync = new LinkedHashSet<SyncPatchType>(); // WAWebHandleDirtyBits.handleDirtyBits (aggregated collections)

        for (var dirtyNode : node.getChildren("dirty")) { // WAWebHandleInfoBulletin (parser: forEachChildWithTag DIRTY)
            var type = dirtyNode.getAttributeAsString("type", null); // WAWebHandleInfoBulletin (parser: e.attrString("type"))
            if ("account_sync".equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits (SUPPORTED_DIRTY_TYPE.account_sync)
                whatsapp.store().setSyncedContacts(false); // ADAPTED: WAWebHandleDirtyBits.c (account sync handler)
                whatsapp.store().setSyncedStatus(false); // ADAPTED: WAWebHandleDirtyBits.c (account sync handler)
            } else if ("syncd_app_state".equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits (SUPPORTED_DIRTY_TYPE.syncd_app_state)
                java.util.Collections.addAll(collectionsToSync, SyncPatchType.values()); // WAWebHandleDirtyBits.p: markCollectionsForSync(ALL collections)
            }

            for (var child : dirtyNode.children()) { // WAWebHandleInfoBulletin (parser: e.mapChildren for protocols)
                SyncPatchType.of(child.description()).ifPresent(collectionsToSync::add); // WAWebHandleDirtyBits.handleDirtyBits (collection name matching)
            }
        }

        if (!collectionsToSync.isEmpty()) { // WAWebHandleDirtyBits.handleDirtyBits (markCollectionsForSync call)
            whatsapp.store().setSyncedWebAppState(false); // ADAPTED: Cobalt store flag
            whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new)); // WAWebHandleDirtyBits.p -> WAWebSyncd.markCollectionsForSync
        }

        whatsapp.retryOrphanMutations(); // ADAPTED: retry orphan mutations on dirty (Cobalt-specific)
    }

    private void handleRouting(Node routingNode) {
        var info = routingNode.getChild("routing_info")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        var domain = routingNode.getChild("dns_domain")
                .flatMap(Node::toContentString)
                .orElse(null);
        whatsapp.store().setRoutingInfo(info);
        whatsapp.store().setRoutingDomain(domain);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received routing bulletin with {0} routing_info bytes", info == null ? 0 : info.length);
    }

    private void handleOffline(Node offlineNode) {
        var count = offlineNode.getAttributeAsInt("count", 0);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline bulletin with count={0}", count);

        if (count == 0) {
            whatsapp.retryOrphanMutations();
        }
    }

    private void handleOfflinePreview(Node previewNode) {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline preview bulletin message={0} receipt={1} notification={2} call={3}",
                previewNode.getAttributeAsInt("message", 0),
                previewNode.getAttributeAsInt("receipt", 0),
                previewNode.getAttributeAsInt("notification", 0),
                previewNode.getAttributeAsInt("call", 0));
    }

    private void handleOfflinePriorityComplete() {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline_priority_complete bulletin");
        whatsapp.retryOrphanMutations();
    }

    private void handleTos(Node tosNode) {
        var notices = tosNode.getChildren("notice").stream()
                .map(entry -> entry.getAttributeAsString("id", null))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        whatsapp.store().setTosNoticeIds(notices);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received TOS bulletin notices={0}", notices);
    }

    private void handleClientExpiration(Node clientExpirationNode) {
        var expiration = clientExpirationNode.getAttributeAsLong("t", (Long) null);
        if (expiration != null) {
            whatsapp.store().setClientExpiration(java.time.Instant.ofEpochSecond(expiration));
        }
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received client expiration bulletin expiring at {0}",
                expiration != null ? java.time.Instant.ofEpochSecond(expiration) : "[missing]");
    }

    private void handleThreadMeta(Node threadMetaNode) {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received thread_meta bulletin {0}",
                threadMetaNode);
    }
}
