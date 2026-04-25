package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Success result of {@code WAWebUsyncDisappearingMode.disappearingModeParser}.
 *
 * @implNote WAWebUsyncDisappearingMode.disappearingModeParser: success
 *     branch returns {@code {duration, t, ephemeralityDisabled?}}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncDisappearingMode")
public final class DisappearingModeResult implements UsyncProtocolResponse {
    /**
     * The disappearing-message timer (zero meaning "off").
     */
    private final Duration duration;

    /**
     * The timestamp the setting was last changed at.
     */
    private final Instant timestamp;

    /**
     * Whether ephemerality is disabled on the peer's PA thread.
     */
    private final boolean ephemeralityDisabled;

    /**
     * Creates a new disappearing-mode result.
     *
     * @param duration             the timer duration; must not be
     *                             {@code null}
     * @param timestamp            the last-changed timestamp; must not be
     *                             {@code null}
     * @param ephemeralityDisabled the {@code ephemerality_disabled} flag
     */
    public DisappearingModeResult(Duration duration, Instant timestamp, boolean ephemeralityDisabled) {
        this.duration = Objects.requireNonNull(duration, "duration cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.ephemeralityDisabled = ephemeralityDisabled;
    }

    /**
     * Returns the disappearing-message timer.
     *
     * @return the duration, never {@code null}
     */
    public Duration duration() {
        return duration;
    }

    /**
     * Returns the timestamp the setting was last changed at.
     *
     * @return the timestamp, never {@code null}
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Returns whether ephemerality is disabled.
     *
     * @return {@code true} if {@code ephemerality_disabled="true"}
     */
    public boolean ephemeralityDisabled() {
        return ephemeralityDisabled;
    }
}
