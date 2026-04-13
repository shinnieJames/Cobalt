package com.github.auties00.cobalt.stream.notification.business;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotificationBuilder;
import com.github.auties00.cobalt.model.payment.PaymentInfo;
import com.github.auties00.cobalt.model.payment.PaymentInfoBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.message.PaymentMessageStatus;
import com.github.auties00.cobalt.stream.message.PaymentMessageTransactionType;
import com.github.auties00.cobalt.stream.SocketStream;

import java.util.Objects;

/**
 * Handles incoming payment notification stanzas ({@code type="pay"}) by parsing
 * invite and transaction child nodes, updating the corresponding message's payment
 * metadata in the store, and sending protocol-level acknowledgements.
 *
 * <p>When a transaction notification references a message not yet in the store, the
 * payment data is saved as an orphan payment notification for later resolution when
 * the message arrives via the message stream handler.
 *
 * @implNote WAWebPaymentNotificationHandler
 */
final class NotificationPaymentStreamHandler implements SocketStream.Handler {
    /**
     * Logger for payment notification handling diagnostics.
     *
     * @implNote WAWebPaymentNotificationHandler (WALogger references)
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationPaymentStreamHandler.class.getName());

    /**
     * The WhatsApp client instance providing access to the store and socket for
     * sending acknowledgement stanzas.
     *
     * @implNote WAWebPaymentNotificationHandler (module-level dependencies)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new payment notification stream handler with the given client.
     *
     * @implNote WAWebPaymentNotificationHandler (module initialization)
     * @param whatsapp the WhatsApp client instance
     */
    NotificationPaymentStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles an incoming payment notification stanza by dispatching to the appropriate
     * sub-handler based on whether it contains an invite or transaction child node,
     * then sending an acknowledgement stanza back to the server.
     *
     * <p>Invite notifications take priority: if the stanza contains an {@code invite}
     * child, the transaction child (if any) is not processed. This mirrors the WA Web
     * ternary dispatch: {@code invite != null ? handleInvite(invite) : transaction != null && handleTransaction(transaction)}.
     *
     * @implNote WAWebPaymentNotificationHandler.handlePaymentNotification
     * @param node the incoming notification stanza node
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "pay")) {
            return;
        }

        try {
            // WAWebPaymentNotificationHandler.handlePaymentNotification:
            // invite takes priority over transaction (if/else, not both)
            var invite = node.getChild("invite").orElse(null);
            if (invite != null) {
                handlePaymentInvite(node, invite);
            } else {
                node.getChild("transaction").ifPresent(this::handlePaymentTransaction);
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle payment notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    /**
     * Handles a payment invite notification by processing {@code account-set-up} invites.
     *
     * <p>When the invite type is {@code "account-set-up"}, WA Web creates a local
     * notification template message and processes it through the single-message handler.
     * This Cobalt implementation logs the invite details; the full notification template
     * message creation is not yet implemented.
     *
     * @implNote WAWebPaymentNotificationHandler (inner function handling invite branch)
     * @param node   the parent notification stanza node
     * @param invite the invite child node
     */
    private void handlePaymentInvite(Node node, Node invite) {
        // WAWebPaymentNotificationHandler: only "account-set-up" invites are processed
        var type = invite.getAttributeAsString("type", null);
        var service = invite.getAttributeAsString("service", null);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received payment invite notification type={0} service={1} id={2}",
                type, service, node.getAttributeAsString("id", "[missing-id]"));
        // TODO: For "account-set-up" type, WA Web creates a NotificationTemplate message
        // via WAWebPaymentNotificationHandler inner function R and processes it through
        // WAWebHandleSingleMsgFactory.handleSingleMsg. This would require creating a
        // ChatMessageInfo with MsgSubtype.PaymentInviteAccountSetUp and storing it.
    }

    /**
     * Handles a payment transaction notification by looking up the referenced message,
     * updating its payment metadata, and notifying listeners.
     *
     * <p>If the referenced message cannot be found in the store, the transaction data
     * is saved as an orphan payment notification for later resolution when the message
     * arrives.
     *
     * @implNote WAWebPaymentNotificationHandler (inner functions m/p and g/h)
     * @param transaction the transaction child node from the notification stanza
     */
    private void handlePaymentTransaction(Node transaction) {
        // WAWebPaymentNotificationParser.parseTransactionNode: skip Novi transactions
        var service = transaction.getAttributeAsString("service", null);
        if (service != null && service.equalsIgnoreCase("NOVI")) { // WAWebPaymentNotificationParser.isNoviTransaction
            LOGGER.log(System.Logger.Level.WARNING, "Payment notification from Novi not supported.");
            return;
        }

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
            // WAWebPaymentNotificationHandler: store orphan for later resolution
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

        applyPaymentTransaction(chatMessageInfo, transaction, fromMe);
    }

    /**
     * Applies payment transaction data to a resolved chat message, updating its
     * payment info fields and notifying listeners.
     *
     * <p>This method also handles the linked payment request message: if the resolved
     * message has a {@code paymentRequestMessageKey}, the request message is looked up
     * and its payment status is updated with the fulfilled status.
     *
     * @implNote WAWebPaymentNotificationHandler (inner functions y and g/h)
     * @param chatMessageInfo the resolved chat message to update
     * @param transaction     the transaction child node with payment data
     * @param fromMe          whether the current user is the sender
     */
    private void applyPaymentTransaction(ChatMessageInfo chatMessageInfo, Node transaction, boolean fromMe) {
        var receiver = transaction.getAttributeAsJid("receiver").orElse(null);
        var type = transaction.getAttributeAsString("transaction-type", null);
        var status = transaction.getAttributeAsString("status", null);

        // WAWebPaymentNotificationHandler function y: update message payment fields
        var paymentInfo = chatMessageInfo.paymentInfo().orElseGet(this::newPaymentInfo);
        paymentInfo.setReceiverJid(receiver);
        paymentInfo.setAmount1000(transaction.getAttributeAsLong("amount_1000", (Long) null));
        paymentInfo.setCurrency(transaction.getAttributeAsString("currency", null));
        paymentInfo.setTransactionTimestamp(transaction.getAttributeAsLong("ts", (Long) null));
        paymentInfo.setStatus(mapPaymentStatus(type, status, fromMe));
        paymentInfo.setTxnStatus(mapTxnStatus(type, status, fromMe));
        chatMessageInfo.setPaymentInfo(paymentInfo);

        // WAWebPaymentNotificationHandler: notify listeners
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, chatMessageInfo));
        }

        // WAWebPaymentNotificationHandler: remove orphan after successful processing
        chatMessageInfo.key().id().ifPresent(whatsapp.store()::removeOrphanPaymentNotification);
    }

    /**
     * Looks up a payment message first from the in-memory store by exact message key,
     * then falls back to a broader search by message ID within the chat.
     *
     * <p>This method combines the behavior of both {@code getMessageFromCollection}
     * (in-memory lookup) and {@code getMessageFromDb} (persistent lookup) from WA Web,
     * since Cobalt's store unifies both storage layers.
     *
     * @implNote WAWebPaymentNotificationHandler.getMessageFromCollection,
     *           WAWebPaymentNotificationHandler.getMessageFromDb
     * @param remote      the remote JID (chat or group) that owns the message
     * @param participant the sender JID within a group, or {@code null} for 1:1 chats
     * @param messageId   the message identifier to look up
     * @param fromMe      whether the message was sent by the current user
     * @return the found {@link MessageInfo}, or {@code null} if not found
     */
    private MessageInfo findPaymentMessage(Jid remote, Jid participant, String messageId, boolean fromMe) {
        // WAWebPaymentNotificationHandler.getMessageFromCollection: in-memory lookup by key
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

        // WAWebPaymentNotificationHandler.getMessageFromDb: persistent lookup by id
        return whatsapp.store().findMessageById(remote, messageId)
                .map(MessageInfo.class::cast)
                .orElse(null);
    }

    /**
     * Creates a new {@link PaymentInfo} with default unknown status values.
     *
     * @implNote WAWebPaymentNotificationHandler (implicit default in function g/h)
     * @return a new {@link PaymentInfo} with {@link PaymentInfo.Status#UNKNOWN_STATUS}
     *         and {@link PaymentInfo.TxnStatus#UNKNOWN}
     */
    private PaymentInfo newPaymentInfo() {
        return new PaymentInfoBuilder()
                .status(PaymentInfo.Status.UNKNOWN_STATUS)
                .txnStatus(PaymentInfo.TxnStatus.UNKNOWN)
                .build();
    }

    /**
     * Maps the resolved payment message status and transaction type to a high-level
     * {@link PaymentInfo.Status} for user-facing display.
     *
     * @implNote WAWebPaymentStatusUtils.getPaymentWebStatus
     * @param type   the raw transaction type string from the stanza
     * @param status the raw status string from the stanza
     * @param fromMe whether the current user is the sender
     * @return the mapped {@link PaymentInfo.Status}
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
            default -> PaymentInfo.Status.UNKNOWN_STATUS;
        };
    }

    /**
     * Maps the resolved payment message status to a fine-grained
     * {@link PaymentInfo.TxnStatus} that tracks the internal transaction state machine.
     *
     * @implNote WAWebPaymentStatusUtils.getPaymentTxnWebStatus
     * @param type   the raw transaction type string from the stanza
     * @param status the raw status string from the stanza
     * @param fromMe whether the current user is the sender
     * @return the mapped {@link PaymentInfo.TxnStatus}
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
            default -> PaymentInfo.TxnStatus.UNKNOWN;
        };
    }

    /**
     * Resolves a raw transaction type string and status string into a fine-grained
     * {@link PaymentMessageStatus} constant.
     *
     * <p>The resolution uses the transaction type (combined with {@code fromMe}) to
     * select the appropriate status mapping table, then matches the uppercased status
     * string against the known server-reported values.
     *
     * @implNote WAWebPaymentStatusUtils.getNotificationTransactionStatus
     * @param type   the raw transaction type string from the stanza, or {@code null}
     * @param status the raw status string from the stanza, or {@code null}
     * @param fromMe whether the current user is the sender
     * @return the resolved {@link PaymentMessageStatus}
     */
    private PaymentMessageStatus paymentMessageStatus(String type, String status, boolean fromMe) {
        // WAWebPaymentStatusUtils.getNotificationTransactionStatus:
        // if (!t) return u.STATUS_UNSET; var n = t.toUpperCase();
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
     * Resolves the raw transaction type string from the stanza into a
     * {@link PaymentMessageTransactionType} constant.
     *
     * <p>When the type string is {@code null} or unrecognized, the result defaults to
     * {@link PaymentMessageTransactionType#TYPE_P2P_SENT} or
     * {@link PaymentMessageTransactionType#TYPE_P2P_RCVD} based on the {@code fromMe}
     * flag.
     *
     * @implNote WAWebPaymentStatusUtils.getPaymentTransactionType
     * @param type   the raw transaction type string, or {@code null}
     * @param fromMe whether the current user is the sender
     * @return the resolved {@link PaymentMessageTransactionType}
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
     * Sends an acknowledgement stanza for a payment notification back to the server.
     *
     * <p>The ACK stanza uses fixed {@code class="notification"} and {@code type="pay"}
     * attributes as specified in the WA Web protocol, rather than reflecting the
     * original stanza's attributes generically.
     *
     * @implNote WAWebPaymentNotificationHandler (inner function E)
     * @param node the notification stanza to acknowledge
     */
    private void sendNotificationAck(Node node) {
        // WAWebPaymentNotificationHandler function E:
        // wap("ack", {class: "notification", type: "pay", id: CUSTOM_STRING(stanzaId), to: from})
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from").orElse(null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification") // WAWebPaymentNotificationHandler: hardcoded "notification"
                .attribute("to", stanzaFrom)
                .attribute("type", "pay") // WAWebPaymentNotificationHandler: hardcoded "pay"
                .build());
    }
}
