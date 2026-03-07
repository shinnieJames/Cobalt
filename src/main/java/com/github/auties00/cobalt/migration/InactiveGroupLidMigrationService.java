package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.client.WhatsAppClient;
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
 * A scheduled service that migrates inactive groups to LID addressing mode
 * by re-querying their metadata from the server.
 *
 * <p>When enabled via the {@link ABProp#ENABLE_INACTIVE_GROUP_LID_MIGRATION}
 * AB prop, this service identifies groups that still use phone-number
 * addressing mode ({@code isLidAddressingMode == false}), queries their
 * metadata from the server (which triggers an update of the addressing mode
 * flag), and marks the migration as complete once all groups have been
 * migrated.
 *
 * <p>The service runs once after a 60-second startup delay. If groups
 * remain in PN mode after the initial pass, a follow-up pass is scheduled
 * after 24 hours.
 *
 * @apiNote WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid:
 * finds all non-LID groups where the user is a member, batch-queries
 * their metadata, and marks the migration complete when none remain.
 */
public final class InactiveGroupLidMigrationService {
    private static final System.Logger LOGGER = System.getLogger(InactiveGroupLidMigrationService.class.getName());

    /**
     * The initial delay before the first migration attempt, matching the
     * WhatsApp Web requirement that at least 60 seconds have elapsed
     * since pairing.
     */
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(60);

    /**
     * The delay between retry attempts when groups remain in PN mode
     * after the first pass.
     *
     * @apiNote WAWebTasksDefinitions: returns {@code DAY_SECONDS} after
     * a migration attempt.
     */
    private static final Duration RETRY_DELAY = Duration.ofDays(1);

    private final WhatsAppClient client;
    private final ABPropsService abPropsService;
    private final AtomicBoolean complete;
    private volatile CompletableFuture<Void> scheduledTask;

    /**
     * Constructs a new inactive group LID migration service.
     *
     * @param client        the WhatsApp client for querying group metadata
     * @param abPropsService the AB props service for checking the feature flag
     */
    public InactiveGroupLidMigrationService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = Objects.requireNonNull(client, "client");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.complete = new AtomicBoolean(false);
    }

    /**
     * Starts the migration service by scheduling the first attempt after
     * the initial delay.
     *
     * <p>This method should be called once after the client has logged in
     * and AB props are available.
     */
    public void start() {
        if (complete.get()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "[lid-inactive-group-migration] already done, skip");
            return;
        }

        scheduledTask = SchedulerUtils.scheduleDelayed(INITIAL_DELAY, this::run);
    }

    /**
     * Cancels any pending scheduled migration task and resets the service
     * state. Called on disconnect/reconnect.
     */
    public void reset() {
        var task = scheduledTask;
        if (task != null) {
            task.cancel(true);
            scheduledTask = null;
        }
    }

    /**
     * Executes a single migration pass.
     *
     * <p>Checks the AB prop, finds PN-mode groups, queries their metadata,
     * and either marks migration complete or schedules a retry.
     *
     * @apiNote WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid
     */
    private void run() {
        try {
            if (!abPropsService.getBool(ABProp.ENABLE_INACTIVE_GROUP_LID_MIGRATION)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "[lid-inactive-group-migration] ABProp disabled, skip");
                return;
            }

            if (complete.get()) {
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
                complete.set(true);
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
                complete.set(true);
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
     * Finds all group chats that are still in phone-number addressing mode.
     *
     * <p>Filters to groups where the metadata exists and
     * {@code isLidAddressingMode} is {@code false}.
     *
     * @return the list of group JIDs that have not migrated to LID
     *
     * @apiNote WAWebInactiveGroupLidMigrationJob: filters
     * {@code getGroupMetadataTable().all()} by
     * {@code isLidAddressingMode !== true}, then checks membership
     * and excludes suspended/terminated groups.
     */
    private List<Jid> findPnGroups() {
        var store = client.store();
        return store.chats()
                .stream()
                .map(chat -> chat.jid())
                .filter(jid -> jid.hasServer(JidServer.groupOrCommunity()))
                .filter(jid -> {
                    var metadata = store.findChatMetadata(jid).orElse(null);
                    return metadata != null && !metadata.isLidAddressingMode();
                })
                .toList();
    }
}
