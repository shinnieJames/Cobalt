package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.waa.WhatsAppAdsAccountTypeReset;
import com.github.auties00.cobalt.model.business.waa.WhatsAppAdsAccountTypeResetBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the resolve-account-type-and-ad-page mutation built by
 * {@link ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlRequest} into a
 * {@link WhatsAppAdsAccountTypeReset}.
 *
 * <p>Reads the single scalar root {@code xfb_wa_biz_clear_oidc_preference} and projects it onto the
 * Cobalt domain model: the server's acknowledgement that the cached WhatsApp Ads account-type
 * preference was cleared so the next sign-in re-prompts for the account type and Facebook page.
 *
 * @see ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebResolveAccountTypeAndAdPageMutation")
public final class ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed account-type reset acknowledgement.
     */
    private final WhatsAppAdsAccountTypeReset reset;

    /**
     * Constructs a response wrapping the parsed acknowledgement.
     *
     * <p>Reserved for the static parser.
     *
     * @param reset the parsed acknowledgement, or {@code null} when the relay omitted the field
     */
    private ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlResponse(WhatsAppAdsAccountTypeReset reset) {
        this.reset = reset;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the scalar root {@code xfb_wa_biz_clear_oidc_preference} and projects it onto a
     * {@link WhatsAppAdsAccountTypeReset}; the returned {@link Optional} is empty when {@code data}
     * is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var reset = new WhatsAppAdsAccountTypeResetBuilder()
                .acknowledgement(data.getString("xfb_wa_biz_clear_oidc_preference"))
                .build();
        return Optional.of(new ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlResponse(reset));
    }

    /**
     * Returns the parsed account-type reset acknowledgement.
     *
     * @return the parsed {@link WhatsAppAdsAccountTypeReset}, never {@code null}
     */
    public WhatsAppAdsAccountTypeReset reset() {
        return reset;
    }
}
