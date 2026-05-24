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
 * The sealed reply family for a {@link SmaxGroupsUnlinkGroupsRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher in {@code WASmaxGroupsUnlinkGroupsRPC}.
 * {@link Success} always wraps the per-sub-group result rows returned by the relay; individual rows may carry an
 * {@link Success.UnlinkedGroup#errorTag()} signalling a per-sub-group failure even when the envelope is
 * successful, so callers must walk the list to detect partial failures.
 */
public sealed interface SmaxGroupsUnlinkGroupsResponse extends SmaxOperation.Response
        permits SmaxGroupsUnlinkGroupsResponse.Success, SmaxGroupsUnlinkGroupsResponse.ClientError, SmaxGroupsUnlinkGroupsResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsUnlinkGroupsResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsUnlinkGroupsRPC}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound {@link SmaxGroupsUnlinkGroupsRequest} stanza, used to validate echoed
     *                identifiers
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsUnlinkGroupsRPC",
            exports = "sendUnlinkGroupsRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsUnlinkGroupsResponse> of(Node node, Node request) {
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
     * The reply variant carrying the per-sub-group result rows when the relay accepted the request envelope.
     *
     * @apiNote The IQ envelope succeeds even when individual rows carry an
     * {@link UnlinkedGroup#errorTag()} signalling a per-sub-group failure (one of the six tags listed on
     * {@link UnlinkedGroup}); callers must walk {@link #unlinkedGroups()} to detect partial failures.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsUnlinkGroupsResponseSuccess")
    final class Success implements SmaxGroupsUnlinkGroupsResponse {
        /**
         * The per-sub-group result rows projected from the {@code <unlink>} child.
         */
        private final List<UnlinkedGroup> unlinkedGroups;

        /**
         * Constructs a {@link Success}.
         *
         * @param unlinkedGroups the per-sub-group result rows
         * @throws NullPointerException if {@code unlinkedGroups} is {@code null}
         */
        public Success(List<UnlinkedGroup> unlinkedGroups) {
            Objects.requireNonNull(unlinkedGroups, "unlinkedGroups cannot be null");
            this.unlinkedGroups = List.copyOf(unlinkedGroups);
        }

        /**
         * Returns the per-sub-group result rows.
         *
         * @return an unmodifiable list of {@link UnlinkedGroup} entries; never {@code null}
         */
        public List<UnlinkedGroup> unlinkedGroups() {
            return unlinkedGroups;
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Matches the WA Web parser {@code parseUnlinkGroupsResponseSuccess}: the IQ must be a valid
         * {@code type="result"} echo of the request, must carry an {@code <unlink unlink_type="sub_group">}
         * child, and the child must contain at least one {@code <group>} grand-child carrying a {@code jid}
         * attribute.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsUnlinkGroupsResponseSuccess",
                exports = "parseUnlinkGroupsResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var unlink = node.getChild("unlink").orElse(null);
            if (unlink == null) {
                return Optional.empty();
            }
            if (!unlink.hasAttribute("unlink_type", "sub_group")) {
                return Optional.empty();
            }
            var unlinkedGroups = new ArrayList<UnlinkedGroup>();
            for (var groupNode : unlink.getChildren("group")) {
                var jid = groupNode.getAttributeAsJid("jid").orElse(null);
                if (jid == null) {
                    return Optional.empty();
                }
                var removeOrphaned = groupNode.hasAttribute("remove_orphaned_members", "true");
                String errorTag = null;
                for (var child : groupNode.children()) {
                    var description = child.description();
                    if (description == null) {
                        continue;
                    }
                    if (UnlinkedGroup.isErrorTag(description)) {
                        errorTag = description;
                        break;
                    }
                }
                unlinkedGroups.add(new UnlinkedGroup(jid, removeOrphaned, errorTag));
            }
            if (unlinkedGroups.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Success(unlinkedGroups));
        }

        /**
         * Compares this success to {@code obj} for value equality on {@link #unlinkedGroups()}.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Success} with the same result rows
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
            return Objects.equals(this.unlinkedGroups, that.unlinkedGroups);
        }

        /**
         * Returns a hash derived from {@link #unlinkedGroups()}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(unlinkedGroups);
        }

        /**
         * Returns a debug string carrying {@link #unlinkedGroups()}.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsUnlinkGroupsResponse.Success[unlinkedGroups=" + unlinkedGroups + ']';
        }

        /**
         * Per-sub-group result row inside a {@link Success}.
         *
         * @apiNote {@link #errorTag()} captures the per-sub-group discriminator emitted by the relay when an
         * individual unlink fails; values mirror the WA Web mixin family:
         * <ul>
         *   <li>{@code "bad_request"}</li>
         *   <li>{@code "not_authorized"}</li>
         *   <li>{@code "not_exist"}</li>
         *   <li>{@code "not_acceptable"}</li>
         *   <li>{@code "partial_server_error"}</li>
         *   <li>{@code "server_error"}</li>
         * </ul>
         * Empty when the unlink succeeded for that sub-group.
         */
        @WhatsAppWebModule(moduleName = "WASmaxInGroupsUnlinkGroupsResponseSuccess")
        @WhatsAppWebModule(moduleName = "WASmaxInGroupsSubGroupBadRequestOrNotAuthorizedOrNotExistOrNotAcceptableOrPartialServerErrorOrServerErrorMixinGroup")
        public static final class UnlinkedGroup {
            /**
             * Returns whether {@code description} is one of the documented sub-group error discriminator tags.
             *
             * @param description the child tag to test
             * @return {@code true} when the tag is a sub-group error discriminator
             */
            private static boolean isErrorTag(String description) {
                return "bad_request".equals(description)
                        || "not_authorized".equals(description)
                        || "not_exist".equals(description)
                        || "not_acceptable".equals(description)
                        || "partial_server_error".equals(description)
                        || "server_error".equals(description);
            }

            /**
             * The sub-group {@link Jid} echoed by the relay.
             */
            private final Jid jid;

            /**
             * Whether the relay echoed the {@code remove_orphaned_members="true"} flag.
             */
            private final boolean removeOrphanedMembers;

            /**
             * The optional per-sub-group error-discriminator tag.
             */
            private final String errorTag;

            /**
             * Constructs an {@link UnlinkedGroup} result row.
             *
             * @param jid                   the sub-group {@link Jid}
             * @param removeOrphanedMembers whether the relay echoed the eviction flag
             * @param errorTag              the optional error-discriminator tag; may be {@code null}
             * @throws NullPointerException if {@code jid} is {@code null}
             */
            public UnlinkedGroup(Jid jid, boolean removeOrphanedMembers, String errorTag) {
                this.jid = Objects.requireNonNull(jid, "jid cannot be null");
                this.removeOrphanedMembers = removeOrphanedMembers;
                this.errorTag = errorTag;
            }

            /**
             * Returns the sub-group {@link Jid}.
             *
             * @return the sub-group {@link Jid}; never {@code null}
             */
            public Jid jid() {
                return jid;
            }

            /**
             * Returns whether the eviction flag was echoed.
             *
             * @return {@code true} when the {@code remove_orphaned_members="true"} attribute is present
             */
            public boolean removeOrphanedMembers() {
                return removeOrphanedMembers;
            }

            /**
             * Returns the optional per-sub-group error-discriminator tag.
             *
             * @return an {@link Optional} carrying the tag, or empty when the unlink succeeded for this
             *         sub-group
             */
            public Optional<String> errorTag() {
                return Optional.ofNullable(errorTag);
            }

            /**
             * Compares this row to {@code obj} for value equality across every field.
             *
             * @param obj the other object
             * @return {@code true} when {@code obj} is an {@link UnlinkedGroup} with identical fields
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (UnlinkedGroup) obj;
                return this.removeOrphanedMembers == that.removeOrphanedMembers
                        && Objects.equals(this.jid, that.jid)
                        && Objects.equals(this.errorTag, that.errorTag);
            }

            /**
             * Returns a hash composed of every field.
             *
             * @return the hash code
             */
            @Override
            public int hashCode() {
                return Objects.hash(jid, removeOrphanedMembers, errorTag);
            }

            /**
             * Returns a debug string carrying every field.
             *
             * @return the debug representation
             */
            @Override
            public String toString() {
                return "SmaxGroupsUnlinkGroupsResponse.Success.UnlinkedGroup[jid=" + jid
                        + ", removeOrphanedMembers=" + removeOrphanedMembers
                        + ", errorTag=" + errorTag + ']';
            }
        }
    }

    /**
     * The reply variant emitted when the relay rejected the request envelope as malformed, unauthorised, or
     * referencing a non-existent parent or sub-group pairing.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsUnlinkGroupsResponseClientError")
    final class ClientError implements SmaxGroupsUnlinkGroupsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsUnlinkGroupsResponseClientError",
                exports = "parseUnlinkGroupsResponseClientError",
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
            return "SmaxGroupsUnlinkGroupsResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsUnlinkGroupsResponseServerError")
    final class ServerError implements SmaxGroupsUnlinkGroupsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsUnlinkGroupsResponseServerError",
                exports = "parseUnlinkGroupsResponseServerError",
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
            return "SmaxGroupsUnlinkGroupsResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
