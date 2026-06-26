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
 * Parses the WhatsApp Web GraphQL response of the create-catalog mutation built by
 * {@link BizCatalogManagementCreateCatalogWhatsAppGraphQlRequest} into a {@link BusinessCatalogMutationResult}.
 *
 * <p>Reads the linked root {@code xfb_whatsapp_catalog_create} and projects its {@code success} flag
 * onto the shared status-only outcome.
 *
 * @see BizCatalogManagementCreateCatalogWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizCatalogManagementCreateCatalogMutation")
public final class BizCatalogManagementCreateCatalogWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed creation outcome.
     */
    private final BusinessCatalogMutationResult result;

    /**
     * Constructs a response wrapping the parsed creation outcome.
     *
     * <p>Reserved for the static parser.
     *
     * @param result the parsed creation outcome, never {@code null}
     */
    private BizCatalogManagementCreateCatalogWhatsAppGraphQlResponse(BusinessCatalogMutationResult result) {
        this.result = result;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code xfb_whatsapp_catalog_create} and projects its {@code success}
     * flag onto a {@link BusinessCatalogMutationResult}; the returned {@link Optional} is empty when
     * {@code data} or the root is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null} or the root is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCatalogManagementCreateCatalogMutation", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<BizCatalogManagementCreateCatalogWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var root = data.getJSONObject("xfb_whatsapp_catalog_create");
        if (root == null) {
            return Optional.empty();
        }
        var result = new BusinessCatalogMutationResultBuilder()
                .success(Boolean.TRUE.equals(root.getBoolean("success")))
                .build();
        return Optional.of(new BizCatalogManagementCreateCatalogWhatsAppGraphQlResponse(result));
    }

    /**
     * Returns the parsed creation outcome.
     *
     * @return the parsed {@link BusinessCatalogMutationResult}, never {@code null}
     */
    public BusinessCatalogMutationResult result() {
        return result;
    }
}
