package com.github.auties00.cobalt.node.mex.json.user;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the MEX IQ stanza that fetches the authenticated user's username record.
 *
 * <p>The reply feeds the username pane of the Settings screen. The dispatched stanza is paired with
 * {@link GetUsernameMexResponse} to consume the reply.
 *
 * @implNote WhatsApp Web treats an HTTP 404 reply as the absence of a registered username and
 * synthesises a {@code (null, null, null)} record. Cobalt does not collapse that case at this layer,
 * so callers treat an empty {@link GetUsernameMexResponse#usernameInfo()} as the no-username outcome.
 *
 * @see GetUsernameMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexGetUsernameJob")
public final class GetUsernameMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled-document identifier the relay maps to the persisted query.
     *
     * <p>Emitted as the {@code query_id} attribute of the outbound {@code <query>} node.
     *
     * @implNote The value matches the compiled query for the WhatsApp Web snapshot this file was
     * generated against, and must be rotated together with that bundle.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetUsernameJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "25347099718279209";

    /**
     * Holds the GraphQL operation name reported alongside this request.
     *
     * <p>WhatsApp Web tags this name onto its per-operation latency metrics.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetUsernameJobQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexGetUsernameQueryJob";

    /**
     * Constructs a get-username request.
     *
     * <p>The compiled GraphQL document declares no variables; the dispatched stanza carries an empty
     * {@code variables} object.
     */
    public GetUsernameMexRequest() {
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
     * @implNote This implementation emits {@code {"variables": {}}} and defers envelope construction
     * to {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetUsernameJob", exports = "mexGetUsernameQueryJob",
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
