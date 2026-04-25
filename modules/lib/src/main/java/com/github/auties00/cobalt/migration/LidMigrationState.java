package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * Tracks the progress of the LID (Long ID) migration pipeline for a single
 * WhatsApp account.
 *
 * <p>LID migration replaces phone-number addressing with privacy-preserving
 * Long IDs for 1:1 chats. The pipeline starts when a paired client receives
 * the AB prop enabling migration, waits for the primary device to sync its
 * mapping tables, executes the migration over the stored chats, and finally
 * marks the account as fully migrated.
 *
 * <p>This state machine is the Cobalt equivalent of WhatsApp Web's
 * {@code LidThreadMigrationStatus} enum, with three additional Cobalt-only
 * states ({@link #NOT_STARTED}, {@link #FAILED}, {@link #DISABLED}) used
 * for initialisation and the configurable error model.
 *
 * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus.
 */
@ProtobufEnum
@WhatsAppWebModule(moduleName = "WAWebLid1X1ThreadAccountMigrations.flow")
public enum LidMigrationState {
    /**
     * Migration has not been initiated.
     * This is the initial state before any migration activity begins.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific initial state for state machine tracking
     */
    NOT_STARTED(0),

    /**
     * Waiting for the AB prop to enable LID migration.
     * The client checks server-sent feature flags to determine if migration is enabled.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus.WAITING_PROP
     */
    WAITING_PROP(1),

    /**
     * Waiting for LID mappings from the primary device.
     * The primary device sends mappings via LIDMigrationMappingSyncMessage.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus.WAITING_MAPPINGS
     */
    WAITING_MAPPINGS(2),

    /**
     * Mappings received and validated, ready to start migration.
     * The client has all necessary data to perform the migration.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus.READY
     */
    READY(3),

    /**
     * Migration is currently in progress.
     * Threads are being migrated from PN to LID addressing.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus.IN_PROGRESS
     */
    IN_PROGRESS(4),

    /**
     * Migration completed successfully.
     * All eligible threads have been migrated to LID addressing.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus.COMPLETE
     */
    COMPLETE(5),

    /**
     * Migration failed due to a critical error.
     * This may require session termination or re-pairing.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific error state for configurable error handling
     */
    FAILED(6),

    /**
     * Migration is disabled and will not proceed.
     *
     * @implNote NO_WA_BASIS — Cobalt-specific disabled state for configurable error handling
     */
    DISABLED(7);

    /**
     * The protobuf index value for this migration state.
     *
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus
     */
    final int index;

    /**
     * Creates a new LID migration state with the specified protobuf index.
     *
     * @param index the protobuf index value
     * @implNote WAWebLid1X1ThreadAccountMigrations.flow.LidThreadMigrationStatus
     */
    LidMigrationState(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    /**
     * Returns whether this state indicates migration has finished, whether
     * successfully, with failure, or by being disabled.
     *
     * @return {@code true} if migration is in a terminal state
     * @implNote ADAPTED: WAWebLid1X1ThreadAccountMigrations.flow — convenience for Cobalt state machine
     */
    public boolean isTerminal() {
        return this == DISABLED || this == COMPLETE || this == FAILED;
    }
}
