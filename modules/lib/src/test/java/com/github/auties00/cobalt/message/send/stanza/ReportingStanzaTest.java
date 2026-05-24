package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the gating predicates on {@link ReportingStanza}.
 *
 * @apiNote
 * Pins the constructor null guard and the two cheap suppression paths:
 * a message without a {@code messageSecret} and a message whose key has
 * no id. The positive HMAC-derivation path needs a populated sender
 * reporting-token version AB prop and is covered by the upstream
 * reporting-token integration tests.
 *
 * @implNote
 * This implementation uses a default {@link TestABPropsService} where the
 * sender reporting-token version is zero, so even with a populated
 * message secret the gate at the top of
 * {@link ReportingStanza#build(com.github.auties00.cobalt.model.chat.ChatMessageInfo, Jid, Jid)}
 * short-circuits.
 */
@DisplayName("ReportingStanza")
class ReportingStanzaTest {

    /**
     * The local user's PN JID used to seed fixtures.
     */
    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");

    /**
     * The remote recipient JID for the gated build call.
     */
    private static final Jid REMOTE = Jid.of("19254863482@s.whatsapp.net");

    /**
     * Fixture message secret bytes used wherever the actual key value
     * does not matter.
     */
    private static final byte[] SECRET = new byte[32];

    /**
     * A null AB-props service rejects construction up front.
     */
    @Test
    @DisplayName("constructor: null abPropsService throws NullPointerException")
    void nullAbPropsThrows() {
        assertThrows(NullPointerException.class, () -> new ReportingStanza(null));
    }

    /**
     * A message without a {@code messageSecret} produces no
     * {@code <reporting>} child.
     */
    @Test
    @DisplayName("returns null when message has no messageSecret")
    void nullWhenNoSecret() {
        var ab = TestABPropsService.builder().build();
        var stanza = new ReportingStanza(ab);
        var msg = new ChatMessageInfoBuilder()
                .key(new MessageKeyBuilder().id("3EB0").parentJid(REMOTE).fromMe(true).build())
                .message(MessageContainer.of("body"))
                .build();
        assertNull(stanza.build(msg, SELF, REMOTE),
                "missing messageSecret must suppress the <reporting> emission");
    }

    /**
     * A message whose key has no id produces no {@code <reporting>}
     * child, even when a secret is present.
     */
    @Test
    @DisplayName("returns null when message has no id")
    void nullWhenNoId() {
        var ab = TestABPropsService.builder().build();
        var stanza = new ReportingStanza(ab);
        var msg = new ChatMessageInfoBuilder()
                .key(new MessageKeyBuilder().parentJid(REMOTE).fromMe(true).build())
                .message(MessageContainer.of("body"))
                .messageSecret(SECRET)
                .build();
        assertNotNull(stanza);
        assertNull(stanza.build(msg, SELF, REMOTE));
    }
}
