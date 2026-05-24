package com.github.auties00.cobalt.node.iq.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound reply variants the relay produces in
 * response to an {@link IqQueryGroupInviteProfilePicRequest}.
 *
 * @apiNote
 * After dispatching the request, pattern-match the returned variant:
 * {@link Success} carries the CDN URL and direct path the caller
 * needs to fetch the avatar bytes, while {@link ClientError} and
 * {@link ServerError} surface envelope-level rejections (typically
 * {@code 401}/{@code 404} when the invite code is expired or the
 * group no longer exists, or {@code 5xx} for transient relay
 * failures).
 *
 * @implNote
 * This implementation collapses WA Web's shared
 * {@code queryGroupProfilePicParser} (used by both
 * {@code queryGroupInviteLinkProfilePic} and
 * {@code queryGroupInviteMessageProfilePic}) into a single sealed
 * sum; the WA Web parser asserts the same {@code <picture id type url direct_path/>}
 * shape regardless of which export was called.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryGroupInviteProfilePicApi")
public sealed interface IqQueryGroupInviteProfilePicResponse extends IqOperation.Response
        permits IqQueryGroupInviteProfilePicResponse.Success, IqQueryGroupInviteProfilePicResponse.ClientError, IqQueryGroupInviteProfilePicResponse.ServerError {

    /**
     * Tries each {@link IqQueryGroupInviteProfilePicResponse} variant
     * in priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Use this when dispatching through the typed {@link IqOperation}
     * pipeline; the dispatcher hands the inbound {@link Node} together
     * with the original outbound request so that each variant can
     * correlate echoed identifiers. Returns {@link Optional#empty()}
     * when none of the documented shapes match, which the caller
     * should treat as an unknown server reply and surface up.
     *
     * @implNote
     * This implementation tries {@link Success} first, then
     * {@link ClientError}, then {@link ServerError}; the order
     * matches WA Web's parser fall-through where the success branch
     * is asserted before any error envelope is inspected.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza used to validate echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
            exports = "queryGroupInviteLinkProfilePic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
            exports = "queryGroupInviteMessageProfilePic",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqQueryGroupInviteProfilePicResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant.
     *
     * @apiNote
     * Carries the {@code <picture>} grandchild's identifier, MIME
     * type, CDN URL and direct path. The {@link #url()} is the
     * fully-qualified avatar URL the caller should follow to fetch
     * the bytes, while the {@link #pictureId()} can be cached and
     * passed back as
     * {@link IqQueryGroupInviteProfilePicRequest#pictureId()} on
     * subsequent fetches to short-circuit when nothing has changed.
     *
     * @implNote
     * This implementation projects WA Web's shared
     * {@code queryGroupProfilePicParser} return shape
     * ({@code {id, type, url, direct_path}}) into typed accessors,
     * keeping the WA Web {@code direct_path} attribute name as the
     * camel-cased {@link #directPath()} accessor.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryGroupInviteProfilePicApi")
    final class Success implements IqQueryGroupInviteProfilePicResponse {
        /**
         * The picture identifier.
         */
        private final String pictureId;

        /**
         * The picture MIME type.
         */
        private final String pictureType;

        /**
         * The CDN URL.
         */
        private final String url;

        /**
         * The direct path on the CDN.
         */
        private final String directPath;

        /**
         * Constructs a {@link Success} reply.
         *
         * @param pictureId   the picture identifier; never {@code null}
         * @param pictureType the picture MIME type; never {@code null}
         * @param url         the CDN URL; never {@code null}
         * @param directPath  the direct path on the CDN; never {@code null}
         * @throws NullPointerException if any argument is {@code null}
         */
        public Success(String pictureId, String pictureType, String url, String directPath) {
            this.pictureId = Objects.requireNonNull(pictureId, "pictureId cannot be null");
            this.pictureType = Objects.requireNonNull(pictureType, "pictureType cannot be null");
            this.url = Objects.requireNonNull(url, "url cannot be null");
            this.directPath = Objects.requireNonNull(directPath, "directPath cannot be null");
        }

        /**
         * Returns the picture identifier.
         *
         * @return the picture id; never {@code null}
         */
        public String pictureId() {
            return pictureId;
        }

        /**
         * Returns the picture MIME type.
         *
         * @return the MIME type; never {@code null}
         */
        public String pictureType() {
            return pictureType;
        }

        /**
         * Returns the CDN URL.
         *
         * @return the URL; never {@code null}
         */
        public String url() {
            return url;
        }

        /**
         * Returns the direct path on the CDN.
         *
         * @return the path; never {@code null}
         */
        public String directPath() {
            return directPath;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqQueryGroupInviteProfilePicResponse#of(Node, Node)};
         * this factory is exposed so callers can short-circuit when
         * they already know the wire shape is a success.
         *
         * @implNote
         * This implementation first runs
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)} to
         * confirm the envelope is an IQ {@code result} matching the
         * request id, then mirrors WA Web's
         * {@code queryGroupProfilePicParser}: reads the {@code <picture>}
         * child and extracts its {@code id}, {@code type}, {@code url}
         * and {@code direct_path} attributes. Any missing attribute
         * yields {@link Optional#empty()}, matching the parser's
         * {@code attrString} (non-optional) contract that WA Web
         * implements by throwing.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
                exports = "queryGroupInviteLinkProfilePic",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
                exports = "queryGroupInviteMessageProfilePic",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var picture = node.getChild("picture").orElse(null);
            if (picture == null) {
                return Optional.empty();
            }
            var id = picture.getAttributeAsString("id").orElse(null);
            var type = picture.getAttributeAsString("type").orElse(null);
            var url = picture.getAttributeAsString("url").orElse(null);
            var directPath = picture.getAttributeAsString("direct_path").orElse(null);
            if (id == null || type == null || url == null || directPath == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(id, type, url, directPath));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return Objects.equals(this.pictureId, that.pictureId)
                    && Objects.equals(this.pictureType, that.pictureType)
                    && Objects.equals(this.url, that.url)
                    && Objects.equals(this.directPath, that.directPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pictureId, pictureType, url, directPath);
        }

        @Override
        public String toString() {
            return "IqQueryGroupInviteProfilePicResponse.Success[pictureId=" + pictureId
                    + ", pictureType=" + pictureType
                    + ", url=" + url
                    + ", directPath=" + directPath + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant.
     *
     * @apiNote
     * Surfaces caller-side rejections of the avatar fetch: typically
     * {@code 401} or {@code 404} when the invite code has been
     * revoked or the group no longer exists, or {@code 403} when the
     * caller is barred from the group. Retries are not meaningful
     * without first fetching a fresh invite link.
     *
     * @implNote
     * This implementation corresponds to the {@code 4xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code queryGroupInviteLinkProfilePic} /
     * {@code queryGroupInviteMessageProfilePic}; the {@code <error>}
     * envelope's {@code code} and {@code text} attributes feed
     * {@link #errorCode()} and {@link #errorText()}.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryGroupInviteProfilePicApi")
    final class ClientError implements IqQueryGroupInviteProfilePicResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a {@link ClientError} reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqQueryGroupInviteProfilePicResponse#of(Node, Node)};
         * this factory is exposed so callers can short-circuit when
         * they already know the wire shape is a client error.
         *
         * @implNote
         * This implementation delegates to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * to validate the {@code type="error"} envelope and the
         * {@code <error>} child's {@code 4xx} {@code code} before
         * extracting code/text.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
                exports = "queryGroupInviteLinkProfilePic",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
                exports = "queryGroupInviteMessageProfilePic",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqQueryGroupInviteProfilePicResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     *
     * @apiNote
     * Surfaces transient {@code 5xx} relay failures while fetching
     * the group avatar; the request may be retried after a backoff.
     *
     * @implNote
     * This implementation corresponds to the {@code 5xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code queryGroupInviteLinkProfilePic} /
     * {@code queryGroupInviteMessageProfilePic}.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryGroupInviteProfilePicApi")
    final class ServerError implements IqQueryGroupInviteProfilePicResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a {@link ServerError} reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqQueryGroupInviteProfilePicResponse#of(Node, Node)};
         * this factory is exposed so callers can short-circuit when
         * they already know the wire shape is a server error.
         *
         * @implNote
         * This implementation delegates to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * to validate the {@code type="error"} envelope and the
         * {@code <error>} child's {@code 5xx} {@code code} before
         * extracting code/text.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
                exports = "queryGroupInviteLinkProfilePic",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
                exports = "queryGroupInviteMessageProfilePic",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqQueryGroupInviteProfilePicResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
