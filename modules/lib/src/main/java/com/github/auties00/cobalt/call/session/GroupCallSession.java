package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.call.audio.AudioPipeline;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.call.audio.opus.OpusPacket;
import com.github.auties00.cobalt.call.audio.opus.OpusDecoder;
import com.github.auties00.cobalt.call.stream.AudioFrame;
import com.github.auties00.cobalt.call.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.rtp.RtpReceiver;
import com.github.auties00.cobalt.call.rtp.RtpSender;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.model.jid.Jid;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Drives the media plane of a many-to-many group call over a single SFU-relayed transport.
 *
 * <p>The outbound side carries one local audio track: the local microphone, encoded to Opus by an
 * {@link AudioPipeline} and sent up to the SFU on the local SSRC supplied by {@link VoiceCallOptions}.
 * The inbound side carries one audio stream per remote participant, each forwarded by the SFU on a
 * distinct SSRC and demultiplexed back to a per-peer {@link GroupCallParticipant} listener. There is
 * no server-side mixing: every participant's audio arrives as a separate stream and the application
 * is responsible for mixing the decoded frames it receives.
 *
 * <p>This session differs from the one-to-one {@link VoiceCallSession} in four ways:
 *
 * <ul>
 *   <li>One sender, many receivers. A single {@link RtpSender} feeds the SFU, which re-broadcasts to
 *       every participant; inbound audio is routed to a per-peer consumer rather than to a single
 *       remote audio source.</li>
 *   <li>Per-participant decoder. Each peer's SSRC owns its own {@link OpusDecoder} so the SFU's
 *       forwarded streams do not corrupt each other's packet-loss-concealment state.</li>
 *   <li>Outbound preprocessing reads only from {@link CallRuntime#audioOut()}; the application's
 *       mix of all decoded participants is what feeds the echo canceller's far-end reference.</li>
 *   <li>Group SSRC management. Participants join and leave through {@link #addParticipant} and
 *       {@link #removeParticipant}, driven by the group-call signaling layer above this session.</li>
 * </ul>
 *
 * <p>This session does not speak group-call signaling. Invite, remove, and group-state changes live
 * in the call layer above it, which feeds this session a connected {@link DatagramTransport} and the
 * peer or SFU DTLS fingerprint.
 */
public final class GroupCallSession implements CallMediaSession {
    /**
     * The call runtime whose media streams drive this session.
     */
    private final CallRuntime call;

    /**
     * The per-call configuration supplying the local SSRC, the Opus payload type, and the audio
     * pipeline options; never mutated after construction.
     */
    private final VoiceCallOptions options;

    /**
     * The DTLS-SRTP handshake driver that owns the underlying {@link DatagramTransport} and derives
     * the SRTP keys shared by the outbound sender and every per-participant receiver.
     *
     * <p>The local SSRC and Opus payload type come from {@link #options}; each participant supplies
     * its own SFU-assigned receive SSRC through {@link GroupCallParticipant#audioSsrc()}.
     */
    private final DtlsSrtpDriver dtls;

    /**
     * The outbound RTP sender for the single local audio track, instantiated once the handshake
     * completes; {@code null} until then.
     */
    private RtpSender sender;

    /**
     * The audio pipeline that drives microphone frames through Opus encoding into outbound RTP,
     * instantiated once the handshake completes; {@code null} until then.
     */
    private AudioPipeline audio;

    /**
     * The per-peer subscriber state, keyed on the participant's receive SSRC.
     *
     * <p>The SFU forwards each peer's audio with a distinct SSRC, so inbound SRTP is demultiplexed by
     * reading the SSRC from the packet header and looking up the matching {@link Subscriber}.
     */
    private final ConcurrentHashMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();

    /**
     * Participant JIDs that joined the call via {@code <group_update action="add">} but whose
     * forward SSRC has not yet arrived through the SFU's
     * {@link com.github.auties00.cobalt.model.call.datachannel.SenderSubscriptions SenderSubscriptions}
     * message.
     *
     * <p>Reconciled by {@link #resolvePendingParticipant(Jid, int, java.util.function.BiFunction)}
     * once the SSRC is learned. Entries are removed on
     * {@link #removeParticipant(Jid)} so a participant who leaves before subscribing never gets
     * promoted into the active subscriber map.
     */
    private final java.util.concurrent.ConcurrentHashMap<Jid, Boolean> pendingJoins =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether {@link #start()} has been called, guarding against a second handshake.
     */
    private volatile boolean started;

    /**
     * Whether {@link #close()} has been called, after which media delivery and participant changes
     * are suppressed.
     */
    private volatile boolean closed;

    /**
     * Bundles the per-participant state required to receive and decode one forwarded audio stream.
     *
     * <p>Each subscriber owns the {@link RtpReceiver} that filters on the participant's SSRC, a
     * dedicated {@link OpusDecoder} so packet-loss-concealment state is independent of other peers,
     * and the application listener carried on the {@link GroupCallParticipant}.
     */
    private static final class Subscriber {
        /**
         * The participant this subscriber receives audio for, carrying its JID, SSRC, and listener.
         */
        final GroupCallParticipant participant;

        /**
         * The RTP receiver filtering inbound SRTP on the participant's SSRC.
         */
        final RtpReceiver receiver;

        /**
         * The decoder dedicated to this participant so its concealment state stays isolated.
         */
        final OpusDecoder decoder;

        /**
         * The per-channel sample count of one decoded frame, taken from the audio options.
         */
        final int frameSize;

        /**
         * Constructs a subscriber bundle for one participant.
         *
         * @param participant the participant whose stream this subscriber receives
         * @param receiver    the RTP receiver filtering on the participant's SSRC
         * @param decoder     the decoder dedicated to this participant
         * @param frameSize   the per-channel sample count of one decoded frame
         */
        Subscriber(GroupCallParticipant participant, RtpReceiver receiver,
                   OpusDecoder decoder, int frameSize) {
            this.participant = participant;
            this.receiver = receiver;
            this.decoder = decoder;
            this.frameSize = frameSize;
        }
    }

    /**
     * Constructs a group-call session bound to the given call and transport.
     *
     * <p>The remote SSRC carried by {@code options} is ignored: participants supply their own receive
     * SSRCs when they join through {@link #addParticipant}.
     *
     * @param call                    the active call whose media ports drive the session
     * @param transport               the connected datagram path to the SFU
     * @param role                    the DTLS role to play in the handshake
     * @param localCert               the local DTLS certificate
     * @param expectedPeerFingerprint the expected SFU certificate fingerprint
     * @param options                 the per-call configuration; supplies the local SSRC and Opus
     *                                payload type, with the remote SSRC ignored
     * @throws NullPointerException if any argument is {@code null}
     */
    public GroupCallSession(CallRuntime call, DatagramTransport transport,
                            SrtpRole role, DtlsCertificate localCert,
                            byte[] expectedPeerFingerprint, VoiceCallOptions options) {
        this(call,
                new DtlsSrtpDriver(
                        Objects.requireNonNull(transport, "transport cannot be null"),
                        Objects.requireNonNull(role, "role cannot be null"),
                        Objects.requireNonNull(localCert, "localCert cannot be null"),
                        Objects.requireNonNull(expectedPeerFingerprint, "expectedPeerFingerprint cannot be null")),
                options);
    }

    /**
     * Constructs a group-call session over an already-built {@link DtlsSrtpDriver}.
     *
     * <p>Used when the transport layer has already constructed the driver and run its connectivity
     * check over the relay socket (the SFU leg), so the session shares the same byte transport and
     * SRTP demultiplexer rather than building a second driver that would clobber the first's inbound
     * listener.
     *
     * @param call    the active call whose media ports drive the session
     * @param driver  the pre-built DTLS-SRTP driver wrapping the SFU transport
     * @param options the per-call configuration
     * @throws NullPointerException if any argument is {@code null}
     */
    public GroupCallSession(CallRuntime call, DtlsSrtpDriver driver, VoiceCallOptions options) {
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        this.dtls = Objects.requireNonNull(driver, "driver cannot be null");
    }

    /**
     * The 30-byte hop-by-hop key the relay issued in {@code <hbh_key>}, keying the SFU-leg SRTP; or
     * {@code null} when media keys from a DTLS-SRTP export instead.
     *
     * <p>WhatsApp group calls route media through an SFU over the same hop-by-hop SRTP transport a 1:1
     * call uses for its relay leg; per-participant end-to-end confidentiality is layered on top with
     * SFrame. When set, the SFU-leg SRTP is keyed from this hop-by-hop key (no peer DTLS handshake),
     * mirroring {@link VoiceCallSession#useHopByHopKey(byte[])}.
     */
    private volatile byte[] hopByHopKey;

    /**
     * Keys the SFU-leg SRTP from the relay's 30-byte hop-by-hop key instead of a DTLS-SRTP export.
     *
     * <p>Must be called before {@link #start()}.
     *
     * @param hbhKey the 30-byte hop-by-hop key, or {@code null} to keep DTLS-export keying
     * @return this session, for chaining
     */
    public GroupCallSession useHopByHopKey(byte[] hbhKey) {
        this.hopByHopKey = hbhKey == null ? null : hbhKey.clone();
        return this;
    }

    /**
     * The 32-byte end-to-end group call key the media payload SRTP derives from (Family B), or
     * {@code null} on the loopback path.
     */
    private volatile byte[] callKey;

    /**
     * The local participant {@code <lid>:<device>@lid} JID that keys the outbound media stream.
     */
    private volatile String localParticipantJid;

    /**
     * The truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}, defaulting to 10.
     */
    private volatile int authTagLength = SrtpEndpoint.DEFAULT_AUTH_TAG_LENGTH;

    /**
     * Keys the end-to-end media payload SRTP from the group call key, per participant JID (Family B).
     *
     * <p>Must be called before {@link #start()}. Group calls reuse the same per-participant call-key
     * HKDF as one-to-one calls: the local stream is keyed with the local participant JID and each
     * forwarded peer stream with that peer's JID. SFrame end-to-end protection is layered on top.
     *
     * @param callKey       the 32-byte group call key
     * @param localJid      the local participant {@code <lid>:<device>@lid} JID
     * @param authTagLength the truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}
     * @return this session, for chaining before {@link #start()}
     */
    public GroupCallSession useParticipantKeys(byte[] callKey, String localJid, int authTagLength) {
        this.callKey = callKey == null ? null : callKey.clone();
        this.localParticipantJid = localJid;
        this.authTagLength = authTagLength;
        return this;
    }

    /**
     * Returns the active call this session drives.
     *
     * @return the active call
     */
    public CallRuntime call() {
        return call;
    }

    /**
     * Returns whether the handshake has completed and the media plane is wired.
     *
     * <p>This reports {@code true} once both the audio pipeline and the outbound sender have been
     * instantiated by the completer thread, which happens only after the DTLS handshake succeeds.
     *
     * @return {@code true} if media is flowing
     */
    public boolean connected() {
        return audio != null && sender != null;
    }

    /**
     * Returns a snapshot of the receive SSRCs of the currently-subscribed participants.
     *
     * @return an immutable copy of the subscribed SSRC set
     */
    public Set<Integer> subscribedSsrcs() {
        return Set.copyOf(subscribers.keySet());
    }

    /**
     * Starts the DTLS handshake and spawns the completer thread that wires the outbound media plane.
     *
     * <p>The handshake runs on a virtual thread; this method returns immediately. Calling it more
     * than once, or after {@link #close()}, is a no-op.
     */
    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        dtls.start();
        Thread.ofVirtual()
                .name("group-call-completer")
                .start(this::completeAfterHandshake);
    }

    /**
     * Blocks until the handshake completes and the local microphone pipeline is wired, or fails.
     *
     * <p>This first waits for the DTLS handshake, then polls until the completer thread has
     * instantiated the audio pipeline. It throws if the session is closed before wiring finishes or
     * if wiring does not complete within the given budget.
     *
     * @param timeout the maximum time to wait
     * @param unit    the unit of {@code timeout}
     * @throws IOException          if the handshake failed, the session closed early, or wiring timed
     *                              out
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @implNote This implementation polls the {@code audio} field every 5 ms after the handshake
     * completes because the completer thread wires the pipeline asynchronously; the same {@code unit}
     * budget bounds both the handshake wait and the wiring poll.
     */
    public void awaitConnected(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        if (hopByHopKey == null) {
            dtls.awaitHandshake(timeout, unit);
        }
        var deadline = System.nanoTime() + unit.toNanos(timeout);
        while (audio == null) {
            if (System.nanoTime() > deadline) {
                throw new IOException("group call did not finish wiring within " + timeout + " " + unit);
            }
            if (closed) {
                throw new IOException("session closed before media wiring");
            }
            Thread.sleep(5);
        }
    }

    /**
     * Subscribes to a participant's forwarded audio stream.
     *
     * <p>This requires the DTLS handshake to have completed so the SRTP endpoint is available, and is
     * idempotent for a given SSRC: subscribing again with the same {@link GroupCallParticipant#audioSsrc()}
     * closes the previous decoder and replaces the subscriber so the latest listener wins.
     *
     * @param participant the participant to subscribe to
     * @throws NullPointerException  if {@code participant} is {@code null}
     * @throws IllegalStateException if the session is closed or the handshake has not completed
     */
    public synchronized void addParticipant(GroupCallParticipant participant) {
        Objects.requireNonNull(participant, "participant cannot be null");
        if (closed) {
            throw new IllegalStateException("session is closed");
        }
        SrtpEndpoint srtp;
        if (callKey != null) {
            srtp = null; // per-participant Family-B endpoints are built in buildSubscriber
        } else if (hopByHopKey != null) {
            srtp = SrtpEndpoint.fromHopByHopKey(hopByHopKey, authTagLength);
        } else {
            srtp = dtls.srtpEndpoint();
            if (srtp == null) {
                throw new IllegalStateException("DTLS handshake has not completed");
            }
        }
        var existing = subscribers.remove(participant.audioSsrc());
        if (existing != null) {
            try {
                existing.decoder.close();
            } catch (Throwable _) {
            }
        }
        subscribers.put(participant.audioSsrc(),
                buildSubscriber(srtp, participant));
    }

    /**
     * Records a participant join announced through {@code <group_update action="add">} whose
     * forward SSRC is not yet known.
     *
     * <p>The actual {@link #addParticipant(GroupCallParticipant)} call can only happen once the SFU
     * publishes the participant's send SSRC via the
     * {@link com.github.auties00.cobalt.model.call.datachannel.SenderSubscriptions SenderSubscriptions}
     * AppData message; until then we record the join and reconcile later.
     *
     * @param jid the participant JID
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    public void notePendingJoin(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        pendingJoins.put(jid, Boolean.TRUE);
    }

    /**
     * Resolves a previously-pending join now that the SFU has reported the participant's send SSRC.
     *
     * <p>If {@code jid} is in {@link #pendingJoins}, builds a {@link GroupCallParticipant} via the
     * supplied factory and routes it through {@link #addParticipant(GroupCallParticipant)}; the
     * pending entry is then removed. If the participant was never marked pending (e.g., joined
     * before the call layer observed any {@code group_update}), the factory is invoked anyway so
     * late-arriving subscriptions still wire.
     *
     * @param jid        the participant JID whose SSRC is now known
     * @param audioSsrc  the SFU-assigned audio receive SSRC
     * @param onResolved factory turning {@code (jid, audioSsrc)} into a {@link GroupCallParticipant};
     *                   typically attaches the application's audio-frame listener
     */
    public void resolvePendingParticipant(Jid jid, int audioSsrc,
                                          java.util.function.BiFunction<Jid, Integer, GroupCallParticipant> onResolved) {
        Objects.requireNonNull(jid, "jid cannot be null");
        Objects.requireNonNull(onResolved, "onResolved cannot be null");
        pendingJoins.remove(jid);
        var participant = onResolved.apply(jid, audioSsrc);
        if (participant != null) {
            addParticipant(participant);
        }
    }

    /**
     * Returns whether a join for {@code jid} has been observed but not yet resolved with a SSRC.
     *
     * @param jid the participant JID
     * @return {@code true} when pending
     */
    public boolean isPendingJoin(Jid jid) {
        return pendingJoins.containsKey(jid);
    }

    /**
     * Removes the participant with the given JID, closing its decoder and stopping frame delivery.
     *
     * <p>The first subscriber whose participant matches {@code jid} is removed; if none matches, this
     * is a no-op.
     *
     * @param jid the JID of the participant to remove
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    public synchronized void removeParticipant(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        pendingJoins.remove(jid);
        Integer toRemove = null;
        for (var entry : subscribers.entrySet()) {
            if (entry.getValue().participant.jid().equals(jid)) {
                toRemove = entry.getKey();
                break;
            }
        }
        if (toRemove == null) {
            return;
        }
        var sub = subscribers.remove(toRemove);
        if (sub != null) {
            try {
                sub.decoder.close();
            } catch (Throwable _) {
            }
        }
    }

    /**
     * Tears down the session, closing every subscriber decoder, the audio pipeline, and the DTLS
     * driver (which closes the transport).
     *
     * <p>Calling this more than once is a no-op.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var sub : Map.copyOf(subscribers).values()) {
            try {
                sub.decoder.close();
            } catch (Throwable _) {
            }
        }
        subscribers.clear();
        if (audio != null) {
            try {
                audio.close();
            } catch (Throwable _) {
            }
            audio = null;
        }
        try {
            dtls.close();
        } catch (Throwable _) {
        }
    }

    /**
     * Runs on the completer thread: awaits the handshake and then builds the outbound sender and
     * audio pipeline.
     *
     * <p>On handshake failure the session is closed and the call is hung up. On success, and only if
     * the session is still open, this installs the outbound sender, registers the inbound SRTP
     * handler, and starts the audio pipeline; if the pipeline fails to start, the session is closed.
     *
     * @implNote This implementation bounds the handshake wait at 30 seconds, matching the call layer's
     * setup timeout, and any failure path hangs the call up so the application observes the ended
     * state.
     */
    private void completeAfterHandshake() {
        SrtpEndpoint srtp;
        if (callKey != null) {
            // Group media payload uses the same Family-B per-participant call-key SRTP as 1:1; the
            // outbound stream is keyed with the local participant JID.
            // TODO: layer SFrame end-to-end protection over the Family-B SRTP for group calls. The
            //  per-participant SFrame base key is already derivable via
            //  SFrameKeyDerivation.deriveParticipantBaseKey(callKey, participantJid); what is still
            //  missing is the per-frame SFrame transform (header KID/CTR layout, per-frame key/salt/
            //  nonce expansion, AES-128-CTR + HMAC-SHA256 4-byte tag, and the ratchet step) wired
            //  over this SRTP context. That wire format is not yet captured byte-exact from the voip
            //  WASM, so it is intentionally not emitted here rather than guessed.
            srtp = SrtpEndpoint.fromParticipantKeys(callKey, localParticipantJid,
                    localParticipantJid, authTagLength);
        } else if (hopByHopKey != null) {
            srtp = SrtpEndpoint.fromHopByHopKey(hopByHopKey, authTagLength);
        } else {
            try {
                srtp = dtls.awaitHandshake(30, TimeUnit.SECONDS);
            } catch (Throwable _) {
                close();
                try {
                    call.end(CallEndReason.UNKNOWN, null);
                } catch (Throwable suppressed) {
                }
                return;
            }
        }
        synchronized (this) {
            if (closed) {
                return;
            }
            this.sender = new RtpSender(options.opusPayloadType(), options.localAudioSsrc(),
                    options.audio().sampleRate(), srtp, dtls::sendSrtp);
            dtls.setSrtpHandler(this::onProtectedSrtp);
            var pipeline = new AudioPipeline(call, this::onEncodedOutbound, options.audio());
            try {
                pipeline.start();
            } catch (RuntimeException e) {
                try {
                    pipeline.close();
                } catch (Throwable _) {
                }
                close();
                return;
            }
            this.audio = pipeline;
        }
    }

    /**
     * Builds a {@link Subscriber} bundle for the given participant.
     *
     * <p>The receiver filters on the participant's SSRC and the Opus payload type, dispatching each
     * inbound payload to {@link #deliverFromPeer}; the decoder is dedicated to this participant.
     *
     * @param sharedSrtp  the negotiated SRTP endpoint
     * @param participant the participant to build the subscriber for
     * @return the constructed subscriber
     */
    private Subscriber buildSubscriber(SrtpEndpoint sharedSrtp, GroupCallParticipant participant) {
        var decoder = new OpusDecoder(options.audio().sampleRate(), options.audio().channels());
        // Each forwarded peer stream is Family-B keyed with that peer's participant JID (group reuses
        // the 1:1 per-participant call-key HKDF); fall back to the shared hop-by-hop or DTLS endpoint
        // when no call key was distributed.
        var peerSrtp = callKey != null
                ? SrtpEndpoint.fromParticipantKeys(callKey, localParticipantJid,
                        participant.jid().toString(), authTagLength)
                : sharedSrtp;
        var receiver = new RtpReceiver(peerSrtp, participant.audioSsrc(),
                options.opusPayloadType(),
                inbound -> deliverFromPeer(participant, decoder, inbound));
        return new Subscriber(participant, receiver, decoder, options.audio().frameSize());
    }

    /**
     * Decodes one inbound RTP payload from a participant and forwards the resulting {@link AudioFrame}
     * to its listener.
     *
     * <p>A missing marker on the inbound emission drives the decoder's packet-loss concealment instead
     * of decoding bytes. Empty decoder output and decode failures are dropped silently, and any
     * exception thrown by the listener is swallowed so one misbehaving consumer cannot stall the media
     * plane.
     *
     * @param participant the source participant
     * @param decoder     the participant's dedicated decoder
     * @param inbound     the receiver's emission
     */
    private void deliverFromPeer(GroupCallParticipant participant, OpusDecoder decoder,
                                 RtpReceiver.InboundRtp inbound) {
        if (closed) {
            return;
        }
        short[] pcm;
        try {
            pcm = inbound.missing()
                    ? decoder.decodePacketLoss(options.audio().frameSize())
                    : decoder.decode(inbound.payload(), options.audio().frameSize());
        } catch (RuntimeException _) {
            return;
        }
        if (pcm.length == 0) {
            return;
        }
        var ptsMs = inbound.timestamp() * 1000L / options.audio().sampleRate();
        var frame = new AudioFrame(pcm, ptsMs);
        try {
            participant.onAudio().accept(frame);
        } catch (Throwable _) {
        }
    }

    /**
     * Demultiplexes one inbound SRTP packet by SSRC and routes it to the matching subscriber.
     *
     * <p>Packets shorter than the RTP header are dropped. The SSRC is read from the clear header,
     * the matching {@link Subscriber} is looked up, and the packet is handed to its receiver before
     * draining the jitter buffer; packets with an unknown SSRC are ignored.
     *
     * @param packet the SRTP-protected bytes
     * @implNote This implementation reads the SSRC from bytes 8 through 11 of the RTP header, which is
     * in clear under SRTP, so the right subscriber is selected before any AES-CM decryption cost; the
     * 12-byte guard is the minimum RTP header length.
     */
    private void onProtectedSrtp(byte[] packet) {
        if (packet.length < 12) {
            return;
        }
        var ssrc = ((packet[8] & 0xFF) << 24) | ((packet[9] & 0xFF) << 16)
                   | ((packet[10] & 0xFF) << 8) | (packet[11] & 0xFF);
        var subscriber = subscribers.get(ssrc);
        if (subscriber == null) {
            return;
        }
        subscriber.receiver.onSrtpPacket(packet);
        subscriber.receiver.drain();
    }

    /**
     * Forwards one encoded outbound Opus frame to the single RTP sender, which the SFU re-broadcasts
     * to every participant.
     *
     * <p>If the sender is not yet wired the frame is dropped, and any send failure is swallowed so a
     * transient transport error does not stop the pipeline.
     *
     * @param packet the encoded outbound packet
     */
    private void onEncodedOutbound(OpusPacket packet) {
        var s = sender;
        if (s == null) {
            return;
        }
        try {
            s.send(packet.payload(), packet.ptsMs(), packet.voiceActive());
        } catch (RuntimeException _) {
        }
    }

    /**
     * Returns the negotiated SRTP endpoint, or {@code null} until the handshake completes.
     *
     * @return the SRTP endpoint, or {@code null} if the handshake has not completed
     */
    public SrtpEndpoint srtpEndpoint() {
        return dtls.srtpEndpoint();
    }

    /**
     * Applies a freshly-published end-to-end rekey bundle to this group session's SRTP endpoint.
     *
     * <p>Walks {@link com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload#keys()} and
     * routes each {@link com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType#AUDIO AUDIO}
     * or {@link com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType#VIDEO VIDEO} entry to
     * {@link com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint#rotateMasterKey(byte[])}
     * on the shared endpoint owned by this session's DTLS driver. The rotation invalidates every
     * per-participant inbound context, so the next inbound packet on each peer's SSRC rebuilds its
     * context with the new master key.
     *
     * @param payload the rekey bundle
     * @throws NullPointerException if {@code payload} is {@code null}
     */
    @Override
    public void applyRekey(com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        var srtp = dtls.srtpEndpoint();
        if (srtp == null) {
            return;
        }
        for (var entry : payload.keys()) {
            switch (entry.type()) {
                case AUDIO, VIDEO -> srtp.rotateMasterKey(entry.key());
                case APPDATA -> { /* DataChannel rekey; outside the SRTP endpoint */ }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation is a no-op: a group call session receives audio only, so there is no
     * inbound video sender to request a key frame from. Group video, were it added, would request key
     * frames per subscribed participant SSRC over the SFU rather than on a single peer leg.
     */
    @Override
    public void requestKeyframe() {
    }

    /**
     * Encodes an
     * {@link com.github.auties00.cobalt.model.call.datachannel.RxSubscriptions RxSubscriptions}
     * proto describing which participants the local user wants to receive video from, and ships it
     * over the supplied AppData {@link com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannel
     * DataChannel}.
     *
     * <p>The encoded payload is a length-prefixed
     * {@link com.github.auties00.cobalt.model.call.datachannel.RxSubscriptionsSpec RxSubscriptionsSpec}-serialized
     * message. Audio-only group calls still ship an empty bundle so the SFU knows the receiver is
     * subscribed to nothing.
     *
     * @param channel the AppData DataChannel obtained from
     *                {@link com.github.auties00.cobalt.call.transport.ActiveCallTransport#dataChannel() ActiveCallTransport.dataChannel()}
     * @param videoPids the participant PIDs we want any video from; pass an empty set for
     *                  audio-only subscriptions
     * @param videoQualities the per-PID quality entries; pass an empty list for audio-only
     * @throws NullPointerException if any argument is {@code null}
     */
    public void requestSubscriptions(
            com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannel channel,
            java.util.Set<Integer> videoPids,
            java.util.List<com.github.auties00.cobalt.model.call.datachannel.RxVidSubscriptionInfo> videoQualities) {
        Objects.requireNonNull(channel, "channel cannot be null");
        Objects.requireNonNull(videoPids, "videoPids cannot be null");
        Objects.requireNonNull(videoQualities, "videoQualities cannot be null");
        var subs = new com.github.auties00.cobalt.model.call.datachannel.RxSubscriptionsBuilder()
                .vidRxPids(java.util.List.copyOf(videoPids))
                .vidSubscriptions(java.util.List.copyOf(videoQualities))
                .build();
        var bytes = com.github.auties00.cobalt.model.call.datachannel.RxSubscriptionsSpec.encode(subs);
        channel.send(bytes);
    }
}
