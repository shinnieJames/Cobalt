package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiRule;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the create-business-AI-rule mutation built by
 * {@link BizAiRuleCreateWhatsAppGraphQlRequest} into a {@link BusinessAiRule}.
 *
 * <p>Projects the linked {@code xfb_meta_ai_biz_agent_wa_create_rule} field: when the relay reports
 * success and echoes the created rule, the rule's identifier, kind, custom-instruction text, and the
 * emoji-frequency and price-sharing markers are projected onto a {@link BusinessAiRule}. The
 * projection is absent when the relay reported failure or echoed no rule.
 *
 * @see BizAiRuleCreateWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiRuleCreateMutation")
public final class BizAiRuleCreateWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the projected created rule, or {@code null} when the relay reported failure or echoed no
     * rule.
     */
    private final BusinessAiRule rule;

    /**
     * Constructs a response wrapping the projected created rule.
     *
     * <p>Reserved for the static parser.
     *
     * @param rule the projected created rule, or {@code null} when the relay reported failure or echoed
     *             no rule
     */
    private BizAiRuleCreateWhatsAppGraphQlResponse(BusinessAiRule rule) {
        this.rule = rule;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects the
     * created rule onto a {@link BusinessAiRule}.
     *
     * <p>Reads the linked root {@code xfb_meta_ai_biz_agent_wa_create_rule}; the returned
     * {@link Optional} is empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiRuleCreateWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xfb_meta_ai_biz_agent_wa_create_rule");
        if (node == null || !Boolean.TRUE.equals(node.getBoolean("success"))) {
            return Optional.of(new BizAiRuleCreateWhatsAppGraphQlResponse(null));
        }

        var rule = BizAiRuleProjection.of(node.getJSONObject("rule"));
        return Optional.of(new BizAiRuleCreateWhatsAppGraphQlResponse(rule));
    }

    /**
     * Returns the projected created rule.
     *
     * @return the projected {@link BusinessAiRule}, or empty when the relay reported failure or echoed
     *         no rule
     */
    public Optional<BusinessAiRule> rule() {
        return Optional.ofNullable(rule);
    }
}
