package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed reply family for a {@link SmaxGroupsGetInviteGroupInfoRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher's {@code Success}/{@code ClientError}/{@code ServerError}
 * cases: {@link Success} carries the inviting group's preview metadata (size plus the raw {@code <group/>} subtree),
 * the two error variants surface the relay's reason codes. The {@code WAWebGroupQueryJob.queryGroupInviteCode}
 * caller in WA Web uses the same dispatch shape.
 */
public sealed interface SmaxGroupsGetInviteGroupInfoResponse extends SmaxOperation.Response
        permits SmaxGroupsGetInviteGroupInfoResponse.Success, SmaxGroupsGetInviteGroupInfoResponse.ClientError, SmaxGroupsGetInviteGroupInfoResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsGetInviteGroupInfoResponse} variant in priority order
     * and returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsGetInviteGroupInfoRPC}:
     * {@link Success} first, then {@link ClientError} (HTTP 4xx codes), then {@link ServerError} (HTTP 5xx codes).
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the three documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node the inbound IQ stanza received from the relay
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetInviteGroupInfoRPC",
            exports = "sendGetInviteGroupInfoRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsGetInviteGroupInfoResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        var success = Success.of(node);
        if (success.isPresent()) {
            return success;
        }
        var clientError = ClientError.of(node);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node);
    }

    /**
     * The reply variant emitted when the relay accepted the invite code and returned the inviting group's preview.
     *
     * @apiNote Surfaces as the {@code GetInviteGroupInfoResponseSuccess} case in {@code WAWebGroupQueryJob};
     * the caller's "preview group from invite link" UI binds subject, picture, owner, and admin list from
     * {@link #group()} and the participant count from {@link #groupSize()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetInviteGroupInfoResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsInviteLinkGroupInfoMixin")
    final class Success implements SmaxGroupsGetInviteGroupInfoResponse {
        /**
         * The participant count reported by the relay; clamped server-side to {@code [0, 19999]}.
         */
        private final int groupSize;

        /**
         * The raw {@code <group/>} subtree carrying the {@code InviteLinkGroupInfoMixin} payload.
         */
        private final Node group;

        /**
         * Constructs a {@link Success} reply.
         *
         * @apiNote {@code groupSize} mirrors the relay's clamp; passing {@code 0} is valid for an empty preview.
         *
         * @param groupSize the participant count
         * @param group     the {@code <group/>} sub-node; never {@code null}
         * @throws NullPointerException     if {@code group} is {@code null}
         * @throws IllegalArgumentException if {@code groupSize} is negative
         */
        public Success(int groupSize, Node group) {
            if (groupSize < 0) {
                throw new IllegalArgumentException("groupSize must be non-negative");
            }
            this.groupSize = groupSize;
            this.group = Objects.requireNonNull(group, "group cannot be null");
        }

        /**
         * Returns the participant count reported by the relay.
         *
         * @return the group size
         */
        public int groupSize() {
            return groupSize;
        }

        /**
         * Returns the raw {@code <group/>} sub-node carrying the {@code InviteLinkGroupInfoMixin} payload.
         *
         * @apiNote Cobalt exposes the subtree verbatim so callers project subject, picture, owner, and admin list
         * directly via {@link Node#getAttributeAsString(String)} without committing to the full mixin schema.
         *
         * @return the {@code <group/>} node; never {@code null}
         */
        public Node group() {
            return group;
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Matches when the IQ is a {@code type="result"} envelope carrying a {@code <group>} child; the
         * {@code size} attribute defaults to {@code 0} when the relay omits it.
         *
         * @param node the inbound IQ stanza
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetInviteGroupInfoResponseSuccess",
                exports = "parseGetInviteGroupInfoResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "result")) {
                return Optional.empty();
            }
            var group = node.getChild("group").orElse(null);
            if (group == null) {
                return Optional.empty();
            }
            var size = group.getAttributeAsInt("size").orElse(0);
            var success = new Success(size, group);
            return Optional.of(success);
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
            return this.groupSize == that.groupSize && Objects.equals(this.group, that.group);
        }

        /**
         * Returns a hash composed of every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(groupSize, group);
        }

        /**
         * Returns a debug string carrying every field.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetInviteGroupInfoResponse.Success[groupSize=" + groupSize
                    + ", group=" + group + ']';
        }
    }

    /**
     * The reply variant emitted when the relay rejected the request as malformed, unauthorised, or referencing a
     * revoked or non-existent invite code.
     *
     * @apiNote Surfaces as the {@code GetInviteGroupInfoResponseClientError} case in {@code WAWebGroupQueryJob};
     * the caller's UI typically maps the {@link #errorCode()} into a "this invite link is no longer valid" toast.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetInviteGroupInfoResponseClientError")
    final class ClientError implements SmaxGroupsGetInviteGroupInfoResponse {
        /**
         * The numeric error code echoed by the relay (HTTP-style 4xx).
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
         * @apiNote Matches when the IQ is a {@code type="error"} envelope carrying an {@code <error code="4xx"/>} child.
         *
         * @param node the inbound IQ stanza
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetInviteGroupInfoResponseClientError",
                exports = "parseGetInviteGroupInfoResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "error")) {
                return Optional.empty();
            }
            var error = node.getChild("error").orElse(null);
            if (error == null) {
                return Optional.empty();
            }
            var code = error.getAttributeAsInt("code").orElse(-1);
            if (code < 400 || code >= 500) {
                return Optional.empty();
            }
            var text = error.getAttributeAsString("text").orElse(null);
            var clientError = new ClientError(code, text);
            return Optional.of(clientError);
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
            return "SmaxGroupsGetInviteGroupInfoResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     *
     * @apiNote Surfaces as the {@code GetInviteGroupInfoResponseServerError} case in {@code WAWebGroupQueryJob},
     * where it is typically logged and surfaced as a retry-eligible relay outage rather than caller error.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetInviteGroupInfoResponseServerError")
    final class ServerError implements SmaxGroupsGetInviteGroupInfoResponse {
        /**
         * The numeric error code echoed by the relay (HTTP-style 5xx).
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
         * @apiNote Matches when the IQ is a {@code type="error"} envelope carrying an {@code <error code="5xx"/>} child.
         *
         * @param node the inbound IQ stanza
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetInviteGroupInfoResponseServerError",
                exports = "parseGetInviteGroupInfoResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "error")) {
                return Optional.empty();
            }
            var error = node.getChild("error").orElse(null);
            if (error == null) {
                return Optional.empty();
            }
            var code = error.getAttributeAsInt("code").orElse(-1);
            if (code < 500) {
                return Optional.empty();
            }
            var text = error.getAttributeAsString("text").orElse(null);
            var serverError = new ServerError(code, text);
            return Optional.of(serverError);
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
            return "SmaxGroupsGetInviteGroupInfoResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
