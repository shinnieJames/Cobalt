package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural tests for {@link MetaStanza}, mirroring
 * {@code WAWebSendMsgMetaNode.genMetaNode}.
 *
 * @apiNote
 * Pins the constructor null guard and the two attribute pass-through
 * paths driven by build inputs rather than chat state:
 * {@code status_setting} for status broadcasts and
 * {@code conversation_thread_id} for AI threads. The richer message-type
 * branches (polls, events, view-once, hosted business) are exercised by
 * the upstream send-pipeline tests with seeded message containers.
 *
 * @implNote
 * This implementation uses
 * {@link MessageFixtures#temporaryStore(Jid, Jid)} to obtain a usable
 * store; no contact records are seeded so the LID origin and
 * hosted-business branches are not exercised.
 */
@DisplayName("MetaStanza")
class MetaStanzaTest {

    /**
     * A null store rejects construction up front.
     */
    @Test
    @DisplayName("constructor: null store throws NullPointerException")
    void nullStoreThrows() {
        assertThrows(NullPointerException.class, () -> new MetaStanza(null));
    }

    /**
     * The {@code status_setting} argument propagates verbatim onto the
     * {@code <meta>} attribute map for status broadcasts.
     */
    @Test
    @DisplayName("buildChat: status_setting propagates to the meta attribute when supplied")
    void statusSettingPropagates() {
        var store = MessageFixtures.temporaryStore(
                Jid.of("12025550100@s.whatsapp.net"),
                Jid.of("258252122116273@lid"));
        var stanza = new MetaStanza(store);
        var node = stanza.buildChat(Jid.statusBroadcastAccount(),
                MessageContainer.of("status body"), "contacts", null);
        assertNotNull(node, "status messages with status_setting must emit <meta>");
        assertEquals("meta", node.description());
        assertEquals("contacts", node.getAttributeAsString("status_setting").orElseThrow(),
                "status_setting must propagate verbatim onto <meta status_setting=...>");
    }

    /**
     * The pre-hashed AI thread id propagates onto the
     * {@code conversation_thread_id} attribute when a message is sent
     * inside an AI thread.
     */
    @Test
    @DisplayName("buildChat: AI thread id propagates as conversation_thread_id")
    void aiThreadIdPropagates() {
        var store = MessageFixtures.temporaryStore(
                Jid.of("12025550100@s.whatsapp.net"), null);
        var stanza = new MetaStanza(store);
        var hashedThreadId = "abcdef0123456789";
        var node = stanza.buildChat(
                Jid.of("12025550200@s.whatsapp.net"),
                MessageContainer.of("hi"),
                null, hashedThreadId);
        assertNotNull(node, "messages in an AI thread must emit <meta conversation_thread_id=...>");
        assertEquals(hashedThreadId, node.getAttributeAsString("conversation_thread_id").orElseThrow());
    }
}
