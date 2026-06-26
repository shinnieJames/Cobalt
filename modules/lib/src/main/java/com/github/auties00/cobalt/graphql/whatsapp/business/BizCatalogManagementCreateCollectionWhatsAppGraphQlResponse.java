package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessProductCollection;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the create-collection mutation built by
 * {@link BizCatalogManagementCreateCollectionWhatsAppGraphQlRequest} into a {@link BusinessProductCollection}.
 *
 * <p>Reads the linked chain {@code xfb_whatsapp_catalog_create_collection -> collection} and projects
 * the created collection's identifier and its moderation status onto the Cobalt domain model. The
 * mutation does not echo the seeded products, so {@link BusinessProductCollection#products()} is empty.
 *
 * @see BizCatalogManagementCreateCollectionWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementCreateCollectionMutation")
public final class BizCatalogManagementCreateCollectionWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed created collection.
     */
    private final BusinessProductCollection collection;

    /**
     * Constructs a response wrapping the parsed created collection.
     *
     * <p>Reserved for the static parser.
     *
     * @param collection the parsed created collection, or {@code null} when the relay omitted the field
     */
    private BizCatalogManagementCreateCollectionWhatsAppGraphQlResponse(BusinessProductCollection collection) {
        this.collection = collection;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked chain {@code xfb_whatsapp_catalog_create_collection -> collection} and
     * projects the created collection onto a {@link BusinessProductCollection}; the returned
     * {@link Optional} is empty when {@code data} or the collection is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null} or the collection is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementCreateCollectionMutation", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementCreateCollectionWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog_create_collection");
        if (root == null) {
            return Optional.empty();
        }
        var collectionNode = root.getJSONObject("collection");
        if (collectionNode == null) {
            return Optional.empty();
        }
        var collection = CatalogCollectionStatusParser.parseCollection(collectionNode);
        return Optional.of(new BizCatalogManagementCreateCollectionWhatsAppGraphQlResponse(collection));
    }

    /**
     * Returns the parsed created collection.
     *
     * @return the parsed {@link BusinessProductCollection}, never {@code null}
     */
    public BusinessProductCollection collection() {
        return collection;
    }
}
