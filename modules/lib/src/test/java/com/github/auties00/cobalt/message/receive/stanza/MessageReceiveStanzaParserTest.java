package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link MessageReceiveStanzaParser#parse} against synthesised
 * stanzas and one captured live bot stanza.
 *
 * @apiNote
 * Covers the contract of WA Web's {@code WAWebHandleMsgParser.incomingMsgParser}:
 * required attributes ({@code id}, {@code t}, {@code from}, {@code type}),
 * optional metadata ({@code notify}, {@code edit}, {@code count}), the
 * absent-child default for the HSM flag, and the missing-required-attribute
 * error path. Pulls one captured bot stanza through end to end to verify
 * that the parser does not regress on real wire input.
 *
 * @implNote
 * This implementation builds synthetic stanzas via {@link NodeBuilder} so
 * each invariant can be isolated. The live-fixture test is guarded by
 * {@link MessageFixtures#isAvailable} so the suite stays green when the
 * captured corpus is not present.
 */
@DisplayName("MessageReceiveStanzaParser")
class MessageReceiveStanzaParserTest {

    /**
     * The local account's phone-number JID used for self-account checks in
     * the synthesised stanzas.
     */
    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");

    /**
     * The local account's LID used for the LID branch of the self-account
     * check.
     */
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");

    /**
     * A peer's phone-number JID used as the {@code from} attribute on
     * synthesised 1:1 stanzas.
     *
     * @implNote
     * Matches the {@code business} session captured in the corpus so
     * downstream LID/PN derivations align between synthesised and captured
     * input.
     */
    private static final Jid PEER_PN = Jid.of("19254863482@s.whatsapp.net");

    /**
     * The required {@code id}, {@code t}, {@code from}, and {@code type}
     * attributes propagate verbatim onto the parsed record.
     */
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
        Assertions.assertNotNull(parsed.messageType());
    }

    /**
     * The optional {@code notify} attribute surfaces as the parsed push
     * name.
     */
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

    /**
     * The {@code edit} attribute defaults to {@code 0} when absent.
     */
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
        assertEquals(0, parsed.editAttribute());
    }

    /**
     * Missing the required {@code id} attribute triggers
     * {@link NoSuchElementException}.
     */
    @Test
    @DisplayName("parse: missing required id throws IllegalArgumentException")
    void parseMissingIdThrows() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("t", 1700000000L)
                .attribute("from", PEER_PN)
                .attribute("type", "text")
                .build();
        assertThrows(NoSuchElementException.class,
                () -> MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID));
    }

    /**
     * Missing the required {@code from} attribute triggers
     * {@link NoSuchElementException}.
     */
    @Test
    @DisplayName("parse: missing required from throws IllegalArgumentException")
    void parseMissingFromThrows() {
        var msg = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0")
                .attribute("t", 1700000000L)
                .attribute("type", "text")
                .build();
        assertThrows(NoSuchElementException.class,
                () -> MessageReceiveStanzaParser.parse(msg, SELF_PN, SELF_LID));
    }

    /**
     * Passing a {@code null} node triggers
     * {@link NullPointerException} from the explicit guard.
     */
    @Test
    @DisplayName("parse: null node throws NullPointerException")
    void parseNullNodeThrows() {
        assertThrows(NullPointerException.class,
                () -> MessageReceiveStanzaParser.parse(null, SELF_PN, SELF_LID));
    }

    /**
     * A captured Meta AI bot stanza parses end-to-end with the required
     * fields populated.
     *
     * @apiNote
     * Validates the parser against real wire input rather than only against
     * synthesised stanzas; pairs with
     * {@link BotMsmsgReceiveLiveOracleTest}.
     */
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
        var node = MessageFixtures.buildNodeFromEvent(incoming);
        var parsed = MessageReceiveStanzaParser.parse(node, SELF_PN, SELF_LID);

        assertNotNull(parsed.id());
        assertTrue(parsed.chatJid().toString().endsWith("@bot"),
                "bot stream stanza must parse with @bot from JID");
        Assertions.assertNotNull(parsed.messageType());
        assertNotNull(parsed.timestamp());
        assertTrue(parsed.pushName().isPresent(),
                "captured bot stanza carries a notify (push name) attribute");
    }

    /**
     * The optional {@code count} attribute round-trips into the parsed
     * record's {@code Optional<Integer>} accessor.
     */
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

    /**
     * Absent {@code <hsm>} child resolves to {@code isHsm() == false}.
     */
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
                "no <hsm> child means isHsm=false");
    }
}
