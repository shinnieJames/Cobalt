package com.github.auties00.cobalt.node.iq.debug;

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
 * {@link IqDebugGdprRequest}.
 *
 * @apiNote
 * Switch on the returned variant to discriminate the relay outcome: a
 * {@link Success} echoes the post-cancel GDPR status (typically the
 * {@code "none"} string with a zeroed expiration), a {@link ClientError} surfaces
 * relay rejections (commonly {@code 404} when no GDPR request is in flight), and a
 * {@link ServerError} surfaces transient relay failures the caller may retry.
 *
 * @implNote
 * This implementation mirrors WA Web's GDPR status wap-parser
 * ({@code useWAWebGdprStatus.GdprStatusWapParser}) which projects the same trio of
 * outcomes, plus the standard SMAX server-error envelope.
 */
@WhatsAppWebModule(moduleName = "WAWebGdprHookUtils")
public sealed interface IqDebugGdprResponse extends IqOperation.Response
        permits IqDebugGdprResponse.Success, IqDebugGdprResponse.ClientError, IqDebugGdprResponse.ServerError {

    /**
     * Parses the inbound stanza into the first matching {@link IqDebugGdprResponse}
     * variant.
     *
     * @apiNote
     * Try this once per inbound reply, in any order; the priority ordering
     * (success, then client-error, then server-error) matches the wire shape and
     * never returns ambiguous matches.
     *
     * @implNote
     * This implementation calls each variant's {@code of(node, request)} in turn and
     * returns the first present result.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no
     *         documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebDebugGDPR",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqDebugGdprResponse> of(Node node, Node request) {
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
     * Success variant. The relay accepted the cancel request and echoed the
     * post-cancel GDPR status.
     *
     * @apiNote
     * Inspect {@link #status()} for the textual status (typically {@code "none"}
     * after a successful cancel) and {@link #expirationSeconds()} for the
     * relay-echoed expiration timestamp (typically {@code 0} after cancel).
     */
    @WhatsAppWebModule(moduleName = "WAWebDebugGDPR")
    final class Success implements IqDebugGdprResponse {
        /**
         * Holds the relay-echoed GDPR status string, or {@code null} when the
         * relay omitted the {@code <gdpr>} child entirely.
         */
        private final String status;

        /**
         * Holds the relay-echoed expiration timestamp in seconds since epoch, or
         * {@code null} when the relay omitted the attribute.
         */
        private final Long expirationSeconds;

        /**
         * Constructs a successful reply carrying the relay-echoed fields.
         *
         * @apiNote
         * Both fields are nullable because the relay may return either a bare
         * acknowledgement or a {@code <gdpr>} child with arbitrary attribute
         * coverage.
         *
         * @param status            the optional status string; may be {@code null}
         * @param expirationSeconds the optional expiration timestamp; may be
         *                          {@code null}
         */
        public Success(String status, Long expirationSeconds) {
            this.status = status;
            this.expirationSeconds = expirationSeconds;
        }

        /**
         * Returns the optional relay-echoed GDPR status string.
         *
         * @return an {@link Optional} carrying the status (typically
         *         {@code "none"} after cancel), or empty when omitted
         */
        public Optional<String> status() {
            return Optional.ofNullable(status);
        }

        /**
         * Returns the optional relay-echoed expiration timestamp in seconds.
         *
         * @return an {@link Optional} carrying the timestamp, or empty when
         *         omitted
         */
        public Optional<Long> expirationSeconds() {
            return Optional.ofNullable(expirationSeconds);
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant when it
         * matches the success schema.
         *
         * @apiNote
         * Returns empty when the SMAX result-envelope check fails; never
         * inspects the {@code <gdpr>} child's attribute set beyond the optional
         * {@code status} and {@code expiration}.
         *
         * @implNote
         * This implementation accepts a missing {@code <gdpr>} child by
         * returning a {@link Success} with both fields {@code null}, matching
         * the relay's bare-acknowledgement shape.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebDebugGDPR",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var gdpr = node.getChild("gdpr").orElse(null);
            if (gdpr == null) {
                return Optional.of(new Success(null, null));
            }
            var status = gdpr.getAttributeAsString("status").orElse(null);
            var expirationAttr = gdpr.getAttributeAsLong("expiration");
            var expirationSeconds = expirationAttr.isPresent()
                    ? expirationAttr.getAsLong()
                    : null;
            return Optional.of(new Success(status, expirationSeconds));
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
            return Objects.equals(this.status, that.status)
                    && Objects.equals(this.expirationSeconds, that.expirationSeconds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, expirationSeconds);
        }

        @Override
        public String toString() {
            return "IqDebugGdprResponse.Success[status=" + status
                    + ", expirationSeconds=" + expirationSeconds + ']';
        }
    }

    /**
     * Client-error variant. The relay rejected the cancel request with a {@code 4xx}
     * code.
     *
     * @apiNote
     * The dominant client-error is {@code 404} when no GDPR request is in flight for
     * the bound report type; the request payload itself is well-formed so a different
     * code typically signals an authorisation issue.
     */
    @WhatsAppWebModule(moduleName = "WAWebDebugGDPR")
    final class ClientError implements IqDebugGdprResponse {
        /**
         * Holds the numeric server-side error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text echoed by the relay, or
         * {@code null} when omitted.
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
         * @return an {@link Optional} carrying the text, or empty when the relay
         *         omitted it
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
        @WhatsAppWebExport(moduleName = "WAWebDebugGDPR",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqDebugGdprResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Server-error variant. The relay encountered a transient {@code 5xx} failure
     * processing the cancel request.
     *
     * @apiNote
     * Typically retryable after a short backoff; the relay surfaces no
     * machine-readable backoff hint on this stanza, so the caller's standard
     * retry policy applies.
     */
    @WhatsAppWebModule(moduleName = "WAWebDebugGDPR")
    final class ServerError implements IqDebugGdprResponse {
        /**
         * Holds the numeric server-side error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text echoed by the relay, or
         * {@code null} when omitted.
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
         * @return an {@link Optional} carrying the text, or empty when the relay
         *         omitted it
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
        @WhatsAppWebExport(moduleName = "WAWebDebugGDPR",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqDebugGdprResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
