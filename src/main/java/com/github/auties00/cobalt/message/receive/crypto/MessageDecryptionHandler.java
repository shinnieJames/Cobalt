package com.github.auties00.cobalt.message.receive.crypto;

import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.message.receive.stanza.MessageReceiveEncryptedPayload;
import com.github.auties00.cobalt.message.MessageEncryptionType;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tracks decryption outcomes across multiple encrypted payloads in an
 * incoming message, producing a composite {@link MessageDecryptionResult}.
 *
 * <p>An incoming message stanza may contain up to two {@code <enc>} nodes:
 * a sender-key message (SKMSG) for group encryption and a per-device
 * message (PKMSG or MSG) for session encryption.  The handler tracks
 * failures for each slot independently and applies WA Web's resolution
 * rules to determine the overall result.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>If a non-SKMSG payload fails with a retryable Signal error, no
 *       further payloads are attempted — the failure is final.</li>
 *   <li>If the SKMSG fails but the non-SKMSG succeeds (or was not
 *       present), the overall result is {@link MessageDecryptionResult#SUCCESS}.</li>
 *   <li>Error types are classified from the exception hierarchy and
 *       mapped to the appropriate result code for receipt generation.</li>
 * </ul>
 *
 * @implNote WAWebMsgProcessingDecryptionHandler.createDecryptionHandler:
 * creates a per-message handler via function {@code S(e)} that tracks
 * {@code pkOrMsgFailedEnc} and {@code skMsgFailedEnc}, and returns
 * closures {@code canDecryptNext}, {@code handleError}, and
 * {@code getResult}.
 */
public final class MessageDecryptionHandler {
    /**
     * Logger for decryption handler diagnostics.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.I: logs decryption
     * errors per enc payload via WALogger.WARN.
     */
    private static final System.Logger LOGGER = System.getLogger(MessageDecryptionHandler.class.getName());

    /**
     * Error types that block further decryption attempts when they
     * occur on a non-SKMSG payload.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler: the constant
     * {@code C = new Set([y.SignalRetryable])} — only SignalRetryable
     * blocks further attempts via {@code canDecryptNext}.
     */
    private static final Set<DecryptionErrorType> RETRYABLE_BLOCKERS =
            EnumSet.of(DecryptionErrorType.SIGNAL_RETRYABLE);

    /**
     * The set of enc types that have been accessed (attempted) so far.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.S: the handler
     * state field {@code t.accessedEncs}, a {@code Set} that records
     * each enc's {@code e2eType} when {@code canDecryptNext} allows it.
     */
    private final Set<MessageEncryptionType> accessedEncs =
            EnumSet.noneOf(MessageEncryptionType.class);

    /**
     * The failure from the non-SKMSG (PKMSG or MSG) payload, if any.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.S: the handler
     * state field {@code t.pkOrMsgFailedEnc}.
     */
    private EncFailure pkOrMsgFailure;

    /**
     * The failure from the SKMSG payload, if any.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.S: the handler
     * state field {@code t.skMsgFailedEnc}.
     */
    private EncFailure skMsgFailure;

    /**
     * Determines whether the next encrypted payload should be attempted.
     *
     * <p>If a non-SKMSG payload already failed with a retryable Signal
     * error, no further payloads are attempted.  Otherwise the payload
     * is allowed.
     *
     * @param enc the next encrypted payload to consider
     * @return {@code true} if decryption should be attempted
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.canDecryptNext:
     * checks if {@code pkOrMsgFailedEnc?.errorType} is in the retryable
     * blocker set {@code C}, then adds the enc's {@code e2eType} to
     * {@code accessedEncs}.
     */
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
     * <p>The exception is classified into a {@link DecryptionErrorType}
     * and stored in the appropriate slot (SKMSG or PKMSG/MSG).
     *
     * @param enc   the encrypted payload that failed
     * @param error the decryption exception
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.handleError:
     * classifies the error via {@code v()}, stores in the appropriate
     * slot ({@code skMsgFailedEnc} or {@code pkOrMsgFailedEnc}),
     * then calls {@code I()} for metric/logging reporting.
     */
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
     * Computes the composite decryption result from all tracked failures.
     *
     * <p>Resolution rules (from WAWebMsgProcessingDecryptionHandler.getResult):
     * <ol>
     *   <li>If no failures occurred, return SUCCESS.</li>
     *   <li>Pick the "dominant" failure: prefer skMsgFailure if it exists,
     *       else use pkOrMsgFailure.</li>
     *   <li>If the SKMSG was accessed and did NOT fail, but the other
     *       slot did fail, the overall result is still SUCCESS (the SKMSG
     *       decryption succeeded).</li>
     *   <li>Map the dominant failure's error type to a result code.</li>
     * </ol>
     *
     * @return the composite decryption result
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.getResult: calls
     * {@code L(e, t, n)} which delegates to async function {@code E()}.
     * Computes {@code dominant = skMsgFailedEnc ?? pkOrMsgFailedEnc},
     * returns SUCCESS if no failure or if SKMSG was accessed and
     * succeeded, otherwise maps the error type to a result.
     */
    public MessageDecryptionResult getResult() {
        var dominant = skMsgFailure != null ? skMsgFailure : pkOrMsgFailure;

        if (dominant == null) {
            return MessageDecryptionResult.SUCCESS;
        }

        // If SKMSG was accessed and succeeded (no skMsgFailure), but
        // the other enc failed, the overall result is success
        var skMsgAccessed = accessedEncs.contains(MessageEncryptionType.SKMSG);
        if (skMsgAccessed && skMsgFailure == null) {
            return MessageDecryptionResult.SUCCESS;
        }

        return mapErrorToResult(dominant);
    }

    /**
     * Returns the failed encrypted payload, if any, for use in
     * duplicate message dedup handling.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.getResult: the
     * result object includes {@code failedEnc: l} for
     * SIGNAL_OLD_COUNTER_ERROR; this accessor exposes the dominant
     * failure's enc for the caller to use.
     *
     * @return the failed enc payload, or empty if no failure occurred
     */
    public Optional<MessageReceiveEncryptedPayload> failedEnc() {
        var dominant = skMsgFailure != null ? skMsgFailure : pkOrMsgFailure;
        return dominant != null ? Optional.of(dominant.enc) : Optional.empty();
    }

    /**
     * Returns the exception from the dominant failure, if any.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.getResult: the
     * result object includes {@code retryReason} and
     * {@code e2eFailureReason} derived from the error; this accessor
     * exposes the raw exception so the caller can extract those fields.
     *
     * @return the decryption exception, or empty if no failure
     */
    public Optional<WhatsAppMessageException.Receive> failedError() {
        var dominant = skMsgFailure != null ? skMsgFailure : pkOrMsgFailure;
        return dominant != null ? Optional.of(dominant.error) : Optional.empty();
    }

    /**
     * Classifies a decryption exception into a {@link DecryptionErrorType}.
     *
     * @param error the exception to classify
     * @return the classified error type
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.v: maps exception
     * types to DecryptionErrorType values via instanceof checks.
     * Sub-classification of {@code SignalDecryptionError} is handled
     * by function {@code b()}, which checks
     * {@code e.message === "errDuplicateMsg"} to distinguish
     * {@code SignalDuplicateMessage} from {@code SignalRetryable}.
     * In Cobalt, the exception hierarchy already separates these at
     * the source ({@code DuplicateMessage} vs other Signal errors).
     */
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
     * Maps a failure's error type to a {@link MessageDecryptionResult}.
     *
     * @param failure the failure to map
     * @return the corresponding result code
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.getResult: maps
     * each error type to an {@code E2EProcessResult} value in the
     * ternary chain inside async function {@code E()}.
     */
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
     * Classification of decryption errors into categories that
     * determine the overall message processing result and receipt type.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler: the Mirrored enum
     * {@code y} with values {@code ["SignalRetryable",
     * "SignalDuplicateMessage", "UnknownDevice", "DeviceSentMessage",
     * "InvalidProtobuf", "HsmMismatch", "BroadcastEphSettings",
     * "Unknown"]}.
     */
    private enum DecryptionErrorType {
        /**
         * A retryable Signal protocol error (no session, invalid key,
         * invalid message, bad MAC, missing sender key, etc.).
         * Triggers a retry receipt.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.SignalRetryable}.
         */
        SIGNAL_RETRYABLE,

        /**
         * The message counter was already seen (duplicate/old counter).
         * Handled specially for dedup; may still produce a delivery receipt.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.SignalDuplicateMessage}.
         */
        SIGNAL_DUPLICATE_MESSAGE,

        /**
         * The message came from a companion device not in the local
         * device list.  Triggers device list sync and retry.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.UnknownDevice}.
         */
        UNKNOWN_DEVICE,

        /**
         * The DeviceSentMessage wrapper was missing, invalid, or present
         * when it should not be.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.DeviceSentMessage}.
         */
        DEVICE_SENT_MESSAGE,

        /**
         * The decrypted protobuf failed structural validation
         * (multiple message keys, type mismatch, etc.).
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.InvalidProtobuf}.
         */
        INVALID_PROTOBUF,

        /**
         * The stanza indicated HSM but the protobuf content did not
         * match, or vice versa.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.HsmMismatch}.
         */
        HSM_MISMATCH,

        /**
         * Failed to decode broadcast ephemeral settings from the
         * shared secret.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.BroadcastEphSettings}.
         */
        BROADCAST_EPH_SETTINGS,

        /**
         * An unclassified error that does not match any known category.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: {@code y.Unknown}.
         */
        UNKNOWN
    }

    /**
     * Tracks a single encrypted payload's decryption failure, combining
     * the payload, exception, and classified error type.
     *
     * @implNote WAWebMsgProcessingDecryptionHandler.handleError: stores
     * failures as {@code {enc, error, errorType}} objects in the handler
     * state fields {@code pkOrMsgFailedEnc} and {@code skMsgFailedEnc}.
     */
    private static final class EncFailure {
        /**
         * The encrypted payload that failed decryption.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: the {@code enc}
         * field of the failure object.
         */
        private final MessageReceiveEncryptedPayload enc;

        /**
         * The exception that caused the failure.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: the {@code error}
         * field of the failure object.
         */
        private final WhatsAppMessageException.Receive error;

        /**
         * The classified error type.
         *
         * @implNote WAWebMsgProcessingDecryptionHandler: the {@code errorType}
         * field of the failure object, produced by function {@code v()}.
         */
        private final DecryptionErrorType errorType;

        /**
         * Constructs a new failure record for a single encrypted payload.
         *
         * @param enc       the encrypted payload that failed
         * @param error     the exception that caused the failure
         * @param errorType the classified error type
         *
         * @implNote WAWebMsgProcessingDecryptionHandler.handleError:
         * constructs the {@code {enc, error, errorType}} object inline.
         */
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
