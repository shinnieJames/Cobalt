package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-shape oracle for encrypted reactions on CAG (community announcement
 * group) default subgroups, anchored to
 * {@code fixtures/message/send/cag-reaction-encrypted.jsonl}.
 *
 * <p>The CAG default-subgroup path wraps the inner {@code ReactionMessage}
 * in an {@code EncReactionMessage} envelope before signal encryption, so
 * the {@code <enc>} carries {@code decrypt-fail="hide"} (the addon hint)
 * and the outer {@code <message>} type is {@code "reaction"}. Cobalt's
 * {@link com.github.auties00.cobalt.message.addon.EncMessageFactory} is
 * the unit under test on the byte-equality side; this oracle pins the
 * wire-stanza shape.
 */
@DisplayName("CAG encrypted reaction live wire oracle")
class CagEncReactionLiveOracleTest {

    @Test
    @DisplayName("CAG reaction: <message type=\"reaction\" addressing_mode=\"lid\"> with <enc type=\"skmsg\" decrypt-fail=\"hide\">")
    void cagEncReactionShape() {
        var topic = "send/cag-reaction-encrypted";
        if (!MessageFixtures.isAvailable(topic)) return;

        var events = MessageFixtures.loadEvents(topic);
        var outgoing = events.stream()
                .filter(e -> "out".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outgoing CAG <message> in reaction fixture"));
        var message = MessageFixtures.buildNodeFromEvent(outgoing);

        assertEquals("reaction", message.getAttributeAsString("type").orElseThrow(),
                "CAG reaction must wire-type=reaction");
        assertEquals("lid", message.getAttributeAsString("addressing_mode").orElseThrow(),
                "CAG addressing must be lid");
        assertTrue(message.getAttributeAsString("to").orElseThrow().endsWith("@g.us"),
                "outer to must be @g.us");

        var enc = message.getChild("enc").orElseThrow(
                () -> new AssertionError("CAG reaction must emit a bare <enc> sibling (steady-state)"));
        assertEquals("skmsg", enc.getAttributeAsString("type").orElseThrow(),
                "<enc> uses SKMSG (group sender key)");
        assertEquals("2", enc.getAttributeAsString("v").orElseThrow());
        assertEquals("hide", enc.getAttributeAsString("decrypt-fail").orElseThrow(),
                "EncReactionMessage envelope sets decrypt-fail=hide on the <enc>");

        assertFalse(message.getChild("participants").isPresent(),
                "encrypted reaction on CAG steady-state must NOT carry <participants>");
    }
}
