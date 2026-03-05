package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.adv.DeviceADVChecker;
import com.github.auties00.cobalt.device.adv.DeviceADVValidator;
import com.github.auties00.cobalt.device.fanout.DeviceFanoutCalculator;
import com.github.auties00.cobalt.device.icdc.IcdcComputer;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.device.fanout.DevicePhashCalculator;
import com.github.auties00.cobalt.device.fanout.DevicePhashVersion;
import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.device.adv.ValidatedKeyIndexListResult;
import com.github.auties00.cobalt.device.stanza.DeviceUSyncQueryBuilder;
import com.github.auties00.cobalt.device.stanza.DeviceUSyncResponseParser;
import com.github.auties00.cobalt.device.timestamp.DeviceExpectedTsUtils;
import com.github.auties00.cobalt.exception.WhatsAppAdvValidationException;
import com.github.auties00.cobalt.exception.WhatsAppDeviceSyncException;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.device.key.DevicePreKeyHandler;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.info.DeviceListHashInfo;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
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
 * Manages device lists, USync queries, and message fanout calculations.
 * </p>
 * This service handles synchronization of device information with the WhatsApp server
 * using the USync protocol, validates ADV signatures for companion devices, and calculates
 * which devices should receive messages (fanout).
 *
 * @apiNote WAWebAdvSyncDeviceListApi: provides syncDeviceList, syncMyDeviceList, syncAndGetDeviceList.
 * WAWebDBDeviceListFanout.getFanOutList: generates device lists for message fanout.
 * WAWebAdvHandlerApi: handles USync response processing and dispatches to handlers.
 * WAWebHandleAdvKeyIndexResultApi: processes full device list responses with key index validation.
 * WAWebHandleAdvOmittedResultApi: handles omitted responses when server dhash matches local.
 * WAWebHandleAdvDeviceNotificationApi: processes real-time device add/remove notifications.
 */
public final class DeviceService {
    /**
     * Logger for device service operations.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceService.class.getName());

    /**
     * Joiner for parallel USync batch processing using structured concurrency.
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
     * The WhatsApp client instance for sending requests and accessing configuration.
     */
    private final WhatsAppClient client;

    /**
     * The store for persisting and retrieving device lists, identities, and sessions.
     */
    private final WhatsAppStore store;

    /**
     * Map of pending device list fetches for request deduplication.
     *
     * <p>When multiple callers request the same user's device list simultaneously,
     * only one USync request is made and all callers wait on the same future.
     *
     * @apiNote WAWebAdvSyncDeviceListApi.syncDeviceList: uses a Map (_) to track
     * pending promises and avoid duplicate requests for the same user.
     */
    private final ConcurrentHashMap<Jid, CompletableFuture<DeviceList>> pendingFetches;

    /**
     * Scheduler for periodic ADV device info expiration checks.
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob: runs every 24 hours to detect and handle
     * expired device lists based on timestamp age and expectedTs staleness.
     */
    private final DeviceADVChecker advCheckScheduler;

    /**
     * Service for fetching and storing Signal pre-key bundles and identity keys.
     *
     * @apiNote WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: prefetches identity
     * keys for users with validated key index info after device sync.
     */
    private final DevicePreKeyHandler preKeyHandler;

    /**
     * Service for accessing AB (A/B test) property values for feature gating.
     *
     * @apiNote WAWebABProps: provides configuration values that control feature behavior
     * such as hosted device support, username sync, and expiration thresholds.
     */
    private final ABPropsService abPropsService;

    /**
     * Service for validating ADV signatures on device identities and key index lists.
     *
     * @apiNote WAWebAdvSignatureApi: validates Curve25519 signatures using the appropriate headers.
     */
    private final DeviceADVValidator advValidator;

    /**
     * Parser for USync IQ response nodes into structured device list results.
     *
     * @apiNote WAWebUsyncDevice.deviceParser: extracts device lists, key indices,
     * timestamps, and hosting status from USync response XML nodes.
     */
    private final DeviceUSyncResponseParser usyncResponseParser;

    /**
     * Service for calculating message fanout (which devices receive a message).
     *
     * @apiNote WAWebDBDeviceListFanout.getFanOutList: computes the set of device JIDs
     * that should receive a message, excluding self devices and filtering hosted devices.
     */
    private final DeviceFanoutCalculator fanoutCalculator;

    /**
     * Service for computing ICDC (Identity Change Detection Consistency) metadata.
     *
     * @apiNote WAWebIdentityIcdcApi: computes identity key hashes and device
     * timestamps for inclusion in every outgoing message's messageContextInfo.
     */
    private final IcdcComputer icdcComputer;

    /**
     * Service for calculating participant hashes (phash) for group message verification.
     *
     * @apiNote WAWebPhashUtils: computes SHA-1 (V1) or SHA-256 (V2) hash of sorted
     * device JIDs to verify sender and server agree on recipient list.
     */
    private final DevicePhashCalculator phashCalculator;

    /**
     * Lock for serializing device table updates.
     *
     * <p>Ensures consistency when updating multiple related tables (device-list, session,
     * sender-key, missing-keys, contact) that must remain synchronized.
     *
     * @apiNote WAWebApiGetDeviceUpdateLock.getDeviceUpdateLock: device table updates should
     * be serialized to prevent race conditions when updating device-list, session, sender-key,
     * missing-keys, and contact tables.
     */
    private final ReentrantLock deviceUpdateLock;

    /**
     * Cache for deduplicating initial hosted system messages.
     *
     * <p>Maps user JID to the timestamp when a hosted system message was last created,
     * preventing duplicate messages within the same second.
     *
     * @apiNote WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg: prevents creating
     * duplicate system messages for the same user within the same second.
     */
    private final ConcurrentHashMap<Jid, Instant> hostedSystemMsgDedupCache;

    /**
     * Creates a new device service.
     *
     * @param client         the WhatsApp client
     * @param abPropsService the AB props service for feature gating
     * @param sessionCipher the session cipher
     */
    public DeviceService(WhatsAppClient client, ABPropsService abPropsService, SignalSessionCipher sessionCipher) {
        this.client = client;
        this.store = client.store();
        this.preKeyHandler = new DevicePreKeyHandler(client, sessionCipher);
        this.abPropsService = abPropsService;
        this.advValidator = new DeviceADVValidator(store, abPropsService);
        this.fanoutCalculator = new DeviceFanoutCalculator(abPropsService);
        this.icdcComputer = new IcdcComputer(store, abPropsService);
        this.phashCalculator = new DevicePhashCalculator(abPropsService);
        this.usyncResponseParser = new DeviceUSyncResponseParser(advValidator);
        this.pendingFetches = new ConcurrentHashMap<>();
        this.advCheckScheduler = new DeviceADVChecker(client, this, abPropsService);
        this.deviceUpdateLock = new ReentrantLock();
        this.hostedSystemMsgDedupCache = new ConcurrentHashMap<>();
    }

    /**
     * Executes a task while holding the device update lock.
     *
     * <p>This ensures consistency when updating multiple related tables that must
     * remain synchronized (device-list, session, sender-key, missing-keys, contact).
     *
     * @param task the task to execute
     *
     * @apiNote WAWebApiGetDeviceUpdateLock.getDeviceUpdateLock: ensures device table updates
     * (device-list, session, sender-key, missing-keys, contact) are serialized.
     */
    private void withDeviceUpdateLock(Runnable task) {
        deviceUpdateLock.lock();
        try {
            task.run();
        } finally {
            deviceUpdateLock.unlock();
        }
    }

    /**
     * Checks if the hostedOverrideAdvAccountSignatureKey feature is enabled.
     *
     * <p>When enabled, allows using the embedded accountSignatureKey from the protobuf
     * for identity verification of hosted devices, rather than requiring a stored identity.
     *
     * @return {@code true} if the feature is enabled
     *
     * @apiNote WAWebBizCoexGatingUtils.hostedOverrideAdvAccountSignatureKeyEnabled: requires
     * both adv_accept_hosted_devices AND override_adv_account_signature_key_enabled AB props
     * to be true. Used in WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey.
     */
    private boolean isHostedOverrideAdvAccountSignatureKeyEnabled() {
        // WAWebBizCoexGatingUtils: first check if hosted devices are enabled at all
        var hostedDevicesEnabled = abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
        if (!hostedDevicesEnabled) {
            return false;
        }
        // WAWebBizCoexGatingUtils: then check the override flag
        return abPropsService.getBool(ABProp.OVERRIDE_ADV_ACCOUNT_SIGNATURE_KEY_ENABLED);
    }

    /**
     * Gets device lists for the specified users with phash pre-check optimization.
     *
     * <p>If {@code expectedPhash} is provided and matches the locally computed phash,
     * the server sync is skipped entirely. This is an optimization for group messages
     * where the sender already knows the expected participant hash.
     *
     * @param userJids            the user JIDs to get device lists for
     * @param context             the sync context (e.g., "message", "interactive", "adv_expiration")
     * @param expectedPhash       optional phash to compare against local device list; if matches, sync is skipped
     * @param shouldMergeAltDevices whether to merge PN/LID alternate device lists for the same user
     * @return the device lists, one per user JID
     *
     * @apiNote WAWebAdvSyncDeviceListApi.syncAndGetDeviceList: if local phash matches
     * expectedPhash (l===d), the function returns early without sending USync request.
     */
    public Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices) {
        // WAWebAdvSyncDeviceListApi.syncDeviceList: if phash is provided (l!=null), check local match
        if (expectedPhash != null && !expectedPhash.isEmpty()) {
            // WAWebApiDeviceList.getDeviceIds: get cached device lists for all requested JIDs
            var cachedLists = userJids.stream()
                    .map(store::findDeviceList)
                    .flatMap(Optional::stream)
                    .toList();

            // WAWebAdvSyncDeviceListApi.syncDeviceList: if all cached, compute local phash and compare
            if (cachedLists.size() == userJids.size()) {
                try {
                    // WAWebPhashUtils.phashV2: extract device JIDs from cached lists
                    var allDeviceJids = cachedLists.stream()
                            .map(DeviceList::deviceJids)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toUnmodifiableSet());

                    // WAWebPhashUtils.phashV2: compute local phash (allowIncludeMetaBot=true for AB check)
                    var localPhash = phashCalculator.calculate(allDeviceJids, DevicePhashVersion.V2, true);

                    // WAWebAdvSyncDeviceListApi.syncDeviceList: if (l === d) return early
                    if (localPhash.equals(expectedPhash)) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Phash pre-check passed, skipping device sync");
                        return mergeAlternateDeviceLists(cachedLists);
                    }
                } catch (NoSuchAlgorithmException e) {
                    // WAWebAdvSyncDeviceListApi: fall through to normal sync on error
                }
            }
        }

        // WAWebAdvSyncDeviceListApi.syncDeviceList: normal sync path
        var result = new HashSet<DeviceList>();
        var missingJids = new ArrayList<Jid>();

        // WAWebApiDeviceList.getDeviceIds: check local cache first before querying server
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

            // WAWebBizCoexUtils: if deleted but not due to hosted transition, fallback to primary
            var fallback = createPrimaryOnlyDeviceList(jid);
            store.addDeviceList(fallback);
            result.add(fallback);
        }

        // WAWebAdvSyncDeviceListApi.syncDeviceList: fetch missing device lists from server
        if (!missingJids.isEmpty()) {
            var fetched = fetchDeviceListsFromServer(missingJids, context);
            for (var deviceList : fetched) {
                store.addDeviceList(deviceList);
                result.add(deviceList);
            }
        }

        // WAWebApiDeviceList.getDeviceIds: merge PN/LID alternate device lists when shouldMergeAltDevices=true
        if (shouldMergeAltDevices) {
            return mergeAlternateDeviceLists(result);
        }

        return result;
    }

    /**
     * Merges device lists for users who have both PN and LID identities.
     *
     * <p>During LID migration, users may have device lists under both their phone number (PN)
     * and LID (Linked ID). This method deduplicates by device ID, with the PN version taking
     * precedence over the LID version.
     *
     * @param deviceLists the device lists to merge
     * @return merged device lists with one entry per canonical user
     *
     * @apiNote WAWebLidMigrationUtils: during migration, both PN and LID may have device records.
     * The PN identity is preferred as the canonical representation.
     */
    private Set<DeviceList> mergeAlternateDeviceLists(Collection<DeviceList> deviceLists) {
        var mergedMap = new LinkedHashMap<Jid, DeviceList>();

        for (var deviceList : deviceLists) {
            var userJid = deviceList.userJid();
            var canonicalJid = userJid;

            // WAWebLidMigrationUtils: resolve LID to PN for canonical representation
            if (userJid.hasLidServer()) {
                var phoneJid = store.findPhoneByLid(userJid)
                        .orElse(null);
                if (phoneJid != null) {
                    canonicalJid = phoneJid;
                }
            }

            // WAWebLidMigrationUtils: existing (PN) takes precedence over incoming (LID)
            mergedMap.merge(canonicalJid, deviceList, DeviceList::merge);
        }

        return Set.copyOf(mergedMap.values());
    }

    /**
     * Checks and logs missing PN-to-LID mappings for a collection of JIDs.
     *
     * <p>This is a diagnostic check that helps identify users who should have LID mappings
     * but don't. Missing mappings can cause issues with device list synchronization and
     * message routing.
     *
     * @param userJids the user JIDs to check
     * @param caller   the caller context for logging (e.g., "device_sync_request")
     *
     * @apiNote WAWebApiContact.checkPnToLidMapping: diagnostic check that logs warnings
     * for phone number JIDs missing LID mappings. Called before USync requests.
     */
    private void checkPnToLidMapping(Collection<Jid> userJids, String caller) {
        // WAWebApiContact.checkPnToLidMapping: filter to only phone number JIDs
        // Excludes bots (!isBot()), hosted (!isHosted()), and LIDs (!isLid())
        var phoneNumberJids = userJids.stream()
                .filter(jid -> !jid.hasBotServer() && !jid.hasHostedServer() && !jid.hasLidServer())
                .map(Jid::toUserJid)
                .toList();

        if (phoneNumberJids.isEmpty()) {
            return;
        }

        // WAWebApiContact.checkPnToLidMapping: check which phone numbers are missing LID mappings
        var missingMappings = phoneNumberJids.stream()
                .filter(jid -> store.findLidByPhone(jid).isEmpty())
                .toList();

        if (!missingMappings.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "LID mapping missing for {0} of {1} phone numbers during {2}. Missing: {3}",
                    missingMappings.size(), phoneNumberJids.size(), caller,
                    missingMappings.stream().limit(5).toList());
        }
    }

    /**
     * Fetches device lists from the server with request deduplication.
     *
     * <p>If a fetch is already in progress for a JID, waits for that result instead of
     * making a duplicate request. This prevents thundering herd issues when multiple
     * messages target the same recipient simultaneously.
     *
     * <p>The method builds USync queries with optional device_hash (dhash) for delta updates.
     * If the server's dhash matches our local hash, it returns an omitted result and we
     * preserve our cached device list.
     *
     * @param userJids the user JIDs to fetch
     * @param context  the sync context (e.g., "message", "interactive", "adv_expiration")
     * @return list of device lists fetched or resolved from cache
     *
     * @apiNote WAWebAdvSyncDeviceListApi.syncDeviceList: uses a Map (_) to deduplicate
     * requests. Builds USync query via WAWebUsync.USyncQuery with device protocol.
     * Processes results via WAWebAdvHandlerApi.handleADVDeviceSyncResult.
     */
    private List<DeviceList> fetchDeviceListsFromServer(Collection<Jid> userJids, String context) {
        var result = new ArrayList<DeviceList>();
        var toFetch = new HashSet<Jid>();

        // WAWebAdvSyncDeviceListApi.syncDeviceList: deduplicate requests using pendingFetches map (_)
        for (var jid : userJids) {
            var pendingFuture = pendingFetches.get(jid);
            if (pendingFuture != null) {
                // WAWebAdvSyncDeviceListApi: m.add(t) - wait for existing promise
                try {
                    result.add(pendingFuture.join());
                } catch (Exception e) {
                    // WAWebAdvSyncDeviceListApi: if pending request failed, add to fetch list
                    toFetch.add(jid);
                }
            } else {
                toFetch.add(jid);
            }
        }

        if (toFetch.isEmpty()) {
            return result;
        }

        // WAWebAdvSyncDeviceListApi: _.set(createDeviceListPK(e), g) - track pending requests
        var futures = new ConcurrentHashMap<Jid, CompletableFuture<DeviceList>>();
        for (var jid : toFetch) {
            var future = new CompletableFuture<DeviceList>();
            pendingFetches.put(jid, future);
            futures.put(jid, future);
        }

        try {
            // WAWebAdvSyncDeviceListApi.syncDeviceList (function h/y): compute device_hash using phashV2
            // on device WIDs formatted as user.0:device@s.whatsapp.net
            var hashInfos = new HashMap<Jid, DeviceListHashInfo>();
            for (var jid : toFetch) {
                var cached = store.findDeviceList(jid);
                if (cached.isPresent()) {
                    try {
                        // WAWebPhashUtils.phashV2: SHA-256 on sorted legacy JID strings
                        // allowIncludeMetaBot=false for per-user hash (not group fanout)
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
                        // WAWebAdvSyncDeviceListApi: continue without hash on algorithm error
                    }
                }
            }

            // WAWebApiContact.checkPnToLidMapping: diagnostic check before USync
            checkPnToLidMapping(toFetch, "device_sync_request");

            // WAWebUsernameGatingUtils.usernameUsyncEnabled: check if username protocol should be included
            var includeUsernameProtocol = abPropsService.getBool(ABProp.USERNAME_USYNC);

            // WAWebUsync.USyncQuery.execute: batch fetch from server via deprecatedSendIq
            var batches = DeviceUSyncQueryBuilder.build(toFetch, context, hashInfos, includeUsernameProtocol);
            var fetchedResults = getDevicesFetchedResults(batches);

            // WAWebContactSyncUtils.backfillMissingDeviceSyncEntries: if PN was requested but
            // server returned LID entry only, duplicate LID result as PN
            fetchedResults = backfillMissingDeviceSyncEntries(toFetch, fetchedResults);

            // WAWebAdvHandlerApi.handleADVDeviceSyncResult: filter hosted devices (id===99) when
            // bizHostedDevicesEnabled is false
            if (!isBizHostedDevicesEnabled()) {
                fetchedResults = filterHostedDevicesFromResults(fetchedResults);
            }

            // WAWebAdvHandlerApi.handleADVDeviceSyncResult: process full and omitted results
            var lastADVCheckTime = store.lastAdvCheckTime()
                    .orElse(null);

            // WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: track users for identity key prefetch
            var usersWithValidatedKeyIndex = new ArrayList<Jid>();
            // WAWebBizCoexGatingUtils.hostedOverrideAdvAccountSignatureKeyEnabled: check feature flag
            var hostedOverrideEnabled = isHostedOverrideAdvAccountSignatureKeyEnabled();
            // WAWebSyncdStoreMissingKeys.updateMissingKeyDevices: track own device removals
            var ownDevicesRemoved = false;
            var myJid = store.jid().map(Jid::toUserJid).orElse(null);
            for (var deviceResult : fetchedResults) {
                var deviceList = switch (deviceResult) {
                    case DeviceListResult.Full full -> {
                        // WAWebHandleAdvKeyIndexResultApi.handleKeyIndexResult: full device list response
                        var newList = full.deviceList();

                        // WAWebUsyncUsername.usernameParser: store username if present in response
                        full.username().ifPresent(username ->
                                store.findContactByJid(newList.userJid())
                                        .ifPresent(contact -> contact.setUsername(username)));

                        // WAWebBizCoexHostedAddVerification.addToCoexHostedVerificationCache: add hosted
                        // users to verification cache for message validation
                        if (newList.advAccountType() == ADVEncryptionType.HOSTED) {
                            store.addToCoexHostedVerificationCache(newList.userJid());
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Added {0} to coex hosted verification cache", newList.userJid());
                        }

                        // WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey: save
                        // accountSignatureKey as identity key during device sync
                        full.accountSignatureKey()
                                .filter(key -> key.length > 0)
                                .ifPresent(accountSignatureKey -> {
                                    // WAWebBizCoexGatingUtils: hosted devices with override use special handling
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
                                        // WAWebSignalProtocolStore.saveIdentity: save identity key directly
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

                        // WAWebHandleAdvKeyIndexResultApi: detect rawId change (l.rawId !== f)
                        // triggers handleListReset path with clearRecord=true
                        var needsListReset = false;
                        if (cachedList.isPresent() && requiresListReset(cachedList.get(), newList.rawId())) {
                            needsListReset = true;
                            handleListReset(newList.userJid(), cachedList.get(),
                                    cachedList.get().rawId(), newList.rawId());
                        }

                        // WAWebBizCoexUtils: detect account type transitions (E2EE ↔ HOSTED)
                        if (cachedList.isPresent() && newList.hasAccountTypeChanged(cachedList.get())) {
                            var oldType = cachedList.get().advAccountType();
                            var newType = newList.advAccountType();
                            handleAccountTypeTransition(newList.userJid(), oldType, newType, cachedList.get());
                            needsListReset = true; // Account type change also requires list reset
                        }

                        // WAWebHandleAdvNoListResetApi.handleNoListReset: incremental update path
                        if (!needsListReset && cachedList.isPresent() && !cachedList.get().deleted()) {
                            handleNoListReset(newList.userJid(), cachedList.get(), newList);
                        }

                        // WAWebAdvExpectedTsApi: track expectedTimestamp changes and update tracking fields
                        Instant newExpectedTsUpdateTs = null;
                        Instant newExpectedTsLastDeviceJobTs = null;
                        var finalExpectedTs = newList.expectedTimestamp();

                        // WAWebHandleAdvListResetApi.handleListReset: calculate timestamp for device record
                        // List reset: use cached timestamp or pastUnixTime((expirationDays-1) * DAY_SECONDS)
                        // Non-reset: use current time
                        Instant newTimestamp;
                        if (needsListReset) {
                            if (cachedList.isPresent()) {
                                // WAWebHandleAdvListResetApi: use cached list's timestamp (l?.timestamp)
                                newTimestamp = cachedList.get().timestamp();
                            } else {
                                // WAWebHandleAdvListResetApi: pastUnixTime((expirationDays-1) * DAY_SECONDS)
                                var expirationDays = abPropsService.getInt(ABProp.NUM_DAYS_KEY_INDEX_LIST_EXPIRATION);
                                var pastSeconds = (expirationDays - 1) * 24 * 60 * 60L;
                                newTimestamp = Instant.now().minusSeconds(pastSeconds);
                            }
                        } else {
                            newTimestamp = Instant.now();
                        }
                        // WAWebAdvExpectedTsApi.shouldClearExpectedTs: check if should clear expectedTs
                        if (DeviceExpectedTsUtils.shouldClearExpectedTimestamp(newTimestamp, finalExpectedTs, cachedList.orElse(null), lastADVCheckTime)) {
                            finalExpectedTs = null;
                            newExpectedTsUpdateTs = null;
                            newExpectedTsLastDeviceJobTs = null;
                        } else if (cachedList.isPresent()) {
                            // WAWebAdvExpectedTsApi.computeNewExpectedTs: check if expectedTimestamp changed
                            var oldExpectedTs = cachedList.get().expectedTimestamp();
                            if (DeviceExpectedTsUtils.hasExpectedTimestampChanged(oldExpectedTs, finalExpectedTs)) {
                                // WAWebAdvExpectedTsApi: expectedTs changed - update tracking fields
                                newExpectedTsUpdateTs = Instant.now();
                                newExpectedTsLastDeviceJobTs = lastADVCheckTime;
                            } else {
                                // WAWebAdvExpectedTsApi: expectedTs unchanged - preserve existing tracking
                                newExpectedTsUpdateTs = cachedList.get().expectedTimestampUpdateTimestamp();
                                newExpectedTsLastDeviceJobTs = cachedList.get().expectedTimestampLastDeviceJobTimestamp();
                            }
                        }

                        // WAWebHandleAdvKeyIndexResultApi: create device list with tracked expectedTs fields
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

                        // WAWebIdentityUpdateDeviceTableApi: detect identity changes and cleanup sessions
                        if (cachedList.isPresent()) {
                            var changes = trackedList.mismatch(cachedList.get());
                            if (!changes.identityChangedDevices().isEmpty()) {
                                // WAWebIdentityUpdateDeviceTableApi: mark devices and cleanup Signal sessions
                                for (var changedDevice : changes.identityChangedDevices()) {
                                    store.markIdentityChange(changedDevice);
                                    store.cleanupSignalSessions(changedDevice);
                                }

                                // WAWebClientListener: notify listeners about identity changes
                                for (var listener : client.store().listeners()) {
                                    Thread.startVirtualThread(() ->
                                            listener.onDeviceIdentityChanged(client, trackedList.userJid(), changes.identityChangedDevices())
                                    );
                                }
                            }

                            // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: cleanup removed device sessions
                            if (!changes.removedDevices().isEmpty()) {
                                for (var removedDevice : changes.removedDevices()) {
                                    store.cleanupSignalSessions(removedDevice);
                                }
                                // WAWebSyncdStoreMissingKeys.updateMissingKeyDevices: track own device removals
                                if (trackedList.userJid().equals(myJid)) {
                                    ownDevicesRemoved = true;
                                }
                            }

                            // WAWebAdvUpdateParticipantApi.updateGroupParticipantsInTransaction:
                            // Mark user as needing sender key rotation if devices were added or removed
                            // This ensures group sender keys are properly updated
                            if (!changes.addedDevices().isEmpty() || !changes.removedDevices().isEmpty()) {
                                store.markKeyRotation(trackedList.userJid());
                            }
                        }

                        // WAWebAdvSyncDeviceListApi.sendDeviceSyncRequest (R function): track users with
                        // validated key index info for WAWebGetIdentityKeysJob.getAndStoreIdentityKeys
                        if (!trackedList.validIndexes().isEmpty() || trackedList.currentIndex() > 0) {
                            usersWithValidatedKeyIndex.add(trackedList.userJid());
                        }

                        yield trackedList;
                    }

                    case DeviceListResult.Omitted omitted -> {
                        // WAWebHandleAdvOmittedResultApi.handleOmittedResult: server confirmed dhash matches,
                        // preserve rawId/validIndexes but reset devices to primary only
                        var cachedList = omitted.userJid()
                                .flatMap(store::findDeviceList);
                        if (cachedList.isEmpty()) {
                            // WAWebHandleAdvOmittedResultApi: no cached list (!t || t.deleted) => return null
                            yield null;
                        }

                        var oldList = cachedList.get();
                        var newTimestamp = omitted.timestamp().orElse(oldList.timestamp());
                        var newExpectedTs = omitted.expectedTimestamp().orElse(null);

                        // WAWebAdvExpectedTsApi: track expectedTimestamp changes
                        var finalExpectedTs = newExpectedTs;
                        var newExpectedTsUpdateTs = oldList.expectedTimestampUpdateTimestamp();
                        var newExpectedTsLastDeviceJobTs = oldList.expectedTimestampLastDeviceJobTimestamp();

                        // WAWebAdvExpectedTsApi.shouldClearExpectedTs: check staleness
                        if (DeviceExpectedTsUtils.shouldClearExpectedTimestamp(newTimestamp, finalExpectedTs, oldList, lastADVCheckTime)) {
                            finalExpectedTs = null;
                            newExpectedTsUpdateTs = null;
                            newExpectedTsLastDeviceJobTs = null;
                        } else {
                            // WAWebAdvExpectedTsApi.computeNewExpectedTs: check if changed
                            var oldExpectedTs = oldList.expectedTimestamp();
                            if (DeviceExpectedTsUtils.hasExpectedTimestampChanged(oldExpectedTs, newExpectedTs)) {
                                // WAWebAdvExpectedTsApi: expectedTs changed - update tracking fields
                                newExpectedTsUpdateTs = Instant.now();
                                newExpectedTsLastDeviceJobTs = lastADVCheckTime;
                            }
                        }

                        // WAWebHandleAdvOmittedResultApi.handleOmittedResult: reset devices to PRIMARY ONLY
                        // a.devices=[{id:o("WAJids").DEFAULT_DEVICE_ID,keyIndex:0}]
                        var resetDevices = List.of(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0));

                        // WAWebHandleAdvOmittedResultApi: reset HOSTED account type to E2EE
                        // t.advAccountType===HOSTED && (a.advAccountType=E2EE)
                        var resetAdvAccountType = oldList.advAccountType() == ADVEncryptionType.HOSTED
                                ? ADVEncryptionType.E2EE
                                : oldList.advAccountType();

                        // WAWebAdvHandlerApi.handleADVDeviceSyncResult: when fromHandleOmittedResult=true
                        // and HOSTED→E2EE transition, trigger account type change handling (m=true)
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

                        // WAWebHandleAdvOmittedResultApi: create updated device list with reset devices
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

                // WAWebIdentityUpdateDeviceTableApi.bulkApplyDeviceUpdate: store and complete future
                if (deviceList != null) {
                    store.addDeviceList(deviceList);
                    var future = futures.get(deviceList.userJid());
                    if (future != null) {
                        future.complete(deviceList);
                        result.add(deviceList);
                    }
                }
            }

            // WAWebDBDeviceListFanout.getFanOutList: fallback to primary device for unfound JIDs
            for (var entry : futures.entrySet()) {
                if (!entry.getValue().isDone()) {
                    var fallback = createPrimaryOnlyDeviceList(entry.getKey());
                    store.addDeviceList(fallback);
                    entry.getValue().complete(fallback);
                    result.add(fallback);
                    LOGGER.log(System.Logger.Level.DEBUG, "Device list not found for {0}, falling back to primary device", entry.getKey());
                }
            }

            // WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: prefetch identity keys for users
            // with validated key index info (signedKeyIndexBytes passed signature verification)
            if (!usersWithValidatedKeyIndex.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Device sync completed for {0} users with validated key index info",
                        usersWithValidatedKeyIndex.size());

                try {
                    // WAWebGetIdentityKeysJob: run prefetch asynchronously to not block sync
                    var usersToPrefetch = List.copyOf(usersWithValidatedKeyIndex);
                    Thread.startVirtualThread(() -> {
                        try {
                            preKeyHandler.fetchAndStoreIdentityKeys(usersToPrefetch);
                            LOGGER.log(System.Logger.Level.DEBUG,
                                    "Identity key prefetch completed for {0} users",
                                    usersToPrefetch.size());
                        } catch (Exception e) {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "Identity key prefetch failed: {0}", e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Failed to start identity key prefetch: {0}", e.getMessage());
                }
            }

            // WAWebSyncdStoreMissingKeys.updateMissingKeyDevices: update missing key tracking
            // when own companion devices are removed
            if (ownDevicesRemoved) {
                updateMissingKeyDevices();
            }

            return result;
        } catch (Exception e) {
            // WAWebApiPendingDeviceSync.addUserToPendingDeviceSync: save for retry on reconnect
            var pending = PendingDeviceSync.of(toFetch, context);
            store.addPendingDeviceSync(pending);

            // WAWebAdvSyncDeviceListApi: complete all futures exceptionally
            for (var future : futures.values()) {
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            }

            throw new RuntimeException("Failed to fetch device lists", e);
        } finally {
            // WAWebAdvSyncDeviceListApi: _.delete(createDeviceListPK(e)) - clean up pending requests
            for (var jid : toFetch) {
                pendingFetches.remove(jid);
            }
        }
    }

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
     * Handles an account type transition (E2EE ↔ HOSTED).
     *
     * <p>When a user's account type changes between E2EE and HOSTED, several cleanup
     * and notification actions are required
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous account type
     * @param newType the new account type
     * @param oldList the cached device list before the transition
     *
     * @apiNote WAWebBizCoexHostedAddVerification: requires users transitioning to HOSTED to
     * be in the verification cache (populated during USync). WAWebIdentityUpdateDeviceTableApi:
     * clears Signal sessions when account type changes. WAWebBizCoexUtils: creates system messages.
     */
    private void handleAccountTypeTransition(Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType, DeviceList oldList) {
        LOGGER.log(System.Logger.Level.INFO, "Account type changed for {0}: {1} -> {2}", userJid, oldType, newType);

        // WAWebBizCoexHostedAddVerification: verify hosted users are in cache
        // This prevents accepting transitions from potentially spoofed hosted device updates
        if (newType == ADVEncryptionType.HOSTED) {
            store.assertCoexHostedVerification(userJid);
        }

        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: cleanup all Signal sessions
        cleanupAllSessionsForUser(userJid, oldList);

        // WAWebBizCoexUtils: if transitioning to HOSTED, mark device list as deleted-changed-to-host
        if (newType == ADVEncryptionType.HOSTED) {
            store.addDeviceList(createDeletedDeviceList(userJid, true));
        }

        // WAWebApiContact: update contact's encryption type metadata
        store.findContactByJid(userJid).ifPresent(contact -> contact.setEncryptionType(newType));

        // Notify listeners about account type change
        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() ->
                    listener.onAccountTypeChanged(client, userJid, oldType, newType)
            );
        }

        // WAWebBizCoexUtils: create system message in chat
        createAccountTypeChangeSystemMessage(userJid, newType);
    }

    /**
     * Creates a system message in the chat when a contact's account type changes.
     *
     * <p>Creates a stub message indicating whether messages with this contact are now
     * end-to-end encrypted (E2E_ENCRYPTED_NOW) or no longer encrypted (CIPHERTEXT).
     *
     * @param userJid the user JID whose account type changed
     * @param newType the new account type
     *
     * @apiNote WAWebBizCoexUtils: creates system messages for account type transitions.
     * Uses shouldDedupInitialHostedSystemMsg to prevent duplicate messages within the same second.
     */
    private void createAccountTypeChangeSystemMessage(Jid userJid, ADVEncryptionType newType) {
        var chat = store.findChatByJid(userJid).orElse(null);
        if (chat == null) {
            return;
        }

        // WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg: check if should deduplicate
        if (newType == ADVEncryptionType.HOSTED && shouldDedupInitialHostedSystemMsg(userJid)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Skipping duplicate hosted system message for {0}", userJid);
            return;
        }

        // WAWebBizCoexUtils: E2EE→HOSTED = no longer E2E encrypted (CIPHERTEXT)
        // HOSTED→E2EE = now E2E encrypted (E2E_ENCRYPTED_NOW)
        var stubType = (newType == ADVEncryptionType.E2EE)
                ? MessageInfoStubType.E2E_ENCRYPTED_NOW
                : MessageInfoStubType.CIPHERTEXT;

        var key = new MessageKeyBuilder()
                .id(MessageKey.randomId(store.clientType()))
                .chatJid(chat.jid())
                .senderJid(userJid)

                .build();
        var message = new ChatMessageInfoBuilder()
                .status(MessageStatus.DELIVERED)
                .timestampSeconds(Instant.now().getEpochSecond())
                .key(key)
                .ignore(true)
                .stubType(stubType)
                .senderJid(userJid)
                .build();
        chat.addMessage(message);

        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewMessage(client, message));
        }
    }

    /**
     * Checks if an initial hosted system message should be deduplicated.
     *
     * @param userJid the user JID
     * @return {@code true} if the message should be skipped (duplicate)
     *
     * @apiNote WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg: returns true if a
     * system message was already created for this user within the same second.
     */
    private boolean shouldDedupInitialHostedSystemMsg(Jid userJid) {
        var currentSecond = Instant.now();
        var lastMsgSecond = hostedSystemMsgDedupCache.get(userJid);

        if (lastMsgSecond != null && lastMsgSecond.equals(currentSecond)) {
            // Message was already created this second - deduplicate
            return true;
        }

        // Update the cache with current timestamp
        hostedSystemMsgDedupCache.put(userJid, currentSecond);
        return false;
    }

    /**
     * Cleans up all Signal sessions and sender keys for all devices of a user.
     *
     * @param userJid the user JID
     * @param oldList the device list containing devices to clean up
     *
     * @apiNote WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clears Signal sessions
     * for all non-primary devices when cleaning up a device record.
     */
    private void cleanupAllSessionsForUser(Jid userJid, DeviceList oldList) {
        for (var device : oldList.devices()) {
            var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
            store.cleanupSignalSessions(deviceJid);
        }
    }

    /**
     * Determines if a full list reset is required based on rawId change.
     *
     * <p>The rawId is a unique identifier for a user's device configuration. When it
     * changes, it indicates the user has re-registered or reset their account, requiring
     * all existing Signal sessions to be invalidated.
     *
     * @param cachedList the cached device list
     * @param newRawId   the new rawId from the server
     * @return {@code true} if a full list reset is required
     *
     * @apiNote WAWebHandleAdvListResetApi.handleListReset: when rawId changes (l.rawId!==f),
     * all Signal sessions must be cleared and the device list rebuilt from scratch.
     * This is the "clearRecord" path in WAWebHandleAdvKeyIndexResultApi.
     */
    private boolean requiresListReset(DeviceList cachedList, String newRawId) {
        if (cachedList == null || cachedList.deleted()) {
            return false;
        }
        var oldRawId = cachedList.rawId();
        // WAWebHandleAdvKeyIndexResultApi: l.rawId!==f ? y=!0 : C=l.devices
        return oldRawId != null && newRawId != null && !oldRawId.equals(newRawId);
    }

    /**
     * Handles a full device list reset when rawId changes.
     *
     * <p>Clears all existing Signal sessions for the user's devices. The device list
     * will be rebuilt from scratch with the new rawId.
     *
     * @param userJid    the user JID
     * @param cachedList the cached device list
     * @param oldRawId   the old rawId
     * @param newRawId   the new rawId
     *
     * @apiNote WAWebHandleAdvListResetApi.handleListReset: clears all existing Signal
     * sessions for the user when rawId changes. Called via clearDeviceRecord path.
     */
    private void handleListReset(Jid userJid, DeviceList cachedList, String oldRawId, String newRawId) {
        LOGGER.log(System.Logger.Level.INFO,
                "Device list rawId changed for {0}: {1} -> {2}, triggering full reset (handleListReset)",
                userJid, oldRawId, newRawId);
        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: cleanup all Signal sessions
        cleanupAllSessionsForUser(userJid, cachedList);
    }

    /**
     * Handles an incremental device list update when rawId is unchanged.
     *
     * <p>Validates incoming devices against the cached validIndexes to detect out-of-order
     * timestamp attacks.
     *
     * @param userJid    the user JID
     * @param cachedList the cached device list
     * @param newList    the new device list from server
     * @return the merged device list
     * @throws IllegalStateException if out-of-order timestamp detected with unknown keyIndex
     *
     * @apiNote WAWebHandleAdvNoListResetApi.handleNoListReset: validates incoming keyIndex
     * against cached validIndexes. Throws error if timestamp is not newer but keyIndex is
     * not in validIndexes (indicates replay attack or corrupted state).
     */
    private DeviceList handleNoListReset(Jid userJid, DeviceList cachedList, DeviceList newList) {
        // WAWebHandleAdvNoListResetApi.handleNoListReset: detect out-of-order timestamps
        // if(i.timestamp>=p && i.validIndexes && !i.validIndexes.includes(m))
        //     throw err("handleNoListReset: out-of-order timestamp detected")
        var cachedValidIndexes = cachedList.validIndexes();
        if (!cachedValidIndexes.isEmpty() && !cachedList.timestamp().isAfter(newList.timestamp())) {
            var cachedCurrentIndex = cachedList.currentIndex();
            for (var device : newList.devices()) {
                var keyIndex = device.keyIndex();
                // WAWebHandleAdvNoListResetApi: valid if keyIndex == 0 OR in validIndexes OR > currentIndex
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

        // WAWebHandleAdvNoListResetApi: for USync device sync, server sends authoritative list
        // Merge logic in WAWeb is for message-based ADV updates; here we detect changes for
        // session cleanup in WAWebIdentityUpdateDeviceTableApi.bulkApplyDeviceUpdate

        var changes = newList.mismatch(cachedList);

        if (!changes.addedDevices().isEmpty() || !changes.removedDevices().isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device list incrementally updated for {0}: +{1} -{2} devices (handleNoListReset)",
                    userJid, changes.addedDevices().size(), changes.removedDevices().size());
        }

        return newList;
    }

    /**
     * Backfills missing device sync entries when server returns LID-based results but PN was requested.
     *
     * <p>During LID migration, the server may return results keyed by LID even when the request
     * was made using a phone number (PN). This method duplicates the LID result as a PN result
     * so callers get the data they requested.
     *
     * @param requestedJids the JIDs that were originally requested
     * @param results       the results returned from the server
     * @return the results with backfilled entries for missing PNs
     *
     * @apiNote WAWebContactSyncUtils.backfillMissingDeviceSyncEntries: if a PN entry was requested
     * but server only returned the LID entry, duplicate the LID result as if it were the PN entry.
     * Uses getCurrentLid() to find LID mappings and isRegularUserPn() to filter applicable JIDs.
     */
    private List<DeviceListResult> backfillMissingDeviceSyncEntries(
            Set<Jid> requestedJids,
            List<DeviceListResult> results
    ) {
        // WAWebContactSyncUtils.backfillMissingDeviceSyncEntries: build map of returned results
        var resultMap = new HashMap<Jid, DeviceListResult>();
        for (var result : results) {
            if(result instanceof DeviceListResult.Error error) {
                throw new WhatsAppDeviceSyncException(error.errorCode(), error.errorText(), error.fatal());
            }

            result.userJid()
                    .ifPresent(value -> resultMap.put(value, result));
        }

        // WAWebContactSyncUtils.backfillMissingDeviceSyncEntries: check for missing PN entries
        // that have LID counterparts. isRegularUserPn = isUser() && !isPSA() && !isBot() && !isLid()
        var backfilledResults = new ArrayList<>(results);
        for (var requestedJid : requestedJids) {
            // WAWebContactSyncUtils: skip if we already have a result for this JID
            if (resultMap.containsKey(requestedJid)) {
                continue;
            }

            // WAWebContactSyncUtils: only backfill for regular user PN JIDs
            var isRegularUserPn = requestedJid.hasUserServer()
                    && !requestedJid.equals(Jid.announcementsAccount())
                    && !requestedJid.hasBotServer()
                    && !requestedJid.hasLidServer();
            if (!isRegularUserPn) {
                continue;
            }

            // WAWebApiContact.getCurrentLid: try to find LID mapping for this PN
            var lidJid = store.findLidByPhone(requestedJid);
            if (lidJid.isEmpty()) {
                continue;
            }

            // WAWebContactSyncUtils: check if we have a result for the LID
            var lidResult = resultMap.get(lidJid.get());
            if (lidResult == null) {
                continue;
            }

            // WAWebContactSyncUtils.backfillMissingDeviceSyncEntries: duplicate LID result as PN
            LOGGER.log(System.Logger.Level.DEBUG, "Backfilling device list for {0} from LID {1}",
                    requestedJid, lidJid.get());

            var backfilledResult = switch (lidResult) {
                case DeviceListResult.Full full -> {
                    // WAWebContactSyncUtils: create new DeviceList with PN JID
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
     * Gets the last ADV check time from WhatsAppStore.
     *
     * @return the last check time, or empty if never checked
     *
     * @apiNote WAWebLastADVCheckTimeApi.getLastADVDeviceInfoCheckTime: returns the timestamp
     * of the last scheduled ADV device info check job.
     */
    public Optional<Instant> lastAdvCheckTime() {
        return store.lastAdvCheckTime();
    }

    /**
     * Updates the ADV check time in WhatsAppStore to the current time.
     *
     * @apiNote WAWebLastADVCheckTimeApi: stores the timestamp when the ADV device info
     * check job runs, used for expected timestamp staleness calculations.
     */
    public void updateAdvCheckTime() {
        store.updateAdvCheckTime();
    }

    /**
     * Creates a device list containing only the primary device (device ID 0).
     *
     * <p>Used as a fallback when no device information is available from the server.
     *
     * @param userJid the user JID
     * @return a device list with only the primary device
     *
     * @apiNote WAWebDBDeviceListFanout.getFanOutList: when no device record is found,
     * sends to primary device only (l.toString()).
     */
    private DeviceList createPrimaryOnlyDeviceList(Jid userJid) {
        var now = Instant.now();
        return new DeviceListBuilder()
                .userJid(userJid)
                .devices(List.of(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0)))
                .timestamp(now)
                .build();
    }

    /**
     * Creates a deleted device list marker.
     *
     * <p>Used to mark a device list as deleted in the cache, optionally indicating
     * that the deletion was due to a transition to hosted account type.
     *
     * @param userJid       the user JID
     * @param changedToHost whether the deletion is due to a HOSTED transition
     * @return a deleted device list marker
     *
     * @apiNote WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: marks device list
     * as deleted=true. WAWebBizCoexUtils: sets deletedChangedToHost for HOSTED transitions.
     */
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
     * Starts the ADV device info check scheduler.
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: schedules periodic
     * check that runs every 24 hours to verify device list freshness.
     */
    public void startAdvCheckScheduler() {
        advCheckScheduler.start();
    }

    /**
     * Stops the ADV device info check scheduler.
     *
     * <p>Should be called before client disconnects to prevent background tasks
     * from running on a disconnected client.
     */
    public void stopAdvCheckScheduler() {
        advCheckScheduler.close();
    }

    /**
     * Retries all pending device syncs.
     *
     * <p>Should be called after client reconnects to complete any device syncs
     * that failed due to connection issues. Respects retry limits and expiration.
     *
     * @apiNote WAWebApiPendingDeviceSync.doPendingDeviceSync: called during
     * RESUME_WITH_OPEN_TAB to retry pending device syncs.
     */
    public void retryPendingSyncs() {
        var pending = store.pendingDevicesSyncs();
        for (var sync : pending) {
            // Remove expired syncs
            if (sync.isExpired()) {
                store.removePendingDeviceSync(sync);
                continue;
            }

            // Skip syncs that have exceeded max retries
            if (!sync.shouldRetry()) {
                store.removePendingDeviceSync(sync);
                continue;
            }

            // Retry the sync using the original context
            try {
                fetchDeviceListsFromServer(sync.userJids(), sync.context());
                store.removePendingDeviceSync(sync);
            } catch (Exception e) {
                // Increment retry count and re-queue
                var retried = sync.nextRetry();
                store.removePendingDeviceSync(sync);
                if (retried.shouldRetry()) {
                    store.addPendingDeviceSync(retried);
                }
            }
        }
    }

    /**
     * Updates missing key device tracking when own companion devices are removed.
     *
     * <p>When a companion device is removed from the account, any missing sync key
     * entries that were waiting for that device need to be updated. If a key is
     * missing on all remaining devices, it triggers a fatal sync error.
     *
     * @apiNote WAWebSyncdStoreMissingKeys.updateMissingKeyDevices: when companion devices
     * are removed, update missing sync key entries to remove the device from tracking.
     * If all devices report missing, triggers SyncdFatalErrorType.MISSING_KEY_ON_ALL_CLIENTS.
     */
    public void updateMissingKeyDevices() {
        // WAWebApiGetDeviceUpdateLock: serialize device and missing-key updates
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

                // Per WhatsApp Web: when all devices have responded without the key,
                // don't trigger fatal immediately. The timeout scheduler handles the
                // 5-second grace period to allow for late-arriving responses.
                if (missingKey.isMissingOnAllDevices()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "All devices responded without missing sync key, waiting for grace period");
                }
            }
        });
    }

    /**
     * Gets the complete fanout for a group message.
     *
     * @param groupJid    the group JID
     * @param myDeviceJid the current device's JID (to exclude from fanout)
     * @return the fanout result containing device list and phash
     *
     * @apiNote WAWebDBDeviceListFanout.getFanOutList: generates device lists for message fanout.
     * WAWebPhashUtils.phashV2: calculates the participant hash for group messages.
     */
    /**
     * Computes the fanout device list for a 1:1 user chat.
     *
     * <p>This resolves device lists for both the recipient and the sender,
     * computes the fanout (filtering self and applying hosted device logic),
     * and filters out devices with unconfirmed identity changes.
     *
     * <p>If {@code expectedPhash} is non-null, the device list sync uses
     * it for a phash pre-check optimisation, skipping the server round-trip
     * when the local device list already matches.
     *
     * @param chatJid       the recipient user JID
     * @param expectedPhash the server-provided phash to match against,
     *                      or {@code null} for an unconditional sync
     * @return the fanout device JIDs
     *
     * @apiNote WAWebSendUserMsgJob.encryptAndSendUserMsg: calls
     * getFanOutList({wids: [to, meDevice],
     * chatWidSetToIncludeHostedInFanoutOneToOneChatOnly: to}).
     * WAWebDBDeviceListFanout.getFanOutList: filters self, includes hosted
     * for 1:1 user chats when bizHostedDevicesEnabled.
     */
    public Collection<Jid> getUserFanout(Jid chatJid, String expectedPhash) {
        var myDeviceJid = resolveMyDeviceJid(chatJid);
        var deviceLists = getDeviceLists(
                List.of(chatJid, myDeviceJid), "message", expectedPhash, false);

        // WAWebDBDeviceListFanout.getFanOutList: include hosted devices for 1:1 user chats
        var fanoutDevices = fanoutCalculator.calculate(myDeviceJid, deviceLists, chatJid);

        // Filter out devices with unconfirmed identity changes
        var changedIdentities = store.unconfirmedIdentityChanges();
        return fanoutCalculator.filterIdentityChanges(fanoutDevices, changedIdentities);
    }

    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid) {
        var myDeviceJid = resolveMyDeviceJid(groupJid);
        try {
            // WAWebDBDeviceListFanout.getFanOutList: get devices for all participants
            var metadata = client.queryChatMetadata(groupJid);
            var participants = metadata.participants()
                    .stream()
                    .map(GroupParticipant::userJid)
                    .toList();
            var deviceLists = getDeviceLists(participants, "message", null, false);

            // WAWebDBDeviceListFanout.getFanOutList: group messages don't include hosted devices
            var fanoutDevices = fanoutCalculator.calculate(myDeviceJid, deviceLists, null);

            // Filter out devices with unconfirmed identity changes
            var changedIdentities = store.unconfirmedIdentityChanges();
            var filteredDevices = fanoutCalculator.filterIdentityChanges(fanoutDevices, changedIdentities);

            // WAWebPhashUtils.phashV2: calculate phash for message integrity verification
            // allowIncludeMetaBot=true lets the service check AB props for group fanout
            var phash = phashCalculator.calculate(filteredDevices, DevicePhashVersion.V2, true);
            return new DeviceGroupFanoutResult(filteredDevices, phash);
        } catch (NoSuchAlgorithmException exception) {
            throw new InternalError("Missing SHA-256 implementation", exception);
        }
    }

    /**
     * Computes ICDC metadata for the given user.
     *
     * @param userJid the user JID (will be normalised to a user-level JID)
     * @return the ICDC result, or {@code Optional.empty()} if no device list is cached
     *         or the list is marked as deleted
     *
     * @apiNote WAWebIdentityIcdcApi.getICDCMeta: retrieves the device record
     * and delegates to {@code getICDCMetaFromDeviceRecord}.
     */
    public Optional<IcdcResult> computeIcdc(Jid userJid) {
        return icdcComputer.compute(userJid);
    }

    /**
     * Ensures Signal sessions exist for all specified devices, fetching
     * prekey bundles from the server for any devices without sessions.
     *
     * <p>This must be called before encrypting messages for a device
     * list.  Devices that already have established sessions are skipped.
     *
     * @param deviceJids the device JIDs to ensure sessions for
     *
     * @apiNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza:
     * calls {@code ensureE2ESessions(devices)} before encrypting.
     * WAWebE2ESessionService: deduplicates concurrent session
     * establishment requests.
     */
    public void ensureSessions(Collection<Jid> deviceJids) {
        preKeyHandler.ensureSessions(deviceJids);
    }

    /**
     * Resolves the sender's device JID based on the chat's addressing.
     *
     * <p>For LID-addressed chats, uses the sender's LID if available;
     * otherwise falls back to the PN device JID.
     *
     * @param chatJid the chat JID that determines addressing
     * @return the sender's device JID
     * @throws IllegalStateException if not logged in
     *
     * @apiNote WAWebSendUserMsgJob: uses getMeDeviceLid for LID chats,
     * getMeDevicePn otherwise.
     * WAWebSendGroupSkmsgJob: uses getMeDeviceLid for LID groups,
     * getMeDevicePn otherwise.
     */
    private Jid resolveMyDeviceJid(Jid chatJid) {
        var selfJid = store.jid().orElseThrow(() ->
                new IllegalStateException("Not logged in"));
        if (chatJid.hasLidServer()) {
            return store.lid().orElse(selfJid);
        }
        return selfJid;
    }

    /**
     * Checks if business hosted devices feature is enabled.
     *
     * @return {@code true} if hosted devices are enabled
     *
     * @apiNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: returns
     * getABPropConfigValue("adv_accept_hosted_devices"), defaulting to false.
     */
    private boolean isBizHostedDevicesEnabled() {
        // WAWebBizCoexGatingUtils: defaults to false when AB prop is not set
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Filters out hosted devices from device list results.
     *
     * <p>When bizHostedDevicesEnabled is false, hosted devices (device ID 99 or
     * is_hosted=true) should be removed from device lists to prevent sending
     * messages to business coex hosted devices.
     *
     * @param results the original results
     * @return filtered results with hosted devices removed from Full results
     *
     * @apiNote WAWebAdvHandlerApi.handleADVDeviceSyncResult: when bizHostedDevicesEnabled
     * is false, filters out devices where id === HOSTED_DEVICE_ID (99).
     */
    private static List<DeviceListResult> filterHostedDevicesFromResults(List<DeviceListResult> results) {
        return results.stream()
                .map(DeviceListResult::withoutHostedDevices)
                .toList();
    }

    /**
     * Handles a device notification (add or remove).
     *
     * <p>Device notifications are push updates from the server when a user adds or removes
     * companion devices. These allow updating the local device cache without polling.
     *
     * @param node    the notification node containing device-list and key-index-list
     * @param action  the action type ("add" or "remove")
     * @param userJid the user JID whose device list changed
     *
     * @apiNote WAWebHandleAdvDeviceNotificationApi.handleDeviceNotification: dispatches to
     * handleDeviceAddNotification or handleDeviceRemoveNotification based on action type.
     * Uses device update lock to serialize table updates.
     */
    public void handleDeviceNotification(Node node, String action, Jid userJid) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(userJid, "userJid cannot be null");

        // WAWebHandleAdvDeviceNotificationApi: parse device list and key index list from notification
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

        // WAWebApiGetDeviceUpdateLock: serialize device table updates
        // to prevent race conditions when updating multiple tables (device-list, session, sender-key, etc.)
        withDeviceUpdateLock(() -> {
            switch (action) {
                case "add" -> handleDeviceAddNotification(userJid, deviceListNode, keyIndexListNode.get(), timestamp);
                case "remove" -> handleDeviceRemoveNotification(userJid, keyIndexListNode.get(), timestamp);
                default -> LOGGER.log(System.Logger.Level.WARNING, "Unknown device action: {0}", action);
            }
        });
    }

    /**
     * Handles a device add notification.
     *
     *
     * @param userJid          the user JID
     * @param deviceListNode   the device-list node from notification
     * @param keyIndexListNode the key-index-list node from notification
     * @param timestamp        the notification timestamp (Unix seconds)
     *
     * @apiNote WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification: if no cached
     * record exists or it's deleted, triggers USync via triggerUsyncForCoexDeviceAdd.
     * Otherwise validates and merges devices following WAWebHandleAdvKeyIndexResultApi logic.
     */
    private void handleDeviceAddNotification(
            Jid userJid,
            Node deviceListNode,
            Node keyIndexListNode,
            long timestamp
    ) {
        // WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification: get cached list first
        var cachedList = store.findDeviceList(userJid);

        // WAWebHandleAdvDeviceNotificationApi: if (!l || l.deleted) trigger USync instead
        if (cachedList.isEmpty() || cachedList.get().deleted()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device add notification for {0} without cached record, queueing for USync", userJid);

            // WAWebBizCoexUtils.triggerUsyncForCoexDeviceAdd: for hosted devices,
            // trigger immediate USync to get complete device list
            triggerUsyncForCoexDeviceAdd(deviceListNode, userJid);

            return;
        }

        // WAWebHandleAdvDeviceNotificationApi: validate signed key index bytes (i == null) check
        var signedKeyIndexBytes = keyIndexListNode.toContentBytes();
        if (signedKeyIndexBytes.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Device add notification missing signedKeyIndexBytes for {0}", userJid);
            // WAWebSyncdMetricFatalError: warn when trying to delete own device list
            var myJid = store.jid().map(Jid::toUserJid).orElse(null);
            if (myJid != null && myJid.equals(userJid.toUserJid())) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "syncd: trying to delete own device list for {0}", userJid);
            }
            store.removeDeviceList(userJid);
            return;
        }

        // WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification: reject if r < l.timestamp
        if (timestamp < cachedList.get().timestamp().getEpochSecond()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device add notification timestamp {0} < cached {1} for {2}, ignoring",
                    timestamp, cachedList.get().timestamp().getEpochSecond(), userJid);
            return;
        }

        // WAWebHandleAdvDeviceNotificationApi: parse device list
        if (deviceListNode == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Device add notification missing device-list for {0}", userJid);
            return;
        }

        // WAWebHandleAdvDeviceNotificationApi: build key index map from notification
        var keyIndexMap = buildKeyIndexMap(keyIndexListNode);

        // WAWebHandleAdvDeviceNotificationUtils.decodeSignedKeyIndexBytes: validate key index list
        // from protobuf; stored account signature key used as fallback when embedded key missing
        var validatedKeyIndexInfo = validateKeyIndexList(keyIndexListNode);

        // WAWebHandleAdvDeviceNotificationApi: reject if protobuf timestamp != notification timestamp
        // (m !== r) => return null
        if (validatedKeyIndexInfo != null && validatedKeyIndexInfo.timestamp().getEpochSecond() != timestamp) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Device add notification timestamp mismatch for {0}: protobuf={1}, notification={2}, ignoring",
                    userJid, validatedKeyIndexInfo.timestamp().getEpochSecond(), timestamp);
            return;
        }

        // WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification: parse devices with validation
        var devices = deviceListNode.streamChildren("device")
                .flatMap(deviceNode -> parseAndValidateAddedDevice(deviceNode, keyIndexMap, validatedKeyIndexInfo))
                .toList();

        // WAWebUsyncDevice.deviceParser: parse additional metadata from key-index-list
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

        // WAWebBizCoexHostedAddVerification.addToCoexHostedVerificationCache: add hosted users
        if (advAccountType == ADVEncryptionType.HOSTED) {
            store.addToCoexHostedVerificationCache(userJid);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Added {0} to coex hosted verification cache via notification", userJid);
        }

        var currentIndex = validatedKeyIndexInfo != null ? validatedKeyIndexInfo.currentIndex() : 0;
        var validIndexesSet = validatedKeyIndexInfo != null && validatedKeyIndexInfo.validIndexes() != null
                ? validatedKeyIndexInfo.validIndexes()
                : new LinkedHashSet<Integer>();

        // WAWebHandleAdvKeyIndexResultApi: detect rawId change (l.rawId !== f)
        var oldCachedList = cachedList.get();
        var clearRecord = false;
        var oldRawId = oldCachedList.rawId();
        if (oldRawId != null && !oldRawId.equals(rawId)) {
            LOGGER.log(System.Logger.Level.INFO, "Device list rawId changed via notification for {0}: {1} -> {2}, triggering full reset",
                    userJid, oldRawId, rawId);
            clearRecord = true;
            // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clear all Signal sessions
            for (var device : oldCachedList.devices()) {
                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
                store.cleanupSignalSessions(deviceJid);
            }
        }

        // WAWebBizCoexUtils: detect account type transitions (E2EE ↔ HOSTED)
        if (advAccountType != null && oldCachedList.advAccountType() != null
                && advAccountType != oldCachedList.advAccountType()) {
            LOGGER.log(System.Logger.Level.INFO, "Account type changed via notification for {0}: {1} -> {2}",
                    userJid, oldCachedList.advAccountType(), advAccountType);
            clearRecord = true;
            handleAccountTypeTransition(userJid, oldCachedList.advAccountType(), advAccountType, oldCachedList);
        }

        // WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification: merge cached with new
        // 1. Keep cached devices with valid keyIndex (in validIndexes OR > currentIndex)
        // 2. Add new devices from notification with valid keyIndex
        // 3. Always include primary device
        var mergedDevices = new LinkedHashMap<Integer, DeviceInfo>();

        // WAWebHandleAdvDeviceNotificationApi: add cached devices that are still valid
        if (!clearRecord) {
            for (var cachedDevice : oldCachedList.devices()) {
                if (cachedDevice.isPrimary()) {
                    continue;
                }
                var keyIdx = cachedDevice.keyIndex();
                // WAWebHandleAdvDeviceNotificationApi: keep if h.has(e.keyIndex) || e.keyIndex > y
                if (validIndexesSet.contains(keyIdx) || keyIdx > currentIndex) {
                    mergedDevices.put(cachedDevice.id(), cachedDevice);
                }
            }
        }

        // WAWebHandleAdvDeviceNotificationApi: n.forEach - add/update with new devices
        for (var newDevice : devices) {
            if (newDevice.isPrimary()) {
                continue;
            }
            mergedDevices.put(newDevice.id(), newDevice);
        }

        // WAWebHandleAdvDeviceNotificationApi: C.push({id: DEFAULT_DEVICE_ID, keyIndex: 0})
        mergedDevices.put(DeviceConstants.PRIMARY_DEVICE_ID, DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0));

        var finalDevices = new ArrayList<>(mergedDevices.values());

        // WAWebHandleAdvDeviceNotificationApi: create device list preserving cached timestamp (g)
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

        // WAWebAdvExpectedTsApi.computeExpectedTsForDeviceRecord: compute and apply expected timestamp
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

        // WAWebIdentityUpdateDeviceTableApi: detect identity changes for notification
        var changes = newDeviceList.mismatch(oldCachedList);
        if (!changes.identityChangedDevices().isEmpty()) {
            // WAWebIdentityUpdateDeviceTableApi: mark devices and cleanup stale Signal sessions
            for (var changedDevice : changes.identityChangedDevices()) {
                store.markIdentityChange(changedDevice);
                store.cleanupSignalSessions(changedDevice);
            }

            // WAWebClientListener: notify listeners about identity changes
            for (var listener : store.listeners()) {
                Thread.startVirtualThread(() ->
                        listener.onDeviceIdentityChanged(client, userJid, changes.identityChangedDevices())
                );
            }
        }

        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: cleanup sessions for removed devices
        if (!changes.removedDevices().isEmpty()) {
            for (var removedDevice : changes.removedDevices()) {
                store.cleanupSignalSessions(removedDevice);
            }
        }

        // WAWebIdentityUpdateDeviceTableApi.bulkApplyDeviceUpdate: store updated device list
        store.addDeviceList(newDeviceList);

        LOGGER.log(System.Logger.Level.DEBUG, "Device added for {0}: {1} devices", userJid, devices.size());
    }

    private Stream<DeviceInfo> parseAndValidateAddedDevice(Node deviceNode, Map<Integer, Integer> keyIndexMap, ValidatedKeyIndexListResult validatedKeyIndexInfo) {
        var id = deviceNode.getAttributeAsInt("id");
        if (id.isEmpty()) {
            return Stream.empty();
        }

        var deviceId = id.getAsInt();
        var keyIndex = keyIndexMap.getOrDefault(deviceId, 0);

        // WAWebHandleAdvDeviceNotificationApi: for NOTIFICATION devices (server/incoming),
        // only accept if keyIndex is in validIndexes. The "keyIndex > currentIndex" rule
        // applies ONLY to CACHED devices. validIndexes is cryptographically signed.
        if (validatedKeyIndexInfo != null && validatedKeyIndexInfo.validIndexes() != null && !validatedKeyIndexInfo.validIndexes().isEmpty()) {
            var isInValidIndexes = validatedKeyIndexInfo.validIndexes().contains(keyIndex);

            // WAWebHandleAdvDeviceNotificationApi: notification devices ONLY accept validIndexes
            // Primary device (keyIndex 0) is always accepted
            if (keyIndex != 0 && !isInValidIndexes) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Device {0} has keyIndex {1} not in validIndexes {2}, excluding from notification",
                        deviceId, keyIndex, validatedKeyIndexInfo.validIndexes());
                return Stream.empty();
            }
        }

        // WAWebBizCoexGatingUtils: filter hosted devices when bizHostedDevicesEnabled=false
        if (deviceId != DeviceConstants.HOSTED_DEVICE_ID) {
            return Stream.of(DeviceInfo.ofE2EE(deviceId, keyIndex));
        }

        if (isBizHostedDevicesEnabled()) {
            return Stream.of(DeviceInfo.ofHosted(keyIndex));
        }

        return Stream.empty();
    }

    /**
     * Handles a device remove notification.
     *
     * @param userJid          the user JID
     * @param keyIndexListNode the key-index-list node containing devices being removed
     * @param timestamp        the notification timestamp (Unix seconds)
     *
     * @apiNote WAWebHandleAdvDeviceNotificationApi.handleDeviceRemoveNotification: the
     * notification contains devices being REMOVED. Filter logic at line:
     * {@code n.devices.filter(e => e.id !== DEFAULT_DEVICE_ID && (t=r.get(e.id), t==null || t!==e.keyIndex))}
     */
    private void handleDeviceRemoveNotification(Jid userJid, Node keyIndexListNode, long timestamp) {
        // WAWebHandleAdvDeviceNotificationApi: need cached list to filter against
        var cachedList = store.findDeviceList(userJid);
        if (cachedList.isEmpty() || cachedList.get().deleted()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No cached device list for {0}, ignoring remove", userJid);
            return;
        }

        var oldList = cachedList.get();

        // WAWebHandleAdvDeviceNotificationApi: reject if notification is older than cached list
        // (t < n.timestamp) => return null
        if (timestamp < oldList.timestamp().getEpochSecond()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Device remove notification timestamp {0} < cached {1}, ignoring",
                    timestamp, oldList.timestamp().getEpochSecond());
            return;
        }

        // WAWebHandleAdvDeviceNotificationApi: build map of devices being REMOVED
        var removedDevicesMap = buildKeyIndexMap(keyIndexListNode);

        // WAWebHandleAdvDeviceNotificationApi: keep devices NOT in notification OR with different keyIndex
        // n.devices.filter(e => e.id !== DEFAULT_DEVICE_ID && (t=r.get(e.id), t==null || t!==e.keyIndex))
        var remainingDevices = oldList.devices().stream()
                .filter(device -> {
                    if (device.isPrimary()) {
                        return false; // Will be added back explicitly
                    }

                    var notificationKeyIndex = removedDevicesMap.get(device.id());
                    // Keep if: NOT in notification OR keyIndex doesn't match notification
                    return notificationKeyIndex == null || notificationKeyIndex != device.keyIndex();
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // WAWebHandleAdvDeviceNotificationApi: always add primary device back
        // a.push({id: DEFAULT_DEVICE_ID, keyIndex: 0})
        remainingDevices.add(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0));

        // WAWebIdentityUpdateDeviceTableApi: cleanup Signal sessions for removed devices
        // A device is removed if: in notification AND keyIndex matches
        var removedDevices = oldList.devices().stream()
                .filter(device -> {
                    if (device.isPrimary()) {
                        return false; // Primary is never removed
                    }
                    var notificationKeyIndex = removedDevicesMap.get(device.id());
                    // Removed if: IN notification AND keyIndex matches
                    return notificationKeyIndex != null && notificationKeyIndex == device.keyIndex();
                })
                .toList();
        for (var removedDevice : removedDevices) {
            var deviceJid = removedDevice.toDeviceJid(userJid.user(), userJid.server());
            store.cleanupSignalSessions(deviceJid);
        }

        // WAWebHandleAdvDeviceNotificationApi: create updated list preserving original timestamp
        // {update: extends({}, n, {devices: a}), clearRecord: false}
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

        // Store updated device list
        store.addDeviceList(updatedList);

        LOGGER.log(System.Logger.Level.DEBUG, "Devices removed for {0}: {1} remaining", userJid, remainingDevices.size());
    }

    /**
     * Builds a map of device ID to key index from the key-index-list node.
     *
     * <p>Parses the device children of the key-index-list node and extracts
     * the device ID (from jid attribute) and key-index pairs.
     *
     * @param keyIndexListNode the key-index-list node to parse
     * @return map of device ID to key index
     *
     * @apiNote WAWebHandleAdvDeviceNotificationApi: builds map from notification devices
     * using {@code new Map(e.map(e => [e.id, e.keyIndex]))}.
     */
    private static Map<Integer, Integer> buildKeyIndexMap(Node keyIndexListNode) {
        return keyIndexListNode.streamChildren("device")
                .flatMap(DeviceService::parseDevice)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

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
     * Validates and extracts key index list data from the signed protobuf.
     *
     * @param keyIndexListNode the key-index-list node from the notification
     * @return the validated key index list data, or {@code null} if validation fails
     *
     * @apiNote WAWebHandleAdvDeviceNotificationUtils.decodeSignedKeyIndexBytes: validates
     * Curve25519 signature using embedded or stored accountSignatureKey.
     */
    private ValidatedKeyIndexListResult validateKeyIndexList(Node keyIndexListNode) {
        var signedKeyIndexBytes = keyIndexListNode.toContentBytes();
        if (signedKeyIndexBytes.isEmpty()) {
            return null;
        }

        var validated = advValidator.validateAndDecodeSignedKeyIndexList(signedKeyIndexBytes.get());
        if (validated.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Key index list signature verification failed in notification");
            return null;
        }

        return validated.get();
    }

    /**
     * Triggers a USync query for coex device add notifications when no cached record exists.
     *
     * <p>When a device add notification is received but no cached record exists, we need
     * to fetch the complete device list from the server.
     *
     * @param deviceListNode the device-list node from the notification, or {@code null}
     * @param userJid        the user JID
     *
     * @apiNote WAWebBizCoexUtils.triggerUsyncForCoexDeviceAdd: checks resumeFromRestartComplete
     * flag to determine whether to trigger immediate sync or queue for doPendingDeviceSync.
     */
    private void triggerUsyncForCoexDeviceAdd(Node deviceListNode, Jid userJid) {
        // WAWebBizCoexUtils: check if this is a hosted device notification
        var isHostedDevice = deviceListNode != null && hasHostedDevice(deviceListNode);

        // WAWebBizCoexUtils.triggerUsyncForCoexDeviceAdd:
        // if (resumeFromRestartComplete) syncDeviceListJob else addUserToPendingDeviceSync
        if (store.isResumeFromRestartComplete()) {
            // Resume complete - trigger immediate device list sync
            getDeviceLists(List.of(userJid), "coex_device_notification", null, false);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Triggered immediate USync for device notification from {0}", userJid);
        } else {
            // During resume - queue for later via addUserToPendingDeviceSync
            var pendingSync = PendingDeviceSync.of(List.of(userJid), "coex_device_notification");
            store.addPendingDeviceSync(pendingSync);

            if (isHostedDevice) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Queued coex USync for hosted device notification from {0} (during resume)", userJid);
            }
        }
    }

    private static boolean hasHostedDevice(Node deviceListNode) {
        return deviceListNode.streamChildren("device")
                .anyMatch(deviceNode -> deviceNode.hasAttribute("id", DeviceConstants.HOSTED_DEVICE_ID));
    }

    /**
     * Extracts and validates a local device identity from a pairing response.
     *
     * @param deviceIdentityNode the device identity node from pairing response
     * @return the validated signed device identity with generated device signature
     * @throws IllegalStateException          if required store values are missing
     *
     * @apiNote WAWebHandlePairSuccess: validates SignedDeviceIdentityHMAC during pairing,
     * verifies account signature, and generates device signature using local identity key.
     * Uses advSecretKey (not companion public key) for HMAC verification.
     */
    public Optional<SignedDeviceIdentity> extractAndValidateLocalSignedDeviceIdentity(Node deviceIdentityNode) {
        try {
            var signedDeviceIdentity = advValidator.extractAndValidateLocalSignedDeviceIdentity(deviceIdentityNode);
            return Optional.of(signedDeviceIdentity);
        } catch (WhatsAppAdvValidationException exception) {
            client.handleFailure(exception);
            return Optional.empty();
        }
    }
}
