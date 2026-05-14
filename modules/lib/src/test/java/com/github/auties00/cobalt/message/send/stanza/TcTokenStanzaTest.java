package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural tests for {@link TcTokenStanza}, the builder for the
 * optional {@code <tctoken>} child (trusted-contact privacy token).
 *
 * <p>Returns {@code null} when AB-prop
 * {@link ABProp#PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES} is
 * disabled, the chat is missing from the store, or the chat carries no
 * (non-expired) token.
 */
@DisplayName("TcTokenStanza")
class TcTokenStanzaTest {

    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid CHAT = Jid.of("19254863482@s.whatsapp.net");

    @Test
    @DisplayName("constructor: null store / null abPropsService both throw NullPointerException")
    void nullArgsThrow() {
        var ab = TestABPropsService.builder().build();
        var store = MessageFixtures.temporaryStore(SELF, null);
        assertThrows(NullPointerException.class, () -> new TcTokenStanza(null, ab));
        assertThrows(NullPointerException.class, () -> new TcTokenStanza(store, null));
    }

    @Test
    @DisplayName("build: AB-prop disabled → returns null (early gate)")
    void abPropDisabledReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder()
                .with(ABProp.PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES, false)
                .build();
        var stanza = new TcTokenStanza(store, ab);
        assertNull(stanza.build(CHAT),
                "AB-prop off must suppress <tctoken> emission unconditionally");
    }

    @Test
    @DisplayName("build: AB-prop enabled but chat missing → returns null")
    void chatMissingReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder()
                .with(ABProp.PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES, true)
                .build();
        var stanza = new TcTokenStanza(store, ab);
        // No chat seeded in the store for CHAT JID.
        assertNull(stanza.build(CHAT),
                "missing chat must produce no <tctoken>");
    }
}
