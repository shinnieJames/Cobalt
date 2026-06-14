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
 * Layers the RFC 8831/8832 WebRTC DataChannel protocol on top of a single
 * {@link SctpAssociation}.
 *
 * <p>The transport owns the SCTP association for one DataChannel session and provides per-channel
 * DCEP establishment for in-band negotiation, out-of-band negotiated channels via
 * {@link DataChannelOptions#negotiated()}, PPID-based dispatch of inbound application data to the
 * right {@link DataChannel}, and stream-id parity allocation in which the DTLS client owns the odd
 * ids and the DTLS server owns the even ids.
 *
 * <p>The intended lifecycle is: construct with the DTLS role and an outbound sink wired to the
 * DTLS transport; {@link #bind(int)} the local SCTP port and {@link #connect(int)} to the peer's
 * port; {@link #open(String, DataChannelOptions)} channels locally and register a
 * {@link PeerOpenListener} for channels the peer initiates; {@link #feedInboundPacket(byte[])} for
 * each SCTP packet decoded from the DTLS transport; and {@link #close()} when the call ends. In
 * WebRTC both the local and the remote port are fixed at {@code 5000}.
 *
 * <p>{@link #feedInboundPacket(byte[])} drives the SCTP state machine synchronously, so the
 * calling thread is the one that fires {@link DataChannel.MessageListener} and
 * {@link PeerOpenListener#onPeerOpen}. {@link #open(String, DataChannelOptions)}, {@link #bind(int)},
 * {@link #connect(int)}, and {@link #close()} may be called from any thread because they delegate
 * to the underlying {@link SctpAssociation}, which the WebRTC convention drives single-threaded.
 *
 * @implNote This implementation always sends application data with full reliability (ordered or
 * unordered). The {@link DataChannelOptions#maxRetransmits()} and
 * {@link DataChannelOptions#maxLifetimeMs()} parameters are faithfully encoded into outbound
 * {@link DcepMessage.Open} messages, so the peer observes the requested reliability, but the local
 * outbound path does not yet honour them.
 */
public final class DataChannelTransport implements AutoCloseable {
    /**
     * The SCTP Payload Protocol Identifier for WebRTC string messages.
     *
     * @implNote This implementation uses {@code 51}, the value assigned to "WebRTC String" by the
     * IANA SCTP PPID registry per RFC 8831.
     */
    public static final int PPID_STRING = 51;

    /**
     * The SCTP Payload Protocol Identifier for WebRTC binary messages.
     *
     * @implNote This implementation uses {@code 53}, the value assigned to "WebRTC Binary" by the
     * IANA SCTP PPID registry per RFC 8831.
     */
    public static final int PPID_BINARY = 53;

    /**
     * The SCTP Payload Protocol Identifier used to carry an empty WebRTC string.
     *
     * <p>SCTP forbids zero-length DATA chunks, so an application-empty string rides on a one-byte
     * placeholder distinguished by this PPID.
     *
     * @implNote This implementation uses {@code 56}, the value assigned to "WebRTC String Empty"
     * by the IANA SCTP PPID registry per RFC 8831.
     */
    public static final int PPID_STRING_EMPTY = 56;

    /**
     * The SCTP Payload Protocol Identifier used to carry an empty WebRTC binary message, the
     * binary counterpart of {@link #PPID_STRING_EMPTY}.
     *
     * @implNote This implementation uses {@code 57}, the value assigned to "WebRTC Binary Empty"
     * by the IANA SCTP PPID registry per RFC 8831.
     */
    public static final int PPID_BINARY_EMPTY = 57;

    /**
     * The one-byte placeholder shipped on {@link #PPID_STRING_EMPTY} and {@link #PPID_BINARY_EMPTY}
     * for the empty-message convention.
     *
     * @implNote This implementation ships a single {@code 0} byte. RFC 8831 leaves the placeholder
     * value unspecified and requires the receiver to discard it, so any one-byte payload would do.
     */
    static final byte[] EMPTY_PLACEHOLDER = {0};

    /**
     * The underlying SCTP association that owns the usrsctp socket.
     */
    private final SctpAssociation association;

    /**
     * Whether the local peer acted as the DTLS client during the preceding DTLS-SRTP handshake.
     *
     * <p>This determines stream-id parity: the DTLS client allocates odd ids and the DTLS server
     * allocates even ids.
     */
    private final boolean dtlsClient;

    /**
     * The registry of stream id to {@link DataChannel} covering both locally opened and
     * peer-opened channels.
     */
    private final ConcurrentHashMap<Integer, DataChannel> channels = new ConcurrentHashMap<>();

    /**
     * The next stream id to allocate when {@link #open(String, DataChannelOptions)} needs one.
     *
     * <p>It starts at {@code 1} for the DTLS client or {@code 0} for the DTLS server and advances
     * by two so it stays within the correct parity class.
     */
    private int nextStreamId;

    /**
     * The listener invoked when the peer opens a channel via DCEP, or {@code null} when none is
     * registered.
     *
     * <p>The field is volatile so a registration on one thread is visible to the SCTP receive
     * thread that fires it.
     */
    private volatile PeerOpenListener peerOpenListener;

    /**
     * Receives a channel the peer initiates via {@link DcepMessage.Open}.
     */
    @FunctionalInterface
    public interface PeerOpenListener {
        /**
         * Handles one peer-initiated channel.
         *
         * <p>The listener fires after the {@link DcepMessage.Ack} has been sent and the channel
         * has been transitioned to {@link DataChannelState#OPEN}, so the channel is already usable
         * when this method runs.
         *
         * @param channel the newly opened channel
         */
        void onPeerOpen(DataChannel channel);
    }

    /**
     * Constructs a transport, internally creating an {@link SctpAssociation} wired to the given
     * outbound sink.
     *
     * @param dtlsClient   {@code true} if the local peer acted as the DTLS client during the
     *                     preceding handshake, which makes it allocate odd stream ids
     * @param outboundSink the consumer for outbound SCTP packets, which the caller wires to the
     *                     call's DTLS transport send path
     * @throws NullPointerException              if {@code outboundSink} is {@code null}
     * @throws WhatsAppCallException.Sctp        if the underlying {@link SctpAssociation} cannot
     *                                           be constructed
     */
    public DataChannelTransport(boolean dtlsClient, Consumer<byte[]> outboundSink) {
        Objects.requireNonNull(outboundSink, "outboundSink cannot be null");
        this.dtlsClient = dtlsClient;
        this.nextStreamId = dtlsClient ? 1 : 0;
        this.association = new SctpAssociation(outboundSink, this::handleSctpMessage);
    }

    /**
     * Returns whether the local peer acted as the DTLS client, and so allocates odd stream ids.
     *
     * @return the DTLS role
     */
    public boolean dtlsClient() {
        return dtlsClient;
    }

    /**
     * Binds the underlying SCTP association to the given local port.
     *
     * @param localPort the local SCTP port, which is {@code 5000} in WebRTC
     * @throws WhatsAppCallException.Sctp if the underlying bind fails
     */
    public void bind(int localPort) {
        association.bind(localPort);
    }

    /**
     * Performs the SCTP handshake with the peer and blocks until the association comes up.
     *
     * <p>This is equivalent to {@link #connect(int, long, TimeUnit)} with no timeout. WebRTC uses
     * a simultaneous-open pattern, so both peers must call connect concurrently, typically each on
     * its own thread.
     *
     * @param remotePort the peer's SCTP port, which is {@code 5000} in WebRTC
     * @throws WhatsAppCallException.Sctp if the handshake fails
     */
    public void connect(int remotePort) {
        association.connect(remotePort);
    }

    /**
     * Performs the SCTP handshake and blocks until the association comes up, the handshake fails,
     * or the timeout expires.
     *
     * @param remotePort the peer's SCTP port, which is {@code 5000} in WebRTC
     * @param timeout    the maximum time to wait
     * @param unit       the unit of {@code timeout}
     * @throws WhatsAppCallException.Sctp if the handshake fails or times out
     * @throws NullPointerException       if {@code unit} is {@code null}
     */
    public void connect(int remotePort, long timeout, TimeUnit unit) {
        association.connect(remotePort, timeout, unit);
    }

    /**
     * Feeds one inbound SCTP packet, decoded from a DTLS record, into the local stack.
     *
     * <p>Processing happens synchronously, so this call may dispatch to a
     * {@link DataChannel.MessageListener} or a {@link PeerOpenListener} on the calling thread.
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
     * <p>Passing {@code null} deregisters any existing listener.
     *
     * @param listener the listener, or {@code null} to deregister
     */
    public void setPeerOpenListener(PeerOpenListener listener) {
        this.peerOpenListener = listener;
    }

    /**
     * Returns the channel registered for the given stream id, or empty if none.
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
     * <p>For an in-band channel, where {@link DataChannelOptions#negotiated()} is {@code false},
     * this allocates a fresh stream id of the correct parity, sends a {@link DcepMessage.Open}, and
     * returns a channel in {@link DataChannelState#CONNECTING}; the channel transitions to
     * {@link DataChannelState#OPEN} once the peer's {@link DcepMessage.Ack} arrives. For an
     * out-of-band channel, where {@link DataChannelOptions#negotiated()} is {@code true}, this uses
     * the supplied {@link DataChannelOptions#streamId()} directly and returns a channel already in
     * {@link DataChannelState#OPEN} with no DCEP exchange.
     *
     * @param label   the channel label, UTF-8 encoded and up to 65535 bytes
     * @param options the channel options
     * @return the freshly created channel
     * @throws NullPointerException              if either argument is {@code null}
     * @throws WhatsAppCallException.DataChannel if the chosen stream id is already in use, the
     *                                           parity class is exhausted, or the
     *                                           {@link DcepMessage.Open} cannot be sent
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
     * Tears the transport down, closing every open channel and then the underlying
     * {@link SctpAssociation}.
     *
     * <p>The call is idempotent. Each channel is notified through {@link DataChannel#notifyPeerClosed()}
     * so its close listener fires before the registry is cleared.
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
     * Forwards a payload to the underlying SCTP socket on a specific stream and PPID.
     *
     * <p>This is the send path for {@link DataChannel#send(String)} and
     * {@link DataChannel#send(byte[])}. An SCTP failure is rethrown as a
     * {@link WhatsAppCallException.DataChannel} chaining the original {@link WhatsAppCallException.Sctp}.
     *
     * @param streamId the SCTP stream id
     * @param ppid     the PPID, one of {@link #PPID_STRING}, {@link #PPID_BINARY},
     *                 {@link #PPID_STRING_EMPTY}, or {@link #PPID_BINARY_EMPTY}
     * @param payload  the message bytes
     * @param ordered  whether to preserve message ordering
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
     * Sends a DataChannel application message under a partial-reliability policy.
     *
     * <p>Routes through {@link SctpAssociation#sendWithPolicy(int, int, byte[], boolean, short, int)}
     * with the supplied policy operand so usrsctp honours the channel's negotiated
     * {@code maxRetransmits} or {@code maxLifetimeMs} on the local outbound path. The previous
     * fully-reliable send path remains available via
     * {@link #sendDataMessage(int, int, byte[], boolean)} for channels that did not negotiate
     * partial reliability.
     *
     * @param streamId the SCTP stream id
     * @param ppid     the PPID, one of {@link #PPID_STRING}, {@link #PPID_BINARY},
     *                 {@link #PPID_STRING_EMPTY}, or {@link #PPID_BINARY_EMPTY}
     * @param payload  the message bytes
     * @param ordered  whether to preserve message ordering
     * @param prPolicy one of the {@code SCTP_PR_SCTP_NONE / TTL / RTX} constants
     * @param prValue  the policy operand: max retransmissions for {@code RTX}, lifetime
     *                 milliseconds for {@code TTL}
     * @throws WhatsAppCallException.DataChannel if the SCTP send fails
     */
    void sendDataMessage(int streamId, int ppid, byte[] payload, boolean ordered,
                         short prPolicy, int prValue) {
        try {
            association.sendWithPolicy(streamId, ppid, payload, ordered, prPolicy, prValue);
        } catch (WhatsAppCallException.Sctp e) {
            throw new WhatsAppCallException.DataChannel("SCTP send (SPA) failed on stream " + streamId, e);
        }
    }

    /**
     * Removes a channel from the registry; called from {@link DataChannel#close()}.
     *
     * @param streamId the channel's stream id
     */
    void unregisterChannel(int streamId) {
        channels.remove(streamId);
    }

    /**
     * Dispatches an inbound SCTP message by PPID.
     *
     * <p>This is the {@link SctpAssociation.InboundListener} registered at construction. DCEP
     * messages are decoded and handled, string and binary PPIDs are delivered to the matching
     * channel, the empty-message PPIDs deliver an empty value, and any other PPID is ignored.
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
     * Decodes and dispatches a DCEP message that arrived on the given stream.
     *
     * <p>A malformed payload is silently dropped. A decoded {@link DcepMessage.Open} drives
     * {@link #handlePeerOpen(int, DcepMessage.Open)} and a {@link DcepMessage.Ack} drives
     * {@link #handlePeerAck(int)}.
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
     * Reacts to a peer-initiated {@link DcepMessage.Open}.
     *
     * <p>This builds a {@link DataChannel} for the new stream id in {@link DataChannelState#OPEN},
     * registers it, sends a {@link DcepMessage.Ack}, and notifies the registered
     * {@link PeerOpenListener}. The open is ignored if the stream id is already registered, and
     * the channel is rolled back if the acknowledgement cannot be sent.
     *
     * @param streamId the stream id the open arrived on
     * @param open     the decoded open message
     */
    private void handlePeerOpen(int streamId, DcepMessage.Open open) {
        var ordered = !open.unordered();
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
     * Reacts to a peer {@link DcepMessage.Ack} by opening the matching local channel.
     *
     * <p>The channel registered for the stream id, if any, is transitioned from
     * {@link DataChannelState#CONNECTING} to {@link DataChannelState#OPEN}.
     *
     * @param streamId the stream id the acknowledgement arrived on
     */
    private void handlePeerAck(int streamId) {
        var channel = channels.get(streamId);
        if (channel != null) {
            channel.notifyOpen();
        }
    }

    /**
     * Delivers an inbound text message to the channel registered for the given stream id, if any.
     *
     * @param streamId the stream id
     * @param value    the decoded UTF-8 string, possibly empty
     */
    private void deliverText(int streamId, String value) {
        var channel = channels.get(streamId);
        if (channel != null) {
            channel.notifyMessage(new DataChannel.Message.Text(channel, value));
        }
    }

    /**
     * Delivers an inbound binary message to the channel registered for the given stream id, if any.
     *
     * @param streamId the stream id
     * @param data     the message bytes, possibly empty
     */
    private void deliverBinary(int streamId, byte[] data) {
        var channel = channels.get(streamId);
        if (channel != null) {
            channel.notifyMessage(new DataChannel.Message.Binary(channel, data));
        }
    }

    /**
     * Allocates the next free stream id of the correct parity for the local DTLS role, advancing
     * {@link #nextStreamId} past it.
     *
     * @return the freshly allocated stream id
     * @throws WhatsAppCallException.DataChannel if the parity class is exhausted, which is unlikely
     *                                           given 32768 ids per side
     */
    private synchronized int allocateStreamId() {
        while (nextStreamId <= 65534) {
            var candidate = nextStreamId;
            nextStreamId += 2;
            if (!channels.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new WhatsAppCallException.DataChannel(
                "stream id space exhausted for DTLS " + (dtlsClient ? "client (odd)" : "server (even)"));
    }

    /**
     * Returns whether the registry currently holds the given stream id.
     *
     * @param streamId the stream id
     * @return {@code true} if a channel is registered for it
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
     * Returns the next stream id that {@link #allocateStreamId()} would pick, or empty if the
     * parity class is exhausted.
     *
     * @return the next stream id, or empty
     */
    OptionalInt nextStreamIdForTesting() {
        return nextStreamId <= 65534 ? OptionalInt.of(nextStreamId) : OptionalInt.empty();
    }
}
