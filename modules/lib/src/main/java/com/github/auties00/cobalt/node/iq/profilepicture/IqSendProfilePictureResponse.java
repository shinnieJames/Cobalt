package com.github.auties00.cobalt.node.iq.profilepicture;

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
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqSendProfilePictureRequest}.
 *
 * @apiNote
 * WA Web's {@code photoResponseParser} returns either {@code {id}} on success or rejects
 * the whole flow with a {@code ServerStatusCodeError}; Cobalt instead routes failures to
 * {@link ClientError} and {@link ServerError} so the photo-thumb cache can decide whether
 * to surface a "couldn't update" toast (transient) or revert the optimistic UI update
 * (permanent).
 */
public sealed interface IqSendProfilePictureResponse extends IqOperation.Response
        permits IqSendProfilePictureResponse.Success, IqSendProfilePictureResponse.ClientError, IqSendProfilePictureResponse.ServerError {

    /**
     * Tries each {@link IqSendProfilePictureResponse} variant in priority order and returns
     * the first that parses cleanly.
     *
     * @apiNote
     * The priority order ({@link Success}, {@link ClientError}, {@link ServerError}) mirrors
     * the order WA Web's reply parser tries.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound stanza
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()}
     *         when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqSendProfilePictureResponse> of(Node node, Node request) {
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
     * Reply variant signalling that the relay accepted the picture set or clear.
     *
     * @apiNote
     * Carries the relay-assigned picture id when a picture was uploaded; the id is empty
     * when the request cleared the existing picture (no {@code <picture/>} grandchild on
     * the reply).
     */
    @WhatsAppWebModule(moduleName = "WAWebSendProfilePictureJob")
    final class Success implements IqSendProfilePictureResponse {
        /**
         * Relay-assigned picture identifier, or {@code null} when the picture was cleared.
         */
        private final Long pictureId;

        /**
         * Constructs a successful reply.
         *
         * @param pictureId the relay-assigned picture identifier, or {@code null} when the
         *                  picture was cleared
         */
        public Success(Long pictureId) {
            this.pictureId = pictureId;
        }

        /**
         * Returns the relay-assigned picture identifier.
         *
         * @return an {@link Optional} carrying the picture id, or {@link Optional#empty()}
         *         when the picture was cleared
         */
        public Optional<Long> pictureId() {
            return Optional.ofNullable(pictureId);
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the envelope is a
         * {@code type="result"} echoing the {@code request} id; the {@code <picture id/>}
         * grandchild is optional and its absence signals a clear-picture confirmation.
         *
         * @implNote
         * This implementation mirrors WA Web's {@code photoResponseParser}:
         * {@code e.hasChild("picture")} populates the id from {@code attrInt("id")}, and an
         * absent {@code <picture/>} child collapses to {@code {id: null}}; a present
         * {@code <picture/>} with no {@code id} attribute also folds to a {@code null} id
         * rather than failing the parse.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob",
                exports = "photoResponseParser",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var pictureChild = node.getChild("picture").orElse(null);
            if (pictureChild == null) {
                return Optional.of(new Success(null));
            }
            var idAttr = pictureChild.getAttributeAsLong("id");
            if (idAttr.isEmpty()) {
                return Optional.of(new Success(null));
            }
            return Optional.of(new Success(idAttr.getAsLong()));
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
            return Objects.equals(this.pictureId, that.pictureId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(pictureId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqSendProfilePictureResponse.Success[pictureId=" + pictureId + ']';
        }
    }

    /**
     * Reply variant signalling that the relay rejected the picture set or clear.
     *
     * @apiNote
     * Maps to the {@code 4xx} branch of WA Web's reply pipeline; typical codes are
     * {@code 403} when the caller is not an admin of the target group, and {@code 406} when
     * the JPEG bytes fail relay-side format validation.
     */
    @WhatsAppWebModule(moduleName = "WAWebSendProfilePictureJob")
    final class ClientError implements IqSendProfilePictureResponse {
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
        @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob",
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
            return "IqSendProfilePictureResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Reply variant signalling a transient server-side failure on a picture set or clear.
     *
     * @apiNote
     * Maps to the {@code 5xx} branch of WA Web's reply pipeline; callers may retry the same
     * request after a short backoff once the socket has settled.
     */
    @WhatsAppWebModule(moduleName = "WAWebSendProfilePictureJob")
    final class ServerError implements IqSendProfilePictureResponse {
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
        @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob",
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
            return "IqSendProfilePictureResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
