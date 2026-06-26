package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the edit-business-profile mutation built by
 * {@link EditBizProfileWhatsAppGraphQlRequest}.
 *
 * <p>Exposes the single scalar field {@code edit_wa_web_biz_profile}, the mutation outcome the relay
 * returns for the edited business account.
 *
 * @see EditBizProfileWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebEditBizProfileMutation")
public final class EditBizProfileWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the mutation outcome returned under {@code edit_wa_web_biz_profile}.
     */
    private final String editResult;

    /**
     * Constructs a response wrapping the parsed mutation outcome.
     *
     * <p>Reserved for the static parser.
     *
     * @param editResult the mutation outcome, or {@code null} when the relay omitted the field
     */
    private EditBizProfileWhatsAppGraphQlResponse(String editResult) {
        this.editResult = editResult;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the scalar root {@code edit_wa_web_biz_profile}; the returned {@link Optional} is
     * empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<EditBizProfileWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var editResult = data.getString("edit_wa_web_biz_profile");
        return Optional.of(new EditBizProfileWhatsAppGraphQlResponse(editResult));
    }

    /**
     * Returns the mutation outcome.
     *
     * @return the mutation outcome, or empty when the relay omitted the field
     */
    public Optional<String> editResult() {
        return Optional.ofNullable(editResult);
    }
}
