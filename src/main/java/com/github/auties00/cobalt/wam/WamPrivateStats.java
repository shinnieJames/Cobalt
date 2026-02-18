package com.github.auties00.cobalt.wam;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the rotating pseudonymous identifiers (PS IDs) used for
 * privacy-sensitive WAM events on the {@code PRIVATE} channel.
 *
 * <p>WhatsApp defines eight rotation buckets, each with a different
 * rotation period. Events are assigned to a bucket via their
 * {@code privateStatsId} integer. The PS ID for each bucket is a random
 * 16-character hex string that is rotated when the current time crosses
 * a rotation boundary.
 *
 * <p>PS IDs are generated fresh on service initialization. Rotation
 * checks happen lazily on each access. The default PS ID (hash
 * {@code 113760892}) and the pre-metrics ID (hash {@code 56300709})
 * never rotate.
 *
 * <p>This class is thread-safe.
 *
 * @see WamService
 */
final class WamPrivateStats {
    private static final long DAY_SECONDS = 86_400L;

    /**
     * The "none" sentinel used when no PS ID is assigned.
     */
    static final String NONE = "none";

    /**
     * The eight rotation buckets defined by WhatsApp.
     */
    enum Bucket {
        DEFAULT_PS_ID(113760892, -1),
        GROUP_EXIT_EXPERIENCE_ID(152546501, 30),
        GROUP_SAFETY_CHECK_ID(216763284, 30),
        ID_PRE_METRICS(56300709, -1),
        ID_TTL_90_DAYS(37887164, 90),
        ID_TTL_DAILY(248614979, 1),
        ID_TTL_MONTHLY(191000728, 30),
        ID_TTL_WEEKLY(42196056, 7);

        final int keyHashInt;
        final int rotationPeriodDays;

        Bucket(int keyHashInt, int rotationPeriodDays) {
            this.keyHashInt = keyHashInt;
            this.rotationPeriodDays = rotationPeriodDays;
        }
    }

    private final ConcurrentHashMap<Integer, PsIdEntry> entries = new ConcurrentHashMap<>();
    private final Map<Integer, Bucket> hashToBucket = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@code WamPrivateStats} and generates fresh PS IDs
     * for all eight buckets.
     */
    WamPrivateStats() {
        for (var bucket : Bucket.values()) {
            hashToBucket.put(bucket.keyHashInt, bucket);
            entries.put(bucket.keyHashInt, new PsIdEntry(
                    randomHex16(),
                    bucket.rotationPeriodDays,
                    System.currentTimeMillis() / 1000
            ));
        }
    }

    /**
     * Returns the current PS ID value for the given private stats key hash,
     * rotating the ID if the rotation period has elapsed.
     *
     * @param keyHashInt the private stats key hash integer from the event
     * @return the PS ID string, or {@link #NONE} if the key is unknown
     */
    String getPsId(int keyHashInt) {
        var entry = entries.get(keyHashInt);
        if (entry == null) {
            return NONE;
        }

        var bucket = hashToBucket.get(keyHashInt);
        if (bucket != null && shouldRotate(entry.creationEpochSeconds, bucket.rotationPeriodDays)) {
            var newEntry = new PsIdEntry(
                    randomHex16(),
                    bucket.rotationPeriodDays,
                    System.currentTimeMillis() / 1000
            );
            entries.put(keyHashInt, newEntry);
            return newEntry.value;
        }

        return entry.value;
    }

    /**
     * Returns the PS ID for the "regular" private channel, which uses the
     * {@link Bucket#DEFAULT_PS_ID} bucket.
     *
     * @return the default PS ID string
     */
    String getDefaultPsId() {
        return getPsId(Bucket.DEFAULT_PS_ID.keyHashInt);
    }

    /**
     * Returns the PS ID values for all buckets, keyed by hash integer.
     *
     * @return an unmodifiable map of hash integers to PS ID strings
     */
    Map<Integer, String> getAllPsIds() {
        var result = new ConcurrentHashMap<Integer, String>();
        for (var entry : entries.entrySet()) {
            result.put(entry.getKey(), entry.getValue().value);
        }
        return Map.copyOf(result);
    }

    private static boolean shouldRotate(long creationEpochSeconds, int rotationPeriodDays) {
        if (rotationPeriodDays <= 0) {
            return false;
        }
        var periodSeconds = rotationPeriodDays * DAY_SECONDS;
        var now = System.currentTimeMillis() / 1000;
        var currentBoundary = (now / periodSeconds) * periodSeconds;
        return creationEpochSeconds < currentBoundary;
    }

    private static String randomHex16() {
        var random = ThreadLocalRandom.current();
        return String.format("%016x", random.nextLong());
    }

    private record PsIdEntry(String value, int rotationPeriodDays, long creationEpochSeconds) {
    }
}
