package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.device.StubDeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfoBuilder;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.wam.DefaultWamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link MessageSendingService}'s error and validation branches
 * for the {@code WAWebSendMsgJob.encryptAndSendMsg} contract.
 *
 * @apiNote
 * Covers the cross-shape rejection cells: a {@link com.github.auties00.cobalt.model.chat.ChatMessageInfo}
 * targeting a newsletter JID and a
 * {@link com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo}
 * targeting a non-newsletter JID both raise
 * {@link WhatsAppMessageException.Send.InvalidRecipient}; a missing
 * {@code messageId} or {@code parentJid} on the key raises
 * {@link IllegalArgumentException};
 * {@link MessageSendingService#sendKeyDistribution(Jid, MessageKey)}
 * rejects non-group JIDs with the same {@code InvalidRecipient}; null
 * arguments raise {@link NullPointerException}.
 *
 * @implNote
 * The successful dispatch-by-sender-kind path is exercised by the
 * per-sender test families and by the live-corpus oracle; this class only
 * covers the validation cells that need a real but minimal DI graph.
 */
@DisplayName("MessageSendingService")
class MessageSendingServiceTest {

    private static final Jid SELF_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");
    private static final Jid PEER_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid GROUP = Jid.of("120363023250764418@g.us");
    private static final Jid NEWSLETTER = Jid.of("120363402045452944@newsletter");

    /**
     * Asserts that a {@link com.github.auties00.cobalt.model.chat.ChatMessageInfo}
     * with a newsletter parent JID is rejected as an invalid recipient.
     */
    @Test
    @DisplayName("send(MessageInfo): ChatMessageInfo with a newsletter parent JID -> InvalidRecipient")
    void chatMessageInfoToNewsletterFails() {
        var service = buildService();
        var info = new ChatMessageInfoBuilder()
                .key(new MessageKeyBuilder()
                        .id("3EB0CROSS01")
                        .parentJid(NEWSLETTER)
                        .fromMe(true)
                        .build())
                .message(MessageContainer.of("nope"))
                .build();

        assertThrows(WhatsAppMessageException.Send.InvalidRecipient.class,
                () -> service.send(info),
                "ChatMessageInfo to a newsletter JID is an unsupported combination");
    }

    /**
     * Asserts that a {@link com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo}
     * with a non-newsletter parent JID is rejected as an invalid recipient.
     */
    @Test
    @DisplayName("send(MessageInfo): NewsletterMessageInfo with a non-newsletter parent JID -> InvalidRecipient")
    void newsletterMessageInfoToChatFails() {
        var service = buildService();
        var info = new NewsletterMessageInfoBuilder()
                .key(new MessageKeyBuilder()
                        .id("3EB0CROSS02")
                        .parentJid(PEER_PN)
                        .fromMe(true)
                        .build())
                .serverId(1)
                .message(MessageContainer.of("nope"))
                .build();

        assertThrows(WhatsAppMessageException.Send.InvalidRecipient.class,
                () -> service.send(info),
                "NewsletterMessageInfo to a non-newsletter JID is an unsupported combination");
    }

    /**
     * Asserts that a missing message id on the key fails fast with
     * {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("send(MessageInfo): missing messageId on the key throws IllegalArgumentException")
    void missingMessageIdThrows() {
        var service = buildService();
        var info = new ChatMessageInfoBuilder()
                .key(new MessageKeyBuilder()
                        .parentJid(PEER_PN)
                        .fromMe(true)
                        .build())
                .message(MessageContainer.of("hi"))
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.send(info));
    }

    /**
     * Asserts that a missing parent JID on the key fails fast with
     * {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("send(MessageInfo): missing parentJid on the key throws IllegalArgumentException")
    void missingParentJidThrows() {
        var service = buildService();
        var info = new ChatMessageInfoBuilder()
                .key(new MessageKeyBuilder()
                        .id("3EB0NOPARENT")
                        .fromMe(true)
                        .build())
                .message(MessageContainer.of("hi"))
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.send(info));
    }

    /**
     * Asserts that {@code sendKeyDistribution} rejects non-group JIDs.
     */
    @Test
    @DisplayName("sendKeyDistribution: non-group JID throws InvalidRecipient")
    void sendKeyDistributionNonGroup() {
        var service = buildService();
        var key = new MessageKeyBuilder()
                .id("3EB0SKD0001")
                .parentJid(PEER_PN)
                .fromMe(true)
                .build();
        assertThrows(WhatsAppMessageException.Send.InvalidRecipient.class,
                () -> service.sendKeyDistribution(PEER_PN, key));
    }

    /**
     * Asserts that {@code sendKeyDistribution} rejects a key without an id.
     */
    @Test
    @DisplayName("sendKeyDistribution: key without id throws IllegalArgumentException")
    void sendKeyDistributionMissingId() {
        var service = buildService();
        var key = new MessageKeyBuilder()
                .parentJid(GROUP)
                .fromMe(true)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> service.sendKeyDistribution(GROUP, key));
    }

    /**
     * Asserts that null arguments to
     * {@code send(Jid, MessageContainer)} fail fast.
     */
    @Test
    @DisplayName("send(Jid, MessageContainer): null arguments throw NullPointerException")
    void sendNullArgs() {
        var service = buildService();
        assertThrows(NullPointerException.class,
                () -> service.send(null, MessageContainer.of("hi")));
        assertThrows(NullPointerException.class,
                () -> service.send(PEER_PN, null));
    }

    /**
     * Asserts that a null argument to {@code send(MessageInfo)} fails fast.
     */
    @Test
    @DisplayName("send(MessageInfo): null arg throws NullPointerException")
    void sendInfoNullArg() {
        var service = buildService();
        assertThrows(NullPointerException.class, () -> service.send(null));
    }

    /**
     * Asserts that null arguments to {@code sendPeer} fail fast.
     */
    @Test
    @DisplayName("sendPeer: null arguments throw NullPointerException")
    void sendPeerNullArgs() {
        var service = buildService();
        var info = new ChatMessageInfoBuilder()
                .key(new MessageKeyBuilder()
                        .id("3EB0PEER01")
                        .parentJid(SELF_PN)
                        .fromMe(true)
                        .build())
                .message(MessageContainer.of("peer payload"))
                .build();
        assertThrows(NullPointerException.class,
                () -> service.sendPeer(null, info));
        assertThrows(NullPointerException.class,
                () -> service.sendPeer(SELF_PN, null));
    }

    /**
     * Builds a fully-wired {@link MessageSendingService} backed by stubbed
     * dependencies.
     *
     * @apiNote
     * Shared factory used by every cell; the service is wired with a
     * temporary {@link com.github.auties00.cobalt.store.WhatsAppStore},
     * the {@link TestWhatsAppClient}, and
     * a {@link StubDeviceService} so the
     * validation cells can exercise the public surface without touching
     * the wire.
     *
     * @return the configured {@link MessageSendingService}
     */
    private static MessageSendingService buildService() {
        var store = MessageFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create()
                .withStore(store)
                .withAbPropsService(TestABPropsService.builder().build());
        var encryption = new MessageEncryption(store,
                new SignalSessionCipher(store),
                new SignalGroupCipher(store));
        var wam = new DefaultWamService(client, client.abPropsService());
        var migration = new LidMigrationService(client, client.abPropsService(), wam);
        return new MessageSendingService(client, encryption,
                StubDeviceService.create(), migration, client.abPropsService(), wam,
                new MediaTranscoderService(client, client.abPropsService(), TestMediaConnectionService.create()));
    }
}
