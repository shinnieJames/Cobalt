package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.ack.CallAck;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.receive.MessageReceivingService;
import com.github.auties00.cobalt.message.send.MessageSendingService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;

/**
 * Facade that fans message traffic between the outbound send pipeline and the inbound receive
 * pipeline.
 *
 * <p>Code that wants to send or react to messages talks to this service instead of touching the
 * {@link MessageSendingService} and {@link MessageReceivingService} pair directly. The two
 * pipelines share the {@link LinkedWhatsAppClient#store() client store}, so the send and receive
 * sides observe a single source of truth for sessions, devices, and pending-message caches.
 *
 * @implSpec
 * Implementations must forward every outbound call to a {@link MessageSendingService} and every
 * inbound call to a {@link MessageReceivingService} backed by the same store, and must own no
 * additional message state of their own.
 */
public interface MessageService {
    /**
     * Sends a fresh outbound message to the given chat.
     *
     * <p>This allocates a message id, resolves the sender and recipient device
     * lists, encrypts the payload, ships the fanout, and blocks on the server
     * acknowledgment. This overload is for plain user-facing sends where the
     * caller does not need to pre-build a {@link MessageInfo}.
     *
     * @implSpec
     * Implementations must delegate to {@link MessageSendingService#send(Jid, MessageContainer)}.
     *
     * @param chatJid   the recipient chat JID
     * @param container the message payload
     * @return the server acknowledgment outcome
     * @throws NullPointerException if {@code chatJid} or {@code container} is
     *                              {@code null}
     * @see MessageSendingService#send(Jid, MessageContainer)
     */
    AckResult send(Jid chatJid, MessageContainer container);

    /**
     * Sends a pre-populated {@link MessageInfo} the caller has already keyed,
     * timestamped, and decorated with any extension metadata.
     *
     * <p>This overload is for messages prepared by the caller, for example when
     * rehydrating a stored draft or re-transmitting after a nack. The sending
     * service does not mutate the supplied {@link MessageInfo}; the same
     * instance can safely be passed again on a retry.
     *
     * @implSpec
     * Implementations must delegate to {@link MessageSendingService#send(MessageInfo)}.
     *
     * @param messageInfo the prepared outbound message, either a
     *                    {@link ChatMessageInfo} or a {@link NewsletterMessageInfo}
     * @return the server acknowledgment outcome
     * @throws NullPointerException if {@code messageInfo} is {@code null}
     * @see MessageSendingService#send(MessageInfo)
     */
    AckResult send(MessageInfo messageInfo);

    /**
     * Sends a peer protocol message to one of the current account's own linked
     * devices.
     *
     * <p>Peer messages never reach other users; they carry app-state sync
     * payloads, key share notifications, and fatal-exception reports between
     * linked devices of the same account. The {@code targetDevice} argument is
     * normally the account's primary device JID.
     *
     * @implSpec
     * Implementations must delegate to
     * {@link MessageSendingService#sendPeer(Jid, ChatMessageInfo)}.
     *
     * @param targetDevice the target device JID
     * @param messageInfo  the peer protocol message
     * @return the server acknowledgment outcome
     * @throws NullPointerException if {@code targetDevice} or
     *                              {@code messageInfo} is {@code null}
     * @see MessageSendingService#sendPeer(Jid, ChatMessageInfo)
     */
    AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo);

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
     * @implSpec
     * Implementations must delegate to {@link MessageReceivingService#process(Node)}.
     *
     * @param node the raw inbound {@code <message>} node
     * @return the processed {@link MessageInfo}, or {@code null} for
     *         unavailable fanout placeholders
     * @throws NullPointerException if {@code node} is {@code null}
     * @throws WhatsAppMessageException.Receive
     *         if decryption or validation fails for an encrypted payload
     * @see MessageReceivingService#process(Node)
     */
    MessageInfo process(Node node);

    /**
     * Builds and sends an outbound {@code <call><offer>} stanza to the peer's devices.
     *
     * <p>Owns the addressing-mode resolution (LID where the local user has migrated, PN
     * otherwise), the per-device list sync, the {@code ensureSessions} prekey fetch, the
     * per-device Signal envelope of the call-key plaintext, and the ADV-signed device-identity
     * attachment. Returns the parsed {@link CallAck} so the call layer can read the
     * {@link CallAck#relay() relay block} and drive the media plane on success, or surface the
     * {@link AckResult#error() error code} on rejection.
     *
     * @implSpec
     * Implementations must resolve both self and peer to the call's canonical addressing mode
     * before building the wire stanza; the WhatsApp server rejects mixed-addressing offers with
     * {@code error="439"}.
     *
     * @param peer    the peer user JID (PN or LID; will be resolved to the canonical mode)
     * @param callId  the call identifier
     * @param callKey the per-call shared key bytes to encrypt to every peer device
     * @param video   whether the call offers video
     * @return the parsed call ACK
     * @throws NullPointerException  if {@code peer}, {@code callId}, or {@code callKey} is
     *                               {@code null}
     * @throws IllegalStateException if the client is not logged in, or if no trusted-contact token
     *                               is available for the peer
     */
    CallAck sendCall(Jid peer, String callId, byte[] callKey, boolean video);

    /**
     * Sends a group-call offer to the group-call JID and returns the parsed ACK.
     *
     * <p>A group call offer differs from a 1:1 offer: it targets {@code <group-user>@call} (the WA
     * call domain) rather than a device JID, carries the group's {@code g.us} JID as the
     * {@code group-jid} attribute and a {@code <group_info>} child listing the participants, and
     * encrypts the call key to every device of every participant in the {@code <destination>} fanout.
     * The server fans the offer out to the group's members.
     *
     * @param group        the group JID (a {@code g.us} JID)
     * @param participants the participant user JIDs to invite
     * @param callId       the call identifier
     * @param callKey      the per-call shared key bytes to encrypt to every participant device
     * @param video        whether the call offers video
     * @return the parsed call ACK
     * @throws NullPointerException  if any argument is {@code null}
     * @throws IllegalStateException if the client is not logged in
     */
    CallAck sendGroupCall(Jid group, java.util.Collection<Jid> participants, String callId,
                          byte[] callKey, boolean video);

    /**
     * Decrypts a Signal-encrypted call payload into its plaintext bytes.
     *
     * <p>Used by the call layer's {@code <enc_rekey>} runtime to recover the
     * {@link com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload E2eRekeyPayload}
     * the peer published. The {@code encType} attribute is the wire-level Signal envelope
     * variant ({@code msg} or {@code pkmsg}) carried on the inbound {@code <enc>} child.
     *
     * @param senderJid  the device JID that authored the envelope
     * @param encType    the Signal envelope variant
     * @param ciphertext the Signal-encrypted bytes
     * @return the plaintext bytes
     * @throws NullPointerException             if any argument is {@code null}
     * @throws WhatsAppMessageException.Receive if decryption or validation fails
     */
    byte[] processCall(Jid senderJid, MessageEncryptionType encType, byte[] ciphertext);

    /**
     * Clears the pending-message deduplication cache held by the receiving
     * service.
     *
     * <p>This runs when the offline-delivery phase ends so stanzas replayed in a
     * new session are not mistakenly treated as duplicates of pre-reconnect
     * traffic.
     *
     * @implSpec
     * Implementations must delegate to {@link MessageReceivingService#clearPendingMessages()}.
     *
     * @see MessageReceivingService#clearPendingMessages()
     */
    void clearPendingMessages();
}
