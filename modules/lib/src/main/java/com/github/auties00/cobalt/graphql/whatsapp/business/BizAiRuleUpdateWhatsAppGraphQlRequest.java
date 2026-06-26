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
 * Builds the relay mutation that updates a single WhatsApp Business AI agent rule.
 *
 * <p>The single {@code request} GraphQL variable carries the rule to apply. WhatsApp Web's
 * {@code WAWebBizAiRuleUpdateMutation.updateRule(request)} forwards the caller-built object straight
 * through to {@code WAWebRelayClient.commitMutation}; the request object describes the rule, mirroring
 * the rule shape the relay echoes back (a {@code rule_type}, a {@code custom_rule}, an emoji-frequency
 * config, and a price-sharing config). The relay returns the update outcome under
 * {@code xfb_meta_ai_biz_agent_wa_update_rule}; the reply is consumed through
 * {@link BizAiRuleUpdateWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code request} object as a caller-supplied, already
 * JSON-encoded object literal because no caller in the JS bundle of snapshot {@code 1040120866}
 * constructs it (the {@code updateRule} dispatcher forwards its argument verbatim), so the input
 * type's field names are not recoverable; the value is emitted verbatim as the {@code request}
 * variable. Once a caller that builds the object surfaces, replace this with typed scalar fields
 * mirroring that construction.
 *
 * @see BizAiRuleUpdateWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiRuleUpdateMutation")
public final class BizAiRuleUpdateWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiRuleUpdateMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "26525955470399173";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiRuleUpdateMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiRuleUpdateMutation";

    /**
     * The pre-encoded JSON of the {@code request} GraphQL object describing the rule to update, or
     * {@code null} to omit it.
     */
    private final String requestJson;

    /**
     * Constructs an update-business-AI-rule mutation request.
     *
     * <p>The {@code requestJson} is the already-JSON-encoded {@code request} object describing the
     * rule to update; its field names are defined by the server-side rule input type and are not
     * modelled here (see the class {@code @implNote}). A {@code null} value omits the variable.
     *
     * @param requestJson the already-JSON-encoded {@code request} object, or {@code null} to omit the
     *                    variable
     */
    public BizAiRuleUpdateWhatsAppGraphQlRequest(String requestJson) {
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
    @WhatsAppWebExport(moduleName = "WAWebBizAiRuleUpdateMutation", exports = "updateRule",
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
