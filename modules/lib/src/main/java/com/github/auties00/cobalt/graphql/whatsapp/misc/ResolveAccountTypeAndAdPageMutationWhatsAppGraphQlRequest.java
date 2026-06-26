package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Builds the relay mutation that clears the caller's stored OIDC account-type preference while
 * resolving the WhatsApp Business account type and ad page.
 *
 * <p>The operation takes no variables; it instructs the relay to drop the persisted OpenID Connect
 * account-type preference tied to the authenticated WhatsApp Web session, returned as the scalar
 * {@code xfb_wa_biz_clear_oidc_preference}. The reply is consumed through
 * {@link ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlResponse}.
 *
 * @see ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebResolveAccountTypeAndAdPageMutation")
public final class ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebResolveAccountTypeAndAdPageMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "24732033759799062";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebResolveAccountTypeAndAdPageMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebResolveAccountTypeAndAdPageMutation";

    /**
     * Constructs a resolve-account-type-and-ad-page mutation request.
     *
     * <p>The operation carries no variables, so the request holds no state.
     */
    public ResolveAccountTypeAndAdPageMutationWhatsAppGraphQlRequest() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String docId() {
        return DOC_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation returns the empty object {@code "{}"}: the operation declares no
     * GraphQL variables, so there is nothing to serialize.
     */
    @Override
    public String variables() {
        return "{}";
    }
}
