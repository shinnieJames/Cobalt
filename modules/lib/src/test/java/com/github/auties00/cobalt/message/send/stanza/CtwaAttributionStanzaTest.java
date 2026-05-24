package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the constructor null guards and the no-campaign default
 * branch on {@link CtwaAttributionStanza}.
 *
 * @apiNote
 * Pins that the builder rejects null services up front and that without a
 * recorded {@link ExternalEntryPoint} the build call collapses to
 * {@code null}; the positive emission path with a stored entry point is
 * exercised by the upstream send-pipeline tests.
 *
 * @implNote
 * This implementation uses an empty
 * {@link MessageFixtures#temporaryStore(Jid, Jid)} and a default
 * {@link TestABPropsService}; the entry-point cache on
 * {@link CtwaAttributionStanza} starts empty so the build path always
 * returns {@code null} until {@code saveEntryPoint} is invoked.
 */
@DisplayName("CtwaAttributionStanza")
class CtwaAttributionStanzaTest {

    /**
     * The local user's PN JID used to seed fixtures.
     */
    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");

    /**
     * The recipient JID for the gated build call.
     */
    private static final Jid CHAT = Jid.of("19254863482@s.whatsapp.net");

    /**
     * A null store rejects construction up front.
     */
    @Test
    @DisplayName("constructor: null store throws NullPointerException")
    void nullStoreThrows() {
        var ab = TestABPropsService.builder().build();
        assertThrows(NullPointerException.class, () -> new CtwaAttributionStanza(null, ab));
    }

    /**
     * A null AB-props service rejects construction up front.
     */
    @Test
    @DisplayName("constructor: null abPropsService throws NullPointerException")
    void nullAbPropsThrows() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        assertThrows(NullPointerException.class, () -> new CtwaAttributionStanza(store, null));
    }

    /**
     * Without a stored entry point for the chat the build call returns
     * {@code null}.
     */
    @Test
    @DisplayName("build: returns null when the chat has no CTWA campaign")
    void noCampaignReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder().build();
        var stanza = new CtwaAttributionStanza(store, ab);
        assertNull(stanza.build(CHAT),
                "chat with no campaign info must not emit <ctwa_attribution>");
    }
}
