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
 * The sealed reply family for a {@link SmaxGroupsAcceptGroupAddRequest}.
 *
 * @apiNote The four variants mirror the WA Web RPC dispatcher's
 * {@code GroupJoinRequestSuccess}/{@code Success}/{@code ClientError}/{@code ServerError} cases:
 * {@link GroupJoinRequestSuccess} means the relay accepted the {@code accept} but the group's membership-approval
 * mode rerouted the caller into the pending-approval queue, {@link Success} means the caller has joined the group
 * directly, and the two error variants surface the relay's reason codes. Call {@link #of(Node, Node)} on the
 * inbound IQ to land on the right variant; the {@code joinGroupViaInviteV4} caller in
 * {@code WAWebGroupInviteV4Job} uses the same dispatch shape.
 */
public sealed interface SmaxGroupsAcceptGroupAddResponse extends SmaxOperation.Response
        permits SmaxGroupsAcceptGroupAddResponse.GroupJoinRequestSuccess, SmaxGroupsAcceptGroupAddResponse.Success,
        SmaxGroupsAcceptGroupAddResponse.ClientError, SmaxGroupsAcceptGroupAddResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsAcceptGroupAddResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsAcceptGroupAddRPC}:
     * {@link GroupJoinRequestSuccess} is tried first because its {@code <membership_approval_request/>} child
     * discriminates it from the bare {@link Success}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the four documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound {@link SmaxGroupsAcceptGroupAddRequest} stanza, used to validate
     *                echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsAcceptGroupAddRPC",
            exports = "sendAcceptGroupAddRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsAcceptGroupAddResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var groupJoin = GroupJoinRequestSuccess.of(node, request);
        if (groupJoin.isPresent()) {
            return groupJoin;
        }
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
     * The reply variant emitted when the relay accepted the {@code accept} but the group's membership-approval
     * mode rerouted the caller into the pending-approval queue.
     *
     * @apiNote Surfaces as the {@code AcceptGroupAddResponseGroupJoinRequestSuccess} case in
     * {@code WAWebGroupInviteV4Job}: the caller is not yet a participant but the relay records the request,
     * and a group admin must approve it.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsAcceptGroupAddResponseGroupJoinRequestSuccess")
    final class GroupJoinRequestSuccess implements SmaxGroupsAcceptGroupAddResponse {
        /**
         * Constructs a marker {@link GroupJoinRequestSuccess}.
         *
         * @apiNote The instance carries no payload; the discriminator is solely the presence of the
         * {@code <membership_approval_request/>} child on the IQ.
         */
        public GroupJoinRequestSuccess() {
        }

        /**
         * Tries to parse a {@link GroupJoinRequestSuccess} variant from {@code node}.
         *
         * @apiNote Matches the WA Web parser {@code parseAcceptGroupAddResponseGroupJoinRequestSuccess}: the IQ
         * must be a valid {@code type="result"} echo of the request and must carry a
         * {@code <membership_approval_request/>} child.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsAcceptGroupAddResponseGroupJoinRequestSuccess",
                exports = "parseAcceptGroupAddResponseGroupJoinRequestSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<GroupJoinRequestSuccess> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            if (node.getChild("membership_approval_request").isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new GroupJoinRequestSuccess());
        }

        /**
         * Compares this marker to {@code obj} for value equality.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link GroupJoinRequestSuccess}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * Returns a constant hash shared by every {@link GroupJoinRequestSuccess} instance.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return GroupJoinRequestSuccess.class.hashCode();
        }

        /**
         * Returns the marker's debug representation.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsAcceptGroupAddResponse.GroupJoinRequestSuccess[]";
        }
    }

    /**
     * The reply variant emitted when the relay admitted the caller into the group as a regular participant.
     *
     * @apiNote Surfaces as the {@code AcceptGroupAddResponseSuccess} case in {@code WAWebGroupInviteV4Job};
     * the caller's UI receives a confirmation toast and the local chat row materialises immediately.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsAcceptGroupAddResponseSuccess")
    final class Success implements SmaxGroupsAcceptGroupAddResponse {
        /**
         * Constructs a marker {@link Success}.
         *
         * @apiNote The instance carries no payload; the discriminator is the absence of the
         * {@code <membership_approval_request/>} child.
         */
        public Success() {
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Matches the WA Web parser {@code parseAcceptGroupAddResponseSuccess}: the IQ must be a valid
         * {@code type="result"} echo of the request and must NOT carry a {@code <membership_approval_request/>}
         * child, otherwise the {@link GroupJoinRequestSuccess} branch wins.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsAcceptGroupAddResponseSuccess",
                exports = "parseAcceptGroupAddResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            if (node.getChild("membership_approval_request").isPresent()) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        /**
         * Compares this marker to {@code obj} for value equality.
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
         * Returns a constant hash shared by every {@link Success} instance.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        /**
         * Returns the marker's debug representation.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsAcceptGroupAddResponse.Success[]";
        }
    }

    /**
     * The reply variant emitted when the relay rejected the {@code accept} as malformed, expired, or referencing
     * a non-existent pending request.
     *
     * @apiNote Surfaces as the {@code AcceptGroupAddResponseClientError} case in {@code WAWebGroupInviteV4Job},
     * which logs the {@link #errorCode()} as the HTTP-style status passed back to the join-via-invite UI.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsAcceptGroupAddResponseClientError")
    final class ClientError implements SmaxGroupsAcceptGroupAddResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsAcceptGroupAddResponseClientError",
                exports = "parseAcceptGroupAddResponseClientError",
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
            return "SmaxGroupsAcceptGroupAddResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     *
     * @apiNote Surfaces as the {@code AcceptGroupAddResponseServerError} case in {@code WAWebGroupInviteV4Job},
     * where it is logged at the same severity as {@link ClientError} but typically signals retry-eligible
     * relay outages rather than caller error.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsAcceptGroupAddResponseServerError")
    final class ServerError implements SmaxGroupsAcceptGroupAddResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsAcceptGroupAddResponseServerError",
                exports = "parseAcceptGroupAddResponseServerError",
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
            return "SmaxGroupsAcceptGroupAddResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
