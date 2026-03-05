package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.model.sync.SyncActionMessage;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;

import java.time.Instant;
import java.util.HashSet;

/**
 * Utilities for comparing and merging {@link SyncActionMessageRange} objects
 * during conflict resolution.
 *
 * <p>Per WhatsApp Web {@code WAWebMessageRangeUtils}: message ranges describe
 * the set of messages an action applies to. When a local and remote mutation
 * conflict on the same index, the range comparison determines which mutation
 * covers a broader scope of messages.
 */
final class MessageRangeUtils {
    /**
     * The result of comparing two message ranges.
     */
    enum EnclosureType {
        /**
         * Range A fully encloses range B (A covers all messages that B covers).
         */
        RANGE_A_ENCLOSES_RANGE_B,

        /**
         * Range B fully encloses range A (B covers all messages that A covers).
         */
        RANGE_B_ENCLOSES_RANGE_A,

        /**
         * Both ranges cover exactly the same set of messages.
         */
        RANGES_ARE_EQUAL,

        /**
         * Neither range fully encloses the other (partial overlap or disjoint).
         */
        RANGES_NOT_ENCLOSING
    }

    private MessageRangeUtils() {
    }

    /**
     * Compares two message ranges to determine their enclosure relationship.
     *
     * <p>Per WhatsApp Web {@code compareMessageRanges}: checks in both
     * directions whether one range encloses the other.
     *
     * @param rangeA the first range (typically remote)
     * @param rangeB the second range (typically local)
     * @return the enclosure relationship between the two ranges
     */
    static EnclosureType compareMessageRanges(SyncActionMessageRange rangeA, SyncActionMessageRange rangeB) {
        var aEnclosesB = encloses(rangeA, rangeB);
        var bEnclosesA = encloses(rangeB, rangeA);
        if (aEnclosesB && bEnclosesA) {
            return EnclosureType.RANGES_ARE_EQUAL;
        } else if (aEnclosesB) {
            return EnclosureType.RANGE_A_ENCLOSES_RANGE_B;
        } else if (bEnclosesA) {
            return EnclosureType.RANGE_B_ENCLOSES_RANGE_A;
        } else {
            return EnclosureType.RANGES_NOT_ENCLOSING;
        }
    }

    /**
     * Checks whether range {@code encloser} encloses range {@code enclosed}.
     *
     * <p>Per WhatsApp Web: a range A encloses range B when every message in B
     * is accounted for by A. Specifically, for each message in B:
     * <ul>
     *   <li>If the message has no timestamp, its key ID must be present in A's
     *       messages list
     *   <li>If the message's key ID is NOT found in A's messages, its timestamp
     *       must be {@code <=} A's {@code lastMessageTimestamp}
     * </ul>
     *
     * @param encloser the range that should enclose
     * @param enclosed the range that should be enclosed
     * @return {@code true} if {@code encloser} encloses {@code enclosed}
     */
    private static boolean encloses(SyncActionMessageRange encloser, SyncActionMessageRange enclosed) {
        var encloserLastTimestamp = encloser.lastMessageTimestamp()
                .orElse(Instant.EPOCH);

        // Build a set of message key IDs in the encloser for fast lookup
        var encloserKeyIds = new HashSet<String>();
        for (var msg : encloser.messages()) {
            msg.key()
                    .flatMap(key -> key.id())
                    .ifPresent(encloserKeyIds::add);
        }

        // Check every message in the enclosed range
        for (var msg : enclosed.messages()) {
            var keyId = msg.key()
                    .flatMap(key -> key.id())
                    .orElse(null);
            var msgTimestamp = msg.timestamp().orElse(null);

            if (keyId != null && encloserKeyIds.contains(keyId)) {
                // Message explicitly present in encloser's list
                continue;
            }

            if (msgTimestamp == null) {
                // No timestamp and not in encloser's explicit list
                return false;
            }

            // Message not in explicit list: its timestamp must be <= encloser's last
            if (msgTimestamp.compareTo(encloserLastTimestamp) > 0) {
                return false;
            }
        }

        return true;
    }
}
