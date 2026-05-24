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
 * {@link SmaxNewslettersGetNewsletterMessageUpdatesRequest}.
 *
 * @apiNote
 * Pattern-match on the three permitted variants ({@link Success},
 * {@link ClientError}, {@link ServerError}) when handling a reply from
 * WA Web's
 * {@code WAWebNewsletterGetMessageUpdatesQuery.getNewsletterMessageUpdatesQuery};
 * WA Web rejects every client-error subtype as a fatal
 * {@code ServerStatusCodeError}, so Cobalt callers typically route
 * non-success variants through the configured error handler.
 */
public sealed interface SmaxNewslettersGetNewsletterMessageUpdatesResponse extends SmaxOperation.Response
        permits SmaxNewslettersGetNewsletterMessageUpdatesResponse.Success, SmaxNewslettersGetNewsletterMessageUpdatesResponse.ClientError, SmaxNewslettersGetNewsletterMessageUpdatesResponse.ServerError {

    /**
     * Dispatches the inbound IQ stanza to the first matching variant
     * parser.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code sendGetNewsletterMessageUpdatesRPC} entry-point: try
     * {@link Success}, then {@link ClientError}, then {@link ServerError}
     * in order and surface the first that parses. An empty
     * {@link Optional} means the stanza did not match any documented
     * variant.
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
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterMessageUpdatesRPC",
            exports = "sendGetNewsletterMessageUpdatesRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxNewslettersGetNewsletterMessageUpdatesResponse> of(Node node, Node request) {
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
     * The variant that carries the delta-of-message-updates batch.
     *
     * @apiNote
     * Project {@link #messages()} onto the local newsletter store; the
     * batch reuses the same
     * {@link SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage}
     * shape as the full history fetch, so the consumer can share the
     * downstream add-on parser.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterMessageUpdatesResponseSuccess")
    final class Success implements SmaxNewslettersGetNewsletterMessageUpdatesResponse {
        /**
         * The optional newsletter {@link Jid} echoed on the
         * {@code <messages>} block.
         */
        private final Jid newsletterJid;

        /**
         * The optional unix-second timestamp echoed by the relay.
         */
        private final Long timestamp;

        /**
         * The list of message-update entries returned by the relay.
         */
        private final List<SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage> messages;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Both {@code newsletterJid} and {@code timestamp} are optional
         * because the relay only echoes them when the corresponding
         * attributes were present on the wire.
         *
         * @param newsletterJid the optional echoed newsletter
         *                      {@link Jid}; may be {@code null}
         * @param timestamp     the optional echoed unix-second
         *                      timestamp; may be {@code null}
         * @param messages      the message-update entries; never
         *                      {@code null} (empty allowed)
         */
        public Success(Jid newsletterJid,
                       Long timestamp,
                       List<SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage> messages) {
            this.newsletterJid = newsletterJid;
            this.timestamp = timestamp;
            this.messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
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
         * Returns the message-update entries.
         *
         * @return an unmodifiable {@link List} of entries; never
         *         {@code null}
         */
        public List<SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage> messages() {
            return messages;
        }

        /**
         * Tries to parse a {@link Success} from the inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the IQ envelope fails
         * {@link SmaxIqResultResponseMixin} validation, the
         * {@code <message_updates>} or {@code <messages>} envelopes are
         * missing, the {@code t} attribute is negative, or any nested
         * {@code <message>} child fails its own
         * {@link SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage#of(Node)}
         * parse.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound stanza; never
         *                {@code null}
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterMessageUpdatesResponseSuccess",
                exports = "parseGetNewsletterMessageUpdatesResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var messageUpdates = node.getChild("message_updates").orElse(null);
            if (messageUpdates == null) {
                return Optional.empty();
            }
            var messagesNode = messageUpdates.getChild("messages").orElse(null);
            if (messagesNode == null) {
                return Optional.empty();
            }
            var jid = messagesNode.getAttributeAsJid("jid").orElse(null);
            Long timestamp = null;
            var tOpt = messagesNode.getAttributeAsLong("t");
            if (tOpt.isPresent()) {
                var tv = tOpt.getAsLong();
                if (tv < 0) {
                    return Optional.empty();
                }
                timestamp = tv;
            }
            var entries = new ArrayList<SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage>();
            for (var messageNode : messagesNode.getChildren("message")) {
                var entry = SmaxNewslettersGetNewsletterMessagesResponse.NewsletterMessage.of(messageNode)
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
         *         carrying equal {@link #newsletterJid()},
         *         {@link #timestamp()}, and {@link #messages()}
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
                    && Objects.equals(this.messages, that.messages);
        }

        /**
         * Returns the hash code derived from every field.
         *
         * @return the combined hash of {@link #newsletterJid()},
         *         {@link #timestamp()}, and {@link #messages()}
         */
        @Override
        public int hashCode() {
            return Objects.hash(newsletterJid, timestamp, messages);
        }

        /**
         * Returns a debug representation including every field.
         *
         * @return a record-like rendering of this reply
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterMessageUpdatesResponse.Success[newsletterJid="
                    + newsletterJid + ", timestamp=" + timestamp
                    + ", messages=" + messages + ']';
        }
    }

    /**
     * The variant carrying a relay-side client-rejection.
     *
     * @apiNote
     * WA Web routes every documented sub-error
     * ({@code ItemNotFoundIQErrorResponse},
     * {@code RateLimitedIQErrorResponse},
     * {@code BadRequestIQErrorResponse},
     * {@code SuspendedIQErrorResponse},
     * {@code UnavailableForLegalReasonsResponse}) through the same
     * {@code ServerStatusCodeError} type, so Cobalt callers can treat
     * this variant uniformly as a fatal user-facing error.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterMessageUpdatesResponseClientError")
    final class ClientError implements SmaxNewslettersGetNewsletterMessageUpdatesResponse {
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
         * The text is optional because not every relay-side error
         * carries a human-readable message; surface
         * {@link #errorCode()} when the text is empty.
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
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * which validates the {@code <iq type="error">} envelope and
         * the nested error code / text.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the client-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterMessageUpdatesResponseClientError",
                exports = "parseGetNewsletterMessageUpdatesResponseClientError",
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
         *         carrying equal {@link #errorCode()} and
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
            return "SmaxNewslettersGetNewsletterMessageUpdatesResponse.ClientError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The variant carrying a transient relay-side failure.
     *
     * @apiNote
     * WA Web also throws {@code ServerStatusCodeError} on this variant,
     * but Cobalt callers may opt into a retry via the back-off helper
     * in the consuming layer; the variant itself does not retry.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterMessageUpdatesResponseServerError")
    final class ServerError implements SmaxNewslettersGetNewsletterMessageUpdatesResponse {
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
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * which validates the {@code <iq type="error">} envelope for
         * an internal-server-error mixin.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the server-error
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterMessageUpdatesResponseServerError",
                exports = "parseGetNewsletterMessageUpdatesResponseServerError",
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
         *         carrying equal {@link #errorCode()} and
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
            return "SmaxNewslettersGetNewsletterMessageUpdatesResponse.ServerError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }
}
