package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.StubDeviceService;
import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.MetaStanza;
import com.github.auties00.cobalt.message.send.stanza.ReportingStanza;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link StatusMessageSender}'s SKMSG send shape against the
 * {@code WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg} contract.
 *
 * @apiNote
 * Status broadcasts share the SKMSG group-send pipeline but address the
 * {@code status@broadcast} JID and carry a
 * {@code <meta status_setting=...>} child that mirrors the user's status
 * privacy preference (suppressed on revokes). Revokes whose audience is
 * no longer reachable by SKMSG take a dedicated per-device direct path.
 *
 * @implNote
 * This implementation drives the sender through a captured
 * {@link TestWhatsAppClient} and a
 * stubbed {@link StubDeviceService};
 * the cells exercise the steady-state SKMSG branch and the no-privacy
 * meta-shape branch, both of which can be asserted without seeding a real
 * status audience pipeline.
 */
@DisplayName("StatusMessageSender")
class StatusMessageSenderTest {

    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid STATUS = Jid.statusBroadcastAccount();
    private static final Jid AUDIENCE_DEVICE = Jid.of("19254863482:0@s.whatsapp.net");

    /**
     * Asserts the steady-state SKMSG status stanza shape when every
     * audience device already holds the sender key.
     */
    @Test
    @DisplayName("steady-state text status: outer to=status@broadcast, <enc type=skmsg>, no PKMSG distribution")
    void steadyStateStatus() {
        var senderStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        // Pre-mark the audience device as already having the sender key so the
        // steady-state branch is taken.
        senderStore.markSenderKeyDistributed(STATUS, AUDIENCE_DEVICE);

        var captured = new AtomicReference<Node>();
        var client = clientWithCapture(senderStore, captured);
        var sender = statusMessageSender(client, senderStore,
                StubDeviceService.create().withGroupFanout(
                        (g, s) -> new DeviceGroupFanoutResult(Set.of(AUDIENCE_DEVICE), "")));

        sender.send(STATUS, statusMessage("3EB0STAT001", MessageContainer.of("status body")));

        var stanza = captured.get();
        assertEquals("message", stanza.description());
        assertEquals(STATUS.toString(), stanza.getAttributeAsString("to").orElseThrow(),
                "outer to must be the canonical status@broadcast JID");
        assertEquals("text", stanza.getAttributeAsString("type").orElseThrow());

        // The bare SKMSG <enc> sibling carries the encrypted body.
        var enc = stanza.getChild("enc").orElseThrow();
        assertEquals("skmsg", enc.getAttributeAsString("type").orElseThrow());
        assertEquals("2", enc.getAttributeAsString("v").orElseThrow());

        // Steady-state: no PKMSG distribution, though a <participants> echo
        // may still exist for the receipt list with empty <to jid="..."> entries.
        stanza.getChild("participants").ifPresent(participants -> {
            var pkmsgCount = participants.streamChildren("to")
                    .flatMap(to -> to.streamChild("enc"))
                    .filter(e -> "pkmsg".equals(e.getAttributeAsString("type").orElse(null)))
                    .count();
            assertEquals(0, pkmsgCount,
                    "steady-state status must NOT carry any PKMSG distribution");
        });
    }

    /**
     * Asserts that the {@code status_setting} attribute is omitted when
     * no privacy entry is seeded.
     */
    @Test
    @DisplayName("text status: <meta> child is emitted when present (status_setting omitted when no privacy entry seeded)")
    void metaShapeWhenNoPrivacy() {
        var senderStore = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        senderStore.markSenderKeyDistributed(STATUS, AUDIENCE_DEVICE);

        var captured = new AtomicReference<Node>();
        var client = clientWithCapture(senderStore, captured);
        var sender = statusMessageSender(client, senderStore,
                StubDeviceService.create().withGroupFanout(
                        (g, s) -> new DeviceGroupFanoutResult(Set.of(AUDIENCE_DEVICE), "")));

        sender.send(STATUS, statusMessage("3EB0STAT002", MessageContainer.of("status with no privacy entry")));

        var stanza = captured.get();
        var meta = stanza.getChild("meta").orElse(null);
        if (meta != null) {
            assertTrue(meta.getAttribute("status_setting").isEmpty(),
                    "no privacy entry means no status_setting attribute on <meta>");
        }
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
     * Builds a fully-wired {@link StatusMessageSender} for the supplied
     * dependencies.
     *
     * @apiNote
     * Shared factory used by the steady-state and meta-shape cells; wires
     * every dependency the sender needs so the per-test setup only swaps
     * the store and the {@link StubDeviceService}.
     *
     * @param client        the {@link TestWhatsAppClient}
     * @param store         the sender {@link WhatsAppStore}
     * @param deviceService the stubbed {@link StubDeviceService}
     * @return the configured {@link StatusMessageSender}
     */
    private static StatusMessageSender statusMessageSender(TestWhatsAppClient client, WhatsAppStore store, StubDeviceService deviceService) {
        var ab = client.abPropsService();
        var encryption = new MessageEncryption(store,
                new SignalSessionCipher(store),
                new SignalGroupCipher(store));
        var wamService = new DefaultWamService(client, ab);
        var skDistribution = new SenderKeyDistribution(encryption, deviceService, store);
        var meta = new MetaStanza(store);
        var reporting = new ReportingStanza(ab);
        return new StatusMessageSender(client, encryption, deviceService, ab, skDistribution,
                meta, reporting, wamService);
    }

    /**
     * Builds a {@link ChatMessageInfo}
     * targeted at the status broadcast JID.
     *
     * @apiNote
     * Helper used by every status-send test cell; pairs the status
     * broadcast JID as the parent JID, a fixed sender JID, and the
     * {@code broadcast} flag.
     *
     * @param id        the wire id
     * @param container the {@link MessageContainer} payload
     * @return the configured message info
     */
    private static ChatMessageInfo statusMessage(
            String id, MessageContainer container) {
        var key = new MessageKeyBuilder()
                .id(id)
                .parentJid(STATUS)
                .fromMe(true)
                .senderJid(SELF_LID)
                .build();
        return new ChatMessageInfoBuilder()
                .key(key)
                .senderJid(SELF_LID)
                .message(container)
                .messageSecret(new byte[32])
                .broadcast(true)
                .build();
    }
}
