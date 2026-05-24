package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parity tests for {@link MessageReceivingService} against WhatsApp Web's
 * {@code WAWebCommsHandleMessagingStanza.handleMessagingStanza} dispatch.
 *
 * @apiNote
 * Verifies that the orchestrator routes inbound {@code <message>} nodes to the
 * newsletter receiver when the {@code from} JID is on the {@code @newsletter}
 * server and to the chat receiver otherwise; the dedup cache keyed on
 * {@code fromJid:id} skips duplicate fanout deliveries inside the processing
 * window.
 *
 * @implNote
 * Builds synthetic inbound stanzas via {@link NodeBuilder}; the chat-dispatch
 * tests install a full sender-recipient libsignal pair via
 * {@link TestSignalSession#establishSession} so the decryption path can execute
 * end-to-end. The dedup test exercises the in-flight release in
 * {@code finally} (true concurrent dedup is covered by {@code MessageDedupTest}
 * because a PreKey ciphertext is single-use and cannot be replayed against the
 * same recipient).
 */
@DisplayName("MessageReceivingService")
class MessageReceivingServiceTest {

    private static final Jid SENDER_PRIMARY = Jid.of("12025550100:0@s.whatsapp.net");
    private static final Jid RECIPIENT_BARE = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid NEWSLETTER = Jid.of("120363402045452944@newsletter");

    /**
     * Verifies that a null store fails fast through the nested receiver's null
     * check.
     */
    @Test
    @DisplayName("constructor: null store throws NullPointerException")
    void nullStoreThrows() {
        var store = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        var decryption = decryption(store);
        assertThrows(NullPointerException.class,
                () -> new MessageReceivingService(null, decryption));
    }

    /**
     * Verifies that a non-newsletter inbound stanza routes to
     * {@link ChatMessageReceiver} and produces a {@link ChatMessageInfo}.
     */
    @Test
    @DisplayName("process: chat dispatch; non-newsletter from JID routes to ChatMessageReceiver and decrypts the payload")
    void chatDispatch() {
        var senderStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        var recipientStore = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        TestSignalSession.establishSession(senderStore, Jid.of("19254863482:0@s.whatsapp.net"), recipientStore);

        var senderEncryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var payload = senderEncryption.encryptForDevice(
                Jid.of("19254863482:0@s.whatsapp.net"),
                MessageContainerSpec.encode(MessageContainer.of("dispatched via chat")));

        var inbound = buildInbound("3EB0RCV0001", SENDER_PRIMARY, "pkmsg", payload.ciphertext());
        var service = new MessageReceivingService(recipientStore, decryption(recipientStore));

        var info = service.process(inbound);

        assertInstanceOf(ChatMessageInfo.class, info,
                "non-newsletter dispatch returns a ChatMessageInfo");
        assertEquals("3EB0RCV0001", info.key().id().orElseThrow());
    }

    /**
     * Verifies that the dedup key is released in the {@code finally} block so
     * back-to-back distinct messages from the same peer both process.
     *
     * @implNote
     * The Signal session is single-use for PreKey messages so the same PKMSG
     * ciphertext cannot be replayed in this test; true concurrent in-flight
     * dedup is exercised at the unit level by {@code MessageDedupTest}.
     */
    @Test
    @DisplayName("process: dedup key is released in the finally block so a follow-up message processes independently")
    void dedupKeyReleasedAfterProcessing() {
        var senderStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        var recipientStore = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        TestSignalSession.establishSession(senderStore, Jid.of("19254863482:0@s.whatsapp.net"), recipientStore);

        var senderEncryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));

        var firstPayload = senderEncryption.encryptForDevice(
                Jid.of("19254863482:0@s.whatsapp.net"),
                MessageContainerSpec.encode(MessageContainer.of("first")));
        var firstInbound = buildInbound("3EB0DEDUP01", SENDER_PRIMARY, "pkmsg", firstPayload.ciphertext());

        var secondPayload = senderEncryption.encryptForDevice(
                Jid.of("19254863482:0@s.whatsapp.net"),
                MessageContainerSpec.encode(MessageContainer.of("second")));
        var secondInbound = buildInbound("3EB0DEDUP02", SENDER_PRIMARY,
                secondPayload.type().protocolValue(), secondPayload.ciphertext());

        var service = new MessageReceivingService(recipientStore, decryption(recipientStore));

        var first = service.process(firstInbound);
        assertNotNull(first, "first message processes successfully");
        var second = service.process(secondInbound);
        assertNotNull(second, "second distinct message processes; dedup key from first was released");
    }

    /**
     * Verifies that a {@code null} node argument fails fast on the public
     * {@link MessageReceivingService#process(Node)} entry point.
     */
    @Test
    @DisplayName("process: null node throws NullPointerException")
    void nullNodeThrows() {
        var store = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        var service = new MessageReceivingService(store, decryption(store));
        assertThrows(NullPointerException.class, () -> service.process(null));
    }

    /**
     * Verifies that {@link MessageReceivingService#clearPendingMessages()} is a
     * safe no-op on a fresh service and remains idempotent across calls.
     */
    @Test
    @DisplayName("clearPendingMessages: safe to call on a fresh service (idempotent no-op)")
    void clearPendingMessagesIdempotent() {
        var store = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        var service = new MessageReceivingService(store, decryption(store));
        Assertions.assertDoesNotThrow(service::clearPendingMessages);
        Assertions.assertDoesNotThrow(service::clearPendingMessages);
    }

    /**
     * Verifies that a newsletter-server {@code from} JID routes to the newsletter
     * receiver and that a missing {@code <plaintext>} child resolves to a silent
     * null drop.
     *
     * @apiNote
     * The chat-decryption path would throw on the missing {@code <enc>}; a null
     * return is the fingerprint of a newsletter dispatch.
     */
    @Test
    @DisplayName("process: newsletter-server JID routes to NewsletterMessageReceiver; missing <plaintext> returns null")
    void newsletterDispatchRecognised() {
        var store = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        var service = new MessageReceivingService(store, decryption(store));

        var inbound = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0NL0001")
                .attribute("from", NEWSLETTER)
                .attribute("t", 1700000000L)
                .attribute("server_id", 1)
                .attribute("type", "text")
                .build();

        var info = service.process(inbound);
        Assertions.assertNull(info,
                "newsletter dispatch + no <plaintext> then null, not a chat-decryption throw");
    }

    /**
     * Builds a {@link MessageDecryption} bound to the supplied recipient store.
     *
     * @apiNote
     * Used by every test that constructs a {@link MessageReceivingService}; the
     * decryption service shares the same store as the receiver chain so signal
     * sessions installed on either side are visible to both.
     *
     * @param store the recipient's protocol store
     * @return the decryption service
     */
    private static MessageDecryption decryption(WhatsAppStore store) {
        return new MessageDecryption(store,
                new SignalSessionCipher(store),
                new SignalGroupCipher(store));
    }

    /**
     * Builds a synthetic inbound {@code <message>} node carrying a single
     * {@code <enc>} child of the supplied type and ciphertext.
     *
     * @apiNote
     * Used to drive the chat-dispatch and dedup tests with deterministic stanza
     * shapes; the timestamp is pinned to a single epoch second so the receiver's
     * expired-status branch never fires.
     *
     * @param id         the wire id
     * @param fromJid    the {@code from} JID
     * @param encType    the {@code <enc type=...>} attribute value
     * @param ciphertext the encrypted payload bytes
     * @return the inbound node
     */
    private static Node buildInbound(String id, Jid fromJid, String encType, byte[] ciphertext) {
        return new NodeBuilder()
                .description("message")
                .attribute("id", id)
                .attribute("from", fromJid)
                .attribute("t", 1700000000L)
                .attribute("type", "text")
                .content(new NodeBuilder()
                        .description("enc")
                        .attribute("v", "2")
                        .attribute("type", encType)
                        .content(ciphertext)
                        .build())
                .build();
    }
}
