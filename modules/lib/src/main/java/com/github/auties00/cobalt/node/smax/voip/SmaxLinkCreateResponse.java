package com.github.auties00.cobalt.node.smax.voip;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in
 * response to a {@link SmaxLinkCreateRequest}.
 */
public sealed interface SmaxLinkCreateResponse extends SmaxOperation.Response
        permits SmaxLinkCreateResponse.Success, SmaxLinkCreateResponse.ClientError {

    /**
     * Tries each {@link SmaxLinkCreateResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @param node    the inbound stanza received from the relay;
     *                never {@code null}
     * @param request the original outbound stanza. Used to validate
     *                the echoed identifier; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxVoipLinkCreateRPC",
            exports = "sendLinkCreateRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxLinkCreateResponse> of(Node node, Node request) {
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
     * both the {@code Ack} and {@code Nack} reply variants.
     *
     * <p>Checks the {@code <ack>} tag, the echoed {@code id}, the
     * {@code class="call"} marker, and the literal
     * {@code from="call"} server.
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
     * request and returned the freshly minted call-link token.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipLinkCreateResponseLinkCreateAck")
    final class Success implements SmaxLinkCreateResponse {
        /**
         * The freshly minted call-link token. The short string that
         * forms the suffix of the shareable call-link URL.
         */
        private final String linkCreateToken;

        /**
         * The optional echoed media type.
         */
        private final String linkCreateMedia;

        /**
         * The optional echoed call-creator device JID.
         */
        private final Jid linkCreateCallCreator;

        /**
         * The optional echoed call identifier.
         */
        private final String linkCreateCallId;

        /**
         * Constructs a new successful reply.
         *
         * @param linkCreateToken       the freshly minted token;
         *                              never {@code null}
         * @param linkCreateMedia       the optional media type; may
         *                              be {@code null}
         * @param linkCreateCallCreator the optional call-creator
         *                              device JID; may be
         *                              {@code null}
         * @param linkCreateCallId      the optional call id; may be
         *                              {@code null}
         */
        public Success(String linkCreateToken,
                       String linkCreateMedia,
                       Jid linkCreateCallCreator,
                       String linkCreateCallId) {
            this.linkCreateToken = Objects.requireNonNull(linkCreateToken, "linkCreateToken cannot be null");
            this.linkCreateMedia = linkCreateMedia;
            this.linkCreateCallCreator = linkCreateCallCreator;
            this.linkCreateCallId = linkCreateCallId;
        }

        /**
         * Returns the freshly minted call-link token.
         *
         * @return the token; never {@code null}
         */
        public String linkCreateToken() {
            return linkCreateToken;
        }

        /**
         * Returns the optional echoed media type.
         *
         * @return an {@link Optional} carrying the media type, or
         *         empty when omitted
         */
        public Optional<String> linkCreateMedia() {
            return Optional.ofNullable(linkCreateMedia);
        }

        /**
         * Returns the optional echoed call-creator device JID.
         *
         * @return an {@link Optional} carrying the device JID, or
         *         empty when omitted
         */
        public Optional<Jid> linkCreateCallCreator() {
            return Optional.ofNullable(linkCreateCallCreator);
        }

        /**
         * Returns the optional echoed call identifier.
         *
         * @return an {@link Optional} carrying the call id, or empty
         *         when omitted
         */
        public Optional<String> linkCreateCallId() {
            return Optional.ofNullable(linkCreateCallId);
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the success
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInVoipLinkCreateResponseLinkCreateAck",
                exports = "parseLinkCreateResponseLinkCreateAck",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "link_create")) {
                return Optional.empty();
            }
            var linkCreate = node.getChild("link_create").orElse(null);
            if (linkCreate == null) {
                return Optional.empty();
            }
            var token = linkCreate.getAttributeAsString("token").orElse(null);
            if (token == null) {
                return Optional.empty();
            }
            var media = linkCreate.getAttributeAsString("media").orElse(null);
            var callCreator = linkCreate.getAttributeAsString("call-creator")
                    .map(Jid::of)
                    .orElse(null);
            var callId = linkCreate.getAttributeAsString("call-id").orElse(null);
            return Optional.of(new Success(token, media, callCreator, callId));
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
            return Objects.equals(this.linkCreateToken, that.linkCreateToken)
                    && Objects.equals(this.linkCreateMedia, that.linkCreateMedia)
                    && Objects.equals(this.linkCreateCallCreator, that.linkCreateCallCreator)
                    && Objects.equals(this.linkCreateCallId, that.linkCreateCallId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(linkCreateToken, linkCreateMedia, linkCreateCallCreator, linkCreateCallId);
        }

        @Override
        public String toString() {
            return "SmaxLinkCreateResponse.Success[linkCreateToken=" + linkCreateToken
                    + ", linkCreateMedia=" + linkCreateMedia
                    + ", linkCreateCallCreator=" + linkCreateCallCreator
                    + ", linkCreateCallId=" + linkCreateCallId + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant. The relay rejected the
     * request, typically because the user is not authorised to
     * create call links or because the supplied call id is invalid.
     *
     * <p>Modelled as a {@code (errorCode, errorText)} pair to fit the
     * universal SMAX error contract; in practice {@code errorCode}
     * carries the {@code @error} string of the {@code <ack>} envelope
     * encoded as a numeric code (mapped via {@code parseInt} when
     * possible) and {@code errorText} carries the raw string for
     * human inspection.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipLinkCreateResponseLinkCreateNack")
    final class ClientError implements SmaxLinkCreateResponse {
        /**
         * The numeric error code, parsed from the {@code @error}
         * attribute when it is a decimal integer; {@code -1} when the
         * attribute is non-numeric.
         */
        private final int errorCode;

        /**
         * The raw {@code @error} attribute string, surfaced for
         * human inspection.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @param errorCode the numeric error code; {@code -1} when
         *                  the attribute is non-numeric
         * @param errorText the raw error string; may be {@code null}
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
         * Returns the optional raw error string.
         *
         * @return an {@link Optional} carrying the error string, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given
         * inbound stanza.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the
         *         client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInVoipLinkCreateResponseLinkCreateNack",
                exports = "parseLinkCreateResponseLinkCreateNack",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "link_create")) {
                return Optional.empty();
            }
            var errorAttr = node.getAttributeAsString("error").orElse(null);
            if (errorAttr == null) {
                return Optional.empty();
            }
            int code;
            try {
                code = Integer.parseInt(errorAttr);
            } catch (NumberFormatException _) {
                code = -1;
            }
            return Optional.of(new ClientError(code, errorAttr));
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
            return "SmaxLinkCreateResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
