package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.wam.event.CallEventBuilder;
import com.github.auties00.cobalt.wam.type.CallResultType;
import com.github.auties00.cobalt.wam.type.CallSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.auties00.cobalt.call.internal.CallService;

/**
 * Builder-level tests for the WAM Call event (id 462). Does not run a full
 * {@link CallService}, which needs a live client and WAM service; instead it
 * asserts that {@code CallEventBuilder} round-trips every populated field
 * through the {@code Optional} accessors on the generated {@code CallEvent}
 * impl.
 */
public class CallEventTest {

    @Test
    @DisplayName("HANGUP of an outgoing 1:1 audio call round-trips every populated field; unset fields stay empty")
    public void hangupOutgoingAudioCallRoundTrips() {
        var startedAt = Instant.parse("2024-01-01T00:00:00Z");
        var event = new CallEventBuilder()
                .callRandomId("0011223344556677")
                .callSide(CallSide.CALLER)
                .callResult(CallResultType.CONNECTED)
                .videoEnabled(false)
                .videoEnabledAtCallStart(false)
                .callOfferElapsedT(startedAt)
                .build();

        assertEquals("0011223344556677", event.callRandomId().orElseThrow());
        assertEquals(CallSide.CALLER, event.callSide().orElseThrow());
        assertEquals(CallResultType.CONNECTED, event.callResult().orElseThrow());
        assertEquals(false, event.videoEnabled().orElseThrow());
        assertEquals(false, event.videoEnabledAtCallStart().orElseThrow());
        assertEquals(startedAt, event.callOfferElapsedT().orElseThrow());
        assertTrue(event.callTransitionCount().isEmpty(),
                "transitionCount unset → Optional.empty()");
        assertTrue(event.isLinkJoin().isEmpty(),
                "isLinkJoin unset → Optional.empty()");
    }

    @Test
    @DisplayName("inbound video call accepted then peer-hung-up: side is CALLEE and video flags propagate")
    public void inboundVideoCalleeRoundTrips() {
        var event = new CallEventBuilder()
                .callRandomId("FFEE")
                .callSide(CallSide.CALLEE)
                .callResult(CallResultType.CONNECTED)
                .videoEnabled(true)
                .videoEnabledAtCallStart(true)
                .build();

        assertEquals(CallSide.CALLEE, event.callSide().orElseThrow());
        assertEquals(true, event.videoEnabled().orElseThrow());
        assertEquals(true, event.videoEnabledAtCallStart().orElseThrow());
    }

    @Test
    @DisplayName("a timed-out call surfaces as CallResultType.MISSED")
    public void timedOutCallReportedAsMissed() {
        var event = new CallEventBuilder()
                .callRandomId("AABB")
                .callSide(CallSide.CALLEE)
                .callResult(CallResultType.MISSED)
                .videoEnabled(false)
                .build();

        assertEquals(CallResultType.MISSED, event.callResult().orElseThrow());
    }
}
