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
 * Sealed family of inbound reply variants produced by the relay in response to a
 * {@link SmaxGroupsGetGroupInfoRequest}.
 *
 * @apiNote
 * Pattern-match the result returned by {@link #of(Node, Node)} to drive chat-info refresh and invite-link
 * landing surfaces equivalent to WA Web's {@code WAWebGroupGetGroupInfoJob} / {@code WAWebGroupInviteV4Job}
 * switches: {@link Success} carries the {@code GroupInfoMixin} projection; the two error variants surface
 * caller-side and relay-side failures.
 */
public sealed interface SmaxGroupsGetGroupInfoResponse extends SmaxOperation.Response
        permits SmaxGroupsGetGroupInfoResponse.Success, SmaxGroupsGetGroupInfoResponse.ClientError, SmaxGroupsGetGroupInfoResponse.ServerError {

    /**
     * Parses the inbound IQ stanza into the first matching {@link SmaxGroupsGetGroupInfoResponse} variant.
     *
     * @apiNote
     * Mirrors WA Web's {@code WASmaxGroupsGetGroupInfoRPC.sendGetGroupInfoRPC} fall-through cascade:
     * {@link Success}, {@link ClientError}, {@link ServerError}. An empty {@link Optional} signals a stanza
     * shape outside the documented union.
     *
     * @implNote
     * This implementation runs the variant probes in the same priority order as WA Web; it does not throw a
     * parsing-failure exception, leaving the recovery decision to the caller.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound {@link SmaxGroupsGetGroupInfoRequest} stanza; used to validate the
     *                echoed {@code id} attribute; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when no
     *         documented variant matched
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
     * The success variant returned when the relay echoed the {@code <group/>} subtree carrying the
     * {@code WASmaxInGroupsGroupInfoMixin} projection.
     *
     * @apiNote
     * Carries the top-level participant {@code size} attribute and the verbatim {@code <group/>} sub-node so
     * callers can drive their own projection (subject, picture, owner, admin list, ephemeral expiration, and
     * so on) without this class committing to the full mixin schema. The typical caller reads child
     * attributes via {@link Node#getAttributeAsString(String)} and {@link Node#streamChildren(String)}.
     *
     * @implNote
     * This implementation exposes the raw {@code <group/>} child rather than projecting every mixin field
     * because the relay's {@code GroupInfoMixin} surface is wide and evolves with server-side feature flags;
     * callers can read only the fields they need.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetGroupInfoResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupInfoMixin")
    final class Success implements SmaxGroupsGetGroupInfoResponse {
        /**
         * The group's participant count in the range {@code [0, 19999]}; {@code null} when the relay omitted
         * the {@code size} attribute.
         */
        private final Integer groupSize;

        /**
         * The {@code <group/>} child carrying the full {@code GroupInfoMixin} subtree.
         */
        private final Node group;

        /**
         * Constructs a success variant.
         *
         * @apiNote
         * Typically produced by {@link #of(Node, Node)}; direct construction is used to seed test fixtures.
         *
         * @param groupSize the group's participant count; may be {@code null} when the relay omitted the
         *                  {@code size} attribute
         * @param group     the {@code <group/>} sub-node; never {@code null}
         * @throws NullPointerException     if {@code group} is {@code null}
         * @throws IllegalArgumentException if {@code groupSize} is negative
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
         * @return an {@link Optional} carrying the group size, or empty when the relay omitted the
         *         {@code size} attribute
         */
        public Optional<Integer> groupSize() {
            return Optional.ofNullable(groupSize);
        }

        /**
         * Returns the raw {@code <group/>} sub-node carrying the remaining {@code GroupInfoMixin} fields.
         *
         * @apiNote
         * Callers drive their own projection via {@link Node#getAttributeAsString(String)} and
         * {@link Node#streamChildren(String)} against this node.
         *
         * @return the {@code <group/>} {@link Node}; never {@code null}
         */
        public Node group() {
            return group;
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant.
         *
         * @apiNote
         * Invoked as the first probe in the variant cascade by
         * {@link SmaxGroupsGetGroupInfoResponse#of(Node, Node)}.
         *
         * @implNote
         * This implementation validates the IQ envelope via {@link SmaxIqResultResponseMixin#validate(Node, Node)},
         * extracts the {@code <group/>} child, and reads the optional {@code size} attribute as the
         * participant count.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         stanza does not match the success schema
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
            var size = sizeOpt.isPresent() ? sizeOpt.getAsInt() : null;
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
     * The client-error variant returned when the relay rejected the request as malformed, unauthorised, or
     * referencing a non-existent group.
     *
     * @apiNote
     * Forwarded by WA Web's {@code WAWebGroupGetGroupInfoJob} / {@code WAWebGroupInviteV4Job} as a
     * {@code ServerStatusCodeError} carrying {@link #errorCode()} and {@link #errorText()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetGroupInfoResponseClientError")
    final class ClientError implements SmaxGroupsGetGroupInfoResponse {
        /**
         * The numeric server-side error code, mirroring the {@code <error code="...">} attribute on the
         * inbound stanza.
         */
        private final int errorCode;

        /**
         * The human-readable error text echoed by the relay; {@code null} when the relay omitted the
         * {@code <error text="...">} attribute.
         */
        private final String errorText;

        /**
         * Constructs a client-error variant.
         *
         * @apiNote
         * Typically produced by {@link #of(Node, Node)}; direct construction is used to seed test fixtures.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ClientError} envelope.
         *
         * @apiNote
         * Invoked as the second probe in the variant cascade by
         * {@link SmaxGroupsGetGroupInfoResponse#of(Node, Node)}.
         *
         * @implNote
         * This implementation delegates the error-envelope extraction to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} so every SMAX response in the family
         * shares the same client-error parsing.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         envelope does not match the client-error schema
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
     * The server-error variant returned when the relay encountered a transient internal failure.
     *
     * @apiNote
     * Forwarded by WA Web's {@code WAWebGroupGetGroupInfoJob} / {@code WAWebGroupInviteV4Job} as a
     * {@code ServerStatusCodeError}; callers can decide whether to retry based on the surfaced
     * {@link #errorCode()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetGroupInfoResponseServerError")
    final class ServerError implements SmaxGroupsGetGroupInfoResponse {
        /**
         * The numeric server-side error code, mirroring the {@code <error code="...">} attribute on the
         * inbound stanza.
         */
        private final int errorCode;

        /**
         * The human-readable error text echoed by the relay; {@code null} when the relay omitted the
         * {@code <error text="...">} attribute.
         */
        private final String errorText;

        /**
         * Constructs a server-error variant.
         *
         * @apiNote
         * Typically produced by {@link #of(Node, Node)}; direct construction is used to seed test fixtures.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ServerError} envelope.
         *
         * @apiNote
         * Invoked as the terminal probe in the variant cascade by
         * {@link SmaxGroupsGetGroupInfoResponse#of(Node, Node)}.
         *
         * @implNote
         * This implementation delegates the error-envelope extraction to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} so every SMAX response in the family
         * shares the same server-error parsing.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         envelope does not match the server-error schema
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
