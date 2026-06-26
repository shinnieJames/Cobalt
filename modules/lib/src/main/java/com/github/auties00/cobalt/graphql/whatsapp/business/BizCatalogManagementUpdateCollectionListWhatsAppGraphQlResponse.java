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
 * Parses the WhatsApp Web GraphQL response of the update-collection-list mutation built by
 * {@link BizCatalogManagementUpdateCollectionListWhatsAppGraphQlRequest} into a
 * {@link BusinessCatalogMutationResult}.
 *
 * <p>Reads the linked root {@code xfb_whatsapp_catalog_update_collection_list} and projects its
 * {@code success} flag onto the shared status-only outcome.
 *
 * @see BizCatalogManagementUpdateCollectionListWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementUpdateCollectionListMutation")
public final class BizCatalogManagementUpdateCollectionListWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed reorder outcome.
     */
    private final BusinessCatalogMutationResult result;

    /**
     * Constructs a response wrapping the parsed reorder outcome.
     *
     * <p>Reserved for the static parser.
     *
     * @param result the parsed reorder outcome, never {@code null}
     */
    private BizCatalogManagementUpdateCollectionListWhatsAppGraphQlResponse(BusinessCatalogMutationResult result) {
        this.result = result;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code xfb_whatsapp_catalog_update_collection_list} and projects its
     * {@code success} flag onto a {@link BusinessCatalogMutationResult}; the returned {@link Optional}
     * is empty when {@code data} or the root is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null} or the root is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementUpdateCollectionListMutation", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementUpdateCollectionListWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog_update_collection_list");
        if (root == null) {
            return Optional.empty();
        }
        var result = new BusinessCatalogMutationResultBuilder()
                .success(Boolean.TRUE.equals(root.getBoolean("success")))
                .build();
        return Optional.of(new BizCatalogManagementUpdateCollectionListWhatsAppGraphQlResponse(result));
    }

    /**
     * Returns the parsed reorder outcome.
     *
     * @return the parsed {@link BusinessCatalogMutationResult}, never {@code null}
     */
    public BusinessCatalogMutationResult result() {
        return result;
    }
}
