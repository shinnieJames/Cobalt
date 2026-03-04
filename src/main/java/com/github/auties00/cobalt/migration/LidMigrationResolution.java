package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Optional;

/**
 * Sealed interface representing the resolution of a thread's LID migration.
 * <p>
 * Each thread is analyzed during migration to determine the appropriate action:
 * <ul>
 *     <li>{@link Migrate} - Thread will be migrated to use LID addressing</li>
 *     <li>{@link Keep} - Thread will remain unchanged</li>
 *     <li>{@link Error} - Resolution failed due to an error</li>
 * </ul>
 */
public sealed interface LidMigrationResolution
        permits LidMigrationResolution.Migrate,
                LidMigrationResolution.Keep,
                LidMigrationResolution.Delete{
    /**
     * Returns the original JID of the thread.
     *
     * @return the original JID
     */
    Jid originalJid();

    /**
     * Resolution indicating the thread should be migrated to LID addressing.
     *
     * @param originalJid the original phone number JID
     * @param targetLid   the target LID to migrate to
     */
    record Migrate(Jid originalJid, Jid targetLid) implements LidMigrationResolution {
        /**
         * Returns the target LID for migration.
         *
         * @return the target LID
         */
        public Optional<Jid> lid() {
            return Optional.of(targetLid);
        }
    }

    /**
     * Resolution indicating the thread should be kept as-is.
     *
     * @param originalJid the original JID (may be PN or LID)
     * @param reason      the reason for keeping the thread unchanged
     */
    record Keep(Jid originalJid, KeepReason reason) implements LidMigrationResolution {

    }

    /**
     * Resolution indicating the thread should be deleted.
     *
     * @param originalJid the original phone number JID
     * @param reason      the reason for deletion
     */
    record Delete(Jid originalJid, DeleteReason reason) implements LidMigrationResolution {

    }

    /**
     * Reasons why a thread would be kept unchanged during migration.
     */
    enum KeepReason {
        /**
         * Thread is already using LID addressing.
         */
        ALREADY_LID,

        /**
         * Thread is a group or community (not subject to LID migration).
         */
        GROUP_OR_COMMUNITY,

        /**
         * Thread is a newsletter (not subject to LID migration).
         */
        NEWSLETTER,

        /**
         * Thread is a broadcast list (not subject to LID migration).
         */
        BROADCAST,

        /**
         * Thread is the status broadcast (not subject to LID migration).
         */
        STATUS_BROADCAST,

        /**
         * Thread belongs to a bot account.
         */
        BOT,

        /**
         * Thread has a duplicate LID thread that will be merged.
         */
        DUPLICATE_WILL_MERGE
    }

    /**
     * Reasons why a thread would be deleted during migration.
     */
    enum DeleteReason {
        /**
         * No LID mapping found in primary device's cache.
         */
        NO_LID_MAPPING,

        /**
         * Contact has not completed LID migration on their end.
         */
        CONTACT_NOT_MIGRATED,

        /**
         * Split thread detected - would result in duplicate after migration.
         */
        SPLIT_THREAD_MISMATCH
    }
}
