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
 */
@WhatsAppWebModule(moduleName = "WAWebWamMediaMetricUtils")
public final class MediaMetricUtils {
    /**
     * Detail-message prefix produced by Cobalt's {@code MediaDownloadInputStream}
     * when the downloaded ciphertext SHA-256 disagrees with the expected
     * {@code encFilehash}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHttpErrors", exports = "MmsDownloadFilehashMismatchError",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final String CIPHERTEXT_HASH_MISMATCH_MESSAGE =
            "Ciphertext SHA256 hash doesn't match the expected value";

    /**
     * Detail-message prefix produced by Cobalt's {@code MediaDownloadInputStream}
     * when the decrypted plaintext SHA-256 disagrees with the expected
     * {@code fileSha256}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMiscErrors", exports = "MediaDecryptionError",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMiscErrors", exports = "PLAINTEXT_HASH_MISMATCH_ERROR",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final String PLAINTEXT_HASH_MISMATCH_MESSAGE =
            "Plaintext SHA256 hash doesn't match the expected value";

    /**
     * Prevents instantiation of this utility class.
     */
    private MediaMetricUtils() {
        throw new AssertionError("MediaMetricUtils is a static-only utility");
    }

    /**
     * Returns whether the given web-media-type string represents a
     * thumbnail variant.
     *
     * <p>WA Web's local helper {@code u} is not exported but is referenced
     * by both {@link #getMetricOverallDownloadModeType(String, String, boolean)}
     * and {@link #getMetricOverallUploadModeType(String)} to short-circuit
     * to the thumbnail mode-type bucket.
     *
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
     * Maps the triple {@code (webMediaType, downloadReason, isPrefetched)}
     * to a {@link MediaDownloadModeType}.
     *
     * <p>Thumbnail variants always collapse to
     * {@link MediaDownloadModeType#THUMBNAIL}. Otherwise an explicit
     * {@code "manual"} download reason wins, then a truthy
     * {@code isPrefetched} flag, with {@link MediaDownloadModeType#FULL}
     * as the fallback.
     *
     * @param webMediaType   the {@code WAWebMmsMediaTypes.MEDIA_TYPES}
     *                       string describing the media being downloaded
     * @param downloadReason the {@code "manual"} or non-{@code "manual"}
     *                       reason passed by the caller
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
     * <p>Thumbnail variants collapse to {@link MediaUploadModeType#THUMBNAIL}
     * and everything else maps to {@link MediaUploadModeType#REGULAR}. This
     * is the coarsest of the upload-mode classifiers, with finer-grained
     * values like {@code FAST_FORWARD_EXIST_CHECK} or {@code WEB_REUPLOAD}
     * decided at the call site.
     *
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
     * mirrors the JavaScript {@code switch} cascade verbatim, including the
     * cases where multiple input strings collapse to the same WAM enum
     * (for example {@code "image"}, {@code "waffle-image"},
     * {@code "thumbnail-image"} and {@code "newsletter-image"} all map to
     * {@link MediaType#PHOTO}).
     *
     * <p>Unrecognised values raise {@link IllegalArgumentException} with
     * the same {@code "webMediaType is invalid: <value>"} message format
     * used by WA Web's {@code err()} helper.
     *
     * @param webMediaType the {@code WAWebMmsMediaTypes.MEDIA_TYPES} string
     *                     to translate, must not be {@code null}
     * @return the matching {@link MediaType} value
     * @throws IllegalArgumentException when {@code webMediaType} does not
     *     correspond to any known {@code WAWebMmsMediaTypes.MEDIA_TYPES}
     *     value
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
     * <p>The lookup mirrors WA Web's {@code instanceof} cascade. Order is
     * load-bearing because WA Web's error classes are not disjoint: for
     * example {@code MMSThrottleError} extends {@code HttpStatusCodeError(507)}
     * and {@code MediaTooLargeError} extends {@code HttpStatusCodeError(413)},
     * so the throttle and too-large branches must be examined before the
     * generic {@code status >= 500} branch. Cobalt collapses the six
     * {@code WAWebMmsClientErrors} subclasses into
     * {@link WhatsAppMediaException.Upload} carrying an optional HTTP
     * status code, so this classifier reads the status code first and
     * dispatches in the same priority order. {@code AbortError} is mapped
     * from {@link InterruptedException} and {@code NoMediaHostsError} from
     * {@link WhatsAppMediaException.Connection}.
     *
     * @param throwable the error that aborted the upload, or {@code null}
     * @return the matching {@link MediaUploadResultType}, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricUploadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaUploadResultType getMetricUploadErrorResultType(Throwable throwable) {
        for (var current = throwable; current != null; current = nextCause(current)) {
            if (current instanceof InterruptedException) {
                return MediaUploadResultType.ERROR_CANCEL;
            }
            if (current instanceof WhatsAppMediaException.Connection) {
                return MediaUploadResultType.ERROR_MEDIA_CONN;
            }
            if (current instanceof WhatsAppMediaException.Upload upload) {
                var status = upload.httpStatusCode();
                if (status.isPresent()) {
                    var code = status.getAsInt();
                    return switch (code) {
                        case 401 -> MediaUploadResultType.ERROR_NO_PERMISSIONS;
                        case 413 -> MediaUploadResultType.ERROR_BAD_MEDIA;
                        case 507 -> MediaUploadResultType.ERROR_THROTTLE;
                        default -> {
                            if (code >= 500) {
                                yield MediaUploadResultType.ERROR_SERVER;
                            }
                            yield MediaUploadResultType.ERROR_UNKNOWN;
                        }
                    };
                }
                return MediaUploadResultType.ERROR_UPLOAD;
            }
        }
        return MediaUploadResultType.ERROR_UNKNOWN;
    }

    /**
     * Classifies a thrown download error into a {@link MediaDownloadResultType}.
     *
     * <p>The cascade matches WA Web verbatim: throttle detection wins over
     * generic status-code handling because {@code MMSThrottleError} extends
     * {@code HttpStatusCodeError(507)}, media-host failures map to
     * {@link MediaDownloadResultType#ERROR_MEDIA_CONN}, generic network
     * failures (no status) to {@link MediaDownloadResultType#ERROR_NETWORK},
     * then the explicit status-code switch (404, 410, 416, 401, 429, 507),
     * and finally the cancel and hash-mismatch tail branches.
     *
     * <p>The hash-mismatch branches detect Cobalt's
     * {@link WhatsAppMediaException.Download} variants raised by
     * {@code MediaDownloadInputStream}. A ciphertext SHA-256 mismatch (the
     * Cobalt analogue of {@code MmsDownloadFilehashMismatchError}) maps to
     * {@link MediaDownloadResultType#ERROR_ENC_HASH_MISMATCH}, and a
     * plaintext SHA-256 mismatch (the Cobalt analogue of
     * {@code MediaDecryptionError} carrying
     * {@code PLAINTEXT_HASH_MISMATCH_ERROR}) maps to
     * {@link MediaDownloadResultType#ERROR_HASH_MISMATCH}.
     *
     * @param throwable the error raised by the download path, or
     *                  {@code null}
     * @return the matching {@link MediaDownloadResultType}, never
     *         {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricDownloadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaDownloadResultType getMetricDownloadErrorResultType(Throwable throwable) {
        for (var current = throwable; current != null; current = nextCause(current)) {
            // Checked first because InterruptedException may be wrapped under any media exception.
            if (current instanceof InterruptedException) {
                return MediaDownloadResultType.ERROR_CANCEL;
            }
            if (current instanceof WhatsAppMediaException.Connection) {
                return MediaDownloadResultType.ERROR_MEDIA_CONN;
            }
            if (current instanceof WhatsAppMediaException.Download download) {
                var status = download.httpStatusCode();
                if (status.isPresent()) {
                    var code = status.getAsInt();
                    return switch (code) {
                        case 429, 507 -> MediaDownloadResultType.ERROR_THROTTLE;
                        case 404, 410 -> MediaDownloadResultType.ERROR_TOO_OLD;
                        case 416 -> MediaDownloadResultType.ERROR_CANNOT_RESUME;
                        case 401 -> MediaDownloadResultType.ERROR_INVALID_URL;
                        default -> MediaDownloadResultType.ERROR_UNKNOWN;
                    };
                }
                // Hash-mismatch variants surface as Download with a known message prefix.
                var message = download.getMessage();
                if (message != null) {
                    if (message.startsWith(CIPHERTEXT_HASH_MISMATCH_MESSAGE)) {
                        return MediaDownloadResultType.ERROR_ENC_HASH_MISMATCH;
                    }
                    if (message.startsWith(PLAINTEXT_HASH_MISMATCH_MESSAGE)) {
                        return MediaDownloadResultType.ERROR_HASH_MISMATCH;
                    }
                }
                return MediaDownloadResultType.ERROR_NETWORK;
            }
            // Plaintext mismatch can also surface as Processing when raised outside the streaming download path.
            if (current instanceof WhatsAppMediaException.Processing processing) {
                var message = processing.getMessage();
                if (message != null && message.startsWith(PLAINTEXT_HASH_MISMATCH_MESSAGE)) {
                    return MediaDownloadResultType.ERROR_HASH_MISMATCH;
                }
            }
        }
        return MediaDownloadResultType.ERROR_UNKNOWN;
    }

    /**
     * Returns the HTTP status code carried by the given throwable, walking
     * the cause chain to find the first {@link WhatsAppMediaException} that
     * has a status code attached.
     * @param throwable the error to inspect, or {@code null}
     * @return the HTTP status code, or {@code null} if none of the
     *         exceptions in the chain carries one
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
     * {@code WebcMediaErrorUnknownDetailsEvent} so the server can
     * deduplicate retried attempts. The range matches JavaScript's
     * {@code Number.MAX_SAFE_INTEGER} (2<sup>53</sup>&minus;1 =
     * {@code 9007199254740991}) exactly.
     * @return a random {@code long} in the closed range
     *         {@code [1, 9007199254740991]}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "generateMediaEventId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static long generateMediaEventId() {
        return 1L + ThreadLocalRandom.current().nextLong(9007199254740991L);
    }

    /**
     * Maps a media direct-path string to the matching
     * {@link BackendStoreType} by inspecting the first two characters of
     * the path.
     *
     * <p>WA Web treats the leading {@code /v}, {@code /o} and {@code /m}
     * markers as the canonical signals for Everstore, OIL and Manifold
     * blob stores respectively. Any other prefix is unrecognised. A
     * {@code null} or empty path means the asset is not using a
     * direct-path scheme and maps to {@link BackendStoreType#NON_DIRECT_PATH}.
     * @param directPath the {@code direct_path} string handed back by the
     *                   media-upload server, or {@code null}
     * @return the matching {@link BackendStoreType}, or {@code null} when
     *         the prefix is unrecognised
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricBackendStore",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static BackendStoreType getMetricBackendStore(String directPath) {
        if (directPath == null || directPath.isEmpty()) {
            return BackendStoreType.NON_DIRECT_PATH;
        }
        // WA Web's t.slice(0,2) accepts strings shorter than two characters; Java's substring would throw, so cap at len.
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
     * Emits a one-shot {@code WebcMediaErrorUnknownDetailsWamEvent} when
     * the outer {@link MediaUploadResultType} or
     * {@link MediaDownloadResultType} is {@code ERROR_UNKNOWN}, so the
     * server can correlate the unclassified error with its underlying
     * class name and message.
     *
     * <p>The event is committed only when an unknown overall result is
     * present and {@code throwable} is non-{@code null}. The download
     * result takes priority over the upload result, matching WA Web's
     * conditional that sets the operation to {@code DOWNLOAD} first and
     * only overrides to {@code UPLOAD} when the download branch did not
     * match.
     *
     * @param wamService     the WAM service used to commit the event when
     *                       triggered, must not be {@code null}
     * @param mediaId        the media-event identifier, propagated as
     *                       {@code mediaId}, or {@code null} to omit the
     *                       field
     * @param downloadResult the overall download result, or {@code null}
     *                       when this call site is upload-only
     * @param uploadResult   the overall upload result, or {@code null}
     *                       when this call site is download-only
     * @param throwable      the original error that caused the unknown
     *                       classification, or {@code null} to skip
     *                       emission
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
                .webcMediaErrorName(throwable.getClass().getSimpleName())
                .webcMediaErrorMessage(throwable.getMessage());
        if (mediaId != null) {
            // mediaId is a JS double in [1, MAX_SAFE_INTEGER] but the WAM property is INTEGER, so callers overflowing Integer must narrow.
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
