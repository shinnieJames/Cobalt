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
import com.github.auties00.cobalt.model.message.MessageKey;
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
 * WAWebSendMsgJob.encryptAndSendKeyDistributionMsg: standalone sender-key
 * distribution to groups (no message content).
 * WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg: status sending.
 * WAWebNewsletterSendMessageQueryJob.querySendNewsletterMessage: newsletter sending.
 * WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg: peer message sending.
 */
public final class MessageSendingService {
    /**
     * Prepares raw {@link MessageContainer} instances into fully populated
     * {@link MessageInfo} objects before they enter the send pipeline.
     *
     * @implNote ADAPTED: WAWebOutgoingMessage.createOutgoingMessageProtobuf
     * is an inline function; Cobalt extracts it into a dedicated class.
     */
    private final MessagePreparer preparer;

    /**
     * Tracks in-flight message IDs to prevent duplicate sends.
     *
     * @implNote WAWebMessageDedupUtils: checks and registers message IDs
     * to prevent the same message from being sent concurrently.
     */
    private final MessageDedup messageDedup;

    /**
     * Sender for 1:1 user chats (PN and LID addressed).
     *
     * @implNote WAWebSendUserMsgJob.encryptAndSendUserMsg: per-device
     * Signal encryption with chat fanout.
     */
    private final UserMessageSender userSender;

    /**
     * Sender for group chats (sender-key encryption).
     *
     * @implNote WAWebSendGroupMsgJob.encryptAndSendGroupMsg: sender-key
     * encryption serialised per group.
     */
    private final GroupMessageSender groupSender;

    /**
     * Sender for status updates (sender-key encryption to status audience).
     *
     * @implNote WAWebEncryptAndSendStatusMsg.encryptAndSendStatusMsg:
     * sender-key encryption to the status audience list.
     */
    private final StatusMessageSender statusSender;

    /**
     * Sender for newsletter messages (plaintext via SMAX).
     *
     * @implNote WAWebNewsletterSendMessageQueryJob.querySendNewsletterMessage:
     * plaintext via SMAX RPC.
     */
    private final NewsletterMessageSender newsletterSender;

    /**
     * Sender for peer protocol messages (app state sync, key shares).
     *
     * @implNote WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg:
     * per-device Signal encryption with category="peer".
     */
    private final PeerMessageSender peerSender;

    /**
     * Creates a new message sending service.
     *
     * @param client         the WhatsApp client for sending stanzas
     * @param encryption     the message encryption service
     * @param deviceService  the device service for device list resolution
     * @param abPropsService the AB props service for feature gating
     *
     * @implNote ADAPTED: WAWebSendMsgJob uses module-level imports for all
     * dependencies; Cobalt uses constructor-based DI and creates all sub-senders.
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
     * @implNote ADAPTED: WAWebSendMsgJob.encryptAndSendMsg calls
     * WAWebOutgoingMessage.createOutgoingMessageProtobuf inline before
     * routing; Cobalt separates preparation (via {@link MessagePreparer})
     * from routing (via {@link #send(MessageInfo)}).
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
     * @implNote WAWebSendMsgJob.encryptAndSendMsg: validates {@code id}
     * and {@code to}, waits for offline delivery end, calls
     * {@code createOutgoingMessageProtobuf}, then routes to
     * {@code encryptAndSendUserMsg} (user) or
     * {@code encryptAndSendGroupMsg} (group) based on JID type.
     * Cobalt extends routing to also handle status and newsletter JIDs.
     */
    public AckResult send(MessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo");

        var messageId = messageInfo
                .key()
                .id()
                .orElseThrow(() -> new IllegalArgumentException("messageId is required for outgoing messages"));
        var parentJid = messageInfo.key()
                .parentJid()
                .orElseThrow(() -> new IllegalArgumentException("parentJid is required for outgoing messages"));

        // WAWebMessageDedupUtils: check if this message ID is already in flight
        if (messageDedup.isPending(messageId)) {
            throw new WhatsAppMessageException.Send.Unknown(
                    "Duplicate send for message ID: " + messageId, null);
        }

        messageDedup.add(messageId);
        try {
            return switch (messageInfo) {
                case ChatMessageInfo chatMessage when parentJid.hasUserServer() || parentJid.hasLidServer() ->
                    // WAWebSendMsgJob: to.isUser() → encryptAndSendUserMsg
                    userSender.send(parentJid, chatMessage);
                case ChatMessageInfo chatMessage when parentJid.hasGroupOrCommunityServer() ->
                    // WAWebSendMsgJob: to.isGroup() → encryptAndSendGroupMsg
                    groupSender.send(parentJid, chatMessage);
                case ChatMessageInfo chatMessage when parentJid.isStatusBroadcastAccount() ->
                    // WAWebEncryptAndSendStatusMsg: status@broadcast → encryptAndSendStatusMsg
                    statusSender.send(parentJid, chatMessage);
                case NewsletterMessageInfo newsletterMessage when parentJid.hasNewsletterServer() ->
                    // WAWebNewsletterSendMessageQueryJob: newsletter → querySendNewsletterMessage
                    newsletterSender.send(parentJid, newsletterMessage);
                default -> throw new WhatsAppMessageException.Send.InvalidRecipient(
                    parentJid, "Unsupported combination: " + messageInfo.getClass().getSimpleName()
                            + " with JID type " + parentJid.server());
            };
        } finally {
            messageDedup.remove(messageId);
        }
    }

    /**
     * Sends a standalone sender-key distribution to a group chat without
     * any message content.
     *
     * <p>This is used to pre-distribute sender keys to group participants
     * that do not yet possess them, independent of sending an actual
     * message.  It is the counterpart to
     * {@code WAWebSendKeyDistributionMsgAction.sendKeyDistributionMsg},
     * which is triggered by group membership changes or periodic key
     * refresh operations.
     *
     * <p>If the group JID is not a group, this method throws.  If all
     * devices already possess the sender key, this method returns without
     * sending anything.
     *
     * @param groupJid the group JID to distribute keys for
     * @param key      the message key containing the ID and remote JID
     * @throws NullPointerException                          if any argument is {@code null}
     * @throws WhatsAppMessageException.Send.InvalidRecipient if the JID is not a group
     *
     * @implNote WAWebSendMsgJob.encryptAndSendKeyDistributionMsg: validates
     * {@code id} and {@code remote}, checks {@code remote.isGroup()},
     * then delegates to
     * WAWebSendGroupKeyDistributionMsgJob.encryptAndSendGroupKeyDistributionMsg.
     * WAWebSendKeyDistributionMsgAction.sendKeyDistributionMsg: constructs
     * the MsgKey with the sender's JID, generates a new message ID, fetches
     * group metadata, and calls {@code encryptAndSendKeyDistributionMsg}.
     */
    public void sendKeyDistribution(Jid groupJid, MessageKey key) {
        Objects.requireNonNull(groupJid, "groupJid");
        Objects.requireNonNull(key, "key");

        // WAWebSendMsgJob.encryptAndSendKeyDistributionMsg: validate id
        var messageId = key.id()
                .orElseThrow(() -> new IllegalArgumentException(
                        "messageId is required for key distribution"));

        // WAWebSendMsgJob.encryptAndSendKeyDistributionMsg: validate remote
        // and check remote.isGroup()
        if (!groupJid.hasGroupOrCommunityServer()) {
            throw new WhatsAppMessageException.Send.InvalidRecipient(
                    groupJid, "Key distribution is only supported for group chats");
        }

        // WAWebSendMsgJob.encryptAndSendKeyDistributionMsg:
        // delegates to encryptAndSendGroupKeyDistributionMsg
        groupSender.sendKeyDistribution(groupJid, messageId);
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
     * @implNote WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg: sends peer
     * messages via createUserDeviceMsgStanza with category="peer".
     */
    public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
        Objects.requireNonNull(targetDevice, "targetDevice");
        Objects.requireNonNull(messageInfo, "messageInfo");
        return peerSender.send(targetDevice, messageInfo);
    }
}
