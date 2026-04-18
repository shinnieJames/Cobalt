package com.github.auties00.cobalt.stream.message;

/**
 * Enumerates the transaction types used by the payment notification and transaction
 * handling pipeline to classify a payment's direction and mechanism.
 *
 * <p>Each constant corresponds to a specific server-reported transaction type string
 * combined with the {@code fromMe} flag, as defined in the WhatsApp Web payment
 * status utilities. The mapping from raw server type strings to these constants is
 * performed by the {@code paymentMessageTransactionType} helper methods.
 *
 * @implNote WAWebPaymentStatusUtils.PaymentTransactionType
 */
public enum PaymentMessageTransactionType {
    /**
     * No transaction type has been set or the type is unknown.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_UNSET
     */
    TYPE_UNSET,

    /**
     * A peer-to-peer payment sent by the current user.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_SENT
     */
    TYPE_P2P_SENT,

    /**
     * A peer-to-peer payment received by the current user.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_RCVD
     */
    TYPE_P2P_RCVD,

    /**
     * A peer-to-peer payment within a group conversation.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_GRP
     */
    TYPE_P2P_GRP,

    /**
     * A peer-to-peer payment with no direction information available.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_NO_INFO
     */
    TYPE_P2P_NO_INFO,

    /**
     * A future-dated peer-to-peer payment.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_FUTURE
     */
    TYPE_FUTURE,

    /**
     * A peer-to-peer payment request sent by the current user.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_REQ_SENT
     */
    TYPE_P2P_REQ_SENT,

    /**
     * A peer-to-peer payment request received by the current user.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_REQ_RCVD
     */
    TYPE_P2P_REQ_RCVD,

    /**
     * A scheduled payment for a received peer-to-peer request.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_REQ_SCHEDULED_PAYMENT_RCVD
     */
    TYPE_P2P_REQ_SCHEDULED_PAYMENT_RCVD,

    /**
     * A peer-to-peer payment request within a group conversation.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2P_REQ_GRP
     */
    TYPE_P2P_REQ_GRP,

    /**
     * A person-to-merchant payment sent by the current user.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2M_SENT
     */
    TYPE_P2M_SENT,

    /**
     * A person-to-merchant payment received by the current user (merchant).
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2M_RCVD
     */
    TYPE_P2M_RCVD,

    /**
     * A merchant payout transaction.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_P2M_PAYOUT
     */
    TYPE_P2M_PAYOUT,

    /**
     * A transaction whose details are missing or incomplete.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_MISSING_DETAILS
     */
    TYPE_MISSING_DETAILS,

    /**
     * A deposit transaction.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_DEPOSIT
     */
    TYPE_DEPOSIT,

    /**
     * A refund transaction.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_REFUND
     */
    TYPE_REFUND,

    /**
     * A withdrawal transaction.
     *
     * @implNote WAWebPaymentStatusUtils.PaymentTransactionType.TYPE_WITHDRAWAL
     */
    TYPE_WITHDRAWAL
}
