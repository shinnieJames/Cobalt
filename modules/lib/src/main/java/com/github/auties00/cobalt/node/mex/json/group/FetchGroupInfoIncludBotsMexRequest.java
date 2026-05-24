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
 * Outbound MEX query that fetches the full metadata snapshot for a group
 * with bot participants included as first-class members alongside humans.
 *
 * @apiNote Issued by WA Web's
 * {@code WAWebGroupQueryGroupJob.queryGroupJob} when
 * {@code WAWebBotGroupGatingUtils.isOpenGroupBotParticipantAddEnabled()}
 * is on, in place of the bot-excluding
 * {@link FetchGroupInfoMexRequest}. The participant edge list emits a
 * {@code participant.jid} field per row that callers route through
 * {@code WAWebBotUtils.isWidOpenGroupMetaBotFbidWid} /
 * {@code isWidTeeGroupMetaBotFbidWid} to classify open-group bots and TEE
 * (Trusted Execution Environment) bots, surfacing bot display names and
 * roles in the chat-info panel.
 *
 * @implNote This implementation forwards an optional caller-supplied
 * {@code query_context} verbatim; WA Web folds the surface tag through a
 * normalising dispatcher (see {@link FetchGroupInfoMexRequest}). The
 * variables shape matches the bot-excluding variant; only the
 * {@code participant} edge sub-object on the response side carries the
 * extra {@code jid} and {@code display_name} bot fields.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchGroupInfoIncludBotsJob")
public final class FetchGroupInfoIncludBotsMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchGroupInfoIncludBotsJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchGroupInfoIncludBotsJobQuery.graphql}. The relay
     * maps this id to its persisted operation; the GraphQL text is never
     * sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInfoIncludBotsJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "26342357488707090";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexGetGroupInfoIncludBots";

    /**
     * The target group identifier bound to the {@code id} GraphQL variable.
     */
    private final String id;

    /**
     * The username-projection toggle bound to the {@code include_username}
     * GraphQL variable.
     */
    private final Boolean includeUsername;

    /**
     * The participant-set hash bound to the {@code participants_phash}
     * GraphQL variable, enabling the relay to skip the edge list when the
     * local cache matches.
     */
    private final String participantsPhash;

    /**
     * The telemetry context tag bound to the {@code query_context} GraphQL
     * variable.
     */
    private final String queryContext;

    /**
     * Constructs a new request with the four GraphQL variables.
     *
     * @apiNote {@code includeUsername} mirrors WA Web's
     * {@code WAWebUsernameGatingUtils.usernameDisplayedEnabled()} feature
     * flag and controls whether the relay projects participant usernames.
     * {@code participantsPhash} is the rolling hash WA Web maintains on
     * the cached participant set; sending it lets the relay return
     * {@code participants_phash_match = true} and skip the edge list to
     * save bandwidth. Any of the four arguments may be {@code null} to
     * omit the variable from the wire payload.
     *
     * @param id                 the target group identifier, may be {@code null} to omit
     * @param includeUsername    whether to project usernames on participant edges, may be {@code null} to omit
     * @param participantsPhash  the rolling participant-set hash, may be {@code null} to omit
     * @param queryContext       the telemetry context tag, may be {@code null} to omit
     */
    public FetchGroupInfoIncludBotsMexRequest(String id, Boolean includeUsername, String participantsPhash, String queryContext) {
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
     * @implNote This implementation streams the GraphQL variables through
     * fastjson2's {@link JSONWriter} and only emits each field when its
     * corresponding constructor argument is non-null, matching the WA Web
     * pattern that omits undefined GraphQL variables. The wrapped
     * envelope is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInfoIncludBotsJob", exports = "mexGetGroupInfoIncludBots",
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
