package com.github.auties00.cobalt.exception;

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
 * <ul>
 *   <li>{@link SplitThreadMismatch} - Split thread state differs between local and primary device</li>
 *   <li>{@link PrimaryMappingsObsolete} - Primary device's mappings are outdated</li>
 *   <li>{@link FailedToParseMappings} - Migration mapping data could not be parsed</li>
 *   <li>{@link NoLidAvailable} - A non-deletable chat has no LID mapping</li>
 *   <li>{@link IncompatibleClient} - The companion client is not compatible with LID migration</li>
 * </ul>
 *
 * <h2>Fatality</h2>
 * All LID migration errors are fatal as they indicate the client cannot correctly
 * identify contacts and groups, which would lead to data corruption or message misrouting.
 *
 * @see SplitThreadMismatch
 * @see PrimaryMappingsObsolete
 * @see FailedToParseMappings
 * @see NoLidAvailable
 * @see IncompatibleClient
 */
public sealed abstract class WhatsAppLidMigrationException
        extends WhatsAppException
        permits WhatsAppLidMigrationException.SplitThreadMismatch,
                WhatsAppLidMigrationException.PrimaryMappingsObsolete,
                WhatsAppLidMigrationException.FailedToParseMappings,
                WhatsAppLidMigrationException.NoLidAvailable,
                WhatsAppLidMigrationException.IncompatibleClient {

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
         */
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
         */
        public PrimaryMappingsObsolete() {
            super("Primary device mappings are obsolete");
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
         */
        public FailedToParseMappings(String message) {
            super("Failed to parse migration mappings (" + message + ")");
        }

        /**
         * Constructs a new failed to parse mappings exception with a detail message and cause.
         *
         * @param message additional context about the parsing failure
         * @param reason  the underlying parsing exception
         */
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
     * {@code LogoutReason.LidMigrationNoLidAvailable} logout reason, which aborts
     * the entire migration process.
     */
    public static final class NoLidAvailable extends WhatsAppLidMigrationException {
        /**
         * Constructs a new no LID available exception.
         */
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
         */
        public IncompatibleClient() {
            super("Companion client is not compatible with LID migration (killswitch)");
        }
    }
}
