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
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in
 * response to a {@link SmaxGroupsGetGroupInfoRequest}.
 */
public sealed interface SmaxGroupsGetGroupInfoResponse extends SmaxOperation.Response
        permits SmaxGroupsGetGroupInfoResponse.Success, SmaxGroupsGetGroupInfoResponse.ClientError, SmaxGroupsGetGroupInfoResponse.ServerError {

    /**
     * Tries each {@link SmaxGroupsGetGroupInfoResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @param node    the inbound IQ stanza received from the relay;
     *                never {@code null}
     * @param request the original outbound stanza — used to validate
     *                echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetGroupInfoRPC",
            exports = "sendGetGroupInfoRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsGetGroupInfoResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant — carries the
     * {@code <group/>} subtree carrying the
     * {@code WASmaxInGroupsGroupInfoMixin} projection plus the
     * top-level {@code size} attribute.
     *
     * <p>The {@code <group/>} sub-node is exposed verbatim so callers
     * can drive their own projection (subject, picture, owner, admin
     * list, ephemeral expiration, etc.) without committing this
     * class to the full mixin schema; the typical caller will read
     * the standard child attributes via
     * {@link Node#getAttributeAsString(String)} and
     * {@link Node#streamChildren(String)}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetGroupInfoResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupInfoMixin")
    final class Success implements SmaxGroupsGetGroupInfoResponse {
        /**
         * The group's participant count (range {@code [0, 19999]});
         * {@code null} when the relay omitted the {@code size}
         * attribute.
         */
        private final Integer groupSize;

        /**
         * The {@code <group/>} child carrying the
         * {@code GroupInfoMixin} subtree.
         */
        private final Node group;

        /**
         * Constructs a new successful reply.
         *
         * @param groupSize the group's participant count; may be
         *                  {@code null} when the relay omitted the
         *                  {@code size} attribute
         * @param group     the {@code <group/>} sub-node; never
         *                  {@code null}
         * @throws NullPointerException     if {@code group} is
         *                                  {@code null}
         * @throws IllegalArgumentException if {@code groupSize} is
         *                                  negative
         */
        public Success(Integer groupSize, Node group) {
            if (groupSize != null && groupSize < 0) {
                throw new IllegalArgumentException("groupSize must be non-negative");
            }
            this.groupSize = groupSize;
            this.group = Objects.requireNonNull(group, "group cannot be null");
        }

        /**
         * Returns the group's participant count.
         *
         * @return an {@link Optional} carrying the group size, or
         *         empty when the relay omitted the {@code size}
         *         attribute
         */
        public Optional<Integer> groupSize() {
            return Optional.ofNullable(groupSize);
        }

        /**
         * Returns the raw {@code <group/>} sub-node carrying the
         * remaining {@code GroupInfoMixin} fields.
         *
         * @return the {@code <group/>} node; never {@code null}
         */
        public Node group() {
            return group;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetGroupInfoResponseSuccess",
                exports = "parseGetGroupInfoResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var group = node.getChild("group").orElse(null);
            if (group == null) {
                return Optional.empty();
            }
            var sizeOpt = group.getAttributeAsInt("size");
            Integer size = sizeOpt.isPresent() ? sizeOpt.getAsInt() : null;
            var success = new Success(size, group);
            return Optional.of(success);
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
            return Objects.equals(this.groupSize, that.groupSize) && Objects.equals(this.group, that.group);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupSize, group);
        }

        @Override
        public String toString() {
            return "SmaxGroupsGetGroupInfoResponse.Success[groupSize=" + groupSize
                    + ", group=" + group + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant — the relay rejected
     * the request as malformed, unauthorised, or referencing a
     * non-existent group.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetGroupInfoResponseClientError")
    final class ClientError implements SmaxGroupsGetGroupInfoResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied one.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
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
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the
         * given inbound stanza.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetGroupInfoResponseClientError",
                exports = "parseGetGroupInfoResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "SmaxGroupsGetGroupInfoResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant — the relay encountered
     * a transient internal failure while processing the request.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetGroupInfoResponseServerError")
    final class ServerError implements SmaxGroupsGetGroupInfoResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied one.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
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
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the
         * given inbound stanza.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         server-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetGroupInfoResponseServerError",
                exports = "parseGetGroupInfoResponseServerError",
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
            return "SmaxGroupsGetGroupInfoResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
