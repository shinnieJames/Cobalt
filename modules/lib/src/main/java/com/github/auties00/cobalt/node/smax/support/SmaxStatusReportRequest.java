package com.github.auties00.cobalt.node.smax.support;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound {@code spam} IQ that reports an offending status (chat-status) message.
 *
 * @apiNote
 * Drives the "Report status" surface invoked by WA Web's {@code WAWebReportSpamJob}; pair with
 * {@link SmaxStatusReportResponse} to consume the relay's verdict. Use {@link #builder()} to
 * supply the required status owner / sender / timestamp / id triplet and the optional mixin
 * payloads (recipient, FRX, is-known-chat, biz-opt-out, biz-report).
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain (BaseIQSetRequest, BaseReport, FRX,
 * IsKnownChat, BizOptOut, BizReport, MessageRecipient) into a single {@link NodeBuilder} that
 * pins {@code xmlns="spam"}, {@code to=JidServer.user()} and {@code type="set"}. The
 * biz-opt-out and biz-report children attach to {@code <spam_list>}; only FRX attaches to the
 * outer {@code <iq>}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSpamStatusReportRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseReportMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamMessageRecipientMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamFRXMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamIsKnownChatMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBizOptOutMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBizReportMixin")
public final class SmaxStatusReportRequest implements SmaxOperation.Request {
    /**
     * The status-owner JID.
     *
     * @apiNote
     * Routed into {@code <spam_list jid="..."/>} via WA Web's {@code JID} marshaller.
     */
    private final Jid spamListJid;

    /**
     * The spam-flow identifier surfacing the user-facing report flow.
     *
     * @apiNote
     * Routed into {@code <spam_list spam_flow="..."/>}.
     */
    private final String spamListSpamFlow;

    /**
     * The sender JID of the offending status message.
     *
     * @apiNote
     * Routed into {@code <message from="..."/>}.
     */
    private final Jid messageFrom;

    /**
     * The status message timestamp in Unix seconds.
     *
     * @apiNote
     * Routed into {@code <message t="..."/>}.
     */
    private final long messageTimestamp;

    /**
     * The status message stanza id.
     *
     * @apiNote
     * Routed into {@code <message id="..."/>}.
     */
    private final String messageId;

    /**
     * The optional recipient JID.
     *
     * @apiNote
     * Routed into {@code <message to="..."/>} via
     * {@code WASmaxOutSpamMessageRecipientMixin}; surfaces the user the status was originally
     * sent to when reporting from a one-to-one timeline.
     */
    private final Jid messageTo;

    /**
     * The optional {@code is_known_chat} marker.
     *
     * @apiNote
     * Routed into {@code <spam_list is_known_chat="..."/>}.
     */
    private final String spamListIsKnownChat;

    /**
     * The optional pre-built {@code <biz_opt_out>} child.
     *
     * @apiNote
     * Appended under {@code <spam_list>} when present.
     */
    private final Node bizOptOutChild;

    /**
     * The optional pre-built {@code <biz_report>} child.
     *
     * @apiNote
     * Appended under {@code <spam_list>} when present.
     */
    private final Node bizReportChild;

    /**
     * The optional pre-built {@code <frx>} child.
     *
     * @apiNote
     * Appended under the outer {@code <iq>} when present.
     */
    private final Node frxChild;

    /**
     * The optional pre-built {@code <message>} child.
     *
     * @apiNote
     * When set, the entry's {@link #toNode()} embeds this node verbatim instead of synthesising
     * the {@code (from, t, id, to)} envelope from the scalar fields.
     */
    private final Node messageChild;

    /**
     * Constructs a request from the assembled {@link Builder} state.
     *
     * @apiNote
     * Invoked by {@link Builder#build()}; consumers use {@link #builder()} rather than calling
     * this constructor directly.
     *
     * @param builder the source builder; never {@code null}
     */
    private SmaxStatusReportRequest(Builder builder) {
        this.spamListJid = Objects.requireNonNull(builder.spamListJid, "spamListJid cannot be null");
        this.spamListSpamFlow = Objects.requireNonNull(builder.spamListSpamFlow,
                "spamListSpamFlow cannot be null");
        this.messageFrom = Objects.requireNonNull(builder.messageFrom, "messageFrom cannot be null");
        this.messageTimestamp = builder.messageTimestamp;
        this.messageId = Objects.requireNonNull(builder.messageId, "messageId cannot be null");
        this.messageTo = builder.messageTo;
        this.spamListIsKnownChat = builder.spamListIsKnownChat;
        this.bizOptOutChild = builder.bizOptOutChild;
        this.bizReportChild = builder.bizReportChild;
        this.frxChild = builder.frxChild;
        this.messageChild = builder.messageChild;
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @apiNote
     * The canonical entry point for assembling a status spam report.
     *
     * @return a new builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the status-owner JID.
     *
     * @apiNote
     * Surfaces the {@code <spam_list jid>} value.
     *
     * @return the JID; never {@code null}
     */
    public Jid spamListJid() {
        return spamListJid;
    }

    /**
     * Returns the spam-flow identifier.
     *
     * @apiNote
     * Surfaces the {@code <spam_list spam_flow>} value naming the user-facing surface.
     *
     * @return the spam-flow; never {@code null}
     */
    public String spamListSpamFlow() {
        return spamListSpamFlow;
    }

    /**
     * Returns the sender JID of the offending status message.
     *
     * @apiNote
     * Surfaces the value routed into {@code <message from>}.
     *
     * @return the JID; never {@code null}
     */
    public Jid messageFrom() {
        return messageFrom;
    }

    /**
     * Returns the status message timestamp.
     *
     * @apiNote
     * Surfaces the value routed into {@code <message t>} in Unix seconds.
     *
     * @return the timestamp
     */
    public long messageTimestamp() {
        return messageTimestamp;
    }

    /**
     * Returns the status message stanza id.
     *
     * @apiNote
     * Surfaces the value routed into {@code <message id>}.
     *
     * @return the id; never {@code null}
     */
    public String messageId() {
        return messageId;
    }

    /**
     * Returns the optional recipient JID.
     *
     * @apiNote
     * Surfaces the {@code <message to>} value; empty when the status was reported from a
     * surface other than a one-to-one timeline.
     *
     * @return an {@link Optional} carrying the JID, or empty when omitted
     */
    public Optional<Jid> messageTo() {
        return Optional.ofNullable(messageTo);
    }

    /**
     * Returns the optional {@code is_known_chat} marker.
     *
     * @apiNote
     * Surfaces the {@code <spam_list is_known_chat>} value.
     *
     * @return an {@link Optional} carrying the marker, or empty when omitted
     */
    public Optional<String> spamListIsKnownChat() {
        return Optional.ofNullable(spamListIsKnownChat);
    }

    /**
     * Returns the optional biz-opt-out child.
     *
     * @apiNote
     * Surfaces the pre-built {@code <biz_opt_out>} payload appended under {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> bizOptOutChild() {
        return Optional.ofNullable(bizOptOutChild);
    }

    /**
     * Returns the optional biz-report child.
     *
     * @apiNote
     * Surfaces the pre-built {@code <biz_report>} payload appended under {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> bizReportChild() {
        return Optional.ofNullable(bizReportChild);
    }

    /**
     * Returns the optional FRX child.
     *
     * @apiNote
     * Surfaces the pre-built {@code <frx>} payload appended under the outer {@code <iq>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> frxChild() {
        return Optional.ofNullable(frxChild);
    }

    /**
     * Returns the optional pre-built {@code <message>} child.
     *
     * @apiNote
     * Empty when {@link #toNode()} should synthesise the envelope from the scalar fields.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> messageChild() {
        return Optional.ofNullable(messageChild);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Emits the outbound status-report IQ ready for
     * {@link com.github.auties00.cobalt.node.smax} dispatch.
     *
     * @implNote
     * This implementation either embeds {@link #messageChild} verbatim or synthesises a
     * {@code <message>} envelope from the {@code (from, t, id, to)} scalar fields, attaches it
     * under {@code <spam_list>} together with the optional biz-opt-out and biz-report children,
     * and finally appends the optional FRX child to the outer {@code <iq>}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutSpamStatusReportRequest",
            exports = "makeStatusReportRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        Node messageNode;
        if (messageChild != null) {
            messageNode = messageChild;
        } else {
            var messageBuilder = new NodeBuilder()
                    .description("message")
                    .attribute("from", messageFrom)
                    .attribute("t", messageTimestamp)
                    .attribute("id", messageId);
            if (messageTo != null) {
                messageBuilder.attribute("to", messageTo);
            }
            messageNode = messageBuilder.build();
        }
        var spamListBuilder = new NodeBuilder()
                .description("spam_list")
                .attribute("jid", spamListJid)
                .attribute("spam_flow", spamListSpamFlow);
        if (spamListIsKnownChat != null) {
            spamListBuilder.attribute("is_known_chat", spamListIsKnownChat);
        }
        spamListBuilder.content(messageNode);
        if (bizOptOutChild != null) {
            spamListBuilder.content(bizOptOutChild);
        }
        if (bizReportChild != null) {
            spamListBuilder.content(bizReportChild);
        }
        var iqBuilder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "spam")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(spamListBuilder.build());
        if (frxChild != null) {
            iqBuilder.content(frxChild);
        }
        return iqBuilder;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxStatusReportRequest) obj;
        return this.messageTimestamp == that.messageTimestamp
                && Objects.equals(this.spamListJid, that.spamListJid)
                && Objects.equals(this.spamListSpamFlow, that.spamListSpamFlow)
                && Objects.equals(this.messageFrom, that.messageFrom)
                && Objects.equals(this.messageId, that.messageId)
                && Objects.equals(this.messageTo, that.messageTo)
                && Objects.equals(this.spamListIsKnownChat, that.spamListIsKnownChat)
                && Objects.equals(this.bizOptOutChild, that.bizOptOutChild)
                && Objects.equals(this.bizReportChild, that.bizReportChild)
                && Objects.equals(this.frxChild, that.frxChild)
                && Objects.equals(this.messageChild, that.messageChild);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spamListJid, spamListSpamFlow, messageFrom, messageTimestamp, messageId,
                messageTo, spamListIsKnownChat, bizOptOutChild, bizReportChild, frxChild, messageChild);
    }

    @Override
    public String toString() {
        return "SmaxStatusReportRequest[spamListJid=" + spamListJid
                + ", spamListSpamFlow=" + spamListSpamFlow
                + ", messageFrom=" + messageFrom
                + ", messageTimestamp=" + messageTimestamp
                + ", messageId=" + messageId
                + ", messageTo=" + messageTo + ']';
    }

    /**
     * Step-builder that assembles a {@link SmaxStatusReportRequest} from the WA Web mixin
     * inputs.
     *
     * @apiNote
     * The canonical entry point for callers that need to issue a status spam report; use
     * {@link SmaxStatusReportRequest#builder()} to obtain an instance.
     *
     * @implNote
     * This implementation collects the optional mixin payloads into private fields and validates
     * the required {@link #spamListJid}, {@link #spamListSpamFlow}, {@link #messageFrom} and
     * {@link #messageId} at setter time; the resulting {@code build()} copy is immutable.
     */
    public static final class Builder {
        /**
         * The required status-owner JID.
         *
         * @apiNote
         * Set via {@link #spamListJid(Jid)}; must be set before {@link #build()}.
         */
        private Jid spamListJid;

        /**
         * The required spam-flow string.
         *
         * @apiNote
         * Set via {@link #spamListSpamFlow(String)}; must be set before {@link #build()}.
         */
        private String spamListSpamFlow;

        /**
         * The required sender JID.
         *
         * @apiNote
         * Set via {@link #messageFrom(Jid)}; must be set before {@link #build()}.
         */
        private Jid messageFrom;

        /**
         * The status message timestamp in Unix seconds.
         *
         * @apiNote
         * Set via {@link #messageTimestamp(long)}; defaults to {@code 0} when not set.
         */
        private long messageTimestamp;

        /**
         * The required status message stanza id.
         *
         * @apiNote
         * Set via {@link #messageId(String)}; must be set before {@link #build()}.
         */
        private String messageId;

        /**
         * The optional recipient JID.
         *
         * @apiNote
         * Set via {@link #messageTo(Jid)}; {@code null} when omitted.
         */
        private Jid messageTo;

        /**
         * The optional {@code is_known_chat} marker.
         *
         * @apiNote
         * Set via {@link #spamListIsKnownChat(String)}; {@code null} when omitted.
         */
        private String spamListIsKnownChat;

        /**
         * The optional pre-built {@code <biz_opt_out>} child.
         *
         * @apiNote
         * Set via {@link #bizOptOutChild(Node)}; {@code null} when omitted.
         */
        private Node bizOptOutChild;

        /**
         * The optional pre-built {@code <biz_report>} child.
         *
         * @apiNote
         * Set via {@link #bizReportChild(Node)}; {@code null} when omitted.
         */
        private Node bizReportChild;

        /**
         * The optional pre-built {@code <frx>} child.
         *
         * @apiNote
         * Set via {@link #frxChild(Node)}; {@code null} when omitted.
         */
        private Node frxChild;

        /**
         * The optional pre-built {@code <message>} child.
         *
         * @apiNote
         * Set via {@link #messageChild(Node)}; {@code null} when {@link #toNode()} should
         * synthesise the envelope from the scalar fields.
         */
        private Node messageChild;

        /**
         * Constructs an empty builder.
         *
         * @apiNote
         * Prefer {@link SmaxStatusReportRequest#builder()} over invoking this constructor
         * directly.
         */
        public Builder() {
        }

        /**
         * Sets the status-owner JID.
         *
         * @apiNote
         * Required; the resulting {@code <spam_list jid>} attribute names the status owner.
         *
         * @param spamListJid the JID; never {@code null}
         * @return this builder
         * @throws NullPointerException if {@code spamListJid} is {@code null}
         */
        public Builder spamListJid(Jid spamListJid) {
            this.spamListJid = Objects.requireNonNull(spamListJid, "spamListJid cannot be null");
            return this;
        }

        /**
         * Sets the spam-flow identifier.
         *
         * @apiNote
         * Required; the resulting {@code <spam_list spam_flow>} attribute names the user-facing
         * surface that triggered the report.
         *
         * @param spamListSpamFlow the spam-flow; never {@code null}
         * @return this builder
         * @throws NullPointerException if {@code spamListSpamFlow} is {@code null}
         */
        public Builder spamListSpamFlow(String spamListSpamFlow) {
            this.spamListSpamFlow = Objects.requireNonNull(spamListSpamFlow,
                    "spamListSpamFlow cannot be null");
            return this;
        }

        /**
         * Sets the sender JID of the offending status.
         *
         * @apiNote
         * Required; routes into {@code <message from>}.
         *
         * @param messageFrom the JID; never {@code null}
         * @return this builder
         * @throws NullPointerException if {@code messageFrom} is {@code null}
         */
        public Builder messageFrom(Jid messageFrom) {
            this.messageFrom = Objects.requireNonNull(messageFrom, "messageFrom cannot be null");
            return this;
        }

        /**
         * Sets the status message timestamp.
         *
         * @apiNote
         * Routes into {@code <message t>} in Unix seconds.
         *
         * @param messageTimestamp the timestamp
         * @return this builder
         */
        public Builder messageTimestamp(long messageTimestamp) {
            this.messageTimestamp = messageTimestamp;
            return this;
        }

        /**
         * Sets the status message stanza id.
         *
         * @apiNote
         * Required; routes into {@code <message id>}.
         *
         * @param messageId the id; never {@code null}
         * @return this builder
         * @throws NullPointerException if {@code messageId} is {@code null}
         */
        public Builder messageId(String messageId) {
            this.messageId = Objects.requireNonNull(messageId, "messageId cannot be null");
            return this;
        }

        /**
         * Sets the optional recipient JID.
         *
         * @apiNote
         * Routes into {@code <message to>} when set; surfaces the user the status was
         * originally sent to.
         *
         * @param messageTo the JID; may be {@code null}
         * @return this builder
         */
        public Builder messageTo(Jid messageTo) {
            this.messageTo = messageTo;
            return this;
        }

        /**
         * Sets the optional {@code is_known_chat} marker.
         *
         * @apiNote
         * Routes into {@code <spam_list is_known_chat>} when set.
         *
         * @param spamListIsKnownChat the marker; may be {@code null}
         * @return this builder
         */
        public Builder spamListIsKnownChat(String spamListIsKnownChat) {
            this.spamListIsKnownChat = spamListIsKnownChat;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <biz_opt_out>} child.
         *
         * @apiNote
         * Routes under {@code <spam_list>} when set; only meaningful for reports against
         * business statuses.
         *
         * @param bizOptOutChild the node; may be {@code null}
         * @return this builder
         */
        public Builder bizOptOutChild(Node bizOptOutChild) {
            this.bizOptOutChild = bizOptOutChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <biz_report>} child.
         *
         * @apiNote
         * Routes under {@code <spam_list>} when set; only meaningful for reports against
         * business statuses.
         *
         * @param bizReportChild the node; may be {@code null}
         * @return this builder
         */
        public Builder bizReportChild(Node bizReportChild) {
            this.bizReportChild = bizReportChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <frx>} child.
         *
         * @apiNote
         * Routes under the outer {@code <iq>} when set.
         *
         * @param frxChild the node; may be {@code null}
         * @return this builder
         */
        public Builder frxChild(Node frxChild) {
            this.frxChild = frxChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <message>} child.
         *
         * @apiNote
         * When set, {@link #toNode()} embeds the supplied node verbatim instead of synthesising
         * the envelope from the scalar fields; use when WA Web has already produced a richer
         * message node carrying additional mixin payloads.
         *
         * @param messageChild the node; may be {@code null}
         * @return this builder
         */
        public Builder messageChild(Node messageChild) {
            this.messageChild = messageChild;
            return this;
        }

        /**
         * Builds an immutable {@link SmaxStatusReportRequest} from the accumulated state.
         *
         * @apiNote
         * Invoke once after all required and optional fields have been set; the resulting
         * instance can be reused across dispatches.
         *
         * @return a new {@link SmaxStatusReportRequest}; never {@code null}
         * @throws NullPointerException if any required field was not set
         */
        public SmaxStatusReportRequest build() {
            return new SmaxStatusReportRequest(this);
        }
    }
}
