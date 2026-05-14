package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.StubDeviceService;
import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.BizStanza;
import com.github.auties00.cobalt.message.send.stanza.BotStanza;
import com.github.auties00.cobalt.message.send.stanza.MetaStanza;
import com.github.auties00.cobalt.message.send.stanza.ReportingStanza;
import com.github.auties00.cobalt.message.send.bot.BotProtobufTransform;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GroupMessageSender}, mirroring
 * {@code WAWebSendGroupMsgJob.encryptAndSendGroupMsg} and
 * {@code WAWebSendGroupSkmsgJob.encryptAndSendSenderKeyMsg}.
 *
 * <p>Two regimes are covered:
 *
 * <ul>
 *   <li><b>Steady-state SKMSG</b> — every group participant already
 *       holds the sender's key (the store flags them via
 *       {@code markSenderKeyDistributed}). The outgoing stanza carries a
 *       bare {@code <enc type="skmsg">} sibling under {@code <message>}
 *       and no {@code <participants>} wrapper.</li>
 *   <li><b>First-time distribution</b> — at least one participant has
 *       not yet received the sender's key. The outgoing stanza wraps
 *       per-device PKMSG envelopes under {@code <participants>} alongside
 *       the bare SKMSG payload, plus a {@code <device-identity>} sibling
 *       for PKMSG verification.</li>
 * </ul>
 */
@DisplayName("GroupMessageSender")
class GroupMessageSenderTest {

    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid GROUP = Jid.of("120363023250764418@g.us");
    private static final Jid PARTICIPANT_LID = Jid.of("83116928594056:0@lid");

    @Test
    @DisplayName("steady-state: bare <enc type=\"skmsg\">, no <participants>")
    void steadyStateSkmsg() {
        var senderStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        seedGroupMetadata(senderStore, /*lidAddressing*/ true);
        senderStore.markSenderKeyDistributed(GROUP, PARTICIPANT_LID);

        var captured = new AtomicReference<Node>();
        var client = clientWithCapture(senderStore, captured);
        var sender = groupMessageSender(client, senderStore,
                StubDeviceService.create().withGroupFanout(
                        (g, s) -> new DeviceGroupFanoutResult(Set.of(PARTICIPANT_LID), "2:hash")));

        sender.send(GROUP, chatMessage("3EB0GRP0001", GROUP, MessageContainer.of("hi")));

        var stanza = captured.get();
        assertEquals("message", stanza.description());
        assertEquals(GROUP.toString(), stanza.getAttributeAsString("to").orElseThrow());
        assertEquals("text", stanza.getAttributeAsString("type").orElseThrow());
        assertEquals("lid", stanza.getAttributeAsString("addressing_mode").orElseThrow());
        assertEquals("2:hash", stanza.getAttributeAsString("phash").orElseThrow());

        var enc = stanza.getChild("enc").orElseThrow();
        assertEquals("skmsg", enc.getAttributeAsString("type").orElseThrow());
        assertEquals("2", enc.getAttributeAsString("v").orElseThrow());
        assertFalse(stanza.getChild("participants").isPresent(),
                "steady-state SKMSG must not carry <participants>");
    }

    @Test
    @DisplayName("first-time distribution: <participants> wraps PKMSG envelopes plus the bare SKMSG sibling")
    void firstTimeDistribution() {
        var senderStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        var participantStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        TestSignalSession.establishSession(senderStore, PARTICIPANT_LID, participantStore);
        seedGroupMetadata(senderStore, /*lidAddressing*/ true);
        // Do NOT mark the participant as already-distributed — forces the
        // first-time distribution branch.

        var captured = new AtomicReference<Node>();
        var client = clientWithCapture(senderStore, captured);
        var sender = groupMessageSender(client, senderStore,
                StubDeviceService.create()
                        .withGroupFanout(
                                (g, s) -> new DeviceGroupFanoutResult(Set.of(PARTICIPANT_LID), "2:hash"))
                        .withEnsureSessions(devices -> 0));

        sender.send(GROUP, chatMessage("3EB0GRP0002", GROUP, MessageContainer.of("hello group")));

        var stanza = captured.get();
        assertEquals("text", stanza.getAttributeAsString("type").orElseThrow());
        assertEquals("lid", stanza.getAttributeAsString("addressing_mode").orElseThrow());

        // Bare SKMSG sibling.
        var enc = stanza.getChild("enc").orElseThrow();
        assertEquals("skmsg", enc.getAttributeAsString("type").orElseThrow());

        // <participants> wrapping per-device PKMSG payloads.
        var participants = stanza.getChild("participants").orElseThrow(
                () -> new AssertionError("first-distribution must wrap PKMSG payloads in <participants>"));
        var tos = participants.streamChildren("to").toList();
        assertEquals(1, tos.size(), "fanout is just one participant in this test");
        var participantEnc = tos.getFirst().getChild("enc").orElseThrow();
        assertEquals("pkmsg", participantEnc.getAttributeAsString("type").orElseThrow(),
                "SK distribution payload is wrapped as PKMSG");

        // <device-identity> sibling for PKMSG verification.
        assertTrue(stanza.getChild("device-identity").isPresent(),
                "first-distribution carries <device-identity> since PKMSG is present");
    }

    @Test
    @DisplayName("PN-addressing group: addressing_mode=\"pn\" propagates to outer <message>")
    void pnAddressingMode() {
        var senderStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        var participantPn = Jid.of("19254863482:0@s.whatsapp.net");
        seedGroupMetadata(senderStore, /*lidAddressing*/ false);
        senderStore.markSenderKeyDistributed(GROUP, participantPn);

        var captured = new AtomicReference<Node>();
        var client = clientWithCapture(senderStore, captured);
        var sender = groupMessageSender(client, senderStore,
                StubDeviceService.create().withGroupFanout(
                        (g, s) -> new DeviceGroupFanoutResult(Set.of(participantPn), "2:hash")));

        sender.send(GROUP, chatMessage("3EB0GRPPN01", GROUP, MessageContainer.of("hi")));

        var stanza = captured.get();
        assertEquals("pn", stanza.getAttributeAsString("addressing_mode").orElseThrow(),
                "non-LID group emits addressing_mode=pn");
    }

    /**
     * Seeds the supplied store with a {@link com.github.auties00.cobalt.model.chat.group.GroupMetadata}
     * for {@link #GROUP} so {@code GroupMessageSender} can resolve the
     * addressing mode.
     *
     * @param store          the sender store
     * @param lidAddressing  whether the group uses LID addressing
     */
    private static void seedGroupMetadata(WhatsAppStore store, boolean lidAddressing) {
        var metadata = new GroupMetadataBuilder()
                .jid(GROUP)
                .subject("test group")
                .isLidAddressingMode(lidAddressing)
                .build();
        store.addChatMetadata(metadata);
    }

    /**
     * Builds a TestWhatsAppClient that captures the first emitted stanza
     * and returns a success ack.
     *
     * @param store          the sender store
     * @param capturedStanza the slot to capture the emitted stanza into
     * @return the configured client
     */
    private static TestWhatsAppClient clientWithCapture(WhatsAppStore store, AtomicReference<Node> capturedStanza) {
        return TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(TestABPropsService.builder().build())
                .withSendNodeHandler(nb -> {
                    capturedStanza.set(nb.build());
                    return new NodeBuilder()
                            .description("ack")
                            .attribute("t", 1700000000L)
                            .build();
                });
    }

    /**
     * Builds a fully-wired GroupMessageSender for the supplied client + store.
     *
     * @param client        the test client
     * @param store         the sender store
     * @param deviceService the stubbed device service
     * @return the configured sender
     */
    private static GroupMessageSender groupMessageSender(TestWhatsAppClient client, WhatsAppStore store, StubDeviceService deviceService) {
        var ab = client.abPropsService();
        var encryption = new MessageEncryption(store,
                new SignalSessionCipher(store),
                new SignalGroupCipher(store));
        var wamService = new DefaultWamService(client, ab);
        var skDistribution = new SenderKeyDistribution(encryption, deviceService, store);
        var bot = new BotStanza(encryption, new BotProtobufTransform(store));
        var biz = new BizStanza(store);
        var meta = new MetaStanza(store);
        var reporting = new ReportingStanza(ab);
        return new GroupMessageSender(client, encryption, deviceService, ab,
                skDistribution, bot, biz, meta, reporting, wamService);
    }

    /**
     * Builds a {@link com.github.auties00.cobalt.model.chat.ChatMessageInfo}
     * targeted at the supplied group with the supplied container payload.
     *
     * @param id        the wire id
     * @param groupJid  the group JID
     * @param container the message payload
     * @return the configured message info
     */
    private static com.github.auties00.cobalt.model.chat.ChatMessageInfo chatMessage(
            String id, Jid groupJid, MessageContainer container) {
        var key = new MessageKeyBuilder()
                .id(id)
                .parentJid(groupJid)
                .fromMe(true)
                .senderJid(SELF_LID)
                .build();
        return new ChatMessageInfoBuilder()
                .key(key)
                .senderJid(SELF_LID)
                .message(container)
                .messageSecret(new byte[32])
                .build();
    }
}
