package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the relay mutation that creates a lead-generation flow for a WhatsApp Business AI agent.
 *
 * <p>The single {@code request} GraphQL variable is the lead-gen-flow creation input. WhatsApp Web's
 * {@code WAWebBizAiLeadGenCreateMutation.createLeadGenFlow(request)} forwards the caller-built object
 * straight through to the relay; the creation input describes the flow to create, mirroring the
 * lead-gen-flow shape returned by {@code WAWebBizAiLeadGenFormsQuery} (a moment type, a custom moment,
 * and a list of {@code {label, is_enabled}} fields). The relay returns the creation outcome under
 * {@code xfb_meta_ai_biz_agent_wa_create_lead_gen_flow}; the reply is consumed through
 * {@link BizAiLeadGenCreateWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code request} object as a caller-supplied, already
 * JSON-encoded object literal because no caller in the JS bundle of snapshot {@code 1040120866}
 * constructs it (the dispatcher forwards its argument verbatim), so the input type's field names are
 * not recoverable; the value is emitted verbatim as the {@code request} variable. Once a caller that
 * builds the object surfaces, replace this with typed scalar fields mirroring that construction.
 *
 * @see BizAiLeadGenCreateWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiLeadGenCreateMutation")
public final class BizAiLeadGenCreateWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiLeadGenCreateMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "25741990895475954";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiLeadGenCreateMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiLeadGenCreateMutation";

    /**
     * The pre-encoded JSON of the {@code request} GraphQL object describing the flow to create, or
     * {@code null} to omit it.
     */
    private final String requestJson;

    /**
     * Constructs a create-lead-gen-flow mutation request.
     *
     * <p>The {@code requestJson} is the already-JSON-encoded {@code request} object describing the
     * flow to create; its field names are defined by the server-side creation input type and are not
     * modelled here (see the class {@code @implNote}). A {@code null} value omits the variable.
     *
     * @param requestJson the already-JSON-encoded {@code request} object, or {@code null} to omit the
     *                    variable
     */
    public BizAiLeadGenCreateWhatsAppGraphQlRequest(String requestJson) {
        this.requestJson = requestJson;
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
     * @implNote This implementation emits {@code {"request": <requestJson>}}, writing the variable
     * only when its value is non-null and emitting {@code "{}"} when it is {@code null}. The
     * {@code request} value is spliced in as a raw JSON value via {@link JSONWriter#writeRaw(String)}
     * because it is supplied already encoded.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiLeadGenCreateMutation", exports = "createLeadGenFlow",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            if (requestJson != null) {
                writer.writeName("request");
                writer.writeColon();
                writer.writeRaw(requestJson);
            }
            writer.endObject();
            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return output.toString();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
