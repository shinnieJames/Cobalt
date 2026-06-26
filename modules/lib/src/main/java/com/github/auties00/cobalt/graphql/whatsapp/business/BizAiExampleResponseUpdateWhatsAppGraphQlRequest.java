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
 * Builds the relay mutation that updates a WhatsApp Business AI agent's example responses (the FAQ
 * knowledge entries the agent answers from).
 *
 * <p>The mutation takes one {@code input} GraphQL variable. WhatsApp Web's
 * {@code WAWebBizAiExampleResponseUpdateMutation.updateExampleResponses(faq)} wraps the caller's FAQ
 * payload as {@code {input: {faq: <faq>}}} and forwards it to the relay; the {@code faq} value is a
 * FAQ-knowledge object built by an unbundled caller, so its internal field names are not recoverable
 * from the current snapshot. The relay returns the update outcome under
 * {@code xfb_meta_ai_biz_agent_wa_update_knowledge}; the reply is consumed through
 * {@link BizAiExampleResponseUpdateWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code faq} object as a caller-supplied, already
 * JSON-encoded object literal because the FAQ-input field names are not present in the JS bundle of
 * snapshot {@code 1040120866}; the {@code {input: {faq: ...}}} envelope is reconstructed here and the
 * {@code faq} value is emitted verbatim under it. Once a caller that builds the FAQ object surfaces,
 * replace this with typed scalar fields mirroring that construction.
 *
 * @see BizAiExampleResponseUpdateWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiExampleResponseUpdateMutation")
public final class BizAiExampleResponseUpdateWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiExampleResponseUpdateMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "36542743545312870";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiExampleResponseUpdateMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiExampleResponseUpdateMutation";

    /**
     * The pre-encoded JSON of the {@code faq} object spliced under the {@code input} GraphQL object,
     * or {@code null} to omit it.
     */
    private final String faqJson;

    /**
     * Constructs an example-response-update mutation request.
     *
     * <p>The {@code faqJson} is the already-JSON-encoded FAQ-knowledge object placed under
     * {@code input.faq}; its field names are defined by the server-side FAQ-input type and are not
     * modelled here (see the class {@code @implNote}). A {@code null} value omits the {@code faq}
     * field from the serialized {@code input} object.
     *
     * @param faqJson the already-JSON-encoded {@code faq} object, or {@code null} to omit the field
     */
    public BizAiExampleResponseUpdateWhatsAppGraphQlRequest(String faqJson) {
        this.faqJson = faqJson;
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
     * @implNote This implementation emits {@code {"input": {"faq": <faqJson>}}}, writing the
     * {@code faq} field only when {@code faqJson} is non-null and emitting {@code {"input": {}}} when
     * it is {@code null}. The {@code faq} value is spliced in as a raw JSON value via
     * {@link JSONWriter#writeRaw(String)} because it is supplied already encoded.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiExampleResponseUpdateMutation", exports = "updateExampleResponses",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("input");
            writer.writeColon();
            writer.startObject();
            if (faqJson != null) {
                writer.writeName("faq");
                writer.writeColon();
                writer.writeRaw(faqJson);
            }
            writer.endObject();
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
