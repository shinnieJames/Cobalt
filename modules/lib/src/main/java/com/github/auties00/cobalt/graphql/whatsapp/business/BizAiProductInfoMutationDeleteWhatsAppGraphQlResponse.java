package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResult;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResultBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the delete-product-info mutation built by
 * {@link BizAiProductInfoMutationDeleteWhatsAppGraphQlRequest} into a list of {@link BusinessAiMutationResult}.
 *
 * <p>Projects the plural linked root {@code xfb_maiba_multi_delete_product_info_knowledge} onto one
 * {@link BusinessAiMutationResult} per requested product id: each carries the per-id {@code success}
 * flag and the product-catalog id the outcome refers to as its sole affected id. WhatsApp Web treats
 * the whole batch as successful only when every entry reports {@code success == true}.
 *
 * @see BizAiProductInfoMutationDeleteWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiProductInfoMutationDeleteMutation")
public final class BizAiProductInfoMutationDeleteWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the projected per-id deletion outcomes.
     */
    private final List<BusinessAiMutationResult> results;

    /**
     * Constructs a response wrapping the projected per-id deletion outcomes.
     *
     * <p>Reserved for the static parser.
     *
     * @param results the projected per-id deletion outcomes
     */
    private BizAiProductInfoMutationDeleteWhatsAppGraphQlResponse(List<BusinessAiMutationResult> results) {
        this.results = results;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects each
     * per-id outcome onto a {@link BusinessAiMutationResult}.
     *
     * <p>Reads the plural linked root {@code xfb_maiba_multi_delete_product_info_knowledge}; the
     * returned {@link Optional} is empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiProductInfoMutationDeleteWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var results = parseResults(data.getJSONArray("xfb_maiba_multi_delete_product_info_knowledge"));
        return Optional.of(new BizAiProductInfoMutationDeleteWhatsAppGraphQlResponse(results));
    }

    /**
     * Projects the {@code xfb_maiba_multi_delete_product_info_knowledge} array onto a list of
     * {@link BusinessAiMutationResult}.
     *
     * @param arr the {@code xfb_maiba_multi_delete_product_info_knowledge} array, possibly {@code null}
     * @return the projected outcomes, never {@code null}
     */
    private static List<BusinessAiMutationResult> parseResults(JSONArray arr) {
        if (arr == null) {
            return List.of();
        }

        var result = new ArrayList<BusinessAiMutationResult>(arr.size());
        for (var i = 0; i < arr.size(); i++) {
            var obj = arr.getJSONObject(i);
            if (obj == null) {
                continue;
            }
            var productId = obj.getString("product_id");
            result.add(new BusinessAiMutationResultBuilder()
                    .success(Boolean.TRUE.equals(obj.getBoolean("success")))
                    .affectedIds(productId == null ? List.of() : List.of(productId))
                    .build());
        }
        return result;
    }

    /**
     * Returns the projected per-id deletion outcomes.
     *
     * @return the projected outcomes, empty when the relay returned none
     */
    public List<BusinessAiMutationResult> results() {
        return results;
    }
}
