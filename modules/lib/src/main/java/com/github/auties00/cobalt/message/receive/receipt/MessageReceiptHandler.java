package com.github.auties00.cobalt.message.receive.receipt;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;

import java.util.Objects;

/**
 * Sends receipt stanzas in response to incoming messages.
 *
 * <p>After an incoming message is decrypted and processed, the client
 * must send a receipt back to the server indicating the outcome. The
 * receipt type and content vary depending on the result of processing:
 * <ul>
 *   <li><b>Delivery receipt</b>: the message was successfully
 *       decrypted and stored; acknowledges delivery to the sender.</li>
 *   <li><b>Retry receipt</b>: decryption failed with a retryable
 *       error; asks the sender to re-encrypt and resend, optionally
 *       bundling a fresh prekey for session re-establishment.</li>
 *   <li><b>Nack receipt</b>: the message was received but could not
 *       be parsed or validated; rejects the message with an error
 *       code.</li>
 *   <li><b>Bot invoke response ack</b>: the message was received
 *       from a bot sender; sends a specialised ack with
 *       {@code class="message"} and {@code type="text"}.</li>
 *   <li><b>Plain ack</b>: for messages that do not need a full
 *       delivery receipt (unavailable placeholders, media notify).</li>
 * </ul>
 *
 * @implNote WAWebHandleMsgSendReceipt.sendReceipt: routes to delivery,
 * retry, or nack based on the E2E processing result.
 * WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption:
 * builds the delivery receipt stanza.
 * WAWebSendRetryReceiptJob.sendRetryReceipt: builds the retry receipt
 * stanza with registration info and optional key bundle.
 * WAWebHandleMsgSendAck.sendAck/sendNack: sends the ack/nack stanzas.
 * WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks: sends the
 * bot-specific ack stanzas.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgSendReceipt")
@WhatsAppWebModule(moduleName = "WAWebSendDeliveryReceiptJob")
@WhatsAppWebModule(moduleName = "WAWebSendRetryReceiptJob")
@WhatsAppWebModule(moduleName = "WAWebHandleMsgSendAck")
@WhatsAppWebModule(moduleName = "WAWebSendReceiptJobCommon")
public final class MessageReceiptHandler {
    /**
     * Logger for diagnostic messages during receipt construction and
     * sending.
     *
     * @implNote WAWebHandleMsgSendReceipt uses WALogger; Cobalt uses
     * {@code System.Logger} instead.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceiptHandler.class.getName());

    /**
     * The retry count at which the prekey bundle is included in the
     * retry receipt for session re-establishment.
     *
     * @implNote WAWebSendRetryReceiptJob: {@code d = 2}, the key
     * section is built when {@code retryCount >= d}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendRetryReceiptJob", exports = "sendRetryReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int RETRY_KEY_BUNDLE_THRESHOLD = 2;

    /**
     * The client used to send receipt stanzas over the wire.
     *
     * @implNote WAWebDeprecatedSendIq.deprecatedCastStanza is the
     * shared transport used by every receipt job; Cobalt injects the
     * {@link WhatsAppClient} through this field.
     */
    private final WhatsAppClient client;

    /**
     * The store used to access registration info, keys, and JID
     * state.
     *
     * @implNote WAWebSignalProtocolStore and WAWebSignalStoreApi
     * provide the registration id and key material; Cobalt accesses
     * them through the injected {@link WhatsAppStore}.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a new receipt handler.
     *
     * @param client the client used to send receipt stanzas
     *
     * @throws NullPointerException if {@code client} is {@code null}
     *
     * @implNote Constructor-based DI replaces module-level imports in
     * WAWebHandleMsgSendReceipt, WAWebSendDeliveryReceiptJob,
     * WAWebSendRetryReceiptJob, and WAWebHandleMsgSendAck.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageReceiptHandler(WhatsAppClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.store = client.store();
    }

    /**
     * Sends a delivery receipt for a successfully decrypted message,
     * assuming the message is active (not inactive).
     *
     * <p>This is a convenience overload that delegates to
     * {@link #sendDeliveryReceipt(MessageReceiveStanza, MessageInfo, boolean)}
     * with {@code hasInactiveMsg = false}.
     *
     * @param stanza the parsed incoming stanza
     * @param info   the successfully processed message info, or {@code null}
     *
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt: delegates to
     * the three-argument form with hasInactiveMsg defaulting to false.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendDeliveryReceipt(MessageReceiveStanza stanza, MessageInfo info) {
        sendDeliveryReceipt(stanza, info, false);
    }

    /**
     * Sends a delivery receipt for a successfully decrypted message.
     *
     * <p>The receipt type is determined by the message context:
     * <ul>
     *   <li>Peer messages use {@link MessageReceiptType#PEER}.</li>
     *   <li>Messages from self (companion device) use
     *       {@link MessageReceiptType#SENDER}.</li>
     *   <li>Inactive messages (when {@code hasInactiveMsg} is
     *       {@code true} and the sender is not self) use
     *       {@link MessageReceiptType#INACTIVE}.</li>
     *   <li>All other messages use {@link MessageReceiptType#DELIVERY}.</li>
     * </ul>
     *
     * @param stanza         the parsed incoming stanza
     * @param info           the successfully processed message info, or {@code null}
     * @param hasInactiveMsg whether processing produced an inactive-message flag
     *
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt: for the
     * SUCCESS result, dispatches to sendDeliveryReceiptsAfterDecryption
     * which determines the receipt type from isPeer, isSender, and
     * hasInactiveMsg. WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption:
     * builds the receipt stanza with to, participant, recipient, and
     * type attributes.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendDeliveryReceiptJob", exports = "sendDeliveryReceiptsAfterDecryption",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendDeliveryReceipt(MessageReceiveStanza stanza, MessageInfo info, boolean hasInactiveMsg) {
        // WAWebMsgProcessingApiUtils.getFrom
        // Resolves the from JID used for every receipt addressing decision
        var from = resolveFrom(stanza);

        // WAWebHandleMsgSendReceipt.sendReceipt
        // Resolves the participant attribute from the stanza for non-CHAT receipts
        var participant = resolveReceiptParticipant(stanza);

        // WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption
        // Determines whether this is a sender receipt for our own companion device
        var isSender = isSenderReceipt(from, participant);

        // WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption
        // Picks the concrete receipt type based on peer/sender/inactive status
        var receiptType = resolveDeliveryReceiptType(stanza, isSender, hasInactiveMsg);

        // WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption
        // Resolves the optional recipient attribute only for non-peer self-sent CHAT receipts
        var isPeer = stanza.isPeer();
        var recipientJid = resolveRecipientForReceipt(stanza);
        var shouldSetRecipient = !isPeer && isSender && recipientJid != null;

        // WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption
        // Builds the receipt stanza with to/type/participant/recipient attributes
        var toJid = from.toUserJid();
        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", stanza.id())
                .attribute("type", receiptType.protocolValue())
                .attribute("to", toJid)
                .attribute("participant",
                        (from.hasGroupOrCommunityServer() || from.hasBroadcastServer()) && participant != null
                                ? participant
                                : null)
                .attribute("recipient",
                        shouldSetRecipient ? recipientJid.toUserJid() : null);
        client.sendNodeWithNoResponse(receipt.build());
    }

    /**
     * Sends a retry receipt requesting the sender to re-encrypt and
     * resend the message.
     *
     * <p>The retry receipt carries the failure reason code and the
     * current retry count. For retries after the first attempt, the
     * client also includes its registration id and optionally a
     * prekey bundle so the sender can re-establish the Signal
     * session.
     *
     * @param stanza      the parsed incoming stanza
     * @param retryReason the reason for the decryption failure
     * @param retryCount  the current retry attempt number (1-based)
     *
     * @implNote WAWebSendRetryReceiptJob.sendRetryReceipt: builds the
     * retry receipt with registration info and key section. The retry
     * stanza structure is:
     * {@code <receipt id="..." to="..." type="retry" participant="..."
     *   category="..." recipient="...">
     *   <retry v="1" count="..." id="..." t="..." error="..."/>
     *   <registration>registrationId</registration>
     *   [<keys>...</keys> if retryCount >= 2]
     * </receipt>}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendRetryReceiptJob", exports = "sendRetryReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendRetryReceipt(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive.RetryReason retryReason,
            int retryCount
    ) {
        // WAWebSendRetryReceiptJob.sendRetryReceipt
        // Skips retry when we are talking to a bot via a non-bot chat (no useful retry semantics)
        var from = resolveFrom(stanza);
        var participant = resolveReceiptParticipant(stanza);
        if (!from.hasBotServer() && participant != null && participant.hasBotServer()) {
            return;
        }

        // WAWebSendRetryReceiptJob.sendRetryReceipt
        // Builds the inner retry node with protocol version, count, id, timestamp, and error reason
        var retryNode = new NodeBuilder()
                .description("retry")
                .attribute("v", "1")
                .attribute("count", retryCount)
                .attribute("id", stanza.id())
                .attribute("t", String.valueOf(stanza.timestamp().getEpochSecond()))
                .attribute("error", retryReason.protocolValue())
                .build();

        // WAWebSendRetryReceiptJob.sendRetryReceipt
        // Builds the registration node carrying our 32-bit registrationId encoded as 4 bytes
        var registrationNode = new NodeBuilder()
                .description("registration")
                .content(DataUtils.intToBytes(store.registrationId(), 4))
                .build();

        // WAWebSendRetryReceiptJob.sendRetryReceipt
        // Builds the optional key bundle node when the retry count crosses the threshold
        var keysNode = retryCount >= RETRY_KEY_BUNDLE_THRESHOLD
                ? buildKeyBundleNode()
                : null;

        // WAWebSendRetryReceiptJob.sendRetryReceipt
        // Resolves to/participant/recipient/category attributes based on whether we are the sender
        Jid toJid;
        Jid participantAttr = null;
        Jid recipientAttr = null;
        String categoryAttr = null;

        if (from.hasUserServer() || from.hasLidServer()) {
            // WAWebSendRetryReceiptJob.sendRetryReceipt user branch
            // For 1:1 messages we address the sender device directly
            toJid = from;
            var selfJid = store.jid().orElse(null);
            if (selfJid != null && from.toUserJid().equals(selfJid.toUserJid())) {
                // WAWebSendRetryReceiptJob.sendRetryReceipt
                // Self-sent messages receive a peer category or a recipient attribute based on isPeer
                if (stanza.isPeer()) {
                    categoryAttr = "peer";
                } else {
                    var recipientJid = resolveRecipientForReceipt(stanza);
                    if (recipientJid != null) {
                        recipientAttr = recipientJid.toUserJid();
                    }
                }
            }
        } else {
            // WAWebSendRetryReceiptJob.sendRetryReceipt group/broadcast branch
            // Addresses the chat and carries the participant attribute
            toJid = from.toUserJid();
            if (participant != null) {
                participantAttr = participant;
            }
        }

        // WAWebSendRetryReceiptJob.sendRetryReceipt
        // Assembles the outer receipt stanza with the retry/registration/keys children
        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", stanza.id())
                .attribute("type", MessageReceiptType.RETRY.protocolValue())
                .attribute("to", toJid)
                .attribute("participant", participantAttr)
                .attribute("recipient", recipientAttr)
                .attribute("category", categoryAttr)
                .content(retryNode, registrationNode, keysNode);
        client.sendNodeWithNoResponse(receipt.build());
    }

    /**
     * Sends a bot invoke response ack for messages received from bot
     * senders.
     *
     * <p>Bot messages receive a specialised ack node (not a receipt
     * node) with {@code class="message"} and {@code type="text"}.
     * The addressing differs from normal receipts:
     * <ul>
     *   <li>For 1:1 chats: {@code to = author, recipient = chat}.</li>
     *   <li>For groups/broadcasts: {@code to = chat, participant = author}.</li>
     * </ul>
     *
     * @param stanza the parsed incoming stanza
     *
     * @implNote WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks:
     * builds an ack node with {@code class="message"} and
     * {@code type="text"} for bot message acknowledgment.
     * WAWebHandleMsgSendReceipt.sendReceipt: for the CHAT type calls
     * sendBotInvokeResponseAcks([id], author, chat, null); for
     * non-CHAT types calls sendBotInvokeResponseAcks([id], chat,
     * null, author).
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "sendBotInvokeResponseAcks",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendBotInvokeResponseAck(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        Jid to;
        Jid recipient = null;
        Jid participantJid = null;

        // WAWebHandleMsgSendReceipt.sendReceipt
        // Discriminates CHAT vs group/broadcast addressing for the bot ack
        if (!chatJid.hasGroupOrCommunityServer() && !chatJid.hasBroadcastServer()) {
            // CHAT type: to = author (sender), recipient = chat
            to = stanza.senderJid();
            recipient = chatJid.toUserJid();
        } else {
            // Non-CHAT type: to = chat, participant = author
            to = chatJid;
            participantJid = stanza.participant()
                    .map(Jid::toUserJid)
                    .orElse(null);
        }

        // WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks
        // Builds the ack node with class="message" and type="text"
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("to", to)
                .attribute("recipient", recipient)
                .attribute("participant", participantJid)
                .attribute("class", "message")
                .attribute("type", "text");
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Returns whether the message sender is a bot that requires a
     * bot-specific receipt rather than a normal delivery receipt.
     *
     * @param stanza the parsed incoming stanza
     * @return {@code true} if the sender is a bot
     *
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt: checks
     * {@code !chat.isBot() && author.isBot()} to route to bot acks.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isBotSender(MessageReceiveStanza stanza) {
        // WAWebHandleMsgSendReceipt.sendReceipt
        // Returns true when the chat is not itself a bot but the author is
        return !stanza.chatJid().hasBotServer()
                && stanza.senderJid().hasBotServer();
    }

    /**
     * Sends a negative acknowledgment (NACK) for a message that
     * failed validation or protobuf parsing, without a failure
     * reason.
     *
     * <p>Convenience overload that delegates to
     * {@link #sendNackReceipt(MessageReceiveStanza, int, Integer)}
     * with {@code failureReason = null}.
     *
     * @param stanza    the parsed incoming stanza
     * @param errorCode the integer error code to include in the NACK
     *
     * @implNote WAWebHandleMsgSendAck.sendNack: delegates to the
     * three-argument form with failureReason defaulting to null.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendNack",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendNackReceipt(MessageReceiveStanza stanza, int errorCode) {
        sendNackReceipt(stanza, errorCode, null);
    }

    /**
     * Sends a negative acknowledgment (NACK) for a message that
     * failed validation or protobuf parsing.
     *
     * <p>The NACK is an {@code <ack>} stanza with an {@code error}
     * attribute carrying the integer error code. For
     * {@code InvalidProtobuf} errors (code 491), a {@code <meta>}
     * child with a {@code failure_reason} attribute is included when
     * available.
     *
     * @param stanza        the parsed incoming stanza
     * @param errorCode     the integer error code to include in the NACK
     * @param failureReason the optional failure reason for InvalidProtobuf errors
     *
     * @implNote WAWebHandleMsgSendAck.sendNack: sends an ack stanza
     * with an error attribute and optional meta child with
     * failure_reason. The attribute order on the {@code <ack>} stanza
     * mirrors WA Web's object-literal order:
     * {@code id, class, from, to, participant, type, error}.
     * WAWebCreateNackFromStanza.NackReason: defines error code
     * constants (InvalidProtobuf=491, ParsingError=487).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendNack",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendNackReceipt(MessageReceiveStanza stanza, int errorCode, Integer failureReason) {
        // WAWebHandleMsgSendAck.sendNack
        // Builds the optional meta child for InvalidProtobuf errors carrying a failure_reason
        Node metaNode = null;
        if (errorCode == 491 && failureReason != null) {
            metaNode = new NodeBuilder()
                    .description("meta")
                    .attribute("failure_reason", failureReason)
                    .build();
        }

        // WAWebHandleMsgSendAck.sendNack
        // Builds the ack stanza with the error code and optional meta child.
        // Attribute order mirrors WA Web: id, class, from, to, participant, type, error.
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("class", "message")
                // WAWebHandleMsgSendAck.sendNack: from = DEVICE_JID(getMeDevicePnOrThrow_DO_NOT_USE())
                .attribute("from", store.jid().orElse(null))
                .attribute("to", resolveFrom(stanza))
                .attribute("participant",
                        resolveReceiptParticipant(stanza))
                .attribute("type", stanza.stanzaType())
                .attribute("error", errorCode)
                .content(metaNode);
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Sends a plain ack for messages that do not need a full delivery
     * receipt (for example unavailable/fanout placeholders or media
     * notify).
     *
     * @param stanza the parsed incoming stanza
     *
     * @implNote WAWebHandleMsgSendAck.sendAck: sends an ack with
     * id, class, from, to, participant, and type attributes (in that
     * exact object-literal order). The {@code from} attribute is the
     * client's own device JID.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendAck(MessageReceiveStanza stanza) {
        // WAWebHandleMsgSendAck.sendAck
        // Builds the plain ack stanza with class/from/to/participant/type attributes.
        // Attribute order mirrors WA Web: id, class, from, to, participant, type.
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("class", "message")
                // WAWebHandleMsgSendAck.sendAck: from = DEVICE_JID(getMeDevicePnOrThrow_DO_NOT_USE())
                .attribute("from", store.jid().orElse(null))
                .attribute("to", resolveFrom(stanza))
                .attribute("participant",
                        resolveReceiptParticipant(stanza))
                .attribute("type", stanza.stanzaType());
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Builds a {@code <keys>} node carrying the identity key, a
     * one-time prekey, the signed prekey, and the device-identity
     * for session re-establishment.
     *
     * @return the keys node, or {@code null} if the bundle cannot be
     *         built
     *
     * @implNote WAWebSendRetryReceiptJob function h(): builds the
     * key section with type, identity, prekey, signed prekey, and
     * device-identity when retryCount is at or above the threshold.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendRetryReceiptJob", exports = "sendRetryReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Node buildKeyBundleNode() {
        try {
            // WAWebSendRetryReceiptJob.getOrGenSinglePreKey
            // Uses an existing prekey if available, otherwise generates one on the fly
            var preKey = store.hasPreKeys()
                    ? store.preKeys().getFirst()
                    : SignalPreKeyPair.random(1);
            if (!store.hasPreKeys()) {
                store.addPreKey(preKey);
            }

            // WAWebSendRetryReceiptJob
            // Builds the type node carrying the curve type byte
            var typeNode = new NodeBuilder()
                    .description("type")
                    .content(new byte[]{SignalIdentityPublicKey.type()})
                    .build();

            // WAWebSendRetryReceiptJob
            // Builds the identity node carrying our long-term identity public key
            var identityNode = new NodeBuilder()
                    .description("identity")
                    .content(store.identityKeyPair().publicKey().toEncodedPoint())
                    .build();

            // WAWebSendRetryReceiptJob.xmppPreKey
            // Builds the prekey node with id and value children
            var preKeyIdNode = new NodeBuilder()
                    .description("id")
                    .content(DataUtils.intToBytes(preKey.id(), 3))
                    .build();
            var preKeyValueNode = new NodeBuilder()
                    .description("value")
                    .content(preKey.publicKey().toEncodedPoint())
                    .build();
            var preKeyNode = new NodeBuilder()
                    .description("key")
                    .content(preKeyIdNode, preKeyValueNode)
                    .build();

            // WAWebSendRetryReceiptJob.xmppSignedPreKey
            // Builds the signed prekey node with id, value, and signature children
            var signedKeyPair = store.signedKeyPair();
            var skeyIdNode = new NodeBuilder()
                    .description("id")
                    .content(DataUtils.intToBytes(signedKeyPair.id(), 3))
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

            // WAWebSendRetryReceiptJob
            // Builds the device-identity node carrying the ADV-signed identity proof
            var deviceIdentityNode = store.signedDeviceIdentity()
                    .map(id -> new NodeBuilder()
                            .description("device-identity")
                            .content(ADVSignedDeviceIdentitySpec.encode(id))
                            .build())
                    .orElse(null);

            // WAWebSendRetryReceiptJob
            // Assembles the keys node containing type, identity, prekey, skey, and device-identity
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
     * Resolves the {@code from} value used for receipt addressing.
     *
     * <p>For 1:1 chat messages (CHAT type) the {@code from} is the
     * sender's JID (author). For group, broadcast, and status
     * messages the {@code from} is the chat JID.
     *
     * @param stanza the parsed incoming stanza
     * @return the resolved from JID
     *
     * @implNote WAWebMsgProcessingApiUtils.getFrom: returns
     * {@code info.author} for CHAT and {@code info.chat} for others.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingApiUtils", exports = "getFrom",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveFrom(MessageReceiveStanza stanza) {
        // WAWebMsgProcessingApiUtils.getFrom
        // Returns the sender JID for CHAT messages
        var chatJid = stanza.chatJid();
        if (!chatJid.hasGroupOrCommunityServer()
                && !chatJid.hasBroadcastServer()
                && !chatJid.isStatusBroadcastAccount()) {
            return stanza.senderJid();
        }

        // WAWebMsgProcessingApiUtils.getFrom
        // Returns the chat JID for GROUP/BROADCAST/STATUS messages
        return chatJid;
    }

    /**
     * Resolves the participant attribute used for receipt stanzas.
     *
     * <p>For non-CHAT messages the participant is the sender's
     * device JID (the {@code participant} attribute from the stanza).
     * For CHAT messages the participant is {@code null}.
     *
     * @param stanza the parsed incoming stanza
     * @return the participant JID, or {@code null} for 1:1 chats
     *
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt:
     * {@code f = type === CHAT ? null : preMatChat ?? author}.
     * Since preMatChat is not commonly present, this resolves to the
     * stanza's participant attribute for non-CHAT types.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveReceiptParticipant(MessageReceiveStanza stanza) {
        // WAWebHandleMsgSendReceipt.sendReceipt
        // Returns the stanza's participant for group/broadcast/status messages, null otherwise
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()
                || chatJid.isStatusBroadcastAccount()) {
            return stanza.participant().orElse(null);
        }
        return null;
    }

    /**
     * Resolves the recipient JID for receipt stanzas.
     *
     * <p>The recipient is only relevant for CHAT-type messages sent
     * from our own account (companion-device messages). It is
     * derived from {@code originalBotRecipient}, {@code preMatChat},
     * or the chat JID itself.
     *
     * @param stanza the parsed incoming stanza
     * @return the recipient JID, or {@code null} if not applicable
     *
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt:
     * {@code p = originalBotRecipient ?? preMatChat ?? chat} when
     * the type is CHAT and isMeAccount(author).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveRecipientForReceipt(MessageReceiveStanza stanza) {
        // WAWebHandleMsgSendReceipt.sendReceipt
        // Recipient is only applicable to CHAT messages
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()
                || chatJid.isStatusBroadcastAccount()) {
            return null;
        }

        // WAWebHandleMsgSendReceipt.sendReceipt
        // Requires a logged-in self JID to compare against
        var selfJid = store.jid().orElse(null);
        if (selfJid == null) {
            return null;
        }

        // WAWebHandleMsgSendReceipt.sendReceipt
        // Only populated when the author is our own account
        if (!stanza.senderJid().toUserJid().equals(selfJid.toUserJid())) {
            return null;
        }

        // WAWebHandleMsgSendReceipt.sendReceipt
        // Returns the chat JID since we do not track originalBotRecipient/preMatChat
        return chatJid;
    }

    /**
     * Returns whether the receipt should use the {@code sender}
     * type.
     *
     * <p>A sender receipt is sent when the message is from our own
     * account (from a companion device), indicating that we as
     * sender know the message was delivered.
     *
     * @param from        the resolved from JID
     * @param participant the resolved participant JID, or {@code null}
     * @return {@code true} if the sender receipt type should be used
     *
     * @implNote WAWebSendDeliveryReceiptJob:
     * {@code l = from.isUser() && isMeAccount(from) || participant != null && isMeAccount(participant)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendDeliveryReceiptJob", exports = "sendDeliveryReceiptsAfterDecryption",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isSenderReceipt(Jid from, Jid participant) {
        // WAWebSendDeliveryReceiptJob
        // Requires a logged-in self JID
        var selfJid = store.jid().orElse(null);
        if (selfJid == null) {
            return false;
        }

        var selfUser = selfJid.toUserJid();

        // WAWebSendDeliveryReceiptJob
        // Sender receipt when from is a user JID matching us
        if ((from.hasUserServer() || from.hasLidServer())
                && from.toUserJid().equals(selfUser)) {
            return true;
        }

        // WAWebSendDeliveryReceiptJob
        // Sender receipt when a participant is present and matches us (group/broadcast from ourself)
        if (participant != null && participant.toUserJid().equals(selfUser)) {
            return true;
        }

        return false;
    }

    /**
     * Determines the delivery receipt type based on the message
     * context.
     *
     * @param stanza         the parsed incoming stanza
     * @param isSender       whether the message is from our own account
     * @param hasInactiveMsg whether processing produced an inactive flag
     * @return the resolved receipt type
     *
     * @implNote WAWebSendDeliveryReceiptJob: isPeer -&gt; PEER_MSG,
     * isSender -&gt; SENDER,
     * isInactive (hasInactiveMsg and not isSender) -&gt; INACTIVE,
     * otherwise active -&gt; DELIVERY (drop type attr).
     */
    @WhatsAppWebExport(moduleName = "WAWebSendDeliveryReceiptJob", exports = "sendDeliveryReceiptsAfterDecryption",
            adaptation = WhatsAppAdaptation.DIRECT)
    private MessageReceiptType resolveDeliveryReceiptType(
            MessageReceiveStanza stanza,
            boolean isSender,
            boolean hasInactiveMsg
    ) {
        // WAWebSendDeliveryReceiptJob
        // Peer category maps to the PEER type (protocol value peer_msg)
        if (stanza.isPeer()) {
            return MessageReceiptType.PEER;
        }

        // WAWebSendDeliveryReceiptJob
        // Self-origin maps to the SENDER type
        if (isSender) {
            return MessageReceiptType.SENDER;
        }

        // WAWebSendDeliveryReceiptJob
        // Inactive messages from remote senders map to the INACTIVE type
        if (hasInactiveMsg) {
            return MessageReceiptType.INACTIVE;
        }

        // WAWebSendDeliveryReceiptJob
        // Active delivery drops the type attribute via the DELIVERY null protocol value
        return MessageReceiptType.DELIVERY;
    }
}
