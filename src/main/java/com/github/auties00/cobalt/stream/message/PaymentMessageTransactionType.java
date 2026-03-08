package com.github.auties00.cobalt.stream.message;

public enum PaymentMessageTransactionType {
    TYPE_P2P_SENT,
    TYPE_P2P_RCVD,
    TYPE_P2P_REQ_SENT,
    TYPE_P2P_REQ_RCVD,
    TYPE_P2P_REQ_SCHEDULED_PAYMENT_RCVD,
    TYPE_P2M_SENT,
    TYPE_P2M_RCVD,
    TYPE_P2M_PAYOUT,
    TYPE_DEPOSIT,
    TYPE_REFUND,
    TYPE_WITHDRAWAL
}
