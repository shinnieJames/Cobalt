package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="optoutlist" type="get">} stanza fetching the user's marketing-message opt-out list.
 *
 * @apiNote
 * Drives the marketing-messages opt-out surface; the WA Web caller is {@code WAWebGetOptOutList.getOptOutList},
 * which seeds the request with the cached digest from {@code WAWebUserPrefsMultiDevice.getOptOutListHash} and
 * optionally scopes the result to a single marketing category.
 *
 * @implNote
 * This implementation flattens WA Web's {@code makeGetOptOutListRequest} and
 * {@code makeGetOptOutListRequestItem} factories into a single method; both attributes are optional and emitted
 * only when the corresponding field is set.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBlocklistsGetOptOutListRequest")
public final class SmaxGetOptOutListRequest implements SmaxOperation.Request {
    /**
     * The cached digest of the opt-out list or {@code null} to request a full list.
     *
     * @apiNote
     * Populated from {@code WAWebUserPrefsMultiDevice.getOptOutListHash}; pass {@code null} on first boot or
     * after a cache wipe.
     */
    private final String itemDhash;

    /**
     * The marketing-message category scoping the query or {@code null} for an unscoped fetch.
     *
     * @apiNote
     * One of the user-controls category constants; consumers typically pass the active category surfaced by the
     * UI.
     */
    private final String iqCategory;

    /**
     * Constructs an opt-out-list request.
     *
     * @apiNote
     * Pass {@code null} for either argument to omit the corresponding wire attribute and fall back to the
     * relay's defaults (full list, all categories).
     *
     * @param itemDhash  the cached digest; may be {@code null}
     * @param iqCategory the category filter; may be {@code null}
     */
    public SmaxGetOptOutListRequest(String itemDhash, String iqCategory) {
        this.itemDhash = itemDhash;
        this.iqCategory = iqCategory;
    }

    /**
     * Returns the cached digest when set.
     *
     * @return an {@link Optional} carrying the digest, or empty when no cached digest was supplied
     */
    public Optional<String> itemDhash() {
        return Optional.ofNullable(itemDhash);
    }

    /**
     * Returns the category filter when set.
     *
     * @return an {@link Optional} carrying the category, or empty when no category was supplied
     */
    public Optional<String> iqCategory() {
        return Optional.ofNullable(iqCategory);
    }

    /**
     * Builds the outbound {@code <iq>} stanza ready for dispatch.
     *
     * @apiNote
     * The returned {@link NodeBuilder} addresses {@code s.whatsapp.net} with {@code xmlns="optoutlist"} and
     * {@code type="get"}; the {@code id} attribute is filled in downstream by the central client dispatcher.
     * The {@code category} attribute is added only when {@link #iqCategory()} is present; the
     * {@code <item dhash="..."/>} child is added only when {@link #itemDhash()} is present.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBlocklistsGetOptOutListRequest",
            exports = "makeGetOptOutListRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBlocklistsGetOptOutListRequest",
            exports = "makeGetOptOutListRequestItem", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var iqBuilder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "optoutlist")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
        if (iqCategory != null) {
            iqBuilder.attribute("category", iqCategory);
        }
        if (itemDhash != null) {
            var itemNode = new NodeBuilder()
                    .description("item")
                    .attribute("dhash", itemDhash)
                    .build();
            iqBuilder.content(itemNode);
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
        var that = (SmaxGetOptOutListRequest) obj;
        return Objects.equals(this.itemDhash, that.itemDhash)
                && Objects.equals(this.iqCategory, that.iqCategory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemDhash, iqCategory);
    }

    @Override
    public String toString() {
        return "SmaxGetOptOutListRequest[itemDhash=" + itemDhash
                + ", iqCategory=" + iqCategory + ']';
    }
}
