package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.StubDeviceService;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PeerMessageSender}, mirroring
 * {@code WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg}.
 *
 * <p>Peer messages target one of the user's own devices and are wire-tagged
 * with {@code category="peer"} and {@code push_priority="high"}. The
 * payload is encrypted per-device via the Signal session cipher; the very
 * first send carries a {@code PKMSG} envelope plus a sibling
 * {@code <device-identity>} child.
 */
@DisplayName("PeerMessageSender")
class PeerMessageSenderTest {

    private static final Jid SELF_PRIMARY = Jid.of("12025550100:0@s.whatsapp.net");
    private static final Jid SELF_COMPANION = Jid.of("12025550100:73@s.whatsapp.net");

    @Test
    @DisplayName("send: emits <message category=\"peer\" push_priority=\"high\"> with <enc> and <device-identity>")
    void sendShape() {
        var senderStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        var recipientStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        TestSignalSession.establishSession(senderStore, SELF_COMPANION, recipientStore);

        var capturedStanza = new AtomicReference<Node>();
        var client = TestWhatsAppClient.create()
                .withStore(senderStore)
                .withAbPropsService(TestABPropsService.builder().build())
                .withSendNodeHandler(node -> {
                    capturedStanza.set(node.build());
                    // Return a success ack so the sender's AckParser is satisfied.
                    return new NodeBuilder()
                            .description("ack")
                            .attribute("t", 1700000000L)
                            .build();
                });
        var wamService = new DefaultWamService(client, client.abPropsService());
        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var deviceService = StubDeviceService.create();
        var sender = new PeerMessageSender(client, encryption, deviceService, client.abPropsService(), wamService);

        var messageInfo = peerMessageInfo("3EB0CAFEBABE", SELF_COMPANION);
        var ack = sender.send(SELF_COMPANION, messageInfo);

        var stanza = capturedStanza.get();
        assertNotNull(stanza, "PeerMessageSender must emit exactly one outbound <message>");
        assertEquals("message", stanza.description());

        // Outer attrs the WAWebSendMsgCreateDeviceStanza pin.
        assertEquals("3EB0CAFEBABE", stanza.getAttributeAsString("id").orElseThrow());
        assertEquals(SELF_COMPANION.toString(), stanza.getAttributeAsString("to").orElseThrow(),
                "outer to must be the target device JID");
        assertEquals("peer", stanza.getAttributeAsString("category").orElseThrow(),
                "category=peer routes the stanza outside the normal chat fanout pipeline");
        assertEquals("high", stanza.getAttributeAsString("push_priority").orElseThrow(),
                "push_priority=high asks the server to deliver promptly");

        // No <participants> wrapper — peer sends go straight to the device.
        assertFalse(stanza.getChild("participants").isPresent(),
                "peer fanout has no <participants> wrapper");

        // <enc v="2" type="pkmsg"> direct child.
        var enc = stanza.getChild("enc").orElseThrow();
        assertEquals("2", enc.getAttributeAsString("v").orElseThrow());
        assertEquals("pkmsg", enc.getAttributeAsString("type").orElseThrow(),
                "first send carries PKMSG since the recipient hasn't processed the prekey yet");

        // <device-identity> sibling for PKMSG sends.
        assertTrue(stanza.getChild("device-identity").isPresent(),
                "PKMSG peer sends must include <device-identity> for the recipient to verify the identity key");

        // Sanity: the ack handler returned a parseable success ack.
        assertTrue(ack.isSuccess(), "stub returned a t-only ack → success");
    }

    @Test
    @DisplayName("send: invokes deviceService.ensureSessions exactly once with the target device")
    void ensureSessionsCalled() {
        var senderStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        var recipientStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        TestSignalSession.establishSession(senderStore, SELF_COMPANION, recipientStore);

        var ensureCalls = new java.util.ArrayList<java.util.Collection<Jid>>();
        var client = TestWhatsAppClient.create()
                .withStore(senderStore)
                .withAbPropsService(TestABPropsService.builder().build())
                .withSendNodeHandler(node -> new NodeBuilder()
                        .description("ack")
                        .attribute("t", 1700000000L)
                        .build());
        var wamService = new DefaultWamService(client, client.abPropsService());
        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var deviceService = StubDeviceService.create()
                .withEnsureSessions(devices -> {
                    ensureCalls.add(devices);
                    return 0;
                });
        var sender = new PeerMessageSender(client, encryption, deviceService, client.abPropsService(), wamService);

        sender.send(SELF_COMPANION, peerMessageInfo("3EB0PEER01", SELF_COMPANION));

        assertEquals(1, ensureCalls.size(),
                "ensureSessions must be called exactly once per peer send");
        assertTrue(ensureCalls.getFirst().contains(SELF_COMPANION),
                "ensureSessions must receive the target device JID");
    }

    @Test
    @DisplayName("send: stanza type is derived from the payload (text content → type=\"text\")")
    void stanzaTypeFromContent() {
        var senderStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        var recipientStore = MessageFixtures.temporaryStore(Jid.of("12025550100@s.whatsapp.net"), null);
        TestSignalSession.establishSession(senderStore, SELF_COMPANION, recipientStore);

        var capturedStanza = new AtomicReference<Node>();
        var client = TestWhatsAppClient.create()
                .withStore(senderStore)
                .withAbPropsService(TestABPropsService.builder().build())
                .withSendNodeHandler(node -> {
                    capturedStanza.set(node.build());
                    return new NodeBuilder().description("ack").attribute("t", 1700000000L).build();
                });
        var wamService = new DefaultWamService(client, client.abPropsService());
        var encryption = new MessageEncryption(senderStore,
                new SignalSessionCipher(senderStore),
                new SignalGroupCipher(senderStore));
        var sender = new PeerMessageSender(client, encryption, StubDeviceService.create(), client.abPropsService(), wamService);

        sender.send(SELF_COMPANION, peerMessageInfo("3EB0TYPE01", SELF_COMPANION));

        var stanza = capturedStanza.get();
        // ExtendedTextMessage content → "text" stanza type.
        assertEquals("text", stanza.getAttributeAsString("type").orElseThrow(),
                "ExtendedTextMessage payload must produce a type=\"text\" peer stanza");
    }

    /**
     * Builds a peer-bound {@link com.github.auties00.cobalt.model.chat.ChatMessageInfo}
     * for the supplied target device.
     *
     * @param id           the wire message id
     * @param targetDevice the target device JID
     * @return the configured message info
     */
    private static com.github.auties00.cobalt.model.chat.ChatMessageInfo peerMessageInfo(
            String id, Jid targetDevice) {
        var key = new MessageKeyBuilder()
                .id(id)
                .parentJid(targetDevice)
                .fromMe(true)
                .senderJid(SELF_PRIMARY)
                .build();
        return new ChatMessageInfoBuilder()
                .key(key)
                .message(MessageContainer.of("peer payload"))
                .build();
    }
}
