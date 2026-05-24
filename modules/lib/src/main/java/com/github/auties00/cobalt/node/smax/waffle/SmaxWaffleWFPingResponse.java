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
 * {@link SmaxWaffleWFPingRequest}.
 *
 * @apiNote
 * Mirrors WA Web's three documented {@code WFPing} reply shapes: a
 * {@link Success} carrying the relay-chosen next-ping cadence
 * (consumed by {@code WAWebAccountLinkingAPI.ping} to call
 * {@code updatePingInterval}), a {@link ClientError} for malformed,
 * unauthorised, or unknown-fbid requests, and a {@link ServerError}
 * for transient relay failures.
 */
public sealed interface SmaxWaffleWFPingResponse extends SmaxOperation.Response
        permits SmaxWaffleWFPingResponse.Success, SmaxWaffleWFPingResponse.ClientError, SmaxWaffleWFPingResponse.ServerError {

    /**
     * Tries each {@link SmaxWaffleWFPingResponse} variant in priority
     * order and returns the first that parses cleanly.
     *
     * @apiNote
     * Mirrors WA Web's {@code sendWFPingRPC} dispatch: the incoming
     * stanza is offered to the {@link Success} parser first, then the
     * {@link ClientError} parser, then the {@link ServerError} parser.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty
     *         when none of the three parsers matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxWaffleWFPingRPC",
            exports = "sendWFPingRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxWaffleWFPingResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant: the relay accepted the ping
     * and surfaced the next-ping cadence.
     *
     * @apiNote
     * Consumed by {@code WAWebAccountLinkingAPI.ping}, which feeds
     * {@link #pingInterval()} to {@code updatePingInterval} so the
     * Waffle scheduler reschedules the next ping; the relay can lower
     * or raise the cadence to throttle individual clients.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleWFPingResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleIQResultResponseMixin")
    final class Success implements SmaxWaffleWFPingResponse {
        /**
         * The relay-chosen seconds-between-pings cadence.
         */
        private final int pingInterval;

        /**
         * Constructs a new success projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope shape
         * has been validated; embedders typically do not instantiate
         * this directly.
         *
         * @param pingInterval the relay-chosen cadence in seconds
         */
        public Success(int pingInterval) {
            this.pingInterval = pingInterval;
        }

        /**
         * Returns the relay-chosen ping cadence.
         *
         * @return the cadence in seconds as supplied by the relay
         */
        public int pingInterval() {
            return pingInterval;
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope check
         * fails or when the {@code <ping_interval/>} child is missing
         * or non-numeric.
         *
         * @implNote
         * This implementation parses {@code ping_interval} content as
         * an ASCII integer via {@link Integer#parseInt(String)}; WA Web
         * uses its typed {@code contentInt} parser.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleWFPingResponseSuccess",
                exports = "parseWFPingResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var pingIntervalNode = node.getChild("ping_interval").orElse(null);
            if (pingIntervalNode == null) {
                return Optional.empty();
            }
            var pingInterval = pingIntervalNode.toContentString()
                    .map(String::trim)
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .orElse(null);
            if (pingInterval == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(pingInterval));
        }

        /**
         * Returns whether the given object is a {@link Success} with
         * an equal ping cadence.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both cadences match
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
            return this.pingInterval == that.pingInterval;
        }

        /**
         * Returns a hash code derived from the ping cadence.
         *
         * @return the {@link Integer#hashCode(int)} of the cadence
         */
        @Override
        public int hashCode() {
            return Integer.hashCode(pingInterval);
        }

        /**
         * Returns a debug rendering of this success variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleWFPingResponse.Success[pingInterval=" + pingInterval + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant: the relay rejected the
     * ping with a code below {@code 500}.
     *
     * @apiNote
     * Surfaces malformed-request, unauthorised, and unknown-state
     * rejections from the Waffle backend. {@code WAWebAccountLinkingAPI.ping}
     * routes the error name through
     * {@code WAWebWaffleIQErrorHandler.handleCommonWaffleIQError} and
     * may schedule a nonce-refresh retry when the handler returns
     * {@code request_nonce}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleWFPingResponseError")
    final class ClientError implements SmaxWaffleWFPingResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleWFPingResponseError",
                exports = "parseWFPingResponseError",
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
            return "SmaxWaffleWFPingResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant: the relay rejected the
     * ping with a code of {@code 500} or above.
     *
     * @apiNote
     * Indicates a transient relay-side failure. The error name is
     * still surfaced through {@code WAWebWaffleIQErrorHandler} for
     * telemetry consistency with {@link ClientError}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleWFPingResponseError")
    final class ServerError implements SmaxWaffleWFPingResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleWFPingResponseError",
                exports = "parseWFPingResponseError",
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
            return "SmaxWaffleWFPingResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
