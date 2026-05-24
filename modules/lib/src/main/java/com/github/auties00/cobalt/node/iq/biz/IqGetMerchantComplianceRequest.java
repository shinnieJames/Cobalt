package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The typed outbound {@code <iq xmlns="w:biz:merchant_info" type="get">} stanza that requests the regulatory-compliance bundle for one or more merchants.
 *
 * @apiNote
 * Use this request from the India e-commerce surfaces (compliance entry-point banner, post-thread legal-entity row, grievance officer details) to fetch the merchant's compliance bundle for rendering; one stanza can carry multiple merchant JIDs so a list view can fetch all entries at once. The matching {@link IqGetMerchantComplianceResponse} surfaces the entity name, the entity type, the registered flag, the customer-care contact triple and the grievance-officer block per merchant.
 *
 * @implNote
 * This implementation targets the deprecated WAP path of {@code WAWebMerchantComplianceJob.getMerchantCompliance}; WA Web routes through {@code WAWebBizGetMerchantCompliance} first when {@code graphQLForGetComplianceInfo} is set and only falls back to this stanza shape when the GraphQL path is disabled.
 */
@WhatsAppWebModule(moduleName = "WAWebMerchantComplianceJob")
public final class IqGetMerchantComplianceRequest implements IqOperation.Request {
    /**
     * The merchant JIDs whose compliance bundles are being queried, emitted as the {@code jid} attribute of one {@code <merchant_info/>} child per entry.
     */
    private final List<Jid> businessJids;

    /**
     * Constructs a typed request.
     *
     * @apiNote
     * Call this constructor with the merchant JIDs that should be queried; the list must contain at least one entry because the relay rejects an empty fan-out.
     *
     * @param businessJids the merchant JIDs; never {@code null} and must be non-empty
     * @throws NullPointerException     if {@code businessJids} is {@code null}
     * @throws IllegalArgumentException when {@code businessJids} is empty
     */
    public IqGetMerchantComplianceRequest(List<Jid> businessJids) {
        Objects.requireNonNull(businessJids, "businessJids cannot be null");
        if (businessJids.isEmpty()) {
            throw new IllegalArgumentException("businessJids cannot be empty");
        }
        this.businessJids = List.copyOf(businessJids);
    }

    /**
     * Returns the queried merchant JIDs.
     *
     * @apiNote
     * Use this getter to read back the merchant JIDs that the stanza will fan out to; the list preserves the caller-supplied order.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Jid> businessJids() {
        return businessJids;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the WAP envelope produced by the legacy fallback branch of {@code WAWebMerchantComplianceJob.getMerchantCompliance}: one {@code <merchant_info jid/>} child per queried JID, wrapped in an {@code <iq xmlns="w:biz:merchant_info" type="get"/>} envelope routed to the WhatsApp service.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMerchantComplianceJob",
            exports = "getMerchantCompliance", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var children = new ArrayList<Node>();
        for (var jid : businessJids) {
            children.add(new NodeBuilder()
                    .description("merchant_info")
                    .attribute("jid", jid)
                    .build());
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz:merchant_info")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(children);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqGetMerchantComplianceRequest) obj;
        return Objects.equals(this.businessJids, that.businessJids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(businessJids);
    }

    @Override
    public String toString() {
        return "IqGetMerchantComplianceRequest[businessJids=" + businessJids + ']';
    }
}
