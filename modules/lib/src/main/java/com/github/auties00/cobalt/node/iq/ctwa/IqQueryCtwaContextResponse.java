package com.github.auties00.cobalt.node.iq.ctwa;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.ctwa.BusinessCtwaMediaType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqQueryCtwaContextRequest}.
 *
 * @apiNote
 * WA Web's {@code ctwaContext} parser throws a generic {@code "invalid response"} error when
 * it encounters any failure path; Cobalt instead routes failures to {@link ClientError} and
 * {@link ServerError} so the chat composer can decide whether to fall back to a degraded
 * preview (transient) or hide the ad context entirely (permanent).
 */
@WhatsAppWebModule(moduleName = "WAWebQueryCtwaContextJob")
public sealed interface IqQueryCtwaContextResponse extends IqOperation.Response
        permits IqQueryCtwaContextResponse.Success, IqQueryCtwaContextResponse.ClientError, IqQueryCtwaContextResponse.ServerError {

    /**
     * Tries each {@link IqQueryCtwaContextResponse} variant in priority order and returns
     * the first that parses cleanly.
     *
     * @apiNote
     * The priority order ({@link Success}, {@link ClientError}, {@link ServerError}) mirrors
     * WA Web's {@code ctwaContext} parser: a {@code type="result"} envelope with a
     * {@code <context>} child wins, otherwise the {@code <error code/>} branch is split by
     * code range.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound stanza
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()}
     *         when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryCtwaContextJob",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqQueryCtwaContextResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * Reply variant carrying the projected {@code <context>} grandchild for a successful
     * CTWA-context lookup.
     *
     * @apiNote
     * The mandatory fields ({@code sourceUrl}, {@code sourceId}, {@code sourceType}) always
     * populate; the optional fields populate when the ad creative included them, and the
     * WAMO automated-greeting-message fields ({@code greetingMessageBody},
     * {@code automatedGreetingMessageShown}, {@code ctaPayload}, {@code originalImageUrl})
     * populate only when {@code WAWebCtwaAGMUtils.isWamoAGMIntegrationEnabled(sourceApp)} is
     * true on the relay side.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryCtwaContextJob")
    final class Success implements IqQueryCtwaContextResponse {
        /**
         * Mandatory {@code <source>}/{@code <url>} content carrying the ad's landing URL.
         */
        private final String sourceUrl;

        /**
         * Mandatory {@code <source>}/{@code <id>} content carrying the ad creative id.
         */
        private final String sourceId;

        /**
         * Mandatory {@code <source>}/{@code <type>} content tagging the ad source surface.
         */
        private final String sourceType;

        /**
         * Optional {@code <headline>} content rendered as the chat preview title.
         */
        private final String title;

        /**
         * Optional {@code <body>} content rendered as the chat preview description.
         */
        private final String description;

        /**
         * Optional {@code <thumbnail>}/{@code <url>} content for an externally-hosted
         * thumbnail.
         */
        private final String thumbnailUrl;

        /**
         * Optional inline {@code <thumbnail>}/{@code <bytes>} content; WA Web base64-encodes
         * these for the JS bridge, Cobalt surfaces the raw bytes.
         */
        private final byte[] thumbnailBytes;

        /**
         * Optional {@code <video>}/{@code <url>} content when the ad creative is a video.
         */
        private final String mediaUrl;

        /**
         * Media classifier derived from the response shape, mirroring WA Web's
         * {@code ContextInfo$ExternalAdReplyInfo$MediaType} mapping.
         *
         * @apiNote
         * {@link BusinessCtwaMediaType#VIDEO} when the response carried a {@code <video>}
         * grandchild, {@link BusinessCtwaMediaType#IMAGE} when it carried only a thumbnail,
         * and {@code null} when the response had no thumbnail at all.
         */
        private final BusinessCtwaMediaType mediaType;

        /**
         * Optional {@code <sourceApp>} content tagging the host platform of the ad
         * (typically {@code "instagram"} or {@code "facebook"}); also gates the WAMO-AGM
         * fields server-side.
         */
        private final String sourceApp;

        /**
         * Optional WAMO-AGM {@code <greetingMessageBody>} content carrying the prefilled
         * greeting the relay wants the composer to render.
         */
        private final String greetingMessageBody;

        /**
         * Optional WAMO-AGM {@code <automatedGreetingMessageShown>} flag.
         *
         * @apiNote
         * Relay encodes it as the literal string {@code "true"} or {@code "false"} which
         * this class parses into a tri-state {@link Boolean} ({@code null} when absent).
         */
        private final Boolean automatedGreetingMessageShown;

        /**
         * Optional WAMO-AGM {@code <ctaPayload>} content carrying the call-to-action payload
         * the composer should attach to the first outbound message.
         */
        private final String ctaPayload;

        /**
         * Optional WAMO-AGM {@code <originalImageUrl>} content carrying the un-cropped
         * source image URL.
         */
        private final String originalImageUrl;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Defensively clones {@code thumbnailBytes}; the other byte-free fields are
         * primitives or immutable strings and are stored directly.
         *
         * @param sourceUrl                     the source URL
         * @param sourceId                      the source id
         * @param sourceType                    the source type
         * @param title                         the optional title, or {@code null}
         * @param description                   the optional description, or {@code null}
         * @param thumbnailUrl                  the optional thumbnail URL, or {@code null}
         * @param thumbnailBytes                the optional inline thumbnail bytes, or
         *                                      {@code null}
         * @param mediaUrl                      the optional video URL, or {@code null}
         * @param mediaType                     the media classifier, or {@code null} when no
         *                                      thumbnail was shipped
         * @param sourceApp                     the optional source app, or {@code null}
         * @param greetingMessageBody           the optional WAMO-AGM greeting, or
         *                                      {@code null}
         * @param automatedGreetingMessageShown the optional WAMO-AGM shown flag, or
         *                                      {@code null}
         * @param ctaPayload                    the optional WAMO-AGM CTA payload, or
         *                                      {@code null}
         * @param originalImageUrl              the optional WAMO-AGM original image URL, or
         *                                      {@code null}
         * @throws NullPointerException if {@code sourceUrl}, {@code sourceId}, or
         *                              {@code sourceType} is {@code null}
         */
        public Success(String sourceUrl, String sourceId, String sourceType, String title,
                       String description, String thumbnailUrl, byte[] thumbnailBytes,
                       String mediaUrl, BusinessCtwaMediaType mediaType, String sourceApp,
                       String greetingMessageBody, Boolean automatedGreetingMessageShown,
                       String ctaPayload, String originalImageUrl) {
            this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl cannot be null");
            this.sourceId = Objects.requireNonNull(sourceId, "sourceId cannot be null");
            this.sourceType = Objects.requireNonNull(sourceType, "sourceType cannot be null");
            this.title = title;
            this.description = description;
            this.thumbnailUrl = thumbnailUrl;
            this.thumbnailBytes = thumbnailBytes == null ? null : thumbnailBytes.clone();
            this.mediaUrl = mediaUrl;
            this.mediaType = mediaType;
            this.sourceApp = sourceApp;
            this.greetingMessageBody = greetingMessageBody;
            this.automatedGreetingMessageShown = automatedGreetingMessageShown;
            this.ctaPayload = ctaPayload;
            this.originalImageUrl = originalImageUrl;
        }

        /**
         * Returns the source URL.
         *
         * @return the URL, never {@code null}
         */
        public String sourceUrl() {
            return sourceUrl;
        }

        /**
         * Returns the source identifier.
         *
         * @return the id, never {@code null}
         */
        public String sourceId() {
            return sourceId;
        }

        /**
         * Returns the source type.
         *
         * @return the type, never {@code null}
         */
        public String sourceType() {
            return sourceType;
        }

        /**
         * Returns the optional title.
         *
         * @return an {@link Optional} carrying the title, or {@link Optional#empty()}
         */
        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        /**
         * Returns the optional description.
         *
         * @return an {@link Optional} carrying the description, or {@link Optional#empty()}
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * Returns the optional thumbnail URL.
         *
         * @return an {@link Optional} carrying the URL, or {@link Optional#empty()}
         */
        public Optional<String> thumbnailUrl() {
            return Optional.ofNullable(thumbnailUrl);
        }

        /**
         * Returns the optional inline thumbnail bytes.
         *
         * @apiNote
         * Returns a defensive copy; callers may mutate the array without affecting subsequent
         * reads.
         *
         * @return an {@link Optional} carrying a clone of the bytes, or
         *         {@link Optional#empty()}
         */
        public Optional<byte[]> thumbnailBytes() {
            return Optional.ofNullable(thumbnailBytes).map(byte[]::clone);
        }

        /**
         * Returns the optional video URL.
         *
         * @return an {@link Optional} carrying the URL, or {@link Optional#empty()}
         */
        public Optional<String> mediaUrl() {
            return Optional.ofNullable(mediaUrl);
        }

        /**
         * Returns the optional media classifier.
         *
         * @return an {@link Optional} carrying the type, or {@link Optional#empty()} when
         *         no thumbnail was shipped
         */
        public Optional<BusinessCtwaMediaType> mediaType() {
            return Optional.ofNullable(mediaType);
        }

        /**
         * Returns the optional source-app identifier.
         *
         * @return an {@link Optional} carrying the identifier, or {@link Optional#empty()}
         */
        public Optional<String> sourceApp() {
            return Optional.ofNullable(sourceApp);
        }

        /**
         * Returns the optional WAMO-AGM greeting body.
         *
         * @return an {@link Optional} carrying the greeting, or {@link Optional#empty()}
         */
        public Optional<String> greetingMessageBody() {
            return Optional.ofNullable(greetingMessageBody);
        }

        /**
         * Returns the optional WAMO-AGM shown flag.
         *
         * @return an {@link Optional} carrying the flag, or {@link Optional#empty()}
         */
        public Optional<Boolean> automatedGreetingMessageShown() {
            return Optional.ofNullable(automatedGreetingMessageShown);
        }

        /**
         * Returns the optional WAMO-AGM CTA payload.
         *
         * @return an {@link Optional} carrying the payload, or {@link Optional#empty()}
         */
        public Optional<String> ctaPayload() {
            return Optional.ofNullable(ctaPayload);
        }

        /**
         * Returns the optional WAMO-AGM original image URL.
         *
         * @return an {@link Optional} carrying the URL, or {@link Optional#empty()}
         */
        public Optional<String> originalImageUrl() {
            return Optional.ofNullable(originalImageUrl);
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * The parse only succeeds when the envelope echoes the {@code request} id, carries a
         * {@code <context>} child with a {@code <source>} grandchild, and that
         * {@code <source>} grandchild populates {@code <url>}, {@code <id>}, and
         * {@code <type>}; anything else returns {@link Optional#empty()} so the caller can
         * fall through to the error variants.
         *
         * @implNote
         * This implementation deviates from WA Web's parser, which throws
         * {@code "invalid response"} as soon as it sees a {@code <context>} child carrying
         * an {@code <error>} grandchild. Cobalt does not throw on that shape; it instead
         * lets {@link ClientError#of(Node, Node)} pick the envelope up via the standard
         * {@code <error/>} child mechanism.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryCtwaContextJob",
                exports = "ctwaContext",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var context = node.getChild("context").orElse(null);
            if (context == null) {
                return Optional.empty();
            }
            var source = context.getChild("source").orElse(null);
            if (source == null) {
                return Optional.empty();
            }
            var sourceUrl = source.getChild("url")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            var sourceId = source.getChild("id")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            var sourceType = source.getChild("type")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            if (sourceUrl == null || sourceId == null || sourceType == null) {
                return Optional.empty();
            }
            var title = context.getChild("headline")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            var description = context.getChild("body")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            String thumbnailUrl = null;
            byte[] thumbnailBytes = null;
            BusinessCtwaMediaType mediaType = null;
            String mediaUrl = null;
            var thumbnail = context.getChild("thumbnail").orElse(null);
            if (thumbnail != null) {
                thumbnailUrl = thumbnail.getChild("url")
                        .flatMap(Node::toContentString)
                        .orElse(null);
                thumbnailBytes = thumbnail.getChild("bytes")
                        .flatMap(Node::toContentBytes)
                        .orElse(null);
                var video = context.getChild("video").orElse(null);
                if (video != null) {
                    mediaUrl = video.getChild("url")
                            .flatMap(Node::toContentString)
                            .orElse(null);
                    mediaType = BusinessCtwaMediaType.VIDEO;
                } else {
                    mediaType = BusinessCtwaMediaType.IMAGE;
                }
            }
            var sourceApp = context.getChild("sourceApp")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            var greetingMessageBody = context.getChild("greetingMessageBody")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            Boolean automatedGreetingMessageShown = null;
            var automatedShown = context.getChild("automatedGreetingMessageShown")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            if (automatedShown != null) {
                automatedGreetingMessageShown = "true".equals(automatedShown);
            }
            var ctaPayload = context.getChild("ctaPayload")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            var originalImageUrl = context.getChild("originalImageUrl")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            return Optional.of(new Success(sourceUrl, sourceId, sourceType, title, description,
                    thumbnailUrl, thumbnailBytes, mediaUrl, mediaType, sourceApp,
                    greetingMessageBody, automatedGreetingMessageShown, ctaPayload,
                    originalImageUrl));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return Objects.equals(this.sourceUrl, that.sourceUrl)
                    && Objects.equals(this.sourceId, that.sourceId)
                    && Objects.equals(this.sourceType, that.sourceType)
                    && Objects.equals(this.title, that.title)
                    && Objects.equals(this.description, that.description)
                    && Objects.equals(this.thumbnailUrl, that.thumbnailUrl)
                    && Arrays.equals(this.thumbnailBytes, that.thumbnailBytes)
                    && Objects.equals(this.mediaUrl, that.mediaUrl)
                    && this.mediaType == that.mediaType
                    && Objects.equals(this.sourceApp, that.sourceApp)
                    && Objects.equals(this.greetingMessageBody, that.greetingMessageBody)
                    && Objects.equals(this.automatedGreetingMessageShown, that.automatedGreetingMessageShown)
                    && Objects.equals(this.ctaPayload, that.ctaPayload)
                    && Objects.equals(this.originalImageUrl, that.originalImageUrl);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(sourceUrl, sourceId, sourceType, title, description,
                    thumbnailUrl, Arrays.hashCode(thumbnailBytes), mediaUrl, mediaType,
                    sourceApp, greetingMessageBody, automatedGreetingMessageShown,
                    ctaPayload, originalImageUrl);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            var thumbnailBytesLength = thumbnailBytes == null ? -1 : thumbnailBytes.length;
            return "IqQueryCtwaContextResponse.Success[sourceUrl=" + sourceUrl
                    + ", sourceId=" + sourceId
                    + ", sourceType=" + sourceType
                    + ", title=" + title
                    + ", description=" + description
                    + ", thumbnailUrl=" + thumbnailUrl
                    + ", thumbnailBytesLength=" + thumbnailBytesLength
                    + ", mediaUrl=" + mediaUrl
                    + ", mediaType=" + mediaType
                    + ", sourceApp=" + sourceApp
                    + ", greetingMessageBody=" + greetingMessageBody
                    + ", automatedGreetingMessageShown=" + automatedGreetingMessageShown
                    + ", ctaPayload=" + ctaPayload
                    + ", originalImageUrl=" + originalImageUrl + ']';
        }
    }

    /**
     * Reply variant signalling that the relay rejected the CTWA-context lookup.
     *
     * @apiNote
     * Maps both to the {@code 4xx} branch of WA Web's reply pipeline and to the inline
     * {@code <error/>} child WA Web's {@code ctwaContext} parser flags as
     * {@code "invalid response"}; in either case the ad context cannot be rendered and the
     * composer should hide the CTWA banner.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryCtwaContextJob")
    final class ClientError implements IqQueryCtwaContextResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional text, or {@code null} when omitted
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the stanza is a {@code type="error"}
         * envelope echoing the {@code request} id and carrying a {@code <error/>} child whose
         * {@code code} attribute falls in the {@code 4xx} range, per the parsing contract of
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the client-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryCtwaContextJob",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ClientError) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryCtwaContextResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Reply variant signalling a transient server-side failure on a CTWA-context lookup.
     *
     * @apiNote
     * Maps to the {@code 5xx} branch of WA Web's reply pipeline; callers may retry the same
     * lookup after a backoff or fall back to a degraded composer state.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryCtwaContextJob")
    final class ServerError implements IqQueryCtwaContextResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional text, or {@code null} when omitted
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the stanza is a {@code type="error"}
         * envelope echoing the {@code request} id and carrying a {@code <error/>} child whose
         * {@code code} attribute falls outside the {@code 4xx} range, per the parsing
         * contract of {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the server-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryCtwaContextJob",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ServerError) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryCtwaContextResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
