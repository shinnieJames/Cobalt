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
 * Orchestrates the 1:1 LID (Long ID) migration flow for a WhatsApp account.
 *
 * <p>WhatsApp introduced LID addressing to replace phone-number identifiers
 * in 1:1 conversations with privacy-preserving long identifiers. Migration
 * runs once per account: a paired client waits for an AB prop, receives a
 * mapping sync message from the primary device describing how every known
 * phone number maps to its assigned LID, rewrites every eligible local chat
 * to use the new address, deletes chats that can no longer be resolved, and
 * persists the mapping table so that outgoing and incoming stanzas can be
 * translated between addressing modes.
 *
 * <p>Beyond the migration itself, this service also exposes the utility
 * conversions ({@link #toLid(Jid)}, {@link #toPn(Jid)},
 * {@link #getAlternateMsgKey(MessageKey)}, etc.) that the rest of the
 * client uses to freely move between PN and LID representations for
 * messages, chats, and the current user's identity.
 *
 * <p>WA Web implements this same functionality across a handful of
 * single-purpose modules backed by UserPrefs keys
 * ({@code WAIsAccountLidFieldMigrated}, {@code WALidOneOnOneMigrationSource},
 * {@code WAIsPureLidSyncDSession}). Cobalt collapses them into a single
 * service with an explicit {@link LidMigrationState} state machine injected
 * via {@link WhatsAppClient}.
 *
 * @implNote WAWebLid1X1MigrationGating: exposes the {@code Lid1X1MigrationUtils}
 *           gating object. WAWebLid1X1ThreadAccountMigrations: drives the
 *           migration pass over chats. WAWebLid1x1MigrationPrimaryCache:
 *           holds the assigned/latest LID maps from the primary device.
 *           WAWebLid1x1MigrationManager: top-level trigger for
 *           {@code executeMigrationIfNeeded}. WAWebLidMigrationUtils:
 *           LID/PN conversion helpers.
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
     *
     * @implNote ADAPTED: WA Web uses {@code WALogger.LOG} tagged templates;
     *           Cobalt uses {@link System.Logger}.
     */
    private static final System.Logger LOGGER = System.getLogger(LidMigrationService.class.getName());

    /**
     * Marker string for click-to-WhatsApp chats whose LID was created with
     * phone-number hiding (PNH).
     *
     * @implNote WAWebUsernameTypes.LidOriginType.PNH_CTWA: module-level
     *           constant value {@code "ctwa"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsernameTypes", exports = "LidOriginType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String LID_ORIGIN_TYPE_PNH_CTWA = "ctwa";

    /**
     * Marker string for regular LID chats that have no PNH ancestry.
     *
     * @implNote WAWebUsernameTypes.LidOriginType.GENERAL: module-level
     *           constant value {@code "general"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsernameTypes", exports = "LidOriginType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final String LID_ORIGIN_TYPE_GENERAL = "general";

    /**
     * Stub message types that the LID migration considers "safe" and can
     * be ignored when deciding whether a chat may be deleted.
     *
     * <p>The WA Web {@code X} predicate matches exactly two conditions:
     * <ul>
     *     <li>{@code getIsInitialE2ENotification}:
     *         {@code type === "e2e_notification" && subtype === "encrypt"},</li>
     *     <li>{@code getIsDisappearingModeSystemMessage}:
     *         {@code type === "notification_template" && subtype === "disappearing_mode"}.</li>
     * </ul>
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.X
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "X",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final Set<ChatMessageInfo.StubType> MIGRATION_SAFE_STUB_TYPES = EnumSet.of(
            // Maps to getIsInitialE2ENotification (e2e_notification + encrypt)
            ChatMessageInfo.StubType.E2E_ENCRYPTED,
            ChatMessageInfo.StubType.E2E_ENCRYPTED_NOW,
            // Maps to getIsDisappearingModeSystemMessage (notification_template + disappearing_mode)
            ChatMessageInfo.StubType.DISAPPEARING_MODE
    );

    /**
     * Stub message types representing call log entries, used by the
     * deletability heuristic.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.ee: the
     *           {@code type === MSG_TYPE.CALL_LOG} check inside the
     *           {@code ee} predicate.
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
     * The WhatsApp client used to surface migration failures through the
     * configurable error handler.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The flat store that persists chats, contacts, and LID/PN mappings.
     *
     * @implNote ADAPTED: WA Web shards this data across UserPrefsIdb and
     *           several IndexedDB tables; Cobalt reads and writes through
     *           the single {@link WhatsAppStore} facade.
     */
    private final WhatsAppStore store;

    /**
     * Provides access to the server-sent AB props that gate the migration
     * ({@code ENABLE_INACTIVE_GROUP_LID_MIGRATION},
     * {@code LID_ONE_ON_ONE_MIGRATION_*}).
     */
    private final ABPropsService abPropsService;

    /**
     * Current position in the migration pipeline.
     *
     * <p>WA Web encodes the same information across several UserPrefs keys
     * ({@code WAIsAccountLidFieldMigrated}, {@code WAIsPureLidSyncDSession},
     * etc.); Cobalt consolidates them into a single {@link LidMigrationState}
     * state machine.
     *
     * @implNote ADAPTED: WAWebLid1X1MigrationGating.isLidMigrated maps to
     *           {@link LidMigrationState#COMPLETE}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "getLidThreadMigrationStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final AtomicReference<LidMigrationState> state;

    /**
     * Maps each phone-number user part to the LID the primary device
     * assigned to that contact at migration time.
     *
     * @implNote WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache:
     *           corresponds to the internal {@code $2} table populated from
     *           {@code LIDMigrationMappingSyncPayload}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final ConcurrentHashMap<String, Jid> primaryPnToAssignedLidCache;

    /**
     * Maps each phone-number user part to the most recent LID known to the
     * primary device, which may be newer than the originally assigned LID
     * if the contact has rotated their LID.
     *
     * @implNote WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache:
     *           corresponds to the internal {@code $3} table populated from
     *           {@code LIDMigrationMappingSyncPayload}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final ConcurrentHashMap<String, Jid> primaryPnToLatestLidCache;

    /**
     * Caches the original LID set on a chat at creation time, keyed by the
     * chat's phone-number user part.
     *
     * <p>Used as a last-resort LID source in {@link #resolveThread(Chat, Set)}
     * when neither the local chat nor the primary caches contain a mapping.
     *
     * @implNote WAWebCreateChat: corresponds to the {@code chat.originalLid}
     *           field that WA Web stores directly on the chat object. Cobalt
     *           does not have this per-chat field, so a service-scoped map
     *           holds the same information.
     */
    private final ConcurrentHashMap<String, Jid> originalLidCache;

    /**
     * Last known chat DB migration timestamp reported by the primary device.
     *
     * @implNote WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache:
     *           corresponds to the {@code $4} field populated from
     *           {@code primaryMigrationTsSec} of the parsed payload.
     */
    private volatile Instant chatDbMigrationTimestamp;

    /**
     * Time at which the mapping sync protocol message arrived, used as a
     * fallback when the primary device did not report a migration
     * timestamp.
     */
    private volatile Instant receiveTimestamp;

    /**
     * Pending scheduled task that logs the client out when peer mappings do
     * not arrive within the AB-prop-defined window.
     *
     * @implNote ADAPTED: WAWebLid1x1MigrationTimeout.scheduleLogoutIfNeeded:
     *           WA Web stores the raw {@code setTimeout} handle in the
     *           module-level variable {@code _}; Cobalt uses a
     *           {@link CompletableFuture} produced by
     *           {@link SchedulerUtils#scheduleDelayed(Duration, Runnable)}.
     */
    private volatile CompletableFuture<Void> mappingTimeoutFuture;

    /**
     * Creates a new LID migration service bound to the given client and AB
     * props service.
     *
     * @implNote ADAPTED: WA Web constructs {@code Lid1X1MigrationUtils}
     *           as a module-level singleton; Cobalt builds the service via
     *           constructor DI so tests can swap the collaborators.
     * @param whatsapp       the WhatsApp client that owns this service
     * @param abPropsService the AB props service used for reading feature flags
     */
    public LidMigrationService(WhatsAppClient whatsapp, ABPropsService abPropsService) {
        this.whatsapp = whatsapp;
        this.store = whatsapp.store();
        this.abPropsService = abPropsService;
        this.state = new AtomicReference<>(LidMigrationState.NOT_STARTED);
        this.primaryPnToAssignedLidCache = new ConcurrentHashMap<>();
        this.primaryPnToLatestLidCache = new ConcurrentHashMap<>();
        this.originalLidCache = new ConcurrentHashMap<>();
    }

    /**
     * Returns whether the 1:1 LID migration has completed for this account.
     *
     * <p>Consumers use this flag to decide whether outgoing messages
     * should be addressed by LID or by phone number.
     *
     * @implNote ADAPTED: WAWebLid1X1MigrationGating.Lid1X1MigrationUtils.isLidMigrated:
     *           WA Web reads {@code WAIsAccountLidFieldMigrated === true}
     *           from UserPrefsIdb. Cobalt reads the in-memory state machine
     *           and returns {@code true} only when it has reached
     *           {@link LidMigrationState#COMPLETE}.
     * @return {@code true} if the migration state is
     *         {@link LidMigrationState#COMPLETE}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1MigrationGating", exports = "Lid1X1MigrationUtils",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isLidMigrated() {
        return state.get() == LidMigrationState.COMPLETE;
    }

    /**
     * Returns whether the Syncd session has been migrated to LID.
     *
     * <p>Matches WA Web's stub that unconditionally returns {@code false};
     * the syncd-session migration is not implemented in either codebase.
     *
     * @implNote WAWebLid1X1MigrationGating.Lid1X1MigrationUtils.isSyncdSessionMigrated:
     *           unconditional return {@code false}.
     * @return {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1MigrationGating", exports = "Lid1X1MigrationUtils",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isSyncdSessionMigrated() {
        return false;
    }

    /**
     * Returns whether a new PN-addressed chat should still be created.
     *
     * <p>Matches WA Web's stub that unconditionally returns {@code false};
     * after the LID migration is enabled server-side, new chats are always
     * created with LID addressing.
     *
     * @implNote WAWebLid1X1MigrationGating.Lid1X1MigrationUtils.shouldCreatePnChat:
     *           unconditional return {@code false}.
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
     *
     * <p>In WA Web the check is {@code !isLidMigrated() && rawValue === true},
     * which is unreachable because both sides read the same UserPrefs key.
     * Cobalt preserves this observable behaviour by returning {@code false}.
     *
     * @implNote WAWebLid1X1MigrationGating.Lid1X1MigrationUtils.hasStateDiscrepancy:
     *           dead code in WA Web.
     * @return {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1MigrationGating", exports = "Lid1X1MigrationUtils",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean hasStateDiscrepancy() {
        return false;
    }

    /**
     * Moves the state machine from {@link LidMigrationState#NOT_STARTED} to
     * {@link LidMigrationState#WAITING_PROP} so the service is ready to
     * react to the AB prop.
     *
     * <p>Should be called once after the connection has been established
     * and before any protocol traffic is processed.
     *
     * @implNote Cobalt lifecycle hook with no WA Web counterpart: the JS
     *           code starts in the "waiting for prop" state implicitly.
     */
    public void initialize() {
        if (state.compareAndSet(LidMigrationState.NOT_STARTED, LidMigrationState.WAITING_PROP)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration initialized, waiting for AB prop");
        }
    }

    /**
     * Enables the migration once the AB prop reports it is active, moving
     * the state machine to {@link LidMigrationState#WAITING_MAPPINGS} and
     * arming a timeout that will log the client out if the primary device
     * never sends its mapping sync.
     *
     * <p>The timeout duration is read from
     * {@link ABProp#LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS};
     * a value of {@code 0} means "no timeout", matching WA Web's early
     * return in {@code shouldScheduleTimeoutForMissingPeerMessage}.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.checkIfMigrationEnabled:
     *           corresponds to the internal function {@code w}.
     *           ADAPTED: WAWebLid1x1MigrationTimeout.scheduleLogoutIfNeeded
     *           and WAWebLid1x1MigrationTimeoutUtils.shouldScheduleTimeoutForMissingPeerMessage.
     *           WA Web computes the timeout relative to the primary migration
     *           timestamp via {@code timeoutForAt(now, primaryMigrationTime, timeoutSeconds)};
     *           Cobalt schedules a flat timeout from the moment migration
     *           is enabled because the primary migration timestamp is not
     *           yet available when the AB prop flips. The callback checks
     *           {@code state == WAITING_MAPPINGS} instead of re-calling
     *           {@code shouldScheduleTimeoutForMissingPeerMessage}.
     *           The {@code hasStateDiscrepancy} branch and WAM telemetry
     *           are omitted.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "checkIfMigrationEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationTimeout", exports = "scheduleLogoutIfNeeded",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void enableMigration() {
        if (state.compareAndSet(LidMigrationState.WAITING_PROP, LidMigrationState.WAITING_MAPPINGS)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration enabled, waiting for mappings from primary");

            // WAWebLid1x1MigrationTimeoutUtils.shouldScheduleTimeoutForMissingPeerMessage
            // Reads the peer-sync timeout from AB props; a value of 0 disables the logout scheduling
            var timeoutSeconds = abPropsService.getInt(ABProp.LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS);
            if (timeoutSeconds == 0) {
                LOGGER.log(System.Logger.Level.INFO, "LID migration peer sync timeout disabled by AB prop");
                return;
            }

            // WAWebLid1x1MigrationTimeout.scheduleLogoutIfNeeded
            // Arms a delayed task that fails the migration if mappings have not arrived when it fires
            mappingTimeoutFuture = SchedulerUtils.scheduleDelayed(
                    Duration.ofSeconds(timeoutSeconds),
                    () -> {
                        if (state.get() == LidMigrationState.WAITING_MAPPINGS) {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "LID migration timed out after {0}s waiting for mappings", timeoutSeconds);
                            // WAWebLid1x1MigrationTimeout.h: new Lid11MigrationLifecycleWamEvent({
                            //   migrationStage: COMPANION_LOCAL_MIGRATION_FAILED,
                            //   stageFailureReason: COMPANION_TIMEOUT_BASED_ON_DEVICE_CAPABILITY,
                            //   isLocally1x1MigratedFromDb: Lid1X1MigrationUtils.isLidMigrated()
                            // }).commitAndWaitForFlush(true)
                            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
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
     *
     * @implNote Cobalt-specific transition with no direct WA Web
     *           counterpart. WA Web simply never leaves the implicit
     *           "waiting" state when the prop stays off.
     */
    public void disableMigration() {
        if (state.compareAndSet(LidMigrationState.WAITING_PROP, LidMigrationState.DISABLED)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration disabled");
        }
    }

    /**
     * Processes a mapping sync protocol message received from the primary
     * device and advances the state machine to
     * {@link LidMigrationState#READY}.
     *
     * <p>The payload carries the authoritative table of PN to assigned-LID
     * pairs plus an optional {@code chatDbMigrationTimestamp}. On arrival,
     * this method populates {@link #primaryPnToAssignedLidCache} and
     * {@link #primaryPnToLatestLidCache}, cancels the pending timeout,
     * marks the service ready, and auto-starts the migration.
     *
     * <p>A {@code null} payload is treated as a malformed peer message and
     * escalated through the client's error handler. An empty mapping list
     * is accepted: WA Web's parser returns
     * {@code {mappings: [], primaryMigrationTsSec: null}} in that case and
     * any per-chat failures surface later when the executor runs.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings:
     *           writes the primary caches and transitions to READY.
     *           WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache:
     *           the {@code forEach} loop that populates the inner tables.
     *           WAWebLid1x1MigrationMsgParser.parseLidMigrationMappingSyncMsg:
     *           the decoder whose failure mode maps to
     *           {@link WhatsAppLidMigrationException.FailedToParseMappings}.
     * @param payload the decoded mapping payload from the primary device,
     *                or {@code null} if the message failed to parse
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "setLidMigrationMappings",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationMsgParser", exports = "parseLidMigrationMappingSyncMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void processProtocolMessage(LIDMigrationMappingSyncPayload payload) {
        // WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings
        // A null payload means the peer message could not be decoded, which forces a logout in WA Web
        if (payload == null) {
            // WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings (e == null branch):
            //   new Lid11MigrationLifecycleWamEvent({
            //     migrationStage: COMPANION_LOCAL_MIGRATION_FAILED,
            //     stageFailureReason: MALFORMED_PEER_MESSAGE
            //   }).commitAndWaitForFlush(true)
            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                    .stageFailureReason(StageFailureReasonEnum.MALFORMED_PEER_MESSAGE)
                    .build());
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("null payload"));
            return;
        }

        // WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings
        // Ignores mappings that arrive outside the WAITING_PROP/WAITING_MAPPINGS window
        var currentState = state.get();
        if (currentState != LidMigrationState.WAITING_MAPPINGS && currentState != LidMigrationState.WAITING_PROP) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring mappings in state: {0}", currentState);
            return;
        }

        try {
            // WAWebLid1x1MigrationTimeout.scheduleLogoutIfNeeded
            // Cancels the peer-sync timeout now that mappings have arrived
            var timeout = mappingTimeoutFuture;
            if (timeout != null) {
                timeout.cancel(false);
                mappingTimeoutFuture = null;
            }

            // ADAPTED: WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings
            // Records the receive timestamp to serve as a fallback when no primary migration timestamp is reported
            this.receiveTimestamp = Instant.now();

            // WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings:
            //   new Lid11MigrationLifecycleWamEvent({migrationStage: COMPANION_RECEIVED_PEER_MESSAGE}).commit()
            // Emitted unconditionally when a peer-mapping sync arrives (even when the mapping list is empty).
            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_RECEIVED_PEER_MESSAGE)
                    .build());

            // WAWebLid1x1MigrationMsgParser.parseLidMigrationMappingSyncMsg
            // Extracts the mapping list; the parser tolerates an empty list and returns {mappings: [], primaryMigrationTsSec: null}
            var mappings = payload.pnToLidMappings();

            // WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache
            // Mirrors the primaryMigrationTsSec assignment: null for empty mappings, payload value otherwise
            if (mappings.isEmpty()) {
                this.chatDbMigrationTimestamp = null;
            } else {
                this.chatDbMigrationTimestamp = payload.chatDbMigrationTimestamp()
                        .orElse(null);
            }

            LOGGER.log(System.Logger.Level.INFO, "Processing {0} LID mappings from primary", mappings.size());

            // WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache
            // Walks every mapping entry and populates the assigned/latest LID caches plus known contacts
            for (var mapping : mappings) {
                processSingleMapping(mapping);
            }

            // WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings
            // Transitions the machine to READY once the caches have been loaded
            state.set(LidMigrationState.READY);
            LOGGER.log(System.Logger.Level.INFO, "LID migration ready with {0} assigned mappings, {1} latest mappings",
                    primaryPnToAssignedLidCache.size(), primaryPnToLatestLidCache.size());

            // WAWebLid1x1MigrationManager.executeMigrationIfNeeded
            // Triggers the chat sweep immediately so the UI observes a consistent addressing mode
            if (shouldAutoStartMigration()) {
                executeMigration();
            }

        } catch (Throwable throwable) {
            // WAWebLid1x1MigrationMsgParser.parseLidMigrationMappingSyncMsg
            // Any unexpected parse failure becomes a logout-worthy error in WA Web and a fatal exception in Cobalt
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("error processing mappings", throwable));
        }
    }

    /**
     * Records the primary device's chat-DB migration timestamp, keeping
     * the newest value seen so far.
     *
     * <p>Used by the migration decision logic to tell whether the primary
     * device's mapping table is fresher than the local chats.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the
     *           {@code chatDbMigrationTimestamp} bookkeeping scattered
     *           across the flow module.
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
     * <p>Two sources are inspected: the top-level
     * {@code phoneNumberToLidMappings} field and each individual
     * conversation's {@code pnJid}/{@code lidJid} fields. Mappings found
     * here feed only the general store (contact records and the bidirectional
     * LID/PN table); they do not populate {@link #primaryPnToAssignedLidCache}
     * or {@link #primaryPnToLatestLidCache} because WA Web also restricts
     * those caches to the primary-device protocol message.
     *
     * <p>If the history sync's {@code GlobalSettings} contains a
     * {@code chatDbLidMigrationTimestamp}, that value is recorded via
     * {@link #observeChatDbMigrationTimestamp(Instant)} so the migration
     * decision logic has a fresher timestamp to work with.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: equivalent to the
     *           history-sync paths that add bidirectional mappings to
     *           the store without feeding the primary cache.
     * @param historySync the decoded HistorySync protobuf, may be
     *                    {@code null}
     */
    public void processHistorySync(HistorySync historySync) {
        if (historySync == null) {
            return;
        }

        var mappingsProcessed = 0;

        // 1. Process top-level phoneNumberToLidMappings
        var topLevelMappings = historySync.phoneNumberToLidMappings();
        if (topLevelMappings != null && !topLevelMappings.isEmpty()) {
            for (var mapping : topLevelMappings) {
                if (processPhoneNumberToLidMapping(mapping)) {
                    mappingsProcessed++;
                }
            }
        }

        // 2. Process conversation-level LID fields
        var conversations = historySync.chats();
        if (conversations != null) {
            for (var conversation : conversations) {
                if (processConversationLidData(conversation)) {
                    mappingsProcessed++;
                }
            }
        }

        // 3. Extract chatDbMigrationTimestamp from GlobalSettings if present
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
     * Registers one {@link PhoneNumberToLIDMapping} entry coming from the
     * history sync payload.
     *
     * <p>The mapping is stored bidirectionally in the store and mirrored
     * onto any existing contact. The primary-device caches are intentionally
     * not touched: WA Web reserves them for the mapping sync protocol
     * message.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: history-sync branch of
     *           the mapping learner.
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

        // WAWebLid1X1ThreadAccountMigrations
        // Registers the bidirectional mapping in the store and mirrors it onto an existing contact when present
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
     * {@code pnJid} protobuf field; for PN-keyed chats the LID is taken
     * from the {@code lidJid} field. Only 1:1 chats are considered.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: history-sync branch
     *           that handles per-conversation LID fields.
     * @param conversation the conversation to process, may be {@code null}
     * @return {@code true} if a valid mapping was extracted
     */
    private boolean processConversationLidData(Chat conversation) {
        if (conversation == null) {
            return false;
        }

        var chatJid = conversation.jid();

        // WAWebLid1X1ThreadAccountMigrations
        // Restricts processing to 1:1 chats; groups and broadcast JIDs are not part of LID migration
        if (!chatJid.hasUserServer() && !chatJid.hasLidServer()) {
            return false;
        }

        final Jid phoneJid;
        final Jid lidJid;

        // WAWebLid1X1ThreadAccountMigrations
        // Picks the paired JID from either phoneNumberJid (LID chat) or lid (PN chat)
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

        // WAWebLid1X1ThreadAccountMigrations
        // Persists the mapping in the store and propagates it to the contact and chat objects
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
     *
     * <p>Stores the {@code assignedLid} in
     * {@link #primaryPnToAssignedLidCache} and the {@code latestLid} in
     * {@link #primaryPnToLatestLidCache} when present. WA Web defers contact
     * and store updates to {@code learnMappingsInBulk}; Cobalt eagerly
     * mirrors the pairing onto any existing contact as a proactive
     * optimisation.
     *
     * @implNote WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache:
     *           the forEach callback that writes the {@code $2} and
     *           {@code $3} caches.
     * @param mapping the mapping entry to process
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void processSingleMapping(LIDMigrationMapping mapping) {
        if (mapping == null) {
            return;
        }

        // WAWebLid1x1MigrationMsgParser.parseLidMigrationMappingSyncMsg
        // Normalises the PN entry to a user-level JID via asUserWidOrThrow
        var jid = mapping.pn();
        var user = jid.user();

        // WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache
        // Writes the assigned LID into the $2 table keyed by the PN user part
        var assignedLid = mapping.assignedLid();
        primaryPnToAssignedLidCache.put(user, assignedLid);

        // WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache
        // Optionally writes the latest LID into the $3 table when the mapping reports one
        mapping.latestLid().ifPresent(latest ->
                primaryPnToLatestLidCache.put(user, latest)
        );

        // ADAPTED: WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache
        // WA Web defers contact and store updates to learnMappingsInBulk; Cobalt updates eagerly for known contacts
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
     * {@link LidMigrationState#COMPLETE}, and finally flushes the primary
     * caches into the store's bidirectional mapping tables.
     *
     * <p>If the compatibility AB prop
     * ({@link ABProp#LID_ONE_ON_ONE_MIGRATION_COMPATIBLE}) is off, the
     * migration is aborted via the configurable error handler.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats:
     *           the executor for the migration flow.
     *           ADAPTED: WAWebLid1X1MigrationGating.setIsLidMigrated:
     *           the state transition to COMPLETE replaces the UserPrefs write.
     *           WAM emissions (Lid11MigrationLifecycleEvent) are wired
     *           at the start (COMPANION_LOCAL_MIGRATION_STARTED),
     *           the incompatibility short-circuit
     *           (COMPANION_LOCAL_MIGRATION_FAILED +
     *           COMPANION_UNSUPPORTED_VERSION), the success tail
     *           (COMPANION_LOCAL_MIGRATION_ENDED with mapping/thread
     *           counters), the logout-based failure from resolveThread
     *           (COMPANION_LOCAL_MIGRATION_FAILED +
     *           INITIATED_LOGOUT_BASED_ON_MAPPING), and the catch-all
     *           internal error (COMPANION_LOCAL_MIGRATION_FAILED +
     *           INTERNAL_ERROR).
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "migrate1x1Chats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void executeMigration() {
        if (!state.compareAndSet(LidMigrationState.READY, LidMigrationState.IN_PROGRESS)) {
            var currentState = state.get();
            LOGGER.log(System.Logger.Level.WARNING, "Cannot start migration in state: {0}", currentState);
            return;
        }

        // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats (function W, first statement):
        //   new Lid11MigrationLifecycleWamEvent({
        //     migrationStage: COMPANION_LOCAL_MIGRATION_STARTED,
        //     mappingCount: lidPnMigrationPrimaryCache.getAllPnLidMappings().length
        //   }).commit()
        // WA Web counts the flat list of PN->LID mappings; Cobalt mirrors this with the assigned-LID cache size.
        whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
                .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_STARTED)
                .mappingCount(primaryPnToAssignedLidCache.size())
                .build());

        // Check compatibility AB prop before proceeding
        if (!abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_COMPATIBLE)) {
            // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats (killswitch branch):
            //   new Lid11MigrationLifecycleWamEvent({
            //     migrationStage: COMPANION_LOCAL_MIGRATION_FAILED,
            //     stageFailureReason: COMPANION_UNSUPPORTED_VERSION
            //   }).commitAndWaitForFlush(true)
            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
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

            // Pre-compute set of existing LID thread JIDs for inline split thread detection
            var existingLidThreads = new HashSet<Jid>();
            for (var chat : chatsToProcess) {
                if (chat.jid().hasLidServer()) {
                    existingLidThreads.add(chat.jid().toUserJid());
                }
            }

            // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats: counters l, s, and migrated-thread tally
            // l: companionHasADifferentMappingCount - PN chats where primary LID differs from the locally known LID
            // s: chatNotInMappingCount             - PN chats whose user has no currentLid mapping yet
            // migratedThreadCount                  - length of i.push(...) entries (KEEP when already LID + MIGRATE)
            var companionHasADifferentMappingCount = 0;
            var chatNotInMappingCount = 0;
            var migratedThreadCount = 0;

            // Phase 1: Resolve all threads (split thread detection is inline)
            for (var chat : chatsToProcess) {
                var resolution = resolveThread(chat, existingLidThreads);
                resolutions.add(resolution);

                // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats: metric tallies inside the chat map
                //   var y = getCurrentLid(asUserWidOrThrow(n));         // latestLocalLid
                //   y == null && s++;                                    // chatNotInMappingCount
                //   r.equals(y, lidPnMigrationPrimaryCache.getLidForPn(n)) || l++; // companionHasADifferentMappingCount
                //   i.push({id, accountLid: S.threadLid, lidOriginType}) // migratedThreadCount source
                // Cobalt derives the same metrics from the chat model and the primary caches.
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

                // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats: i.push(...) happens for already-LID chats and migrated PN chats
                switch (resolution) {
                    case LidMigrationResolution.Migrate _ -> migratedThreadCount++;
                    case LidMigrationResolution.Keep keep -> {
                        if (keep.reason() == LidMigrationResolution.KeepReason.ALREADY_LID) {
                            migratedThreadCount++;
                        }
                    }
                    case LidMigrationResolution.Delete _ -> { /* not counted, matches WA Web's m.push path */ }
                }
            }

            // Phase 2: Execute migrations
            executeResolutions(resolutions);

            // Phase 3: Mark complete — WA Web sets COMPLETE and setIsLidMigrated(true)
            // inside the DB lock, BEFORE learnMappingsInBulk() runs
            // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats: V(COMPLETE), setIsLidMigrated(true)
            state.set(LidMigrationState.COMPLETE);
            LOGGER.log(System.Logger.Level.INFO, "LID migration completed");

            // Phase 4: Bulk-register all primary mappings in the store
            // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats: learnMappingsInBulk() after lock
            learnMappingsInBulk();

            // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats (success tail, after learnMappingsInBulk):
            //   var c = sumBy(getAllPnLidMappings(), e => e.primaryProvidedLatestLid != null ? 1 : 0);
            //   new Lid11MigrationLifecycleWamEvent({
            //     migrationStage: COMPANION_LOCAL_MIGRATION_ENDED,
            //     mappingCount: getAllPnLidMappings().length,
            //     migratedThreadCount: i.length,
            //     companionHasADifferentMappingCount: l,
            //     chatNotInMappingCount: s,
            //     latestMappingCount: c
            //   }).commit()
            var latestMappingCount = primaryPnToLatestLidCache.size();
            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_ENDED)
                    .mappingCount(primaryPnToAssignedLidCache.size())
                    .migratedThreadCount(migratedThreadCount)
                    .companionHasADifferentMappingCount(companionHasADifferentMappingCount)
                    .chatNotInMappingCount(chatNotInMappingCount)
                    .latestMappingCount(latestMappingCount)
                    .build());

        } catch (WhatsAppLidMigrationException e) {
            // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats (u != null branch):
            //   new Lid11MigrationLifecycleWamEvent({
            //     migrationStage: COMPANION_LOCAL_MIGRATION_FAILED,
            //     stageFailureReason: INITIATED_LOGOUT_BASED_ON_MAPPING
            //   }).commitAndWaitForFlush(true)
            // resolveThread throws (PrimaryMappingsObsolete / NoLidAvailable / SplitThreadMismatch) map to the
            // WA Web logoutReason returned from getResolvedThreadAccountLid that drives this failure emission.
            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                    .stageFailureReason(StageFailureReasonEnum.INITIATED_LOGOUT_BASED_ON_MAPPING)
                    .build());
            handleError(e);
        } catch (Throwable throwable) {
            // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats (outer catch):
            //   new Lid11MigrationLifecycleWamEvent({
            //     migrationStage: COMPANION_LOCAL_MIGRATION_FAILED,
            //     stageFailureReason: INTERNAL_ERROR
            //   }).commitAndWaitForFlush(true)
            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
                    .migrationStage(MigrationStageEnum.COMPANION_LOCAL_MIGRATION_FAILED)
                    .stageFailureReason(StageFailureReasonEnum.INTERNAL_ERROR)
                    .build());
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("migration execution failed", throwable));
        }
    }

    /**
     * Classifies a single chat thread into a {@link LidMigrationResolution}.
     *
     * <p>Walks a cascade of rules to decide whether the chat is already on
     * LID, whether it is a type that does not participate in 1:1 migration
     * (group, newsletter, broadcast, bot), whether the primary device has
     * an assigned LID for the contact, whether a locally-known LID can be
     * used as a fallback, and finally whether the chat can be deleted if
     * no LID is known.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.getResolvedThreadAccountLid:
     *           the core resolution function.
     *           WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats:
     *           the surrounding executor contains the split-thread collision
     *           check that is inlined here.
     * @param chat               the chat to resolve
     * @param existingLidThreads the set of JIDs of existing LID threads,
     *                           used for the inline split-thread collision
     *                           check
     * @return the resolution to apply to this chat
     * @throws WhatsAppLidMigrationException.PrimaryMappingsObsolete if the
     *         local chat is fresher than the primary mapping table and the
     *         mismatch AB prop is on
     * @throws WhatsAppLidMigrationException.NoLidAvailable if a non-deletable
     *         chat has no LID mapping anywhere
     * @throws WhatsAppLidMigrationException.SplitThreadMismatch if the local
     *         LID would collide with an existing LID thread
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "getResolvedThreadAccountLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private LidMigrationResolution resolveThread(Chat chat, Set<Jid> existingLidThreads) {
        var jid = chat.jid();

        // Rule 1: Already LID -> KEEP
        // Also handle LidOriginType promotion: PNH_CTWA -> GENERAL when primaryProvidedLatestLid matches
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

        // Rule 2: Groups and communities -> KEEP (not subject to 1:1 migration)
        if (jid.hasGroupOrCommunityServer()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.GROUP_OR_COMMUNITY);
        }

        // Rule 3: Newsletters -> KEEP
        if (jid.hasNewsletterServer()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.NEWSLETTER);
        }

        // Rule 4: Broadcast lists -> KEEP
        if (jid.hasBroadcastServer()) {
            if (jid.isStatusBroadcastAccount()) {
                return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.STATUS_BROADCAST);
            }
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.BROADCAST);
        }

        // Rule 5: Bot accounts -> KEEP
        if (jid.hasBotServer()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.BOT);
        }

        // Rule 6: Check for split thread flag
        // phoneDuplicateLidThread indicates this PN chat has a duplicate LID thread
        if (chat.phoneNumberhDuplicateLidThread()) {
            return new LidMigrationResolution.Keep(jid, LidMigrationResolution.KeepReason.DUPLICATE_WILL_MERGE);
        }

        // Rule 7: Determine local LID (from chat or store) and primary LID (from assigned cache)
        var chatLid = chat.lid().orElse(null);
        var user = jid.user();
        // Use assignedLid cache (not merged) - matches WA Web's getLidForPn()
        var primaryLid = user != null
                ? primaryPnToAssignedLidCache.get(user)
                : null;
        var localLid = chatLid != null
                ? chatLid
                : (user != null ? store.findLidByPhone(jid).orElse(null) : null);

        // Rule 8: Primary has a LID for this contact
        if (primaryLid != null) {
            // Rule 8a: No local LID or local matches primary -> use primary
            if (localLid == null || localLid.toUserJid().equals(primaryLid.toUserJid())) {
                return new LidMigrationResolution.Migrate(jid, primaryLid);
            }

            // Rule 8b: LID mismatch between local and primary
            // Gate mismatch check with AB prop
            if (abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_LOG_OUT_ON_MISMATCH)) {
                // Compare timestamps to determine which is fresher
                // Use >= (not >) - local timestamp >= sync timestamp means local is fresher
                var chatTimestamp = chat.conversationTimestamp();
                var effectiveSyncTimestamp = getEffectiveSyncTimestamp();
                if (chatTimestamp.isPresent() && !chatTimestamp.get().isBefore(effectiveSyncTimestamp)) {
                    // Local data is fresher than or equal to primary sync -> primary mappings are obsolete
                    throw new WhatsAppLidMigrationException.PrimaryMappingsObsolete();
                }
            }

            // Primary is fresher or mismatch logging out is disabled -> trust primary
            return new LidMigrationResolution.Migrate(jid, primaryLid);
        }

        // Rule 9: Primary has no LID, but local does -> use local
        // Inline split thread check: if the local LID already exists as a separate thread, abort
        // Matches WA Web's inline check: isThreadExistsWithChatJid ? logout(SplitThreadMismatch) : migrate
        if (localLid != null) {
            if (existingLidThreads.contains(localLid.toUserJid())) {
                throw new WhatsAppLidMigrationException.SplitThreadMismatch();
            }
            return new LidMigrationResolution.Migrate(jid, localLid);
        }

        // Rule 9b: originalLid fallback — check the cache of LIDs set at chat creation time
        // Matches WA Web's chat.originalLid check in getResolvedThreadAccountLid
        var cachedOriginalLid = user != null ? originalLidCache.get(user) : null;
        if (cachedOriginalLid != null) {
            return new LidMigrationResolution.Migrate(jid, cachedOriginalLid.toUserJid());
        }

        // Rule 10: No LID found - evaluate if chat can be deleted
        if (!canDeleteChat(chat)) {
            // Non-deletable chat with no LID -> abort migration
            throw new WhatsAppLidMigrationException.NoLidAvailable();
        }

        // Chat is eligible for deletion
        return new LidMigrationResolution.Delete(jid, LidMigrationResolution.DeleteReason.NO_LID_MAPPING);
    }

    /**
     * Returns the timestamp used for freshness comparisons against local
     * chats during migration.
     *
     * <p>Prefers {@link #chatDbMigrationTimestamp} set by the primary
     * device; falls back to {@link #receiveTimestamp} captured on
     * mapping-sync arrival, and finally to {@link Instant#EPOCH}.
     *
     * @implNote ADAPTED: WAWebLid1x1MigrationPrimaryCache.getPrimaryMigrationTsSec:
     *           WA Web returns the raw {@code $4} integer (Unix seconds);
     *           Cobalt returns an {@link Instant} and adds the
     *           {@link #receiveTimestamp} and epoch fallbacks.
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
     * Decides whether a chat is safe to delete when the migration cannot
     * find a LID for it.
     *
     * <p>The rule cascade follows WA Web exactly:
     * <ol>
     *     <li>Broadcast exemption: all messages are safe stubs or broadcast
     *         messages, and the pairing timestamp is at or before the
     *         oldest message timestamp (the {@code ne} ContactInfoCard
     *         check cannot be replicated because Cobalt's protobuf model
     *         does not expose the subtype field).</li>
     *     <li>The {@code createdLocally !== true} gate is skipped because
     *         Cobalt's {@link Chat} model does not track that field; the
     *         message-content check at the end keeps the heuristic safe.</li>
     *     <li>Chats with ephemeral settings are not deletable unless the
     *         disappearing-mode trigger is {@code ACCOUNT_SETTING} and at
     *         least one disappearing-mode system message exists.</li>
     *     <li>Locked, archived, or muted chats are not deletable.</li>
     *     <li>Otherwise the chat is deletable when every message is a safe
     *         stub, or when every message is a safe stub or a call-log
     *         entry with at least one call-log entry, or when the
     *         broadcast exemption applies.</li>
     * </ol>
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the {@code K}
     *           deletability predicate.
     * @param chat the chat to evaluate
     * @return {@code true} if the chat can be safely deleted
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "K",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean canDeleteChat(Chat chat) {
        var messages = chat.messages();

        // WAWebLid1X1ThreadAccountMigrations.K: var a = false;
        // (te(r) || ne(r)) && H(getPairingTimestamp(), J(r).oldestMessageTs) === "false" && (a = true)
        // H returns "false" when pairingTimestamp != null && !(oldestMessageTs < pairingTimestamp),
        // i.e., when pairingTimestamp <= oldestMessageTs
        var broadcastExempt = false; // WAWebLid1X1ThreadAccountMigrations.K
        if (allMessagesAreSafeStubsOrBroadcast(messages)) { // WAWebLid1X1ThreadAccountMigrations.te
            var oldestMessageTs = getOldestMessageTimestamp(messages);
            if (oldestMessageTs != null && isPairingTimestampAtOrBefore(oldestMessageTs)) { // WAWebLid1X1ThreadAccountMigrations.H
                broadcastExempt = true;
            }
        }
        // ADAPTED: ne() (contactInfoCard) check is omitted because Cobalt's protobuf-based
        // ChatMessageInfo model does not expose the message subtype field needed to identify
        // ContactInfoCard messages. WA Web's subtype is an internal DB field, not protobuf.
        // ADAPTED: WAWebLid1X1ThreadAccountMigrations.K: !a && e.createdLocally !== true
        // Cobalt's Chat model does not track the createdLocally field.
        // WA Web blocks deletion when !a AND !createdLocally.
        // Since we cannot determine createdLocally, we skip this check and fall through
        // to the remaining guards (ephemeral/locked/archived/muted/message content).
        // This is slightly more permissive than WA Web for chats that are both
        // non-broadcast and non-createdLocally, but the message content check
        // (allSafeStubs || allStubsOrCallLog || broadcastExempt) at the end
        // provides the primary safety net.
        // Ephemeral settings check — NOT deletable unless exempted
        // WAWebLid1X1ThreadAccountMigrations.K: (e.ephemeralDuration != null || e.ephemeralSettingTimestamp != null) && !re(e, r)
        if (hasEphemeralSettings(chat) && !isEphemeralExempt(chat, messages)) {
            return false; // WAWebLid1X1ThreadAccountMigrations.K: "ephemeral_duration"
        }

        // Locked chats are NOT deletable
        // WAWebLid1X1ThreadAccountMigrations.K: e.isLocked
        if (chat.locked()) {
            return false; // WAWebLid1X1ThreadAccountMigrations.K: "locked"
        }

        // Archived chats are NOT deletable
        // WAWebLid1X1ThreadAccountMigrations.K: e.archive
        if (chat.archived()) {
            return false; // WAWebLid1X1ThreadAccountMigrations.K: "archived"
        }

        // Muted chats are NOT deletable
        // WAWebLid1X1ThreadAccountMigrations.K: e.muteExpiration
        if (chat.mute().map(ChatMute::isMuted).orElse(false)) {
            return false; // WAWebLid1X1ThreadAccountMigrations.K: "mute_expiration"
        }

        // WAWebLid1X1ThreadAccountMigrations.K: r.every(X) || ee(r) || a
        // Message content check: deletable if all messages are safe stubs,
        // or all messages are safe stubs + call log entries with at least one call log,
        // or pre-exempted via broadcast check
        return allMessagesAreSafeStubs(messages)
                || allMessagesAreSafeStubsOrCallLog(messages)
                || broadcastExempt; // WAWebLid1X1ThreadAccountMigrations.K
    }

    /**
     * Returns whether the chat has ephemeral (disappearing message)
     * settings configured.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the
     *           {@code e.ephemeralDuration != null || e.ephemeralSettingTimestamp != null}
     *           predicate inside {@code K}.
     * @param chat the chat to check
     * @return {@code true} if the chat has an ephemeral duration or an
     *         ephemeral setting timestamp
     */
    private boolean hasEphemeralSettings(Chat chat) {
        return chat.ephemeralExpiration().isPresent() || chat.ephemeralSettingTimestamp().isPresent();
    }

    /**
     * Returns whether the chat is exempt from the "no-delete-if-ephemeral"
     * rule.
     *
     * <p>Returns {@code true} when the chat's disappearing-mode trigger is
     * {@code ACCOUNT_SETTING} and at least one disappearing-mode system
     * message is present.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the {@code re}
     *           predicate used inside the {@code K} cascade.
     * @param chat     the chat to check
     * @param messages the chat's messages
     * @return {@code true} if the chat is exempt from the ephemeral block
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
     * Returns whether every message is either a migration-safe stub or a
     * broadcast message, with at least one broadcast present.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the {@code te}
     *           predicate used inside the {@code K} cascade.
     * @param messages the messages to inspect
     * @return {@code true} if the all-stubs-or-broadcast rule applies
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "te",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean allMessagesAreSafeStubsOrBroadcast(Collection<ChatMessageInfo> messages) {
        // WAWebLid1X1ThreadAccountMigrations.te
        // Tracks whether at least one broadcast message was observed because the predicate requires both conditions
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
     * @implNote WAWebLid1X1ThreadAccountMigrations: the {@code J}
     *           helper that computes {@code oldestMessageTs} as
     *           {@code Math.min(...r.map(e => e.t))}.
     * @param messages the messages to scan
     * @return the oldest timestamp, or {@code null} if the collection is empty
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
     * Returns whether the pairing timestamp is known and at or before the
     * supplied timestamp.
     *
     * <p>Matches the truthy branch of WA Web's {@code H} helper:
     * {@code pairingTimestamp != null && !(messageTimestamp < pairingTimestamp)}.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the {@code H}
     *           helper used to compare timestamps.
     * @param messageTimestamp the message timestamp to compare against
     * @return {@code true} if pairing timestamp is non-null and at or
     *         before the message timestamp
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "H",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isPairingTimestampAtOrBefore(Instant messageTimestamp) {
        // WAWebUserPrefsMultiDevice.getPairingTimestamp
        // Reads the pairing timestamp stored when the client linked to the primary device
        var pairingTs = store.pairingTimestamp().orElse(null);
        if (pairingTs == null) {
            return false;
        }

        // WAWebLid1X1ThreadAccountMigrations.H
        // Equivalent to H(...) === "false": pairingTimestamp != null and messageTimestamp >= pairingTimestamp
        return !messageTimestamp.isBefore(pairingTs);
    }

    /**
     * Returns whether every message in the collection is a migration-safe
     * system stub.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the
     *           {@code r.every(X)} expression used inside the {@code K}
     *           cascade.
     * @param messages the messages to inspect
     * @return {@code true} if every message is a safe system stub
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "X",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean allMessagesAreSafeStubs(Collection<ChatMessageInfo> messages) {
        return messages.stream().allMatch(this::isMigrationSafeStub);
    }

    /**
     * Returns whether every message is either a migration-safe stub or a
     * call-log entry, with at least one call-log entry present.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the {@code ee}
     *           predicate used inside the {@code K} cascade.
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
     * Returns whether the given message is an initial E2E notification or
     * a disappearing-mode system message, either of which the migration
     * considers safe to ignore.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the {@code X}
     *           predicate.
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
     * @implNote WAWebLid1X1ThreadAccountMigrations: the call-log branch
     *           of the {@code ee} predicate.
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
     * Applies the pre-computed resolutions to the store.
     *
     * @implNote ADAPTED: WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats:
     *           WA Web batches updates into arrays and issues a single
     *           {@code bulkCreateOrMerge}/{@code bulkRemove}; Cobalt
     *           executes each resolution individually for simplicity.
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
     * Re-keys a chat to use LID addressing and mirrors the new mapping onto
     * the store and contact records.
     *
     * @implNote ADAPTED: WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats:
     *           this corresponds to WA Web's
     *           {@code bulkCreateOrMerge(i)} where every entry has
     *           {@code {id, accountLid, lidOriginType}}.
     * @param migrate the migration action to apply
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "migrate1x1Chats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void executeMigrate(LidMigrationResolution.Migrate migrate) {
        var originalJid = migrate.originalJid();
        var targetLid = migrate.targetLid();

        // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats
        // Looks up the chat object; missing chats simply skip this resolution with a warning
        var chat = store.findChatByJid(originalJid).orElse(null);
        if (chat == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Chat not found for migration: {0}", originalJid);
            return;
        }

        // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats
        // Rewrites the chat key to the target LID while preserving the original PN as metadata
        chat.setLid(targetLid);
        chat.setPhoneNumberJid(originalJid);

        // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats
        // Registers the mapping bidirectionally and mirrors it onto the associated contact
        store.registerLidMapping(originalJid, targetLid);
        store.findContactByJid(originalJid).ifPresent(contact -> contact.setLid(targetLid));

        LOGGER.log(System.Logger.Level.DEBUG, "Migrated chat {0} -> {1}", originalJid, targetLid);
    }

    /**
     * Removes a chat that was classified as safe to delete.
     *
     * @implNote ADAPTED: WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats:
     *           this corresponds to WA Web's
     *           {@code bulkRemove(m)} for chats and
     *           {@code bulkRemove(p)} for their messages.
     * @param delete the delete action to apply
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations", exports = "migrate1x1Chats",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void executeDelete(LidMigrationResolution.Delete delete) {
        var originalJid = delete.originalJid();

        // WAWebLid1X1ThreadAccountMigrations.migrate1x1Chats
        // Removes the chat from the store; downstream consumers receive the removal as a normal store event
        var removed = store.removeChat(originalJid);
        if (removed.isPresent()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Deleted chat {0}: {1}", originalJid, delete.reason());
        }
    }

    /**
     * Applies a LID-change notification received for an existing contact,
     * updating the primary caches, the bidirectional store mapping, the
     * contact, and any chat keyed by the phone number.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations: the change-notification
     *           branch that reacts to server-pushed LID rotations.
     * @param phoneJid the phone-number JID whose LID is changing
     * @param newLid   the LID it is changing to
     * @param oldLid   the previous LID, may be {@code null}
     */
    public void changeLid(Jid phoneJid, Jid newLid, Jid oldLid) {
        if (phoneJid == null || newLid == null) {
            return;
        }

        // WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache
        // Overwrites both the assigned and latest LID caches with the new value
        if (phoneJid.user() != null) {
            primaryPnToAssignedLidCache.put(phoneJid.user(), newLid);
            primaryPnToLatestLidCache.put(phoneJid.user(), newLid);
        }

        // WAWebLid1X1ThreadAccountMigrations
        // Propagates the change through the store, the contact record, and the chat if one exists
        store.registerLidMapping(phoneJid, newLid);
        store.findContactByJid(phoneJid).ifPresent(contact -> contact.setLid(newLid));
        store.findChatByJid(phoneJid).ifPresent(chat -> {
            chat.setLid(newLid);
            chat.setPhoneNumberJid(phoneJid);
        });

        LOGGER.log(System.Logger.Level.DEBUG, "LID changed for {0}: {1} -> {2}", phoneJid, oldLid, newLid);
    }

    /**
     * Remembers the LID that was known at chat-creation time so it can be
     * used as a fallback during migration when no better source is
     * available.
     *
     * <p>Called by the chat-creation path when a local client already knows
     * the LID for a phone-number chat before the migration has started.
     *
     * @implNote WAWebCreateChat: corresponds to the {@code chat.originalLid}
     *           field on the WA Web chat object.
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
     * <p>Follows WA Web's two-phase learning:
     * <ol>
     *     <li>Entries whose assigned LID already matches the store's
     *         current LID for the phone number are skipped.</li>
     *     <li>Entries whose latest LID matches the local LID are treated
     *         as "migration-sync-old" and only the assigned LID is
     *         registered.</li>
     *     <li>Entries whose latest LID differs are treated as
     *         "migration-sync-latest" and both the assigned and latest
     *         LIDs are registered.</li>
     * </ol>
     *
     * @implNote WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache:
     *           the {@code learnMappingsInBulk} method and the
     *           categorisation helper {@code $6}.
     *           WAWebDBCreateLidPnMappings.createLidPnMappings:
     *           the store-side registration call.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void learnMappingsInBulk() {
        var oldMappings = new ArrayList<Map.Entry<Jid, Jid>>();
        var latestMappings = new ArrayList<Map.Entry<Jid, Jid>>();

        for (var entry : primaryPnToAssignedLidCache.entrySet()) {
            var phoneJid = Jid.of(entry.getKey());
            var assignedLid = entry.getValue();

            // Skip if the assigned LID already matches the store's current mapping
            var currentLid = store.findLidByPhone(phoneJid).orElse(null);
            if (assignedLid.toUserJid().equals(currentLid != null ? currentLid.toUserJid() : null)) {
                continue;
            }

            // Check if latestLid matches the current local LID to determine categorization
            var latestLid = primaryPnToLatestLidCache.get(entry.getKey());
            if (latestLid != null && latestLid.toUserJid().equals(currentLid != null ? currentLid.toUserJid() : null)) {
                // Latest matches local -> "migration-sync-old": register assigned only
                oldMappings.add(Map.entry(phoneJid, assignedLid));
            } else {
                // Latest differs from local -> "migration-sync-latest": register assigned + latest
                latestMappings.add(Map.entry(phoneJid, assignedLid));
                if (latestLid != null) {
                    latestMappings.add(Map.entry(phoneJid, latestLid));
                }
            }
        }

        // Process old mappings first, then latest (ordering matters for conflict resolution)
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
     * Returns whether the migration sweep should run immediately after the
     * primary-device mapping has been stored.
     *
     * <p>WA Web's {@code executeMigrationIfNeeded} unconditionally calls
     * {@code shouldMigrateNow} (which accepts READY or IN_PROGRESS) and
     * then {@code migrate1x1Chats}. Because Cobalt only reaches this method
     * from {@link #processProtocolMessage(LIDMigrationMappingSyncPayload)}
     * with the state already on READY, the return value is always
     * {@code true}.
     *
     * <p>The {@code ThreadMigrationManager} dependent-task registry is
     * deliberately omitted: those tasks are WA Web-specific IndexedDB
     * migrations (favourites, labels, carts, blocklist, PNH threads)
     * that do not apply to Cobalt's flat store.
     *
     * @implNote ADAPTED: WAWebLid1x1MigrationManager.executeMigrationIfNeeded
     *           and WAWebLid1X1ThreadAccountMigrations.shouldMigrateNow.
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
     *
     * @implNote ADAPTED: WAWebLid1X1MigrationGating: WA Web performs
     *           inline recovery via {@code socketLogout}; Cobalt raises
     *           the exception through the pluggable error handler.
     * @param error the migration exception to surface
     */
    private void handleError(WhatsAppLidMigrationException error) {
        state.set(LidMigrationState.FAILED);
        LOGGER.log(System.Logger.Level.ERROR, "LID migration failed: {0}", error.getMessage());
        whatsapp.handleFailure(error);
    }

    /**
     * Resets the migration service for a new session without discarding
     * the primary caches.
     *
     * <p>Cancels any pending mapping-sync timeout and returns the state
     * machine to {@link LidMigrationState#NOT_STARTED} unless it is
     * already in a terminal state.
     *
     * @implNote ADAPTED: WAWebLid1x1MigrationPrimaryCache.clear:
     *           WA Web's {@code clear()} empties the {@code $1} flag and
     *           the two tables. Cobalt preserves the caches across
     *           reconnects because {@code clear()} in WA Web is only
     *           invoked from {@code updateCacheIfEmpty} (a re-population
     *           guard) and the debug utility
     *           {@code WAWebTestUtilRollbackLidThreadMigration}.
     */
    public void reset() {
        // WAWebLid1x1MigrationTimeout.scheduleLogoutIfNeeded
        // Cancels any armed logout timer so the reconnect path can re-arm it from a clean slate
        var timeout = mappingTimeoutFuture;
        if (timeout != null) {
            timeout.cancel(false);
            mappingTimeoutFuture = null;
        }

        // Cobalt state machine reset
        // Leaves terminal states (COMPLETE, FAILED, DISABLED) intact so a session bounce does not reopen the migration
        var currentState = state.get();
        if (!currentState.isTerminal()) {
            state.set(LidMigrationState.NOT_STARTED);
        }
    }

    /**
     * Looks up the LID associated with the given phone-number JID.
     *
     * <p>Checks the primary assigned cache first, then falls back to the
     * store's bidirectional mapping table so mappings learned outside the
     * primary-device flow are still found.
     *
     * @implNote ADAPTED: WAWebLid1x1MigrationPrimaryCache.getLidForPn:
     *           WA Web returns only the assigned-cache value; Cobalt
     *           additionally consults {@code store.findLidByPhone}.
     * @param phoneJid the phone-number JID to look up, may be {@code null}
     * @return the LID if one is known, otherwise an empty {@link Optional}
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1x1MigrationPrimaryCache", exports = "lidPnMigrationPrimaryCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Jid> lookupLid(Jid phoneJid) {
        if (phoneJid == null || phoneJid.user() == null) {
            return Optional.empty();
        }

        // WAWebLid1x1MigrationPrimaryCache.lidPnMigrationPrimaryCache
        // Checks the primary assigned-LID cache populated from the mapping sync message
        var cached = primaryPnToAssignedLidCache.get(phoneJid.user());
        if (cached != null) {
            return Optional.of(cached);
        }

        // Cobalt extension to WAWebLid1x1MigrationPrimaryCache.getLidForPn
        // Falls back to the persistent store mapping for LIDs learned through history sync and other channels
        return store.findLidByPhone(phoneJid);
    }

    /**
     * Returns whether outgoing messages to the given recipient should use
     * LID addressing.
     *
     * <p>LID addressing is used when the recipient already has a LID JID,
     * or when the recipient is a 1:1 user, the migration is complete or
     * in progress, and a LID mapping exists for them.
     *
     * @implNote ADAPTED: WAWebLid1X1MigrationGating.isLidMigrated:
     *           the check is expressed through the Cobalt state machine
     *           and the local mapping lookup.
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
     * <p>Eligibility requires that the 1:1 migration has completed and the
     * JID represents a regular user (not the PSA/announcements account,
     * not a bot).
     *
     * @implNote WAWebLidMigrationUtils.shouldHaveAccountLid:
     *           the check is {@code isLidMigrated() && wid.isRegularUser()}.
     * @param jid the JID to evaluate, may be {@code null}
     * @return {@code true} if the JID should carry an account LID
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "shouldHaveAccountLid",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean shouldHaveAccountLid(Jid jid) {
        if (jid == null) {
            return false;
        }

        // WAWebLidMigrationUtils.shouldHaveAccountLid
        // Combines the migration-complete flag with the regular-user predicate
        return isLidMigrated() && isRegularUser(jid);
    }

    /**
     * Returns whether the given JID represents a regular user (not a PSA,
     * not a bot, and on a user-like server).
     *
     * @implNote WAWebWid.isRegularUser: the JS predicate is
     *           {@code this.isUser() && !this.isPSA() && !this.isBot()}
     *           with {@code isUser()} matching {@code c.us}, {@code lid},
     *           {@code bot}, {@code hosted}, and {@code hosted.lid}.
     * @param jid the JID to check
     * @return {@code true} if the JID is a regular user
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "isRegularUser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean isRegularUser(Jid jid) { // WAWebWid.isRegularUser - promoted to public so stream handlers can reuse it instead of inlining the predicate
        // WAWebWid.isUser
        // Fails fast for any JID whose server is not one of the user-like servers
        if (!jid.hasUserServer() && !jid.hasLidServer() && !jid.hasBotServer()
                && !jid.hasHostedServer() && !jid.hasHostedLidServer()) {
            return false;
        }

        // WAWebWid.isPSA
        // Excludes the special announcements account 0@s.whatsapp.net
        if (jid.equals(Jid.announcementsAccount())) {
            return false;
        }

        // WAWebWid.isBot
        // Excludes PN bots and FBID bots
        if (jid.isBot()) {
            return false;
        }

        return true;
    }

    /**
     * Converts a JID to its phone number (PN) representation.
     *
     * <p>If the JID is not a LID, returns it unchanged. If the JID is a LID,
     * looks up the corresponding phone number from the store. Returns
     * {@code null} if no phone number mapping is found.
     *
     * <p>This matches WA Web's {@code toPn(wid)} function which returns the wid
     * unchanged if not a LID, or looks up the phone number via
     * {@code getPhoneNumber(wid)}.
     *
     * @implNote WAWebLidMigrationUtils.toPn: returns the JID unchanged when
     *           not a LID; otherwise looks up the PN via
     *           {@code getPhoneNumber(wid)}.
     * @param jid the JID to convert, may be {@code null}
     * @return the phone number JID, the original JID if not a LID,
     *         or {@code null} if no mapping is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toPn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toPn(Jid jid) {
        if (jid == null) {
            return null;
        }

        // WAWebLidMigrationUtils.toPn
        // Returns the JID unchanged when it is not a LID; there is nothing to translate
        if (!jid.hasLidServer()) {
            return jid;
        }

        // WAWebLidMigrationUtils.toPn
        // Delegates to the store's LID to phone-number lookup
        return store.findPhoneByLid(jid).orElse(null);
    }

    /**
     * Converts a JID to its LID representation.
     *
     * <p>If the JID is already a LID, returns it unchanged. Otherwise, coerces
     * the JID to a user JID (stripping device/agent) and looks up the
     * corresponding LID from the store. Returns {@code null} if no LID mapping
     * is found.
     *
     * <p>This matches WA Web's {@code toLid(wid)} function which returns the wid
     * unchanged if already a LID, or calls {@code getCurrentLid(asUserWidOrThrow(wid))}
     * to look up the LID.
     *
     * @implNote WAWebLidMigrationUtils.toLid: returns the JID unchanged
     *           when already a LID; otherwise strips the device/agent via
     *           {@code asUserWidOrThrow} and looks up the LID via
     *           {@code getCurrentLid}.
     * @param jid the JID to convert, may be {@code null}
     * @return the LID JID, the original JID if already a LID,
     *         or {@code null} if no mapping is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toLid",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toLid(Jid jid) {
        if (jid == null) {
            return null;
        }

        // WAWebLidMigrationUtils.toLid
        // Returns the JID unchanged when it is already on the LID server
        if (jid.hasLidServer()) {
            return jid;
        }

        // WAWebLidMigrationUtils.toLid
        // Normalises the JID via asUserWidOrThrow and looks up the LID via the store
        var userJid = jid.toUserJid();
        return store.findLidByPhone(userJid).orElse(null);
    }

    /**
     * Converts a JID to its user LID representation.
     *
     * <p>First coerces the JID to a user JID (stripping device/agent data).
     * If the resulting JID is already a LID, returns it. Otherwise, looks up the
     * corresponding LID from the store. Returns {@code null} if no mapping is found.
     *
     * <p>This matches WA Web's {@code toUserLid(wid)} function which calls
     * {@code asUserWidOrThrow(wid)} first, then returns the wid if it is a LID,
     * or calls {@code getCurrentLid(t)} otherwise.
     *
     * @implNote WAWebLidMigrationUtils.toUserLid: normalises the JID via
     *           {@code asUserWidOrThrow}, returns it when already on the
     *           LID server, otherwise looks up the LID.
     * @param jid the JID to convert, may be {@code null}
     * @return the user LID JID, or {@code null} if no mapping is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toUserLid",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toUserLid(Jid jid) {
        if (jid == null) {
            return null;
        }

        // WAWebWidFactory.asUserWidOrThrow
        // Strips device and agent information to obtain a user-level JID
        var userJid = jid.toUserJid();

        // WAWebLidMigrationUtils.toUserLid
        // Returns the user JID when it is already on the LID server; otherwise looks up the mapping
        if (userJid.hasLidServer()) {
            return userJid;
        }

        return store.findLidByPhone(userJid).orElse(null);
    }

    /**
     * Converts a JID to its user LID representation, throwing if no LID is found.
     *
     * <p>This is the throwing variant of {@link #toUserLid(Jid)}. Matches WA Web's
     * {@code toUserLidOrThrow(wid)} which calls {@code toUserLid(wid)} and throws
     * {@code Error("No LID for user")} if the result is {@code null}.
     *
     * @implNote WAWebLidMigrationUtils.toUserLidOrThrow: delegates to
     *           {@code toUserLid} and throws
     *           {@code Error("No LID for user")} when the result is null.
     * @param jid the JID to convert
     * @return the user LID JID, never {@code null}
     * @throws IllegalStateException if no LID mapping is found for the JID
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toUserLidOrThrow",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toUserLidOrThrow(Jid jid) {
        // WAWebLidMigrationUtils.toUserLidOrThrow
        // Wraps toUserLid with an error when no LID can be resolved for the given user
        var result = toUserLid(jid);
        if (result == null) {
            throw new IllegalStateException("No LID for user");
        }
        return result;
    }

    /**
     * Converts a JID to its phone number (PN) representation, throwing if not found.
     *
     * <p>This is the throwing variant of {@link #toPn(Jid)}. Matches WA Web's
     * {@code toPnOrThrow(wid)} which calls {@code toPn(wid)} and throws
     * {@code Error("No PN for user")} if the result is {@code null}.
     *
     * @implNote WAWebLidMigrationUtils.toPnOrThrow: delegates to
     *           {@code toPn} and throws
     *           {@code Error("No PN for user")} when the result is null.
     * @param jid the JID to convert
     * @return the phone number JID, never {@code null}
     * @throws IllegalStateException if no phone number mapping is found for the JID
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toPnOrThrow",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid toPnOrThrow(Jid jid) {
        // WAWebLidMigrationUtils.toPnOrThrow
        // Wraps toPn with an error when no phone number can be resolved
        var result = toPn(jid);
        if (result == null) {
            throw new IllegalStateException("No PN for user");
        }
        return result;
    }

    /**
     * Returns the appropriate addressing mode conversion function based on whether
     * LID addressing is active.
     *
     * <p>When {@code isLid} is {@code true}, returns the {@link #toLid(Jid)} function.
     * When {@code isLid} is {@code false}, returns the {@link #toPn(Jid)} function.
     *
     * <p>This matches WA Web's {@code toAddressingModeFactory(isLid)} which returns
     * the {@code toLid} function reference when {@code isLid === true}, or the
     * {@code toPn} function reference otherwise.
     *
     * @implNote WAWebLidMigrationUtils.toAddressingModeFactory: returns
     *           the {@code toLid} reference when the argument is
     *           {@code true} and {@code toPn} otherwise.
     * @param isLid {@code true} to return the LID conversion function,
     *              {@code false} to return the PN conversion function
     * @return the addressing mode conversion function
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toAddressingModeFactory",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Function<Jid, Jid> toAddressingModeFactory(boolean isLid) {
        // WAWebLidMigrationUtils.toAddressingModeFactory
        // Picks the method reference matching the requested addressing mode
        return isLid ? this::toLid : this::toPn;
    }

    /**
     * Normalizes two JIDs to a common addressing mode by converting one to match
     * the other's addressing mode (LID or PN).
     *
     * <p>If both JIDs are non-null users with different addressing modes (one is LID,
     * the other is PN), tries to find an alternate JID for either side so they match.
     * First tries to find the alternate for the first JID; if that succeeds, returns
     * the alternate paired with the second JID. Otherwise tries the second JID.
     *
     * <p>This matches WA Web's {@code toCommonAddressingMode(e, t)} function which
     * calls {@code getAlternateUserWid()} on each side in order to resolve addressing
     * mode mismatches.
     *
     * @implNote WAWebLidMigrationUtils.toCommonAddressingMode: calls
     *           {@code getAlternateUserWid} on each side to resolve the
     *           mismatch.
     * @param first  the first JID, or {@code null}
     * @param second the second JID, or {@code null}
     * @return a two-element array with the (possibly converted) JIDs
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "toCommonAddressingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid[] toCommonAddressingMode(Jid first, Jid second) {
        // WAWebLidMigrationUtils.toCommonAddressingMode
        // Only acts when both sides are user JIDs on different addressing modes
        if (first != null && second != null
                && isUserWid(first) && isUserWid(second)
                && first.hasLidServer() != second.hasLidServer()) {

            // WAWebLidMigrationUtils.toCommonAddressingMode
            // Tries to resolve the first JID to match the second; returns on success
            var alternateFirst = getAlternateUserWid(first.toUserJid());
            if (alternateFirst != null) {
                return new Jid[]{alternateFirst, second};
            }

            // WAWebLidMigrationUtils.toCommonAddressingMode
            // Falls back to resolving the second JID to match the first
            var alternateSecond = getAlternateUserWid(second.toUserJid());
            if (alternateSecond != null) {
                return new Jid[]{first, alternateSecond};
            }
        }
        return new Jid[]{first, second};
    }

    /**
     * Creates an alternate message key by looking up the alternate user JID for the
     * relevant participant or remote JID.
     *
     * <p>For group, status, or broadcast messages, the participant JID is alternated.
     * For 1:1 user messages, the remote JID is alternated. Returns {@code null} if
     * no alternate JID can be found.
     *
     * <p>This matches WA Web's {@code getAlternateMsgKey(msgKey)} function which
     * delegates to internal helpers for group/status/broadcast keys (alternating
     * participant) and user keys (alternating remote).
     *
     * @implNote WAWebLidMigrationUtils.getAlternateMsgKey: dispatches to
     *           internal helpers {@code S} (group/status/broadcast, where
     *           the participant JID is alternated) and {@code R}
     *           (1:1 user, where the remote JID is alternated).
     * @param msgKey the message key to create an alternate for, may be
     *               {@code null}
     * @return the alternate message key, or {@code null} if no alternate
     *         is available
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

        // WAWebLidMigrationUtils.getAlternateMsgKey
        // Dispatches to the group/status/broadcast path when the remote is a group or broadcast JID
        if (remote.hasGroupOrCommunityServer() || remote.hasBroadcastServer()) {
            return getAlternateMsgKeyForGroup(msgKey);
        }

        // WAWebLidMigrationUtils.getAlternateMsgKey
        // Dispatches to the 1:1 path when the remote is a user JID
        if (isUserWid(remote)) {
            return getAlternateMsgKeyForUser(msgKey);
        }

        return null;
    }

    /**
     * Builds the alternate message key for a group, status, or broadcast
     * message by swapping the participant JID.
     *
     * @implNote WAWebLidMigrationUtils.S: looks up an alternate participant
     *           through {@code getAlternateUserWid} and reconstructs the
     *           key around it.
     * @param msgKey the message key whose remote is a group, status, or
     *               broadcast JID
     * @return the alternate message key, or {@code null} if no alternate
     *         participant is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "S",
            adaptation = WhatsAppAdaptation.DIRECT)
    private MessageKey getAlternateMsgKeyForGroup(MessageKey msgKey) {
        // WAWebLidMigrationUtils.S
        // Requires a raw participant; a null participant short-circuits the lookup
        var participant = getRawParticipant(msgKey);
        if (participant == null) {
            return null;
        }

        // WAWebLidMigrationUtils.S
        // Looks up the alternate participant JID via getAlternateUserWid
        var alternateParticipant = getAlternateUserWid(participant.toUserJid());
        if (alternateParticipant == null) {
            return null;
        }

        // WAWebLidMigrationUtils.S
        // Reconstructs the message key around the alternate participant
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
     * Builds the alternate message key for a 1:1 message by swapping the
     * remote JID.
     *
     * @implNote WAWebLidMigrationUtils.R: looks up an alternate remote
     *           through {@code getAlternateUserWid} and reconstructs the
     *           key around it.
     * @param msgKey the message key whose remote is a user JID
     * @return the alternate message key, or {@code null} if no alternate
     *         remote is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "R",
            adaptation = WhatsAppAdaptation.DIRECT)
    private MessageKey getAlternateMsgKeyForUser(MessageKey msgKey) {
        var remote = msgKey.parentJid().orElse(null);
        if (remote == null) {
            return null;
        }

        // WAWebLidMigrationUtils.R
        // Looks up the alternate remote JID via getAlternateUserWid
        var alternateRemote = getAlternateUserWid(remote.toUserJid());
        if (alternateRemote == null) {
            return null;
        }

        // WAWebLidMigrationUtils.R
        // Reconstructs the message key around the alternate remote while keeping the raw participant
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
     * Returns the raw participant JID of a message key, returning
     * {@code null} when no participant is set.
     *
     * <p>Cobalt's {@link MessageKey#senderJid()} falls back to the parent
     * JID when the sender is unset; this helper recreates WA Web's raw
     * field access by detecting that fallback.
     *
     * @implNote ADAPTED: WAWebMsgKey.participant: WA Web exposes the
     *           raw participant field; Cobalt infers whether the fallback
     *           kicked in by comparing the sender to the parent.
     * @param msgKey the message key
     * @return the raw participant JID, or {@code null} if not set
     */
    private static Jid getRawParticipant(MessageKey msgKey) {
        // ADAPTED: WAWebMsgKey.participant
        // Reads the sender; senderJid() returns the parent when the raw participant was null
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
     * and edited messages; the three values follow slightly different
     * LID vs PN rules for Community Announcement Groups.
     *
     * @implNote WAWebMsgKeyUtils.TranslateMsgKeyType: the JS enum with
     *           values {@code Addon}, {@code Message}, and
     *           {@code EditMessage}.
     */
    @WhatsAppWebModule(moduleName = "WAWebMsgKeyUtils")
    public enum TranslateMsgKeyType {
        /**
         * Applied when the outgoing message is an addon (reaction,
         * receipt, etc.).
         *
         * @implNote WAWebMsgKeyUtils.TranslateMsgKeyType.Addon
         */
        ADDON,

        /**
         * Applied when the outgoing message is a regular message.
         *
         * @implNote WAWebMsgKeyUtils.TranslateMsgKeyType.Message
         */
        MESSAGE,

        /**
         * Applied when the outgoing message is an edit.
         *
         * @implNote WAWebMsgKeyUtils.TranslateMsgKeyType.EditMessage
         */
        EDIT_MESSAGE
    }

    /**
     * Returns the current user's identity (as LID or PN) appropriate for the given
     * chat and translate type.
     *
     * <p>The logic determines whether to use the LID or PN identity based on:
     * <ul>
     *     <li>Whether the chat JID is a LID</li>
     *     <li>Whether the chat is a Community Announcement Group (CAG)</li>
     *     <li>Whether the chat's group metadata has LID addressing mode enabled</li>
     *     <li>The translate type (Addon, Message, EditMessage)</li>
     * </ul>
     *
     * <p>For {@code Addon} type: returns LID if the chat is LID, CAG, or has LID
     * addressing mode; otherwise returns PN. For {@code Message} and {@code EditMessage}:
     * if CAG, returns LID only when group has LID addressing mode; otherwise uses the
     * same LID/PN decision as Addon.
     *
     * @implNote WAWebLidMigrationUtils.getMeUserLidOrJidForChat: selects
     *           LID or PN based on the chat JID, CAG detection, LID
     *           addressing mode, and the translate type.
     * @param chat          the chat for which to determine the user identity
     * @param translateType the type of message key translation
     * @return the current user's JID in the appropriate addressing mode
     * @throws IllegalStateException if the store has no JID or LID configured
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "getMeUserLidOrJidForChat",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Jid getMeUserLidOrJidForChat(Chat chat, TranslateMsgKeyType translateType) {
        // WAWebLidMigrationUtils.getMeUserLidOrJidForChat
        // Captures the chat JID and whether it is itself on the LID server
        var chatJid = chat.jid();
        var isLid = chatJid.hasLidServer();

        // WAWebChatGetters.getIsGroup
        // Identifies Community Announcement Groups by group server and default-subgroup metadata
        var chatMetadata = store.findChatMetadata(chatJid).orElse(null);
        var isGroup = chatJid.hasGroupOrCommunityServer();
        var isCAG = isGroup && chatMetadata instanceof com.github.auties00.cobalt.model.chat.group.GroupMetadata gm
                && gm.isDefaultSubgroup();

        // WAWebLidMigrationUtils.getMeUserLidOrJidForChat
        // Detects whether the group has flipped to LID addressing mode server-side
        var isLidAddressingMode = isGroup
                && chatMetadata != null
                && chatMetadata.isLidAddressingMode();

        // WAWebLidMigrationUtils.getMeUserLidOrJidForChat
        // Applies the LID/PN selection table: addon and message/edit follow slightly different CAG rules
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
     * Returns the LID and PN pair for a JID so both addressing modes can be updated.
     *
     * <p>If the JID is a LID, finds the corresponding PN and returns {@code [lid, pn]}.
     * If the JID is a PN, finds the corresponding LID and returns {@code [pn, lid]}.
     * If no mapping is found, returns a single-element list with just the original JID.
     *
     * <p>This matches WA Web's {@code getPnAndLidToUpdate(wid)} function which returns
     * a two-element array {@code [lid, pn]} when both are known, or a single-element
     * array when the alternate cannot be found.
     *
     * @implNote WAWebLidMigrationUtils.getPnAndLidToUpdate: returns a
     *           two-element array when both addressing modes are known,
     *           otherwise a single-element array.
     * @param jid the JID to find the PN/LID pair for, may be {@code null}
     * @return a list containing both addressing-mode JIDs, or just the
     *         original if no alternate is found
     */
    @WhatsAppWebExport(moduleName = "WAWebLidMigrationUtils", exports = "getPnAndLidToUpdate",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<Jid> getPnAndLidToUpdate(Jid jid) {
        if (jid == null) {
            return List.of();
        }

        // WAWebLidMigrationUtils.getPnAndLidToUpdate
        // LID path: pairs the LID with its resolved PN
        if (jid.hasLidServer()) {
            var pn = toPn(jid);
            if (pn != null) {
                return List.of(jid, pn);
            }
        } else {
            // WAWebLidMigrationUtils.getPnAndLidToUpdate
            // PN path: pairs the PN with its resolved LID
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
     * <p>A chat uses LID addressing if its JID is on the LID server, or if it is a
     * group whose group metadata has {@code isLidAddressingMode} enabled. This matches
     * WA Web's {@code chatIsLid(chat)} function which checks
     * {@code chat.id.isLid() || (isGroup && groupMetadata?.isLidAddressingMode)}.
     *
     * @implNote WAWebLidMigrationUtils.chatIsLid: returns
     *           {@code chat.id.isLid() || (isGroup && groupMetadata?.isLidAddressingMode)}.
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

        // WAWebLidMigrationUtils.chatIsLid
        // Short-circuits for chats already on the LID server
        if (chatJid.hasLidServer()) {
            return true;
        }

        // WAWebLidMigrationUtils.chatIsLid
        // For group chats, checks the groupMetadata.isLidAddressingMode flag
        if (chatJid.hasGroupOrCommunityServer()) {
            var chatMetadata = store.findChatMetadata(chatJid).orElse(null);
            return chatMetadata != null && chatMetadata.isLidAddressingMode();
        }

        return false;
    }

    /**
     * Returns the alternate user JID for the given JID.
     *
     * <p>For a LID, returns the phone number. For a phone number, returns the LID.
     * Handles the "me" user specially: if the given JID matches the current user's
     * PN, returns the current user's LID (and vice versa), before falling back to
     * store lookups.
     *
     * <p>This matches WA Web's {@code WAWebApiContact.getAlternateUserWid(wid)} which
     * dispatches to {@code getPhoneNumber()} for LIDs and {@code getCurrentLid()} for
     * PNs, with special handling for the "me" user.
     *
     * @implNote ADAPTED: WAWebApiContact.getAlternateUserWid: WA Web uses
     *           separate {@code getCurrentLid} and {@code getPhoneNumber}
     *           helpers with me-user special casing. Cobalt consolidates
     *           them into this method, reading {@code store.jid()} and
     *           {@code store.lid()} for the me-user fast path.
     *           <p>
     *           Three sub-exports are folded into this single method:
     *           <ul>
     *             <li>{@code WAWebApiContact.getAlternateUserWid(P)} — the
     *                 outer dispatcher whose JS form is
     *                 {@code e.isLid() ? getPhoneNumber(e) : getCurrentLid(e)};
     *                 WA throws when {@code e.device != null}, but every
     *                 caller in Cobalt already passes a user-level JID so the
     *                 throw is redundant.</li>
     *             <li>{@code WAWebApiContact.getPhoneNumber(A)} — the LID-to-PN
     *                 lookup with the me-user fast path.</li>
     *             <li>{@code WAWebApiContact.getCurrentLid(w)} — the PN-to-LID
     *                 lookup with the me-user fast path.</li>
     *           </ul>
     *           Cobalt accepts a {@code null} JID and returns {@code null}
     *           rather than throwing because the migration callers downstream
     *           already need to coalesce {@code null} into a fall-through path.
     * @param userJid the user JID (must already be stripped of
     *                device/agent data)
     * @return the alternate JID, or {@code null} if not found
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

        // WAWebApiContact.getAlternateUserWid
        // Dispatches to the LID path when the input is already a LID
        if (userJid.hasLidServer()) {
            // WAWebApiContact.getPhoneNumber
            // Me-user fast path: map the current user's LID back to their PN without a store lookup
            var meLid = store.lid().map(Jid::toUserJid).orElse(null);
            var mePn = store.jid().map(Jid::toUserJid).orElse(null);
            if (mePn != null && meLid != null && userJid.equals(meLid)) {
                return mePn;
            }
            return store.findPhoneByLid(userJid).orElse(null);
        } else {
            // WAWebApiContact.getCurrentLid
            // Me-user fast path: map the current user's PN back to their LID without a store lookup
            var mePn = store.jid().map(Jid::toUserJid).orElse(null);
            var meLid = store.lid().map(Jid::toUserJid).orElse(null);
            if (meLid != null && mePn != null && userJid.equals(mePn)) {
                return meLid;
            }
            return store.findLidByPhone(userJid).orElse(null);
        }
    }

    /**
     * Returns the current user's LID at user-level, throwing if no LID is
     * configured yet.
     *
     * @implNote ADAPTED: WAWebUserPrefsMeUser.getMeLidUserOrThrow:
     *           Cobalt reads {@code store.lid()} instead of the UserPrefs
     *           key.
     * @return the current user's LID, never {@code null}
     * @throws IllegalStateException if no LID is configured for the
     *         current user
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser", exports = "getMeLidUserOrThrow",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid getMeLidUserOrThrow() {
        // WAWebUserPrefsMeUser.getMeLidUserOrThrow
        // Reads the current user's LID from the store and strips it down to user level
        return store.lid()
                .map(Jid::toUserJid)
                .orElseThrow(() -> new IllegalStateException("No LID for current user"));
    }

    /**
     * Returns the current user's phone-number JID at user-level, throwing
     * if no JID is configured yet.
     *
     * @implNote ADAPTED: WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE:
     *           Cobalt reads {@code store.jid()} instead of the UserPrefs
     *           key.
     * @return the current user's PN JID, never {@code null}
     * @throws IllegalStateException if no JID is configured for the
     *         current user
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser", exports = "getMePnUserOrThrow_DO_NOT_USE",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Jid getMePnUserOrThrow() {
        // WAWebUserPrefsMeUser.getMePnUserOrThrow_DO_NOT_USE
        // Reads the current user's PN JID from the store and strips it down to user level
        return store.jid()
                .map(Jid::toUserJid)
                .orElseThrow(() -> new IllegalStateException("No PN for current user"));
    }

    /**
     * Returns whether the given JID is a user wid (on one of the user-like
     * servers) in the WhatsApp Web sense.
     *
     * <p>WA Web's {@code Wid.isUser()} returns {@code true} for servers
     * {@code c.us}, {@code lid}, {@code bot}, {@code hosted}, and
     * {@code hosted.lid}. Cobalt normalises {@code c.us} to
     * {@code s.whatsapp.net}.
     *
     * @implNote WAWebWid.isUser
     * @param jid the JID to check
     * @return {@code true} if the JID is a user wid
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "isUser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isUserWid(Jid jid) {
        // WAWebWid.isUser
        // Matches the union of user-like servers: c.us, lid, bot, hosted, hosted.lid
        return jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer();
    }
}
