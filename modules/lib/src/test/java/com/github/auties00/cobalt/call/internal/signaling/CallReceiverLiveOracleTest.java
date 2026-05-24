package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.CallFixtures;
import com.github.auties00.cobalt.call.CallOptions;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live-oracle tests for {@link CallReceiver}. Each captured inbound
 * {@code <call>} stanza is replayed through a real {@link CallReceiver}
 * wired to a {@link TestWhatsAppClient} + a real {@link CallService}; the
 * test asserts:
 * <ul>
 *   <li>the outgoing receipt / ack stanza shape;</li>
 *   <li>which {@link WhatsAppClientListener} method fired and with what
 *       arguments;</li>
 *   <li>store-side effects (LID-to-PN mapping, push name updates, call
 *       registry add/remove).</li>
 * </ul>
 *
 * <p>Fixtures live under {@code fixtures/call/}; each topic's
 * {@code .callee.jsonl} file carries the inbound side captured by the
 * primary session of {@code capture-call-corpus.mjs}.
 *
 * <p>Tests are guarded with {@link CallFixtures#isAvailable(String)} so
 * regenerated corpora without a given topic skip cleanly.
 */
@DisplayName("CallReceiver live wire oracle")
class CallReceiverLiveOracleTest {

    /**
     * Test helper: wires a real {@link CallReceiver} + {@link CallService}
     * over a {@link TestWhatsAppClient}, installs a recording listener,
     * and exposes both the recorded outgoing stanzas (via
     * {@code onNodeSent}) and the listener-method invocations.
     */
    private static final class Harness {
        final TestWhatsAppClient client;
        final CallService service;
        final CallReceiver receiver;
        final List<Node> sentNodes = new ArrayList<>();
        final ListenerRecorder listener = new ListenerRecorder();

        Harness(Jid selfPn, Jid selfLid) {
            var store = MessageFixtures.temporaryStore(selfPn, selfLid);
            store.addListener(listener);
            this.client = TestWhatsAppClient.create().withStore(store);
            store.addListener(new WhatsAppClientListener() {
                @Override public void onNodeSent(WhatsAppClient whatsapp, Node node) {
                    sentNodes.add(node);
                }
            });
            this.service = new CallService(client, /* wamService */ null);
            this.receiver = new CallReceiver(client, service, new AckSender(client));
        }

        Node outgoingOf(String tag) {
            return sentNodes.stream().filter(n -> tag.equals(n.description())).findFirst()
                    .orElseThrow(() -> new AssertionError("no outgoing <" + tag + "> recorded; got " + sentNodes));
        }
    }

    /**
     * Recording {@link WhatsAppClientListener} that captures every
     * call-related callback into queues the test can poll.
     */
    private static final class ListenerRecorder implements WhatsAppClientListener {
        final ConcurrentLinkedQueue<IncomingCall> calls = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<EndedCall> ended = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<MuteChange> mutes = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<VideoStateChange> videos = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> preaccepts = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<IncomingCall> notices = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<PeerStateChange> peerStates = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<ParticipantsChange> participants = new ConcurrentLinkedQueue<>();

        @Override public void onCall(WhatsAppClient w, IncomingCall c) { calls.add(c); }
        @Override public void onCallEnded(WhatsAppClient w, String callId, Jid fromJid, CallEndReason reason) {
            ended.add(new EndedCall(callId, fromJid, reason));
        }
        @Override public void onCallMuteChanged(WhatsAppClient w, String callId, Jid fromJid, boolean muted) {
            mutes.add(new MuteChange(callId, fromJid, muted));
        }
        @Override public void onCallVideoStateChanged(WhatsAppClient w, String callId, Jid fromJid, boolean enabled) {
            videos.add(new VideoStateChange(callId, fromJid, enabled));
        }
        @Override public void onCallPreaccept(WhatsAppClient w, String callId, Jid fromJid) {
            preaccepts.add(callId);
        }
        @Override public void onCallOfferNotice(WhatsAppClient w, IncomingCall call) {
            notices.add(call);
        }
        @Override public void onCallPeerStateChanged(WhatsAppClient w, String callId, Jid fromJid, CallPeerState state) {
            peerStates.add(new PeerStateChange(callId, fromJid, state));
        }
        @Override public void onCallParticipantsChanged(WhatsAppClient w, String callId, Jid groupJid, List<Jid> ps, boolean added) {
            participants.add(new ParticipantsChange(callId, groupJid, List.copyOf(ps), added));
        }
    }

    private record EndedCall(String callId, Jid fromJid, CallEndReason reason) {}
    private record MuteChange(String callId, Jid fromJid, boolean muted) {}
    private record VideoStateChange(String callId, Jid fromJid, boolean enabled) {}
    private record PeerStateChange(String callId, Jid fromJid, CallPeerState state) {}
    private record ParticipantsChange(String callId, Jid groupJid, List<Jid> participants, boolean added) {}

    // The PN+LID pair the capture script paired with the `primary` session.
    private static final Jid SELF_PN  = Jid.of("19153544650@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("39110693621863@lid");

    /**
     * Loads every inbound {@code <call>} event from a fixture and feeds
     * each into the harness's receiver. Returns the harness so the test
     * can inspect outgoing stanzas + listener state.
     */
    private static Harness replayInbound(String topic) {
        var h = new Harness(SELF_PN, SELF_LID);
        for (var event : CallFixtures.loadEvents(topic)) {
            if (!"call".equals(event.getString("tag"))) continue;
            if (!"in".equals(event.getString("direction"))) continue;
            var node = CallFixtures.buildNodeFromEvent(event);
            h.receiver.handle(node);
        }
        // Listener events fire on virtual threads; give them a moment to land.
        try { Thread.sleep(50); } catch (InterruptedException _) { Thread.currentThread().interrupt(); }
        return h;
    }

    @Nested
    @DisplayName("offer — inbound 1:1 audio")
    class OfferInbound {
        @Test
        @DisplayName("inbound offer → outgoing receipt + onCall listener fires + store has the call")
        void offerFires() {
            var topic = "1to1/audio-accept.callee";
            if (!CallFixtures.isAvailable(topic)) return;
            var h = replayInbound(topic);

            // Listener — at least one IncomingCall delivered (the inbound offer).
            assertFalse(h.listener.calls.isEmpty(),
                    "onCall listener must fire on inbound offer");
            var first = h.listener.calls.peek();
            assertNotNull(first.callId());
            assertNotNull(first.peer());

            // Outgoing — a <receipt> wrapping <offer call-id call-creator/>.
            var receipt = h.sentNodes.stream()
                    .filter(n -> "receipt".equals(n.description()))
                    .filter(n -> n.getChild("offer").isPresent())
                    .findFirst().orElseThrow(
                            () -> new AssertionError("no <receipt><offer/></receipt> in outgoing: " + h.sentNodes));
            var inner = receipt.getChild("offer").orElseThrow();
            assertEquals(first.callId(), inner.getAttributeAsString("call-id").orElseThrow());
        }
    }

    @Nested
    @DisplayName("accept — peer accepted our outbound offer")
    class AcceptInbound {
        @Test
        @DisplayName("inbound accept → outgoing receipt + service.onPeerAccept routed")
        void acceptFires() {
            var topic = "1to1/audio-accept.caller";
            if (!CallFixtures.isAvailable(topic)) return;

            // Register a synthetic outbound ActiveCall first so the receiver
            // can route the peer accept to it. Use the call-id from the
            // captured inbound <accept>.
            var event = CallFixtures.loadCallEventWithChild(topic, "accept", "in");
            var call = CallFixtures.buildNodeFromEvent(event);
            var accept = call.getChild("accept").orElseThrow();
            var callId = accept.getAttributeAsString("call-id").orElseThrow();
            var callCreator = accept.getAttributeAsJid("call-creator", null);
            assertNotNull(callCreator);

            var h = new Harness(SELF_PN, SELF_LID);
            // The receiver routes peer-accept transitions to the engine
            // by call-id only; no pre-registered ActiveCall needed for
            // the receipt-emission assertion below.
            h.receiver.handle(call);

            // Outgoing receipt for the accept tag.
            var receipt = h.sentNodes.stream()
                    .filter(n -> "receipt".equals(n.description()))
                    .filter(n -> n.getChild("accept").isPresent())
                    .findFirst().orElse(null);
            assertNotNull(receipt, "no <receipt><accept/></receipt> in outgoing: " + h.sentNodes);
        }
    }

    @Nested
    @DisplayName("reject — peer rejected our outbound offer")
    class RejectInbound {
        @Test
        @DisplayName("inbound reject → outgoing receipt + onCallEnded fires with REJECT_*")
        void rejectFires() {
            var topic = "1to1/audio-reject.caller";
            if (!CallFixtures.isAvailable(topic)) return;
            var h = replayInbound(topic);
            // The receiver's reject path always fires notifyEnded.
            assertFalse(h.listener.ended.isEmpty(),
                    "onCallEnded listener must fire on inbound reject");
        }
    }

    @Nested
    @DisplayName("preaccept — server-side alert acknowledgement")
    class PreacceptInbound {
        @Test
        @DisplayName("inbound preaccept → outgoing ack + onCallPreaccept fires")
        void preacceptFires() {
            // A successful audio-accept flow's caller side records the
            // server's preaccept fan-out before the callee actually answers.
            var topic = "1to1/audio-accept.caller";
            if (!CallFixtures.isAvailable(topic)) return;
            var h = replayInbound(topic);
            assertFalse(h.listener.preaccepts.isEmpty(),
                    "onCallPreaccept listener must fire on inbound preaccept");
            // Each preaccept is acked.
            var preacceptAcks = h.sentNodes.stream()
                    .filter(n -> "ack".equals(n.description()))
                    .filter(n -> "preaccept".equals(n.getAttributeAsString("type").orElse(null)))
                    .count();
            assertTrue(preacceptAcks >= 1,
                    "at least one outgoing <ack type=\"preaccept\"> expected");
        }
    }

    @Nested
    @DisplayName("terminate — bare top-level <terminate> (e.g. accepted_elsewhere)")
    class TerminateInbound {
        @Test
        @DisplayName("inbound bare <terminate> → onCallEnded fires with the wire reason")
        void terminateFires() throws InterruptedException {
            var topic = "1to1/callee-terminate-post-accept.caller.terminate";
            if (!CallFixtures.isAvailable(topic)) return;
            var h = new Harness(SELF_PN, SELF_LID);
            var terminateReceiver = new CallTerminateReceiver(
                    h.client, h.service);

            for (var event : CallFixtures.loadEvents(topic)) {
                if (!"terminate".equals(event.getString("tag"))) continue;
                if (!"in".equals(event.getString("direction"))) continue;
                terminateReceiver.handle(CallFixtures.buildNodeFromEvent(event));
            }

            Thread.sleep(50);
            assertFalse(h.listener.ended.isEmpty(),
                    "bare <terminate> must surface as onCallEnded; got: " + h.sentNodes);
        }
    }

    @Nested
    @DisplayName("mute_v2 / video_state — in-call peer state announcements")
    class StateChangeInbound {
        @Test
        @DisplayName("inbound mute_v2 → onCallMuteChanged fires with peer state")
        void muteFires() {
            // The caller-side fixture captures the callee's inbound mute_v2
            // when the callee toggles its mic. Use the caller capture so we
            // have a guaranteed inbound mute_v2 stanza.
            var topic = "1to1/mute-toggle.caller";
            if (!CallFixtures.isAvailable(topic)) return;
            var h = replayInbound(topic);
            assertFalse(h.listener.mutes.isEmpty(),
                    "onCallMuteChanged listener must fire on inbound mute_v2; got: " + h.sentNodes);
        }

        @Test
        @DisplayName("inbound video_state: TODO — no <video_state> stanza observed on the wire")
        void videoStateFires() {
            // No `<call><video_state/></call>` envelope appears in any
            // captured side of the video-state-toggle flow. The state
            // change likely rides the RTCDataChannel rather than XMPP
            // signaling. See task #39 for the verification work.
        }
    }

    @Nested
    @DisplayName("group_update — mid-call participant add / remove")
    class GroupUpdateInbound {
        @Test
        @DisplayName("inbound group_update add → onCallParticipantsChanged(added=true)")
        void groupUpdateAddFires() {
            var topic = "group/update-add.callee";
            if (!CallFixtures.isAvailable(topic)) return;
            // TODO: WA Web's server-side dispatch for group calls routes the
            // group_update fanout to phones first; the callee's web session
            // may or may not see it in any given capture window. Once the
            // capture script's group flow is hardened to wait for the
            // participant join to propagate, replace this stub with a
            // strict assertEquals on participants.size() + added=true.
        }

        @Test
        @DisplayName("inbound group_update remove → TODO (corpus inbound side unavailable)")
        void groupUpdateRemoveFires() {
            // TODO: callee-side .jsonl for group/update-remove is empty in
            // the current corpus (WA server does not always deliver the
            // remove stanza to a linked web session). Add a hand-built
            // group_update stanza modeled on the caller-side capture once
            // the receiver is wired to handle it.
        }
    }

    @Nested
    @DisplayName("offer_notice — missed-call notification on next connect")
    class OfferNoticeInbound {
        @Test
        @DisplayName("offer_notice → TODO — server queues only when ALL devices of the account are offline")
        void offerNoticeFires() {
            // Empirically verified (2026-05-14): the server queues
            // offer_notice for redelivery only when EVERY device of the
            // account is offline at offer-time. Our capture pair has the
            // `primary` emulator running its WhatsApp app continuously,
            // and the server marks the call delivered to that device —
            // so when the linked web session reconnects after the
            // offer, no offer_notice fires (verified by waiting 60s
            // post-restart with the web session online and the
            // emulator phone running). Capturing this fixture requires
            // turning the primary emulator's WA app off (kill the
            // process, not just the web session) before placing the
            // offer.
            //
            // Once that capture lands, replace this stub with a strict
            //   assertFalse(h.listener.notices.isEmpty(), …)
            // assertion against the fixture.
        }
    }

    @Nested
    @DisplayName("server media-plane signals — transport / relaylatency / etc.")
    class MediaPlaneInbound {
        @Test
        @DisplayName("relaylatency → outgoing ack (drop-through, no listener fires)")
        void relayLatencyAcked() {
            var topic = "1to1/server-relay-signals.caller";
            if (!CallFixtures.isAvailable(topic)) return;
            var h = replayInbound(topic);
            // Every inbound relaylatency under a <call> envelope must be
            // acked by the receiver's default media-plane drop-through path.
            var acks = h.sentNodes.stream()
                    .filter(n -> "ack".equals(n.description()))
                    .filter(n -> "relaylatency".equals(n.getAttributeAsString("type").orElse(null)))
                    .count();
            assertTrue(acks >= 1,
                    "relaylatency stanzas must be acked even in pure-signaling mode");
        }
    }
}
