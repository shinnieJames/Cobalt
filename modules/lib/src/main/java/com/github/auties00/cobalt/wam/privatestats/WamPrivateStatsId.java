package com.github.auties00.cobalt.wam.privatestats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.util.DataUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Manages rotating pseudonymous identifiers for WAM private
 * statistics.
 *
 * <p>Private-channel events are correlated using random hex
 * identifiers that rotate on a configurable schedule. This prevents
 * the server from building long-term user profiles while still
 * allowing correlation within each rotation window.
 *
 * <p>Eight rotation groups are defined by WhatsApp. Each group has a
 * key name, a hash integer (written as the {@code psId} global on
 * the wire), and a rotation period in days:
 * <ul>
 * <li>{@code DefaultPsId} (113760892), never rotates</li>
 * <li>{@code IdTtlDaily} (248614979), rotates every day</li>
 * <li>{@code IdTtlWeekly} (42196056), rotates every 7 days</li>
 * <li>{@code IdTtlMonthly} (191000728), rotates every 30 days</li>
 * <li>{@code IdTtl90Days} (37887164), rotates every 90 days</li>
 * <li>{@code GroupExitExperienceId} (152546501), rotates every 30
 *     days</li>
 * <li>{@code GroupSafetyCheckId} (216763284), rotates every 30
 *     days</li>
 * <li>{@code IdPreMetrics} (56300709), never rotates</li>
 * </ul>
 *
 * <p>This class is not thread-safe. All calls must be made from the
 * single WAM flush thread.
 */
@WhatsAppWebModule(moduleName = "WAWebWamPrivateStats")
@WhatsAppWebModule(moduleName = "WAWebWamGlobals")
public final class WamPrivateStatsId {
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
     * Wall-clock supplier returning Unix epoch seconds. Bound to the
     * system clock in the public constructor; a controlled supplier
     * is injected by behavioural tests through the package-private
     * constructor.
     */
    private final LongSupplier nowEpochSec;

    /**
     * Constructs a new {@code WamPrivateStatsId} instance and
     * initialises all eight rotation groups with fresh random
     * identifiers.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamGlobals", exports = "PrivateStatsAllIds", adaptation = WhatsAppAdaptation.DIRECT)
    public WamPrivateStatsId() {
        this(() -> Instant.now().getEpochSecond());
    }

    /**
     * Constructs a {@code WamPrivateStatsId} with the given
     * wall-clock supplier.
     *
     * <p>Package-private hook used by behavioural tests to drive
     * rotation deterministically. The default public constructor
     * binds {@code nowEpochSec} to {@code Instant.now().getEpochSecond()}.
     *
     * @param nowEpochSec a supplier returning the current Unix
     *                    epoch in seconds
     */
    WamPrivateStatsId(LongSupplier nowEpochSec) {
        this.nowEpochSec = nowEpochSec;
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
     * regenerated.
     *
     * <p>Used by the WAM pipeline to emit a {@code PsIdUpdateEvent}
     * with action {@code ROTATED} for each regenerated entry,
     * mirroring the per-rotation {@code logPsIdUpdate} calls in
     * {@code WAWebWamPrivateStats}'s internal rotate function.
     *
     * @return the list of rotation group descriptors (hash integer
     *         and rotation period in days) that were regenerated,
     *         possibly empty
     */
    @WhatsAppWebExport(moduleName = "WAWebWamPrivateStats", exports = "maybeRotatePsIds", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<RotationInfo> rotateAndReportChanges() {
        return rotate();
    }

    /**
     * Returns a snapshot of every rotation group descriptor currently
     * held (hash integer and rotation period in days).
     *
     * <p>Used at WAM service initialization to emit a
     * {@code PsIdUpdateEvent} with action {@code CREATED} for each
     * entry, mirroring the per-new-entry {@code logPsIdUpdate} calls
     * in {@code WAWebWamPrivateStats.initPrivateStats}. Because
     * Cobalt does not persist PS IDs across sessions, every entry is
     * treated as freshly created on startup.
     *
     * @return an unmodifiable list of rotation group descriptors
     */
    @WhatsAppWebExport(moduleName = "WAWebWamPrivateStats", exports = "initPrivateStats", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<RotationInfo> snapshotAll() {
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
        var now = nowEpochSec.getAsLong();
        var rotated = new ArrayList<RotationInfo>();
        for (var mapEntry : entries.entrySet()) {
            var entry = mapEntry.getValue();
            if (shouldRotate(entry, now)) {
                // WAWebWamPrivateStats: m[e].value=o("WARandomHex").randomHex(16) (32 uppercase hex chars)
                var value = DataUtils.randomHex(16);
                var newEntry = new Entry(entry.key, entry.keyHashInt, entry.rotationDays, value, now);
                mapEntry.setValue(newEntry);
                rotated.add(new RotationInfo(entry.keyHashInt, entry.rotationDays));
            }
        }
        return rotated;
    }

    /**
     * Returns the current identifier value for the given hash
     * integer.
     *
     * @param keyHashInt the rotation group hash integer
     * @return the current hex identifier, or {@code "none"} when the
     *         hash integer is unknown
     */
    @WhatsAppWebExport(moduleName = "WAWebWamPrivateStats", exports = "getLatestPrivateStatsIdValueFromKey", adaptation = WhatsAppAdaptation.ADAPTED)
    public String getValueForHash(int keyHashInt) {
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
     * @return the key name, for example {@code "DefaultPsId"}, or
     *         {@code "unknown_<hash>"} when the hash integer is
     *         unknown
     */
    @WhatsAppWebExport(moduleName = "WAWebWamPrivateStats", exports = "getPrivateStatsKeyFromInt", adaptation = WhatsAppAdaptation.ADAPTED)
    public String getKeyNameForHash(int keyHashInt) {
        if (keyHashInt == 0) {
            return "none";
        }
        var entry = entries.get(keyHashInt);
        return entry != null ? entry.key : "unknown_" + keyHashInt;
    }

    /**
     * Inserts a fresh rotation group keyed by its hash integer with a
     * newly-generated 32-character hex identifier.
     *
     * @param key          the human-readable key name
     * @param keyHashInt   the hash integer used as the wire-level
     *                     {@code psId} key
     * @param rotationDays the rotation period in days, or {@code -1}
     *                     when the entry never rotates
     */
    private void addEntry(String key, int keyHashInt, int rotationDays) {
        // WAWebWamPrivateStats: m[e].value=o("WARandomHex").randomHex(16) (32 uppercase hex chars)
        var value = DataUtils.randomHex(16);
        var epoch = nowEpochSec.getAsLong();
        var entry = new Entry(key, keyHashInt, rotationDays, value, epoch);
        entries.put(keyHashInt, entry);
    }

    /**
     * Returns whether the given entry should rotate at the supplied
     * wall-clock instant.
     *
     * @param entry       the rotation group entry
     * @param nowEpochSec the current Unix epoch seconds
     * @return {@code true} when the entry has a positive rotation
     *         period and its creation timestamp predates the current
     *         period boundary
     */
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
    public record RotationInfo(int keyHashInt, int rotationDays) {

    }

    /**
     * A single rotation group entry, holding the current identifier
     * value and the timestamp at which it was generated.
     *
     * @param key              Human-readable key name, for example {@code "DefaultPsId"}.
     * @param keyHashInt       Hash integer key, written into the on-wire {@code psId}
     *                         global.
     * @param rotationDays     Rotation period in days, or {@code -1} when the entry
     *                         never rotates.
     * @param value            The 32-character hex identifier currently assigned to this
     *                         entry.
     * @param creationEpochSec Unix epoch seconds at which {@link #value()} was generated,
     *                         used to detect period boundaries.
     */
    private record Entry(String key, int keyHashInt, int rotationDays, String value, long creationEpochSec) {

    }
}
