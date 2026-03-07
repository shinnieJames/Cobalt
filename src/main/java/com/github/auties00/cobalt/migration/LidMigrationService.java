package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppLidMigrationException;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatDisappearingMode;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMute;
import com.github.auties00.cobalt.model.sync.history.HistorySync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMapping;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayload;
import com.github.auties00.cobalt.model.jid.migration.PhoneNumberToLIDMapping;
import com.github.auties00.cobalt.model.setting.GlobalSettings;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import com.github.auties00.cobalt.util.SchedulerUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service responsible for orchestrating LID (Long ID) migration.
 *
 * <p>LID migration is a process where WhatsApp transitions from phone number-based
 * addressing to LID-based addressing for improved privacy.
 */
public final class LidMigrationService {
    private static final System.Logger LOGGER = System.getLogger(LidMigrationService.class.getName());

    private static final long MAPPING_TIMEOUT_SECONDS = 300;

    /**
     * LID origin type value for phone-number-hiding click-to-WhatsApp chats.
     * Matches WA Web's {@code LidOriginType.PNH_CTWA} ({@code "ctwa"}).
     */
    private static final String LID_ORIGIN_TYPE_PNH_CTWA = "ctwa";

    /**
     * LID origin type value for general (non-PNH) chats.
     * Matches WA Web's {@code LidOriginType.GENERAL} ({@code "general"}).
     */
    private static final String LID_ORIGIN_TYPE_GENERAL = "general";

    /**
     * Stub types that are considered safe to ignore during LID migration deletability
     * checks, matching WA Web's {@code X()} function.
     *
     * <p>WA Web's {@code X()} matches exactly two conditions:
     * <ul>
     * <li>{@code getIsInitialE2ENotification}: type === "e2e_notification" AND subtype === "encrypt"
     * <li>{@code getIsDisappearingModeSystemMessage}: type === "notification_template" AND subtype === "disappearing_mode"
     * </ul>
     */
    private static final Set<ChatMessageInfo.StubType> MIGRATION_SAFE_STUB_TYPES = EnumSet.of(
            // Maps to getIsInitialE2ENotification (e2e_notification + encrypt)
            ChatMessageInfo.StubType.E2E_ENCRYPTED,
            ChatMessageInfo.StubType.E2E_ENCRYPTED_NOW,
            // Maps to getIsDisappearingModeSystemMessage (notification_template + disappearing_mode)
            ChatMessageInfo.StubType.DISAPPEARING_MODE
    );

    /**
     * Stub types that represent call log entries, matching WA Web's
     * {@code type === MSG_TYPE.CALL_LOG} check in the {@code ee()} function.
     */
    private static final Set<ChatMessageInfo.StubType> CALL_LOG_STUB_TYPES = EnumSet.of(
            ChatMessageInfo.StubType.CALL_MISSED_VOICE,
            ChatMessageInfo.StubType.CALL_MISSED_VIDEO,
            ChatMessageInfo.StubType.CALL_MISSED_GROUP_VOICE,
            ChatMessageInfo.StubType.CALL_MISSED_GROUP_VIDEO,
            ChatMessageInfo.StubType.SILENCED_UNKNOWN_CALLER_AUDIO,
            ChatMessageInfo.StubType.SILENCED_UNKNOWN_CALLER_VIDEO
    );

    private final WhatsAppClient whatsapp;
    private final WhatsAppStore store;
    private final ABPropsService abPropsService;

    /**
     * Current migration state.
     */
    private final AtomicReference<LidMigrationState> state;

    /**
     * Primary device's PN to assigned LID mappings cache.
     * Maps phone number (as numeric string) to the LID assigned by the primary
     * device at migration time.
     */
    private final ConcurrentHashMap<String, Jid> primaryPnToAssignedLidCache;

    /**
     * Primary device's PN to latest LID mappings cache.
     * Maps phone number (as numeric string) to the most recent LID known to
     * the primary device, which may differ from the assigned LID.
     */
    private final ConcurrentHashMap<String, Jid> primaryPnToLatestLidCache;

    /**
     * Cache of original LIDs for locally-created chats, keyed by the chat's
     * phone number JID user part.
     *
     * <p>Matches WA Web's {@code chat.originalLid} field, which is set in
     * {@code WAWebCreateChat} when a LID mapping is known at chat creation
     * time. Used as a last-resort fallback in {@code resolveThread()}
     * when no other LID mapping is available.
     */
    private final ConcurrentHashMap<String, Jid> originalLidCache;

    /**
     * Chat DB migration timestamp from primary device.
     */
    private volatile Instant chatDbMigrationTimestamp;

    /**
     * Timestamp when the protocol message was received, used as a fallback
     * when {@link #chatDbMigrationTimestamp} is {@code null}.
     */
    private volatile Instant receiveTimestamp;

    private volatile CompletableFuture<Void> mappingTimeoutFuture;

    /**
     * Creates a new LID migration service.
     *
     * @param whatsapp      the WhatsApp client instance
     * @param abPropsService the AB props service for reading feature flags
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
     * Initializes the migration service.
     * Should be called after connection is established.
     */
    public void initialize() {
        if (state.compareAndSet(LidMigrationState.NOT_STARTED, LidMigrationState.WAITING_PROP)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration initialized, waiting for AB prop");
        }
    }

    /**
     * Called when the AB prop indicates LID migration is enabled.
     * Transitions from WAITING_PROP to WAITING_MAPPINGS state.
     */
    public void enableMigration() {
        if (state.compareAndSet(LidMigrationState.WAITING_PROP, LidMigrationState.WAITING_MAPPINGS)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration enabled, waiting for mappings from primary");
            mappingTimeoutFuture = SchedulerUtils.scheduleDelayed(
                    Duration.ofSeconds(MAPPING_TIMEOUT_SECONDS),
                    () -> {
                        if (state.get() == LidMigrationState.WAITING_MAPPINGS) {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "LID migration timed out after {0}s waiting for mappings", MAPPING_TIMEOUT_SECONDS);
                            handleError(new WhatsAppLidMigrationException.FailedToParseMappings(
                                    "Timed out waiting for peer migration mappings"));
                        }
                    });
        }
    }

    /**
     * Called when the AB prop indicates LID migration is disabled.
     * Transitions from WAITING_PROP to DISABLED.
     */
    public void disableMigration() {
        if (state.compareAndSet(LidMigrationState.WAITING_PROP, LidMigrationState.DISABLED)) {
            LOGGER.log(System.Logger.Level.INFO, "LID migration disabled");
        }
    }

    /**
     * Processes LID migration mappings received from the primary device.
     *
     * <p>This method:
     * <ol>
     *     <li>Validates the payload</li>
     *     <li>Populates the primary mapping caches</li>
     *     <li>Updates contacts with LID information</li>
     *     <li>Transitions to READY state</li>
     * </ol>
     *
     * @param payload the decoded mapping payload from primary device
     */
    public void processProtocolMessage(LIDMigrationMappingSyncPayload payload) {
        if (payload == null) {
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("null payload"));
            return;
        }

        var currentState = state.get();
        if (currentState != LidMigrationState.WAITING_MAPPINGS && currentState != LidMigrationState.WAITING_PROP) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring mappings in state: {0}", currentState);
            return;
        }

        try {
            // Cancel mapping timeout since we received mappings
            var timeout = mappingTimeoutFuture;
            if (timeout != null) {
                timeout.cancel(false);
                mappingTimeoutFuture = null;
            }

            // Store migration timestamp and receive timestamp
            this.chatDbMigrationTimestamp = payload.chatDbMigrationTimestamp()
                    .orElse(null);
            this.receiveTimestamp = Instant.now();

            // Process mappings
            var mappings = payload.pnToLidMappings();
            if (mappings == null || mappings.isEmpty()) {
                handleError(new WhatsAppLidMigrationException.FailedToParseMappings(
                        "Peer migration mappings malformed: empty or null"));
                return;
            }

            LOGGER.log(System.Logger.Level.INFO, "Processing {0} LID mappings from primary", mappings.size());

            // Populate primary caches and update contacts
            for (var mapping : mappings) {
                processSingleMapping(mapping);
            }

            // Transition to READY state
            state.set(LidMigrationState.READY);
            LOGGER.log(System.Logger.Level.INFO, "LID migration ready with {0} assigned mappings, {1} latest mappings",
                    primaryPnToAssignedLidCache.size(), primaryPnToLatestLidCache.size());

            // Check if we should auto-start migration
            if (shouldAutoStartMigration()) {
                executeMigration();
            }

        } catch (Throwable throwable) {
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("error processing mappings", throwable));
        }
    }

    /**
     * Processes LID mappings from a HistorySync message.
     *
     * <p>This method extracts LID mappings from two sources:
     * <ol>
     *     <li>The top-level {@code phoneNumberToLidMappings} field</li>
     *     <li>Individual conversation entries containing {@code pnJid} or {@code lidJid} fields</li>
     * </ol>
     *
     * <p>History sync data is only stored in the general store (via
     * {@code store.registerLidMapping()} and contact updates), not in the
     * primary mapping caches. This matches WhatsApp Web's behavior where
     * history sync mappings do not feed into the migration decision caches.
     *
     * <p>Additionally, if GlobalSettings contains a {@code chatDbLidMigrationTimestamp},
     * that timestamp is recorded for use during migration timing decisions.
     *
     * @param historySync the decoded HistorySync protobuf
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
     * Processes a single PhoneNumberToLidMapping from the top-level history sync field.
     *
     * <p>History sync mappings are stored only in the general store, not in the
     * primary migration caches.
     *
     * @param mapping the mapping to process
     * @return {@code true} if a valid mapping was processed
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

        // Register bidirectional mapping in store (not in primary cache)
        store.registerLidMapping(pnJid, lidJid);

        // Update contact if exists
        store.findContactByJid(pnJid).ifPresent(contact -> contact.setLid(lidJid));

        return true;
    }

    /**
     * Extracts LID mapping from a conversation entry.
     *
     * <p>For LID chats (jid has lid server): extracts phone number from pnJid field.
     * For PN chats (jid has user server): extracts LID from lidJid field.
     *
     * <p>History sync conversation data is stored only in the general store, not
     * in the primary migration caches.
     *
     * @param conversation the conversation to process
     * @return {@code true} if a valid mapping was extracted
     */
    private boolean processConversationLidData(Chat conversation) {
        if (conversation == null) {
            return false;
        }

        var chatJid = conversation.jid();

        // Only process 1:1 chats (user or lid server)
        if (!chatJid.hasUserServer() && !chatJid.hasLidServer()) {
            return false;
        }

        final Jid phoneJid;
        final Jid lidJid;

        if (chatJid.hasLidServer()) {
            // LID chat: extract phone number from pnJid field
            phoneJid = conversation.phoneNumberJid().orElse(null);
            lidJid = chatJid;
        } else if (chatJid.hasUserServer()) {
            // PN chat: extract LID from lidJid field (the 'lid' field in Chat)
            phoneJid = chatJid;
            lidJid = conversation.lid().orElse(null);
        } else {
            phoneJid = null;
            lidJid = null;
        }

        if (phoneJid == null || lidJid == null) {
            return false;
        }

        // Register bidirectional mapping in store (not in primary cache)
        store.registerLidMapping(phoneJid, lidJid);

        // Update contact if exists
        store.findContactByJid(phoneJid).ifPresent(contact -> contact.setLid(lidJid));

        // Update the chat itself to ensure it has the mapping
        conversation.setLid(lidJid);
        if (!phoneJid.equals(chatJid)) {
            conversation.setPhoneNumberJid(phoneJid);
        }

        return true;
    }

    /**
     * Processes a single LID mapping entry from the primary device's protocol message.
     *
     * <p>Stores {@code assignedLid} and {@code latestLid} into their respective
     * caches separately, matching WhatsApp Web's behavior where {@code getLidForPn()}
     * returns only the assigned LID.
     *
     * @param mapping the mapping entry to process
     */
    private void processSingleMapping(LIDMigrationMapping mapping) {
        if (mapping == null) {
            return;
        }

        var jid = mapping.pn();
        var user = jid.user();

        // Store assignedLid in its own cache
        var assignedLid = mapping.assignedLid();
        primaryPnToAssignedLidCache.put(user, assignedLid);

        // Store latestLid in its own cache (if present)
        mapping.latestLid().ifPresent(latest ->
                primaryPnToLatestLidCache.put(user, latest)
        );

        // Update contact if exists
        store.findContactByJid(jid).ifPresent(contact -> {
            contact.setLid(assignedLid);
            store.registerLidMapping(jid, assignedLid);
        });
    }

    /**
     * Executes the LID migration for all eligible chat threads.
     */
    public void executeMigration() {
        if (!state.compareAndSet(LidMigrationState.READY, LidMigrationState.IN_PROGRESS)) {
            var currentState = state.get();
            LOGGER.log(System.Logger.Level.WARNING, "Cannot start migration in state: {0}", currentState);
            return;
        }

        // Check compatibility AB prop before proceeding
        if (!abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_COMPATIBLE)) {
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

            // Phase 1: Resolve all threads (split thread detection is inline)
            for (var chat : chatsToProcess) {
                var resolution = resolveThread(chat, existingLidThreads);
                resolutions.add(resolution);
            }

            // Phase 2: Execute migrations
            executeResolutions(resolutions);

            // Phase 3: Bulk-register all primary mappings in the store
            learnMappingsInBulk();

            // Phase 4: Mark complete
            state.set(LidMigrationState.COMPLETE);
            LOGGER.log(System.Logger.Level.INFO, "LID migration completed");

        } catch (WhatsAppLidMigrationException e) {
            handleError(e);
        } catch (Throwable throwable) {
            handleError(new WhatsAppLidMigrationException.FailedToParseMappings("migration execution failed", throwable));
        }
    }

    /**
     * Resolves a single chat thread to determine its migration action.
     *
     * @param chat               the chat to resolve
     * @param existingLidThreads the set of JIDs of existing LID threads, used for
     *                           inline split thread detection
     * @return the resolution for this thread
     * @throws WhatsAppLidMigrationException.PrimaryMappingsObsolete if a LID mismatch
     *         indicates stale primary mappings
     * @throws WhatsAppLidMigrationException.NoLidAvailable if a non-deletable chat
     *         has no LID mapping
     * @throws WhatsAppLidMigrationException.SplitThreadMismatch if a local LID would
     *         collide with an existing LID thread
     */
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
     * Returns the effective sync timestamp for migration timing comparisons.
     *
     * <p>Returns {@link #chatDbMigrationTimestamp} if non-{@code null}, otherwise
     * falls back to {@link #receiveTimestamp}, and finally to {@link Instant#EPOCH}.
     *
     * @return the effective sync timestamp, never {@code null}
     */
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
     * Determines if a chat can be deleted during LID migration when no LID
     * mapping is available.
     *
     * <p>This matches WhatsApp Web's deletion logic (function {@code j/K}):
     * <ol>
     *     <li>Chats with ephemeral settings are NOT deletable, unless the
     *         disappearing mode trigger is {@code ACCOUNT_SETTING} and the
     *         chat contains a disappearing mode system message</li>
     *     <li>Locked chats are NOT deletable</li>
     *     <li>Archived chats are NOT deletable</li>
     *     <li>Muted chats are NOT deletable</li>
     *     <li>Chats where all messages are safe system stubs are deletable</li>
     *     <li>Chats where all messages are safe system stubs or call log
     *         entries (with at least one call log) are deletable</li>
     *     <li>Otherwise the chat is NOT deletable</li>
     * </ol>
     *
     * @param chat the chat to evaluate
     * @return {@code true} if the chat can be safely deleted
     */
    private boolean canDeleteChat(Chat chat) {
        var messages = chat.messages();

        // Ephemeral settings check — NOT deletable unless exempted
        if (hasEphemeralSettings(chat) && !isEphemeralExempt(chat, messages)) {
            return false;
        }

        // Locked chats are NOT deletable
        if (chat.locked()) {
            return false;
        }

        // Archived chats are NOT deletable
        if (chat.archived()) {
            return false;
        }

        // Muted chats are NOT deletable
        if (chat.mute().map(ChatMute::isMuted).orElse(false)) {
            return false;
        }

        // Message content check: deletable if all messages are safe stubs,
        // or all messages are safe stubs + call log entries with at least one call log
        return allMessagesAreSafeStubs(messages) || allMessagesAreSafeStubsOrCallLog(messages);
    }

    /**
     * Returns whether the chat has ephemeral settings configured.
     *
     * <p>Matches WA Web's check: {@code e.ephemeralDuration != null || e.ephemeralSettingTimestamp != null}.
     *
     * @param chat the chat to check
     * @return {@code true} if the chat has ephemeral duration or setting timestamp
     */
    private boolean hasEphemeralSettings(Chat chat) {
        return chat.ephemeralExpiration().isPresent() || chat.ephemeralSettingTimestamp().isPresent();
    }

    /**
     * Returns whether the chat is exempt from the ephemeral deletability block.
     *
     * <p>Matches WA Web's {@code re()} function: returns {@code true} if the chat's
     * disappearing mode trigger is {@code ACCOUNT_SETTING} and the message list contains
     * at least one disappearing mode system message.
     *
     * @param chat     the chat to check
     * @param messages the chat's messages
     * @return {@code true} if the chat is exempt from the ephemeral block
     */
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
     * Returns whether all messages in the collection are migration-safe system stubs.
     *
     * <p>Matches WA Web's {@code r.every(X)} check, where {@code X()} returns {@code true}
     * for initial e2e notifications and disappearing mode system messages.
     *
     * @param messages the messages to check
     * @return {@code true} if every message is a safe system stub
     */
    private boolean allMessagesAreSafeStubs(Collection<ChatMessageInfo> messages) {
        return messages.stream().allMatch(this::isMigrationSafeStub);
    }

    /**
     * Returns whether all messages are either migration-safe stubs or call log entries,
     * with at least one call log entry present.
     *
     * <p>Matches WA Web's {@code ee(r)} function.
     *
     * @param messages the messages to check
     * @return {@code true} if every message is a safe stub or call log, with at least one call log
     */
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
     * Returns whether a message is a migration-safe system stub that can be ignored.
     *
     * @param msg the message to check
     * @return {@code true} if the message is a safe stub
     */
    private boolean isMigrationSafeStub(ChatMessageInfo msg) {
        if (!msg.message().isEmpty()) {
            return false;
        }

        var stubType = msg.messageStubType().orElse(null);
        return stubType != null && MIGRATION_SAFE_STUB_TYPES.contains(stubType);
    }

    /**
     * Returns whether a message is a call log entry.
     *
     * @param msg the message to check
     * @return {@code true} if the message is a call log entry
     */
    private boolean isCallLogMessage(ChatMessageInfo msg) {
        var stubType = msg.messageStubType().orElse(null);
        return stubType != null && CALL_LOG_STUB_TYPES.contains(stubType);
    }


    /**
     * Executes the resolved migrations.
     *
     * @param resolutions the resolutions to execute
     */
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
     * Executes a migration resolution - updates the chat to use LID addressing.
     */
    private void executeMigrate(LidMigrationResolution.Migrate migrate) {
        var originalJid = migrate.originalJid();
        var targetLid = migrate.targetLid();

        // Find the chat
        var chat = store.findChatByJid(originalJid).orElse(null);
        if (chat == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Chat not found for migration: {0}", originalJid);
            return;
        }

        // Update the chat's LID
        chat.setLid(targetLid);

        // Store the phoneJid for backward compatibility
        chat.setPhoneNumberJid(originalJid);

        // Register the mapping in the store
        store.registerLidMapping(originalJid, targetLid);

        // Update associated contact if exists
        store.findContactByJid(originalJid).ifPresent(contact -> {
            contact.setLid(targetLid);
        });

        LOGGER.log(System.Logger.Level.DEBUG, "Migrated chat {0} -> {1}", originalJid, targetLid);
    }

    /**
     * Executes a delete resolution - removes the chat.
     */
    private void executeDelete(LidMigrationResolution.Delete delete) {
        var originalJid = delete.originalJid();

        // Remove the chat from store
        var removed = store.removeChat(originalJid);

        if (removed.isPresent()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Deleted chat {0}: {1}", originalJid, delete.reason());
        }
    }

    /**
     * Handles a LID change notification for a contact.
     *
     * @param phoneJid the phone number JID
     * @param newLid   the new LID
     * @param oldLid   the old LID (may be null)
     */
    public void changeLid(Jid phoneJid, Jid newLid, Jid oldLid) {
        if (phoneJid == null || newLid == null) {
            return;
        }

        // Update primary caches
        if (phoneJid.user() != null) {
            primaryPnToAssignedLidCache.put(phoneJid.user(), newLid);
            primaryPnToLatestLidCache.put(phoneJid.user(), newLid);
        }

        // Update store mappings
        store.registerLidMapping(phoneJid, newLid);

        // Update contact
        store.findContactByJid(phoneJid).ifPresent(contact -> {
            contact.setLid(newLid);
        });

        // Update chat
        store.findChatByJid(phoneJid).ifPresent(chat -> {
            chat.setLid(newLid);
            chat.setPhoneNumberJid(phoneJid);
        });

        LOGGER.log(System.Logger.Level.DEBUG, "LID changed for {0}: {1} -> {2}", phoneJid, oldLid, newLid);
    }

    /**
     * Registers the original LID for a locally-created chat.
     *
     * <p>This should be called from the chat creation logic when a LID mapping
     * is already known at chat creation time and LID migration has not yet
     * completed. Matches WA Web's {@code WAWebCreateChat} behavior where
     * {@code originalLid} is set on the chat object.
     *
     * @param phoneJid the phone number JID of the chat
     * @param lid      the LID known at chat creation time
     */
    public void registerOriginalLid(Jid phoneJid, Jid lid) {
        if (phoneJid == null || lid == null || phoneJid.user() == null) {
            return;
        }

        originalLidCache.put(phoneJid.user(), lid);
    }

    /**
     * Persists primary mappings to the store's bidirectional mapping tables.
     *
     * <p>Matches WA Web's {@code learnMappingsInBulk()} which uses two learning
     * sources with skip logic:
     * <ol>
     *     <li>Skips entries where the assigned LID already matches the store's
     *         current LID for that phone number</li>
     *     <li>Processes "migration-sync-old" mappings first (assigned LID only,
     *         when the latest LID already matches local)</li>
     *     <li>Processes "migration-sync-latest" mappings second (both assigned
     *         and latest LIDs, when the latest LID differs from local)</li>
     * </ol>
     */
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
     * Determines if migration should auto-start.
     *
     * <p>WhatsApp Web unconditionally triggers migration after READY state,
     * so this always returns {@code true}.
     *
     * @return {@code true}
     */
    private boolean shouldAutoStartMigration() {
        return true;
    }

    /**
     * Handles a migration error.
     */
    private void handleError(WhatsAppLidMigrationException error) {
        state.set(LidMigrationState.FAILED);
        LOGGER.log(System.Logger.Level.ERROR, "LID migration failed: {0}", error.getMessage());
        whatsapp.handleFailure(error);
    }

    /**
     * Resets the migration service state for a new session.
     * Called when the client disconnects and reconnects.
     */
    public void reset() {
        var timeout = mappingTimeoutFuture;
        if (timeout != null) {
            timeout.cancel(false);
            mappingTimeoutFuture = null;
        }
        var currentState = state.get();
        // Only reset if not in a terminal state
        if (!currentState.isTerminal()) {
            state.set(LidMigrationState.NOT_STARTED);
        }
        // Don't clear primary caches on reconnect - they persist across sessions
    }

    /**
     * Looks up a LID for a phone number JID.
     * Checks primary assigned cache first, then store mappings.
     *
     * @param phoneJid the phone number JID
     * @return the LID if found
     */
    public Optional<Jid> lookupLid(Jid phoneJid) {
        if (phoneJid == null || phoneJid.user() == null) {
            return Optional.empty();
        }

        // Check primary assigned cache
        var cached = primaryPnToAssignedLidCache.get(phoneJid.user());
        if (cached != null) {
            return Optional.of(cached);
        }

        // Check store mappings (these don't expire)
        return store.findLidByPhone(phoneJid);
    }

    /**
     * Determines whether to use LID addressing mode for a recipient.
     *
     * @param recipientJid the recipient's JID
     * @return {@code true} if LID addressing should be used
     */
    public boolean shouldUseLidAddressing(Jid recipientJid) {
        if (recipientJid == null) {
            return false;
        }

        // Already using LID
        if (recipientJid.hasLidServer()) {
            return true;
        }

        // Groups, newsletters, broadcasts don't use LID addressing
        if (recipientJid.hasGroupOrCommunityServer() ||
            recipientJid.hasNewsletterServer() ||
            recipientJid.hasBroadcastServer()) {
            return false;
        }

        // Check migration state
        var currentState = state.get();
        if (currentState != LidMigrationState.COMPLETE && currentState != LidMigrationState.IN_PROGRESS) {
            return false;
        }

        // Check if we have a LID for this recipient
        return lookupLid(recipientJid).isPresent();
    }
}
