package com.github.auties00.cobalt.call.transport.sctp;

import com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelTransport;
import org.bouncycastle.tls.DTLSTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drains inbound DTLS application-data records into a {@link DataChannelTransport}'s
 * {@link DataChannelTransport#feedInboundPacket(byte[]) feedInboundPacket} entry point.
 *
 * <p>WebRTC layers SCTP on top of DTLS (RFC 8261): each SCTP packet travels as one application-data DTLS record, and the
 * same DTLS handshake that derives the SRTP keying material also produces the application-data transport these records
 * ride on. This bridge owns the inbound direction only: it runs a receive pump that blocks in {@link DTLSTransport}'s
 * receive call until a record arrives, copies the record into a fresh array, and hands it to the SCTP layer. Outbound
 * packets do not pass through this bridge; they leave the SCTP layer directly through the {@code outboundSink} given to
 * {@link DataChannelTransport} at construction, which the caller wires to {@link DTLSTransport}'s send call so the two
 * directions are symmetric.
 *
 * <p>The bridge is {@link AutoCloseable}; closing it shuts the DTLS transport and interrupts the pump.
 *
 * @implNote This implementation runs the receive pump on a dedicated daemon virtual thread and reads with a short
 * 200 ms per-call timeout so that {@link #close()} takes effect promptly rather than waiting for the next inbound
 * record. A {@link RuntimeException} from a single malformed SCTP packet is swallowed so it cannot kill the pump.
 */
public final class SctpDtlsBridge implements AutoCloseable {
    /**
     * Holds the maximum DTLS record size, in bytes, that the receive buffer is sized for.
     *
     * @implNote This implementation uses 16 KiB, comfortably above the SCTP-over-DTLS path MTU, so a single
     * {@link DTLSTransport} receive can never overflow the buffer.
     */
    private static final int MAX_RECORD_BYTES = 16 * 1024;

    /**
     * Holds the per-call receive timeout, in milliseconds.
     *
     * @implNote This implementation uses 200 ms, short enough that {@link #close()} returns promptly without waiting on
     * the next inbound record while still keeping the pump's wakeup rate low.
     */
    private static final int RECEIVE_TIMEOUT_MILLIS = 200;

    /**
     * Holds the DTLS application-data transport this bridge reads inbound records from.
     */
    private final DTLSTransport dtls;

    /**
     * Holds the SCTP transport this bridge feeds inbound bytes into.
     */
    private final DataChannelTransport sctp;

    /**
     * Holds the daemon virtual thread that runs the inbound receive pump.
     */
    private final Thread receiver;

    /**
     * Tracks whether the bridge has been closed, guarding {@link #close()} against running its teardown twice.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Wires the bridge and starts the inbound receive pump.
     *
     * @param dtls the DTLS application-data transport to read inbound records from
     * @param sctp the SCTP transport to feed those records into
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
     * Runs the inbound receive pump until the bridge is closed or the thread is interrupted.
     *
     * <p>Each iteration reads one DTLS record with the {@link #RECEIVE_TIMEOUT_MILLIS} timeout into a reused buffer; a
     * non-positive read (timeout or empty record) is retried, and any other read is copied into a fresh array and
     * handed to {@link DataChannelTransport#feedInboundPacket(byte[])}. An {@link IOException} ends the pump, and a
     * {@link RuntimeException} from a malformed packet is swallowed so the pump survives it.
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
            }
        }
    }

    /**
     * Closes the bridge, shutting the DTLS transport and stopping the receive pump.
     *
     * <p>The method is idempotent: a second call returns immediately. It closes the {@link DTLSTransport} (any
     * {@link IOException} is swallowed) and interrupts the receive thread so it observes the closed state and exits.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            dtls.close();
        } catch (IOException _) {
        }
        receiver.interrupt();
    }
}
