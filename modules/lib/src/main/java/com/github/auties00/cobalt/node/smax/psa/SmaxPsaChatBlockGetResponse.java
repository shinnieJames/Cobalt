package com.github.auties00.cobalt.node.smax.psa;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The inbound reply to a {@link SmaxPsaChatBlockGetRequest}, projecting
 * either the {@link Success} variant carrying the current PSA blocking
 * status or the {@link ServerError} variant collapsing the documented
 * {@code 5xx}/{@code 4xx} errors.
 *
 * @apiNote
 * Consumed by the Cobalt analogue of
 * {@code WAWebQueryBlockListJob.getBlockingStatusForPSAUser}, which lifts
 * {@link Success#blockingStatus()} into a UI-facing boolean or surfaces the
 * server-error pair to the caller.
 */
public sealed interface SmaxPsaChatBlockGetResponse extends SmaxOperation.Response
        permits SmaxPsaChatBlockGetResponse.Success, SmaxPsaChatBlockGetResponse.ServerError {

    /**
     * Parses an inbound stanza into the first matching reply variant.
     *
     * @apiNote
     * Mirrors {@code WASmaxPsaChatBlockGetRPC.sendChatBlockGetRPC}; Cobalt
     * returns {@link Optional#empty()} on no-match instead of throwing the
     * JS {@code SmaxParsingFailure}, so callers can route the parse miss
     * through the configured error policy.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxPsaChatBlockGetRPC",
            exports = "sendChatBlockGetRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxPsaChatBlockGetResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        return ServerError.of(node, request);
    }

    /**
     * The successful reply carrying the current PSA blocking status.
     *
     * @apiNote
     * The {@link #blockingStatus()} value drives the local "PSA muted"
     * preference and the corresponding boolean returned by
     * {@code WAWebQueryBlockListJob.getBlockingStatusForPSAUser}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPsaChatBlockGetResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInPsaIQResultResponseMixin")
    final class Success implements SmaxPsaChatBlockGetResponse {
        /**
         * The current blocking status echoed by the relay. Either
         * {@link SmaxPsaChatBlockGetBlockingStatus#BLOCKED} or
         * {@link SmaxPsaChatBlockGetBlockingStatus#UNBLOCKED}.
         */
        private final SmaxPsaChatBlockGetBlockingStatus blockingStatus;

        /**
         * Constructs a successful reply.
         *
         * @param blockingStatus the blocking status; never {@code null}
         * @throws NullPointerException if {@code blockingStatus} is {@code null}
         */
        public Success(SmaxPsaChatBlockGetBlockingStatus blockingStatus) {
            this.blockingStatus = Objects.requireNonNull(blockingStatus, "blockingStatus cannot be null");
        }

        /**
         * Returns the current blocking status.
         *
         * @return the blocking status; never {@code null}
         */
        public SmaxPsaChatBlockGetBlockingStatus blockingStatus() {
            return blockingStatus;
        }

        /**
         * Parses an inbound stanza into a {@link Success} variant.
         *
         * @implNote
         * This implementation mirrors {@code parseChatBlockGetResponseSuccess}:
         * after validating the shared
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)} envelope it
         * requires an inner {@code <blocking>} child carrying a {@code status}
         * attribute admitted by
         * {@link SmaxPsaChatBlockGetBlockingStatus#ofWire(String)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPsaChatBlockGetResponseSuccess",
                exports = "parseChatBlockGetResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var blocking = node.getChild("blocking").orElse(null);
            if (blocking == null) {
                return Optional.empty();
            }
            var statusAttr = blocking.getAttributeAsString("status").orElse(null);
            if (statusAttr == null) {
                return Optional.empty();
            }
            var status = SmaxPsaChatBlockGetBlockingStatus.ofWire(statusAttr).orElse(null);
            if (status == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(status));
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
            return this.blockingStatus == that.blockingStatus;
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockingStatus);
        }

        @Override
        public String toString() {
            return "SmaxPsaChatBlockGetResponse.Success[blockingStatus=" + blockingStatus + ']';
        }
    }

    /**
     * The server-error reply collapsing the four documented PSA error
     * variants into a single {@code (errorCode, errorText)} pair.
     *
     * @apiNote
     * {@code WASmaxInPsaChatBlockError.parseChatBlockError} routes the
     * inbound error through four sequential mixin parsers
     * ({@code IQErrorInternalServerError/500},
     * {@code IQErrorRequestTimeout/408},
     * {@code IQErrorServiceUnavailable/503},
     * {@code IQErrorRateOverlimit/429}); the surfaced UI uses only the
     * {@code (code, text)} pair, so Cobalt collapses the four variants.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPsaChatBlockGetResponseServerError")
    @WhatsAppWebModule(moduleName = "WASmaxInPsaChatBlockError")
    final class ServerError implements SmaxPsaChatBlockGetResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text supplied by the relay.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
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
         * @return an {@link Optional} carrying the error text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses an inbound stanza into a {@link ServerError} variant.
         *
         * @implNote
         * This implementation delegates to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)},
         * which validates the IQ-error envelope and extracts the
         * {@code (code, text)} pair from whichever of the four documented
         * error mixins matched.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPsaChatBlockGetResponseServerError",
                exports = "parseChatBlockGetResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "SmaxPsaChatBlockGetResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
