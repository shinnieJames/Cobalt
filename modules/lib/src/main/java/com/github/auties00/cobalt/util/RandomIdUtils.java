package com.github.auties00.cobalt.util;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates WhatsApp stanza identifiers and session identifiers used
 * for IQ correlation, message requests, and analytics journeys.
 *
 * @apiNote
 * Instantiate one {@code RandomIdUtils} per logical session that
 * needs sequenced stanza IDs sharing the same random prefix
 * ({@code <rnd1>.<rnd2>-<n>}), so the server can correlate every
 * stanza back to one client session. For one-shot IDs that do not
 * need a shared prefix use the static
 * {@link #newId()} factory. For WAM analytics journey IDs use
 * {@link #generateSid()}.
 *
 * @implNote
 * This implementation derives the prefix from two
 * {@link DataUtils#randomInt(int)} draws in {@code [0, 65536)}, so
 * the prefix space is {@code 2^32}. The counter starts at {@code 1}
 * to match the JS convention.
 */
public final class RandomIdUtils {
    /**
     * The random session prefix shared by every ID emitted from this
     * instance.
     *
     * @apiNote
     * Holds the textual prefix {@code <rnd1>.<rnd2>-} so
     * {@link #generateId()} can concatenate the counter without
     * recomputing the random pair on every call.
     */
    private final String prefix;

    /**
     * Monotonically increasing counter appended after
     * {@link #prefix} by {@link #generateId()}.
     */
    private final AtomicLong counter;

    /**
     * Creates a new generator with a freshly randomised prefix and a
     * counter that starts at {@code 1}.
     *
     * @apiNote
     * Construct one instance per logical client session; reuse it
     * for every stanza issued on that session so the server can
     * group correlated stanzas.
     */
    public RandomIdUtils() {
        var num1 = DataUtils.randomInt(65536);
        var num2 = DataUtils.randomInt(65536);
        this.prefix = num1 + "." + num2 + "-";
        this.counter = new AtomicLong(1);
    }

    /**
     * Returns the next sequenced identifier in the form
     * {@code <rnd1>.<rnd2>-<n>}, advancing the internal counter.
     *
     * @apiNote
     * Call this when emitting a stanza that the server may correlate
     * back via the {@code id} attribute (IQ requests, message
     * requests, retry receipts). Concurrent callers receive distinct
     * counters thanks to {@link AtomicLong#getAndIncrement()}.
     *
     * @return the generated identifier
     */
    public String generateId() {
        return prefix + counter.getAndIncrement();
    }

    /**
     * Returns a one-shot identifier that reuses the same textual
     * layout as {@link #generateId()} but always ends in
     * {@code -1}.
     *
     * @apiNote
     * Use this where allocating a fresh
     * {@link RandomIdUtils} instance would be overkill (single
     * stanza requests that never correlate to a session prefix).
     *
     * @return the generated identifier
     */
    public static String newId() {
        var num1 = DataUtils.randomInt(65536);
        var num2 = DataUtils.randomInt(65536);
        return num1 + "." + num2 + "-1";
    }

    /**
     * Returns a WAM session identifier composed of an epoch-seconds
     * timestamp, a 10-digit random number, and a 0-to-999 random
     * suffix.
     *
     * @apiNote
     * Use this to tag the {@code sid} on WAM events that participate
     * in a user-journey funnel (for example the messaging and
     * pre-call journey loggers). The shape mirrors the WhatsApp
     * convention of a wall-clock prefix that lets the server bucket
     * sessions by minute even when the random tail is lost.
     *
     * @return the generated session identifier
     */
    public static String generateSid() {
        return Instant.now().getEpochSecond()
               + "-" + DataUtils.randomLong(1_000_000_000, 9_999_999_999L)
               + "-" + DataUtils.randomInt(0, 1000);
    }
}
