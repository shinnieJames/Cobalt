package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime override map for per-event WAM sampling weights pushed in
 * from AB props or other server configuration channels.
 *
 * @apiNote
 * Embedders do not interact with this class directly; it sits between
 * {@code WAWebApiAbPropEventSamplingConfig} (the AB-props facade that
 * ships the {@code (eventCode, samplingWeight)} pairs) and
 * {@link WamService}'s commit-time weight resolver. When an event id is
 * present in the override map, {@link WamService} uses the override
 * instead of the {@code @WamEvent(releaseWeight = ...)} declared on the
 * generated event class; absent ids fall back to the annotation value.
 *
 * @implNote
 * This implementation folds the JavaScript pair
 * ({@code WAWebEventSamplingCache} for write, {@code WAWebEventSampling}
 * for read) into a single class. WA Web tracks an initialisation flag
 * that suppresses reads before {@code updateEventSamplingFromStorage}
 * has run; Cobalt does not, because the empty starting map already
 * returns empty and the caller (WamService) treats empty the same way
 * the JS reader treats {@code null}.
 */
@WhatsAppWebModule(moduleName = "WAWebEventSamplingCache")
@WhatsAppWebModule(moduleName = "WAWebEventSampling")
final class WamSamplingOverride {
    /**
     * The backing map from WAM event id to overridden sampling weight;
     * absent keys signal the caller should fall back to the static
     * {@code @WamEvent} annotation value.
     */
    private final Map<Integer, Integer> overrides;

    /**
     * Constructs an override map with no entries; equivalent to the
     * pre-initialisation state of {@code WAWebEventSamplingCache}.
     *
     * @apiNote
     * Called by {@link WamService}'s constructor; embedders do not
     * instantiate this class.
     */
    WamSamplingOverride() {
        this.overrides = new ConcurrentHashMap<>();
    }

    /**
     * Registers a sampling weight override for the given WAM event id.
     *
     * @apiNote
     * Used to install a single override row; the bulk replacement path
     * is {@link #replaceAll(Map)}, which is what
     * {@link WamService#initialize()} actually calls after reading the
     * full AB-props snapshot. Direct {@code put} usage is reserved for
     * tests and for any future code that wants to install one-off
     * overrides without disturbing the rest of the map.
     *
     * @param eventId the numeric WAM event identifier
     * @param weight  the overridden sampling weight to use instead of
     *                the event's static {@code @WamEvent(releaseWeight)}
     */
    @WhatsAppWebExport(
            moduleName = "WAWebEventSamplingCache",
            exports = "updateEventSamplingFromStorage",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void put(int eventId, int weight) {
        overrides.put(eventId, weight);
    }

    /**
     * Removes the override for the given WAM event id, restoring the
     * static {@code @WamEvent(releaseWeight)} fallback for subsequent
     * commits of that id.
     *
     * @apiNote
     * Test-facing only; AB-props refreshes flow through
     * {@link #replaceAll(Map)} so removals happen implicitly by absence
     * from the new snapshot.
     *
     * @param eventId the numeric WAM event identifier
     */
    void remove(int eventId) {
        overrides.remove(eventId);
    }

    /**
     * Atomically swaps the entire override set with the entries from
     * {@code newOverrides}; keys present before but absent from the
     * new map are dropped.
     *
     * @apiNote
     * Called by {@link WamService#initialize()} once after the AB-props
     * snapshot has loaded and again after every subsequent push, so the
     * override map always reflects the latest server state in a single
     * atomic visible step.
     *
     * @implNote
     * This implementation is not atomic with respect to concurrent
     * readers; the clear-then-putAll pair on a
     * {@link ConcurrentHashMap} leaves a brief window in which a
     * reader can observe the empty intermediate state. The single-flush
     * threading model documented on {@link WamService} guarantees this
     * window is invisible in production because readers and writers run
     * on the same thread.
     *
     * @param newOverrides the new full override set to install,
     *                     mapping event id to sampling weight
     */
    @WhatsAppWebExport(
            moduleName = "WAWebEventSamplingCache",
            exports = "updateEventSamplingFromStorage",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void replaceAll(Map<Integer, Integer> newOverrides) {
        overrides.clear();
        overrides.putAll(newOverrides);
    }

    /**
     * Returns the overridden sampling weight for the given WAM event
     * id, or empty when no override is registered.
     *
     * @apiNote
     * Consulted by {@link WamService}'s commit-time weight resolver;
     * empty means the caller should use the static
     * {@code @WamEvent(releaseWeight)} annotation value instead.
     *
     * @implNote
     * This implementation returns the override as stored, without the
     * {@link Math#abs} fold that {@code WAWebEventSampling.getClientEventSamplingWeight}
     * applies in JavaScript; Cobalt callers normalise the value
     * themselves.
     *
     * @param eventId the numeric WAM event identifier
     * @return the override weight when present, otherwise empty
     */
    @WhatsAppWebExport(
            moduleName = "WAWebEventSampling",
            exports = "getClientEventSamplingWeight",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    OptionalInt get(int eventId) {
        var weight = overrides.get(eventId);
        return weight != null ? OptionalInt.of(weight) : OptionalInt.empty();
    }
}
