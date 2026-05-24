package com.github.auties00.cobalt.node.mex.json.misc;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Outbound MEX query that fetches the current OHAI (Oblivious HTTP
 * Application Initialisation) key configuration list issued by the
 * WhatsApp relay.
 *
 * @apiNote Issued by WA Web's
 * {@code WAWebOHAIKeyConfigProvider.provideOHAIKeyConfig} when the OHAI
 * key cache stored under {@code WAWebOHAIUserPrefs.getOHAIKeyConfig} is
 * empty or its {@code expirationDate} is within an hour of the wall
 * clock. The result feeds {@code WAWebDebugACS} and ACS-credential
 * redemption helpers, which use the bundle to wrap Account Centre
 * Service (ACS) requests inside an HPKE-encrypted Oblivious HTTP envelope.
 *
 * @implNote This implementation emits an empty {@code variables} object
 * because the compiled GraphQL artifact declares
 * {@code argumentDefinitions: []}; the {@code WAWebOHAIKeyConfigProvider}
 * call-site likewise passes the empty object literal.
 */
@WhatsAppWebModule(moduleName = "WAWebFetchOHAIKeyConfigJob")
public final class FetchOHAIKeyConfigMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebFetchOHAIKeyConfigJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebFetchOHAIKeyConfigJobQuery.graphql}. The relay maps the
     * id to a server-side persisted operation and never sees the GraphQL
     * text on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchOHAIKeyConfigJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "29366514836329275";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "WAWebFetchOHAIKeyConfigJobQuery";

    /**
     * Constructs a new request carrying no GraphQL variables.
     *
     * @apiNote The compiled GraphQL artifact takes no inputs so a single
     * instance is sufficient for every dispatch.
     */
    public FetchOHAIKeyConfigMexRequest() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String id() {
        return QUERY_ID;
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
     * @implNote This implementation writes an empty {@code variables}
     * object using fastjson2's {@link JSONWriter} and wraps it via
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchOHAIKeyConfigJob", exports = "mexFetchOHAIKeyConfig",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            writer.endObject();
            writer.endObject();

            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return Json.createMexNode(QUERY_ID, output.toString());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
