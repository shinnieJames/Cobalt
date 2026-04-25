package com.github.auties00.cobalt.util;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.event.WebcMediaErrorUnknownDetailsEventBuilder;
import com.github.auties00.cobalt.wam.type.BackendStoreType;
import com.github.auties00.cobalt.wam.type.MediaDownloadModeType;
import com.github.auties00.cobalt.wam.type.MediaDownloadResultType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MediaUploadModeType;
import com.github.auties00.cobalt.wam.type.MediaUploadResultType;
import com.github.auties00.cobalt.wam.type.WebcMediaOperationCode;
import com.github.auties00.cobalt.wam.WamService;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Static helpers that adapt {@code WAWebWamMediaMetricUtils} for the Cobalt
 * stack.
 *
 * <p>The WA Web module exposes nine helpers consumed by the media-upload and
 * media-download metrics pipelines: enum mappings (web media-type string to
 * {@link MediaType}, upload/download mode types, backend-store classification
 * from a direct-path prefix), error classifiers ({@code Throwable} to
 * {@link MediaUploadResultType} / {@link MediaDownloadResultType}), an HTTP
 * status-code accessor, a random media-event identifier generator and a
 * one-shot {@code WebcMediaErrorUnknownDetailsWamEvent} emitter.
 *
 * <p>Each export is replicated here so every caller (history-sync downloads,
 * app-state external-patch uploads, future media-message uploads) routes
 * through the same code path and produces identical telemetry.
 *
 * <p>Instances of this class are not meant to be created: all members are
 * {@code static}. The hidden private constructor prevents accidental
 * instantiation.
 *
 * @implNote WAWebWamMediaMetricUtils: each export is mirrored as a static
 *     method below. The classifier methods adapt the WA Web JavaScript
 *     {@code instanceof} cascade to Cobalt's
 *     {@link WhatsAppMediaException} sealed hierarchy plus
 *     {@link InterruptedException} for cancel detection (see CLAUDE.md error
 *     model).
 */
@WhatsAppWebModule(moduleName = "WAWebWamMediaMetricUtils")
public final class MediaMetricUtils {
    /**
     * Detail-message prefix produced by Cobalt's {@code MediaDownloadInputStream}
     * when the downloaded ciphertext SHA-256 disagrees with the expected
     * {@code encFilehash}.
     *
     * <p>This is Cobalt's analogue of WA Web's {@code MmsDownloadFilehashMismatchError}
     * (fixed message {@code "mmsDownload: filehash mismatch"}); the integrity check
     * surfaces as {@link WhatsAppMediaException.Download} so the existing per-host
     * retry loop reacts to it.
     *
     * @implNote WAWebHttpErrors.MmsDownloadFilehashMismatchError: WA Web throws a
     *     dedicated error subclass when the ciphertext byte length disagrees with
     *     {@code encFilehash}; Cobalt collapses this into a {@code Download}
     *     exception with this exact message prefix so
     *     {@link #getMetricDownloadErrorResultType(Throwable)} can map it to
     *     {@link MediaDownloadResultType#ERROR_ENC_HASH_MISMATCH}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHttpErrors", exports = "MmsDownloadFilehashMismatchError",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final String CIPHERTEXT_HASH_MISMATCH_MESSAGE =
            "Ciphertext SHA256 hash doesn't match the expected value";

    /**
     * Detail-message prefix produced by Cobalt's {@code MediaDownloadInputStream}
     * when the decrypted plaintext SHA-256 disagrees with the expected
     * {@code fileSha256}.
     *
     * <p>This is Cobalt's analogue of WA Web's
     * {@code MediaDecryptionError(message)} where {@code message.includes(
     * PLAINTEXT_HASH_MISMATCH_ERROR)} — it represents a successful download whose
     * decrypted bytes failed integrity verification.
     *
     * @implNote WAWebMiscErrors.MediaDecryptionError combined with
     *     {@code WAWebMiscErrors.PLAINTEXT_HASH_MISMATCH_ERROR =
     *     "plaintext hash mismatch"}: WA Web checks {@code e instanceof
     *     MediaDecryptionError && e.message.includes(...)}; Cobalt's
     *     {@code MediaDownloadInputStream} surfaces the same condition as a
     *     {@code Download} with this exact message prefix.
     */
    @WhatsAppWebExport(moduleName = "WAWebMiscErrors", exports = "MediaDecryptionError",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMiscErrors", exports = "PLAINTEXT_HASH_MISMATCH_ERROR",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final String PLAINTEXT_HASH_MISMATCH_MESSAGE =
            "Plaintext SHA256 hash doesn't match the expected value";

    /**
     * Hidden private constructor.
     *
     * <p>This class is a stateless static-method holder; instantiation would be
     * meaningless and is therefore prevented.
     */
    private MediaMetricUtils() {
        throw new AssertionError("MediaMetricUtils is a static-only utility");
    }

    /**
     * Returns whether the given web-media-type string represents a thumbnail
     * variant, mirroring WA Web's local helper {@code u}.
     *
     * <p>WA Web's helper is not exported but is referenced by both
     * {@link #getMetricOverallDownloadModeType(String, String, boolean)} and
     * {@link #getMetricOverallUploadModeType(String)} to short-circuit to the
     * thumbnail mode-type bucket.
     *
     * @implNote WAWebWamMediaMetricUtils local helper {@code u}:
     *     {@code e === "thumbnail-document" || e === "thumbnail-image" ||
     *     e === "thumbnail-video" || e === "thumbnail-link" ||
     *     e === "newsletter-thumbnail-link"}.
     * @param webMediaType the {@code WAWebMmsMediaTypes.MEDIA_TYPES} string
     *                     identifying the media variant
     * @return {@code true} if the value is one of the five thumbnail variants
     */
    private static boolean isThumbnailMediaType(String webMediaType) {
        return "thumbnail-document".equals(webMediaType)
                || "thumbnail-image".equals(webMediaType)
                || "thumbnail-video".equals(webMediaType)
                || "thumbnail-link".equals(webMediaType)
                || "newsletter-thumbnail-link".equals(webMediaType);
    }

    /**
     * Maps the triple {@code (webMediaType, downloadReason, isPrefetched)} to
     * a {@link MediaDownloadModeType}.
     *
     * <p>The lookup order matches WA Web exactly: thumbnail variants always
     * collapse to {@link MediaDownloadModeType#THUMBNAIL}; otherwise an
     * explicit {@code "manual"} download reason wins, then a truthy
     * {@code isPrefetched} flag, and the fallback is
     * {@link MediaDownloadModeType#FULL}.
     *
     * @implNote WAWebWamMediaMetricUtils.getMetricOverallDownloadModeType
     *     ({@code function c}): {@code u(e) ? THUMBNAIL : t === "manual" ?
     *     MANUAL : n ? PREFETCH : FULL}.
     * @param webMediaType   the {@code WAWebMmsMediaTypes.MEDIA_TYPES} string
     *                       describing the media being downloaded
     * @param downloadReason the {@code "manual"} / non-{@code "manual"} reason
     *                       passed by the caller
     * @param isPrefetched   {@code true} when the download is part of an
     *                       autodownload prefetch cycle
     * @return the matching {@link MediaDownloadModeType}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricOverallDownloadModeType",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static MediaDownloadModeType getMetricOverallDownloadModeType(
            String webMediaType,
            String downloadReason,
            boolean isPrefetched
    ) {
        if (isThumbnailMediaType(webMediaType)) {
            return MediaDownloadModeType.THUMBNAIL;
        }
        if ("manual".equals(downloadReason)) {
            return MediaDownloadModeType.MANUAL;
        }
        if (isPrefetched) {
            return MediaDownloadModeType.PREFETCH;
        }
        return MediaDownloadModeType.FULL;
    }

    /**
     * Maps a web-media-type string to a {@link MediaUploadModeType}.
     *
     * <p>Thumbnail variants collapse to {@link MediaUploadModeType#THUMBNAIL};
     * everything else maps to {@link MediaUploadModeType#REGULAR}. This is the
     * coarsest of the upload-mode classifiers — finer-grained values like
     * {@code FAST_FORWARD_EXIST_CHECK} or {@code WEB_REUPLOAD} are decided at
     * the call site.
     *
     * @implNote WAWebWamMediaMetricUtils.getMetricOverallUploadModeType
     *     ({@code function d}): {@code u(e) ? THUMBNAIL : REGULAR}.
     * @param webMediaType the {@code WAWebMmsMediaTypes.MEDIA_TYPES} string
     *                     describing the media being uploaded
     * @return {@link MediaUploadModeType#THUMBNAIL} for thumbnail variants,
     *         {@link MediaUploadModeType#REGULAR} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricOverallUploadModeType",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static MediaUploadModeType getMetricOverallUploadModeType(String webMediaType) {
        if (isThumbnailMediaType(webMediaType)) {
            return MediaUploadModeType.THUMBNAIL;
        }
        return MediaUploadModeType.REGULAR;
    }

    /**
     * Maps a {@code WAWebMmsMediaTypes.MEDIA_TYPES} string to the matching
     * {@link MediaType} reported on WAM media events.
     *
     * <p>The mapping is exhaustive over WA Web's MMS media-type enum and
     * mirrors the JavaScript {@code switch} cascade verbatim — including the
     * cases where multiple input strings collapse to the same WAM enum
     * (e.g. {@code "image"}, {@code "waffle-image"}, {@code "thumbnail-image"}
     * and {@code "newsletter-image"} all map to {@link MediaType#PHOTO}).
     *
     * <p>Unrecognised values raise {@link IllegalArgumentException} with the
     * same {@code "webMediaType is invalid: <value>"} message format used by
     * WA Web's {@code err()} helper, preserving error provenance for callers
     * that catch and log.
     *
     * @implNote WAWebWamMediaMetricUtils.getMetricMediaType ({@code function
     *     m}): switches over the union of {@code "audio" / NEWSLETTER_AUDIO},
     *     {@code "document" / "thumbnail-document"}, {@code "gif" /
     *     NEWSLETTER_GIF}, {@code "image" / "waffle-image" / NEWSLETTER_IMAGE
     *     / "thumbnail-image"}, {@code "ppic"}, {@code "product" /
     *     "product-catalog-image"}, {@code "ptt" / NEWSLETTER_PTT},
     *     {@code "sticker" / NEWSLETTER_STICKER}, {@code "sticker-pack" /
     *     NEWSLETTER_STICKER_PACK / "thumbnail-sticker-pack"},
     *     {@code "video" / NEWSLETTER_VIDEO / "waffle-video" /
     *     "thumbnail-video"}, {@code "ptv" / NEWSLETTER_PTV},
     *     {@code "template"}, {@code "md-msg-hist"}, {@code "md-app-state"},
     *     {@code "thumbnail-link" / NEWSLETTER_THUMBNAIL_LINK},
     *     {@code "music-artwork" / "newsletter-music-artwork"} and the
     *     {@code NONE}-mapped {@code "payment-bg-image" / "biz-cover-photo" /
     *     "ads-image" / "ads-video" / "group-history"} variants.
     * @param webMediaType the {@code WAWebMmsMediaTypes.MEDIA_TYPES} string
     *                     to translate, must not be {@code null}
     * @return the matching {@link MediaType} value
     * @throws IllegalArgumentException when {@code webMediaType} does not
     *     correspond to any known {@code WAWebMmsMediaTypes.MEDIA_TYPES} value
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricMediaType",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static MediaType getMetricMediaType(String webMediaType) {
        Objects.requireNonNull(webMediaType, "webMediaType cannot be null");
        return switch (webMediaType) {
            case "audio", "newsletter-audio" -> MediaType.AUDIO;
            case "document", "thumbnail-document" -> MediaType.DOCUMENT;
            case "gif", "newsletter-gif" -> MediaType.GIF;
            case "image", "waffle-image", "newsletter-image", "thumbnail-image" -> MediaType.PHOTO;
            case "ppic" -> MediaType.PROFILE_PIC;
            case "product", "product-catalog-image" -> MediaType.PRODUCT_IMAGE;
            case "ptt", "newsletter-ptt" -> MediaType.PTT;
            case "sticker", "newsletter-sticker" -> MediaType.STICKER;
            case "sticker-pack", "newsletter-sticker-pack", "thumbnail-sticker-pack" -> MediaType.STICKER_PACK;
            case "video", "newsletter-video", "waffle-video", "thumbnail-video" -> MediaType.VIDEO;
            case "ptv", "newsletter-ptv" -> MediaType.PUSH_TO_VIDEO;
            case "template" -> MediaType.TEMPLATE;
            case "md-msg-hist" -> MediaType.MD_HISTORY_SYNC;
            case "md-app-state" -> MediaType.MD_APP_STATE;
            case "thumbnail-link", "newsletter-thumbnail-link" -> MediaType.URL;
            case "music-artwork", "newsletter-music-artwork" -> MediaType.MUSIC_ARTWORK;
            case "payment-bg-image", "biz-cover-photo", "ads-image", "ads-video", "group-history" -> MediaType.NONE;
            default -> throw new IllegalArgumentException("webMediaType is invalid: " + webMediaType);
        };
    }

    /**
     * Classifies a thrown upload error into a {@link MediaUploadResultType}.
     *
     * <p>The lookup mirrors WA Web's {@code instanceof} cascade — the order is
     * load-bearing because the WA Web error classes are not disjoint
     * (e.g. {@code MMSThrottleError} extends {@code HttpStatusCodeError(507)}
     * and {@code MediaTooLargeError} extends {@code HttpStatusCodeError(413)},
     * so the throttle and too-large branches must be examined before the
     * generic {@code status >= 500} branch).
     *
     * <p>Cobalt collapses the six {@code WAWebMmsClientErrors} subclasses into
     * {@link WhatsAppMediaException.Upload} carrying an optional HTTP status
     * code; this classifier therefore reads the status code first and
     * dispatches in the same priority order. {@code AbortError} is mapped from
     * {@link InterruptedException}, and {@code NoMediaHostsError} from
     * {@link WhatsAppMediaException.Connection}.
     *
     * @implNote WAWebWamMediaMetricUtils.getMetricUploadErrorResultType
     *     ({@code function p}): {@code MMSUnauthorizedError ->
     *     ERROR_NO_PERMISSIONS; MediaTooLargeError -> ERROR_BAD_MEDIA;
     *     MMSThrottleError -> ERROR_THROTTLE; HttpStatusCodeError && status >=
     *     500 -> ERROR_SERVER; NoMediaHostsError -> ERROR_MEDIA_CONN;
     *     AbortError -> ERROR_CANCEL; HttpNetworkError -> ERROR_UPLOAD; else
     *     -> ERROR_UNKNOWN}.
     * @param throwable the error that aborted the upload, or {@code null}
     * @return the matching {@link MediaUploadResultType}; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricUploadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaUploadResultType getMetricUploadErrorResultType(Throwable throwable) {
        for (var current = throwable; current != null; current = nextCause(current)) {
            // WAWebWamMediaMetricUtils.getMetricUploadErrorResultType:
            // e.name === ABORT_ERROR -> ERROR_CANCEL.
            if (current instanceof InterruptedException) {
                return MediaUploadResultType.ERROR_CANCEL;
            }
            // WAWebWamMediaMetricUtils.getMetricUploadErrorResultType:
            // e instanceof NoMediaHostsError -> ERROR_MEDIA_CONN.
            if (current instanceof WhatsAppMediaException.Connection) {
                return MediaUploadResultType.ERROR_MEDIA_CONN;
            }
            if (current instanceof WhatsAppMediaException.Upload upload) {
                var status = upload.httpStatusCode();
                if (status.isPresent()) {
                    var code = status.getAsInt();
                    return switch (code) {
                        // MMSUnauthorizedError(401) -> ERROR_NO_PERMISSIONS.
                        case 401 -> MediaUploadResultType.ERROR_NO_PERMISSIONS;
                        // MediaTooLargeError(413) -> ERROR_BAD_MEDIA.
                        case 413 -> MediaUploadResultType.ERROR_BAD_MEDIA;
                        // MMSThrottleError(507) -> ERROR_THROTTLE.
                        case 507 -> MediaUploadResultType.ERROR_THROTTLE;
                        default -> {
                            if (code >= 500) {
                                // HttpStatusCodeError && status >= 500 -> ERROR_SERVER.
                                yield MediaUploadResultType.ERROR_SERVER;
                            }
                            // MediaInvalidError(415), MMSForbiddenError(403),
                            // MediaNotFoundError(404), HttpStatusCodeError(other 4xx)
                            // all fall through to ERROR_UNKNOWN in WA Web.
                            yield MediaUploadResultType.ERROR_UNKNOWN;
                        }
                    };
                }
                // HttpNetworkError (no status) -> ERROR_UPLOAD.
                return MediaUploadResultType.ERROR_UPLOAD;
            }
        }
        // Default branch in WA Web: any other error -> ERROR_UNKNOWN.
        return MediaUploadResultType.ERROR_UNKNOWN;
    }

    /**
     * Classifies a thrown download error into a {@link MediaDownloadResultType}.
     *
     * <p>The cascade matches WA Web's {@code function _} verbatim: throttle
     * detection wins over generic status-code handling (because
     * {@code MMSThrottleError} extends {@code HttpStatusCodeError(507)}),
     * media-host failures map to {@link MediaDownloadResultType#ERROR_MEDIA_CONN},
     * generic network failures (no status) to
     * {@link MediaDownloadResultType#ERROR_NETWORK}, then the explicit
     * status-code switch (404/410/416/401/429/507), and finally the cancel
     * and hash-mismatch tail branches.
     *
     * <p>The hash-mismatch branches detect Cobalt's
     * {@link WhatsAppMediaException.Download} variants raised by
     * {@code MediaDownloadInputStream}: a ciphertext SHA-256 mismatch (the
     * Cobalt analogue of {@code MmsDownloadFilehashMismatchError}) maps to
     * {@link MediaDownloadResultType#ERROR_ENC_HASH_MISMATCH}, and a plaintext
     * SHA-256 mismatch (the Cobalt analogue of {@code MediaDecryptionError}
     * containing {@code PLAINTEXT_HASH_MISMATCH_ERROR}) maps to
     * {@link MediaDownloadResultType#ERROR_HASH_MISMATCH}.
     *
     * @implNote WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType
     *     ({@code function _}): {@code MMSThrottleError -> ERROR_THROTTLE;
     *     NoMediaHostsError -> ERROR_MEDIA_CONN; HttpNetworkError ->
     *     ERROR_NETWORK; HttpStatusCodeError -> switch(status){404,410:
     *     ERROR_TOO_OLD; 416: ERROR_CANNOT_RESUME; 401: ERROR_INVALID_URL;
     *     429,507: ERROR_THROTTLE; default: ERROR_UNKNOWN};
     *     AbortError -> ERROR_CANCEL; MmsDownloadFilehashMismatchError ->
     *     ERROR_ENC_HASH_MISMATCH; MediaDecryptionError && msg includes
     *     PLAINTEXT_HASH_MISMATCH_ERROR -> ERROR_HASH_MISMATCH; else ->
     *     ERROR_UNKNOWN}.
     * @param throwable the error raised by the download path, or {@code null}
     * @return the matching {@link MediaDownloadResultType}; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricDownloadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaDownloadResultType getMetricDownloadErrorResultType(Throwable throwable) {
        for (var current = throwable; current != null; current = nextCause(current)) {
            // WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType:
            // e.name === ABORT_ERROR -> ERROR_CANCEL (checked first because
            // InterruptedException can be wrapped under any media exception).
            if (current instanceof InterruptedException) {
                return MediaDownloadResultType.ERROR_CANCEL;
            }
            // WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType:
            // e instanceof NoMediaHostsError -> ERROR_MEDIA_CONN.
            if (current instanceof WhatsAppMediaException.Connection) {
                return MediaDownloadResultType.ERROR_MEDIA_CONN;
            }
            if (current instanceof WhatsAppMediaException.Download download) {
                var status = download.httpStatusCode();
                if (status.isPresent()) {
                    var code = status.getAsInt();
                    return switch (code) {
                        // 507 / 429 -> ERROR_THROTTLE (MMSThrottleError extends
                        // HttpStatusCodeError(507) so the throttle branch wins
                        // before the default).
                        case 429, 507 -> MediaDownloadResultType.ERROR_THROTTLE;
                        // 404 / 410 -> ERROR_TOO_OLD (MediaNotFoundError(404)).
                        case 404, 410 -> MediaDownloadResultType.ERROR_TOO_OLD;
                        // 416 -> ERROR_CANNOT_RESUME.
                        case 416 -> MediaDownloadResultType.ERROR_CANNOT_RESUME;
                        // 401 -> ERROR_INVALID_URL (MMSUnauthorizedError(401)).
                        case 401 -> MediaDownloadResultType.ERROR_INVALID_URL;
                        // Any other status -> ERROR_UNKNOWN.
                        default -> MediaDownloadResultType.ERROR_UNKNOWN;
                    };
                }
                // HttpInvalidResponseError + MmsDownloadFilehashMismatchError
                // and MediaDecryptionError(plaintext mismatch) all surface as
                // Download exceptions in Cobalt; key off the message prefix.
                var message = download.getMessage();
                if (message != null) {
                    if (message.startsWith(CIPHERTEXT_HASH_MISMATCH_MESSAGE)) {
                        return MediaDownloadResultType.ERROR_ENC_HASH_MISMATCH;
                    }
                    if (message.startsWith(PLAINTEXT_HASH_MISMATCH_MESSAGE)) {
                        return MediaDownloadResultType.ERROR_HASH_MISMATCH;
                    }
                }
                // HttpNetworkError (no status, no hash-mismatch marker) ->
                // ERROR_NETWORK.
                return MediaDownloadResultType.ERROR_NETWORK;
            }
            // WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType:
            // MediaDecryptionError && msg includes PLAINTEXT_HASH_MISMATCH_ERROR
            // -> ERROR_HASH_MISMATCH (also surfaces as Processing in Cobalt
            // when raised outside the streaming download path).
            if (current instanceof WhatsAppMediaException.Processing processing) {
                var message = processing.getMessage();
                if (message != null && message.startsWith(PLAINTEXT_HASH_MISMATCH_MESSAGE)) {
                    return MediaDownloadResultType.ERROR_HASH_MISMATCH;
                }
            }
        }
        // Default in WA Web: any other error -> ERROR_UNKNOWN.
        return MediaDownloadResultType.ERROR_UNKNOWN;
    }

    /**
     * Returns the HTTP status code carried by the given throwable, walking the
     * cause chain to find the first {@link WhatsAppMediaException} with a
     * status code attached.
     *
     * <p>Mirrors WA Web's {@code getStatusCode} which only inspects the top
     * exception; Cobalt walks the cause chain because Java exception wrappers
     * (notably {@link java.util.concurrent.CompletionException}) sometimes
     * sit between the call site and the original media exception.
     *
     * @implNote WAWebWamMediaMetricUtils.getStatusCode ({@code function f}):
     *     {@code e instanceof HttpStatusCodeError ? e.status : undefined}.
     * @param throwable the error to inspect, or {@code null}
     * @return the HTTP status code, or {@code null} if none of the exceptions
     *         in the chain carries one
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getStatusCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Integer getStatusCode(Throwable throwable) {
        for (var current = throwable; current != null; current = nextCause(current)) {
            if (current instanceof WhatsAppMediaException mediaException) {
                var status = mediaException.httpStatusCode();
                if (status.isPresent()) {
                    return status.getAsInt();
                }
            }
        }
        return null;
    }

    /**
     * Generates a random media-event identifier in {@code [1, 2^53 - 1]}.
     *
     * <p>The value is used as a correlation identifier on
     * {@code MediaUpload2Event}, {@code MediaDownload2Event} and
     * {@code WebcMediaErrorUnknownDetailsEvent} so the server can deduplicate
     * retried attempts. The range is fixed at JavaScript's
     * {@code Number.MAX_SAFE_INTEGER} (= 2<sup>53</sup> &minus; 1 =
     * {@value Long#MAX_VALUE}-aligned to {@code 9007199254740991}) to match
     * the WA Web range exactly.
     *
     * <p>{@link ThreadLocalRandom} is used instead of {@link Math#random()} so
     * the call is thread-safe under WAM's virtual-thread commit pipeline.
     *
     * @implNote WAWebWamMediaMetricUtils.generateMediaEventId ({@code function
     *     g}): {@code 1 + Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}.
     * @return a random {@code long} in the closed range {@code [1,
     *         9007199254740991]}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "generateMediaEventId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static long generateMediaEventId() {
        // ThreadLocalRandom.nextLong(bound) returns a value in [0, bound);
        // adding 1 yields the [1, MAX_SAFE_INTEGER] half-open range that
        // 1 + floor(MAX_SAFE_INTEGER * Math.random()) produces in JavaScript.
        return 1L + ThreadLocalRandom.current().nextLong(9007199254740991L);
    }

    /**
     * Maps a media direct-path string to the matching {@link BackendStoreType}
     * by inspecting the first two characters of the path.
     *
     * <p>WA Web treats the leading {@code /v}, {@code /o} and {@code /m}
     * markers as the canonical signals for Everstore, OIL and Manifold blob
     * stores respectively; any other prefix is unrecognized and logged
     * (Cobalt skips the WAM ERROR log because the {@code WALogger} channel
     * isn't separately observed by the WAM pipeline). A {@code null} or empty
     * path means the asset isn't using a direct-path scheme and maps to
     * {@link BackendStoreType#NON_DIRECT_PATH}.
     *
     * @implNote WAWebWamMediaMetricUtils.getMetricBackendStore ({@code function
     *     y}): {@code !t ? NON_DIRECT_PATH : t.slice(0,2).toLowerCase() ==
     *     "/v" ? EVERSTORE : "/o" ? OIL : "/m" ? MANIFOLD : null} (followed
     *     by an ERROR log via WAWebLogger that is omitted in Cobalt).
     * @param directPath the {@code direct_path} string handed back by the
     *                   media-upload server, or {@code null}
     * @return the matching {@link BackendStoreType}, or {@code null} when the
     *         prefix is unrecognized
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricBackendStore",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static BackendStoreType getMetricBackendStore(String directPath) {
        if (directPath == null || directPath.isEmpty()) {
            return BackendStoreType.NON_DIRECT_PATH;
        }
        // WAWebWamMediaMetricUtils.getMetricBackendStore: t.slice(0,2)
        // accepts strings shorter than two characters by falling back to the
        // single-character prefix. Java's substring would throw, so cap at len.
        var prefix = directPath.substring(0, Math.min(2, directPath.length()))
                .toLowerCase(Locale.ROOT);
        return switch (prefix) {
            case "/v" -> BackendStoreType.EVERSTORE;
            case "/o" -> BackendStoreType.OIL;
            case "/m" -> BackendStoreType.MANIFOLD;
            default -> null;
        };
    }

    /**
     * Emits a one-shot {@code WebcMediaErrorUnknownDetailsWamEvent} when the
     * outer {@link MediaUploadResultType} or {@link MediaDownloadResultType}
     * is {@code ERROR_UNKNOWN}, so the server can correlate the unclassified
     * error with its underlying class name and message.
     *
     * <p>The event is committed only when both an unknown overall result is
     * present AND the {@code throwable} is non-{@code null}; this matches WA
     * Web's guard {@code t != null && (a === ERROR_UNKNOWN || b ===
     * ERROR_UNKNOWN)}.
     *
     * <p>The download result takes priority over the upload result: in WA Web,
     * the conditional sets {@code n} to {@code DOWNLOAD} first and only
     * overrides to {@code UPLOAD} when the download branch did not match.
     *
     * @implNote WAWebWamMediaMetricUtils.logErrorUnknownDetails ({@code
     *     function h}): {@code if (t != null) { var n; if
     *     (e.overallDownloadResult === ERROR_UNKNOWN) n = DOWNLOAD; else if
     *     (e.overallUploadResult === ERROR_UNKNOWN) n = UPLOAD; if (n != null)
     *     new WebcMediaErrorUnknownDetailsWamEvent({mediaId, webcMediaOperation:
     *     n, webcMediaErrorName: t.name, webcMediaErrorMessage:
     *     t.message}).commit(); }}.
     * @param wamService     the WAM service used to commit the event when
     *                       triggered, must not be {@code null}
     * @param mediaId        the media-event identifier, propagated as
     *                       {@code mediaId}, or {@code null} to omit the field
     * @param downloadResult the overall download result, or {@code null} when
     *                       this call site is upload-only
     * @param uploadResult   the overall upload result, or {@code null} when
     *                       this call site is download-only
     * @param throwable      the original error that caused the unknown
     *                       classification, or {@code null} to skip emission
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "logErrorUnknownDetails",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static void logErrorUnknownDetails(
            WamService wamService,
            Long mediaId,
            MediaDownloadResultType downloadResult,
            MediaUploadResultType uploadResult,
            Throwable throwable
    ) {
        Objects.requireNonNull(wamService, "wamService cannot be null");
        if (throwable == null) {
            return;
        }
        WebcMediaOperationCode operation = null;
        if (downloadResult == MediaDownloadResultType.ERROR_UNKNOWN) {
            operation = WebcMediaOperationCode.DOWNLOAD;
        } else if (uploadResult == MediaUploadResultType.ERROR_UNKNOWN) {
            operation = WebcMediaOperationCode.UPLOAD;
        }
        if (operation == null) {
            return;
        }
        var builder = new WebcMediaErrorUnknownDetailsEventBuilder()
                .webcMediaOperation(operation)
                // WAWebWebcMediaErrorUnknownDetailsWamEvent constructor reads
                // {@code t.name} / {@code t.message}; Cobalt mirrors with
                // Throwable.getClass().getSimpleName() and getMessage().
                .webcMediaErrorName(throwable.getClass().getSimpleName())
                .webcMediaErrorMessage(throwable.getMessage());
        if (mediaId != null) {
            // mediaId is a JS double in [1, MAX_SAFE_INTEGER]; Cobalt's
            // WAM property is INTEGER (32-bit) so callers that overflow Integer
            // are responsible for narrowing. ThreadLocalRandom's range above
            // can exceed Integer.MAX_VALUE, mirroring WA Web's tolerance for
            // large numeric ids encoded as JS doubles.
            builder.mediaId((int) (long) mediaId);
        }
        wamService.commit(builder.build());
    }

    /**
     * Returns the next exception in the cause chain, breaking self-cycles.
     *
     * @param throwable the current throwable, must not be {@code null}
     * @return the cause, or {@code null} when the chain ends or self-references
     */
    private static Throwable nextCause(Throwable throwable) {
        var cause = throwable.getCause();
        return cause == throwable ? null : cause;
    }
}
