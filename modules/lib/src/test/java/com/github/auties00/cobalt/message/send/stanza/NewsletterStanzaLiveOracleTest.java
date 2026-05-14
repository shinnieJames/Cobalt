package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-shape oracle for newsletter plaintext (SMAX) sends, anchored to
 * {@code fixtures/message/send/newsletter-text.jsonl}.
 *
 * <p>Newsletter messages have a distinct wire form: a single
 * {@code <plaintext>} payload child under
 * {@code <message to="…@newsletter">}, with no Signal envelope and no
 * {@code <participants>} list.
 */
@DisplayName("Newsletter stanza live wire oracle")
class NewsletterStanzaLiveOracleTest {

    @Test
    @DisplayName("newsletter text send: <message to=\"…@newsletter\" type=\"text\"> with <plaintext> body")
    void newsletterTextPlaintext() {
        var topic = "send/newsletter-text";
        if (!MessageFixtures.isAvailable(topic)) return;

        var events = MessageFixtures.loadEvents(topic);
        var outgoing = events.stream()
                .filter(e -> "out".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outgoing newsletter <message> in fixture"));
        var message = MessageFixtures.buildNodeFromEvent(outgoing);

        assertEquals("text", message.getAttributeAsString("type").orElseThrow(),
                "newsletter text send must carry type=text");
        var to = message.getAttributeAsString("to").orElseThrow();
        assertTrue(to.endsWith("@newsletter"),
                "outer to must be @newsletter, got " + to);

        var plaintext = message.getChild("plaintext").orElseThrow(
                () -> new AssertionError("newsletter send must include <plaintext> child"));
        assertTrue(plaintext.hasContent(),
                "<plaintext> must carry payload bytes (the SMAX message body)");
        assertTrue(plaintext.toContentBytes().orElseThrow().length > 0,
                "<plaintext> payload must be non-empty");

        // The SMAX path uses NO Signal envelope.
        assertFalse(message.getChild("participants").isPresent(),
                "newsletter sends must not carry <participants>");
        assertFalse(message.getChild("enc").isPresent(),
                "newsletter sends must not carry <enc>");
    }
}
