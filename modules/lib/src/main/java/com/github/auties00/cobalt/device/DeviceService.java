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
 * Orchestrates all device list operations for WhatsApp Multi-Device: synchronises companion
 * device lists over USync, processes real-time device add/remove notifications, computes
 * message fanout (1:1 and group), attaches ICDC metadata to outgoing messages, and verifies
 * business coexistence (hosted) transitions.
 *
 * <p>Every outgoing message and every decrypted PKMSG routes through this service so the
 * client maintains a consistent view of each peer's companion devices and each peer's
 * identity keys. It also schedules a daily ADV expiration check via
 * {@link com.github.auties00.cobalt.device.adv.DeviceADVChecker} to invalidate stale device
 * records and trigger proactive syncs.
 *
 * <p>This service is the single entry-point that the message send pipeline uses to decide
 * who receives a message and with which key; the message receive pipeline uses it to update
 * the local device cache when it observes identity changes, key rotations, and account-type
 * transitions in incoming messages.
 *
 * @implNote WAWebAdvSyncDeviceListApi: provides syncDeviceList, syncMyDeviceList, syncAndGetDeviceList.
 * WAWebDBDeviceListFanout.getFanOutList: generates device lists for message fanout.
 * WAWebAdvHandlerApi: handles USync response processing and dispatches to handlers.
 * WAWebHandleAdvKeyIndexResultApi: processes full device list responses with key index validation.
 * WAWebHandleAdvOmittedResultApi: handles omitted responses when server dhash matches local.
 * WAWebHandleAdvDeviceNotificationApi: processes real-time device add/remove notifications.
 * WAWebHandleAdvForMessageApi: handles ADV device updates from incoming messages.
 * WAWebIcdcHandlerApi: handles ICDC data processing for incoming messages.
 * WAWebApiDeviceList: provides the per-user device-list cache and CRUD operations:
 * getDeviceRecord, bulkGetDeviceRecord, createOrReplaceDeviceRecord,
 * bulkCreateOrReplaceDeviceRecord, getDeviceIds, hasDevice, getDeviceInfoForSync,
 * getMyDeviceList, getAllDeviceLists.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvSyncDeviceListApi")
@WhatsAppWebModule(moduleName = "WAWebAdvHandlerApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvKeyIndexResultApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvOmittedResultApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvDeviceNotificationApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvForMessageApi")
@WhatsAppWebModule(moduleName = "WAWebIcdcHandlerApi")
@WhatsAppWebModule(moduleName = "WAWebApiDeviceList")
public final class DeviceService {
    /**
     * Logger for device service operations.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceService.class.getName());

    /**
     * Custom structured-concurrency joiner that fans out one USync IQ per batch and collects
     * the parsed {@link DeviceListResult} entries across all batches into a single flat list.
     *
     * <p>Failed subtasks are swallowed rather than cancelling the entire scope so that one
     * failed batch does not lose the successful results from its siblings; failures surface
     * later when the expected JID is missing from the collated output and callers fall back
     * to the primary-only device list.
     *
     * @implNote ADAPTED: WAWebUsync.USyncQuery.execute uses Promise.all on sliced batches and
     * Promise.allSettled semantics; Cobalt implements the same semantics via a bespoke
     * {@link Joiner} over virtual threads.
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
     * Reference to the web app-state service used to schedule the
     * all-devices-responded missing-key grace period check after a
     * device removal has made every remaining device unable to
     * produce a requested sync key.
     *
     * @implNote WAWebSyncdStoreMissingKeys.N
     */
    private final WebAppStateService webAppStateService;

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
     * @implNote WAWebAdvSyncDeviceListApi.syncDeviceList: uses a Map (_) to track
     * pending promises and avoid duplicate requests for the same user.
     */
    private final ConcurrentHashMap<Jid, CompletableFuture<DeviceList>> pendingFetches;

    /**
     * Scheduler for periodic ADV device info expiration checks.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob: runs every 24 hours to detect and handle
     * expired device lists based on timestamp age and expectedTs staleness.
     */
    private final DeviceADVChecker advCheckScheduler;

    /**
     * Service for fetching and storing Signal pre-key bundles and identity keys.
     *
     * @implNote WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: prefetches identity
     * keys for users with validated key index info after device sync.
     */
    private final DevicePreKeyHandler preKeyHandler;

    /**
     * Service for accessing AB (A/B test) property values for feature gating.
     *
     * @implNote WAWebABProps: provides configuration values that control feature behavior
     * such as hosted device support, username sync, and expiration thresholds.
     */
    private final ABPropsService abPropsService;

    /**
     * Service for validating ADV signatures on device identities and key index lists.
     *
     * @implNote WAWebAdvSignatureApi: validates Curve25519 signatures using the appropriate headers.
     */
    private final DeviceADVValidator advValidator;

    /**
     * Parser for USync IQ response nodes into structured device list results.
     *
     * @implNote WAWebUsyncDevice.deviceParser: extracts device lists, key indices,
     * timestamps, and hosting status from USync response XML nodes.
     */
    private final DeviceUSyncResponseParser usyncResponseParser;

    /**
     * Service for calculating message fanout (which devices receive a message).
     *
     * @implNote WAWebDBDeviceListFanout.getFanOutList: computes the set of device JIDs
     * that should receive a message, excluding self devices and filtering hosted devices.
     */
    private final DeviceFanoutCalculator fanoutCalculator;

    /**
     * Service for computing ICDC (Identity Change Detection Consistency) metadata.
     *
     * @implNote WAWebIdentityIcdcApi: computes identity key hashes and device
     * timestamps for inclusion in every outgoing message's messageContextInfo.
     */
    private final IcdcComputer icdcComputer;

    /**
     * Service for calculating participant hashes (phash) for group message verification.
     *
     * @implNote WAWebPhashUtils: computes SHA-1 (V1) or SHA-256 (V2) hash of sorted
     * device JIDs to verify sender and server agree on recipient list.
     */
    private final DevicePhashCalculator phashCalculator;

    /**
     * Lock for serializing device table updates.
     *
     * <p>Ensures consistency when updating multiple related tables (device-list, session,
     * sender-key, missing-keys, contact) that must remain synchronized.
     *
     * @implNote WAWebApiGetDeviceUpdateLock.getDeviceUpdateLock: device table updates should
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
     * @implNote WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg: prevents creating
     * duplicate system messages for the same user within the same second.
     */
    private final ConcurrentHashMap<Jid, Instant> hostedSystemMsgDedupCache;

    /**
     * Cache for tracking which senders have already been processed for offline hosted ICDC.
     *
     * <p>Prevents redundant processing of hosted ICDC metadata during offline message delivery.
     * This is an in-memory cache that is cleared on reconnect.
     *
     * @implNote WAWebBizCoexOfflineICDCHandledCache: module-level in-memory cache
     * used by handleHostedIcdcMetadataInline to avoid duplicate processing.
     */
    private final Set<Jid> offlineBizHostedSenderICDCProcessedCache;

    /**
     * Creates a new device service and its owned helpers.
     *
     * <p>Instantiates the ADV validator, fanout calculator, phash calculator, ICDC computer,
     * USync response parser, pre-key handler, and ADV expiration scheduler. All of these
     * collaborators receive the same store and AB props dependencies so they operate on a
     * single consistent view of device state.
     *
     * @param client         the WhatsApp client providing store and network access
     * @param abPropsService the AB props service for feature gating
     * @param sessionCipher  the Signal session cipher used by the pre-key handler
     * @implNote ADAPTED: WA Web wires these modules via module-level imports; Cobalt groups
     * their construction here so a single {@link DeviceService} instance owns and can
     * deterministically tear down the background ADV scheduler.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceService(WhatsAppClient client, WebAppStateService webAppStateService, ABPropsService abPropsService, SignalSessionCipher sessionCipher) {
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
        this.advCheckScheduler = new DeviceADVChecker(client, this, abPropsService);
        this.deviceUpdateLock = new ReentrantLock();
        this.hostedSystemMsgDedupCache = new ConcurrentHashMap<>();
        this.offlineBizHostedSenderICDCProcessedCache = ConcurrentHashMap.newKeySet();
    }

    /**
     * Executes a task while holding the device update lock.
     *
     * <p>This ensures consistency when updating multiple related tables that must
     * remain synchronized (device-list, session, sender-key, missing-keys, contact).
     *
     * @param task the task to execute
     *
     * @implNote WAWebApiGetDeviceUpdateLock.getDeviceUpdateLock: ensures device table updates
     * (device-list, session, sender-key, missing-keys, contact) are serialized.
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
     * Checks if the hostedOverrideAdvAccountSignatureKey feature is enabled.
     *
     * <p>When enabled, allows using the embedded accountSignatureKey from the protobuf
     * for identity verification of hosted devices, rather than requiring a stored identity.
     *
     * @return {@code true} if the feature is enabled
     *
     * @implNote WAWebBizCoexGatingUtils.hostedOverrideAdvAccountSignatureKeyEnabled: requires
     * both adv_accept_hosted_devices AND override_adv_account_signature_key_enabled AB props
     * to be true. Used in WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "hostedOverrideAdvAccountSignatureKeyEnabled",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * @implNote WAWebAdvSyncDeviceListApi.syncAndGetDeviceList: if local phash matches
     * expectedPhash (l===d), the function returns early without sending USync request.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = {"syncDeviceList", "syncAndGetDeviceList"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices) {
        // WAWebAdvSyncDeviceListApi.syncDeviceList: if phash is provided (a!=null), check local match
        if (expectedPhash != null && !expectedPhash.isEmpty()) {
            try {
                // WAWebApiDeviceList.getDeviceIds: get cached device lists for all requested JIDs
                // WA Web returns null for missing/deleted records; null entries contribute no devices
                var cachedLists = userJids.stream()
                        .map(jid -> store.findDeviceList(jid).orElse(null))
                        .toList();

                // WAWebAdvSyncDeviceListApi.syncDeviceList: compute phash from all device JIDs
                // Null/deleted entries map to empty device lists in WA Web
                var allDeviceJids = cachedLists.stream()
                        .filter(list -> list != null && !list.deleted())
                        .map(DeviceList::deviceJids)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toUnmodifiableSet());

                // WAWebPhashUtils.phashV2: compute local phash
                var localPhash = phashCalculator.calculate(allDeviceJids, DevicePhashVersion.V2, true);

                // WAWebAdvSyncDeviceListApi.syncDeviceList: if (a === m) return early
                if (localPhash.equals(expectedPhash)) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Phash pre-check passed, skipping device sync");
                    var nonNullLists = cachedLists.stream()
                            .filter(Objects::nonNull)
                            .toList();
                    return mergeAlternateDeviceLists(nonNullLists);
                }
            } catch (NoSuchAlgorithmException e) {
                // WAWebAdvSyncDeviceListApi: fall through to normal sync on error
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
     * @implNote WAWebLidMigrationUtils: during migration, both PN and LID may have device records.
     * The PN identity is preferred as the canonical representation.
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils",
            exports = "getCurrentLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * @param caller   the caller context for logging (e.g., "device_sync_request"); when
     *                 {@code null}, the literal {@code "unknown"} is used to match WA Web
     *
     * @implNote WAWebApiContact.checkPnToLidMapping: diagnostic check that logs warnings
     *           for phone number JIDs missing LID mappings. Called before USync requests.
     *           Uses {@link Set} for both the eligible-PN bucket and the missing-mapping
     *           bucket so that duplicates in {@code userJids} are collapsed exactly the
     *           way WA Web's two {@code Set} accumulators do. The eligible filter
     *           excludes bots ({@code !isBot()}), hosted users ({@code !isHosted()}, which
     *           covers both {@code hosted} and {@code hosted.lid} servers), and LIDs
     *           ({@code !isLid()}). The look-up uses {@link WhatsAppStore#findLidByPhone}
     *           which already implements WA Web's {@code getCurrentLid} me-user fast path.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiContact",
            exports = "checkPnToLidMapping",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void checkPnToLidMapping(Collection<Jid> userJids, String caller) {
        // WAWebApiContact.checkPnToLidMapping: build the eligible-PN set
        // Mirrors the JS forEach + Set.add: a user JID enters the bucket only when it is
        // not a bot, not hosted (hosted or hosted.lid), and not a LID. Duplicates are
        // collapsed by the Set just like WA Web's `r` accumulator.
        var phoneNumberJids = new LinkedHashSet<Jid>();
        for (var jid : userJids) {
            if (jid.isBot() || jid.hasHostedServer() || jid.hasHostedLidServer() || jid.hasLidServer()) {
                continue;
            }
            // WAWebWidFactory.asUserWidOrThrow: strip device/agent before lookup
            phoneNumberJids.add(jid.toUserJid());
        }

        // WAWebApiContact.checkPnToLidMapping: build the missing-mapping set
        // Mirrors the JS forEach + Set.add over `r`: every PN whose getCurrentLid lookup
        // returns null is collected into the second Set `a`.
        var missingMappings = new LinkedHashSet<Jid>();
        for (var jid : phoneNumberJids) {
            // WAWebApiContact.getCurrentLid: store.findLidByPhone embeds the me-user fast path
            if (store.findLidByPhone(jid).isEmpty()) {
                missingMappings.add(jid);
            }
        }

        // WAWebApiContact.checkPnToLidMapping: emit the diagnostic log when any mapping is missing
        // Caller defaults to "unknown" when null, matching the JS `n!=null?n:"unknown"` ternary.
        if (!missingMappings.isEmpty()) {
            var resolvedCaller = caller != null ? caller : "unknown";
            LOGGER.log(System.Logger.Level.WARNING,
                    "LID null - {0} PNs, missing: {1}, caller: {2}",
                    phoneNumberJids.size(), missingMappings.size(), resolvedCaller);
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
     * @implNote WAWebAdvSyncDeviceListApi.syncDeviceList: uses a Map (_) to deduplicate
     * requests. Builds USync query via WAWebUsync.USyncQuery with device protocol.
     * Processes results via WAWebAdvHandlerApi.handleADVDeviceSyncResult.
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

        // WAWebContactSyncLogger.contactSyncLogger.createEventContext: start timestamp for
        // the ContactSyncEvent WAM emission at success/failure time.
        var syncStartTimestamp = Instant.now();
        int fetchedResponseCount = 0;

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
            fetchedResponseCount = fetchedResults.size();

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
                        // WAWebHandleAdvOmittedResultApi: if (!t || t.deleted) return null
                        if (cachedList.isEmpty() || cachedList.get().deleted()) {
                            yield null;
                        }

                        var oldList = cachedList.get();

                        // WAWebHandleAdvOmittedResultApi: if (e != null && e < t.timestamp) return null
                        if (omitted.timestamp().isPresent()
                                && omitted.timestamp().get().isBefore(oldList.timestamp())) {
                            yield null;
                        }
                        // WAWebHandleAdvOmittedResultApi: if (e != null) a.timestamp = e
                        // Only update timestamp and check expectedTs clearing when ts is present.
                        // The shallow-copy semantics of `babelHelpers.extends({}, t)` mean the
                        // expectedTs / expectedTsLastDeviceJobTs / expectedTsUpdateTs fields are
                        // preserved from the cached record by default and are only mutated when
                        // shouldClearExpectedTs returns true.
                        Instant newTimestamp;
                        Instant finalExpectedTs = oldList.expectedTimestamp();
                        Instant newExpectedTsUpdateTs = oldList.expectedTimestampUpdateTimestamp();
                        Instant newExpectedTsLastDeviceJobTs = oldList.expectedTimestampLastDeviceJobTimestamp();
                        if (omitted.timestamp().isPresent()) {
                            // WAWebHandleAdvOmittedResultApi: timestamp is present, update it (a.timestamp = e)
                            newTimestamp = omitted.timestamp().get();

                            // WAWebAdvExpectedTsApi.shouldClearExpectedTs: clear only when staleness
                            // condition is met. Note: the incoming expectedTs is passed solely as
                            // input to shouldClearExpectedTs and is NOT written into the record.
                            var incomingExpectedTs = omitted.expectedTimestamp().orElse(null);
                            if (DeviceExpectedTsUtils.shouldClearExpectedTimestamp(newTimestamp, incomingExpectedTs, oldList, lastADVCheckTime)) {
                                // a.expectedTs = void 0; a.expectedTsLastDeviceJobTs = void 0; a.expectedTsUpdateTs = void 0
                                finalExpectedTs = null;
                                newExpectedTsUpdateTs = null;
                                newExpectedTsLastDeviceJobTs = null;
                            }
                        } else {
                            // WAWebHandleAdvOmittedResultApi: when ts is null, preserve all existing values
                            newTimestamp = oldList.timestamp();
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

            // WAWebContactSyncLogger.contactSyncLogger.logSuccess: emit ContactSyncEvent (id 1006)
            // with contactSyncType/requestOrigin/counts/timestamps for the device-sync flow.
            // WA Web calls createUpdateCounterWith({deviceChange:d.length}) where d is the
            // filtered list of successful device entries, so deviceResponseNew tracks how many
            // result rows Cobalt successfully ingested.
            emitContactSyncSuccess(context, toFetch.size(), fetchedResponseCount, syncStartTimestamp,
                    abPropsService.getBool(ABProp.USERNAME_USYNC), result.size());

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

            // WAWebContactSyncLogger.contactSyncLogger.logFailure: emit ContactSyncEvent (id 1006)
            // with the USync error code and DEVICE_SYNC (1300) as the 429 fallback code.
            emitContactSyncFailure(context, toFetch.size(), fetchedResponseCount, syncStartTimestamp,
                    abPropsService.getBool(ABProp.USERNAME_USYNC), extractUsyncErrorCode(e),
                    CONTACT_SYNC_ERROR_CODE_DEVICE_SYNC);

            throw new RuntimeException("Failed to fetch device lists", e);
        } finally {
            // WAWebAdvSyncDeviceListApi: _.delete(createDeviceListPK(e)) - clean up pending requests
            for (var jid : toFetch) {
                pendingFetches.remove(jid);
            }
        }
    }

    /**
     * Bit position in the contact-sync protocol bitmask for the
     * {@code devices} protocol, matching WAWebContactSyncLogger PROTOCOL_BIT.DEVICE.
     */
    private static final int CONTACT_SYNC_PROTOCOL_BIT_DEVICE = 5;

    /**
     * Bit position in the contact-sync protocol bitmask for the
     * {@code username} protocol, matching WAWebContactSyncLogger PROTOCOL_BIT.USERNAME.
     */
    private static final int CONTACT_SYNC_PROTOCOL_BIT_USERNAME = 10;

    /**
     * Request origin for device-sync USync queries, matching
     * WAWebContactSyncLogger SYNC_REQUEST_ORIGIN.DEVICE_REQUEST.
     */
    private static final int CONTACT_SYNC_REQUEST_ORIGIN_DEVICE_REQUEST = 48;

    /**
     * Error-protocol code used as the 429 fallback when a device-sync USync
     * fails, matching {@link com.github.auties00.cobalt.wam.type.ContactSyncErrorCode#DEVICE_SYNC}.
     *
     * @implNote WAWebContactSyncErrorCodes.DEVICE_SYNC: {@code 1300}. The
     * constant is duplicated here as a plain {@code int} because the WAM
     * event property {@link com.github.auties00.cobalt.wam.event.ContactSyncEventEvent#contactSyncErrorCode()}
     * is serialized as a raw integer on the wire, not as an enum reference.
     */
    private static final int CONTACT_SYNC_ERROR_CODE_DEVICE_SYNC = 1300;

    /**
     * Computes the WAM contact-sync protocol bitmask for the device-sync
     * USync query, matching WAWebContactSyncLogger.computeProtocolBitmask
     * ({@code p(t.protocols)}).
     *
     * @param includeUsernameProtocol whether the username protocol was included
     * @return the protocol bitmask for the WAM request_protocol property
     *
     * @implNote WAWebContactSyncLogger.p: {@code for (var n of e) t |= 1<<m[n.getName()]}.
     */
    private static int contactSyncProtocolBitmask(boolean includeUsernameProtocol) {
        var bitmask = 1 << CONTACT_SYNC_PROTOCOL_BIT_DEVICE;
        if (includeUsernameProtocol) {
            bitmask |= 1 << CONTACT_SYNC_PROTOCOL_BIT_USERNAME;
        }
        return bitmask;
    }

    /**
     * Extracts the server-side USync error code from a caught exception,
     * preferring the code carried on {@link WhatsAppDeviceSyncException}.
     *
     * @param throwable the exception thrown during the USync flow
     * @return the error code, or {@code 0} when unavailable
     *
     * @implNote WAWebContactSyncLogger.logFailure: the second argument is
     * {@code c.errorCode} from the USync response.
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
     * Emits the successful {@code ContactSyncEvent} (id 1006) for the
     * device-sync USync flow.
     *
     * @param context                 the USync context string (for example
     *                                {@code "interactive"}, {@code "background"})
     * @param requestedCount          the number of JIDs originally requested
     * @param responseCount           the number of entries returned by the server
     * @param syncStartTimestamp      the instant at which the sync started
     * @param includeUsernameProtocol whether the username protocol was added
     * @param deviceResponseNew       the count of successful device results
     *                                ingested during this sync
     *
     * @implNote WAWebContactSyncLogger.logSuccess: emits ContactSyncEvent with
     * success=true, noop=(requestedCount===0), latency=now-start, and the
     * request_protocol bitmask.
     */
    private void emitContactSyncSuccess(String context, int requestedCount, int responseCount,
                                        Instant syncStartTimestamp, boolean includeUsernameProtocol,
                                        int deviceResponseNew) {
        var endTimestamp = Instant.now();
        client.wamService().commit(new ContactSyncEventEventBuilder()
                // WAWebContactSyncLogger.getSyncTypeString: (context + "_query").toUpperCase()
                .contactSyncType((context + "_query").toUpperCase(Locale.ROOT))
                // WAWebContactSyncLogger SYNC_REQUEST_ORIGIN.DEVICE_REQUEST
                .contactSyncRequestOrigin(CONTACT_SYNC_REQUEST_ORIGIN_DEVICE_REQUEST)
                .contactSyncSuccess(true)
                // WAWebContactSyncLogger: noop = requestedCount === 0
                .contactSyncNoop(requestedCount == 0)
                .contactSyncStartTimestamp(syncStartTimestamp)
                .contactSyncEndTimestamp(endTimestamp)
                // WAWebContactSyncLogger: latency is raw millisecond diff (Date.now() - startTimestamp)
                .contactSyncLatency((int) (endTimestamp.toEpochMilli() - syncStartTimestamp.toEpochMilli()))
                .contactSyncRequestedCount(requestedCount)
                .contactSyncResponseCount(responseCount)
                .contactSyncRequestProtocol(contactSyncProtocolBitmask(includeUsernameProtocol))
                // WAWebContactSyncLogger: failureProtocol is a bitmask of per-protocol errors;
                // on full success it is zero.
                .contactSyncFailureProtocol(0)
                // WAWebContactSyncLogger createUpdateCounterWith({deviceChange:d.length}): number of
                // successful device entries processed during this sync.
                .contactSyncDeviceResponseNew(deviceResponseNew)
                .build());
    }

    /**
     * Emits the failing {@code ContactSyncEvent} (id 1006) for the
     * device-sync USync flow.
     *
     * @param context                 the USync context string
     * @param requestedCount          the number of JIDs originally requested
     * @param responseCount           the number of entries returned by the server
     *                                before the failure was observed, or {@code 0}
     * @param syncStartTimestamp      the instant at which the sync started
     * @param includeUsernameProtocol whether the username protocol was added
     * @param serverErrorCode         the error code from the USync response
     * @param fallbackErrorCode       the fallback error code used when the
     *                                server code is {@code 429} (rate-limited)
     *
     * @implNote WAWebContactSyncLogger.logFailure: {@code s = n===429 && a!=null ? a : n}.
     */
    private void emitContactSyncFailure(String context, int requestedCount, int responseCount,
                                        Instant syncStartTimestamp, boolean includeUsernameProtocol,
                                        int serverErrorCode, int fallbackErrorCode) {
        var endTimestamp = Instant.now();
        var errorCode = serverErrorCode == 429 ? fallbackErrorCode : serverErrorCode;
        client.wamService().commit(new ContactSyncEventEventBuilder()
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
                // WAWebContactSyncLogger: failureProtocol = _(r.error) bitmask of per-protocol errors;
                // Cobalt does not track per-protocol sub-errors, so zero is emitted.
                .contactSyncFailureProtocol(0)
                .build());
    }

    /**
     * Sends all USync batches in parallel and collates the parsed results into a single list.
     *
     * @param batches the USync IQ batches to dispatch
     * @return the flattened list of parsed device list results
     * @throws RuntimeException if the calling virtual thread is interrupted while waiting
     * @implNote WAWebUsync.USyncQuery.execute: WA Web iterates batches with {@code Promise.all};
     * Cobalt forks virtual-thread subtasks with {@link StructuredTaskScope} and accumulates
     * results via the static {@code JOINER}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private List<DeviceListResult> getDevicesFetchedResults(List<NodeBuilder> batches) {
        // WAWebUsync.USyncQuery.execute
        // Fans out one virtual-thread subtask per batch and collates their parsed results
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
     * @implNote WAWebBizCoexHostedAddVerification: requires users transitioning to HOSTED to
     * be in the verification cache (populated during USync). WAWebIdentityUpdateDeviceTableApi:
     * clears Signal sessions when account type changes. WAWebBizCoexUtils: creates system messages.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "handleAccountTypeTransition",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleAccountTypeTransition(Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType, DeviceList oldList) {
        LOGGER.log(System.Logger.Level.INFO, "Account type changed for {0}: {1} -> {2}", userJid, oldType, newType);

        // WAWebBizCoexHostedAddVerification: verify hosted users are in cache
        // This prevents accepting transitions from potentially spoofed hosted device updates
        if (newType == ADVEncryptionType.HOSTED) {
            if(!store.isInCoexHostedVerificationCache(userJid)) {
                throw new IllegalStateException(userJid + " is not in the coex hosted verification cache");
            }
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
        createAccountTypeChangeSystemMessage(userJid, oldType, newType);
    }

    /**
     * Creates a system message in the chat when a contact's account type changes.
     *
     * <p>Creates a stub message indicating whether messages with this contact are now
     * end-to-end encrypted (E2E_ENCRYPTED_NOW) or no longer encrypted (CIPHERTEXT).
     * After the system message is inserted, this method also commits a
     * {@code CoexPrivacySysMsg} WAM event mirroring
     * {@code WAWebBizCoexUtils.sendWamCoexPrivacySysMsgInsertSuccess} so telemetry
     * reports the insertion outcome, state transition, and whether the target is
     * the current user.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous account type (may be {@code null} if unknown)
     * @param newType the new account type
     *
     * @implNote WAWebBizCoexUtils: creates system messages for account type transitions.
     * Uses shouldDedupInitialHostedSystemMsg to prevent duplicate messages within the same second.
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

        // WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg: check if should deduplicate
        if (newType == ADVEncryptionType.HOSTED && shouldDedupInitialHostedSystemMsg(userJid)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Skipping duplicate hosted system message for {0}", userJid);
            return;
        }

        // WAWebBizCoexUtils: E2EE→HOSTED = no longer E2E encrypted (CIPHERTEXT)
        // HOSTED→E2EE = now E2E encrypted (E2E_ENCRYPTED_NOW)
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

        // WAWebBizCoexUtils.sendWamCoexPrivacySysMsgInsertSuccess: commit a CoexPrivacySysMsg
        // WAM event for the inserted system message. Mirrors the subtype -> property
        // mapping in WAWebBizCoexUtils.C: state_transition is determined by (oldType,newType),
        // isSelf reflects whether the transitioning user is the currently logged-in account,
        // businessId is the numeric user-part of the transitioning JID, and multiDeviceId
        // comes from the signed-in device (WAWebUserPrefsMeUser.getMaybeMeDevicePn.device).
        emitCoexPrivacySysMsgWamEvent(userJid, oldType, newType);
    }

    /**
     * Commits a {@code CoexPrivacySysMsg} WAM event describing an inserted account-type
     * change system message.
     *
     * <p>Maps the (oldType, newType) pair to the state-transition enum used by WA Web's
     * subtype routing: {@code E2EE->HOSTED} and {@code null->HOSTED} produce
     * {@code E2EE_TO_HOSTED}, {@code HOSTED->E2EE} produces {@code HOSTED_TO_E2EE}, and
     * {@code HOSTED->HOSTED} produces {@code HOSTED_TO_HOSTED}. The {@code coexSysMsgIsSelf}
     * flag is set when the transitioning user equals the currently logged-in user, matching
     * the {@code biz_me_account_type_is_hosted*} subtypes in WA Web.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous account type, or {@code null} if unknown
     * @param newType the new account type
     *
     * @implNote WAWebBizCoexUtils.C: constructs CoexPrivacySysMsgWamEvent with
     * {@code coexSysMsgInsertionSuccess=true}, {@code coexSysMsgMultiDeviceId=meDevice.device},
     * a subtype-dependent {@code coexSysMsgStateTransitionAttempt}, {@code coexSysMsgIsSelf}
     * based on whether the target is the logged-in user, and {@code coexSysMsgBusinessId}
     * populated from {@code t.remote.user} (the transitioning user's numeric id).
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "sendWamCoexPrivacySysMsgInsertSuccess",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitCoexPrivacySysMsgWamEvent(Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType) {
        // WAWebBizCoexUtils.C: determine state transition attempt enum from (old,new)
        CoexSysMsgStateTransitionAttempt stateTransition;
        if (newType == ADVEncryptionType.E2EE) {
            // "encrypt" subtype -> HOSTED_TO_E2EE
            stateTransition = CoexSysMsgStateTransitionAttempt.HOSTED_TO_E2EE;
        } else if (oldType == ADVEncryptionType.HOSTED) {
            // "biz_account_type_is_hosted" / "biz_me_account_type_is_hosted" -> HOSTED_TO_HOSTED
            stateTransition = CoexSysMsgStateTransitionAttempt.HOSTED_TO_HOSTED;
        } else {
            // "biz_account_type_changed_to_hosted" / "biz_me_account_type_is_hosted_transition"
            // -> E2EE_TO_HOSTED (also covers null -> HOSTED)
            stateTransition = CoexSysMsgStateTransitionAttempt.E2EE_TO_HOSTED;
        }

        // WAWebBizCoexUtils.C: isSelf when the transitioning user is the current account
        // (matches the "biz_me_account_type_is_hosted*" subtypes in WA Web).
        var meJid = store.jid().orElse(null);
        var isSelf = meJid != null && Objects.equals(meJid.user(), userJid.user());

        // WAWebBizCoexUtils.C: coexSysMsgMultiDeviceId = getMaybeMeDevicePn().device ?? 0
        var multiDeviceId = meJid != null ? meJid.device() : 0;

        // WAWebBizCoexUtils.C: coexSysMsgBusinessId = t.remote.user (numeric user part of
        // the transitioning user JID). For self transitions WA Web uses the me user's
        // own numeric id, which for Cobalt is equivalent because userJid == meJid.user here.
        var builder = new CoexPrivacySysMsgEventBuilder()
                .coexSysMsgInsertionSuccess(Boolean.TRUE)
                .coexSysMsgIsSelf(isSelf)
                .coexSysMsgMultiDeviceId(multiDeviceId)
                .coexSysMsgStateTransitionAttempt(stateTransition)
                .coexSysMsgBusinessId(userJid.user());

        // WAWebBizCoexUtils.C emission path: channel is left unset for the
        // sendWamCoexPrivacySysMsgInsertSuccess (bulkApplyDeviceUpdate / clearDeviceRecord)
        // call site; only the history-sync entrypoint populates HISTORY_SYNC.
        client.wamService().commit(builder.build());
    }

    /**
     * Checks if an initial hosted system message should be deduplicated.
     *
     * @param userJid the user JID
     * @return {@code true} if the message should be skipped (duplicate)
     *
     * @implNote WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg: returns true if a
     * system message was already created for this user within the same second.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "shouldDedupInitialHostedSystemMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldDedupInitialHostedSystemMsg(Jid userJid) {
        // WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg: uses unixTime() (seconds granularity)
        // Creates key from jid + "_" + unixTime, dedup within same second
        var currentSecond = Instant.now().getEpochSecond();
        var lastMsgSecond = hostedSystemMsgDedupCache.get(userJid);

        if (lastMsgSecond != null && lastMsgSecond.getEpochSecond() == currentSecond) {
            // WAWebBizCoexUtils: message already created this second - deduplicate
            return true;
        }

        // WAWebBizCoexUtils: add to dedup set and return false (not a duplicate)
        hostedSystemMsgDedupCache.put(userJid, Instant.ofEpochSecond(currentSecond));
        return false;
    }

    /**
     * Cleans up all Signal sessions and sender keys for all devices of a user.
     *
     * @param userJid the user JID
     * @param oldList the device list containing devices to clean up
     *
     * @implNote WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clears Signal sessions
     * for all non-primary devices when cleaning up a device record.
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
     * @implNote WAWebHandleAdvListResetApi.handleListReset: when rawId changes (l.rawId!==f),
     * all Signal sessions must be cleared and the device list rebuilt from scratch.
     * This is the "clearRecord" path in WAWebHandleAdvKeyIndexResultApi.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvKeyIndexResultApi",
            exports = "handleKeyIndexResultSync",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * @implNote WAWebHandleAdvListResetApi.handleListReset: clears all existing Signal
     * sessions for the user when rawId changes. Called via clearDeviceRecord path.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvListResetApi",
            exports = "handleListReset",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * @implNote WAWebHandleAdvNoListResetApi.handleNoListReset: validates incoming keyIndex
     * against cached validIndexes. Throws error if timestamp is not newer but keyIndex is
     * not in validIndexes (indicates replay attack or corrupted state).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvNoListResetApi",
            exports = "handleNoListReset",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * @implNote WAWebContactSyncUtils.backfillMissingDeviceSyncEntries: if a PN entry was requested
     * but server only returned the LID entry, duplicate the LID result as if it were the PN entry.
     * Uses getCurrentLid() to find LID mappings and isRegularUserPn() to filter applicable JIDs.
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSyncUtils",
            exports = "backfillMissingDeviceSyncEntries",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * @implNote WAWebLastADVCheckTimeApi.getLastADVDeviceInfoCheckTime: returns the timestamp
     * of the last scheduled ADV device info check job.
     */
    @WhatsAppWebExport(moduleName = "WAWebLastADVCheckTimeApi",
            exports = "getLastADVDeviceInfoCheckTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Instant> lastAdvCheckTime() {
        return store.lastAdvCheckTime();
    }

    /**
     * Updates the ADV check time in WhatsAppStore to the current time.
     *
     * @implNote WAWebLastADVCheckTimeApi: stores the timestamp when the ADV device info
     * check job runs, used for expected timestamp staleness calculations.
     */
    @WhatsAppWebExport(moduleName = "WAWebLastADVCheckTimeApi",
            exports = "setLastADVDeviceInfoCheckTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * @implNote WAWebDBDeviceListFanout.getFanOutList: when no device record is found,
     * sends to primary device only (l.toString()).
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
     * Creates a deleted device list marker.
     *
     * <p>Used to mark a device list as deleted in the cache, optionally indicating
     * that the deletion was due to a transition to hosted account type.
     *
     * @param userJid       the user JID
     * @param changedToHost whether the deletion is due to a HOSTED transition
     * @return a deleted device list marker
     *
     * @implNote WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: marks device list
     * as deleted=true. WAWebBizCoexUtils: sets deletedChangedToHost for HOSTED transitions.
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
     * Starts the ADV device info check scheduler.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: schedules periodic
     * check that runs every 24 hours to verify device list freshness.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void startAdvCheckScheduler() {
        advCheckScheduler.start();
    }

    /**
     * Stops the ADV device info check scheduler.
     *
     * <p>Should be called before client disconnects to prevent background tasks
     * from running on a disconnected client.
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob: WA Web has no explicit cancel hook
     * for the scheduled timeout; the timeout is simply cleared during logout.
     * Cobalt exposes this as a public method so the connection lifecycle can
     * deterministically stop background work before disconnect.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void stopAdvCheckScheduler() {
        advCheckScheduler.close();
    }

    /**
     * Retries all pending device syncs.
     *
     * <p>Should be called after client reconnects to complete any device syncs
     * that failed due to connection issues. Respects retry limits and expiration.
     *
     * @implNote WAWebApiPendingDeviceSync.doPendingDeviceSync: called during
     * RESUME_WITH_OPEN_TAB to retry pending device syncs.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiPendingDeviceSync",
            exports = "doPendingDeviceSync",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * @implNote WAWebSyncdStoreMissingKeys.updateMissingKeyDevices: when companion devices
     * are removed, update missing sync key entries to remove the device from tracking.
     * If all devices report missing, triggers SyncdFatalErrorType.MISSING_KEY_ON_ALL_CLIENTS.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdStoreMissingKeys",
            exports = "updateMissingKeyDevices",
            adaptation = WhatsAppAdaptation.ADAPTED)
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

                // Per WhatsApp Web: when all remaining devices have responded
                // without the key after a device removal, schedule the grace
                // period check before declaring a fatal missing key error.
                if (missingKey.isMissingOnAllDevices()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "All devices responded without missing sync key, scheduling grace period check");
                    webAppStateService.scheduleAllDevicesRespondedCheck();
                }
            }
        });
    }

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
     * @implNote WAWebSendUserMsgJob.encryptAndSendUserMsg: calls
     * getFanOutList({wids: [to, meDevice],
     * chatWidSetToIncludeHostedInFanoutOneToOneChatOnly: to}).
     * WAWebDBDeviceListFanout.getFanOutList: filters self, includes hosted
     * for 1:1 user chats when bizHostedDevicesEnabled.
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
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

    /**
     * Computes the fanout device list and participant hash for a group chat.
     *
     * <p>The returned {@link DeviceGroupFanoutResult} contains the target
     * devices (excluding the sender) and the phash computed over those
     * devices <em>plus</em> the sender's own device JID, matching the
     * WA Web pattern {@code phashV2([].concat(M, [F]))}.
     *
     * @param groupJid the group JID
     * @param senderDeviceJid the sender's own device JID to include in the phash
     *                        but <em>not</em> in the returned device list
     * @return the fanout result with devices and phash
     *
     * @implNote WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg: computes
     * {@code phashV2([].concat(M, [F]))} where M is the device list (excluding
     * sender) and F is the sender's own device JID
     * ({@code isCagAddon || isLidAddressingMode ? getMeDeviceLidOrThrow() : getMeDevicePnOrThrow_DO_NOT_USE()}).
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = "phashV2",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid senderDeviceJid) {
        var myDeviceJid = resolveMyDeviceJid(groupJid);
        try {
            // WAWebDBDeviceListFanout.getFanOutList: get devices for all participants
            var metadata = client.queryChatMetadata(groupJid);
            var participants = metadata.participants()
                    .stream()
                    .map(entry -> entry.userJid())
                    .toList();
            var deviceLists = getDeviceLists(participants, "message", null, false);

            // WAWebDBDeviceListFanout.getFanOutList: group messages don't include hosted devices
            var fanoutDevices = fanoutCalculator.calculate(myDeviceJid, deviceLists, null);

            // Filter out devices with unconfirmed identity changes
            var changedIdentities = store.unconfirmedIdentityChanges();
            var filteredDevices = fanoutCalculator.filterIdentityChanges(fanoutDevices, changedIdentities);

            // WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg: phash is computed on
            // [].concat(M, [F]) where M = filteredDevices and F = sender's own device JID
            var phashDevices = new java.util.HashSet<>(filteredDevices);
            phashDevices.add(senderDeviceJid); // WAWebSendGroupSkmsgJob: concat(M, [F])
            var phash = phashCalculator.calculate(phashDevices, DevicePhashVersion.V2, true);
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
     * @implNote WAWebIdentityIcdcApi.getICDCMeta: retrieves the device record
     * and delegates to {@code getICDCMetaFromDeviceRecord}.
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMeta",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * @return the number of devices in the server response for which the one-time
     *         pre-key pool was depleted (no {@code <key>} element returned for a
     *         non-bot device); callers should pass this count to
     *         {@link com.github.auties00.cobalt.wam.WamService#commit} via
     *         {@link com.github.auties00.cobalt.wam.event.PrekeysDepletionEventBuilder}
     *         to mirror {@code WAWebPostPrekeysDepletionMetric.maybePostPrekeysDepletionMetric}.
     *
     * @implNote WAWebSendMsgCreateFanoutStanza.createFanoutMsgStanza:
     * calls {@code ensureE2ESessions(devices)} before encrypting.
     * WAWebManageE2ESessionsJob.ensureE2ESessions: deduplicates concurrent session
     * establishment requests and returns the depleted pre-key count.
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public int ensureSessions(Collection<Jid> deviceJids) {
        return preKeyHandler.ensureSessions(deviceJids);
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
     * @implNote WAWebSendUserMsgJob: uses getMeDeviceLid for LID chats,
     * getMeDevicePn otherwise.
     * WAWebSendGroupSkmsgJob: uses getMeDeviceLid for LID groups,
     * getMeDevicePn otherwise.
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser",
            exports = {"getMeDeviceLid", "getMeDevicePnOrThrow_DO_NOT_USE"},
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
     * Checks if business hosted devices feature is enabled.
     *
     * @return {@code true} if hosted devices are enabled
     *
     * @implNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: returns
     * getABPropConfigValue("adv_accept_hosted_devices"), defaulting to false.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "bizHostedDevicesEnabled",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * @implNote WAWebAdvHandlerApi.handleADVDeviceSyncResult: when bizHostedDevicesEnabled
     * is false, filters out devices where id === HOSTED_DEVICE_ID (99).
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
     * Handles a device notification (add or remove).
     *
     * <p>Device notifications are push updates from the server when a user adds or removes
     * companion devices. These allow updating the local device cache without polling.
     *
     * @param node    the notification node containing device-list and key-index-list
     * @param action  the action type ("add" or "remove")
     * @param userJid the user JID whose device list changed
     *
     * @implNote WAWebHandleAdvDeviceNotificationApi.handleDeviceNotification: dispatches to
     * handleDeviceAddNotification or handleDeviceRemoveNotification based on action type.
     * Uses device update lock to serialize table updates.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = {"handleDeviceAddNotification", "handleDeviceRemoveNotification"},
            adaptation = WhatsAppAdaptation.ADAPTED)
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
                case "remove" -> handleDeviceRemoveNotification(userJid, deviceListNode, keyIndexListNode.get(), timestamp);
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
     * @implNote WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification: if no cached
     * record exists or it's deleted, triggers USync via triggerUsyncForCoexDeviceAdd.
     * Otherwise validates and merges devices following WAWebHandleAdvKeyIndexResultApi logic.
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

        // WAWebHandleAdvDeviceNotificationApi: if (r < l.timestamp || i == null) return null
        // i = signedKeyIndexBytes; when null for add notification, skip update
        var signedKeyIndexBytes = keyIndexListNode.toContentBytes();
        if (signedKeyIndexBytes.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Device add notification missing signedKeyIndexBytes for {0}, ignoring", userJid);
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

        // WAWebHandleAdvDeviceNotificationApi: build key index map from notification devices
        // The device children (with jid and key-index attrs) are under device-list, not key-index-list
        var keyIndexMap = buildKeyIndexMap(deviceListNode);

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

    /**
     * Parses and validates a single device entry from an incoming add notification.
     *
     * <p>For notification-sourced devices, only keyIndexes that appear in the
     * cryptographically signed {@code validIndexes} set are accepted; the
     * "keyIndex &gt; currentIndex" allowance that applies to cached devices does not apply
     * here. Hosted devices (id 99) are additionally gated by {@code bizHostedDevicesEnabled}.
     *
     * @param deviceNode             the device child node
     * @param keyIndexMap            map of device id to keyIndex from the key-index-list
     * @param validatedKeyIndexInfo  the validated key index list, or {@code null}
     * @return a single-element stream with the parsed device, or empty when rejected
     * @implNote WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification: filters
     * notification devices against {@code validIndexes} and rejects anything outside that
     * signed set, unlike cached-device filtering which also allows {@code keyIndex > currentIndex}.
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
     * @param deviceListNode   the device-list node containing devices being removed
     * @param keyIndexListNode the key-index-list node from the notification
     * @param timestamp        the notification timestamp (Unix seconds)
     *
     * @implNote WAWebHandleAdvDeviceNotificationApi.handleDeviceRemoveNotification: the
     * notification contains devices being REMOVED. Filter logic at line:
     * {@code n.devices.filter(e => e.id !== DEFAULT_DEVICE_ID && (t=r.get(e.id), t==null || t!==e.keyIndex))}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = "handleDeviceRemoveNotification",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void handleDeviceRemoveNotification(Jid userJid, Node deviceListNode, Node keyIndexListNode, long timestamp) {
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
        // Device info (jid, key-index) comes from the notification's device children
        var removedDevicesMap = deviceListNode != null
                ? buildKeyIndexMap(deviceListNode)
                : Map.<Integer, Integer>of();

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
     * @implNote WAWebHandleAdvDeviceNotificationApi: builds map from notification devices
     * using {@code new Map(e.map(e => [e.id, e.keyIndex]))}.
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
     * Parses a device child node into a {@code (deviceId, keyIndex)} entry.
     *
     * <p>Returns an empty stream when the JID attribute is missing or the {@code key-index}
     * attribute cannot be parsed, so callers can {@code flatMap} without explicit null handling.
     *
     * @param deviceNode the device child node
     * @return a single-element stream with the parsed entry, or empty when invalid
     * @implNote WAWebHandleAdvDeviceNotificationApi: uses
     * {@code new Map(devices.map(e => [e.id, e.keyIndex]))} to build the same mapping inline.
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
     * Validates and extracts key index list data from the signed protobuf.
     *
     * @param keyIndexListNode the key-index-list node from the notification
     * @return the validated key index list data, or {@code null} if validation fails
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.decodeSignedKeyIndexBytes: validates
     * Curve25519 signature using embedded or stored accountSignatureKey.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "decodeSignedKeyIndexBytes",
            adaptation = WhatsAppAdaptation.DIRECT)
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
     * @implNote WAWebBizCoexUtils.triggerUsyncForCoexDeviceAdd: checks resumeFromRestartComplete
     * flag to determine whether to trigger immediate sync or queue for doPendingDeviceSync.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "triggerUsyncForCoexDeviceAdd",
            adaptation = WhatsAppAdaptation.DIRECT)
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

    /**
     * Checks whether a device-list node contains the hosted device sentinel (id 99).
     *
     * @param deviceListNode the device-list node
     * @return {@code true} if any child device carries id equal to {@link DeviceConstants#HOSTED_DEVICE_ID}
     * @implNote WAWebBizCoexUtils: used by {@code triggerUsyncForCoexDeviceAdd} to route
     * hosted-device notifications.
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
     *
     * <p>This is equivalent to WhatsApp Web's "sync my device list" operation which ensures
     * the local device cache is up-to-date for the current user's own devices. It requests
     * device lists for both the phone number (PN) JID and the linked ID (LID) JID.
     *
     * @implNote WAWebAdvSyncDeviceListApi.syncMyDeviceList: calls syncDeviceList with
     * {@code wids: getMePNandLIDWids(), context: null, phash: null}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncMyDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void syncMyDeviceList() {
        // WAWebAdvSyncDeviceListApi.syncMyDeviceList: getMePNandLIDWids returns both PN and LID JIDs
        var myJids = new ArrayList<Jid>();
        store.jid().ifPresent(jid -> myJids.add(jid.toUserJid()));
        store.lid().ifPresent(myJids::add);
        if (myJids.isEmpty()) {
            return;
        }
        // WAWebAdvSyncDeviceListApi.syncMyDeviceList: context=null, phash=null
        getDeviceLists(myJids, null, null, false);
    }

    /**
     * Synchronises and returns device lists for the specified user JIDs.
     *
     * <p>First performs a device list sync (equivalent to {@link #getDeviceLists}), then
     * returns the cached device ID information for the requested JIDs.
     *
     * @param userJids the user JIDs to sync and retrieve device lists for
     * @return the device lists, one per user JID; entries may be {@code null} if no device list is cached
     *
     * @implNote WAWebAdvSyncDeviceListApi.syncAndGetDeviceList: calls
     * {@code syncDeviceList({wids, context: null, phash: null})} then returns
     * {@code getDeviceIds(wids)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncAndGetDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<DeviceList> syncAndGetDeviceList(Collection<Jid> userJids) {
        // WAWebAdvSyncDeviceListApi.syncAndGetDeviceList: yield syncDeviceList({wids, context:null, phash:null})
        getDeviceLists(userJids, null, null, false);
        // WAWebAdvSyncDeviceListApi.syncAndGetDeviceList: return yield getDeviceIds(wids)
        return getDeviceIds(userJids, false);
    }

    /**
     * Returns the cached device record for a user.
     *
     * <p>Looks up the user's device list in the in-memory cache; the cache is
     * shared with the rest of the device service and is sized for {@code 5000}
     * entries with LRU eviction. The accessor also fires a
     * {@code checkPnToLidMapping} diagnostic for the requested JID, mirroring
     * WA Web's behaviour, so that missing PN/LID mappings are reported.
     *
     * <p>WA Web reads from the {@code device-list} IndexedDB table when the
     * record is not yet cached. Cobalt has no IDB equivalent: persisted device
     * lists live in the same {@link WhatsAppStore} that backs the cache, so
     * the lookup is a single store call.
     *
     * @param userJid the user JID; only the user portion is significant
     * @return an {@link Optional} holding the cached record when present,
     *         otherwise {@link Optional#empty()}
     *
     * @implNote ADAPTED: WAWebApiDeviceList.getDeviceRecord: WA Web's
     * implementation backs the LRU with an IDB read (Promise resolving to
     * the record). Cobalt collapses cache and persistence into
     * {@link WhatsAppStore#findDeviceList(Jid)}, so the asynchronous
     * IDB-fallback path is not needed. The {@code checkPnToLidMapping}
     * diagnostic side effect is preserved verbatim.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<DeviceList> getDeviceRecord(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        // WAWebApiDeviceList.getDeviceRecord: lookup record from cache (or load via IDB in WA Web)
        var record = store.findDeviceList(userJid);
        // WAWebApiDeviceList.getDeviceRecord:
        //   d.get(r)!=null && checkPnToLidMapping([e], WAWEB_API_DEVICE_LIST_GET_DEVICE_RECORD)
        // After the IDB put d.get(r) is the cached Promise — never null — so the check fires
        // unconditionally. Cobalt fires the diagnostic on every call to mirror that.
        checkPnToLidMapping(List.of(userJid), "waweb_api_device_list_get_device_record");
        return record;
    }

    /**
     * Returns the cached device records for a collection of users.
     *
     * <p>Returns one entry per input JID, in the same order as the input. Slots
     * for which no record is cached or persisted are returned as {@code null}.
     * Fires a single {@code checkPnToLidMapping} diagnostic over the JIDs that
     * resolved to non-null records, mirroring WA Web's behaviour.
     *
     * @param userJids the user JIDs to look up
     * @return one record per input JID, in the same order; missing entries are
     *         {@code null}
     *
     * @implNote ADAPTED: WAWebApiDeviceList.bulkGetDeviceRecord: WA Web fans
     * out an IDB {@code bulkGet} for the keys not yet in the LRU and stores
     * Promise-wrapped results back into the LRU. Cobalt's
     * {@link WhatsAppStore} keeps the cache and persistence collapsed, so the
     * IDB step is unnecessary. The {@code checkPnToLidMapping} diagnostic
     * side effect is preserved verbatim, including its filter to JIDs whose
     * record resolved to non-null.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "bulkGetDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<DeviceList> bulkGetDeviceRecord(Collection<Jid> userJids) {
        Objects.requireNonNull(userJids, "userJids cannot be null");

        // WAWebApiDeviceList.bulkGetDeviceRecord: read each cached/persisted record in input order
        var records = new ArrayList<DeviceList>(userJids.size());
        for (var jid : userJids) {
            records.add(store.findDeviceList(jid).orElse(null));
        }

        // WAWebApiDeviceList.bulkGetDeviceRecord:
        // var a=e.filter(function(e){return d.get(createDeviceListPK(e))!=null});
        // a.length>0 && checkPnToLidMapping(a, WAWEB_API_DEVICE_LIST_BULK_GET_DEVICE_RECORD)
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
     * Persists a device record and refreshes the cache entry.
     *
     * <p>Fires a {@code checkPnToLidMapping} diagnostic for the record's owner,
     * stores the record through the underlying {@link WhatsAppStore}, and emits
     * a warning when the record is being saved as a tombstone for the current
     * user's account.
     *
     * @param record the device list to persist; the {@link DeviceList#userJid()}
     *               is used as the cache key
     *
     * @implNote ADAPTED: WAWebApiDeviceList.createOrReplaceDeviceRecord: WA Web
     * writes the record through {@code getDeviceListTable().createOrReplace(e)}
     * and stores a {@code Promise.resolve(e)} in the LRU. Cobalt persists
     * through {@link WhatsAppStore#addDeviceList(DeviceList)} which already
     * subsumes both steps. The {@code checkPnToLidMapping} diagnostic and the
     * "syncd: trying to delete own device list" warning fired by the JS
     * helper {@code f(t)} are preserved verbatim.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "createOrReplaceDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void createOrReplaceDeviceRecord(DeviceList record) {
        Objects.requireNonNull(record, "record cannot be null");

        // WAWebApiDeviceList.createOrReplaceDeviceRecord:
        // checkPnToLidMapping([createUserWidFromDeviceListPk(e.id)], WAWEB_API_DEVICE_LIST_CREATE_OR_REPLACE_DEVICE_RECORD)
        checkPnToLidMapping(List.of(record.userJid()), "waweb_api_device_list_create_or_replace_device_record");

        // WAWebApiDeviceList.createOrReplaceDeviceRecord:
        // yield getDeviceListTable().createOrReplace(e); d.put(e.id, Promise.resolve(e))
        store.addDeviceList(record);

        // WAWebApiDeviceList.f: trying to delete own device list warning
        warnIfDeletingOwnDeviceList(record);
    }

    /**
     * Persists a batch of device records and refreshes their cache entries.
     *
     * <p>Fires a single {@code checkPnToLidMapping} diagnostic over every
     * record's owner, persists each record through {@link WhatsAppStore}, and
     * emits a per-record warning when a record is being saved as a tombstone
     * for the current user's account.
     *
     * @param records the device lists to persist
     *
     * @implNote ADAPTED: WAWebApiDeviceList.bulkCreateOrReplaceDeviceRecord:
     * WA Web writes through {@code getDeviceListTable().bulkCreateOrReplace(e)}
     * and pushes Promise-wrapped records into the LRU. Cobalt collapses both
     * steps into {@link WhatsAppStore#addDeviceList(DeviceList)} per record.
     * The {@code checkPnToLidMapping} diagnostic and the per-record
     * "syncd: trying to delete own device list" warning fired by
     * {@code f(t)} are preserved verbatim.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "bulkCreateOrReplaceDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void bulkCreateOrReplaceDeviceRecord(Collection<DeviceList> records) {
        Objects.requireNonNull(records, "records cannot be null");

        // WAWebApiDeviceList.bulkCreateOrReplaceDeviceRecord:
        // checkPnToLidMapping(e.map(e => createUserWidFromDeviceListPk(e.id)), WAWEB_API_DEVICE_LIST_BULK_CREATE_OR_REPLACE_DEVICE_RECORD)
        var ownerJids = records.stream()
                .map(DeviceList::userJid)
                .toList();
        checkPnToLidMapping(ownerJids, "waweb_api_device_list_bulk_create_or_replace_device_record");

        // WAWebApiDeviceList.bulkCreateOrReplaceDeviceRecord:
        // yield bulkCreateOrReplace(e); e.forEach(e => { d.put(e.id, Promise.resolve(e)); f(e); })
        for (var record : records) {
            store.addDeviceList(record);
            warnIfDeletingOwnDeviceList(record);
        }
    }

    /**
     * Returns the cached device records for a collection of users, optionally
     * folding in alternate-identity device lists.
     *
     * <p>Each output slot mirrors the corresponding input JID. When no record
     * is cached for an input JID, the slot is {@code null}. When
     * {@code shouldMergeAltDevices} is {@code true}, the alternate device list
     * (PN for a LID input, or LID for a PN input) is also fetched and its
     * devices are appended to the primary slot if a primary record exists, or
     * its content replaces the slot when the primary record is missing.
     *
     * <p>Unlike WA Web's projection, Cobalt returns the full {@link DeviceList}
     * record rather than a stripped {@code {id, devices: [{id, isHosted}]}}
     * shape: callers in Cobalt that only need device ids work directly with
     * {@link DeviceList#devices()}.
     *
     * @param userJids              the user JIDs to look up; processed in order
     * @param shouldMergeAltDevices whether alternate-identity records should be
     *                              merged into the result
     * @return one record per input JID, in input order; missing or deleted
     *         entries are {@code null}
     *
     * @implNote ADAPTED: WAWebApiDeviceList.getDeviceIds: WA Web returns a
     * stripped projection with only {@code {id, devices: [{id, isHosted}]}}.
     * Cobalt returns the full {@link DeviceList} for downstream callers; the
     * input ordering, the {@code null} slots for missing/deleted records, and
     * the merge behaviour with alternate-identity records are preserved
     * verbatim. WA Web mutates {@code u.devices} in place when merging;
     * Cobalt rebuilds an immutable {@link DeviceList} via the existing
     * {@link DeviceList#merge(DeviceList)} helper.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceIds",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<DeviceList> getDeviceIds(Collection<Jid> userJids, boolean shouldMergeAltDevices) {
        Objects.requireNonNull(userJids, "userJids cannot be null");

        // WAWebApiDeviceList.getDeviceIds: var n=Array.from(yield bulkGetDeviceRecord(e))
        var records = new ArrayList<>(bulkGetDeviceRecord(userJids));

        if (shouldMergeAltDevices) {
            // WAWebApiDeviceList.getDeviceIds: var r=new Map; n.forEach(e => e!=null && r.set(e.id, e))
            var byOwner = new HashMap<Jid, DeviceList>();
            for (var record : records) {
                if (record != null) {
                    byOwner.put(record.userJid(), record);
                }
            }

            // WAWebApiDeviceList.getDeviceIds: var a=new Map; e.forEach((e,t) => a.set(e.toString(),t))
            var positionByJid = new HashMap<Jid, Integer>();
            var pos = 0;
            for (var jid : userJids) {
                positionByJid.putIfAbsent(jid, pos);
                pos++;
            }

            // WAWebApiDeviceList.getDeviceIds: var i=e.reduce(...) — alternate JIDs for user JIDs only
            var alternateJids = new ArrayList<Jid>();
            for (var jid : userJids) {
                if (!jid.isUser()) {
                    continue;
                }
                var alternate = findAlternateUserWid(jid.toUserJid());
                if (alternate != null) {
                    alternateJids.add(alternate);
                }
            }

            // WAWebApiDeviceList.getDeviceIds: var l=yield bulkGetDeviceRecord(i)
            var alternateRecords = bulkGetDeviceRecord(alternateJids);

            // WAWebApiDeviceList.getDeviceIds: l.forEach((e,t) => { if(!(!e||e.deleted)) ... })
            for (var t = 0; t < alternateRecords.size(); t++) {
                var altRecord = alternateRecords.get(t);
                if (altRecord == null || altRecord.deleted()) {
                    continue;
                }
                var altJid = alternateJids.get(t);
                // WAWebApiDeviceList.getDeviceIds: var s=getAlternateUserWid(altJid)
                var originalJid = findAlternateUserWid(altJid.toUserJid());
                if (originalJid == null) {
                    continue;
                }
                var primary = byOwner.get(originalJid);
                if (primary != null) {
                    // WAWebApiDeviceList.getDeviceIds: append missing devices into primary's record
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
                            // Replace in-place at every input slot referring to originalJid
                            var p = positionByJid.get(originalJid);
                            if (p != null) {
                                records.set(p, merged);
                            }
                        }
                    }
                } else {
                    // WAWebApiDeviceList.getDeviceIds: replace slot with synthesised record carrying alt's data
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

        // WAWebApiDeviceList.getDeviceIds: n.map(e => e&&!e.deleted ? {id, devices:[{id,isHosted}]} : null)
        // Cobalt returns the full record but tombstones deleted entries to null to match the JS shape.
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
     * Returns the cached device records for a collection of users, projected
     * to the fields needed for sync.
     *
     * <p>WA Web returns a lighter projection containing only the device id,
     * the device entries (each stripped to id+isHosted), the record timestamp
     * and the expected timestamp. Cobalt returns the full {@link DeviceList}
     * because the same set of fields is already addressable on the record.
     *
     * @param userJids the user JIDs to look up; processed in order
     * @return one record per input JID, in input order; missing or deleted
     *         entries are {@code null}
     *
     * @implNote ADAPTED: WAWebApiDeviceList.getDeviceInfoForSync: WA Web
     * returns {@code {id, devices: [{id, isHosted}], timestamp, expectedTs}}
     * objects. Cobalt returns the full {@link DeviceList} so callers can read
     * any of those fields directly through the existing accessors.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceInfoForSync",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<DeviceList> getDeviceInfoForSync(Collection<Jid> userJids) {
        Objects.requireNonNull(userJids, "userJids cannot be null");
        // WAWebApiDeviceList.getDeviceInfoForSync: var t=yield bulkGetDeviceRecord(e); return t.map(...)
        var records = bulkGetDeviceRecord(userJids);
        var result = new ArrayList<DeviceList>(records.size());
        for (var record : records) {
            // WAWebApiDeviceList.getDeviceInfoForSync: e&&!e.deleted ? projection : null
            if (record == null || record.deleted()) {
                result.add(null);
            } else {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * Emits a warning when a deleted device list belongs to the current user's
     * account.
     *
     * <p>Mirrors WA Web's helper {@code f(t)}: when persisting a record that is
     * marked as deleted, if the owner JID matches the logged-in user's PN or
     * LID, log a diagnostic so that accidental own-device-list deletions are
     * surfaced.
     *
     * @param record the device record about to be persisted
     *
     * @implNote WAWebApiDeviceList.f (file-private helper invoked from
     * {@code createOrReplaceDeviceRecord} and {@code bulkCreateOrReplaceDeviceRecord}):
     * {@code if(t.deleted){var n=createUserWidFromDeviceListPk(t.id);
     * isMeAccount(n)&&LOG.WARN("syncd: trying to delete own device list")}}.
     * The PK-to-Wid step is folded into a direct comparison because Cobalt
     * already addresses records by user-level {@link Jid}.
     */
    private void warnIfDeletingOwnDeviceList(DeviceList record) {
        if (!record.deleted()) {
            return;
        }
        // WAWebApiDeviceList.f: var n=createUserWidFromDeviceListPk(t.id) — owner JID
        var owner = record.userJid();
        // WAWebUserPrefsMeUser.isMeAccount: own PN or own LID
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
     * Returns the alternate-identity user JID for the given JID by consulting
     * the store's PN-to-LID and LID-to-PN mappings.
     *
     * <p>Local helper that mirrors the semantics of
     * {@code WAWebApiContact.getAlternateUserWid} as needed by
     * {@link #getDeviceIds(Collection, boolean)}; the canonical mapping for
     * that export lives in {@code LidMigrationService.getAlternateUserWid}.
     * This copy is kept private to avoid coupling the device service to the
     * migration service for what is otherwise a pure store lookup.
     *
     * @param userJid the user JID whose alternate identity should be resolved
     * @return the alternate user JID, or {@code null} if none is mapped
     *
     * @implNote ADAPTED: WAWebApiContact.getAlternateUserWid (canonical
     * mapping in {@code LidMigrationService}). Returns the PN for a LID input
     * and the LID for a PN input, with me-user fast paths through
     * {@link WhatsAppStore#jid()} and {@link WhatsAppStore#lid()}.
     */
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
     * Checks whether a user has a specific device ID in their device list.
     *
     * <p>If the requested device ID is the primary device (device 0), returns {@code true}
     * immediately since every user has a primary device. Otherwise, looks up the cached
     * device list and checks if any device matches the requested ID.
     *
     * @param userJid  the user JID to check
     * @param deviceId the device ID to look for
     * @return {@code true} if the device exists in the user's device list
     *
     * @implNote ADAPTED: WAWebApiDeviceList.hasDevice: if
     * {@code deviceId === DEFAULT_DEVICE_ID} returns {@code true} immediately.
     * Otherwise WA calls {@code getDeviceIds([userJid])} and checks if any
     * device in the result matches; Cobalt skips the projection step and
     * reads the cached {@link DeviceList} directly through
     * {@link WhatsAppStore#findDeviceList(Jid)}, which is the underlying
     * cache that WA's {@code getDeviceIds} would have queried.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "hasDevice",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasDevice(Jid userJid, int deviceId) {
        // WAWebApiDeviceList.hasDevice: primary device always exists
        if (deviceId == DeviceConstants.PRIMARY_DEVICE_ID) {
            return true;
        }
        // WAWebApiDeviceList.hasDevice: check cached device list (WA: getDeviceIds([userJid])[0])
        var deviceList = store.findDeviceList(userJid).orElse(null);
        if (deviceList == null || deviceList.deleted()) {
            return false;
        }
        // WAWebApiDeviceList.hasDevice: r.devices.some(e => e.id === t)
        return deviceList.devices().stream()
                .anyMatch(device -> device.id() == deviceId);
    }

    /**
     * Returns the device list for the current user's PN identity.
     *
     * <p>Throws if no device list is found for the current user, which indicates
     * a critical synchronisation failure.
     *
     * @return the current user's device list
     * @throws IllegalStateException if the device list cannot be found
     *
     * @implNote WAWebApiDeviceList.getMyDeviceList: calls
     * {@code getDeviceRecord(getMeDevicePnOrThrow_DO_NOT_USE())}. If the PN
     * record is missing or deleted, falls back to
     * {@code getDeviceRecord(getMeDeviceLidOrThrow())}. Throws with the JS
     * diagnostic line if both records are missing or deleted.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getMyDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public DeviceList getMyDeviceList() {
        // WAWebApiDeviceList.getMyDeviceList: var e=getMeDevicePnOrThrow_DO_NOT_USE(); var t=yield getDeviceRecord(e)
        var pnJid = store.jid().map(Jid::toUserJid).orElse(null);
        DeviceList pnRecord = null;
        if (pnJid != null) {
            pnRecord = getDeviceRecord(pnJid).orElse(null);
            if (pnRecord != null && !pnRecord.deleted()) {
                return pnRecord;
            }
        }

        // WAWebApiDeviceList.getMyDeviceList:
        // if(!t||t.deleted){var n=getMeDeviceLidOrThrow(); var a=yield getDeviceRecord(n); ...}
        var lidJid = store.lid().orElse(null);
        DeviceList lidRecord = null;
        if (lidJid != null) {
            lidRecord = getDeviceRecord(lidJid).orElse(null);
            if (lidRecord != null && !lidRecord.deleted()) {
                return lidRecord;
            }
        }

        // WAWebApiDeviceList.getMyDeviceList:
        // var i=t!=null, l=t?.deleted===true, u=a!=null, c=a?.deleted===true
        // LOG("[syncd] no device list pn=%s/%s lid=%s/%s", i, l, u, c)
        var hasPn = pnRecord != null;
        var isPnDeleted = pnRecord != null && pnRecord.deleted();
        var hasLid = lidRecord != null;
        var isLidDeleted = lidRecord != null && lidRecord.deleted();
        LOGGER.log(System.Logger.Level.WARNING,
                "[syncd] no device list pn={0}/{1} lid={2}/{3}",
                hasPn, isPnDeleted, hasLid, isLidDeleted);
        // WAWebApiDeviceList.getMyDeviceList: throw err("syncd: cannot find my device list")
        throw new IllegalStateException("syncd: cannot find my device list");
    }

    /**
     * Returns all device lists currently stored in the cache.
     *
     * <p>Logs a diagnostic line carrying the number of records and the time
     * taken, mirroring WA Web's
     * {@code "getAllDeviceLists: got %s devices, took %sms"} log.
     *
     * @return an unmodifiable collection of all cached device lists
     *
     * @implNote ADAPTED: WAWebApiDeviceList.getAllDeviceLists: WA Web reads
     * the IDB-backed table via {@code getDeviceListTable().all()}; Cobalt
     * reads from the {@link WhatsAppStore} which collapses cache and
     * persistence. The timing log is preserved verbatim.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getAllDeviceLists",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Collection<DeviceList> getAllDeviceLists() {
        // WAWebApiDeviceList.getAllDeviceLists: var e=self.performance.now()
        var start = System.nanoTime();
        // WAWebApiDeviceList.getAllDeviceLists: var t=yield getDeviceListTable().all()
        var lists = store.deviceLists();
        // WAWebApiDeviceList.getAllDeviceLists:
        // LOG("getAllDeviceLists: got %s devices, took %sms", t.length, Math.round(performance.now()-e))
        var elapsedMs = Math.round((System.nanoTime() - start) / 1_000_000.0);
        LOGGER.log(System.Logger.Level.DEBUG,
                "getAllDeviceLists: got {0} devices, took {1}ms",
                lists.size(), elapsedMs);
        return lists;
    }

    /**
     * Handles ICDC (Identity Change Detection Consistency) data from an incoming message.
     *
     * <p>Processes the device list metadata from a message's context info to update expected
     * timestamps for device records. This allows detecting stale device lists without requiring
     * a full device sync.
     *
     * <p>For messages from the primary device (device 0) with a sender timestamp but no sender
     * key hash, performs a minimal ADV sync to update the device record timestamp.
     *
     * @param senderJid        the sender's JID (with device information)
     * @param recipientUserJid the recipient user JID for 1:1 chats, or {@code null} for group chats
     * @param metadata         the device list metadata from the message's context info
     *
     * @implNote WAWebIcdcHandlerApi.handleICDCData: enqueues processing via a PromiseQueue.
     * Handles two cases: (1) primary device with timestamp-only metadata triggers a minimal
     * ADV sync result, (2) normal case updates expectedTs for both sender and recipient records.
     */
    @WhatsAppWebExport(moduleName = "WAWebIcdcHandlerApi",
            exports = "handleICDCData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handleICDCData(Jid senderJid, Jid recipientUserJid, DeviceListMetadata metadata) {
        // WAWebIcdcHandlerApi.handleICDCData: if metadata is null, return
        if (metadata == null) {
            return;
        }

        // WAWebIcdcHandlerApi.handleICDCData: check for primary device with timestamp-only update
        // (e.device==null || e.device===DEFAULT_DEVICE_ID) && r.senderTimestamp!=null && r.senderKeyHash==null
        if ((senderJid.device() == DeviceConstants.PRIMARY_DEVICE_ID)
                && metadata.senderTimestamp().isPresent()
                && metadata.senderKeyHash().isEmpty()) {
            // WAWebIcdcHandlerApi.handleICDCData: minimal ADV sync with timestamp+1
            var senderTimestamp = metadata.senderTimestamp().get();
            var adjustedTimestamp = senderTimestamp.plusSeconds(1);
            // WAWebHandleAdvForUsyncApi.handleADVSyncResult: process with primary-only device list
            handleMinimalTimestampOnlySync(senderJid.toUserJid(), adjustedTimestamp);
            return;
        }

        // WAWebIcdcHandlerApi.handleICDCData: build list of entries to check
        var entries = new ArrayList<IcdcTimestampEntry>();
        entries.add(new IcdcTimestampEntry(senderJid.toUserJid(), metadata.senderTimestamp().orElse(null)));

        // WAWebIcdcHandlerApi.handleICDCData: add recipient entry if sender is self and recipient is present
        var myUser = store.jid().map(jid -> jid.user()).orElse(null);
        var isSelf = myUser != null && senderJid.user().equals(myUser);
        if (isSelf && recipientUserJid != null) {
            entries.add(new IcdcTimestampEntry(recipientUserJid, metadata.recipientTimestamp().orElse(null)));
        }

        // WAWebIcdcHandlerApi.handleICDCData: get last ADV check time
        var lastADVCheckTime = store.lastAdvCheckTime().orElse(null);

        // WAWebIcdcHandlerApi.handleICDCData: bulk get device records and compute expected timestamps
        var updatedRecords = new ArrayList<DeviceList>();
        for (var entry : entries) {
            if (entry.timestamp() == null) {
                continue;
            }

            var cached = store.findDeviceList(entry.jid()).orElse(null);
            if (cached == null || cached.deleted()) {
                continue;
            }

            // WAWebAdvExpectedTsApi.computeExpectedTsForDeviceRecord: compute new expected timestamp
            var computed = DeviceExpectedTsUtils.computeExpectedTimestampForDeviceRecord(
                    entry.timestamp(), cached, lastADVCheckTime);

            // WAWebIcdcHandlerApi.handleICDCData: only update if any tracking field changed
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

        // WAWebApiDeviceList.bulkCreateOrReplaceDeviceRecord: persist updated records
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
     * Handles a minimal timestamp-only sync for ICDC primary device updates.
     *
     * <p>When a message from the primary device contains only a sender timestamp
     * (no key hash), this creates a minimal device update with just the primary
     * device and the adjusted timestamp. This is used for identity update detection.
     *
     * @param userJid           the user JID to update
     * @param adjustedTimestamp the sender timestamp + 1 second
     *
     * @implNote WAWebIcdcHandlerApi.handleICDCData: calls handleADVSyncResult with a minimal
     * device list containing only the primary device and the adjusted timestamp.
     */
    @WhatsAppWebExport(moduleName = "WAWebIcdcHandlerApi",
            exports = "handleICDCData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleMinimalTimestampOnlySync(Jid userJid, Instant adjustedTimestamp) {
        // WAWebIcdcHandlerApi.handleICDCData: handleADVSyncResult(e, {deviceList: [{id: 0, keyIndex: 0}],
        // keyIndex: {ts: castToUnixTime(senderTimestamp+1), signedKeyIndexBytes: null}}, null, null)
        var cachedList = store.findDeviceList(userJid).orElse(null);

        // WAWebHandleAdvForUsyncApi.handleADVSyncResultSync: when signedKeyIndexBytes is null
        // and no companion devices, falls through to handleOmittedResult
        // WAWebHandleAdvOmittedResultApi.handleOmittedResult: update timestamp
        if (cachedList == null || cachedList.deleted()) {
            return;
        }

        if (adjustedTimestamp.isBefore(cachedList.timestamp())) {
            return;
        }

        var lastADVCheckTime = store.lastAdvCheckTime().orElse(null);

        // WAWebHandleAdvOmittedResultApi: update timestamp and check expectedTs clearing
        var finalExpectedTs = cachedList.expectedTimestamp();
        var finalUpdateTs = cachedList.expectedTimestampUpdateTimestamp();
        var finalLastJobTs = cachedList.expectedTimestampLastDeviceJobTimestamp();

        if (DeviceExpectedTsUtils.shouldClearExpectedTimestamp(
                adjustedTimestamp, null, cachedList, lastADVCheckTime)) {
            finalExpectedTs = null;
            finalUpdateTs = null;
            finalLastJobTs = null;
        }

        // WAWebHandleAdvOmittedResultApi: reset to primary only
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
     * Internal entry for ICDC timestamp processing.
     *
     * @implNote WAWebIcdcHandlerApi.handleICDCData: constructs entries with
     * {@code {id: senderJid, ts: senderTimestamp}} and optionally
     * {@code {id: recipientJid, ts: recipientTimestamp}}.
     */
    private record IcdcTimestampEntry(Jid jid, Instant timestamp) {
    }

    /**
     * Handles hosted ICDC metadata inline during message processing.
     *
     * <p>Processes the device list metadata from an incoming message to detect whether
     * the sender or recipient account type is HOSTED (business coexistence). When a
     * HOSTED transition is detected, clears the existing device record and triggers
     * a device sync to get the updated device list.
     *
     * @param chatJid   the chat JID (must be a user chat, not group)
     * @param authorJid the message author JID
     * @param metadata  the device list metadata from the message's context info
     * @return the result indicating whether hosted mismatch was detected
     *
     * @implNote WAWebIcdcHandlerApi.handleHostedIcdcMetadataInline: checks business hosted
     * device gating, determines sender/receiver account type, detects HOSTED transitions,
     * and triggers device sync when account type changes from E2EE to HOSTED.
     */
    @WhatsAppWebExport(moduleName = "WAWebIcdcHandlerApi",
            exports = "handleHostedIcdcMetadataInline",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public HostedIcdcResult handleHostedIcdcMetadataInline(Jid chatJid, Jid authorJid, DeviceListMetadata metadata) {
        // WAWebIcdcHandlerApi.handleHostedIcdcMetadataInline: check gating
        if (!isBizHostedDevicesEnabled()) {
            return HostedIcdcResult.DEFAULT;
        }

        // WAWebIcdcHandlerApi: skip if sender is self or not a user JID
        if (store.jid().map(myJid -> myJid.toUserJid().equals(chatJid.toUserJid())).orElse(false)) {
            return HostedIcdcResult.DEFAULT;
        }

        if (!chatJid.hasUserServer()) {
            return HostedIcdcResult.DEFAULT;
        }

        // WAWebIcdcHandlerApi.handleHostedIcdcMetadataInline: get deviceListMetadata
        if (metadata == null) {
            return HostedIcdcResult.DEFAULT;
        }

        // WAWebIcdcHandlerApi: determine account type based on who is "me"
        // var d=isMeAccount(n), m=d?c.receiverAccountType:c.senderAccountType, p=d?t:n
        var isAuthorMe = store.jid().map(myJid -> myJid.toUserJid().equals(authorJid.toUserJid())).orElse(false);
        var accountType = isAuthorMe
                ? metadata.receiverAccountType().orElse(null)
                : metadata.senderAccountType().orElse(null);
        var relevantJid = isAuthorMe ? chatJid.toUserJid() : authorJid.toUserJid();

        if (accountType == null) {
            return HostedIcdcResult.DEFAULT;
        }

        // WAWebIcdcHandlerApi: if E2EE, remove from offline cache and return default
        if (accountType == ADVEncryptionType.E2EE) {
            // WAWebBizCoexOfflineICDCHandledCache.removeFromOfflineBizHostedSenderICDCProcessedCache
            offlineBizHostedSenderICDCProcessedCache.remove(relevantJid);
            return HostedIcdcResult.DEFAULT;
        }

        // WAWebIcdcHandlerApi: if not HOSTED, return default
        if (accountType != ADVEncryptionType.HOSTED) {
            return HostedIcdcResult.DEFAULT;
        }

        // WAWebBizCoexOfflineICDCHandledCache.hasOfflineBizHostedSenderICDCProcessedForSender: check dedup
        if (offlineBizHostedSenderICDCProcessedCache.contains(relevantJid)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "[handleHostedIcdcMetadataInline] skip, already processed {0}", relevantJid);
            return new HostedIcdcResult(false, true);
        }

        // WAWebBizCoexOfflineICDCHandledCache.addToOfflineBizHostedSenderICDCProcessedCache
        offlineBizHostedSenderICDCProcessedCache.add(relevantJid);
        LOGGER.log(System.Logger.Level.DEBUG,
                "handleIcdcMetadataInline: add to coex cache for {0}", relevantJid);

        // WAWebBizCoexHostedAddVerification.addToCoexHostedVerificationCache
        store.addToCoexHostedVerificationCache(relevantJid);

        // WAWebIcdcHandlerApi: get existing device record and compare timestamps
        var existingRecord = store.findDeviceList(relevantJid).orElse(null);
        var senderTimestamp = metadata.senderTimestamp()
                .map(Instant::getEpochSecond)
                .orElse(0L);
        var existingTimestamp = existingRecord != null ? existingRecord.timestamp().getEpochSecond() : 0L;

        // WAWebIcdcHandlerApi: if senderTimestamp >= existingTimestamp
        if (senderTimestamp >= existingTimestamp) {
            // WAWebIcdcHandlerApi: if existing record is NOT HOSTED, trigger transition
            if (existingRecord == null || existingRecord.advAccountType() != ADVEncryptionType.HOSTED) {
                // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clear sessions
                if (existingRecord != null) {
                    cleanupAllSessionsForUser(relevantJid, existingRecord);
                }

                // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: mark as deleted with HOSTED transition
                // (5th arg of clearDeviceRecord here is HOSTED, so the tombstone records deletedChangedToHost=true)
                store.addDeviceList(createDeletedDeviceList(relevantJid, true));

                // WAWebIcdcHandlerApi: trigger device sync
                if (store.isResumeFromRestartComplete()) {
                    // WAWebSyncDeviceAdvDeviceListJob.syncDeviceListJob: immediate sync
                    Thread.startVirtualThread(() -> {
                        try {
                            getDeviceLists(List.of(relevantJid), null, null, false);
                        } catch (Exception e) {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "Failed to sync device list for hosted transition: {0}", e.getMessage());
                        }
                    });
                } else {
                    // WAWebApiPendingDeviceSync.addUserToPendingDeviceSync: queue for later
                    var pendingSync = PendingDeviceSync.of(List.of(relevantJid), null);
                    store.addPendingDeviceSync(pendingSync);
                }

                LOGGER.log(System.Logger.Level.DEBUG,
                        "handleHostedIcdcMetadataInline: update ADV type for {0}", relevantJid);

                // WAWebIcdcHandlerApi: hostedBizEncMismatch is true when existing was E2EE or not deletedChangedToHost
                var mismatch = (existingRecord != null && existingRecord.advAccountType() == ADVEncryptionType.E2EE)
                        || (existingRecord == null || !existingRecord.deletedChangedToHost());
                return new HostedIcdcResult(mismatch, true);
            }

            // WAWebIcdcHandlerApi: existing is already HOSTED, no mismatch
            return new HostedIcdcResult(false, true);
        }

        // WAWebIcdcHandlerApi: senderTimestamp < existingTimestamp, return default
        return HostedIcdcResult.DEFAULT;
    }

    /**
     * Handles an ADV device update for an incoming message from a companion device.
     *
     * <p>When a companion device sends a message containing a signed device identity,
     * this method verifies the identity matches the expected account and updates the
     * device list accordingly. It detects identity changes (new primary identity) and
     * performs either a list reset or an incremental update.
     *
     * <p>This is called during message decryption when a PKMSG contains a device-identity
     * node with signed device identity data.
     *
     * @param deviceJid       the full device JID (with device component, must NOT be primary)
     * @param rawId           the rawId from the signed device identity
     * @param timestamp       the timestamp from the signed device identity (Unix seconds)
     * @param keyIndex        the key index from the signed device identity
     * @param identityKey     the identity key from the message (from PKMSG), or {@code null}
     * @param accountType     the ADV account type from the signed identity, or {@code null}
     *
     * @implNote WAWebAdvHandlerApi.handleADVDeviceUpdateForMessage: delegates to
     * WAWebHandleAdvForMessageApi.handleADVDeviceUpdateForMessage which checks rawId, timestamp,
     * keyIndex assertions, retrieves the device record, determines if primary identity changed,
     * and dispatches to handleListReset or handleNoListReset.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvForMessageApi",
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
        // WAWebHandleAdvForMessageApi: assertions
        Objects.requireNonNull(rawId, "rawId cannot be null");

        // WAWebHandleAdvForMessageApi: device must NOT be primary
        if (deviceJid.device() == DeviceConstants.PRIMARY_DEVICE_ID) {
            throw new IllegalArgumentException(
                    "handleADVDeviceUpdateForMessage: device must not be primary: " + deviceJid);
        }

        // WAWebHandleAdvForMessageApi: get existing device record
        var userJid = deviceJid.toUserJid();
        var existingRecord = store.findDeviceList(userJid).orElse(null);

        // WAWebHandleAdvForMessageApi: get stored identity key for comparison
        var storedIdentityKey = store.findIdentityByAddress(userJid.toSignalAddress())
                .map(SignalIdentityPublicKey::toEncodedPoint)
                .orElse(null);

        // WAWebHandleAdvForMessageApi: check if this is a new primary identity
        // JS: y = r == null || (a != null && !bufferEqual(r, a))
        // where r = stored identity (from loadIdentityKey), a = message-side primary identity (accountSignatureKey from PKMSG)
        var isNewPrimaryIdentity = storedIdentityKey == null
                || (identityKey != null && !Arrays.equals(storedIdentityKey, identityKey));

        // WAWebHandleAdvForMessageApi: check existing record timestamp
        if (existingRecord != null && !existingRecord.deleted()
                && existingRecord.timestamp() != null
                && timestamp < existingRecord.timestamp().getEpochSecond()) {
            // WAWebHandleAdvKeyIndexResultApi: r < l.timestamp => return null
            return;
        }

        var lastADVCheckTime = store.lastAdvCheckTime().orElse(null);

        // WAWebHandleAdvForMessageApi: dispatch to list reset or no-list-reset
        // if (!h || h.deleted || h.rawId !== m || isNewPrimaryIdentity) => handleListReset
        // else => handleNoListReset
        if (existingRecord == null || existingRecord.deleted()
                || !rawId.equals(existingRecord.rawId()) || isNewPrimaryIdentity) {
            // WAWebHandleAdvListResetApi.handleListReset: full reset path
            LOGGER.log(System.Logger.Level.INFO,
                    "ADV device update for message: list reset for {0} (rawId={1}, isNewPrimary={2})",
                    userJid, rawId, isNewPrimaryIdentity);

            // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: cleanup existing sessions
            if (existingRecord != null && !existingRecord.deleted()) {
                cleanupAllSessionsForUser(userJid, existingRecord);
            }

            // WAWebHandleAdvListResetApi: create device list with single companion device
            var devices = List.of(
                    DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0),
                    DeviceInfo.ofE2EE(deviceJid.device(), keyIndex)
            );

            // WAWebHandleAdvListResetApi: compute timestamp
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

            // WAWebHandleAdvNoListResetApi: save identity key
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
            // WAWebHandleAdvNoListResetApi.handleNoListReset: incremental update
            var existingDevices = new LinkedHashMap<Integer, DeviceInfo>();
            for (var device : existingRecord.devices()) {
                existingDevices.put(device.id(), device);
            }

            // WAWebHandleAdvNoListResetApi: out-of-order timestamp validation
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

            // WAWebHandleAdvNoListResetApi: update if keyIndex changed
            var currentKeyIndex = existingDevices.containsKey(deviceJid.device())
                    ? existingDevices.get(deviceJid.device()).keyIndex()
                    : -1;
            if (currentKeyIndex != keyIndex) {
                existingDevices.put(deviceJid.device(), DeviceInfo.ofE2EE(deviceJid.device(), keyIndex));

                var updatedDevices = new ArrayList<>(existingDevices.values());

                // WAWebAdvExpectedTsApi.computeExpectedTsForDeviceRecord
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

                // WAWebIdentityUpdateDeviceTableApi.bulkApplyDeviceUpdate: detect changes and store
                var changes = updatedList.mismatch(existingRecord);
                if (!changes.identityChangedDevices().isEmpty()) {
                    for (var changedDevice : changes.identityChangedDevices()) {
                        store.markIdentityChange(changedDevice);
                        store.cleanupSignalSessions(changedDevice);
                    }
                }
                store.addDeviceList(updatedList);
                store.markKeyRotation(userJid);

                // WAWebHandleAdvNoListResetApi: save identity key for the companion device
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
     * Extracts and validates a local device identity from a pairing response.
     *
     * @param deviceIdentityNode the device identity node from pairing response
     * @return the validated signed device identity with generated device signature
     * @throws IllegalStateException          if required store values are missing
     *
     * @implNote WAWebHandlePairSuccess: validates SignedDeviceIdentityHMAC during pairing,
     * verifies account signature, and generates device signature using local identity key.
     * Uses advSecretKey (not companion public key) for HMAC verification.
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
     * Persists the account signature key extracted from a validated pair-success identity as
     * the local user's signal identity.
     *
     * <p>WA Web's {@code WAWebHandlePairSuccess} flow performs
     * {@code waSignalStore.putIdentity(createSignalAddress(asUserWidOrThrow(deviceJidToDeviceWid(y))).toString(),
     * bufferToStr(toSignalCurvePubKey(P)))} immediately after generating the device signature, so the
     * primary device's identity (device 0) becomes resolvable for subsequent ADV verification and
     * encrypted-session establishment. This helper mirrors that step by saving the
     * {@code accountSignatureKey} bytes against the user-only signal address (device id stripped).
     *
     * @param deviceJid           the local device JID assigned by the server during pair-success
     * @param accountSignatureKey the {@code accountSignatureKey} extracted from the validated
     *                            {@link ADVSignedDeviceIdentity}, may be {@code null} when the
     *                            decoded identity did not carry one
     * @implNote WAWebHandlePairSuccess: yield waSignalStore.putIdentity(
     * createSignalAddress(asUserWidOrThrow(deviceJidToDeviceWid(y))).toString(),
     * bufferToStr(toSignalCurvePubKey(P))). Cobalt delegates to
     * {@link DevicePreKeyHandler#storeIdentityFromAccountSignatureKey(Jid, byte[])} which strips
     * the device id and writes the 32-byte curve public key against device 0.
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
            // Mirrors WA Web error tolerance: pair-success continues even if the signal store
            // write fails because the account signature key is rederived on the next sync.
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to persist local identity from pair-success accountSignatureKey for {0}: {1}",
                    deviceJid, exception.getMessage());
        }
    }
}
