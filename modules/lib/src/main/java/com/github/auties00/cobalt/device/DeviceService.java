package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.device.DeviceConstants;
import com.github.auties00.cobalt.device.adv.DeviceADVChecker;
import com.github.auties00.cobalt.device.adv.DeviceADVValidator;
import com.github.auties00.cobalt.device.adv.ValidatedKeyIndexListResult;
import com.github.auties00.cobalt.device.fanout.DeviceFanoutCalculator;
import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.device.fanout.DevicePhashCalculator;
import com.github.auties00.cobalt.device.fanout.DevicePhashVersion;
import com.github.auties00.cobalt.device.icdc.HostedIcdcResult;
import com.github.auties00.cobalt.device.icdc.IcdcComputer;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.device.key.DevicePreKeyHandler;
import com.github.auties00.cobalt.device.stanza.DeviceUSyncQueryBuilder;
import com.github.auties00.cobalt.device.stanza.DeviceUSyncResponseParser;
import com.github.auties00.cobalt.device.timestamp.DeviceExpectedTsUtils;
import com.github.auties00.cobalt.exception.WhatsAppAdvValidationException;
import com.github.auties00.cobalt.exception.WhatsAppDeviceSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.device.DeviceListMetadata;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.info.*;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.CoexPrivacySysMsgEventBuilder;
import com.github.auties00.cobalt.wam.event.ContactSyncEventEventBuilder;
import com.github.auties00.cobalt.wam.type.CoexSysMsgStateTransitionAttempt;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrates all device list operations for WhatsApp Multi-Device. Synchronises companion
 * device lists over USync, processes real-time device add/remove notifications, computes
 * message fanout for 1:1 and group sends, attaches ICDC metadata to outgoing messages, and
 * verifies business coexistence (hosted) transitions.
 *
 * <p>Every outgoing message and every decrypted PKMSG routes through this service so the
 * client keeps a consistent view of each peer's companion devices and identity keys. A daily
 * ADV expiration check runs via {@link DeviceADVChecker} to invalidate stale records and
 * trigger proactive syncs.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvSyncDeviceListApi")
@WhatsAppWebModule(moduleName = "WAWebAdvHandlerApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvOmittedResultApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvDeviceNotificationApi")
@WhatsAppWebModule(moduleName = "WAWebIcdcHandlerApi")
@WhatsAppWebModule(moduleName = "WAWebApiDeviceList")
public final class DeviceService {
    /**
     * Logger for device service operations.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceService.class.getName());

    /**
     * Structured-concurrency joiner that fans out one USync IQ per batch and collates the
     * parsed {@link DeviceListResult} entries from all batches into a single flat list.
     * Failed subtasks are swallowed so a single failed batch does not lose the successful
     * results from its siblings. Failures surface later when the expected JID is missing
     * from the collated output and callers fall back to the primary-only device list.
     */
    private static final Joiner<List<DeviceListResult>, List<DeviceListResult>> JOINER = new Joiner<>() {
        private final List<Subtask<? extends List<DeviceListResult>>> subtasks = new ArrayList<>();

        @Override
        public boolean onFork(Subtask<? extends List<DeviceListResult>> subtask) {
            Objects.requireNonNull(subtask, "subtask cannot be null");

            if(subtask.state() != Subtask.State.UNAVAILABLE) {
                throw new IllegalStateException("Subtask should not be available");
            }

            subtasks.add(subtask);
            return false;
        }

        @Override
        public boolean onComplete(Subtask<? extends List<DeviceListResult>> subtask) {
            Objects.requireNonNull(subtask, "subtask cannot be null");

            return switch (subtask.state()) {
                case UNAVAILABLE -> throw new IllegalStateException("Subtask is not completed");
                case SUCCESS -> true;
                case FAILED -> false;
            };
        }

        @Override
        public List<DeviceListResult> result() {
            return subtasks.stream()
                    .flatMap(subtask -> subtask.get().stream())
                    .toList();
        }
    };

    /**
     * Client used to send IQs and access shared configuration.
     */
    private final WhatsAppClient client;

    /**
     * Web app-state service used to schedule the all-devices-responded grace-period check
     * after a device removal leaves every remaining device unable to produce a requested
     * sync key.
     */
    private final WebAppStateService webAppStateService;

    /**
     * Store for persisting and retrieving device lists, identities, and sessions.
     */
    private final WhatsAppStore store;

    /**
     * Tracks in-flight USync fetches per user JID so concurrent callers wait on the same
     * future instead of issuing duplicate IQs.
     */
    private final ConcurrentHashMap<Jid, CompletableFuture<DeviceList>> pendingFetches;

    /**
     * Scheduler that runs the daily ADV device info expiration check.
     */
    private final DeviceADVChecker advCheckScheduler;

    /**
     * Handler that fetches and stores Signal pre-key bundles and identity keys.
     */
    private final DevicePreKeyHandler preKeyHandler;

    /**
     * AB property service used for feature gating (hosted devices, username sync,
     * expiration thresholds).
     */
    private final ABPropsService abPropsService;

    /**
     * Validator for ADV signatures on device identities and key index lists.
     */
    private final DeviceADVValidator advValidator;

    /**
     * Parser that turns USync IQ response nodes into structured device list results.
     */
    private final DeviceUSyncResponseParser usyncResponseParser;

    /**
     * Calculator that produces the per-message fanout device set.
     */
    private final DeviceFanoutCalculator fanoutCalculator;

    /**
     * Computer for ICDC (Identity Change Detection Consistency) metadata attached to every
     * outgoing message's {@code messageContextInfo}.
     */
    private final IcdcComputer icdcComputer;

    /**
     * Participant-hash (phash) calculator used to verify sender and server agree on the
     * group recipient list.
     */
    private final DevicePhashCalculator phashCalculator;

    /**
     * Serializes updates that must remain consistent across the device-list, session,
     * sender-key, missing-keys and contact tables.
     */
    private final ReentrantLock deviceUpdateLock;

    /**
     * Maps user JID to the second at which a hosted system message was last created, so
     * duplicate insertions within the same second are skipped.
     */
    private final ConcurrentHashMap<Jid, Instant> hostedSystemMsgDedupCache;

    /**
     * Senders for which offline hosted ICDC metadata has already been processed in the
     * current connection. Cleared on reconnect.
     */
    private final Set<Jid> offlineBizHostedSenderICDCProcessedCache;

    /**
     * WAM telemetry service used to commit device-related events.
     */
    private final WamService wamService;

    /**
     * Constructs the device service and instantiates every helper it owns. Collaborators
     * receive the same store and AB props dependencies so they operate on a single
     * consistent view of device state.
     *
     * @param client             the WhatsApp client providing store and network access
     * @param webAppStateService the web app-state service for missing-key grace-period scheduling
     * @param abPropsService     the AB props service for feature gating
     * @param sessionCipher      the Signal session cipher used by the pre-key handler
     * @param wamService         the WAM telemetry service for committing device events
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceService(WhatsAppClient client, WebAppStateService webAppStateService, ABPropsService abPropsService, SignalSessionCipher sessionCipher, WamService wamService) {
        this.client = client;
        this.webAppStateService = webAppStateService;
        this.store = client.store();
        this.preKeyHandler = new DevicePreKeyHandler(client, sessionCipher);
        this.abPropsService = abPropsService;
        this.advValidator = new DeviceADVValidator(store, abPropsService);
        this.fanoutCalculator = new DeviceFanoutCalculator(abPropsService);
        this.icdcComputer = new IcdcComputer(store, abPropsService);
        this.phashCalculator = new DevicePhashCalculator(abPropsService);
        this.usyncResponseParser = new DeviceUSyncResponseParser(advValidator);
        this.pendingFetches = new ConcurrentHashMap<>();
        this.advCheckScheduler = new DeviceADVChecker(client, this, abPropsService, wamService);
        this.deviceUpdateLock = new ReentrantLock();
        this.hostedSystemMsgDedupCache = new ConcurrentHashMap<>();
        this.offlineBizHostedSenderICDCProcessedCache = ConcurrentHashMap.newKeySet();
        this.wamService = wamService;
    }

    /**
     * Runs the given task while holding the device update lock.
     *
     * @param task the task to execute under the lock
     */
    @WhatsAppWebExport(moduleName = "WAWebApiGetDeviceUpdateLock",
            exports = "getDeviceUpdateLock",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void withDeviceUpdateLock(Runnable task) {
        deviceUpdateLock.lock();
        try {
            task.run();
        } finally {
            deviceUpdateLock.unlock();
        }
    }

    /**
     * Returns whether the hosted-override account-signature-key feature is enabled. When
     * enabled, the embedded {@code accountSignatureKey} from the protobuf is used to verify
     * hosted-device identities instead of requiring a stored identity.
     *
     * @return {@code true} if both AB props gate the feature on
     */
    private boolean isHostedOverrideAdvAccountSignatureKeyEnabled() {
        var hostedDevicesEnabled = abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
        if (!hostedDevicesEnabled) {
            return false;
        }
        return abPropsService.getBool(ABProp.OVERRIDE_ADV_ACCOUNT_SIGNATURE_KEY_ENABLED);
    }

    /**
     * Returns the device lists for the specified users, optionally short-circuiting on a
     * phash pre-check. When {@code expectedPhash} is non-null and matches the locally
     * computed phash, the server sync is skipped entirely. This is an optimisation for
     * group messages where the sender already knows the expected participant hash.
     *
     * @param userJids              the user JIDs to get device lists for
     * @param context               the sync context (for example {@code "message"},
     *                              {@code "interactive"}, {@code "adv_expiration"})
     * @param expectedPhash         optional phash to compare against the local device list;
     *                              when it matches, the server sync is skipped
     * @param shouldMergeAltDevices whether to merge PN/LID alternate device lists for the
     *                              same user
     * @return the device lists, one per user JID
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = {"syncDeviceList", "syncAndGetDeviceList"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices) {
        if (expectedPhash != null && !expectedPhash.isEmpty()) {
            try {
                // WA Web treats missing/deleted records as empty device lists for phash purposes.
                var cachedLists = userJids.stream()
                        .map(jid -> store.findDeviceList(jid).orElse(null))
                        .toList();

                var allDeviceJids = cachedLists.stream()
                        .filter(list -> list != null && !list.deleted())
                        .map(DeviceList::deviceJids)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toUnmodifiableSet());

                var localPhash = phashCalculator.calculate(allDeviceJids, DevicePhashVersion.V2, true);

                if (localPhash.equals(expectedPhash)) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Phash pre-check passed, skipping device sync");
                    var nonNullLists = cachedLists.stream()
                            .filter(Objects::nonNull)
                            .toList();
                    return mergeAlternateDeviceLists(nonNullLists);
                }
            } catch (NoSuchAlgorithmException e) {
                // Fall through to a normal sync when phash cannot be computed.
            }
        }

        var result = new HashSet<DeviceList>();
        var missingJids = new ArrayList<Jid>();

        for (var jid : userJids) {
            var cached = store.findDeviceList(jid);
            if (cached.isEmpty()) {
                missingJids.add(jid);
                continue;
            }

            var deviceList = cached.get();
            if (!deviceList.deleted() || deviceList.deletedChangedToHost()) {
                result.add(deviceList);
                continue;
            }

            // Deleted but not due to a hosted transition: fall back to a primary-only list.
            var fallback = createPrimaryOnlyDeviceList(jid);
            store.addDeviceList(fallback);
            result.add(fallback);
        }

        if (!missingJids.isEmpty()) {
            var fetched = fetchDeviceListsFromServer(missingJids, context);
            for (var deviceList : fetched) {
                store.addDeviceList(deviceList);
                result.add(deviceList);
            }
        }

        if (shouldMergeAltDevices) {
            return mergeAlternateDeviceLists(result);
        }

        return result;
    }

    /**
     * Merges device lists for users who have both PN and LID identities. During LID
     * migration, users may have device records under both their phone number (PN) and
     * LID. The PN identity is treated as canonical and takes precedence on collision.
     *
     * @param deviceLists the device lists to merge
     * @return merged device lists, one entry per canonical user
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils",
            exports = "toPn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Set<DeviceList> mergeAlternateDeviceLists(Collection<DeviceList> deviceLists) {
        var mergedMap = new LinkedHashMap<Jid, DeviceList>();

        for (var deviceList : deviceLists) {
            var userJid = deviceList.userJid();
            var canonicalJid = userJid;

            if (userJid.hasLidServer()) {
                var phoneJid = store.findPhoneByLid(userJid)
                        .orElse(null);
                if (phoneJid != null) {
                    canonicalJid = phoneJid;
                }
            }

            mergedMap.merge(canonicalJid, deviceList, DeviceList::merge);
        }

        return Set.copyOf(mergedMap.values());
    }

    /**
     * Logs a diagnostic when any of the eligible phone-number JIDs in {@code userJids}
     * lacks a LID mapping. Bots, hosted users (both {@code hosted} and {@code hosted.lid}
     * servers) and LIDs are excluded from the eligible set.
     *
     * @param userJids the user JIDs to check
     * @param caller   the caller context for the log line; defaults to {@code "unknown"}
     *                 when {@code null}, matching WA Web
     */
    @WhatsAppWebExport(moduleName = "WAWebApiContact",
            exports = "checkPnToLidMapping",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void checkPnToLidMapping(Collection<Jid> userJids, String caller) {
        var phoneNumberJids = new LinkedHashSet<Jid>();
        for (var jid : userJids) {
            if (jid.isBot() || jid.hasHostedServer() || jid.hasHostedLidServer() || jid.hasLidServer()) {
                continue;
            }
            phoneNumberJids.add(jid.toUserJid());
        }

        var missingMappings = new LinkedHashSet<Jid>();
        for (var jid : phoneNumberJids) {
            if (store.findLidByPhone(jid).isEmpty()) {
                missingMappings.add(jid);
            }
        }

        if (!missingMappings.isEmpty()) {
            var resolvedCaller = caller != null ? caller : "unknown";
            LOGGER.log(System.Logger.Level.WARNING,
                    "LID null - {0} PNs, missing: {1}, caller: {2}",
                    phoneNumberJids.size(), missingMappings.size(), resolvedCaller);
        }
    }

    /**
     * Fetches device lists from the server with request deduplication. If a fetch is
     * already in progress for a JID, waits on that result rather than issuing a duplicate
     * IQ. USync queries carry an optional {@code device_hash} (dhash) for delta updates.
     * When the server's dhash matches the local one, the response is "omitted" and the
     * cached device list is preserved.
     *
     * @param userJids the user JIDs to fetch
     * @param context  the sync context (for example {@code "message"},
     *                 {@code "interactive"}, {@code "adv_expiration"})
     * @return device lists fetched or resolved from cache
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebAdvHandlerApi",
            exports = "handleADVDeviceSyncResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    private List<DeviceList> fetchDeviceListsFromServer(Collection<Jid> userJids, String context) {
        var result = new ArrayList<DeviceList>();
        var toFetch = new HashSet<Jid>();

        for (var jid : userJids) {
            var pendingFuture = pendingFetches.get(jid);
            if (pendingFuture != null) {
                try {
                    result.add(pendingFuture.join());
                } catch (Exception e) {
                    toFetch.add(jid);
                }
            } else {
                toFetch.add(jid);
            }
        }

        if (toFetch.isEmpty()) {
            return result;
        }

        var futures = new ConcurrentHashMap<Jid, CompletableFuture<DeviceList>>();
        for (var jid : toFetch) {
            var future = new CompletableFuture<DeviceList>();
            pendingFetches.put(jid, future);
            futures.put(jid, future);
        }

        var syncStartTimestamp = Instant.now();
        int fetchedResponseCount = 0;

        try {
            var hashInfos = new HashMap<Jid, DeviceListHashInfo>();
            for (var jid : toFetch) {
                var cached = store.findDeviceList(jid);
                if (cached.isPresent()) {
                    try {
                        // phashV2 over sorted legacy JID strings, per-user (allowIncludeMetaBot=false).
                        var hash = phashCalculator.calculate(
                                cached.get().deviceJids(),
                                DevicePhashVersion.V2,
                                false
                        );

                        var hashInfo = new DeviceListHashInfoBuilder()
                                .hash(hash)
                                .timestamp(cached.get().timestamp())
                                .expectedTimestamp(cached.get().expectedTimestamp())
                                .build();
                        hashInfos.put(jid, hashInfo);
                    } catch (NoSuchAlgorithmException e) {
                        // Continue without dhash for this JID.
                    }
                }
            }

            checkPnToLidMapping(toFetch, "device_sync_request");

            // WA Web's syncDeviceList runs getAndStoreIdentityKeys before handleADVDeviceSyncResult
            // so that signed key index list verification can resolve a stored primary identity for
            // every JID in the response. Without this prefetch, a first-contact USync would parse the
            // device list, fail signature verification (no stored identity yet), and fall through to
            // a primary-only fanout — which the server then rejects with error 479 because the
            // recipient actually has companion devices.
            preKeyHandler.fetchAndStoreIdentityKeys(toFetch);

            var includeUsernameProtocol = abPropsService.getBool(ABProp.USERNAME_USYNC);

            var batches = DeviceUSyncQueryBuilder.build(toFetch, context, hashInfos, includeUsernameProtocol);
            var fetchedResults = getDevicesFetchedResults(batches);
            fetchedResponseCount = fetchedResults.size();

            fetchedResults = backfillMissingDeviceSyncEntries(toFetch, fetchedResults);

            if (!isBizHostedDevicesEnabled()) {
                fetchedResults = filterHostedDevicesFromResults(fetchedResults);
            }

            var lastADVCheckTime = store.lastAdvCheckTime()
                    .orElse(null);

            var usersWithValidatedKeyIndex = new ArrayList<Jid>();
            var hostedOverrideEnabled = isHostedOverrideAdvAccountSignatureKeyEnabled();
            var ownDevicesRemoved = false;
            var myJid = store.jid().map(Jid::toUserJid).orElse(null);
            for (var deviceResult : fetchedResults) {
                var deviceList = switch (deviceResult) {
                    case DeviceListResult.Full full -> {
                        var newList = full.deviceList();

                        full.username().ifPresent(username ->
                                store.findContactByJid(newList.userJid())
                                        .ifPresent(contact -> contact.setUsername(username)));

                        if (newList.advAccountType() == ADVEncryptionType.HOSTED) {
                            store.addToInteropHostedVerificationCache(newList.userJid());
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Added {0} to interop hosted verification cache", newList.userJid());
                        }

                        full.accountSignatureKey()
                                .filter(key -> key.length > 0)
                                .ifPresent(accountSignatureKey -> {
                                    if (hostedOverrideEnabled && full.hasHostedDevice()) {
                                        try {
                                            preKeyHandler.storeIdentityFromAccountSignatureKey(newList.userJid(), accountSignatureKey);
                                            LOGGER.log(System.Logger.Level.DEBUG,
                                                    "Saved identity key from accountSignatureKey for hosted user {0}",
                                                    newList.userJid());
                                        } catch (Exception e) {
                                            LOGGER.log(System.Logger.Level.WARNING,
                                                    "Failed to save identity key from accountSignatureKey for {0}: {1}",
                                                    newList.userJid(), e.getMessage());
                                        }
                                    } else {
                                        try {
                                            store.saveIdentity(
                                                    newList.userJid().toSignalAddress(),
                                                    SignalIdentityPublicKey.ofDirect(accountSignatureKey)
                                            );
                                            LOGGER.log(System.Logger.Level.DEBUG,
                                                    "Saved identity key from accountSignatureKey for user {0}",
                                                    newList.userJid());
                                        } catch (Exception e) {
                                            LOGGER.log(System.Logger.Level.WARNING,
                                                    "Failed to save identity key for {0}: {1}",
                                                    newList.userJid(), e.getMessage());
                                        }
                                    }
                                });
                        var cachedList = store.findDeviceList(newList.userJid());

                        var needsListReset = false;
                        if (cachedList.isPresent() && requiresListReset(cachedList.get(), newList.rawId())) {
                            needsListReset = true;
                            handleListReset(newList.userJid(), cachedList.get(),
                                    cachedList.get().rawId(), newList.rawId());
                        }

                        if (cachedList.isPresent() && newList.hasAccountTypeChanged(cachedList.get())) {
                            var oldType = cachedList.get().advAccountType();
                            var newType = newList.advAccountType();
                            handleAccountTypeTransition(newList.userJid(), oldType, newType, cachedList.get());
                            needsListReset = true;
                        }

                        if (!needsListReset && cachedList.isPresent() && !cachedList.get().deleted()) {
                            handleNoListReset(newList.userJid(), cachedList.get(), newList);
                        }

                        Instant newExpectedTsUpdateTs = null;
                        Instant newExpectedTsLastDeviceJobTs = null;
                        var finalExpectedTs = newList.expectedTimestamp();

                        // List reset uses the cached timestamp or {@code pastUnixTime((expirationDays-1)*DAY)};
                        // non-reset uses now.
                        Instant newTimestamp;
                        if (needsListReset) {
                            if (cachedList.isPresent()) {
                                newTimestamp = cachedList.get().timestamp();
                            } else {
                                var expirationDays = abPropsService.getInt(ABProp.NUM_DAYS_KEY_INDEX_LIST_EXPIRATION);
                                var pastSeconds = (expirationDays - 1) * 24 * 60 * 60L;
                                newTimestamp = Instant.now().minusSeconds(pastSeconds);
                            }
                        } else {
                            newTimestamp = Instant.now();
                        }
                        if (DeviceExpectedTsUtils.shouldClearExpectedTimestamp(newTimestamp, finalExpectedTs, cachedList.orElse(null), lastADVCheckTime)) {
                            finalExpectedTs = null;
                            newExpectedTsUpdateTs = null;
                            newExpectedTsLastDeviceJobTs = null;
                        } else if (cachedList.isPresent()) {
                            var oldExpectedTs = cachedList.get().expectedTimestamp();
                            if (DeviceExpectedTsUtils.hasExpectedTimestampChanged(oldExpectedTs, finalExpectedTs)) {
                                newExpectedTsUpdateTs = Instant.now();
                                newExpectedTsLastDeviceJobTs = lastADVCheckTime;
                            } else {
                                newExpectedTsUpdateTs = cachedList.get().expectedTimestampUpdateTimestamp();
                                newExpectedTsLastDeviceJobTs = cachedList.get().expectedTimestampLastDeviceJobTimestamp();
                            }
                        }

                        var trackedList = new DeviceListBuilder()
                                .userJid(newList.userJid())
                                .devices(newList.devices())
                                .timestamp(newTimestamp)
                                .rawId(newList.rawId())
                                .deleted(newList.deleted())
                                .deletedChangedToHost(newList.deletedChangedToHost())
                                .advAccountType(newList.advAccountType())
                                .expectedTimestamp(finalExpectedTs)
                                .expectedTimestampLastDeviceJobTimestamp(newExpectedTsLastDeviceJobTs)
                                .expectedTimestampUpdateTimestamp(newExpectedTsUpdateTs)
                                .currentIndex(newList.currentIndex())
                                .validIndexes(newList.validIndexes())
                                .build();

                        if (cachedList.isPresent()) {
                            var changes = trackedList.mismatch(cachedList.get());
                            if (!changes.identityChangedDevices().isEmpty()) {
                                for (var changedDevice : changes.identityChangedDevices()) {
                                    store.markIdentityChange(changedDevice);
                                    store.cleanupSignalSessions(changedDevice);
                                }

                                for (var listener : client.store().listeners()) {
                                    Thread.startVirtualThread(() ->
                                            listener.onDeviceIdentityChanged(client, trackedList.userJid(), changes.identityChangedDevices())
                                    );
                                }
                            }

                            if (!changes.removedDevices().isEmpty()) {
                                for (var removedDevice : changes.removedDevices()) {
                                    store.cleanupSignalSessions(removedDevice);
                                }
                                if (trackedList.userJid().equals(myJid)) {
                                    ownDevicesRemoved = true;
                                }
                            }

                            // Mark for sender-key rotation when membership changed.
                            if (!changes.addedDevices().isEmpty() || !changes.removedDevices().isEmpty()) {
                                store.markKeyRotation(trackedList.userJid());
                            }
                        }

                        // Track users for which the prefetch via getAndStoreIdentityKeys is appropriate.
                        if (!trackedList.validIndexes().isEmpty() || trackedList.currentIndex() > 0) {
                            usersWithValidatedKeyIndex.add(trackedList.userJid());
                        }

                        yield trackedList;
                    }

                    case DeviceListResult.Omitted omitted -> {
                        // Server confirmed the dhash matches: preserve rawId/validIndexes but
                        // reset devices to primary-only.
                        var cachedList = omitted.userJid()
                                .flatMap(store::findDeviceList);
                        if (cachedList.isEmpty() || cachedList.get().deleted()) {
                            yield null;
                        }

                        var oldList = cachedList.get();

                        if (omitted.timestamp().isPresent()
                                && omitted.timestamp().get().isBefore(oldList.timestamp())) {
                            yield null;
                        }
                        // The expectedTs tracking fields are preserved from the cached record by
                        // default and only mutated when shouldClearExpectedTs returns true.
                        Instant newTimestamp;
                        Instant finalExpectedTs = oldList.expectedTimestamp();
                        Instant newExpectedTsUpdateTs = oldList.expectedTimestampUpdateTimestamp();
                        Instant newExpectedTsLastDeviceJobTs = oldList.expectedTimestampLastDeviceJobTimestamp();
                        if (omitted.timestamp().isPresent()) {
                            newTimestamp = omitted.timestamp().get();

                            // The incoming expectedTs is fed only to shouldClearExpectedTs and is
                            // never written into the record.
                            var incomingExpectedTs = omitted.expectedTimestamp().orElse(null);
                            if (DeviceExpectedTsUtils.shouldClearExpectedTimestamp(newTimestamp, incomingExpectedTs, oldList, lastADVCheckTime)) {
                                finalExpectedTs = null;
                                newExpectedTsUpdateTs = null;
                                newExpectedTsLastDeviceJobTs = null;
                            }
                        } else {
                            newTimestamp = oldList.timestamp();
                        }

                        var resetDevices = List.of(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0));

                        var resetAdvAccountType = oldList.advAccountType() == ADVEncryptionType.HOSTED
                                ? ADVEncryptionType.E2EE
                                : oldList.advAccountType();

                        // When fromHandleOmittedResult=true and a HOSTED -> E2EE transition is implied,
                        // run the account-type transition handler.
                        if (omitted.fromHandleOmittedResult()
                                && oldList.advAccountType() == ADVEncryptionType.HOSTED
                                && resetAdvAccountType == ADVEncryptionType.E2EE) {
                            handleAccountTypeTransition(
                                    oldList.userJid(),
                                    ADVEncryptionType.HOSTED,
                                    ADVEncryptionType.E2EE,
                                    oldList
                            );
                        }

                        yield new DeviceListBuilder()
                                .userJid(oldList.userJid())
                                .devices(resetDevices)
                                .timestamp(newTimestamp)
                                .rawId(oldList.rawId())
                                .deleted(oldList.deleted())
                                .deletedChangedToHost(oldList.deletedChangedToHost())
                                .advAccountType(resetAdvAccountType)
                                .expectedTimestamp(finalExpectedTs)
                                .expectedTimestampLastDeviceJobTimestamp(newExpectedTsLastDeviceJobTs)
                                .expectedTimestampUpdateTimestamp(newExpectedTsUpdateTs)
                                .currentIndex(oldList.currentIndex())
                                .validIndexes(oldList.validIndexes())
                                .build();
                    }

                    case DeviceListResult.Error error -> throw new WhatsAppDeviceSyncException(error.errorCode(), error.errorText(), error.fatal());
                };

                if (deviceList != null) {
                    store.addDeviceList(deviceList);
                    var future = futures.get(deviceList.userJid());
                    if (future != null) {
                        future.complete(deviceList);
                        result.add(deviceList);
                    }
                }
            }

            // Fall back to a primary-only device list for any JID the server omitted entirely.
            for (var entry : futures.entrySet()) {
                if (!entry.getValue().isDone()) {
                    var fallback = createPrimaryOnlyDeviceList(entry.getKey());
                    store.addDeviceList(fallback);
                    entry.getValue().complete(fallback);
                    result.add(fallback);
                    LOGGER.log(System.Logger.Level.DEBUG, "Device list not found for {0}, falling back to primary device", entry.getKey());
                }
            }

            if (!usersWithValidatedKeyIndex.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Device sync completed for {0} users with validated key index info",
                        usersWithValidatedKeyIndex.size());
            }

            if (ownDevicesRemoved) {
                updateMissingKeyDevices();
            }

            emitContactSyncSuccess(context, toFetch.size(), fetchedResponseCount, syncStartTimestamp,
                    abPropsService.getBool(ABProp.USERNAME_USYNC), result.size());

            return result;
        } catch (Exception e) {
            var pending = PendingDeviceSync.of(toFetch, context);
            store.addPendingDeviceSync(pending);

            for (var future : futures.values()) {
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            }

            emitContactSyncFailure(context, toFetch.size(), fetchedResponseCount, syncStartTimestamp,
                    abPropsService.getBool(ABProp.USERNAME_USYNC), extractUsyncErrorCode(e),
                    CONTACT_SYNC_ERROR_CODE_DEVICE_SYNC);

            throw new RuntimeException("Failed to fetch device lists", e);
        } finally {
            for (var jid : toFetch) {
                pendingFetches.remove(jid);
            }
        }
    }

    /**
     * Bit position for the {@code devices} protocol in the contact-sync protocol bitmask.
     */
    private static final int CONTACT_SYNC_PROTOCOL_BIT_DEVICE = 5;

    /**
     * Bit position for the {@code username} protocol in the contact-sync protocol bitmask.
     */
    private static final int CONTACT_SYNC_PROTOCOL_BIT_USERNAME = 10;

    /**
     * Request origin for device-sync USync queries
     * ({@code WAWebContactSyncLogger.SYNC_REQUEST_ORIGIN.DEVICE_REQUEST}).
     */
    private static final int CONTACT_SYNC_REQUEST_ORIGIN_DEVICE_REQUEST = 48;

    /**
     * Error-protocol code used as the 429 fallback when a device-sync USync fails
     * ({@code WAWebContactSyncErrorCodes.DEVICE_SYNC}). Duplicated here as a plain
     * {@code int} because {@link com.github.auties00.cobalt.wam.event.ContactSyncEventEvent#contactSyncErrorCode()}
     * is serialised as a raw integer on the wire.
     */
    private static final int CONTACT_SYNC_ERROR_CODE_DEVICE_SYNC = 1300;

    /**
     * Returns the WAM contact-sync protocol bitmask for the device-sync USync query.
     *
     * @param includeUsernameProtocol whether the username protocol was included
     * @return the protocol bitmask for the WAM {@code request_protocol} property
     */
    private static int contactSyncProtocolBitmask(boolean includeUsernameProtocol) {
        var bitmask = 1 << CONTACT_SYNC_PROTOCOL_BIT_DEVICE;
        if (includeUsernameProtocol) {
            bitmask |= 1 << CONTACT_SYNC_PROTOCOL_BIT_USERNAME;
        }
        return bitmask;
    }

    /**
     * Extracts the server-side USync error code from a caught exception, preferring the
     * code carried on {@link WhatsAppDeviceSyncException}.
     *
     * @param throwable the exception thrown during the USync flow
     * @return the error code, or {@code 0} when unavailable
     */
    private static int extractUsyncErrorCode(Throwable throwable) {
        if (throwable instanceof WhatsAppDeviceSyncException dse) {
            return dse.errorCode();
        }
        var cause = throwable.getCause();
        if (cause instanceof WhatsAppDeviceSyncException dse) {
            return dse.errorCode();
        }
        return 0;
    }

    /**
     * Emits the successful {@code ContactSyncEvent} (id 1006) for the device-sync USync flow.
     *
     * @param context                 the USync context string (for example
     *                                {@code "interactive"}, {@code "background"})
     * @param requestedCount          the number of JIDs originally requested
     * @param responseCount           the number of entries returned by the server
     * @param syncStartTimestamp      the instant at which the sync started
     * @param includeUsernameProtocol whether the username protocol was included in the request
     * @param deviceResponseNew       the count of successful device results ingested
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSyncLogger",
            exports = "contactSyncLogger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitContactSyncSuccess(String context, int requestedCount, int responseCount,
                                        Instant syncStartTimestamp, boolean includeUsernameProtocol,
                                        int deviceResponseNew) {
        var endTimestamp = Instant.now();
        wamService.commit(new ContactSyncEventEventBuilder()
                .contactSyncType((context + "_query").toUpperCase(Locale.ROOT))
                .contactSyncRequestOrigin(CONTACT_SYNC_REQUEST_ORIGIN_DEVICE_REQUEST)
                .contactSyncSuccess(true)
                .contactSyncNoop(requestedCount == 0)
                .contactSyncStartTimestamp(syncStartTimestamp)
                .contactSyncEndTimestamp(endTimestamp)
                .contactSyncLatency((int) (endTimestamp.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                .contactSyncRequestedCount(requestedCount)
                .contactSyncResponseCount(responseCount)
                .contactSyncRequestProtocol(contactSyncProtocolBitmask(includeUsernameProtocol))
                .contactSyncFailureProtocol(0)
                .contactSyncDeviceResponseNew(deviceResponseNew)
                .build());
    }

    /**
     * Emits the failing {@code ContactSyncEvent} (id 1006) for the device-sync USync flow.
     * The HTTP {@code 429} (rate-limited) status is folded onto {@code fallbackErrorCode}
     * to match {@code WAWebContactSyncLogger.logFailure}.
     *
     * @param context                 the USync context string
     * @param requestedCount          the number of JIDs originally requested
     * @param responseCount           the number of entries returned before the failure
     * @param syncStartTimestamp      the instant at which the sync started
     * @param includeUsernameProtocol whether the username protocol was included
     * @param serverErrorCode         the error code from the USync response
     * @param fallbackErrorCode       the fallback error code substituted for {@code 429}
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSyncLogger",
            exports = "contactSyncLogger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitContactSyncFailure(String context, int requestedCount, int responseCount,
                                        Instant syncStartTimestamp, boolean includeUsernameProtocol,
                                        int serverErrorCode, int fallbackErrorCode) {
        var endTimestamp = Instant.now();
        var errorCode = serverErrorCode == 429 ? fallbackErrorCode : serverErrorCode;
        wamService.commit(new ContactSyncEventEventBuilder()
                .contactSyncType((context + "_query").toUpperCase(Locale.ROOT))
                .contactSyncRequestOrigin(CONTACT_SYNC_REQUEST_ORIGIN_DEVICE_REQUEST)
                .contactSyncSuccess(false)
                .contactSyncNoop(false)
                .contactSyncErrorCode(errorCode)
                .contactSyncStartTimestamp(syncStartTimestamp)
                .contactSyncEndTimestamp(endTimestamp)
                .contactSyncLatency((int) (endTimestamp.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                .contactSyncRequestedCount(requestedCount)
                .contactSyncResponseCount(responseCount)
                .contactSyncRequestProtocol(contactSyncProtocolBitmask(includeUsernameProtocol))
                .contactSyncFailureProtocol(0)
                .build());
    }

    /**
     * Sends all USync batches in parallel and collates the parsed results into a single list.
     *
     * @param batches the USync IQ batches to dispatch
     * @return the flattened list of parsed device list results
     * @throws RuntimeException if the calling virtual thread is interrupted while waiting
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private List<DeviceListResult> getDevicesFetchedResults(List<NodeBuilder> batches) {
        try (var scope = StructuredTaskScope.open(JOINER)) {
            for (var batch : batches) {
                scope.fork(() -> {
                    var response = client.sendNode(batch);
                    return usyncResponseParser.parse(response);
                });
            }
            return scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching device lists", e);
        }
    }

    /**
     * Handles an account-type transition between {@code E2EE} and {@code HOSTED}.
     * Verifies hosted users are in the verification cache, clears Signal sessions, marks
     * the device list as deleted-changed-to-host on {@code E2EE -> HOSTED}, updates the
     * contact's encryption metadata, notifies listeners, and inserts a system message.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous account type
     * @param newType the new account type
     * @param oldList the cached device list before the transition
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "isMeOrCurrentContactHosted",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleAccountTypeTransition(Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType, DeviceList oldList) {
        LOGGER.log(System.Logger.Level.INFO, "Account type changed for {0}: {1} -> {2}", userJid, oldType, newType);

        // Reject transitions to HOSTED for users not in the verification cache to prevent spoofing.
        if (newType == ADVEncryptionType.HOSTED) {
            if(!store.isInInteropHostedVerificationCache(userJid)) {
                throw new IllegalStateException(userJid + " is not in the interop hosted verification cache");
            }
        }

        cleanupAllSessionsForUser(userJid, oldList);

        if (newType == ADVEncryptionType.HOSTED) {
            store.addDeviceList(createDeletedDeviceList(userJid, true));
        }

        store.findContactByJid(userJid).ifPresent(contact -> contact.setEncryptionType(newType));

        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() ->
                    listener.onAccountTypeChanged(client, userJid, oldType, newType)
            );
        }

        createAccountTypeChangeSystemMessage(userJid, oldType, newType);
    }

    /**
     * Inserts a system message into the chat for an account-type transition. Emits an
     * {@code E2E_ENCRYPTED_NOW} stub for {@code HOSTED -> E2EE} and a {@code CIPHERTEXT}
     * stub for {@code E2EE -> HOSTED}. Initial hosted-transition messages are deduplicated
     * within the same second.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous account type, or {@code null} if unknown
     * @param newType the new account type
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "shouldDedupInitialHostedSystemMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "sendWamCoexPrivacySysMsgInsertSuccess",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void createAccountTypeChangeSystemMessage(Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType) {
        var chat = store.findChatByJid(userJid).orElse(null);
        if (chat == null) {
            return;
        }

        if (newType == ADVEncryptionType.HOSTED && shouldDedupInitialHostedSystemMsg(userJid)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Skipping duplicate hosted system message for {0}", userJid);
            return;
        }

        var stubType = (newType == ADVEncryptionType.E2EE)
                ? ChatMessageInfo.StubType.E2E_ENCRYPTED_NOW
                : ChatMessageInfo.StubType.CIPHERTEXT;

        var key = new MessageKeyBuilder()
                .id(MessageIdGenerator.generate(MessageIdVersion.V2, userJid))
                .parentJid(chat.jid())
                .senderJid(userJid)

                .build();
        var message = new ChatMessageInfoBuilder()
                .status(MessageStatus.DELIVERED)
                .timestamp(Instant.now())
                .key(key)
                .ignore(true)
                .stubType(stubType)
                .senderJid(userJid)
                .build();
        chat.addMessage(message);

        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewMessage(client, message));
        }

        emitCoexPrivacySysMsgWamEvent(userJid, oldType, newType);
    }

    /**
     * Commits a {@code CoexPrivacySysMsg} WAM event for an inserted account-type change
     * system message. The {@code (oldType, newType)} pair maps to the state-transition
     * enum used by WA Web's subtype routing.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous account type, or {@code null} if unknown
     * @param newType the new account type
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "sendWamCoexPrivacySysMsgInsertSuccess",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitCoexPrivacySysMsgWamEvent(Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType) {
        CoexSysMsgStateTransitionAttempt stateTransition;
        if (newType == ADVEncryptionType.E2EE) {
            stateTransition = CoexSysMsgStateTransitionAttempt.HOSTED_TO_E2EE;
        } else if (oldType == ADVEncryptionType.HOSTED) {
            stateTransition = CoexSysMsgStateTransitionAttempt.HOSTED_TO_HOSTED;
        } else {
            // E2EE_TO_HOSTED also covers the null -> HOSTED case.
            stateTransition = CoexSysMsgStateTransitionAttempt.E2EE_TO_HOSTED;
        }

        var meJid = store.jid().orElse(null);
        var isSelf = meJid != null && Objects.equals(meJid.user(), userJid.user());

        var multiDeviceId = meJid != null ? meJid.device() : 0;

        var builder = new CoexPrivacySysMsgEventBuilder()
                .coexSysMsgInsertionSuccess(Boolean.TRUE)
                .coexSysMsgIsSelf(isSelf)
                .coexSysMsgMultiDeviceId(multiDeviceId)
                .coexSysMsgStateTransitionAttempt(stateTransition)
                .coexSysMsgBusinessId(userJid.user());

        // {@code channel} is intentionally unset for this call site: only the history-sync
        // entry point populates {@code HISTORY_SYNC}.
        wamService.commit(builder.build());
    }

    /**
     * Returns whether an initial hosted system message for the given user has already
     * been emitted within the current second.
     *
     * @param userJid the user JID
     * @return {@code true} if the message should be skipped as a duplicate
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "shouldDedupInitialHostedSystemMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldDedupInitialHostedSystemMsg(Jid userJid) {
        var currentSecond = Instant.now().getEpochSecond();
        var lastMsgSecond = hostedSystemMsgDedupCache.get(userJid);

        if (lastMsgSecond != null && lastMsgSecond.getEpochSecond() == currentSecond) {
            return true;
        }

        hostedSystemMsgDedupCache.put(userJid, Instant.ofEpochSecond(currentSecond));
        return false;
    }

    /**
     * Cleans up Signal sessions and sender keys for every device in the given list.
     *
     * @param userJid the owning user JID
     * @param oldList the device list containing devices to clean up
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityUpdateDeviceTableApi",
            exports = "clearDeviceRecord",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void cleanupAllSessionsForUser(Jid userJid, DeviceList oldList) {
        for (var device : oldList.devices()) {
            var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
            store.cleanupSignalSessions(deviceJid);
        }
    }

    /**
     * Returns whether a full list reset is required because the {@code rawId} has changed.
     * The {@code rawId} uniquely identifies a user's device configuration; a change implies
     * re-registration and forces every Signal session to be invalidated.
     *
     * @param cachedList the cached device list
     * @param newRawId   the new {@code rawId} from the server
     * @return {@code true} if a full list reset is required
     */
    private boolean requiresListReset(DeviceList cachedList, String newRawId) {
        if (cachedList == null || cachedList.deleted()) {
            return false;
        }
        var oldRawId = cachedList.rawId();
        return oldRawId != null && newRawId != null && !oldRawId.equals(newRawId);
    }

    /**
     * Performs a full device list reset, clearing every Signal session belonging to the
     * user's previous devices.
     *
     * @param userJid    the user JID
     * @param cachedList the previous cached device list
     * @param oldRawId   the previous {@code rawId}
     * @param newRawId   the new {@code rawId}
     */
    private void handleListReset(Jid userJid, DeviceList cachedList, String oldRawId, String newRawId) {
        LOGGER.log(System.Logger.Level.INFO,
                "Device list rawId changed for {0}: {1} -> {2}, triggering full reset (handleListReset)",
                userJid, oldRawId, newRawId);
        cleanupAllSessionsForUser(userJid, cachedList);
    }

    /**
     * Validates an incremental device list update against the cached {@code validIndexes}
     * and logs the diff. Out-of-order timestamps with an unknown {@code keyIndex} are
     * rejected as potential replay attempts.
     *
     * @param userJid    the user JID
     * @param cachedList the cached device list
     * @param newList    the new device list from the server
     * @return the new device list (callers persist it)
     *
     * @throws IllegalStateException if the new list contains a non-primary {@code keyIndex}
     *                               outside {@code validIndexes} despite a non-newer timestamp
     */
    private DeviceList handleNoListReset(Jid userJid, DeviceList cachedList, DeviceList newList) {
        var cachedValidIndexes = cachedList.validIndexes();
        if (!cachedValidIndexes.isEmpty() && !cachedList.timestamp().isAfter(newList.timestamp())) {
            var cachedCurrentIndex = cachedList.currentIndex();
            for (var device : newList.devices()) {
                var keyIndex = device.keyIndex();
                // Acceptable if keyIndex==0, in validIndexes, or strictly greater than currentIndex.
                var isValid = keyIndex == 0
                        || cachedValidIndexes.contains(keyIndex)
                        || keyIndex > cachedCurrentIndex;
                if (!isValid) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "handleNoListReset: out-of-order timestamp detected for {0}: " +
                                    "incomingTs={1}, cachedTs={2}, keyIndex={3} not in validIndexes={4}",
                            userJid, newList.timestamp(), cachedList.timestamp(), keyIndex, cachedValidIndexes);
                    throw new IllegalStateException(
                            "handleNoListReset: out-of-order timestamp detected for " + userJid);
                }
            }
        }

        var changes = newList.mismatch(cachedList);

        if (!changes.addedDevices().isEmpty() || !changes.removedDevices().isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device list incrementally updated for {0}: +{1} -{2} devices (handleNoListReset)",
                    userJid, changes.addedDevices().size(), changes.removedDevices().size());
        }

        return newList;
    }

    /**
     * Backfills missing device-sync entries when the server returns LID-based results
     * but the request used the phone number. Duplicates the LID result as a PN entry so
     * callers receive the data they requested.
     *
     * @param requestedJids the JIDs that were originally requested
     * @param results       the results returned from the server
     * @return the results with backfilled PN entries appended
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSyncUtils",
            exports = "backfillMissingDeviceSyncEntries",
            adaptation = WhatsAppAdaptation.DIRECT)
    private List<DeviceListResult> backfillMissingDeviceSyncEntries(
            Set<Jid> requestedJids,
            List<DeviceListResult> results
    ) {
        var resultMap = new HashMap<Jid, DeviceListResult>();
        for (var result : results) {
            if(result instanceof DeviceListResult.Error error) {
                throw new WhatsAppDeviceSyncException(error.errorCode(), error.errorText(), error.fatal());
            }

            result.userJid()
                    .ifPresent(value -> resultMap.put(value, result));
        }

        var backfilledResults = new ArrayList<>(results);
        for (var requestedJid : requestedJids) {
            if (resultMap.containsKey(requestedJid)) {
                continue;
            }

            var isRegularUserPn = requestedJid.hasUserServer()
                    && !requestedJid.equals(Jid.announcementsAccount())
                    && !requestedJid.hasBotServer()
                    && !requestedJid.hasLidServer();
            if (!isRegularUserPn) {
                continue;
            }

            var lidJid = store.findLidByPhone(requestedJid);
            if (lidJid.isEmpty()) {
                continue;
            }

            var lidResult = resultMap.get(lidJid.get());
            if (lidResult == null) {
                continue;
            }

            LOGGER.log(System.Logger.Level.DEBUG, "Backfilling device list for {0} from LID {1}",
                    requestedJid, lidJid.get());

            var backfilledResult = switch (lidResult) {
                case DeviceListResult.Full full -> {
                    var originalList = full.deviceList();
                    var backfilledList = new DeviceListBuilder()
                            .userJid(requestedJid)
                            .devices(originalList.devices())
                            .timestamp(originalList.timestamp())
                            .rawId(originalList.rawId())
                            .deleted(originalList.deleted())
                            .deletedChangedToHost(originalList.deletedChangedToHost())
                            .advAccountType(originalList.advAccountType())
                            .expectedTimestamp(originalList.expectedTimestamp())
                            .expectedTimestampLastDeviceJobTimestamp(originalList.expectedTimestampLastDeviceJobTimestamp())
                            .expectedTimestampUpdateTimestamp(originalList.expectedTimestampUpdateTimestamp())
                            .currentIndex(originalList.currentIndex())
                            .validIndexes(originalList.validIndexes())
                            .build();
                    yield new DeviceListResult.Full(backfilledList, full.accountSignatureKey().orElse(null), full.username().orElse(null));
                }
                case DeviceListResult.Omitted omitted -> new DeviceListResult.Omitted(
                        requestedJid,
                        omitted.timestamp().orElse(null),
                        omitted.expectedTimestamp().orElse(null),
                        omitted.fromHandleOmittedResult()
                );
                case DeviceListResult.Error error -> throw new WhatsAppDeviceSyncException(error.errorCode(), error.errorText(), error.fatal());
            };

            backfilledResults.add(backfilledResult);
        }

        return backfilledResults;
    }

    /**
     * Returns the timestamp of the last ADV device info check job, if any.
     *
     * @return the last check time, or empty if never run
     */
    @WhatsAppWebExport(moduleName = "WAWebLastADVCheckTimeApi",
            exports = "getLastADVDeviceInfoCheckTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Instant> lastAdvCheckTime() {
        return store.lastAdvCheckTime();
    }

    /**
     * Updates the ADV check time to the current instant.
     */
    @WhatsAppWebExport(moduleName = "WAWebLastADVCheckTimeApi",
            exports = "setLastADVDeviceInfoCheckTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateAdvCheckTime() {
        store.updateAdvCheckTime();
    }

    /**
     * Builds a device list containing only the primary device (device id 0). Used as a
     * fallback when the server returns no device information.
     *
     * @param userJid the user JID
     * @return a device list with only the primary device
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private DeviceList createPrimaryOnlyDeviceList(Jid userJid) {
        var now = Instant.now();
        return new DeviceListBuilder()
                .userJid(userJid)
                .devices(List.of(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0)))
                .timestamp(now)
                .build();
    }

    /**
     * Builds a tombstone device list. The {@code changedToHost} flag distinguishes
     * deletions caused by an {@code E2EE -> HOSTED} transition from generic deletions.
     *
     * @param userJid       the user JID
     * @param changedToHost whether the deletion is due to a HOSTED transition
     * @return a deleted device list marker
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityUpdateDeviceTableApi",
            exports = "clearDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private DeviceList createDeletedDeviceList(Jid userJid, boolean changedToHost) {
        var now = Instant.now();
        return new DeviceListBuilder()
                .userJid(userJid)
                .devices(List.of())
                .timestamp(now)
                .deleted(true)
                .deletedChangedToHost(changedToHost)
                .build();
    }

    /**
     * Starts the daily ADV device info check scheduler.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void startAdvCheckScheduler() {
        advCheckScheduler.start();
    }

    /**
     * Stops the ADV device info check scheduler. Callers should invoke this before
     * disconnecting to halt background work deterministically.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void stopAdvCheckScheduler() {
        advCheckScheduler.close();
    }

    /**
     * Retries every queued pending device sync, respecting retry limits and expiration.
     * Intended to run after reconnect.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiPendingDeviceSync",
            exports = "doPendingDeviceSync",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void retryPendingSyncs() {
        var pending = store.pendingDevicesSyncs();
        for (var sync : pending) {
            if (sync.isExpired()) {
                store.removePendingDeviceSync(sync);
                continue;
            }

            if (!sync.shouldRetry()) {
                store.removePendingDeviceSync(sync);
                continue;
            }

            try {
                fetchDeviceListsFromServer(sync.userJids(), sync.context());
                store.removePendingDeviceSync(sync);
            } catch (Exception e) {
                var retried = sync.nextRetry();
                store.removePendingDeviceSync(sync);
                if (retried.shouldRetry()) {
                    store.addPendingDeviceSync(retried);
                }
            }
        }
    }

    /**
     * Updates missing-key device tracking when own companion devices are removed. Removed
     * devices are dropped from each missing-key record. When a key is missing on every
     * remaining device, schedules the all-devices-responded grace-period check before any
     * fatal-sync decision.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdStoreMissingKeys",
            exports = "updateMissingKeyDevices",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateMissingKeyDevices() {
        withDeviceUpdateLock(() -> {
            var myJid = store.jid().orElse(null);
            if (myJid == null) {
                return;
            }

            var deviceList = store.findDeviceList(myJid.toUserJid()).orElse(null);
            if (deviceList == null || deviceList.deleted()) {
                return;
            }

            var currentDeviceIds = deviceList.devices()
                    .stream()
                    .map(DeviceInfo::id)
                    .collect(Collectors.toSet());

            var missingSyncKeys = store.missingSyncKeys();
            if (missingSyncKeys.isEmpty()) {
                return;
            }

            for (var missingKey : missingSyncKeys) {
                missingKey.retainDevices(currentDeviceIds);

                if (missingKey.isMissingOnAllDevices()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "All devices responded without missing sync key, scheduling grace period check");
                    webAppStateService.scheduleAllDevicesRespondedCheck();
                }
            }
        });
    }

    /**
     * Returns the fanout device JIDs for a 1:1 user chat. Resolves device lists for both
     * the recipient and the sender, filters self, applies hosted-device gating, and
     * removes devices with unconfirmed identity changes. When {@code expectedPhash} is
     * non-null, the device-list sync short-circuits if the local phash already matches.
     *
     * @param chatJid       the recipient user JID
     * @param expectedPhash the server-provided phash to match against, or {@code null}
     *                      for an unconditional sync
     * @return the fanout device JIDs
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Collection<Jid> getUserFanout(Jid chatJid, String expectedPhash) {
        var myDeviceJid = resolveMyDeviceJid(chatJid);
        var deviceLists = getDeviceLists(
                List.of(chatJid, myDeviceJid), "message", expectedPhash, false);

        // 1:1 user chats include hosted devices when bizHostedDevicesEnabled.
        // Pass both PN and LID device JIDs so isMeDevice/isMeAccount filter both sides
        // (WAWebDBDeviceListFanout uses WAWebUserPrefsMeUser globals which read both).
        var mePnDeviceJid = store.jid().orElse(null);
        var meLidDeviceJid = store.lid().orElse(null);
        var fanoutDevices = fanoutCalculator.calculate(
                mePnDeviceJid, meLidDeviceJid, deviceLists, chatJid);

        var changedIdentities = store.unconfirmedIdentityChanges();
        return fanoutCalculator.filterIdentityChanges(fanoutDevices, changedIdentities);
    }

    /**
     * Returns the fanout device JIDs and participant hash for a group chat. The phash is
     * computed over the recipients <em>plus</em> the sender's device JID
     * ({@code phashV2([].concat(M, [F]))}).
     *
     * @param groupJid        the group JID
     * @param senderDeviceJid the sender's own device JID, included in the phash but not
     *                        in the returned device list
     * @return the fanout result with devices and phash
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = "phashV2",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid senderDeviceJid) {
        try {
            var metadata = client.queryChatMetadata(groupJid);
            var participants = metadata.participants()
                    .stream()
                    .map(entry -> entry.userJid())
                    .toList();
            var deviceLists = getDeviceLists(participants, "message", null, false);

            // Group messages exclude hosted devices. Pass both PN and LID device JIDs so
            // isMeDevice/isMeAccount filter both addressing-mode sides
            // (WAWebDBDeviceListFanout uses WAWebUserPrefsMeUser globals which read both).
            var mePnDeviceJid = store.jid().orElse(null);
            var meLidDeviceJid = store.lid().orElse(null);
            var fanoutDevices = fanoutCalculator.calculate(
                    mePnDeviceJid, meLidDeviceJid, deviceLists, null);

            var changedIdentities = store.unconfirmedIdentityChanges();
            var filteredDevices = fanoutCalculator.filterIdentityChanges(fanoutDevices, changedIdentities);

            var phashDevices = new HashSet<>(filteredDevices);
            phashDevices.add(senderDeviceJid);
            var phash = phashCalculator.calculate(phashDevices, DevicePhashVersion.V2, true);
            return new DeviceGroupFanoutResult(filteredDevices, phash);
        } catch (NoSuchAlgorithmException exception) {
            throw new InternalError("Missing SHA-256 implementation", exception);
        }
    }

    /**
     * Computes ICDC metadata for the given user.
     *
     * @param userJid the user JID; normalised to a user-level JID by callees
     * @return the ICDC result, or {@link Optional#empty()} if no usable device list is cached
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMeta",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<IcdcResult> computeIcdc(Jid userJid) {
        return icdcComputer.compute(userJid);
    }

    /**
     * Ensures Signal sessions exist for every device JID, fetching pre-key bundles when
     * needed. Devices that already have an established session are skipped.
     *
     * @param deviceJids the device JIDs to ensure sessions for
     * @return the number of devices for which the one-time pre-key pool was depleted in
     *         the server response (no {@code <key>} element for a non-bot device).
     *         Callers should commit this count via
     *         {@link com.github.auties00.cobalt.wam.event.PrekeysDepletionEventBuilder}
     *         to mirror {@code WAWebPostPrekeysDepletionMetric.maybePostPrekeysDepletionMetric}.
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public int ensureSessions(Collection<Jid> deviceJids) {
        return preKeyHandler.ensureSessions(deviceJids);
    }

    /**
     * Returns the sender's device JID for the given chat, picking the LID device for
     * LID-addressed chats and the PN device otherwise.
     *
     * @param chatJid the chat JID that determines addressing
     * @return the sender's device JID
     *
     * @throws IllegalStateException if not logged in
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser",
            exports = {"getMeDeviceLidOrThrow", "getMeDevicePnOrThrow_DO_NOT_USE", "getMeDeviceOrThrow"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid resolveMyDeviceJid(Jid chatJid) {
        var selfJid = store.jid().orElseThrow(() ->
                new IllegalStateException("Not logged in"));
        if (chatJid.hasLidServer()) {
            return store.lid().orElse(selfJid);
        }
        return selfJid;
    }

    /**
     * Returns whether the business hosted-devices feature is enabled.
     *
     * @return {@code true} if {@code adv_accept_hosted_devices} is set
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "bizHostedDevicesEnabled",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Strips hosted devices (id 99) from every {@code Full} entry. Applied when
     * {@code bizHostedDevicesEnabled} is false so the client does not send to coex
     * hosted devices.
     *
     * @param results the original results
     * @return results with hosted devices removed from {@code Full} entries
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvHandlerApi",
            exports = "handleADVDeviceSyncResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static List<DeviceListResult> filterHostedDevicesFromResults(List<DeviceListResult> results) {
        return results.stream()
                .map(DeviceListResult::withoutHostedDevices)
                .toList();
    }

    /**
     * Dispatches an incoming device notification ({@code add} or {@code remove}) to the
     * matching handler under the device update lock.
     *
     * @param node    the notification node containing {@code device-list} and {@code key-index-list}
     * @param action  the action type ({@code "add"} or {@code "remove"})
     * @param userJid the user JID whose device list changed
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = {"handleDeviceAddNotification", "handleDeviceRemoveNotification"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handleDeviceNotification(Node node, String action, Jid userJid) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(userJid, "userJid cannot be null");

        var deviceListNode = node.getChild("device-list", null);
        var keyIndexListNode = node.getChild("key-index-list");
        if (keyIndexListNode.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Device notification missing key-index-list for {0}", userJid);
            return;
        }

        var timestamp = keyIndexListNode.get().getAttributeAsLong("ts", null);
        if (timestamp == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Device notification missing timestamp for {0}", userJid);
            return;
        }

        withDeviceUpdateLock(() -> {
            switch (action) {
                case "add" -> handleDeviceAddNotification(userJid, deviceListNode, keyIndexListNode.get(), timestamp);
                case "remove" -> handleDeviceRemoveNotification(userJid, deviceListNode, keyIndexListNode.get(), timestamp);
                default -> LOGGER.log(System.Logger.Level.WARNING, "Unknown device action: {0}", action);
            }
        });
    }

    /**
     * Processes an {@code add} device notification by validating the signed key-index
     * list, detecting {@code rawId} or account-type changes, and merging the cached
     * record with the new devices. When no cached record exists, defers to
     * {@link #triggerUsyncForCoexDeviceAdd}.
     *
     * @param userJid          the user JID
     * @param deviceListNode   the {@code device-list} node from the notification
     * @param keyIndexListNode the {@code key-index-list} node from the notification
     * @param timestamp        the notification timestamp in Unix seconds
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = "handleDeviceAddNotification",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void handleDeviceAddNotification(
            Jid userJid,
            Node deviceListNode,
            Node keyIndexListNode,
            long timestamp
    ) {
        var cachedList = store.findDeviceList(userJid);

        if (cachedList.isEmpty() || cachedList.get().deleted()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device add notification for {0} without cached record, queueing for USync", userJid);

            triggerUsyncForCoexDeviceAdd(deviceListNode, userJid);

            return;
        }

        var signedKeyIndexBytes = keyIndexListNode.toContentBytes();
        if (signedKeyIndexBytes.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device add notification missing signedKeyIndexBytes for {0}, ignoring", userJid);
            return;
        }

        if (timestamp < cachedList.get().timestamp().getEpochSecond()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device add notification timestamp {0} < cached {1} for {2}, ignoring",
                    timestamp, cachedList.get().timestamp().getEpochSecond(), userJid);
            return;
        }

        if (deviceListNode == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Device add notification missing device-list for {0}", userJid);
            return;
        }

        // The device children (with jid and key-index attrs) live under device-list,
        // not under key-index-list.
        var keyIndexMap = buildKeyIndexMap(deviceListNode);

        var validatedKeyIndexInfo = validateKeyIndexList(userJid, deviceListNode, keyIndexListNode);

        // Reject when the protobuf timestamp does not match the stanza timestamp.
        if (validatedKeyIndexInfo != null && validatedKeyIndexInfo.timestamp().getEpochSecond() != timestamp) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Device add notification timestamp mismatch for {0}: protobuf={1}, notification={2}, ignoring",
                    userJid, validatedKeyIndexInfo.timestamp().getEpochSecond(), timestamp);
            return;
        }

        var devices = deviceListNode.streamChildren("device")
                .flatMap(deviceNode -> parseAndValidateAddedDevice(deviceNode, keyIndexMap, validatedKeyIndexInfo))
                .toList();

        var expectedTsSeconds = keyIndexListNode.getAttributeAsLong("expected_ts", null);
        var expectedTs = expectedTsSeconds != null
                ? Instant.ofEpochSecond(expectedTsSeconds)
                : null;
        var rawId = validatedKeyIndexInfo != null
                ? String.valueOf(validatedKeyIndexInfo.rawId())
                : String.valueOf(timestamp);
        var advAccountType = validatedKeyIndexInfo != null
                ? validatedKeyIndexInfo.accountType()
                : null;

        if (advAccountType == ADVEncryptionType.HOSTED) {
            store.addToInteropHostedVerificationCache(userJid);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Added {0} to interop hosted verification cache via notification", userJid);
        }

        var currentIndex = validatedKeyIndexInfo != null ? validatedKeyIndexInfo.currentIndex() : 0;
        var validIndexesSet = validatedKeyIndexInfo != null && validatedKeyIndexInfo.validIndexes() != null
                ? validatedKeyIndexInfo.validIndexes()
                : new LinkedHashSet<Integer>();

        var oldCachedList = cachedList.get();
        var clearRecord = false;
        var oldRawId = oldCachedList.rawId();
        if (oldRawId != null && !oldRawId.equals(rawId)) {
            LOGGER.log(System.Logger.Level.INFO, "Device list rawId changed via notification for {0}: {1} -> {2}, triggering full reset",
                    userJid, oldRawId, rawId);
            clearRecord = true;
            for (var device : oldCachedList.devices()) {
                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
                store.cleanupSignalSessions(deviceJid);
            }
        }

        if (advAccountType != null && oldCachedList.advAccountType() != null
                && advAccountType != oldCachedList.advAccountType()) {
            LOGGER.log(System.Logger.Level.INFO, "Account type changed via notification for {0}: {1} -> {2}",
                    userJid, oldCachedList.advAccountType(), advAccountType);
            clearRecord = true;
            handleAccountTypeTransition(userJid, oldCachedList.advAccountType(), advAccountType, oldCachedList);
        }

        // Merge: keep cached devices whose keyIndex is still valid (in validIndexes or
        // greater than currentIndex), apply the notification's devices on top, and ensure
        // the primary device is present.
        var mergedDevices = new LinkedHashMap<Integer, DeviceInfo>();

        if (!clearRecord) {
            for (var cachedDevice : oldCachedList.devices()) {
                if (cachedDevice.isPrimary()) {
                    continue;
                }
                var keyIdx = cachedDevice.keyIndex();
                if (validIndexesSet.contains(keyIdx) || keyIdx > currentIndex) {
                    mergedDevices.put(cachedDevice.id(), cachedDevice);
                }
            }
        }

        for (var newDevice : devices) {
            if (newDevice.isPrimary()) {
                continue;
            }
            mergedDevices.put(newDevice.id(), newDevice);
        }

        mergedDevices.put(DeviceConstants.PRIMARY_DEVICE_ID, DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0));

        var finalDevices = new ArrayList<>(mergedDevices.values());

        var newDeviceList = new DeviceListBuilder()
                .userJid(userJid)
                .devices(finalDevices)
                .timestamp(oldCachedList.timestamp())
                .rawId(rawId)
                .deleted(false)
                .deletedChangedToHost(false)
                .advAccountType(advAccountType)
                .expectedTimestamp(expectedTs)
                .expectedTimestampLastDeviceJobTimestamp(oldCachedList.expectedTimestampLastDeviceJobTimestamp())
                .expectedTimestampUpdateTimestamp(oldCachedList.expectedTimestampUpdateTimestamp())
                .currentIndex(currentIndex)
                .validIndexes(validIndexesSet)
                .build();

        var incomingTimestamp = validatedKeyIndexInfo != null
                ? validatedKeyIndexInfo.timestamp()
                : Instant.ofEpochSecond(timestamp);
        var computedExpectedTimestamp = DeviceExpectedTsUtils.computeExpectedTimestampForDeviceRecord(
                incomingTimestamp,
                oldCachedList,
                store.lastAdvCheckTime().orElse(null)
        );
        if (computedExpectedTimestamp.expectedTimestamp().isPresent() || computedExpectedTimestamp.expectedTimestampUpdateTimestamp().isPresent()) {
            newDeviceList = new DeviceListBuilder()
                    .userJid(newDeviceList.userJid())
                    .devices(newDeviceList.devices())
                    .timestamp(newDeviceList.timestamp())
                    .rawId(newDeviceList.rawId())
                    .deleted(newDeviceList.deleted())
                    .deletedChangedToHost(newDeviceList.deletedChangedToHost())
                    .advAccountType(newDeviceList.advAccountType())
                    .expectedTimestamp(computedExpectedTimestamp.expectedTimestamp().orElse(null))
                    .expectedTimestampLastDeviceJobTimestamp(computedExpectedTimestamp.expectedTimestampLastDeviceJobTimestamp().orElse(null))
                    .expectedTimestampUpdateTimestamp(computedExpectedTimestamp.expectedTimestampUpdateTimestamp().orElse(null))
                    .currentIndex(newDeviceList.currentIndex())
                    .validIndexes(newDeviceList.validIndexes())
                    .build();
        }

        var changes = newDeviceList.mismatch(oldCachedList);
        if (!changes.identityChangedDevices().isEmpty()) {
            for (var changedDevice : changes.identityChangedDevices()) {
                store.markIdentityChange(changedDevice);
                store.cleanupSignalSessions(changedDevice);
            }

            for (var listener : store.listeners()) {
                Thread.startVirtualThread(() ->
                        listener.onDeviceIdentityChanged(client, userJid, changes.identityChangedDevices())
                );
            }
        }

        if (!changes.removedDevices().isEmpty()) {
            for (var removedDevice : changes.removedDevices()) {
                store.cleanupSignalSessions(removedDevice);
            }
        }

        store.addDeviceList(newDeviceList);

        LOGGER.log(System.Logger.Level.DEBUG, "Device added for {0}: {1} devices", userJid, devices.size());
    }

    /**
     * Parses a single device entry from an {@code add} notification, applying the
     * notification-specific validation. Notification devices must have a {@code keyIndex}
     * in the cryptographically signed {@code validIndexes} set; the
     * {@code keyIndex > currentIndex} allowance that applies to cached devices does not
     * apply here. Hosted devices (id 99) are also gated by {@code bizHostedDevicesEnabled}.
     *
     * @param deviceNode            the device child node
     * @param keyIndexMap           map of device id to keyIndex parsed from the device list
     * @param validatedKeyIndexInfo the validated key-index list, or {@code null}
     * @return a single-element stream with the parsed device, or empty when rejected
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = "handleDeviceAddNotification",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Stream<DeviceInfo> parseAndValidateAddedDevice(Node deviceNode, Map<Integer, Integer> keyIndexMap, ValidatedKeyIndexListResult validatedKeyIndexInfo) {
        var id = deviceNode.getAttributeAsInt("id");
        if (id.isEmpty()) {
            return Stream.empty();
        }

        var deviceId = id.getAsInt();
        var keyIndex = keyIndexMap.getOrDefault(deviceId, 0);

        if (validatedKeyIndexInfo != null && validatedKeyIndexInfo.validIndexes() != null && !validatedKeyIndexInfo.validIndexes().isEmpty()) {
            var isInValidIndexes = validatedKeyIndexInfo.validIndexes().contains(keyIndex);

            // Primary device (keyIndex 0) is always accepted; everything else must be
            // present in the signed validIndexes set.
            if (keyIndex != 0 && !isInValidIndexes) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Device {0} has keyIndex {1} not in validIndexes {2}, excluding from notification",
                        deviceId, keyIndex, validatedKeyIndexInfo.validIndexes());
                return Stream.empty();
            }
        }

        if (deviceId != DeviceConstants.HOSTED_DEVICE_ID) {
            return Stream.of(DeviceInfo.ofE2EE(deviceId, keyIndex));
        }

        if (isBizHostedDevicesEnabled()) {
            return Stream.of(DeviceInfo.ofHosted(keyIndex));
        }

        return Stream.empty();
    }

    /**
     * Processes a {@code remove} device notification. The notification carries the
     * devices being removed. Cached devices stay if they are absent from the notification
     * or have a different {@code keyIndex}; matching entries trigger a Signal session
     * cleanup. The primary device is always preserved.
     *
     * @param userJid          the user JID
     * @param deviceListNode   the {@code device-list} node containing devices being removed
     * @param keyIndexListNode the {@code key-index-list} node from the notification
     * @param timestamp        the notification timestamp in Unix seconds
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = "handleDeviceRemoveNotification",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void handleDeviceRemoveNotification(Jid userJid, Node deviceListNode, Node keyIndexListNode, long timestamp) {
        var cachedList = store.findDeviceList(userJid);
        if (cachedList.isEmpty() || cachedList.get().deleted()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No cached device list for {0}, ignoring remove", userJid);
            return;
        }

        var oldList = cachedList.get();

        if (timestamp < oldList.timestamp().getEpochSecond()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Device remove notification timestamp {0} < cached {1}, ignoring",
                    timestamp, oldList.timestamp().getEpochSecond());
            return;
        }

        var removedDevicesMap = deviceListNode != null
                ? buildKeyIndexMap(deviceListNode)
                : Map.<Integer, Integer>of();

        var remainingDevices = oldList.devices().stream()
                .filter(device -> {
                    if (device.isPrimary()) {
                        return false;
                    }

                    var notificationKeyIndex = removedDevicesMap.get(device.id());
                    return notificationKeyIndex == null || notificationKeyIndex != device.keyIndex();
                })
                .collect(Collectors.toCollection(ArrayList::new));

        remainingDevices.add(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0));

        var removedDevices = oldList.devices().stream()
                .filter(device -> {
                    if (device.isPrimary()) {
                        return false;
                    }
                    var notificationKeyIndex = removedDevicesMap.get(device.id());
                    return notificationKeyIndex != null && notificationKeyIndex == device.keyIndex();
                })
                .toList();
        for (var removedDevice : removedDevices) {
            var deviceJid = removedDevice.toDeviceJid(userJid.user(), userJid.server());
            store.cleanupSignalSessions(deviceJid);
        }

        @SuppressWarnings("ConstantValue")
        var updatedList = new DeviceListBuilder()
                .userJid(userJid)
                .devices(remainingDevices)
                .timestamp(oldList.timestamp())
                .rawId(oldList.rawId())
                .deleted(oldList.deleted())
                .deletedChangedToHost(oldList.deletedChangedToHost())
                .advAccountType(oldList.advAccountType())
                .expectedTimestamp(oldList.expectedTimestamp())
                .expectedTimestampLastDeviceJobTimestamp(oldList.expectedTimestampLastDeviceJobTimestamp())
                .expectedTimestampUpdateTimestamp(oldList.expectedTimestampUpdateTimestamp())
                .currentIndex(oldList.currentIndex())
                .validIndexes(oldList.validIndexes())
                .build();

        store.addDeviceList(updatedList);

        LOGGER.log(System.Logger.Level.DEBUG, "Devices removed for {0}: {1} remaining", userJid, remainingDevices.size());
    }

    /**
     * Builds a {@code (deviceId, keyIndex)} map from the device children of the given node.
     *
     * @param keyIndexListNode the node whose {@code device} children are parsed
     * @return map of device id to key index
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = "handleDeviceAddNotification",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Map<Integer, Integer> buildKeyIndexMap(Node keyIndexListNode) {
        return keyIndexListNode.streamChildren("device")
                .flatMap(DeviceService::parseDevice)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Parses a {@code device} child node into a {@code (deviceId, keyIndex)} entry.
     * Returns an empty stream when the JID is missing or {@code key-index} cannot be
     * parsed so callers may {@code flatMap} without nullable handling.
     *
     * @param deviceNode the {@code device} child node
     * @return a single-element stream with the parsed entry, or empty when invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = "handleDeviceAddNotification",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Stream<Map.Entry<Integer, Integer>> parseDevice(Node deviceNode) {
        var jid = deviceNode.getAttributeAsJid("jid");
        if (jid.isEmpty()) {
            return Stream.empty();
        }

        var keyIndex = deviceNode.getAttributeAsInt("key-index", -1);
        if (keyIndex == -1) {
            return Stream.empty();
        }

        return Stream.of(Map.entry(jid.get().device(), keyIndex));
    }

    /**
     * Validates and decodes the signed key-index list carried by the given node.
     *
     * <p>Mirrors WA Web's {@code handleKeyIndexResultSync} branching: when hosted-business
     * gating is on and the device-list advertises at least one hosted device the embedded
     * {@code accountSignatureKey} is used; otherwise the verification key is the user's
     * locally-stored primary identity.
     *
     * @param userJid          the user JID whose primary identity is used for the
     *                         standard verification path
     * @param deviceListNode   the {@code device-list} node from the notification, used
     *                         to detect hosted devices
     * @param keyIndexListNode the {@code key-index-list} node from the notification
     * @return the validated key-index list data, or {@code null} if validation fails
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "decodeSignedKeyIndexBytes",
            adaptation = WhatsAppAdaptation.DIRECT)
    private ValidatedKeyIndexListResult validateKeyIndexList(
            Jid userJid,
            Node deviceListNode,
            Node keyIndexListNode
    ) {
        var signedKeyIndexBytes = keyIndexListNode.toContentBytes();
        if (signedKeyIndexBytes.isEmpty()) {
            return null;
        }

        var useHostedPath = deviceListNode != null
                && advValidator.isBizHostedDevicesEnabled()
                && deviceListNode.streamChildren("device")
                        .anyMatch(d -> d.hasAttribute("is_hosted", true));
        var validated = useHostedPath
                ? advValidator.verifySKeyIndexWithAccSigKey(signedKeyIndexBytes.get())
                : advValidator.decodeSignedKeyIndexBytes(userJid, signedKeyIndexBytes.get());
        if (validated.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Key index list signature verification failed in notification");
            return null;
        }

        return validated.get();
    }

    /**
     * Schedules a USync to fetch the complete device list when a device-add notification
     * arrives without a cached record. While restart is incomplete the request is queued
     * via {@code addUserToPendingDeviceSync}; otherwise it runs immediately.
     *
     * @param deviceListNode the {@code device-list} node from the notification, or {@code null}
     * @param userJid        the user JID
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "triggerUsyncForCoexDeviceAdd",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void triggerUsyncForCoexDeviceAdd(Node deviceListNode, Jid userJid) {
        var isHostedDevice = deviceListNode != null && hasHostedDevice(deviceListNode);

        if (store.isResumeFromRestartComplete()) {
            getDeviceLists(List.of(userJid), "coex_device_notification", null, false);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Triggered immediate USync for device notification from {0}", userJid);
        } else {
            var pendingSync = PendingDeviceSync.of(List.of(userJid), "coex_device_notification");
            store.addPendingDeviceSync(pendingSync);

            if (isHostedDevice) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Queued coex USync for hosted device notification from {0} (during resume)", userJid);
            }
        }
    }

    /**
     * Returns whether the given device-list node carries the hosted-device sentinel
     * ({@link DeviceConstants#HOSTED_DEVICE_ID}, id 99).
     *
     * @param deviceListNode the device-list node
     * @return {@code true} if any child device carries that id
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "triggerUsyncForCoexDeviceAdd",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean hasHostedDevice(Node deviceListNode) {
        return deviceListNode.streamChildren("device")
                .anyMatch(deviceNode -> deviceNode.hasAttribute("id", DeviceConstants.HOSTED_DEVICE_ID));
    }

    /**
     * Synchronises the device lists for the current user's PN and LID identities.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncMyDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser",
            exports = "getMePNandLIDWids",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void syncMyDeviceList() {
        var myJids = new ArrayList<Jid>();
        store.jid().ifPresent(jid -> myJids.add(jid.toUserJid()));
        store.lid().ifPresent(myJids::add);
        if (myJids.isEmpty()) {
            return;
        }
        getDeviceLists(myJids, null, null, false);
    }

    /**
     * Synchronises the device lists for the given JIDs and returns the cached device
     * records. {@code null} entries indicate no record was cached.
     *
     * @param userJids the user JIDs to sync and retrieve device lists for
     * @return one device list per input JID, in input order; {@code null} for missing slots
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncAndGetDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<DeviceList> syncAndGetDeviceList(Collection<Jid> userJids) {
        getDeviceLists(userJids, null, null, false);
        return getDeviceIds(userJids, false);
    }

    /**
     * Returns the cached device record for a user, firing a {@code checkPnToLidMapping}
     * diagnostic for the requested JID.
     *
     * @param userJid the user JID; only the user portion is significant
     * @return the cached record, or {@link Optional#empty()} when none is stored
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<DeviceList> getDeviceRecord(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        var record = store.findDeviceList(userJid);
        // WA Web fires this diagnostic unconditionally because the LRU is populated by an
        // IDB put that always resolves to a non-null promise.
        checkPnToLidMapping(List.of(userJid), "waweb_api_device_list_get_device_record");
        return record;
    }

    /**
     * Returns the cached device records for a collection of users in input order. Missing
     * entries surface as {@code null}. Fires a single {@code checkPnToLidMapping}
     * diagnostic over the JIDs that resolved to a non-null record.
     *
     * @param userJids the user JIDs to look up
     * @return one record per input JID, in input order; missing entries are {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "bulkGetDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<DeviceList> bulkGetDeviceRecord(Collection<Jid> userJids) {
        Objects.requireNonNull(userJids, "userJids cannot be null");

        var records = new ArrayList<DeviceList>(userJids.size());
        for (var jid : userJids) {
            records.add(store.findDeviceList(jid).orElse(null));
        }

        var withRecord = new ArrayList<Jid>(userJids.size());
        var index = 0;
        for (var jid : userJids) {
            if (records.get(index) != null) {
                withRecord.add(jid);
            }
            index++;
        }
        if (!withRecord.isEmpty()) {
            checkPnToLidMapping(withRecord, "waweb_api_device_list_bulk_get_device_record");
        }

        return records;
    }

    /**
     * Persists a device record and refreshes its cache entry. Fires a
     * {@code checkPnToLidMapping} diagnostic for the record's owner and emits a warning
     * when the record is a tombstone for the current user's account.
     *
     * @param record the device list to persist; {@link DeviceList#userJid()} is the cache key
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "createOrReplaceDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void createOrReplaceDeviceRecord(DeviceList record) {
        Objects.requireNonNull(record, "record cannot be null");

        checkPnToLidMapping(List.of(record.userJid()), "waweb_api_device_list_create_or_replace_device_record");

        store.addDeviceList(record);

        warnIfDeletingOwnDeviceList(record);
    }

    /**
     * Persists a batch of device records and refreshes their cache entries. Fires a single
     * {@code checkPnToLidMapping} diagnostic over every owner and a per-record warning
     * when a record is a tombstone for the current user's account.
     *
     * @param records the device lists to persist
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "bulkCreateOrReplaceDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void bulkCreateOrReplaceDeviceRecord(Collection<DeviceList> records) {
        Objects.requireNonNull(records, "records cannot be null");

        var ownerJids = records.stream()
                .map(DeviceList::userJid)
                .toList();
        checkPnToLidMapping(ownerJids, "waweb_api_device_list_bulk_create_or_replace_device_record");

        for (var record : records) {
            store.addDeviceList(record);
            warnIfDeletingOwnDeviceList(record);
        }
    }

    /**
     * Returns one cached device record per input JID, in input order. Missing or deleted
     * slots are {@code null}. When {@code shouldMergeAltDevices} is {@code true},
     * alternate-identity records (PN for a LID input, LID for a PN input) are folded in.
     *
     * @param userJids              the user JIDs to look up
     * @param shouldMergeAltDevices whether alternate-identity records should be merged
     * @return one record per input JID, in input order; missing or deleted entries are {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceIds",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<DeviceList> getDeviceIds(Collection<Jid> userJids, boolean shouldMergeAltDevices) {
        Objects.requireNonNull(userJids, "userJids cannot be null");

        var records = new ArrayList<>(bulkGetDeviceRecord(userJids));

        if (shouldMergeAltDevices) {
            var byOwner = new HashMap<Jid, DeviceList>();
            for (var record : records) {
                if (record != null) {
                    byOwner.put(record.userJid(), record);
                }
            }

            var positionByJid = new HashMap<Jid, Integer>();
            var pos = 0;
            for (var jid : userJids) {
                positionByJid.putIfAbsent(jid, pos);
                pos++;
            }

            var alternateJids = new ArrayList<Jid>();
            for (var jid : userJids) {
                if (!jid.hasUserServer() && !jid.hasLidServer() && !jid.hasBotServer() && !jid.hasHostedServer() && !jid.hasHostedLidServer()) {
                    continue;
                }

                var alternate = findAlternateUserWid(jid.toUserJid());
                if (alternate != null) {
                    alternateJids.add(alternate);
                }
            }

            var alternateRecords = bulkGetDeviceRecord(alternateJids);

            for (var t = 0; t < alternateRecords.size(); t++) {
                var altRecord = alternateRecords.get(t);
                if (altRecord == null || altRecord.deleted()) {
                    continue;
                }
                var altJid = alternateJids.get(t);
                var originalJid = findAlternateUserWid(altJid.toUserJid());
                if (originalJid == null) {
                    continue;
                }
                var primary = byOwner.get(originalJid);
                if (primary != null) {
                    if (!primary.deleted()) {
                        var existingIds = new HashSet<Integer>();
                        for (var device : primary.devices()) {
                            existingIds.add(device.id());
                        }
                        var newDevices = new ArrayList<>(primary.devices());
                        for (var device : altRecord.devices()) {
                            if (!existingIds.contains(device.id())) {
                                newDevices.add(device);
                            }
                        }
                        if (newDevices.size() != primary.devices().size()) {
                            var merged = new DeviceListBuilder()
                                    .userJid(primary.userJid())
                                    .devices(newDevices)
                                    .timestamp(primary.timestamp())
                                    .rawId(primary.rawId())
                                    .deleted(primary.deleted())
                                    .deletedChangedToHost(primary.deletedChangedToHost())
                                    .advAccountType(primary.advAccountType())
                                    .expectedTimestamp(primary.expectedTimestamp())
                                    .expectedTimestampLastDeviceJobTimestamp(primary.expectedTimestampLastDeviceJobTimestamp())
                                    .expectedTimestampUpdateTimestamp(primary.expectedTimestampUpdateTimestamp())
                                    .currentIndex(primary.currentIndex())
                                    .validIndexes(primary.validIndexes())
                                    .build();
                            byOwner.put(originalJid, merged);
                            var p = positionByJid.get(originalJid);
                            if (p != null) {
                                records.set(p, merged);
                            }
                        }
                    }
                } else {
                    var slot = positionByJid.get(originalJid);
                    if (slot != null) {
                        var synthesised = new DeviceListBuilder()
                                .userJid(originalJid)
                                .devices(altRecord.devices())
                                .timestamp(altRecord.timestamp())
                                .rawId(altRecord.rawId())
                                .deleted(altRecord.deleted())
                                .currentIndex(altRecord.currentIndex())
                                .validIndexes(altRecord.validIndexes())
                                .build();
                        records.set(slot, synthesised);
                    }
                }
            }
        }

        // Tombstone deleted entries to null to match WA Web's projection shape.
        var result = new ArrayList<DeviceList>(records.size());
        for (var record : records) {
            if (record == null || record.deleted()) {
                result.add(null);
            } else {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * Returns the cached device records for a collection of users in input order, with
     * deleted entries tombstoned to {@code null}.
     *
     * @param userJids the user JIDs to look up
     * @return one record per input JID, in input order; missing or deleted entries are {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceInfoForSync",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<DeviceList> getDeviceInfoForSync(Collection<Jid> userJids) {
        Objects.requireNonNull(userJids, "userJids cannot be null");
        var records = bulkGetDeviceRecord(userJids);
        var result = new ArrayList<DeviceList>(records.size());
        for (var record : records) {
            if (record == null || record.deleted()) {
                result.add(null);
            } else {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * Logs a diagnostic when a tombstone record belongs to the logged-in user. Mirrors
     * WA Web's file-private helper {@code f(t)}.
     *
     * @param record the device record about to be persisted
     */
    private void warnIfDeletingOwnDeviceList(DeviceList record) {
        if (!record.deleted()) {
            return;
        }
        var owner = record.userJid();
        var ownPn = store.jid().map(Jid::toUserJid).orElse(null);
        var ownLid = store.lid().map(Jid::toUserJid).orElse(null);
        var isMe = (ownPn != null && ownPn.equals(owner.toUserJid()))
                || (ownLid != null && ownLid.equals(owner.toUserJid()));
        if (isMe) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "syncd: trying to delete own device list");
        }
    }

    /**
     * Returns the alternate-identity user JID for the given JID. Resolves PN to LID and
     * LID to PN via the store, with me-user fast paths.
     *
     * @param userJid the user JID whose alternate identity should be resolved
     * @return the alternate user JID, or {@code null} when no mapping exists
     */
    @WhatsAppWebExport(moduleName = "WAWebApiContact",
            exports = "getAlternateUserWid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid findAlternateUserWid(Jid userJid) {
        if (userJid == null) {
            return null;
        }
        if (userJid.hasLidServer()) {
            var meLid = store.lid().map(Jid::toUserJid).orElse(null);
            var mePn = store.jid().map(Jid::toUserJid).orElse(null);
            if (mePn != null && meLid != null && userJid.equals(meLid)) {
                return mePn;
            }
            return store.findPhoneByLid(userJid).orElse(null);
        }
        var mePn = store.jid().map(Jid::toUserJid).orElse(null);
        var meLid = store.lid().map(Jid::toUserJid).orElse(null);
        if (meLid != null && mePn != null && userJid.equals(mePn)) {
            return meLid;
        }
        return store.findLidByPhone(userJid).orElse(null);
    }

    /**
     * Returns whether the given user has the specified device id in their device list.
     * The primary device (id 0) is always considered present.
     *
     * @param userJid  the user JID to check
     * @param deviceId the device id to look for
     * @return {@code true} if the device exists
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "hasDevice",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasDevice(Jid userJid, int deviceId) {
        if (deviceId == DeviceConstants.PRIMARY_DEVICE_ID) {
            return true;
        }
        var deviceList = store.findDeviceList(userJid).orElse(null);
        if (deviceList == null || deviceList.deleted()) {
            return false;
        }
        return deviceList.devices().stream()
                .anyMatch(device -> device.id() == deviceId);
    }

    /**
     * Returns the device list for the current user, preferring the PN record and falling
     * back to the LID record.
     *
     * @return the current user's device list
     *
     * @throws IllegalStateException if neither the PN nor the LID device list is available
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getMyDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public DeviceList getMyDeviceList() {
        var pnJid = store.jid().map(Jid::toUserJid).orElse(null);
        DeviceList pnRecord = null;
        if (pnJid != null) {
            pnRecord = getDeviceRecord(pnJid).orElse(null);
            if (pnRecord != null && !pnRecord.deleted()) {
                return pnRecord;
            }
        }

        var lidJid = store.lid().orElse(null);
        DeviceList lidRecord = null;
        if (lidJid != null) {
            lidRecord = getDeviceRecord(lidJid).orElse(null);
            if (lidRecord != null && !lidRecord.deleted()) {
                return lidRecord;
            }
        }

        var hasPn = pnRecord != null;
        var isPnDeleted = pnRecord != null && pnRecord.deleted();
        var hasLid = lidRecord != null;
        var isLidDeleted = lidRecord != null && lidRecord.deleted();
        LOGGER.log(System.Logger.Level.WARNING,
                "[syncd] no device list pn={0}/{1} lid={2}/{3}",
                hasPn, isPnDeleted, hasLid, isLidDeleted);
        throw new IllegalStateException("syncd: cannot find my device list");
    }

    /**
     * Returns every cached device list and logs the size and elapsed time, mirroring
     * WA Web's {@code "getAllDeviceLists: got %s devices, took %sms"} diagnostic.
     *
     * @return an unmodifiable collection of all cached device lists
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getAllDeviceLists",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Collection<DeviceList> getAllDeviceLists() {
        var start = System.nanoTime();
        var lists = store.deviceLists();
        var elapsedMs = Math.round((System.nanoTime() - start) / 1_000_000.0);
        LOGGER.log(System.Logger.Level.DEBUG,
                "getAllDeviceLists: got {0} devices, took {1}ms",
                lists.size(), elapsedMs);
        return lists;
    }

    /**
     * Processes ICDC (Identity Change Detection Consistency) metadata from an incoming
     * message. Updates the {@code expectedTs} tracking on the sender's record and, when
     * the sender is self, on the 1:1 recipient's record. Messages from the primary device
     * carrying only a sender timestamp trigger a minimal sync via
     * {@link #handleMinimalTimestampOnlySync}.
     *
     * @param senderJid        the sender's JID, including device part
     * @param recipientUserJid the recipient user JID for 1:1 chats, or {@code null} for groups
     * @param metadata         the device-list metadata from the message's context info
     */
    @WhatsAppWebExport(moduleName = "WAWebIcdcHandlerApi",
            exports = "handleICDCData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handleICDCData(Jid senderJid, Jid recipientUserJid, DeviceListMetadata metadata) {
        if (metadata == null) {
            return;
        }

        // Primary device with sender timestamp but no sender-key hash: take the minimal
        // sync path with {@code timestamp+1}.
        if ((senderJid.device() == DeviceConstants.PRIMARY_DEVICE_ID)
                && metadata.senderTimestamp().isPresent()
                && metadata.senderKeyHash().isEmpty()) {
            var senderTimestamp = metadata.senderTimestamp().get();
            var adjustedTimestamp = senderTimestamp.plusSeconds(1);
            handleMinimalTimestampOnlySync(senderJid.toUserJid(), adjustedTimestamp);
            return;
        }

        var entries = new ArrayList<IcdcTimestampEntry>();
        entries.add(new IcdcTimestampEntry(senderJid.toUserJid(), metadata.senderTimestamp().orElse(null)));

        var myUser = store.jid().map(jid -> jid.user()).orElse(null);
        var isSelf = myUser != null && senderJid.user().equals(myUser);
        if (isSelf && recipientUserJid != null) {
            entries.add(new IcdcTimestampEntry(recipientUserJid, metadata.recipientTimestamp().orElse(null)));
        }

        var lastADVCheckTime = store.lastAdvCheckTime().orElse(null);

        var updatedRecords = new ArrayList<DeviceList>();
        for (var entry : entries) {
            if (entry.timestamp() == null) {
                continue;
            }

            var cached = store.findDeviceList(entry.jid()).orElse(null);
            if (cached == null || cached.deleted()) {
                continue;
            }

            var computed = DeviceExpectedTsUtils.computeExpectedTimestampForDeviceRecord(
                    entry.timestamp(), cached, lastADVCheckTime);

            var expectedTsChanged = !Objects.equals(
                    computed.expectedTimestamp().orElse(null), cached.expectedTimestamp());
            var lastJobTsChanged = !Objects.equals(
                    computed.expectedTimestampLastDeviceJobTimestamp().orElse(null),
                    cached.expectedTimestampLastDeviceJobTimestamp());
            var updateTsChanged = !Objects.equals(
                    computed.expectedTimestampUpdateTimestamp().orElse(null),
                    cached.expectedTimestampUpdateTimestamp());

            if (expectedTsChanged || lastJobTsChanged || updateTsChanged) {
                var updated = new DeviceListBuilder()
                        .userJid(cached.userJid())
                        .devices(cached.devices())
                        .timestamp(cached.timestamp())
                        .rawId(cached.rawId())
                        .deleted(cached.deleted())
                        .deletedChangedToHost(cached.deletedChangedToHost())
                        .advAccountType(cached.advAccountType())
                        .expectedTimestamp(computed.expectedTimestamp().orElse(null))
                        .expectedTimestampLastDeviceJobTimestamp(computed.expectedTimestampLastDeviceJobTimestamp().orElse(null))
                        .expectedTimestampUpdateTimestamp(computed.expectedTimestampUpdateTimestamp().orElse(null))
                        .currentIndex(cached.currentIndex())
                        .validIndexes(cached.validIndexes())
                        .build();
                updatedRecords.add(updated);
            }
        }

        if (!updatedRecords.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "handleICDCData: updated expected timestamp for {0} records",
                    updatedRecords.size());
            for (var record : updatedRecords) {
                store.addDeviceList(record);
            }
        }
    }

    /**
     * Handles the minimal timestamp-only sync triggered when a primary-device message
     * carries only a sender timestamp (no key hash). Resets the cached record to the
     * primary device and bumps the timestamp.
     *
     * @param userJid           the user JID to update
     * @param adjustedTimestamp the sender timestamp plus one second
     */
    private void handleMinimalTimestampOnlySync(Jid userJid, Instant adjustedTimestamp) {
        var cachedList = store.findDeviceList(userJid).orElse(null);

        if (cachedList == null || cachedList.deleted()) {
            return;
        }

        if (adjustedTimestamp.isBefore(cachedList.timestamp())) {
            return;
        }

        var lastADVCheckTime = store.lastAdvCheckTime().orElse(null);

        var finalExpectedTs = cachedList.expectedTimestamp();
        var finalUpdateTs = cachedList.expectedTimestampUpdateTimestamp();
        var finalLastJobTs = cachedList.expectedTimestampLastDeviceJobTimestamp();

        if (DeviceExpectedTsUtils.shouldClearExpectedTimestamp(
                adjustedTimestamp, null, cachedList, lastADVCheckTime)) {
            finalExpectedTs = null;
            finalUpdateTs = null;
            finalLastJobTs = null;
        }

        var updated = new DeviceListBuilder()
                .userJid(cachedList.userJid())
                .devices(List.of(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0)))
                .timestamp(adjustedTimestamp)
                .rawId(cachedList.rawId())
                .deleted(cachedList.deleted())
                .deletedChangedToHost(cachedList.deletedChangedToHost())
                .advAccountType(cachedList.advAccountType())
                .expectedTimestamp(finalExpectedTs)
                .expectedTimestampLastDeviceJobTimestamp(finalLastJobTs)
                .expectedTimestampUpdateTimestamp(finalUpdateTs)
                .currentIndex(cachedList.currentIndex())
                .validIndexes(cachedList.validIndexes())
                .build();

        store.addDeviceList(updated);
    }

    /**
     * Internal {@code (jid, timestamp)} pair used by {@link #handleICDCData} to walk
     * sender and recipient entries uniformly.
     *
     * @param jid       the user JID
     * @param timestamp the per-side timestamp from the message metadata
     */
    private record IcdcTimestampEntry(Jid jid, Instant timestamp) {
    }

    /**
     * Handles hosted ICDC metadata inline during message processing. When the relevant
     * account type is {@code HOSTED} and the cached record is not yet hosted, clears the
     * record and triggers a device sync. The {@code E2EE} case is treated as a hint to
     * release any prior dedup state for that sender.
     *
     * @param chatJid   the chat JID (must be a 1:1 user chat)
     * @param authorJid the message author JID
     * @param metadata  the device-list metadata from the message's context info
     * @return the resulting hosted-ICDC outcome
     */
    @WhatsAppWebExport(moduleName = "WAWebIcdcHandlerApi",
            exports = "handleHostedIcdcMetadataInline",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public HostedIcdcResult handleHostedIcdcMetadataInline(Jid chatJid, Jid authorJid, DeviceListMetadata metadata) {
        if (!isBizHostedDevicesEnabled()) {
            return HostedIcdcResult.DEFAULT;
        }

        if (store.jid().map(myJid -> myJid.toUserJid().equals(chatJid.toUserJid())).orElse(false)) {
            return HostedIcdcResult.DEFAULT;
        }

        if (!chatJid.hasUserServer()) {
            return HostedIcdcResult.DEFAULT;
        }

        if (metadata == null) {
            return HostedIcdcResult.DEFAULT;
        }

        // Pick the account type belonging to the remote side: if the author is self,
        // the relevant side is the chat peer (receiver), otherwise it is the sender.
        var isAuthorMe = store.jid().map(myJid -> myJid.toUserJid().equals(authorJid.toUserJid())).orElse(false);
        var accountType = isAuthorMe
                ? metadata.receiverAccountType().orElse(null)
                : metadata.senderAccountType().orElse(null);
        var relevantJid = isAuthorMe ? chatJid.toUserJid() : authorJid.toUserJid();

        if (accountType == null) {
            return HostedIcdcResult.DEFAULT;
        }

        if (accountType == ADVEncryptionType.E2EE) {
            offlineBizHostedSenderICDCProcessedCache.remove(relevantJid);
            return HostedIcdcResult.DEFAULT;
        }

        if (accountType != ADVEncryptionType.HOSTED) {
            return HostedIcdcResult.DEFAULT;
        }

        if (offlineBizHostedSenderICDCProcessedCache.contains(relevantJid)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "[handleHostedIcdcMetadataInline] skip, already processed {0}", relevantJid);
            return new HostedIcdcResult(false, true);
        }

        offlineBizHostedSenderICDCProcessedCache.add(relevantJid);
        LOGGER.log(System.Logger.Level.DEBUG,
                "handleIcdcMetadataInline: add to interop cache for {0}", relevantJid);

        store.addToInteropHostedVerificationCache(relevantJid);

        var existingRecord = store.findDeviceList(relevantJid).orElse(null);
        var senderTimestamp = metadata.senderTimestamp()
                .map(Instant::getEpochSecond)
                .orElse(0L);
        var existingTimestamp = existingRecord != null ? existingRecord.timestamp().getEpochSecond() : 0L;

        if (senderTimestamp >= existingTimestamp) {
            if (existingRecord == null || existingRecord.advAccountType() != ADVEncryptionType.HOSTED) {
                if (existingRecord != null) {
                    cleanupAllSessionsForUser(relevantJid, existingRecord);
                }

                // Mark as deleted with deletedChangedToHost=true so the next sync routes
                // through the hosted-transition path.
                store.addDeviceList(createDeletedDeviceList(relevantJid, true));

                if (store.isResumeFromRestartComplete()) {
                    Thread.startVirtualThread(() -> {
                        try {
                            getDeviceLists(List.of(relevantJid), null, null, false);
                        } catch (Exception e) {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "Failed to sync device list for hosted transition: {0}", e.getMessage());
                        }
                    });
                } else {
                    var pendingSync = PendingDeviceSync.of(List.of(relevantJid), null);
                    store.addPendingDeviceSync(pendingSync);
                }

                LOGGER.log(System.Logger.Level.DEBUG,
                        "handleHostedIcdcMetadataInline: update ADV type for {0}", relevantJid);

                // {@code hostedBizEncMismatch} is set when the prior record was E2EE or has
                // never observed the hosted transition.
                var mismatch = (existingRecord != null && existingRecord.advAccountType() == ADVEncryptionType.E2EE)
                        || (existingRecord == null || !existingRecord.deletedChangedToHost());
                return new HostedIcdcResult(mismatch, true);
            }

            return new HostedIcdcResult(false, true);
        }

        return HostedIcdcResult.DEFAULT;
    }

    /**
     * Handles an ADV device update carried by an incoming PKMSG. Decides between a full
     * list reset (missing record, deleted record, {@code rawId} changed, or new primary
     * identity) and an incremental {@code keyIndex} update for the companion device.
     *
     * @param deviceJid   the full device JID; must not be the primary device
     * @param rawId       the {@code rawId} from the signed device identity
     * @param timestamp   the timestamp from the signed device identity in Unix seconds
     * @param keyIndex    the key index from the signed device identity
     * @param identityKey the message-side primary identity key, or {@code null}
     * @param accountType the ADV account type, or {@code null}
     *
     * @throws IllegalArgumentException if {@code deviceJid} is the primary device
     * @throws IllegalStateException    if an incremental update detects an out-of-order
     *                                  timestamp combined with an unknown {@code keyIndex}
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvHandlerApi",
            exports = "handleADVDeviceUpdateForMessage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handleADVDeviceUpdateForMessage(
            Jid deviceJid,
            String rawId,
            long timestamp,
            int keyIndex,
            byte[] identityKey,
            ADVEncryptionType accountType
    ) {
        Objects.requireNonNull(rawId, "rawId cannot be null");

        if (deviceJid.device() == DeviceConstants.PRIMARY_DEVICE_ID) {
            throw new IllegalArgumentException(
                    "handleADVDeviceUpdateForMessage: device must not be primary: " + deviceJid);
        }

        var userJid = deviceJid.toUserJid();
        var existingRecord = store.findDeviceList(userJid).orElse(null);

        var storedIdentityKey = store.findIdentityByAddress(userJid.toSignalAddress())
                .map(SignalIdentityPublicKey::toEncodedPoint)
                .orElse(null);

        // New primary identity when no stored identity exists, or when the stored bytes
        // differ from the message-side primary identity.
        var isNewPrimaryIdentity = storedIdentityKey == null
                || (identityKey != null && !Arrays.equals(storedIdentityKey, identityKey));

        if (existingRecord != null && !existingRecord.deleted()
                && existingRecord.timestamp() != null
                && timestamp < existingRecord.timestamp().getEpochSecond()) {
            return;
        }

        var lastADVCheckTime = store.lastAdvCheckTime().orElse(null);

        if (existingRecord == null || existingRecord.deleted()
                || !rawId.equals(existingRecord.rawId()) || isNewPrimaryIdentity) {
            LOGGER.log(System.Logger.Level.INFO,
                    "ADV device update for message: list reset for {0} (rawId={1}, isNewPrimary={2})",
                    userJid, rawId, isNewPrimaryIdentity);

            if (existingRecord != null && !existingRecord.deleted()) {
                cleanupAllSessionsForUser(userJid, existingRecord);
            }

            var devices = List.of(
                    DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0),
                    DeviceInfo.ofE2EE(deviceJid.device(), keyIndex)
            );

            Instant listTimestamp;
            if (existingRecord != null && existingRecord.timestamp() != null) {
                listTimestamp = existingRecord.timestamp();
            } else {
                var expirationDays = abPropsService.getInt(ABProp.NUM_DAYS_KEY_INDEX_LIST_EXPIRATION);
                var pastSeconds = (expirationDays - 1) * 24 * 60 * 60L;
                listTimestamp = Instant.now().minusSeconds(pastSeconds);
            }

            var newList = new DeviceListBuilder()
                    .userJid(userJid)
                    .devices(devices)
                    .timestamp(listTimestamp)
                    .rawId(rawId)
                    .deleted(false)
                    .advAccountType(accountType)
                    .build();
            store.addDeviceList(newList);

            if (storedIdentityKey != null && isNewPrimaryIdentity) {
                try {
                    store.saveIdentity(
                            userJid.toSignalAddress(),
                            SignalIdentityPublicKey.ofDirect(storedIdentityKey)
                    );
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Failed to save identity for {0}: {1}", userJid, e.getMessage());
                }
            }

            store.markKeyRotation(userJid);
        } else {
            var existingDevices = new LinkedHashMap<Integer, DeviceInfo>();
            for (var device : existingRecord.devices()) {
                existingDevices.put(device.id(), device);
            }

            var cachedValidIndexes = existingRecord.validIndexes();
            if (!cachedValidIndexes.isEmpty()
                    && existingRecord.timestamp().getEpochSecond() >= timestamp
                    && !cachedValidIndexes.contains(keyIndex)) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleADVDeviceIdentity:handleNoListReset: out-of-order timestamp detected " +
                                "for {0}: incomingTs={1}, cachedTs={2}, keyIndex={3}, validIndexes={4}",
                        userJid, timestamp, existingRecord.timestamp(), keyIndex, cachedValidIndexes);
                throw new IllegalStateException(
                        "handleADVDeviceIdentity:handleNoListReset: out-of-order timestamp detected");
            }

            var currentKeyIndex = existingDevices.containsKey(deviceJid.device())
                    ? existingDevices.get(deviceJid.device()).keyIndex()
                    : -1;
            if (currentKeyIndex != keyIndex) {
                existingDevices.put(deviceJid.device(), DeviceInfo.ofE2EE(deviceJid.device(), keyIndex));

                var updatedDevices = new ArrayList<>(existingDevices.values());

                var computedExpectedTs = DeviceExpectedTsUtils.computeExpectedTimestampForDeviceRecord(
                        Instant.ofEpochSecond(timestamp), existingRecord, lastADVCheckTime);

                var updatedList = new DeviceListBuilder()
                        .userJid(userJid)
                        .devices(updatedDevices)
                        .timestamp(existingRecord.timestamp())
                        .rawId(existingRecord.rawId())
                        .deleted(false)
                        .advAccountType(accountType != null ? accountType : existingRecord.advAccountType())
                        .expectedTimestamp(computedExpectedTs.expectedTimestamp().orElse(null))
                        .expectedTimestampLastDeviceJobTimestamp(computedExpectedTs.expectedTimestampLastDeviceJobTimestamp().orElse(null))
                        .expectedTimestampUpdateTimestamp(computedExpectedTs.expectedTimestampUpdateTimestamp().orElse(null))
                        .currentIndex(existingRecord.currentIndex())
                        .validIndexes(existingRecord.validIndexes())
                        .build();

                var changes = updatedList.mismatch(existingRecord);
                if (!changes.identityChangedDevices().isEmpty()) {
                    for (var changedDevice : changes.identityChangedDevices()) {
                        store.markIdentityChange(changedDevice);
                        store.cleanupSignalSessions(changedDevice);
                    }
                }
                store.addDeviceList(updatedList);
                store.markKeyRotation(userJid);

                if (identityKey != null) {
                    try {
                        store.saveIdentity(
                                deviceJid.toSignalAddress(),
                                SignalIdentityPublicKey.ofDirect(identityKey)
                        );
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Failed to save identity for {0}: {1}", deviceJid, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Extracts and validates a local device identity from a pair-success response.
     * Validates the {@code SignedDeviceIdentityHMAC} using the {@code advSecretKey},
     * verifies the account signature, and generates the device signature using the
     * local identity key.
     *
     * @param deviceIdentityNode the {@code device-identity} node from the pair-success stanza
     * @return the validated identity, or {@link Optional#empty()} when validation fails
     */
    @WhatsAppWebExport(moduleName = "WAWebHandlePairSuccess",
            exports = "handlePairSuccess",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<ADVSignedDeviceIdentity> extractAndValidateLocalSignedDeviceIdentity(Node deviceIdentityNode) {
        try {
            var signedDeviceIdentity = advValidator.extractAndValidateLocalSignedDeviceIdentity(deviceIdentityNode);
            return Optional.of(signedDeviceIdentity);
        } catch (WhatsAppAdvValidationException exception) {
            client.handleFailure(exception);
            return Optional.empty();
        }
    }

    /**
     * Persists the {@code accountSignatureKey} extracted from a validated pair-success
     * identity as the local user's Signal identity (against device 0). Failures are
     * logged but never thrown: WA Web tolerates the signal store write failing because
     * the key is rederived on the next sync.
     *
     * @param deviceJid           the local device JID assigned by the server
     * @param accountSignatureKey the {@code accountSignatureKey} bytes, possibly {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandlePairSuccess",
            exports = "handlePairSuccess",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void persistLocalDeviceIdentityFromPairSuccess(Jid deviceJid, byte[] accountSignatureKey) {
        if (deviceJid == null || accountSignatureKey == null || accountSignatureKey.length == 0) {
            return;
        }
        try {
            preKeyHandler.storeIdentityFromAccountSignatureKey(deviceJid, accountSignatureKey);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to persist local identity from pair-success accountSignatureKey for {0}: {1}",
                    deviceJid, exception.getMessage());
        }
    }
}
