package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.io.AudioFrame;
import com.github.auties00.cobalt.call.signaling.CallEndReason;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ActiveCall}'s state machine and lifecycle —
 * verifies transitions to {@link CallState#ENDED}, the
 * {@link CallEndReason} mapping, mute toggles, the four media-port
 * sentinels, and the {@link IncomingCall#markResponded()} one-shot
 * gate that {@code WhatsAppClient.acceptCall}/{@code rejectCall}
 * rely on.
 *
 * <p>These tests don't depend on the call engine being wired to a
 * live {@code WhatsAppClient} — they exercise {@link ActiveCall}
 * directly with a synthetic engine that captures the signaling
 * stanzas it would send.
 */
public class ActiveCallTest {

    /**
     * The peer JID used by every test case.
     */
    private static final Jid PEER = Jid.of("12345", JidServer.user());

    /**
     * The local self JID used by every test case.
     */
    private static final Jid SELF = Jid.of("99999", JidServer.user());

    /**
     * A plain hangup transitions an outbound call from
     * {@link CallState#CONNECTING} to {@link CallState#ENDED} with
     * the {@link CallEndReason#HANGUP} reason and unblocks any
     * in-flight {@code remoteAudioSource.next()}.
     */
    @Test
    public void hangupTransitionsOutboundCallToEnded() throws InterruptedException {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-1", PEER, PEER, SELF, true, CallOptions.audio());

        assertEquals(CallState.CONNECTING, call.state());
        assertTrue(call.endReason().isEmpty());

        call.hangup();

        assertEquals(CallState.ENDED, call.state());
        assertEquals(CallEndReason.HANGUP, call.endReason().orElseThrow());
        assertEquals(1, engine.terminateCount);

        // remoteAudioSource.next() must not block once the call has ended.
        assertNull(call.remoteAudioSource().next());
    }

    /**
     * A peer-driven {@code <terminate reason="hangup">} transitions
     * the call to ENDED via {@link ActiveCall#onPeerEnded} and maps
     * the wire reason back to {@link CallEndReason#HANGUP}.
     */
    @Test
    public void peerEndedMapsWireReason() throws InterruptedException {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-2", PEER, PEER, SELF, true, CallOptions.audio());

        call.onPeerEnded("hangup");

        assertEquals(CallState.ENDED, call.state());
        assertEquals(CallEndReason.HANGUP, call.endReason().orElseThrow());
        // The peer ended; we did not send a terminate stanza.
        assertEquals(0, engine.terminateCount);
        // Listeners were notified.
        assertEquals(1, engine.notifyEndedCount);
    }

    /**
     * An unknown wire reason maps to {@link CallEndReason#UNKNOWN}.
     */
    @Test
    public void unknownWireReasonMapsToUnknown() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-3", PEER, PEER, SELF, true, CallOptions.audio());

        call.onPeerEnded("something-not-in-the-enum");

        assertEquals(CallEndReason.UNKNOWN, call.endReason().orElseThrow());
    }

    /**
     * Mute toggling fires signaling stanzas only when the value
     * actually changes; redundant calls are no-ops.
     */
    @Test
    public void muteOnlyFiresOnTransition() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-4", PEER, PEER, SELF, true, CallOptions.video());

        call.mute(true, false);
        call.mute(true, false);   // no-op
        call.mute(false, false);  // unmute audio
        call.mute(false, true);   // mute video
        call.mute(false, true);   // no-op

        assertEquals(2, engine.muteCount, "mute(true) then mute(false) = 2 transitions");
        assertEquals(1, engine.videoStateCount, "video toggle = 1 transition");
    }

    /**
     * Writing into the local audio sink while muted silently drops
     * the frame; once unmuted, frames flow to the queue. Verified
     * by checking the next dequeued frame is the post-unmute one,
     * not the dropped pre-unmute one.
     */
    @Test
    public void mutedAudioSinkDropsFrames() throws InterruptedException {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-5", PEER, PEER, SELF, true, CallOptions.audio());

        var droppedFrame = new AudioFrame(new short[160], 0L);
        var deliveredFrame = new AudioFrame(new short[160], 1L);

        call.mute(true, false);
        call.localAudioSink().write(droppedFrame);
        call.mute(false, false);
        call.localAudioSink().write(deliveredFrame);

        assertSame(deliveredFrame, call.takeOutboundAudio(),
                "the muted-time write should have been dropped, leaving only the post-unmute frame");
    }

    /**
     * After {@link ActiveCall#close()}, all four media ports unblock
     * cleanly via the end-of-stream sentinel.
     */
    @Test
    public void closeUnblocksAllMediaPorts() throws InterruptedException {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-6", PEER, PEER, SELF, true, CallOptions.video());

        call.close();

        assertEquals(CallState.ENDED, call.state());
        assertNull(call.remoteAudioSource().next());
        assertNull(call.remoteVideoSource().next());
        assertNull(call.takeOutboundAudio());
        assertNull(call.takeOutboundVideo());
    }

    /**
     * {@link IncomingCall#markResponded()} succeeds exactly once;
     * every subsequent call throws. This is the gate
     * {@code WhatsAppClient.acceptCall}/{@code rejectCall} use to
     * enforce one-shot semantics.
     */
    @Test
    public void incomingCallIsOneShot() {
        var offer = new IncomingCall(
                "id-7", PEER, PEER,
                Instant.EPOCH,
                false, false, null, false);

        offer.markResponded();

        assertThrows(IllegalStateException.class, offer::markResponded);
    }

    /**
     * Hanging up an already-ended call is a no-op (does not send a
     * second terminate stanza).
     */
    @Test
    public void doubleHangupIsNoOp() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-8", PEER, PEER, SELF, true, CallOptions.audio());

        call.hangup();
        call.hangup();
        call.hangup();

        assertEquals(1, engine.terminateCount, "subsequent hangups must be no-ops");
        assertFalse(call.endReason().isEmpty());
    }

    /**
     * The M4 video-upgrade flow — request, accept, reject all map
     * to engine stanza-send calls.
     */
    @Test
    public void videoUpgradeFlowRoutesThroughEngine() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-up", PEER, PEER, SELF, true,
                CallOptions.audio());

        call.requestVideoUpgrade();
        assertEquals(1, engine.videoStateCount,
                "request: peer is asked to enable video");

        call.acceptVideoUpgrade();
        assertEquals(2, engine.videoStateCount,
                "accept: confirmation sent as video-state on");

        call.rejectVideoUpgrade();
        assertEquals(3, engine.videoStateCount,
                "reject: video-state off sent as denial");
    }

    /**
     * Synthetic {@link CallService}-shaped recipient that captures
     * the calls {@link ActiveCall} would make. Avoids constructing
     * a real {@link CallService} (which needs a {@code WhatsAppClient})
     * and lets us assert what stanzas the lifecycle would emit.
     */
    private static final class RecordingEngine extends CallService {
        int terminateCount;
        int muteCount;
        int videoStateCount;
        int notifyEndedCount;
        int unregisterCount;

        RecordingEngine() {
            super(null, null);
        }

        @Override
        void sendTerminate(Jid peer, Jid creator, String callId, CallEndReason reason) {
            terminateCount++;
        }

        @Override
        void sendMute(Jid peer, Jid creator, String callId, boolean muted) {
            muteCount++;
        }

        @Override
        void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled) {
            videoStateCount++;
        }

        @Override
        void unregister(String callId) {
            unregisterCount++;
        }

        @Override
        void notifyEnded(String callId, Jid fromJid, String reason) {
            notifyEndedCount++;
        }
    }
}
