package com.github.auties00.cobalt.message.receive.stanza;

import java.util.Optional;

/**
 * Parsed payment information from the {@code <pay>} and
 * {@code <transaction>} children of an incoming message stanza.
 *
 * <p>Payment messages carry metadata about the payment flow (send,
 * request, invite, etc.), the receiver, and the transaction details.
 * The {@code <pay>} and {@code <transaction>} nodes are siblings
 * (both direct children of the message node), not nested.
 *
 * @implNote WAWebHandleMsgParser function R(): parses pay node type,
 * receiver JID string, transaction currency/amount/timestamp/status,
 * and novi/futureproof detection.
 */
public final class MessageReceivePaymentInfo {
    /**
     * Whether this is a futureproofed (novi) transaction.
     *
     * @implNote WAWebHandleMsgParser function R(): set when
     * {@code isNoviTransaction(pay)} or {@code isNoviTransaction(transaction)}.
     */
    private final boolean futureproofed;

    /**
     * The receiver JID as a string from the payment metadata.
     *
     * @implNote WAWebHandleMsgParser function R(): {@code l.receiver.toString()}
     * for transaction, or {@code n.attrString("receiver")} / {@code e.attrString("recipient")}
     * for pay send.
     */
    private final String receiverJid;

    /**
     * The currency code from the transaction or pay node.
     *
     * @implNote WAWebHandleMsgParser function R(): {@code l.currency} or
     * {@code getAmount1000AndCurrency(n).currency}
     */
    private final String currency;

    /**
     * The payment amount in 1/1000 units.
     *
     * @implNote WAWebHandleMsgParser function R(): {@code l.amount1000} or
     * {@code getAmount1000AndCurrency(n).amount1000}
     */
    private final Long amount1000;

    /**
     * The transaction timestamp.
     *
     * @implNote WAWebHandleMsgParser function R(): {@code l.ts} or
     * {@code e.attrInt("t")} for pay send.
     */
    private final Long transactionTimestamp;

    /**
     * The transaction status, present when the payment is relevant
     * to the current user (not a group payment between other parties).
     *
     * @implNote WAWebHandleMsgParser function R(): {@code getPaymentTxnWebStatus(l.status)}
     * or {@code PaymentInfo$TxnStatus.INIT} for pay send.
     */
    private final String txnStatus;

    /**
     * Constructs a new payment info with the given parameters.
     *
     * @param futureproofed        whether this is a futureproofed novi transaction
     * @param receiverJid          the receiver JID string (nullable)
     * @param currency             the currency code (nullable)
     * @param amount1000           the amount in 1/1000 units (nullable)
     * @param transactionTimestamp the transaction timestamp (nullable)
     * @param txnStatus            the transaction status (nullable)
     * @implNote WAWebHandleMsgParser function R()
     */
    public MessageReceivePaymentInfo(
            boolean futureproofed,
            String receiverJid,
            String currency,
            Long amount1000,
            String txnStatus,
            Long transactionTimestamp
    ) {
        this.futureproofed = futureproofed;
        this.receiverJid = receiverJid;
        this.currency = currency;
        this.amount1000 = amount1000;
        this.txnStatus = txnStatus;
        this.transactionTimestamp = transactionTimestamp;
    }

    /**
     * Returns whether this is a futureproofed (novi) transaction.
     *
     * @return {@code true} if futureproofed
     * @implNote WAWebHandleMsgParser function R(): {@code futureproofed: true}
     */
    public boolean futureproofed() {
        return futureproofed;
    }

    /**
     * Returns the payment receiver JID as a string.
     *
     * @return an {@link Optional} containing the receiver JID string
     * @implNote WAWebHandleMsgParser function R(): {@code receiverJid: l.receiver.toString()}
     */
    public Optional<String> receiverJid() {
        return Optional.ofNullable(receiverJid);
    }

    /**
     * Returns the currency code from the transaction.
     *
     * @return an {@link Optional} containing the currency code
     * @implNote WAWebHandleMsgParser function R(): {@code currency: l.currency}
     */
    public Optional<String> currency() {
        return Optional.ofNullable(currency);
    }

    /**
     * Returns the payment amount in 1/1000 units.
     *
     * @return an {@link Optional} containing the amount
     * @implNote WAWebHandleMsgParser function R(): {@code amount1000: l.amount1000}
     */
    public Optional<Long> amount1000() {
        return Optional.ofNullable(amount1000);
    }

    /**
     * Returns the transaction status.
     *
     * @return an {@link Optional} containing the transaction status
     * @implNote WAWebHandleMsgParser function R(): {@code txnStatus}
     */
    public Optional<String> txnStatus() {
        return Optional.ofNullable(txnStatus);
    }

    /**
     * Returns the transaction timestamp.
     *
     * @return an {@link Optional} containing the timestamp
     * @implNote WAWebHandleMsgParser function R(): {@code transactionTimestamp: l.ts}
     */
    public Optional<Long> transactionTimestamp() {
        return Optional.ofNullable(transactionTimestamp);
    }
}
