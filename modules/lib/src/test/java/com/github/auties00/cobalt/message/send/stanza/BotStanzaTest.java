package com.github.auties00.cobalt.message.send.stanza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests for {@link BotStanza#buildMetadata}, the
 * stateless builders that emit the optional {@code <bot>} child carrying
 * AI bot attributes ({@code type}, {@code local_automated_type},
 * {@code client_thread_id}, {@code mode_selection}, {@code mode_selected}).
 *
 * <p>The stateful instance method {@link BotStanza#build(...)} needs the
 * full encryption + transform DI graph; it's exercised indirectly by the
 * bot-text live oracle test. This class focuses on the pure builders.
 */
@DisplayName("BotStanza")
class BotStanzaTest {

    @Test
    @DisplayName("buildMetadata(all-null): returns null (no <bot> node when no attributes apply)")
    void allNullReturnsNull() {
        assertNull(BotStanza.buildMetadata(null, null, null, null, null),
                "no signals → no <bot> node, not an empty one");
        assertNull(BotStanza.buildMetadata(null, null, null));
    }

    @Test
    @DisplayName("buildMetadata: every supplied attribute propagates verbatim to the <bot> node")
    void attributesPropagate() {
        var node = BotStanza.buildMetadata(
                "feedback", "1p_partial", "abc123", "think_hard", "default");
        assertEquals("bot", node.description());
        assertEquals("feedback", node.getAttributeAsString("type").orElseThrow());
        assertEquals("1p_partial", node.getAttributeAsString("local_automated_type").orElseThrow());
        assertEquals("abc123", node.getAttributeAsString("client_thread_id").orElseThrow());
        assertEquals("think_hard", node.getAttributeAsString("mode_selection").orElseThrow());
        assertEquals("default", node.getAttributeAsString("mode_selected").orElseThrow());
    }

    @Test
    @DisplayName("buildMetadata(3-arg overload): defaults mode selection attributes to absent")
    void threeArgOverloadOmitsModeAttrs() {
        var node = BotStanza.buildMetadata("feedback", null, null);
        assertEquals("feedback", node.getAttributeAsString("type").orElseThrow());
        assertTrue(node.getAttribute("mode_selection").isEmpty(),
                "3-arg overload must NOT emit mode_selection attribute");
        assertTrue(node.getAttribute("mode_selected").isEmpty(),
                "3-arg overload must NOT emit mode_selected attribute");
    }

    @Test
    @DisplayName("buildMetadata: partial inputs only emit the attributes that are set")
    void partialAttributes() {
        var node = BotStanza.buildMetadata(null, null, "thread-1", null, null);
        assertEquals("thread-1", node.getAttributeAsString("client_thread_id").orElseThrow());
        assertTrue(node.getAttribute("type").isEmpty());
        assertTrue(node.getAttribute("local_automated_type").isEmpty());
        assertTrue(node.getAttribute("mode_selection").isEmpty());
        assertTrue(node.getAttribute("mode_selected").isEmpty());
    }
}
