package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.dedup.MessageDedup;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.message.send.bot.BotProtobufTransform;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution;
import com.github.auties00.cobalt.message.send.stanza.*;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.LidMigrationService;
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
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.WebcMessageSendEventBuilder;

import java.util.Objects;

/**
 * Orchestrates every outgoing-message wire dispatch.
 *
 * <p>The service is the single entry point for the send pipeline. It prepares a
 * raw {@link MessageContainer} into a fully-populated {@link MessageInfo} via
 * {@link MessagePreparer}, dedupes concurrent sends keyed by message id via
 * {@link MessageDedup}, brackets the dispatch in a {@code WebcMessageSend} WAM
 * event, then routes by the parent JID's server kind to one of the
 * per-chat-kind sub-senders:
 *
 * <ul>
 *   <li>1:1 PN or LID chat to {@link UserMessageSender}: per-device Signal
 *       fanout with optional phash-mismatch resend.</li>
 *   <li>group or community to {@link GroupMessageSender}: sender-key SKMSG,
 *       sender-key distribution on first contact, addressing-mode migration on
 *       stale-ack errors.</li>
 *   <li>status broadcast to {@link StatusMessageSender}: SKMSG against the
 *       audience derived from the user's status privacy preferences.</li>
 *   <li>business broadcast list to {@link BroadcastMessageSender}: SKMSG against
 *       the audience read from the local
 *       {@link com.github.auties00.cobalt.model.business.BusinessBroadcastList}
 *       roster.</li>
 *   <li>newsletter to {@link NewsletterMessageSender}: plaintext SMAX publish,
 *       no Signal envelope.</li>
 * </ul>
 *
 * <p>Peer protocol messages (app-state sync, fatal-exception notification,
 * peer-data ops) bypass the dispatch switch and go through
 * {@link #sendPeer(Jid, ChatMessageInfo)}, which delegates straight to
 * {@link PeerMessageSender}.
 */
@WhatsAppWebModule(moduleName = "WAWebSendMsgJob")
@WhatsAppWebModule(moduleName = "WAWebEncryptAndSendStatusMsg")
@WhatsAppWebModule(moduleName = "WAWebNewsletterSendMessageQueryJob")
@WhatsAppWebModule(moduleName = "WAWebSendAppStateSyncMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendMsgRecordAction")
public final class MessageSendingService {
    /**
     * Turns a raw {@link MessageContainer} into a populated {@link MessageInfo}.
     */
    private final MessagePreparer preparer;

    /**
     * Rejects a second concurrent send carrying the same wire id.
     */
    private final MessageDedup messageDedup;

    /**
     * Handles 1:1 PN- or LID-addressed chats.
     */
    private final UserMessageSender userSender;

    /**
     * Handles group and community chats.
     */
    private final GroupMessageSender groupSender;

    /**
     * Handles status broadcasts.
     */
    private final StatusMessageSender statusSender;

    /**
     * Handles business broadcast-list sends addressed via
     * {@code <id>@broadcast}.
     */
    private final BroadcastMessageSender broadcastSender;

    /**
     * Handles plaintext SMAX newsletter publishes.
     */
    private final NewsletterMessageSender newsletterSender;

    /**
     * Handles app-state-sync and other own-device peer protocol stanzas.
     */
    private final PeerMessageSender peerSender;

    /**
     * Reaches shared services not already injected into the sub-senders.
     */
    private final WhatsAppClient client;

    /**
     * Records per-send {@code WebcMessageSend} events.
     */
    private final WamService wamService;

    /**
     * Attaches a rich preview to outgoing extended-text messages whose body
     * contains a URL.
     */
    private final MediaTranscoderService mediaTranscoderService;

    /**
     * Constructs a {@link MessageSendingService} and wires up every sub-sender.
     *
     * <p>Embedders construct exactly one of these per client; the constructor
     * also instantiates the link-preview pipeline, the sender-key distribution
     * helper, and the stanza-decoration builders ({@link BotStanza},
     * {@link BizStanza}, {@link MetaStanza}, {@link ReportingStanza},
     * {@link CtwaAttributionStanza}, {@link TcTokenStanza}) that the sub-senders
     * share.
     *
     * @param client                 the {@link WhatsAppClient} used to dispatch
     *                                stanzas
     * @param encryption             the {@link MessageEncryption} service
     * @param deviceService          the {@link DeviceService} used for fanout
     *                               resolution
     * @param lidMigrationService    the {@link LidMigrationService} consulted by
     *                               the user-chat sender to gate the PN-to-LID
     *                               stanza rewrite
     * @param abPropsService         the {@link ABPropsService} consulted for
     *                               feature-gating decisions
     * @param wamService             the {@link WamService} that records per-send
     *                               events
     * @param mediaTranscoderService the {@link MediaTranscoderService} consulted
     *                               for link-preview decoration on outgoing text
     *                               messages
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageSendingService(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            WamService wamService,
            MediaTranscoderService mediaTranscoderService
    ) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(encryption, "encryption");
        Objects.requireNonNull(deviceService, "deviceService");
        Objects.requireNonNull(lidMigrationService, "lidMigrationService");
        Objects.requireNonNull(abPropsService, "abPropsService");
        Objects.requireNonNull(wamService, "wamService");
        Objects.requireNonNull(mediaTranscoderService, "mediaTranscoderService");

        this.client = client;
        this.wamService = wamService;
        this.mediaTranscoderService = mediaTranscoderService;
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

        this.userSender = new UserMessageSender(client, encryption, deviceService, lidMigrationService, abPropsService,
                botStanza, bizStanza, metaStanza, reportingStanza, ctwaStanza, tcTokenStanza, wamService);
        this.groupSender = new GroupMessageSender(client, encryption, deviceService, abPropsService, skDistribution, botStanza, bizStanza, metaStanza, reportingStanza, wamService);
        this.statusSender = new StatusMessageSender(client, encryption, deviceService, abPropsService, skDistribution, metaStanza, reportingStanza, wamService);
        this.broadcastSender = new BroadcastMessageSender(client, encryption, deviceService, abPropsService, skDistribution, metaStanza, reportingStanza, wamService);
        this.newsletterSender = new NewsletterMessageSender(client, abPropsService, wamService);
        this.peerSender = new PeerMessageSender(client, encryption, deviceService, abPropsService, wamService);
    }

    /**
     * Prepares the raw container and dispatches it to the supplied chat.
     *
     * <p>Top-level entry point for embedders sending a message: the call
     * generates a wire id and {@code messageSecret}, populates the
     * device-context info, auto-converts reactions and comments to their
     * encrypted addon variants in CAG groups, decorates an extended-text body
     * with a link preview when applicable, and then forwards to the
     * chat-kind-specific sender.
     *
     * @param chatJid   the recipient chat, group, status, or newsletter
     *                  {@link Jid}
     * @param container the raw {@link MessageContainer}
     * @return the parsed server {@link AckResult}
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgJob", exports = "encryptAndSendMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AckResult send(Jid chatJid, MessageContainer container) {
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(container, "container");

        if (container.content() instanceof ExtendedTextMessage extended) {
            mediaTranscoderService.decorate(chatJid, extended);
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
     * Dispatches a fully-prepared {@link MessageInfo} to its target chat.
     *
     * <p>This overload is for callers that have already prepared the
     * {@link MessageInfo} (typically a resend, a debug-injection harness, or a
     * follow-up to a {@link #sendKeyDistribution(Jid, MessageKey)} call). The
     * routing predicate keys on the message-info subtype combined with the
     * parent JID's server. {@link DocumentMessage} payloads also emit the
     * {@code WAWebSendDocumentEvent} WAM metric before dispatch.
     *
     * @param messageInfo the fully-prepared outgoing {@link MessageInfo}
     * @return the parsed server {@link AckResult}
     * @throws NullPointerException                           if
     *                                                        {@code messageInfo}
     *                                                        is {@code null}
     * @throws IllegalArgumentException                       if the
     *                                                        {@link MessageKey}
     *                                                        is missing the id or
     *                                                        parent JID
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the parent JID's
     *                                                        server does not
     *                                                        match the
     *                                                        {@link MessageInfo}
     *                                                        subtype
     * @throws WhatsAppMessageException.Send.Unknown          if a send is
     *                                                        already in flight
     *                                                        for the same id
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
                case ChatMessageInfo chatMessage when parentJid.hasBroadcastServer() ->
                    broadcastSender.send(parentJid, chatMessage);
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
     * Builds the pre-configured {@link WebcMessageSendEventBuilder} for the
     * given outgoing message, or returns {@code null} when the event must be
     * suppressed.
     *
     * <p>Suppressed for newsletter sends (a different WA Web logger covers
     * those) and for protocol revoke messages. For every other chat message the
     * builder is pre-populated with the resolved {@code messageType},
     * {@code messageMediaType}, the {@code messageIsForward} flag taken from the
     * optional {@link ContextualMessage#contextInfo()}, and the latency timer is
     * started so it can be stopped before the success-branch commit on the
     * caller side.
     *
     * @param messageInfo the message about to be sent
     * @return the prepared builder, or {@code null} when no event should be
     *         emitted
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
     * Dispatches a standalone sender-key distribution to a group with no message
     * content.
     *
     * <p>Use to pre-seed sender keys before sending a media-heavy or otherwise
     * latency-sensitive message; the call returns silently when every
     * participant already holds the key. The stanza carries the {@code text}
     * type marker and {@code device_fanout="false"}.
     *
     * @param groupJid the target group {@link Jid}
     * @param key      the {@link MessageKey} carrying the wire id and parent JID
     * @throws NullPointerException                           if any argument is
     *                                                        {@code null}
     * @throws IllegalArgumentException                       if the key has no id
     * @throws WhatsAppMessageException.Send.InvalidRecipient if {@code groupJid}
     *                                                        is not a group or
     *                                                        community
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
     * Dispatches a peer protocol message to one of the user's own devices.
     *
     * <p>Peer messages cover app-state sync, key shares, fatal-exception
     * notifications, and peer-data operation requests and responses; the wire
     * stanza is encrypted per device and tagged with {@code category="peer"} so
     * the server routes it to the linked-device shelf rather than to the main
     * chat fanout.
     *
     * @param targetDevice the target device {@link Jid}, typically the user's
     *                     primary device
     * @param messageInfo  the {@link ChatMessageInfo} wrapping the protocol
     *                     payload
     * @return the parsed server {@link AckResult}
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
