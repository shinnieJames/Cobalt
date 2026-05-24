package com.github.auties00.cobalt.wam.privatestats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Objects;

/**
 * The categorised outcome of one private-stats buffer upload attempt.
 *
 * @apiNote
 * Returned by {@link WamPrivateStatsUploader#upload(byte[])} so the
 * caller can emit a {@code PsBufferUploadWamEvent} mirroring WA Web's
 * {@link WhatsAppWebModule WAWebUploadPrivateStatsBackend} flow.
 *
 * @param result           the categorised result code; see {@link Type}
 * @param httpResponseCode the HTTP status returned by the endpoint,
 *                         or {@code -1} when the request never
 *                         reached the server (token issuance failure
 *                         or transport exception)
 */
@WhatsAppWebModule(moduleName = "WAWebUploadPrivateStatsBackend")
@WhatsAppWebModule(moduleName = "WAWebWamEnumPsBufferUploadResult")
public record WamPrivateStatsUploadResult(Type result, int httpResponseCode) {
    /**
     * Validates the {@code result} component for {@code null}.
     *
     * @throws NullPointerException if {@code result} is {@code null}
     */
    public WamPrivateStatsUploadResult {
        Objects.requireNonNull(result, "result must not be null");
    }

    /**
     * The categorised upload outcomes.
     *
     * @apiNote
     * Mirrors the {@code PS_BUFFER_UPLOAD_RESULT} constants of
     * {@link WhatsAppWebModule WAWebWamEnumPsBufferUploadResult}, which
     * {@link WhatsAppWebModule WAWebUploadPrivateStatsBackend} reads
     * when populating {@code PsBufferUploadWamEvent.psBufferUploadResult}.
     */
    public enum Type {
        /**
         * The server returned HTTP {@code 200}.
         *
         * @apiNote
         * Mirrors {@code PS_BUFFER_UPLOAD_RESULT.SUCCESS}.
         */
        SUCCESS,
        /**
         * The server returned a transient server-side error
         * (HTTP {@code 429} or {@code 500}).
         *
         * @apiNote
         * Mirrors {@code PS_BUFFER_UPLOAD_RESULT.ERROR_SERVER_OTHER};
         * WA Web treats these as retry-after-backoff outcomes.
         */
        ERROR_SERVER_OTHER,
        /**
         * The server returned HTTP {@code 400} indicating the
         * multipart body could not be parsed.
         *
         * @apiNote
         * Mirrors {@code PS_BUFFER_UPLOAD_RESULT.ERROR_PARSING}.
         */
        ERROR_PARSING,
        /**
         * The server returned HTTP {@code 400} indicating the
         * carried WAM buffer could not be decoded.
         *
         * @apiNote
         * Mirrors {@code PS_BUFFER_UPLOAD_RESULT.ERROR_DECODING}.
         * Cobalt does not currently distinguish between
         * parse-failure and decode-failure 400 responses on the wire;
         * both map to {@link #ERROR_PARSING} in the default mapping.
         */
        ERROR_DECODING,
        /**
         * Token issuance failed before the request was assembled.
         *
         * @apiNote
         * Mirrors {@code PS_BUFFER_UPLOAD_RESULT.ERROR_CREDENTIAL};
         * surfaced by {@link WamPrivateStatsUploader} when its
         * {@link WamPrivateStatsTokenIssuer} throws.
         */
        ERROR_CREDENTIAL,
        /**
         * The server returned HTTP {@code 401} indicating the
         * hard-coded {@code access_token} is no longer accepted.
         *
         * @apiNote
         * Mirrors {@code PS_BUFFER_UPLOAD_RESULT.ERROR_ACCESS_TOKEN}.
         */
        ERROR_ACCESS_TOKEN,
        /**
         * Any other failure: a network error, an unexpected HTTP
         * status, or a transport-layer exception.
         *
         * @apiNote
         * Mirrors {@code PS_BUFFER_UPLOAD_RESULT.ERROR_OTHER}.
         */
        ERROR_OTHER
    }
}
