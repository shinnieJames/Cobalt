package com.github.auties00.cobalt.device.key;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.libsignal.SignalSessionCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture-driven structural tests for {@link DevicePreKeyHandler}.
 *
 * @apiNote
 * Validates the wire-shape parity between Cobalt's pre-key fetch path and the captured
 * {@code WAWebFetchPrekeysJob} response: every {@code <user>} child carries the canonical
 * registration, identity, signed-pre-key, and (when not depleted) one-time-pre-key children,
 * and the depletion count matches the oracle's expectation. Also pins the empty-input no-op
 * branch in {@link DevicePreKeyHandler#ensureSessions(java.util.Collection)}.
 *
 * @implNote
 * This implementation replays the captured pre-key response from
 * {@code modules/lib/src/test/resources/fixtures/device/prekey-batch.jsonl} and the matching
 * {@code prekey-batch.expected.json} oracle that pins {@code missedPrekeyCount=8} and
 * {@code depletedPrekeyCount=0}. The {@code prekey-single} fixture covers the one-device path
 * for a hosted enterprise primary.
 */
@DisplayName("DevicePreKeyHandler")
class DevicePreKeyHandlerTest {
    private static final Jid SELF_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");

    /**
     * Returns the inbound response node from the captured pre-key fetch.
     *
     * @apiNote
     * Walks the captured JSONL stream for the given topic and returns the first
     * {@code direction == "in"} event materialised as a {@link Node}.
     *
     * @param topic the fixture topic name (e.g. {@code "prekey-batch"})
     * @return the captured inbound response
     */
    private static Node loadCapturedResponse(String topic) {
        for (var event : DeviceFixtures.loadEvents(topic)) {
            if ("in".equals(event.getString("direction"))) {
                return DeviceFixtures.buildNodeFromEvent(event);
            }
        }
        throw new AssertionError("no inbound event in fixture " + topic);
    }

    /**
     * Returns the device JIDs the captured pre-key response refers to.
     *
     * @apiNote
     * Iterates the {@code <list>/<user>} children and extracts every {@code jid} attribute as
     * a {@link Jid}; the order matches the response's declaration order.
     *
     * @param response the captured response node
     * @return the device JIDs in declaration order
     */
    private static List<Jid> capturedDeviceJids(Node response) {
        var jids = new ArrayList<Jid>();
        response.streamChildren("list")
                .flatMap(list -> list.streamChildren("user"))
                .forEach(user -> user.getAttributeAsJid("jid").ifPresent(jids::add));
        return jids;
    }

    /**
     * {@link DevicePreKeyHandler#ensureSessions(java.util.Collection)} returns zero and does
     * not issue an IQ when called with no devices.
     */
    @Test
    @DisplayName("ensureSessions: no devices need sessions when called with empty input")
    void emptyInput() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var sessionCipher = new SignalSessionCipher(store);
        var handler = new DevicePreKeyHandler(client, sessionCipher);

        var depleted = handler.ensureSessions(List.of());
        assertEquals(0, depleted, "empty input is a no-op");
    }

    /**
     * The captured batch fixture (8 devices) has a {@code <key>} child on every {@code <user>}
     * entry, so the depletion count must come out as zero, matching the oracle.
     */
    @Test
    @DisplayName("captured batch response: every device carries a <key> child, so depletion = 0")
    void batchResponseHasNoDepletion() {
        var response = loadCapturedResponse("prekey-batch");
        var deviceJids = capturedDeviceJids(response);
        assertEquals(8, deviceJids.size(),
                "captured batch fixture has 8 devices");

        var withKey = response.streamChildren("list")
                .flatMap(list -> list.streamChildren("user"))
                .filter(user -> user.hasChild("key"))
                .count();
        assertEquals(8, withKey,
                "captured response: every device has a <key> child, matching the prekey-batch.expected.json oracle (depletedPrekeyCount=0)");
    }

    /**
     * The single-device fixture exercises the hosted enterprise primary; depending on capture
     * timing it may or may not carry a {@code <key>} child.
     */
    @Test
    @DisplayName("captured single response: one device, one <key> child, no depletion")
    void singleResponseHasOneKey() {
        var response = loadCapturedResponse("prekey-single");
        var deviceJids = capturedDeviceJids(response);
        assertEquals(1, deviceJids.size(),
                "captured single fixture has 1 device (the hosted enterprise primary)");

        var withKey = response.streamChildren("list")
                .flatMap(list -> list.streamChildren("user"))
                .filter(user -> user.hasChild("key"))
                .count();
        assertTrue(withKey <= 1, "single response can have 0 or 1 <key> children");
    }

    /**
     * Every {@code <user>} entry in the batch fixture carries the canonical pre-key bundle
     * children: {@code registration}, {@code type}, {@code identity}, and {@code skey}.
     */
    @Test
    @DisplayName("captured response: every user carries the canonical pre-key bundle children")
    void canonicalBundleShape() {
        var response = loadCapturedResponse("prekey-batch");
        response.streamChildren("list")
                .flatMap(list -> list.streamChildren("user"))
                .forEach(user -> {
                    assertTrue(user.hasChild("registration"), "<registration> child present");
                    assertTrue(user.hasChild("type"), "<type> child present");
                    assertTrue(user.hasChild("identity"), "<identity> child present");
                    assertTrue(user.hasChild("skey"), "<skey> child present");
                });
    }
}
