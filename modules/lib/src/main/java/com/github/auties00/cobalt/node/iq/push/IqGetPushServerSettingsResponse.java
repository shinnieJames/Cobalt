package com.github.auties00.cobalt.node.iq.push;

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
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqGetPushServerSettingsRequest}.
 *
 * @apiNote
 * WA Web's {@code getPushServerSettings} parser collapses both the success path and the
 * error path into a single {@code {webserverkey | {errorCode, errorText}}} discriminated
 * record; Cobalt splits the reply into {@link Success}, {@link ClientError}, and
 * {@link ServerError} variants so the browser-push driver can distinguish a missing-entry
 * failure from a transient relay failure.
 */
public sealed interface IqGetPushServerSettingsResponse extends IqOperation.Response
        permits IqGetPushServerSettingsResponse.Success, IqGetPushServerSettingsResponse.ClientError, IqGetPushServerSettingsResponse.ServerError {

    /**
     * Tries each {@link IqGetPushServerSettingsResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @apiNote
     * The priority order ({@link Success}, {@link ClientError}, {@link ServerError}) mirrors
     * the order WA Web's parser tries; only one variant ever populates because
     * {@link SmaxIqResultResponseMixin} and {@link SmaxBaseServerErrorMixin} match disjoint
     * stanza shapes.
     *
     * @param node    the inbound IQ stanza received from the relay
     * @param request the original outbound stanza, used to validate echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()}
     *         when no documented variant matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetPushServerSettingsJob",
            exports = "getPushServerSettings", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqGetPushServerSettingsResponse> of(Node node, Node request) {
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
     * Reply variant carrying the relay-issued server-side push key.
     *
     * @apiNote
     * The {@code webserverkey} is the VAPID-style server public key the browser will use to
     * validate web-push payload signatures; the browser-push driver hands it to
     * {@code PushManager.subscribe} verbatim.
     */
    @WhatsAppWebModule(moduleName = "WAWebGetPushServerSettingsJob")
    final class Success implements IqGetPushServerSettingsResponse {
        /**
         * Base64-encoded server-side push key returned in the {@code webserverkey} attribute
         * of the {@code <settings/>} grandchild.
         */
        private final String webServerKey;

        /**
         * Constructs a new successful reply.
         *
         * @param webServerKey the server-side push key
         * @throws NullPointerException if {@code webServerKey} is {@code null}
         */
        public Success(String webServerKey) {
            this.webServerKey = Objects.requireNonNull(webServerKey, "webServerKey cannot be null");
        }

        /**
         * Returns the base64-encoded server-side push key.
         *
         * @return the key string, never {@code null}
         */
        public String webServerKey() {
            return webServerKey;
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope lacks the {@code <settings/>}
         * grandchild or when the grandchild lacks the {@code webserverkey} attribute; the
         * caller falls through to the error variants in either case.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGetPushServerSettingsJob",
                exports = "getPushServerSettings", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var settingsChild = node.getChild("settings").orElse(null);
            if (settingsChild == null) {
                return Optional.empty();
            }
            var webServerKey = settingsChild.getAttributeAsString("webserverkey").orElse(null);
            if (webServerKey == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(webServerKey));
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
            return Objects.equals(this.webServerKey, that.webServerKey);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(webServerKey);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqGetPushServerSettingsResponse.Success[webServerKey=" + webServerKey + ']';
        }
    }

    /**
     * Reply variant signalling that the relay rejected the push-server-settings query as
     * malformed or unauthorised.
     *
     * @apiNote
     * Maps to the {@code 4xx} branch of WA Web's reply pipeline; the browser-push driver
     * treats this as a hard failure and skips the subscription attempt for the session.
     */
    @WhatsAppWebModule(moduleName = "WAWebGetPushServerSettingsJob")
    final class ClientError implements IqGetPushServerSettingsResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text, or {@code null} when omitted
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the stanza is a {@code type="error"}
         * envelope echoing the {@code request} id and carrying a {@code <error/>} child whose
         * {@code code} attribute falls in the {@code 4xx} range, per the parsing contract of
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the client-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGetPushServerSettingsJob",
                exports = "getPushServerSettings", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
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
            return "IqGetPushServerSettingsResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Reply variant signalling a transient server-side failure on a push-server-settings
     * query.
     *
     * @apiNote
     * Maps to the {@code 5xx} branch of WA Web's reply pipeline; the browser-push driver
     * may retry the same query on the next session attempt once the socket has settled.
     */
    @WhatsAppWebModule(moduleName = "WAWebGetPushServerSettingsJob")
    final class ServerError implements IqGetPushServerSettingsResponse {
        /**
         * Numeric server-side error code from the {@code <error code/>} attribute.
         */
        private final int errorCode;

        /**
         * Optional human-readable error text from the {@code <error text/>} attribute.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text, or {@code null} when omitted
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
         * @return an {@link Optional} carrying the text, or {@link Optional#empty()} when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given inbound stanza.
         *
         * @apiNote
         * Returns a populated {@link Optional} only when the stanza is a {@code type="error"}
         * envelope echoing the {@code request} id and carrying a {@code <error/>} child whose
         * {@code code} attribute falls outside the {@code 4xx} range, per the parsing
         * contract of {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not match the server-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGetPushServerSettingsJob",
                exports = "getPushServerSettings", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
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
            return "IqGetPushServerSettingsResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
