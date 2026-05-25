package com.github.auties00.cobalt.node.mex.json.group;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Outbound MEX query that fetches the full metadata snapshot for a group, community or community
 * subgroup; bot participants are excluded from the participant edge list.
 *
 * <p>The reply carries the four-way group profile (regular group, community, default subgroup,
 * subgroup) including creator identity, subject and description (with author and edit timestamps),
 * participant edges, ephemeral timer, membership-approval state, LID migration state,
 * parent-community link and group-lock status. The bot-inclusive variant is
 * {@link FetchGroupInfoIncludBotsMexRequest}, used when the open-group bot gating flag is on.
 *
 * @implNote This implementation forwards an optional caller-supplied {@code query_context} variable
 * verbatim; WA Web normalises the surface tag (mapping {@code "interactive"} and
 * {@code "enter_group_info"} to {@code "INTERACTIVE"}, and the
 * {@code "missing_participant_identification"} tag to its uppercase equivalent) before sending,
 * whereas Cobalt leaves the normalisation to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchGroupInfoJob")
public final class FetchGroupInfoMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the {@code WAWebMexFetchGroupInfoJobQuery} document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text is never sent on the
     * wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInfoJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "26197094166585473";

    /**
     * GraphQL operation name reported alongside this query when it is dispatched.
     *
     * <p>Tags the query in latency and error metrics; kept on the request for embedders mirroring
     * WhatsApp's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexGetGroupInfo";

    /**
     * Target group identifier bound to the {@code id} GraphQL variable.
     */
    private final String id;

    /**
     * Username-projection toggle bound to the {@code include_username} GraphQL variable.
     */
    private final Boolean includeUsername;

    /**
     * Participant-set hash bound to the {@code participants_phash} GraphQL variable, enabling the
     * relay to skip the edge list when the local cache matches.
     */
    private final String participantsPhash;

    /**
     * Telemetry context tag bound to the {@code query_context} GraphQL variable.
     */
    private final String queryContext;

    /**
     * Constructs a new request with the four GraphQL variables.
     *
     * <p>The {@code includeUsername} flag controls whether the relay projects participant usernames,
     * mirroring WA Web's username-display gating. The {@code participantsPhash} is the rolling hash
     * maintained on the cached participant set; sending it lets the relay reply with
     * {@code participants_phash_match = true} and skip the edge list to save bandwidth. Any of the
     * four arguments may be {@code null} to omit the variable from the wire payload.
     *
     * @param id                 the target group identifier, may be {@code null} to omit
     * @param includeUsername    whether to project usernames on participant edges, may be {@code null} to omit
     * @param participantsPhash  the rolling participant-set hash, may be {@code null} to omit
     * @param queryContext       the telemetry context tag, may be {@code null} to omit
     */
    public FetchGroupInfoMexRequest(String id, Boolean includeUsername, String participantsPhash, String queryContext) {
        this.id = id;
        this.includeUsername = includeUsername;
        this.participantsPhash = participantsPhash;
        this.queryContext = queryContext;
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
     * @implNote This implementation streams the GraphQL variables through fastjson2's
     * {@link JSONWriter} and emits each field only when its corresponding constructor argument is
     * non-null, matching the WA Web convention of omitting undefined GraphQL variables. The wrapped
     * envelope is built through {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInfoJob", exports = "mexGetGroupInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (id != null) {
                writer.writeName("id");
                writer.writeColon();
                writer.writeString(id);
            }
            if (includeUsername != null) {
                writer.writeName("include_username");
                writer.writeColon();
                writer.writeBool(includeUsername);
            }
            if (participantsPhash != null) {
                writer.writeName("participants_phash");
                writer.writeColon();
                writer.writeString(participantsPhash);
            }
            if (queryContext != null) {
                writer.writeName("query_context");
                writer.writeColon();
                writer.writeString(queryContext);
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
