package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.util.SchedulerUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background service that upgrades inactive group chats to LID addressing
 * by re-querying their metadata from the WhatsApp server.
 *
 * <p>On WhatsApp Web/Desktop, groups must eventually switch from phone-number
 * addressing to LID addressing. Active groups pick this up naturally when a
 * member sends a message, but inactive groups remain on the old addressing
 * mode indefinitely. This service walks the local chat store, asks the
 * server for metadata on every non-LID group, and relies on the server's
 * response to flip each group's {@code isLidAddressingMode} flag. Once no
 * PN-mode groups remain, the migration is considered complete and does not
 * run again for the session.
 *
 * <p>Execution is gated on the
 * {@link ABProp#ENABLE_INACTIVE_GROUP_LID_MIGRATION} AB prop. The first pass
 * runs 60 seconds after startup; if any groups are still on PN after the
 * pass, a retry is scheduled for 24 hours later.
 *
 * @implNote WAWebInactiveGroupLidMigrationJob: adapts the
 * {@code migrateInactiveGroupsToLid} export, its inner {@code findPnGroups}
 * helper, and the retry-on-remaining behaviour. Depends on
 * {@code WAWebQueryAndUpdateGroupMetadataJob} and {@code WAWebABProps} in
 * WA Web; in Cobalt these are replaced by {@link WhatsAppClient} and
 * {@link ABPropsService} via constructor DI.
 */
@WhatsAppWebModule(moduleName = "WAWebInactiveGroupLidMigrationJob")
@WhatsAppWebModule(moduleName = "WAWebInactiveGroupLidMigration")
public final class InactiveGroupLidMigrationService {
    /**
     * Logger used to trace migration progress and failures in the Cobalt
     * client.
     *
     * @implNote ADAPTED: WAWebInactiveGroupLidMigrationJob uses
     *           {@code WALogger.LOG}/{@code WALogger.ERROR} with tagged
     *           template literals. Cobalt uses {@link System.Logger}.
     */
    private static final System.Logger LOGGER = System.getLogger(InactiveGroupLidMigrationService.class.getName());

    /**
     * Delay before the first migration attempt, matching the WhatsApp Web
     * requirement that at least 60 seconds have elapsed since pairing.
     *
     * @implNote ADAPTED: WAWebTasksDefinitions registers the task with
     *           a 60-second post-pairing delay; Cobalt hard-codes the
     *           same value and schedules it directly.
     */
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(60);

    /**
     * Delay before a follow-up migration attempt when some groups still
     * remain on phone-number addressing after the initial pass.
     *
     * @implNote ADAPTED: WAWebTasksDefinitions schedules the task at
     *           {@code DAY_SECONDS} intervals. Cobalt self-schedules
     *           with the same 24-hour delay.
     */
    private static final Duration RETRY_DELAY = Duration.ofDays(1);

    /**
     * The WhatsApp client used to query group metadata from the server.
     *
     * @implNote WAWebInactiveGroupLidMigrationJob: corresponds to the
     *           module-level import of {@code WAWebQueryAndUpdateGroupMetadataJob}.
     */
    private final WhatsAppClient client;

    /**
     * Provides access to server-side AB props; used to read the
     * {@link ABProp#ENABLE_INACTIVE_GROUP_LID_MIGRATION} feature flag.
     *
     * @implNote WAWebInactiveGroupLidMigrationJob: corresponds to the
     *           module-level import of {@code WAWebABProps}.
     */
    private final ABPropsService abPropsService;

    /**
     * Tracks whether the inactive-group LID migration has already completed
     * for this session.
     *
     * <p>WA Web persists this flag via
     * {@code WAWebUserPrefsStore} under the key
     * {@code UserPrefs.InactiveGroupLidMigrationComplete}. Cobalt keeps a
     * process-local {@link AtomicBoolean} because the UserPrefs persistence
     * layer is not replicated for this single boolean flag; the field plays
     * the role of the {@code UserPrefs.InactiveGroupLidMigrationComplete}
     * slot read and written by the two module exports.
     *
     * @implNote ADAPTED: backing storage for
     *           {@link #isInactiveGroupLidMigrationComplete()} and
     *           {@link #setInactiveGroupLidMigrationComplete()}.
     */
    private final AtomicBoolean complete;

    /**
     * Handle on the currently scheduled migration task, or {@code null} if
     * no task is pending. Used to cancel pending retries on
     * disconnect/reconnect.
     *
     * @implNote Cobalt lifecycle field for managing scheduled task
     *           cancellation. WA Web relies on the task scheduler's own
     *           lifecycle management.
     */
    private volatile CompletableFuture<Void> scheduledTask;

    /**
     * Constructs a new inactive-group LID migration service.
     *
     * @implNote WAWebInactiveGroupLidMigration: module-level initialization.
     *           The WA Web module initializes by reading
     *           {@code UserPrefs.InactiveGroupLidMigrationComplete}
     *           from {@code WAWebUserPrefsStore}. Cobalt uses constructor
     *           DI with an {@link AtomicBoolean} initialized to {@code false}.
     * @param client         the WhatsApp client used for querying group metadata
     * @param abPropsService the AB props service used for checking the feature flag
     */
    public InactiveGroupLidMigrationService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = Objects.requireNonNull(client, "client");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.complete = new AtomicBoolean(false);
    }

    /**
     * Starts the migration service by scheduling the first pass after the
     * initial delay.
     *
     * <p>This method should be called once after the client has logged in
     * and AB props are available. Subsequent calls after completion are
     * no-ops.
     *
     * @implNote ADAPTED: WAWebTasksDefinitions.registerTasks schedules the
     *           migration task via the WA Web task scheduler with a 60-second
     *           initial delay. Cobalt uses {@link SchedulerUtils#scheduleDelayed}
     *           to achieve the same effect.
     */
    public void start() {
        // WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
        // Short-circuits scheduling when the migration has already been marked complete in a previous call
        if (isInactiveGroupLidMigrationComplete()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "[lid-inactive-group-migration] already done, skip");
            return;
        }

        // WAWebTasksDefinitions.registerTasks
        // Schedules the migration pass to run after the 60-second post-pairing grace period
        scheduledTask = SchedulerUtils.scheduleDelayed(INITIAL_DELAY, this::run);
    }

    /**
     * Cancels any pending scheduled migration task so no further passes
     * run until {@link #start()} is called again.
     *
     * <p>Intended to be invoked when the client disconnects and needs to
     * clear scheduled work tied to the session.
     *
     * @implNote Cobalt lifecycle method for cleaning up scheduled tasks
     *           on disconnect/reconnect. WA Web relies on the task
     *           scheduler's own cleanup mechanism.
     */
    public void reset() {
        var task = scheduledTask;
        if (task != null) {
            task.cancel(true);
            scheduledTask = null;
        }
    }

    /**
     * Returns whether the inactive-group LID migration has been recorded as
     * complete for this client.
     *
     * <p>WhatsApp Web reads
     * {@code UserPrefs.InactiveGroupLidMigrationComplete} from
     * {@code WAWebUserPrefsStore} and returns {@code true} if and only if the
     * persisted value is strictly equal to {@code true}; any other value
     * (including {@code null} / {@code undefined}) returns {@code false}.
     * Cobalt mirrors this semantic with the local {@link AtomicBoolean},
     * which defaults to {@code false} when the flag has never been written.
     *
     * @implNote WAWebInactiveGroupLidMigration.isInactiveGroupLidMigrationComplete:
     *           {@code === true} strict-equality check is preserved by the
     *           {@link AtomicBoolean#get()} contract, which always returns a
     *           primitive {@code boolean}.
     * @return {@code true} if the migration has been marked complete,
     *         {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigration",
            exports = "isInactiveGroupLidMigrationComplete",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isInactiveGroupLidMigrationComplete() {
        // WAWebInactiveGroupLidMigration.isInactiveGroupLidMigrationComplete
        // Reads the UserPrefs.InactiveGroupLidMigrationComplete slot and applies the === true strict-equality semantic
        return complete.get();
    }

    /**
     * Marks the inactive-group LID migration as complete for this client.
     *
     * <p>WhatsApp Web writes {@code true} to
     * {@code UserPrefs.InactiveGroupLidMigrationComplete} via
     * {@code WAWebUserPrefsStore.set}. The export takes no parameter; the
     * value being written is hard-coded to {@code true}. Cobalt mirrors this
     * by always setting the underlying {@link AtomicBoolean} to {@code true}.
     *
     * @implNote WAWebInactiveGroupLidMigration.setInactiveGroupLidMigrationComplete:
     *           the JS export writes {@code !0} (i.e. {@code true}) and never
     *           clears the flag.
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigration",
            exports = "setInactiveGroupLidMigrationComplete",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void setInactiveGroupLidMigrationComplete() {
        // WAWebInactiveGroupLidMigration.setInactiveGroupLidMigrationComplete
        // Persists true into the UserPrefs.InactiveGroupLidMigrationComplete slot
        complete.set(true);
    }

    /**
     * Executes a single migration pass.
     *
     * <p>Checks the AB prop, finds PN-mode groups, queries their metadata,
     * and either marks migration complete or schedules a retry 24 hours
     * later.
     *
     * @implNote WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid:
     *           WA Web uses {@code queryAndUpdateAllGroupMetadata} for batch
     *           queries; Cobalt iterates individually via
     *           {@code queryChatMetadata}. WA Web relies on the external task
     *           scheduler for retry (24h interval); Cobalt self-schedules via
     *           {@link SchedulerUtils#scheduleDelayed}. WA Web rethrows
     *           errors to the task scheduler; Cobalt logs and swallows since
     *           the scheduled task handles completion independently.
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigrationJob",
            exports = "migrateInactiveGroupsToLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void run() {
        try {
            // WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
            // Gates execution on the AB prop that enables inactive-group LID migration server-side
            if (!abPropsService.getBool(ABProp.ENABLE_INACTIVE_GROUP_LID_MIGRATION)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "[lid-inactive-group-migration] ABProp disabled, skip");
                return;
            }

            // WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
            // Re-checks the complete flag in case the initial pass ran during a previous session
            if (isInactiveGroupLidMigrationComplete()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "[lid-inactive-group-migration] already done, skip");
                return;
            }

            LOGGER.log(System.Logger.Level.INFO,
                    "[lid-inactive-group-migration] starting migration");

            // WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
            // Collects the set of group chats that are still on phone-number addressing
            var pnGroups = findPnGroups();
            if (pnGroups.isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO,
                        "[lid-inactive-group-migration] no PN groups, done");
                setInactiveGroupLidMigrationComplete();
                return;
            }

            LOGGER.log(System.Logger.Level.INFO,
                    "[lid-inactive-group-migration] found {0} PN groups", pnGroups.size());

            // ADAPTED: WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
            // WA Web uses queryAndUpdateAllGroupMetadata for a batch request; Cobalt issues one query per group
            for (var groupJid : pnGroups) {
                try {
                    client.queryChatMetadata(groupJid);
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "[lid-inactive-group-migration] failed to query {0}: {1}",
                            groupJid, e.getMessage());
                }
            }

            LOGGER.log(System.Logger.Level.INFO,
                    "[lid-inactive-group-migration] groups queried+updated");

            // WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
            // Re-scans the store after the refresh to decide between completion and retry
            var remaining = findPnGroups();
            if (remaining.isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO,
                        "[lid-inactive-group-migration] no PN groups left, done");
                setInactiveGroupLidMigrationComplete();
            } else {
                LOGGER.log(System.Logger.Level.INFO,
                        "[lid-inactive-group-migration] {0} PN groups left, retry later",
                        remaining.size());
                scheduledTask = SchedulerUtils.scheduleDelayed(RETRY_DELAY, this::run);
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "[lid-inactive-group-migration] Failed to complete migration: {0}",
                    e.getMessage());
        }
    }

    /**
     * Finds all group chats that are still using phone-number addressing.
     *
     * <p>Walks the stored chats on the group-or-community server, loads
     * their cached metadata, and returns only those that have not yet been
     * flipped to LID and are not suspended or terminated.
     *
     * <p>WA Web makes the membership check explicit via
     * {@code bulkCheckMyMembership}; Cobalt's store already holds only
     * chats the user participates in, so the membership filter is
     * implicit.
     *
     * @implNote ADAPTED: WAWebInactiveGroupLidMigrationJob.C (findPnGroups).
     *           WA Web calls {@code getGroupMetadataTable().all()}, filters by
     *           {@code isLidAddressingMode !== true}, then calls
     *           {@code bulkCheckMyMembership} and excludes
     *           {@code suspended}/{@code terminated} groups. Cobalt uses
     *           {@code store.chats()} (which implicitly filters to member
     *           chats) and checks suspended/terminated on the concrete
     *           metadata types.
     * @return the list of group JIDs that have not migrated to LID
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigrationJob",
            exports = "migrateInactiveGroupsToLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private List<Jid> findPnGroups() {
        var store = client.store();
        return store.chats()
                .stream()
                .map(chat -> chat.jid())
                // WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
                // Restricts the scan to chats on the groups-or-communities server
                .filter(jid -> jid.hasServer(JidServer.groupOrCommunity()))
                .filter(jid -> {
                    // WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
                    // Keeps only groups whose metadata is cached, still on PN addressing, and neither suspended nor terminated
                    var metadata = store.findChatMetadata(jid).orElse(null);
                    return metadata != null
                            && !metadata.isLidAddressingMode()
                            && !isSuspendedOrTerminated(metadata);
                })
                .toList();
    }

    /**
     * Returns whether the given group or community metadata indicates the
     * chat is suspended or has been terminated.
     *
     * <p>Because {@link ChatMetadata} is a sealed interface permitting
     * {@link GroupMetadata} and {@link CommunityMetadata}, this method uses
     * pattern matching to access the concrete accessors for each variant.
     *
     * @implNote WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid:
     *           the {@code !e.suspended && !e.terminated} filter applied
     *           after the membership check.
     * @param metadata the non-{@code null} chat metadata to inspect
     * @return {@code true} if the group or community is suspended or terminated
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigrationJob",
            exports = "migrateInactiveGroupsToLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isSuspendedOrTerminated(ChatMetadata metadata) {
        return switch (metadata) {
            case GroupMetadata gm -> gm.isSuspended() || gm.isTerminated();
            case CommunityMetadata cm -> cm.isSuspended() || cm.isTerminated();
        };
    }
}
