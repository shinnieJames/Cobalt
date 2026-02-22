package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.receive.MessageReceivingService;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.send.MessageSendingService;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;

import java.util.Objects;

/**
 * Unified service that coordinates both outgoing and incoming message
 * processing through the WhatsApp protocol.
 *
 * @apiNote WAWebSendMsgJob.encryptAndSendMsg: main outbound entry point.
 * WAWebCommsHandleMessagingStanza.handleMessagingStanza: main inbound
 * entry point that routes newsletter and E2E messages.
 */
public final class MessageService {
    /**
     * The service for processing outbound messages.
     */
    private final MessageSendingService sendingService;

    /**
     * The service for processing inbound messages.
     */
    private final MessageReceivingService receivingService;

    /**
     * Creates a new unified message service, assembling both the sending
     * and receiving pipelines from the provided dependencies.
     *
     * @param client         the WhatsApp client for sending stanzas
     * @param sessionCipher  the Signal session cipher for 1:1 encryption
     * @param groupCipher    the Signal group cipher for sender-key encryption
     * @param deviceService  the device service for device-list resolution
     * @param abPropsService the AB props service for feature gating
     */
    public MessageService(
            WhatsAppClient client,
            SignalSessionCipher sessionCipher,
            SignalGroupCipher groupCipher,
            DeviceService deviceService,
            ABPropsService abPropsService
    ) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(sessionCipher, "sessionCipher");
        Objects.requireNonNull(groupCipher, "groupCipher");
        Objects.requireNonNull(deviceService, "deviceService");
        Objects.requireNonNull(abPropsService, "abPropsService");

        var store = client.store();
        var encryption = new MessageEncryption(store, sessionCipher, groupCipher);
        var decryption = new MessageDecryption(store, sessionCipher, groupCipher);
        this.sendingService = new MessageSendingService(client, encryption, deviceService, abPropsService);
        this.receivingService = new MessageReceivingService(store, decryption);
    }

    /**
     * Prepares and sends a message to the specified chat.
     *
     * <p>The raw {@link MessageContainer} is prepared into a fully-populated
     * message info before being dispatched to the appropriate sender.
     *
     * @param chatJid   the recipient chat JID
     * @param container the raw message content
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     *
     * @see MessageSendingService#send(Jid, MessageContainer)
     */
    public AckResult send(Jid chatJid, MessageContainer container) {
        return sendingService.send(chatJid, container);
    }

    /**
     * Sends a pre-prepared message directly without any preparation.
     *
     * <p>Use this overload when the caller has already constructed a
     * fully-populated {@link ChatMessageInfo} or
     * {@link NewsletterMessageInfo}
     * with all required fields.
     *
     * @param messageInfo the fully-prepared outgoing message
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     *
     * @see MessageSendingService#send(MessageInfo)
     */
    public AckResult send(MessageInfo messageInfo) {
        return sendingService.send(messageInfo);
    }

    /**
     * Sends a peer protocol message to the user's own primary device.
     *
     * <p>Peer messages include app state sync, key shares, and fatal
     * exception notifications.
     *
     * @param targetDevice the target device JID (typically the primary device)
     * @param messageInfo  the protocol message
     * @return the server ack result
     * @throws NullPointerException if any argument is {@code null}
     *
     * @see MessageSendingService#sendPeer(Jid, ChatMessageInfo)
     */
    public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
        return sendingService.sendPeer(targetDevice, messageInfo);
    }

    /**
     * Processes an incoming {@code <message>} node, producing the
     * appropriate {@link MessageInfo} subtype.
     *
     * <p>Newsletter messages produce
     * {@link NewsletterMessageInfo};
     * all other messages go through E2E decryption and produce
     * {@link ChatMessageInfo}.  Returns {@code null} for unavailable
     * (fanout placeholder) messages.
     *
     * @param node the raw incoming {@code <message>} node
     * @return the processed message info, or {@code null} for unavailable
     *         messages
     * @throws com.github.auties00.cobalt.exception.WhatsAppMessageException.Receive
     *         if decryption or validation fails for E2E messages
     *
     * @see MessageReceivingService#process(Node)
     */
    public MessageInfo process(Node node) {
        return receivingService.process(node);
    }

    /**
     * Clears the pending-message dedup cache.
     *
     * <p>Should be called when offline delivery ends so that messages
     * received in a new session are not falsely considered duplicates.
     *
     * @see MessageReceivingService#clearPendingMessages()
     */
    public void clearPendingMessages() {
        receivingService.clearPendingMessages();
    }
}
