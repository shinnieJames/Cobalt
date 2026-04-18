package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles incoming {@code <ib>} (info bulletin) stanzas from the WhatsApp
 * server.
 *
 * <p>Info bulletins are a catch-all control-plane channel that the server
 * uses to push a wide range of asynchronous notifications to the client:
 * dirty-bit syncs that force re-fetch of out-of-date data, edge-routing
 * updates that steer the next reconnect, offline backlog counters that
 * drive the offline-resume state machine, offline priority completion
 * markers, Terms-of-Service notice lists, per-thread offline timestamps
 * and server-mandated client expiration overrides.
 *
 * <p>The WA Web parser first validates that the stanza is tagged
 * {@code ib} and comes from the server, then inspects child tags in a
 * fixed priority order. The first recognised child determines the
 * parsed result shape, which is handed to an async dispatch function
 * that routes each result type to the appropriate subsystem. Cobalt
 * collapses the two-phase parser/dispatcher into a single ordered
 * {@code if-else} chain mirroring the WA Web priority, with one private
 * method per branch.
 *
 * @implNote WAWebHandleInfoBulletin.default: the top-level
 * {@code infoBulletinParser} plus the async dispatch function {@code _}.
 * Child tags and the {@code INFO_TYPE} constants come from
 * {@code WAWebHandleInfoBulletinTypes.flow.INFO_TYPE}.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleInfoBulletin")
@WhatsAppWebModule(moduleName = "WAWebHandleDirtyBits")
@WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
@WhatsAppWebModule(moduleName = "WAWebHandleRoutingInfo")
@WhatsAppWebModule(moduleName = "WAWebHandleServerClientExpiration")
public final class InfoBulletinStreamHandler implements SocketStream.Handler {
    /**
     * Logger used for info bulletin diagnostic output.
     *
     * @implNote WAWebHandleInfoBulletin.default: uses {@code WALogger} with
     * tagged template literals for {@code ERROR}, {@code WARN} and
     * {@code LOG} output. Cobalt uses {@link System.Logger} instead.
     */
    private static final System.Logger LOGGER =
            System.getLogger(InfoBulletinStreamHandler.class.getName());

    /**
     * Child tag carrying dirty-bit notifications.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.DIRTY =
     * {@code "dirty"}.
     */
    private static final String INFO_TYPE_DIRTY = "dirty";

    /**
     * Child tag carrying edge-routing info.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.ROUTING =
     * {@code "edge_routing"}.
     */
    private static final String INFO_TYPE_ROUTING = "edge_routing";

    /**
     * Child tag carrying the total offline message count.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.OFFLINE =
     * {@code "offline"}.
     */
    private static final String INFO_TYPE_OFFLINE = "offline";

    /**
     * Child tag carrying the offline priority completion marker.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.OFFLINE_PRIORITY_COMPLETE
     * = {@code "priority_offline_complete"}.
     */
    private static final String INFO_TYPE_OFFLINE_PRIORITY_COMPLETE = "priority_offline_complete";

    /**
     * Child tag carrying categorised offline message counts.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.OFFLINE_PREVIEW
     * = {@code "offline_preview"}.
     */
    private static final String INFO_TYPE_OFFLINE_PREVIEW = "offline_preview";

    /**
     * Child tag carrying Terms-of-Service notices.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.TOS =
     * {@code "tos"}.
     */
    private static final String INFO_TYPE_TOS = "tos";

    /**
     * Child tag carrying per-thread offline timestamps.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.THREAD_META =
     * {@code "thread_metadata"}.
     */
    private static final String INFO_TYPE_THREAD_META = "thread_metadata";

    /**
     * Child tag carrying the server-mandated client expiration override.
     *
     * @implNote WAWebHandleInfoBulletinTypes.flow.INFO_TYPE.CLIENT_EXPIRATION
     * = {@code "client_expiration"}.
     */
    private static final String INFO_TYPE_CLIENT_EXPIRATION = "client_expiration";

    /**
     * Dirty-bit type name that triggers app-state syncd collection pulls.
     *
     * @implNote WAWebDirtyBitsConsts.SUPPORTED_DIRTY_TYPE.syncd_app_state =
     * {@code "syncd_app_state"}.
     */
    private static final String DIRTY_TYPE_SYNCD_APP_STATE = "syncd_app_state";

    /**
     * Dirty-bit type name that triggers account-level subsystem refreshes
     * (devices, profile picture, privacy, block list, notices, opt-out list).
     *
     * @implNote WAWebDirtyBitsConsts.SUPPORTED_DIRTY_TYPE.account_sync =
     * {@code "account_sync"}.
     */
    private static final String DIRTY_TYPE_ACCOUNT_SYNC = "account_sync";

    /**
     * Dirty-bit type name that triggers a group metadata refresh after
     * offline delivery ends.
     *
     * @implNote WAWebDirtyBitsConsts.SUPPORTED_DIRTY_TYPE.groups =
     * {@code "groups"}.
     */
    private static final String DIRTY_TYPE_GROUPS = "groups";

    /**
     * Dirty-bit type name that triggers a newsletter metadata refresh
     * after offline delivery ends.
     *
     * @implNote WAWebDirtyBitsConsts.SUPPORTED_DIRTY_TYPE.newsletter_metadata
     * = {@code "newsletter_metadata"}.
     */
    private static final String DIRTY_TYPE_NEWSLETTER_METADATA = "newsletter_metadata";

    /**
     * Set of supported account-sync protocol names that may appear as
     * children of an {@code account_sync} dirty entry. Any value not in
     * this set is ignored.
     *
     * @implNote WAWebDirtyBitsConsts.SUPPORTED_DIRTY_PROTOCOLS =
     * {@code {devices, picture, privacy, blocklist, notice}} from
     * {@code WAWebAccountSyncJob.AccountSyncType}.
     */
    private static final Set<String> SUPPORTED_DIRTY_PROTOCOLS = Set.of(
            "devices", "picture", "privacy", "blocklist", "notice"
    );

    /**
     * Default routing domain used when the stanza omits {@code dns_domain}
     * and no previously stored domain exists.
     *
     * @implNote WAWebHandleRoutingInfo.DOMAINS = {@code {fb: "fb", sl: "sl"}};
     * the default used inside {@code handleRoutingInfo} when the domain is
     * missing and {@code getRoutingInfo()} resolves to {@code null} is
     * {@code DOMAINS.fb}, i.e. the string {@code "fb"}.
     */
    private static final String DEFAULT_ROUTING_DOMAIN = "fb";

    /**
     * Minimum future floor applied to the client expiration override, in
     * seconds. When the server pushes an expiration timestamp the resulting
     * value is clamped to at least this many seconds in the future.
     *
     * @implNote WAWebHandleServerClientExpiration: {@code 3 * DAY_SECONDS}
     * where {@code DAY_SECONDS} is {@code 24 * 60 * 60 = 86400} per
     * {@code WATimeUtils.DAY_SECONDS}.
     */
    private static final long CLIENT_EXPIRATION_MIN_FLOOR_SECONDS = 3L * 86_400L;

    /**
     * The WhatsApp client used for store access, outgoing stanza dispatch
     * and delegated service calls.
     *
     * @implNote WAWebHandleInfoBulletin.default uses module-level imports
     * (for example {@code o("WAWebHandleRoutingInfo")}); Cobalt injects the
     * single {@link WhatsAppClient} facade via the constructor and reaches
     * the equivalent subsystems through it.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new info bulletin stream handler bound to the supplied
     * client.
     *
     * @param whatsapp the WhatsApp client instance, must not be {@code null}
     * @implNote WAWebHandleInfoBulletin.default: the handler is registered
     * by {@code WADeprecatedWapParser("infoBulletinParser", ...)}; Cobalt
     * registers handlers as {@link SocketStream.Handler} implementations
     * via {@link SocketStream}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InfoBulletinStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Dispatches an incoming {@code <ib>} stanza to the appropriate
     * subsystem based on the first recognised info-type child.
     *
     * <p>The priority order follows WA Web's parser exactly: {@code dirty},
     * then {@code edge_routing}, then {@code offline}, then
     * {@code priority_offline_complete}, then {@code offline_preview}, then
     * {@code tos}, then {@code thread_metadata}, then
     * {@code client_expiration}. Only the first matching child drives
     * dispatch; any subsequent children are ignored. A stanza whose
     * children contain no recognised info type is logged as a warning,
     * matching WA Web's {@code "handleInfoBulletin unrecognized info
     * bulletin"} fallback.
     *
     * <p>Any exception thrown by a branch is caught and logged so that a
     * malformed bulletin cannot propagate up through the socket reader.
     *
     * @param node the {@code ib} stanza node, never {@code null}
     * @implNote WAWebHandleInfoBulletin.default: the parser function
     * {@code p} performs {@code assertTag("ib")}, {@code assertFromServer()}
     * and then the ordered {@code hasChild} chain; the async dispatch
     * function {@code _}/{@code f} switches on the parsed result type and
     * returns {@code "NO_ACK"} so that the stream does not ack the stanza.
     * Cobalt does not ack control stanzas either so the sentinel is not
     * materialised.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        try {
            // WAWebHandleInfoBulletin.default parser: e.hasChild(INFO_TYPE.DIRTY)
            if (node.hasChild(INFO_TYPE_DIRTY)) {
                handleDirty(node);
                return;
            }

            // WAWebHandleInfoBulletin.default parser: else if e.hasChild(INFO_TYPE.ROUTING)
            var routing = node.getChild(INFO_TYPE_ROUTING);
            if (routing.isPresent()) {
                handleRouting(routing.get());
                return;
            }

            // WAWebHandleInfoBulletin.default parser: if e.hasChild(INFO_TYPE.OFFLINE)
            var offline = node.getChild(INFO_TYPE_OFFLINE);
            if (offline.isPresent()) {
                handleOffline(offline.get());
                return;
            }

            // WAWebHandleInfoBulletin.default parser: if e.hasChild(INFO_TYPE.OFFLINE_PRIORITY_COMPLETE)
            if (node.hasChild(INFO_TYPE_OFFLINE_PRIORITY_COMPLETE)) {
                handleOfflinePriorityComplete();
                return;
            }

            // WAWebHandleInfoBulletin.default parser: if e.hasChild(INFO_TYPE.OFFLINE_PREVIEW)
            var preview = node.getChild(INFO_TYPE_OFFLINE_PREVIEW);
            if (preview.isPresent()) {
                handleOfflinePreview(preview.get());
                return;
            }

            // WAWebHandleInfoBulletin.default parser: else if e.hasChild(INFO_TYPE.TOS)
            var tos = node.getChild(INFO_TYPE_TOS);
            if (tos.isPresent()) {
                handleTos(tos.get());
                return;
            }

            // WAWebHandleInfoBulletin.default parser: if e.hasChild(INFO_TYPE.THREAD_META)
            var threadMeta = node.getChild(INFO_TYPE_THREAD_META);
            if (threadMeta.isPresent()) {
                handleThreadMeta(threadMeta.get());
                return;
            }

            // WAWebHandleInfoBulletin.default parser: if e.hasChild(INFO_TYPE.CLIENT_EXPIRATION)
            var expiration = node.getChild(INFO_TYPE_CLIENT_EXPIRATION);
            if (expiration.isPresent()) {
                handleClientExpiration(expiration.get());
                return;
            }

            // WAWebHandleInfoBulletin.default dispatch: WARN "handleInfoBulletin unrecognized info bulletin"
            LOGGER.log(System.Logger.Level.WARNING,
                    "handleInfoBulletin unrecognized info bulletin {0}",
                    node.getAttributeAsString("id", "[missing-id]"));
        } catch (Throwable throwable) {
            // ADAPTED: WAWebHandleInfoBulletin.default throws parse errors out to the caller;
            // Cobalt catches so that one malformed ib cannot poison the stream reader.
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle info bulletin {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"),
                    throwable.getMessage());
        }
    }

    /**
     * Processes the {@code dirty} children of an {@code ib} stanza,
     * refreshing every affected subsystem and finally acknowledging the
     * dirty bits back to the server.
     *
     * <p>For each dirty entry the {@code type} attribute determines the
     * action: {@code syncd_app_state} marks every app-state collection for
     * pull, {@code account_sync} iterates the supported protocol children
     * ({@code devices}, {@code picture}, {@code privacy}, {@code blocklist},
     * {@code notice}) and flags the corresponding Cobalt sync booleans,
     * {@code groups} and {@code newsletter_metadata} are logged for the
     * deferred metadata refresh path. Entries whose {@code type} is not in
     * {@link #SUPPORTED_DIRTY_PROTOCOLS} for account-sync or in the set of
     * supported dirty types are still included in the ack batch sent to
     * the server, matching WA Web's {@code [].concat(unsupported,
     * supported)} behaviour.
     *
     * <p>After every entry has been processed this method schedules the
     * aggregated app-state pull and sends the {@code clean} IQ.
     *
     * @param node the parent {@code ib} node, never {@code null}
     * @implNote WAWebHandleDirtyBits.handleDirtyBits: iterates every
     * supported dirty entry, resolves each {@code type} against
     * {@code SUPPORTED_DIRTY_TYPE}, runs the {@code Promise.all} of
     * per-type handlers, then calls
     * {@code WAWebClearDirtyBitsJob.clearDirtyBits([...unsupported,
     * ...supported])}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleDirtyBits", exports = "handleDirtyBits",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleDirty(Node node) {
        var collectionsToSync = new LinkedHashSet<SyncPatchType>(); // ADAPTED: WA Web schedules WAWebSyncd.markCollectionsForSync(...); Cobalt aggregates into a single pullWebAppState call
        var allDirtyEntries = new ArrayList<Node>(); // WAWebHandleDirtyBits.handleDirtyBits: var d = [].concat(a, r)
        var supportedTypes = new ArrayList<String>(); // WAWebHandleDirtyBits.handleDirtyBits: i = r.map(e => e.type).join(",")
        var unsupportedTypes = new ArrayList<String>(); // WAWebHandleDirtyBits.handleDirtyBits: l = a.map(e => e.type).join(",")

        // WAWebHandleInfoBulletin.default parser: e.forEachChildWithTag(INFO_TYPE.DIRTY, ...)
        for (var dirtyNode : node.getChildren(INFO_TYPE_DIRTY)) {
            allDirtyEntries.add(dirtyNode); // WAWebHandleDirtyBits.handleDirtyBits: both supported and unsupported are passed to clearDirtyBits
            var type = dirtyNode.getAttributeAsString("type", null); // WAWebHandleInfoBulletin.default parser: e.attrString("type")

            if (DIRTY_TYPE_ACCOUNT_SYNC.equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits: SUPPORTED_DIRTY_TYPE.account_sync -> c(protocols)
                supportedTypes.add(type);
                // WAWebHandleInfoBulletin.default parser: var i = e.mapChildren(e => e.tag())
                for (var child : dirtyNode.children()) {
                    var protocol = child.description();
                    // WAWebHandleInfoBulletin.default parser: i.forEach(e => {
                    //   if (hasOwnProperty(SUPPORTED_DIRTY_PROTOCOLS, e)) r.push(e);
                    // })
                    if (!SUPPORTED_DIRTY_PROTOCOLS.contains(protocol)) {
                        continue;
                    }
                    // WAWebHandleDirtyBits.c: dispatches per AccountSyncType.
                    // Cobalt delegates to subsystem-specific sync flags; the client / device
                    // service observes them and re-fetches lazily on the next access.
                    switch (protocol) {
                        case "devices" -> // ADAPTED: WAWebHandleDirtyBits.c: DEVICES -> _() -> getDevices("notification")
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/devices: device list refresh needed");
                        case "picture" -> // ADAPTED: WAWebHandleDirtyBits.c: PICTURE -> getAndUpdateProfilePicture()
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/picture: profile picture refresh needed");
                        case "privacy" -> { // ADAPTED: WAWebHandleDirtyBits.c: PRIVACY -> updatePrivacySettings()
                            whatsapp.store().setSyncedContacts(false);
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Dirty bit account_sync/privacy: privacy settings refresh needed");
                        }
                        case "blocklist" -> { // ADAPTED: WAWebHandleDirtyBits.c: BLOCKLIST -> fetchAndUpdateBlocklist("dirty-bit")
                            whatsapp.store().setSyncedContacts(false);
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Dirty bit account_sync/blocklist: block list refresh needed");
                        }
                        case "notice" -> // ADAPTED: WAWebHandleDirtyBits.c: NOTICE -> f() -> TosManager.run() + newsletter gating notices
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/notice: notice refresh needed");
                        default -> {
                            // unreachable: SUPPORTED_DIRTY_PROTOCOLS gate above
                        }
                    }
                }
                // ADAPTED: Cobalt marks status as dirty on account_sync to force a refresh
                // the next time the caller reads the value; WA Web uses explicit imperative
                // calls into each subsystem instead.
                whatsapp.store().setSyncedStatus(false);
            } else if (DIRTY_TYPE_SYNCD_APP_STATE.equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits: SUPPORTED_DIRTY_TYPE.syncd_app_state -> p()
                supportedTypes.add(type);
                // WAWebHandleDirtyBits.p: WAWebSyncd.markCollectionsForSync(Array.from(CollectionName.members()))
                Collections.addAll(collectionsToSync, SyncPatchType.values());
            } else if (DIRTY_TYPE_GROUPS.equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits: SUPPORTED_DIRTY_TYPE.groups
                supportedTypes.add(type);
                // ADAPTED: WA Web calls waitForOfflineDeliveryEnd().then(queryAndUpdateAllGroupMetadata);
                // Cobalt logs and lets the caller refetch group metadata on the next read.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Dirty bit groups: group metadata refresh needed");
            } else if (DIRTY_TYPE_NEWSLETTER_METADATA.equals(type)) { // WAWebHandleDirtyBits.handleDirtyBits: SUPPORTED_DIRTY_TYPE.newsletter_metadata
                supportedTypes.add(type);
                // ADAPTED: WA Web calls waitForOfflineDeliveryEnd().then(queryAndUpdateAllNewsletterMetadataAction);
                // Cobalt logs and lets the caller refetch newsletter metadata on the next read.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Dirty bit newsletter_metadata: newsletter metadata refresh needed");
            } else {
                // WAWebHandleInfoBulletin.default parser: unsupported dirty types go into t = []
                unsupportedTypes.add(type == null ? "" : type);
            }
        }

        // WAWebHandleDirtyBits.handleDirtyBits: LOG "handleDirtyBits supported=... unsupported=..."
        LOGGER.log(System.Logger.Level.DEBUG,
                "handleDirtyBits supported={0} unsupported={1}",
                String.join(",", supportedTypes),
                String.join(",", unsupportedTypes));

        if (!collectionsToSync.isEmpty()) {
            whatsapp.store().setSyncedWebAppState(false); // ADAPTED: Cobalt gate that lets the next app-state consumer re-pull
            // WAWebHandleDirtyBits.p: WAWebSyncd.markCollectionsForSync(...)
            whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new));
        }

        clearDirtyBits(allDirtyEntries); // WAWebHandleDirtyBits.handleDirtyBits: return WAWebClearDirtyBitsJob.clearDirtyBits(d)
        whatsapp.retryOrphanMutations(); // ADAPTED: Cobalt retries orphan app-state mutations after a dirty batch; no direct WA Web equivalent
    }

    /**
     * Sends a {@code clean} IQ stanza to acknowledge every processed dirty
     * entry.
     *
     * <p>The IQ carries one {@code clean} child per dirty entry, preserving
     * the original {@code type} and {@code timestamp} attributes so that
     * the server can clear the matching dirty bits. An empty batch is
     * skipped, mirroring WA Web's {@code if (t.length !== 0)} guard.
     * Transport failures are logged as warnings and do not propagate, since
     * the server will simply retransmit the next time the client connects.
     *
     * @param dirtyEntries the dirty entries to acknowledge, never
     *                     {@code null}
     * @implNote WAWebClearDirtyBitsJob.clearDirtyBits: builds an IQ with
     * attributes {@code to=s.whatsapp.net}, {@code type=set},
     * {@code xmlns=urn:xmpp:whatsapp:dirty}, {@code id=generateId()}, and
     * sends it via {@code deprecatedSendIq}; logs on success and swallows
     * errors with a {@code WARN}.
     */
    @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob", exports = "clearDirtyBits",
            adaptation = WhatsAppAdaptation.DIRECT)
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
            whatsapp.sendNode(new NodeBuilder() // WAWebClearDirtyBitsJob.clearDirtyBits: deprecatedSendIq(wap("iq", {...}, children), parser)
                    .description("iq")
                    .attribute("to", Jid.userServer())
                    .attribute("type", "set")
                    .attribute("xmlns", "urn:xmpp:whatsapp:dirty")
                    .content(cleanChildren));
            LOGGER.log(System.Logger.Level.DEBUG,
                    "clearDirtyBits: success for type: {0}", // WAWebClearDirtyBitsJob.clearDirtyBits: LOG "clearDirtyBits: success for type: %s"
                    dirtyEntries.stream()
                            .map(d -> d.getAttributeAsString("type", "unknown"))
                            .reduce((a, b) -> a + "," + b)
                            .orElse(""));
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "clearDirtyBits: failed with error"); // WAWebClearDirtyBitsJob.clearDirtyBits: WARN "clearDirtyBits: failed with error"
        }
    }

    /**
     * Handles the {@code edge_routing} info bulletin, updating the locally
     * stored routing info and DNS domain.
     *
     * <p>The {@code edge_routing} child carries two sub-nodes: a mandatory
     * {@code routing_info} byte payload and an optional {@code dns_domain}
     * enum. When {@code dns_domain} is absent, the WA Web implementation
     * reads the previously stored domain and falls back to
     * {@link #DEFAULT_ROUTING_DOMAIN} only if neither is present. The new
     * {@code routing_info} bytes always replace the stored payload.
     *
     * @param routingNode the {@code edge_routing} child node, never
     *                    {@code null}
     * @implNote WAWebHandleRoutingInfo.handleRoutingInfo: the async
     * function reads the optional {@code domain} from the parsed result,
     * falls back to the previously stored domain via
     * {@code WAWebUserPrefsMultiDevice.getRoutingInfo()} or to
     * {@code DOMAINS.fb}, converts the {@code edgeRouting} bytes with
     * {@code WAHex.bytesToBuffer} and writes everything back with
     * {@code setRoutingInfo({domain, edgeRouting})}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleRoutingInfo", exports = "handleRoutingInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleRouting(Node routingNode) {
        // WAWebHandleInfoBulletin.default parser: edgeRouting = a.child("routing_info").contentBytes()
        var edgeRouting = routingNode.getChild("routing_info")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        // WAWebHandleInfoBulletin.default parser: domain = a.hasChild("dns_domain") ? a.child("dns_domain").contentEnum(DOMAINS) : null
        var domain = routingNode.getChild("dns_domain")
                .flatMap(Node::toContentString)
                .orElse(null);
        // WAWebHandleRoutingInfo.handleRoutingInfo: validate against DOMAINS map; unknown values fall back to null
        if (domain != null && !"fb".equals(domain) && !"sl".equals(domain)) {
            domain = null;
        }
        if (domain == null) { // WAWebHandleRoutingInfo.handleRoutingInfo: if (!n) { var r = yield getRoutingInfo(); n = r ? r.domain : s.fb }
            domain = whatsapp.store().routingDomain().orElse(DEFAULT_ROUTING_DOMAIN);
        }
        // WAWebHandleRoutingInfo.handleRoutingInfo: var a = WAHex.bytesToBuffer(t.edgeRouting)
        whatsapp.store().setRoutingInfo(edgeRouting);
        // WAWebHandleRoutingInfo.handleRoutingInfo: setRoutingInfo({domain: n, edgeRouting: a})
        whatsapp.store().setRoutingDomain(domain);
        LOGGER.log(System.Logger.Level.DEBUG,
                "handleInfoBulletin setting and domain: {0} and edgeRouting: {1} bytes", // WAWebHandleRoutingInfo.handleRoutingInfo: LOG "handleInfoBulletin setting and domain: %s and edgeRouting:"
                domain, edgeRouting == null ? 0 : edgeRouting.length);
    }

    /**
     * Handles the {@code offline} info bulletin that announces the total
     * number of queued offline messages the server will deliver.
     *
     * <p>In WA Web this feeds the offline resume state machine
     * ({@code OfflineMessageHandler.processOfflineIb}), triggers
     * {@code reportOfflineNotifications} and clears the pending-message
     * dedup cache when the count hits zero. Cobalt does not model the
     * offline resume state machine; this method logs the count and, when
     * the backlog is already empty, drives a best-effort retry of orphan
     * app-state mutations to pick up changes that arrived just before
     * connect.
     *
     * @param offlineNode the {@code offline} child node, never {@code null}
     * @implNote WAWebHandleInfoBulletin.default dispatch
     * (INFO_TYPE.OFFLINE): runs {@code processOfflineIb(count)},
     * {@code reportOfflineNotifications()} and
     * {@code maybeClearPendingMessages(count)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOffline(Node offlineNode) {
        var count = offlineNode.getAttributeAsInt("count", 0); // WAWebHandleInfoBulletin.default parser: e.child(OFFLINE).attrInt("count")
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline bulletin with count={0}", count);
        if (count == 0) { // WAWebMessageDedupUtils.maybeClearPendingMessages: if (count === 0) clear the pending cache
            whatsapp.retryOrphanMutations(); // ADAPTED: Cobalt retries orphan mutations when the backlog is empty; WA Web only clears the dedup cache
        }
    }

    /**
     * Handles the {@code offline_preview} info bulletin that carries a
     * breakdown of the pending offline backlog by stanza type.
     *
     * <p>The preview counts are read for observability. In WA Web this
     * would drive {@code OfflineMessageHandler.processOfflinePreviewIb}
     * which transitions the blocking resume stage; Cobalt logs the values
     * and leaves any future stage-machine integration to higher layers.
     *
     * @param previewNode the {@code offline_preview} child node, never
     *                    {@code null}
     * @implNote WAWebHandleInfoBulletin.default parser reads the attributes
     * {@code count}, {@code message}, {@code receipt}, {@code notification}
     * and {@code call} from the {@code offline_preview} child; the
     * dispatch then calls
     * {@code OfflineMessageHandler.processOfflinePreviewIb}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOfflinePreview(Node previewNode) {
        LOGGER.log(System.Logger.Level.DEBUG, // ADAPTED: WAWebHandleInfoBulletin.default dispatch: OfflineMessageHandler.processOfflinePreviewIb(n.count)
                "Received offline preview bulletin count={0} message={1} receipt={2} notification={3} call={4}",
                previewNode.getAttributeAsInt("count", 0), // WAWebHandleInfoBulletin.default parser: attrInt("count")
                previewNode.getAttributeAsInt("message", 0), // WAWebHandleInfoBulletin.default parser: attrInt("message")
                previewNode.getAttributeAsInt("receipt", 0), // WAWebHandleInfoBulletin.default parser: attrInt("receipt")
                previewNode.getAttributeAsInt("notification", 0), // WAWebHandleInfoBulletin.default parser: attrInt("notification")
                previewNode.getAttributeAsInt("call", 0)); // WAWebHandleInfoBulletin.default parser: attrInt("call")
    }

    /**
     * Handles the {@code priority_offline_complete} info bulletin.
     *
     * <p>This bulletin signals that every high-priority offline stanza
     * queued for the client has been delivered. WA Web acknowledges the
     * bulletin without performing any further work; Cobalt additionally
     * drives a best-effort orphan-mutation retry because dependencies on
     * peer data may now be satisfied.
     *
     * @implNote WAWebHandleInfoBulletin.default dispatch:
     * {@code INFO_TYPE.OFFLINE_PRIORITY_COMPLETE} has no case in the switch
     * and the parser returns {@code {type: OFFLINE_PRIORITY_COMPLETE}}, so
     * the dispatcher falls through the {@code default} branch and simply
     * returns {@code "NO_ACK"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOfflinePriorityComplete() {
        LOGGER.log(System.Logger.Level.DEBUG, // WAWebHandleInfoBulletin.default dispatch: no switch case, just NO_ACK
                "Received priority_offline_complete bulletin");
        whatsapp.retryOrphanMutations(); // ADAPTED: Cobalt retries orphan app-state mutations when priority offline delivery ends
    }

    /**
     * Handles the {@code tos} info bulletin that carries a list of new
     * Terms-of-Service notices pending the user's attention.
     *
     * <p>Each {@code notice} child has an {@code id} attribute identifying
     * a specific notice. Cobalt stores the collected IDs; consumer code
     * can inspect the set and render the notices at an appropriate time.
     *
     * @param tosNode the {@code tos} child node, never {@code null}
     * @implNote WAWebHandleInfoBulletin.default parser: iterates
     * {@code "tos"} child {@code notice} entries and collects the
     * {@code id} attribute into {@code noticeIds}; dispatch then calls
     * {@code WAWebTos.TosManager.maybeUpdateServer(noticeIds)} which pushes
     * accepted states back. Cobalt performs the server round-trip lazily
     * at the consumer side via the stored set.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleTos(Node tosNode) {
        var notices = tosNode.getChildren("notice").stream() // WAWebHandleInfoBulletin.default parser: e.child("tos").forEachChildWithTag("notice", ...)
                .map(entry -> entry.getAttributeAsString("id", null)) // WAWebHandleInfoBulletin.default parser: e.attrString("id")
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        whatsapp.store().setTosNoticeIds(notices); // ADAPTED: WAWebHandleInfoBulletin.default dispatch: TosManager.maybeUpdateServer(noticeIds)
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received TOS bulletin notices={0}", notices);
    }

    /**
     * Handles the {@code thread_metadata} info bulletin that carries
     * per-thread offline timestamps.
     *
     * <p>Each {@code item} child has a {@code from} chat JID and a
     * {@code t} timestamp attribute. WA Web parses these into a map keyed
     * by chat JID string and hands it to
     * {@code WAWebThreadMetadata.setOfflineThreadMeta}. Cobalt does not
     * expose an offline thread metadata store; the payload is parsed for
     * validation and logged at debug level.
     *
     * @param threadMetaNode the {@code thread_metadata} child node, never
     *                       {@code null}
     * @implNote WAWebHandleInfoBulletin.default parser (THREAD_META): via
     * {@code WAWebParseThreadMetadata.parseThreadMetadata}, for every
     * {@code item} child reads {@code attrChatJid("from")} and
     * {@code attrTime("t")} into a {@code {chatTimestamp: {jid: t}}} map;
     * the dispatch then calls
     * {@code WAWebThreadMetadata.setOfflineThreadMeta(threadMeta)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleThreadMeta(Node threadMetaNode) {
        var itemCount = 0;
        for (var item : threadMetaNode.getChildren("item")) { // WAWebParseThreadMetadata.parseThreadMetadata: t.forEachChildWithTag("item", ...)
            var from = item.getAttributeAsJid("from").orElse(null); // WAWebParseThreadMetadata.parseThreadMetadata: e.attrChatJid("from")
            var timestamp = item.getAttributeAsLong("t", (Long) null); // WAWebParseThreadMetadata.parseThreadMetadata: e.attrTime("t")
            if (from == null || timestamp == null) {
                continue;
            }
            itemCount++;
            // ADAPTED: WA Web stores the entry in WAWebThreadMetadata.setOfflineThreadMeta;
            // Cobalt has no offline thread metadata store and logs per entry instead.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "thread_metadata item chat={0} timestamp={1}",
                    from, timestamp);
        }
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received thread_metadata bulletin with {0} items", itemCount);
    }

    /**
     * Handles the {@code client_expiration} info bulletin, applying or
     * clearing the server-mandated client expiration override.
     *
     * <p>When the {@code t} attribute is absent the stored override is
     * cleared. Otherwise the new timestamp is compared against any
     * existing override: if the new value is not earlier than the current
     * override the update is ignored (the server never extends the
     * expiration window). Accepted values are clamped to at least
     * {@link #CLIENT_EXPIRATION_MIN_FLOOR_SECONDS} in the future to give
     * the user a reasonable grace period.
     *
     * @param clientExpirationNode the {@code client_expiration} child
     *                             node, never {@code null}
     * @implNote WAWebHandleServerClientExpiration.handleServerClientExpiration:
     * reads {@code WAWebUpdaterHardExpireTime} as the hard cap,
     * short-circuits when the new value is not earlier than either the
     * existing override or the hard cap, clamps via
     * {@code Math.max(futureUnixTime(3 * DAY_SECONDS), Math.min(e, t))},
     * and persists with
     * {@code setServerClientExpirationOverride(String(l), VERSION_BASE)}.
     * Cobalt does not have {@code WAWebUpdaterHardExpireTime}; the hard
     * cap check is skipped which makes the accepted value effectively
     * unbounded above.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleServerClientExpiration",
            exports = "handleServerClientExpiration", adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleClientExpiration(Node clientExpirationNode) {
        // WASmaxInClientExpirationClientExpirationRequest.parseClientExpirationRequest:
        // reads the optional int attribute "t" from the client_expiration child
        var expirationAttr = clientExpirationNode.getAttributeAsLong("t", (Long) null);
        if (expirationAttr == null) { // WAWebHandleServerClientExpiration.handleServerClientExpiration: if (e == null) clearServerClientExpirationOverride()
            whatsapp.store().setClientExpiration(null);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cleared client expiration override"); // ADAPTED: Cobalt observability log; WA Web clears silently
            return;
        }

        // WATimeUtils.castToUnixTime clamps (e|0) to [MIN_INT+1, MAX_INT]. In Java the value is
        // already a long and the clamp would only collapse invalid values, so we forward as-is.
        var newExpiration = expirationAttr;

        // WAWebHandleServerClientExpiration.handleServerClientExpiration:
        // var a = getServerClientExpirationOverride()?.timestamp
        var existingExpiration = whatsapp.store().clientExpiration().orElse(null);

        // WAWebHandleServerClientExpiration.handleServerClientExpiration:
        // if (a != null && e >= a) return;
        if (existingExpiration != null && newExpiration >= existingExpiration.getEpochSecond()) {
            LOGGER.log(System.Logger.Level.DEBUG, // ADAPTED: Cobalt observability log; WA Web returns silently
                    "Ignoring client expiration {0}: not earlier than existing {1}",
                    newExpiration, existingExpiration);
            return;
        }

        // WAWebHandleServerClientExpiration.handleServerClientExpiration:
        // if (e >= t) return; (t = WAWebUpdaterHardExpireTime)
        // ADAPTED: Cobalt has no WAWebUpdaterHardExpireTime equivalent so the hard cap is skipped.

        // WAWebHandleServerClientExpiration.handleServerClientExpiration:
        // var i = futureUnixTime(3 * DAY_SECONDS)
        var minFloor = Instant.now().plusSeconds(CLIENT_EXPIRATION_MIN_FLOOR_SECONDS);

        // WAWebHandleServerClientExpiration.handleServerClientExpiration:
        // var l = Math.max(i, Math.min(e, t))
        // Without a hard cap the inner Math.min(e, t) collapses to e, so the final value is
        // max(minFloor, newExpiration).
        var clampedExpiration = newExpiration < minFloor.getEpochSecond()
                ? minFloor
                : Instant.ofEpochSecond(newExpiration);

        whatsapp.store().setClientExpiration(clampedExpiration); // WAWebHandleServerClientExpiration.handleServerClientExpiration: setServerClientExpirationOverride("" + l, VERSION_BASE)
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received client expiration bulletin, clamped to {0}", clampedExpiration);
    }
}
