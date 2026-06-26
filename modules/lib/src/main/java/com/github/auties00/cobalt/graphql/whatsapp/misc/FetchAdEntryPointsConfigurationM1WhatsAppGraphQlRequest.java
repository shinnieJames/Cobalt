package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Builds the relay query that fetches the click-to-WhatsApp (CTWA) advertising entry-point
 * entitlement configuration.
 *
 * <p>The operation takes no variables; it asks the relay for the set of CTWA client entry points the
 * authenticated session is entitled to surface, returned under the plural linked root
 * {@code ctwa_client_entry_point_entitlement}. The reply is consumed through
 * {@link FetchAdEntryPointsConfigurationM1WhatsAppGraphQlResponse}.
 *
 * @see FetchAdEntryPointsConfigurationM1WhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebFetchAdEntryPointsConfigurationM1Query")
public final class FetchAdEntryPointsConfigurationM1WhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchAdEntryPointsConfigurationM1Query.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "9737776042983782";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchAdEntryPointsConfigurationM1Query.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebFetchAdEntryPointsConfigurationM1Query";

    /**
     * Constructs a fetch-ad-entry-points-configuration request.
     *
     * <p>The operation carries no variables, so the request holds no state.
     */
    public FetchAdEntryPointsConfigurationM1WhatsAppGraphQlRequest() {
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
