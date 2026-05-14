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
 * by re-querying their metadata from the server.
 *
 * <p>Active groups flip from phone-number addressing to LID addressing as
 * soon as a member sends a message, but inactive groups stay on the old
 * mode indefinitely. This service walks the local chat store, asks the
 * server for fresh metadata on every group still on PN, and relies on the
 * server response to flip each group's {@code isLidAddressingMode} flag.
 * Once no PN-mode groups remain, the migration is recorded as complete and
 * does not run again for the session.
 *
 * <p>Execution is gated on the
 * {@link ABProp#ENABLE_INACTIVE_GROUP_LID_MIGRATION} AB prop. The first
 * pass runs sixty seconds after pairing; if any groups are still on PN
 * after the pass, a retry is scheduled twenty-four hours later.
 */
@WhatsAppWebModule(moduleName = "WAWebInactiveGroupLidMigrationJob")
@WhatsAppWebModule(moduleName = "WAWebInactiveGroupLidMigration")
public final class InactiveGroupLidMigrationService {
    /**
     * Logger used to trace migration progress and failures.
     */
    private static final System.Logger LOGGER = System.getLogger(InactiveGroupLidMigrationService.class.getName());

    /**
     * Delay applied before the first migration attempt, matching the
     * sixty-second post-pairing grace period that WhatsApp Web's task
     * scheduler enforces.
     */
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(60);

    /**
     * Delay applied before a follow-up attempt when some groups are still
     * on phone-number addressing after the initial pass.
     */
    private static final Duration RETRY_DELAY = Duration.ofDays(1);

    /**
     * Client used to query group metadata from the server.
     */
    private final WhatsAppClient client;

    /**
     * Service used to read the AB prop that gates execution.
     */
    private final ABPropsService abPropsService;

    /**
     * Tracks whether the inactive-group LID migration has already
     * completed for this session.
     */
    private final AtomicBoolean complete;

    /**
     * Currently scheduled migration task, or {@code null} if no task is
     * pending. Held so a pending retry can be cancelled when the client
     * disconnects or reconnects.
     */
    private volatile CompletableFuture<Void> scheduledTask;

    /**
     * Constructs a new service bound to the given client and AB props
     * service.
     *
     * @param client         the client used for querying group metadata
     * @param abPropsService the AB props service used for the gating flag
     */
    public InactiveGroupLidMigrationService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = Objects.requireNonNull(client, "client");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.complete = new AtomicBoolean(false);
    }

    /**
     * Starts the service by scheduling the first migration pass after the
     * initial delay.
     *
     * <p>Should be called once after the client has logged in and AB
     * props are available. Subsequent calls after completion are no-ops.
     */
    public void start() {
        if (isInactiveGroupLidMigrationComplete()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "[lid-inactive-group-migration] already done, skip");
            return;
        }

        scheduledTask = SchedulerUtils.scheduleDelayed(INITIAL_DELAY, this::run);
    }

    /**
     * Cancels any pending scheduled task so no further passes run until
     * {@link #start()} is called again.
     *
     * <p>Intended to be invoked when the client disconnects and needs to
     * release scheduled work tied to the session.
     */
    public void reset() {
        var task = scheduledTask;
        if (task != null) {
            task.cancel(true);
            scheduledTask = null;
        }
    }

    /**
     * Returns whether the inactive-group LID migration has been recorded
     * as complete for this client.
     *
     * @return {@code true} if the migration has been marked complete
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigration",
            exports = "isInactiveGroupLidMigrationComplete",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isInactiveGroupLidMigrationComplete() {
        return complete.get();
    }

    /**
     * Marks the inactive-group LID migration as complete for this client.
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigration",
            exports = "setInactiveGroupLidMigrationComplete",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void setInactiveGroupLidMigrationComplete() {
        complete.set(true);
    }

    /**
     * Executes a single migration pass.
     *
     * <p>Checks the AB prop, finds PN-mode groups, queries their metadata
     * one by one, and either marks the migration complete or schedules a
     * retry when groups remain.
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigrationJob",
            exports = "migrateInactiveGroupsToLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    void run() {
        try {
            // WA Web's migrateInactiveGroupsToLid runs unconditionally now
            // (the enable_inactive_group_lid_migration prop was retired);
            // only the completion-state guard remains.
            if (isInactiveGroupLidMigrationComplete()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "[lid-inactive-group-migration] already done, skip");
                return;
            }

            LOGGER.log(System.Logger.Level.INFO,
                    "[lid-inactive-group-migration] starting migration");

            var pnGroups = findPnGroups();
            if (pnGroups.isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO,
                        "[lid-inactive-group-migration] no PN groups, done");
                setInactiveGroupLidMigrationComplete();
                return;
            }

            LOGGER.log(System.Logger.Level.INFO,
                    "[lid-inactive-group-migration] found {0} PN groups", pnGroups.size());

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
     * their cached metadata, and returns only those that have not yet
     * been flipped to LID and are not suspended or terminated.
     * @return the JIDs of the groups that have not migrated to LID
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigrationJob",
            exports = "migrateInactiveGroupsToLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    List<Jid> findPnGroups() {
        var store = client.store();
        return store.chats()
                .stream()
                .map(chat -> chat.jid())
                .filter(jid -> jid.hasServer(JidServer.groupOrCommunity()))
                .filter(jid -> {
                    var metadata = store.findChatMetadata(jid).orElse(null);
                    return metadata != null
                            && !metadata.isLidAddressingMode()
                            && !isSuspendedOrTerminated(metadata);
                })
                .toList();
    }

    /**
     * Returns whether the given group or community metadata indicates the
     * chat is suspended or terminated.
     *
     * <p>{@link ChatMetadata} is a sealed interface permitting
     * {@link GroupMetadata} and {@link CommunityMetadata}, so this method
     * uses pattern matching to reach the concrete accessors.
     *
     * @param metadata the chat metadata to inspect
     * @return {@code true} if the group or community is suspended or
     *         terminated
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
