package com.github.auties00.cobalt.call.signaling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.call.CallPeerState;

/**
 * Unit tests for {@link CallEndReason#fromWireValue(String)} and
 * {@link CallPeerState#fromWireValue(String)}, the typed parsers the call receiver feeds into the
 * listener so consumers never see raw wire strings. Covers round-tripping every enum constant through
 * its wire literal and the {@link CallEndReason#UNKNOWN} / {@link CallPeerState#UNKNOWN} fallback for
 * {@code null}, empty, and unrecognized literals.
 */
public class CallTypedParsersTest {

    @Test
    public void callEndReasonRoundTrips() {
        for (var reason : CallEndReason.values()) {
            assertEquals(reason, CallEndReason.fromWireValue(reason.wireValue()),
                    "round-trip failed for " + reason);
        }
    }

    @Test
    public void callEndReasonUnknownFallback() {
        assertEquals(CallEndReason.UNKNOWN, CallEndReason.fromWireValue(null));
        assertEquals(CallEndReason.UNKNOWN, CallEndReason.fromWireValue(""));
        assertEquals(CallEndReason.UNKNOWN, CallEndReason.fromWireValue("not_a_real_reason"));
    }

    @Test
    public void callPeerStateRoundTrips() {
        for (var state : CallPeerState.values()) {
            if (state == CallPeerState.UNKNOWN) {
                assertNull(state.wireValue(), "UNKNOWN should carry a null wire value");
                continue;
            }
            assertEquals(state, CallPeerState.fromWireValue(state.wireValue()),
                    "round-trip failed for " + state);
        }
    }

    @Test
    public void callPeerStateUnknownFallback() {
        assertEquals(CallPeerState.UNKNOWN, CallPeerState.fromWireValue(null));
        assertEquals(CallPeerState.UNKNOWN, CallPeerState.fromWireValue(""));
        assertEquals(CallPeerState.UNKNOWN, CallPeerState.fromWireValue("disconnected_someday"));
    }
}
