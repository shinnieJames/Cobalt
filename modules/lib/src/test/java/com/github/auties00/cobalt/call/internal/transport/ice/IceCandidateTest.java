package com.github.auties00.cobalt.call.internal.transport.ice;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java tests for {@link IceCandidate} and {@link IceCandidatePair} priority computation,
 * checked against the worked examples in RFC 8445 sections 5.1.2.1 (candidate priority) and 6.1.2.3
 * (pair priority).
 */
public class IceCandidateTest {

    private static final InetSocketAddress LOCAL_RTP =
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 50000);

    @Test
    public void hostCandidatePriorityMatchesFormula() {
        var candidate = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        // RFC 8445 5.1.2.1: (type-pref 126 << 24) + (local-pref 65535 << 8) + (256 - component 1).
        var expected = (126L << 24) + (65535L << 8) + (256 - 1);
        assertEquals(expected, candidate.priority());
    }

    @Test
    public void relayedCandidateHasLowestTypePreference() {
        var relayed = IceCandidate.relayed(IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "wa-relay-1");
        var srflx = IceCandidate.serverReflexive(IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "stun-1");
        var host = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        assertTrue(host.priority() > srflx.priority());
        assertTrue(srflx.priority() > relayed.priority());
    }

    @Test
    public void pairPriorityMatchesFormulaForControlling() {
        var hostA = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        var hostB = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth1:4");
        // RFC 8445 6.1.2.3: G is the controlling agent's priority, D the controlled agent's.
        var g = hostA.priority();
        var d = hostB.priority();
        var expected = (Math.min(g, d) << 32) + (2L * Math.max(g, d)) + (g > d ? 1 : 0);
        var pair = new IceCandidatePair(hostA, hostB, true);
        assertEquals(expected, pair.priority());
    }

    @Test
    public void pairPriorityFlipsKickerWhenControlSwitches() {
        // Distinct local preferences give the candidates distinct priorities, which is the only
        // case where the "G > D ? 1 : 0" kicker fires.
        var higher = new IceCandidate(IceCandidateType.HOST, IceComponent.RTP,
                LOCAL_RTP, LOCAL_RTP, "eth0:4", 65535);
        var lower = new IceCandidate(IceCandidateType.HOST, IceComponent.RTP,
                LOCAL_RTP, LOCAL_RTP, "eth1:4", 1024);
        assertTrue(higher.priority() > lower.priority(),
                "fixture must use distinct priorities for this assertion to be meaningful");
        var controllingPair = new IceCandidatePair(higher, lower, true);
        var controlledPair = new IceCandidatePair(higher, lower, false);
        // Min/max terms are identical; only the +1/+0 kicker depends on which side is G, so the two
        // priorities differ by exactly 1.
        assertEquals(1L, Math.abs(controllingPair.priority() - controlledPair.priority()));
    }

    @Test
    public void mismatchedComponentsRejected() {
        var rtp = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        var rtcp = IceCandidate.host(IceComponent.RTCP, LOCAL_RTP, "eth0:4");
        assertThrows(IllegalArgumentException.class, () -> new IceCandidatePair(rtp, rtcp, true));
    }

    @Test
    public void pairStateMachineUsesCompareAndSet() {
        var pair = new IceCandidatePair(
                IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4"),
                IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth1:4"),
                true);
        assertSame(IceCheckState.FROZEN, pair.state());
        assertTrue(pair.transition(IceCheckState.FROZEN, IceCheckState.WAITING));
        // Wrong expected state: the compare-and-set must reject the transition.
        assertEquals(false, pair.transition(IceCheckState.FROZEN, IceCheckState.IN_PROGRESS));
        assertSame(IceCheckState.WAITING, pair.state());
    }

    @Test
    public void candidateFoundationRequired() {
        assertThrows(IllegalArgumentException.class, () -> new IceCandidate(
                IceCandidateType.HOST, IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "", 65535));
    }

    @Test
    public void candidateLocalPreferenceBounded() {
        assertThrows(IllegalArgumentException.class, () -> new IceCandidate(
                IceCandidateType.HOST, IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "f", -1));
        assertThrows(IllegalArgumentException.class, () -> new IceCandidate(
                IceCandidateType.HOST, IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "f", 0x10000));
    }
}
