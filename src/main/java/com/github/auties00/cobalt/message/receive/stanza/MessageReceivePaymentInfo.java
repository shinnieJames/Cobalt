package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Optional;

/**
 * Parsed payment information from the {@code <pay>} and
 * {@code <transaction>} children of an incoming message stanza.
 *
 * <p>Payment messages carry metadata about the payment flow (send,
 * request, invite, etc.), the receiver, and the transaction details.
 *
 * @apiNote WAWebHandleMsgParser function L(): parses pay node type,
 * pay receiver JID, and transaction child with amount, currency,
 * extended_text_message flag, and futureproof status.
 * WAWebHandleMsgCommon.PAY_NODE_TYPES: send, request, futureproof,
 * request-decline, request-cancel, invite.
 */
public final class MessageReceivePaymentInfo {
    private final String payType;
    private final Jid receiverJid;
    private final String currency;
    private final Long amount1000;
    private final String transactionStatus;
    private final Long transactionTimestamp;

    public MessageReceivePaymentInfo(
            String payType,
            Jid receiverJid,
            String currency,
            Long amount1000,
            String transactionStatus,
            Long transactionTimestamp
    ) {
        this.payType = payType;
        this.receiverJid = receiverJid;
        this.currency = currency;
        this.amount1000 = amount1000;
        this.transactionStatus = transactionStatus;
        this.transactionTimestamp = transactionTimestamp;
    }

    /**
     * Returns the payment type from the {@code type} attribute of the
     * {@code <pay>} node (e.g. "send", "request", "futureproof",
     * "request-decline", "request-cancel", "invite").
     */
    public Optional<String> payType() {
        return Optional.ofNullable(payType);
    }

    /**
     * Returns the payment receiver JID from the {@code <pay>} node's
     * {@code jid} attribute.
     */
    public Optional<Jid> receiverJid() {
        return Optional.ofNullable(receiverJid);
    }

    /**
     * Returns the currency code from the {@code <transaction>} child.
     */
    public Optional<String> currency() {
        return Optional.ofNullable(currency);
    }

    /**
     * Returns the payment amount in 1/1000 units from the
     * {@code <transaction>} child's {@code amount_1000} attribute.
     */
    public Optional<Long> amount1000() {
        return Optional.ofNullable(amount1000);
    }

    /**
     * Returns the transaction status from the {@code <transaction>}
     * child's {@code status} attribute.
     */
    public Optional<String> transactionStatus() {
        return Optional.ofNullable(transactionStatus);
    }

    /**
     * Returns the transaction timestamp from the {@code <transaction>}
     * child's {@code t} attribute.
     */
    public Optional<Long> transactionTimestamp() {
        return Optional.ofNullable(transactionTimestamp);
    }
}
