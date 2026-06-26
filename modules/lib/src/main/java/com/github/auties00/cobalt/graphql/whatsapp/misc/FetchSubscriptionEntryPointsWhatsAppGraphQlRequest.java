package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Builds the relay query that fetches the subscription entry-point configuration, such as the Meta
 * Verified upsell surfaces eligible for the authenticated session.
 *
 * <p>The operation takes no variables; it asks the relay for the set of subscription entry points the
 * session may surface, returned under the linked root {@code xwa_subscription_entrypoints} (aliased
 * {@code waSubscriptionEntryPoints}). The reply is consumed through
 * {@link FetchSubscriptionEntryPointsWhatsAppGraphQlResponse}.
 *
 * @see FetchSubscriptionEntryPointsWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebFetchSubscriptionEntryPointsQuery")
public final class FetchSubscriptionEntryPointsWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchSubscriptionEntryPointsQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "9569660009784796";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchSubscriptionEntryPointsQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebFetchSubscriptionEntryPointsQuery";

    /**
     * Constructs a fetch-subscription-entry-points request.
     *
     * <p>The operation carries no variables, so the request holds no state.
     */
    public FetchSubscriptionEntryPointsWhatsAppGraphQlRequest() {
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
