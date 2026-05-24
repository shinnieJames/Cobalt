package com.github.auties00.cobalt.registration.push.apns.plist.value;

import java.time.Instant;
import java.util.Objects;

/**
 * Plist {@code <date>} leaf, the binary plist {@code 0x33} marker.
 *
 * @apiNote
 * Used wherever an APNS plist payload carries a wall-clock timestamp;
 * the binary representation is a big-endian {@code float64} of seconds
 * since the Apple epoch (2001-01-01 UTC) and is converted to a UTC
 * {@link Instant} on decode.
 *
 * @implNote
 * This implementation stores the converted {@link Instant} directly
 * rather than the raw Apple-epoch double; the Apple-epoch offset is
 * applied once at parse time so consumers always see a standard UTC
 * value.
 *
 * @param value the instant
 */
public record PlistDateValue(Instant value) implements PlistValue {
    /**
     * Canonical constructor that rejects a {@code null} instant.
     *
     * @param value the instant
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public PlistDateValue {
        Objects.requireNonNull(value, "value");
    }
}
