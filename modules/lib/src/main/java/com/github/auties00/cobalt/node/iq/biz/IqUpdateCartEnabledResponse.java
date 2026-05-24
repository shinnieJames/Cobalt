package com.github.auties00.cobalt.node.iq.biz;

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
 * Sealed family of inbound reply variants the relay produces in response
 * to an {@link IqUpdateCartEnabledRequest}.
 *
 * @apiNote
 * Pattern-match the returned variant to drive the commerce-settings
 * edit surface: {@link Success} echoes the post-mutation cart-enabled
 * flag, {@link ClientError} surfaces a rejected mutation and
 * {@link ServerError} surfaces a transient internal failure.
 */
@WhatsAppWebModule(moduleName = "WAWebBusinessProfileJob")
public sealed interface IqUpdateCartEnabledResponse extends IqOperation.Response
        permits IqUpdateCartEnabledResponse.Success, IqUpdateCartEnabledResponse.ClientError, IqUpdateCartEnabledResponse.ServerError {

    /**
     * Tries each variant in priority order until one matches.
     *
     * @apiNote
     * Use this entry point on every IQ stanza ack-ing a cart-enabled
     * mutation; the order is {@link Success}, then {@link ClientError},
     * then {@link ServerError}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqUpdateCartEnabledResponse> of(Node node, Node request) {
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
     * The {@code Success} variant carrying the post-mutation
     * cart-enabled flag the relay echoes inside
     * {@code <commerce_settings><cart enabled/>}.
     *
     * @apiNote
     * Use {@link #cartEnabled()} to refresh the cached
     * commerce-settings projection so the catalog-grid affordance
     * reflects the new state immediately.
     */
    final class Success implements IqUpdateCartEnabledResponse {
        /**
         * The post-mutation cart-enabled flag echoed by the relay.
         */
        private final boolean cartEnabled;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}.
         *
         * @param cartEnabled the post-mutation flag
         */
        public Success(boolean cartEnabled) {
            this.cartEnabled = cartEnabled;
        }

        /**
         * Returns the post-mutation flag.
         *
         * @apiNote
         * Use this getter to refresh the cached commerce-settings
         * projection; the value should match the requested state in
         * the absence of relay-side coercion.
         *
         * @return the flag
         */
        public boolean cartEnabled() {
            return cartEnabled;
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method validates
         * the {@code <iq type="result">} envelope and reads the
         * post-mutation flag from
         * {@code <commerce_settings><cart enabled/>}.
         *
         * @implNote
         * This implementation decodes the {@code enabled} attribute as
         * a literal {@code "true"} match, mirroring WA Web's
         * {@code commerceSettingsResponse} parser.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the success
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob",
                exports = "commerceSettingsResponse", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var enabled = node.getChild("commerce_settings")
                    .flatMap(cs -> cs.getChild("cart"))
                    .flatMap(cart -> cart.getAttributeAsString("enabled"))
                    .map("true"::equals)
                    .orElse(false);
            return Optional.of(new Success(enabled));
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
            return this.cartEnabled == that.cartEnabled;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Boolean.hashCode(cartEnabled);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqUpdateCartEnabledResponse.Success[cartEnabled=" + cartEnabled + ']';
        }
    }

    /**
     * The {@code ClientError} variant emitted when the relay rejects
     * the mutation as malformed or unauthorised.
     *
     * @apiNote
     * Use this variant to surface a user-facing 4xx-class error to the
     * commerce-settings edit surface.
     */
    final class ClientError implements IqUpdateCartEnabledResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
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
         * @apiNote
         * Use this getter to dispatch on the relay-side error code
         * when surfacing a localised message to the commerce-settings
         * edit surface.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter for logging; the text is server-localised
         * and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * to extract the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the client-error
         *         schema
         */
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
            return "IqUpdateCartEnabledResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} variant emitted when the relay returns a
     * transient internal-failure status while processing the mutation.
     *
     * @apiNote
     * Use this variant to drive a backoff-and-retry path in the
     * commerce-settings edit surface; the relay returns this shape
     * when the commerce-settings backend is temporarily unavailable.
     */
    final class ServerError implements IqUpdateCartEnabledResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
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
         * @apiNote
         * Use this getter to log the relay-side error code; a 5xx-class
         * value is the canonical retry trigger.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter for logging only; the text is server-localised
         * and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * to extract the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the server-error
         *         schema
         */
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
            return "IqUpdateCartEnabledResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
