package com.github.auties00.cobalt.message.send.stanza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the static {@code buildMetadata} overloads on
 * {@link BotStanza}.
 *
 * @apiNote
 * Pins the all-null suppression rule, the verbatim attribute propagation
 * for every recognised metadata field, and the three-argument overload's
 * defaulting of the AI mode selection attributes. The stateful
 * {@link BotStanza#build} path needs the full encryption pipeline and is
 * covered indirectly by the bot-text live oracle.
 *
 * @implNote
 * This implementation drives the metadata builder directly without
 * touching the store; the assertions read back attributes via
 * {@code Node#getAttributeAsString} so they assert both presence and
 * value at the same time.
 */
@DisplayName("BotStanza")
class BotStanzaTest {

    /**
     * All-null inputs to both overloads must yield {@code null} so the
     * outer stanza can suppress an empty {@code <bot>} child.
     */
    @Test
    @DisplayName("buildMetadata(all-null): returns null (no <bot> node when no attributes apply)")
    void allNullReturnsNull() {
        assertNull(BotStanza.buildMetadata(null, null, null, null, null),
                "no signals -> no <bot> node, not an empty one");
        assertNull(BotStanza.buildMetadata(null, null, null));
    }

    /**
     * Every recognised attribute round-trips verbatim to the
     * {@code <bot>} node attribute map.
     */
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

    /**
     * The three-argument overload omits the AI-mode-selector attributes
     * entirely rather than emitting them with empty values.
     */
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

    /**
     * Partial inputs only emit the supplied attributes; null attributes
     * remain absent in the attribute map.
     */
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
