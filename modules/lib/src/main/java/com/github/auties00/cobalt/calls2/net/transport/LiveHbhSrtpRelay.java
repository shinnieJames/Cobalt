package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.calls2.core.participant.CallE2eKeyDerivation;
import com.github.auties00.cobalt.calls2.net.transport.srtp.bindings.CobaltSrtp;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.model.call.datachannel.SrtpAfbStreams;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Implements {@link HbhSrtpRelay} over the portable libsrtp shim binding, creating separate outbound and
 * inbound SRTP contexts for RTP media and for RTCP control, keyed by their respective derived hop-by-hop
 * masters and protecting and unprotecting packets in place through
 * {@link CobaltSrtp#cobalt_srtp_protect}/{@link CobaltSrtp#cobalt_srtp_unprotect}.
 *
 * <p>The relay leg is symmetric around two masters: this client keys an outbound context that encrypts
 * everything it sends to the relay and an inbound context that decrypts everything it receives, each
 * from a {@value SrtpCryptoSuite#SUITE_MASTER_LENGTH}-byte master (16-byte key plus 14-byte salt)
 * derived from the relay {@code <hbh_key>}. RTP media is keyed from the
 * {@link CallE2eKeyDerivation.HopByHopGroup#MEDIA} master and RTCP control from the
 * {@link CallE2eKeyDerivation.HopByHopGroup#SRTCP} master, because the relay keys the two flows from
 * different {@code wa_sfu_kdf} groups. A libsrtp session uses one master for both its RTP and RTCP
 * transforms, so the two flows cannot share a session: four sessions are created in total, an outbound
 * and an inbound session over the media master for RTP and an outbound and an inbound session over the
 * SRTCP master for RTCP. Within each master two libsrtp sessions are needed because a libsrtp session
 * protects or unprotects, not both: the outbound session is created with the wildcard outbound SSRC
 * direction and the inbound session with the wildcard inbound SSRC direction, so a single key set
 * covers every stream multiplexed on the leg. All four use the same {@link SrtpCryptoSuite}, selected
 * once at construction and passed to the shim as a portable selector.
 *
 * <p>Packet crypto crosses the foreign boundary through a per-instance off-heap scratch buffer sized
 * for a jumbo datagram plus the SRTP trailer: a protect or unprotect copies the heap packet into the
 * scratch buffer, runs the in-place libsrtp call with a foreign length cell, then copies the resized
 * packet back. This instance owns a shared {@link Arena} holding the four sessions' policy memory, the
 * key buffers, the scratch buffer, and the length cell; {@link #close()} deallocates the sessions and
 * closes the arena. An instance is used from the single transport thread that owns the relay leg.
 *
 * @implNote This implementation reproduces {@code create_srtp_context_for_hbh_srtp} and the protect and
 *           unprotect paths of {@code transport/wa_hbh_srtp_relay.cc}: the context keyed by
 *           {@code derive_hbh_srtp_key} (fn4808) with the suite from {@code fill_hbh_srtp_crypto}
 *           (fn4809). {@code create_srtp_context_for_hbh_srtp} keys RTP media from the {@code 'hbh srtp'}
 *           ({@link CallE2eKeyDerivation.HopByHopGroup#MEDIA}) group and keys RTCP control, in both
 *           directions, from the non-directional {@code 'hbh srtcp'}
 *           ({@link CallE2eKeyDerivation.HopByHopGroup#SRTCP}, KDF index pair {@code (4, 5)}) master: the
 *           relay-leg setup (fn11466) builds both the outbound and the inbound RTCP policy from that one
 *           SRTCP master, and the {@code is_hbh_srtp} branch of {@code create_srtp_context_for_hbh_srtp}
 *           (fn4811) derives only the {@code (4, 5)} master and keys both its transmit and receive
 *           policies from it. The directional {@code 'uplink hbh srtcp'} and {@code 'downlink hbh srtcp'}
 *           groups (KDF {@code (6, 7)} and {@code (8, 9)}) belong to the SFU-forwarding role
 *           ({@code is_hbh_srtp == 0}), a different transport role than this 1:1 relay leg, so they do not
 *           key these contexts. The host libsrtp split (one outbound and one inbound
 *           session over wildcard SSRCs per master) matches libsrtp's
 *           {@code ssrc_any_outbound}/{@code ssrc_any_inbound} usage; in the WhatsApp
 *           Web build the equivalent SRTP transform is a browser WebRTC native callback, and Cobalt
 *           binds the libsrtp inside the combined {@code cobalt-native} library instead. The binding is
 *           the portable extern-C shim {@link CobaltSrtp}: every libsrtp struct
 *           ({@code srtp_policy_t}, {@code srtp_crypto_policy_t}, {@code srtp_ssrc_t}) is built C-side
 *           from portable scalars and a libsrtp session is returned as an opaque pointer, so this class
 *           never touches an ABI-sensitive layout and the generated binding is host-ABI independent.
 *           {@link CobaltSrtp#cobalt_srtp_init()} is invoked exactly once for the process; the
 *           per-instance scratch buffer is sized to {@value #MAX_PACKET_LENGTH} bytes plus
 *           {@link CobaltSrtp#COBALT_SRTP_MAX_TRAILER_LEN()} so any RTP or RTCP packet plus its appended
 *           authentication tag and SRTCP index fits.
 */
public final class LiveHbhSrtpRelay implements HbhSrtpRelay {
    /**
     * Holds the maximum cleartext packet length, in bytes, the scratch buffer accommodates before the
     * SRTP trailer.
     *
     * <p>This bounds a single RTP or RTCP packet handed to a protect or unprotect call; it is a jumbo
     * datagram ceiling well above the relay path MTU, so a real media packet always fits.
     */
    private static final int MAX_PACKET_LENGTH = 2048;

    /**
     * Guards one-time process initialization of libsrtp.
     */
    private static final Object INIT_LOCK = new Object();

    /**
     * Tracks whether {@link #ensureInitialized()} has loaded the native library and run
     * {@link CobaltSrtp#cobalt_srtp_init()}.
     */
    private static boolean initialized;

    /**
     * Holds the SRTP crypto suite all four contexts use.
     */
    private final SrtpCryptoSuite suite;

    /**
     * Holds the shared arena backing this instance's foreign memory; closed by {@link #close()}.
     */
    private final Arena arena;

    /**
     * Holds the outbound libsrtp session pointer that encrypts RTP media packets toward the relay,
     * keyed from the {@link CallE2eKeyDerivation.HopByHopGroup#MEDIA} master.
     */
    private final MemorySegment outboundSession;

    /**
     * Holds the inbound libsrtp session pointer that decrypts RTP media packets from the relay,
     * keyed from the {@link CallE2eKeyDerivation.HopByHopGroup#MEDIA} master.
     */
    private final MemorySegment inboundSession;

    /**
     * Holds the outbound libsrtp session pointer that encrypts RTCP control packets toward the relay,
     * keyed from the {@link CallE2eKeyDerivation.HopByHopGroup#SRTCP} master.
     */
    private final MemorySegment outboundRtcpSession;

    /**
     * Holds the inbound libsrtp session pointer that decrypts RTCP control packets from the relay,
     * keyed from the {@link CallE2eKeyDerivation.HopByHopGroup#SRTCP} master.
     */
    private final MemorySegment inboundRtcpSession;

    /**
     * Holds the off-heap scratch buffer the in-place protect and unprotect calls operate on.
     */
    private final MemorySegment scratch;

    /**
     * Holds the off-heap length cell passed to {@link CobaltSrtp#cobalt_srtp_protect}/{@link
     * CobaltSrtp#cobalt_srtp_unprotect}.
     */
    private final MemorySegment lengthCell;

    /**
     * Holds the RTCP-feedback subscription table for this relay leg.
     */
    private final RtcpRxSubscriptionTable rtcpFeedbackSubscriptions;

    /**
     * Holds the per-stream SRTP authenticated-feedback index tracker for this relay leg.
     */
    private final SrtpAfbStreamTracker afbStreamTracker;

    /**
     * Tracks whether this relay leg has been closed.
     */
    private boolean closed;

    /**
     * Creates a hop-by-hop relay context for the elected relay from its decoded {@code <hbh_key>} and a
     * crypto suite.
     *
     * <p>This is the convenience entry point for relay election: it derives the non-directional
     * hop-by-hop media SRTP master and the non-directional hop-by-hop SRTCP master from the relay
     * {@code <hbh_key>} through
     * {@link CallE2eKeyDerivation#deriveHbhSrtpMaster(byte[], CallE2eKeyDerivation.HopByHopGroup)} and
     * then builds the libsrtp contexts. The {@code <hbh_key>} is the 30-byte value the relay block
     * carried, base64-decoded by the signaling layer, shared identically across every participant of a
     * group call. The relay keys RTP media from the {@link CallE2eKeyDerivation.HopByHopGroup#MEDIA}
     * group and both RTCP directions from the {@link CallE2eKeyDerivation.HopByHopGroup#SRTCP} group, so
     * the two masters are derived here and the media and control flows keyed independently.
     *
     * @param hopByHopKey the {@value CallE2eKeyDerivation#HBH_KEY_LENGTH}-byte decoded relay
     *                    {@code <hbh_key>}
     * @param suite       the SRTP crypto suite to apply, from {@code fill_hbh_srtp_crypto}
     * @return a relay context keyed for the leg
     * @throws NullPointerException       if {@code hopByHopKey} or {@code suite} is {@code null}
     * @throws IllegalArgumentException   if {@code hopByHopKey} is not exactly the decoded hop-by-hop
     *                                    key length
     * @throws WhatsAppCallException.Srtp if the key derivation or libsrtp session creation fails
     */
    public static LiveHbhSrtpRelay fromHopByHopKey(byte[] hopByHopKey, SrtpCryptoSuite suite) {
        var mediaMaster = CallE2eKeyDerivation.deriveHbhSrtpMaster(hopByHopKey, CallE2eKeyDerivation.HopByHopGroup.MEDIA);
        var srtcpMaster = CallE2eKeyDerivation.deriveHbhSrtpMaster(hopByHopKey, CallE2eKeyDerivation.HopByHopGroup.SRTCP);
        return new LiveHbhSrtpRelay(mediaMaster, srtcpMaster, suite);
    }

    /**
     * Creates a hop-by-hop relay context from the derived hop-by-hop media and SRTCP SRTP masters and a
     * crypto suite.
     *
     * <p>Each master is split into its 16-byte key and 14-byte salt and copied into its own foreign key
     * buffer. Four libsrtp sessions are created: an outbound and an inbound session over
     * {@code mediaMaster} for RTP media, and an outbound and an inbound RTCP session both over
     * {@code srtcpMaster} for control toward and from the relay. RTP and RTCP are keyed from different
     * masters because the relay keys the two flows from different {@code wa_sfu_kdf} groups, so media
     * cannot share a session with control; the two RTCP directions share the one non-directional SRTCP
     * master but stay separate sessions for their inbound and outbound SSRC-wildcard roles.
     *
     * @param mediaMaster the {@value SrtpCryptoSuite#SUITE_MASTER_LENGTH}-byte hop-by-hop media SRTP
     *                    master keying RTP, from {@link CallE2eKeyDerivation.HopByHopGroup#MEDIA}
     * @param srtcpMaster the {@value SrtpCryptoSuite#SUITE_MASTER_LENGTH}-byte SRTCP master keying both
     *                    RTCP directions, from {@link CallE2eKeyDerivation.HopByHopGroup#SRTCP}
     * @param suite       the SRTP crypto suite to apply, from {@code fill_hbh_srtp_crypto}
     * @throws NullPointerException       if {@code mediaMaster}, {@code srtcpMaster} or {@code suite} is
     *                                    {@code null}
     * @throws IllegalArgumentException   if any master is not exactly
     *                                    {@value SrtpCryptoSuite#SUITE_MASTER_LENGTH} bytes long
     * @throws WhatsAppCallException.Srtp if libsrtp cannot create any session
     */
    public LiveHbhSrtpRelay(byte[] mediaMaster, byte[] srtcpMaster, SrtpCryptoSuite suite) {
        Objects.requireNonNull(mediaMaster, "mediaMaster cannot be null");
        Objects.requireNonNull(srtcpMaster, "srtcpMaster cannot be null");
        this.suite = Objects.requireNonNull(suite, "suite cannot be null");
        if (mediaMaster.length != SrtpCryptoSuite.SUITE_MASTER_LENGTH) {
            throw new IllegalArgumentException(
                    "mediaMaster must be " + SrtpCryptoSuite.SUITE_MASTER_LENGTH + " bytes, got " + mediaMaster.length);
        }
        if (srtcpMaster.length != SrtpCryptoSuite.SUITE_MASTER_LENGTH) {
            throw new IllegalArgumentException(
                    "srtcpMaster must be " + SrtpCryptoSuite.SUITE_MASTER_LENGTH + " bytes, got " + srtcpMaster.length);
        }
        ensureInitialized();
        this.rtcpFeedbackSubscriptions = new RtcpRxSubscriptionTable();
        this.afbStreamTracker = new SrtpAfbStreamTracker();
        this.arena = Arena.ofShared();
        MemorySegment outbound = null;
        MemorySegment inbound = null;
        MemorySegment outboundRtcp = null;
        try {
            var mediaKeyBuffer = arena.allocate(SrtpCryptoSuite.SUITE_MASTER_LENGTH);
            MemorySegment.copy(mediaMaster, 0, mediaKeyBuffer, ValueLayout.JAVA_BYTE, 0, SrtpCryptoSuite.SUITE_MASTER_LENGTH);
            var srtcpKeyBuffer = arena.allocate(SrtpCryptoSuite.SUITE_MASTER_LENGTH);
            MemorySegment.copy(srtcpMaster, 0, srtcpKeyBuffer, ValueLayout.JAVA_BYTE, 0, SrtpCryptoSuite.SUITE_MASTER_LENGTH);
            outbound = createSession(mediaKeyBuffer, CobaltSrtp.COBALT_SRTP_DIR_OUTBOUND());
            this.outboundSession = outbound;
            inbound = createSession(mediaKeyBuffer, CobaltSrtp.COBALT_SRTP_DIR_INBOUND());
            this.inboundSession = inbound;
            outboundRtcp = createSession(srtcpKeyBuffer, CobaltSrtp.COBALT_SRTP_DIR_OUTBOUND());
            this.outboundRtcpSession = outboundRtcp;
            this.inboundRtcpSession = createSession(srtcpKeyBuffer, CobaltSrtp.COBALT_SRTP_DIR_INBOUND());
            this.scratch = arena.allocate(MAX_PACKET_LENGTH + CobaltSrtp.COBALT_SRTP_MAX_TRAILER_LEN());
            this.lengthCell = arena.allocate(ValueLayout.JAVA_INT);
        } catch (RuntimeException e) {
            if (outbound != null) {
                CobaltSrtp.cobalt_srtp_dealloc(outbound);
            }
            if (inbound != null) {
                CobaltSrtp.cobalt_srtp_dealloc(inbound);
            }
            if (outboundRtcp != null) {
                CobaltSrtp.cobalt_srtp_dealloc(outboundRtcp);
            }
            arena.close();
            throw e;
        }
    }

    @Override
    public int protectRtp(byte[] packet, int length) {
        return transform(outboundSession, packet, length, CobaltSrtp::cobalt_srtp_protect, false);
    }

    @Override
    public int unprotectRtp(byte[] packet, int length) {
        return transform(inboundSession, packet, length, CobaltSrtp::cobalt_srtp_unprotect, true);
    }

    @Override
    public int protectRtcp(byte[] packet, int length) {
        return transform(outboundRtcpSession, packet, length, CobaltSrtp::cobalt_srtp_protect_rtcp, false);
    }

    @Override
    public int unprotectRtcp(byte[] packet, int length) {
        return transform(inboundRtcpSession, packet, length, CobaltSrtp::cobalt_srtp_unprotect_rtcp, true);
    }

    @Override
    public RtcpRxSubscriptionTable rtcpFeedbackSubscriptions() {
        requireOpen();
        return rtcpFeedbackSubscriptions;
    }

    @Override
    public SrtpAfbStreams srtpAfbStreams() {
        requireOpen();
        return afbStreamTracker.toReport();
    }

    /**
     * Returns the per-stream SRTP authenticated-feedback index tracker for this relay leg.
     *
     * <p>This exposes the mutable tracker so the transport advances the RTP and SRTCP watermarks as it
     * protects and unprotects packets; {@link #srtpAfbStreams()} renders its snapshot.
     *
     * @return the relay leg's authenticated-feedback tracker; never {@code null}
     * @throws IllegalStateException if the context has been closed
     */
    public SrtpAfbStreamTracker afbStreamTracker() {
        requireOpen();
        return afbStreamTracker;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            CobaltSrtp.cobalt_srtp_dealloc(outboundSession);
            CobaltSrtp.cobalt_srtp_dealloc(inboundSession);
            CobaltSrtp.cobalt_srtp_dealloc(outboundRtcpSession);
            CobaltSrtp.cobalt_srtp_dealloc(inboundRtcpSession);
        } finally {
            arena.close();
        }
    }

    /**
     * Creates one libsrtp session over a key buffer for a wildcard SSRC direction through the shim.
     *
     * <p>The shim builds the {@code srtp_policy_t}, its RTP and RTCP {@code srtp_crypto_policy_t} (from
     * the suite selector), and the wildcard {@code srtp_ssrc_t} C-side, then calls {@code srtp_create}
     * and returns the session as an opaque pointer through a foreign out-cell. This method supplies only
     * the portable scalars: the suite selector, the key buffer and its length, and the SSRC direction.
     *
     * @param keyBuffer     the foreign buffer holding the 16-byte key followed by the 14-byte salt
     * @param ssrcDirection the wildcard SSRC direction, {@link CobaltSrtp#COBALT_SRTP_DIR_OUTBOUND()} or
     *                      {@link CobaltSrtp#COBALT_SRTP_DIR_INBOUND()}
     * @return the created libsrtp session pointer
     * @throws WhatsAppCallException.Srtp if {@code cobalt_srtp_create} does not return success
     */
    private MemorySegment createSession(MemorySegment keyBuffer, int ssrcDirection) {
        var sessionCell = arena.allocate(CobaltSrtp.C_POINTER);
        var status = CobaltSrtp.cobalt_srtp_create(
                suite.selector(), keyBuffer, keyBuffer.byteSize(), ssrcDirection, sessionCell);
        if (status != CobaltSrtp.COBALT_SRTP_OK()) {
            throw new WhatsAppCallException.Srtp(
                    "cobalt_srtp_create failed for ssrc direction " + ssrcDirection + " with status " + status);
        }
        return sessionCell.get(CobaltSrtp.C_POINTER, 0);
    }

    /**
     * Runs one in-place SRTP transform over a heap packet through a foreign scratch buffer.
     *
     * <p>The packet is bounds-checked and copied into the scratch buffer, the foreign length cell is set
     * to {@code length}, the libsrtp call runs in place, and the resized packet is copied back into the
     * caller's array. The resulting length is returned.
     *
     * @param session the libsrtp session to run the transform on
     * @param packet  the heap buffer holding the packet, with trailing room on protect
     * @param length  the length, in bytes, of the input packet
     * @param call    the libsrtp transform to invoke
     * @param shrinks whether the transform shrinks the packet (unprotect) rather than growing it
     * @return the length, in bytes, of the transformed packet
     * @throws NullPointerException       if {@code packet} is {@code null}
     * @throws IllegalArgumentException   if {@code length} is negative or exceeds {@code packet.length},
     *                                    or, when growing, would overflow the scratch buffer
     * @throws IllegalStateException      if the context has been closed
     * @throws WhatsAppCallException.Srtp if the libsrtp call does not return success
     */
    private int transform(MemorySegment session, byte[] packet, int length, SrtpCall call, boolean shrinks) {
        Objects.requireNonNull(packet, "packet cannot be null");
        requireOpen();
        if (length < 0 || length > packet.length) {
            throw new IllegalArgumentException("length out of bounds: " + length + " for buffer " + packet.length);
        }
        if (length > MAX_PACKET_LENGTH) {
            throw new IllegalArgumentException("packet length " + length + " exceeds maximum " + MAX_PACKET_LENGTH);
        }
        MemorySegment.copy(packet, 0, scratch, ValueLayout.JAVA_BYTE, 0, length);
        lengthCell.set(ValueLayout.JAVA_INT, 0, length);
        var status = call.invoke(session, scratch, lengthCell);
        if (status != CobaltSrtp.COBALT_SRTP_OK()) {
            throw new WhatsAppCallException.Srtp("SRTP transform failed with status " + status);
        }
        var resultLength = lengthCell.get(ValueLayout.JAVA_INT, 0);
        if (resultLength < 0 || resultLength > scratch.byteSize()) {
            throw new WhatsAppCallException.Srtp("SRTP transform returned invalid length " + resultLength);
        }
        if (!shrinks && resultLength > packet.length) {
            throw new IllegalArgumentException(
                    "protected packet length " + resultLength + " exceeds buffer " + packet.length);
        }
        MemorySegment.copy(scratch, ValueLayout.JAVA_BYTE, 0, packet, 0, Math.min(resultLength, packet.length));
        return resultLength;
    }

    /**
     * Verifies this relay leg has not been closed.
     *
     * @throws IllegalStateException if the context has been closed
     */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("HbhSrtpRelay has been closed");
        }
    }

    /**
     * Loads the native library and runs {@link CobaltSrtp#cobalt_srtp_init()} exactly once for the
     * process.
     *
     * <p>The shim symbols resolve through the loader lookup the combined {@code cobalt-native} library
     * populates, so the library is loaded before the first {@link CobaltSrtp#cobalt_srtp_create}; the
     * shim's {@code cobalt_srtp_init} forwards to libsrtp's {@code srtp_init}, which initializes
     * libsrtp's global cipher and authentication tables that a session creation requires.
     *
     * @throws WhatsAppCallException.Srtp if {@link CobaltSrtp#cobalt_srtp_init()} does not return success
     * @implNote This implementation mirrors the one-time native bring-up of the other calls2 native
     *           bindings ({@code NativeLibLoader.load("cobalt-native", Arena.global())}) and adds the
     *           libsrtp-specific global setup the host runtime performs once, through the shim's
     *           {@code cobalt_srtp_init}.
     */
    private static void ensureInitialized() {
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }
            NativeLibLoader.load("cobalt-native", Arena.global());
            var status = CobaltSrtp.cobalt_srtp_init();
            if (status != CobaltSrtp.COBALT_SRTP_OK()) {
                throw new WhatsAppCallException.Srtp("cobalt_srtp_init failed with status " + status);
            }
            initialized = true;
        }
    }

    /**
     * Adapts the four shim in-place transform entry points to a common call shape.
     *
     * <p>Each of {@link CobaltSrtp#cobalt_srtp_protect}, {@link CobaltSrtp#cobalt_srtp_unprotect},
     * {@link CobaltSrtp#cobalt_srtp_protect_rtcp}, and {@link CobaltSrtp#cobalt_srtp_unprotect_rtcp}
     * takes a session, an in-place buffer, and a length cell and returns a status; this functional
     * interface lets {@link #transform(MemorySegment, byte[], int, SrtpCall, boolean)} dispatch to any
     * of them.
     */
    @FunctionalInterface
    private interface SrtpCall {
        /**
         * Invokes one shim in-place transform.
         *
         * @param session    the libsrtp session pointer
         * @param buffer     the in-place packet buffer
         * @param lengthCell the foreign length cell, read and written by the transform
         * @return the shim status code
         */
        int invoke(MemorySegment session, MemorySegment buffer, MemorySegment lengthCell);
    }
}
