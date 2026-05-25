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
 * Reports an individual user or business as an outbound {@code spam} IQ, inlining the offending
 * messages, calls and user-initiated extensions under {@code <spam_list>}.
 *
 * <p>A minimal report requires only {@link Builder#spamListSpamFlow(String)}; the reportee JID,
 * is-known-chat marker, business-opt-out, business-report, ui-state-set, TC-token and FRX fields
 * are optional. The relay's verdict is parsed by {@link SmaxIndividualReportResponse}.
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain into a single {@link NodeBuilder} that pins
 * {@code xmlns="spam"}, {@code to=JidServer.user()} and {@code type="set"}. The biz-opt-out,
 * biz-report, ui-state-set and TC-token children attach to {@code <spam_list>}, not to the outer
 * {@code <iq>}; only FRX attaches to the outer {@code <iq>}.
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
     * Holds the optional reportee JID routed into {@code <spam_list jid="..."/>} when present;
     * may be omitted when the subject is inferable from the attached message attribution.
     */
    private final Jid spamListJid;

    /**
     * Holds the spam-flow identifier routed into {@code <spam_list spam_flow="..."/>}, naming the
     * user-facing surface that issued the report.
     */
    private final String spamListSpamFlow;

    /**
     * Holds the optional {@code is_known_chat} marker routed into
     * {@code <spam_list is_known_chat="..."/>} when present.
     */
    private final String spamListIsKnownChat;

    /**
     * Holds the optional pre-built {@code <biz_opt_out>} child appended under {@code <spam_list>}
     * when present, for reports against business accounts.
     */
    private final Node bizOptOutChild;

    /**
     * Holds the optional pre-built {@code <ui_state_set>} child appended under {@code <spam_list>}
     * when present, surfacing UI-state metadata captured at report time.
     */
    private final Node uistateSetChild;

    /**
     * Holds the optional pre-built {@code <biz_report>} child appended under {@code <spam_list>}
     * when present, for reports against business accounts.
     */
    private final Node bizReportChild;

    /**
     * Holds the optional pre-built {@code <tc_token>} child appended under {@code <spam_list>}
     * when present, surfacing the trust-center token bound to the report.
     */
    private final Node tcTokenChild;

    /**
     * Holds the optional pre-built {@code <frx>} (free-form reporting extensions) child appended
     * under the outer {@code <iq>} when present.
     */
    private final Node frxChild;

    /**
     * Holds the pre-built {@code <message>} children harvested from the offending chat (WA Web
     * caps this at 210), appended in insertion order under {@code <spam_list>}; never
     * {@code null}.
     */
    private final List<Node> messageChildren;

    /**
     * Holds the pre-built {@code <call>} children harvested from the offending chat (WA Web caps
     * this at 5), appended in insertion order under {@code <spam_list>}; never {@code null}.
     */
    private final List<Node> callChildren;

    /**
     * Holds the pre-built {@code <user_initiated_extension>} children (WA Web caps this at 5),
     * appended in insertion order under {@code <spam_list>}; never {@code null}.
     */
    private final List<Node> userInitiatedExtensionChildren;

    /**
     * Constructs a request from the assembled {@link Builder} state.
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
     * Returns a new {@link Builder}, the canonical entry point for assembling an individual spam
     * report.
     *
     * @return a new builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the optional reportee JID, the {@code <spam_list jid>} value.
     *
     * <p>Empty when the subject was inferred from the message attribution alone.
     *
     * @return an {@link Optional} carrying the JID, or empty when omitted
     */
    public Optional<Jid> spamListJid() {
        return Optional.ofNullable(spamListJid);
    }

    /**
     * Returns the spam-flow identifier, the {@code <spam_list spam_flow>} value.
     *
     * @return the spam-flow; never {@code null}
     */
    public String spamListSpamFlow() {
        return spamListSpamFlow;
    }

    /**
     * Returns the optional {@code is_known_chat} marker, the {@code <spam_list is_known_chat>}
     * value.
     *
     * @return an {@link Optional} carrying the marker, or empty when omitted
     */
    public Optional<String> spamListIsKnownChat() {
        return Optional.ofNullable(spamListIsKnownChat);
    }

    /**
     * Returns the optional pre-built {@code <biz_opt_out>} child appended under
     * {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> bizOptOutChild() {
        return Optional.ofNullable(bizOptOutChild);
    }

    /**
     * Returns the optional pre-built {@code <ui_state_set>} child appended under
     * {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> uistateSetChild() {
        return Optional.ofNullable(uistateSetChild);
    }

    /**
     * Returns the optional pre-built {@code <biz_report>} child appended under
     * {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> bizReportChild() {
        return Optional.ofNullable(bizReportChild);
    }

    /**
     * Returns the optional pre-built {@code <tc_token>} child appended under {@code <spam_list>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> tcTokenChild() {
        return Optional.ofNullable(tcTokenChild);
    }

    /**
     * Returns the optional pre-built {@code <frx>} child appended under the outer {@code <iq>}.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> frxChild() {
        return Optional.ofNullable(frxChild);
    }

    /**
     * Returns the offending-message children captured from the chat at report time.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Node> messageChildren() {
        return messageChildren;
    }

    /**
     * Returns the offending-call children captured from the chat at report time.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Node> callChildren() {
        return callChildren;
    }

    /**
     * Returns the user-initiated-extension children captured at report time.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Node> userInitiatedExtensionChildren() {
        return userInitiatedExtensionChildren;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds the {@code <spam_list>} child with its attributes and children, then attaches the
     * optional FRX child to the outer {@code <iq>}.
     *
     * @implNote
     * This implementation skips the {@code jid} attribute when {@link #spamListJid} is
     * {@code null}, then concatenates the message, call and user-initiated-extension children
     * followed by the optional biz-opt-out, ui-state-set, biz-report and TC-token children under
     * {@code <spam_list>}; only FRX is attached to the outer {@code <iq>}.
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

    /**
     * Compares this request to another for value equality across all fields.
     *
     * @param obj the object to compare against; may be {@code null}
     * @return {@code true} when {@code obj} is an equal {@link SmaxIndividualReportRequest}
     */
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

    /**
     * Returns a hash code derived from all fields.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(spamListJid, spamListSpamFlow, spamListIsKnownChat,
                bizOptOutChild, uistateSetChild, bizReportChild, tcTokenChild, frxChild,
                messageChildren, callChildren, userInitiatedExtensionChildren);
    }

    /**
     * Returns a debug string listing the scalar fields and the child counts.
     *
     * @return the string representation
     */
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
     * Assembles a {@link SmaxIndividualReportRequest} from the WA Web mixin inputs.
     *
     * <p>The required {@link #spamListSpamFlow} is validated at setter time; the remaining fields
     * are optional. Obtain an instance via {@link SmaxIndividualReportRequest#builder()}.
     *
     * @implNote
     * This implementation collects the optional mixin payloads into private fields and produces an
     * immutable copy from {@link #build()}.
     */
    public static final class Builder {
        /**
         * Holds the optional reportee JID, set via {@link #spamListJid(Jid)}; {@code null} when
         * the subject is inferred from the attached message attribution alone.
         */
        private Jid spamListJid;

        /**
         * Holds the required spam-flow string, set via {@link #spamListSpamFlow(String)}.
         */
        private String spamListSpamFlow;

        /**
         * Holds the optional {@code is_known_chat} marker, set via
         * {@link #spamListIsKnownChat(String)}.
         */
        private String spamListIsKnownChat;

        /**
         * Holds the optional pre-built {@code <biz_opt_out>} child, set via
         * {@link #bizOptOutChild(Node)}.
         */
        private Node bizOptOutChild;

        /**
         * Holds the optional pre-built {@code <ui_state_set>} child, set via
         * {@link #uistateSetChild(Node)}.
         */
        private Node uistateSetChild;

        /**
         * Holds the optional pre-built {@code <biz_report>} child, set via
         * {@link #bizReportChild(Node)}.
         */
        private Node bizReportChild;

        /**
         * Holds the optional pre-built {@code <tc_token>} child, set via
         * {@link #tcTokenChild(Node)}.
         */
        private Node tcTokenChild;

        /**
         * Holds the optional pre-built {@code <frx>} child, set via {@link #frxChild(Node)}.
         */
        private Node frxChild;

        /**
         * Accumulates the {@code <message>} children appended via {@link #addMessageChild(Node)}.
         */
        private final List<Node> messageChildren = new ArrayList<>();

        /**
         * Accumulates the {@code <call>} children appended via {@link #addCallChild(Node)}.
         */
        private final List<Node> callChildren = new ArrayList<>();

        /**
         * Accumulates the {@code <user_initiated_extension>} children appended via
         * {@link #addUserInitiatedExtensionChild(Node)}.
         */
        private final List<Node> userInitiatedExtensionChildren = new ArrayList<>();

        /**
         * Constructs an empty builder.
         */
        public Builder() {
        }

        /**
         * Sets the optional reportee JID routed into {@code <spam_list jid>} when set.
         *
         * <p>Leave unset to let the subject be inferred from the attached message attribution.
         *
         * @param spamListJid the JID; may be {@code null}
         * @return this builder
         */
        public Builder spamListJid(Jid spamListJid) {
            this.spamListJid = spamListJid;
            return this;
        }

        /**
         * Sets the spam-flow identifier, naming the user-facing surface that triggered the report
         * through {@code <spam_list spam_flow>}.
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
         * Sets the optional {@code is_known_chat} marker routed into
         * {@code <spam_list is_known_chat>} when set.
         *
         * @param spamListIsKnownChat the marker; may be {@code null}
         * @return this builder
         */
        public Builder spamListIsKnownChat(String spamListIsKnownChat) {
            this.spamListIsKnownChat = spamListIsKnownChat;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <biz_opt_out>} child routed under
         * {@code <spam_list>} when set, meaningful only for reports against business accounts.
         *
         * @param bizOptOutChild the node; may be {@code null}
         * @return this builder
         */
        public Builder bizOptOutChild(Node bizOptOutChild) {
            this.bizOptOutChild = bizOptOutChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <ui_state_set>} child routed under
         * {@code <spam_list>} when set, surfacing UI-state metadata captured at report time.
         *
         * @param uistateSetChild the node; may be {@code null}
         * @return this builder
         */
        public Builder uistateSetChild(Node uistateSetChild) {
            this.uistateSetChild = uistateSetChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <biz_report>} child routed under {@code <spam_list>}
         * when set, meaningful only for reports against business accounts.
         *
         * @param bizReportChild the node; may be {@code null}
         * @return this builder
         */
        public Builder bizReportChild(Node bizReportChild) {
            this.bizReportChild = bizReportChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <tc_token>} child routed under {@code <spam_list>}
         * when set, surfacing the trust-center token bound to the report.
         *
         * @param tcTokenChild the node; may be {@code null}
         * @return this builder
         */
        public Builder tcTokenChild(Node tcTokenChild) {
            this.tcTokenChild = tcTokenChild;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <frx>} child routed under the outer {@code <iq>}
         * when set.
         *
         * <p>The caller is responsible for building the FRX payload per the WA Web schema.
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
         * @implNote
         * WA Web caps the count at 210; this implementation does not enforce the cap and lets the
         * relay reject oversize lists.
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
         * @implNote
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
         * Appends a pre-built {@code <user_initiated_extension>} child to the spam-list payload.
         *
         * @implNote
         * WA Web caps the count at 5; this implementation does not enforce the cap and lets the
         * relay reject oversize lists.
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
         * @return a new {@link SmaxIndividualReportRequest}; never {@code null}
         * @throws NullPointerException if {@code spamListSpamFlow} was not set
         */
        public SmaxIndividualReportRequest build() {
            return new SmaxIndividualReportRequest(this);
        }
    }
}
