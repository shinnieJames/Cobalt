package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;

import java.util.Objects;
import java.util.Optional;

/**
 * The typed sealed family of inbound reply variants produced by the relay in response to an {@link IqDeleteCoverPhotoRequest}.
 *
 * @apiNote
 * Use this type to switch over the three documented outcomes of a cover-photo detach: {@link Success} confirms the detach landed (the WAP parser carries an empty payload), {@link ClientError} surfaces a relay validation rejection, and {@link ServerError} reports a transport or backend failure. The dispatcher invokes {@link #of(Node, Node)} to project the raw {@link Node} into the right variant before handing it to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebBusinessProfileJob")
public sealed interface IqDeleteCoverPhotoResponse extends IqOperation.Response
        permits IqDeleteCoverPhotoResponse.Success,
        IqDeleteCoverPhotoResponse.ClientError,
        IqDeleteCoverPhotoResponse.ServerError {

    /**
     * Tries each {@link IqDeleteCoverPhotoResponse} variant in priority order.
     *
     * @apiNote
     * Call this entry from the dispatcher to fan the inbound stanza into the matching sealed variant; the success path is tried first, then the client-error envelope, then the server-error envelope. Returns empty only when none of the three documented shapes apply.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqDeleteCoverPhotoResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying an empty acknowledgement payload.
     *
     * @apiNote
     * Use this variant to confirm that the relay accepted the cover-photo detach; there is no body to read because {@code WAWebBusinessProfileJob.deleteCoverPhoto} ignores the result envelope and only checks the success flag.
     */
    final class Success implements IqDeleteCoverPhotoResponse {
        /**
         * Constructs a typed success reply.
         *
         * @apiNote
         * Call this constructor when projecting an empty {@code result} envelope into the typed model.
         */
        public Success() {
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqDeleteCoverPhotoResponse#of(Node, Node)} or directly when only the success branch is interesting; returns empty when the stanza does not carry a {@code result} envelope matching the original request.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
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
            return "IqDeleteCoverPhotoResponse.Success[]";
        }
    }

    /**
     * The {@code ClientError} reply variant surfacing a client-side rejection.
     *
     * @apiNote
     * Use this variant to react to a refused cover-photo detach; typical examples include a malformed upload id or a relay validation rejection surfaced as a SMAX error envelope.
     */
    final class ClientError implements IqDeleteCoverPhotoResponse {
        /**
         * The numeric error code lifted from the SMAX error envelope.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text lifted from the SMAX error envelope.
         */
        private final String errorText;

        /**
         * Constructs a typed client-error reply.
         *
         * @apiNote
         * Call this constructor when projecting a client-error envelope into the typed model; pass {@code null} for {@code errorText} when the wire shape omitted the text field.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable error text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to read back the SMAX error code that the relay used to classify the failure.
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
         * Use this getter to surface the relay-supplied error explanation in the UI when present.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqDeleteCoverPhotoResponse#of(Node, Node)} or directly when only the client-error branch is interesting; returns empty when the stanza does not carry a client-error envelope matching the original request.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the client-error schema
         */
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqDeleteCoverPhotoResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant surfacing a server-side failure.
     *
     * @apiNote
     * Use this variant to react to a backend failure that did not produce a typed acknowledgement; WA Web's {@code WAWebBusinessProfileJob.deleteCoverPhoto} surfaces this as a {@code ServerStatusCodeError} carrying the relay-supplied status.
     */
    final class ServerError implements IqDeleteCoverPhotoResponse {
        /**
         * The numeric error code lifted from the SMAX error envelope.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text lifted from the SMAX error envelope.
         */
        private final String errorText;

        /**
         * Constructs a typed server-error reply.
         *
         * @apiNote
         * Call this constructor when projecting a server-error envelope into the typed model; pass {@code null} for {@code errorText} when the wire shape omitted the text field.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable error text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to read back the SMAX error code that the relay used to classify the failure.
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
         * Use this getter to surface the relay-supplied error explanation in the UI when present.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqDeleteCoverPhotoResponse#of(Node, Node)} or directly when only the server-error branch is interesting; returns empty when the stanza does not carry a server-error envelope matching the original request.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the server-error schema
         */
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqDeleteCoverPhotoResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
