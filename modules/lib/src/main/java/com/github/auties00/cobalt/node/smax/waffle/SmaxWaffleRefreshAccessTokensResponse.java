package com.github.auties00.cobalt.node.smax.waffle;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound replies to a
 * {@link SmaxWaffleRefreshAccessTokensRequest}.
 *
 * @apiNote
 * Mirrors WA Web's three documented {@code RefreshAccessTokens} reply
 * shapes: a {@link Success} carrying a fresh
 * {@link SmaxWaffleRsaEncryptionMetadata} subtree (the embedder
 * decrypts it to recover the rotated access tokens via the WA Web
 * counterpart of {@code WAWebAPIParser.parseRSAEncryptionMetadataMixin}
 * and {@code decryptRSAEncryptedPayload}), a {@link ClientError} for
 * malformed or unauthorised requests, and a {@link ServerError} for
 * transient relay failures.
 */
public sealed interface SmaxWaffleRefreshAccessTokensResponse extends SmaxOperation.Response
        permits SmaxWaffleRefreshAccessTokensResponse.Success, SmaxWaffleRefreshAccessTokensResponse.ClientError, SmaxWaffleRefreshAccessTokensResponse.ServerError {

    /**
     * Tries each {@link SmaxWaffleRefreshAccessTokensResponse} variant
     * in priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Mirrors WA Web's {@code sendRefreshAccessTokensRPC} dispatch: the
     * incoming stanza is offered to the {@link Success} parser first,
     * then the {@link ClientError} parser, then the {@link ServerError}
     * parser.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty
     *         when none of the three parsers matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxWaffleRefreshAccessTokensRPC",
            exports = "sendRefreshAccessTokensRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxWaffleRefreshAccessTokensResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant: the relay returned a fresh
     * encryption-metadata subtree carrying the rotated access tokens.
     *
     * @apiNote
     * Consumed by {@code WAWebAccountLinkingAPI.refreshAccessToken},
     * which decrypts {@link #encryptionMetadata()} via
     * {@code decryptRSAEncryptedPayload} to recover the rotated tokens
     * before persisting them through the linking store.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleRefreshAccessTokensResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleIQResultResponseMixin")
    final class Success implements SmaxWaffleRefreshAccessTokensResponse {
        /**
         * The relay-returned encryption metadata carrying the rotated
         * tokens.
         */
        private final SmaxWaffleRsaEncryptionMetadata encryptionMetadata;

        /**
         * Constructs a new success projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope and
         * payload have been validated; embedders typically do not
         * instantiate this directly.
         *
         * @param encryptionMetadata the relay-returned metadata; never
         *                           {@code null}
         * @throws NullPointerException if {@code encryptionMetadata} is
         *                              {@code null}
         */
        public Success(SmaxWaffleRsaEncryptionMetadata encryptionMetadata) {
            this.encryptionMetadata = Objects.requireNonNull(encryptionMetadata, "encryptionMetadata cannot be null");
        }

        /**
         * Returns the relay-returned encryption metadata.
         *
         * @return the metadata as supplied by the relay; never
         *         {@code null}
         */
        public SmaxWaffleRsaEncryptionMetadata encryptionMetadata() {
            return encryptionMetadata;
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope check
         * fails, when the {@code <encryption_metadata/>} child is
         * missing, or when the inner metadata subtree is malformed (see
         * {@link SmaxWaffleRsaEncryptionMetadata#of(Node)}).
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleRefreshAccessTokensResponseSuccess",
                exports = "parseRefreshAccessTokensResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var encryptionMetadataNode = node.getChild("encryption_metadata").orElse(null);
            if (encryptionMetadataNode == null) {
                return Optional.empty();
            }
            var metadata = SmaxWaffleRsaEncryptionMetadata.of(encryptionMetadataNode).orElse(null);
            if (metadata == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(metadata));
        }

        /**
         * Returns whether the given object is a {@link Success} with
         * equal encryption metadata.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both metadata subtrees match
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
            return Objects.equals(this.encryptionMetadata, that.encryptionMetadata);
        }

        /**
         * Returns a hash code derived from the encryption metadata.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(encryptionMetadata);
        }

        /**
         * Returns a debug rendering of this success variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleRefreshAccessTokensResponse.Success[encryptionMetadata="
                    + encryptionMetadata + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant: the relay rejected the
     * refresh with a code below {@code 500}.
     *
     * @apiNote
     * Surfaces malformed-request, unauthorised, and stale-nonce
     * rejections from the Waffle backend.
     * {@code WAWebAccountLinkingAPI.refreshAccessToken} routes the
     * error name through {@code WAWebWaffleIQErrorHandler} and may
     * trigger a {@code handleNonceRetry} pass when the handler returns
     * {@code request_nonce}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleRefreshAccessTokensResponseError")
    final class ClientError implements SmaxWaffleRefreshAccessTokensResponse {
        /**
         * The numeric error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope shape
         * has been validated; embedders typically do not instantiate
         * this directly.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable text, or {@code null}
         *                  when absent
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the code as supplied by the relay
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the
         * inbound stanza.
         *
         * @apiNote
         * Delegates the envelope and code-range check to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)},
         * which only matches codes below {@code 500}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleRefreshAccessTokensResponseError",
                exports = "parseRefreshAccessTokensResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * Returns whether the given object is a {@link ClientError}
         * with equal code and text.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both code and text match
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
         * Returns a hash code derived from the code and text.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug rendering of this client-error variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleRefreshAccessTokensResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant: the relay rejected the
     * refresh with a code of {@code 500} or above.
     *
     * @apiNote
     * Indicates a transient relay-side failure.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleRefreshAccessTokensResponseError")
    final class ServerError implements SmaxWaffleRefreshAccessTokensResponse {
        /**
         * The numeric error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope shape
         * has been validated; embedders typically do not instantiate
         * this directly.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable text, or {@code null}
         *                  when absent
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the code as supplied by the relay
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the
         * inbound stanza.
         *
         * @apiNote
         * Delegates the envelope and code-range check to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)},
         * which only matches codes at or above {@code 500}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleRefreshAccessTokensResponseError",
                exports = "parseRefreshAccessTokensResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * Returns whether the given object is a {@link ServerError}
         * with equal code and text.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both code and text match
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
         * Returns a hash code derived from the code and text.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug rendering of this server-error variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleRefreshAccessTokensResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
