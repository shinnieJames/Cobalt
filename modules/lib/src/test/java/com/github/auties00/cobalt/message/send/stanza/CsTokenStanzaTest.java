package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the gating predicates on {@link CsTokenStanza}.
 *
 * @apiNote
 * Pins the constructor's null guards and the empty-store default-disabled
 * branch: with no NCT salt and the AB prop off, {@link CsTokenStanza#build}
 * must not emit a {@code <cstoken>} child. The positive HMAC-derivation
 * path needs a seeded salt and an account LID and is covered by the
 * upstream send-pipeline tests.
 *
 * @implNote
 * This implementation uses an empty
 * {@link MessageFixtures#temporaryStore(Jid, Jid)} so the store carries
 * neither a salt nor a chat record for the recipient; both negative paths
 * collapse the build to {@code null}.
 */
@DisplayName("CsTokenStanza")
class CsTokenStanzaTest {

    /**
     * The local user's PN JID used to seed fixtures.
     */
    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");

    /**
     * The recipient JID for the gated build call.
     */
    private static final Jid CHAT = Jid.of("19254863482@s.whatsapp.net");

    /**
     * Both constructor arguments are required; null values throw up
     * front.
     */
    @Test
    @DisplayName("constructor: null store / null abPropsService both throw NullPointerException")
    void nullArgsThrow() {
        var ab = TestABPropsService.builder().build();
        var store = MessageFixtures.temporaryStore(SELF, null);
        assertThrows(NullPointerException.class, () -> new CsTokenStanza(null, ab));
        assertThrows(NullPointerException.class, () -> new CsTokenStanza(store, null));
    }

    /**
     * A chat with no NCT salt and no recipient {@code accountLid} must
     * not produce a {@code <cstoken>} child.
     */
    @Test
    @DisplayName("build: chat with no consent state -> returns null")
    void noConsentStateReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder().build();
        var stanza = new CsTokenStanza(store, ab);
        assertNull(stanza.build(CHAT),
                "chat with no consent state must not emit <cstoken>");
    }
}
