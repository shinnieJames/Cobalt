package com.github.auties00.cobalt.call.transport.dtls;

import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import org.bouncycastle.tls.DTLSTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Drives the DTLS-SRTP handshake over a connected {@link DatagramTransport}, demultiplexes inbound
 * packets per RFC 7983, and exposes the negotiated {@link SrtpEndpoint} to the RTP loop.
 *
 * <p>The transport is normally the path nominated by the ICE agent. WebRTC layers STUN, DTLS, and
 * SRTP/SRTCP onto a single UDP 5-tuple, so every inbound datagram must be routed by inspecting its
 * first byte. {@link #demuxInbound(byte[])} applies the RFC 7983 byte-0 ranges:
 *
 * <ul>
 *   <li>{@code 0..3}: STUN, handed back to the ICE agent via the {@code stunHandler} for
 *       connectivity-check keepalives</li>
 *   <li>{@code 20..63}: DTLS records, driven into the BouncyCastle DTLS state machine through the
 *       internal adapter</li>
 *   <li>{@code 64..79}: TURN ChannelData, also routed to the ICE agent's relay handler</li>
 *   <li>{@code 128..191}: SRTP/SRTCP, forwarded to the RTP layer through the {@code srtpHandler}
 *       once the handshake has completed</li>
 * </ul>
 *
 * <p>Datagrams outside these ranges (for example the ZRTP range {@code 16..19}) are dropped
 * silently.
 *
 * <p>The lifecycle is: construct with the nominated transport, a role, the local certificate, and
 * the peer's expected fingerprint; call {@link #start()} to spawn the handshake virtual thread;
 * call {@link #awaitHandshake(long, TimeUnit)} to block until the handshake completes or fails;
 * after success, read the keys via {@link #srtpEndpoint()} and wire {@link #setSrtpHandler(Consumer)}
 * to the RTP loop; finally call {@link #close()} to drop the handshake thread, close the transport,
 * and release native state. The driver is {@link AutoCloseable}.
 */
public final class DtlsSrtpDriver implements AutoCloseable {
    /**
     * Receive and send size limit reported to the BouncyCastle DTLS layer.
     *
     * @implNote This implementation reports 1500 bytes, larger than any realistic UDP datagram, so
     * that the adapter's {@code receive} can copy a whole inbound record without truncation.
     */
    private static final int MTU = 1500;

    /**
     * Connected datagram transport carrying all multiplexed traffic, normally the ICE-nominated
     * path.
     */
    private final DatagramTransport transport;

    /**
     * BouncyCastle endpoint that runs the actual handshake for the configured role.
     */
    private final DtlsSrtpEndpoint endpoint;

    /**
     * Adapter bridging the BouncyCastle DTLS layer to {@link #transport}: outbound DTLS records are
     * written straight to the transport, inbound records demultiplexed off the wire are queued for
     * the handshake thread to consume.
     */
    private final BcAdapter adapter;

    /**
     * Latch released once {@link #handshakeResult} has been populated with either the negotiated
     * endpoint or the handshake failure.
     */
    private final CountDownLatch handshakeDone = new CountDownLatch(1);

    /**
     * Holds the handshake outcome, remaining {@code null} until {@link #handshakeDone} fires.
     */
    private final AtomicReference<HandshakeResult> handshakeResult = new AtomicReference<>();

    /**
     * Listener for inbound STUN bytes (byte-0 in {@code 0..3}) and TURN ChannelData
     * (byte-0 in {@code 64..79}), set by the call layer to route back to the ICE agent.
     */
    private volatile Consumer<byte[]> stunHandler;

    /**
     * Listener for inbound SRTP/SRTCP bytes (byte-0 in {@code 128..191}), set by the call layer to
     * forward to the RTP loop once the handshake completes.
     */
    private volatile Consumer<byte[]> srtpHandler;

    /**
     * Virtual thread running {@link #runHandshake()}, remaining {@code null} until {@link #start()}.
     */
    private Thread handshakeThread;

    /**
     * Set once {@link #close()} runs, after which inbound packets are dropped.
     */
    private volatile boolean closed;

    /**
     * Carries the outcome of the handshake, either a success holding the negotiated endpoint and
     * application-data transport or a failure holding the {@link IOException} that ended it.
     *
     * @param srtp    the negotiated SRTP endpoint, or {@code null} on failure
     * @param dtls    the BouncyCastle application-data DTLS transport, or {@code null} on failure
     * @param failure the exception that ended the handshake, or {@code null} on success
     */
    private record HandshakeResult(SrtpEndpoint srtp, DTLSTransport dtls, IOException failure) {
        /**
         * Wraps a successful handshake.
         *
         * @param srtp the negotiated SRTP endpoint
         * @param dtls the BouncyCastle application-data DTLS transport
         * @return a success result
         */
        static HandshakeResult success(SrtpEndpoint srtp, DTLSTransport dtls) {
            return new HandshakeResult(srtp, dtls, null);
        }

        /**
         * Wraps a failed handshake.
         *
         * @param failure the {@link IOException} that ended the handshake
         * @return a failure result
         */
        static HandshakeResult failure(IOException failure) {
            return new HandshakeResult(null, null, failure);
        }
    }

    /**
     * Constructs a driver bound to the given transport, role, local certificate, and expected peer
     * fingerprint.
     *
     * <p>The constructor builds the BouncyCastle adapter, creates the client or server
     * {@link DtlsSrtpEndpoint} for the requested {@code role}, and registers
     * {@link #demuxInbound(byte[])} as the transport's inbound listener. It does not begin the
     * handshake; call {@link #start()} for that.
     *
     * @param transport               the datagram path, normally the ICE-nominated one
     * @param role                    {@link SrtpRole#CLIENT} or {@link SrtpRole#SERVER}
     * @param localCert               the local certificate and private key
     * @param expectedPeerFingerprint the peer's 32-byte SHA-256 fingerprint, exchanged out-of-band
     *                                via call signaling
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code expectedPeerFingerprint} is not exactly 32 bytes
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
     * Returns the underlying datagram transport.
     *
     * @return the transport
     */
    public DatagramTransport transport() {
        return transport;
    }

    /**
     * Registers a listener for inbound STUN and TURN ChannelData bytes.
     *
     * <p>The call layer pipes these back to its ICE agent for connectivity-check keepalives and
     * consent freshness.
     *
     * @param handler the listener, or {@code null} to clear it
     */
    public void setStunHandler(Consumer<byte[]> handler) {
        this.stunHandler = handler;
    }

    /**
     * Registers a listener for inbound SRTP/SRTCP bytes, wired to the RTP loop after the handshake.
     *
     * @param handler the listener, or {@code null} to clear it
     */
    public void setSrtpHandler(Consumer<byte[]> handler) {
        this.srtpHandler = handler;
    }

    /**
     * Spawns the handshake virtual thread.
     *
     * <p>The thread runs {@link #runHandshake()}. The method is idempotent: a second call while a
     * thread already exists is a no-op. It is synchronized against {@link #close()}.
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
     * Blocks until the handshake completes successfully, fails, or the given timeout elapses.
     *
     * <p>On success the negotiated {@link SrtpEndpoint} is returned. A handshake failure is rethrown
     * as the {@link IOException} that ended it, and a timeout is reported as a fresh
     * {@link IOException}.
     *
     * @param timeout the maximum time to wait
     * @param unit    the unit of {@code timeout}
     * @return the negotiated {@link SrtpEndpoint}
     * @throws IOException          if the handshake failed or the wait timed out
     * @throws InterruptedException if the calling thread is interrupted while waiting
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
     * Returns the negotiated {@link SrtpEndpoint}, or {@code null} if the handshake has not yet
     * completed successfully.
     *
     * @return the SRTP endpoint, or {@code null}
     */
    public SrtpEndpoint srtpEndpoint() {
        var result = handshakeResult.get();
        return result == null ? null : result.srtp();
    }

    /**
     * Returns the BouncyCastle {@link DTLSTransport} for the application-data layer of the
     * negotiated DTLS session, or {@code null} if the handshake has not yet completed successfully.
     *
     * <p>The data-channel layer sends SCTP packets through this transport:
     * {@code send(buf, off, len)} writes one encrypted record and
     * {@code receive(buf, off, len, waitMillis)} reads the next decrypted one.
     *
     * @return the BouncyCastle DTLS transport, or {@code null}
     */
    public DTLSTransport dtlsTransport() {
        var result = handshakeResult.get();
        return result == null ? null : result.dtls();
    }

    /**
     * Sends an already-protected SRTP/SRTCP packet over the underlying transport.
     *
     * <p>The caller is responsible for having encrypted the packet through the negotiated
     * {@link SrtpEndpoint} beforehand. The call is a no-op once the driver is closed.
     *
     * @param packet the protected SRTP/SRTCP bytes
     * @throws NullPointerException if {@code packet} is {@code null}
     */
    public void sendSrtp(byte[] packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (closed) {
            return;
        }
        transport.send(packet);
    }

    /**
     * Sends a STUN packet over the underlying transport.
     *
     * <p>Used by the ICE agent for keepalives and consent-freshness checks after the handshake
     * completes. The call is a no-op once the driver is closed.
     *
     * @param packet the STUN bytes
     * @throws NullPointerException if {@code packet} is {@code null}
     */
    public void sendStun(byte[] packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (closed) {
            return;
        }
        transport.send(packet);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Marks the driver closed, closes the internal adapter and the underlying transport,
     * interrupts the handshake thread and joins it for up to one second, and unblocks any thread
     * waiting in {@link #awaitHandshake(long, TimeUnit)} by publishing a failure result if none was
     * published yet. The method is idempotent and synchronized against {@link #start()}.
     *
     * @implNote This implementation publishes a failure result via
     * {@link AtomicReference#compareAndSet(Object, Object)} so that a {@link #close()} racing ahead
     * of the handshake thread's own result publication cannot leave {@link #awaitHandshake(long, TimeUnit)}
     * blocked forever.
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
        if (handshakeResult.compareAndSet(null,
                HandshakeResult.failure(new IOException("driver closed before handshake completed")))) {
            handshakeDone.countDown();
        }
    }

    /**
     * Demultiplexes one inbound datagram by its first byte per RFC 7983 and routes it to the STUN
     * handler, the DTLS adapter, or the SRTP handler.
     *
     * <p>Bytes {@code 0..3} (STUN) and {@code 64..79} (TURN ChannelData) go to the {@code stunHandler};
     * bytes {@code 20..63} (DTLS) are delivered into the adapter's inbound queue; bytes
     * {@code 128..191} (SRTP/SRTCP) go to the {@code srtpHandler}; all other ranges are dropped.
     * Handler invocations are guarded so a throwing listener cannot break the demultiplexer. Called
     * synchronously on whichever thread the transport's inbound listener fires on, and a no-op once
     * the driver is closed or the packet is empty.
     *
     * @param packet the inbound bytes
     */
    private void demuxInbound(byte[] packet) {
        if (closed || packet == null || packet.length == 0) {
            return;
        }
        var b0 = packet[0] & 0xFF;
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
        // Non-RTP/RTCP, non-DTLS, non-STUN bytes (relay Allocate-response echoes and the relay's 0x08
        // keepalive replies) are not part of the media demux and are silently dropped.
    }

    /**
     * Runs the handshake on the virtual thread, publishing the negotiated endpoint and transport on
     * success or a failure result on error.
     *
     * <p>Drives {@link DtlsSrtpEndpoint#handshakeWithDtls()} through the adapter. A thrown
     * {@link IOException} is published verbatim; a thrown {@link RuntimeException} is wrapped in an
     * {@link IOException}. Either way {@link #handshakeDone} is counted down in a {@code finally}
     * block so waiters always unblock. The result is published with
     * {@link AtomicReference#compareAndSet(Object, Object)} so a concurrent {@link #close()} cannot
     * be overwritten.
     */
    private void runHandshake() {
        try {
            var result = endpoint.handshakeWithDtls();
            handshakeResult.compareAndSet(null,
                    HandshakeResult.success(result.srtp(), result.dtls()));
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
     * Adapts the owning driver's {@link DatagramTransport} to BouncyCastle's
     * {@link org.bouncycastle.tls.DatagramTransport} contract.
     *
     * <p>Inbound DTLS records arrive via {@link #deliverInbound(byte[])}, called by the
     * demultiplexer, and are queued for {@link #receive(byte[], int, int, int)} to drain; outbound
     * records written by the DTLS layer are copied and forwarded straight to the owner's transport.
     */
    private static final class BcAdapter implements org.bouncycastle.tls.DatagramTransport {
        /**
         * Owning driver, used to write outbound bytes and observe the closed flag.
         */
        private final DtlsSrtpDriver owner;

        /**
         * Queue of inbound DTLS records populated by {@link #deliverInbound(byte[])} and drained by
         * {@link #receive(byte[], int, int, int)}.
         */
        private final LinkedBlockingQueue<byte[]> inbound = new LinkedBlockingQueue<>();

        /**
         * Zero-length sentinel enqueued by {@link #close()} to wake any thread blocked in
         * {@link #receive(byte[], int, int, int)}.
         */
        private static final byte[] CLOSE_SENTINEL = new byte[0];

        /**
         * Set once {@link #close()} runs, after which inbound records are discarded and reads fail.
         */
        private volatile boolean adapterClosed;

        /**
         * Constructs an adapter bound to the owning driver.
         *
         * @param owner the driver that owns this adapter
         */
        BcAdapter(DtlsSrtpDriver owner) {
            this.owner = owner;
        }

        /**
         * Enqueues one inbound DTLS record for the handshake thread to consume, or discards it if
         * the adapter is closed.
         *
         * @param packet the DTLS record bytes
         */
        void deliverInbound(byte[] packet) {
            if (adapterClosed) {
                return;
            }
            inbound.offer(packet);
        }

        /**
         * {@inheritDoc}
         *
         * @return the receive size limit, {@link DtlsSrtpDriver#MTU} bytes
         */
        @Override
        public int getReceiveLimit() {
            return MTU;
        }

        /**
         * {@inheritDoc}
         *
         * @return the send size limit, {@link DtlsSrtpDriver#MTU} bytes
         */
        @Override
        public int getSendLimit() {
            return MTU;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Takes the next queued DTLS record, blocking indefinitely when {@code waitMillis} is not
         * positive or up to {@code waitMillis} otherwise, and copies up to {@code len} bytes into
         * {@code buf} at {@code off}. Returns {@code -1} when a bounded wait expires with no record,
         * and throws when the adapter is closed (including when the close sentinel is dequeued).
         *
         * @return the number of bytes copied, or {@code -1} if the wait expired with no record
         * @throws IOException if the adapter is closed or the wait is interrupted
         */
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
            var n = Math.min(pkt.length, len);
            System.arraycopy(pkt, 0, buf, off, n);
            return n;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Copies {@code len} bytes from {@code buf} at {@code off} into a fresh array and
         * forwards it to the owner's transport.
         *
         * @throws IOException if the adapter or the owning driver is closed
         */
        @Override
        public void send(byte[] buf, int off, int len) throws IOException {
            if (adapterClosed || owner.closed) {
                throw new IOException("DTLS transport closed");
            }
            var copy = new byte[len];
            System.arraycopy(buf, off, copy, 0, len);
            owner.transport.send(copy);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Marks the adapter closed and enqueues {@link #CLOSE_SENTINEL} so a thread blocked in
         * {@link #receive(byte[], int, int, int)} wakes and fails.
         */
        @Override
        public void close() {
            adapterClosed = true;
            inbound.offer(CLOSE_SENTINEL);
        }
    }
}
