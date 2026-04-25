package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

/**
 * Enumerates the outcome types reported by WAM telemetry for a WhatsApp media
 * upload attempt.
 *
 * <p>Each constant carries the fixed integer identifier transmitted on the
 * wire and identifies either a successful upload, a duplicate-detection fast
 * path, a skipped endpoint-preference ("EP") optimization, or a specific
 * failure category (request-level, transport-level, storage-level, or
 * server-rejection). Values must never be renumbered or reused.
 *
 * @implNote WAWebWamEnumMediaUploadResultType: the module default-exports a
 *     single frozen object {@code MEDIA_UPLOAD_RESULT_TYPE} whose keys are the
 *     outcome names and whose values are the integer identifiers; Cobalt
 *     mirrors the full enumeration with {@link WamEnumConstant} preserving
 *     each numeric value.
 */
@WamEnum
@WhatsAppWebModule(moduleName = "WAWebWamEnumMediaUploadResultType")
public enum MediaUploadResultType {
    /**
     * Upload completed successfully.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code OK = 1}.
     */
    @WamEnumConstant(1)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    OK,

    /**
     * The media was recognized as a server-side duplicate of an
     * already-uploaded blob; no new bytes were transferred.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code DUPLICATE = 3}.
     */
    @WamEnumConstant(3)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DUPLICATE,

    /**
     * Upload failed for an unspecified or unclassified reason.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_UNKNOWN = 2}.
     */
    @WamEnumConstant(2)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_UNKNOWN,

    /**
     * Upload failed because the preparation of the upload request itself
     * failed (request construction, signing, or metadata assembly).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_REQUEST = 4}.
     */
    @WamEnumConstant(4)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_REQUEST,

    /**
     * Upload failed at the transfer stage (the request reached the server but
     * the body-upload step itself did not complete).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_UPLOAD = 5}.
     */
    @WamEnumConstant(5)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_UPLOAD,

    /**
     * Upload failed because the client ran out of memory while buffering or
     * processing the media.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_OOM = 6}.
     */
    @WamEnumConstant(6)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_OOM,

    /**
     * Upload failed because of a local I/O error while reading the source
     * file from disk.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_IO = 7}.
     */
    @WamEnumConstant(7)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_IO,

    /**
     * Upload failed because the client lacked the filesystem or system
     * permissions required to access the source media.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_NO_PERMISSIONS = 8}.
     */
    @WamEnumConstant(8)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_NO_PERMISSIONS,

    /**
     * Upload failed because the source media was detected as malformed or
     * otherwise invalid before transfer.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_BAD_MEDIA = 9}.
     */
    @WamEnumConstant(9)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_BAD_MEDIA,

    /**
     * Upload failed because the device did not have enough free storage to
     * stage or encrypt the media before upload.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_INSUFFICIENT_SPACE = 10}.
     */
    @WamEnumConstant(10)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_INSUFFICIENT_SPACE,

    /**
     * Upload failed because the source file was not found at the expected
     * path (file-not-found).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_FNF = 11}.
     */
    @WamEnumConstant(11)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_FNF,

    /**
     * Upload was cancelled, typically because the user abandoned the send or
     * the chat containing the upload was closed.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_CANCEL = 12}.
     */
    @WamEnumConstant(12)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_CANCEL,

    /**
     * Upload failed because the media-upload server returned an error
     * response.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_SERVER = 13}.
     */
    @WamEnumConstant(13)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_SERVER,

    /**
     * Upload failed because the HTTP request exceeded its request-level
     * timeout.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_REQUEST_TIMEOUT = 14}.
     */
    @WamEnumConstant(14)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_REQUEST_TIMEOUT,

    /**
     * Upload failed because the upload was never finalized on the server
     * (the client did not receive or did not send the finalize step).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_NOT_FINALIZED = 15}.
     */
    @WamEnumConstant(15)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_NOT_FINALIZED,

    /**
     * Upload failed during the optimistic-hash check, where the client
     * probes the server with the media hash to skip transfer when possible.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_OPTIMISTIC_HASH = 16}.
     */
    @WamEnumConstant(16)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_OPTIMISTIC_HASH,

    /**
     * Upload failed because the client could not obtain or refresh a media
     * connection ({@code media-conn}) from the server.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_MEDIA_CONN = 17}.
     */
    @WamEnumConstant(17)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_MEDIA_CONN,

    /**
     * Upload failed because DNS resolution of the media-upload host failed.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_DNS = 18}.
     */
    @WamEnumConstant(18)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_DNS,

    /**
     * Upload failed because the client was throttled by the server and the
     * attempt was not retried within the allowed window.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_THROTTLE = 19}.
     */
    @WamEnumConstant(19)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_THROTTLE,

    /**
     * Upload failed during the TLS/SSL handshake or because of a
     * transport-level SSL error mid-stream.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_SSL = 20}.
     */
    @WamEnumConstant(20)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_SSL,

    /**
     * Upload failed because the device reported no available client network
     * (airplane mode, no connectivity) at the time of the attempt.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_NO_CLIENT_NETWORK = 21}.
     */
    @WamEnumConstant(21)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_NO_CLIENT_NETWORK,

    /**
     * The endpoint-preference optimization was skipped because the targeted
     * recipient endpoint was not online.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code SKIPPED_EP_NOT_ONLINE = 22}.
     */
    @WamEnumConstant(22)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SKIPPED_EP_NOT_ONLINE,

    /**
     * The endpoint-preference optimization was skipped because the chat was
     * not a one-to-one chat (the EP path only applies to 1:1 chats).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code SKIPPED_EP_NOT_1TO1CHAT = 23}.
     */
    @WamEnumConstant(23)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SKIPPED_EP_NOT_1TO1CHAT,

    /**
     * The endpoint-preference optimization was skipped because a prior
     * upload for this media had already failed.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code SKIPPED_EP_UPLOAD_FAILED = 24}.
     */
    @WamEnumConstant(24)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SKIPPED_EP_UPLOAD_FAILED,

    /**
     * The endpoint-preference optimization was skipped because the message
     * was being sent to multiple chats (multi-chat fan-out is not eligible
     * for the EP path).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code SKIPPED_EP_MULTI_CHAT = 25}.
     */
    @WamEnumConstant(25)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SKIPPED_EP_MULTI_CHAT,

    /**
     * The endpoint-preference optimization was skipped because no primary
     * host was available for the targeted endpoint.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code SKIPPED_EP_NO_PRIMARY_HOST = 26}.
     */
    @WamEnumConstant(26)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SKIPPED_EP_NO_PRIMARY_HOST,

    /**
     * Upload failed because of a Cronet transport-layer error (on clients
     * using the Cronet HTTP stack).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_CRONET = 27}.
     */
    @WamEnumConstant(27)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_CRONET,

    /**
     * Upload failed because the server returned a response that terminated
     * prematurely or was missing required fields.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_INCOMPLETE_SERVER_RESPONSE = 28}.
     */
    @WamEnumConstant(28)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_INCOMPLETE_SERVER_RESPONSE,

    /**
     * Upload failed because transcoding the source media to the upload
     * format failed.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_TRANSCODING = 29}.
     */
    @WamEnumConstant(29)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_TRANSCODING,

    /**
     * Upload was cancelled programmatically by the client (distinguished
     * from {@link #ERROR_CANCEL} which is caused by user action).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_CANCEL_PROGRAMMATIC = 30}.
     */
    @WamEnumConstant(30)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_CANCEL_PROGRAMMATIC,

    /**
     * Upload failed because no network route was available to reach the
     * media-upload host.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_NO_ROUTE = 31}.
     */
    @WamEnumConstant(31)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_NO_ROUTE,

    /**
     * Upload failed because the source media exceeded the maximum allowed
     * size for its media type.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_TOO_LARGE = 32}.
     */
    @WamEnumConstant(32)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_TOO_LARGE,

    /**
     * Upload failed because the client could not transcode the source media
     * at all (no compatible encoder available).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_CANNOT_TRANSCODE = 33}.
     */
    @WamEnumConstant(33)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_CANNOT_TRANSCODE,

    /**
     * Upload failed because the source media had no detectable mime-type.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_UNKNOWN_MIMETYPE = 34}.
     */
    @WamEnumConstant(34)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_UNKNOWN_MIMETYPE,

    /**
     * Upload failed because the source media's mime-type was detected but
     * is not supported for upload.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_UNSUPPORTED_MIMETYPE = 35}.
     */
    @WamEnumConstant(35)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_UNSUPPORTED_MIMETYPE,

    /**
     * Upload completed but the server explicitly rejected the uploaded
     * media (for example, for policy or content reasons).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_SERVER_REJECTED_MEDIA = 36}.
     */
    @WamEnumConstant(36)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_SERVER_REJECTED_MEDIA,

    /**
     * Upload failed because of an I/O error during the media-encryption
     * step (reading plaintext or writing ciphertext).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_IO_ENCRYPTION = 37}.
     */
    @WamEnumConstant(37)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_IO_ENCRYPTION,

    /**
     * Upload failed because the client could not find or initialize a
     * suitable encryption algorithm for the media.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_NO_ENCRYPTION_ALGORITHM = 38}.
     */
    @WamEnumConstant(38)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_NO_ENCRYPTION_ALGORITHM,

    /**
     * Upload failed because the server instructed the client to switch to
     * a different host and the switch was not completed before the attempt
     * expired.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_HOST_SWITCH_REQUIRED = 39}.
     */
    @WamEnumConstant(39)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_HOST_SWITCH_REQUIRED,

    /**
     * Upload failed because of an internal WAMSys error (the native WAM
     * subsystem on mobile clients reported a failure).
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_WAMSYS = 40}.
     */
    @WamEnumConstant(40)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_WAMSYS,

    /**
     * Upload failed because the upload URL handed back by the server was
     * malformed or otherwise unusable.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code ERROR_INVALID_URL = 41}.
     */
    @WamEnumConstant(41)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ERROR_INVALID_URL,

    /**
     * Upload completed but the post-upload integrity check (hash / length /
     * metadata verification against the server-side blob) failed.
     *
     * @implNote WAWebWamEnumMediaUploadResultType.MEDIA_UPLOAD_RESULT_TYPE: {@code INTEGRITY_CHECK_FAILURE = 42}.
     */
    @WamEnumConstant(42)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaUploadResultType",
            exports = "MEDIA_UPLOAD_RESULT_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INTEGRITY_CHECK_FAILURE
}
