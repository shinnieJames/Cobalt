package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.internal.transport.ActiveCallTransport;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.auties00.cobalt.call.internal.CallService;

/**
 * Unit tests for {@link ActiveCall}'s state machine and lifecycle —
 * verifies transitions to {@link CallState#ENDED}, the
 * {@link CallEndReason} mapping, mute toggles, the four media-port
 * sentinels, and the {@link IncomingCall#markResponded()} one-shot
 * gate that {@code WhatsAppClient.acceptCall}/{@code rejectCall} rely
 * on.
 *
 * <p>These tests drive a real {@link CallService} against a
 * {@link TestWhatsAppClient}. The previous {@code RecordingEngine}
 * subclass-stub has been removed — outgoing stanzas are asserted on
 * the {@code TestWhatsAppClient}'s {@code onNodeSent} pipeline instead.
 */
public class ActiveCallTest {

    private static final Jid PEER = Jid.of("12345@s.whatsapp.net");
    private static final Jid SELF = Jid.of("99999@s.whatsapp.net");

    /**
     * Wires a real {@link CallService} + {@link TestWhatsAppClient} +
     * outgoing-stanza recorder. Owned by each test; tests inspect
     * {@link #sentNodes} to assert wire behaviour.
     */
    private static final class Wiring {
        final TestWhatsAppClient client;
        final CallService service;
        final List<Node> sentNodes = new ArrayList<>();

        Wiring() {
            var store = MessageFixtures.temporaryStore(SELF, null);
            this.client = TestWhatsAppClient.create().withStore(store);
            store.addListener(new WhatsAppClientListener() {
                @Override public void onNodeSent(WhatsAppClient w, Node node) { sentNodes.add(node); }
            });
            this.service = new CallService(client, null);
        }

        long count(String description, String childTag) {
            return sentNodes.stream()
                    .filter(n -> description.equals(n.description()))
                    .filter(n -> n.getChild(childTag).isPresent())
                    .count();
        }
    }

    @Test
    public void hangupTransitionsOutboundCallToEnded() throws InterruptedException {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-1", PEER, PEER, SELF, true, CallOptions.audio());

        assertEquals(CallState.CONNECTING, call.state());
        assertTrue(call.endReason().isEmpty());

        call.hangup();

        assertEquals(CallState.ENDED, call.state());
        assertEquals(CallEndReason.HANGUP, call.endReason().orElseThrow());
        // Exactly one outgoing <call><terminate/></call>.
        assertEquals(1, w.count("call", "terminate"),
                "hangup must emit one <call><terminate/></call>");

        // remoteAudioSource.next() must not block once the call has ended.
        assertNull(call.remoteAudioSource().next());
    }

    @Test
    public void peerEndedMapsWireReason() throws InterruptedException {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-2", PEER, PEER, SELF, true, CallOptions.audio());

        call.onPeerEnded("hangup");

        assertEquals(CallState.ENDED, call.state());
        assertEquals(CallEndReason.HANGUP, call.endReason().orElseThrow());
        // Peer ended; we did NOT send a terminate stanza.
        assertEquals(0, w.count("call", "terminate"),
                "peer-driven end must not send our own terminate");
    }

    @Test
    public void unknownWireReasonMapsToUnknown() {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-3", PEER, PEER, SELF, true, CallOptions.audio());

        call.onPeerEnded("something-not-in-the-enum");

        assertEquals(CallEndReason.UNKNOWN, call.endReason().orElseThrow());
    }

    @Test
    public void muteOnlyFiresOnTransition() {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-4", PEER, PEER, SELF, true, CallOptions.video());

        call.mute(true, false);
        call.mute(true, false);   // no-op
        call.mute(false, false);  // unmute audio
        call.mute(false, true);   // mute video
        call.mute(false, true);   // no-op

        assertEquals(2, w.count("call", "mute_v2"),
                "mute(true) then mute(false) = 2 transitions");
        assertEquals(1, w.count("call", "video_state"),
                "video toggle = 1 transition");
    }

    @Test
    public void mutedAudioSinkDropsFrames() throws InterruptedException {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-5", PEER, PEER, SELF, true, CallOptions.audio());

        var droppedFrame = new AudioFrame(new short[160], 0L);
        var deliveredFrame = new AudioFrame(new short[160], 1L);

        call.mute(true, false);
        call.localAudioSink().write(droppedFrame);
        call.mute(false, false);
        call.localAudioSink().write(deliveredFrame);

        assertSame(deliveredFrame, call.takeOutboundAudio(),
                "the muted-time write must have been dropped, leaving only the post-unmute frame");
    }

    @Test
    public void closeUnblocksAllMediaPorts() throws InterruptedException {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-6", PEER, PEER, SELF, true, CallOptions.video());

        call.close();

        assertEquals(CallState.ENDED, call.state());
        assertNull(call.remoteAudioSource().next());
        assertNull(call.remoteVideoSource().next());
        assertNull(call.takeOutboundAudio());
        assertNull(call.takeOutboundVideo());
    }

    @Test
    public void incomingCallIsOneShot() {
        var offer = new IncomingCall(
                "id-7", PEER, PEER,
                Instant.EPOCH,
                false, false, null, false);

        offer.markResponded();

        assertThrows(IllegalStateException.class, offer::markResponded);
    }

    @Test
    public void doubleHangupIsNoOp() {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-8", PEER, PEER, SELF, true, CallOptions.audio());

        call.hangup();
        call.hangup();
        call.hangup();

        assertEquals(1, w.count("call", "terminate"),
                "subsequent hangups must be no-ops");
        assertFalse(call.endReason().isEmpty());
    }

    @Test
    public void videoUpgradeFlowRoutesThroughEngine() {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-up", PEER, PEER, SELF, true, CallOptions.audio());

        call.requestVideoUpgrade();
        assertEquals(1, w.count("call", "video_state"),
                "request: peer is asked to enable video");

        call.acceptVideoUpgrade();
        assertEquals(2, w.count("call", "video_state"),
                "accept: confirmation sent as video-state on");

        call.rejectVideoUpgrade();
        assertEquals(3, w.count("call", "video_state"),
                "reject: video-state off sent as denial");
    }

    @Test
    public void transportLifecycleMatchesCallLifecycle() {
        var w = new Wiring();
        var call = new ActiveCall(w.service, "id-tx", PEER, PEER, SELF, true, CallOptions.audio());

        // Fresh call → transport is IDLE.
        assertEquals(
                ActiveCallTransport.State.IDLE,
                call.transport().state());

        // hangup → call → ENDED → transport closes.
        call.hangup();
        assertEquals(CallState.ENDED, call.state());
        assertEquals(
                ActiveCallTransport.State.CLOSED,
                call.transport().state());
    }
}
