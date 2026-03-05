package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.type.WamChannel;

/**
 * A base interface for all WhatsApp Metrics (WAM) event types, providing
 * methods to calculate the binary-encoded size, write the event into a
 * WAM buffer, and query event metadata.
 *
 * <p>Each {@code @WamEvent}-annotated interface extends this interface.
 * The annotation processor generates an implementation class that
 * provides high-performance, zero-reflection implementations of all
 * methods using hardcoded literal values from the annotation and direct
 * calls to
 * {@link com.github.auties00.cobalt.wam.binary.WamEventEncoder WamEventEncoder}.
 *
 * @see com.github.auties00.cobalt.wam.annotation.WamEvent
 */
public interface WamEventSpec {
    /**
     * Returns the numeric event identifier assigned by WhatsApp.
     *
     * @return the event id
     */
    int id();

    /**
     * Returns the transport channel for this event.
     *
     * @return the channel
     */
    WamChannel channel();

    /**
     * Returns the sampling weight for alpha (internal) builds.
     *
     * @return the alpha build sampling weight
     */
    int alphaWeight();

    /**
     * Returns the sampling weight for beta builds.
     *
     * @return the beta build sampling weight
     */
    int betaWeight();

    /**
     * Returns the sampling weight for release (production) builds.
     *
     * @return the release build sampling weight
     */
    int releaseWeight();

    /**
     * Returns the private-statistics identifier for events on the
     * {@link WamChannel#PRIVATE} channel, or {@code -1} if not
     * applicable.
     *
     * @return the private stats id, or {@code -1}
     */
    int privateStatsId();

    /**
     * Marks this event as committed and returns whether this is the
     * first commit.
     *
     * <p>If this method returns {@code false}, the event has already
     * been committed and the caller should discard the duplicate. This
     * matches the redundant-commit guard in WhatsApp Web's
     * {@code WAWebWam.commit()}.
     *
     * @return {@code true} if this is the first commit, {@code false}
     *         if already committed
     */
    boolean markCommitted();

    /**
     * Returns the number of bytes required to encode this event in the
     * WAM binary protocol using the static release weight.
     *
     * <p>The size includes the event marker (event id and negative
     * weight) and all non-{@code null} field entries.
     *
     * @return the encoded size in bytes
     */
    default int sizeOf() {
        return sizeOf(releaseWeight());
    }

    /**
     * Returns the number of bytes required to encode this event in the
     * WAM binary protocol using the given weight.
     *
     * <p>The size includes the event marker (event id and negative
     * weight) and all non-{@code null} field entries.
     *
     * @param weight the resolved sampling weight to encode
     * @return the encoded size in bytes
     */
    int sizeOf(int weight);

    /**
     * Writes this event into the given output buffer starting at the
     * specified offset, using the static release weight.
     *
     * <p>The caller must ensure that the output array has at least
     * {@code offset + sizeOf()} bytes available.
     *
     * @param output the output byte array
     * @param offset the starting offset within the output array
     * @return the new offset after the last byte written
     */
    default int encode(byte[] output, int offset) {
        return encode(output, offset, releaseWeight());
    }

    /**
     * Writes this event into the given output buffer starting at the
     * specified offset, using the given weight.
     *
     * <p>The caller must ensure that the output array has at least
     * {@code offset + sizeOf(weight)} bytes available.
     *
     * @param output the output byte array
     * @param offset the starting offset within the output array
     * @param weight the resolved sampling weight to encode
     * @return the new offset after the last byte written
     */
    int encode(byte[] output, int offset, int weight);
}
