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
 * Builds the relay mutation that creates a chat-history backup for a WhatsApp Business AI agent.
 *
 * <p>The mutation takes one GraphQL variable, {@code input}, which the relay forwards as the
 * {@code request} argument of {@code xfb_maiba_create_chat_history}. WhatsApp Web's
 * {@code WAWebBizAiChatHistoryCreateMutation.createChatHistoryBackup(input)} passes its caller-built
 * argument straight through as the {@code input} variable without destructuring it, so the object's
 * internal field names are not recoverable from the current snapshot. The relay returns the create
 * outcome under the linked {@code xfb_maiba_create_chat_history} field; the reply is consumed through
 * {@link BizAiChatHistoryCreateWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code input} object as a caller-supplied, already
 * JSON-encoded object literal because its field names are not present in the JS bundle of snapshot
 * {@code 1040120866}; the dispatcher forwards the argument verbatim. The value is emitted verbatim as
 * the {@code input} variable. Once a caller that builds the object surfaces, replace this with typed
 * scalar fields mirroring that construction.
 *
 * @see BizAiChatHistoryCreateWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiChatHistoryCreateMutation")
public final class BizAiChatHistoryCreateWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiChatHistoryCreateMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "27499829029619003";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiChatHistoryCreateMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiChatHistoryCreateMutation";

    /**
     * The pre-encoded JSON of the {@code input} GraphQL object forwarded as the field's
     * {@code request} argument, or {@code null} to omit it.
     */
    private final String inputJson;

    /**
     * Constructs a create-chat-history mutation request.
     *
     * <p>The {@code inputJson} is the already-JSON-encoded {@code input} object the relay forwards as
     * the {@code request} argument; its field names are defined by the server-side input type and are
     * not modelled here (see the class {@code @implNote}). A {@code null} value omits the variable
     * from the serialized object, matching the dispatcher's empty-variables branch.
     *
     * @param inputJson the already-JSON-encoded {@code input} object, or {@code null} to omit the
     *                  variable
     */
    public BizAiChatHistoryCreateWhatsAppGraphQlRequest(String inputJson) {
        this.inputJson = inputJson;
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
     * @implNote This implementation emits {@code {"input": <inputJson>}}, writing the variable only
     * when {@code inputJson} is non-null and emitting {@code "{}"} otherwise. The {@code input} value
     * is spliced in as a raw JSON value via {@link JSONWriter#writeRaw(String)} because it is supplied
     * already encoded.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiChatHistoryCreateMutation", exports = "createChatHistoryBackup",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            if (inputJson != null) {
                writer.writeName("input");
                writer.writeColon();
                writer.writeRaw(inputJson);
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
