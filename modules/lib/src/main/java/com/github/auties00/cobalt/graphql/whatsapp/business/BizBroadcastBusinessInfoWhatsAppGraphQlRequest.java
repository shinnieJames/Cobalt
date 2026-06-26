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
 * Builds the relay mutation that resolves the marketing-message business info backing a WhatsApp
 * Business broadcast.
 *
 * <p>The single {@code input} GraphQL variable carries the business-info request. The relay returns
 * the resolved entities under {@code xwa_smb_mm_business_info}: the {@code business}, the
 * {@code business_payment_account}, the {@code ad_account}, and the {@code page}, each exposed by its
 * id. The reply is consumed through {@link BizBroadcastBusinessInfoWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code input} object as a caller-supplied, already
 * JSON-encoded object literal because the source module {@code useWAWebBizBroadcastBusinessInfoMutation}
 * and its dispatcher are absent from the JS bundle of snapshot {@code 1040120866}, so the input type's
 * field names are not recoverable; the value is emitted verbatim as the {@code input} variable. Once a
 * caller that builds the object surfaces, replace this with typed scalar fields mirroring that
 * construction.
 *
 * @see BizBroadcastBusinessInfoWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "useWAWebBizBroadcastBusinessInfoMutation")
public final class BizBroadcastBusinessInfoWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "useWAWebBizBroadcastBusinessInfoMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "26164128406511010";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "useWAWebBizBroadcastBusinessInfoMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "useWAWebBizBroadcastBusinessInfoMutation";

    /**
     * The pre-encoded JSON of the {@code input} GraphQL object carrying the business-info request, or
     * {@code null} to omit it.
     */
    private final String inputJson;

    /**
     * Constructs a resolve-business-info mutation request.
     *
     * <p>The {@code inputJson} is the already-JSON-encoded {@code input} object carrying the
     * business-info request; its field names are defined by the server-side input type and are not
     * modelled here (see the class {@code @implNote}). A {@code null} value omits the variable.
     *
     * @param inputJson the already-JSON-encoded {@code input} object, or {@code null} to omit the
     *                  variable
     */
    public BizBroadcastBusinessInfoWhatsAppGraphQlRequest(String inputJson) {
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
     * when its value is non-null and emitting {@code "{}"} when it is {@code null}. The {@code input}
     * value is spliced in as a raw JSON value via {@link JSONWriter#writeRaw(String)} because it is
     * supplied already encoded.
     */
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
