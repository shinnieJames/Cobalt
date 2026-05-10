package com.github.auties00.cobalt.node.smax.voip;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay.
 */
public sealed interface SmaxLinkQueryResponse extends SmaxOperation.Response
        permits SmaxLinkQueryResponse.Success, SmaxLinkQueryResponse.ClientError {

    /**
     * Tries each {@link SmaxLinkQueryResponse} variant in priority order.
     *
     * @param node    the inbound stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         empty on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxVoipLinkQueryRPC",
            exports = "sendLinkQueryRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxLinkQueryResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant. The relay resolved the
     * supplied token and returned the call link's full metadata.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipLinkQueryResponseLinkQueryAck")
    final class Success implements SmaxLinkQueryResponse {
        /**
         * The call link's creator device JID.
         */
        private final Jid linkQueryLinkCreator;

        /**
         * The optional creator phone-number JID. Supplied when the
         * relay knows both the LID and PN identifiers for the
         * creator.
         */
        private final Jid linkQueryLinkCreatorPn;

        /**
         * The optional creator-supplied display username.
         */
        private final String linkQueryLinkCreatorUsername;

        /**
         * The optional echoed action attribute.
         */
        private final String linkQueryAction;

        /**
         * The echoed call-link token.
         */
        private final String linkQueryToken;

        /**
         * The echoed media type.
         */
        private final String linkQueryMedia;

        /**
         * Whether the link is associated with a scheduled event.
         */
        private final boolean hasLinkQueryEvent;

        /**
         * The optional waiting-room descriptor.
         */
        private final WaitingRoom linkQueryWaitingRoom;

        /**
         * Constructs a new successful reply.
         *
         * @param linkQueryLinkCreator         the creator device JID;
         *                                     never {@code null}
         * @param linkQueryLinkCreatorPn       the optional creator PN;
         *                                     may be {@code null}
         * @param linkQueryLinkCreatorUsername the optional creator
         *                                     username; may be
         *                                     {@code null}
         * @param linkQueryAction              the optional echoed
         *                                     action; may be
         *                                     {@code null}
         * @param linkQueryToken               the echoed token; never
         *                                     {@code null}
         * @param linkQueryMedia               the echoed media type;
         *                                     never {@code null}
         * @param hasLinkQueryEvent            whether the link has an
         *                                     associated event
         * @param linkQueryWaitingRoom         the optional waiting-room
         *                                     descriptor; may be
         *                                     {@code null}
         */
        public Success(Jid linkQueryLinkCreator,
                       Jid linkQueryLinkCreatorPn,
                       String linkQueryLinkCreatorUsername,
                       String linkQueryAction,
                       String linkQueryToken,
                       String linkQueryMedia,
                       boolean hasLinkQueryEvent,
                       WaitingRoom linkQueryWaitingRoom) {
            this.linkQueryLinkCreator = Objects.requireNonNull(linkQueryLinkCreator, "linkQueryLinkCreator cannot be null");
            this.linkQueryLinkCreatorPn = linkQueryLinkCreatorPn;
            this.linkQueryLinkCreatorUsername = linkQueryLinkCreatorUsername;
            this.linkQueryAction = linkQueryAction;
            this.linkQueryToken = Objects.requireNonNull(linkQueryToken, "linkQueryToken cannot be null");
            this.linkQueryMedia = Objects.requireNonNull(linkQueryMedia, "linkQueryMedia cannot be null");
            this.hasLinkQueryEvent = hasLinkQueryEvent;
            this.linkQueryWaitingRoom = linkQueryWaitingRoom;
        }

        /**
         * Returns the creator device JID.
         *
         * @return the creator JID; never {@code null}
         */
        public Jid linkQueryLinkCreator() {
            return linkQueryLinkCreator;
        }

        /**
         * Returns the optional creator phone-number JID.
         *
         * @return an {@link Optional} carrying the creator PN, or
         *         empty when omitted
         */
        public Optional<Jid> linkQueryLinkCreatorPn() {
            return Optional.ofNullable(linkQueryLinkCreatorPn);
        }

        /**
         * Returns the optional creator username.
         *
         * @return an {@link Optional} carrying the username, or empty
         *         when omitted
         */
        public Optional<String> linkQueryLinkCreatorUsername() {
            return Optional.ofNullable(linkQueryLinkCreatorUsername);
        }

        /**
         * Returns the optional echoed action.
         *
         * @return an {@link Optional} carrying the action, or empty
         *         when omitted
         */
        public Optional<String> linkQueryAction() {
            return Optional.ofNullable(linkQueryAction);
        }

        /**
         * Returns the echoed token.
         *
         * @return the token; never {@code null}
         */
        public String linkQueryToken() {
            return linkQueryToken;
        }

        /**
         * Returns the echoed media type.
         *
         * @return the media type; never {@code null}
         */
        public String linkQueryMedia() {
            return linkQueryMedia;
        }

        /**
         * Returns whether the link has an associated event.
         *
         * @return {@code true} when the {@code <event/>} child was
         *         present; {@code false} otherwise
         */
        public boolean hasLinkQueryEvent() {
            return hasLinkQueryEvent;
        }

        /**
         * Returns the optional waiting-room descriptor.
         *
         * @return an {@link Optional} carrying the descriptor, or
         *         empty when the {@code <waiting_room/>} child was
         *         absent
         */
        public Optional<WaitingRoom> linkQueryWaitingRoom() {
            return Optional.ofNullable(linkQueryWaitingRoom);
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
        @WhatsAppWebExport(moduleName = "WASmaxInVoipLinkQueryResponseLinkQueryAck",
                exports = "parseLinkQueryResponseLinkQueryAck",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "link_query")) {
                return Optional.empty();
            }
            var linkQuery = node.getChild("link_query").orElse(null);
            if (linkQuery == null) {
                return Optional.empty();
            }
            var linkCreator = linkQuery.getAttributeAsString("link_creator")
                    .map(Jid::of)
                    .orElse(null);
            if (linkCreator == null) {
                return Optional.empty();
            }
            var token = linkQuery.getAttributeAsString("token").orElse(null);
            if (token == null) {
                return Optional.empty();
            }
            var media = linkQuery.getAttributeAsString("media").orElse(null);
            if (media == null) {
                return Optional.empty();
            }
            var linkCreatorPn = linkQuery.getAttributeAsString("link_creator_pn")
                    .map(Jid::of)
                    .orElse(null);
            var linkCreatorUsername = linkQuery.getAttributeAsString("link_creator_username").orElse(null);
            var action = linkQuery.getAttributeAsString("action").orElse(null);
            var hasEvent = linkQuery.getChild("event").isPresent();
            var waitingRoom = linkQuery.getChild("waiting_room")
                    .flatMap(WaitingRoom::of)
                    .orElse(null);
            return Optional.of(new Success(
                    linkCreator,
                    linkCreatorPn,
                    linkCreatorUsername,
                    action,
                    token,
                    media,
                    hasEvent,
                    waitingRoom));
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
            return this.hasLinkQueryEvent == that.hasLinkQueryEvent
                    && Objects.equals(this.linkQueryLinkCreator, that.linkQueryLinkCreator)
                    && Objects.equals(this.linkQueryLinkCreatorPn, that.linkQueryLinkCreatorPn)
                    && Objects.equals(this.linkQueryLinkCreatorUsername, that.linkQueryLinkCreatorUsername)
                    && Objects.equals(this.linkQueryAction, that.linkQueryAction)
                    && Objects.equals(this.linkQueryToken, that.linkQueryToken)
                    && Objects.equals(this.linkQueryMedia, that.linkQueryMedia)
                    && Objects.equals(this.linkQueryWaitingRoom, that.linkQueryWaitingRoom);
        }

        @Override
        public int hashCode() {
            return Objects.hash(linkQueryLinkCreator, linkQueryLinkCreatorPn,
                    linkQueryLinkCreatorUsername, linkQueryAction, linkQueryToken,
                    linkQueryMedia, hasLinkQueryEvent, linkQueryWaitingRoom);
        }

        @Override
        public String toString() {
            return "SmaxLinkQueryResponse.Success[linkQueryLinkCreator=" + linkQueryLinkCreator
                    + ", linkQueryLinkCreatorPn=" + linkQueryLinkCreatorPn
                    + ", linkQueryLinkCreatorUsername=" + linkQueryLinkCreatorUsername
                    + ", linkQueryAction=" + linkQueryAction
                    + ", linkQueryToken=" + linkQueryToken
                    + ", linkQueryMedia=" + linkQueryMedia
                    + ", hasLinkQueryEvent=" + hasLinkQueryEvent
                    + ", linkQueryWaitingRoom=" + linkQueryWaitingRoom + ']';
        }

        /**
         * Descriptor for the inner {@code <waiting_room
         * is_admin enabled/>} child carried by some
         * {@code Success} replies.
         */
        public static final class WaitingRoom {
            /**
             * Whether the caller is an admin of this call link's
             * waiting room (the {@code is_admin="1"} marker).
             */
            private final boolean isAdmin;

            /**
             * Whether the waiting-room gate is currently enabled.
             * Mirrors the {@code enabled} attribute, which carries
             * either {@code "0"} or {@code "1"} on the wire.
             */
            private final String enabled;

            /**
             * Constructs a new descriptor.
             *
             * @param isAdmin whether the caller is an admin
             * @param enabled the raw enabled string; never
             *                {@code null}
             */
            public WaitingRoom(boolean isAdmin, String enabled) {
                this.isAdmin = isAdmin;
                this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
            }

            /**
             * Returns whether the caller is an admin.
             *
             * @return {@code true} when the caller is an admin
             */
            public boolean isAdmin() {
                return isAdmin;
            }

            /**
             * Returns the raw enabled string.
             *
             * @return the enabled string; never {@code null}
             */
            public String enabled() {
                return enabled;
            }

            /**
             * Tries to parse a descriptor from the given
             * {@code <waiting_room>} node.
             *
             * @param node the inner stanza; never {@code null}
             * @return an {@link Optional} carrying the parsed
             *         descriptor, or empty when malformed
             */
            public static Optional<WaitingRoom> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("waiting_room")) {
                    return Optional.empty();
                }
                var enabled = node.getAttributeAsString("enabled").orElse(null);
                if (enabled == null) {
                    return Optional.empty();
                }
                var isAdmin = node.hasAttribute("is_admin", "1");
                return Optional.of(new WaitingRoom(isAdmin, enabled));
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (WaitingRoom) obj;
                return this.isAdmin == that.isAdmin && Objects.equals(this.enabled, that.enabled);
            }

            @Override
            public int hashCode() {
                return Objects.hash(isAdmin, enabled);
            }

            @Override
            public String toString() {
                return "SmaxLinkQueryResponse.Success.WaitingRoom[isAdmin=" + isAdmin
                        + ", enabled=" + enabled + ']';
            }
        }
    }

    /**
     * The {@code ClientError} reply variant. The relay rejected the
     * query because the supplied token is invalid, expired, revoked,
     * or refers to a link the caller is not authorised to view.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInVoipLinkQueryResponseLinkQueryNack")
    final class ClientError implements SmaxLinkQueryResponse {
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
         * The per-RPC token attribute carried by the inner
         * {@code <error/>} child.
         */
        private final String errorToken;

        /**
         * Constructs a new client-error reply.
         *
         * @param errorCode  the numeric code; {@code -1} when the
         *                   raw attribute is non-numeric
         * @param errorText  the raw error string; may be {@code null}
         * @param errorToken the per-RPC token; may be {@code null}
         */
        public ClientError(int errorCode, String errorText, String errorToken) {
            this.errorCode = errorCode;
            this.errorText = errorText;
            this.errorToken = errorToken;
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
         * Returns the optional per-RPC token attribute.
         *
         * @return an {@link Optional} carrying the token, or empty
         *         when omitted
         */
        public Optional<String> errorToken() {
            return Optional.ofNullable(errorToken);
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
        @WhatsAppWebExport(moduleName = "WASmaxInVoipLinkQueryResponseLinkQueryNack",
                exports = "parseLinkQueryResponseLinkQueryNack",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "link_query")) {
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
            var token = errorChild.getAttributeAsString("token").orElse(null);
            if (token == null) {
                return Optional.empty();
            }
            int code;
            try {
                code = Integer.parseInt(errorAttr);
            } catch (NumberFormatException _) {
                code = -1;
            }
            return Optional.of(new ClientError(code, errorAttr, token));
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
                    && Objects.equals(this.errorToken, that.errorToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText, errorToken);
        }

        @Override
        public String toString() {
            return "SmaxLinkQueryResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText
                    + ", errorToken=" + errorToken + ']';
        }
    }
}
