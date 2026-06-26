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
 * Builds the relay mutation that creates a business AI assistant rule.
 *
 * <p>The mutation takes a single {@code request} GraphQL variable. WhatsApp Web's
 * {@code WAWebBizAiRuleCreateMutation.createRule(request)} forwards the caller's {@code request}
 * object straight through to the relay under the shape {@code {request: <request>}}; the object is an
 * opaque pass-through built by an unbundled caller, so its internal field names are not recoverable
 * from the current snapshot. The relay returns the created rule under
 * {@code xfb_meta_ai_biz_agent_wa_create_rule}; the reply is consumed through
 * {@link BizAiRuleCreateWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code request} object as a caller-supplied, already
 * JSON-encoded object literal because the server-side input type's field names are not present in the
 * JS bundle of snapshot {@code 1040120866} (the dispatcher forwards the caller's argument verbatim,
 * and no caller that builds the object is bundled); the value is emitted verbatim as the
 * {@code request} variable. Once a caller that builds the object surfaces, replace this with typed
 * scalar fields mirroring that construction.
 *
 * @see BizAiRuleCreateWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiRuleCreateMutation")
public final class BizAiRuleCreateWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiRuleCreateMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "35226836873596446";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiRuleCreateMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiRuleCreateMutation";

    /**
     * The pre-encoded JSON of the {@code request} GraphQL object describing the rule to create, or
     * {@code null} to omit it.
     */
    private final String requestJson;

    /**
     * Constructs a create-business-AI-rule mutation request.
     *
     * <p>The {@code requestJson} is the already-JSON-encoded {@code request} object describing the
     * rule to create; its field names are defined by the server-side input type and are not modelled
     * here (see the class {@code @implNote}). A {@code null} value omits the variable from the
     * serialized object.
     *
     * @param requestJson the already-JSON-encoded {@code request} object, or {@code null} to omit the
     *                    variable
     */
    public BizAiRuleCreateWhatsAppGraphQlRequest(String requestJson) {
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
    @WhatsAppWebExport(moduleName = "WAWebBizAiRuleCreateMutation", exports = "createRule",
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
