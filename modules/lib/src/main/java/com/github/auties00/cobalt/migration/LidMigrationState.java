package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * Position of a single account inside the 1:1 LID migration pipeline.
 *
 * @apiNote
 * Drives the {@link LidMigrationService} state machine: the pipeline starts
 * when a paired client receives the {@code lid_one_on_one_migration_enabled}
 * AB prop, waits for the primary device to deliver its mapping tables,
 * runs the rewrite over the local chat store, and finally records the
 * account as fully migrated. Indices 1 to 5 round-trip 1:1 with WA Web's
 * {@code LidThreadMigrationStatus} enum exported from
 * {@code WAWebLid1X1ThreadAccountMigrations.flow}; indices 0, 6, and 7
 * ({@link #NOT_STARTED}, {@link #FAILED}, {@link #DISABLED}) are
 * Cobalt-only because Cobalt drives the migration through an explicit
 * state machine rather than the ad-hoc UserPrefs flags WA Web reads in
 * {@code G()} and {@code V(t)}.
 *
 * @implNote
 * This implementation keeps the underlying protobuf index of the shared
 * values identical to WA Web's so persisted state from a JS export could
 * be read back by Cobalt without re-mapping; the Cobalt-only values are
 * appended at the end so they never collide with future additions on the
 * JS side.
 */
@ProtobufEnum
@WhatsAppWebModule(moduleName = "WAWebLid1X1ThreadAccountMigrations.flow")
public enum LidMigrationState {
    /**
     * Initial state before any migration activity has begun.
     *
     * @apiNote
     * Cobalt-only; WA Web's pipeline implicitly enters
     * {@link #WAITING_PROP} on first read because the JS code initialises
     * the {@code WALidThreadAccountMigrationStatus} UserPrefs entry to
     * {@code WAITING_PROP} on miss. Cobalt models the pre-init phase
     * explicitly so the state-machine transitions are observable in tests.
     */
    NOT_STARTED(0),

    /**
     * Waiting for the {@code lid_one_on_one_migration_enabled} AB prop to
     * flip on.
     *
     * @apiNote
     * Initial state once {@link LidMigrationService} is wired in; the AB
     * prop check inside WA Web's {@code checkIfMigrationEnabled} flips
     * this state to {@link #WAITING_MAPPINGS}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations.flow",
            exports = "LidThreadMigrationStatus", adaptation = WhatsAppAdaptation.DIRECT)
    WAITING_PROP(1),

    /**
     * Waiting for the primary device to send the LID mapping sync message.
     *
     * @apiNote
     * Reached after the AB-prop gate fires; mappings arrive through the
     * peer-message channel WA Web models in
     * {@code setLidMigrationMappings}. Transition out is handled by
     * {@link LidMigrationService} on receipt of the mappings payload.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations.flow",
            exports = "LidThreadMigrationStatus", adaptation = WhatsAppAdaptation.DIRECT)
    WAITING_MAPPINGS(2),

    /**
     * Mappings have been received and validated; the rewrite is ready to
     * run.
     *
     * @apiNote
     * Set by {@link LidMigrationService} after the peer-mapping payload has
     * been cached and the pre-migration sanity checks have passed; matches
     * the {@code shouldMigrateNow} predicate on the JS side.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations.flow",
            exports = "LidThreadMigrationStatus", adaptation = WhatsAppAdaptation.DIRECT)
    READY(3),

    /**
     * The rewrite is currently moving threads from PN to LID addressing.
     *
     * @apiNote
     * Set immediately before the per-chat resolution loop in
     * {@link LidMigrationService} starts producing
     * {@link LidMigrationResolution} values; ensures concurrent transitions
     * cannot reorder the sweep.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations.flow",
            exports = "LidThreadMigrationStatus", adaptation = WhatsAppAdaptation.DIRECT)
    IN_PROGRESS(4),

    /**
     * The rewrite finished successfully and every eligible thread has been
     * migrated.
     *
     * @apiNote
     * Terminal; reported by {@link #isTerminal()} as {@code true}. Mirrors
     * the {@code COMPLETE} value WA Web sets after
     * {@code bulkCreateOrMerge}, {@code bulkRemove}, and
     * {@code Lid1X1MigrationUtils.setIsLidMigrated(true, ...)} have all
     * succeeded.
     */
    @WhatsAppWebExport(moduleName = "WAWebLid1X1ThreadAccountMigrations.flow",
            exports = "LidThreadMigrationStatus", adaptation = WhatsAppAdaptation.DIRECT)
    COMPLETE(5),

    /**
     * The rewrite aborted because of a fatal error surfaced through the
     * configurable error handler.
     *
     * @apiNote
     * Cobalt-only; WA Web responds to fatal failures by calling
     * {@code socketLogout} with one of several {@code LogoutReason}
     * values. Cobalt routes the same failure through the
     * {@code WhatsAppClientErrorHandler} and parks the state here so the
     * embedder can decide whether to retry, log out, or ignore.
     *
     * @implNote
     * This implementation is the terminal sink for every fatal exception
     * thrown out of {@link LidMigrationService}; the configurable error
     * handler's verdict ({@code LOG_OUT}, {@code RECONNECT}, etc.) is
     * applied separately and does not change the {@link LidMigrationState}.
     */
    FAILED(6),

    /**
     * Migration is disabled and will not run for this session.
     *
     * @apiNote
     * Cobalt-only terminal state. Set by {@link LidMigrationService} when
     * the embedder explicitly opts out, or when the AB-prop gate is
     * checked and reports the migration as not enabled for this account.
     */
    DISABLED(7);

    /**
     * Protobuf wire index assigned to this state.
     *
     * @apiNote
     * Package-private so the {@link LidMigrationService} persistence layer
     * can read it without going through reflection.
     */
    final int index;

    /**
     * Constructs a new state with the given protobuf index.
     *
     * @apiNote
     * Called by the JVM during enum initialisation; not part of the public
     * surface.
     *
     * @param index the protobuf wire index
     */
    LidMigrationState(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    /**
     * Returns whether this state is terminal.
     *
     * @apiNote
     * Used by {@link LidMigrationService} to short-circuit further
     * transitions once the pipeline has settled; consumers can also call
     * it to gate UI affordances such as "retry migration".
     *
     * @return {@code true} if this state is {@link #COMPLETE},
     *         {@link #FAILED}, or {@link #DISABLED}
     */
    public boolean isTerminal() {
        return this == DISABLED || this == COMPLETE || this == FAILED;
    }
}
