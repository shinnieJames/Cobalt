package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

/**
 * Enumerates the lifecycle states of a {@link DataChannel}.
 *
 * <p>The four states mirror the {@code RTCDataChannel.readyState} values defined by the W3C
 * WebRTC specification. A channel advances strictly forward through
 * {@link #CONNECTING}, {@link #OPEN}, {@link #CLOSING}, {@link #CLOSED}, with {@link #CLOSED}
 * being terminal; no transition ever moves backward. The transition is published atomically on
 * the channel's state holder so concurrent readers observe a single consistent value.
 */
public enum DataChannelState {
    /**
     * Indicates the channel exists but is not yet usable for application data.
     *
     * <p>A locally opened in-band channel starts here and remains until the peer's
     * {@link DcepMessage.Ack} arrives; a channel created from a freshly received
     * {@link DcepMessage.Open} also passes through this state momentarily before its
     * acknowledgement is sent. Calls to {@link DataChannel#send(String)} and
     * {@link DataChannel#send(byte[])} are rejected while the channel is in this state.
     */
    CONNECTING,
    /**
     * Indicates both sides have completed (or skipped, for negotiated channels) the DCEP
     * handshake and the channel can carry application data in either direction.
     *
     * <p>Sends are permitted and inbound messages are dispatched to the registered
     * {@link DataChannel.MessageListener}.
     */
    OPEN,
    /**
     * Indicates a local {@link DataChannel#close()} has been requested but the peer has not yet
     * observed the close.
     *
     * <p>The local SCTP outgoing stream has been reset while the matching incoming stream-reset
     * from the peer is still outstanding. Sends are no longer permitted.
     */
    CLOSING,
    /**
     * Indicates the channel is closed in both directions and permanently inert.
     *
     * <p>Further calls to {@link DataChannel#close()} are no-ops and the {@link DataChannel}
     * instance can no longer send or receive.
     */
    CLOSED
}
