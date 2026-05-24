package com.github.auties00.cobalt.node.smax.support;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound {@code smax_id=138} IQ that submits feedback labels for a previously rated
 * support-bot message to the {@code fb:thrift_iq} relay.
 *
 * @apiNote
 * Drives the support-bot message-rating surface invoked by WA Web's
 * {@code WAWebSendSupportBotFeedbackActions}; the labels enumerate the kinds of feedback the
 * user selected (one to ten entries) and pair with the rated message id.
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain
 * ({@code WASmaxOutSupportMessageFeedbackHackBaseIQSetRequestMixin} over
 * {@code WASmaxOutSupportMessageFeedbackBaseIQSetRequestMixin}) into a single
 * {@link NodeBuilder} that pins {@code xmlns="fb:thrift_iq"}, {@code smax_id=138},
 * {@code to=Jid.userServer()} and {@code type="set"}; the {@code REPEATED_CHILD(feedback, 1, 10)}
 * cap is enforced in the constructor (WA Web enforces it at marshalling time).
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSupportMessageFeedbackSendFeedbackRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutSupportMessageFeedbackHackBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSupportMessageFeedbackBaseIQSetRequestMixin")
public final class SmaxSendFeedbackRequest implements SmaxOperation.Request {
    /**
     * The optional {@code from} JID forwarded verbatim into the IQ envelope.
     *
     * @apiNote
     * Set this only when the dispatcher needs to attribute the feedback to a non-default
     * identity; WA Web's {@code WAWebSendSupportBotFeedbackActions} leaves it unset.
     */
    private final Jid iqFrom;

    /**
     * The id of the rated message.
     *
     * @apiNote
     * Routed into {@code <message id="..."/>} via WA Web's {@code STANZA_ID} marshaller.
     */
    private final String messageId;

    /**
     * The non-empty list of feedback-kind labels (one to ten entries).
     *
     * @apiNote
     * Each entry is materialised as a {@code <feedback kind="..."/>} child under
     * {@code <feedback_list>}; the labels themselves are server-side enum values surfaced by
     * the support-bot UI.
     */
    private final List<String> feedbackKinds;

    /**
     * Constructs a feedback request.
     *
     * @apiNote
     * Typically invoked by a UI handler after the user submits a message-rating form; supply
     * {@code iqFrom == null} to let the relay infer the sender from the connection.
     *
     * @param iqFrom        the optional sender JID; may be {@code null}
     * @param messageId     the rated message id; never {@code null}
     * @param feedbackKinds the feedback labels (1..10 entries); never {@code null}
     * @throws NullPointerException     if {@code messageId} or {@code feedbackKinds} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code feedbackKinds} is empty or has more than ten
     *                                  entries
     */
    public SmaxSendFeedbackRequest(Jid iqFrom, String messageId, List<String> feedbackKinds) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(feedbackKinds, "feedbackKinds cannot be null");
        if (feedbackKinds.isEmpty() || feedbackKinds.size() > 10) {
            throw new IllegalArgumentException("feedbackKinds must contain 1..10 entries");
        }
        this.iqFrom = iqFrom;
        this.messageId = messageId;
        this.feedbackKinds = List.copyOf(feedbackKinds);
    }

    /**
     * Returns the optional {@code from} JID.
     *
     * @apiNote
     * Empty when the dispatcher wants the relay to infer the sender from the connection.
     *
     * @return an {@link Optional} carrying the sender JID, or empty when omitted
     */
    public Optional<Jid> iqFrom() {
        return Optional.ofNullable(iqFrom);
    }

    /**
     * Returns the rated message id.
     *
     * @apiNote
     * Surfaces the value routed into {@code <message id>}.
     *
     * @return the message id; never {@code null}
     */
    public String messageId() {
        return messageId;
    }

    /**
     * Returns the feedback-kind labels.
     *
     * @apiNote
     * Surfaces the labels materialised as {@code <feedback kind>} children; the list is
     * unmodifiable and contains 1..10 entries.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<String> feedbackKinds() {
        return feedbackKinds;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Emits the outbound feedback IQ ready for {@link com.github.auties00.cobalt.node.smax}
     * dispatch.
     *
     * @implNote
     * This implementation builds the {@code <message>} child, materialises each feedback kind
     * as a separate {@code <feedback kind="..."/>} node under {@code <feedback_list>}, and
     * forwards {@link #iqFrom} into the envelope's {@code from} attribute (the
     * {@link NodeBuilder#attribute} call accepts a {@code null} JID).
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutSupportMessageFeedbackSendFeedbackRequest",
            exports = "makeSendFeedbackRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var messageNode = new NodeBuilder()
                .description("message")
                .attribute("id", messageId)
                .build();
        var feedbackNodes = feedbackKinds.stream()
                .map(kind -> new NodeBuilder()
                        .description("feedback")
                        .attribute("kind", kind)
                        .build())
                .toList();
        var feedbackListNode = new NodeBuilder()
                .description("feedback_list")
                .content(feedbackNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("smax_id", 138)
                .attribute("from", iqFrom)
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(List.of(messageNode, feedbackListNode));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxSendFeedbackRequest) obj;
        return Objects.equals(this.iqFrom, that.iqFrom)
                && Objects.equals(this.messageId, that.messageId)
                && Objects.equals(this.feedbackKinds, that.feedbackKinds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iqFrom, messageId, feedbackKinds);
    }

    @Override
    public String toString() {
        return "SmaxSendFeedbackRequest[iqFrom=" + iqFrom
                + ", messageId=" + messageId
                + ", feedbackKinds=" + feedbackKinds + ']';
    }
}
