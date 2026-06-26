package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Builds the relay query that fetches a WhatsApp Business AI agent's knowledge home, including its
 * example responses and product-info knowledge.
 *
 * <p>The operation takes no variables; it asks the relay for the agent's
 * {@code xfb_meta_ai_biz_agent_wa_ai_home} projection, which carries the ordered knowledge entries,
 * the website-backed knowledge, the product-info entries, and the product-info eligibility flag. The
 * reply is consumed through {@link BizAiExampleResponsesWhatsAppGraphQlResponse}.
 *
 * @see BizAiExampleResponsesWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiExampleResponsesQuery")
public final class BizAiExampleResponsesWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiExampleResponsesQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "26131148333230179";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiExampleResponsesQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiExampleResponsesQuery";

    /**
     * Constructs a fetch-example-responses request.
     *
     * <p>The operation carries no variables, so the request holds no state.
     */
    public BizAiExampleResponsesWhatsAppGraphQlRequest() {
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
