package com.github.auties00.cobalt.call.internal.transport;

import com.github.auties00.cobalt.call.CallFixtures;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live-oracle parity tests for {@link OfferTransportSpec}. Feeds a captured inbound {@code <offer>}
 * payload through {@link OfferTransportSpec#parse} and asserts that the relay tokens, te2 endpoints,
 * participants, and call key are all present and shaped as the capture-time wire dictated. The
 * fixtures are real WA Web transport stanzas loaded through {@code CallFixtures}; tests no-op when the
 * fixture is unavailable.
 */
@DisplayName("OfferTransportSpec live wire oracle")
class OfferTransportSpecParityTest {

    @Test
    @DisplayName("inbound 1:1 audio offer: parses uuid, pids, key/hbh_key, te2s, tokens")
    void inbound1to1OfferParses() {
        var topic = "1to1/audio-accept.callee";
        if (!CallFixtures.isAvailable(topic)) return;

        var callEvent = CallFixtures.loadCallEventWithChild(topic, "offer", "in");
        var call = CallFixtures.buildNodeFromEvent(callEvent);
        var offer = call.getChild("offer").orElseThrow();

        var spec = OfferTransportSpec.parse(offer).orElseThrow(
                () -> new AssertionError("inbound 1:1 offer must carry a <relay> payload"));

        assertNotNull(spec.relayUuid(), "relay uuid must be present");
        assertNotNull(spec.peerPid(), "peer_pid must be present");
        assertNotNull(spec.selfPid(), "self_pid must be present");

        // <key> and <hbh_key> are the call-session keys; both required.
        assertNotNull(spec.callKey(), "<key> must be present");
        assertTrue(spec.callKey().length > 0);
        assertNotNull(spec.hbhKey(), "<hbh_key> must be present");
        assertTrue(spec.hbhKey().length > 0);

        // At least one te2 endpoint: the relay election needs candidates.
        assertFalse(spec.te2Endpoints().isEmpty(),
                "at least one <te2> endpoint must be parsed; got " + spec.te2Endpoints());
        var first = spec.te2Endpoints().get(0);
        assertNotNull(first.domainName());
        assertNotNull(first.relayName());
        assertTrue(first.bytes().length > 0);

        // At least one <token> + one <auth_token>.
        assertFalse(spec.tokens().isEmpty(), "at least one <token> must be parsed");
        assertFalse(spec.authTokens().isEmpty(), "at least one <auth_token> must be parsed");

        // <rte> child carries 6 bytes on this snapshot.
        assertNotNull(spec.rte(), "<rte> must be parsed");
        assertEquals(6, spec.rte().length, "rte payload is 6 bytes on the current snapshot");
    }

    @Test
    @DisplayName("offer without <relay> returns Optional.empty (e.g. signaling-only Cobalt offer)")
    void offerWithoutRelayReturnsEmpty() {
        var offer = new NodeBuilder()
                .description("offer")
                .attribute("call-id", "CID-NO-RELAY")
                .build();
        assertTrue(OfferTransportSpec.parse(offer).isEmpty());
    }

    @Test
    @DisplayName("null offer returns Optional.empty")
    void nullOfferReturnsEmpty() {
        assertTrue(OfferTransportSpec.parse(null).isEmpty());
    }
}
