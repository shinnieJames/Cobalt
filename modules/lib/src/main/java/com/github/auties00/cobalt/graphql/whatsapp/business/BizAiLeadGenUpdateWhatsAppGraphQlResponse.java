package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ai.BusinessAiLeadGenForm;
import com.github.auties00.cobalt.model.business.ai.BusinessAiLeadGenFormBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the update-lead-gen-flow mutation built by
 * {@link BizAiLeadGenUpdateWhatsAppGraphQlRequest} into a {@link BusinessAiLeadGenForm}.
 *
 * <p>The relay confirms the update with a single {@code success} scalar under
 * {@code xfb_meta_ai_biz_agent_wa_update_lead_gen_flow} without echoing the flow's contents, so the
 * projected {@link BusinessAiLeadGenForm} is present only when the relay reported success and carries
 * no fields beyond representing the updated flow.
 *
 * @see BizAiLeadGenUpdateWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiLeadGenUpdateMutation")
public final class BizAiLeadGenUpdateWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the projected lead-capture flow, or {@code null} when the relay omitted the field or
     * reported failure.
     */
    private final BusinessAiLeadGenForm form;

    /**
     * Constructs a response wrapping the projected lead-capture flow.
     *
     * <p>Reserved for the static parser.
     *
     * @param form the projected lead-capture flow, or {@code null} when the relay omitted the field or
     *             reported failure
     */
    private BizAiLeadGenUpdateWhatsAppGraphQlResponse(BusinessAiLeadGenForm form) {
        this.form = form;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object and projects the
     * update outcome onto a {@link BusinessAiLeadGenForm}.
     *
     * <p>Reads the linked root {@code xfb_meta_ai_biz_agent_wa_update_lead_gen_flow}; the returned
     * {@link Optional} is empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<BizAiLeadGenUpdateWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var result = data.getJSONObject("xfb_meta_ai_biz_agent_wa_update_lead_gen_flow");
        var success = result != null && Boolean.TRUE.equals(result.getBoolean("success"));
        var form = success ? new BusinessAiLeadGenFormBuilder().build() : null;
        return Optional.of(new BizAiLeadGenUpdateWhatsAppGraphQlResponse(form));
    }

    /**
     * Returns the projected lead-capture flow updated by the relay.
     *
     * @return the projected {@link BusinessAiLeadGenForm}, or empty when the relay reported failure or
     *         omitted the field
     */
    public Optional<BusinessAiLeadGenForm> form() {
        return Optional.ofNullable(form);
    }
}
