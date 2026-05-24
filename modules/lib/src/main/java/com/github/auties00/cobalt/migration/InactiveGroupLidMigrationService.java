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
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.util.SchedulerUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background service that re-queries inactive group metadata so that groups
 * still on phone-number addressing flip to LID addressing.
 *
 * <p>WhatsApp's group-fanout flow flips a group from PN to LID addressing as
 * soon as a member sends a message, but inactive groups stay on PN
 * indefinitely until someone explicitly asks the server for fresh metadata.
 * This service walks the local chat store, picks every group still on PN,
 * issues a metadata query for each one, and re-checks once all queries land.
 * Once no PN-mode groups remain, the migration is marked complete for the
 * session.
 *
 * @apiNote
 * Started once per session by the {@link WhatsAppClient} pairing code. The
 * caller does not invoke the migration directly; it observes completion via
 * {@link #isInactiveGroupLidMigrationComplete()} when, for example, the
 * daily stats task wants to tag the session as {@code inactg}-complete (see
 * WA Web's {@code WAWebTasksDailyStatsTask}).
 *
 * @implNote
 * This implementation diverges from
 * {@code WAWebInactiveGroupLidMigrationJob.migrateInactiveGroupsToLid} in
 * three ways. First, queries are issued one-by-one through
 * {@link WhatsAppClient#queryChatMetadata} instead of through WA Web's
 * batched {@code WAWebQueryAndUpdateGroupMetadataJob.queryAndUpdateAllGroupMetadata},
 * because the per-chat query is the only public Cobalt API. Second, the
 * group membership check is satisfied by the per-chat metadata gate (a
 * group whose metadata is missing is excluded) rather than by a separate
 * {@code bulkCheckMyMembership} pass. Third, the failure handling does not
 * call {@code sendLogs}; an in-flight {@link Exception} from
 * {@link WhatsAppClient#queryChatMetadata} is logged and the next group is
 * tried, matching WA Web's outer try-catch but without the WAM telemetry
 * fan-out.
 */
@WhatsAppWebModule(moduleName = "WAWebInactiveGroupLidMigrationJob")
@WhatsAppWebModule(moduleName = "WAWebInactiveGroupLidMigration")
public final class InactiveGroupLidMigrationService {
    /**
     * Logger used by {@link #run()} and {@link #start()} to trace migration
     * progress and individual query failures.
     */
    private static final System.Logger LOGGER = System.getLogger(InactiveGroupLidMigrationService.class.getName());

    /**
     * Delay before the first migration pass after {@link #start()}.
     *
     * @apiNote
     * Matches the {@code MINUTE_SECONDS} pacing the WA Web task scheduler
     * imposes inside {@code WAWebTasksDefinitions} so the first sweep does
     * not race the initial offline-resume burst.
     */
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(60);

    /**
     * Delay before a retry pass when some groups remain on PN after the
     * initial sweep.
     *
     * @apiNote
     * Matches the daily cadence WA Web uses for the retry attempt; the same
     * 24-hour budget is used by the daily stats task that consumes the
     * completion flag.
     */
    private static final Duration RETRY_DELAY = Duration.ofDays(1);

    /**
     * Client used to look up the local chat store and to issue the per-group
     * {@code queryChatMetadata} IQs.
     */
    private final WhatsAppClient client;

    /**
     * Service used to read the AB-prop snapshot.
     *
     * @implNote
     * This implementation keeps the {@link ABPropsService} as a field for
     * symmetry with the other migration services and to support a future
     * AB-prop gate; the current code path does not read any AB prop because
     * WA Web removed its gating prop ({@code enable_inactive_group_lid_migration})
     * from the live bundle.
     */
    private final ABPropsService abPropsService;

    /**
     * Latched completion flag mirroring WA Web's
     * {@code UserPrefs.InactiveGroupLidMigrationComplete}.
     *
     * @implNote
     * Held in memory only; the migration runs once per session because the
     * Cobalt store is not yet wired to persist UserPrefs across restarts.
     */
    // TODO: persist the completion flag across sessions so a restarted
    //       client does not re-run the migration on every boot. WA Web
    //       stores the bool in IndexedDB under
    //       UserPrefs.InactiveGroupLidMigrationComplete.
    private final AtomicBoolean complete;

    /**
     * The currently scheduled migration task, or {@code null} when no task
     * is pending.
     *
     * @apiNote
     * Held so {@link #reset()} can cancel a pending retry when the client
     * disconnects.
     */
    private volatile CompletableFuture<Void> scheduledTask;

    /**
     * Constructs a new service bound to the given client and AB-props
     * service.
     *
     * @apiNote
     * Constructed once per {@link WhatsAppClient}; see the class-level
     * documentation for the lifecycle.
     *
     * @param client         the client used to query group metadata
     * @param abPropsService the AB-props service held for future gating
     * @throws NullPointerException if either argument is {@code null}
     */
    public InactiveGroupLidMigrationService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = Objects.requireNonNull(client, "client");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
        this.complete = new AtomicBoolean(false);
    }

    /**
     * Schedules the first migration pass after {@link #INITIAL_DELAY}.
     *
     * @apiNote
     * Invoked once per session by the pairing flow; subsequent invocations
     * after the migration has been marked complete (via
     * {@link #isInactiveGroupLidMigrationComplete()}) are no-ops. The work
     * runs on a virtual thread spawned by
     * {@link SchedulerUtils#scheduleDelayed(Duration, Runnable)}.
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
     * @apiNote
     * Invoked when the client disconnects so a queued retry does not fire
     * after the socket has been torn down.
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
     * @apiNote
     * Consumers (for example the daily stats task) tag the session as
     * {@code inactg}-complete when this returns {@code true}, matching the
     * WA Web {@code "inactg"} marker emitted by
     * {@code WAWebTasksDailyStatsTask}.
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
     *
     * @apiNote
     * Called by {@link #run()} when no PN groups remain and the migration
     * has nothing more to do; not part of the public surface.
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigration",
            exports = "setInactiveGroupLidMigrationComplete",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void setInactiveGroupLidMigrationComplete() {
        complete.set(true);
    }

    /**
     * Executes a single migration pass over the local chat store.
     *
     * @apiNote
     * Package-private so the test suite can exercise the body directly,
     * bypassing the {@link #INITIAL_DELAY} scheduler; production callers go
     * through {@link #start()}.
     *
     * @implNote
     * This implementation drops the AB-prop check WA Web's predecessor used
     * because the {@code enable_inactive_group_lid_migration} prop has been
     * retired in the live bundle; only the completion-state guard remains.
     * Exceptions from individual {@link WhatsAppClient#queryChatMetadata}
     * calls are caught and logged so a single failure does not abort the
     * whole sweep, and the outer catch swallows everything (matching WA
     * Web's outer try-catch minus the WAM logging).
     */
    @WhatsAppWebExport(moduleName = "WAWebInactiveGroupLidMigrationJob",
            exports = "migrateInactiveGroupsToLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    void run() {
        try {
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
     * Returns every locally cached group that has not yet flipped to LID
     * addressing.
     *
     * @apiNote
     * Walks the chat store filtering on
     * {@link JidServer#groupOrCommunity()}, then drops groups whose cached
     * metadata is missing, already on LID, or marked
     * suspended/terminated. The returned list is the input to the per-group
     * metadata refresh loop in {@link #run()}.
     *
     * @implNote
     * This implementation collapses WA Web's {@code bulkCheckMyMembership}
     * filter into the "metadata is non-{@code null}" gate; in Cobalt a
     * group's cached metadata implies membership.
     *
     * @return the JIDs of the groups still on phone-number addressing
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
     * Returns whether the given group or community metadata is suspended or
     * terminated.
     *
     * @apiNote
     * Used by {@link #findPnGroups()} to skip groups the server has flagged
     * as inactive; suspended or terminated groups will not flip to LID even
     * if their metadata is refreshed.
     *
     * @implNote
     * This implementation uses sealed-interface pattern matching against
     * {@link GroupMetadata} and {@link CommunityMetadata}, the only two
     * permitted subtypes of {@link ChatMetadata} that carry
     * suspended/terminated state.
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
