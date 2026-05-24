package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the gating predicates on {@link TcTokenStanza}.
 *
 * @apiNote
 * Pins the constructor null guards and the two cheap negative paths: the
 * AB-prop gate ({@link ABProp#PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES})
 * off and the chat absent from the store. The positive emission path
 * needs a seeded {@code tcToken} and a non-expired
 * {@code tcTokenTimestamp} on the chat record; that case is covered by
 * the upstream send-pipeline tests.
 *
 * @implNote
 * This implementation uses an empty
 * {@link MessageFixtures#temporaryStore(Jid, Jid)} so the recipient chat
 * is absent; combined with the AB-prop off branch the two tests cover
 * both early-return paths.
 */
@DisplayName("TcTokenStanza")
class TcTokenStanzaTest {

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
        assertThrows(NullPointerException.class, () -> new TcTokenStanza(null, ab));
        assertThrows(NullPointerException.class, () -> new TcTokenStanza(store, null));
    }

    /**
     * With the AB prop disabled the build call returns {@code null}
     * before looking at the store.
     */
    @Test
    @DisplayName("build: AB-prop disabled -> returns null (early gate)")
    void abPropDisabledReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder()
                .with(ABProp.PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES, false)
                .build();
        var stanza = new TcTokenStanza(store, ab);
        assertNull(stanza.build(CHAT),
                "AB-prop off must suppress <tctoken> emission unconditionally");
    }

    /**
     * With the AB prop enabled but no chat record for the recipient the
     * build call still returns {@code null}.
     */
    @Test
    @DisplayName("build: AB-prop enabled but chat missing -> returns null")
    void chatMissingReturnsNull() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        var ab = TestABPropsService.builder()
                .with(ABProp.PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES, true)
                .build();
        var stanza = new TcTokenStanza(store, ab);
        assertNull(stanza.build(CHAT),
                "missing chat must produce no <tctoken>");
    }
}
