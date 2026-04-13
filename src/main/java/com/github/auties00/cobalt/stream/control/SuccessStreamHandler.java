package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.WamService;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the {@code success} stanza received from the WhatsApp server after a
 * successful authentication handshake. This handler triggers the full client
 * bootstrap sequence: updating store state, syncing A/B props, initialising
 * device and migration services, and notifying listeners that the client is
 * logged in.
 *
 * <p>The handler uses an {@link AtomicBoolean} guard to ensure the bootstrap
 * sequence runs at most once per connection lifetime. The guard is reset via
 * {@link #reset()} when the socket stream is torn down, allowing the sequence
 * to run again on reconnection.
 *
 * @implNote WAWebHandleSuccess.default
 */
public final class SuccessStreamHandler implements SocketStream.Handler {
    /**
     * The WhatsApp client instance used for store access and listener notification.
     *
     * @implNote WAWebHandleSuccess.default -- the function receives the node
     *           and accesses global stores/services via module imports; Cobalt
     *           uses constructor DI instead.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Service for synchronising A/B testing properties from the server.
     *
     * @implNote WAWebHandleSuccess.default -- corresponds to the
     *           {@code WAWebAbPropsSyncJob.syncABPropsTask} and
     *           {@code WAWebABPropsLocalStorage} interactions in the WA Web
     *           success handler.
     */
    private final ABPropsService abPropsService;

    /**
     * Service for device management operations such as ADV checking, pending
     * sync retries, and missing key device updates.
     *
     * @implNote WAWebHandleSuccess.default -- WA Web triggers equivalent
     *           device housekeeping through passive tasks and event listeners;
     *           Cobalt consolidates them here.
     */
    private final DeviceService deviceService;

    /**
     * Service managing LID-based one-on-one chat migration.
     *
     * @implNote WAWebHandleSuccess.default -- WA Web enables/disables LID
     *           migration via passive tasks; Cobalt triggers it directly
     *           after AB prop sync.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * Service migrating inactive group chats to LID addressing.
     *
     * @implNote WAWebHandleSuccess.default -- WA Web handles inactive group
     *           LID migration through event listeners; Cobalt starts it
     *           during bootstrap.
     */
    private final InactiveGroupLidMigrationService inactiveGroupLidMigrationService;

    /**
     * Service for WAM (WhatsApp Analytics/Metrics) event initialisation.
     *
     * @implNote WAWebHandleSuccess.default -- WAM initialisation in WA Web
     *           is triggered by event listeners after success; Cobalt
     *           initialises it directly.
     */
    private final WamService wamService;

    /**
     * Service for managing web app state synchronisation (syncd).
     *
     * @implNote WAWebHandleSuccess.default -- WA Web resumes sync and starts
     *           periodic jobs through passive tasks; Cobalt calls them
     *           directly in bootstrap.
     */
    private final WebAppStateService webAppStateService;

    /**
     * Guard ensuring bootstrap runs at most once per connection. Reset by
     * {@link #reset()} on disconnection.
     *
     * @implNote WAWebHandleSuccess.default -- WA Web does not have an
     *           explicit guard because the server sends the success stanza
     *           only once. This is a defensive adaptation.
     */
    private final AtomicBoolean started;

    /**
     * Constructs a new success stream handler with the specified dependencies.
     *
     * @param whatsapp                         the WhatsApp client instance
     * @param abPropsService                   service for A/B prop synchronisation
     * @param deviceService                    service for device management
     * @param lidMigrationService              service for LID migration
     * @param inactiveGroupLidMigrationService service for inactive group LID migration
     * @param wamService                       service for WAM initialisation
     * @param webAppStateService               service for web app state sync
     * @implNote WAWebHandleSuccess.default -- Cobalt uses constructor DI instead
     *           of WA Web's module-level imports
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
        this.whatsapp = whatsapp;
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.inactiveGroupLidMigrationService = Objects.requireNonNull(inactiveGroupLidMigrationService, "inactiveGroupLidMigrationService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.webAppStateService = Objects.requireNonNull(webAppStateService, "webAppStateService cannot be null");
        this.started = new AtomicBoolean();
    }

    /**
     * Handles an incoming {@code success} stanza node. Delegates to
     * {@link #bootstrap(Node)} on the first invocation per connection.
     *
     * @param node the {@code success} stanza node
     * @implNote WAWebHandleSuccess.default -- WA Web registers the handler via
     *           {@code WADeprecatedWapParser} and calls the default export
     *           directly when a success node arrives.
     */
    @Override
    public void handle(Node node) {
        if (started.compareAndSet(false, true)) { // ADAPTED: defensive guard; WA Web has no explicit guard
            bootstrap(node);
        }
    }

    /**
     * Resets the handler state so that the next {@code success} stanza will
     * trigger a full bootstrap. Called when the socket stream is torn down.
     *
     * @implNote WAWebHandleSuccess.default -- WA Web does not reset handler
     *           state explicitly; each new socket connection creates fresh
     *           handler instances. Cobalt reuses handler instances across
     *           reconnects.
     */
    @Override
    public void reset() {
        started.set(false);
    }

    /**
     * Performs the full client bootstrap sequence after receiving a
     * {@code success} stanza from the server. This includes:
     *
     * <ol>
     *   <li>Marking the store as online and registered</li>
     *   <li>Updating the user's LID from the stanza</li>
     *   <li>Updating the user's display name if present</li>
     *   <li>Initialising LID migration and syncing A/B props</li>
     *   <li>Starting device, WAM, and app state sync services</li>
     *   <li>Notifying listeners that the client is logged in</li>
     *   <li>Persisting the store</li>
     * </ol>
     *
     * @param node the parsed {@code success} stanza node
     * @implNote WAWebHandleSuccess.default
     */
    private void bootstrap(Node node) {
        var store = whatsapp.store();
        store.setOnline(true); // NO_WA_BASIS: Cobalt-specific online state tracking
        store.setRegistered(true); // NO_WA_BASIS: Cobalt-specific registration state tracking

        // WAWebHandleSuccess.default -> WAWebUpdateMeLidUtils.updateMeLid
        node.getAttributeAsJid("lid").ifPresent(store::setLid);

        // WAWebHandleSuccess.default -> WAWebUserPrefsMeUser.setMeDisplayName
        var displayName = node.getAttributeAsString("display_name", null);
        if (displayName != null && !displayName.isBlank()) { // ADAPTED: WAWebHandleSuccess.default checks != null only; blank check is defensive
            var oldName = store.name();
            store.setName(displayName);
            if (!java.util.Objects.equals(oldName, displayName)) {
                // ADAPTED: WAWebHandleSuccess.default -- WA Web just stores the name;
                // Cobalt notifies listeners of name changes
                for (var listener : store.listeners()) {
                    Thread.startVirtualThread(() -> listener.onNameChanged(whatsapp, oldName, displayName));
                }
            }
        }

        // ADAPTED: WAWebHandleSuccess.default -> WAWebPassiveModeManager.executePassiveTasks
        // WA Web runs LID migration init as a passive task; Cobalt calls it directly
        lidMigrationService.initialize();

        // WAWebHandleSuccess.default -> WAWebAbPropsSyncJob.syncABPropsTask
        // WA Web conditionally syncs based on refresh IDs; Cobalt always syncs
        var abPropsSynced = abPropsService.sync(); // ADAPTED: WAWebHandleSuccess.default
        if (abPropsSynced && abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_ENABLED)) {
            lidMigrationService.enableMigration(); // ADAPTED: WAWebHandleSuccess.default
        } else {
            lidMigrationService.disableMigration(); // ADAPTED: WAWebHandleSuccess.default
        }

        // ADAPTED: WAWebHandleSuccess.default -- WAM init is triggered by event
        // listeners in WA Web; Cobalt initialises directly
        wamService.initialize();

        // ADAPTED: WAWebHandleSuccess.default -- device housekeeping in WA Web
        // runs through passive tasks and event listeners
        deviceService.startAdvCheckScheduler();
        deviceService.retryPendingSyncs();
        deviceService.updateMissingKeyDevices();

        // ADAPTED: WAWebHandleSuccess.default -- inactive group LID migration
        // in WA Web is triggered via event listeners
        inactiveGroupLidMigrationService.start();

        // ADAPTED: WAWebHandleSuccess.default -- WA Web resumes syncd through
        // passive tasks; Cobalt calls directly
        webAppStateService.resumeAfterRestart();
        webAppStateService.startPeriodicSyncJob();

        // ADAPTED: WAWebHandleSuccess.default -- Cobalt notifies listeners;
        // WA Web does not have an equivalent onLoggedIn callback
        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onLoggedIn(whatsapp));
        }

        // ADAPTED: WAWebHandleSuccess.default -- Cobalt persists store to disk;
        // WA Web persists individual prefs via IndexedDB/localStorage throughout
        try {
            store.save();
        } catch (Exception ignored) {
        }
    }
}
