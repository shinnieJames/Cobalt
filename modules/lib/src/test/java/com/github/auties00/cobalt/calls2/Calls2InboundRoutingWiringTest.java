package com.github.auties00.cobalt.calls2;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.calls2.core.CallEventType;
import com.github.auties00.cobalt.calls2.core.Calls2CallContextRegistry;
import com.github.auties00.cobalt.calls2.core.Calls2CallEventSink;
import com.github.auties00.cobalt.calls2.core.Calls2CallInfoUpdater;
import com.github.auties00.cobalt.calls2.core.Calls2CallState;
import com.github.auties00.cobalt.calls2.core.Calls2CallStateTransition;
import com.github.auties00.cobalt.calls2.core.Calls2CallTimerKind;
import com.github.auties00.cobalt.calls2.core.Calls2CallTimerScheduler;
import com.github.auties00.cobalt.calls2.core.Calls2LifecycleController;
import com.github.auties00.cobalt.calls2.core.Calls2MediaPlane;
import com.github.auties00.cobalt.calls2.crypto.CallKeyExchange;
import com.github.auties00.cobalt.calls2.crypto.CallRekeyEnvelope;
import com.github.auties00.cobalt.calls2.platform.VoipHostApi;
import com.github.auties00.cobalt.calls2.signaling.CallKeyDistribution;
import com.github.auties00.cobalt.calls2.signaling.CallMessage;
import com.github.auties00.cobalt.calls2.signaling.CallMessageBuffer;
import com.github.auties00.cobalt.calls2.signaling.CallSignalingRouter;
import com.github.auties00.cobalt.calls2.signaling.Calls2CallReceiver;
import com.github.auties00.cobalt.calls2.signaling.Calls2TerminateReceiver;
import com.github.auties00.cobalt.calls2.signaling.OfferStanza;
import com.github.auties00.cobalt.calls2.signaling.TerminateStanza;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;
import com.github.auties00.cobalt.client.linked.TestWhatsAppClient;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial P8 wiring oracle for the inbound {@code <call>}/{@code <terminate>} routing seam.
 *
 * <p>This suite builds the two calls2 receivers exactly as {@code LiveNodeStreamService} wires them (an
 * {@link Calls2CallReceiver} under the {@code "call"} tag and a {@link Calls2TerminateReceiver} under the
 * {@code "terminate"} tag), feeds representative inbound stanzas, and asserts the inbound action reaches
 * the calls2 sink, the {@link LiveCalls2Service}, and (when an engine is wired) the
 * {@link Calls2LifecycleController}. The point is that the inbound routing reaches the calls2 engine
 * end to end through the calls2 receivers alone.
 *
 * <p>The harness uses a {@link TestWhatsAppClient} over a real temporary store (so receipts emitted via
 * {@code sendNodeWithNoResponse} surface through {@code onNodeSent}) and a real {@link Calls2LifecycleController}
 * assembled from minimal interface fakes, since the production controller's nine collaborators are all
 * interfaces and a real one would otherwise drag in transport and native units. The lifecycle reach is
 * observed through a recording {@link Calls2CallEventSink}; the state-transition fake returns the prior
 * state so the controller's guarded transition actually fires its event.
 */
@DisplayName("calls2 P8 inbound routing wiring")
class Calls2InboundRoutingWiringTest {
    // The PN+LID pair the call corpus pairs with the primary session; reused so a parsed call-creator
    // LID is genuinely LID-addressed and clears the router's LID-only gate.
    private static final Jid SELF_PN = Jid.of("19153544650@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("39110693621863@lid");
    private static final Jid PEER_DEVICE_LID = Jid.of("55555555", JidServer.lid(), 7, 0);
    private static final String CALL_ID = "CAFEBABECAFEBABECAFEBABECAFEBABE";

    private static Stanza inboundOffer(String callId, Jid from, Jid callCreatorDevice, boolean withMedia) {
        var offer = new StanzaBuilder()
                .description("offer")
                .attribute("call-id", callId)
                .attribute("call-creator", callCreatorDevice);
        if (withMedia) {
            offer.content(new StanzaBuilder().description("media").attribute("type", "audio").build());
        }
        return new StanzaBuilder()
                .description("call")
                .attribute("from", from)
                .attribute("id", "stanza-" + callId)
                .attribute("sender_lid", callCreatorDevice)
                .content(offer.build())
                .build();
    }

    private static Stanza inboundEnvelope(Stanza payload, Jid from, Jid senderLid) {
        return new StanzaBuilder()
                .description("call")
                .attribute("from", from)
                .attribute("id", "stanza-x")
                .attribute("sender_lid", senderLid)
                .content(payload)
                .build();
    }

    @Nested
    @DisplayName("Calls2CallReceiver (the \"call\" tag handler)")
    class CallReceiver {
        @Test
        @DisplayName("a fresh inbound offer is acked and replayed to the calls2 sink, never handed to a legacy receiver")
        void freshOfferRoutesToCalls2() throws IOException {
            var sentNodes = new ConcurrentLinkedQueue<Stanza>();
            var client = clientRecording(sentNodes);
            var forwarded = new ConcurrentLinkedQueue<CallMessage>();
            var receiver = new Calls2CallReceiver(client, new AckSender(client),
                    new CallSignalingRouter(), new CallMessageBuffer(),
                    callId -> false, (message, from) -> forwarded.add(message));

            receiver.handle(inboundOffer(CALL_ID, PEER_DEVICE_LID, PEER_DEVICE_LID, true));

            // callExists==false: the offer BUFFERs (calls2 disposition), but the offer is the call-creating
            // signal, so the receiver drains the call's buffer at once and replays the decoded offer to the
            // calls2 sink (the lifecycle controller forwarder), which rings the call. The legacy
            // CallService/CallReceiver is not on this path at all.
            assertEquals(1, forwarded.size(),
                    "a fresh offer must be replayed to the calls2 sink so the call rings; got " + forwarded);
            assertTrue(forwarded.peek() instanceof OfferStanza, "the replayed message is the decoded offer");
            var receipt = outgoing(sentNodes, "receipt");
            assertTrue(receipt.isPresent(), "offer must be acked with a <receipt>; got " + sentNodes);
            assertTrue(receipt.get().getChild("offer").isPresent(), "receipt child mirrors the offer tag");
        }

        @Test
        @DisplayName("a routable signal for an existing call is decoded and forwarded to the calls2 sink")
        void processForwardsToCalls2Sink() throws IOException {
            var sentNodes = new ConcurrentLinkedQueue<Stanza>();
            var client = clientRecording(sentNodes);
            var forwarded = new ConcurrentLinkedQueue<CallMessage>();
            var receiver = new Calls2CallReceiver(client, new AckSender(client),
                    new CallSignalingRouter(), new CallMessageBuffer(),
                    callId -> CALL_ID.equals(callId), (message, from) -> forwarded.add(message));

            var terminate = TerminateStanza.of(CALL_ID, PEER_DEVICE_LID, CallEndReason.HANGUP, List.of());
            receiver.handle(inboundEnvelope(terminate.toStanza(), PEER_DEVICE_LID, PEER_DEVICE_LID));

            assertEquals(1, forwarded.size(), "a PROCESS verdict must forward exactly one decoded message");
            assertTrue(forwarded.peek() instanceof TerminateStanza, "the forwarded message is the decoded terminate");
        }

        @Test
        @DisplayName("a stanza with no payload is dropped without an ack")
        void malformedStanzaDropped() throws IOException {
            var sentNodes = new ConcurrentLinkedQueue<Stanza>();
            var client = clientRecording(sentNodes);
            var forwarded = new ConcurrentLinkedQueue<CallMessage>();
            var receiver = new Calls2CallReceiver(client, new AckSender(client),
                    new CallSignalingRouter(), new CallMessageBuffer(),
                    callId -> true, (message, from) -> forwarded.add(message));

            var bare = new StanzaBuilder().description("call").attribute("from", PEER_DEVICE_LID).build();
            receiver.handle(bare);

            assertTrue(forwarded.isEmpty(), "no payload means nothing to forward");
            assertTrue(sentNodes.isEmpty(), "no payload means nothing to ack");
        }
    }

    @Nested
    @DisplayName("Calls2TerminateReceiver (the \"terminate\" tag handler)")
    class TerminateReceiver {
        @Test
        @DisplayName("a bare top-level terminate is decoded and forwarded to the calls2 sink")
        void bareTerminateRoutesToCalls2() throws IOException {
            var forwarded = new ConcurrentLinkedQueue<TerminateStanza>();
            var receiver = new Calls2TerminateReceiver((terminate, from) -> forwarded.add(terminate));

            var bare = TerminateStanza.of(CALL_ID, PEER_DEVICE_LID, CallEndReason.HANGUP, List.of()).toStanza();
            receiver.handle(bare);

            assertEquals(1, forwarded.size(), "a well-formed bare terminate must forward once");
            assertEquals(CALL_ID, forwarded.peek().callId());
        }

        @Test
        @DisplayName("a bare terminate with no call-id is dropped")
        void terminateWithoutCallIdDropped() throws IOException {
            var forwarded = new ConcurrentLinkedQueue<TerminateStanza>();
            var receiver = new Calls2TerminateReceiver((terminate, from) -> forwarded.add(terminate));

            receiver.handle(new StanzaBuilder().description("terminate").attribute("reason", "hangup").build());

            assertTrue(forwarded.isEmpty(), "a terminate with no call-id cannot be associated with a call");
        }
    }

    @Nested
    @DisplayName("inbound reaches LiveCalls2Service and, when wired, the lifecycle controller")
    class LifecycleReach {
        @Test
        @DisplayName("an offer fed to a service backed by a wired controller fires CALL_OFFER_RECEIVED on the engine event sink")
        void offerReachesLifecycleEventSink() {
            var events = new RecordingEventSink();
            var service = serviceWithLifecycle(events);

            var offer = SignalingFixturesBridge.minimalOffer(CALL_ID, PEER_DEVICE_LID);
            service.handleInbound(offer, PEER_DEVICE_LID);

            assertTrue(events.emitted(CallEventType.CALL_OFFER_RECEIVED),
                    "an inbound offer must reach the lifecycle controller and fire CALL_OFFER_RECEIVED; got "
                            + events.eventTypes());
        }

        @Test
        @DisplayName("a terminate forwarded to the live service ends the tracked call through the lifecycle")
        void terminateReachesLifecycle() {
            var events = new RecordingEventSink();
            var service = serviceWithLifecycle(events);
            // Seed a live call (offer -> ring) so the controller tracks it, then end it with a terminate.
            service.handleInbound(SignalingFixturesBridge.minimalOffer(CALL_ID, PEER_DEVICE_LID), PEER_DEVICE_LID);
            events.clear();

            var terminate = TerminateStanza.of(CALL_ID, PEER_DEVICE_LID, CallEndReason.HANGUP, List.of());
            service.handleInbound(terminate, PEER_DEVICE_LID);

            assertTrue(events.emitted(CallEventType.CALL_TERMINATE_RECEIVED),
                    "a terminate for a tracked call must reach the lifecycle; got " + events.eventTypes());
        }
    }

    private static TestWhatsAppClient clientRecording(ConcurrentLinkedQueue<Stanza> sentStanzas) {
        var store = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.addListener(new LinkedWhatsAppClientListener() {
            @Override
            public void onNodeSent(LinkedWhatsAppClient whatsapp, Stanza stanza) {
                sentStanzas.add(stanza);
            }
        });
        return TestWhatsAppClient.create().withStore(store);
    }

    private static Optional<Stanza> outgoing(ConcurrentLinkedQueue<Stanza> sentStanzas, String tag) {
        return sentStanzas.stream().filter(n -> tag.equals(n.description())).findFirst();
    }

    private LiveCalls2Service serviceWithLifecycle(RecordingEventSink events) {
        var client = clientRecording(new ConcurrentLinkedQueue<>());
        var controller = new Calls2LifecycleController(
                offerEnvelope -> new StanzaBuilder().description("ack").build(),
                new NoopCallKeyExchange(),
                new NoopVoipHostApi(),
                new NoopRegistry(),
                new PriorStateTransition(),
                new NoopTimers(),
                new NoopInfoUpdater(),
                events,
                new NoopMediaPlane());
        return new LiveCalls2Service(client, null, new StubMessageService(client), controller);
    }

    // Builds the minimal <call><offer> via Calls2CallStanza and routes it through client.sendNode so
    // the harness sees the outbound call; skips the real Signal encryption and device-list sync the
    // routing assertions do not exercise. Every other MessageService method is unreachable on this path.
    private static final class StubMessageService implements MessageService {
        private final LinkedWhatsAppClient client;

        StubMessageService(LinkedWhatsAppClient client) {
            this.client = client;
        }

        @Override
        public AckResult send(Jid chatJid, MessageContainer container) {
            throw new UnsupportedOperationException("StubMessageService.send not stubbed");
        }

        @Override
        public AckResult send(MessageInfo messageInfo) {
            throw new UnsupportedOperationException("StubMessageService.send not stubbed");
        }

        @Override
        public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
            throw new UnsupportedOperationException("StubMessageService.sendPeer not stubbed");
        }

        @Override
        public MessageInfo process(Stanza stanza) {
            throw new UnsupportedOperationException("StubMessageService.process not stubbed");
        }

        @Override
        public MessageService.CallPeerAddressing resolveCallPeerAddressing(Jid peer) {
            throw new UnsupportedOperationException("StubMessageService.resolveCallPeerAddressing not stubbed");
        }

        @Override
        public byte[] processCall(Jid senderJid, MessageEncryptionType encType, byte[] ciphertext) {
            return ciphertext;
        }

        @Override
        public void clearPendingMessages() {
        }
    }

    /**
     * Records every {@link CallEventType} emitted so the test can assert the lifecycle was reached.
     */
    private static final class RecordingEventSink implements Calls2CallEventSink {
        private final List<CallEventType> events = new CopyOnWriteArrayList<>();

        @Override
        public void emit(CallEventType eventType, byte[] payload) {
            events.add(eventType);
        }

        boolean emitted(CallEventType type) {
            return events.contains(type);
        }

        List<CallEventType> eventTypes() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }

    /**
     * Returns the prior state on every transition so the controller's guarded transition fires its event
     * (the guard suppresses the event only when the prior state is absent or equals the new state).
     */
    private static final class PriorStateTransition implements Calls2CallStateTransition {
        private final java.util.concurrent.ConcurrentHashMap<String, Calls2CallState> states =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Optional<Calls2CallState> transition(String callId, Calls2CallState newState) {
            var prior = states.put(callId, newState);
            return Optional.of(prior == null ? Calls2CallState.NONE : prior);
        }
    }

    private static final class NoopRegistry implements Calls2CallContextRegistry {
        @Override
        public com.github.auties00.cobalt.calls2.core.Calls2CallContext allocate(Call call,
                                                                                 Calls2CallState initialState) {
            return null;
        }

        @Override
        public void release(String callId) {
        }
    }

    private static final class NoopTimers implements Calls2CallTimerScheduler {
        @Override
        public void arm(String callId, Calls2CallTimerKind kind) {
        }

        @Override
        public void cancel(String callId, Calls2CallTimerKind kind) {
        }

        @Override
        public void cancelAll(String callId) {
        }
    }

    private static final class NoopInfoUpdater implements Calls2CallInfoUpdater {
        @Override
        public void updateForEvent(String callId, CallEventType eventType) {
        }
    }

    private static final class NoopMediaPlane implements Calls2MediaPlane {
        @Override
        public Session bringUp(String callId, Stanza relay, java.util.List<Stanza> voipSettings, byte[] callKey,
                               boolean isCaller, boolean video, int participantCount,
                               com.github.auties00.cobalt.calls2.core.participant.CallMembership membership,
                               com.github.auties00.cobalt.calls2.core.Calls2MediaStreams streams,
                               com.github.auties00.cobalt.model.jid.Jid peerDeviceJid,
                               Optional<String> electedRelayName) {
            return () -> {
            };
        }
    }

    private static final class NoopCallKeyExchange implements CallKeyExchange {
        @Override
        public byte[] mintCallKey() {
            return new byte[32];
        }

        @Override
        public byte[] wrapCallKey(byte[] callKey) {
            return callKey;
        }

        @Override
        public List<CallKeyDistribution> encryptOfferFanout(Collection<Jid> deviceJids, byte[] plaintext) {
            return List.of();
        }

        @Override
        public List<CallRekeyEnvelope> encryptRekeyFanout(Collection<Jid> recipientDevices, byte[] plaintext) {
            return List.of();
        }

        @Override
        public Optional<byte[]> decryptCallKey(CallKeyDistribution slot, Jid senderJid) {
            return Optional.empty();
        }

        @Override
        public byte[] signedDeviceIdentity() {
            return new byte[0];
        }
    }

    private static final class NoopVoipHostApi implements VoipHostApi {
        @Override
        public void sendSignaling(Stanza stanza) {
        }

        @Override
        public int sendDatagram(byte[] payload, SocketAddress destination) {
            return 0;
        }

        @Override
        public List<InetAddress> resolveHost(String hostName) {
            return List.of();
        }

        @Override
        public byte[] randomBytes(int length) {
            return new byte[length];
        }

        @Override
        public Path persistentDirectory() {
            return Path.of(".");
        }

        @Override
        public Optional<Path> bweMlModelPath(int modelType) {
            return Optional.empty();
        }

        @Override
        public boolean isKnownContact(Jid participant) {
            return false;
        }

        @Override
        public void renderVideoFrame(RenderedVideoFrame frame) {
        }

        @Override
        public int browserAudioProcessingStatus() {
            return 0;
        }

        @Override
        public void log(System.Logger.Level level, String message) {
        }

        @Override
        public void onCallEvent(CallEventType eventType, byte[] payload) {
        }
    }

    /**
     * Bridges to the package-private {@code SignalingFixtures.minimalOffer} so this test in package
     * {@code calls2} can build a representative {@link OfferStanza} without re-declaring its long
     * constructor. Lives here rather than in {@code calls2.signaling} so the routing assertions stay in
     * one file; it builds the same minimal offer the signaling tests use.
     */
    private static final class SignalingFixturesBridge {
        private static OfferStanza minimalOffer(String callId, Jid callCreator) {
            return new OfferStanza(callId, callCreator, null, null, null, null, null, null,
                    false, false, null, -1, -1, List.of(), List.of(), List.of(), List.of(), null,
                    null, null, null, null, null, null, List.of(), null);
        }
    }
}
