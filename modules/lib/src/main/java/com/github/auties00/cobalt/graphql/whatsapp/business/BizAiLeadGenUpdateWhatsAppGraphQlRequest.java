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
 * Builds the relay mutation that updates a lead-generation flow of a WhatsApp Business AI agent.
 *
 * <p>The single {@code request} GraphQL variable is the lead-gen-flow update input. WhatsApp Web's
 * {@code WAWebBizAiLeadGenUpdateMutation.updateLeadGenFlow(request)} forwards the caller-built object
 * straight through to the relay as {@code {request: request}}; the update input describes the flow
 * changes, mirroring the lead-gen-flow shape returned by {@code WAWebBizAiLeadGenFormsQuery} (a flow
 * id, a moment configuration, and a list of {@code {label, is_enabled}} fields). The relay returns
 * the update outcome under {@code xfb_meta_ai_biz_agent_wa_update_lead_gen_flow}; the reply is consumed
 * through {@link BizAiLeadGenUpdateWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code request} object as a caller-supplied, already
 * JSON-encoded object literal because no caller in the JS bundle of snapshot {@code 1040120866}
 * constructs it (the dispatcher forwards its argument verbatim), so the input type's field names are
 * not recoverable; the value is emitted verbatim as the {@code request} variable. Once a caller that
 * builds the object surfaces, replace this with typed scalar fields mirroring that construction.
 *
 * @see BizAiLeadGenUpdateWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiLeadGenUpdateMutation")
public final class BizAiLeadGenUpdateWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiLeadGenUpdateMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "26449028471447289";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiLeadGenUpdateMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiLeadGenUpdateMutation";

    /**
     * The pre-encoded JSON of the {@code request} GraphQL object describing the flow changes, or
     * {@code null} to omit it.
     */
    private final String requestJson;

    /**
     * Constructs an update-lead-gen-flow mutation request.
     *
     * <p>The {@code requestJson} is the already-JSON-encoded {@code request} object describing the
     * flow changes; its field names are defined by the server-side update input type and are not
     * modelled here (see the class {@code @implNote}). A {@code null} value omits the variable.
     *
     * @param requestJson the already-JSON-encoded {@code request} object, or {@code null} to omit the
     *                    variable
     */
    public BizAiLeadGenUpdateWhatsAppGraphQlRequest(String requestJson) {
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
    @WhatsAppWebExport(moduleName = "WAWebBizAiLeadGenUpdateMutation", exports = "updateLeadGenFlow",
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
