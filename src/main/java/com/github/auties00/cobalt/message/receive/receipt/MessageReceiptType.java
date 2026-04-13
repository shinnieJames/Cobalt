package com.github.auties00.cobalt.message.receive.receipt;

/**
 * Types of receipt stanzas sent in response to incoming messages or
 * as acknowledgment of message state changes (read, played, etc.).
 *
 * <p>After decrypting and processing an incoming message, the client sends
 * a receipt back to the server to indicate the outcome.  The receipt type
 * determines how the server and sending client handle the acknowledgment.
 *
 * <p>Additional receipt types (READ, PLAYED, etc.) are used when sending
 * aggregate receipts for outbound state changes via
 * {@code WAWebSendReceiptJobCommon.sendAggregateReceipts}.
 *
 * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE: frozen object with
 * INACTIVE, SENDER, DELIVERY, READ, READ_SELF, PLAYED, PLAYED_SELF,
 * HISTORY_SYNC_COMPLETION, SERVER_ERROR, PEER_MSG.
 * WAWebHandleMsgSendReceipt.sendReceipt: selects the receipt type based
 * on the E2E processing result and message metadata.
 */
public enum MessageReceiptType {
    /**
     * Standard delivery receipt — message was successfully decrypted and
     * processed.  No {@code type} attribute is set on the receipt stanza
     * (the value is dropped/omitted).
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.DELIVERY:
     * value is {@code "delivery"} but when used in
     * WAWebSendDeliveryReceiptJob the type attribute is set to
     * {@code DROP_ATTR} (omitted) for active delivery receipts.
     */
    DELIVERY(null),

    /**
     * Sender receipt — the message was received by our own companion
     * device, acknowledging that we (as sender) know it was delivered.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.SENDER
     */
    SENDER("sender"),

    /**
     * Peer message receipt — the message was a peer protocol message
     * (e.g. app state sync) from our own device.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.PEER_MSG
     */
    PEER("peer_msg"),

    /**
     * Inactive receipt — the message was processed but the recipient
     * chat is considered inactive (e.g. muted or archived).
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.INACTIVE
     */
    INACTIVE("inactive"),

    /**
     * Retry receipt — decryption failed and we are requesting the sender
     * to re-send the message, optionally with a new prekey bundle.
     *
     * @implNote WAWebSendRetryReceiptJob.sendRetryReceipt: builds a retry
     * receipt with registration ID, retry count, and optional key bundle.
     */
    RETRY("retry"),

    /**
     * Read receipt — the message has been read by the recipient.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.READ
     */
    READ("read"),

    /**
     * Read-self receipt — the message has been read on the user's own
     * device (for syncing read state across companion devices).
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.READ_SELF
     */
    READ_SELF("read-self"),

    /**
     * Played receipt — the voice message or view-once media has been
     * played/viewed by the recipient.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.PLAYED
     */
    PLAYED("played"),

    /**
     * Played-self receipt — the voice message or view-once media has
     * been played/viewed on the user's own device.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.PLAYED_SELF
     */
    PLAYED_SELF("played-self"),

    /**
     * History sync completion receipt — indicates that history sync
     * has been completed for a chat.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.HISTORY_SYNC_COMPLETION
     */
    HISTORY_SYNC_COMPLETION("hist_sync"),

    /**
     * Server error receipt — sent when a server-side error occurs
     * for the message.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE.SERVER_ERROR
     */
    SERVER_ERROR("server-error");

    /**
     * The protocol-level string value used in the {@code type} attribute
     * of receipt stanzas.
     *
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE: maps each type
     * name to its protocol string value.
     */
    private final String protocolValue;

    /**
     * Constructs a receipt type with the specified protocol value.
     *
     * @param protocolValue the protocol string value, or {@code null}
     *                      for the default delivery type
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE
     */
    MessageReceiptType(String protocolValue) {
        this.protocolValue = protocolValue;
    }

    /**
     * Returns the protocol-level value used in the {@code type} attribute
     * of the receipt stanza, or {@code null} for the default delivery type.
     *
     * @return the protocol value string, or {@code null}
     * @implNote WAWebSendReceiptJobCommon.RECEIPT_TYPE
     */
    public String protocolValue() {
        return protocolValue;
    }
}
