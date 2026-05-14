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
 * response to a {@link SmaxGroupsGetLinkedGroupRequest}.
 */
public sealed interface SmaxGroupsGetLinkedGroupResponse extends SmaxOperation.Response
        permits SmaxGroupsGetLinkedGroupResponse.Success, SmaxGroupsGetLinkedGroupResponse.ClientError, SmaxGroupsGetLinkedGroupResponse.ServerError {

    /**
     * Tries each {@link SmaxGroupsGetLinkedGroupResponse} variant in priority order.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound request
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetLinkedGroupRPC",
            exports = "sendGetLinkedGroupRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsGetLinkedGroupResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant — carries a
     * {@code <linked_group jid="…">} wrapper holding a single
     * {@code <group/>} child carrying the standard
     * {@code GroupInfoMixin} subtree alongside the
     * {@link #linkedGroupJid} attribute and the {@link #groupSize}
     * scalar.
     *
     * <p>The remaining group attributes (subject, picture, owner,
     * admin list, ephemeral expiration, addressing mode, etc.) are
     * exposed verbatim via {@link #group()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetLinkedGroupResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsLinkedGroupInfoMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupAddressingModeMixin")
    final class Success implements SmaxGroupsGetLinkedGroupResponse {
        /**
         * The linked group's JID (the {@code jid} attribute on the
         * {@code <linked_group>} wrapper).
         */
        private final Jid linkedGroupJid;

        /**
         * The linked group's participant count (range
         * {@code [1, 19999]}); {@code null} when the relay omitted
         * the {@code size} attribute.
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
         * @param linkedGroupJid the linked group's JID; never
         *                       {@code null}
         * @param groupSize      the linked group's participant
         *                       count; may be {@code null} when the
         *                       relay omitted the {@code size}
         *                       attribute
         * @param group          the {@code <group/>} sub-node;
         *                       never {@code null}
         * @throws NullPointerException     if {@code linkedGroupJid}
         *                                  or {@code group} is
         *                                  {@code null}
         * @throws IllegalArgumentException if {@code groupSize} is
         *                                  negative
         */
        public Success(Jid linkedGroupJid, Integer groupSize, Node group) {
            this.linkedGroupJid = Objects.requireNonNull(linkedGroupJid, "linkedGroupJid cannot be null");
            if (groupSize != null && groupSize < 0) {
                throw new IllegalArgumentException("groupSize must be non-negative");
            }
            this.groupSize = groupSize;
            this.group = Objects.requireNonNull(group, "group cannot be null");
        }

        /**
         * Returns the linked group's JID.
         *
         * @return the JID; never {@code null}
         */
        public Jid linkedGroupJid() {
            return linkedGroupJid;
        }

        /**
         * Returns the linked group's participant count.
         *
         * @return an {@link Optional} carrying the size, or empty
         *         when the relay omitted the {@code size} attribute
         */
        public Optional<Integer> groupSize() {
            return Optional.ofNullable(groupSize);
        }

        /**
         * Returns the raw {@code <group/>} sub-node carrying the
         * {@code GroupInfoMixin} fields.
         *
         * @return the group node; never {@code null}
         */
        public Node group() {
            return group;
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetLinkedGroupResponseSuccess",
                exports = "parseGetLinkedGroupResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var linkedGroup = node.getChild("linked_group").orElse(null);
            if (linkedGroup == null) {
                return Optional.empty();
            }
            var linkedGroupJid = linkedGroup.getAttributeAsJid("jid").orElse(null);
            if (linkedGroupJid == null) {
                return Optional.empty();
            }
            var group = linkedGroup.getChild("group").orElse(null);
            if (group == null) {
                return Optional.empty();
            }
            var sizeOpt = group.getAttributeAsInt("size");
            Integer size = sizeOpt.isPresent() ? sizeOpt.getAsInt() : null;
            return Optional.of(new Success(linkedGroupJid, size, group));
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
            return Objects.equals(this.groupSize, that.groupSize)
                    && Objects.equals(this.linkedGroupJid, that.linkedGroupJid)
                    && Objects.equals(this.group, that.group);
        }

        @Override
        public int hashCode() {
            return Objects.hash(linkedGroupJid, groupSize, group);
        }

        @Override
        public String toString() {
            return "SmaxGroupsGetLinkedGroupResponse.Success[linkedGroupJid=" + linkedGroupJid
                    + ", groupSize=" + groupSize + ", group=" + group + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetLinkedGroupResponseClientError")
    final class ClientError implements SmaxGroupsGetLinkedGroupResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied
         * one.
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
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetLinkedGroupResponseClientError",
                exports = "parseGetLinkedGroupResponseClientError",
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
            return "SmaxGroupsGetLinkedGroupResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetLinkedGroupResponseServerError")
    final class ServerError implements SmaxGroupsGetLinkedGroupResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied
         * one.
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
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetLinkedGroupResponseServerError",
                exports = "parseGetLinkedGroupResponseServerError",
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
            return "SmaxGroupsGetLinkedGroupResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
