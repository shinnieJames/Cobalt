package com.github.auties00.cobalt.message.receive.receipt;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.FastRandomUtils;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;

import java.util.Objects;

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
 *   <li><b>Bot invoke response ack:</b> Message was received from a bot
 *       sender — sends a special ack with {@code class="message"} and
 *       {@code type="text"}.</li>
 * </ul>
 *
 * @implNote WAWebHandleMsgSendReceipt.sendReceipt: routes to delivery,
 * retry, or nack based on the E2E processing result.
 * WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption: builds
 * the delivery receipt stanza.
 * WAWebSendRetryReceiptJob.sendRetryReceipt: builds the retry receipt
 * stanza with registration info and optional key bundle.
 * WAWebHandleMsgSendAck.sendAck / sendNack: sends ack/nack stanzas.
 * WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks: sends bot-specific
 * ack stanzas.
 */
public final class MessageReceiptHandler {
    /**
     * Logger for this handler.
     *
     * @implNote NO_WA_BASIS
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceiptHandler.class.getName());

    /**
     * The minimum retry count at which the prekey bundle is included
     * in the retry receipt for session re-establishment.
     *
     * @implNote WAWebSendRetryReceiptJob: {@code d = 2}, the key section
     * is built when {@code retryCount >= d}.
     */
    private static final int RETRY_KEY_BUNDLE_THRESHOLD = 2;

    /**
     * The client used to send receipt stanzas.
     *
     * @implNote ADAPTED: WAWebDeprecatedSendIq.deprecatedCastStanza
     */
    private final WhatsAppClient client;

    /**
     * The store used to access registration info, keys, and JID state.
     *
     * @implNote ADAPTED: WAWebSignalProtocolStore, WAWebSignalStoreApi
     */
    private final WhatsAppStore store;

    /**
     * Constructs a new receipt handler.
     *
     * @param client the client used to send receipt stanzas
     * @implNote ADAPTED: constructor-based DI instead of module-level imports
     */
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
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt: delegates to the
     * three-argument form with hasInactiveMsg defaulting to false.
     */
    public void sendDeliveryReceipt(MessageReceiveStanza stanza, MessageInfo info) {
        sendDeliveryReceipt(stanza, info, false);
    }

    /**
     * Sends a delivery receipt for a successfully decrypted message.
     *
     * <p>The receipt type is determined by the message context:
     * <ul>
     *   <li>Peer messages → {@link MessageReceiptType#PEER}</li>
     *   <li>Messages from self (companion device) → {@link MessageReceiptType#SENDER}</li>
     *   <li>Inactive messages (when {@code hasInactiveMsg} is {@code true}
     *       and the sender is not self) → {@link MessageReceiptType#INACTIVE}</li>
     *   <li>All other messages → {@link MessageReceiptType#DELIVERY}</li>
     * </ul>
     *
     * @param stanza         the parsed incoming stanza
     * @param info           the successfully processed message info, or {@code null}
     *                       if the message could not produce a result but still
     *                       requires a delivery receipt
     * @param hasInactiveMsg whether the message processing produced an inactive
     *                       message flag (e.g. for muted/archived chats)
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt: for SUCCESS result,
     * dispatches to sendDeliveryReceiptsAfterDecryption which determines
     * the receipt type based on isPeer, isSender, hasInactiveMsg.
     * WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption:
     * builds the receipt stanza with to, participant, recipient, and type
     * attributes.
     */
    public void sendDeliveryReceipt(MessageReceiveStanza stanza, MessageInfo info, boolean hasInactiveMsg) {
        // WAWebSendDeliveryReceiptJob: determine isSender
        var from = resolveFrom(stanza); // WAWebMsgProcessingApiUtils.getFrom
        var participant = resolveReceiptParticipant(stanza); // WAWebHandleMsgSendReceipt: f variable
        var isSender = isSenderReceipt(from, participant); // WAWebSendDeliveryReceiptJob: l variable

        // WAWebSendDeliveryReceiptJob: determine receipt type
        var receiptType = resolveDeliveryReceiptType(stanza, isSender, hasInactiveMsg);

        // WAWebSendDeliveryReceiptJob: resolve recipient attribute
        // recipient: !isPeer && isSender && recipient ? USER_JID(recipient) : DROP
        var isPeer = stanza.isPeer();
        var recipientJid = resolveRecipientForReceipt(stanza);
        var shouldSetRecipient = !isPeer && isSender && recipientJid != null;

        // WAWebSendDeliveryReceiptJob: build receipt stanza
        var toJid = from.toUserJid(); // WAWebSendDeliveryReceiptJob: extractJidFromJidWithType(widToJidWithType(from))
        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("id", stanza.id())
                .attribute("type", receiptType.protocolValue()) // WAWebSendDeliveryReceiptJob: type attribute
                .attribute("to", toJid) // WAWebSendDeliveryReceiptJob: to attribute
                .attribute("participant", // WAWebSendDeliveryReceiptJob: participant attribute
                        (from.hasGroupOrCommunityServer() || from.hasBroadcastServer()) && participant != null
                                ? participant
                                : null)
                .attribute("recipient", // WAWebSendDeliveryReceiptJob: recipient attribute
                        shouldSetRecipient ? recipientJid.toUserJid() : null);
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
     * @implNote WAWebSendRetryReceiptJob.sendRetryReceipt: builds the
     * retry receipt with registration info and key section.
     * The retry stanza structure:
     * {@code <receipt id="..." to="..." type="retry" participant="..."
     *   category="..." recipient="...">
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
        // WAWebSendRetryReceiptJob: R = !to.isBot() && participant?.isBot()
        var from = resolveFrom(stanza); // WAWebSendRetryReceiptJob: to parameter = getFrom(info)
        var participant = resolveReceiptParticipant(stanza); // WAWebSendRetryReceiptJob: participant parameter
        if (!from.hasBotServer() && participant != null && participant.hasBotServer()) {
            return; // WAWebSendRetryReceiptJob: skip retry for bot senders
        }

        // WAWebSendRetryReceiptJob: build retry node
        var retryNode = new NodeBuilder()
                .description("retry")
                .attribute("v", "1")
                .attribute("count", retryCount)
                .attribute("id", stanza.id())
                .attribute("t", String.valueOf(stanza.timestamp().getEpochSecond()))
                .attribute("error", retryReason.protocolValue())
                .build();

        // WAWebSendRetryReceiptJob: build registration node
        var registrationNode = new NodeBuilder()
                .description("registration")
                .content(FastRandomUtils.intToBytes(store.registrationId(), 4))
                .build();

        // WAWebSendRetryReceiptJob: build keys node when retryCount >= threshold
        var keysNode = retryCount >= RETRY_KEY_BUNDLE_THRESHOLD
                ? buildKeyBundleNode()
                : null;

        // WAWebSendRetryReceiptJob: resolve to/participant/recipient/category attributes
        Jid toJid; // WAWebSendRetryReceiptJob: I variable
        Jid participantAttr = null; // WAWebSendRetryReceiptJob: k variable
        Jid recipientAttr = null; // WAWebSendRetryReceiptJob: E variable
        String categoryAttr = null; // WAWebSendRetryReceiptJob: L variable

        if (from.hasUserServer() || from.hasLidServer()) {
            // WAWebSendRetryReceiptJob: to.isUser() branch
            toJid = from; // DEVICE_JID(to)
            var selfJid = store.jid().orElse(null);
            if (selfJid != null && from.toUserJid().equals(selfJid.toUserJid())) {
                // WAWebSendRetryReceiptJob: isMeAccount(asUserWidOrThrow(to))
                if (stanza.isPeer()) {
                    categoryAttr = "peer"; // WAWebSendRetryReceiptJob: L = "peer"
                } else {
                    var recipientJid = resolveRecipientForReceipt(stanza);
                    if (recipientJid != null) {
                        recipientAttr = recipientJid.toUserJid(); // WAWebSendRetryReceiptJob: E = USER_JID(recipient)
                    }
                }
            }
        } else {
            // WAWebSendRetryReceiptJob: to is group/broadcast
            toJid = from.toUserJid(); // CHAT_JID(to)
            if (participant != null) {
                participantAttr = participant; // WAWebSendRetryReceiptJob: k = DEVICE_JID(participant)
            }
        }

        // WAWebSendRetryReceiptJob: build receipt stanza
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
     * <p>Bot messages get a special ack node (not a receipt node) with
     * {@code class="message"} and {@code type="text"}.  The addressing
     * differs from normal receipts:
     * <ul>
     *   <li>For 1:1 chats: {@code to = author, recipient = chat}</li>
     *   <li>For groups/broadcasts: {@code to = chat, participant = author}</li>
     * </ul>
     *
     * @param stanza the parsed incoming stanza
     * @implNote WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks:
     * builds an ack node with class="message" and type="text" for bot
     * message acknowledgment.
     * WAWebHandleMsgSendReceipt.sendReceipt: for CHAT type,
     * calls sendBotInvokeResponseAcks([id], author, chat, null);
     * for non-CHAT type, calls sendBotInvokeResponseAcks([id], chat, null, author).
     */
    public void sendBotInvokeResponseAck(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        Jid to; // WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks: t parameter
        Jid recipient = null; // WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks: n parameter
        Jid participantJid = null; // WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks: r parameter

        if (!chatJid.hasGroupOrCommunityServer() && !chatJid.hasBroadcastServer()) {
            // WAWebHandleMsgSendReceipt: CHAT type → (y = author, C = chat)
            // sendBotInvokeResponseAcks([id], author, chat, null)
            to = stanza.senderJid();
            recipient = chatJid.toUserJid();
        } else {
            // WAWebHandleMsgSendReceipt: non-CHAT type → (y = chat, S = author)
            // sendBotInvokeResponseAcks([id], chat, null, author)
            to = chatJid;
            participantJid = stanza.participant()
                    .map(Jid::toUserJid)
                    .orElse(null);
        }

        // WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks: builds ack node
        var ack = new NodeBuilder()
                .description("ack") // WAWebSendReceiptJobCommon.f: wap("ack", ...)
                .attribute("id", stanza.id())
                .attribute("to", to)
                .attribute("recipient", recipient) // WAWebSendReceiptJobCommon.f: recipient attribute
                .attribute("participant", participantJid) // WAWebSendReceiptJobCommon.f: participant attribute
                .attribute("class", "message") // WAWebSendReceiptJobCommon.f: class="message"
                .attribute("type", "text"); // WAWebSendReceiptJobCommon.f: type="text"
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Returns whether the message sender is a bot (requires a bot-specific
     * receipt rather than a normal delivery receipt).
     *
     * @param stanza the parsed incoming stanza
     * @return {@code true} if the sender is a bot
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt: checks
     * {@code !chat.isBot() && author.isBot()} to route to bot acks.
     */
    public boolean isBotSender(MessageReceiveStanza stanza) {
        return !stanza.chatJid().hasBotServer() // WAWebHandleMsgSendReceipt: !t.chat.isBot()
                && stanza.senderJid().hasBotServer(); // WAWebHandleMsgSendReceipt: t.author.isBot()
    }

    /**
     * Sends a negative acknowledgment (NACK) for a message that failed
     * validation or protobuf parsing, without a failure reason.
     *
     * <p>This is a convenience overload that delegates to
     * {@link #sendNackReceipt(MessageReceiveStanza, int, Integer)}
     * with {@code failureReason = null}.
     *
     * @param stanza    the parsed incoming stanza
     * @param errorCode the integer error code to include in the NACK
     * @implNote WAWebHandleMsgSendAck.sendNack: delegates to the
     * three-argument form with failureReason defaulting to null.
     */
    public void sendNackReceipt(MessageReceiveStanza stanza, int errorCode) {
        sendNackReceipt(stanza, errorCode, null);
    }

    /**
     * Sends a negative acknowledgment (NACK) for a message that failed
     * validation or protobuf parsing.
     *
     * <p>The NACK is an {@code <ack>} stanza with an {@code error} attribute
     * containing the integer error code.  For
     * {@code InvalidProtobuf} errors (code 491), a {@code <meta>} child
     * with a {@code failure_reason} attribute is included when available.
     *
     * @param stanza        the parsed incoming stanza
     * @param errorCode     the integer error code to include in the NACK
     * @param failureReason the optional failure reason for InvalidProtobuf
     *                      errors, or {@code null} if not applicable
     * @implNote WAWebHandleMsgSendAck.sendNack: sends an ack stanza with
     * an error attribute and optional meta child with failure_reason.
     * WAWebCreateNackFromStanza.NackReason: defines error code constants
     * (e.g. InvalidProtobuf=491, ParsingError=487).
     */
    public void sendNackReceipt(MessageReceiveStanza stanza, int errorCode, Integer failureReason) {
        // WAWebHandleMsgSendAck.sendNack: build optional meta child for InvalidProtobuf
        Node metaNode = null;
        if (errorCode == 491 && failureReason != null) {
            // WAWebHandleMsgSendAck: errorCode === NackReason.InvalidProtobuf && failureReason != null
            metaNode = new NodeBuilder()
                    .description("meta")
                    .attribute("failure_reason", failureReason)
                    .build();
        }

        // WAWebHandleMsgSendAck.sendNack: build ack stanza
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("class", "message")
                .attribute("to", resolveFrom(stanza)) // WAWebHandleMsgSendAck: to parameter
                .attribute("participant", // WAWebHandleMsgSendAck: participant parameter
                        resolveReceiptParticipant(stanza))
                .attribute("type", stanza.stanzaType()) // WAWebHandleMsgSendAck: type parameter
                .attribute("error", errorCode) // WAWebHandleMsgSendAck: error attribute
                .content(metaNode); // WAWebHandleMsgSendAck: optional meta child
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Sends a plain ack for messages that don't need a full delivery
     * receipt (e.g. unavailable/fanout placeholders, media notify).
     *
     * @param stanza the parsed incoming stanza
     * @implNote WAWebHandleMsgSendAck.sendAck: sends an ack with class,
     * type, to, and optional participant.
     */
    public void sendAck(MessageReceiveStanza stanza) {
        // WAWebHandleMsgSendAck.sendAck: build ack stanza
        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("class", "message")
                .attribute("to", resolveFrom(stanza)) // WAWebHandleMsgSendAck: to = from parameter
                .attribute("participant", // WAWebHandleMsgSendAck: participant parameter
                        resolveReceiptParticipant(stanza))
                .attribute("type", stanza.stanzaType()); // WAWebHandleMsgSendAck: type parameter
        client.sendNodeWithNoResponse(ack.build());
    }

    /**
     * Builds a {@code <keys>} node containing the identity key, a one-time
     * prekey, the signed prekey, and the device-identity for session
     * re-establishment.
     *
     * @return the keys node, or {@code null} if the bundle cannot be built
     * @implNote WAWebSendRetryReceiptJob function h(): builds the key
     * section with type, identity, prekey, signed prekey, and
     * device-identity when retryCount &gt;= 2.
     */
    private Node buildKeyBundleNode() {
        try {
            // WAWebSendRetryReceiptJob: getOrGenSinglePreKey
            var preKey = store.hasPreKeys()
                    ? store.preKeys().getFirst()
                    : SignalPreKeyPair.random(1);
            if (!store.hasPreKeys()) {
                store.addPreKey(preKey);
            }

            // WAWebSendRetryReceiptJob: wap("type", null, KEY_BUNDLE_TYPE)
            var typeNode = new NodeBuilder()
                    .description("type")
                    .content(new byte[]{SignalIdentityPublicKey.type()})
                    .build();

            // WAWebSendRetryReceiptJob: wap("identity", null, t.pubKey)
            var identityNode = new NodeBuilder()
                    .description("identity")
                    .content(store.identityKeyPair().publicKey().toEncodedPoint())
                    .build();

            // WAWebSendRetryReceiptJob: xmppPreKey(preKey)
            var preKeyIdNode = new NodeBuilder()
                    .description("id")
                    .content(FastRandomUtils.intToBytes(preKey.id(), 3))
                    .build();
            var preKeyValueNode = new NodeBuilder()
                    .description("value")
                    .content(preKey.publicKey().toEncodedPoint())
                    .build();
            var preKeyNode = new NodeBuilder()
                    .description("key")
                    .content(preKeyIdNode, preKeyValueNode)
                    .build();

            // WAWebSendRetryReceiptJob: xmppSignedPreKey(signedPreKey)
            var signedKeyPair = store.signedKeyPair();
            var skeyIdNode = new NodeBuilder()
                    .description("id")
                    .content(FastRandomUtils.intToBytes(signedKeyPair.id(), 3))
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

            // WAWebSendRetryReceiptJob: wap("device-identity", null, advEncodedIdentity)
            var deviceIdentityNode = store.signedDeviceIdentity()
                    .map(id -> new NodeBuilder()
                            .description("device-identity")
                            .content(ADVSignedDeviceIdentitySpec.encode(id))
                            .build())
                    .orElse(null);

            // WAWebSendRetryReceiptJob: wap("keys", null, type, identity, preKey, skey, deviceIdentity)
            return new NodeBuilder()
                    .description("keys")
                    .content(typeNode, identityNode, preKeyNode, skeyNode, deviceIdentityNode)
                    .build();
        } catch (Exception e) {
            // WAWebSendRetryReceiptJob: catch → LOG ERROR
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to build key bundle for retry receipt: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the {@code from} value used for receipt addressing.
     *
     * <p>For 1:1 chat messages (CHAT type), the {@code from} is the
     * sender's JID (author).  For group, broadcast, and status messages,
     * the {@code from} is the chat JID.
     *
     * @param stanza the parsed incoming stanza
     * @return the resolved from JID
     * @implNote WAWebMsgProcessingApiUtils.getFrom: returns
     * {@code info.author} for CHAT, {@code info.chat} for others.
     */
    private Jid resolveFrom(MessageReceiveStanza stanza) {
        // WAWebMsgProcessingApiUtils.getFrom: type === CHAT ? author : chat
        var chatJid = stanza.chatJid();
        if (!chatJid.hasGroupOrCommunityServer()
                && !chatJid.hasBroadcastServer()
                && !chatJid.isStatusBroadcastAccount()) {
            // CHAT type: from = author = senderJid (same as chatJid for 1:1)
            return stanza.senderJid();
        }
        // GROUP/BROADCAST/STATUS: from = chat
        return chatJid;
    }

    /**
     * Resolves the participant used for receipt stanzas.
     *
     * <p>For non-CHAT type messages, the participant is the sender's
     * device JID (the {@code participant} attribute from the stanza).
     * For CHAT type messages, participant is {@code null}.
     *
     * @param stanza the parsed incoming stanza
     * @return the participant JID, or {@code null} for 1:1 chats
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt:
     * {@code f = type === CHAT ? null : preMatChat ?? author}.
     * Since preMatChat is not commonly present, this resolves to the
     * stanza's participant attribute for non-CHAT types.
     */
    private Jid resolveReceiptParticipant(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()
                || chatJid.isStatusBroadcastAccount()) {
            // WAWebHandleMsgSendReceipt: non-CHAT → preMatChat ?? author
            return stanza.participant().orElse(null);
        }
        return null; // WAWebHandleMsgSendReceipt: CHAT → null
    }

    /**
     * Resolves the recipient JID for receipt stanzas.
     *
     * <p>The recipient is only relevant for CHAT-type messages sent
     * from our own account (companion device messages).  It is derived
     * from {@code originalBotRecipient}, {@code preMatChat}, or the
     * chat JID itself.
     *
     * @param stanza the parsed incoming stanza
     * @return the recipient JID, or {@code null} if not applicable
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt:
     * {@code p = originalBotRecipient ?? preMatChat ?? chat} when
     * type is CHAT and isMeAccount(author).
     */
    private Jid resolveRecipientForReceipt(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        // WAWebHandleMsgSendReceipt: only for CHAT type and self account
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()
                || chatJid.isStatusBroadcastAccount()) {
            return null;
        }

        var selfJid = store.jid().orElse(null);
        if (selfJid == null) {
            return null;
        }

        // WAWebHandleMsgSendReceipt: isMeAccount(t.author)
        if (!stanza.senderJid().toUserJid().equals(selfJid.toUserJid())) {
            return null;
        }

        // WAWebHandleMsgSendReceipt: p = originalBotRecipient ?? preMatChat ?? chat
        // Since we don't track originalBotRecipient/preMatChat, use chat JID
        return chatJid;
    }

    /**
     * Determines whether the receipt should be a "sender" type receipt.
     *
     * <p>A sender receipt is sent when the message is from our own account
     * (i.e. from a companion device), indicating that we (as sender) know
     * the message was delivered.
     *
     * @param from        the resolved from JID
     * @param participant the resolved participant JID (may be {@code null})
     * @return {@code true} if a sender receipt should be used
     * @implNote WAWebSendDeliveryReceiptJob:
     * {@code l = from.isUser() && isMeAccount(from) || participant != null && isMeAccount(participant)}
     */
    private boolean isSenderReceipt(Jid from, Jid participant) {
        var selfJid = store.jid().orElse(null);
        if (selfJid == null) {
            return false;
        }

        var selfUser = selfJid.toUserJid();

        // WAWebSendDeliveryReceiptJob: from.isUser() && isMeAccount(from)
        if ((from.hasUserServer() || from.hasLidServer())
                && from.toUserJid().equals(selfUser)) {
            return true;
        }

        // WAWebSendDeliveryReceiptJob: participant != null && isMeAccount(participant)
        if (participant != null && participant.toUserJid().equals(selfUser)) {
            return true;
        }

        return false;
    }

    /**
     * Determines the delivery receipt type based on the message context.
     *
     * @param stanza         the parsed incoming stanza
     * @param isSender       whether the message is from our own account
     * @param hasInactiveMsg whether the message processing produced an
     *                       inactive flag
     * @return the resolved receipt type
     * @implNote WAWebSendDeliveryReceiptJob: isPeer → PEER_MSG,
     * isSender → SENDER, isInactive (hasInactiveMsg && !isSender) → INACTIVE,
     * else (active) → DELIVERY (drop type attr).
     */
    private MessageReceiptType resolveDeliveryReceiptType(
            MessageReceiveStanza stanza,
            boolean isSender,
            boolean hasInactiveMsg
    ) {
        // WAWebSendDeliveryReceiptJob: isPeer → "peer_msg"
        if (stanza.isPeer()) {
            return MessageReceiptType.PEER;
        }

        // WAWebSendDeliveryReceiptJob: isSender → "sender"
        if (isSender) {
            return MessageReceiptType.SENDER;
        }

        // WAWebSendDeliveryReceiptJob: isInactive = hasInactiveMsg && !isSender → "inactive"
        // (isSender is false at this point)
        if (hasInactiveMsg) {
            return MessageReceiptType.INACTIVE;
        }

        // WAWebSendDeliveryReceiptJob: active → drop (null → no type attribute)
        return MessageReceiptType.DELIVERY;
    }
}
