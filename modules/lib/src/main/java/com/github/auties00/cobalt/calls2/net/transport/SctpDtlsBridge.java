package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Seals the two ways an {@link SctpAssociation} reaches the wire, isolating the SCTP stack from whether
 * the SCTP packets are wrapped in a DTLS record before they leave.
 *
 * <p>The two implementations differ in exactly one respect that matters to SCTP: whether each SCTP packet
 * is wrapped in a DTLS application-data record per RFC 8261 before it leaves. The WhatsApp Web call
 * transport runs DTLS on every leg: it brings the call up over ICE and DTLS and carries the SCTP data
 * channel inside the DTLS-wrapped association, both on the Meta edgeray relay leg and on the Web-P2P interop
 * leg (re/calls2-spec/captures/webrtc-datachannel-transport-2026-06-21.md,
 * re/calls2-spec/web-transport-construction-RE.md). This sealed interface is the seam that hides that
 * wrapping: {@link #wrapOutbound(byte[])} turns an SCTP packet into the bytes the datagram sink ships, and
 * {@link #feedInbound(byte[])} turns an inbound datagram back into the SCTP packet fed to
 * {@link SctpAssociation#feedInboundPacket(byte[])}.
 *
 * <p>It permits exactly two implementations. {@link WebP2p} wraps a {@link VoipDtlsTransport}:
 * outbound packets are sent as DTLS records and a receive pump drains inbound DTLS application-data records
 * into the SCTP layer; this is the bridge {@link RelayDataChannel} uses for the live DTLS-over-SCTP relay
 * leg, so the relay path runs DTLS through it. {@link Relay} is the complementary no-DTLS passthrough that
 * neither encrypts nor decrypts, carrying SCTP datagrams verbatim; it is the seam shape for a leg that ever
 * carries SCTP datagrams without DTLS. The DTLS itself is pure-Java BouncyCastle, never a native binding,
 * because the call's SCTP data channel is low-volume relative to media.
 *
 * @implNote This implementation realises the {@code DtlsPacketEnvelope}-mediated seam between the
 * data-channel controller and the transport in {@code transport/wa_transport.cc} of the wa-voip WASM
 * module {@code ff-tScznZ8P}: {@code transport_send_scpt_dtls_packet} (fn5064) ships an outbound DTLS
 * packet over the elected relay, and {@code on_rx_data_internal} (fn4976) dispatches an inbound DTLS or
 * SCTP record to the data-channel controller and implicitly elects the relay. The native build delegates
 * the DTLS record layer to a host-provided transport; Cobalt provides it with BouncyCastle on the
 * {@link WebP2p} path, which the relay leg uses. The {@code DtlsPacketEnvelope} family-and-length
 * discriminator is applied by {@link WebP2p} so the relay-egress bytes match the native wire form.
 */
public sealed interface SctpDtlsBridge extends AutoCloseable permits SctpDtlsBridge.Relay, SctpDtlsBridge.WebP2p {
    /**
     * Transforms one outbound SCTP packet into the bytes to put on the wire.
     *
     * <p>On the {@link Relay} path the packet is returned unchanged. On the {@link WebP2p} path the packet
     * is sent through the DTLS transport, which encrypts it into a DTLS application-data record and emits
     * the record bytes to the configured datagram sink; in that case this method drives the send and
     * returns {@code null}, because the wrapped bytes leave through the sink rather than the return value.
     *
     * @param sctpPacket the SCTP packet produced by usrsctp's conn-output callback
     * @return the bytes to put on the wire for a passthrough bridge, or {@code null} when the bytes have
     * already been dispatched to the sink
     * @throws NullPointerException              if {@code sctpPacket} is {@code null}
     * @throws WhatsAppCallException.DataChannel if the DTLS transport fails to send the packet
     */
    byte[] wrapOutbound(byte[] sctpPacket);

    /**
     * Feeds one inbound datagram through the bridge into the SCTP stack.
     *
     * <p>On the {@link Relay} path the datagram is handed straight to
     * {@link SctpAssociation#feedInboundPacket(byte[])}. On the {@link WebP2p} path the bytes are queued
     * into the DTLS transport's inbound side; the bridge's receive pump decrypts them and feeds the
     * resulting SCTP packet to the association asynchronously, so callers must not assume the SCTP layer
     * has seen the packet by the time this returns.
     *
     * @param datagram the inbound transport bytes (an SCTP packet on the relay path, a DTLS record on the
     *                 Web-P2P path)
     * @throws NullPointerException if {@code datagram} is {@code null}
     */
    void feedInbound(byte[] datagram);

    /**
     * Releases the resources the bridge holds.
     *
     * <p>For {@link Relay} this is a no-op. For {@link WebP2p} it closes the DTLS transport and stops the
     * receive pump. The method is idempotent.
     */
    @Override
    void close();

    /**
     * Passes SCTP packets straight to and from the wire with no DTLS, the complementary no-DTLS variant of
     * the bridge.
     *
     * <p>This bridge neither encrypts nor decrypts: it returns outbound packets unchanged for the caller to
     * ship, and feeds inbound datagrams directly to {@link SctpAssociation#feedInboundPacket(byte[])}. It
     * holds no native or cryptographic state, so {@link #close()} does nothing. The live WhatsApp Web relay
     * leg runs DTLS through {@link WebP2p} rather than this passthrough
     * (re/calls2-spec/captures/webrtc-datachannel-transport-2026-06-21.md); this variant exists for a leg
     * that ever carries SCTP datagrams without DTLS.
     *
     * @implNote This implementation is the no-DTLS branch of the SCTP-over-DTLS seam: when no DTLS wraps the
     * SCTP packets the framing collapses to a passthrough. The {@code SctpAssociation} reference is held so a
     * DataChannel driven without DTLS can be fed; if it is {@code null} the inbound feed is dropped.
     */
    final class Relay implements SctpDtlsBridge {
        /**
         * Holds the SCTP association inbound datagrams are fed into, or {@code null} when no association is
         * wired to this bridge.
         */
        private final SctpAssociation association;

        /**
         * Constructs a passthrough bridge feeding the given association.
         *
         * @param association the SCTP association to feed inbound datagrams into, or {@code null} when the
         *                    relay path carries no SCTP DataChannel and inbound feeds should be dropped
         */
        public Relay(SctpAssociation association) {
            this.association = association;
        }

        /**
         * {@inheritDoc}
         *
         * @param sctpPacket {@inheritDoc}
         * @return the same packet bytes, unchanged, for the caller to ship over the relay
         * @throws NullPointerException if {@code sctpPacket} is {@code null}
         */
        @Override
        public byte[] wrapOutbound(byte[] sctpPacket) {
            Objects.requireNonNull(sctpPacket, "sctpPacket cannot be null");
            return sctpPacket;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Feeds the datagram straight to the wired association; a {@code null} association drops it.
         *
         * @param datagram {@inheritDoc}
         * @throws NullPointerException if {@code datagram} is {@code null}
         */
        @Override
        public void feedInbound(byte[] datagram) {
            Objects.requireNonNull(datagram, "datagram cannot be null");
            if (association != null) {
                association.feedInboundPacket(datagram);
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p>Does nothing, because the passthrough bridge holds no resources.
         */
        @Override
        public void close() {
        }
    }

    /**
     * Wraps SCTP packets in DTLS application-data records over a {@link VoipDtlsTransport}, for the Web-P2P
     * interop path.
     *
     * <p>This bridge owns both directions. Outbound: {@link #wrapOutbound(byte[])} sends the SCTP packet
     * through {@link VoipDtlsTransport#send(byte[], int, int)}, which encrypts it into a DTLS record and writes
     * it to the {@link VoipDtlsTransport.Datagrams} seam the caller gave the handshake, wired to the relay
     * datagram sink, so the enveloped bytes leave through the sink. Inbound: a receive pump on a dedicated
     * daemon virtual thread blocks in {@link VoipDtlsTransport#receive(byte[], int, int, int)} until a record
     * arrives, then hands the decrypted SCTP packet to {@link SctpAssociation#feedInboundPacket(byte[])}.
     * {@link #feedInbound(byte[])} pushes raw inbound DTLS bytes into the DTLS transport's datagram side so the
     * pump can decrypt them.
     *
     * @implNote This implementation runs the receive pump with a short {@value #RECEIVE_TIMEOUT_MILLIS}-ms
     * per-call receive timeout so {@link #close()} takes effect promptly rather than waiting for the next
     * inbound record. A {@link RuntimeException} from a single malformed SCTP packet is swallowed so it
     * cannot kill the pump, matching Cobalt's receive-path invariant. The receive buffer is sized at
     * {@value #MAX_RECORD_BYTES} bytes, comfortably above the SCTP-over-DTLS path MTU, so one receive can
     * never overflow it. The DTLS record layer is the JDK's own {@code DTLSv1.2} engine rather than a native
     * binding because the call's SCTP data channel is low-volume relative to media; the relay leg uses this
     * same bridge to wrap its SCTP association in DTLS (re/calls2-spec/captures/webrtc-datachannel-transport-2026-06-21.md).
     */
    final class WebP2p implements SctpDtlsBridge {
        /**
         * Holds the maximum DTLS record size, in bytes, the receive buffer is sized for.
         *
         * @implNote This implementation uses 16 KiB, comfortably above the SCTP-over-DTLS path MTU, so a
         * single {@link VoipDtlsTransport} receive can never overflow the buffer.
         */
        private static final int MAX_RECORD_BYTES = 16 * 1024;

        /**
         * Holds the per-call receive timeout, in milliseconds.
         *
         * @implNote This implementation uses 200 ms, short enough that {@link #close()} returns promptly
         * without waiting on the next inbound record while still keeping the pump's wakeup rate low.
         */
        private static final int RECEIVE_TIMEOUT_MILLIS = 200;

        /**
         * Holds the established DTLS application-data transport this bridge sends and receives records on.
         */
        private final VoipDtlsTransport dtls;

        /**
         * Holds the SCTP association decrypted inbound packets are fed into.
         */
        private final SctpAssociation association;

        /**
         * Holds the sink raw inbound DTLS bytes are queued into so the DTLS transport's datagram side can
         * read them, or {@code null} when inbound bytes reach the DTLS transport by another route.
         *
         * <p>The Web-P2P path layers the JDK DTLS engine over a {@link VoipDtlsTransport.Datagrams} seam the
         * caller wires to the UDP flow; {@link #feedInbound(byte[])} forwards inbound bytes to this sink, which
         * the caller has connected to that seam's receive queue.
         */
        private final Consumer<byte[]> inboundDatagramSink;

        /**
         * Holds the daemon virtual thread that runs the inbound receive pump.
         */
        private final Thread receiver;

        /**
         * Tracks whether the bridge has been closed, guarding {@link #close()} against running its teardown
         * twice.
         */
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * Wires the DTLS bridge and starts the inbound receive pump.
         *
         * @param dtls                the established DTLS application-data transport
         * @param association         the SCTP association decrypted inbound packets are fed into
         * @param inboundDatagramSink the sink raw inbound DTLS bytes are forwarded to so the DTLS
         *                            transport's datagram side can read them, or {@code null} when inbound
         *                            bytes reach the DTLS transport by another route
         * @throws NullPointerException if {@code dtls} or {@code association} is {@code null}
         */
        public WebP2p(VoipDtlsTransport dtls, SctpAssociation association, Consumer<byte[]> inboundDatagramSink) {
            this.dtls = Objects.requireNonNull(dtls, "dtls cannot be null");
            this.association = Objects.requireNonNull(association, "association cannot be null");
            this.inboundDatagramSink = inboundDatagramSink;
            this.receiver = Thread.ofVirtual()
                    .name("calls2-sctp-dtls-bridge-rx")
                    .unstarted(this::receiveLoop);
            this.receiver.setDaemon(true);
            this.receiver.start();
        }

        /**
         * {@inheritDoc}
         *
         * <p>Sends the SCTP packet through the DTLS transport, which encrypts it into a record and writes
         * that record to the underlying datagram transport (the relay sink). The enveloped bytes therefore
         * leave through the sink, so this method returns {@code null}.
         *
         * @param sctpPacket {@inheritDoc}
         * @return always {@code null}, because the DTLS record leaves through the underlying datagram
         * transport
         * @throws NullPointerException             if {@code sctpPacket} is {@code null}
         * @throws WhatsAppCallException.DataChannel if the DTLS transport fails to send the packet
         */
        @Override
        public byte[] wrapOutbound(byte[] sctpPacket) {
            Objects.requireNonNull(sctpPacket, "sctpPacket cannot be null");
            if (closed.get()) {
                throw new WhatsAppCallException.DataChannel("DTLS bridge is closed");
            }
            try {
                // TODO: wire Web-P2P DtlsPacketEnvelope - in WebP2p.wrapOutbound, route the outbound record through DtlsPacketEnvelope.of(sctpPacket).relayPacket() (validating the sockaddr_conn family==2/addr_len==0x10 discriminator) before dtls.send()
                // TODO: wire SctpRingBuffer egress - route outbound SCTP DATA through SctpRingBuffer.write + a drain loop (FrameSink) instead of synchronous per-datagram send, matching initSctpRingBuffer/shutdownSctpRingBuffer lifecycle
                dtls.send(sctpPacket, 0, sctpPacket.length);
            } catch (IOException e) {
                throw new WhatsAppCallException.DataChannel("DTLS send failed", e);
            }
            return null;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Forwards the raw inbound DTLS bytes to the datagram sink the DTLS transport reads from, so the
         * receive pump can decrypt them. A {@code null} sink means inbound bytes reach the DTLS transport
         * by another route and this call is a no-op.
         *
         * @param datagram {@inheritDoc}
         * @throws NullPointerException if {@code datagram} is {@code null}
         */
        @Override
        public void feedInbound(byte[] datagram) {
            Objects.requireNonNull(datagram, "datagram cannot be null");
            if (inboundDatagramSink != null) {
                inboundDatagramSink.accept(datagram);
            }
        }

        /**
         * Runs the inbound receive pump until the bridge is closed or the thread is interrupted.
         *
         * <p>Each iteration reads one DTLS record with the {@link #RECEIVE_TIMEOUT_MILLIS} timeout into a
         * reused buffer; a non-positive read (timeout or empty record) is retried, and any other read is
         * copied into a fresh array and handed to {@link SctpAssociation#feedInboundPacket(byte[])}. An
         * {@link IOException} ends the pump, and a {@link RuntimeException} from a malformed packet is
         * swallowed so the pump survives it.
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
                    association.feedInboundPacket(bytes);
                } catch (RuntimeException _) {
                }
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p>The method is idempotent: a second call returns immediately. It closes the
         * {@link VoipDtlsTransport} and interrupts the receive thread so it observes the closed state and exits.
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            dtls.close();
            receiver.interrupt();
        }
    }
}
