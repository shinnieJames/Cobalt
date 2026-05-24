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
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
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
 * Exercises {@link GroupMessageSender}'s wire-stanza shape against the
 * WA Web {@code encryptAndSendGroupMsg} contract.
 *
 * @apiNote
 * Covers three send regimes that mirror the WA Web job split. Steady-state
 * SKMSG (every participant already holds the key) emits a bare
 * {@code <enc type="skmsg">} sibling with no {@code <participants>}
 * wrapper. First-time distribution wraps the per-device PKMSG envelopes
 * under {@code <participants>} alongside the SKMSG payload and emits a
 * sibling {@code <device-identity>} so the recipients can verify the
 * sender's identity key. PN-addressed groups stamp
 * {@code addressing_mode="pn"} on the outer message.
 *
 * @implNote
 * This implementation drives the sender through a stubbed
 * {@link StubDeviceService} and a
 * captured {@link TestWhatsAppClient}
 * that records the first emitted stanza into an {@link AtomicReference};
 * the assertions read attribute slots off the captured {@link Node}.
 */
@DisplayName("GroupMessageSender")
class GroupMessageSenderTest {

    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid GROUP = Jid.of("120363023250764418@g.us");
    private static final Jid PARTICIPANT_LID = Jid.of("83116928594056:0@lid");

    /**
     * Asserts the steady-state SKMSG stanza shape when every audience
     * device already holds the sender key.
     */
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

    /**
     * Asserts the first-time-distribution stanza shape when at least one
     * audience device must receive the sender-key distribution payload.
     */
    @Test
    @DisplayName("first-time distribution: <participants> wraps PKMSG envelopes plus the bare SKMSG sibling")
    void firstTimeDistribution() {
        var senderStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        var participantStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        TestSignalSession.establishSession(senderStore, PARTICIPANT_LID, participantStore);
        seedGroupMetadata(senderStore, /*lidAddressing*/ true);
        // The participant is intentionally NOT marked as already-distributed
        // so the first-time distribution branch fires.

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

    /**
     * Asserts that a PN-addressed group emits
     * {@code addressing_mode="pn"} on the outer {@code <message>}.
     */
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
     * Seeds the supplied store with a
     * {@link com.github.auties00.cobalt.model.chat.group.GroupMetadata} for
     * {@link #GROUP}.
     *
     * @apiNote
     * Required so that {@link GroupMessageSender} can resolve the
     * addressing mode for the test group without going through the live
     * group-metadata fetch path.
     *
     * @param store          the sender {@link WhatsAppStore}
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
     * Builds a {@link TestWhatsAppClient} that captures the first emitted
     * stanza into {@code capturedStanza} and returns a success ack.
     *
     * @apiNote
     * The synthetic ack carries only the {@code t} attribute so
     * {@link com.github.auties00.cobalt.ack.AckParser} parses
     * it as a success result with no error code.
     *
     * @param store          the sender {@link WhatsAppStore}
     * @param capturedStanza the slot to capture the emitted stanza into
     * @return the configured {@link TestWhatsAppClient}
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
     * Builds a fully-wired {@link GroupMessageSender} for the supplied
     * dependencies.
     *
     * @apiNote
     * Wires every dependency the sender needs (encryption, WAM, sender-key
     * distribution, bot, biz, meta, reporting) so the per-test setup only
     * has to swap the store and the {@link StubDeviceService}.
     *
     * @param client        the {@link TestWhatsAppClient}
     * @param store         the sender {@link WhatsAppStore}
     * @param deviceService the stubbed {@link StubDeviceService}
     * @return the configured {@link GroupMessageSender}
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
     * Builds a {@link ChatMessageInfo}
     * targeted at the supplied group.
     *
     * @apiNote
     * The message carries a fixed wire id and a zeroed
     * {@code messageSecret}; tests that need a unique id pass distinct
     * literal {@code id} values.
     *
     * @param id        the wire id
     * @param groupJid  the group {@link Jid}
     * @param container the {@link MessageContainer} payload
     * @return the configured message info
     */
    private static ChatMessageInfo chatMessage(
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
