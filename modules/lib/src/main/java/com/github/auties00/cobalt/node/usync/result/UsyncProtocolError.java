package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.UsyncBackoff;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncQuery;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-protocol error returned for a single {@code <user>} entry inside a USync
 * response.
 *
 * @apiNote
 * Surfaced by every {@code WAWebUsync*Protocol.parser} when the relay returns
 * an {@code <error/>} child in place of the protocol-specific payload. Use
 * with {@link UsyncProtocolResult} pattern matching to discriminate this case
 * from the success {@link UsyncProtocolResponse} branch. The optional
 * {@link #errorBackoff()} carries the {@code error_backoff} attribute used by
 * {@link UsyncQuery} to feed {@link UsyncBackoff#setProtocolBackoffMs(String, long)}.
 *
 * @implNote
 * This implementation collapses the JS object literal
 * {@code {errorCode, errorText, errorBackoff}} produced inside
 * {@code WAWebUsync.usyncParser} into a typed record-like class with the
 * backoff field exposed as a {@link Duration}; the wire attribute is an
 * integer in seconds and the conversion to seconds is performed by the
 * top-level parser before construction.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public final class UsyncProtocolError implements UsyncProtocolResult {
    /**
     * The {@code code} attribute on the {@code <error/>} child.
     */
    private final int errorCode;

    /**
     * The {@code text} attribute on the {@code <error/>} child, normalised to
     * the empty string when the relay omits the attribute.
     */
    private final String errorText;

    /**
     * The {@code error_backoff} attribute decoded as a {@link Duration}, or
     * {@code null} when the relay did not specify a backoff window for this
     * protocol error.
     */
    private final Duration errorBackoff;

    /**
     * Creates a new per-protocol error.
     *
     * @apiNote
     * Instantiated by the top-level USync parser; callers do not build these
     * by hand.
     *
     * @param errorCode    the {@code code} attribute value
     * @param errorText    the {@code text} attribute value, or {@code null} to
     *                     normalise to the empty string
     * @param errorBackoff the {@code error_backoff} window, or {@code null}
     *                     when absent
     */
    public UsyncProtocolError(int errorCode, String errorText, Duration errorBackoff) {
        this.errorCode = errorCode;
        this.errorText = Objects.requireNonNullElse(errorText, "");
        this.errorBackoff = errorBackoff;
    }

    /**
     * Returns the {@code code} attribute value.
     *
     * @apiNote
     * The relay uses small integer codes per protocol; the meaning is defined
     * by the WA server, not Cobalt.
     *
     * @return the {@code code} attribute value
     */
    public int errorCode() {
        return errorCode;
    }

    /**
     * Returns the {@code text} attribute value.
     *
     * @apiNote
     * Free-form diagnostic text from the relay; safe to log but not intended
     * for routing decisions.
     *
     * @return the {@code text} attribute value, never {@code null}
     */
    public String errorText() {
        return errorText;
    }

    /**
     * Returns the requested backoff window, when present.
     *
     * @apiNote
     * When present, {@link UsyncQuery} forwards this value to
     * {@link UsyncBackoff#setProtocolBackoffMs(String, long)} so subsequent
     * queries for the same protocol observe the timeout.
     *
     * @return the backoff window, or empty when the relay did not specify one
     */
    public Optional<Duration> errorBackoff() {
        return Optional.ofNullable(errorBackoff);
    }
}
