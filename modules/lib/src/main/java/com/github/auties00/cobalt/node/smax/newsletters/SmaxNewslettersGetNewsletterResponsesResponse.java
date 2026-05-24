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
 * {@link SmaxNewslettersGetNewsletterResponsesRequest}.
 *
 * @apiNote
 * Pattern-match on the three permitted variants ({@link Success},
 * {@link ClientError}, {@link ServerError}) when handling a reply on
 * the Channels admin question-responses panel; WA Web's
 * {@code WAWebNewsletterGetQuestionResponsesQuery.getQuestionResponsesQuery}
 * additionally honours the
 * {@code UnauthorizedIQErrorResponse} sub-error within
 * {@link ClientError} for non-admin callers.
 */
public sealed interface SmaxNewslettersGetNewsletterResponsesResponse extends SmaxOperation.Response
        permits SmaxNewslettersGetNewsletterResponsesResponse.Success, SmaxNewslettersGetNewsletterResponsesResponse.ClientError, SmaxNewslettersGetNewsletterResponsesResponse.ServerError {

    /**
     * Dispatches the inbound IQ stanza to the first matching variant
     * parser.
     *
     * @apiNote
     * Mirrors WA Web's {@code sendGetNewsletterResponsesRPC}
     * entry-point: try {@link Success}, then {@link ClientError}, then
     * {@link ServerError} in order. Returns {@link Optional#empty()}
     * when none match.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxNewslettersGetNewsletterResponsesRPC",
            exports = "sendGetNewsletterResponsesRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxNewslettersGetNewsletterResponsesResponse> of(Node node, Node request) {
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
     * The variant that carries the requested response slice.
     *
     * @apiNote
     * Project {@link #questionResponses()} onto the admin UI; each
     * entry's underlying {@link Node} exposes the variable-shape
     * content-type / payload children.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterResponsesResponseSuccess")
    final class Success implements SmaxNewslettersGetNewsletterResponsesResponse {
        /**
         * The newsletter {@link Jid} echoed by the relay on the
         * {@code <iq from>} attribute.
         */
        private final Jid from;

        /**
         * The echoed server-id of the question whose responses are
         * carried in this slice.
         */
        private final long questionResponsesServerId;

        /**
         * The list of question-response entries returned by the relay.
         */
        private final List<QuestionResponse> questionResponses;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Both {@code from} and {@code questionResponses} are required;
         * an absent {@code from} on the wire breaks the parser
         * upstream.
         *
         * @param from                      the echoed newsletter
         *                                  {@link Jid}; never
         *                                  {@code null}
         * @param questionResponsesServerId the echoed question
         *                                  server-id
         * @param questionResponses         the response entries; never
         *                                  {@code null}
         * @throws NullPointerException if {@code from} or
         *                              {@code questionResponses} is
         *                              {@code null}
         */
        public Success(Jid from, long questionResponsesServerId,
                       List<QuestionResponse> questionResponses) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
            this.questionResponsesServerId = questionResponsesServerId;
            this.questionResponses = List.copyOf(Objects.requireNonNull(questionResponses,
                    "questionResponses cannot be null"));
        }

        /**
         * Returns the echoed newsletter {@link Jid}.
         *
         * @return the newsletter {@link Jid}; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Returns the echoed question server-id.
         *
         * @return the question server-id
         */
        public long questionResponsesServerId() {
            return questionResponsesServerId;
        }

        /**
         * Returns the response entries.
         *
         * @return an unmodifiable {@link List} of entries; never
         *         {@code null}
         */
        public List<QuestionResponse> questionResponses() {
            return questionResponses;
        }

        /**
         * Tries to parse a {@link Success} from the inbound stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the IQ envelope fails
         * {@link SmaxIqResultResponseMixin} validation, the
         * {@code from} attribute is missing, the
         * {@code <question_responses>} envelope is missing, the
         * {@code server_id} is outside {@code [99, 2147476647]}, or
         * any {@code <question_response>} child fails its own parse.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterResponsesResponseSuccess",
                exports = "parseGetNewsletterResponsesResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null) {
                return Optional.empty();
            }
            var questionResponsesNode = node.getChild("question_responses").orElse(null);
            if (questionResponsesNode == null) {
                return Optional.empty();
            }
            var serverIdOpt = questionResponsesNode.getAttributeAsLong("server_id");
            if (serverIdOpt.isEmpty()) {
                return Optional.empty();
            }
            var serverId = serverIdOpt.getAsLong();
            if (serverId < 99 || serverId > 2147476647L) {
                return Optional.empty();
            }
            var entries = new ArrayList<QuestionResponse>();
            for (var qr : questionResponsesNode.getChildren("question_response")) {
                var entry = QuestionResponse.of(qr).orElse(null);
                if (entry == null) {
                    return Optional.empty();
                }
                entries.add(entry);
            }
            return Optional.of(new Success(from, serverId, entries));
        }

        /**
         * Compares two replies for value equality on every field.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link Success}
         *         carrying equal field values
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
            return this.questionResponsesServerId == that.questionResponsesServerId
                    && Objects.equals(this.from, that.from)
                    && Objects.equals(this.questionResponses, that.questionResponses);
        }

        /**
         * Returns the hash code derived from every field.
         *
         * @return the combined hash of every field
         */
        @Override
        public int hashCode() {
            return Objects.hash(from, questionResponsesServerId, questionResponses);
        }

        /**
         * Returns a debug representation including every field.
         *
         * @return a record-like rendering of this reply
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterResponsesResponse.Success[from=" + from
                    + ", questionResponsesServerId=" + questionResponsesServerId
                    + ", questionResponses=" + questionResponses + ']';
        }
    }

    /**
     * One typed projection of a {@code <question_response>} entry.
     *
     * @apiNote
     * Each entry represents a subscriber's free-form reply against a
     * newsletter question post. The underlying {@link Node} exposes
     * the variable-shape content-type / payload children for downstream
     * rendering on the admin UI.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersQuestionResponseMessageMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersQuestionResponseSenderMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersQuestionResponseFlagsMixin")
    final class QuestionResponse {
        /**
         * The message stanza id.
         */
        private final String messageId;

        /**
         * The message unix-second timestamp.
         */
        private final long messageTimestamp;

        /**
         * Whether the message was authored by the connected client.
         */
        private final boolean fromSelf;

        /**
         * The optional anonymised sender {@link Jid} (LID form).
         */
        private final Jid senderLid;

        /**
         * The optional sender notify-name.
         */
        private final String senderNotifyName;

        /**
         * The sender's profile-picture direct-path.
         */
        private final String senderPictureDirectPath;

        /**
         * Whether the question owner has explicitly replied to this
         * response.
         */
        private final boolean hasRepliedFlag;

        /**
         * The underlying {@link Node} exposing the content / payload
         * children.
         */
        private final Node raw;

        /**
         * Constructs a new question-response projection.
         *
         * @apiNote
         * {@code senderLid} and {@code senderNotifyName} are optional
         * because the relay anonymises subscribers it does not have
         * full identity for; {@code senderPictureDirectPath} is always
         * present per WA Web's wire schema.
         *
         * @param messageId               the message stanza id; never
         *                                {@code null}
         * @param messageTimestamp        the unix-second timestamp
         * @param fromSelf                whether the message was
         *                                authored by self
         * @param senderLid               the optional sender LID; may
         *                                be {@code null}
         * @param senderNotifyName        the optional notify-name; may
         *                                be {@code null}
         * @param senderPictureDirectPath the picture direct-path; never
         *                                {@code null}
         * @param hasRepliedFlag          whether the {@code <replied/>}
         *                                marker was present
         * @param raw                     the underlying {@link Node};
         *                                never {@code null}
         * @throws NullPointerException if {@code messageId},
         *                              {@code senderPictureDirectPath},
         *                              or {@code raw} is {@code null}
         */
        public QuestionResponse(String messageId, long messageTimestamp, boolean fromSelf,
                                Jid senderLid, String senderNotifyName,
                                String senderPictureDirectPath, boolean hasRepliedFlag, Node raw) {
            this.messageId = Objects.requireNonNull(messageId, "messageId cannot be null");
            this.messageTimestamp = messageTimestamp;
            this.fromSelf = fromSelf;
            this.senderLid = senderLid;
            this.senderNotifyName = senderNotifyName;
            this.senderPictureDirectPath = Objects.requireNonNull(senderPictureDirectPath,
                    "senderPictureDirectPath cannot be null");
            this.hasRepliedFlag = hasRepliedFlag;
            this.raw = Objects.requireNonNull(raw, "raw cannot be null");
        }

        /**
         * Returns the message stanza id.
         *
         * @return the stanza id; never {@code null}
         */
        public String messageId() {
            return messageId;
        }

        /**
         * Returns the message unix-second timestamp.
         *
         * @return the timestamp
         */
        public long messageTimestamp() {
            return messageTimestamp;
        }

        /**
         * Returns whether the message was authored by the connected
         * client.
         *
         * @return {@code true} when {@code is_sender="true"} was
         *         present on the wire
         */
        public boolean fromSelf() {
            return fromSelf;
        }

        /**
         * Returns the optional anonymised sender LID.
         *
         * @return an {@link Optional} carrying the LID, or empty when
         *         the relay omitted it
         */
        public Optional<Jid> senderLid() {
            return Optional.ofNullable(senderLid);
        }

        /**
         * Returns the optional sender notify-name.
         *
         * @return an {@link Optional} carrying the notify-name, or
         *         empty when the relay omitted it
         */
        public Optional<String> senderNotifyName() {
            return Optional.ofNullable(senderNotifyName);
        }

        /**
         * Returns the sender's profile-picture direct-path.
         *
         * @return the direct-path; never {@code null}
         */
        public String senderPictureDirectPath() {
            return senderPictureDirectPath;
        }

        /**
         * Returns whether the question owner has explicitly replied to
         * this response.
         *
         * @return {@code true} when the {@code <replied/>} flag was
         *         present
         */
        public boolean hasRepliedFlag() {
            return hasRepliedFlag;
        }

        /**
         * Returns the underlying {@link Node}.
         *
         * @return the raw {@link Node} exposing the content children;
         *         never {@code null}
         */
        public Node raw() {
            return raw;
        }

        /**
         * Tries to parse a {@link QuestionResponse} from a
         * {@code <question_response>} {@link Node}.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the description is not
         * {@code question_response}, the nested {@code <message>}
         * envelope is missing or carries an out-of-range {@code t}
         * (outside {@code [1577865600, 4102473600]}), or any required
         * child of {@code <sender>} (including {@code <picture
         * direct_path>}) is missing.
         *
         * @param node the source {@link Node}; never {@code null}
         * @return an {@link Optional} carrying the parsed entry, or
         *         empty when the node fails validation
         * @throws NullPointerException if {@code node} is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterResponsesResponseSuccess",
                exports = "parseGetNewsletterResponsesResponseSuccessQuestionResponsesQuestionResponse",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<QuestionResponse> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("question_response")) {
                return Optional.empty();
            }
            var messageNode = node.getChild("message").orElse(null);
            if (messageNode == null) {
                return Optional.empty();
            }
            var messageId = messageNode.getAttributeAsString("id").orElse(null);
            if (messageId == null) {
                return Optional.empty();
            }
            var tOpt = messageNode.getAttributeAsLong("t");
            if (tOpt.isEmpty()) {
                return Optional.empty();
            }
            var messageT = tOpt.getAsLong();
            if (messageT < 1577865600L || messageT > 4102473600L) {
                return Optional.empty();
            }
            var fromSelf = messageNode.hasAttribute("is_sender", "true");
            var senderNode = node.getChild("sender").orElse(null);
            if (senderNode == null) {
                return Optional.empty();
            }
            var pictureNode = senderNode.getChild("picture").orElse(null);
            if (pictureNode == null) {
                return Optional.empty();
            }
            var directPath = pictureNode.getAttributeAsString("direct_path").orElse(null);
            if (directPath == null) {
                return Optional.empty();
            }
            var senderLid = senderNode.getAttributeAsJid("lid").orElse(null);
            var notifyName = senderNode.getAttributeAsString("notify_name").orElse(null);
            var flagsNode = node.getChild("flags").orElse(null);
            var hasReplied = flagsNode != null && flagsNode.getChild("replied").isPresent();
            return Optional.of(new QuestionResponse(messageId, messageT, fromSelf,
                    senderLid, notifyName, directPath, hasReplied, node));
        }

        /**
         * Compares two entries for value equality on every field.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a
         *         {@link QuestionResponse} with equal field values
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (QuestionResponse) obj;
            return this.messageTimestamp == that.messageTimestamp
                    && this.fromSelf == that.fromSelf
                    && this.hasRepliedFlag == that.hasRepliedFlag
                    && Objects.equals(this.messageId, that.messageId)
                    && Objects.equals(this.senderLid, that.senderLid)
                    && Objects.equals(this.senderNotifyName, that.senderNotifyName)
                    && Objects.equals(this.senderPictureDirectPath, that.senderPictureDirectPath)
                    && Objects.equals(this.raw, that.raw);
        }

        /**
         * Returns the hash code derived from every field.
         *
         * @return the combined hash of every field
         */
        @Override
        public int hashCode() {
            return Objects.hash(messageId, messageTimestamp, fromSelf, senderLid, senderNotifyName,
                    senderPictureDirectPath, hasRepliedFlag, raw);
        }

        /**
         * Returns a debug representation including the typed fields.
         *
         * @return a record-like rendering of this entry, excluding the
         *         underlying {@link Node} for brevity
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterResponsesResponse.QuestionResponse[messageId="
                    + messageId
                    + ", messageTimestamp=" + messageTimestamp
                    + ", fromSelf=" + fromSelf
                    + ", senderLid=" + senderLid
                    + ", senderNotifyName=" + senderNotifyName
                    + ", senderPictureDirectPath=" + senderPictureDirectPath
                    + ", hasRepliedFlag=" + hasRepliedFlag + ']';
        }
    }

    /**
     * The variant carrying a relay-side client-rejection.
     *
     * @apiNote
     * WA Web's
     * {@code WAWebNewsletterGetQuestionResponsesQuery.getQuestionResponsesQuery}
     * accepts a wider sub-error palette here than the other RPCs in
     * this family: {@code ItemNotFoundIQErrorResponse},
     * {@code RateLimitedIQErrorResponse},
     * {@code BadRequestIQErrorResponse},
     * {@code SuspendedIQErrorResponse},
     * {@code NotAllowedIQErrorResponse},
     * {@code UnauthorizedIQErrorResponse}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterResponsesResponseClientError")
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterResponsesClientErrors")
    final class ClientError implements SmaxNewslettersGetNewsletterResponsesResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterResponsesResponseClientError",
                exports = "parseGetNewsletterResponsesResponseClientError",
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
            return "SmaxNewslettersGetNewsletterResponsesResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The variant carrying a transient relay-side failure.
     *
     * @apiNote
     * WA Web rejects the {@code Promise} with a
     * {@code ServerStatusCodeError} on this variant; Cobalt callers
     * may retry through the consuming layer's back-off helper.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInNewslettersGetNewsletterResponsesResponseServerError")
    final class ServerError implements SmaxNewslettersGetNewsletterResponsesResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInNewslettersGetNewsletterResponsesResponseServerError",
                exports = "parseGetNewsletterResponsesResponseServerError",
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
            return "SmaxNewslettersGetNewsletterResponsesResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
