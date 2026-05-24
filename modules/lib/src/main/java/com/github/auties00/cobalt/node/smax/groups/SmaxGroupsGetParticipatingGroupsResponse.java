package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed reply family for a {@link SmaxGroupsGetParticipatingGroupsRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher's {@code Success}/{@code ClientError}/{@code ServerError}
 * cases: {@link Success} carries every group the caller participates in as raw {@code <group/>} subtrees, the two
 * error variants surface the relay's reason codes. The {@code WAWebGroupQueryJob.queryGroups} caller in WA Web uses
 * the same dispatch shape.
 */
public sealed interface SmaxGroupsGetParticipatingGroupsResponse extends SmaxOperation.Response
        permits SmaxGroupsGetParticipatingGroupsResponse.Success, SmaxGroupsGetParticipatingGroupsResponse.ClientError, SmaxGroupsGetParticipatingGroupsResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsGetParticipatingGroupsResponse} variant in priority order
     * and returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsGetParticipatingGroupsRPC}:
     * {@link Success} first, then {@link ClientError}, then {@link ServerError}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the three documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound request
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetParticipatingGroupsRPC",
            exports = "sendGetParticipatingGroupsRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsGetParticipatingGroupsResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node);
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
     * The reply variant emitted when the relay returned the caller's participating-group set.
     *
     * @apiNote Surfaces as the {@code GetParticipatingGroupsResponseSuccess} case in {@code WAWebGroupQueryJob};
     * each child group node feeds the chat-list bulk loader which builds the per-group {@code WAWebChatModel} via
     * {@code WAWebGroupsQueryApi.parseGroupSmax}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetParticipatingGroupsResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupInfoOrTruncatedGroupInfoGroupInfoMixinGroup")
    final class Success implements SmaxGroupsGetParticipatingGroupsResponse {
        /**
         * The raw {@code <group/>} children carried by the {@code <groups/>} wrapper.
         */
        private final List<Node> groups;

        /**
         * Constructs a {@link Success} reply.
         *
         * @apiNote {@code null} normalises to {@link List#of()} for callers that want to construct an empty result
         * directly.
         *
         * @param groups the {@code <group/>} sub-nodes; may be {@code null}
         */
        public Success(List<Node> groups) {
            this.groups = List.copyOf(Objects.requireNonNullElse(groups, List.of()));
        }

        /**
         * Returns the raw {@code <group/>} sub-nodes.
         *
         * @apiNote Cobalt exposes the subtrees verbatim so callers project subject, picture, owner, admin list,
         * ephemeral expiration, and addressing mode directly without committing to the full mixin schema.
         *
         * @return an unmodifiable list of {@code <group/>} nodes; never {@code null}
         */
        public List<Node> groups() {
            return groups;
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Matches when the IQ is a {@code type="result"} envelope carrying a {@code <groups>} wrapper; the
         * wrapper may be empty when the caller participates in no groups.
         *
         * @param node the inbound IQ stanza
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetParticipatingGroupsResponseSuccess",
                exports = "parseGetParticipatingGroupsResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "result")) {
                return Optional.empty();
            }
            var groupsWrapper = node.getChild("groups").orElse(null);
            if (groupsWrapper == null) {
                return Optional.empty();
            }
            var groups = groupsWrapper.streamChildren("group").toList();
            return Optional.of(new Success(groups));
        }

        /**
         * Compares this success to {@code obj} for value equality across every field.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Success} with identical fields
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
            return Objects.equals(this.groups, that.groups);
        }

        /**
         * Returns a hash composed of every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(groups);
        }

        /**
         * Returns a debug string carrying every field.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetParticipatingGroupsResponse.Success[groups=" + groups + ']';
        }
    }

    /**
     * The reply variant emitted when the relay rejected the bulk query as malformed or unauthorised.
     *
     * @apiNote Surfaces as the {@code GetParticipatingGroupsResponseClientError} case in {@code WAWebGroupQueryJob},
     * which logs the {@link #errorCode()} as the HTTP-style status passed back to the chat-list bulk loader.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetParticipatingGroupsResponseClientError")
    final class ClientError implements SmaxGroupsGetParticipatingGroupsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetParticipatingGroupsResponseClientError",
                exports = "parseGetParticipatingGroupsResponseClientError",
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
            return "SmaxGroupsGetParticipatingGroupsResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     *
     * @apiNote Surfaces as the {@code GetParticipatingGroupsResponseServerError} case in {@code WAWebGroupQueryJob},
     * where it is logged at the same severity as {@link ClientError} but typically signals retry-eligible relay
     * outages rather than caller error.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetParticipatingGroupsResponseServerError")
    final class ServerError implements SmaxGroupsGetParticipatingGroupsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetParticipatingGroupsResponseServerError",
                exports = "parseGetParticipatingGroupsResponseServerError",
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
            return "SmaxGroupsGetParticipatingGroupsResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
