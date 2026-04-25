package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the possible outcomes of the end-to-end decryption pipeline
 * for an incoming WhatsApp message.
 *
 * <p>After a {@link MessageDecryptionHandler} iterates over every
 * encrypted payload in a stanza (SKMSG plus PKMSG/MSG), it condenses
 * the aggregated outcome into one of these values. The result drives
 * the subsequent decision tree: whether to send a delivery receipt,
 * a retry receipt asking the sender to re-encrypt, a NACK rejecting
 * the message, or silently drop it. The result also determines whether
 * the decoded message content should be stored locally and surfaced
 * to the user.
 *
 * @implNote WAWebHandleMsgTypes.flow.E2EProcessResult: the Mirrored
 * enum with values {@code SUCCESS}, {@code RETRY},
 * {@code HSM_MISMATCH}, {@code BACKFILL}, {@code PARSE_ERROR},
 * {@code PARSE_VALIDATION_ERROR}, {@code SIGNAL_OLD_COUNTER_ERROR}.
 * The {@link #BACKFILL} value additionally absorbs every value of
 * WAWebHandleMsgTypes.flow.PlaceholderType ({@code E2E},
 * {@code FANOUT}, {@code BOT_UNAVAILABLE_FANOUT},
 * {@code HOSTED_UNAVAILABLE_FANOUT},
 * {@code VIEW_ONCE_UNAVAILABLE_FANOUT}): Cobalt does not retain the
 * placeholder subtype because the downstream receipt logic is identical
 * for all four unavailable variants.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgTypes.flow")
public enum MessageDecryptionResult {
    /**
     * At least one encrypted payload was successfully decrypted and the
     * protobuf content was successfully processed.
     *
     * <p>Callers should send a delivery receipt to acknowledge the
     * message and persist the decoded content locally.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.SUCCESS}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    SUCCESS,

    /**
     * Decryption failed with a retryable Signal protocol error.
     *
     * <p>A retry receipt should be sent to request the sender to
     * re-encrypt and re-send the message. On the second retry the
     * receipt also carries a fresh prekey bundle so the sender can
     * rebuild the Signal session from scratch.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.RETRY}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    RETRY,

    /**
     * The stanza indicated a highly structured message (HSM) but the
     * decoded protobuf content did not contain one, or vice versa.
     *
     * <p>No receipt is sent for this error: the content is silently
     * dropped because HSM mismatches indicate a protocol-level
     * inconsistency that retrying would not resolve.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.HSM_MISMATCH}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    HSM_MISMATCH,

    /**
     * The message is an unavailable fanout placeholder for a companion
     * device.
     *
     * <p>An ack is sent but no content is stored. Unavailable messages
     * appear when a companion device requires backfill of history
     * but does not yet have the content.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.BACKFILL}.
     * This single outcome absorbs every unavailable-placeholder variant
     * from WAWebHandleMsgTypes.flow.PlaceholderType
     * ({@code FANOUT}, {@code BOT_UNAVAILABLE_FANOUT},
     * {@code HOSTED_UNAVAILABLE_FANOUT}, {@code VIEW_ONCE_UNAVAILABLE_FANOUT});
     * Cobalt does not persist the placeholder subtype because the downstream
     * action (ack + drop) is identical for all four.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "PlaceholderType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    BACKFILL,

    /**
     * The decrypted bytes could not be parsed as a valid
     * {@code MessageContainer} protobuf, or contained unrecognised
     * content.
     *
     * <p>A NACK with {@code ParsingError} is returned.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.PARSE_ERROR}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    PARSE_ERROR,

    /**
     * The decoded protobuf parsed correctly but failed structural
     * validation (e.g. stanza type vs protobuf content mismatch, an
     * invalid {@code DeviceSentMessage} envelope).
     *
     * <p>A NACK with {@code InvalidProtobuf} is returned, optionally
     * with an {@code e2eFailureReason} meta attribute for telemetry.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.PARSE_VALIDATION_ERROR}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    PARSE_VALIDATION_ERROR,

    /**
     * The Signal protocol reported a duplicate message counter (the
     * message was already decrypted and processed in a previous
     * session).
     *
     * <p>Handled specially: if the message qualifies for dedup the
     * cached delivery outcome is reused; otherwise it is treated like
     * a normal delivery.
     *
     * @implNote WAWebHandleMsgTypes.flow: {@code E2EProcessResult.SIGNAL_OLD_COUNTER_ERROR}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgTypes.flow", exports = "E2EProcessResult",
            adaptation = WhatsAppAdaptation.DIRECT)
    SIGNAL_OLD_COUNTER_ERROR
}
