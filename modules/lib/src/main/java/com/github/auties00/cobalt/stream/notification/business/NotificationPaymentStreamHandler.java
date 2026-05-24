package com.github.auties00.cobalt.stream.notification.business;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo.StubType;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotificationBuilder;
import com.github.auties00.cobalt.model.payment.PaymentInfo;
import com.github.auties00.cobalt.model.payment.PaymentInfoBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.message.PaymentMessageStatus;
import com.github.auties00.cobalt.stream.message.PaymentMessageTransactionType;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.util.RandomIdUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Handles {@code type="pay"} notifications carrying payment invites and
 * payment-transaction state changes.
 *
 * @apiNote
 * Dispatched by {@link NotificationBusinessDispatcher}. Each notification
 * contains either an {@code <invite>} child (account-setup invite or a
 * pre-payment prompt) or a {@code <transaction>} child (transaction
 * lifecycle update for an already-sent payment request or transfer).
 * Transactions whose referenced message has not yet arrived are
 * recorded as orphan-payment entries on the store and reconciled when
 * the message arrives via the chat-message stream handler.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code WAWebPaymentNotificationHandler.handlePaymentNotification}
 * ternary: {@code invite != null ? handleInvite(invite) : transaction != null && handleTransaction(transaction)}.
 * Invite takes priority over transaction; only one is processed per
 * stanza.
 */
@WhatsAppWebModule(moduleName = "WAWebPaymentNotificationHandler")
@WhatsAppWebModule(moduleName = "WAWebPaymentNotificationParser")
final class NotificationPaymentStreamHandler implements SocketStream.Handler {
    /**
     * Logger used for warnings about malformed stanzas and unsupported
     * services (e.g. Novi payments).
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationPaymentStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} used for store reads, message lookups,
     * and orphan-payment writes.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification" type="pay"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @apiNote
     * Called once by {@link NotificationBusinessDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp  the {@link WhatsAppClient}
     * @param ackSender the {@link AckSender}
     */
    NotificationPaymentStreamHandler(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Dispatches to the invite or transaction branch and always sends
     * the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationBusinessDispatcher}. Invite takes
     * priority over transaction so a stanza carrying both children
     * processes only the invite, matching WA Web's ternary dispatch.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "pay")) {
            return;
        }

        try {
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
     * Materialises an account-setup invite as a local stub message in
     * the inviter's chat and fires
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onNewMessage}
     * for listeners.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebPaymentNotificationHandler._(invite)} when
     * {@code invite.type === "account-set-up"}, which generates a
     * {@code NotificationTemplate}/{@code PaymentInviteAccountSetUp}
     * subtype stub message via
     * {@code WAWebHandleSingleMsgWorkerCompatible.handleSingleMsg}.
     * Other invite types are logged and otherwise ignored.
     *
     * @implNote
     * This implementation uses {@link StubType#PAYMENT_ACTION_ACCOUNT_SETUP_REMINDER}
     * for the stub type because that is Cobalt's nearest model
     * equivalent to WA Web's
     * {@code MsgSubtype.PaymentInviteAccountSetUp}.
     *
     * @param node   the parent {@code <notification>} stanza
     * @param invite the {@code <invite>} child node
     */
    private void handlePaymentInvite(Node node, Node invite) {
        var type = invite.getAttributeAsString("type", null);
        var service = invite.getAttributeAsString("service", null);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received payment invite notification type={0} service={1} id={2}",
                type, service, node.getAttributeAsString("id", "[missing-id]"));
        if (!"account-set-up".equals(type)) {
            return;
        }

        var from = invite.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            return;
        }

        var key = new MessageKeyBuilder()
                .id(RandomIdUtils.newId())
                .parentJid(from)
                .fromMe(false)
                .senderJid(from)
                .build();

        var timestampAttr = invite.getAttributeAsLong("t");
        var timestamp = timestampAttr.isPresent()
                ? Instant.ofEpochSecond(timestampAttr.getAsLong())
                : Instant.now();

        var info = new ChatMessageInfoBuilder()
                .key(key)
                .senderJid(from)
                .timestamp(timestamp)
                .status(MessageStatus.DELIVERED)
                .stubType(StubType.PAYMENT_ACTION_ACCOUNT_SETUP_REMINDER)
                .stubParameters(List.of(from.toString()))
                .message(MessageContainer.empty())
                .build();

        var chat = whatsapp.store()
                .findChatByJid(from)
                .orElseGet(() -> whatsapp.store().addNewChat(from));
        chat.setLastMsgTimestamp(timestamp);
        chat.setConversationTimestamp(timestamp);
        chat.setUnreadCount(chat.unreadCount().orElse(0) + 1);
        chat.addMessage(info);

        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewMessage(whatsapp, info));
        }
    }

    /**
     * Applies a transaction state change to the referenced message;
     * records the data as an orphan payment when the message is not
     * yet in the store.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebPaymentNotificationHandler.m(transaction)} which
     * resolves the message via
     * {@code getMessageFromCollection} (in-memory) then
     * {@code getMessageFromDb} (persistent) and falls back to
     * {@code WAWebSchemaOrphanPaymentNotification.getTable().createOrReplace}
     * when neither finds the message.
     *
     * @implNote
     * This implementation skips Novi-service transactions
     * ({@code service="NOVI"}) with a warning, matching WA Web's
     * comment {@code "Payment notification from Novi not supported"}.
     *
     * @param transaction the {@code <transaction>} child node
     */
    private void handlePaymentTransaction(Node transaction) {
        var service = transaction.getAttributeAsString("service", null);
        if (service != null && service.equalsIgnoreCase("NOVI")) {
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
     * Writes transaction fields onto the resolved chat message,
     * propagates the status onto the originating payment request when
     * one exists, and fires
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onMessageStatus}
     * for listeners.
     *
     * @apiNote
     * The originating request message (when this transaction fulfils a
     * payment request) is coerced through
     * {@link #determinePaymentRequestFulfilledStatus(PaymentInfo.TxnStatus)}
     * so a successful payment marks the request as fulfilled and any
     * other status resets it to
     * {@link PaymentInfo.TxnStatus#COLLECT_INIT}.
     *
     * @implNote
     * This implementation removes the orphan-payment record by message
     * id on success, mirroring WA Web's
     * {@code WAWebSchemaOrphanPaymentNotification.getTable().bulkRemove([msgId])}.
     *
     * @param chatMessageInfo the resolved chat message
     * @param transaction     the {@code <transaction>} child node
     * @param fromMe          whether the local account is the sender
     */
    private void applyPaymentTransaction(ChatMessageInfo chatMessageInfo, Node transaction, boolean fromMe) {
        var receiver = transaction.getAttributeAsJid("receiver").orElse(null);
        var type = transaction.getAttributeAsString("transaction-type", null);
        var status = transaction.getAttributeAsString("status", null);

        var paymentInfo = chatMessageInfo.paymentInfo().orElseGet(this::newPaymentInfo);
        paymentInfo.setReceiverJid(receiver);
        paymentInfo.setAmount1000(transaction.getAttributeAsLong("amount_1000", (Long) null));
        paymentInfo.setCurrency(transaction.getAttributeAsString("currency", null));
        paymentInfo.setTransactionTimestamp(transaction.getAttributeAsLong("ts", (Long) null));
        paymentInfo.setStatus(mapPaymentStatus(type, status, fromMe));
        paymentInfo.setTxnStatus(mapTxnStatus(type, status, fromMe));
        chatMessageInfo.setPaymentInfo(paymentInfo);

        paymentInfo.requestMessageKey().ifPresent(requestKey -> {
            var requestIdOpt = requestKey.id();
            var requestRemoteOpt = requestKey.parentJid();
            if (requestIdOpt.isEmpty() || requestRemoteOpt.isEmpty()) {
                return;
            }
            var requestMessage = whatsapp.store().findMessageByKey(requestKey).orElse(null);
            if (requestMessage == null) {
                requestMessage = whatsapp.store().findMessageById(requestRemoteOpt.get(), requestIdOpt.get()).orElse(null);
            }
            if (!(requestMessage instanceof ChatMessageInfo requestChat)) {
                return;
            }
            var requestPaymentInfo = requestChat.paymentInfo().orElseGet(this::newPaymentInfo);
            requestPaymentInfo.setStatus(paymentInfo.status().orElse(PaymentInfo.Status.UNKNOWN_STATUS));
            requestPaymentInfo.setTxnStatus(determinePaymentRequestFulfilledStatus(
                    paymentInfo.txnStatus().orElse(PaymentInfo.TxnStatus.UNKNOWN)));
            requestChat.setPaymentInfo(requestPaymentInfo);
            for (var listener : whatsapp.store().listeners()) {
                Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, requestChat));
            }
        });

        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, chatMessageInfo));
        }

        chatMessageInfo.key().id().ifPresent(whatsapp.store()::removeOrphanPaymentNotification);
    }

    /**
     * Coerces a transaction status into the status that should propagate
     * onto the originating payment-request message.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebPaymentStatusUtils.determinePaymentRequestFulfilledStatus}
     * which returns the input status when it is
     * {@link PaymentInfo.TxnStatus#COMPLETED} or
     * {@link PaymentInfo.TxnStatus#SUCCESS} and
     * {@link PaymentInfo.TxnStatus#COLLECT_INIT} otherwise.
     *
     * @param txnStatus the transaction status from the fulfilling payment
     * @return the coerced status to apply to the request
     */
    private PaymentInfo.TxnStatus determinePaymentRequestFulfilledStatus(PaymentInfo.TxnStatus txnStatus) {
        if (txnStatus == PaymentInfo.TxnStatus.COMPLETED || txnStatus == PaymentInfo.TxnStatus.SUCCESS) {
            return txnStatus;
        }
        return PaymentInfo.TxnStatus.COLLECT_INIT;
    }

    /**
     * Locates the chat message that this transaction targets, first by
     * exact key, then by id within the chat.
     *
     * @apiNote
     * Mirrors WA Web's {@code k(key, alt)} (collection lookup) and
     * {@code I(key, alt)} (DB lookup) functions which Cobalt collapses
     * into one call because the Cobalt store unifies the two storage
     * layers.
     *
     * @param remote      the chat/group JID
     * @param participant the sender JID in a group, or {@code null} for one-to-one chats
     * @param messageId   the message id to look up
     * @param fromMe      whether the local account is the sender
     * @return the matching {@link MessageInfo}, or {@code null} when not found
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
     * Returns a fresh {@link PaymentInfo} with {@code status =
     * UNKNOWN_STATUS} and {@code txnStatus = UNKNOWN}.
     *
     * @apiNote
     * Internal helper used by
     * {@link #applyPaymentTransaction(ChatMessageInfo, Node, boolean)}
     * when neither the resolved message nor the originating request
     * message has an existing {@link PaymentInfo}.
     *
     * @return the new {@link PaymentInfo}
     */
    private PaymentInfo newPaymentInfo() {
        return new PaymentInfoBuilder()
                .status(PaymentInfo.Status.UNKNOWN_STATUS)
                .txnStatus(PaymentInfo.TxnStatus.UNKNOWN)
                .build();
    }

    /**
     * Maps a resolved {@link PaymentMessageStatus} to the user-facing
     * {@link PaymentInfo.Status}.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebPaymentStatusUtils.getPaymentWebStatus(status, type)}
     * which is the source of truth for the status-to-display-state
     * mapping shown in the chat thread.
     *
     * @param type   the raw transaction-type string
     * @param status the raw status string
     * @param fromMe whether the local account is the sender
     * @return the mapped {@link PaymentInfo.Status}
     */
    private PaymentInfo.Status mapPaymentStatus(String type, String status, boolean fromMe) {
        return switch (paymentMessageStatus(type, status, fromMe)) {
            case SEND_PAY_INIT, SEND_PAY_PENDING, RECV_PAY_INIT, RECV_PAY_PENDING, RECV_PAY_RETRY_ON_FAILURE, REQUEST_PAY_INIT -> PaymentInfo.Status.PROCESSING;
            case SEND_PAY_PENDING_RECEIVER, SEND_PAY_FAILURE_RECEIVER -> PaymentInfo.Status.SENT;
            case REQUEST_PAY_SUCCESS -> paymentMessageTransactionType(type, fromMe) == PaymentMessageTransactionType.TYPE_P2P_REQ_SENT ? PaymentInfo.Status.WAITING_FOR_PAYER : PaymentInfo.Status.WAITING;
            case RECV_PAY_PENDING_SETUP -> PaymentInfo.Status.NEED_TO_ACCEPT;
            case SEND_PAY_SUCCESS, RECV_PAY_SUCCESS, REQUEST_PAY_FULFILLED -> PaymentInfo.Status.COMPLETE;
            case SEND_PAY_FAILURE, SEND_PAY_FAILURE_RISK, SEND_PAY_PENDING_REFUND, SEND_PAY_REFUND_PENDING, SEND_PAY_REFUND_FAILED, SEND_PAY_REFUND_FAILED_PROCESSING, RECV_PAY_FAILURE, REQUEST_PAY_FAILED, REQUEST_PAY_FAILED_RISK -> PaymentInfo.Status.COULD_NOT_COMPLETE;
            case SEND_PAY_REFUNDED -> PaymentInfo.Status.REFUNDED;
            case RECV_PAY_EXPIRED, REQUEST_PAY_EXPIRED, SEND_PAY_AUTH_CANCELED, SEND_PAY_AUTH_CANCEL_FAILED, SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING -> PaymentInfo.Status.EXPIRED;
            case REQUEST_PAY_REJECTED -> PaymentInfo.Status.REJECTED;
            case REQUEST_PAY_CANCELLED -> PaymentInfo.Status.CANCELLED;
            default -> PaymentInfo.Status.UNKNOWN_STATUS;
        };
    }

    /**
     * Maps a resolved {@link PaymentMessageStatus} to the fine-grained
     * {@link PaymentInfo.TxnStatus} that tracks the internal state
     * machine.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebPaymentStatusUtils.getPaymentTxnWebStatus(status)}
     * mapping table.
     *
     * @param type   the raw transaction-type string
     * @param status the raw status string
     * @param fromMe whether the local account is the sender
     * @return the mapped {@link PaymentInfo.TxnStatus}
     */
    private PaymentInfo.TxnStatus mapTxnStatus(String type, String status, boolean fromMe) {
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
     * Resolves a raw transaction-type and status pair into the
     * fine-grained {@link PaymentMessageStatus} used by the two
     * mapping functions above.
     *
     * @apiNote
     * Mirrors WA Web's status-mapping switch inside
     * {@code WAWebPaymentStatusUtils.getPaymentWebStatus} which uses
     * the resolved transaction type as the outer key and the
     * uppercased status string as the inner key.
     *
     * @param type   the raw transaction-type string, or {@code null}
     * @param status the raw status string, or {@code null}
     * @param fromMe whether the local account is the sender
     * @return the resolved {@link PaymentMessageStatus}
     */
    private PaymentMessageStatus paymentMessageStatus(String type, String status, boolean fromMe) {
        var statusValue = status == null ? "" : status.toUpperCase();
        return switch (paymentMessageTransactionType(type, fromMe)) {
            case TYPE_P2M_PAYOUT -> PaymentMessageStatus.STATUS_UNSET;
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
                case "AUTH_CANCELED" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCELED;
                case "CANCELLED" -> PaymentMessageStatus.SEND_PAY_USER_CANCELED;
                case "EXPIRED" -> PaymentMessageStatus.SEND_PAY_EXPIRED;
                case "IN_REVIEW" -> PaymentMessageStatus.SEND_PAY_IN_REVIEW;
                case "PENDING" -> PaymentMessageStatus.SEND_PAY_PENDING_PROCESSING;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_RCVD, TYPE_P2M_RCVD -> switch (statusValue) {
                case "PENDING_SETUP" -> PaymentMessageStatus.RECV_PAY_PENDING_SETUP;
                case "FAILED_DA" -> PaymentMessageStatus.RECV_PAY_PENDING;
                case "FAILED_PROCESSING" -> PaymentMessageStatus.RECV_PAY_RETRY_ON_FAILURE;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.RECV_PAY_SUCCESS;
                case "FAILURE", "FAILED" -> PaymentMessageStatus.RECV_PAY_FAILURE;
                case "EXPIRED" -> PaymentMessageStatus.RECV_PAY_EXPIRED;
                case "FAILED_RISK" -> PaymentMessageStatus.RECV_PAY_FAILURE_RISK;
                case "WITHDRAWAL_PROCESSING" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PROCESSING;
                case "WITHDRAWAL_FAILURE" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_FAILURE;
                case "WITHDRAWAL_PERMANENT_FAILED" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PERMANENT_FAILED;
                case "CANCELLED" -> PaymentMessageStatus.RECV_PAY_SENDER_CANCELED;
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
                case "CANCELLED" -> PaymentMessageStatus.WITHDRAWAL_USER_CANCELED;
                case "EXPIRED" -> PaymentMessageStatus.WITHDRAWAL_EXPIRED;
                case "WITHDRAWAL_ACTIVE" -> PaymentMessageStatus.WITHDRAWAL_ACTIVE;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_UNSET, TYPE_P2P_GRP, TYPE_P2P_NO_INFO, TYPE_FUTURE, TYPE_P2P_REQ_GRP, TYPE_MISSING_DETAILS ->
                    PaymentMessageStatus.STATUS_UNSET;
        };
    }

    /**
     * Resolves the raw transaction-type string and {@code fromMe} flag
     * into a {@link PaymentMessageTransactionType}.
     *
     * @apiNote
     * The fallback for {@code null}/unknown types is
     * {@link PaymentMessageTransactionType#TYPE_P2P_SENT} when
     * {@code fromMe} is {@code true} and
     * {@link PaymentMessageTransactionType#TYPE_P2P_RCVD} otherwise,
     * matching the defensive default WA Web's status mapper applies
     * when the server omits the field.
     *
     * @param type   the raw transaction-type string, or {@code null}
     * @param fromMe whether the local account is the sender
     * @return the resolved {@link PaymentMessageTransactionType}
     */
    private PaymentMessageTransactionType paymentMessageTransactionType(String type, boolean fromMe) {
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
     * Sends the {@code <ack class="notification" type="pay"/>} stanza.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code E(success)} ack-builder inside
     * {@code WAWebPaymentNotificationHandler.handlePaymentNotification}.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("pay").send();
    }
}
