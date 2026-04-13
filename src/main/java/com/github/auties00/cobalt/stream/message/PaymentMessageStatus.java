package com.github.auties00.cobalt.stream.message;

/**
 * Enumerates all fine-grained payment message status values used internally by the
 * payment notification and transaction handling pipeline.
 *
 * <p>Each constant corresponds to a specific combination of transaction type and
 * server-reported status string, as defined in the WhatsApp Web payment status
 * utilities. The mapping from raw server status strings to these constants is
 * performed by the {@code paymentMessageStatus} helper methods in the notification
 * and message stream handlers.
 *
 * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus
 */
public enum PaymentMessageStatus {
    /**
     * No status has been set or the status could not be determined from
     * the server-reported values.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.STATUS_UNSET
     */
    STATUS_UNSET,

    /**
     * A payment request has been initialized.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_INIT
     */
    REQUEST_PAY_INIT,

    /**
     * A payment request has been successfully collected.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_SUCCESS
     */
    REQUEST_PAY_SUCCESS,

    /**
     * A payment request has failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_FAILED
     */
    REQUEST_PAY_FAILED,

    /**
     * A payment request has failed due to risk assessment.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_FAILED_RISK
     */
    REQUEST_PAY_FAILED_RISK,

    /**
     * A payment request has been rejected by the payer.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_REJECTED
     */
    REQUEST_PAY_REJECTED,

    /**
     * A payment request has expired before being fulfilled.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_EXPIRED
     */
    REQUEST_PAY_EXPIRED,

    /**
     * A payment request has been fulfilled by the payer.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_FULFILLED
     */
    REQUEST_PAY_FULFILLED,

    /**
     * A payment request has been cancelled by the requester.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_CANCELLED
     */
    REQUEST_PAY_CANCELLED,

    /**
     * A payment request is in the process of being cancelled.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_CANCELLING
     */
    REQUEST_PAY_CANCELLING,

    /**
     * A scheduled payment request has been successfully collected.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.REQUEST_PAY_SCHEDULED_PAYMENT_SUCCESS
     */
    REQUEST_PAY_SCHEDULED_PAYMENT_SUCCESS,

    /**
     * A received payment has been initialized.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_INIT
     */
    RECV_PAY_INIT,

    /**
     * A received payment is pending setup by the receiver.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_PENDING_SETUP
     */
    RECV_PAY_PENDING_SETUP,

    /**
     * A received payment is pending due to a direct account settlement issue.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_PENDING
     */
    RECV_PAY_PENDING,

    /**
     * A received payment failed but a retry is being attempted.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_RETRY_ON_FAILURE
     */
    RECV_PAY_RETRY_ON_FAILURE,

    /**
     * A received payment has failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_FAILURE
     */
    RECV_PAY_FAILURE,

    /**
     * A received payment has been successfully processed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_SUCCESS
     */
    RECV_PAY_SUCCESS,

    /**
     * A received payment has expired.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_EXPIRED
     */
    RECV_PAY_EXPIRED,

    /**
     * A received payment has failed due to risk assessment.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_FAILURE_RISK
     */
    RECV_PAY_FAILURE_RISK,

    /**
     * A received payment withdrawal is being processed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_WITHDRAWAL_PROCESSING
     */
    RECV_PAY_WITHDRAWAL_PROCESSING,

    /**
     * A received payment withdrawal has failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_WITHDRAWAL_FAILURE
     */
    RECV_PAY_WITHDRAWAL_FAILURE,

    /**
     * A received payment withdrawal has permanently failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_WITHDRAWAL_PERMANENT_FAILED
     */
    RECV_PAY_WITHDRAWAL_PERMANENT_FAILED,

    /**
     * A received payment has been cancelled by the sender.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.RECV_PAY_SENDER_CANCELED
     */
    RECV_PAY_SENDER_CANCELED,

    /**
     * A sent payment has been initialized.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_INIT
     */
    SEND_PAY_INIT,

    /**
     * A sent payment is pending receiver setup.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_PENDING_RECEIVER
     */
    SEND_PAY_PENDING_RECEIVER,

    /**
     * A sent payment is pending due to a direct account settlement issue.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_PENDING
     */
    SEND_PAY_PENDING,

    /**
     * A sent payment refund is pending due to a direct account settlement issue.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_REFUND_PENDING
     */
    SEND_PAY_REFUND_PENDING,

    /**
     * A sent payment has been successfully processed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_SUCCESS
     */
    SEND_PAY_SUCCESS,

    /**
     * A sent payment has failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_FAILURE
     */
    SEND_PAY_FAILURE,

    /**
     * A sent payment has failed due to risk assessment.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_FAILURE_RISK
     */
    SEND_PAY_FAILURE_RISK,

    /**
     * A sent payment has been refunded.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_REFUNDED
     */
    SEND_PAY_REFUNDED,

    /**
     * A refund for a sent payment has failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_REFUND_FAILED
     */
    SEND_PAY_REFUND_FAILED,

    /**
     * A sent payment has failed during receiver-side processing.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_FAILURE_RECEIVER
     */
    SEND_PAY_FAILURE_RECEIVER,

    /**
     * A refund for a sent payment has failed during processing.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_REFUND_FAILED_PROCESSING
     */
    SEND_PAY_REFUND_FAILED_PROCESSING,

    /**
     * A sent payment is pending refund after a final direct account settlement failure.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_PENDING_REFUND
     */
    SEND_PAY_PENDING_REFUND,

    /**
     * A sent payment authorization cancellation has failed during processing.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING
     */
    SEND_PAY_AUTH_CANCEL_FAILED_PROCESSING,

    /**
     * A sent payment authorization cancellation has failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_AUTH_CANCEL_FAILED
     */
    SEND_PAY_AUTH_CANCEL_FAILED,

    /**
     * A sent payment authorization has been cancelled.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_AUTH_CANCELED
     */
    SEND_PAY_AUTH_CANCELED,

    /**
     * A sent payment has expired.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_EXPIRED
     */
    SEND_PAY_EXPIRED,

    /**
     * A sent payment authorization has succeeded.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_AUTH_SUCCESS
     */
    SEND_PAY_AUTH_SUCCESS,

    /**
     * A sent payment authorization success is being cancelled.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_AUTH_SUCCESS_CANCELING
     */
    SEND_PAY_AUTH_SUCCESS_CANCELING,

    /**
     * A sent payment is under review by the payment provider.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_IN_REVIEW
     */
    SEND_PAY_IN_REVIEW,

    /**
     * A sent payment is pending processing.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_PENDING_PROCESSING
     */
    SEND_PAY_PENDING_PROCESSING,

    /**
     * A sent payment has been cancelled by the user.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.SEND_PAY_USER_CANCELED
     */
    SEND_PAY_USER_CANCELED,

    /**
     * A withdrawal has been initialized.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_INIT
     */
    WITHDRAWAL_INIT,

    /**
     * A withdrawal is pending processing.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_PENDING
     */
    WITHDRAWAL_PENDING,

    /**
     * A withdrawal is under review.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_IN_REVIEW
     */
    WITHDRAWAL_IN_REVIEW,

    /**
     * A withdrawal has been successfully processed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_SUCCESS
     */
    WITHDRAWAL_SUCCESS,

    /**
     * A withdrawal has failed.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_FAILED
     */
    WITHDRAWAL_FAILED,

    /**
     * A withdrawal has been cancelled by the user.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_USER_CANCELED
     */
    WITHDRAWAL_USER_CANCELED,

    /**
     * A withdrawal has expired.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_EXPIRED
     */
    WITHDRAWAL_EXPIRED,

    /**
     * A withdrawal is currently active.
     *
     * @implNote WAWebPaymentStatusUtils.NotificationTransactionStatus.WITHDRAWAL_ACTIVE
     */
    WITHDRAWAL_ACTIVE
}
