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
 * Builds the MEX IQ stanza that queries a user's about-status text.
 *
 * @apiNote Powers the "About" line shown on contact profile screens and the
 * "Read more" overlay; WA Web's {@code WAWebGetAboutQueryJob} fans this out
 * to LID-addressed and PN-addressed users (it dispatches this request only
 * for non-LID JIDs). Pair the dispatched stanza with
 * {@link FetchAboutStatusMexResponse} to consume the relay's reply.
 *
 * @implNote This implementation exposes only the {@code user} variable;
 * WA Web additionally attaches a {@code privacy_token.tctoken} when the
 * profile-scraping-protection gate is on, derived from
 * {@code WAWebPrivacyGatingUtils.isProfileScrappingProtectionInMexFetchEnabled()}.
 * Cobalt callers that need that gate must supply the token at a higher
 * layer; the present request does not embed it.
 *
 * @see FetchAboutStatusMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchAboutStatusJob")
public final class FetchAboutStatusMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted query.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexFetchAboutStatusJobQuery.graphql} for the snapshot this
     * file was generated against; the constant must be rotated together with
     * the WA Web JS bundle.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAboutStatusJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "24535500086059408";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexFetchAboutStatusJobQuery.graphql}. WA Web feeds the
     * value to {@code MexPerfTracker.setOperationName} so latency metrics
     * are bucketed per operation; Cobalt embedders mirroring that telemetry
     * surface should tag their spans with this constant.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAboutStatusJobQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexGetAbout";

    /**
     * The {@code user} GraphQL variable carrying the target user identifier.
     */
    private final String user;

    /**
     * Constructs a request that asks for the about-status of a single user.
     *
     * @apiNote {@code user} is forwarded verbatim as the {@code user}
     * GraphQL variable; WA Web populates it from {@code wid.user} (the bare
     * user portion of the JID, without the server suffix). A {@code null}
     * value omits the variable entirely; passing it deliberately yields the
     * caller's own about-status when the relay defaults the missing field.
     *
     * @param user the bare user identifier of the target account, or
     *             {@code null} to omit the variable
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
     * @implNote This implementation serialises {@code {"variables": {"user": <user>}}}
     * (or {@code {"variables": {}}} when {@code user} is {@code null}) and
     * delegates to {@link MexOperation.Request.Json#createMexNode(String, String)}
     * to wrap the JSON in the {@code <iq xmlns="w:mex">} envelope.
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
