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
 * End-to-end tests for {@link IceAgent}, covering candidate gathering, pair construction,
 * connectivity-check sending, response handling, and pair nomination. The harness drives the agent
 * through a fake outbound sink that captures the STUN packets it emits and synthesises the
 * responses to feed back in. The fixture credentials use the same shared password on both sides so
 * a single MESSAGE-INTEGRITY computation verifies and stamps for either direction.
 */
public class IceAgentTest {

    private static final IceCredentials CREDS = new IceCredentials(
            "ufrag-local",
            "pass-shared".getBytes(),
            "ufrag-remote",
            "pass-shared".getBytes());

    private static IceCandidate localCandidate(int port, String foundation) {
        return IceCandidate.host(IceComponent.RTP,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), foundation);
    }

    private static IceCandidate remoteCandidate(int port, String foundation) {
        return IceCandidate.host(IceComponent.RTP,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), foundation);
    }

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

    @Test
    public void cannotStartWithoutPairs() {
        var agent = new IceAgent(true, CREDS, (packet, dest) -> {
        });
        assertThrows(IllegalStateException.class, agent::start);
    }

    // Full-candidate-set ICE (not trickle): the gathering and checking phases are kept separate,
    // so adding candidates after start() is rejected.
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
        // Destination is the remote candidate's transport address.
        assertEquals(60001, captureDest.get().getPort());

        var top = agent.checkList().get(0);
        assertSame(IceCheckState.IN_PROGRESS, top.state());
        assertNotNull(top.inFlightTxId());
    }

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

        var pair = agent.checkList().get(0);
        var response = synthesizeBindingSuccess(pair.inFlightTxId());
        agent.handleInboundStun(response);

        assertSame(IceCheckState.SUCCEEDED, pair.state());
        assertTrue(pair.nominated());
        assertNotNull(nominated.get());
        assertSame(pair, nominated.get());
    }

    @Test
    public void tamperedMacFailsThePair() {
        var captured = new ArrayList<byte[]>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet));
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();

        var pair = agent.checkList().get(0);
        var response = synthesizeBindingSuccess(pair.inFlightTxId());
        var miOffset = WaRelayMessageIntegrity.locate(response);
        response[miOffset + 4] ^= (byte) 0xFF;
        agent.handleInboundStun(response);

        assertSame(IceCheckState.FAILED, pair.state());
        assertFalse(pair.nominated());
        assertNull(agent.nominatedPair());
    }

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

        fakeClock.advance(Duration.ofSeconds(2));
        agent.tick();

        assertSame(IceCheckState.FAILED, firstPair.state());
        assertEquals(2, captured.size(), "tick should fire the next waiting pair");
    }

    @Test
    public void inboundBindingRequestEchoesSuccess() {
        var captured = new ArrayList<byte[]>();
        var agent = new IceAgent(true, CREDS, (packet, dest) -> captured.add(packet));
        agent.addLocalCandidate(localCandidate(50001, "eth0:4"));
        agent.addRemoteCandidate(remoteCandidate(60001, "wlan:4"));
        agent.start();
        captured.clear();

        // Inbound binding REQUEST is stamped with the local password: from the peer's perspective
        // its "remote" password is our local password.
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
     * A {@link Clock} adjustable from the test thread, used to fast-forward the agent's check
     * timeout.
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
