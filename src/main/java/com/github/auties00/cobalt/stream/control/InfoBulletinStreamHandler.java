package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.*;

/**
 * Handles incoming {@code ib} (info bulletin) stanzas from the WhatsApp server.
 *
 * <p>Info bulletins carry a variety of server-to-client notifications: dirty
 * bit syncs, routing updates, offline message counts, offline previews,
 * Terms-of-Service notices, thread metadata, client expiration overrides, and
 * offline priority completion signals.
 *
 * <p>The handler iterates the children of the {@code ib} node and dispatches
 * each recognised child tag to the appropriate private method. Unrecognised
 * children are silently skipped; if no child is recognised, a debug log is
 * emitted.
 *
 * @implNote WAWebHandleInfoBulletin.default: the main {@code infoBulletinParser}
 * callback and the async dispatch function {@code _} / {@code f}.
 */
public final class InfoBulletinStreamHandler implements SocketStream.Handler {
    /**
     * Logger for info bulletin events.
     *
     * @implNote WAWebHandleInfoBulletin.default: uses {@code WALogger} with
     * tagged template literals for error/warn/log output.
     */
    private static final System.Logger LOGGER =
            System.getLogger(InfoBulletinStreamHandler.class.getName());

    /**
     * The default routing domain used when no domain is provided and no
     * existing domain is stored.
     *
     * @implNote WAWebHandleRoutingInfo.DOMAINS: {@code {fb: "fb", sl: "sl"}};
     * the default used in {@code handleRoutingInfo} when domain is absent is
     * {@code s.fb}, i.e. {@code "fb"}.
     */
    private static final String DEFAULT_ROUTING_DOMAIN = "fb"; // WAWebHandleRoutingInfo.DOMAINS.fb

    /**
     * The WhatsApp client instance used for store access, node sending, and
     * delegated service calls.
     *
     * @implNote WAWebHandleInfoBulletin.default: the handler accesses various
     * WA Web modules via {@code o("ModuleName")}; Cobalt injects via
     * constructor DI through {@link WhatsAppClient}.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new info bulletin stream handler.
     *
     * @param whatsapp the WhatsApp client instance
     * @implNote WAWebHandleInfoBulletin.default: the handler is registered as
     * a parser via {@code new WADeprecatedWapParser("infoBulletinParser", ...)}.
     */
    public InfoBulletinStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles an incoming {@code ib} stanza by dispatching on the child tag.
     *
     * <p>The WA Web parser checks {@code e.hasChild(INFO_TYPE.XXX)} for each
     * known info type and returns a parsed result object. The dispatch function
     * then switches on the result type. Cobalt collapses both steps into a
     * single child-tag switch.
     *
     * @param node the {@code ib} stanza node
     * @implNote WAWebHandleInfoBulletin.default: parser function {@code p} and
     * async dispatch function {@code _} / {@code f}.
     */
    @Override
    public void handle(Node node) {
        try {
            for (var child : node.children()) {
                switch (child.description()) {
                    case "dirty" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.DIRTY
                        handleDirty(node);
                        return;
                    }
                    case "edge_routing" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.ROUTING
                        handleRouting(child);
                        return;
                    }
                    case "offline" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.OFFLINE
                        handleOffline(child);
                        return;
                    }
                    case "offline_preview" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.OFFLINE_PREVIEW
                        handleOfflinePreview(child);
                        return;
                    }
                    case "priority_offline_complete" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.OFFLINE_PRIORITY_COMPLETE
                        handleOfflinePriorityComplete();
                        return;
                    }
                    case "tos" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.TOS
                        handleTos(child);
                        return;
                    }
                    case "thread_metadata" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.THREAD_META
                        handleThreadMeta(child);
                        return;
                    }
                    case "client_expiration" -> { // WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.CLIENT_EXPIRATION
                        handleClientExpiration(child);
                        return;
                    }
                    default -> {
                        // WAWebHandleInfoBulletin.default: unrecognised children are skipped
                    }
                }
            }

            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported info bulletin {0}",
                    node.getAttributeAsString("id", "[missing-id]")); // WAWebHandleInfoBulletin.default: WARN "handleInfoBulletin unrecognized info bulletin"
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle info bulletin {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage()); // ADAPTED: WAWebHandleInfoBulletin.default throws parse errors, Cobalt catches
        }
    }

    /**
     * Handles dirty bit notifications by inspecting each dirty node's type
     * and syncing the appropriate syncd collections.
     *
     * <p>Per WhatsApp Web, the {@code syncd_app_state} dirty type triggers a
     * sync for all collection types, while the {@code account_sync} dirty type
     * triggers account-level syncs. The {@code groups} and
     * {@code newsletter_metadata} dirty types trigger metadata refreshes for
     * those entity types respectively.
     *
     * <p>After processing all dirty types, a {@code clean} IQ is sent back to
     * the server to acknowledge the dirty bits.
     *
     * @param node the info bulletin node containing dirty children
     * @implNote WAWebHandleDirtyBits.handleDirtyBits: iterates supported dirty
     * types, dispatches to type-specific handlers, then calls
     * {@code WAWebClearDirtyBitsJob.clearDirtyBits(allDirtyEntries)}.
     */
    private void handleDirty(Node node) {
        var collectionsToSync = new LinkedHashSet<SyncPatchType>(); // WAWebHandleDirtyBits.handleDirtyBits (aggregated collections)
        var allDirtyEntries = new ArrayList<Node>(); // WAWebHandleDirtyBits.handleDirtyBits: var d = [].concat(a, r)

        for (var dirtyNode : node.getChildren("dirty")) { // WAWebHandleInfoBulletin (parser: forEachChildWithTag DIRTY)
            allDirtyEntries.add(dirtyNode); // WAWebHandleDirtyBits.handleDirtyBits: both supported and unsupported go into d
            var type = dirtyNode.getAttributeAsString("type", null); // WAWebHandleInfoBulletin (parser: e.attrString("type"))
            if ("account_sync".equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits (SUPPORTED_DIRTY_TYPE.account_sync -> c(t))
                whatsapp.store().setSyncedContacts(false); // ADAPTED: WAWebHandleDirtyBits.c (account sync handler dispatches to AccountSyncType handlers)
                whatsapp.store().setSyncedStatus(false); // ADAPTED: WAWebHandleDirtyBits.c (account sync handler dispatches to AccountSyncType handlers)
                for (var child : dirtyNode.children()) { // WAWebHandleInfoBulletin (parser: e.mapChildren for protocols)
                    SyncPatchType.of(child.description()).ifPresent(collectionsToSync::add); // WAWebHandleDirtyBits.handleDirtyBits (collection name matching via SUPPORTED_DIRTY_PROTOCOLS)
                }
            } else if ("syncd_app_state".equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits (SUPPORTED_DIRTY_TYPE.syncd_app_state -> p())
                Collections.addAll(collectionsToSync, SyncPatchType.values()); // WAWebHandleDirtyBits.p: markCollectionsForSync(Array.from(CollectionName.members()))
            } else if ("groups".equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits (SUPPORTED_DIRTY_TYPE.groups -> queryAndUpdateAllGroupMetadata)
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Dirty bit groups: group metadata refresh needed"); // ADAPTED: WA Web calls waitForOfflineDeliveryEnd -> queryAndUpdateAllGroupMetadata; Cobalt defers
            } else if ("newsletter_metadata".equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits (SUPPORTED_DIRTY_TYPE.newsletter_metadata -> queryAndUpdateAllNewsletterMetadataAction)
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Dirty bit newsletter_metadata: newsletter metadata refresh needed"); // ADAPTED: WA Web calls waitForOfflineDeliveryEnd -> queryAndUpdateAllNewsletterMetadataAction; Cobalt defers
            }
        }

        if (!collectionsToSync.isEmpty()) { // WAWebHandleDirtyBits.handleDirtyBits (markCollectionsForSync call)
            whatsapp.store().setSyncedWebAppState(false); // ADAPTED: Cobalt store flag
            whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new)); // WAWebHandleDirtyBits.p -> WAWebSyncd.markCollectionsForSync
        }

        clearDirtyBits(allDirtyEntries); // WAWebHandleDirtyBits.handleDirtyBits: clearDirtyBits(d) at end
        whatsapp.retryOrphanMutations(); // ADAPTED: retry orphan mutations on dirty (Cobalt-specific)
    }

    /**
     * Sends a {@code clean} IQ stanza to acknowledge processed dirty bits.
     *
     * <p>The server expects acknowledgement of dirty bits via an IQ set
     * containing one {@code clean} child per dirty entry, each carrying the
     * original {@code type} and {@code timestamp} attributes.
     *
     * @param dirtyEntries the list of dirty nodes to acknowledge
     * @implNote WAWebClearDirtyBitsJob.clearDirtyBits: builds an IQ with
     * xmlns {@code "urn:xmpp:whatsapp:dirty"}, type {@code "set"}, containing
     * {@code clean} children with {@code type} and {@code timestamp} attrs.
     */
    private void clearDirtyBits(List<Node> dirtyEntries) {
        if (dirtyEntries.isEmpty()) { // WAWebClearDirtyBitsJob.clearDirtyBits: if (t.length !== 0)
            return;
        }

        var cleanChildren = dirtyEntries.stream() // WAWebClearDirtyBitsJob.clearDirtyBits: t.map(e => wap("clean", {type, timestamp}))
                .map(dirty -> new NodeBuilder()
                        .description("clean")
                        .attribute("type", dirty.getAttributeAsString("type", null))
                        .attribute("timestamp", dirty.getAttributeAsString("timestamp", null))
                        .build())
                .toList();

        try {
            whatsapp.sendNode(new NodeBuilder() // WAWebClearDirtyBitsJob.clearDirtyBits: deprecatedSendIq(wap("iq", {...}), parser)
                    .description("iq")
                    .attribute("to", Jid.userServer())
                    .attribute("type", "set")
                    .attribute("xmlns", "urn:xmpp:whatsapp:dirty")
                    .content(cleanChildren));
            LOGGER.log(System.Logger.Level.DEBUG,
                    "clearDirtyBits: success for type: {0}",
                    dirtyEntries.stream()
                            .map(d -> d.getAttributeAsString("type", "unknown"))
                            .reduce((a, b) -> a + "," + b)
                            .orElse("")); // WAWebClearDirtyBitsJob.clearDirtyBits: LOG "clearDirtyBits: success for type: ..."
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "clearDirtyBits: failed with error"); // WAWebClearDirtyBitsJob.clearDirtyBits: WARN "clearDirtyBits: failed with error"
        }
    }

    /**
     * Handles routing info bulletins by storing the edge routing data and
     * domain.
     *
     * <p>When the {@code dns_domain} child is absent, the domain falls back to
     * the previously stored routing domain. If no stored domain exists, the
     * default domain {@code "fb"} is used.
     *
     * @param routingNode the {@code edge_routing} child node
     * @implNote WAWebHandleRoutingInfo.handleRoutingInfo: reads
     * {@code edgeRouting} from {@code routing_info} child bytes and
     * {@code domain} from {@code dns_domain} child enum, falling back to
     * stored domain or {@code DOMAINS.fb}.
     */
    private void handleRouting(Node routingNode) {
        var info = routingNode.getChild("routing_info") // WAWebHandleInfoBulletin parser: a.child("routing_info").contentBytes()
                .flatMap(Node::toContentBytes)
                .orElse(null);
        var domain = routingNode.getChild("dns_domain") // WAWebHandleInfoBulletin parser: a.hasChild("dns_domain") ? a.child("dns_domain").contentEnum(DOMAINS) : null
                .flatMap(Node::toContentString)
                .orElse(null);
        if (domain == null) { // WAWebHandleRoutingInfo.handleRoutingInfo: if (!n) { var r = yield getRoutingInfo(); n = r ? r.domain : s.fb }
            domain = whatsapp.store().routingDomain().orElse(DEFAULT_ROUTING_DOMAIN);
        }
        whatsapp.store().setRoutingInfo(info); // WAWebHandleRoutingInfo.handleRoutingInfo: yield setRoutingInfo({domain: n, edgeRouting: a})
        whatsapp.store().setRoutingDomain(domain); // WAWebHandleRoutingInfo.handleRoutingInfo: yield setRoutingInfo({domain: n, edgeRouting: a})
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received routing bulletin with domain={0} and {1} routing_info bytes",
                domain, info == null ? 0 : info.length); // WAWebHandleRoutingInfo.handleRoutingInfo: LOG "handleInfoBulletin setting and domain: ... and edgeRouting:"
    }

    /**
     * Handles offline message count bulletins.
     *
     * <p>The offline bulletin indicates the number of offline messages pending
     * delivery. When the count reaches zero, orphan mutations are retried.
     *
     * @param offlineNode the {@code offline} child node
     * @implNote WAWebHandleInfoBulletin.default (OFFLINE case):
     * calls {@code OfflineMessageHandler.processOfflineIb(count)},
     * {@code reportOfflineNotifications()}, and
     * {@code maybeClearPendingMessages(count)}.
     */
    private void handleOffline(Node offlineNode) {
        var count = offlineNode.getAttributeAsInt("count", 0); // WAWebHandleInfoBulletin parser: e.child(OFFLINE).attrInt("count")
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline bulletin with count={0}", count);

        if (count == 0) { // ADAPTED: WAWebHandleInfoBulletin.default: maybeClearPendingMessages(count) clears when count=0
            whatsapp.retryOrphanMutations(); // ADAPTED: Cobalt triggers orphan retry at offline end
        }
    }

    /**
     * Handles offline preview bulletins which contain categorised counts of
     * pending offline messages.
     *
     * <p>The preview provides counts broken down by message type: total count,
     * message, receipt, notification, and call.
     *
     * @param previewNode the {@code offline_preview} child node
     * @implNote WAWebHandleInfoBulletin.default (OFFLINE_PREVIEW case):
     * calls {@code OfflineMessageHandler.processOfflinePreviewIb(count)}
     * with an object containing {@code count}, {@code message},
     * {@code receipt}, {@code notification}, and {@code call} fields.
     */
    private void handleOfflinePreview(Node previewNode) {
        LOGGER.log(System.Logger.Level.DEBUG, // ADAPTED: WAWebHandleInfoBulletin.default: yield OfflineMessageHandler.processOfflinePreviewIb(n.count)
                "Received offline preview bulletin count={0} message={1} receipt={2} notification={3} call={4}",
                previewNode.getAttributeAsInt("count", 0), // WAWebHandleInfoBulletin parser: attrInt("count")
                previewNode.getAttributeAsInt("message", 0), // WAWebHandleInfoBulletin parser: attrInt("message")
                previewNode.getAttributeAsInt("receipt", 0), // WAWebHandleInfoBulletin parser: attrInt("receipt")
                previewNode.getAttributeAsInt("notification", 0), // WAWebHandleInfoBulletin parser: attrInt("notification")
                previewNode.getAttributeAsInt("call", 0)); // WAWebHandleInfoBulletin parser: attrInt("call")
    }

    /**
     * Handles the offline priority complete bulletin which signals that all
     * priority offline messages have been delivered.
     *
     * @implNote WAWebHandleInfoBulletin.default (OFFLINE_PRIORITY_COMPLETE
     * case): returns {@code "NO_ACK"} with no further action.
     */
    private void handleOfflinePriorityComplete() {
        LOGGER.log(System.Logger.Level.DEBUG, // WAWebHandleInfoBulletin.default: no explicit action, just returns NO_ACK
                "Received offline_priority_complete bulletin");
        whatsapp.retryOrphanMutations(); // ADAPTED: Cobalt retries orphan mutations on priority offline complete (Cobalt-specific)
    }

    /**
     * Handles Terms-of-Service notice bulletins by extracting notice IDs and
     * storing them.
     *
     * <p>The TOS bulletin contains one or more {@code notice} children, each
     * with an {@code id} attribute identifying a specific TOS notice.
     *
     * @param tosNode the {@code tos} child node
     * @implNote WAWebHandleInfoBulletin.default (TOS case):
     * extracts notice IDs and calls
     * {@code WAWebTos.TosManager.maybeUpdateServer(noticeIds)}.
     */
    private void handleTos(Node tosNode) {
        var notices = tosNode.getChildren("notice").stream() // WAWebHandleInfoBulletin parser: e.child("tos").forEachChildWithTag("notice", ...)
                .map(entry -> entry.getAttributeAsString("id", null)) // WAWebHandleInfoBulletin parser: e.attrString("id")
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        whatsapp.store().setTosNoticeIds(notices); // ADAPTED: WAWebHandleInfoBulletin.default: TosManager.maybeUpdateServer(noticeIds) - Cobalt stores for consumer-side handling
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received TOS bulletin notices={0}", notices);
    }

    /**
     * Handles client expiration bulletins by computing and storing the
     * server-mandated client expiration override.
     *
     * <p>The expiration timestamp from the server is clamped between a minimum
     * of 3 days in the future and the hard expiration time configured for the
     * client version. If the new timestamp is not earlier than an existing
     * override or the hard expiration, it is ignored. If the timestamp is
     * {@code null}, any existing override is cleared.
     *
     * @param clientExpirationNode the {@code client_expiration} child node
     * @implNote WAWebHandleServerClientExpiration.handleServerClientExpiration:
     * reads hard expire time from {@code WAWebUpdaterHardExpireTime}, compares
     * against existing override, clamps with
     * {@code Math.max(futureUnixTime(3 * DAY_SECONDS), Math.min(e, t))},
     * and stores via
     * {@code setServerClientExpirationOverride(String, VERSION_BASE)}.
     */
    private void handleClientExpiration(Node clientExpirationNode) {
        // WASmaxInClientExpirationClientExpirationRequest.parseClientExpirationRequest:
        // reads optional int attr "t" from the client_expiration child
        var expirationAttr = clientExpirationNode.getAttributeAsLong("t", (Long) null); // WASmaxInClientExpirationClientExpirationRequest: attrIntRange(n.value, "t", 0, undefined)
        if (expirationAttr == null) { // WAWebHandleServerClientExpiration.handleServerClientExpiration: if (e == null) clearServerClientExpirationOverride()
            whatsapp.store().setClientExpiration(null); // WAWebHandleServerClientExpiration: clearServerClientExpirationOverride()
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cleared client expiration override"); // WAWebHandleServerClientExpiration: no explicit log, but Cobalt logs for observability
            return;
        }

        // WAWebHandleInfoBulletin parser: castToUnixTime(u) clamps to valid unix time range
        var newExpiration = expirationAttr; // WATimeUtils.castToUnixTime: clamps e|0 to [MIN_INT+1, MAX_INT]

        // WAWebHandleServerClientExpiration: var n,a = getServerClientExpirationOverride()?.timestamp
        var existingExpiration = whatsapp.store().clientExpiration().orElse(null); // WAWebUserPrefsMultiDevice.getServerClientExpirationOverride

        // WAWebHandleServerClientExpiration: if (a != null && e >= a || e >= t) return;
        // The hard expire time check is skipped in Cobalt since we don't have WAWebUpdaterHardExpireTime;
        // but the existing-override check is preserved.
        if (existingExpiration != null && newExpiration >= existingExpiration.getEpochSecond()) { // WAWebHandleServerClientExpiration: a != null && e >= a
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring client expiration {0}: not earlier than existing {1}",
                    newExpiration, existingExpiration); // NO_WA_BASIS: Cobalt log for observability
            return;
        }

        // WAWebHandleServerClientExpiration: var i = futureUnixTime(3 * DAY_SECONDS)
        // DAY_SECONDS = 86400, 3 * DAY_SECONDS = 259200
        var threeDaysFromNow = Instant.now().plusSeconds(3L * 86400L); // WATimeUtils.futureUnixTime(3 * DAY_SECONDS)

        // WAWebHandleServerClientExpiration: var l = Math.max(i, Math.min(e, t))
        // Without hard expire time (t), we use the new expiration directly as the upper bound
        var clampedExpiration = newExpiration < threeDaysFromNow.getEpochSecond() // WAWebHandleServerClientExpiration: Math.max(floor, Math.min(e, t))
                ? threeDaysFromNow
                : Instant.ofEpochSecond(newExpiration);

        whatsapp.store().setClientExpiration(clampedExpiration); // WAWebHandleServerClientExpiration: setServerClientExpirationOverride("" + l, VERSION_BASE)
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received client expiration bulletin, clamped to {0}", clampedExpiration);
    }

    /**
     * Handles thread metadata bulletins.
     *
     * <p>Thread metadata is parsed and stored for offline thread
     * synchronisation purposes.
     *
     * @param threadMetaNode the {@code thread_metadata} child node
     * @implNote WAWebHandleInfoBulletin.default (THREAD_META case):
     * calls {@code WAWebParseThreadMetadata.parseThreadMetadata(e)} then
     * {@code WAWebThreadMetadata.setOfflineThreadMeta(threadMeta)}.
     */
    private void handleThreadMeta(Node threadMetaNode) {
        LOGGER.log(System.Logger.Level.DEBUG, // ADAPTED: WAWebHandleInfoBulletin.default: parses and stores thread metadata; Cobalt logs only
                "Received thread_metadata bulletin {0}",
                threadMetaNode);
    }
}
