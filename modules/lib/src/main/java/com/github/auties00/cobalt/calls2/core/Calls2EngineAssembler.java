package com.github.auties00.cobalt.calls2.core;

import com.github.auties00.cobalt.calls2.config.Calls2FeatureGate;
import com.github.auties00.cobalt.calls2.core.control.CallLinkIqSender;
import com.github.auties00.cobalt.calls2.crypto.CallKeyCryptography;
import com.github.auties00.cobalt.calls2.crypto.CallKeyExchange;
import com.github.auties00.cobalt.calls2.signaling.CallMessage;
import com.github.auties00.cobalt.calls2.platform.LiveAudioCaptureDriver;
import com.github.auties00.cobalt.calls2.platform.LiveAudioPlaybackDriver;
import com.github.auties00.cobalt.calls2.platform.LiveVoipHostApi;
import com.github.auties00.cobalt.calls2.platform.VoipDriverManager;
import com.github.auties00.cobalt.calls2.platform.VoipHostApi;
import com.github.auties00.cobalt.calls2.stream.VideoOutput;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppAccountStore;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppStore;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Assembles a live {@link Calls2LifecycleController} from a client's call-engine units.
 *
 * <p>The lifecycle controller depends only on its nine collaborating seams; this assembler is the one
 * place that binds each seam to the concrete unit that backs it for a live client, so the call service can
 * hold a fully wired engine without knowing how a transition, a timer, or an event reaches its unit. It
 * composes the units a live client already owns: the offer-ack send rides the client's id-correlated
 * {@link LinkedWhatsAppClient#sendNode(StanzaBuilder) sendNode}, the call-key
 * crypto is the {@link CallKeyCryptography} facade over the reused Signal pipeline, signaling egress and
 * randomness come
 * from {@link LiveVoipHostApi}, the at-most-two call contexts live in a single {@link Calls2CallManager},
 * the transition guard is the {@link Calls2CallStateMachine} over that manager, the per-call timers run on
 * {@link Calls2CallTimers} virtual-thread drivers, and the call-info snapshots refresh through a single
 * {@link Calls2CallInfoManager}.
 *
 * <p>The engine is fully live across every seam. The signaling, ringing, state, timer, and listener path
 * sequences a call end to end (an inbound offer rings and reaches the host, an accept, reject, preaccept,
 * and terminate ship on the socket, the state machine drives the call, and a fired watchdog or lonely
 * timeout tears the call down with the correct {@link CallEndReason}), and the {@link Calls2MediaPlane} is
 * the real {@link LiveMediaSession.LiveMediaPlane}: once a call is answered it brings up the relay
 * transport from the relay block and the call key, runs the Opus encode and decode pipeline over the call's
 * application capture and playback streams (a microphone- or speaker-bound stream carries its platform
 * device behind the same interface, and a call that supplied no stream falls back to opening a platform
 * device), and ships and receives media as hop-by-hop SRTP carried as SCTP DATA over the call's one
 * DTLS-wrapped SCTP data channel; the call's host {@link DatagramChannel} carries only the ICE connectivity
 * checks and DTLS records of that channel's bring-up, not raw media. The media plane reports its first
 * traffic back to the controller through a connection sink bound after the controller is built, so the call
 * advances to {@link Calls2CallState#CALL_ACTIVE} when media flows. A media-plane bring-up failure surfaces
 * as a non-fatal call exception the controller isolates to that one call.
 *
 * @apiNote This is an internal engine assembler, not a public surface; the call service is its only caller.
 * @implNote This implementation binds the engine's host boundary: the {@link Calls2CallContextRegistry}
 * adopts a {@link Calls2CallContext} built from the controller's {@link Call} (with the controller's call
 * id, so the {@link Calls2CallStateMachine} resolves the same context by id) into the manager, choosing the
 * primary slot first and the secondary slot when a primary call is already live; the
 * {@link Calls2CallTimerScheduler} owns one {@link Calls2CallTimers} driver per call id, mapping each
 * {@link Calls2CallTimerKind} to the matching {@link Calls2CallTimers.Timer}; the {@link Calls2CallInfoUpdater}
 * refreshes the single {@link Calls2CallInfoManager} from the resolved context's accumulated durations; and
 * the {@link Calls2CallEventSink} logs each gated lifecycle id. The typed host-facing listener events the
 * application observes ({@code onCall}, {@code onCallEnded}, and the in-call control events) are fanned out
 * by the call service and the in-call control units through {@link LiveCallEventBus}, not by this opaque
 * sink, because the per-event payload byte layout that would let this sink reconstruct the typed
 * {@link CallEvent} is not yet recovered (SPEC 17.1).
 */
public final class Calls2EngineAssembler {
    /**
     * The default capture geometry width, in pixels, the engine's video and screen-share capture sources
     * are opened at when the application supplied no video source of its own.
     *
     * <p>Standard-definition 640x480 matches the {@link VideoOutput#fromCamera()} and
     * {@link VideoOutput#fromScreen()} defaults and the media plane's default outbound video geometry, so a
     * driver-manager-sourced capture and an application-sourced capture size the encoder identically.
     */
    private static final int DEFAULT_CAPTURE_WIDTH = 640;

    /**
     * The default capture geometry height, in pixels, the engine's video and screen-share capture sources
     * are opened at when the application supplied no video source of its own.
     */
    private static final int DEFAULT_CAPTURE_HEIGHT = 480;

    /**
     * The default capture frame rate, in frames per second, the engine's video and screen-share capture
     * sources are opened at when the application supplied no video source of its own.
     */
    private static final int DEFAULT_CAPTURE_FPS = 30;

    /**
     * The default target encoder bitrate, in bits per second, advertised by a driver-manager-sourced
     * capture source.
     *
     * <p>Only the geometry of a driver-manager capture source sizes the encoder; the advertised bitrate is
     * a positive placeholder the rate controller adapts, so this carries the {@link VideoOutput} default of
     * one megabit per second rather than a negotiated value.
     */
    private static final int DEFAULT_CAPTURE_BITRATE_BPS = 1_000_000;

    /**
     * Hidden constructor; this assembler exposes only its static factory.
     */
    private Calls2EngineAssembler() {
        throw new AssertionError("No instances");
    }

    /**
     * Builds the engine's single {@link VoipDriverManager} over fresh platform capture and playback drivers
     * and the default camera and screen-share source factories.
     *
     * <p>The manager owns the two audio capture drivers (microphone and system-audio loopback), the audio
     * playback driver, and the camera and screen-share video source factories for the lifetime of the
     * engine; each brought-up media session routes its capture and playback bring-up through this one
     * manager rather than opening a device directly. The audio drivers back onto {@code javax.sound}
     * lines and the video factories open the platform default camera or screen as a {@link VideoOutput},
     * so a host without the corresponding device fails at capture-start time, which the media session
     * isolates, rather than at engine assembly. The returned manager is not yet
     * {@linkplain VoipDriverManager#initialize() initialized}; the caller initializes it once.
     *
     * @return a fresh, uninitialized driver manager owning the engine's capture and playback drivers
     */
    private static VoipDriverManager newVoipDriverManager() {
        return new VoipDriverManager(
                new LiveAudioCaptureDriver(),
                new LiveAudioCaptureDriver(),
                new LiveAudioPlaybackDriver(),
                deviceId -> VideoOutput.fromCamera(
                        DEFAULT_CAPTURE_WIDTH, DEFAULT_CAPTURE_HEIGHT, DEFAULT_CAPTURE_FPS,
                        DEFAULT_CAPTURE_BITRATE_BPS),
                surfaceId -> VideoOutput.fromScreen(
                        DEFAULT_CAPTURE_WIDTH, DEFAULT_CAPTURE_HEIGHT, DEFAULT_CAPTURE_FPS,
                        DEFAULT_CAPTURE_BITRATE_BPS));
    }

    /**
     * Assembles a live {@link Calls2LifecycleController} for a client over its call-engine units.
     *
     * <p>Builds the call-key crypto facade over the supplied Signal pipeline, a {@link LiveVoipHostApi}
     * whose signaling rides the client and whose {@code call_sendto} host datagram seam is a real
     * {@link DatagramChannel}, the
     * engine's single {@link VoipDriverManager} (initialized once here, owning the audio capture and
     * playback drivers and the camera and screen-share source factories every brought-up media session
     * routes its capture and playback through), the real {@link LiveMediaSession.LiveMediaPlane} over that
     * host and driver manager, a {@link Calls2CallManager} with its {@link Calls2CallStateMachine} and
     * {@link Calls2CallInfoManager}, and the per-call timer scheduler, then threads them into the controller
     * as its nine seams, binds the media plane's connection sink to the controller's
     * {@link Calls2LifecycleController#onMediaConnected(String) media-connected} entry point, binds a
     * {@link Calls2FeatureGate} over the client's AB-props service onto the controller so the start,
     * group-start, and screen-share entry points are gated on the server calling feature flags, and binds a
     * store-backed own-device predicate onto the controller so the companion-device terminate guard can tell
     * a terminate authored by another device of the local account from one authored by the remote peer. The
     * same gate supplies the media plane's group-call initial-BWE seed decision
     * ({@link Calls2FeatureGate#isInitBweForGroupCallEnabled()}), read per bring-up.
     *
     * @param whatsapp          the owning client, used for signaling egress and the offer-ack round-trip
     * @param messageEncryption the encryption service the call-key crypto wraps the key with
     * @param messageService    the message service the call-key crypto decrypts inbound key envelopes with
     * @param deviceService     the device service the call-key crypto ensures sessions through
     * @param store             the store supplying the local ADV-signed device identity
     * @param abPropsService    the AB-props service the {@link Calls2FeatureGate} reads its calling feature
     *                          flags from, bound onto the controller so the start, group-start, and
     *                          screen-share entry points are gated
     * @param eventBus          the shared host event bus the in-call control units publish their host-facing
     *                          events onto, bound onto the controller so an answerable call gets its control
     *                          units
     * @return a fully wired lifecycle controller
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Calls2LifecycleController assemble(LinkedWhatsAppClient whatsapp,
                                                     MessageEncryption messageEncryption,
                                                     MessageService messageService,
                                                     DeviceService deviceService,
                                                     LinkedWhatsAppStore store,
                                                     ABPropsService abPropsService,
                                                     LiveCallEventBus eventBus) {
        Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        Objects.requireNonNull(messageEncryption, "messageEncryption cannot be null");
        Objects.requireNonNull(messageService, "messageService cannot be null");
        Objects.requireNonNull(deviceService, "deviceService cannot be null");
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        Objects.requireNonNull(eventBus, "eventBus cannot be null");

        var secureRandom = new SecureRandom();
        CallKeyExchange crypto =
                new CallKeyCryptography(messageEncryption, messageService, deviceService, store, secureRandom);
        var manager = new Calls2CallManager();
        var stateMachine = new Calls2CallStateMachine(manager);
        var infoManager = new Calls2CallInfoManager();
        var events = new LoggingEventSink();
        VoipHostApi host = new LiveVoipHostApi(whatsapp, new LiveDatagramSink(), frame -> {
        }, (eventType, payload) -> {
        });

        var selfJid = new AtomicReference<>(ownLidDeviceJid(store).orElse(null));
        var connectionSink = new AtomicReference<Consumer<String>>();
        var voipDriverManager = newVoipDriverManager();
        voipDriverManager.initialize();
        // The feature gate is built once here and shared: it backs the controller's start/group-start/
        // screen-share gating and supplies the group-call initial-BWE seed decision the media plane threads
        // into each group call's rate-control loop (ENABLE_INIT_BWE_FOR_GROUP_CALL), read per bring-up so the
        // AB-props cache is warm by the time a call starts rather than read on a cold cache here at assembly.
        var featureGate = new Calls2FeatureGate(abPropsService);
        var mediaPlane = new LiveMediaSession.LiveMediaPlane(host, selfJid, connectionSink, voipDriverManager,
                featureGate::isInitBweForGroupCallEnabled);

        var timers = new PerCallTimerScheduler(manager, events, featureGate);
        var controller = new Calls2LifecycleController(
                new ClientOfferAckSender(whatsapp),
                crypto,
                host,
                new ManagerContextRegistry(manager, timers),
                stateMachine,
                timers,
                new InfoManagerUpdater(manager, infoManager),
                events,
                mediaPlane);
        timers.bindController(controller);
        timers.bindGroupOutboundResolver(controller::groupOutbound);
        connectionSink.set(controller::onMediaConnected);
        controller.bindEventBus(eventBus, () -> store.accountStore().jid().orElse(null));
        controller.bindFeatureGate(featureGate);
        controller.bindOwnDeviceResolver(deviceJid -> isOwnDevice(store, deviceJid));
        controller.bindCallLinkIqSender(new LiveCallLinkIqSender(whatsapp));
        return controller;
    }

    /**
     * Reports whether a device JID is one of the local account's own devices for the companion-device
     * terminate guard.
     *
     * <p>A device JID belongs to the local account when it resolves to the same account as the account's
     * own phone-number JID or LID, or when it appears in the account's linked-device list; the
     * account-equality test normalizes the device and agent suffixes away so a device JID
     * ({@code user:device@server}) matches the bare account JID. The store is read live on each call so a
     * self JID, LID, or linked-device set that became known after assembly is picked up without rebinding.
     *
     * @implNote This implementation reproduces the local-account membership the native receive path encodes
     * in the {@code ctx[0x11c]} companion flag: a terminate authored by another device of the local account
     * is a companion terminate. The own-account match is {@link Jid#isSameAccount(Jid)} against both the
     * phone-number JID and the LID (a call's signaling sender may use either addressing mode), with the
     * linked-device list as the explicit enumeration of the account's companions when the self identity is
     * not yet populated.
     *
     * @param store     the store whose account sub-store supplies the local identity and device list
     * @param deviceJid the device JID to test, never {@code null}
     * @return {@code true} when {@code deviceJid} is one of the local account's own devices
     */
    private static boolean isOwnDevice(LinkedWhatsAppStore store, Jid deviceJid) {
        var accountStore = store.accountStore();
        if (accountStore.jid().map(deviceJid::isSameAccount).orElse(false)
                || accountStore.lid().map(deviceJid::isSameAccount).orElse(false)) {
            return true;
        }
        for (var linkedDevice : accountStore.linkedDevices()) {
            if (deviceJid.isSameAccount(linkedDevice)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the local account's own LID device JID, the {@code <lid>:<device>@lid} form the call media
     * plane keys its SSRCs and SFrame keys on.
     *
     * <p>The media plane derives the deterministic per-device media SSRCs and the per-participant SFrame base
     * key from the self device JID, and the native {@code call_generate_device_ssrc} and SFrame derivation
     * key on the participant's {@code <lid>:<device>@lid} device JID, the form
     * {@link com.github.auties00.cobalt.calls2.core.participant.CallSecureSsrcGenerator} is byte-verified
     * against. This composes that JID from the account LID (the user part and the {@code @lid} domain) and the
     * device number carried on the account phone-number JID, so a peer that pre-registers a receive context
     * for the self device recognises the stamped SSRCs. When the session is not yet LID-paired (no account
     * LID) this falls back to the account phone-number JID so a degenerate pre-LID session still seeds a JID;
     * the media plane's own random-layout fallback covers a fully unseeded holder.
     *
     * @implNote This implementation keys the media plane on the LID device JID rather than the account
     * phone-number JID, fixing the divergence where the self-JID holder carried the
     * {@link LinkedWhatsAppAccountStore#jid()} phone-number form: the native
     * {@code call_generate_device_ssrc} (fn10901) and {@code derive_sframe_key} (fn10896) key on the device's
     * {@code <lid>:<device>@lid} JID (verified in re/calls2-spec/captures/group-sframe-frame.json against
     * device JID {@code 83116928594056:2@lid}), so the self device JID must be the LID device form for the
     * stamped SSRCs and the SFrame base key to match the byte-verified live values. The device number is read
     * from the account JID because the account LID is stored in its bare user form; combining the LID user
     * with that device number yields the device JID. The result is the seed of the media plane's self-JID
     * holder, which the plane reads at each call bring-up rather than capturing here.
     *
     * @param store the store whose account sub-store supplies the LID and the device number
     * @return the own LID device JID, or the account phone-number JID when no LID is set, or empty when the
     *         account is not yet paired
     */
    private static Optional<Jid> ownLidDeviceJid(LinkedWhatsAppStore store) {
        var accountStore = store.accountStore();
        var lid = accountStore.lid().orElse(null);
        if (lid == null) {
            return accountStore.jid();
        }
        var device = accountStore.jid().map(Jid::device).orElse(0);
        return Optional.of(lid.withServer(JidServer.lid()).withDevice(device));
    }

    /**
     * Sends an offer envelope on the client's id-correlated socket and returns its synchronous call ack.
     *
     * <p>The controller builds the {@code <call>} offer envelope and hands it here as a built {@link Stanza};
     * the client's value-returning send takes a {@link StanzaBuilder} and
     * auto-injects the stanza id, so this sender re-wraps the envelope's recipient and offer child into a
     * fresh builder and blocks on {@link LinkedWhatsAppClient#sendNode(StanzaBuilder)}
     * for the relay-bearing ack.
     */
    private record ClientOfferAckSender(LinkedWhatsAppClient whatsapp) implements Calls2OfferAckSender {
        /**
         * Canonicalizes the sender over its client.
         *
         * @throws NullPointerException if {@code whatsapp} is {@code null}
         */
        private ClientOfferAckSender {
            Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote This implementation reads the {@code to} recipient and the single offer child off the
         * built envelope and rebuilds the {@code <call>} envelope as a
         * {@link StanzaBuilder} so the client send can stamp the dispatcher
         * id, then blocks for the matching {@code <ack class="call">}. A
         * {@link WhatsAppCallException.DataChannel} surfaces a send failure as the non-fatal call exception
         * the controller expects.
         */
        @Override
        public Stanza sendOfferAndAwaitAck(Stanza offerEnvelope) {
            Objects.requireNonNull(offerEnvelope, "offerEnvelope cannot be null");
            var to = offerEnvelope.getAttributeAsJid("to")
                    .orElseThrow(() -> new WhatsAppCallException.DataChannel("offer envelope has no recipient"));
            var child = offerEnvelope.getChild()
                    .orElseThrow(() -> new WhatsAppCallException.DataChannel("offer envelope has no action"));
            var builder = new StanzaBuilder()
                    .description(offerEnvelope.description())
                    .attribute("to", to)
                    .content(child);
            try {
                return whatsapp.sendNode(builder);
            } catch (RuntimeException exception) {
                throw new WhatsAppCallException.DataChannel("could not send call offer", exception);
            }
        }
    }

    /**
     * Dispatches a call-link or waiting-room request-reply IQ over the client's id-correlated socket and
     * returns the echoed action stanza of the reply.
     *
     * <p>This is the live {@link CallLinkIqSender} the call-link and waiting-room control units depend on. It
     * wraps the typed request's {@linkplain CallMessage#toStanza() action stanza} in the
     * {@code <call to="call">} envelope addressed to the {@code call} service and blocks on
     * {@link LinkedWhatsAppClient#sendNode(StanzaBuilder)} for the matching {@code <ack class="call">} reply,
     * then unwraps the reply's echoed action child so the controller's ack parser receives the
     * {@code <link_query>}, {@code <link_join>}, or {@code <waiting_room>} stanza it expects rather than the
     * {@code <ack>} envelope.
     *
     * @implNote This implementation reproduces the {@code to="call"} SMAX round trip the native call-link and
     * waiting-room IQ senders of module {@code ff-tScznZ8P} ({@code protocol/xmpp/stanzas/call_link.cc} and
     * {@code waiting_room.cc}) perform, modeled on the proven legacy
     * {@link com.github.auties00.cobalt.stanza.smax.voip.SmaxLinkQueryRequest} wire shape
     * ({@code <call to="call"><link_query/></call>}). The per-operation native message-type code (query
     * {@code 0x84}, admit {@code 0x47}, deny {@code 0x49}) is the relay's internal classification and is not
     * stamped as a wire attribute, because the relay routes the request on its single child element tag, the
     * same tag the request record renders; the proven legacy query and toggle RPCs likewise carry no
     * {@code type} attribute on the {@code <call>} envelope. The reply is the {@code <ack class="call">}
     * envelope whose single echoed action child each ack parser
     * ({@link com.github.auties00.cobalt.calls2.signaling.LinkQueryAck#of(Stanza)},
     * {@link com.github.auties00.cobalt.calls2.signaling.LinkJoinAck#of(Stanza)},
     * {@link com.github.auties00.cobalt.calls2.signaling.WaitingRoomAdmitAck#of(Stanza)}) parses, so this sender
     * returns that child; an error reply with no echoed child returns the envelope so the parser surfaces the
     * missing required attribute.
     */
    private record LiveCallLinkIqSender(LinkedWhatsAppClient whatsapp) implements CallLinkIqSender {
        /**
         * The wire element tag of the call signaling envelope and of its acknowledgement.
         */
        private static final String CALL_ELEMENT = "call";

        /**
         * The wire attribute naming the recipient on the {@code <call>} envelope.
         */
        private static final String TO_ATTRIBUTE = "to";

        /**
         * Canonicalizes the sender over its client.
         *
         * @throws NullPointerException if {@code whatsapp} is {@code null}
         */
        private LiveCallLinkIqSender {
            Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote This implementation wraps the request's action stanza in a {@code <call to="call">}
         * envelope, blocks on the client's id-correlated send for the {@code <ack class="call">} reply, and
         * returns the reply's echoed action child (preferring the child whose tag matches the request, then
         * the single child, then the envelope itself) so the controller's ack parser receives the echoed
         * action stanza rather than the {@code <ack>} wrapper. A send failure surfaces as a non-fatal
         * {@link WhatsAppCallException.DataChannel} the controller treats as the operation failing.
         */
        @Override
        public Stanza sendForReply(CallMessage request) {
            Objects.requireNonNull(request, "request cannot be null");
            var action = request.toStanza();
            var builder = new StanzaBuilder()
                    .description(CALL_ELEMENT)
                    .attribute(TO_ATTRIBUTE, JidServer.call())
                    .content(action);
            Stanza reply;
            try {
                reply = whatsapp.sendNode(builder);
            } catch (RuntimeException exception) {
                throw new WhatsAppCallException.DataChannel("could not send call-link IQ", exception);
            }
            return reply.getChild(action.description())
                    .or(reply::getChild)
                    .orElse(reply);
        }
    }

    /**
     * Allocates and frees engine call contexts in a {@link Calls2CallManager} on behalf of the controller.
     *
     * <p>A context is built from the controller's {@link Call} so it carries the same call id the controller
     * tracks, which lets the {@link Calls2CallStateMachine} resolve the very context this registry adopted
     * when the controller later requests a transition by id. The primary slot is filled first; a second
     * concurrent call fills the secondary (dual) slot. As it allocates a context this registry also wires
     * the context's {@linkplain Calls2CallContext#onScheduleConnectedLonelyTimer(java.util.function.Consumer)
     * connected-lonely-timer} seams onto the {@link PerCallTimerScheduler}, because the engine arms the
     * connected-lonely timer from the state-transition guard rather than from the controller, so the seam
     * must be in place before the context's first transition.
     *
     * @param manager the call manager that holds the at-most-two call contexts
     * @param timers  the per-call timer scheduler the connected-lonely seams are wired onto
     */
    private record ManagerContextRegistry(Calls2CallManager manager, PerCallTimerScheduler timers)
            implements Calls2CallContextRegistry {
        /**
         * Canonicalizes the registry over its manager and timer scheduler.
         *
         * @throws NullPointerException if {@code manager} or {@code timers} is {@code null}
         */
        private ManagerContextRegistry {
            Objects.requireNonNull(manager, "manager cannot be null");
            Objects.requireNonNull(timers, "timers cannot be null");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote This implementation builds a {@link Calls2CallContext} pinned to {@code call}'s call id,
         * wires its connected-lonely-timer scheduling and cancelling seams onto the
         * {@link PerCallTimerScheduler}, adopts it into the {@link Calls2CallManager}'s primary slot, or
         * the secondary slot when a primary call is already live, so the dual-call ceiling is the manager's
         * authoritative backstop, and returns the adopted context so the controller can hand it to the
         * outbound call-log sink at teardown. The seams are wired before the context is adopted because the
         * state guard ({@code change_call_state_no_event}, fn10920) fires the schedule seam the moment the
         * call enters {@link Calls2CallState#CONNECTED_LONELY}, which can be the context's very first
         * transition.
         */
        @Override
        public Calls2CallContext allocate(Call call, Calls2CallState initialState) {
            Objects.requireNonNull(call, "call cannot be null");
            Objects.requireNonNull(initialState, "initialState cannot be null");
            var direction = call.isOutgoing()
                    ? Calls2CallContext.Calls2CallDirection.OUTGOING
                    : Calls2CallContext.Calls2CallDirection.INCOMING;
            var role = manager.hasPrimary()
                    ? Calls2CallContext.Calls2CallRole.SECONDARY
                    : Calls2CallContext.Calls2CallRole.PRIMARY;
            var context = new Calls2CallContext(call.callId(), role, direction, call.peer(), call.creator(),
                    call.creator(), call.chatJid(), call.isGroup(), call.isVideo());
            context.lock().lock();
            try {
                context.state(initialState);
            } finally {
                context.lock().unlock();
            }
            context.onScheduleConnectedLonelyTimer(timers::scheduleConnectedLonely);
            context.onCancelConnectedLonelyTimer(() -> timers.cancel(call.callId(), Calls2CallTimerKind.CONNECTED_LONELY));
            if (role == Calls2CallContext.Calls2CallRole.SECONDARY) {
                manager.startDualCall(context);
            } else {
                manager.startCall(context);
            }
            return context;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void release(String callId) {
            Objects.requireNonNull(callId, "callId cannot be null");
            manager.endCall(callId);
        }
    }

    /**
     * Owns one {@link Calls2CallTimers} driver per call, arms or cancels its timers by kind, and runs each
     * timer's recovered callback body when it fires.
     *
     * <p>Each call gets its own virtual-thread timer driver, created lazily on the first arm and stopped
     * when the call's timers are cancelled wholesale on teardown. A {@link Calls2CallTimerKind} maps
     * one-to-one onto a {@link Calls2CallTimers.Timer}. The lifecycle-relevant callbacks of SPEC section 4.5
     * are bound here, where the call manager, the event sink, and the lifecycle controller are all reachable:
     * the {@link Calls2CallTimerKind#PERIODIC} watchdog re-arms itself every second and sweeps for the
     * deadlines the host path tracks, the {@link Calls2CallTimerKind#CALLER_LONELY} timeout ends an
     * unanswered outbound call, and the {@link Calls2CallTimerKind#CONNECTED_LONELY} timeout walks its
     * interval sequence and ends a connected call that never gained a peer. The remaining kinds are armed
     * with their recovered period but carry no teardown body here, because their callbacks belong to the
     * group-call and in-call-control units that arm them rather than to the lifecycle controller.
     *
     * <p>A fired lonely callback ends the call by calling back into the {@link Calls2LifecycleController},
     * which is {@linkplain #bindController(Calls2LifecycleController) bound} after the controller is built;
     * the callback runs on the timer driver thread holding no call lock, so the re-entrant
     * {@link Calls2LifecycleController#endCall(String, CallEndReason)} teardown (which cancels this very
     * driver) neither deadlocks nor self-joins, because
     * {@link Calls2CallTimers#stop()} skips joining the driver thread from within its own callback.
     */
    private static final class PerCallTimerScheduler implements Calls2CallTimerScheduler {
        /**
         * Logs a timeout that arrives before the controller is bound, which should never happen because a
         * timer is armed only from a controller method that runs after {@link #bindController}.
         */
        private static final System.Logger LOGGER = System.getLogger(PerCallTimerScheduler.class.getName());

        /**
         * The empty payload emitted with a timer-driven lifecycle event, since the per-event payload byte
         * layout is not yet recovered (SPEC section 17.1).
         */
        private static final byte[] EMPTY_PAYLOAD = new byte[0];

        /**
         * Holds the live timer driver for each call id, created on first arm and removed on cancel-all.
         */
        private final ConcurrentHashMap<String, Calls2CallTimers> timers = new ConcurrentHashMap<>();

        /**
         * The call manager used to resolve a firing timer's call context for the watchdog sweep.
         */
        private final Calls2CallManager manager;

        /**
         * The event sink a fired lonely or watchdog timeout emits its lifecycle event onto.
         */
        private final Calls2CallEventSink events;

        /**
         * The calls feature gate, read for the AB-prop-configured group-call heartbeat cadence when arming
         * the {@link Calls2CallTimers.Timer#HEARTBEAT} timer.
         */
        private final Calls2FeatureGate featureGate;

        /**
         * The lifecycle controller a fired lonely timeout ends the call through, bound after construction
         * by {@link #bindController(Calls2LifecycleController)}; {@code null} only in the construction window
         * before any timer can be armed.
         */
        private volatile Calls2LifecycleController controller;

        /**
         * Resolves a call's outbound-group-call unit by call id for the watchdog's unanswered-offer sweep,
         * bound after construction by {@link #bindGroupOutboundResolver(Function)}; {@code null} only in the
         * construction window before any watchdog can fire.
         *
         * <p>The watchdog calls this each tick rather than capturing the unit at arm time, so a one-to-one
         * call (no unit) yields an empty result and a group call torn down between ticks is no longer swept.
         */
        private volatile Function<String, Optional<GroupCallOutbound>> groupOutboundResolver;

        /**
         * Constructs a timer scheduler over the call manager, event sink, and feature gate.
         *
         * @param manager     the call manager used to resolve a firing timer's call context
         * @param events      the event sink a fired timeout emits its lifecycle event onto
         * @param featureGate the calls feature gate read for the group-call heartbeat cadence
         * @throws NullPointerException if {@code manager}, {@code events}, or {@code featureGate} is
         *                              {@code null}
         */
        private PerCallTimerScheduler(Calls2CallManager manager, Calls2CallEventSink events,
                                      Calls2FeatureGate featureGate) {
            this.manager = Objects.requireNonNull(manager, "manager cannot be null");
            this.events = Objects.requireNonNull(events, "events cannot be null");
            this.featureGate = Objects.requireNonNull(featureGate, "featureGate cannot be null");
        }

        /**
         * Binds the lifecycle controller a fired lonely timeout ends the call through.
         *
         * <p>The controller takes this scheduler as a constructor argument, so it cannot be passed to the
         * scheduler's constructor; the assembler builds the scheduler, builds the controller, then binds the
         * controller here before returning, so the binding is in place before the controller's first call
         * method can arm any timer.
         *
         * @param controller the lifecycle controller
         * @throws NullPointerException if {@code controller} is {@code null}
         */
        private void bindController(Calls2LifecycleController controller) {
            this.controller = Objects.requireNonNull(controller, "controller cannot be null");
        }

        /**
         * Binds the resolver the watchdog reaches a call's outbound-group-call unit through for its
         * unanswered-offer sweep.
         *
         * <p>The controller is built after this scheduler (it takes the scheduler as a constructor
         * argument), so this resolver cannot be passed to the constructor either; the assembler builds the
         * scheduler, builds the controller, then binds the controller's
         * {@link Calls2LifecycleController#groupOutbound(String) group-outbound resolver} here before
         * returning, the same post-construction wiring step {@link #bindController(Calls2LifecycleController)}
         * uses, so the binding is in place before any watchdog can fire.
         *
         * @param resolver the resolver from a call id to its outbound-group-call unit
         * @throws NullPointerException if {@code resolver} is {@code null}
         */
        private void bindGroupOutboundResolver(Function<String, Optional<GroupCallOutbound>> resolver) {
            this.groupOutboundResolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote This implementation arms the matching {@link Calls2CallTimers.Timer} with the recovered
         * callback body of SPEC section 4.5: {@link Calls2CallTimerKind#PERIODIC} is armed at the
         * thousand-millisecond watchdog cadence with the self-rescheduling
         * {@link #periodicWatchdog(String, Calls2CallTimers)} sweep, {@link Calls2CallTimerKind#CALLER_LONELY}
         * at the recovered short lonely-state timeout with the {@link #callerLonelyTimeout(String)} teardown,
         * and {@link Calls2CallTimerKind#CONNECTED_LONELY} at its direction-picked first interval (see
         * {@link #firstLonelyIntervalMillis(String)}) with the interval-walking
         * {@link #connectedLonelyTimeout(String, Calls2CallTimers, int)} teardown (the controller arms the
         * connected-lonely timer only as a direct request; the engine's usual path is the state-guard seam
         * {@link #scheduleConnectedLonely(Calls2CallContext)}). The remaining kinds are armed with their
         * recovered period and an inert body, because their callbacks are owned by the group-call and
         * in-call-control units that arm them, not by the lifecycle controller.
         */
        @Override
        public void arm(String callId, Calls2CallTimerKind kind) {
            Objects.requireNonNull(callId, "callId cannot be null");
            Objects.requireNonNull(kind, "kind cannot be null");
            var driver = timers.computeIfAbsent(callId, Calls2CallTimers::new);
            switch (kind) {
                case PERIODIC ->
                        driver.arm(Calls2CallTimers.Timer.PERIODIC, Calls2CallTimers.Timer.PERIODIC.defaultPeriod(),
                                () -> periodicWatchdog(callId, driver));
                case CALLER_LONELY ->
                        driver.arm(Calls2CallTimers.Timer.CALLER_LONELY,
                                Calls2CallTimers.Timer.CALLER_LONELY.defaultPeriod(),
                                () -> callerLonelyTimeout(callId));
                case CONNECTED_LONELY -> armConnectedLonely(callId, driver, firstLonelyIntervalMillis(callId));
                case HEARTBEAT -> {
                    var period = heartbeatPeriod();
                    if (!period.isZero() && !period.isNegative()) {
                        driver.arm(Calls2CallTimers.Timer.HEARTBEAT, period, () -> heartbeatTick(callId, driver));
                    }
                }
                default -> armUnownedTimer(driver, kind);
            }
        }

        /**
         * Sends the group-call heartbeat and reschedules itself for the next cadence tick.
         *
         * <p>Mirrors the native {@code heartbeat_callback} (fn10936) re-arm: the lifecycle controller emits
         * one heartbeat (itself a no-op unless the call is an active group call) and the timer re-arms at the
         * {@code heartbeat_interval_s} cadence as long as the call's driver is still running, so the heartbeat
         * stops once the call is torn down.
         *
         * @param callId the identifier of the call being heartbeated
         * @param driver the call's timer driver
         */
        private void heartbeatTick(String callId, Calls2CallTimers driver) {
            var current = controller;
            if (current != null) {
                current.sendHeartbeat(callId);
            }
            var period = heartbeatPeriod();
            if (!period.isZero() && !period.isNegative()) {
                driver.armIfRunning(Calls2CallTimers.Timer.HEARTBEAT, period,
                        () -> heartbeatTick(callId, driver));
            }
        }

        /**
         * Returns the group-call heartbeat cadence read from the feature gate.
         *
         * <p>The {@code heartbeat_interval_s} server setting is read per call, so a configuration change is
         * picked up the next time a call arms its heartbeat.
         *
         * @return the heartbeat period
         */
        private Duration heartbeatPeriod() {
            return Duration.ofSeconds(featureGate.heartbeatIntervalSeconds());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void cancel(String callId, Calls2CallTimerKind kind) {
            Objects.requireNonNull(callId, "callId cannot be null");
            Objects.requireNonNull(kind, "kind cannot be null");
            var driver = timers.get(callId);
            if (driver != null) {
                driver.cancel(toTimer(kind));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void cancelAll(String callId) {
            Objects.requireNonNull(callId, "callId cannot be null");
            var driver = timers.remove(callId);
            if (driver != null) {
                driver.stop();
            }
        }

        /**
         * Schedules the connected-lonely timer for a call entering the connected-lonely state.
         *
         * <p>This is the seam the state-transition guard fires through
         * {@link Calls2CallContext#fireScheduleConnectedLonelyTimer()} the moment a call enters
         * {@link Calls2CallState#CONNECTED_LONELY}; the engine arms the connected-lonely timer from the guard
         * rather than from the controller, so this is the usual entry point for the timer. The first interval
         * is picked by direction from the context's
         * {@linkplain Calls2CallContext#connectedLonelyConfig() connected-lonely configuration} (the short
         * thirty-second interval for the caller, the long two-hundred-seventy-second interval for the
         * callee), and the callback walks the rest of
         * {@link Calls2CallTimers#DEFAULT_CONNECTED_LONELY_INTERVALS_MS} before ending the call.
         *
         * @implNote This implementation reproduces the connected-lonely scheduling of the state guard
         * ({@code change_call_state_no_event}, fn10920, the {@code reschedule_call_timer_entry} at
         * {@code ctx+0x7e}): {@code parse_lonely_state_timeouts} picks the interval by direction and the
         * {@code connected_lonely_state_timer_callback} (fn10939) walks the interval array. The caller takes
         * the short interval (the engine's caller-lonely path) and the callee the long interval; the array
         * walk then steps up to the maximum interval before the lonely-timeout teardown.
         *
         * @param context the call context entering the connected-lonely state
         * @throws NullPointerException if {@code context} is {@code null}
         */
        private void scheduleConnectedLonely(Calls2CallContext context) {
            Objects.requireNonNull(context, "context cannot be null");
            var callId = context.callId();
            var first = context.connectedLonelyConfig().intervalForDirection(context.direction());
            var driver = timers.computeIfAbsent(callId, Calls2CallTimers::new);
            armConnectedLonely(callId, driver, first);
        }

        /**
         * Arms the connected-lonely timer at its first interval with the interval-walking teardown.
         *
         * <p>Shared by the state-guard seam {@link #scheduleConnectedLonely(Calls2CallContext)} and the
         * direct {@link Calls2CallTimerKind#CONNECTED_LONELY} arm: the first interval fires
         * {@link #connectedLonelyTimeout(String, Calls2CallTimers, int)} with the next index, which walks the
         * remaining
         * intervals of {@link Calls2CallTimers#DEFAULT_CONNECTED_LONELY_INTERVALS_MS} and ends the call after
         * the last.
         *
         * @param callId      the identifier of the call whose connected-lonely timer is armed
         * @param driver      the call's timer driver
         * @param firstMillis the first interval, in milliseconds
         */
        private void armConnectedLonely(String callId, Calls2CallTimers driver, long firstMillis) {
            driver.arm(Calls2CallTimers.Timer.CONNECTED_LONELY, Duration.ofMillis(firstMillis),
                    () -> connectedLonelyTimeout(callId, driver, 1));
        }

        /**
         * Returns the first connected-lonely interval for a call, in milliseconds.
         *
         * <p>The first interval is picked by direction from the call context's connected-lonely
         * configuration when the manager still holds the context (the short interval for the caller, the long
         * interval for the callee); when the context is not resolvable it falls back to the first entry of
         * {@link Calls2CallTimers#DEFAULT_CONNECTED_LONELY_INTERVALS_MS}.
         *
         * @param callId the identifier of the call whose first interval is resolved
         * @return the first connected-lonely interval in milliseconds
         */
        private long firstLonelyIntervalMillis(String callId) {
            return manager.getByCallId(callId)
                    .map(context -> context.connectedLonelyConfig().intervalForDirection(context.direction()))
                    .orElse(Calls2CallTimers.DEFAULT_CONNECTED_LONELY_INTERVALS_MS[0]);
        }

        /**
         * Runs the periodic watchdog sweep and reschedules itself for the next second.
         *
         * <p>The watchdog resolves the firing call's context and, for a group call, would terminate any peer
         * whose offer has gone unanswered past the {@link Calls2CallTimers#UNANSWERED_GROUP_OFFER_TIMEOUT}
         * cutoff; it then re-arms itself for the next second, matching the engine's self-rescheduling
         * watchdog. The two one-to-one call-setup deadlines the native watchdog also enforces are enforced on
         * the host path elsewhere: the offer-ack deadline by the synchronous offer-ack wait in
         * {@link Calls2LifecycleController#startCall(com.github.auties00.cobalt.model.jid.Jid,
         * com.github.auties00.cobalt.model.jid.Jid, java.util.List, boolean, Calls2MediaStreams)}, and the
         * no-answer deadline by the {@link Calls2CallTimerKind#CALLER_LONELY} timer, so the watchdog adds no
         * further one-to-one teardown here.
         *
         * @implNote This implementation reproduces the rescheduling spine of the
         * {@code periodic_call_timer_callback} (fn10932, the {@code ctx+0x188} entry that re-arms every
         * thousand milliseconds) and its per-peer unanswered-group-offer sweep
         * ({@code call_lifecycle.cc:8227}, the {@code elapsed > 44999} -> mark and terminate that peer
         * batch). The sweep is delegated to the call's {@link GroupCallOutbound} unit, resolved through the
         * controller's {@linkplain #bindGroupOutboundResolver(Function) bound resolver}: the unit reads each
         * peer's offer-send timestamp off the call's
         * {@link com.github.auties00.cobalt.calls2.core.participant.CallMembership} and terminates a peer
         * whose offer has gone unanswered past {@link Calls2CallTimers#UNANSWERED_GROUP_OFFER_TIMEOUT}. A
         * one-to-one call resolves to no unit and is swept of nothing. The unanswered-mute-response and
         * {@code check_received_offer_after_peeking} sweeps belong to the in-call-control and offer-peek
         * units. A call the manager no longer holds is not rescheduled, so the watchdog stops once the call
         * is torn down.
         *
         * @param callId the identifier of the call whose watchdog fired
         * @param driver the call's timer driver the watchdog re-arms itself on
         */
        private void periodicWatchdog(String callId, Calls2CallTimers driver) {
            var context = manager.getByCallId(callId).orElse(null);
            if (context == null) {
                return;
            }
            var resolver = groupOutboundResolver;
            if (resolver != null) {
                resolver.apply(callId).ifPresent(GroupCallOutbound::sweepUnansweredOffers);
            }
            driver.armIfRunning(Calls2CallTimers.Timer.PERIODIC, Calls2CallTimers.Timer.PERIODIC.defaultPeriod(),
                    () -> periodicWatchdog(callId, driver));
        }

        /**
         * Ends an unanswered outbound one-to-one call when the caller-lonely timeout fires.
         *
         * <p>The caller-lonely timer is armed when an outbound call starts ringing; its expiry means the
         * callee never answered within the lonely-state window, so the call ends with
         * {@link CallEndReason#TIMEOUT} and the {@link CallEventType#LONELY_STATE_TIMEOUT} event.
         *
         * @implNote This implementation reproduces the {@code caller_lonely_state_timer_callback} (fn10934)
         * teardown, the engine's "lonely state timeout, ends call". The public projection of the engine's
         * lonely-timeout result is {@link CallEndReason#TIMEOUT} (the call rang until the timeout elapsed).
         *
         * @param callId the identifier of the call whose caller-lonely timer fired
         */
        private void callerLonelyTimeout(String callId) {
            failCall(callId, CallEndReason.TIMEOUT, CallEventType.LONELY_STATE_TIMEOUT);
        }

        /**
         * Walks the connected-lonely interval sequence, ending the call after the final interval.
         *
         * <p>The connected-lonely timer walks {@link Calls2CallTimers#DEFAULT_CONNECTED_LONELY_INTERVALS_MS}:
         * on each non-final interval it re-arms at the next interval so a connected-but-alone call survives a
         * transient peer absence, and on the final interval it ends the call with {@link CallEndReason#TIMEOUT}
         * and the {@link CallEventType#LONELY_STATE_TIMEOUT} event. The {@code nextIndex} is the index of the
         * interval to arm next, the host analogue of the engine's current-interval index.
         *
         * @implNote This implementation reproduces the {@code connected_lonely_state_timer_callback} (fn10939)
         * interval walk (the {@code connected_lonely_state_timer_intervals_ms} array at {@code ctx+0x22f40}
         * with the current index at {@code ctx+0x2a8}): each non-final expiry reschedules at the next
         * interval, and the final expiry sets the engine's lonely-timeout log state ({@code ctx+0x47c=0x1f})
         * and ends the call. The log-state field has no host-side counterpart; the teardown carries the
         * lonely-timeout cause through the {@link CallEventType#LONELY_STATE_TIMEOUT} event and the
         * {@link CallEndReason#TIMEOUT} reason instead.
         *
         * @param callId    the identifier of the call whose connected-lonely timer fired
         * @param driver    the call's timer driver the timeout re-arms itself on
         * @param nextIndex the index of the next interval to arm, or one past the last interval to end the
         *                  call
         */
        private void connectedLonelyTimeout(String callId, Calls2CallTimers driver, int nextIndex) {
            var intervals = Calls2CallTimers.DEFAULT_CONNECTED_LONELY_INTERVALS_MS;
            if (nextIndex >= intervals.length) {
                failCall(callId, CallEndReason.TIMEOUT, CallEventType.LONELY_STATE_TIMEOUT);
                return;
            }
            driver.armIfRunning(Calls2CallTimers.Timer.CONNECTED_LONELY, Duration.ofMillis(intervals[nextIndex]),
                    () -> connectedLonelyTimeout(callId, driver, nextIndex + 1));
        }

        /**
         * Emits a timer-driven lifecycle event and ends the call through the lifecycle controller.
         *
         * <p>Fires the cause event (the lonely-timeout event) onto the shared event sink, then ends the call
         * through {@link Calls2LifecycleController#endCall(String, CallEndReason)}, which sends the terminate,
         * cancels every per-call timer, closes the media plane, drives the state to
         * {@link Calls2CallState#NONE}, and emits the ending events. The callback runs on the timer driver
         * thread holding no call lock, so the re-entrant teardown (which cancels this very driver) is safe.
         *
         * @param callId      the identifier of the call to end
         * @param reason      the end reason published on the call view and carried on the terminate
         * @param causeEvent  the lifecycle event for the cause of the teardown
         */
        private void failCall(String callId, CallEndReason reason, CallEventType causeEvent) {
            var current = controller;
            if (current == null) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Timer fired for call {0} before the lifecycle controller was bound", callId);
                return;
            }
            events.emit(causeEvent, EMPTY_PAYLOAD);
            current.endCall(callId, reason);
        }

        /**
         * Arms a timer whose callback body is owned by another engine unit at its recovered period.
         *
         * <p>The heartbeat, lobby, ohai, key-rotation, reaction-clear, video-upgrade, end-to-end-restore,
         * and app-data-stream-test timers are armed by the group-call and in-call-control units that own
         * their callbacks; this scheduler only holds the driver and the lifecycle-relevant bodies, so it
         * arms these with their recovered period (or the watchdog cadence for a configured-per-call timer
         * whose negotiated delay is not threaded here yet) and an inert body the owning unit will replace
         * when it arms the timer with its own callback.
         *
         * @param driver the call's timer driver
         * @param kind   the timer kind whose callback is owned by another unit
         */
        private void armUnownedTimer(Calls2CallTimers driver, Calls2CallTimerKind kind) {
            // The outbound-group-call unit (GroupCallOutbound, Piece 9) owns the per-peer unanswered-offer
            // sweep and the <group_update> fanout, both driven from the controller and the PERIODIC
            // watchdog, not from this seam. HEARTBEAT now has its own self-rescheduling case above that
            // emits through the lifecycle controller at the heartbeat_interval_s cadence. The
            // UPDATE_ENCRYPTION_KEY timer reaches this seam with an inert body on purpose: the native
            // update_encryption_key_timer_callback (fn10940) re-sends the EXISTING call key (call+0x61ed0,
            // not a freshly minted key, so no SFrame key-id bump) to the connected participants whose
            // per-participant key-sent flag is still clear, and the native arms that timer from
            // call_membership.cc on a membership change with a short debounce. Cobalt performs the same
            // membership-driven rekey event-driven and immediately in
            // Calls2LifecycleController.handleGroupUpdate (fanOutGroupRekey over the reconciled
            // <group_update> roster diff), so the rekey outcome matches without replicating the debounce
            // timer; the inert body here is therefore intentional, not a gap.
            // The remaining kinds (LOBBY, OHAI, REACTION_CLEAR, VIDEO_UPGRADE, E2EE_RESTORE,
            // APP_DATA_STREAM_TEST) are reached only if a future caller drives one through this seam.
            var timer = toTimer(kind);
            var period = timer.defaultPeriod().isZero()
                    ? Calls2CallTimers.Timer.PERIODIC.defaultPeriod()
                    : timer.defaultPeriod();
            driver.arm(timer, period, () -> {
            });
        }

        /**
         * Maps a controller timer kind onto its timer-unit member.
         *
         * @param kind the controller-facing timer kind
         * @return the matching {@link Calls2CallTimers.Timer}
         */
        private static Calls2CallTimers.Timer toTimer(Calls2CallTimerKind kind) {
            return switch (kind) {
                case PERIODIC -> Calls2CallTimers.Timer.PERIODIC;
                case CALLER_LONELY -> Calls2CallTimers.Timer.CALLER_LONELY;
                case OHAI -> Calls2CallTimers.Timer.OHAI;
                case HEARTBEAT -> Calls2CallTimers.Timer.HEARTBEAT;
                case LOBBY -> Calls2CallTimers.Timer.LOBBY;
                case CONNECTED_LONELY -> Calls2CallTimers.Timer.CONNECTED_LONELY;
                case UPDATE_ENCRYPTION_KEY -> Calls2CallTimers.Timer.UPDATE_ENCRYPTION_KEY;
                case REACTION_CLEAR -> Calls2CallTimers.Timer.REACTION_CLEAR;
                case VIDEO_UPGRADE -> Calls2CallTimers.Timer.VIDEO_UPGRADE;
                case E2EE_RESTORE -> Calls2CallTimers.Timer.E2EE_RESTORE;
                case APP_DATA_STREAM_TEST -> Calls2CallTimers.Timer.APP_DATA_STREAM_TEST;
            };
        }
    }

    /**
     * Refreshes the call-info snapshot for a lifecycle event from the resolved context's running state.
     */
    private record InfoManagerUpdater(Calls2CallManager manager, Calls2CallInfoManager infoManager)
            implements Calls2CallInfoUpdater {
        /**
         * Canonicalizes the updater over its manager and info manager.
         *
         * @throws NullPointerException if {@code manager} or {@code infoManager} is {@code null}
         */
        private InfoManagerUpdater {
            Objects.requireNonNull(manager, "manager cannot be null");
            Objects.requireNonNull(infoManager, "infoManager cannot be null");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote This implementation reads the resolved context's accumulated active and lonely
         * durations and current state and result under the context lock, then refreshes the single
         * {@link Calls2CallInfoManager} snapshot; a call the manager does not hold is a no-op. The setup
         * duration is reported as zero until the connected-state timestamp accounting is threaded through,
         * and an as-yet-unresolved result defaults to the in-progress
         * {@link Calls2CallResult#CALL_OFFER_ACK_NOT_RECEIVED}.
         */
        @Override
        public void updateForEvent(String callId, CallEventType eventType) {
            Objects.requireNonNull(callId, "callId cannot be null");
            Objects.requireNonNull(eventType, "eventType cannot be null");
            var context = manager.getByCallId(callId).orElse(null);
            if (context == null) {
                return;
            }
            context.lock().lock();
            try {
                var active = Duration.ofMillis(context.activeDurationMillis());
                var lonely = Duration.ofMillis(context.lonelyDurationMillis());
                var result = context.result().orElse(Calls2CallResult.CALL_OFFER_ACK_NOT_RECEIVED);
                infoManager.updateForEvent(eventType, context.state(), result, active, lonely,
                        Duration.ZERO, null);
            } finally {
                context.lock().unlock();
            }
        }
    }

    /**
     * Logs each gated lifecycle event id the controller emits.
     *
     * <p>The typed host-facing listener events the application observes are fanned out by the call service
     * and the in-call control units through {@link LiveCallEventBus}; this opaque sink is the diagnostic tap
     * for the lifecycle ids the controller raises directly, whose typed payload layout is not yet recovered.
     */
    private static final class LoggingEventSink implements Calls2CallEventSink {
        /**
         * Logs the gated lifecycle event ids.
         */
        private static final System.Logger LOGGER = System.getLogger(LoggingEventSink.class.getName());

        /**
         * {@inheritDoc}
         *
         * @implNote This implementation logs the event id at trace level and drops the opaque payload; the
         * application-facing typed events fire from the call service and control units through the event
         * bus, so this sink surfaces nothing to listeners on its own.
         */
        @Override
        public void emit(CallEventType eventType, byte[] payload) {
            Objects.requireNonNull(eventType, "eventType cannot be null");
            LOGGER.log(System.Logger.Level.TRACE, "calls2 lifecycle event {0}", eventType);
        }
    }

    /**
     * The host-level {@code call_sendto} datagram seam, backed by an unconnected {@link DatagramChannel}.
     *
     * <p>This satisfies the engine's {@code call_sendto} host downcall through
     * {@link LiveVoipHostApi.MediaDatagramSink}: it sends each datagram on its own unconnected channel to the
     * destination the engine supplies. The pure-Java web transport's media plane does not exercise this
     * downcall: the per-call {@link LiveMediaSession} owns the host socket the ICE connectivity checks and
     * DTLS records leave through, and media rides as SCTP DATA over the DTLS-wrapped data channel rather than
     * as raw UDP. The channel is opened lazily on the first send and reused for the lifetime of the host.
     */
    private static final class LiveDatagramSink implements LiveVoipHostApi.MediaDatagramSink {
        /**
         * Logs a datagram-send failure on the host fallback path.
         */
        private static final System.Logger LOGGER = System.getLogger(LiveDatagramSink.class.getName());

        /**
         * Holds the lazily-opened unconnected datagram channel, or {@code null} until the first send.
         */
        private final AtomicReference<DatagramChannel> channel = new AtomicReference<>();

        /**
         * {@inheritDoc}
         *
         * @implNote This implementation lazily opens one unconnected {@link DatagramChannel} and sends the
         * payload to the destination, returning the byte count the channel accepted. It is the
         * {@code call_sendto} host downcall; the relay path's per-frame egress runs through the media
         * session's host socket (ICE and DTLS) and carries media as SCTP DATA, so the live web transport
         * does not route through this sink.
         */
        @Override
        public int send(byte[] payload, SocketAddress destination) {
            Objects.requireNonNull(payload, "payload cannot be null");
            Objects.requireNonNull(destination, "destination cannot be null");
            var open = channel.get();
            if (open == null) {
                try {
                    open = DatagramChannel.open();
                } catch (IOException exception) {
                    LOGGER.log(System.Logger.Level.DEBUG, "calls2 host datagram channel open failed", exception);
                    return 0;
                }
                if (!channel.compareAndSet(null, open)) {
                    closeQuietly(open);
                    open = channel.get();
                }
            }
            try {
                return open.send(ByteBuffer.wrap(payload), destination);
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.DEBUG, "calls2 host datagram send failed", exception);
                return 0;
            }
        }

        /**
         * Closes a datagram channel, swallowing any close error.
         *
         * <p>Used to discard a channel that lost the open race so two callers never leak a socket.
         *
         * @param toClose the channel to close
         */
        private static void closeQuietly(DatagramChannel toClose) {
            try {
                toClose.close();
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.DEBUG, "calls2 host datagram channel close failed", exception);
            }
        }
    }
}
