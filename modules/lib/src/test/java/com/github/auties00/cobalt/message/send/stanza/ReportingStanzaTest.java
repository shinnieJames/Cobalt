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
 * Structural tests for {@link ReportingStanza}, mirroring
 * {@code WAWebReportingTokenUtils.genReportingTokenBody}.
 *
 * <p>The builder emits a {@code <reporting>} child containing a
 * {@code <reporting_token v=…>} body when (a) reporting tokens are
 * enabled by AB props, (b) the message type is compatible, and (c) the
 * outgoing message has a {@code messageSecret}. Otherwise it returns
 * {@code null}.
 */
@DisplayName("ReportingStanza")
class ReportingStanzaTest {

    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid REMOTE = Jid.of("19254863482@s.whatsapp.net");
    private static final byte[] SECRET = new byte[32];

    @Test
    @DisplayName("constructor: null abPropsService throws NullPointerException")
    void nullAbPropsThrows() {
        assertThrows(NullPointerException.class, () -> new ReportingStanza(null));
    }

    @Test
    @DisplayName("returns null when message has no messageSecret")
    void nullWhenNoSecret() {
        var ab = TestABPropsService.builder().build();
        var stanza = new ReportingStanza(ab);
        var msg = new ChatMessageInfoBuilder()
                .key(new MessageKeyBuilder().id("3EB0").parentJid(REMOTE).fromMe(true).build())
                .message(MessageContainer.of("body"))
                // no messageSecret
                .build();
        assertNull(stanza.build(msg, SELF, REMOTE),
                "missing messageSecret must suppress the <reporting> emission");
    }

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
        // No id on key → builder returns null even when other inputs are valid.
        // (Build path checks id.isEmpty() and short-circuits.)
        assertNotNull(stanza); // sanity: the stanza is constructed even if the call returns null
        assertNull(stanza.build(msg, SELF, REMOTE));
    }
}
