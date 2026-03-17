package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * A notification that conveys the result of a media re-upload request.
 *
 * <p>When a client requests a media re-upload (for example, because a
 * previously downloaded media file is no longer available on the CDN), the
 * server processes the request and responds with this notification. The
 * notification identifies the original message via its {@code stanzaId},
 * provides the updated {@code directPath} on success, and includes a
 * {@code result} code indicating the outcome.
 *
 * <p>The {@code messageSecret} field carries the AES-GCM decryption key
 * for the notification payload. The client derives the final key from this
 * secret using HKDF with the info string {@code "WhatsApp Media Retry
 * Notification"}.
 *
 * <p>This message is defined in {@code WAWebProtobufsMmsRetry.pb}.
 */
@ProtobufMessage(name = "MediaRetryNotification")
public final class MediaRetryNotification {
    /**
     * The stanza identifier of the original message whose media was
     * requested for re-upload.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String stanzaId;

    /**
     * The updated CDN direct path to the re-uploaded media file, populated
     * when the result is {@link ResultType#SUCCESS}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String directPath;

    /**
     * The outcome of the media re-upload request.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    ResultType result;

    /**
     * The secret used as input to HKDF key derivation for AES-GCM
     * decryption of this notification's encrypted payload.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] messageSecret;

    /**
     * Constructs a new {@code MediaRetryNotification} with the given field values.
     *
     * @param stanzaId      the stanza identifier of the original message
     * @param directPath    the updated CDN direct path on success
     * @param result        the outcome of the re-upload request
     * @param messageSecret the HKDF input secret for payload decryption
     */
    MediaRetryNotification(String stanzaId, String directPath, ResultType result, byte[] messageSecret) {
        this.stanzaId = stanzaId;
        this.directPath = directPath;
        this.result = result;
        this.messageSecret = messageSecret;
    }

    /**
     * Returns the stanza identifier of the original message whose media was
     * requested for re-upload.
     *
     * @return an {@link Optional} containing the stanza identifier, or empty if not set
     */
    public Optional<String> stanzaId() {
        return Optional.ofNullable(stanzaId);
    }

    /**
     * Returns the updated CDN direct path to the re-uploaded media file.
     *
     * @return an {@link Optional} containing the direct path, or empty if not set
     */
    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
    }

    /**
     * Returns the outcome of the media re-upload request.
     *
     * @return an {@link Optional} containing the result type, or empty if not set
     */
    public Optional<ResultType> result() {
        return Optional.ofNullable(result);
    }

    /**
     * Returns the secret used for HKDF key derivation to decrypt this
     * notification's payload.
     *
     * @return an {@link Optional} containing the message secret, or empty if not set
     */
    public Optional<byte[]> messageSecret() {
        return Optional.ofNullable(messageSecret);
    }

    /**
     * Sets the stanza identifier of the original message.
     *
     * @param stanzaId the stanza identifier
     */
    public void setStanzaId(String stanzaId) {
        this.stanzaId = stanzaId;
    }

    /**
     * Sets the updated CDN direct path to the re-uploaded media file.
     *
     * @param directPath the direct path
     */
    public void setDirectPath(String directPath) {
        this.directPath = directPath;
    }

    /**
     * Sets the outcome of the media re-upload request.
     *
     * @param result the result type
     */
    public void setResult(ResultType result) {
        this.result = result;
    }

    /**
     * Sets the secret for HKDF key derivation.
     *
     * @param messageSecret the message secret
     */
    public void setMessageSecret(byte[] messageSecret) {
        this.messageSecret = messageSecret;
    }

    /**
     * The outcome of a media re-upload request, indicating whether the
     * server was able to successfully re-upload the requested media.
     *
     * <p>On the client side these result codes are mapped to HTTP-like status
     * codes for internal processing: {@link #SUCCESS} corresponds to 200,
     * {@link #NOT_FOUND} and {@link #DECRYPTION_ERROR} correspond to 404,
     * and {@link #GENERAL_ERROR} corresponds to 500.
     */
    @ProtobufEnum(name = "MediaRetryNotification.ResultType")
    public enum ResultType {
        /**
         * The re-upload request failed due to an unspecified server-side error.
         * This has the numeric value of {@code 0}.
         */
        GENERAL_ERROR(0),

        /**
         * The re-upload request completed successfully and the media is
         * available at the updated direct path.
         * This has the numeric value of {@code 1}.
         */
        SUCCESS(1),

        /**
         * The requested media could not be found on the server.
         * This has the numeric value of {@code 2}.
         */
        NOT_FOUND(2),

        /**
         * The server encountered a decryption error while processing the
         * re-upload request.
         * This has the numeric value of {@code 3}.
         */
        DECRYPTION_ERROR(3);

        /**
         * Constructs a new {@code ResultType} with the given protobuf index.
         *
         * @param index the protobuf enum index
         */
        ResultType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf enum index of this result type.
         */
        final int index;

        /**
         * Returns the protobuf enum index of this result type.
         *
         * @return the numeric index
         */
        public int index() {
            return this.index;
        }
    }
}
