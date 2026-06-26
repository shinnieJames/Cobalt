package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResult;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResultBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the re-engagement-update mutation built by
 * {@link BizAiReengagementUpdateWhatsAppGraphQlRequest} into a {@link BusinessAiMutationResult}.
 *
 * <p>Projects the linked {@code xfb_meta_ai_biz_agent_wa_update_reengagement} field onto the shared
 * mutation-result shape. The relay echoes the persisted re-engagement settings rather than a status
 * flag, so the presence of the echoed stanza is read as success.
 *
 * @see BizAiReengagementUpdateWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiReengagementUpdateMutation")
public final class BizAiReengagementUpdateWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
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
    private BizAiReengagementUpdateWhatsAppGraphQlResponse(BusinessAiMutationResult result) {
        this.result = result;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects the echoed
     * re-engagement settings onto a {@link BusinessAiMutationResult}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiReengagementUpdateWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xfb_meta_ai_biz_agent_wa_update_reengagement");
        if (node == null) {
            return Optional.of(new BizAiReengagementUpdateWhatsAppGraphQlResponse(null));
        }

        var result = new BusinessAiMutationResultBuilder()
                .success(true)
                .build();
        return Optional.of(new BizAiReengagementUpdateWhatsAppGraphQlResponse(result));
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
