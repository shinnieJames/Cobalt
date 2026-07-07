package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.io.IOException;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The relay-path {@link LiveRelayTransport.DataChannel}: it runs the DTLS handshake against a Meta edgeray
 * relay, connects one SCTP association over the resulting DTLS transport, and carries every media and
 * control message as SCTP DATA on a single pre-negotiated data channel.
 *
 * <p>The WhatsApp Web relay transport is an {@code RTCPeerConnection} whose synthesized remote answer points
 * a single host ICE candidate at the relay and pins one well-known DTLS certificate; once ICE selects that
 * pair the client runs DTLS as the client (the relay is the DTLS server unless the relay block sets
 * {@code enable_edgeray_dtls_active_mode}, which flips the client to the active role), opens one SCTP
 * association on port {@value #SCTP_PORT}, and creates one data channel that is pre-negotiated
 * ({@code negotiated=true, id=0, ordered=false, maxRetransmits=0}) so no RFC 8832 DCEP open exchange is
 * sent. The engine multiplexes RTP, RTCP, application STUN, WARP, and the subscription over that one channel
 * as SCTP DATA; this backend is the opaque pipe that carries those bytes verbatim.
 *
 * <p>Outbound SCTP packets the association produces are wrapped in DTLS application-data records by the
 * {@link SctpDtlsBridge.WebP2p} bridge and written to the host UDP egress through a BouncyCastle
 * {@link DatagramTransport}; inbound DTLS records arrive through {@link #feedDtlsRecord(byte[])}, are queued
 * to that same datagram transport, decrypted by the bridge's receive pump, and fed to the SCTP stack, whose
 * decoded application messages reach the consumer {@link #onMessage(Consumer)} registered. The certificate
 * the relay presents is not signaled per call; the handshake pins the fixed relay fingerprint
 * {@link #RELAY_CERT_FINGERPRINT_SHA256}.
 *
 * @implNote This implementation wires the BouncyCastle pure-Java DTLS record layer (the relay path is
 *           low-volume and never needs a native DTLS binding) to Cobalt's usrsctp {@link SctpAssociation}
 *           through the existing {@link SctpDtlsBridge.WebP2p} seam, reproducing the
 *           {@code WAWebVoipSctpConnectionManager} create-offer / synthesize-answer / open-pre-negotiated-
 *           channel flow of WhatsApp Web restricted to the relay endpoint. The handshake, the SCTP connect,
 *           and the channel open all run on the calling bring-up thread inside {@link #connect()}, which
 *           blocks until the channel is up or throws; the SCTP socket is opened with the WebRTC defaults the
 *           {@link SctpAssociation} constructor applies.
 */
public final class RelayDataChannel implements LiveRelayTransport.DataChannel {
    /**
     * The SCTP port WebRTC data channels use at both ends (RFC 8831).
     */
    public static final int SCTP_PORT = 5000;

    /**
     * The pre-negotiated data channel's stream id.
     *
     * @implNote This implementation uses {@code 0}, the id WhatsApp Web hardcodes when it passes
     * {@code createDataChannel("pre-negotiated", {negotiated:true, id:0, ...})}. Because the channel is
     * pre-negotiated, the id is fixed at {@code 0} regardless of which side takes the DTLS client role, so it
     * does not depend on {@link #relayActiveMode}.
     */
    public static final int CHANNEL_STREAM_ID = 0;

    /**
     * The SCTP Payload Protocol Identifier for a binary WebRTC data-channel message (RFC 8831).
     *
     * @implNote This implementation uses {@code 53} ("WebRTC Binary"): the engine ships every multiplexed
     * datagram as opaque binary application data, so each {@link #send(byte[])} payload travels under this
     * PPID and the receive path delivers any message carrying it to the registered consumer.
     */
    public static final int PPID_BINARY = 53;

    /**
     * The pinned SHA-256 fingerprint of the relay's DTLS certificate, as the thirty-two raw digest bytes.
     *
     * <p>The relay path does not signal a per-call {@code <certificate>}; the relay presents a fixed,
     * well-known certificate and the client pins this fingerprint. It corresponds to the colon-separated
     * form {@code F9:CA:0C:98:A3:CC:71:D6:42:CE:5A:E2:53:D2:15:20:D3:1B:BA:D8:57:A4:F0:AF:BE:0B:FB:F3:6B:0C:A0:68}.
     *
     * @implNote This implementation pins the fingerprint WhatsApp Web hardcodes into its synthesized relay
     * answer SDP ({@code a=fingerprint:sha-256 F9:CA:...:A0:68}). It is version-pinned: the relay certificate
     * may rotate, so this constant is re-extracted per WA Web snapshot rather than computed.
     */
    public static final byte[] RELAY_CERT_FINGERPRINT_SHA256 = {
            (byte) 0xF9, (byte) 0xCA, (byte) 0x0C, (byte) 0x98, (byte) 0xA3, (byte) 0xCC, (byte) 0x71, (byte) 0xD6,
            (byte) 0x42, (byte) 0xCE, (byte) 0x5A, (byte) 0xE2, (byte) 0x53, (byte) 0xD2, (byte) 0x15, (byte) 0x20,
            (byte) 0xD3, (byte) 0x1B, (byte) 0xBA, (byte) 0xD8, (byte) 0x57, (byte) 0xA4, (byte) 0xF0, (byte) 0xAF,
            (byte) 0xBE, (byte) 0x0B, (byte) 0xFB, (byte) 0xF3, (byte) 0x6B, (byte) 0x0C, (byte) 0xA0, (byte) 0x68
    };

    /**
     * The maximum data-channel message size advertised to the SCTP stack, in bytes.
     *
     * @implNote This implementation uses {@code 1500}, the value WhatsApp Web rewrites the synthesized
     * answer's {@code a=max-message-size} to; every framed datagram the engine ships fits in one SCTP DATA
     * message of at most this size.
     */
    public static final int MAX_MESSAGE_SIZE = 1500;

    /**
     * The maximum time the SCTP handshake is allowed to take before {@link #connect()} fails, in seconds.
     *
     * @implNote This implementation bounds the {@link SctpAssociation#connect(int, long, TimeUnit)} wait so a
     * relay that completes DTLS but never answers the SCTP INIT fails the bring-up rather than blocking the
     * bring-up thread forever.
     */
    private static final int SCTP_CONNECT_TIMEOUT_SECONDS = 10;

    /**
     * The maximum time the DTLS handshake is allowed to take before {@link #connect()} fails, in milliseconds.
     *
     * @implNote This implementation bounds the {@link VoipDtlsTransport#handshake(long)} wait so a relay that
     * never completes DTLS fails the bring-up rather than retransmitting forever.
     */
    private static final long DTLS_HANDSHAKE_TIMEOUT_MILLIS = 10_000L;

    /**
     * The per-receive datagram-transport read timeout, in milliseconds.
     *
     * @implNote This implementation uses a short timeout so the bridge's receive pump and the DTLS handshake
     * wake up promptly when {@link #close()} drains the inbound queue with a poison record.
     */
    private static final int DATAGRAM_RECEIVE_TIMEOUT_MILLIS = 200;

    /**
     * The bounded depth of the inbound DTLS-record queue.
     *
     * @implNote This implementation bounds the queue so a relay flooding records cannot grow it without
     * limit; a record offered to a full queue is dropped, which the DTLS retransmission recovers.
     */
    private static final int INBOUND_QUEUE_CAPACITY = 64;

    /**
     * The host UDP egress the DTLS records leave through, addressed to {@link #relayAddress}.
     */
    private final LiveRelayTransport.Egress egress;

    /**
     * The relay transport address the DTLS records are sent to.
     */
    private final SocketAddress relayAddress;

    /**
     * Whether the relay enabled DTLS active mode, flipping the relay to the DTLS client role.
     */
    private final boolean relayActiveMode;

    /**
     * The pinned relay certificate SHA-256 fingerprint the handshake verifies the server certificate against.
     */
    private final byte[] pinnedFingerprint;

    /**
     * The bounded inbound queue the DTLS datagram transport reads records from.
     *
     * <p>{@link #feedDtlsRecord(byte[])} offers inbound DTLS records here; the {@link RelayDatagramTransport}
     * polls it with the receive timeout so a closed channel can unblock the pump with a poison record.
     */
    private final LinkedBlockingQueue<byte[]> inbound = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

    /**
     * The consumer that receives each inbound SCTP DATA application message, set through
     * {@link #onMessage(Consumer)}.
     */
    private final AtomicReference<Consumer<byte[]>> messageConsumer = new AtomicReference<>();

    /**
     * Whether the data channel has completed bring-up and can carry application data.
     */
    private final AtomicBoolean ready = new AtomicBoolean();

    /**
     * Whether the channel has been closed, guarding the bring-up and the teardown against running twice.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * The SCTP association opened over the DTLS transport, or {@code null} until {@link #connect()} runs.
     */
    private volatile SctpAssociation association;

    /**
     * The DTLS-over-datagram bridge that wraps SCTP packets in DTLS records, or {@code null} until
     * {@link #connect()} runs.
     */
    private volatile SctpDtlsBridge bridge;

    /**
     * The poison record offered to {@link #inbound} on close so a blocked datagram read returns at once.
     */
    private static final byte[] POISON = new byte[0];

    /**
     * Logs the DTLS handshake and SCTP-connect stages so a relay leg that stalls after ICE nomination shows
     * exactly which step did not complete (DTLS ClientHello sent, first relay record received, handshake
     * done, SCTP connected) rather than only the bring-up thread's generic relay-bind failure.
     */
    private static final System.Logger LOGGER = System.getLogger(RelayDataChannel.class.getName());

    /**
     * Guards the one-time log of the first outbound DTLS record so the handshake's first ClientHello is
     * recorded without logging every retransmission.
     */
    private final AtomicBoolean firstOutboundDtlsLogged = new AtomicBoolean();

    /**
     * Guards the one-time log of the first inbound DTLS record so a relay that answers the handshake is
     * recorded without logging every record.
     */
    private final AtomicBoolean firstInboundDtlsLogged = new AtomicBoolean();

    /**
     * Constructs the relay data channel over the host egress, relay address, DTLS role, and pinned
     * fingerprint.
     *
     * @param egress            the host UDP egress the DTLS records leave through
     * @param relayAddress      the relay transport address the DTLS records are sent to
     * @param relayActiveMode   whether the relay enabled DTLS active mode (the relay is then the DTLS client
     *                          and this side is the DTLS server)
     * @param pinnedFingerprint the SHA-256 fingerprint the relay certificate is pinned to, thirty-two raw
     *                          digest bytes
     * @throws NullPointerException     if {@code egress}, {@code relayAddress}, or {@code pinnedFingerprint}
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code pinnedFingerprint} is not exactly thirty-two bytes
     */
    public RelayDataChannel(LiveRelayTransport.Egress egress,
                            SocketAddress relayAddress,
                            boolean relayActiveMode,
                            byte[] pinnedFingerprint) {
        this.egress = Objects.requireNonNull(egress, "egress cannot be null");
        this.relayAddress = Objects.requireNonNull(relayAddress, "relayAddress cannot be null");
        this.relayActiveMode = relayActiveMode;
        Objects.requireNonNull(pinnedFingerprint, "pinnedFingerprint cannot be null");
        if (pinnedFingerprint.length != VoipDtlsCertificates.SHA256_FINGERPRINT_LENGTH) {
            throw new IllegalArgumentException("pinned fingerprint must be "
                    + VoipDtlsCertificates.SHA256_FINGERPRINT_LENGTH + " bytes, got " + pinnedFingerprint.length);
        }
        this.pinnedFingerprint = pinnedFingerprint.clone();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation runs the BouncyCastle DTLS client handshake against the relay over a
     *           {@link RelayDatagramTransport} wired to the host egress and the inbound queue, verifying the
     *           server certificate against {@link #pinnedFingerprint}; on success it opens the
     *           {@link SctpAssociation}, wraps it in an {@link SctpDtlsBridge.WebP2p} over the same DTLS
     *           transport, binds and connects SCTP to {@value #SCTP_PORT}, and marks the channel ready. The
     *           pre-negotiated channel needs no DCEP open, so the association is usable for application data
     *           the moment SCTP reaches {@code COMM_UP}. Any failure releases the partially-built state and
     *           is surfaced as a {@link WhatsAppCallException.DataChannel}.
     */
    @Override
    public void connect() {
        if (closed.get()) {
            throw new WhatsAppCallException.DataChannel("relay data channel is closed");
        }
        if (ready.get()) {
            return;
        }
        var datagramTransport = new RelayDatagramTransport();
        SctpAssociation localAssociation = null;
        try {
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay data-channel connect: starting DTLS handshake to {0}", relayAddress);
            var dtls = handshake(datagramTransport);
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay DTLS handshake complete; connecting SCTP on port {0}", SCTP_PORT);
            localAssociation = new SctpAssociation(
                    packet -> {
                        var localBridge = bridge;
                        if (localBridge != null) {
                            localBridge.wrapOutbound(packet);
                        }
                    },
                    this::onInboundMessage);
            this.association = localAssociation;
            this.bridge = new SctpDtlsBridge.WebP2p(dtls, localAssociation, null);
            localAssociation.bind(SCTP_PORT);
            localAssociation.connect(SCTP_PORT, SCTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ready.set(true);
            LOGGER.log(System.Logger.Level.INFO, "calls2 relay data channel ready (SCTP connected)");
        } catch (WhatsAppCallException exception) {
            releaseQuietly(localAssociation);
            throw exception;
        } catch (RuntimeException exception) {
            releaseQuietly(localAssociation);
            throw new WhatsAppCallException.DataChannel("relay data channel bring-up failed", exception);
        }
    }

    /**
     * Runs the DTLS handshake over the datagram transport and returns the established DTLS transport.
     *
     * <p>The role is chosen by {@link #relayActiveMode}: in the common passive mode the relay is the DTLS
     * server and this side is the DTLS client; when the relay block set {@code enable_edgeray_dtls_active_mode}
     * the relay is the DTLS client and this side is the DTLS server instead. Both roles present a freshly
     * generated self-signed ECDSA P-256 certificate and pin the relay's certificate to
     * {@link #pinnedFingerprint} through {@link VoipDtlsCertificates#createEngine(boolean, byte[])}; the
     * established transport is identical whichever role ran it, so everything after this method (the SCTP
     * association and the pre-negotiated channel) does not branch on the role.
     *
     * @param datagramTransport the datagram transport wired to the host egress and the inbound queue
     * @return the established DTLS application-data transport
     * @throws WhatsAppCallException.DataChannel if the handshake fails or the relay certificate does not pin
     * @implNote This implementation runs the active-mode server path as a defensive, uncaptured branch: every
     *           live relay answer observed carries {@code a=setup:passive}, so the relay-as-DTLS-client role has
     *           never run against a real relay, but the reverse engineering deterministically defines it
     *           (re/calls2-spec/web-transport-construction-RE.md), so it is implemented rather than rejected.
     *           The DTLS record and handshake layer is the JDK's own {@code DTLSv1.2} {@link javax.net.ssl.SSLEngine}
     *           driven by {@link VoipDtlsTransport}, not a third-party provider.
     */
    private VoipDtlsTransport handshake(RelayDatagramTransport datagramTransport) {
        try {
            var engine = VoipDtlsCertificates.createEngine(!relayActiveMode, pinnedFingerprint);
            var transport = new VoipDtlsTransport(engine, datagramTransport);
            transport.handshake(DTLS_HANDSHAKE_TIMEOUT_MILLIS);
            return transport;
        } catch (GeneralSecurityException | IOException exception) {
            throw new WhatsAppCallException.DataChannel("relay DTLS handshake failed", exception);
        }
    }

    /**
     * Delivers one inbound SCTP DATA application message to the registered consumer.
     *
     * <p>Only application data is forwarded; the DCEP control PPID is dropped because the channel is
     * pre-negotiated and never exchanges a DCEP open or ack. A message that arrives before a consumer is
     * registered, or after {@link #close()}, is dropped.
     *
     * @param message the decoded inbound SCTP message
     */
    private void onInboundMessage(SctpAssociation.InboundMessage message) {
        if (closed.get() || message.ppid() == DcepMessage.PPID_DCEP) {
            return;
        }
        var consumer = messageConsumer.get();
        if (consumer != null) {
            consumer.accept(message.payload());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param message {@inheritDoc}
     * @return {@code true} when SCTP accepted the message for transmission, {@code false} when the channel is
     * not open, is closed, or SCTP rejected it
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public boolean send(byte[] message) {
        Objects.requireNonNull(message, "message cannot be null");
        var localAssociation = association;
        if (closed.get() || !ready.get() || localAssociation == null) {
            return false;
        }
        try {
            localAssociation.sendWithPolicy(CHANNEL_STREAM_ID, PPID_BINARY, message, false,
                    com.github.auties00.cobalt.calls2.net.transport.sctp.bindings.CobaltSctp.COBALT_SCTP_PR_RTX(), 0);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param consumer {@inheritDoc}
     * @throws NullPointerException if {@code consumer} is {@code null}
     */
    @Override
    public void onMessage(Consumer<byte[]> consumer) {
        this.messageConsumer.set(Objects.requireNonNull(consumer, "consumer cannot be null"));
    }

    /**
     * Feeds one inbound DTLS record from the host socket into the DTLS datagram transport.
     *
     * <p>The record is offered to the inbound queue the {@link RelayDatagramTransport} reads from, so the
     * bridge's DTLS receive pump can decrypt it. Before {@link #connect()} the handshake itself reads from
     * the same queue, so a record that arrives during the handshake is consumed by it. After {@link #close()}
     * the record is dropped.
     *
     * @param record the inbound DTLS record bytes
     * @throws NullPointerException if {@code record} is {@code null}
     */
    @Override
    public void feedDtlsRecord(byte[] record) {
        Objects.requireNonNull(record, "record cannot be null");
        if (closed.get()) {
            return;
        }
        if (firstInboundDtlsLogged.compareAndSet(false, true)) {
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay first inbound DTLS record from relay ({0} bytes)", record.length);
        }
        // A non-blocking offer keeps the socket reader thread from stalling; a record dropped because the
        // bounded queue is momentarily full is recovered by the DTLS retransmission.
        inbound.offer(record);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} once the DTLS handshake, SCTP connect, and channel open have completed
     */
    @Override
    public boolean isReady() {
        return ready.get() && !closed.get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotent: a second call returns at once. It marks the channel closed, wakes any blocked datagram
     * read with a poison record, and tears down the SCTP association and the DTLS bridge.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ready.set(false);
        inbound.offer(POISON);
        var localBridge = bridge;
        if (localBridge != null) {
            try {
                localBridge.close();
            } catch (RuntimeException exception) {
                // The DTLS bridge teardown swallows its own IOException; any residual runtime fault is
                // isolated so the rest of the teardown still runs.
            }
        }
        releaseQuietly(association);
    }

    /**
     * Closes an SCTP association, swallowing any teardown fault.
     *
     * @param target the association to close, or {@code null}
     */
    private static void releaseQuietly(SctpAssociation target) {
        if (target == null) {
            return;
        }
        try {
            target.close();
        } catch (RuntimeException exception) {
            // The association teardown is best-effort on a failed bring-up; a residual fault must not mask
            // the original bring-up exception.
        }
    }

    /**
     * Bridges the JDK DTLS record layer to the host UDP egress and the inbound DTLS-record queue.
     *
     * <p>{@link VoipDtlsTransport} drives DTLS over this {@link VoipDtlsTransport.Datagrams} seam: it writes one
     * DTLS record per {@link #send(byte[])} and reads one per {@link #receive(int)}. {@link #send(byte[])}
     * forwards the record to {@link RelayDataChannel#egress} addressed to the relay; {@link #receive(int)} polls
     * {@link RelayDataChannel#inbound}, which {@link RelayDataChannel#feedDtlsRecord(byte[])} fills from the host
     * socket reader. The poison record offered on close returns {@code null} so the DTLS layer observes the
     * closed transport.
     */
    private final class RelayDatagramTransport implements VoipDtlsTransport.Datagrams {
        /**
         * {@inheritDoc}
         *
         * <p>Writes one DTLS record to the host egress addressed to the relay. A failed egress send is a
         * best-effort loss the DTLS retransmission recovers, so it does not raise.
         *
         * @param record {@inheritDoc}
         */
        @Override
        public void send(byte[] record) {
            if (closed.get()) {
                return;
            }
            var sent = egress.send(record, relayAddress);
            if (firstOutboundDtlsLogged.compareAndSet(false, true)) {
                LOGGER.log(System.Logger.Level.INFO,
                        "calls2 relay first outbound DTLS record (ClientHello) to {0}: {1} of {2} bytes accepted",
                        relayAddress, sent, record.length);
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p>Polls the inbound DTLS-record queue with the given timeout; a timeout, a closed transport (the
         * poison record), or an empty record returns {@code null} so the DTLS layer retries or observes the
         * close.
         *
         * @param waitMillis {@inheritDoc}
         * @return {@inheritDoc}
         */
        @Override
        public byte[] receive(int waitMillis) {
            if (closed.get()) {
                return null;
            }
            byte[] record;
            try {
                record = inbound.poll(Math.max(1, waitMillis), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
            if (record == null || record == POISON || record.length == 0) {
                return null;
            }
            return record;
        }
    }
}
