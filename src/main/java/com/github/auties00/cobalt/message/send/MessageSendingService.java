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
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.props.ABPropsService;

import java.util.Objects;

/**
 * Orchestrates the sending of messages through the WhatsApp protocol.
 *
 * <p>This service is the main entry point for outgoing messages.  It
 * dispatches to the appropriate send path based on the chat JID type:
 * <ul>
 *   <li><b>User chats</b> ({@code user@s.whatsapp.net}, {@code user@lid})
 *       — per-device Signal encryption with chat fanout</li>
 *   <li><b>Group chats</b> ({@code group@g.us})
 *       — sender-key encryption, serialised per group</li>
 *   <li><b>Status updates</b> ({@code status@broadcast})
 *       — sender-key encryption to the status audience list</li>
 *   <li><b>Newsletters</b> ({@code newsletter@newsletter})
 *       — plaintext via SMAX RPC (no E2E encryption)</li>
 * </ul>
 *
 * <p>Peer protocol messages (app state sync, history sync errors) sent
 * to the user's own devices follow a dedicated path via
 * {@link #sendPeer(Jid, ChatMessageInfo)}.
 *
 * @apiNote WAWebSendMsgJob.encryptAndSendMsg: main entry point that
 * routes to encryptAndSendUserMsg (user) or encryptAndSendGroupMsg (group).
 * WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg: status sending.
 * WAWebNewsletterSendMessageQueryJob.querySendNewsletterMessage: newsletter sending.
 * WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg: peer message sending.
 */
public final class MessageSendingService {
    private final MessagePreparer preparer;
    private final MessageDedup messageDedup;
    private final UserMessageSender userSender;
    private final GroupMessageSender groupSender;
    private final StatusMessageSender statusSender;
    private final NewsletterMessageSender newsletterSender;
    private final PeerMessageSender peerSender;

    /**
     * Creates a new message sending service.
     *
     * @param client         the WhatsApp client for sending stanzas
     * @param encryption     the message encryption service
     * @param deviceService  the device service for device list resolution
     * @param abPropsService the AB props service for feature gating
     */
    public MessageSendingService(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService
    ) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(encryption, "encryption");
        Objects.requireNonNull(deviceService, "deviceService");
        Objects.requireNonNull(abPropsService, "abPropsService");

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
                botStanza, bizStanza, metaStanza, reportingStanza, ctwaStanza, tcTokenStanza);
        this.groupSender = new GroupMessageSender(client, encryption, deviceService, abPropsService, skDistribution, botStanza, bizStanza, metaStanza, reportingStanza);
        this.statusSender = new StatusMessageSender(client, encryption, deviceService, skDistribution, metaStanza, reportingStanza);
        this.newsletterSender = new NewsletterMessageSender(client);
        this.peerSender = new PeerMessageSender(client, encryption, deviceService);
    }

    /**
     * Prepares and sends a message to the specified chat.
     *
     * <p>This is the primary send API.  The raw {@link MessageContainer}
     * is prepared into a fully-populated {@link ChatMessageInfo} or
     * {@link NewsletterMessageInfo} before being dispatched to the
     * appropriate sender.  Preparation includes:
     * <ul>
     *   <li>Generating a unique message ID</li>
     *   <li>Generating the 32-byte messageSecret</li>
     *   <li>Generating random padding bytes</li>
     *   <li>Populating {@code DeviceContextInfo}</li>
     *   <li>Validating addon encryption state</li>
     *   <li>Auto-converting {@code ReactionMessage} to
     *       {@code EncryptedReactionMessage} for CAG groups</li>
     * </ul>
     *
     * @param chatJid   the recipient chat JID
     * @param container the raw message content
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     *
     * @apiNote WAWebOutgoingMessage.createOutgoingMessageProtobuf:
     * prepares the protobuf before passing to encryptAndSendMsg.
     */
    public AckResult send(Jid chatJid, MessageContainer container) {
        Objects.requireNonNull(chatJid, "chatJid");
        Objects.requireNonNull(container, "container");

        MessageInfo prepared;
        if (chatJid.hasServer(JidServer.newsletter())) {
            prepared = preparer.prepareNewsletter(chatJid, container);
        } else {
            prepared = preparer.prepareChat(chatJid, container);
        }

        return send(prepared);
    }

    /**
     * Sends a pre-prepared message directly without any preparation.
     *
     * <p>Use this overload when the caller has already constructed a
     * fully-populated {@link ChatMessageInfo} or
     * {@link NewsletterMessageInfo} with all required fields
     * (messageSecret, deviceInfo, encryption metadata, etc.).
     *
     * <p>The method dispatches to the appropriate send path based on the
     * message info type and the chat JID's server type.
     *
     * @param messageInfo the fully-prepared outgoing message
     * @return the server ack result
     * @throws NullPointerException                          if any argument is {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the chat JID type is unsupported
     *         or the message info type does not match the JID type
     *
     * @apiNote WAWebSendMsgJob.encryptAndSendMsg: routes based on JID type.
     */
    public AckResult send(MessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo");

        var parentId = messageInfo.key().chatJid();

        // WAWebMessageDedupUtils: check if this message ID is already in flight
        var messageId = messageInfo.key().id();
        if (messageDedup.isPending(messageId)) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Duplicate send for message ID: " + messageId, null);
        }

        messageDedup.add(messageId);
        try {
            return switch (messageInfo) {
                case ChatMessageInfo chatMessage when parentId.hasUserServer() || parentId.hasLidServer() ->
                    // WAWebSendMsgJob: to.isUser() → encryptAndSendUserMsg
                    userSender.send(parentId, chatMessage);
                case ChatMessageInfo chatMessage when parentId.hasGroupOrCommunityServer() ->
                    // WAWebSendMsgJob: to.isGroup() → encryptAndSendGroupMsg
                    groupSender.send(parentId, chatMessage);
                case ChatMessageInfo chatMessage when parentId.isStatusBroadcastAccount() ->
                    // WAWebEncryptAndSendStatusMsg: status@broadcast → encryptAndSendStatusMsg
                    statusSender.send(parentId, chatMessage);
                case NewsletterMessageInfo newsletterMessage when parentId.hasNewsletterServer() ->
                    // WAWebNewsletterSendMessageQueryJob: newsletter → querySendNewsletterMessage
                    newsletterSender.send(parentId, newsletterMessage);
                default -> throw new WhatsAppMessageException.Send.InvalidRecipient(
                    parentId, "Unsupported combination: " + messageInfo.getClass().getSimpleName()
                            + " with JID type " + parentId.server());
            };
        } finally {
            messageDedup.remove(messageId);
        }
    }

    /**
     * Sends a peer protocol message to the user's own primary device.
     *
     * <p>Peer messages include app state sync, key shares, and fatal
     * exception notifications.  They are encrypted per-device and tagged
     * with {@code category="peer"}.
     *
     * @param targetDevice the target device JID (typically the primary device)
     * @param messageInfo  the protocol message
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     *
     * @apiNote WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg: sends peer
     * messages via createUserDeviceMsgStanza with category="peer".
     */
    public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
        Objects.requireNonNull(targetDevice, "targetDevice");
        Objects.requireNonNull(messageInfo, "messageInfo");
        return peerSender.send(targetDevice, messageInfo);
    }
}
