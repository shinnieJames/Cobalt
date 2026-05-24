package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.wam.event.CallEventBuilder;
import com.github.auties00.cobalt.wam.type.CallResultType;
import com.github.auties00.cobalt.wam.type.CallSide;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.auties00.cobalt.call.internal.CallService;

/**
 * Builder-level tests for the WAM Call event (id 462). Doesn't run a
 * full {@link CallService} (requires a live {@code WhatsAppClient} +
 * {@code WamService}); instead asserts the builder pattern is wired
 * up and round-trips every populated field through the
 * {@code Optional} accessors on the generated impl.
 */
public class CallEventTest {

    /**
     * The full populated set of fields the engine writes when a
     * 1:1 outgoing audio call ends in HANGUP round-trips through
     * the builder + interface accessors.
     */
    @Test
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

    /**
     * Inbound video call accepted then ended by peer hangup: side
     * becomes CALLEE, video flags propagate.
     */
    @Test
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

    /**
     * A timed-out call surfaces as {@link CallResultType#MISSED}.
     */
    @Test
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
