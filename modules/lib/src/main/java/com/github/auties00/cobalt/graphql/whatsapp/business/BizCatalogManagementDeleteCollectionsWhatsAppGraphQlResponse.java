package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogMutationResult;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogMutationResultBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the delete-collections mutation built by
 * {@link BizCatalogManagementDeleteCollectionsWhatsAppGraphQlRequest} into a
 * {@link BusinessCatalogMutationResult}.
 *
 * <p>Reads the linked root {@code xfb_whatsapp_catalog_delete_collections} and projects its
 * {@code success} flag onto the shared status-only outcome.
 *
 * @see BizCatalogManagementDeleteCollectionsWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementDeleteCollectionsMutation")
public final class BizCatalogManagementDeleteCollectionsWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed deletion outcome.
     */
    private final BusinessCatalogMutationResult result;

    /**
     * Constructs a response wrapping the parsed deletion outcome.
     *
     * <p>Reserved for the static parser.
     *
     * @param result the parsed deletion outcome, never {@code null}
     */
    private BizCatalogManagementDeleteCollectionsWhatsAppGraphQlResponse(BusinessCatalogMutationResult result) {
        this.result = result;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code xfb_whatsapp_catalog_delete_collections} and projects its
     * {@code success} flag onto a {@link BusinessCatalogMutationResult}; the returned {@link Optional}
     * is empty when {@code data} or the root is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null} or the root is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementDeleteCollectionsMutation", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementDeleteCollectionsWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog_delete_collections");
        if (root == null) {
            return Optional.empty();
        }
        var result = new BusinessCatalogMutationResultBuilder()
                .success(Boolean.TRUE.equals(root.getBoolean("success")))
                .build();
        return Optional.of(new BizCatalogManagementDeleteCollectionsWhatsAppGraphQlResponse(result));
    }

    /**
     * Returns the parsed deletion outcome.
     *
     * @return the parsed {@link BusinessCatalogMutationResult}, never {@code null}
     */
    public BusinessCatalogMutationResult result() {
        return result;
    }
}
