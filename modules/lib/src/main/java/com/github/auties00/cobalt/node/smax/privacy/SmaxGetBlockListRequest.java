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
 * The outbound {@code <iq xmlns="blocklist" type="get">} stanza that fetches the user's current blocked-contacts list.
 *
 * @apiNote
 * Drives the Settings > Privacy > Blocked contacts surface; the WA Web caller is
 * {@code WAWebGetBlocklistJob.fetchBlocklist}, which seeds the request with the cached digest from
 * {@code WAWebUserPrefsMultiDevice.getBlocklistHash} so the relay can short-circuit to a
 * {@link SmaxGetBlockListResponse.SuccessWithMatch} when the cache is still authoritative.
 *
 * @implNote
 * This implementation models the optional {@code <item dhash/>} child as a single nullable field; the LID-versus-PN
 * addressing dispatch lives entirely on the response side, since the request envelope is the same on both wires.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBlocklistsGetBlockListRequest")
public final class SmaxGetBlockListRequest implements SmaxOperation.Request {
    /**
     * The cached digest of the local blocklist or {@code null} to request a fresh full list.
     *
     * @apiNote
     * Populated from {@code WAWebUserPrefsMultiDevice.getBlocklistHash} by the WA Web caller; passing {@code null}
     * forces the relay to return the full {@code SuccessWithMismatch} body rather than a short-circuit match.
     */
    private final String itemDhash;

    /**
     * Constructs a get-blocklist request.
     *
     * @apiNote
     * Pass {@code null} for {@code itemDhash} on first boot or whenever the local cache has been invalidated;
     * pass the previously stored digest to opt into the relay's cache-match short-circuit.
     *
     * @param itemDhash the cached blocklist digest, or {@code null} to request a full list
     */
    public SmaxGetBlockListRequest(String itemDhash) {
        this.itemDhash = itemDhash;
    }

    /**
     * Returns the cached digest when set.
     *
     * @apiNote
     * Lets callers inspect whether the request was issued in cache-match mode; absent means a full re-fetch was
     * requested.
     *
     * @return an {@link Optional} carrying the digest, or empty when no cached digest was supplied
     */
    public Optional<String> itemDhash() {
        return Optional.ofNullable(itemDhash);
    }

    /**
     * Builds the outbound {@code <iq>} stanza ready for dispatch.
     *
     * @apiNote
     * The returned {@link NodeBuilder} addresses {@code s.whatsapp.net} with {@code xmlns="blocklist"} and
     * {@code type="get"}; the {@code id} attribute is filled in downstream by the central client dispatcher.
     * When {@link #itemDhash()} is present, the builder also attaches a {@code <item dhash="..."/>} child so the
     * relay can compare against the cached digest.
     *
     * @implNote
     * This implementation flattens WA Web's {@code makeGetBlockListRequest} and {@code makeGetBlockListRequestItem}
     * factories into a single method since Cobalt centralises {@code generateId()} on the client and the inner
     * item factory has no other call sites.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBlocklistsGetBlockListRequest",
            exports = "makeGetBlockListRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBlocklistsGetBlockListRequest",
            exports = "makeGetBlockListRequestItem", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var iqBuilder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "blocklist")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
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
        var that = (SmaxGetBlockListRequest) obj;
        return Objects.equals(this.itemDhash, that.itemDhash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemDhash);
    }

    @Override
    public String toString() {
        return "SmaxGetBlockListRequest[itemDhash=" + itemDhash + ']';
    }
}
