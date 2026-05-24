package com.github.auties00.cobalt.node.smax.newsletters;

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
 * Sealed family of inbound reply variants for a
 * {@link SmaxNewslettersGetNewsletterStatusUpdatesRequest}.
 *
 * @apiNote
 * Pattern-match on the three permitted variants ({@link Success},
 * {@link ClientError}, {@link ServerError}) when handling a reply from
 * WA Web's
 * {@code WAWebNewsletterGetStatusUpdatesJob.fetchNewsletterStatusUpdates};
 * the job logs and swallows non-success variants and rebuilds the
 * status maps from {@link Success#statuses()} only.
 */
public sealed interface SmaxNewslettersGetNewsletterStatusUpdatesResponse extends SmaxOperation.Response
        permits SmaxNewslettersGetNewsletterStatusUpdatesResponse.Success, SmaxNewslettersGetNewsletterStatusUpdatesResponse.ClientError, SmaxNewslettersGetNewsletterStatusUpdatesResponse.ServerError {

    /**
     * Dispatches the inbound IQ stanza to the first matching variant
     * parser.
     *
     * @apiNote
     * Mirrors WA Web's {@code sendGetNewsletterStatusUpdatesRPC}
     * entry-point: try {@link Success}, then {@link ClientError}, then
     * {@link ServerError} in order.
     *
     * @param node    the inbound IQ stanza received from the relay;
     *                never {@code null}
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterStatusUpdatesRPC",
            exports = "sendGetNewsletterStatusUpdatesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxNewslettersGetNewsletterStatusUpdatesResponse> of(Node node, Node request) {
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
     * The variant that carries the delta-of-status-updates batch.
     *
     * @apiNote
     * Project {@link #statuses()} onto the local view-count and
     * reaction maps; the entries reuse the same
     * {@link SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus}
     * envelope as the full status fetch.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterStatusUpdatesResponseSuccess")
    final class Success implements SmaxNewslettersGetNewsletterStatusUpdatesResponse {
        /**
         * The optional newsletter {@link Jid} echoed on the
         * {@code <statuses>} block.
         */
        private final Jid newsletterJid;

        /**
         * The optional unix-second timestamp echoed by the relay.
         */
        private final Long timestamp;

        /**
         * The list of status-update entries returned by the relay.
         */
        private final List<SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus> statuses;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Both {@code newsletterJid} and {@code timestamp} are optional
         * because the relay only echoes them when the corresponding
         * attributes were present on the wire.
         *
         * @param newsletterJid the optional echoed {@link Jid}; may be
         *                      {@code null}
         * @param timestamp     the optional echoed unix-second
         *                      timestamp; may be {@code null}
         * @param statuses      the status-update entries; never
         *                      {@code null} (empty allowed)
         */
        public Success(Jid newsletterJid,
                       Long timestamp,
                       List<SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus> statuses) {
            this.newsletterJid = newsletterJid;
            this.timestamp = timestamp;
            this.statuses = List.copyOf(Objects.requireNonNullElse(statuses, List.of()));
        }

        /**
         * Returns the optional echoed newsletter {@link Jid}.
         *
         * @return an {@link Optional} carrying the {@link Jid}, or
         *         empty when the relay omitted it
         */
        public Optional<Jid> newsletterJid() {
            return Optional.ofNullable(newsletterJid);
        }

        /**
         * Returns the optional echoed unix-second timestamp.
         *
         * @return an {@link Optional} carrying the timestamp, or empty
         *         when the relay omitted it
         */
        public Optional<Long> timestamp() {
            return Optional.ofNullable(timestamp);
        }

        /**
         * Returns the status-update entries.
         *
         * @return an unmodifiable {@link List} of entries; never
         *         {@code null}
         */
        public List<SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus> statuses() {
            return statuses;
        }

        /**
         * Tries to parse a {@link Success} from the inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the IQ envelope fails
         * {@link SmaxIqResultResponseMixin} validation, the
         * {@code <status_updates>} or {@code <statuses>} envelopes are
         * missing, the {@code t} attribute is negative, or any nested
         * {@code <status>} fails its own
         * {@link SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus#of(Node)}
         * parse.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterStatusUpdatesResponseSuccess",
                exports = "parseGetNewsletterStatusUpdatesResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var statusUpdates = node.getChild("status_updates").orElse(null);
            if (statusUpdates == null) {
                return Optional.empty();
            }
            var statusesNode = statusUpdates.getChild("statuses").orElse(null);
            if (statusesNode == null) {
                return Optional.empty();
            }
            var jid = statusesNode.getAttributeAsJid("jid").orElse(null);
            Long timestamp = null;
            var tOpt = statusesNode.getAttributeAsLong("t");
            if (tOpt.isPresent()) {
                var tv = tOpt.getAsLong();
                if (tv < 0) {
                    return Optional.empty();
                }
                timestamp = tv;
            }
            var entries = new ArrayList<SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus>();
            for (var statusNode : statusesNode.getChildren("status")) {
                var entry = SmaxNewslettersGetNewsletterStatusesResponse.NewsletterStatus.of(statusNode)
                        .orElse(null);
                if (entry == null) {
                    return Optional.empty();
                }
                entries.add(entry);
            }
            return Optional.of(new Success(jid, timestamp, entries));
        }

        /**
         * Compares two replies for value equality on every field.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link Success}
         *         with equal field values
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
            return Objects.equals(this.newsletterJid, that.newsletterJid)
                    && Objects.equals(this.timestamp, that.timestamp)
                    && Objects.equals(this.statuses, that.statuses);
        }

        /**
         * Returns the hash code derived from every field.
         *
         * @return the combined hash of every field
         */
        @Override
        public int hashCode() {
            return Objects.hash(newsletterJid, timestamp, statuses);
        }

        /**
         * Returns a debug representation including every field.
         *
         * @return a record-like rendering of this reply
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterStatusUpdatesResponse.Success[newsletterJid="
                    + newsletterJid + ", timestamp=" + timestamp
                    + ", statuses=" + statuses + ']';
        }
    }

    /**
     * The variant carrying a relay-side client-rejection.
     *
     * @apiNote
     * WA Web's status-updates job logs and discards every documented
     * sub-error rather than propagating, since the poll is best-effort
     * and re-fires on the next tick.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterStatusUpdatesResponseClientError")
    final class ClientError implements SmaxNewslettersGetNewsletterStatusUpdatesResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text from the relay.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @apiNote
         * The text is optional because not every sub-error carries a
         * human-readable message.
         *
         * @param errorCode the numeric error code echoed by the relay
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code echoed by the relay
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} from the inbound stanza.
         *
         * @apiNote
         * Delegates to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the client-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterStatusUpdatesResponseClientError",
                exports = "parseGetNewsletterStatusUpdatesResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * Compares two replies for value equality on both fields.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link ClientError}
         *         with equal {@link #errorCode()} and
         *         {@link #errorText()}
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
         * Returns the hash code derived from both fields.
         *
         * @return the combined hash of {@link #errorCode()} and
         *         {@link #errorText()}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug representation including both fields.
         *
         * @return a record-like rendering of this reply
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterStatusUpdatesResponse.ClientError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The variant carrying a transient relay-side failure.
     *
     * @apiNote
     * WA Web's status-updates job swallows this variant after logging;
     * the next periodic tick re-fires the same query.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterStatusUpdatesResponseServerError")
    final class ServerError implements SmaxNewslettersGetNewsletterStatusUpdatesResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text from the relay.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @apiNote
         * Mirror of {@link ClientError} for relay-side internal
         * failures; the text is optional.
         *
         * @param errorCode the numeric error code echoed by the relay
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code echoed by the relay
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} from the inbound stanza.
         *
         * @apiNote
         * Delegates to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the server-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterStatusUpdatesResponseServerError",
                exports = "parseGetNewsletterStatusUpdatesResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * Compares two replies for value equality on both fields.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link ServerError}
         *         with equal {@link #errorCode()} and
         *         {@link #errorText()}
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
         * Returns the hash code derived from both fields.
         *
         * @return the combined hash of {@link #errorCode()} and
         *         {@link #errorText()}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug representation including both fields.
         *
         * @return a record-like rendering of this reply
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterStatusUpdatesResponse.ServerError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }
}
