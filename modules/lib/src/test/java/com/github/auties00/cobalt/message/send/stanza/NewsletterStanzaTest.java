package com.github.auties00.cobalt.message.send.stanza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests for {@link NewsletterStanza}, mirroring the SMAX
 * publish-message payload mixin used by WA Web for newsletter sends.
 *
 * <p>The unit just wraps the serialised protobuf bytes in a
 * {@code <plaintext>} node, with an optional {@code mediatype} attribute
 * for media sends. There is no Signal envelope; newsletters are not
 * end-to-end-encrypted.
 */
@DisplayName("NewsletterStanza")
class NewsletterStanzaTest {

    @Test
    @DisplayName("buildPlaintext(bytes): emits <plaintext>payload</plaintext> with no attributes")
    void plaintextBasic() {
        var payload = new byte[]{1, 2, 3, 4};
        var node = NewsletterStanza.buildPlaintext(payload);

        assertEquals("plaintext", node.description());
        assertTrue(node.attributes().isEmpty(),
                "plaintext node with no mediaType has no attributes");
        assertArrayEquals(payload, node.toContentBytes().orElseThrow());
    }

    @Test
    @DisplayName("buildPlaintext(bytes, mediaType): mediatype attribute propagates verbatim")
    void plaintextWithMediaType() {
        var payload = new byte[]{9, 9, 9};
        var node = NewsletterStanza.buildPlaintext(payload, "image");
        assertEquals("plaintext", node.description());
        assertEquals("image", node.getAttributeAsString("mediatype").orElseThrow());
        assertArrayEquals(payload, node.toContentBytes().orElseThrow());
    }

    @Test
    @DisplayName("plaintext mediatype variants round-trip for image / video / url / audio")
    void plaintextMediaTypes() {
        for (var type : new String[]{"image", "video", "url", "audio", "document", "sticker"}) {
            var node = NewsletterStanza.buildPlaintext(new byte[]{0}, type);
            assertEquals(type, node.getAttributeAsString("mediatype").orElseThrow(),
                    "mediatype=" + type + " must propagate");
        }
    }

    @Test
    @DisplayName("empty payload: zero-byte content is still a valid plaintext node")
    void plaintextEmptyPayload() {
        var node = NewsletterStanza.buildPlaintext(new byte[0]);
        assertEquals("plaintext", node.description());
        var bytes = node.toContentBytes().orElseThrow();
        assertEquals(0, bytes.length);
    }

    @Test
    @DisplayName("no Signal envelope: plaintext node has no <enc> child")
    void noEncEnvelope() {
        var node = NewsletterStanza.buildPlaintext(new byte[]{1, 2});
        assertFalse(node.getChild("enc").isPresent(),
                "newsletter sends do not carry an <enc> child — payload travels in clear");
    }
}
