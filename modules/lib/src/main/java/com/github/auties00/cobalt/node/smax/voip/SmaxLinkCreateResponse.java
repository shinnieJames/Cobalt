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
 * The inbound reply to a {@link SmaxLinkCreateRequest}, projecting the
 * relay's {@code <ack class="call">} stanza into one of two documented
 * variants: {@link Success} (token minted) and {@link ClientError}
 * (request refused).
 *
 * @apiNote
 * Consumed by the Cobalt counterpart of {@code WAWebVoipCreateCallLinkJob}
 * to turn the relay reply into the public-facing
 * {@code https://call.whatsapp.com/{voice|video}/<token>} URL or to surface
 * the {@code 503}/{@code 400} error to the UI.
 */
public sealed interface SmaxLinkCreateResponse extends SmaxOperation.Response
        permits SmaxLinkCreateResponse.Success, SmaxLinkCreateResponse.ClientError {

    /**
     * Parses an inbound stanza into the first matching reply variant.
     *
     * @apiNote
     * Backs the Cobalt analogue of
     * {@code WASmaxVoipLinkCreateRPC.sendLinkCreateRPC}: the JS dispatcher
     * tries the Ack parser, then the Nack parser, then throws an
     * {@code SmaxParsingFailure}; Cobalt instead returns
     * {@link Optional#empty()} so the caller can map the parse miss to the
     * configured error policy.
     *
     * @param node    the inbound stanza received from the relay; never {@code null}
     * @param request the original outbound stanza; used to verify the echoed {@code id}; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant matched
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
     * Validates the {@code <ack class="call">} envelope common to both reply
     * variants.
     *
     * @implNote
     * This implementation mirrors {@code WASmaxInVoipCallAckBaseMixin.parseCallAckBaseMixin}:
     * it requires the {@code <ack>} description, the {@code class="call"} marker,
     * the echoed request {@code id}, and the literal {@code from="call"} server.
     *
     * @param node    the inbound stanza
     * @param request the original outbound request
     * @return {@code true} when the envelope matches; {@code false} otherwise
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
     * The successful reply carrying the freshly minted call-link token.
     *
     * @apiNote
     * The {@link #linkCreateToken()} value is the short suffix appended to
     * {@code https://call.whatsapp.com/voice/} or {@code .../video/} to
     * produce the shareable URL.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipLinkCreateResponseLinkCreateAck")
    final class Success implements SmaxLinkCreateResponse {
        /**
         * The minted call-link token.
         *
         * @apiNote
         * The token is opaque, server-issued, and forms the path suffix of
         * the shareable {@code https://call.whatsapp.com/{voice|video}/<token>}
         * URL.
         */
        private final String linkCreateToken;

        /**
         * The optional echoed media type carried by the {@code media} attribute.
         */
        private final String linkCreateMedia;

        /**
         * The optional echoed creator-device JID.
         */
        private final Jid linkCreateCallCreator;

        /**
         * The optional echoed call identifier.
         */
        private final String linkCreateCallId;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * The {@code linkCreateMedia}, {@code linkCreateCallCreator}, and
         * {@code linkCreateCallId} echoes appear only when the original
         * request set them; the relay does not synthesise defaults.
         *
         * @param linkCreateToken       the minted token; never {@code null}
         * @param linkCreateMedia       the optional echoed media type; may be {@code null}
         * @param linkCreateCallCreator the optional echoed creator-device JID; may be {@code null}
         * @param linkCreateCallId      the optional echoed call id; may be {@code null}
         * @throws NullPointerException if {@code linkCreateToken} is {@code null}
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
         * Returns the minted call-link token.
         *
         * @return the token; never {@code null}
         */
        public String linkCreateToken() {
            return linkCreateToken;
        }

        /**
         * Returns the optional echoed media type.
         *
         * @return an {@link Optional} carrying the media type, or empty when omitted
         */
        public Optional<String> linkCreateMedia() {
            return Optional.ofNullable(linkCreateMedia);
        }

        /**
         * Returns the optional echoed creator-device JID.
         *
         * @return an {@link Optional} carrying the device JID, or empty when omitted
         */
        public Optional<Jid> linkCreateCallCreator() {
            return Optional.ofNullable(linkCreateCallCreator);
        }

        /**
         * Returns the optional echoed call identifier.
         *
         * @return an {@link Optional} carrying the call id, or empty when omitted
         */
        public Optional<String> linkCreateCallId() {
            return Optional.ofNullable(linkCreateCallId);
        }

        /**
         * Parses an inbound stanza into a {@link Success} variant.
         *
         * @apiNote
         * Returns an empty {@link Optional} on schema mismatch so callers can
         * fall through to {@link ClientError#of(Node, Node)}.
         *
         * @implNote
         * This implementation requires the shared ack envelope, the
         * {@code type="link_create"} marker, an inner {@code <link_create>}
         * child, and a non-null {@code token} attribute; the other attributes
         * are optional echoes of the request.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
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
     * The client-error reply produced when the relay rejects the request.
     *
     * @apiNote
     * {@code WAWebVoipCreateCallLinkJob} surfaces {@code 503} as
     * "Service Unavailable" and {@code 400} as "Bad Request"; any other
     * numeric code falls through to "Unknown Error". The non-numeric case
     * is folded onto {@code errorCode == -1}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipLinkCreateResponseLinkCreateNack")
    final class ClientError implements SmaxLinkCreateResponse {
        /**
         * The numeric error code parsed from the {@code error} attribute,
         * or {@code -1} when the raw value is non-numeric.
         */
        private final int errorCode;

        /**
         * The raw {@code error} attribute value.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @param errorCode the numeric error code; {@code -1} when the raw attribute is non-numeric
         * @param errorText the raw error string; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code, or {@code -1} when the raw {@code error}
         *         attribute was non-numeric
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional raw error string.
         *
         * @return an {@link Optional} carrying the error string, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses an inbound stanza into a {@link ClientError} variant.
         *
         * @implNote
         * This implementation requires the shared ack envelope, the
         * {@code type="link_create"} marker, and a non-null {@code error}
         * attribute; the attribute value is parsed as a decimal integer and
         * falls back to {@code -1} when non-numeric.
         *
         * @param node    the inbound stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
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
