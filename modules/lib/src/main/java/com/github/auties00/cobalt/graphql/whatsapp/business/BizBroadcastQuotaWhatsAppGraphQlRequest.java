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
 * Builds the relay query that fetches the SMB marketing-message quota for a WhatsApp Business
 * broadcast.
 *
 * <p>The query takes a single {@code data} GraphQL variable describing the quota lookup. The relay
 * returns the quota under {@code xwa_smb_mm_quota}; the reply is consumed through
 * {@link BizBroadcastQuotaWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code data} object as a caller-supplied, already
 * JSON-encoded object literal because the {@code useWAWebBizBroadcastQuotaQuery} document and its
 * dispatcher are absent from the WhatsApp Web bundle of snapshot {@code 1040120866}, so the
 * {@code data} field names are not recoverable; the value is emitted verbatim as the {@code data}
 * variable. Once a caller that builds the object surfaces, replace this with typed scalar fields
 * mirroring that construction.
 *
 * @see BizBroadcastQuotaWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "useWAWebBizBroadcastQuotaQuery")
public final class BizBroadcastQuotaWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "useWAWebBizBroadcastQuotaQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "26388379850831833";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "useWAWebBizBroadcastQuotaQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "useWAWebBizBroadcastQuotaQuery";

    /**
     * The pre-encoded JSON of the {@code data} GraphQL object describing the quota lookup, or
     * {@code null} to omit it.
     */
    private final String dataJson;

    /**
     * Constructs a broadcast-quota query request.
     *
     * <p>The {@code dataJson} is the already-JSON-encoded {@code data} object; its field names are
     * defined by the server-side input type and are not modelled here (see the class
     * {@code @implNote}). A {@code null} value omits the variable from the serialized object.
     *
     * @param dataJson the already-JSON-encoded {@code data} object, or {@code null} to omit the
     *                 variable
     */
    public BizBroadcastQuotaWhatsAppGraphQlRequest(String dataJson) {
        this.dataJson = dataJson;
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
     * @implNote This implementation emits {@code {"data": <dataJson>}}, writing the variable only when
     * its value is non-null and emitting {@code "{}"} when it is {@code null}. The {@code data} value
     * is spliced in as a raw JSON value via {@link JSONWriter#writeRaw(String)} because it is supplied
     * already encoded.
     */
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            if (dataJson != null) {
                writer.writeName("data");
                writer.writeColon();
                writer.writeRaw(dataJson);
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
