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
 * {@link SmaxGroupsDeleteParentGroupRequest}.
 *
 * @apiNote
 * Pattern-match the result returned by {@link #of(Node, Node)} to drive UI surfaces equivalent to WA Web's
 * {@code WAWebGroupCommunityJob.deleteParentGroup} switch: {@link Success} confirms the community was torn
 * down, {@link ClientError} surfaces caller-side failures (permissions, malformed envelope), and
 * {@link ServerError} surfaces relay-side transient failures that may be retried.
 */
public sealed interface SmaxGroupsDeleteParentGroupResponse extends SmaxOperation.Response
        permits SmaxGroupsDeleteParentGroupResponse.Success, SmaxGroupsDeleteParentGroupResponse.ClientError, SmaxGroupsDeleteParentGroupResponse.ServerError {

    /**
     * Parses the inbound IQ stanza into the first matching {@link SmaxGroupsDeleteParentGroupResponse} variant.
     *
     * @apiNote
     * Mirrors WA Web's {@code WASmaxGroupsDeleteParentGroupRPC.sendDeleteParentGroupRPC} fall-through cascade:
     * {@link Success}, {@link ClientError}, {@link ServerError}. An empty {@link Optional} signals a stanza
     * shape outside the documented union and is treated by WA Web as a parsing failure
     * ({@code WASmaxParsingFailure.SmaxParsingFailure}).
     *
     * @implNote
     * This implementation runs the variant probes in the same priority order as WA Web; it does not throw
     * a parsing-failure exception, leaving the recovery decision to the caller.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound {@link SmaxGroupsDeleteParentGroupRequest} stanza; used to validate
     *                the echoed {@code id} attribute; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when no documented
     *         variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsDeleteParentGroupRPC",
            exports = "sendDeleteParentGroupRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsDeleteParentGroupResponse> of(Node node, Node request) {
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
     * The success variant returned when the relay tore down the targeted community.
     *
     * @apiNote
     * Carries no payload; the absence of an error envelope and a matching {@code <iq type="result">} shell is
     * the only signal callers receive that the community and its sub-groups have been deactivated. WA Web's
     * {@code WAWebGroupCommunityJob.deleteParentGroup} switch maps this variant to the resolved
     * {@code parent_group_jid} response object.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsDeleteParentGroupResponseSuccess")
    final class Success implements SmaxGroupsDeleteParentGroupResponse {
        /**
         * Constructs a success variant.
         *
         * @apiNote
         * Typically created by {@link #of(Node, Node)}; embedders may construct it directly to seed
         * test fixtures.
         */
        public Success() {
        }

        /**
         * Parses the inbound stanza into a {@link Success} when the result envelope is well-formed.
         *
         * @apiNote
         * Invoked by {@link SmaxGroupsDeleteParentGroupResponse#of(Node, Node)} as the first probe in the
         * variant cascade.
         *
         * @implNote
         * This implementation delegates the envelope shell check (description, {@code type="result"}, echoed
         * {@code id}) to {@link SmaxIqResultResponseMixin#validate(Node, Node)} and accepts any payload
         * shape; the relay does not include child elements on success.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when the
         *         envelope does not satisfy the result-shell contract
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsDeleteParentGroupResponseSuccess",
                exports = "parseDeleteParentGroupResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        @Override
        public String toString() {
            return "SmaxGroupsDeleteParentGroupResponse.Success[]";
        }
    }

    /**
     * The client-error variant returned when the relay rejected the request as malformed, unauthorised, or
     * targeting a non-community group.
     *
     * @apiNote
     * WA Web's {@code WAWebGroupCommunityJob.deleteParentGroup} forwards this variant as a
     * {@code ServerStatusCodeError} carrying the numeric {@link #errorCode()} and {@link #errorText()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsDeleteParentGroupResponseClientError")
    final class ClientError implements SmaxGroupsDeleteParentGroupResponse {
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
         * @apiNote
         * Forwarded as the {@code Number} status code in WA Web's {@code ServerStatusCodeError}.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @apiNote
         * Forwarded as the message argument to WA Web's {@code ServerStatusCodeError}; empty when the relay
         * omitted the {@code text} attribute.
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
         * {@link SmaxGroupsDeleteParentGroupResponse#of(Node, Node)}.
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsDeleteParentGroupResponseClientError",
                exports = "parseDeleteParentGroupResponseClientError",
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
            return "SmaxGroupsDeleteParentGroupResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The server-error variant returned when the relay encountered a transient internal failure.
     *
     * @apiNote
     * WA Web's {@code WAWebGroupCommunityJob.deleteParentGroup} forwards this variant as a
     * {@code ServerStatusCodeError}; callers can decide whether to retry based on the surfaced
     * {@link #errorCode()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsDeleteParentGroupResponseServerError")
    final class ServerError implements SmaxGroupsDeleteParentGroupResponse {
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
         * @apiNote
         * Forwarded as the {@code Number} status code in WA Web's {@code ServerStatusCodeError}.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @apiNote
         * Empty when the relay omitted the {@code text} attribute; WA Web forwards the value verbatim to its
         * {@code ServerStatusCodeError} constructor.
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
         * {@link SmaxGroupsDeleteParentGroupResponse#of(Node, Node)}.
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsDeleteParentGroupResponseServerError",
                exports = "parseDeleteParentGroupResponseServerError",
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
            return "SmaxGroupsDeleteParentGroupResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
