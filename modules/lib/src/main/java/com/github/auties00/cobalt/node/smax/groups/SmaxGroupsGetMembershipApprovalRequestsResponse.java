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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed reply family for a {@link SmaxGroupsGetMembershipApprovalRequestsRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher's {@code Success}/{@code ClientError}/{@code ServerError}
 * cases: {@link Success} carries the pending {@code <membership_approval_request>} entries that drive the admin
 * "Pending requests" surface, the two error variants surface the relay's reason codes. The
 * {@code WAWebGroupGetMembershipApprovalRequestsJob} caller in WA Web uses the same dispatch shape.
 */
public sealed interface SmaxGroupsGetMembershipApprovalRequestsResponse extends SmaxOperation.Response
        permits SmaxGroupsGetMembershipApprovalRequestsResponse.Success, SmaxGroupsGetMembershipApprovalRequestsResponse.ClientError, SmaxGroupsGetMembershipApprovalRequestsResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsGetMembershipApprovalRequestsResponse} variant in priority
     * order and returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in
     * {@code WASmaxGroupsGetMembershipApprovalRequestsRPC}: {@link Success} first, then {@link ClientError}, then
     * {@link ServerError}.
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
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetMembershipApprovalRequestsRPC",
            exports = "sendGetMembershipApprovalRequestsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsGetMembershipApprovalRequestsResponse> of(Node node, Node request) {
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
     * The reply variant emitted when the relay returned the pending approval queue.
     *
     * @apiNote Surfaces as the {@code GetMembershipApprovalRequestsResponseSuccess} case in
     * {@code WAWebGroupGetMembershipApprovalRequestsJob}; the admin "Pending requests" UI iterates {@link #approvals()}
     * and uses {@link #requestorFetch()} to decide whether to render the rich requestor identity. WA Web normalises
     * the request-method strings ({@code "InviteLink"}, {@code "LinkedGroupJoin"}, {@code "NonAdminAdd"}) into the
     * {@code WAWebRequestMethodType.RequestMethod} enum.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsRequestorFetchMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupAddressingModeMixin")
    final class Success implements SmaxGroupsGetMembershipApprovalRequestsResponse {
        /**
         * The pending membership-approval entries.
         */
        private final List<Approval> approvals;

        /**
         * The echo of the {@code requestor_fetch="true"} attribute on the wrapper.
         */
        private final boolean requestorFetch;

        /**
         * Constructs a {@link Success} reply.
         *
         * @param approvals      the pending {@link Approval} entries; never {@code null}
         * @param requestorFetch whether the relay echoed the rich requestor projection
         * @throws NullPointerException if {@code approvals} is {@code null}
         */
        public Success(List<Approval> approvals, boolean requestorFetch) {
            Objects.requireNonNull(approvals, "approvals cannot be null");
            this.approvals = List.copyOf(approvals);
            this.requestorFetch = requestorFetch;
        }

        /**
         * Returns the pending {@link Approval} entries.
         *
         * @return an unmodifiable list of approval entries; never {@code null}
         */
        public List<Approval> approvals() {
            return approvals;
        }

        /**
         * Returns whether the relay echoed the rich requestor projection.
         *
         * @return {@code true} when the {@code requestor_fetch="true"} attribute was present on the wrapper
         */
        public boolean requestorFetch() {
            return requestorFetch;
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxIqResultResponseMixin#validate(Node, Node)} for envelope validation, then
         * matches the {@code <membership_approval_requests>} wrapper holding zero or more
         * {@code <membership_approval_request/>} entries.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsResponseSuccess",
                exports = "parseGetMembershipApprovalRequestsResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var wrapper = node.getChild("membership_approval_requests").orElse(null);
            if (wrapper == null) {
                return Optional.empty();
            }
            var requestorFetch = wrapper.hasAttribute("requestor_fetch", "true");
            var approvalNodes = wrapper.getChildren("membership_approval_request");
            var approvals = new ArrayList<Approval>(approvalNodes.size());
            for (var approvalNode : approvalNodes) {
                var approval = Approval.of(approvalNode).orElse(null);
                if (approval == null) {
                    return Optional.empty();
                }
                approvals.add(approval);
            }
            return Optional.of(new Success(approvals, requestorFetch));
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
            return this.requestorFetch == that.requestorFetch
                    && Objects.equals(this.approvals, that.approvals);
        }

        /**
         * Returns a hash composed of every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(approvals, requestorFetch);
        }

        /**
         * Returns a debug string carrying every field.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetMembershipApprovalRequestsResponse.Success[approvals="
                    + approvals + ", requestorFetch=" + requestorFetch + ']';
        }
    }

    /**
     * Per-approval projection carrying the requesting user's JID plus the optional resolved identity attributes,
     * the request timestamp, and the optional request-method enum token.
     *
     * @apiNote Mirrors the {@code MembershipApprovalRequestMixin} shape; the {@link #requestor()},
     * {@link #requestorPn()}, {@link #requestorUsername()}, and {@link #parentGroupJid()} fields are populated only
     * when the request was issued with {@code requestor_fetch="true"} and the requestor identity differs from the
     * displayed {@link #jid()} (community sub-group join requests). The {@link #requestMethod()} token is one of
     * {@code "InviteLink"}, {@code "LinkedGroupJoin"}, or {@code "NonAdminAdd"}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsMembershipApprovalRequestMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsMembershipRequestMethodAttributeMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsIdentityMixin")
    final class Approval {
        /**
         * The requesting user's primary {@link Jid}.
         */
        private final Jid jid;

        /**
         * The optional resolved requestor {@link Jid} surfaced on the rich projection.
         */
        private final Jid requestor;

        /**
         * The optional resolved requestor phone-number {@link Jid} surfaced on the rich projection.
         */
        private final Jid requestorPn;

        /**
         * The optional resolved requestor username surfaced on the rich projection.
         */
        private final String requestorUsername;

        /**
         * The optional parent-community {@link Jid} surfaced on the rich projection for community-link join requests.
         */
        private final Jid parentGroupJid;

        /**
         * The unix-seconds timestamp at which the request was filed.
         */
        private final long requestTime;

        /**
         * The optional request-method enum token (one of {@code "InviteLink"}, {@code "LinkedGroupJoin"},
         * {@code "NonAdminAdd"}).
         */
        private final String requestMethod;

        /**
         * Constructs an {@link Approval} projection.
         *
         * @param jid               the requesting user's {@link Jid}; never {@code null}
         * @param requestor         the optional resolved requestor {@link Jid}; may be {@code null}
         * @param requestorPn       the optional resolved requestor phone-number {@link Jid}; may be {@code null}
         * @param requestorUsername the optional resolved requestor username; may be {@code null}
         * @param parentGroupJid    the optional parent-community {@link Jid}; may be {@code null}
         * @param requestTime       the unix-seconds timestamp
         * @param requestMethod     the optional request-method enum token; may be {@code null}
         * @throws NullPointerException     if {@code jid} is {@code null}
         * @throws IllegalArgumentException if {@code requestTime} is negative
         */
        public Approval(Jid jid, Jid requestor, Jid requestorPn, String requestorUsername,
                        Jid parentGroupJid, long requestTime, String requestMethod) {
            this.jid = Objects.requireNonNull(jid, "jid cannot be null");
            if (requestTime < 0) {
                throw new IllegalArgumentException("requestTime must be non-negative");
            }
            this.requestor = requestor;
            this.requestorPn = requestorPn;
            this.requestorUsername = requestorUsername;
            this.parentGroupJid = parentGroupJid;
            this.requestTime = requestTime;
            this.requestMethod = requestMethod;
        }

        /**
         * Returns the requesting user's primary {@link Jid}.
         *
         * @return the JID; never {@code null}
         */
        public Jid jid() {
            return jid;
        }

        /**
         * Returns the resolved requestor {@link Jid} when supplied by the relay.
         *
         * @return an {@link Optional} carrying the requestor JID, or empty when omitted
         */
        public Optional<Jid> requestor() {
            return Optional.ofNullable(requestor);
        }

        /**
         * Returns the resolved requestor phone-number {@link Jid} when supplied by the relay.
         *
         * @return an {@link Optional} carrying the requestor phone-number JID, or empty when omitted
         */
        public Optional<Jid> requestorPn() {
            return Optional.ofNullable(requestorPn);
        }

        /**
         * Returns the resolved requestor username when supplied by the relay.
         *
         * @return an {@link Optional} carrying the username, or empty when omitted
         */
        public Optional<String> requestorUsername() {
            return Optional.ofNullable(requestorUsername);
        }

        /**
         * Returns the parent-community {@link Jid} when supplied by the relay.
         *
         * @return an {@link Optional} carrying the parent-community JID, or empty when omitted
         */
        public Optional<Jid> parentGroupJid() {
            return Optional.ofNullable(parentGroupJid);
        }

        /**
         * Returns the request timestamp.
         *
         * @return the unix-seconds timestamp
         */
        public long requestTime() {
            return requestTime;
        }

        /**
         * Returns the request-method enum token when supplied by the relay.
         *
         * @apiNote Tokens are one of {@code "InviteLink"}, {@code "LinkedGroupJoin"}, or {@code "NonAdminAdd"}; WA Web
         * maps these onto its {@code WAWebRequestMethodType.RequestMethod} enum.
         *
         * @return an {@link Optional} carrying the enum token, or empty when omitted
         */
        public Optional<String> requestMethod() {
            return Optional.ofNullable(requestMethod);
        }

        /**
         * Tries to parse an {@link Approval} from the given {@code <membership_approval_request/>} child.
         *
         * @apiNote Matches when the child carries the {@code jid} and {@code request_time} attributes; the
         * {@code requestor}/{@code requestor_pn}/{@code requestor_username}/{@code parent_group_jid}/
         * {@code request_method} attributes are optional and only populated on the rich projection.
         *
         * @param node the {@code <membership_approval_request/>} child node
         * @return an {@link Optional} carrying the parsed approval, or empty when the child does not match
         */
        public static Optional<Approval> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("membership_approval_request")) {
                return Optional.empty();
            }
            var jid = node.getAttributeAsJid("jid").orElse(null);
            if (jid == null) {
                return Optional.empty();
            }
            var requestTimeOptional = node.getAttributeAsLong("request_time");
            if (requestTimeOptional.isEmpty()) {
                return Optional.empty();
            }
            var requestor = node.getAttributeAsJid("requestor").orElse(null);
            var requestorPn = node.getAttributeAsJid("requestor_pn").orElse(null);
            var requestorUsername = node.getAttributeAsString("requestor_username").orElse(null);
            var parentGroupJid = node.getAttributeAsJid("parent_group_jid").orElse(null);
            var requestMethod = node.getAttributeAsString("request_method").orElse(null);
            var approval = new Approval(jid, requestor, requestorPn, requestorUsername,
                    parentGroupJid, requestTimeOptional.getAsLong(), requestMethod);
            return Optional.of(approval);
        }

        /**
         * Compares this approval to {@code obj} for value equality across every field.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is an {@link Approval} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Approval) obj;
            return this.requestTime == that.requestTime
                    && Objects.equals(this.jid, that.jid)
                    && Objects.equals(this.requestor, that.requestor)
                    && Objects.equals(this.requestorPn, that.requestorPn)
                    && Objects.equals(this.requestorUsername, that.requestorUsername)
                    && Objects.equals(this.parentGroupJid, that.parentGroupJid)
                    && Objects.equals(this.requestMethod, that.requestMethod);
        }

        /**
         * Returns a hash composed of every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(jid, requestor, requestorPn, requestorUsername,
                    parentGroupJid, requestTime, requestMethod);
        }

        /**
         * Returns a debug string carrying every field.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetMembershipApprovalRequestsResponse.Approval[jid=" + jid
                    + ", requestor=" + requestor
                    + ", requestorPn=" + requestorPn
                    + ", requestorUsername=" + requestorUsername
                    + ", parentGroupJid=" + parentGroupJid
                    + ", requestTime=" + requestTime
                    + ", requestMethod=" + requestMethod + ']';
        }
    }

    /**
     * The reply variant emitted when the relay rejected the request as malformed, unauthorised, or referencing a
     * non-existent group.
     *
     * @apiNote Surfaces as the {@code GetMembershipApprovalRequestsResponseClientError} case in
     * {@code WAWebGroupGetMembershipApprovalRequestsJob}, which logs the {@link #errorCode()} as the HTTP-style
     * status passed back to the "Pending requests" admin UI.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsResponseClientError")
    final class ClientError implements SmaxGroupsGetMembershipApprovalRequestsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsResponseClientError",
                exports = "parseGetMembershipApprovalRequestsResponseClientError",
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
            return "SmaxGroupsGetMembershipApprovalRequestsResponse.ClientError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     *
     * @apiNote Surfaces as the {@code GetMembershipApprovalRequestsResponseServerError} case in
     * {@code WAWebGroupGetMembershipApprovalRequestsJob}, where it is logged at the same severity as
     * {@link ClientError} but typically signals retry-eligible relay outages rather than caller error.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsResponseServerError")
    final class ServerError implements SmaxGroupsGetMembershipApprovalRequestsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetMembershipApprovalRequestsResponseServerError",
                exports = "parseGetMembershipApprovalRequestsResponseServerError",
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
            return "SmaxGroupsGetMembershipApprovalRequestsResponse.ServerError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }
}
