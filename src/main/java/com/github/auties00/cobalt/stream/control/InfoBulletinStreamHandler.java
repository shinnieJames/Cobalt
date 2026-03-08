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

    private void handleDirty(Node node) {
        var collectionsToSync = new LinkedHashSet<SyncPatchType>();

        for (var dirtyNode : node.getChildren("dirty")) {
            var type = dirtyNode.getAttributeAsString("type", null);
            if ("account_sync".equals(type)) {
                whatsapp.store().setSyncedContacts(false);
                whatsapp.store().setSyncedStatus(false);
            }

            for (var child : dirtyNode.children()) {
                SyncPatchType.of(child.description()).ifPresent(collectionsToSync::add);
            }
        }

        if (!collectionsToSync.isEmpty()) {
            whatsapp.store().setSyncedWebAppState(false);
            whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new));
        }

        // Dirty bulletins often arrive when remote mutations or contact changes land.
        whatsapp.retryOrphanMutations();
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
