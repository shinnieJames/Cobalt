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
 * Parses the WhatsApp Web GraphQL response of the appeal-product mutation built by
 * {@link BizCatalogManagementAppealProductWhatsAppGraphQlRequest} into a {@link BusinessCatalogMutationResult}.
 *
 * <p>Reads the linked root {@code xfb_whatsapp_catalog_appeal_product} and projects its
 * {@code success} flag onto the shared status-only outcome.
 *
 * @see BizCatalogManagementAppealProductWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementAppealProductMutation")
public final class BizCatalogManagementAppealProductWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed appeal outcome.
     */
    private final BusinessCatalogMutationResult result;

    /**
     * Constructs a response wrapping the parsed appeal outcome.
     *
     * <p>Reserved for the static parser.
     *
     * @param result the parsed appeal outcome, never {@code null}
     */
    private BizCatalogManagementAppealProductWhatsAppGraphQlResponse(BusinessCatalogMutationResult result) {
        this.result = result;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code xfb_whatsapp_catalog_appeal_product} and projects its
     * {@code success} flag onto a {@link BusinessCatalogMutationResult}; the returned {@link Optional}
     * is empty when {@code data} or the root is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null} or the root is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementAppealProductMutation", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementAppealProductWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog_appeal_product");
        if (root == null) {
            return Optional.empty();
        }
        var result = new BusinessCatalogMutationResultBuilder()
                .success(Boolean.TRUE.equals(root.getBoolean("success")))
                .build();
        return Optional.of(new BizCatalogManagementAppealProductWhatsAppGraphQlResponse(result));
    }

    /**
     * Returns the parsed appeal outcome.
     *
     * @return the parsed {@link BusinessCatalogMutationResult}, never {@code null}
     */
    public BusinessCatalogMutationResult result() {
        return result;
    }
}
