package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogPage;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogPageBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the fetch-catalog query built by
 * {@link BizCatalogManagementFetchCatalogWhatsAppGraphQlRequest} into a {@link BusinessCatalogPage}.
 *
 * <p>Reads the linked chain {@code xfb_whatsapp_catalog -> product_catalog} and projects the catalog
 * identity, the page of products, and the pagination cursors onto the Cobalt domain model.
 *
 * @see BizCatalogManagementFetchCatalogWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementFetchCatalogQuery")
public final class BizCatalogManagementFetchCatalogWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed catalog page.
     */
    private final BusinessCatalogPage catalog;

    /**
     * Constructs a response wrapping the parsed catalog page.
     *
     * <p>Reserved for the static parser.
     *
     * @param catalog the parsed catalog page, or {@code null} when the relay omitted the field
     */
    private BizCatalogManagementFetchCatalogWhatsAppGraphQlResponse(BusinessCatalogPage catalog) {
        this.catalog = catalog;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked chain {@code xfb_whatsapp_catalog -> product_catalog} and projects the
     * catalog identity, products, and cursors onto a {@link BusinessCatalogPage}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} or the catalog projection is missing
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementFetchCatalog", exports = "fetchCatalog",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementFetchCatalogWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog");
        if (root == null) {
            return Optional.empty();
        }
        var productCatalog = root.getJSONObject("product_catalog");
        if (productCatalog == null) {
            return Optional.empty();
        }
        String before = null;
        String after = null;
        var paging = productCatalog.getJSONObject("paging");
        if (paging != null) {
            before = paging.getString("before");
            after = paging.getString("after");
        }
        var products = CatalogProductInfoParser.parseProducts(productCatalog.getJSONArray("products"));
        var page = new BusinessCatalogPageBuilder()
                .catalogId(productCatalog.getString("catalog_id"))
                .catalogType(productCatalog.getString("catalog_type"))
                .catalogName(productCatalog.getString("catalog_name"))
                .products(products)
                .beforeCursor(before)
                .afterCursor(after)
                .build();
        return Optional.of(new BizCatalogManagementFetchCatalogWhatsAppGraphQlResponse(page));
    }

    /**
     * Returns the parsed catalog page.
     *
     * <p>The returned {@link BusinessCatalogPage} carries the catalog identity, one page of products,
     * and the pagination cursors.
     *
     * @return the parsed catalog page, never {@code null}
     */
    public BusinessCatalogPage catalog() {
        return catalog;
    }
}
