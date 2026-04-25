package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Represents a CTWA (Click-to-WhatsApp) external entry point recorded
 * when a user opens a chat via a CTWA ad link.
 *
 * <p>Each entry point captures the ad deep-link type, authentication
 * status, optional partner name, and the time it was added. Entry
 * points expire after one week.
 *
 * @implNote WAWebExternalEntryPointPrefs: stores entry points keyed by
 * chat JID string in {@code WAWebUserPrefsStore} under the
 * {@code EXTERNAL_ENTRY_POINT} key. Each entry has fields:
 * {@code addedTime}, {@code deepLinkType}, {@code authSuccess},
 * {@code partnerName}.
 *
 * @param deepLinkType the type of deep link that led the user to this
 *                     chat (e.g. ad campaign type)
 * @param authSuccess  {@code true} if the user was successfully
 *                     authenticated during the ad flow
 * @param partnerName  the partner/advertiser name, or {@code null} if
 *                     not available
 * @param addedTime    the instant when this entry point was recorded
 */
@WhatsAppWebModule(moduleName = "WAWebExternalEntryPointPrefs")
public record ExternalEntryPoint(
        String deepLinkType,
        boolean authSuccess,
        String partnerName,
        Instant addedTime
) {
    /**
     * Maximum age of an external entry point before it is considered
     * expired and discarded.
     *
     * <p>WA Web reads this constant from
     * {@code WATimeUtils.WEEK_MILLISECONDS}, which is exported as
     * {@code 6048e5} milliseconds — exactly seven days.
     *
     * @implNote WAWebExternalEntryPointPrefs: references
     * {@code o("WATimeUtils").WEEK_MILLISECONDS} as the expiry
     * threshold inside {@code function u(e)}.
     */
    @WhatsAppWebExport(moduleName = "WATimeUtils", exports = "WEEK_MILLISECONDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Duration MAX_AGE = Duration.ofDays(7);

    /**
     * Returns whether this entry point has expired.
     *
     * <p>An entry point is expired when strictly more than one week
     * has elapsed since its {@link #addedTime()}, matching the JS
     * source's strict {@code >} comparison.
     *
     * @return {@code true} if expired
     * @implNote WAWebExternalEntryPointPrefs: function
     * {@code u(e){var t=Date.now(); return t-e.addedTime > WEEK_MILLISECONDS}}.
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalEntryPointPrefs", exports = "getExternalEntryPoint",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isExpired() {
        // WAWebExternalEntryPointPrefs: t - e.addedTime > WEEK_MILLISECONDS
        return Duration.between(addedTime, Instant.now()).compareTo(MAX_AGE) > 0;
    }

    /**
     * Returns the partner name as an {@link Optional}.
     *
     * <p>The JS source stores {@code partnerName} as a possibly-null
     * string ({@code partnerName: r != null ? r : null}); Cobalt
     * exposes it through this Optional accessor while keeping the raw
     * nullable field for direct mirroring.
     *
     * @return the partner name, or empty if {@code null}
     * @implNote WAWebExternalEntryPointPrefs: {@code partnerName} field
     * is set to the fourth argument of {@code saveExternalEntryPoint}
     * coerced to {@code null} when undefined.
     */
    public Optional<String> optionalPartnerName() {
        return Optional.ofNullable(partnerName);
    }
}
