package com.github.auties00.cobalt.node.iq.debug;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="urn:xmpp:whatsapp:account" type="get">} stanza that cancels an
 * in-flight GDPR data-export request for the bound report type.
 *
 * @apiNote
 * Use this to surface WA Web's "cancel pending GDPR request" debug action; it backs
 * {@code WAWebDebugGDPR.cancelGDPRRequest}, the only public caller in the bundle. The
 * payload is a {@code <gdpr action="delete" [report_type=?]/>} child where
 * {@code report_type} is the {@link IqDebugGdprReportType#wire()} value (omitted entirely
 * for {@link IqDebugGdprReportType#ACCOUNT}). The relay reply is parsed by
 * {@link IqDebugGdprResponse}.
 *
 * @implNote
 * This implementation routes the outbound stanza to {@link JidServer#user()} and fixes
 * {@code type="get"}, matching WA Web's {@code WAWebGdprHookUtils.getGdprIq} verbatim.
 * The IQ id is omitted here and assigned by the dispatch layer.
 */
@WhatsAppWebModule(moduleName = "WAWebGdprHookUtils")
public final class IqDebugGdprRequest implements IqOperation.Request {
    /**
     * Holds the {@link IqDebugGdprReportType} whose export is being cancelled.
     */
    private final IqDebugGdprReportType reportType;

    /**
     * Constructs a new cancel-GDPR request bound to the given report type.
     *
     * @apiNote
     * The {@code reportType} parameter must match the report type of the previously
     * issued GDPR request; cancelling with a mismatched type produces a {@code 404}
     * surfaced as {@link IqDebugGdprResponse.ClientError}.
     *
     * @param reportType the report type to cancel; never {@code null}
     * @throws NullPointerException if {@code reportType} is {@code null}
     */
    public IqDebugGdprRequest(IqDebugGdprReportType reportType) {
        this.reportType = Objects.requireNonNull(reportType, "reportType cannot be null");
    }

    /**
     * Returns the bound {@link IqDebugGdprReportType}.
     *
     * @return the report type being cancelled; never {@code null}
     */
    public IqDebugGdprReportType reportType() {
        return reportType;
    }

    /**
     * Builds the outbound {@code <iq>} stanza wrapping the
     * {@code <gdpr action="delete" [report_type=?]/>} payload.
     *
     * @apiNote
     * The resulting {@link NodeBuilder} is wire-ready except for the IQ {@code id}
     * attribute, which the dispatch layer assigns.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <gdpr>}
     *         payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDebugGDPR",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebGdprHookUtils",
            exports = "getGdprIq", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var gdprBuilder = new NodeBuilder()
                .description("gdpr")
                .attribute("action", "delete");
        var wireType = reportType.wire().orElse(null);
        if (wireType != null) {
            gdprBuilder.attribute("report_type", wireType);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "urn:xmpp:whatsapp:account")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(gdprBuilder.build());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqDebugGdprRequest) obj;
        return this.reportType == that.reportType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportType);
    }

    @Override
    public String toString() {
        return "IqDebugGdprRequest[reportType=" + reportType + ']';
    }
}
