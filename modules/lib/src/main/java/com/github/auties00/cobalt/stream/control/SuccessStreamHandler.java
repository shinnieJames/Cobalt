package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppSettingsStore;
import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedChatsListener;
import com.github.auties00.cobalt.listener.linked.LinkedContactsListener;
import com.github.auties00.cobalt.listener.LoggedInListener;
import com.github.auties00.cobalt.listener.linked.LinkedNameChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedNewslettersListener;
import com.github.auties00.cobalt.listener.linked.LinkedStatusListener;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientType;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppFacebookGraphQlException;
import com.github.auties00.cobalt.exception.WhatsAppServerRuntimeException;
import com.github.auties00.cobalt.exception.WhatsAppWebGraphQlException;
import com.github.auties00.cobalt.media.MediaConnectionService;
import com.github.auties00.cobalt.model.device.pairing.LinkedPrimaryPlatform;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.model.tos.TosNotice;
import com.github.auties00.cobalt.stanza.iq.media.IqQueryMediaConnsRequest;
import com.github.auties00.cobalt.stanza.iq.media.IqQueryMediaConnsResponse;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.tos.TosService;
import com.github.auties00.cobalt.stream.NodeStreamService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.util.ScheduledTask;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.ClockSkewDifferenceTEventBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Handles the {@code <success>} stanza pushed by the WhatsApp server immediately after a successful authentication
 * handshake and drives the full client bootstrap that follows.
 *
 * <p>The handler is registered under the {@code "success"} tag inside {@link NodeStreamService}. One {@code <success>}
 * stanza per session drives a one-shot bootstrap: update the {@code me} identity (LID, display name, phone number) from
 * the parsed attributes, sync A/B props, enable or disable LID migration, start the WAM service, schedule the ADV
 * device check, resume web app-state syncing, send the {@code <iq xmlns="passive"><active/></iq>} stanza that
 * transitions the server out of passive mode, run the launch-time compliance probes and notify
 * {@link LoggedInListener#onLoggedIn(LinkedWhatsAppClient)}. A one-shot {@link AtomicBoolean} guard ensures the
 * bootstrap runs at most once per connection; {@link #reset()} clears it on socket teardown so the next reconnect
 * repeats the bootstrap.
 *
 * @implNote This implementation flattens WA Web's split between the success handler, the registered passive-task
 * pipeline and the IndexedDB-key derivation step into a single sequential virtual-thread call; WA Web's UI-only passive
 * tasks (collection action handlers, temporary-ban banner reset, offline push toggle, at-rest encryption key
 * derivation) are skipped because Cobalt is headless and persists the store without at-rest encryption.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleSuccess")
public final class SuccessStreamHandler extends SocketStreamHandler.Concurrent {
    /**
     * The {@link LinkedWhatsAppClient} used for store access, listener notification and outbound IQ sending.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * The {@link ABPropsService} used to sync A/B testing properties from the server during bootstrap.
     */
    private final ABPropsService abPropsService;

    /**
     * The {@link DeviceService} used to schedule the ADV check, retry pending device syncs and refresh missing-key
     * device tracking.
     */
    private final DeviceService deviceService;

    /**
     * The {@link LidMigrationService} consulted during bootstrap to flip LID-based one-on-one chat migration on or off
     * based on the synced AB-prop.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * The {@link InactiveGroupLidMigrationService} started during bootstrap so inactive group chats migrate to LID
     * addressing in the background.
     */
    private final InactiveGroupLidMigrationService inactiveGroupLidMigrationService;

    /**
     * The {@link WamService} used to commit the launch-time {@code ClockSkewDifferenceT} event and to initialise
     * itself with the up-to-date AB-prop-derived globals.
     */
    private final WamService wamService;

    /**
     * The {@link WebAppStateService} resumed during bootstrap and given its periodic-sync job tick.
     */
    private final WebAppStateService webAppStateService;

    /**
     * The shared {@link MediaConnectionService} updated each time the periodic {@code media_conn} IQ reply lands.
     */
    private final MediaConnectionService mediaConnectionService;

    /**
     * The {@link TosService} used at bootstrap to refresh the interoperability Terms-of-Service acceptance state when
     * notice fetching is enabled.
     */
    private final TosService tosService;

    /**
     * The one-shot guard ensuring the bootstrap runs at most once per connection; cleared by {@link #reset()} on
     * socket teardown.
     */
    private final AtomicBoolean started;

    /**
     * The currently scheduled media-connection refresh job, or {@code null} when none is pending.
     *
     * <p>Held so {@link #reset()} can cancel the next refresh when the socket is torn down; re-armed at the end of
     * every {@link #refreshMediaConnection()} pass.
     */
    private volatile ScheduledTask mediaConnectionRefreshJob;

    /**
     * Constructs a new success stream handler bound to the given services.
     *
     * @param whatsapp                         the {@link LinkedWhatsAppClient}; must not be {@code null}
     * @param abPropsService                   the {@link ABPropsService}; must not be {@code null}
     * @param deviceService                    the {@link DeviceService}; must not be {@code null}
     * @param lidMigrationService              the {@link LidMigrationService}; must not be {@code null}
     * @param inactiveGroupLidMigrationService the {@link InactiveGroupLidMigrationService}; must not be {@code null}
     * @param wamService                       the {@link WamService}; must not be {@code null}
     * @param webAppStateService               the {@link WebAppStateService}; must not be {@code null}
     * @param mediaConnectionService           the {@link MediaConnectionService}; must not be {@code null}
     * @param tosService                       the {@link TosService}; must not be {@code null}
     * @throws NullPointerException if any service is {@code null}
     */
    public SuccessStreamHandler(
            LinkedWhatsAppClient whatsapp,
            ABPropsService abPropsService,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            InactiveGroupLidMigrationService inactiveGroupLidMigrationService,
            WamService wamService,
            WebAppStateService webAppStateService,
            MediaConnectionService mediaConnectionService,
            TosService tosService
    ) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.inactiveGroupLidMigrationService = Objects.requireNonNull(inactiveGroupLidMigrationService, "inactiveGroupLidMigrationService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.webAppStateService = Objects.requireNonNull(webAppStateService, "webAppStateService cannot be null");
        this.mediaConnectionService = Objects.requireNonNull(mediaConnectionService, "mediaConnectionService cannot be null");
        this.tosService = Objects.requireNonNull(tosService, "tosService cannot be null");
        this.started = new AtomicBoolean();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drives {@link #bootstrap(Stanza)} the first time the {@code <success>} stanza is observed on the current
     * connection; any later {@code <success>} stanzas are no-ops until {@link #reset()} runs on socket teardown.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleSuccess", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Stanza stanza) {
        if (started.compareAndSet(false, true)) {
            bootstrap(stanza);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the one-shot bootstrap guard so the next {@code <success>} stanza after a reconnection re-runs the full
     * sequence, and cancels any pending media-connection refresh job.
     */
    @Override
    public void reset() {
        started.set(false);
        var job = mediaConnectionRefreshJob;
        if (job != null) {
            job.cancel();
            mediaConnectionRefreshJob = null;
        }
    }

    /**
     * Drives the one-shot post-handshake bootstrap sequence: parses the {@code <success>} attributes, updates the
     * {@code me} identity, syncs A/B props, primes LID migration, starts the WAM, device and inactive-group services,
     * resumes app-state syncing, transitions the socket out of passive mode, fans out
     * {@link LoggedInListener#onLoggedIn(LinkedWhatsAppClient)} and persists the store.
     *
     * <p>Reachable only via {@link #handle(Stanza)} the first time a {@code <success>} stanza is observed on the current
     * connection.
     *
     * @implNote This implementation runs the clock-skew normalisation, AB-prop sync, WAM initialisation, device-service
     * kick-off, inactive-group migration, web-app-state resume, initial pre-key upload via
     * {@link #uploadInitialPreKeysIfNeeded()}, passive-mode iq, {@link LinkedWhatsAppClient#editPresence(ContactStatus)}
     * broadcast, compliance probes, collection listener replay, newsletter and business-profile bootstraps in the exact
     * order required by WA Web's await chain. The pre-key upload runs before {@link LinkedWhatsAppClient#enableActiveMode()}
     * so the primary device can establish the Signal session it needs to encrypt history-sync messages as soon as the
     * companion goes active. The {@link ContactStatus#AVAILABLE} presence broadcast that follows tells the relay the
     * companion is online so the server flushes the queued primary-to-companion {@code <message>} envelopes (history-
     * sync notifications among them) instead of parking them until the next presence broadcast. Newsletter and
     * business-profile bootstraps are fire-and-forget on virtual threads because their server round trips can be slow
     * and must not block the listener fan-out; the final store save is best-effort because the bootstrap must not fail
     * on a serializer hiccup.
     *
     * @param stanza the parsed {@code <success>} stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleSuccess", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void bootstrap(Stanza stanza) {
        var store = whatsapp.store();

        var replayChats = store.syncStore().syncedChats();
        var replayContacts = store.syncStore().syncedContacts();
        var replayNewsletters = store.syncStore().syncedNewsletters();
        var replayStatus = store.syncStore().syncedStatus();

        store.accountStore().setOnline(true);
        store.accountStore().setRegistered(true);

        var serverTimestampSeconds = stanza.getAttributeAsLong("t", 0L);
        if (serverTimestampSeconds > 0) {
            var localSeconds = Instant.now().getEpochSecond();
            var skewSeconds = serverTimestampSeconds - localSeconds;
            var clockSkewHourly = (int) Math.round(skewSeconds / 3600.0);
            if (clockSkewHourly != 0 && abPropsService.getBool(ABProp.LOG_CLOCK_SKEW)) {
                wamService.commit(new ClockSkewDifferenceTEventBuilder()
                        .clockSkewHourly(clockSkewHourly)
                        .build());
            }
            store.syncStore().setClockSkewSeconds(skewSeconds);
        }

        stanza.getAttributeAsJid("lid").ifPresent(store.accountStore()::setLid);

        var displayName = stanza.getAttributeAsString("display_name", null);
        if (displayName != null && !displayName.isBlank()) {
            var oldName = store.accountStore().name().orElse(null);
            store.accountStore().setName(displayName);
            if (!Objects.equals(oldName, displayName)) {
                for (var listener : store.listeners()) {
                    if (listener instanceof LinkedNameChangedListener typed) {
                        Thread.startVirtualThread(() -> typed.onNameChanged(whatsapp, oldName, displayName));
                    }
                }
            }
        }

        lidMigrationService.initialize();

        var abPropsSynced = abPropsService.sync();

        if (abPropsSynced && abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_ENABLED)) {
            lidMigrationService.enableMigration();
        } else {
            lidMigrationService.disableMigration();
        }

        wamService.initialize();

        // Pull the interoperability Terms-of-Service acceptance state so the inbound-message gating
        // check has it; gated on the fetch flag and run off-thread so it does not delay bootstrap
        if (abPropsService.getBool(ABProp.TOS_CLIENT_STATE_FETCH_ENABLED)) {
            Thread.ofVirtual().start(() -> tosService.refresh(List.of(TosNotice.TOS_3)));
        }

        deviceService.startAdvCheckScheduler();
        deviceService.retryPendingSyncs();
        deviceService.updateMissingKeyDevices();

        inactiveGroupLidMigrationService.start();

        var groupAbpropsRefreshId = stanza.getAttributeAsLong("group_abprops", 0L);
        if (groupAbpropsRefreshId != 0L && serverTimestampSeconds > 0
                && groupAbpropsRefreshId != store.syncStore().groupAbPropsRefreshId()) {
            store.syncStore().setGroupAbPropsEmergencyPushTimestamp(Instant.ofEpochSecond(serverTimestampSeconds));
            store.syncStore().setGroupAbPropsRefreshId(groupAbpropsRefreshId);
        }

        webAppStateService.resumeAfterRestart();
        webAppStateService.startPeriodicSyncJob();

        refreshMediaConnection();

        uploadInitialPreKeysIfNeeded();

        whatsapp.enableActiveMode();

        whatsapp.editPresence(ContactStatus.AVAILABLE);

        runComplianceProbe("reachout timelock", whatsapp::queryReachoutTimelock);

        runComplianceProbe("new-chat capping info", whatsapp::queryNewChatMessageCappingInfo);

        syncPrivacySettings();

        syncPrivacyDisallowedListsMex();

        for (var listener : store.listeners()) {
            if (listener instanceof LoggedInListener typed) {
                Thread.startVirtualThread(() -> typed.onLoggedIn(whatsapp));
            }
        }

        replayCachedCollectionListeners(replayChats, replayContacts, replayNewsletters, replayStatus);

        bootstrapNewsletterBackend();

        bootstrapBusinessCertificate();

        if (store.accountStore().clientType() == LinkedWhatsAppClientType.WEB) {
            Thread.startVirtualThread(() -> {
                try {
                    whatsapp.refreshWhatsAppWebGraphQlSession();
                } catch (WhatsAppWebGraphQlException.SessionUnseeded _) {

                } catch (WhatsAppWebGraphQlException exception) {
                    LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                            "WhatsApp Web GraphQL credentials auto-refresh failed", exception);
                }
            });
            if (store.accountStore().primaryPlatform().filter(LinkedPrimaryPlatform::isBusiness).isPresent()
                    && store.webSessionStore().facebookGraphQlSession().isEmpty()) {
                Thread.startVirtualThread(() -> {
                    try {
                        whatsapp.refreshFacebookGraphQlSession();
                    } catch (WhatsAppFacebookGraphQlException.SessionUnseeded _) {

                    } catch (WhatsAppFacebookGraphQlException exception) {
                        LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                                "Facebook GraphQL credentials auto-refresh failed", exception);
                    }
                });
            }
        }

        try {
            store.save();
        } catch (Exception ignored) {
        }
    }

    /**
     * Replays the cached chats, contacts, newsletters and status collections to every registered listener for each
     * collection that was already synced when the connection was established.
     *
     * <p>Surfaces each dataset exactly once per login through
     * {@link LinkedChatsListener#onChats(LinkedWhatsAppClient, java.util.Collection)},
     * {@link LinkedContactsListener#onContacts(LinkedWhatsAppClient, java.util.Collection)},
     * {@link LinkedNewslettersListener#onNewsletters(LinkedWhatsAppClient, java.util.Collection)} and
     * {@link LinkedStatusListener#onStatus(LinkedWhatsAppClient, java.util.Collection)}. This path covers the reconnect
     * case, where the data was synced in a prior session and read back from the persisted store: the history-sync
     * pipeline will not re-deliver it, so a fresh listener would otherwise never see it. A collection that was still
     * unsynced at connection time is deliberately skipped here, because
     * {@link com.github.auties00.cobalt.sync.WebHistorySyncService} fires its callback once the first chunk lands;
     * replaying it as well would double-fire the listener. The callback fires even when the cached collection is empty
     * so embedders can rely on a one-shot post-login signal.
     *
     * @param replayChats       whether the chats collection was already synced at connection time
     * @param replayContacts    whether the contacts collection was already synced at connection time
     * @param replayNewsletters whether the newsletters collection was already synced at connection time
     * @param replayStatus      whether the status collection was already synced at connection time
     * @implNote This implementation gates on the connection-time snapshot rather than the live sync gates so that a
     * fresh login, whose gates flip mid-bootstrap while this thread blocks on compliance and privacy round-trips, does
     * not replay a collection that {@link com.github.auties00.cobalt.sync.WebHistorySyncService} has already delivered.
     * Each callback fans out on a fresh virtual thread so a slow listener cannot stall the bootstrap. WA Web has no
     * equivalent because its UI components subscribe directly to the reactive collections.
     */
    private void replayCachedCollectionListeners(boolean replayChats, boolean replayContacts,
                                                 boolean replayNewsletters, boolean replayStatus) {
        var store = whatsapp.store();
        var listeners = store.listeners();
        if (listeners.isEmpty()) {
            return;
        }
        if (replayChats) {
            var chats = store.chatStore().chats();
            for (var listener : listeners) {
                if (listener instanceof LinkedChatsListener typed) {
                    Thread.startVirtualThread(() -> typed.onChats(whatsapp, chats));
                }
            }
        }
        if (replayContacts) {
            var contacts = store.contactStore().contacts();
            for (var listener : listeners) {
                if (listener instanceof LinkedContactsListener typed) {
                    Thread.startVirtualThread(() -> typed.onContacts(whatsapp, contacts));
                }
            }
        }
        if (replayNewsletters) {
            var newsletters = store.chatStore().newsletters();
            for (var listener : listeners) {
                if (listener instanceof LinkedNewslettersListener typed) {
                    Thread.startVirtualThread(() -> typed.onNewsletters(whatsapp, newsletters));
                }
            }
        }
        if (replayStatus) {
            var status = store.chatStore().status();
            for (var listener : listeners) {
                if (listener instanceof LinkedStatusListener typed) {
                    Thread.startVirtualThread(() -> typed.onStatus(whatsapp, status));
                }
            }
        }
    }

    /**
     * Drives the one-shot newsletter metadata fetch that backfills the newsletter collection on first login.
     *
     * <p>Gated on three conditions: this is a web client (newsletters are a web-only companion feature), the configured
     * history settings include newsletters (see {@link com.github.auties00.cobalt.store.linked.LinkedWhatsAppSyncStore#hasHistoryNewsletters()}), and the
     * newsletter sync gate is still false. The {@link LinkedWhatsAppClient#refreshNewsletters()} call sets the gate and fans
     * out {@link LinkedNewslettersListener#onNewsletters(LinkedWhatsAppClient, java.util.Collection)} internally.
     *
     * @implNote This implementation runs the fetch on a fresh virtual thread because the round trip can be slow on
     * first install; failures are logged through {@link #LOGGER_COMPLIANCE} and swallowed so the rest of the bootstrap
     * is unaffected.
     */
    @WhatsAppWebExport(moduleName = "WAWebBootstrapNewsletter", exports = "bootstrapNewsletterBackend",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void bootstrapNewsletterBackend() {
        var store = whatsapp.store();
        if (store.accountStore().clientType() != LinkedWhatsAppClientType.WEB || store.syncStore().syncedNewsletters()) {
            return;
        }
        if (!store.syncStore().hasHistoryNewsletters()) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                whatsapp.refreshNewsletters();
            } catch (Exception exception) {
                LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                        "Initial newsletter metadata fetch failed: {0}",
                        exception.getMessage());
            }
        });
    }

    /**
     * Drives the one-shot business-profile fetch that backfills the verified-name and business-profile fields when the
     * business-certificate sync gate is still false on bootstrap.
     *
     * <p>The {@link com.github.auties00.cobalt.stream.notification.business.NotificationBusinessStreamHandler} already
     * flips the gate when the primary device pushes a {@code verified_name} or {@code profile} notification; this
     * proactive call covers the case where the companion has just paired (or has been re-paired after invalidation) and
     * the primary has not yet emitted the notification. The flag is set after the call regardless of result so
     * non-business accounts do not re-query on every reconnect.
     *
     * @implNote This implementation runs the fetch on a fresh virtual thread and applies the resulting
     * {@link BusinessProfile} via {@link #applyOwnBusinessProfile(BusinessProfile)} so the bootstrap-fetch path
     * produces the same store mutations as the notification path.
     */
    private void bootstrapBusinessCertificate() {
        var store = whatsapp.store();
        if (store.syncStore().syncedBusinessCertificate()) {
            return;
        }
        var self = store.accountStore().jid().orElse(null);
        if (self == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                whatsapp.queryBusinessProfile(self.withoutData())
                        .ifPresent(this::applyOwnBusinessProfile);
            } catch (Exception exception) {
                LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                        "Initial business certificate fetch failed: {0}",
                        exception.getMessage());
            } finally {
                store.syncStore().setSyncedBusinessCertificate(true);
            }
        });
    }

    /**
     * Applies the fields lifted from a freshly-fetched {@link BusinessProfile} onto the store so the bootstrap fetch
     * produces the same resulting state as the notification path.
     *
     * <p>Keeping the two paths field-aligned with the
     * {@link com.github.auties00.cobalt.stream.notification.business.NotificationBusinessStreamHandler} copy ensures
     * the embedder sees the same observable store state regardless of which path won the race on a fresh pair.
     *
     * @param profile the freshly-fetched {@link BusinessProfile}
     */
    private void applyOwnBusinessProfile(BusinessProfile profile) {
        whatsapp.store().accountStore().setBusinessDescription(profile.description().orElse(null))
                .setBusinessAddress(profile.address().orElse(null))
                .setBusinessEmail(profile.email().orElse(null))
                .setBusinessWebsites(profile.websites())
                .setBusinessCategories(profile.categories());
    }

    /**
     * The system logger reused by every post-success compliance probe so probe failures are surfaced as warnings
     * without aborting the bootstrap.
     */
    private static final System.Logger LOGGER_COMPLIANCE = System.getLogger(SuccessStreamHandler.class.getName() + ".compliance");

    /**
     * The floor delay between successive media-connection refresh attempts.
     *
     * <p>Bounds the schedule away from zero when the server returns a tiny or zero TTL so a malformed response cannot
     * turn the refresh loop into a tight spin.
     */
    private static final Duration MEDIA_CONNECTION_MIN_REFRESH_DELAY = Duration.ofSeconds(30);

    /**
     * The delay before retrying a failed media-connection refresh.
     *
     * <p>Used when the {@code media_conn} query throws so a transient WhatsApp Web GraphQL error does not permanently halt the renewal
     * loop.
     */
    private static final Duration MEDIA_CONNECTION_RETRY_DELAY = Duration.ofMinutes(1);

    /**
     * Runs a single post-success MEX compliance probe and logs any failure without re-throwing.
     *
     * <p>Probes mirror the housekeeping the official client emits at app launch (reachout timelock, new-chat capping
     * info). They are fire-and-forget on Cobalt because the responses have no store slot yet; only the
     * server-observable round trip matters for compliance.
     *
     * @param probeName the human-readable probe name used in log output
     * @param probe     the probe to run; must not be {@code null}
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
     * The pre-key batch size uploaded once per device when the local store has not yet generated any pre-keys.
     *
     * @implNote This value matches WA Web's {@code UPLOAD_KEYS_COUNT}; a larger batch reduces the chance of pre-key
     * exhaustion before the steady-state pre-key-low replenishment can fire.
     */
    private static final long INITIAL_PRE_KEYS_COUNT = 812;

    /**
     * Uploads an initial Signal pre-key batch when the local store has none yet.
     *
     * <p>On a fresh pair the companion has not yet uploaded any one-time pre-keys to the server, so the primary device
     * cannot fetch a pre-key bundle to establish the Signal session it needs to encrypt the history-sync
     * {@code <message>} envelopes. The upload runs before {@link LinkedWhatsAppClient#enableActiveMode()} so the primary can
     * encrypt and push history-sync notifications as soon as the companion goes active. Subsequent reconnects observe
     * an already-populated pre-key store and skip the upload.
     *
     * @implNote WA Web runs the equivalent upload as a passive task registered before the passive-mode iq; Cobalt has
     * no passive-task pipeline, so the upload is inlined here. Failures are logged through {@link #LOGGER_COMPLIANCE}
     * and swallowed so a transient WhatsApp Web GraphQL error does not abort the rest of the post-success bootstrap; the next
     * pre-key-low {@code <notification type="encrypt">} push retries the upload.
     */
    @WhatsAppWebExport(moduleName = "WAWebRegisterPassiveTasks",
            exports = "registerPassiveTaskForStartUp", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebUploadPrekeysForRegTask", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void uploadInitialPreKeysIfNeeded() {
        if (whatsapp.store().signalStore().hasPreKeys()) {
            return;
        }
        try {
            whatsapp.sendPreKeys(INITIAL_PRE_KEYS_COUNT);
        } catch (Throwable throwable) {
            LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                    "Initial pre-key upload after pairing failed: {0}",
                    throwable.getMessage());
        }
    }

    /**
     * Fetches a fresh media connection, publishes it to the shared {@link MediaConnectionService}, and arms the next
     * refresh tick.
     *
     * <p>Invoked from {@link #bootstrap(Stanza)} so the very first upload or download path sees a populated connection.
     * The next refresh is re-armed at the end of every pass on the cadence advertised by the server, so a long quiet
     * period followed by a sudden download burst does not stall waiting for a fresh {@code media_conn} round trip. The
     * scheduled task is cancelled by {@link #reset()} on socket teardown.
     *
     * @implNote This implementation diverges from WA Web's lazy-on-demand refresh, which fires fresh queries only when
     * an upload or download path observes a stale singleton. Cobalt schedules the next refresh eagerly on a virtual
     * thread; the cadence follows {@link MediaConnectionService#needsRefresh()}'s
     * {@code min(ttl, floor(0.8 * authTtl))} formula, clamped to {@link #MEDIA_CONNECTION_MIN_REFRESH_DELAY} so a
     * malformed response cannot produce a zero or negative delay. On failure the next tick fires after
     * {@link #MEDIA_CONNECTION_RETRY_DELAY} so a transient WhatsApp Web GraphQL error does not permanently halt the loop.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob",
            exports = "queryMediaConn", adaptation = WhatsAppAdaptation.ADAPTED)
    private void refreshMediaConnection() {
        Duration nextDelay;
        try {
            var response = queryMediaConnection();
            mediaConnectionService.update(response);
            var ttlSeconds = (long) mediaConnectionService.ttl();
            var authThresholdSeconds = (long) Math.floor(mediaConnectionService.authTtl() * 0.8);
            var delay = Duration.ofSeconds(Math.min(ttlSeconds, authThresholdSeconds));
            nextDelay = delay.compareTo(MEDIA_CONNECTION_MIN_REFRESH_DELAY) < 0
                    ? MEDIA_CONNECTION_MIN_REFRESH_DELAY
                    : delay;
        } catch (Throwable throwable) {
            LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                    "Media-connection refresh failed, retrying in {0}s: {1}",
                    MEDIA_CONNECTION_RETRY_DELAY.toSeconds(), throwable.getMessage());
            nextDelay = MEDIA_CONNECTION_RETRY_DELAY;
        }
        mediaConnectionRefreshJob = ScheduledTask.scheduleDelayed(nextDelay, this::refreshMediaConnection);
    }

    /**
     * Sends a fresh {@code media_conn} IQ and returns the response stanza.
     *
     * <p>The reply is fed into {@link MediaConnectionService#update(Stanza)} by {@link #refreshMediaConnection()}. Throws
     * when the server returns a malformed or error response so the caller can apply the retry-delay backoff.
     *
     * @return the IQ response stanza
     * @throws WhatsAppServerRuntimeException if the server rejects the query or returns an unrecognised response
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob",
            exports = "queryMediaConn", adaptation = WhatsAppAdaptation.DIRECT)
    private Stanza queryMediaConnection() {
        var request = new IqQueryMediaConnsRequest();
        var requestBuilder = request.toStanza();
        var response = whatsapp.sendNode(requestBuilder);
        return switch (IqQueryMediaConnsResponse.of(response, requestBuilder.build()).orElse(null)) {
            case IqQueryMediaConnsResponse.Success _ -> response;
            case IqQueryMediaConnsResponse.ClientError clientError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query media conns rejected: code=" + clientError.errorCode()
                            + ", text=" + clientError.errorText().orElse(null));
            case IqQueryMediaConnsResponse.ServerError serverError ->
                    throw new WhatsAppServerRuntimeException(
                            "Query media conns server error: code=" + serverError.errorCode()
                            + ", text=" + serverError.errorText().orElse(null));
            case null -> throw new WhatsAppServerRuntimeException(
                    "Query media conns: response did not match any documented variant");
        };
    }

    /**
     * Fetches the account privacy settings during the post-success bootstrap so that
     * {@link LinkedWhatsAppSettingsStore#privacySettings()} is populated before the
     * {@code onLoggedIn} listeners fire.
     *
     * <p>Issues the {@code <iq xmlns="privacy" type="get">} query through
     * {@link LinkedWhatsAppClient#refreshPrivacySettings()}, which stores each returned category.
     *
     * @implNote This implementation swallows a failure through {@link #LOGGER_COMPLIANCE} so a transient WhatsApp Web GraphQL error
     * does not abort the rest of the bootstrap; the settings can still be re-queried on demand.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncPrivacyDisallowedLists",
            exports = "syncPrivacyDisallowedLists", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebQueryPrivacySettingsJob", exports = "getPrivacySettings",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void syncPrivacySettings() {
        try {
            whatsapp.refreshPrivacySettings();
        } catch (Throwable throwable) {
            LOGGER_COMPLIANCE.log(System.Logger.Level.WARNING,
                    "Cannot fetch privacy settings: {0}",
                    throwable.getMessage());
        }
    }

    private void syncPrivacyDisallowedListsMex() {
        var meLid = whatsapp.store().accountStore().lid().orElse(null);
        if (meLid == null) {
            return;
        }
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
