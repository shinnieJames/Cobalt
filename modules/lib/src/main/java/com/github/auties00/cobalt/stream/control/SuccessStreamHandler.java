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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
 */
@WhatsAppWebModule(moduleName = "WAWebHandleSuccess")
public final class SuccessStreamHandler implements SocketStream.Handler {
    /**
     * The WhatsApp client used for store access, listener notification, and outbound IQ sending (active-mode transition).
     */
    private final WhatsAppClient whatsapp;

    /**
     * Service for synchronising A/B testing properties from the server.
     */
    private final ABPropsService abPropsService;

    /**
     * Service for device management operations such as the ADV check scheduler, pending device-sync retries, and missing-key device tracking.
     */
    private final DeviceService deviceService;

    /**
     * Service managing LID-based one-on-one chat migration.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * Service migrating inactive group chats to LID addressing.
     */
    private final InactiveGroupLidMigrationService inactiveGroupLidMigrationService;

    /**
     * Service for WAM (WhatsApp Analytics and Metrics) event recording.
     */
    private final WamService wamService;

    /**
     * Service managing web app-state synchronisation (syncd).
     */
    private final WebAppStateService webAppStateService;

    /**
     * Guard ensuring the full bootstrap runs at most once per connection. Reset by {@link #reset()} on socket teardown.
     */
    private final AtomicBoolean started;

    /**
     * Constructs a new success stream handler with the specified dependencies.
     *
     * @param whatsapp                         the WhatsApp client instance
     * @param abPropsService                   service for A/B prop synchronisation
     * @param deviceService                    service for device management
     * @param lidMigrationService              service for LID migration
     * @param inactiveGroupLidMigrationService service for inactive-group LID migration
     * @param wamService                       service for WAM initialisation
     * @param webAppStateService               service for web app-state sync
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
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleSuccess", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        if (started.compareAndSet(false, true)) {
            bootstrap(node);
        }
    }

    /**
     * Resets the handler state so that the next {@code <success>} stanza will trigger a full bootstrap. Called when the socket stream is torn down.
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
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleSuccess", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void bootstrap(Node node) {
        var store = whatsapp.store();

        // Cobalt-specific lifecycle state. Flips the store out of the "connecting" state once authentication has succeeded.
        store.setOnline(true);
        store.setRegistered(true);

        // Records the difference between server and local time so later timestamps and timeouts can be rebased through store.clockSkewSeconds().
        var serverTimestampSeconds = node.getAttributeAsLong("t", 0L);
        if (serverTimestampSeconds > 0) {
            var localSeconds = Instant.now().getEpochSecond();
            var skewSeconds = serverTimestampSeconds - localSeconds;
            // Math.round((server-local)/3600) is already WA Web's clockSkewHourly value (r * -1).
            var clockSkewHourly = (int) Math.round(skewSeconds / 3600.0);
            if (clockSkewHourly != 0 && abPropsService.getBool(ABProp.LOG_CLOCK_SKEW)) {
                wamService.commit(new ClockSkewDifferenceTEventBuilder()
                        .clockSkewHourly(clockSkewHourly)
                        .build());
            }
            store.setClockSkewSeconds(skewSeconds);
        }

        // updateMeLid only writes when transitioning null to value or value to a different value; it never clears an existing LID. Using ifPresent mirrors that semantics.
        node.getAttributeAsJid("lid").ifPresent(store::setLid);

        // Cobalt also rejects blank display names to avoid persisting an empty pushname; WA Web only checks for null.
        var displayName = node.getAttributeAsString("display_name", null);
        if (displayName != null && !displayName.isBlank()) {
            var oldName = store.name();
            store.setName(displayName);
            if (!Objects.equals(oldName, displayName)) {
                for (var listener : store.listeners()) {
                    Thread.startVirtualThread(() -> listener.onNameChanged(whatsapp, oldName, displayName));
                }
            }
        }

        // WA Web's passive-task pipeline (collection action handlers, temporary-ban banner reset, offline push toggle, IndexedDB key derivation) has no Cobalt equivalent because Cobalt is headless and uses Java serialization without at-rest encryption.
        lidMigrationService.initialize();

        // Cobalt always syncs AB props (refresh-id state is not persisted across runs); this is a strict superset of WA Web's conditional sync and never under-syncs.
        var abPropsSynced = abPropsService.sync();

        if (abPropsSynced && abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_ENABLED)) {
            lidMigrationService.enableMigration();
        } else {
            lidMigrationService.disableMigration();
        }

        // Initialised after the AB-prop sync so the abKey and sampling overrides are picked up before the first event is committed.
        wamService.initialize();

        deviceService.startAdvCheckScheduler();
        deviceService.retryPendingSyncs();
        deviceService.updateMissingKeyDevices();

        inactiveGroupLidMigrationService.start();

        // Stamps the emergency push timestamp only when the refresh id actually changed, mirroring WA Web's getGroupAbPropsRefreshId comparison.
        var groupAbpropsRefreshId = node.getAttributeAsLong("group_abprops", 0L);
        if (groupAbpropsRefreshId != 0L && serverTimestampSeconds > 0
                && groupAbpropsRefreshId != store.groupAbPropsRefreshId()) {
            store.setGroupAbPropsEmergencyPushTimestamp(Instant.ofEpochSecond(serverTimestampSeconds));
            store.setGroupAbPropsRefreshId(groupAbpropsRefreshId);
        }

        webAppStateService.resumeAfterRestart();
        webAppStateService.startPeriodicSyncJob();

        // The Cobalt login payload sets passive=true so the server buffers offline traffic until the active iq is sent here.
        sendActiveModeIq();

        // Compliance probes mirror the housekeeping the official client emits at app launch. Cobalt has no store slots for the responses yet so the calls are fire-and-log to match the server-observable behaviour.
        runComplianceProbe("reachout timelock", whatsapp::queryReachoutTimelock);

        runComplianceProbe("new-chat capping info", whatsapp::queryNewChatMessageCappingInfo);

        syncPrivacyDisallowedListsMex();

        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onLoggedIn(whatsapp));
        }

        // Persisting the store is best-effort; the bootstrap must not fail if the underlying serializer is unhappy.
        try {
            store.save();
        } catch (Exception ignored) {
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
     */
    @WhatsAppWebExport(moduleName = "WASendPassiveModeProtocol", exports = "sendPassiveModeProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPassiveModeManager", exports = "PassiveTaskManager",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendActiveModeIq() {
        try {
            // sendNode auto-injects an id when none is provided; the server expects an id on every type="set" iq.
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
            // Best-effort. The server will start streaming after its own passive timeout even if the active iq never reaches it.
        }
    }

    /**
     * Logger reused by the post-success compliance probes so failures are
     * surfaced as warnings without aborting the bootstrap.
     */
    private static final System.Logger LOGGER_COMPLIANCE = System.getLogger(SuccessStreamHandler.class.getName() + ".compliance");

    /**
     * Runs a single post-success MEX compliance probe, logging any failure
     * without re-throwing.
     *
     * <p>The probes mirror the housekeeping the official client emits at
     * app launch (reachout timelock, new-chat capping). They are
     * fire-and-forget on Cobalt because the responses have no store slot
     * yet; only the server-observable round trip matters for compliance.
     *
     * @param probeName the human-readable name of the probe for log output
     * @param probe     the probe to run; never {@code null}
     */
    private static void runComplianceProbe(String probeName, Supplier<?> probe) {
        try {
            probe.get();
        } catch (Throwable throwable) {
            LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                    "Cannot run {0} compliance probe: {1}",
                    probeName,
                    throwable.getMessage());
        }
    }

    /**
     * Reconciles the four privacy disallowed lists through the MEX/GraphQL
     * transport, mirroring the WA Web post-success privacy sync.
     *
     * <p>WA Web's
     * {@code WAWebSyncPrivacyDisallowedLists.syncPrivacyDisallowedLists}
     * fans out one
     * {@code WAWebQueryPrivacyDisallowedListMexJob.queryPrivacyDisallowedListMex}
     * call per
     * {@code PrivacyDisallowedListType} enum value (ABOUT, GROUPADD, LAST,
     * PROFILE) so the server records the same compliance ping the official
     * client emits at app launch. Cobalt has no per-category dhash store
     * slot yet, so the local cache digest is sent as the empty string on
     * every probe; the server therefore always returns the full roster (or
     * {@code match} when empty), but the round trip itself is sufficient
     * for compliance. The reply is logged and discarded.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncPrivacyDisallowedLists",
            exports = "syncPrivacyDisallowedLists", adaptation = WhatsAppAdaptation.ADAPTED)
    private void syncPrivacyDisallowedListsMex() {
        var meLid = whatsapp.store().lid().orElse(null);
        if (meLid == null) {
            // WA Web's getMaybeMeDeviceLid == null check skips the MEX path entirely.
            return;
        }
        // PrivacyDisallowedListType enum maps to MEX category strings: About to ABOUT, GroupAdd to GROUPADD, LastSeen to LAST, ProfilePicture to PROFILE.
        var categories = List.of("ABOUT", "GROUPADD", "LAST", "PROFILE");
        for (var category : categories) {
            try {
                whatsapp.queryPrivacyDisallowedList(meLid, "", category, "DENYLIST");
            } catch (Throwable throwable) {
                LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                        "Cannot reconcile privacy disallowed list {0}: {1}",
                        category,
                        throwable.getMessage());
            }
        }
    }
}
