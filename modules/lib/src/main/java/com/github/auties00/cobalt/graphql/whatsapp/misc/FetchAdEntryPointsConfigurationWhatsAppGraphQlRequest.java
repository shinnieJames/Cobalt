package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Builds the relay query that fetches the click-to-WhatsApp ad entry-points configuration for the
 * authenticated session.
 *
 * <p>The operation takes no variables; it asks the relay for the set of ad entry-point entitlements
 * gating where ad surfaces may appear, returned under the plural
 * {@code ctwa_client_entry_point_entitlement}. WhatsApp Web's
 * {@code WAWebFetchAdEntryPointsConfiguration.fetchAdEntryPointsConfiguration} issues this query when
 * its locale-keyed cache misses. The reply is consumed through
 * {@link FetchAdEntryPointsConfigurationWhatsAppGraphQlResponse}.
 *
 * @see FetchAdEntryPointsConfigurationWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebFetchAdEntryPointsConfigurationQuery")
public final class FetchAdEntryPointsConfigurationWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchAdEntryPointsConfigurationQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "9656368401073090";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchAdEntryPointsConfigurationQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebFetchAdEntryPointsConfigurationQuery";

    /**
     * Constructs a fetch-ad-entry-points-configuration request.
     *
     * <p>The operation carries no variables, so the request holds no state.
     */
    public FetchAdEntryPointsConfigurationWhatsAppGraphQlRequest() {
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
