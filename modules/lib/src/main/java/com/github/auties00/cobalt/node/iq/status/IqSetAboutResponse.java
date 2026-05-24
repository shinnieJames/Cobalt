package com.github.auties00.cobalt.node.iq.status;

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
 * {@link IqSetAboutRequest}.
 *
 * @apiNote
 * WA Web's {@code aboutResponse} parser collapses success to {@code {status: 200}} and
 * failure to {@code {status: errorCode}}; Cobalt splits the reply into {@link Success},
 * {@link ClientError}, and {@link ServerError} variants so the about-edit surface can
 * distinguish a length-cap rejection ({@link ClientError} with code {@code 406}) from a
 * transient relay failure ({@link ServerError}).
 */
public sealed interface IqSetAboutResponse extends IqOperation.Response
        permits IqSetAboutResponse.Success, IqSetAboutResponse.ClientError, IqSetAboutResponse.ServerError {

    /**
     * Tries each {@link IqSetAboutResponse} variant in priority order and returns the first
     * that parses cleanly.
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
    @WhatsAppWebExport(moduleName = "WAWebSetAboutJob",
            exports = "setAbout", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqSetAboutResponse> of(Node node, Node request) {
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
     * Reply variant carrying the integer revision identifier the relay assigned to the new
     * about-text version.
     *
     * @apiNote
     * The about-id is monotonically increasing; the persisted-job runtime stores it
     * alongside the local about cache so subsequent edits can be ordered against
     * concurrently-arriving server-pushed about-text updates.
     */
    @WhatsAppWebModule(moduleName = "WAWebSetAboutJob")
    final class Success implements IqSetAboutResponse {
        /**
         * Relay-assigned about-text revision identifier, read from the {@code <iq id/>}
         * attribute of the success envelope.
         */
        private final long aboutId;

        /**
         * Constructs a successful reply.
         *
         * @param aboutId the relay-assigned revision identifier
         */
        public Success(long aboutId) {
            this.aboutId = aboutId;
        }

        /**
         * Returns the relay-assigned about-text revision identifier.
         *
         * @return the about-id
         */
        public long aboutId() {
            return aboutId;
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope is missing the integer
         * {@code id} attribute the relay echoes back; the parser does not synthesise an id
         * because the caller would lose ordering invariants.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebSetAboutJob",
                exports = "aboutResponse",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var idAttr = node.getAttributeAsLong("id");
            if (idAttr.isEmpty()) {
                return Optional.empty();
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
            return this.aboutId == that.aboutId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(aboutId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqSetAboutResponse.Success[aboutId=" + aboutId + ']';
        }
    }

    /**
     * Reply variant signalling that the relay rejected the about-text update.
     *
     * @apiNote
     * Maps to the {@code 4xx} branch of WA Web's reply pipeline; typical codes are
     * {@code 406} when the about-text exceeds the relay-side length cap and {@code 400}
     * when the payload contains invalid Unicode.
     */
    @WhatsAppWebModule(moduleName = "WAWebSetAboutJob")
    final class ClientError implements IqSetAboutResponse {
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
        @WhatsAppWebExport(moduleName = "WAWebSetAboutJob",
                exports = "setAbout", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqSetAboutResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Reply variant signalling a transient server-side failure on an about-text update.
     *
     * @apiNote
     * Maps to the {@code 5xx} branch of WA Web's reply pipeline; the persisted-job runtime
     * retries the same update on the next socket reconnect.
     */
    @WhatsAppWebModule(moduleName = "WAWebSetAboutJob")
    final class ServerError implements IqSetAboutResponse {
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
        @WhatsAppWebExport(moduleName = "WAWebSetAboutJob",
                exports = "setAbout", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqSetAboutResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
