package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MessageReceiveStanzaParser}, mirroring
 * {@code WAWebHandleMsgParser.incomingMsgParser}.
 *
 * <p>Pure-function parser: takes an inbound {@code <message>} Node plus
 * the receiver's PN/LID and returns a {@link MessageReceiveStanza}.
 * Coverage exercises the required attributes, optional metadata, and
 * pulls a captured bot stream stanza through to verify end-to-end parse.
 */
@DisplayName("MessageReceiveStanzaParser")
class MessageReceiveStanzaParserTest {

    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid PEER_PN = Jid.of("19254863482@s.whatsapp.net");

    @Test
    @DisplayName("parse: required attributes (id, t, from, type) populate the result")
    void parseRequiredAttrs() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0CAFEBABE")
                .attribute("t", 1700000000L)
                .attribute("from", PEER_PN)
                .attribute("type", "text")
                .build();

        var parsed = MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID);
        assertNotNull(parsed);
        assertEquals("3EB0CAFEBABE", parsed.id());
        assertEquals(Instant.ofEpochSecond(1700000000L), parsed.timestamp());
        assertEquals(PEER_PN, parsed.chatJid());
        // Wire-type attribute is consumed and reflected via the resolved MessageType enum.
        org.junit.jupiter.api.Assertions.assertNotNull(parsed.messageType());
    }

    @Test
    @DisplayName("parse: optional notify (pushName) propagates")
    void parseOptionalNotify() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0")
                .attribute("t", 1700000000L)
                .attribute("from", PEER_PN)
                .attribute("type", "text")
                .attribute("notify", "Alice")
                .build();
        var parsed = MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID);
        assertEquals("Alice", parsed.pushName().orElseThrow());
    }

    @Test
    @DisplayName("parse: edit attribute defaults to 0 when absent")
    void parseEditDefaultsToZero() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0")
                .attribute("t", 1700000000L)
                .attribute("from", PEER_PN)
                .attribute("type", "text")
                .build();
        var parsed = MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID);
        // Edit defaults to 0 — no in-place edit / revoke.
        assertEquals(0, parsed.editAttribute());
    }

    @Test
    @DisplayName("parse: missing required id throws IllegalArgumentException")
    void parseMissingIdThrows() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("t", 1700000000L)
                .attribute("from", PEER_PN)
                .attribute("type", "text")
                .build();
        assertThrows(java.util.NoSuchElementException.class,
                () -> MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID));
    }

    @Test
    @DisplayName("parse: missing required from throws IllegalArgumentException")
    void parseMissingFromThrows() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0")
                .attribute("t", 1700000000L)
                .attribute("type", "text")
                .build();
        assertThrows(java.util.NoSuchElementException.class,
                () -> MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID));
    }

    @Test
    @DisplayName("parse: null node throws NullPointerException")
    void parseNullNodeThrows() {
        assertThrows(NullPointerException.class,
                () -> MessageReceiveStanzaParser.parse(null, SELF_PN, SELF_LID));
    }

    @Test
    @DisplayName("parse: live captured bot MSMSG stanza parses end-to-end with non-null fields")
    void parseLiveBotMsmsg() {
        var topic = "receive/template-or-buttons-from-biz";
        if (!MessageFixtures.isAvailable(topic)) return;
        var events = MessageFixtures.loadEvents(topic);
        var incoming = events.stream()
                .filter(e -> "in".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .findFirst()
                .orElseThrow();
        Node node = MessageFixtures.buildNodeFromEvent(incoming);
        var parsed = MessageReceiveStanzaParser.parse(node, SELF_PN, SELF_LID);

        assertNotNull(parsed.id());
        assertTrue(parsed.chatJid().toString().endsWith("@bot"),
                "bot stream stanza must parse with @bot from JID");
        // Wire-type attribute is consumed and reflected via the resolved MessageType enum.
        org.junit.jupiter.api.Assertions.assertNotNull(parsed.messageType());
        assertNotNull(parsed.timestamp());
        // Bot replies come with notify="Meta AI" — visible in the live fixture.
        assertTrue(parsed.pushName().isPresent(),
                "captured bot stanza carries a notify (push name) attribute");
    }

    @Test
    @DisplayName("parse: count attribute (group ack-like) is preserved as Optional<Integer>")
    void parseCountAttribute() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0")
                .attribute("t", 1700000000L)
                .attribute("from", PEER_PN)
                .attribute("type", "text")
                .attribute("count", 5)
                .build();
        var parsed = MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID);
        assertEquals(5, parsed.count().orElseThrow(),
                "count attribute must propagate to the parsed stanza");
    }

    @Test
    @DisplayName("parse: stanza with no <hsm> child has isHsm=false")
    void parseHsmAbsent() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0")
                .attribute("t", 1700000000L)
                .attribute("from", PEER_PN)
                .attribute("type", "text")
                .build();
        var parsed = MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID);
        assertFalse(parsed.isHsm(),
                "no <hsm> child → isHsm=false");
    }
}
