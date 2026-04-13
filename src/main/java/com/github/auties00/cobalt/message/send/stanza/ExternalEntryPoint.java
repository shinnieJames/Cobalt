package com.github.auties00.cobalt.message.send.stanza;

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
     * @implNote WAWebExternalEntryPointPrefs: uses
     * {@code WATimeUtils.WEEK_MILLISECONDS} (7 days) as the expiry
     * threshold.
     */
    private static final Duration MAX_AGE = Duration.ofDays(7);

    /**
     * Returns whether this entry point has expired.
     *
     * <p>An entry point is expired when more than one week has
     * elapsed since its {@link #addedTime()}.
     *
     * @return {@code true} if expired
     * @implNote WAWebExternalEntryPointPrefs: checks
     * {@code Date.now() - e.addedTime > WEEK_MILLISECONDS}.
     */
    public boolean isExpired() {
        return Duration.between(addedTime, Instant.now()).compareTo(MAX_AGE) > 0;
    }

    /**
     * Returns the partner name as an {@link Optional}.
     *
     * @return the partner name, or empty if {@code null}
     * @implNote WAWebExternalEntryPointPrefs: {@code partnerName} can
     * be {@code null}.
     */
    public Optional<String> optionalPartnerName() {
        return Optional.ofNullable(partnerName);
    }
}
