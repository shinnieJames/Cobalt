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
 * Outbound {@code spam} IQ that reports a WhatsApp group, with the offending messages and calls
 * inlined under {@code <spam_list>}.
 *
 * @apiNote
 * Drives the "Report group" surface invoked by WA Web's {@code WAWebReportSpamJob}; pair with
 * {@link SmaxGroupReportResponse} to consume the relay's verdict. The {@link Builder} mirrors
 * WA Web's six optional mixins layered over the base envelope; a minimal report only requires
 * {@link Builder#spamListJid(Jid)} and {@link Builder#spamListSpamFlow(String)}.
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain (BaseIQSetRequest, BaseReport,
 * EntitySubject, IsKnownChat, FRX) into a single {@link NodeBuilder} that pins
 * {@code xmlns="spam"}, {@code to=JidServer.user()} and {@code type="set"}. Optional attributes
 * and children are only emitted when their builder fields are non-{@code null} or non-empty,
 * matching WA Web's {@code OPTIONAL}/{@code OPTIONAL_CHILD} semantics.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSpamGroupReportRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseReportMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamEntitySubjectMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamFRXMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamIsKnownChatMixin")
public final class SmaxGroupReportRequest implements SmaxOperation.Request {
    /**
     * The group JID being reported.
     *
     * @apiNote
     * Routed into the {@code <spam_list jid="..."/>} attribute via WA Web's {@code GROUP_JID}
     * marshaller.
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
     * The optional adder JID (the user who added the reporter to the group).
     *
     * @apiNote
     * Routed into {@code <spam_list source="..."/>} when present; empty when WA Web's
     * EntitySubject mixin was not supplied a source.
     */
    private final Jid spamListSource;

    /**
     * The optional group subject string echoed by the relay for attribution context.
     *
     * @apiNote
     * Routed into {@code <spam_list subject="..."/>} when present.
     */
    private final String spamListSubject;

    /**
     * The optional {@code is_known_chat} marker indicating whether the reporter has prior
     * history with the group.
     *
     * @apiNote
     * Routed into {@code <spam_list is_known_chat="..."/>} when present.
     */
    private final String spamListIsKnownChat;

    /**
     * The optional pre-built {@code <frx>} (free-form reporting extensions) child.
     *
     * @apiNote
     * Appended verbatim under the IQ envelope when present; bundles tagset, context and
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
     * Constructs a request from the assembled {@link Builder} state.
     *
     * @apiNote
     * Invoked by {@link Builder#build()}; consumers use {@link #builder()} rather than calling
     * this constructor directly.
     *
     * @param builder the source builder; never {@code null}
     */
    private SmaxGroupReportRequest(Builder builder) {
        this.spamListJid = Objects.requireNonNull(builder.spamListJid, "spamListJid cannot be null");
        this.spamListSpamFlow = Objects.requireNonNull(builder.spamListSpamFlow,
                "spamListSpamFlow cannot be null");
        this.spamListSource = builder.spamListSource;
        this.spamListSubject = builder.spamListSubject;
        this.spamListIsKnownChat = builder.spamListIsKnownChat;
        this.frxChild = builder.frxChild;
        this.messageChildren = List.copyOf(builder.messageChildren);
        this.callChildren = List.copyOf(builder.callChildren);
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @apiNote
     * The canonical entry point for assembling a group spam report.
     *
     * @return a new builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the group JID being reported.
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
     * Returns the optional adder JID.
     *
     * @apiNote
     * Surfaces the {@code <spam_list source>} value; empty when WA Web did not supply one.
     *
     * @return an {@link Optional} carrying the JID, or empty when omitted
     */
    public Optional<Jid> spamListSource() {
        return Optional.ofNullable(spamListSource);
    }

    /**
     * Returns the optional group subject.
     *
     * @apiNote
     * Surfaces the {@code <spam_list subject>} value.
     *
     * @return an {@link Optional} carrying the subject, or empty when omitted
     */
    public Optional<String> spamListSubject() {
        return Optional.ofNullable(spamListSubject);
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
     * Returns the optional FRX child.
     *
     * @apiNote
     * Surfaces the pre-built {@code <frx>} payload appended to the IQ.
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
     * Surfaces the {@code <message>} children that were captured from the chat at report time.
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
     * Surfaces the {@code <call>} children that were captured from the chat at report time.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Node> callChildren() {
        return callChildren;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Emits the outbound group-report IQ ready for {@link com.github.auties00.cobalt.node.smax}
     * dispatch.
     *
     * @implNote
     * This implementation assembles the {@code <spam_list>} child first (attributes plus
     * message and call children) and only attaches the FRX child to the outer {@code <iq>} when
     * present; identical to WA Web's mixin composition order.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutSpamGroupReportRequest",
            exports = "makeGroupReportRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var spamListBuilder = new NodeBuilder()
                .description("spam_list")
                .attribute("jid", spamListJid)
                .attribute("spam_flow", spamListSpamFlow);
        if (spamListSource != null) {
            spamListBuilder.attribute("source", spamListSource);
        }
        if (spamListSubject != null) {
            spamListBuilder.attribute("subject", spamListSubject);
        }
        if (spamListIsKnownChat != null) {
            spamListBuilder.attribute("is_known_chat", spamListIsKnownChat);
        }
        var spamListChildren = new ArrayList<Node>(messageChildren.size() + callChildren.size());
        spamListChildren.addAll(messageChildren);
        spamListChildren.addAll(callChildren);
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
        var that = (SmaxGroupReportRequest) obj;
        return Objects.equals(this.spamListJid, that.spamListJid)
                && Objects.equals(this.spamListSpamFlow, that.spamListSpamFlow)
                && Objects.equals(this.spamListSource, that.spamListSource)
                && Objects.equals(this.spamListSubject, that.spamListSubject)
                && Objects.equals(this.spamListIsKnownChat, that.spamListIsKnownChat)
                && Objects.equals(this.frxChild, that.frxChild)
                && Objects.equals(this.messageChildren, that.messageChildren)
                && Objects.equals(this.callChildren, that.callChildren);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spamListJid, spamListSpamFlow, spamListSource, spamListSubject,
                spamListIsKnownChat, frxChild, messageChildren, callChildren);
    }

    @Override
    public String toString() {
        return "SmaxGroupReportRequest[spamListJid=" + spamListJid
                + ", spamListSpamFlow=" + spamListSpamFlow
                + ", spamListSource=" + spamListSource
                + ", spamListSubject=" + spamListSubject
                + ", spamListIsKnownChat=" + spamListIsKnownChat
                + ", messageChildren=" + messageChildren.size()
                + ", callChildren=" + callChildren.size() + ']';
    }

    /**
     * Step-builder that assembles a {@link SmaxGroupReportRequest} from the WA Web mixin inputs.
     *
     * @apiNote
     * The canonical entry point for callers that need to issue a group spam report; use
     * {@link SmaxGroupReportRequest#builder()} to obtain an instance.
     *
     * @implNote
     * This implementation collects the optional mixin payloads into private fields and validates
     * the required {@link #spamListJid} and {@link #spamListSpamFlow} at setter time; the
     * resulting {@code build()} copy is immutable.
     */
    public static final class Builder {
        /**
         * The required group JID.
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
         * The optional adder JID.
         *
         * @apiNote
         * Set via {@link #spamListSource(Jid)}; {@code null} when omitted.
         */
        private Jid spamListSource;

        /**
         * The optional group subject.
         *
         * @apiNote
         * Set via {@link #spamListSubject(String)}; {@code null} when omitted.
         */
        private String spamListSubject;

        /**
         * The optional {@code is_known_chat} marker.
         *
         * @apiNote
         * Set via {@link #spamListIsKnownChat(String)}; {@code null} when omitted.
         */
        private String spamListIsKnownChat;

        /**
         * The optional pre-built FRX child node.
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
         * Constructs an empty builder.
         *
         * @apiNote
         * Prefer {@link SmaxGroupReportRequest#builder()} over invoking this constructor
         * directly.
         */
        public Builder() {
        }

        /**
         * Sets the target group JID.
         *
         * @apiNote
         * Required; the resulting {@code <spam_list jid>} attribute names the group being
         * reported.
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
         * Sets the optional adder JID.
         *
         * @apiNote
         * Routes into {@code <spam_list source>} when set; identifies the user who added the
         * reporter to the group.
         *
         * @param spamListSource the JID; may be {@code null}
         * @return this builder
         */
        public Builder spamListSource(Jid spamListSource) {
            this.spamListSource = spamListSource;
            return this;
        }

        /**
         * Sets the optional group subject.
         *
         * @apiNote
         * Routes into {@code <spam_list subject>} when set; surfaces the human-readable group
         * title for attribution.
         *
         * @param spamListSubject the subject; may be {@code null}
         * @return this builder
         */
        public Builder spamListSubject(String spamListSubject) {
            this.spamListSubject = spamListSubject;
            return this;
        }

        /**
         * Sets the optional {@code is_known_chat} marker.
         *
         * @apiNote
         * Routes into {@code <spam_list is_known_chat>} when set; flags whether the reporter
         * has prior history with the group.
         *
         * @param spamListIsKnownChat the marker; may be {@code null}
         * @return this builder
         */
        public Builder spamListIsKnownChat(String spamListIsKnownChat) {
            this.spamListIsKnownChat = spamListIsKnownChat;
            return this;
        }

        /**
         * Sets the optional pre-built {@code <frx>} child.
         *
         * @apiNote
         * The supplied node is appended verbatim under the outer {@code <iq>} when set; the
         * caller is responsible for building the tagset / context / parameters per the WA Web
         * FRX schema.
         *
         * @param frxChild the FRX node; may be {@code null}
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
         * Builds an immutable {@link SmaxGroupReportRequest} from the accumulated state.
         *
         * @apiNote
         * Invoke once after all required and optional fields have been set; the resulting
         * instance can be reused across dispatches.
         *
         * @return a new {@link SmaxGroupReportRequest}; never {@code null}
         * @throws NullPointerException if any required field was not set
         */
        public SmaxGroupReportRequest build() {
            return new SmaxGroupReportRequest(this);
        }
    }
}
