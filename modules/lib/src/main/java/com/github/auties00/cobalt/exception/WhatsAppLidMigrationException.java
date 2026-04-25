package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Exception thrown when LID (Lid Identity) migration encounters a critical error.
 * <p>
 * LID migration is WhatsApp's process for transitioning from phone number-based identifiers
 * to Lid-based identifiers for improved privacy. This migration affects how contacts and
 * groups are identified across the platform.
 *
 * <h2>LID Migration Architecture</h2>
 * The migration involves:
 * <ul>
 *   <li><b>Mappings:</b> Translation tables between old phone-based IDs and new LIDs</li>
 *   <li><b>Split thread:</b> Mechanism to handle conversations during the migration period</li>
 *   <li><b>Primary sync:</b> Synchronization of mappings from the primary device</li>
 * </ul>
 *
 * <h2>Exception Hierarchy</h2>
 * Each nested type maps to a distinct {@code LogoutReason.LidMigration*} /
 * {@code LogoutReason.LidBlocklist*} value in
 * {@code WAWebLogoutReasonConstants}. WA Web triggers {@code socketLogout} with
 * one of these reasons; Cobalt throws the corresponding exception subtype and
 * routes recovery through the configurable {@code WhatsAppClientErrorHandler}.
 * <ul>
 *   <li>{@link SplitThreadMismatch} &mdash; {@code LidMigrationSplitThreadMismatch}</li>
 *   <li>{@link PrimaryMappingsObsolete} &mdash; {@code LidMigrationPrimaryMappingsObsolete}</li>
 *   <li>{@link PeerMappingsNotReceived} &mdash; {@code LidMigrationPeerMappingsNotReceived}</li>
 *   <li>{@link StateDiscrepancy} &mdash; {@code LidMigrationStateDiscrepancy}</li>
 *   <li>{@link PeerMappingsMalformed} &mdash; {@code LidMigrationPeerMappingsMalformed}</li>
 *   <li>{@link FailedToParseMappings} &mdash; {@code LidMigrationFailedToParseMapping}</li>
 *   <li>{@link NoLidAvailable} &mdash; {@code LidMigrationNoLidAvailiable} (WA Web typo preserved in the string value {@code "lid_migration_no_lid_available"})</li>
 *   <li>{@link IncompatibleClient} &mdash; {@code LidMigrationCompanionIncompatibleKillswitch}</li>
 *   <li>{@link OneOnOneThreadMigrationInternalError} &mdash; {@code LidMigrationOneOnOneThreadMigrationInternalError}</li>
 *   <li>{@link BlocklistPnWhenMigrated} &mdash; {@code LidBlocklistPnWhenMigrated}</li>
 *   <li>{@link BlocklistChatDbUnmigrated} &mdash; {@code LidBlocklistChatDbUnmigrated}</li>
 * </ul>
 *
 * <h2>Fatality</h2>
 * All LID migration errors are fatal as they indicate the client cannot correctly
 * identify contacts and groups, which would lead to data corruption or message misrouting.
 * This matches {@code WAWebLogoutReason.getErrorCodeFromLogoutReason}, which classifies
 * every {@code LidMigration*} and {@code LidBlocklist*} value as a {@code null} error code
 * (that is, no {@code LOGOUT_REASON_CODE} bucket) because they each trigger a full session
 * teardown rather than a recoverable fault.
 *
 * @implNote WA Web models logout reasons as a flat string enum in
 *           {@code WAWebLogoutReasonConstants.LogoutReason}. Cobalt's error model
 *           intentionally replaces the enum with a sealed exception hierarchy so
 *           recovery is driven by {@code WhatsAppClientErrorHandler} rather than
 *           hardcoded inline {@code socketLogout} calls (see CLAUDE.md / Error Model).
 *           Only LID-migration reasons are modelled in this file; non-LID reasons
 *           map to other members of the {@code WhatsAppException} family
 *           (for example {@code LogoutReason.AccountLocked} maps to
 *           {@link WhatsAppSessionException.LoggedOut} inside
 *           {@code FailureStreamHandler}).
 *
 * @see SplitThreadMismatch
 * @see PrimaryMappingsObsolete
 * @see PeerMappingsNotReceived
 * @see StateDiscrepancy
 * @see PeerMappingsMalformed
 * @see FailedToParseMappings
 * @see NoLidAvailable
 * @see IncompatibleClient
 * @see OneOnOneThreadMigrationInternalError
 * @see BlocklistPnWhenMigrated
 * @see BlocklistChatDbUnmigrated
 */
@WhatsAppWebModule(moduleName = "WAWebLogoutReasonConstants")
public sealed abstract class WhatsAppLidMigrationException
        extends WhatsAppException
        permits WhatsAppLidMigrationException.SplitThreadMismatch,
                WhatsAppLidMigrationException.PrimaryMappingsObsolete,
                WhatsAppLidMigrationException.PeerMappingsNotReceived,
                WhatsAppLidMigrationException.StateDiscrepancy,
                WhatsAppLidMigrationException.PeerMappingsMalformed,
                WhatsAppLidMigrationException.FailedToParseMappings,
                WhatsAppLidMigrationException.NoLidAvailable,
                WhatsAppLidMigrationException.IncompatibleClient,
                WhatsAppLidMigrationException.OneOnOneThreadMigrationInternalError,
                WhatsAppLidMigrationException.BlocklistPnWhenMigrated,
                WhatsAppLidMigrationException.BlocklistChatDbUnmigrated {

    /**
     * Constructs a new LID migration exception with the specified detail message.
     *
     * @param message the detail message describing the migration failure
     */
    protected WhatsAppLidMigrationException(String message) {
        super("LID migration failed: " + message);
    }

    /**
     * Constructs a new LID migration exception with a detail message and cause.
     *
     * @param message the detail message describing the migration failure
     * @param reason  the underlying cause of the migration failure
     */
    protected WhatsAppLidMigrationException(String message, Throwable reason) {
        super("LID migration failed: " + message, reason);
    }

    /**
     * Returns whether this exception represents a fatal error.
     * <p>
     * All LID migration errors are fatal as they indicate fundamental issues with
     * contact and group identification that cannot be safely ignored.
     *
     * @return {@code true}
     */
    @Override
    public boolean isFatal() {
        return true;
    }

    /**
     * Exception thrown when there is a split thread mismatch between local and primary device.
     * <p>
     * During LID migration, conversations may be "split" into pre-migration and post-migration
     * threads. This exception occurs when the local device's split thread state doesn't match
     * the primary device's state, indicating a synchronization failure.
     *
     * <h2>Implications</h2>
     * <ul>
     *   <li>Messages may be routed to the wrong conversation</li>
     *   <li>Chat history may appear fragmented</li>
     *   <li>The client cannot safely proceed without risking data loss</li>
     * </ul>
     */
    public static final class SplitThreadMismatch extends WhatsAppLidMigrationException {
        /**
         * Constructs a new split thread mismatch exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationSplitThreadMismatch)}
         *           (string value {@code "lid_migration_split_thread_mismatch"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public SplitThreadMismatch() {
            super("Split thread mismatch between local and primary device");
        }
    }

    /**
     * Exception thrown when the primary device's LID mappings are obsolete.
     * <p>
     * Companion devices synchronize their LID mappings from the primary device.
     * This exception occurs when the primary device's mappings are outdated or
     * inconsistent with the current state of the account.
     *
     * <h2>Possible Causes</h2>
     * <ul>
     *   <li>Primary device has been offline for too long</li>
     *   <li>Primary device was restored from an old backup</li>
     *   <li>Migration state became corrupted on the primary device</li>
     * </ul>
     */
    public static final class PrimaryMappingsObsolete extends WhatsAppLidMigrationException {
        /**
         * Constructs a new primary mappings obsolete exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationPrimaryMappingsObsolete)}
         *           (string value {@code "lid_migration_primary_mappings_obsolete"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public PrimaryMappingsObsolete() {
            super("Primary device mappings are obsolete");
        }
    }

    /**
     * Exception thrown when peer LID mappings are not received within the migration timeout.
     *
     * <p>During companion-device LID migration the primary device pushes peer
     * mapping records. If the timeout fires before any mappings arrive, WA Web
     * schedules a forced logout so the companion can be re-bootstrapped from
     * scratch.
     *
     * @implNote This corresponds to the
     *           {@code WAWebLid1x1MigrationTimeout.scheduleLogoutIfNeeded} path
     *           that calls
     *           {@code socketLogout(LogoutReason.LidMigrationPeerMappingsNotReceived)}.
     */
    public static final class PeerMappingsNotReceived extends WhatsAppLidMigrationException {
        /**
         * Constructs a new peer mappings not received exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationPeerMappingsNotReceived)}
         *           (string value {@code "lid_migration_peer_mapping_not_received"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public PeerMappingsNotReceived() {
            super("Peer LID mappings were not received before the migration timeout");
        }
    }

    /**
     * Exception thrown when the LID migration state on this device does not match
     * the primary device's state.
     *
     * <p>WA Web detects state drift via
     * {@code WAWebLid1x1MigrationTimeoutUtils.hasStateDiscrepancy()} in
     * {@code WAWebQueryBlockListJob} and by explicit checks in
     * {@code WAWebLid1x1MigrationTimeout}; both trigger
     * {@code socketLogout(LogoutReason.LidMigrationStateDiscrepancy)} because the
     * mismatch cannot be reconciled without a fresh companion bootstrap.
     */
    public static final class StateDiscrepancy extends WhatsAppLidMigrationException {
        /**
         * Constructs a new state discrepancy exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationStateDiscrepancy)}
         *           (string value {@code "lid_migration_state_discrepancy"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public StateDiscrepancy() {
            super("LID migration state does not match primary device");
        }
    }

    /**
     * Exception thrown when peer LID mapping data is malformed and cannot be applied.
     *
     * <p>Unlike {@link FailedToParseMappings}, which signals a hard parse error,
     * this is raised when decoded mappings fail subsequent structural validation
     * (for example, empty mapping sets in
     * {@code WAWebLid1X1ThreadAccountMigrations}). WA Web logs
     * {@code "lid-migration-empty-mappings"} and calls
     * {@code socketLogout(LogoutReason.LidMigrationPeerMappingsMalformed)}.
     */
    public static final class PeerMappingsMalformed extends WhatsAppLidMigrationException {
        /**
         * Constructs a new peer mappings malformed exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationPeerMappingsMalformed)}
         *           (string value {@code "lid_migration_peer_mapping_malformed"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public PeerMappingsMalformed() {
            super("Peer LID mappings are malformed or empty");
        }

        /**
         * Constructs a new peer mappings malformed exception with additional context.
         *
         * @param message additional context about why the mappings are malformed
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationPeerMappingsMalformed)}
         *           (string value {@code "lid_migration_peer_mapping_malformed"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public PeerMappingsMalformed(String message) {
            super("Peer LID mappings are malformed or empty (" + message + ")");
        }
    }

    /**
     * Exception thrown when migration mapping data could not be parsed.
     * <p>
     * The LID migration process involves serialized mapping data that translates
     * between old and new identifier formats. This exception occurs when this
     * data is malformed or corrupted.
     *
     * <h2>Possible Causes</h2>
     * <ul>
     *   <li>Data corruption during transmission</li>
     *   <li>Protocol version mismatch</li>
     *   <li>Invalid protobuf encoding</li>
     *   <li>Missing required fields in the mapping data</li>
     * </ul>
     */
    public static final class FailedToParseMappings extends WhatsAppLidMigrationException {
        /**
         * Constructs a new failed to parse mappings exception with a detail message.
         *
         * @param message additional context about the parsing failure
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationFailedToParseMapping)}
         *           (string value {@code "lid_migration_failed_to_parse_mapping"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public FailedToParseMappings(String message) {
            super("Failed to parse migration mappings (" + message + ")");
        }

        /**
         * Constructs a new failed to parse mappings exception with a detail message and cause.
         *
         * @param message additional context about the parsing failure
         * @param reason  the underlying parsing exception
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationFailedToParseMapping)}
         *           (string value {@code "lid_migration_failed_to_parse_mapping"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public FailedToParseMappings(String message, Throwable reason) {
            super("Failed to parse migration mappings (" + message + ")", reason);
        }
    }

    /**
     * Exception thrown when a non-deletable chat has no LID mapping available.
     *
     * <p>During LID migration, every chat that cannot be deleted (because it has
     * user content, is archived, muted, or locked) must have a LID mapping to
     * migrate to. This exception occurs when no such mapping exists, which means
     * the migration cannot proceed safely without data loss.
     *
     * <p>This corresponds to WhatsApp Web's
     * {@code LogoutReason.LidMigrationNoLidAvailiable} logout reason (WA Web
     * typo preserved in the key; the string value is
     * {@code "lid_migration_no_lid_available"}), which aborts the entire migration
     * process.
     */
    public static final class NoLidAvailable extends WhatsAppLidMigrationException {
        /**
         * Constructs a new no LID available exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationNoLidAvailiable)}
         *           (string value {@code "lid_migration_no_lid_available"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public NoLidAvailable() {
            super("Non-deletable chat has no LID mapping available");
        }
    }

    /**
     * Exception thrown when the companion client is not compatible with LID migration.
     *
     * <p>The {@code LID_ONE_ON_ONE_MIGRATION_COMPATIBLE} AB prop controls whether
     * the companion device is allowed to perform the migration. When set to
     * {@code false}, the migration must not proceed.
     *
     * <p>This corresponds to WhatsApp Web's
     * {@code LogoutReason.LidMigrationCompanionIncompatibleKillswitch} logout reason.
     */
    public static final class IncompatibleClient extends WhatsAppLidMigrationException {
        /**
         * Constructs a new incompatible client exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationCompanionIncompatibleKillswitch)}
         *           (string value {@code "lid_migration_companion_incompatible_killswitch"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public IncompatibleClient() {
            super("Companion client is not compatible with LID migration (killswitch)");
        }
    }

    /**
     * Exception thrown when one-on-one thread migration fails with an internal error.
     *
     * <p>WA Web's {@code WAWebLid1X1ThreadAccountMigrations} runs the actual
     * thread migration in a try/catch; on any unexpected failure it sends
     * {@code "lid-thread-migration"} logs and calls
     * {@code socketLogout(LogoutReason.LidMigrationOneOnOneThreadMigrationInternalError)}.
     * In Cobalt the migration runner rethrows this exception, letting the
     * configurable error handler drive disconnect or log-out as appropriate.
     */
    public static final class OneOnOneThreadMigrationInternalError extends WhatsAppLidMigrationException {
        /**
         * Constructs a new one-on-one thread migration internal error.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationOneOnOneThreadMigrationInternalError)}
         *           (string value {@code "lid_migration_one_on_one_thread_migration_internal_error"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public OneOnOneThreadMigrationInternalError() {
            super("One-on-one thread migration failed with internal error");
        }

        /**
         * Constructs a new one-on-one thread migration internal error with a cause.
         *
         * @param reason the underlying cause of the migration failure
         * @implNote Replaces {@code socketLogout(LogoutReason.LidMigrationOneOnOneThreadMigrationInternalError)}
         *           (string value {@code "lid_migration_one_on_one_thread_migration_internal_error"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public OneOnOneThreadMigrationInternalError(Throwable reason) {
            super("One-on-one thread migration failed with internal error", reason);
        }
    }

    /**
     * Exception thrown when a phone-number JID is present in the blocklist after the
     * account has already been migrated to LID.
     *
     * <p>After a successful LID migration the blocklist must contain only LID
     * identifiers. Encountering a legacy phone-number JID indicates that the
     * blocklist was not correctly migrated and the client cannot trust its
     * local state to enforce blocking correctly. WA Web forces a logout via
     * {@code socketLogout(LogoutReason.LidBlocklistPnWhenMigrated)}.
     */
    public static final class BlocklistPnWhenMigrated extends WhatsAppLidMigrationException {
        /**
         * Constructs a new blocklist phone-number-when-migrated exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidBlocklistPnWhenMigrated)}
         *           (string value {@code "lid_blocklist_pn_when_migrated"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public BlocklistPnWhenMigrated() {
            super("Blocklist contains a phone-number JID after LID migration");
        }
    }

    /**
     * Exception thrown when the chat database is unmigrated while the blocklist
     * requests LID-aware enforcement.
     *
     * <p>WA Web's {@code WAWebQueryBlockListJob.fetchAndUpdateBlocklist} sleeps
     * briefly and then calls
     * {@code socketLogout(LogoutReason.LidBlocklistChatDbUnmigrated)} when it
     * detects that the chat database has not yet been migrated to LID even
     * though the blocklist expects it.
     */
    public static final class BlocklistChatDbUnmigrated extends WhatsAppLidMigrationException {
        /**
         * Constructs a new blocklist chat-db-unmigrated exception.
         *
         * @implNote Replaces {@code socketLogout(LogoutReason.LidBlocklistChatDbUnmigrated)}
         *           (string value {@code "lid_blocklist_chat_db_unmigrated"}).
         */
        @WhatsAppWebExport(
                moduleName = "WAWebLogoutReasonConstants",
                exports = "LogoutReason",
                adaptation = WhatsAppAdaptation.ADAPTED
        )
        public BlocklistChatDbUnmigrated() {
            super("Chat database is not migrated to LID but blocklist requires it");
        }
    }
}
