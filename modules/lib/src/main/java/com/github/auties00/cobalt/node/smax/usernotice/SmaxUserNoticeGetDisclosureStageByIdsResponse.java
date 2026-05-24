package com.github.auties00.cobalt.node.smax.usernotice;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
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
 * Sealed family of inbound reply variants for
 * {@link SmaxUserNoticeGetDisclosureStageByIdsRequest}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WASmaxUserNoticeGetDisclosureStageByIdsRPC}
 * dispatch: {@link ClientSuccess} (per-disclosure stage entries),
 * {@link ClientError} (the relay rejected the request as malformed),
 * {@link ServerError} (transient relay-side failure or rate limit). WA
 * Web's {@code WAWebBizBroadcastTos} uses the success branch to check
 * whether the biz-broadcast disclosure has progressed to
 * {@code SOFT_OPT_IN} or {@code ACCEPTED}.
 */
public sealed interface SmaxUserNoticeGetDisclosureStageByIdsResponse extends SmaxOperation.Response
        permits SmaxUserNoticeGetDisclosureStageByIdsResponse.ClientSuccess, SmaxUserNoticeGetDisclosureStageByIdsResponse.ClientError, SmaxUserNoticeGetDisclosureStageByIdsResponse.ServerError {

    /**
     * Tries each {@link SmaxUserNoticeGetDisclosureStageByIdsResponse}
     * variant in priority order and returns the first that parses
     * cleanly.
     *
     * @apiNote
     * The dispatcher entry point used by Cobalt's SMAX layer to lift an
     * inbound stanza into the sealed disjunction. An empty result
     * indicates a protocol violation.
     *
     * @implNote
     * This implementation tries {@link ClientSuccess}, then
     * {@link ClientError}, then {@link ServerError}, mirroring the WA
     * Web call order.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxUserNoticeGetDisclosureStageByIdsRPC",
            exports = "sendGetDisclosureStageByIdsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxUserNoticeGetDisclosureStageByIdsResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var clientSuccess = ClientSuccess.of(node, request);
        if (clientSuccess.isPresent()) {
            return clientSuccess;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * The {@code ClientSuccess} reply variant.
     *
     * @apiNote
     * Carries one {@link DisclosureStageNotice} per polled disclosure,
     * in the same order the relay produced them.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeGetDisclosureStageByIdsResponseClientSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeIQResultResponseMixin")
    final class ClientSuccess implements SmaxUserNoticeGetDisclosureStageByIdsResponse {
        /**
         * The list of stage entries returned by the relay.
         */
        private final List<DisclosureStageNotice> notices;

        /**
         * Constructs a {@code ClientSuccess} reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after envelope validation; the
         * list is defensively copied.
         *
         * @param notices the per-disclosure stage entries; defaults to
         *                an empty list when {@code null}
         */
        public ClientSuccess(List<DisclosureStageNotice> notices) {
            this.notices = List.copyOf(Objects.requireNonNullElse(notices, List.of()));
        }

        /**
         * Returns the list of stage entries.
         *
         * @apiNote
         * Each entry carries the per-disclosure {@code (id, t, stage)}
         * triple plus optional version/type metadata; embedders match
         * the id with the corresponding query and dispatch on the
         * stage.
         *
         * @return an unmodifiable {@link List} of
         *         {@link DisclosureStageNotice}
         */
        public List<DisclosureStageNotice> notices() {
            return notices;
        }

        /**
         * Tries to parse a {@link ClientSuccess} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope fails the
         * standard {@code <iq type="result">} validation or when any
         * {@code <notice>} child fails per-entry parsing.
         *
         * @implNote
         * This implementation walks every {@code <notice>} child via
         * {@link DisclosureStageNotice#of(Node)}; any failed entry
         * aborts the whole parse so the dispatcher can try the error
         * branches.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosureStageByIdsResponseClientSuccess",
                exports = "parseGetDisclosureStageByIdsResponseClientSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientSuccess> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var notices = new ArrayList<DisclosureStageNotice>();
            for (var noticeNode : node.getChildren("notice")) {
                var notice = DisclosureStageNotice.of(noticeNode).orElse(null);
                if (notice == null) {
                    return Optional.empty();
                }
                notices.add(notice);
            }
            return Optional.of(new ClientSuccess(notices));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the notices list.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ClientSuccess) obj;
            return Objects.equals(this.notices, that.notices);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the notices list.
         */
        @Override
        public int hashCode() {
            return Objects.hash(notices);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} response family.
         */
        @Override
        public String toString() {
            return "SmaxUserNoticeGetDisclosureStageByIdsResponse.ClientSuccess[notices=" + notices + ']';
        }

        /**
         * A single {@code <notice>} entry projecting the per-disclosure
         * stage marker carried in the reply.
         *
         * @apiNote
         * Unlike
         * {@link SmaxUserNoticeGetDisclosuresResponse.ClientSuccess.DisclosureNotice},
         * the {@code version} and {@code type} attributes are optional;
         * the StageByIds RPC only commits to surfacing the
         * {@code (id, t, stage)} triple.
         */
        @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeStageMixin")
        public static final class DisclosureStageNotice {
            /**
             * The relay-side timestamp ({@code t} attribute, in
             * seconds since the UNIX epoch).
             */
            private final long timestampSeconds;

            /**
             * The optional notice version ({@code version} attribute,
             * at least one when present).
             */
            private final Integer version;

            /**
             * The optional notice type ({@code type} attribute,
             * non-negative when present).
             */
            private final Integer type;

            /**
             * The disclosure id from
             * {@code WASmaxInUserNoticeStageMixin}.
             */
            private final long noticeId;

            /**
             * The current stage in the {@code [0, 1000]} range.
             */
            private final int stage;

            /**
             * Constructs a disclosure-stage notice.
             *
             * @apiNote
             * Used by {@link #of(Node)} after the {@code <notice>}
             * element passes every per-field check.
             *
             * @param timestampSeconds the relay-side timestamp
             * @param version          the optional notice version
             * @param type             the optional notice type
             * @param noticeId         the disclosure id
             * @param stage            the current stage
             */
            public DisclosureStageNotice(long timestampSeconds, Integer version, Integer type,
                                         long noticeId, int stage) {
                this.timestampSeconds = timestampSeconds;
                this.version = version;
                this.type = type;
                this.noticeId = noticeId;
                this.stage = stage;
            }

            /**
             * Returns the relay-side timestamp.
             *
             * @apiNote
             * Surfaced for audit and freshness checks.
             *
             * @return the timestamp in seconds
             */
            public long timestampSeconds() {
                return timestampSeconds;
            }

            /**
             * Returns the optional notice version.
             *
             * @apiNote
             * Distinguishes successive revisions of the same disclosure
             * when surfaced; absent on entries that carry only the
             * stage triple.
             *
             * @return an {@link Optional} carrying the version
             */
            public Optional<Integer> version() {
                return Optional.ofNullable(version);
            }

            /**
             * Returns the optional notice type.
             *
             * @apiNote
             * Identifies the disclosure category as defined by
             * {@code WAWebPDFNTypes}; absent on entries that carry only
             * the stage triple.
             *
             * @return an {@link Optional} carrying the type
             */
            public Optional<Integer> type() {
                return Optional.ofNullable(type);
            }

            /**
             * Returns the disclosure id.
             *
             * @apiNote
             * Matches the {@code disclosureId} of the corresponding
             * outbound {@code <get_disclosure_stage_by_id>} query.
             *
             * @return the id
             */
            public long noticeId() {
                return noticeId;
            }

            /**
             * Returns the current stage.
             *
             * @apiNote
             * Compare against the
             * {@code WAWebPDFNTypes.DISCLOSURE_STAGE} entries
             * ({@code SOFT_OPT_IN}, {@code ACCEPTED}, etc.) to decide
             * whether the user has dismissed or accepted the
             * disclosure.
             *
             * @return the stage in the {@code [0, 1000]} range
             */
            public int stage() {
                return stage;
            }

            /**
             * Tries to parse a stage notice from the given
             * {@code <notice>} child.
             *
             * @apiNote
             * Returns {@link Optional#empty()} for any sub-tree missing
             * a required attribute or carrying an out-of-range value.
             *
             * @implNote
             * This implementation enforces the WA Web range contracts:
             * version at least 1 when present, type non-negative when
             * present, stage within {@code [0, 1000]}. An attribute
             * that is present but out of range aborts the parse, while
             * a missing optional attribute leaves the corresponding
             * {@link Optional} empty.
             *
             * @param node the {@code <notice>} child
             * @return an {@link Optional} carrying the parsed entry
             * @throws NullPointerException if {@code node} is
             *                              {@code null}
             */
            @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosureStageByIdsResponseClientSuccess",
                    exports = "parseGetDisclosureStageByIdsResponseClientSuccessNotice",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            public static Optional<DisclosureStageNotice> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("notice")) {
                    return Optional.empty();
                }
                var timestampOpt = node.getAttributeAsLong("t");
                if (timestampOpt.isEmpty()) {
                    return Optional.empty();
                }
                var versionAttr = node.getAttributeAsInt("version").orElse(-1);
                Integer version = null;
                if (versionAttr >= 1) {
                    version = versionAttr;
                } else if (node.hasAttribute("version")) {
                    return Optional.empty();
                }
                var typeAttr = node.getAttributeAsInt("type").orElse(-1);
                Integer type = null;
                if (typeAttr >= 0) {
                    type = typeAttr;
                } else if (node.hasAttribute("type")) {
                    return Optional.empty();
                }
                var idOpt = node.getAttributeAsLong("id");
                if (idOpt.isEmpty()) {
                    return Optional.empty();
                }
                var stageOpt = node.getAttributeAsInt("stage");
                if (stageOpt.isEmpty() || stageOpt.getAsInt() < 0 || stageOpt.getAsInt() > 1000) {
                    return Optional.empty();
                }
                return Optional.of(new DisclosureStageNotice(timestampOpt.getAsLong(),
                        version, type, idOpt.getAsLong(), stageOpt.getAsInt()));
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation compares every carried field.
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (DisclosureStageNotice) obj;
                return this.timestampSeconds == that.timestampSeconds
                        && this.noticeId == that.noticeId
                        && this.stage == that.stage
                        && Objects.equals(this.version, that.version)
                        && Objects.equals(this.type, that.type);
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation hashes every carried field via
             * {@link Objects#hash(Object...)}.
             */
            @Override
            public int hashCode() {
                return Objects.hash(timestampSeconds, version, type, noticeId, stage);
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation mirrors the record-like rendering
             * used across the {@code Smax*} response family.
             */
            @Override
            public String toString() {
                return "SmaxUserNoticeGetDisclosureStageByIdsResponse.ClientSuccess.DisclosureStageNotice[timestampSeconds="
                        + timestampSeconds
                        + ", version=" + version
                        + ", type=" + type
                        + ", noticeId=" + noticeId
                        + ", stage=" + stage + ']';
            }
        }
    }

    /**
     * The {@code ClientError} reply variant.
     *
     * @apiNote
     * The relay rejected the request as malformed (always
     * {@code 400 bad-request}); embedders typically log and skip the
     * affected disclosure poll.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeGetDisclosureStageByIdsResponseClientError")
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeIQErrorBadRequestMixin")
    final class ClientError implements SmaxUserNoticeGetDisclosureStageByIdsResponse {
        /**
         * The numeric error code (always {@code 400} in practice).
         */
        private final int errorCode;

        /**
         * The human-readable error text (typically
         * {@code "bad-request"}).
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after the
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * envelope check succeeds.
         *
         * @param errorCode the error code
         * @param errorText the optional text
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Below {@code 500}; embedders typically map the code into a
         * client-facing exception.
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
         * Useful for logging and as the exception message.
         *
         * @return an {@link Optional} carrying the text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the stanza is not a
         * well-formed client-error envelope.
         *
         * @implNote
         * This implementation delegates the envelope and code-range
         * checks to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosureStageByIdsResponseClientError",
                exports = "parseGetDisclosureStageByIdsResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares both the code and the text.
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
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes both fields via
         * {@link Objects#hash(Object...)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} response family.
         */
        @Override
        public String toString() {
            return "SmaxUserNoticeGetDisclosureStageByIdsResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     *
     * @apiNote
     * The relay encountered an internal failure or rate-limited the
     * caller; embedders should retry with backoff. The user-notice
     * domain folds both genuine {@code 5xx} codes and the
     * {@code 429 rate-overlimit} throttle into this branch, matching
     * {@code WAWebBizBroadcastTos}'s use of the soft-opt-in poll inside
     * an exponential-backoff loop.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeGetDisclosureStageByIdsResponseServerError")
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeUserNoticeServerError")
    final class ServerError implements SmaxUserNoticeGetDisclosureStageByIdsResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied one.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after the envelope check
         * succeeds; both genuine {@code [500, ...]} codes and the
         * {@code 429 rate-overlimit} fallback land here.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional text
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Either a genuine {@code 5xx} from the server-error mixin or
         * the {@code 429} rate-overlimit code surfaced through the
         * client-error helper.
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
         * Useful for logging and as the exception message.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the stanza is not a
         * well-formed server-error envelope and does not carry the
         * {@code 429 rate-overlimit} client-error pair.
         *
         * @implNote
         * This implementation first delegates to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)};
         * the user-notice domain folds {@code 429 rate-overlimit} into
         * the {@code ServerError} disjunction, so a second pass through
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * picks up that one code when the primary helper declines.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosureStageByIdsResponseServerError",
                exports = "parseGetDisclosureStageByIdsResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                var clientEnvelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
                if (clientEnvelope == null || clientEnvelope.code() != 429) {
                    return Optional.empty();
                }
                return Optional.of(new ServerError(clientEnvelope.code(), clientEnvelope.text()));
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares both the code and the text.
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
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes both fields via
         * {@link Objects#hash(Object...)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} response family.
         */
        @Override
        public String toString() {
            return "SmaxUserNoticeGetDisclosureStageByIdsResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
