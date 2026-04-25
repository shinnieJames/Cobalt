package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides server-pushed overrides for WAM event sampling weights.
 *
 * <p>By default each event is sampled using the static
 * {@code releaseWeight()} declared in its {@code @WamEvent} annotation.
 * This class allows the runtime to register per-event-id overrides
 * (typically received via AB props or server configuration), which take
 * precedence over the annotation value.
 *
 * <p>When no override is registered for a given event id, the caller
 * falls back to the static weight.
 *
 * <p>This class is thread-safe: the backing map is a
 * {@link ConcurrentHashMap} and individual lookups are atomic.
 *
 * @implNote Collapses two WA Web modules into a single storage class.
 *     {@code WAWebEventSamplingCache} owns the {@code Map<eventId, weight>}
 *     and is populated from {@code WAWebApiAbPropEventSamplingConfig};
 *     {@code WAWebEventSampling} is a thin façade that exposes
 *     {@code getClientEventSamplingWeight(id)} as
 *     {@code Math.abs(impl(id))} where {@code impl} is installed by
 *     {@code WAWebEventSamplingCache.initializeEventSamplingCache} via
 *     {@code setGetEventSamplingConfigValueImpl}. Cobalt removes the
 *     pluggable-function seam (there is only one implementation in
 *     practice) and has the caller, {@link WamService#commit}, read
 *     directly from this cache and apply {@code Math.abs} locally. As
 *     a result {@code setGetEventSamplingConfigValueImpl} has no Java
 *     analogue and {@code getClientEventSamplingWeight} is split across
 *     {@link #get} (map lookup) and {@link WamService} (absolute-value
 *     normalization plus fallback to the annotation weight).
 */
@WhatsAppWebModule(moduleName = "WAWebEventSamplingCache")
@WhatsAppWebModule(moduleName = "WAWebEventSampling")
final class WamSamplingOverride {
    /**
     * Map from event id to the overridden sampling weight. Absent keys
     * mean "use the default annotation weight".
     */
    private final Map<Integer, Integer> overrides;

    /**
     * Constructs a new {@code WamSamplingOverride} with no overrides.
     */
    WamSamplingOverride() {
        this.overrides = new ConcurrentHashMap<>();
    }

    /**
     * Registers or updates a sampling weight override for the given
     * event id.
     *
     * @implNote Mirrors the per-entry insert in
     *     {@code WAWebEventSamplingCache.updateEventSamplingFromStorage}'s
     *     {@code forEach(function(t){e.set(t.eventCode, t.samplingWeight)})}
     *     loop, exposed here as a direct setter so callers can stage
     *     overrides individually rather than only through a bulk replace.
     * @param eventId the numeric WAM event identifier
     * @param weight  the overridden sampling weight (must be positive)
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
     * Removes any sampling weight override for the given event id.
     *
     * @implNote NO_WA_BASIS. WA Web's {@code WAWebEventSamplingCache}
     *     only supports wholesale rebuild of the map via
     *     {@code updateEventSamplingFromStorage}; Cobalt adds a targeted
     *     removal so the surrounding {@link WamService} can expose a
     *     symmetric add/remove override API without forcing a full
     *     rebuild on every toggle.
     * @param eventId the numeric WAM event identifier
     */
    void remove(int eventId) {
        overrides.remove(eventId);
    }

    /**
     * Replaces all current overrides with the entries from the given map.
     *
     * @implNote Mirrors the body of
     *     {@code WAWebEventSamplingCache.updateEventSamplingFromStorage},
     *     which clears and repopulates the backing map with the
     *     {@code (eventCode, samplingWeight)} pairs returned by
     *     {@code WAWebApiAbPropEventSamplingConfig.getEventSamplingConfigs}
     *     and then flips the cache-ready flag. In WA Web that flag gates
     *     {@code u(t) = s ? e.get(t) : null}; Cobalt omits the flag
     *     because the map is only consulted once initialization has
     *     explicitly seeded it, so an empty map already produces the
     *     correct {@code null} semantics.
     * @param newOverrides a map from event id to sampling weight
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
     * Returns the overridden sampling weight for the given event id, or
     * empty if no override is registered.
     *
     * @implNote Implements the raw lookup portion of WA Web's
     *     {@code WAWebEventSampling.getClientEventSamplingWeight}, which
     *     reads {@code impl(id)} where {@code impl} is the cache-backed
     *     closure installed by
     *     {@code WAWebEventSamplingCache.initializeEventSamplingCache}
     *     ({@code function u(t){return s ? e.get(t) : null}}). The
     *     absolute-value normalization applied by WA Web's
     *     {@code getClientEventSamplingWeight} ({@code Math.abs(t)}) is
     *     applied by the caller in
     *     {@link WamService#commit} so that the two pieces stay adjacent
     *     to the release-weight fallback.
     * @param eventId the numeric WAM event identifier
     * @return an {@code OptionalInt} containing the raw override weight,
     *         or empty if no override is registered
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
