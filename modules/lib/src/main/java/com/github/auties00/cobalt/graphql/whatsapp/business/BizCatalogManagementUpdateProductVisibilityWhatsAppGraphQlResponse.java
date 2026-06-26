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
 * Parses the WhatsApp Web GraphQL response of the update-product-visibility mutation built by
 * {@link BizCatalogManagementUpdateProductVisibilityWhatsAppGraphQlRequest} into a
 * {@link BusinessCatalogMutationResult}.
 *
 * <p>Reads the linked root {@code xfb_whatsapp_catalog_product_visibility_update} and projects its
 * {@code success} flag onto the shared status-only outcome.
 *
 * @see BizCatalogManagementUpdateProductVisibilityWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementUpdateProductVisibilityMutation")
public final class BizCatalogManagementUpdateProductVisibilityWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed visibility-update outcome.
     */
    private final BusinessCatalogMutationResult result;

    /**
     * Constructs a response wrapping the parsed visibility-update outcome.
     *
     * <p>Reserved for the static parser.
     *
     * @param result the parsed visibility-update outcome, never {@code null}
     */
    private BizCatalogManagementUpdateProductVisibilityWhatsAppGraphQlResponse(BusinessCatalogMutationResult result) {
        this.result = result;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code xfb_whatsapp_catalog_product_visibility_update} and projects its
     * {@code success} flag onto a {@link BusinessCatalogMutationResult}; the returned {@link Optional}
     * is empty when {@code data} or the root is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null} or the root is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementUpdateProductVisibilityMutation", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementUpdateProductVisibilityWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog_product_visibility_update");
        if (root == null) {
            return Optional.empty();
        }
        var result = new BusinessCatalogMutationResultBuilder()
                .success(Boolean.TRUE.equals(root.getBoolean("success")))
                .build();
        return Optional.of(new BizCatalogManagementUpdateProductVisibilityWhatsAppGraphQlResponse(result));
    }

    /**
     * Returns the parsed visibility-update outcome.
     *
     * @return the parsed {@link BusinessCatalogMutationResult}, never {@code null}
     */
    public BusinessCatalogMutationResult result() {
        return result;
    }
}
