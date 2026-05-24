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
 * Outbound {@code spam} IQ that reports a newsletter and a list of offending messages.
 *
 * @apiNote
 * Drives the "Report newsletter" surface invoked by WA Web's
 * {@code WAWebNewsletterReportUtils.sendNewsletterReport}; pair with
 * {@link SmaxNewsletterReportResponse} to consume the relay's verdict. Each offending message
 * is represented by a {@link SmaxNewsletterReportMessageEntry}.
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain (BaseIQSetRequest, BaseReport,
 * EntitySubject) into a single {@link NodeBuilder} that pins {@code xmlns="spam"},
 * {@code to=JidServer.user()} and {@code type="set"}. Unlike the per-message group / individual
 * variants, all four scalar fields are required and the message list copy is non-{@code null}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSpamNewsletterReportRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseReportMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamEntitySubjectMixin")
public final class SmaxNewsletterReportRequest implements SmaxOperation.Request {
    /**
     * The newsletter JID being reported.
     *
     * @apiNote
     * Routed into {@code <spam_list jid="..."/>} via WA Web's {@code JID} marshaller.
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
     * The newsletter subject string echoed by the relay for attribution context.
     *
     * @apiNote
     * Routed into {@code <spam_list subject="..."/>} via WA Web's
     * {@code WASmaxOutSpamEntitySubjectMixin}.
     */
    private final String spamListSubject;

    /**
     * The list of offending {@link SmaxNewsletterReportMessageEntry} entries.
     *
     * @apiNote
     * WA Web caps the count at 65 ({@code REPEATED_CHILD(message, 0, 65)}); appended in
     * insertion order under {@code <spam_list>}.
     */
    private final List<SmaxNewsletterReportMessageEntry> messages;

    /**
     * Constructs a newsletter-report request.
     *
     * @apiNote
     * Typically invoked by callers that have collected the form fields and harvested offending
     * messages from the local cache.
     *
     * @param spamListJid      the newsletter JID; never {@code null}
     * @param spamListSpamFlow the spam-flow string; never {@code null}
     * @param spamListSubject  the subject string; never {@code null}
     * @param messages         the message entries; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxNewsletterReportRequest(Jid spamListJid, String spamListSpamFlow, String spamListSubject,
                   List<SmaxNewsletterReportMessageEntry> messages) {
        this.spamListJid = Objects.requireNonNull(spamListJid, "spamListJid cannot be null");
        this.spamListSpamFlow = Objects.requireNonNull(spamListSpamFlow, "spamListSpamFlow cannot be null");
        this.spamListSubject = Objects.requireNonNull(spamListSubject, "spamListSubject cannot be null");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages cannot be null"));
    }

    /**
     * Returns the newsletter JID.
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
     * Returns the newsletter subject.
     *
     * @apiNote
     * Surfaces the {@code <spam_list subject>} value.
     *
     * @return the subject; never {@code null}
     */
    public String spamListSubject() {
        return spamListSubject;
    }

    /**
     * Returns the message entries.
     *
     * @apiNote
     * Surfaces the {@link SmaxNewsletterReportMessageEntry} entries that {@link #toNode()}
     * embeds under {@code <spam_list>}.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<SmaxNewsletterReportMessageEntry> messages() {
        return messages;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Emits the outbound newsletter-report IQ ready for
     * {@link com.github.auties00.cobalt.node.smax} dispatch.
     *
     * @implNote
     * This implementation materialises each {@link SmaxNewsletterReportMessageEntry} via
     * {@link SmaxNewsletterReportMessageEntry#toNode()} and attaches the resulting list as
     * {@code <spam_list>} content; the outer IQ never carries optional children.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutSpamNewsletterReportRequest",
            exports = "makeNewsletterReportRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var children = new ArrayList<Node>(messages.size());
        for (var message : messages) {
            children.add(message.toNode());
        }
        var spamListBuilder = new NodeBuilder()
                .description("spam_list")
                .attribute("jid", spamListJid)
                .attribute("spam_flow", spamListSpamFlow)
                .attribute("subject", spamListSubject)
                .content(children);
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "spam")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(spamListBuilder.build());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxNewsletterReportRequest) obj;
        return Objects.equals(this.spamListJid, that.spamListJid)
                && Objects.equals(this.spamListSpamFlow, that.spamListSpamFlow)
                && Objects.equals(this.spamListSubject, that.spamListSubject)
                && Objects.equals(this.messages, that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spamListJid, spamListSpamFlow, spamListSubject, messages);
    }

    @Override
    public String toString() {
        return "SmaxNewsletterReportRequest[spamListJid=" + spamListJid
                + ", spamListSpamFlow=" + spamListSpamFlow
                + ", spamListSubject=" + spamListSubject
                + ", messages=" + messages + ']';
    }
}
