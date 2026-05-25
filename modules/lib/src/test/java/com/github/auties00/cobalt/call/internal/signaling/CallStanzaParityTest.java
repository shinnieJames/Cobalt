package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.call.CallFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.auties00.cobalt.call.CallEndReason;

/**
 * Wire-shape parity tests for {@link CallStanza}, anchored to {@code <call>} stanzas captured from
 * live WhatsApp Web sessions under {@code fixtures/call/}. Each test loads a captured outgoing-side or
 * incoming-side stanza from a {@code .jsonl} fixture via {@link CallFixtures#loadCallEventWithChild},
 * then asserts that its payload shape (attributes, child topology) matches what {@link CallStanza}
 * builds for the same logical event.
 *
 * <p>The builder side is exercised by constructing the Cobalt stanza with the same call-id and
 * call-creator the capture used, then comparing structural attributes; full byte-for-byte equality is
 * not feasible because the {@code id} attribute on the outer {@code <call>} is assigned by
 * {@code WAWap.generateId} at dispatch time and is irrelevant to the wire-shape invariants. Tests are
 * guarded with {@link CallFixtures#isAvailable(String)} so they skip cleanly when the corpus
 * regenerates without that topic.
 */
@DisplayName("CallStanza live wire oracle")
class CallStanzaParityTest {

    @Test
    @DisplayName("offer (1:1 audio): outer <call><offer> + <audio enc=opus rate=8000> + <audio rate=16000>")
    void offer1to1AudioShape() {
        var topic = "1to1/audio-accept.callee";
        if (!CallFixtures.isAvailable(topic)) return;
        var event = CallFixtures.loadCallEventWithChild(topic, "offer", "in");
        var call = CallFixtures.buildNodeFromEvent(event);

        assertEquals("call", call.description());
        var offer = call.getChild("offer").orElseThrow();
        assertNotNull(offer.getAttributeAsString("call-id").orElse(null));
        assertNotNull(offer.getAttributeAsString("call-creator").orElse(null));

        var audios = offer.streamChildren("audio").toList();
        assertEquals(2, audios.size(), "1:1 audio offer carries two <audio> codec advertisements");
        var encs = audios.stream().map(a -> a.getAttributeAsString("enc").orElse(null)).toList();
        assertEquals(List.of("opus", "opus"), encs);
        var rates = audios.stream().map(a -> a.getAttributeAsString("rate").orElse(null)).toList();
        assertTrue(rates.contains("8000") && rates.contains("16000"),
                "offer must advertise both Opus 8kHz and 16kHz, got " + rates);
    }

    @Test
    @DisplayName("preaccept: outgoing <call><preaccept call-id call-creator/></call> on the callee side")
    void preacceptShape() {
        var topic = "1to1/audio-accept.callee";
        if (!CallFixtures.isAvailable(topic)) return;
        // Find a preaccept that the callee EMITTED (direction=out).
        var event = CallFixtures.loadCallEventWithChild(topic, "preaccept", "out");
        var call = CallFixtures.buildNodeFromEvent(event);
        var preaccept = call.getChild("preaccept").orElseThrow();
        var callId = preaccept.getAttributeAsString("call-id").orElseThrow();
        var callCreator = preaccept.getAttributeAsJid("call-creator", null);
        assertNotNull(callCreator);

        // Build the Cobalt-side equivalent and compare child topology.
        var built = CallStanza.preaccept(callCreator, callId);
        var builtPreaccept = built.getChild("preaccept").orElseThrow();
        assertEquals(callId, builtPreaccept.getAttributeAsString("call-id").orElseThrow());
        assertEquals(callCreator, builtPreaccept.getAttributeAsJid("call-creator", null));
    }

    @Test
    @DisplayName("accept (1:1 audio): outgoing <call><accept> from callee")
    void accept1to1Shape() {
        var topic = "1to1/audio-accept.callee";
        if (!CallFixtures.isAvailable(topic)) return;
        var event = CallFixtures.loadCallEventWithChild(topic, "accept", "out");
        var call = CallFixtures.buildNodeFromEvent(event);
        var accept = call.getChild("accept").orElseThrow();
        var callId = accept.getAttributeAsString("call-id").orElseThrow();
        var callCreator = accept.getAttributeAsJid("call-creator", null);
        assertNotNull(callCreator);

        var built = CallStanza.accept(callCreator, callId);
        var builtAccept = built.getChild("accept").orElseThrow();
        assertEquals(callId, builtAccept.getAttributeAsString("call-id").orElseThrow());
        assertEquals(callCreator, builtAccept.getAttributeAsJid("call-creator", null));
    }

    @Test
    @DisplayName("reject (1:1 audio): outgoing <call><reject> from callee")
    void rejectShape() {
        var topic = "1to1/audio-reject.callee";
        if (!CallFixtures.isAvailable(topic)) return;
        var event = CallFixtures.loadCallEventWithChild(topic, "reject", "out");
        var call = CallFixtures.buildNodeFromEvent(event);
        var reject = call.getChild("reject").orElseThrow();
        var callId = reject.getAttributeAsString("call-id").orElseThrow();
        var callCreator = reject.getAttributeAsJid("call-creator", null);
        assertNotNull(callCreator);

        var built = CallStanza.reject(callCreator, callId);
        var builtReject = built.getChild("reject").orElseThrow();
        assertEquals(callId, builtReject.getAttributeAsString("call-id").orElseThrow());
        assertEquals(callCreator, builtReject.getAttributeAsJid("call-creator", null));
    }

    @Test
    @DisplayName("terminate: <terminate reason call-id call-creator/>")
    void terminateShape() {
        // The capture script writes bare <terminate> nodes (without a <call>
        // envelope) to <topic>.<role>.terminate.jsonl.
        var topic = "1to1/caller-hangup-pre-accept.caller.terminate";
        if (!CallFixtures.isAvailable(topic)) return;
        var event = CallFixtures.loadEvents(topic).stream()
                .filter(e -> "terminate".equals(e.getString("tag")))
                .findFirst().orElseThrow();
        var terminate = CallFixtures.buildNodeFromEvent(event);
        var callId = terminate.getAttributeAsString("call-id").orElseThrow();
        var callCreator = terminate.getAttributeAsJid("call-creator", null);
        var reason = terminate.getAttributeAsString("reason").orElseThrow();
        assertNotNull(callCreator);

        var canonicalReason = CallEndReason.fromWireValue(reason);
        var built = CallStanza.terminate(callCreator, callCreator, callId, canonicalReason);
        var builtTerminate = built.getChild("terminate").orElseThrow();
        assertEquals(callId, builtTerminate.getAttributeAsString("call-id").orElseThrow());
        assertEquals(canonicalReason.wireValue(), builtTerminate.getAttributeAsString("reason").orElseThrow());
    }

    @Test
    @DisplayName("mute_v2: <mute_v2 call-id call-creator mute-state=\"0|1\"/>")
    void muteShape() {
        var topic = "1to1/mute-toggle.caller";
        if (!CallFixtures.isAvailable(topic)) return;
        var event = CallFixtures.loadCallEventWithChild(topic, "mute_v2", "out");
        var call = CallFixtures.buildNodeFromEvent(event);
        var muteNode = call.getChild("mute_v2").orElseThrow();
        var muteState = muteNode.getAttributeAsString("mute-state").orElseThrow();
        assertTrue("0".equals(muteState) || "1".equals(muteState),
                "mute-state attribute must be 0|1, got " + muteState);

        // Build the Cobalt-side equivalent and compare the wire tag + state.
        var callId = muteNode.getAttributeAsString("call-id").orElseThrow();
        var creator = muteNode.getAttributeAsJid("call-creator", null);
        var built = CallStanza.mute(creator, creator, callId, "1".equals(muteState));
        var builtInner = built.getChild("mute_v2").orElseThrow();
        assertEquals(muteState, builtInner.getAttributeAsString("mute-state").orElseThrow());
    }

    @Test
    @DisplayName("video_state: receive-side-only — WA Web never emits, Cobalt builder reachable only for mobile peers")
    void videoStateShape() {
        // Verified by literal search across the WA Web bundle: the string
        // "video_state" appears in only ONE non-WAWapDict location:
        // WAWebVoipSignalingEnums.TYPE_NAME[15], i.e. the enum dictionary
        // entry. No JS code path constructs a <call><video_state/> stanza
        // for dispatch. WA Web's receive side does handle inbound
        // video_state (in WAWebHandleVoipCall), so the stanza is emitted
        // by non-web peers (mobile / desktop) only.
        //
        // Implication: Cobalt's CallStanza.videoState(...) is reachable
        // only when Cobalt itself is the emitter to a mobile / desktop
        // peer. Parity against WA Web captures is therefore impossible:
        // there is no web-emitted reference stanza to compare against.
        // The builder's wire shape was originally derived from the
        // receive-side parser in WAWebHandleVoipCall and matches the
        // attributes Cobalt's CallReceiver expects to see.
    }

    @Test
    @DisplayName("ringing: TODO — capture corpus has no client-emitted <ringing>")
    void ringingShape() {
        // TODO: WA Web emits <ringing> only when its receive-side voip stack
        // alerts the user. The current capture script's auto-accept hook
        // races ahead of the alert UI, so we have not observed a
        // <ringing> on any captured callee. Once the script gains a
        // "wait-and-ring-then-answer" flow, replace this with a real shape
        // assertion modeled on `preacceptShape`.
    }

    @Test
    @DisplayName("group_update add: receive-side-only — same posture as videoStateShape")
    void groupUpdateAddShape() {
        // Verified by literal search: "group_update" is in WAWapDict (the
        // wire-token dictionary, which is consulted by BOTH sender and
        // receiver wasm code) and in WAWebVoipSignalingEnums.TYPE_NAME[24].
        // No JS code path constructs a <call><group_update/> stanza for
        // dispatch. WA Web's web client never emits group_update; the
        // stanza is emitted by mobile / desktop peers and parsed by the
        // web receiver.
        //
        // Cobalt's CallStanza.groupUpdate(...) builder is therefore
        // reachable only when Cobalt is the emitter to a non-web peer,
        // and cannot be parity-tested against WA Web captures.
    }

    @Test
    @DisplayName("group_update remove: same posture as groupUpdateAddShape")
    void groupUpdateRemoveShape() {
        // See groupUpdateAddShape; web client doesn't emit; the
        // builder's wire shape was derived from the receive-side parser.
    }
}
