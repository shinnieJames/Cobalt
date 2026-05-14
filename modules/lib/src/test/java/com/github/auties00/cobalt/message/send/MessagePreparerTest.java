package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.store.WhatsAppStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MessagePreparer}, the package-private builder that
 * converts a raw {@link MessageContainer} into a fully populated
 * {@link ChatMessageInfo} (or
 * {@link com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo}).
 *
 * <p>Lives in the same package as {@code MessagePreparer} so it can call
 * the package-private constructor and methods directly.
 *
 * <p>Coverage focuses on the invariants the rest of the send pipeline
 * relies on:
 *
 * <ul>
 *   <li>{@code key.parentJid()} = chatJid</li>
 *   <li>{@code key.fromMe()} = true</li>
 *   <li>{@code key.senderJid()} = self</li>
 *   <li>{@code ChatMessageInfo.senderJid()} = self</li>
 *   <li>{@code status} = PENDING</li>
 *   <li>{@code messageSecret} populated, 32 bytes, distinct per call</li>
 *   <li>{@code broadcast} flag set when chatJid is the broadcast server</li>
 *   <li>Failure modes: not-logged-in, newsletter-not-joined</li>
 * </ul>
 */
@DisplayName("MessagePreparer")
class MessagePreparerTest {

    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid CHAT_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid GROUP_JID = Jid.of("120363023250764418@g.us");

    @Test
    @DisplayName("prepareChat: returns a ChatMessageInfo populated with key, status, secret, and self JID")
    void prepareChatBasic() {
        var store = store();
        var preparer = new MessagePreparer(store);
        var prepared = preparer.prepareChat(CHAT_PN, MessageContainer.of("hi"));

        assertNotNull(prepared);
        assertEquals(MessageStatus.PENDING, prepared.status().orElseThrow());
        assertEquals(SELF_PN, prepared.senderJid().orElseThrow());

        var key = prepared.key();
        assertEquals(CHAT_PN, key.parentJid().orElseThrow());
        assertTrue(key.fromMe(), "outgoing prepared message must have fromMe=true");
        assertTrue(key.id().isPresent(), "key must carry a generated id");
    }

    @Test
    @DisplayName("prepareChat: messageSecret is exactly 32 bytes and stamped on the container's messageContextInfo")
    void prepareChatStampsSecret() {
        var store = store();
        var preparer = new MessagePreparer(store);
        var prepared = preparer.prepareChat(CHAT_PN, MessageContainer.of("hi"));

        var secret = prepared.messageSecret().orElseThrow();
        assertEquals(32, secret.length, "messageSecret must be 32 bytes");

        // The same secret must be stamped into the prepared container's
        // messageContextInfo so downstream stanza builders can read it.
        var ctxInfo = prepared.message().messageContextInfo().orElseThrow();
        var stampedSecret = ctxInfo.messageSecret().orElseThrow();
        assertEquals(32, stampedSecret.length);
        org.junit.jupiter.api.Assertions.assertArrayEquals(secret, stampedSecret,
                "the container's messageContextInfo must carry the same secret as the outer info");
    }

    @Test
    @DisplayName("prepareChat: each call generates a distinct id and secret")
    void prepareChatFreshIdAndSecret() {
        var store = store();
        var preparer = new MessagePreparer(store);
        var first = preparer.prepareChat(CHAT_PN, MessageContainer.of("hi"));
        var second = preparer.prepareChat(CHAT_PN, MessageContainer.of("hi"));

        org.junit.jupiter.api.Assertions.assertNotEquals(
                first.key().id().orElseThrow(),
                second.key().id().orElseThrow(),
                "id generator must produce a fresh id per call");
        org.junit.jupiter.api.Assertions.assertFalse(
                java.util.Arrays.equals(
                        first.messageSecret().orElseThrow(),
                        second.messageSecret().orElseThrow()),
                "messageSecret must be sampled fresh per call");
    }

    @Test
    @DisplayName("prepareChat: groups receive the group JID on the key")
    void prepareChatToGroup() {
        var store = store();
        var preparer = new MessagePreparer(store);
        var prepared = preparer.prepareChat(GROUP_JID, MessageContainer.of("group"));
        assertEquals(GROUP_JID, prepared.key().parentJid().orElseThrow());
    }

    @Test
    @DisplayName("prepareChat: broadcast flag is set when chatJid is the status broadcast account")
    void prepareChatBroadcastFlag() {
        var store = store();
        var preparer = new MessagePreparer(store);
        var prepared = preparer.prepareChat(Jid.statusBroadcastAccount(), MessageContainer.of("status"));
        assertTrue(prepared.broadcast(),
                "broadcast flag must be true when targeting the status broadcast account");
    }

    @Test
    @DisplayName("prepareChat: store with no JID throws IllegalStateException (not logged in)")
    void prepareChatNotLoggedIn() {
        var store = MessageFixtures.temporaryStore(SELF_PN, null);
        store.setJid(null);
        var preparer = new MessagePreparer(store);
        assertThrows(IllegalStateException.class,
                () -> preparer.prepareChat(CHAT_PN, MessageContainer.of("hi")));
    }

    @Test
    @DisplayName("prepareChat: null arguments throw NullPointerException")
    void prepareChatNullArgs() {
        var store = store();
        var preparer = new MessagePreparer(store);
        assertThrows(NullPointerException.class,
                () -> preparer.prepareChat(null, MessageContainer.of("hi")));
        assertThrows(NullPointerException.class,
                () -> preparer.prepareChat(CHAT_PN, null));
    }

    @Test
    @DisplayName("prepareNewsletter: throws IllegalArgumentException when the user has not joined the newsletter")
    void prepareNewsletterNotJoined() {
        var store = store();
        var preparer = new MessagePreparer(store);
        var newsletter = Jid.of("120363402045452944@newsletter");
        assertThrows(IllegalArgumentException.class,
                () -> preparer.prepareNewsletter(newsletter, MessageContainer.of("hi")));
    }

    @Test
    @DisplayName("constructor: null store throws NullPointerException")
    void constructorNullStore() {
        assertThrows(NullPointerException.class, () -> new MessagePreparer(null));
    }

    /**
     * Builds a temporary store pre-configured with the canonical self PN+LID
     * pair used across these tests.
     *
     * @return the configured store
     */
    private static WhatsAppStore store() {
        return MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
    }
}
