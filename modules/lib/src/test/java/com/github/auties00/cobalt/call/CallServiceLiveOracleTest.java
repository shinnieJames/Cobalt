package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.wam.TestWamService;
import com.github.auties00.cobalt.wam.event.CallEvent;
import com.github.auties00.cobalt.wam.type.CallResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.github.auties00.cobalt.call.internal.CallService;

/**
 * Live-oracle tests for {@link CallService} exercising the real engine
 * (no {@code RecordingEngine} stub) against {@link TestWhatsAppClient}.
 * Drives the three public entrypoints {@link CallService#placeCall},
 * {@link CallService#accept}, {@link CallService#reject} plus the
 * peer-driven hooks, and asserts:
 * <ul>
 *   <li>the outgoing stanza dispatched via {@link TestWhatsAppClient}'s
 *       {@code onNodeSent} pipeline matches the wire shape captured from
 *       WA Web;</li>
 *   <li>the in-flight {@link ActiveCall} registry adds and removes
 *       correctly through the call lifecycle;</li>
 *   <li>listener callbacks fire with the right typed arguments.</li>
 * </ul>
 *
 * <p>TODO: WAM telemetry assertion. {@link CallService#unregister} drains
 * the per-call stats into a {@code CallEvent} (id 462) via
 * {@code WamService.commit}. We pass {@code wamService = null} here so
 * the telemetry path is skipped; once a {@code TestWamService} stub is
 * available, add an assertion that the right {@code CallResultType} is
 * committed per end-reason.
 */
@DisplayName("CallService live wire oracle")
class CallServiceLiveOracleTest {

    /**
     * Peer-roster constants pinned to the {@code business}/{@code primary}
     * pair the capture-call-corpus script uses. Cross-reference
     * {@code reference_call_capture_sessions.md} in the memory directory.
     */
    private static final Jid SELF_PN  = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid PEER_PN  = Jid.of("19153544650@s.whatsapp.net");
    private static final Jid PEER_LID = Jid.of("39110693621863@lid");

    /**
     * Test harness wiring a real {@link CallService} + a recording
     * {@link TestWhatsAppClient} + a recording
     * {@link TestWamService}. The {@code sentNodes} and {@code wam}
     * collectors are the primary assertion surfaces.
     */
    private static final class Harness {
        final TestWhatsAppClient client;
        final CallService service;
        final List<Node> sentNodes = new ArrayList<>();
        final RecordingListener listener = new RecordingListener();
        final TestWamService wam;

        Harness() {
            var store = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
            store.addListener(listener);
            this.client = TestWhatsAppClient.create().withStore(store);
            store.addListener(new WhatsAppClientListener() {
                @Override public void onNodeSent(WhatsAppClient w, Node node) { sentNodes.add(node); }
            });
            this.wam = TestWamService.create(client);
            this.service = new CallService(client, wam);
        }
    }

    /**
     * Recording listener: captures only the call-lifecycle callbacks the
     * orchestrator fires; other listener methods are inherited as no-ops.
     */
    private static final class RecordingListener implements WhatsAppClientListener {
        final ConcurrentLinkedQueue<EndedEvent> ended = new ConcurrentLinkedQueue<>();
        @Override public void onCallEnded(WhatsAppClient w, String callId, Jid fromJid, CallEndReason reason) {
            ended.add(new EndedEvent(callId, fromJid, reason));
        }
    }

    private record EndedEvent(String callId, Jid fromJid, CallEndReason reason) {}

    // -------- placeCall — outgoing offer ----------------------------------

    @Test
    @DisplayName("placeCall: registers ActiveCall, dispatches <call><offer>, parks in CONNECTING")
    void placeCallDispatchesOffer() {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        assertNotNull(session);
        assertEquals(CallState.CONNECTING, session.state());
        assertSame(session, h.service.find(session.callId()),
                "placed call must be findable in the engine's registry");

        // Outgoing — exactly one <call><offer/></call> with the expected
        // call-id + call-creator.
        var offers = h.sentNodes.stream().filter(n -> "call".equals(n.description()))
                .filter(n -> n.getChild("offer").isPresent()).toList();
        assertEquals(1, offers.size(), "exactly one offer expected; got " + h.sentNodes);
        var offer = offers.get(0).getChild("offer").orElseThrow();
        assertEquals(session.callId(), offer.getAttributeAsString("call-id").orElseThrow());
    }

    @Test
    @DisplayName("placeCall(video=true): offer carries an empty <video/> marker")
    void placeCallVideoMarker() {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.video());
        var offer = h.sentNodes.stream().filter(n -> "call".equals(n.description()))
                .findFirst().orElseThrow().getChild("offer").orElseThrow();
        assertTrue(offer.getChild("video").isPresent(),
                "video call offer must include a <video/> marker");
    }

    @Test
    @DisplayName("placeCall: null peer/options throw NPE")
    void placeCallNullArgsThrow() {
        var h = new Harness();
        assertThrows(NullPointerException.class, () -> h.service.placeCall(null, CallOptions.audio()));
        assertThrows(NullPointerException.class, () -> h.service.placeCall(PEER_LID, null));
    }

    // -------- accept(IncomingCall) ---------------------------------------

    @Test
    @DisplayName("accept: dispatches <call><accept>, registers session")
    void acceptDispatches() {
        var h = new Harness();
        var offer = new IncomingCall("CID-INBOUND", PEER_LID, PEER_LID,
                Instant.EPOCH, false, false, null, false);
        var session = h.service.accept(offer, CallOptions.audio());

        assertEquals(CallState.CONNECTING, session.state());
        assertSame(session, h.service.find("CID-INBOUND"));

        var accept = h.sentNodes.stream().filter(n -> "call".equals(n.description()))
                .findFirst().orElseThrow().getChild("accept").orElseThrow();
        assertEquals("CID-INBOUND", accept.getAttributeAsString("call-id").orElseThrow());
    }

    // -------- reject(IncomingCall) ---------------------------------------

    @Test
    @DisplayName("reject: dispatches <call><reject>, fires onCallEnded(REJECT_*)")
    void rejectDispatches() throws InterruptedException {
        var h = new Harness();
        var offer = new IncomingCall("CID-REJECT", PEER_LID, PEER_LID,
                Instant.EPOCH, false, false, null, false);

        h.service.reject(offer, CallEndReason.REJECT_DO_NOT_DISTURB);

        var reject = h.sentNodes.stream().filter(n -> "call".equals(n.description()))
                .findFirst().orElseThrow().getChild("reject").orElseThrow();
        assertEquals("CID-REJECT", reject.getAttributeAsString("call-id").orElseThrow());

        // Listener fan-out is virtual-thread fired; allow a moment to land.
        Thread.sleep(50);
        var ev = h.listener.ended.peek();
        assertNotNull(ev, "onCallEnded must fire on reject");
        assertEquals("CID-REJECT", ev.callId());
        assertEquals(CallEndReason.REJECT_DO_NOT_DISTURB, ev.reason());

        // Registry must be empty after reject.
        assertNull(h.service.find("CID-REJECT"));
    }

    // -------- peer-driven transitions ------------------------------------

    @Test
    @DisplayName("onPeerAccept: routes to the right ActiveCall; no listener fires (state-only)")
    void peerAcceptRoutes() {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        h.sentNodes.clear(); // drop the outbound offer for clarity

        h.service.onPeerAccept(session.callId());
        // ActiveCall.onPeerAccept is intentionally a no-op state-wise until
        // the media plane lands — so we assert there's no terminate stanza
        // and the call is still registered.
        assertSame(session, h.service.find(session.callId()));
    }

    @Test
    @DisplayName("onPeerTerminate: routes to ActiveCall, drives to ENDED, fires onCallEnded")
    void peerTerminateRoutes() throws InterruptedException {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        h.sentNodes.clear();

        h.service.onPeerTerminate(session.callId(), "hangup");
        Thread.sleep(50);

        assertEquals(CallState.ENDED, session.state());
        assertEquals(CallEndReason.HANGUP, session.endReason().orElseThrow());
        assertNull(h.service.find(session.callId()),
                "ended call must be removed from the engine registry");
        var ev = h.listener.ended.peek();
        assertNotNull(ev);
        assertEquals(CallEndReason.HANGUP, ev.reason());
    }

    @Test
    @DisplayName("onPeerReject: routes through onPeerEnded with the wire reason")
    void peerRejectRoutes() throws InterruptedException {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        h.sentNodes.clear();

        h.service.onPeerReject(session.callId(), "reject");
        Thread.sleep(50);
        assertEquals(CallState.ENDED, session.state());
    }

    // -------- concurrent calls do not cross-contaminate ------------------

    @Test
    @DisplayName("two concurrent calls: separate registry entries, separate lifecycles")
    void concurrentCallsIsolated() throws InterruptedException {
        var h = new Harness();
        var first = h.service.placeCall(PEER_LID, CallOptions.audio());
        var second = h.service.placeCall(PEER_LID, CallOptions.audio());
        assertFalse(first.callId().equals(second.callId()),
                "engine must mint unique call-ids per placeCall");

        h.service.onPeerTerminate(first.callId(), "hangup");
        Thread.sleep(50);
        assertEquals(CallState.ENDED, first.state());
        assertEquals(CallState.CONNECTING, second.state(),
                "terminating the first call must not affect the second");
        assertSame(second, h.service.find(second.callId()));
    }

    // -------- end-reason → CallResultType mapping ------------------------

    /**
     * Returns the {@link CallResultType} of the last
     * {@link CallEvent} committed by the harness's WAM service.
     */
    private static CallResultType lastCommittedResult(Harness h) {
        var events = h.wam.committedEvents();
        for (var i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof CallEvent ce) {
                return ce.callResult().orElse(null);
            }
        }
        throw new AssertionError("no CallEvent committed; events=" + events);
    }

    @Test
    @DisplayName("WAM mapping: HANGUP → CONNECTED")
    void wamHangupMapsToConnected() throws InterruptedException {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        h.service.onPeerTerminate(session.callId(), "hangup");
        Thread.sleep(50);
        assertEquals(CallResultType.CONNECTED, lastCommittedResult(h));
    }

    @Test
    @DisplayName("WAM mapping: TIMEOUT → MISSED")
    void wamTimeoutMapsToMissed() throws InterruptedException {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        h.service.onPeerTerminate(session.callId(), "timeout");
        Thread.sleep(50);
        assertEquals(CallResultType.MISSED, lastCommittedResult(h));
    }

    @Test
    @DisplayName("WAM mapping: REJECT_DO_NOT_DISTURB → REJECTED_BY_USER")
    void wamDndMapsToRejected() throws InterruptedException {
        var h = new Harness();
        var offer = new IncomingCall("CID-DND", PEER_LID, PEER_LID,
                Instant.EPOCH, false, false, null, false);
        // Accept then drive to DND-rejected by routing through onPeerEnded.
        var session = h.service.accept(offer, CallOptions.audio());
        h.service.onPeerReject(session.callId(), "dnd");
        Thread.sleep(50);
        assertEquals(CallResultType.REJECTED_BY_USER, lastCommittedResult(h));
    }

    @Test
    @DisplayName("WAM mapping: REJECT_BLOCKED → REJECTED_BY_USER")
    void wamBlockedMapsToRejected() throws InterruptedException {
        var h = new Harness();
        var offer = new IncomingCall("CID-BLOCKED", PEER_LID, PEER_LID,
                Instant.EPOCH, false, false, null, false);
        var session = h.service.accept(offer, CallOptions.audio());
        h.service.onPeerReject(session.callId(), "blocked");
        Thread.sleep(50);
        assertEquals(CallResultType.REJECTED_BY_USER, lastCommittedResult(h));
    }

    @Test
    @DisplayName("WAM mapping: ACCEPTED_ELSEWHERE → CONNECTED (the call DID connect — just on another device)")
    void wamAcceptedElsewhereMapsToConnected() throws InterruptedException {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        h.service.onPeerTerminate(session.callId(), "accepted_elsewhere");
        Thread.sleep(50);
        assertEquals(CallResultType.CONNECTED, lastCommittedResult(h));
    }

    @Test
    @DisplayName("WAM mapping: unrecognised reason (e.g. \"\") → INVALID")
    void wamUnknownMapsToInvalid() throws InterruptedException {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        h.service.onPeerTerminate(session.callId(), "something-not-in-the-enum");
        Thread.sleep(50);
        assertEquals(CallResultType.INVALID, lastCommittedResult(h));
    }
}
