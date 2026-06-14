package com.github.auties00.cobalt.call.transport.sctp.datachannel;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents one bidirectional WebRTC DataChannel established over SCTP.
 *
 * <p>The type models W3C {@code RTCDataChannel} and RFC 8831/8832. An instance is produced in one
 * of three ways:
 *
 * <ul>
 *   <li>By {@link DataChannelTransport#open(String, DataChannelOptions)} on the local side, in
 *       which case the channel starts in {@link DataChannelState#CONNECTING} until the peer's
 *       {@link DcepMessage.Ack} arrives and then transitions to {@link DataChannelState#OPEN}.</li>
 *   <li>By a peer-initiated open delivered through {@link DataChannelTransport.PeerOpenListener},
 *       in which case the channel arrives already in {@link DataChannelState#OPEN} because the
 *       transport has already sent the {@link DcepMessage.Ack}.</li>
 *   <li>By an {@linkplain DataChannelOptions#negotiated() externally negotiated} open on either
 *       side, in which case the channel arrives in {@link DataChannelState#OPEN} immediately with
 *       no DCEP exchange.</li>
 * </ul>
 *
 * <p>{@link #send(String)} and {@link #send(byte[])} are safe to call from any thread; they
 * delegate to the underlying SCTP association, which is single-threaded internally but tolerates
 * concurrent callers as long as each send is atomic. By contrast {@link MessageListener#onMessage}
 * fires synchronously on the thread that called
 * {@link DataChannelTransport#feedInboundPacket(byte[])}, so a listener that needs to offload work
 * must hand off itself.
 */
public final class DataChannel implements AutoCloseable {
    /**
     * The transport that created this channel.
     *
     * <p>The channel calls back into the transport to send DCEP and data messages and to
     * deregister itself on close.
     */
    private final DataChannelTransport transport;

    /**
     * The SCTP stream id this channel rides, in the range {@code [0, 65534]}.
     *
     * <p>For RFC 8832 in-band negotiation the transport allocates this from the DTLS-role parity
     * class; for an out-of-band negotiated channel it is the value the application supplied.
     */
    private final int streamId;

    /**
     * The channel label.
     *
     * <p>Copied from the remote {@link DcepMessage.Open#label()} on the peer-opened side or from
     * the argument to {@link DataChannelTransport#open(String, DataChannelOptions)} on the local
     * side.
     */
    private final String label;

    /**
     * The channel subprotocol identifier, or the empty string when unused.
     */
    private final String protocol;

    /**
     * Whether messages are delivered in send order.
     */
    private final boolean ordered;

    /**
     * Whether the channel's stream id was agreed out-of-band, which suppresses DCEP.
     */
    private final boolean negotiated;

    /**
     * The maximum number of retransmissions per message, or empty for a fully reliable or
     * lifetime-limited channel.
     */
    private final OptionalInt maxRetransmits;

    /**
     * The maximum message lifetime in milliseconds, or empty for a fully reliable or
     * retransmit-limited channel.
     */
    private final OptionalInt maxLifetimeMs;

    /**
     * The mutable channel state, published atomically on every transition.
     *
     * <p>The state advances monotonically along {@link DataChannelState#CONNECTING},
     * {@link DataChannelState#OPEN}, {@link DataChannelState#CLOSING},
     * {@link DataChannelState#CLOSED}.
     */
    private final AtomicReference<DataChannelState> state;

    /**
     * The listener invoked when the channel transitions to {@link DataChannelState#OPEN}, or
     * {@code null} when none is registered.
     */
    private volatile Runnable openListener;

    /**
     * The listener invoked for every inbound application message, or {@code null} when none is
     * registered.
     */
    private volatile MessageListener messageListener;

    /**
     * The listener invoked when the channel transitions to {@link DataChannelState#CLOSED}, or
     * {@code null} when none is registered.
     */
    private volatile Runnable closeListener;

    /**
     * Represents a decoded inbound application message delivered to
     * {@link MessageListener#onMessage}.
     *
     * <p>This sealed interface has exactly two permitted variants, {@link Text} for string
     * messages and {@link Binary} for byte messages, mirroring the two payload kinds WebRTC data
     * channels carry. Every variant exposes the {@link #channel()} that delivered it.
     */
    public sealed interface Message {
        /**
         * Returns the channel that delivered this message.
         *
         * @return the originating channel
         */
        DataChannel channel();

        /**
         * Represents a string message.
         *
         * <p>A {@code Text} carries a value that arrived either as a non-empty string payload or,
         * for the empty-string convention, as the empty-string placeholder PPID.
         *
         * @param channel the originating channel
         * @param value   the decoded UTF-8 string
         */
        record Text(DataChannel channel, String value) implements Message {
            /**
             * Validates the field values, rejecting a null channel or value.
             */
            public Text {
                Objects.requireNonNull(channel, "channel cannot be null");
                Objects.requireNonNull(value, "value cannot be null");
            }
        }

        /**
         * Represents a binary message.
         *
         * <p>A {@code Binary} carries data that arrived either as a non-empty binary payload or,
         * for the empty-binary convention, as the empty-binary placeholder PPID.
         *
         * @param channel the originating channel
         * @param data    the message bytes
         */
        record Binary(DataChannel channel, byte[] data) implements Message {
            /**
             * Validates the field values, rejecting a null channel or data array.
             */
            public Binary {
                Objects.requireNonNull(channel, "channel cannot be null");
                Objects.requireNonNull(data, "data cannot be null");
            }
        }
    }

    /**
     * Receives one inbound application message per invocation.
     *
     * <p>Implementations run synchronously on the thread that fed the inbound SCTP packet, so they
     * must not block that thread for long.
     */
    @FunctionalInterface
    public interface MessageListener {
        /**
         * Handles one inbound application message.
         *
         * @param message the inbound message
         */
        void onMessage(Message message);
    }

    /**
     * Constructs a channel; only the owning {@link DataChannelTransport} may do so.
     *
     * @param transport      the owning transport
     * @param streamId       the SCTP stream id
     * @param label          the channel label
     * @param protocol       the subprotocol identifier, possibly empty
     * @param ordered        whether ordered delivery is requested
     * @param negotiated     whether the channel was negotiated out-of-band
     * @param maxRetransmits the maximum retransmits parameter, or empty
     * @param maxLifetimeMs  the maximum lifetime parameter in milliseconds, or empty
     * @param initialState   the initial state, typically {@link DataChannelState#CONNECTING} for
     *                       an in-band open and {@link DataChannelState#OPEN} for a negotiated or
     *                       peer-opened channel
     */
    /**
     * Cached SCTP partial-reliability policy byte resolved at construction from
     * {@link #maxRetransmits} / {@link #maxLifetimeMs}.
     */
    private final short prPolicy;

    /**
     * Cached SCTP partial-reliability policy operand resolved at construction: the
     * {@code maxRetransmits} count for {@code SCTP_PR_SCTP_RTX}, the {@code maxLifetimeMs}
     * value for {@code SCTP_PR_SCTP_TTL}, or zero when fully reliable.
     */
    private final int prValue;

    DataChannel(DataChannelTransport transport, int streamId, String label, String protocol,
                boolean ordered, boolean negotiated, OptionalInt maxRetransmits, OptionalInt maxLifetimeMs,
                DataChannelState initialState) {
        this.transport = transport;
        this.streamId = streamId;
        this.label = label;
        this.protocol = protocol;
        this.ordered = ordered;
        this.negotiated = negotiated;
        this.maxRetransmits = maxRetransmits;
        this.maxLifetimeMs = maxLifetimeMs;
        this.state = new AtomicReference<>(initialState);
        if (maxRetransmits.isPresent()) {
            this.prPolicy = (short) com.github.auties00.cobalt.call.transport.sctp.bindings.UsrSctp.SCTP_PR_SCTP_RTX();
            this.prValue = maxRetransmits.getAsInt();
        } else if (maxLifetimeMs.isPresent()) {
            this.prPolicy = (short) com.github.auties00.cobalt.call.transport.sctp.bindings.UsrSctp.SCTP_PR_SCTP_TTL();
            this.prValue = maxLifetimeMs.getAsInt();
        } else {
            this.prPolicy = (short) com.github.auties00.cobalt.call.transport.sctp.bindings.UsrSctp.SCTP_PR_SCTP_NONE();
            this.prValue = 0;
        }
    }

    /**
     * Returns whether this channel's negotiated reliability requires the partial-reliability send
     * path on every outbound message.
     *
     * @return {@code true} when {@link #maxRetransmits} or {@link #maxLifetimeMs} is set
     */
    boolean isPartiallyReliable() {
        return maxRetransmits.isPresent() || maxLifetimeMs.isPresent();
    }

    /**
     * Returns the cached SCTP partial-reliability policy byte (one of
     * {@code SCTP_PR_SCTP_NONE / TTL / RTX}).
     *
     * @return the policy byte
     */
    short prPolicy() {
        return prPolicy;
    }

    /**
     * Returns the cached SCTP partial-reliability policy operand.
     *
     * @return the policy operand
     */
    int prValue() {
        return prValue;
    }

    /**
     * Returns the SCTP stream id this channel rides.
     *
     * @return the stream id in the range {@code [0, 65534]}
     */
    public int streamId() {
        return streamId;
    }

    /**
     * Returns the channel label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the channel subprotocol identifier, or the empty string when unused.
     *
     * @return the subprotocol identifier
     */
    public String protocol() {
        return protocol;
    }

    /**
     * Returns whether the channel preserves message ordering.
     *
     * @return {@code true} if delivery is ordered
     */
    public boolean ordered() {
        return ordered;
    }

    /**
     * Returns whether the channel was negotiated out-of-band, and so created without DCEP.
     *
     * @return {@code true} if the channel was negotiated out-of-band
     */
    public boolean negotiated() {
        return negotiated;
    }

    /**
     * Returns the partial-reliability maximum-retransmits parameter, or empty if not set.
     *
     * @return the maximum retransmits, or empty
     */
    public OptionalInt maxRetransmits() {
        return maxRetransmits;
    }

    /**
     * Returns the partial-reliability maximum-lifetime parameter in milliseconds, or empty if not
     * set.
     *
     * @return the maximum lifetime in milliseconds, or empty
     */
    public OptionalInt maxLifetimeMs() {
        return maxLifetimeMs;
    }

    /**
     * Returns the current channel state.
     *
     * <p>The value is published atomically on each transition, so a reader always observes a
     * consistent state rather than an intermediate one.
     *
     * @return the current state
     */
    public DataChannelState state() {
        return state.get();
    }

    /**
     * Registers a listener invoked when the channel transitions to {@link DataChannelState#OPEN}.
     *
     * <p>If the channel is already open when this method is called, the listener fires immediately
     * on the calling thread. Passing {@code null} deregisters any existing listener.
     *
     * @param listener the listener, or {@code null} to deregister
     */
    public void setOpenListener(Runnable listener) {
        this.openListener = listener;
        if (listener != null && state.get() == DataChannelState.OPEN) {
            listener.run();
        }
    }

    /**
     * Registers a listener invoked for every inbound application message.
     *
     * <p>Passing {@code null} deregisters any existing listener; while none is registered, inbound
     * messages are silently dropped.
     *
     * @param listener the listener, or {@code null} to deregister
     */
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Registers a listener invoked when the channel transitions to {@link DataChannelState#CLOSED}.
     *
     * <p>If the channel is already closed when this method is called, the listener fires
     * immediately on the calling thread. Passing {@code null} deregisters any existing listener.
     *
     * @param listener the listener, or {@code null} to deregister
     */
    public void setCloseListener(Runnable listener) {
        this.closeListener = listener;
        if (listener != null && state.get() == DataChannelState.CLOSED) {
            listener.run();
        }
    }

    /**
     * Sends a UTF-8 string message.
     *
     * <p>A non-empty string is sent on {@link DataChannelTransport#PPID_STRING}. An empty string
     * is instead sent on {@link DataChannelTransport#PPID_STRING_EMPTY} carrying the one-byte
     * placeholder, because SCTP forbids zero-length application data.
     *
     * @param text the message
     * @throws NullPointerException              if {@code text} is {@code null}
     * @throws IllegalStateException             if the channel is not in {@link DataChannelState#OPEN}
     * @throws WhatsAppCallException.DataChannel if the underlying SCTP send fails
     */
    public void send(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        requireOpen();
        var payload = text.isEmpty() ? DataChannelTransport.EMPTY_PLACEHOLDER : text.getBytes(StandardCharsets.UTF_8);
        var ppid = text.isEmpty() ? DataChannelTransport.PPID_STRING_EMPTY : DataChannelTransport.PPID_STRING;
        if (isPartiallyReliable()) {
            transport.sendDataMessage(streamId, ppid, payload, ordered, prPolicy, prValue);
        } else {
            transport.sendDataMessage(streamId, ppid, payload, ordered);
        }
    }

    /**
     * Sends a binary message.
     *
     * <p>A non-empty array is sent on {@link DataChannelTransport#PPID_BINARY}. An empty array is
     * instead sent on {@link DataChannelTransport#PPID_BINARY_EMPTY} carrying the one-byte
     * placeholder, because SCTP forbids zero-length application data.
     *
     * @param data the message bytes
     * @throws NullPointerException              if {@code data} is {@code null}
     * @throws IllegalStateException             if the channel is not in {@link DataChannelState#OPEN}
     * @throws WhatsAppCallException.DataChannel if the underlying SCTP send fails
     */
    public void send(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");
        requireOpen();
        var payload = data.length == 0 ? DataChannelTransport.EMPTY_PLACEHOLDER : data;
        var ppid = data.length == 0 ? DataChannelTransport.PPID_BINARY_EMPTY : DataChannelTransport.PPID_BINARY;
        if (isPartiallyReliable()) {
            transport.sendDataMessage(streamId, ppid, payload, ordered, prPolicy, prValue);
        } else {
            transport.sendDataMessage(streamId, ppid, payload, ordered);
        }
    }

    /**
     * Closes the channel.
     *
     * <p>The call is idempotent and atomically moves the channel to {@link DataChannelState#CLOSED},
     * dropping it from the transport's registry and firing the close listener exactly once. The
     * SCTP stream reset that signals the peer is best-effort and is not awaited.
     */
    @Override
    public void close() {
        var prev = state.getAndSet(DataChannelState.CLOSED);
        if (prev == DataChannelState.CLOSED) {
            return;
        }
        transport.unregisterChannel(streamId);
        var listener = closeListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (Throwable _) {
            }
        }
    }

    /**
     * Throws {@link IllegalStateException} unless the channel is in {@link DataChannelState#OPEN}.
     *
     * @throws IllegalStateException if the channel is not in {@link DataChannelState#OPEN}
     */
    private void requireOpen() {
        var current = state.get();
        if (current != DataChannelState.OPEN) {
            throw new IllegalStateException(
                    "DataChannel '" + label + "' is " + current + ", not OPEN");
        }
    }

    /**
     * Transitions the channel from {@link DataChannelState#CONNECTING} to
     * {@link DataChannelState#OPEN} and fires the open listener.
     *
     * <p>The transition and listener notification happen only on the first call that observes the
     * channel in {@link DataChannelState#CONNECTING}; any other state is a no-op. A throwing
     * listener is swallowed so it cannot disrupt the SCTP receive thread.
     */
    void notifyOpen() {
        if (!state.compareAndSet(DataChannelState.CONNECTING, DataChannelState.OPEN)) {
            return;
        }
        var listener = openListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (Throwable _) {
            }
        }
    }

    /**
     * Forwards an inbound message to the registered {@link MessageListener}.
     *
     * <p>The message is silently dropped when no listener is attached, and a throwing listener is
     * swallowed so it cannot disrupt the SCTP receive thread.
     *
     * @param message the inbound message
     */
    void notifyMessage(Message message) {
        var listener = messageListener;
        if (listener == null) {
            return;
        }
        try {
            listener.onMessage(message);
        } catch (Throwable _) {
        }
    }

    /**
     * Marks the channel closed in response to a peer-driven SCTP stream reset and fires the close
     * listener.
     *
     * <p>The behaviour matches {@link #close()} except that it does not call
     * {@link DataChannelTransport#unregisterChannel(int)}, because the transport has already
     * removed the registry entry before invoking this method. Like {@link #close()}, the
     * transition and notification happen at most once.
     */
    void notifyPeerClosed() {
        var prev = state.getAndSet(DataChannelState.CLOSED);
        if (prev == DataChannelState.CLOSED) {
            return;
        }
        var listener = closeListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (Throwable _) {
            }
        }
    }
}
