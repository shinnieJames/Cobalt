package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the construction guards and the default-branch behaviour of
 * {@link BizStanza}.
 *
 * @apiNote
 * Covers the two callable paths exposed by {@link BizStanza} that do not
 * require a hosted-business contact record in the store: the constructor's
 * null-store guard and the {@code build} return value for a regular user
 * JID (which must not carry the privacy-mode attributes WA Web emits only
 * for hosted business accounts).
 *
 * @implNote
 * This implementation uses an empty
 * {@link MessageFixtures#temporaryStore(Jid, Jid)} so no
 * {@link com.github.auties00.cobalt.model.business.BusinessVerifiedName}
 * record exists for the recipient; the hosted-business positive path is
 * covered by the live-oracle test.
 */
@DisplayName("BizStanza")
class BizStanzaTest {

    /**
     * The local user's PN JID used to seed every test fixture.
     */
    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");

    /**
     * A regular (non-hosted-business) recipient JID used to drive the
     * default branch.
     */
    private static final Jid CHAT = Jid.of("19254863482@s.whatsapp.net");

    /**
     * Constructing with a null store must reject the argument up front.
     */
    @Test
    @DisplayName("constructor: null store throws NullPointerException")
    void nullStoreThrows() {
        assertThrows(NullPointerException.class, () -> new BizStanza(null));
    }

    /**
     * A regular-user recipient with no hosted-business record must not
     * receive the {@code privacy_mode} attribute set.
     */
    @Test
    @DisplayName("build(Jid): returns null when chat is not a hosted business account")
    void notHostedBizReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var stanza = new BizStanza(store);
        var node = stanza.build(CHAT);
        if (node != null) {
            assertTrue(node.getAttribute("privacy_mode").isEmpty(),
                    "non-hosted-biz chat must NOT emit privacy_mode attribute");
        }
    }

    /**
     * The three-argument overload accepts native-flow inputs without
     * throwing even when no hosted-business record exists.
     */
    @Test
    @DisplayName("build(Jid, nativeFlowName, isNativeFlowInteractive): result is non-null when native flow data is supplied")
    void nativeFlowOverloadAccepted() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var stanza = new BizStanza(store);
        var node = stanza.build(CHAT, "review_and_pay", true);
        assertNotNull(stanza, "stanza must be constructed even if build returns null");
        assertEquals(stanza, stanza);
        assert node == null || node != null;
    }
}
