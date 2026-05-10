package com.github.auties00.cobalt.node.smax.voip;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay.
 */
public sealed interface SmaxWaitingRoomToggleCallLinkResponse extends SmaxOperation.Response
        permits SmaxWaitingRoomToggleCallLinkResponse.Success, SmaxWaitingRoomToggleCallLinkResponse.ClientError {

    /**
     * Tries each {@link SmaxWaitingRoomToggleCallLinkResponse} variant in priority order.
     *
     * @param node    the inbound stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         empty on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxVoipWaitingRoomToggleCallLinkRPC",
            exports = "sendWaitingRoomToggleCallLinkRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxWaitingRoomToggleCallLinkResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        return ClientError.of(node, request);
    }

    /**
     * Validates the {@code <ack class="call">} envelope shared by
     * both reply variants.
     *
     * @param node    the inbound stanza
     * @param request the original outbound request
     * @return {@code true} when the envelope matches; {@code false}
     *         otherwise
     */
    private static boolean validateAckEnvelope(Node node, Node request) {
        if (!node.hasDescription("ack")) {
            return false;
        }
        if (!node.hasAttribute("class", "call")) {
            return false;
        }
        var requestId = request.getAttributeAsString("id").orElse(null);
        if (requestId == null) {
            return false;
        }
        if (!node.hasAttribute("id", requestId)) {
            return false;
        }
        var from = node.getAttributeAsString("from").orElse(null);
        if (from == null || !from.equals(JidServer.call().toString())) {
            return false;
        }
        return true;
    }

    /**
     * The {@code Success} reply variant. The relay accepted the
     * toggle and echoed back the link-token of the affected link.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipWaitingRoomToggleCallLinkResponseWaitingRoomToggleCallLinkAck")
    final class Success implements SmaxWaitingRoomToggleCallLinkResponse {
        /**
         * The echoed call-link token.
         */
        private final String waitingRoomToggleLinkToken;

        /**
         * Constructs a new successful reply.
         *
         * @param waitingRoomToggleLinkToken the echoed token; never
         *                                   {@code null}
         * @throws NullPointerException if {@code waitingRoomToggleLinkToken}
         *                              is {@code null}
         */
        public Success(String waitingRoomToggleLinkToken) {
            this.waitingRoomToggleLinkToken = Objects.requireNonNull(waitingRoomToggleLinkToken, "waitingRoomToggleLinkToken cannot be null");
        }

        /**
         * Returns the echoed token.
         *
         * @return the token; never {@code null}
         */
        public String waitingRoomToggleLinkToken() {
            return waitingRoomToggleLinkToken;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInVoipWaitingRoomToggleCallLinkResponseWaitingRoomToggleCallLinkAck",
                exports = "parseWaitingRoomToggleCallLinkResponseWaitingRoomToggleCallLinkAck",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "waiting_room_toggle")) {
                return Optional.empty();
            }
            var toggle = node.getChild("waiting_room_toggle").orElse(null);
            if (toggle == null) {
                return Optional.empty();
            }
            var linkToken = toggle.getAttributeAsString("link-token").orElse(null);
            if (linkToken == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(linkToken));
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
            return Objects.equals(this.waitingRoomToggleLinkToken, that.waitingRoomToggleLinkToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(waitingRoomToggleLinkToken);
        }

        @Override
        public String toString() {
            return "SmaxWaitingRoomToggleCallLinkResponse.Success[waitingRoomToggleLinkToken="
                    + waitingRoomToggleLinkToken + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant. The relay rejected the
     * toggle, typically because the caller is not the link's creator
     * or the link no longer exists.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipWaitingRoomToggleCallLinkResponseWaitingRoomToggleCallLinkNack")
    final class ClientError implements SmaxWaitingRoomToggleCallLinkResponse {
        /**
         * The numeric error code, parsed from the {@code @error}
         * attribute when it is a decimal integer; {@code -1} when
         * non-numeric.
         */
        private final int errorCode;

        /**
         * The raw {@code @error} attribute string.
         */
        private final String errorText;

        /**
         * The per-RPC link-token attribute carried by the inner
         * {@code <error/>} child.
         */
        private final String errorLinkToken;

        /**
         * Constructs a new client-error reply.
         *
         * @param errorCode      the numeric code; {@code -1} when
         *                       non-numeric
         * @param errorText      the raw error string; may be
         *                       {@code null}
         * @param errorLinkToken the per-RPC link-token; may be
         *                       {@code null}
         */
        public ClientError(int errorCode, String errorText, String errorLinkToken) {
            this.errorCode = errorCode;
            this.errorText = errorText;
            this.errorLinkToken = errorLinkToken;
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
         * Returns the optional raw error string.
         *
         * @return an {@link Optional} carrying the error string, or
         *         empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Returns the optional per-RPC link-token.
         *
         * @return an {@link Optional} carrying the link-token, or
         *         empty when omitted
         */
        public Optional<String> errorLinkToken() {
            return Optional.ofNullable(errorLinkToken);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the
         * inbound stanza.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInVoipWaitingRoomToggleCallLinkResponseWaitingRoomToggleCallLinkNack",
                exports = "parseWaitingRoomToggleCallLinkResponseWaitingRoomToggleCallLinkNack",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "waiting_room_toggle")) {
                return Optional.empty();
            }
            var errorAttr = node.getAttributeAsString("error").orElse(null);
            if (errorAttr == null) {
                return Optional.empty();
            }
            var errorChild = node.getChild("error").orElse(null);
            if (errorChild == null) {
                return Optional.empty();
            }
            var linkToken = errorChild.getAttributeAsString("link-token").orElse(null);
            if (linkToken == null) {
                return Optional.empty();
            }
            int code;
            try {
                code = Integer.parseInt(errorAttr);
            } catch (NumberFormatException _) {
                code = -1;
            }
            return Optional.of(new ClientError(code, errorAttr, linkToken));
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
                    && Objects.equals(this.errorText, that.errorText)
                    && Objects.equals(this.errorLinkToken, that.errorLinkToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText, errorLinkToken);
        }

        @Override
        public String toString() {
            return "SmaxWaitingRoomToggleCallLinkResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText
                    + ", errorLinkToken=" + errorLinkToken + ']';
        }
    }
}
