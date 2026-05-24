package com.github.auties00.cobalt.node.iq.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound reply variants the relay produces in
 * response to an {@link IqJoinGroupByInviteCodeRequest}.
 *
 * @apiNote
 * After dispatching the join request, pattern-match the returned
 * variant: {@link Success} carries the group JID and a flag
 * indicating whether the caller was admitted immediately or queued
 * for moderator approval; {@link UnexpectedJoinShape} surfaces a
 * mismatch between the caller's expected gating mode and the relay's
 * actual reply shape; and {@link ClientError} / {@link ServerError}
 * surface envelope-level rejections (typically {@code 401} for an
 * expired/revoked code, {@code 403} for a banned caller, or
 * {@code 404} for a deleted group).
 *
 * @implNote
 * This implementation collapses WA Web's
 * {@code joinGroupViaInviteParser} return shape plus the
 * {@code UnexpectedJoinGroupViaInviteResponse} exception thrown
 * inside the parser into a single sealed sum, so callers can
 * pattern-match a closed set of variants instead of catching a
 * sibling exception.
 */
@WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
public sealed interface IqJoinGroupByInviteCodeResponse extends IqOperation.Response
        permits IqJoinGroupByInviteCodeResponse.Success, IqJoinGroupByInviteCodeResponse.UnexpectedJoinShape,
                IqJoinGroupByInviteCodeResponse.ClientError, IqJoinGroupByInviteCodeResponse.ServerError {

    /**
     * Tries each {@link IqJoinGroupByInviteCodeResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Use this when dispatching through the typed {@link IqOperation}
     * pipeline; the dispatcher hands the inbound {@link Node}
     * together with the original outbound request so that the parser
     * can correlate echoed identifiers and approval-mode expectations.
     * Returns {@link Optional#empty()} when none of the documented
     * shapes match, which the caller should treat as an unknown
     * server reply and surface up.
     *
     * @implNote
     * This implementation tries {@link Success} first, then
     * {@link UnexpectedJoinShape}, then {@link ClientError}, then
     * {@link ServerError}; the order matches WA Web's parser where
     * the expected gating mode is asserted before the unexpected-shape
     * branch is taken, and where the result branch is always
     * inspected before the error envelope.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza used to validate echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
            exports = "joinGroupViaInvite",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqJoinGroupByInviteCodeResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var unexpected = UnexpectedJoinShape.of(node, request);
        if (unexpected.isPresent()) {
            return unexpected;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * The {@code Success} reply variant.
     *
     * @apiNote
     * Carries the group {@link Jid} the caller joined (or whose
     * approval was queued) and the
     * {@link #isMembershipApprovalPending()} flag that discriminates
     * the two paths: {@code true} means the request is queued for
     * moderator approval and the caller is not yet a member,
     * {@code false} means the caller is already in. WA Web's
     * {@code WAWebGroupInviteAction.joinGroupViaInvite} immediately
     * follows up with a {@code findOrCreateLatestChat} call on the
     * returned JID, which Cobalt callers should mirror to surface
     * the new chat in the user's chat list.
     *
     * @implNote
     * This implementation projects WA Web's
     * {@code joinGroupViaInviteParser} success shape ({@code {gid}})
     * plus the explicit {@code membership_approval_request} vs
     * {@code group} child discriminator into a typed pair of
     * accessors.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
    final class Success implements IqJoinGroupByInviteCodeResponse {
        /**
         * The group JID the caller joined or whose membership
         * approval was queued.
         */
        private final Jid groupJid;

        /**
         * {@code true} when the entry is queued for moderator
         * approval, {@code false} when the caller is already
         * admitted.
         */
        private final boolean membershipApprovalPending;

        /**
         * Constructs a {@link Success} reply.
         *
         * @param groupJid                  the group {@link Jid}; never {@code null}
         * @param membershipApprovalPending {@code true} when the entry is queued for approval
         * @throws NullPointerException if {@code groupJid} is {@code null}
         */
        public Success(Jid groupJid, boolean membershipApprovalPending) {
            this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
            this.membershipApprovalPending = membershipApprovalPending;
        }

        /**
         * Returns the group JID.
         *
         * @return the group {@link Jid}; never {@code null}
         */
        public Jid groupJid() {
            return groupJid;
        }

        /**
         * Returns whether the entry is queued for moderator approval.
         *
         * @return {@code true} when the request is queued for moderator approval, {@code false} when the caller has already been admitted
         */
        public boolean isMembershipApprovalPending() {
            return membershipApprovalPending;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqJoinGroupByInviteCodeResponse#of(Node, Node)};
         * this factory is exposed so callers can short-circuit when
         * they already know the wire shape is a success.
         *
         * @implNote
         * This implementation matches WA Web's
         * {@code joinGroupViaInviteParser} assertions: it requires
         * the IQ to be a {@code result} sent {@code from="g.us"}, then
         * tries the {@code <group>} child first (open-join shape) and
         * falls back to the {@code <membership_approval_request>}
         * child (approval-gated shape). Either branch reads the JID
         * from the child's {@code jid} attribute. The branch order
         * here differs from WA Web's parser, which switches on the
         * caller's expected mode; Cobalt instead tries the expected
         * branch as part of dispatch and lets
         * {@link UnexpectedJoinShape} surface mismatches.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
                exports = "joinGroupViaInvite",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var fromAttr = node.getAttributeAsJid("from").orElse(null);
            if (fromAttr == null || !"g.us".equals(fromAttr.toString())) {
                return Optional.empty();
            }
            var groupChild = node.getChild("group").orElse(null);
            if (groupChild != null) {
                var gid = groupChild.getAttributeAsJid("jid").orElse(null);
                if (gid == null) {
                    return Optional.empty();
                }
                return Optional.of(new Success(gid, false));
            }
            var approvalChild = node.getChild("membership_approval_request").orElse(null);
            if (approvalChild != null) {
                var gid = approvalChild.getAttributeAsJid("jid").orElse(null);
                if (gid == null) {
                    return Optional.empty();
                }
                return Optional.of(new Success(gid, true));
            }
            return Optional.empty();
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
            return this.membershipApprovalPending == that.membershipApprovalPending
                    && Objects.equals(this.groupJid, that.groupJid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupJid, membershipApprovalPending);
        }

        @Override
        public String toString() {
            return "IqJoinGroupByInviteCodeResponse.Success[groupJid=" + groupJid
                    + ", membershipApprovalPending=" + membershipApprovalPending + ']';
        }
    }

    /**
     * The {@code UnexpectedJoinShape} reply variant.
     *
     * @apiNote
     * Fires when the relay admits the caller through a different
     * gating mode than the request signalled via
     * {@link IqJoinGroupByInviteCodeRequest#expectsMembershipApproval()};
     * the upstream UI typically treats this as a soft-fail and
     * re-fetches the group metadata before re-rendering. Carries
     * both the actual group {@link Jid} the relay returned and the
     * actual gating mode the relay used, so callers can correct
     * their cached view of the group without issuing a second
     * round trip.
     *
     * @implNote
     * This implementation models the WA Web
     * {@code UnexpectedJoinGroupViaInviteResponse} custom error
     * thrown by {@code joinGroupViaInviteParser} when the
     * actually-present child differs from the caller's expected
     * child. Unlike WA Web (which raises an exception out of the
     * parser), Cobalt surfaces this as a sealed variant the caller
     * pattern-matches on.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
    final class UnexpectedJoinShape implements IqJoinGroupByInviteCodeResponse {
        /**
         * The group JID parsed from the alternate child.
         */
        private final Jid groupJid;

        /**
         * {@code true} when the relay actually returned a
         * {@code <membership_approval_request>}, {@code false} when
         * it returned a {@code <group>}.
         */
        private final boolean actualMembershipApprovalPending;

        /**
         * Constructs an {@link UnexpectedJoinShape} reply.
         *
         * @param groupJid                        the group {@link Jid}; never {@code null}
         * @param actualMembershipApprovalPending the actual gating mode used by the relay
         * @throws NullPointerException if {@code groupJid} is {@code null}
         */
        public UnexpectedJoinShape(Jid groupJid, boolean actualMembershipApprovalPending) {
            this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
            this.actualMembershipApprovalPending = actualMembershipApprovalPending;
        }

        /**
         * Returns the group JID parsed from the alternate child.
         *
         * @return the group {@link Jid}; never {@code null}
         */
        public Jid groupJid() {
            return groupJid;
        }

        /**
         * Returns the actual gating mode used by the relay.
         *
         * @return {@code true} when the relay queued for approval, {@code false} when it admitted directly
         */
        public boolean actualMembershipApprovalPending() {
            return actualMembershipApprovalPending;
        }

        /**
         * Returns {@link Optional#empty()} unconditionally.
         *
         * @apiNote
         * Detecting the unexpected-shape case requires comparing the
         * inbound reply against the caller's expected gating mode,
         * which is not available from the wire {@link Node} alone.
         * The dispatcher therefore constructs this variant directly
         * by comparing {@link Success#isMembershipApprovalPending()}
         * against
         * {@link IqJoinGroupByInviteCodeRequest#expectsMembershipApproval()};
         * this static factory exists so the parser fall-through chain
         * stays symmetric with the other sibling variants.
         *
         * @implNote
         * This implementation deliberately never returns a present
         * value. WA Web's parser detects the case by switching on the
         * caller's expected mode and raising
         * {@code UnexpectedJoinGroupViaInviteResponse} when the
         * actual child differs; Cobalt routes the equivalent
         * detection through the dispatcher rather than this factory
         * because the {@code node}/{@code request} pair does not
         * carry the caller's expectation flag.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return always {@link Optional#empty()}
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
                exports = "joinGroupViaInvite",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<UnexpectedJoinShape> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            return Optional.empty();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (UnexpectedJoinShape) obj;
            return this.actualMembershipApprovalPending == that.actualMembershipApprovalPending
                    && Objects.equals(this.groupJid, that.groupJid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupJid, actualMembershipApprovalPending);
        }

        @Override
        public String toString() {
            return "IqJoinGroupByInviteCodeResponse.UnexpectedJoinShape[groupJid=" + groupJid
                    + ", actualMembershipApprovalPending=" + actualMembershipApprovalPending + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant.
     *
     * @apiNote
     * Surfaces caller-side rejections of the invite redemption:
     * typically {@code 401} when the invite code is expired or
     * revoked, {@code 403} when the caller is banned from the
     * group, or {@code 404} when the group no longer exists. The
     * upstream UI surfaces a per-code-error message; retries are
     * not meaningful without first fetching a fresh invite link.
     *
     * @implNote
     * This implementation corresponds to the {@code 4xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code joinGroupViaInvite}; the {@code <error>} envelope's
     * {@code code} and {@code text} attributes feed
     * {@link #errorCode()} and {@link #errorText()}.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
    final class ClientError implements IqJoinGroupByInviteCodeResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a {@link ClientError} reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional text; may be {@code null}
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
         * Returns the optional error text.
         *
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqJoinGroupByInviteCodeResponse#of(Node, Node)};
         * this factory is exposed so callers can short-circuit when
         * they already know the wire shape is a client error.
         *
         * @implNote
         * This implementation delegates to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * to validate the {@code type="error"} envelope and the
         * {@code <error>} child's {@code 4xx} {@code code} before
         * extracting code/text.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
                exports = "joinGroupViaInvite",
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqJoinGroupByInviteCodeResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     *
     * @apiNote
     * Surfaces transient {@code 5xx} relay failures while redeeming
     * the invite code; the request may be retried after a backoff.
     *
     * @implNote
     * This implementation corresponds to the {@code 5xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code joinGroupViaInvite}.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
    final class ServerError implements IqJoinGroupByInviteCodeResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a {@link ServerError} reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional text; may be {@code null}
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
         * Returns the optional error text.
         *
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqJoinGroupByInviteCodeResponse#of(Node, Node)};
         * this factory is exposed so callers can short-circuit when
         * they already know the wire shape is a server error.
         *
         * @implNote
         * This implementation delegates to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * to validate the {@code type="error"} envelope and the
         * {@code <error>} child's {@code 5xx} {@code code} before
         * extracting code/text.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
                exports = "joinGroupViaInvite",
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqJoinGroupByInviteCodeResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
