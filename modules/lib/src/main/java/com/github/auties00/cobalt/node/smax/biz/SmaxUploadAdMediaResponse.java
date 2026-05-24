package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxDeprecatedIqErrorResponseOptionalFromMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqErrorResponseMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound reply variants produced by the relay
 * in response to a {@link SmaxUploadAdMediaRequest}.
 *
 * @apiNote
 * Surfaced by the CTWA (click-to-WhatsApp) native-ad media linking
 * flow whose JS caller
 * {@code WAWebLinkAdMediaInFacebook.linkAdMediaInFacebook} registers
 * an already-uploaded WA media handle ({@code mediaId},
 * {@code mediaType: "image"}) against the connected Facebook ad
 * account; the three variants split the wire outcome into
 * {@link Success} (relay echoed the linked {@code (id, type)}
 * entries via a {@code <media>} and/or {@code <media_list>} tree),
 * {@link ClientError} (relay rejected the link with one of two
 * documented {@code 4xx} arms: {@code (bad-request, 400)} or
 * {@code (forbidden, 403)}) and {@link ServerError} (one of two
 * documented {@code 5xx} arms: {@code (internal-server-error, 500)}
 * or {@code (service-unavailable, 503)}). The JS caller collapses
 * any error branch into the UI literal {@code "error"}.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code WASmaxBizCtwaNativeAdUploadAdMediaRPC.sendUploadAdMediaRPC}
 * by trying each variant in priority order via {@link #of} and
 * returning the first successful parse.
 */
public sealed interface SmaxUploadAdMediaResponse extends SmaxOperation.Response
        permits SmaxUploadAdMediaResponse.Success, SmaxUploadAdMediaResponse.ClientError, SmaxUploadAdMediaResponse.ServerError {

    /**
     * Tries each {@link SmaxUploadAdMediaResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Invoked by the smax reply pump after dispatching a
     * {@link SmaxUploadAdMediaRequest}; the priority order matches
     * WA Web's {@code parsing} dispatch table so that a malformed
     * {@code Success} stanza falls through to {@link ClientError}
     * rather than masking an error.
     *
     * @implNote
     * This implementation invokes {@link Success#of(Node, Node)}
     * first, then {@link ClientError#of(Node, Node)}, then
     * {@link ServerError#of(Node, Node)}; an unrecognised stanza
     * shape returns {@link Optional#empty()}.
     *
     * @param node    the inbound IQ stanza received from the relay;
     *                never {@code null}
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched the stanza shape
     * @throws NullPointerException if either argument is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBizCtwaNativeAdUploadAdMediaRPC",
            exports = "sendUploadAdMediaRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxUploadAdMediaResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying the relay's echoed
     * media registrations.
     *
     * @apiNote
     * Projected by {@link SmaxUploadAdMediaResponse#of(Node, Node)}
     * when the relay returns the documented {@code <iq>} envelope
     * with an optional {@code <media>} child and a {@code 0..10}
     * sequence of {@code <media_list>} siblings; the JS caller
     * collapses this branch to the UI literal {@code "success"}
     * without consuming the projected fields.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseSuccess")
    final class Success implements SmaxUploadAdMediaResponse {
        /**
         * The optional primary {@code <media/>} echo carrying the
         * registered {@code (id, type)} pair, or {@code null} when
         * the relay omitted the child.
         */
        private final SmaxUploadAdMediaMediaEntry media;

        /**
         * The list of {@code <media_list/>} sibling echoes, each
         * carrying a registered {@code (id, type)} pair; admits
         * {@code 0..10} entries by the
         * {@code mapChildrenWithTag(..., 0, 10, e)} contract.
         */
        private final List<SmaxUploadAdMediaMediaEntry> mediaList;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the {@code <iq>}
         * envelope and both echo trees have been validated.
         *
         * @param media     the optional primary media echo; may be
         *                  {@code null}
         * @param mediaList the media-list echoes; never {@code null}
         * @throws NullPointerException if {@code mediaList} is
         *                              {@code null}
         */
        public Success(SmaxUploadAdMediaMediaEntry media, List<SmaxUploadAdMediaMediaEntry> mediaList) {
            Objects.requireNonNull(mediaList, "mediaList cannot be null");
            this.media = media;
            this.mediaList = List.copyOf(mediaList);
        }

        /**
         * Returns the optional primary {@code <media/>} echo.
         *
         * @return an {@link Optional} carrying the entry, or empty
         *         when the relay omitted the {@code <media/>} child
         */
        public Optional<SmaxUploadAdMediaMediaEntry> media() {
            return Optional.ofNullable(media);
        }

        /**
         * Returns the {@code <media_list/>} sibling echoes.
         *
         * @return an unmodifiable list of {@code 0..10} entries;
         *         never {@code null}
         */
        public List<SmaxUploadAdMediaMediaEntry> mediaList() {
            return mediaList;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @implNote
         * This implementation enforces the
         * {@link SmaxIqResultResponseMixin} envelope check up front,
         * then walks the optional {@code <media>} child followed by
         * the {@code <media_list>} sequence; the
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)}
         * call subsumes WA Web's
         * {@code assertTag(iq) + parseHackBaseIQResultResponseMixin}
         * prefix, while the same {@code parseEntry} helper drives
         * both echo trees (their per-child shapes are byte
         * identical aside from the tag name, which the caller
         * already filtered by tag). The hard cap of ten
         * {@code <media_list>} entries reflects the
         * {@code mapChildrenWithTag(..., 0, 10, e)} contract;
         * exceeding it rejects the parse. Cobalt's
         * {@code Node#getChild} silently picks the first matching
         * child when more than one {@code <media>} is present,
         * whereas WA Web's {@code optionalChildWithTag} rejects;
         * the relay never emits multiple {@code <media>} children so
         * this divergence is unreachable in practice.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseSuccess",
                exports = "parseUploadAdMediaResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            SmaxUploadAdMediaMediaEntry media = null;
            var mediaNode = node.getChild("media").orElse(null);
            if (mediaNode != null) {
                var parsed = parseEntry(mediaNode);
                if (parsed.isEmpty()) {
                    return Optional.empty();
                }
                media = parsed.get();
            }
            var entries = new ArrayList<SmaxUploadAdMediaMediaEntry>();
            var iter = node.streamChildren("media_list").iterator();
            while (iter.hasNext()) {
                var listNode = iter.next();
                var parsed = parseEntry(listNode);
                if (parsed.isEmpty()) {
                    return Optional.empty();
                }
                entries.add(parsed.get());
            }
            if (entries.size() > 10) {
                return Optional.empty();
            }
            return Optional.of(new Success(media, entries));
        }

        /**
         * Parses a single {@code (id, type)} entry from a
         * {@code <media/>} or {@code <media_list/>} echo node.
         *
         * @apiNote
         * Used internally by {@link #of(Node, Node)} to drive both
         * echo trees through a single byte-identical parser.
         *
         * @implNote
         * This implementation consolidates WA Web's two
         * byte-identical parsers
         * {@code parseUploadAdMediaResponseSuccessMedia} and
         * {@code parseUploadAdMediaResponseSuccessMediaList}, which
         * differ only in the asserted tag name; the tag check is
         * already enforced by the call sites
         * ({@link Node#getChild(String)} for {@code <media>} and
         * {@link Node#streamChildren(String)} for
         * {@code <media_list>}), so a redundant {@code assertTag}
         * here would be a no-op. The {@code id} attribute is
         * required as a non-empty string; the {@code type}
         * attribute is required to match the lowercase
         * {@code {"image", "video"}} dictionary via
         * {@link SmaxUploadAdMediaMediaType#of(String)}.
         *
         * @param node the {@code <media/>} or {@code <media_list/>}
         *             child node
         * @return an {@link Optional} carrying the parsed entry, or
         *         empty when either attribute is missing or
         *         malformed
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseSuccess",
                exports = "parseUploadAdMediaResponseSuccessMedia",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseSuccess",
                exports = "parseUploadAdMediaResponseSuccessMediaList",
                adaptation = WhatsAppAdaptation.ADAPTED)
        private static Optional<SmaxUploadAdMediaMediaEntry> parseEntry(Node node) {
            var id = node.getAttributeAsString("id").orElse(null);
            if (id == null) {
                return Optional.empty();
            }
            var typeStr = node.getAttributeAsString("type").orElse(null);
            if (typeStr == null) {
                return Optional.empty();
            }
            var type = SmaxUploadAdMediaMediaType.of(typeStr).orElse(null);
            if (type == null) {
                return Optional.empty();
            }
            return Optional.of(new SmaxUploadAdMediaMediaEntry(id, type));
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
            return Objects.equals(this.media, that.media)
                    && Objects.equals(this.mediaList, that.mediaList);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(media, mediaList);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxUploadAdMediaResponse.Success[media=" + media
                    + ", mediaList=" + mediaList + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant carrying one of two
     * documented {@code 4xx} native-ad rejection pairs.
     *
     * @apiNote
     * Surfaced when the relay rejected the link via either
     * {@code (text="bad-request", code=400)} from
     * {@code WASmaxInBizCtwaNativeAdIQErrorBadRequestMixin} or
     * {@code (text="forbidden", code=403)} from
     * {@code WASmaxInBizCtwaNativeAdIQErrorForbiddenMixin}; any
     * other {@code (code, text)} pair falls through the
     * disjunction and is rejected by
     * {@link #of(Node, Node)} the same way WA Web rejects it.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdNativeAdErrors")
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdIQErrorBadRequestMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdIQErrorForbiddenMixin")
    final class ClientError implements SmaxUploadAdMediaResponse {
        /**
         * The numeric server-side error code; one of {@code 400} or
         * {@code 403}.
         */
        private final int errorCode;

        /**
         * The human-readable error text; one of
         * {@code "bad-request"} or {@code "forbidden"} when the pair
         * matches a documented arm.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after one of the two
         * documented {@code (code, text)} pairs has been matched.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the
         * given inbound stanza.
         *
         * @implNote
         * This implementation runs the
         * {@link SmaxDeprecatedIqErrorResponseOptionalFromMixin}
         * envelope check first (mirroring
         * {@code parseDeprecatedIQErrorResponseOptionalFromMixin}),
         * then flattens the {@code <error/>} child via
         * {@link SmaxIqErrorResponseMixin#parseError(Node)}, and
         * finally requires the {@code (code, text)} pair to match
         * one of the two documented client-error arms via
         * {@link #matchClientErrorPair(int, String)}; non-matching
         * pairs fall through the disjunction so the smax dispatch
         * can drop to {@link ServerError}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseError",
                exports = "parseUploadAdMediaResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdNativeAdErrors",
                exports = "parseNativeAdErrors",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdDeprecatedIQErrorResponseOptionalFromMixin",
                exports = "parseDeprecatedIQErrorResponseOptionalFromMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdIQErrorBadRequestMixin",
                exports = "parseIQErrorBadRequestMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdIQErrorForbiddenMixin",
                exports = "parseIQErrorForbiddenMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            if (!SmaxDeprecatedIqErrorResponseOptionalFromMixin.validate(node, request)) {
                return Optional.empty();
            }
            var envelope = SmaxIqErrorResponseMixin.parseError(node).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return matchClientErrorPair(envelope.code(), envelope.text())
                    ? Optional.of(new ClientError(envelope.code(), envelope.text()))
                    : Optional.empty();
        }

        /**
         * Reports whether the supplied {@code (code, text)} pair
         * matches one of the two documented {@code ClientError}
         * arms enumerated by
         * {@code WASmaxInBizCtwaNativeAdNativeAdErrors.parseNativeAdErrors}.
         *
         * @apiNote
         * Used internally by {@link #of(Node, Node)} to enforce the
         * narrow {@code 4xx} disjunction; the two admitted pairs
         * are {@code ("bad-request", 400)} and
         * {@code ("forbidden", 403)}.
         *
         * @implNote
         * This implementation hard-codes the literal pairs that the
         * WA Web mixins enforce via
         * {@code literal(attrString, "text", "...")} +
         * {@code literal(attrInt, "code", ...)} so a malformed pair
         * fails the parse the same way WA Web's
         * {@code parseNativeAdErrors} propagates the last mixin
         * arm's failure.
         *
         * @param code the parsed error code
         * @param text the parsed error text; may be {@code null}
         * @return {@code true} when the pair matches one of the
         *         enumerated client-error arms; {@code false}
         *         otherwise
         */
        private static boolean matchClientErrorPair(int code, String text) {
            return ("bad-request".equals(text) && code == 400)
                    || ("forbidden".equals(text) && code == 403);
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
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
            return "SmaxUploadAdMediaResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant carrying one of two
     * documented {@code 5xx} native-ad transient failure pairs.
     *
     * @apiNote
     * Surfaced when the relay returned either
     * {@code (text="internal-server-error", code=500)} from
     * {@code WASmaxInBizCtwaNativeAdIQErrorInternalServerErrorMixin}
     * or {@code (text="service-unavailable", code=503)} from
     * {@code WASmaxInBizCtwaNativeAdIQErrorServiceUnavailableMixin};
     * the caller can re-issue the request with backoff.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdNativeAdErrors")
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdIQErrorInternalServerErrorMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdIQErrorServiceUnavailableMixin")
    final class ServerError implements SmaxUploadAdMediaResponse {
        /**
         * The numeric server-side error code; one of {@code 500} or
         * {@code 503}.
         */
        private final int errorCode;

        /**
         * The human-readable error text; one of
         * {@code "internal-server-error"} or
         * {@code "service-unavailable"} when the pair matches a
         * documented arm.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after one of the two
         * documented {@code (code, text)} pairs has been matched.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the
         * given inbound stanza.
         *
         * @implNote
         * This implementation runs the
         * {@link SmaxDeprecatedIqErrorResponseOptionalFromMixin}
         * envelope check first (mirroring
         * {@code parseDeprecatedIQErrorResponseOptionalFromMixin}),
         * then flattens the {@code <error/>} child via
         * {@link SmaxIqErrorResponseMixin#parseError(Node)}, and
         * finally requires the {@code (code, text)} pair to match
         * one of the two documented server-error arms via
         * {@link #matchServerErrorPair(int, String)}; non-matching
         * pairs fall through the disjunction and yield
         * {@link Optional#empty()}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         server-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseError",
                exports = "parseUploadAdMediaResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdNativeAdErrors",
                exports = "parseNativeAdErrors",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdDeprecatedIQErrorResponseOptionalFromMixin",
                exports = "parseDeprecatedIQErrorResponseOptionalFromMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdIQErrorInternalServerErrorMixin",
                exports = "parseIQErrorInternalServerErrorMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaNativeAdIQErrorServiceUnavailableMixin",
                exports = "parseIQErrorServiceUnavailableMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            if (!SmaxDeprecatedIqErrorResponseOptionalFromMixin.validate(node, request)) {
                return Optional.empty();
            }
            var envelope = SmaxIqErrorResponseMixin.parseError(node).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return matchServerErrorPair(envelope.code(), envelope.text())
                    ? Optional.of(new ServerError(envelope.code(), envelope.text()))
                    : Optional.empty();
        }

        /**
         * Reports whether the supplied {@code (code, text)} pair
         * matches one of the two documented {@code ServerError}
         * arms enumerated by
         * {@code WASmaxInBizCtwaNativeAdNativeAdErrors.parseNativeAdErrors}.
         *
         * @apiNote
         * Used internally by {@link #of(Node, Node)} to enforce the
         * narrow {@code 5xx} disjunction; the two admitted pairs
         * are {@code ("internal-server-error", 500)} and
         * {@code ("service-unavailable", 503)}.
         *
         * @implNote
         * This implementation hard-codes the literal pairs that the
         * WA Web mixins enforce via
         * {@code literal(attrString, "text", "...")} +
         * {@code literal(attrInt, "code", ...)}; any other
         * {@code 5xx} pair falls through the disjunction the same
         * way WA Web's {@code parseNativeAdErrors} propagates the
         * last mixin arm's failure.
         *
         * @param code the parsed error code
         * @param text the parsed error text; may be {@code null}
         * @return {@code true} when the pair matches one of the
         *         enumerated server-error arms; {@code false}
         *         otherwise
         */
        private static boolean matchServerErrorPair(int code, String text) {
            return ("internal-server-error".equals(text) && code == 500)
                    || ("service-unavailable".equals(text) && code == 503);
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
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
            return "SmaxUploadAdMediaResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
