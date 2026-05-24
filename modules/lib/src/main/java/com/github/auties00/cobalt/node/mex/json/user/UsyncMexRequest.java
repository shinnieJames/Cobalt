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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the MEX IQ stanza that batches user-directory lookups through the
 * GraphQL usync variant.
 *
 * @apiNote Powers contact sync, device-list refresh, and the per-feature
 * facades that ship in {@code WAWebMexUsersGetAboutStatus},
 * {@code WAWebMexUsersGetCountryCode}, and {@code WAWebMexUsersGetUsername}.
 * Each facade dispatches this query with a different {@code fetch} flag
 * subset; Cobalt exposes the three flags individually so callers can
 * compose the same projections. Pair the dispatched stanza with
 * {@link UsyncMexResponse} to consume the reply.
 *
 * @implNote This implementation forwards {@code input} as a pre-serialised
 * scalar. WA Web's mirror constructs it from
 * {@code {users: [{jid, privacy_token?}], telemetry: {context}}} after
 * filtering each entry against
 * {@code WAWebWidFactory.createWid(jid).isEligibleForUSync()}; Cobalt
 * callers must perform that filtering and serialisation at a higher
 * layer.
 *
 * @see UsyncMexResponse
 */
@WhatsAppWebModule(moduleName = "WAWebMexUsync")
public final class UsyncMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled-document id the relay maps to the persisted query.
     *
     * @apiNote Used as the {@code query_id} attribute of the outbound
     * {@code <query>} node. Matches the {@code params.id} field of
     * {@code WAWebMexUsyncQuery.graphql} for the snapshot this file was
     * generated against.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsyncQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "29829202653362039";

    /**
     * The GraphQL operation name reported alongside this request.
     *
     * @apiNote Mirrors {@code params.name} on
     * {@code WAWebMexUsyncQuery.graphql}; WA Web tags the value to
     * {@code MexPerfTracker} for per-operation telemetry bucketing.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsyncQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexUsyncQuery";

    /**
     * The {@code include_about_status} flag, possibly {@code null}.
     */
    private final Boolean includeAboutStatus;

    /**
     * The {@code include_country_code} flag, possibly {@code null}.
     */
    private final Boolean includeCountryCode;

    /**
     * The {@code include_username} flag, possibly {@code null}.
     */
    private final Boolean includeUsername;

    /**
     * The {@code input} GraphQL variable carrying the pre-serialised batch.
     */
    private final String input;

    /**
     * Constructs a usync query request.
     *
     * @apiNote Each {@code include_*} toggle controls whether the
     * corresponding sub-object is projected on each result row. WA Web's
     * facades set exactly one toggle per dispatch
     * ({@link UsyncMexResponse.Item#aboutStatusInfo()} from
     * {@code WAWebMexUsersGetAboutStatus},
     * {@link UsyncMexResponse.Item#countryCode()} from
     * {@code WAWebMexUsersGetCountryCode},
     * {@link UsyncMexResponse.Item#usernameInfo()} from
     * {@code WAWebMexUsersGetUsername}); callers may combine them in a
     * single request to amortise the round-trip cost.
     *
     * @param includeAboutStatus whether to include the about-status sub-object,
     *                           or {@code null} to omit the variable
     * @param includeCountryCode whether to include the country-code field,
     *                           or {@code null} to omit the variable
     * @param includeUsername whether to include the username sub-object,
     *                        or {@code null} to omit the variable
     * @param input the serialised batch input, or {@code null} to omit
     *              the variable
     */
    public UsyncMexRequest(Boolean includeAboutStatus, Boolean includeCountryCode, Boolean includeUsername, String input) {
        this.includeAboutStatus = includeAboutStatus;
        this.includeCountryCode = includeCountryCode;
        this.includeUsername = includeUsername;
        this.input = input;
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
     * @implNote This implementation emits each non-{@code null} flag and
     * the {@code input} scalar into the {@code variables} object, then
     * defers envelope construction to
     * {@link MexOperation.Request.Json#createMexNode(String, String)}. A
     * fully empty request serialises as {@code {"variables": {}}}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsync", exports = "mexUsyncQuery",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (includeAboutStatus != null) {
                writer.writeName("include_about_status");
                writer.writeColon();
                writer.writeBool(includeAboutStatus);
            }
            if (includeCountryCode != null) {
                writer.writeName("include_country_code");
                writer.writeColon();
                writer.writeBool(includeCountryCode);
            }
            if (includeUsername != null) {
                writer.writeName("include_username");
                writer.writeColon();
                writer.writeBool(includeUsername);
            }
            if (input != null) {
                writer.writeName("input");
                writer.writeColon();
                writer.writeString(input);
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
