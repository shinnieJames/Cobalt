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
 * Holds the eight rotating pseudonymous identifiers that tag every WAM
 * private-channel event.
 *
 * <p>The set of rotation groups, their hash integers, and their
 * rotation periods are declared by {@link WhatsAppWebModule WAWebWamGlobals}
 * as the {@code PrivateStatsAllIds} export, and consumed by
 * {@link WhatsAppWebModule WAWebWamPrivateStats} which keeps each
 * group's current identifier in memory and rotates it when the period
 * elapses.
 *
 * <p>The eight groups defined by WhatsApp Web are listed below; each
 * is keyed by its hash integer (written into the {@code psId} global
 * on the wire) and carries a rotation period in days, or {@code -1}
 * when the identifier never rotates:
 *
 * <ul>
 *   <li>{@code DefaultPsId} (113760892), never rotates</li>
 *   <li>{@code IdTtlDaily} (248614979), rotates every 1 day</li>
 *   <li>{@code IdTtlWeekly} (42196056), rotates every 7 days</li>
 *   <li>{@code IdTtlMonthly} (191000728), rotates every 30 days</li>
 *   <li>{@code IdTtl90Days} (37887164), rotates every 90 days</li>
 *   <li>{@code GroupExitExperienceId} (152546501), rotates every 30 days</li>
 *   <li>{@code GroupSafetyCheckId} (216763284), rotates every 30 days</li>
 *   <li>{@code IdPreMetrics} (56300709), never rotates</li>
 * </ul>
 *
 * @implNote
 * This implementation is not thread-safe and is expected to be touched
 * only by the single WAM flush thread, mirroring WA Web's
 * {@code WAWebWamPrivateStats}-internal mutability. WA Web persists
 * the {@code (value, creationTs)} of every group to {@code WAWebWamStorage}
 * (IndexedDB-backed) so identifiers survive a page reload; Cobalt
 * keeps everything in a {@link LinkedHashMap} and regenerates fresh
 * values on every restart.
 */
@WhatsAppWebModule(moduleName = "WAWebWamPrivateStats")
@WhatsAppWebModule(moduleName = "WAWebWamGlobals")
public final class WamPrivateStatsId {
    /**
     * The number of seconds in a UTC day.
     *
     * @implNote
     * This implementation matches the {@code WATimeUtils.DAY_SECONDS}
     * constant that drives WA Web's rotation arithmetic.
     */
    private static final long DAY_SECONDS = 86_400L;

    /**
     * The map from each group's wire-level hash integer to its current
     * rotation entry, kept in insertion order so iteration is
     * deterministic.
     */
    private final Map<Integer, Entry> entries;

    /**
     * The supplier delivering the current Unix epoch in seconds.
     *
     * @implNote
     * This implementation injects the supplier so behavioural tests
     * can drive period-boundary crossings deterministically. The
     * public constructor wires it to {@link Instant#now()}.
     */
    private final LongSupplier nowEpochSec;

    /**
     * Constructs a fresh {@code WamPrivateStatsId} that reads the
     * current epoch from the system clock.
     *
     * @apiNote
     * The eight rotation groups defined by {@link WhatsAppWebModule WAWebWamGlobals}
     * are initialised on construction, each with a freshly generated
     * 32-character hexadecimal identifier. Cobalt does not persist
     * identifiers, so every {@code WhatsAppClient} restart begins a
     * new rotation window for every group.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamGlobals", exports = "PrivateStatsAllIds", adaptation = WhatsAppAdaptation.DIRECT)
    public WamPrivateStatsId() {
        this(() -> Instant.now().getEpochSecond());
    }

    /**
     * Constructs a fresh {@code WamPrivateStatsId} that reads the
     * current epoch from the supplied clock.
     *
     * @apiNote
     * Package-private; intended only for the behavioural test in this
     * sub-package that needs to step the clock across period
     * boundaries.
     *
     * @param nowEpochSec the wall-clock supplier returning Unix epoch
     *                    seconds
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
     * Rotates every group whose period has elapsed since its last
     * regeneration and returns the descriptors of the groups that
     * rotated.
     *
     * @apiNote
     * Called by the WAM flush loop before draining the per-group
     * buffers. The caller emits a {@code PsIdUpdateEvent} with action
     * {@code ROTATED} for every returned descriptor, mirroring the
     * per-rotation {@code logPsIdUpdate} calls in
     * {@link WhatsAppWebModule WAWebWamPrivateStats}.
     *
     * @implNote
     * This implementation runs the rotation inline on the caller
     * thread. WA Web schedules it asynchronously through a Promise
     * loop guarded by a {@code Semaphore} and a server-side
     * {@code WAWebWamStorage.updatePsMeta}; Cobalt does not persist
     * the rotation state, so the storage round-trip is skipped.
     *
     * @return the rotation descriptors for every group whose
     *         identifier was regenerated by this call, possibly empty
     * @see RotationInfo
     */
    @WhatsAppWebExport(moduleName = "WAWebWamPrivateStats", exports = "maybeRotatePsIds", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<RotationInfo> rotateAndReportChanges() {
        return rotate();
    }

    /**
     * Returns a snapshot of every rotation group currently held.
     *
     * @apiNote
     * Called at WAM service initialisation to emit a
     * {@code PsIdUpdateEvent} with action {@code CREATED} for each
     * descriptor, mirroring the per-new-entry {@code logPsIdUpdate}
     * calls in {@link WhatsAppWebModule WAWebWamPrivateStats}
     * {@code .initPrivateStats}.
     *
     * @implNote
     * This implementation always treats every entry as freshly created
     * because Cobalt does not persist {@code WAWebWamStorage}; WA Web
     * filters this list down to the subset of groups absent from the
     * persisted {@code getPsMeta} response.
     *
     * @return an unmodifiable list of {@link RotationInfo} descriptors
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
     * Performs the in-place rotation pass and returns the descriptors
     * for every regenerated entry.
     *
     * @implNote
     * This implementation iterates the {@link LinkedHashMap} in
     * insertion order, applies {@link #shouldRotate} as the
     * period-boundary test, and replaces each elapsed entry with a
     * fresh {@link DataUtils#randomHex} value carrying the new
     * creation timestamp.
     *
     * @return the rotation descriptors for entries regenerated by
     *         this pass
     */
    private List<RotationInfo> rotate() {
        var now = nowEpochSec.getAsLong();
        var rotated = new ArrayList<RotationInfo>();
        for (var mapEntry : entries.entrySet()) {
            var entry = mapEntry.getValue();
            if (shouldRotate(entry, now)) {
                var value = DataUtils.randomHex(16);
                var newEntry = new Entry(entry.key, entry.keyHashInt, entry.rotationDays, value, now);
                mapEntry.setValue(newEntry);
                rotated.add(new RotationInfo(entry.keyHashInt, entry.rotationDays));
            }
        }
        return rotated;
    }

    /**
     * Returns the current 32-character hexadecimal identifier for the
     * group identified by the given wire-level hash integer.
     *
     * @apiNote
     * Called when assembling a private-channel WAM event so the
     * {@code psId} field carries the current rotation value. An
     * unknown hash collapses to the {@code "none"} sentinel that WA
     * Web also uses for the {@code "regular"} channel.
     *
     * @implNote
     * This implementation diverges from {@link WhatsAppWebModule WAWebWamPrivateStats}
     * {@code .getLatestPrivateStatsIdValueFromKey}, which throws when
     * the map has no entry for the given hash; Cobalt returns the
     * {@code "none"} sentinel so callers may treat unknown groups as
     * untagged rather than fatal.
     *
     * @param keyHashInt the wire-level hash integer of the rotation
     *                   group
     * @return the current hexadecimal identifier, or {@code "none"}
     *         when the hash is unknown
     */
    @WhatsAppWebExport(moduleName = "WAWebWamPrivateStats", exports = "getLatestPrivateStatsIdValueFromKey", adaptation = WhatsAppAdaptation.ADAPTED)
    public String getValueForHash(int keyHashInt) {
        var entry = entries.get(keyHashInt);
        return entry != null ? entry.value : "none";
    }

    /**
     * Returns the human-readable key name for the group identified by
     * the given wire-level hash integer.
     *
     * @apiNote
     * Called when selecting the beaconing-buffer key for a
     * private-channel event so each rotation group accumulates onto
     * its own independent buffer.
     *
     * @implNote
     * This implementation mirrors {@link WhatsAppWebModule WAWebWamPrivateStats}
     * {@code .getPrivateStatsKeyFromInt} for known and zero hashes,
     * and returns an {@code "unknown_<hash>"} sentinel for unknown
     * non-zero hashes; WA Web returns {@code undefined} in that case.
     *
     * @param keyHashInt the wire-level hash integer of the rotation
     *                   group
     * @return the key name (for example {@code "DefaultPsId"}),
     *         {@code "none"} for the zero sentinel, or
     *         {@code "unknown_<hash>"} for an unknown non-zero hash
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
     * Inserts a new rotation group keyed by its wire-level hash
     * integer with a freshly generated identifier.
     *
     * @implNote
     * This implementation replicates the per-row logic of WA Web's
     * {@code WAWebWamPrivateStats.initPrivateStats} when no persisted
     * value exists for the group: generate a fresh
     * {@link DataUtils#randomHex} value, stamp it with the current
     * epoch, and store the resulting {@link Entry}.
     *
     * @param key          the human-readable key name (for example
     *                     {@code "DefaultPsId"})
     * @param keyHashInt   the wire-level hash integer
     * @param rotationDays the rotation period in days, or {@code -1}
     *                     when the group never rotates
     */
    private void addEntry(String key, int keyHashInt, int rotationDays) {
        var value = DataUtils.randomHex(16);
        var epoch = nowEpochSec.getAsLong();
        var entry = new Entry(key, keyHashInt, rotationDays, value, epoch);
        entries.put(keyHashInt, entry);
    }

    /**
     * Returns whether the given entry has crossed a period boundary
     * since its current value was generated.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebWamPrivateStats}-internal {@code R} helper:
     * {@code -1} or missing creation timestamps never rotate, and
     * positive periods rotate when the entry's creation epoch is
     * earlier than the start of the current period window
     * ({@code floor(now / periodSeconds) * periodSeconds}).
     *
     * @param entry       the rotation group entry to test
     * @param nowEpochSec the current epoch in seconds
     * @return {@code true} when the entry must rotate, {@code false}
     *         otherwise
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
     * The descriptor reported to the WAM pipeline for a rotation
     * group that has just been created or rotated.
     *
     * @apiNote
     * Consumed by the WAM service to populate the {@code psIdKey} and
     * {@code psIdRotationFrequence} fields of the
     * {@code PsIdUpdateEvent} emitted for every rotation transition,
     * matching the shape WA Web feeds into {@code logPsIdUpdate}.
     *
     * @param keyHashInt   the wire-level hash integer for the rotation
     *                     group, used as {@code psIdKey}
     * @param rotationDays the rotation period in days, used as
     *                     {@code psIdRotationFrequence}
     */
    public record RotationInfo(int keyHashInt, int rotationDays) {

    }

    /**
     * A single rotation group entry binding the human-readable key,
     * the wire-level hash integer, the rotation period, the current
     * identifier value, and the epoch at which it was generated.
     *
     * @apiNote
     * Internal to {@link WamPrivateStatsId}; callers receive only the
     * sanitised {@link RotationInfo} projection.
     *
     * @param key              the human-readable key name (for example
     *                         {@code "DefaultPsId"})
     * @param keyHashInt       the wire-level hash integer written into
     *                         the {@code psId} global
     * @param rotationDays     the rotation period in days, or
     *                         {@code -1} when the group never rotates
     * @param value            the 32-character hexadecimal identifier
     *                         currently assigned to this group
     * @param creationEpochSec the Unix epoch in seconds at which
     *                         {@link #value()} was generated; used by
     *                         {@link #shouldRotate} to detect period
     *                         boundary crossings
     */
    private record Entry(String key, int keyHashInt, int rotationDays, String value, long creationEpochSec) {

    }
}
