package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the relay mutation that submits a WhatsApp support bug report.
 *
 * <p>The single {@code input} GraphQL variable is the server-side bug-report payload.
 * WhatsApp Web's {@code WAWebSupportBugReportSubmitMutation.submitBugReportGraphQL(input)} forwards
 * the object straight through to the relay, which returns the submission outcome under
 * {@code xwa_wa_support_bug_report_submit}; the reply is consumed through
 * {@link SupportBugReportSubmitWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code input} object as a caller-supplied, already
 * JSON-encoded object literal because no caller that builds the bug-report input is present in the JS
 * bundle of snapshot {@code 1040120866}: the dispatcher forwards the {@code input} argument opaquely
 * and no field names are recoverable. The value is emitted verbatim as the {@code input} variable.
 * Once a caller that builds the object surfaces, replace this with typed scalar fields mirroring that
 * construction.
 *
 * @see SupportBugReportSubmitWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebSupportBugReportSubmitMutation")
public final class SupportBugReportSubmitWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebSupportBugReportSubmitMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "25952242091096312";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebSupportBugReportSubmitMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebSupportBugReportSubmitMutation";

    /**
     * The pre-encoded JSON of the {@code input} GraphQL object carrying the bug-report payload, or
     * {@code null} to omit it.
     */
    private final String inputJson;

    /**
     * Constructs a submit-bug-report mutation request.
     *
     * <p>The {@code inputJson} is the already-JSON-encoded {@code input} object holding the bug-report
     * payload; its field names are defined by the server-side bug-report input type and are not
     * modelled here (see the class {@code @implNote}). A {@code null} value omits the variable from
     * the serialized object.
     *
     * @param inputJson the already-JSON-encoded {@code input} object, or {@code null} to omit the
     *                  variable
     */
    public SupportBugReportSubmitWhatsAppGraphQlRequest(String inputJson) {
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
    @WhatsAppWebExport(moduleName = "WAWebSupportBugReportSubmitMutation", exports = "submitBugReportGraphQL",
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
