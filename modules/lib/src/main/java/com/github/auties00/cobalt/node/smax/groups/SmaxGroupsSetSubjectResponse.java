package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed reply family for a {@link SmaxGroupsSetSubjectRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher in {@code WASmaxGroupsSetSubjectRPC}.
 * {@link Success} carries no payload because the relay's acknowledgement is empty; the actual subject change is
 * broadcast back to participants via a separate notification path.
 */
public sealed interface SmaxGroupsSetSubjectResponse extends SmaxOperation.Response
        permits SmaxGroupsSetSubjectResponse.Success, SmaxGroupsSetSubjectResponse.ClientError, SmaxGroupsSetSubjectResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsSetSubjectResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsSetSubjectRPC}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound {@link SmaxGroupsSetSubjectRequest} stanza, used to validate echoed
     *                identifiers
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsSetSubjectRPC",
            exports = "sendSetSubjectRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsSetSubjectResponse> of(Node node, Node request) {
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
     * The reply variant emitted when the relay accepted the subject mutation.
     *
     * @apiNote The payload is empty by design: the relay returns a bare {@code type="result"} envelope and the
     * actual subject change is broadcast back to participants via a separate notification path.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsSetSubjectResponseSuccess")
    final class Success implements SmaxGroupsSetSubjectResponse {
        /**
         * Constructs a {@link Success}.
         */
        public Success() {
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Matches the WA Web parser {@code parseSetSubjectResponseSuccess}: the IQ must be a valid
         * {@code type="result"} echo of the request. No further fields are extracted.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsSetSubjectResponseSuccess",
                exports = "parseSetSubjectResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        /**
         * Compares this success to {@code obj} for type equality.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Success}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * Returns the constant hash code shared by every {@link Success} instance.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        /**
         * Returns a debug string identifying this success.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsSetSubjectResponse.Success[]";
        }
    }

    /**
     * The reply variant emitted when the relay rejected the request envelope as malformed, unauthorised, or
     * referencing a non-existent group.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsSetSubjectResponseClientError")
    final class ClientError implements SmaxGroupsSetSubjectResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Constructs a {@link ClientError} from raw error attributes.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code echoed by the relay.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text echoed by the relay.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} which validates the
         * shared {@code <iq type="error"><error code="..." text="..."/></iq>} envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsSetSubjectResponseClientError",
                exports = "parseSetSubjectResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * Compares this error to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link ClientError} with identical fields
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
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsSetSubjectResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsSetSubjectResponseServerError")
    final class ServerError implements SmaxGroupsSetSubjectResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Constructs a {@link ServerError} from raw error attributes.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code echoed by the relay.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text echoed by the relay.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} which validates the
         * shared {@code <iq type="error"><error code="..." text="..."/></iq>} envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsSetSubjectResponseServerError",
                exports = "parseSetSubjectResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * Compares this error to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link ServerError} with identical fields
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
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsSetSubjectResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
