package com.github.auties00.cobalt.node.mex.json.user;

import com.alibaba.fastjson2.JSON;
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
import java.util.Optional;

/**
 * Builds the MEX IQ stanza that retrieves a linked-identity (LID) change
 * notification.
 *
 * @apiNote Powers the LID migration flow. WA Web's
 * {@code WAWebMexLidChangeNotificationHandler.mexHandleLidChangeNotification}
 * dispatches this stanza (gated on
 * {@code WAWebUsernameGatingUtils.usernameDisplayedEnabled}) when the
 * server emits a {@code lid_change} notification, then inserts a chat-side
 * "change_lid" notification template so the user sees the new identifier.
 * Pair the dispatched stanza with {@link LidChangeNotificationMexResponse}
 * to consume the reply.
 *
 * @see LidChangeNotificationMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexLidChangeNotification")
public final class LidChangeNotificationMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted query.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexLidChangeNotificationQuery.graphql} for the snapshot
     * this file was generated against.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexLidChangeNotificationQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9892367127524985";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexLidChangeNotificationQuery.graphql}; WA Web tags the
     * value to {@code MexPerfTracker} for per-operation telemetry bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexLidChangeNotificationQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "parseLidChangeNotification";

    /**
     * Constructs a LID-change notification fetch request.
     *
     * @apiNote The compiled GraphQL document declares no variables; the
     * dispatched stanza carries an empty {@code variables} object.
     */
    public LidChangeNotificationMexRequest() {
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
     * @implNote This implementation emits {@code {"variables": {}}} and
     * defers envelope construction to
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexLidChangeNotification", exports = "parseLidChangeNotification",
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
