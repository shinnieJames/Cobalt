package com.github.auties00.cobalt.call.internal.transport.ice;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.net.InetSocketAddress;

/**
 * The output of a successful {@link IceAgent} session — a connected
 * datagram pipe over the nominated candidate pair, ready for the
 * DTLS-SRTP layer (#77) to layer on top.
 *
 * <p>Modeled on {@link java.nio.channels.DatagramChannel} but with a
 * sink/feed shape so it composes with the call's
 * {@link com.github.auties00.cobalt.call.internal.transport.sctp.SctpAssociation}-style
 * outbound consumer pattern.
 */
public interface DatagramTransport extends AutoCloseable {
    /**
     * Returns the local-side address of the nominated path.
     *
     * @return the local address
     */
    InetSocketAddress localAddress();

    /**
     * Returns the remote-side address of the nominated path.
     *
     * @return the remote address
     */
    InetSocketAddress remoteAddress();

    /**
     * Sends one application-layer datagram (typically a DTLS record
     * after #77 lands, or an SRTP packet once #78 is ready) over the
     * nominated pair. For RELAYED pairs the agent prepends the
     * appropriate TURN ChannelData / Send-indication framing.
     *
     * @param packet the payload
     * @throws WhatsAppCallException.Ice        if the underlying socket has been
     *                              closed or the relay rejects the
     *                              send
     * @throws NullPointerException if {@code packet} is {@code null}
     */
    void send(byte[] packet);

    /**
     * Registers a listener for inbound application-layer datagrams
     * — fires after the agent has stripped any TURN framing. The
     * listener is invoked synchronously on whichever thread the
     * underlying socket is read from.
     *
     * @param listener the listener, or {@code null} to deregister
     */
    void setInboundListener(InboundListener listener);

    /**
     * Closes the transport. Idempotent.
     */
    @Override
    void close();

    /**
     * Functional interface invoked once per inbound application
     * datagram.
     */
    @FunctionalInterface
    interface InboundListener {
        /**
         * Receives one datagram from the nominated path.
         *
         * @param packet the payload bytes
         */
        void onDatagram(byte[] packet);
    }
}
