package com.github.auties00.cobalt.node.smax.status;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The reply produced by the relay for a
 * {@link SmaxStatusPublishPostNewsletterStatusRequest}; either a
 * {@link Success} acknowledging the publish or a {@link Negative}
 * carrying the rejection code and optional retry backoff.
 *
 * @apiNote
 * Returned by the smax send pipeline that
 * {@code WASmaxStatusPublishPostNewsletterStatusRPC.sendPostNewsletterStatusRPC}
 * drives. The success arm hands back the relay-assigned server-id of
 * the new status; the negative arm carries enough information for the
 * caller to schedule a retry per the {@code backoff} hint.
 */
public sealed interface SmaxStatusPublishPostNewsletterStatusResponse extends SmaxOperation.Response
        permits SmaxStatusPublishPostNewsletterStatusResponse.Success, SmaxStatusPublishPostNewsletterStatusResponse.Negative {

    /**
     * Resolves an inbound ack into the first matching response
     * variant in negative-then-success priority.
     *
     * @apiNote
     * Called by the smax send pipeline after dispatching a
     * {@link SmaxStatusPublishPostNewsletterStatusRequest}.
     *
     * @implNote
     * This implementation mirrors the WA Web
     * {@code sendPostNewsletterStatusRPC} disjunction's priority
     * order.
     *
     * @param node    the inbound ack stanza; never {@code null}
     * @param request the originating outbound status stanza; never
     *                {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxStatusPublishPostNewsletterStatusRPC",
            exports = "sendPostNewsletterStatusRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxStatusPublishPostNewsletterStatusResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var negative = Negative.of(node, request);
        if (negative.isPresent()) {
            return negative;
        }
        return Success.of(node, request);
    }

    /**
     * Validates the {@code <ack class="status">} envelope by
     * cross-checking the description, class, id, from, and
     * timestamp attributes against the originating request.
     *
     * @apiNote
     * Used internally by {@link Success#of(Node, Node)} and
     * {@link Negative#of(Node, Node)} to gate further parsing.
     *
     * @implNote
     * This implementation enforces the WA Web timestamp range
     * ({@code 1577865600..4102473600}, year-2020-to-2099 seconds) and
     * lets the {@code from} echo check succeed when the request
     * lacked a {@code to} attribute, mirroring
     * {@code parseStatusAckMixin}.
     *
     * @param reply   the inbound ack stanza
     * @param request the originating outbound status stanza
     * @return {@code true} when the envelope passes every echo check
     */
    @WhatsAppWebExport(moduleName = "WASmaxInStatusPublishStatusAckMixin",
            exports = "parseStatusAckMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean validateAckEnvelope(Node reply, Node request) {
        if (!reply.hasDescription("ack")) {
            return false;
        }
        if (!reply.hasAttribute("class", "status")) {
            return false;
        }
        var requestId = request.getAttributeAsString("id").orElse(null);
        if (requestId == null) {
            return false;
        }
        if (!reply.hasAttribute("id", requestId)) {
            return false;
        }
        var requestTo = request.getAttributeAsString("to").orElse(null);
        if (requestTo != null && !reply.hasAttribute("from", requestTo)) {
            return false;
        }
        var tOpt = reply.getAttributeAsLong("t");
        if (tOpt.isEmpty()) {
            return false;
        }
        var t = tOpt.getAsLong();
        return t >= 1577865600L && t <= 4102473600L;
    }

    /**
     * The positive reply variant carrying the assigned server-id
     * (for brand-new client-id-only publishes) and the ack
     * timestamp.
     *
     * @apiNote
     * Surfaced to callers when the relay landed the status publish.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInStatusPublishPostNewsletterStatusResponseSuccess")
    final class Success implements SmaxStatusPublishPostNewsletterStatusResponse {
        /**
         * The optional relay-assigned status-message server-id; set
         * only on brand-new-status publishes.
         */
        private final Long serverId;

        /**
         * The unix-second timestamp of the ack.
         */
        private final long timestamp;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after a successful parse.
         *
         * @param serverId  the optional server-id; may be {@code null}
         * @param timestamp the unix-second timestamp
         */
        public Success(Long serverId, long timestamp) {
            this.serverId = serverId;
            this.timestamp = timestamp;
        }

        /**
         * Returns the optional relay-assigned status server-id.
         *
         * @apiNote
         * Present only on brand-new-status publishes; the relay uses
         * it as the stable key for the newly landed status.
         *
         * @return an {@link Optional} carrying the id, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<Long> serverId() {
            return Optional.ofNullable(serverId);
        }

        /**
         * Returns the unix-second timestamp of the ack.
         *
         * @return the timestamp
         */
        public long timestamp() {
            return timestamp;
        }

        /**
         * Parses a {@code Success} reply from the given inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} for any deviation from the
         * documented success schema (malformed ack envelope,
         * out-of-range server-id).
         *
         * @implNote
         * This implementation enforces the WA Web server-id range
         * ({@code 99..2147476647}) when the attribute is present.
         *
         * @param node    the inbound ack stanza
         * @param request the originating outbound status stanza
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInStatusPublishPostNewsletterStatusResponseSuccess",
                exports = "parsePostNewsletterStatusResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            Long serverId = null;
            var serverIdOpt = node.getAttributeAsLong("server_id");
            if (serverIdOpt.isPresent()) {
                var sv = serverIdOpt.getAsLong();
                if (sv < 99 || sv > 2147476647L) {
                    return Optional.empty();
                }
                serverId = sv;
            }
            var timestamp = node.getAttributeAsLong("t").orElse(-1);
            return Optional.of(new Success(serverId, timestamp));
        }

        /**
         * Compares this success reply to another for value equality.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is a {@link Success}
         *         with identical fields
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
            return this.timestamp == that.timestamp
                    && Objects.equals(this.serverId, that.serverId);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(serverId, timestamp);
        }

        /**
         * Returns a debug-friendly representation of this reply.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxStatusPublishPostNewsletterStatusResponse.Success[serverId=" + serverId
                    + ", timestamp=" + timestamp + ']';
        }
    }

    /**
     * The negative reply variant carrying the relay's rejection
     * code and optional application-error / retry-backoff hints.
     *
     * @apiNote
     * Surfaced when the relay rejected the status publish.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInStatusPublishPostNewsletterStatusResponseNegative")
    @WhatsAppWebModule(moduleName = "WASmaxInStatusPublishStatusNegativeAckMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInStatusPublishStatusApplicationNegativeAckMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInStatusPublishMessageNackRetryAttributesMixin")
    final class Negative implements SmaxStatusPublishPostNewsletterStatusResponse {
        /**
         * The relay-supplied error code carried as the {@code error}
         * attribute on the ack.
         */
        private final String errorCode;

        /**
         * The optional application-level error integer.
         */
        private final Integer applicationError;

        /**
         * The optional retry-backoff hint in seconds; bounded to
         * {@code 0..86400}.
         */
        private final Integer backoff;

        /**
         * The unix-second timestamp of the ack.
         */
        private final long timestamp;

        /**
         * Constructs a negative reply.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after a successful parse.
         *
         * @param errorCode        the error code; never {@code null}
         * @param applicationError the optional application error;
         *                         may be {@code null}
         * @param backoff          the optional retry backoff in
         *                         seconds; may be {@code null}
         * @param timestamp        the unix-second timestamp
         * @throws NullPointerException if {@code errorCode} is
         *                              {@code null}
         */
        public Negative(String errorCode, Integer applicationError, Integer backoff, long timestamp) {
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode cannot be null");
            this.applicationError = applicationError;
            this.backoff = backoff;
            this.timestamp = timestamp;
        }

        /**
         * Returns the error code.
         *
         * @return the code; never {@code null}
         */
        public String errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional application-level error integer.
         *
         * @return an {@link Optional} carrying the error, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<Integer> applicationError() {
            return Optional.ofNullable(applicationError);
        }

        /**
         * Returns the optional retry-backoff hint in seconds.
         *
         * @apiNote
         * Bounded to {@code 0..86400}; callers may schedule a retry
         * after this many seconds.
         *
         * @return an {@link Optional} carrying the backoff, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<Integer> backoff() {
            return Optional.ofNullable(backoff);
        }

        /**
         * Returns the unix-second timestamp of the ack.
         *
         * @return the timestamp
         */
        public long timestamp() {
            return timestamp;
        }

        /**
         * Parses a {@code Negative} reply from the given inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} for any deviation from the
         * documented negative-ack schema.
         *
         * @implNote
         * This implementation enforces the WA Web range bounds on
         * application-error ({@code >= 0}) and backoff
         * ({@code 0..86400}) inline.
         *
         * @param node    the inbound ack stanza
         * @param request the originating outbound status stanza
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInStatusPublishPostNewsletterStatusResponseNegative",
                exports = "parsePostNewsletterStatusResponseNegative",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Negative> of(Node node, Node request) {
            if (!validateAckEnvelope(node, request)) {
                return Optional.empty();
            }
            var errorCode = node.getAttributeAsString("error").orElse(null);
            if (errorCode == null) {
                return Optional.empty();
            }
            Integer applicationError = null;
            var appErrOpt = node.getAttributeAsInt("application_error");
            if (appErrOpt.isPresent()) {
                var av = appErrOpt.getAsInt();
                if (av < 0) {
                    return Optional.empty();
                }
                applicationError = av;
            }
            Integer backoff = null;
            var backoffOpt = node.getAttributeAsInt("backoff");
            if (backoffOpt.isPresent()) {
                var bv = backoffOpt.getAsInt();
                if (bv < 0 || bv > 86400) {
                    return Optional.empty();
                }
                backoff = bv;
            }
            var timestamp = node.getAttributeAsLong("t").orElse(-1);
            return Optional.of(new Negative(errorCode, applicationError, backoff, timestamp));
        }

        /**
         * Compares this negative reply to another for value equality.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is a {@link Negative}
         *         with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Negative) obj;
            return this.timestamp == that.timestamp
                    && Objects.equals(this.errorCode, that.errorCode)
                    && Objects.equals(this.applicationError, that.applicationError)
                    && Objects.equals(this.backoff, that.backoff);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, applicationError, backoff, timestamp);
        }

        /**
         * Returns a debug-friendly representation of this reply.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxStatusPublishPostNewsletterStatusResponse.Negative[errorCode=" + errorCode
                    + ", applicationError=" + applicationError
                    + ", backoff=" + backoff
                    + ", timestamp=" + timestamp + ']';
        }
    }
}
