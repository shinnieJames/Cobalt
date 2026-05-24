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
import com.github.auties00.cobalt.model.props.ABProp;
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
 * Default implementation of {@link DeviceService} that orchestrates every Multi-Device flow.
 *
 * <p>This implementation collapses WA Web's family of ADV modules ({@code WAWebAdvSyncDeviceListApi},
 * {@code WAWebAdvHandlerApi}, {@code WAWebHandleAdvOmittedResultApi},
 * {@code WAWebHandleAdvDeviceNotificationApi}, {@code WAWebIcdcHandlerApi},
 * {@code WAWebApiDeviceList}) into a single service. It owns the per-user device-list cache via
 * {@link WhatsAppStore}, deduplicates concurrent USync IQs, runs the daily ADV expiration check
 * via {@link DeviceADVChecker}, computes per-message fanout via {@link DeviceFanoutCalculator},
 * attaches ICDC metadata via {@link IcdcComputer}, validates ADV signatures via
 * {@link DeviceADVValidator}, and inserts the business-coexistence system messages that mark
 * {@code E2EE <-> HOSTED} transitions.
 *
 * <p>Outgoing message paths call {@link #getDeviceLists(Collection, String, String, boolean)}
 * (optionally short-circuited by a phash pre-check), notification handlers feed back into the
 * cache via the {@code handleADV...} helpers later in the file, and the {@link WamService}
 * collaborator receives {@code ContactSyncEvent} and {@code CoexPrivacySysMsg} telemetry on
 * every device-sync cycle and every account-type transition.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvSyncDeviceListApi")
@WhatsAppWebModule(moduleName = "WAWebAdvHandlerApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvOmittedResultApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvDeviceNotificationApi")
@WhatsAppWebModule(moduleName = "WAWebIcdcHandlerApi")
@WhatsAppWebModule(moduleName = "WAWebApiDeviceList")
public final class DefaultDeviceService implements DeviceService {
    /**
     * The {@link System.Logger} used for every device-service diagnostic.
     *
     * @apiNote
     * Channels the {@code WALogger} calls that WA Web emits from the ADV modules into a
     * single Java logger so embedders can route them via {@code java.util.logging}.
     */
    private static final System.Logger LOGGER = System.getLogger(DefaultDeviceService.class.getName());

    /**
     * The {@link Joiner} that collates per-batch USync IQ results into a single flat list.
     *
     * @apiNote
     * Used by {@link #getDevicesFetchedResults(List)} to fan out one IQ per
     * {@link DeviceUSyncQueryBuilder} batch under a {@link StructuredTaskScope} and merge the
     * parsed {@link DeviceListResult} entries from every successful batch.
     *
     * @implNote
     * This implementation never propagates a subtask failure: {@link #onComplete} returns
     * {@code false} for failed subtasks so {@link StructuredTaskScope#join} keeps running, and
     * {@link #result} flat-maps only the successful subtasks. A failed batch surfaces later in
     * {@link #fetchDeviceListsFromServer(Collection, String)} as a missing entry that triggers
     * the primary-only fallback, mirroring WA Web's batch-level error tolerance.
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
     * The {@link WhatsAppClient} this service is bound to.
     *
     * @apiNote
     * Carries the socket used to dispatch USync IQs and the store/listener tree that device
     * mutations are reported through.
     */
    private final WhatsAppClient client;

    /**
     * The {@link WebAppStateService} consulted when a device removal forces a missing-key
     * grace-period check.
     *
     * @apiNote
     * Invoked from {@link #updateMissingKeyDevices()} when every remaining device of a peer is
     * unable to produce a requested app-state sync key, mirroring WA Web's
     * {@code WAWebAppStateSyncKeyDistributionApi} all-devices-responded path.
     */
    private final WebAppStateService webAppStateService;

    /**
     * The {@link WhatsAppStore} that persists device lists, ADV records, and Signal state.
     *
     * @apiNote
     * Backs WA Web's {@code WAWebApiDeviceList} table plus the Signal session and identity
     * tables; every read and write the service performs goes through this collaborator.
     */
    private final WhatsAppStore store;

    /**
     * The per-JID in-flight USync fetch futures keyed by user JID.
     *
     * @apiNote
     * Mirrors the {@code d} map in {@code WAWebAdvSyncDeviceListApi.syncDeviceList} so
     * concurrent callers requesting the same JID share one IQ.
     */
    private final ConcurrentHashMap<Jid, CompletableFuture<DeviceList>> pendingFetches;

    /**
     * The {@link DeviceADVChecker} that drives the daily ADV expiration job.
     *
     * @apiNote
     * Mirrors WA Web's {@code WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck}, scheduled
     * via {@link #startAdvCheckScheduler()} and stopped via {@link #stopAdvCheckScheduler()}.
     */
    private final DeviceADVChecker advCheckScheduler;

    /**
     * The {@link DevicePreKeyHandler} responsible for fetching and persisting Signal identity
     * keys and pre-key bundles.
     *
     * @apiNote
     * Invoked during every device sync to preload identity keys for new devices before any
     * outgoing message attempts encryption.
     */
    private final DevicePreKeyHandler preKeyHandler;

    /**
     * The {@link ABPropsService} consulted for every feature gate this service honours.
     *
     * @apiNote
     * Gates hosted-device acceptance ({@link ABProp#ADV_ACCEPT_HOSTED_DEVICES}), ADV key-index
     * list expiration windows, fanout policy decisions, and ICDC inclusion.
     */
    private final ABPropsService abPropsService;

    /**
     * The {@link DeviceADVValidator} that verifies ADV signatures on device identities and
     * key-index lists.
     *
     * @apiNote
     * Owned here so {@link DeviceUSyncResponseParser} and downstream notification handlers
     * share a single validator instance over the same store.
     */
    private final DeviceADVValidator advValidator;

    /**
     * The {@link DeviceUSyncResponseParser} that decodes USync IQ responses into
     * {@link DeviceListResult} variants.
     */
    private final DeviceUSyncResponseParser usyncResponseParser;

    /**
     * The {@link DeviceFanoutCalculator} that resolves the recipient device set for each
     * outgoing message.
     *
     * @apiNote
     * Mirrors WA Web's {@code WAWebDBDeviceListFanout.getFanOutList} and is invoked from the
     * peer and group send paths through {@link DeviceService}.
     */
    private final DeviceFanoutCalculator fanoutCalculator;

    /**
     * The {@link IcdcComputer} that builds the ICDC metadata attached to outgoing
     * {@code messageContextInfo}.
     *
     * @apiNote
     * ICDC (Identity Change Detection Consistency) lets the recipient detect that the
     * sender's device list at send time differs from what the recipient sees.
     */
    private final IcdcComputer icdcComputer;

    /**
     * The {@link DevicePhashCalculator} that computes participant hashes for group fanout
     * agreement checks.
     *
     * @apiNote
     * Used both for the {@code expectedPhash} pre-check short-circuit in
     * {@link #getDeviceLists(Collection, String, String, boolean)} and for the {@code dhash}
     * delta-update marker on outgoing USync IQs.
     */
    private final DevicePhashCalculator phashCalculator;

    /**
     * The {@link ReentrantLock} serialising every multi-table write that touches device
     * records together with Signal sessions, sender keys, missing-keys, and contacts.
     *
     * @apiNote
     * Stands in for WA Web's {@code WAWebApiGetDeviceUpdateLock.getDeviceUpdateLock}, which
     * grabs a multi-resource IndexedDB lock over the same set of tables.
     *
     * @implNote
     * This implementation collapses WA Web's per-table lock array
     * ({@code participant}, {@code device-list}, {@code message}, {@code message-association},
     * {@code missing-keys}, {@code contact}) into a single in-process mutex. Cobalt has no
     * IndexedDB and runs every store mutation in-VM, so coarse-grained locking is sufficient.
     */
    private final ReentrantLock deviceUpdateLock;

    /**
     * The dedup cache holding the {@link Instant} at which the last initial hosted system
     * message was inserted for each user JID.
     *
     * @apiNote
     * Backs {@link #shouldDedupInitialHostedSystemMsg(Jid)}; entries live forever, mirroring
     * WA Web's process-lifetime {@code Set<string>} in {@code WAWebBizCoexUtils}.
     */
    private final ConcurrentHashMap<Jid, Instant> hostedSystemMsgDedupCache;

    /**
     * The set of sender JIDs whose offline hosted ICDC metadata has been processed for the
     * current connection.
     *
     * @apiNote
     * Cleared on reconnect to mirror WA Web's per-session {@code Set} in
     * {@code WAWebHandleBizHostedSenderICDC}.
     */
    private final Set<Jid> offlineBizHostedSenderICDCProcessedCache;

    /**
     * The {@link WamService} that receives every device-related telemetry event.
     *
     * @apiNote
     * Collects {@code ContactSyncEvent} on each USync flow and {@code CoexPrivacySysMsg} on
     * each account-type transition.
     */
    private final WamService wamService;

    /**
     * Constructs a {@link DefaultDeviceService} bound to the given client and collaborators.
     *
     * @apiNote
     * Internal constructor invoked by the {@link WhatsAppClient} bootstrap; embedders should
     * not call it directly and instead consume {@link DeviceService} through the client.
     *
     * @implNote
     * This implementation owns the lifecycle of every helper it instantiates
     * ({@link DevicePreKeyHandler}, {@link DeviceADVValidator}, {@link DeviceFanoutCalculator},
     * {@link IcdcComputer}, {@link DevicePhashCalculator}, {@link DeviceUSyncResponseParser},
     * {@link DeviceADVChecker}) so they share the same {@link WhatsAppStore} and
     * {@link ABPropsService} instances and therefore a single coherent view of device state.
     *
     * @param client             the {@link WhatsAppClient} providing store and socket access
     * @param webAppStateService the {@link WebAppStateService} used for the missing-key
     *                           grace-period scheduling triggered by self-device removals
     * @param abPropsService     the {@link ABPropsService} used for every feature gate
     * @param sessionCipher      the {@link SignalSessionCipher} threaded into the
     *                           {@link DevicePreKeyHandler}
     * @param wamService         the {@link WamService} that receives device-related telemetry
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DefaultDeviceService(WhatsAppClient client, WebAppStateService webAppStateService, ABPropsService abPropsService, SignalSessionCipher sessionCipher, WamService wamService) {
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
     * Runs {@code task} while holding {@link #deviceUpdateLock}.
     *
     * @apiNote
     * Used by every mutation path that must update the device-list, Signal session, sender-key,
     * missing-keys, or contact tables atomically with respect to other device updates.
     *
     * @implNote
     * This implementation uses an in-process {@link ReentrantLock} where WA Web's
     * {@code WAWebApiGetDeviceUpdateLock} grabs a multi-resource IndexedDB lock over six
     * specific tables; the coarser scope is sufficient because Cobalt holds the entire store
     * in memory.
     *
     * @param task the {@link Runnable} to execute under the lock
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
     * Returns whether the embedded {@code accountSignatureKey} from a USync response may be
     * accepted as an identity key for hosted-device users.
     *
     * @apiNote
     * Gates the override branch in {@link #fetchDeviceListsFromServer(Collection, String)}
     * that calls {@link DevicePreKeyHandler} to ingest the protobuf-carried key for hosted
     * accounts rather than waiting for a separate pre-key fetch.
     *
     * @implNote
     * This implementation reads {@link ABProp#ADV_ACCEPT_HOSTED_DEVICES} alone, matching
     * current WA Web behaviour where {@code bizHostedDevicesEnabled} is the single gate;
     * the legacy {@code override_adv_account_signature_key_enabled} prop has been retired.
     *
     * @return {@code true} when hosted devices are accepted
     */
    private boolean isHostedOverrideAdvAccountSignatureKeyEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Returns the {@link DeviceList} for every user in {@code userJids}, fetching from the
     * server only what is missing or invalid.
     *
     * @apiNote
     * Entry point for every Cobalt send path that needs the recipient device fanout, equivalent
     * to {@code syncAndGetDeviceList} in WA Web. Pass a non-null {@code expectedPhash} when the
     * caller already knows the server-side participant hash (typical for group fanout): when
     * the local phash matches, the server round-trip is skipped entirely. Set
     * {@code shouldMergeAltDevices} to also include the device ids of the user's
     * alternate-addressing record (LID for a PN input, PN for a LID input).
     *
     * @implNote
     * This implementation feeds cached records into the phash short-circuit even when the
     * record is marked deleted (treated as an empty device list, matching WA Web). Cached but
     * deleted records that were not flagged as deleted-changed-to-host fall back to the
     * primary-only device list rather than a server fetch, also matching WA Web.
     *
     * @param userJids              the user JIDs to resolve
     * @param context               the USync context string (for example {@code "message"},
     *                              {@code "interactive"}, {@code "adv_expiration"})
     * @param expectedPhash         the phash to compare against the local device list, or
     *                              {@code null} to disable the short-circuit
     * @param shouldMergeAltDevices whether to merge PN and LID alternate device lists
     * @return the resolved device lists, one per user JID
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = {"syncDeviceList", "syncAndGetDeviceList"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices) {
        userJids = userJids.stream().map(Jid::toUserJid).toList();

        if (expectedPhash != null && !expectedPhash.isEmpty()) {
            try {
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
     * Returns {@code primaryLists} with each entry's device ids enriched by the device ids of
     * its alternate-addressing counterpart.
     *
     * @apiNote
     * Implements the {@code shouldMergeAltDevices} branch of WA Web's
     * {@link DeviceList#userJid()} of each returned record is the originally-queried JID, so
     * any device JID minted from the result keeps the addressing mode the caller asked for;
     * alternate records that are absent from {@code primaryLists} are pulled from the local
     * store via {@link #bulkGetDeviceRecord(List)}.
     *
     * @implNote
     * This implementation skips the alternate enrichment entirely when no alternate WID can be
     * resolved for any input, mirroring the early-return shape of WA Web's loop.
     *
     * @param primaryLists the device lists for the originally-queried user JIDs
     * @return one {@link DeviceList} per input, augmented by the alternate record's devices
     *         when available
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceIds",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Set<DeviceList> mergeAlternateDeviceLists(Collection<DeviceList> primaryLists) {
        if (primaryLists.isEmpty()) {
            return Set.copyOf(primaryLists);
        }

        var primaryByJid = new LinkedHashMap<Jid, DeviceList>();
        for (var primary : primaryLists) {
            primaryByJid.put(primary.userJid(), primary);
        }

        var altJidByPrimary = new LinkedHashMap<Jid, Jid>();
        for (var primaryJid : primaryByJid.keySet()) {
            var alt = findAlternateUserWid(primaryJid);
            if (alt != null) {
                altJidByPrimary.put(primaryJid, alt);
            }
        }
        if (altJidByPrimary.isEmpty()) {
            return new LinkedHashSet<>(primaryLists);
        }

        var altListByJid = new HashMap<Jid, DeviceList>();
        var altsToFetch = new ArrayList<Jid>();
        for (var altJid : altJidByPrimary.values()) {
            var inInput = primaryByJid.get(altJid);
            if (inInput != null) {
                altListByJid.put(altJid, inInput);
            } else {
                altsToFetch.add(altJid);
            }
        }
        if (!altsToFetch.isEmpty()) {
            var fetched = bulkGetDeviceRecord(altsToFetch);
            for (var i = 0; i < fetched.size(); i++) {
                var record = fetched.get(i);
                if (record != null && !record.deleted()) {
                    altListByJid.put(altsToFetch.get(i), record);
                }
            }
        }

        var result = new LinkedHashSet<DeviceList>(primaryByJid.size());
        for (var entry : primaryByJid.entrySet()) {
            var primary = entry.getValue();
            if (primary.deleted()) {
                result.add(primary);
                continue;
            }
            var altJid = altJidByPrimary.get(entry.getKey());
            var alt = altJid != null ? altListByJid.get(altJid) : null;
            if (alt == null) {
                result.add(primary);
                continue;
            }
            var existingIds = new HashSet<Integer>();
            for (var device : primary.devices()) {
                existingIds.add(device.id());
            }
            var augmented = new ArrayList<>(primary.devices());
            for (var device : alt.devices()) {
                if (!existingIds.contains(device.id())) {
                    augmented.add(device);
                }
            }
            if (augmented.size() != primary.devices().size()) {
                result.add(withAugmentedDevices(primary, augmented));
            } else {
                result.add(primary);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns a copy of {@code original} with its devices replaced by {@code devices}.
     *
     * @apiNote
     * Helper for {@link #mergeAlternateDeviceLists(Collection)}; every other field is
     * preserved verbatim, including the {@link DeviceList#userJid()} that carries the
     * addressing mode (PN or LID) of the originally-queried record.
     *
     * @param original the source {@link DeviceList}
     * @param devices  the {@link DeviceInfo} list to install on the copy
     * @return the rebuilt {@link DeviceList}
     */
    private static DeviceList withAugmentedDevices(DeviceList original, List<DeviceInfo> devices) {
        return new DeviceListBuilder()
                .userJid(original.userJid())
                .devices(devices)
                .timestamp(original.timestamp())
                .rawId(original.rawId())
                .deleted(original.deleted())
                .deletedChangedToHost(original.deletedChangedToHost())
                .advAccountType(original.advAccountType())
                .expectedTimestamp(original.expectedTimestamp())
                .expectedTimestampLastDeviceJobTimestamp(original.expectedTimestampLastDeviceJobTimestamp())
                .expectedTimestampUpdateTimestamp(original.expectedTimestampUpdateTimestamp())
                .currentIndex(original.currentIndex())
                .validIndexes(original.validIndexes())
                .build();
    }

    /**
     * Emits a warning diagnostic for every regular phone-number JID in {@code userJids} that
     * lacks a cached LID mapping.
     *
     * @apiNote
     * Mirrors WA Web's {@code WAWebApiContact.checkPnToLidMapping}, which surfaces missing
     * LID mappings to telemetry so a high miss-rate can be investigated. Bots and hosted-server
     * JIDs are excluded as they do not participate in the PN-LID address-pairing scheme.
     *
     * @implNote
     * This implementation defaults {@code caller} to the string {@code "unknown"} when
     * {@code null}, matching the JS counterpart's fallback.
     *
     * @param userJids the user JIDs to inspect
     * @param caller   a short identifier of the call site for the log line, or {@code null}
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
     * Issues USync IQs to fetch {@link DeviceList} records for the given user JIDs, ingests
     * the response, persists the result, and emits the matching WAM telemetry.
     *
     * @apiNote
     * The single entry point for forcing a server-side device sync; called by
     * {@link #getDeviceLists(Collection, String, String, boolean)} for cache misses and by
     * {@link #retryPendingSyncs()} after reconnect. Concurrent calls for the same JID share
     * the same in-flight {@link CompletableFuture} via {@link #pendingFetches} so duplicate IQs
     * are coalesced. The {@code device_hash} (dhash) attached to each batch lets the server
     * answer with an {@link DeviceListResult.Omitted} marker when the local hash already
     * matches the server's view; an omitted record is reset to primary-only but keeps its
     * {@code rawId} and {@code validIndexes}.
     *
     * @implNote
     * This implementation drives the entire pipeline that WA Web splits across
     * {@code WAWebAdvSyncDeviceListApi.syncDeviceList} and
     * {@code WAWebAdvHandlerApi.handleADVDeviceSyncResult}: dhash construction, identity-key
     * preloading via {@link DevicePreKeyHandler}, IQ dispatch via
     * {@link #getDevicesFetchedResults(List)}, PN-to-LID backfill via
     * {@link #backfillMissingDeviceSyncEntries(Set, List)}, optional hosted-device filtering,
     * per-record list-reset and account-type-transition detection, expected-timestamp tracking,
     * listener fan-out, sender-key rotation marking, missing-key reconciliation, and contact
     * sync telemetry. Failures stash the requested set as a {@link PendingDeviceSync} so
     * {@link #retryPendingSyncs()} can replay it after reconnect.
     *
     * @param userJids the user JIDs to fetch from the server
     * @param context  the USync context string carried into the IQ and WAM event
     * @return the resolved device lists
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
        var fetchedResponseCount = 0;

        try {
            var hashInfos = new HashMap<Jid, DeviceListHashInfo>();
            for (var jid : toFetch) {
                var cached = store.findDeviceList(jid);
                if (cached.isPresent()) {
                    try {
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
                    }
                }
            }

            checkPnToLidMapping(toFetch, "device_sync_request");

            preKeyHandler.fetchAndStoreIdentityKeys(toFetch);

            var batches = DeviceUSyncQueryBuilder.build(toFetch, context, hashInfos, true);
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

                            if (!changes.addedDevices().isEmpty() || !changes.removedDevices().isEmpty()) {
                                store.markKeyRotation(trackedList.userJid());
                            }
                        }

                        if (!trackedList.validIndexes().isEmpty() || trackedList.currentIndex() > 0) {
                            usersWithValidatedKeyIndex.add(trackedList.userJid());
                        }

                        yield trackedList;
                    }

                    case DeviceListResult.Omitted omitted -> {
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
                        Instant newTimestamp;
                        var finalExpectedTs = oldList.expectedTimestamp();
                        var newExpectedTsUpdateTs = oldList.expectedTimestampUpdateTimestamp();
                        var newExpectedTsLastDeviceJobTs = oldList.expectedTimestampLastDeviceJobTimestamp();
                        if (omitted.timestamp().isPresent()) {
                            newTimestamp = omitted.timestamp().get();

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
                    true, result.size());

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
                    true, extractUsyncErrorCode(e),
                    CONTACT_SYNC_ERROR_CODE_DEVICE_SYNC);

            throw new RuntimeException("Failed to fetch device lists", e);
        } finally {
            for (var jid : toFetch) {
                pendingFetches.remove(jid);
            }
        }
    }

    /**
     * The bit position of the {@code DEVICE} protocol inside the contact-sync request bitmask.
     *
     * @apiNote
     * Matches {@code WAWebContactSyncLogger.PROTOCOL_BIT.DEVICE = 5} on the WA Web side; used
     * by {@link #contactSyncProtocolBitmask(boolean)} to build the
     * {@code request_protocol} WAM property.
     */
    private static final int CONTACT_SYNC_PROTOCOL_BIT_DEVICE = 5;

    /**
     * The bit position of the {@code USERNAME} protocol inside the contact-sync request
     * bitmask.
     *
     * @apiNote
     * Matches {@code WAWebContactSyncLogger.PROTOCOL_BIT.USERNAME = 10}; toggled on whenever
     * the USync IQ includes the username sub-protocol.
     */
    private static final int CONTACT_SYNC_PROTOCOL_BIT_USERNAME = 10;

    /**
     * The {@code SYNC_REQUEST_ORIGIN.DEVICE_REQUEST} value reported on every device-sync WAM
     * event.
     *
     * @apiNote
     * Matches {@code WAWebContactSyncLogger.SYNC_REQUEST_ORIGIN.DEVICE_REQUEST = 48} and is
     * the origin code attached to {@code ContactSyncEvent} entries emitted by this service.
     */
    private static final int CONTACT_SYNC_REQUEST_ORIGIN_DEVICE_REQUEST = 48;

    /**
     * The error code substituted for HTTP {@code 429} when emitting the contact-sync failure
     * WAM event.
     *
     * @apiNote
     * Mirrors the {@code WAWebContactSyncErrorCodes.DEVICE_SYNC} fallback that
     * {@code WAWebContactSyncLogger.logFailure} swaps in for rate-limited responses so the WAM
     * dashboard can distinguish device-sync rate limiting from other 429s.
     *
     * @implNote
     * This implementation duplicates the literal here as a plain {@code int} because the
     * matching enum constant in
     * {@link com.github.auties00.cobalt.wam.event.ContactSyncEventEvent#contactSyncErrorCode()}
     * is serialised as a raw integer on the wire.
     */
    private static final int CONTACT_SYNC_ERROR_CODE_DEVICE_SYNC = 1300;

    /**
     * Returns the bitmask written to {@code contactSyncRequestProtocol} on the
     * {@code ContactSyncEvent}.
     *
     * @apiNote
     * Reproduces the {@code p()} helper from {@code WAWebContactSyncLogger}; called from
     * {@link #emitContactSyncSuccess} and {@link #emitContactSyncFailure} to tag the device
     * sync with its actual sub-protocol set.
     *
     * @param includeUsernameProtocol {@code true} when the IQ also carried the username
     *                                sub-protocol
     * @return the OR of every enabled {@code PROTOCOL_BIT_*} flag
     */
    private static int contactSyncProtocolBitmask(boolean includeUsernameProtocol) {
        var bitmask = 1 << CONTACT_SYNC_PROTOCOL_BIT_DEVICE;
        if (includeUsernameProtocol) {
            bitmask |= 1 << CONTACT_SYNC_PROTOCOL_BIT_USERNAME;
        }
        return bitmask;
    }

    /**
     * Returns the server-reported USync error code carried on {@code throwable}, unwrapping
     * one layer of cause if necessary.
     *
     * @apiNote
     * Used by {@link #fetchDeviceListsFromServer(Collection, String)} to feed a meaningful
     * error code into the failure {@code ContactSyncEvent} rather than the wrapper
     * {@code RuntimeException} thrown to callers.
     *
     * @param throwable the exception caught during the device-sync flow
     * @return the embedded {@link WhatsAppDeviceSyncException#errorCode()}, or {@code 0} when
     *         neither {@code throwable} nor its direct cause is a
     *         {@link WhatsAppDeviceSyncException}
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
     * Commits the success-path {@code ContactSyncEvent} for a completed device sync.
     *
     * @apiNote
     * Reproduces the {@code logSuccess} branch of {@code WAWebContactSyncLogger}; called from
     * {@link #fetchDeviceListsFromServer(Collection, String)} after the USync results have
     * been persisted. The sync type literal is upper-cased
     * ({@code <context>_QUERY}) to match the WA Web event payload.
     *
     * @param context                 the USync context string (for example
     *                                {@code "interactive"}, {@code "background"})
     * @param requestedCount          the number of JIDs originally requested
     * @param responseCount           the number of entries returned by the server
     * @param syncStartTimestamp      the {@link Instant} at which the sync started
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
     * Commits the failure-path {@code ContactSyncEvent} for an aborted device sync.
     *
     * @apiNote
     * Reproduces the {@code logFailure} branch of {@code WAWebContactSyncLogger}; called from
     * the {@code catch} in {@link #fetchDeviceListsFromServer(Collection, String)} so the WAM
     * dashboard sees aborted device syncs.
     *
     * @implNote
     * This implementation folds HTTP {@code 429} onto {@code fallbackErrorCode} (typically
     * {@link #CONTACT_SYNC_ERROR_CODE_DEVICE_SYNC}) before committing so rate-limited responses
     * are bucketed separately from other server errors, matching WA Web's substitution.
     *
     * @param context                 the USync context string
     * @param requestedCount          the number of JIDs originally requested
     * @param responseCount           the number of entries returned before the failure
     * @param syncStartTimestamp      the {@link Instant} at which the sync started
     * @param includeUsernameProtocol whether the username protocol was included
     * @param serverErrorCode         the raw error code from the USync response
     * @param fallbackErrorCode       the code substituted when {@code serverErrorCode} is
     *                                {@code 429}
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
     * Dispatches every USync IQ batch in parallel and returns the parsed results flattened.
     *
     * @apiNote
     * Used by {@link #fetchDeviceListsFromServer(Collection, String)} after
     * {@link DeviceUSyncQueryBuilder} has split the JIDs into one IQ per server-allowed batch
     * size. Failed batches are tolerated via {@link #JOINER}.
     *
     * @implNote
     * This implementation forks each batch as a subtask under a {@link StructuredTaskScope}
     * with virtual-thread scheduling, matching WA Web's {@code Promise.all} fan-out but with
     * structured cancellation semantics.
     *
     * @param batches the USync IQ batches produced by {@link DeviceUSyncQueryBuilder}
     * @return the parsed device list results from every successful batch
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
     * Carries out the bookkeeping required when a user's
     * {@link ADVEncryptionType ADVEncryptionType} switches between {@code E2EE} and
     * {@code HOSTED}.
     *
     * @apiNote
     * Triggered when the USync flow in {@link #fetchDeviceListsFromServer(Collection, String)}
     * detects a different {@code advAccountType} from the cached record, or when an
     * {@link DeviceListResult.Omitted} carries an implicit {@code HOSTED -> E2EE} transition.
     * Cobalt clients listening on {@link WhatsAppClient#store()} receive
     * {@code onAccountTypeChanged}.
     *
     * @implNote
     * This implementation rejects {@code -> HOSTED} transitions for JIDs absent from the
     * interop hosted verification cache (anti-spoofing guard mirroring WA Web's
     * {@code WAWebBizCoexHostedAddVerification.assertThrowsWidAdvTypeFromVerificationCache}),
     * clears every Signal session belonging to {@code oldList}, replaces the record with a
     * {@code deletedChangedToHost} tombstone on {@code -> HOSTED}, updates the contact's
     * encryption type, fans the change out to listeners on virtual threads, and inserts the
     * system message via {@link #createAccountTypeChangeSystemMessage(Jid, ADVEncryptionType, ADVEncryptionType)}.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous {@link ADVEncryptionType}
     * @param newType the new {@link ADVEncryptionType}
     * @param oldList the cached {@link DeviceList} immediately before the transition
     * @throws IllegalStateException when transitioning to {@code HOSTED} for a JID not present
     *                               in the interop hosted verification cache
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "isMeOrCurrentContactHosted",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleAccountTypeTransition(Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType, DeviceList oldList) {
        LOGGER.log(System.Logger.Level.INFO, "Account type changed for {0}: {1} -> {2}", userJid, oldType, newType);

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
     * Inserts the user-visible system message that announces an account-type transition into
     * the user's chat.
     *
     * @apiNote
     * Surfaces the {@code "Messages are now end-to-end encrypted"} banner for
     * {@code HOSTED -> E2EE} (stub {@link ChatMessageInfo.StubType#E2E_ENCRYPTED_NOW}) and the
     * counterpart {@link ChatMessageInfo.StubType#CIPHERTEXT} banner for {@code E2EE -> HOSTED}.
     * Listeners on {@link WhatsAppClient#store()} receive
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onNewMessage onNewMessage}.
     *
     * @implNote
     * This implementation deduplicates the initial {@code -> HOSTED} message per second via
     * {@link #shouldDedupInitialHostedSystemMsg(Jid)} so repeated transitions inside the same
     * USync cycle do not flood the chat, matching the WA Web set-based guard.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous {@link ADVEncryptionType}, or {@code null} when unknown
     * @param newType the new {@link ADVEncryptionType}
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
     * Commits a {@code CoexPrivacySysMsg} WAM event for an inserted account-type-change
     * system message.
     *
     * @apiNote
     * Reproduces {@code WAWebBizCoexUtils.sendWamCoexPrivacySysMsgInsertSuccess}; tagged with
     * the {@link CoexSysMsgStateTransitionAttempt} derived from
     * {@code (oldType, newType)} so the WAM dashboard can attribute the inserted message to the
     * right transition bucket.
     *
     * @implNote
     * This implementation maps a {@code null -> HOSTED} input onto
     * {@link CoexSysMsgStateTransitionAttempt#E2EE_TO_HOSTED} because WA Web treats unknown
     * priors as E2EE for telemetry purposes. The {@code channel} property is left unset on
     * purpose: only the history-sync entry point in {@code sendWamCoexPrivacySysMsgHistorySyncInsert}
     * populates {@code HISTORY_SYNC}.
     *
     * @param userJid the user JID whose account type changed
     * @param oldType the previous {@link ADVEncryptionType}, or {@code null} if unknown
     * @param newType the new {@link ADVEncryptionType}
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

        wamService.commit(builder.build());
    }

    /**
     * Returns whether an initial hosted-transition system message has already been emitted for
     * {@code userJid} during the current wall-clock second.
     *
     * @apiNote
     * Mirrors {@code WAWebBizCoexUtils.shouldDedupInitialHostedSystemMsg}; consulted by
     * {@link #createAccountTypeChangeSystemMessage(Jid, ADVEncryptionType, ADVEncryptionType)}
     * to suppress duplicate banners when several USync responses report the same hosted
     * transition in quick succession.
     *
     * @implNote
     * This implementation stores the latest emission second per JID in
     * {@link #hostedSystemMsgDedupCache} and returns {@code true} when the cached second
     * matches the current second; entries are never purged, matching the process-lifetime
     * {@code Set<string>} guard on the WA Web side.
     *
     * @param userJid the user JID
     * @return {@code true} when the caller should skip insertion
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
     * Tears down every Signal session associated with the devices in {@code oldList}.
     *
     * @apiNote
     * Invoked from list-reset and account-type-transition paths so that subsequent outgoing
     * messages negotiate fresh sessions instead of resurrecting stale ratchets.
     *
     * @param userJid the owning user JID
     * @param oldList the {@link DeviceList} whose devices should be cleaned up
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
     * Returns whether the cached device list must be discarded because the server reports a
     * new {@code rawId}.
     *
     * @apiNote
     * The {@code rawId} is the per-user device-configuration fingerprint; a change implies the
     * user re-registered, so every cached Signal session targeting their old devices is no
     * longer usable.
     *
     * @param cachedList the cached {@link DeviceList}, or {@code null}
     * @param newRawId   the {@code rawId} reported by the server
     * @return {@code true} when {@link #handleListReset(Jid, DeviceList, String, String)} must
     *         run
     */
    private boolean requiresListReset(DeviceList cachedList, String newRawId) {
        if (cachedList == null || cachedList.deleted()) {
            return false;
        }
        var oldRawId = cachedList.rawId();
        return oldRawId != null && newRawId != null && !oldRawId.equals(newRawId);
    }

    /**
     * Carries out the Signal-session cleanup that a {@code rawId} change demands.
     *
     * @apiNote
     * Invoked from {@link #fetchDeviceListsFromServer(Collection, String)} when
     * {@link #requiresListReset(DeviceList, String)} returns {@code true}; the new
     * {@link DeviceList} is then persisted by the caller with a backdated timestamp so the
     * next ADV check re-validates the user.
     *
     * @param userJid    the user JID being reset
     * @param cachedList the previous {@link DeviceList} whose devices are about to be
     *                   invalidated
     * @param oldRawId   the previous {@code rawId} (logged for diagnostics)
     * @param newRawId   the new {@code rawId} (logged for diagnostics)
     */
    private void handleListReset(Jid userJid, DeviceList cachedList, String oldRawId, String newRawId) {
        LOGGER.log(System.Logger.Level.INFO,
                "Device list rawId changed for {0}: {1} -> {2}, triggering full reset (handleListReset)",
                userJid, oldRawId, newRawId);
        cleanupAllSessionsForUser(userJid, cachedList);
    }

    /**
     * Validates an incremental device-list update against the cached {@code validIndexes} and
     * logs the diff.
     *
     * @apiNote
     * Runs on the non-reset branch of {@link #fetchDeviceListsFromServer(Collection, String)};
     * out-of-order timestamps that carry a non-primary {@code keyIndex} absent from the cached
     * {@code validIndexes} are rejected because they signal a potential replay of a stale
     * device list.
     *
     * @implNote
     * This implementation accepts a device when its {@code keyIndex} is {@code 0} (the primary
     * device), is contained in {@link DeviceList#validIndexes()}, or is strictly greater than
     * the cached {@link DeviceList#currentIndex()}. The first two cases match WA Web's check;
     * the third is a forward-tolerant relaxation for newly-issued indexes that have not yet
     * been advertised in {@code validIndexes}.
     *
     * @param userJid    the user JID being checked
     * @param cachedList the cached {@link DeviceList}
     * @param newList    the new {@link DeviceList} from the server
     * @return {@code newList} unchanged, for caller convenience
     * @throws IllegalStateException when {@code newList} contains a non-primary
     *                               {@code keyIndex} outside {@code validIndexes} despite a
     *                               non-newer timestamp
     */
    private DeviceList handleNoListReset(Jid userJid, DeviceList cachedList, DeviceList newList) {
        var cachedValidIndexes = cachedList.validIndexes();
        if (!cachedValidIndexes.isEmpty() && !cachedList.timestamp().isAfter(newList.timestamp())) {
            var cachedCurrentIndex = cachedList.currentIndex();
            for (var device : newList.devices()) {
                var keyIndex = device.keyIndex();
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
     * Duplicates LID-keyed USync results back onto the original phone-number request when the
     * server returned only the LID record.
     *
     * @apiNote
     * Mirrors {@code WAWebContactSyncUtils.backfillMissingDeviceSyncEntries}; called from
     * {@link #fetchDeviceListsFromServer(Collection, String)} before any record is persisted,
     * so callers that issued a PN-keyed request receive an entry under that PN even when the
     * server only knows the user by LID.
     *
     * @implNote
     * This implementation rethrows any {@link DeviceListResult.Error} encountered in the input
     * as a {@link WhatsAppDeviceSyncException}, then walks the requested JIDs and looks up the
     * matching LID via {@link WhatsAppStore#findLidByPhone(Jid)}; non-regular-user PNs
     * (announcements, bots, LIDs) are skipped to match the {@code isRegularUserPn()} filter
     * applied at the WA Web call site.
     *
     * @param requestedJids the JIDs originally passed to the USync IQ
     * @param results       the parsed USync results
     * @return {@code results} with PN-keyed clones of LID-keyed entries appended where needed
     * @throws WhatsAppDeviceSyncException when {@code results} contains a
     *                                     {@link DeviceListResult.Error}
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
     * Returns the {@link Instant} at which the daily ADV device-info check last ran.
     *
     * @apiNote
     * Used by {@link DeviceADVChecker} to compute the delay until the next run; also consulted
     * inside {@link #fetchDeviceListsFromServer(Collection, String)} to decide whether an
     * incoming {@code expectedTimestamp} should be retained or cleared.
     *
     * @return the last run time, or {@link Optional#empty()} if the check has never run
     */
    @WhatsAppWebExport(moduleName = "WAWebLastADVCheckTimeApi",
            exports = "getLastADVDeviceInfoCheckTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Instant> lastAdvCheckTime() {
        return store.lastAdvCheckTime();
    }

    /**
     * Records that the daily ADV device-info check has just completed.
     *
     * @apiNote
     * Called from {@link DeviceADVChecker} immediately after a successful run so the next run
     * is scheduled one {@code DAY_SECONDS} window away.
     */
    @WhatsAppWebExport(moduleName = "WAWebLastADVCheckTimeApi",
            exports = "setLastADVDeviceInfoCheckTime",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateAdvCheckTime() {
        store.updateAdvCheckTime();
    }

    /**
     * Builds a fallback {@link DeviceList} containing only the user's primary device
     * ({@link DeviceConstants#PRIMARY_DEVICE_ID}).
     *
     * @apiNote
     * Used in {@link #getDeviceLists(Collection, String, String, boolean)} and
     * {@link #fetchDeviceListsFromServer(Collection, String)} when the server reports no
     * companion devices, mirroring the primary-only branch of
     * {@code WAWebDBDeviceListFanout.getFanOutList}.
     *
     * @param userJid the user JID
     * @return a {@link DeviceList} with one {@link DeviceInfo} entry for device id {@code 0}
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
     * Builds a tombstone {@link DeviceList} marking a user's record as deleted.
     *
     * @apiNote
     * Mirrors the {@code {deleted: true, deletedChangedToHost?}} record WA Web writes in
     * {@code WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord}; {@code changedToHost} is
     * set when the deletion is the direct result of an {@code E2EE -> HOSTED} transition so
     * later reads can distinguish it from a generic clear and skip the primary-only fallback
     * in {@link #getDeviceLists(Collection, String, String, boolean)}.
     *
     * @param userJid       the user JID being marked deleted
     * @param changedToHost whether the deletion is caused by a hosted transition
     * @return the tombstone {@link DeviceList}
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
     * Starts the daily ADV device-info check scheduler.
     *
     * @apiNote
     * Mirrors WA Web's {@code WAWebStartBackend} call to
     * {@code WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck}; the
     * {@link WhatsAppClient} bootstrap is expected to call this once after login completes.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void startAdvCheckScheduler() {
        advCheckScheduler.start();
    }

    /**
     * Stops the daily ADV device-info check scheduler.
     *
     * @apiNote
     * Should be called before disconnecting so the background job does not survive past the
     * lifetime of the {@link WhatsAppClient}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void stopAdvCheckScheduler() {
        advCheckScheduler.close();
    }

    /**
     * Re-runs every {@link PendingDeviceSync} that was stashed when an earlier device sync
     * failed.
     *
     * @apiNote
     * Mirrors {@code WAWebApiPendingDeviceSync.doPendingDeviceSync}; expected to be invoked by
     * the reconnect path so that USync requests that failed offline are replayed once the
     * socket is back. Expired entries and entries that have exhausted their retry budget are
     * dropped without retrying.
     *
     * @implNote
     * This implementation re-queues a fresh {@link PendingDeviceSync} via
     * {@link PendingDeviceSync#nextRetry()} when the replay itself fails, where WA Web simply
     * removes the row from {@code WAWebSchemaPendingDeviceSync} after a single attempt; the
     * extra retry budget makes Cobalt's pending-sync recovery more aggressive than WA Web's.
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
     * Reconciles the {@code MissingKeyStore} entries for the local user with the current
     * companion device set.
     *
     * @apiNote
     * Mirrors {@code WAWebSyncdStoreMissingKeys.updateMissingKeyDevices}; the
     * {@link WebAppStateService} calls this after a
     * companion is removed so that syncd does not keep blocking on key responses from a
     * device that is no longer present. When the resulting set of remaining devices has
     * all responded without ever sending the missing key, schedules the grace-period
     * fatal-error check via {@link WebAppStateService#scheduleAllDevicesRespondedCheck()}.
     *
     * @implNote
     * This implementation runs the whole reconciliation under
     * {@link #withDeviceUpdateLock(Runnable)} to serialise against device-list mutations,
     * exits silently when no JID is set or the cached list is missing or tombstoned, and
     * relies on {@link WhatsAppStore#missingSyncKeys()} for the unbatched view of the
     * missing-key records that WA Web reads from {@code MissingKeyStore} inside an IDB
     * transaction.
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
     * Returns the recipient device JIDs to fan out a 1:1 message to.
     *
     * @apiNote
     * Mirrors {@code WAWebDBDeviceListFanout.getFanOutList} for the
     * {@code chatWidSetToIncludeHostedInFanoutOneToOneChatOnly} branch; called from every
     * outgoing 1:1 send path before encryption to obtain the per-device targets. When
     * {@code expectedPhash} is non-{@code null} and the locally-computed phash matches,
     * the underlying {@link #getDeviceLists(Collection, String, String, boolean)} call
     * skips the USync IQ so a steady-state thread does not re-query the server on every
     * keypress.
     *
     * @implNote
     * This implementation resolves the sender's device JID via
     * {@link #resolveMyDeviceJid(Jid)} so that LID-addressed chats use the LID-side
     * me-device, then asks the {@link DeviceFanoutCalculator} to drop self and apply
     * hosted-device gating, and finally strips devices in
     * {@link WhatsAppStore#unconfirmedIdentityChanges()} so messages do not silently
     * land on a recipient whose security code has changed but not been re-verified.
     *
     * @param chatJid       the recipient user JID
     * @param expectedPhash the server-reported phash to match against, or {@code null}
     *                      to force a sync
     * @return the device JIDs to encrypt to
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Collection<Jid> getUserFanout(Jid chatJid, String expectedPhash) {
        var myUserJid = resolveMyDeviceJid(chatJid).toUserJid();
        var deviceLists = getDeviceLists(
                List.of(chatJid.toUserJid(), myUserJid), "message", expectedPhash, false);

        var mePnDeviceJid = store.jid().orElse(null);
        var meLidDeviceJid = store.lid().orElse(null);
        var fanoutDevices = fanoutCalculator.calculate(
                mePnDeviceJid, meLidDeviceJid, deviceLists, chatJid);

        var changedIdentities = store.unconfirmedIdentityChanges();
        return fanoutCalculator.filterIdentityChanges(fanoutDevices, changedIdentities);
    }

    /**
     * Returns the recipient device JIDs and the participant hash for a group send.
     *
     * @apiNote
     * Mirrors {@code WAWebDBDeviceListFanout.getFanOutList} with the
     * {@code chatWidSetToIncludeHostedInFanoutOneToOneChatOnly} argument left empty so
     * hosted devices (id 99) are excluded from group fanout; consumed by the chat-message
     * sender after participant resolution. The returned
     * {@link DeviceGroupFanoutResult#phash()} is what the {@code <enc phash="...">}
     * attribute on the outgoing stanza carries.
     *
     * @implNote
     * This implementation pulls the participant roster via
     * {@link WhatsAppClient#queryChatMetadata(Jid)}
     * because Cobalt has no IDB-backed group cache; it then runs
     * {@link DeviceFanoutCalculator#calculate(Jid, Jid, List, Jid)} with both
     * me-device JIDs so the {@code isMeDevice}/{@code isMeAccount} filter applies to
     * both addressing-mode sides, and computes the {@code phashV2} over the filtered
     * recipient set unioned with {@code senderDeviceJid}.
     *
     * @param groupJid        the group JID
     * @param senderDeviceJid the sender's own device JID; folded into the phash but
     *                        not into the returned recipient list
     * @return the fanout result with recipients and phash
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
     * Returns the fanout device JIDs and participant hash for a business
     * broadcast list whose recipient roster is supplied directly by the
     * caller.
     *
     * @apiNote
     * Broadcast lists carry their roster in the local
     * {@link com.github.auties00.cobalt.model.business.BusinessBroadcastList}
     * record rather than in server-side group metadata, so the caller
     * passes the resolved recipient user JIDs explicitly instead of
     * having this method look them up. The phash is computed over the
     * resolved device set plus {@code senderDeviceJid}, matching the
     * {@link #getGroupFanout(Jid, Jid)} convention.
     *
     * @implNote
     * This implementation reuses {@link #getDeviceLists(Collection, String, String, boolean)},
     * the {@link DeviceFanoutCalculator}, and the identity-change filter
     * verbatim from {@link #getGroupFanout(Jid, Jid)}; the only divergence
     * is the skipped {@link WhatsAppClient#queryChatMetadata(Jid)}
     * call, which has no counterpart for client-only audiences.
     *
     * @param broadcastJid      the broadcast list JID, used for
     *                          diagnostic purposes only
     * @param senderDeviceJid   the sender's own device JID, included in
     *                          the phash but not in the returned device
     *                          list
     * @param recipientUserJids the resolved recipient user JIDs from
     *                          the local broadcast list roster
     * @return the fanout result with devices and phash
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = "phashV2",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceGroupFanoutResult getBroadcastFanout(Jid broadcastJid, Jid senderDeviceJid, Collection<Jid> recipientUserJids) {
        try {
            var deviceLists = getDeviceLists(recipientUserJids, "message", null, false);
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
     * Returns the Identity Change Detection Consistency (ICDC) metadata for an outgoing
     * message to {@code userJid}.
     *
     * @apiNote
     * Mirrors {@code WAWebIdentityIcdcApi.getICDCMeta}; the chat-message sender calls this
     * to populate {@code DeviceListMetadata} on outgoing {@code <message>} stanzas so
     * recipients can detect when the sender's view of their device list has drifted from
     * the server's view. The result is empty when no device list is cached, when the
     * cached list is a tombstone, or when the user is a single-primary-device entry
     * whose timestamp is older than the {@code 30d} freshness window.
     *
     * @implNote
     * This implementation delegates to {@link IcdcComputer#compute(Jid)}; the underlying
     * hash truncation length is read from the {@link ABProp} cache to match
     * {@code md_icdc_hash_length} on the WA Web side.
     *
     * @param userJid the user JID, normalised to user-level by the computer
     * @return the ICDC metadata, or {@link Optional#empty()} when no usable record is
     *         cached
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMeta",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<IcdcResult> computeIcdc(Jid userJid) {
        return icdcComputer.compute(userJid);
    }

    /**
     * Ensures every device JID in {@code deviceJids} has an established Signal session,
     * fetching pre-key bundles for the ones that do not.
     *
     * @apiNote
     * Mirrors {@code WAWebManageE2ESessionsJob.ensureE2ESessions}; callers (the
     * chat-message sender, the retry-receipt handler, the broadcast sender) invoke this
     * before the encrypt step so the {@link SignalSessionCipher}
     * does not error out on a missing session. Devices that already have a session are
     * skipped, so the call is cheap on a warm address book.
     *
     * @implNote
     * This implementation delegates to {@link DevicePreKeyHandler#ensureSessions(Collection)};
     * non-user JIDs and PSA broadcast JIDs are filtered out, the {@code 406} server status
     * for a non-existent companion device is swallowed (the affected device is reported
     * back as {@code deletedDevices} in WA Web; Cobalt currently only surfaces the
     * depleted-pre-key count via the returned int).
     *
     * @param deviceJids the device JIDs to ensure sessions for
     * @return the number of devices whose server response carried no {@code <key>}
     *         element (a depleted one-time pre-key pool); callers should commit this
     *         count to a
     *         {@link com.github.auties00.cobalt.wam.event.PrekeysDepletionEventBuilder}
     *         to mirror {@code WAWebPostPrekeysDepletionMetric.maybePostPrekeysDepletionMetric}
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public int ensureSessions(Collection<Jid> deviceJids) {
        return preKeyHandler.ensureSessions(deviceJids);
    }

    /**
     * Returns the local sender's device JID in the addressing mode that matches
     * {@code chatJid}.
     *
     * @apiNote
     * Mirrors the {@code WAWebUserPrefsMeUser.getMeDeviceOrThrow} branching when WA Web's
     * {@code removeDevicePnDependenciesEnabled} gate is set; consulted by
     * {@link #getUserFanout(Jid, String)} so that LID-addressed chats receive a
     * LID-addressed sender for the self filter.
     *
     * @implNote
     * This implementation falls back to the PN device JID when no LID is recorded for the
     * current user, matching the {@code getMeDeviceLidOrThrow} fallback chain in WA Web.
     *
     * @param chatJid the chat JID that determines addressing mode
     * @return the sender's device JID, in the matching addressing mode
     * @throws IllegalStateException when the {@link WhatsAppStore} has no recorded
     *                               {@link Jid#device()}
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
     * Returns whether business hosted devices (id 99 device entries) are accepted in
     * this session.
     *
     * @apiNote
     * Mirrors {@code WAWebBizCoexGatingUtils.bizHostedDevicesEnabled}; consulted by every
     * notification path before deciding whether to surface a hosted-companion device on
     * the cached {@link DeviceList}, and by {@link #filterHostedDevicesFromResults(List)}
     * when stripping hosted devices from USync results for sessions that opt out.
     *
     * @implNote
     * This implementation reads {@link ABProp#ADV_ACCEPT_HOSTED_DEVICES} from the
     * {@link ABPropsService}; WA Web reads the same AB-prop key under three separate
     * exported function names ({@code bizHostedDevicesEnabled},
     * {@code bizHostedDevicesSystemMessageEnabled},
     * {@code hostedDeviceSecurityCodeVerificationEnabled}) but Cobalt collapses them to
     * one call since the underlying gate is identical.
     *
     * @return {@code true} when {@code adv_accept_hosted_devices} is set
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "bizHostedDevicesEnabled",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Returns {@code results} with hosted devices (id {@link DeviceConstants#HOSTED_DEVICE_ID})
     * stripped from every {@link DeviceListResult.Full} entry.
     *
     * @apiNote
     * Applied at the head of {@code WAWebAdvHandlerApi.handleADVDeviceSyncResult} when
     * {@link #isBizHostedDevicesEnabled()} returns {@code false}, so that a session that
     * has opted out of business hosted devices never persists a hosted entry to its
     * cached device list nor encrypts to it.
     *
     * @implNote
     * This implementation maps each entry through
     * {@link DeviceListResult#withoutHostedDevices()}, leaving
     * {@link DeviceListResult.Omitted} and {@link DeviceListResult.Error} variants
     * unchanged because they carry no device list.
     *
     * @param results the parsed USync results
     * @return the results with hosted devices removed from every full entry
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
     * Dispatches an incoming {@code <notification type="devices">} stanza to the matching
     * add or remove handler under the device-update lock.
     *
     * @apiNote
     * Mirrors {@code WAWebAdvHandlerApi.handleADVDeviceNotification}; the notification
     * dispatcher in {@link WhatsAppClient} routes
     * {@code <notification type="devices">} stanzas here so the cached
     * {@link DeviceList} stays consistent with companion-device add and remove events
     * pushed by the server outside the daily USync.
     *
     * @implNote
     * This implementation rejects the notification when the {@code key-index-list} or
     * its {@code ts} attribute is missing (WA Web errors with {@code "notification
     * without type"} for the equivalent missing case), then routes through
     * {@link #withDeviceUpdateLock(Runnable)} so the add and remove handlers serialise
     * against the daily ADV check and against {@link #handleADVDeviceUpdateForMessage}.
     *
     * @param node    the {@code <notification>} root carrying {@code device-list} and
     *                {@code key-index-list} children
     * @param action  the {@code "add"} or {@code "remove"} action tag
     * @param userJid the user whose device list is being mutated
     * @throws NullPointerException if any argument is {@code null}
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
     * Applies a companion-add notification on top of the cached {@link DeviceList}.
     *
     * @apiNote
     * Drives the cache update WA Web performs when a user's primary device pushes a
     * new companion (a new linked phone, tablet or Web/Desktop session) to its
     * contacts. When no cached record exists the call is bounced to
     * {@link #triggerUsyncForCoexDeviceAdd(Node, Jid)} so the full list is fetched
     * instead of being synthesised from the notification alone.
     *
     * @implNote
     * This implementation matches the {@code WAWebHandleAdvDeviceNotificationApi.handleDeviceAddNotification}
     * shape: it rejects out-of-order notifications by comparing {@code timestamp} to the
     * cached {@link DeviceList#timestamp()}, validates the signed key-index list (using
     * the {@code accountSignatureKey} hosted path when both
     * {@link #isBizHostedDevicesEnabled()} and the presence of an
     * {@code is_hosted="true"} attribute on the device-list demand it), then merges the
     * cached devices (keeping those still in the signed {@code validIndexes} or with
     * {@code keyIndex > currentIndex}) with the notification's devices, dropping the
     * primary device from both inputs before re-inserting it last. A {@code rawId}
     * mismatch or an account-type transition triggers a full session cleanup via
     * {@link #cleanupAllSessionsForUser(Jid, DeviceList)} and a
     * {@link #handleAccountTypeTransition} call before the merge.
     *
     * @param userJid          the user whose device list is being mutated
     * @param deviceListNode   the {@code device-list} child node, possibly {@code null}
     * @param keyIndexListNode the {@code key-index-list} child node
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

        var keyIndexMap = buildKeyIndexMap(deviceListNode);

        var validatedKeyIndexInfo = validateKeyIndexList(userJid, deviceListNode, keyIndexListNode);

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
     * Parses a single {@code <device>} entry from an add-notification and applies the
     * notification-only validation gate.
     *
     * @apiNote
     * Helper for {@link #handleDeviceAddNotification(Jid, Node, Node, long)}; returns an
     * empty stream rather than throwing so the caller can {@code flatMap} the children
     * without nullable plumbing.
     *
     * @implNote
     * This implementation enforces the stricter notification rule that every non-primary
     * device must have a {@code keyIndex} present in the signed
     * {@link ValidatedKeyIndexListResult#validIndexes()} set; the {@code keyIndex > currentIndex}
     * tolerance that {@link #handleNoListReset(Jid, DeviceList, DeviceList)} grants to
     * cached devices is intentionally absent here because the notification is the
     * authoritative carrier of newly-published indexes. Hosted devices
     * (id {@link DeviceConstants#HOSTED_DEVICE_ID}) are additionally gated by
     * {@link #isBizHostedDevicesEnabled()}; when the gate is off, the hosted device is
     * silently dropped rather than rejected.
     *
     * @param deviceNode            the {@code <device>} child node
     * @param keyIndexMap           the {@code (id, keyIndex)} map for the whole
     *                              device-list, as produced by
     *                              {@link #buildKeyIndexMap(Node)}
     * @param validatedKeyIndexInfo the verified key-index list, or {@code null} when no
     *                              signed list was provided
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
     * Applies a companion-remove notification on top of the cached {@link DeviceList}.
     *
     * @apiNote
     * Mirrors {@code WAWebHandleAdvDeviceNotificationApi.handleDeviceRemoveNotification};
     * called for {@code <notification type="devices" action="remove">} stanzas after a
     * user unlinks a companion device. Out-of-order notifications (timestamp before the
     * cached one) and notifications without a cached record are silently dropped.
     *
     * @implNote
     * This implementation retains a cached device when either the notification omits its
     * id, or carries a different {@code keyIndex} for it (the device is treated as still
     * present under a fresh key); the primary device is dropped from the cache filter
     * and unconditionally re-appended last so the resulting list always has it. Removed
     * devices have their Signal sessions cleaned up via
     * {@link WhatsAppStore#cleanupSignalSessions(Jid)}.
     *
     * @param userJid          the user whose device list is being mutated
     * @param deviceListNode   the {@code device-list} child node listing the removed
     *                         devices, possibly {@code null} for a no-op remove
     * @param keyIndexListNode the {@code key-index-list} child node (unused by this
     *                         branch, retained for parity with the add path)
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
     * Returns the {@code (deviceId, keyIndex)} projection of the {@code <device>}
     * children of {@code keyIndexListNode}.
     *
     * @apiNote
     * Used by both {@link #handleDeviceAddNotification(Jid, Node, Node, long)} and
     * {@link #handleDeviceRemoveNotification(Jid, Node, Node, long)} to translate the
     * stanza shape into the lookup table the merge logic expects.
     *
     * @implNote
     * This implementation collects through {@link Collectors#toUnmodifiableMap} so
     * downstream code cannot mutate the table; entries with a missing JID or an
     * unparseable {@code key-index} attribute are silently dropped by
     * {@link #parseDevice(Node)}.
     *
     * @param keyIndexListNode the node whose {@code device} children are parsed
     * @return the unmodifiable {@code id -> keyIndex} map
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationApi",
            exports = "handleDeviceAddNotification",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static Map<Integer, Integer> buildKeyIndexMap(Node keyIndexListNode) {
        return keyIndexListNode.streamChildren("device")
                .flatMap(DefaultDeviceService::parseDevice)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Parses a single {@code <device jid="..." key-index="...">} child into a
     * {@code (deviceId, keyIndex)} {@link Map.Entry}.
     *
     * @apiNote
     * Helper for {@link #buildKeyIndexMap(Node)}; returns a stream so callers may
     * {@code flatMap} without nullable handling for malformed children.
     *
     * @implNote
     * This implementation extracts the {@link Jid#device()} portion via
     * {@link Node#getAttributeAsJid(String)} and validates {@code key-index} via the
     * {@link Node#getAttributeAsInt(String, int)} sentinel form; the WA Web counterpart
     * relies on the JS parser producing {@code undefined} for missing attributes and is
     * structurally equivalent.
     *
     * @param deviceNode the {@code <device>} child node
     * @return a single-element stream with the parsed entry, or an empty stream when
     *         the {@code jid} or {@code key-index} attribute is missing or unparseable
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
     * Returns the decoded and signature-verified {@link ValidatedKeyIndexListResult}
     * carried by an ADV device notification.
     *
     * @apiNote
     * Mirrors the {@code WAWebHandleAdvDeviceNotificationUtils.decodeSignedKeyIndexBytes}
     * call and its sibling {@code verifySKeyIndexWithAccSigKey} branch in WA Web's
     * notification path; called by {@link #handleDeviceAddNotification(Jid, Node, Node, long)}
     * before the merge decides whether to trust the notification's device list.
     *
     * @implNote
     * This implementation picks the verification key per WA Web's notification rule: when
     * {@link #isBizHostedDevicesEnabled()} is on AND the {@code device-list} carries any
     * child with {@code is_hosted="true"}, the verification uses the embedded
     * {@code accountSignatureKey} (the hosted-path
     * {@link DeviceADVValidator#verifySKeyIndexWithAccSigKey(byte[])}); otherwise the
     * verification key is the user's locally-stored primary identity. Returns
     * {@code null} (matching WA Web's null-on-failure contract) when the content bytes
     * are missing or the signature check fails; the caller logs and drops the
     * notification.
     *
     * @param userJid          the user whose stored primary identity is the verification
     *                         key in the non-hosted branch
     * @param deviceListNode   the {@code device-list} child node, inspected for the
     *                         hosted-device sentinel
     * @param keyIndexListNode the {@code key-index-list} child whose content bytes carry
     *                         the signed protobuf
     * @return the validated key-index data, or {@code null} when validation fails
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
     * Triggers a follow-up USync to materialise the full device list when an add
     * notification arrives without a cached record.
     *
     * @apiNote
     * Mirrors {@code WAWebBizCoexUtils.triggerUsyncForCoexDeviceAdd}; the entry point is
     * the no-cached-record branch of
     * {@link #handleDeviceAddNotification(Jid, Node, Node, long)}. The
     * {@code coex_device_notification} context tag flows through to the IQ so server-side
     * logging can correlate the sync with the notification that prompted it.
     *
     * @implNote
     * This implementation routes the USync immediately when the resume-from-restart
     * sequence is finished
     * ({@link WhatsAppStore#isResumeFromRestartComplete()}); during resume, the request
     * is buffered as a {@link PendingDeviceSync} so it replays alongside the rest of the
     * suspended sync queue once the socket reaches steady state, matching WA Web's
     * {@code OfflinePendingDeviceCache.addOfflinePendingDevice} path.
     *
     * @param deviceListNode the {@code device-list} child node, possibly {@code null};
     *                       a hosted-device sentinel inside it only changes the log line
     * @param userJid        the user whose device list needs syncing
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
     * Returns whether {@code deviceListNode} contains a child {@code <device>} with the
     * hosted sentinel id {@link DeviceConstants#HOSTED_DEVICE_ID}.
     *
     * @apiNote
     * Diagnostic helper for {@link #triggerUsyncForCoexDeviceAdd(Node, Jid)}; used only
     * to enrich the queued-during-resume log line so operators can tell hosted-device
     * notifications apart in the logs without grepping the raw stanza.
     *
     * @param deviceListNode the {@code device-list} node
     * @return {@code true} when any child device carries the hosted id
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexUtils",
            exports = "triggerUsyncForCoexDeviceAdd",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean hasHostedDevice(Node deviceListNode) {
        return deviceListNode.streamChildren("device")
                .anyMatch(deviceNode -> deviceNode.hasAttribute("id", DeviceConstants.HOSTED_DEVICE_ID));
    }

    /**
     * Re-syncs the device list for both of the current user's identities (PN and LID).
     *
     * @apiNote
     * Mirrors {@code WAWebAdvSyncDeviceListApi.syncMyDeviceList}; called after pair-success
     * and from the resume-from-restart path so that the local view of self-companion
     * devices is current before the message-processing loop starts attributing inbound
     * stanzas to specific devices.
     *
     * @implNote
     * This implementation collects both me-JIDs via {@link WhatsAppStore#jid()} and
     * {@link WhatsAppStore#lid()} so a session that has only one of them (initial pair,
     * or LID-less legacy session) still fires a sync for whichever it has; if neither is
     * present, the call is a no-op rather than throwing, matching the WA Web
     * {@code getMePNandLIDWids} contract that returns an empty array in that state.
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
     * Returns the cached {@link DeviceList} for each input JID after first issuing a
     * USync sweep over the same set.
     *
     * @apiNote
     * Mirrors {@code WAWebAdvSyncDeviceListApi.syncAndGetDeviceList}; intended for paths
     * that need both a fresh sync and the resulting cache rows in one round trip (for
     * example, callers driving an immediate fanout after a contact add). Slots without a
     * record surface as {@code null} in the returned list.
     *
     * @param userJids the user JIDs to sync and retrieve
     * @return one record per input JID, in input order; {@code null} for missing or
     *         tombstoned slots
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSyncDeviceListApi",
            exports = "syncAndGetDeviceList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<DeviceList> syncAndGetDeviceList(Collection<Jid> userJids) {
        getDeviceLists(userJids, null, null, false);
        return getDeviceIds(userJids, false);
    }

    /**
     * Returns the cached {@link DeviceList} for {@code userJid}, recording the lookup
     * via {@link #checkPnToLidMapping(Collection, String)}.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.getDeviceRecord}; the read path used by every
     * consumer that wants the cached entry but does not need to trigger a sync. The
     * {@code waweb_api_device_list_get_device_record} mapping-check tag is preserved so
     * the LID-PN drift telemetry matches WA Web's bucket name.
     *
     * @implNote
     * This implementation fires the mapping check unconditionally (matching WA Web's
     * shape, where the LRU always returns a settled promise) even when no record is
     * cached, so the absence of a record still flows into the LID-PN drift counter.
     *
     * @param userJid the user JID; only the user portion is significant
     * @return the cached record, or {@link Optional#empty()} when none is stored
     * @throws NullPointerException if {@code userJid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<DeviceList> getDeviceRecord(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        var record = store.findDeviceList(userJid);
        checkPnToLidMapping(List.of(userJid), "waweb_api_device_list_get_device_record");
        return record;
    }

    /**
     * Returns one cached {@link DeviceList} per input JID, in input order.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.bulkGetDeviceRecord}; the batched companion to
     * {@link #getDeviceRecord(Jid)}, used by sync-result handlers and the IDB-backed
     * pre-key fetch path. Slots without a cached record are {@code null}, allowing the
     * caller to align inputs and outputs positionally.
     *
     * @implNote
     * This implementation fires a single
     * {@link #checkPnToLidMapping(Collection, String)} call only over JIDs that did
     * resolve to a non-null record, matching WA Web's filtered call that excludes
     * un-resolved entries (the LID-PN drift counter does not count misses).
     *
     * @param userJids the user JIDs to look up
     * @return one record per input JID, in input order; {@code null} for missing slots
     * @throws NullPointerException if {@code userJids} is {@code null}
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
     * Persists a single {@link DeviceList} and refreshes its cache slot.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.createOrReplaceDeviceRecord}; the write half of
     * the cache, used after every sync, notification, and ICDC repair to commit the
     * resolved {@link DeviceList}. The owning {@link DeviceList#userJid()} is also the
     * primary key in the underlying store.
     *
     * @implNote
     * This implementation fires
     * {@link #checkPnToLidMapping(Collection, String)} over the single owner JID and
     * routes through {@link #warnIfDeletingOwnDeviceList(DeviceList)} so that a
     * tombstone targeting the local account produces the
     * {@code "syncd: trying to delete own device list"} warning WA Web logs.
     *
     * @param record the record to persist
     * @throws NullPointerException if {@code record} is {@code null}
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
     * Persists a batch of {@link DeviceList} records and refreshes their cache slots.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.bulkCreateOrReplaceDeviceRecord}; used by the
     * sync-result fan-in path that produces many records per IQ. Sharing one
     * {@link #checkPnToLidMapping(Collection, String)} call across the batch matches the
     * WA Web telemetry shape (one bucket per IQ, not one per record).
     *
     * @implNote
     * This implementation iterates the records sequentially, invoking
     * {@link WhatsAppStore#addDeviceList(DeviceList)} and
     * {@link #warnIfDeletingOwnDeviceList(DeviceList)} per record; WA Web wraps the same
     * loop in an IDB bulk write, which Cobalt has no per-record cost to amortise.
     *
     * @param records the records to persist
     * @throws NullPointerException if {@code records} is {@code null}
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
     * Returns the cached {@link DeviceList} for each input JID with deleted slots
     * projected to {@code null}, optionally folding in the alternate-identity record
     * for each.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.getDeviceIds}; the fanout-side read path used by
     * {@code WAWebDBDeviceListFanout.getFanOutList}. When {@code shouldMergeAltDevices}
     * is {@code true}, the alternate-identity record (PN for a LID input, LID for a PN
     * input) is unioned into the primary's device set so the caller does not have to
     * issue two lookups for users that exist on both addressing modes.
     *
     * @implNote
     * This implementation resolves the alternate user JID via
     * {@link #findAlternateUserWid(Jid)}, then for the merge branch deduplicates by
     * device id (the primary's entry wins on conflict) and synthesises a record under
     * the original JID when only the alternate is cached, matching WA Web's
     * {@code byOwner.set(...)} fallback. Non-regular JIDs (no user, LID, bot, hosted,
     * or hosted-LID server) bypass the alternate lookup entirely to match WA Web's
     * {@code isUser()} short-circuit.
     *
     * @param userJids              the user JIDs to look up
     * @param shouldMergeAltDevices whether to fold alternate-identity records into the
     *                              result
     * @return one record per input JID, in input order; missing or tombstoned slots are
     *         {@code null}
     * @throws NullPointerException if {@code userJids} is {@code null}
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
     * Returns the cached {@link DeviceList} for each input JID with deleted slots
     * projected to {@code null}.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.getDeviceInfoForSync}; the lightweight
     * sync-side read used by {@code WAWebAdvSyncDeviceListApi} to pull the cached
     * timestamps and {@code expectedTs} fields before deciding which users still need
     * a USync, bypassing the alternate-identity merge that
     * {@link #getDeviceIds(Collection, boolean)} performs.
     *
     * @param userJids the user JIDs to look up
     * @return one record per input JID, in input order; missing or tombstoned slots
     *         are {@code null}
     * @throws NullPointerException if {@code userJids} is {@code null}
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
     * Logs a {@code "syncd: trying to delete own device list"} warning when {@code record}
     * is a tombstone whose owner matches one of the local user's identities.
     *
     * @apiNote
     * Called from {@link #createOrReplaceDeviceRecord(DeviceList)} and
     * {@link #bulkCreateOrReplaceDeviceRecord(Collection)}; surfacing this warning
     * matches the diagnostic WA Web's file-private helper {@code f(t)} emits when an
     * accidental self-tombstone reaches the persistence layer.
     *
     * @implNote
     * This implementation considers both the PN ({@link WhatsAppStore#jid()}) and LID
     * ({@link WhatsAppStore#lid()}) projections of the local user; the warning is
     * logged-only and never blocks the write because the deletion is sometimes the
     * deliberate outcome of an account-type transition.
     *
     * @param record the record about to be persisted
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
     * Returns the alternate-identity user JID for {@code userJid}, mapping LID to PN and
     * vice versa.
     *
     * @apiNote
     * Mirrors {@code WAWebApiContact.getAlternateUserWid}; used by
     * {@link #getDeviceIds(Collection, boolean)} during the alternate-identity merge.
     * Returns {@code null} when no mapping exists rather than throwing, so the caller
     * can skip merge attempts for users that exist on only one addressing mode.
     *
     * @implNote
     * This implementation short-circuits via the local me-user pair when the input
     * matches one of them (returning the other side without a store lookup); otherwise
     * it consults {@link WhatsAppStore#findLidByPhone(Jid)} or
     * {@link WhatsAppStore#findPhoneByLid(Jid)} as appropriate. Device-scoped JIDs are
     * tolerated (no throw, unlike WA Web's {@code getAlternateUserWid} which rejects
     * device wids), because Cobalt callers may pass them without first downcasting to
     * user level.
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
     * Returns whether {@code userJid}'s cached device list contains {@code deviceId}.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.hasDevice}; consulted by message paths that need
     * to know whether a specific companion id is known before encrypting to it or
     * surfacing it in UI. The primary device (id
     * {@link DeviceConstants#PRIMARY_DEVICE_ID}) is unconditionally treated as present,
     * matching WA Web's {@code DEFAULT_DEVICE_ID} fast path.
     *
     * @implNote
     * This implementation returns {@code false} for tombstoned and missing records,
     * matching the WA Web shape where {@code getDeviceIds([e])[0]} resolves to a
     * {@code null}-projected entry.
     *
     * @param userJid  the user JID
     * @param deviceId the device id to look for
     * @return {@code true} when the device is present
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
     * Returns the cached {@link DeviceList} for the current user, preferring the PN
     * record and falling back to the LID record.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.getMyDeviceList}; consulted by syncd-style
     * code paths that need the local companion roster to attribute syncd writes to a
     * specific local device. The PN-then-LID order matches WA Web's
     * {@code getMeDevicePnOrThrow_DO_NOT_USE} preference.
     *
     * @implNote
     * This implementation logs the {@code "[syncd] no device list pn=.../... lid=.../..."}
     * diagnostic with the same shape WA Web emits before throwing, so the operator can
     * tell from the log whether the PN or LID record was the missing piece.
     *
     * @return the current user's cached device list
     * @throws IllegalStateException when neither the PN nor the LID record is cached
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
     * Returns every cached {@link DeviceList}.
     *
     * @apiNote
     * Mirrors {@code WAWebApiDeviceList.getAllDeviceLists}; the bulk read consumed by
     * {@link DeviceADVChecker} when it scans for users whose key-index lists are about
     * to expire. The {@code "getAllDeviceLists: got %s devices, took %sms"} diagnostic
     * matches WA Web's log so operators can compare the two corpora at a glance.
     *
     * @return an unmodifiable snapshot of all cached device lists
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
     * Processes the Identity Change Detection Consistency (ICDC) metadata carried by an
     * incoming message.
     *
     * @apiNote
     * Mirrors {@code WAWebIcdcHandlerApi.handleICDCData}; the message receiver invokes
     * this so the cached device-list {@code expectedTs}, {@code expectedTsLastDeviceJobTs}
     * and {@code expectedTsUpdateTs} fields on the sender (and on the 1:1 recipient when
     * the sender is self) get bumped, which feeds the daily ADV expiration check.
     * A primary-device message carrying only a sender timestamp (no key hash) takes the
     * minimal sync path so the cache reflects the implicit
     * {@code <device-list>=[{primary}]} that the JS path materialises.
     *
     * @implNote
     * This implementation builds an explicit list of
     * {@link IcdcTimestampEntry} rather than the inline JS array literal so the loop
     * remains readable; the {@code +1s} adjustment on the minimal-sync branch matches
     * WA Web's {@code numberOrThrowIfTooLarge(r.senderTimestamp)+1} exactly.
     *
     * @param senderJid        the sender device JID
     * @param recipientUserJid the recipient user JID for 1:1 chats, {@code null} for
     *                         group sends
     * @param metadata         the {@code DeviceListMetadata} extracted from the
     *                         message's context info; the call is a no-op when this is
     *                         {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebIcdcHandlerApi",
            exports = "handleICDCData",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handleICDCData(Jid senderJid, Jid recipientUserJid, DeviceListMetadata metadata) {
        if (metadata == null) {
            return;
        }

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
     * Applies the implicit primary-only device-list that a primary-device message with
     * only a sender timestamp carries.
     *
     * @apiNote
     * Invoked by {@link #handleICDCData(Jid, Jid, DeviceListMetadata)} on the
     * {@code device==0 AND senderKeyHash==null} branch; materialises the
     * {@code [{primary}]} device list inline so the cache reflects the same shape that
     * WA Web's {@code handleADVSyncResult([{id: DEFAULT_DEVICE_ID, keyIndex: 0}], ...)}
     * call writes.
     *
     * @implNote
     * This implementation drops the call when no record is cached, when the record is a
     * tombstone, or when {@code adjustedTimestamp} is older than the cached one. The
     * expected-timestamp fields are cleared per
     * {@link DeviceExpectedTsUtils#shouldClearExpectedTimestamp(Instant, Instant, DeviceList, Instant)}
     * so the daily ADV check does not re-trigger immediately on the rewritten record.
     *
     * @param userJid           the user JID
     * @param adjustedTimestamp the sender timestamp plus one second, matching WA Web's
     *                          off-by-one bump
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
     * Carries one ICDC {@code (jid, timestamp)} pair through
     * {@link #handleICDCData(Jid, Jid, DeviceListMetadata)}.
     *
     * @apiNote
     * Local helper that lets the ICDC handler iterate the sender and (optional 1:1)
     * recipient entries with the same body; never escapes the file.
     *
     * @param jid       the user JID for this entry
     * @param timestamp the matching per-side timestamp from the message metadata, or
     *                  {@code null} when absent
     */
    private record IcdcTimestampEntry(Jid jid, Instant timestamp) {
    }

    /**
     * Processes hosted-business ICDC metadata inline during message receipt.
     *
     * @apiNote
     * Mirrors {@code WAWebIcdcHandlerApi.handleHostedIcdcMetadataInline}; called by the
     * 1:1 message receiver before decryption so a freshly-observed {@code HOSTED}
     * account type triggers a device-list clear and the appropriate
     * {@code hostedBizEncMismatch} signal that surfaces in the
     * {@link HostedIcdcResult}. The {@code E2EE} branch removes the sender from the
     * one-shot dedup cache so a later hosted transition is not silently ignored.
     *
     * @implNote
     * This implementation short-circuits with {@link HostedIcdcResult#DEFAULT} when
     * {@link #isBizHostedDevicesEnabled()} is off, when the chat is the local user, or
     * when the chat JID is not a user JID, mirroring WA Web's
     * {@code isMeAccount || !isUser()} guard. The relevant account type is taken from
     * the receiver side when the message author is self, otherwise from the sender
     * side, matching the {@code d ? receiverAccountType : senderAccountType} branch in
     * WA Web. Resume-incomplete sessions buffer the follow-up sync via
     * {@link PendingDeviceSync}; otherwise the sync runs on a fresh virtual thread so
     * the message receiver thread does not block waiting for the IQ.
     *
     * @param chatJid   the chat JID; must be a user JID for the call to be non-default
     * @param authorJid the message author JID; equality with the local user selects the
     *                  receiver-side account type
     * @param metadata  the {@code DeviceListMetadata}; {@code null} produces a default
     *                  result
     * @return the hosted-ICDC outcome flags consumed by the message receiver
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

                var mismatch = (existingRecord != null && existingRecord.advAccountType() == ADVEncryptionType.E2EE)
                        || (existingRecord == null || !existingRecord.deletedChangedToHost());
                return new HostedIcdcResult(mismatch, true);
            }

            return new HostedIcdcResult(false, true);
        }

        return HostedIcdcResult.DEFAULT;
    }

    /**
     * Applies an ADV companion-device update carried inline by an incoming
     * {@code pkmsg} signed device identity.
     *
     * @apiNote
     * Mirrors {@code WAWebAdvHandlerApi.handleADVDeviceUpdateForMessage}; the message
     * receiver calls this when a {@code pkmsg} from a companion arrives with a fresh
     * signed device identity attached, so the cached {@link DeviceList} reflects the
     * new {@code keyIndex} without waiting for the next USync. The primary device is
     * not a valid input because primary-device updates come through pair-success and
     * the daily ADV check, not through {@code pkmsg}.
     *
     * @implNote
     * This implementation chooses between a full list reset and an incremental key-index
     * bump: a reset is forced when no record is cached, when the cached record is a
     * tombstone, when the {@code rawId} changed, or when the local identity bytes
     * differ from the message-side primary identity. The reset path materialises a
     * primary-plus-this-companion list backdated by
     * {@code (NUM_DAYS_KEY_INDEX_LIST_EXPIRATION - 1) * 1d} so the next ADV check
     * revalidates the user immediately, then re-persists the existing identity for the
     * primary so the saved primary identity does not drift to the message-side primary
     * identity (WA Web preserves this invariant via the same {@code putIdentity} dance).
     * The incremental path rejects out-of-order timestamps that carry an unknown
     * {@code keyIndex} (the same check that
     * {@link #handleNoListReset(Jid, DeviceList, DeviceList)} runs for full syncs);
     * identity changes that surface in the cached-vs-new diff are recorded via
     * {@link WhatsAppStore#markIdentityChange(Jid)} and their Signal sessions cleaned
     * up, matching the {@code mismatch.identityChangedDevices} branch.
     *
     * @param deviceJid   the companion's full device JID
     * @param rawId       the {@code rawId} from the signed device identity
     * @param timestamp   the timestamp from the signed device identity, in Unix
     *                    seconds
     * @param keyIndex    the {@code keyIndex} from the signed device identity
     * @param identityKey the message-side primary identity bytes, or {@code null} when
     *                    the message omitted them
     * @param accountType the ADV account type, or {@code null} when unknown
     * @throws IllegalArgumentException when {@code deviceJid} is the primary device
     * @throws IllegalStateException    when the incremental path detects an out-of-order
     *                                  timestamp combined with an unknown
     *                                  {@code keyIndex}
     * @throws NullPointerException     when {@code rawId} is {@code null}
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
     * Returns the validated {@link ADVSignedDeviceIdentity} extracted from the
     * {@code <device-identity>} node of a pair-success stanza.
     *
     * @apiNote
     * Drives the pair-success handling sequence that
     * {@code WAWebHandlePairSuccess.handlePairSuccess} performs in WA Web: validates
     * the {@code ADVSignedDeviceIdentityHMAC} against the {@code advSecretKey},
     * verifies the embedded account signature against the local identity key, and
     * generates the device signature so the constructed identity is ready to be sent
     * back in the pair-device-sign response.
     *
     * @implNote
     * This implementation surfaces validation failures as
     * {@link Optional#empty()} after notifying the {@link WhatsAppClient} via
     * {@link WhatsAppClient#handleFailure(Throwable)}, matching WA Web's path that
     * logs a pair error and triggers
     * {@code WAWebCompanionRegUtils.logoutAfterValidationFail} without rethrowing to
     * the caller.
     *
     * @param deviceIdentityNode the {@code device-identity} node from the pair-success
     *                           stanza
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
     * Persists the {@code accountSignatureKey} from a validated pair-success identity
     * as the local user's primary Signal identity.
     *
     * @apiNote
     * Mirrors WA Web's {@code putIdentity(createSignalAddress(asUserWidOrThrow(...)).toString(), ...)}
     * call inside {@code handlePairSuccess}; pinning the key against the user-level
     * (device 0) Signal address is what lets the next outgoing message to a companion
     * succeed without going through pre-key fetch.
     *
     * @implNote
     * This implementation tolerates {@link RuntimeException} from the signal store write
     * because WA Web also swallows the equivalent IndexedDB failure: the key is
     * rederived on the next ICDC/USync cycle, so a transient failure does not need to
     * roll back pair-success. The {@code null}/empty guard preserves the WA Web
     * behaviour of skipping the call when the embedded {@code accountSignatureKey} is
     * missing.
     *
     * @param deviceJid           the local device JID the server assigned during pair
     * @param accountSignatureKey the {@code accountSignatureKey} bytes, possibly
     *                            {@code null} or empty
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
