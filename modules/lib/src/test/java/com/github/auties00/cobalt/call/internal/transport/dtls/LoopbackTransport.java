package com.github.auties00.cobalt.call.internal.transport.dtls;

import org.bouncycastle.tls.DatagramTransport;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test harness: an in-memory BouncyCastle {@link DatagramTransport} pair that drives the DTLS-SRTP
 * handshake without a UDP socket. Each instance writes to an outbound {@link LinkedBlockingQueue}
 * that is the peer's inbound queue, so what one side writes via {@link #send} the other reads via
 * {@link #receive}, and vice versa. {@link #pair()} returns the two cross-wired instances.
 */
final class LoopbackTransport implements DatagramTransport {
    private final LinkedBlockingQueue<byte[]> inbound;

    private final LinkedBlockingQueue<byte[]> outbound;

    // 1500 covers any realistic DTLS handshake message under standard IP MTU.
    private static final int MTU = 1500;

    private volatile boolean closed;

    private LoopbackTransport(LinkedBlockingQueue<byte[]> inbound,
                              LinkedBlockingQueue<byte[]> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    static LoopbackTransport[] pair() {
        var q1 = new LinkedBlockingQueue<byte[]>();
        var q2 = new LinkedBlockingQueue<byte[]>();
        return new LoopbackTransport[] {
                new LoopbackTransport(q1, q2),
                new LoopbackTransport(q2, q1)
        };
    }

    @Override
    public int getReceiveLimit() {
        return MTU;
    }

    @Override
    public int getSendLimit() {
        return MTU;
    }

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

    @Override
    public void send(byte[] buf, int off, int len) throws IOException {
        if (closed) throw new IOException("transport closed");
        var copy = new byte[len];
        System.arraycopy(buf, off, copy, 0, len);
        outbound.add(copy);
    }

    @Override
    public void close() {
        closed = true;
    }
}
