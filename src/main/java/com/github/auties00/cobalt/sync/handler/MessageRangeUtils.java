package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.model.sync.SyncActionMessage;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRangeBuilder;

import java.time.Instant;
import java.util.*;

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
     * Merges two message ranges into one that covers the union of both.
     *
     * <p>Per WhatsApp Web {@code mergeMessageRanges}:
     * <ul>
     *   <li>Takes the maximum of both {@code lastMessageTimestamp} values
     *   <li>Merges message lists: keeps only messages whose timestamp is
     *       {@code >=} the max timestamp; among duplicates (same key ID),
     *       keeps the one with the higher timestamp
     *   <li>Takes the maximum of both {@code lastSystemMessageTimestamp}
     *       values, but only sets it if it exceeds the merged
     *       {@code lastMessageTimestamp}
     * </ul>
     *
     * @param rangeA the first range (typically remote)
     * @param rangeB the second range (typically local)
     * @return a new merged range covering both inputs
     */
    static SyncActionMessageRange mergeMessageRanges(SyncActionMessageRange rangeA, SyncActionMessageRange rangeB) {
        var aLastTimestamp = toEpochSeconds(rangeA.lastMessageTimestamp().orElse(null));
        var bLastTimestamp = toEpochSeconds(rangeB.lastMessageTimestamp().orElse(null));
        var maxTimestamp = Math.max(aLastTimestamp, bLastTimestamp);

        var mergedMessages = mergeMessages(rangeA.messages(), rangeB.messages(), maxTimestamp);

        var builder = new SyncActionMessageRangeBuilder()
                .messages(mergedMessages);

        if (maxTimestamp != 0) {
            builder.lastMessageTimestamp(Instant.ofEpochSecond(maxTimestamp));
        }

        var aSystemTimestamp = toEpochSeconds(rangeA.lastSystemMessageTimestamp().orElse(null));
        var bSystemTimestamp = toEpochSeconds(rangeB.lastSystemMessageTimestamp().orElse(null));
        if (aSystemTimestamp != 0 || bSystemTimestamp != 0) {
            var maxSystemTimestamp = Math.max(aSystemTimestamp, bSystemTimestamp);
            if (maxTimestamp == 0 || maxSystemTimestamp > maxTimestamp) {
                builder.lastSystemMessageTimestamp(Instant.ofEpochSecond(maxSystemTimestamp));
            }
        }

        return builder.build();
    }

    /**
     * Merges two message lists per WhatsApp Web {@code g} helper.
     *
     * <p>Concatenates both lists, then keeps only messages whose timestamp
     * is {@code >=} the given threshold. Among messages with the same key ID,
     * the one with the higher timestamp wins.
     *
     * @param messagesA     the first message list
     * @param messagesB     the second message list
     * @param maxTimestamp   the threshold timestamp (epoch seconds)
     * @return the merged and deduplicated message list
     */
    private static List<SyncActionMessage> mergeMessages(List<SyncActionMessage> messagesA, List<SyncActionMessage> messagesB, long maxTimestamp) {
        var byKeyId = new LinkedHashMap<String, SyncActionMessage>();
        var combined = new ArrayList<SyncActionMessage>(messagesA.size() + messagesB.size());
        combined.addAll(messagesA);
        combined.addAll(messagesB);

        for (var msg : combined) {
            var keyId = msg.key()
                    .flatMap(key -> key.id())
                    .orElse("");
            var msgTimestamp = toEpochSeconds(msg.timestamp().orElse(null));

            if (msgTimestamp >= maxTimestamp) {
                var existing = byKeyId.get(keyId);
                if (existing != null) {
                    var existingTimestamp = toEpochSeconds(existing.timestamp().orElse(null));
                    if (existingTimestamp < msgTimestamp) {
                        byKeyId.put(keyId, msg);
                    }
                } else {
                    byKeyId.put(keyId, msg);
                }
            }
        }

        return new ArrayList<>(byKeyId.values());
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
     *       must be strictly less than A's {@code lastMessageTimestamp}
     * </ul>
     *
     * @param encloser the range that should enclose
     * @param enclosed the range that should be enclosed
     * @return {@code true} if {@code encloser} encloses {@code enclosed}
     */
    private static boolean encloses(SyncActionMessageRange encloser, SyncActionMessageRange enclosed) {
        var encloserLastTimestamp = toEpochSeconds(encloser.lastMessageTimestamp().orElse(null));

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

            // Per WA Web: encloserLast <= msgTimestamp means NOT enclosed
            // i.e., only enclosed when msgTimestamp is strictly less than encloserLast
            if (encloserLastTimestamp <= toEpochSeconds(msgTimestamp)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Converts an {@link Instant} to epoch seconds, returning {@code 0}
     * for {@code null} values. Mirrors the WhatsApp Web convention of
     * treating missing timestamps as {@code 0}.
     *
     * @param instant the instant to convert, may be {@code null}
     * @return the epoch seconds, or {@code 0} if {@code null}
     */
    private static long toEpochSeconds(Instant instant) {
        return instant != null ? instant.getEpochSecond() : 0;
    }
}
