package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Captures the configuration for a {@link DataChannel} the local peer is about to
 * {@linkplain DataChannelTransport#open(String, DataChannelOptions) open}.
 *
 * <p>The fields mirror W3C {@code RTCDataChannelInit} and the parameters carried by an
 * RFC 8832 {@code DATA_CHANNEL_OPEN} message. Reliability is mutually exclusive: at most one of
 * {@link #maxRetransmits()} and {@link #maxLifetimeMs()} may be present. When both are empty the
 * channel is fully reliable; otherwise partial-reliability semantics apply. When
 * {@link #negotiated()} is {@code true} the application has agreed the {@link #streamId()}
 * out-of-band, so no DCEP handshake is sent or expected and both peers create their
 * {@link DataChannel} directly in {@link DataChannelState#OPEN}. The compact constructor enforces
 * these invariants together with the numeric ranges of the stream id, priority, and reliability
 * parameters.
 *
 * @param ordered        whether messages must be delivered in send order
 * @param maxRetransmits the maximum number of retransmissions before a message is dropped, or
 *                       empty for fully reliable
 * @param maxLifetimeMs  the maximum lifetime of a message in milliseconds before it is dropped,
 *                       or empty for fully reliable
 * @param protocol       the application-level subprotocol identifier, or the empty string when
 *                       unused
 * @param negotiated     whether the channel is created with the stream id agreed out-of-band,
 *                       which suppresses DCEP
 * @param streamId       the agreed stream id, required when {@code negotiated} is {@code true}
 *                       and ignored otherwise
 * @param priority       the {@code priority} field encoded into the DCEP {@code DATA_CHANNEL_OPEN},
 *                       defaulting to {@value #DEFAULT_PRIORITY}
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
     * The default DCEP priority value, denoting "below normal".
     *
     * @implNote This implementation uses {@code 256}, the "below normal" priority defined by
     * RFC 8831 as the default for a data channel.
     */
    public static final int DEFAULT_PRIORITY = 256;

    /**
     * Validates the option invariants, rejecting null {@link OptionalInt} or string fields, both
     * reliability parameters set at once, a negative reliability parameter, a negotiated channel
     * without a stream id, an out-of-range stream id, and an out-of-range priority.
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
     * Returns options for an ordered, fully reliable, in-band channel with an empty protocol and
     * the {@linkplain #DEFAULT_PRIORITY default priority}.
     *
     * <p>These are the typical defaults for sending application payloads such as JSON blobs over
     * the channel.
     *
     * @return the default options
     */
    public static DataChannelOptions reliable() {
        return new DataChannelOptions(true, OptionalInt.empty(), OptionalInt.empty(),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns options for a fully reliable but unordered in-band channel.
     *
     * @return the reliable, unordered options
     */
    public static DataChannelOptions reliableUnordered() {
        return new DataChannelOptions(false, OptionalInt.empty(), OptionalInt.empty(),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns options for a partially reliable channel that gives up on a message after the given
     * number of retransmissions.
     *
     * @param maxRetransmits the maximum number of retransmissions, which must be non-negative
     * @param ordered        whether to preserve message ordering
     * @return the retransmit-limited partial-reliability options
     */
    public static DataChannelOptions partialReliableByRetransmit(int maxRetransmits, boolean ordered) {
        return new DataChannelOptions(ordered, OptionalInt.of(maxRetransmits), OptionalInt.empty(),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns options for a partially reliable channel that gives up on a message after the given
     * wall-clock lifetime in milliseconds.
     *
     * @param maxLifetimeMs the maximum message lifetime in milliseconds, which must be
     *                      non-negative
     * @param ordered       whether to preserve message ordering
     * @return the lifetime-limited partial-reliability options
     */
    public static DataChannelOptions partialReliableByLifetime(int maxLifetimeMs, boolean ordered) {
        return new DataChannelOptions(ordered, OptionalInt.empty(), OptionalInt.of(maxLifetimeMs),
                "", false, OptionalInt.empty(), DEFAULT_PRIORITY);
    }

    /**
     * Returns a copy of these options with the {@link #streamId()} set and {@link #negotiated()}
     * forced to {@code true}.
     *
     * <p>The peer is then expected to create its side of the channel out-of-band with the same
     * stream id, so no DCEP is exchanged.
     *
     * @param streamId the agreed stream id
     * @return the negotiated copy
     */
    public DataChannelOptions withNegotiatedStreamId(int streamId) {
        return new DataChannelOptions(ordered, maxRetransmits, maxLifetimeMs,
                protocol, true, OptionalInt.of(streamId), priority);
    }

    /**
     * Returns a copy of these options with the {@link #protocol()} set.
     *
     * @param protocol the subprotocol identifier
     * @return the copy
     */
    public DataChannelOptions withProtocol(String protocol) {
        return new DataChannelOptions(ordered, maxRetransmits, maxLifetimeMs,
                protocol, negotiated, streamId, priority);
    }

    /**
     * Returns a copy of these options with the {@link #priority()} set.
     *
     * @param priority the DCEP priority field
     * @return the copy
     */
    public DataChannelOptions withPriority(int priority) {
        return new DataChannelOptions(ordered, maxRetransmits, maxLifetimeMs,
                protocol, negotiated, streamId, priority);
    }
}
