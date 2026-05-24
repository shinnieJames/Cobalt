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
 * Outbound {@code spam} IQ that reports an offending newsletter-status post (v2 schema).
 *
 * @apiNote
 * Drives the "Report newsletter status" surface invoked by WA Web's
 * {@code WAWebNewsletterReportUtils.sendNewsletterStatusReport}; the v2 schema replaces the
 * legacy {@code <message>} envelope of {@link SmaxStatusReportRequest} with a
 * {@code <status>} envelope carrying {@code server_id} and {@code t} attributes plus an
 * optional payload child. Pair with {@link SmaxStatusReportV2Response} to consume the relay's
 * verdict.
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain (BaseIQSetRequest, BaseReport,
 * EntitySubject, ReportableNewsletterStatus) into a single {@link NodeBuilder} that pins
 * {@code xmlns="spam"}, {@code to=JidServer.user()} and {@code type="set"}. WA Web composes
 * the payload child via {@code mergeStatusNewsletterTextOrMediaMixinGroup}; this implementation
 * accepts the supplied node verbatim under {@code <status>}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSpamStatusReportV2Request")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseReportMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSpamReportableNewsletterStatusMixin")
public final class SmaxStatusReportV2Request implements SmaxOperation.Request {
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
     * Routed into {@code <spam_list spam_flow="..."/>}.
     */
    private final String spamListSpamFlow;

    /**
     * The {@code server_id} of the offending status post.
     *
     * @apiNote
     * Routed into {@code <status server_id="..."/>}.
     */
    private final long statusServerId;

    /**
     * The Unix-second timestamp of the offending status post.
     *
     * @apiNote
     * Routed into {@code <status t="..."/>}.
     */
    private final long statusTimestamp;

    /**
     * The optional newsletter subject string echoed by the relay for attribution context.
     *
     * @apiNote
     * Routed into {@code <spam_list subject="..."/>} via
     * {@code WASmaxOutSpamEntitySubjectMixin}.
     */
    private final String spamListSubject;

    /**
     * The optional pre-built payload child for the {@code <status>} envelope.
     *
     * @apiNote
     * Appended verbatim under {@code <status>} when present; carries the text-or-media inner
     * content produced by WA Web's {@code mergeStatusNewsletterTextOrMediaMixinGroup}. When
     * absent the {@code <status>} envelope is emitted without a content child.
     */
    private final Node statusPayloadContent;

    /**
     * Constructs a v2 status-report request.
     *
     * @apiNote
     * Typically invoked by callers that have collected the form fields and harvested the
     * offending status from the local cache.
     *
     * @param spamListJid          the newsletter JID; never {@code null}
     * @param spamListSpamFlow     the spam-flow string; never {@code null}
     * @param statusServerId       the status's {@code server_id}
     * @param statusTimestamp      the status's Unix-second timestamp
     * @param spamListSubject      the optional subject; may be {@code null}
     * @param statusPayloadContent the optional payload child; may be {@code null}
     * @throws NullPointerException if {@code spamListJid} or {@code spamListSpamFlow} is
     *                              {@code null}
     */
    public SmaxStatusReportV2Request(Jid spamListJid, String spamListSpamFlow,
                   long statusServerId, long statusTimestamp,
                   String spamListSubject, Node statusPayloadContent) {
        this.spamListJid = Objects.requireNonNull(spamListJid, "spamListJid cannot be null");
        this.spamListSpamFlow = Objects.requireNonNull(spamListSpamFlow, "spamListSpamFlow cannot be null");
        this.statusServerId = statusServerId;
        this.statusTimestamp = statusTimestamp;
        this.spamListSubject = spamListSubject;
        this.statusPayloadContent = statusPayloadContent;
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
     * Returns the status {@code server_id}.
     *
     * @apiNote
     * Surfaces the value routed into {@code <status server_id>}.
     *
     * @return the {@code server_id}
     */
    public long statusServerId() {
        return statusServerId;
    }

    /**
     * Returns the status timestamp.
     *
     * @apiNote
     * Surfaces the value routed into {@code <status t>} in Unix seconds.
     *
     * @return the timestamp
     */
    public long statusTimestamp() {
        return statusTimestamp;
    }

    /**
     * Returns the optional newsletter subject.
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
     * Returns the optional pre-built payload child.
     *
     * @apiNote
     * Empty when the {@code <status>} envelope should be emitted without a content child.
     *
     * @return an {@link Optional} carrying the node, or empty when omitted
     */
    public Optional<Node> statusPayloadContent() {
        return Optional.ofNullable(statusPayloadContent);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Emits the outbound v2 status-report IQ ready for
     * {@link com.github.auties00.cobalt.node.smax} dispatch.
     *
     * @implNote
     * This implementation builds the {@code <status>} envelope first ({@code server_id},
     * {@code t}, optional payload child) and then attaches it under {@code <spam_list>} with
     * the optional {@code subject} attribute; the outer {@code <iq>} carries no further
     * optional children.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutSpamStatusReportV2Request",
            exports = "makeStatusReportV2Request", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var statusBuilder = new NodeBuilder()
                .description("status")
                .attribute("server_id", statusServerId)
                .attribute("t", statusTimestamp);
        if (statusPayloadContent != null) {
            statusBuilder.content(statusPayloadContent);
        }
        var spamListBuilder = new NodeBuilder()
                .description("spam_list")
                .attribute("jid", spamListJid)
                .attribute("spam_flow", spamListSpamFlow);
        if (spamListSubject != null) {
            spamListBuilder.attribute("subject", spamListSubject);
        }
        spamListBuilder.content(statusBuilder.build());
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
        var that = (SmaxStatusReportV2Request) obj;
        return this.statusServerId == that.statusServerId
                && this.statusTimestamp == that.statusTimestamp
                && Objects.equals(this.spamListJid, that.spamListJid)
                && Objects.equals(this.spamListSpamFlow, that.spamListSpamFlow)
                && Objects.equals(this.spamListSubject, that.spamListSubject)
                && Objects.equals(this.statusPayloadContent, that.statusPayloadContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spamListJid, spamListSpamFlow, statusServerId, statusTimestamp,
                spamListSubject, statusPayloadContent);
    }

    @Override
    public String toString() {
        return "SmaxStatusReportV2Request[spamListJid=" + spamListJid
                + ", spamListSpamFlow=" + spamListSpamFlow
                + ", statusServerId=" + statusServerId
                + ", statusTimestamp=" + statusTimestamp
                + ", spamListSubject=" + spamListSubject + ']';
    }
}
