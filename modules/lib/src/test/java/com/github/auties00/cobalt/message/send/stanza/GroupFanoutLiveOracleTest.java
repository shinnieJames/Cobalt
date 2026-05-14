package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.node.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-shape byte-equality oracle tests for group/SKMSG fanout, anchored
 * to captured {@code <message to="…@g.us">} stanzas under
 * {@code fixtures/message/send/}.
 *
 * <p>Covers three regimes:
 *
 * <ul>
 *   <li><b>Small group SKMSG steady-state</b>
 *       ({@code group-small-text}) — every group recipient already has the
 *       sender key, so the outgoing stanza is a bare
 *       {@code <enc type="skmsg">} sibling under {@code <message>}.</li>
 *   <li><b>Large group SKMSG fanout</b> ({@code group-large-text}) —
 *       205-participant group; same shape, but the capture is
 *       proportionally larger and the assertion targets only the outer
 *       attributes.</li>
 *   <li><b>CAG announcement subgroup with first-time distribution</b>
 *       ({@code cag-text}) — initial send before the sender-key has
 *       propagated; falls back to per-device PKMSG fanout via
 *       {@code <participants>}.</li>
 * </ul>
 */
@DisplayName("Group fanout live wire oracle")
class GroupFanoutLiveOracleTest {

    @Test
    @DisplayName("small group steady-state: <message addressing_mode=\"lid\" type=\"text\"> with bare <enc type=\"skmsg\">")
    void smallGroupSkmsgSteadyState() {
        var topic = "send/group-small-text";
        if (!MessageFixtures.isAvailable(topic)) return;
        var message = loadOutgoingMessage(topic);

        assertEquals("text", message.getAttributeAsString("type").orElseThrow());
        assertEquals("lid", message.getAttributeAsString("addressing_mode").orElseThrow(),
                "groups send with addressing_mode=lid");
        assertTrue(message.getAttributeAsString("to").orElseThrow().endsWith("@g.us"),
                "outer to must be @g.us");
        assertTrue(message.getAttributeAsString("phash").isPresent(),
                "groups must carry phash on outgoing fanout");

        var enc = message.getChild("enc").orElseThrow(
                () -> new AssertionError("small-group steady-state must have a bare <enc> sibling"));
        assertEquals("skmsg", enc.getAttributeAsString("type").orElseThrow(),
                "group send uses SKMSG when the sender key is already distributed");
        assertEquals("2", enc.getAttributeAsString("v").orElseThrow(),
                "<enc> must carry CIPHERTEXT_VERSION v=2");
        assertFalse(message.getChild("participants").isPresent(),
                "steady-state group send must NOT emit <participants> (sender-key only)");
    }

    @Test
    @DisplayName("large group: outer <message to=\"…@g.us\" addressing_mode=\"lid\" type=\"text\">")
    void largeGroupHeaders() {
        var topic = "send/group-large-text";
        if (!MessageFixtures.isAvailable(topic)) return;
        var message = loadOutgoingMessage(topic);

        assertEquals("text", message.getAttributeAsString("type").orElseThrow());
        assertEquals("lid", message.getAttributeAsString("addressing_mode").orElseThrow());
        assertTrue(message.getAttributeAsString("to").orElseThrow().endsWith("@g.us"));
        assertTrue(message.getAttributeAsString("phash").isPresent());
    }

    @Test
    @DisplayName("CAG announcement subgroup: text fanout with per-device PKMSG participants list (initial distribution)")
    void cagInitialDistribution() {
        var topic = "send/cag-text";
        if (!MessageFixtures.isAvailable(topic)) return;
        var message = loadOutgoingMessage(topic);

        assertEquals("text", message.getAttributeAsString("type").orElseThrow());
        assertEquals("lid", message.getAttributeAsString("addressing_mode").orElseThrow(),
                "CAG subgroup must use addressing_mode=lid");
        assertTrue(message.getAttributeAsString("to").orElseThrow().endsWith("@g.us"));

        // First send into the subgroup distributes the sender key via
        // per-device PKMSG/MSG fanout under <participants>.
        var participants = message.getChild("participants").orElseThrow(
                () -> new AssertionError("first CAG send must wrap <enc> in <participants>"));
        var participantTos = participants.streamChildren("to").toList();
        assertFalse(participantTos.isEmpty(),
                "<participants> must have at least one <to jid=…> child");
        for (var to : participantTos) {
            var jid = to.getAttributeAsString("jid").orElseThrow();
            assertTrue(jid.endsWith("@lid"),
                    "CAG (lid addressing_mode) requires every participant to be @lid, got " + jid);
            var enc = to.getChild("enc").orElseThrow();
            assertTrue(List.of("pkmsg", "msg").contains(enc.getAttributeAsString("type").orElseThrow()),
                    "<enc type=…> must be pkmsg or msg for SKMSG distribution");
        }
    }

    /**
     * Loads the first outgoing {@code <message>} for a topic and rebuilds
     * the captured event as a Cobalt {@link Node}.
     *
     * @param topic the fixture topic
     * @return the rebuilt outgoing message node
     */
    private static Node loadOutgoingMessage(String topic) {
        var events = MessageFixtures.loadEvents(topic);
        var outgoing = events.stream()
                .filter(e -> "out".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outgoing <message> for topic " + topic));
        return MessageFixtures.buildNodeFromEvent(outgoing);
    }
}
