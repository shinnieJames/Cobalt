package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Configuration for a {@link DataChannel} that the local peer is
 * about to {@link DataChannelTransport#open opening}, mirroring the
 * fields of W3C {@code RTCDataChannelInit} and the parameters carried
 * by an RFC 8832 {@code DATA_CHANNEL_OPEN} message.
 *
 * <p>Reliability is mutually exclusive: at most one of
 * {@link #maxRetransmits()} and {@link #maxLifetimeMs()} may be set.
 * If both are empty the channel is fully reliable; otherwise
 * partial-reliability semantics apply per RFC 3758/8832.
 *
 * <p>If {@link #negotiated()} is {@code true} the application has
 * agreed on the {@link #streamId()} out-of-band and no DCEP
 * handshake will be sent or expected — both peers create their
 * {@link DataChannel} in {@link DataChannelState#OPEN} immediately.
 *
 * @param ordered        whether messages must be delivered in send
 *                       order
 * @param maxRetransmits the maximum number of retransmissions before
 *                       a message is dropped, or empty for fully
 *                       reliable
 * @param maxLifetimeMs  the maximum lifetime of a message in
 *                       milliseconds before it is dropped, or empty
 *                       for fully reliable
 * @param protocol       the application-level subprotocol identifier;
 *                       empty string if unused
 * @param negotiated     whether the channel is created with the
 *                       stream-id agreed out-of-band; suppresses DCEP
 * @param streamId       the agreed-upon stream id when
 *                       {@code negotiated} is {@code true}; ignored
 *                       otherwise
 * @param priority       the {@code priority} field encoded into the
 *                       DCEP {@code DATA_CHANNEL_OPEN}; defaults to
 *                       {@value #DEFAULT_PRIORITY} per RFC 8831 §6.4
 */
public record DataChannelOptions(
        boolean ordered,
        OptionalInt maxRetransmits,
        OptionalInt maxLifetimeMs,
        String protocol,
        boolean negotiated,
        OptionalInt streamId,
        int priority
) {
    /**
     * RFC 8831 §6.4 default priority value: "below normal".
     */
    public static final int DEFAULT_PRIORITY = 256;

    /**
     * Compact constructor — validates the mutually-exclusive
     * reliability fields and the negotiated/stream-id pairing.
     */
    public DataChannelOptions {
        Objects.requireNonNull(maxRetransmits, "maxRetransmits cannot be null");
        Objects.requireNonNull(maxLifetimeMs, "maxLifetimeMs cannot be null");
        Objects.requireNonNull(protocol, "protocol cannot be null");
        Objects.requireNonNull(streamId, "streamId cannot be null");
        if (maxRetransmits.isPresent() && maxLifetimeMs.isPresent()) {
            throw new IllegalArgumentException(
                    "maxRetransmits and maxLifetimeMs are mutually exclusive");
        }
        if (maxRetransmits.isPresent() && maxRetransmits.getAsInt() < 0) {
            throw new IllegalArgumentException(
                    "maxRetransmits cannot be negative");
        }
        if (maxLifetimeMs.isPresent() && maxLifetimeMs.getAsInt() < 0) {
            throw new IllegalArgumentException(
                    "maxLifetimeMs cannot be negative");
        }
        if (negotiated && streamId.isEmpty()) {
            throw new IllegalArgumentException(
                    "negotiated channels must specify a streamId");
        }
        if (streamId.isPresent() && (streamId.getAsInt() < 0 || streamId.getAsInt() > 65534)) {
            throw new IllegalArgumentException(
                    "streamId out of range [0, 65534]: " + streamId.getAsInt());
        }
        if (priority < 0 || priority > 0xFFFF) {
            throw new IllegalArgumentException(
                    "priority out of range [0, 65535]: " + priority);
        }
    }

    /**
     * Returns the default options: ordered, fully reliable, empty
     * protocol, in-band negotiated, default priority. Suitable for
     * the typical "send a JSON blob over the channel" use case.
     *
     * @return the default options
     */
    public static DataChannelOptions reliable() {
        return new DataChannelOptions(true, OptionalInt.empty(), OptionalInt.empty(),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns reliable, but unordered, options.
     *
     * @return reliable + unordered
     */
    public static DataChannelOptions reliableUnordered() {
        return new DataChannelOptions(false, OptionalInt.empty(), OptionalInt.empty(),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns options for a partially-reliable channel that gives up
     * after {@code maxRetransmits} retransmits.
     *
     * @param maxRetransmits the maximum number of retransmissions
     *                       (>= 0)
     * @param ordered        whether to preserve ordering
     * @return partial-reliability-by-retransmit options
     */
    public static DataChannelOptions partialReliableByRetransmit(int maxRetransmits, boolean ordered) {
        return new DataChannelOptions(ordered, OptionalInt.of(maxRetransmits), OptionalInt.empty(),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns options for a partially-reliable channel that gives up
     * after a wall-clock lifetime in milliseconds.
     *
     * @param maxLifetimeMs the maximum message lifetime (>= 0 ms)
     * @param ordered       whether to preserve ordering
     * @return partial-reliability-by-lifetime options
     */
    public static DataChannelOptions partialReliableByLifetime(int maxLifetimeMs, boolean ordered) {
        return new DataChannelOptions(ordered, OptionalInt.empty(), OptionalInt.of(maxLifetimeMs),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns a copy of these options with {@link #streamId()} set —
     * implies {@link #negotiated()} is {@code true} and the peer is
     * expected to create its side with the matching stream id.
     *
     * @param streamId the agreed-upon stream id
     * @return the negotiated copy
     */
    public DataChannelOptions withNegotiatedStreamId(int streamId) {
        return new DataChannelOptions(ordered, maxRetransmits, maxLifetimeMs,
                protocol, true, OptionalInt.of(streamId), priority);
    }

    /**
     * Returns a copy of these options with {@link #protocol()} set.
     *
     * @param protocol the subprotocol identifier
     * @return the copy
     */
    public DataChannelOptions withProtocol(String protocol) {
        return new DataChannelOptions(ordered, maxRetransmits, maxLifetimeMs,
                protocol, negotiated, streamId, priority);
    }

    /**
     * Returns a copy of these options with {@link #priority()} set.
     *
     * @param priority the DCEP priority field
     * @return the copy
     */
    public DataChannelOptions withPriority(int priority) {
        return new DataChannelOptions(ordered, maxRetransmits, maxLifetimeMs,
                protocol, negotiated, streamId, priority);
    }
}
