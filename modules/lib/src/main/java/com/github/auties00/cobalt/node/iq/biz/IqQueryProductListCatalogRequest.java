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
import java.util.Optional;

/**
 * Builds the {@code <iq xmlns="w:biz:catalog" type="get">} stanza that fetches a list of products
 * from a merchant catalog by id.
 *
 * <p>The stanza names a merchant catalog, a list of opaque product ids and a requested image
 * resolution; the reply carries the typed product entries together with their images and videos
 * rendered at that resolution. The direct-connection encrypted-info blob is attached only when the
 * cart is operating under the direct-connection flow.
 *
 * @implNote
 * This implementation models the legacy WAP-IQ path only; WA Web routes the same call through the
 * Relay GraphQL endpoint when the catalog belongs to the calling user and the
 * {@code graphQLForGetProductListEnabled} gating flag is on, falling back to the WAP-IQ payload on
 * failure, but Cobalt keeps the WAP-IQ payload as the single transport.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryProductListCatalogJob")
public final class IqQueryProductListCatalogRequest implements IqOperation.Request {
    /**
     * Holds the merchant catalog JID stamped into the {@code jid} attribute of the
     * {@code <product_list/>} child.
     */
    private final Jid catalogJid;

    /**
     * Holds the opaque product ids to fetch; each id becomes one
     * {@code <product><id>...</id></product>} grandchild.
     */
    private final List<String> productIds;

    /**
     * Holds the requested image width in pixels stamped into the {@code <width/>} grandchild.
     */
    private final int width;

    /**
     * Holds the requested image height in pixels stamped into the {@code <height/>} grandchild.
     */
    private final int height;

    /**
     * Holds the optional opaque direct-connection encrypted-info blob, stamped into the
     * {@code <direct_connection_encrypted_info/>} grandchild when present.
     */
    private final String directConnectionEncryptedInfo;

    /**
     * Constructs a request from the catalog JID, the non-empty list of product ids, the requested
     * image resolution and the optional direct-connection encrypted-info blob.
     *
     * <p>The product id list is defensively copied so the caller may mutate the source freely after
     * construction. The encrypted-info blob is required only when the cart UI is operating under the
     * direct-connection flow and may otherwise be {@code null}.
     *
     * @param catalogJid                    the catalog JID; never {@code null}
     * @param productIds                    the product ids; never {@code null} and must be non-empty
     * @param width                         the requested image width
     * @param height                        the requested image height
     * @param directConnectionEncryptedInfo the optional encrypted-info blob; may be {@code null}
     * @throws NullPointerException     if {@code catalogJid} or {@code productIds} is {@code null}
     * @throws IllegalArgumentException if {@code productIds} is empty
     */
    public IqQueryProductListCatalogRequest(Jid catalogJid,
                   List<String> productIds,
                   int width,
                   int height,
                   String directConnectionEncryptedInfo) {
        this.catalogJid = Objects.requireNonNull(catalogJid, "catalogJid cannot be null");
        Objects.requireNonNull(productIds, "productIds cannot be null");
        if (productIds.isEmpty()) {
            throw new IllegalArgumentException("productIds cannot be empty");
        }
        this.productIds = List.copyOf(productIds);
        this.width = width;
        this.height = height;
        this.directConnectionEncryptedInfo = directConnectionEncryptedInfo;
    }

    /**
     * Returns the merchant catalog JID.
     *
     * <p>The value is routed verbatim into the {@code jid} attribute of the resulting
     * {@code <product_list/>} child.
     *
     * @return the catalog JID; never {@code null}
     */
    public Jid catalogJid() {
        return catalogJid;
    }

    /**
     * Returns the requested product ids in caller-supplied wire order.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<String> productIds() {
        return productIds;
    }

    /**
     * Returns the requested image width the relay uses to size the rendered image URLs.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the requested image height the relay uses to size the rendered image URLs.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    /**
     * Returns the direct-connection encrypted-info blob the stanza attaches.
     *
     * <p>An empty optional means the request is not operating under the direct-connection flow.
     *
     * @return an {@link Optional} carrying the blob
     */
    public Optional<String> directConnectionEncryptedInfo() {
        return Optional.ofNullable(directConnectionEncryptedInfo);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises one {@code <product><id/></product>} grandchild per id, plus
     * the {@code <width/>}, {@code <height/>} and (optionally)
     * {@code <direct_connection_encrypted_info/>} grandchildren of the {@code <product_list jid/>}
     * child, wrapped in the {@code w:biz:catalog get} IQ frame routed to the WhatsApp service.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryProductListCatalogJob",
            exports = "QueryProductListCatalog", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var children = new ArrayList<Node>();
        for (var id : productIds) {
            var idNode = new NodeBuilder()
                    .description("id")
                    .content(id)
                    .build();
            children.add(new NodeBuilder()
                    .description("product")
                    .content(idNode)
                    .build());
        }
        children.add(new NodeBuilder()
                .description("width")
                .content(String.valueOf(width))
                .build());
        children.add(new NodeBuilder()
                .description("height")
                .content(String.valueOf(height))
                .build());
        if (directConnectionEncryptedInfo != null) {
            children.add(new NodeBuilder()
                    .description("direct_connection_encrypted_info")
                    .content(directConnectionEncryptedInfo)
                    .build());
        }
        var productListNode = new NodeBuilder()
                .description("product_list")
                .attribute("jid", catalogJid)
                .content(children)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz:catalog")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(productListNode);
    }

    /**
     * Compares this request with another for value equality across every wire-bearing field.
     *
     * @param obj the object to compare against; may be {@code null}
     * @return {@code true} when {@code obj} is an equal request
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqQueryProductListCatalogRequest) obj;
        return this.width == that.width
                && this.height == that.height
                && Objects.equals(this.catalogJid, that.catalogJid)
                && Objects.equals(this.productIds, that.productIds)
                && Objects.equals(this.directConnectionEncryptedInfo, that.directConnectionEncryptedInfo);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(catalogJid, productIds, width, height, directConnectionEncryptedInfo);
    }

    /**
     * Returns a diagnostic string naming the catalog JID, product ids and requested resolution.
     *
     * @return the string form
     */
    @Override
    public String toString() {
        return "IqQueryProductListCatalogRequest[catalogJid=" + catalogJid
                + ", productIds=" + productIds + ", width=" + width
                + ", height=" + height + ']';
    }
}
