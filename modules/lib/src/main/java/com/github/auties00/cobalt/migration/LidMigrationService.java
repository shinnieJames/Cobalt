package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppLidMigrationException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatDisappearingMode;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.ChatMute;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.sync.history.HistorySync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMapping;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayload;
import com.github.auties00.cobalt.model.jid.migration.PhoneNumberToLIDMapping;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.setting.GlobalSettings;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.Lid11MigrationLifecycleEventBuilder;
import com.github.auties00.cobalt.wam.type.MigrationStageEnum;
import com.github.auties00.cobalt.wam.type.StageFailureReasonEnum;

import com.github.auties00.cobalt.util.SchedulerUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Orchestrates the 1:1 LID migration flow for a WhatsApp account and
 * exposes the LID/PN conversion helpers that the rest of the client
 * depends on.
 *
 * <p>WhatsApp introduced LID addressing to replace phone-number
 * identifiers in 1:1 conversations with privacy-preserving long
 * identifiers. Migration runs once per account. A paired client waits for
 * an AB prop, receives a mapping sync message from the primary device
 * describing how every known phone number maps to its assigned LID,
 * rewrites every eligible local chat to use the new address, deletes
 * chats that can no longer be resolved, and persists the mapping table so
 * that outgoing and incoming stanzas can be translated between addressing
 * modes.
 *
 * <p>Beyond the migration itself, this service also exposes the
 * conversion utilities ({@link #toLid(Jid)}, {@link #toPn(Jid)},
 * {@link #getAlternateMsgKey(MessageKey)} and friends) that the rest of
 * the client uses to freely move between PN and LID representations for
 * messages, chats, and the current user's identity.
 *
 * <p>WA Web spreads this same functionality across several single-purpose
 * modules ({@code WAWebLid1X1MigrationGating},
 * {@code WAWebLid1X1ThreadAccountMigrations},
 * {@code WAWebLid1x1MigrationPrimaryCache},
 * {@code WAWebLid1x1MigrationManager}, {@code WAWebLidMigrationUtils})
 * backed by various UserPrefs keys. Cobalt collapses them into this
 * single service with an explicit {@link LidMigrationState} state
 * machine.
 */
@WhatsAppWebModule(moduleName = "WAWebLid1X1MigrationGating")
@WhatsAppWebModule(moduleName = "WAWebLid1X1ThreadAccountMigrations")
@WhatsAppWebModule(moduleName = "WAWebLid1x1MigrationPrimaryCache")
@WhatsAppWebModule(moduleName = "WAWebLid1x1MigrationManager")
@WhatsAppWebModule(moduleName = "WAWebLid1x1MigrationTimeout")
@WhatsAppWebModule(moduleName = "WAWebLid1x1MigrationTimeoutUtils")
@WhatsAppWebModule(moduleName = "WAWebLid1x1MigrationMsgParser")
@WhatsAppWebModule(moduleName = "WAWebLidMigrationUtils")
@WhatsAppWebModule(moduleName = "WAWebApiContact")
public final class LidMigrationService {
    /**
     * Logger used to trace the lifecycle of the LID migration.
     */
    private static final System.Logger LOGGER = System.getLogger(LidMigrationService.class.getName());

    /**
     * Marker string for click-to-WhatsApp chats whose LID was created
     * with phone-number hiding.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsernameTypes", exports = "LidOriginType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String LID_ORIGIN_TYPE_PNH_CTWA = "ctwa";

    /**
     * Marker string for regular LID chats that have no phone-number
     * hiding ancestry.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsernameTypes", exports = "LidOriginType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String LID_ORIGIN_TYPE_GENERAL = "general";

    /**
     * Stub message types that the LID migration considers safe and can
     * be ignored when deciding whether a chat may be deleted.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "X",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final Set<ChatMessageInfo.StubType> MIGRATION_SAFE_STUB_TYPES = EnumSet.of(
            ChatMessageInfo.StubType.E2E_ENCRYPTED,
            ChatMessageInfo.StubType.E2E_ENCRYPTED_NOW,
            ChatMessageInfo.StubType.DISAPPEARING_MODE
    );

    /**
     * Stub message types representing call-log entries, used by the
     * deletability heuristic.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "ee",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final Set<ChatMessageInfo.StubType> CALL_LOG_STUB_TYPES = EnumSet.of(
            ChatMessageInfo.StubType.CALL_MISSED_VOICE,
            ChatMessageInfo.StubType.CALL_MISSED_VIDEO,
            ChatMessageInfo.StubType.CALL_MISSED_GROUP_VOICE,
            ChatMessageInfo.StubType.CALL_MISSED_GROUP_VIDEO,
            ChatMessageInfo.StubType.SILENCED_UNKNOWN_CALLER_AUDIO,
            ChatMessageInfo.StubType.SILENCED_UNKNOWN_CALLER_VIDEO
    );

    /**
     * Client used to surface migration failures through the configurable
     * error handler.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Flat store that persists chats, contacts, and LID/PN mappings.
     */
    private final WhatsAppStore store;

    /**
     * Service used to read the AB props that gate the migration.
     */
    private final ABPropsService abPropsService;

    /**
     * WAM telemetry service used to commit migration lifecycle events.
     */
    private final WamService wamService;

    /**
     * Current position of the migration pipeline.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "getLidThreadMigrationStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final AtomicReference<LidMigrationState> state;

    /**
     * Maps each phone-number user part to the LID the primary device
     * assigned to that contact at migration time.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final ConcurrentHashMap<String, Jid> primaryPnToAssignedLidCache;

    /**
     * Maps each phone-number user part to the most recent LID known to
     * the primary device, which may be newer than the originally
     * assigned LID if the contact has rotated theirs.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final ConcurrentHashMap<String, Jid> primaryPnToLatestLidCache;

    /**
     * Caches the original LID seen on a chat at creation time, keyed by
     * the chat's phone-number user part.
     */
    private final ConcurrentHashMap<String, Jid> originalLidCache;

    /**
     * Last known chat-DB migration timestamp reported by the primary
     * device.
     */
    private volatile Instant chatDbMigrationTimestamp;

    /**
     * Wall-clock time at which the mapping sync protocol message
     * arrived, used as a fallback when the primary device did not report
     * a migration timestamp.
     */
    private volatile Instant receiveTimestamp;

    /**
     * Pending scheduled task that fails the migration when peer mappings
     * do not arrive within the AB-prop-defined window.
     */
    private volatile CompletableFuture<Void> mappingTimeoutFuture;

    /**
     * Constructs a new LID migration service bound to the given client,
     * AB props service, and WAM telemetry service.
     *
     * @param whatsapp       the client that owns this service
     * @param abPropsService the service used for reading feature flags
     * @param wamService     the telemetry service used for lifecycle events
     */
    public LidMigrationService(WhatsAppClient whatsapp, ABPropsService abPropsService, WamService wamService) {
        this.whatsapp = whatsapp;
        this.store = whatsapp.store();
        this.abPropsService = abPropsService;
        this.wamService = wamService;
        this.state = new AtomicReference<>(LidMigrationState.NOT_STARTED);
        this.primaryPnToAssignedLidCache = new ConcurrentHashMap<>();
        this.primaryPnToLatestLidCache = new ConcurrentHashMap<>();
        this.originalLidCache = new ConcurrentHashMap<>();
    }

    /**
     * Returns whether the 1:1 LID migration has completed for this
     * account.
     *
     * <p>Consumers use this flag to decide whether outgoing messages
     * should be addressed by LID or by phone number.
     *
     * @return {@code true} if the state machine has reached
     *         {@link LidMigrationState#COMPLETE}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1MigrationGating", exports = "Lid1X1MigrationUtils",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isLidMigrated() {
        return state.get() == LidMigrationState.COMPLETE;
    }

    /**
     * Returns whether the Syncd session has been migrated to LID.
     * @return {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1MigrationGating", exports = "Lid1X1MigrationUtils",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isSyncdSessionMigrated() {
        return false;
    }

    /**
     * Returns whether a new PN-addressed chat should still be created.
     * @return {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1MigrationGating", exports = "Lid1X1MigrationUtils",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean shouldCreatePnChat() {
        return false;
    }

    /**
     * Returns whether the runtime state disagrees with the persisted LID
     * migration flag.
     * @return {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1MigrationGating", exports = "Lid1X1MigrationUtils",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean hasStateDiscrepancy() {
        return false;
    }

    /**
     * Returns the current position of the state machine.
     *
     * @return the current state, never {@code null}
     */
    LidMigrationState state() {
        return state.get();
    }

    /**
     * Moves the state machine from {@link LidMigrationState#NOT_STARTED}
     * to {@link LidMigrationState#WAITING_PROP} so the service can react
     * to the AB prop flip.
     *
     * <p>Should be called once after the connection is established and
     * before any protocol traffic is processed.
     */
    public void initialize() {
        if (state.compareAndSet(LidMigrationState.NOT_STARTED, LidMigrationState.WAITING_PROP)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration initialized, waiting for AB prop");
        }
    }

    /**
     * Enables the migration once the AB prop reports it is active,
     * moving the state machine to
     * {@link LidMigrationState#WAITING_MAPPINGS} and arming a timeout
     * that fails the migration if the primary device never sends its
     * mapping sync.
     *
     * <p>The timeout duration is read from
     * {@link ABProp#LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS}.
     * A value of zero disables timeout scheduling, matching the early
     * return that WA Web's {@code shouldScheduleTimeoutForMissingPeerMessage}
     * exposes.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "checkIfMigrationEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationTimeout", exports = "scheduleLogoutIfNeeded",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void enableMigration() {
        if (state.compareAndSet(LidMigrationState.WAITING_PROP, LidMigrationState.WAITING_MAPPINGS)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration enabled, waiting for mappings from primary");

            var timeoutSeconds = abPropsService.getInt(ABProp.LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS);
            if (timeoutSeconds == 0) {
                LOGGER.log(System.Logger.Level.INFO, "LID migration peer sync timeout disabled by AB prop");
                return;
            }

            mappingTimeoutFuture = SchedulerUtils.scheduleDelayed(
                    Duration.ofSeconds(timeoutSeconds),
                    () -> {
                        if (state.get() == LidMigrationState.WAITING_MAPPINGS) {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "LID migration timed out after {0}s waiting for mappings", timeoutSeconds);
                            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                                    .stageFailureReason(StageFailureReasonEnum.COMPANION_TIMEOUT_BASED_ON_DEVICE_CAPABILITY)
                                    .isLocally1x1MigratedFromDb(isLidMigrated())
                                    .build());
                            handleError(new WhatsAppLidMigrationException.FailedToParseMappings(
                                    "Timed out waiting for peer migration mappings"));
                        }
                    });
        }
    }

    /**
     * Moves the state machine to {@link LidMigrationState#DISABLED} when
     * the server-sent AB prop indicates the migration is not enabled for
     * this account.
     */
    public void disableMigration() {
        if (state.compareAndSet(LidMigrationState.WAITING_PROP, LidMigrationState.DISABLED)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration disabled");
        }
    }

    /**
     * Processes a mapping sync protocol message received from the
     * primary device and advances the state machine to
     * {@link LidMigrationState#READY}.
     *
     * <p>The payload carries the authoritative table of PN to
     * assigned-LID pairs plus an optional chat-DB migration timestamp.
     * On arrival, this method populates the primary caches, cancels the
     * pending timeout, marks the service ready, and auto-starts the
     * migration.
     *
     * <p>A {@code null} payload is treated as a malformed peer message
     * and escalated through the client's error handler. An empty mapping
     * list is accepted (WA Web's parser returns the same shape with no
     * entries) and any per-chat failures surface later when the executor
     * runs.
     *
     * @param payload the decoded mapping payload from the primary device,
     *                or {@code null} if the message failed to parse
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "setLidMigrationMappings",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationMsgParser", exports = "parseLidMigrationMappingSyncMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void processProtocolMessage(LIDMigrationMappingSyncPayload payload) {
        if (payload == null) {
            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                    .stageFailureReason(StageFailureReasonEnum.MALFORMED_PEER_MESSAGE)
                    .build());
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("null payload"));
            return;
        }

        var currentState = state.get();
        if (currentState != LidMigrationState.WAITING_MAPPINGS && currentState != LidMigrationState.WAITING_PROP) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring mappings in state: {0}", currentState);
            return;
        }

        try {
            var timeout = mappingTimeoutFuture;
            if (timeout != null) {
                timeout.cancel(false);
                mappingTimeoutFuture = null;
            }

            // Recorded so it can serve as a fallback when no primary migration timestamp is reported
            this.receiveTimestamp = Instant.now();

            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_RECEIVED_PEER_MESSAGE)
                    .build());

            var mappings = payload.pnToLidMappings();

            if (mappings.isEmpty()) {
                this.chatDbMigrationTimestamp = null;
            } else {
                this.chatDbMigrationTimestamp = payload.chatDbMigrationTimestamp()
                        .orElse(null);
            }

            LOGGER.log(System.Logger.Level.INFO, "Processing {0} LID mappings from primary", mappings.size());

            for (var mapping : mappings) {
                processSingleMapping(mapping);
            }

            state.set(LidMigrationState.READY);
            LOGGER.log(System.Logger.Level.INFO, "LID migration ready with {0} assigned mappings, {1} latest mappings",
                    primaryPnToAssignedLidCache.size(), primaryPnToLatestLidCache.size());

            if (shouldAutoStartMigration()) {
                executeMigration();
            }

        } catch (Throwable throwable) {
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("error processing mappings", throwable));
        }
    }

    /**
     * Records the primary device's chat-DB migration timestamp, keeping
     * the newest value seen so far.
     *
     * <p>Used by the migration decision logic to tell whether the
     * primary device's mapping table is fresher than the local chats.
     *
     * @param timestamp the observed timestamp, or {@code null} to keep
     *                  the current value unchanged
     */
    public void observeChatDbMigrationTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return;
        }

        if (chatDbMigrationTimestamp == null || timestamp.isAfter(chatDbMigrationTimestamp)) {
            chatDbMigrationTimestamp = timestamp;
        }
    }

    /**
     * Learns LID mappings from a history sync payload.
     *
     * <p>Two sources are inspected. The top-level
     * {@code phoneNumberToLidMappings} field carries a flat table of
     * pairs, and each individual conversation may carry its own
     * {@code pnJid} and {@code lidJid} fields. Mappings found here feed
     * only the general store (contacts and the bidirectional LID/PN
     * table) and do not populate {@link #primaryPnToAssignedLidCache} or
     * {@link #primaryPnToLatestLidCache} because WA Web also restricts
     * those caches to the primary-device protocol message.
     *
     * <p>If the history sync's {@code GlobalSettings} contains a
     * {@code chatDbLidMigrationTimestamp}, the value is recorded via
     * {@link #observeChatDbMigrationTimestamp(Instant)} so the migration
     * decision logic has a fresher timestamp to work with.
     *
     * @param historySync the decoded HistorySync protobuf, may be
     *                    {@code null}
     */
    public void processHistorySync(HistorySync historySync) {
        if (historySync == null) {
            return;
        }

        var mappingsProcessed = 0;

        var topLevelMappings = historySync.phoneNumberToLidMappings();
        if (topLevelMappings != null && !topLevelMappings.isEmpty()) {
            for (var mapping : topLevelMappings) {
                if (processPhoneNumberToLidMapping(mapping)) {
                    mappingsProcessed++;
                }
            }
        }

        var conversations = historySync.chats();
        if (conversations != null) {
            for (var conversation : conversations) {
                if (processConversationLidData(conversation)) {
                    mappingsProcessed++;
                }
            }
        }

        var chatDbLidMigrationTimestamp = historySync.globalSettings()
                .flatMap(GlobalSettings::chatDbLidMigrationTimestamp);
        if (chatDbLidMigrationTimestamp.isPresent()) {
            if (chatDbMigrationTimestamp == null || chatDbLidMigrationTimestamp.get().isAfter(chatDbMigrationTimestamp)) {
                this.chatDbMigrationTimestamp = chatDbLidMigrationTimestamp.get();
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Updated chatDbMigrationTimestamp from GlobalSettings: {0}", chatDbLidMigrationTimestamp.get());
            }
        }

        if (mappingsProcessed > 0) {
            LOGGER.log(System.Logger.Level.INFO,
                    "Processed {0} LID mappings from history sync (type={1})",
                    mappingsProcessed, historySync.syncType());
        }
    }

    /**
     * Registers one {@link PhoneNumberToLIDMapping} entry coming from
     * the history sync payload.
     *
     * <p>The mapping is stored bidirectionally and mirrored onto any
     * existing contact. The primary-device caches are intentionally not
     * touched because WA Web reserves them for the mapping sync
     * protocol message.
     *
     * @param mapping the mapping entry to process, may be {@code null}
     * @return {@code true} if a valid mapping was registered
     */
    private boolean processPhoneNumberToLidMapping(PhoneNumberToLIDMapping mapping) {
        if (mapping == null) {
            return false;
        }

        var pnJid = mapping.pnJid().orElse(null);
        var lidJid = mapping.lidJid().orElse(null);

        if (pnJid == null || lidJid == null) {
            return false;
        }

        store.registerLidMapping(pnJid, lidJid);
        store.findContactByJid(pnJid).ifPresent(contact -> contact.setLid(lidJid));

        return true;
    }

    /**
     * Extracts the LID/PN pair from a conversation entry carried by a
     * history sync payload and mirrors the pairing onto the chat, the
     * store, and any known contact.
     *
     * <p>For LID-keyed chats the phone number is taken from the
     * {@code pnJid} protobuf field. For PN-keyed chats the LID is taken
     * from the {@code lidJid} field. Only 1:1 chats are considered.
     *
     * @param conversation the conversation to process, may be
     *                     {@code null}
     * @return {@code true} if a valid mapping was extracted
     */
    boolean processConversationLidData(Chat conversation) {
        if (conversation == null) {
            return false;
        }

        var chatJid = conversation.jid();

        if (!chatJid.hasUserServer() && !chatJid.hasLidServer()) {
            return false;
        }

        final Jid phoneJid;
        final Jid lidJid;

        if (chatJid.hasLidServer()) {
            phoneJid = conversation.phoneNumberJid().orElse(null);
            lidJid = chatJid;
        } else if (chatJid.hasUserServer()) {
            phoneJid = chatJid;
            lidJid = conversation.lid().orElse(null);
        } else {
            phoneJid = null;
            lidJid = null;
        }

        if (phoneJid == null || lidJid == null) {
            return false;
        }

        store.registerLidMapping(phoneJid, lidJid);
        store.findContactByJid(phoneJid).ifPresent(contact -> contact.setLid(lidJid));
        conversation.setLid(lidJid);
        if (!phoneJid.equals(chatJid)) {
            conversation.setPhoneNumberJid(phoneJid);
        }

        return true;
    }

    /**
     * Records a single mapping entry from the primary device's protocol
     * message into the primary caches.
     * @param mapping the mapping entry to process
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void processSingleMapping(LIDMigrationMapping mapping) {
        if (mapping == null) {
            return;
        }

        var jid = mapping.pn();
        var user = jid.user();

        var assignedLid = mapping.assignedLid();
        primaryPnToAssignedLidCache.put(user, assignedLid);

        mapping.latestLid().ifPresent(latest ->
                primaryPnToLatestLidCache.put(user, latest)
        );

        store.findContactByJid(jid).ifPresent(contact -> {
            contact.setLid(assignedLid);
            store.registerLidMapping(jid, assignedLid);
        });
    }

    /**
     * Runs the LID migration over every chat in the store.
     *
     * <p>The method blocks until the Signal offline-delivery window has
     * finished, snapshots all chats, classifies each one through
     * {@link #resolveThread(Chat, Set)}, executes the resulting
     * {@link LidMigrationResolution} actions, marks the state machine as
     * {@link LidMigrationState#COMPLETE}, and finally flushes the
     * primary caches into the store's bidirectional mapping tables.
     *
     * <p>If the compatibility AB prop
     * {@link ABProp#LID_ONE_ON_ONE_MIGRATION_COMPATIBLE} is off the
     * migration is aborted via the configurable error handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "migrate1x1Chats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void executeMigration() {
        if (!state.compareAndSet(LidMigrationState.READY, LidMigrationState.IN_PROGRESS)) {
            var currentState = state.get();
            LOGGER.log(System.Logger.Level.WARNING, "Cannot start migration in state: {0}", currentState);
            return;
        }

        wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_STARTED)
                .mappingCount(primaryPnToAssignedLidCache.size())
                .build());

        if (!abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_COMPATIBLE)) {
            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                    .stageFailureReason(StageFailureReasonEnum.COMPANION_UNSUPPORTED_VERSION)
                    .build());
            handleError(new WhatsAppLidMigrationException.IncompatibleClient());
            return;
        }

        LOGGER.log(System.Logger.Level.INFO, "Starting LID migration execution, waiting for offline delivery");
        store.waitForOfflineDeliveryEnd();
        LOGGER.log(System.Logger.Level.INFO, "Offline delivery complete, proceeding with migration");

        try {
            var resolutions = new ArrayList<LidMigrationResolution>();
            var chatsToProcess = new ArrayList<>(store.chats());

            // Pre-computed once so resolveThread can detect split-thread collisions without a per-chat scan
            var existingLidThreads = new HashSet<Jid>();
            for (var chat : chatsToProcess) {
                if (chat.jid().hasLidServer()) {
                    existingLidThreads.add(chat.jid().toUserJid());
                }
            }

            var companionHasADifferentMappingCount = 0;
            var chatNotInMappingCount = 0;
            var migratedThreadCount = 0;

            for (var chat : chatsToProcess) {
                var resolution = resolveThread(chat, existingLidThreads);
                resolutions.add(resolution);

                var chatJid = chat.jid();
                if (chatJid.hasUserServer()) {
                    var user = chatJid.user();
                    var latestLocalLid = user != null ? store.findLidByPhone(chatJid).orElse(null) : null;
                    if (latestLocalLid == null) {
                        chatNotInMappingCount++;
                    }
                    var primaryLid = user != null ? primaryPnToAssignedLidCache.get(user) : null;
                    var latestLocalLidUser = latestLocalLid != null ? latestLocalLid.toUserJid() : null;
                    var primaryLidUser = primaryLid != null ? primaryLid.toUserJid() : null;
                    if (!Objects.equals(latestLocalLidUser, primaryLidUser)) {
                        companionHasADifferentMappingCount++;
                    }
                }

                switch (resolution) {
                    case LidMigrationResolution.Migrate _ -> migratedThreadCount++;
                    case LidMigrationResolution.Keep keep -> {
                        if (keep.reason() == LidMigrationResolution.KeepReason.ALREADY_LID) {
                            migratedThreadCount++;
                        }
                    }
                    case LidMigrationResolution.Delete _ -> {}
                }
            }

            executeResolutions(resolutions);

            // WA Web sets COMPLETE inside the DB lock, before learnMappingsInBulk runs
            state.set(LidMigrationState.COMPLETE);
            LOGGER.log(System.Logger.Level.INFO, "LID migration completed");

            learnMappingsInBulk();

            var latestMappingCount = primaryPnToLatestLidCache.size();
            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_ENDED)
                    .mappingCount(primaryPnToAssignedLidCache.size())
                    .migratedThreadCount(migratedThreadCount)
                    .companionHasADifferentMappingCount(companionHasADifferentMappingCount)
                    .chatNotInMappingCount(chatNotInMappingCount)
                    .latestMappingCount(latestMappingCount)
                    .build());

        } catch (WhatsAppLidMigrationException e) {
            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                    .stageFailureReason(StageFailureReasonEnum.INITIATED_LOGOUT_BASED_ON_MAPPING)
                    .build());
            handleError(e);
        } catch (Throwable throwable) {
            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                    .stageFailureReason(StageFailureReasonEnum.INTERNAL_ERROR)
                    .build());
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("migration execution failed", throwable));
        }
    }

    /**
     * Classifies a single chat thread into a
     * {@link LidMigrationResolution}.
     *
     * <p>Walks a cascade of rules to decide whether the chat is already
     * on LID, whether its type does not participate in 1:1 migration,
     * whether the primary device has an assigned LID for the contact,
     * whether a locally known LID can be used as a fallback, and finally
     * whether the chat is safe to delete when no LID is known.
     * @param chat               the chat to resolve
     * @param existingLidThreads the user-level JIDs of existing LID
     *                           threads, used to detect split-thread
     *                           collisions
     * @return the resolution to apply to this chat
     * @throws WhatsAppLidMigrationException.PrimaryMappingsObsolete if
     *         the local chat is fresher than the primary mapping table
     *         and the mismatch AB prop is on
     * @throws WhatsAppLidMigrationException.NoLidAvailable if a
     *         non-deletable chat has no LID mapping anywhere
     * @throws WhatsAppLidMigrationException.SplitThreadMismatch if the
     *         local LID would collide with an existing LID thread
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "getResolvedThreadAccountLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    LidMigrationResolution resolveThread(Chat chat, Set<Jid> existingLidThreads) {
        var jid = chat.jid();

        // PNH_CTWA chats are promoted to GENERAL when their LID matches the primary's latest cache
        if (jid.hasLidServer()) {
            if (LID_ORIGIN_TYPE_PNH_CTWA.equals(chat.lidOriginType().orElse(null))) {
                var matchesPrimary = primaryPnToLatestLidCache.values().stream()
                        .anyMatch(latestLid -> latestLid.toUserJid().equals(jid.toUserJid()));
                if (matchesPrimary) {
                    chat.setLidOriginType(LID_ORIGIN_TYPE_GENERAL);
                }
            }
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.ALREADY_LID);
        }

        if (jid.hasGroupOrCommunityServer()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.GROUP_OR_COMMUNITY);
        }

        if (jid.hasNewsletterServer()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.NEWSLETTER);
        }

        if (jid.hasBroadcastServer()) {
            if (jid.isStatusBroadcastAccount()) {
                return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.STATUS_BROADCAST);
            }
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.BROADCAST);
        }

        if (jid.hasBotServer()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.BOT);
        }

        if (chat.phoneNumberhDuplicateLidThread()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.DUPLICATE_WILL_MERGE);
        }

        var chatLid = chat.lid().orElse(null);
        var user = jid.user();
        // Reads the assigned cache (not the merged view) to match WA Web's getLidForPn semantics
        var primaryLid = user != null
                ? primaryPnToAssignedLidCache.get(user)
                : null;
        var localLid = chatLid != null
                ? chatLid
                : (user != null ? store.findLidByPhone(jid).orElse(null) : null);

        if (primaryLid != null) {
            if (localLid == null || localLid.toUserJid().equals(primaryLid.toUserJid())) {
                return new LidMigrationResolution.Migrate(jid, primaryLid);
            }

            if (abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_LOG_OUT_ON_MISMATCH)) {
                // A non-strict comparison treats local data with a timestamp at or after the sync as fresher
                var chatTimestamp = chat.conversationTimestamp();
                var effectiveSyncTimestamp = getEffectiveSyncTimestamp();
                if (chatTimestamp.isPresent() && !chatTimestamp.get().isBefore(effectiveSyncTimestamp)) {
                    throw new WhatsAppLidMigrationException.PrimaryMappingsObsolete();
                }
            }

            return new LidMigrationResolution.Migrate(jid, primaryLid);
        }

        if (localLid != null) {
            if (existingLidThreads.contains(localLid.toUserJid())) {
                throw new WhatsAppLidMigrationException.SplitThreadMismatch();
            }
            return new LidMigrationResolution.Migrate(jid, localLid);
        }

        var cachedOriginalLid = user != null ? originalLidCache.get(user) : null;
        if (cachedOriginalLid != null) {
            return new LidMigrationResolution.Migrate(jid, cachedOriginalLid.toUserJid());
        }

        if (!canDeleteChat(chat)) {
            throw new WhatsAppLidMigrationException.NoLidAvailable();
        }

        return new LidMigrationResolution.Delete(jid, LidMigrationResolution.DeleteReason.NO_LID_MAPPING);
    }

    /**
     * Convenience overload that derives {@code existingLidThreads} from
     * the current chat store before delegating to
     * {@link #resolveThread(Chat, Set)}.
     *
     * @param chat the chat to classify
     * @return the resolution chosen for the chat
     */
    LidMigrationResolution resolveThread(Chat chat) {
        var existingLidThreads = new HashSet<Jid>();
        for (var stored : store.chats()) {
            if (stored.jid().hasLidServer()) {
                existingLidThreads.add(stored.jid().toUserJid());
            }
        }
        return resolveThread(chat, existingLidThreads);
    }

    /**
     * Returns the timestamp used for freshness comparisons against
     * local chats during migration.
     *
     * <p>Prefers the primary-device value when known, falls back to the
     * mapping-sync arrival time, and finally to {@link Instant#EPOCH}.
     *
     * @return the effective sync timestamp, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Instant getEffectiveSyncTimestamp() {
        if (chatDbMigrationTimestamp != null) {
            return chatDbMigrationTimestamp;
        }
        if (receiveTimestamp != null) {
            return receiveTimestamp;
        }
        return Instant.EPOCH;
    }

    /**
     * Decides whether a chat is safe to delete when the migration
     * cannot find a LID for it.
     *
     * <p>Replicates the rule cascade behind WA Web's deletability
     * predicate. A broadcast exemption applies when every message is a
     * safe stub or a broadcast and the pairing timestamp is at or
     * before the oldest message. Any chat carrying ephemeral, locked,
     * archived, or muted state is not deletable. Otherwise the chat is
     * deletable when every message is a safe stub, or when every
     * message is either a safe stub or a call-log entry (with at least
     * one call-log entry present), or when the broadcast exemption
     * already applied.
     * @param chat the chat to evaluate
     * @return {@code true} if the chat can be safely deleted
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "K",
            adaptation = WhatsAppAdaptation.ADAPTED)
    boolean canDeleteChat(Chat chat) {
        List<ChatMessageInfo> messages;
        try (var stream = chat.messages()) {
            messages = stream.toList();
        }

        var broadcastExempt = false;
        if (allMessagesAreSafeStubsOrBroadcast(messages)) {
            var oldestMessageTs = getOldestMessageTimestamp(messages);
            if (oldestMessageTs != null && isPairingTimestampAtOrBefore(oldestMessageTs)) {
                broadcastExempt = true;
            }
        }

        if (hasEphemeralSettings(chat) && !isEphemeralExempt(chat, messages)) {
            return false;
        }

        if (chat.locked()) {
            return false;
        }

        if (chat.archived()) {
            return false;
        }

        if (chat.mute().map(ChatMute::isMuted).orElse(false)) {
            return false;
        }

        return allMessagesAreSafeStubs(messages)
                || allMessagesAreSafeStubsOrCallLog(messages)
                || broadcastExempt;
    }

    /**
     * Returns whether the chat has any ephemeral (disappearing message)
     * settings configured.
     *
     * @param chat the chat to check
     * @return {@code true} if the chat has an ephemeral duration or an
     *         ephemeral setting timestamp
     */
    private boolean hasEphemeralSettings(Chat chat) {
        return chat.ephemeralExpiration().isPresent() || chat.ephemeralSettingTimestamp().isPresent();
    }

    /**
     * Returns whether the chat is exempt from the no-delete-if-ephemeral
     * rule.
     *
     * <p>Returns {@code true} when the chat's disappearing-mode trigger
     * is {@code ACCOUNT_SETTING} and at least one disappearing-mode
     * system message is present.
     *
     * @param chat     the chat to check
     * @param messages the chat's messages
     * @return {@code true} if the chat is exempt from the ephemeral
     *         block
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "re",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isEphemeralExempt(Chat chat, Collection<ChatMessageInfo> messages) {
        var trigger = chat.disappearingMode()
                .flatMap(ChatDisappearingMode::trigger)
                .orElse(null);
        if (trigger != ChatDisappearingMode.Trigger.ACCOUNT_SETTING) {
            return false;
        }

        return messages.stream().anyMatch(msg -> {
            var stubType = msg.messageStubType().orElse(null);
            return stubType == ChatMessageInfo.StubType.DISAPPEARING_MODE;
        });
    }

    /**
     * Returns whether every message is either a migration-safe stub or
     * a broadcast message, with at least one broadcast present.
     *
     * @param messages the messages to inspect
     * @return {@code true} if the all-stubs-or-broadcast rule applies
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "te",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean allMessagesAreSafeStubsOrBroadcast(Collection<ChatMessageInfo> messages) {
        var hasBroadcast = false;
        for (var msg : messages) {
            if (isMigrationSafeStub(msg)) {
                continue;
            }
            if (msg.broadcast()) {
                hasBroadcast = true;
                continue;
            }
            return false;
        }
        return hasBroadcast;
    }

    /**
     * Returns the oldest timestamp among the given messages.
     *
     * @param messages the messages to scan
     * @return the oldest timestamp, or {@code null} if the collection
     *         is empty or no message carries a timestamp
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "J",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Instant getOldestMessageTimestamp(Collection<ChatMessageInfo> messages) {
        Instant oldest = null;
        for (var msg : messages) {
            var ts = msg.timestamp().orElse(null);
            if (ts != null && (oldest == null || ts.isBefore(oldest))) {
                oldest = ts;
            }
        }
        return oldest;
    }

    /**
     * Returns whether the pairing timestamp is known and is at or
     * before the supplied message timestamp.
     *
     * @param messageTimestamp the message timestamp to compare against
     * @return {@code true} if the pairing timestamp is set and is not
     *         after the message timestamp
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "H",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isPairingTimestampAtOrBefore(Instant messageTimestamp) {
        var pairingTs = store.pairingTimestamp().orElse(null);
        if (pairingTs == null) {
            return false;
        }

        return !messageTimestamp.isBefore(pairingTs);
    }

    /**
     * Returns whether every message in the collection is a
     * migration-safe system stub.
     *
     * @param messages the messages to inspect
     * @return {@code true} if every message is a safe system stub
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "X",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean allMessagesAreSafeStubs(Collection<ChatMessageInfo> messages) {
        return messages.stream().allMatch(this::isMigrationSafeStub);
    }

    /**
     * Returns whether every message is either a migration-safe stub or
     * a call-log entry, with at least one call-log entry present.
     *
     * @param messages the messages to inspect
     * @return {@code true} if the stubs-or-call-log rule applies
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "ee",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean allMessagesAreSafeStubsOrCallLog(Collection<ChatMessageInfo> messages) {
        var hasCallLog = false;
        for (var msg : messages) {
            if (isMigrationSafeStub(msg)) {
                continue;
            }
            if (isCallLogMessage(msg)) {
                hasCallLog = true;
                continue;
            }
            return false;
        }
        return hasCallLog;
    }

    /**
     * Returns whether the given message is an initial E2E notification
     * or a disappearing-mode system message, either of which the
     * migration considers safe to ignore.
     *
     * @param msg the message to check
     * @return {@code true} if the message is a migration-safe stub
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "X",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isMigrationSafeStub(ChatMessageInfo msg) {
        if (!msg.message().isEmpty()) {
            return false;
        }

        var stubType = msg.messageStubType().orElse(null);
        return stubType != null && MIGRATION_SAFE_STUB_TYPES.contains(stubType);
    }

    /**
     * Returns whether the given message is a call-log entry.
     *
     * @param msg the message to check
     * @return {@code true} if the message is a call-log entry
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "ee",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isCallLogMessage(ChatMessageInfo msg) {
        var stubType = msg.messageStubType().orElse(null);
        return stubType != null && CALL_LOG_STUB_TYPES.contains(stubType);
    }


    /**
     * Applies the pre-computed resolutions to the store, swallowing per
     * resolution errors so a single failure does not abort the sweep.
     * @param resolutions the resolutions to execute, in order
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "migrate1x1Chats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void executeResolutions(List<LidMigrationResolution> resolutions) {
        for (var resolution : resolutions) {
            try {
                switch (resolution) {
                    case LidMigrationResolution.Migrate migrate -> executeMigrate(migrate);
                    case LidMigrationResolution.Delete delete -> executeDelete(delete);
                    case LidMigrationResolution.Keep _ -> {}
                }
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.ERROR, "Error executing resolution for {0}: {1}",
                        resolution.originalJid(), throwable.getMessage());
            }
        }
    }

    /**
     * Re-keys a chat to use LID addressing and mirrors the new mapping
     * onto the store and contact records.
     *
     * @param migrate the migration action to apply
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "migrate1x1Chats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void executeMigrate(LidMigrationResolution.Migrate migrate) {
        var originalJid = migrate.originalJid();
        var targetLid = migrate.targetLid();

        var chat = store.findChatByJid(originalJid).orElse(null);
        if (chat == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Chat not found for migration: {0}", originalJid);
            return;
        }

        chat.setLid(targetLid);
        chat.setPhoneNumberJid(originalJid);

        store.registerLidMapping(originalJid, targetLid);
        store.findContactByJid(originalJid).ifPresent(contact -> contact.setLid(targetLid));

        LOGGER.log(System.Logger.Level.DEBUG, "Migrated chat {0} -> {1}", originalJid, targetLid);
    }

    /**
     * Removes a chat that was classified as safe to delete.
     *
     * @param delete the delete action to apply
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "migrate1x1Chats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void executeDelete(LidMigrationResolution.Delete delete) {
        var originalJid = delete.originalJid();

        var removed = store.removeChat(originalJid);
        if (removed.isPresent()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Deleted chat {0}: {1}", originalJid, delete.reason());
        }
    }

    /**
     * Applies a LID-change notification received for an existing
     * contact, updating the primary caches, the bidirectional store
     * mapping, the contact, and any chat keyed by the phone number.
     *
     * @param phoneJid the phone-number JID whose LID is changing
     * @param newLid   the LID it is changing to
     * @param oldLid   the previous LID, may be {@code null}
     */
    public void changeLid(Jid phoneJid, Jid newLid, Jid oldLid) {
        if (phoneJid == null || newLid == null) {
            return;
        }

        if (phoneJid.user() != null) {
            primaryPnToAssignedLidCache.put(phoneJid.user(), newLid);
            primaryPnToLatestLidCache.put(phoneJid.user(), newLid);
        }

        store.registerLidMapping(phoneJid, newLid);
        store.findContactByJid(phoneJid).ifPresent(contact -> contact.setLid(newLid));
        store.findChatByJid(phoneJid).ifPresent(chat -> {
            chat.setLid(newLid);
            chat.setPhoneNumberJid(phoneJid);
        });

        LOGGER.log(System.Logger.Level.DEBUG, "LID changed for {0}: {1} -> {2}", phoneJid, oldLid, newLid);
    }

    /**
     * Remembers the LID that was known at chat-creation time so it can
     * be used as a fallback during migration when no better source is
     * available.
     *
     * <p>Called by the chat-creation path when the local client already
     * knows the LID for a phone-number chat before migration has begun.
     * @param phoneJid the phone-number JID of the chat
     * @param lid      the LID known at chat-creation time
     */
    public void registerOriginalLid(Jid phoneJid, Jid lid) {
        if (phoneJid == null || lid == null || phoneJid.user() == null) {
            return;
        }

        originalLidCache.put(phoneJid.user(), lid);
    }

    /**
     * Flushes the primary caches into the store's bidirectional LID/PN
     * mapping tables.
     *
     * <p>Follows WA Web's two-phase learning. Entries whose assigned
     * LID already matches the store's current LID for the phone number
     * are skipped. Entries whose latest LID matches the local LID are
     * treated as migration-sync-old and only their assigned LID is
     * registered. Entries whose latest LID differs are treated as
     * migration-sync-latest and both the assigned and latest LIDs are
     * registered.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void learnMappingsInBulk() {
        var oldMappings = new ArrayList<Map.Entry<Jid, Jid>>();
        var latestMappings = new ArrayList<Map.Entry<Jid, Jid>>();

        for (var entry : primaryPnToAssignedLidCache.entrySet()) {
            var phoneJid = Jid.of(entry.getKey());
            var assignedLid = entry.getValue();

            var currentLid = store.findLidByPhone(phoneJid).orElse(null);
            if (assignedLid.toUserJid().equals(currentLid != null ? currentLid.toUserJid() : null)) {
                continue;
            }

            var latestLid = primaryPnToLatestLidCache.get(entry.getKey());
            if (latestLid != null && latestLid.toUserJid().equals(currentLid != null ? currentLid.toUserJid() : null)) {
                oldMappings.add(Map.entry(phoneJid, assignedLid));
            } else {
                latestMappings.add(Map.entry(phoneJid, assignedLid));
                if (latestLid != null) {
                    latestMappings.add(Map.entry(phoneJid, latestLid));
                }
            }
        }

        // Old mappings first, latest second, because ordering matters for conflict resolution in the store
        for (var mapping : oldMappings) {
            store.registerLidMapping(mapping.getKey(), mapping.getValue());
        }
        for (var mapping : latestMappings) {
            store.registerLidMapping(mapping.getKey(), mapping.getValue());
        }

        LOGGER.log(System.Logger.Level.INFO,
                "Bulk-registered LID mappings: {0} old, {1} latest",
                oldMappings.size(), latestMappings.size());
    }

    /**
     * Returns whether the migration sweep should run immediately after
     * the primary-device mapping has been stored.
     * @return {@code true}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationManager", exports = "ThreadMigrationManager",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "shouldMigrateNow",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean shouldAutoStartMigration() {
        return true;
    }

    /**
     * Reports a migration error by flipping the state to
     * {@link LidMigrationState#FAILED} and delegating to the client's
     * configurable error handler.
     * @param error the migration exception to surface
     */
    private void handleError(WhatsAppLidMigrationException error) {
        state.set(LidMigrationState.FAILED);
        LOGGER.log(System.Logger.Level.ERROR, "LID migration failed: {0}", error.getMessage());
        whatsapp.handleFailure(error);
    }

    /**
     * Resets the service for a new session without discarding the
     * primary caches.
     *
     * <p>Cancels any pending mapping-sync timeout and returns the state
     * machine to {@link LidMigrationState#NOT_STARTED} unless it is
     * already in a terminal state. Terminal states are preserved so a
     * session bounce does not reopen a migration that has already
     * completed, failed, or been disabled.
     */
    public void reset() {
        var timeout = mappingTimeoutFuture;
        if (timeout != null) {
            timeout.cancel(false);
            mappingTimeoutFuture = null;
        }

        var currentState = state.get();
        if (!currentState.isTerminal()) {
            state.set(LidMigrationState.NOT_STARTED);
        }
    }

    /**
     * Looks up the LID associated with the given phone-number JID.
     *
     * <p>Checks the primary assigned cache first, then falls back to
     * the store's bidirectional mapping table so mappings learned
     * outside the primary-device flow are still found.
     * @param phoneJid the phone-number JID to look up, may be
     *                 {@code null}
     * @return the LID if one is known, otherwise an empty
     *         {@link Optional}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Jid> lookupLid(Jid phoneJid) {
        if (phoneJid == null || phoneJid.user() == null) {
            return Optional.empty();
        }

        var cached = primaryPnToAssignedLidCache.get(phoneJid.user());
        if (cached != null) {
            return Optional.of(cached);
        }

        return store.findLidByPhone(phoneJid);
    }

    /**
     * Returns whether outgoing messages to the given recipient should
     * use LID addressing.
     *
     * <p>LID addressing applies when the recipient already has a LID
     * JID, or when the recipient is a 1:1 user, the migration is
     * complete or in progress, and a LID mapping exists for them.
     *
     * @param recipientJid the recipient JID, may be {@code null}
     * @return {@code true} if LID addressing should be used
     */
    public boolean shouldUseLidAddressing(Jid recipientJid) {
        if (recipientJid == null) {
            return false;
        }

        if (recipientJid.hasLidServer()) {
            return true;
        }

        if (recipientJid.hasGroupOrCommunityServer() ||
            recipientJid.hasNewsletterServer() ||
            recipientJid.hasBroadcastServer()) {
            return false;
        }

        var currentState = state.get();
        if (currentState != LidMigrationState.COMPLETE && currentState != LidMigrationState.IN_PROGRESS) {
            return false;
        }

        return lookupLid(recipientJid).isPresent();
    }

    /**
     * Returns whether the given JID is eligible to carry an
     * {@code account_lid} attribute.
     *
     * <p>Eligibility requires that the 1:1 migration has completed and
     * the JID represents a regular user (not the PSA announcements
     * account, not a bot).
     *
     * @param jid the JID to evaluate, may be {@code null}
     * @return {@code true} if the JID should carry an account LID
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "shouldHaveAccountLid",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean shouldHaveAccountLid(Jid jid) {
        if (jid == null) {
            return false;
        }

        return isLidMigrated() && isRegularUser(jid);
    }

    /**
     * Returns whether the given JID represents a regular user, meaning
     * it lives on a user-like server, is not the PSA announcements
     * account, and is not a bot.
     *
     * @param jid the JID to check
     * @return {@code true} if the JID is a regular user
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "isRegularUser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean isRegularUser(Jid jid) {
        if (!jid.hasUserServer() && !jid.hasLidServer() && !jid.hasBotServer()
                && !jid.hasHostedServer() && !jid.hasHostedLidServer()) {
            return false;
        }

        if (jid.equals(Jid.announcementsAccount())) {
            return false;
        }

        if (jid.isBot()) {
            return false;
        }

        return true;
    }

    /**
     * Converts a JID to its phone-number representation.
     *
     * <p>If the JID is not a LID, returns it unchanged. Otherwise looks
     * up the corresponding phone number through the store and returns
     * {@code null} when no mapping is known.
     *
     * @param jid the JID to convert, may be {@code null}
     * @return the phone-number JID, the original JID if it was not a
     *         LID, or {@code null} if no mapping is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toPn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toPn(Jid jid) {
        if (jid == null) {
            return null;
        }

        if (!jid.hasLidServer()) {
            return jid;
        }

        return store.findPhoneByLid(jid).orElse(null);
    }

    /**
     * Converts a JID to its LID representation.
     *
     * <p>If the JID is already a LID, returns it unchanged. Otherwise
     * coerces the JID to a user JID (stripping device and agent data)
     * and looks up the corresponding LID through the store. Returns
     * {@code null} when no mapping is known.
     *
     * @param jid the JID to convert, may be {@code null}
     * @return the LID JID, the original JID if it was already a LID,
     *         or {@code null} if no mapping is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toLid",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toLid(Jid jid) {
        if (jid == null) {
            return null;
        }

        if (jid.hasLidServer()) {
            return jid;
        }

        var userJid = jid.toUserJid();
        return store.findLidByPhone(userJid).orElse(null);
    }

    /**
     * Converts a JID to its user-level LID representation.
     *
     * <p>Strips the device and agent data first. If the resulting user
     * JID is already a LID, returns it. Otherwise looks up the LID
     * through the store and returns {@code null} when no mapping is
     * known.
     *
     * @param jid the JID to convert, may be {@code null}
     * @return the user LID JID, or {@code null} if no mapping is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toUserLid",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toUserLid(Jid jid) {
        if (jid == null) {
            return null;
        }

        var userJid = jid.toUserJid();

        if (userJid.hasLidServer()) {
            return userJid;
        }

        return store.findLidByPhone(userJid).orElse(null);
    }

    /**
     * Returns the user-level LID for the given JID, throwing when none
     * can be resolved.
     *
     * @param jid the JID to convert
     * @return the user LID JID, never {@code null}
     * @throws IllegalStateException if no LID mapping exists for the
     *         JID
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toUserLidOrThrow",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toUserLidOrThrow(Jid jid) {
        var result = toUserLid(jid);
        if (result == null) {
            throw new IllegalStateException("No LID for user");
        }
        return result;
    }

    /**
     * Returns the phone-number JID for the given JID, throwing when
     * none can be resolved.
     *
     * @param jid the JID to convert
     * @return the phone-number JID, never {@code null}
     * @throws IllegalStateException if no phone-number mapping exists
     *         for the JID
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toPnOrThrow",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toPnOrThrow(Jid jid) {
        var result = toPn(jid);
        if (result == null) {
            throw new IllegalStateException("No PN for user");
        }
        return result;
    }

    /**
     * Returns the appropriate addressing-mode conversion function for
     * the requested mode.
     *
     * @param isLid {@code true} to return the LID conversion function,
     *              {@code false} to return the PN conversion function
     * @return either {@link #toLid(Jid)} or {@link #toPn(Jid)} as a
     *         method reference
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toAddressingModeFactory",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Function<Jid, Jid> toAddressingModeFactory(boolean isLid) {
        return isLid ? this::toLid : this::toPn;
    }

    /**
     * Normalises two JIDs to a common addressing mode by converting one
     * side so both sit on the same server family.
     *
     * <p>When both JIDs are user wids on different addressing modes,
     * the first side is converted to match the second; if no alternate
     * exists for the first side, the second side is converted instead.
     * If neither alternate is known the input pair is returned
     * unchanged.
     *
     * @param first  the first JID, may be {@code null}
     * @param second the second JID, may be {@code null}
     * @return a two-element array with the (possibly converted) JIDs
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toCommonAddressingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid[] toCommonAddressingMode(Jid first, Jid second) {
        if (first != null && second != null
                && isUserWid(first) && isUserWid(second)
                && first.hasLidServer() != second.hasLidServer()) {

            var alternateFirst = getAlternateUserWid(first.toUserJid());
            if (alternateFirst != null) {
                return new Jid[]{alternateFirst, second};
            }

            var alternateSecond = getAlternateUserWid(second.toUserJid());
            if (alternateSecond != null) {
                return new Jid[]{first, alternateSecond};
            }
        }
        return new Jid[]{first, second};
    }

    /**
     * Builds an alternate message key by swapping the participant or
     * remote JID into the opposite addressing mode.
     *
     * <p>For group, status, or broadcast messages the participant JID
     * is alternated; for 1:1 user messages the remote JID is
     * alternated. Returns {@code null} if no alternate JID can be
     * resolved.
     *
     * @param msgKey the message key to create an alternate for, may be
     *               {@code null}
     * @return the alternate message key, or {@code null} if no
     *         alternate is available
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "getAlternateMsgKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public MessageKey getAlternateMsgKey(MessageKey msgKey) {
        if (msgKey == null) {
            return null;
        }

        var remote = msgKey.parentJid().orElse(null);
        if (remote == null) {
            return null;
        }

        if (remote.hasGroupOrCommunityServer() || remote.hasBroadcastServer()) {
            return getAlternateMsgKeyForGroup(msgKey);
        }

        if (isUserWid(remote)) {
            return getAlternateMsgKeyForUser(msgKey);
        }

        return null;
    }

    /**
     * Builds the alternate message key for a group, status, or
     * broadcast message by swapping the participant JID into the
     * opposite addressing mode.
     *
     * @param msgKey the message key whose remote is a group, status,
     *               or broadcast JID
     * @return the alternate message key, or {@code null} if no
     *         alternate participant is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "S",
            adaptation = WhatsAppAdaptation.DIRECT)
    private MessageKey getAlternateMsgKeyForGroup(MessageKey msgKey) {
        var participant = getRawParticipant(msgKey);
        if (participant == null) {
            return null;
        }

        var alternateParticipant = getAlternateUserWid(participant.toUserJid());
        if (alternateParticipant == null) {
            return null;
        }

        var remote = msgKey.parentJid().orElse(null);
        var id = msgKey.id().orElse(null);
        return new MessageKeyBuilder()
                .fromMe(msgKey.fromMe())
                .parentJid(remote)
                .id(id)
                .senderJid(alternateParticipant)
                .build();
    }

    /**
     * Builds the alternate message key for a 1:1 message by swapping
     * the remote JID into the opposite addressing mode.
     *
     * @param msgKey the message key whose remote is a user JID
     * @return the alternate message key, or {@code null} if no
     *         alternate remote is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "R",
            adaptation = WhatsAppAdaptation.DIRECT)
    private MessageKey getAlternateMsgKeyForUser(MessageKey msgKey) {
        var remote = msgKey.parentJid().orElse(null);
        if (remote == null) {
            return null;
        }

        var alternateRemote = getAlternateUserWid(remote.toUserJid());
        if (alternateRemote == null) {
            return null;
        }

        var id = msgKey.id().orElse(null);
        var participant = getRawParticipant(msgKey);
        return new MessageKeyBuilder()
                .fromMe(msgKey.fromMe())
                .parentJid(alternateRemote)
                .id(id)
                .senderJid(participant)
                .build();
    }

    /**
     * Returns the raw participant JID of a message key, or {@code null}
     * when no participant is set.
     * @param msgKey the message key
     * @return the raw participant JID, or {@code null} if not set
     */
    private static Jid getRawParticipant(MessageKey msgKey) {
        var sender = msgKey.senderJid().orElse(null);
        var parent = msgKey.parentJid().orElse(null);
        if (sender != null && sender.equals(parent)) {
            return null;
        }
        return sender;
    }

    /**
     * Selects which addressing mode the current user should use when
     * composing a message key for a given chat.
     *
     * <p>Used by {@link #getMeUserLidOrJidForChat(Chat, TranslateMsgKeyType)}
     * to distinguish message addons (reactions, receipts) from regular
     * and edited messages, which follow slightly different LID and PN
     * rules for Community Announcement Groups.
     */
    @WhatsAppWebModule(moduleName = "WAWebMsgKeyUtils")
    public enum TranslateMsgKeyType {
        /**
         * Applied when the outgoing message is an addon such as a
         * reaction or a receipt.
         */
        @WhatsAppWebExport(moduleName = "WAWebMsgKeyUtils", exports = "TranslateMsgKeyType",
                adaptation = WhatsAppAdaptation.DIRECT)
        ADDON,

        /**
         * Applied when the outgoing message is a regular message.
         */
        @WhatsAppWebExport(moduleName = "WAWebMsgKeyUtils", exports = "TranslateMsgKeyType",
                adaptation = WhatsAppAdaptation.DIRECT)
        MESSAGE,

        /**
         * Applied when the outgoing message is an edit.
         */
        @WhatsAppWebExport(moduleName = "WAWebMsgKeyUtils", exports = "TranslateMsgKeyType",
                adaptation = WhatsAppAdaptation.DIRECT)
        EDIT_MESSAGE
    }

    /**
     * Returns the current user's identity, as either LID or PN,
     * appropriate for the given chat and translate type.
     *
     * <p>The selection depends on whether the chat itself is on the
     * LID server, whether it is a Community Announcement Group, whether
     * the chat's group metadata has LID addressing mode enabled, and
     * the translate type. For {@code ADDON} the LID is used whenever
     * the chat is LID, a CAG, or already on LID addressing mode. For
     * {@code MESSAGE} and {@code EDIT_MESSAGE} a CAG uses LID only when
     * the group is on LID addressing mode; non-CAG chats follow the
     * same rule as {@code ADDON}.
     *
     * @param chat          the chat for which to determine the user
     *                      identity
     * @param translateType the type of message key translation
     * @return the current user's JID in the appropriate addressing
     *         mode
     * @throws IllegalStateException if the store has no JID or LID
     *         configured for the current user
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "getMeUserLidOrJidForChat",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid getMeUserLidOrJidForChat(Chat chat, TranslateMsgKeyType translateType) {
        var chatJid = chat.jid();
        var isLid = chatJid.hasLidServer();

        var chatMetadata = store.findChatMetadata(chatJid).orElse(null);
        var isGroup = chatJid.hasGroupOrCommunityServer();
        var isCAG = isGroup && chatMetadata instanceof GroupMetadata gm
                && gm.isDefaultSubgroup();

        var isLidAddressingMode = isGroup
                && chatMetadata != null
                && chatMetadata.isLidAddressingMode();

        return switch (translateType) {
            case ADDON -> {
                if (isLid || isCAG || isLidAddressingMode) {
                    yield getMeLidUserOrThrow();
                } else {
                    yield getMePnUserOrThrow();
                }
            }
            case MESSAGE, EDIT_MESSAGE -> {
                if (isCAG) {
                    if (isLidAddressingMode) {
                        yield getMeLidUserOrThrow();
                    } else {
                        yield getMePnUserOrThrow();
                    }
                } else {
                    if (isLid || isLidAddressingMode) {
                        yield getMeLidUserOrThrow();
                    } else {
                        yield getMePnUserOrThrow();
                    }
                }
            }
        };
    }

    /**
     * Returns both addressing-mode JIDs for the given JID so callers
     * can update each side in turn.
     *
     * <p>When the input is a LID, the result is the LID followed by
     * its resolved PN. When the input is a PN, the result is the PN
     * followed by its resolved LID. If no alternate can be resolved a
     * single-element list with just the original JID is returned.
     *
     * @param jid the JID to find the PN/LID pair for, may be
     *            {@code null}
     * @return a list of one or two JIDs covering both addressing modes
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "getPnAndLidToUpdate",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<Jid> getPnAndLidToUpdate(Jid jid) {
        if (jid == null) {
            return List.of();
        }

        if (jid.hasLidServer()) {
            var pn = toPn(jid);
            if (pn != null) {
                return List.of(jid, pn);
            }
        } else {
            var lid = toLid(jid);
            if (lid != null) {
                return List.of(jid, lid);
            }
        }

        return List.of(jid);
    }

    /**
     * Returns whether a chat uses LID addressing mode.
     *
     * <p>A chat uses LID addressing if its JID is on the LID server,
     * or if it is a group whose metadata reports
     * {@code isLidAddressingMode} as {@code true}.
     *
     * @param chat the chat to check, may be {@code null}
     * @return {@code true} if the chat uses LID addressing mode
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "chatIsLid",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean chatIsLid(Chat chat) {
        if (chat == null) {
            return false;
        }

        var chatJid = chat.jid();

        if (chatJid.hasLidServer()) {
            return true;
        }

        if (chatJid.hasGroupOrCommunityServer()) {
            var chatMetadata = store.findChatMetadata(chatJid).orElse(null);
            return chatMetadata != null && chatMetadata.isLidAddressingMode();
        }

        return false;
    }

    /**
     * Returns the alternate user JID for the given JID.
     *
     * <p>For a LID input the corresponding phone-number JID is
     * returned, and for a phone-number input the corresponding LID is
     * returned. The "me" user takes a fast path that consults the
     * store's own JID and LID before any mapping lookup, so the
     * current user can be flipped without a store roundtrip.
     * @param userJid the user JID, already stripped of device and
     *                agent data
     * @return the alternate JID, or {@code null} if none is known
     */
    @WhatsAppWebExport(moduleName = "WAWebApiContact", exports = "getAlternateUserWid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebApiContact", exports = "getPhoneNumber",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebApiContact", exports = "getCurrentLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid getAlternateUserWid(Jid userJid) {
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
        } else {
            var mePn = store.jid().map(Jid::toUserJid).orElse(null);
            var meLid = store.lid().map(Jid::toUserJid).orElse(null);
            if (meLid != null && mePn != null && userJid.equals(mePn)) {
                return meLid;
            }
            return store.findLidByPhone(userJid).orElse(null);
        }
    }

    /**
     * Returns the current user's LID at user level.
     *
     * @return the current user's LID, never {@code null}
     * @throws IllegalStateException if no LID is configured for the
     *         current user
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser", exports = "getMeLidUserOrThrow",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid getMeLidUserOrThrow() {
        return store.lid()
                .map(Jid::toUserJid)
                .orElseThrow(() -> new IllegalStateException("No LID for current user"));
    }

    /**
     * Returns the current user's phone-number JID at user level.
     *
     * @return the current user's PN JID, never {@code null}
     * @throws IllegalStateException if no JID is configured for the
     *         current user
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser", exports = "getMePnUserOrThrow_DO_NOT_USE",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid getMePnUserOrThrow() {
        return store.jid()
                .map(Jid::toUserJid)
                .orElseThrow(() -> new IllegalStateException("No PN for current user"));
    }

    /**
     * Returns whether the given JID lives on one of the user-like
     * servers ({@code s.whatsapp.net}, {@code lid}, {@code bot},
     * {@code hosted}, {@code hosted.lid}).
     *
     * @param jid the JID to check
     * @return {@code true} if the JID is a user wid
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "isUser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isUserWid(Jid jid) {
        return jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer();
    }
}
