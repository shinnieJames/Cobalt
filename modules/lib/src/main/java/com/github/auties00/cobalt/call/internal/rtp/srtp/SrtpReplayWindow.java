package com.github.auties00.cobalt.call.internal.rtp.srtp;

/**
 * Tracks the highest authenticated packet index seen and a bitmap of the indices preceding it
 * to reject replayed packets.
 *
 * <p>The window implements the sliding-window replay detector of RFC 3711 section 3.3.2 with a
 * width of 64 indices. An index is acceptable when it is strictly greater than the current top,
 * or when it falls within the window and its bit has not yet been set. {@link #check(long)} is
 * non-mutating and only reports whether an index would be accepted; {@link #update(long)} records
 * that an index has been authenticated and integrates it into the window, advancing the top or
 * setting the corresponding bit. Callers must authenticate the packet between {@code check} and
 * {@code update} so that a forged or tampered packet cannot poison the window.
 *
 * <p>The same instance serves both SRTP (48-bit packet index) and SRTCP (31-bit index); the
 * {@code long} index space accommodates either width.
 */
final class SrtpReplayWindow {
    /**
     * Holds the width of the bitmap, in indices.
     *
     * <p>This implementation fixes the width at 64, matching the minimum recommended by
     * RFC 3711 section 3.3.2 and allowing the bitmap to occupy a single {@code long}.
     */
    private static final int WINDOW_SIZE = 64;

    /**
     * Holds the bitmap of the most recent {@link #WINDOW_SIZE} indices ending at {@link #top}
     * inclusive.
     *
     * <p>Bit 0 represents {@code top}; bit {@code i} represents {@code top - i}. A set bit means
     * the corresponding index has already been accepted.
     */
    private long bits;

    /**
     * Holds the highest authenticated index accepted so far, or {@code -1} when no packet has
     * been accepted yet.
     */
    private long top = -1L;

    /**
     * Reports whether the given index would be accepted as a non-replayed packet.
     *
     * <p>An index is acceptable when no packet has been accepted yet, when it is strictly greater
     * than {@link #top}, or when it lies within the window and its bit is clear. An index older
     * than the window edge, or one whose bit is already set, is rejected. This method does not
     * mutate any state.
     *
     * @param index the candidate 48-bit (SRTP) or 31-bit (SRTCP) index
     * @return {@code true} if the index would be accepted, {@code false} if it is a replay or
     *         falls before the window
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
     * Records that an authenticated packet at the given index has been accepted.
     *
     * <p>When the index exceeds {@link #top}, the window slides forward by the gap and the new
     * top bit is set; a gap of at least {@link #WINDOW_SIZE} clears the bitmap entirely. When the
     * index falls within the existing window, only its bit is set. Callers must have authenticated
     * the packet before calling this method.
     *
     * @param index the authenticated index to integrate into the window
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
