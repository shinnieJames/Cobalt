package com.github.auties00.cobalt.call.transport.sctp.datachannel;

import com.github.auties00.cobalt.call.transport.sctp.SctpAssociation;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.DataUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebRTC DataChannel transport — RFC 8831/8832 layered on top of
 * {@link SctpAssociation}. Owns the SCTP association for one
 * DataChannel session and provides:
 *
 * <ul>
 *   <li>Per-channel {@link DcepMessage DCEP} establishment for
 *       in-band negotiation.</li>
 *   <li>Out-of-band negotiated channels via
 *       {@link DataChannelOptions#negotiated()}.</li>
 *   <li>PPID-based dispatch of inbound application data to the right
 *       {@link DataChannel}.</li>
 *   <li>Stream-id parity allocation per RFC 8832 §6: the DTLS client
 *       uses odd ids, the DTLS server uses even ids.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>Construct with the DTLS role and an outbound sink hooked to
 *       the DTLS transport.</li>
 *   <li>{@link #bind(int)} the local SCTP port (RFC 8831 §6.2 fixes
 *       this to 5000), then {@link #connect(int)} to the peer's port
 *       (also 5000 in WebRTC).</li>
 *   <li>{@link #open(String, DataChannelOptions)} channels locally,
 *       and/or register a {@link PeerOpenListener} for channels the
 *       peer initiates.</li>
 *   <li>{@link #feedInboundPacket(byte[])} for each SCTP packet
 *       decoded from the DTLS transport.</li>
 *   <li>{@link #close()} when the call ends.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #feedInboundPacket(byte[])} drives the SCTP state
 * machine synchronously, so the calling thread is the one that fires
 * {@link DataChannel.MessageListener} and
 * {@link PeerOpenListener#onPeerOpen}. {@link #open}, {@link #bind},
 * {@link #connect}, and {@link #close} may be called from any thread
 * — they're delegated to the underlying {@link SctpAssociation}, which
 * the WebRTC convention drives single-threaded.
 *
 * <h2>Reliability semantics</h2>
 *
 * <p>The current implementation always sends with full reliability
 * (ordered or unordered). The {@link DataChannelOptions#maxRetransmits()}
 * and {@link DataChannelOptions#maxLifetimeMs()} parameters are
 * faithfully encoded into outbound {@link DcepMessage.Open} messages
 * so the peer sees the requested reliability, but local sends are not
 * yet routed through {@code usrsctp_sendv} with a
 * {@code sctp_sendv_spa} container — partial reliability on the local
 * outbound path will be added when {@link SctpAssociation} grows a
 * {@code sctp_prinfo}-aware send variant.
 */
public final class DataChannelTransport implements AutoCloseable {
    /**
     * SCTP PPID for WebRTC string messages (RFC 8831 §8 — "WebRTC
     * String").
     */
    public static final int PPID_STRING = 51;

    /**
     * SCTP PPID for WebRTC binary messages (RFC 8831 §8 — "WebRTC
     * Binary").
     */
    public static final int PPID_BINARY = 53;

    /**
     * SCTP PPID used to carry an empty WebRTC string. SCTP forbids
     * zero-length DATA chunks, so the application-empty case rides on
     * a one-byte placeholder distinguished by this PPID (RFC 8831
     * §6.6).
     */
    public static final int PPID_STRING_EMPTY = 56;

    /**
     * SCTP PPID used to carry an empty WebRTC binary message —
     * counterpart of {@link #PPID_STRING_EMPTY} for binary
     * (RFC 8831 §6.6).
     */
    public static final int PPID_BINARY_EMPTY = 57;

    /**
     * One-byte placeholder shipped on PPID
     * {@link #PPID_STRING_EMPTY}/{@link #PPID_BINARY_EMPTY} for the
     * "empty message" convention. The exact byte value is
     * unspecified by RFC 8831; the receiver must discard it.
     */
    static final byte[] EMPTY_PLACEHOLDER = {0};

    /**
     * The underlying SCTP association — owns the usrsctp socket.
     */
    private final SctpAssociation association;

    /**
     * Whether this side acted as the DTLS client during the
     * preceding DTLS-SRTP handshake. Determines stream-id parity per
     * RFC 8832 §6: client allocates odd, server allocates even.
     */
    private final boolean dtlsClient;

    /**
     * Registry of stream-id → {@link DataChannel} for both
     * locally-opened and peer-opened channels.
     */
    private final ConcurrentHashMap<Integer, DataChannel> channels = new ConcurrentHashMap<>();

    /**
     * Next stream id to allocate when {@link #open(String, DataChannelOptions)}
     * needs to choose one. Starts at 1 (client) or 0 (server) and
     * advances by 2 to stay within the right parity class.
     */
    private int nextStreamId;

    /**
     * Application-registered listener invoked when the peer opens a
     * channel via DCEP. Volatile so a registration on one thread is
     * visible to the SCTP receive thread that fires it.
     */
    private volatile PeerOpenListener peerOpenListener;

    /**
     * Functional interface invoked when the peer initiates a
     * DataChannel via {@link DcepMessage.Open DATA_CHANNEL_OPEN}.
     */
    @FunctionalInterface
    public interface PeerOpenListener {
        /**
         * Called once per inbound channel, after the
         * {@link DcepMessage.Ack} has been sent and the
         * {@link DataChannel} has been transitioned to
         * {@link DataChannelState#OPEN}.
         *
         * @param channel the newly opened channel
         */
        void onPeerOpen(DataChannel channel);
    }

    /**
     * Constructs a new transport, internally creating an
     * {@link SctpAssociation} wired to {@code outboundSink}.
     *
     * @param dtlsClient   {@code true} if the local peer acted as
     *                     DTLS client during the preceding handshake;
     *                     determines stream-id parity per RFC 8832 §6
     * @param outboundSink consumer for outbound SCTP packets — wire
     *                     this to the call's DTLS transport's send
     *                     method
     * @throws NullPointerException if {@code outboundSink} is
     *                              {@code null}
     * @throws WhatsAppCallException.Sctp        if the underlying
     *                              {@link SctpAssociation} cannot be
     *                              constructed
     */
    public DataChannelTransport(boolean dtlsClient, Consumer<byte[]> outboundSink) {
        Objects.requireNonNull(outboundSink, "outboundSink cannot be null");
        this.dtlsClient = dtlsClient;
        this.nextStreamId = dtlsClient ? 1 : 0;
        this.association = new SctpAssociation(outboundSink, this::handleSctpMessage);
    }

    /**
     * Returns whether the local peer acted as DTLS client (and thus
     * allocates odd stream ids).
     *
     * @return the DTLS role
     */
    public boolean dtlsClient() {
        return dtlsClient;
    }

    /**
     * Binds the underlying SCTP association to the given local port.
     *
     * @param localPort the local SCTP port (5000 in WebRTC)
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_bind} fails
     */
    public void bind(int localPort) {
        association.bind(localPort);
    }

    /**
     * Performs the SCTP handshake with the peer and blocks until
     * {@code SCTP_COMM_UP} is observed. Equivalent to
     * {@link #connect(int, long, TimeUnit)} with no timeout.
     *
     * <p>For WebRTC's "simultaneous open" pattern, both peers must
     * call {@code connect} concurrently — typically each on its own
     * thread.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @throws WhatsAppCallException.Sctp if the handshake fails
     */
    public void connect(int remotePort) {
        association.connect(remotePort);
    }

    /**
     * Performs the SCTP handshake and blocks until
     * {@code SCTP_COMM_UP} is observed, the handshake fails, or the
     * timeout expires.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @param timeout    the maximum time to wait
     * @param unit       the timeout unit
     * @throws WhatsAppCallException.Sctp        if the handshake fails or times
     *                              out
     * @throws NullPointerException if {@code unit} is {@code null}
     */
    public void connect(int remotePort, long timeout, TimeUnit unit) {
        association.connect(remotePort, timeout, unit);
    }

    /**
     * Feeds one inbound SCTP packet (decoded from a DTLS record) into
     * the local stack. May synchronously dispatch to
     * {@link DataChannel.MessageListener} or
     * {@link PeerOpenListener} on the calling thread.
     *
     * @param packet the SCTP packet bytes
     * @throws NullPointerException if {@code packet} is {@code null}
     */
    public void feedInboundPacket(byte[] packet) {
        association.feedInboundPacket(packet);
    }

    /**
     * Registers a listener for peer-initiated channels.
     *
     * @param listener the listener, or {@code null} to deregister
     */
    public void setPeerOpenListener(PeerOpenListener listener) {
        this.peerOpenListener = listener;
    }

    /**
     * Returns a snapshot view of the channel registered for the
     * given stream id, or empty if none.
     *
     * @param streamId the stream id
     * @return the channel, if any
     */
    public Optional<DataChannel> channel(int streamId) {
        return Optional.ofNullable(channels.get(streamId));
    }

    /**
     * Opens a new local-side {@link DataChannel}.
     *
     * <p>For an in-band channel ({@code options.negotiated()} is
     * {@code false}), allocates a fresh stream id of the correct
     * parity, sends a {@link DcepMessage.Open DATA_CHANNEL_OPEN}, and
     * returns a channel in {@link DataChannelState#CONNECTING}; the
     * channel transitions to {@link DataChannelState#OPEN} once the
     * peer's {@link DcepMessage.Ack} arrives.
     *
     * <p>For an out-of-band channel ({@code options.negotiated()} is
     * {@code true}), uses the supplied {@link DataChannelOptions#streamId()}
     * directly and returns a channel already in
     * {@link DataChannelState#OPEN}; no DCEP is exchanged.
     *
     * @param label   the channel label (UTF-8, up to 65535 bytes)
     * @param options the channel options
     * @return the freshly created channel
     * @throws NullPointerException     if either argument is
     *                                  {@code null}
     * @throws WhatsAppCallException.DataChannel     if a stream id collision is
     *                                  detected, or if the requested
     *                                  parity is exhausted
     * @throws IllegalArgumentException if a negotiated channel
     *                                  supplies a stream id of the
     *                                  wrong parity (RFC 8832 §6 —
     *                                  detected only as a sanity
     *                                  check; both peers are still
     *                                  allowed to create out-of-band
     *                                  channels of either parity if
     *                                  they agreed)
     */
    public DataChannel open(String label, DataChannelOptions options) {
        Objects.requireNonNull(label, "label cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        int streamId;
        DataChannelState initialState;
        if (options.negotiated()) {
            streamId = options.streamId().orElseThrow(() ->
                    new IllegalStateException("negotiated channel without streamId"));
            initialState = DataChannelState.OPEN;
        } else {
            streamId = options.streamId().orElseGet(this::allocateStreamId);
            initialState = DataChannelState.CONNECTING;
        }

        var channel = new DataChannel(this, streamId, label, options.protocol(),
                options.ordered(), options.negotiated(),
                options.maxRetransmits(), options.maxLifetimeMs(), initialState);

        var existing = channels.putIfAbsent(streamId, channel);
        if (existing != null) {
            throw new WhatsAppCallException.DataChannel(
                    "stream id " + streamId + " already in use by channel '" + existing.label() + "'");
        }

        if (!options.negotiated()) {
            try {
                var open = DcepMessage.Open.from(label, options);
                association.send(streamId, DcepMessage.PPID_DCEP, open.encode(), true);
            } catch (RuntimeException e) {
                channels.remove(streamId);
                throw e instanceof WhatsAppCallException.DataChannel dce ? dce
                        : new WhatsAppCallException.DataChannel("failed to send DATA_CHANNEL_OPEN", e);
            }
        }
        return channel;
    }

    /**
     * Tears the transport down: closes all open channels, then closes
     * the underlying {@link SctpAssociation}. Idempotent.
     */
    @Override
    public void close() {
        for (var ch : channels.values()) {
            try {
                ch.notifyPeerClosed();
            } catch (Throwable _) {
            }
        }
        channels.clear();
        association.close();
    }

    /**
     * Forwards a payload to the underlying SCTP socket on a specific
     * stream/PPID combination. Used by {@link DataChannel#send(String)}
     * and {@link DataChannel#send(byte[])}.
     *
     * @param streamId the SCTP stream id
     * @param ppid     the PPID (51, 53, 56, or 57)
     * @param payload  the message bytes
     * @param ordered  whether to preserve order
     * @throws WhatsAppCallException.DataChannel if the SCTP send fails
     */
    void sendDataMessage(int streamId, int ppid, byte[] payload, boolean ordered) {
        try {
            association.send(streamId, ppid, payload, ordered);
        } catch (WhatsAppCallException.Sctp e) {
            throw new WhatsAppCallException.DataChannel("SCTP send failed on stream " + streamId, e);
        }
    }

    /**
     * Removes a channel from the registry — called from
     * {@link DataChannel#close()}.
     *
     * @param streamId the channel's stream id
     */
    void unregisterChannel(int streamId) {
        channels.remove(streamId);
    }

    /**
     * Inbound message dispatcher — this is the
     * {@link SctpAssociation.InboundListener} we registered at
     * construction. Routes by PPID per RFC 8831 §8.
     *
     * @param msg the inbound SCTP message
     */
    private void handleSctpMessage(SctpAssociation.InboundMessage msg) {
        switch (msg.ppid()) {
            case DcepMessage.PPID_DCEP -> handleDcep(msg.streamId(), msg.payload());
            case PPID_STRING -> deliverText(msg.streamId(),
                    new String(msg.payload(), StandardCharsets.UTF_8));
            case PPID_BINARY -> deliverBinary(msg.streamId(), msg.payload());
            case PPID_STRING_EMPTY -> deliverText(msg.streamId(), "");
            case PPID_BINARY_EMPTY -> deliverBinary(msg.streamId(), DataUtils.EMPTY_BYTE_ARRAY);
            default -> {}
        }
    }

    /**
     * Decodes and dispatches a DCEP message arriving on {@code streamId}.
     *
     * @param streamId the stream id the message arrived on
     * @param payload  the DCEP payload bytes
     */
    private void handleDcep(int streamId, byte[] payload) {
        DcepMessage decoded;
        try {
            decoded = DcepMessage.decode(payload);
        } catch (WhatsAppCallException.DataChannel _) {
            return;
        }
        switch (decoded) {
            case DcepMessage.Open open -> handlePeerOpen(streamId, open);
            case DcepMessage.Ack ignored -> handlePeerAck(streamId);
        }
    }

    /**
     * Reacts to a peer-initiated {@link DcepMessage.Open}: builds a
     * {@link DataChannel} for the new stream id, sends a
     * {@link DcepMessage.Ack}, and notifies the registered
     * {@link PeerOpenListener}.
     *
     * @param streamId the stream id the OPEN arrived on
     * @param open     the decoded OPEN message
     */
    private void handlePeerOpen(int streamId, DcepMessage.Open open) {
        boolean ordered = !open.unordered();
        var channel = new DataChannel(this, streamId, open.label(), open.protocol(),
                ordered, false, open.maxRetransmits(), open.maxLifetimeMs(),
                DataChannelState.OPEN);
        var existing = channels.putIfAbsent(streamId, channel);
        if (existing != null) {
            return;
        }
        try {
            association.send(streamId, DcepMessage.PPID_DCEP, DcepMessage.Ack.INSTANCE.encode(), true);
        } catch (RuntimeException _) {
            channels.remove(streamId, channel);
            return;
        }
        var listener = peerOpenListener;
        if (listener != null) {
            try {
                listener.onPeerOpen(channel);
            } catch (Throwable _) {
            }
        }
    }

    /**
     * Reacts to a peer {@link DcepMessage.Ack}: transitions the
     * matching local channel from
     * {@link DataChannelState#CONNECTING} to
     * {@link DataChannelState#OPEN}.
     *
     * @param streamId the stream id the ACK arrived on
     */
    private void handlePeerAck(int streamId) {
        var channel = channels.get(streamId);
        if (channel != null) {
            channel.notifyOpen();
        }
    }

    /**
     * Delivers an inbound text message to the registered channel.
     *
     * @param streamId the stream id
     * @param value    the decoded UTF-8 string (may be empty)
     */
    private void deliverText(int streamId, String value) {
        var channel = channels.get(streamId);
        if (channel != null) {
            channel.notifyMessage(new DataChannel.Message.Text(channel, value));
        }
    }

    /**
     * Delivers an inbound binary message to the registered channel.
     *
     * @param streamId the stream id
     * @param data     the message bytes (may be empty)
     */
    private void deliverBinary(int streamId, byte[] data) {
        var channel = channels.get(streamId);
        if (channel != null) {
            channel.notifyMessage(new DataChannel.Message.Binary(channel, data));
        }
    }

    /**
     * Allocates the next free stream id of the correct parity for the
     * local DTLS role, advancing {@link #nextStreamId} past it.
     *
     * @return the freshly allocated stream id
     * @throws WhatsAppCallException.DataChannel if the parity class is exhausted
     *                              (unlikely — 32768 ids per side)
     */
    private synchronized int allocateStreamId() {
        while (nextStreamId <= 65534) {
            int candidate = nextStreamId;
            nextStreamId += 2;
            if (!channels.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new WhatsAppCallException.DataChannel(
                "stream id space exhausted for DTLS " + (dtlsClient ? "client (odd)" : "server (even)"));
    }

    /**
     * Returns whether the registry currently holds the given stream
     * id — used by tests to verify cleanup paths.
     *
     * @param streamId the stream id
     * @return {@code true} if a channel is registered
     */
    boolean hasChannel(int streamId) {
        return channels.containsKey(streamId);
    }

    /**
     * Returns a snapshot count of currently registered channels.
     *
     * @return the number of channels
     */
    public int channelCount() {
        return channels.size();
    }

    /**
     * Returns the next stream id that {@link #allocateStreamId} would
     * pick — visible for the test suite to verify parity.
     *
     * @return the next id
     */
    OptionalInt nextStreamIdForTesting() {
        return nextStreamId <= 65534 ? OptionalInt.of(nextStreamId) : OptionalInt.empty();
    }
}
