package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.usync.UsyncBackoff;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncQuery;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-protocol error returned for a single {@code <user>} entry.
 *
 * <p>Every {@code WAWebUsync*Protocol.parser} starts by asserting the
 * protocol's tag and then probing for an {@code <error/>} child; if found,
 * the parser returns {@code {errorCode, errorText}} instead of the
 * protocol-specific shape. Cobalt collapses both cases into the
 * {@link UsyncProtocolResult} sealed family — every protocol's success
 * variant and {@code UsyncProtocolError} share the same supertype so the
 * caller can pattern-match without lossy casts.
 *
 * <p>The optional {@link #errorBackoff()} carries the
 * {@code error_backoff} attribute, expressed as a {@link Duration}; when
 * present, {@link UsyncQuery} forwards it to
 * {@link UsyncBackoff#setProtocolBackoffMs} so subsequent queries for the
 * same protocol observe the timeout.
 *
 * @implNote each {@code WAWebUsync*Protocol.parser}: the JS shape returns
 *     {@code {errorCode, errorText}} (the device parser also reads
 *     {@code error_backoff}). Cobalt always reads the optional backoff so
 *     every protocol benefits from rate-limit feedback.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public final class UsyncProtocolError implements UsyncProtocolResult {
    /**
     * The {@code code} attribute on the {@code <error/>} child.
     */
    private final int errorCode;

    /**
     * The {@code text} attribute on the {@code <error/>} child; never
     * {@code null} (defaults to the empty string when absent).
     */
    private final String errorText;

    /**
     * The {@code error_backoff} attribute, decoded as a {@link Duration}
     * of seconds, or {@code null} if absent.
     */
    private final Duration errorBackoff;

    /**
     * Creates a new error variant.
     *
     * @param errorCode    the error code
     * @param errorText    the error text
     * @param errorBackoff the requested backoff window, or {@code null} if
     *                     the relay did not specify one
     */
    public UsyncProtocolError(int errorCode, String errorText, Duration errorBackoff) {
        this.errorCode = errorCode;
        this.errorText = Objects.requireNonNullElse(errorText, "");
        this.errorBackoff = errorBackoff;
    }

    /**
     * Returns the error code.
     *
     * @return the {@code code} attribute value
     */
    public int errorCode() {
        return errorCode;
    }

    /**
     * Returns the error text.
     *
     * @return the {@code text} attribute value, never {@code null}
     */
    public String errorText() {
        return errorText;
    }

    /**
     * Returns the requested backoff window, when present.
     *
     * @return the backoff duration
     */
    public Optional<Duration> errorBackoff() {
        return Optional.ofNullable(errorBackoff);
    }
}
