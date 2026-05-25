package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers {@link ChatMessageReceiver} by driving a full encrypt-then-receive cycle: a sender-side
 * {@link MessageEncryption} produces a PKMSG ciphertext, it is wrapped in a synthetic inbound
 * {@code <message>} node, and handed to the recipient's {@link ChatMessageReceiver}, which must
 * decrypt, decode the protobuf, and return a populated
 * {@link com.github.auties00.cobalt.model.chat.ChatMessageInfo} carrying the sender's original
 * {@link MessageContainer}. Both sides share libsignal session state installed by
 * {@link TestSignalSession#establishSession} before the first encrypt.
 */
@DisplayName("ChatMessageReceiver")
class ChatMessageReceiverTest {

    private static final Jid SENDER_PRIMARY = Jid.of("12025550100:0@s.whatsapp.net");
    private static final Jid RECIPIENT_PRIMARY = Jid.of("19254863482:0@s.whatsapp.net");
    private static final Jid RECIPIENT_BARE = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SENDER_BARE = Jid.of("12025550100@s.whatsapp.net");

    @Test
    @DisplayName("PKMSG receive: decrypts to the sender's MessageContainer and returns a populated ChatMessageInfo")
    void receivePkmsgRoundTrip() {
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var recipientStore = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        TestSignalSession.establishSession(senderStore, RECIPIENT_PRIMARY, recipientStore);

        var senderContainer = MessageContainer.of("hello from sender");
        var senderEncryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var payload = senderEncryption.encryptForDevice(
                RECIPIENT_PRIMARY,
                MessageContainerSpec.encode(senderContainer));

        var inbound = new NodeBuilder()
                .description("message")
                .attribute("id", "3EB0RCV0001")
                .attribute("from", SENDER_PRIMARY)
                .attribute("t", 1700000000L)
                .attribute("type", "text")
                .content(new NodeBuilder()
                        .description("enc")
                        .attribute("v", "2")
                        .attribute("type", "pkmsg")
                        .content(payload.ciphertext())
                        .build())
                .build();

        var recipientDecryption = new MessageDecryption(recipientStore,
                new SignalSessionCipher(recipientStore),
                new SignalGroupCipher(recipientStore));
        var receiver = new ChatMessageReceiver(recipientStore, recipientDecryption);

        var info = receiver.receive(inbound, SENDER_PRIMARY);

        assertNotNull(info, "ChatMessageReceiver must return a populated ChatMessageInfo for a valid PKMSG");
        assertEquals("3EB0RCV0001", info.key().id().orElseThrow(),
                "message id must round-trip from the inbound stanza");

        var receivedContent = info.message().content();
        var receivedText = assertInstanceOf(ExtendedTextMessage.class, receivedContent,
                "the conversation field decodes into an ExtendedTextMessage on receive");
        assertEquals("hello from sender", receivedText.text().orElseThrow(),
                "decrypted payload bytes equal the sender's plaintext");
    }

    // Pins the WA Web behaviour that the sender keeps emitting PKMSG until the recipient's first
    // ack lands; the send-side mirror is MessageEncryptionTest.typeSwitchesAfterFirstAck.
    @Test
    @DisplayName("PKMSG receive: subsequent decrypts switch to MSG and recover correctly")
    void msgEnvelopeAfterPrekeyConsumed() {
        var senderStore = MessageFixtures.temporaryStore(SENDER_BARE, null);
        var recipientStore = MessageFixtures.temporaryStore(RECIPIENT_BARE, null);
        TestSignalSession.establishSession(senderStore, RECIPIENT_PRIMARY, recipientStore);
        var senderEncryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var recipientDecryption = new MessageDecryption(recipientStore,
                new SignalSessionCipher(recipientStore),
                new SignalGroupCipher(recipientStore));
        var receiver = new ChatMessageReceiver(recipientStore, recipientDecryption);

        var firstPayload = senderEncryption.encryptForDevice(
                RECIPIENT_PRIMARY,
                MessageContainerSpec.encode(MessageContainer.of("first")));
        receiver.receive(buildInbound("3EB0RCV0010", "pkmsg", firstPayload.ciphertext()), SENDER_PRIMARY);

        var secondPayload = senderEncryption.encryptForDevice(
                RECIPIENT_PRIMARY,
                MessageContainerSpec.encode(MessageContainer.of("second")));
        var info = receiver.receive(buildInbound("3EB0RCV0011", "pkmsg", secondPayload.ciphertext()), SENDER_PRIMARY);

        var receivedText = (ExtendedTextMessage) info.message().content();
        assertEquals("second", receivedText.text().orElseThrow(),
                "second decrypt must recover the new plaintext");
    }

    private static Node buildInbound(String id, String encType, byte[] ciphertext) {
        return new NodeBuilder()
                .description("message")
                .attribute("id", id)
                .attribute("from", SENDER_PRIMARY)
                // pins a deterministic stanza age so the expired-status branch never fires
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
