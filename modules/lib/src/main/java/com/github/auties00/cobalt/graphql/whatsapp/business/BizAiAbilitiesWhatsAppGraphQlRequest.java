package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Builds the relay query that fetches the AI capabilities available to a WhatsApp Business AI agent.
 *
 * <p>The operation takes no variables; it asks the relay for the abilities surfaced on the business
 * AI home view, each tagged with its type and current availability status. The relay returns them
 * under the linked {@code xfb_meta_ai_biz_agent_wa_ai_home} field; the reply is consumed through
 * {@link BizAiAbilitiesWhatsAppGraphQlResponse}.
 *
 * @see BizAiAbilitiesWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiAbilitiesQuery")
public final class BizAiAbilitiesWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiAbilitiesQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "35774632425517289";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiAbilitiesQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiAbilitiesQuery";

    /**
     * Constructs a business-AI-abilities query request.
     *
     * <p>The operation carries no variables, so the request holds no state.
     */
    public BizAiAbilitiesWhatsAppGraphQlRequest() {
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
