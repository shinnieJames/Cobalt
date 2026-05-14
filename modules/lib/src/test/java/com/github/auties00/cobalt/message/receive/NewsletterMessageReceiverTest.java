package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NewsletterMessageReceiver}, mirroring
 * {@code WAWebHandleNewsletterMsg.default}.
 *
 * <p>Pure-function-ish parser: extracts {@code id}, {@code t},
 * {@code server_id}, {@code is_sender} from the wire, reads the protobuf
 * bytes from the {@code <plaintext>} child, decodes into a
 * {@link MessageContainer}, and assembles a
 * {@link com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo}
 * with status {@code DELIVERED}.
 */
@DisplayName("NewsletterMessageReceiver")
class NewsletterMessageReceiverTest {

    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid NEWSLETTER = Jid.of("120363402045452944@newsletter");

    @Test
    @DisplayName("receive: extracts id, t, server_id, status=DELIVERED and decodes the <plaintext> payload")
    void receivePlaintext() {
        var receiver = new NewsletterMessageReceiver(MessageFixtures.temporaryStore(SELF, null));
        var payload = MessageContainerSpec.encode(MessageContainer.of("newsletter body"));

        var inbound = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0NL0001")
                .attribute("from", NEWSLETTER)
                .attribute("t", 1700000000L)
                .attribute("server_id", 42)
                .attribute("type", "text")
                .content(new NodeBuilder()
                        .description("plaintext")
                        .content(payload)
                        .build())
                .build();

        var info = receiver.receive(inbound, NEWSLETTER);

        assertNotNull(info, "valid <plaintext> input must yield a non-null NewsletterMessageInfo");
        assertEquals("3EB0NL0001", info.key().id().orElseThrow());
        assertEquals(NEWSLETTER, info.key().parentJid().orElseThrow());
        assertEquals(Instant.ofEpochSecond(1700000000L), info.timestamp().orElseThrow());
        assertEquals(42, info.serverId());
        assertEquals(MessageStatus.DELIVERED, info.status().orElseThrow(),
                "newsletter receive always produces DELIVERED status (no E2E ack contract)");

        var content = info.message().content();
        var text = assertInstanceOf(ExtendedTextMessage.class, content,
                "the conversation field decodes to an ExtendedTextMessage on receive");
        assertEquals("newsletter body", text.text().orElseThrow());

        // fromMe defaults to false when is_sender attr is absent.
        assertFalse(info.key().fromMe(),
                "absent is_sender attribute → fromMe=false");
    }

    @Test
    @DisplayName("receive: is_sender=\"true\" sets the resulting key.fromMe=true")
    void isSenderTrue() {
        var receiver = new NewsletterMessageReceiver(MessageFixtures.temporaryStore(SELF, null));
        var payload = MessageContainerSpec.encode(MessageContainer.of("self post"));

        var inbound = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0SELF001")
                .attribute("from", NEWSLETTER)
                .attribute("t", 1700000000L)
                .attribute("server_id", 100)
                .attribute("type", "text")
                .attribute("is_sender", "true")
                .content(new NodeBuilder()
                        .description("plaintext")
                        .content(payload)
                        .build())
                .build();

        var info = receiver.receive(inbound, NEWSLETTER);
        assertTrue(info.key().fromMe(),
                "is_sender=\"true\" must propagate to key.fromMe=true");
    }

    @Test
    @DisplayName("receive: missing <plaintext> child returns null (no-op)")
    void missingPlaintextReturnsNull() {
        var receiver = new NewsletterMessageReceiver(MessageFixtures.temporaryStore(SELF, null));
        var inbound = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0NL0002")
                .attribute("from", NEWSLETTER)
                .attribute("t", 1700000000L)
                .attribute("server_id", 1)
                .attribute("type", "text")
                .build();

        assertNull(receiver.receive(inbound, NEWSLETTER),
                "no <plaintext> child → null (the orchestrator treats this as an unavailable message)");
    }

    @Test
    @DisplayName("receive: empty <plaintext> payload returns null")
    void emptyPlaintextReturnsNull() {
        var receiver = new NewsletterMessageReceiver(MessageFixtures.temporaryStore(SELF, null));
        var inbound = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0NL0003")
                .attribute("from", NEWSLETTER)
                .attribute("t", 1700000000L)
                .attribute("server_id", 1)
                .attribute("type", "text")
                .content(new NodeBuilder()
                        .description("plaintext")
                        .content(new byte[0])
                        .build())
                .build();

        assertNull(receiver.receive(inbound, NEWSLETTER));
    }

    @Test
    @DisplayName("receive: missing required server_id throws NoSuchElementException")
    void missingServerIdThrows() {
        var receiver = new NewsletterMessageReceiver(MessageFixtures.temporaryStore(SELF, null));
        var inbound = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0NL0004")
                .attribute("from", NEWSLETTER)
                .attribute("t", 1700000000L)
                .attribute("type", "text")
                .content(new NodeBuilder()
                        .description("plaintext")
                        .content(MessageContainerSpec.encode(MessageContainer.of("hi")))
                        .build())
                .build();

        // server_id is required for newsletter publish receipts.
        assertThrows(java.util.NoSuchElementException.class,
                () -> receiver.receive(inbound, NEWSLETTER));
    }

    @Test
    @DisplayName("receive: missing required id throws NoSuchElementException")
    void missingIdThrows() {
        var receiver = new NewsletterMessageReceiver(MessageFixtures.temporaryStore(SELF, null));
        var inbound = new NodeBuilder()
                .description("message")
                .attribute("from", NEWSLETTER)
                .attribute("t", 1700000000L)
                .attribute("server_id", 1)
                .attribute("type", "text")
                .content(new NodeBuilder()
                        .description("plaintext")
                        .content(MessageContainerSpec.encode(MessageContainer.of("hi")))
                        .build())
                .build();
        assertThrows(java.util.NoSuchElementException.class,
                () -> receiver.receive(inbound, NEWSLETTER));
    }
}
