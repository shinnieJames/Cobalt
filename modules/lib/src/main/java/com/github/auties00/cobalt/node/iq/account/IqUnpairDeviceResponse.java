package com.github.auties00.cobalt.node.iq.account;

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
 * {@link IqUnpairDeviceRequest}.
 *
 * @apiNote
 * The WA Web {@code unpairResponse} parser collapses every reply to a {@code {status}}
 * record, conflating the success path with relay errors; Cobalt preserves the distinction by
 * splitting the reply into {@link Success}, {@link ClientError}, and {@link ServerError}
 * variants so callers can react differently to a stale device handle versus a transient
 * server failure.
 */
@WhatsAppWebModule(moduleName = "WAWebUnpairDeviceJob")
public sealed interface IqUnpairDeviceResponse extends IqOperation.Response
        permits IqUnpairDeviceResponse.Success, IqUnpairDeviceResponse.ClientError, IqUnpairDeviceResponse.ServerError {

    /**
     * Tries each {@link IqUnpairDeviceResponse} variant in priority order and returns the
     * first that parses cleanly.
     *
     * @apiNote
     * Called by the legacy-IQ dispatcher after the inbound {@code <iq>} stanza is matched
     * against the outbound request by id; the priority order ({@link Success},
     * {@link ClientError}, {@link ServerError}) mirrors the order WA Web's
     * {@code WADeprecatedWapParser} tries: a {@code type="result"} envelope wins, a
     * {@code type="error"} with a {@code <error code/>} child in the {@code 4xx} range maps
     * to {@link ClientError}, and otherwise to {@link ServerError}.
     *
     * @implNote
     * This implementation never returns a parsed variant blindly; each candidate validates
     * the echoed id against {@code request} via {@link SmaxIqResultResponseMixin} or
     * {@link SmaxBaseServerErrorMixin}, so a reply mis-routed by the dispatcher surfaces as
     * {@link Optional#empty()} rather than a silently-wrong variant.
     *
     * @param node    the inbound IQ stanza received from the relay
     * @param request the original outbound stanza, used to validate echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()}
     *         when no documented variant matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
            exports = "unpairDevice", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqUnpairDeviceResponse> of(Node node, Node request) {
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
     * Reply variant signalling that the relay accepted the unpair request.
     *
     * @apiNote
     * Carries no payload; WA Web's {@code unpairResponse} parser collapses this to
     * {@code {status: 200}}.
     */
    @WhatsAppWebModule(moduleName = "WAWebUnpairDeviceJob")
    final class Success implements IqUnpairDeviceResponse {
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
        @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
                exports = "unpairResponse", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqUnpairDeviceResponse.Success[]";
        }
    }

    /**
     * Reply variant signalling that the relay rejected the unpair request as malformed,
     * unauthorised, or referencing an unknown device.
     *
     * @apiNote
     * Maps to the {@code 4xx} branch of WA Web's {@code unpairResponse} parser, which reads
     * the {@code <error code/>} child of a {@code type="error"} envelope.
     */
    @WhatsAppWebModule(moduleName = "WAWebUnpairDeviceJob")
    final class ClientError implements IqUnpairDeviceResponse {
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
        @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
                exports = "unpairResponse", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqUnpairDeviceResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Reply variant signalling that the relay encountered a transient internal failure while
     * processing the unpair request.
     *
     * @apiNote
     * Maps to the {@code 5xx} branch of WA Web's {@code unpairResponse} parser; callers may
     * retry the same request after a backoff once the socket has settled.
     */
    @WhatsAppWebModule(moduleName = "WAWebUnpairDeviceJob")
    final class ServerError implements IqUnpairDeviceResponse {
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
        @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
                exports = "unpairResponse", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqUnpairDeviceResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
