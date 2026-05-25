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
 * Builds the MEX IQ stanza that queries a single user's about-status text.
 *
 * <p>The about-status is the free-text line shown on a contact's profile screen and in the
 * "Read more" overlay. The request carries a single {@code user} GraphQL variable identifying the
 * target account; the dispatched stanza is paired with {@link FetchAboutStatusMexResponse} to
 * consume the relay's reply.
 *
 * @implNote This implementation exposes only the {@code user} variable. WhatsApp Web additionally
 * attaches a {@code privacy_token.tctoken} when the profile-scraping-protection gate is enabled;
 * Cobalt callers that need that gate must supply the token at a higher layer, as the present request
 * does not embed it.
 *
 * @see FetchAboutStatusMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchAboutStatusJob")
public final class FetchAboutStatusMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled-document identifier the relay maps to the persisted query.
     *
     * <p>Emitted as the {@code query_id} attribute of the outbound {@code <query>} node.
     *
     * @implNote The value matches the compiled query for the WhatsApp Web snapshot this file was
     * generated against, and must be rotated together with that bundle.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAboutStatusJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "24535500086059408";

    /**
     * Holds the GraphQL operation name reported alongside this request.
     *
     * <p>WhatsApp Web tags this name onto its per-operation latency metrics; embedders mirroring
     * that telemetry surface tag their spans with this constant.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAboutStatusJobQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexGetAbout";

    /**
     * Holds the {@code user} GraphQL variable carrying the target user identifier.
     */
    private final String user;

    /**
     * Constructs a request that asks for the about-status of a single user.
     *
     * <p>The identifier is forwarded verbatim as the {@code user} GraphQL variable; WhatsApp Web
     * populates it from the bare user portion of the JID, without the server suffix. A {@code null}
     * value omits the variable entirely.
     *
     * @param user the bare user identifier of the target account, or {@code null} to omit the
     *             variable
     */
    public FetchAboutStatusMexRequest(String user) {
        this.user = user;
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
     * @implNote This implementation serialises {@code {"variables": {"user": <user>}}}, or
     * {@code {"variables": {}}} when {@code user} is {@code null}, and delegates to
     * {@link MexOperation.Request.Json#createMexNode(String, String)} to wrap the JSON in the
     * {@code <iq xmlns="w:mex">} envelope.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAboutStatusJob", exports = "mexGetAbout",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (user != null) {
                writer.writeName("user");
                writer.writeColon();
                writer.writeString(user);
            }
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
