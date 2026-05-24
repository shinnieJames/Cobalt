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
 * {@link SmaxUserNoticeGetDisclosuresRequest}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WASmaxUserNoticeGetDisclosuresRPC} dispatch:
 * {@link ClientSuccess} (the relay returned zero or more disclosure
 * notices), {@link ClientError} (the relay rejected the request as
 * malformed, normally {@code 400 bad-request}), and {@link ServerError}
 * (transient relay-side failure including the {@code 429 rate-overlimit}
 * throttle).
 */
public sealed interface SmaxUserNoticeGetDisclosuresResponse extends SmaxOperation.Response
        permits SmaxUserNoticeGetDisclosuresResponse.ClientSuccess, SmaxUserNoticeGetDisclosuresResponse.ClientError, SmaxUserNoticeGetDisclosuresResponse.ServerError {

    /**
     * Tries each {@link SmaxUserNoticeGetDisclosuresResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * The dispatcher entry point used by Cobalt's SMAX layer to lift an
     * inbound stanza into the sealed disjunction. An empty result
     * indicates a protocol violation.
     *
     * @implNote
     * This implementation tries {@link ClientSuccess}, then
     * {@link ClientError}, then {@link ServerError}, mirroring the
     * WA Web call order.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxUserNoticeGetDisclosuresRPC",
            exports = "sendGetDisclosuresRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxUserNoticeGetDisclosuresResponse> of(Node node, Node request) {
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
     * Wraps the per-disclosure {@code <notice>} entries returned by the
     * relay; embedders surface them through a TOS-prompt UI.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeGetDisclosuresResponseClientSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeIQResultResponseMixin")
    final class ClientSuccess implements SmaxUserNoticeGetDisclosuresResponse {
        /**
         * The list of disclosure notices returned by the relay.
         */
        private final List<DisclosureNotice> notices;

        /**
         * Constructs a {@code ClientSuccess} reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after envelope validation; the
         * list is defensively copied.
         *
         * @param notices the disclosure notices; defaults to an empty
         *                list when {@code null}
         */
        public ClientSuccess(List<DisclosureNotice> notices) {
            this.notices = List.copyOf(Objects.requireNonNullElse(notices, List.of()));
        }

        /**
         * Returns the list of disclosure notices.
         *
         * @apiNote
         * Each {@link DisclosureNotice} carries the per-notice id,
         * version, type, timestamp, and current stage in the
         * acceptance lifecycle; embedders iterate the list to render
         * the prompt UI.
         *
         * @return an unmodifiable {@link List} of
         *         {@link DisclosureNotice}
         */
        public List<DisclosureNotice> notices() {
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
         * {@link DisclosureNotice#of(Node)}; any failed entry aborts
         * the whole parse so the dispatcher can try the error branches.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosuresResponseClientSuccess",
                exports = "parseGetDisclosuresResponseClientSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientSuccess> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var notices = new ArrayList<DisclosureNotice>();
            for (var noticeNode : node.getChildren("notice")) {
                var notice = DisclosureNotice.of(noticeNode).orElse(null);
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
            return "SmaxUserNoticeGetDisclosuresResponse.ClientSuccess[notices=" + notices + ']';
        }

        /**
         * A single {@code <notice>} entry.
         *
         * @apiNote
         * The relay's projection of one disclosure; carries the
         * per-notice timestamp, version/type pair, id, and current
         * stage. {@code WAWebGetUserDisclosuresJob.getAllUserDisclosures}
         * surfaces the list to the TOS-prompt UI; the stage value
         * maps onto {@code WAWebPDFNTypes.DISCLOSURE_STAGE} entries
         * such as {@code SOFT_OPT_IN} and {@code ACCEPTED}.
         */
        @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeStageMixin")
        public static final class DisclosureNotice {
            /**
             * The relay-side timestamp ({@code t} attribute, in seconds
             * since the UNIX epoch).
             */
            private final long timestampSeconds;

            /**
             * The notice version ({@code version} attribute, at least
             * one).
             */
            private final int version;

            /**
             * The notice type ({@code type} attribute, non-negative).
             */
            private final int type;

            /**
             * The notice id from
             * {@code WASmaxInUserNoticeStageMixin}.
             */
            private final long noticeId;

            /**
             * The current stage in the {@code [0, 1000]} range
             * (e.g., 1 = soft-opt-in, 2 = accepted).
             */
            private final int stage;

            /**
             * Constructs a disclosure notice.
             *
             * @apiNote
             * Used by {@link #of(Node)} after the {@code <notice>}
             * element passes every per-field check.
             *
             * @param timestampSeconds the relay-side timestamp
             * @param version          the notice version
             * @param type             the notice type
             * @param noticeId         the disclosure id
             * @param stage            the current stage
             */
            public DisclosureNotice(long timestampSeconds, int version, int type,
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
             * Surfaced for audit and TOS-prompt timestamping.
             *
             * @return the timestamp in seconds
             */
            public long timestampSeconds() {
                return timestampSeconds;
            }

            /**
             * Returns the notice version.
             *
             * @apiNote
             * Distinguishes successive revisions of the same disclosure.
             *
             * @return the version
             */
            public int version() {
                return version;
            }

            /**
             * Returns the notice type.
             *
             * @apiNote
             * Identifies the disclosure category as defined by
             * {@code WAWebPDFNTypes}.
             *
             * @return the type
             */
            public int type() {
                return type;
            }

            /**
             * Returns the disclosure id.
             *
             * @apiNote
             * Uniquely identifies the disclosure across versions; used
             * to correlate the entry with the corresponding
             * {@code WASmaxUserNoticeGetDisclosureStageByIdsRPC} reply.
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
             * Tries to parse a disclosure notice from the given
             * {@code <notice>} child.
             *
             * @apiNote
             * Returns {@link Optional#empty()} for any sub-tree missing
             * a required attribute or carrying an out-of-range value.
             *
             * @implNote
             * This implementation enforces the WA Web range contracts:
             * version at least 1, type non-negative, stage within
             * {@code [0, 1000]}.
             *
             * @param node the {@code <notice>} child
             * @return an {@link Optional} carrying the parsed entry
             * @throws NullPointerException if {@code node} is
             *                              {@code null}
             */
            @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosuresResponseClientSuccess",
                    exports = "parseGetDisclosuresResponseClientSuccessNotice",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            public static Optional<DisclosureNotice> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("notice")) {
                    return Optional.empty();
                }
                var timestampOpt = node.getAttributeAsLong("t");
                if (timestampOpt.isEmpty()) {
                    return Optional.empty();
                }
                var versionOpt = node.getAttributeAsInt("version");
                if (versionOpt.isEmpty() || versionOpt.getAsInt() < 1) {
                    return Optional.empty();
                }
                var typeOpt = node.getAttributeAsInt("type");
                if (typeOpt.isEmpty() || typeOpt.getAsInt() < 0) {
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
                return Optional.of(new DisclosureNotice(timestampOpt.getAsLong(),
                        versionOpt.getAsInt(), typeOpt.getAsInt(),
                        idOpt.getAsLong(), stageOpt.getAsInt()));
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation compares all five fields.
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (DisclosureNotice) obj;
                return this.timestampSeconds == that.timestampSeconds
                        && this.version == that.version
                        && this.type == that.type
                        && this.noticeId == that.noticeId
                        && this.stage == that.stage;
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation hashes all five fields via
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
                return "SmaxUserNoticeGetDisclosuresResponse.ClientSuccess.DisclosureNotice[timestampSeconds="
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
     * TOS prompt.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeGetDisclosuresResponseClientError")
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeIQErrorBadRequestMixin")
    final class ClientError implements SmaxUserNoticeGetDisclosuresResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosuresResponseClientError",
                exports = "parseGetDisclosuresResponseClientError",
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
            return "SmaxUserNoticeGetDisclosuresResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     *
     * @apiNote
     * The relay encountered an internal failure while processing the
     * request, or rate-limited the caller; embedders should retry with
     * backoff. WA Web's
     * {@code WAWebGetUserDisclosuresQueryJob} throws a
     * {@code ServerStatusCodeError} on this branch; Cobalt embedders can
     * map the {@code (code, text)} pair into their own retry layer.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeGetDisclosuresResponseServerError")
    @WhatsAppWebModule(moduleName = "WASmaxInUserNoticeUserNoticeServerError")
    final class ServerError implements SmaxUserNoticeGetDisclosuresResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInUserNoticeGetDisclosuresResponseServerError",
                exports = "parseGetDisclosuresResponseServerError",
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
            return "SmaxUserNoticeGetDisclosuresResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
