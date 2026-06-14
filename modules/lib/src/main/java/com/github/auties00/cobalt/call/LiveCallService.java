package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.audio.AudioPipelineOptions;
import com.github.auties00.cobalt.call.interaction.CallInteractionEncoder;
import com.github.auties00.cobalt.call.rtp.srtp.SecureSsrcDerivation;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.session.GroupCallSession;
import com.github.auties00.cobalt.call.session.VoiceCallOptions;
import com.github.auties00.cobalt.call.session.VoiceCallSession;
import com.github.auties00.cobalt.call.signaling.CallIdGenerator;
import com.github.auties00.cobalt.call.signaling.CallReceiver;
import com.github.auties00.cobalt.call.signaling.CallRelay;
import com.github.auties00.cobalt.call.signaling.CallStanza;
import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.call.stream.VideoInputStream;
import com.github.auties00.cobalt.call.stream.VideoOutputStream;
import com.github.auties00.cobalt.call.transport.ActiveCallTransport;
import com.github.auties00.cobalt.call.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.transport.ice.IceCredentials;
import com.github.auties00.cobalt.call.transport.relay.WaRelayConnector;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientType;
import com.github.auties00.cobalt.listener.linked.LinkedCallEndedListener;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.model.call.*;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayloadSpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.CallEventBuilder;
import com.github.auties00.cobalt.wam.type.CallResultType;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Live implementation of {@link CallService} that coordinates one client's call activity.
 *
 * <p>This service owns the registry of in-flight {@link CallRuntime} sessions and exposes the
 * call-control entry points used by {@link LinkedWhatsAppClient}:
 * {@link #placeCall(Jid, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)} for
 * outbound calls, and
 * {@link #accept(IncomingCall, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)}
 * and {@link #reject(IncomingCall, CallEndReason)} for inbound offers. It sits
 * between the public client API and listener surface above and the signaling
 * classes ({@link CallReceiver}, {@link CallStanza}) and transport and media
 * layers below. There is one instance per {@link LinkedWhatsAppClient}.
 *
 * <p>The service implements the signaling and state-machine portion of a
 * call: it produces a public {@link Call} data view backed by a {@link CallRuntime} that owns the
 * lifecycle and reacts to peer-side state changes routed back through
 * {@link #onPeerAccept(String)}, {@link #onPeerReject(String, String)}, and
 * {@link #onPeerTerminate(String, String)}. A placed or accepted call sits in
 * {@link CallState#CONNECTING} until the media plane wires into its
 * {@link CallRuntime} and is terminated by either a local
 * {@link #terminate(String, CallEndReason)} or a peer termination.
 */
public class LiveCallService implements CallService {
    /**
     * Holds the owning client.
     *
     * <p>The client is used to send signaling stanzas, resolve the local self
     * {@link Jid}, and surface end-of-call notifications to listeners.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Tracks live call runtimes keyed by their unique call identifier.
     *
     * <p>An entry is added on
     * {@link #placeCall(Jid, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)}
     * or {@link #accept(IncomingCall, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)}
     * and removed by {@link #unregister(String)} when the call reaches
     * {@link CallState#ENDED}.
     */
    private final ConcurrentHashMap<String, CallRuntime> activeCalls = new ConcurrentHashMap<>();

    /**
     * Holds the pre-acceptance credentials of inbound offers, keyed by call identifier.
     *
     * <p>An inbound {@code <offer>} carries the relay block and, in its per-device {@code <enc>} fanout,
     * the caller's 32-byte call key, both of which the callee needs to bring up its media plane only once
     * the user answers. {@link CallReceiver} stashes them here through
     * {@link #noteOfferCredentials(String, CallRelay, byte[])} as the offer is dispatched (the slim
     * {@link IncomingCall} model cannot carry the {@code modules/lib} {@link CallRelay} type), and
     * {@link #accept(IncomingCall, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)}
     * consumes them onto the {@link CallRuntime}. An offer that is rejected, cancelled, or times out
     * instead has its entry evicted by {@link #discardOfferCredentials(String)}, so an un-answered offer
     * never leaks its relay block or call key. The caller-side bring-up continuation and call key live on
     * the {@link CallRuntime} itself rather than in a service map.
     */
    private final ConcurrentHashMap<String, PendingOffer> pendingOffers = new ConcurrentHashMap<>();

    /**
     * Holds the WAM service used to commit the per-call telemetry event.
     */
    private final WamService wamService;

    /**
     * Holds the {@link MessageService} that owns the per-device fanout encryption + addressing-mode
     * resolution for outbound call offers and the Signal decryption for inbound
     * {@code <enc_rekey>} envelopes.
     */
    private final MessageService messageService;

    /**
     * Length, in bytes, of the per-call shared key that authenticates the relay-encrypted
     * media stream. The wire format wraps the key in a
     * {@code MessageContainer{ call: Call{ callKey } }} protobuf and Signal-encrypts it per
     * peer device.
     */
    private static final int CALL_KEY_LENGTH = 32;

    /**
     * Logger for transport bring-up diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(LiveCallService.class.getName());

    /**
     * Default remote audio SSRC accepted by the inbound-audio pipeline.
     */
    private static final int DEFAULT_REMOTE_AUDIO_SSRC = 0xCB02;

    /**
     * Default local video SSRC for the camera or screen-share track.
     */
    private static final int DEFAULT_LOCAL_VIDEO_SSRC = 0xCB03;

    /**
     * Default remote video SSRC accepted by the inbound-video pipeline.
     */
    private static final int DEFAULT_REMOTE_VIDEO_SSRC = 0xCB04;

    /**
     * Default local SSRC for the screen-share track, distinct from the camera SSRC so a camera and a
     * screen share can run at once.
     */
    private static final int DEFAULT_LOCAL_SCREEN_SSRC = 0xCB05;

    /**
     * Default remote SSRC accepted for the peer's screen-share track.
     */
    private static final int DEFAULT_REMOTE_SCREEN_SSRC = 0xCB06;

    /**
     * Default Opus RTP payload type.
     *
     * @implNote This implementation uses {@code 120}, the dynamic payload type WhatsApp's voip engine
     * stamps on Opus media RTP. A live 1:1 call to a WhatsApp Android client shows its outbound audio
     * arriving with payload type {@code 120}; sending any other type leaves the peer unable to map the
     * stream to a codec, so it never reaches the connected media state. The payload type is not carried
     * in the {@code <audio>} call-signaling element, so it is a fixed profile constant rather than a
     * negotiated value.
     */
    private static final int DEFAULT_OPUS_PAYLOAD_TYPE = 120;

    /**
     * DTLS-handshake timeout used by the media-session bring-up before the call is marked
     * connected.
     */
    private static final long DTLS_HANDSHAKE_TIMEOUT_SECONDS = 15;

    /**
     * Time budget for the ICE connectivity check to bind the relay path before media is pumped.
     *
     * @implNote This implementation uses 8 seconds: the relay binding is a single roundtrip retried
     * every 50 ms, so 8 seconds comfortably covers a slow relay without stalling the call setup past
     * the user-visible ring window.
     */
    private static final long ICE_CONNECTIVITY_TIMEOUT_SECONDS = 8;

    /**
     * Default SCTP port used by both sides for the in-call data channel, matching the WebRTC
     * convention of {@code 5000} on both ends.
     */
    private static final int DEFAULT_SCTP_PORT = 5000;

    /**
     * Number of times each data-plane in-call action is re-sent over the unreliable DataChannel.
     *
     * @implNote This implementation uses 3, mirroring the live voip stack, which repeats each app-data message a few
     * times because the call's DataChannel is {@code maxRetransmits=0} (lossy) so a single send may be dropped.
     */
    private static final int DATA_CHANNEL_RESEND_COUNT = 3;

    /**
     * Source of randomness for {@code callKey} bytes.
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * Constructs a service bound to the given client and {@link MessageService}.
     *
     * @param whatsapp       the owning client
     * @param wamService     the WAM telemetry service used for end-of-call field-stats events
     * @param messageService the {@link MessageService} used to build, encrypt, and ship the
     *                       outbound {@code <call><offer>} stanza per peer and to decrypt the
     *                       inbound {@code <enc_rekey>} envelope
     */
    public LiveCallService(LinkedWhatsAppClient whatsapp, WamService wamService,
                           MessageService messageService) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.wamService = wamService;
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
    }

    /**
     * Builds a fresh {@link CallRuntime} for a call backed by the supplied media streams and registers
     * it under {@code callId}.
     *
     * <p>The runtime builds and owns its own telemetry accumulator, deriving the call side from
     * {@link Call#isOutgoing()}.
     *
     * @param callId   the call identifier
     * @param call     the public data view the runtime drives
     * @param audioOut the stream the engine drains local audio from for transmission
     * @param audioIn  the stream the engine fills with received remote audio
     * @param videoOut the stream the engine drains local video from for transmission, or {@code null}
     *                 for an audio-only call
     * @param videoIn  the stream the engine fills with received remote video, or {@code null} for an
     *                 audio-only call
     * @return the registered runtime
     */
    private CallRuntime registerRuntime(String callId, Call call,
                                        AudioOutputStream audioOut, AudioInputStream audioIn,
                                        VideoOutputStream videoOut, VideoInputStream videoIn) {
        // CallRuntime always plumbs four streams; an audio-only call carries idle buffered video
        // streams the engine never drains or fills, since maybeStartVideoTrack gates on Call.isVideo().
        var runtimeVideoOut = videoOut != null ? videoOut : VideoOutputStream.buffered();
        var runtimeVideoIn = videoIn != null ? videoIn : VideoInputStream.buffered();
        var runtime = new CallRuntime(this, call, audioOut, audioIn, runtimeVideoOut, runtimeVideoIn);
        activeCalls.put(callId, runtime);
        return runtime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call placeCall(Jid peer, AudioOutputStream audioOut, AudioInputStream audioIn,
                          VideoOutputStream videoOut, VideoInputStream videoIn) {
        Objects.requireNonNull(peer, "peer cannot be null");
        Objects.requireNonNull(audioOut, "audioOut cannot be null");
        Objects.requireNonNull(audioIn, "audioIn cannot be null");
        if (peer.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException(
                    "placeCall is for one-to-one calls; use placeGroupCall for a group or community JID: "
                    + peer);
        }
        var video = videoOut != null;
        var selfJid = whatsapp.store().accountStore().jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var callId = CallIdGenerator.generate();
        var call = new Call(callId, peer, peer, selfJid, true, false, video, CallState.CONNECTING);
        var runtime = registerRuntime(callId, call, audioOut, audioIn, videoOut, videoIn);

        var callKey = new byte[CALL_KEY_LENGTH];
        random.nextBytes(callKey);
        runtime.setCallKey(callKey);

        var ack = messageService.sendCall(peer, callId, callKey, video);
        LOGGER.log(System.Logger.Level.INFO, "[mediaplane-diag] placeCall " + callId
                                             + " ackRelayPresent=" + ack.relay().isPresent()
                                             + ack.relay().map(r -> " key=" + r.callKey().isPresent() + " hbh=" + r.hbhKey().isPresent()
                                                                    + " te2=" + r.endpoints().size()).orElse(""));
        ack.relay().ifPresent(relay -> bringUpMediaPlane(runtime, relay, true));

        return call;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call placeGroupCall(java.util.Set<Jid> peers, Jid groupJid, AudioOutputStream audioOut,
                               AudioInputStream audioIn, VideoOutputStream videoOut, VideoInputStream videoIn) {
        Objects.requireNonNull(peers, "peers cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(audioOut, "audioOut cannot be null");
        Objects.requireNonNull(audioIn, "audioIn cannot be null");
        if (peers.isEmpty()) {
            throw new IllegalArgumentException("peers cannot be empty");
        }
        var video = videoOut != null;
        var selfJid = whatsapp.store().accountStore().jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var callId = CallIdGenerator.generate();
        var peerList = List.copyOf(peers);
        // TODO: Call.peer() for a group call is an arbitrary participant (the first peer); the model has
        //  no first-class notion of "the group" as the peer. Listeners that read peer() on a group call
        //  see one member, not the group. Model the group peer properly, or document peer() as unspecified
        //  for group calls.
        var primaryPeer = peerList.get(0);
        var call = new Call(callId, primaryPeer, groupJid, selfJid, true, true, video, CallState.CONNECTING);
        var runtime = registerRuntime(callId, call, audioOut, audioIn, videoOut, videoIn);

        // A group call sends ONE offer to the group-call JID (<group>@call) carrying the group-jid and
        // a <group_info> of participants, with the shared call key encrypted to every participant
        // device; the server fans it out to the group's members.
        var callKey = new byte[CALL_KEY_LENGTH];
        random.nextBytes(callKey);
        runtime.setCallKey(callKey);

        var ack = messageService.sendGroupCall(groupJid, peerList, callId, callKey, video);
        ack.relay().ifPresent(relay -> bringUpMediaPlane(runtime, relay, true));
        return call;
    }

    /**
     * Drives the transport bring-up chain and instantiates the media session for one
     * {@link CallRuntime} after the relay-tokens block has been parsed.
     *
     * <p>Runs the entire connect chain on a virtual thread so the calling stanza handler
     * returns immediately:
     * <ol>
     *   <li>Build {@link ActiveCallTransport.StartParameters} from the relay-derived ICE
     *       credentials, a fresh self-signed {@link DtlsCertificate}, and
     *       {@link ActiveCallTransport#WA_PEER_DTLS_FINGERPRINT_SHA256}.</li>
     *   <li>Drive {@code transport.start}, then {@code connectRelay}, then
     *       {@code connectDtls} so the single shared {@link DtlsSrtpDriver} owned by the transport
     *       completes its handshake.</li>
     *   <li>Open the default SCTP data channel via
     *       {@code connectDataChannel(DEFAULT_SCTP_PORT, DEFAULT_SCTP_PORT)} so the same DTLS
     *       layer carries in-call signaling.</li>
     *   <li>Instantiate {@link VoiceCallSession} bound to the already-handshaked driver and
     *       start it; its completer wires the RTP and SRTP plumbing without re-running DTLS.</li>
     *   <li>Block until the session reports {@link VoiceCallSession#connected()}, attach the
     *       session to the call for lifecycle ownership, and flip the call to
     *       {@link CallState#ACTIVE} via {@link CallRuntime#notifyActive()}.</li>
     * </ol>
     *
     * @implNote This implementation consolidates the DTLS handshake on a single {@link DtlsSrtpDriver}
     * per call: the relay allocation produces one UDP socket and the byte-0 demux in
     * {@code DtlsSrtpDriver} routes STUN, DTLS (application data for SCTP), and SRTP/SRTCP back to
     * the right consumer. This matches WA's wire architecture, which issues a single
     * {@code auth_token} + {@code relay_key} per call rather than one per logical stream.
     *
     * @param runtime       the runtime whose transport is being brought up
     * @param transportSpec the parsed relay-tokens block (from the offer ACK on the caller
     *                      side, or from the inbound offer on the callee side)
     * @param isCaller      {@code true} for the call placer (DTLS client), {@code false}
     *                      for the callee (DTLS server)
     */
    private void bringUpMediaPlane(CallRuntime runtime, CallRelay transportSpec, boolean isCaller) {
        if (!runtime.beginMediaPlane()) {
            return;
        }
        Thread.ofVirtual()
                .name("call-bringup-" + runtime.call().callId())
                .start(() -> bringUpMediaPlane0(runtime, transportSpec, isCaller));
    }

    /**
     * Runs the transport + media-session bring-up steps documented on
     * {@link #bringUpMediaPlane(CallRuntime, CallRelay, boolean)} on the calling thread.
     *
     * @param runtime       the runtime whose transport is being brought up
     * @param transportSpec the parsed relay-tokens block
     * @param isCaller      whether the local side placed the call
     */
    private void bringUpMediaPlane0(CallRuntime runtime, CallRelay transportSpec, boolean isCaller) {
        var callId = runtime.call().callId();
        try {
            // The async bring-up vthread can lose a race with an inbound reject/timeout/terminate:
            // the receiver ends the call before this thread reaches connectRelay. Re-check the call
            // state and skip silently when the call is no longer live, instead of throwing.
            if (runtime.call().state() == CallState.ENDED) {
                return;
            }
            var localCert = DtlsCertificate.generate();
            var transport = runtime.transport();
            var hbhKey = transportSpec.hbhKey().orElse(null);

            // The caller defers the ENTIRE media bring-up until the callee answers: the relay drops an
            // Allocate fired before <accept>, so the relay path cannot come up at offer-ACK. The callee
            // already holds the peer's accept (it just sent its own), so it runs inline.
            Runnable bringUp = () -> {
                // The offer-ACK relay block is allocated before the callee answers and carries
                // placeholder edgeray credentials the edgeray rejects (STUN error 431); the finalized
                // credentials the edgeray honors arrive in the peer's <accept> relay block. The web ICE
                // leg therefore keys from the accept relay when present. The native raw-UDP relay path
                // keeps its proven offer-ACK block to avoid regressing the web-to-mobile flow.
                var effectiveSpec = relayMode() == ActiveCallTransport.RelayMode.WEB
                        ? runtime.acceptRelay().orElse(transportSpec)
                        : transportSpec;
                var effectiveHbhKey = effectiveSpec.hbhKey().orElse(hbhKey);
                LOGGER.log(System.Logger.Level.INFO, "[mediaplane-diag] bringUp.run " + callId
                                                     + " isCaller=" + isCaller + " state=" + runtime.call().state()
                                                     + " usingAcceptRelay=" + runtime.acceptRelay().isPresent()
                                                     + " key=" + effectiveSpec.callKey().isPresent() + " hbh=" + (effectiveHbhKey != null));
                // The relay path needs the per-call relay <key>; a block without it cannot bring media up.
                if (effectiveSpec.callKey().isEmpty()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Cannot bring up call " + callId + " via relay: the relay block is missing the relay key");
                    return;
                }
                // The ICE ufrag is the relay <auth_token> base64; the MESSAGE-INTEGRITY is keyed on the
                // relay <key> base64 string. Both come from the effective (accept or offer-ACK) block.
                var authToken = !effectiveSpec.authTokens().isEmpty()
                        ? effectiveSpec.authTokens().getFirst().bytes()
                        : (!effectiveSpec.tokens().isEmpty() ? effectiveSpec.tokens().getFirst().bytes() : new byte[0]);
                var relayKeyBytes = effectiveSpec.callKey().orElseThrow();
                System.out.println("[webfix] bringUp.run callId=" + callId
                        + " src=" + (runtime.acceptRelay().isPresent() ? "ACCEPT" : "OFFER-ACK")
                        + " authTokenLen=" + authToken.length
                        + " keyLen=" + relayKeyBytes.length
                        + " keyAscii='" + new String(relayKeyBytes, java.nio.charset.StandardCharsets.US_ASCII) + "'"
                        + " te2Count=" + effectiveSpec.endpoints().size()
                        + " tokens=" + effectiveSpec.tokens().size()
                        + " authTokens=" + effectiveSpec.authTokens().size());
                var iceCreds = IceCredentials.fromRelay(authToken, relayKeyBytes);
                var params = new ActiveCallTransport.StartParameters(
                        iceCreds, localCert,
                        ActiveCallTransport.WA_PEER_DTLS_FINGERPRINT_SHA256,
                        isCaller, effectiveSpec, localAudioSsrc(runtime, effectiveSpec),
                        relayMode());
                WaRelayConnector.Allocation allocation;
                try {
                    transport.start(params);
                    allocation = transport.connectRelay();
                } catch (IllegalStateException ex) {
                    // Transport closed by an inbound reject/terminate while starting or allocating.
                    LOGGER.log(System.Logger.Level.INFO, "[mediaplane-diag] connectRelay ISE for " + callId
                                                         + ": " + ex.getMessage());
                    return;
                } catch (RuntimeException e) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Relay Allocate failed for call " + callId + ": " + e.getMessage());
                    return;
                }
                LOGGER.log(System.Logger.Level.INFO, "[mediaplane-diag] connectRelay OK " + callId
                                                     + " allocation=" + (allocation != null));
                completeMediaPlane0(runtime, localCert, allocation, isCaller, effectiveHbhKey, effectiveSpec);
            };
            // The caller defers the bring-up until the callee answers: the finalized relay credentials
            // (web edgeray ICE and native raw-UDP alike) only arrive in the <accept>. The callee already
            // holds the peer's accept, so it runs inline.
            if (isCaller) {
                runtime.setPendingMediaPlane(bringUp);
                if (runtime.peerAccepted()) {
                    runtime.runPendingMediaPlane();
                }
                return;
            }
            bringUp.run();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Transport bring-up failed for call " + callId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the relay transport this client drives, derived from its
     * {@link LinkedWhatsAppClientType client type}.
     *
     * <p>A {@link LinkedWhatsAppClientType#WEB} client cannot open a raw UDP socket on a real browser,
     * so it tunnels the relay Allocate and the media SRTP through an SCTP DataChannel to the edgeray
     * ({@link ActiveCallTransport.RelayMode#WEB}); a {@link LinkedWhatsAppClientType#MOBILE} client uses
     * the native raw-UDP relay ({@link ActiveCallTransport.RelayMode#NATIVE}).
     *
     * @return the relay mode for this client
     */
    private ActiveCallTransport.RelayMode relayMode() {
        return whatsapp.store().accountStore().clientType() == LinkedWhatsAppClientType.WEB
                ? ActiveCallTransport.RelayMode.WEB
                : ActiveCallTransport.RelayMode.NATIVE;
    }

    /**
     * Announces the local user's initial unmuted media state once the call connects.
     *
     * <p>The native WhatsApp client emits {@code <mute_v2 mute-state="0">} immediately after the call
     * media comes up; the captured reference sequence shows it right after the {@code <accept>}. The
     * announcement marks the device as a live, actively-sending media participant. Cobalt previously
     * sent {@code mute_v2} only on an explicit user mute toggle, never the initial unmute, so it never
     * announced itself as an active sender.
     *
     * @param runtime       the connected call runtime
     * @param transportSpec the parsed relay-tokens block, source of the participant JIDs
     * @param callId        the call identifier
     */
    private void announceInitialMuteState(CallRuntime runtime, CallRelay transportSpec, String callId) {
        try {
            var localJid = resolveLocalParticipantJid(runtime, transportSpec);
            var peerJid = resolvePeerParticipantJid(runtime, transportSpec);
            var creatorLid = Jid.of(localJid);
            var peerLid = Jid.of(peerJid.replaceFirst(":[0-9]+@lid$", "@lid"));
            whatsapp.sendNodeWithNoResponse(CallStanza.mute(peerLid, creatorLid, callId, false).build());
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Initial mute-state announce failed for call " + callId + ": " + e.getMessage());
        }
    }

    /**
     * Runs the deferred half of the media bring-up: the peer-DTLS handshake, the SCTP data channel,
     * and the media session, after the relay has been allocated and (for the caller) the peer has
     * accepted.
     *
     * @param runtime       the runtime whose media plane is being completed
     * @param localCert     the self-signed DTLS certificate generated for this call
     * @param allocation    the relay allocation produced by {@code connectRelay}
     * @param isCaller      whether the local side placed the call
     * @param hbhKey        the 30-byte hop-by-hop key from the relay {@code <hbh_key>} element, or
     *                      {@code null}
     * @param transportSpec the parsed relay-tokens block, source of the participant JIDs and tag length
     */
    private void completeMediaPlane0(CallRuntime runtime, DtlsCertificate localCert,
                                     WaRelayConnector.Allocation allocation, boolean isCaller,
                                     byte[] hbhKey, CallRelay transportSpec) {
        var callId = runtime.call().callId();
        LOGGER.log(System.Logger.Level.INFO, "[mediaplane-diag] completeMediaPlane0 " + callId
                                             + " state=" + runtime.call().state() + " hbh=" + (hbhKey != null));
        try {
            if (runtime.call().state() == CallState.ENDED) {
                return;
            }
            var transport = runtime.transport();
            var role = isCaller ? SrtpRole.CLIENT : SrtpRole.SERVER;
            // WhatsApp encodes 1:1 audio in 40 ms Opus frames (live capture); a 10 ms frame produces
            // SILK-WB config 8, a combination WhatsApp never emits and its Opus depacketiser rejects with a
            // codec parse error. Match 40 ms, which selects the SILK-WB config 10 the peer accepts.
            // TODO: this pins the SILK speech engine, so music is degraded. Opus AUDIO mode reproduces
            //  music cleanly but, at 40 ms, can only do so with a two-frame CELT packet, which the peer's
            //  depacketiser also rejects (live: jb_puts 0). Probe the emulator (jb_puts) for a single-frame
            //  config the peer accepts that still allows CELT (a 20 ms frame is the candidate) to match
            //  WhatsApp's adaptive voice-plus-music quality.
            // In-band FEC on every call (as WhatsApp does) so a single lost packet is reconstructed from
            // the next one's piggybacked copy instead of leaving a jitter-buffer gap that stutters.
            var audioOpts = AudioPipelineOptions.defaults(40).withInbandFec(20);
            // Mic conditioning (echo cancellation, denoise, AGC, VAD) and DTX are for live acoustic capture
            // only; a file, tone, or application-fed source is already clean continuous line audio, so
            // conditioning distorts it and DTX silence-indicator frames break it into comfort noise.
            if (!runtime.audioOut().isLiveCapture()) {
                audioOpts = audioOpts.withoutAec().withoutPreprocessor().withoutDtx();
            }
            var voiceOpts = new VoiceCallOptions(
                    localAudioSsrc(runtime, transportSpec),
                    DEFAULT_REMOTE_AUDIO_SSRC,
                    DEFAULT_OPUS_PAYLOAD_TYPE,
                    audioOpts);

            // Hop-by-hop 1:1 media: the relay issued the SRTP keys in <hbh_key>, so the media plane
            // does not wait on a peer DTLS-SRTP handshake (the relay forwards SRTP and the peer P2P
            // path is disabled). Build the driver purely as the raw-SRTP byte transport over the relay
            // DataChannel and wire media immediately; skip the SCTP control channel, which the relayed
            // media path does not use.
            if (hbhKey != null) {
                DtlsSrtpDriver hbhDriver;
                try {
                    hbhDriver = transport.buildDtlsDriver();
                } catch (IllegalStateException _) {
                    return;
                }
                // The regular relay forwards media off the Allocate plus its keepalive alone; it exchanges
                // no STUN binding checks with the client (A/B capture verified), so connectIce is a no-op
                // success that simply confirms the allocation succeeded.
                transport.connectIce(hbhDriver, ICE_CONNECTIVITY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                var callKey = runtime.callKey();
                // The end-to-end participant media SRTP always authenticates with the fixed 4-byte media
                // tag; it is NOT the warp_mi_tag_len (a warp_mi_tag_len of 8 would make the peer reject our
                // media, which keys and tags the E2E layer independently of the relay hop). The raw-UDP
                // relay path sends plain SRTP straight to the relay :3478 socket, exactly as every real
                // WhatsApp client does (A/B capture verified), so no warp framing is applied.
                var mediaTagLen = SrtpEndpoint.RELAY_MEDIA_AUTH_TAG_LENGTH;
                if (runtime.call().isGroup()) {
                    // Group media routes through the SFU over the same hop-by-hop SRTP relay leg as a
                    // 1:1 call; per-participant end-to-end confidentiality (SFrame) layers on top.
                    var groupSession = new GroupCallSession(runtime, hbhDriver, voiceOpts);
                    groupSession.useHopByHopKey(hbhKey);
                    // Real WhatsApp keys the audio/video payload end-to-end from the call key (Family
                    // B, per participant JID); the hop-by-hop key keys only the SRTCP control traffic.
                    if (callKey != null) {
                        var localJid = resolveLocalParticipantJid(runtime, transportSpec);
                        groupSession.useParticipantKeys(callKey, localJid, mediaTagLen);
                    }
                    runtime.attachSession(groupSession);
                    groupSession.start();
                    groupSession.awaitConnected(DTLS_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (groupSession.connected()) {
                        runtime.notifyActive();
                    }
                    return;
                }
                var voiceSession = new VoiceCallSession(runtime, hbhDriver, role, localCert,
                        ActiveCallTransport.WA_PEER_DTLS_FINGERPRINT_SHA256, voiceOpts);
                voiceSession.useHopByHopKey(hbhKey);
                if (callKey != null) {
                    var localJid = resolveLocalParticipantJid(runtime, transportSpec);
                    var peerJid = resolvePeerParticipantJid(runtime, transportSpec);
                    voiceSession.useParticipantKeys(callKey, localJid, peerJid, mediaTagLen);
                }
                runtime.attachSession(voiceSession);
                voiceSession.start();
                voiceSession.awaitConnected(DTLS_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                LOGGER.log(System.Logger.Level.INFO, "[mediaplane-diag] voiceSession " + callId
                                                     + " connected=" + voiceSession.connected());
                if (voiceSession.connected()) {
                    runtime.notifyActive();
                    maybeStartVideoTrack(runtime, voiceSession);
                    announceInitialMuteState(runtime, transportSpec, callId);
                }
                return;
            }

            try {
                transport.connectDtls(DTLS_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (IllegalStateException _) {
                // Transport closed by an inbound reject/terminate during the handshake.
                return;
            }
            try {
                transport.connectDataChannel(DEFAULT_SCTP_PORT, DEFAULT_SCTP_PORT);
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Data channel bring-up failed for call " + callId + ": " + e.getMessage());
            }

            var driver = transport.dtlsDriver().orElseThrow(() ->
                    new IllegalStateException("connectDtls completed but driver is not exposed"));
            if (runtime.call().isGroup()) {
                // GroupCallSession owns its own DtlsSrtpDriver today; we hand it the relay's
                // datagram path and let it run a second handshake on top of the same UDP socket.
                var groupSession = new GroupCallSession(runtime, allocation.transport(),
                        role, localCert,
                        ActiveCallTransport.WA_PEER_DTLS_FINGERPRINT_SHA256,
                        voiceOpts);
                runtime.attachSession(groupSession);
                groupSession.start();
                groupSession.awaitConnected(DTLS_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (groupSession.connected()) {
                    runtime.notifyActive();
                }
            } else {
                var voiceSession = new VoiceCallSession(runtime, driver, role, localCert,
                        ActiveCallTransport.WA_PEER_DTLS_FINGERPRINT_SHA256,
                        voiceOpts);
                voiceSession.useHopByHopKey(hbhKey);
                runtime.attachSession(voiceSession);
                voiceSession.start();
                voiceSession.awaitConnected(DTLS_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (voiceSession.connected()) {
                    runtime.notifyActive();
                }
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Transport bring-up failed for call " + callId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the local participant {@code <lid>:<device>@lid} JID that keys the outbound media
     * stream.
     *
     * <p>The peer derives the key for our media stream (Family B) from our own LID device JID, exactly
     * as we key the inbound stream from {@link #resolvePeerParticipantJid(CallRuntime, CallRelay) the
     * peer's LID device JID}. The JID is therefore built from the account's LID user JID combined with
     * our device id (taken from the account's phone-number device JID), since a relayed call addresses
     * participants in LID space. The relay block's {@code <participant>} entry indexed by
     * {@code self_pid} and then the runtime's {@link Call#creator() creator} JID are fallbacks for when
     * no LID is recorded yet.
     *
     * @param runtime       the call runtime
     * @param transportSpec the parsed relay-tokens block
     * @return the local participant JID string
     */
    /**
     * Derives the deterministic audio SSRC the local side transmits on for a call.
     *
     * <p>WhatsApp endpoints do not negotiate SSRCs; both sides compute each participant's SSRC from the
     * shared call-id and the participant JID (see {@link SecureSsrcDerivation}). The peer registers a
     * receive context for exactly this value, so the local sender must stamp it onto its outbound audio
     * RTP and declare it in the relay stream-descriptor publish, or the peer drops the stream as an
     * unknown source.
     *
     * @param runtime       the call runtime, source of the call-id
     * @param transportSpec the parsed relay-tokens block, source of the local participant JID
     * @return the 32-bit local audio SSRC
     */
    private int localAudioSsrc(CallRuntime runtime, CallRelay transportSpec) {
        return SecureSsrcDerivation.audioMainSsrc(
                runtime.call().callId(), resolveLocalParticipantJid(runtime, transportSpec));
    }

    private String resolveLocalParticipantJid(CallRuntime runtime, CallRelay transportSpec) {
        var accountStore = whatsapp.store().accountStore();
        var selfLid = accountStore.lid().orElse(null);
        var selfPn = accountStore.jid().orElse(null);
        if (selfLid != null && selfPn != null) {
            return selfLid.toUserJid().withDevice(selfPn.device()).toString();
        }
        return participantJidForPid(transportSpec, transportSpec.selfPid())
                .orElseGet(() -> runtime.call().creator().toString());
    }

    /**
     * Resolves the peer participant {@code <lid>:<device>@lid} JID that keys the inbound media stream.
     *
     * <p>The peer encrypts its end-to-end media payload (Family B) with its answering device JID as the
     * HKDF info (for example {@code 258252122116273:71@lid}), which is the device that sent the
     * {@code preaccept} or {@code accept}. The relay roster's {@code peer_pid} instead maps to the bare
     * user JID ({@code 258252122116273@lid}), so deriving the inbound SRTP master from {@code peer_pid}
     * yields the wrong master and every inbound packet fails SRTP authentication. The answering device
     * JID recorded on the runtime is therefore preferred; the relay {@code peer_pid} entry and then the
     * call's {@link Call#peer() peer} JID are only fallbacks for when no answer device was captured.
     *
     * @param runtime       the call runtime
     * @param transportSpec the parsed relay-tokens block
     * @return the peer participant JID string
     */
    private static String resolvePeerParticipantJid(CallRuntime runtime, CallRelay transportSpec) {
        var answeringDevice = runtime.peerDeviceJid().map(Jid::toString);
        if (answeringDevice.isPresent()) {
            return answeringDevice.get();
        }
        return participantJidForPid(transportSpec, transportSpec.peerPid())
                .orElseGet(() -> runtime.call().peer().toString());
    }

    /**
     * Returns the {@code <participant>} JID string whose {@code pid} matches the given participant id.
     *
     * @param transportSpec the parsed relay-tokens block
     * @param pid           the participant id to match, possibly {@link java.util.OptionalInt#empty()}
     * @return the matching participant JID string, or empty when none matches
     */
    private static java.util.Optional<String> participantJidForPid(CallRelay transportSpec,
                                                                   java.util.OptionalInt pid) {
        if (pid.isEmpty()) {
            return java.util.Optional.empty();
        }
        var target = pid.getAsInt();
        return transportSpec.participants().stream()
                .filter(participant -> participant.pid() == target)
                .map(participant -> participant.jid().toString())
                .findFirst();
    }

    /**
     * Starts a camera video track on the session when the call was set up with video enabled.
     *
     * <p>For an audio-and-video call the local camera track must start as soon as the media plane is up
     * so the peer receives the local video; a mid-call audio-to-video upgrade routes through
     * {@link #startLocalVideo(String)} instead. A failure to start the track is logged and swallowed so
     * a missing or busy camera does not tear down an otherwise-healthy audio call.
     *
     * @param runtime the call runtime
     * @param session the connected voice-call session
     */
    private void maybeStartVideoTrack(CallRuntime runtime, VoiceCallSession session) {
        if (!runtime.call().isVideo()) {
            return;
        }
        try {
            session.startVideoTrack(com.github.auties00.cobalt.call.session.VideoTrackOptions.defaults(
                    DEFAULT_LOCAL_VIDEO_SSRC, DEFAULT_REMOTE_VIDEO_SSRC));
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not start video track for call " + runtime.call().callId() + ": " + e.getMessage());
        }
    }

    /**
     * Starts a camera video track mid-call, used by an accepted audio-to-video upgrade.
     *
     * <p>Looks up the call's attached voice session and starts a {@link com.github.auties00.cobalt.call.session.VideoTrackOptions.Kind#CAMERA}
     * track keyed on the session's already-negotiated SRTP endpoint. A no-op when the call is not
     * tracked or has no voice session yet.
     *
     * @param callId the call identifier
     */
    @Override
    public void startLocalVideo(String callId) {
        var runtime = activeCalls.get(callId);
        if (runtime == null) {
            return;
        }
        runtime.voiceSession().ifPresent(session -> {
            try {
                session.startVideoTrack(com.github.auties00.cobalt.call.session.VideoTrackOptions.defaults(
                        DEFAULT_LOCAL_VIDEO_SSRC, DEFAULT_REMOTE_VIDEO_SSRC));
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not start camera track for call " + callId + ": " + e.getMessage());
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startScreenShare(String callId) {
        startScreenShare(callId, com.github.auties00.cobalt.call.session.VideoTrackOptions.screenShareDefaults(
                DEFAULT_LOCAL_SCREEN_SSRC, DEFAULT_REMOTE_SCREEN_SSRC));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation announces the share with the screen-share track's configured
     * encode resolution, the dimensions the peer actually receives, rather than the raw capture-source
     * dimensions, which are not known until the first frame is decoded.
     */
    @Override
    public void startScreenShare(String callId, com.github.auties00.cobalt.call.session.VideoTrackOptions options) {
        var runtime = activeCalls.get(callId);
        if (runtime == null) {
            return;
        }
        runtime.voiceSession().ifPresent(session -> {
            try {
                session.startScreenShare(options);
                whatsapp.sendNodeWithNoResponse(CallStanza.screenShare(
                        runtime.call().peer(), runtime.call().creator(), callId,
                        options.pipeline().width(), options.pipeline().height()).build());
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not start screen-share track for call " + callId + ": " + e.getMessage());
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopScreenShare(String callId) {
        var runtime = find(callId);
        if (runtime == null) {
            return;
        }
        runtime.voiceSession().ifPresent(VoiceCallSession::stopScreenShare);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void noteOfferCredentials(String callId, CallRelay relay, byte[] callKey) {
        if (callId == null || (relay == null && callKey == null)) {
            return;
        }
        var incoming = new PendingOffer(relay, callKey == null ? null : callKey.clone());
        // Merge rather than overwrite: the relay and the call key can arrive in separate notes, so a
        // later note that carries only one of them must not clear the other.
        pendingOffers.merge(callId, incoming, (existing, added) -> new PendingOffer(
                added.relay() != null ? added.relay() : existing.relay(),
                added.callKey() != null ? added.callKey() : existing.callKey()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call accept(IncomingCall offer, AudioOutputStream audioOut, AudioInputStream audioIn,
                       VideoOutputStream videoOut, VideoInputStream videoIn) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(audioOut, "audioOut cannot be null");
        Objects.requireNonNull(audioIn, "audioIn cannot be null");
        var video = videoOut != null;
        var call = new Call(
                offer.callId(),
                offer.peer(),
                offer.chatJid(),
                offer.peer(),
                false,
                offer.group(),
                video,
                CallState.CONNECTING);
        var runtime = registerRuntime(offer.callId(), call, audioOut, audioIn, videoOut, videoIn);
        if (offer.group()) {
            // A group callee joins by addressing <preaccept> then <accept> to the call MUC address
            // (<callId>@call), not to the creator device. When the offer carried no inline <relay>
            // (the native desktop caller omits it), the server responds to this join with a
            // <group_update> that carries the <relay> block, which the receiver feeds back here to
            // bring up the media plane.
            var callTarget = Jid.of(offer.callId() + "@call");
            // The captured real callee sends <accept> only after the server acks the <preaccept>;
            // firing both back-to-back makes the server see the accept before it registers the
            // preaccept and drop the join. Send the preaccept with sendNode so this blocks on the
            // matching <ack> (correlated by stanza id) before the accept goes out. Runs on a virtual
            // thread so the call-listener dispatch is not blocked; the bring-up that consumes the
            // relay arrives later in a <group_update>.
            Thread.ofVirtual().name("call-join-" + offer.callId()).start(() -> {
                try {
                    whatsapp.sendNode(CallStanza.preaccept(offer.peer(), offer.callId(), callTarget));
                } catch (RuntimeException e) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Group preaccept ack wait failed for " + offer.callId() + ": " + e.getMessage());
                }
                if (activeCalls.containsKey(offer.callId())) {
                    // Send the accept with sendNode as well: this injects the mandatory stanza id on
                    // the <call> envelope (sendNodeWithNoResponse leaves it absent, and the server
                    // silently drops an id-less call action so the join never completes) and blocks on
                    // the matching <ack>. The <group_update> carrying the relay follows the ack.
                    try {
                        whatsapp.sendNode(CallStanza.accept(offer.peer(), offer.callId(), callTarget));
                    } catch (RuntimeException e) {
                        LOGGER.log(System.Logger.Level.DEBUG,
                                "Group accept ack wait failed for " + offer.callId() + ": " + e.getMessage());
                    }
                }
            });
        } else {
            // A one-to-one callee must route its <accept> with a stanza id on the <call> envelope:
            // sendNodeWithNoResponse leaves the id absent and the server silently drops an id-less call
            // action, so the caller never registers the answer and the call rings to timeout ("No
            // answer"). sendNode injects the mandatory id and blocks on the matching <ack>, mirroring the
            // group-join path above. Runs on a virtual thread so the call-listener dispatch is not
            // blocked while it waits for the ack; the media plane is brought up below once the accept is
            // on the wire.
            Thread.ofVirtual().name("call-accept-" + offer.callId()).start(() -> {
                try {
                    whatsapp.sendNode(CallStanza.accept(offer.peer(), offer.callId()));
                } catch (RuntimeException e) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Accept ack wait failed for " + offer.callId() + ": " + e.getMessage());
                }
            });
        }
        // The relay block and the 32-byte call key the caller generated were stashed by CallReceiver
        // as the offer was dispatched (noteOfferCredentials): the slim IncomingCall model cannot carry
        // the lib relay type, and the call key arrived Signal-encrypted in the offer's per-device
        // <enc> fanout. Transfer the call key onto the runtime so the bring-up keys Family-B media with
        // the SAME key both sides use, then bring the media plane up from the stashed relay. A group
        // offer may omit the inline relay (the native desktop caller sends it later in a
        // <group_update>); in that case only the call key is transferred here and onGroupRelay brings
        // the media plane up when the relay arrives.
        var pending = pendingOffers.remove(offer.callId());
        if (pending != null) {
            if (pending.callKey() != null) {
                runtime.setCallKey(pending.callKey());
            }
            if (pending.relay() != null) {
                bringUpMediaPlane(runtime, pending.relay(), false);
            }
        }
        return call;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reject(IncomingCall offer, CallEndReason reason) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        whatsapp.sendNodeWithNoResponse(CallStanza.reject(offer.peer(), offer.callId()).build());
        discardOfferCredentials(offer.callId());
        whatsapp.store().chatStore().removeCall(offer.callId());
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallEndedListener typed) {
                Thread.startVirtualThread(() ->
                        typed.onCallEnded(whatsapp, offer.callId(), offer.peer(), reason));
            }
        }
    }

    /**
     * Evicts the pre-acceptance offer credentials (the stashed relay block and decrypted call key) for
     * a call that ended before, or instead of, being answered.
     *
     * <p>{@link #noteOfferCredentials(String, CallRelay, byte[])} stashes these as an inbound offer is
     * dispatched, before any {@link CallRuntime} exists. When the offer is accepted the relay and call
     * key are consumed by
     * {@link #accept(IncomingCall, AudioOutputStream, AudioInputStream, VideoOutputStream, VideoInputStream)};
     * when the offer is instead rejected, cancelled, or times out, no runtime is ever registered, so this
     * is the only place that entry is released.
     *
     * @param callId the call identifier
     */
    private void discardOfferCredentials(String callId) {
        pendingOffers.remove(callId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CallRuntime find(String callId) {
        return callId == null ? null : activeCalls.get(callId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPeerAccept(String callId) {
        var runtime = find(callId);
        LOGGER.log(System.Logger.Level.INFO, "[mediaplane-diag] onPeerAccept " + callId
                                             + " runtimeFound=" + (runtime != null));
        if (runtime != null) {
            runtime.onPeerAccept();
            // Set the accept flag (above) before draining the continuation: this orders against the
            // relay-allocation thread's park + peerAccepted() check so the deferred bring-up runs
            // exactly once regardless of which side wins the race.
            runtime.runPendingMediaPlane();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onGroupRelay(String callId, CallRelay relay) {
        if (callId == null || relay == null) {
            return;
        }
        var runtime = activeCalls.get(callId);
        if (runtime == null || !runtime.call().isGroup()) {
            return;
        }
        bringUpMediaPlane(runtime, relay, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPeerReject(String callId, String reason) {
        var runtime = find(callId);
        if (runtime != null) {
            runtime.onPeerEnded(reason);
        } else {
            discardOfferCredentials(callId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPeerTerminate(String callId, String reason) {
        var runtime = find(callId);
        if (runtime != null) {
            runtime.onPeerEnded(reason);
        } else {
            discardOfferCredentials(callId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void terminate(String callId, CallEndReason reason) {
        Objects.requireNonNull(reason, "reason cannot be null");
        var runtime = find(callId);
        if (runtime == null) {
            return;
        }
        var call = runtime.call();
        sendTerminate(call.peer(), call.creator(), call.callId(), reason);
        runtime.end(reason, reason.wireValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregister(String callId) {
        var runtime = activeCalls.remove(callId);
        whatsapp.store().chatStore().removeCall(callId);
        if (wamService == null || runtime == null) {
            return;
        }
        emitFieldstatsEvent(runtime);
    }

    /**
     * Builds and commits the WAM Call event for one ended call.
     *
     * <p>The event combines the call's start-time telemetry dimensions, drawn from its
     * {@link CallRuntime#stats()} accumulator, with the call's terminal {@link CallEndReason}, mapped to
     * a {@link CallResultType}, and is committed through {@link WamService}. Any
     * {@link RuntimeException} raised while building or committing is swallowed so that telemetry never
     * propagates a failure into the call path.
     *
     * @param runtime the runtime of the call that just ended
     */
    private void emitFieldstatsEvent(CallRuntime runtime) {
        try {
            var stats = runtime.stats();
            var endReason = runtime.call().endReason().orElse(CallEndReason.UNKNOWN);
            var connectedDuration = stats.connectedDurationSeconds();
            var builder = new CallEventBuilder()
                    .callRandomId(stats.callId())
                    .callSide(stats.side())
                    .callResult(mapToResultType(endReason))
                    .videoEnabled(stats.videoEnabled())
                    .videoEnabledAtCallStart(stats.videoEnabled())
                    .callOfferElapsedT(stats.startedAt());
            if (connectedDuration > 0) {
                builder.durationTSs(Instant.ofEpochSecond(connectedDuration));
            }
            wamService.commit(builder.build());
        } catch (RuntimeException _) {
        }
    }

    /**
     * Maps a {@link CallEndReason} to the {@link CallResultType} reported in
     * the WAM Call event.
     *
     * @implNote This implementation reports
     * {@link CallEndReason#ACCEPTED_ELSEWHERE} as
     * {@link CallResultType#CONNECTED}: it arrives as a peer-side terminate,
     * but from the local user's perspective the call did connect, just not on
     * this device.
     *
     * @param reason the canonical end reason
     * @return the matching WAM result type
     */
    private static CallResultType mapToResultType(CallEndReason reason) {
        return switch (reason) {
            case HANGUP -> CallResultType.CONNECTED;
            case TIMEOUT -> CallResultType.MISSED;
            case REJECT_DO_NOT_DISTURB, REJECT_BLOCKED -> CallResultType.REJECTED_BY_USER;
            case MIC_PERMISSION_DENIED, CAMERA_PERMISSION_DENIED -> CallResultType.SETUP_ERROR;
            case ACCEPTED_ELSEWHERE -> CallResultType.CONNECTED;
            case UNKNOWN -> CallResultType.INVALID;
        };
    }

    /**
     * Sends a call-termination stanza to the peer, the wire-level leg of
     * {@link #terminate(String, CallEndReason)}.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}: the local user for outbound calls, the peer for
     *                inbound calls
     * @param callId  the call identifier
     * @param reason  the {@link CallEndReason} to communicate
     */
    private void sendTerminate(Jid peer, Jid creator, String callId, CallEndReason reason) {
        whatsapp.sendNodeWithNoResponse(CallStanza.terminate(peer, creator, callId, reason).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMute(Jid peer, Jid creator, String callId, boolean muted) {
        whatsapp.sendNodeWithNoResponse(CallStanza.mute(peer, creator, callId, muted).build());
        var runtime = find(callId);
        if (runtime != null) {
            runtime.call().setAudioMuted(muted);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled) {
        whatsapp.sendNodeWithNoResponse(CallStanza.videoState(peer, creator, callId, enabled).build());
        var runtime = find(callId);
        if (runtime != null) {
            runtime.call().setVideoMuted(!enabled);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation expresses the request as an enabling video-state stanza and
     * delegates to {@link #sendVideoState(Jid, Jid, String, boolean)} so a subclass overriding
     * that one method intercepts the whole video-state family.
     */
    @Override
    public void sendVideoUpgradeRequest(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendVideoUpgradeReject(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, false);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation routes each interaction to the plane the live WhatsApp voip engine
     * uses for it, which is not the same plane for every kind. The raise-hand, lower-hand, peer-mute, and
     * video-upgrade interactions are server-relayed {@code <call>} signaling stanzas (a
     * {@code <user_action action="raise_hand">} wrapper, a {@code <mute_v2 request-state>} payload, and a
     * {@code <video state="11">} announce respectively), sent via
     * {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient#sendNode(NodeBuilder) sendNode} so
     * the server assigns an {@code id} and acknowledges them, mirroring the accept path. A key-frame request
     * is an RTCP Picture Loss Indication on the media plane (not a stanza and not a DataChannel message),
     * dispatched through {@link CallRuntime#requestKeyframe()} to the attached media session. Only the
     * reaction rides the call's pre-negotiated DataChannel, as an
     * {@link com.github.auties00.cobalt.model.call.datachannel.AppDataMessage AppDataMessage} protobuf
     * message (DTLS-SCTP carries it; no SRTP wrap), and only once the media plane's DataChannel is open. The
     * voip wasm's protobuf-c descriptor table registers no message for raise-hand, peer-mute, key-frame, or
     * video-upgrade: those are deliberately not protobuf, so none can be a DataChannel AppData payload.
     */
    @Override
    public void sendInteraction(Jid peer, Jid creator, String callId, CallInteraction interaction) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(interaction, "interaction cannot be null");
        var runtime = activeCalls.get(callId);
        if (runtime == null) {
            return;
        }
        var relayTarget = runtime.call().isGroup() ? Jid.of(callId + "@call") : peer;
        switch (interaction) {
            case CallInteraction.RaiseHand _ ->
                    sendCallActionStanza(CallStanza.raiseHand(relayTarget, creator, callId, true), callId);
            case CallInteraction.LowerHand _ ->
                    sendCallActionStanza(CallStanza.raiseHand(relayTarget, creator, callId, false), callId);
            case CallInteraction.PeerMuteRequest request -> {
                var muteTarget = Jid.of(request.target());
                sendCallActionStanza(CallStanza.peerMute(muteTarget, creator, callId), callId);
            }
            case CallInteraction.VideoUpgradeRequest _ ->
                    sendCallActionStanza(CallStanza.videoState(relayTarget, creator, callId, true), callId);
            case CallInteraction.KeyFrameRequest _ -> runtime.requestKeyframe();
            case CallInteraction.Reaction _ -> sendDataPlaneInteraction(runtime, interaction);
        }
    }

    /**
     * Sends one server-relayed call-action signaling stanza on a virtual thread.
     *
     * <p>The stanza is dispatched through {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient#sendNode(NodeBuilder)}
     * so the server assigns its {@code id} and the call returns the acknowledgement; the send runs on
     * a virtual thread so a caller invoking a raise-hand or peer-mute interaction never blocks on the
     * round trip.
     *
     * @param stanza the {@code <call>} stanza builder
     * @param callId the call identifier, used only to name the worker thread
     */
    private void sendCallActionStanza(NodeBuilder stanza, String callId) {
        Thread.ofVirtual().name("call-action-" + callId).start(() -> {
            try {
                whatsapp.sendNode(stanza);
            } catch (RuntimeException ignored) {
                // best-effort: a dropped action ack does not invalidate the call
            }
        });
    }

    /**
     * Sends a data-plane interaction over the call's pre-negotiated DataChannel as an
     * {@link com.github.auties00.cobalt.model.call.datachannel.AppDataMessage AppDataMessage} batch.
     *
     * <p>The interaction is encoded by {@link CallInteractionEncoder} into the byte-exact-verified
     * {@link com.github.auties00.cobalt.model.call.datachannel.AppDataPayloads AppDataPayloads} batch bytes and written
     * directly to the channel; the DataChannel rides DTLS-encrypted SCTP, so no SRTP wrap is applied. Because the channel
     * is {@code maxRetransmits=0} unreliable, the same bytes are re-sent {@link #DATA_CHANNEL_RESEND_COUNT} times,
     * mirroring the live voip stack which repeats each app-data message. It is a no-op when the media plane's DataChannel
     * is not open.
     *
     * <p>Only the {@link CallInteraction.Reaction Reaction} interaction is routed here: it is the sole in-call action the
     * voip wasm carries as a DataChannel {@code AppDataMessage}. The signaling-stanza interactions (raise-hand, peer-mute,
     * video-upgrade) and the RTCP key-frame request are dispatched by {@link #sendInteraction} on their own planes and
     * never reach this method.
     *
     * @param runtime     the active call runtime
     * @param interaction the data-plane interaction to encode and send
     */
    private void sendDataPlaneInteraction(CallRuntime runtime, CallInteraction interaction) {
        var channel = runtime.transport().dataChannel().orElse(null);
        if (channel == null) {
            return;
        }
        var payload = CallInteractionEncoder.encode(interaction, runtime.transport().interactionStreamState());
        for (var attempt = 0; attempt < DATA_CHANNEL_RESEND_COUNT; attempt++) {
            try {
                channel.send(payload);
            } catch (RuntimeException _) {
                // The channel may have closed between the open check and the send, or a later resend may race a
                // teardown; an unreliable app-data message is best-effort, so a failed send must not break the call.
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation decrypts the Signal envelope through {@link MessageService#processCall}
     * and parses the plaintext as {@link E2eRekeyPayload}. The parsed bundle is then handed to
     * {@link CallRuntime#applyRekey(E2eRekeyPayload)}, which both caches it for inspection and
     * delegates to the attached media-plane session so the new per-domain master keys land on the
     * shared {@link com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint SrtpEndpoint} via
     * {@link com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint#rotateMasterKey(byte[]) rotateMasterKey}.
     */
    @Override
    public void onEncRekey(String callId, Jid senderJid, MessageEncryptionType encType, byte[] ciphertext) {
        var runtime = activeCalls.get(callId);
        if (runtime == null) {
            return;
        }
        try {
            var plaintext = messageService.processCall(senderJid, encType, ciphertext);
            var rekey = E2eRekeyPayloadSpec.decode(plaintext);
            runtime.applyRekey(rekey);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "enc_rekey decode failed for call " + callId + ": " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallEndedListener typed) {
                Thread.startVirtualThread(() -> typed.onCallEnded(whatsapp, callId, fromJid, parsed));
            }
        }
    }

    /**
     * Bundles the pre-acceptance credentials of an inbound offer.
     *
     * <p>Either field may be {@code null}: a relay-less offer (native desktop caller) carries only the
     * call key, and an offer whose call-key {@code <enc>} could not be decrypted carries only the relay.
     *
     * @param relay   the relay block parsed from the inbound offer, or {@code null}
     * @param callKey the 32-byte call key decrypted from the offer's self-device {@code <enc>}, or
     *                {@code null}
     */
    private record PendingOffer(CallRelay relay, byte[] callKey) {
    }
}
