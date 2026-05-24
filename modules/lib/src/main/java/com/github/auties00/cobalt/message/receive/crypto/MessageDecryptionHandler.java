package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveEncryptedPayload;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Stateful per-stanza handler that tracks the decryption outcome of every
 * {@code <enc>} child and produces the aggregated {@link MessageDecryptionResult} for
 * receipt selection.
 *
 * @apiNote
 * Constructed once per incoming message stanza by {@link com.github.auties00.cobalt.message.receive.ChatMessageReceiver};
 * each enc is offered first to {@link #canDecryptNext(MessageReceiveEncryptedPayload)}
 * and, on success, the receiver invokes the appropriate cipher and reports any
 * failure back via {@link #handleError(MessageReceiveEncryptedPayload, WhatsAppMessageException.Receive)}.
 *
 * @implNote
 * This implementation mirrors WhatsApp Web's
 * {@code WAWebMsgProcessingDecryptionHandler.createDecryptionHandler} closure but
 * lifts the state into instance fields so the handler can be reused in tests. A
 * stanza carries at most two {@code <enc>} children: a sender-key payload (SKMSG) and
 * a per-device Signal payload (PKMSG or MSG). Each slot tracks its own failure; the
 * non-SKMSG slot also short-circuits the SKMSG attempt when it fails with a
 * retryable error, matching the WA Web {@code SignalRetryable}-only blocker set.
 */
@WhatsAppWebModule(moduleName = "WAWebMsgProcessingDecryptionHandler")
public final class MessageDecryptionHandler {
    /**
     * Logger used for per-enc decryption-error diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageDecryptionHandler.class.getName());

    /**
     * Error types that block further decryption attempts once observed on the
     * non-SKMSG slot.
     *
     * @apiNote
     * Mirrors WhatsApp Web's local {@code b} set inside
     * {@code WAWebMsgProcessingDecryptionHandler}, which contains only
     * {@code SignalRetryable}; other error types still allow the SKMSG slot to be
     * attempted.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Set<DecryptionErrorType> RETRYABLE_BLOCKERS =
            EnumSet.of(DecryptionErrorType.SIGNAL_RETRYABLE);

    /**
     * Encryption types whose enc nodes have been offered so far.
     *
     * @apiNote
     * Tracked so {@link #getResult()} can promote a SKMSG failure to
     * {@link MessageDecryptionResult#SUCCESS} when the SKMSG slot was actually
     * attempted and only the PKMSG/MSG slot failed; mirrors WA Web's
     * {@code accessedEncs} set.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final Set<MessageEncryptionType> accessedEncs =
            EnumSet.noneOf(MessageEncryptionType.class);

    /**
     * Failure recorded for the per-device Signal slot (PKMSG or MSG), or {@code null}
     * when that slot was not attempted or succeeded.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    private EncFailure pkOrMsgFailure;

    /**
     * Failure recorded for the sender-key slot (SKMSG), or {@code null} when that
     * slot was not attempted or succeeded.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    private EncFailure skMsgFailure;

    /**
     * Returns whether the given encrypted payload should be attempted next and
     * records the attempt when it is.
     *
     * @apiNote
     * Called by the caller's enc-iteration loop before each cipher call; returning
     * {@code false} aborts the remaining payloads after a retryable per-device
     * Signal failure so the stanza's overall result is settled by the first
     * blocker.
     *
     * @implNote
     * This implementation also records the offered enc type in
     * {@link #accessedEncs} so {@link #getResult()} can distinguish "SKMSG attempted
     * and succeeded" from "SKMSG never tried" when the PKMSG/MSG slot also failed.
     *
     * @param enc the next encrypted payload to consider
     * @return {@code true} if decryption should be attempted; {@code false} when a
     *         prior retryable failure blocks further work
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean canDecryptNext(MessageReceiveEncryptedPayload enc) {
        if (pkOrMsgFailure != null && RETRYABLE_BLOCKERS.contains(pkOrMsgFailure.errorType)) {
            return false;
        }

        accessedEncs.add(enc.e2eType());
        return true;
    }

    /**
     * Records a decryption failure for the given encrypted payload.
     *
     * @apiNote
     * Called by {@link com.github.auties00.cobalt.message.receive.ChatMessageReceiver}
     * inside the per-enc {@code try/catch}; the resulting classification drives both
     * the SKMSG short-circuit and the final receipt selection.
     *
     * @implNote
     * This implementation routes SKMSG failures into {@link #skMsgFailure} and
     * non-SKMSG failures into {@link #pkOrMsgFailure}; if both fail the SKMSG slot
     * is preferred as the dominant failure in {@link #getResult()}.
     *
     * @param enc   the encrypted payload that failed
     * @param error the decryption exception
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void handleError(
            MessageReceiveEncryptedPayload enc,
            WhatsAppMessageException.Receive error
    ) {
        var errorType = classifyError(error);
        var failure = new EncFailure(enc, error, errorType);

        if (enc.e2eType().isSenderKeyMessage()) {
            skMsgFailure = failure;
        } else {
            pkOrMsgFailure = failure;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Decryption error for {0}: {1} ({2})",
                enc.e2eType(), errorType, error.getMessage());
    }

    /**
     * Returns the aggregated decryption result computed from the recorded failures.
     *
     * @apiNote
     * Called once at the end of the enc-iteration loop; the returned
     * {@link MessageDecryptionResult} drives the follow-up receipt selection
     * (delivery, retry, NACK, plain ack) inside the orchestrator.
     *
     * @implNote
     * This implementation mirrors WhatsApp Web's local {@code E} function: if no
     * failure was recorded the result is {@link MessageDecryptionResult#SUCCESS}; a
     * SKMSG-only failure on a stanza that also carried an attempted SKMSG slot also
     * resolves to SUCCESS (the WA Web {@code l} short-circuit); otherwise the
     * dominant failure (SKMSG preferred over PKMSG/MSG) is mapped through
     * {@link #mapErrorToResult(EncFailure)}.
     *
     * @return the aggregated decryption result
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    public MessageDecryptionResult getResult() {
        var dominant = skMsgFailure != null ? skMsgFailure : pkOrMsgFailure;

        if (dominant == null) {
            return MessageDecryptionResult.SUCCESS;
        }

        var skMsgAccessed = accessedEncs.contains(MessageEncryptionType.SKMSG);
        if (skMsgAccessed && skMsgFailure == null) {
            return MessageDecryptionResult.SUCCESS;
        }

        return mapErrorToResult(dominant);
    }

    /**
     * Returns the failed encrypted payload that the receipt path should reference
     * when surfacing the failure to the server.
     *
     * @apiNote
     * Used by the duplicate-message dedup branch of WhatsApp Web's
     * {@code WAWebHandleMsg} dispatcher to attach the original failing enc to the
     * cached duplicate-receipt info; returns {@link Optional#empty()} when no
     * failure was recorded.
     *
     * @return an {@link Optional} wrapping the failed enc payload
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<MessageReceiveEncryptedPayload> failedEnc() {
        var dominant = skMsgFailure != null ? skMsgFailure : pkOrMsgFailure;
        return dominant != null ? Optional.of(dominant.enc) : Optional.empty();
    }

    /**
     * Returns the dominant failure exception so the caller can rethrow it when no
     * enc could be decrypted.
     *
     * @apiNote
     * Consumed by {@link com.github.auties00.cobalt.message.receive.ChatMessageReceiver}
     * after the enc-iteration loop completes without a successful payload, so the
     * original Signal exception (rather than a synthesised one) bubbles up.
     *
     * @return an {@link Optional} wrapping the dominant exception
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<WhatsAppMessageException.Receive> failedError() {
        var dominant = skMsgFailure != null ? skMsgFailure : pkOrMsgFailure;
        return dominant != null ? Optional.of(dominant.error) : Optional.empty();
    }

    /**
     * Classifies a decryption exception into a {@link DecryptionErrorType} for
     * downstream slot tracking and result mapping.
     *
     * @apiNote
     * Mirrors WhatsApp Web's local {@code S} classifier inside
     * {@code WAWebMsgProcessingDecryptionHandler}, which dispatches on the Signal
     * and validation-error class hierarchy; Cobalt switches on the sealed
     * {@link WhatsAppMessageException.Receive}
     * hierarchy instead.
     *
     * @implNote
     * This implementation collapses every Signal-protocol error subtype (NoSession,
     * BadMac, InvalidKey, etc.) into {@link DecryptionErrorType#SIGNAL_RETRYABLE}
     * because WhatsApp Web's classifier also folds them all into its
     * {@code SignalRetryable} bucket via the shared {@code SignalDecryptionError}
     * class.
     *
     * @param error the exception to classify
     * @return the corresponding error type
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static DecryptionErrorType classifyError(WhatsAppMessageException.Receive error) {
        return switch (error) {
            case WhatsAppMessageException.Receive.UnknownDevice _ ->
                    DecryptionErrorType.UNKNOWN_DEVICE;
            case WhatsAppMessageException.Receive.DuplicateMessage _ ->
                    DecryptionErrorType.SIGNAL_DUPLICATE_MESSAGE;
            case WhatsAppMessageException.Receive.InvalidDeviceSentMessage _ ->
                    DecryptionErrorType.DEVICE_SENT_MESSAGE;
            case WhatsAppMessageException.Receive.InvalidProtobuf _ ->
                    DecryptionErrorType.INVALID_PROTOBUF;
            case WhatsAppMessageException.Receive.HsmMismatch _ ->
                    DecryptionErrorType.HSM_MISMATCH;
            case WhatsAppMessageException.Receive.BroadcastEphemeralSettings _ ->
                    DecryptionErrorType.BROADCAST_EPH_SETTINGS;
            case WhatsAppMessageException.Receive.NoSession _,
                 WhatsAppMessageException.Receive.InvalidKey _,
                 WhatsAppMessageException.Receive.InvalidKeyId _,
                 WhatsAppMessageException.Receive.InvalidOneTimeKey _,
                 WhatsAppMessageException.Receive.InvalidSignedPreKey _,
                 WhatsAppMessageException.Receive.InvalidMessage _,
                 WhatsAppMessageException.Receive.InvalidSignature _,
                 WhatsAppMessageException.Receive.FutureMessage _,
                 WhatsAppMessageException.Receive.BadMac _,
                 WhatsAppMessageException.Receive.NoSenderKey _,
                 WhatsAppMessageException.Receive.InvalidSenderKey _,
                 WhatsAppMessageException.Receive.AdvFailure _ ->
                    DecryptionErrorType.SIGNAL_RETRYABLE;
            default -> DecryptionErrorType.UNKNOWN;
        };
    }

    /**
     * Maps the dominant failure's error type onto the corresponding
     * {@link MessageDecryptionResult} that drives receipt selection.
     *
     * @apiNote
     * Mirrors WhatsApp Web's local {@code k}/{@code E} switch inside
     * {@code WAWebMsgProcessingDecryptionHandler} that materialises the
     * {@code E2EProcessResult} from the classified error type.
     *
     * @implNote
     * This implementation does not surface WA Web's {@code DEFERRED} variant
     * (used for orphan bot messages); the omission is intentional because Cobalt
     * does not model the orphan-bot-message buffer.
     *
     * @param failure the dominant failure to map
     * @return the corresponding decryption result
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MessageDecryptionResult mapErrorToResult(EncFailure failure) {
        return switch (failure.errorType) {
            case SIGNAL_RETRYABLE, UNKNOWN_DEVICE, BROADCAST_EPH_SETTINGS ->
                    MessageDecryptionResult.RETRY;
            case SIGNAL_DUPLICATE_MESSAGE ->
                    MessageDecryptionResult.SIGNAL_OLD_COUNTER_ERROR;
            case DEVICE_SENT_MESSAGE, INVALID_PROTOBUF ->
                    MessageDecryptionResult.PARSE_VALIDATION_ERROR;
            case HSM_MISMATCH ->
                    MessageDecryptionResult.HSM_MISMATCH;
            case UNKNOWN ->
                    MessageDecryptionResult.PARSE_ERROR;
        };
    }

    /**
     * Internal classification of decryption failures used to drive the
     * SKMSG-blocker short-circuit and the {@link MessageDecryptionResult} mapping.
     *
     * @apiNote
     * Not exposed outside the package; {@link MessageDecryptionHandler#classifyError}
     * is the only producer and {@link MessageDecryptionHandler#mapErrorToResult}
     * the only consumer.
     */
    @WhatsAppWebModule(moduleName = "WAWebMsgProcessingDecryptionHandler")
    private enum DecryptionErrorType {
        /**
         * Catch-all for Signal-protocol errors that the sender can resolve by
         * re-encrypting and re-sending the payload.
         *
         * @apiNote
         * Covers NoSession, InvalidKey, InvalidMessage, BadMac, missing sender
         * key, and similar; resolves to {@link MessageDecryptionResult#RETRY}.
         */
        SIGNAL_RETRYABLE,

        /**
         * The Signal protocol reported a duplicate or already-seen counter.
         *
         * @apiNote
         * Resolves to {@link MessageDecryptionResult#SIGNAL_OLD_COUNTER_ERROR};
         * the orchestrator either reuses the cached delivery outcome or treats
         * the message as a normal delivery based on the dedup gate.
         */
        SIGNAL_DUPLICATE_MESSAGE,

        /**
         * The message came from a device not present in the local device list.
         *
         * @apiNote
         * Resolves to {@link MessageDecryptionResult#RETRY}; WhatsApp Web also
         * triggers a device-list sync, which Cobalt drives separately from the
         * sync handlers.
         */
        UNKNOWN_DEVICE,

        /**
         * The {@code DeviceSentMessage} envelope was missing, invalid, or
         * present when it should not be.
         *
         * @apiNote
         * Resolves to {@link MessageDecryptionResult#PARSE_VALIDATION_ERROR}.
         */
        DEVICE_SENT_MESSAGE,

        /**
         * The decrypted protobuf failed structural validation
         * (multiple message keys, type mismatch, etc.).
         *
         * @apiNote
         * Resolves to {@link MessageDecryptionResult#PARSE_VALIDATION_ERROR}.
         */
        INVALID_PROTOBUF,

        /**
         * The stanza marked the message as HSM but the protobuf did not match,
         * or vice versa.
         *
         * @apiNote
         * Resolves to {@link MessageDecryptionResult#HSM_MISMATCH}.
         */
        HSM_MISMATCH,

        /**
         * Failed to decode broadcast ephemeral settings from the shared secret.
         *
         * @apiNote
         * Resolves to {@link MessageDecryptionResult#RETRY}; WhatsApp Web
         * additionally tags the retry receipt with the
         * {@code INVALID_BROADCAST_STANZA_ATTRIBUTE} failure reason, which
         * Cobalt does not currently surface.
         */
        BROADCAST_EPH_SETTINGS,

        /**
         * Unclassified error that does not match any known category.
         *
         * @apiNote
         * Resolves to {@link MessageDecryptionResult#PARSE_ERROR}.
         */
        UNKNOWN
    }

    /**
     * Encapsulates a single slot's failure: the payload, the exception, and the
     * classified error type.
     *
     * @apiNote
     * Stored in {@link #pkOrMsgFailure} or {@link #skMsgFailure} so
     * {@link #getResult()} can both pick the dominant failure and surface the
     * original exception via {@link #failedError()}.
     */
    @WhatsAppWebModule(moduleName = "WAWebMsgProcessingDecryptionHandler")
    private static final class EncFailure {
        /**
         * The encrypted payload whose decryption failed.
         */
        @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final MessageReceiveEncryptedPayload enc;

        /**
         * The exception that caused the failure.
         */
        @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final WhatsAppMessageException.Receive error;

        /**
         * The classified error type produced by
         * {@link MessageDecryptionHandler#classifyError}.
         */
        @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final DecryptionErrorType errorType;

        /**
         * Constructs a failure record from its three components.
         *
         * @apiNote
         * Used only by {@link MessageDecryptionHandler#handleError}.
         *
         * @param enc       the encrypted payload that failed
         * @param error     the exception that caused the failure
         * @param errorType the classified error type
         */
        @WhatsAppWebExport(moduleName = "WAWebMsgProcessingDecryptionHandler", exports = "createDecryptionHandler",
                adaptation = WhatsAppAdaptation.DIRECT)
        private EncFailure(
                MessageReceiveEncryptedPayload enc,
                WhatsAppMessageException.Receive error,
                DecryptionErrorType errorType
        ) {
            this.enc = enc;
            this.error = error;
            this.errorType = errorType;
        }
    }
}
