package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code GetLinkedAccounts} IQ request stanza for the
 * SMB business-linking enumeration bridge.
 *
 * @apiNote
 * Used by Cobalt clients that mirror WA Web's
 * {@code WAWebLinkedAccountsJob.queryLinkedPagesInfo} flow, which
 * is consulted from the CTWA ad-creation pipeline
 * ({@code WAWebBizNativeAdsResolveRelayIdentityBundle},
 * {@code WAWebResolveAccountTypeAndAdPage},
 * {@code WAWebBizNativeAdsFlowLoadable}) and from the SMB
 * linked-accounts settings surface to enumerate the linked
 * Facebook page, Facebook business, Instagram-professional and
 * WhatsApp ad-identity records associated with the active user.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code makeGetLinkedAccountsRequest} by stamping the static
 * {@code xmlns="fb:thrift_iq"} envelope and emitting a bare
 * {@code <linked_accounts/>} child; the {@code id} attribute is
 * appended by Cobalt's send path, matching WA's
 * {@code generateId()} insertion point.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBizLinkingGetLinkedAccountsRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutBizLinkingHackBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutBizLinkingBaseIQGetRequestMixin")
public final class SmaxGetLinkedAccountsRequest implements SmaxOperation.Request {
    /**
     * The optional {@code from} attribute echoed onto the outbound
     * IQ via the {@code HackBaseIQGetRequestMixin}; the active user
     * {@link Jid} is the only legal value and {@code null} omits
     * the attribute.
     */
    private final Jid fromUserJid;

    /**
     * Constructs a new request with no {@code from} echo.
     *
     * @apiNote
     * The default shape invoked by
     * {@code WAWebLinkedAccountsJob.queryLinkedPagesInfo}: a bare
     * IQ-get with only the empty {@code <linked_accounts/>}
     * payload.
     */
    public SmaxGetLinkedAccountsRequest() {
        this(null);
    }

    /**
     * Constructs a new request optionally echoing the supplied user
     * {@link Jid} onto the {@code from} attribute.
     *
     * @apiNote
     * Use when a companion linked device proxies the request on
     * behalf of the active user; the relay validates that the
     * echoed JID matches the authenticated session.
     *
     * @param fromUserJid the optional user {@link Jid} to echo
     *                    onto the {@code from} attribute; may be
     *                    {@code null}
     */
    public SmaxGetLinkedAccountsRequest(Jid fromUserJid) {
        this.fromUserJid = fromUserJid;
    }

    /**
     * Returns the optional {@code from} echo.
     *
     * @return an {@link Optional} carrying the user {@link Jid}, or
     *         empty when no echo was supplied
     */
    public Optional<Jid> fromUserJid() {
        return Optional.ofNullable(fromUserJid);
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @implNote
     * This implementation composes three WA Web mixins in a single
     * pass: {@code makeGetLinkedAccountsRequest} stamps the
     * {@code xmlns="fb:thrift_iq"} envelope and the empty
     * {@code <linked_accounts/>} child,
     * {@code mergeHackBaseIQGetRequestMixin} stamps {@code to} and
     * the optional {@code from}, and
     * {@code mergeBaseIQGetRequestMixin} stamps {@code type="get"}.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and
     *         the empty {@code <linked_accounts/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBizLinkingGetLinkedAccountsRequest",
            exports = "makeGetLinkedAccountsRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizLinkingHackBaseIQGetRequestMixin",
            exports = "mergeHackBaseIQGetRequestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizLinkingBaseIQGetRequestMixin",
            exports = "mergeBaseIQGetRequestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var linkedAccountsNode = new NodeBuilder()
                .description("linked_accounts")
                .build();
        var builder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(linkedAccountsNode);
        if (fromUserJid != null) {
            builder.attribute("from", fromUserJid);
        }
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGetLinkedAccountsRequest) obj;
        return Objects.equals(this.fromUserJid, that.fromUserJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(fromUserJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmaxGetLinkedAccountsRequest[fromUserJid=" + fromUserJid + ']';
    }
}
