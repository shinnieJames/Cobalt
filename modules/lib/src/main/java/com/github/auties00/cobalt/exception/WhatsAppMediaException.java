package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.OptionalInt;

/**
 * Base exception for media-related errors in the WhatsApp protocol.
 * <p>
 * This sealed class hierarchy represents all failures that can occur during media operations
 * including uploading, downloading, processing, and managing media connections. Media operations
 * in WhatsApp involve separate infrastructure from the main messaging servers, using dedicated
 * media endpoints for file transfer.
 *
 * <h2>Media Architecture</h2>
 * WhatsApp media operations follow this flow:
 * <ol>
 *   <li><b>Connection:</b> Establish connection to media servers using dynamic endpoints</li>
 *   <li><b>Upload:</b> Encrypt media locally, upload to server, receive download URL</li>
 *   <li><b>Download:</b> Fetch encrypted media from URL, decrypt using media keys</li>
 *   <li><b>Processing:</b> Generate thumbnails, extract metadata, validate formats</li>
 * </ol>
 *
 * <h2>HTTP Status Codes</h2>
 * The MMS CDN servers return specific HTTP status codes that map to distinct error conditions.
 * These are preserved in the {@link #httpStatusCode()} field for programmatic inspection by
 * the configurable error handler:
 * <ul>
 *   <li>{@code 401} - Unauthorized: authentication token expired or invalid</li>
 *   <li>{@code 403} - Forbidden: access denied to the media resource</li>
 *   <li>{@code 404} - Not Found: media has been deleted or URL expired</li>
 *   <li>{@code 410} - Gone: treated identically to 404 (media no longer available)</li>
 *   <li>{@code 413} - Payload Too Large: media exceeds server size limits</li>
 *   <li>{@code 415} - Unsupported Media Type: media format invalid or hash mismatch</li>
 *   <li>{@code 507} - Insufficient Storage: server-side throttling</li>
 * </ul>
 *
 * <h2>Exception Hierarchy</h2>
 * <ul>
 *   <li>{@link Connection} - Media server connection failures</li>
 *   <li>{@link Upload} - Media upload failures</li>
 *   <li>{@link Download} - Media download failures</li>
 *   <li>{@link Processing} - Media processing failures (encryption, decryption, thumbnails)</li>
 * </ul>
 *
 * <h2>Error Recovery</h2>
 * Media exceptions are non-fatal, meaning the client connection remains active. Individual
 * media operations can be retried without affecting the session. Common recovery strategies:
 * <ul>
 *   <li>Retry with exponential backoff for transient network errors</li>
 *   <li>Request new media connection for connection failures</li>
 *   <li>Re-request media URL for expired download links</li>
 * </ul>
 *
 * @implNote WAWebMmsClientErrors - maps WA Web's six MMS error classes
 *     ({@code MediaNotFoundError}, {@code MediaTooLargeError}, {@code MediaInvalidError},
 *     {@code MMSUnauthorizedError}, {@code MMSForbiddenError}, {@code MMSThrottleError})
 *     into the sealed {@code Upload}/{@code Download} subtypes with HTTP status code metadata
 * @see Connection
 * @see Upload
 * @see Download
 * @see Processing
 */
@WhatsAppWebModule(moduleName = "WAWebMmsClientErrors")
@WhatsAppWebModule(moduleName = "WAWebHttpErrors")
public sealed class WhatsAppMediaException extends WhatsAppException
        permits WhatsAppMediaException.Download,
                WhatsAppMediaException.Processing,
                WhatsAppMediaException.Upload,
                WhatsAppMediaException.Connection {

    /**
     * HTTP status code 401 returned by the MMS CDN when the authentication token is expired or invalid.
     * <p>
     * Corresponds to WA Web's {@code MMSUnauthorizedError}. This error is retryable after
     * refreshing the media connection authentication.
     *
     * @implNote WAWebMmsClientErrors.MMSUnauthorizedError
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientErrors", exports = "MMSUnauthorizedError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final int HTTP_UNAUTHORIZED = 401;

    /**
     * HTTP status code 403 returned by the MMS CDN when access to the media resource is denied.
     * <p>
     * Corresponds to WA Web's {@code MMSForbiddenError}. When the response body contains
     * "URL signature expired" or the URL's expiration date has passed, WA Web reclassifies
     * this as a {@code MediaNotFoundError} instead.
     *
     * @implNote WAWebMmsClientErrors.MMSForbiddenError
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientErrors", exports = "MMSForbiddenError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final int HTTP_FORBIDDEN = 403;

    /**
     * HTTP status code 404 returned by the MMS CDN when the media file is not found.
     * <p>
     * Corresponds to WA Web's {@code MediaNotFoundError}. HTTP 410 (Gone) is treated
     * identically. This error indicates the media has been deleted from servers or the
     * download URL has expired.
     *
     * @implNote WAWebMmsClientErrors.MediaNotFoundError
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientErrors", exports = "MediaNotFoundError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final int HTTP_NOT_FOUND = 404;

    /**
     * HTTP status code 413 returned by the MMS CDN when the uploaded media exceeds size limits.
     * <p>
     * Corresponds to WA Web's {@code MediaTooLargeError}. This error is not retryable
     * as the media must be compressed or split before retrying.
     *
     * @implNote WAWebMmsClientErrors.MediaTooLargeError
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientErrors", exports = "MediaTooLargeError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final int HTTP_TOO_LARGE = 413;

    /**
     * HTTP status code 415 returned by the MMS CDN when the media format is invalid or the hash mismatches.
     * <p>
     * Corresponds to WA Web's {@code MediaInvalidError}. This typically indicates a
     * ciphertext hash mismatch during upload.
     *
     * @implNote WAWebMmsClientErrors.MediaInvalidError
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientErrors", exports = "MediaInvalidError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final int HTTP_INVALID_MEDIA = 415;

    /**
     * HTTP status code 507 returned by the MMS CDN when the server is throttling requests.
     * <p>
     * Corresponds to WA Web's {@code MMSThrottleError}. Despite being a 5xx status code,
     * this error is explicitly not retryable according to WA Web's
     * {@code WAWebMmsClientIsErrorRetryable.isRetriableStatusCode}.
     *
     * @implNote WAWebMmsClientErrors.MMSThrottleError
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientErrors", exports = "MMSThrottleError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public static final int HTTP_THROTTLE = 507;

    /**
     * The HTTP status code returned by the MMS CDN server, or {@code -1} if not applicable.
     */
    private final int httpStatusCode;

    /**
     * Constructs a new media exception with the specified detail message.
     *
     * @param message the detail message describing the media error
     * @implNote WAWebMmsClientErrors - base constructor without HTTP status code
     */
    public WhatsAppMediaException(String message) {
        super(message);
        this.httpStatusCode = -1;
    }

    /**
     * Constructs a new media exception with the specified HTTP status code and detail message.
     *
     * @param httpStatusCode the HTTP status code from the MMS CDN server
     * @param message        the detail message describing the media error
     * @implNote WAWebHttpErrors.HttpStatusCodeError - WA Web's {@code HttpStatusCodeError(status, name, options)}
     *     constructor stores {@code status} on the error instance; Cobalt preserves the same
     *     field via {@link #httpStatusCode()}. WA Web composes the message string
     *     {@code name + ": " + status [+ " " + url]} at the call site; Cobalt expects the caller
     *     to pre-build the message in the same shape.
     */
    @WhatsAppWebExport(moduleName = "WAWebHttpErrors", exports = "HttpStatusCodeError",
                       adaptation = WhatsAppAdaptation.ADAPTED)
    public WhatsAppMediaException(int httpStatusCode, String message) {
        super(message);
        this.httpStatusCode = httpStatusCode;
    }

    /**
     * Constructs a new media exception with the specified detail message and cause.
     *
     * @param message the detail message describing the media error
     * @param cause   the underlying cause of this exception
     * @implNote WAWebMmsClientErrors - base constructor without HTTP status code
     */
    public WhatsAppMediaException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = -1;
    }

    /**
     * Constructs a new media exception wrapping the specified cause.
     *
     * @param cause the underlying cause of this exception
     * @implNote WAWebMmsClientErrors - base constructor without HTTP status code
     */
    public WhatsAppMediaException(Throwable cause) {
        super(cause);
        this.httpStatusCode = -1;
    }

    /**
     * Returns whether this exception represents a fatal error.
     * <p>
     * Media exceptions are never fatal as they affect individual media operations
     * but do not compromise the overall session or connection.
     *
     * @return {@code false} for all media exceptions
     * @implNote WAWebMmsClientErrors - all MMS errors are non-fatal; WA Web handles
     *     them with catch blocks that log or surface UI errors without disconnecting
     */
    @Override
    public boolean isFatal() {
        return false;
    }

    /**
     * Returns the HTTP status code from the MMS CDN server, if available.
     * <p>
     * When the media error originated from an HTTP response, this returns the status code
     * that triggered the error. This allows the configurable error handler to make
     * status-code-specific decisions (e.g., retry on 401 after refreshing auth, do not
     * retry on 413 or 507).
     * <p>
     * The returned value corresponds to one of the WA Web MMS error types:
     * <ul>
     *   <li>{@link #HTTP_UNAUTHORIZED} (401) - {@code MMSUnauthorizedError}</li>
     *   <li>{@link #HTTP_FORBIDDEN} (403) - {@code MMSForbiddenError}</li>
     *   <li>{@link #HTTP_NOT_FOUND} (404) - {@code MediaNotFoundError}</li>
     *   <li>{@link #HTTP_TOO_LARGE} (413) - {@code MediaTooLargeError}</li>
     *   <li>{@link #HTTP_INVALID_MEDIA} (415) - {@code MediaInvalidError}</li>
     *   <li>{@link #HTTP_THROTTLE} (507) - {@code MMSThrottleError}</li>
     * </ul>
     *
     * @return an {@link OptionalInt} containing the HTTP status code, or empty if the
     *     error did not originate from an HTTP response
     * @implNote WAWebHttpErrors.HttpStatusCodeError.status - the {@code status} field
     *     stored on all {@code HttpStatusCodeError} subclasses
     */
    public OptionalInt httpStatusCode() {
        return httpStatusCode == -1 ? OptionalInt.empty() : OptionalInt.of(httpStatusCode);
    }

    /**
     * Exception thrown when establishing or maintaining media server connections fails.
     * <p>
     * WhatsApp uses dedicated media servers separate from the main WebSocket connection.
     * Media connections are established dynamically and have a limited lifetime. This
     * exception occurs when:
     * <ul>
     *   <li>Initial connection to media servers fails</li>
     *   <li>Media connection expires during an operation</li>
     *   <li>Authentication with media servers fails</li>
     *   <li>No media hosts are available in the response</li>
     *   <li>TLS/SSL handshake with media server fails</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * When this exception occurs, the client should:
     * <ol>
     *   <li>Request a new media connection from the server</li>
     *   <li>Wait for the new connection parameters</li>
     *   <li>Retry the media operation with the new connection</li>
     * </ol>
     *
     * @implNote WAWebMediaHostsErrors.NoMediaHostsError - connection-level errors
     *     are not part of WAWebMmsClientErrors but represent media infrastructure failures
     */
    @WhatsAppWebModule(moduleName = "WAWebMediaHostsErrors")
    public static final class Connection extends WhatsAppMediaException {
        /**
         * Constructs a new media connection exception with a default message.
         *
         * @implNote WAWebMediaHostsErrors.NoMediaHostsError - default connection failure
         */
        public Connection() {
            super("Media connection failed");
        }

        /**
         * Constructs a new media connection exception with the specified message.
         *
         * @param message the detail message describing the connection failure
         * @implNote WAWebMediaHostsErrors.NoMediaHostsError - connection failure with context
         */
        public Connection(String message) {
            super(message);
        }

        /**
         * Constructs a new media connection exception with the specified message and cause.
         *
         * @param message the detail message describing the connection failure
         * @param cause   the underlying cause of the connection failure
         * @implNote WAWebMediaHostsErrors.NoMediaHostsError - connection failure with cause chain
         */
        public Connection(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new media connection exception wrapping the specified cause.
         *
         * @param cause the underlying cause of the connection failure
         * @implNote WAWebMediaHostsErrors.NoMediaHostsError - connection failure wrapping cause
         */
        public Connection(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Exception thrown when media download fails.
     * <p>
     * Media downloads involve fetching encrypted content from WhatsApp's CDN and
     * decrypting it using the media keys provided in the message. This exception
     * occurs when:
     * <ul>
     *   <li>The media URL has expired (typically after 30 days)</li>
     *   <li>Network errors prevent fetching the content</li>
     *   <li>The media server returns an error response</li>
     *   <li>The downloaded content fails integrity validation</li>
     *   <li>Decryption fails due to invalid or missing media keys</li>
     *   <li>The media file has been deleted from servers</li>
     * </ul>
     *
     * <h2>HTTP Status Code Mapping</h2>
     * The following WA Web MMS error types map to this exception during download operations:
     * <ul>
     *   <li>{@code MediaNotFoundError} (404, 410) - media deleted or URL expired</li>
     *   <li>{@code MMSUnauthorizedError} (401) - auth token expired</li>
     *   <li>{@code MMSForbiddenError} (403) - access denied (non-expired URL)</li>
     *   <li>{@code MMSThrottleError} (507) - server throttling</li>
     * </ul>
     *
     * <h2>Media Key Structure</h2>
     * Each media file is encrypted with a unique key derived from:
     * <ul>
     *   <li>File encryption key (32 bytes)</li>
     *   <li>File HMAC key (32 bytes)</li>
     *   <li>Initialization vector (16 bytes)</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * <ul>
     *   <li>For expired URLs: Request a re-upload from the sender</li>
     *   <li>For transient errors: Retry with exponential backoff</li>
     *   <li>For deleted media: Inform user that media is no longer available</li>
     * </ul>
     *
     * @implNote WAWebMmsClientErrors.MediaNotFoundError, WAWebMmsClientErrors.MMSForbiddenError,
     *     WAWebMmsClientErrors.MMSUnauthorizedError, WAWebMmsClientErrors.MMSThrottleError -
     *     download-context MMS errors are collapsed into this single type with HTTP status code metadata
     */
    @WhatsAppWebModule(moduleName = "WAWebMmsClientErrors")
    @WhatsAppWebModule(moduleName = "WAWebMmsClientMmsDownload")
    public static final class Download extends WhatsAppMediaException {
        /**
         * Constructs a new media download exception with the specified message.
         *
         * @param message the detail message describing the download failure
         * @implNote WAWebMmsClientErrors - download error without HTTP status code context
         */
        public Download(String message) {
            super(message);
        }

        /**
         * Constructs a new media download exception with the specified HTTP status code and message.
         * <p>
         * Use this constructor when the download failure is caused by a specific HTTP error
         * response from the MMS CDN server. The status code is preserved for programmatic
         * inspection by the configurable error handler.
         *
         * @param httpStatusCode the HTTP status code from the MMS CDN server
         * @param message        the detail message describing the download failure
         * @implNote WAWebMmsClientMmsDownload.validateMmsResponse - maps HTTP status codes
         *     to specific MMS error types: 401 ({@code MMSUnauthorizedError}),
         *     403 ({@code MMSForbiddenError} or {@code MediaNotFoundError} if expired),
         *     404/410 ({@code MediaNotFoundError}), 507 ({@code MMSThrottleError})
         */
        public Download(int httpStatusCode, String message) {
            super(httpStatusCode, message);
        }

        /**
         * Constructs a new media download exception with the specified message and cause.
         *
         * @param message the detail message describing the download failure
         * @param cause   the underlying cause of the download failure
         * @implNote WAWebHttpErrors.HttpNetworkError - WA Web's
         *     {@code WAWebHttpExtendedFetch} wraps any non-AbortError fetch failure in a
         *     {@code HttpNetworkError(message)}; Cobalt instead uses Java's {@link java.net.http.HttpClient}
         *     and folds {@link java.io.IOException} / {@link InterruptedException} into this
         *     {@code Download(message, cause)} constructor at the call site (see
         *     {@code MediaConnection.tryDownload}). The lack of HTTP status code on the resulting
         *     exception is what {@code isDownloadRetryable} keys off to reproduce WA Web's
         *     {@code WAWebMmsClientIsErrorRetryable.isErrorRetryable} branch for {@code HttpNetworkError}.
         * @implNote WAWebHttpErrors.HttpTimedOutError - WA Web declares
         *     {@code HttpTimedOutError extends HttpNetworkError} but no module in the bundle
         *     constructs one; the class is dead in the JS source. Java's
         *     {@link java.net.http.HttpTimeoutException} (a subclass of {@link java.io.IOException})
         *     reaches this same constructor when the underlying request times out, mirroring
         *     the inheritance shape on the JS side.
         */
        @WhatsAppWebExport(moduleName = "WAWebHttpErrors", exports = "HttpNetworkError",
                           adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebHttpErrors", exports = "HttpTimedOutError",
                           adaptation = WhatsAppAdaptation.ADAPTED)
        public Download(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new media download exception wrapping the specified cause.
         *
         * @param cause the underlying cause of the download failure
         * @implNote WAWebMmsClientErrors - download error wrapping cause
         */
        public Download(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Exception thrown when media upload fails.
     * <p>
     * Media uploads involve encrypting content locally, uploading to WhatsApp's media
     * servers, and receiving a download URL to include in the message. This exception
     * occurs when:
     * <ul>
     *   <li>Media connection is not available or expired</li>
     *   <li>Network errors during upload</li>
     *   <li>Server rejects the upload (size limits, format restrictions)</li>
     *   <li>Authentication token has expired</li>
     *   <li>Upload resumption fails</li>
     *   <li>Server returns unexpected response format</li>
     * </ul>
     *
     * <h2>HTTP Status Code Mapping</h2>
     * The following WA Web MMS error types map to this exception during upload operations:
     * <ul>
     *   <li>{@code MMSUnauthorizedError} (401) - auth token expired</li>
     *   <li>{@code MediaTooLargeError} (413) - media exceeds size limits</li>
     *   <li>{@code MediaInvalidError} (415) - media format invalid or hash mismatch</li>
     *   <li>{@code MMSThrottleError} (507) - server throttling</li>
     * </ul>
     *
     * <h2>Upload Process</h2>
     * <ol>
     *   <li>Generate random media keys</li>
     *   <li>Encrypt media content with AES-CBC</li>
     *   <li>Compute file hash for integrity</li>
     *   <li>Upload encrypted content to media server</li>
     *   <li>Receive direct path (download URL) from server</li>
     * </ol>
     *
     * <h2>Recovery</h2>
     * <ul>
     *   <li>For connection errors: Request new media connection and retry</li>
     *   <li>For size limits: Compress media or inform user of limits</li>
     *   <li>For transient errors: Retry with exponential backoff</li>
     * </ul>
     *
     * @implNote WAWebMmsClientErrors.MMSUnauthorizedError, WAWebMmsClientErrors.MediaTooLargeError,
     *     WAWebMmsClientErrors.MediaInvalidError, WAWebMmsClientErrors.MMSThrottleError -
     *     upload-context MMS errors are collapsed into this single type with HTTP status code metadata
     */
    @WhatsAppWebModule(moduleName = "WAWebMmsClientErrors")
    public static final class Upload extends WhatsAppMediaException {
        /**
         * Constructs a new media upload exception with the specified message.
         *
         * @param message the detail message describing the upload failure
         * @implNote WAWebMmsClientErrors - upload error without HTTP status code context
         */
        public Upload(String message) {
            super(message);
        }

        /**
         * Constructs a new media upload exception with the specified HTTP status code and message.
         * <p>
         * Use this constructor when the upload failure is caused by a specific HTTP error
         * response from the MMS CDN server. The status code is preserved for programmatic
         * inspection by the configurable error handler.
         *
         * @param httpStatusCode the HTTP status code from the MMS CDN server
         * @param message        the detail message describing the upload failure
         * @implNote WAWebMmsClientMmsUpload - maps HTTP status codes to specific MMS error types:
         *     401 ({@code MMSUnauthorizedError}), 413 ({@code MediaTooLargeError}),
         *     415 ({@code MediaInvalidError}), 507 ({@code MMSThrottleError})
         */
        public Upload(int httpStatusCode, String message) {
            super(httpStatusCode, message);
        }

        /**
         * Constructs a new media upload exception with the specified message and cause.
         *
         * @param message the detail message describing the upload failure
         * @param cause   the underlying cause of the upload failure
         * @implNote WAWebMmsClientErrors - upload error with cause chain
         */
        public Upload(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new media upload exception wrapping the specified cause.
         *
         * @param cause the underlying cause of the upload failure
         * @implNote WAWebMmsClientErrors - upload error wrapping cause
         */
        public Upload(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Exception thrown when media processing fails.
     * <p>
     * Media processing encompasses all operations on media content beyond transfer,
     * including encryption, decryption, thumbnail generation, format conversion,
     * and metadata extraction. This exception occurs when:
     * <ul>
     *   <li>Encryption/decryption operations fail</li>
     *   <li>Thumbnail generation fails</li>
     *   <li>Media format is unsupported or invalid</li>
     *   <li>Metadata extraction fails</li>
     *   <li>Content validation fails (corrupted data)</li>
     *   <li>Required codecs are not available</li>
     * </ul>
     *
     * <h2>Processing Operations</h2>
     * <ul>
     *   <li><b>Images:</b> JPEG compression, thumbnail generation, EXIF extraction</li>
     *   <li><b>Videos:</b> H.264 encoding, thumbnail extraction, duration detection</li>
     *   <li><b>Audio:</b> Opus/AAC encoding, waveform generation, duration detection</li>
     *   <li><b>Documents:</b> Preview generation, metadata extraction</li>
     *   <li><b>Stickers:</b> WebP validation, animation detection</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * <ul>
     *   <li>For format errors: Convert to supported format before retry</li>
     *   <li>For corruption: Request re-send of original content</li>
     *   <li>For codec errors: Use fallback processing or skip optional features</li>
     * </ul>
     *
     * @implNote WAWebHttpErrors.HttpInvalidResponseError - the parent type in WA Web for
     *     responses whose body fails structural validation (used for the upload-progress JSON
     *     missing {@code resume} field, etc.); Cobalt collapses it into {@code Processing}.
     * @implNote WAWebHttpErrors.MmsDownloadFilehashMismatchError - the
     *     {@code HttpInvalidResponseError} subclass thrown when downloaded ciphertext byte
     *     length disagrees with the expected {@code encFilehash}; Cobalt's analogous integrity
     *     verification is handled inside {@code MediaDownloadInputStream} but throws as
     *     {@link Download} so the existing per-host retry loop can react.
     */
    @WhatsAppWebModule(moduleName = "WAWebHttpErrors")
    public static final class Processing extends WhatsAppMediaException {
        /**
         * Constructs a new media processing exception with the specified message.
         *
         * @param message the detail message describing the processing failure
         * @implNote WAWebHttpErrors.HttpInvalidResponseError - WA Web throws
         *     {@code HttpInvalidResponseError(message, options)} when an MMS response is
         *     structurally valid (HTTP 2xx) but the body is missing required fields
         *     (e.g. {@code WAWebMmsClientMmsGetUploadProgress} on missing {@code resume}).
         *     Cobalt uses {@code Processing(message)} for the same semantic of "the response
         *     parsed but the content is unusable". WA Web composes the message as
         *     {@code message [+ ": " + url]}; Cobalt expects the caller to embed any url
         *     context in the supplied message.
         * @implNote WAWebHttpErrors.MmsDownloadFilehashMismatchError - WA Web's
         *     {@code MmsDownloadFilehashMismatchError(options)} extends
         *     {@code HttpInvalidResponseError} with a fixed message
         *     {@code "mmsDownload: filehash mismatch"} and is thrown when the byte length
         *     of the downloaded ciphertext disagrees with {@code encFilehash}. Cobalt's
         *     equivalent integrity check is implemented in {@code MediaDownloadInputStream}
         *     and currently throws as {@link Download} ("Ciphertext SHA256 hash doesn't match
         *     the expected value") to reuse the download-retry pipeline; both are non-fatal
         *     and surface through {@link WhatsAppClientErrorHandler} unchanged.
         */
        @WhatsAppWebExport(moduleName = "WAWebHttpErrors", exports = "HttpInvalidResponseError",
                           adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebHttpErrors", exports = "MmsDownloadFilehashMismatchError",
                           adaptation = WhatsAppAdaptation.ADAPTED)
        public Processing(String message) {
            super(message);
        }

        /**
         * Constructs a new media processing exception with the specified message and cause.
         *
         * @param message the detail message describing the processing failure
         * @param cause   the underlying cause of the processing failure
         * @implNote WAWebHttpErrors.MmsDownloadFilehashMismatchError - processing error with cause
         */
        public Processing(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new media processing exception wrapping the specified cause.
         *
         * @param cause the underlying cause of the processing failure
         * @implNote WAWebHttpErrors.MmsDownloadFilehashMismatchError - processing error wrapping cause
         */
        public Processing(Throwable cause) {
            super(cause);
        }
    }
}
