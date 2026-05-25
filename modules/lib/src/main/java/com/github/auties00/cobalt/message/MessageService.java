package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.media.transcode.MediaTranscoderService;
import com.github.auties00.cobalt.message.receive.MessageReceivingService;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.send.MessageSendingService;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;

import java.util.Objects;

/**
 * Fans message traffic between the outbound send pipeline and the inbound
 * receive pipeline behind a single facade.
 *
 * <p>Code that wants to send or react to messages talks to this class instead
 * of touching the {@link MessageSendingService} and
 * {@link MessageReceivingService} pair directly. The two sub-services are
 * assembled from the supplied collaborators in the constructor and share the
 * {@link WhatsAppClient#store() client store}, so the send and receive sides
 * observe a single source of truth for sessions, devices, and pending-message
 * caches.
 *
 * @implNote This implementation collapses WA Web's two separate entry points,
 * {@code WAWebSendMsgJob.encryptAndSendMsg} for outbound fanout and
 * {@code WAWebCommsHandleMessagingStanza.handleMessagingStanza} for inbound
 * dispatch, into one facade that owns no state of its own.
 */
public final class MessageService {
    /**
     * Holds the outbound pipeline owning device fetch, fanout, encryption, and
     * stanza emission.
     */
    private final MessageSendingService sendingService;

    /**
     * Holds the inbound pipeline owning stanza parsing, Signal decryption, and
     * {@link MessageInfo} construction.
     */
    private final MessageReceivingService receivingService;

    /**
     * Wires the send and receive pipelines from the supplied collaborators.
     *
     * <p>The two pipelines share the {@link WhatsAppClient#store() client store}
     * and the {@link SignalSessionCipher}/{@link SignalGroupCipher} pair so
     * encrypted state stays consistent across both directions of traffic. The
     * ciphers passed in must be backed by the same store as
     * {@link WhatsAppClient#store()}; otherwise the send and receive sides see
     * different Signal session records.
     *
     * @param client              the {@link WhatsAppClient} used to send
     *                            stanzas and to register inbound stanza
     *                            handlers
     * @param sessionCipher       the {@link SignalSessionCipher} used for
     *                            one-to-one encryption and decryption
     * @param groupCipher         the {@link SignalGroupCipher} used for
     *                            sender-key fanout in group threads
     * @param deviceService       the {@link DeviceService} consulted to
     *                            resolve per-user device lists before each
     *                            fanout
     * @param lidMigrationService the {@link LidMigrationService} that gates
     *                            the PN-to-LID stanza rewrite in the
     *                            user-chat send path
     * @param abPropsService      the {@link ABPropsService} consulted to
     *                            gate optional protocol behaviour
     * @param wamService             the {@link WamService} forwarded to
     *                               the sending pipeline for end-to-end
     *                               telemetry events
     * @param mediaTranscoderService the {@link MediaTranscoderService}
     *                               threaded into the sending pipeline
     *                               for link-preview decoration
     * @throws NullPointerException if any argument is {@code null}
     */
    public MessageService(
            WhatsAppClient client,
            SignalSessionCipher sessionCipher,
            SignalGroupCipher groupCipher,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            WamService wamService,
            MediaTranscoderService mediaTranscoderService
    ) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(sessionCipher, "sessionCipher");
        Objects.requireNonNull(groupCipher, "groupCipher");
        Objects.requireNonNull(deviceService, "deviceService");
        Objects.requireNonNull(lidMigrationService, "lidMigrationService");
        Objects.requireNonNull(abPropsService, "abPropsService");
        Objects.requireNonNull(wamService, "wamService");
        Objects.requireNonNull(mediaTranscoderService, "mediaTranscoderService");

        var store = client.store();
        var encryption = new MessageEncryption(store, sessionCipher, groupCipher);
        var decryption = new MessageDecryption(store, sessionCipher, groupCipher);
        this.sendingService = new MessageSendingService(client, encryption, deviceService, lidMigrationService, abPropsService, wamService, mediaTranscoderService);
        this.receivingService = new MessageReceivingService(store, decryption);
    }

    /**
     * Sends a fresh outbound message to the given chat.
     *
     * <p>This allocates a message id, resolves the sender and recipient device
     * lists, encrypts the payload, ships the fanout, and blocks on the server
     * acknowledgment. This overload is for plain user-facing sends where the
     * caller does not need to pre-build a {@link MessageInfo}.
     *
     * @param chatJid   the recipient chat JID
     * @param container the message payload
     * @return the server acknowledgment outcome
     * @throws NullPointerException if {@code chatJid} or {@code container} is
     *                              {@code null}
     * @see MessageSendingService#send(Jid, MessageContainer)
     */
    public AckResult send(Jid chatJid, MessageContainer container) {
        return sendingService.send(chatJid, container);
    }

    /**
     * Sends a pre-populated {@link MessageInfo} the caller has already keyed,
     * timestamped, and decorated with any extension metadata.
     *
     * <p>This overload is for messages prepared by the caller, for example when
     * rehydrating a stored draft or re-transmitting after a nack. The sending
     * service does not mutate the supplied {@link MessageInfo}; the same
     * instance can safely be passed again on a retry.
     *
     * @param messageInfo the prepared outbound message, either a
     *                    {@link ChatMessageInfo} or a {@link NewsletterMessageInfo}
     * @return the server acknowledgment outcome
     * @throws NullPointerException if {@code messageInfo} is {@code null}
     * @see MessageSendingService#send(MessageInfo)
     */
    public AckResult send(MessageInfo messageInfo) {
        return sendingService.send(messageInfo);
    }

    /**
     * Sends a peer protocol message to one of the current account's own linked
     * devices.
     *
     * <p>Peer messages never reach other users; they carry app-state sync
     * payloads, key share notifications, and fatal-exception reports between
     * linked devices of the same account. The {@code targetDevice} argument is
     * normally the account's primary device JID.
     *
     * @param targetDevice the target device JID
     * @param messageInfo  the peer protocol message
     * @return the server acknowledgment outcome
     * @throws NullPointerException if {@code targetDevice} or
     *                              {@code messageInfo} is {@code null}
     * @see MessageSendingService#sendPeer(Jid, ChatMessageInfo)
     */
    public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
        return sendingService.sendPeer(targetDevice, messageInfo);
    }

    /**
     * Processes a single inbound {@code <message>} stanza and returns the typed
     * {@link MessageInfo} ready for consumption.
     *
     * <p>Newsletter messages are returned as {@link NewsletterMessageInfo};
     * every other shape goes through the Signal decryption pipeline and is
     * returned as {@link ChatMessageInfo}. Fanout placeholders for messages the
     * server failed to deliver produce a {@code null} return so the caller can
     * distinguish them from genuine payloads.
     *
     * @param node the raw inbound {@code <message>} node
     * @return the processed {@link MessageInfo}, or {@code null} for
     *         unavailable fanout placeholders
     * @throws NullPointerException if {@code node} is {@code null}
     * @throws com.github.auties00.cobalt.exception.WhatsAppMessageException.Receive
     *         if decryption or validation fails for an encrypted payload
     * @see MessageReceivingService#process(Node)
     */
    public MessageInfo process(Node node) {
        return receivingService.process(node);
    }

    /**
     * Clears the pending-message deduplication cache held by the receiving
     * service.
     *
     * <p>This runs when the offline-delivery phase ends so stanzas replayed in a
     * new session are not mistakenly treated as duplicates of pre-reconnect
     * traffic.
     *
     * @see MessageReceivingService#clearPendingMessages()
     */
    public void clearPendingMessages() {
        receivingService.clearPendingMessages();
    }
}
