package com.github.auties00.cobalt.call.internal.signaling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.github.auties00.cobalt.call.CallEndReason;

/**
 * Unit tests for {@link CallEndReason#fromWireValue(String)} and
 * {@link CallPeerState#fromWireValue(String)} — the typed parsers
 * the call receiver feeds into the listener so consumers never see
 * raw wire strings.
 */
public class CallTypedParsersTest {

    /**
     * Every {@link CallEndReason} round-trips through its wire
     * literal via {@link CallEndReason#fromWireValue}.
     */
    @Test
    public void callEndReasonRoundTrips() {
        for (var reason : CallEndReason.values()) {
            assertEquals(reason, CallEndReason.fromWireValue(reason.wireValue()),
                    "round-trip failed for " + reason);
        }
    }

    /**
     * Unknown wire reasons map to {@link CallEndReason#UNKNOWN};
     * {@code null} also yields {@code UNKNOWN}.
     */
    @Test
    public void callEndReasonUnknownFallback() {
        assertEquals(CallEndReason.UNKNOWN, CallEndReason.fromWireValue(null));
        assertEquals(CallEndReason.UNKNOWN, CallEndReason.fromWireValue(""));
        assertEquals(CallEndReason.UNKNOWN, CallEndReason.fromWireValue("not_a_real_reason"));
    }

    /**
     * Every {@link CallPeerState} except {@link CallPeerState#UNKNOWN}
     * has a non-null wire literal that round-trips back to the same
     * enum.
     */
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

    /**
     * Unknown wire states map to {@link CallPeerState#UNKNOWN};
     * {@code null} also yields {@code UNKNOWN}.
     */
    @Test
    public void callPeerStateUnknownFallback() {
        assertEquals(CallPeerState.UNKNOWN, CallPeerState.fromWireValue(null));
        assertEquals(CallPeerState.UNKNOWN, CallPeerState.fromWireValue(""));
        assertEquals(CallPeerState.UNKNOWN, CallPeerState.fromWireValue("disconnected_someday"));
    }
}
