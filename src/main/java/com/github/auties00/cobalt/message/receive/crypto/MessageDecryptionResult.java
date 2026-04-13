package com.github.auties00.cobalt.message.receive.crypto;

/**
 * Outcome of the E2E decryption pipeline for an incoming message.
 *
 * <p>After iterating over all encrypted payloads, the decryption handler
 * produces one of these results.  The result determines what receipt is
 * sent back to the sender and whether the message is stored locally.
 *
 * @implNote WAWebHandleMsgTypes.flow.E2EProcessResult: the Mirrored
 * enum {@code c} with values {@code ["SUCCESS", "RETRY", "HSM_MISMATCH",
 * "BACKFILL", "PARSE_ERROR", "PARSE_VALIDATION_ERROR",
 * "SIGNAL_OLD_COUNTER_ERROR"]}.
 */
public enum MessageDecryptionResult {
    /**
     * At least one encrypted payload was successfully decrypted and
     * the protobuf content was processed.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.SUCCESS}.
     */
    SUCCESS,

    /**
     * Decryption failed with a retryable Signal error — a retry receipt
     * should be sent so the sender re-encrypts and resends.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.RETRY}.
     */
    RETRY,

    /**
     * HSM template mismatch — the stanza indicated HSM but the protobuf
     * did not contain a highly structured message, or vice versa.
     * No receipt is sent.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.HSM_MISMATCH}.
     */
    HSM_MISMATCH,

    /**
     * The message payload was not available (fanout placeholder from a
     * companion device).  An ack is sent but no content is stored.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.BACKFILL}.
     */
    BACKFILL,

    /**
     * The decrypted protobuf could not be parsed or contained
     * unrecognised content.  A NACK with ParsingError is sent.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.PARSE_ERROR}.
     */
    PARSE_ERROR,

    /**
     * The decrypted protobuf failed structural validation (e.g.
     * stanza type vs protobuf content mismatch, invalid DSM).
     * A NACK with InvalidProtobuf is sent, optionally with an
     * {@code e2eFailureReason}.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.PARSE_VALIDATION_ERROR}.
     */
    PARSE_VALIDATION_ERROR,

    /**
     * The Signal protocol reported a duplicate message counter (old
     * counter error).  The message was already decrypted previously.
     * Handled specially: if dedup-eligible, cached for receipt; otherwise
     * treated like a normal delivery.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.SIGNAL_OLD_COUNTER_ERROR}.
     */
    SIGNAL_OLD_COUNTER_ERROR
}
