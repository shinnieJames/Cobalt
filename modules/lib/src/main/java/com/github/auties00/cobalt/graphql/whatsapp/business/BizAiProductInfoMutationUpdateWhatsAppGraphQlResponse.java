package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiProductInfo;
import com.github.auties00.cobalt.model.business.ai.BusinessAiProductInfoBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the update-product-info mutation built by
 * {@link BizAiProductInfoMutationUpdateWhatsAppGraphQlRequest} into a {@link BusinessAiProductInfo}.
 *
 * <p>The relay confirms the update with a single {@code success} scalar under
 * {@code xfb_maiba_update_product_info_knowledge} without echoing the product's contents, so the
 * projected {@link BusinessAiProductInfo} is present only when the relay reported success and carries
 * no fields beyond representing the updated entry.
 *
 * @see BizAiProductInfoMutationUpdateWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiProductInfoMutationUpdateMutation")
public final class BizAiProductInfoMutationUpdateWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the projected updated product entry, or {@code null} when the relay reported failure or
     * omitted the field.
     */
    private final BusinessAiProductInfo productInfo;

    /**
     * Constructs a response wrapping the projected updated product entry.
     *
     * <p>Reserved for the static parser.
     *
     * @param productInfo the projected updated product entry, or {@code null} when the relay reported
     *                    failure or omitted the field
     */
    private BizAiProductInfoMutationUpdateWhatsAppGraphQlResponse(BusinessAiProductInfo productInfo) {
        this.productInfo = productInfo;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects the
     * update outcome onto a {@link BusinessAiProductInfo}.
     *
     * <p>Reads the linked root {@code xfb_maiba_update_product_info_knowledge}; the returned
     * {@link Optional} is empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiProductInfoMutationUpdateWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xfb_maiba_update_product_info_knowledge");
        var success = node != null && Boolean.TRUE.equals(node.getBoolean("success"));
        var productInfo = success ? new BusinessAiProductInfoBuilder().build() : null;
        return Optional.of(new BizAiProductInfoMutationUpdateWhatsAppGraphQlResponse(productInfo));
    }

    /**
     * Returns the projected updated product entry.
     *
     * @return the projected {@link BusinessAiProductInfo}, or empty when the relay reported failure or
     *         omitted the field
     */
    public Optional<BusinessAiProductInfo> productInfo() {
        return Optional.ofNullable(productInfo);
    }
}
