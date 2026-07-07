package com.github.auties00.cobalt.calls2;

import com.github.auties00.cobalt.calls2.stream.AudioInput;
import com.github.auties00.cobalt.calls2.stream.AudioOutput;
import com.github.auties00.cobalt.calls2.stream.BufferedVideoInput;
import com.github.auties00.cobalt.calls2.stream.BufferedVideoOutput;
import com.github.auties00.cobalt.calls2.stream.VideoInput;
import com.github.auties00.cobalt.calls2.stream.VideoOutput;
import com.github.auties00.cobalt.calls2.core.CallEvent;
import com.github.auties00.cobalt.calls2.core.Calls2CallManager;
import com.github.auties00.cobalt.calls2.core.Calls2CallResult;
import com.github.auties00.cobalt.calls2.core.Calls2EngineAssembler;
import com.github.auties00.cobalt.calls2.core.Calls2LifecycleController;
import com.github.auties00.cobalt.calls2.core.Calls2MediaStreams;
import com.github.auties00.cobalt.calls2.core.LiveCallEventBus;
import com.github.auties00.cobalt.calls2.signaling.CallAckOutcome;
import com.github.auties00.cobalt.calls2.signaling.CallMessage;
import com.github.auties00.cobalt.calls2.signaling.OfferNoticeStanza;
import com.github.auties00.cobalt.calls2.signaling.OfferStanza;
import com.github.auties00.cobalt.calls2.signaling.TerminateStanza;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedCallEndedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallOfferNoticeListener;
import com.github.auties00.cobalt.util.ScheduledTask;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.model.call.CallLink;
import com.github.auties00.cobalt.model.call.CallLinkMedia;
import com.github.auties00.cobalt.model.call.CallState;
import com.github.auties00.cobalt.model.call.IncomingCall;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.CallEventBuilder;
import com.github.auties00.cobalt.wam.event.JoinableCallEventBuilder;
import com.github.auties00.cobalt.wam.event.PreCallUserJourneyChatThreadEventBuilder;
import com.github.auties00.cobalt.wam.threadlogging.ThreadLoggingActivity;
import com.github.auties00.cobalt.wam.type.CallFromUi;
import com.github.auties00.cobalt.wam.type.CallNetworkMedium;
import com.github.auties00.cobalt.wam.type.CallResultType;
import com.github.auties00.cobalt.wam.type.CallSide;
import com.github.auties00.cobalt.wam.type.CallSizeType;
import com.github.auties00.cobalt.wam.type.CallTransportType;
import com.github.auties00.cobalt.wam.type.PreCallActionType;
import com.github.auties00.cobalt.wam.type.SubSurface;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Live implementation of {@link Calls2Service} that coordinates one client's call activity over the
 * wa-voip engine.
 *
 * <p>This service is the per-client owner of the call subsystem: there is exactly one instance per
 * {@link LinkedWhatsAppClient}, constructed eagerly and held as a private final field, before the
 * stanza-stream service that registers the inbound signaling receiver. It owns the registry of in-flight
 * {@link Calls2Runtime} sessions, and it owns the two host-boundary engine pieces the public surface
 * drives: the {@link LiveCallEventBus} that fans host-facing events out to the registered listeners, and
 * the {@link Calls2LifecycleController} the public call methods delegate to. The at-most-two engine call
 * contexts are held by the engine's own {@link Calls2CallManager}, assembled into the controller rather
 * than owned here. The client's call-control methods are thin delegators onto this service, and the call
 * signaling receivers forward every decoded inbound action to it.
 *
 * <p>Where the engine controller runs the call's signaling and media state machine, this service owns the
 * host-API translation around it: it builds the public {@link Call} view, registers the
 * {@link Calls2Runtime} that owns the application media streams and the telemetry accumulator before
 * delegating the engine work, emits a pre-call user-journey funnel event as a call is placed, drains a
 * call's telemetry into a WAM Call event when it unregisters, and fans the call-ended event out to
 * listeners. A placed or accepted call sits in {@link CallState#RINGING}
 * or {@link CallState#CONNECTING} until the engine wires its media plane and is terminated by either a
 * local {@link #terminate(String, CallEndReason)} or a peer termination.
 *
 * @implNote This implementation mirrors the ownership and constructor of the legacy {@code call.LiveCallService}
 * (constructor {@code (LinkedWhatsAppClient, WamService, MessageService)}; private final {@code whatsapp},
 * {@code activeCalls}, {@code wamService}, {@code messageService}), so the client construction site swaps
 * one service for the other with no change beyond the type. The host-boundary engine pieces this service
 * owns map onto the native engine's host boundary: the event bus replaces the
 * {@code wa_system_send_platform_event} platform event queue with direct per-listener virtual-thread
 * fan-out, and the controller is the {@code wa_call_*} action surface. The native {@code call_manager}
 * singleton is the engine's {@link Calls2CallManager}, assembled into the controller rather than held by
 * this service. The listener fan-out, the registry, and the inline end-of-call WAM emission are pure Java
 * with no FFM.
 */
public final class LiveCalls2Service implements Calls2Service {
    /**
     * Logs call-setup diagnostics and isolated telemetry or listener-dispatch failures.
     */
    private static final System.Logger LOGGER = System.getLogger(LiveCalls2Service.class.getName());

    /**
     * The window within which an {@code <offer_notice>} is still acted on; a notice whose original offer
     * is older than this is dropped, and a recorded notice is purged from the call history after the same
     * window elapses.
     *
     * @implNote
     * This implementation matches the {@code c = 45} second constant WhatsApp Web's
     * {@code WAWebIncomingOfferNoticeVoipHandlerAction} uses both as the staleness threshold and as the
     * delay of the {@code setTimeout} that removes the notice from the call collection.
     */
    private static final Duration OFFER_NOTICE_STALENESS = Duration.ofSeconds(45);

    /**
     * Holds the owning client, used to resolve the local self {@link Jid}, resolve a peer's device list,
     * read the call store, and reach the listener registry for the call-ended fan-out.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Tracks live call runtimes keyed by their unique call identifier.
     *
     * <p>An entry is added on
     * {@link #placeCall(Jid, AudioOutput, AudioInput, VideoOutput, VideoInput)} or
     * {@link #accept(IncomingCall, AudioOutput, AudioInput, VideoOutput, VideoInput)}
     * and removed by {@link #unregister(String)} when the call reaches {@link CallState#ENDED}. This is the
     * host's view of the at-most-two engine call contexts the {@link Calls2CallManager} holds.
     */
    private final ConcurrentHashMap<String, Calls2Runtime> activeCalls = new ConcurrentHashMap<>();

    /**
     * Holds the WAM service used to commit the per-call telemetry event, or {@code null} when telemetry is
     * disabled for this client.
     */
    private final WamService wamService;

    /**
     * Holds the {@link MessageService} that owns the per-device fanout encryption and addressing-mode
     * resolution for outbound call offers and the Signal decryption for inbound key envelopes.
     */
    private final MessageService messageService;

    /**
     * Fans host-facing call events out to the registered listeners on their own virtual threads.
     */
    private final LiveCallEventBus eventBus;

    /**
     * Drives a call through its phases over the engine units, or {@code null} when this service is built
     * without an engine.
     *
     * <p>The lifecycle controller is the {@code wa_call_*} action surface the public call methods delegate
     * to and the sink the inbound seam forwards into. The production client always injects an assembled
     * controller through the full-dependency constructor; the controller-less constructors exist only for
     * the call-API and inbound-routing unit tests that exercise the host translation without a live engine.
     */
    private final Calls2LifecycleController lifecycleController;

    /**
     * Identifies the WAM app session this service runs under, stamped on every pre-call user-journey event.
     *
     * <p>WhatsApp tags each pre-call funnel beacon with the app session identifier of the launch it was
     * emitted in so a whole journey correlates back to one session. This is a stable random identifier
     * minted once per service instance, matching WhatsApp's per-app-launch session id: there is exactly one
     * calls2 service per {@link LinkedWhatsAppClient} connection, so one identifier per instance is the
     * connection's app session.
     */
    private final String appSessionId = randomSessionId();

    /**
     * Constructs a service bound to the given client, WAM service, and {@link MessageService}, building a
     * fresh event bus.
     *
     * <p>A fresh {@link LiveCallEventBus} is built for this service since none is supplied. The
     * {@link Calls2LifecycleController} is left unset by this constructor and is supplied through
     * {@link #LiveCalls2Service(LinkedWhatsAppClient, WamService, MessageService, Calls2LifecycleController)}
     * once its collaborating engine units are assembled.
     *
     * @param whatsapp       the owning client
     * @param wamService     the WAM telemetry service used for end-of-call events, or {@code null} when
     *                       telemetry is disabled for this client
     * @param messageService the {@link MessageService} used to build, encrypt, and ship the outbound offer
     *                       per peer and to decrypt the inbound key envelope
     * @throws NullPointerException if {@code whatsapp} or {@code messageService} is {@code null}
     */
    public LiveCalls2Service(LinkedWhatsAppClient whatsapp, WamService wamService,
                             MessageService messageService) {
        this(whatsapp, wamService, messageService, null);
    }

    /**
     * Constructs a service bound to the given client, WAM service, {@link MessageService}, and an
     * assembled {@link Calls2LifecycleController}, building a fresh event bus.
     *
     * <p>This constructor builds a fresh {@link LiveCallEventBus} for the service. It is the seam the
     * call-API and inbound-routing unit tests build the service through, where the controller carries no
     * in-call control units and the bus is therefore not shared with an engine. The production client
     * instead uses
     * {@link #LiveCalls2Service(LinkedWhatsAppClient, WamService, MessageService, Calls2LifecycleController, LiveCallEventBus)}
     * so the engine's control units and this service publish onto one shared bus.
     *
     * @param whatsapp            the owning client
     * @param wamService          the WAM telemetry service, or {@code null} when telemetry is disabled
     * @param messageService      the {@link MessageService} used for offer encryption and key decryption
     * @param lifecycleController the assembled engine lifecycle controller, or {@code null} when this service
     *                            is built without an engine
     * @throws NullPointerException if {@code whatsapp} or {@code messageService} is {@code null}
     */
    public LiveCalls2Service(LinkedWhatsAppClient whatsapp, WamService wamService, MessageService messageService,
                             Calls2LifecycleController lifecycleController) {
        this(whatsapp, wamService, messageService, lifecycleController, new LiveCallEventBus(whatsapp));
    }

    /**
     * Constructs a service over an assembled {@link Calls2LifecycleController} and a shared
     * {@link LiveCallEventBus}.
     *
     * <p>This is the full-dependency constructor the production client uses: it injects the controller the
     * public call methods delegate to and the one event bus shared with the engine, so the host-facing
     * events the in-call control units emit through the engine and the call-ended events this service emits
     * are gated and fanned out by the same bus. The client constructs the bus first and passes the same
     * instance both here and into {@link Calls2EngineAssembler#assemble} so the engine binds it onto the
     * controller.
     *
     * <p>When a controller is supplied, this constructor binds {@link #unregister(String)} onto the
     * controller's teardown sink ({@link Calls2LifecycleController#bindTeardownSink}), so the controller
     * drives the service-level teardown and end-of-call WAM drain once at the ENDED transition of every call,
     * regardless of which side ended it, and binds {@link #markCallConnected(String)} onto the controller's
     * connected sink ({@link Calls2LifecycleController#bindConnectedSink}), so the call's connected instant is
     * stamped once its media plane goes active. Binding from here, where the service and its controller are
     * both in hand, keeps the service the owner of the call back into itself, mirroring how the engine
     * assembler binds the result and call-log sinks; a controller-less service binds nothing.
     *
     * @param whatsapp            the owning client
     * @param wamService          the WAM telemetry service, or {@code null} when telemetry is disabled
     * @param messageService      the {@link MessageService} used for offer encryption and key decryption
     * @param lifecycleController the assembled engine lifecycle controller, or {@code null} when this service
     *                            is built without an engine
     * @param eventBus            the shared host event bus that gates and fans out the host-facing call
     *                            events
     * @throws NullPointerException if {@code whatsapp}, {@code messageService}, or {@code eventBus} is
     *                              {@code null}
     */
    public LiveCalls2Service(LinkedWhatsAppClient whatsapp, WamService wamService, MessageService messageService,
                             Calls2LifecycleController lifecycleController, LiveCallEventBus eventBus) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.wamService = wamService;
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.lifecycleController = lifecycleController;
        if (lifecycleController != null) {
            lifecycleController.bindTeardownSink(this::unregister);
            lifecycleController.bindConnectedSink(this::markCallConnected);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call placeCall(Jid peer, AudioOutput audioOut, AudioInput audioIn,
                          VideoOutput videoOut, VideoInput videoIn) {
        Objects.requireNonNull(peer, "peer cannot be null");
        Objects.requireNonNull(audioOut, "audioOut cannot be null");
        Objects.requireNonNull(audioIn, "audioIn cannot be null");
        if (peer.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException(
                    "placeCall is for one-to-one calls; use placeGroupCall for a group or community JID: "
                            + peer);
        }
        var self = requireSelfJid();
        var video = videoOut != null;
        // WhatsApp addresses 1:1 call signaling by LID: resolve the peer to its LID and fan out over its
        // LID device JIDs. A peer with no resolvable LID aborts here rather than sending a phone-number
        // offer the server rejects with error="439".
        var addressing = messageService.resolveCallPeerAddressing(peer);
        var streams = mediaStreams(audioOut, audioIn, videoOut, videoIn);
        var call = engine().startCall(self, addressing.peer(), addressing.peerDevices(), video, streams);
        emitPreCallJourney(call.callId(), video ? PreCallActionType.CLICK_VIDEO_CALL
                        : PreCallActionType.CLICK_AUDIO_CALL, CallSizeType.ONE_TO_ONE, video,
                SubSurface.CHAT_HEADER, 2L, null, null);
        if (notifyIfEndedDuringPlacement(call, addressing.peer())) {
            return call;
        }
        registerRuntime(call, CallSide.CALLER, video, audioOut, audioIn, videoOut, videoIn);
        return call;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call placeGroupCall(Set<Jid> peers, Jid groupJid, AudioOutput audioOut,
                               AudioInput audioIn, VideoOutput videoOut, VideoInput videoIn) {
        Objects.requireNonNull(peers, "peers cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(audioOut, "audioOut cannot be null");
        Objects.requireNonNull(audioIn, "audioIn cannot be null");
        if (peers.isEmpty()) {
            throw new IllegalArgumentException("peers cannot be empty");
        }
        var self = requireSelfJid();
        var video = videoOut != null;
        var streams = mediaStreams(audioOut, audioIn, videoOut, videoIn);
        var call = engine().startGroupCall(self, peers, groupJid, video, streams);
        var callSize = (long) (peers.size() + 1);
        emitPreCallJourney(call.callId(), PreCallActionType.CLICK_START_CALL, CallSizeType.LGC, video,
                SubSurface.CHAT_HEADER, callSize, callSize, false);
        if (notifyIfEndedDuringPlacement(call, groupJid)) {
            return call;
        }
        registerRuntime(call, CallSide.CALLER, video, audioOut, audioIn, videoOut, videoIn);
        return call;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation resolves the local self JID, derives the join-with-video flag from
     * whether a video source was supplied, bundles the streams, and delegates the link query-and-join
     * handshake and the answer to {@link Calls2LifecycleController#joinCallLink(Jid, String, CallLinkMedia,
     * boolean, Calls2MediaStreams)}, which applies the call-link feature gate. The runtime is registered as a
     * caller-side telemetry session, matching a placed group call.
     */
    @Override
    public Call joinCallLink(String token, CallLinkMedia media, AudioOutput audioOut, AudioInput audioIn,
                             VideoOutput videoOut, VideoInput videoIn) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(media, "media cannot be null");
        Objects.requireNonNull(audioOut, "audioOut cannot be null");
        Objects.requireNonNull(audioIn, "audioIn cannot be null");
        var self = requireSelfJid();
        var video = videoOut != null;
        var streams = mediaStreams(audioOut, audioIn, videoOut, videoIn);
        var call = engine().joinCallLink(self, token, media, video, streams);
        emitPreCallJourney(call.callId(), PreCallActionType.CLICK_CALL_LINK, CallSizeType.CALL_LINK, video,
                null, null, null, null);
        registerRuntime(call, CallSide.CALLER, video, audioOut, audioIn, videoOut, videoIn);
        return call;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call accept(IncomingCall offer, AudioOutput audioOut, AudioInput audioIn,
                       VideoOutput videoOut, VideoInput videoIn) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(audioOut, "audioOut cannot be null");
        Objects.requireNonNull(audioIn, "audioIn cannot be null");
        var video = videoOut != null;
        var streams = mediaStreams(audioOut, audioIn, videoOut, videoIn);
        var call = engine().acceptCall(offer.callId(), video, streams);
        registerRuntime(call, CallSide.CALLEE, video, audioOut, audioIn, videoOut, videoIn);
        return call;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reject(IncomingCall offer, CallEndReason reason) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        engine().rejectCall(offer.callId(), offer.peer(), reason);
        whatsapp.store().chatStore().removeCall(offer.callId());
        notifyEnded(offer.callId(), offer.peer(), reason.wireValue());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation ends the call through the engine
     * ({@link Calls2LifecycleController#endCall(String, CallEndReason)}, which sends the terminate and runs
     * the engine teardown) and then fans the {@code onCallEnded} event out through
     * {@link #notifyEnded(String, Jid, String)} with the call creator and wire reason. The host end event is
     * fired on the local hangup path here to match {@link #reject(IncomingCall, CallEndReason)} and the
     * inbound-terminate path, so the application observes the call ending regardless of who hung up. The
     * engine teardown drives the registry removal, the runtime stream and media-session shutdown through
     * {@link Calls2Runtime#end(CallEndReason)}, and the end-of-call WAM drain through the teardown sink bound
     * to {@link #unregister(String)}, so this method only adds the host listener fan-out the slim engine event
     * sink cannot carry.
     */
    @Override
    public void terminate(String callId, CallEndReason reason) {
        Objects.requireNonNull(reason, "reason cannot be null");
        var runtime = find(callId);
        if (runtime == null) {
            return;
        }
        var creator = runtime.call().creator();
        engine().endCall(callId, reason);
        // Fan the local hangup out to the application's onCallEnded listeners, matching reject() and the
        // inbound-terminate path; the engine teardown fires the registry/WAM/runtime teardown sink but not the
        // host end event, so the local end path surfaces it here for symmetry with every peer-driven end.
        notifyEnded(callId, creator, reason.wireValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preaccept(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        if (!callExists(callId)) {
            return;
        }
        engine().preaccept(callId);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation routes the membership update through the engine's outbound-group-call
     * unit ({@link Calls2LifecycleController#sendGroupParticipants(String, Jid, Jid, List, boolean)}), which
     * builds the {@code <group_update>} with a {@code <group_info>} roster of the affected participants,
     * ships it fire-and-forget to the group target, and reconciles the call's {@code CallMembership} against
     * the add or remove so the unit's per-peer offer sweep tracks a newly added participant. A call that is
     * not tracked, or one that is not a group call, is a no-op in the unit.
     */
    @Override
    public void sendGroupParticipants(String callId, Jid target, Jid creator, List<Jid> participants,
                                      boolean added) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }
        engine().sendGroupParticipants(callId, target, creator, participants, added);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation routes the camera turn-on through
     * {@link Calls2LifecycleController#startLocalVideo(String)}, which announces video-on to the peer through
     * the call's {@code VideoStateController} and starts the outbound camera-capture and encode path on the
     * call's media plane. A session brought up audio-only raises its local video track through the engine's
     * mid-call audio-to-video upgrade seam, which builds the video pipeline and starts both loops, so the
     * camera-track start is effected on such a call rather than being announce-only.
     */
    @Override
    public void startLocalVideo(String callId) {
        var runtime = find(callId);
        if (runtime == null) {
            return;
        }
        engine().startLocalVideo(callId);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation drives the engine's
     * {@link Calls2LifecycleController#startScreenShare(String)}, which sends the {@code <screen_share>}
     * action at the negotiated protocol version through the call's
     * {@link com.github.auties00.cobalt.calls2.core.control.ScreenShareController}. That announce is the
     * complete V2 single-stream half (the screen content port-swaps onto the existing camera video stream).
     * On a V3-negotiated call the dual-stream auxiliary screen media track is not started here: it is blocked
     * on the unwired per-participant subscription/StreamDescriptor publish-subscribe layer (SPEC 14.3), a
     * screenshare SSRC derivation, and an aux-stream media seam, all detailed in the TODO in the method body.
     */
    @Override
    public void startScreenShare(String callId) {
        var runtime = find(callId);
        if (runtime == null) {
            return;
        }
        // TODO: start the auxiliary screen media stream for the V3 dual-stream path. The
        //  ScreenShareController.start() announce the engine drives below is the complete signaling half (the
        //  <screen_share> action, type 38, screenshare_state 1, with the negotiated version); V2 needs only
        //  that announce because the screen content port-swaps onto the existing camera video stream
        //  (handle_screen_share/start_peer_screen_share_and_notify fn11132 param3<3: it only sets the
        //  screenshare-active flag and reuses the camera stream, plus screen_share_swap_render_port_for_v2_compat
        //  fn6342 on the render side). The V3 dual-stream path (fn11132 param3>=3 ->
        //  call_create_auxiliary_screen_share_stream fn11247 -> create_and_connect_screen_share_video_stream
        //  fn6337, building a second "vid_aux_*" pjmedia video stream) is blocked on layers that are unwired
        //  across calls2, NOT on this seam, so an aux stream cannot be started faithfully here yet:
        //   1. The per-participant subscription ON-WIRE PUBLISH (SPEC 14.3) is not wired into the production
        //      media plane. fn11132's V3 branch builds the stream descriptor (fn10991) and publishes it BEFORE
        //      allocating the aux stream, and a receiver only decodes it after it subscribes
        //      (build_and_send_stream_subscription_request fn6406, SenderSubscriptions in STUN attr 0x4025,
        //      RxSubscriptions in 0x4021): without that publish the relay/SFU has no route for the new
        //      screenshare SSRCs and no peer ever receives the stream. LiveMediaPlane.assemble now constructs a
        //      LiveSubscriptionPublisher, holds the CallMembership, and builds the StreamDescriptors from the
        //      call's StreamLayout, but the LiveSubscriptionPublisher is not yet wired to the transport's
        //      0x0003-envelope send seam (LiveRelayTransport.sendSubscriptionEnvelope): the relay transport
        //      runs the ICE/STUN binding handshake and can ship the subscription envelope, but the production
        //      media plane does not yet route the built StreamDescriptors through it, so the camera mid-call
        //      upgrade still sends no subscription update (LiveMediaSession.upgradeToVideo); the descriptor
        //      publish is unwired for every stream, not just this one.
        //   2. StreamLayout carries no screenshare SSRC field and LiveSubscriptionPublisher.buildStreamDescriptors
        //      emits no SCREEN_SHARE_VIDEO_STREAM0/1 descriptor, although the model layer constants exist
        //      (StreamDescriptor.StreamLayer indices 8 and 9). The layout/publisher must learn the two
        //      screenshare layers before they can be published.
        //   3. CallSecureSsrcGenerator derives no screenshare SSRC. fn10901's second video loop allocates the
        //      screenshare family at param2+0x1e (vs the camera family at +0x18) by calling fn10902 with the
        //      screenshare selector (param6=1, vs camera param6=0); fn10902 derives the SSRC over the SAME tx
        //      media-type code (2/3/5) the camera uses but over a screenshare-distinguished identifier (its
        //      type-name label is "screen share", 0x76ec0, vs the camera "video"), and for a screenshare tx
        //      stream it requires the constructed identifier to contain a screenshare marker substring,
        //      erroring at fn10902 0x2d0 (fn9184 substring check against 0x2b4a9) when it does not. The exact
        //      screenshare identifier format is only partially recovered (the camera path's "%.*s_%d"
        //      stream-index format, 0x92342, plus an unconfirmed screenshare marker) and is NOT verified
        //      against a live screenshare call: the byte-exact live verification in CallSecureSsrcGenerator
        //      covers audio/video/appdata/hbh-fec/imu only, so deriving the screenshare identifier from the
        //      partial static shape would be an unverified guess.
        //   4. Calls2MediaPlane.Session exposes no aux-stream seam (only startLocalVideo()/requestKeyFrame()),
        //      so there is nothing here for the controller to drive even once the layers above land; the second
        //      video stream object (a second VideoPipeline + OutboundVideoRtpPacketizer bound to the live
        //      transport on the screenshare SSRC) would be built in the media plane mirroring upgradeToVideo,
        //      and reached through orchestrated.mediaSession() exactly as startLocalVideo() is.
        //  Building a second video stream on a stand-in SSRC with no subscription would make every screenshare
        //  frame Cobalt sends unroutable by real peers, a divergent half-build; the faithful prerequisite is the
        //  production subscription/StreamDescriptor wiring of layers 1-2, then the screenshare SSRC derivation
        //  of layer 3 verified live, then the aux-stream media seam of layer 4.
        engine().startScreenShare(callId);
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
        engine().stopScreenShare(callId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMute(Jid peer, Jid creator, String callId, boolean muted) {
        var runtime = find(callId);
        if (runtime != null) {
            runtime.call().setAudioMuted(muted);
        }
        engine().setMuted(callId, muted);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled) {
        var runtime = find(callId);
        if (runtime != null) {
            runtime.call().setVideoMuted(!enabled);
        }
        engine().setVideoEnabled(callId, enabled);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation expresses the request as an enabling video-state announce and delegates
     * to {@link #sendVideoState(Jid, Jid, String, boolean)} so the whole video-state family funnels through
     * one method.
     */
    @Override
    public void sendVideoUpgradeRequest(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, true);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation expresses the rejection as a disabling video-state announce and
     * delegates to {@link #sendVideoState(Jid, Jid, String, boolean)}.
     */
    @Override
    public void sendVideoUpgradeReject(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, false);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation routes the interaction onto its real engine control plane through
     * {@link Calls2LifecycleController#sendInteraction(String, CallInteraction)}, which dispatches a
     * raise-hand, peer-mute, or video-upgrade onto the matching signaling-plane control unit, routes a
     * reaction onto the call's {@code ReactionController} over the media plane's application-data side-channel,
     * and arms the outbound video encoder for a fresh key frame through the call's media session. Cobalt
     * deliberately does NOT re-encode the interaction through a single signaling-string facade.
     */
    @Override
    public void sendInteraction(Jid peer, Jid creator, String callId, CallInteraction interaction) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(interaction, "interaction cannot be null");
        var runtime = find(callId);
        if (runtime == null) {
            return;
        }
        engine().sendInteraction(callId, interaction);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation delegates to
     * {@link Calls2LifecycleController#createCallLink(CallLinkMedia, boolean)}, which runs the blocking
     * {@code link_create} request-reply against the {@code call} service and applies the call-link feature
     * gate; the minted {@link CallLink} is returned directly rather than surfaced as a listener event.
     */
    @Override
    public CallLink createCallLink(CallLinkMedia media, boolean waitingRoomEnabled) {
        Objects.requireNonNull(media, "media cannot be null");
        return engine().createCallLink(media, waitingRoomEnabled);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation delegates to
     * {@link Calls2LifecycleController#setWaitingRoomEnabled(String, boolean)}, which drives the call's
     * waiting-room controller when the call is a tracked call-link call and is a no-op otherwise.
     */
    @Override
    public void setWaitingRoomEnabled(String callId, boolean enabled) {
        Objects.requireNonNull(callId, "callId cannot be null");
        engine().setWaitingRoomEnabled(callId, enabled);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation delegates to
     * {@link Calls2LifecycleController#admitWaitingRoomParticipant(String, Jid)}, which drives the call's
     * waiting-room controller when the call is a tracked call-link call and is a no-op otherwise.
     */
    @Override
    public void admitWaitingRoomParticipant(String callId, Jid userJid) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(userJid, "userJid cannot be null");
        engine().admitWaitingRoomParticipant(callId, userJid);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation delegates to
     * {@link Calls2LifecycleController#admitAllWaitingRoomParticipants(String)}, which drives the call's
     * waiting-room controller when the call is a tracked call-link call and is a no-op otherwise.
     */
    @Override
    public void admitAllWaitingRoomParticipants(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        engine().admitAllWaitingRoomParticipants(callId);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation delegates to
     * {@link Calls2LifecycleController#denyWaitingRoomParticipant(String, Jid)}, which drives the call's
     * waiting-room controller when the call is a tracked call-link call and is a no-op otherwise.
     */
    @Override
    public void denyWaitingRoomParticipant(String callId, Jid userJid) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(userJid, "userJid cannot be null");
        engine().denyWaitingRoomParticipant(callId, userJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Calls2Runtime find(String callId) {
        return callId == null ? null : activeCalls.get(callId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean callExists(String callId) {
        return callId != null && activeCalls.containsKey(callId);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation routes an {@link OfferStanza} through
     * {@link Calls2LifecycleController#handleIncomingOffer(OfferStanza, Jid)} so the controller rings the
     * new call and the returned {@link IncomingCall} can be fanned out as the {@code onCall} listener event
     * through the {@link LiveCallEventBus}; every other decoded action goes through
     * {@link Calls2LifecycleController#handleIncomingMessage(CallMessage, Jid)} for its per-type dispatch.
     * The offer path is split out here because the slim opaque engine event sink cannot carry the
     * {@link IncomingCall} the {@code onCall} callback needs, so the service surfaces it from the
     * controller's return value.
     */
    @Override
    public void handleInbound(CallMessage message, Jid senderJid) {
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        if (message instanceof OfferStanza offer) {
            // Learn the caller's PN<->LID pairing from the server-stamped caller_pn so onCallEnded and any
            // later signaling can present the caller's phone number rather than its LID.
            offer.callerPnValue().ifPresent(callerPn ->
                    whatsapp.store().contactStore().registerLidMapping(callerPn.toUserJid(),
                            offer.callCreator().toUserJid()));
            engine().handleIncomingOffer(offer, senderJid)
                    .ifPresent(incoming -> eventBus.emit(new CallEvent.IncomingOffer(incoming)));
            return;
        }
        engine().handleIncomingMessage(message, senderJid);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation drops a notice whose original offer is older than
     * {@link #OFFER_NOTICE_STALENESS}, mirroring WhatsApp Web's stale-offer guard. A fresh notice is
     * recorded in the call history as an {@link IncomingCall} flagged as an offline offer, its peer
     * resolved from the {@code call-creator} device JID and mapped back to its phone number when known, and
     * is fanned out to the {@code onCallOfferNotice} listeners; for a one-to-one notice the chat JID is the
     * peer, and a group notice carries no group JID because the wire form does not include one. The notice
     * is purged from the call history after {@link #OFFER_NOTICE_STALENESS} elapses, matching WhatsApp
     * Web's {@code setTimeout} cleanup.
     */
    @Override
    public void handleOfferNotice(OfferNoticeStanza notice) {
        Objects.requireNonNull(notice, "notice cannot be null");
        if (Duration.between(notice.offerTime(), Instant.now()).compareTo(OFFER_NOTICE_STALENESS) > 0) {
            LOGGER.log(System.Logger.Level.DEBUG, "Dropping stale offer_notice for call {0}", notice.callId());
            return;
        }
        var peer = toListenerJid(notice.callCreator().toUserJid());
        var incoming = new IncomingCall(notice.callId(), peer, peer, notice.offerTime(),
                notice.video(), notice.group(), null, true);
        whatsapp.store().chatStore().addCall(incoming);
        notifyOfferNotice(incoming);
        ScheduledTask.scheduleDelayed(OFFER_NOTICE_STALENESS,
                () -> whatsapp.store().chatStore().removeCall(notice.callId()));
    }

    /**
     * Fans an offline-offer notice out to every registered {@link LinkedCallOfferNoticeListener}.
     *
     * <p>Each listener is invoked on its own virtual thread, and a listener exception is logged and
     * swallowed rather than propagated, so one faulty listener neither stalls the signaling reader nor
     * blocks the fan-out to the other listeners.
     *
     * @param call the offline-offer descriptor to deliver
     */
    private void notifyOfferNotice(IncomingCall call) {
        // FIXME: this hand-rolls the per-listener virtual-thread fan-out that LiveCallEventBus.emit performs.
        //  Routing through eventBus.emit would need a new CallEvent variant for the offline offer-notice
        //  (LinkedCallOfferNoticeListener) plus a matching case in LiveCallEventBus.emit and a host-facing
        //  entry in its gate; no such event exists, so a faithful reroute is not possible without adding one.
        //  Left as a direct fan-out.
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallOfferNoticeListener typed) {
                Thread.startVirtualThread(() -> {
                    try {
                        typed.onCallOfferNotice(whatsapp, call);
                    } catch (RuntimeException exception) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "onCallOfferNotice listener threw " + exception.getClass().getSimpleName()
                                        + ": " + exception.getMessage());
                    }
                });
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation forwards the terminate to the engine for its teardown and then, only when
     * the engine reports the terminate was effected, fans the {@code onCallEnded} listener event out through
     * {@link #notifyEnded(String, Jid, String)} with the terminate's call creator and wire reason, so a
     * peer-driven or accepted-elsewhere call end reaches the application even though the opaque engine event
     * sink carries no typed end payload. The engine's
     * {@link Calls2LifecycleController#handleIncomingMessage(CallMessage, Jid)} returns whether the inbound
     * terminate tore the call down rather than being suppressed by the companion-device or
     * joinable-on-expired-offer guard (or dropped by the router as a duplicate); a suppressed or dropped
     * terminate fires no host end, matching the native {@code handle_terminate} (fn11492) which fires its
     * end-of-call host event only after the ignore guards pass.
     */
    @Override
    public void handleInboundTerminate(TerminateStanza terminate, Jid senderJid) {
        Objects.requireNonNull(terminate, "terminate cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var torn = engine().handleIncomingMessage(terminate, senderJid);
        if (torn) {
            notifyEnded(terminate.callId(), terminate.callCreator(), terminate.reasonWire());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation first stamps the accept-ack NACK result onto the tracked call's
     * telemetry accumulator (so the WAM Call event reports the engine's own result code, which survives the
     * controller's release of the engine context at teardown), then forwards the outcome to the engine's
     * {@link Calls2LifecycleController#handleIncomingAck(CallAckOutcome)}, which keys it to the answered call
     * and, on an accept NACK, ends the call with the accept-side setup failure; a success ack or an ack for
     * an untracked call is a no-op.
     */
    @Override
    public void handleInboundAck(CallAckOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome cannot be null");
        if (outcome.isNack()) {
            var runtime = activeCalls.get(outcome.id());
            if (runtime != null) {
                Calls2CallResult.fromAcceptAckError(outcome.error().getAsInt())
                        .ifPresent(result -> runtime.stats().result(result));
            }
        }
        engine().handleIncomingAck(outcome);
    }

    /**
     * Records the engine-resolved call result on a tracked call's telemetry accumulator.
     *
     * <p>The lifecycle controller reports a distinct engine result through its result sink for outcomes whose
     * terminal end reason is lossy (an offer NACK is {@link Calls2CallResult#SERVER_NACK}); this stamps it on
     * the call's {@link Calls2CallStats} so the WAM Call event reports the engine's own result code rather
     * than the end-reason projection. A result for an untracked call is ignored.
     *
     * @param callId the call identifier
     * @param result the engine-resolved call result
     */
    public void recordCallResult(String callId, Calls2CallResult result) {
        var runtime = activeCalls.get(callId);
        if (runtime != null) {
            runtime.stats().result(result);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation is driven once per call by the engine lifecycle controller's teardown
     * sink, which the service binds to this method through
     * {@link Calls2LifecycleController#bindTeardownSink(java.util.function.Consumer)} in its constructor. The
     * controller fires it from its single ENDED transition for every end path (local hangup, peer terminate,
     * reject, timeout, offer NACK, or setup failure), so the registry removal, the runtime stream and
     * media-session shutdown through {@link Calls2Runtime#end(CallEndReason)}, and the end-of-call WAM Call
     * and JoinableCall events fire exactly once for every ended call regardless of who ended it. The runtime
     * teardown runs here as the sole owner of {@link Calls2Runtime#end(CallEndReason)}, using the terminal end
     * reason the controller stamped on the call view before firing this sink; an unknown {@code callId}
     * removes nothing and ends nothing, and a call with no telemetry accumulator (telemetry disabled) still
     * has its runtime shut down but skips the WAM commit.
     */
    @Override
    public void unregister(String callId) {
        var runtime = activeCalls.remove(callId);
        whatsapp.store().chatStore().removeCall(callId);
        if (runtime == null) {
            return;
        }
        // Own the runtime teardown here: this sink is fired once at the ENDED transition for every end path
        // (local hangup, peer terminate, reject, timeout, offer NACK, setup failure), so shutting the four
        // streams and the media session from here unblocks the application's blocked reads and writes and
        // reaches CallState#ENDED regardless of who ended the call. The controller has already stamped the
        // terminal end reason on the shared call view before firing this sink.
        runtime.end(runtime.call().endReason().orElse(CallEndReason.UNKNOWN));
        if (wamService == null) {
            return;
        }
        emitFieldStatsEvent(runtime);
    }

    /**
     * Stamps a tracked call's telemetry accumulator with its connected instant when its media plane first
     * goes active.
     *
     * <p>Bound onto the engine lifecycle controller's connected sink through
     * {@link Calls2LifecycleController#bindConnectedSink(java.util.function.Consumer)} in this service's
     * constructor, this is driven once as a call first reaches {@link CallState#ACTIVE}, symmetric with the
     * ended instant the runtime stamps at teardown, so the end-of-call WAM Call event reports a non-zero
     * connected duration rather than zero. A signal for an untracked call stamps nothing.
     *
     * @param callId the identifier of the call whose media plane became active
     */
    private void markCallConnected(String callId) {
        var runtime = activeCalls.get(callId);
        if (runtime != null) {
            runtime.stats().markConnected();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        // Call signaling is LID-addressed on the wire; applications expect the peer's phone number, so
        // map the LID back to its PN through the learned mapping before fanning the event out.
        var from = toListenerJid(fromJid);
        // FIXME: this hand-rolls the per-listener virtual-thread fan-out that LiveCallEventBus.emit already
        //  performs for a CallEvent.Ended, duplicating its dispatch. The faithful route is
        //  eventBus.emit(new CallEvent.Ended(callId, from, parsed)), but LiveCallEventBus gates every event
        //  through shouldEmit(CallEventType) and this method is called from local hangup, reject,
        //  placement-NACK, and inbound terminate; rerouting would subject those ends to the host-facing gate
        //  the hand-rolled path deliberately bypasses, which could suppress an onCallEnded the application
        //  currently always receives. Not verified WA-faithful, so left as a direct fan-out.
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallEndedListener typed) {
                Thread.startVirtualThread(() -> {
                    try {
                        typed.onCallEnded(whatsapp, callId, from, parsed);
                    } catch (RuntimeException exception) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "onCallEnded listener threw " + exception.getClass().getSimpleName()
                                        + ": " + exception.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Surfaces a call the engine ended synchronously during placement to the application, reporting
     * whether it did.
     *
     * <p>An outbound offer the server rejects (an offer NACK, e.g. server {@code error="439"}) is torn
     * down inside the engine placement before it returns, leaving the {@link Call} in
     * {@link CallState#ENDED}. This fans the {@code onCallEnded} event out with the recorded end reason so
     * the application learns the call never connected, rather than the placement path registering a dead
     * runtime and returning a call indistinguishable from a ringing one.
     *
     * @param call the call returned by the engine placement
     * @param peer the peer the offer addressed, surfaced as the ended-event JID
     * @return {@code true} when the call had already ended and the {@code onCallEnded} event was fanned out
     */
    private boolean notifyIfEndedDuringPlacement(Call call, Jid peer) {
        if (call.state() != CallState.ENDED) {
            return false;
        }
        var reason = call.endReason().orElse(CallEndReason.UNKNOWN);
        notifyEnded(call.callId(), peer, reason.wireValue());
        return true;
    }

    /**
     * Maps a possibly LID-addressed call peer JID back to the phone-number JID applications expect.
     *
     * <p>Call signaling is LID-addressed on the wire, but listeners are handed the peer's phone number so
     * it matches the JID applications hold for contacts and chats. A LID-server JID is resolved through
     * the learned PN-to-LID mapping; a JID that is already a phone number, has no known mapping, or is
     * {@code null} is returned unchanged.
     *
     * @param jid the JID surfaced by the call layer, or {@code null}
     * @return the phone-number JID when one is known, otherwise the input unchanged
     */
    private Jid toListenerJid(Jid jid) {
        if (jid == null || !jid.hasLidServer()) {
            return jid;
        }
        return whatsapp.store().contactStore().findPhoneByLid(jid.toUserJid()).orElse(jid);
    }

    /**
     * Returns the event bus that fans host-facing call events out to the registered listeners.
     *
     * @return the event bus
     */
    public LiveCallEventBus eventBus() {
        return eventBus;
    }

    /**
     * Builds a fresh {@link Calls2Runtime} for a call backed by the supplied media streams and registers it
     * in the active-call registry.
     *
     * <p>The runtime builds and owns its own telemetry accumulator with the supplied side and video flag.
     * An audio-only call carries idle buffered video streams the engine never drains or fills, so the
     * runtime always plumbs four streams.
     *
     * @param call     the public data view the runtime drives
     * @param side     which side initiated the call, for the telemetry accumulator
     * @param video    whether the call was placed or accepted with video enabled
     * @param audioOut the source the engine drains local audio from for transmission
     * @param audioIn  the sink the engine fills with received remote audio
     * @param videoOut the source the engine drains local video from for transmission, or {@code null} for
     *                 an audio-only call
     * @param videoIn  the sink the engine fills with received remote video, or {@code null} for an
     *                 audio-only call
     * @return the registered runtime
     */
    private Calls2Runtime registerRuntime(Call call, CallSide side, boolean video,
                                          AudioOutput audioOut, AudioInput audioIn,
                                          VideoOutput videoOut, VideoInput videoIn) {
        var runtimeVideoOut = videoOut != null ? videoOut : BufferedVideoOutput.buffered(640, 480);
        var runtimeVideoIn = videoIn != null ? videoIn : BufferedVideoInput.buffered();
        var stats = new Calls2CallStats(call.callId(), side, video, Instant.now());
        var runtime = new Calls2Runtime(call, stats, audioOut, audioIn, runtimeVideoOut, runtimeVideoIn);
        activeCalls.put(call.callId(), runtime);
        return runtime;
    }

    /**
     * Bundles the application capture sources and playback sinks for the engine media plane, mapped by media
     * flow rather than by public parameter name.
     *
     * <p>The public call API names its streams from the application's point of view: {@code audioOut} and
     * {@code videoOut} are the streams the application writes outbound media into, and {@code audioIn} and
     * {@code videoIn} are the streams it reads inbound media from. The media plane instead groups them by
     * the direction it drives them: the {@code out} streams are the capture sources it encodes and the
     * {@code in} streams are the playback sinks it delivers decoded media to. An audio-only call passes a
     * {@code null} video source and sink, so the engine starts no video pipeline; the idle buffered video
     * streams {@link #registerRuntime} plumbs onto the runtime for teardown are deliberately not handed to
     * the engine, since the engine drives only the streams the call actually carries.
     *
     * @param audioOut the local-audio capture source the engine encodes
     * @param audioIn  the remote-audio playback sink the engine renders decoded audio to
     * @param videoOut the local-video capture source the engine encodes, or {@code null} for an audio-only
     *                 call
     * @param videoIn  the remote-video playback sink the engine renders decoded video to, or {@code null}
     *                 for an audio-only call
     * @return the engine media-streams bundle
     */
    private static Calls2MediaStreams mediaStreams(AudioOutput audioOut, AudioInput audioIn,
                                                   VideoOutput videoOut, VideoInput videoIn) {
        return new Calls2MediaStreams(audioOut, audioIn, videoOut, videoIn);
    }

    /**
     * Returns the local self device JID in the LID addressing mode call signaling requires.
     *
     * <p>WhatsApp stamps {@code call-creator} with the local LID carrying the current device suffix, not
     * the phone number. This returns the account LID promoted to the phone-number JID's device, falling
     * back to the phone-number JID only for an account that has no LID assigned.
     *
     * @return the local self JID, LID-addressed when a LID is available
     * @throws IllegalStateException if the client is not logged in
     */
    private Jid requireSelfJid() {
        var accountStore = whatsapp.store().accountStore();
        var selfPn = accountStore.jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        return accountStore.lid()
                .map(Jid::toUserJid)
                .map(lid -> selfPn.hasDevice() ? lid.withDevice(selfPn.device()) : lid)
                .orElse(selfPn);
    }

    /**
     * Returns the engine lifecycle controller, requiring it to have been injected.
     *
     * <p>The production client injects an assembled controller through the
     * {@link #LiveCalls2Service(LinkedWhatsAppClient, WamService, MessageService, Calls2LifecycleController, LiveCallEventBus)
     * full-dependency constructor} ({@code LiveLinkedWhatsAppClient} assembles it through
     * {@link Calls2EngineAssembler#assemble}), so this method returns it directly. A service built through
     * the {@link #LiveCalls2Service(LinkedWhatsAppClient, WamService, MessageService) controller-less
     * constructor} has no engine, and any call-control method that reaches the engine through this accessor
     * fails fast rather than silently dropping the action.
     *
     * @return the engine lifecycle controller
     * @throws IllegalStateException if this service was built without an engine controller
     */
    private Calls2LifecycleController engine() {
        if (lifecycleController == null) {
            throw new IllegalStateException("calls2 engine lifecycle controller is not wired");
        }
        return lifecycleController;
    }

    /**
     * Builds and commits the WAM Call event for one ended call.
     *
     * <p>The event combines the call's start-time telemetry dimensions, drawn from its
     * {@link Calls2Runtime#stats()} accumulator, with the call's terminal {@link CallEndReason} mapped to a
     * {@link CallResultType}, and is committed through {@link WamService}. The completed call is also reported
     * to the ctlv2 thread-logging aggregator through
     * {@link LinkedWhatsAppClient#recordThreadActivity(com.github.auties00.cobalt.model.jid.JidProvider, ThreadLoggingActivity)}
     * as a {@link ThreadLoggingActivity.Call} keyed by the call's {@link com.github.auties00.cobalt.model.call.Call#chatJid() chat JID},
     * carrying the direction and connected duration. Any {@link RuntimeException} raised while building,
     * committing, or recording is swallowed so that telemetry never propagates a failure into the call path.
     *
     * @param runtime the runtime of the call that just ended
     */
    private void emitFieldStatsEvent(Calls2Runtime runtime) {
        try {
            var stats = runtime.stats();
            var endReason = runtime.call().endReason().orElse(CallEndReason.UNKNOWN);
            var resultType = stats.result()
                    .map(LiveCalls2Service::toResultType)
                    .orElseGet(() -> mapToResultType(endReason));
            var connectedDuration = stats.connectedDurationSeconds();
            var group = runtime.call().isGroup();
            var builder = new CallEventBuilder()
                    .callRandomId(stats.callId())
                    .callSide(stats.side())
                    .callResult(resultType)
                    .videoEnabled(stats.videoEnabled())
                    .videoEnabledAtCallStart(stats.videoEnabled())
                    .callOfferElapsedT(stats.startedAt())
                    .isLidCall(true)
                    .callTransport(CallTransportType.UDP_RELAY)
                    .callNetwork(CallNetworkMedium.WIFI)
                    .isRejoin(false)
                    .isCallFull(false);
            if (connectedDuration > 0) {
                builder.durationTSs(Instant.ofEpochSecond(connectedDuration));
                if (!group) {
                    builder.numConnectedPeers(1);
                }
            }
            if (stats.side() == CallSide.CALLER) {
                builder.callFromUi(group ? CallFromUi.GROUP_CALL_INFO : CallFromUi.CONVERSATION);
            }
            wamService.commit(builder.build());
            whatsapp.recordThreadActivity(runtime.call().chatJid(), new ThreadLoggingActivity.Call(
                    stats.side() == CallSide.CALLER, connectedDuration));
            if (group) {
                emitJoinableCallEvent(runtime, resultType);
            }
        } catch (RuntimeException _) {
            // Telemetry is best-effort and must never break the call path; a build or commit failure is
            // swallowed.
        }
    }

    /**
     * Builds and commits the WAM Joinable Call event for one ended joinable (group or call-link) call.
     *
     * <p>WhatsApp emits a JoinableCall event in addition to the Call event over a joinable call's lifetime;
     * this is its end-of-call counterpart, committed alongside the Call event for a group or call-link call.
     * It carries the call identity, side, result, and video dimensions the service has and marks the call as
     * not one-to-one; the lobby-latency timers, the connected and invited peer counts, and the call-link
     * dimensions stay unset until the group-call manager feeds them in.
     *
     * @param runtime    the runtime of the joinable call that just ended
     * @param resultType the call result already resolved for the Call event
     */
    private void emitJoinableCallEvent(Calls2Runtime runtime, CallResultType resultType) {
        var stats = runtime.stats();
        var event = new JoinableCallEventBuilder()
                .callRandomId(stats.callId())
                .callSide(stats.side())
                .legacyCallResult(resultType)
                .videoEnabled(stats.videoEnabled())
                .isOneOnOneCall(false)
                .build();
        wamService.commit(event);
    }

    /**
     * Builds and commits the WAM pre-call user-journey event for a call this client is placing.
     *
     * <p>WhatsApp records a pre-call funnel beacon at the moment the user initiates a call from a chat
     * thread (tapping the audio or video call control, starting a group call, or joining a call link), well
     * before the call connects, so the pre-call journey can be reconciled against the call that follows. This
     * emits that beacon the instant the engine has minted the call identifier, keyed to it through
     * {@link PreCallUserJourneyChatThreadEventBuilder#callRandomId(String)}, and stamps the initiating
     * action, the call-size class, and the video flag drawn from the placement call, together with the
     * service's {@link #appSessionId} and freshly minted per-call surface and funnel identifiers. The
     * event fires only when {@link WamService telemetry} is enabled; a build or commit failure is swallowed
     * so telemetry never breaks the call-placement path. The optional dimensions are set only when the
     * caller supplies them, so an audio-only one-to-one placement carries no group size and a call-link join
     * carries no sub-surface.
     *
     * @param callId           the identifier of the call being placed, correlating the funnel to the call
     * @param action           the pre-call action that initiated the placement
     * @param sizeType         the call-size class of the placement
     * @param video            whether the call is being placed with video enabled
     * @param subSurface       the UI sub-surface the placement originated from, or {@code null} when none
     *                         applies
     * @param callSize         the number of participants the call is placed to, or {@code null} when it is
     *                         not known at placement
     * @param groupSize        the size of the group the call is placed within, or {@code null} for a
     *                         non-group placement
     * @param isCommunityGroup whether the group is a community group, or {@code null} for a non-group
     *                         placement
     */
    private void emitPreCallJourney(String callId, PreCallActionType action, CallSizeType sizeType,
                                    boolean video, SubSurface subSurface, Long callSize, Long groupSize,
                                    Boolean isCommunityGroup) {
        if (wamService == null) {
            return;
        }
        try {
            var builder = new PreCallUserJourneyChatThreadEventBuilder()
                    .appSessionId(appSessionId)
                    .callRandomId(callId)
                    .callSizeType(sizeType)
                    .isVideoCall(video)
                    .preCallActionType(action)
                    .surfaceSessionId(randomSessionId())
                    .userJourneyFunnelId(randomSessionId())
                    .userJourneyEventMs(System.currentTimeMillis());
            if (subSurface != null) {
                builder.subSurface(subSurface);
            }
            if (callSize != null) {
                builder.callSize(callSize);
            }
            if (groupSize != null) {
                builder.groupSize(groupSize);
            }
            if (isCommunityGroup != null) {
                builder.isCommunityGroup(isCommunityGroup);
            }
            wamService.commit(builder.build());
        } catch (RuntimeException _) {
            // Telemetry is best-effort and must never break the call-placement path; a build or commit
            // failure is swallowed.
        }
    }

    /**
     * Mints a fresh random 16-hex-character identifier for a WAM session or funnel field.
     *
     * <p>WhatsApp identifies an app session and a pre-call funnel with opaque random strings the receiver
     * only groups by, never parses; this produces one such identifier from the low 64 bits of a
     * {@link ThreadLocalRandom} draw, zero-padded to 16 hexadecimal characters.
     *
     * @return a random 16-hex-character identifier
     */
    private static String randomSessionId() {
        return String.format("%016x", ThreadLocalRandom.current().nextLong());
    }

    /**
     * Maps a {@link CallEndReason} to the {@link CallResultType} reported in the WAM Call event.
     *
     * @implNote This implementation reports {@link CallEndReason#ACCEPTED_ELSEWHERE} as
     * {@link CallResultType#CONNECTED}: it arrives as a peer-side terminate, but from the local user's
     * perspective the call did connect, just not on this device.
     *
     * @param reason the canonical end reason
     * @return the matching WAM result type
     */
    private static CallResultType mapToResultType(CallEndReason reason) {
        return switch (reason) {
            case HANGUP, MEDIA_TX_TIMEOUT, MEDIA_RX_TIMEOUT, AV_UPGRADABLE, AV_UPGRADE -> CallResultType.CONNECTED;
            case TIMEOUT -> CallResultType.MISSED;
            case REJECT_DO_NOT_DISTURB, REJECT_BLOCKED, REJECTED -> CallResultType.REJECTED_BY_USER;
            case REJECTED_ELSEWHERE -> CallResultType.REJECTED_ELSEWHERE;
            case MIC_PERMISSION_DENIED, CAMERA_PERMISSION_DENIED, SETUP_FAILED, RELAY_BIND_FAILED ->
                    CallResultType.SETUP_ERROR;
            case ACCEPTED_ELSEWHERE -> CallResultType.CONNECTED;
            case DEVICE_SWITCH -> CallResultType.ACTIVE_ELSEWHERE;
            case UNKNOWN -> CallResultType.INVALID;
        };
    }

    /**
     * Maps an engine {@link Calls2CallResult} the service recorded onto the {@link CallResultType} the WAM
     * Call event reports.
     *
     * <p>WhatsApp reports the engine's own numeric call-result code on the WAM event, not the terminate
     * reason. The service records that result for the outcomes whose terminal end reason is lossy, in
     * particular the accept-ack NACK; this maps those engine results onto the matching WAM result type:
     * {@link Calls2CallResult#CALL_DOES_NOT_EXIST_FOR_REJOIN}, {@link Calls2CallResult#CALL_IS_FULL}, and
     * {@link Calls2CallResult#CALL_OFFER_ACK_NOT_RECEIVED} carry their own {@link CallResultType} of the same
     * meaning, while any other recorded result falls back to its end-reason projection through
     * {@link #mapToResultType(CallEndReason)} (so an engine result on the terminate axis still reports the
     * same type the reason projection would).
     *
     * @param result the engine call result the service recorded
     * @return the matching WAM result type
     */
    private static CallResultType toResultType(Calls2CallResult result) {
        return switch (result) {
            case CALL_DOES_NOT_EXIST_FOR_REJOIN -> CallResultType.CALL_DOES_NOT_EXIST_FOR_REJOIN;
            case CALL_IS_FULL -> CallResultType.CALL_IS_FULL;
            case CALL_OFFER_ACK_NOT_RECEIVED -> CallResultType.CALL_OFFER_ACK_NOT_RECEIVED;
            case SERVER_NACK -> CallResultType.SERVER_NACK;
            default -> mapToResultType(result.toEndReason());
        };
    }
}
