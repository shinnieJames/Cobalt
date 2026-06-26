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
 * Builds the relay query that fetches the batched native machine-learning model manifest for a set
 * of requested models.
 *
 * <p>The operation takes two GraphQL variables: {@code model_request_metadatas}, a list naming the
 * models and versions to resolve, and {@code client_capability_metadata}, an object describing the
 * client's decode capabilities. WhatsApp Web's
 * {@code WAWebNativeMLModelQuery.getNativeMLModel(modelRequestMetadatas, clientCapabilityMetadata)}
 * forwards both straight through to the relay. The relay returns the model manifest under
 * {@code aim_model_batched_manifest}; the reply is consumed through
 * {@link NativeMlModelWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation accepts each variable as a caller-supplied, already JSON-encoded
 * value because the {@code model_request_metadatas} list elements and the
 * {@code client_capability_metadata} object are server-side input types whose field schema is not
 * declared in the JS bundle of snapshot {@code 1040120866}; the relay forwards both opaquely. The
 * only construction observed in the bundle ({@code WAWebBweMLModelManager}) builds list elements as
 * {@code {"name": <modelName>, "version": <version>}} and the capability object as
 * {@code {"bytecodeVersion": [...]}}. Once a typed caller surfaces, replace these with typed scalar
 * fields mirroring that construction.
 *
 * @see NativeMlModelWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebNativeMLModelQuery")
public final class NativeMlModelWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     *
     * @implNote This implementation ships the live {@code WAWebGraphQLPersistedQueries} numeric id
     * rather than the compiled {@code params.id} literal ({@code "32743078615336512"}); the
     * persisted-query map overrides that literal at dispatch time on WhatsApp Web.
     */
    @WhatsAppWebExport(moduleName = "WAWebNativeMLModelQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "9175958945830972";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebNativeMLModelQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebNativeMLModelQuery";

    /**
     * The pre-encoded JSON of the {@code model_request_metadatas} list naming the models to resolve,
     * or {@code null} to omit it.
     */
    private final String modelRequestMetadatasJson;

    /**
     * The pre-encoded JSON of the {@code client_capability_metadata} object describing the client's
     * decode capabilities, or {@code null} to omit it.
     */
    private final String clientCapabilityMetadataJson;

    /**
     * Constructs a native-ML-model query request carrying the model request metadatas and the client
     * capability metadata.
     *
     * <p>Both values are already-JSON-encoded GraphQL variables forwarded opaquely to the relay (see
     * the class {@code @implNote}); each value that is {@code null} is omitted from the serialized
     * object.
     *
     * @param modelRequestMetadatasJson    the already-JSON-encoded {@code model_request_metadatas}
     *                                     list, or {@code null} to omit the variable
     * @param clientCapabilityMetadataJson the already-JSON-encoded {@code client_capability_metadata}
     *                                     object, or {@code null} to omit the variable
     */
    public NativeMlModelWhatsAppGraphQlRequest(String modelRequestMetadatasJson, String clientCapabilityMetadataJson) {
        this.modelRequestMetadatasJson = modelRequestMetadatasJson;
        this.clientCapabilityMetadataJson = clientCapabilityMetadataJson;
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
     * @implNote This implementation emits {@code {"model_request_metadatas":
     * <modelRequestMetadatasJson>, "client_capability_metadata": <clientCapabilityMetadataJson>}},
     * writing each variable only when its value is non-null and emitting {@code "{}"} when both are
     * {@code null}. Each value is spliced in as a raw JSON value via
     * {@link JSONWriter#writeRaw(String)} because it is supplied already encoded.
     */
    @WhatsAppWebExport(moduleName = "WAWebNativeMLModelQuery", exports = "getNativeMLModel",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            if (modelRequestMetadatasJson != null) {
                writer.writeName("model_request_metadatas");
                writer.writeColon();
                writer.writeRaw(modelRequestMetadatasJson);
            }

            if (clientCapabilityMetadataJson != null) {
                writer.writeName("client_capability_metadata");
                writer.writeColon();
                writer.writeRaw(clientCapabilityMetadataJson);
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
