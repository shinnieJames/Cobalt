package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the relay mutation that edits a WhatsApp Business profile.
 *
 * <p>The mutation takes two GraphQL variables: a {@code lid} {@link Jid} naming the business account
 * to edit and an {@code input} object carrying the fields to change. WhatsApp Web's
 * {@code WAWebEditBizProfileMutation.editBizProfile(lid, input)} forwards both straight through to
 * the relay; the {@code input} object is an opaque pass-through built by an unbundled caller, so its
 * internal field names are not recoverable from the current snapshot. The relay returns the edit
 * outcome under the scalar {@code edit_wa_web_biz_profile}; the reply is consumed through
 * {@link EditBizProfileWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code input} object as a caller-supplied, already
 * JSON-encoded object literal because the {@code WebBizProfileInput} field names are not present in
 * the JS bundle of snapshot {@code 1040120866}; the value is emitted verbatim as the {@code input}
 * variable. Once a caller that builds the object surfaces, replace this with typed scalar fields
 * mirroring that construction.
 *
 * @see EditBizProfileWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebEditBizProfileMutation")
public final class EditBizProfileWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebEditBizProfileMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "26652989367627867";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebEditBizProfileMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebEditBizProfileMutation";

    /**
     * The {@code lid} GraphQL variable naming the business account to edit, or {@code null} to omit
     * it.
     */
    private final Jid lid;

    /**
     * The pre-encoded JSON of the {@code input} GraphQL object carrying the fields to change, or
     * {@code null} to omit it.
     */
    private final String inputJson;

    /**
     * Constructs an edit-business-profile mutation request.
     *
     * <p>The {@code lid} names the business account to edit. The {@code inputJson} is the
     * already-JSON-encoded {@code input} object holding the profile fields to change; its field
     * names are defined by the server-side {@code WebBizProfileInput} type and are not modelled here
     * (see the class {@code @implNote}). Each value that is {@code null} omits its variable from the
     * serialized object.
     *
     * @param lid       the business account {@link Jid} to edit, or {@code null} to omit the variable
     * @param inputJson the already-JSON-encoded {@code input} object, or {@code null} to omit the
     *                  variable
     */
    public EditBizProfileWhatsAppGraphQlRequest(Jid lid, String inputJson) {
        this.lid = lid;
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
     * @implNote This implementation emits {@code {"lid": <lid>, "input": <inputJson>}}, writing each
     * variable only when its value is non-null and emitting {@code "{}"} when both are {@code null}.
     * The {@code input} value is spliced in as a raw JSON value via
     * {@link JSONWriter#writeRaw(String)} because it is supplied already encoded.
     */
    @WhatsAppWebExport(moduleName = "WAWebEditBizProfileMutation", exports = "editBizProfile",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            if (lid != null) {
                writer.writeName("lid");
                writer.writeColon();
                writer.writeString(lid.toString());
            }

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
