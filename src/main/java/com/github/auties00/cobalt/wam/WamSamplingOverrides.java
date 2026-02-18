package com.github.auties00.cobalt.wam;

import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds server-configurable sampling weight overrides for WAM events.
 *
 * <p>WhatsApp servers can push per-event sampling weight overrides via
 * A/B props configuration, allowing remote adjustment of telemetry volume
 * without a client update. When an override is present for an event ID,
 * it takes precedence over the static weight defined in the
 * {@code @WamEvent} annotation.
 *
 * <p>Override values follow the same semantics as static weights: a value
 * of {@code 1} means every occurrence is logged, a value of {@code 10}
 * means roughly one in ten occurrences is logged.
 *
 * <p>This class is thread-safe.
 *
 * @see WamService
 */
final class WamSamplingOverrides {
    private final ConcurrentMap<Integer, Integer> overrides = new ConcurrentHashMap<>();

    /**
     * Returns the override weight for the given event ID, or empty if no
     * override is configured.
     *
     * @param eventId the numeric event identifier
     * @return the override weight, or empty
     */
    OptionalInt getWeight(int eventId) {
        var value = overrides.get(eventId);
        return value != null ? OptionalInt.of(value) : OptionalInt.empty();
    }

    /**
     * Sets a sampling weight override for the given event ID.
     *
     * <p>An override value of {@code 0} means the event is always dropped.
     * A value of {@code 1} means every occurrence is logged. Negative
     * values are treated as their absolute value.
     *
     * @param eventId the numeric event identifier
     * @param weight  the override sampling weight
     */
    void setWeight(int eventId, int weight) {
        overrides.put(eventId, Math.abs(weight));
    }

    /**
     * Removes the sampling weight override for the given event ID,
     * reverting to the static weight from the annotation.
     *
     * @param eventId the numeric event identifier
     */
    void removeWeight(int eventId) {
        overrides.remove(eventId);
    }

    /**
     * Removes all sampling weight overrides.
     */
    void clear() {
        overrides.clear();
    }

    /**
     * Bulk-updates sampling weight overrides from a map of event IDs to
     * weights. Existing overrides not present in the map are retained.
     *
     * @param weights a map of event IDs to sampling weights
     */
    void putAll(java.util.Map<Integer, Integer> weights) {
        weights.forEach((id, w) -> overrides.put(id, Math.abs(w)));
    }
}
