package com.github.auties00.cobalt.stream.message;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.receive.receipt.MessageReceiptHandler;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanza;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveStanzaParser;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayload;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncPayloadSpec;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyShare;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyShareBuilder;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestResponseMessage;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestType;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotificationBuilder;
import com.github.auties00.cobalt.model.payment.PaymentInfo;
import com.github.auties00.cobalt.model.payment.PaymentInfoBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.model.sync.SyncCollectionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class MessageStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(MessageStreamHandler.class.getName());

    private final WhatsAppClient whatsapp;
    private final MessageService messageService;
    private final MessageReceiptHandler receiptHandler;
    private final SnapshotRecoveryService snapshotRecoveryService;
    private final LidMigrationService lidMigrationService;

    public MessageStreamHandler(
            WhatsAppClient whatsapp,
            MessageService messageService,
            SnapshotRecoveryService snapshotRecoveryService,
            LidMigrationService lidMigrationService
    ) {
        this.whatsapp = whatsapp;
        this.messageService = Objects.requireNonNull(messageService, "messageService cannot be null");
        this.receiptHandler = new MessageReceiptHandler(whatsapp);
        this.snapshotRecoveryService = Objects.requireNonNull(snapshotRecoveryService, "snapshotRecoveryService cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
    }

    @Override
    public void handle(Node node) {
        if ("medianotify".equals(node.getAttributeAsString("type", null))) {
            sendAck(node);
            return;
        }

        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            sendNack(node, "400");
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
            sendNack(node, "400");
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
                var quoted = findQuotedMessage(info);
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
            sendNack(node, "500");
        }
    }

    private void handleNewsletterMessage(Node node) {
        try {
            var info = messageService.process(node);
            if (info == null) {
                return;
            }

            storeIncomingMessage(info);
            resolveOrphanPayment(info);
            var quoted = findQuotedMessage(info);
            notifyMessageReceived(info, quoted);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle newsletter message stanza: {0}",
                    exception.getMessage());
        }
    }

    private void handleReceiveFailure(
            MessageReceiveStanza stanza,
            WhatsAppMessageException.Receive exception
    ) {
        if (!whatsapp.store().automaticMessageReceipts()) {
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

    private static int parseErrorCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 500;
        }
    }

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

    private Optional<MessageInfo> findQuotedMessage(MessageInfo info) {
        return contextInfo(info)
                .flatMap(context -> {
                    var quotedId = context.stanzaId().orElse(null);
                    var provider = context.remoteJid()
                            .or(() -> info.key().parentJid())
                            .orElse(null);
                    if (quotedId == null || provider == null) {
                        return Optional.empty();
                    }

                    return whatsapp.store()
                            .findMessageById(provider, quotedId)
                            .map(MessageInfo.class::cast);
                });
    }

    private Optional<ContextInfo> contextInfo(MessageInfo info) {
        return switch (info) {
            case NewsletterMessageInfo newsletterInfo -> newsletterInfo.message()
                    .contentWithContext()
                    .flatMap(ContextualMessage::contextInfo);
            case ChatMessageInfo chatInfo -> chatInfo.message()
                    .contentWithContext()
                    .flatMap(ContextualMessage::contextInfo);
        };
    }

    private void notifyMessageReceived(MessageInfo info, Optional<MessageInfo> quotedMessage) {
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

    private boolean isStatusMessage(MessageInfo info) {
        return info.key()
                .parentJid()
                .map(Jid::isStatusBroadcastAccount)
                .orElse(false);
    }

    private void resolveOrphanPayment(MessageInfo info) {
        if (!(info instanceof ChatMessageInfo chatMessageInfo)) {
            return;
        }

        var messageId = chatMessageInfo.id();
        var orphan = whatsapp.store().removeOrphanPaymentNotification(messageId).orElse(null);
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
                .attribute("sender", chatMessageInfo.senderJid())
                .build();
        handlePaymentTransaction(transactionNode);
    }

    private void handlePaymentTransaction(Node transaction) {
        var sender = transaction.getAttributeAsJid("sender").orElse(null);
        var receiver = transaction.getAttributeAsJid("receiver").orElse(null);
        var messageId = transaction.getAttributeAsString("message-id", null);
        if (sender == null || receiver == null || messageId == null) {
            return;
        }

        var self = whatsapp.store().jid().orElse(null);
        var fromMe = self != null && java.util.Objects.equals(self.toUserJid(), sender.toUserJid());
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

        paymentInfo.setReceiverJid(receiver)
                .setAmount1000(transaction.getAttributeAsLong("amount_1000", (Long) null))
                .setCurrency(transaction.getAttributeAsString("currency", null))
                .setTransactionTimestamp(transaction.getAttributeAsLong("ts", (Long) null))
                .setStatus(mapPaymentStatus(type, status, fromMe))
                .setTxnStatus(mapTxnStatus(type, status, fromMe));

        chatMessageInfo.setPaymentInfo(paymentInfo);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onMessageStatus(whatsapp, chatMessageInfo));
        }
        whatsapp.store().removeOrphanPaymentNotification(messageId);
    }

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

    private PaymentInfo newPaymentInfo() {
        return new PaymentInfoBuilder()
                .status(PaymentInfo.Status.UNKNOWN_STATUS)
                .txnStatus(PaymentInfo.TxnStatus.UNKNOWN)
                .build();
    }

    private PaymentInfo.Status mapPaymentStatus(String type, String status, boolean fromMe) {
        return switch (paymentMessageStatus(type, status, fromMe)) {
            case SEND_PAY_INIT, SEND_PAY_PENDING, RECV_PAY_INIT, RECV_PAY_PENDING, RECV_PAY_RETRY_ON_FAILURE, REQUEST_PAY_INIT -> PaymentInfo.Status.PROCESSING;
            case SEND_PAY_PENDING_RECEIVER, SEND_PAY_FAILURE_RECEIVER -> PaymentInfo.Status.SENT;
            case REQUEST_PAY_SUCCESS -> paymentMessageTransactionType(type, fromMe) == PaymentMessageTransactionType.TYPE_P2P_REQ_SENT ? PaymentInfo.Status.WAITING_FOR_PAYER : PaymentInfo.Status.WAITING;
            case RECV_PAY_PENDING_SETUP -> PaymentInfo.Status.NEED_TO_ACCEPT;
            case SEND_PAY_SUCCESS, RECV_PAY_SUCCESS, REQUEST_PAY_FULFILLED -> PaymentInfo.Status.COMPLETE;
            case SEND_PAY_FAILURE, SEND_PAY_FAILURE_RISK, SEND_PAY_PENDING_REFUND, SEND_PAY_REFUND_PENDING, SEND_PAY_REFUND_FAILED, SEND_PAY_REFUND_FAILED_PROCESSING, RECV_PAY_FAILURE, REQUEST_PAY_FAILED, REQUEST_PAY_FAILED_RISK -> PaymentInfo.Status.COULD_NOT_COMPLETE;
            case SEND_PAY_REFUNDED -> PaymentInfo.Status.REFUNDED;
            case RECV_PAY_EXPIRED, REQUEST_PAY_EXPIRED, SEND_PAY_AUTH_CANCELED, SEND_PAY_AUTH_CANCEL_FAILED, SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING, SEND_PAY_EXPIRED -> PaymentInfo.Status.EXPIRED;
            case REQUEST_PAY_REJECTED -> PaymentInfo.Status.REJECTED;
            case REQUEST_PAY_CANCELLED -> PaymentInfo.Status.CANCELLED;
            default -> PaymentInfo.Status.UNKNOWN_STATUS;
        };
    }

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

    private PaymentMessageStatus paymentMessageStatus(String type, String status, boolean fromMe) {
        var statusValue = status == null ? "" : status.toUpperCase();
        return switch (paymentMessageTransactionType(type, fromMe)) {
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
                case "AUTH_CANCELED", "CANCELED" -> PaymentMessageStatus.SEND_PAY_AUTH_CANCELED;
                case "EXPIRED" -> PaymentMessageStatus.SEND_PAY_EXPIRED;
                case "IN_REVIEW" -> PaymentMessageStatus.SEND_PAY_IN_REVIEW;
                case "PENDING" -> PaymentMessageStatus.SEND_PAY_PENDING_PROCESSING;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
            case TYPE_P2P_RCVD, TYPE_P2M_RCVD -> switch (statusValue) {
                case "PENDING_SETUP" -> PaymentMessageStatus.RECV_PAY_PENDING_SETUP;
                case "FAILED_DA", "PENDING" -> PaymentMessageStatus.RECV_PAY_PENDING;
                case "FAILED_PROCESSING" -> PaymentMessageStatus.RECV_PAY_RETRY_ON_FAILURE;
                case "SUCCESS", "COMPLETED" -> PaymentMessageStatus.RECV_PAY_SUCCESS;
                case "FAILURE", "FAILED" -> PaymentMessageStatus.RECV_PAY_FAILURE;
                case "EXPIRED" -> PaymentMessageStatus.RECV_PAY_EXPIRED;
                case "FAILED_RISK" -> PaymentMessageStatus.RECV_PAY_FAILURE_RISK;
                case "WITHDRAWAL_PROCESSING" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PROCESSING;
                case "WITHDRAWAL_FAILURE" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_FAILURE;
                case "WITHDRAWAL_PERMANENT_FAILED" -> PaymentMessageStatus.RECV_PAY_WITHDRAWAL_PERMANENT_FAILED;
                case "CANCELED" -> PaymentMessageStatus.RECV_PAY_SENDER_CANCELED;
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
                case "CANCELED" -> PaymentMessageStatus.WITHDRAWAL_USER_CANCELED;
                case "EXPIRED" -> PaymentMessageStatus.WITHDRAWAL_EXPIRED;
                case "WITHDRAWAL_ACTIVE" -> PaymentMessageStatus.WITHDRAWAL_ACTIVE;
                default -> PaymentMessageStatus.STATUS_UNSET;
            };
        };
    }

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
                .ifPresent(this::processAppStateSyncKeyShare);

        protocolMessage.appStateSyncKeyRequest()
                .ifPresent(request -> processAppStateSyncKeyRequest(info, request));
    }

    private void processAppStateSyncKeyShare(AppStateSyncKeyShare keyShare) {
        var keys = keyShare.keys();
        if (keys.isEmpty()) {
            return;
        }

        whatsapp.store().addWebAppStateKeys(keys);

        var resolvedAny = false;
        for (var key : keys) {
            var keyId = key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyId != null && whatsapp.store().findMissingSyncKey(keyId).isPresent()) {
                whatsapp.store().removeMissingSyncKey(keyId);
                resolvedAny = true;
            }
        }

        if (resolvedAny) {
            for (var patchType : SyncPatchType.values()) {
                var metadata = whatsapp.store().findWebAppState(patchType);
                if (metadata.state() == SyncCollectionState.BLOCKED) {
                    whatsapp.store().markWebAppStateDirty(patchType);
                }
            }
        }
    }

    private void processAppStateSyncKeyRequest(
            ChatMessageInfo info,
            com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyRequest request
    ) {
        var sender = info.senderJid();
        if (sender == null) {
            return;
        }

        var keysToShare = new ArrayList<com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey>();
        for (var requestedKeyId : request.keyIds()) {
            var rawKeyId = requestedKeyId.keyId().orElse(null);
            if (rawKeyId == null) {
                continue;
            }

            whatsapp.store().findWebAppStateKeyById(rawKeyId)
                    .ifPresent(keysToShare::add);
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
                    .id(MessageKey.randomId(whatsapp.store().clientType()))
                    .chatJid(self)
                    .fromMe(true)
                    .senderJid(self)
                    .build();
            var response = new ChatMessageInfoBuilder()
                    .key(key)
                    .message(messageContainer)
                    .timestamp(java.time.Instant.now())
                    .senderJid(self)
                    .build();
            whatsapp.sendPeerMessage(sender, response);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to answer app state sync key request from {0}: {1}",
                    sender,
                    throwable.getMessage());
        }
    }

    private void resolveSnapshotRecovery(PeerDataOperationRequestResponseMessage response) {
        if (response.peerDataOperationRequestType().orElse(null)
                != PeerDataOperationRequestType.COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY) {
            return;
        }

        for (var result : response.peerDataOperationResult()) {
            var recovery = result.syncdSnapshotFatalRecoveryResponse().orElse(null);
            if (recovery == null) {
                continue;
            }

            try {
                var snapshot = snapshotRecoveryService.decodeRecoverySnapshot(recovery);
                var collectionName = snapshot.collectionName()
                        .flatMap(SyncPatchType::of)
                        .orElse(null);
                if (collectionName != null) {
                    snapshotRecoveryService.resolveRecovery(collectionName, recovery);
                }
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Failed to decode snapshot recovery response: {0}",
                        exception.getMessage());
            }
        }
    }

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
