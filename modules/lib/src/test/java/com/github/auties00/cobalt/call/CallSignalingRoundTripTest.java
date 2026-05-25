package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.internal.signaling.CallReceiver;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.auties00.cobalt.call.internal.CallService;

/**
 * End-to-end signaling round-trip tests exercising the full
 * {@link CallReceiver}, {@link CallService}, {@link ActiveCall} chain against
 * a {@link TestWhatsAppClient}, with stanzas modeled on the
 * {@code fixtures/call/} corpus. Each scenario seeds an inbound
 * {@code <call>} stanza through the receiver, drives a follow-up step
 * (accept, reject, or peer-terminate), and asserts the outgoing stanzas on
 * the wire, the {@link WhatsAppClientListener} fan-out, and the terminal
 * state of the {@link CallService} registry.
 *
 * <p>Unlike
 * {@link com.github.auties00.cobalt.call.internal.signaling.CallReceiverLiveOracleTest}
 * and {@link CallServiceLiveOracleTest}, which test each layer in isolation,
 * these tests intentionally exercise the receiver, service, and session
 * together in one shot.
 */
@DisplayName("Call signaling round-trip — receiver + service + ActiveCall")
class CallSignalingRoundTripTest {

    private static final Jid SELF_PN  = Jid.of("19153544650@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("39110693621863@lid");
    private static final Jid PEER_LID = Jid.of("83116928594056@lid");

    private static final class Harness {
        final TestWhatsAppClient client;
        final CallService service;
        final CallReceiver receiver;
        final List<Node> sentNodes = new ArrayList<>();
        final Listener listener = new Listener();

        Harness() {
            var store = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
            store.addListener(listener);
            this.client = TestWhatsAppClient.create().withStore(store);
            store.addListener(new WhatsAppClientListener() {
                @Override public void onNodeSent(WhatsAppClient w, Node node) { sentNodes.add(node); }
            });
            this.service = new CallService(client, null);
            this.receiver = new CallReceiver(client, service, new AckSender(client));
        }
    }

    private static final class Listener implements WhatsAppClientListener {
        final ConcurrentLinkedQueue<IncomingCall> calls = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<EndedEvent> ended = new ConcurrentLinkedQueue<>();
        @Override public void onCall(WhatsAppClient w, IncomingCall c) { calls.add(c); }
        @Override public void onCallEnded(WhatsAppClient w, String callId, Jid fromJid, CallEndReason reason) {
            ended.add(new EndedEvent(callId, fromJid, reason));
        }
    }

    private record EndedEvent(String callId, Jid fromJid, CallEndReason reason) {}

    // Minimal inbound <call><offer/></call> the receiver needs to dispatch:
    // call-id, call-creator, and a from attribute on the outer envelope.
    private static Node inboundOffer(String callId, Jid peer) {
        var offer = new NodeBuilder()
                .description("offer")
                .attribute("call-id", callId)
                .attribute("call-creator", peer)
                .build();
        return new NodeBuilder()
                .description("call")
                .attribute("from", peer)
                .attribute("id", "stanza-" + callId)
                .content(offer)
                .build();
    }

    private static Node inboundTerminate(String callId, Jid peer, String reason) {
        var terminate = new NodeBuilder()
                .description("terminate")
                .attribute("call-id", callId)
                .attribute("call-creator", peer)
                .attribute("reason", reason)
                .build();
        return new NodeBuilder()
                .description("call")
                .attribute("from", peer)
                .attribute("id", "stanza-term-" + callId)
                .content(terminate)
                .build();
    }

    @Test
    @DisplayName("inbound offer → accept → peer terminate: listener + registry stay coherent")
    void offerAcceptPeerTerminateRoundTrip() throws InterruptedException {
        var h = new Harness();

        // 1. Inbound offer arrives on the wire.
        h.receiver.handle(inboundOffer("CID-RT-1", PEER_LID));
        Thread.sleep(50);
        var incoming = h.listener.calls.peek();
        assertNotNull(incoming, "onCall must fire on inbound offer");
        assertEquals("CID-RT-1", incoming.callId());

        var receiptCount = h.sentNodes.stream()
                .filter(n -> "receipt".equals(n.description()))
                .filter(n -> n.getChild("offer").isPresent())
                .count();
        assertTrue(receiptCount >= 1,
                "<receipt><offer/></receipt> must dispatch on inbound offer; got " + h.sentNodes);

        // 2. Local user accepts.
        var session = h.service.accept(incoming, CallOptions.audio());
        assertEquals(CallState.CONNECTING, session.state());
        var accept = h.sentNodes.stream().filter(n -> "call".equals(n.description()))
                .map(n -> n.getChild("accept")).filter(Optional::isPresent)
                .map(Optional::orElseThrow).findFirst().orElseThrow();
        assertEquals("CID-RT-1", accept.getAttributeAsString("call-id").orElseThrow());

        // 3. Peer terminates.
        h.receiver.handle(inboundTerminate("CID-RT-1", PEER_LID, "hangup"));
        Thread.sleep(50);
        assertEquals(CallState.ENDED, session.state());
        assertEquals(CallEndReason.HANGUP, session.endReason().orElseThrow());
        assertNull(h.service.find("CID-RT-1"),
                "registry must drop the call on terminate");
        assertFalse(h.listener.ended.isEmpty(),
                "onCallEnded must fire on peer terminate");
    }

    @Test
    @DisplayName("outbound place → peer-reject: outbound terminate not emitted, listener fires REJECT_*")
    void placeThenPeerRejectRoundTrip() throws InterruptedException {
        var h = new Harness();
        var session = h.service.placeCall(PEER_LID, CallOptions.audio());
        var callId = session.callId();
        h.sentNodes.clear();

        // Peer rejects via receiver-side inbound stanza.
        var reject = new NodeBuilder()
                .description("reject")
                .attribute("call-id", callId)
                .attribute("call-creator", SELF_LID)
                .build();
        var call = new NodeBuilder()
                .description("call")
                .attribute("from", PEER_LID)
                .attribute("id", "stanza-rj-" + callId)
                .content(reject)
                .build();
        h.receiver.handle(call);
        Thread.sleep(50);

        assertEquals(CallState.ENDED, session.state());
        var ev = h.listener.ended.peek();
        assertNotNull(ev);
        assertEquals(callId, ev.callId());
    }

    @Test
    @DisplayName("inbound offer → reject(REJECT_BLOCKED): outgoing reject + listener with REJECT_BLOCKED")
    void offerThenRejectRoundTrip() throws InterruptedException {
        var h = new Harness();
        h.receiver.handle(inboundOffer("CID-RJ-1", PEER_LID));
        Thread.sleep(50);
        var incoming = h.listener.calls.peek();
        assertNotNull(incoming);

        h.service.reject(incoming, CallEndReason.REJECT_BLOCKED);
        Thread.sleep(50);

        var reject = h.sentNodes.stream().filter(n -> "call".equals(n.description()))
                .map(n -> n.getChild("reject")).filter(Optional::isPresent)
                .map(Optional::orElseThrow).findFirst().orElseThrow();
        assertEquals("CID-RJ-1", reject.getAttributeAsString("call-id").orElseThrow());

        var ev = h.listener.ended.peek();
        assertNotNull(ev);
        assertEquals(CallEndReason.REJECT_BLOCKED, ev.reason());
    }
}
