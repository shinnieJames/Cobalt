package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the fetch-OIDC-state query built by
 * {@link FetchOidcStateWhatsAppGraphQlRequest}.
 *
 * <p>Exposes the single scalar field {@code xfb_wa_biz_get_oidc_state}, the OpenID Connect state
 * blob the relay returns for the authenticated WhatsApp Business session.
 *
 * @see FetchOidcStateWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebFetchOIDCStateQuery")
public final class FetchOidcStateWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the OIDC state blob returned under {@code xfb_wa_biz_get_oidc_state}.
     */
    private final String oidcState;

    /**
     * Constructs a response wrapping the parsed OIDC state blob.
     *
     * <p>Reserved for the static parser.
     *
     * @param oidcState the OIDC state blob, or {@code null} when the relay omitted the field
     */
    private FetchOidcStateWhatsAppGraphQlResponse(String oidcState) {
        this.oidcState = oidcState;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the scalar root {@code xfb_wa_biz_get_oidc_state}; the returned {@link Optional} is
     * empty when {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<FetchOidcStateWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var oidcState = data.getString("xfb_wa_biz_get_oidc_state");
        return Optional.of(new FetchOidcStateWhatsAppGraphQlResponse(oidcState));
    }

    /**
     * Returns the OpenID Connect state blob.
     *
     * @return the OIDC state blob, or empty when the relay omitted the field
     */
    public Optional<String> oidcState() {
        return Optional.ofNullable(oidcState);
    }
}
