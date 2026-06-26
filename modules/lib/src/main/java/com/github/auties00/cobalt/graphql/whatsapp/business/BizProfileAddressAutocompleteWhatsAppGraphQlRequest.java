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
 * Builds the relay query that resolves a postal-address typeahead for a WhatsApp Business profile.
 *
 * <p>The single {@code input} GraphQL variable is the Maps typeahead query parameters object; the
 * compiled document remaps it onto the server-side {@code query_params} argument of the
 * {@code whatsapp_maps_typeahead} field. No bundled caller constructs that object in snapshot
 * {@code 1040120866}, so its field names are not recoverable from the JS source. The relay returns
 * the ranked typeahead matches under {@code whatsapp_maps_typeahead}; the reply is consumed through
 * {@link BizProfileAddressAutocompleteWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts the {@code input} object as a caller-supplied, already
 * JSON-encoded object literal because no module in snapshot {@code 1040120866} builds the Maps
 * typeahead query-parameters object; the value is emitted verbatim as the {@code input} variable.
 * Once a caller that builds the object surfaces, replace this with typed scalar fields mirroring that
 * construction.
 *
 * @see BizProfileAddressAutocompleteWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizProfileAddressAutocompleteQuery")
public final class BizProfileAddressAutocompleteWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizProfileAddressAutocompleteQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "34963438739971331";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizProfileAddressAutocompleteQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizProfileAddressAutocompleteQuery";

    /**
     * The pre-encoded JSON of the {@code input} GraphQL object carrying the Maps typeahead query
     * parameters, or {@code null} to omit it.
     */
    private final String inputJson;

    /**
     * Constructs an address-autocomplete query request.
     *
     * <p>The {@code inputJson} is the already-JSON-encoded {@code input} object holding the Maps
     * typeahead query parameters; its field names are defined by the server-side query-parameters
     * type and are not modelled here (see the class {@code @implNote}). A {@code null} value omits the
     * variable from the serialized object.
     *
     * @param inputJson the already-JSON-encoded {@code input} object, or {@code null} to omit the
     *                  variable
     */
    public BizProfileAddressAutocompleteWhatsAppGraphQlRequest(String inputJson) {
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
     * when its value is non-null and emitting {@code "{}"} otherwise. The {@code input} value is
     * spliced in as a raw JSON value via {@link JSONWriter#writeRaw(String)} because it is supplied
     * already encoded.
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
