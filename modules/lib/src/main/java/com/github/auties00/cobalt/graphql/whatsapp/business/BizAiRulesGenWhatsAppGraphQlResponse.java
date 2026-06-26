package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiRule;

import java.util.List;
import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the generate-business-AI-rules mutation built by
 * {@link BizAiRulesGenWhatsAppGraphQlRequest} into a list of {@link BusinessAiRule}.
 *
 * <p>Projects the linked {@code xfb_meta_ai_biz_agent_wa_gen_rules} field: when the relay reports
 * success, each synthesized rule under {@code rules} is projected onto a {@link BusinessAiRule}
 * carrying the rule's identifier, kind, custom-instruction text, and the emoji-frequency and
 * price-sharing markers. The list is empty when the relay reported failure or synthesized no rule.
 *
 * @see BizAiRulesGenWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiRulesGenMutation")
public final class BizAiRulesGenWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the projected synthesized rules.
     */
    private final List<BusinessAiRule> rules;

    /**
     * Constructs a response wrapping the projected synthesized rules.
     *
     * <p>Reserved for the static parser.
     *
     * @param rules the projected synthesized rules
     */
    private BizAiRulesGenWhatsAppGraphQlResponse(List<BusinessAiRule> rules) {
        this.rules = rules;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects each
     * synthesized rule onto a {@link BusinessAiRule}.
     *
     * <p>Reads the linked root {@code xfb_meta_ai_biz_agent_wa_gen_rules}; the returned
     * {@link Optional} is empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiRulesGenWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xfb_meta_ai_biz_agent_wa_gen_rules");
        if (node == null || !Boolean.TRUE.equals(node.getBoolean("success"))) {
            return Optional.of(new BizAiRulesGenWhatsAppGraphQlResponse(List.of()));
        }

        var rules = BizAiRuleProjection.ofArray(node.getJSONArray("rules"));
        return Optional.of(new BizAiRulesGenWhatsAppGraphQlResponse(rules));
    }

    /**
     * Returns the projected synthesized rules.
     *
     * @return the projected rules, empty when the relay reported failure or synthesized none
     */
    public List<BusinessAiRule> rules() {
        return rules;
    }
}
