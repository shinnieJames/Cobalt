package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.message.receipt.MessageReceiptHandler;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the aggregated outcomes of the end-to-end decryption pipeline for an
 * incoming message stanza.
 *
 * @apiNote
 * Returned by {@link MessageDecryptionHandler#getResult()} after iteration over every
 * {@code <enc>} child finishes; the value drives the follow-up branching in
 * {@link MessageReceiptHandler}
 * (delivery receipt, retry, NACK, plain ack) and decides whether the decoded
 * {@link com.github.auties00.cobalt.model.chat.ChatMessageInfo} is persisted.
 *
 * @implNote
 * This implementation mirrors WhatsApp Web's {@code E2EProcessResult} mirrored enum
 * from {@code WAWebHandleMsgTypes.flow}; the WA enum also defines a {@code DEFERRED}
 * variant used for orphan bot messages, which Cobalt does not yet implement.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgTypes.flow")
public enum MessageDecryptionResult {
    /**
     * At least one encrypted payload was decrypted and the protobuf content passed
     * post-decryption validation.
     *
     * @apiNote
     * Triggers a delivery receipt via
     * {@code WAWebSendDeliveryReceiptJob.sendDeliveryReceiptsAfterDecryption} and
     * persists the decoded {@link com.github.auties00.cobalt.model.chat.ChatMessageInfo}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    SUCCESS,

    /**
     * Decryption failed with a retryable Signal-protocol error and the sender must
     * re-encrypt the payload.
     *
     * @apiNote
     * Triggers a retry receipt; WhatsApp Web's
     * {@code WAWebSendRetryReceiptJob.sendRetryReceipt} attaches a fresh prekey bundle
     * from the second attempt onward so the sender can re-establish the Signal
     * session.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    RETRY,

    /**
     * The stanza marked the message as highly-structured but the decoded protobuf did
     * not carry an {@link com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage},
     * or vice versa.
     *
     * @apiNote
     * Triggers a silent drop via the {@code HSM_MISMATCH} branch of
     * {@code WAWebHandleMsgSendReceipt.sendReceipt}: no receipt is sent because the
     * mismatch is a protocol-level inconsistency that retrying cannot resolve.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    HSM_MISMATCH,

    /**
     * The stanza is an unavailable fanout placeholder produced when a companion
     * device needs history backfill but has not yet received the content.
     *
     * @apiNote
     * Triggers a plain {@code <ack>} via {@code WAWebHandleMsgSendAck.sendAck} and the
     * placeholder is recorded so the on-demand history sync can replay the original
     * message later.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "PlaceholderType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    BACKFILL,

    /**
     * The decrypted bytes could not be parsed as a
     * {@link com.github.auties00.cobalt.model.message.MessageContainer} protobuf or
     * carried an unrecognised content tag.
     *
     * @apiNote
     * Triggers a NACK with the WhatsApp Web {@code NackReason.ParsingError} code via
     * {@code WAWebHandleMsgSendAck.sendNack}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    PARSE_ERROR,

    /**
     * The protobuf parsed but failed post-decode structural validation, for example
     * an invalid {@code DeviceSentMessage} envelope or a stanza/protobuf type
     * mismatch.
     *
     * @apiNote
     * Triggers a NACK with the {@code NackReason.InvalidProtobuf} code (491); the
     * NACK may additionally carry a {@code <meta failure_reason=...>} child surfaced
     * from the underlying validation error for server-side telemetry.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    PARSE_VALIDATION_ERROR,

    /**
     * The Signal protocol reported a duplicate or already-seen message counter for
     * one of the encrypted payloads.
     *
     * @apiNote
     * If the message qualifies for dedup the cached delivery outcome is reused via the
     * {@code WAWebGetMessageCache} duplicate-message receipt path; otherwise the
     * message proceeds as a normal delivery.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    SIGNAL_OLD_COUNTER_ERROR
}
