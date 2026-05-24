package com.github.auties00.cobalt.node.iq.dirty;

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
 * {@link IqClearDirtyBitsRequest}.
 *
 * @apiNote
 * WA Web's {@code cleanDirtyReplyParser} only asserts {@code type="result"} and logs
 * failures to the warn channel without surfacing them; Cobalt instead splits the reply into
 * {@link Success}, {@link ClientError}, and {@link ServerError} variants so the dirty-bit
 * driver can decide whether the resource should be marked as not-yet-cleared (transient
 * failure) or whether the entire dirty-bit pass should be aborted (permanent failure).
 */
@WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
public sealed interface IqClearDirtyBitsResponse extends IqOperation.Response
        permits IqClearDirtyBitsResponse.Success, IqClearDirtyBitsResponse.ClientError, IqClearDirtyBitsResponse.ServerError {

    /**
     * Tries each {@link IqClearDirtyBitsResponse} variant in priority order and returns the
     * first that parses cleanly.
     *
     * @apiNote
     * The priority order ({@link Success}, {@link ClientError}, {@link ServerError}) mirrors
     * the order WA Web's {@code WADeprecatedWapParser} tries; only one variant ever
     * populates because {@link SmaxIqResultResponseMixin} and
     * {@link SmaxBaseServerErrorMixin} match disjoint stanza shapes.
     *
     * @param node    the inbound IQ stanza received from the relay
     * @param request the original outbound stanza, used to validate echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()}
     *         when no documented variant matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob",
            exports = "clearDirtyBits", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqClearDirtyBitsResponse> of(Node node, Node request) {
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
     * Reply variant signalling that the relay accepted the dirty-bit clear.
     *
     * @apiNote
     * Carries no payload; WA Web's {@code cleanDirtyReplyParser} reduces this to
     * {@code {}}.
     */
    @WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
    final class Success implements IqClearDirtyBitsResponse {
        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * The reply variant is empty by construction; the constructor takes no arguments
         * because the success envelope carries no payload beyond the echoed id.
         */
        public Success() {
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the stanza is a {@code type="result"}
         * envelope echoing the {@code request} id, mirroring WA Web's
         * {@code e.assertAttr("type","result")} guard.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob",
                exports = "cleanDirtyReplyParser", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqClearDirtyBitsResponse.Success[]";
        }
    }

    /**
     * Reply variant signalling that the relay rejected the dirty-bit clear as malformed or
     * unauthorised.
     *
     * @apiNote
     * Maps to the {@code 4xx} branch of the dirty-bit reply pipeline; the dirty-bit driver
     * should treat the resource as still dirty (because the relay-side marker was not
     * cleared) but should not retry the same request because the failure is structural.
     */
    @WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
    final class ClientError implements IqClearDirtyBitsResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text, or {@code null} when omitted
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
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         the relay omitted it
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
        @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob",
                exports = "clearDirtyBits", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqClearDirtyBitsResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Reply variant signalling that the relay encountered a transient internal failure while
     * processing the dirty-bit clear.
     *
     * @apiNote
     * Maps to the {@code 5xx} branch of the dirty-bit reply pipeline; the dirty-bit driver
     * may retry the same request on the next dirty-bit pass since the marker is still set
     * server-side.
     */
    @WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
    final class ServerError implements IqClearDirtyBitsResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text, or {@code null} when omitted
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
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         the relay omitted it
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
        @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob",
                exports = "clearDirtyBits", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqClearDirtyBitsResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
