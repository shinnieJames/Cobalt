package com.github.auties00.cobalt.call.internal.transport.ice;

import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayAttribute;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayAttributeType;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayMessageIntegrity;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayMessageType;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayPacket;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for {@link IceAgent} — drives candidate gathering,
 * pair construction, connectivity-check sending, response handling,
 * and pair nomination using a fake outbound sink that captures the
 * STUN packets the agent sends and synthesises responses.
 */
public class IceAgentTest {

    /**
     * Fixture credentials — same key on both sides so the same
     * MESSAGE-INTEGRITY computation works for both verify and stamp.
     */
    private static final IceCredentials CREDS = new IceCredentials(
            "ufrag-local",
            "pass-shared".getBytes(),
            "ufrag-remote",
            "pass-shared".getBytes());

    /**
     * Fixture local candidate.
     */
    private static IceCandidate localCandidate(int port, String foundation) {
        return IceCandidate.host(IceComponent.RTP,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), foundation);
    }

    /**
     * Fixture remote candidate.
     */
    private static IceCandidate remoteCandidate(int port, String foundation) {
        return IceCandidate.host(IceComponent.RTP,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), foundation);
    }

    /**
     * The agent forms candidate pairs sorted in descending priority
     * order — and refuses to start with no pairs.
     */
    @Test
    public void buildsCheckListInDescendingPriorityOrder() {
        var captured = new ArrayList<byte[]>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet));
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addLocalCandidate(localCandidate(50002, "eth1:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.addRemoteCandidate(remoteCandidate(60002, "lte:4"));
        agent.start();

        var pairs = agent.checkList();
        assertEquals(4, pairs.size(), "cartesian product of 2x2 candidate sets");
        for (var i = 1; i < pairs.size(); i++) {
            assertTrue(pairs.get(i - 1).priority() >= pairs.get(i).priority(),
                    "pairs must be sorted descending by priority");
        }
    }

    /**
     * Cannot start without candidates on at least one side.
     */
    @Test
    public void cannotStartWithoutPairs() {
        var agent = new IceAgent(true, CREDS, (packet, dest) -> {
        });
        assertThrows(IllegalStateException.class, agent::start);
    }

    /**
     * Adding candidates after start() is rejected — gathering and
     * checking phases are kept separate (full-candidate-set ICE,
     * not trickle).
     */
    @Test
    public void cannotAddCandidatesAfterStart() {
        var agent = new IceAgent(true, CREDS, (packet, dest) -> {
        });
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();
        assertThrows(IllegalStateException.class,
                () -> agent.addLocalCandidate(localCandidate(50002, "eth1:4")));
        assertThrows(IllegalStateException.class,
                () -> agent.addRemoteCandidate(remoteCandidate(60002, "lte:4")));
    }

    /**
     * After {@link IceAgent#start()} the highest-priority pair has
     * been transitioned to {@link IceCheckState#IN_PROGRESS} and a
     * STUN binding request has been emitted to the outbound sink.
     */
    @Test
    public void startEmitsBindingRequestForTopPair() {
        var captured = new ArrayList<byte[]>();
        var captureDest = new AtomicReference<InetSocketAddress>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> {
            captured.add(packet);
            captureDest.set(dest);
        });
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();

        assertEquals(1, captured.size(), "exactly one binding request fired");
        var packet = WaRelayPacket.decode(captured.get(0));
        assertEquals(WaRelayMessageType.BINDING_REQUEST.wireValue(), packet.messageType());
        // The destination is the remote candidate's transport address.
        assertEquals(60001, captureDest.get().getPort());

        var top = agent.checkList().get(0);
        assertSame(IceCheckState.IN_PROGRESS, top.state());
        assertNotNull(top.inFlightTxId());
    }

    /**
     * Inbound binding success response — matched on transaction id —
     * transitions the in-flight pair to
     * {@link IceCheckState#SUCCEEDED} and (controlling agent)
     * nominates it.
     */
    @Test
    public void successResponseNominatesPair() {
        var captured = new ArrayList<byte[]>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet));
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        var nominated = new AtomicReference<IceCandidatePair>();
        agent.setListener(new IceAgent.Listener() {
            @Override
            public void onNominated(IceCandidatePair pair) {
                nominated.set(pair);
            }
        });
        agent.start();

        // Synthesise a binding success response for the in-flight tx.
        var pair = agent.checkList().get(0);
        var response = synthesizeBindingSuccess(pair.inFlightTxId());
        agent.handleInboundStun(response);

        assertSame(IceCheckState.SUCCEEDED, pair.state());
        assertTrue(pair.nominated());
        assertNotNull(nominated.get());
        assertSame(pair, nominated.get());
    }

    /**
     * A success response with a tampered MESSAGE-INTEGRITY is
     * rejected — pair fails.
     */
    @Test
    public void tamperedMacFailsThePair() {
        var captured = new ArrayList<byte[]>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet));
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();

        var pair = agent.checkList().get(0);
        var response = synthesizeBindingSuccess(pair.inFlightTxId());
        // Flip a bit in the MESSAGE-INTEGRITY MAC.
        var miOffset = WaRelayMessageIntegrity.locate(response);
        response[miOffset + 4] ^= (byte) 0xFF;
        agent.handleInboundStun(response);

        assertSame(IceCheckState.FAILED, pair.state());
        assertFalse(pair.nominated());
        assertNull(agent.nominatedPair());
    }

    /**
     * Inbound responses for an unknown transaction id are dropped
     * silently (don't poison the agent state).
     */
    @Test
    public void unknownTxIdIsDropped() {
        var captured = new ArrayList<byte[]>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet));
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();

        var bogus = synthesizeBindingSuccess(new byte[12]);
        agent.handleInboundStun(bogus);

        var pair = agent.checkList().get(0);
        assertSame(IceCheckState.IN_PROGRESS, pair.state());
    }

    /**
     * {@link IceAgent#tick()} fails any pair whose in-flight check
     * has exceeded the timeout, and triggers the next waiting pair.
     */
    @Test
    public void tickTimesOutInFlightPairs() {
        var captured = new ArrayList<byte[]>();
        var fakeClock = new MutableClock(Instant.parse("2026-05-06T00:00:00Z"));
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet),
                fakeClock, new SecureRandom());
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addLocalCandidate(localCandidate(50002, "eth1:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();

        var firstPair = agent.checkList().get(0);
        assertSame(IceCheckState.IN_PROGRESS, firstPair.state());

        // Advance clock past the timeout.
        fakeClock.advance(Duration.ofSeconds(2));
        agent.tick();

        assertSame(IceCheckState.FAILED, firstPair.state());
        // The next-priority pair should have been triggered.
        assertEquals(2, captured.size(), "tick should fire the next waiting pair");
    }

    /**
     * Inbound binding requests with a valid MESSAGE-INTEGRITY get a
     * BINDING_SUCCESS response stamped back. Tampered requests are
     * silently dropped (no response).
     */
    @Test
    public void inboundBindingRequestEchoesSuccess() {
        var captured = new ArrayList<byte[]>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet));
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();
        captured.clear();

        // Build an inbound binding REQUEST stamped with the local
        // password (peer's "remote" password is our local password).
        var txId = new byte[12];
        new SecureRandom().nextBytes(txId);
        var attrs = new ArrayList<WaRelayAttribute>();
        attrs.add(new WaRelayAttribute(0x0006, "ufrag-local:ufrag-remote".getBytes()));
        attrs.add(new WaRelayAttribute(WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue(),
                new byte[WaRelayMessageIntegrity.MAC_LENGTH]));
        var request = new WaRelayPacket(
                WaRelayMessageType.BINDING_REQUEST.wireValue(), txId, attrs).encode();
        WaRelayMessageIntegrity.stamp(request, CREDS.localPassword());

        agent.handleInboundStun(request);

        assertEquals(1, captured.size(), "valid inbound request must be answered");
        var response = WaRelayPacket.decode(captured.get(0));
        assertEquals(WaRelayMessageType.BINDING_SUCCESS.wireValue(), response.messageType());
    }

    /**
     * Synthesises a {@link WaRelayMessageType#BINDING_SUCCESS} response
     * for the given transaction id, stamped with MESSAGE-INTEGRITY
     * keyed on the shared fixture password.
     *
     * @param txId the transaction id of the request being answered
     * @return the encoded response bytes
     */
    private static byte[] synthesizeBindingSuccess(byte[] txId) {
        var attrs = List.of(new WaRelayAttribute(
                WaRelayAttributeType.MESSAGE_INTEGRITY.wireValue(),
                new byte[WaRelayMessageIntegrity.MAC_LENGTH]));
        var packet = new WaRelayPacket(
                WaRelayMessageType.BINDING_SUCCESS.wireValue(), txId, attrs).encode();
        WaRelayMessageIntegrity.stamp(packet, CREDS.remotePassword());
        return packet;
    }

    /**
     * A {@link Clock} that's adjustable from the test thread — used
     * to fast-forward the agent's check timeout.
     */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration d) {
            this.now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
