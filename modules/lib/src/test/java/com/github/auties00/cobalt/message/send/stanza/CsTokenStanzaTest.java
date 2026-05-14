package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural tests for {@link CsTokenStanza}, the builder for the
 * optional consent-token child added to outgoing stanzas to demonstrate
 * the user's consent to share state with the recipient.
 *
 * <p>Returns {@code null} when the AB-prop gate is disabled or the chat
 * carries no consent state; otherwise emits a node carrying the consent
 * token payload.
 */
@DisplayName("CsTokenStanza")
class CsTokenStanzaTest {

    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid CHAT = Jid.of("19254863482@s.whatsapp.net");

    @Test
    @DisplayName("constructor: null store / null abPropsService both throw NullPointerException")
    void nullArgsThrow() {
        var ab = TestABPropsService.builder().build();
        var store = MessageFixtures.temporaryStore(SELF, null);
        assertThrows(NullPointerException.class, () -> new CsTokenStanza(null, ab));
        assertThrows(NullPointerException.class, () -> new CsTokenStanza(store, null));
    }

    @Test
    @DisplayName("build: chat with no consent state → returns null")
    void noConsentStateReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder().build();
        var stanza = new CsTokenStanza(store, ab);
        assertNull(stanza.build(CHAT),
                "chat with no consent state must not emit <cstoken>");
    }
}
