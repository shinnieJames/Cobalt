package com.github.auties00.cobalt.stream.message;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.receive.receipt.MessageReceiptHandler;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanzaParser;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayload;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayloadSpec;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.*;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestResponseMessage;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestType;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotificationBuilder;
import com.github.auties00.cobalt.model.payment.PaymentInfo;
import com.github.auties00.cobalt.model.payment.PaymentInfoBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshotRecovery;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.sync.key.SyncKeyRotationService;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Handles incoming {@code <message>} stanzas from the WhatsApp server.
 *
 * <p>This is the top-level entry point for all incoming message stanzas.
 * It routes messages through three paths:
 * <ul>
 *   <li><b>Media notify:</b> stanzas with {@code type="medianotify"} are
 *       acknowledged immediately without parsing or decryption.</li>
 *   <li><b>Newsletter:</b> stanzas from newsletter JIDs are routed to
 *       a separate processing path that does not send receipts.</li>
 *   <li><b>Normal messages:</b> parsed, decrypted via {@link MessageService},
 *       then receipts (delivery, retry, nack, or bot ack) are sent based
 *       on the processing result.</li>
 * </ul>
 *
 * <p>After successful processing, the handler stores the message, handles
 * protocol messages (key shares, key requests, snapshot recovery, LID
 * migration), resolves orphan payment notifications, and notifies
 * registered listeners.
 *
 * @implNote WAWebHandleMsg.default: the main entry point for incoming
 * message stanzas.  WAWebCommsHandleWorkerCompatibleStanza: routes
 * newsletter messages to WAWebHandleNewsletterMsg before WAWebHandleMsg.
 * WAWebCommsHandleMessagingStanza.handleMessagingStanza: wraps
 * WAWebHandleMsg with error handling.
 */
public final class MessageStreamHandler implements SocketStream.Handler {
    /**
     * Logger for this handler.
     *
     * @implNote NO_WA_BASIS
     */
    private static final System.Logger LOGGER = System.getLogger(MessageStreamHandler.class.getName());

    /**
     * The WhatsApp client used for sending stanzas, accessing the store,
     * and notifying listeners.
     *
     * @implNote ADAPTED: constructor-based DI instead of module-level imports
     */
    private final WhatsAppClient whatsapp;

    /**
     * The message service that coordinates parsing, decryption, and
     * processing of incoming message payloads.
     *
     * @implNote ADAPTED: WAWebMsgProcessingDecryptApi.decryptE2EPayload,
     * WAWebHandleMsgProcess.processDecryptedMessageProto
     */
    private final MessageService messageService;

    /**
     * The receipt handler that sends delivery, retry, nack, and bot ack
     * receipts after message processing.
     *
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt
     */
    private final MessageReceiptHandler receiptHandler;

    /**
     * The snapshot recovery service for handling syncd snapshot fatal
     * recovery responses in peer data operation messages.
     *
     * @implNote WAWebNonMessageDataRequestHandler.handlePeerDataOperationRequestResponse
     */
    private final SnapshotRecoveryService snapshotRecoveryService;

    /**
     * The sync key rotation service for handling app state sync key
     * shares and requests in protocol messages.
     *
     * @implNote WAWebKeyManagementHandleKeyShareApi, WAWebSyncdHandleKeyShare
     */
    private final SyncKeyRotationService syncKeyRotationService;

    /**
     * The LID migration service for processing LID migration mapping
     * sync payloads in protocol messages.
     *
     * @implNote WAWebProcessMsgInfoForLid.maybeProcesMsgInfoForLid
     */
    private final LidMigrationService lidMigrationService;

    /**
     * Constructs a new message stream handler with the specified
     * dependencies.
     *
     * @param whatsapp                the WhatsApp client
     * @param messageService          the message processing service
     * @param snapshotRecoveryService the snapshot recovery service
     * @param webAppStateService      the web app state service (provides
     *                                the sync key rotation service)
     * @param lidMigrationService     the LID migration service
     * @implNote ADAPTED: constructor-based DI instead of module-level
     * imports.  The handler only depends on the key rotation service
     * exposed by WebAppStateService; the parameter type is kept as
     * WebAppStateService to make the dependency direction explicit.
     */
    public MessageStreamHandler(
            WhatsAppClient whatsapp,
            MessageService messageService,
            SnapshotRecoveryService snapshotRecoveryService,
            WebAppStateService webAppStateService,
            LidMigrationService lidMigrationService
    ) {
        this.whatsapp = whatsapp;
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.receiptHandler = new MessageReceiptHandler(whatsapp);
        this.snapshotRecoveryService = Objects.requireNonNull(snapshotRecoveryService, "snapshotRecoveryService cannot be null");
        this.syncKeyRotationService = Objects.requireNonNull(webAppStateService, "webAppStateService cannot be null").syncKeyRotationService();
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
    }

    /**
     * Handles an incoming {@code <message>} stanza.
     *
     * <p>Dispatches the stanza through the appropriate processing path
     * based on the message type and sender, then sends the appropriate
     * receipt (delivery, retry, nack, or bot ack).
     *
     * @param node the raw {@code <message>} stanza node
     * @implNote WAWebHandleMsg.default: the main entry point function
     * {@code y(t)} that parses, queues, and processes incoming messages.
     * WAWebCommsHandleWorkerCompatibleStanza: routes newsletters before
     * this handler.
     */
    @Override
    public void handle(Node node) {
        // ADAPTED: WAWebHandleMsgSendReceipt.sendReceipt: for medianotify
        // type with SUCCESS result, sends ack instead of delivery receipt.
        // Cobalt short-circuits here since medianotify stanzas don't carry
        // actual message content requiring decryption.
        if ("medianotify".equals(node.getAttributeAsString("type", null))) {
            sendAck(node);
            return;
        }

        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            // WAWebCreateNackFromStanza.createNackFromStanza: returns "NO_ACK" when
            // from is null, meaning no response is sent to the server
            return;
        }

        if (from.hasNewsletterServer()) {
            handleNewsletterMessage(node);
            return;
        }

        MessageReceiveStanza stanza;
        try {
            stanza = MessageReceiveStanzaParser.parse(node, whatsapp.store().jid().orElse(null));
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to parse incoming message stanza: {0}",
                    exception.getMessage());
            sendNack(node, "487"); // WAWebCreateNackFromStanza.NackReason.ParsingError
            return;
        }

        try {
            var info = messageService.process(node);
            if (info != null) {
                storeIncomingMessage(info);
                if (info instanceof ChatMessageInfo chatInfo) {
                    handleProtocolMessage(chatInfo);
                }
                resolveOrphanPayment(info);
                var quoted = whatsapp.store().findQuotedMessage(info);
                notifyMessageReceived(info, quoted);
            }

            if (!whatsapp.store().automaticMessageReceipts()) {
                return;
            }

            if (info == null) {
                if (receiptHandler.isBotSender(stanza)) {
                    receiptHandler.sendBotInvokeResponseAck(stanza);
                } else {
                    receiptHandler.sendAck(stanza);
                }
                return;
            }

            if (receiptHandler.isBotSender(stanza)) {
                receiptHandler.sendBotInvokeResponseAck(stanza);
            } else {
                receiptHandler.sendDeliveryReceipt(stanza, info);
            }
        } catch (WhatsAppMessageException.Receive exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Incoming message {0} failed: {1}",
                    stanza.id(),
                    exception.getMessage());
            handleReceiveFailure(stanza, exception);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Incoming message {0} failed unexpectedly: {1}",
                    stanza.id(),
                    exception.getMessage());
            sendNack(node, "500"); // WAWebCreateNackFromStanza.NackReason.UnhandledError
        }
    }

    /**
     * Handles a newsletter message stanza separately from normal E2E
     * messages.
     *
     * <p>Newsletter messages are not end-to-end encrypted and do not
     * require receipts.  They are processed, stored, and listeners
     * are notified.
     *
     * @param node the raw newsletter message stanza
     * @implNote ADAPTED: WAWebCommsHandleWorkerCompatibleStanza routes
     * newsletter messages to WAWebHandleNewsletterMsg before
     * WAWebHandleMsg.  Cobalt combines this routing here.
     */
    private void handleNewsletterMessage(Node node) {
        try {
            var info = messageService.process(node);
            if (info == null) {
                return;
            }

            storeIncomingMessage(info);
            resolveOrphanPayment(info);
            var quoted = whatsapp.store().findQuotedMessage(info);
            notifyMessageReceived(info, quoted);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle newsletter message stanza: {0}",
                    exception.getMessage());
        }
    }

    /**
     * Handles a message decryption failure by sending the appropriate
     * receipt based on the exception type.
     *
     * <p>The receipt type is determined by the exception:
     * <ul>
     *   <li>{@link WhatsAppMessageException.Receive.HsmMismatch} ->
     *       no receipt sent (WA Web silently drops)</li>
     *   <li>Exceptions with an error code -> NACK receipt</li>
     *   <li>Retryable exceptions -> retry receipt</li>
     *   <li>All other exceptions -> delivery receipt (or bot ack)</li>
     * </ul>
     *
     * @param stanza    the parsed incoming stanza
     * @param exception the decryption exception
     * @implNote WAWebHandleMsgSendReceipt.sendReceipt: routes to the
     * appropriate receipt based on E2EProcessResult.  HSM_MISMATCH
     * sends no receipt.  RETRY sends retry receipt.  PARSE_ERROR and
     * PARSE_VALIDATION_ERROR send nack.  SUCCESS and
     * SIGNAL_OLD_COUNTER_ERROR send delivery receipt.
     */
    private void handleReceiveFailure(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        if (!whatsapp.store().automaticMessageReceipts()) {
            return;
        }

        // WAWebHandleMsgSendReceipt.sendReceipt: HSM_MISMATCH -> no receipt sent at all
        if (exception instanceof WhatsAppMessageException.Receive.HsmMismatch) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "HSM mismatch for message {0}, no receipt sent", // WAWebHandleMsgSendReceipt.sendReceipt
                    stanza.id());
            return;
        }

        var errorCode = exception.errorCode().orElse(null);
        if (errorCode != null) {
            receiptHandler.sendNackReceipt(stanza, parseErrorCode(errorCode));
            return;
        }

        if (exception.shouldSendRetryReceipt()) {
            receiptHandler.sendRetryReceipt(
                    stanza,
                    exception.retryReason(),
                    stanza.retryCount().orElse(0) + 1
            );
            return;
        }

        if (receiptHandler.isBotSender(stanza)) {
            receiptHandler.sendBotInvokeResponseAck(stanza);
        } else {
            receiptHandler.sendDeliveryReceipt(stanza, null);
        }
    }

    /**
     * Sends a plain acknowledgment for the given message stanza.
     *
     * <p>Used for stanzas that do not require full message processing
     * (e.g. {@code medianotify} type messages).
     *
     * @param node the raw message stanza to acknowledge
     * @implNote WAWebHandleMsgSendAck.sendAck: sends an ack with
     * class="message" and the original type/participant attributes.
     */
    private void sendAck(Node node) {
        if (!whatsapp.store().automaticMessageReceipts()) {
            return;
        }

        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", "message")
                .attribute("to", from)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant").orElse(null))
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    /**
     * Sends a negative acknowledgment (NACK) for the given message stanza.
     *
     * <p>Includes an integer error code matching the
     * {@code WAWebCreateNackFromStanza.NackReason} constants.
     *
     * @param node      the raw message stanza to nack
     * @param errorCode the string representation of the error code
     * @implNote WAWebCreateNackFromStanza.createNackFromStanza: builds
     * an ack node with the error attribute set to the NackReason value.
     */
    private void sendNack(Node node, String errorCode) {
        var id = node.getAttributeAsString("id", null);
        var from = node.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", "message")
                .attribute("to", from)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant").orElse(null))
                .attribute("error", parseErrorCode(errorCode))
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    /**
     * Parses a string error code into an integer, defaulting to 500
     * ({@code NackReason.UnhandledError}) if parsing fails.
     *
     * @param value the string error code
     * @return the parsed integer error code
     * @implNote WAWebCreateNackFromStanza.NackReason: error codes are
     * integer constants (e.g. ParsingError=487, UnhandledError=500).
     */
    private static int parseErrorCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 500;
        }
    }

    /**
     * Stores the processed message info in the appropriate store
     * collection (newsletter, status, or chat).
     *
     * <p>For chat messages, also updates the chat's last message
     * timestamp, conversation timestamp, and unread count.
     *
     * @param info the processed message info to store
     * @implNote ADAPTED: WAWebHandleMsgProcess.processDecryptedMessageProto
     * stores messages via WAWebModelStorageInitialize; Cobalt stores
     * directly in AbstractWhatsAppStore.
     */
    private void storeIncomingMessage(MessageInfo info) {
        switch (info) {
            case NewsletterMessageInfo newsletterInfo -> {
                var newsletterJid = newsletterInfo.key().parentJid().orElse(null);
                if (newsletterJid == null) {
                    return;
                }

                var newsletter = whatsapp.store()
                        .findNewsletterByJid(newsletterJid)
                        .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
                newsletter.setTimestamp(newsletterInfo.timestamp().orElse(null));
                if (!newsletterInfo.key().fromMe()) {
                    newsletter.setUnreadMessagesCount(newsletter.unreadMessagesCount() + 1);
                }
                newsletter.addMessage(newsletterInfo);
            }
            case ChatMessageInfo chatInfo -> {
                if (isStatusMessage(chatInfo)) {
                    whatsapp.store().addStatus(chatInfo);
                    return;
                }

                var chatJid = chatInfo.key().parentJid().orElse(null);
                if (chatJid == null) {
                    return;
                }

                var chat = whatsapp.store()
                        .findChatByJid(chatJid)
                        .orElseGet(() -> whatsapp.store().addNewChat(chatJid));
                var timestamp = chatInfo.timestamp().orElse(null);
                chat.setLastMsgTimestamp(timestamp);
                chat.setConversationTimestamp(timestamp);
                if (!chatInfo.key().fromMe()) {
                    chat.setUnreadCount(chat.unreadCount().orElse(0) + 1);
                }
                chat.addMessage(chatInfo);
            }
        }
    }

    /**
     * Notifies all registered listeners about a received message.
     *
     * <p>Each listener is invoked on its own virtual thread.  Status
     * messages trigger an additional {@code onNewStatus} callback.
     * If the message quotes another message, {@code onMessageReply}
     * is also invoked.
     *
     * @param info          the received message info
     * @param quotedMessage the quoted message, if any
     * @implNote ADAPTED: WAWebBackendEventBus.BackendEventBus: Cobalt
     * uses listener callbacks instead of event bus dispatch.
     */
    private void notifyMessageReceived(MessageInfo info, Optional<? extends MessageInfo> quotedMessage) {
        var statusMessage = isStatusMessage(info);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> {
                listener.onNewMessage(whatsapp, info);
                if (statusMessage && info instanceof ChatMessageInfo chatMessageInfo) {
                    listener.onNewStatus(whatsapp, chatMessageInfo);
                }
                quotedMessage.ifPresent(quoted -> listener.onMessageReply(whatsapp, info, quoted));
            });
        }
    }

    /**
     * Returns whether the message is a status broadcast message.
     *
     * @param info the message info to check
     * @return {@code true} if the parent JID is the status broadcast account
     * @implNote WAWebHandleMsg.default: checks
     * {@code getFrom(x).isStatus() || isGroupStatus === true}.
     */
    private boolean isStatusMessage(MessageInfo info) {
        return info.key()
                .parentJid()
                .map(Jid::isStatusBroadcastAccount)
                .orElse(false);
    }

    /**
     * Resolves a previously stored orphan payment notification for
     * the given message, if one exists.
     *
     * <p>When a payment transaction notification arrives before the
     * corresponding message, it is stored as an orphan.  When the
     * message arrives later, this method matches and resolves it.
     *
     * @param info the received message info
     * @implNote ADAPTED: WAWebHandlePaymentNotification: resolves
     * orphan payment notifications against incoming messages.
     */
    private void resolveOrphanPayment(MessageInfo info) {
        if (!(info instanceof ChatMessageInfo chatMessageInfo)) {
            return;
        }

        var orphan = chatMessageInfo.key()
                .id()
                .flatMap(whatsapp.store()::removeOrphanPaymentNotification)
                .orElse(null);
        if (orphan == null) {
            return;
        }

        var transactionNode = new NodeBuilder()
                .description("transaction")
                .attribute("message-id", orphan.messageId())
                .attribute("receiver", orphan.receiverJid().orElse(null))
                .attribute("currency", orphan.currency().orElse(null))
                .attribute("amount_1000", orphan.amount1000().orElse(null))
                .attribute("transaction-type", orphan.transactionType().orElse(null))
                .attribute("status", orphan.status().orElse(null))
                .attribute("ts", orphan.transactionTimestamp().orElse(null))
                .attribute("sender", chatMessageInfo.senderJid().orElse(null))
                .build();
        handlePaymentTransaction(transactionNode);
    }

    /**
     * Processes a payment transaction node by locating the associated
     * message and updating its payment info.
     *
     * <p>If the associated message is not found, the transaction data
     * is stored as an orphan payment notification for later resolution.
     *
     * @param transaction the transaction node containing payment attributes
     * @implNote ADAPTED: WAWebHandlePaymentNotification.handlePaymentTransaction
     */
    private void handlePaymentTransaction(Node transaction) {
        var sender = transaction.getAttributeAsJid("sender").orElse(null);
        var receiver = transaction.getAttributeAsJid("receiver").orElse(null);
        var messageId = transaction.getAttributeAsString("message-id", null);
        if (sender == null || receiver == null || messageId == null) {
            return;
        }

        var self = whatsapp.store().jid().orElse(null);
        var fromMe = self != null && Objects.equals(self.toUserJid(), sender.toUserJid());
        var group = transaction.getAttributeAsJid("group").orElse(null);
        var remote = group != null ? group : fromMe ? receiver : sender;
        var participant = group != null ? sender : null;

        var message = findPaymentMessage(remote, participant, messageId, fromMe);
        if (!(message instanceof ChatMessageInfo chatMessageInfo)) {
            whatsapp.store().addOrphanPaymentNotification(new OrphanPaymentNotificationBuilder()
                    .messageId(messageId)
                    .receiverJid(receiver)
                    .currency(transaction.getAttributeAsString("currency", null))
                    .amount1000(transaction.getAttributeAsLong("amount_1000", (Long) null))
                    .transactionType(transaction.getAttributeAsString("transaction-type", null))
                    .status(transaction.getAttributeAsString("status", null))
                    .transactionTimestamp(transaction.getAttributeAsLong("ts", (Long) null))
                    .build());
            return;
        }

        var paymentInfo = chatMessageInfo.paymentInfo().orElseGet(this::newPaymentInfo);
        var type = transaction.getAttributeAsString("transaction-type", null);
        var status = transaction.getAttributeAsString("status", null);

        paymentInfo.setReceiverJid(receiver);
        paymentInfo.setAmount1000(transaction.getAttributeAsLong("amount_1000", (Long) null));
        paymentInfo.setCurrency(transaction.getAttributeAsString("currency", null));
        paymentInfo.setTransactionTimestamp(transaction.getAttributeAsLong("ts", (Long) null));
        paymentInfo.setStatus(mapPaymentStatus(type, status, fromMe));
        paymentInfo.setTxnStatus(mapTxnStatus(type, status, fromMe));

        chatMessageInfo.setPaymentInfo(paymentInfo);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, chatMessageInfo));
        }
        whatsapp.store().removeOrphanPaymentNotification(messageId);
    }

    /**
     * Finds the message associated with a payment transaction.
     *
     * <p>First attempts a key-based lookup, then falls back to an
     * ID-based search within the chat.
     *
     * @param remote      the remote JID (chat or contact)
     * @param participant the participant JID for group messages
     * @param messageId   the message ID to search for
     * @param fromMe      whether the message was sent by us
     * @return the matching message info, or {@code null} if not found
     * @implNote ADAPTED: WAWebHandlePaymentNotification: looks up
     * messages by key and ID.
     */
    private MessageInfo findPaymentMessage(Jid remote, Jid participant, String messageId, boolean fromMe) {
        var direct = whatsapp.store()
                .findMessageByKey(new MessageKeyBuilder()
                        .id(messageId)
                        .parentJid(remote)
                        .fromMe(fromMe)
                        .senderJid(participant != null ? participant : remote)
                        .build())
                .orElse(null);
        if (direct != null) {
            return direct;
        }

        return whatsapp.store().findMessageById(remote, messageId)
                .map(MessageInfo.class::cast)
                .orElse(null);
    }

    /**
     * Creates a new payment info with default unknown status values.
     *
     * @return a new {@link PaymentInfo} with unknown status
     * @implNote ADAPTED: WAWebHandlePaymentNotification: default
     * payment info initialization.
     */
    private PaymentInfo newPaymentInfo() {
        return new PaymentInfoBuilder()
                .status(PaymentInfo.Status.UNKNOWN_STATUS)
                .txnStatus(PaymentInfo.TxnStatus.UNKNOWN)
                .build();
    }

    /**
     * Maps a payment transaction type and status to a
     * {@link PaymentInfo.Status} value.
     *
     * @param type   the transaction type string
     * @param status the transaction status string
     * @param fromMe whether the transaction was initiated by us
     * @return the mapped payment status
     * @implNote WAWebPaymentStatusUtils.getPaymentWebStatus
     */
    private PaymentInfo.Status mapPaymentStatus(String type, String status, boolean fromMe) {
        // WAWebPaymentStatusUtils.getPaymentWebStatus
        return switch (paymentMessageStatus(type, status, fromMe)) {
            case SEND_PAY_INIT, SEND_PAY_PENDING, RECV_PAY_INIT, RECV_PAY_PENDING, RECV_PAY_RETRY_ON_FAILURE, REQUEST_PAY_INIT -> PaymentInfo.Status.PROCESSING;
            case SEND_PAY_PENDING_RECEIVER, SEND_PAY_FAILURE_RECEIVER -> PaymentInfo.Status.SENT;
            case REQUEST_PAY_SUCCESS -> paymentMessageTransactionType(type, fromMe) == PaymentMessageTransactionType.TYPE_P2P_REQ_SENT ? PaymentInfo.Status.WAITING_FOR_PAYER : PaymentInfo.Status.WAITING;
            case RECV_PAY_PENDING_SETUP -> PaymentInfo.Status.NEED_TO_ACCEPT;
            case SEND_PAY_SUCCESS, RECV_PAY_SUCCESS, REQUEST_PAY_FULFILLED -> PaymentInfo.Status.COMPLETE;
            case SEND_PAY_FAILURE, SEND_PAY_FAILURE_RISK, SEND_PAY_PENDING_REFUND, SEND_PAY_REFUND_PENDING, SEND_PAY_REFUND_FAILED, SEND_PAY_REFUND_FAILED_PROCESSING, RECV_PAY_FAILURE, REQUEST_PAY_FAILED, REQUEST_PAY_FAILED_RISK -> PaymentInfo.Status.COULD_NOT_COMPLETE;
            case SEND_PAY_REFUNDED -> PaymentInfo.Status.REFUNDED;
            case RECV_PAY_EXPIRED, REQUEST_PAY_EXPIRED, SEND_PAY_AUTH_CANCELED, SEND_PAY_AUTH_CANCEL_FAILED, SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING -> PaymentInfo.Status.EXPIRED; // WAWebPaymentStatusUtils: SEND_PAY_EXPIRED is NOT in EXPIRED
            case REQUEST_PAY_REJECTED -> PaymentInfo.Status.REJECTED;
            case REQUEST_PAY_CANCELLED -> PaymentInfo.Status.CANCELLED;
            case null, default -> PaymentInfo.Status.UNKNOWN_STATUS;
        };
    }

    /**
     * Maps a payment transaction type and status to a
     * {@link PaymentInfo.TxnStatus} value.
     *
     * @param type   the transaction type string
     * @param status the transaction status string
     * @param fromMe whether the transaction was initiated by us
     * @return the mapped transaction status
     * @implNote WAWebPaymentStatusUtils.getPaymentTxnWebStatus
     */
    private PaymentInfo.TxnStatus mapTxnStatus(String type, String status, boolean fromMe) {
        // WAWebPaymentStatusUtils.getPaymentTxnWebStatus
        return switch (paymentMessageStatus(type, status, fromMe)) {
            case RECV_PAY_EXPIRED, SEND_PAY_EXPIRED -> PaymentInfo.TxnStatus.EXPIRED_TXN;
            case RECV_PAY_FAILURE, SEND_PAY_FAILURE -> PaymentInfo.TxnStatus.FAILED;
            case RECV_PAY_INIT, SEND_PAY_INIT -> PaymentInfo.TxnStatus.INIT;
            case RECV_PAY_PENDING_SETUP -> PaymentInfo.TxnStatus.PENDING_SETUP;
            case RECV_PAY_PENDING, SEND_PAY_PENDING -> PaymentInfo.TxnStatus.FAILED_DA;
            case RECV_PAY_RETRY_ON_FAILURE -> PaymentInfo.TxnStatus.FAILED_PROCESSING;
            case RECV_PAY_SUCCESS, SEND_PAY_SUCCESS, REQUEST_PAY_FULFILLED -> PaymentInfo.TxnStatus.SUCCESS;
            case REQUEST_PAY_CANCELLED -> PaymentInfo.TxnStatus.COLLECT_CANCELED;
            case REQUEST_PAY_CANCELLING -> PaymentInfo.TxnStatus.COLLECT_CANCELLING;
            case REQUEST_PAY_EXPIRED -> PaymentInfo.TxnStatus.COLLECT_EXPIRED;
            case REQUEST_PAY_FAILED_RISK -> PaymentInfo.TxnStatus.COLLECT_FAILED_RISK;
            case REQUEST_PAY_FAILED -> PaymentInfo.TxnStatus.COLLECT_FAILED;
            case REQUEST_PAY_INIT -> PaymentInfo.TxnStatus.COLLECT_INIT;
            case REQUEST_PAY_REJECTED -> PaymentInfo.TxnStatus.COLLECT_REJECTED;
            case REQUEST_PAY_SUCCESS -> PaymentInfo.TxnStatus.COLLECT_SUCCESS;
            case SEND_PAY_AUTH_CANCELED -> PaymentInfo.TxnStatus.AUTH_CANCELED;
            case SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING -> PaymentInfo.TxnStatus.AUTH_CANCEL_FAILED_PROCESSING;
            case SEND_PAY_AUTH_CANCEL_FAILED -> PaymentInfo.TxnStatus.AUTH_CANCEL_FAILED;
            case SEND_PAY_FAILURE_RECEIVER -> PaymentInfo.TxnStatus.FAILED_RECEIVER_PROCESSING;
            case SEND_PAY_FAILURE_RISK, RECV_PAY_FAILURE_RISK -> PaymentInfo.TxnStatus.FAILED_RISK;
            case SEND_PAY_PENDING_RECEIVER -> PaymentInfo.TxnStatus.PENDING_RECEIVER_SETUP;
            case SEND_PAY_PENDING_REFUND -> PaymentInfo.TxnStatus.FAILED_DA_FINAL;
            case SEND_PAY_REFUNDED -> PaymentInfo.TxnStatus.REFUNDED_TXN;
            case SEND_PAY_REFUND_FAILED_PROCESSING -> PaymentInfo.TxnStatus.REFUND_FAILED_PROCESSING;
            case SEND_PAY_REFUND_FAILED -> PaymentInfo.TxnStatus.REFUND_FAILED;
            case SEND_PAY_REFUND_PENDING -> PaymentInfo.TxnStatus.REFUND_FAILED_DA;
            case SEND_PAY_IN_REVIEW -> PaymentInfo.TxnStatus.IN_REVIEW;
            case null, default -> PaymentInfo.TxnStatus.UNKNOWN;
        };
    }

    /**
     * Determines the internal payment message status from the transaction
     * type, raw status string, and direction.
     *
     * @param type   the transaction type string
     * @param status the raw status string from the transaction node
     * @param fromMe whether the payment was sent by us
     * @return the resolved payment message status
     * @implNote WAWebPaymentStatusUtils.getNotificationTransactionStatus
     */
    private PaymentMessageStatus paymentMessageStatus(String type, String status, boolean fromMe) {
        // WAWebPaymentStatusUtils.getNotificationTransactionStatus
        var statusValue = status == null ? "" : status.toUpperCase();
        return switch (paymentMessageTransactionType(type, fromMe)) {
            case TYPE_P2M_PAYOUT -> PaymentMessageStatus.STATUS_UNSET; // WAWebPaymentStatusUtils: falls through to STATUS_UNSET
            case TYPE_P2P_SENT, TYPE_P2M_SENT, TYPE_DEPOSIT -> switch (statusValue) {
                case "PENDING_RECEIVER_SETUP" -> PaymentMessageStatus.SEND_PAY_PENDING_RECEIVER;
                case "FAILED_DA" -> PaymentMessageStatus.SEND_PAY_PENDING;
                case "REFUND_FAILED_DA" -> PaymentMessageStatus.SEND_PAY_REFUND_PENDING;
                case "FAILED_RISK" -> PaymentMessageStatus.SEND_PAY_FAILURE_RISK;
                case "INITIAL" -> PaymentMessageStatus.SEND_PAY_INIT;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.SEND_PAY_SUCCESS;
                case "FAILURE", "FAILED" -> PaymentMessageStatus.SEND_PAY_FAILURE;
                case "REFUNDED" -> PaymentMessageStatus.SEND_PAY_REFUNDED;
                case "REFUND_FAILED" -> PaymentMessageStatus.SEND_PAY_REFUND_FAILED;
                case "FAILED_RECEIVER_PROCESSING" -> PaymentMessageStatus.SEND_PAY_FAILURE_RECEIVER;
                case "REFUND_FAILED_PROCESSING" -> PaymentMessageStatus.SEND_PAY_REFUND_FAILED_PROCESSING;
                case "FAILED_DA_FINAL" -> PaymentMessageStatus.SEND_PAY_PENDING_REFUND;
                case "AUTH_CANCEL_FAILED_PROCESSING" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING;
                case "AUTH_CANCEL_FAILED" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCEL_FAILED;
                case "AUTH_CANCELED" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCELED; // WAWebPaymentStatusUtils: d.AUTH_CANCELED = "AUTH_CANCELED"
                case "CANCELLED" -> PaymentMessageStatus.SEND_PAY_USER_CANCELED; // WAWebPaymentStatusUtils: d.CANCELED = "CANCELLED" -> SEND_PAY_USER_CANCELED
                case "EXPIRED" -> PaymentMessageStatus.SEND_PAY_EXPIRED;
                case "IN_REVIEW" -> PaymentMessageStatus.SEND_PAY_IN_REVIEW;
                case "PENDING" -> PaymentMessageStatus.SEND_PAY_PENDING_PROCESSING;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_RCVD, TYPE_P2M_RCVD -> switch (statusValue) {
                case "PENDING_SETUP" -> PaymentMessageStatus.RECV_PAY_PENDING_SETUP;
                case "FAILED_DA" -> PaymentMessageStatus.RECV_PAY_PENDING; // WAWebPaymentStatusUtils: only FAILED_DA, no PENDING
                case "FAILED_PROCESSING" -> PaymentMessageStatus.RECV_PAY_RETRY_ON_FAILURE;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.RECV_PAY_SUCCESS;
                case "FAILURE", "FAILED" -> PaymentMessageStatus.RECV_PAY_FAILURE;
                case "EXPIRED" -> PaymentMessageStatus.RECV_PAY_EXPIRED;
                case "FAILED_RISK" -> PaymentMessageStatus.RECV_PAY_FAILURE_RISK;
                case "WITHDRAWAL_PROCESSING" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PROCESSING;
                case "WITHDRAWAL_FAILURE" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_FAILURE;
                case "WITHDRAWAL_PERMANENT_FAILED" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PERMANENT_FAILED;
                case "CANCELLED" -> PaymentMessageStatus.RECV_PAY_SENDER_CANCELED; // WAWebPaymentStatusUtils: d.CANCELED = "CANCELLED"
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_REQ_SENT, TYPE_P2P_REQ_RCVD -> switch (statusValue) {
                case "COLLECT_SUCCESS" -> PaymentMessageStatus.REQUEST_PAY_SUCCESS;
                case "COLLECT_FAILED" -> PaymentMessageStatus.REQUEST_PAY_FAILED;
                case "COLLECT_FAILED_RISK" -> PaymentMessageStatus.REQUEST_PAY_FAILED_RISK;
                case "COLLECT_REJECTED" -> PaymentMessageStatus.REQUEST_PAY_REJECTED;
                case "COLLECT_EXPIRED" -> PaymentMessageStatus.REQUEST_PAY_EXPIRED;
                case "COLLECT_CANCELED" -> PaymentMessageStatus.REQUEST_PAY_CANCELLED;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_REQ_SCHEDULED_PAYMENT_RCVD -> switch (statusValue) {
                case "COLLECT_SUCCESS" -> PaymentMessageStatus.REQUEST_PAY_SCHEDULED_PAYMENT_SUCCESS;
                case "AUTH_SUCCESS" -> PaymentMessageStatus.SEND_PAY_AUTH_SUCCESS;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_REFUND -> switch (statusValue) {
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.RECV_PAY_SUCCESS;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_WITHDRAWAL -> switch (statusValue) {
                case "PENDING" -> PaymentMessageStatus.WITHDRAWAL_PENDING;
                case "IN_REVIEW" -> PaymentMessageStatus.WITHDRAWAL_IN_REVIEW;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.WITHDRAWAL_SUCCESS;
                case "FAILED", "DECLINED" -> PaymentMessageStatus.WITHDRAWAL_FAILED;
                case "CANCELLED" -> PaymentMessageStatus.WITHDRAWAL_USER_CANCELED; // WAWebPaymentStatusUtils: d.CANCELED = "CANCELLED"
                case "EXPIRED" -> PaymentMessageStatus.WITHDRAWAL_EXPIRED;
                case "WITHDRAWAL_ACTIVE" -> PaymentMessageStatus.WITHDRAWAL_ACTIVE;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
        };
    }

    /**
     * Determines the payment transaction type from the raw type string
     * and direction.
     *
     * @param type   the raw transaction type string, or {@code null}
     * @param fromMe whether the payment was sent by us
     * @return the resolved transaction type
     * @implNote WAWebPaymentStatusUtils.getPaymentTransactionType
     */
    private PaymentMessageTransactionType paymentMessageTransactionType(String type, boolean fromMe) {
        // WAWebPaymentStatusUtils.getPaymentTransactionType
        if (type == null) {
            return fromMe ? PaymentMessageTransactionType.TYPE_P2P_SENT : PaymentMessageTransactionType.TYPE_P2P_RCVD;
        }

        return switch (type.toLowerCase()) {
            case "p2p" -> fromMe ? PaymentMessageTransactionType.TYPE_P2P_SENT : PaymentMessageTransactionType.TYPE_P2P_RCVD;
            case "p2m" -> fromMe ? PaymentMessageTransactionType.TYPE_P2M_SENT : PaymentMessageTransactionType.TYPE_P2M_RCVD;
            case "payout" -> PaymentMessageTransactionType.TYPE_P2M_PAYOUT;
            case "deposit" -> PaymentMessageTransactionType.TYPE_DEPOSIT;
            case "refund" -> PaymentMessageTransactionType.TYPE_REFUND;
            case "withdrawal" -> PaymentMessageTransactionType.TYPE_WITHDRAWAL;
            default -> fromMe ? PaymentMessageTransactionType.TYPE_P2P_SENT : PaymentMessageTransactionType.TYPE_P2P_RCVD;
        };
    }

    /**
     * Handles protocol messages embedded within chat messages.
     *
     * <p>Processes the following protocol message types:
     * <ul>
     *   <li>LID migration mapping sync</li>
     *   <li>Peer data operation request/response (snapshot recovery)</li>
     *   <li>App state sync key share</li>
     *   <li>App state sync key request</li>
     * </ul>
     *
     * @param info the chat message info containing a protocol message
     * @implNote WAWebHandleMsg.default: after successful decryption,
     * protocol messages are dispatched to their respective handlers.
     * WAWebNonMessageDataRequestHandler.handlePeerDataOperationRequestResponse,
     * WAWebKeyManagementHandleKeyShareApi, WAWebSyncdHandleKeyShare.
     */
    private void handleProtocolMessage(ChatMessageInfo info) {
        var content = info.message().content();
        if (!(content instanceof ProtocolMessage protocolMessage)) {
            return;
        }

        protocolMessage.lidMigrationMappingSyncMessage()
                .flatMap(message -> message.encodedMappingPayload().flatMap(this::decodeLidMappingPayload))
                .ifPresent(lidMigrationService::processProtocolMessage);

        protocolMessage.peerDataOperationRequestResponseMessage()
                .ifPresent(this::resolveSnapshotRecovery);

        protocolMessage.appStateSyncKeyShare()
                .ifPresent(keyShare -> processAppStateSyncKeyShare(info, keyShare));

        protocolMessage.appStateSyncKeyRequest()
                .ifPresent(request -> processAppStateSyncKeyRequest(info, request));
    }

    /**
     * Processes an app state sync key share protocol message.
     *
     * <p>Validates key IDs (must be exactly 6 bytes) and delegates
     * the validated keys to the sync key rotation service.
     *
     * @param info     the chat message info containing the key share
     * @param keyShare the key share containing the shared keys
     * @implNote WAWebKeyManagementHandleKeyShareApi: validates key IDs
     * before delegating to WAWebSyncdHandleKeyShare.handleKeyShare.
     */
    private void processAppStateSyncKeyShare(ChatMessageInfo info, AppStateSyncKeyShare keyShare) {
        // WAWebKeyManagementHandleKeyShareApi: caller-side validation before delegating
        // to WAWebSyncdHandleKeyShare.handleKeyShare. Sender device ID is extracted from
        // the message info; keys with missing or malformed key IDs are filtered out so
        // they never reach the underlying handler.
        var senderDeviceId = info.senderJid().isPresent() ? info.senderJid().get().device() : -1;

        var keys = keyShare.keys();
        var validatedKeys = new ArrayList<AppStateSyncKey>(keys.size());
        for (var key : keys) {
            var keyId = key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyId == null) {
                continue; // WAWebKeyManagementHandleKeyShareApi: skip keys with missing keyID
            }

            // WAWebKeyManagementHandleKeyShareApi: key ID must be exactly 6 bytes
            if (keyId.length != 6) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "syncd: key share key id has invalid bytelength of {0}", keyId.length);
                continue;
            }

            validatedKeys.add(key);
        }

        if (validatedKeys.isEmpty()) {
            return;
        }

        // WAWebSyncdHandleKeyShare.handleKeyShare: stores keys, updates missing key
        // tracking, reschedules timeouts, and resumes blocked collections (which
        // includes the equivalent of WAWebSyncd.syncBlockedCollections).
        syncKeyRotationService.handleKeyShare(senderDeviceId, validatedKeys);
    }

    /**
     * Processes an app state sync key request protocol message by
     * sending the requested keys back to the requester.
     *
     * <p>Looks up each requested key ID in the store.  If a key is
     * not found, a placeholder entry with just the key ID is included.
     * The response is sent as a peer message.
     *
     * @param info    the chat message info containing the key request
     * @param request the key request listing the requested key IDs
     * @implNote WAWebKeyManagementHandleKeyRequestApi: handles key
     * request by looking up keys and sending a key share response.
     */
    private void processAppStateSyncKeyRequest(
            ChatMessageInfo info,
            AppStateSyncKeyRequest request
    ) {
        var sender = info.senderJid();
        if (sender.isEmpty()) {
            return;
        }

        var keysToShare = new ArrayList<AppStateSyncKey>();
        for (var requestedKeyId : request.keyIds()) {
            var rawKeyId = requestedKeyId.keyId().orElse(null);
            if (rawKeyId == null) {
                continue;
            }

            var keyToShare = whatsapp.store().findWebAppStateKeyById(rawKeyId)
                    .orElseGet(() -> new AppStateSyncKeyBuilder()
                            .keyId(new AppStateSyncKeyIdBuilder()
                                    .keyId(rawKeyId)
                                    .build())
                            .build());
            keysToShare.add(keyToShare);
        }

        if (keysToShare.isEmpty()) {
            return;
        }

        try {
            var keyShare = new AppStateSyncKeyShareBuilder()
                    .keys(keysToShare)
                    .build();
            var protocolMessage = new ProtocolMessageBuilder()
                    .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_SHARE)
                    .appStateSyncKeyShare(keyShare)
                    .build();
            var messageContainer = new MessageContainerBuilder()
                    .protocolMessage(protocolMessage)
                    .build();
            var self = whatsapp.store().jid().orElse(null);
            if (self == null) {
                return;
            }

            var key = new MessageKeyBuilder()
                    .id(MessageIdGenerator.generate(MessageIdVersion.V2, sender.get()))
                    .parentJid(self)
                    .fromMe(true)
                    .senderJid(self)
                    .build();
            var response = new ChatMessageInfoBuilder()
                    .key(key)
                    .message(messageContainer)
                    .timestamp(Instant.now())
                    .senderJid(self)
                    .build();
            whatsapp.sendPeerMessage(sender.get(), response);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to answer app state sync key request from {0}: {1}",
                    sender,
                    throwable.getMessage());
        }
    }

    /**
     * Resolves a syncd snapshot fatal recovery response from a peer
     * data operation message.
     *
     * <p>Only processes responses of type
     * {@code COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY}.  Decodes the
     * recovery snapshot and passes it to the snapshot recovery service.
     *
     * @param response the peer data operation response message
     * @implNote WAWebNonMessageDataRequestHandler.handlePeerDataOperationRequestResponse:
     * decodes the SyncDSnapshotFatalRecoveryResponse and resolves the
     * recovery promise.
     */
    private void resolveSnapshotRecovery(PeerDataOperationRequestResponseMessage response) {
        if (response.peerDataOperationRequestType().orElse(null)
                != PeerDataOperationRequestType.COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY) {
            return;
        }

        if (!snapshotRecoveryService.isRecoveryEnabled()) { // WAWebNonMessageDataRequestHandler: syncdSnapshotRecoveryEnabled() === false
            return;
        }

        // WAWebNonMessageDataRequestHandler: only the first peerDataOperationResult is
        // consumed (`d(n.peerDataOperationResult[0], t)`); for snapshot recovery the
        // primary device sends back a single result.
        var results = response.peerDataOperationResult();
        if (results.isEmpty()) {
            return;
        }

        var recovery = results.get(0).syncdSnapshotFatalRecoveryResponse().orElse(null);
        if (recovery == null) {
            return;
        }

        // WAWebNonMessageDataRequestHandler.m: decode the SyncDSnapshotFatalRecoveryResponse
        // exactly once and pass the decoded SyncdSnapshotRecovery through the recovery
        // promise so the awaiting consumer in WebAppStateService does not decode again.
        SyncdSnapshotRecovery decoded;
        try {
            decoded = snapshotRecoveryService.decodeRecoverySnapshot(recovery);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to decode snapshot recovery payload: {0}",
                    exception.getMessage());
            return;
        }

        decoded.collectionName()
                .flatMap(SyncPatchType::of)
                .ifPresent(collectionName -> snapshotRecoveryService.resolveRecovery(collectionName, decoded));
    }

    /**
     * Decodes a GZIP-compressed LID migration mapping sync payload.
     *
     * @param payload the GZIP-compressed protobuf payload bytes
     * @return the decoded payload, or empty if decoding fails
     * @implNote WAWebProcessMsgInfoForLid.maybeProcesMsgInfoForLid:
     * decodes the encoded mapping payload from the LID migration
     * mapping sync message.
     */
    private Optional<LIDMigrationMappingSyncPayload> decodeLidMappingPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }

        try (var protobufStream = ProtobufInputStream.fromStream(new GZIPInputStream(new ByteArrayInputStream(payload)))) {
            return Optional.of(LIDMigrationMappingSyncPayloadSpec.decode(protobufStream));
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to decode LID migration mapping payload: {0}",
                    exception.getMessage());
            return Optional.empty();
        }
    }
}
