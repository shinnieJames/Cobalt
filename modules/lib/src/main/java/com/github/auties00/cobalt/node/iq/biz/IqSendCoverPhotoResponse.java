package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants the relay produces in response
 * to an {@link IqSendCoverPhotoRequest}.
 *
 * @apiNote
 * Pattern-match the returned variant to drive the cover-photo edit
 * surface: {@link Success} carries no payload, {@link ClientError}
 * surfaces a rejected mutation and {@link ServerError} surfaces a
 * transient internal failure.
 */
@WhatsAppWebModule(moduleName = "WAWebBusinessProfileJob")
public sealed interface IqSendCoverPhotoResponse extends IqOperation.Response
        permits IqSendCoverPhotoResponse.Success, IqSendCoverPhotoResponse.ClientError, IqSendCoverPhotoResponse.ServerError {

    /**
     * Tries each variant in priority order until one matches.
     *
     * @apiNote
     * Use this entry point on every IQ stanza ack-ing a cover-photo
     * upload; the order is {@link Success}, then {@link ClientError},
     * then {@link ServerError}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqSendCoverPhotoResponse> of(Node node, Node request) {
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
     * The {@code Success} variant carrying no payload.
     *
     * @apiNote
     * The relay echoes a bare {@code <iq type="result"/>} envelope when
     * the cover-photo update lands; the cover photo itself is then
     * surfaced via the next business-profile fetch.
     */
    final class Success implements IqSendCoverPhotoResponse {
        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * success variant has no payload to carry.
         */
        public Success() {
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method validates
         * the {@code <iq type="result">} envelope and returns the
         * empty success.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the success
         *         schema
         */
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
            return "IqSendCoverPhotoResponse.Success[]";
        }
    }

    /**
     * The {@code ClientError} variant emitted when the relay rejects
     * the mutation as malformed or referencing an unknown upload.
     *
     * @apiNote
     * Use this variant to surface a user-facing 4xx-class error to the
     * cover-photo edit surface; the relay returns this shape when the
     * upload artefact has expired or the token does not match.
     */
    final class ClientError implements IqSendCoverPhotoResponse {
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
         * when surfacing a localised message to the cover-photo edit
         * surface.
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
            return "IqSendCoverPhotoResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} variant emitted when the relay returns a
     * transient internal-failure status while processing the mutation.
     *
     * @apiNote
     * Use this variant to drive a backoff-and-retry path in the
     * cover-photo edit surface; the relay returns this shape when the
     * business-profile backend is temporarily unavailable.
     */
    final class ServerError implements IqSendCoverPhotoResponse {
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
            return "IqSendCoverPhotoResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
