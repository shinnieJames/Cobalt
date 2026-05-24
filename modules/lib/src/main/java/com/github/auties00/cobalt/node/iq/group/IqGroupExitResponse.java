package com.github.auties00.cobalt.node.iq.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound reply variants the relay produces in
 * response to an {@link IqGroupExitRequest}.
 *
 * @apiNote
 * After dispatching the leave request, pattern-match the returned
 * variant: {@link Success} carries one {@link Success.LeaveResult}
 * per requested target (with a per-target outcome code so partial
 * failures inside a batch are visible), while {@link ClientError}
 * and {@link ServerError} surface envelope-level rejections that
 * abort the entire batch.
 *
 * @implNote
 * This implementation collapses WA Web's
 * {@code leaveGroupsResultParser} and
 * {@code leaveCommunitiesResultParser} (two
 * {@link WhatsAppWebModule WAWebGroupExitJob}-internal
 * parsers fed into {@code deprecatedSendIq}) into a single sealed
 * sum; the grandchild-shape (whether to read {@code id} on
 * {@code <group>} or {@code parent_group_jid} on
 * {@code <linked_groups>}) is recovered from the outbound request.
 */
@WhatsAppWebModule(moduleName = "WAWebGroupExitJob")
public sealed interface IqGroupExitResponse extends IqOperation.Response
        permits IqGroupExitResponse.Success, IqGroupExitResponse.ClientError, IqGroupExitResponse.ServerError {

    /**
     * Tries each {@link IqGroupExitResponse} variant in priority order
     * and returns the first that parses cleanly.
     *
     * @apiNote
     * Use this when dispatching through the typed {@link IqOperation}
     * pipeline; the dispatcher hands the inbound {@link Node} together
     * with the original outbound request so that the parser can
     * recover the grandchild shape from the request rather than from
     * the reply (the relay echoes whichever shape the caller used).
     *
     * @implNote
     * This implementation tries {@link Success} first, then
     * {@link ClientError}, then {@link ServerError}; the order matches
     * WA Web's promise resolution where the result branch is asserted
     * before any error envelope is inspected.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza used to validate echoed identifiers and to discover the grandchild-shape mode; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when no documented variant matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
            exports = "leaveGroup", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
            exports = "leaveCommunity", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
            exports = "leaveCommunities", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqGroupExitResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant.
     *
     * @apiNote
     * Carries one {@link LeaveResult} per requested target. Each
     * carries an {@code error} code mirroring the relay's per-target
     * status (defaulting to {@code 200} when the relay omits it,
     * which signals a per-target success); a batch that nominally
     * succeeded at the envelope level can still hold individual
     * non-{@code 200} entries when one or more targets failed.
     * Callers iterating {@link #results()} should treat any code
     * other than {@code 200} as a per-target leave failure.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code leaveGroupsResultParser} / {@code leaveCommunitiesResultParser}
     * output shape ({@code [{id, code}, ...]}) where the parser maps
     * each {@code <group>} or {@code <linked_groups>} grandchild of
     * the {@code <leave>} child to a {@code (jid, code)} pair.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupExitJob")
    final class Success implements IqGroupExitResponse {
        /**
         * The per-target reply projection that pairs the echoed
         * target JID with its (possibly partial) leave outcome code.
         *
         * @apiNote
         * The {@link #code()} accessor returns {@code 200} on a clean
         * leave; any other value is the relay's per-target error code
         * (for example {@code 403} when the caller is not actually a
         * member of the target group).
         */
        public static final class LeaveResult {
            /**
             * The echoed target JID.
             */
            private final Jid jid;

            /**
             * The per-target outcome code.
             */
            private final int code;

            /**
             * Constructs a per-target leave result.
             *
             * @param jid  the echoed target {@link Jid}; never {@code null}
             * @param code the per-target outcome code; {@code 200} signals a clean per-target leave
             * @throws NullPointerException if {@code jid} is {@code null}
             */
            public LeaveResult(Jid jid, int code) {
                this.jid = Objects.requireNonNull(jid, "jid cannot be null");
                this.code = code;
            }

            /**
             * Returns the echoed target JID.
             *
             * @return the target {@link Jid}; never {@code null}
             */
            public Jid jid() {
                return jid;
            }

            /**
             * Returns the per-target outcome code.
             *
             * @return the outcome code; {@code 200} on success
             */
            public int code() {
                return code;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (LeaveResult) obj;
                return this.code == that.code
                        && Objects.equals(this.jid, that.jid);
            }

            @Override
            public int hashCode() {
                return Objects.hash(jid, code);
            }

            @Override
            public String toString() {
                return "IqGroupExitResponse.Success.LeaveResult[jid=" + jid
                        + ", code=" + code + ']';
            }
        }

        /**
         * The list of per-target outcome projections.
         */
        private final List<LeaveResult> results;

        /**
         * Constructs a {@link Success} reply.
         *
         * @param results the per-target outcome list; never {@code null}
         * @throws NullPointerException if {@code results} is {@code null}
         */
        public Success(List<LeaveResult> results) {
            Objects.requireNonNull(results, "results cannot be null");
            this.results = List.copyOf(results);
        }

        /**
         * Returns the list of per-target outcome projections.
         *
         * @return an unmodifiable {@link List} of {@link LeaveResult} entries; never {@code null}
         */
        public List<LeaveResult> results() {
            return results;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqGroupExitResponse#of(Node, Node)}; this factory is
         * exposed so callers can short-circuit when they already know
         * the wire shape is a success.
         *
         * @implNote
         * This implementation recovers the grandchild shape from the
         * outbound request: if the outbound {@code <leave>} carries
         * any {@code <linked_groups>} child the parser reads
         * {@code parent_group_jid} on each grandchild, otherwise it
         * reads {@code id} on {@code <group>} children. This mirrors
         * WA Web's separate {@code leaveGroupsResultParser} and
         * {@code leaveCommunitiesResultParser} pair: WA Web picks the
         * parser at dispatch time, Cobalt picks the attribute name at
         * parse time. The {@code error} attribute defaults to
         * {@code 200} when absent, matching the parser's
         * {@code maybeAttrInt("error") != null ? ... : 200} contract.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveGroup",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveCommunity",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveCommunities",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var leaveChild = node.getChild("leave").orElse(null);
            if (leaveChild == null) {
                return Optional.empty();
            }
            var requestLeaveChild = request.getChild("leave").orElse(null);
            if (requestLeaveChild == null) {
                return Optional.empty();
            }
            String jidAttribute;
            String grandchildTag;
            if (requestLeaveChild.hasChild("linked_groups")) {
                grandchildTag = "linked_groups";
                jidAttribute = "parent_group_jid";
            } else {
                grandchildTag = "group";
                jidAttribute = "id";
            }
            var results = new ArrayList<LeaveResult>();
            var children = leaveChild.streamChildren(grandchildTag).toList();
            for (var child : children) {
                var jid = child.getAttributeAsJid(jidAttribute).orElse(null);
                if (jid == null) {
                    return Optional.empty();
                }
                var code = child.getAttributeAsInt("error", 200);
                results.add(new LeaveResult(jid, code));
            }
            return Optional.of(new Success(results));
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
            return Objects.equals(this.results, that.results);
        }

        @Override
        public int hashCode() {
            return Objects.hash(results);
        }

        @Override
        public String toString() {
            return "IqGroupExitResponse.Success[results=" + results + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant.
     *
     * @apiNote
     * Surfaces caller-side rejections of the whole leave batch:
     * typically {@code 400} on a malformed stanza or {@code 401}
     * when the caller's session no longer authorises group operations.
     * Per-target failures are surfaced as non-{@code 200} entries
     * inside a {@link Success} instead; this variant only fires when
     * the relay rejects the envelope itself.
     *
     * @implNote
     * This implementation corresponds to the {@code 4xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code leaveGroup} / {@code leaveCommunity} /
     * {@code leaveCommunities}; the {@code <error>} envelope's
     * {@code code} and {@code text} attributes feed
     * {@link #errorCode()} and {@link #errorText()}.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupExitJob")
    final class ClientError implements IqGroupExitResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text when the relay supplied one,
         * otherwise {@code null}.
         */
        private final String errorText;

        /**
         * Constructs a {@link ClientError} reply.
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
         * Tries to parse a {@link ClientError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqGroupExitResponse#of(Node, Node)}; this factory is
         * exposed so callers can short-circuit when they already know
         * the wire shape is a client error.
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
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveGroup",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveCommunity",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveCommunities",
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
            return "IqGroupExitResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     *
     * @apiNote
     * Surfaces transient {@code 5xx} relay failures while processing
     * the leave batch; the request may be retried after a backoff.
     *
     * @implNote
     * This implementation corresponds to the {@code 5xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code leaveGroup} / {@code leaveCommunity} /
     * {@code leaveCommunities}.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupExitJob")
    final class ServerError implements IqGroupExitResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text when the relay supplied one,
         * otherwise {@code null}.
         */
        private final String errorText;

        /**
         * Constructs a {@link ServerError} reply.
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
         * Tries to parse a {@link ServerError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqGroupExitResponse#of(Node, Node)}; this factory is
         * exposed so callers can short-circuit when they already know
         * the wire shape is a server error.
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
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveGroup",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveCommunity",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
                exports = "leaveCommunities",
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
            return "IqGroupExitResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
