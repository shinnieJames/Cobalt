package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.util.DataUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <li>{@code DefaultPsId} (113760892): never rotates
 * <li>{@code IdTtlDaily} (248614979): rotates every day
 * <li>{@code IdTtlWeekly} (42196056): rotates every 7 days
 * <li>{@code IdTtlMonthly} (191000728): rotates every 30 days
 * <li>{@code IdTtl90Days} (37887164): rotates every 90 days
 * <li>{@code GroupExitExperienceId} (152546501): rotates every 30 days
 * <li>{@code GroupSafetyCheckId} (216763284): rotates every 30 days
 * <li>{@code IdPreMetrics} (56300709): never rotates
 * </ul>
 *
 * <p>This class is not thread-safe; all calls must be made from the
 * single WAM flush thread.
 *
 * @implNote Adapts {@code WAWebWamPrivateStats}, which initialises PS IDs
 *     from IndexedDB, generates random hex identifiers, and rotates them
 *     based on configurable day-aligned periods. The eight rotation groups
 *     and their keyHashInt values/rotation periods are defined by
 *     {@code WAWebWamGlobals.PrivateStatsAllIds}.
 */
@WhatsAppWebModule(moduleName = "WAWebWamPrivateStats")
@WhatsAppWebModule(moduleName = "WAWebWamGlobals")
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
     *
     * @implNote The rotation-group tuples (key name, key hash int,
     *           rotation period in days) replicate the JS literal
     *           {@code WAWebWamGlobals.PrivateStatsAllIds} exactly.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamGlobals", exports = "PrivateStatsAllIds")
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
     * returns metadata for each rotation group whose value was
     * regenerated in this call.
     *
     * <p>This is used by the WAM pipeline to emit a
     * {@code PsIdUpdateEvent} with action {@code ROTATED} for each
     * regenerated entry, mirroring the per-rotation
     * {@code logPsIdUpdate} calls in
     * {@code WAWebWamPrivateStats}'s internal rotate function.
     *
     * @return the list of rotation group descriptors (hash integer and
     *         rotation period in days) that were regenerated, possibly
     *         empty
     */
    List<RotationInfo> rotateAndReportChanges() {
        return rotate();
    }

    /**
     * Returns a snapshot of every rotation group descriptor currently
     * held (hash integer and rotation period in days).
     *
     * <p>This is used at WAM service initialization to emit a
     * {@code PsIdUpdateEvent} with action {@code CREATED} for each
     * entry, mirroring the per-new-entry {@code logPsIdUpdate} calls
     * in {@code WAWebWamPrivateStats.initPrivateStats}. Because Cobalt
     * does not persist PS IDs across sessions, every entry is treated
     * as freshly created on startup.
     *
     * @return an unmodifiable list of rotation group descriptors
     */
    List<RotationInfo> snapshotAll() {
        var result = new ArrayList<RotationInfo>(entries.size());
        for (var entry : entries.values()) {
            result.add(new RotationInfo(entry.keyHashInt, entry.rotationDays));
        }
        return List.copyOf(result);
    }

    /**
     * Performs the in-place rotation of entries whose period has
     * elapsed and returns metadata for those that were regenerated.
     *
     * @return the list of rotation descriptors for entries that were
     *         regenerated in this call
     */
    private List<RotationInfo> rotate() {
        var now = Instant.now().getEpochSecond();
        var rotated = new ArrayList<RotationInfo>();
        for (var mapEntry : entries.entrySet()) {
            var entry = mapEntry.getValue();
            if (shouldRotate(entry, now)) {
                var value = DataUtils.randomHex(32);
                var newEntry = new Entry(entry.key, entry.keyHashInt, entry.rotationDays, value, now);
                mapEntry.setValue(newEntry);
                rotated.add(new RotationInfo(entry.keyHashInt, entry.rotationDays));
            }
        }
        return rotated;
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
        var value = DataUtils.randomHex(32);
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
     * Descriptor exposed to the WAM pipeline for a rotation group that
     * is reported as created or rotated.
     *
     * @param keyHashInt   the rotation group hash integer, used as the
     *                     {@code psIdKey} on the emitted
     *                     {@code PsIdUpdateEvent}
     * @param rotationDays the rotation period in days, used as the
     *                     {@code psIdRotationFrequence} on the emitted
     *                     {@code PsIdUpdateEvent}
     */
    record RotationInfo(int keyHashInt, int rotationDays) {
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
