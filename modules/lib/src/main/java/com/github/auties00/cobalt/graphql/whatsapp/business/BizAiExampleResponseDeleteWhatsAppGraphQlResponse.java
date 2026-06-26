package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResult;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResultBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the delete-knowledge mutation built by
 * {@link BizAiExampleResponseDeleteWhatsAppGraphQlRequest} into a {@link BusinessAiMutationResult}.
 *
 * <p>Projects the linked {@code xfb_meta_ai_biz_agent_wa_delete_knowledge} field, whose single
 * {@code success} scalar reports whether the knowledge entries were deleted, onto the shared
 * mutation-result shape. WhatsApp Web treats the mutation as successful only when {@code success} is
 * exactly {@code true}.
 *
 * @see BizAiExampleResponseDeleteWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiExampleResponseDeleteMutation")
public final class BizAiExampleResponseDeleteWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the projected mutation result, or {@code null} when the relay omitted the field.
     */
    private final BusinessAiMutationResult result;

    /**
     * Constructs a response wrapping the projected mutation result.
     *
     * <p>Reserved for the static parser.
     *
     * @param result the projected mutation result, or {@code null} when the relay omitted the field
     */
    private BizAiExampleResponseDeleteWhatsAppGraphQlResponse(BusinessAiMutationResult result) {
        this.result = result;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects the
     * {@code success} scalar onto a {@link BusinessAiMutationResult}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiExampleResponseDeleteWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xfb_meta_ai_biz_agent_wa_delete_knowledge");
        if (node == null) {
            return Optional.of(new BizAiExampleResponseDeleteWhatsAppGraphQlResponse(null));
        }

        var result = new BusinessAiMutationResultBuilder()
                .success(Boolean.TRUE.equals(node.getBoolean("success")))
                .build();
        return Optional.of(new BizAiExampleResponseDeleteWhatsAppGraphQlResponse(result));
    }

    /**
     * Returns the projected mutation result.
     *
     * @return the projected {@link BusinessAiMutationResult}, or empty when the relay omitted the field
     */
    public Optional<BusinessAiMutationResult> result() {
        return Optional.ofNullable(result);
    }
}
