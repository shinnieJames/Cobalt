package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.ClockSkewDifferenceTEventBuilder;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the {@code <success>} stanza received from the WhatsApp server after
 * a successful authentication handshake. This handler triggers the full client
 * bootstrap sequence equivalent to the WA Web {@code WAWebHandleSuccess.default}
 * function: parsing the success attributes, updating the {@code me} user
 * identity (LID, display name), installing collection action handlers,
 * triggering passive-mode tasks, syncing A/B props, starting LID migration,
 * starting device, WAM and app-state services, sending the active-mode IQ to
 * resume foreground traffic, and notifying listeners that the client is
 * logged in.
 *
 * <p>The handler uses an {@link AtomicBoolean} guard to ensure the bootstrap
 * sequence runs at most once per connection lifetime. The guard is reset via
 * {@link #reset()} when the socket stream is torn down, allowing the sequence
 * to run again on reconnection.
 *
 * @implNote WAWebHandleSuccess.default is a single async function that
 *           sequentially runs all post-authentication bootstrap actions.
 *           Cobalt collapses these into the {@link #bootstrap(Node)} method,
 *           injecting each downstream service through the constructor instead
 *           of importing module-level singletons.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleSuccess")
public final class SuccessStreamHandler implements SocketStream.Handler {
    /**
     * The WhatsApp client instance used for store access, listener
     * notification, and outbound IQ sending (active-mode transition).
     *
     * @implNote WAWebHandleSuccess.default accesses store/services via
     *           module-level imports such as {@code WAWebUserPrefsMeUser};
     *           Cobalt injects the client through the constructor.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Service for synchronising A/B testing properties from the server.
     *
     * @implNote Maps to the WA Web call
     *           {@code WAWebAbPropsSyncJob.syncABPropsTask({localRefreshId, shouldSendHash:false})}.
     */
    private final ABPropsService abPropsService;

    /**
     * Service for device management operations such as the ADV check
     * scheduler, pending device-sync retries, and missing-key device
     * tracking.
     *
     * @implNote These device-housekeeping tasks are registered in WA Web
     *           through {@code WAWebRegisterPassiveTasks} and
     *           {@code WAWebPassiveModeManager.executePassiveTasks}; Cobalt
     *           invokes them directly here as part of the success bootstrap.
     */
    private final DeviceService deviceService;

    /**
     * Service managing LID-based one-on-one chat migration.
     *
     * @implNote WA Web checks the {@code lid_one_on_one_migration_enabled}
     *           AB prop after sync and either schedules the peer-mapping
     *           timeout or leaves the migration disabled; Cobalt routes the
     *           same decision through this service.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * Service migrating inactive group chats to LID addressing.
     *
     * @implNote WA Web triggers inactive-group LID migration through event
     *           listeners attached to the {@code BackendEventBus}; Cobalt
     *           invokes the equivalent service directly during bootstrap.
     */
    private final InactiveGroupLidMigrationService inactiveGroupLidMigrationService;

    /**
     * Service for WAM (WhatsApp Analytics/Metrics) event recording.
     *
     * @implNote WAM globals are populated in WA Web by
     *           {@code WAWebABPropsWamGlobals.setAbPropDependingGlobalWamAttributes}
     *           after the AB-props sync task; Cobalt initialises the WAM
     *           service explicitly so that every event committed after
     *           {@code <success>} carries the up-to-date attributes.
     */
    private final WamService wamService;

    /**
     * Service for managing web app state synchronisation (syncd).
     *
     * @implNote WA Web resumes the syncd state machine and starts the
     *           periodic app-state sync job through passive tasks scheduled
     *           after success; Cobalt calls them directly here.
     */
    private final WebAppStateService webAppStateService;

    /**
     * Guard ensuring the full bootstrap runs at most once per connection.
     * Reset by {@link #reset()} on socket teardown.
     *
     * @implNote ADAPTED: WAWebHandleSuccess.default has no explicit guard
     *           because the JS module is a single function fired exactly
     *           once per connection by the parser registry. Cobalt reuses
     *           handler instances across reconnects, so the guard is needed
     *           to avoid double bootstrap if the server sends two success
     *           stanzas on the same socket.
     */
    private final AtomicBoolean started;

    /**
     * Constructs a new success stream handler with the specified
     * dependencies.
     *
     * @param whatsapp                         the WhatsApp client instance
     * @param abPropsService                   service for A/B prop
     *                                         synchronisation
     * @param deviceService                    service for device management
     * @param lidMigrationService              service for LID migration
     * @param inactiveGroupLidMigrationService service for inactive group LID
     *                                         migration
     * @param wamService                       service for WAM initialisation
     * @param webAppStateService               service for web app state sync
     * @implNote ADAPTED: WAWebHandleSuccess.default uses module-level
     *           imports; Cobalt uses constructor DI.
     */
    public SuccessStreamHandler(
            WhatsAppClient whatsapp,
            ABPropsService abPropsService,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            InactiveGroupLidMigrationService inactiveGroupLidMigrationService,
            WamService wamService,
            WebAppStateService webAppStateService
    ) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.inactiveGroupLidMigrationService = Objects.requireNonNull(inactiveGroupLidMigrationService, "inactiveGroupLidMigrationService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.webAppStateService = Objects.requireNonNull(webAppStateService, "webAppStateService cannot be null");
        this.started = new AtomicBoolean();
    }

    /**
     * Handles an incoming {@code <success>} stanza node. Delegates to
     * {@link #bootstrap(Node)} on the first invocation per connection.
     *
     * @param node the {@code <success>} stanza node
     * @implNote WA Web registers this function via
     *           {@code WADeprecatedWapParser("successParser", ...)} inside
     *           the {@code WAWebCommsRouter} bootstrap and dispatches to it
     *           directly when a {@code <success>} node arrives.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleSuccess", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        // ADAPTED: defensive guard; WA Web has no explicit guard because the
        // JS module is invoked at most once per connection by the parser
        // registry, but Cobalt reuses handler instances across reconnects.
        if (started.compareAndSet(false, true)) {
            bootstrap(node);
        }
    }

    /**
     * Resets the handler state so that the next {@code <success>} stanza
     * will trigger a full bootstrap. Called when the socket stream is torn
     * down.
     *
     * @implNote NO_WA_BASIS: WA Web does not reset handler state explicitly
     *           because each new socket connection creates fresh module
     *           instances. Cobalt reuses handler instances across reconnects.
     */
    @Override
    public void reset() {
        started.set(false);
    }

    /**
     * Performs the full client bootstrap sequence after receiving a
     * {@code <success>} stanza from the server. This is the Cobalt
     * equivalent of WA Web's {@code WAWebHandleSuccess.default} async
     * function.
     *
     * <p>The sequence performs, in order:
     *
     * <ol>
     *   <li>Marks the store as online and registered (Cobalt-specific
     *       lifecycle state)</li>
     *   <li>Updates the user's LID from the {@code lid} attribute</li>
     *   <li>Updates the user's display name from the {@code display_name}
     *       attribute and notifies listeners on change</li>
     *   <li>Triggers LID migration initialisation</li>
     *   <li>Synchronises A/B props and enables/disables LID migration based
     *       on the {@code lid_one_on_one_migration_enabled} prop</li>
     *   <li>Initialises the WAM service so subsequent events carry the
     *       up-to-date AB-prop derived globals</li>
     *   <li>Schedules ADV device checks, retries pending device syncs and
     *       refreshes missing-key device tracking</li>
     *   <li>Starts inactive-group LID migration</li>
     *   <li>Resumes web app-state syncing and starts the periodic sync
     *       job</li>
     *   <li>Sends the {@code <iq xmlns="passive"><active/></iq>} stanza so
     *       the server transitions the socket out of passive-mode and
     *       resumes pushing live traffic</li>
     *   <li>Notifies listeners that the client is logged in</li>
     *   <li>Persists the store to disk</li>
     * </ol>
     *
     * @param node the parsed {@code <success>} stanza node
     * @implNote WAWebHandleSuccess.default
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleSuccess", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void bootstrap(Node node) {
        var store = whatsapp.store();

        // ADAPTED: WAWebHandleSuccess.default ->
        // WAWebPageLoadLogging.addPageLoadQplPoint("success_received") and
        // WAWebQplFlowWrapper.QPL.markerPoint(p, "SuccessReceived").
        // Cobalt has no QPL/page-load instrumentation, so these telemetry
        // markers have no equivalent.

        // NO_WA_BASIS: Cobalt-specific lifecycle state — flips the store
        // out of the "connecting" state once authentication has succeeded.
        store.setOnline(true);
        store.setRegistered(true);

        // WAWebHandleSuccess.default: WAWebUpdateClockSkewUtils.updateClockSkew(u.ts)
        // WA Web records the difference between server time and local time so
        // subsequent stamps and timeouts can be rebased. Cobalt now persists
        // the skew in seconds on the store; callers read it through
        // store.clockSkewSeconds().
        var serverTimestampSeconds = node.getAttributeAsLong("t", 0L);
        if (serverTimestampSeconds > 0) {
            // WAWebUpdateClockSkewUtils.updateClockSkew:
            //   t = Date.now()/1e3, n = Math.round(t - e), r = Math.round(n / HOUR_SECONDS)
            //   if (r !== 0 && getABPropConfigValue("log_clock_skew"))
            //       new ClockSkewDifferenceTWamEvent({clockSkewHourly: r * -1}).commit()
            //   WATimeUtils.setClockSkew(n)
            // Cobalt stores the skew as (server - local) seconds so the
            // hourly value computed below is already the WA Web "r * -1".
            var localSeconds = Instant.now().getEpochSecond();
            var skewSeconds = serverTimestampSeconds - localSeconds;
            // WAWebUpdateClockSkewUtils.updateClockSkew: r = Math.round(n/HOUR_SECONDS);
            // we compute Math.round((server-local)/3600) = -r so that -r is the
            // clockSkewHourly value WA Web emits (r * -1).
            var clockSkewHourly = (int) Math.round(skewSeconds / 3600.0);
            if (clockSkewHourly != 0 && abPropsService.getBool(ABProp.LOG_CLOCK_SKEW)) {
                // WAWebUpdateClockSkewUtils.updateClockSkew: new ClockSkewDifferenceTWamEvent({clockSkewHourly:r*-1}).commit()
                wamService.commit(new ClockSkewDifferenceTEventBuilder()
                        .clockSkewHourly(clockSkewHourly)
                        .build());
            }
            store.setClockSkewSeconds(skewSeconds); // WAWebUpdateClockSkewUtils.updateClockSkew: WATimeUtils.setClockSkew(n)
        }

        // WAWebHandleSuccess.default: WAWebUpdateMeLidUtils.updateMeLid
        // updateMeLid only writes when transitioning null -> value or value
        // -> different value; it never clears an existing LID. Using
        // ifPresent on getAttributeAsJid mirrors that semantics because the
        // setter is only invoked when the attribute is present and parses
        // as a valid JID.
        node.getAttributeAsJid("lid").ifPresent(store::setLid);

        // WAWebHandleSuccess.default: WAWebUserPrefsMeUser.setMeDisplayName
        // WA Web checks displayName != null only; Cobalt also rejects blank
        // strings to avoid persisting an empty pushname.
        var displayName = node.getAttributeAsString("display_name", null);
        if (displayName != null && !displayName.isBlank()) {
            var oldName = store.name();
            store.setName(displayName);
            if (!Objects.equals(oldName, displayName)) {
                // ADAPTED: WAWebHandleSuccess.default just stores the name;
                // Cobalt notifies registered listeners of name changes.
                for (var listener : store.listeners()) {
                    Thread.startVirtualThread(() -> listener.onNameChanged(whatsapp, oldName, displayName));
                }
            }
        }

        // ADAPTED: WAWebHandleSuccess.default ->
        // WAWebSyncdGetActionHandler.setActionHandlers(WAWebCollectionHandlerActions.ActionHandlers)
        // WA Web installs the collection action handler registry as a global
        // singleton. Cobalt wires the equivalent registry into
        // WebAppStateService at construction time, so no explicit call is
        // required here.
        // ADAPTED: WAWebHandleSuccess.default ->
        // BackendEventBus.triggerTemporaryBan({banned:false})
        // WA Web fires a frontend bridge event so the UI can dismiss any
        // "temporarily banned" banner. Cobalt does not surface that UI
        // banner because it is a headless library; the equivalent state is
        // implied by the fact that authentication succeeded.
        // ADAPTED: WAWebHandleSuccess.default ->
        // WAWebUserPrefsGeneral.setOfflinePushDisabled(false)
        // WA Web re-enables offline push notifications on every successful
        // login. Cobalt has no offline push subsystem (the project does not
        // ship a service worker), so the call has no equivalent.
        // ADAPTED: WAWebHandleSuccess.default ->
        // WAWebDbEncryptionKey.DbEncKeyStore.generateFinalDbEncryptionAndFtsKey(c)
        // and generateFinalDbEncryptionAndFtsKeyForInvoker(c). WA Web uses
        // the companion_enc_static attribute as a salt to finalise IndexedDB
        // encryption keys. Cobalt persists session data via Java
        // serialization without an at-rest encryption layer, so the
        // companion_enc_static attribute is intentionally ignored.
        // ADAPTED: WAWebHandleSuccess.default -> WAWebPassiveModeManager
        // .executePassiveTasks runs LID-migration init as a passive task;
        // Cobalt calls it directly to keep ordering explicit.
        lidMigrationService.initialize();

        // WAWebHandleSuccess.default -> WAWebAbPropsSyncJob.syncABPropsTask(
        //     {localRefreshId: g!==d ? g : C, shouldSendHash:false})
        // WA Web only syncs when the server's abprops refresh id differs
        // from the locally stored one (or the web refresh id changed via
        // the justknobx 2086 flag). Cobalt always syncs because the
        // refresh-id state is not persisted across runs; this is a strict
        // superset of the WA Web behaviour and never under-syncs.
        var abPropsSynced = abPropsService.sync();

        // WAWebHandleSuccess.default ->
        // checkIfMigrationEnabled (read after the sync so the latest value
        // of lid_one_on_one_migration_enabled is honoured).
        if (abPropsSynced && abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_ENABLED)) {
            lidMigrationService.enableMigration();
        } else {
            lidMigrationService.disableMigration();
        }

        // ADAPTED: WAWebHandleSuccess.default -> the WAM globals are
        // populated by passive tasks (WAWebABPropsWamGlobals); Cobalt
        // explicitly initialises the WAM service after the AB-prop sync so
        // the abKey/sampling overrides are picked up before the first
        // event is committed.
        wamService.initialize();

        // ADAPTED: WAWebHandleSuccess.default -> the device housekeeping
        // suite (WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck,
        // WAWebApiPendingDeviceSync.doPendingDeviceSync,
        // WAWebSyncdStoreMissingKeys.updateMissingKeyDevices) is scheduled
        // via passive tasks and event listeners in WA Web; Cobalt fires
        // them directly here so the post-success ordering is deterministic.
        deviceService.startAdvCheckScheduler();
        deviceService.retryPendingSyncs();
        deviceService.updateMissingKeyDevices();

        // ADAPTED: WAWebHandleSuccess.default -> inactive-group LID
        // migration runs through BackendEventBus listeners in WA Web;
        // Cobalt starts it explicitly here.
        inactiveGroupLidMigrationService.start();

        // WAWebHandleSuccess.default -> WAWebABPropsLocalStorage
        //     .setGroupAbPropsEmergencyPushTimestamp(u.ts)
        // WA Web compares the server-supplied group_abprops refresh id against
        // its locally-persisted copy via WAWebABPropsLocalStorage
        //     .getGroupAbPropsRefreshId(), and when they differ it writes the
        // stanza's server timestamp so a future sync can detect the push.
        // Cobalt now persists the refresh id on the store via
        // WhatsAppStore.setGroupAbPropsRefreshId, so we mirror the JS
        // exact comparison instead of stamping unconditionally.
        var groupAbpropsRefreshId = node.getAttributeAsLong("group_abprops", 0L);
        if (groupAbpropsRefreshId != 0L && serverTimestampSeconds > 0
                && groupAbpropsRefreshId != store.groupAbPropsRefreshId()) {
            store.setGroupAbPropsEmergencyPushTimestamp(Instant.ofEpochSecond(serverTimestampSeconds)); // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
            store.setGroupAbPropsRefreshId(groupAbpropsRefreshId); // WAWebABPropsLocalStorage.setGroupAbPropsRefreshId
        }

        // ADAPTED: WAWebHandleSuccess.default -> WA Web resumes syncd and
        // starts the periodic app-state sync job through passive tasks;
        // Cobalt calls them directly so the order is explicit.
        webAppStateService.resumeAfterRestart();
        webAppStateService.startPeriodicSyncJob();

        // ADAPTED: WAWebHandleSuccess.default -> b(1000) which calls
        // mediaHosts.forceRefresh(signal) with a 1-second timeout to
        // pre-warm the media-conn cache for the next outgoing media
        // request. Cobalt's media subsystem fetches the conn lazily on the
        // first media operation; pre-warming is omitted to keep the
        // success handler synchronous and side-effect free for tests.
        // WAWebHandleSuccess.default -> WAWebPassiveModeManager
        //     .executePassiveTasks ends with WASendPassiveModeProtocol
        //     .sendPassiveModeProtocol("active"), which sends
        //     <iq xmlns="passive" type="set"><active/></iq> to tell the
        // server the client is ready to leave passive-mode. The Cobalt
        // login payload sets passive=true (see WhatsAppSocketClient
        // .getClientPayloadForLogin), so the server holds offline traffic
        // until the active iq is sent. Sending it here keeps the bootstrap
        // sequence in lockstep with WA Web.
        sendActiveModeIq();

        // ADAPTED: WAWebHandleSuccess.default -> Cobalt notifies registered
        // listeners; WA Web has no equivalent {@code onLoggedIn} callback
        // because consumers of the JS code are inside the same bundle and
        // observe state via reactive collections.
        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onLoggedIn(whatsapp));
        }

        // ADAPTED: WAWebHandleSuccess.default -> Cobalt persists the store
        // to disk so the next process can restart from the post-success
        // state. WA Web persists individual prefs via IndexedDB and
        // localStorage throughout the bootstrap, so there is no single
        // equivalent flush.
        try {
            store.save();
        } catch (Exception ignored) {
            // Persisting the store is best-effort; the bootstrap must not
            // fail if the underlying serializer is unhappy.
        }
    }

    /**
     * Sends the {@code <iq xmlns="passive" type="set"><active/></iq>}
     * stanza to transition the server-side socket out of passive mode.
     *
     * <p>The login client payload sets {@code passive=true}, which
     * instructs the server to buffer outgoing traffic until the client
     * confirms it is ready. WA Web's {@code WAWebPassiveModeManager}
     * dispatches this iq after running every registered passive task;
     * because Cobalt runs the passive tasks inline inside
     * {@link #bootstrap(Node)}, the active iq is sent as the last step
     * of the bootstrap before listener notification.
     *
     * <p>Failures are swallowed because the worst case is that the server
     * starts streaming traffic anyway after a short timeout; surfacing the
     * exception would only abort the listener notification and the store
     * persistence steps that follow.
     *
     * @implNote WAWebPassiveModeManager.executePassiveTasks (final
     *           {@code sendPassiveModeProtocol("active")} call) and
     *           WASmaxOutPassiveModeActiveIQRequest.makeActiveIQRequest.
     */
    @WhatsAppWebExport(moduleName = "WASendPassiveModeProtocol", exports = "sendPassiveModeProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPassiveModeManager", exports = "PassiveTaskManager",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendActiveModeIq() {
        try {
            // Use sendNode(NodeBuilder) so an `id` attribute is auto-injected
            // when one is not provided; the server expects an id on every
            // type="set" iq because WAWap.generateId() is invoked by
            // WASmaxOutPassiveModeActiveIQRequest.makeActiveIQRequest. The
            // returned response is ignored — Cobalt only needs the server to
            // observe the request so it transitions out of passive mode.
            var iq = new NodeBuilder()
                    .description("iq")
                    .attribute("xmlns", "passive")
                    .attribute("to", "s.whatsapp.net")
                    .attribute("type", "set")
                    .content(new NodeBuilder()
                            .description("active")
                            .build());
            whatsapp.sendNode(iq);
        } catch (Exception ignored) {
            // Best-effort: the server will start streaming after its own
            // passive timeout even if the active iq never reaches it.
        }
    }
}
