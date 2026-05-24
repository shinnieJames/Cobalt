package com.github.auties00.cobalt.message.receipt;

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
 * Builds and dispatches the {@code <receipt>}, {@code <ack>}, and bot
 * {@code <ack>}-with-{@code class="message"} stanzas that respond to every incoming
 * message processed by the receive pipeline.
 *
 * @apiNote
 * Embedders never instantiate this class directly; it is constructed by the client
 * during session setup and invoked by the receive orchestrator after the
 * {@link com.github.auties00.cobalt.message.receive.crypto.MessageDecryptionResult}
 * has been resolved. The {@link MessageReceiptType} associated with each entry point
 * is what the server uses to fan out the delivery/retry status across the conversation
 * participants.
 *
 * @implNote
 * This implementation centralises addressing decisions in three private resolvers
 * ({@link #resolveFrom(MessageReceiveStanza)},
 * {@link #resolveReceiptParticipant(MessageReceiveStanza)},
 * {@link #resolveRecipientForReceipt(MessageReceiveStanza)}) so each public entry
 * point only worries about the receipt-type selection; WhatsApp Web instead
 * threads {@code from}, {@code participant}, and {@code recipient} through every
 * call site of {@code sendDeliveryReceiptsAfterDecryption} and
 * {@code sendRetryReceipt}.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgSendReceipt")
@WhatsAppWebModule(moduleName = "WAWebSendDeliveryReceiptJob")
@WhatsAppWebModule(moduleName = "WAWebSendRetryReceiptJob")
@WhatsAppWebModule(moduleName = "WAWebHandleMsgSendAck")
@WhatsAppWebModule(moduleName = "WAWebSendReceiptJobCommon")
public final class MessageReceiptHandler {
    /**
     * Logger used for the prekey-bundle build-failure trace inside
     * {@link #buildKeyBundleNode()}.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageReceiptHandler.class.getName());

    /**
     * Retry count from which the {@code <keys>} bundle is attached to the retry
     * receipt.
     *
     * @apiNote
     * Mirrors WhatsApp Web's {@code d=2} threshold inside
     * {@code WAWebSendRetryReceiptJob.sendRetryReceipt}: the first retry is sent
     * without the bundle because the sender is expected to still hold the original
     * session; the second and subsequent retries carry the bundle so the sender can
     * re-establish.
     */
    @WhatsAppWebExport(moduleName = "WAWebSendRetryReceiptJob", exports = "sendRetryReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int RETRY_KEY_BUNDLE_THRESHOLD = 2;

    /**
     * Client used to dispatch the constructed receipt stanzas over the socket.
     */
    private final WhatsAppClient client;

    /**
     * Central session store used to look up the local PN/LID, signed prekey pair,
     * one-time prekeys, and the device-identity blob attached to the prekey bundle.
     */
    private final WhatsAppStore store;

    /**
     * Constructs a receipt handler bound to the given client.
     *
     * @apiNote
     * Constructor injection; the client also supplies the store via
     * {@link WhatsAppClient#store()} so the handler does not need a separate store
     * argument.
     *
     * @param client the client used to dispatch receipt stanzas
     * @throws NullPointerException if {@code client} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageReceiptHandler(WhatsAppClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.store = client.store();
    }

    /**
     * Sends a delivery receipt for a successfully processed message, assuming the
     * message is active.
     *
     * @apiNote
     * Convenience overload that delegates to
     * {@link #sendDeliveryReceipt(MessageReceiveStanza, MessageInfo, boolean)} with
     * {@code hasInactiveMsg} set to {@code false}; suitable for normal active-chat
     * deliveries where the inactive-chat fast path does not apply.
     *
     * @param stanza the parsed incoming stanza
     * @param info   the processed message info, or {@code null} when no info was
     *               produced
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendDeliveryReceipt(MessageReceiveStanza stanza, MessageInfo info) {
        sendDeliveryReceipt(stanza, info, false);
    }

    /**
     * Sends a delivery receipt for a successfully processed message.
     *
     * @apiNote
     * Mirrors WhatsApp Web's
     * {@code WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption}; the
     * resulting {@link MessageReceiptType} is selected from the stanza addressing:
     * <ul>
     *   <li>peer messages -> {@link MessageReceiptType#PEER}.</li>
     *   <li>self-sent messages from a companion device -> {@link MessageReceiptType#SENDER}.</li>
     *   <li>inactive messages where the sender is not self ->
     *       {@link MessageReceiptType#INACTIVE}.</li>
     *   <li>everything else -> {@link MessageReceiptType#DELIVERY} (the type
     *       attribute is dropped).</li>
     * </ul>
     *
     * @param stanza         the parsed incoming stanza
     * @param info           the processed message info, or {@code null} when no info
     *                       was produced
     * @param hasInactiveMsg whether processing reported an inactive-message flag
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendDeliveryReceiptJob", exports = "sendDeliveryReceiptsAfterDecryption",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendDeliveryReceipt(MessageReceiveStanza stanza, MessageInfo info, boolean hasInactiveMsg) {
        var from = resolveFrom(stanza);
        var participant = resolveReceiptParticipant(stanza);
        var isSender = isSenderReceipt(from, participant);
        var receiptType = resolveDeliveryReceiptType(stanza, isSender, hasInactiveMsg);

        var isPeer = stanza.isPeer();
        var recipientJid = resolveRecipientForReceipt(stanza);
        var shouldSetRecipient = !isPeer && isSender && recipientJid != null;

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
     * Sends a retry receipt that asks the sender to re-encrypt and re-send a
     * payload that could not be decrypted.
     *
     * @apiNote
     * Mirrors WhatsApp Web's {@code WAWebSendRetryReceiptJob.sendRetryReceipt}; the
     * receipt carries the failure reason and the retry count, and from the
     * {@value #RETRY_KEY_BUNDLE_THRESHOLD}th attempt onward attaches a fresh
     * {@code <keys>} bundle so the sender can re-establish the Signal session. The
     * call is skipped when the chat is a non-bot user but the participant is a bot
     * (the WA Web {@code E} short-circuit), because the bot has no useful retry
     * semantics on that channel.
     *
     * @param stanza      the parsed incoming stanza
     * @param retryReason the failure reason carried in the {@code error} attribute
     * @param retryCount  the 1-based current retry attempt
     */
    @WhatsAppWebExport(moduleName = "WAWebSendRetryReceiptJob", exports = "sendRetryReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendRetryReceipt(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive.RetryReason retryReason,
            int retryCount
    ) {
        var from = resolveFrom(stanza);
        var participant = resolveReceiptParticipant(stanza);
        if (!from.hasBotServer() && participant != null && participant.hasBotServer()) {
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
                .content(DataUtils.intToBytes(store.registrationId(), 4))
                .build();

        var keysNode = retryCount >= RETRY_KEY_BUNDLE_THRESHOLD
                ? buildKeyBundleNode()
                : null;

        Jid toJid;
        Jid participantAttr = null;
        Jid recipientAttr = null;
        String categoryAttr = null;

        if (from.hasUserServer() || from.hasLidServer()) {
            toJid = from;
            var selfJid = store.jid().orElse(null);
            if (selfJid != null && from.toUserJid().equals(selfJid.toUserJid())) {
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
            toJid = from.toUserJid();
            if (participant != null) {
                participantAttr = participant;
            }
        }

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
     * Sends the specialised {@code <ack>} stanza used to acknowledge a bot
     * invoke-response message.
     *
     * @apiNote
     * Mirrors WhatsApp Web's {@code WAWebSendReceiptJobCommon.sendBotInvokeResponseAcks};
     * bot replies use an {@code <ack class="message" type="text">} rather than a
     * {@code <receipt>}, with the addressing flipped for 1:1 chats so that
     * {@code to} is the bot author and {@code recipient} is the chat. Group and
     * broadcast bot replies use {@code to = chat} and {@code participant = author}.
     *
     * @param stanza the parsed incoming stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebSendReceiptJobCommon", exports = "sendBotInvokeResponseAcks",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendBotInvokeResponseAck(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        Jid to;
        Jid recipient = null;
        Jid participantJid = null;

        if (!chatJid.hasGroupOrCommunityServer() && !chatJid.hasBroadcastServer()) {
            to = stanza.senderJid();
            recipient = chatJid.toUserJid();
        } else {
            to = chatJid;
            participantJid = stanza.participant()
                    .map(Jid::toUserJid)
                    .orElse(null);
        }

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
     * Returns whether the message originates from a bot sender that requires a
     * bot-specific receipt rather than a normal delivery receipt.
     *
     * @apiNote
     * Used by the orchestrator to choose between
     * {@link #sendDeliveryReceipt(MessageReceiveStanza, MessageInfo)} and
     * {@link #sendBotInvokeResponseAck(MessageReceiveStanza)}; mirrors the
     * {@code y} predicate inside WhatsApp Web's
     * {@code WAWebHandleMsgSendReceipt.sendReceipt} that checks for a non-bot chat
     * paired with a bot author.
     *
     * @param stanza the parsed incoming stanza
     * @return {@code true} when the chat is not on the bot server but the sender is
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isBotSender(MessageReceiveStanza stanza) {
        return !stanza.chatJid().hasBotServer()
                && stanza.senderJid().hasBotServer();
    }

    /**
     * Sends a NACK for a message that failed validation or protobuf parsing,
     * without a failure reason.
     *
     * @apiNote
     * Convenience overload that delegates to
     * {@link #sendNackReceipt(MessageReceiveStanza, int, Integer)} with
     * {@code failureReason = null}; suitable for the
     * {@link com.github.auties00.cobalt.message.receive.crypto.MessageDecryptionResult#PARSE_ERROR}
     * path where WhatsApp Web's {@code NackReason.ParsingError} has no extra
     * {@code failure_reason} payload.
     *
     * @param stanza    the parsed incoming stanza
     * @param errorCode the NACK error code carried in the {@code error} attribute
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendNack",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendNackReceipt(MessageReceiveStanza stanza, int errorCode) {
        sendNackReceipt(stanza, errorCode, null);
    }

    /**
     * Sends a NACK for a message that failed validation or protobuf parsing.
     *
     * @apiNote
     * Mirrors WhatsApp Web's {@code WAWebHandleMsgSendAck.sendNack}; the resulting
     * stanza is an {@code <ack class="message">} with an {@code error} attribute.
     * For {@code InvalidProtobuf} (code 491) the stanza additionally carries a
     * {@code <meta failure_reason=...>} child when the caller supplies a reason,
     * matching WA Web's {@code u} helper inside the same module.
     *
     * @param stanza        the parsed incoming stanza
     * @param errorCode     the NACK error code carried in the {@code error}
     *                      attribute
     * @param failureReason the failure-reason payload for InvalidProtobuf errors,
     *                      or {@code null} to omit the {@code <meta>} child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendNack",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void sendNackReceipt(MessageReceiveStanza stanza, int errorCode, Integer failureReason) {
        Node metaNode = null;
        if (errorCode == 491 && failureReason != null) {
            metaNode = new NodeBuilder()
                    .description("meta")
                    .attribute("failure_reason", failureReason)
                    .build();
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", stanza.id())
                .attribute("class", "message")
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
     * Builds the {@code <keys>} bundle attached to a retry receipt from the
     * {@value #RETRY_KEY_BUNDLE_THRESHOLD}th attempt onward.
     *
     * @apiNote
     * Mirrors WhatsApp Web's local {@code h} helper inside
     * {@code WAWebSendRetryReceiptJob}: the bundle includes the registered prekey
     * type, the local identity key, a one-time prekey, the signed prekey, and the
     * ADV-signed device identity; the sender uses these to install a fresh
     * Signal session before re-encrypting.
     *
     * @implNote
     * This implementation lazily provisions a one-time prekey when the local store
     * has none: a random {@link SignalPreKeyPair} is generated and persisted so the
     * next retry attempt sees a stable bundle. A build failure (for example a
     * missing identity-key pair) is logged and the method returns {@code null} so
     * the retry receipt still goes out without the bundle, matching WA Web's
     * {@code y} catch branch.
     *
     * @return the {@code <keys>} node, or {@code null} when the bundle cannot be
     *         built
     */
    @WhatsAppWebExport(moduleName = "WAWebSendRetryReceiptJob", exports = "sendRetryReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
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

            var deviceIdentityNode = store.signedDeviceIdentity()
                    .map(id -> new NodeBuilder()
                            .description("device-identity")
                            .content(ADVSignedDeviceIdentitySpec.encode(id))
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
     * Resolves the JID that the {@code from} attribute (or, for receipt stanzas,
     * the {@code to} attribute) should carry.
     *
     * @apiNote
     * Mirrors WhatsApp Web's {@code WAWebMsgProcessingApiUtils.getFrom}; for 1:1
     * chat messages the value is the sender's JID, for group, community,
     * broadcast, and status messages the value is the chat JID.
     *
     * @param stanza the parsed incoming stanza
     * @return the resolved JID
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingApiUtils", exports = "getFrom",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveFrom(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        if (!chatJid.hasGroupOrCommunityServer()
                && !chatJid.hasBroadcastServer()
                && !chatJid.isStatusBroadcastAccount()) {
            return stanza.senderJid();
        }

        return chatJid;
    }

    /**
     * Resolves the participant attribute placed on receipt stanzas.
     *
     * @apiNote
     * Group, community, broadcast, and status receipts carry the sender device JID
     * in the {@code participant} attribute so the server can route the receipt
     * back to the originating device; 1:1 chats omit the attribute because the
     * {@code to} JID already identifies the recipient.
     *
     * @param stanza the parsed incoming stanza
     * @return the participant JID, or {@code null} for 1:1 chats
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveReceiptParticipant(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()
                || chatJid.isStatusBroadcastAccount()) {
            return stanza.participant().orElse(null);
        }
        return null;
    }

    /**
     * Resolves the recipient attribute placed on delivery and retry receipts for
     * companion-device messages.
     *
     * @apiNote
     * The recipient is only meaningful for 1:1-style chat messages sent from the
     * user's own account (i.e. echoed from a companion device); group, community,
     * broadcast, and status receipts return {@code null} so the attribute is
     * dropped.
     *
     * @implNote
     * This implementation collapses WhatsApp Web's three-way resolution chain
     * ({@code originalBotRecipient} -> {@code preMatChat} -> {@code chat}) to a
     * single {@code chat} fallback because Cobalt does not yet model the
     * {@code originalBotRecipient} or {@code preMatChat} stanza metadata; the
     * collapse is safe for non-bot self-echoes but loses fidelity on bot replies
     * routed via a different recipient.
     *
     * @param stanza the parsed incoming stanza
     * @return the recipient JID, or {@code null} when the receipt should omit the
     *         attribute
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendReceipt", exports = "sendReceipt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Jid resolveRecipientForReceipt(MessageReceiveStanza stanza) {
        var chatJid = stanza.chatJid();
        if (chatJid.hasGroupOrCommunityServer()
                || chatJid.hasBroadcastServer()
                || chatJid.isStatusBroadcastAccount()) {
            return null;
        }

        var selfJid = store.jid().orElse(null);
        if (selfJid == null) {
            return null;
        }

        if (!stanza.senderJid().toUserJid().equals(selfJid.toUserJid())) {
            return null;
        }

        // TODO: track WAWebMsgProcessingApiUtils originalBotRecipient and preMatChat
        //       to mirror the full WA Web recipient chain for companion bot replies.
        return chatJid;
    }

    /**
     * Returns whether the receipt should use the {@link MessageReceiptType#SENDER}
     * type.
     *
     * @apiNote
     * Mirrors WhatsApp Web's {@code d} predicate inside
     * {@code WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption}: the
     * sender type is selected when the {@code from} JID is the local account's
     * user, or when the participant JID is the local account's user, so a
     * companion-device echo loops back to the primary as a sender confirmation.
     *
     * @param from        the resolved {@code from} JID
     * @param participant the resolved participant JID, or {@code null} for 1:1
     * @return {@code true} when the sender receipt type should be used
     */
    @WhatsAppWebExport(moduleName = "WAWebSendDeliveryReceiptJob", exports = "sendDeliveryReceiptsAfterDecryption",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isSenderReceipt(Jid from, Jid participant) {
        var selfJid = store.jid().orElse(null);
        if (selfJid == null) {
            return false;
        }

        var selfUser = selfJid.toUserJid();

        if ((from.hasUserServer() || from.hasLidServer())
                && from.toUserJid().equals(selfUser)) {
            return true;
        }

        if (participant != null && participant.toUserJid().equals(selfUser)) {
            return true;
        }

        return false;
    }

    /**
     * Resolves the {@link MessageReceiptType} used for the delivery receipt path.
     *
     * @apiNote
     * Mirrors the {@code u=DROP_ATTR / PEER_MSG / SENDER / INACTIVE} branching
     * inside {@code WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption};
     * peer messages win over sender and sender wins over the inactive-message
     * branch.
     *
     * @param stanza         the parsed incoming stanza
     * @param isSender       whether the message is from the local account
     * @param hasInactiveMsg whether processing produced an inactive-message flag
     * @return the resolved delivery receipt type
     */
    @WhatsAppWebExport(moduleName = "WAWebSendDeliveryReceiptJob", exports = "sendDeliveryReceiptsAfterDecryption",
            adaptation = WhatsAppAdaptation.DIRECT)
    private MessageReceiptType resolveDeliveryReceiptType(
            MessageReceiveStanza stanza,
            boolean isSender,
            boolean hasInactiveMsg
    ) {
        if (stanza.isPeer()) {
            return MessageReceiptType.PEER;
        }

        if (isSender) {
            return MessageReceiptType.SENDER;
        }

        if (hasInactiveMsg) {
            return MessageReceiptType.INACTIVE;
        }

        return MessageReceiptType.DELIVERY;
    }
}
