package com.github.auties00.cobalt.calls2.util;

import java.util.Arrays;

/**
 * Accumulates an amount into a sliding window of fixed-duration buckets and reports
 * the amount-per-second rate over that window.
 *
 * <p>The window is divided into ten buckets, each spanning a caller-chosen duration
 * in milliseconds, so the whole window covers ten times that duration. Each
 * {@link #update(long, long)} advances a ring of buckets by the number of bucket
 * durations that have elapsed since the window head, evicting the buckets that scroll
 * out of the window and subtracting their contents from a running total, then adds the
 * supplied amount to the current head bucket and to the total. {@link #rate(long)}
 * divides the running total by the window duration and scales to a per-second figure,
 * but only once enough buckets have filled to make the figure meaningful; before that
 * threshold, and when the window has fully drained, it reports zero. The calculator is
 * unit-agnostic: feeding byte counts yields bytes per second, feeding packet counts
 * yields packets per second.
 *
 * <p>Timestamps supplied to {@link #update(long, long)} and {@link #rate(long)} are
 * milliseconds from a single monotonic source and must be non-decreasing across calls.
 * The calculator is uninitialized until its first update seeds the window head; an
 * elapsed span that meets or exceeds the full window resets the ring rather than
 * scrolling through every bucket. Instances are not thread-safe; the owning statistics
 * path drives one calculator from a single thread.
 *
 * @implNote This implementation ports {@code wa_rate_calc_update} (fn9492) and
 * {@code wa_rate_calc_get_rate} (fn9493) from the wa-voip engine
 * ({@code foundation/src/utils/rate_calc.cc} in WASM module {@code ff-tScznZ8P}). The
 * native record holds ten {@code int32} buckets, a {@code uint64} window-start
 * timestamp ({@code 0} when uninitialized, offset {@code +40}), an {@code int32}
 * bucket duration (offset {@code +48}), an {@code int32} running total (offset
 * {@code +52}), {@code uint8} head and tail indices (offsets {@code +56}/{@code +57}),
 * and a {@code uint8} minimum-filled-buckets threshold (offset {@code +58}). The update
 * computes {@code elapsed} in bucket-duration units, advances the head index modulo ten
 * that many buckets while zeroing and subtracting each newly vacated bucket, resets the
 * ring when the window start is {@code 0} or the elapsed span reaches the full window,
 * then adds the amount to the head bucket and the running total. The rate computes
 * {@code filled = ((head - tail + 10) % 10) + 1} and returns {@code total * 1000 /
 * window_ms} only when {@code filled} meets the threshold and the elapsed span is not
 * exactly the full window, else {@code 0}. The ten-bucket count, the {@code 1000}
 * millisecond-to-second scale, and the filled-count arithmetic are reproduced exactly;
 * the running total is held as a Java {@code long} to avoid the 32-bit overflow the
 * native record risks on high byte counts.
 */
public final class RateCalculator {
    /**
     * Number of buckets the sliding window is divided into.
     *
     * <p>The window duration is this count multiplied by the per-bucket duration; the
     * ring index advances and wraps modulo this value.
     */
    private static final int BUCKET_COUNT = 10;

    /**
     * Scale converting the per-millisecond ratio into a per-second rate.
     */
    private static final long RATE_SCALE_MILLIS = 1000L;

    /**
     * Per-bucket accumulated amounts, indexed cyclically by the head and tail cursors.
     *
     * <p>The buckets within the live span {@code [tail, head]} carry the amounts added
     * during the window; buckets evicted by advancing the head are zeroed.
     */
    private final long[] buckets;

    /**
     * Duration of a single bucket in milliseconds.
     *
     * <p>The full window spans {@link #BUCKET_COUNT} times this value; an elapsed span
     * is converted to a bucket count by integer division against it.
     */
    private final long bucketMillis;

    /**
     * Minimum number of filled buckets required before {@link #rate(long)} returns a
     * non-zero value.
     *
     * <p>Until this many buckets have accumulated, the running total is considered too
     * sparse to yield a meaningful rate and the calculator reports zero.
     */
    private final int minBuckets;

    /**
     * Timestamp in milliseconds at which the current head bucket began, or {@code 0}
     * when the calculator is uninitialized.
     *
     * <p>Seeded by the first {@link #update(long, long)} and advanced in whole bucket
     * durations as time elapses.
     */
    private long windowStartMillis;

    /**
     * Sum of the amounts held across the live buckets.
     *
     * <p>Increased when an amount is added to the head bucket and decreased by the
     * contents of each bucket evicted as the window scrolls.
     */
    private long runningTotal;

    /**
     * Index of the current head bucket, into which the next amount is accumulated.
     *
     * <p>Advances modulo {@link #BUCKET_COUNT} as the window scrolls forward in time.
     */
    private int headIndex;

    /**
     * Index of the oldest live bucket in the window.
     *
     * <p>Trails the head; the span from this index to the head, taken cyclically, gives
     * the count of filled buckets.
     */
    private int tailIndex;

    /**
     * Constructs a rate calculator with the given bucket duration and fill threshold.
     *
     * <p>The window covers {@link #BUCKET_COUNT} times {@code bucketMillis}. The
     * calculator starts uninitialized with an empty window; the first
     * {@link #update(long, long)} seeds the window head.
     *
     * @param bucketMillis the duration of one bucket in milliseconds; must be positive
     * @param minBuckets   the minimum number of filled buckets before {@link #rate(long)}
     *                     yields a non-zero value; clamped into {@code [1, 10]}
     * @throws IllegalArgumentException if {@code bucketMillis} is not positive
     */
    public RateCalculator(long bucketMillis, int minBuckets) {
        if (bucketMillis <= 0) {
            throw new IllegalArgumentException("bucketMillis must be positive: " + bucketMillis);
        }
        this.buckets = new long[BUCKET_COUNT];
        this.bucketMillis = bucketMillis;
        this.minBuckets = Math.clamp(minBuckets, 1, BUCKET_COUNT);
        this.windowStartMillis = 0;
        this.runningTotal = 0;
        this.headIndex = 0;
        this.tailIndex = 0;
    }

    /**
     * Adds an amount to the window at the given time, scrolling the window forward
     * first.
     *
     * <p>When uninitialized, or when the elapsed span since the window head reaches the
     * full window, the ring is reset so the amount opens a fresh window at
     * {@code nowMillis}. Otherwise the head advances by the number of whole bucket
     * durations elapsed, each newly vacated bucket is zeroed and subtracted from the
     * running total, and the window head moves forward by the same number of bucket
     * durations. The amount is then added to the head bucket and the running total.
     *
     * @param nowMillis the current time in milliseconds; must not precede the previous
     *                  call's time
     * @param amount    the amount to accumulate into the current bucket
     */
    public void update(long nowMillis, long amount) {
        if (windowStartMillis == 0) {
            reset(nowMillis);
        } else {
            var elapsed = nowMillis - windowStartMillis;
            var advance = elapsed / bucketMillis;
            if (advance >= BUCKET_COUNT) {
                reset(nowMillis);
            } else if (advance > 0) {
                for (var i = 0; i < advance; i++) {
                    headIndex = (headIndex + 1) % BUCKET_COUNT;
                    runningTotal -= buckets[headIndex];
                    buckets[headIndex] = 0;
                    if (headIndex == tailIndex) {
                        tailIndex = (tailIndex + 1) % BUCKET_COUNT;
                    }
                }
                windowStartMillis += advance * bucketMillis;
            }
        }
        buckets[headIndex] += amount;
        runningTotal += amount;
    }

    /**
     * Returns the amount-per-second rate over the window at the given time, or
     * {@code 0} when the window is too sparse or fully drained.
     *
     * <p>Computes the count of filled buckets as the cyclic span from the tail to the
     * head plus one. When that count is below the configured minimum, or the elapsed
     * span since the window head is exactly the full window, the rate is {@code 0}.
     * Otherwise the rate is the running total scaled to a per-second figure over the
     * window duration. This method does not scroll the window; pair it with
     * {@link #update(long, long)} to keep the window current.
     *
     * @param nowMillis the current time in milliseconds, used to detect a fully drained
     *                  window
     * @return the per-second rate, or {@code 0} if not yet computable
     */
    public long rate(long nowMillis) {
        if (windowStartMillis == 0) {
            return 0;
        }
        var filled = ((headIndex - tailIndex + BUCKET_COUNT) % BUCKET_COUNT) + 1;
        if (filled < minBuckets) {
            return 0;
        }
        var elapsed = nowMillis - windowStartMillis;
        var windowMillis = (long) BUCKET_COUNT * bucketMillis;
        if (elapsed == windowMillis) {
            return 0;
        }
        return runningTotal * RATE_SCALE_MILLIS / windowMillis;
    }

    /**
     * Returns the running total of amounts currently held across the live buckets.
     *
     * @return the sum of the live buckets
     */
    public long total() {
        return runningTotal;
    }

    /**
     * Returns the calculator to its uninitialized state so it can be reused in place.
     *
     * <p>Zeroes every bucket and the running total, collapses the head and tail to the same index, and
     * marks the window uninitialized so the next {@link #update(long, long)} seeds a fresh window head,
     * exactly as a newly constructed instance would. Reusing an existing calculator through this method
     * avoids reallocating one on a stream reset.
     */
    public void clear() {
        reset(0L);
    }

    /**
     * Clears the ring and opens a fresh window starting at the given time.
     *
     * <p>Zeroes every bucket and the running total, collapses the head and tail to the
     * same index, and seeds the window head at {@code nowMillis}. Used both for the
     * first update and whenever the elapsed span has exceeded the full window so that
     * scrolling bucket by bucket would be wasteful.
     *
     * @param nowMillis the time at which the new window begins, in milliseconds
     */
    private void reset(long nowMillis) {
        Arrays.fill(buckets, 0L);
        runningTotal = 0;
        headIndex = 0;
        tailIndex = 0;
        windowStartMillis = nowMillis;
    }
}
