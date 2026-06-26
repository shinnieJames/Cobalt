package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedTosNoticesChangedListener;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientOfflineResumeState;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.stanza.smax.clientexpiration.SmaxClientExpirationResponse;
import com.github.auties00.cobalt.stream.NodeStreamService;
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
 * Handles {@code <ib>} (info bulletin) stanzas, the server's catch-all control-plane channel for asynchronous
 * notifications that do not fit any other stanza tag.
 *
 * <p>The handler is registered under the {@code "ib"} tag inside {@link NodeStreamService} and inspects child tags in a
 * fixed priority order: {@code dirty} dirty-bit bundles that drive re-fetch of out-of-date subsystems,
 * {@code edge_routing} updates that steer the next reconnect, {@code offline} backlog counters that close the
 * offline-resume state machine, {@code priority_offline_complete} markers, {@code offline_preview} pre-delivery
 * snapshots, {@code tos} Terms-of-Service notice lists, {@code thread_metadata} per-thread offline timestamps and
 * {@code client_expiration} server-mandated client expiration overrides. The first matching child drives dispatch;
 * later children are ignored. The offline-resume side effects surface through {@link LinkedWhatsAppClient}.
 *
 * @implNote This implementation collapses WA Web's two-phase parse-then-dispatch flow into a single ordered
 * {@code if/else} chain, one private method per branch, and wraps the dispatch in a {@link Throwable} catch so a
 * malformed bulletin cannot propagate up through the socket reader.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleInfoBulletin")
@WhatsAppWebModule(moduleName = "WAWebHandleDirtyBits")
@WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
@WhatsAppWebModule(moduleName = "WAWebHandleRoutingInfo")
@WhatsAppWebModule(moduleName = "WAWebHandleServerClientExpiration")
@WhatsAppWebModule(moduleName = "WASmaxClientExpirationClientExpirationRPC")
public final class InfoBulletinStreamHandler extends SocketStreamHandler.Concurrent {
    /**
     * The system logger used for diagnostic output during info bulletin processing.
     */
    private static final System.Logger LOGGER =
            System.getLogger(InfoBulletinStreamHandler.class.getName());

    /**
     * The child tag carrying dirty-bit notifications.
     */
    private static final String INFO_TYPE_DIRTY = "dirty";

    /**
     * The child tag carrying an {@code edge_routing} routing-info bundle.
     */
    private static final String INFO_TYPE_ROUTING = "edge_routing";

    /**
     * The child tag carrying the offline-delivery completion counter.
     */
    private static final String INFO_TYPE_OFFLINE = "offline";

    /**
     * The child tag carrying the priority-offline completion marker.
     */
    private static final String INFO_TYPE_OFFLINE_PRIORITY_COMPLETE = "priority_offline_complete";

    /**
     * The child tag carrying categorised offline message counts pushed before the backlog drains.
     */
    private static final String INFO_TYPE_OFFLINE_PREVIEW = "offline_preview";

    /**
     * The child tag carrying pending Terms-of-Service notices.
     */
    private static final String INFO_TYPE_TOS = "tos";

    /**
     * The child tag carrying per-thread offline timestamps.
     */
    private static final String INFO_TYPE_THREAD_META = "thread_metadata";

    /**
     * The child tag carrying the server-mandated client expiration override.
     */
    private static final String INFO_TYPE_CLIENT_EXPIRATION = "client_expiration";

    /**
     * The {@code dirty.type} value that triggers an app-state syncd collection pull.
     */
    private static final String DIRTY_TYPE_SYNCD_APP_STATE = "syncd_app_state";

    /**
     * The {@code dirty.type} value that triggers account-level subsystem refreshes ({@code devices}, {@code picture},
     * {@code privacy}, {@code blocklist}, {@code notice}, {@code optoutlist}).
     */
    private static final String DIRTY_TYPE_ACCOUNT_SYNC = "account_sync";

    /**
     * The {@code dirty.type} value that triggers a deferred group metadata refresh after offline delivery ends.
     */
    private static final String DIRTY_TYPE_GROUPS = "groups";

    /**
     * The {@code dirty.type} value that triggers a deferred newsletter metadata refresh after offline delivery ends.
     */
    private static final String DIRTY_TYPE_NEWSLETTER_METADATA = "newsletter_metadata";

    /**
     * The set of supported {@code account_sync} child protocol names that map to a Cobalt-side refresh action.
     *
     * <p>Any value outside this set is ignored when iterating the children of an {@code account_sync} dirty entry.
     */
    private static final Set<String> SUPPORTED_DIRTY_PROTOCOLS = Set.of(
            "devices", "picture", "privacy", "blocklist", "notice"
    );

    /**
     * The fallback routing domain applied when the stanza omits {@code dns_domain} and no domain is already stored.
     */
    private static final String DEFAULT_ROUTING_DOMAIN = "fb";

    /**
     * The lower bound in seconds applied to an accepted client expiration override.
     *
     * @implNote This value is three days expressed in seconds ({@code 3 * 86400}); the server cannot push an
     * expiration that fires sooner than this many seconds from now.
     */
    private static final long CLIENT_EXPIRATION_MIN_FLOOR_SECONDS = 3L * 86_400L;

    /**
     * The {@link LinkedWhatsAppClient} used for store access, outbound stanza dispatch and delegated service calls.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * The {@link WebAppStateService} used to retry orphan app-state mutations whenever a bulletin signals that
     * previously missing referents may now exist.
     */
    private final WebAppStateService webAppStateService;

    /**
     * The shared {@link OfflineNotificationsReporter} that accumulates per-collection offline {@code server_sync}
     * notification counts and is flushed as a WAM event when the offline bulletin arrives.
     */
    private final OfflineNotificationsReporter offlineNotificationsReporter;

    /**
     * The {@link WamService} used to commit the dirty-bits false-positive event after an app-state pull resolves.
     */
    private final WamService wamService;

    /**
     * The {@link DeviceService} used to drive the pending-device-sync retry that closes the offline-resume state
     * machine when the {@code offline} bulletin arrives.
     */
    private final DeviceService deviceService;

    /**
     * The epoch-millis timestamp of the {@code offline_preview} bulletin that drove the current
     * {@link LinkedWhatsAppClientOfflineResumeState#RESUME_ON_RESTART} transition, or {@code 0L} when no preview has been
     * observed since the last completion.
     *
     * <p>Gates repeated previews against the {@link LinkedWhatsAppClientOfflineResumeState#OFFLINE_PREVIEW_PERIOD_MS}
     * debounce window: previews inside the window are accepted as cumulative updates, previews outside the window are
     * rejected as noise.
     */
    private volatile long firstOfflinePreviewMillis;

    /**
     * Constructs a new info bulletin handler bound to the given client, web app-state service, shared reporter, WAM
     * service and device service.
     *
     * @param whatsapp                     the {@link LinkedWhatsAppClient}; must not be {@code null}
     * @param webAppStateService           the {@link WebAppStateService} used for orphan-mutation retries; must not be
     *                                     {@code null}
     * @param offlineNotificationsReporter the shared reporter flushed when the offline bulletin arrives; must not be
     *                                     {@code null}
     * @param wamService                   the {@link WamService} used to commit the dirty-bits event; must not be
     *                                     {@code null}
     * @param deviceService                the {@link DeviceService} used to run the post-resume pending device sync;
     *                                     must not be {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InfoBulletinStreamHandler(LinkedWhatsAppClient whatsapp, WebAppStateService webAppStateService, OfflineNotificationsReporter offlineNotificationsReporter, WamService wamService, DeviceService deviceService) {
        this.whatsapp = whatsapp;
        this.webAppStateService = webAppStateService;
        this.offlineNotificationsReporter = offlineNotificationsReporter;
        this.wamService = wamService;
        this.deviceService = deviceService;
        this.firstOfflinePreviewMillis = 0L;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Dispatches the {@code <ib>} stanza to the first recognised info-type branch in priority order: {@code dirty},
     * {@code edge_routing}, {@code offline}, {@code priority_offline_complete}, {@code offline_preview}, {@code tos},
     * {@code thread_metadata}, then {@code client_expiration}. A stanza whose children contain no recognised info type
     * is logged as a warning.
     *
     * @implNote This implementation wraps the dispatch in a {@link Throwable} catch so a malformed bulletin cannot
     * poison the socket reader.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Stanza stanza) {
        try {
            if (stanza.hasChild(INFO_TYPE_DIRTY)) {
                handleDirty(stanza);
                return;
            }

            var routing = stanza.getChild(INFO_TYPE_ROUTING);
            if (routing.isPresent()) {
                handleRouting(routing.get());
                return;
            }

            var offline = stanza.getChild(INFO_TYPE_OFFLINE);
            if (offline.isPresent()) {
                handleOffline(offline.get());
                return;
            }

            if (stanza.hasChild(INFO_TYPE_OFFLINE_PRIORITY_COMPLETE)) {
                handleOfflinePriorityComplete();
                return;
            }

            var preview = stanza.getChild(INFO_TYPE_OFFLINE_PREVIEW);
            if (preview.isPresent()) {
                handleOfflinePreview(preview.get());
                return;
            }

            var tos = stanza.getChild(INFO_TYPE_TOS);
            if (tos.isPresent()) {
                handleTos(tos.get());
                return;
            }

            var threadMeta = stanza.getChild(INFO_TYPE_THREAD_META);
            if (threadMeta.isPresent()) {
                handleThreadMeta(threadMeta.get());
                return;
            }

            if (stanza.hasChild(INFO_TYPE_CLIENT_EXPIRATION)) {
                handleClientExpiration(stanza);
                return;
            }

            LOGGER.log(System.Logger.Level.WARNING,
                    "handleInfoBulletin unrecognized info bulletin {0}",
                    stanza.getAttributeAsString("id", "[missing-id]"));
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle info bulletin {0}: {1}",
                    stanza.getAttributeAsString("id", "[missing-id]"),
                    throwable.getMessage());
        }
    }

    /**
     * Processes every {@code <dirty/>} child of the {@code <ib>} stanza and acknowledges the batch back to the server.
     *
     * <p>For each dirty entry the {@code type} attribute selects the action: {@code syncd_app_state} flags every
     * collection for the next app-state pull, {@code account_sync} iterates the supported protocol children and flags
     * the corresponding store sync booleans, and {@code groups} and {@code newsletter_metadata} are logged for the
     * deferred metadata refresh path. Entries whose {@code type} is unsupported are still included in the ack batch
     * sent to the server.
     *
     * @implNote This implementation aggregates the {@code syncd_app_state} collections into a single
     * {@link LinkedWhatsAppClient#pullWebAppState(SyncPatchType...)} call rather than firing one mark-for-sync per entry, and
     * commits the dirty-bits WAM event inline based on the pull's return value rather than via a sync-completed
     * subscription. Account-sync subsystem refreshes are deferred to the next caller through the
     * {@code setSyncedXxx(false)} flags rather than issued imperatively.
     *
     * @param stanza the parent {@code <ib>} stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleDirtyBits", exports = "handleDirtyBits",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleDirty(Stanza stanza) {
        var collectionsToSync = new LinkedHashSet<SyncPatchType>();
        var allDirtyEntries = new ArrayList<Stanza>();
        var supportedTypes = new ArrayList<String>();
        var unsupportedTypes = new ArrayList<String>();
        var syncOwnDevices = false;

        for (var dirtyNode : stanza.getChildren(INFO_TYPE_DIRTY)) {
            allDirtyEntries.add(dirtyNode);
            var type = dirtyNode.getAttributeAsString("type", null);

            if (DIRTY_TYPE_ACCOUNT_SYNC.equals(type)) {
                supportedTypes.add(type);
                for (var child : dirtyNode.children()) {
                    var protocol = child.description();
                    if (!SUPPORTED_DIRTY_PROTOCOLS.contains(protocol)) {
                        continue;
                    }
                    switch (protocol) {
                        case "devices" -> {
                            syncOwnDevices = true;
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Dirty bit account_sync/devices: syncing own device list");
                        }
                        case "picture" ->
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/picture: profile picture refresh needed");
                        case "privacy" -> {
                            whatsapp.store().syncStore().setSyncedContacts(false);
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Dirty bit account_sync/privacy: privacy settings refresh needed");
                        }
                        case "blocklist" -> {
                            whatsapp.store().syncStore().setSyncedContacts(false);
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Dirty bit account_sync/blocklist: block list refresh needed");
                        }
                        case "notice" ->
                                LOGGER.log(System.Logger.Level.DEBUG,
                                        "Dirty bit account_sync/notice: notice refresh needed");
                        default -> {
                        }
                    }
                }
                whatsapp.store().syncStore().setSyncedStatus(false);
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
            var hasAppStateChanges = whatsapp.pullWebAppState(collectionsToSync.toArray(SyncPatchType[]::new));
            wamService.commit(new MdAppStateDirtyBitsEventBuilder()
                    .dirtyBitsFalsePositive(!hasAppStateChanges)
                    .build());
        }

        if (syncOwnDevices) {
            deviceService.syncMyDeviceList();
        }

        clearDirtyBits(allDirtyEntries);
        webAppStateService.retryAllOrphanMutations();
    }

    /**
     * Sends a {@code <iq type="set" xmlns="urn:xmpp:whatsapp:dirty">} containing one {@code <clean/>} child per
     * processed dirty entry, acknowledging the bits back to the server so they can be cleared.
     *
     * <p>The IQ preserves each entry's original {@code type} and {@code timestamp} attributes so the server can match
     * the ack to the dirty record. An empty batch skips the IQ.
     *
     * @implNote This implementation swallows transport failures at {@code WARNING} because the server retransmits the
     * same dirty bits on the next connection; reraising would tear down the in-progress dispatch on the {@code <ib>}
     * stanza for no recoverable benefit.
     *
     * @param dirtyEntries the dirty entries to acknowledge; must not be {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob", exports = "clearDirtyBits",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void clearDirtyBits(List<Stanza> dirtyEntries) {
        if (dirtyEntries.isEmpty()) {
            return;
        }

        var cleanChildren = dirtyEntries.stream()
                .map(dirty -> new StanzaBuilder()
                        .description("clean")
                        .attribute("type", dirty.getAttributeAsString("type", null))
                        .attribute("timestamp", dirty.getAttributeAsString("timestamp", null))
                        .build())
                .toList();

        try {
            whatsapp.sendNode(new StanzaBuilder()
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
     * Persists the {@code edge_routing} routing-info payload and DNS domain in the store so the next reconnect uses
     * them.
     *
     * <p>The {@code <edge_routing/>} child carries a mandatory {@code <routing_info>} byte payload (the steering blob
     * the client sends in the noise handshake) and an optional {@code <dns_domain>} enum selecting between the
     * {@code fb} and {@code sl} domains. When {@code dns_domain} is absent or unknown, the existing stored domain is
     * reused, falling back to {@link #DEFAULT_ROUTING_DOMAIN} when neither is set.
     *
     * @implNote This implementation rejects domain values outside {@code {"fb", "sl"}} inline; the domain decoder
     * cannot produce other values.
     *
     * @param routingStanza the {@code <edge_routing/>} child stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleRoutingInfo", exports = "handleRoutingInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleRouting(Stanza routingStanza) {
        var edgeRouting = routingStanza.getChild("routing_info")
                .flatMap(Stanza::toContentBytes)
                .orElse(null);
        var domain = routingStanza.getChild("dns_domain")
                .flatMap(Stanza::toContentString)
                .orElse(null);
        if (domain != null && !"fb".equals(domain) && !"sl".equals(domain)) {
            domain = null;
        }
        if (domain == null) {
            domain = whatsapp.store().connectionStore().routingDomain().orElse(DEFAULT_ROUTING_DOMAIN);
        }
        whatsapp.store().connectionStore().setRoutingInfo(edgeRouting);
        whatsapp.store().connectionStore().setRoutingDomain(domain);
        LOGGER.log(System.Logger.Level.DEBUG,
                "handleInfoBulletin setting and domain: {0} and edgeRouting: {1} bytes",
                domain, edgeRouting == null ? 0 : edgeRouting.length);
    }

    /**
     * Closes the offline-resume state machine when the server announces via the {@code <offline/>} child that the
     * queued backlog has finished delivering.
     *
     * <p>The {@code count} attribute is the total number of offline messages the server has delivered. The transition
     * follows three branches:
     * <ul>
     *   <li>If the state is already {@link LinkedWhatsAppClientOfflineResumeState#COMPLETE}, the bulletin is acknowledged for
     *       telemetry but no further work runs.</li>
     *   <li>If the state is {@link LinkedWhatsAppClientOfflineResumeState#RESUME_WITH_OPEN_TAB}, the live-tab disconnect path
     *       runs the pending device sync inline and then transitions to
     *       {@link LinkedWhatsAppClientOfflineResumeState#COMPLETE}.</li>
     *   <li>Otherwise the post-restart path transitions to {@link LinkedWhatsAppClientOfflineResumeState#COMPLETE} immediately
     *       and schedules the pending device sync after
     *       {@link LinkedWhatsAppClientOfflineResumeState#OFFLINE_DEVICE_SYNC_DELAY}.</li>
     * </ul>
     *
     * @implNote This implementation always flushes the accumulated offline {@code server_sync} notification counts
     * through {@link OfflineNotificationsReporter#report()} and, when {@code count == 0}, drives a best-effort
     * {@link WebAppStateService#retryAllOrphanMutations()} retry to pick up app-state changes that landed just before
     * connect. WA Web's UI bookkeeping has no Cobalt analogue. The scheduled device sync runs on a fresh virtual
     * thread; an {@link InterruptedException} during the delay sets the interrupt flag and returns quietly.
     *
     * @param offlineStanza the {@code <offline/>} child stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "processOfflineIb",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "OfflineMessageHandlerImpl",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOffline(Stanza offlineStanza) {
        var count = offlineStanza.getAttributeAsInt("count", 0);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline bulletin with count={0}", count);
        offlineNotificationsReporter.report();
        if (count == 0) {
            webAppStateService.retryAllOrphanMutations();
        }

        var store = whatsapp.store();
        var current = store.connectionStore().offlineResumeState();
        if (current == LinkedWhatsAppClientOfflineResumeState.COMPLETE) {
            return;
        }

        if (current == LinkedWhatsAppClientOfflineResumeState.RESUME_WITH_OPEN_TAB) {
            try {
                deviceService.retryPendingSyncs();
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "doPendingDeviceSync failed during open-tab resume completion: {0}",
                        throwable.getMessage());
            }
            store.connectionStore().setOfflineResumeState(LinkedWhatsAppClientOfflineResumeState.COMPLETE);
            firstOfflinePreviewMillis = 0L;
            return;
        }

        store.connectionStore().setOfflineResumeState(LinkedWhatsAppClientOfflineResumeState.COMPLETE);
        firstOfflinePreviewMillis = 0L;
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(LinkedWhatsAppClientOfflineResumeState.OFFLINE_DEVICE_SYNC_DELAY);
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
     * Opens or advances the offline-resume state machine in response to the {@code <offline_preview/>} pre-delivery
     * snapshot of the pending backlog.
     *
     * <p>The transition follows three branches:
     * <ul>
     *   <li>If the resume-from-restart phase is already complete (state is past
     *       {@link LinkedWhatsAppClientOfflineResumeState#RESUME_ON_RESTART}), a live socket disconnect is in progress; the
     *       state moves to {@link LinkedWhatsAppClientOfflineResumeState#RESUME_WITH_OPEN_TAB}.</li>
     *   <li>If the current state is {@link LinkedWhatsAppClientOfflineResumeState#INIT}, this is the first preview after a
     *       cold start; the state moves to {@link LinkedWhatsAppClientOfflineResumeState#RESUME_ON_RESTART} and
     *       {@link #firstOfflinePreviewMillis} is set for the debounce window.</li>
     *   <li>Otherwise the state is already {@link LinkedWhatsAppClientOfflineResumeState#RESUME_ON_RESTART} and repeated
     *       previews are gated by {@link LinkedWhatsAppClientOfflineResumeState#OFFLINE_PREVIEW_PERIOD_MS}: previews inside
     *       the window are accepted as cumulative updates, previews outside the window are rejected and logged.</li>
     * </ul>
     *
     * @implNote This implementation skips WA Web's chat-sort listener throttle and the open-tab-limit refresh path
     * because both are UI-only side effects with no Cobalt analogue on the headless client.
     *
     * @param previewStanza the {@code <offline_preview/>} child stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "processOfflinePreviewIb",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebOfflineHandler",
            exports = "OfflineMessageHandlerImpl",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOfflinePreview(Stanza previewStanza) {
        var messageCount = previewStanza.getAttributeAsInt("message", 0);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received offline preview bulletin count={0} message={1} receipt={2} notification={3} call={4}",
                previewStanza.getAttributeAsInt("count", 0),
                messageCount,
                previewStanza.getAttributeAsInt("receipt", 0),
                previewStanza.getAttributeAsInt("notification", 0),
                previewStanza.getAttributeAsInt("call", 0));

        var store = whatsapp.store();
        if (store.connectionStore().isResumeFromRestartComplete()) {
            store.connectionStore().setOfflineResumeState(LinkedWhatsAppClientOfflineResumeState.RESUME_WITH_OPEN_TAB);
            return;
        }

        var current = store.connectionStore().offlineResumeState();
        if (current == LinkedWhatsAppClientOfflineResumeState.INIT) {
            firstOfflinePreviewMillis = System.currentTimeMillis();
            store.connectionStore().setOfflineResumeState(LinkedWhatsAppClientOfflineResumeState.RESUME_ON_RESTART);
            return;
        }

        var firstMillis = firstOfflinePreviewMillis;
        if (firstMillis == 0L) {
            return;
        }
        var delay = System.currentTimeMillis() - firstMillis;
        if (delay < LinkedWhatsAppClientOfflineResumeState.OFFLINE_PREVIEW_PERIOD_MS) {
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
     * Handles the {@code <priority_offline_complete/>} bulletin that announces every high-priority offline stanza has
     * been delivered.
     *
     * <p>Drives a best-effort {@link WebAppStateService#retryAllOrphanMutations()} retry because peer dependencies for
     * queued orphans may now be satisfied.
     *
     * @implNote WA Web only logs on this branch because its resume manager does not track priority completion; Cobalt
     * additionally retries orphan mutations.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOfflinePriorityComplete() {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received priority_offline_complete bulletin");
        webAppStateService.retryAllOrphanMutations();
    }

    /**
     * Stores the set of pending Terms-of-Service notice IDs carried by the {@code <tos/>} child of the bulletin.
     *
     * <p>The raw notice IDs are recorded on the store so embedder code can render or acknowledge each notice at the
     * appropriate time.
     *
     * @implNote This implementation does not run the dirty-bit-driven consent-collection pipeline that WA Web fires
     * from the {@code account_sync/notice} branch; the IDs are pure metadata and the embedder owns the consent flow.
     *
     * @param tosStanza the {@code <tos/>} child stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleTos(Stanza tosStanza) {
        var notices = tosStanza.getChildren("notice").stream()
                .map(entry -> entry.getAttributeAsString("id", null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        whatsapp.store().settingsStore().setTosNotices(notices);
        var snapshot = Set.copyOf(notices);
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedTosNoticesChangedListener typed) {
                Thread.startVirtualThread(() -> typed.onTosNoticesChanged(whatsapp, snapshot));
            }
        }
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received TOS bulletin notices={0}", notices);
    }

    /**
     * Parses the {@code <thread_metadata/>} bulletin and logs each per-thread offline timestamp without persisting it.
     *
     * <p>The per-thread last-seen-offline timestamps drive the unread-divider position in WA Web's chat view. Cobalt
     * has no equivalent UI state and so the payload is parsed for validation only; each {@code <item/>} carries a
     * {@code from} JID plus a {@code t} timestamp.
     *
     * @param threadMetaStanza the {@code <thread_metadata/>} child stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleInfoBulletin", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleThreadMeta(Stanza threadMetaStanza) {
        var itemCount = 0;
        for (var item : threadMetaStanza.getChildren("item")) {
            var from = item.getAttributeAsJid("from").orElse(null);
            var timestamp = item.getAttributeAsLong("t", (Long) null);
            if (from == null || timestamp == null) {
                continue;
            }
            itemCount++;
            LOGGER.log(System.Logger.Level.DEBUG,
                    "thread_metadata item chat={0} timestamp={1}",
                    from, timestamp);
        }
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received thread_metadata bulletin with {0} items", itemCount);
    }

    /**
     * Applies or clears the server-mandated client expiration override carried by the {@code <client_expiration/>}
     * bulletin.
     *
     * <p>The {@code <ib>} envelope is parsed via the typed {@link SmaxClientExpirationResponse} parser. When the parsed
     * {@code t} attribute is absent the stored override is cleared. Otherwise the new timestamp is normalised through
     * {@link #castToUnixTime(long)} and compared to any existing override: if the new value is not earlier than the
     * current override the update is ignored, because the server never extends the expiration window. Accepted values
     * are floored to at least {@link #CLIENT_EXPIRATION_MIN_FLOOR_SECONDS} in the future. Parse failures are logged at
     * {@code WARNING}.
     *
     * @implNote This implementation has no hard-expire build constant, so the upper-bound clamp is skipped; the final
     * value is {@code max(minFloor, newExpiration)}.
     *
     * @param stanza the {@code <ib>} envelope; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxClientExpirationClientExpirationRPC",
            exports = "receiveClientExpirationRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHandleServerClientExpiration",
            exports = "handleServerClientExpiration", adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleClientExpiration(Stanza stanza) {
        var parsed = SmaxClientExpirationResponse.of(stanza).orElse(null);
        if (!(parsed instanceof SmaxClientExpirationResponse.Inbound inbound)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to parse client_expiration bulletin {0}",
                    stanza.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        var newExpiration = inbound.clientExpirationT()
                .map(InfoBulletinStreamHandler::castToUnixTime)
                .orElse(null);
        if (newExpiration == null) {
            whatsapp.store().accountStore().setClientExpiration(null);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cleared client expiration override");
            return;
        }

        var existingExpiration = whatsapp.store().accountStore().clientExpiration().orElse(null);

        if (existingExpiration != null && newExpiration >= existingExpiration.getEpochSecond()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring client expiration {0}: not earlier than existing {1}",
                    newExpiration, existingExpiration);
            return;
        }

        var minFloor = Instant.now().plusSeconds(CLIENT_EXPIRATION_MIN_FLOOR_SECONDS);

        var clampedExpiration = newExpiration < minFloor.getEpochSecond()
                ? minFloor
                : Instant.ofEpochSecond(newExpiration);

        whatsapp.store().accountStore().setClientExpiration(clampedExpiration);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received client expiration bulletin, clamped to {0}", clampedExpiration);
    }

    /**
     * Clamps a Unix-second timestamp to the signed 32-bit integer range.
     *
     * <p>Downstream paths multiply the timestamp through {@code Date}, so the explicit clamp prevents silent overflow.
     *
     * @implNote This implementation expresses the int32 truncation as {@code (long) (int) value}; the surrounding
     * clamp to the {@code [-(2^31 - 1), 2^31 - 1]} range is then a no-op within int32 range and is preserved for
     * documentation parity with WA Web's helper.
     *
     * @param value the raw timestamp in seconds
     * @return the clamped timestamp in seconds
     */
    @WhatsAppWebExport(moduleName = "WATimeUtils", exports = "castToUnixTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static long castToUnixTime(long value) {
        return Math.max(-2_147_483_647L, Math.min((long) (int) value, 2_147_483_647L));
    }
}
