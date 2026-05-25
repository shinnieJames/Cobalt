package com.github.auties00.cobalt.call.internal.transport.ice;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.net.InetSocketAddress;

/**
 * Abstracts a connected datagram pipe over a single nominated ICE candidate pair, the output of a
 * successful {@link IceAgent} session.
 *
 * <p>A transport exposes the local and remote transport addresses of the nominated path, sends one
 * application-layer datagram at a time, delivers inbound datagrams to a registered
 * {@link InboundListener}, and closes idempotently. The DTLS-SRTP layer composes on top of it, and
 * for a relayed pair the agent prepends the TURN ChannelData or Send-indication framing on send and
 * strips it before delivery. The sink-and-feed shape mirrors the outbound-consumer pattern of
 * {@link com.github.auties00.cobalt.call.internal.transport.sctp.SctpAssociation}.
 */
public interface DatagramTransport extends AutoCloseable {
    /**
     * Returns the local-side transport address of the nominated path.
     *
     * @return the local address
     */
    InetSocketAddress localAddress();

    /**
     * Returns the remote-side transport address of the nominated path.
     *
     * @return the remote address
     */
    InetSocketAddress remoteAddress();

    /**
     * Sends one application-layer datagram, such as a DTLS record or an SRTP packet, over the
     * nominated pair.
     *
     * <p>For a relayed pair the implementation prepends the TURN ChannelData or Send-indication
     * framing before the datagram leaves the socket.
     *
     * @implSpec Implementations must transmit the supplied bytes over the nominated path as a
     * single datagram, reject a {@code null} argument with a {@link NullPointerException}, and
     * surface a closed socket or a relay-side rejection as a {@link WhatsAppCallException.Ice}.
     * @param packet the payload bytes
     * @throws WhatsAppCallException.Ice if the underlying socket has been closed or the relay
     *                                   rejects the send
     * @throws NullPointerException      if {@code packet} is {@code null}
     */
    void send(byte[] packet);

    /**
     * Registers the listener invoked for each inbound application-layer datagram, replacing any
     * previously registered listener.
     *
     * <p>The listener fires after the implementation has stripped any TURN framing, and is invoked
     * synchronously on whichever thread the underlying socket is read from. Passing {@code null}
     * deregisters the current listener.
     *
     * @implSpec Implementations must invoke the most recently registered listener for every
     * received datagram, deliver the de-framed payload, and treat a {@code null} argument as
     * deregistration. The replacement must take effect for subsequent datagrams without losing
     * those already in flight on the read thread.
     * @param listener the listener to register, or {@code null} to deregister
     */
    void setInboundListener(InboundListener listener);

    /**
     * Closes the transport, releasing the underlying socket and stopping inbound delivery.
     *
     * @implSpec Implementations must be idempotent: a second or later call has no effect and does
     * not throw.
     */
    @Override
    void close();

    /**
     * Receives inbound application-layer datagrams from the nominated path.
     */
    @FunctionalInterface
    interface InboundListener {
        /**
         * Receives one de-framed datagram from the nominated path.
         *
         * @implSpec The transport invokes this synchronously on its read thread, so an
         * implementation must return promptly and must not assume any particular caller thread.
         * @param packet the payload bytes
         */
        void onDatagram(byte[] packet);
    }
}
