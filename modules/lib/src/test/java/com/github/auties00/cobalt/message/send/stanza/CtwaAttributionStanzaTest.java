package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural tests for {@link CtwaAttributionStanza}, the builder for the
 * optional {@code <ctwa_attribution>} child that carries the
 * click-to-WhatsApp campaign id.
 *
 * <p>Only emitted when the chat has an associated CTWA campaign and the
 * AB-prop gate is enabled; otherwise returns {@code null}.
 */
@DisplayName("CtwaAttributionStanza")
class CtwaAttributionStanzaTest {

    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid CHAT = Jid.of("19254863482@s.whatsapp.net");

    @Test
    @DisplayName("constructor: null store throws NullPointerException")
    void nullStoreThrows() {
        var ab = TestABPropsService.builder().build();
        assertThrows(NullPointerException.class, () -> new CtwaAttributionStanza(null, ab));
    }

    @Test
    @DisplayName("constructor: null abPropsService throws NullPointerException")
    void nullAbPropsThrows() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        assertThrows(NullPointerException.class, () -> new CtwaAttributionStanza(store, null));
    }

    @Test
    @DisplayName("build: returns null when the chat has no CTWA campaign")
    void noCampaignReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder().build();
        var stanza = new CtwaAttributionStanza(store, ab);
        // CHAT has no CTWA campaign info in the store, so output is null.
        assertNull(stanza.build(CHAT),
                "chat with no campaign info must not emit <ctwa_attribution>");
    }
}
