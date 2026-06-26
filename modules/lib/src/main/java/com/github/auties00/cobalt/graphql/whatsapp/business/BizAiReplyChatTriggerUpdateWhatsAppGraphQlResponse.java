package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResult;
import com.github.auties00.cobalt.model.business.ai.BusinessAiMutationResultBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the update-reply-chat-trigger mutation built by
 * {@link BizAiReplyChatTriggerUpdateWhatsAppGraphQlRequest} into a {@link BusinessAiMutationResult}.
 *
 * <p>Projects the linked {@code xfb_meta_ai_biz_agent_wa_update_reply_chat_trigger} field, whose
 * single {@code success} scalar reports whether the chat-scope update was applied, onto the shared
 * mutation-result shape.
 *
 * @see BizAiReplyChatTriggerUpdateWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiReplyChatTriggerUpdateMutation")
public final class BizAiReplyChatTriggerUpdateWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
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
    private BizAiReplyChatTriggerUpdateWhatsAppGraphQlResponse(BusinessAiMutationResult result) {
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
    public static Optional<BizAiReplyChatTriggerUpdateWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xfb_meta_ai_biz_agent_wa_update_reply_chat_trigger");
        if (node == null) {
            return Optional.of(new BizAiReplyChatTriggerUpdateWhatsAppGraphQlResponse(null));
        }

        var result = new BusinessAiMutationResultBuilder()
                .success(Boolean.TRUE.equals(node.getBoolean("success")))
                .build();
        return Optional.of(new BizAiReplyChatTriggerUpdateWhatsAppGraphQlResponse(result));
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
