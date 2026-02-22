package com.github.auties00.cobalt.message.receive.receipt;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;

import java.util.Objects;
import java.util.Optional;

/**
 * Sends receipt stanzas in response to incoming messages.
 *
 * <p>After an incoming message is decrypted and processed, the client must
 * send a receipt to the server indicating the result.  The receipt type and
 * content vary depending on the outcome:
 * <ul>
 *   <li><b>Delivery receipt:</b> Message was successfully decrypted and
 *       stored — acknowledges delivery to the sender.</li>
 *   <li><b>Retry receipt:</b> Decryption failed with a retryable error —
 *       requests the sender to re-encrypt and resend, optionally including
 *       a new prekey bundle for session re-establishment.</li>
 *   <li><b>Nack receipt:</b> Message was received but could not be parsed
 *       or validated — rejects the message with an error code.</li>
 * </ul>
 *
 * @apiNote WAWebHandleMsgSendReceipt.sendReceipt: routes to delivery,
 * retry, or nack based on the E2E processing result.
 * WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption: builds
 * the delivery receipt stanza.
 * WAWebSendRetryReceiptJob.sendRetryReceipt: builds the retry receipt
 * stanza with registration info and optional key bundle.
 * WAWebHandleMsgSendAck.sendAck / sendNack: sends ack/nack stanzas.
 */
public final class MessageReceiptHandler {
    private static final System.Logger LOGGER = System.getLogger(MessageReceiptHandler.class.getName());

    /**
     * The minimum retry count at which the prekey bundle is included
     * in the retry receipt for session re-establishment.
     *
     * @apiNote WAWebSendRetryReceiptJob: {@code d = 2}, the key section
     * is built when {@code retryCount >= d}.
     */
    private static final int RETRY_KEY_BUNDLE_THRESHOLD = 2;

    private final WhatsAppClient client;
    private final WhatsAppStore store;

    public MessageReceiptHandler(WhatsAppClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.store = client.store();
    }

    /**
     * Sends a delivery receipt for a successfully decrypted message.
     *
     * <p>The receipt type is determined by the message context:
     * <ul>
     *   <li>Peer messages → {@link MessageReceiptType#PEER}</li>
     *   <li>Messages from self (companion device) → {@link MessageReceiptType#SENDER}</li>
     *   <li>All other messages → {@link MessageReceiptType#DELIVERY}</li>
     * </ul>
     *
     * @param stanza the parsed incoming stanza
     * @param info   the successfully processed message info
     *
     * @apiNote WAWebHandleMsgSendReceipt.sendReceipt: for SUCCESS result,
     * dispatches to sendDeliveryReceiptsAfterDecryption which determines
     * the receipt type based on isPeer, isSender, and isInactive.
     * WAWebSendDeliveryReceiptJob: builds the receipt stanza with to,
     * participant, recipient, and type attributes.
     */
    public void sendDeliveryReceipt(MessageReceiveStanza stanza, MessageInfo info) {
        var receiptType = resolveDeliveryReceiptType(stanza, info);
        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", stanza.id())
                .attribute("type", receiptType.protocolValue())
                .attribute("to", stanza.chatJid())
                .attribute("participant",
                        resolveParticipantForReceipt(stanza).orElse(null));
        client.sendNodeWithNoResponse(receipt.build());
    }

    /**
     * Sends a retry receipt requesting the sender to re-encrypt and
     * resend the message.
     *
     * <p>The retry receipt contains the failure reason code and the
     * current retry count.  For retries after the first attempt, the
     * client also includes its registration ID and optionally a prekey
     * bundle so the sender can re-establish the Signal session.
     *
     * @param stanza      the parsed incoming stanza
     * @param retryReason the reason for the decryption failure
     * @param retryCount  the current retry attempt number (1-based)
     *
     * @apiNote WAWebSendRetryReceiptJob.sendRetryReceipt: builds the
     * retry receipt with registration info and key section.
     * The retry stanza structure:
     * {@code <receipt id="..." to="..." type="retry" participant="...">
     *   <retry v="1" count="..." id="..." t="..." error="..."/>
     *   <registration>registrationId</registration>
     *   [<keys>...</keys> if retryCount >= 2]
     * </receipt>}
     */
    public void sendRetryReceipt(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive.RetryReason retryReason,
            int retryCount
    ) {
        // WAWebSendRetryReceiptJob: skip retry for bot senders
        if (!stanza.chatJid().hasBotServer() && stanza.senderJid().hasBotServer()) {
            return;
        }

        var retryNode = new NodeBuilder()
                .description("retry")
                .attribute("v", "1")
                .attribute("count", retryCount)
                .attribute("id", stanza.id())
                .attribute("t", String.valueOf(stanza.timestamp().getEpochSecond()))
                .attribute("error", retryReason.protocolValue())
                .build();

        var registrationNode = new NodeBuilder()
                .description("registration")
                .content(SecureBytes.intToBytes(store.registrationId(), 4))
                .build();

        var keysNode = retryCount >= RETRY_KEY_BUNDLE_THRESHOLD
                ? buildKeyBundleNode()
                : null;

        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", stanza.id())
                .attribute("type", MessageReceiptType.RETRY.protocolValue())
                .attribute("to", stanza.chatJid())
                .attribute("participant",
                        resolveParticipantForReceipt(stanza).orElse(null))
                .content(retryNode, registrationNode, keysNode);
        client.sendNodeWithNoResponse(receipt.build());
    }

    /**
     * Sends a bot invoke response ack for messages received from bot
     * senders.
     *
     * <p>Bot messages get a special ack instead of a normal delivery receipt.
     *
     * @param stanza the parsed incoming stanza
     *
     * @apiNote WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks:
     * sends a bot-specific receipt for bot message delivery.
     */
    public void sendBotInvokeResponseAck(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        Jid to;
        Jid participant = null;

        if (chatJid.hasGroupOrCommunityServer() || chatJid.hasBroadcastServer()) {
            to = chatJid;
            participant = stanza.participant().orElse(null);
        } else {
            to = stanza.senderJid();
        }

        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", stanza.id())
                .attribute("type", "server-error")
                .attribute("to", to)
                .attribute("participant", participant);
        client.sendNodeWithNoResponse(receipt.build());
    }

    /**
     * Returns whether the message sender is a bot (requires a bot-specific
     * receipt rather than a normal delivery receipt).
     *
     * @param stanza the parsed incoming stanza
     * @return {@code true} if the sender is a bot
     *
     * @apiNote WAWebHandleMsgSendReceipt: checks
     * {@code !chat.isBot() && author.isBot()} to route to bot acks.
     */
    public boolean isBotSender(MessageReceiveStanza stanza) {
        return !stanza.chatJid().hasBotServer()
                && stanza.senderJid().hasBotServer();
    }

    /**
     * Sends a negative acknowledgment (NACK) for a message that failed
     * validation or protobuf parsing.
     *
     * @param stanza    the parsed incoming stanza
     * @param errorCode the error code to include in the NACK
     *
     * @apiNote WAWebHandleMsgSendAck.sendNack: sends an ack stanza with
     * an error attribute and optional meta child with failure_reason.
     */
    public void sendNackReceipt(MessageReceiveStanza stanza, int errorCode) {
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("class", "message")
                .attribute("to", stanza.chatJid())
                .attribute("participant",
                        resolveParticipantForReceipt(stanza).orElse(null))
                .attribute("type", stanza.stanzaType())
                .attribute("error", errorCode);
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Sends a plain ack for messages that don't need a full delivery
     * receipt (e.g. unavailable/fanout placeholders, media notify).
     *
     * @param stanza the parsed incoming stanza
     *
     * @apiNote WAWebHandleMsgSendAck.sendAck: sends an ack with class,
     * type, to, and optional participant.
     */
    public void sendAck(MessageReceiveStanza stanza) {
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("class", "message")
                .attribute("to", stanza.chatJid())
                .attribute("participant",
                        resolveParticipantForReceipt(stanza).orElse(null))
                .attribute("type", stanza.stanzaType());
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Builds a {@code <keys>} node containing the identity key, a one-time
     * prekey, the signed prekey, and the device-identity for session
     * re-establishment.
     *
     * @return the keys node, or {@code null} if the bundle cannot be built
     *
     * @apiNote WAWebSendRetryReceiptJob function h(): builds the key
     * section with type, identity, prekey, signed prekey, and
     * device-identity when retryCount &gt;= 2.
     */
    private Node buildKeyBundleNode() {
        try {
            var preKey = store.hasPreKeys()
                    ? store.preKeys().getFirst()
                    : SignalPreKeyPair.random(1);
            if (!store.hasPreKeys()) {
                store.addPreKey(preKey);
            }

            var typeNode = new NodeBuilder()
                    .description("type")
                    .content(new byte[]{SignalIdentityPublicKey.type()})
                    .build();
            var identityNode = new NodeBuilder()
                    .description("identity")
                    .content(store.identityKeyPair().publicKey().toEncodedPoint())
                    .build();

            var preKeyIdNode = new NodeBuilder()
                    .description("id")
                    .content(SecureBytes.intToBytes(preKey.id(), 3))
                    .build();
            var preKeyValueNode = new NodeBuilder()
                    .description("value")
                    .content(preKey.publicKey().toEncodedPoint())
                    .build();
            var preKeyNode = new NodeBuilder()
                    .description("key")
                    .content(preKeyIdNode, preKeyValueNode)
                    .build();

            var signedKeyPair = store.signedKeyPair();
            var skeyIdNode = new NodeBuilder()
                    .description("id")
                    .content(SecureBytes.intToBytes(signedKeyPair.id(), 3))
                    .build();
            var skeyValueNode = new NodeBuilder()
                    .description("value")
                    .content(signedKeyPair.publicKey().toEncodedPoint())
                    .build();
            var skeySigNode = new NodeBuilder()
                    .description("signature")
                    .content(signedKeyPair.signature())
                    .build();
            var skeyNode = new NodeBuilder()
                    .description("skey")
                    .content(skeyIdNode, skeyValueNode, skeySigNode)
                    .build();

            var deviceIdentityNode = store.signedDeviceIdentity()
                    .map(id -> new NodeBuilder()
                            .description("device-identity")
                            .content(SignedDeviceIdentitySpec.encode(id))
                            .build())
                    .orElse(null);

            return new NodeBuilder()
                    .description("keys")
                    .content(typeNode, identityNode, preKeyNode, skeyNode, deviceIdentityNode)
                    .build();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to build key bundle for retry receipt: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Determines the delivery receipt type based on the message context.
     *
     * @apiNote WAWebSendDeliveryReceiptJob: isPeer → PEER_MSG,
     * isSender (from self) → SENDER, active → DELIVERY,
     * inactive → INACTIVE.
     */
    private MessageReceiptType resolveDeliveryReceiptType(
            MessageReceiveStanza stanza,
            MessageInfo info
    ) {
        if (stanza.isPeer()) {
            return MessageReceiptType.PEER;
        }

        var selfJid = store.jid().orElse(null);
        if (selfJid != null && info instanceof ChatMessageInfo chatInfo) {
            var senderUser = stanza.senderJid().toUserJid();
            if (senderUser.equals(selfJid.toUserJid())) {
                return MessageReceiptType.SENDER;
            }
        }

        return MessageReceiptType.DELIVERY;
    }

    /**
     * Resolves the participant attribute for receipt stanzas.
     *
     * <p>For group, broadcast, and status messages the participant is
     * the sender's device JID.  For 1:1 chat messages there is no
     * participant.
     *
     * @apiNote WAWebSendDeliveryReceiptJob: participant is set for
     * group/broadcast messages, null for 1:1 chats.
     */
    private Optional<Jid> resolveParticipantForReceipt(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer() || chatJid.hasBroadcastServer()) {
            return stanza.participant();
        }
        return Optional.empty();
    }
}
