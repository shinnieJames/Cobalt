package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.time.Duration;
import java.util.Optional;

/**
 * Success result of {@code WAWebUsyncTextStatus.textStatusParser}.
 *
 * @implNote WAWebUsyncTextStatus.textStatusParser: success branch returns
 *     {@code {text?, emoji?, ephemeralDurationSeconds?, lastUpdateTime?}}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncTextStatus")
public final class TextStatusResult implements UsyncProtocolResult {
    /**
     * The status text, or {@code null} when absent.
     */
    private final String text;

    /**
     * The leading emoji glyph, or {@code null} when absent.
     */
    private final String emoji;

    /**
     * The status's ephemeral lifetime, or {@code null} when absent.
     */
    private final Duration ephemeralDuration;

    /**
     * The last-update timestamp string the relay echoes back, or
     * {@code null} when absent.
     */
    private final String lastUpdateTime;

    /**
     * Creates a new text-status result.
     *
     * @param text              the status text, or {@code null}
     * @param emoji             the leading emoji glyph, or {@code null}
     * @param ephemeralDuration the ephemeral lifetime, or {@code null}
     * @param lastUpdateTime    the last-update timestamp string, or
     *                          {@code null}
     */
    public TextStatusResult(String text, String emoji, Duration ephemeralDuration, String lastUpdateTime) {
        this.text = text;
        this.emoji = emoji;
        this.ephemeralDuration = ephemeralDuration;
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Returns the status text, when present.
     *
     * @return the text
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * Returns the leading emoji glyph, when present.
     *
     * @return the emoji
     */
    public Optional<String> emoji() {
        return Optional.ofNullable(emoji);
    }

    /**
     * Returns the ephemeral lifetime, when present.
     *
     * @return the duration
     */
    public Optional<Duration> ephemeralDuration() {
        return Optional.ofNullable(ephemeralDuration);
    }

    /**
     * Returns the last-update timestamp string, when present.
     *
     * @return the timestamp string
     */
    public Optional<String> lastUpdateTime() {
        return Optional.ofNullable(lastUpdateTime);
    }
}
