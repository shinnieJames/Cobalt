package com.github.auties00.cobalt.call.internal.rtp.srtp;

/**
 * Sliding-window replay detector per RFC 3711 §3.3.2, sized at 64
 * indices. Tracks the highest authenticated packet index seen and a
 * 64-bit bitmap of the indices preceding it.
 *
 * <p>{@link #check} is non-mutating and reports whether the index is
 * acceptable; {@link #update} records that an index has been
 * authenticated and integrates it into the window. Callers must run
 * authentication between {@code check} and {@code update} so a
 * tampered packet cannot poison the window.
 *
 * <p>Used for both SRTP (48-bit packet index) and SRTCP (31-bit
 * index); the long index space accommodates either.
 */
final class SrtpReplayWindow {
    /**
     * Width of the bitmap, in indices. Matches RFC 3711's recommended
     * minimum of 64.
     */
    private static final int WINDOW_SIZE = 64;

    /**
     * Bitmap of the {@link #WINDOW_SIZE} indices ending at
     * {@link #top} (inclusive). Bit 0 represents {@code top}, bit
     * {@code i} represents {@code top - i}.
     */
    private long bits;

    /**
     * Highest authenticated index accepted, or {@code -1} if no
     * packet has been accepted yet.
     */
    private long top = -1L;

    /**
     * Reports whether the given index is acceptable: not seen before
     * and not older than the window edge.
     *
     * @param index the candidate 48-bit (RTP) or 31-bit (SRTCP) index
     * @return {@code true} if the index would be accepted
     */
    boolean check(long index) {
        if (top == -1L) {
            return true;
        }
        if (index > top) {
            return true;
        }
        var delta = top - index;
        if (delta >= WINDOW_SIZE) {
            return false;
        }
        return (bits & (1L << delta)) == 0L;
    }

    /**
     * Records that an authenticated packet at the given index has
     * been accepted, advancing {@link #top} or marking the
     * corresponding bit as needed.
     *
     * @param index the authenticated index
     */
    void update(long index) {
        if (top == -1L) {
            top = index;
            bits = 1L;
            return;
        }
        if (index > top) {
            var shift = index - top;
            if (shift >= WINDOW_SIZE) {
                bits = 1L;
            } else {
                bits = (bits << shift) | 1L;
            }
            top = index;
            return;
        }
        var delta = top - index;
        bits |= (1L << delta);
    }
}
