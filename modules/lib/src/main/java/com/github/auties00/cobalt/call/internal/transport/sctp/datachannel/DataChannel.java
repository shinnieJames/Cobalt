package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One bidirectional WebRTC DataChannel established over SCTP, modelled
 * on W3C {@code RTCDataChannel} and RFC 8831/8832.
 *
 * <p>An instance is produced by either:
 *
 * <ul>
 *   <li>{@link DataChannelTransport#open(String, DataChannelOptions)}
 *       on the local side — the channel starts in
 *       {@link DataChannelState#CONNECTING} until the peer's
 *       {@link DcepMessage.Ack DATA_CHANNEL_ACK} arrives, then
 *       transitions to {@link DataChannelState#OPEN}.</li>
 *   <li>{@link DataChannelTransport.PeerOpenListener} firing on the
 *       remote side — the channel arrives already in
 *       {@link DataChannelState#OPEN} (the {@code ACK} is sent by the
 *       transport before the listener fires).</li>
 *   <li>An {@linkplain DataChannelOptions#negotiated() externally
 *       negotiated} channel created on either side — the channel
 *       arrives in {@link DataChannelState#OPEN} immediately, no
 *       DCEP exchange.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #send(String)} and {@link #send(byte[])} are safe to call
 * from any thread; they delegate to the underlying
 * {@link com.github.auties00.cobalt.call.internal.transport.sctp.SctpAssociation},
 * which is single-threaded internally but tolerates concurrent
 * callers as long as each {@code send} is atomic.
 *
 * <p>{@link MessageListener#onMessage} fires synchronously on the
 * thread that called
 * {@link DataChannelTransport#feedInboundPacket(byte[])} —
 * applications that want to offload work should hand off in the
 * listener.
 */
public final class DataChannel implements AutoCloseable {
    /**
     * The transport that created this channel; the channel calls
     * back into it to send DCEP/data messages and to deregister on
     * close.
     */
    private final DataChannelTransport transport;

    /**
     * SCTP stream id (0..65534). For RFC 8832 in-band negotiation
     * this is allocated by the transport based on DTLS role; for
     * out-of-band negotiated channels this is the value the
     * application supplied.
     */
    private final int streamId;

    /**
     * The channel label, copied from the
     * {@link DcepMessage.Open#label()} on the remote side or supplied
     * to {@link DataChannelTransport#open(String, DataChannelOptions)}
     * on the local side.
     */
    private final String label;

    /**
     * The channel subprotocol identifier; empty string when unused.
     */
    private final String protocol;

    /**
     * Whether messages are delivered in send order.
     */
    private final boolean ordered;

    /**
     * Whether the channel was created with the stream id agreed
     * out-of-band, suppressing DCEP.
     */
    private final boolean negotiated;

    /**
     * Maximum number of retransmissions per message (PR-SCTP rexmit),
     * or empty for fully reliable / lifetime-based.
     */
    private final OptionalInt maxRetransmits;

    /**
     * Maximum message lifetime in milliseconds (PR-SCTP timed), or
     * empty for fully reliable / rexmit-based.
     */
    private final OptionalInt maxLifetimeMs;

    /**
     * Mutable channel state. Transitions monotonically along
     * {@link DataChannelState#CONNECTING CONNECTING} →
     * {@link DataChannelState#OPEN OPEN} →
     * {@link DataChannelState#CLOSING CLOSING} →
     * {@link DataChannelState#CLOSED CLOSED}.
     */
    private final AtomicReference<DataChannelState> state;

    /**
     * Application-registered listener invoked when the channel
     * transitions to {@link DataChannelState#OPEN}.
     */
    private volatile Runnable openListener;

    /**
     * Application-registered listener invoked for every inbound
     * application message.
     */
    private volatile MessageListener messageListener;

    /**
     * Application-registered listener invoked when the channel
     * transitions to {@link DataChannelState#CLOSED}.
     */
    private volatile Runnable closeListener;

    /**
     * Decoded inbound application message delivered to
     * {@link MessageListener#onMessage}.
     */
    public sealed interface Message {
        /**
         * Returns the channel that delivered the message.
         *
         * @return the originating channel
         */
        DataChannel channel();

        /**
         * A string message, originally arrived on PPID 51 or PPID 56
         * (empty-string convention).
         *
         * @param channel the originating channel
         * @param value   the decoded UTF-8 string
         */
        record Text(DataChannel channel, String value) implements Message {
            /**
             * Compact constructor — null-checks fields.
             */
            public Text {
                Objects.requireNonNull(channel, "channel cannot be null");
                Objects.requireNonNull(value, "value cannot be null");
            }
        }

        /**
         * A binary message, originally arrived on PPID 53 or PPID 57
         * (empty-binary convention).
         *
         * @param channel the originating channel
         * @param data    the message bytes
         */
        record Binary(DataChannel channel, byte[] data) implements Message {
            /**
             * Compact constructor — null-checks fields.
             */
            public Binary {
                Objects.requireNonNull(channel, "channel cannot be null");
                Objects.requireNonNull(data, "data cannot be null");
            }
        }
    }

    /**
     * Functional interface invoked once per inbound application
     * message.
     */
    @FunctionalInterface
    public interface MessageListener {
        /**
         * Called on the thread that fed the inbound SCTP packet.
         *
         * @param message the inbound message
         */
        void onMessage(Message message);
    }

    /**
     * Package-private constructor — only the {@link DataChannelTransport}
     * may create channels.
     *
     * @param transport      the owning transport
     * @param streamId       the SCTP stream id
     * @param label          the channel label
     * @param protocol       the subprotocol identifier (possibly
     *                       empty)
     * @param ordered        whether ordered delivery is requested
     * @param negotiated     whether the channel was negotiated
     *                       out-of-band
     * @param maxRetransmits the max-retransmits parameter, or empty
     * @param maxLifetimeMs  the max-lifetime parameter, or empty
     * @param initialState   the initial {@link DataChannelState}
     *                       (typically {@link DataChannelState#CONNECTING}
     *                       for in-band, {@link DataChannelState#OPEN}
     *                       for negotiated or peer-opened)
     */
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
    }

    /**
     * Returns the SCTP stream id this channel rides.
     *
     * @return the stream id (0..65534)
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
     * Returns the channel subprotocol identifier; empty string when
     * unused.
     *
     * @return the subprotocol
     */
    public String protocol() {
        return protocol;
    }

    /**
     * Returns whether the channel preserves message ordering.
     *
     * @return {@code true} if ordered
     */
    public boolean ordered() {
        return ordered;
    }

    /**
     * Returns whether the channel was negotiated out-of-band, i.e.
     * created without DCEP.
     *
     * @return {@code true} if negotiated
     */
    public boolean negotiated() {
        return negotiated;
    }

    /**
     * Returns the partial-reliability max-retransmits parameter, or
     * empty if not set.
     *
     * @return the max retransmits, or empty
     */
    public OptionalInt maxRetransmits() {
        return maxRetransmits;
    }

    /**
     * Returns the partial-reliability max-lifetime parameter (ms),
     * or empty if not set.
     *
     * @return the max lifetime ms, or empty
     */
    public OptionalInt maxLifetimeMs() {
        return maxLifetimeMs;
    }

    /**
     * Returns the current channel state. The value is updated
     * atomically on transitions; readers see a consistent snapshot.
     *
     * @return the current state
     */
    public DataChannelState state() {
        return state.get();
    }

    /**
     * Registers a listener invoked when the channel transitions to
     * {@link DataChannelState#OPEN}. If the channel is already open
     * the listener fires immediately on the calling thread.
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
     * Registers a listener invoked for every inbound application
     * message.
     *
     * @param listener the listener, or {@code null} to deregister
     */
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Registers a listener invoked when the channel transitions to
     * {@link DataChannelState#CLOSED}. If the channel is already
     * closed the listener fires immediately on the calling thread.
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
     * Sends a UTF-8 string message. Empty strings are sent on PPID
     * {@value DataChannelTransport#PPID_STRING_EMPTY} per RFC 8831
     * §6.6 (since SCTP forbids zero-length application data).
     *
     * @param text the message
     * @throws NullPointerException  if {@code text} is {@code null}
     * @throws IllegalStateException if the channel is not
     *                               {@link DataChannelState#OPEN}
     * @throws WhatsAppCallException.DataChannel  if the underlying SCTP send fails
     */
    public void send(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        requireOpen();
        if (text.isEmpty()) {
            transport.sendDataMessage(streamId, DataChannelTransport.PPID_STRING_EMPTY,
                    DataChannelTransport.EMPTY_PLACEHOLDER, ordered);
        } else {
            var payload = text.getBytes(StandardCharsets.UTF_8);
            transport.sendDataMessage(streamId, DataChannelTransport.PPID_STRING, payload, ordered);
        }
    }

    /**
     * Sends a binary message. Empty arrays are sent on PPID
     * {@value DataChannelTransport#PPID_BINARY_EMPTY} per RFC 8831
     * §6.6.
     *
     * @param data the message bytes
     * @throws NullPointerException  if {@code data} is {@code null}
     * @throws IllegalStateException if the channel is not
     *                               {@link DataChannelState#OPEN}
     * @throws WhatsAppCallException.DataChannel  if the underlying SCTP send fails
     */
    public void send(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");
        requireOpen();
        if (data.length == 0) {
            transport.sendDataMessage(streamId, DataChannelTransport.PPID_BINARY_EMPTY,
                    DataChannelTransport.EMPTY_PLACEHOLDER, ordered);
        } else {
            transport.sendDataMessage(streamId, DataChannelTransport.PPID_BINARY, data, ordered);
        }
    }

    /**
     * Closes the channel. Idempotent. Drops the channel from the
     * transport's registry; the SCTP stream-reset that signals the
     * peer is best-effort and not awaited.
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
     * Throws {@link IllegalStateException} unless the channel is in
     * {@link DataChannelState#OPEN}.
     */
    private void requireOpen() {
        var current = state.get();
        if (current != DataChannelState.OPEN) {
            throw new IllegalStateException(
                    "DataChannel '" + label + "' is " + current + ", not OPEN");
        }
    }

    /**
     * Transitions {@link DataChannelState#CONNECTING CONNECTING} →
     * {@link DataChannelState#OPEN OPEN} and fires the open listener.
     * No-op if not in {@code CONNECTING}.
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
     * Forwards an inbound message to the registered
     * {@link MessageListener}, or silently drops it if no listener is
     * attached.
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
     * Marks the channel as closed in response to a peer-driven SCTP
     * stream reset and fires the close listener. Differs from
     * {@link #close()} only in that it skips the
     * {@link DataChannelTransport#unregisterChannel} call (the
     * transport already removed the entry before invoking this).
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
