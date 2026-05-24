package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

/**
 * Lifecycle state of a {@link DataChannel}.
 *
 * <p>Mirrors the four states defined by the WebRTC RTCDataChannel
 * specification (W3C webrtc §6.2). A channel proceeds linearly:
 * {@link #CONNECTING} → {@link #OPEN} → {@link #CLOSING} →
 * {@link #CLOSED}, with terminal {@link #CLOSED}.
 */
public enum DataChannelState {
    /**
     * The channel has been created locally (or a remote
     * {@link DcepMessage.Open DATA_CHANNEL_OPEN} was just received) but
     * has not yet been acknowledged by the peer. Sends are not
     * permitted.
     */
    CONNECTING,
    /**
     * Both sides have exchanged DCEP and the channel is ready for
     * application data. Sends are permitted; the peer may also send to
     * us.
     */
    OPEN,
    /**
     * A local {@link DataChannel#close()} has been requested but the
     * close has not yet been observed by the peer (the SCTP outgoing
     * stream has been reset, but the incoming stream-reset from the
     * peer has not been acknowledged). Sends are no longer permitted.
     */
    CLOSING,
    /**
     * The channel is closed in both directions and the {@code DataChannel}
     * instance is permanently inert. Subsequent calls to
     * {@link DataChannel#close()} are no-ops.
     */
    CLOSED
}
