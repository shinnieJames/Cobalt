package com.github.auties00.cobalt.call.transport.dtls;

import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Drives the DTLS-SRTP handshake on top of a connected
 * {@link DatagramTransport} (typically the nominated path produced by
 * the ICE agent #76), demultiplexes inbound packets per RFC 7983, and
 * exposes the negotiated {@link SrtpEndpoint} to the RTP loop (#78).
 *
 * <h2>Multiplexing</h2>
 *
 * <p>WebRTC layers STUN, DTLS, and SRTP/SRTCP on a single UDP
 * 5-tuple. RFC 7983 specifies the byte-0 ranges that distinguish them:
 *
 * <ul>
 *   <li>{@code 0..3} → STUN (handed back to the ICE agent for
 *       connectivity-check keepalives)</li>
 *   <li>{@code 20..63} → DTLS records (driven into the BouncyCastle
 *       DTLS state machine)</li>
 *   <li>{@code 64..79} → TURN ChannelData (also routed to the ICE
 *       agent's relay handler)</li>
 *   <li>{@code 128..191} → SRTP/SRTCP (forwarded to the RTP layer
 *       after handshake)</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>Construct with the ICE-nominated transport and a
 *       {@link DtlsSrtpEndpoint} (client or server).</li>
 *   <li>{@link #start()} spawns the handshake virtual thread.</li>
 *   <li>{@link #awaitHandshake} blocks until the handshake
 *       completes or fails.</li>
 *   <li>Post-handshake, {@link #srtpEndpoint()} returns the
 *       negotiated keys; the caller wires
 *       {@link #setSrtpHandler(Consumer)} to the RTP loop.</li>
 *   <li>{@link #close()} drops the handshake thread, closes the
 *       transport, and releases native state.</li>
 * </ol>
 */
public final class DtlsSrtpDriver implements AutoCloseable {
    /**
     * Maximum-sized DTLS record buffer — bigger than any
     * realistic UDP datagram so {@link #adapter}'s
     * {@code receive} can copy the whole packet without truncation.
     */
    private static final int MTU = 1500;

    /**
     * The connected ICE transport.
     */
    private final DatagramTransport transport;

    /**
     * The BouncyCastle endpoint that drives the actual handshake.
     */
    private final DtlsSrtpEndpoint endpoint;

    /**
     * BC-side adapter — bridges our outbound API
     * ({@link DatagramTransport#send(byte[])}) to BC's
     * ({@code send(byte[], int, int)}) and queues inbound DTLS
     * records demultiplexed off the wire.
     */
    private final BcAdapter adapter;

    /**
     * Latch released once {@link #handshakeResult} has been
     * populated with either the negotiated {@link SrtpEndpoint} or
     * the handshake failure.
     */
    private final CountDownLatch handshakeDone = new CountDownLatch(1);

    /**
     * Holds the handshake outcome — {@code null} until
     * {@link #handshakeDone} fires.
     */
    private final AtomicReference<HandshakeResult> handshakeResult = new AtomicReference<>();

    /**
     * Application listener for inbound STUN bytes (byte-0 in
     * {@code 0..3} or TURN ChannelData {@code 64..79}). Set by the
     * call layer to route back to the ICE agent.
     */
    private volatile Consumer<byte[]> stunHandler;

    /**
     * Application listener for inbound SRTP/SRTCP bytes (byte-0 in
     * {@code 128..191}). Set by the call layer to forward to the
     * RTP loop.
     */
    private volatile Consumer<byte[]> srtpHandler;

    /**
     * The handshake virtual thread; {@code null} until
     * {@link #start()}.
     */
    private Thread handshakeThread;

    /**
     * Set once {@link #close()} runs so subsequent inbound packets
     * are dropped.
     */
    private volatile boolean closed;

    /**
     * Outcome of the handshake — either the negotiated SRTP
     * endpoint or the failure that ended the handshake.
     */
    private record HandshakeResult(SrtpEndpoint srtp, IOException failure) {
        /**
         * Wraps a successful handshake.
         *
         * @param srtp the negotiated endpoint
         * @return a success result
         */
        static HandshakeResult success(SrtpEndpoint srtp) {
            return new HandshakeResult(srtp, null);
        }

        /**
         * Wraps a failed handshake.
         *
         * @param failure the IOException that ended the handshake
         * @return a failure result
         */
        static HandshakeResult failure(IOException failure) {
            return new HandshakeResult(null, failure);
        }
    }

    /**
     * Constructs a new driver.
     *
     * @param transport               the ICE-nominated datagram
     *                                path
     * @param role                    {@link SrtpRole#CLIENT} or
     *                                {@link SrtpRole#SERVER}
     * @param localCert               the local cert + private key
     * @param expectedPeerFingerprint the peer's SHA-256 fingerprint
     *                                exchanged out-of-band via call
     *                                signaling
     * @throws NullPointerException     if any argument is
     *                                  {@code null}
     * @throws IllegalArgumentException if
     *                                  {@code expectedPeerFingerprint}
     *                                  is not exactly 32 bytes
     */
    public DtlsSrtpDriver(DatagramTransport transport, SrtpRole role,
                          DtlsCertificate localCert, byte[] expectedPeerFingerprint) {
        this.transport = Objects.requireNonNull(transport, "transport cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(localCert, "localCert cannot be null");
        Objects.requireNonNull(expectedPeerFingerprint, "expectedPeerFingerprint cannot be null");
        this.adapter = new BcAdapter(this);
        this.endpoint = switch (role) {
            case CLIENT -> DtlsSrtpEndpoint.client(localCert, expectedPeerFingerprint, adapter);
            case SERVER -> DtlsSrtpEndpoint.server(localCert, expectedPeerFingerprint, adapter);
        };
        this.transport.setInboundListener(this::demuxInbound);
    }

    /**
     * Returns the underlying ICE-nominated transport.
     *
     * @return the transport
     */
    public DatagramTransport transport() {
        return transport;
    }

    /**
     * Registers a listener for inbound STUN bytes — the call layer
     * pipes these back to its ICE agent for connectivity-check
     * keepalives.
     *
     * @param handler the listener; may be {@code null} to clear
     */
    public void setStunHandler(Consumer<byte[]> handler) {
        this.stunHandler = handler;
    }

    /**
     * Registers a listener for inbound SRTP/SRTCP bytes — wired to
     * the RTP loop after the handshake.
     *
     * @param handler the listener; may be {@code null} to clear
     */
    public void setSrtpHandler(Consumer<byte[]> handler) {
        this.srtpHandler = handler;
    }

    /**
     * Spawns the handshake virtual thread. Idempotent.
     */
    public synchronized void start() {
        if (handshakeThread != null) {
            return;
        }
        handshakeThread = Thread.ofVirtual()
                .name("dtls-srtp-handshake")
                .start(this::runHandshake);
    }

    /**
     * Blocks until the handshake either completes successfully or
     * fails.
     *
     * @param timeout the maximum time to wait
     * @param unit    the timeout unit
     * @return the negotiated {@link SrtpEndpoint}
     * @throws IOException                   if the handshake failed
     *                                       or the wait timed out
     * @throws InterruptedException          if the calling thread
     *                                       is interrupted
     */
    public SrtpEndpoint awaitHandshake(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        if (!handshakeDone.await(timeout, unit)) {
            throw new IOException("DTLS handshake timed out after " + timeout + " " + unit);
        }
        var result = handshakeResult.get();
        if (result.failure() != null) {
            throw result.failure();
        }
        return result.srtp();
    }

    /**
     * Returns the negotiated {@link SrtpEndpoint}, or {@code null}
     * if the handshake has not completed yet.
     *
     * @return the SRTP endpoint, or {@code null}
     */
    public SrtpEndpoint srtpEndpoint() {
        var result = handshakeResult.get();
        return result == null ? null : result.srtp();
    }

    /**
     * Sends an SRTP/SRTCP packet over the underlying transport. The
     * caller is responsible for having encrypted the packet first
     * via the negotiated {@link SrtpEndpoint}.
     *
     * @param packet the protected SRTP/SRTCP bytes
     */
    public void sendSrtp(byte[] packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (closed) {
            return;
        }
        transport.send(packet);
    }

    /**
     * Sends a STUN packet over the underlying transport — used by
     * the ICE agent for keepalives and consent freshness checks
     * after the handshake completes.
     *
     * @param packet the STUN bytes
     */
    public void sendStun(byte[] packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (closed) {
            return;
        }
        transport.send(packet);
    }

    /**
     * Closes the driver — interrupts the handshake thread, closes
     * the underlying transport, and unblocks any thread waiting in
     * {@link #awaitHandshake}. Idempotent.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            adapter.close();
        } catch (Throwable _) {
        }
        try {
            transport.close();
        } catch (Throwable _) {
        }
        if (handshakeThread != null) {
            handshakeThread.interrupt();
            try {
                handshakeThread.join(1_000);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
        // Make sure awaitHandshake doesn't hang if close() races
        // ahead of the handshake thread's setHandshakeResult.
        if (handshakeResult.compareAndSet(null,
                HandshakeResult.failure(new IOException("driver closed before handshake completed")))) {
            handshakeDone.countDown();
        }
    }

    /**
     * Demultiplexes one inbound datagram per RFC 7983 byte-0 ranges
     * and routes to the right path. Called synchronously on
     * whichever thread the underlying transport's inbound listener
     * fires on.
     *
     * @param packet the inbound bytes
     */
    private void demuxInbound(byte[] packet) {
        if (closed || packet == null || packet.length == 0) {
            return;
        }
        int b0 = packet[0] & 0xFF;
        if (b0 <= 3 || (b0 >= 64 && b0 <= 79)) {
            var h = stunHandler;
            if (h != null) {
                try {
                    h.accept(packet);
                } catch (Throwable _) {
                }
            }
        } else if (b0 >= 20 && b0 <= 63) {
            adapter.deliverInbound(packet);
        } else if (b0 >= 128 && b0 <= 191) {
            var h = srtpHandler;
            if (h != null) {
                try {
                    h.accept(packet);
                } catch (Throwable _) {
                }
            }
        }
        // Other ranges (ZRTP 16..19, etc.) are dropped silently.
    }

    /**
     * Handshake thread body — drives
     * {@link DtlsSrtpEndpoint#handshake()} via the BC adapter and
     * publishes the result.
     */
    private void runHandshake() {
        try {
            var srtp = endpoint.handshake();
            handshakeResult.compareAndSet(null, HandshakeResult.success(srtp));
        } catch (IOException e) {
            handshakeResult.compareAndSet(null, HandshakeResult.failure(e));
        } catch (RuntimeException e) {
            handshakeResult.compareAndSet(null,
                    HandshakeResult.failure(new IOException("DTLS handshake threw", e)));
        } finally {
            handshakeDone.countDown();
        }
    }

    /**
     * Adapter implementing BouncyCastle's
     * {@link org.bouncycastle.tls.DatagramTransport} on top of our
     * {@link DatagramTransport}. Inbound DTLS records flow in via
     * {@link #deliverInbound(byte[])} (called by the demuxer),
     * outbound flow goes straight to the underlying transport.
     */
    private static final class BcAdapter implements org.bouncycastle.tls.DatagramTransport {
        /**
         * Owning driver — used to write outbound bytes.
         */
        private final DtlsSrtpDriver owner;

        /**
         * Queue of inbound DTLS records, populated by
         * {@link #deliverInbound}.
         */
        private final LinkedBlockingQueue<byte[]> inbound = new LinkedBlockingQueue<>();

        /**
         * Sentinel record pushed by {@link #close()} to wake any
         * thread blocked in {@link #receive(byte[], int, int, int)}.
         */
        private static final byte[] CLOSE_SENTINEL = new byte[0];

        /**
         * Whether the adapter has been closed.
         */
        private volatile boolean adapterClosed;

        /**
         * Constructs a new adapter.
         *
         * @param owner the driver
         */
        BcAdapter(DtlsSrtpDriver owner) {
            this.owner = owner;
        }

        /**
         * Pushes one inbound DTLS record into the queue.
         *
         * @param packet the bytes
         */
        void deliverInbound(byte[] packet) {
            if (adapterClosed) {
                return;
            }
            inbound.offer(packet);
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
            if (adapterClosed) {
                throw new IOException("DTLS transport closed");
            }
            byte[] pkt;
            try {
                pkt = waitMillis <= 0
                        ? inbound.take()
                        : inbound.poll(waitMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("DTLS receive interrupted", e);
            }
            if (pkt == null) {
                return -1;
            }
            if (pkt == CLOSE_SENTINEL) {
                throw new IOException("DTLS transport closed");
            }
            int n = Math.min(pkt.length, len);
            System.arraycopy(pkt, 0, buf, off, n);
            return n;
        }

        @Override
        public void send(byte[] buf, int off, int len) throws IOException {
            if (adapterClosed || owner.closed) {
                throw new IOException("DTLS transport closed");
            }
            var copy = new byte[len];
            System.arraycopy(buf, off, copy, 0, len);
            owner.transport.send(copy);
        }

        @Override
        public void close() {
            adapterClosed = true;
            inbound.offer(CLOSE_SENTINEL);
        }
    }
}
