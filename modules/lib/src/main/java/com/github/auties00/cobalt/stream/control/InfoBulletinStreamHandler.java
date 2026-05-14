package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientOfflineResumeState;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.clientexpiration.SmaxClientExpirationResponse;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdAppStateDirtyBitsEventBuilder;

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
 */
@WhatsAppWebModule(moduleName = "WAWebHandleInfoBulletin")
@WhatsAppWebModule(moduleName = "WAWebHandleDirtyBits")
@WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
@WhatsAppWebModule(moduleName = "WAWebHandleRoutingInfo")
@WhatsAppWebModule(moduleName = "WAWebHandleServerClientExpiration")
@WhatsAppWebModule(moduleName = "WASmaxClientExpirationClientExpirationRPC")
public final class InfoBulletinStreamHandler implements SocketStream.Handler {
    /**
     * Logger used for info bulletin diagnostic output.
     */
    private static final System.Logger LOGGER =
            System.getLogger(InfoBulletinStreamHandler.class.getName());

    /**
     * Child tag carrying dirty-bit notifications.
     */
    private static final String INFO_TYPE_DIRTY = "dirty";

    /**
     * Child tag carrying edge-routing info.
     */
    private static final String INFO_TYPE_ROUTING = "edge_routing";

    /**
     * Child tag carrying the total offline message count.
     */
    private static final String INFO_TYPE_OFFLINE = "offline";

    /**
     * Child tag carrying the offline priority completion marker.
     */
    private static final String INFO_TYPE_OFFLINE_PRIORITY_COMPLETE = "priority_offline_complete";

    /**
     * Child tag carrying categorised offline message counts.
     */
    private static final String INFO_TYPE_OFFLINE_PREVIEW = "offline_preview";

    /**
     * Child tag carrying Terms-of-Service notices.
     */
    private static final String INFO_TYPE_TOS = "tos";

    /**
     * Child tag carrying per-thread offline timestamps.
     */
    private static final String INFO_TYPE_THREAD_META = "thread_metadata";

    /**
     * Child tag carrying the server-mandated client expiration override.
     */
    private static final String INFO_TYPE_CLIENT_EXPIRATION = "client_expiration";

    /**
     * Dirty-bit type name that triggers app-state syncd collection pulls.
     */
    private static final String DIRTY_TYPE_SYNCD_APP_STATE = "syncd_app_state";

    /**
     * Dirty-bit type name that triggers account-level subsystem refreshes
     * (devices, profile picture, privacy, block list, notices, opt-out list).
     */
    private static final String DIRTY_TYPE_ACCOUNT_SYNC = "account_sync";

    /**
     * Dirty-bit type name that triggers a group metadata refresh after
     * offline delivery ends.
     */
    private static final String DIRTY_TYPE_GROUPS = "groups";

    /**
     * Dirty-bit type name that triggers a newsletter metadata refresh
     * after offline delivery ends.
     */
    private static final String DIRTY_TYPE_NEWSLETTER_METADATA = "newsletter_metadata";

    /**
     * Set of supported account-sync protocol names that may appear as
     * children of an {@code account_sync} dirty entry. Any value not in
     * this set is ignored.
     */
    private static final Set<String> SUPPORTED_DIRTY_PROTOCOLS = Set.of(
            "devices", "picture", "privacy", "blocklist", "notice"
    );

    /**
     * Default routing domain used when the stanza omits {@code dns_domain}
     * and no previously stored domain exists.
     */
    private static final String DEFAULT_ROUTING_DOMAIN = "fb";

    /**
     * Minimum future floor applied to the client expiration override, in
     * seconds. When the server pushes an expiration timestamp the resulting
     * value is clamped to at least this many seconds in the future.
     */
    private static final long CLIENT_EXPIRATION_MIN_FLOOR_SECONDS = 3L * 86_400L;

    /**
     * The WhatsApp client used for store access, outgoing stanza dispatch
     * and delegated service calls.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Reference to the web app-state service, used to retry orphan
     * app-state mutations whenever a bulletin signals that previously
     * missing referents may now exist.
     */
    private final WebAppStateService webAppStateService;

    /**
     * Shared reporter that accumulates per-collection offline
     * {@code server_sync} notification counts in
     * {@code NotificationSyncStreamHandler} and flushes them as a WAM event
     * when the offline bulletin arrives.
     */
    private final OfflineNotificationsReporter offlineNotificationsReporter;

    /**
     * The WAM telemetry service used to commit the dirty-bits event.
     */
    private final WamService wamService;

    /**
     * Reference to the device service, used to drive the
     * {@code doPendingDeviceSync} retry that closes out the offline resume
     * state machine when the {@code offline} info bulletin arrives.
     */
    private final DeviceService deviceService;

    /**
     * Epoch-millis timestamp of the {@code offline_preview} IB that drove
     * the current {@link WhatsAppClientOfflineResumeState#RESUME_ON_RESTART}
     * transition, or {@code 0L} when no preview has been observed since the
     * last completion. Used to gate repeated previews against the
     * {@link WhatsAppClientOfflineResumeState#OFFLINE_PREVIEW_PERIOD_MS}
     * debounce window before they are accepted as updates rather than
     * rejected as noise.
     */
    private volatile long firstOfflinePreviewMillis;

    /**
     * Constructs a new info bulletin stream handler bound to the supplied
     * client and web app-state service.
     *
     * @param whatsapp                     the WhatsApp client instance, must not be {@code null}
     * @param webAppStateService           the web app-state service used for orphan
     *                                     mutation retries, must not be {@code null}
     * @param offlineNotificationsReporter the shared reporter used to flush
     *                                     accumulated offline {@code server_sync}
     *                                     notification counts as a WAM event when the
     *                                     offline bulletin arrives, must not be {@code null}
     * @param wamService                   the WAM telemetry service used to commit the
     *                                     dirty-bits event, must not be {@code null}
     * @param deviceService                the device service, used to run the post-resume
     *                                     pending device sync, must not be {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InfoBulletinStreamHandler(WhatsAppClient whatsapp, WebAppStateService webAppStateService, OfflineNotificationsReporter offlineNotificationsReporter, WamService wamService, DeviceService deviceService) {
        this.whatsapp = whatsapp;
        this.webAppStateService = webAppStateService;
        this.offlineNotificationsReporter = offlineNotificationsReporter;
        this.wamService = wamService;
        this.deviceService = deviceService;
        this.firstOfflinePreviewMillis = 0L;
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
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        try {
            if (node.hasChild(INFO_TYPE_DIRTY)) {
                handleDirty(node);
                return;
            }

            var routing = node.getChild(INFO_TYPE_ROUTING);
            if (routing.isPresent()) {
                handleRouting(routing.get());
                return;
            }

            var offline = node.getChild(INFO_TYPE_OFFLINE);
            if (offline.isPresent()) {
                handleOffline(offline.get());
                return;
            }

            if (node.hasChild(INFO_TYPE_OFFLINE_PRIORITY_COMPLETE)) {
                handleOfflinePriorityComplete();
                return;
            }

            var preview = node.getChild(INFO_TYPE_OFFLINE_PREVIEW);
            if (preview.isPresent()) {
                handleOfflinePreview(preview.get());
                return;
            }

            var tos = node.getChild(INFO_TYPE_TOS);
            if (tos.isPresent()) {
                handleTos(tos.get());
                return;
            }

            var threadMeta = node.getChild(INFO_TYPE_THREAD_META);
            if (threadMeta.isPresent()) {
                handleThreadMeta(threadMeta.get());
                return;
            }

            // WAWebHandleInfoBulletin.default: hasChild(CLIENT_EXPIRATION) gates the call to receiveClientExpirationRPC(e.getNode()).
            if (node.hasChild(INFO_TYPE_CLIENT_EXPIRATION)) {
                handleClientExpiration(node);
                return;
            }

            LOGGER.log(System.Logger.Level.WARNING,
                    "handleInfoBulletin unrecognized info bulletin {0}",
                    node.getAttributeAsString("id", "[missing-id]"));
        } catch (Throwable throwable) {
            // Cobalt catches parse failures here so that one malformed ib cannot poison the stream reader.
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
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleDirtyBits", exports = "handleDirtyBits",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleDirty(Node node) {
        // Cobalt aggregates the dirty syncd_app_state collections into a single pullWebAppState call.
        var collectionsToSync = new LinkedHashSet<SyncPatchType>();
        var allDirtyEntries = new ArrayList<Node>();
        var supportedTypes = new ArrayList<String>();
        var unsupportedTypes = new ArrayList<String>();

        for (var dirtyNode : node.getChildren(INFO_TYPE_DIRTY)) {
            allDirtyEntries.add(dirtyNode);
            var type = dirtyNode.getAttributeAsString("type", null);

            if (DIRTY_TYPE_ACCOUNT_SYNC.equals(type)) {
                supportedTypes.add(type);
                for (var child : dirtyNode.children()) {
                    var protocol = child.description();
                    if (!SUPPORTED_DIRTY_PROTOCOLS.contains(protocol)) {
                        continue;
                    }
                    // Cobalt delegates to subsystem-specific sync flags; the client and device service observe them and re-fetch lazily on the next access.
                    switch (protocol) {
                        case "devices" ->
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/devices: device list refresh needed");
                        case "picture" ->
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/picture: profile picture refresh needed");
                        case "privacy" -> {
                            whatsapp.store().setSyncedContacts(false);
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Dirty bit account_sync/privacy: privacy settings refresh needed");
                        }
                        case "blocklist" -> {
                            whatsapp.store().setSyncedContacts(false);
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Dirty bit account_sync/blocklist: block list refresh needed");
                        }
                        case "notice" ->
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/notice: notice refresh needed");
                        default -> {
                            // unreachable: SUPPORTED_DIRTY_PROTOCOLS gate above
                        }
                    }
                }
                // Cobalt marks status as dirty on account_sync so that the next caller refreshes; WA Web makes explicit imperative calls instead.
                whatsapp.store().setSyncedStatus(false);
            } else if (DIRTY_TYPE_SYNCD_APP_STATE.equals(type)) {
                supportedTypes.add(type);
                Collections.addAll(collectionsToSync, SyncPatchType.values());
            } else if (DIRTY_TYPE_GROUPS.equals(type)) {
                supportedTypes.add(type);
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Dirty bit groups: group metadata refresh needed");
            } else if (DIRTY_TYPE_NEWSLETTER_METADATA.equals(type)) {
                supportedTypes.add(type);
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Dirty bit newsletter_metadata: newsletter metadata refresh needed");
            } else {
                unsupportedTypes.add(type == null ? "" : type);
            }
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "handleDirtyBits supported={0} unsupported={1}",
                String.join(",", supportedTypes),
                String.join(",", unsupportedTypes));

        if (!collectionsToSync.isEmpty()) {
            // pullWebAppState returns the Cobalt equivalent of WA Web's `e.some(r => r.patches?.length > 0 || r.snapshot != null)` directly because it runs synchronously on a virtual thread.
            var hasAppStateChanges = whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new));
            wamService.commit(new MdAppStateDirtyBitsEventBuilder()
                    .dirtyBitsFalsePositive(!hasAppStateChanges)
                    .build());
        }

        clearDirtyBits(allDirtyEntries);
        webAppStateService.retryAllOrphanMutations();
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
     */
    @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob", exports = "clearDirtyBits",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void clearDirtyBits(List<Node> dirtyEntries) {
        if (dirtyEntries.isEmpty()) {
            return;
        }

        var cleanChildren = dirtyEntries.stream()
                .map(dirty -> new NodeBuilder()
                        .description("clean")
                        .attribute("type", dirty.getAttributeAsString("type", null))
                        .attribute("timestamp", dirty.getAttributeAsString("timestamp", null))
                        .build())
                .toList();

        try {
            whatsapp.sendNode(new NodeBuilder()
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
                            .orElse(""));
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "clearDirtyBits: failed with error");
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
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleRoutingInfo", exports = "handleRoutingInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleRouting(Node routingNode) {
        var edgeRouting = routingNode.getChild("routing_info")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        var domain = routingNode.getChild("dns_domain")
                .flatMap(Node::toContentString)
                .orElse(null);
        // Validates against the DOMAINS map; unknown values fall back to null.
        if (domain != null && !"fb".equals(domain) && !"sl".equals(domain)) {
            domain = null;
        }
        if (domain == null) {
            domain = whatsapp.store().routingDomain().orElse(DEFAULT_ROUTING_DOMAIN);
        }
        whatsapp.store().setRoutingInfo(edgeRouting);
        whatsapp.store().setRoutingDomain(domain);
        LOGGER.log(System.Logger.Level.DEBUG,
                "handleInfoBulletin setting and domain: {0} and edgeRouting: {1} bytes",
                domain, edgeRouting == null ? 0 : edgeRouting.length);
    }

    /**
     * Handles the {@code offline} info bulletin that announces the total
     * number of queued offline messages the server has delivered, closing
     * out the offline resume state machine.
     *
     * <p>Mirrors WA Web's {@code OfflineMessageHandler.processOfflineIb},
     * which calls into {@code processOfflineSessionComplete} on the active
     * resume manager. The Cobalt store records the resume state on a
     * single {@link WhatsAppClientOfflineResumeState} field. The transition
     * follows the WA Web branching exactly:
     * <ul>
     *   <li>If the state is already {@link WhatsAppClientOfflineResumeState#COMPLETE},
     *       the bulletin is acknowledged for telemetry but no further
     *       work is performed. WA Web's UI bookkeeping, QPL flow markers
     *       and reporter commits have no Cobalt analogue.</li>
     *   <li>If the state is {@link WhatsAppClientOfflineResumeState#RESUME_WITH_OPEN_TAB},
     *       the live-tab disconnect path runs the pending device sync
     *       inline (mirroring WA Web's {@code yield doPendingDeviceSync()})
     *       and then transitions to {@link WhatsAppClientOfflineResumeState#COMPLETE}.</li>
     *   <li>Otherwise the post-restart path transitions to
     *       {@link WhatsAppClientOfflineResumeState#COMPLETE} immediately
     *       and schedules the pending device sync after
     *       {@link WhatsAppClientOfflineResumeState#OFFLINE_DEVICE_SYNC_DELAY}
     *       on a virtual thread, mirroring WA Web's
     *       {@code self.setTimeout(doPendingDeviceSync, OFFLINE_DEVICE_SYNC_DELAY)}.</li>
     * </ul>
     *
     * <p>Independent of the resume transition, Cobalt always flushes the
     * accumulated offline {@code server_sync} notification counts as a WAM
     * event (mirroring WA Web's {@code reportOfflineNotifications}) and,
     * when the count is zero, drives a best-effort orphan-mutation retry
     * to pick up app-state changes that arrived just before connect.
     *
     * @param offlineNode the {@code offline} child node, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "processOfflineIb",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "OfflineMessageHandlerImpl",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOffline(Node offlineNode) {
        var count = offlineNode.getAttributeAsInt("count", 0);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline bulletin with count={0}", count);
        offlineNotificationsReporter.report();
        if (count == 0) {
            // Cobalt retries orphan mutations when the backlog is empty; WA Web only clears the dedup cache here.
            webAppStateService.retryAllOrphanMutations();
        }

        var store = whatsapp.store();
        var current = store.offlineResumeState();
        if (current == WhatsAppClientOfflineResumeState.COMPLETE) {
            // WAWebBlockingOfflineResumeManager.processOfflineSessionComplete: early return when state === COMPLETE; Cobalt skips the WAM commit since reporting is centralised in WamService.
            return;
        }

        if (current == WhatsAppClientOfflineResumeState.RESUME_WITH_OPEN_TAB) {
            // WAWebBlockingOfflineResumeManager.processOfflineSessionComplete: yield doPendingDeviceSync() then transition to COMPLETE on the live-tab reconnect path.
            try {
                deviceService.retryPendingSyncs();
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "doPendingDeviceSync failed during open-tab resume completion: {0}",
                        throwable.getMessage());
            }
            store.setOfflineResumeState(WhatsAppClientOfflineResumeState.COMPLETE);
            firstOfflinePreviewMillis = 0L;
            return;
        }

        // WAWebBlockingOfflineResumeManager.processOfflineSessionComplete: post-restart path transitions to COMPLETE then schedules doPendingDeviceSync after OFFLINE_DEVICE_SYNC_DELAY.
        store.setOfflineResumeState(WhatsAppClientOfflineResumeState.COMPLETE);
        firstOfflinePreviewMillis = 0L;
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(WhatsAppClientOfflineResumeState.OFFLINE_DEVICE_SYNC_DELAY);
                deviceService.retryPendingSyncs();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "doPendingDeviceSync failed after offline resume completion: {0}",
                        throwable.getMessage());
            }
        });
    }

    /**
     * Handles the {@code offline_preview} info bulletin that carries a
     * breakdown of the pending offline backlog by stanza type, opening or
     * advancing the offline resume state machine.
     *
     * <p>Mirrors WA Web's {@code OfflineMessageHandler.processOfflinePreviewIb},
     * which delegates to {@code processOfflinePreview} on the active
     * resume manager. The transition mirrors WA Web's three-way branch on
     * the current state:
     * <ul>
     *   <li>If {@code isResumeFromRestartComplete()} (the state is past
     *       {@link WhatsAppClientOfflineResumeState#RESUME_ON_RESTART}),
     *       a live socket disconnect is in progress; the state moves to
     *       {@link WhatsAppClientOfflineResumeState#RESUME_WITH_OPEN_TAB}.
     *       WA Web additionally throttles the chat-sort listener and may
     *       refresh the window when {@code exceedResumeWithOpenTabLimit}
     *       trips; both are UI/JS-runtime concerns with no Cobalt
     *       analogue and are intentionally skipped.</li>
     *   <li>If the current state is {@link WhatsAppClientOfflineResumeState#INIT},
     *       this is the first preview after a cold start; the state
     *       moves to {@link WhatsAppClientOfflineResumeState#RESUME_ON_RESTART}
     *       and the preview timestamp is recorded for the debounce
     *       window.</li>
     *   <li>Otherwise the state is already {@code RESUME_ON_RESTART} and
     *       repeated previews are gated by the
     *       {@link WhatsAppClientOfflineResumeState#OFFLINE_PREVIEW_PERIOD_MS}
     *       debounce window: previews arriving inside the window are
     *       accepted as cumulative updates and previews arriving outside
     *       are rejected and logged, matching WA Web's
     *       {@code "Accept multiple offline preview ibs"} /
     *       {@code "Reject multiple offline preview ibs"} branches.</li>
     * </ul>
     *
     * @param previewNode the {@code offline_preview} child node, never
     *                    {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "processOfflinePreviewIb",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "OfflineMessageHandlerImpl",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOfflinePreview(Node previewNode) {
        var messageCount = previewNode.getAttributeAsInt("message", 0);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline preview bulletin count={0} message={1} receipt={2} notification={3} call={4}",
                previewNode.getAttributeAsInt("count", 0),
                messageCount,
                previewNode.getAttributeAsInt("receipt", 0),
                previewNode.getAttributeAsInt("notification", 0),
                previewNode.getAttributeAsInt("call", 0));

        var store = whatsapp.store();
        if (store.isResumeFromRestartComplete()) {
            // WAWebBlockingOfflineResumeManager.processOfflinePreview: live socket disconnect path; UI-only side effects (chat-sort listener throttle, exceedResumeWithOpenTabLimit refresh) are skipped on the headless Cobalt client.
            store.setOfflineResumeState(WhatsAppClientOfflineResumeState.RESUME_WITH_OPEN_TAB);
            return;
        }

        var current = store.offlineResumeState();
        if (current == WhatsAppClientOfflineResumeState.INIT) {
            // WAWebBlockingOfflineResumeManager.processOfflinePreview: first preview after cold start drives INIT -> RESUME_ON_RESTART; the preview timestamp gates the debounce window.
            firstOfflinePreviewMillis = System.currentTimeMillis();
            store.setOfflineResumeState(WhatsAppClientOfflineResumeState.RESUME_ON_RESTART);
            return;
        }

        // WAWebBlockingOfflineResumeManager.processOfflinePreview: state is RESUME_ON_RESTART and another offline_preview arrived; accept cumulative updates inside OFFLINE_PREVIEW_PERIOD_MS, reject otherwise.
        var firstMillis = firstOfflinePreviewMillis;
        if (firstMillis == 0L) {
            return;
        }
        var delay = System.currentTimeMillis() - firstMillis;
        if (delay < WhatsAppClientOfflineResumeState.OFFLINE_PREVIEW_PERIOD_MS) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Accept multiple offline preview ibs during offline resume, delay={0} message={1}",
                    delay, messageCount);
        } else {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Reject multiple offline preview ibs during offline resume, delay={0}",
                    delay);
        }
    }

    /**
     * Handles the {@code priority_offline_complete} info bulletin.
     *
     * <p>This bulletin signals that every high-priority offline stanza
     * queued for the client has been delivered. WA Web acknowledges the
     * bulletin without performing any further work; Cobalt additionally
     * drives a best-effort orphan-mutation retry because dependencies on
     * peer data may now be satisfied.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOfflinePriorityComplete() {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received priority_offline_complete bulletin");
        // Cobalt retries orphan app-state mutations when priority offline delivery ends.
        webAppStateService.retryAllOrphanMutations();
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
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleTos(Node tosNode) {
        var notices = tosNode.getChildren("notice").stream()
                .map(entry -> entry.getAttributeAsString("id", null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        whatsapp.store().setTosNoticeIds(notices);
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
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleThreadMeta(Node threadMetaNode) {
        var itemCount = 0;
        for (var item : threadMetaNode.getChildren("item")) {
            var from = item.getAttributeAsJid("from").orElse(null);
            var timestamp = item.getAttributeAsLong("t", (Long) null);
            if (from == null || timestamp == null) {
                continue;
            }
            itemCount++;
            // Cobalt has no offline thread metadata store and logs per entry instead of calling WAWebThreadMetadata.setOfflineThreadMeta.
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
     * <p>The full {@code <ib>} envelope is parsed via the typed
     * {@link SmaxClientExpirationResponse} parser, mirroring WA Web's
     * {@code receiveClientExpirationRPC(e.getNode())} dispatch. A parse
     * failure is logged and the bulletin is dropped, matching WA Web's
     * {@code SmaxParsingFailure} which is caught by the surrounding
     * dispatcher.
     *
     * <p>When the parsed {@code t} attribute is absent the stored
     * override is cleared. Otherwise the new timestamp is normalised
     * through the int32 clamp that mirrors {@code WATimeUtils.castToUnixTime},
     * compared against any existing override: if the new value is not
     * earlier than the current override the update is ignored (the
     * server never extends the expiration window). Accepted values are
     * clamped to at least {@link #CLIENT_EXPIRATION_MIN_FLOOR_SECONDS}
     * in the future to give the user a reasonable grace period.
     *
     * @param node the {@code <ib>} envelope, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxClientExpirationClientExpirationRPC",
            exports = "receiveClientExpirationRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHandleServerClientExpiration",
            exports = "handleServerClientExpiration", adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleClientExpiration(Node node) {
        // WASmaxClientExpirationClientExpirationRPC.receiveClientExpirationRPC: typed parse of <ib><client_expiration t?/></ib>; failure surfaces as Optional.empty (Cobalt) vs SmaxParsingFailure throw (WA Web).
        var parsed = SmaxClientExpirationResponse.of(node).orElse(null);
        if (!(parsed instanceof SmaxClientExpirationResponse.Inbound inbound)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to parse client_expiration bulletin {0}",
                    node.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        // WAWebHandleInfoBulletin.default: c = u != null ? WATimeUtils.castToUnixTime(u) : null.
        var newExpiration = inbound.clientExpirationT()
                .map(InfoBulletinStreamHandler::castToUnixTime)
                .orElse(null);
        if (newExpiration == null) {
            whatsapp.store().setClientExpiration(null);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cleared client expiration override");
            return;
        }

        var existingExpiration = whatsapp.store().clientExpiration().orElse(null);

        if (existingExpiration != null && newExpiration >= existingExpiration.getEpochSecond()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring client expiration {0}: not earlier than existing {1}",
                    newExpiration, existingExpiration);
            return;
        }

        // Cobalt has no WAWebUpdaterHardExpireTime equivalent so WA Web's hard-cap check is skipped; the final value is max(minFloor, newExpiration).
        var minFloor = Instant.now().plusSeconds(CLIENT_EXPIRATION_MIN_FLOOR_SECONDS);

        var clampedExpiration = newExpiration < minFloor.getEpochSecond()
                ? minFloor
                : Instant.ofEpochSecond(newExpiration);

        whatsapp.store().setClientExpiration(clampedExpiration);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received client expiration bulletin, clamped to {0}", clampedExpiration);
    }

    /**
     * Clamps a Unix-second timestamp to the signed 32-bit integer range,
     * mirroring {@code WATimeUtils.castToUnixTime} which is invoked on
     * the parsed {@code clientExpirationT} before downstream handling.
     *
     * <p>WA Web's {@code k(e)} truncates with {@code (e | 0)} then applies
     * {@code Math.max(b, Math.min(t, C))} with
     * {@code b = (1 << 31) + 1 = -2147483647} and
     * {@code C = ~(1 << 31) = 2147483647}. Cobalt has no native int32
     * coercion of a {@code long}, so the truncation is expressed as
     * {@code (long) (int) value}; the surrounding clamp is then a no-op
     * within int32 but preserved for documentation parity.
     *
     * @param value the raw timestamp in seconds; never {@code null}
     * @return the clamped timestamp; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WATimeUtils", exports = "castToUnixTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static long castToUnixTime(long value) {
        return Math.max(-2_147_483_647L, Math.min((long) (int) value, 2_147_483_647L));
    }
}
