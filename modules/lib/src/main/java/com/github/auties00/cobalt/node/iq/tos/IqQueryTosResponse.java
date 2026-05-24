package com.github.auties00.cobalt.node.iq.tos;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqQueryTosRequest}.
 *
 * @apiNote
 * Switch on the returned variant to discriminate the relay outcome: a {@link Success}
 * carries the relay-clamped refresh interval plus the per-notice
 * {@link Success.NoticeState accepted-state} entries (used to drive WA Web's
 * {@code TosManager.run} cadence and to surface acceptance prompts), a
 * {@link ClientError} surfaces a relay rejection, and a {@link ServerError} surfaces
 * a transient relay failure WA Web retries via exponential backoff.
 */
public sealed interface IqQueryTosResponse extends IqOperation.Response
        permits IqQueryTosResponse.Success, IqQueryTosResponse.ClientError, IqQueryTosResponse.ServerError {

    /**
     * Minimum server-recommended refresh interval in seconds.
     *
     * @apiNote
     * Replies below this floor are clamped to
     * {@link #DEFAULT_TOS_REFRESH_INTERVAL_SECONDS}; the value matches WA Web's
     * implicit local floor of {@code 7200} seconds (two hours).
     */
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "queryTosState", adaptation = WhatsAppAdaptation.DIRECT)
    int MIN_TOS_REFRESH_INTERVAL_SECONDS = 7200;

    /**
     * Maximum server-recommended refresh interval in seconds.
     *
     * @apiNote
     * Replies above this ceiling are clamped to
     * {@link #DEFAULT_TOS_REFRESH_INTERVAL_SECONDS}; the value matches WA Web's
     * implicit local ceiling of {@code 259200} seconds (three days).
     */
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "queryTosState", adaptation = WhatsAppAdaptation.DIRECT)
    int MAX_TOS_REFRESH_INTERVAL_SECONDS = 259200;

    /**
     * Default server-recommended refresh interval in seconds (24h).
     *
     * @apiNote
     * Used as a fallback when the reply's {@code refresh} value falls outside the
     * {@code [MIN_TOS_REFRESH_INTERVAL_SECONDS, MAX_TOS_REFRESH_INTERVAL_SECONDS]}
     * range; the value matches WA Web's
     * {@code WAWebTosJob.DEFAULT_TOS_REFRESH_INTERVAL = 86400}.
     */
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "DEFAULT_TOS_REFRESH_INTERVAL", adaptation = WhatsAppAdaptation.DIRECT)
    int DEFAULT_TOS_REFRESH_INTERVAL_SECONDS = 86400;

    /**
     * Parses the inbound stanza into the first matching {@link IqQueryTosResponse}
     * variant.
     *
     * @apiNote
     * Try this once per inbound reply; the priority ordering (success, then
     * client-error, then server-error) matches the wire shape and never returns
     * ambiguous matches.
     *
     * @implNote
     * This implementation calls each variant's {@code of(node, request)} in turn
     * and returns the first present result.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no
     *         documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "queryTosState", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqQueryTosResponse> of(Node node, Node request) {
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
     * Success variant. The relay returned the per-notice accepted-state plus the
     * clamped refresh interval.
     *
     * @apiNote
     * Inspect {@link #refreshIntervalSeconds()} to drive the cadence of the
     * {@link IqQueryTosRequest} loop and {@link #notices()} for the per-notice
     * accepted-state. The list is filtered against the locally-known notice ids
     * by the caller's {@code TosManager} before being applied to {@code UserPrefs}.
     */
    @WhatsAppWebModule(moduleName = "WAWebTosJob")
    final class Success implements IqQueryTosResponse {
        /**
         * Holds the server-recommended refresh interval in seconds, clamped to
         * {@code [MIN_TOS_REFRESH_INTERVAL_SECONDS, MAX_TOS_REFRESH_INTERVAL_SECONDS]}
         * (falling back to {@link #DEFAULT_TOS_REFRESH_INTERVAL_SECONDS} on
         * out-of-range).
         */
        private final int refreshIntervalSeconds;

        /**
         * Holds the per-notice accepted-state entries returned by the relay.
         */
        private final List<NoticeState> notices;

        /**
         * Constructs a successful reply bound to the clamped refresh interval and
         * the per-notice entries.
         *
         * @param refreshIntervalSeconds the clamped refresh interval
         * @param notices                the per-notice entries; never {@code null}
         * @throws NullPointerException if {@code notices} is {@code null}
         */
        public Success(int refreshIntervalSeconds, List<NoticeState> notices) {
            this.refreshIntervalSeconds = refreshIntervalSeconds;
            Objects.requireNonNull(notices, "notices cannot be null");
            this.notices = List.copyOf(notices);
        }

        /**
         * Returns the clamped server-recommended refresh interval in seconds.
         *
         * @return the refresh interval
         */
        public int refreshIntervalSeconds() {
            return refreshIntervalSeconds;
        }

        /**
         * Returns the unmodifiable list of per-notice accepted-state entries.
         *
         * @return the entries; never {@code null}
         */
        public List<NoticeState> notices() {
            return notices;
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant when it
         * matches the success schema.
         *
         * @apiNote
         * Returns empty when the SMAX result-envelope check fails, when the
         * {@code <tos>} child is absent, when its {@code refresh} attribute is
         * missing, or when any {@code <notice>} grandchild is missing the
         * {@code id} attribute.
         *
         * @implNote
         * This implementation treats absent {@code state} attributes as
         * "accepted" and only treats {@code state="false"} as not-accepted,
         * matching WA Web's branch {@code maybeAttrString("state") !== "false"}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when
         *         the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebTosJob",
                exports = "queryTosState", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var tosChild = node.getChild("tos").orElse(null);
            if (tosChild == null) {
                return Optional.empty();
            }
            var rawRefresh = tosChild.getAttributeAsInt("refresh").orElse(-1);
            if (rawRefresh < 0) {
                return Optional.empty();
            }
            var clampedRefresh = rawRefresh;
            if (clampedRefresh > MAX_TOS_REFRESH_INTERVAL_SECONDS
                    || clampedRefresh < MIN_TOS_REFRESH_INTERVAL_SECONDS) {
                clampedRefresh = DEFAULT_TOS_REFRESH_INTERVAL_SECONDS;
            }
            var noticeChildren = tosChild.getChildren("notice");
            var notices = new ArrayList<NoticeState>(noticeChildren.size());
            for (var noticeNode : noticeChildren) {
                var id = noticeNode.getAttributeAsString("id").orElse(null);
                if (id == null) {
                    return Optional.empty();
                }
                var stateAttr = noticeNode.getAttributeAsString("state").orElse(null);
                var accepted = stateAttr == null || !stateAttr.equals("false");
                notices.add(new NoticeState(id, accepted));
            }
            return Optional.of(new Success(clampedRefresh, notices));
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
            return this.refreshIntervalSeconds == that.refreshIntervalSeconds
                    && Objects.equals(this.notices, that.notices);
        }

        @Override
        public int hashCode() {
            return Objects.hash(refreshIntervalSeconds, notices);
        }

        @Override
        public String toString() {
            return "IqQueryTosResponse.Success[refreshIntervalSeconds="
                    + refreshIntervalSeconds + ", notices=" + notices + ']';
        }

        /**
         * Per-notice accepted-state entry projected from one {@code <notice/>}
         * child of the {@code <tos/>} reply envelope.
         *
         * @apiNote
         * Feeds WA Web's {@code TosManager} state map: an {@link #accepted() true}
         * entry surfaces as {@code "ACCEPTED"} in {@code UserPrefs}, a false
         * entry surfaces as {@code "NOT_ACCEPTED"} (which then drives the
         * corresponding acceptance prompt on the next UI surface that gates on
         * the notice).
         */
        @WhatsAppWebModule(moduleName = "WAWebTosJob")
        public static final class NoticeState {
            /**
             * Holds the notice id (echoed from the corresponding request entry).
             */
            private final String id;

            /**
             * Holds the accepted-state flag.
             */
            private final boolean accepted;

            /**
             * Constructs a per-notice entry bound to the given id and flag.
             *
             * @apiNote
             * The {@code accepted} flag is {@code true} when the user has
             * accepted the notice (or when the relay omits the {@code state}
             * attribute), {@code false} only when the relay explicitly returns
             * {@code state="false"}.
             *
             * @param id       the notice id; never {@code null}
             * @param accepted the accepted-state flag
             * @throws NullPointerException if {@code id} is {@code null}
             */
            public NoticeState(String id, boolean accepted) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.accepted = accepted;
            }

            /**
             * Returns the notice id.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the accepted-state flag.
             *
             * @return {@code true} when the notice has been accepted,
             *         {@code false} otherwise
             */
            public boolean accepted() {
                return accepted;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (NoticeState) obj;
                return this.accepted == that.accepted
                        && Objects.equals(this.id, that.id);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, accepted);
            }

            @Override
            public String toString() {
                return "IqQueryTosResponse.Success.NoticeState[id="
                        + id + ", accepted=" + accepted + ']';
            }
        }
    }

    /**
     * Client-error variant. The relay rejected the query with a {@code 4xx} code.
     *
     * @apiNote
     * WA Web's {@code TosManager.run} treats any non-500 code as a fatal failure
     * (it stops the run loop instead of retrying); treat the same way unless the
     * caller has a richer policy.
     */
    @WhatsAppWebModule(moduleName = "WAWebTosJob")
    final class ClientError implements IqQueryTosResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply carrying the relay-echoed envelope.
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
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ClientError} variant when it
         * matches the standard SMAX client-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebTosJob",
                exports = "queryTosState", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqQueryTosResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Server-error variant. The relay encountered a transient internal failure
     * processing the query.
     *
     * @apiNote
     * WA Web specifically retries the {@code 500} arm via exponential backoff
     * (max five retries, {@code 1s -> 16s} base) and treats any other 5xx as
     * fatal; replicate this if a tight retry policy is required.
     */
    @WhatsAppWebModule(moduleName = "WAWebTosJob")
    final class ServerError implements IqQueryTosResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply carrying the relay-echoed envelope.
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
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ServerError} variant when it
         * matches the standard SMAX server-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebTosJob",
                exports = "queryTosState", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqQueryTosResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
