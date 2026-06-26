package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.linking.BusinessAccountNonce;
import com.github.auties00.cobalt.model.business.linking.BusinessAccountNonceBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the account-nonce mutation built by
 * {@link GetAccountNonceWhatsAppGraphQlRequest} into a {@link BusinessAccountNonce}.
 *
 * <p>Reads the linked root {@code xfb_wa_biz_account_nonce} and flattens its nested
 * {@code detail -> nonce / request.id} chain onto the Cobalt domain model: the issued single-use
 * nonce paired with the identifier of the request that produced it.
 *
 * @see GetAccountNonceWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebGetAccountNonceMutation")
public final class GetAccountNonceWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed account nonce.
     */
    private final BusinessAccountNonce nonce;

    /**
     * Constructs a response wrapping the parsed account nonce.
     *
     * <p>Reserved for the static parser.
     *
     * @param nonce the parsed account nonce, or {@code null} when the relay omitted the field
     */
    private GetAccountNonceWhatsAppGraphQlResponse(BusinessAccountNonce nonce) {
        this.nonce = nonce;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code xfb_wa_biz_account_nonce} and projects its {@code detail}
     * sub-object onto a {@link BusinessAccountNonce}; the returned {@link Optional} is empty when
     * {@code data}, the root, or the detail object is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data}, the root, or the detail object is
     *         missing
     */
    public static Optional<GetAccountNonceWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xfb_wa_biz_account_nonce");
        if (root == null) {
            return Optional.empty();
        }

        var detail = root.getJSONObject("detail");
        if (detail == null) {
            return Optional.empty();
        }

        var request = detail.getJSONObject("request");
        var requestId = request == null ? null : request.getString("id");
        var nonce = new BusinessAccountNonceBuilder()
                .nonce(detail.getString("nonce"))
                .requestId(requestId)
                .build();
        return Optional.of(new GetAccountNonceWhatsAppGraphQlResponse(nonce));
    }

    /**
     * Returns the parsed account nonce.
     *
     * @return the parsed {@link BusinessAccountNonce}, never {@code null}
     */
    public BusinessAccountNonce nonce() {
        return nonce;
    }
}
