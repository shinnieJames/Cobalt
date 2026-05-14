package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.dedup.MessageDedup;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.bot.BotProtobufTransform;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.*;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.message.preview.LinkPreviewService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.WebcMessageSendEventBuilder;

import java.util.Objects;

/**
 * Routes outgoing messages through the WhatsApp protocol. The service is the
 * single entry point for the send pipeline and dispatches to the appropriate
 * sender based on the chat JID server: 1:1 user chats use per-device Signal
 * encryption with chat fanout, groups use sender-key encryption serialised
 * per group, status updates use sender-key encryption against the status
 * audience, and newsletters use plaintext SMAX. Peer protocol messages sent
 * to the user's own devices follow a dedicated path via
 * {@link #sendPeer(Jid, ChatMessageInfo)}.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgJob")
@WhatsAppWebModule(moduleName = "WAWebEncryptAndSendStatusMsg")
@WhatsAppWebModule(moduleName = "WAWebNewsletterSendMessageQueryJob")
@WhatsAppWebModule(moduleName = "WAWebSendAppStateSyncMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendMsgRecordAction")
public final class MessageSendingService {
    /**
     * Holds the preparer that turns raw {@link MessageContainer} instances
     * into fully populated {@link MessageInfo} objects.
     */
    private final MessagePreparer preparer;

    /**
     * Holds the deduplication tracker used to prevent the same message id
     * from being sent concurrently.
     */
    private final MessageDedup messageDedup;

    /**
     * Holds the sender for 1:1 user chats addressed by PN or LID.
     */
    private final UserMessageSender userSender;

    /**
     * Holds the sender for group chats using sender-key encryption.
     */
    private final GroupMessageSender groupSender;

    /**
     * Holds the sender for status updates.
     */
    private final StatusMessageSender statusSender;

    /**
     * Holds the sender for newsletter messages, which travel as plaintext over
     * SMAX.
     */
    private final NewsletterMessageSender newsletterSender;

    /**
     * Holds the sender for peer protocol messages exchanged with the user's
     * own devices.
     */
    private final PeerMessageSender peerSender;

    /**
     * Holds the client used to reach shared services from the send-pipeline
     * entry point.
     */
    private final WhatsAppClient client;

    /**
     * Holds the WAM telemetry service used to commit per-send events.
     */
    private final WamService wamService;

    /**
     * Holds the link-preview pipeline used to enrich outgoing extended-text
     * messages with rich previews.
     */
    private final LinkPreviewService linkPreviewService;

    /**
     * Constructs a sending service and wires up all sub-senders.
     *
     * @param client         the WhatsApp client used to dispatch stanzas
     * @param encryption     the message encryption service
     * @param deviceService  the device service used for fanout resolution
     * @param abPropsService the AB-props service used for feature gating
     * @param wamService     the WAM telemetry service for per-send events
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageSendingService(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService,
            WamService wamService
    ) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(encryption, "encryption");
        Objects.requireNonNull(deviceService, "deviceService");
        Objects.requireNonNull(abPropsService, "abPropsService");
        Objects.requireNonNull(wamService, "wamService");

        this.client = client;
        this.wamService = wamService;
        this.linkPreviewService = new LinkPreviewService(client, abPropsService);
        var store = client.store();
        this.preparer = new MessagePreparer(store);
        this.messageDedup = new MessageDedup();
        var skDistribution = new SenderKeyDistribution(encryption, deviceService, store);
        var botTransform = new BotProtobufTransform(store);
        var botStanza = new BotStanza(encryption, botTransform);
        var bizStanza = new BizStanza(store);
        var metaStanza = new MetaStanza(store);
        var reportingStanza = new ReportingStanza(abPropsService);
        var ctwaStanza = new CtwaAttributionStanza(store, abPropsService);
        var tcTokenStanza = new TcTokenStanza(store, abPropsService);

        this.userSender = new UserMessageSender(client, encryption, deviceService, abPropsService,
                botStanza, bizStanza, metaStanza, reportingStanza, ctwaStanza, tcTokenStanza, wamService);
        this.groupSender = new GroupMessageSender(client, encryption, deviceService, abPropsService, skDistribution, botStanza, bizStanza, metaStanza, reportingStanza, wamService);
        this.statusSender = new StatusMessageSender(client, encryption, deviceService, abPropsService, skDistribution, metaStanza, reportingStanza, wamService);
        this.newsletterSender = new NewsletterMessageSender(client, abPropsService, wamService);
        this.peerSender = new PeerMessageSender(client, encryption, deviceService, abPropsService, wamService);
    }

    /**
     * Prepares the given container into a fully populated message info and
     * sends it to the specified chat.
     *
     * <p>Preparation generates a unique id and message secret, populates the
     * device-context info, validates addon encryption state, auto-converts
     * reactions/comments to their encrypted variants for CAG groups, and
     * decorates extended-text bodies with a link preview when applicable.
     *
     * @param chatJid   the recipient chat JID
     * @param container the raw message content
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult send(Jid chatJid, MessageContainer container) {
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(container, "container");

        if (container.content() instanceof ExtendedTextMessage extended) {
            linkPreviewService.decorate(chatJid, extended);
        }

        MessageInfo prepared;
        if (chatJid.hasServer(JidServer.newsletter())) {
            prepared = preparer.prepareNewsletter(chatJid, container);
        } else {
            prepared = preparer.prepareChat(chatJid, container);
        }

        return send(prepared);
    }

    /**
     * Sends a pre-prepared message info directly, dispatching to the
     * appropriate sender based on the message-info subtype and the chat JID's
     * server.
     *
     * @param messageInfo the fully-prepared outgoing message
     * @return the server ack result
     * @throws NullPointerException                            if any argument is {@code null}
     * @throws IllegalArgumentException                        if the key is missing the id or parent JID
     * @throws WhatsAppMessageException.Send.InvalidRecipient  if the chat JID type is unsupported or
     *                                                         does not match the message-info subtype
     * @throws WhatsAppMessageException.Send.Unknown           if a send is already in flight for the
     *                                                         same message id
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult send(MessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo");

        var messageId = messageInfo
                .key()
                .id()
                .orElseThrow(() -> new IllegalArgumentException("messageId is required for outgoing messages"));
        var parentJid = messageInfo.key()
                .parentJid()
                .orElseThrow(() -> new IllegalArgumentException("parentJid is required for outgoing messages"));

        if (messageInfo.message() != null
                && messageInfo.message().content() instanceof DocumentMessage document) {
            wamService.logSendDocumentEvent(
                    document.fileName().orElse(null),
                    document.mediaSize().orElse(0L));
        }

        if (messageDedup.isPending(messageId)) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Duplicate send for message ID: " + messageId, null);
        }

        messageDedup.add(messageId);
        try {
            var sendEvent = buildWebcMessageSendEvent(messageInfo);
            var result = switch (messageInfo) {
                case ChatMessageInfo chatMessage when parentJid.hasUserServer() || parentJid.hasLidServer() ->
                    userSender.send(parentJid, chatMessage);
                case ChatMessageInfo chatMessage when parentJid.hasGroupOrCommunityServer() ->
                    groupSender.send(parentJid, chatMessage);
                case ChatMessageInfo chatMessage when parentJid.isStatusBroadcastAccount() ->
                    statusSender.send(parentJid, chatMessage);
                case NewsletterMessageInfo newsletterMessage when parentJid.hasNewsletterServer() ->
                    newsletterSender.send(parentJid, newsletterMessage);
                default -> throw new WhatsAppMessageException.Send.InvalidRecipient(
                    parentJid, "Unsupported combination: " + messageInfo.getClass().getSimpleName()
                            + " with JID type " + parentJid.server());
            };
            if (sendEvent != null) {
                wamService.commit(sendEvent.stopMessageSendT().build());
            }
            return result;
        } finally {
            messageDedup.remove(messageId);
        }
    }

    /**
     * Builds a {@link WebcMessageSendEventBuilder} pre-populated with the
     * message classification and a started timer for the given outbound
     * message, or returns {@code null} when the event must be suppressed.
     *
     * <p>The event is suppressed for newsletter sends (which go through a
     * different WA Web logger entirely) and for protocol revoke messages.
     * For every other chat message the builder is initialised with the
     * resolved {@code messageType} and {@code messageMediaType}, the
     * {@code messageIsForward} flag taken from the optional
     * {@link ContextualMessage#contextInfo()}, and the timer is started so it
     * can be stopped before the success-branch commit.
     *
     * @param messageInfo the message about to be sent
     * @return the pre-configured builder, or {@code null} when no event should
     *         be emitted for this send
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgRecordAction", exports = "sendMsgRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private WebcMessageSendEventBuilder buildWebcMessageSendEvent(MessageInfo messageInfo) {
        if (!(messageInfo instanceof ChatMessageInfo chatMessage)) {
            return null;
        }
        var content = chatMessage.message() == null ? null : chatMessage.message().content();
        if (content instanceof ProtocolMessage protocol
                && protocol.type().orElse(null) == ProtocolMessage.Type.REVOKE) {
            return null;
        }
        var isForwarded = content instanceof ContextualMessage contextual
                && contextual.contextInfo().map(ctx -> ctx.isForwarded()).orElse(false);
        return new WebcMessageSendEventBuilder()
                .messageType(wamService.getWamMessageType(chatMessage))
                .messageMediaType(wamService.getWamMediaType(chatMessage))
                .messageIsForward(isForwarded)
                .startMessageSendT();
    }

    /**
     * Sends a standalone sender-key distribution to a group, with no message
     * content. This pre-distributes sender keys to participants that do not
     * yet hold them and returns silently when every participant already does.
     *
     * @param groupJid the group JID to distribute keys for
     * @param key      the message key carrying the id and parent JID
     * @throws NullPointerException                            if any argument is {@code null}
     * @throws IllegalArgumentException                        if the key has no id
     * @throws WhatsAppMessageException.Send.InvalidRecipient  if the JID does not refer to a group
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendKeyDistributionMsgAction", exports = "sendKeyDistributionMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void sendKeyDistribution(Jid groupJid, MessageKey key) {
        Objects.requireNonNull(groupJid, "groupJid");
        Objects.requireNonNull(key, "key");

        var messageId = key.id()
                .orElseThrow(() -> new IllegalArgumentException(
                        "messageId is required for key distribution"));

        if (!groupJid.hasGroupOrCommunityServer()) {
            throw new WhatsAppMessageException.Send.InvalidRecipient(
                    groupJid, "Key distribution is only supported for group chats");
        }

        groupSender.sendKeyDistribution(groupJid, messageId);
    }

    /**
     * Sends a peer protocol message to one of the user's own devices. Peer
     * messages cover app-state sync, key shares, and fatal-exception
     * notifications, are encrypted per device, and are tagged with
     * {@code category="peer"} on the wire.
     *
     * @param targetDevice the target device JID, typically the primary device
     * @param messageInfo  the protocol message to send
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendAppStateSyncMsgJob", exports = "encryptAndSendKeyMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
        Objects.requireNonNull(targetDevice, "targetDevice");
        Objects.requireNonNull(messageInfo, "messageInfo");
        return peerSender.send(targetDevice, messageInfo);
    }
}
