package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.OptionalLong;

/**
 * Manages daily-sampled sequence numbers for WAM event beaconing.
 *
 * <p>At the start of each calendar day in UTC, there is a one percent
 * chance that beaconing is activated for the current session. When
 * activated, each call to {@link #nextSequenceNumber(String)} returns a
 * monotonically increasing sequence number that is written as global
 * field {@code 3433} ({@code beaconSessionId}) before each event.
 *
 * <p>If beaconing is not activated for the current day the method
 * returns an empty {@link OptionalLong} and no beacon global is written.
 *
 * <p>Beaconing state is tracked independently per buffer key. WhatsApp
 * Web defines ten buffer keys, namely {@code "regular"},
 * {@code "realtime"}, and the eight private-stats id key names such as
 * {@code "DefaultPsId"} or {@code "IdTtlDaily"}.
 *
 * <p>Implementations are not required to be thread-safe; all calls
 * occur on the single WAM flush thread. The production implementation
 * is {@link DefaultWamBeaconing}; tests substitute their own.
 */
@WhatsAppWebModule(moduleName = "WAWebWamBeaconing")
interface WamBeaconing {
    /**
     * Returns the next beaconing sequence number when beaconing is
     * active for the current day, otherwise returns an empty value.
     *
     * <p>On the first call of a new calendar day a random check
     * determines whether beaconing is activated. When activated the
     * sequence counter resets to {@code 1} and increments on each
     * subsequent call within the same day.
     * @param bufferKey the buffer key identifying the beaconing track,
     *                  for example {@code "regular"}, {@code "realtime"},
     *                  or a private-stats id key name
     * @return an {@code OptionalLong} containing the sequence number
     *         when beaconing is active, or empty otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebWamBeaconing", exports = "maybeGetEventSequenceNumber", adaptation = WhatsAppAdaptation.ADAPTED)
    OptionalLong nextSequenceNumber(String bufferKey);
}