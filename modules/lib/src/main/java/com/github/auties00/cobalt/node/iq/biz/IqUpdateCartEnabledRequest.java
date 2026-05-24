package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;

/**
 * The outbound {@code <iq xmlns="fb:thrift_iq" type="set">} stanza that
 * toggles the cart-enabled flag in the current merchant's commerce
 * settings.
 *
 * @apiNote
 * Use this request from the catalog-management commerce-settings
 * surface; flipping the flag controls whether the merchant's catalog
 * grid shows the "add to cart" affordance, and the relay echoes the
 * post-mutation value back inside the response.
 *
 * @implNote
 * This implementation models the legacy WAP-IQ path only; WA Web routes
 * the same call through the Relay GraphQL endpoint when the
 * {@code graphQLForCommerceSettingsEnabled} gating flag is on, falling
 * back to the WAP-IQ payload on graphql-error / recovery-required
 * paths, but Cobalt keeps the WAP-IQ payload as the single transport.
 */
@WhatsAppWebModule(moduleName = "WAWebBusinessProfileJob")
public final class IqUpdateCartEnabledRequest implements IqOperation.Request {
    /**
     * The desired cart-enabled flag stamped into the {@code enabled}
     * attribute of the {@code <cart/>} grandchild.
     */
    private final boolean cartEnabled;

    /**
     * Constructs a request.
     *
     * @apiNote
     * Pass {@code true} to enable the cart affordance on the catalog
     * grid; pass {@code false} to disable it.
     *
     * @param cartEnabled the desired state
     */
    public IqUpdateCartEnabledRequest(boolean cartEnabled) {
        this.cartEnabled = cartEnabled;
    }

    /**
     * Returns the desired cart-enabled flag.
     *
     * @apiNote
     * Use this getter to read back the desired flag the stanza will
     * stamp; the relay routes it verbatim into the {@code enabled}
     * attribute of the resulting {@code <cart/>} grandchild.
     *
     * @return the flag
     */
    public boolean cartEnabled() {
        return cartEnabled;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the WAP envelope produced by
     * the {@code WAWebBusinessProfileJob.updateCartEnabled} export: a
     * {@code <cart enabled/>} grandchild wrapped in a
     * {@code <commerce_settings/>} envelope and an
     * {@code fb:thrift_iq set} IQ frame routed to the WhatsApp service.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob",
            exports = "updateCartEnabled", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var cartNode = new NodeBuilder()
                .description("cart")
                .attribute("enabled", String.valueOf(cartEnabled))
                .build();
        var commerceSettingsNode = new NodeBuilder()
                .description("commerce_settings")
                .content(cartNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(commerceSettingsNode);
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
        var that = (IqUpdateCartEnabledRequest) obj;
        return this.cartEnabled == that.cartEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Boolean.hashCode(cartEnabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqUpdateCartEnabledRequest[cartEnabled=" + cartEnabled + ']';
    }
}
