package com.github.auties00.cobalt.call.internal.transport.dtls;

import org.bouncycastle.tls.DatagramTransport;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory pair of {@link DatagramTransport}s for unit-testing the
 * DTLS-SRTP handshake without actually using a UDP socket. What one
 * side writes via {@link #send}, the other side reads via
 * {@link #receive}, and vice versa.
 *
 * <p>Construction: {@link #pair()} returns two instances that share
 * each other's outbound queues as inbound queues.
 *
 * <p>Test-only — lives in {@code src/test/java}.
 */
final class LoopbackTransport implements DatagramTransport {
    /**
     * Inbound queue: datagrams written by the peer end up here.
     */
    private final LinkedBlockingQueue<byte[]> inbound;

    /**
     * Outbound queue: datagrams we write end up here, where the peer
     * reads them.
     */
    private final LinkedBlockingQueue<byte[]> outbound;

    /**
     * Maximum supported datagram size — large enough for any
     * realistic DTLS handshake message under standard IP MTU.
     */
    private static final int MTU = 1500;

    /**
     * Closed flag — once set, send/receive throw.
     */
    private volatile boolean closed;

    /**
     * Constructs a transport bound to the supplied inbound +
     * outbound queues.
     *
     * @param inbound  the queue to read from
     * @param outbound the queue to write to
     */
    private LoopbackTransport(LinkedBlockingQueue<byte[]> inbound,
                              LinkedBlockingQueue<byte[]> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Constructs two paired loopback transports such that what one
     * writes the other reads.
     *
     * @return a 2-element array {@code [a, b]} where {@code a.send}
     *         feeds {@code b.receive} and vice versa
     */
    static LoopbackTransport[] pair() {
        var q1 = new LinkedBlockingQueue<byte[]>();
        var q2 = new LinkedBlockingQueue<byte[]>();
        return new LoopbackTransport[] {
                new LoopbackTransport(q1, q2),
                new LoopbackTransport(q2, q1)
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReceiveLimit() {
        return MTU;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSendLimit() {
        return MTU;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int receive(byte[] buf, int off, int len, int waitMillis) throws IOException {
        if (closed) throw new IOException("transport closed");
        try {
            var pkt = waitMillis <= 0
                    ? inbound.take()
                    : inbound.poll(waitMillis, TimeUnit.MILLISECONDS);
            if (pkt == null) return -1;
            var n = Math.min(pkt.length, len);
            System.arraycopy(pkt, 0, buf, off, n);
            return n;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("receive interrupted", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(byte[] buf, int off, int len) throws IOException {
        if (closed) throw new IOException("transport closed");
        var copy = new byte[len];
        System.arraycopy(buf, off, copy, 0, len);
        outbound.add(copy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        closed = true;
    }
}
