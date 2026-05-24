package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.time.Duration;
import java.util.Optional;

/**
 * Success result of the
 * {@code WAWebUsyncTextStatus.textStatusParser} parser.
 *
 * @apiNote
 * Surfaced by USync queries that include
 * {@code UsyncQuery.withTextStatusProtocol()}; the WA Web caller is the
 * background contact sync, gated by
 * {@code WAWebTextStatusGatingUtils.receiveTextStatusEnabled}. Carries the
 * peer's modern text-status payload (text, leading emoji, ephemeral
 * lifetime, last-update timestamp); every field is nullable on the wire and
 * the relay sets only those the peer published.
 *
 * @implNote
 * This implementation reads the emoji glyph from the {@code content}
 * attribute of the {@code <emoji>} child (the modern text-status uses an
 * attribute, not inline content), matching {@code textStatusParser}'s
 * {@code r.attrString("content")} call. The {@link #lastUpdateTime()} value
 * is kept as a {@link String} because the relay echoes the value verbatim
 * and WA Web does not parse it into a typed instant.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncTextStatus")
public final class TextStatusResult implements UsyncProtocolResponse {
    /**
     * The status text from the {@code text} attribute, or {@code null} when
     * absent.
     */
    private final String text;

    /**
     * The leading emoji glyph from the {@code <emoji>} child's
     * {@code content} attribute, or {@code null} when absent.
     */
    private final String emoji;

    /**
     * The status's ephemeral lifetime decoded from the
     * {@code ephemeral_duration_sec} attribute, or {@code null} when absent.
     */
    private final Duration ephemeralDuration;

    /**
     * The {@code last_update_time} attribute echoed verbatim by the relay,
     * or {@code null} when absent.
     */
    private final String lastUpdateTime;

    /**
     * Creates a new text-status result.
     *
     * @apiNote
     * Instantiated by the text-status parser; embedders do not call this
     * directly.
     *
     * @param text              the status text, or {@code null}
     * @param emoji             the leading emoji glyph, or {@code null}
     * @param ephemeralDuration the ephemeral lifetime, or {@code null}
     * @param lastUpdateTime    the {@code last_update_time} attribute, or
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
     * @apiNote
     * Body of the status chip shown beside the peer's name in the chat
     * header.
     *
     * @return the text, or empty when absent
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * Returns the leading emoji glyph, when present.
     *
     * @apiNote
     * Decorative emoji rendered to the left of the status text; sourced from
     * the {@code content} attribute of the {@code <emoji>} child, not from
     * the element's inline content.
     *
     * @return the emoji, or empty when absent
     */
    public Optional<String> emoji() {
        return Optional.ofNullable(emoji);
    }

    /**
     * Returns the ephemeral lifetime, when present.
     *
     * @apiNote
     * Wall-clock duration the status remains visible for; absent for
     * permanent statuses.
     *
     * @return the lifetime, or empty when absent
     */
    public Optional<Duration> ephemeralDuration() {
        return Optional.ofNullable(ephemeralDuration);
    }

    /**
     * Returns the {@code last_update_time} attribute, when present.
     *
     * @apiNote
     * Kept as a {@link String} because the relay echoes the value verbatim
     * and WA Web does not decode it into a typed instant; callers that need
     * a timestamp parse it themselves.
     *
     * @return the timestamp string, or empty when absent
     */
    public Optional<String> lastUpdateTime() {
        return Optional.ofNullable(lastUpdateTime);
    }
}
