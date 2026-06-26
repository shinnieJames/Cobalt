package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiRule;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the update-business-AI-rule mutation built by
 * {@link BizAiRuleUpdateWhatsAppGraphQlRequest} into a {@link BusinessAiRule}.
 *
 * <p>Projects the linked {@code xfb_meta_ai_biz_agent_wa_update_rule} field: when the relay reports
 * success and echoes the rule's post-update state, the rule's identifier, kind, custom-instruction
 * text, and the emoji-frequency and price-sharing markers are projected onto a {@link BusinessAiRule}.
 * The projection is absent when the relay reported failure or echoed no rule.
 *
 * @see BizAiRuleUpdateWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiRuleUpdateMutation")
public final class BizAiRuleUpdateWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the projected updated rule, or {@code null} when the relay reported failure or echoed no
     * rule.
     */
    private final BusinessAiRule rule;

    /**
     * Constructs a response wrapping the projected updated rule.
     *
     * <p>Reserved for the static parser.
     *
     * @param rule the projected updated rule, or {@code null} when the relay reported failure or echoed
     *             no rule
     */
    private BizAiRuleUpdateWhatsAppGraphQlResponse(BusinessAiRule rule) {
        this.rule = rule;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects the
     * echoed rule onto a {@link BusinessAiRule}.
     *
     * <p>Reads the linked root {@code xfb_meta_ai_biz_agent_wa_update_rule}; the returned
     * {@link Optional} is empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiRuleUpdateWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xfb_meta_ai_biz_agent_wa_update_rule");
        if (node == null || !Boolean.TRUE.equals(node.getBoolean("success"))) {
            return Optional.of(new BizAiRuleUpdateWhatsAppGraphQlResponse(null));
        }

        var rule = BizAiRuleProjection.of(node.getJSONObject("rule"));
        return Optional.of(new BizAiRuleUpdateWhatsAppGraphQlResponse(rule));
    }

    /**
     * Returns the projected updated rule.
     *
     * @return the projected {@link BusinessAiRule}, or empty when the relay reported failure or echoed
     *         no rule
     */
    public Optional<BusinessAiRule> rule() {
        return Optional.ofNullable(rule);
    }
}
