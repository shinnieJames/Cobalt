package com.github.auties00.cobalt.calls2.core;

import com.github.auties00.cobalt.calls2.VideoStreamState;
import com.github.auties00.cobalt.calls2.common.CallDeviceJid;
import com.github.auties00.cobalt.calls2.common.VoipCapabilities;
import com.github.auties00.cobalt.calls2.config.Calls2FeatureGate;
import com.github.auties00.cobalt.calls2.core.control.*;
import com.github.auties00.cobalt.calls2.core.participant.CallMembership;
import com.github.auties00.cobalt.calls2.core.participant.CallParticipant;
import com.github.auties00.cobalt.calls2.core.participant.CallParticipantUserNode;
import com.github.auties00.cobalt.calls2.crypto.CallKeyExchange;
import com.github.auties00.cobalt.calls2.crypto.CallRekeyEnvelope;
import com.github.auties00.cobalt.calls2.net.transport.AppDataController;
import com.github.auties00.cobalt.calls2.net.transport.RelayElection;
import com.github.auties00.cobalt.calls2.net.transport.RelayLatencyState;
import com.github.auties00.cobalt.calls2.platform.VoipHostApi;
import com.github.auties00.cobalt.calls2.signaling.*;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.model.call.*;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Drives a call through its phases by wiring the signaling, crypto, transport, media, state, timer, and
 * event units together.
 *
 * <p>This is the {@code wa_call_*} engine entry surface: the lifecycle controller the call service layer
 * calls into to place, answer, decline, join, and end a call, and into which the signaling receiver
 * forwards every decoded inbound action. It owns no transport, codec, or state-machine logic of its own;
 * it orchestrates the units that do. For each call it threads the pieces together in the order the engine
 * does:
 * <ul>
 *   <li>allocates and frees the engine call context through {@link Calls2CallContextRegistry} (the
 *       manager seam) as a call starts and ends;</li>
 *   <li>mints and distributes the thirty-two-byte end-to-end call key through {@link CallKeyExchange}
 *       (the reused Signal pipeline), once in the offer for a one-to-one call;</li>
 *   <li>builds each signaling action with {@link Calls2CallStanza} and the typed {@link CallMessage}
 *       records and ships it, the offer through {@link Calls2OfferAckSender} for its synchronous relay-
 *       bearing ack and every other leg through {@link VoipHostApi#sendSignaling(Stanza)} fire-and-forget;</li>
 *   <li>brings up the media plane through {@link Calls2MediaPlane} once the call is answered and the relay
 *       block is known;</li>
 *   <li>advances the fifteen-state internal machine through {@link Calls2CallStateTransition} and fires
 *       the {@code call_state_event} through {@link Calls2CallEventSink} on every accepted change;</li>
 *   <li>arms and cancels the per-call timers through {@link Calls2CallTimerScheduler};</li>
 *   <li>folds each lifecycle event into the call's result snapshot through {@link Calls2CallInfoUpdater};
 *       and</li>
 *   <li>emits the typed events of the one-hundred-seventy-two-event bus through
 *       {@link Calls2CallEventSink}.</li>
 * </ul>
 *
 * <p>The controller keeps the public {@link Call} view and the cross-unit wiring per call in a
 * {@link Calls2OrchestratedCall} handle, registered by call identifier; the heavy engine call context,
 * its eleven timer entries, and its durations live in the state, timer, and info-manager units this
 * controller drives by call identifier. At most a primary and one secondary (dual) call are admitted at
 * once, the engine's call-manager ceiling; a third placement or a second inbound offer while two calls
 * are live is refused. Every public method blocks on its own virtual thread per the Cobalt threading
 * model (a native {@code await} is a plain blocking call here); a single call's orchestration fields are
 * read and mutated under that call's {@linkplain Calls2OrchestratedCall#lock() per-call lock}, the engine
 * {@code call-info-mutex} analogue, so a user action and an inbound signal on the same call never
 * interleave a half-applied phase change.
 *
 * <p>Call failures are isolated, never session-fatal: a transport or media bring-up that cannot start
 * surfaces as a non-fatal {@link WhatsAppCallException.DataChannel} that ends only that call, matching the
 * Cobalt redesigned error model in which a call never tears the messaging session down. A precondition
 * violation (placing a call while not connected, answering a call that does not exist) is an
 * {@link IllegalStateException} or {@link IllegalArgumentException} the caller is expected to avoid.
 *
 * @apiNote This is an internal engine surface, not a public client API; the call service layer
 * ({@code LiveCalls2Service}) is the only caller, and it adapts the application's place, accept, reject,
 * and end requests onto these methods and wires the signaling receiver's sink onto
 * {@link #handleIncomingMessage(CallMessage, Jid)}. Embedders never call this controller directly.
 * @implNote This implementation is the host-side reimplementation of the lifecycle entry points of
 * {@code call_lifecycle.cc} in the wa-voip WASM module {@code ff-tScznZ8P}: {@code wa_call_start_call}
 * (fn10711), {@code call_accept} / {@code call_accept_impl} (fn10717 / fn10709), {@code make_and_send_preaccept}
 * (fn11453), {@code wa_call_reject} / {@code wa_call_reject_without_call_ctx} (fn10722 / fn10723),
 * {@code wa_call_join_ongoing_call} (fn10712), {@code call_manager_end_call} (fn10733), and the inbound
 * dispatcher {@code wa_call_handle_incoming_signaling_xmpp_msg} (fn10724) with its offer path
 * {@code wa_call_handle_incoming_xmpp_offer}. The native pjlib timer heap, mem pools, and per-context
 * mutex it touches are replaced by a virtual-thread scheduler, the JVM heap, and a {@link ReentrantLock}
 * per the Cobalt threading model; the media and transport bring-up the native {@code call_accept_impl}
 * performs inline are reached through the {@link Calls2MediaPlane} seam over the codec and transport units
 * rather than reproduced here. The result/end-reason axis is kept separate from the state machine
 * ({@link Calls2CallResult} and {@link Calls2CallInfoUpdater} versus {@link Calls2CallStateTransition}),
 * matching the native split between {@code set_call_result} (fn10923) and {@code change_call_state}
 * (fn10921).
 */
public final class Calls2LifecycleController {
    /**
     * Logs lifecycle-phase traces and isolated call failures.
     */
    private static final System.Logger LOGGER = System.getLogger(Calls2LifecycleController.class.getName());

    /**
     * The number of random bytes drawn to seed a call identifier.
     *
     * <p>The engine draws sixteen random bytes and hex-encodes them into the thirty-two-character call
     * identifier.
     */
    private static final int CALL_ID_RANDOM_BYTES = 16;

    /**
     * The hex digits used to encode a call identifier, with the high sixteen entries in upper case and
     * the low sixteen in lower case so the encoded identifier's case varies across its characters.
     *
     * <p>This is the exact translation table the engine uses; encoding a byte's high nibble then its low
     * nibble through it reproduces the engine's mixed-case identifier.
     */
    private static final char[] CALL_ID_HEX = "0123456789ABCDEF0123456789abcdef".toCharArray();

    /**
     * The maximum number of calls admitted at once: a primary and one optional secondary (dual) call.
     */
    private static final int MAX_CONCURRENT_CALLS = 2;

    /**
     * The {@code join-state} attribute value stamped on the single {@code link_join} request.
     *
     * <p>The native {@code make_and_send_link_join} (fn11454) sends exactly one {@code link_join} stanza and
     * stamps a single {@code join-state} attribute on it whose value is {@code 2} by default and {@code 1}
     * when a call-context flag byte (context-relative {@code +0xa14b7}) is clear (fn11454 lines 48-50). There
     * is no second leg or second stanza: the serializer writes the one message and returns (fn11454 line 72
     * then line 78). The flag's exact meaning is not labeled in the binary; pending a live call-link-join
     * capture to confirm which condition selects {@code 1} versus {@code 2}, the join is sent with the
     * default {@code 2}.
     *
     * @implNote This implementation reproduces the {@code join-state} default of {@code make_and_send_link_join}
     * (fn11454): the {@code local_c0 = 2} default, taken because the context flag that would lower it to
     * {@code 1} is a condition Cobalt's link-join entry does not yet model. See the
     * {@link #joinCallLink(Jid, String, CallLinkMedia, boolean, Calls2MediaStreams)} TODO for the precise
     * blocker.
     */
    private static final int LINK_JOIN_STATE_DEFAULT = 2;

    /**
     * The all-zeros sentinel call-id a call-link joiner registers under until the join ack supplies the
     * relay-assigned id.
     *
     * <p>WA's {@code wa_call_preview_call_link} (fn11114) inits the joiner's call object with the
     * thirty-two-character zero string at data offset {@code 830675} and adopts the relay-assigned id from
     * the join ack; this is that sentinel.
     */
    private static final String PLACEHOLDER_CALL_ID = "00000000000000000000000000000000";

    /**
     * The wire {@code rate} of the narrowband audio format offered.
     */
    private static final int AUDIO_RATE_NARROWBAND = 8000;

    /**
     * The wire {@code rate} of the wideband audio format offered.
     */
    private static final int AUDIO_RATE_WIDEBAND = 16000;

    /**
     * The wire {@code enc} codec name of the offered audio format.
     */
    private static final String AUDIO_CODEC_OPUS = "opus";

    /**
     * The wire {@code dec} decode-codec token of the offered video format.
     *
     * <p>The live captures show every video offer advertising a single
     * {@code <video dec="H264" enc="h.264" device_orientation="0" screen_width="0" screen_height="0"/>}
     * element, so the offer advertises H.264 as the decode token.
     */
    private static final String VIDEO_DEC_TOKEN = "H264";

    /**
     * The wire {@code enc} encode-codec name of the offered video format, the lowercase form the live
     * captures pair with the {@link #VIDEO_DEC_TOKEN} decode token.
     */
    private static final String VIDEO_ENC_NAME = "h.264";

    /**
     * The {@code <net medium>} classification an outbound offer advertises.
     *
     * <p>The live captures show every offer carrying {@code <net medium="3"/>}; the caller advertises
     * medium three and the callee selects {@link #NET_MEDIUM_ACCEPT} in its accept.
     */
    private static final int NET_MEDIUM_OFFER = 3;

    /**
     * The {@code <net medium>} classification an outbound accept selects, {@code <net medium="2"/>} in
     * the live captures.
     */
    private static final int NET_MEDIUM_ACCEPT = 2;

    /**
     * The capability advertisement version stamped on the {@code <capability>} element.
     */
    private static final int CAPABILITY_VERSION = 1;

    /**
     * The default maximum number of simultaneously-connected participants advertised on a group offer's
     * {@code <group_info>} roster.
     *
     * <p>The live group capture shows {@code connected-limit="32"} on every {@code <group_info>}; the
     * server may negotiate a different ceiling through the {@code GROUP_CALL_MAX_PARTICIPANTS} AB-prop, but
     * the placement default is this value.
     */
    private static final int GROUP_CONNECTED_LIMIT = 32;

    /**
     * The wire element tag of an engine parameter bundle the offer and offer acknowledgement carry.
     */
    private static final String VOIP_SETTINGS_ELEMENT = "voip_settings";

    /**
     * Sends an offer and returns its synchronous, relay-bearing call ack.
     */
    private final Calls2OfferAckSender offerAckSender;

    /**
     * Mints, wraps, distributes, and recovers the end-to-end call key over the reused Signal pipeline.
     */
    private final CallKeyExchange crypto;

    /**
     * Sends the fire-and-forget signaling legs and supplies the cryptographically strong randomness that
     * seeds a call identifier.
     */
    private final VoipHostApi host;

    /**
     * Allocates and frees the engine call context for each call.
     */
    private final Calls2CallContextRegistry registry;

    /**
     * Drives the internal state machine through the transition guard.
     */
    private final Calls2CallStateTransition stateMachine;

    /**
     * Arms and cancels the per-call timers.
     */
    private final Calls2CallTimerScheduler timers;

    /**
     * Folds each lifecycle event into the call's result snapshot.
     */
    private final Calls2CallInfoUpdater infoUpdater;

    /**
     * Applies the should-emit gate and fans each lifecycle event out to the listeners.
     */
    private final Calls2CallEventSink events;

    /**
     * Brings up and tears down each call's media plane.
     */
    private final Calls2MediaPlane mediaPlane;

    /**
     * Validates, de-duplicates, and classifies each decoded inbound signaling message before dispatch.
     *
     * <p>The router is stateless (the per-call de-duplication state is threaded in and out through each
     * call's {@link Calls2OrchestratedCall#dedupState()}), so one instance classifies every inbound call.
     * It is parameterised on the controller's own {@link Calls2OrchestratedCall} handle and resolves a
     * context through the {@code calls} working-set lookup, so its verdict's resolved context is the very
     * handle the per-type inbound handlers re-resolve through {@code calls.get}.
     */
    private final Calls2IncomingMessageRouter<Calls2OrchestratedCall> incomingRouter =
            new Calls2IncomingMessageRouter<>();

    /**
     * Holds the controller's per-call orchestration handles, keyed by call identifier.
     *
     * <p>The map is the controller's own working set, distinct from the engine call context the state and
     * timer units own; it is bounded at {@value #MAX_CONCURRENT_CALLS} by the dual-call guard.
     */
    private final ConcurrentHashMap<String, Calls2OrchestratedCall> calls = new ConcurrentHashMap<>();

    /**
     * The shared host event bus a call's in-call control units publish their host-facing events onto, or
     * {@code null} until {@linkplain #bindEventBus(LiveCallEventBus, Supplier) bound}.
     *
     * <p>The bus is bound after construction rather than taken as a constructor argument, mirroring the
     * timer scheduler and media-plane connection sink the assembler binds the same way; until it is bound a
     * call carries no in-call control units, so the lifecycle path (offer, accept, reject, terminate) is
     * exercisable without it.
     */
    private volatile LiveCallEventBus eventBus;

    /**
     * Supplies the local device JID an in-call control context is stamped with, or {@code null} until
     * {@linkplain #bindEventBus(LiveCallEventBus, Supplier) bound}.
     *
     * <p>Read each time a call's control units are built so a self JID that became known after assembly (the
     * engine is assembled before the client logs in) is picked up; an outbound call falls back to its call
     * creator, which already is the local device.
     */
    private volatile Supplier<Jid> selfJidSupplier;

    /**
     * The typed read facade over the server calling AB-props that gate which call operations may start, or
     * {@code null} until {@linkplain #bindFeatureGate(Calls2FeatureGate) bound}.
     *
     * <p>The gate is bound after construction rather than taken as a constructor argument, mirroring the event
     * bus and the timer scheduler the assembler binds the same way; this keeps the controller's constructor
     * stable for the test harnesses that build it directly without the live AB-props service. Until it is
     * bound the start, group-start, and screen-share entry points apply no feature gating, matching a build
     * exercised without the AB-props service.
     */
    private volatile Calls2FeatureGate featureGate;

    /**
     * The store-backed predicate reporting whether a device JID is one of the local account's own
     * devices, or {@code null} until {@linkplain #bindOwnDeviceResolver(Predicate) bound}.
     *
     * <p>Consulted by the companion-device terminate guard in {@link #handlePeerTerminate(TerminateStanza,
     * Jid)} to decide whether an inbound terminate's authoring device is another device of the local
     * account (a companion) rather than the remote peer. The resolver is bound after construction rather
     * than taken as a constructor argument, mirroring the event bus and feature gate the assembler binds
     * the same way; this keeps the controller's constructor stable for the test harnesses that build it
     * directly without a store. Until it is bound the companion-device guard never fires, so an unbound
     * build tears the call down on any terminate exactly as before.
     */
    private volatile Predicate<Jid> ownDeviceResolver;

    /**
     * The request-reply IQ sender the per-call {@link CallLinkController} and {@link WaitingRoomController}
     * dispatch their {@code to="call"} link and waiting-room IQs through, or {@code null} until
     * {@linkplain #bindCallLinkIqSender(CallLinkIqSender) bound}.
     *
     * <p>The sender is bound after construction rather than taken as a constructor argument, mirroring the
     * event bus, feature gate, and own-device resolver the assembler binds the same way; this keeps the
     * controller's constructor stable for the test harnesses that build it directly without a live socket.
     * Until it is bound the call-link join entry point ({@link #joinCallLink(Jid, String, CallLinkMedia,
     * boolean, Calls2MediaStreams)}) cannot run, since the link query-and-join handshake is a blocking
     * round trip with no fire-and-forget fallback.
     */
    private volatile CallLinkIqSender callLinkIqSender;

    /**
     * The outbound call-log sink each ended call's finished context is handed to, or a no-op until
     * {@linkplain #bindCallLogSink(BiConsumer) bound}.
     *
     * <p>The sink is fired once per call from {@link #tearDown(Calls2OrchestratedCall, CallEndReason,
     * CallEventType)} with the call's engine {@link Calls2CallContext} and the terminal {@link CallEndReason},
     * after the call's durations are closed out, so the call-log build and the {@code call_log} app-state push
     * run at the same teardown moment the WAM stats drain does while staying an independent output. The sink
     * is bound after construction rather than taken as a constructor argument, mirroring the event bus,
     * feature gate, own-device resolver, and call-link sender the assembler and client bind the same way; the
     * field defaults to a no-op so the test harnesses that build the controller directly and never bind it
     * record no call log and are otherwise unaffected.
     */
    private volatile BiConsumer<Calls2CallContext, CallEndReason> callLogSink = (context, reason) -> {
    };

    /**
     * The result sink each engine-resolved call result is reported to, or a no-op until
     * {@linkplain #bindResultSink(BiConsumer) bound}.
     *
     * <p>Some outcomes carry a distinct engine call result the terminal end reason cannot recover (an offer
     * NACK is {@link Calls2CallResult#SERVER_NACK}, not the generic teardown reason). The controller reports
     * those to this sink, keyed by call id, so the service can record the result on the call's telemetry
     * before the call is torn down; it defaults to a no-op so an unbound build is unaffected.
     */
    private volatile BiConsumer<String, Calls2CallResult> resultSink = (callId, result) -> {
    };

    /**
     * The teardown sink each ended call's identifier is reported to once at the ENDED transition, or a no-op
     * until {@linkplain #bindTeardownSink(Consumer) bound}.
     *
     * <p>WhatsApp drains a call's end-of-call WAM telemetry and frees its per-call context from the call
     * manager exactly once at the call's ending transition, for every ended call regardless of which side
     * ended it. The controller's {@link #tearDown(Calls2OrchestratedCall, CallEndReason, CallEventType)} is
     * that single ending transition for every end path (a local hangup through {@link #endCall(String,
     * CallEndReason)}, a peer terminate, a reject, a timeout, an offer NACK, or a setup failure), so it fires
     * this sink there. The call service binds {@code unregister} onto it, which removes the call from the
     * service registry, removes it from the store, and commits the WAM Call event. The sink is bound after
     * construction rather than taken as a constructor argument, mirroring the result sink and call-log sink
     * the service binds the same way; it defaults to a no-op so the controller-construction test harnesses
     * that never bind it tear a call down without draining service-level telemetry.
     */
    private volatile Consumer<String> teardownSink = callId -> {
    };

    /**
     * Constructs a lifecycle controller over its collaborating units.
     *
     * @param offerAckSender the sender that ships an offer and returns its synchronous call ack
     * @param crypto         the call-key crypto facade over the reused Signal pipeline
     * @param host           the host API supplying signaling egress and cryptographically strong
     *                       randomness
     * @param registry       the manager seam that allocates and frees the engine call context
     * @param stateMachine   the state-transition guard
     * @param timers         the per-call timer scheduler
     * @param infoUpdater    the info-manager update that folds events into the result snapshot
     * @param events         the event sink that gates and fans out the typed events
     * @param mediaPlane     the media-plane bring-up and teardown seam
     * @throws NullPointerException if any argument is {@code null}
     */
    public Calls2LifecycleController(Calls2OfferAckSender offerAckSender, CallKeyExchange crypto,
                                     VoipHostApi host, Calls2CallContextRegistry registry,
                                     Calls2CallStateTransition stateMachine, Calls2CallTimerScheduler timers,
                                     Calls2CallInfoUpdater infoUpdater, Calls2CallEventSink events,
                                     Calls2MediaPlane mediaPlane) {
        this.offerAckSender = Objects.requireNonNull(offerAckSender, "offerAckSender cannot be null");
        this.crypto = Objects.requireNonNull(crypto, "crypto cannot be null");
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine cannot be null");
        this.timers = Objects.requireNonNull(timers, "timers cannot be null");
        this.infoUpdater = Objects.requireNonNull(infoUpdater, "infoUpdater cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.mediaPlane = Objects.requireNonNull(mediaPlane, "mediaPlane cannot be null");
    }

    /**
     * Binds the shared host event bus and local-JID supplier the in-call control units publish through.
     *
     * <p>The bus and self-JID supplier are not constructor arguments because the assembler builds this
     * controller before the bus and the logged-in identity are both available; it binds them here once,
     * the same post-construction wiring step the timer scheduler and the media-plane connection sink use.
     * Once bound, a call that becomes answerable is given its in-call control units (mute, video,
     * screen-share, raise-hand) wired onto a {@link ControlEventBridge} over this bus; before binding a call
     * carries no control units and the in-call control methods are no-ops for a tracked call.
     *
     * @apiNote The call service's engine assembler is the only caller; an embedder never binds the bus.
     * @implNote This implementation stores the bus and supplier in {@code volatile} fields read on the
     * wiring thread and the control threads; both are written once, before any call is placed or answered.
     * @param eventBus        the shared host event bus the control units publish their events onto
     * @param selfJidSupplier supplies the local device JID stamped onto a control context, re-read per call
     * @throws NullPointerException if {@code eventBus} or {@code selfJidSupplier} is {@code null}
     */
    public void bindEventBus(LiveCallEventBus eventBus, Supplier<Jid> selfJidSupplier) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.selfJidSupplier = Objects.requireNonNull(selfJidSupplier, "selfJidSupplier cannot be null");
    }

    /**
     * Binds the server-AB-prop feature gate the start, group-start, and screen-share entry points consult.
     *
     * <p>The gate is not a constructor argument because the assembler builds this controller before the live
     * AB-props service is threaded in, and the test harnesses build the controller directly with no such
     * service; binding it here, the same post-construction wiring step {@link #bindEventBus(LiveCallEventBus,
     * Supplier)} uses, keeps the constructor stable. Once bound, {@link #startCall(Jid, Jid, List, boolean,
     * Calls2MediaStreams)} is gated on {@link Calls2FeatureGate#isWebCallingEnabled()},
     * {@link #startGroupCall(Jid, Collection, Jid, boolean, Calls2MediaStreams)} on
     * {@link Calls2FeatureGate#isGroupCallsEnabled()} and the
     * {@link Calls2FeatureGate#groupCallMaxParticipants()} ceiling, and {@link #startScreenShare(String)} on
     * {@link Calls2FeatureGate#isScreenShareEnabled()}; before binding those entry points apply no gating.
     *
     * @apiNote The call service's engine assembler is the only caller; an embedder never binds the gate.
     * @implNote This implementation stores the gate in a {@code volatile} field read on the calling threads;
     * it is written once, before any call is placed.
     * @param featureGate the typed read facade over the server calling AB-props
     * @throws NullPointerException if {@code featureGate} is {@code null}
     */
    public void bindFeatureGate(Calls2FeatureGate featureGate) {
        this.featureGate = Objects.requireNonNull(featureGate, "featureGate cannot be null");
    }

    /**
     * Binds the store-backed own-device predicate the companion-device terminate guard consults.
     *
     * <p>The resolver is not a constructor argument because the assembler builds this controller before
     * the logged-in identity and device list are settled, and the test harnesses build the controller
     * directly with no store; binding it here, the same post-construction wiring step
     * {@link #bindEventBus(LiveCallEventBus, Supplier)} and {@link #bindFeatureGate(Calls2FeatureGate)}
     * use, keeps the constructor stable. Once bound, the companion-device guard in
     * {@link #handlePeerTerminate(TerminateStanza, Jid)} consults it to decide whether a terminate's
     * authoring device is one of the local account's own devices; before binding the guard never fires.
     *
     * @apiNote The call service's engine assembler is the only caller; an embedder never binds the
     * resolver.
     * @implNote This implementation stores the predicate in a {@code volatile} field read on the inbound
     * signaling threads; it is written once, before any call is placed or answered. The predicate itself
     * reads the live account store on each call, so a self JID, LID, or linked-device set that became
     * known after binding is picked up without rebinding.
     * @param ownDeviceResolver the predicate reporting whether a device JID is one of the local account's
     *                          own devices
     * @throws NullPointerException if {@code ownDeviceResolver} is {@code null}
     */
    public void bindOwnDeviceResolver(Predicate<Jid> ownDeviceResolver) {
        this.ownDeviceResolver = Objects.requireNonNull(ownDeviceResolver, "ownDeviceResolver cannot be null");
    }

    /**
     * Binds the request-reply IQ sender the call-link and waiting-room control units dispatch through.
     *
     * <p>The sender is not a constructor argument because the assembler builds this controller before the
     * client socket it sends over is reachable, and the test harnesses build the controller directly with no
     * socket; binding it here, the same post-construction wiring step
     * {@link #bindEventBus(LiveCallEventBus, Supplier)} and {@link #bindFeatureGate(Calls2FeatureGate)} use,
     * keeps the constructor stable. Once bound, {@link #joinCallLink(Jid, String, CallLinkMedia, boolean,
     * Calls2MediaStreams)} constructs the per-call {@link CallLinkController} and {@link WaitingRoomController}
     * over this sender so the link query-and-join handshake and the waiting-room admit and deny ride it;
     * before binding the call-link join entry point refuses to run.
     *
     * @apiNote The call service's engine assembler is the only caller; an embedder never binds the sender.
     * @implNote This implementation stores the sender in a {@code volatile} field read on the call-link join
     * thread; it is written once, before any call is placed or joined.
     * @param callLinkIqSender the blocking {@code to="call"} request-reply egress the link controllers use
     * @throws NullPointerException if {@code callLinkIqSender} is {@code null}
     */
    public void bindCallLinkIqSender(CallLinkIqSender callLinkIqSender) {
        this.callLinkIqSender = Objects.requireNonNull(callLinkIqSender, "callLinkIqSender cannot be null");
    }

    /**
     * Binds the outbound call-log sink each ended call's finished context is handed to at teardown.
     *
     * <p>The sink is not a constructor argument because the call-log collaborator it points at
     * ({@code Calls2CallLogSync.recordEndOfCall}) depends on the client's app-state push round, which is
     * threaded in after this controller is assembled, and the test harnesses build the controller directly
     * with no such collaborator; binding it here, the same post-construction wiring step
     * {@link #bindEventBus(LiveCallEventBus, Supplier)} and {@link #bindFeatureGate(Calls2FeatureGate)} use,
     * keeps the constructor stable. Once bound, {@link #tearDown(Calls2OrchestratedCall, CallEndReason,
     * CallEventType)} hands the ended call's engine {@link Calls2CallContext} and terminal
     * {@link CallEndReason} to the sink exactly once per call, after the call's durations are closed out;
     * before binding the teardown records no call log.
     *
     * @apiNote The call service's engine assembler, or the client that owns the call-log collaborator, is
     * the only caller; an embedder never binds the sink.
     * @implNote This implementation stores the sink in a {@code volatile} field read on the teardown thread;
     * it is written once, before any call is placed or answered, and defaults to a no-op so an unbound build
     * (the controller-construction test harnesses) tears a call down without producing a call log.
     * @param callLogSink the sink consuming the finished {@link Calls2CallContext} and terminal
     *                    {@link CallEndReason} of each ended call
     * @throws NullPointerException if {@code callLogSink} is {@code null}
     */
    public void bindCallLogSink(BiConsumer<Calls2CallContext, CallEndReason> callLogSink) {
        this.callLogSink = Objects.requireNonNull(callLogSink, "callLogSink cannot be null");
    }

    /**
     * Binds the sink each engine-resolved call result is reported to.
     *
     * <p>Bound once, before any call is placed, to the service that records the result on the call's
     * telemetry accumulator. Defaults to a no-op so an unbound build is unaffected.
     *
     * @param resultSink the sink consuming the call id and its resolved {@link Calls2CallResult}
     * @throws NullPointerException if {@code resultSink} is {@code null}
     */
    public void bindResultSink(BiConsumer<String, Calls2CallResult> resultSink) {
        this.resultSink = Objects.requireNonNull(resultSink, "resultSink cannot be null");
    }

    /**
     * Binds the sink each ended call's identifier is reported to once at the ENDED transition.
     *
     * <p>Bound once, before any call is placed, to the service whose {@code unregister} removes the call from
     * the registry, removes it from the store, and commits the end-of-call WAM telemetry. The controller
     * fires it from {@link #tearDown(Calls2OrchestratedCall, CallEndReason, CallEventType)} for every end
     * path, so the service-level teardown and WAM drain fire for every ended call regardless of who ended it,
     * matching WhatsApp's once-per-ending-transition telemetry and context free. Defaults to a no-op so an
     * unbound build is unaffected.
     *
     * @apiNote The call service is the only caller; an embedder never binds the sink.
     * @implNote This implementation stores the sink in a {@code volatile} field read on the teardown thread;
     * it is written once, before any call is placed or answered. It is kept independent of the call-log sink
     * and the result sink because the service-level teardown (registry removal plus WAM commit) is a distinct
     * end-of-call output from the app-state call-log push and the engine-result record.
     * @param teardownSink the sink consuming the identifier of each ended call
     * @throws NullPointerException if {@code teardownSink} is {@code null}
     */
    public void bindTeardownSink(Consumer<String> teardownSink) {
        this.teardownSink = Objects.requireNonNull(teardownSink, "teardownSink cannot be null");
    }

    /**
     * Reports the local user's own mute state for a call, announcing it to the peer.
     *
     * <p>Delegates to the call's {@link MuteController#setMuted(boolean)}, which sends the {@code mute_v2}
     * self-state action and emits the mute change; a recent unmute opens the controller's thirty-second
     * lockout against an inbound peer-mute. A call that is not tracked, or one whose control units are not
     * built (the bus is unbound or the call is not yet answerable), is a no-op.
     *
     * @param callId the identifier of the call whose mute state is reported
     * @param muted  {@code true} to report the local user muted, {@code false} to report unmuted
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void setMuted(String callId, boolean muted) {
        Objects.requireNonNull(callId, "callId cannot be null");
        withControls(callId, controls -> controls.mute().setMuted(muted));
    }

    /**
     * Turns the local camera on or off for a call, announcing the video state to the peer.
     *
     * <p>Delegates to the call's {@link VideoStateController#turnCamera(boolean)}, which sends the
     * {@code video_state} action and emits the video change. This is the announce half only; the actual
     * camera capture on the media plane is driven separately. A call that is not tracked, or one whose
     * control units are not built, is a no-op.
     *
     * @param callId  the identifier of the call whose video state is reported
     * @param enabled {@code true} to turn the camera on, {@code false} to turn it off
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void setVideoEnabled(String callId, boolean enabled) {
        Objects.requireNonNull(callId, "callId cannot be null");
        withControls(callId, controls -> controls.video().turnCamera(enabled));
    }

    /**
     * Starts the local camera video track on a call, announcing the video state and driving the media plane.
     *
     * <p>This is the in-call camera turn-on used by an audio-to-video upgrade: it announces video-on to the
     * peer through the call's {@link VideoStateController#turnCamera(boolean) turnCamera(true)} and starts the
     * outbound camera-capture and encode path on the call's media plane through
     * {@link Calls2MediaPlane.Session#startLocalVideo()}. The announce and the media-plane start are the two
     * halves of turning the local camera on: {@link #setVideoEnabled(String, boolean)} carries only the
     * announce, while this also raises the local video track. A call that is not tracked, one whose control
     * units are not built (the bus is unbound or the call is not yet answerable), or one whose media plane is
     * not yet up runs only the parts it can: the announce fires once the control units exist, and the
     * media-plane start fires once the session is up.
     *
     * @implNote This implementation reproduces {@code wa_call_video_turn_camera_on} (fn1109): the
     * {@code video_state} announce and the local camera-track start are driven together, the announce through
     * the per-call {@link VideoStateController} and the camera-track start through the media session's
     * {@link Calls2MediaPlane.Session#startLocalVideo()} seam. Both run under the call's lock so the announce
     * and the media-plane start are not interleaved with a concurrent teardown.
     *
     * @param callId the identifier of the call whose local camera is turned on
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void startLocalVideo(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            ensureControls(orchestrated);
            orchestrated.controls().ifPresent(controls -> controls.video().turnCamera(true));
            orchestrated.mediaSession().ifPresent(Calls2MediaPlane.Session::startLocalVideo);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Issues a video-upgrade request for a one-to-one call, asking the peer to turn an audio call to video.
     *
     * <p>Delegates to the call's {@link VideoStateController#requestUpgrade(boolean)} with the v1 request
     * shape; the peer answers with an accept or reject. A call that is not tracked, or one whose control
     * units are not built, is a no-op.
     *
     * @param callId the identifier of the call to upgrade
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void requestVideoUpgrade(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        withControls(callId, controls -> controls.video().requestUpgrade(false));
    }

    /**
     * Rejects a pending video-upgrade request on a call.
     *
     * <p>Delegates to the call's {@link VideoStateController#rejectUpgrade()}. A call that is not tracked, or
     * one whose control units are not built, is a no-op.
     *
     * @param callId the identifier of the call whose upgrade request is rejected
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void rejectVideoUpgrade(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        withControls(callId, controls -> controls.video().rejectUpgrade());
    }

    /**
     * Starts the local screen-share stream for a call, announcing it to the peer.
     *
     * <p>Delegates to the call's {@link ScreenShareController#start()}, which sends the {@code screen_share}
     * action at the negotiated protocol version and emits the screen-share change. This is the V2
     * announce-only half; the V3 dual-stream auxiliary media stream is not started here. A call that is not
     * tracked, or one whose control units are not built, is a no-op. When the server feature gate is bound and
     * reports screen sharing disabled, the request is refused rather than silently dropped.
     *
     * @param callId the identifier of the call to start sharing on
     * @throws NullPointerException  if {@code callId} is {@code null}
     * @throws IllegalStateException if in-call screen sharing is disabled for this account by the server
     *                               feature gate
     */
    public void startScreenShare(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var gate = featureGate;
        if (gate != null && !gate.isScreenShareEnabled()) {
            throw new IllegalStateException(
                    "Cannot start screen sharing: it is disabled for this account");
        }
        withControls(callId, controls -> controls.screenShare().start());
    }

    /**
     * Stops the local screen-share stream for a call, announcing it to the peer.
     *
     * <p>Delegates to the call's {@link ScreenShareController#stop()}. A call that is not tracked, or one
     * whose control units are not built, is a no-op.
     *
     * @param callId the identifier of the call to stop sharing on
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void stopScreenShare(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        withControls(callId, controls -> controls.screenShare().stop());
    }

    /**
     * Routes an outbound in-call interaction for a call onto its real engine control plane.
     *
     * <p>Switches the sealed {@link CallInteraction} onto the control unit that owns its plane: a
     * {@link CallInteraction.RaiseHand} or {@link CallInteraction.LowerHand} drives the call's
     * {@link RaiseHandController#setHandRaised(boolean)}, a {@link CallInteraction.PeerMuteRequest} drives the
     * call's {@link MuteController#requestPeerMute(Jid)} against the named target, and a
     * {@link CallInteraction.VideoUpgradeRequest} drives {@link #requestVideoUpgrade(String)}. A
     * {@link CallInteraction.Reaction} drives the call's {@link ReactionController} over the media plane's
     * application-data side-channel when that plane is up; it is accepted without effect when the call
     * carries no app-data plane. A {@link CallInteraction.KeyFrameRequest} arms the outbound video encoder for
     * a fresh key frame through the call's {@link Calls2MediaPlane.Session#requestKeyFrame() media session};
     * it is accepted without effect when the call's media plane is not up or carries no video. A call that is
     * not tracked, or one whose control units are not built, is a no-op.
     *
     * @implNote This implementation handles the signaling-plane interactions (raise-hand, peer-mute,
     * video-upgrade) and routes a reaction onto the {@link ReactionController} bound to the call's
     * {@link AppDataController} (SPEC 17.2: reactions ride the app-data side-channel via
     * {@code wa_app_data_controller_send_reaction}). The outbound key-frame request (the local application
     * asking the encoder for an intra frame) is forwarded to the media session's
     * {@link Calls2MediaPlane.Session#requestKeyFrame()} passthrough, reproducing the web-calling bridge's
     * app-facing {@code requestKeyFrame} (fn1123 in {@code WaCallWebCallingBridge.cpp}), which forwards to
     * {@code pjmedia_vid_stream_request_keyframe} (fn5587). Acting on an inbound peer picture-loss
     * indication (a relayed RTCP PLI/FIR) to trigger a local key-frame is a separate, media-internal path the
     * native engine keeps entirely below the call-control layer (RTCP parser fn4572 -> vid_stream RTCP pass
     * fn5685 -> encoder pending flag fn5533), so it is owned by the media/transport cluster and wired there:
     * {@link com.github.auties00.cobalt.calls2.net.transport.RtcpFeedbackParser} decodes the PSFB PLI/FIR
     * onto {@code RtcpFeedback}, and the media session's inbound-RTCP listener arms the video encoder on it.
     * It is not routed through this controller.
     * @param callId      the identifier of the call the interaction targets
     * @param interaction the interaction to route
     * @throws NullPointerException if {@code callId} or {@code interaction} is {@code null}
     */
    public void sendInteraction(String callId, CallInteraction interaction) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(interaction, "interaction cannot be null");
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            ensureControls(orchestrated);
            // The key-frame arm is the only interaction that reaches the media plane rather than a control
            // unit, so it is dispatched whenever the session is up even before the control units exist; the
            // remaining arms run only once the control units are built.
            if (interaction instanceof CallInteraction.KeyFrameRequest) {
                orchestrated.mediaSession().ifPresent(Calls2MediaPlane.Session::requestKeyFrame);
                return;
            }
            var controls = orchestrated.controls().orElse(null);
            if (controls == null) {
                return;
            }
            switch (interaction) {
                case CallInteraction.RaiseHand ignored -> controls.raiseHand().setHandRaised(true);
                case CallInteraction.LowerHand ignored -> controls.raiseHand().setHandRaised(false);
                case CallInteraction.PeerMuteRequest request ->
                        controls.mute().requestPeerMute(Jid.of(request.target()));
                case CallInteraction.VideoUpgradeRequest ignored -> controls.video().requestUpgrade(false);
                case CallInteraction.Reaction reaction ->
                        controls.reactionControl().ifPresent(unit -> unit.sendReaction(reaction.emoji()));
                case CallInteraction.KeyFrameRequest ignored -> {
                    // Handled above against the media session before the control-unit gate.
                }
            }
        } finally {
            orchestrated.lock().unlock();
        }
    }

    // The inbound PLI/FIR -> local key-frame reaction is deliberately NOT here: it is media-internal in WA
    // (fn4572 -> fn5685 -> fn5534/fn5533), so it is wired in the media/transport cluster (RtcpFeedbackParser
    // surfaces the PSFB PLI/FIR on RtcpFeedback; the media session arms videoPipeline.requestKeyFrame() on
    // it). Only the OUTBOUND key-frame request is routed through this controller, in sendInteraction above.

    /**
     * Places an outbound one-to-one call to a peer's devices and rings them.
     *
     * <p>Allocates the call, generates its identifier, mints the call key and fans it out per peer device
     * inside the offer, builds and sends the offer, and on a positive ack records the caller's relay block.
     * The call enters {@link Calls2CallState#CALLING} with the initial result
     * {@link Calls2CallResult#CALL_OFFER_ACK_NOT_RECEIVED} and the caller-lonely timer armed; a positive
     * offer ack clears that initial result, and a NACK ends the call. The caller's media plane is brought
     * up only once the peer answers (the offer-ack relay credentials are rejected by the relay until the
     * accept arrives), so this method returns with the call ringing rather than connected. The returned
     * {@link Call} is the live view the application observes as the call progresses.
     *
     * @implNote This implementation reproduces {@code wa_call_start_call} (fn10711): it requires the
     * starting precondition (no call already live for the local side beyond the dual-call ceiling),
     * allocates the primary orchestration handle, generates the sixteen-byte call identifier, mints the
     * raw key through {@link CallKeyExchange#mintCallKey()}, and ships the offer. The caller-lonely
     * timer is the engine's ringing watchdog; the {@link Calls2CallResult#CALL_OFFER_ACK_NOT_RECEIVED}
     * initial result is the engine's {@code set_call_result(8)} on a freshly placed call, cleared on the
     * offer ack.
     *
     * @param self       the local account's device JID, stamped as the call creator
     * @param peer        the peer user JID being called
     * @param deviceJids  the peer device JIDs to fan the offer and call key out to
     * @param video       whether the call is placed with video enabled
     * @param streams     the application capture sources and playback sinks the media plane drives once the
     *                    peer answers, or {@link Calls2MediaStreams#none()} to fall back to platform devices
     * @return the live call view
     * @throws NullPointerException     if {@code self}, {@code peer}, {@code deviceJids}, or {@code streams}
     *                                  is {@code null}, or {@code deviceJids} contains a {@code null} element
     * @throws IllegalArgumentException if {@code peer} is a group or community JID, or {@code deviceJids}
     *                                  is empty
     * @throws IllegalStateException    if the dual-call ceiling is already reached, or one-to-one web calling
     *                                  is disabled for this account by the server feature gate
     * @throws WhatsAppCallException    if the offer could not be sent or no offer ack arrived
     */
    public Call startCall(Jid self, Jid peer, List<Jid> deviceJids, boolean video, Calls2MediaStreams streams) {
        Objects.requireNonNull(self, "self cannot be null");
        Objects.requireNonNull(peer, "peer cannot be null");
        Objects.requireNonNull(streams, "streams cannot be null");
        var devices = List.copyOf(deviceJids);
        if (peer.hasGroupOrCommunityServer()) {
            throw new IllegalArgumentException("startCall is for one-to-one calls; got group/community JID " + peer);
        }
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("deviceJids cannot be empty for an outbound offer");
        }
        var gate = featureGate;
        if (gate != null && !gate.isWebCallingEnabled()) {
            throw new IllegalStateException("Cannot start a call: web calling is disabled for this account");
        }
        admitNewCall();

        var callId = generateCallId();
        var call = new Call(callId, peer, peer, self, true, false, video, CallState.RINGING);
        var orchestrated = new Calls2OrchestratedCall(call, true, peer, Calls2CallState.NONE);
        orchestrated.peerDevices(devices);
        orchestrated.mediaStreams(streams);
        calls.put(callId, orchestrated);
        orchestrated.engineContext(registry.allocate(call, Calls2CallState.NONE));

        orchestrated.lock().lock();
        try {
            var callKey = crypto.mintCallKey();
            orchestrated.callKey(callKey);

            var offer = buildOffer(callId, self, devices, callKey, video, offerIdentity());
            transition(orchestrated, Calls2CallState.CALLING, CallEventType.CALL_OFFER_SENT);
            timers.arm(callId, Calls2CallTimerKind.CALLER_LONELY);
            timers.arm(callId, Calls2CallTimerKind.PERIODIC);
            timers.arm(callId, Calls2CallTimerKind.HEARTBEAT);

            var ack = sendOffer(callId, self, peer, offer);
            applyOfferAck(orchestrated, ack);
            return call;
        } catch (WhatsAppCallException e) {
            tearDown(orchestrated, CallEndReason.SETUP_FAILED, CallEventType.CALL_OFFER_SEND_FAILED);
            throw e;
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Places an outbound group call into a group, fanning the offer out over the selective-forwarding unit
     * and sharing the call key per participant.
     *
     * <p>Allocates the call and its {@link CallMembership} roster from {@code peers}, generates the call
     * identifier, and sends a group {@code <offer>} carrying the group JID, the {@code joinable} flag, and
     * the {@code <group_info>} roster (a group offer ships NO call key in the offer itself). The offer rides
     * the synchronous ack seam, and the positive ack carries the selective-forwarding unit's shared relay
     * block, which the controller records and reconciles its membership against and uses to bring up the
     * media plane. The controller then mints the thirty-two-byte call key and fans it out as a
     * per-participant {@code <enc_rekey>}, one unicast stanza per connected participant device. The call
     * enters {@link Calls2CallState#CALLING} for the offer leg, then {@link Calls2CallState#ACCEPT_SENT}
     * once the unit relay is known and the media plane is brought up (the media bring-up is underway), and
     * once the transport connects {@link #onMediaConnected(String)} settles it in
     * {@link Calls2CallState#CONNECTED_LONELY}, the group "lonely" state, advancing to
     * {@link Calls2CallState#CALL_ACTIVE} once a peer connects, driven by a later {@code <group_update>}
     * reconcile. This method returns with the offer acked and the media bring-up started. The returned
     * {@link Call}'s {@link Call#chatJid()} is the group.
     *
     * @implNote This implementation reproduces the outbound group-call placement
     * ({@code startWAWebVoipGroupCallFromWids} in the live capture method, over {@code wa_call_start_call}
     * fn10711 with a group target): the group offer carries the group JID and roster but no per-device key
     * fanout (SPEC section 8 and {@code re/calls2-spec/captures/group-sfu.json}: a group offer ships no call
     * key, the per-participant key arrives post-join as {@code <enc_rekey>}); the ack relay is the shared SFU
     * relay block with its {@code uuid}/{@code participant_uuid}/{@code key}/{@code hbh_key}; the key share is
     * the unicast {@code <enc_rekey>} fanout of {@code make_and_send_rekey_msg} (fn11448) confirmed in
     * {@code re/calls2-spec/captures/group-rekey.json} (one {@code <enc>} per recipient stanza, a single
     * thirty-two-byte key); and the {@link Calls2CallState#CONNECTED_LONELY}-versus-{@link Calls2CallState#CALL_ACTIVE}
     * settle on the media-connected step is the {@code post_process_group_info} (fn10987)
     * active-versus-lonely decision by connected-peer count. The selective-forwarding subscription publish (the {@code SenderSubscriptions} and
     * {@code RxSubscriptions} embedded in STUN binding-requests, SPEC section 14.3) rides the media plane's
     * transport rather than this signaling path, so it is reached through
     * {@link Calls2MediaPlane#bringUp(String, Stanza, List, byte[], boolean, boolean, int, CallMembership, Calls2MediaStreams, Jid, Optional)}
     * alongside the SRTP and SFrame key bring-up.
     *
     * @param self      the local account's device JID, stamped as the call creator
     * @param peers     the user JIDs of every other group participant the offer rosters and the key is
     *                  fanned out to
     * @param groupJid  the group JID the call belongs to
     * @param video     whether the call is placed with video enabled
     * @param streams   the application capture sources and playback sinks the media plane drives, or
     *                  {@link Calls2MediaStreams#none()} to fall back to platform devices
     * @return the live call view
     * @throws NullPointerException     if {@code self}, {@code peers}, {@code groupJid}, or {@code streams}
     *                                  is {@code null}, or {@code peers} contains a {@code null} element
     * @throws IllegalArgumentException if {@code peers} is empty, or the call's participant count (the peers
     *                                  plus the local user) exceeds the server group-call participant ceiling
     * @throws IllegalStateException    if the dual-call ceiling is already reached, or group calling is
     *                                  disabled for this account by the server feature gate
     * @throws WhatsAppCallException    if the offer could not be sent or no offer ack arrived
     */
    public Call startGroupCall(Jid self, Collection<Jid> peers, Jid groupJid, boolean video,
                               Calls2MediaStreams streams) {
        Objects.requireNonNull(self, "self cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(streams, "streams cannot be null");
        var participants = List.copyOf(peers);
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("peers cannot be empty for an outbound group offer");
        }
        var gate = featureGate;
        if (gate != null) {
            // isGroupCallsEnabled is the conjunction isWebCallingEnabled() && isWebGroupCallingEnabled(), so
            // this single check enforces both the one-to-one calling master gate and the web group-calling
            // master gate that must hold for a group call to start on web; the web-group-calling flag is
            // honoured here transitively rather than re-tested separately.
            if (!gate.isGroupCallsEnabled()) {
                throw new IllegalStateException(
                        "Cannot start a group call: group calling is disabled for this account");
            }
            // The participant count is the peers plus the local user; the gate's ceiling caps total
            // membership, so reject before allocating when the requested roster would exceed it.
            var requested = participants.size() + 1;
            var max = gate.groupCallMaxParticipants();
            if (requested > max) {
                throw new IllegalArgumentException("Cannot start a group call with " + requested
                        + " participants: the server ceiling is " + max);
            }
        }
        admitNewCall();

        var callId = generateCallId();
        var call = new Call(callId, groupJid, groupJid, self, true, true, video, CallState.CONNECTING);
        var orchestrated = new Calls2OrchestratedCall(call, true, null, Calls2CallState.NONE);
        var membership = new CallMembership(callId);
        membership.selfUserJid(self);
        orchestrated.membership(membership);
        orchestrated.groupOutbound(new GroupCallOutbound(callId, self, membership, host));
        var roster = rosterOf(participants);
        membership.reconcile(roster);
        orchestrated.peerDevices(participants);
        orchestrated.mediaStreams(streams);
        calls.put(callId, orchestrated);
        orchestrated.engineContext(registry.allocate(call, Calls2CallState.NONE));

        var groupTarget = Jid.of(callId + "@call");
        orchestrated.lock().lock();
        try {
            var callKey = crypto.mintCallKey();
            orchestrated.callKey(callKey);

            var offer = buildGroupOffer(callId, self, groupJid, roster, video, offerIdentity());
            transition(orchestrated, Calls2CallState.CALLING, CallEventType.CALL_OFFER_SENT);
            timers.arm(callId, Calls2CallTimerKind.CALLER_LONELY);
            timers.arm(callId, Calls2CallTimerKind.PERIODIC);
            timers.arm(callId, Calls2CallTimerKind.HEARTBEAT);

            var ack = sendOffer(callId, self, groupTarget, offer);
            applyOfferAck(orchestrated, ack);
            if (orchestrated.state() == Calls2CallState.ENDING || orchestrated.state() == Calls2CallState.NONE) {
                return call;
            }

            reconcileFromAck(orchestrated, ack);
            transition(orchestrated, Calls2CallState.ACCEPT_SENT, CallEventType.CALL_ACCEPT_SENT);
            orchestrated.relay().ifPresent(relay ->
                    bringUpMediaPlane(orchestrated, relay, orchestrated.offerAckVoipSettings(), callKey, true, video));
            fanOutGroupRekey(orchestrated, self, callKey);
            timers.arm(callId, Calls2CallTimerKind.UPDATE_ENCRYPTION_KEY);
            ensureControls(orchestrated);
            return call;
        } catch (WhatsAppCallException e) {
            tearDown(orchestrated, CallEndReason.SETUP_FAILED, CallEventType.CALL_OFFER_SEND_FAILED);
            throw e;
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Accepts an inbound offer the local user answered, bringing up the call's media plane.
     *
     * <p>Validates that the call exists and is in an answerable state, brings up the media plane from the
     * relay block the offer or a later group update carried (deriving the per-direction SRTP and the
     * per-participant SFrame keys from the call key the offer delivered), sends the accept, transitions to
     * {@link Calls2CallState#ACCEPT_SENT} with the result {@link Calls2CallResult#ACCEPTED}, and arms the
     * watchdog. A group accept addresses the call's MUC target rather than the creator device.
     *
     * @implNote This implementation reproduces {@code call_accept} / {@code call_accept_impl} (fn10717 /
     * fn10709): the state precondition (the call must not be ending or active-elsewhere), the media and
     * transport bring-up, the {@code change_call_state(4)} to {@link Calls2CallState#ACCEPT_SENT}, the
     * {@code set_call_result(10)} accepted result, and the accept send. The native inline audio-device,
     * media, transport, and BWE start is reached here through
     * {@link Calls2MediaPlane#bringUp(String, Stanza, List, byte[], boolean, boolean, int, CallMembership, Calls2MediaStreams, Jid, Optional)}.
     *
     * @param callId  the identifier of the call being accepted
     * @param video   whether the local side answers with video enabled
     * @param streams the application capture sources and playback sinks the media plane drives, or
     *                {@link Calls2MediaStreams#none()} to fall back to platform devices
     * @return the live call view
     * @throws NullPointerException     if {@code callId} or {@code streams} is {@code null}
     * @throws IllegalArgumentException if no call exists for {@code callId}
     * @throws IllegalStateException    if the call is not in an answerable state
     * @throws WhatsAppCallException    if the media plane cannot be brought up
     */
    public Call acceptCall(String callId, boolean video, Calls2MediaStreams streams) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(streams, "streams cannot be null");
        var orchestrated = require(callId);
        orchestrated.lock().lock();
        try {
            var state = orchestrated.state();
            if (state == Calls2CallState.ENDING || state == Calls2CallState.CALL_ACTIVE_ELSEWHERE) {
                throw new IllegalStateException("Cannot accept call " + callId + " in state " + state);
            }
            orchestrated.mediaStreams(streams);
            var call = orchestrated.call();
            var relay = orchestrated.relay().orElse(null);
            var callKey = orchestrated.callKey().orElse(null);
            if (relay != null && callKey != null) {
                var voipSettings = orchestrated.incomingOffer()
                        .map(OfferStanza::voipSettings)
                        .orElseGet(List::of);
                bringUpMediaPlane(orchestrated, relay, voipSettings, callKey, false, video);
            }

            var creator = call.creator();
            var to = call.isGroup() ? Jid.of(callId + "@call") : orchestrated.peerDeviceJid().orElse(call.peer());
            var offerVideo = orchestrated.incomingOffer().map(offer -> !offer.videoCodecs().isEmpty()).orElse(false);
            var accept = buildAccept(callId, creator, relay, offerVideo);
            // The accept is shipped fire-and-forget: unlike the offer's synchronous relay-bearing ack, the
            // <ack class="call" type="accept"> is asynchronous and is consumed by handleIncomingAck, which
            // ends the call on an accept NACK (404 -> CallDoesNotExistForRejoin, 434 -> CallIsFull).
            host.sendSignaling(Calls2CallStanza.toCall(accept, to, callId));

            transition(orchestrated, Calls2CallState.ACCEPT_SENT, CallEventType.CALL_ACCEPT_SENT);
            timers.arm(callId, Calls2CallTimerKind.PERIODIC);
            ensureControls(orchestrated);
            return call;
        } catch (WhatsAppCallException e) {
            tearDown(orchestrated, CallEndReason.SETUP_FAILED, CallEventType.CALL_ACCEPT_SEND_FAILED);
            throw e;
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Sends an early ring acknowledgement for an inbound offer before the user answers.
     *
     * <p>A callee device emits a preaccept after the offer and before the user answers, so the caller
     * learns the device is alerting and can begin early media preparation. The preaccept echoes the call's
     * audio profile and capability; it carries no key fanout or transport block, which arrive with the
     * accept. A group preaccept addresses the call's MUC target. This method does not change the call
     * state; the call stays ringing until the user accepts or rejects.
     *
     * @implNote This implementation reproduces {@code make_and_send_preaccept} (fn11453): it builds the
     * {@code <preaccept>} with the callee audio capabilities and the voip capability advertisement and
     * ships it fire-and-forget. The group end-to-end key and video capability the native group preaccept
     * additionally embeds are owned by the key-distribution and capability units rather than emitted here.
     *
     * @param callId the identifier of the inbound call to acknowledge
     * @throws NullPointerException     if {@code callId} is {@code null}
     * @throws IllegalArgumentException if no call exists for {@code callId}
     */
    public void preaccept(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var orchestrated = require(callId);
        orchestrated.lock().lock();
        try {
            var call = orchestrated.call();
            var creator = call.creator();
            var to = call.isGroup() ? Jid.of(callId + "@call") : orchestrated.peerDeviceJid().orElse(call.peer());
            var offerVideo = orchestrated.incomingOffer().map(offer -> !offer.videoCodecs().isEmpty()).orElse(false);
            var preaccept = buildPreaccept(callId, creator, offerVideo);
            host.sendSignaling(Calls2CallStanza.toCall(preaccept, to, callId));
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Declines an inbound offer before answering it.
     *
     * <p>Sends a reject for the call (or, when no call object exists for the identifier, a standalone
     * reject to the offer's creator), then tears the call down: the timers are cancelled, any media plane
     * is closed, the call leaves through {@link Calls2CallState#ENDING}, and the reject and ending events
     * are emitted. The reject reason is the wire literal vocabulary shared with terminate.
     *
     * @implNote This implementation reproduces {@code wa_call_reject} (fn10722) and, for the no-context
     * case, {@code wa_call_reject_without_call_ctx} (fn10723): the former sends a reject for an existing
     * context and transitions toward {@link Calls2CallState#ENDING}; the latter builds the peer JID from
     * the supplied creator and sends a standalone reject when no orchestration handle exists.
     *
     * @param callId      the identifier of the call to decline
     * @param callCreator the call creator's device JID, the reject recipient when no call object exists
     * @param reason      the decline reason
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code reason} is
     *                              {@code null}
     */
    public void rejectCall(String callId, Jid callCreator, CallEndReason reason) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            var reject = RejectStanza.of(callId, callCreator, reason);
            host.sendSignaling(Calls2CallStanza.toCall(reject, callCreator.toUserJid(), callId));
            return;
        }
        orchestrated.lock().lock();
        try {
            // Mark the call rejected so any inbound signaling that races the teardown is classified
            // IGNORE_REJECTED by the message router rather than acted on; once tearDown removes the handle
            // the router instead returns DROP for the now-unknown call, so this guards only the in-window
            // race while the rejected handle is still tracked.
            orchestrated.dedupState(orchestrated.dedupState().markRejected());
            var to = orchestrated.peerDeviceJid().orElse(callCreator.toUserJid());
            var reject = RejectStanza.of(callId, callCreator, reason);
            host.sendSignaling(Calls2CallStanza.toCall(reject, to, callId));
            tearDown(orchestrated, reason, CallEventType.CALL_REJECT_RECEIVED);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Joins an ongoing group call already identified by its call id and group JID.
     *
     * <p>Allocates the call and enters {@link Calls2CallState#LINK}, the group-call join phase. This is the
     * call-id-and-group-JID join entry, distinct from the call-link token join
     * ({@link #joinCallLink(Jid, String, CallLinkMedia, boolean, Calls2MediaStreams)}): it allocates the
     * orchestration handle and its {@link CallMembership} roster for a call whose id is already known and
     * parks it in the link phase. The returned {@link Call} is the live view of the joined call.
     *
     * @implNote This implementation reproduces the entry of {@code wa_call_join_ongoing_call} (fn10712): it
     * refuses to join while already in a call, allocates the orchestration handle and its
     * {@link CallMembership} roster, and sets the state to {@link Calls2CallState#LINK}. The token-driven
     * query-and-join handshake (the {@code change_call_link_state_no_event} sub-state advance and the
     * {@code post_process_group_info} active-versus-lonely decision) is driven by the token entry
     * {@link #joinCallLink(Jid, String, CallLinkMedia, boolean, Calls2MediaStreams)} through the per-call
     * {@link CallLinkController} and {@link WaitingRoomController}, not from this id-and-group-JID shape,
     * which carries no link token to query.
     * <p>TODO: the call-id-and-group-JID join leg (a join into an already-known ongoing group call without a
     * link token, the {@code wa_call_join_ongoing_call} non-link path) does not yet drive the post-LINK
     * accept from here; it stops at the LINK state until the non-token join sequencing (how a device that
     * already knows the call id obtains its relay and membership without a link query) is confirmed against a
     * live ongoing-group-call join capture.
     *
     * @param self     the local account's device JID
     * @param callId   the identifier of the ongoing call to join
     * @param groupJid the group JID hosting the call
     * @param video    whether the local user joins with video enabled
     * @return the live call view
     * @throws NullPointerException     if {@code self}, {@code callId}, or {@code groupJid} is
     *                                  {@code null}
     * @throws IllegalStateException    if a call is already live beyond the dual-call ceiling
     */
    public Call joinCall(Jid self, String callId, Jid groupJid, boolean video) {
        Objects.requireNonNull(self, "self cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        admitNewCall();
        var call = new Call(callId, groupJid, groupJid, self, true, true, video, CallState.CONNECTING);
        var orchestrated = new Calls2OrchestratedCall(call, true, null, Calls2CallState.NONE);
        var membership = new CallMembership(callId);
        membership.selfUserJid(self);
        orchestrated.membership(membership);
        orchestrated.groupOutbound(new GroupCallOutbound(callId, self, membership, host));
        calls.put(callId, orchestrated);
        orchestrated.engineContext(registry.allocate(call, Calls2CallState.NONE));
        orchestrated.lock().lock();
        try {
            transition(orchestrated, Calls2CallState.LINK, CallEventType.CALL_LINK_STATE_CHANGED);
            return call;
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Joins a call through a call-link token, running the query-and-join handshake and bringing up media.
     *
     * <p>Resolves the link token, requests admission, and answers the joined call. The flow follows the
     * native call-link join: the call is allocated and parked in {@link Calls2CallState#LINK}; the per-call
     * {@link CallLinkController} runs the {@code link_query} preview then the {@code link_join} request over
     * the bound {@link CallLinkIqSender}; the join ack's lobby participant list, when present, is surfaced
     * through the per-call {@link WaitingRoomController}; the join ack's {@code <relay>} subtree, when
     * present, is recorded as the call's relay; and the call is then answered through
     * {@link #acceptCall(String, boolean, Calls2MediaStreams)}, which sends the accept and brings the media
     * plane up once the relay and the call key are both known. A group join carries no call key in the join
     * ack (the per-participant key arrives post-join as an {@code <enc_rekey>}), so the accept ships and the
     * call sits in {@link Calls2CallState#ACCEPT_SENT} with the media plane brought up on the later rekey,
     * mirroring an outbound group placement.
     *
     * <p>The whole path is gated on {@link Calls2FeatureGate#isCallLinkEnabled()} when the feature gate is
     * bound: a build whose server flags disable call links refuses the join rather than running the
     * handshake, the same deny-by-throw the start, group-start, and screen-share entry points apply. The
     * call-link join requires the {@link CallLinkIqSender} bound (the query and join are blocking round
     * trips with no fire-and-forget fallback), so a controller built without it refuses the join.
     *
     * @implNote This implementation reproduces {@code wa_call_join_ongoing_call} (fn10712) over the
     * call-link token path: the {@code change_call_state} to {@link Calls2CallState#LINK}, the
     * {@code serialize_link_query}/{@code make_and_send_link_join} handshake (driven through the
     * {@link CallLinkController}), and the subsequent {@code call_accept_impl} (driven through
     * {@link #acceptCall(String, boolean, Calls2MediaStreams)}). The query is the
     * {@link CallLinkQueryAction#PREVIEW} lookup a joiner issues, and the join is the single {@code link_join}
     * stanza {@code make_and_send_link_join} (fn11454) sends: that serializer emits exactly one message with
     * one {@code join-state} attribute (default {@link #LINK_JOIN_STATE_DEFAULT}), never a two-leg sequence.
     * The joiner registers under WA's all-zeros placeholder call-id (the sentinel
     * {@code wa_call_preview_call_link} (fn11114) inits the call object with), runs the {@code link_join}
     * handshake, then adopts the relay-assigned identity the ack carries through {@link #adoptJoinAckIdentity}:
     * {@code deserialize_link_join_ack} (fn11681) parses a required {@code call-id}, a required
     * {@code call-creator}, and a required {@code <group_info>} roster, and {@code handle_link_join_ack}
     * (fn11501 -> {@code f11432}, gated on the id still being the placeholder) overwrites the call-id,
     * re-registers the call, and applies the creator. This method mirrors that: it re-keys the {@code calls}
     * registration and the engine context to the resolved id, swaps in a rebuilt {@link Call} under the
     * resolved id and creator, and builds the {@link CallMembership} (whose per-device SSRCs derive from the
     * call-id) under the resolved id, seeded from the ack's {@code <group_info>}.
     *
     * @param self    the local account's device JID, stamped as the joined call's creator placeholder (the
     *                join ack's real creator is not applied to the immutable {@link Call})
     * @param token   the call-link token to join
     * @param media   the media kind the caller intends to use, carried on the link query and join
     * @param video   whether the local user joins with video enabled
     * @param streams the application capture sources and playback sinks the media plane drives, or
     *                {@link Calls2MediaStreams#none()} to fall back to platform devices
     * @return the live call view
     * @throws NullPointerException     if {@code self}, {@code token}, {@code media}, or {@code streams} is
     *                                  {@code null}
     * @throws IllegalStateException    if the dual-call ceiling is already reached, if call links are
     *                                  disabled for this account by the server feature gate, or if the
     *                                  call-link IQ sender is not wired
     * @throws WhatsAppCallException    if the media plane cannot be brought up
     */
    public Call joinCallLink(Jid self, String token, CallLinkMedia media, boolean video,
                             Calls2MediaStreams streams) {
        Objects.requireNonNull(self, "self cannot be null");
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(media, "media cannot be null");
        Objects.requireNonNull(streams, "streams cannot be null");
        var gate = featureGate;
        if (gate != null && !gate.isCallLinkEnabled()) {
            throw new IllegalStateException(
                    "Cannot join a call link: call links are disabled for this account");
        }
        var iqSender = callLinkIqSender;
        if (iqSender == null) {
            throw new IllegalStateException(
                    "Cannot join a call link: the call-link IQ sender is not wired");
        }
        admitNewCall();

        // WA mints the joiner's call object at preview (wa_call_preview_call_link, fn11114) under the
        // all-zeros sentinel call-id, registers it, runs the link_join handshake, then adopts the
        // relay-assigned id and creator from the join ack (handle_link_join_ack, fn11501 -> f11432, gated on
        // the id still being the placeholder). This mirrors that: register under the placeholder, transition
        // to LINK, run the handshake, then adopt the resolved identity in adoptJoinAckIdentity. The
        // membership and outbound-group unit are built there, under the resolved id, because the membership's
        // per-device SSRCs derive from the call-id.
        var callId = PLACEHOLDER_CALL_ID;
        var call = new Call(callId, self, self, self, true, true, video, CallState.CONNECTING);
        var orchestrated = new Calls2OrchestratedCall(call, true, null, Calls2CallState.NONE);
        orchestrated.mediaStreams(streams);
        calls.put(callId, orchestrated);
        orchestrated.engineContext(registry.allocate(call, Calls2CallState.NONE));

        orchestrated.lock().lock();
        try {
            transition(orchestrated, Calls2CallState.LINK, CallEventType.CALL_LINK_STATE_CHANGED);

            // The link sink reads the call's id at emit (orchestrated::callId), so the LINK-phase events fire
            // under the placeholder and post-adoption events under the relay-assigned id, following the re-key.
            CallEventSink linkSink = eventBus == null ? event -> { } : new ControlEventBridge(orchestrated::callId, eventBus);
            var linkController = new CallLinkController(iqSender, linkSink);

            linkController.preview(token, media);
            // The join is a single link_join stanza, which is faithful: the native make_and_send_link_join
            //  (fn11454) sends exactly one 0x138-byte LinkJoin message carrying one join-state attribute, not
            //  a two-leg sequence. The join-state value is 2 by default and 1 when a preview-parameter flag
            //  byte (call-context +0xA1437, copied from +0x400681) is clear (fn11454; WASM select, byte!=0
            //  selects 2).
            // TODO: confirm which previewCallLink boolean selects join-state 1 versus the default 2. The flag
            //  at call-context +0xA1437 originates from a previewCallLink parameter byte but no symbol or
            //  string names it, it is provably distinct from the rejoin flag (+661496), and captures contain
            //  ZERO link_join stanzas, so the 1-versus-2 selection is genuinely unrecoverable. This is NOT a
            //  missing second leg.
            var joinAck = linkController.join(token, LINK_JOIN_STATE_DEFAULT);
            // Adopt the relay-assigned call-id and creator from the ack, re-keying the placeholder
            // registration, and seed the membership (its per-device SSRCs derive from the call-id) from the
            // ack's <group_info>. The ack carries no relay (fn11681 has no fill_relay_info/<relay>/auth_token);
            // the joiner's relay arrives via a later <group_update>.
            callId = adoptJoinAckIdentity(orchestrated, callId, joinAck, video, self);

            var context = CallControlContext.group(callId, orchestrated.call().creator(), self);
            var waitingRoomController = new WaitingRoomController(context, iqSender, linkSink);
            if (!joinAck.waitingRoomUsers().isEmpty()) {
                waitingRoomController.onWaitingRoomUpdate(joinAck.waitingRoomUsers());
            }

            acceptCall(callId, video, streams);
            installLinkControls(orchestrated, linkController, waitingRoomController);
            return orchestrated.call();
        } catch (RuntimeException e) {
            // A handshake failure (the link query or join IQ throwing) leaves the call allocated but not yet
            // answered, so tear it down here. A media bring-up failure inside acceptCall has already torn the
            // call down and removed it from the registry, so the still-tracked guard avoids a double teardown.
            if (calls.containsKey(callId)) {
                tearDown(orchestrated, CallEndReason.SETUP_FAILED, CallEventType.CALL_IS_ENDING);
            }
            throw e;
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Adopts the relay-assigned identity a call-link join ack carries, re-keying the call from its all-zeros
     * placeholder registration to the resolved id.
     *
     * <p>Rebuilds the public {@link Call} under the resolved call-id and the ack's creator (carrying the
     * live phase and mute flags forward), swaps it into the orchestration handle, moves the {@code calls}
     * registration from the placeholder id to the resolved id, and re-homes the engine context under the
     * resolved id. It then builds the call's {@link CallMembership} under the resolved id, seeded from the
     * ack's {@code <group_info>} roster, and the outbound-group unit against it. The membership is built
     * here rather than at the placeholder registration because its per-device secure SSRCs derive from the
     * call-id, so it must key on the resolved id.
     *
     * @implNote WA's {@code handle_link_join_ack} (fn11501 -> {@code f11432}) re-keys the one flat call
     * struct in place: it overwrites the call-id buffer ({@code callObj+1380}) with the ack's id, re-registers
     * the call in the active-calls map, and writes the ack's creator ({@code callObj+1312}), gated on the id
     * still being the all-zeros placeholder ({@code 830675}). Cobalt's call state is composed rather than
     * flat, so this rebuilds the {@link Call}, {@link CallMembership}, and {@link GroupCallOutbound} under the
     * resolved id and re-homes the engine context through the {@link Calls2CallContextRegistry}
     * (release-then-allocate, since the registry keys by call-id and the LINK phase arms no timer state to
     * carry over) for the same effect.
     *
     * @param orchestrated  the joined call's orchestration handle, registered under {@code placeholderId}
     * @param placeholderId the all-zeros placeholder id the call is currently registered under
     * @param joinAck       the decoded join acknowledgement carrying the relay-assigned identity
     * @param video         whether the local user joined with video enabled
     * @param self          the local account's device JID, the membership's self and the outbound unit's
     *                      creator
     * @return the resolved call-id the call is now registered under
     */
    private String adoptJoinAckIdentity(Calls2OrchestratedCall orchestrated, String placeholderId,
                                        LinkJoinAck joinAck, boolean video, Jid self) {
        var resolvedId = joinAck.callId();
        var creator = joinAck.callCreator();
        var target = Jid.of(resolvedId + "@call");
        var placeholderCall = orchestrated.call();
        var resolvedCall = new Call(resolvedId, target, target, creator, true, true, video, placeholderCall.state());
        resolvedCall.setAudioMuted(placeholderCall.isAudioMuted());
        resolvedCall.setVideoMuted(placeholderCall.isVideoMuted());
        orchestrated.call(resolvedCall);
        calls.remove(placeholderId);
        calls.put(resolvedId, orchestrated);
        registry.release(placeholderId);
        orchestrated.engineContext(registry.allocate(resolvedCall, orchestrated.state()));
        var membership = new CallMembership(resolvedId);
        membership.selfUserJid(self);
        membership.reconcile(joinAck.groupInfo());
        orchestrated.membership(membership);
        orchestrated.groupOutbound(new GroupCallOutbound(resolvedId, self, membership, host));
        return resolvedId;
    }

    /**
     * Folds a call's call-link and waiting-room control units into its stored in-call control bundle.
     *
     * <p>The call-link join builds the {@link CallLinkController} and {@link WaitingRoomController} to run the
     * query-and-join handshake before the call is answered, then answers the call, which builds the base
     * {@link Calls2CallControls} bundle (the four signaling-plane units, plus the app-data units when the
     * media plane is up). This merges the two link units into that bundle so they are held for the call's
     * lifetime, reachable by the host-side waiting-room admit and deny, and dropped on teardown with the rest
     * of the bundle. When no base bundle was built (the event bus is unbound, so the call carries no in-call
     * control units), the link units are dropped with the rest, since they have no holder; the link handshake
     * they performed has already completed by then. Must be called under the call's lock.
     *
     * @param orchestrated  the call whose link controls are folded in
     * @param linkController the call-link control unit built for the join handshake
     * @param waitingRoom   the waiting-room control unit built for the join handshake
     */
    private static void installLinkControls(Calls2OrchestratedCall orchestrated, CallLinkController linkController,
                                            WaitingRoomController waitingRoom) {
        orchestrated.controls()
                .map(base -> base.withCallLink(linkController, waitingRoom))
                .ifPresent(orchestrated::controls);
    }

    /**
     * Ends a live call, sending a terminate and tearing it down.
     *
     * <p>Sends a terminate carrying the end reason to the peer (fanned out to the call's peer devices for
     * an outbound call, or addressed to the group call target for a group call), then cancels the call's
     * timers, closes its media plane, leaves through {@link Calls2CallState#ENDING} to
     * {@link Calls2CallState#NONE}, and emits the ending event. Ending a call that does not exist is a no-op.
     *
     * @implNote This implementation reproduces {@code call_manager_end_call} (fn10733): it sends the
     * terminate, then stops every per-call timer ({@code stop_call_timer_worker_thread}, fn10952), frees
     * the orchestration handle, and clears the manager slot. The dual-call end order (secondary first,
     * then primary) is the manager's responsibility when it ends both; this method ends one call by
     * identifier.
     *
     * @param callId the identifier of the call to end
     * @param reason the end reason carried on the terminate
     * @throws NullPointerException if {@code callId} or {@code reason} is {@code null}
     */
    public void endCall(String callId, CallEndReason reason) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            var call = orchestrated.call();
            var creator = call.creator();
            var destination = orchestrated.peerDevices();
            var to = call.isGroup()
                    ? Jid.of(callId + "@call")
                    : orchestrated.peerDeviceJid().orElse(call.peer());
            var terminate = TerminateStanza.of(callId, creator, reason, destination);
            host.sendSignaling(Calls2CallStanza.toCall(terminate, to, callId));
            tearDown(orchestrated, reason, CallEventType.CALL_IS_ENDING);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Sends a mid-call group membership update adding or removing participants on an in-progress group call.
     *
     * <p>Routes the update through the call's outbound-group-call unit, which builds the
     * {@code <group_update>} carrying a {@code <group_info>} roster of the affected participants, ships it
     * fire-and-forget to {@code target}, and reconciles the call's {@link CallMembership} against the add or
     * remove. A call that is not tracked, or one that is not a group call (and so has no outbound-group-call
     * unit), is a no-op.
     *
     * @implNote This implementation drives the unit that owns the outbound group-membership path
     * ({@link GroupCallOutbound#sendGroupParticipants(Jid, Jid, List, boolean)}), so the membership update is
     * built and the call's roster reconciled in one place rather than inline in the call service. The unit
     * also owns the per-peer offer-send timestamps and the unanswered-offer sweep, so a participant added
     * here becomes a sweep candidate once the controller's rekey path fans the key to it.
     *
     * @param callId       the group call identifier
     * @param target       the group call target JID the update is addressed to
     * @param creator      the call-creator JID stamped on the update
     * @param participants the participants to add or remove; must be non-empty
     * @param added        {@code true} to add the participants, {@code false} to remove them
     * @throws NullPointerException     if {@code callId}, {@code target}, {@code creator}, or
     *                                  {@code participants} is {@code null}
     * @throws IllegalArgumentException if {@code participants} is empty
     */
    public void sendGroupParticipants(String callId, Jid target, Jid creator, List<Jid> participants,
                                      boolean added) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            orchestrated.groupOutbound()
                    .ifPresent(unit -> unit.sendGroupParticipants(target, creator, participants, added));
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Returns the outbound-group-call unit for a tracked group call, for the watchdog's unanswered-offer
     * sweep.
     *
     * <p>The per-call timer scheduler resolves the unit by call id on each watchdog tick and drives its
     * {@link GroupCallOutbound#sweepUnansweredOffers() sweep}; a call the controller does not track, or a
     * one-to-one call with no outbound-group-call unit, yields an empty result so the watchdog sweeps
     * nothing. The unit is resolved fresh per tick rather than captured at arm time, so a call torn down
     * between ticks is no longer swept.
     *
     * @param callId the call identifier
     * @return an {@link Optional} holding the call's outbound-group-call unit, or empty when none exists
     */
    Optional<GroupCallOutbound> groupOutbound(String callId) {
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return Optional.empty();
        }
        return orchestrated.groupOutbound();
    }

    /**
     * Processes an inbound offer, ringing a new call and recovering its call key.
     *
     * <p>Allocates the call from the offer, recovers the end-to-end call key from the offer's per-device
     * {@code <enc>} fanout (a one-to-one offer) or leaves it for a later group rekey, records the offer's
     * relay block, and rings: the call enters {@link Calls2CallState#RECEIVED_CALL}, or
     * {@link Calls2CallState#RECEIVED_CALL_WITHOUT_OFFER} when the offer carries no media descriptor. A group
     * offer additionally allocates the call's {@link CallMembership} and reconciles it against the offer's
     * {@code <group_info>} roster, so a subsequent inbound {@code <group_update>} reconciles against an
     * already-populated membership rather than being dropped. The returned {@link IncomingCall} is the
     * listener-facing offer the application accepts or rejects. An offer for a call that already exists, or
     * arriving while the dual-call ceiling is reached, is dropped and reported as an empty result.
     *
     * @implNote This implementation reproduces {@code wa_call_handle_incoming_xmpp_offer}: it allocates the
     * orchestration handle from the offer, Signal-decrypts the call key through
     * {@link CallKeyExchange#decryptCallKey(CallKeyDistribution, Jid)} (the sender of the envelope is
     * the offer's authoring device, supplied as {@code senderJid}), records the relay block, rings, and
     * sets the state to {@link Calls2CallState#RECEIVED_CALL} or {@link Calls2CallState#RECEIVED_CALL_WITHOUT_OFFER}
     * by whether the offer carried a media descriptor. A group offer's {@code <group_info>} establishes the
     * call's membership up front through {@link CallMembership#reconcile(GroupInfoStanza)}, mirroring the
     * native receive path seeding the participant set from the offer roster before the first
     * {@code <group_update>}. The busy-offer buffering into a pending-call context
     * ({@code handle_pending_call_offer}, fn10959) is owned by the pending-call unit; this method handles a
     * direct ringing offer.
     *
     * @param offer     the decoded inbound offer
     * @param senderJid the device JID that authored the offer envelope, used as the call-key decryption
     *                  sender
     * @return an {@link Optional} holding the listener-facing incoming call, or empty when the offer was
     *         dropped
     * @throws NullPointerException if {@code offer} or {@code senderJid} is {@code null}
     */
    public Optional<IncomingCall> handleIncomingOffer(OfferStanza offer, Jid senderJid) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var callId = offer.callId();
        if (calls.containsKey(callId)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Dropping duplicate offer for already-tracked call {0}", callId);
            return Optional.empty();
        }
        if (calls.size() >= MAX_CONCURRENT_CALLS) {
            // TODO: buffer the busy offer into a pending-call context (Calls2PendingCall) instead of
            //  dropping it, replaying its queued signaling once the user joins. The native
            //  handle_pending_call_offer (fn10959, call_waiting.cc) buffers a busy/group/lobby offer and its
            //  later messages into the singleton pending_call_ctx (DAT_ram_001508c4), but only when
            //  vp->enable_pending_call is set (its first gate); that flag is a per-call <voip_settings> field
            //  (VoipParamKey VP_ENABLE_PENDING_CALL, default 0/disabled), so with it unset the native engine
            //  also drops the offer here and this matches the default. Wiring the enabled path is blocked on
            //  two unrecovered details: (1) the incoming inbound-message router (Calls2IncomingMessageRouter)
            //  DROPs any non-offer message for a call-id not in the active-calls map, so the buffered call
            //  needs a separate holder the router and handleIncomingMessage consult before the DROP, and
            //  (2) the promotion-on-join sequencing (wa_call_join_ongoing_call, fn10712) that drains the
            //  buffer and replays it against the promoted context is the same path joinCall leaves unconfirmed
            //  (no live ongoing-group-call join capture). See Calls2PendingCall and joinCall.
            LOGGER.log(System.Logger.Level.DEBUG, "Dropping offer for {0}: dual-call ceiling reached", callId);
            return Optional.empty();
        }

        var creator = offer.callCreator();
        var group = offer.isGroup();
        var chatJid = offer.groupJidValue().orElse(creator.toUserJid());
        var call = new Call(callId, creator.toUserJid(), chatJid, creator, false, group, offer.isVideo(),
                CallState.RINGING);
        var orchestrated = new Calls2OrchestratedCall(call, false, senderJid, Calls2CallState.NONE);
        orchestrated.incomingOffer(offer);
        offer.relayNode().ifPresent(orchestrated::relay);
        if (group) {
            attachOfferMembership(orchestrated, offer);
        }
        calls.put(callId, orchestrated);
        orchestrated.engineContext(registry.allocate(call, Calls2CallState.NONE));

        orchestrated.lock().lock();
        try {
            recoverOfferCallKey(orchestrated, offer, senderJid);
            var ringingState = offer.mediaDescriptor().isPresent()
                    ? Calls2CallState.RECEIVED_CALL
                    : Calls2CallState.RECEIVED_CALL_WITHOUT_OFFER;
            transition(orchestrated, ringingState, CallEventType.CALL_OFFER_RECEIVED);

            // Begin the relay-latency exchange as soon as the offer's relay block is known so the peer's
            // report has arrived before this side answers and brings up its media plane, letting the election
            // converge both ends onto a shared relay rather than each picking its locally fastest one.
            startRelayLatencyExchange(orchestrated, orchestrated.relay().orElse(null));

            // The listener-facing call carries the caller's phone number (the server stamps caller_pn on
            // the delivered offer), not the LID the protocol addresses, so it matches the JID applications
            // hold for contacts; the internal Call keeps the LID creator for reply addressing.
            var callerForApp = offer.callerPnValue().map(Jid::toUserJid).orElse(creator.toUserJid());
            var chatForApp = group ? chatJid : callerForApp;
            var incoming = new IncomingCall(callId, callerForApp, chatForApp, java.time.Instant.now(),
                    offer.isVideo(), group, offer.groupJidValue().orElse(null), false);
            return Optional.of(incoming);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Classifies one decoded inbound signaling action through the message router, then dispatches it to its
     * lifecycle handler on a routable verdict.
     *
     * <p>This is the inbound seam the signaling receiver forwards every routable {@link CallMessage} to,
     * after the receiver has validated the envelope header, classified the raw envelope, and emitted its
     * acknowledgement. The controller first runs the finer per-message {@link Calls2IncomingMessageRouter}
     * classification against live call state, threading the call's per-call de-duplication state in and out,
     * then acts on the {@link Calls2IncomingMessageRouter.RoutingClass} verdict:
     * <ul>
     *   <li>{@link Calls2IncomingMessageRouter.RoutingClass#PROCESS PROCESS} dispatches on the message type
     *       to the matching phase transition (an offer rings a new call, a preaccept marks the peer
     *       alerting, an accept starts the local bring-up, a reject or terminate ends the call, a transport
     *       message advances the media plane);</li>
     *   <li>{@link Calls2IncomingMessageRouter.RoutingClass#OFFER_RERING OFFER_RERING} routes the re-ringing
     *       offer back through {@link #handleIncomingOffer(OfferStanza, Jid)}, which detects the already-tracked
     *       call and drops the duplicate offer body (the offer acknowledgement the re-ring needs was already
     *       re-sent by the receiver before this dispatch);</li>
     *   <li>{@link Calls2IncomingMessageRouter.RoutingClass#ACCEPT_HANDLE ACCEPT_HANDLE} routes the accept onto
     *       the accept-received bring-up path;</li>
     *   <li>{@link Calls2IncomingMessageRouter.RoutingClass#IGNORE IGNORE},
     *       {@link Calls2IncomingMessageRouter.RoutingClass#IGNORE_REJECTED IGNORE_REJECTED}, and
     *       {@link Calls2IncomingMessageRouter.RoutingClass#DROP DROP} drop the message without dispatch
     *       (a duplicate or stale transaction id, signaling for a call the local user already rejected, and a
     *       message naming no resolvable call respectively).</li>
     * </ul>
     * A dispatched message advances the call's recorded transaction id once the call exists. A message type
     * the controller does not act on at this layer is logged and ignored.
     *
     * <p>The boolean return reports only whether an inbound {@link TerminateStanza} was effected, that is,
     * whether {@link #handlePeerTerminate(TerminateStanza, Jid)} tore the call down rather than suppressing
     * the teardown behind a guard. It is {@code true} only for a terminate the dispatch acted on, and
     * {@code false} for every other message type, for a terminate the router dropped as a duplicate or stale
     * transaction, and for a terminate a guard ignored. The call service reads it to fire the host
     * {@code onCallEnded} fan-out only for an effected terminate, since the engine's terminate handler is the
     * point the native engine fires its end-of-call host event from and it fires it only after the ignore
     * guards pass.
     *
     * @implNote This implementation runs {@code message_router} (fn11497) through
     * {@link Calls2IncomingMessageRouter} before the per-type dispatch of
     * {@code wa_call_handle_incoming_signaling_xmpp_msg} (fn10724): the router validates LID addressing,
     * de-duplicates by {@code (type, call-id, transaction-id)}, detects an offer re-ring, and suppresses
     * signaling for a rejected call, returning one of the six routing classes the native dispatcher branches
     * on. The authoring-device {@code senderJid} (the {@code <call>} envelope {@code from}) is passed as the
     * router's LID-addressing signal, matching the controller's existing use of it as the peer signaling
     * device, so a message whose authoring device or call creator is LID-addressed clears the router's LID
     * gate exactly as the inline path (which applied no LID gate of its own) admitted it. The PROCESS branch
     * dispatches {@link OfferStanza} to {@link #handleIncomingOffer(OfferStanza, Jid)}, {@link PreacceptStanza}
     * to the {@link Calls2CallState#PREACCEPT_RECEIVED} transition, {@link AcceptStanza} to the accept-received
     * bring-up, {@link RejectStanza} and {@link TerminateStanza} to {@code handle_terminate} (fn11492) and
     * teardown, {@link TransportStanza} to the transport advance, and {@link GroupUpdateStanza} to the
     * membership reconcile and active-versus-lonely re-decision ({@code post_process_group_info}, fn10987).
     * The remaining in-call action types (mute, video-state, screen-share, reactions, standalone rekey) are
     * dispatched to the control units the controller delegates to and are not all handled inline here. The
     * per-type handlers are unchanged; only dispatch is gated on the router verdict and the dedup state is
     * threaded around it.
     *
     * @param message   the decoded inbound action
     * @param senderJid the device JID that authored the message envelope, used as the decryption sender,
     *                  the peer signaling device, and the router's LID-addressing signal
     * @return {@code true} when the message was an inbound terminate the dispatch tore the call down for;
     *         {@code false} for every other message type, a router-dropped terminate, or a terminate a guard
     *         ignored
     * @throws NullPointerException if {@code message} or {@code senderJid} is {@code null}
     */
    public boolean handleIncomingMessage(CallMessage message, Jid senderJid) {
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(senderJid, "senderJid cannot be null");
        var callId = message.toStanza().getAttributeAsString("call-id", null);
        var dedup = callId == null
                ? Calls2IncomingMessageRouter.DedupState.INITIAL
                : Optional.ofNullable(calls.get(callId))
                        .map(Calls2OrchestratedCall::dedupState)
                        .orElse(Calls2IncomingMessageRouter.DedupState.INITIAL);
        var verdict = incomingRouter.route(message, senderJid, dedup, calls::get);
        switch (verdict.routingClass()) {
            case PROCESS, OFFER_RERING, ACCEPT_HANDLE -> {
                var terminateEffected = dispatchInbound(message, senderJid);
                advanceDedup(message, callId);
                return terminateEffected;
            }
            case IGNORE, IGNORE_REJECTED, DROP -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Router verdict {0} drops inbound call message {1} for call {2}",
                    verdict.routingClass(), message.type(), callId);
        }
        return false;
    }

    /**
     * Dispatches a router-admitted inbound message to its per-type lifecycle handler.
     *
     * <p>Switches the decoded {@link CallMessage} onto the handler that owns its phase transition; this is
     * the per-type dispatch the inline inbound path performed before the {@link Calls2IncomingMessageRouter}
     * gate was added, unchanged. A message type the controller does not act on at this layer is logged and
     * ignored.
     *
     * <p>The boolean return is meaningful only for a {@link TerminateStanza}: it forwards whether
     * {@link #handlePeerTerminate(TerminateStanza, Jid)} tore the call down rather than suppressing the
     * teardown behind a guard. Every other message type returns {@code false}, since only a terminate teardown
     * drives the host end-of-call notification.
     *
     * @param message   the router-admitted inbound action
     * @param senderJid the device JID that authored the message envelope
     * @return {@code true} when the message was a terminate that tore the call down; {@code false} otherwise
     */
    private boolean dispatchInbound(CallMessage message, Jid senderJid) {
        switch (message) {
            case OfferStanza offer -> handleIncomingOffer(offer, senderJid);
            case PreacceptStanza preaccept -> handlePeerPreaccept(preaccept);
            case AcceptStanza accept -> handlePeerAccept(accept, senderJid);
            case RejectStanza reject -> handlePeerReject(reject, senderJid);
            case TerminateStanza terminate -> {
                return handlePeerTerminate(terminate, senderJid);
            }
            case TransportStanza transport -> handlePeerTransport(transport);
            case RelayLatencyStanza relayLatency -> handlePeerRelayLatency(relayLatency, senderJid);
            case GroupUpdateStanza groupUpdate -> handleGroupUpdate(groupUpdate);
            case MuteV2Stanza mute -> handlePeerMute(mute, senderJid);
            case VideoStateStanza videoState -> handlePeerVideoState(videoState, senderJid);
            case ScreenShareStanza screenShare -> handlePeerScreenShare(screenShare, senderJid);
            case RaiseHandStanza raiseHand -> handlePeerRaiseHand(raiseHand, senderJid);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "No inline lifecycle handling for call message {0}", message.type());
        }
        return false;
    }

    /**
     * Handles an inbound asynchronous call ack: the server confirmed or rejected a fire-and-forget leg.
     *
     * <p>The offer ack is the offer send's synchronous return value, so the only ack that reaches here is
     * the accept's: shipped fire-and-forget, it arrives later as a top-level {@code <ack class="call"
     * type="accept">}. A positive ack needs no action, because the answered call is already brought up and
     * advancing. A NACK ends the answered call: the server {@code error} is mapped onto a
     * {@link Calls2CallResult}, that result's {@link Calls2CallResult#toEndReason() end reason} ends the
     * call, and {@link CallEventType#HANDLE_ACCEPT_ACK_FAILED} is fired. An ack for an untracked call is
     * ignored.
     *
     * @implNote This implementation ports the accept-ack NACK leg of {@code handle_accept_ack} (fn11502,
     * {@code messages/handlers/basic.cc}): it logs {@code "Accept ACK: error code = %d. Treating as an Accept
     * NACK"}, maps server error {@code 404} to {@link Calls2CallResult#CALL_DOES_NOT_EXIST_FOR_REJOIN} and
     * {@code 434} to {@link Calls2CallResult#CALL_IS_FULL} (any other error sets no result code but still ends
     * the call), then ends the call through {@link #tearDown(Calls2OrchestratedCall, CallEndReason,
     * CallEventType)} firing {@link CallEventType#HANDLE_ACCEPT_ACK_FAILED} (native event id {@code 0x4f}).
     * The mapped result is not written onto the engine context here, since the controller never mutates the
     * context (the state units own it) and the info manager derives the snapshot result from the dispatched
     * event; the result instead drives the public end reason and the diagnostic log.
     *
     * @param outcome the decoded inbound call ack
     * @throws NullPointerException if {@code outcome} is {@code null}
     */
    public void handleIncomingAck(CallAckOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome cannot be null");
        if (outcome.isAck()) {
            return;
        }
        var orchestrated = calls.get(outcome.id());
        if (orchestrated == null) {
            return;
        }
        var error = outcome.error().getAsInt();
        orchestrated.lock().lock();
        try {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Accept ACK: error code = {0}. Treating as an Accept NACK", error);
            var reason = Calls2CallResult.fromAcceptAckError(error)
                    .map(Calls2CallResult::toEndReason)
                    .orElse(CallEndReason.SETUP_FAILED);
            tearDown(orchestrated, reason, CallEventType.HANDLE_ACCEPT_ACK_FAILED);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Sends one call-liveness heartbeat for the call.
     *
     * <p>The {@link Calls2CallTimerKind#HEARTBEAT} timer invokes this on its recovered cadence. A heartbeat
     * is emitted while the call is active ({@link Calls2CallState#CALL_ACTIVE} or
     * {@link Calls2CallState#CONNECTED_LONELY}), for both one-to-one and group calls, so a tick that fires
     * during setup or after teardown sends nothing. The action is a content-less
     * {@code <heartbeat call-id call-creator/>} addressed to the call's {@code <callId>@call} target and
     * written fire-and-forget, like the in-call control actions; it takes no call lock, since the timer
     * driver runs the tick off the lock the way the native timer callback does.
     *
     * @implNote A one-to-one call heartbeats too, not only a group call: a connected one-to-one peer tears
     * the call down with {@code reason=timeout} when the periodic heartbeat stops, as the working WhatsApp
     * Web reference client sends it for every call (not only groups).
     *
     * @param callId the identifier of the call to heartbeat; never {@code null}
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    void sendHeartbeat(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        if (host == null) {
            return;
        }
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        var call = orchestrated.call();
        var state = orchestrated.state();
        if (state != Calls2CallState.CALL_ACTIVE && state != Calls2CallState.CONNECTED_LONELY) {
            return;
        }
        host.sendSignaling(Calls2CallStanza.toCall(
                new HeartbeatStanza(callId, call.creator()), Jid.of(callId + "@call"), callId));
    }

    /**
     * Advances the recorded inbound transaction id for a call after a routed message is dispatched.
     *
     * <p>Reads the dispatched message's {@code transaction-id} attribute and folds it into the call's
     * {@linkplain Calls2OrchestratedCall#dedupState() de-duplication state} through
     * {@link Calls2IncomingMessageRouter.DedupState#withTransactionId(int)}, so a later replay of the same
     * or an older transaction id is classified as a duplicate. A message that carries no transaction id, or
     * one whose call the dispatch did not create or that has since been torn down, advances nothing.
     *
     * @implNote This implementation advances the dedup state only after the per-type handler has run, so an
     * offer that created the call (the router returns its verdict with no resolved context for a fresh offer)
     * is found here by its now-tracked handle; the {@link Calls2IncomingMessageRouter.DedupState#withTransactionId(int)}
     * step keeps the newest transaction id and leaves a stale or absent one unchanged, matching the native
     * router's per-call seen-id high-water mark.
     *
     * @param message the dispatched inbound action
     * @param callId  the message's call id, or {@code null} when it carried none
     */
    private void advanceDedup(CallMessage message, String callId) {
        if (callId == null) {
            return;
        }
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        var transactionId = message.toStanza().getAttributeAsInt("transaction-id", -1);
        if (transactionId < 0) {
            return;
        }
        orchestrated.lock().lock();
        try {
            orchestrated.dedupState(orchestrated.dedupState().withTransactionId(transactionId));
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Marks a call active once its media plane carries traffic in both directions.
     *
     * <p>The media-plane and transport unit calls this when the call's transport reaches the
     * bidirectional-traffic state: the controller moves the call from its accept leg
     * ({@link Calls2CallState#ACCEPT_SENT} or {@link Calls2CallState#ACCEPT_RECEIVED}) to
     * {@link Calls2CallState#CALL_ACTIVE}, the "marking call active" step, and the transition guard
     * captures the active-duration start and cancels the connected-lonely timer. A one-to-one call goes
     * active when peer media flows; a group call goes active when at least one peer connects, otherwise it
     * sits in {@link Calls2CallState#CONNECTED_LONELY}. A signal for an untracked call is ignored.
     *
     * @implNote This implementation reproduces the {@code change_call_state(6)} to
     * {@link Calls2CallState#CALL_ACTIVE} the engine performs from {@code call_accept_impl} (fn10709) once
     * transport and media connect with peer media present, and from {@code post_process_group_info}
     * (fn10987) once a group peer connects. The transport-state source is the {@code kTxTrafficStart}
     * notification of {@code call_transport.cc}, surfaced here as a call from the media-plane unit.
     *
     * @param callId the identifier of the call whose media plane became bidirectional
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void onMediaConnected(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            var state = orchestrated.state();
            if (state != Calls2CallState.ACCEPT_SENT && state != Calls2CallState.ACCEPT_RECEIVED
                    && state != Calls2CallState.CONNECTED_LONELY && state != Calls2CallState.REJOINING) {
                return;
            }
            transition(orchestrated, mediaConnectedTarget(orchestrated), CallEventType.CALL_STATE_CHANGED);
            ensureControls(orchestrated);
            announceInitialVideoState(orchestrated);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Returns the in-call state a call enters when its media plane becomes bidirectional.
     *
     * <p>A one-to-one call goes straight to {@link Calls2CallState#CALL_ACTIVE} once peer media flows. A
     * group call goes to {@link Calls2CallState#CALL_ACTIVE} only when at least one other participant is
     * connected; with no connected peer it settles in {@link Calls2CallState#CONNECTED_LONELY}, the group
     * "lonely" state, until a peer joins.
     *
     * @implNote This implementation reproduces the {@code post_process_group_info} (fn10987)
     * active-versus-lonely decision for the media-connected step: a group call's connected-peer presence
     * selects the target, while a one-to-one call has no roster and is always active on connect. The
     * connected-peer test reads the call's {@link CallMembership#participantProvider() participant provider}
     * ({@link com.github.auties00.cobalt.calls2.core.participant.ParticipantProvider#firstConnectedPeer()},
     * the {@code participant_provider.cc} first-connected-peer scan), whose per-slot
     * {@link com.github.auties00.cobalt.calls2.core.participant.CallParticipant} aggregates carry the
     * membership state projected from each roster entry's {@code "connected"} literal.
     *
     * @param orchestrated the call whose media plane connected
     * @return {@link Calls2CallState#CALL_ACTIVE}, or {@link Calls2CallState#CONNECTED_LONELY} for a group
     *         call with no connected peer
     */
    private static Calls2CallState mediaConnectedTarget(Calls2OrchestratedCall orchestrated) {
        if (!orchestrated.call().isGroup()) {
            return Calls2CallState.CALL_ACTIVE;
        }
        var anyPeerConnected = orchestrated.membership()
                .map(membership -> membership.participantProvider().firstConnectedPeer().isPresent())
                .orElse(false);
        return anyPeerConnected ? Calls2CallState.CALL_ACTIVE : Calls2CallState.CONNECTED_LONELY;
    }

    /**
     * Announces the local camera-on state once a video call's media plane connects.
     *
     * <p>A call placed or answered with video carries the camera on from the start, yet the peer renders
     * the local video only after it receives the camera-on {@code <video>} state announcement. This
     * broadcasts {@link VideoStreamState#ENABLED} through the call's {@link VideoStateController} the first
     * time the media plane connects, so the peer learns the camera is live and renders the inbound video;
     * an audio-only call announces nothing, and a reconnect that finds the camera already enabled
     * re-announces nothing.
     *
     * @implNote This implementation closes the from-start video gap: the native engine raises the camera
     * track and broadcasts the {@code <video>} state together at connect (the {@code video_state} announce
     * driven alongside {@code call_accept_impl}), whereas the streams-based bring-up here started the video
     * media without the accompanying state announce, so a peer that received the video RTP never learned
     * the camera was on. A mid-call camera toggle still flows through
     * {@link #startLocalVideo(String)}/{@link #setVideoEnabled(String, boolean)}.
     *
     * @param orchestrated the call whose media plane connected
     */
    private static void announceInitialVideoState(Calls2OrchestratedCall orchestrated) {
        if (!orchestrated.call().isVideo()) {
            return;
        }
        orchestrated.controls().ifPresent(controls -> {
            if (controls.video().state() != VideoStreamState.ENABLED) {
                controls.video().turnCamera(true);
            }
        });
    }

    /**
     * Marks a call reconnecting when its media plane loses its network path.
     *
     * <p>The media-plane and transport unit calls this when the call's transport loses connectivity: the
     * controller moves an active call to {@link Calls2CallState#REJOINING} so the transport can renegotiate
     * its relay or DTLS path; a successful renegotiation returns the call to
     * {@link Calls2CallState#CALL_ACTIVE} through {@link #onMediaConnected(String)}, while a failed one
     * ends it. A signal for an untracked call is ignored.
     *
     * @implNote This implementation reproduces the {@code change_call_state(9)} to
     * {@link Calls2CallState#REJOINING} the engine performs on a network-path loss, the internal
     * reconnecting analogue. The transport-state source is the traffic-stopped notification of
     * {@code call_transport.cc}, surfaced here as a call from the media-plane unit.
     *
     * @param callId the identifier of the call whose media plane lost its path
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public void onMediaLost(String callId) {
        Objects.requireNonNull(callId, "callId cannot be null");
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            if (orchestrated.state() == Calls2CallState.CALL_ACTIVE) {
                transition(orchestrated, Calls2CallState.REJOINING, CallEventType.CALL_STATE_CHANGED);
            }
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Handles an inbound preaccept: the peer device is alerting.
     *
     * <p>Marks an outbound call's state {@link Calls2CallState#PREACCEPT_RECEIVED} so the caller can begin
     * early media preparation, and emits the preaccept-received event. A preaccept for an untracked call is
     * ignored.
     *
     * @implNote This implementation reproduces the {@code Preaccept(13)} arm of the inbound dispatcher: the
     * {@code change_call_state(2)} to {@link Calls2CallState#PREACCEPT_RECEIVED} for a call in
     * {@link Calls2CallState#CALLING}.
     *
     * @param preaccept the decoded inbound preaccept
     */
    private void handlePeerPreaccept(PreacceptStanza preaccept) {
        var orchestrated = calls.get(preaccept.callId());
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            transition(orchestrated, Calls2CallState.PREACCEPT_RECEIVED, CallEventType.CALL_PREACCEPT_RECEIVED);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Handles an inbound accept: the peer answered an outbound call.
     *
     * <p>Records the answering device, moves an outbound call to {@link Calls2CallState#ACCEPT_RECEIVED},
     * and brings up the media plane from the relay block the accept (or the earlier offer ack) carried,
     * keying it with the call key the caller minted. A nested {@code <transport>} block in the accept
     * supplies the relay block when the offer ack did not. An accept for an untracked call is ignored.
     *
     * @implNote This implementation reproduces the {@code Accept(3)} / {@code AcceptReceipt(14)} arm: the
     * {@code change_call_state(5)} to {@link Calls2CallState#ACCEPT_RECEIVED} and the transport and media
     * bring-up. The answering device JID is recorded as the peer signaling device so subsequent
     * point-to-point signaling and the per-participant key derivation address the device that answered,
     * matching the engine keying media from the answering device JID.
     *
     * @param accept    the decoded inbound accept
     * @param senderJid the device JID that authored the accept, recorded as the answering device
     */
    private void handlePeerAccept(AcceptStanza accept, Jid senderJid) {
        var orchestrated = calls.get(accept.callId());
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            orchestrated.peerDeviceJid(senderJid);
            accept.transportNode()
                    .flatMap(transport -> transport.getChild("relay"))
                    .ifPresent(orchestrated::relay);
            transition(orchestrated, Calls2CallState.ACCEPT_RECEIVED, CallEventType.CALL_ACCEPT_RECEIVED);
            // The callee answered: cancel the caller-lonely no-answer timer armed at offer-send, otherwise it
            // later fires callerLonelyTimeout and tears the now-answered call down with reason=timeout. The
            // engine cancels caller_lonely_state_timer (fn10934) when the accept arrives.
            timers.cancel(accept.callId(), Calls2CallTimerKind.CALLER_LONELY);

            var relay = orchestrated.relay().orElse(null);
            var callKey = orchestrated.callKey().orElse(null);
            // Fallback for a caller whose synchronous offer ack carried no relay block: the accept's relay is
            // the first one learned, so start the exchange here. A no-op once applyOfferAck already started it.
            startRelayLatencyExchange(orchestrated, relay);
            if (relay != null && callKey != null) {
                bringUpMediaPlane(orchestrated, relay, orchestrated.offerAckVoipSettings(), callKey, true,
                        orchestrated.call().isVideo());
            }
            ensureControls(orchestrated);
        } catch (WhatsAppCallException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Media plane bring-up failed after peer accept for {0}: {1}", accept.callId(), e.getMessage());
            tearDown(orchestrated, CallEndReason.SETUP_FAILED, CallEventType.MEDIA_STREAM_ERROR);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Handles an inbound reject: a callee device declined the outbound call.
     *
     * <p>Ends the call with the reject's reason and emits the reject-received event, unless the reject comes
     * from a device that is not the active peer. On a multi-device callee every device rings, so once one
     * device has answered (its JID pinned as the {@linkplain Calls2OrchestratedCall#peerDeviceJid() active
     * peer} by {@link #handlePeerAccept(AcceptStanza, Jid)}), another of the callee's devices declining its
     * own ringing leg must not tear down the now-answered call; that reject is dropped. A reject from the
     * active peer itself, or a reject before any device has answered (no active peer pinned), ends the call.
     * A reject for an untracked call is ignored.
     *
     * @implNote This implementation reproduces the {@code Reject(4)} arm with the native active-device guard:
     * a per-device reject only ends the call when it concerns the device the call is established with, so a
     * sibling device dismissing its ringing notification does not end a call another device is already
     * carrying.
     *
     * @param reject    the decoded inbound reject
     * @param senderJid the device JID that authored the reject
     */
    private void handlePeerReject(RejectStanza reject, Jid senderJid) {
        var orchestrated = calls.get(reject.callId());
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            var activePeer = orchestrated.peerDeviceJid().orElse(null);
            if (activePeer != null && senderJid != null && !activePeer.equals(senderJid)) {
                return;
            }
            tearDown(orchestrated, reject.reason(), CallEventType.CALL_REJECT_RECEIVED);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Handles an inbound terminate: either side ended the call, unless an ignore guard suppresses it.
     *
     * <p>Ends the call with the terminate's reason and emits the terminate-received event. A terminate for
     * an untracked call is ignored. The reason maps onto the result axis through the info-manager update
     * fired by the teardown. Two guards can suppress the teardown so a stale or misdirected terminate does
     * not end an otherwise valid call:
     * <ul>
     *   <li>the companion-device guard drops a one-to-one-shaped terminate that arrives during a group
     *       call when its authoring device is one of the local account's own devices, so another of the
     *       user's devices ending its own one-to-one leg does not tear down the user's group call; and</li>
     *   <li>the joinable-on-expired-offer guard drops a terminate for a joinable (call-link) call whose
     *       offer has already expired, so a late terminate does not abort an otherwise valid join.</li>
     * </ul>
     * Each guard is gated on its server feature flag and on the data the guard needs; when the flag is
     * clear, the gate is unbound, or the guard's data is absent, the terminate ends the call as usual.
     *
     * @implNote This implementation reproduces the reason-to-result mapping and media teardown of
     * {@code handle_terminate} (fn11492, {@code call_state.cc}) and its two ignore guards. The
     * companion-device guard reproduces the {@code ctx[0x11c]} companion-flag path
     * ("ignoring terminate from companion device"): the native receive path sets the flag when the
     * terminate's sender is another device of the local account, which Cobalt resolves through the
     * {@linkplain #bindOwnDeviceResolver(Predicate) bound own-device predicate} over the authoring
     * {@code senderJid}, scoped (per {@link Calls2FeatureGate#isIgnoreOneToOneTerminateInGroupCall()}) to a
     * one-to-one terminate arriving during a group call. The joinable guard reproduces the native
     * state-and-reason shape: a joinable terminate is dropped when the call is {@link Calls2CallState#REJOINING}
     * or {@link Calls2CallState#ACCEPT_SENT}, or is outside {@link Calls2CallState#CALL_ACTIVE} and
     * {@link Calls2CallState#CONNECTED_LONELY} with a reason other than the handled-elsewhere reasons, gated by
     * the inbound terminate's joinable byte ({@code msg[0x118]}) and the {@code call_state_is} (fn10663)
     * membership of {@code handle_terminate} (fn11492); see
     * {@link #isIgnoredJoinableTerminate(Calls2OrchestratedCall, TerminateStanza)}.
     *
     * <p>The boolean return reports whether the terminate was effected: {@code true} when the call was torn
     * down, and {@code false} when the terminate was for an untracked call or when either ignore guard
     * suppressed the teardown. The call service reads it so the host {@code onCallEnded} fan-out fires only
     * for an effected terminate, matching the native engine where the end-of-call host event fires only after
     * the ignore guards pass.
     *
     * @param terminate the decoded inbound terminate
     * @param senderJid the device JID that authored the terminate envelope, the companion-device
     *                  discriminator, or {@code null} when the envelope carried no sender
     * @return {@code true} when the call was torn down; {@code false} when it was untracked or a guard
     *         suppressed the teardown
     */
    private boolean handlePeerTerminate(TerminateStanza terminate, Jid senderJid) {
        var orchestrated = calls.get(terminate.callId());
        if (orchestrated == null) {
            return false;
        }
        orchestrated.lock().lock();
        try {
            if (isIgnoredCompanionTerminate(orchestrated, senderJid)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring terminate from companion device {0} during group call {1}",
                        senderJid, terminate.callId());
                return false;
            }
            if (isIgnoredJoinableTerminate(orchestrated, terminate)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring joinable terminate in {0} state for call {1}",
                        orchestrated.state(), terminate.callId());
                return false;
            }
            tearDown(orchestrated, terminate.reason(), CallEventType.CALL_TERMINATE_RECEIVED);
            return true;
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Reports whether an inbound terminate is an ignored companion-device terminate during a group call.
     *
     * <p>Returns {@code true} only when every condition of the companion-device guard holds: the
     * {@link Calls2FeatureGate#isIgnoreOneToOneTerminateInGroupCall()} flag is set, the tracked call is a
     * group call, and the terminate's authoring {@code senderJid} is one of the local account's own
     * devices per the {@linkplain #bindOwnDeviceResolver(Predicate) bound own-device predicate}. When the
     * feature gate is unbound, the own-device resolver is unbound, or {@code senderJid} is {@code null},
     * the guard does not fire and the caller tears the call down as usual.
     *
     * @implNote This implementation maps the native {@code handle_terminate} {@code ctx[0x11c]}
     * companion-flag check: the native receive path sets the flag for a terminate authored by another
     * device of the local account, and the engine then drops a one-to-one terminate while a group call is
     * active. The own-device test reproduces the flag through the store-backed predicate; the
     * group-call-active condition is the call's {@link Call#isGroup()} status. The one-to-one shape of the
     * terminate is carried by the companion-flag/own-device signal itself: the bare {@link TerminateStanza}
     * carries no group marker to discriminate a one-to-one terminate from a group terminate on the wire, so
     * the own-device-author-during-a-group-call test is the recoverable form of the native one-to-one
     * companion check, exactly as the {@code IGNORE_ONE_TO_ONE_TERMINATE_IN_GROUP_CALL} prop names it.
     *
     * @param orchestrated the tracked call the terminate targets, accessed under its lock
     * @param senderJid    the device JID that authored the terminate, or {@code null} when absent
     * @return {@code true} when the companion-device guard suppresses the teardown
     */
    private boolean isIgnoredCompanionTerminate(Calls2OrchestratedCall orchestrated, Jid senderJid) {
        var gate = featureGate;
        var resolver = ownDeviceResolver;
        if (gate == null || resolver == null || senderJid == null) {
            return false;
        }
        return gate.isIgnoreOneToOneTerminateInGroupCall()
                && orchestrated.call().isGroup()
                && resolver.test(senderJid);
    }

    /**
     * Reports whether an inbound joinable terminate is dropped by the native state-and-reason guard.
     *
     * <p>Returns {@code true} only when the call is joinable and its state is one the native engine ignores a
     * joinable terminate in: either {@link Calls2CallState#REJOINING} or {@link Calls2CallState#ACCEPT_SENT},
     * or a state outside {@link Calls2CallState#CALL_ACTIVE} and {@link Calls2CallState#CONNECTED_LONELY} whose
     * terminate reason is none of the handled-elsewhere reasons ({@link CallEndReason#ACCEPTED_ELSEWHERE},
     * {@link CallEndReason#REJECTED_ELSEWHERE}, {@link CallEndReason#DEVICE_SWITCH}). The call is joinable when
     * its inbound {@link OfferStanza#joinable()} offer says so; an outbound call (which tracks no inbound
     * offer) is never treated as joinable here.
     *
     * @implNote This implementation reproduces the "ignoring joinable terminate in %s state" path of
     * {@code handle_terminate} (fn11492, {@code messages/handlers/basic.cc}): gated by the joinable byte on the
     * inbound terminate ({@code msg[0x118]}), it ignores when {@code call_state_is(ctx, {Rejoining(9),
     * AcceptSent(4)}, 0)} OR ({@code call_state_is(ctx, {CallActive(6), ConnectedLonely(11)}, 1)} AND the
     * terminate reason string is none of {@code accepted_elsewhere} / {@code rejected_elsewhere} /
     * {@code device_switch}, compared directly against the reason literal). The path reads no offer-expiry
     * predicate and no AB-prop: the {@code IGNORE_JOINABLE_TERMINATE_ON_EXPIRED_OFFER} prop is loaded by the
     * VoipInit loader (fn653) but never read by any code in this {@code ff-tScznZ8P} snapshot, and the
     * offer-expiry predicate ({@code wa_call_is_offer_expired}, fn11504) runs only on the offer-handling path.
     * The call's joinable state stands in for {@code msg[0x118]} through the inbound offer's {@code joinable}
     * flag.
     *
     * @param orchestrated the tracked call the terminate targets, accessed under its lock
     * @param terminate    the decoded inbound terminate, supplying the reason for the not-established branch
     * @return {@code true} when the joinable-terminate guard suppresses the teardown
     */
    private boolean isIgnoredJoinableTerminate(Calls2OrchestratedCall orchestrated, TerminateStanza terminate) {
        var offer = orchestrated.incomingOffer().orElse(null);
        if (offer == null || !offer.joinable()) {
            return false;
        }
        var state = orchestrated.state();
        if (state == Calls2CallState.REJOINING || state == Calls2CallState.ACCEPT_SENT) {
            return true;
        }
        if (state != Calls2CallState.CALL_ACTIVE && state != Calls2CallState.CONNECTED_LONELY) {
            var reason = terminate.reason();
            return reason != CallEndReason.ACCEPTED_ELSEWHERE
                    && reason != CallEndReason.REJECTED_ELSEWHERE
                    && reason != CallEndReason.DEVICE_SWITCH;
        }
        return false;
    }

    /**
     * Handles an inbound transport message, recording an updated relay block.
     *
     * <p>A transport message carries the peer's transport and relay candidates; the controller records a
     * relay block it carries so a deferred media-plane bring-up can use it. The fine-grained transport
     * sub-type routing (remote candidates, relay latency, peer health, ICE/DTLS) is owned by the transport
     * unit. A transport message for an untracked call is ignored.
     *
     * @implNote This implementation reproduces the {@code Transport(6)} arm's relay-block capture; the
     * per-sub-type routing of {@code TransportMessageHandler} (the transport unit) handles the candidate,
     * relay-latency, and ICE/DTLS sub-types and is not reproduced here.
     *
     * @param transport the decoded inbound transport message
     */
    private void handlePeerTransport(TransportStanza transport) {
        var orchestrated = calls.get(transport.callId());
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            transport.toStanza().getChild("relay").ifPresent(orchestrated::relay);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Handles an inbound relay-latency report: folds the peer's per-relay round-trip latencies into the
     * call's relay-election state.
     *
     * <p>A {@code <relaylatency>} report carries the peer's measured latency to each relay it probed; recording
     * it marks the relays the peer can reach, so the next {@linkplain RelayLatencyState#electBestRelayName(RelayElection.Mode)
     * election} the media-plane bring-up runs can converge both ends onto a relay they share. A report for an
     * untracked call, or one that arrives before the local end has built its own
     * {@linkplain Calls2OrchestratedCall#relayLatencyState() relay-latency state} from its relay block, is
     * dropped.
     *
     * <p>Receiving a report also pins the peer device the relay exchange runs with: it replies with the local
     * end's own per-relay latencies addressed to the exact {@code senderJid} that sent this report, so the
     * peer's own election folds in the relays the local end reaches and converges onto a shared relay. This
     * device-to-device reply is required because the proactive offer-ack report is addressed to the callee's
     * primary device, which is not the device that answers and runs the election; the answering device only
     * learns the local end's relays through this reply to its own report.
     *
     * @implNote This implementation ports {@code set_remote_relay_latencies} (fn5173) of
     * {@code wa_transport_relay_election.cc} ({@code ff-tScznZ8P}): the peer report overwrites the per-relay
     * remote-latency table the election reads, keyed by relay name. The election itself runs at bring-up; the
     * reply mirrors the bidirectional exchange a live capture showed both ends drive (each end reports its
     * latencies to the specific peer device it is exchanging with).
     *
     * @param message   the decoded inbound relay-latency report
     * @param senderJid the peer device that sent the report, the reply's recipient
     */
    private void handlePeerRelayLatency(RelayLatencyStanza message, Jid senderJid) {
        var orchestrated = calls.get(message.callId());
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            var state = orchestrated.relayLatencyState().orElse(null);
            if (state == null) {
                return;
            }
            state.recordPeerLatencies(message.entries());
            // Reply with the local end's own relay latencies to the exact device that sent this report, so the
            //  answering device (not the primary the proactive offer-ack report is addressed to) learns the
            //  relays the local end reaches and re-elects onto the shared relay before it binds.
            if (host != null && senderJid != null) {
                var callId = orchestrated.callId();
                var reply = new RelayLatencyStanza(callId, orchestrated.call().creator(), false, -1,
                        state.toLatencyEntries());
                host.sendSignaling(Calls2CallStanza.toCall(reply, senderJid, callId));
            }
            // The primary flow needs no mid-call switch: a live capture (cobalt.log call 1F53F887) confirmed the
            //  peer sends its <relaylatency> reports BEFORE its <accept>, so they are folded in before the
            //  media plane bring-up runs the election, which then converges both ends at bind time. A report
            //  that arrives AFTER bring-up (a relay rekey, or a late peer) cannot move the bound relay in this
            //  pass: the relay address is selected once at bring-up and LiveRelayTransport binds it as a
            //  one-shot. Handling that robustness case is the native update_best_relays (fn5172) mid-call
            //  relay-switch; it re-runs the election here and, when it elects a different relay, rebinds the
            //  transport. Left as a follow-up because it is not on the convergence path the captures exercise.
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Builds the call's relay-election state from a learned relay block and ships the local end's
     * {@code <relaylatency>} report to the peer, once per call.
     *
     * <p>Parses the relay block into its {@link RelayInfo}, seeds a {@link RelayLatencyState} from the offered
     * relay {@linkplain RelayInfo#endpoints() endpoints} (each relay's {@code c2r_rtt} measured by the server),
     * records it on the orchestration handle, and sends a {@code <relaylatency>} carrying the seeded per-relay
     * latencies to the peer so the two ends can exchange their views and converge their relay choice before the
     * media plane comes up. The send is idempotent per call: a second invocation (a later relay block on the
     * same call) is a no-op once the state exists, so the report is sent exactly once. A call with no host
     * transport, no relay block, or a relay block that does not parse sends nothing.
     *
     * @implNote This implementation drives the relay-latency exchange the engine runs from
     * {@code apply_relay_latencies} (fn5168) of {@code wa_transport_relay_election.cc} ({@code ff-tScznZ8P}):
     * each end seeds its per-relay table from the offered {@code c2r_rtt} and reports it in a
     * {@code <relaylatency>}, then folds the peer's report into the table the election reads. The report is
     * addressed with the same {@link #controlRecipient(Calls2OrchestratedCall) recipient rule} the in-call
     * control actions use (the MUC call target for a group call, the peer signaling device otherwise).
     *
     * @param orchestrated the call whose relay-latency exchange is started
     * @param relayNode    the call's learned {@code <relay>} block subtree, or {@code null} when none is known
     */
    private void startRelayLatencyExchange(Calls2OrchestratedCall orchestrated, Stanza relayNode) {
        if (host == null || relayNode == null || orchestrated.relayLatencyState().isPresent()) {
            return;
        }
        var relayInfo = RelayInfo.of(relayNode).orElse(null);
        if (relayInfo == null) {
            return;
        }
        // The local per-relay latencies are seeded from the offer-ack c2r_rtt the server measured (the
        //  RelayLatencyState constructor). A live capture confirmed this seed converges both ends: WhatsApp
        //  clients run a live probe and report measured latencies well above c2r_rtt (WA-Web reported
        //  262/293/331 ms and the native desktop 76/77/77 ms against a 14/23/21 ms c2r_rtt), yet
        //  find_best_relay (fn5170) elects by the SUM of each relay's per-party latencies, so as long as each
        //  end reports the same value it elects with (this state reports and elects from the same
        //  localLatencies), both ends compute an identical per-relay sum and elect the same relay regardless
        //  of the latency scale. The native live per-relay probe-ping round that would replace the c2r_rtt
        //  seed with Cobalt's own measured RTT (RelayLatencyState.recordProbeLatency) is an optional accuracy
        //  refinement, gated on the relay's pre-bind probe-response wire shape (untested without a probe
        //  capture); it does not affect convergence, only which of several shared relays wins a close call.
        var state = new RelayLatencyState(relayInfo.endpoints());
        orchestrated.relayLatencyState(state);
        var callId = orchestrated.callId();
        var report = new RelayLatencyStanza(callId, orchestrated.call().creator(), false, -1,
                state.toLatencyEntries());
        host.sendSignaling(Calls2CallStanza.toCall(report, controlRecipient(orchestrated), callId));
    }

    /**
     * Handles an inbound group update: reconciles the membership roster and re-decides the active state.
     *
     * <p>A {@code <group_update>} carries the refreshed {@code <group_info>} roster of an in-progress group
     * call. The controller reconciles the call's {@link CallMembership} against it, then re-runs the
     * active-versus-lonely decision: a group call with at least one other connected participant is
     * {@link Calls2CallState#CALL_ACTIVE}, otherwise it stays or returns to
     * {@link Calls2CallState#CONNECTED_LONELY}. A membership change on the caller side additionally triggers
     * a fresh per-participant key share, so the local participant re-fans its current call key to the
     * connected roster (the re-fan is gated to the caller because it stamps the local device JID, which is
     * the call creator only for an outbound call). A group update for an untracked call, or one carrying no
     * roster, is ignored.
     *
     * @implNote This implementation reproduces the {@code GroupUpdate(17)} arm's
     * {@code post_process_group_info} (fn10987) reconcile-then-decide: the membership diff drives the
     * active-versus-lonely transition and the engine re-shares the call key on a membership change
     * ({@code make_and_send_rekey_msg}, fn11448). The downstream per-participant media and SFrame-key bring-up
     * each {@link CallMembership.Reconciliation#added() added} member triggers is owned by the participant
     * and media subsystems reached through the media plane, not performed inline here.
     *
     * @param groupUpdate the decoded inbound group update
     */
    private void handleGroupUpdate(GroupUpdateStanza groupUpdate) {
        var orchestrated = calls.get(groupUpdate.callId());
        if (orchestrated == null) {
            return;
        }
        var roster = groupUpdate.groupInfoValue().orElse(null);
        if (roster == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            var membership = orchestrated.membership().orElse(null);
            if (membership == null) {
                return;
            }
            var diff = membership.reconcile(roster);
            if (!diff.isEmpty()) {
                infoUpdater.updateForEvent(orchestrated.callId(), CallEventType.GROUP_INFO_CHANGED);
                events.emit(CallEventType.GROUP_INFO_CHANGED, new byte[0]);
                if (orchestrated.isCaller()) {
                    orchestrated.callKey().ifPresent(callKey ->
                            fanOutGroupRekey(orchestrated, orchestrated.call().creator(), callKey));
                }
            }
            // After any re-fan re-stamps the roster's devices, clear the offer marker of every peer the
            // roster now reports connected, so a connected peer's key rotation is never mistaken for an
            // unanswered offer and only a peer that never connects remains a sweep candidate.
            orchestrated.groupOutbound().ifPresent(GroupCallOutbound::clearConnectedOffers);
            decideGroupActiveState(orchestrated, membership);
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Handles an inbound {@code mute_v2}: a peer reported its own mute state or asked the local user to mute.
     *
     * <p>Routes a peer-mute request to the call's {@link MuteController#onPeerMuteRequest(Jid)}, which
     * surfaces a {@link com.github.auties00.cobalt.calls2.core.control.MuteByAnotherParticipant} unless the
     * recent-unmute lockout is active, and a peer self-state report to
     * {@link MuteController#onPeerMuted(Jid)}, which satisfies a pending outbound peer-mute request without
     * re-emitting. A {@code mute_v2} for an untracked call, or one whose control units are not built, is
     * ignored.
     *
     * @param mute      the decoded inbound mute_v2 action
     * @param senderJid the device JID that authored the action, treated as the reporting peer
     */
    private void handlePeerMute(MuteV2Stanza mute, Jid senderJid) {
        withControls(mute.callId(), controls -> {
            if (mute.peerRequest()) {
                controls.mute().onPeerMuteRequest(senderJid);
            } else {
                controls.mute().onPeerMuted(senderJid);
            }
        });
    }

    /**
     * Handles an inbound {@code video_state}: a peer reported a change in its video stream state.
     *
     * <p>Routes the report to the call's {@link VideoStateController#onPeerVideoState(Jid, VideoStreamState)},
     * which emits the peer's video change, and projects the reported state onto the owning
     * {@link CallParticipant} so a {@link com.github.auties00.cobalt.calls2.core.participant.ParticipantView}
     * snapshot reflects the live peer video state: the participant's {@linkplain CallParticipant#videoState(int)
     * video-state code} is set to the reported state's {@linkplain VideoStreamState#wireOrdinal() engine
     * ordinal} and the reporting device's {@linkplain
     * com.github.auties00.cobalt.calls2.core.participant.CallDeviceInfo#videoEnabled(boolean) per-device
     * video-enabled flag} is set to whether the reported state is {@link VideoStreamState#ENABLED}. A
     * {@code video_state} for an untracked call, or one whose control units are not built, still projects
     * the state when the call's membership knows the reporting device.
     *
     * @implNote This implementation mirrors the participant-plane writes the native peer-video-state path
     * makes alongside the host event: {@code handle_peer_video_stopped} (fn11388) sets the participant
     * detail video-state code at {@code participant+0x1c} (the value {@code wa_participant_view_get_video_state}
     * (fn11014) reads back), and {@code set_participant_video_enabled} (fn11415) toggles the per-device
     * video-enabled flag at {@code device+0x69}. Cobalt models those as {@link CallParticipant#videoState(int)}
     * and the active device's {@code videoEnabled} flag. The subscribed encoded-stream id the view also
     * carries is driven by the video-subscription manager in the media plane, not by this signaling path, so
     * it is not set here.
     *
     * @param videoState the decoded inbound video-state action
     * @param senderJid  the device JID that authored the action, treated as the reporting peer
     */
    private void handlePeerVideoState(VideoStateStanza videoState, Jid senderJid) {
        var state = videoState.state();
        projectPeerMediaState(videoState.callId(), senderJid, participant -> {
            participant.videoState(state.wireOrdinal());
            participant.device(CallDeviceJid.of(senderJid))
                    .ifPresent(device -> device.videoEnabled(state == VideoStreamState.ENABLED));
        });
        withControls(videoState.callId(), controls -> controls.video().onPeerVideoState(senderJid, state));
    }

    /**
     * Handles an inbound {@code screen_share}: a peer reported a change in its screen-share stream.
     *
     * <p>Routes the report to the call's
     * {@link ScreenShareController#onPeerScreenShare(Jid, com.github.auties00.cobalt.calls2.core.control.ScreenShareState, int)
     * onPeerScreenShare}, resolving the numeric wire state through
     * {@link com.github.auties00.cobalt.calls2.core.control.ScreenShareState#ofCode(int)}; an unrecognized
     * state code is dropped. A {@code screen_share} for an untracked call, or one whose control units are not
     * built, is ignored.
     *
     * @implNote This implementation mirrors the participant-plane write the native peer-screen-share path
     * makes alongside the host event: the screen-share flag the engine sets at {@code participant+0x1c}, the
     * byte {@code call_get_screen_sharer_peer_participant} (fn10840) reads to find the sharing peer. Cobalt
     * models it as {@link CallParticipant#screenSharing(boolean)}, set when the reported state is
     * {@link ScreenShareState#STARTED} and cleared otherwise.
     *
     * @param screenShare the decoded inbound screen-share action
     * @param senderJid   the device JID that authored the action, treated as the reporting peer
     */
    private void handlePeerScreenShare(ScreenShareStanza screenShare, Jid senderJid) {
        ScreenShareState.ofCode(screenShare.state()).ifPresent(state -> {
            projectPeerMediaState(screenShare.callId(), senderJid,
                    participant -> participant.screenSharing(state == ScreenShareState.STARTED));
            withControls(screenShare.callId(),
                    controls -> controls.screenShare().onPeerScreenShare(senderJid, state, screenShare.version()));
        });
    }

    /**
     * Projects a peer media-state change onto the owning participant aggregate under the call's lock.
     *
     * <p>Resolves the tracked call, takes its lock, finds the call membership's participant owning the
     * reporting device JID, and applies the mutation to that live {@link CallParticipant} so a later
     * {@link com.github.auties00.cobalt.calls2.core.participant.ParticipantView} snapshot reflects the
     * change. A call that is not tracked, that has no membership (a one-to-one call keeps no roster), or
     * whose membership does not know the reporting device runs no mutation, mirroring the engine reading the
     * peer state off the participant only when the participant set holds that device.
     *
     * @param callId    the identifier of the call the report belongs to
     * @param senderJid the reporting device JID whose owning participant is mutated
     * @param mutation  the mutation to apply to the resolved participant aggregate
     */
    private void projectPeerMediaState(String callId, Jid senderJid, java.util.function.Consumer<CallParticipant> mutation) {
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            orchestrated.membership()
                    .flatMap(membership -> membership.findByDeviceJid(senderJid))
                    .ifPresent(slot -> mutation.accept(slot.participant()));
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Handles an inbound {@code raise_hand}: a peer raised or lowered its hand.
     *
     * <p>Routes the report to the call's {@link RaiseHandController#onPeerHandRaised(Jid, boolean)}, which
     * emits the peer's hand-state change and feeds the grid-ranking comparator. A {@code raise_hand} for an
     * untracked call, or one whose control units are not built, is ignored.
     *
     * @param raiseHand the decoded inbound raise-hand action
     * @param senderJid the device JID that authored the action, treated as the reporting peer
     */
    private void handlePeerRaiseHand(RaiseHandStanza raiseHand, Jid senderJid) {
        withControls(raiseHand.callId(), controls -> controls.raiseHand().onPeerHandRaised(senderJid, raiseHand.raised()));
    }

    /**
     * Decides whether a group call is active or lonely from its connected-participant count.
     *
     * <p>A group call with at least one other connected participant is moved to
     * {@link Calls2CallState#CALL_ACTIVE}; one with none returns to {@link Calls2CallState#CONNECTED_LONELY}.
     * Only the two in-call states are transitioned, since the active-versus-lonely decision applies once the
     * call has connected to the unit; a call still in its offer or accept leg is left for the media-connected
     * path to advance.
     *
     * @implNote This implementation reproduces the {@code post_process_group_info} (fn10987) decision: a
     * connected peer selects {@link Calls2CallState#CALL_ACTIVE} and none selects
     * {@link Calls2CallState#CONNECTED_LONELY} (the local participant is alone). The connected-peer test reads
     * the reconciled membership's {@link CallMembership#participantProvider() participant provider}
     * ({@link com.github.auties00.cobalt.calls2.core.participant.ParticipantProvider#firstConnectedPeer()}),
     * whose per-slot aggregates carry the membership state the reconcile projected from each roster entry's
     * {@code "connected"} literal of the seven-entry server-user-state table (SPEC section 7.1).
     *
     * @param orchestrated the group call being decided
     * @param membership   the call's reconciled membership
     */
    private void decideGroupActiveState(Calls2OrchestratedCall orchestrated, CallMembership membership) {
        var state = orchestrated.state();
        if (state != Calls2CallState.CALL_ACTIVE && state != Calls2CallState.CONNECTED_LONELY) {
            return;
        }
        var anyPeerConnected = membership.participantProvider().firstConnectedPeer().isPresent();
        var target = anyPeerConnected ? Calls2CallState.CALL_ACTIVE : Calls2CallState.CONNECTED_LONELY;
        transition(orchestrated, target, CallEventType.CALL_STATE_CHANGED);
    }

    /**
     * Runs an action against a tracked call's in-call control units under the call's lock.
     *
     * <p>Resolves the orchestration handle, takes the call's lock, builds the call's control units if they
     * are not yet built and the bus is bound, and runs the action against them. A call that is not tracked,
     * or one whose control units could not be built (the bus is unbound), runs no action; this is the
     * uniform no-op the public in-call control methods and the inbound action handlers fall back to so an
     * action on an absent or not-yet-answerable call is silently dropped, matching the best-effort control
     * plane.
     *
     * @param callId the identifier of the call whose control units the action targets
     * @param action the action to run against the call's control units
     */
    private void withControls(String callId, java.util.function.Consumer<Calls2CallControls> action) {
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            return;
        }
        orchestrated.lock().lock();
        try {
            ensureControls(orchestrated);
            var controls = orchestrated.controls().orElse(null);
            if (controls != null) {
                action.accept(controls);
            }
        } finally {
            orchestrated.lock().unlock();
        }
    }

    /**
     * Builds and stores a call's in-call control units the first time the call becomes answerable.
     *
     * <p>Constructs the {@link CallControlContext} for the call, a {@link ControlEventBridge} over the shared
     * bus that stamps the call id onto the host events the units emit, a {@link CallSignalingSender} that
     * wraps each action in the {@code <call>} envelope addressed to the call's current control recipient, and
     * the four signaling-plane control units (mute, video, screen-share, raise-hand) plus the
     * {@link SpeakerRankingService} the raise-hand unit feeds. When the call's media plane carries an
     * application-data plane, the three app-data-backed control units (reactions, live transcription, IMU)
     * are also constructed over the media session's {@link AppDataController} and wired onto the same
     * {@link ControlEventBridge}: the {@link ReactionController} attaches its outbound send seam to
     * {@link AppDataController#sendReaction(String)} and registers itself as the controller's inbound
     * reaction observer ({@link AppDataController#attachReactionObserver(AppDataController.ReactionObserver)},
     * host event {@code 0x91} plus the reaction-clear sweep), the {@link LiveTranscriptionController}
     * registers as the inbound transcription observer
     * ({@link AppDataController#attachTranscriptionObserver(java.util.function.BiConsumer)}, host event
     * {@code 0x9b}), and the {@link ImuDataController} is built over the call's outbound IMU app-data send.
     * A call whose transport carries no app-data plane skips the three app-data units, which are then left
     * unset on the holder. The units are stored on the orchestration handle and closed on teardown. This is
     * idempotent: a call whose units are already built, or one for which the bus has not been bound, is left
     * unchanged. Must be called under the call's lock.
     *
     * @implNote This implementation reproduces the engine's lazy per-call control-unit creation: the native
     * engine binds the in-call action serializers to a call once it is answerable (accept sent, accept
     * received, or a group call gone active). The screen-share version defaults to
     * {@link ScreenShareController#VERSION_V2} (the single-stream port-swap path); the V3 dual-stream
     * negotiation is a separate piece. The control context's self JID is read from the bound supplier and
     * falls back to the call creator, which is the local device for an outbound call. The app-data control
     * units exist only once the media plane is up and exposes an {@link AppDataController}, so they are built
     * here rather than at signaling-plane construction time: a call answered before its relay block is known
     * builds the signaling-plane units first and gains the app-data units on the {@code ensureControls} pass
     * that follows the media-plane bring-up.
     *
     * @param orchestrated the call whose control units are built
     */
    private void ensureControls(Calls2OrchestratedCall orchestrated) {
        if (eventBus == null || orchestrated.controls().isPresent()) {
            return;
        }
        var call = orchestrated.call();
        var callId = orchestrated.callId();
        var creator = call.creator();
        var supplier = selfJidSupplier;
        var resolvedSelf = supplier == null ? null : supplier.get();
        var self = resolvedSelf != null ? resolvedSelf : creator;
        var context = call.isGroup()
                ? CallControlContext.group(callId, creator, self)
                : CallControlContext.oneToOne(callId, creator, self);
        CallSignalingSender sender =
                message -> host.sendSignaling(Calls2CallStanza.toCall(message, controlRecipient(orchestrated), callId));
        var bridge = new ControlEventBridge(callId, eventBus);
        var ranking = new SpeakerRankingService(bridge);
        var mute = new MuteController(context, sender, bridge);
        var video = new VideoStateController(context, sender, bridge);
        var screenShare = new ScreenShareController(context, sender, bridge, screenShareVersion());
        var raiseHand = new RaiseHandController(context, sender, bridge, ranking);

        ReactionController reaction = null;
        LiveTranscriptionController transcription = null;
        ImuDataController imu = null;
        var appData = orchestrated.appDataController().orElse(null);
        if (appData != null) {
            reaction = new ReactionController(self, bridge);
            reaction.attach(appData::sendReaction);
            appData.attachReactionObserver(reaction);

            transcription = new LiveTranscriptionController(bridge);
            var transcriptionUnit = transcription;
            appData.attachTranscriptionObserver((speaker, fragment) ->
                    transcriptionUnit.onTranscript(speaker, fragment));

            // The outbound IMU sender is a FAITHFUL no-op for the web target, not a gap: WhatsApp Web has no
            //  IMU producer. The web VoIP stack (WAWebVoipStackInterfaceWeb) ships no sensor/accelerometer/
            //  DeviceMotion source and stubs its native bridge entries as WAWebNoop; the IMU feed the native
            //  imu_data_controller.cc consumes is a host sensor-enqueue ring supplied by the mobile/desktop
            //  app outside the wa-voip module, which the web host does not provide. With no sample source there
            //  is nothing to serialize, so the outbound sender intentionally does nothing. (Independently, the
            //  cross-cluster AppDataController/AppDataChannel API in calls2/net/transport exposes no app-data
            //  IMU send seam; the 0x24-byte ImuSample frame layout is modeled (ImuDataController), but the web
            //  host produces no samples to send; both are moot for the web target.)
            // The inbound IMU observer is likewise a faithful no-op: IMU does not ride the AppData stream this
            //  controller demuxes (reaction, transcription, rekey, subscription, feedback), and the web target
            //  renders no inbound IMU, so although the 0x24-byte ImuSample layout is now modeled
            //  (ImuDataController), no inbound IMU flows on this path to parse.
            imu = new ImuDataController(sample -> { }, (participant, sample) -> { });
        }

        orchestrated.controls(new Calls2CallControls(
                mute, video, screenShare, raiseHand, reaction, transcription, imu, null, null));
    }

    /**
     * Resolves the screen-share protocol version a call's {@link ScreenShareController} advertises.
     *
     * <p>The server expresses the screen-share capability as a milestone version rather than a boolean:
     * {@link Calls2FeatureGate#screenShareMilestoneVersion()} returns the negotiated milestone (its prop
     * default is {@code 2}), which is the protocol version the {@code <screen_share>} action carries, so it
     * is fed straight into the controller. When the feature gate is unbound (the test harnesses build the
     * controller with no AB-props service) this falls back to {@link ScreenShareController#VERSION_V2}, the
     * single-stream port-swap version the prop default selects, matching the previously hardcoded version so
     * a gate-less build behaves exactly as before.
     *
     * @implNote This implementation feeds the {@code CALLING_SCREEN_SHARE_MILESTONE_VERSION} prop into the
     * {@code <screen_share version>} the controller stamps. The milestone is the screen-share protocol
     * version directly ({@code 2} selects {@link ScreenShareController#VERSION_V2}, {@code 3} selects
     * {@link ScreenShareController#VERSION_V3}); the gate already folds the milestone-versus-master-gate
     * decision into {@link Calls2FeatureGate#isScreenShareEnabled()}, which {@link #startScreenShare(String)}
     * checks before a share starts, so this read supplies only the version the action advertises.
     *
     * @return the negotiated screen-share protocol version, or {@link ScreenShareController#VERSION_V2} when
     *         the feature gate is unbound
     */
    private int screenShareVersion() {
        var gate = featureGate;
        return gate == null ? ScreenShareController.VERSION_V2 : gate.screenShareMilestoneVersion();
    }

    /**
     * Resolves the recipient an in-call control action for a call is addressed to.
     *
     * <p>A group call addresses its MUC call target {@code <callId>@call}; a one-to-one call addresses the
     * peer's signaling device when it is known, falling back to the peer user JID. This is the same recipient
     * rule the accept and preaccept legs use, evaluated per send so a peer device JID that becomes known
     * after the control units are built (the caller learns the answering device) is picked up.
     *
     * @param orchestrated the call whose control recipient is resolved
     * @return the recipient JID for an in-call control action
     */
    private static Jid controlRecipient(Calls2OrchestratedCall orchestrated) {
        var call = orchestrated.call();
        return call.isGroup()
                ? Jid.of(call.callId() + "@call")
                : orchestrated.peerDeviceJid().orElse(call.peer());
    }

    /**
     * Brings up the media plane for a call and records the resulting session.
     *
     * <p>Delegates to
     * {@link Calls2MediaPlane#bringUp(String, Stanza, List, byte[], boolean, boolean, int, CallMembership, Calls2MediaStreams, Jid, Optional)}
     * with the negotiated {@code <voip_settings>} bundles, the call's current membership size, and the
     * call's recorded application capture sources and playback sinks, and stores the returned session on the
     * orchestration handle so the teardown can close it. The bring-up exception is propagated to the caller,
     * which decides whether to tear the call down.
     *
     * <p>The participant count is the call's membership size for a group call and zero for a one-to-one call
     * (which tracks no membership roster); the media plane treats a missing roster as the default call size.
     *
     * <p>Once the session is up, the session's application-data controller (present only when the brought-up
     * transport carries an app-data plane) is recorded on the orchestration handle so the in-call control
     * units that observe the app-data side-channel can attach themselves to it when they are built, which
     * happens later in the call lifecycle in {@link #ensureControls(Calls2OrchestratedCall)}. A transport
     * with no app-data plane records {@code null}, and the app-data-backed control units are then simply not
     * built.
     *
     * @param orchestrated the call whose media plane is being brought up
     * @param relay        the relay block subtree
     * @param voipSettings the {@code <voip_settings>} bundle nodes the offer (callee) or offer
     *                     acknowledgement (caller) carried, in wire order
     * @param callKey      the raw call key the SRTP and SFrame keys derive from
     * @param isCaller     whether the local side placed the call
     * @param video        whether the local side participates with video
     * @throws WhatsAppCallException if the media plane cannot be brought up
     */
    private void bringUpMediaPlane(Calls2OrchestratedCall orchestrated, Stanza relay, List<Stanza> voipSettings,
                                   byte[] callKey, boolean isCaller, boolean video) {
        if (orchestrated.mediaSession().isPresent()) {
            return;
        }
        var membership = orchestrated.membership().orElse(null);
        var participantCount = membership == null ? 0 : membership.size();
        // Elect the relay both ends reach from the exchanged latencies before the transport binds; an empty
        // election (no peer report yet, or no relay both ends reported) leaves the bring-up on its local pick.
        var electedRelayName = orchestrated.relayLatencyState()
                .flatMap(state -> state.electBestRelayName(RelayElection.Mode.DEFAULT));
        var session = mediaPlane.bringUp(orchestrated.callId(), relay, voipSettings, callKey, isCaller, video,
                participantCount, membership, orchestrated.mediaStreams(), orchestrated.peerDeviceJid().orElse(null),
                electedRelayName);
        orchestrated.mediaSession(session);
        orchestrated.appDataController(session.appDataController().orElse(null));
    }

    /**
     * Returns the {@code <voip_settings>} bundle subtrees carried directly under a call ack stanza.
     *
     * <p>The server denormalises the engine parameter bundles into the synchronous offer ack as direct
     * {@code <voip_settings>} children alongside the relay, user, and rte blocks; this reads them in wire
     * order for the caller-side media-plane bring-up.
     *
     * @param ack the server's call ack stanza
     * @return the ack's {@code <voip_settings>} children, in wire order; empty when the ack carries none
     */
    private static List<Stanza> voipSettingsOf(Stanza ack) {
        return ack.getChildren(VOIP_SETTINGS_ELEMENT)
                .stream()
                .toList();
    }

    /**
     * Builds the {@code <offer>} action for an outbound one-to-one call.
     *
     * <p>The offer carries the call creator, the caller identity hints from {@code identity} (the
     * {@code caller_pn} and {@code username} attributes, both absent today per {@link #offerIdentity()}), the
     * caller's standard capability advertisement, the offered audio codecs, the standard encryption options,
     * and the per-device call-key fanout the crypto facade produced. A video offer additionally advertises the
     * video codecs.
     *
     * @param callId    the call identifier
     * @param self      the local device JID, the call creator
     * @param devices   the peer device JIDs the key is fanned out to
     * @param callKey   the minted call key
     * @param video     whether video is offered
     * @param identity  the caller identity hints (caller phone-number JID and username) the offer advertises,
     *                  each carried only when present
     * @return the offer action
     */
    private OfferStanza buildOffer(String callId, Jid self, List<Jid> devices, byte[] callKey,
                                   boolean video, OfferIdentity identity) {
        var plaintext = crypto.wrapCallKey(callKey);
        var keyDistribution = crypto.encryptOfferFanout(devices, plaintext);
        var capabilities = List.of(standardCapability());
        var audioCodecs = standardAudioCodecs();
        var videoCodecs = video ? standardVideoCodecs() : List.<CallCodecDescriptor>of();
        var deviceIdentity = anyPreKeyMessage(keyDistribution) ? crypto.signedDeviceIdentity() : null;
        return new OfferStanza(callId, self, identity.callerPn(), null, identity.username(), null, null, null,
                false, false, null, -1, NET_MEDIUM_OFFER, capabilities, audioCodecs, videoCodecs, keyDistribution, null,
                CallEncOptions.standard(), null, deviceIdentity, null, null, null, List.of(), null);
    }

    /**
     * Builds the {@code <offer>} action for an outbound group call.
     *
     * <p>The group offer carries the group JID, the {@code joinable} flag, the caller's standard capability
     * advertisement, the offered audio codecs, the standard encryption options, and the {@code <group_info>}
     * roster of the rostered participants. Unlike the one-to-one offer it carries NO per-device key fanout
     * (the per-participant key arrives post-join as {@code <enc_rekey>}), so no Signal session is established
     * here and no device identity is attached. A video group call additionally advertises the video codecs.
     *
     * @param callId   the call identifier
     * @param self     the local device JID, the call creator
     * @param groupJid the group JID the call belongs to
     * @param roster   the participant roster carried as the offer's {@code <group_info>}
     * @param video    whether video is offered
     * @param identity the caller identity hints (caller phone-number JID and username) the offer advertises,
     *                 each carried only when present (both absent today per {@link #offerIdentity()})
     * @return the group offer action
     */
    private OfferStanza buildGroupOffer(String callId, Jid self, Jid groupJid, GroupInfoStanza roster,
                                        boolean video, OfferIdentity identity) {
        var capabilities = List.of(standardCapability());
        var audioCodecs = standardAudioCodecs();
        var videoCodecs = video ? standardVideoCodecs() : List.<CallCodecDescriptor>of();
        return new OfferStanza(callId, self, identity.callerPn(), null, identity.username(), groupJid, null, null,
                true, false, null, -1, NET_MEDIUM_OFFER, capabilities, audioCodecs, videoCodecs, List.of(), null,
                CallEncOptions.standard(), roster.toStanza(), null, null, null, null, List.of(), null);
    }

    /**
     * Resolves the caller identity hints an outbound {@code <offer>} advertises, which are none.
     *
     * <p>WhatsApp gates which identity hints an offer carries on server AB-props that the
     * {@link Calls2FeatureGate} exposes:
     * <ul>
     *   <li>{@link Calls2FeatureGate#callingLidVersion()} is the LID-versus-PN calling decision. The
     *       call-creator JID form is fixed upstream (the call service stamps the account JID, a LID device
     *       JID once LID calling is active).</li>
     *   <li>{@link Calls2FeatureGate#isPhoneNumberPrivacyEnabled()} governs whether the caller's phone number
     *       may accompany a LID call as {@code caller_pn}.</li>
     *   <li>{@link Calls2FeatureGate#isCallingUsernameEnabled()} governs whether a {@code username} hint
     *       accompanies the call.</li>
     * </ul>
     *
     * <p>The ground truth shows the client does NOT itself stamp {@code caller_pn} or {@code username} on the
     * offer it sends. On the captured sender egress a one-to-one offer carries only {@code call-creator}
     * (a {@code @lid} device JID); {@code caller_pn} and {@code caller_country_code} appear only on the copy
     * the server relays to the callee, server-injected like {@code <relay>}/{@code <voip_settings>}
     * (re/calls2-spec/captures/CAPTURE-FINDINGS.md lines 75-77). So this resolver supplies neither hint, and
     * an offer carries neither attribute regardless of the gate; stamping either would diverge from the native
     * client, which emits neither (see the {@code @implNote}).
     *
     * @implNote This implementation returns no hints. The wa-voip WASM module {@code ff-tScznZ8P} contains no
     * offer serializer that writes {@code caller_pn} or {@code username}: the only {@code caller_pn} reference
     * is the inbound parser {@code parse_xmpp_offer} (fn11641), and the AB-prop name strings
     * ({@code ENABLE_CALLING_USERNAME}, the calling-LID-version and phone-number-privacy props) are absent
     * from the snapshot. The {@link OfferStanza} keeps its {@code caller_pn}/{@code username} components (the
     * inbound parse populates them on a received offer), but the outbound builders pass {@code null} for both.
     *
     * @return the no-hint identity; the outbound offer carries neither {@code caller_pn} nor {@code username}
     */
    private OfferIdentity offerIdentity() {
        // The client does not stamp caller_pn or username on the offer it sends (the server injects caller_pn
        // on the relayed copy); see the body javadoc and the TODO above. No outbound hint is selected.
        return OfferIdentity.NONE;
    }

    /**
     * The caller identity hints an outbound {@code <offer>} could carry.
     *
     * <p>Each hint is present only when an outbound value is faithfully available; an absent hint is
     * {@code null}, which the {@link OfferStanza} builder omits from the wire element. The {@link #NONE}
     * singleton is the no-hint shape {@link #offerIdentity()} always returns today, since the client does not
     * stamp {@code caller_pn} or {@code username} on the offer it sends.
     *
     * @param callerPn the caller's phone-number JID to advertise as {@code caller_pn}, or {@code null} to
     *                 omit it
     * @param username the caller's username to advertise as {@code username}, or {@code null} to omit it
     */
    private record OfferIdentity(Jid callerPn, String username) {
        /**
         * The no-hint identity carrying neither {@code caller_pn} nor {@code username}.
         */
        private static final OfferIdentity NONE = new OfferIdentity(null, null);
    }

    /**
     * Builds a {@code <group_info>} roster of the given participant user JIDs.
     *
     * <p>Each participant becomes a minimal {@code <user>} entry pinned by its user JID; the
     * server-negotiated connected limit is the placement default. The relay fills the per-participant
     * devices, capabilities, and state on the roster it echoes back, so the placement roster carries only
     * the identities to invite.
     *
     * @param participants the participant user JIDs to roster
     * @return the placement group-info roster
     */
    private static GroupInfoStanza rosterOf(Collection<Jid> participants) {
        var users = new ArrayList<Stanza>(participants.size());
        for (var participant : participants) {
            users.add(CallParticipantUserNode.ofUser(participant).toNode());
        }
        return GroupInfoStanza.ofUsers(null, GROUP_CONNECTED_LIMIT, users);
    }

    /**
     * Reconciles a group call's membership against the roster a positive offer ack echoed, if any.
     *
     * <p>The selective-forwarding unit's positive ack carries a {@code <group_info>} roster decorated with
     * the per-participant devices, capabilities, and state the relay assigned; the controller reconciles the
     * call's {@link CallMembership} against it so the membership reflects the unit's view. An ack carrying no
     * roster leaves the placement membership unchanged.
     *
     * @param orchestrated the group call whose membership is reconciled
     * @param ack          the server's offer ack stanza
     */
    private static void reconcileFromAck(Calls2OrchestratedCall orchestrated, Stanza ack) {
        var membership = orchestrated.membership().orElse(null);
        if (membership == null) {
            return;
        }
        ack.streamChildren()
                .map(GroupInfoStanza::of)
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(membership::reconcile);
    }

    /**
     * Fans the call key out to every connected participant device as a per-participant {@code <enc_rekey>}.
     *
     * <p>The local participant re-shares its current call key by sending one unicast {@code <enc_rekey>}
     * stanza per connected participant device, each addressed to that device and carrying a single
     * {@code <enc>} envelope of the wrapped key. The key is wrapped once and encrypted per device through the
     * crypto facade's rekey fanout, which skips a device whose encryption fails rather than aborting the
     * whole rotation. A rekey carries the local device JID as its call creator and an omitted transaction id,
     * since the placement and membership-change rounds do not stamp the self-rekey transaction counter the
     * engine bounds below {@link com.github.auties00.cobalt.calls2.signaling.RekeyStanza#MAX_TRANSACTION_ID}.
     *
     * @implNote This implementation reproduces {@code make_and_send_rekey_msg} (fn11448) and the
     * unicast-per-participant fanout confirmed in {@code re/calls2-spec/captures/group-rekey.json}: each
     * connected participant device receives its own {@code <call to="<deviceLid>"><enc_rekey>} carrying one
     * {@code <enc>} (a single thirty-two-byte key, the same plaintext shape as the offer key), with a
     * {@code <device-identity>} attached only on a {@code pkmsg} envelope. The three per-domain
     * audio/video/appdata keys are derived locally after decrypt, not transmitted. The transaction id is
     * omitted here because the recovered self-rekey counter origin is not threaded through; the live capture's
     * wire transaction ids (25, 30) are the SFU membership-round counters, not the self-rekey guard.
     *
     * @param orchestrated the group call whose key is shared
     * @param self         the local device JID, stamped as the rekey call creator
     * @param callKey      the call key to re-share
     */
    private void fanOutGroupRekey(Calls2OrchestratedCall orchestrated, Jid self, byte[] callKey) {
        var recipients = connectedParticipantDevices(orchestrated, self);
        if (recipients.isEmpty()) {
            return;
        }
        var plaintext = crypto.wrapCallKey(callKey);
        List<CallRekeyEnvelope> envelopes = crypto.encryptRekeyFanout(recipients, plaintext);
        var callId = orchestrated.callId();
        for (var envelope : envelopes) {
            var rekey = envelope.toNode(callId, self, -1);
            host.sendSignaling(wrapInCall(rekey, envelope.recipientDevice(), callId));
        }
        orchestrated.groupOutbound().ifPresent(unit -> unit.fanOfferSent(recipients));
    }

    /**
     * Collects every participant device the local participant fans a rekey out to.
     *
     * <p>Walks the call's membership roster and gathers each device JID of each participant other than the
     * local account, so the local participant's own devices are never sent the rekey. A rekey is addressed
     * unicast to a device JID and Signal-encrypted for that device, so a participant the roster lists with
     * no device yet (a placement roster the relay has not yet decorated with its device list) contributes no
     * recipient: the key reaches it on the next reconcile once the relay's {@code <group_info>} broadcast
     * carries the participant's {@code <device>} list, matching the capture where the rekey fanout follows
     * the connected roster rather than the bare placement roster.
     *
     * @param orchestrated the group call whose roster is walked
     * @param self         the local device JID, whose account is excluded
     * @return the recipient device JIDs, possibly empty
     */
    private static List<Jid> connectedParticipantDevices(Calls2OrchestratedCall orchestrated, Jid self) {
        var membership = orchestrated.membership().orElse(null);
        if (membership == null) {
            return List.of();
        }
        var selfUser = self.toUserJid();
        var recipients = new ArrayList<Jid>();
        for (var identity : membership.identities()) {
            if (identity.jid().toUserJid().equals(selfUser)) {
                continue;
            }
            for (var device : identity.devices()) {
                recipients.add(device.jid());
            }
        }
        return List.copyOf(recipients);
    }

    /**
     * Wraps a built action stanza in a {@code <call>} envelope addressed to a recipient device.
     *
     * <p>This is the envelope shim for an action already built as a {@link Stanza} (a per-participant rekey),
     * distinct from {@link Calls2CallStanza#toCall(CallMessage, Jid, String)} which wraps a typed
     * {@link CallMessage}.
     *
     * @param action the built action stanza to wrap
     * @param to     the recipient device JID
     * @param callId the call identifier, used as the envelope stanza id
     * @return the {@code <call to id>} envelope nesting the action
     */
    private static Stanza wrapInCall(Stanza action, Jid to, String callId) {
        return new StanzaBuilder()
                .description(Calls2CallStanza.ELEMENT)
                .attribute("to", to)
                .attribute("id", callId)
                .content(action)
                .build();
    }

    /**
     * Builds the {@code <accept>} action for answering a call.
     *
     * <p>The accept echoes the server-allocated {@code <relay>} block as its first child (with each
     * endpoint's client-to-relay round-trip hints stripped) so the server can complete relay allocation,
     * and advertises the callee's standard capability and the offered audio codecs; when the offer carried
     * video it also echoes the video codec descriptor ({@code <video dec="H264" device_orientation="0"/>},
     * no {@code enc}) so the caller learns the callee will decode the video stream. The camera-on intent
     * flows to the media-plane bring-up and the {@code <video>} state announcement rather than to this
     * element.
     *
     * @param callId  the call identifier
     * @param creator the call creator's device JID
     * @param relay   the offered {@code <relay>} block to echo, or {@code null} when none was offered
     * @param video   whether the offered call carried video, so the accept echoes the video codec
     * @return the accept action
     */
    private AcceptStanza buildAccept(String callId, Jid creator, Stanza relay, boolean video) {
        var capabilities = List.of(standardCapability());
        var audioCodecs = standardAudioCodecs();
        var videoCodecs = video ? acceptVideoCodecs() : List.<CallCodecDescriptor>of();
        var acceptRelay = relay == null ? null
                : RelayInfo.of(relay).map(RelayInfo::withoutEndpointRoundTripHints).orElse(null);
        return new AcceptStanza(callId, creator, NET_MEDIUM_ACCEPT, capabilities, audioCodecs, videoCodecs,
                List.of(), null, CallEncOptions.standard(), null, acceptRelay);
    }

    /**
     * Builds the {@code <preaccept>} action acknowledging an inbound offer.
     *
     * <p>When the offer carried video the preaccept echoes the video codec descriptor
     * ({@code <video dec="H264" device_orientation="0" screen_width="0" screen_height="0"/>}, no
     * {@code enc}) alongside the audio so the caller learns the alerting device will decode the video.
     *
     * @param callId  the call identifier
     * @param creator the call creator's device JID
     * @param video   whether the offered call carried video, so the preaccept echoes the video codec
     * @return the preaccept action
     */
    private PreacceptStanza buildPreaccept(String callId, Jid creator, boolean video) {
        var capabilities = List.of(standardCapability());
        var audioCodecs = standardAudioCodecs();
        var videoCodecs = video ? preacceptVideoCodecs() : List.<CallCodecDescriptor>of();
        return new PreacceptStanza(callId, creator, capabilities, audioCodecs, videoCodecs, null,
                CallEncOptions.standard());
    }

    /**
     * Sends an offer and returns the server's synchronous ack.
     *
     * <p>The offer rides {@link Calls2OfferAckSender} so the calling virtual thread blocks for the
     * relay-bearing ack; the failure-to-send is surfaced as a non-fatal call exception.
     *
     * @param callId the call identifier, for diagnostics
     * @param self   the local device JID, the envelope sender context
     * @param peer   the peer user JID, the envelope recipient
     * @param offer  the offer action to wrap and send
     * @return the server's ack stanza
     * @throws WhatsAppCallException if the offer could not be sent or no ack arrived
     */
    private Stanza sendOffer(String callId, Jid self, Jid peer, OfferStanza offer) {
        var envelope = Calls2CallStanza.toCall(offer, peer, callId);
        LOGGER.log(System.Logger.Level.DEBUG, "Sending offer for call {0} from {1} to {2}", callId, self, peer);
        return offerAckSender.sendOfferAndAwaitAck(envelope);
    }

    /**
     * Applies an offer's synchronous ack to a call, clearing the initial result or ending on a NACK.
     *
     * <p>A positive ack carries the caller's relay block and the denormalised {@code <voip_settings>}
     * engine parameter bundles; the controller records both and clears the
     * {@link Calls2CallResult#CALL_OFFER_ACK_NOT_RECEIVED} initial result by folding the
     * offer-ack-received event into the info-manager. The caller does NOT bring up its media plane here:
     * the offer-ack relay block carries placeholder credentials the relay rejects before the callee
     * answers, and the finalized credentials arrive in the peer's accept, so the caller defers the
     * bring-up to {@link #handlePeerAccept(AcceptStanza, Jid)}; the recorded bundles are fed to the voip-param
     * manager at that deferred bring-up, since the accept itself carries no {@code <voip_settings>}. A NACK
     * (an ack with an {@code error}) ends the call as unavailable.
     *
     * @implNote This implementation reproduces the caller-defers-media behaviour confirmed in the live
     * capture ({@code re/calls2-spec/captures/CAPTURE-FINDINGS.md}): the offer-ack relay block is allocated
     * before the callee answers and the relay drops an Allocate fired against it, so the relay path comes
     * up only from the accept relay; the caller records the offer-ack relay as a reference but brings media
     * up on the accept.
     *
     * @param orchestrated the outbound call the ack belongs to
     * @param ack          the server's ack stanza
     */
    private void applyOfferAck(Calls2OrchestratedCall orchestrated, Stanza ack) {
        var callId = orchestrated.callId();
        if (ack.hasAttribute("error")) {
            // The engine collapses every offer-ack error to ServerNack (no 404/434 sub-map as the accept
            // ack has); the call ends as a setup failure carrying that reason rather than UNKNOWN.
            LOGGER.log(System.Logger.Level.INFO, "Offer NACK for call {0}: error={1}", callId,
                    ack.getAttributeAsString("error", "?"));
            resultSink.accept(callId, Calls2CallResult.SERVER_NACK);
            tearDown(orchestrated, Calls2CallResult.SERVER_NACK.toEndReason(),
                    CallEventType.CALL_OFFER_NACK_RECEIVED);
            return;
        }
        infoUpdater.updateForEvent(callId, CallEventType.CALL_OFFER_ACK_RECEIVED);
        events.emit(CallEventType.CALL_OFFER_ACK_RECEIVED, new byte[0]);
        ack.getChild("relay").ifPresent(orchestrated::relay);
        orchestrated.offerAckVoipSettings(voipSettingsOf(ack));
        // The offer-ack relay block names the caller's relay set (placeholder credentials notwithstanding):
        // begin the relay-latency exchange now so the peer learns this side's per-relay latencies and the
        // accept-time bring-up can elect a relay both ends reach rather than the locally fastest one.
        startRelayLatencyExchange(orchestrated, orchestrated.relay().orElse(null));
    }

    /**
     * Recovers the end-to-end call key from an inbound one-to-one offer's per-device fanout.
     *
     * <p>Walks the offer's key distribution slots and Signal-decrypts the first slot that yields the
     * thirty-two-byte key, recording it on the orchestration handle. A group offer carries no key in the
     * offer (the key arrives post-join through a rekey), so no key is recovered and none is recorded.
     *
     * @param orchestrated the inbound call the key is recovered for
     * @param offer        the decoded inbound offer
     * @param senderJid    the device JID that authored the offer, the decryption sender
     */
    private void recoverOfferCallKey(Calls2OrchestratedCall orchestrated, OfferStanza offer, Jid senderJid) {
        for (var slot : offer.keyDistribution()) {
            var recovered = crypto.decryptCallKey(slot, senderJid);
            if (recovered.isPresent()) {
                orchestrated.callKey(recovered.get());
                return;
            }
        }
    }

    /**
     * Allocates and seeds the membership of a group call rung from an inbound offer.
     *
     * <p>Attaches a fresh {@link CallMembership} to the orchestration handle and, when the offer carries a
     * parseable {@code <group_info>} roster, reconciles the membership against it so the call's participant
     * set is populated from the offer before any {@code <group_update>} arrives. An inbound-offer group call
     * that skipped this would carry no membership, and {@link #handleGroupUpdate(GroupUpdateStanza)} would
     * drop every later roster reconcile against its {@code membership == null} guard; seeding here keeps the
     * inbound-offer group path symmetric with the placement
     * ({@link #startGroupCall(Jid, Collection, Jid, boolean, Calls2MediaStreams)}) and join
     * ({@link #joinCall(Jid, String, Jid, boolean)}) paths that allocate the membership up front. An
     * offer whose {@code <group_info>} is absent or does not parse leaves an empty membership the first
     * {@code <group_update>} populates.
     *
     * @implNote This implementation mirrors the receive-side seeding of the participant set from the offer
     * roster the native engine performs before the first {@code post_process_group_info} (fn10987): the
     * offer's {@code <group_info>} is the same roster shape the {@code <group_update>} carries, so it is
     * reconciled through the same {@link CallMembership#reconcile(GroupInfoStanza)} the update uses.
     *
     * @param orchestrated the inbound group call whose membership is allocated
     * @param offer        the decoded inbound group offer
     */
    private static void attachOfferMembership(Calls2OrchestratedCall orchestrated, OfferStanza offer) {
        var membership = new CallMembership(orchestrated.callId());
        orchestrated.membership(membership);
        offer.groupInfoNode()
                .flatMap(GroupInfoStanza::of)
                .ifPresent(membership::reconcile);
    }

    /**
     * Transitions a call to a new internal state through the guard and fires the state event on success.
     *
     * <p>Mirrors the native split between {@code change_call_state_no_event} (the guard) and
     * {@code change_call_state} (its event-firing wrapper): the guard runs first through
     * {@link Calls2CallStateTransition#transition(String, Calls2CallState)}, and only when it accepts a
     * real change does the controller mirror the new state on the orchestration handle, publish the public
     * projection onto the {@link Call} view, fold the lifecycle event into the result snapshot, and emit
     * the lifecycle event. The {@code call_state_event} ({@link CallEventType#CALL_STATE_CHANGED}) is fired
     * for every accepted change except the silent {@link Calls2CallState#LINK} and
     * {@link Calls2CallState#ENDING} transitions, which the guard treats as event-free, and except when
     * the {@code lifecycleEvent} already is {@link CallEventType#CALL_STATE_CHANGED} (a state change with no
     * distinct lifecycle event of its own), so it is emitted once rather than twice. The
     * {@code lifecycleEvent} (for the link transition, the call-link-state event) is always emitted. A
     * guard rejection or a no-op transition to the current state fires no event.
     *
     * @param orchestrated   the call being transitioned
     * @param newState       the target internal state
     * @param lifecycleEvent the lifecycle event this transition raises
     */
    private void transition(Calls2OrchestratedCall orchestrated, Calls2CallState newState,
                            CallEventType lifecycleEvent) {
        var callId = orchestrated.callId();
        var prior = stateMachine.transition(callId, newState).orElse(null);
        if (prior == null || prior == newState) {
            return;
        }
        orchestrated.state(newState);
        orchestrated.call().setState(newState.toPublic());
        infoUpdater.updateForEvent(callId, lifecycleEvent);
        events.emit(lifecycleEvent, new byte[0]);
        var silent = newState == Calls2CallState.LINK || newState == Calls2CallState.ENDING;
        if (!silent && lifecycleEvent != CallEventType.CALL_STATE_CHANGED) {
            events.emit(CallEventType.CALL_STATE_CHANGED, new byte[0]);
        }
    }

    /**
     * Tears a call down: cancels its timers, closes its media plane, ends its state, and frees its handle.
     *
     * <p>Sets the public end reason, drives the internal state to {@link Calls2CallState#NONE}, emits the
     * supplied end event, hands the finished engine {@link Calls2CallContext} to the
     * {@linkplain #bindCallLogSink(BiConsumer) bound} outbound call-log sink, reports the call identifier to
     * the {@linkplain #bindTeardownSink(Consumer) bound} service teardown sink so the call's service-level
     * state is freed and its end-of-call WAM telemetry drains, cancels every per-call timer, closes the
     * media-plane session if one is up, and removes the orchestration handle. The path to
     * {@link Calls2CallState#NONE} depends on the current state: from a setup or link state the call first
     * passes through the silent {@link Calls2CallState#ENDING} transition and then the silent
     * {@link Calls2CallState#ENDING} to {@link Calls2CallState#NONE} teardown, both event-free under the
     * guard; from an in-call state ({@link Calls2CallState#CALL_ACTIVE} or
     * {@link Calls2CallState#CONNECTED_LONELY}) the guard forbids the {@link Calls2CallState#ENDING} hop, so
     * the teardown takes the legal direct {@link Calls2CallState#NONE} edge those states permit. The call-log
     * sink and the service teardown sink each fire once per call after the {@link Calls2CallState#NONE}
     * transition has closed out the context's durations; they are independent end-of-call outputs, and a
     * call-log, teardown-sink, or media-plane failure is swallowed so a teardown always completes.
     *
     * @implNote This implementation reproduces the teardown of {@code call_manager_end_call} (fn10733) and
     * {@code stop_call_timer_worker_thread} (fn10952): the {@code change_call_state} to
     * {@link Calls2CallState#NONE}, the cancel of every timer, and the free of the context. The
     * {@link Calls2CallState#ENDING} hop is attempted only from the states the guard
     * ({@code change_call_state_no_event}, fn10920) accepts it from; the two closed in-call states reach
     * {@link Calls2CallState#NONE} directly, because the guard's closed-set check rejects an in-call to
     * {@link Calls2CallState#ENDING} transition before its silent-ending shortcut, exactly as the native
     * guard does. The end event the controller emits is the lifecycle event for the cause of the teardown;
     * the public {@link Call} view is moved to {@link CallState#ENDED} through the
     * {@link Calls2CallState#NONE} projection.
     *
     * @param orchestrated the call being torn down
     * @param reason       the end reason published on the public call view
     * @param endEvent     the lifecycle event for the cause of the teardown
     */
    private void tearDown(Calls2OrchestratedCall orchestrated, CallEndReason reason, CallEventType endEvent) {
        var callId = orchestrated.callId();
        orchestrated.call().setEndReason(reason);
        infoUpdater.updateForEvent(callId, endEvent);
        events.emit(endEvent, new byte[0]);

        var current = orchestrated.state();
        if (current != Calls2CallState.CALL_ACTIVE && current != Calls2CallState.CONNECTED_LONELY) {
            stateMachine.transition(callId, Calls2CallState.ENDING);
        }
        stateMachine.transition(callId, Calls2CallState.NONE);
        orchestrated.state(Calls2CallState.NONE);
        orchestrated.call().setState(CallState.ENDED);
        events.emit(CallEventType.CALL_STATE_CHANGED, new byte[0]);

        recordCallLog(orchestrated, reason);
        notifyTeardown(callId);

        timers.cancelAll(callId);
        orchestrated.controls().ifPresent(controls -> {
            try {
                controls.close();
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "In-call control close failed for call {0}: {1}", callId, e.getMessage());
            }
        });
        orchestrated.mediaSession().ifPresent(session -> {
            try {
                session.close();
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Media plane close failed for call {0}: {1}", callId, e.getMessage());
            }
        });
        registry.release(callId);
        calls.remove(callId);
    }

    /**
     * Hands a torn-down call's finished engine context to the bound outbound call-log sink.
     *
     * <p>Resolves the call's engine {@link Calls2CallContext} from its orchestration handle and, when the
     * context is present, fires the bound sink once with the context and the terminal {@link CallEndReason}.
     * A call whose registry allocated no context (a test registry with no manager) records no log, and a
     * controller whose sink is unbound runs the no-op default sink, so an unbound build records nothing. Any
     * failure the sink raises is caught and logged so the teardown that called this always completes; the
     * call-log push is best-effort exactly as the WAM commit on the same teardown is.
     *
     * @implNote This implementation fires the sink after the {@link Calls2CallState#NONE} transition so the
     * context's active and lonely duration accumulators are already closed out, matching the native
     * end-of-call log build that reads the finished durations. It is kept independent of the WAM stats drain
     * the call service runs from the same teardown moment: the two are separate end-of-call outputs and are
     * never conflated.
     *
     * @param orchestrated the call being torn down, accessed under its lock
     * @param reason       the terminal end reason handed to the sink
     */
    private void recordCallLog(Calls2OrchestratedCall orchestrated, CallEndReason reason) {
        var context = orchestrated.engineContext().orElse(null);
        if (context == null) {
            return;
        }
        try {
            callLogSink.accept(context, reason);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Call-log sink failed for call {0}: {1}", orchestrated.callId(), e.getMessage());
        }
    }

    /**
     * Reports a torn-down call's identifier to the bound service teardown sink.
     *
     * <p>Fires the {@linkplain #bindTeardownSink(Consumer) bound} sink once with the ended call's
     * identifier, so the call service removes the call from its registry and store and drains its
     * end-of-call WAM telemetry. A controller whose sink is unbound runs the no-op default sink, so an
     * unbound build drains no service-level telemetry. Any failure the sink raises is caught and logged so
     * the teardown that called this always completes; the service-level drain is best-effort exactly as the
     * call-log push on the same teardown is.
     *
     * @implNote This implementation fires the sink after the {@link Calls2CallState#NONE} transition and
     * the public {@link CallState#ENDED} projection, so the call view the service reads carries the terminal
     * end reason when its WAM Call event is built. It is kept independent of the call-log sink because the
     * service-level teardown (registry removal plus WAM commit) and the app-state call-log push are separate
     * end-of-call outputs.
     *
     * @param callId the identifier of the call being torn down
     */
    private void notifyTeardown(String callId) {
        try {
            teardownSink.accept(callId);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Teardown sink failed for call {0}: {1}", callId, e.getMessage());
        }
    }

    /**
     * Returns the orchestration handle for a tracked call.
     *
     * @param callId the call identifier
     * @return the orchestration handle
     * @throws IllegalArgumentException if no call exists for {@code callId}
     */
    private Calls2OrchestratedCall require(String callId) {
        var orchestrated = calls.get(callId);
        if (orchestrated == null) {
            throw new IllegalArgumentException("No active call for id " + callId);
        }
        return orchestrated;
    }

    /**
     * Validates that admitting a new call would not exceed the dual-call ceiling.
     *
     * @throws IllegalStateException if a primary and a secondary call are already live
     */
    private void admitNewCall() {
        if (calls.size() >= MAX_CONCURRENT_CALLS) {
            throw new IllegalStateException(
                    "Cannot start a call: the dual-call ceiling of " + MAX_CONCURRENT_CALLS + " is reached");
        }
    }

    /**
     * Generates a fresh thirty-two-character call identifier.
     *
     * <p>Draws sixteen cryptographically strong random bytes from the host and hex-encodes each byte's
     * high nibble then its low nibble through the mixed-case translation table, so the identifier's case
     * varies across its characters exactly as the engine's identifiers do.
     *
     * @implNote This implementation reproduces {@code generate_call_id} (fn10675 seed, fn10731 encode):
     * sixteen random bytes hex-encoded through the table {@code "0123456789ABCDEF0123456789abcdef"} into a
     * thirty-two-character identifier. The engine's one-time RNG seed {@code (os_rand & 0x0fffffff) |
     * 0xa0000000} is a property of its own generator; Cobalt draws the bytes from
     * {@link VoipHostApi#randomBytes(int)}, which is backed by a cryptographically strong source, so the
     * seed step has no Cobalt analogue.
     *
     * @return the generated call identifier
     */
    private String generateCallId() {
        var random = host.randomBytes(CALL_ID_RANDOM_BYTES);
        var chars = new char[CALL_ID_RANDOM_BYTES * 2];
        for (var i = 0; i < random.length; i++) {
            var unsigned = random[i] & 0xff;
            chars[i * 2] = CALL_ID_HEX[unsigned >>> 4];
            chars[i * 2 + 1] = CALL_ID_HEX[unsigned & 0x0f];
        }
        return new String(chars);
    }

    /**
     * Returns the caller's standard capability advertisement.
     *
     * <p>Wraps the reference client's self-advertisement bytes in a {@code <capability ver="1">} element,
     * the capability set the live captures show every device advertising.
     *
     * @return the standard capability
     */
    private static CallCapability standardCapability() {
        return new CallCapability(CAPABILITY_VERSION, VoipCapabilities.standard().serialize());
    }

    /**
     * Returns the offered audio formats: Opus at the narrowband and wideband sampling rates.
     *
     * <p>The live captures show the offer advertising {@code <audio enc="opus" rate="8000"/>} and
     * {@code <audio enc="opus" rate="16000"/>} as flat elements, so the offer advertises the Opus codec
     * at each rate.
     *
     * @return the offered audio format descriptors
     */
    private static List<CallCodecDescriptor> standardAudioCodecs() {
        return List.of(
                CallCodecDescriptor.audio(AUDIO_CODEC_OPUS, AUDIO_RATE_NARROWBAND),
                CallCodecDescriptor.audio(AUDIO_CODEC_OPUS, AUDIO_RATE_WIDEBAND));
    }

    /**
     * Returns the offered video format for a video call: a single flat {@code <video>} element
     * advertising H.264.
     *
     * <p>The live captures show every video offer carrying one
     * {@code <video dec="H264" enc="h.264" device_orientation="0" screen_width="0" screen_height="0"/>}
     * element, with the device orientation and screen geometry zero until the camera starts, so the
     * offer advertises that single element rather than a per-codec list.
     *
     * @return the offered video format descriptor
     */
    private static List<CallCodecDescriptor> standardVideoCodecs() {
        return List.of(CallCodecDescriptor.video(VIDEO_DEC_TOKEN, VIDEO_ENC_NAME, 0, 0, 0));
    }

    /**
     * Returns the callee video codec descriptor echoed in an accept for a video call.
     *
     * <p>The accept advertises the negotiated decode codec without an {@code enc} encode name or screen
     * geometry: the live captures show the answer carrying a flat
     * {@code <video dec="H264" device_orientation="0"/>}, the lighter form the offer's full descriptor is
     * trimmed to once the codec is agreed.
     *
     * @return the accept video format descriptor
     */
    private static List<CallCodecDescriptor> acceptVideoCodecs() {
        return List.of(CallCodecDescriptor.video(VIDEO_DEC_TOKEN, null, 0, -1, -1));
    }

    /**
     * Returns the callee video codec descriptor echoed in a preaccept for a video call.
     *
     * <p>The preaccept advertises the decode codec with the screen geometry but without an {@code enc}
     * encode name: the live captures show the early ring-ack carrying a flat
     * {@code <video dec="H264" device_orientation="0" screen_width="0" screen_height="0"/>}.
     *
     * @return the preaccept video format descriptor
     */
    private static List<CallCodecDescriptor> preacceptVideoCodecs() {
        return List.of(CallCodecDescriptor.video(VIDEO_DEC_TOKEN, null, 0, 0, 0));
    }

    /**
     * Returns whether any offer key-distribution slot is a pre-key message.
     *
     * <p>An offer whose any per-device {@code <enc>} is a {@code pkmsg} bootstraps a Signal session, so the
     * offer must carry the local device identity; this reports whether that is the case so the offer
     * builder attaches the identity only when needed.
     *
     * @param keyDistribution the per-device key fanout
     * @return {@code true} when any slot is an encrypted pre-key-message envelope
     */
    private static boolean anyPreKeyMessage(List<CallKeyDistribution> keyDistribution) {
        return keyDistribution.stream()
                .filter(CallKeyDistribution::isEncrypted)
                .anyMatch(slot -> slot.typeValue().map("pkmsg"::equals).orElse(false));
    }

    /**
     * Bundles a call's in-call control units, built once the call is answerable and closed on teardown.
     *
     * <p>This is the per-call holder the controller stores on a {@link Calls2OrchestratedCall} once the call
     * becomes answerable: the four signaling-plane control units the public in-call control methods and the
     * inbound action handlers drive, all wired onto a single {@link ControlEventBridge} over the shared host
     * bus and a single {@link CallSignalingSender}, plus the three application-data-plane control units
     * (reactions, live transcription, IMU) when the call's media plane carries an {@link AppDataController},
     * plus the two call-link control units (the call-link controller and the waiting-room controller) when
     * the call was joined through a call-link token. The three app-data units are {@code null} for a call
     * whose transport carries no app-data plane (or that is torn down before its media plane is up), and the
     * two call-link units are {@code null} for a call not joined through a link, so consumers must null-check
     * them; the four signaling-plane units are always present. Of the bundled units only {@link MuteController}
     * owns timers and is {@link AutoCloseable}, so {@link #close()} closes it and the rest are released with
     * the handle; the app-data units own no timers of their own (the reaction-clear and retransmission timers
     * live on the {@link AppDataController}, closed with the media session), and the two call-link units own
     * no timers either, so closing the holder simply drops them.
     *
     * @implNote This implementation groups the per-call control units the engine binds to an answerable
     * call; it carries no state of its own beyond the unit references and a single closeable. The app-data
     * units are nullable because they are built only once the media plane exposes an {@link AppDataController}
     * (SPEC 17.2: reactions and transcripts ride the app-data side-channel, not signaling), unlike the four
     * signaling-plane units which are always built when the bus is bound. The two call-link units are
     * nullable because they are built only on the call-link join path
     * ({@link #joinCallLink(Jid, String, CallLinkMedia, boolean, Calls2MediaStreams)}) and folded into the
     * bundle through {@link #withCallLink(CallLinkController, WaitingRoomController)} after the call is
     * answered.
     * @param mute          the mute control unit; never {@code null}
     * @param video         the video-state control unit; never {@code null}
     * @param screenShare   the screen-share control unit; never {@code null}
     * @param raiseHand     the raise-hand control unit; never {@code null}
     * @param reaction      the reaction control unit, or {@code null} when the call carries no app-data plane
     * @param transcription the live-transcription control unit, or {@code null} when the call carries no
     *                      app-data plane
     * @param imu           the IMU control unit, or {@code null} when the call carries no app-data plane
     * @param callLink      the call-link control unit, or {@code null} when the call was not joined through a
     *                      call-link token
     * @param waitingRoom   the waiting-room control unit, or {@code null} when the call was not joined through
     *                      a call-link token
     */
    record Calls2CallControls(MuteController mute, VideoStateController video,
                              ScreenShareController screenShare, RaiseHandController raiseHand,
                              ReactionController reaction, LiveTranscriptionController transcription,
                              ImuDataController imu, CallLinkController callLink,
                              WaitingRoomController waitingRoom)
            implements AutoCloseable {
        /**
         * Canonicalizes the holder over its control units.
         *
         * <p>The four signaling-plane units are required; the three app-data units and the two call-link
         * units are optional and may be {@code null} for a call whose transport carries no app-data plane or
         * that was not joined through a call-link token.
         *
         * @throws NullPointerException if any of {@code mute}, {@code video}, {@code screenShare}, or
         *                              {@code raiseHand} is {@code null}
         */
        Calls2CallControls {
            Objects.requireNonNull(mute, "mute cannot be null");
            Objects.requireNonNull(video, "video cannot be null");
            Objects.requireNonNull(screenShare, "screenShare cannot be null");
            Objects.requireNonNull(raiseHand, "raiseHand cannot be null");
        }

        /**
         * Returns a copy of this bundle carrying the given call-link and waiting-room control units.
         *
         * <p>The call-link join builds the two link units before the call is answered, then answers the call,
         * which builds this base bundle; this folds the two link units into the bundle so they are held for
         * the call's lifetime and dropped on teardown with the rest. The four signaling-plane units and the
         * three app-data units are carried over unchanged.
         *
         * @param callLink    the call-link control unit to carry
         * @param waitingRoom the waiting-room control unit to carry
         * @return a copy of this bundle carrying the two link units
         */
        Calls2CallControls withCallLink(CallLinkController callLink, WaitingRoomController waitingRoom) {
            return new Calls2CallControls(mute, video, screenShare, raiseHand, reaction, transcription, imu,
                    callLink, waitingRoom);
        }

        /**
         * Returns the call's reaction control unit, if the call carries an app-data plane.
         *
         * @return an {@link Optional} with the reaction control unit, or empty when the call carries no
         *         app-data plane
         */
        Optional<ReactionController> reactionControl() {
            return Optional.ofNullable(reaction);
        }

        /**
         * Closes the call's control units, shutting the mute controller's timers down.
         *
         * <p>Only {@link MuteController} owns a scheduler and is closeable; the video, screen-share, and
         * raise-hand units own no timers and need no shutdown. The reaction, transcription, and IMU units
         * own no timers of their own either; the reaction-clear and retransmission timers live on the
         * {@link AppDataController}, which the media session closes on teardown, so the app-data units are
         * simply dropped with this holder.
         */
        @Override
        public void close() {
            mute.close();
        }
    }
}
