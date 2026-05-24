package com.github.auties00.cobalt.call.internal.transport.sctp;

import com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelTransport;
import org.bouncycastle.tls.DTLSTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drains inbound DTLS application-data records into a
 * {@link DataChannelTransport}'s
 * {@link DataChannelTransport#feedInboundPacket(byte[])
 * feedInboundPacket} entry point.
 *
 * <p>WebRTC layers SCTP on top of DTLS: each SCTP packet becomes one
 * application-data DTLS record. The DTLS handshake that produces the
 * SRTP keying material also produces an application-data transport
 * for these records.
 *
 * <p>This bridge owns ONLY the inbound direction. Outbound packets
 * leave the SCTP layer directly through the
 * {@code outboundSink} given to {@link DataChannelTransport} at
 * construction — wire that sink to call
 * {@code dtls.send(packet, 0, packet.length)} so the symmetry is
 * complete.
 *
 * <p>The receive pump runs on a dedicated daemon virtual thread that
 * blocks in {@code dtls.receive(...)} until a record arrives or the
 * bridge is closed.
 */
public final class SctpDtlsBridge implements AutoCloseable {
    /**
     * Maximum DTLS record size. The bridge allocates a buffer of this
     * size on each receive() — well above the SCTP-over-DTLS path MTU.
     */
    private static final int MAX_RECORD_BYTES = 16 * 1024;

    /**
     * Receive timeout per call, in milliseconds. Short enough that
     * {@link #close()} closes promptly without waiting for the next
     * inbound record.
     */
    private static final int RECEIVE_TIMEOUT_MILLIS = 200;

    /**
     * The DTLS application-data transport.
     */
    private final DTLSTransport dtls;

    /**
     * The SCTP transport this bridge feeds.
     */
    private final DataChannelTransport sctp;

    /**
     * The inbound receive thread.
     */
    private final Thread receiver;

    /**
     * Closed flag.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Wires the bridge and starts the inbound pump.
     *
     * @param dtls the BC DTLS application-data transport
     * @param sctp the SCTP transport this bridge feeds inbound bytes
     *             into
     * @throws NullPointerException if either argument is {@code null}
     */
    public SctpDtlsBridge(DTLSTransport dtls, DataChannelTransport sctp) {
        this.dtls = Objects.requireNonNull(dtls, "dtls cannot be null");
        this.sctp = Objects.requireNonNull(sctp, "sctp cannot be null");
        this.receiver = Thread.ofVirtual()
                .name("sctp-dtls-bridge-rx")
                .unstarted(this::receiveLoop);
        this.receiver.setDaemon(true);
        this.receiver.start();
    }

    /**
     * Body of the inbound receive thread.
     */
    private void receiveLoop() {
        var buffer = new byte[MAX_RECORD_BYTES];
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            int read;
            try {
                read = dtls.receive(buffer, 0, buffer.length, RECEIVE_TIMEOUT_MILLIS);
            } catch (IOException _) {
                return;
            }
            if (read <= 0) {
                continue;
            }
            var bytes = new byte[read];
            System.arraycopy(buffer, 0, bytes, 0, read);
            try {
                sctp.feedInboundPacket(bytes);
            } catch (RuntimeException _) {
                // Swallow — one malformed SCTP packet must not kill
                // the pump.
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try { dtls.close(); } catch (IOException _) { /* swallow */ }
        receiver.interrupt();
    }
}
