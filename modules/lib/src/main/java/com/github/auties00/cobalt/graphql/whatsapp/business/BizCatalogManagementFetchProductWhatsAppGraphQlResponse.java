package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessProduct;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the fetch-product query built by
 * {@link BizCatalogManagementFetchProductWhatsAppGraphQlRequest} into a {@link BusinessProduct}.
 *
 * <p>Reads the linked chain {@code xfb_whatsapp_catalog_product -> product_catalog -> product} and
 * projects the single product onto the Cobalt domain model.
 *
 * @see BizCatalogManagementFetchProductWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementFetchProductQuery")
public final class BizCatalogManagementFetchProductWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed product.
     */
    private final BusinessProduct product;

    /**
     * Constructs a response wrapping the parsed product.
     *
     * <p>Reserved for the static parser.
     *
     * @param product the parsed product, or {@code null} when the relay omitted the field
     */
    private BizCatalogManagementFetchProductWhatsAppGraphQlResponse(BusinessProduct product) {
        this.product = product;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked chain {@code xfb_whatsapp_catalog_product -> product_catalog -> product} and
     * projects the single product onto a {@link BusinessProduct}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} or the product projection is missing
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementFetchProduct", exports = "fetchProduct",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementFetchProductWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog_product");
        if (root == null) {
            return Optional.empty();
        }
        var productCatalog = root.getJSONObject("product_catalog");
        if (productCatalog == null) {
            return Optional.empty();
        }
        var product = CatalogProductInfoParser.parseProduct(productCatalog.getJSONObject("product")).orElse(null);
        if (product == null) {
            return Optional.empty();
        }
        return Optional.of(new BizCatalogManagementFetchProductWhatsAppGraphQlResponse(product));
    }

    /**
     * Returns the parsed product.
     *
     * @return the parsed {@link BusinessProduct}, never {@code null}
     */
    public BusinessProduct product() {
        return product;
    }
}
