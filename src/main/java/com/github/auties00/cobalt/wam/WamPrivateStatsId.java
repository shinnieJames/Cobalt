package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.util.FastRandomUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages rotating pseudonymous identifiers for WAM private statistics.
 *
 * <p>Private-channel events are correlated using random hex identifiers
 * that rotate on a configurable schedule. This prevents the server from
 * building long-term user profiles while still allowing correlation
 * within each rotation window.
 *
 * <p>Eight rotation groups are defined by WhatsApp, each with a key
 * name, a hash integer (written as the {@code psId} global on the wire),
 * and a rotation period in days:
 * <ul>
 * <li>{@code DefaultPsId} (113760892) — never rotates
 * <li>{@code IdTtlDaily} (248614979) — rotates every day
 * <li>{@code IdTtlWeekly} (42196056) — rotates every 7 days
 * <li>{@code IdTtlMonthly} (191000728) — rotates every 30 days
 * <li>{@code IdTtl90Days} (37887164) — rotates every 90 days
 * <li>{@code GroupExitExperienceId} (152546501) — rotates every 30 days
 * <li>{@code GroupSafetyCheckId} (216763284) — rotates every 30 days
 * <li>{@code IdPreMetrics} (56300709) — never rotates
 * </ul>
 *
 * <p>This class is not thread-safe; all calls must be made from the
 * single WAM flush thread.
 *
 * @apiNote WAWebWamPrivateStats: initialises PS IDs from IndexedDB,
 * generates random hex identifiers, and rotates them based on
 * configurable day-aligned periods.
 * WAWebWamGlobals.PrivateStatsAllIds: defines the eight rotation groups
 * with their keyHashInt values and rotation periods.
 */
final class WamPrivateStatsId {
    /**
     * Number of seconds in one day.
     */
    private static final long DAY_SECONDS = 86_400L;

    /**
     * Map from hash integer to the current rotation entry. Insertion
     * order is preserved for deterministic iteration.
     */
    private final Map<Integer, Entry> entries;

    /**
     * Constructs a new {@code WamPrivateStatsId} instance and initialises
     * all eight rotation groups with fresh random identifiers.
     */
    WamPrivateStatsId() {
        this.entries = new LinkedHashMap<>();
        addEntry("DefaultPsId", 113760892, -1);
        addEntry("GroupExitExperienceId", 152546501, 30);
        addEntry("GroupSafetyCheckId", 216763284, 30);
        addEntry("IdPreMetrics", 56300709, -1);
        addEntry("IdTtl90Days", 37887164, 90);
        addEntry("IdTtlDaily", 248614979, 1);
        addEntry("IdTtlMonthly", 191000728, 30);
        addEntry("IdTtlWeekly", 42196056, 7);
    }

    /**
     * Rotates any identifiers whose rotation period has elapsed and
     * returns a snapshot of the current hash-int-to-value mapping.
     *
     * @return an unmodifiable map from hash integer to the current
     *         hex identifier value
     */
    Map<Integer, String> rotateAndGet() {
        var now = Instant.now().getEpochSecond();
        for (var mapEntry : entries.entrySet()) {
            var entry = mapEntry.getValue();
            if (shouldRotate(entry, now)) {
                var value = FastRandomUtils.randomHex(32);
                var newEntry = new Entry(entry.key, entry.keyHashInt, entry.rotationDays, value, now);
                mapEntry.setValue(newEntry);
            }
        }
        var result = new LinkedHashMap<Integer, String>();
        entries.forEach((k, v) -> result.put(k, v.value));
        return Map.copyOf(result);
    }

    /**
     * Returns the current identifier value for the given hash integer.
     *
     * @param keyHashInt the rotation group hash integer
     * @return the current hex identifier, or {@code "none"} if the
     *         hash integer is unknown
     */
    String getValueForHash(int keyHashInt) {
        var entry = entries.get(keyHashInt);
        return entry != null ? entry.value : "none";
    }

    /**
     * Returns the key name for the given hash integer.
     *
     * <p>The key name is used as the beaconing buffer key for
     * private-channel events, giving each rotation group its own
     * independent beaconing track.
     *
     * @param keyHashInt the rotation group hash integer
     * @return the key name (e.g. {@code "DefaultPsId"}), or
     *         {@code "unknown_<hash>"} if the hash integer is unknown
     */
    String getKeyNameForHash(int keyHashInt) {
        if (keyHashInt == 0) {
            return "none";
        }
        var entry = entries.get(keyHashInt);
        return entry != null ? entry.key : "unknown_" + keyHashInt;
    }

    private void addEntry(String key, int keyHashInt, int rotationDays) {
        var value = FastRandomUtils.randomHex(32);
        var epoch = Instant.now().getEpochSecond();
        var entry = new Entry(key, keyHashInt, rotationDays, value, epoch);
        entries.put(keyHashInt, entry);
    }

    private static boolean shouldRotate(Entry entry, long nowEpochSec) {
        if (entry.rotationDays <= 0) {
            return false;
        }
        var periodSeconds = entry.rotationDays * DAY_SECONDS;
        var currentPeriodStart = (nowEpochSec / periodSeconds) * periodSeconds;
        return entry.creationEpochSec < currentPeriodStart;
    }

    /**
     * A single rotation group entry.
     */
    private static final class Entry {
        final String key;
        final int keyHashInt;
        final int rotationDays;
        final String value;
        final long creationEpochSec;

        Entry(String key, int keyHashInt, int rotationDays, String value, long creationEpochSec) {
            this.key = key;
            this.keyHashInt = keyHashInt;
            this.rotationDays = rotationDays;
            this.value = value;
            this.creationEpochSec = creationEpochSec;
        }
    }
}
