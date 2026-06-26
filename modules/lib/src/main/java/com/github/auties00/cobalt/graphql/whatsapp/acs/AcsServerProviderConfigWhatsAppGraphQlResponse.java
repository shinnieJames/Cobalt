package com.github.auties00.cobalt.graphql.whatsapp.acs;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.acs.AnonymousCredentialServiceConfig;
import com.github.auties00.cobalt.model.business.acs.AnonymousCredentialServiceConfigBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the anonymous-credential server-provider configuration query built
 * by {@link AcsServerProviderConfigWhatsAppGraphQlRequest} into an
 * {@link AnonymousCredentialServiceConfig}.
 *
 * <p>Reads the linked {@code xwa_wa_acs_config} root and projects its identifier, cipher suite,
 * public key, evaluation and redemption limits, configuration expiry, and token lifetime onto an
 * {@link AnonymousCredentialServiceConfig}.
 *
 * @see AcsServerProviderConfigWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebACSServerProviderConfigQuery")
public final class AcsServerProviderConfigWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed service configuration.
     */
    private final AnonymousCredentialServiceConfig config;

    /**
     * Constructs a response wrapping the parsed service configuration.
     *
     * <p>Reserved for the static parser.
     *
     * @param config the parsed service configuration
     */
    private AcsServerProviderConfigWhatsAppGraphQlResponse(AnonymousCredentialServiceConfig config) {
        this.config = config;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked {@code xwa_wa_acs_config} root and projects it onto an
     * {@link AnonymousCredentialServiceConfig}; the returned {@link Optional} is empty when
     * {@code data} or the root is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} or the root is missing
     */
    public static Optional<AcsServerProviderConfigWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa_wa_acs_config");
        if (root == null) {
            return Optional.empty();
        }

        var config = new AnonymousCredentialServiceConfigBuilder()
                .id(root.getString("id"))
                .cipherSuite(root.getString("cipher_suite"))
                .publicKey(root.getString("public_key"))
                .evaluationLimit(root.getLong("max_evals"))
                .redemptionLimit(root.getLong("redemption_limit"))
                .expiresAtEpochMilli(root.getLong("expire_time"))
                .tokenLifetimeMillis(root.getLong("token_ttl"))
                .build();
        return Optional.of(new AcsServerProviderConfigWhatsAppGraphQlResponse(config));
    }

    /**
     * Returns the parsed service configuration.
     *
     * @return the parsed {@link AnonymousCredentialServiceConfig}, never {@code null}
     */
    public AnonymousCredentialServiceConfig config() {
        return config;
    }
}
