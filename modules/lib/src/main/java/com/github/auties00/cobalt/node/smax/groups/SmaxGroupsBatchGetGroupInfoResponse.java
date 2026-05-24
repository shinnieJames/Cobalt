package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed reply family for a {@link SmaxGroupsBatchGetGroupInfoRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher's
 * {@code Success}/{@code ClientError}/{@code ServerError} cases. The success branch wraps a heterogeneous
 * {@code <groups>} list: each child is either a full {@code group_info}, a truncated {@code group_info}, a
 * {@code group_forbidden} marker, or a {@code group_not_exist} marker. {@code WAWebGroupQueryJob} dispatches on
 * the child's shape to populate the chat database accordingly.
 */
public sealed interface SmaxGroupsBatchGetGroupInfoResponse extends SmaxOperation.Response
        permits SmaxGroupsBatchGetGroupInfoResponse.Success, SmaxGroupsBatchGetGroupInfoResponse.ClientError, SmaxGroupsBatchGetGroupInfoResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsBatchGetGroupInfoResponse} variant in priority order
     * and returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsBatchGetGroupInfoRPC}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound {@link SmaxGroupsBatchGetGroupInfoRequest} stanza, used to validate
     *                echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsBatchGetGroupInfoRPC",
            exports = "sendBatchGetGroupInfoRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsBatchGetGroupInfoResponse> of(Node node, Node request) {
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
     * The reply variant carrying a {@code <groups>} wrapper with one {@code <group/>} child per requested group.
     *
     * @apiNote Each {@code <group/>} child takes one of four sub-shapes (full {@code group_info}, truncated
     * {@code group_info}, {@code group_forbidden}, {@code group_not_exist}); callers dispatch on the child's
     * structure via standard {@link Node} accessors. Cobalt exposes the wrapper as an unmodifiable {@link List}
     * of raw {@link Node}s rather than projecting the four sub-shapes into a typed disjunction, mirroring the
     * shape exposed by {@link SmaxGroupsGetParticipatingGroupsResponse.Success}.
     *
     * @implNote Cobalt does not yet model the four sub-shapes as a typed alternation. Callers that need to
     * branch on the shape should inspect the {@code <group/>} child's description and attributes directly; the
     * upstream WA Web dispatcher in {@code WAWebGroupQueryJob} performs the same inspection.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsBatchGetGroupInfoResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupInfoOrTruncatedGroupInfoOrGroupForbiddenOrGroupNotExistMixinGroup")
    final class Success implements SmaxGroupsBatchGetGroupInfoResponse {
        /**
         * The {@code <group/>} children carried by the {@code <groups>} wrapper.
         */
        private final List<Node> groups;

        /**
         * Constructs a {@link Success}.
         *
         * @param groups the per-group reply nodes; defensively copied
         * @throws NullPointerException if {@code groups} is {@code null}
         */
        public Success(List<Node> groups) {
            Objects.requireNonNull(groups, "groups cannot be null");
            this.groups = List.copyOf(groups);
        }

        /**
         * Returns the per-group reply nodes.
         *
         * @return an unmodifiable list of {@code <group/>} {@link Node}s; never {@code null}
         */
        public List<Node> groups() {
            return groups;
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Matches the WA Web parser {@code parseBatchGetGroupInfoResponseSuccess}: the IQ must be a
         * valid {@code type="result"} echo of the request and must carry a non-empty {@code <groups>} wrapper.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsBatchGetGroupInfoResponseSuccess",
                exports = "parseBatchGetGroupInfoResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var groupsWrapper = node.getChild("groups").orElse(null);
            if (groupsWrapper == null) {
                return Optional.empty();
            }
            var groups = groupsWrapper.streamChildren("group").toList();
            if (groups.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Success(groups));
        }

        /**
         * Compares this success to {@code obj} for value equality on {@link #groups()}.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Success} with the same group list
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
         * Returns a hash derived from {@link #groups()}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(groups);
        }

        /**
         * Returns a debug string carrying {@link #groups()}.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsBatchGetGroupInfoResponse.Success[groups=" + groups + ']';
        }
    }

    /**
     * The reply variant emitted when the relay rejected the request as malformed or unauthorised.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsBatchGetGroupInfoResponseClientError")
    final class ClientError implements SmaxGroupsBatchGetGroupInfoResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsBatchGetGroupInfoResponseClientError",
                exports = "parseBatchGetGroupInfoResponseClientError",
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
            return "SmaxGroupsBatchGetGroupInfoResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsBatchGetGroupInfoResponseServerError")
    final class ServerError implements SmaxGroupsBatchGetGroupInfoResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsBatchGetGroupInfoResponseServerError",
                exports = "parseBatchGetGroupInfoResponseServerError",
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
            return "SmaxGroupsBatchGetGroupInfoResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
