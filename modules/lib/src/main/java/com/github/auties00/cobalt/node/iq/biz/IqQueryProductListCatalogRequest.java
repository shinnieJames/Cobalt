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
 * The outbound {@code <iq xmlns="w:biz:catalog" type="get">} stanza that
 * fetches a list of products from a merchant catalog by id.
 *
 * @apiNote
 * Use this request to populate the catalog grid or to refresh a product
 * carousel from a list of opaque product ids surfaced by an earlier
 * product-message or by the merchant directory; the response carries
 * the typed product entries together with their images and videos at
 * the requested resolution.
 *
 * @implNote
 * This implementation models the legacy WAP-IQ path only; WA Web routes
 * the same call through the Relay GraphQL endpoint when the catalog
 * belongs to the calling user and the
 * {@code graphQLForGetProductListEnabled} gating flag is on, falling
 * back to the WAP-IQ payload on failure, but Cobalt keeps the WAP-IQ
 * payload as the single transport.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryProductListCatalogJob")
public final class IqQueryProductListCatalogRequest implements IqOperation.Request {
    /**
     * The merchant catalog JID stamped into the {@code jid} attribute
     * of the {@code <product_list/>} child.
     */
    private final Jid catalogJid;

    /**
     * The list of opaque product ids to fetch; each id becomes one
     * {@code <product><id>...</id></product>} grandchild.
     */
    private final List<String> productIds;

    /**
     * The requested image width in pixels stamped into the
     * {@code <width/>} grandchild.
     */
    private final int width;

    /**
     * The requested image height in pixels stamped into the
     * {@code <height/>} grandchild.
     */
    private final int height;

    /**
     * The optional opaque direct-connection encrypted-info blob,
     * stamped into the {@code <direct_connection_encrypted_info/>}
     * grandchild when present.
     */
    private final String directConnectionEncryptedInfo;

    /**
     * Constructs a request.
     *
     * @apiNote
     * Pass the merchant catalog JID, the non-empty list of product ids
     * and the requested image resolution; the
     * direct-connection-encrypted-info blob is only required when the
     * cart UI is operating under the direct-connection flow.
     *
     * @param catalogJid                    the catalog JID; never
     *                                      {@code null}
     * @param productIds                    the product ids; never
     *                                      {@code null} and must be
     *                                      non-empty
     * @param width                         the requested image width
     * @param height                        the requested image height
     * @param directConnectionEncryptedInfo the optional encrypted-info
     *                                      blob; may be {@code null}
     * @throws NullPointerException     if {@code catalogJid} or
     *                                  {@code productIds} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code productIds} is empty
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
     * @apiNote
     * Use this getter to read back the catalog JID the stanza will
     * name; the value is routed verbatim into the {@code jid}
     * attribute of the resulting {@code <product_list/>} child.
     *
     * @return the catalog JID; never {@code null}
     */
    public Jid catalogJid() {
        return catalogJid;
    }

    /**
     * Returns the requested product ids.
     *
     * @apiNote
     * Use this getter to read back the product ids the fan-out will
     * fetch; the order is the caller-supplied wire order.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<String> productIds() {
        return productIds;
    }

    /**
     * Returns the requested image width.
     *
     * @apiNote
     * Use this getter to read back the width the relay will use to
     * size the rendered image URLs.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the requested image height.
     *
     * @apiNote
     * Use this getter to read back the height the relay will use to
     * size the rendered image URLs.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    /**
     * Returns the direct-connection encrypted-info blob.
     *
     * @apiNote
     * Use this getter to read back the optional encrypted-info blob
     * the stanza will attach; an empty optional means the request is
     * not operating under the direct-connection flow.
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
     * This implementation materialises the WAP envelope produced by
     * the {@code WAWebQueryProductListCatalogJob} export: one
     * {@code <product><id/></product>} grandchild per id, plus the
     * {@code <width/>}, {@code <height/>} and (optionally)
     * {@code <direct_connection_encrypted_info/>} grandchildren of the
     * {@code <product_list jid/>} child, wrapped in the
     * {@code w:biz:catalog get} IQ frame routed to the WhatsApp service.
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
        var that = (IqQueryProductListCatalogRequest) obj;
        return this.width == that.width
                && this.height == that.height
                && Objects.equals(this.catalogJid, that.catalogJid)
                && Objects.equals(this.productIds, that.productIds)
                && Objects.equals(this.directConnectionEncryptedInfo, that.directConnectionEncryptedInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(catalogJid, productIds, width, height, directConnectionEncryptedInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqQueryProductListCatalogRequest[catalogJid=" + catalogJid
                + ", productIds=" + productIds + ", width=" + width
                + ", height=" + height + ']';
    }
}
