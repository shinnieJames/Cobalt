package com.github.auties00.cobalt.call.internal.transport.ice;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java tests for {@link IceCandidate} and {@link IceCandidatePair}
 * priority computation against the worked examples from RFC 8445
 * §5.1.2.1 and §6.1.2.3.
 */
public class IceCandidateTest {

    /**
     * The reference IPv4 socket address used for fixture candidates.
     */
    private static final InetSocketAddress LOCAL_RTP =
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 50000);

    /**
     * RFC 8445 §5.1.2.1 priority formula: HOST candidate (type=126),
     * default local pref (65535), component=1 (RTP) yields a known
     * priority value.
     */
    @Test
    public void hostCandidatePriorityMatchesFormula() {
        var candidate = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        // (126 << 24) + (65535 << 8) + (256 - 1)
        var expected = (126L << 24) + (65535L << 8) + (256 - 1);
        assertEquals(expected, candidate.priority());
    }

    /**
     * RELAYED type-preference is 0 — relay candidates have the
     * lowest priority of the three captured types.
     */
    @Test
    public void relayedCandidateHasLowestTypePreference() {
        var relayed = IceCandidate.relayed(IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "wa-relay-1");
        var srflx = IceCandidate.serverReflexive(IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "stun-1");
        var host = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        assertTrue(host.priority() > srflx.priority());
        assertTrue(srflx.priority() > relayed.priority());
    }

    /**
     * RFC 8445 §6.1.2.3 pair priority formula: G is the controlling
     * agent's, D is the controlled agent's. Verify the formula
     * symbolically.
     */
    @Test
    public void pairPriorityMatchesFormulaForControlling() {
        var hostA = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        var hostB = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth1:4");
        var g = hostA.priority();
        var d = hostB.priority();
        var expected = (Math.min(g, d) << 32) + (2L * Math.max(g, d)) + (g > d ? 1 : 0);
        var pair = new IceCandidatePair(hostA, hostB, true);
        assertEquals(expected, pair.priority());
    }

    /**
     * Same pair priority is computed regardless of who's
     * controlling — only the {@code G > D ? 1 : 0} kicker flips.
     */
    @Test
    public void pairPriorityFlipsKickerWhenControlSwitches() {
        // Different local preferences so the candidates have
        // distinct priorities — only then does the {@code G > D ? 1
        // : 0} kicker ever fire.
        var higher = new IceCandidate(IceCandidateType.HOST, IceComponent.RTP,
                LOCAL_RTP, LOCAL_RTP, "eth0:4", 65535);
        var lower = new IceCandidate(IceCandidateType.HOST, IceComponent.RTP,
                LOCAL_RTP, LOCAL_RTP, "eth1:4", 1024);
        assertTrue(higher.priority() > lower.priority(),
                "fixture must use distinct priorities for this assertion to be meaningful");
        var controllingPair = new IceCandidatePair(higher, lower, true);
        var controlledPair = new IceCandidatePair(higher, lower, false);
        // The min/max parts are the same, only the +1/+0 kicker
        // differs depending on which side is G — so the priorities
        // differ by exactly 1.
        assertEquals(1L, Math.abs(controllingPair.priority() - controlledPair.priority()));
    }

    /**
     * Pairing candidates of mismatched components is rejected — RTP
     * candidates can't pair with RTCP candidates.
     */
    @Test
    public void mismatchedComponentsRejected() {
        var rtp = IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4");
        var rtcp = IceCandidate.host(IceComponent.RTCP, LOCAL_RTP, "eth0:4");
        assertThrows(IllegalArgumentException.class, () -> new IceCandidatePair(rtp, rtcp, true));
    }

    /**
     * Default state of a fresh pair is {@link IceCheckState#FROZEN};
     * {@link IceCandidatePair#transition} only succeeds from the
     * matching {@code expected} state.
     */
    @Test
    public void pairStateMachineUsesCompareAndSet() {
        var pair = new IceCandidatePair(
                IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth0:4"),
                IceCandidate.host(IceComponent.RTP, LOCAL_RTP, "eth1:4"),
                true);
        assertSame(IceCheckState.FROZEN, pair.state());
        assertTrue(pair.transition(IceCheckState.FROZEN, IceCheckState.WAITING));
        // Wrong "expected" — should not move.
        assertEquals(false, pair.transition(IceCheckState.FROZEN, IceCheckState.IN_PROGRESS));
        assertSame(IceCheckState.WAITING, pair.state());
    }

    /**
     * {@link IceCandidate#foundation()} is required and non-empty.
     */
    @Test
    public void candidateFoundationRequired() {
        assertThrows(IllegalArgumentException.class, () -> new IceCandidate(
                IceCandidateType.HOST, IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "", 65535));
    }

    /**
     * {@link IceCandidate#localPreference()} is bounded to 16 bits.
     */
    @Test
    public void candidateLocalPreferenceBounded() {
        assertThrows(IllegalArgumentException.class, () -> new IceCandidate(
                IceCandidateType.HOST, IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "f", -1));
        assertThrows(IllegalArgumentException.class, () -> new IceCandidate(
                IceCandidateType.HOST, IceComponent.RTP, LOCAL_RTP, LOCAL_RTP, "f", 0x10000));
    }
}
