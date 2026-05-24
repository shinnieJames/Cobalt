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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound {@code spam} IQ that reports an individual user or business, with the offending
 * messages, calls and user-initiated extensions inlined under {@code <spam_list>}.
 *
 * @apiNote
 * Drives the "Report contact" / "Report business" surfaces invoked by WA Web's
 * {@code WAWebReportSpamJob}; pair with {@link SmaxIndividualReportResponse} to consume the
 * relay's verdict. The {@link Builder} mirrors WA Web's optional mixin chain (biz-opt-out,
 * biz-report, ui-state-set, TC-token, FRX, is-known-chat); a minimal report only requires
 * {@link Builder#spamListSpamFlow(String)}.
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain (BaseIQSetRequest, BaseReport, FRX,
 * IsKnownChat, BizOptOut, BizReport, UIStateSet, TCToken) into a single {@link NodeBuilder}
 * that pins {@code xmlns="spam"}, {@code to=JidServer.user()} and {@code type="set"}. Per WA
 * Web's optional-merge wiring the biz-opt-out / biz-report / ui-state-set / TC-token children
 * attach to {@code <spam_list>}, not to the outer {@code <iq>}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSpamIndividualReportRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseReportMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamFRXMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamIsKnownChatMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBizOptOutMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBizReportMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamUIStateSetMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamTCTokenMixin")
public final class SmaxIndividualReportRequest implements SmaxOperation.Request {
    /**
     * The optional reportee JID.
     *
     * @apiNote
     * Routed into {@code <spam_list jid="..."/>} when present; WA Web allows omission when the
     * subject can be inferred from the attached message attribution alone.
     */
    private final Jid spamListJid;

    /**
     * The spam-flow identifier surfacing the user-facing report flow.
     *
     * @apiNote
     * Routed into {@code <spam_list spam_flow="..."/>}; carries the WA Web enum that names the
     * surface from which the report was issued.
     */
    private final String spamListSpamFlow;

    /**
     * The optional {@code is_known_chat} marker.
     *
     * @apiNote
     * Routed into {@code <spam_list is_known_chat="..."/>} when present.
     */
    private final String spamListIsKnownChat;

    /**
     * The optional pre-built {@code <biz_opt_out>} child.
     *
     * @apiNote
     * Appended under {@code <spam_list>} when present; surfaces the business-opt-out flag for
     * reports against business accounts.
     */
    private final Node bizOptOutChild;

    /**
     * The optional pre-built {@code <ui_state_set>} child.
     *
     * @apiNote
     * Appended under {@code <spam_list>} when present; surfaces UI-state metadata captured at
     * report time.
     */
    private final Node uistateSetChild;

    /**
     * The optional pre-built {@code <biz_report>} child.
     *
     * @apiNote
     * Appended under {@code <spam_list>} when present; surfaces the business-report payload
     * for reports against business accounts.
     */
    private final Node bizReportChild;

    /**
     * The optional pre-built {@code <tc_token>} child.
     *
     * @apiNote
     * Appended under {@code <spam_list>} when present; surfaces the trust-center token bound
     * to the report.
     */
    private final Node tcTokenChild;

    /**
     * The optional pre-built {@code <frx>} (free-form reporting extensions) child.
     *
     * @apiNote
     * Appended under the outer {@code <iq>} when present; bundles tagset, context and
     * parameters that the FRX backend consumes.
     */
    private final Node frxChild;

    /**
     * The pre-built {@code <message>} children harvested from the offending chat (WA Web caps
     * this at 210).
     *
     * @apiNote
     * Appended in insertion order under {@code <spam_list>}; never {@code null}.
     */
    private final List<Node> messageChildren;

    /**
     * The pre-built {@code <call>} children harvested from the offending chat (WA Web caps this
     * at 5).
     *
     * @apiNote
     * Appended in insertion order under {@code <spam_list>}; never {@code null}.
     */
    private final List<Node> callChildren;

    /**
     * The pre-built {@code <user_initiated_extension>} children (WA Web caps this at 5).
     *
     * @apiNote
     * Appended in insertion order under {@code <spam_list>}; never {@code null}.
     */
    private final List<Node> userInitiatedExtensionChildren;

    /**
     * Constructs a request from the assembled {@link Builder} state.
     *
     * @apiNote
     * Invoked by {@link Builder#build()}; consumers use {@link #builder()} rather than calling
     * this constructor directly.
     *
     * @param builder the source builder; never {@code null}
     */
    private SmaxIndividualReportRequest(Builder builder) {
        this.spamListJid = builder.spamListJid;
        this.spamListSpamFlow = Objects.requireNonNull(builder.spamListSpamFlow,
                "spamListSpamFlow cannot be null");
        this.spamListIsKnownChat = builder.spamListIsKnownChat;
        this.bizOptOutChild = builder.bizOptOutChild;
        this.uistateSetChild = builder.uistateSetChild;
        this.bizReportChild = builder.bizReportChild;
        this.tcTokenChild = builder.tcTokenChild;
        this.frxChild = builder.frxChild;
        this.messageChildren = List.copyOf(builder.messageChildren);
        this.callChildren = List.copyOf(builder.callChildren);
        this.userInitiatedExtensionChildren = List.copyOf(builder.userInitiatedExtensionChildren);
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @apiNote
     * The canonical entry point for assembling an individual spam report.
     *
     * @return a new builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the optional reportee JID.
     *
     * @apiNote
     * Surfaces the {@code <spam_list jid>} value; empty when WA Web inferred the subject from
     * the message attribution alone.
     *
     * @return an {@link Optional} carrying the JID, or empty when omitted
     */
    public Optional<Jid> spamListJid() {
        return Optional.ofNullable(spamListJid);
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
     * Returns the optional ui-state-set child.
     *
     * @apiNote
     * Surfaces the pre-built {@code <ui_state_set>} payload appended under {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> uistateSetChild() {
        return Optional.ofNullable(uistateSetChild);
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
     * Returns the optional TC-token child.
     *
     * @apiNote
     * Surfaces the pre-built {@code <tc_token>} payload appended under {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> tcTokenChild() {
        return Optional.ofNullable(tcTokenChild);
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
     * Returns the offending-message children.
     *
     * @apiNote
     * Surfaces the {@code <message>} children captured from the chat at report time.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Node> messageChildren() {
        return messageChildren;
    }

    /**
     * Returns the offending-call children.
     *
     * @apiNote
     * Surfaces the {@code <call>} children captured from the chat at report time.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Node> callChildren() {
        return callChildren;
    }

    /**
     * Returns the user-initiated-extension children.
     *
     * @apiNote
     * Surfaces the {@code <user_initiated_extension>} children captured at report time.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Node> userInitiatedExtensionChildren() {
        return userInitiatedExtensionChildren;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Emits the outbound individual-report IQ ready for
     * {@link com.github.auties00.cobalt.node.smax} dispatch.
     *
     * @implNote
     * This implementation builds the {@code <spam_list>} child with attributes first
     * (skipping {@code jid} when {@link #spamListJid} is {@code null}, per WA Web's
     * {@code OPTIONAL} JID semantics), then concatenates the message / call /
     * user-initiated-extension children in WA Web's documented order followed by the optional
     * biz-opt-out / ui-state-set / biz-report / TC-token children; only FRX is attached to the
     * outer {@code <iq>}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutSpamIndividualReportRequest",
            exports = "makeIndividualReportRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var spamListBuilder = new NodeBuilder()
                .description("spam_list")
                .attribute("spam_flow", spamListSpamFlow);
        if (spamListJid != null) {
            spamListBuilder.attribute("jid", spamListJid);
        }
        if (spamListIsKnownChat != null) {
            spamListBuilder.attribute("is_known_chat", spamListIsKnownChat);
        }
        var spamListChildren = new ArrayList<Node>(
                messageChildren.size() + callChildren.size() + userInitiatedExtensionChildren.size());
        spamListChildren.addAll(messageChildren);
        spamListChildren.addAll(callChildren);
        spamListChildren.addAll(userInitiatedExtensionChildren);
        if (bizOptOutChild != null) {
            spamListChildren.add(bizOptOutChild);
        }
        if (uistateSetChild != null) {
            spamListChildren.add(uistateSetChild);
        }
        if (bizReportChild != null) {
            spamListChildren.add(bizReportChild);
        }
        if (tcTokenChild != null) {
            spamListChildren.add(tcTokenChild);
        }
        if (!spamListChildren.isEmpty()) {
            spamListBuilder.content(spamListChildren);
        }
        var iqChildren = new ArrayList<Node>();
        iqChildren.add(spamListBuilder.build());
        if (frxChild != null) {
            iqChildren.add(frxChild);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "spam")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(iqChildren);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxIndividualReportRequest) obj;
        return Objects.equals(this.spamListJid, that.spamListJid)
                && Objects.equals(this.spamListSpamFlow, that.spamListSpamFlow)
                && Objects.equals(this.spamListIsKnownChat, that.spamListIsKnownChat)
                && Objects.equals(this.bizOptOutChild, that.bizOptOutChild)
                && Objects.equals(this.uistateSetChild, that.uistateSetChild)
                && Objects.equals(this.bizReportChild, that.bizReportChild)
                && Objects.equals(this.tcTokenChild, that.tcTokenChild)
                && Objects.equals(this.frxChild, that.frxChild)
                && Objects.equals(this.messageChildren, that.messageChildren)
                && Objects.equals(this.callChildren, that.callChildren)
                && Objects.equals(this.userInitiatedExtensionChildren, that.userInitiatedExtensionChildren);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spamListJid, spamListSpamFlow, spamListIsKnownChat,
                bizOptOutChild, uistateSetChild, bizReportChild, tcTokenChild, frxChild,
                messageChildren, callChildren, userInitiatedExtensionChildren);
    }

    @Override
    public String toString() {
        return "SmaxIndividualReportRequest[spamListJid=" + spamListJid
                + ", spamListSpamFlow=" + spamListSpamFlow
                + ", spamListIsKnownChat=" + spamListIsKnownChat
                + ", messageChildren=" + messageChildren.size()
                + ", callChildren=" + callChildren.size()
                + ", userInitiatedExtensionChildren=" + userInitiatedExtensionChildren.size() + ']';
    }

    /**
     * Step-builder that assembles a {@link SmaxIndividualReportRequest} from the WA Web mixin
     * inputs.
     *
     * @apiNote
     * The canonical entry point for callers that need to issue an individual spam report; use
     * {@link SmaxIndividualReportRequest#builder()} to obtain an instance.
     *
     * @implNote
     * This implementation collects the optional mixin payloads into private fields and validates
     * the required {@link #spamListSpamFlow} at setter time; the resulting {@code build()} copy
     * is immutable.
     */
    public static final class Builder {
        /**
         * The optional reportee JID.
         *
         * @apiNote
         * Set via {@link #spamListJid(Jid)}; {@code null} when WA Web infers the subject from
         * the attached message attribution alone.
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
         * The optional pre-built {@code <ui_state_set>} child.
         *
         * @apiNote
         * Set via {@link #uistateSetChild(Node)}; {@code null} when omitted.
         */
        private Node uistateSetChild;

        /**
         * The optional pre-built {@code <biz_report>} child.
         *
         * @apiNote
         * Set via {@link #bizReportChild(Node)}; {@code null} when omitted.
         */
        private Node bizReportChild;

        /**
         * The optional pre-built {@code <tc_token>} child.
         *
         * @apiNote
         * Set via {@link #tcTokenChild(Node)}; {@code null} when omitted.
         */
        private Node tcTokenChild;

        /**
         * The optional pre-built {@code <frx>} child.
         *
         * @apiNote
         * Set via {@link #frxChild(Node)}; {@code null} when omitted.
         */
        private Node frxChild;

        /**
         * The accumulated {@code <message>} children.
         *
         * @apiNote
         * Appended via {@link #addMessageChild(Node)}; defaults to an empty list.
         */
        private final List<Node> messageChildren = new ArrayList<>();

        /**
         * The accumulated {@code <call>} children.
         *
         * @apiNote
         * Appended via {@link #addCallChild(Node)}; defaults to an empty list.
         */
        private final List<Node> callChildren = new ArrayList<>();

        /**
         * The accumulated {@code <user_initiated_extension>} children.
         *
         * @apiNote
         * Appended via {@link #addUserInitiatedExtensionChild(Node)}; defaults to an empty
         * list.
         */
        private final List<Node> userInitiatedExtensionChildren = new ArrayList<>();

        /**
         * Constructs an empty builder.
         *
         * @apiNote
         * Prefer {@link SmaxIndividualReportRequest#builder()} over invoking this constructor
         * directly.
         */
        public Builder() {
        }

        /**
         * Sets the optional reportee JID.
         *
         * @apiNote
         * Routes into {@code <spam_list jid>} when set; leave unset when WA Web's behaviour of
         * inferring the subject from the attached message attribution is desired.
         *
         * @param spamListJid the JID; may be {@code null}
         * @return this builder
         */
        public Builder spamListJid(Jid spamListJid) {
            this.spamListJid = spamListJid;
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
         * Routes into {@code <spam_list>} when set; only meaningful for reports against
         * business accounts.
         *
         * @param bizOptOutChild the node; may be {@code null}
         * @return this builder
         */
        public Builder bizOptOutChild(Node bizOptOutChild) {
            this.bizOptOutChild = bizOptOutChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <ui_state_set>} child.
         *
         * @apiNote
         * Routes into {@code <spam_list>} when set; surfaces UI-state metadata captured at
         * report time.
         *
         * @param uistateSetChild the node; may be {@code null}
         * @return this builder
         */
        public Builder uistateSetChild(Node uistateSetChild) {
            this.uistateSetChild = uistateSetChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <biz_report>} child.
         *
         * @apiNote
         * Routes into {@code <spam_list>} when set; only meaningful for reports against
         * business accounts.
         *
         * @param bizReportChild the node; may be {@code null}
         * @return this builder
         */
        public Builder bizReportChild(Node bizReportChild) {
            this.bizReportChild = bizReportChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <tc_token>} child.
         *
         * @apiNote
         * Routes into {@code <spam_list>} when set; surfaces the trust-center token bound to
         * the report.
         *
         * @param tcTokenChild the node; may be {@code null}
         * @return this builder
         */
        public Builder tcTokenChild(Node tcTokenChild) {
            this.tcTokenChild = tcTokenChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <frx>} child.
         *
         * @apiNote
         * Routes under the outer {@code <iq>} when set; the caller is responsible for building
         * the FRX payload per the WA Web schema.
         *
         * @param frxChild the node; may be {@code null}
         * @return this builder
         */
        public Builder frxChild(Node frxChild) {
            this.frxChild = frxChild;
            return this;
        }

        /**
         * Appends a pre-built {@code <message>} child to the spam-list payload.
         *
         * @apiNote
         * WA Web caps the count at 210; this implementation does not enforce the cap and lets
         * the relay reject oversize lists.
         *
         * @param messageNode the node; never {@code null}
         * @return this builder
         * @throws NullPointerException if {@code messageNode} is {@code null}
         */
        public Builder addMessageChild(Node messageNode) {
            Objects.requireNonNull(messageNode, "messageNode cannot be null");
            messageChildren.add(messageNode);
            return this;
        }

        /**
         * Appends a pre-built {@code <call>} child to the spam-list payload.
         *
         * @apiNote
         * WA Web caps the count at 5; this implementation does not enforce the cap and lets the
         * relay reject oversize lists.
         *
         * @param callNode the node; never {@code null}
         * @return this builder
         * @throws NullPointerException if {@code callNode} is {@code null}
         */
        public Builder addCallChild(Node callNode) {
            Objects.requireNonNull(callNode, "callNode cannot be null");
            callChildren.add(callNode);
            return this;
        }

        /**
         * Appends a pre-built {@code <user_initiated_extension>} child to the spam-list
         * payload.
         *
         * @apiNote
         * WA Web caps the count at 5; this implementation does not enforce the cap and lets
         * the relay reject oversize lists.
         *
         * @param node the node; never {@code null}
         * @return this builder
         * @throws NullPointerException if {@code node} is {@code null}
         */
        public Builder addUserInitiatedExtensionChild(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            userInitiatedExtensionChildren.add(node);
            return this;
        }

        /**
         * Builds an immutable {@link SmaxIndividualReportRequest} from the accumulated state.
         *
         * @apiNote
         * Invoke once after all required and optional fields have been set; the resulting
         * instance can be reused across dispatches.
         *
         * @return a new {@link SmaxIndividualReportRequest}; never {@code null}
         * @throws NullPointerException if {@code spamListSpamFlow} was not set
         */
        public SmaxIndividualReportRequest build() {
            return new SmaxIndividualReportRequest(this);
        }
    }
}
