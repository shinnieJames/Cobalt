package com.github.auties00.cobalt.node.iq.tos;

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
 * {@link IqUpdateTosRequest}.
 *
 * @apiNote
 * Switch on the returned variant to discriminate the relay outcome: a {@link Success}
 * is a bare acknowledgement (WA Web uses a no-op parser, the relay echoes no
 * payload), a {@link ClientError} surfaces a relay rejection, and a
 * {@link ServerError} surfaces a transient relay failure WA Web retries via
 * exponential backoff.
 */
public sealed interface IqUpdateTosResponse extends IqOperation.Response
        permits IqUpdateTosResponse.Success, IqUpdateTosResponse.ClientError, IqUpdateTosResponse.ServerError {

    /**
     * Parses the inbound stanza into the first matching {@link IqUpdateTosResponse}
     * variant.
     *
     * @apiNote
     * Try this once per inbound reply; the priority ordering (success, then
     * client-error, then server-error) matches the wire shape and never returns
     * ambiguous matches.
     *
     * @implNote
     * This implementation calls each variant's {@code of(node, request)} in turn
     * and returns the first present result.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no
     *         documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "updateTosState", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqUpdateTosResponse> of(Node node, Node request) {
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
     * Success variant. The relay recorded the acknowledgement and echoed only the
     * IQ envelope.
     *
     * @apiNote
     * Carries no payload; the caller treats a present {@link Success} as
     * confirmation that the server has recorded the bound notice ids as accepted.
     */
    @WhatsAppWebModule(moduleName = "WAWebTosJob")
    final class Success implements IqUpdateTosResponse {
        /**
         * Constructs a successful reply.
         */
        public Success() {
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant when it
         * matches the success schema.
         *
         * @apiNote
         * Returns empty when the SMAX result-envelope check fails; never reads
         * past the envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebTosJob",
                exports = "updateTosState", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        @Override
        public String toString() {
            return "IqUpdateTosResponse.Success[]";
        }
    }

    /**
     * Client-error variant. The relay rejected the acknowledgement with a
     * {@code 4xx} code.
     *
     * @apiNote
     * Typically signals a malformed envelope or an unauthorised caller; WA Web's
     * {@code TosManager.maybeUpdateServer} treats any non-500 code as fatal
     * (stops retrying).
     */
    @WhatsAppWebModule(moduleName = "WAWebTosJob")
    final class ClientError implements IqUpdateTosResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply carrying the relay-echoed envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ClientError} variant when it
         * matches the standard SMAX client-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebTosJob",
                exports = "updateTosState", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqUpdateTosResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Server-error variant. The relay encountered a transient internal failure
     * processing the acknowledgement.
     *
     * @apiNote
     * WA Web's {@code TosManager.maybeUpdateServer} specifically retries the
     * {@code 500} arm via exponential backoff (max five retries, {@code 1s -> 16s}
     * base); replicate this if a tight retry policy is required.
     */
    @WhatsAppWebModule(moduleName = "WAWebTosJob")
    final class ServerError implements IqUpdateTosResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply carrying the relay-echoed envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ServerError} variant when it
         * matches the standard SMAX server-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebTosJob",
                exports = "updateTosState", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqUpdateTosResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
